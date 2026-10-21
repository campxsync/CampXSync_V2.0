package com.campx.platform.outbox;

import org.junit.Test;

import java.time.Duration;
import java.time.Instant;

import static org.junit.Assert.*;

public class OutboxRetryPolicyTest {

    @Test
    public void testExponentialBackoffCalculations() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(5, 2, 2.0, 300, false);

        // Attempt 1: 2 * (2.0 ^ 0) = 2 seconds
        assertEquals(2, policy.computeBackoffSeconds(1));

        // Attempt 2: 2 * (2.0 ^ 1) = 4 seconds
        assertEquals(4, policy.computeBackoffSeconds(2));

        // Attempt 3: 2 * (2.0 ^ 2) = 8 seconds
        assertEquals(8, policy.computeBackoffSeconds(3));

        // Attempt 4: 2 * (2.0 ^ 3) = 16 seconds
        assertEquals(16, policy.computeBackoffSeconds(4));

        // Attempt 5: 2 * (2.0 ^ 4) = 32 seconds
        assertEquals(32, policy.computeBackoffSeconds(5));
    }

    @Test
    public void testCappedBackoffSeconds() {
        // Initial 10s, multiplier 3.0, max backoff 50s
        OutboxRetryPolicy policy = new OutboxRetryPolicy(10, 10, 3.0, 50, false);

        assertEquals(10, policy.computeBackoffSeconds(1)); // 10
        assertEquals(30, policy.computeBackoffSeconds(2)); // 30
        assertEquals(50, policy.computeBackoffSeconds(3)); // min(90, 50) = 50
        assertEquals(50, policy.computeBackoffSeconds(10)); // capped at 50
    }

    @Test
    public void testExhaustionDetection() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(3, 2, 2.0, 60, false);

        assertFalse(policy.isExhausted(0));
        assertFalse(policy.isExhausted(1));
        assertFalse(policy.isExhausted(2));
        assertTrue(policy.isExhausted(3));
        assertTrue(policy.isExhausted(4));
    }

    @Test
    public void testComputeNextAttemptTime() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(5, 5, 2.0, 300, false);

        Instant before = Instant.now();
        Instant next = policy.computeNextAttempt(1);
        Instant after = Instant.now();

        long diffSeconds = Duration.between(before, next).getSeconds();
        assertTrue(diffSeconds >= 4 && diffSeconds <= 6);
    }

    @Test
    public void testHandlesExtremeAttemptCountWithoutOverflow() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(5, 2, 2.0, 300, false);
        assertEquals(300, policy.computeBackoffSeconds(60));
        assertEquals(300, policy.computeBackoffSeconds(1000));
        assertEquals(300, policy.computeBackoffSeconds(Integer.MAX_VALUE));

        Instant next = policy.calculateNextAttemptAt(Integer.MAX_VALUE, Instant.EPOCH);
        assertEquals(Instant.EPOCH.plusSeconds(300), next);
    }

    @Test
    public void testHandlesNonPositiveAttemptCountSafely() {
        OutboxRetryPolicy policy = new OutboxRetryPolicy(5, 2, 2.0, 300, false);
        assertEquals(2, policy.computeBackoffSeconds(0));
        assertEquals(2, policy.computeBackoffSeconds(-5));

        Instant next = policy.calculateNextAttemptAt(-1, Instant.EPOCH);
        assertEquals(Instant.EPOCH.plusSeconds(2), next);
    }
}
