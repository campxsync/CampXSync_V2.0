package com.campx.academic.subject.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * In-Memory Token Bucket Rate Limiter (Story 75, §48, §51).
 * Enforces per-client/tenant request limits and tracks remaining tokens and reset intervals.
 */
public class RateLimiter {

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

        public boolean isAllowed() {
            return allowed;
        }

        public int getLimit() {
            return limit;
        }

        public int getRemaining() {
            return remaining;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    private static class TokenBucket {
        private final int capacity;
        private final long refillIntervalMs;
        private final AtomicInteger tokens;
        private volatile long lastRefillTime;

        public TokenBucket(int capacity, long refillIntervalMs) {
            this.capacity = capacity;
            this.refillIntervalMs = refillIntervalMs;
            this.tokens = new AtomicInteger(capacity);
            this.lastRefillTime = System.currentTimeMillis();
        }

        public synchronized RateLimitResult tryConsume() {
            long now = System.currentTimeMillis();
            long timePassed = now - lastRefillTime;

            if (timePassed >= refillIntervalMs) {
                tokens.set(capacity);
                lastRefillTime = now;
            }

            int current = tokens.get();
            if (current > 0) {
                int remaining = tokens.decrementAndGet();
                return new RateLimitResult(true, capacity, Math.max(0, remaining), 0);
            } else {
                long retryAfter = Math.max(1, (refillIntervalMs - (now - lastRefillTime)) / 1000);
                return new RateLimitResult(false, capacity, 0, retryAfter);
            }
        }
    }

    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();
    private final int defaultCapacity;
    private final long defaultWindowMs;

    public RateLimiter() {
        this(120, 60000); // 120 requests per minute default
    }

    public RateLimiter(int capacity, long windowMs) {
        this.defaultCapacity = capacity;
        this.defaultWindowMs = windowMs;
    }

    public RateLimitResult tryAcquire(String clientKey) {
        if (clientKey == null || clientKey.trim().isEmpty()) {
            clientKey = "anonymous";
        }
        TokenBucket bucket = buckets.computeIfAbsent(clientKey, k -> new TokenBucket(defaultCapacity, defaultWindowMs));
        return bucket.tryConsume();
    }

    public void reset() {
        buckets.clear();
    }
}
