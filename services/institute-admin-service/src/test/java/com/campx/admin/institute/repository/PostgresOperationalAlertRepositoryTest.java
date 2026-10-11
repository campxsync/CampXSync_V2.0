package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.model.InstituteModels.OperationalAlert;
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
 * Live integration tests for {@link PostgresOperationalAlertRepository} against PostgreSQL {@code plat.alerts}.
 */
public class PostgresOperationalAlertRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresOperationalAlertRepository repository;
    private UserSecurityContext platformAdminContext;

    private String testAlertId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresOperationalAlertRepository(connectionManager);

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        platformAdminContext = UserSecurityContext.forPlatformAdmin(adminUserId);
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            if (testAlertId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.alerts WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testAlertId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAcknowledgeAndResolveAlert() {
        OperationalAlert alert = new OperationalAlert();
        alert.setAlertCode("DB_PRESSURE_" + UUID.randomUUID().toString().substring(0, 6));
        alert.setSeverity("WARNING");
        alert.setMessage("Database pool connections reached 85% capacity");

        OperationalAlert created = repository.createAlert(platformAdminContext, alert);
        assertNotNull("Created alert must not be null", created);
        assertNotNull("Alert ID must be generated", created.getId());
        testAlertId = created.getId();
        assertEquals(alert.getAlertCode(), created.getAlertCode());
        assertEquals("ACTIVE", created.getStatus());

        // Acknowledge alert
        OperationalAlert ack = repository.acknowledgeAlert(platformAdminContext, UUID.fromString(created.getId()));
        assertEquals("ACKNOWLEDGED", ack.getStatus());

        // Resolve alert
        OperationalAlert resolved = repository.resolveAlert(platformAdminContext, UUID.fromString(created.getId()));
        assertEquals("RESOLVED", resolved.getStatus());

        Optional<OperationalAlert> fetched = repository.findById(platformAdminContext, UUID.fromString(created.getId()));
        assertTrue("Alert must be fetchable", fetched.isPresent());
        assertEquals("RESOLVED", fetched.get().getStatus());
    }

    @Test
    public void testListAlertsFiltering() {
        OperationalAlert alert = new OperationalAlert();
        alert.setAlertCode("API_ERROR_RATE_" + UUID.randomUUID().toString().substring(0, 6));
        alert.setSeverity("ERROR");
        alert.setMessage("High 5xx error rate detected on gateway");

        OperationalAlert created = repository.createAlert(platformAdminContext, alert);
        testAlertId = created.getId();

        List<OperationalAlert> activeList = repository.listAlerts(platformAdminContext, "ACTIVE");
        assertTrue("Must include created active alert", activeList.stream().anyMatch(a -> a.getId().equals(created.getId())));
    }

    @Test
    public void testValidationRejectsMalformedPayloads() {
        try {
            repository.createAlert(platformAdminContext, null);
            fail("Must reject null payload");
        } catch (MalformedPayloadException expected) {}
    }
}
