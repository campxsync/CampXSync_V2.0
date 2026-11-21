package com.campx.iadm.identity;

import com.campx.iadm.identity.config.DatabaseConfig;
import com.campx.iadm.identity.config.DatabaseConnectionManager;
import com.campx.iadm.identity.exception.InstituteAdminException;
import com.campx.iadm.identity.security.UserSecurityContext;
import com.campx.iadm.identity.service.Iadm03DomainService;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * IADM-03-US-009 live DEV: R01 requests, R02 approves Institution Admin appoint (OWNER-DEC-TP-01).
 */
public class Iadm03QuorumLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID R01 = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");
    /** Ephemeral DEV R02 — faculty auth user; granted SYSTEM_ADMIN only for this test class. */
    private static final UUID R02 = UUID.fromString("619b460b-d7f9-46b6-b11a-56045a130a52");
    private static final UUID TARGET = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");

    private static boolean dbReady;
    private static boolean seededR02;
    private static final Iadm03DomainService service = new Iadm03DomainService();

    @BeforeClass
    public static void setup() throws Exception {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement chk = conn.prepareStatement(
                     "SELECT 1 FROM plat.platform_admins WHERE user_id = ? AND deleted_at IS NULL");
             PreparedStatement ins = conn.prepareStatement(
                     "INSERT INTO plat.platform_admins (user_id, full_name, role_code, status) " +
                             "VALUES (?, 'DEV ephemeral System Admin (R02) for IADM-03-US-009', 'SYSTEM_ADMIN', 'ACTIVE')")) {
            chk.setObject(1, R02);
            try (ResultSet rs = chk.executeQuery()) {
                if (!rs.next()) {
                    ins.setObject(1, R02);
                    ins.executeUpdate();
                    seededR02 = true;
                }
            }
        }
    }

    @AfterClass
    public static void teardown() throws Exception {
        if (!dbReady) return;
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            try (PreparedStatement delReq = conn.prepareStatement(
                    "UPDATE plat.institution_admin_quorum_requests SET deleted_at = now() " +
                            "WHERE target_user_id = ? AND reason LIKE 'IADM-03-US-009 live%' AND deleted_at IS NULL")) {
                delReq.setObject(1, TARGET);
                delReq.executeUpdate();
            }
            try (PreparedStatement revoke = conn.prepareStatement(
                    "UPDATE iam.role_assignments ra SET deleted_at = now() FROM iam.roles r " +
                            "WHERE ra.role_id = r.id AND ra.tenant_id = ? AND ra.user_id = ? " +
                            "AND r.code = 'INSTITUTION_ADMIN' AND ra.deleted_at IS NULL " +
                            "AND ra.created_by IN (?, ?)")) {
                revoke.setObject(1, TENANT);
                revoke.setObject(2, TARGET);
                revoke.setObject(3, R01);
                revoke.setObject(4, R02);
                revoke.executeUpdate();
            }
            if (seededR02) {
                try (PreparedStatement del = conn.prepareStatement(
                        "UPDATE plat.platform_admins SET deleted_at = now(), status = 'INACTIVE' " +
                                "WHERE user_id = ? AND role_code = 'SYSTEM_ADMIN' " +
                                "AND full_name LIKE 'DEV ephemeral System Admin%' AND deleted_at IS NULL")) {
                    del.setObject(1, R02);
                    del.executeUpdate();
                }
            }
        } finally {
            DatabaseConnectionManager.resetInstance();
        }
    }

    @Test
    public void r01RequestR02ApproveAppoint_thenSameTrackRejected() {
        UserSecurityContext r01 = UserSecurityContext.forPlatformAdmin(R01);
        UserSecurityContext r02 = UserSecurityContext.forPlatformAdmin(R02);

        Map<String, Object> req = service.requestInstitutionAdminQuorum(r01, TENANT, TARGET, "APPOINT",
                "IADM-03-US-009 live appoint " + System.currentTimeMillis());
        assertEquals("PENDING", req.get("status"));
        assertEquals("R01", req.get("requestedTrack"));
        UUID requestId = UUID.fromString(req.get("requestId").toString());

        try {
            service.decideInstitutionAdminQuorum(r01, requestId, "APPROVE", "same track");
            fail("R01 must not approve R01 request");
        } catch (InstituteAdminException ex) {
            assertTrue(ex.getErrorCode().contains("QUORUM"));
        }

        Map<String, Object> decided = service.decideInstitutionAdminQuorum(r02, requestId, "APPROVE", "R02 confirms");
        assertEquals("APPROVED", decided.get("status"));
        assertEquals("R02", decided.get("decidedTrack"));
        assertNotNull(decided.get("assignmentId"));
        UUID assignmentId = UUID.fromString(decided.get("assignmentId").toString());

        // Read-back via pool (service) — role_assignments RLS is tenant-permission scoped, not platform
        boolean holds;
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT 1 FROM iam.role_assignments ra JOIN iam.roles r ON r.id = ra.role_id " +
                             "WHERE ra.id = ? AND ra.user_id = ? AND r.code = 'INSTITUTION_ADMIN' " +
                             "AND ra.deleted_at IS NULL")) {
            ps.setObject(1, assignmentId);
            ps.setObject(2, TARGET);
            try (ResultSet rs = ps.executeQuery()) {
                holds = rs.next();
            }
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertTrue("Target must hold INSTITUTION_ADMIN after quorum approve", holds);

        // Cleanup remove via quorum
        Map<String, Object> rem = service.requestInstitutionAdminQuorum(r02, TENANT, TARGET, "REMOVE",
                "IADM-03-US-009 live remove " + System.currentTimeMillis());
        UUID remId = UUID.fromString(rem.get("requestId").toString());
        Map<String, Object> remDone = service.decideInstitutionAdminQuorum(r01, remId, "APPROVE", "R01 confirms remove");
        assertEquals("APPROVED", remDone.get("status"));
    }

    @Test
    public void nonPlatformDenied() throws Exception {
        UserSecurityContext college = new UserSecurityContext(
                UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4"), TENANT, false);
        try {
            service.requestInstitutionAdminQuorum(college, TENANT, TARGET, "APPOINT", "should fail");
            fail("College admin must not open quorum");
        } catch (Exception ex) {
            assertTrue(ex.getMessage() != null || ex instanceof InstituteAdminException);
        }
    }

    @Test
    public void platformAdminTrackFunction_r01() throws Exception {
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection();
             Statement st = conn.createStatement()) {
            st.execute("SET LOCAL ROLE authenticated");
        }
        UserSecurityContext ctx = UserSecurityContext.forPlatformAdmin(R01);
        String track = ctx.executeInTransaction(DatabaseConnectionManager.getInstance(), conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT plat.platform_admin_track(?)")) {
                ps.setObject(1, R01);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    return rs.getString(1);
                }
            }
        });
        assertEquals("R01", track);
    }
}
