package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.UsageMetric;
import com.campx.admin.institute.security.UserSecurityContext;
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
 * Live integration tests for {@link PostgresUsageMetricRepository} against PostgreSQL {@code plat.usage_metrics}.
 */
public class PostgresUsageMetricRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresUsageMetricRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private UUID otherTenantId;
    private String testMetricId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresUsageMetricRepository(connectionManager);

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
        assumeNotNull("Requires an active tenant in core.tenants", tenantId);

        otherTenantId = UUID.randomUUID();
        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), otherTenantId);
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            if (testMetricId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.usage_metrics WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testMetricId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testRecordAndRetrieveUsageMetric() {
        UsageMetric metric = new UsageMetric();
        metric.setTenantId(tenantId.toString());
        metric.setMetricType("ACTIVE_USERS");
        metric.setPeriod("2026-10");
        metric.setDimension("portal_users");
        metric.setValue(350);

        UsageMetric created = repository.recordUsageMetric(tenantContext, metric);
        assertNotNull("Created metric should not be null", created);
        assertNotNull("Generated metric ID should not be null", created.getId());
        testMetricId = created.getId();
        assertEquals("ACTIVE_USERS", created.getMetricType());
        assertEquals(350, created.getValue());
        assertEquals("2026-10", created.getPeriod());

        Optional<UsageMetric> fetched = repository.findById(tenantContext, UUID.fromString(created.getId()));
        assertTrue("Metric must be fetchable by ID", fetched.isPresent());
        assertEquals("ACTIVE_USERS", fetched.get().getMetricType());
        assertEquals(350, fetched.get().getValue());
    }

    @Test
    public void testFindByTenantAndPeriodFiltering() {
        UsageMetric m1 = new UsageMetric();
        m1.setTenantId(tenantId.toString());
        m1.setMetricType("STORAGE_MB");
        m1.setPeriod("2026-09");
        m1.setValue(10240);
        UsageMetric c1 = repository.recordUsageMetric(tenantContext, m1);

        UsageMetric m2 = new UsageMetric();
        m2.setTenantId(tenantId.toString());
        m2.setMetricType("API_CALLS");
        m2.setPeriod("2026-10");
        m2.setValue(45000);
        UsageMetric c2 = repository.recordUsageMetric(tenantContext, m2);

        try {
            List<UsageMetric> septList = repository.findByTenantAndPeriod(tenantContext, tenantId, "2026-09");
            assertTrue("Should find 2026-09 metric", septList.stream().anyMatch(m -> m.getId().equals(c1.getId())));

            List<UsageMetric> octList = repository.findByTenantAndPeriod(tenantContext, tenantId, "2026-10");
            assertTrue("Should find 2026-10 metric", octList.stream().anyMatch(m -> m.getId().equals(c2.getId())));
        } finally {
            try (Connection conn = connectionManager.getConnection()) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.usage_metrics WHERE id IN (?, ?)")) {
                    ps.setObject(1, UUID.fromString(c1.getId()));
                    ps.setObject(2, UUID.fromString(c2.getId()));
                    ps.executeUpdate();
                }
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testRLSKernelIsolationCrossTenant() {
        UsageMetric metric = new UsageMetric();
        metric.setTenantId(tenantId.toString());
        metric.setMetricType("COMPUTE_HOURS");
        metric.setPeriod("2026-10");
        metric.setValue(120);

        UsageMetric created = repository.recordUsageMetric(tenantContext, metric);
        testMetricId = created.getId();

        // Attempting to query with otherTenantContext must return empty (RLS / tenant isolation)
        Optional<UsageMetric> crossTenantFetch = repository.findById(otherTenantContext, UUID.fromString(created.getId()));
        assertFalse("Cross-tenant fetch must return empty under tenant isolation", crossTenantFetch.isPresent());

        // Attempting to list another tenant's metrics must trigger SecurityViolationException
        try {
            repository.findByTenantAndPeriod(otherTenantContext, tenantId, "2026-10");
            fail("Listing tenant metrics with other tenant context must fail");
        } catch (SecurityViolationException expected) {
            // Success: Multi-tenant boundary preserved
        }

        // Attempting to write into another tenant's metrics must trigger SecurityViolationException
        UsageMetric crossMetric = new UsageMetric();
        crossMetric.setTenantId(tenantId.toString());
        crossMetric.setMetricType("CROSS_WRITE");
        try {
            repository.recordUsageMetric(otherTenantContext, crossMetric);
            fail("Recording metrics for another tenant must fail");
        } catch (SecurityViolationException expected) {
            // Success: Multi-tenant write prohibited
        }
    }

    @Test
    public void testDeleteUsageMetricSoftDelete() {
        UsageMetric metric = new UsageMetric();
        metric.setTenantId(tenantId.toString());
        metric.setMetricType("BANDWIDTH_GB");
        metric.setPeriod("2026-10");
        metric.setValue(500);

        UsageMetric created = repository.recordUsageMetric(tenantContext, metric);
        UUID metricId = UUID.fromString(created.getId());
        testMetricId = created.getId();

        repository.deleteById(tenantContext, metricId);

        Optional<UsageMetric> fetched = repository.findById(tenantContext, metricId);
        assertFalse("Soft-deleted metric should not be returned by findById", fetched.isPresent());
    }

    @Test
    public void testValidationRejectsMalformedPayloads() {
        try {
            repository.recordUsageMetric(tenantContext, null);
            fail("Should reject null payload");
        } catch (MalformedPayloadException expected) {}

        UsageMetric metric = new UsageMetric();
        try {
            repository.recordUsageMetric(tenantContext, metric);
            fail("Should reject missing metricType");
        } catch (MalformedPayloadException expected) {}
    }
}
