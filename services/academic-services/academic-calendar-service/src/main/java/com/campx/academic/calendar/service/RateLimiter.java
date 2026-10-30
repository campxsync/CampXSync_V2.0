package com.campx.academic.calendar.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory Token-Bucket rate limiter enforcing request throttling (US-038).
 */
public class RateLimiter {

    private final int capacity;
    private final int refillRatePerSecond;
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, int refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    public RateLimitResult tryAcquire(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillRatePerSecond));
        return bucket.tryConsume();
    }

    public static class TokenBucket {
        private final int capacity;
        private final int refillRatePerSecond;
        private double tokens;
        private long lastRefillTime;

        public TokenBucket(int capacity, int refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized RateLimitResult tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new RateLimitResult(true, (long) tokens, capacity, 0);
            }
            long retryAfterSeconds = Math.max(1, (long) Math.ceil((1.0 - tokens) / refillRatePerSecond));
            return new RateLimitResult(false, 0, capacity, retryAfterSeconds);
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double seconds = (now - lastRefillTime) / 1000.0;
            if (seconds > 0) {
                tokens = Math.min(capacity, tokens + seconds * refillRatePerSecond);
                lastRefillTime = now;
            }
        }
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final long remaining;
        private final long limit;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean allowed, long remaining, long limit, long retryAfterSeconds) {
            this.allowed = allowed;
            this.remaining = remaining;
            this.limit = limit;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isAllowed() { return allowed; }
        public long getRemaining() { return remaining; }
        public long getLimit() { return limit; }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }
}
