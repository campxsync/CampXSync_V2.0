package com.campx.enrollment.persistence;

import com.campx.enrollment.config.TestEnvironmentGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live STM-03 persistence/RLS test against azulbaxszsqxymqmlwfe.
 * Requires -Dcampx.approve.stm03.write=true. Ends in unconditional rollback (zero residual rows).
 */
public class LivePostgresEnrollmentPersistenceTest {

    private static final String APPROVED_DEV_REF = TestEnvironmentGuard.APPROVED_DEV_PROJECT_REF;
    public static final String APPROVAL_TOKEN = "APPROVE-STM03-LIVE-PERSISTENCE-TEST";

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
        config.setPoolName("Live-Enrollment-Persistence-Pool");
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

    @Test
    public void stm03En001_schemaOwnershipAndRlsForcedOnEnrolmentTables() throws Exception {
        boolean approved = Boolean.getBoolean("campx.approve.stm03.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));
        if (!approved) {
            System.out.println("=== STM-03-EN-001 SCHEMA AUDIT GATED ===");
            System.out.println("Authorize with -Dcampx.approve.stm03.write=true");
            return;
        }

        HikariDataSource ds = createDevDataSource();
        assertNotNull("JDBC datasource must be configured for live STM-03 schema audit", ds);
        try (Connection conn = ds.getConnection();
             PreparedStatement ps = conn.prepareStatement(
                     "SELECT c.relname AS table_name, c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced "
                             + "FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace "
                             + "WHERE n.nspname = 'stu' AND c.relkind = 'r' AND c.relname LIKE 'enrol%' "
                             + "ORDER BY c.relname")) {
            try (ResultSet rs = ps.executeQuery()) {
                int counted = 0;
                while (rs.next()) {
                    counted++;
                    assertTrue("RLS must be enabled on " + rs.getString("table_name"), rs.getBoolean("rls_enabled"));
                    assertTrue("RLS must be FORCED on " + rs.getString("table_name"), rs.getBoolean("rls_forced"));
                }
                assertTrue("Expected enrolment tables under stu schema", counted >= 5);
            }
        } finally {
            ds.close();
        }
    }

    @Test
    public void liveEnrolmentPersistReadBackAndCrossTenantDenial() throws Exception {
        boolean approved = Boolean.getBoolean("campx.approve.stm03.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));
        if (!approved) {
            System.out.println("=== STM-03 LIVE JDBC TEST GATED ===");
            System.out.println("Authorize with -Dcampx.approve.stm03.write=true");
            System.out.println("Mode: uncommitted transaction ending in rollback()");
            return;
        }

        HikariDataSource ds = createDevDataSource();
        assertNotNull("JDBC datasource must be configured for live STM-03 test", ds);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
        UUID userA = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        UUID windowId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID enrolmentId = UUID.randomUUID();
        UUID applicationId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID programId = UUID.randomUUID();
        UUID facultyId = UUID.randomUUID();

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try {
                bindClaims(conn, tenantA, userA, "REGISTRAR");

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.academic_terms (tenant_id, id, year_code, term_code, name) VALUES (?,?,?,?,?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, termId);
                    ps.setString(3, "2026");
                    ps.setString(4, "LIVE");
                    ps.setString(5, "Live Term");
                    assertEquals(1, ps.executeUpdate());
                }

                Instant opens = Instant.parse("2026-11-01T00:00:00Z");
                Instant closes = Instant.parse("2026-12-01T00:00:00Z");
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.registration_windows (tenant_id, id, term_id, opens_at, closes_at, min_credits, max_credits) VALUES (?,?,?,?,?,?,?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, windowId);
                    ps.setObject(3, termId);
                    ps.setTimestamp(4, Timestamp.from(opens));
                    ps.setTimestamp(5, Timestamp.from(closes));
                    ps.setInt(6, 3);
                    ps.setInt(7, 18);
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.enrolment_courses (tenant_id, id, program_id, term_id, code, credits, is_core, prerequisites) VALUES (?,?,?,?,?,?,?,?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, courseId);
                    ps.setObject(3, programId);
                    ps.setObject(4, termId);
                    ps.setString(5, "CS101");
                    ps.setInt(6, 3);
                    ps.setBoolean(7, true);
                    ps.setArray(8, conn.createArrayOf("uuid", new UUID[0]));
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.enrolment_sections (tenant_id, id, course_id, capacity, enrolled_count, faculty_user_id) VALUES (?,?,?,?,?,?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, sectionId);
                    ps.setObject(3, courseId);
                    ps.setInt(4, 2);
                    ps.setInt(5, 0);
                    ps.setObject(6, facultyId);
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.enrolments (tenant_id, id, application_id, student_id, program_id, term_id, status, total_credits) VALUES (?,?,?,?,?,?,?,?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, enrolmentId);
                    ps.setObject(3, applicationId);
                    ps.setObject(4, studentId);
                    ps.setObject(5, programId);
                    ps.setObject(6, termId);
                    ps.setString(7, "PENDING");
                    ps.setInt(8, 0);
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE stu.enrolment_sections SET enrolled_count = enrolled_count + 1 WHERE tenant_id = ? AND id = ? AND enrolled_count < capacity")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, sectionId);
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.enrolment_selections (tenant_id, enrolment_id, course_id, section_id) VALUES (?,?,?,?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, enrolmentId);
                    ps.setObject(3, courseId);
                    ps.setObject(4, sectionId);
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE stu.enrolments SET status = 'ENROLLED', total_credits = 3 WHERE tenant_id = ? AND id = ?")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, enrolmentId);
                    assertEquals(1, ps.executeUpdate());
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT status, total_credits FROM stu.enrolments WHERE tenant_id = ? AND id = ?")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, enrolmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("ENROLLED", rs.getString("status"));
                        assertEquals(3, rs.getInt("total_credits"));
                    }
                }

                bindClaims(conn, tenantB, UUID.randomUUID(), "REGISTRAR");
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) AS c FROM stu.enrolments WHERE id = ?")) {
                    ps.setObject(1, enrolmentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Cross-tenant read must return 0", 0, rs.getInt("c"));
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.enrolments (tenant_id, id, application_id, student_id, program_id, term_id, status, total_credits) VALUES (?,?,?,?,?,?,?,?)")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, UUID.randomUUID());
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, UUID.randomUUID());
                    ps.setObject(5, programId);
                    ps.setObject(6, termId);
                    ps.setString(7, "PENDING");
                    ps.setInt(8, 0);
                    ps.executeUpdate();
                    fail("Cross-tenant write should be denied by RLS");
                } catch (SQLException expected) {
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
