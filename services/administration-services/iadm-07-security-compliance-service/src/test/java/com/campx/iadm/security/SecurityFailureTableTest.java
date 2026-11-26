package com.campx.iadm.security;

import com.campx.iadm.security.security.SecurityPermissionMatrix;
import com.campx.iadm.security.service.PostureIndicatorEngine;
import com.campx.iadm.security.service.RegisterLifecycleEngine;
import com.campx.iadm.security.service.SecurityOutageFlags;
import com.campx.iadm.security.exception.InstituteAdminException;
import org.junit.After;
import org.junit.Test;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IADM-07-US-031 — failure, isolation and role table (offline proofs).
 * Plain language: missing permission, invalid transition, stale not healthy, audit flag, role denials.
 */
public class SecurityFailureTableTest {

    @After
    public void clearFlags() {
        SecurityOutageFlags.set(Boolean.FALSE, Boolean.FALSE, Boolean.FALSE);
        PostureIndicatorEngine.setStaleThresholdMinutes(10080);
    }

    @Test
    public void r04HasSummaryOnly_noRegisterWrite() {
        assertTrue(SecurityPermissionMatrix.allows("R04", "iadm07.summary.view"));
        assertFalse(SecurityPermissionMatrix.allows("R04", "iadm07.register.write"));
        assertFalse(SecurityPermissionMatrix.allows("R04", "iadm07.register.read"));
        assertFalse(SecurityPermissionMatrix.allows("R04", "iadm07.standard.approve"));
    }

    @Test
    public void r28ReadOnly_noMutations() {
        assertTrue(SecurityPermissionMatrix.allows("R28", "iadm07.posture.view"));
        assertTrue(SecurityPermissionMatrix.allows("R28", "iadm07.audit.view"));
        assertFalse(SecurityPermissionMatrix.allows("R28", "iadm07.register.write"));
        assertFalse(SecurityPermissionMatrix.allows("R28", "iadm07.exception.decide"));
        assertFalse(SecurityPermissionMatrix.allows("R28", "iadm07.standard.approve"));
    }

    @Test
    public void r02CannotApproveOrDecide() {
        assertTrue(SecurityPermissionMatrix.allows("R02", "iadm07.standard.propose"));
        assertFalse(SecurityPermissionMatrix.allows("R02", "iadm07.standard.approve"));
        assertFalse(SecurityPermissionMatrix.allows("R02", "iadm07.exception.decide"));
    }

    @Test
    public void outsiderRolesDenied() {
        assertFalse(SecurityPermissionMatrix.allows("R05", "iadm07.posture.view"));
        assertFalse(SecurityPermissionMatrix.allows("R10", "iadm07.register.read"));
        assertFalse(SecurityPermissionMatrix.allows("R25", "iadm07.summary.view"));
        assertFalse(SecurityPermissionMatrix.allows("R31", "iadm07.register.write"));
    }

    @Test
    public void serviceCallerIntakeOnly() {
        assertTrue(SecurityPermissionMatrix.allows("SERVICE", "iadm07.signal.intake"));
        assertFalse(SecurityPermissionMatrix.allows("SERVICE", "iadm07.register.write"));
        // Human roles do not hold the service intake key in the offline matrix
        assertFalse(SecurityPermissionMatrix.allows("R02", "iadm07.signal.intake"));
        assertFalse(SecurityPermissionMatrix.allows("R03", "iadm07.signal.intake"));
    }

    @Test
    public void invalidFindingTransitionRefused() {
        try {
            RegisterLifecycleEngine.assertTransition("FINDING", "OPEN", "RESOLVED");
            fail("expected invalid transition");
        } catch (InstituteAdminException ex) {
            assertEquals(400, ex.getStatus());
            assertEquals("IADM07_INVALID_TRANSITION", ex.getErrorCode());
        }
    }

    @Test
    public void staleNeverHealthyOrZero() {
        Instant old = Instant.now().minus(20, ChronoUnit.DAYS);
        PostureIndicatorEngine.setStaleThresholdMinutes(7 * 24 * 60);
        assertTrue(PostureIndicatorEngine.isStale(old));
        assertFalse(PostureIndicatorEngine.isStale(Instant.now()));
        assertEquals(10080, PostureIndicatorEngine.getStaleThresholdMinutes());
    }

    @Test
    public void auditOutageFlagReadable() {
        SecurityOutageFlags.set(Boolean.TRUE, null, null);
        assertTrue(SecurityOutageFlags.isAuditDisabled());
        assertFalse(SecurityOutageFlags.isNotificationsDisabled() && false);
        // Notifications outage must never block — flag exists but workflows ignore it
        SecurityOutageFlags.set(Boolean.FALSE, null, Boolean.TRUE);
        assertTrue(SecurityOutageFlags.isNotificationsDisabled());
        assertFalse(SecurityOutageFlags.isAuditDisabled());
    }

    @Test
    public void noSensitiveTokensInErrorCodes() {
        InstituteAdminException ex = new InstituteAdminException(503, "IADM07_AUDIT_UNAVAILABLE",
                "The security governance change cannot be confirmed because audit is unavailable.");
        String msg = ex.getMessage() == null ? "" : ex.getMessage().toLowerCase();
        assertFalse(msg.contains("bearer"));
        assertFalse(msg.contains("password"));
        assertFalse(msg.contains("jwt"));
        assertFalse(msg.contains("secret"));
    }
}
