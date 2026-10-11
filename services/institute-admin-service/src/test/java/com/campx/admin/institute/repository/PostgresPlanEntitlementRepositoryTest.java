package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.model.InstituteModels.PlanEntitlement;
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
 * Live integration tests for {@link PostgresPlanEntitlementRepository} against PostgreSQL (ADM-01 Item 11).
 */
public class PostgresPlanEntitlementRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresSubscriptionPlanRepository planRepository;
    private PostgresPlanEntitlementRepository entitlementRepository;
    private UserSecurityContext platformAdminContext;

    private String testPlanId;
    private String testEntitlementId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        planRepository = new PostgresSubscriptionPlanRepository(connectionManager);
        entitlementRepository = new PostgresPlanEntitlementRepository(connectionManager);

        // Platform administrator user registered in plat.platform_admins
        UUID adminUserId = UUID.fromString("ba77b8f7-ab32-46bd-85d2-5aade3526880");
        platformAdminContext = UserSecurityContext.forPlatformAdmin(adminUserId);

        // Seed a parent plan for entitlement testing
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode("PLAN_ENT_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        plan.setName("Entitlement Test Plan");
        plan.setBillingCycle("MONTHLY");
        plan.setPrice(4999.0);
        SubscriptionPlan createdPlan = planRepository.createPlan(platformAdminContext, plan);
        testPlanId = createdPlan.getId();
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            if (testPlanId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.plan_entitlements WHERE plan_id = ?")) {
                    ps.setObject(1, UUID.fromString(testPlanId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.subscription_plans WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testPlanId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
        }
    }

    @Test
    public void testCreateAndGetPlanEntitlementLiveDb() {
        PlanEntitlement ent = new PlanEntitlement();
        ent.setPlanId(testPlanId);
        ent.setEntitlementKey("MAX_STUDENTS");
        ent.setLimitValue(5000L);
        ent.setEnabled(true);

        PlanEntitlement created = entitlementRepository.createEntitlement(platformAdminContext, ent);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(testPlanId, created.getPlanId());
        assertEquals("MAX_STUDENTS", created.getEntitlementKey());
        assertEquals(Long.valueOf(5000L), created.getLimitValue());
        assertTrue(created.isEnabled());
        assertEquals(1, created.getRowVersion());
        testEntitlementId = created.getId();

        PlanEntitlement fetched = entitlementRepository.getEntitlementById(platformAdminContext, created.getId());
        assertNotNull(fetched);
        assertEquals(created.getId(), fetched.getId());
        assertEquals("MAX_STUDENTS", fetched.getEntitlementKey());
        assertEquals(Long.valueOf(5000L), fetched.getLimitValue());
    }

    @Test(expected = ResourceConflictException.class)
    public void testDuplicateEntitlementKeyConflict() {
        PlanEntitlement ent1 = new PlanEntitlement();
        ent1.setPlanId(testPlanId);
        ent1.setEntitlementKey("STORAGE_GB");
        ent1.setLimitValue(100L);
        entitlementRepository.createEntitlement(platformAdminContext, ent1);

        // Attempt second with identical key under same plan
        PlanEntitlement ent2 = new PlanEntitlement();
        ent2.setPlanId(testPlanId);
        ent2.setEntitlementKey("STORAGE_GB");
        ent2.setLimitValue(200L);
        entitlementRepository.createEntitlement(platformAdminContext, ent2);
    }

    @Test
    public void testListEntitlementsByPlan() {
        PlanEntitlement ent1 = new PlanEntitlement();
        ent1.setPlanId(testPlanId);
        ent1.setEntitlementKey("FEATURE_AI_GRADING");
        ent1.setLimitValue(null);
        ent1.setEnabled(true);
        entitlementRepository.createEntitlement(platformAdminContext, ent1);

        PlanEntitlement ent2 = new PlanEntitlement();
        ent2.setPlanId(testPlanId);
        ent2.setEntitlementKey("MAX_COLLEGES");
        ent2.setLimitValue(10L);
        ent2.setEnabled(true);
        entitlementRepository.createEntitlement(platformAdminContext, ent2);

        List<PlanEntitlement> list = entitlementRepository.listEntitlementsByPlan(platformAdminContext, testPlanId);
        assertTrue(list.size() >= 2);
    }

    @Test
    public void testUpdateEntitlementOptimisticConcurrency() {
        PlanEntitlement ent = new PlanEntitlement();
        ent.setPlanId(testPlanId);
        ent.setEntitlementKey("MAX_FACULTY");
        ent.setLimitValue(50L);
        ent.setEnabled(true);

        PlanEntitlement created = entitlementRepository.createEntitlement(platformAdminContext, ent);
        created.setLimitValue(150L);
        created.setEnabled(false);

        PlanEntitlement updated = entitlementRepository.updateEntitlement(platformAdminContext, created);
        assertEquals(Long.valueOf(150L), updated.getLimitValue());
        assertFalse(updated.isEnabled());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testDeleteEntitlementSoftDelete() {
        PlanEntitlement ent = new PlanEntitlement();
        ent.setPlanId(testPlanId);
        ent.setEntitlementKey("DEPRECATED_FEATURE");
        ent.setLimitValue(1L);

        PlanEntitlement created = entitlementRepository.createEntitlement(platformAdminContext, ent);
        boolean deleted = entitlementRepository.deleteEntitlement(platformAdminContext, created.getId());
        assertTrue(deleted);

        PlanEntitlement fetched = entitlementRepository.getEntitlementById(platformAdminContext, created.getId());
        assertNull(fetched);
    }
}
