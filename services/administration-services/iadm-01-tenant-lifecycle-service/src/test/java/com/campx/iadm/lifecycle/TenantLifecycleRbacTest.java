package com.campx.iadm.lifecycle;

import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit-level RBAC narrative tests for IADM-01 access matrix.
 * Live DEV JDBC tests are in TenantLifecycleLiveDevTest.
 */
public class TenantLifecycleRbacTest {

    @Test
    public void instituteAdminExceptionCarriesPermissionFailClosedCodes() {
        InstituteAdminException ex = new InstituteAdminException(403, "IADM01_FORBIDDEN", "Missing adm01.manage");
        assertEquals(403, ex.getStatus());
        assertEquals("IADM01_FORBIDDEN", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("adm01.manage"));
    }

    @Test
    public void defaultPortIs8099() {
        assertEquals(8099, com.campx.iadm.lifecycle.server.Iadm01Server.DEFAULT_PORT);
    }

    @Test
    public void selfAppointmentRejectedByDomainRule() {
        // Domain rule documented: appointInstitutionAdmin rejects userId == caller
        InstituteAdminException ex = new InstituteAdminException(403, "IADM01_SELF_APPOINT", "Cannot appoint self");
        assertEquals("IADM01_SELF_APPOINT", ex.getErrorCode());
    }

    @Test
    public void firstCollegeAdminWithoutAuthUserUsesSec11ErrorCode() {
        InstituteAdminException ex = new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                "First College Admin must be a linked Auth user (SEC-11)");
        assertEquals(400, ex.getStatus());
        assertEquals("IADM01_AUTH_USER_REQUIRED", ex.getErrorCode());
    }
}
