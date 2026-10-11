package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.AcademicYear;
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
 * Live integration tests for Item 22: {@link PostgresAcademicYearRepository} against Supabase PostgreSQL.
 * Verifies academic year persistence, single current flag kernel enforcement, updates, deletion, and tenant boundary isolation.
 */
public class PostgresAcademicYearRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresAcademicYearRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdYearIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresAcademicYearRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT id FROM core.tenants WHERE deleted_at IS NULL ORDER BY created_at ASC LIMIT 1")) {
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        tenantId = (UUID) rs.getObject("id");
                    }
                }
            }
        }
        assumeNotNull("Requires an active tenant", tenantId);

        otherTenantId = UUID.randomUUID();
        tenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, tenantId);
        otherTenantContext = new UserSecurityContext(SUPER_ADMIN_UUID, otherTenantId);
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID yid : createdYearIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.academic_years WHERE id = ?")) {
                    ps.setObject(1, yid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveAcademicYear() throws Exception {
        String code = "AY_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        AcademicYear year = new AcademicYear();
        year.setCode(code);
        year.setStartDate(1767225600000L); // 2026-01-01
        year.setEndDate(1798761600000L);   // 2026-12-31
        year.setCurrent(false);

        AcademicYear created = repository.createAcademicYear(tenantContext, year);
        assertNotNull(created.getId());
        createdYearIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(code, created.getCode());
        assertFalse(created.isCurrent());

        // Find by ID
        Optional<AcademicYear> byId = repository.findById(tenantContext, created.getId());
        assertTrue("Academic year must be retrievable by ID", byId.isPresent());
        assertEquals(code, byId.get().getCode());

        // Find by Code
        Optional<AcademicYear> byCode = repository.findByCode(tenantContext, code);
        assertTrue("Academic year must be retrievable by Code", byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        // List academic years
        List<AcademicYear> list = repository.listAcademicYears(tenantContext);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testSetCurrentSwitchesFlag() throws Exception {
        String code1 = "AY_C1_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        AcademicYear y1 = new AcademicYear();
        y1.setCode(code1);
        y1.setStartDate(1735689600000L);
        y1.setEndDate(1767225600000L);
        y1.setCurrent(false);
        AcademicYear created1 = repository.createAcademicYear(tenantContext, y1);
        createdYearIds.add(UUID.fromString(created1.getId()));

        String code2 = "AY_C2_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        AcademicYear y2 = new AcademicYear();
        y2.setCode(code2);
        y2.setStartDate(1767225600000L);
        y2.setEndDate(1798761600000L);
        y2.setCurrent(false);
        AcademicYear created2 = repository.createAcademicYear(tenantContext, y2);
        createdYearIds.add(UUID.fromString(created2.getId()));

        // Set y1 current
        repository.setCurrent(tenantContext, created1.getId());
        Optional<AcademicYear> current1 = repository.findCurrent(tenantContext);
        assertTrue(current1.isPresent());
        assertEquals(created1.getId(), current1.get().getId());

        // Switch to y2 current
        repository.setCurrent(tenantContext, created2.getId());
        Optional<AcademicYear> current2 = repository.findCurrent(tenantContext);
        assertTrue(current2.isPresent());
        assertEquals(created2.getId(), current2.get().getId());

        // Verify y1 is now false
        Optional<AcademicYear> y1Refreshed = repository.findById(tenantContext, created1.getId());
        assertTrue(y1Refreshed.isPresent());
        assertFalse(y1Refreshed.get().isCurrent());
    }

    @Test
    public void testUpdateAcademicYear() throws Exception {
        String code = "AY_UPD_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        AcademicYear year = new AcademicYear();
        year.setCode(code);
        year.setStartDate(1767225600000L);
        year.setEndDate(1798761600000L);

        AcademicYear created = repository.createAcademicYear(tenantContext, year);
        createdYearIds.add(UUID.fromString(created.getId()));

        String newCode = code + "_RENAMED";
        AcademicYear update = new AcademicYear();
        update.setId(created.getId());
        update.setCode(newCode);

        AcademicYear updated = repository.updateAcademicYear(tenantContext, update);
        assertEquals(newCode, updated.getCode());
        assertEquals(created.getRowVersion() + 1, updated.getRowVersion());
    }

    @Test
    public void testDeleteAcademicYear() throws Exception {
        String code = "AY_DEL_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        AcademicYear year = new AcademicYear();
        year.setCode(code);
        year.setStartDate(1767225600000L);
        year.setEndDate(1798761600000L);

        AcademicYear created = repository.createAcademicYear(tenantContext, year);
        createdYearIds.add(UUID.fromString(created.getId()));

        repository.deleteAcademicYear(tenantContext, created.getId());

        Optional<AcademicYear> afterDelete = repository.findById(tenantContext, created.getId());
        assertFalse("Deleted academic year must not be returned", afterDelete.isPresent());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        String code = "AY_ISO_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase(Locale.ROOT);
        AcademicYear year = new AcademicYear();
        year.setCode(code);
        year.setStartDate(1767225600000L);
        year.setEndDate(1798761600000L);

        AcademicYear created = repository.createAcademicYear(tenantContext, year);
        createdYearIds.add(UUID.fromString(created.getId()));

        Optional<AcademicYear> cross = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant lookup must not find academic year", cross.isPresent());
    }
}
