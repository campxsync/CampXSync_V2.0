package com.campx.iadm.access;

import com.campx.iadm.access.security.GrantorCeiling;
import org.junit.Test;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/**
 * IADM-03 S2 — US-017 ceiling denials (unit).
 */
public class GrantorCeilingDenialTest {

    @Test
    public void r03CannotAssignPlatformOrPeerAdmins() {
        assertNotNull(GrantorCeiling.denyReason("R03", "SUPER_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "SYSTEM_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("R03", "INSTITUTION_ADMIN", false));
        assertTrue(GrantorCeiling.denyReason("R03", "R01", false).contains("cannot assign"));
    }

    @Test
    public void r03CannotProvisionStudentsOrApplicants() {
        assertTrue(GrantorCeiling.denyReason("R03", "STUDENT", false).contains("STM-07"));
        assertTrue(GrantorCeiling.denyReason("R03", "APPLICANT", false).contains("STM-02"));
    }

    @Test
    public void selfGrantAlwaysDenied() {
        assertNotNull(GrantorCeiling.denyReason("R01", "INSTITUTION_ADMIN", true));
        assertNotNull(GrantorCeiling.denyReason("R03", "FACULTY", true));
    }

    @Test
    public void r03MayAssignEligibleBusinessRoles() {
        assertNull(GrantorCeiling.denyReason("R03", "FACULTY", false));
        assertNull(GrantorCeiling.denyReason("R03", "COLLEGE_ADMIN", false));
        assertNull(GrantorCeiling.denyReason("R03", "REGISTRAR", false));
    }

    @Test
    public void r02TechnicalOnly() {
        assertNull(GrantorCeiling.denyReason("R02", "SERVICE", false));
        assertNull(GrantorCeiling.denyReason("R02", "SVC-BATCH", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "FACULTY", false));
        assertNotNull(GrantorCeiling.denyReason("R02", "INSTITUTION_ADMIN", false));
    }

    @Test
    public void r01AssignsR02AndR03Only() {
        assertNull(GrantorCeiling.denyReason("R01", "SYSTEM_ADMIN", false));
        assertNull(GrantorCeiling.denyReason("R01", "INSTITUTION_ADMIN", false));
        assertNotNull(GrantorCeiling.denyReason("R01", "FACULTY", false));
        assertNotNull(GrantorCeiling.denyReason("R01", "R27", false));
    }
}
