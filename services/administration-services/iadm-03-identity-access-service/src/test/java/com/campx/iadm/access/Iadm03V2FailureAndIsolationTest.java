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
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * IADM-03-US-024 — Failure, isolation, ceiling matrix, no-apply-endpoint, and log-leak suite.
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

    @Test
    public void noApplyEndpointExistsInControllerSource() throws Exception {
        String ctrl = new String(Files.readAllBytes(Paths.get(
                "src/main/java/com/campx/iadm/access/controller/Iadm03Controller.java")),
                StandardCharsets.UTF_8);
        assertFalse("Must not expose /grants/assign", ctrl.contains("/grants/assign"));
        assertFalse("Must not expose /grants/revoke", ctrl.contains("/grants/revoke"));
        assertFalse("Must not expose /grants/appoint-r03", ctrl.contains("/grants/appoint-r03"));
        assertFalse("Must not expose /grant-changes/", ctrl.contains("/grant-changes/"));
        assertFalse("Must not expose /catalog-changes/", ctrl.contains("/catalog-changes/"));
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
        assertEquals("Unknown", CatalogueReadService.deriveOwningService("iam.grant.assign"));
    }
}
