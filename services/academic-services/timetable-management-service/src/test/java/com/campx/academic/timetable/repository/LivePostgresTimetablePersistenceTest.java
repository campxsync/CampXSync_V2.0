package com.campx.academic.timetable.repository;

import com.campx.academic.timetable.config.TestEnvironmentGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Savepoint;
import java.sql.Statement;
import java.sql.Time;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live PostgreSQL relational persistence, transaction boundary, exclusion constraint enforcement,
 * and outbox integration test for Wave 5 Timetable Domain (ACD-05).
 *
 * Safety Controls:
 * 1. Target is strictly restricted to development ref azulbaxszsqxymqmlwfe.
 * 2. Uncommitted transaction: conn.setAutoCommit(false), terminating unconditionally in rollback().
 *    Zero residual rows committed to the live database.
 * 3. Gated by explicit owner authorization: -Dcampx.approve.wave5.write=true.
 */
public class LivePostgresTimetablePersistenceTest {

    public static final String APPROVAL_TOKEN = "APPROVE-WAVE5-LIVE-PERSISTENCE-TEST";
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
        config.setPoolName("Live-Timetable-Persistence-Pool");
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
    public void testLiveTimetablePersistenceAndRollback() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave5.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));

        if (!isApproved) {
            System.out.println("=== LIVE DATABASE WRITE INTEGRATION TEST GATED ===");
            System.out.println("This test requires explicit owner authorization before executing live database writes.");
            System.out.println("Proposed Operation:");
            System.out.println("  - Target Ref: " + APPROVED_DEV_REF);
            System.out.println("  - Tables Affected: acd.timetables, acd.timetable_versions, acd.timetable_entries, sys.outbox_events");
            System.out.println("  - Mode: Uncommitted transaction (autoCommit=false), terminating in rollback()");
            System.out.println("  - To authorize: Run with -Dcampx.approve.wave5.write=true");
            System.out.println("Status: SAFELY HALTED AT WRITE APPROVAL GATE.");
            return;
        }

        assertNotNull("DataSource must be configured", dataSource);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userA = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        UUID collegeId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
        UUID academicAdminRoleId = UUID.fromString("fc535411-dd0a-4cf5-a33d-179ec35d6c89");

        UUID deptId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID employeeId = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID sectionId = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        UUID timetableId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();
        UUID slotId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Step 1: Target Verification
                try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, inet_server_addr()")) {
                    assertTrue(rs.next());
                    System.out.println("=== LIVE TARGET VERIFIED ===");
                    System.out.println("Database: " + rs.getString(1) + " | User: " + rs.getString(2) + " | IP: " + rs.getString(3));
                }

                // Step 2: Provision Prerequisite Fixtures in Transaction (Setup Phase)
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
                                "VALUES (?, ?, ?, 'CS-W5', 'Computer Science Wave 5', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, deptId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.executeUpdate();
                }

                // 2c. Person & Employee
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'FacultyFirst', 'FacultyLast', 'Prof. Wave 5', 'prof.w5@campx.edu', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status, date_of_joining, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, ?, 'EMP-W5-001', 'PERMANENT', 'TEACHING', 'ACTIVE', '2024-01-01', now(), now(), 1)")) {
                    ps.setObject(1, employeeId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personId);
                    ps.setObject(4, collegeId);
                    ps.setObject(5, deptId);
                    ps.executeUpdate();
                }

                // 2d. Calendar & Term
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.calendars (id, tenant_id, college_id, code, name, calendar_type, status, week_start_day, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CAL-W5', 'Wave 5 Calendar', 'ACADEMIC', 'PUBLISHED', 1, now(), now(), 1)")) {
                    ps.setObject(1, calendarId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.calendar_terms (id, tenant_id, calendar_id, term_no, name, term_type, start_date, end_date, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 1, 'Semester 1 W5', 'SEMESTER', '2026-08-01', '2026-12-31', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, termId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, calendarId);
                    ps.executeUpdate();
                }

                // 2e. Course & Offering
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.courses (id, tenant_id, department_id, code, name, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CS501', 'Data Structures W5', 'ACTIVE', now(), now(), 1)")) {
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

                // 2f. Section & Subject
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.course_sections (id, tenant_id, offering_id, section_code, capacity, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'SEC-W5-A', 60, 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, sectionId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, offeringId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.subjects (id, tenant_id, department_id, code, name, subject_type, credits, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'SUB-W5-101', 'Algorithms W5', 'THEORY', 4, 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, subjectId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, deptId);
                    ps.executeUpdate();
                }

                // 2g. Section Faculty (prerequisite for guard_timetable_entry)
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.section_faculty (tenant_id, section_id, employee_id, role) " +
                                "VALUES (?, ?, ?, 'PRIMARY') ON CONFLICT DO NOTHING")) {
                    ps.setObject(1, tenantA);
                    ps.setObject(2, sectionId);
                    ps.setObject(3, employeeId);
                    ps.executeUpdate();
                }

                System.out.println("[SUCCESS] Provisioned all prerequisite relational fixtures in transaction");

                // Step 3: Switch to authenticated role & set tenant claims
                stmt.execute("SET LOCAL ROLE authenticated");
                String claimsA = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsA + "', true)");

                // Step 4: Insert Timetable, TimetableVersion, TimetableEntry under authenticated role
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.timetables (id, tenant_id, college_id, term_id, name, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, 'Wave 5 Verified Timetable', 'DRAFT', now(), now(), 1)")) {
                    ps.setObject(1, timetableId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.setObject(4, termId);
                    int rows = ps.executeUpdate();
                    assertEquals("Timetable insert must affect 1 row", 1, rows);
                    System.out.println("[SUCCESS] Inserted acd.timetables record under authenticated RLS: " + timetableId);
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.timetable_versions (id, tenant_id, timetable_id, version_no, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 1, 'DRAFT', now(), now(), 1)")) {
                    ps.setObject(1, versionId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, timetableId);
                    int rows = ps.executeUpdate();
                    assertEquals("TimetableVersion insert must affect 1 row", 1, rows);
                    System.out.println("[SUCCESS] Inserted acd.timetable_versions record under authenticated RLS: " + versionId);
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.timetable_entries (id, tenant_id, timetable_version_id, section_id, subject_id, employee_id, day_of_week, start_time, end_time, entry_type, valid_from, valid_to, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, ?, ?, 1, '09:00:00', '10:00:00', 'LECTURE', '2026-08-01', '2026-12-31', now(), now(), 1)")) {
                    ps.setObject(1, slotId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, versionId);
                    ps.setObject(4, sectionId);
                    ps.setObject(5, subjectId);
                    ps.setObject(6, employeeId);
                    int rows = ps.executeUpdate();
                    assertEquals("TimetableEntry insert must affect 1 row", 1, rows);
                    System.out.println("[SUCCESS] Inserted acd.timetable_entries record under authenticated RLS: " + slotId);
                }

                // Step 5: Atomic Outbox Event Emission (sys.outbox_events)
                UUID outboxId = UUID.randomUUID();
                String outboxSql = "INSERT INTO sys.outbox_events (" +
                        "id, tenant_id, service, aggregate_type, aggregate_id, event_type, " +
                        "payload, headers, status, attempts, created_at, updated_at" +
                        ") VALUES (?, ?, 'timetable-management-service', 'TIMETABLE_ENTRY', ?, 'timetable.slot_changed.v1', ?::jsonb, ?::jsonb, 'PENDING', 0, now(), now())";
                String payload = String.format("{\"slotId\":\"%s\",\"versionId\":\"%s\",\"sectionId\":\"%s\",\"employeeId\":\"%s\",\"dayOfWeek\":1,\"startTime\":\"09:00:00\",\"endTime\":\"10:00:00\"}",
                        slotId, versionId, sectionId, employeeId);
                String headers = String.format("{\"correlationId\":\"%s\",\"schemaVersion\":\"1.0\"}", UUID.randomUUID());
                try (PreparedStatement ps = conn.prepareStatement(outboxSql)) {
                    ps.setObject(1, outboxId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, slotId);
                    ps.setString(4, payload);
                    ps.setString(5, headers);
                    int rows = ps.executeUpdate();
                    assertEquals("Outbox insert must affect 1 row", 1, rows);
                    System.out.println("[SUCCESS] Inserted sys.outbox_events record: " + outboxId);
                }

                // Verify outbox record is visible within the same transaction
                String selectOutboxSql = "SELECT id, tenant_id, service, aggregate_type, aggregate_id, event_type, payload::text, status " +
                        "FROM sys.outbox_events WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectOutboxSql)) {
                    ps.setObject(1, outboxId);
                    ps.setObject(2, tenantA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Persisted outbox event must be visible in transaction", rs.next());
                        assertEquals(outboxId, rs.getObject("id"));
                        assertEquals(tenantA, rs.getObject("tenant_id"));
                        assertEquals("timetable-management-service", rs.getString("service"));
                        assertEquals("TIMETABLE_ENTRY", rs.getString("aggregate_type"));
                        assertEquals(slotId, rs.getObject("aggregate_id"));
                        assertEquals("timetable.slot_changed.v1", rs.getString("event_type"));
                        assertEquals("PENDING", rs.getString("status"));
                        assertNotNull("Outbox payload must not be null", rs.getString("payload"));
                        System.out.println("[SUCCESS] Persisted outbox event verified within transaction!");
                    }
                }

                // Step 6: Read-back Verification
                String verifySql = "SELECT tte.id, tte.day_of_week, tte.start_time, tte.end_time, tte.entry_type, sub.code as subject_code, cs.section_code " +
                        "FROM acd.timetable_entries tte " +
                        "JOIN acd.subjects sub ON sub.id = tte.subject_id " +
                        "JOIN acd.course_sections cs ON cs.id = tte.section_id " +
                        "WHERE tte.id = ? AND tte.tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(verifySql)) {
                    ps.setObject(1, slotId);
                    ps.setObject(2, tenantA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Must read back inserted timetable entry", rs.next());
                        assertEquals(slotId, rs.getObject(1));
                        assertEquals(1, rs.getInt(2));
                        assertEquals(Time.valueOf("09:00:00"), rs.getTime(3));
                        assertEquals(Time.valueOf("10:00:00"), rs.getTime(4));
                        assertEquals("LECTURE", rs.getString(5));
                        assertEquals("SUB-W5-101", rs.getString(6));
                        assertEquals("SEC-W5-A", rs.getString(7));
                        System.out.println("[SUCCESS] Read-back verified: joined subject code SUB-W5-101 and section SEC-W5-A match!");
                    }
                }

                // Step 7: Cross-Tenant Isolation Test (Denial under synthetic Tenant B)
                UUID syntheticTenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
                String claimsB = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, syntheticTenantB, syntheticTenantB);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsB + "', true)");

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT count(*) FROM acd.timetable_entries WHERE id = ?")) {
                    ps.setObject(1, slotId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Cross-tenant isolation must deny read (0 rows)", 0, rs.getLong(1));
                        System.out.println("[SUCCESS] Cross-tenant isolation verified: Tenant B cannot see Tenant A's timetable slot.");
                    }
                }

                System.out.println("\n[SUCCESS] Live Timetable Persistence & Tenant Isolation Verified. Rolling back...");
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
                System.out.println("[ROLLBACK COMPLETED] Live database remains pristine: zero rows committed.");
            }
        }
    }

    @Test
    public void testLivePostgresConflictDetection() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave5.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));

        if (!isApproved) {
            System.out.println("=== LIVE CONFLICT DETECTION TEST GATED ===");
            return;
        }

        assertNotNull("DataSource must be configured", dataSource);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userA = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        UUID collegeId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
        UUID academicAdminRoleId = UUID.fromString("fc535411-dd0a-4cf5-a33d-179ec35d6c89");

        UUID deptId = UUID.randomUUID();
        UUID personId1 = UUID.randomUUID();
        UUID personId2 = UUID.randomUUID();
        UUID employeeId1 = UUID.randomUUID();
        UUID employeeId2 = UUID.randomUUID();
        UUID calendarId = UUID.randomUUID();
        UUID termId = UUID.randomUUID();
        UUID courseId = UUID.randomUUID();
        UUID offeringId = UUID.randomUUID();
        UUID sectionId1 = UUID.randomUUID();
        UUID sectionId2 = UUID.randomUUID();
        UUID subjectId = UUID.randomUUID();

        UUID timetableId = UUID.randomUUID();
        UUID versionId = UUID.randomUUID();

        try (Connection conn = dataSource.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Setup fixtures before role switch
                stmt.execute(String.format("INSERT INTO iam.role_assignments (id, tenant_id, role_id, user_id, college_id, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', '%s', null, now(), now(), 1)", UUID.randomUUID(), tenantA, academicAdminRoleId, userA));
                stmt.execute(String.format("INSERT INTO core.departments (id, tenant_id, college_id, code, name, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 'CS-C', 'CS Conflict Test', 'ACTIVE', now(), now(), 1)", deptId, tenantA, collegeId));
                stmt.execute(String.format("INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) VALUES ('%s', '%s', 'F1', 'L1', 'Faculty 1', 'f1@campx.edu', 'ACTIVE', now(), now(), 1)", personId1, tenantA));
                stmt.execute(String.format("INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) VALUES ('%s', '%s', 'F2', 'L2', 'Faculty 2', 'f2@campx.edu', 'ACTIVE', now(), now(), 1)", personId2, tenantA));
                stmt.execute(String.format("INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, employee_no, employment_type, staff_category, status, date_of_joining, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', '%s', 'EMP-C-1', 'PERMANENT', 'TEACHING', 'ACTIVE', '2024-01-01', now(), now(), 1)", employeeId1, tenantA, personId1, collegeId));
                stmt.execute(String.format("INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, employee_no, employment_type, staff_category, status, date_of_joining, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', '%s', 'EMP-C-2', 'PERMANENT', 'TEACHING', 'ACTIVE', '2024-01-01', now(), now(), 1)", employeeId2, tenantA, personId2, collegeId));

                stmt.execute(String.format("INSERT INTO core.calendars (id, tenant_id, college_id, code, name, calendar_type, status, week_start_day, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 'CAL-C', 'Cal Conflict', 'ACADEMIC', 'PUBLISHED', 1, now(), now(), 1)", calendarId, tenantA, collegeId));
                stmt.execute(String.format("INSERT INTO acd.calendar_terms (id, tenant_id, calendar_id, term_no, name, term_type, start_date, end_date, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 1, 'Term Conflict', 'SEMESTER', '2026-08-01', '2026-12-31', 'ACTIVE', now(), now(), 1)", termId, tenantA, calendarId));
                stmt.execute(String.format("INSERT INTO acd.courses (id, tenant_id, department_id, code, name, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 'CS-CONF', 'Conflict Course', 'ACTIVE', now(), now(), 1)", courseId, tenantA, deptId));
                stmt.execute(String.format("INSERT INTO acd.course_offerings (id, tenant_id, college_id, course_id, term_id, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', '%s', '%s', 'RUNNING', now(), now(), 1)", offeringId, tenantA, collegeId, courseId, termId));
                stmt.execute(String.format("INSERT INTO acd.course_sections (id, tenant_id, offering_id, section_code, capacity, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 'SEC-C1', 60, 'ACTIVE', now(), now(), 1)", sectionId1, tenantA, offeringId));
                stmt.execute(String.format("INSERT INTO acd.course_sections (id, tenant_id, offering_id, section_code, capacity, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 'SEC-C2', 60, 'ACTIVE', now(), now(), 1)", sectionId2, tenantA, offeringId));
                stmt.execute(String.format("INSERT INTO acd.subjects (id, tenant_id, department_id, code, name, subject_type, credits, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 'SUB-C', 'Subject Conflict', 'THEORY', 4, 'ACTIVE', now(), now(), 1)", subjectId, tenantA, deptId));

                // section_faculty
                stmt.execute(String.format("INSERT INTO acd.section_faculty (tenant_id, section_id, employee_id, role) VALUES ('%s', '%s', '%s', 'PRIMARY') ON CONFLICT DO NOTHING", tenantA, sectionId1, employeeId1));
                stmt.execute(String.format("INSERT INTO acd.section_faculty (tenant_id, section_id, employee_id, role) VALUES ('%s', '%s', '%s', 'PRIMARY') ON CONFLICT DO NOTHING", tenantA, sectionId2, employeeId1));
                stmt.execute(String.format("INSERT INTO acd.section_faculty (tenant_id, section_id, employee_id, role) VALUES ('%s', '%s', '%s', 'PRIMARY') ON CONFLICT DO NOTHING", tenantA, sectionId1, employeeId2));

                // Timetable and Version
                stmt.execute(String.format("INSERT INTO acd.timetables (id, tenant_id, college_id, term_id, name, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', '%s', 'Conflict Timetable', 'DRAFT', now(), now(), 1)", timetableId, tenantA, collegeId, termId));
                stmt.execute(String.format("INSERT INTO acd.timetable_versions (id, tenant_id, timetable_id, version_no, status, created_at, updated_at, row_version) VALUES ('%s', '%s', '%s', 1, 'DRAFT', now(), now(), 1)", versionId, tenantA, timetableId));

                // Switch to authenticated role
                stmt.execute("SET LOCAL ROLE authenticated");
                String claimsA = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsA + "', true)");

                // Insert Slot 1: Faculty 1, Section 1, Day 1, 10:00 to 11:30
                stmt.execute(String.format(
                        "INSERT INTO acd.timetable_entries (id, tenant_id, timetable_version_id, section_id, subject_id, employee_id, day_of_week, start_time, end_time, entry_type, valid_from, valid_to, created_at, updated_at, row_version) " +
                                "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 1, '10:00:00', '11:30:00', 'LECTURE', '2026-08-01', '2026-12-31', now(), now(), 1)",
                        UUID.randomUUID(), tenantA, versionId, sectionId1, subjectId, employeeId1));
                System.out.println("[SUCCESS] Inserted Slot 1: Faculty 1 on Day 1, 10:00-11:30");

                // Test Conflict A: Overlapping slot for same Faculty 1 on Day 1 (10:30 to 12:00) with Section 2
                // Uses savepoint to handle expected exclusion constraint violation cleanly within the uncommitted transaction
                Savepoint sp1 = conn.setSavepoint("sp1");
                try {
                    stmt.execute(String.format(
                            "INSERT INTO acd.timetable_entries (id, tenant_id, timetable_version_id, section_id, subject_id, employee_id, day_of_week, start_time, end_time, entry_type, valid_from, valid_to, created_at, updated_at, row_version) " +
                                    "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 1, '10:30:00', '12:00:00', 'LECTURE', '2026-08-01', '2026-12-31', now(), now(), 1)",
                            UUID.randomUUID(), tenantA, versionId, sectionId2, subjectId, employeeId1));
                    fail("Overlapping slot for same faculty MUST trigger ex_tte_employee exclusion constraint violation");
                } catch (Exception e) {
                    conn.rollback(sp1);
                    assertTrue("Exception message must reference ex_tte_employee",
                            e.getMessage().contains("ex_tte_employee"));
                    System.out.println("[SUCCESS] Live PostgreSQL ex_tte_employee GiST exclusion constraint correctly rejected faculty double-booking: " + e.getMessage());
                }

                // Test Conflict B: Overlapping slot for same Section 1 on Day 1 (10:15 to 11:15) with Faculty 2
                Savepoint sp2 = conn.setSavepoint("sp2");
                try {
                    stmt.execute(String.format(
                            "INSERT INTO acd.timetable_entries (id, tenant_id, timetable_version_id, section_id, subject_id, employee_id, day_of_week, start_time, end_time, entry_type, valid_from, valid_to, created_at, updated_at, row_version) " +
                                    "VALUES ('%s', '%s', '%s', '%s', '%s', '%s', 1, '10:15:00', '11:15:00', 'LECTURE', '2026-08-01', '2026-12-31', now(), now(), 1)",
                            UUID.randomUUID(), tenantA, versionId, sectionId1, subjectId, employeeId2));
                    fail("Overlapping slot for same section MUST trigger ex_tte_section exclusion constraint violation");
                } catch (Exception e) {
                    conn.rollback(sp2);
                    assertTrue("Exception message must reference ex_tte_section",
                            e.getMessage().contains("ex_tte_section"));
                    System.out.println("[SUCCESS] Live PostgreSQL ex_tte_section GiST exclusion constraint correctly rejected section/batch double-booking: " + e.getMessage());
                }

                System.out.println("\n[SUCCESS] Live PostgreSQL GiST Exclusion Conflict Constraints fully verified.");
            } finally {
                conn.rollback();
                conn.setAutoCommit(true);
            }
        }
    }
}
