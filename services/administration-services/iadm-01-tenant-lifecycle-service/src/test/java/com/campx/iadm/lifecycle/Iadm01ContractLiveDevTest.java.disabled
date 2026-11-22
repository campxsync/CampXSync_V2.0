package com.campx.iadm.lifecycle;

import com.campx.iadm.lifecycle.config.DatabaseConfig;
import com.campx.iadm.lifecycle.config.DatabaseConnectionManager;
import com.campx.iadm.lifecycle.exception.InstituteAdminException;
import com.campx.iadm.lifecycle.security.UserSecurityContext;
import com.campx.iadm.lifecycle.service.TenantLifecycleService;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Wave E0 contract 1: First College Admin + handover (IADM-01-US-003/004, SEC-11).
 * Mutations via domain service are cleaned up; permission denials use rolled-back sessions.
 */
public class Iadm01ContractLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    /** Institution Admin (R03) seeded persona — may lack auth.users row; claims still drive RLS. */
    private static final UUID INSTITUTION_ADMIN = UUID.fromString("c03a0000-0000-4000-8000-000000000003");
    /** College Admin (R04) — Auth-linked. */
    private static final UUID COLLEGE_ADMIN = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private static final UUID FAKE_AUTH_USER = UUID.fromString("eeeeeeee-eeee-4eee-8eee-eeeeeeeeeeee");

    private static boolean dbReady;

    @BeforeClass
    public static void setup() {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
    }

    @AfterClass
    public static void teardown() {
        if (dbReady) DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void collegeAdminDeniedAdm01Manage() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(COLLEGE_ADMIN, TENANT, false);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission('adm01.manage')");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertFalse("College Admin (R04) must not hold adm01.manage (handover denied)", rs.getBoolean(1));
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    @Test
    public void sec11RejectsNonAuthLinkedFirstCollegeAdmin() {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        TenantLifecycleService service = new TenantLifecycleService();
        try {
            service.onboardCollege(ctx, "E0-SEC11-" + System.currentTimeMillis(), "E0 Sec11 College", FAKE_AUTH_USER);
            fail("Expected IADM01_AUTH_USER_REQUIRED");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM01_AUTH_USER_REQUIRED", ex.getErrorCode());
            assertEquals(400, ex.getStatus());
        } catch (RuntimeException ex) {
            Throwable c = ex.getCause() != null ? ex.getCause() : ex;
            assertTrue("Expected AUTH_USER_REQUIRED, got: " + c,
                    c.getMessage() != null && c.getMessage().contains("AUTH_USER_REQUIRED")
                            || (c instanceof InstituteAdminException
                            && "IADM01_AUTH_USER_REQUIRED".equals(((InstituteAdminException) c).getErrorCode())));
        }
    }

    @Test
    public void institutionAdminOnboardAndCompleteHandover_thenCleanup() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        TenantLifecycleService service = new TenantLifecycleService();
        String code = "E0H" + (System.currentTimeMillis() % 1000000);
        Map<String, Object> onboard = service.onboardCollege(ctx, code, "E0 Handover College", COLLEGE_ADMIN);
        assertNotNull(onboard.get("collegeId"));
        assertNotNull(onboard.get("handoverId"));
        UUID collegeId = UUID.fromString(onboard.get("collegeId").toString());
        try {
            Map<String, Object> done = service.completeHandover(ctx, collegeId, "E0 contract proof");
            assertEquals("COMPLETED", done.get("status"));
            assertEquals(collegeId.toString(), done.get("collegeId"));
            // Read-back handover row
            try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    ctx.applyClaimsAndRole(conn);
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT status FROM plat.college_admin_handovers WHERE college_id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                        ps.setObject(1, collegeId);
                        ps.setObject(2, TENANT);
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next());
                            assertEquals("COMPLETED", rs.getString(1));
                        }
                    }
                } finally {
                    conn.rollback();
                    conn.setAutoCommit(true);
                }
            }
        } finally {
            cleanupCollege(collegeId);
        }
    }

    private static void cleanupCollege(UUID collegeId) throws Exception {
        UserSecurityContext platform = UserSecurityContext.forPlatformAdmin(
                UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971"));
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                // Use service_role path via platform claims where possible; soft-delete
                platform.applyClaimsAndRole(conn);
                try (PreparedStatement h = conn.prepareStatement(
                        "UPDATE plat.college_admin_handovers SET deleted_at = now() WHERE college_id = ?");
                     PreparedStatement ra = conn.prepareStatement(
                        "UPDATE iam.role_assignments SET deleted_at = now() WHERE college_id = ? AND deleted_at IS NULL");
                     PreparedStatement c = conn.prepareStatement(
                        "UPDATE core.colleges SET deleted_at = now() WHERE id = ? AND deleted_at IS NULL")) {
                    h.setObject(1, collegeId);
                    h.executeUpdate();
                    ra.setObject(1, collegeId);
                    ra.executeUpdate();
                    c.setObject(1, collegeId);
                    c.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
                // Best-effort cleanup; do not fail the contract assertion
            } finally {
                conn.setAutoCommit(true);
            }
        }
    }
}
