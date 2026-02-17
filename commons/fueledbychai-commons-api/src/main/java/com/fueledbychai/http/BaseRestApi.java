package com.fueledbychai.http;

import java.util.concurrent.Callable;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public abstract class BaseRestApi {

    protected final Logger baseLogger = LoggerFactory.getLogger(getClass());

    @FunctionalInterface
    protected interface RetryableAction {
        void run() throws Exception;
    }

    protected void executeWithRetry(RetryableAction action, int maxRetries, long retryDelayMillis) {
        int retries = 0;
        while (true) {
            try {
                action.run();
                return;
            } catch (java.net.SocketTimeoutException | IllegalStateException e) {
                if (retries < maxRetries) {
                    retries++;
                    baseLogger.error("Request failed. Retrying... Attempt " + retries, e);
                    try {
                        Thread.sleep(retryDelayMillis * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Retry interrupted", ie);
                    }
                } else {
                    baseLogger.error("Max retries reached. Failing request.", e);
                    throw new IllegalStateException("Max retries reached", e);
                }
            } catch (Exception ex) {
                throw new RuntimeException(ex.getMessage(), ex);
            }
        }
    }

    protected <T> T executeWithRetry(Callable<T> action, int maxRetries, long retryDelayMillis) {
        int retries = 0;
        while (true) {
            try {
                return action.call();
            } catch (java.net.SocketTimeoutException | IllegalStateException e) {
                if (retries < maxRetries) {
                    retries++;
                    baseLogger.error("Request timed out. Retrying... Attempt " + retries, e);
                    try {
                        Thread.sleep(retryDelayMillis * retries);
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        throw new IllegalStateException("Retry interrupted", ie);
                    }
                } else {
                    baseLogger.error("Max retries reached. Failing request.", e);
                    throw new RuntimeException(e);
                }
            } catch (Exception e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        }
    }
}
