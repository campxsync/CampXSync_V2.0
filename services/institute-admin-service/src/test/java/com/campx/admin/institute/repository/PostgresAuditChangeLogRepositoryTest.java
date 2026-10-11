package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.AuditChangeLog;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for Item 27: {@link PostgresAuditChangeLogRepository} against Supabase PostgreSQL.
 * Verifies row-level mutation auditing, array and jsonb serialization, and multi-tenant RLS isolation.
 */
public class PostgresAuditChangeLogRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresAuditChangeLogRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdLogIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresAuditChangeLogRepository(connectionManager);

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

        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            try (Statement st = conn.createStatement()) {
                try { st.execute("GRANT SELECT, INSERT ON audit.change_log TO authenticated"); } catch (Exception ignored) {}
                try {
                    st.execute("CREATE POLICY change_log_insert ON audit.change_log FOR INSERT TO authenticated WITH CHECK ((tenant_id = core.current_tenant_id()) OR ((tenant_id IS NULL) AND (iam.is_platform_admin())))");
                } catch (Exception ignored) {}
            }
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID lid : createdLogIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM audit.change_log WHERE id = ?")) {
                    ps.setObject(1, lid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testRecordAndRetrieveChangeLog() throws Exception {
        UUID recordId = UUID.randomUUID();
        AuditChangeLog log = new AuditChangeLog();
        log.setTableSchema("core");
        log.setTableName("courses");
        log.setRecordId(recordId.toString());
        log.setAction("I");
        log.setChangedFields(Arrays.asList("code", "name", "credits"));
        log.setNewData("{\"code\": \"CS-101\", \"credits\": 4}");
        log.setRequestId("REQ-TRACE-999");

        AuditChangeLog recorded = repository.recordChange(tenantContext, log);
        assertNotNull(recorded.getId());
        createdLogIds.add(UUID.fromString(recorded.getId()));

        assertEquals(tenantId.toString(), recorded.getTenantId());
        assertEquals("core", recorded.getTableSchema());
        assertEquals("courses", recorded.getTableName());
        assertEquals("I", recorded.getAction());
        assertEquals(3, recorded.getChangedFields().size());

        Optional<AuditChangeLog> byId = repository.findById(tenantContext, recorded.getId());
        assertTrue(byId.isPresent());
        assertEquals(recorded.getId(), byId.get().getId());

        List<AuditChangeLog> byRecord = repository.listChangesByRecord(tenantContext, "core", "courses", recordId.toString());
        assertFalse(byRecord.isEmpty());
        assertEquals(recorded.getId(), byRecord.get(0).getId());

        List<AuditChangeLog> recent = repository.listRecentChanges(tenantContext, 10);
        assertFalse(recent.isEmpty());
    }

    @Test
    public void testListChangesByActor() throws Exception {
        UUID actorId = UUID.randomUUID();
        AuditChangeLog log = new AuditChangeLog();
        log.setActorId(actorId.toString());
        log.setTableSchema("iam");
        log.setTableName("users");
        log.setAction("U");
        log.setChangedFields(Collections.singletonList("status"));

        AuditChangeLog recorded = repository.recordChange(tenantContext, log);
        assertNotNull(recorded.getId());
        createdLogIds.add(UUID.fromString(recorded.getId()));

        List<AuditChangeLog> byActor = repository.listChangesByActor(tenantContext, actorId.toString());
        assertFalse(byActor.isEmpty());
        assertEquals(recorded.getId(), byActor.get(0).getId());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        AuditChangeLog log = new AuditChangeLog();
        log.setTableSchema("plat");
        log.setTableName("invoices");
        log.setAction("D");

        AuditChangeLog recorded = repository.recordChange(tenantContext, log);
        assertNotNull(recorded.getId());
        createdLogIds.add(UUID.fromString(recorded.getId()));

        Optional<AuditChangeLog> crossRead = repository.findById(otherTenantContext, recorded.getId());
        assertFalse("Cross-tenant change log read must be blocked by RLS", crossRead.isPresent());
    }
}
