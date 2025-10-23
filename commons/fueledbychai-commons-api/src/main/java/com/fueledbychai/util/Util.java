/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.util;

import java.io.IOException;
import java.time.ZonedDateTime;

import com.fueledbychai.data.FueledByChaiException;

/**
 *
 *  
 */
public class Util {

    private static final org.slf4j.Logger logger = org.slf4j.LoggerFactory.getLogger(Util.class);

    public static int roundLot(int size, int minimumSize) {
        if (minimumSize == 0) {
            throw new FueledByChaiException("Cannot divide by zero");
        }
        int increments = (int) Math.round((double) size / (double) minimumSize);
        return increments * minimumSize;
    }

    public static String parseTicker(String ticker) {
        if (ticker.indexOf(".") != -1) {
            ticker = ticker.substring(0, ticker.indexOf("."));
        }
        if (ticker.startsWith("0")) {
            return parseTicker(ticker.substring(1));
        } else {
            return ticker;
        }
    }

    public static ZonedDateTime convertEpochToZonedDateTime(long epochMillis) {
        return ZonedDateTime.ofInstant(java.time.Instant.ofEpochMilli(epochMillis), java.time.ZoneId.of("UTC"));
    }

    /**
     * Determines if an exception should trigger a retry. Retries on
     * temporary/network errors, but not on client errors like 404, 400, etc.
     */
    public static boolean isRetryableException(Throwable throwable) {
        // Network-related exceptions are typically retryable
        if (throwable instanceof java.net.SocketTimeoutException || throwable instanceof java.net.ConnectException
                || throwable instanceof java.net.UnknownHostException
                || throwable instanceof java.net.SocketException) {
            logger.debug("Retryable network exception: {}", throwable.getMessage());
            return true;
        }

        // IO exceptions with specific messages that indicate temporary issues
        if (throwable instanceof IOException) {
            String message = throwable.getMessage();
            if (message != null) {
                // Server errors (5xx) are typically retryable
                if (message.contains("HTTP 5") || message.contains("timeout") || message.contains("connection reset")
                        || message.contains("connection refused")) {
                    logger.debug("Retryable HTTP exception: {}", message);
                    return true;
                }

                // Rate limiting (429) should be retried
                if (message.contains("HTTP 429") || message.contains("rate limit")) {
                    logger.debug("Rate limiting detected, will retry: {}", message);
                    return true;
                }

                // Client errors (4xx) are typically permanent - don't retry
                if (message.contains("HTTP 400") || message.contains("HTTP 401") || message.contains("HTTP 403")
                        || message.contains("HTTP 404")) {
                    logger.debug("Client error, not retrying: {}", message);
                    return false;
                }
            }
            // Other IOExceptions might be retryable
            logger.debug("IO exception, will retry: {}", message);
            return true;
        }

        // FueledByChaiException - check the underlying cause
        if (throwable instanceof FueledByChaiException) {
            FueledByChaiException fbc = (FueledByChaiException) throwable;
            String message = fbc.getMessage();

            // Check if it's a wrapped network/timeout error
            if (message != null
                    && (message.contains("timeout") || message.contains("connection") || message.contains("network"))) {
                logger.debug("Retryable FueledByChai exception: {}", message);
                return true;
            }

            // Check if it has a retryable cause
            if (fbc.getCause() != null && isRetryableException(fbc.getCause())) {
                return true;
            }

            logger.debug("Non-retryable FueledByChai exception: {}", message);
            return false;
        }

        // By default, don't retry unknown exceptions
        logger.debug("Non-retryable exception: {}", throwable.getClass().getSimpleName());
        return false;
    }

}
