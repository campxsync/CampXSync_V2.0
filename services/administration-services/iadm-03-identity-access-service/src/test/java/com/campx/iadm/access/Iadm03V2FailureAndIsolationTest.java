package com.campx.iadm.access;

import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.iadm.access.service.CatalogueReadService;
import com.campx.iadm.access.service.DurableAuditWriter;
import org.junit.After;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.Arrays;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IADM-03-US-024 — Failure, isolation, ceiling matrix, legacy/V2 isolation, and log-leak suite.
 * Live before/after catalogue+grant hash equality for V2 decisions is proven by
 * {@code scripts/demo/iadm03_v14_api_hash_proof.mjs} (API-level; replaces source-scan
 * {@code noApplyEndpointExistsInControllerSource}).
 */
public class Iadm03V2FailureAndIsolationTest {

    @After
    public void cleanup() {
        DurableAuditWriter.clearDemoAuditDisabled();
    }

    @Test
    public void ceilingMatrixCoversAllGrantorRows() {
        // R01: may assign R02, R03; cannot self-grant or assign R27
        assertNull(GrantorCeiling.denyReason("R01", "R02", false));
        assertNull(GrantorCeiling.denyReason("R01", "R03", false));
        assertNotNull(GrantorCeiling.denyReason("R01", "R01", false));
        assertNotNull(GrantorCeiling.denyReason("R01", "R27", false));
        assertNotNull(GrantorCeiling.denyReason("R01", "R03", true));

        // R02: technical service identities only; never tenant business roles
        assertNull(GrantorCeiling.denyReason("R02", "SVC-DEMO", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "INSTITUTION_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "PRINCIPAL", false));

        // R03: own-institute roles (e.g. PRINCIPAL, HOD) allowed; R01/R02/R03/R25/R26/R27/R32 denied
        assertNull(GrantorCeiling.denyReason("R03", "PRINCIPAL", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "R01", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "R02", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "R03", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "R25", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "R26", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "R27", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "R32", false));

        // R04: direct grant denied (request-only)
        assertNotNull(GrantorCeiling.denyReason("R04", "FACULTY", false));
        assertTrue(GrantorCeiling.isProhibitedForCollegeAdminTarget("R01"));
        assertTrue(GrantorCeiling.isProhibitedForCollegeAdminTarget("R27"));

        // R38: R25/R26 only; never R01..R04, R27, R31
        assertNull(GrantorCeiling.denyReason("R38", "R25", false));
        assertNull(GrantorCeiling.denyReason("R38", "R26", false));
        assertNotNull(GrantorCeiling.denyReason("R38", "R01", false));
        assertNotNull(GrantorCeiling.denyReason("R38", "R04", false));
        assertNotNull(GrantorCeiling.denyReason("R38", "R27", false));
        assertNotNull(GrantorCeiling.denyReason("R38", "R31", false));
    }

    @Test
    public void nonGovernedRolesHoldZeroIadm03Keys() {
        String[] deniedRoles = {"R05", "R08", "R10", "R25", "R26", "R27", "R31", "R38", "R39"};
        for (String role : deniedRoles) {
            for (String key : AccessPermissionMatrix.IADM03_KEYS) {
                assertFalse(role + " must not hold " + key, AccessPermissionMatrix.allowsIadm03(role, key));
            }
        }
    }

    /**
     * (a) API contract: for every governed role, V2 grant decision is either R01-only approve
     * or a hard deny — never an apply. Live before/after hash equality is in
     * {@code docs/iadm/iadm-03/evidence/v2/v14_api_hash_proof.json}.
     */
    @Test
    public void v2GrantDecisionForEveryRoleNeverAppliesCatalogueOrGrants() {
        String[] roles = {"R01", "R02", "R03", "R04", "R28", "R05", "R10", "R25", "R38"};
        for (String role : roles) {
            boolean canApprove = AccessPermissionMatrix.allowsIadm03(role, "iadm03.grant.approve");
            if ("R01".equals(role)) {
                assertTrue("Only R01 may decide V2 grant requests", canApprove);
            } else {
                assertFalse(role + " must not hold iadm03.grant.approve (V2 record-only; no apply)", canApprove);
            }
            // V2 decision path is not a legacy grant mutator
            assertFalse(
                    "V2 /grant-requests/{id}/decision must not be listed as a legacy grant mutator for " + role,
                    LegacyV14Routes.isLegacyGrantOrCatalogueMutation("POST", "/api/v1/iadm/03/grant-requests/x/decision"));
        }
        // V2 routes are never in the exclusive legacy mutating inventory
        for (String route : LegacyV14Routes.GRANT_OR_CATALOGUE_MUTATING) {
            assertFalse(route.contains("/grant-requests"));
            assertFalse(route.contains("/v2/"));
        }
    }

    /**
     * (b) Restored legacy V1.4 endpoints are the exclusive grant/catalogue mutators and are labeled legacy.
     */
    @Test
    public void restoredLegacyEndpointsAreExclusiveGrantMutatorsAndLabeled() {
        List<String> expected = Arrays.asList(
                "POST /grants/appoint-r03",
                "POST /grants/assign",
                "POST /grants/revoke",
                "POST /grants/r03-change/{id}/decide",
                "POST /catalog-changes",
                "POST /catalog-changes/{id}/decide"
        );
        assertEquals(expected, LegacyV14Routes.GRANT_OR_CATALOGUE_MUTATING);
        assertEquals(Arrays.asList("GET /catalog-changes"), LegacyV14Routes.LEGACY_READ);
        assertEquals(LegacyV14Routes.LABEL, "IADM-03 V1.4 (legacy)");

        assertTrue(LegacyV14Routes.isLegacyGrantOrCatalogueMutation("POST", "/api/v1/iadm/03/grants/assign"));
        assertTrue(LegacyV14Routes.isLegacyGrantOrCatalogueMutation("POST", "/api/v1/iadm/03/grants/revoke"));
        assertTrue(LegacyV14Routes.isLegacyGrantOrCatalogueMutation("POST", "/api/v1/iadm/03/grants/appoint-r03"));
        assertTrue(LegacyV14Routes.isLegacyGrantOrCatalogueMutation(
                "POST", "/api/v1/iadm/03/grants/r03-change/11111111-1111-1111-1111-111111111111/decide"));
        assertTrue(LegacyV14Routes.isLegacyGrantOrCatalogueMutation("POST", "/api/v1/iadm/03/catalog-changes"));
        assertTrue(LegacyV14Routes.isLegacyGrantOrCatalogueMutation(
                "POST", "/api/v1/iadm/03/catalog-changes/11111111-1111-1111-1111-111111111111/decide"));
        assertFalse(LegacyV14Routes.isLegacyGrantOrCatalogueMutation("GET", "/api/v1/iadm/03/catalog-changes"));
        assertFalse(LegacyV14Routes.isLegacyGrantOrCatalogueMutation("POST", "/api/v1/iadm/03/grant-requests"));
        assertFalse(LegacyV14Routes.isLegacyGrantOrCatalogueMutation(
                "POST", "/api/v1/iadm/03/grant-requests/11111111-1111-1111-1111-111111111111/decision"));

        // Permission register labels all iam.* as legacy V1.4 (not Unknown)
        String[] iamKeys = {
                "iam.access.overview.view", "iam.access.subject.view", "iam.audit.view",
                "iam.catalog.change.approve", "iam.catalog.change.propose",
                "iam.catalog.permissions.view", "iam.catalog.roles.view",
                "iam.delegation.create", "iam.delegation.revoke", "iam.governance.read",
                "iam.grant.assign", "iam.grant.r03.decide", "iam.grant.revoke",
                "iam.identity.technical.assign", "iam.profile.invite", "iam.profile.status",
                "iam.profile.view", "iam.request.decide", "iam.request.submit", "iam.review.manage"
        };
        assertEquals(20, iamKeys.length);
        Set<String> unique = new HashSet<String>(Arrays.asList(iamKeys));
        assertEquals(20, unique.size());
        for (String key : iamKeys) {
            assertEquals(LegacyV14Routes.LABEL, CatalogueReadService.deriveOwningService(key));
        }
    }

    @Test
    public void noTokenOrPasswordLeakInIadm03Sources() throws Exception {
        String[] files = {
                "src/main/java/com/campx/iadm/access/controller/Iadm03Controller.java",
                "src/main/java/com/campx/iadm/access/service/CatalogueReadService.java",
                "src/main/java/com/campx/iadm/access/service/RecordOnlyGrantWorkflowService.java",
                "src/main/java/com/campx/iadm/access/service/DurableAuditWriter.java"
        };
        for (String f : files) {
            String src = new String(Files.readAllBytes(Paths.get(f)), StandardCharsets.UTF_8);
            assertFalse(f + " must not log Authorization header", src.contains("logger.info(\"Authorization") || src.contains("logger.debug(\"Authorization"));
            assertFalse(f + " must not contain hardcoded eyJ JWT", src.contains("eyJhbG"));
        }
    }

    @Test
    public void auditUnavailableRefusesGovernedOperation() throws Exception {
        DurableAuditWriter.setDemoAuditDisabled(true);
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(UUID.randomUUID());
        try {
            DurableAuditWriter.appendWithRefs(null, ctx, null, "IADM03_TEST", "PLATFORM", "TEST", "id", null, null, "c1", "msg");
            fail("Expected 503 IADM03_AUDIT_UNAVAILABLE");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM03_AUDIT_UNAVAILABLE", ex.getErrorCode());
        }
    }

    @Test
    public void registerOwningServiceDerivationIsDeterministic() {
        assertEquals("IADM-01", CatalogueReadService.deriveOwningService("iadm01.foo"));
        assertEquals("IADM-07", CatalogueReadService.deriveOwningService("iadm07.register.read"));
        assertEquals("IADM-08", CatalogueReadService.deriveOwningService("iadm08.manage"));
        assertEquals("Legacy", CatalogueReadService.deriveOwningService("adm08.view"));
        assertEquals("IADM-03 V1.4 (legacy)", CatalogueReadService.deriveOwningService("iam.grant.assign"));
        assertEquals("Unknown", CatalogueReadService.deriveOwningService("cfg.view"));
    }
}
