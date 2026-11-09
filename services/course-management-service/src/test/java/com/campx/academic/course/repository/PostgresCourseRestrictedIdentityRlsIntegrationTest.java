package com.campx.academic.course.repository;

import com.campx.academic.course.config.DatabaseConfig;
import com.campx.academic.course.config.DatabaseConnectionManager;
import com.campx.academic.course.config.TestEnvironmentGuard;
import com.campx.academic.course.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.*;
import java.util.*;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeFalse;
import static org.junit.Assume.assumeNotNull;

/**
 * Genuine PostgreSQL Kernel RLS Integration Test Suite for ACD-01 Course Management.
 * <p>
 * Evaluates real PostgreSQL RLS enforcement with non-admin identities holding explicit
 * department-scoped grants and NO wildcard permissions.
 * <p>
 * Guardrails:
 * - Fails safely (skips) when campx.test.env and campx.test.tenant.id are not configured.
 * - Refuses execution on production environments.
 * - Tracks all created fixtures and performs clean teardown via admin context.
 * - Distinguishes RLS failures (42501, 0 rows) from missing fixtures, FK errors (23503),
 *   and privileged execution.
 */
public class PostgresCourseRestrictedIdentityRlsIntegrationTest {

    private DatabaseConnectionManager connectionManager;
    private UserSecurityContext adminContext;
    private UserSecurityContext csContext;
    private UserSecurityContext eeContext;
    private UserSecurityContext crossTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID deptCsId;
    private UUID deptEeId;
    private UUID courseCsId;
    private UUID courseCs2Id;
    private UUID courseEeId;
    private UUID progCsId;
    private UUID progEeId;

    private final List<UUID> createdMapIds = new ArrayList<>();
    private final List<UUID> createdCourseIds = new ArrayList<>();
    private final List<UUID> createdProgIds = new ArrayList<>();
    private final List<UUID> createdDeptIds = new ArrayList<>();
    private final List<UUID> createdRoleAssignmentIds = new ArrayList<>();

    @Before
    public void setUp() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        tenantId = TestEnvironmentGuard.verifySafeStagingTenant(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            // Discover or configure admin user (holding unscoped perm_all / TENANT_ADMIN)
            UUID adminUserId = TestEnvironmentGuard.resolveSafeTestUserId(conn, tenantId);
            adminContext = new UserSecurityContext(adminUserId, tenantId);

            // 3. Provision Departments DEP_CS and DEP_EE under test tenant
            deptCsId = provisionDepartment(conn, "TEST_DEPT_CS_" + randomSuffix(), "CS Test Dept");
            deptEeId = provisionDepartment(conn, "TEST_DEPT_EE_" + randomSuffix(), "EE Test Dept");

            // 4. Provision Programs PROG_CS (in DEP_CS) and PROG_EE (in DEP_EE)
            progCsId = provisionProgram(conn, "PROG_CS_" + randomSuffix(), "CS Degree Program", deptCsId);
            progEeId = provisionProgram(conn, "PROG_EE_" + randomSuffix(), "EE Degree Program", deptEeId);

            // 5. Provision Courses CRS_CS (in DEP_CS) and CRS_EE (in DEP_EE)
            courseCsId = provisionCourse(conn, "CRS_CS_" + randomSuffix(), "CS Intro Course", deptCsId);
            courseCs2Id = provisionCourse(conn, "CRS_CS2_" + randomSuffix(), "CS Advanced Course", deptCsId);
            courseEeId = provisionCourse(conn, "CRS_EE_" + randomSuffix(), "EE Circuits Course", deptEeId);

            // 6. Provision dedicated restricted test identities (NO wildcard grants, strictly department-scoped)
            UUID csUserId = UUID.randomUUID();
            UUID eeUserId = UUID.randomUUID();

            provisionDepartmentRoleAssignment(conn, csUserId, tenantId, deptCsId, "DEPT_HEAD");
            provisionDepartmentRoleAssignment(conn, eeUserId, tenantId, deptEeId, "DEPT_HEAD");

            csContext = new UserSecurityContext(csUserId, tenantId);
            eeContext = new UserSecurityContext(eeUserId, tenantId);

            otherTenantId = UUID.randomUUID();
            crossTenantContext = new UserSecurityContext(UUID.randomUUID(), otherTenantId);
        }
    }

    @After
    public void tearDown() {
        if (connectionManager == null || (createdMapIds.isEmpty() && createdCourseIds.isEmpty()
                && createdProgIds.isEmpty() && createdRoleAssignmentIds.isEmpty() && createdDeptIds.isEmpty())) {
            return;
        }

        try (Connection conn = connectionManager.getConnection()) {
            for (UUID mapId : createdMapIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM acd.course_program_map WHERE id = ?")) {
                    ps.setObject(1, mapId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID courseId : createdCourseIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM acd.courses WHERE id = ?")) {
                    ps.setObject(1, courseId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID progId : createdProgIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM acd.programs WHERE id = ?")) {
                    ps.setObject(1, progId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID raId : createdRoleAssignmentIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM iam.role_assignments WHERE id = ?")) {
                    ps.setObject(1, raId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            for (UUID deptId : createdDeptIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.departments WHERE id = ?")) {
                    ps.setObject(1, deptId);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        } catch (Exception ignored) {}

        createdMapIds.clear();
        createdCourseIds.clear();
        createdProgIds.clear();
        createdDeptIds.clear();
        createdRoleAssignmentIds.clear();
    }

    // -------------------------------------------------------------------------
    // Category A: INSERT Scenarios
    // -------------------------------------------------------------------------

    @Test
    public void testST01_AuthorizedInsertBothProgramAndCourseInScope() {
        // CS User mapping CS Course into CS Program: Both within caller's department scope
        UUID mapId = UUID.randomUUID();
        createdMapIds.add(mapId);

        int rows = csContext.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) " +
                    "VALUES (?, ?, ?, ?, now())";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                ps.setObject(2, tenantId);
                ps.setObject(3, courseCsId);
                ps.setObject(4, progCsId);
                return ps.executeUpdate();
            }
        });

        assertEquals("Authorized insert with both entities in scope must succeed", 1, rows);

        // Independent verification via admin context
        boolean exists = adminContext.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT 1 FROM acd.course_program_map WHERE id = ? AND tenant_id = ?")) {
                ps.setObject(1, mapId);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
        assertTrue("Fixture must exist in database after authorized insert", exists);
    }

    @Test
    public void testST02_DeniedInsertOnlyProgramInScope() {
        // CS User attempting to map EE Course into CS Program (Course is foreign/EE)
        // Pre-proof: PROG_CS and CRS_EE exist in DB (eliminates FK error 23503)
        assertFixtureExists("acd.programs", progCsId);
        assertFixtureExists("acd.courses", courseEeId);

        UUID mapId = UUID.randomUUID();
        createdMapIds.add(mapId);

        try {
            csContext.executeInTransaction(connectionManager, conn -> {
                String sql = "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) " +
                        "VALUES (?, ?, ?, ?, now())";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, mapId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, courseEeId);
                    ps.setObject(4, progCsId);
                    return ps.executeUpdate();
                }
            });
            fail("Expected PostgreSQL RLS policy violation (42501)");
        } catch (Exception ex) {
            assertRlsViolation(ex);
        }

        // Post-verification: Ensure row was not inserted
        assertFixtureNotExists("acd.course_program_map", mapId);
    }

    @Test
    public void testST03_DeniedInsertOnlyCourseInScope() {
        // CS User attempting to map CS Course into EE Program (Program is foreign/EE)
        assertFixtureExists("acd.programs", progEeId);
        assertFixtureExists("acd.courses", courseCsId);

        UUID mapId = UUID.randomUUID();
        createdMapIds.add(mapId);

        try {
            csContext.executeInTransaction(connectionManager, conn -> {
                String sql = "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) " +
                        "VALUES (?, ?, ?, ?, now())";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, mapId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, courseCsId);
                    ps.setObject(4, progEeId);
                    return ps.executeUpdate();
                }
            });
            fail("Expected PostgreSQL RLS policy violation (42501)");
        } catch (Exception ex) {
            assertRlsViolation(ex);
        }

        assertFixtureNotExists("acd.course_program_map", mapId);
    }

    @Test
    public void testST04_DeniedInsertNeitherInScope() {
        // CS User attempting to map EE Course into EE Program
        assertFixtureExists("acd.programs", progEeId);
        assertFixtureExists("acd.courses", courseEeId);

        UUID mapId = UUID.randomUUID();
        createdMapIds.add(mapId);

        try {
            csContext.executeInTransaction(connectionManager, conn -> {
                String sql = "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) " +
                        "VALUES (?, ?, ?, ?, now())";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, mapId);
                    ps.setObject(2, tenantId);
                    ps.setObject(3, courseEeId);
                    ps.setObject(4, progEeId);
                    return ps.executeUpdate();
                }
            });
            fail("Expected PostgreSQL RLS policy violation (42501)");
        } catch (Exception ex) {
            assertRlsViolation(ex);
        }

        assertFixtureNotExists("acd.course_program_map", mapId);
    }

    // -------------------------------------------------------------------------
    // Category B: UPDATE Scenarios
    // -------------------------------------------------------------------------

    @Test
    public void testST05_AuthorizedUpdateWithinScope() {
        // Setup initial mapping via admin context
        UUID mapId = insertMappingPrivileged(courseCsId, progCsId);

        int rows = csContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.course_program_map SET course_id = ? WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, courseCs2Id);
                ps.setObject(2, mapId);
                ps.setObject(3, tenantId);
                return ps.executeUpdate();
            }
        });

        assertEquals("Authorized update within scope must update 1 row", 1, rows);

        // Verify update persisted
        UUID updatedCourse = adminContext.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT course_id FROM acd.course_program_map WHERE id = ?")) {
                ps.setObject(1, mapId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    return (UUID) rs.getObject("course_id");
                }
            }
        });
        assertEquals("Course ID should be updated to CRS_CS2", courseCs2Id, updatedCourse);
    }

    @Test
    public void testST06_DeniedUpdateVisibleRowToOutsideScope() {
        // Initial mapping: CS Course in CS Program (Visible to CS User)
        UUID mapId = insertMappingPrivileged(courseCsId, progCsId);

        // CS User attempts to re-link mapping to EE Program (violates WITH CHECK)
        try {
            csContext.executeInTransaction(connectionManager, conn -> {
                String sql = "UPDATE acd.course_program_map SET program_id = ? WHERE id = ? AND tenant_id = ?";
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setObject(1, progEeId);
                    ps.setObject(2, mapId);
                    ps.setObject(3, tenantId);
                    return ps.executeUpdate();
                }
            });
            fail("Expected PostgreSQL RLS policy violation (42501) on WITH CHECK");
        } catch (Exception ex) {
            assertRlsViolation(ex);
        }

        // Post-verification: Mapping remained unchanged
        UUID currentProg = adminContext.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT program_id FROM acd.course_program_map WHERE id = ?")) {
                ps.setObject(1, mapId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    return (UUID) rs.getObject("program_id");
                }
            }
        });
        assertEquals("Program ID must remain PROG_CS", progCsId, currentProg);
    }

    @Test
    public void testST07_UpdateInvisibleRowReturnsZeroRows() {
        // Mapping exists for EE Course in EE Program (Invisible to CS User under USING)
        UUID mapId = insertMappingPrivileged(courseEeId, progEeId);

        // CS User attempts to update EE mapping
        int rows = csContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.course_program_map SET course_id = ? WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, courseCsId);
                ps.setObject(2, mapId);
                ps.setObject(3, tenantId);
                return ps.executeUpdate();
            }
        });

        assertEquals("Updating an invisible row must return 0 rows affected (silent filtering)", 0, rows);

        // Privileged proof that row existed the entire time and remained untouched
        assertFixtureExists("acd.course_program_map", mapId);
    }

    // -------------------------------------------------------------------------
    // Category C: DELETE Scenarios
    // -------------------------------------------------------------------------

    @Test
    public void testST08_AuthorizedDeleteWithinScope() {
        UUID mapId = insertMappingPrivileged(courseCsId, progCsId);

        int rows = csContext.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM acd.course_program_map WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                ps.setObject(2, tenantId);
                return ps.executeUpdate();
            }
        });

        assertEquals("Authorized delete within scope must delete 1 row", 1, rows);
        assertFixtureNotExists("acd.course_program_map", mapId);
    }

    @Test
    public void testST09_DeleteInvisibleCrossDepartmentMappingReturnsZeroRows() {
        // Cross-department mapping created by admin: EE Program + CS Course
        UUID mapId = insertMappingPrivileged(courseCsId, progEeId);

        // CS User has scope on Course CS, but NOT Program EE.
        // Under AND policy, delete requires scope on BOTH. Row is invisible under USING.
        int rows = csContext.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM acd.course_program_map WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                ps.setObject(2, tenantId);
                return ps.executeUpdate();
            }
        });

        assertEquals("Deleting a cross-department mapping without dual authority must affect 0 rows", 0, rows);

        // Privileged post-verification: row was NOT deleted
        assertFixtureExists("acd.course_program_map", mapId);
    }

    // -------------------------------------------------------------------------
    // Category D: SELECT Visibility & Admin Bypass
    // -------------------------------------------------------------------------

    @Test
    public void testST10_SelectVisibilityUnderPreservedOrPolicy() {
        // 1. Same-dept mapping: (PROG_CS, CRS_CS)
        UUID map1 = insertMappingPrivileged(courseCsId, progCsId);
        // 2. Cross-dept mapping: (PROG_EE, CRS_CS) - Course in CS, Program in EE
        UUID map2 = insertMappingPrivileged(courseCsId, progEeId);

        // CS User SELECT query: Since SELECT retains OR, CS User must see BOTH mappings
        // because courseCsId is within their CS department scope.
        int count = csContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT COUNT(*) FROM acd.course_program_map WHERE id IN (?, ?) AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, map1);
                ps.setObject(2, map2);
                ps.setObject(3, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    return rs.getInt(1);
                }
            }
        });

        assertEquals("SELECT must preserve OR semantics and see both mappings", 2, count);
    }

    @Test
    public void testST11_CrossTenantIsolation() {
        UUID mapId = insertMappingPrivileged(courseCsId, progCsId);

        // 1. SELECT from other tenant returns 0 rows
        int count = crossTenantContext.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT COUNT(*) FROM acd.course_program_map WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue(rs.next());
                    return rs.getInt(1);
                }
            }
        });
        assertEquals("Cross-tenant SELECT must return 0 rows", 0, count);

        // 2. UPDATE from other tenant affects 0 rows
        int updateRows = crossTenantContext.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE acd.course_program_map SET course_id = ? WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, courseCs2Id);
                ps.setObject(2, mapId);
                return ps.executeUpdate();
            }
        });
        assertEquals("Cross-tenant UPDATE must affect 0 rows", 0, updateRows);

        // 3. DELETE from other tenant affects 0 rows
        int deleteRows = crossTenantContext.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM acd.course_program_map WHERE id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                return ps.executeUpdate();
            }
        });
        assertEquals("Cross-tenant DELETE must affect 0 rows", 0, deleteRows);

        // Mapping in tenant A remains untouched
        assertFixtureExists("acd.course_program_map", mapId);
    }

    @Test
    public void testST12_TenantAdminCanManageCrossDepartmentMapping() {
        // Admin user (holding unscoped perm_all) can link cross-department entities
        UUID mapId = UUID.randomUUID();
        createdMapIds.add(mapId);

        int insertRows = adminContext.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) " +
                    "VALUES (?, ?, ?, ?, now())";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                ps.setObject(2, tenantId);
                ps.setObject(3, courseCsId);
                ps.setObject(4, progEeId);
                return ps.executeUpdate();
            }
        });
        assertEquals("Tenant Admin with perm_all must be authorized to link cross-department entities", 1, insertRows);

        // Admin can also delete cross-department mapping
        int deleteRows = adminContext.executeInTransaction(connectionManager, conn -> {
            String sql = "DELETE FROM acd.course_program_map WHERE id = ? AND tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                ps.setObject(2, tenantId);
                return ps.executeUpdate();
            }
        });
        assertEquals("Tenant Admin must be authorized to delete cross-department mapping", 1, deleteRows);
    }

    // -------------------------------------------------------------------------
    // Helper Methods
    // -------------------------------------------------------------------------

    private UUID insertMappingPrivileged(UUID courseId, UUID programId) {
        UUID mapId = UUID.randomUUID();
        createdMapIds.add(mapId);
        adminContext.executeInTransaction(connectionManager, conn -> {
            String sql = "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) " +
                    "VALUES (?, ?, ?, ?, now())";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, mapId);
                ps.setObject(2, tenantId);
                ps.setObject(3, courseId);
                ps.setObject(4, programId);
                ps.executeUpdate();
            }
            return null;
        });
        return mapId;
    }

    private void assertFixtureExists(String table, UUID id) {
        boolean exists = adminContext.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
        assertTrue("Fixture " + id + " must exist in " + table, exists);
    }

    private void assertFixtureNotExists(String table, UUID id) {
        boolean exists = adminContext.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement("SELECT 1 FROM " + table + " WHERE id = ?")) {
                ps.setObject(1, id);
                try (ResultSet rs = ps.executeQuery()) {
                    return rs.next();
                }
            }
        });
        assertFalse("Fixture " + id + " must NOT exist in " + table, exists);
    }

    private void assertRlsViolation(Exception ex) {
        Throwable cause = ex;
        boolean foundRls = false;
        while (cause != null) {
            if (cause instanceof SQLException) {
                SQLException sqle = (SQLException) cause;
                if ("42501".equals(sqle.getSQLState())) {
                    foundRls = true;
                    break;
                }
            }
            cause = cause.getCause();
        }
        assertTrue("Exception must be caused by PostgreSQL SQLSTATE 42501 (RLS violation). Was: " + ex.getMessage(), foundRls);
    }

    private UUID provisionDepartment(Connection conn, String code, String name) throws SQLException {
        UUID id = UUID.randomUUID();
        createdDeptIds.add(id);
        String sql = "INSERT INTO core.departments (id, tenant_id, code, name, status, created_at, updated_at, row_version) " +
                "VALUES (?, ?, ?, ?, 'ACTIVE', now(), now(), 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setString(3, code);
            ps.setString(4, name);
            ps.executeUpdate();
        }
        return id;
    }

    private UUID provisionProgram(Connection conn, String code, String name, UUID deptId) throws SQLException {
        UUID id = UUID.randomUUID();
        createdProgIds.add(id);
        String sql = "INSERT INTO acd.programs (id, tenant_id, department_id, code, name, duration_years, status, created_at, updated_at, row_version) " +
                "VALUES (?, ?, ?, ?, ?, 4, 'ACTIVE', now(), now(), 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, deptId);
            ps.setString(4, code);
            ps.setString(5, name);
            ps.executeUpdate();
        }
        return id;
    }

    private UUID provisionCourse(Connection conn, String code, String name, UUID deptId) throws SQLException {
        UUID id = UUID.randomUUID();
        createdCourseIds.add(id);
        String sql = "INSERT INTO acd.courses (id, tenant_id, department_id, code, name, level, credits, status, created_at, updated_at, row_version) " +
                "VALUES (?, ?, ?, ?, ?, '100', 3.0, 'DRAFT', now(), now(), 1)";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, deptId);
            ps.setString(4, code);
            ps.setString(5, name);
            ps.executeUpdate();
        }
        return id;
    }

    private void provisionDepartmentRoleAssignment(Connection conn, UUID userId, UUID tenantId, UUID deptId, String roleCode) throws SQLException {
        UUID id = UUID.randomUUID();
        createdRoleAssignmentIds.add(id);
        String sql = "INSERT INTO iam.role_assignments (id, tenant_id, user_id, role_code, department_id, created_at, updated_at) " +
                "VALUES (?, ?, ?, ?, ?, now(), now())";
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, id);
            ps.setObject(2, tenantId);
            ps.setObject(3, userId);
            ps.setString(4, roleCode);
            ps.setObject(5, deptId);
            ps.executeUpdate();
        }
    }

    private String randomSuffix() {
        return UUID.randomUUID().toString().replace("-", "").substring(0, 6).toUpperCase();
    }
}
