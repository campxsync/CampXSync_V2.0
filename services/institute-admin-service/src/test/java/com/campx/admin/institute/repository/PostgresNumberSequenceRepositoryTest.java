package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.NumberSequence;
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
 * Live integration tests for Item 24: {@link PostgresNumberSequenceRepository} against Supabase PostgreSQL.
 * Verifies atomic sequence numbers generation, CRUD lifecycle, and multi-tenant RLS isolation.
 */
public class PostgresNumberSequenceRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresNumberSequenceRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdSequenceIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresNumberSequenceRepository(connectionManager);

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
            for (UUID sid : createdSequenceIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM core.number_sequences WHERE id = ?")) {
                    ps.setObject(1, sid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndRetrieveSequence() throws Exception {
        String scopeKey = "INV_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey(scopeKey);
        seq.setPrefix("INV-2026-");
        seq.setSuffix("-A");
        seq.setNextValue(1);
        seq.setPadding((short) 5);
        seq.setResetPolicy("YEARLY");

        NumberSequence created = repository.createSequence(tenantContext, seq);
        assertNotNull(created.getId());
        createdSequenceIds.add(UUID.fromString(created.getId()));

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(scopeKey, created.getScopeKey());
        assertEquals("INV-2026-", created.getPrefix());
        assertEquals("-A", created.getSuffix());
        assertEquals(1, created.getNextValue());

        Optional<NumberSequence> byId = repository.findById(tenantContext, created.getId());
        assertTrue(byId.isPresent());
        assertEquals(created.getId(), byId.get().getId());

        Optional<NumberSequence> byScope = repository.findByScope(tenantContext, scopeKey, null);
        assertTrue(byScope.isPresent());
        assertEquals(created.getId(), byScope.get().getId());

        List<NumberSequence> list = repository.listSequences(tenantContext);
        assertFalse(list.isEmpty());
    }

    @Test
    public void testAtomicNumberGeneration() throws Exception {
        String scopeKey = "STU_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey(scopeKey);
        seq.setPrefix("STU-");
        seq.setSuffix("");
        seq.setNextValue(1);
        seq.setPadding((short) 4);

        NumberSequence created = repository.createSequence(tenantContext, seq);
        assertNotNull(created.getId());
        createdSequenceIds.add(UUID.fromString(created.getId()));

        String num1 = repository.generateNextNumber(tenantContext, scopeKey, null);
        assertEquals("STU-0001", num1);

        String num2 = repository.generateNextNumber(tenantContext, scopeKey, null);
        assertEquals("STU-0002", num2);

        String num3 = repository.generateNextNumber(tenantContext, scopeKey, null);
        assertEquals("STU-0003", num3);
    }

    @Test
    public void testUpdateAndDeleteSequence() throws Exception {
        String scopeKey = "CRS_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey(scopeKey);
        seq.setPrefix("CRS-");
        seq.setNextValue(50);

        NumberSequence created = repository.createSequence(tenantContext, seq);
        assertNotNull(created.getId());
        createdSequenceIds.add(UUID.fromString(created.getId()));

        created.setPrefix("MOD-CRS-");
        NumberSequence updated = repository.updateSequence(tenantContext, created);
        assertEquals("MOD-CRS-", updated.getPrefix());
        assertEquals(2, updated.getRowVersion());

        repository.deleteSequence(tenantContext, created.getId());
        Optional<NumberSequence> deleted = repository.findById(tenantContext, created.getId());
        assertFalse(deleted.isPresent());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        String scopeKey = "ISO_" + UUID.randomUUID().toString().substring(0, 8).toUpperCase(Locale.ROOT);
        NumberSequence seq = new NumberSequence();
        seq.setScopeKey(scopeKey);
        seq.setPrefix("ISO-");

        NumberSequence created = repository.createSequence(tenantContext, seq);
        assertNotNull(created.getId());
        createdSequenceIds.add(UUID.fromString(created.getId()));

        Optional<NumberSequence> crossRead = repository.findById(otherTenantContext, created.getId());
        assertFalse("Cross-tenant sequence read must be blocked by RLS", crossRead.isPresent());

        Optional<NumberSequence> crossScope = repository.findByScope(otherTenantContext, scopeKey, null);
        assertFalse("Cross-tenant scope read must be blocked by RLS", crossScope.isPresent());
    }
}
