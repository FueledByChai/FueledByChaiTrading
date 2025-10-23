package com.fueledbychai.paradex.common.api;

import java.time.Duration;

/**
 * Configuration class for resilient Paradex instrument lookup retry behavior.
 * This allows customization of retry parameters if the defaults don't suit your
 * needs.
 */
public class ParadexLookupRetryConfig {

    private int maxAttempts = 6; // 1 original + 3 retries
    private Duration waitDuration = Duration.ofMillis(500); // 500 ms between retries
    private boolean exponentialBackoff = true;
    private double backoffMultiplier = 2.0;
    private Duration maxWaitDuration = Duration.ofSeconds(10);

    public static ParadexLookupRetryConfig defaultConfig() {
        return new ParadexLookupRetryConfig();
    }

    public static ParadexLookupRetryConfig aggressiveConfig() {
        ParadexLookupRetryConfig config = new ParadexLookupRetryConfig();
        config.maxAttempts = 6; // More retries for aggressive retry
        config.waitDuration = Duration.ofMillis(500); // Shorter wait
        config.exponentialBackoff = true;
        return config;
    }

    public static ParadexLookupRetryConfig conservativeConfig() {
        ParadexLookupRetryConfig config = new ParadexLookupRetryConfig();
        config.maxAttempts = 2; // Fewer retries for conservative approach
        config.waitDuration = Duration.ofMillis(2000); // Longer wait
        return config;
    }

    // Getters and Setters

    public int getMaxAttempts() {
        return maxAttempts;
    }

    public ParadexLookupRetryConfig setMaxAttempts(int maxAttempts) {
        this.maxAttempts = maxAttempts;
        return this;
    }

    public Duration getWaitDuration() {
        return waitDuration;
    }

    public ParadexLookupRetryConfig setWaitDuration(Duration waitDuration) {
        this.waitDuration = waitDuration;
        return this;
    }

    public boolean isExponentialBackoff() {
        return exponentialBackoff;
    }

    public ParadexLookupRetryConfig setExponentialBackoff(boolean exponentialBackoff) {
        this.exponentialBackoff = exponentialBackoff;
        return this;
    }

    public double getBackoffMultiplier() {
        return backoffMultiplier;
    }

    public ParadexLookupRetryConfig setBackoffMultiplier(double backoffMultiplier) {
        this.backoffMultiplier = backoffMultiplier;
        return this;
    }

    public Duration getMaxWaitDuration() {
        return maxWaitDuration;
    }

    public ParadexLookupRetryConfig setMaxWaitDuration(Duration maxWaitDuration) {
        this.maxWaitDuration = maxWaitDuration;
        return this;
    }
}