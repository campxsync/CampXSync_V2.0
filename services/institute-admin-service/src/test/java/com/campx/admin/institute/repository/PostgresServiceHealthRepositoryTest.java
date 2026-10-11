package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.model.InstituteModels.PlatformHealth;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresServiceHealthRepository} against PostgreSQL {@code plat.service_health}.
 */
public class PostgresServiceHealthRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresServiceHealthRepository repository;
    private UserSecurityContext platformAdminContext;

    private String testServiceName;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresServiceHealthRepository(connectionManager);

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        platformAdminContext = UserSecurityContext.forPlatformAdmin(adminUserId);

        testServiceName = "SVC_TEST_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            if (testServiceName != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.service_health WHERE service_name = ?")) {
                    ps.setString(1, testServiceName);
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testRecordHeartbeatAndUpsert() {
        PlatformHealth health = new PlatformHealth();
        health.setComponent(testServiceName);
        health.setStatus("HEALTHY");
        health.setLatencyMs(45);

        PlatformHealth recorded = repository.recordHeartbeat(platformAdminContext, health);
        assertNotNull("Recorded health must not be null", recorded);
        assertEquals(testServiceName, recorded.getComponent());
        assertEquals("HEALTHY", recorded.getStatus());
        assertEquals(45, recorded.getLatencyMs());

        // Upsert same service with updated latency
        health.setStatus("DEGRADED");
        health.setLatencyMs(120);
        PlatformHealth updated = repository.recordHeartbeat(platformAdminContext, health);
        assertNotNull("Updated health must not be null", updated);
        assertEquals(recorded.getId(), updated.getId());
        assertEquals("DEGRADED", updated.getStatus());
        assertEquals(120, updated.getLatencyMs());

        Optional<PlatformHealth> fetched = repository.findByServiceName(platformAdminContext, testServiceName);
        assertTrue("Service must be fetchable by service name", fetched.isPresent());
        assertEquals(120, fetched.get().getLatencyMs());
    }

    @Test
    public void testListAllServices() {
        PlatformHealth health = new PlatformHealth();
        health.setComponent(testServiceName);
        health.setStatus("HEALTHY");
        health.setLatencyMs(30);
        repository.recordHeartbeat(platformAdminContext, health);

        List<PlatformHealth> all = repository.listAll(platformAdminContext);
        assertNotNull("List of all services must not be null", all);
        assertTrue("List must contain the test service", all.stream().anyMatch(h -> testServiceName.equals(h.getComponent())));
    }

    @Test
    public void testValidationRejectsMalformedPayloads() {
        try {
            repository.recordHeartbeat(platformAdminContext, null);
            fail("Must reject null payload");
        } catch (MalformedPayloadException expected) {}

        PlatformHealth empty = new PlatformHealth();
        try {
            repository.recordHeartbeat(platformAdminContext, empty);
            fail("Must reject empty component name");
        } catch (MalformedPayloadException expected) {}
    }
}
