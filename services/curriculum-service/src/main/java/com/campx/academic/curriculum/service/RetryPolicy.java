package com.campx.academic.curriculum.service;

import java.io.IOException;
import java.net.SocketTimeoutException;
import java.util.Random;

/**
 * Enterprise Bounded Exponential Backoff Retry Policy with Jitter (Story 54, 71).
 * Specifically classifies transient vs non-retryable errors:
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

    /**
     * Computes the exponential backoff delay in milliseconds for a given attempt, capped at maxDelayMs.
     *
     * @param attempt 0-indexed attempt count
     * @return delay in milliseconds including pseudo-random jitter
     */
    public long getDelayMs(int attempt) {
        if (attempt <= 0) {
            return baseDelayMs;
        }
        long exponential = (long) (baseDelayMs * Math.pow(2, attempt));
        long bounded = Math.min(exponential, maxDelayMs);
        long jitter = (long) (bounded * jitterFactor * random.nextDouble());
        return bounded + jitter;
    }

    /**
     * Determines whether an HTTP status code represents a retryable condition.
     *
     * @param httpStatus HTTP response status code
     * @return {@code true} if retryable (5xx, 408, 429), {@code false} for client errors (400, 401, 403, 404, 409, 422)
     */
    public boolean isRetryable(int httpStatus) {
        return httpStatus >= 500 || httpStatus == 408 || httpStatus == 429;
    }

    /**
     * Determines whether an exception represents a retryable transient failure.
     *
     * @param t throwable to inspect
     * @return {@code true} for network IO or timeout exceptions
     */
    public boolean isRetryable(Throwable t) {
        if (t == null) return false;
        return t instanceof IOException
                || t instanceof SocketTimeoutException
                || (t.getCause() != null && isRetryable(t.getCause()));
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
