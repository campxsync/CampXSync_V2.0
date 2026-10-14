package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.InvalidStatusTransitionException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit test suite exhaustively verifying the single transition table in {@link TenantProvisioningStateMachine}.
 */
public class TenantProvisioningStateMachineTest {

    // Legal standard transitions
    @Test
    public void testLegalStandardTransitions() {
        TenantProvisioningStateMachine.validateTransition("REQUESTED", "IN_PROGRESS");
        TenantProvisioningStateMachine.validateTransition("IN_PROGRESS", "COMPLETED");
        TenantProvisioningStateMachine.validateTransition("IN_PROGRESS", "FAILED");

        assertTrue(TenantProvisioningStateMachine.isLegalTransition("REQUESTED", "IN_PROGRESS", false));
        assertTrue(TenantProvisioningStateMachine.isLegalTransition("IN_PROGRESS", "COMPLETED", false));
        assertTrue(TenantProvisioningStateMachine.isLegalTransition("IN_PROGRESS", "FAILED", false));
    }

    // Authorized retry transitions
    @Test
    public void testLegalRetryTransitions() {
        TenantProvisioningStateMachine.validateTransition("FAILED", "IN_PROGRESS", true);
        assertTrue(TenantProvisioningStateMachine.isLegalTransition("FAILED", "IN_PROGRESS", true));
    }

    // FAILED -> IN_PROGRESS rejected outside of retry
    @Test(expected = InvalidStatusTransitionException.class)
    public void testFailedToInProgressRejectedWithoutRetry() {
        assertFalse(TenantProvisioningStateMachine.isLegalTransition("FAILED", "IN_PROGRESS", false));
        TenantProvisioningStateMachine.validateTransition("FAILED", "IN_PROGRESS", false);
    }

    // COMPLETED immutability
    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedToInProgressRejected() {
        TenantProvisioningStateMachine.validateTransition("COMPLETED", "IN_PROGRESS");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedToCompletedRejected() {
        TenantProvisioningStateMachine.validateTransition("COMPLETED", "COMPLETED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedToFailedRejected() {
        TenantProvisioningStateMachine.validateTransition("COMPLETED", "FAILED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedToRequestedRejected() {
        TenantProvisioningStateMachine.validateTransition("COMPLETED", "REQUESTED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testCompletedRetryRejected() {
        TenantProvisioningStateMachine.validateTransition("COMPLETED", "IN_PROGRESS", true);
    }

    // All other illegal pairs
    @Test(expected = InvalidStatusTransitionException.class)
    public void testRequestedToCompletedRejected() {
        TenantProvisioningStateMachine.validateTransition("REQUESTED", "COMPLETED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testRequestedToFailedRejected() {
        TenantProvisioningStateMachine.validateTransition("REQUESTED", "FAILED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testRequestedToRequestedRejected() {
        TenantProvisioningStateMachine.validateTransition("REQUESTED", "REQUESTED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testInProgressToRequestedRejected() {
        TenantProvisioningStateMachine.validateTransition("IN_PROGRESS", "REQUESTED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testInProgressToInProgressRejected() {
        TenantProvisioningStateMachine.validateTransition("IN_PROGRESS", "IN_PROGRESS");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testFailedToCompletedRejected() {
        TenantProvisioningStateMachine.validateTransition("FAILED", "COMPLETED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testFailedToRequestedRejected() {
        TenantProvisioningStateMachine.validateTransition("FAILED", "REQUESTED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testFailedToFailedRejected() {
        TenantProvisioningStateMachine.validateTransition("FAILED", "FAILED");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testNullFromStatusThrows() {
        TenantProvisioningStateMachine.validateTransition(null, "IN_PROGRESS");
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testNullToStatusThrows() {
        TenantProvisioningStateMachine.validateTransition("REQUESTED", null);
    }

    @Test(expected = InvalidStatusTransitionException.class)
    public void testEmptyStatusThrows() {
        TenantProvisioningStateMachine.validateTransition("  ", "IN_PROGRESS");
    }

    @Test
    public void testCaseInsensitiveNormalization() {
        assertEquals("REQUESTED", TenantProvisioningStateMachine.normalizeStatus("  requested "));
        assertEquals("IN_PROGRESS", TenantProvisioningStateMachine.normalizeStatus("In_Progress"));
        TenantProvisioningStateMachine.validateTransition("requested", "in_progress");
    }
}
