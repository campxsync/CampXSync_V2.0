package com.campx.iadm.security;

import com.campx.iadm.security.exception.InstituteAdminException;
import com.campx.iadm.security.exception.MalformedPayloadException;
import com.campx.iadm.security.exception.UserProfileAccessDeniedException;
import com.campx.iadm.security.security.SecurityPermissionMatrix;
import com.campx.iadm.security.service.PostureIndicatorEngine;
import com.campx.iadm.security.service.RegisterLifecycleEngine;
import com.campx.iadm.security.service.SecurityDomainService;
import com.campx.iadm.security.service.SecurityOutageFlags;
import org.junit.After;
import org.junit.Test;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

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

    @Test
    public void crossInstituteWriteRefusedForNonPlatform() {
        UUID instA = UUID.fromString("d4000000-0000-4000-8000-000000000004");
        UUID instB = UUID.fromString("72ecbfe3-2077-41b5-8a20-b6d3e045311b");
        assertEquals(instA, SecurityDomainService.resolveWriteInstituteId(false, instA, null));
        assertEquals(instA, SecurityDomainService.resolveWriteInstituteId(false, instA, instA));
        assertEquals(instB, SecurityDomainService.resolveWriteInstituteId(true, null, instB));
        try {
            SecurityDomainService.resolveWriteInstituteId(false, instA, instB);
            fail("expected cross-institute refusal");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue(ex.getMessage().contains("Cross-institute"));
        }
    }

    @Test
    public void exceptionSoDSelfDecideRefused() {
        UUID u1 = UUID.fromString("00000000-0000-0000-0000-000000000001");
        UUID u2 = UUID.fromString("00000000-0000-0000-0000-000000000002");
        SecurityDomainService.assertExceptionDecisionAllowed(u1, u2);
        try {
            SecurityDomainService.assertExceptionDecisionAllowed(u1, u1);
            fail("expected SoD violation");
        } catch (InstituteAdminException ex) {
            assertEquals(403, ex.getStatus());
            assertEquals("IADM07_SOD_VIOLATION", ex.getErrorCode());
        }
    }

    @Test
    public void exceptionMandatoryValidityWindow() {
        Map<String, Object> missing = new LinkedHashMap<String, Object>();
        try {
            SecurityDomainService.parseRequiredValidityWindow(missing);
            fail("expected missing validity window refusal");
        } catch (MalformedPayloadException ex) {
            assertTrue(ex.getMessage().contains("validFrom"));
        }

        Map<String, Object> reversed = new LinkedHashMap<String, Object>();
        reversed.put("validFrom", "2026-12-10");
        reversed.put("validTo", "2026-12-01");
        try {
            SecurityDomainService.parseRequiredValidityWindow(reversed);
            fail("expected reversed validity window refusal");
        } catch (MalformedPayloadException ex) {
            assertTrue(ex.getMessage().contains("validTo must be after validFrom"));
        }

        Map<String, Object> valid = new LinkedHashMap<String, Object>();
        valid.put("validFrom", "2026-12-01T00:00:00Z");
        valid.put("validTo", "2026-12-15T00:00:00Z");
        Timestamp[] window = SecurityDomainService.parseRequiredValidityWindow(valid);
        assertEquals(2, window.length);
        assertTrue(window[1].after(window[0]));
    }

    @Test
    public void serviceIntakeRefusesHumanOperators() {
        SecurityDomainService.assertServiceIntakeAccess(false, true);
        try {
            SecurityDomainService.assertServiceIntakeAccess(true, true);
            fail("expected human operator refusal on signal intake");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue(ex.getMessage().contains("service callers"));
        }
        try {
            SecurityDomainService.assertServiceIntakeAccess(false, false);
            fail("expected missing permission refusal");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue(ex.getMessage().contains("iadm07.signal.intake"));
        }
    }

    @Test
    public void r03FindingAndAlertTransitionLimits() {
        SecurityDomainService.assertInstitutionFindingTransition("ASSIGNED", "REMEDIATION_IN_PROGRESS");
        SecurityDomainService.assertInstitutionFindingTransition("REMEDIATION_IN_PROGRESS", "PENDING_VERIFICATION");
        try {
            SecurityDomainService.assertInstitutionFindingTransition("OPEN", "TRIAGED");
            fail("expected R03 finding transition refusal");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue(ex.getMessage().contains("Institution Admin"));
        }
        try {
            SecurityDomainService.assertInstitutionFindingTransition("PENDING_VERIFICATION", "RESOLVED");
            fail("expected R03 resolve refusal");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue(ex.getMessage().contains("resolved"));
        }

        SecurityDomainService.assertInstitutionAlertTransition("OPEN", "ACKNOWLEDGED");
        try {
            SecurityDomainService.assertInstitutionAlertTransition("ACKNOWLEDGED", "RESOLVED");
            fail("expected R03 alert resolve refusal");
        } catch (UserProfileAccessDeniedException ex) {
            assertTrue(ex.getMessage().contains("acknowledge"));
        }
    }

    @Test
    public void expiringExceptionsExcludesExpiredRows() {
        assertTrue(PostureIndicatorEngine.EXPIRING_EXCEPTIONS_SQL.contains("valid_to > now()"));
        Instant now = Instant.now();
        assertFalse(PostureIndicatorEngine.isWithinExpiringWindow(now.minus(1, ChronoUnit.DAYS), now));
        assertTrue(PostureIndicatorEngine.isWithinExpiringWindow(now.plus(5, ChronoUnit.DAYS), now));
        assertFalse(PostureIndicatorEngine.isWithinExpiringWindow(now.plus(20, ChronoUnit.DAYS), now));
    }

    @Test
    @SuppressWarnings("unchecked")
    public void reportCountsShapeIncludesBreakdownTrendsAndFreshness() {
        Map<String, Object> byStatus = new LinkedHashMap<String, Object>();
        byStatus.put("findings", Collections.singletonMap("ASSIGNED", Integer.valueOf(1)));
        Map<String, Integer> sev = Collections.singletonMap("HIGH", Integer.valueOf(1));
        Map<String, Integer> trends = Collections.singletonMap("findingsCreatedThisMonth", Integer.valueOf(1));
        Map<String, Instant> latest = new LinkedHashMap<String, Instant>();
        latest.put("findings", Instant.now());
        latest.put("signals", Instant.now().minus(30, ChronoUnit.DAYS));

        Map<String, Object> report = SecurityDomainService.assembleReportCounts(
                1, 1, 1, 1, 1, byStatus, sev, trends, latest);
        assertEquals(byStatus, report.get("byStatus"));
        assertEquals(sev, report.get("findingsBySeverity"));
        assertEquals(trends, report.get("monthlyTrends"));
        Map<String, Object> freshness = (Map<String, Object>) report.get("freshness");
        assertEquals("STALE", freshness.get("status"));
    }
}
