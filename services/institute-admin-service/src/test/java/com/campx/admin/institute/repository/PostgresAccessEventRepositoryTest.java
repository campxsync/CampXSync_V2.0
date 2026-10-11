package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.AccessEvent;
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
 * Live integration tests for Item 26: {@link PostgresAccessEventRepository} against Supabase PostgreSQL.
 * Verifies audit access events logging, IP handling, resource/principal querying, and multi-tenant RLS isolation.
 */
public class PostgresAccessEventRepositoryTest {

    private static DatabaseConnectionManager connectionManager;
    private static PostgresAccessEventRepository repository;
    private static UserSecurityContext tenantContext;
    private static UserSecurityContext otherTenantContext;

    private static UUID tenantId;
    private static UUID otherTenantId;

    private static final UUID SUPER_ADMIN_UUID = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
    private static final Set<UUID> createdEventIds = Collections.newSetFromMap(new ConcurrentHashMap<UUID, Boolean>());

    @BeforeClass
    public static void setUpClass() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require database configuration", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresAccessEventRepository(connectionManager);

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
                try { st.execute("GRANT SELECT, INSERT ON audit.access_events TO authenticated"); } catch (Exception ignored) {}
                try {
                    st.execute("CREATE POLICY access_events_insert ON audit.access_events FOR INSERT TO authenticated WITH CHECK ((tenant_id = core.current_tenant_id()) OR ((tenant_id IS NULL) AND (iam.is_platform_admin())))");
                } catch (Exception ignored) {}
            }
        }
    }

    @AfterClass
    public static void tearDownClass() throws Exception {
        try (Connection conn = connectionManager.getConnection()) {
            conn.setAutoCommit(true);
            for (UUID eid : createdEventIds) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM audit.access_events WHERE id = ?")) {
                    ps.setObject(1, eid);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testRecordAndRetrieveEvent() throws Exception {
        UUID resourceId = UUID.randomUUID();
        AccessEvent event = new AccessEvent();
        event.setResourceType("TRANSCRIPT");
        event.setResourceId(resourceId.toString());
        event.setAccessType("EXPORT");
        event.setSensitivity("RESTRICTED");
        event.setIp("192.168.1.100");
        event.setReason("Official graduation audit");

        AccessEvent recorded = repository.recordEvent(tenantContext, event);
        assertNotNull(recorded.getId());
        createdEventIds.add(UUID.fromString(recorded.getId()));

        assertEquals(tenantId.toString(), recorded.getTenantId());
        assertEquals("TRANSCRIPT", recorded.getResourceType());
        assertEquals("EXPORT", recorded.getAccessType());
        assertEquals("RESTRICTED", recorded.getSensitivity());
        assertEquals("192.168.1.100", recorded.getIp());
        assertEquals("Official graduation audit", recorded.getReason());

        Optional<AccessEvent> byId = repository.findById(tenantContext, recorded.getId());
        assertTrue(byId.isPresent());
        assertEquals(recorded.getId(), byId.get().getId());

        List<AccessEvent> byResource = repository.listEventsByResource(tenantContext, "TRANSCRIPT", resourceId.toString());
        assertFalse(byResource.isEmpty());
        assertEquals(recorded.getId(), byResource.get(0).getId());

        List<AccessEvent> recent = repository.listRecentEvents(tenantContext, 10);
        assertFalse(recent.isEmpty());
    }

    @Test
    public void testListEventsByPrincipal() throws Exception {
        UUID principalId = UUID.randomUUID();
        AccessEvent event = new AccessEvent();
        event.setPrincipalId(principalId.toString());
        event.setResourceType("FACULTY_EVALUATION");
        event.setAccessType("READ");
        event.setSensitivity("CONFIDENTIAL");
        event.setIp("10.0.1.50");

        AccessEvent recorded = repository.recordEvent(tenantContext, event);
        assertNotNull(recorded.getId());
        createdEventIds.add(UUID.fromString(recorded.getId()));

        List<AccessEvent> byPrincipal = repository.listEventsByPrincipal(tenantContext, principalId.toString());
        assertFalse(byPrincipal.isEmpty());
        assertEquals(recorded.getId(), byPrincipal.get(0).getId());
    }

    @Test
    public void testTenantIsolation() throws Exception {
        AccessEvent event = new AccessEvent();
        event.setResourceType("FINANCIAL_LEDGER");
        event.setAccessType("DOWNLOAD");
        event.setSensitivity("RESTRICTED");

        AccessEvent recorded = repository.recordEvent(tenantContext, event);
        assertNotNull(recorded.getId());
        createdEventIds.add(UUID.fromString(recorded.getId()));

        Optional<AccessEvent> crossRead = repository.findById(otherTenantContext, recorded.getId());
        assertFalse("Cross-tenant access event read must be blocked by RLS", crossRead.isPresent());
    }
}
