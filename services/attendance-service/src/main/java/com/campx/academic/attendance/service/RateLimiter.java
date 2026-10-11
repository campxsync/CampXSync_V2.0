package com.campx.academic.attendance.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter for API clients and actors.
 */
public class RateLimiter {

    private final int capacity;
    private final double refillRatePerSecond;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    public RateLimitResult tryAcquire(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillRatePerSecond));
        return bucket.tryConsume();
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final int limit;
        private final int remaining;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean allowed, int limit, int remaining, long retryAfterSeconds) {
            this.allowed = allowed;
            this.limit = limit;
            this.remaining = remaining;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isAllowed() { return allowed; }
        public int getLimit() { return limit; }
        public int getRemaining() { return remaining; }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    private static class TokenBucket {
        private final int capacity;
        private final double refillRate;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(int capacity, double refillRate) {
            this.capacity = capacity;
            this.refillRate = refillRate;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized RateLimitResult tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new RateLimitResult(true, capacity, (int) tokens, 0);
            } else {
                double missing = 1.0 - tokens;
                long retryAfter = (long) Math.ceil(missing / refillRate);
                if (retryAfter < 1) retryAfter = 1;
                return new RateLimitResult(false, capacity, 0, retryAfter);
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double seconds = (now - lastRefillTimestamp) / 1000.0;
            if (seconds > 0) {
                tokens = Math.min(capacity, tokens + seconds * refillRate);
                lastRefillTimestamp = now;
            }
        }
    }
}
