package com.campx.student.service;

import com.campx.student.security.UserSecurityContext;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token Bucket Rate Limiter for bounded search, export, and profile access (STM01-US-038).
 * Enforces rate limits using an in-memory token bucket algorithm.
 *
 * Scope & Limitations:
 * Enforcement is process-local per JVM instance, providing burst protection and preventing client
 * starvation within each running service instance. State is held in-memory and does NOT coordinate
 * across independent JVM processes or distributed container replicas. Fleet-wide distributed
 * rate limiting across horizontally-scaled replicas requires an external distributed cache
 * (such as Redis) or API Gateway ingress rate limiting, which is intentionally not configured.
 */
public class RateLimiter {

    /**
     * Scope of quota enforcement.
     */
    public enum RateLimitScope {
        /**
         * Enforced per tenant and authenticated caller (default for user operations).
         */
        TENANT_AND_CALLER,

        /**
         * Enforced per tenant / college organization.
         */
        TENANT,

        /**
         * Enforced per authenticated caller across tenants.
         */
        CALLER,

        /**
         * Global protective cap per route / operation.
         */
        GLOBAL_ROUTE
    }

    /**
     * Result of a rate limit check.
     */
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

    /**
     * Pluggable store interface for rate limit token buckets.
     */
    public interface RateLimitStore {
        RateLimitResult tryAcquire(String key, int capacity, long refillIntervalMs);
        void reset();
    }

    /**
     * Thread-safe in-memory token bucket implementation.
     */
    public static class TokenBucket {
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

    /**
     * Process-local rate limit store.
     * Note: State is local to a single JVM process; does not share quota across replicas.
     */
    public static class ProcessLocalRateLimitStore implements RateLimitStore {
        private final Map<String, TokenBucket> buckets = new ConcurrentHashMap<>();

        @Override
        public RateLimitResult tryAcquire(String key, int capacity, long refillIntervalMs) {
            TokenBucket bucket = buckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillIntervalMs));
            return bucket.tryConsume();
        }

        @Override
        public void reset() {
            buckets.clear();
        }
    }

    /**
     * In-process shared rate limit store singleton.
     * Coordinates token buckets across multiple controllers or components within the same JVM process.
     *
     * Note: This store coordinates state only within a single JVM process and does NOT coordinate across
     * independent OS processes, container replicas, or distributed JVMs. Adding independent service container
     * instances will not share this in-memory state.
     */
    public static class SharedRateLimitStore implements RateLimitStore {
        private static final SharedRateLimitStore INSTANCE = new SharedRateLimitStore();
        private final Map<String, TokenBucket> sharedBuckets = new ConcurrentHashMap<>();

        public static SharedRateLimitStore getInstance() {
            return INSTANCE;
        }

        @Override
        public RateLimitResult tryAcquire(String key, int capacity, long refillIntervalMs) {
            TokenBucket bucket = sharedBuckets.computeIfAbsent(key, k -> new TokenBucket(capacity, refillIntervalMs));
            return bucket.tryConsume();
        }

        @Override
        public void reset() {
            sharedBuckets.clear();
        }
    }

    private final RateLimitStore store;
    private final int defaultCapacity;
    private final long defaultWindowMs;

    public RateLimiter() {
        this(120, 60000); // 120 requests per minute default
    }

    public RateLimiter(int capacity, long windowMs) {
        this(new ProcessLocalRateLimitStore(), capacity, windowMs);
    }

    public RateLimiter(RateLimitStore store, int capacity, long windowMs) {
        this.store = store != null ? store : new ProcessLocalRateLimitStore();
        this.defaultCapacity = capacity;
        this.defaultWindowMs = windowMs;
    }

    /**
     * Creates a RateLimiter backed by the in-process shared store singleton (shared across components within the same JVM).
     */
    public static RateLimiter createShared(int capacity, long windowMs) {
        return new RateLimiter(SharedRateLimitStore.getInstance(), capacity, windowMs);
    }

    /**
     * Creates a RateLimiter explicitly scoped to a single process.
     */
    public static RateLimiter createProcessLocal(int capacity, long windowMs) {
        return new RateLimiter(new ProcessLocalRateLimitStore(), capacity, windowMs);
    }

    public RateLimitResult tryAcquire(String clientKey) {
        if (clientKey == null || clientKey.trim().isEmpty()) {
            clientKey = "anonymous";
        }
        return store.tryAcquire(clientKey, defaultCapacity, defaultWindowMs);
    }

    public RateLimitResult tryAcquire(String operation, UserSecurityContext context) {
        return tryAcquire(RateLimitScope.TENANT_AND_CALLER, operation, context);
    }

    public RateLimitResult tryAcquire(RateLimitScope scope, String operation, UserSecurityContext context) {
        String clientKey = buildRateLimitKey(scope, operation, context);
        return tryAcquire(clientKey);
    }

    /**
     * Constructs a deterministic, trusted rate limit key based on authenticated identity and operation.
     */
    public static String buildRateLimitKey(RateLimitScope scope, String operation, UserSecurityContext context) {
        String op = (operation != null && !operation.trim().isEmpty()) ? operation.trim().toUpperCase() : "DEFAULT";
        if (context == null || context.getTenantId() == null) {
            return op + ":ANON";
        }

        UUID tenantId = context.getTenantId();
        UUID userId = context.getUserId();

        RateLimitScope actualScope = scope != null ? scope : RateLimitScope.TENANT_AND_CALLER;
        switch (actualScope) {
            case TENANT:
                return op + ":tenant:" + tenantId;
            case CALLER:
                return op + ":caller:" + (userId != null ? userId : "anon");
            case GLOBAL_ROUTE:
                return op + ":global";
            case TENANT_AND_CALLER:
            default:
                return op + ":" + tenantId + ":" + (userId != null ? userId : "anon");
        }
    }

    public RateLimitStore getStore() {
        return store;
    }

    public boolean isShared() {
        return store instanceof SharedRateLimitStore;
    }

    public void reset() {
        store.reset();
    }
}
