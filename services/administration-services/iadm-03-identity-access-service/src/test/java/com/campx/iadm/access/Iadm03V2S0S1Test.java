package com.campx.iadm.access;

import com.campx.iadm.access.exception.InstituteAdminException;
import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.UserSecurityContext;
import com.campx.iadm.access.service.CatalogueReadService;
import com.campx.iadm.access.service.DurableAuditWriter;
import org.junit.After;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * S0 & S1 verification tests for IADM-03 V2.0:
 * US-018 (iadm03.* keys & role mapping), US-020 (audit refusal), US-021 (prefix & status engine).
 */
public class Iadm03V2S0S1Test {

    @After
    public void cleanup() {
        DurableAuditWriter.clearDemoAuditDisabled();
    }

    @Test
    public void iadm03KeysFollowConventionAndHaveNoWildcardOrApplyKey() {
        assertEquals(12, AccessPermissionMatrix.IADM03_KEYS.length);
        for (String key : AccessPermissionMatrix.IADM03_KEYS) {
            assertTrue(key.startsWith("iadm03."));
            assertFalse(key.contains("*"));
            assertFalse(key.contains("apply"));
        }
    }

    @Test
    public void iadm03RoleMappingMatchesBoundary7() {
        // R01 has all 12
        for (String key : AccessPermissionMatrix.IADM03_KEYS) {
            assertTrue("R01 must hold " + key, AccessPermissionMatrix.allowsIadm03("R01", key));
        }
        // R02 never approves
        assertTrue(AccessPermissionMatrix.allowsIadm03("R02", "iadm03.register.view"));
        assertTrue(AccessPermissionMatrix.allowsIadm03("R02", "iadm03.grant.review"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R02", "iadm03.grant.approve"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R02", "iadm03.registration.approve"));
        // R03 own-institute view + request + delegation + handover; no matrix/register/approve
        assertTrue(AccessPermissionMatrix.allowsIadm03("R03", "iadm03.holders.view"));
        assertTrue(AccessPermissionMatrix.allowsIadm03("R03", "iadm03.grant.request.submit"));
        assertTrue(AccessPermissionMatrix.allowsIadm03("R03", "iadm03.delegation.manage"));
        assertTrue(AccessPermissionMatrix.allowsIadm03("R03", "iadm03.handover.view"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R03", "iadm03.matrix.view"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R03", "iadm03.grant.approve"));
        // R04 request-only
        assertTrue(AccessPermissionMatrix.allowsIadm03("R04", "iadm03.grant.request.submit"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R04", "iadm03.register.view"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R04", "iadm03.holders.view"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R04", "iadm03.grant.approve"));
        // R28 read-only
        assertTrue(AccessPermissionMatrix.allowsIadm03("R28", "iadm03.register.view"));
        assertTrue(AccessPermissionMatrix.allowsIadm03("R28", "iadm03.matrix.view"));
        assertTrue(AccessPermissionMatrix.allowsIadm03("R28", "iadm03.evidence.view"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R28", "iadm03.grant.request.submit"));
        assertFalse(AccessPermissionMatrix.allowsIadm03("R28", "iadm03.grant.approve"));
        // Other roles get zero iadm03.* keys
        for (String key : AccessPermissionMatrix.IADM03_KEYS) {
            assertFalse(AccessPermissionMatrix.allowsIadm03("R05", key));
            assertFalse(AccessPermissionMatrix.allowsIadm03("R25", key));
            assertFalse(AccessPermissionMatrix.allowsIadm03("R27", key));
            assertFalse(AccessPermissionMatrix.allowsIadm03("R38", key));
        }
    }

    @Test
    public void demoAuditDisabledSwitchFailsClosed() throws Exception {
        DurableAuditWriter.setDemoAuditDisabled(true);
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(
                UUID.fromString("00000000-0000-0000-0000-000000000001"));
        try {
            DurableAuditWriter.appendWithRefs(null, ctx, null,
                    "IADM03_TEST", "PLATFORM", "TEST", "1", "a", "b", "corr-1", "test");
            fail("Expected 503 IADM03_AUDIT_UNAVAILABLE");
        } catch (InstituteAdminException ex) {
            assertEquals(503, ex.getStatus());
            assertEquals("IADM03_AUDIT_UNAVAILABLE", ex.getErrorCode());
        }
    }

    @Test
    public void owningServiceAndStatusDerivationFollowsPrefixRules() {
        assertEquals("IADM-07", CatalogueReadService.deriveOwningService("iadm07.posture.view"));
        assertEquals("IADM-08", CatalogueReadService.deriveOwningService("iadm08.manage"));
        assertEquals("Legacy", CatalogueReadService.deriveOwningService("adm03.view"));
        assertEquals("Unknown", CatalogueReadService.deriveOwningService("cfg.view"));
        assertEquals("Unknown", CatalogueReadService.deriveOwningService("custom.foo"));

        assertEquals("legacy", CatalogueReadService.deriveKeyStatus("adm03.view", "Legacy", 2));
        assertEquals("ungranted-reserved", CatalogueReadService.deriveKeyStatus("iadm08.manage", "IADM-08", 0));
        assertEquals("granted", CatalogueReadService.deriveKeyStatus("iadm07.posture.view", "IADM-07", 3));
        assertEquals("dormant", CatalogueReadService.deriveKeyStatus("fin01.view", "Unknown", 0));
        assertEquals("unknown", CatalogueReadService.deriveKeyStatus("custom.foo", "Unknown", 0));
    }
}
