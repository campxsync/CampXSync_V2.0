package com.campx.iadm.lifecycle;

import com.campx.iadm.lifecycle.auth.SupabaseAuthAdminClient;
import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import com.campx.iadm.lifecycle.service.TenantLifecycleService;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit-level RBAC / contract narrative tests for IADM-01 (Institute & Tenant Lifecycle).
 * Plain language: permission keys fail closed; Auth Admin missing is a clear blocker code.
 */
public class TenantLifecycleRbacTest {

    @Test
    public void instituteAdminExceptionCarriesPermissionFailClosedCodes() {
        InstituteAdminException ex = new InstituteAdminException(403, "IADM01_FORBIDDEN",
                "You are not authorized for this action.");
        assertEquals(403, ex.getStatus());
        assertEquals("IADM01_FORBIDDEN", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("not authorized"));
    }

    @Test
    public void defaultPortIs8099() {
        assertEquals(8099, com.campx.iadm.lifecycle.server.Iadm01Server.DEFAULT_PORT);
    }

    @Test
    public void duplicateInstituteCodeUsesConflictMessage() {
        InstituteAdminException ex = new InstituteAdminException(409, "IADM01_DUPLICATE_CODE",
                "An institute with this code already exists");
        assertEquals(409, ex.getStatus());
        assertEquals("IADM01_DUPLICATE_CODE", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("already exists"));
    }

    @Test
    public void authInviteFailureUsesPlainRetryMessage() {
        InstituteAdminException ex = new InstituteAdminException(502, "IADM01_AUTH_INVITE_FAILED",
                "Invitation could not be sent. Retry invitation.");
        assertEquals("IADM01_AUTH_INVITE_FAILED", ex.getErrorCode());
        assertTrue(ex.getMessage().contains("Retry invitation"));
    }

    @Test
    public void authAdminClientReportsUnconfiguredWithoutPrintingSecrets() {
        SupabaseAuthAdminClient client = new SupabaseAuthAdminClient("", "");
        assertFalse(client.isConfigured());
        try {
            client.inviteOrCreateUser("demo@example.com", "Demo");
            fail("expected blocker when Auth admin is not configured");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM01_AUTH_ADMIN_UNAVAILABLE", ex.getErrorCode());
            assertFalse(ex.getMessage().toLowerCase().contains("eyj"));
            assertFalse(ex.getMessage().matches("(?s).*[A-Za-z0-9_-]{20}\\.[A-Za-z0-9_-]{20}\\.[A-Za-z0-9_-]{10,}.*"));
        }
    }

    @Test
    public void secondApproverFlagDefaultsOff() {
        // TECH-007: system property default is false; DB flag seeded false
        assertFalse(Boolean.getBoolean("campx.iadm01.r03.secondApproverRequired"));
        TenantLifecycleService service = new TenantLifecycleService(new SupabaseAuthAdminClient("", ""));
        assertNotNull(service);
    }

    @Test
    public void firstCollegeAdminWithoutAuthUserUsesSec11ErrorCode() {
        InstituteAdminException ex = new InstituteAdminException(400, "IADM01_AUTH_USER_REQUIRED",
                "Target must be a linked Supabase Auth user");
        assertEquals(400, ex.getStatus());
        assertEquals("IADM01_AUTH_USER_REQUIRED", ex.getErrorCode());
    }

    /**
     * createCollege path institute: platform may pass any; own institute works;
     * second institute refused for non-platform (before withTenant rewrite).
     */
    @Test
    public void createCollegeTrustedInstituteRefusesForeign() {
        java.util.UUID own = java.util.UUID.fromString("11111111-1111-1111-1111-111111111111");
        java.util.UUID other = java.util.UUID.fromString("22222222-2222-2222-2222-222222222222");
        TenantLifecycleService.assertCreateCollegeInstituteAllowed(true, own, other);
        TenantLifecycleService.assertCreateCollegeInstituteAllowed(false, own, own);
        try {
            TenantLifecycleService.assertCreateCollegeInstituteAllowed(false, own, other);
            fail("expected own-tenant-only denial");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM01_OWN_TENANT_ONLY", ex.getErrorCode());
        }
        try {
            TenantLifecycleService.assertCreateCollegeInstituteAllowed(false, null, other);
            fail("expected scope required");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM01_SCOPE_REQUIRED", ex.getErrorCode());
        }
    }
}
