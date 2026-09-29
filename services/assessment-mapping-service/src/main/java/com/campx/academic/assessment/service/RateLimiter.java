package com.campx.academic.assessment.service;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Token bucket / fixed window rate limiter for ACD-09 endpoints.
 */
public class RateLimiter {

    private final int maxRequestsPerMinute;
    private final ConcurrentHashMap<String, WindowCounter> clientCounters = new ConcurrentHashMap<>();

    public RateLimiter(int maxRequestsPerMinute) {
        this.maxRequestsPerMinute = maxRequestsPerMinute;
    }

    public boolean tryAcquire(String clientKey) {
        long currentMinute = System.currentTimeMillis() / 60000;
        WindowCounter counter = clientCounters.compute(clientKey, (k, v) -> {
            if (v == null || v.minuteWindow != currentMinute) {
                return new WindowCounter(currentMinute, 1);
            }
            v.counter.incrementAndGet();
            return v;
        });
        return counter.counter.get() <= maxRequestsPerMinute;
    }

    private static class WindowCounter {
        final long minuteWindow;
        final AtomicInteger counter;

        WindowCounter(long minuteWindow, int initialCount) {
            this.minuteWindow = minuteWindow;
            this.counter = new AtomicInteger(initialCount);
        }
    }
}
