package com.fueledbychai.broker.paradex;

import java.io.IOException;
import java.time.Duration;
import java.util.concurrent.TimeoutException;

import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryConfig;
import io.github.resilience4j.timelimiter.TimeLimiter;
import io.github.resilience4j.timelimiter.TimeLimiterConfig;

enum PlaceResultStatus {
    ACCEPTED, REJECTED, SUBMITTED_UNKNOWN
}

record PlaceOrderResult(PlaceResultStatus status, String clientOrderId, String exchangeOrderId, String message) {
}

record OrderRequest(String clientOrderId, String symbol, double qty, double price, boolean isBuy) {
}

record ExchangeResponse(boolean accepted, boolean rejected, boolean duplicateOnIdempotency, String exchangeOrderId,
        String rejectionReason) {
}

record OrderLookupResult(boolean found, boolean accepted, boolean rejected, String exchangeOrderId, String reason) {
    boolean isAccepted() {
        return found && accepted;
    }

    boolean isRejected() {
        return found && rejected;
    }
}

class SemanticOrderException extends RuntimeException {
    public SemanticOrderException(String msg) {
        super(msg);
    }
}

class AlreadyPlacedException extends RuntimeException {
    public AlreadyPlacedException(String msg) {
        super(msg);
    }
}

public class TradePlacementService {

    private final TimeLimiter timeLimiter = TimeLimiter
            .of(TimeLimiterConfig.custom().timeoutDuration(Duration.ofMillis(900)) // bound latency
                    .cancelRunningFuture(true).build());

    private final Retry retry = Retry.of("placeOrderRetry", RetryConfig.<PlaceOrderResult>custom().maxAttempts(3) // OK
                                                                                                                  // with
                                                                                                                  // idempotency
            .waitDuration(Duration.ofMillis(120)).retryOnException(ex -> ex instanceof IOException)
            .ignoreExceptions(TimeoutException.class, AlreadyPlacedException.class, SemanticOrderException.class)
            .build());

}
