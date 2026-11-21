package com.campx.iadm.billing;

import com.campx.iadm.billing.config.DatabaseConfig;
import com.campx.iadm.billing.config.DatabaseConnectionManager;
import com.campx.iadm.billing.exception.InstituteAdminException;
import com.campx.iadm.billing.security.UserSecurityContext;
import com.campx.iadm.billing.service.Iadm04DomainService;
import org.junit.AfterClass;
import org.junit.Assume;
import org.junit.BeforeClass;
import org.junit.Test;

import java.math.BigDecimal;
import java.util.Map;
import java.util.UUID;

import static org.junit.Assert.*;

/**
 * Closes IADM-04-US-002 / US-005 pending depth on DEV.
 */
public class Iadm04PendingCloseLiveDevTest {

    private static final UUID TENANT = UUID.fromString("a0000000-0000-0000-0000-000000000001");
    private static final UUID PLATFORM = UUID.fromString("54e8308c-ada3-4065-a9fe-51814af6b971");

    private static boolean dbReady;
    private static Iadm04DomainService service;

    @BeforeClass
    public static void setup() {
        DatabaseConfig cfg = DatabaseConfig.load();
        dbReady = cfg.getJdbcUrl() != null && !cfg.getJdbcUrl().trim().isEmpty()
                && cfg.getPassword() != null && !cfg.getPassword().isEmpty();
        Assume.assumeTrue("DEV JDBC not configured", dbReady);
        DatabaseConnectionManager.getInstance().initialize();
        service = new Iadm04DomainService();
    }

    @AfterClass
    public static void teardown() {
        if (dbReady) DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void assignTransitionIssueAndReconcile() {
        UserSecurityContext platform = UserSecurityContext.forPlatformAdmin(PLATFORM);
        String code = "WF4P" + (System.currentTimeMillis() % 100000);
        Map<String, Object> plan = service.createPlan(platform, code, "Wave F Pending Plan",
                "MONTHLY", new BigDecimal("10.00"), "INR");
        assertNotNull(plan.get("id"));
        service.publishPlan(platform, code);

        Map<String, Object> sub = service.assignSubscription(platform, TENANT, code, "TRIAL", null);
        UUID subId = UUID.fromString(sub.get("subscriptionId").toString());
        assertEquals("TRIAL", sub.get("status"));

        try {
            service.transitionSubscription(platform, subId, "PAST_DUE");
            fail("TRIAL→PAST_DUE must be illegal");
        } catch (InstituteAdminException ex) {
            assertEquals("IADM04_ILLEGAL_TRANSITION", ex.getErrorCode());
        }

        Map<String, Object> active = service.transitionSubscription(platform, subId, "ACTIVE");
        assertEquals("ACTIVE", active.get("status"));

        Map<String, Object> inv = service.issueInvoice(platform, TENANT, subId,
                new BigDecimal("10.00"), "INR", "INV-WF4-" + code);
        assertEquals("ISSUED", inv.get("status"));
        UUID invoiceId = UUID.fromString(inv.get("invoiceId").toString());

        Map<String, Object> paid = service.reconcileInvoice(platform, invoiceId, "PAID");
        assertEquals("PAID", paid.get("status"));

        // cleanup soft
        service.transitionSubscription(platform, subId, "CANCELLED");
        service.retirePlan(platform, code);
    }
}
