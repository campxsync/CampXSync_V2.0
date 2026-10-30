package com.campx.academic.analytics.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * High-performance in-memory Token Bucket Rate Limiter
 * for ACD-10: Reporting & Analytics Service.
 */
public class RateLimiter {

    private final long capacity;
    private final double refillRatePerSecond;
    private final ConcurrentMap<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(long capacity, double refillRatePerSecond) {
        this.capacity = capacity;
        this.refillRatePerSecond = refillRatePerSecond;
    }

    public RateLimitResult tryAcquire(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillRatePerSecond));
        return bucket.tryConsume();
    }

    public static class RateLimitResult {
        private final boolean allowed;
        private final long limit;
        private final long remaining;
        private final long retryAfterSeconds;

        public RateLimitResult(boolean allowed, long limit, long remaining, long retryAfterSeconds) {
            this.allowed = allowed;
            this.limit = limit;
            this.remaining = remaining;
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public boolean isAllowed() { return allowed; }
        public long getLimit() { return limit; }
        public long getRemaining() { return remaining; }
        public long getRetryAfterSeconds() { return retryAfterSeconds; }
    }

    private static class TokenBucket {
        private final long capacity;
        private final double refillRatePerSecond;
        private double tokens;
        private long lastRefillTimestamp;

        public TokenBucket(long capacity, double refillRatePerSecond) {
            this.capacity = capacity;
            this.refillRatePerSecond = refillRatePerSecond;
            this.tokens = capacity;
            this.lastRefillTimestamp = System.currentTimeMillis();
        }

        public synchronized RateLimitResult tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return new RateLimitResult(true, capacity, (long) tokens, 0);
            } else {
                long waitMillis = (long) Math.ceil((1.0 - tokens) / (refillRatePerSecond / 1000.0));
                long retryAfterSec = Math.max(1, waitMillis / 1000);
                return new RateLimitResult(false, capacity, 0, retryAfterSec);
            }
        }

        private void refill() {
            long now = System.currentTimeMillis();
            long elapsed = now - lastRefillTimestamp;
            if (elapsed > 0) {
                double addedTokens = (elapsed / 1000.0) * refillRatePerSecond;
                this.tokens = Math.min(capacity, this.tokens + addedTokens);
                this.lastRefillTimestamp = now;
            }
        }
    }
}
