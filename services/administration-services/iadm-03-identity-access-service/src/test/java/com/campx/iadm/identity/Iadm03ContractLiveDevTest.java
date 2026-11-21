package com.campx.iadm.identity;

import com.campx.iadm.identity.config.DatabaseConfig;
import com.campx.iadm.identity.config.DatabaseConnectionManager;
import com.campx.iadm.identity.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Wave E0 contract 3: Role ceiling lookup (IADM-03-US-002 / CADM-03).
 */
public class Iadm03ContractLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID INSTITUTION_ADMIN = UUID.fromString("c03a0000-0000-4000-8000-000000000003");
    private static final UUID STUDENT = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");

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
    public void institutionAdminCanInsertAndReadCeiling_rolledBack() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(INSTITUTION_ADMIN, TENANT, false);
        String roleCode = "E0_CEIL_" + (System.currentTimeMillis() % 100000);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement perm = conn.prepareStatement("SELECT iam.has_permission('adm09.manage')");
                     ResultSet pr = perm.executeQuery()) {
                    assertTrue(pr.next());
                    assertTrue("Institution Admin (R03) must hold adm09.manage", pr.getBoolean(1));
                }
                UUID id = UUID.randomUUID();
                try (PreparedStatement ins = conn.prepareStatement(
                        "INSERT INTO iam.role_ceilings (id, tenant_id, role_code, allowed_permission_keys, created_by) " +
                                "VALUES (?,?,?, ARRAY['adm05.view']::text[], ?)")) {
                    ins.setObject(1, id);
                    ins.setObject(2, TENANT);
                    ins.setString(3, roleCode);
                    ins.setObject(4, INSTITUTION_ADMIN);
                    assertEquals(1, ins.executeUpdate());
                }
                try (PreparedStatement sel = conn.prepareStatement(
                        "SELECT role_code, allowed_permission_keys[1] FROM iam.role_ceilings " +
                                "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                    sel.setObject(1, id);
                    sel.setObject(2, TENANT);
                    try (ResultSet rs = sel.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals(roleCode, rs.getString(1));
                        assertEquals("adm05.view", rs.getString(2));
                    }
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }

    @Test
    public void studentDeniedAdm09Manage() throws Exception {
        UserSecurityContext ctx = new UserSecurityContext(STUDENT, TENANT, false);
        try (Connection conn = DatabaseConnectionManager.getInstance().getConnection()) {
            conn.setAutoCommit(false);
            try {
                ctx.applyClaimsAndRole(conn);
                try (PreparedStatement ps = conn.prepareStatement("SELECT iam.has_permission('adm09.manage')");
                     ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertFalse("Student (R25) must not hold adm09.manage (ceiling write denied)", rs.getBoolean(1));
                }
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }
}
