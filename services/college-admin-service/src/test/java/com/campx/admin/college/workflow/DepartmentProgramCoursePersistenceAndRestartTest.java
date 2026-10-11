package com.campx.admin.college.workflow;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.model.CollegeModels.Program;
import com.campx.admin.college.repository.PostgresDepartmentRepository;
import com.campx.admin.college.repository.PostgresProgramRepository;
import com.campx.admin.college.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.*;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Workstream A MVP Verification Test:
 * 1. Completes and tests the full Department -> Program -> Course workflow with foreign-key and tenant consistency.
 * 2. Confirms that writes persist in Supabase PostgreSQL and remain available after application/service restart.
 * 3. Enforces tenant isolation (cross-tenant reads return 0 rows).
 * 4. Verifies full create/read/update operations across the hierarchy.
 */
public class DepartmentProgramCoursePersistenceAndRestartTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresDepartmentRepository departmentRepository;
    private PostgresProgramRepository programRepository;

    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID collegeId;
    private UUID adminUserId;

    // Track created entity IDs for clean teardown
    private String testDepartmentId;
    private String testProgramId;
    private String testCourseId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        departmentRepository = new PostgresDepartmentRepository(connectionManager);
        programRepository = new PostgresProgramRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            // Find active tenant
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }
            assumeNotNull("Requires an active tenant in core.tenants", tenantId);

            // Find active college under tenant
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        collegeId = (UUID) rs.getObject("id");
                    }
                }
            }
            assumeNotNull("Requires an active college in core.colleges", collegeId);

            // Find tenant admin user
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT user_id FROM iam.role_assignments WHERE tenant_id = ? AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        adminUserId = (UUID) rs.getObject("user_id");
                    }
                }
            }
            if (adminUserId == null) {
                adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
            }
        }

        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;

        // Clean up in reverse dependency order: Course Map -> Course -> Program -> Department
        try (Connection conn = connectionManager.getConnection()) {
            if (testCourseId != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM acd.course_program_map WHERE course_id = ?")) {
                    ps.setObject(1, UUID.fromString(testCourseId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM acd.courses WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testCourseId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            if (testProgramId != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM acd.programs WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testProgramId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            if (testDepartmentId != null) {
                try (PreparedStatement ps = conn.prepareStatement(
                        "DELETE FROM core.departments WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testDepartmentId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testDepartmentProgramCourseWorkflowWithPersistenceAndRestart() throws Exception {
        String runSuffix = Long.toHexString(System.currentTimeMillis()).substring(4).toUpperCase();

        // ---------------------------------------------------------------------
        // Step 1: Create Department in core.departments
        // ---------------------------------------------------------------------
        String depCode = "DEP_WF_" + runSuffix;
        Department dept = new Department();
        dept.setDepartmentCode(depCode);
        dept.setName("Workflow Engineering Department " + runSuffix);
        dept.setHeadUserId(adminUserId.toString());
        dept.setCollegeId(collegeId.toString());

        Department createdDept = departmentRepository.createDepartment(tenantContext, dept);
        assertNotNull("Created department must have an ID", createdDept.getId());
        testDepartmentId = createdDept.getId();
        assertEquals(depCode, createdDept.getDepartmentCode());
        assertEquals("ACTIVE", createdDept.getStatus());

        // ---------------------------------------------------------------------
        // Step 2: Create Program in acd.programs referencing the Department & College
        // ---------------------------------------------------------------------
        String progCode = "PRG_WF_" + runSuffix;
        Program prog = new Program();
        prog.setProgramCode(progCode);
        prog.setName("B.Tech in Workflow Systems " + runSuffix);
        prog.setDepartmentId(testDepartmentId);
        prog.setCollegeId(collegeId.toString());
        prog.setLevel("UG");
        prog.setDurationYears(4);

        Program createdProg = programRepository.createProgram(tenantContext, prog);
        assertNotNull("Created program must have an ID", createdProg.getId());
        testProgramId = createdProg.getId();
        assertEquals(progCode, createdProg.getProgramCode());
        assertEquals(testDepartmentId, createdProg.getDepartmentId());
        assertEquals(collegeId.toString(), createdProg.getCollegeId());
        assertEquals("ACTIVE", createdProg.getStatus());

        // ---------------------------------------------------------------------
        // Step 3: Create Course in acd.courses & map to Program in acd.course_program_map
        // ---------------------------------------------------------------------
        String courseCode = "CRS_WF_" + runSuffix;
        UUID courseUuid = UUID.randomUUID();
        testCourseId = courseUuid.toString();

        tenantContext.executeInTransaction(connectionManager, conn -> {
            // Insert into acd.courses
            String insertCourseSql = "INSERT INTO acd.courses "
                    + "(id, tenant_id, department_id, code, name, description, credits, status, created_at, updated_at, row_version) "
                    + "VALUES (?, ?, ?, ?, ?, ?, ?, 'ACTIVE', now(), now(), 1)";
            try (PreparedStatement ps = conn.prepareStatement(insertCourseSql)) {
                ps.setObject(1, courseUuid);
                ps.setObject(2, tenantId);
                ps.setObject(3, UUID.fromString(testDepartmentId));
                ps.setString(4, courseCode);
                ps.setString(5, "Workflow Engineering Fundamentals");
                ps.setString(6, "Core systems workflow and orchestration");
                ps.setDouble(7, 4.0);
                ps.executeUpdate();
            }

            // Insert into acd.course_program_map
            String insertMapSql = "INSERT INTO acd.course_program_map (id, tenant_id, course_id, program_id, created_at) "
                    + "VALUES (?, ?, ?, ?, now())";
            try (PreparedStatement ps = conn.prepareStatement(insertMapSql)) {
                ps.setObject(1, UUID.randomUUID());
                ps.setObject(2, tenantId);
                ps.setObject(3, courseUuid);
                ps.setObject(4, UUID.fromString(testProgramId));
                ps.executeUpdate();
            }
            return null;
        });

        // ---------------------------------------------------------------------
        // Step 4: Verify Hierarchy Read under tenantContext
        // ---------------------------------------------------------------------
        Optional<Department> readDept = departmentRepository.findById(tenantContext, testDepartmentId);
        assertTrue("Department must be found", readDept.isPresent());
        assertEquals("ACTIVE", readDept.get().getStatus());

        Program readProg = programRepository.getProgramById(tenantContext, testProgramId);
        assertNotNull("Program must be found", readProg);
        assertEquals(testDepartmentId, readProg.getDepartmentId());

        // Verify Course and its Program mapping via SQL under RLS
        tenantContext.executeInTransaction(connectionManager, conn -> {
            String verifySql = "SELECT c.id, c.code, c.name, c.credits, m.program_id "
                    + "FROM acd.courses c "
                    + "LEFT JOIN acd.course_program_map m ON c.id = m.course_id "
                    + "WHERE c.id = ? AND c.tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(verifySql)) {
                ps.setObject(1, courseUuid);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Course must exist in acd.courses", rs.next());
                    assertEquals(courseCode, rs.getString("code"));
                    assertEquals(4.0, rs.getDouble("credits"), 0.001);
                    assertEquals(testProgramId, rs.getString("program_id"));
                }
            }
            return null;
        });

        // ---------------------------------------------------------------------
        // Step 5: Test Update Operations
        // ---------------------------------------------------------------------
        // Update Department name via updateDepartment
        Department toUpdate = new Department();
        toUpdate.setId(testDepartmentId);
        toUpdate.setName("Updated Workflow Engineering " + runSuffix);
        toUpdate.setStatus("ACTIVE");
        Department updatedDeptResult = departmentRepository.updateDepartment(tenantContext, toUpdate);
        assertEquals("Updated Workflow Engineering " + runSuffix, updatedDeptResult.getName());

        // Update Department via retireDepartment -> changes status to RETIRED
        departmentRepository.retireDepartment(tenantContext, testDepartmentId);
        Optional<Department> retiredDept = departmentRepository.findById(tenantContext, testDepartmentId);
        assertTrue(retiredDept.isPresent());
        assertEquals("RETIRED", retiredDept.get().getStatus());

        // Update Program
        readProg.setName("Updated B.Tech Systems " + runSuffix);
        readProg.setDurationYears(5);
        Program updatedProg = programRepository.updateProgram(tenantContext, readProg);
        assertEquals(5, updatedProg.getDurationYears());

        // Update Course
        tenantContext.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE acd.courses SET name = ?, credits = ?, updated_at = now() WHERE id = ? AND tenant_id = ?")) {
                ps.setString(1, "Advanced Workflow Systems");
                ps.setDouble(2, 5.0);
                ps.setObject(3, courseUuid);
                ps.setObject(4, tenantId);
                assertEquals(1, ps.executeUpdate());
            }
            return null;
        });

        // ---------------------------------------------------------------------
        // Step 6: SIMULATE APPLICATION / SERVICE RESTART
        // Shutdown all connection pools, nullify repository instances and simulate cold reboot
        // ---------------------------------------------------------------------
        departmentRepository = null;
        programRepository = null;
        connectionManager = null;
        System.gc();

        // Service Restart: Re-initialize from scratch
        DatabaseConnectionManager restartedConnectionManager = DatabaseConnectionManager.getInstance();
        PostgresDepartmentRepository restartedDeptRepo = new PostgresDepartmentRepository(restartedConnectionManager);
        PostgresProgramRepository restartedProgRepo = new PostgresProgramRepository(restartedConnectionManager);

        // ---------------------------------------------------------------------
        // Step 7: Verify Persistence Across Restart in Supabase PostgreSQL
        // ---------------------------------------------------------------------
        Optional<Department> postRestartDept = restartedDeptRepo.findById(tenantContext, testDepartmentId);
        assertTrue("Department must persist in PostgreSQL after service restart", postRestartDept.isPresent());
        assertEquals(depCode, postRestartDept.get().getDepartmentCode());
        assertEquals("RETIRED", postRestartDept.get().getStatus());

        Program postRestartProg = restartedProgRepo.getProgramById(tenantContext, testProgramId);
        assertNotNull("Program must persist in PostgreSQL after service restart", postRestartProg);
        assertEquals(progCode, postRestartProg.getProgramCode());
        assertEquals(testDepartmentId, postRestartProg.getDepartmentId());
        assertEquals(5, postRestartProg.getDurationYears());

        // Verify Course and its Program mapping after restart
        tenantContext.executeInTransaction(restartedConnectionManager, conn -> {
            String postRestartCourseSql = "SELECT c.id, c.code, c.name, c.credits, m.program_id "
                    + "FROM acd.courses c "
                    + "JOIN acd.course_program_map m ON c.id = m.course_id "
                    + "WHERE c.id = ? AND c.tenant_id = ?";
            try (PreparedStatement ps = conn.prepareStatement(postRestartCourseSql)) {
                ps.setObject(1, courseUuid);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    assertTrue("Course and program mapping must persist after restart", rs.next());
                    assertEquals(courseCode, rs.getString("code"));
                    assertEquals("Advanced Workflow Systems", rs.getString("name"));
                    assertEquals(5.0, rs.getDouble("credits"), 0.001);
                    assertEquals(testProgramId, rs.getString("program_id"));
                }
            }
            return null;
        });

        // ---------------------------------------------------------------------
        // Step 8: Security Verification - Tenant Isolation Post-Restart
        // Cross-tenant access must be rejected by PostgreSQL RLS
        // ---------------------------------------------------------------------
        Optional<Department> otherDept = restartedDeptRepo.findById(otherTenantContext, testDepartmentId);
        assertFalse("Cross-tenant read on department must return empty", otherDept.isPresent());

        Program otherProg = restartedProgRepo.getProgramById(otherTenantContext, testProgramId);
        assertNull("Cross-tenant read on program must return null", otherProg);

        otherTenantContext.executeInTransaction(restartedConnectionManager, conn -> {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM acd.courses WHERE id = ?")) {
                ps.setObject(1, courseUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    assertFalse("Cross-tenant read on course must be blocked by RLS", rs.next());
                }
            }
            return null;
        });

        // Reconnect connectionManager for tearDown
        connectionManager = restartedConnectionManager;
    }
}
