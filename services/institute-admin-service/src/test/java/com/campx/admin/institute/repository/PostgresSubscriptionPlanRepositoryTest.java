package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.model.InstituteModels.SubscriptionPlan;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresSubscriptionPlanRepository} against PostgreSQL (ADM-01 Item 8).
 */
public class PostgresSubscriptionPlanRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresSubscriptionPlanRepository repository;
    private UserSecurityContext platformAdminContext;

    private String testPlanId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        repository = new PostgresSubscriptionPlanRepository(connectionManager);

        // Platform administrator user registered in plat.platform_admins
        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        platformAdminContext = UserSecurityContext.forPlatformAdmin(adminUserId);
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null || testPlanId == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.subscription_plans WHERE id = ?")) {
                ps.setObject(1, UUID.fromString(testPlanId));
                ps.executeUpdate();
            } catch (Exception ignored) {}
        }
    }

    @Test
    public void testCreateAndGetSubscriptionPlanLiveDb() {
        String planCode = "PLAN_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode(planCode);
        plan.setName("Enterprise Campus Plan");
        plan.setBillingCycle("ANNUALLY");
        plan.setPrice(120000.0);
        plan.setCurrencyCode("INR");
        plan.setPublished(true);

        SubscriptionPlan created = repository.createPlan(platformAdminContext, plan);
        assertNotNull(created);
        assertNotNull(created.getId());
        testPlanId = created.getId();

        assertEquals(planCode, created.getPlanCode());
        assertEquals("Enterprise Campus Plan", created.getName());
        assertEquals("ANNUALLY", created.getBillingCycle());
        assertEquals(120000.0, created.getPrice(), 0.01);
        assertTrue(created.isPublished());

        // Get by ID
        SubscriptionPlan fetched = repository.getPlanById(platformAdminContext, testPlanId);
        assertNotNull("Plan must be retrievable by ID", fetched);
        assertEquals(planCode, fetched.getPlanCode());

        // Get by Code
        SubscriptionPlan byCode = repository.getPlanByCode(platformAdminContext, planCode);
        assertNotNull("Plan must be retrievable by code", byCode);
        assertEquals(testPlanId, byCode.getId());

        // Update
        byCode.setName("Updated Enterprise Plan");
        byCode.setPrice(135000.0);
        SubscriptionPlan updated = repository.updatePlan(platformAdminContext, byCode);
        assertNotNull(updated);
        assertEquals("Updated Enterprise Plan", updated.getName());
        assertEquals(135000.0, updated.getPrice(), 0.01);
    }

    @Test(expected = ResourceConflictException.class)
    public void testDuplicatePlanCodeConflict() {
        String planCode = "DUP_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        SubscriptionPlan p1 = new SubscriptionPlan();
        p1.setPlanCode(planCode);
        p1.setName("First Plan");
        p1.setBillingCycle("MONTHLY");
        p1.setPrice(5000.0);

        SubscriptionPlan created = repository.createPlan(platformAdminContext, p1);
        testPlanId = created.getId();

        SubscriptionPlan p2 = new SubscriptionPlan();
        p2.setPlanCode(planCode);
        p2.setName("Second Plan with Same Code");
        p2.setBillingCycle("MONTHLY");
        p2.setPrice(5000.0);

        repository.createPlan(platformAdminContext, p2);
    }

    @Test
    public void testListSubscriptionPlans() {
        String planCode = "LST_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        SubscriptionPlan p = new SubscriptionPlan();
        p.setPlanCode(planCode);
        p.setName("Plan for Listing");
        p.setBillingCycle("MONTHLY");
        p.setPrice(7500.0);
        p.setPublished(true);

        SubscriptionPlan created = repository.createPlan(platformAdminContext, p);
        testPlanId = created.getId();

        List<SubscriptionPlan> plans = repository.listPlans(platformAdminContext);
        assertNotNull(plans);
        assertFalse(plans.isEmpty());

        boolean found = false;
        for (SubscriptionPlan item : plans) {
            if (item.getId().equals(testPlanId)) {
                found = true;
                break;
            }
        }
        assertTrue("Newly created plan must appear in listPlans", found);
    }
}
