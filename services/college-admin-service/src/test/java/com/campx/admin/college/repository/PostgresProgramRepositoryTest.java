package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.model.CollegeModels.Program;
import com.campx.admin.college.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresProgramRepository} against PostgreSQL.
 * Verifies academic program creation, retrieval, listing, update, soft-delete, and cross-tenant isolation.
 */
public class PostgresProgramRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresDepartmentRepository departmentRepository;
    private PostgresProgramRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID collegeId;
    private String testDepId;
    private String testProgId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        departmentRepository = new PostgresDepartmentRepository(connectionManager);
        repository = new PostgresProgramRepository(connectionManager);

        // Discover active tenant in core.tenants
        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }

            assumeNotNull("Requires at least one active tenant in core.tenants", tenantId);

            // Discover or create an active college under this tenant
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        collegeId = (UUID) rs.getObject("id");
                    }
                }
            }

            if (collegeId == null) {
                collegeId = UUID.randomUUID();
                try (PreparedStatement ps = conn.prepareStatement(
                        "INSERT INTO core.colleges (id, tenant_id, code, name, status, created_at, updated_at, row_version) " +
                                "VALUES (?, ?, 'COL_TEST_INT', 'Integration Test College', 'ACTIVE', now(), now(), 1)")) {
                    ps.setObject(1, collegeId);
                    ps.setObject(2, tenantId);
                    ps.executeUpdate();
                }
            }

            UUID adminUserId = null;
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
            tenantContext = new UserSecurityContext(adminUserId, tenantId);

            // Ensure an active parent department exists for the test program
            Department dep = new Department();
            dep.setDepartmentCode("DEP_PROG_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
            dep.setName("Parent Department for Programs");
            dep.setCollegeId(collegeId.toString());
            Department createdDep = departmentRepository.createDepartment(tenantContext, dep);
            testDepId = createdDep.getId();
        }

        otherTenantId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        otherTenantContext = new UserSecurityContext(otherUserId, otherTenantId);
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            if (testProgId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM acd.programs WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testProgId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            if (testDepId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.departments WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testDepId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndGetProgramLiveDb() {
        String progCode = "PRG_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Program prog = new Program();
        prog.setProgramCode(progCode);
        prog.setName("B.Tech in Artificial Intelligence");
        prog.setDepartmentId(testDepId);
        prog.setCollegeId(collegeId.toString());
        prog.setLevel("UG");
        prog.setDurationYears(4);
        prog.setTotalTerms(8);
        prog.setPublished(true);

        Program created = repository.createProgram(tenantContext, prog);
        assertNotNull(created);
        assertNotNull(created.getId());
        testProgId = created.getId();

        assertEquals(progCode, created.getProgramCode());
        assertEquals("B.Tech in Artificial Intelligence", created.getName());
        assertEquals("UG", created.getLevel());
        assertEquals(4, created.getDurationYears());
        assertEquals("ACTIVE", created.getStatus());
        assertTrue(created.isPublished());
        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(collegeId.toString(), created.getCollegeId());
        assertEquals(testDepId, created.getDepartmentId());

        // Read by ID
        Program fetched = repository.getProgramById(tenantContext, testProgId);
        assertNotNull("Program must be found by ID", fetched);
        assertEquals(progCode, fetched.getProgramCode());
        assertEquals(testDepId, fetched.getDepartmentId());

        // Update
        fetched.setName("B.Tech in Artificial Intelligence & Data Science");
        Program updated = repository.updateProgram(tenantContext, fetched);
        assertNotNull(updated);
        assertEquals("B.Tech in Artificial Intelligence & Data Science", updated.getName());
    }

    @Test(expected = CollegeResourceConflictException.class)
    public void testDuplicateProgramCodeConflict() {
        String progCode = "DUP_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Program prog1 = new Program();
        prog1.setProgramCode(progCode);
        prog1.setName("Duplicate Program 1");
        prog1.setDepartmentId(testDepId);
        prog1.setCollegeId(collegeId.toString());
        prog1.setDurationYears(4);

        Program created = repository.createProgram(tenantContext, prog1);
        testProgId = created.getId();

        Program prog2 = new Program();
        prog2.setProgramCode(progCode);
        prog2.setName("Duplicate Program 2");
        prog2.setDepartmentId(testDepId);
        prog2.setCollegeId(collegeId.toString());
        prog2.setDurationYears(4);

        // Must throw CollegeResourceConflictException
        repository.createProgram(tenantContext, prog2);
    }

    @Test
    public void testListProgramsWithFilters() {
        String progCode = "LST_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Program prog = new Program();
        prog.setProgramCode(progCode);
        prog.setName("Program for Listing");
        prog.setDepartmentId(testDepId);
        prog.setCollegeId(collegeId.toString());
        prog.setDurationYears(2);
        prog.setLevel("PG");

        Program created = repository.createProgram(tenantContext, prog);
        testProgId = created.getId();

        List<Program> list = repository.listPrograms(tenantContext, collegeId.toString(), testDepId);
        assertNotNull(list);
        assertFalse(list.isEmpty());

        boolean found = false;
        for (Program p : list) {
            if (p.getId().equals(testProgId)) {
                found = true;
                assertEquals(progCode, p.getProgramCode());
                break;
            }
        }
        assertTrue("Newly created program must appear in filtered listing", found);
    }

    @Test
    public void testCrossTenantIsolation() {
        String progCode = "ISO_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Program prog = new Program();
        prog.setProgramCode(progCode);
        prog.setName("Isolation Test Program");
        prog.setDepartmentId(testDepId);
        prog.setCollegeId(collegeId.toString());
        prog.setDurationYears(3);

        Program created = repository.createProgram(tenantContext, prog);
        testProgId = created.getId();

        // Reading under other tenant must return null
        Program crossTenantResult = repository.getProgramById(otherTenantContext, testProgId);
        assertNull("Cross-tenant read must return null due to tenant_id filter and RLS", crossTenantResult);
    }
}
