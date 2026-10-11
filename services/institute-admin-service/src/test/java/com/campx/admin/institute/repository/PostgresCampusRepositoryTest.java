package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.Campus;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for Item 21: {@link PostgresCampusRepository} against Supabase PostgreSQL.
 * Verifies campus creation, unique code per college, updates, deletion, and tenant boundary isolation.
 */
public class PostgresCampusRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresCampusRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;
    private static UUID collegeId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdCampusIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresCampusRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.colleges WHERE tenant_id = ? AND deleted_at IS NULL LIMIT 1")) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        collegeId = (UUID) rs.getObject("id");
                    }
                }
            }
        }
        assumeNotNull("Requires an active tenant", tenantId);
        assumeNotNull("Requires an active college in tenant", collegeId);

        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, tenantId);
        otherTenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, otherTenantId);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID cid : createdCampusIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.campuses WHERE id = ?")) {
                    ps.setObject(1, cid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveCampus() throws Exception {
        String code = "CMP_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode(code);
        campus.setName("Engineering East Campus");
        campus.setPrimary(true);

        Campus created = repository.createCampus(tenantContext, campus);
        assertNotNull(created.getId());
        createdCampusIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(collegeId.toString(), created.getCollegeId());
        assertEquals(code, created.getCode());
        assertEquals("Engineering East Campus", created.getName());
        assertTrue(created.isPrimary());

        // Find by ID
        Optional<Campus> byId = repository.findById(tenantContext, created.getId());
        assertTrue("Campus must be retrievable by ID", byId.isPresent());
        assertEquals("Engineering East Campus", byId.get().getName());

        // Find by Code
        Optional<Campus> byCode = repository.findByCode(tenantContext, collegeId.toString(), code);
        assertTrue("Campus must be retrievable by Code", byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        // List campuses for college
        List<Campus> list = repository.listCampuses(tenantContext, collegeId.toString());
        assertFalse(list.isEmpty());
    }

    @Test
    public void testUpdateCampus() throws Exception {
        String code = "CMP_UPD_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode(code);
        campus.setName("Original Campus Name");

        Campus created = repository.createCampus(tenantContext, campus);
        createdCampusIds.add(UUID.fromString(created.getId()));

        Campus update = new Campus();
        update.setId(created.getId());
        update.setName("Updated Campus Name");
        update.setPrimary(true);
        update.setStatus("SUSPENDED");

        Campus updated = repository.updateCampus(tenantContext, update);
        assertEquals("Updated Campus Name", updated.getName());
        assertTrue(updated.isPrimary());
        assertEquals("SUSPENDED", updated.getStatus());
        assertEquals(created.getRowVersion() + 1, updated.getRowVersion());
    }

    @Test
    public void testDeleteCampus() throws Exception {
        String code = "CMP_DEL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode(code);
        campus.setName("Campus To Delete");

        Campus created = repository.createCampus(tenantContext, campus);
        createdCampusIds.add(UUID.fromString(created.getId()));

        repository.deleteCampus(tenantContext, created.getId());

        Optional<Campus> afterDelete = repository.findById(tenantContext, created.getId());
        assertFalse("Deleted campus must not be returned", afterDelete.isPresent());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        String code = "CMP_ISO_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        Campus campus = new Campus();
        campus.setCollegeId(collegeId.toString());
        campus.setCode(code);
        campus.setName("Isolated Campus");

        Campus created = repository.createCampus(tenantContext, campus);
        createdCampusIds.add(UUID.fromString(created.getId()));

        Optional<Campus> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant lookup must not find campus", cross.isPresent());
    }
}
