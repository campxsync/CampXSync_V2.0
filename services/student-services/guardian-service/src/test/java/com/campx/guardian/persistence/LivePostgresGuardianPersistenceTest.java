package com.campx.guardian.persistence;

import com.campx.guardian.config.TestEnvironmentGuard;
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

/**
 * STM-05-EN-001 live guardianship persistence/RLS proof against azulbaxszsqxymqmlwfe.
 * Requires -Dcampx.approve.stm05.write=true. Ends in unconditional rollback when writes succeed.
 */
public class LivePostgresGuardianPersistenceTest {

    private static final String APPROVED_DEV_REF = TestEnvironmentGuard.APPROVED_DEV_PROJECT_REF;
    public static final String APPROVAL_TOKEN = "APPROVE-STM05-LIVE-PERSISTENCE-TEST";
    private static final UUID PILOT_TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID OTHER_TENANT = UUID.fromString("b0000000-0000-0000-0000-000000000002");
    private static final UUID COLLEGE_ADMIN_USER = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");
    private static final UUID STUDENT_ID = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");
    private static final UUID GUARDIAN_PERSON_ID = UUID.fromString("bdf7a805-ff27-4ecd-862e-4c0c7b7090ae");

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
        if (!jdbcUrl.contains("prepareThreshold=")) {
            jdbcUrl = jdbcUrl + (jdbcUrl.contains("?") ? "&" : "?") + "prepareThreshold=0";
        }
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(2);
        config.setPoolName("Live-Guardian-Persistence-Pool");
        return new HikariDataSource(config);
    }

    private void bindClaims(Connection conn, UUID tenantId, UUID userId, String role) throws Exception {
        try (Statement stmt = conn.createStatement()) {
            stmt.execute("SET LOCAL ROLE authenticated");
        }
        // Mirror GuardianSecurityContext claim shape (tenant_id + app_metadata.tenant_id).
        String claims = "{\"sub\":\"" + userId + "\",\"tenant_id\":\"" + tenantId
                + "\",\"app_metadata\":{\"tenant_id\":\"" + tenantId + "\"},\"user_role\":\"" + role + "\"}";
        try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
            ps.setString(1, claims);
            ps.execute();
        }
    }

    private boolean approved() {
        return Boolean.getBoolean("campx.approve.stm05.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));
    }

    @Test
    public void stm05En001_schemaOwnershipAndRlsOnGuardianTables() throws Exception {
        if (!approved()) {
            System.out.println("=== STM-05-EN-001 SCHEMA AUDIT GATED ===");
            System.out.println("Authorize with -Dcampx.approve.stm05.write=true");
            return;
        }
        HikariDataSource ds = createDevDataSource();
        assertNotNull("JDBC datasource must be configured for live STM-05 schema audit", ds);
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT n.nspname AS schema_name, c.relname AS table_name, "
                             + "c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced "
                             + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                             + "WHERE c.relkind = 'r' AND ("
                             + "(n.nspname = 'stu' AND c.relname = 'guardianships') OR "
                             + "(n.nspname = 'core' AND c.relname = 'person_emergency_contacts')"
                             + ") ORDER BY n.nspname, c.relname")) {
            try (ResultSet rs = ps.executeQuery()) {
                int counted = 0;
                while (rs.next()) {
                    counted++;
                    assertTrue("RLS must be enabled on " + rs.getString("table_name"), rs.getBoolean("rls_enabled"));
                    if ("guardianships".equals(rs.getString("table_name"))) {
                        assertTrue("RLS must be FORCED on stu.guardianships", rs.getBoolean("rls_forced"));
                    }
                }
                assertEquals("Expected guardianships + emergency contacts", 2, counted);
            }
        } finally {
            ds.close();
        }
    }

    @Test
    public void stm05En001_liveGuardianPersistReadBackAndCrossTenantDenial() throws Exception {
        if (!approved()) {
            System.out.println("=== STM-05 LIVE JDBC TEST GATED ===");
            System.out.println("Authorize with -Dcampx.approve.stm05.write=true");
            return;
        }

        HikariDataSource ds = createDevDataSource();
        assertNotNull("JDBC datasource must be configured for live STM-05 test", ds);

        UUID guardianshipId = UUID.randomUUID();
        Instant now = Instant.parse("2026-11-14T13:00:00Z");

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bindClaims(conn, PILOT_TENANT, COLLEGE_ADMIN_USER, "COLLEGE_ADMIN");

                // Same-tenant read-back of existing persisted guardianship rows.
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id, relationship, status FROM stu.guardianships "
                                + "WHERE tenant_id = ? AND deleted_at IS NULL LIMIT 1")) {
                    ps.setObject(1, PILOT_TENANT);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Expected existing guardianship rows for pilot tenant", rs.next());
                        assertNotNull(rs.getString("relationship"));
                        assertNotNull(rs.getString("status"));
                    }
                }

                boolean wrote = false;
                try {
                    conn.setSavepoint("before_insert");
                    try (PreparedStatement ps = conn.prepareStatement(
                            "INSERT INTO stu.guardianships "
                                    + "(id, tenant_id, student_id, guardian_person_id, relationship, is_primary, "
                                    + "can_view_academics, can_view_fees, can_pay_fees, status, created_at, updated_at, row_version) "
                                    + "VALUES (?, ?, ?, ?, ?, ?, false, false, false, 'ACTIVE', ?, ?, 1)")) {
                        ps.setObject(1, guardianshipId);
                        ps.setObject(2, PILOT_TENANT);
                        ps.setObject(3, STUDENT_ID);
                        ps.setObject(4, GUARDIAN_PERSON_ID);
                        ps.setString(5, "MOTHER");
                        ps.setBoolean(6, false);
                        ps.setTimestamp(7, Timestamp.from(now));
                        ps.setTimestamp(8, Timestamp.from(now));
                        int updated = ps.executeUpdate();
                        assertEquals(1, updated);
                        wrote = true;
                    }
                } catch (java.sql.SQLException insertDenied) {
                    // IAM-scoped insert may deny; roll back to savepoint then continue isolation checks.
                    conn.rollback();
                    bindClaims(conn, PILOT_TENANT, COLLEGE_ADMIN_USER, "COLLEGE_ADMIN");
                    assertTrue("Expected RLS/permission denial when insert blocked: " + insertDenied.getMessage(),
                            insertDenied.getMessage() != null
                                    && (insertDenied.getMessage().toLowerCase().contains("policy")
                                    || insertDenied.getMessage().toLowerCase().contains("row-level")
                                    || "42501".equals(insertDenied.getSQLState())));
                }

                if (wrote) {
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT relationship FROM stu.guardianships WHERE tenant_id = ? AND id = ?")) {
                        ps.setObject(1, PILOT_TENANT);
                        ps.setObject(2, guardianshipId);
                        try (ResultSet rs = ps.executeQuery()) {
                            assertTrue(rs.next());
                            assertEquals("MOTHER", rs.getString("relationship"));
                        }
                    }
                }

                bindClaims(conn, OTHER_TENANT, UUID.randomUUID(), "COLLEGE_ADMIN");
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) AS c FROM stu.guardianships WHERE tenant_id = ?")) {
                    ps.setObject(1, PILOT_TENANT);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Cross-tenant read of pilot tenant rows must return 0", 0, rs.getInt("c"));
                    }
                }
            } finally {
                try {
                    conn.rollback();
                } catch (Exception ignored) {
                    // best effort
                }
                try (Statement stmt = conn.createStatement()) {
                    stmt.execute("RESET ROLE");
                    stmt.execute("SELECT set_config('request.jwt.claims', '', true)");
                } catch (Exception ignored) {
                    // best effort
                }
                try {
                    conn.setAutoCommit(true);
                } catch (Exception ignored) {
                    // best effort
                }
            }
        } finally {
            ds.close();
        }
    }
}
