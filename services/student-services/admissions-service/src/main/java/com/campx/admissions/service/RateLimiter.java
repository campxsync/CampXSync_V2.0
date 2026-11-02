package com.campx.admissions.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Token bucket rate limiter protecting public applicant submission and document
 * upload endpoints (EN-007).
 */
public class RateLimiter {

    private final int capacity;
    private final double refillTokensPerSecond;

    // Key: tenantId + ":" + callerKey + ":" + operation
    private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

    public RateLimiter(int capacity, double refillTokensPerSecond) {
        this.capacity = capacity;
        this.refillTokensPerSecond = refillTokensPerSecond;
    }

    public boolean tryAcquire(String key) {
        TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillTokensPerSecond));
        return bucket.tryConsume();
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

        public synchronized boolean tryConsume() {
            refill();
            if (tokens >= 1.0) {
                tokens -= 1.0;
                return true;
            }
            return false;
        }

        private void refill() {
            long now = System.currentTimeMillis();
            double seconds = (now - lastRefillTimestamp) / 1000.0;
            tokens = Math.min(capacity, tokens + seconds * refillRate);
            lastRefillTimestamp = now;
        }
    }
}
