package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.api.AuditEvent;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresSystemAuditLogRepository} against PostgreSQL (ADM-01 Item 10).
 */
public class PostgresSystemAuditLogRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresSystemAuditLogRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private String testEventId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresSystemAuditLogRepository(connectionManager);

        try (Connection conn = connectionManager.getConnection()) {
            try (Statement stmt = conn.createStatement()) {
                stmt.execute("GRANT SELECT, INSERT ON audit.events TO authenticated");
                stmt.execute("DO $$ BEGIN "
                        + "IF NOT EXISTS (SELECT 1 FROM pg_policies WHERE schemaname='audit' AND tablename='events' AND policyname='events_insert') THEN "
                        + "  CREATE POLICY events_insert ON audit.events FOR INSERT TO authenticated "
                        + "  WITH CHECK (tenant_id = (SELECT core.current_tenant_id()) OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))); "
                        + "END IF; "
                        + "END $$;");
            } catch (Exception ignored) {}
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

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null || testEventId == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM audit.events WHERE id = ?")) {
                ps.setObject(1, UUID.fromString(testEventId));
                ps.executeUpdate();
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testRecordAndListRecentEventsLiveDb() {
        AuditEvent event = AuditEvent.builder()
                .action("SECURITY_POLICY_UPDATED")
                .principalId(tenantContext.getUserId().toString())
                .principalRole("TENANT_ADMIN")
                .resourceType("POLICY")
                .resourceId(UUID.randomUUID().toString())
                .status("SUCCESS")
                .description("Updated global security policy test")
                .build();

        testEventId = repository.recordEvent(tenantContext, event);
        assertNotNull("Audit event ID must not be null", testEventId);

        List<Map<String, Object>> recent = repository.listRecentEvents(tenantContext, 10);
        assertNotNull(recent);
        assertFalse("Recent events list should not be empty", recent.isEmpty());

        boolean found = false;
        for (Map<String, Object> evt : recent) {
            if (testEventId.equals(evt.get("id"))) {
                found = true;
                assertEquals("SECURITY_POLICY_UPDATED", evt.get("action"));
                assertEquals(tenantId.toString(), evt.get("tenantId"));
                break;
            }
        }
        assertTrue("Recorded audit event must be present in recent events", found);
    }

    @Test
    public void testTenantIsolationOnAuditEvents() {
        AuditEvent event = AuditEvent.builder()
                .action("TENANT_ISOLATION_TEST")
                .principalId(tenantContext.getUserId().toString())
                .principalRole("TENANT_ADMIN")
                .resourceType("TENANT")
                .resourceId(tenantId.toString())
                .status("SUCCESS")
                .description("Isolation test event")
                .build();

        testEventId = repository.recordEvent(tenantContext, event);
        assertNotNull(testEventId);

        // Listing under other tenant must not contain this event
        List<Map<String, Object>> otherTenantEvents = repository.listRecentEvents(otherTenantContext, 100);
        for (Map<String, Object> evt : otherTenantEvents) {
            assertNotEquals("Cross-tenant audit event must not leak", testEventId, evt.get("id"));
        }
    }
}
