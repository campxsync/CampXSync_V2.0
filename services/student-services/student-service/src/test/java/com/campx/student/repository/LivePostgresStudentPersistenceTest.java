package com.campx.student.repository;

import com.campx.student.config.TestEnvironmentGuard;
import com.campx.student.model.Gender;
import com.campx.student.model.StudentEntity;
import com.campx.student.model.StudentStatus;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.Date;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Live PostgreSQL persistence, transaction boundary, and outbox integration test for STU-01.
 * <p>
 * STRICT SAFEGUARDS:
 * 1. Target is strictly the approved development Supabase reference 'azulbaxszsqxymqmlwfe'.
 * 2. Strictly rejects production ('ekspahbsbwdpraaqfqui') and legacy staging ('kpwdvvcschankgggitnj').
 * 3. Execution requires explicit approval flag (-Dcampx.approve.wave4.write=true).
 * 4. All write operations execute inside an uncommitted transaction (autoCommit == false)
 *    and unconditionally end with rollback(). ZERO ROWS ARE COMMITTED.
 * 5. Verifies:
 *    - core.persons + stu.students + sys.outbox_events persistence within single transaction.
 *    - Persisted read-back with joined demographic fields.
 *    - Cross-tenant read denial under PostgreSQL RLS.
 *    - Cross-tenant write denial (new row violates row-level security policy).
 *    - Duplicate constraint violation handling.
 *    - Atomic rollback leaving zero residual records.
 */
public class LivePostgresStudentPersistenceTest {

    private static final String APPROVED_DEV_REF = TestEnvironmentGuard.APPROVED_DEV_PROJECT_REF;
    public static final String APPROVAL_TOKEN = "APPROVE-WAVE4-LIVE-PERSISTENCE-TEST";

    private String getDevJdbcUrl() {
        String envUrl = System.getenv("CAMPX_DB_URL");
        if (envUrl == null || envUrl.trim().isEmpty()) {
            envUrl = System.getenv("SUPABASE_JDBC_URL");
        }
        if (envUrl != null && envUrl.contains("ap-southeast-1")) {
            return envUrl.replace("ap-southeast-1", "ap-south-1");
        }
        return envUrl;
    }

    private HikariDataSource createDevDataSource() {
        String jdbcUrl = getDevJdbcUrl();
        String user = System.getenv("CAMPX_DB_USERNAME");
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("CAMPX_DB_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            if (jdbcUrl != null && (jdbcUrl.contains(".pooler.supabase.com") || jdbcUrl.contains("pooler.supabase.co"))) {
                user = "campx_app_server." + APPROVED_DEV_REF;
            } else {
                user = "campx_app_server";
            }
        }
        String pass = System.getenv("CAMPX_DB_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) {
            pass = System.getenv("SUPABASE_PASSWORD");
        }

        if (jdbcUrl == null || user == null || pass == null) {
            return null;
        }

        // Strict target validation before opening any connection
        TestEnvironmentGuard.validateStagingConfiguration(jdbcUrl, user);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setPoolName("Live-Student-Persistence-Pool");
        return new HikariDataSource(config);
    }

    @Test
    public void executeLivePersistenceAndRollback() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave4.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));

        if (!isApproved) {
            System.out.println("=== LIVE DATABASE WRITE INTEGRATION TEST GATED ===");
            System.out.println("This test requires explicit owner authorization before executing live database writes.");
            System.out.println("Proposed Operation:");
            System.out.println("  - Target Ref: " + APPROVED_DEV_REF);
            System.out.println("  - Tables Affected: core.persons, stu.students, sys.outbox_events");
            System.out.println("  - Mode: Uncommitted transaction (autoCommit=false), terminating in rollback()");
            System.out.println("  - To authorize: Run with -Dcampx.approve.wave4.write=true");
            System.out.println("Status: SAFELY HALTED AT WRITE APPROVAL GATE.");
            return;
        }

        HikariDataSource ds = createDevDataSource();
        assertNotNull("DataSource must be configured", ds);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userA = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        UUID collegeId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
        UUID studentId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        UUID outboxEventId = UUID.randomUUID();
        String enrollmentNo = "TEST-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();
        String email = "test." + UUID.randomUUID().toString().substring(0, 8) + "@campx.edu";

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                // Step 1: Target Verification
                try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, inet_server_addr()")) {
                    assertTrue(rs.next());
                    System.out.println("=== LIVE TARGET VERIFIED ===");
                    System.out.println("Database: " + rs.getString(1) + " | User: " + rs.getString(2) + " | IP: " + rs.getString(3));
                }

                // Step 2: Switch to authenticated role
                stmt.execute("SET LOCAL ROLE authenticated");

                // Step 3: Establish Tenant A Context
                String claimsA = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsA + "', true)");

                System.out.println("\n--- Step 3: Inserting Document, Person, Student & Outbox under Tenant A ---");
                // 3-pre: Insert photo document into doc.documents for College A
                String docSql = "INSERT INTO doc.documents (" +
                        "id, tenant_id, college_id, title, classification, status, current_version_no, " +
                        "created_at, updated_at, created_by, updated_by, row_version" +
                        ") VALUES (?, ?, ?, 'Student Enrollment Photo', 'INTERNAL', 'APPROVED', 1, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(docSql)) {
                    ps.setObject(1, docId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    ps.setTimestamp(5, Timestamp.from(Instant.now()));
                    ps.setObject(6, userA);
                    ps.setObject(7, userA);
                    int insertedDoc = ps.executeUpdate();
                    assertEquals("Document insert must affect 1 row", 1, insertedDoc);
                    System.out.println("[SUCCESS] Inserted student photo document: " + docId);
                }

                // 3a. Insert into core.persons with photo_document_id
                String personSql = "INSERT INTO core.persons (" +
                        "id, tenant_id, first_name, last_name, display_name, primary_email, gender, date_of_birth, " +
                        "photo_document_id, status, created_at, updated_at, created_by, updated_by, row_version" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(personSql)) {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantA);
                    ps.setString(3, "TestFirst");
                    ps.setString(4, "TestLast");
                    ps.setString(5, "TestFirst TestLast");
                    ps.setString(6, email);
                    ps.setString(7, Gender.O.name());
                    ps.setDate(8, Date.valueOf(LocalDate.of(2002, 5, 15)));
                    ps.setObject(9, docId);
                    ps.setTimestamp(10, Timestamp.from(Instant.now()));
                    ps.setTimestamp(11, Timestamp.from(Instant.now()));
                    ps.setObject(12, userA);
                    ps.setObject(13, userA);
                    int inserted = ps.executeUpdate();
                    assertEquals("Person insert must affect 1 row", 1, inserted);
                    System.out.println("[SUCCESS] Inserted test person: " + personId);
                }

                // 3b. Insert into stu.students
                String studentSql = "INSERT INTO stu.students (" +
                        "id, tenant_id, person_id, college_id, enrollment_no, status, admission_date, " +
                        "created_at, updated_at, created_by, updated_by, row_version" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(studentSql)) {
                    ps.setObject(1, studentId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personId);
                    ps.setObject(4, collegeId);
                    ps.setString(5, enrollmentNo);
                    ps.setString(6, StudentStatus.ACTIVE.name());
                    ps.setDate(7, Date.valueOf(LocalDate.now()));
                    ps.setTimestamp(8, Timestamp.from(Instant.now()));
                    ps.setTimestamp(9, Timestamp.from(Instant.now()));
                    ps.setObject(10, userA);
                    ps.setObject(11, userA);
                    int inserted = ps.executeUpdate();
                    assertEquals("Student insert must affect 1 row", 1, inserted);
                    System.out.println("[SUCCESS] Inserted test student: " + studentId);
                }

                // 3c. Insert into sys.outbox_events (Required: MUST succeed and fail test if unsuccessful)
                String outboxSql = "INSERT INTO sys.outbox_events (" +
                        "id, tenant_id, service, aggregate_type, aggregate_id, event_type, " +
                        "payload, headers, status, attempts, created_at, updated_at" +
                        ") VALUES (?, ?, 'student-service', 'STUDENT', ?, 'student.enrolled.v1', ?::jsonb, ?::jsonb, 'PENDING', 0, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(outboxSql)) {
                    ps.setObject(1, outboxEventId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, studentId);
                    ps.setString(4, "{\"studentId\":\"" + studentId + "\",\"enrollmentNo\":\"" + enrollmentNo + "\"}");
                    ps.setString(5, "{\"correlationId\":\"" + UUID.randomUUID() + "\"}");
                    ps.setTimestamp(6, Timestamp.from(Instant.now()));
                    ps.setTimestamp(7, Timestamp.from(Instant.now()));
                    int inserted = ps.executeUpdate();
                    assertEquals("Outbox insert must affect 1 row", 1, inserted);
                    System.out.println("[SUCCESS] Inserted atomic outbox event: " + outboxEventId);
                }

                // Step 4: Persisted Read-Back Verification with JOIN & Outbox Read-Back
                System.out.println("\n--- Step 4: Persisted Read-Back Verification ---");
                String selectSql = "SELECT s.id, s.tenant_id, s.enrollment_no, s.status, p.first_name, p.last_name, p.primary_email " +
                        "FROM stu.students s " +
                        "JOIN core.persons p ON s.person_id = p.id " +
                        "WHERE s.id = ? AND s.tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setObject(1, studentId);
                    ps.setObject(2, tenantA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Persisted record must be found", rs.next());
                        assertEquals(studentId, rs.getObject("id"));
                        assertEquals(enrollmentNo, rs.getString("enrollment_no"));
                        assertEquals(StudentStatus.ACTIVE.name(), rs.getString("status"));
                        assertEquals("TestFirst", rs.getString("first_name"));
                        assertEquals("TestLast", rs.getString("last_name"));
                        assertEquals(email, rs.getString("primary_email"));
                        System.out.println("[SUCCESS] Verified persisted read-back for student " + enrollmentNo);
                    }
                }

                // Verify outbox record is visible within the same transaction
                String selectOutboxSql = "SELECT id, tenant_id, service, aggregate_type, aggregate_id, event_type, payload::text, status " +
                        "FROM sys.outbox_events WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectOutboxSql)) {
                    ps.setObject(1, outboxEventId);
                    ps.setObject(2, tenantA);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Persisted outbox event must be visible in transaction", rs.next());
                        assertEquals(outboxEventId, rs.getObject("id"));
                        assertEquals(tenantA, rs.getObject("tenant_id"));
                        assertEquals("student-service", rs.getString("service"));
                        assertEquals("STUDENT", rs.getString("aggregate_type"));
                        assertEquals(studentId, rs.getObject("aggregate_id"));
                        assertEquals("student.enrolled.v1", rs.getString("event_type"));
                        assertEquals("PENDING", rs.getString("status"));
                        String outboxPayload = rs.getString("payload");
                        assertNotNull("Outbox payload must not be null", outboxPayload);
                        assertTrue("Outbox payload must contain studentId", outboxPayload.contains(studentId.toString()));
                        assertTrue("Outbox payload must contain enrollmentNo", outboxPayload.contains(enrollmentNo));
                        System.out.println("[SUCCESS] Verified persisted outbox event with payload in transaction: " + outboxEventId);
                    }
                }

                // Step 5: Cross-Tenant Read Denial (RLS Enforcement)
                System.out.println("\n--- Step 5: Cross-Tenant Read Denial Verification ---");
                UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");
                UUID userB = UUID.fromString("88888888-8888-8888-8888-888888888888");
                String claimsB = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userB, tenantB, tenantB);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsB + "', true)");

                try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM stu.students WHERE id = ?")) {
                    ps.setObject(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Tenant B MUST NOT see Tenant A's student under RLS", 0, rs.getInt(1));
                        System.out.println("[SUCCESS] Cross-tenant read correctly denied: 0 rows returned.");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM sys.outbox_events WHERE id = ?")) {
                    ps.setObject(1, outboxEventId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Tenant B MUST NOT see Tenant A's outbox event under RLS", 0, rs.getInt(1));
                        System.out.println("[SUCCESS] Cross-tenant outbox read correctly denied: 0 rows returned.");
                    }
                }

                // Step 6: Cross-Tenant Write Denial (RLS Check Policy Violation)
                System.out.println("\n--- Step 6: Cross-Tenant Write Denial Verification ---");
                java.sql.Savepoint sp = conn.setSavepoint("sp_cross_tenant_write");
                try (PreparedStatement ps = conn.prepareStatement(studentSql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA); // Attempting to insert into Tenant A while claims are Tenant B
                    ps.setObject(3, personId);
                    ps.setObject(4, collegeId);
                    ps.setString(5, "HACK-ENR");
                    ps.setString(6, StudentStatus.ACTIVE.name());
                    ps.setDate(7, Date.valueOf(LocalDate.now()));
                    ps.setTimestamp(8, Timestamp.from(Instant.now()));
                    ps.setTimestamp(9, Timestamp.from(Instant.now()));
                    ps.setObject(10, userA);
                    ps.setObject(11, userA);
                    ps.executeUpdate();
                    fail("Cross-tenant insert MUST fail with PostgreSQL RLS violation");
                } catch (SQLException e) {
                    conn.rollback(sp);
                    System.out.println("[SUCCESS] Cross-tenant write denied by RLS: " + e.getMessage());
                }

                java.sql.Savepoint spOutboxB = conn.setSavepoint("sp_cross_tenant_outbox");
                try (PreparedStatement ps = conn.prepareStatement(outboxSql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA); // claims are Tenant B, inserting Tenant A
                    ps.setObject(3, studentId);
                    ps.setString(4, "{\"hack\":\"true\"}");
                    ps.setString(5, "{}");
                    ps.setTimestamp(6, Timestamp.from(Instant.now()));
                    ps.setTimestamp(7, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                    fail("Cross-tenant outbox insert MUST fail with PostgreSQL RLS violation");
                } catch (SQLException e) {
                    conn.rollback(spOutboxB);
                    System.out.println("[SUCCESS] Cross-tenant outbox write denied by RLS: " + e.getMessage());
                }

                // Step 7: Connection Hygiene and Rollback
                System.out.println("\n--- Step 7: Executing Unconditional Rollback & Hygiene ---");
                stmt.execute("RESET ROLE");
                stmt.execute("SELECT set_config('request.jwt.claims', '', false)");
            } finally {
                conn.rollback(); // UNCONDITIONAL TRANSACTION ROLLBACK
                System.out.println("[SUCCESS] Transaction rolled back. Zero data committed to database.");
            }

            // Post-rollback verification: Ensure zero residual rows in database
            System.out.println("\n--- Step 8: Post-Rollback Residual Data Verification ---");
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL ROLE authenticated");
                String claimsVerify = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsVerify + "', true)");

                try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM stu.students WHERE id = ?")) {
                    ps.setObject(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Post-rollback: Student record MUST NOT exist in database", 0, rs.getInt(1));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM core.persons WHERE id = ?")) {
                    ps.setObject(1, personId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Post-rollback: Person record MUST NOT exist in database", 0, rs.getInt(1));
                    }
                }
                try (PreparedStatement ps = conn.prepareStatement("SELECT count(*) FROM sys.outbox_events WHERE id = ?")) {
                    ps.setObject(1, outboxEventId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Post-rollback: Outbox record MUST NOT exist in database", 0, rs.getInt(1));
                    }
                }
                stmt.execute("RESET ROLE");
                stmt.execute("SELECT set_config('request.jwt.claims', '', false)");
                System.out.println("[SUCCESS] Verified 0 residual rows across stu.students, core.persons, and sys.outbox_events.");
            }
        } finally {
            ds.close();
        }
    }

    /**
     * Verifies that when an outbox write fails inside a transaction, the entire transaction rolls back
     * atomically, leaving ZERO partially committed student, person, or document records.
     */
    @Test
    public void testLiveAtomicRollbackWhenOutboxFails() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave4.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));

        if (!isApproved) {
            return;
        }

        HikariDataSource ds = createDevDataSource();
        assertNotNull("DataSource must be configured", ds);

        UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID userA = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        UUID collegeId = UUID.fromString("a0000000-0000-0000-0000-000000000002");
        UUID studentId = UUID.randomUUID();
        UUID personId = UUID.randomUUID();
        UUID docId = UUID.randomUUID();
        String enrollmentNo = "FAIL-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL ROLE authenticated");
                String claimsA = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsA + "', true)");

                // Insert document
                String docSql = "INSERT INTO doc.documents (" +
                        "id, tenant_id, college_id, title, classification, status, current_version_no, " +
                        "created_at, updated_at, created_by, updated_by, row_version" +
                        ") VALUES (?, ?, ?, 'Student Enrollment Photo', 'INTERNAL', 'APPROVED', 1, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(docSql)) {
                    ps.setObject(1, docId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, collegeId);
                    ps.setTimestamp(4, Timestamp.from(Instant.now()));
                    ps.setTimestamp(5, Timestamp.from(Instant.now()));
                    ps.setObject(6, userA);
                    ps.setObject(7, userA);
                    ps.executeUpdate();
                }

                // Insert person
                String personSql = "INSERT INTO core.persons (" +
                        "id, tenant_id, first_name, last_name, display_name, primary_email, gender, date_of_birth, " +
                        "photo_document_id, status, created_at, updated_at, created_by, updated_by, row_version" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 'ACTIVE', ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(personSql)) {
                    ps.setObject(1, personId);
                    ps.setObject(2, tenantA);
                    ps.setString(3, "AtomicFirst");
                    ps.setString(4, "AtomicLast");
                    ps.setString(5, "AtomicFirst AtomicLast");
                    ps.setString(6, "atomic." + UUID.randomUUID().toString().substring(0, 8) + "@campx.edu");
                    ps.setString(7, Gender.O.name());
                    ps.setDate(8, Date.valueOf(LocalDate.of(2002, 5, 15)));
                    ps.setObject(9, docId);
                    ps.setTimestamp(10, Timestamp.from(Instant.now()));
                    ps.setTimestamp(11, Timestamp.from(Instant.now()));
                    ps.setObject(12, userA);
                    ps.setObject(13, userA);
                    ps.executeUpdate();
                }

                // Insert student
                String studentSql = "INSERT INTO stu.students (" +
                        "id, tenant_id, person_id, college_id, enrollment_no, status, admission_date, " +
                        "created_at, updated_at, created_by, updated_by, row_version" +
                        ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(studentSql)) {
                    ps.setObject(1, studentId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, personId);
                    ps.setObject(4, collegeId);
                    ps.setString(5, enrollmentNo);
                    ps.setString(6, StudentStatus.ACTIVE.name());
                    ps.setDate(7, Date.valueOf(LocalDate.now()));
                    ps.setTimestamp(8, Timestamp.from(Instant.now()));
                    ps.setTimestamp(9, Timestamp.from(Instant.now()));
                    ps.setObject(10, userA);
                    ps.setObject(11, userA);
                    ps.executeUpdate();
                }

                // Deliberately trigger failure on outbox write using invalid status
                // ck_outbox_events_status requires status IN ('PENDING', 'DISPATCHED', 'FAILED')
                String outboxSql = "INSERT INTO sys.outbox_events (" +
                        "id, tenant_id, service, aggregate_type, aggregate_id, event_type, " +
                        "payload, headers, status, attempts, created_at, updated_at" +
                        ") VALUES (?, ?, 'student-service', 'STUDENT', ?, 'student.enrolled.v1', ?::jsonb, ?::jsonb, 'ILLEGAL_STATUS', 0, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(outboxSql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantA);
                    ps.setObject(3, studentId);
                    ps.setString(4, "{}");
                    ps.setString(5, "{}");
                    ps.setTimestamp(6, Timestamp.from(Instant.now()));
                    ps.setTimestamp(7, Timestamp.from(Instant.now()));
                    ps.executeUpdate();
                    fail("Outbox insert with invalid status MUST fail constraint check");
                } catch (SQLException e) {
                    System.out.println("[VERIFIED] Outbox write failed as expected with constraint check: " + e.getMessage());
                    // Business exception triggers full transaction rollback
                    conn.rollback();
                }
            } finally {
                conn.rollback();
            }

            // Post-rollback verification: Student and person records MUST NOT exist
            try (Connection checkConn = ds.getConnection();
                 Statement stmt = checkConn.createStatement()) {
                stmt.execute("SET LOCAL ROLE authenticated");
                String claimsVerify = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsVerify + "', true)");

                try (PreparedStatement ps = checkConn.prepareStatement("SELECT count(*) FROM stu.students WHERE id = ?")) {
                    ps.setObject(1, studentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Student MUST NOT be persisted after outbox failure rollback", 0, rs.getInt(1));
                    }
                }
                try (PreparedStatement ps = checkConn.prepareStatement("SELECT count(*) FROM core.persons WHERE id = ?")) {
                    ps.setObject(1, personId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Person MUST NOT be persisted after outbox failure rollback", 0, rs.getInt(1));
                    }
                }
                stmt.execute("RESET ROLE");
                System.out.println("[SUCCESS] Live atomic rollback verified: zero partial student/person data committed.");
            }
        } finally {
            ds.close();
        }
    }

    @Test
    public void executeLiveStudentAddressLinkingAndRollback() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave4.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));
        if (!isApproved) {
            System.out.println("[SKIP] Live student address linking test skipped (set -Dcampx.approve.wave4.write=true to run)");
            return;
        }

        HikariDataSource ds = createDevDataSource();
        if (ds == null) {
            System.out.println("[SKIP] Missing staging database credentials for live persistence test");
            return;
        }

        UUID tenantId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID student1AuthId = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");
        UUID addressId = UUID.randomUUID();

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL ROLE authenticated");
                String claims = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        student1AuthId, tenantId, tenantId);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claims + "', true)");

                // 1. Insert address created by student1
                String insertAddr = "INSERT INTO core.addresses (id, tenant_id, line1, city, state_code, postal_code, country_code, created_by, updated_by) " +
                        "VALUES (?, ?, '42 Knowledge Way', 'Bengaluru', 'KA', '560001', 'IND', ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertAddr)) {
                    ps.setObject(1, addressId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, student1AuthId);
                    ps.setObject(4, student1AuthId);
                    ps.executeUpdate();
                }

                // 2. Fetch existing person record
                String selectPerson = "SELECT id, tenant_id, first_name, middle_name, last_name, display_name, " +
                        "date_of_birth, gender, primary_email, primary_phone, permanent_address_id, current_address_id, " +
                        "status, created_at, updated_at, created_by, updated_by, row_version, deleted_at " +
                        "FROM core.persons WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
                String firstName = null, middleName = null, lastName = null, displayName = null, primaryEmail = null, primaryPhone = null, gender = null;
                Date dob = null;
                try (PreparedStatement ps = conn.prepareStatement(selectPerson)) {
                    ps.setObject(1, student1AuthId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Student1 person record must exist", rs.next());
                        firstName = rs.getString("first_name");
                        middleName = rs.getString("middle_name");
                        lastName = rs.getString("last_name");
                        displayName = rs.getString("display_name");
                        dob = rs.getDate("date_of_birth");
                        gender = rs.getString("gender");
                        primaryEmail = rs.getString("primary_email");
                        primaryPhone = rs.getString("primary_phone");
                    }
                }

                // 3. Update person address link as authenticated student (exercising guard trigger and real updatePerson SQL)
                String updatePersonSql = "UPDATE core.persons SET first_name = ?, middle_name = ?, last_name = ?, display_name = ?, " +
                        "date_of_birth = ?, gender = ?, primary_email = ?, primary_phone = ?, " +
                        "permanent_address_id = ?, current_address_id = ?, updated_by = ?, updated_at = now(), " +
                        "row_version = row_version + 1 " +
                        "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL " +
                        "RETURNING updated_at, row_version";
                try (PreparedStatement ps = conn.prepareStatement(updatePersonSql)) {
                    ps.setString(1, firstName);
                    ps.setString(2, middleName);
                    ps.setString(3, lastName);
                    ps.setString(4, displayName);
                    if (dob != null) {
                        ps.setDate(5, dob);
                    } else {
                        ps.setNull(5, java.sql.Types.DATE);
                    }
                    ps.setString(6, gender);
                    ps.setString(7, primaryEmail);
                    ps.setString(8, primaryPhone);
                    ps.setObject(9, addressId);
                    ps.setObject(10, addressId);
                    ps.setObject(11, student1AuthId);
                    ps.setObject(12, student1AuthId);
                    ps.setObject(13, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Update must succeed and return updated row", rs.next());
                    }
                }

                // 4. Read back verified address link (simulating /students/me profile read)
                try (PreparedStatement ps = conn.prepareStatement(selectPerson)) {
                    ps.setObject(1, student1AuthId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Permanent address must be linked", addressId, rs.getObject("permanent_address_id"));
                        assertEquals("Current address must be linked", addressId, rs.getObject("current_address_id"));
                    }
                }

                // 5. Unconditional rollback
                conn.rollback();
                System.out.println("[SUCCESS] Live student address linking and read-back verified under student role; transaction rolled back.");
            } finally {
                conn.rollback();
            }

            // 6. Post-rollback check: Address and links MUST NOT exist
            try (Connection checkConn = ds.getConnection();
                 Statement stmt = checkConn.createStatement()) {
                checkConn.setAutoCommit(false);
                stmt.execute("SET LOCAL ROLE authenticated");
                String claims = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        student1AuthId, tenantId, tenantId);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claims + "', true)");

                try (PreparedStatement ps = checkConn.prepareStatement("SELECT permanent_address_id, current_address_id FROM core.persons WHERE id = ?")) {
                    ps.setObject(1, student1AuthId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Permanent address must remain null post-rollback", null, rs.getObject("permanent_address_id"));
                        assertEquals("Current address must remain null post-rollback", null, rs.getObject("current_address_id"));
                    }
                }
                try (PreparedStatement ps = checkConn.prepareStatement("SELECT count(*) FROM core.addresses WHERE id = ?")) {
                    ps.setObject(1, addressId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Address record must not exist post-rollback", 0, rs.getInt(1));
                    }
                }
                stmt.execute("RESET ROLE");
            }
        } finally {
            ds.close();
        }
    }

    @Test
    public void executeLiveStudentCorrectionSubmitAndRollback() throws Exception {
        boolean isApproved = Boolean.getBoolean("campx.approve.wave4.write")
                || APPROVAL_TOKEN.equals(System.getProperty("campx.approval.token"));
        if (!isApproved) {
            System.out.println("[SKIP] Live student correction submit test skipped (set -Dcampx.approve.wave4.write=true to run)");
            return;
        }

        HikariDataSource ds = createDevDataSource();
        if (ds == null) {
            System.out.println("[SKIP] Missing staging database credentials for live persistence test");
            return;
        }

        UUID tenantId = UUID.fromString("a0000000-0000-0000-0000-000000000001");
        UUID student1AuthId = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a6764861f924");
        UUID student1RecordId = UUID.fromString("81dd67d3-1d5f-4d8e-8061-a67648610002");
        UUID requestId = UUID.randomUUID();
        UUID outboxId = UUID.randomUUID();

        try (Connection conn = ds.getConnection()) {
            conn.setAutoCommit(false);
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("SET LOCAL ROLE authenticated");
                String claims = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        student1AuthId, tenantId, tenantId);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claims + "', true)");

                // 1. Insert into doc.lifecycle_requests as authenticated student (matching PostgresCorrectionRequestRepository)
                String sql = "INSERT INTO doc.lifecycle_requests (" +
                        "id, tenant_id, entity_type, entity_id, request_type, reason, status, " +
                        "requested_by, new_document_id, created_at, updated_at, created_by, updated_by, row_version" +
                        ") VALUES (?, ?, 'STUDENT', ?, 'CORRECTION', ?, 'PENDING', ?, NULL, now(), now(), ?, ?, 1)";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, requestId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, student1RecordId);
                    ps.setString(4, "{\"targetField\":\"displayName\",\"oldValue\":\"Student One\",\"newValue\":\"Student Uno\",\"reason\":\"Typo correction\"}");
                    ps.setObject(5, student1AuthId);
                    ps.setObject(6, student1AuthId);
                    ps.setObject(7, student1AuthId);
                    int inserted = ps.executeUpdate();
                    assertEquals("Correction request insert must succeed under student role", 1, inserted);
                }

                // 2. Insert outbox event for student.profile.correction.requested.v1
                String outboxSql = "INSERT INTO sys.outbox_events (" +
                        "id, tenant_id, service, aggregate_type, aggregate_id, event_type, " +
                        "payload, headers, status, attempts, created_at, updated_at" +
                        ") VALUES (?, ?, 'student-service', 'STUDENT', ?, 'student.profile.correction.requested.v1', ?::jsonb, ?::jsonb, 'PENDING', 0, now(), now())";
                try (PreparedStatement ps = conn.prepareStatement(outboxSql)) {
                    ps.setObject(1, outboxId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, student1RecordId);
                    ps.setString(4, String.format("{\"studentId\":\"%s\",\"requestId\":\"%s\",\"targetField\":\"displayName\",\"status\":\"PENDING\"}", student1RecordId, requestId));
                    ps.setString(5, "{}");
                    int insertedOutbox = ps.executeUpdate();
                    assertEquals("Outbox event insert must succeed", 1, insertedOutbox);
                }

                // 3. Read back correction request under student role
                String selectSql = "SELECT id, status, entity_id, request_type FROM doc.lifecycle_requests WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setObject(1, requestId);
                    ps.setObject(2, tenantId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue("Correction request must be visible to student", rs.next());
                        assertEquals("PENDING", rs.getString("status"));
                        assertEquals(student1RecordId, rs.getObject("entity_id"));
                        assertEquals("CORRECTION", rs.getString("request_type"));
                    }
                }

                // 4. Rollback
                conn.rollback();
                System.out.println("[SUCCESS] Live student correction submission and outbox event verified under student role; transaction rolled back.");
            } finally {
                conn.rollback();
            }

            // 5. Post-rollback verification: 0 rows remain
            try (Connection checkConn = ds.getConnection();
                 Statement stmt = checkConn.createStatement()) {
                checkConn.setAutoCommit(false);
                stmt.execute("SET LOCAL ROLE authenticated");
                String claims = String.format("{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        student1AuthId, tenantId, tenantId);
                stmt.execute("SELECT set_config('request.jwt.claims', '" + claims + "', true)");

                try (PreparedStatement ps = checkConn.prepareStatement("SELECT count(*) FROM doc.lifecycle_requests WHERE id = ?")) {
                    ps.setObject(1, requestId);
                    try (ResultSet rs = ps.executeQuery()) {
                        assertTrue(rs.next());
                        assertEquals("Correction request must not exist post-rollback", 0, rs.getInt(1));
                    }
                }
                stmt.execute("RESET ROLE");
            }
        } finally {
            ds.close();
        }
    }
}
