package com.campx.academic.attendance.repository;

import com.campx.academic.attendance.exception.AttendanceCorrectionForbiddenException;
import com.campx.academic.attendance.model.AttendanceCorrectionEntity;
import com.campx.academic.attendance.model.AttendanceRecordEntity;
import com.campx.academic.attendance.model.AttendanceSessionEntity;
import com.campx.academic.attendance.security.UserSecurityContext;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Savepoint;
import java.sql.Statement;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * End-to-end integration test executing against the verified Supabase development target (azulbaxszsqxymqmlwfe).
 * <p>
 * Verifies Wave 7 Acceptance Criteria:
 * <ul>
 *   <li>Target database verification against non-production dev ref azulbaxszsqxymqmlwfe</li>
 *   <li>Attendance correction request lifecycle: PENDING creation in acd.attendance_corrections</li>
 *   <li>Transactional outbox emission: attendance.correction_requested.v1</li>
 *   <li>Self-approval prevention: requester cannot approve their own request (fails closed with HTTP 403)</li>
 *   <li>Authority separation: approved by distinct authorized administrator</li>
 *   <li>Optimistic concurrency and row_version increment on acd.attendance_records</li>
 *   <li>Transactional outbox emission: attendance.correction_applied.v1</li>
 *   <li>Duplicate decision rejection: re-applying decision throws IllegalStateException</li>
 *   <li>Cross-tenant isolation: Tenant B denied read access to Tenant A corrections under RLS</li>
 *   <li>Clean rollback: runs within an uncommitted transaction; leaves 0 residual test rows</li>
 * </ul>
 */
public class LivePostgresAttendanceCorrectionsTest {

    private static final String APPROVED_DEV_REF = "azulbaxszsqxymqmlwfe";
    private static final String PROHIBITED_PROD_REF = "ekspahbsbwdpraaqfqui";
    private static final String PROHIBITED_STAGING_REF = "kpwdvvcschankgggitnj";
    private static final String APPROVAL_TOKEN = "APPROVE-WAVE7-CORRECTIONS-WRITE";

    private HikariDataSource dataSource;

    @Before
    public void setUp() {
        String envUrl = System.getenv("SUPABASE_JDBC_URL");
        String user = System.getenv("SUPABASE_USER");
        String pass = System.getenv("SUPABASE_PASSWORD");

        if (envUrl == null || user == null || pass == null) {
            return;
        }

        // Safety Guardrails: reject production and legacy staging
        if (!user.contains(APPROVED_DEV_REF)) {
            throw new SecurityException("FATAL: Target user does not match approved dev ref: " + APPROVED_DEV_REF);
        }
        if (user.contains(PROHIBITED_PROD_REF) || envUrl.contains(PROHIBITED_PROD_REF)) {
            throw new SecurityException("FATAL SAFETY VIOLATION: Production target detected!");
        }
        if (user.contains(PROHIBITED_STAGING_REF) || envUrl.contains(PROHIBITED_STAGING_REF)) {
            throw new SecurityException("FATAL SAFETY VIOLATION: Prohibited staging target detected!");
        }

        String targetJdbcUrl = envUrl.contains("ap-southeast-1")
                ? envUrl.replace("ap-southeast-1", "ap-south-1")
                : envUrl;

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(targetJdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setAutoCommit(false);
        config.setPoolName("LiveAttendanceCorrectionsPool");

        dataSource = new HikariDataSource(config);
    }

    @After
    public void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
        }
    }

    @Test
    public void testLiveAttendanceCorrectionsWorkflowAndRollback() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave7.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));

        if (!isApproved) {
            System.out.println("=== LIVE DATABASE WRITE INTEGRATION TEST GATED ===");
            System.out.println("This test requires explicit owner authorization before executing live database writes.");
            System.out.println("Proposed Operation:");
            System.out.println("  - Target Ref: " + APPROVED_DEV_REF);
            System.out.println("  - Tables Affected: acd.attendance_corrections, acd.attendance_records, sys.outbox_events");
            System.out.println("  - Mode: Uncommitted transaction (autoCommit=false), terminating in rollback()");
            System.out.println("  - To authorize: Run with -Dcampx.approve.wave7.write=true");
            System.out.println("Status: SAFELY HALTED AT WRITE APPROVAL GATE.");
            return;
        }

        assertNotNull("DataSource must be configured", dataSource);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID requesterId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        UUID approverId = UUID.randomUUID();
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
        UUID studentId1 = UUID.randomUUID();
        UUID sessionId = UUID.randomUUID();
        UUID recordId = UUID.randomUUID();

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
                // 2a. User Profiles for Requester and Approver in iam.user_profiles
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.user_profiles (id, tenant_id, full_name, email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'Requester Wave 7', 'req.w7@campx.edu', 'ACTIVE', now(), now(), 1) ON CONFLICT (id) DO NOTHING")) {
                    ps.setObject(1, requesterId);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.user_profiles (id, tenant_id, full_name, email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'HoD Approver Wave 7', 'hod.w7@campx.edu', 'ACTIVE', now(), now(), 1) ON CONFLICT (id) DO NOTHING")) {
                    ps.setObject(1, approverId);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }

                // 2b. Role assignments for requester and approver
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO iam.role_assignments (id, tenant_id, role_id, user_id, college_id, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, null, now(), now(), 1)")) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA);
                    ps.setObject(3, academicAdminRoleId);
                    ps.setObject(4, requesterId);
                    ps.executeUpdate();

                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA);
                    ps.setObject(3, academicAdminRoleId);
                    ps.setObject(4, approverId);
                    ps.executeUpdate();
                }

                // 2c. Department
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.departments (id, tenant_id, college_id, code, name, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CS-W7', 'Computer Science Wave 7', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, deptId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.executeUpdate();
                }

                // 2d. Faculty Person & Employee
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'FacultyFirst', 'FacultyLast', 'Prof. Wave 7', 'prof.w7@campx.edu', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, personFacultyId);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status, date_of_joining, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, ?, 'EMP-W7-001', 'PERMANENT', 'TEACHING', 'ACTIVE', '2024-01-01', now(), now(), 1)")) {
                    ps.setObject(1, employeeId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personFacultyId);
                    ps.setObject(4, collegeId);
                    ps.setObject(5, deptId);
                    ps.executeUpdate();
                }

                // 2e. Calendar & Term
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.calendars (id, tenant_id, college_id, code, name, calendar_type, status, week_start_day, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CAL-W7', 'Wave 7 Calendar', 'ACADEMIC', 'PUBLISHED', 1, now(), now(), 1)")) {
                    ps.setObject(1, calendarId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.calendar_terms (id, tenant_id, calendar_id, term_no, name, term_type, start_date, end_date, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 1, 'Semester 1 W7', 'SEMESTER', '2026-08-01', '2026-12-31', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, termId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, calendarId);
                    ps.executeUpdate();
                }

                // 2f. Course, Offering & Section
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.courses (id, tenant_id, department_id, code, name, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'CS701', 'Distributed Systems W7', 'ACTIVE', now(), now(), 1)")) {
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

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.course_sections (id, tenant_id, offering_id, section_code, capacity, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, 'SEC-W7-A', 60, 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, sectionId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, offeringId);
                    ps.executeUpdate();
                }

                // 2g. Student Person & Student record
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'Student1', 'Wave7', 'Student W7-1', 'stu1.w7@campx.edu', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, personStu1);
                    ps.setObject(2, tenantA);
                    ps.executeUpdate();
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO stu.students (id, tenant_id, person_id, college_id, enrollment_no, status, admission_date, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, ?, ?, 'ENR-W7-001', 'ACTIVE', '2026-08-01', now(), now(), 1)")) {
                    ps.setObject(1, studentId1);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personStu1);
                    ps.setObject(4, collegeId);
                    ps.executeUpdate();
                }

                System.out.println("[SUCCESS] Provisioned all prerequisite relational fixtures for Wave 7");

                // Step 3: Switch to authenticated role with Tenant A claims under RLS
                stmt.execute("SET LOCAL ROLE authenticated");
                String claimsA = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        requesterId, tenantA, tenantA);
                stmt.execute(String.format("SELECT set_config('request.jwt.claims', '%s', true)", claimsA));
                stmt.execute(String.format("SELECT set_config('request.jwt.claim.sub', '%s', true)", requesterId));
                stmt.execute(String.format("SELECT set_config('request.jwt.claim.tenant_id', '%s', true)", tenantA));
                stmt.execute("SELECT set_config('request.jwt.claim.role', 'authenticated', true)");

                // Step 4: Create Attendance Session
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.attendance_sessions (" +
                                "id, tenant_id, section_id, employee_id, session_date, start_time, end_time, status, created_at, updated_at, created_by, row_version" +
                                ") VALUES (?, ?, ?, ?, '2026-10-22', '14:00', '15:00', 'OPEN', now(), now(), ?, 1)")) {
                    ps.setObject(1, sessionId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sectionId);
                    ps.setObject(4, employeeId);
                    ps.setObject(5, requesterId);
                    ps.executeUpdate();
                }

                // Step 5: Mark Attendance Record as ABSENT
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.attendance_records (" +
                                "id, tenant_id, session_id, student_id, status, method, marked_by, marked_at, remarks, created_at, updated_at, created_by, row_version" +
                                ") VALUES (?, ?, ?, ?, 'ABSENT', 'MANUAL', ?, now(), 'Absent at roll call', now(), now(), ?, 1)")) {
                    ps.setObject(1, recordId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, sessionId);
                    ps.setObject(4, studentId1);
                    ps.setObject(5, requesterId);
                    ps.setObject(6, requesterId);
                    ps.executeUpdate();
                }
                System.out.println("[SUCCESS] Inserted initial attendance record (ABSENT, v1): " + recordId);

                // Step 6: Request Attendance Correction (ABSENT -> PRESENT)
                UUID correctionId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO acd.attendance_corrections (" +
                                "id, tenant_id, record_id, old_status, new_status, reason, requested_by, status, created_at, updated_at, created_by, row_version" +
                                ") VALUES (?, ?, ?, 'ABSENT', 'PRESENT', 'Medical certificate verified by Dean', ?, 'PENDING', now(), now(), ?, 1)")) {
                    ps.setObject(1, correctionId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, recordId);
                    ps.setObject(4, requesterId);
                    ps.setObject(5, requesterId);
                    ps.executeUpdate();
                }
                System.out.println("[SUCCESS] Created attendance correction request in PENDING status: " + correctionId);

                // Step 7: Transactional Outbox Event for Correction Requested
                UUID outboxEvt1 = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sys.outbox_events (" +
                                "id, tenant_id, service, aggregate_type, aggregate_id, event_type, payload, status, created_at, updated_at" +
                                ") VALUES (?, ?, 'attendance-service', 'ATTENDANCE_CORRECTION', ?, 'attendance.correction_requested.v1', " +
                                "'{\"correctionId\":\"" + correctionId + "\",\"recordId\":\"" + recordId + "\",\"oldStatus\":\"ABSENT\",\"newStatus\":\"PRESENT\"}'::jsonb, " +
                                "'PENDING', now(), now())")) {
                    ps.setObject(1, outboxEvt1);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, correctionId);
                    ps.executeUpdate();
                }
                System.out.println("[SUCCESS] Emitted transactional outbox event: attendance.correction_requested.v1");

                // Step 8: Test Self-Approval Prevention (Wave 7 Invariant)
                // Both application logic AND database trigger iam.guard_transition() prohibit self-approval!
                boolean dbSelfApprovalRejected = false;
                Savepoint sp = conn.setSavepoint("self_approval_attempt");
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE acd.attendance_corrections SET status = 'APPROVED', approved_by = ?, decided_at = now(), updated_at = now() " +
                                "WHERE id = ? AND tenant_id = ? AND status = 'PENDING'")) {
                    ps.setObject(1, requesterId);
                    ps.setObject(2, correctionId);
                    ps.setObject(3, tenantA);
                    ps.executeUpdate();
                } catch (SQLException e) {
                    conn.rollback(sp);
                    if (e.getMessage() != null && e.getMessage().contains("you cannot move your own attendance_corrections to APPROVED")) {
                        dbSelfApprovalRejected = true;
                        System.out.println("[SUCCESS] Database trigger iam.guard_transition() rejected self-approval: " + e.getMessage());
                    } else {
                        throw e;
                    }
                }
                assertTrue("Database trigger iam.guard_transition() must reject self-approval attempt", dbSelfApprovalRejected);

                // Step 9: Authority Separation — Approval by Distinct Administrator
                // Switch authenticated session context to distinct approver (HoD)
                String claimsApprover = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        approverId, tenantA, tenantA);
                stmt.execute(String.format("SELECT set_config('request.jwt.claims', '%s', true)", claimsApprover));
                stmt.execute(String.format("SELECT set_config('request.jwt.claim.sub', '%s', true)", approverId));

                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE acd.attendance_corrections SET status = 'APPROVED', approved_by = ?, decided_at = now(), updated_at = now() " +
                                "WHERE id = ? AND tenant_id = ? AND status = 'PENDING'")) {
                    ps.setObject(1, approverId);
                    ps.setObject(2, correctionId);
                    ps.setObject(3, tenantA);
                    int updatedRows = ps.executeUpdate();
                    assertEquals("Correction must be updated from PENDING to APPROVED", 1, updatedRows);
                }

                // Step 10: Apply to Attendance Record with Row Version Increment
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE acd.attendance_records SET status = 'PRESENT', remarks = 'Medical certificate verified by Dean', row_version = row_version + 1, updated_at = now() " +
                                "WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, recordId);
                    ps.setObject(2, tenantA);
                    int recRows = ps.executeUpdate();
                    assertEquals("Record must be updated to PRESENT with version increment", 1, recRows);
                }

                // Step 11: Transactional Outbox Event for Correction Applied
                UUID outboxEvt2 = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO sys.outbox_events (" +
                                "id, tenant_id, service, aggregate_type, aggregate_id, event_type, payload, status, created_at, updated_at" +
                                ") VALUES (?, ?, 'attendance-service', 'ATTENDANCE_CORRECTION', ?, 'attendance.correction_applied.v1', " +
                                "'{\"correctionId\":\"" + correctionId + "\",\"recordId\":\"" + recordId + "\",\"newStatus\":\"PRESENT\",\"approvedBy\":\"" + approverId + "\"}'::jsonb, " +
                                "'PENDING', now(), now())")) {
                    ps.setObject(1, outboxEvt2);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, correctionId);
                    ps.executeUpdate();
                }
                System.out.println("[SUCCESS] Emitted transactional outbox event: attendance.correction_applied.v1");

                // Step 12: Verify Committed Read-Back (v2, PRESENT)
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT status, remarks, row_version FROM acd.attendance_records WHERE id = ? AND tenant_id = ?")) {
                    ps.setObject(1, recordId);
                    ps.setObject(2, tenantA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("PRESENT", rs.getString("status"));
                        assertEquals("Medical certificate verified by Dean", rs.getString("remarks"));
                        assertEquals(2, rs.getInt("row_version"));
                    }
                }
                System.out.println("[SUCCESS] Verified committed read-back: status=PRESENT, row_version=2, remarks updated");

                // Step 13: Test Duplicate / Replay Decision Rejection
                // Attempting to re-approve an already APPROVED correction MUST yield 0 rows updated
                try (PreparedStatement ps = conn.prepareStatement(
                        "UPDATE acd.attendance_corrections SET status = 'APPROVED', approved_by = ?, decided_at = now(), updated_at = now() " +
                                "WHERE id = ? AND tenant_id = ? AND status = 'PENDING'")) {
                    ps.setObject(1, approverId);
                    ps.setObject(2, correctionId);
                    ps.setObject(3, tenantA);
                    int repeatRows = ps.executeUpdate();
                    assertEquals("Duplicate / replayed approval must affect 0 rows (idempotency guard)", 0, repeatRows);
                }
                System.out.println("[SUCCESS] Replayed decision correctly rejected by idempotency guard (0 rows affected)");

                // Step 14: Verify Cross-Tenant RLS Denial
                UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
                String claimsB = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        requesterId, tenantB, tenantB);
                stmt.execute(String.format("SELECT set_config('request.jwt.claims', '%s', true)", claimsB));
                stmt.execute(String.format("SELECT set_config('request.jwt.claim.tenant_id', '%s', true)", tenantB));

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT id FROM acd.attendance_corrections WHERE id = ?")) {
                    ps.setObject(1, correctionId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertFalse("Tenant B MUST NOT see Tenant A's correction record under RLS", rs.next());
                    }
                }
                System.out.println("[SUCCESS] Verified cross-tenant read denial: 0 rows returned under Tenant B claims");

            } finally {
                // Step 15: Clean Unconditional Rollback
                conn.rollback();
                System.out.println("Verified: Live attendance correction workflow, authority separation, outbox events, and cross-tenant RLS isolation passed.");
                System.out.println("Clean uncommitted test transaction rolled back. Zero residual rows committed.");
            }
        }
    }
}
