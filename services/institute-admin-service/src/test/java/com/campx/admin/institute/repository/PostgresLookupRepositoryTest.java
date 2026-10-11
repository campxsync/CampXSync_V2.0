package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.LookupType;
import com.campx.admin.institute.model.InstituteModels.LookupValue;
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
 * Live integration tests for Item 25: {@link PostgresLookupRepository} against Supabase PostgreSQL.
 * Verifies lookup types, lookup values, cascade deletion, and multi-tenant isolation under RLS.
 */
public class PostgresLookupRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresLookupRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdTypeIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresLookupRepository(connectionManager);

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
            for (UUID tid : createdTypeIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.lookup_types WHERE id = ?")) {
                    ps.setObject(1, tid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveLookupType() throws Exception {
        String code = "TYPE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LookupType type = new LookupType();
        type.setCode(code);
        type.setName("Admission Quotas");
        type.setDescription("Institutional quotas for admission");

        LookupType created = repository.createType(tenantContext, type);
        assertNotNull(created.getId());
        createdTypeIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(code, created.getCode());
        assertEquals("Admission Quotas", created.getName());

        Optional<LookupType> byId = repository.findTypeById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<LookupType> byCode = repository.findTypeByCode(tenantContext, code);
        assertTrue(byCode.isPresent());
        assertEquals(created.getId(), byCode.get().getId());

        List<LookupType> list = repository.listTypes(tenantContext);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testLookupValuesLifecycle() throws Exception {
        String code = "VALTYPE_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LookupType type = new LookupType();
        type.setCode(code);
        type.setName("Hostel Room Types");

        LookupType createdType = repository.createType(tenantContext, type);
        assertNotNull(createdType.getId());
        createdTypeIds.add(UUID.fromString(createdType.getId()));

        LookupValue v1 = new LookupValue();
        v1.setLookupTypeId(createdType.getId());
        v1.setCode("SINGLE_AC");
        v1.setLabel("Single Occupancy AC");
        v1.setSortOrder(1);
        v1.setActive(true);
        v1.setAttrs("{\"beds\": 1, \"ac\": true}");

        LookupValue saved1 = repository.createValue(tenantContext, v1);
        assertNotNull(saved1.getId());
        assertEquals("SINGLE_AC", saved1.getCode());

        LookupValue v2 = new LookupValue();
        v2.setLookupTypeId(createdType.getId());
        v2.setCode("DOUBLE_NON_AC");
        v2.setLabel("Double Occupancy Non-AC");
        v2.setSortOrder(2);
        v2.setActive(true);
        repository.createValue(tenantContext, v2);

        List<LookupValue> values = repository.listValuesByType(tenantContext, createdType.getId());
        assertEquals(2, values.size());

        Optional<LookupValue> byCode = repository.findValueByCode(tenantContext, createdType.getId(), "single_ac");
        assertTrue(byCode.isPresent());
        assertEquals(saved1.getId(), byCode.get().getId());

        saved1.setLabel("Single Occupancy Deluxe AC");
        LookupValue updated = repository.updateValue(tenantContext, saved1);
        assertEquals("Single Occupancy Deluxe AC", updated.getLabel());
        assertEquals(2, updated.getRowVersion());

        repository.deleteValue(tenantContext, saved1.getId());
        Optional<LookupValue> deleted = repository.findValueById(tenantContext, saved1.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testCascadeDelete() throws Exception {
        String code = "CAS_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LookupType type = new LookupType();
        type.setCode(code);
        type.setName("Temporary Domain");

        LookupType createdType = repository.createType(tenantContext, type);
        assertNotNull(createdType.getId());
        createdTypeIds.add(UUID.fromString(createdType.getId()));

        LookupValue v = new LookupValue();
        v.setLookupTypeId(createdType.getId());
        v.setCode("TMP_VAL");
        v.setLabel("Temporary Value");
        repository.createValue(tenantContext, v);

        repository.deleteType(tenantContext, createdType.getId());
        assertFalse(repository.findTypeById(tenantContext, createdType.getId()).isPresent());

        List<LookupValue> remaining = repository.listValuesByType(tenantContext, createdType.getId());
        assertTrue(remaining.isEmpty());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        String code = "ISO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        LookupType type = new LookupType();
        type.setCode(code);
        type.setName("Private Type");

        LookupType createdType = repository.createType(tenantContext, type);
        assertNotNull(createdType.getId());
        createdTypeIds.add(UUID.fromString(createdType.getId()));

        Optional<LookupType> crossRead = repository.findTypeById(otherTenantContext, createdType.getId());
        assertFalse("Cross-tenant lookup type read must be blocked by RLS", crossRead.isPresent());

        Optional<LookupType> crossCode = repository.findTypeByCode(otherTenantContext, code);
        assertFalse("Cross-tenant lookup type code read must be blocked by RLS", crossCode.isPresent());
    }
}
