package com.campx.iadm.access;

import com.campx.iadm.access.security.AccessPermissionMatrix;
import com.campx.iadm.access.security.GrantorCeiling;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * IADM-03-US-033 — Auditor (R28) read keys only; mutations denied.
 */
public class Us033AuditorReadonlyTest {

    @Test
    public void auditorHasEvidenceReadKeys() {
        assertTrue(AccessPermissionMatrix.allows("R28", "iam.audit.view"));
        assertTrue(AccessPermissionMatrix.allows("R28", "iam.catalog.roles.view"));
        assertTrue(AccessPermissionMatrix.allows("R28", "iam.catalog.permissions.view"));
        assertTrue(AccessPermissionMatrix.allows("R28", "iam.access.subject.view"));
        assertTrue(AccessPermissionMatrix.allows("R28", "iam.profile.view"));
    }

    @Test
    public void auditorLacksAllMutationKeys() {
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.profile.invite"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.profile.status"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.grant.assign"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.grant.revoke"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.request.decide"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.delegation.create"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.catalog.change.propose"));
        assertFalse(AccessPermissionMatrix.allows("R28", "iam.review.manage"));
    }

    @Test
    public void auditorCeilingMessageIsClear() {
        assertEquals("Auditor access is read-only.",
                GrantorCeiling.denyReason("R28", "FACULTY", false));
    }
}
