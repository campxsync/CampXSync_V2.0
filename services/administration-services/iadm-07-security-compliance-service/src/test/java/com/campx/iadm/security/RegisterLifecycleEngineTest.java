package com.campx.iadm.security;

import com.campx.iadm.security.exception.InstituteAdminException;
import com.campx.iadm.security.service.RegisterLifecycleEngine;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IADM-07-US-026 — register lifecycle transition proofs.
 */
public class RegisterLifecycleEngineTest {

    @Test
    public void standardDraftToInReviewAllowed() {
        assertTrue(RegisterLifecycleEngine.allows(
                RegisterLifecycleEngine.STANDARD, "DRAFT", "IN_REVIEW"));
    }

    @Test
    public void standardDraftToPublishedDenied() {
        assertFalse(RegisterLifecycleEngine.allows(
                RegisterLifecycleEngine.STANDARD, "DRAFT", "PUBLISHED"));
    }

    @Test
    public void findingPendingVerificationToResolvedAllowed() {
        assertTrue(RegisterLifecycleEngine.allows(
                RegisterLifecycleEngine.FINDING, "PENDING_VERIFICATION", "RESOLVED"));
    }

    @Test
    public void invalidTransitionThrows400() {
        try {
            RegisterLifecycleEngine.assertTransition(
                    RegisterLifecycleEngine.ALERT, "OPEN", "RESOLVED");
            fail("Expected invalid transition");
        } catch (InstituteAdminException ex) {
            if (ex.getStatus() != 400) {
                fail("Expected 400 got " + ex.getStatus());
            }
            if (!"IADM07_INVALID_TRANSITION".equals(ex.getErrorCode())) {
                fail("Wrong code: " + ex.getErrorCode());
            }
        }
    }

    @Test
    public void signalNewToInvestigatingAllowed() {
        assertTrue(RegisterLifecycleEngine.allows(
                RegisterLifecycleEngine.SIGNAL, "NEW", "INVESTIGATING"));
    }
}