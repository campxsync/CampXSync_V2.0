package com.campx.platform.outbox;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

/**
 * Governed retry, exponential backoff, and dead-letter exhaustion policy for outbox event publication.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class OutboxRetryPolicy {

    public static final int DEFAULT_MAX_ATTEMPTS = 5;
    public static final long DEFAULT_INITIAL_BACKOFF_SECONDS = 2;
    public static final double DEFAULT_BACKOFF_MULTIPLIER = 2.0;
    public static final long DEFAULT_MAX_BACKOFF_SECONDS = 300; // 5 minutes

    private final int maxAttempts;
    private final long initialBackoffSeconds;
    private final double backoffMultiplier;
    private final long maxBackoffSeconds;

    private final boolean jitter;

    public OutboxRetryPolicy() {
        this(DEFAULT_MAX_ATTEMPTS, DEFAULT_INITIAL_BACKOFF_SECONDS, DEFAULT_BACKOFF_MULTIPLIER, DEFAULT_MAX_BACKOFF_SECONDS, false);
    }

    public OutboxRetryPolicy(int maxAttempts, long initialBackoffSeconds, double backoffMultiplier, long maxBackoffSeconds) {
        this(maxAttempts, initialBackoffSeconds, backoffMultiplier, maxBackoffSeconds, false);
    }

    public OutboxRetryPolicy(int maxAttempts, long initialBackoffSeconds, double backoffMultiplier, long maxBackoffSeconds, boolean jitter) {
        if (maxAttempts <= 0) throw new IllegalArgumentException("maxAttempts must be > 0");
        if (initialBackoffSeconds <= 0) throw new IllegalArgumentException("initialBackoffSeconds must be > 0");
        if (backoffMultiplier < 1.0) throw new IllegalArgumentException("backoffMultiplier must be >= 1.0");
        if (maxBackoffSeconds < initialBackoffSeconds) throw new IllegalArgumentException("maxBackoffSeconds must be >= initialBackoffSeconds");

        this.maxAttempts = maxAttempts;
        this.initialBackoffSeconds = initialBackoffSeconds;
        this.backoffMultiplier = backoffMultiplier;
        this.maxBackoffSeconds = maxBackoffSeconds;
        this.jitter = jitter;
    }

    public int getMaxAttempts() { return maxAttempts; }
    public long getInitialBackoffSeconds() { return initialBackoffSeconds; }
    public double getBackoffMultiplier() { return backoffMultiplier; }
    public long getMaxBackoffSeconds() { return maxBackoffSeconds; }
    public boolean isJitter() { return jitter; }

    /**
     * Computes the exponential backoff delay in seconds for a 1-based attempt index.
     *
     * @param attemptCount 1-based attempt count (1 for first retry)
     * @return delay in seconds, capped at maxBackoffSeconds
     */
    public long computeBackoffSeconds(int attemptCount) {
        if (attemptCount <= 0) {
            return initialBackoffSeconds;
        }
        if (attemptCount > 30) {
            return maxBackoffSeconds;
        }
        double raw = initialBackoffSeconds * Math.pow(backoffMultiplier, attemptCount - 1);
        if (Double.isInfinite(raw) || Double.isNaN(raw) || raw > maxBackoffSeconds) {
            return maxBackoffSeconds;
        }
        long delaySeconds = (long) raw;
        if (delaySeconds < 0 || delaySeconds > maxBackoffSeconds) {
            return maxBackoffSeconds;
        }
        return Math.max(initialBackoffSeconds, delaySeconds);
    }

    /**
     * Computes the next attempt timestamp for a 1-based attempt count.
     *
     * @param attemptCount 1-based attempt count
     * @return Instant for the next attempt
     */
    public Instant computeNextAttempt(int attemptCount) {
        return Instant.now().plus(computeBackoffSeconds(attemptCount), ChronoUnit.SECONDS);
    }

    /**
     * Determines whether the given attempt count has reached or exceeded maximum retries.
     *
     * @param attemptCount the number of attempts executed so far
     * @return true if retries are exhausted and event should be moved to dead-letter queue
     */
    public boolean isExhausted(int attemptCount) {
        return attemptCount >= maxAttempts;
    }

    /**
     * Calculates the next attempt timestamp using exponential backoff.
     *
     * @param attemptCount current 0-indexed attempt count
     * @return Instant for the next scheduled dispatch attempt
     */
    public Instant calculateNextAttemptAt(int attemptCount) {
        return calculateNextAttemptAt(attemptCount, Instant.now());
    }

    /**
     * Calculates the next attempt timestamp relative to the provided base time.
     *
     * @param attemptCount current attempt count
     * @param baseTime     base timestamp
     * @return Instant for the next attempt
     */
    public Instant calculateNextAttemptAt(int attemptCount, Instant baseTime) {
        Instant base = baseTime != null ? baseTime : Instant.now();
        if (attemptCount <= 0) {
            return base.plus(initialBackoffSeconds, ChronoUnit.SECONDS);
        }
        long delaySeconds;
        if (attemptCount > 30) {
            delaySeconds = maxBackoffSeconds;
        } else {
            double raw = initialBackoffSeconds * Math.pow(backoffMultiplier, attemptCount);
            if (Double.isInfinite(raw) || Double.isNaN(raw) || raw > maxBackoffSeconds) {
                delaySeconds = maxBackoffSeconds;
            } else {
                delaySeconds = (long) raw;
                if (delaySeconds < 0 || delaySeconds > maxBackoffSeconds) {
                    delaySeconds = maxBackoffSeconds;
                }
            }
        }
        return base.plus(Math.max(initialBackoffSeconds, delaySeconds), ChronoUnit.SECONDS);
    }
}
