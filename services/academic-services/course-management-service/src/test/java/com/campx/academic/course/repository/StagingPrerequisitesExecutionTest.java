package com.campx.academic.course.repository;

import com.campx.academic.course.config.DatabaseConfig;
import com.campx.academic.course.config.DatabaseConnectionManager;
import com.campx.academic.course.config.TestEnvironmentGuard;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.File;
import java.nio.file.Files;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Executes the owner-approved staging database prerequisites:
 * 1. GO PARTITION-RLS-FIX: V20261014_01__enable_rls_on_monthly_partition_children.sql
 * 2. GO PROVISION-TEST-TENANT: V20261014_02__provision_test_tenant_staging.sql
 *
 * Strict Guardrails:
 * - Validates target is non-production staging ref 'kpwdvvcschankgggitnj'.
 * - Forbids production ref 'ekspahbsbwdpraaqfqui'.
 * - Verifies postconditions directly via read-only SQL queries.
 */
public class StagingPrerequisitesExecutionTest {

    private DatabaseConnectionManager connectionManager;
    private DatabaseConfig config;

    @Before
    public void setUp() {
        config = DatabaseConfig.load();
        TestEnvironmentGuard.verifySafeStagingEnvironment(config);
        connectionManager = new DatabaseConnectionManager(config);
        connectionManager.initialize();
    }

    @After
    public void tearDown() {
        if (connectionManager != null) {
            connectionManager.close();
        }
    }

    @Test
    public void test01_executePartitionRlsHardening() throws Exception {
        File migrationFile = new File("../../database/migrations/V20261014_01__enable_rls_on_monthly_partition_children.sql");
        if (!migrationFile.exists()) {
            migrationFile = new File("database/migrations/V20261014_01__enable_rls_on_monthly_partition_children.sql");
        }
        assertTrue("Migration file V20261014_01 must exist: " + migrationFile.getAbsolutePath(), migrationFile.exists());

        String sql = new String(Files.readAllBytes(migrationFile.toPath()), "UTF-8");

        try (Connection conn = connectionManager.getConnection()) {
            // Execute DO block
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            // Postcondition verification: All 24 tables must have relrowsecurity = true
            String verifyRlsSql = 
                "SELECT count(*) FROM pg_class c " +
                "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                "WHERE n.nspname IN ('audit', 'cam', 'hrm', 'lms') " +
                "  AND c.relname LIKE '%_p20%' " +
                "  AND c.relrowsecurity = true";

            try (PreparedStatement ps = conn.prepareStatement(verifyRlsSql);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                int countWithRls = rs.getInt(1);
                assertEquals("All 24 monthly partition child tables must have RLS enabled", 24, countWithRls);
            }

            // Postcondition verification: Direct grants to anon/authenticated revoked
            String verifyGrantsSql = 
                "SELECT count(*) FROM information_schema.role_table_grants " +
                "WHERE table_schema IN ('audit', 'cam', 'hrm', 'lms') " +
                "  AND table_name LIKE '%_p20%' " +
                "  AND grantee IN ('anon', 'authenticated')";

            try (PreparedStatement ps = conn.prepareStatement(verifyGrantsSql);
                 ResultSet rs = ps.executeQuery()) {
                assertTrue(rs.next());
                int directGrantCount = rs.getInt(1);
                assertEquals("Direct grants to anon and authenticated must be 0", 0, directGrantCount);
            }
        }
    }

    @Test
    public void test02_executeProvisionTestTenant() throws Exception {
        File migrationFile = new File("../../database/migrations/V20261014_02__provision_test_tenant_staging.sql");
        if (!migrationFile.exists()) {
            migrationFile = new File("database/migrations/V20261014_02__provision_test_tenant_staging.sql");
        }
        assertTrue("Migration file V20261014_02 must exist: " + migrationFile.getAbsolutePath(), migrationFile.exists());

        String sql = new String(Files.readAllBytes(migrationFile.toPath()), "UTF-8");

        try (Connection conn = connectionManager.getConnection()) {
            // Execute tenant provisioning DO block
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            UUID testTenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");

            // Postcondition verification: Tenant exists and is ACTIVE
            String verifyTenantSql = "SELECT code, name, status FROM core.tenants WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(verifyTenantSql)) {
                ps.setObject(1, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Tenant 11111111-1111-1111-1111-111111111111 must exist", rs.next());
                    assertEquals("CAMPX_STAGING", rs.getString("code"));
                    assertEquals("ACTIVE", rs.getString("status"));
                }
            }

            // Postcondition verification: 30 system roles seeded
            String verifyRolesSql = "SELECT count(*) FROM iam.roles WHERE tenant_id = ? AND is_system = true AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(verifyRolesSql)) {
                ps.setObject(1, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int roleCount = rs.getInt(1);
                    assertEquals("Expected 30 seeded system roles for tenant", 30, roleCount);
                }
            }

            // Postcondition verification: 2,138 role-permission mappings seeded
            String verifyPermsSql = "SELECT count(*) FROM iam.role_permissions WHERE tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(verifyPermsSql)) {
                ps.setObject(1, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    int permCount = rs.getInt(1);
                    assertEquals("Expected 2,138 seeded role-permission bindings for tenant", 2138, permCount);
                }
            }
        }
    }

    @Test
    public void test03_provisionTestCollegeAdminIdentity() throws Exception {
        UUID testTenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
        UUID testUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        UUID roleAssignmentId = UUID.fromString("22222222-2222-2222-2222-222222222222");

        try (Connection conn = connectionManager.getConnection()) {
            // 1. Resolve COLLEGE_ADMIN role_id for this tenant
            UUID collegeAdminRoleId = null;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM iam.roles WHERE tenant_id = ? AND code = 'COLLEGE_ADMIN' AND deleted_at IS NULL")) {
                ps.setObject(1, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("COLLEGE_ADMIN role must exist for tenant", rs.next());
                    collegeAdminRoleId = (UUID) rs.getObject("id");
                }
            }

            // 2. Insert test user profile if not present
            String insertProfileSql =
                    "INSERT INTO iam.user_profiles (id, tenant_id, email, full_name, status, created_at, updated_at, row_version) " +
                    "VALUES (?, ?, 'admin.staging@campxsync.edu', 'Staging College Admin', 'ACTIVE', now(), now(), 1) " +
                    "ON CONFLICT (id) DO NOTHING";
            try (PreparedStatement ps = conn.prepareStatement(insertProfileSql)) {
                ps.setObject(1, testUserId);
                ps.setObject(2, testTenantId);
                ps.executeUpdate();
            }

            // 3. Insert role assignment if not present
            String insertAssignmentSql =
                    "INSERT INTO iam.role_assignments (id, tenant_id, user_id, role_id, created_at, updated_at, row_version) " +
                    "VALUES (?, ?, ?, ?, now(), now(), 1) " +
                    "ON CONFLICT (id) DO NOTHING";
            try (PreparedStatement ps = conn.prepareStatement(insertAssignmentSql)) {
                ps.setObject(1, roleAssignmentId);
                ps.setObject(2, testTenantId);
                ps.setObject(3, testUserId);
                ps.setObject(4, collegeAdminRoleId);
                ps.executeUpdate();
            }

            // 4. Verify postcondition: user profile active
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status FROM iam.user_profiles WHERE id = ? AND tenant_id = ?")) {
                ps.setObject(1, testUserId);
                ps.setObject(2, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("User profile must exist for test identity", rs.next());
                    assertEquals("ACTIVE", rs.getString("status"));
                }
            }

            // 5. Verify postcondition: role assignment exists
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT role_id FROM iam.role_assignments WHERE id = ? AND tenant_id = ?")) {
                ps.setObject(1, roleAssignmentId);
                ps.setObject(2, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Role assignment must exist for test identity", rs.next());
                    assertEquals(collegeAdminRoleId, rs.getObject("role_id"));
                }
            }
        }
    }

    @Test
    public void test04_provisionStagingAuthUserIdentity() throws Exception {
        File migrationFile = new File("../../database/migrations/V20261014_03__provision_staging_college_admin_identity.sql");
        if (!migrationFile.exists()) {
            migrationFile = new File("database/migrations/V20261014_03__provision_staging_college_admin_identity.sql");
        }
        assertTrue("Migration file V20261014_03 must exist: " + migrationFile.getAbsolutePath(), migrationFile.exists());

        String sql = new String(Files.readAllBytes(migrationFile.toPath()), "UTF-8");

        try (Connection conn = connectionManager.getConnection()) {
            // Execute DO block
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            UUID testTenantId = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID stagingAuthUserId = UUID.fromString("70a8ffae-b5ad-4c61-a401-16f220893df4");

            // Postcondition verification: User profile active in iam.user_profiles
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT status, email, full_name FROM iam.user_profiles WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, stagingAuthUserId);
                ps.setObject(2, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("User profile must exist for staging auth user", rs.next());
                    assertEquals("ACTIVE", rs.getString("status"));
                    assertEquals("campxsync@gmail.com", rs.getString("email"));
                }
            }

            // Postcondition verification: COLLEGE_ADMIN role assigned in iam.role_assignments
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT r.code FROM iam.role_assignments ra " +
                    "JOIN iam.roles r ON ra.role_id = r.id " +
                    "WHERE ra.user_id = ? AND ra.tenant_id = ? AND ra.deleted_at IS NULL")) {
                ps.setObject(1, stagingAuthUserId);
                ps.setObject(2, testTenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("COLLEGE_ADMIN role assignment must exist for staging auth user", rs.next());
                    assertEquals("COLLEGE_ADMIN", rs.getString("code"));
                }
            }
        }
    }

    @Test
    public void test05_executeDualTenantStagingSeed() throws Exception {
        File seedFile = new File("../../database/seed-data/staging_seed_college_a_and_b.sql");
        if (!seedFile.exists()) {
            seedFile = new File("database/seed-data/staging_seed_college_a_and_b.sql");
        }
        assertTrue("Seed file must exist: " + seedFile.getAbsolutePath(), seedFile.exists());

        String sql = new String(Files.readAllBytes(seedFile.toPath()), "UTF-8");

        try (Connection conn = connectionManager.getConnection()) {
            // 1. Execute DO block
            try (Statement stmt = conn.createStatement()) {
                stmt.execute(sql);
            }

            // 2. Postcondition Assertions: College records
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM core.colleges WHERE code IN ('DIT', 'TCS')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 2 colleges", 2, rs.getInt(1));
                }
            }

            // 3. Departments
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM core.departments WHERE code IN ('CSE', 'ECE', 'PCS')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 3 departments", 3, rs.getInt(1));
                }
            }

            // 4. Programs
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM acd.programs WHERE code IN ('BTECH_CSE', 'BTECH_ECE', 'BSC_PHY')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 3 programs", 3, rs.getInt(1));
                }
            }

            // 5. Batches
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM acd.batches WHERE code IN ('CSE_2024_A', 'CSE_2024_B', 'ECE_2024_A', 'PCS_2024')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 4 batches", 4, rs.getInt(1));
                }
            }

            // 6. Subjects
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM acd.subjects WHERE code IN ('CS301', 'CS302', 'CS303', 'EC301', 'PH101')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 5 subjects", 5, rs.getInt(1));
                }
            }

            // 7. Faculty Employees
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM hrm.employees WHERE employee_no IN ('FAC-CSE-001', 'FAC-CSE-002', 'FAC-ECE-001', 'FAC-PCS-001')")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 4 faculty employees", 4, rs.getInt(1));
                }
            }

            // 8. Students: 40 in College A, 10 in College B
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM stu.students WHERE enrollment_no LIKE '2024-CSE-%'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 40 students in College A", 40, rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM stu.students WHERE enrollment_no LIKE '2024-PCS-%'")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 10 students in College B", 10, rs.getInt(1));
                }
            }

            // 9. Timetable entries: 2 in College A, 1 in College B
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM acd.timetable_entries WHERE id IN (" +
                    "'11111111-7700-0000-0000-000000000003'::uuid, '11111111-7700-0000-0000-000000000004'::uuid, '22222222-7700-0000-0000-000000000003'::uuid)")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 3 timetable entries across both colleges", 3, rs.getInt(1));
                }
            }

            // 10. Attendance: 2 sessions, 20 attendance records
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM acd.attendance_sessions WHERE id IN (" +
                    "'11111111-5e55-0000-0000-000000000001'::uuid, '11111111-5e55-0000-0000-000000000002'::uuid)")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 2 attendance sessions", 2, rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM acd.attendance_records WHERE session_id = '11111111-5e55-0000-0000-000000000001'::uuid")) {
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals("Expected 20 attendance records for closed session", 20, rs.getInt(1));
                }
            }

            // 11. Cross-Tenant Isolation: Tenant A (CAMPX_STAGING) vs Tenant B (TEST_COLL_B)
            UUID tenantA = UUID.fromString("11111111-1111-1111-1111-111111111111");
            UUID tenantB = UUID.fromString("22222222-2222-2222-2222-222222222222");

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM stu.students WHERE tenant_id = ?")) {
                ps.setObject(1, tenantA);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(40, rs.getInt(1));
                }
            }
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT count(*) FROM stu.students WHERE tenant_id = ?")) {
                ps.setObject(1, tenantB);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    assertEquals(10, rs.getInt(1));
                }
            }
        }
    }
}

