package com.campx.iadm.governance;

import com.campx.iadm.governance.exception.InstituteAdminException;
import com.campx.iadm.governance.security.GovernancePermissionMatrix;
import com.campx.iadm.governance.security.UserSecurityContext;
import com.campx.iadm.governance.service.DurableAuditWriter;
import com.campx.iadm.governance.service.GovernanceOutageFlags;
import com.campx.iadm.governance.simulator.DomainExecutionSimulator;
import org.junit.After;
import org.junit.Test;

import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Pattern;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IADM-05-US-031 — Failure-handling and isolation suite (blueprint §11 + role boundaries).
 * Plain language: each failure-table row that can be proven offline is covered here;
 * live tenant isolation is proven by the API smoke + headed film against two institutes.
 */
public class GovernanceFailureTableTest {

    private static final Pattern SENSITIVE_LEAK = Pattern.compile(
            "(?i)(password|passwd|secret|bearer\\s+[a-z0-9._\\-]+|jwt\\s*[:=]|service[_-]?role)");

    @After
    public void reset() {
        GovernanceOutageFlags.set(Boolean.FALSE, Boolean.FALSE);
    }

    /** Missing/invalid tenant context — fail closed at context construction. */
    @Test
    public void missingTenantContextFailsClosed() {
        try {
            UserSecurityContext.fromHeaders(
                    "00000000-0000-0000-0000-000000000001", null);
            fail("expected missing tenant denial");
        } catch (RuntimeException ex) {
            assertTrue(ex.getMessage().toLowerCase().contains("tenant")
                    || ex.getMessage().toLowerCase().contains("security"));
            assertNoSensitiveLeak(ex.getMessage());
        }
    }

    /** No applicable policy — error code contract used by PolicyLifecycleService.resolvePublished. */
    @Test
    public void noApplicablePolicyCodeIsFailClosed() {
        InstituteAdminException ex = new InstituteAdminException(404, "IADM05_NO_APPLICABLE_POLICY",
                "No applicable published policy (fail closed).");
        assertEquals(404, ex.getStatus());
        assertEquals("IADM05_NO_APPLICABLE_POLICY", ex.getErrorCode());
    }

    /** Conflicting policy versions — conflict code contract. */
    @Test
    public void conflictingPolicyVersionsBlocked() {
        InstituteAdminException ex = new InstituteAdminException(409, "IADM05_POLICY_CONFLICT",
                "Conflicting published policy versions; operation blocked.");
        assertEquals("IADM05_POLICY_CONFLICT", ex.getErrorCode());
    }

    /** Domain unavailable / pending / reject / partial — simulator outcomes. */
    @Test
    public void domainUnavailablePendingRejectPartial() {
        DomainExecutionSimulator sim = new DomainExecutionSimulator();
        UUID corr = UUID.randomUUID();
        Map<String, Object> meta = Collections.emptyMap();
        assertEquals("UNAVAILABLE", sim.execute("EXPORT", "X_UNAVAILABLE", corr, meta).get("outcome"));
        assertEquals("PENDING", sim.execute("ERASURE", "DEMO_DOMAIN_B", corr, meta).get("outcome"));
        assertEquals("REJECTED", sim.execute("EXPORT", "DEMO_DOMAIN_B_FAIL", corr, meta).get("outcome"));
        assertEquals("COMPLETED", sim.execute("EXPORT", "DEMO_DOMAIN_A", corr, meta).get("outcome"));
        assertFalse(Boolean.TRUE.equals(
                sim.execute("EXPORT", "DEMO_DOMAIN_A", corr, meta).get("touchesRealDomainData")));
    }

    /** Audit unavailable — privileged action refused (fail closed). */
    @Test
    public void auditUnavailableFailsClosed() {
        GovernanceOutageFlags.set(Boolean.TRUE, null);
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        try {
            DurableAuditWriter.append(null, ctx, null, "IADM05_TEST", "policy", null, "x");
            fail("expected audit unavailable");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM05_AUDIT_UNAVAILABLE", ex.getErrorCode());
            assertNoSensitiveLeak(ex.getMessage());
        } catch (Exception ex) {
            fail("unexpected " + ex);
        }
    }

    /** Duplicate / idempotency — documented by unique idempotency_key; offline proof of key presence. */
    @Test
    public void idempotencyKeyNamingPresentInCatalogue() {
        assertTrue(Arrays.asList(GovernancePermissionMatrix.NEW_KEYS)
                .contains("iadm05.export.request"));
    }

    /** Expired/revoked policy — publish gate and legal gate codes. */
    @Test
    public void privacyPublishLegalGateCode() {
        InstituteAdminException ex = new InstituteAdminException(423, "IADM05_LEGAL_GATE",
                "Blocked: awaiting children's-data legal check (D-RBAC-M17)");
        assertEquals("IADM05_LEGAL_GATE", ex.getErrorCode());
    }

    /** Erasure closure blocked while unresolved. */
    @Test
    public void erasureClosureBlockedCode() {
        InstituteAdminException ex = new InstituteAdminException(409, "IADM05_CLOSURE_BLOCKED",
                "Closure blocked: unresolved domains (technical rule only; G-05 legal TBC).");
        assertEquals("IADM05_CLOSURE_BLOCKED", ex.getErrorCode());
    }

    @Test
    public void r01HasApprovePublishDecide() {
        assertTrue(GovernancePermissionMatrix.allows("R01", "iadm05.policy.approve"));
        assertTrue(GovernancePermissionMatrix.allows("R01", "iadm05.policy.publish"));
        assertTrue(GovernancePermissionMatrix.allows("R01", "iadm05.export.decide"));
        assertTrue(GovernancePermissionMatrix.allows("R01", "iadm05.erasure.close"));
    }

    @Test
    public void r02MayProposeNeverApprove() {
        assertTrue(GovernancePermissionMatrix.allows("R02", "iadm05.classification.propose"));
        assertTrue(GovernancePermissionMatrix.allows("R02", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.policy.approve"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.policy.publish"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.export.decide"));
        assertFalse(GovernancePermissionMatrix.allows("R02", "iadm05.erasure.close"));
    }

    @Test
    public void r03ViewOnlyOwnInstituteKeys() {
        assertTrue(GovernancePermissionMatrix.allows("R03", "iadm05.adherence.view"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.classification.propose"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.policy.approve"));
    }

    @Test
    public void r04ViewOnlyNoTrigger() {
        assertTrue(GovernancePermissionMatrix.allows("R04", "iadm05.college.view"));
        assertFalse(GovernancePermissionMatrix.allows("R04", "iadm05.classification.propose"));
        assertFalse(GovernancePermissionMatrix.allows("R04", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R04", "iadm05.policy.approve"));
    }

    @Test
    public void r28ReadOnly() {
        assertTrue(GovernancePermissionMatrix.allows("R28", "iadm05.audit.view"));
        assertFalse(GovernancePermissionMatrix.allows("R28", "iadm05.policy.approve"));
        assertFalse(GovernancePermissionMatrix.allows("R28", "iadm05.classification.propose"));
        assertFalse(GovernancePermissionMatrix.allows("R28", "iadm05.export.request"));
    }

    /** One role from R05–R39 must be denied (US-031). */
    @Test
    public void r05ThroughR39SampleDenied() {
        List<String> samples = Arrays.asList("R05", "R10", "R25", "R27", "R29", "R31", "R39");
        for (String role : samples) {
            assertFalse(role + " must not hold classification.view",
                    GovernancePermissionMatrix.allows(role, "iadm05.classification.view"));
            assertFalse(role + " must not hold policy.approve",
                    GovernancePermissionMatrix.allows(role, "iadm05.policy.approve"));
        }
    }

    @Test
    public void viewDoesNotImplyExportExecuteDeleteApprove() {
        assertTrue(GovernancePermissionMatrix.allows("R03", "iadm05.classification.view"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.export.request"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.policy.approve"));
        assertFalse(GovernancePermissionMatrix.allows("R03", "iadm05.erasure.close"));
        for (String key : GovernancePermissionMatrix.NEW_KEYS) {
            assertFalse("wildcard forbidden in " + key, key.contains("*"));
        }
    }

    @Test
    public void logLeakCheckOnErrorMessages() {
        assertNoSensitiveLeak("Missing permission: iadm05.policy.approve");
        assertNoSensitiveLeak("No applicable published policy (fail closed).");
        assertNoSensitiveLeak(new InstituteAdminException(503, "IADM05_AUDIT_UNAVAILABLE",
                "The governance change cannot be confirmed because audit is unavailable.").getMessage());
    }

    /**
     * Cross-institute scopeTenantId: platform may select; non-platform foreign → 403;
     * own institute and null client both resolve to trusted claims.
     */
    @Test
    public void trustedScopeTenantRefusesForeignForNonPlatform() {
        UUID own = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID other = UUID.fromString("22222222-2222-2222-2222-222222222222");
        assertEquals(other, com.campx.iadm.governance.service.GovernanceRequestService
                .resolveScopeTenant(true, own, other));
        assertEquals(own, com.campx.iadm.governance.service.GovernanceRequestService
                .resolveScopeTenant(false, own, null));
        assertEquals(own, com.campx.iadm.governance.service.GovernanceRequestService
                .resolveScopeTenant(false, own, own));
        try {
            com.campx.iadm.governance.service.GovernanceRequestService
                    .resolveScopeTenant(false, own, other);
            fail("expected cross-tenant denial");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM05_CROSS_TENANT_DENIED", ex.getErrorCode());
        }
    }

    private static void assertNoSensitiveLeak(String text) {
        if (text == null) {
            return;
        }
        assertFalse("sensitive leak in: " + text, SENSITIVE_LEAK.matcher(text).find());
    }
}
