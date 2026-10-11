package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.model.InstituteModels.Subscription;
import com.campx.admin.institute.model.InstituteModels.SubscriptionPlan;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresSubscriptionRepository} against PostgreSQL (ADM-01 Item 9).
 */
public class PostgresSubscriptionRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresSubscriptionPlanRepository planRepository;
    private PostgresSubscriptionRepository repository;
    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private String testPlanId;
    private String testSubId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        planRepository = new PostgresSubscriptionPlanRepository(connectionManager);
        repository = new PostgresSubscriptionRepository(connectionManager);

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

        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        tenantContext = new UserSecurityContext(adminUserId, tenantId);
        otherTenantContext = new UserSecurityContext(UUID.randomUUID(), UUID.randomUUID());

        // Create a test plan for foreign key
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode("PLAN_SUB_TEST_" + UUID.randomUUID().toString().substring(0, 5).toUpperCase());
        plan.setName("Test Sub Plan");
        plan.setBillingCycle("MONTHLY");
        plan.setPrice(1000.0);
        SubscriptionPlan createdPlan = planRepository.createPlan(tenantContext, plan);
        testPlanId = createdPlan.getId();
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            if (testSubId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.subscriptions WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testSubId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            if (testPlanId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.subscription_plans WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testPlanId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndGetSubscriptionLiveDb() {
        Subscription sub = new Subscription();
        sub.setPlanId(testPlanId);
        sub.setStartDate(System.currentTimeMillis());
        sub.setEndDate(System.currentTimeMillis() + 30L * 24 * 3600 * 1000);
        sub.setAutoRenew(true);
        sub.setStatus("ACTIVE");

        Subscription created = repository.createSubscription(tenantContext, sub);
        assertNotNull(created);
        assertNotNull(created.getId());
        testSubId = created.getId();

        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(testPlanId, created.getPlanId());
        assertEquals("ACTIVE", created.getStatus());
        assertTrue(created.isAutoRenew());

        // Get by ID
        Subscription fetched = repository.getSubscriptionById(tenantContext, testSubId);
        assertNotNull("Subscription must be found by ID", fetched);
        assertEquals(testPlanId, fetched.getPlanId());

        // Get Active Subscription
        Subscription active = repository.getActiveSubscription(tenantContext);
        assertNotNull("Active subscription must be returned", active);
        assertEquals(testSubId, active.getId());

        // Update
        active.setAutoRenew(false);
        Subscription updated = repository.updateSubscription(tenantContext, active);
        assertNotNull(updated);
        assertFalse(updated.isAutoRenew());

        // Cancel
        repository.cancelSubscription(tenantContext, testSubId);
        Subscription cancelled = repository.getSubscriptionById(tenantContext, testSubId);
        assertNotNull(cancelled);
        assertEquals("CANCELLED", cancelled.getStatus());
    }

    @Test
    public void testCrossTenantIsolation() {
        Subscription sub = new Subscription();
        sub.setPlanId(testPlanId);
        sub.setStatus("ACTIVE");

        Subscription created = repository.createSubscription(tenantContext, sub);
        testSubId = created.getId();

        // Reading under other tenant must return null
        Subscription crossResult = repository.getSubscriptionById(otherTenantContext, testSubId);
        assertNull("Cross-tenant subscription read must return null due to tenant filter and RLS", crossResult);
    }
}
