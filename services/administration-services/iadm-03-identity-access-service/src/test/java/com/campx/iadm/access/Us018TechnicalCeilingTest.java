package com.campx.iadm.access;

import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import org.junit.Test;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * IADM-03-US-018 — System Admin (R02) allow/deny matrix for technical identities.
 */
public class Us018TechnicalCeilingTest {

    @Test
    public void r02MayAssignTechnicalIdentities() {
        assertNull(GrantorCeiling.denyReason("R02", "SERVICE", false));
        assertNull(GrantorCeiling.denyReason("R02", "TECHNICAL", false));
        assertNull(GrantorCeiling.denyReason("R02", "SVC-BATCH", false));
    }

    @Test
    public void r02DeniedTenantBusinessAndAdminRoles() {
        assertNotNull(GrantorCeiling.denyReason("R02", "FACULTY", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "INSTITUTION_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "COLLEGE_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "R03", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "R04", false));
        assertTrue(GrantorCeiling.denyReason("R02", "FACULTY", false)
                .contains("platform technical service identities only"));
    }

    @Test
    public void r02LacksCommercialAndGrantKeys() {
        assertTrue(AccessPermissionMatrix.allows("R02", "iam.identity.technical.assign"));
        assertFalse(AccessPermissionMatrix.allows("R02", "iam.grant.assign"));
        assertFalse(AccessPermissionMatrix.allows("R02", "iam.request.decide"));
        assertFalse(AccessPermissionMatrix.allows("R02", "iam.catalog.change.approve"));
        assertFalse(AccessPermissionMatrix.allows("R02", "iam.delegation.create"));
    }

    @Test
    public void r02HasNoDelegationApprovalAuthority() {
        assertFalse(AccessPermissionMatrix.allows("R02", "iam.delegation.revoke"));
        assertFalse(AccessPermissionMatrix.allows("R02", "iam.grant.r03.decide"));
    }
}
