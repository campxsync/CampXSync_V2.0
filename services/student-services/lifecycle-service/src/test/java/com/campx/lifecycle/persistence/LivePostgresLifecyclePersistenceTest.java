package com.campx.lifecycle.persistence;

import com.campx.lifecycle.config.TestEnvironmentGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * STM-04-EN-001 live persistence/RLS proof against azulbaxszsqxymqmlwfe.
 * Requires -Dcampx.approve.stm04.write=true. Ends in unconditional rollback.
 */
public class LivePostgresLifecyclePersistenceTest {

    private static final String APPROVED_DEV_REF = TestEnvironmentGuard.APPROVED_DEV_PROJECT_REF;
    public static final String APPROVAL_TOKEN = "APPROVE-STM04-LIVE-PERSISTENCE-TEST";

    private HikariDataSource createDevDataSource() {
        String jdbcUrl = System.getenv("CAMPX_DB_URL");
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            jdbcUrl = System.getenv("SUPABASE_JDBC_URL");
        }
        if (jdbcUrl != null && jdbcUrl.contains("ap-southeast-1")) {
            jdbcUrl = jdbcUrl.replace("ap-southeast-1", "ap-south-1");
        }
        String user = System.getenv("CAMPX_DB_USERNAME");
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("CAMPX_DB_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("SUPABASE_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            if (jdbcUrl != null && (jdbcUrl.contains("pooler.supabase.com") || jdbcUrl.contains("pooler.supabase.co"))) {
                user = "campx_app_server." + APPROVED_DEV_REF;
            } else {
                user = "campx_app_server";
            }
        }
        String pass = System.getenv("CAMPX_DB_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) {
            pass = System.getenv("SUPABASE_PASSWORD");
        }
        if (jdbcUrl == null || pass == null) {
            return null;
        }
        TestEnvironmentGuard.validateStagingConfiguration(jdbcUrl, user);
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(2);
        config.setPoolName("Live-Lifecycle-Persistence-Pool");
        return new HikariDataSource(config);
    }

    private void bindClaims(Connection conn, UUID tenantId, UUID userId, String role) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL ROLE authenticated");
        }
        String claims = "{\"sub\":\"" + userId + "\",\"tenant_id\":\"" + tenantId + "\",\"user_role\":\"" + role + "\"}";
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            ps.setString(1, claims);
            ps.execute();
        }
    }

    private boolean approved() {
        return Boolean.getBoolean("campx.approve.stm04.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));
    }

    @Test
    public void stm04En001_schemaOwnershipAndRlsForcedOnLifecycleTables() throws Exception {
        if (!approved()) {
            System.out.println("=== STM-04-EN-001 SCHEMA AUDIT GATED ===");
            System.out.println("Authorize with -Dcampx.approve.stm04.write=true");
            return;
        }
        HikariDataSource ds = createDevDataSource();
        assertNotNull("JDBC datasource must be configured for live STM-04 schema audit", ds);
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT c.relname AS table_name, c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced "
                             + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                             + "WHERE n.nspname = 'stu' AND c.relkind = 'r' AND c.relname LIKE 'lifecycle%' "
                             + "ORDER BY c.relname")) {
            try (ResultSet rs = ps.executeQuery()) {
                int counted = 0;
                while (rs.next()) {
                    counted++;
                    assertTrue("RLS must be enabled on " + rs.getString("table_name"), rs.getBoolean("rls_enabled"));
                    assertTrue("RLS must be FORCED on " + rs.getString("table_name"), rs.getBoolean("rls_forced"));
                }
                assertTrue("Expected lifecycle tables under stu schema", counted >= 2);
            }
        } finally {
            ds.close();
        }
    }

    @Test
    public void stm04En001_liveLifecyclePersistReadBackAndCrossTenantDenial() throws Exception {
        if (!approved()) {
            System.out.println("=== STM-04 LIVE JDBC TEST GATED ===");
            System.out.println("Authorize with -Dcampx.approve.stm04.write=true");
            return;
        }

        HikariDataSource ds = createDevDataSource();
        assertNotNull("JDBC datasource must be configured for live STM-04 test", ds);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        UUID userA = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID historyId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-14T10:00:00Z");

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bindClaims(conn, tenantA, userA, "REGISTRAR");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.lifecycle_status (tenant_id, student_id, status, effective_from, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?) "
                                + "ON CONFLICT (tenant_id, student_id) DO UPDATE SET "
                                + "status = EXCLUDED.status, effective_from = EXCLUDED.effective_from, updated_at = EXCLUDED.updated_at")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, studentId);
                    ps.setString(3, "ACTIVE");
                    ps.setTimestamp(4, Timestamp.from(now));
                    ps.setTimestamp(5, Timestamp.from(now));
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.lifecycle_history "
                                + "(tenant_id, id, student_id, status, action, actor_role, occurred_at) "
                                + "VALUES (?, ?, ?, ?, ?, ?, ?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, historyId);
                    ps.setObject(3, studentId);
                    ps.setString(4, "ACTIVE");
                    ps.setString(5, "STATUS_SET");
                    ps.setString(6, "REGISTRAR");
                    ps.setTimestamp(7, Timestamp.from(now));
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT status FROM stu.lifecycle_status WHERE tenant_id = ? AND student_id = ?")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("ACTIVE", rs.getString("status"));
                    }
                }

                bindClaims(conn, tenantB, UUID.randomUUID(), "REGISTRAR");
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) AS c FROM stu.lifecycle_status WHERE student_id = ?")) {
                    ps.setObject(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Cross-tenant read must return 0", 0, rs.getInt("c"));
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.lifecycle_status (tenant_id, student_id, status, effective_from, updated_at) "
                                + "VALUES (?, ?, ?, ?, ?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, UUID.randomUUID());
                    ps.setString(3, "ACTIVE");
                    ps.setTimestamp(4, Timestamp.from(now));
                    ps.setTimestamp(5, Timestamp.from(now));
                    ps.executeUpdate();
                    fail("Cross-tenant write should be denied by RLS");
                } catch (java.sql.SQLException expected) {
                    assertTrue("Expected RLS denial, got: " + expected.getMessage(),
                            expected.getMessage() != null
                                    && (expected.getMessage().toLowerCase().contains("policy")
                                    || expected.getMessage().toLowerCase().contains("row-level")
                                    || expected.getMessage().toLowerCase().contains("violat")
                                    || "42501".equals(expected.getSQLState())));
                }
            } finally {
                conn.rollback();
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("RESET ROLE");
                    stmt.execute("SELECT set_config('request.jwt.claims', '', true)");
                } catch (Exception ignored) {
                    // best effort
                }
                conn.setAutoCommit(true);
            }
        } finally {
            ds.close();
        }
    }
}
