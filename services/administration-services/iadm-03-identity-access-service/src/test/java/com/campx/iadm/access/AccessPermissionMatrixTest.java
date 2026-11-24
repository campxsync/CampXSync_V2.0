package com.campx.iadm.access;

import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * Allowed and denied unit tests for every new iam.* key across personas (US-003).
 */
public class AccessPermissionMatrixTest {

    @Test
    public void everyNewKeyHasAllowAndDenyCoverage() {
        int asserts = 0;
        for (String key : AccessPermissionMatrix.NEW_KEYS) {
            boolean sawAllow = false;
            boolean sawDeny = false;
            for (String persona : AccessPermissionMatrix.PERSONAS) {
                boolean allowed = AccessPermissionMatrix.allows(persona, key);
                if (allowed) {
                    sawAllow = true;
                } else {
                    sawDeny = true;
                }
                asserts++;
            }
            assertTrue("key " + key + " must have at least one allow", sawAllow);
            assertTrue("key " + key + " must have at least one deny", sawDeny);
        }
        assertEquals(AccessPermissionMatrix.NEW_KEYS.length * AccessPermissionMatrix.PERSONAS.length, asserts);
    }

    @Test
    public void r01AllowsAllNewKeys() {
        for (String key : AccessPermissionMatrix.NEW_KEYS) {
            assertTrue(key, AccessPermissionMatrix.allows("R01", key));
            assertTrue(key, AccessPermissionMatrix.allows("SUPER_ADMIN", key));
        }
    }

    @Test
    public void r04RequestOnly() {
        assertTrue(AccessPermissionMatrix.allows("R04", "iam.request.submit"));
        assertFalse(AccessPermissionMatrix.allows("COLLEGE_ADMIN", "iam.grant.assign"));
        assertFalse(AccessPermissionMatrix.allows("R04", "iam.profile.invite"));
        assertFalse(AccessPermissionMatrix.allows("R04", "iam.delegation.create"));
    }

    @Test
    public void r28ReadOnly() {
        assertTrue(AccessPermissionMatrix.allows("R28", "iam.audit.view"));
        assertTrue(AccessPermissionMatrix.allows("AUDITOR", "iam.catalog.roles.view"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.grant.assign"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.profile.status"));
    }

    @Test
    public void r27DeniedAllKeys() {
        for (String key : AccessPermissionMatrix.NEW_KEYS) {
            assertFalse(key, AccessPermissionMatrix.allows("R27", key));
            assertFalse(key, AccessPermissionMatrix.allows("API_CLIENT", key));
        }
    }

    @Test
    public void adm03FamilyNotInNewKeys() {
        for (String key : AccessPermissionMatrix.NEW_KEYS) {
            assertFalse(key.startsWith("adm03."));
        }
    }

    @Test
    public void grantorCeilingR03CannotAssignR01R02R03() {
        assertNotNull(GrantorCeiling.denyReason("R03", "R01", false));
        assertNotNull(GrantorCeiling.denyReason("INSTITUTION_ADMIN", "SYSTEM_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "INSTITUTION_ADMIN", false));
    }

    @Test
    public void grantorCeilingSelfGrantDenied() {
        assertNotNull(GrantorCeiling.denyReason("R01", "R03", true));
    }

    @Test
    public void grantorCeilingR01MayAssignR02R03() {
        assertNull(GrantorCeiling.denyReason("R01", "R02", false));
        assertNull(GrantorCeiling.denyReason("SUPER_ADMIN", "INSTITUTION_ADMIN", false));
    }

    @Test
    public void grantorCeilingR27Parked() {
        assertNotNull(GrantorCeiling.denyReason("R01", "R27", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "API_CLIENT", false));
    }

    @Test
    public void grantorCeilingR02NoTenantBusiness() {
        assertNotNull(GrantorCeiling.denyReason("R02", "COLLEGE_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("SYSTEM_ADMIN", "R03", false));
    }
}
