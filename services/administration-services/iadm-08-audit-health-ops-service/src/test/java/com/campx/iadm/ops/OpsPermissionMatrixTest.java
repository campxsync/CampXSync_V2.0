package com.campx.iadm.ops;

import com.campx.iadm.ops.security.OpsPermissionMatrix;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Offline RBAC matrix for IADM-08 keys (US-017).
 * Plain language: R04 gets nothing; R28 never gets manage; manage never allowed.
 */
public class OpsPermissionMatrixTest {

    @Test
    public void r01HasAuditAndHealthNotManage() {
        assertTrue(OpsPermissionMatrix.allows("R01", OpsPermissionMatrix.KEY_AUDIT_PLATFORM));
        assertTrue(OpsPermissionMatrix.allows("R01", OpsPermissionMatrix.KEY_HEALTH));
        assertTrue(OpsPermissionMatrix.allows("R01", OpsPermissionMatrix.KEY_ALERTS));
        assertFalse(OpsPermissionMatrix.allows("R01", OpsPermissionMatrix.KEY_MANAGE));
    }

    @Test
    public void r02SameAsR01ViewKeys() {
        assertTrue(OpsPermissionMatrix.allows("R02", OpsPermissionMatrix.KEY_AUDIT_PLATFORM));
        assertTrue(OpsPermissionMatrix.allows("R02", OpsPermissionMatrix.KEY_HEALTH));
        assertFalse(OpsPermissionMatrix.allows("R02", OpsPermissionMatrix.KEY_MANAGE));
    }

    @Test
    public void r03InstituteAuditNoHealth() {
        assertTrue(OpsPermissionMatrix.allows("R03", OpsPermissionMatrix.KEY_AUDIT_INSTITUTE));
        assertTrue(OpsPermissionMatrix.allows("R03", OpsPermissionMatrix.KEY_ALERTS));
        assertFalse(OpsPermissionMatrix.allows("R03", OpsPermissionMatrix.KEY_HEALTH));
        assertFalse(OpsPermissionMatrix.allows("R03", OpsPermissionMatrix.KEY_AUDIT_PLATFORM));
    }

    @Test
    public void r04DeniedEverywhere() {
        for (String k : OpsPermissionMatrix.ALL_KEYS) {
            assertFalse("R04 must not hold " + k, OpsPermissionMatrix.allows("R04", k));
        }
    }

    @Test
    public void r28ReadOnlyNoManage() {
        assertTrue(OpsPermissionMatrix.allows("R28", OpsPermissionMatrix.KEY_AUDIT_PLATFORM));
        assertTrue(OpsPermissionMatrix.allows("R28", OpsPermissionMatrix.KEY_HEALTH));
        assertTrue(OpsPermissionMatrix.allows("R28", OpsPermissionMatrix.KEY_ALERTS));
        assertFalse(OpsPermissionMatrix.allows("R28", OpsPermissionMatrix.KEY_MANAGE));
    }
}
