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
 * Wave E1-A: post-handover R03 college user-admin boundary, own-tenant get, suspend session block.
 */
public class Iadm01WaveE1ALiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION_ADMIN = UUID.fromString("c03a0000-0000-4000-8000-000000000003");
    private static final UUID COLLEGE_ADMIN = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private static final UUID PLATFORM_ADMIN = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
    private static final UUID OTHER_TENANT = UUID.fromString("b0000000-0000-0000-0000-000000000002");

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
    public void ownTenantOnly_deniesCrossInstituteRead() {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        TenantLifecycleService service = new TenantLifecycleService();
        try {
            service.getInstitute(ctx, OTHER_TENANT);
            fail("Expected IADM01_OWN_TENANT_ONLY");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM01_OWN_TENANT_ONLY", ex.getErrorCode());
            assertEquals(403, ex.getStatus());
        } catch (RuntimeException ex) {
            Throwable c = ex.getCause() != null ? ex.getCause() : ex;
            assertTrue(c instanceof InstituteAdminException
                    && "IADM01_OWN_TENANT_ONLY".equals(((InstituteAdminException) c).getErrorCode()));
        }
    }

    @Test
    public void handoverCompletes_andBoundaryRevokesR03CollegeUserAdmin() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        TenantLifecycleService service = new TenantLifecycleService();
        String code = "E1A" + (System.currentTimeMillis() % 1000000);
        Map<String, Object> onboard = service.onboardCollege(ctx, code, "E1A Boundary College", COLLEGE_ADMIN);
        UUID collegeId = UUID.fromString(onboard.get("collegeId").toString());
        try {
            Map<String, Object> before = service.collegeUserAdminBoundary(ctx, collegeId);
            assertEquals(Boolean.FALSE, before.get("handedOver"));
            assertEquals(Boolean.TRUE, before.get("r03CollegeUserAdminAllowed"));

            Map<String, Object> done = service.completeHandover(ctx, collegeId, "E1-A boundary proof");
            assertEquals("COMPLETED", done.get("status"));
            assertEquals("REVOKED", done.get("r03CollegeUserAdmin"));

            Map<String, Object> after = service.collegeUserAdminBoundary(ctx, collegeId);
            assertEquals(Boolean.TRUE, after.get("handedOver"));
            assertEquals(Boolean.FALSE, after.get("r03CollegeUserAdminAllowed"));

            try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
                conn.setAutoCommit(false);
                try {
                    ctx.applyClaimsAndRole(conn);
                    try (PreparedStatement ps = conn.prepareStatement("SELECT plat.is_college_handed_over(?)")) {
                        ps.setObject(1, collegeId);
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next());
                            assertTrue(rs.getBoolean(1));
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

    @Test
    public void suspendBlocksTenantSession_thenReactivate() throws Exception {
        // Use a dedicated throwaway tenant so pilot tenant is never left suspended
        UserSecurityContext platform = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN);
        TenantLifecycleService service = new TenantLifecycleService();
        String code = "E1S" + (System.currentTimeMillis() % 1000000);
        Map<String, Object> created = service.registerInstitute(platform, code, "E1A Suspend Probe", "TEST");
        UUID tenantId = UUID.fromString(created.get("tenantId").toString());
        try {
            Map<String, Object> suspended = service.suspendOrReactivate(platform, tenantId, "suspend", "E1-A session block proof");
            assertEquals("SUSPENDED", suspended.get("status"));
            assertEquals(Boolean.TRUE, suspended.get("sessionsBlocked"));

            UserSecurityContext tenantCtx = new UserSecurityContext(INSTITUTION_ADMIN, tenantId, false);
            try {
                service.getInstitute(tenantCtx, tenantId);
                fail("Expected IADM01_SUSPENDED for non-platform caller");
            } catch (InstituteAdminException ex) {
                assertEquals("IADM01_SUSPENDED", ex.getErrorCode());
            } catch (RuntimeException ex) {
                Throwable c = ex.getCause() != null ? ex.getCause() : ex;
                assertTrue("Expected SUSPENDED, got: " + c,
                        c instanceof InstituteAdminException
                                && "IADM01_SUSPENDED".equals(((InstituteAdminException) c).getErrorCode())
                                || (c.getMessage() != null && c.getMessage().contains("SUSPENDED")));
            }

            Map<String, Object> active = service.suspendOrReactivate(platform, tenantId, "reactivate", "E1-A restore");
            assertEquals("ACTIVE", active.get("status"));
            assertEquals(Boolean.FALSE, active.get("sessionsBlocked"));
        } finally {
            softDeleteTenant(tenantId);
        }
    }

    private static void cleanupCollege(UUID collegeId) {
        UserSecurityContext platform = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
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
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ignored) {
        }
    }

    private static void softDeleteTenant(UUID tenantId) {
        UserSecurityContext platform = UserSecurityContext.forPlatformAdmin(PLATFORM_ADMIN);
        try {
            // Ensure not left suspended
            new TenantLifecycleService().suspendOrReactivate(platform, tenantId, "reactivate", "cleanup");
        } catch (Exception ignored) {
        }
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                platform.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE core.tenants SET deleted_at = now(), status = 'ACTIVE' WHERE id = ?")) {
                    ps.setObject(1, tenantId);
                    ps.executeUpdate();
                }
                conn.commit();
            } catch (Exception e) {
                conn.rollback();
            } finally {
                conn.setAutoCommit(true);
            }
        } catch (Exception ignored) {
        }
    }
}
