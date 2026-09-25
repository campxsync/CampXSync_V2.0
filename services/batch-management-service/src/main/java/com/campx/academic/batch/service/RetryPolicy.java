package com.campx.academic.batch.service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Random;

/**
 * Enterprise Bounded Exponential Backoff Retry Policy with Jitter (Story 63, §55).
 * - 4xx client/validation/auth errors are NEVER retried.
 * - 5xx server errors, 408 Request Timeout, 429 Rate Limit, and network IO failures ARE retried.
 */
public class RetryPolicy {

    private final int maxRetries;
    private final long baseDelayMs;
    private final long maxDelayMs;
    private final double jitterFactor;
    private final Random random = new Random();

    public RetryPolicy() {
        this(5, 1000L, 30000L, 0.1);
    }

    public RetryPolicy(int maxRetries, long baseDelayMs, long maxDelayMs, double jitterFactor) {
        this.maxRetries = maxRetries;
        this.baseDelayMs = baseDelayMs;
        this.maxDelayMs = maxDelayMs;
        this.jitterFactor = jitterFactor;
    }

    public long getDelayMs(int attempt) {
        if (attempt <= 0) {
            return baseDelayMs;
        }
        long exponential = (long) (baseDelayMs * Math.pow(2, attempt));
        long bounded = Math.min(exponential, maxDelayMs);
        long jitter = (long) (bounded * jitterFactor * random.nextDouble());
        return bounded + jitter;
    }

    public boolean isRetryable(int httpStatus) {
        return httpStatus >= 500 || httpStatus == 408 || httpStatus == 429;
    }

    public boolean isRetryableException(Throwable throwable) {
        if (throwable instanceof SocketTimeoutException) {
            return true;
        }
        if (throwable instanceof IOException) {
            return true;
        }
        return false;
    }

    public int getMaxRetries() {
        return maxRetries;
    }

    public long getBaseDelayMs() {
        return baseDelayMs;
    }

    public long getMaxDelayMs() {
        return maxDelayMs;
    }
}
