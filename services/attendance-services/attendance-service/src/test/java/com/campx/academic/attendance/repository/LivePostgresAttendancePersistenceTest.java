package com.campx.academic.attendance.repository;

import com.campx.academic.attendance.config.TestEnvironmentGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live PostgreSQL relational persistence, transaction boundary, unique constraint enforcement,
 * and outbox integration test for Wave 6 Attendance Domain (ACD-06).
 *
 * Safety Controls:
 * 1. Target is strictly restricted to development ref azulbaxszsqxymqmlwfe.
 * 2. Uncommitted transaction: conn.setAutoCommit(false), terminating unconditionally in rollback().
 *    Zero residual rows committed to the live database.
 * 3. Gated by explicit owner authorization: -Dcampx.approve.wave6.write=true.
 */
public class LivePostgresAttendancePersistenceTest {

    public static final String APPROVAL_TOKEN = "APPROVE-WAVE6-LIVE-PERSISTENCE-TEST";
    public static final String APPROVED_DEV_REF = "azulbaxszsqxymqmlwfe";

    private static HikariDataSource dataSource;

    private static String getDevJdbcUrl() {
        String envUrl = System.getenv("CAMPX_DB_URL");
        if (envUrl == null || envUrl.trim().isEmpty()) {
            envUrl = System.getenv("SUPABASE_JDBC_URL");
        }
        if (envUrl != null && envUrl.contains("ap-southeast-1")) {
            return envUrl.replace("ap-southeast-1", "ap-south-1");
        }
        return envUrl;
    }

    private static HikariDataSource createDevDataSource() {
        String jdbcUrl = getDevJdbcUrl();
        String user = System.getenv("CAMPX_DB_USER");
        String pass = System.getenv("CAMPX_DB_PASSWORD");

        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("SUPABASE_USER");
            pass = System.getenv("SUPABASE_PASSWORD");
        }

        if (jdbcUrl == null || user == null || pass == null) {
            return null;
        }

        TestEnvironmentGuard.validateStagingConfiguration(jdbcUrl, user);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setPoolName("Live-Attendance-Persistence-Pool");
        return new HikariDataSource(config);
    }

    @BeforeClass
    public static void setUp() {
        dataSource = createDevDataSource();
    }

    @AfterClass
    public static void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    public void testLiveAttendancePersistenceAndRollback() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave6.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));

        if (!isApproved) {
            System.out.println("=== LIVE DATABASE WRITE INTEGRATION TEST GATED ===");
            System.out.println("This test requires explicit owner authorization before executing live database writes.");
            System.out.println("Proposed Operation:");
            System.out.println("  - Target Ref: " + APPROVED_DEV_REF);
            System.out.println("  - Tables Affected: acd.attendance_sessions, acd.attendance_records, sys.outbox_events");
            System.out.println("  - Mode: Uncommitted transaction (autoCommit=false), terminating in rollback()");
            System.out.println("  - To authorize: Run with -Dcampx.approve.wave6.write=true");
            System.out.println("Status: SAFELY HALTED AT WRITE APPROVAL GATE.");
            return;
        }

        assertNotNull("DataSource must be configured", dataSource);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userA = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        UUID collegeId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
        UUID academicAdminRoleId = UUID.fromString("fc535411-dd0a-4cf5-a33d-179ec35d6c89");

        UUID deptId = UUID.randomUUID();
        UUID personFacultyId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID personStu1 = UUID.randomUUID();
        UUID personStu2 = UUID.randomUUID();
        UUID studentId1 = UUID.randomUUID();
        UUID studentId2 = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {

                // Step 1: Target Verification
                try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, inet_server_addr()")) {
                    assertTrue(rs.next());
                    System.out.println("=== LIVE TARGET VERIFIED ===");
                    System.out.println("Database: " + rs.getString(1) + " | User: " + rs.getString(2) + " | IP: " + rs.getString(3));
                }

                // Step 2: Provision Prerequisite Fixtures in Transaction
                // 2a. Role assignment for ACADEMIC_ADMIN
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.role_assignments (id, tenant_id, role_id, user_id, college_id, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, null, now(), now(), 1)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA);
                    ps.setObject(3, academicAdminRoleId);
                    ps.setObject(4, userA);
                    ps.executeUpdate();
                }

                // 2b. Department
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.departments (id, tenant_id, college_id, code, name, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CS-W6', 'Computer Science Wave 6', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, deptId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.executeUpdate();
                }

                // 2c. Faculty Person & Employee
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'FacultyFirst', 'FacultyLast', 'Prof. Wave 6', 'prof.w6@campx.edu', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, personFacultyId);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status, date_of_joining, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, ?, 'EMP-W6-001', 'PERMANENT', 'TEACHING', 'ACTIVE', '2024-01-01', now(), now(), 1)")) {
                    ps.setObject(1, employeeId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personFacultyId);
                    ps.setObject(4, collegeId);
                    ps.setObject(5, deptId);
                    ps.executeUpdate();
                }

                // 2d. Calendar & Term
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.calendars (id, tenant_id, college_id, code, name, calendar_type, status, week_start_day, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CAL-W6', 'Wave 6 Calendar', 'ACADEMIC', 'PUBLISHED', 1, now(), now(), 1)")) {
                    ps.setObject(1, calendarId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.calendar_terms (id, tenant_id, calendar_id, term_no, name, term_type, start_date, end_date, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 1, 'Semester 1 W6', 'SEMESTER', '2026-08-01', '2026-12-31', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, termId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, calendarId);
                    ps.executeUpdate();
                }

                // 2e. Course & Offering
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.courses (id, tenant_id, department_id, code, name, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CS601', 'Distributed Systems W6', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, courseId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, deptId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.course_offerings (id, tenant_id, college_id, course_id, term_id, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, ?, 'RUNNING', now(), now(), 1)")) {
                    ps.setObject(1, offeringId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.setObject(4, courseId);
                    ps.setObject(5, termId);
                    ps.executeUpdate();
                }

                // 2f. Section
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.course_sections (id, tenant_id, offering_id, section_code, capacity, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'SEC-W6-A', 60, 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, sectionId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, offeringId);
                    ps.executeUpdate();
                }

                // 2g. Section Faculty
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.section_faculty (tenant_id, section_id, employee_id, role) " +
                                "VALUES (?, ?, ?, 'PRIMARY') ON CONFLICT DO NOTHING")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, sectionId);
                    ps.setObject(3, employeeId);
                    ps.executeUpdate();
                }

                // 2h. Two Students
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'Alice', 'Student', 'Alice W6', 'alice.w6@campx.edu', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, personStu1);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.students (id, tenant_id, person_id, college_id, enrollment_no, status, admission_date, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, 'ENR-W6-001', 'ACTIVE', '2026-08-01', now(), now(), 1)")) {
                    ps.setObject(1, studentId1);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personStu1);
                    ps.setObject(4, collegeId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'Bob', 'Student', 'Bob W6', 'bob.w6@campx.edu', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, personStu2);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.students (id, tenant_id, person_id, college_id, enrollment_no, status, admission_date, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, 'ENR-W6-002', 'ACTIVE', '2026-08-01', now(), now(), 1)")) {
                    ps.setObject(1, studentId2);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personStu2);
                    ps.setObject(4, collegeId);
                    ps.executeUpdate();
                }

                System.out.println("[SUCCESS] Provisioned all prerequisite relational fixtures in transaction");

                // Step 3: Switch to authenticated role with Tenant A claims
                stmt.execute("SET LOCAL ROLE authenticated");
                String claimsA = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsA + "', true)");

                // Step 4: Persist Attendance Session under authenticated role
                String sessSql = "INSERT INTO acd.attendance_sessions (" +
                        "id, tenant_id, section_id, employee_id, session_date, start_time, end_time, status, created_at, updated_at, row_version" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, 'OPEN', now(), now(), 1)";
                try (PreparedStatement ps = conn.prepareStatement(sessSql)) {
                    ps.setObject(1, sessionId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sectionId);
                    ps.setObject(4, employeeId);
                    ps.setDate(5, Date.valueOf(LocalDate.of(2026, 10, 22)));
                    ps.setTime(6, Time.valueOf("09:00:00"));
                    ps.setTime(7, Time.valueOf("10:00:00"));
                    int inserted = ps.executeUpdate();
                    assertEquals("Attendance session must be inserted", 1, inserted);
                    System.out.println("[SUCCESS] Inserted acd.attendance_sessions record under authenticated RLS: " + sessionId);
                }

                // Step 5: Test duplicate session rejection via savepoint
                Savepoint spDuplicateSession = conn.setSavepoint("sp_dup_session");
                try (PreparedStatement ps = conn.prepareStatement(sessSql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sectionId);
                    ps.setObject(4, employeeId);
                    ps.setDate(5, Date.valueOf(LocalDate.of(2026, 10, 22)));
                    ps.setTime(6, Time.valueOf("09:00:00"));
                    ps.setTime(7, Time.valueOf("10:00:00"));
                    ps.executeUpdate();
                    fail("Duplicate attendance session for same section, date, and start_time must be rejected");
                } catch (Exception e) {
                    conn.rollback(spDuplicateSession);
                    System.out.println("[SUCCESS] Duplicate session correctly rejected by PostgreSQL unique constraint.");
                }

                // Step 6: Mark Attendance Records for Students
                UUID recId1 = UUID.randomUUID();
                UUID recId2 = UUID.randomUUID();
                String recSql = "INSERT INTO acd.attendance_records (" +
                        "id, tenant_id, session_id, student_id, status, method, marked_by, created_at, updated_at, row_version" +
                        ") VALUES (?, ?, ?, ?, ?, 'MANUAL', ?, now(), now(), 1)";

                try (PreparedStatement ps = conn.prepareStatement(recSql)) {
                    ps.setObject(1, recId1);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sessionId);
                    ps.setObject(4, studentId1);
                    ps.setString(5, "PRESENT");
                    ps.setObject(6, userA);
                    ps.executeUpdate();

                    ps.setObject(1, recId2);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sessionId);
                    ps.setObject(4, studentId2);
                    ps.setString(5, "ABSENT");
                    ps.setObject(6, userA);
                    ps.executeUpdate();
                    System.out.println("[SUCCESS] Marked attendance for 2 students in session: " + sessionId);
                }

                // Step 7: Test duplicate student marking rejection via savepoint
                Savepoint spDuplicateRecord = conn.setSavepoint("sp_dup_record");
                try (PreparedStatement ps = conn.prepareStatement(recSql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sessionId);
                    ps.setObject(4, studentId1); // Duplicate student 1
                    ps.setString(5, "LATE");
                    ps.setObject(6, userA);
                    ps.executeUpdate();
                    fail("Duplicate attendance record for same student in same session must be rejected");
                } catch (Exception e) {
                    conn.rollback(spDuplicateRecord);
                    System.out.println("[SUCCESS] Duplicate attendance record correctly rejected by PostgreSQL unique constraint.");
                }

                // Step 8: Transactional outbox events in sys.outbox_events
                String outboxSql = "INSERT INTO sys.outbox_events (" +
                        "id, tenant_id, service, aggregate_type, aggregate_id, event_type, payload, status, attempts, created_at, updated_at" +
                        ") VALUES (?, ?, 'attendance-service', 'ATTENDANCE_SESSION', ?, ?, ?::jsonb, 'PENDING', 0, now(), now())";
                try (PreparedStatement ps = conn.prepareStatement(outboxSql)) {
                    UUID evt1 = UUID.randomUUID();
                    ps.setObject(1, evt1);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sessionId);
                    ps.setString(4, "attendance.session_opened.v1");
                    ps.setString(5, String.format("{\"sessionId\":\"%s\",\"sectionId\":\"%s\"}", sessionId, sectionId));
                    ps.executeUpdate();

                    UUID evt2 = UUID.randomUUID();
                    ps.setObject(1, evt2);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sessionId);
                    ps.setString(4, "attendance.marked.v1");
                    ps.setString(5, String.format("{\"sessionId\":\"%s\",\"markedCount\":2}", sessionId));
                    ps.executeUpdate();
                    System.out.println("[SUCCESS] Transactional outbox events persisted within same transaction: " + evt1 + ", " + evt2);
                }

                // Step 9: Committed read-back within active transaction
                String verifySql = "SELECT s.id, s.status, count(r.id) as record_count " +
                        "FROM acd.attendance_sessions s " +
                        "LEFT JOIN acd.attendance_records r ON r.session_id = s.id AND r.tenant_id = s.tenant_id " +
                        "WHERE s.id = ? AND s.tenant_id = ? " +
                        "GROUP BY s.id, s.status";
                try (PreparedStatement ps = conn.prepareStatement(verifySql)) {
                    ps.setObject(1, sessionId);
                    ps.setObject(2, tenantA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Committed attendance session must be read back", rs.next());
                        assertEquals("OPEN", rs.getString("status"));
                        assertEquals(2, rs.getInt("record_count"));
                        System.out.println("[SUCCESS] Verified committed read-back: session status=OPEN, recordCount=2");
                    }
                }

                // Step 10: Negative Test: Cross-tenant isolation under synthetic Tenant B claims
                UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000001");
                String tenantBClaims = String.format(
                        "{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantB, tenantB
                );
                stmt.execute("SELECT set_config('request.jwt.claims', '" + tenantBClaims + "', true)");

                try (PreparedStatement ps = conn.prepareStatement("SELECT id FROM acd.attendance_sessions WHERE id = ?")) {
                    ps.setObject(1, sessionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        boolean visibleToTenantB = rs.next();
                        assertEquals("Tenant B must NOT be able to see Tenant A attendance session under RLS", false, visibleToTenantB);
                        System.out.println("[SUCCESS] Verified cross-tenant read denial: 0 rows returned under Tenant B claims");
                    }
                }

                System.out.println("Verified: Live attendance persistence, duplicate rejection, and cross-tenant RLS isolation passed.");

            } finally {
                // Safety Guard: Terminates unconditionally in rollback()
                conn.rollback();
                System.out.println("Clean uncommitted test transaction rolled back. Zero residual rows committed.");
            }
        }
    }
}
