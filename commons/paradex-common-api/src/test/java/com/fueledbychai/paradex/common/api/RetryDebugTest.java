package com.fueledbychai.paradex.common.api;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.Test;

import com.fueledbychai.data.ResponseException;
import com.fueledbychai.util.Util;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;

public class RetryDebugTest {

    @Test
    public void testUtilMethodDirectly() {
        ResponseException retryableException = new ResponseException("HTTP 503 Service Unavailable", 503);

        // Debug information
        System.out.println("Exception class: " + retryableException.getClass().getName());
        System.out.println("Exception status code: " + retryableException.getStatusCode());
        System.out.println("Exception message: " + retryableException.getMessage());
        System.out.println("Is instanceof ResponseException: "
                + (retryableException instanceof com.fueledbychai.data.ResponseException));

        boolean isRetryable = Util.isRetryableException(retryableException);
        System.out.println("Is retryable: " + isRetryable);

        assertTrue(isRetryable, "HTTP 503 should be retryable");

        ResponseException nonRetryableException = new ResponseException("HTTP 404 Not Found", 404);
        assertFalse(Util.isRetryableException(nonRetryableException), "HTTP 404 should NOT be retryable");
    }

    @Test
    public void testRetryConfigDirectly() {
        // Test the retry config directly
        RetryConfig config = RetryConfig.custom().maxAttempts(6).retryOnException(Util::isRetryableException).build();

        Retry retry = Retry.of("test", config);

        ResponseException retryableException = new ResponseException("HTTP 503 Service Unavailable", 503);

        final int[] callCount = { 0 };

        assertThrows(ResponseException.class, () -> {
            Retry.decorateSupplier(retry, () -> {
                callCount[0]++;
                throw retryableException;
            }).get();
        });

        assertEquals(6, callCount[0], "Should have made 6 attempts");
    }

    @Test
    public void testWithRuntimeExceptionWrapper() {
        // Test the retry with RuntimeException wrapper like ParadexInstrumentLookup
        // does
        RetryConfig config = RetryConfig.custom().maxAttempts(6).retryOnException(Util::isRetryableException).build();

        Retry retry = Retry.of("test", config);

        ResponseException retryableException = new ResponseException("HTTP 503 Service Unavailable", 503);

        final int[] callCount = { 0 };

        assertThrows(RuntimeException.class, () -> {
            Retry.decorateSupplier(retry, () -> {
                callCount[0]++;
                // This mimics what ParadexInstrumentLookup does
                throw new RuntimeException(retryableException);
            }).get();
        });

        assertEquals(6, callCount[0], "Should have made 6 attempts with wrapped exception");
    }
}