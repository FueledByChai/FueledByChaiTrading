/*
 * To change this license header, choose License Headers in Project Properties.
 * To change this template file, choose Tools | Templates
 * and open the template in the editor.
 */
package com.fueledbychai.util;

import java.io.IOException;
import java.time.ZonedDateTime;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
        System.out.println(
                "DEBUG: Checking exception: " + throwable.getClass().getName() + " - " + throwable.getMessage());

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

        // ResponseException - check HTTP status code (using class name to avoid
        // classloader issues)
        if ("com.fueledbychai.data.ResponseException".equals(throwable.getClass().getName())) {
            try {
                // Use reflection to get the status code to avoid classloader issues
                java.lang.reflect.Method getStatusCodeMethod = throwable.getClass().getMethod("getStatusCode");
                int statusCode = (Integer) getStatusCodeMethod.invoke(throwable);
                System.out.println("DEBUG: Found ResponseException with status code: " + statusCode);

                // Server errors (5xx) are retryable
                if (statusCode >= 500 && statusCode < 600) {
                    System.out.println("DEBUG: Returning true for server error: " + statusCode);
                    logger.debug("Retryable server error: HTTP {}", statusCode);
                    return true;
                }

                // Rate limiting (429) is retryable
                if (statusCode == 429) {
                    logger.debug("Rate limiting detected, will retry: HTTP {}", statusCode);
                    return true;
                }

                // Request timeout (408) is retryable
                if (statusCode == 408) {
                    logger.debug("Request timeout detected, will retry: HTTP {}", statusCode);
                    return true;
                }

                // Client errors (4xx except 408 and 429) are not retryable
                if (statusCode >= 400 && statusCode < 500) {
                    logger.debug("Client error, not retrying: HTTP {}", statusCode);
                    return false;
                }

                // Check if it has a retryable cause (e.g., IOException)
                if (throwable.getCause() != null && isRetryableException(throwable.getCause())) {
                    logger.debug("ResponseException with retryable cause: {}",
                            throwable.getCause().getClass().getSimpleName());
                    return true;
                }

                logger.debug("Non-retryable ResponseException: HTTP {}", statusCode);
                return false;
            } catch (Exception e) {
                logger.warn("Failed to get status code from ResponseException: {}", e.getMessage());
                // Fall through to other checks
            }
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

        // RuntimeException - check the cause (common wrapping pattern)
        if (throwable instanceof RuntimeException) {
            RuntimeException re = (RuntimeException) throwable;
            if (re.getCause() != null) {
                logger.debug("RuntimeException with cause, checking cause: {}",
                        re.getCause().getClass().getSimpleName());
                return isRetryableException(re.getCause());
            }
        }

        // By default, don't retry unknown exceptions
        logger.debug("Non-retryable exception: {}", throwable.getClass().getSimpleName());
        return false;
    }

}
