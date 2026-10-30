package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.exception.CollegeResourceConflictException;
import com.campx.admin.college.model.CollegeModels.Department;
import com.campx.admin.college.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresDepartmentRepository} against PostgreSQL.
 * Verifies department creation, retrieval, listing, soft-retire, and cross-tenant isolation.
 */
public class PostgresDepartmentRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresDepartmentRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private UUID collegeId;
    private String testDepId;

    @Before
    public void setUp() throws Exception {
        connectionManager = DatabaseConnectionManager.getInstance();
        tenantId = com.campx.admin.college.config.TestEnvironmentGuard.verifySafeStagingTenant(connectionManager);
        repository = new PostgresDepartmentRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            com.campx.admin.college.config.TestEnvironmentGuard.setTenantJwtContext(conn, tenantId);

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

            UUID adminUserId = com.campx.admin.college.config.TestEnvironmentGuard.resolveSafeTestUserId(conn, tenantId);
            tenantContext = new UserSecurityContext(adminUserId, tenantId);
        }

        otherTenantId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        otherTenantContext = new UserSecurityContext(otherUserId, otherTenantId);
    }

    @After
    public void tearDown() {
        if (testDepId != null && tenantContext != null) {
            try {
                repository.deleteDepartment(tenantContext, testDepId);
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testCreateAndRetrieveDepartment() {
        Department dep = new Department();
        String code = "DEP" + System.currentTimeMillis() % 100000;
        dep.setDepartmentCode(code);
        dep.setName("Test Computer Science Department");
        dep.setCollegeId(collegeId.toString());

        Department created = repository.createDepartment(tenantContext, dep);
        assertNotNull(created.getId());
        testDepId = created.getId();
        assertEquals(code, created.getDepartmentCode());
        assertEquals("ACTIVE", created.getStatus());

        Optional<Department> found = repository.findById(tenantContext, created.getId());
        assertTrue(found.isPresent());
        assertEquals("Test Computer Science Department", found.get().getName());
        assertEquals(code, found.get().getDepartmentCode());
    }

    @Test
    public void testCrossTenantIsolationReturnsEmpty() {
        Department dep = new Department();
        String code = "ISO" + System.currentTimeMillis() % 100000;
        dep.setDepartmentCode(code);
        dep.setName("Tenant Isolated Dept");
        dep.setCollegeId(collegeId.toString());

        Department created = repository.createDepartment(tenantContext, dep);
        testDepId = created.getId();

        // Querying from another tenant must NOT see this department
        Optional<Department> isolated = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant lookup must return empty", isolated.isPresent());

        List<Department> otherList = repository.listDepartments(otherTenantContext, null);
        for (Department d : otherList) {
            assertNotEquals(created.getId(), d.getId());
        }
    }

    @Test(expected = CollegeResourceConflictException.class)
    public void testDuplicateDepartmentCodeRejected() {
        String code = "DUP" + System.currentTimeMillis() % 100000;

        Department dep1 = new Department();
        dep1.setDepartmentCode(code);
        dep1.setName("Duplicate Dept 1");
        dep1.setCollegeId(collegeId.toString());
        Department created1 = repository.createDepartment(tenantContext, dep1);
        testDepId = created1.getId();

        Department dep2 = new Department();
        dep2.setDepartmentCode(code);
        dep2.setName("Duplicate Dept 2");
        dep2.setCollegeId(collegeId.toString());
        repository.createDepartment(tenantContext, dep2);
    }

    @Test
    public void testRetireDepartment() {
        Department dep = new Department();
        String code = "RET" + System.currentTimeMillis() % 100000;
        dep.setDepartmentCode(code);
        dep.setName("Retirement Test Dept");
        dep.setCollegeId(collegeId.toString());

        Department created = repository.createDepartment(tenantContext, dep);
        testDepId = created.getId();

        repository.retireDepartment(tenantContext, created.getId());

        Optional<Department> retired = repository.findById(tenantContext, created.getId());
        assertTrue(retired.isPresent());
        assertEquals("RETIRED", retired.get().getStatus());
    }
}
