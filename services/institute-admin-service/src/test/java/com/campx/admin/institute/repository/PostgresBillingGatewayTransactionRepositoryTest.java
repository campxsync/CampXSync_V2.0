package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.model.InstituteModels.BillingGatewayTransaction;
import com.campx.admin.institute.model.InstituteModels.Invoice;
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
 * Live integration tests for {@link PostgresBillingGatewayTransactionRepository} against PostgreSQL (ADM-01 Item 13).
 */
public class PostgresBillingGatewayTransactionRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresSubscriptionPlanRepository planRepository;
    private PostgresSubscriptionRepository subscriptionRepository;
    private PostgresInvoiceRepository invoiceRepository;
    private PostgresBillingGatewayTransactionRepository transactionRepository;

    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private String testPlanId;
    private String testSubId;
    private String testInvoiceId;
    private String testTxnId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        planRepository = new PostgresSubscriptionPlanRepository(connectionManager);
        subscriptionRepository = new PostgresSubscriptionRepository(connectionManager);
        invoiceRepository = new PostgresInvoiceRepository(connectionManager);
        transactionRepository = new PostgresBillingGatewayTransactionRepository(connectionManager);

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

        // 1. Create test plan
        UserSecurityContext platformAdmin = UserSecurityContext.forPlatformAdmin(adminUserId);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode("PLAN_TXN_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        plan.setName("Gateway Txn Plan");
        plan.setBillingCycle("MONTHLY");
        plan.setPrice(15000.0);
        SubscriptionPlan createdPlan = planRepository.createPlan(platformAdmin, plan);
        testPlanId = createdPlan.getId();

        // 2. Create test subscription
        Subscription sub = new Subscription();
        sub.setTenantId(tenantId.toString());
        sub.setPlanId(testPlanId);
        Subscription createdSub = subscriptionRepository.createSubscription(tenantContext, sub);
        testSubId = createdSub.getId();

        // 3. Create test invoice
        Invoice inv = new Invoice();
        inv.setInvoiceNo("INV-TXN-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        inv.setSubscriptionId(testSubId);
        inv.setTotal(15000.0);
        Invoice createdInv = invoiceRepository.createInvoice(tenantContext, inv);
        testInvoiceId = createdInv.getId();
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
            if (testTxnId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.gateway_transactions WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testTxnId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
            if (testInvoiceId != null) {
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.invoice_lines WHERE invoice_id = ?")) {
                    ps.setObject(1, UUID.fromString(testInvoiceId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
                try (PreparedStatement ps = conn.prepareStatement("DELETE FROM plat.invoices WHERE id = ?")) {
                    ps.setObject(1, UUID.fromString(testInvoiceId));
                    ps.executeUpdate();
                } catch (Exception ignored) {}
            }
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
    public void testCreateAndGetTransactionLiveDb() {
        String payRef = "pay_" + UUID.randomUUID().toString().substring(0, 10);

        BillingGatewayTransaction txn = new BillingGatewayTransaction();
        txn.setInvoiceId(testInvoiceId);
        txn.setGateway("RAZORPAY");
        txn.setGatewayTransactionId(payRef);
        txn.setAmount(15000.0);
        txn.setCurrency("INR");
        txn.setStatus("PENDING");
        txn.setRawReference("{\"method\":\"upi\",\"bank\":\"HDFC\"}");

        BillingGatewayTransaction created = transactionRepository.createTransaction(tenantContext, txn);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(testInvoiceId, created.getInvoiceId());
        assertEquals("RAZORPAY", created.getGateway());
        assertEquals(payRef, created.getGatewayTransactionId());
        assertEquals(15000.0, created.getAmount(), 0.01);
        assertEquals("PENDING", created.getStatus());
        assertEquals(1, created.getRowVersion());
        testTxnId = created.getId();

        // Retrieve by ID
        BillingGatewayTransaction fetched = transactionRepository.getTransactionById(tenantContext, created.getId());
        assertNotNull(fetched);
        assertEquals(created.getId(), fetched.getId());
        assertEquals(payRef, fetched.getGatewayTransactionId());

        // Retrieve by Reference
        BillingGatewayTransaction fetchedByRef = transactionRepository.getTransactionByReference(tenantContext, "RAZORPAY", payRef);
        assertNotNull(fetchedByRef);
        assertEquals(created.getId(), fetchedByRef.getId());
    }

    @Test(expected = ResourceConflictException.class)
    public void testDuplicateGatewayReferenceConflict() {
        String payRef = "pay_dup_" + UUID.randomUUID().toString().substring(0, 6);

        BillingGatewayTransaction txn1 = new BillingGatewayTransaction();
        txn1.setInvoiceId(testInvoiceId);
        txn1.setGateway("STRIPE");
        txn1.setGatewayTransactionId(payRef);
        txn1.setAmount(5000.0);
        BillingGatewayTransaction created = transactionRepository.createTransaction(tenantContext, txn1);
        testTxnId = created.getId();

        BillingGatewayTransaction txn2 = new BillingGatewayTransaction();
        txn2.setInvoiceId(testInvoiceId);
        txn2.setGateway("STRIPE");
        txn2.setGatewayTransactionId(payRef);
        txn2.setAmount(6000.0);
        transactionRepository.createTransaction(tenantContext, txn2);
    }

    @Test
    public void testListTransactionsByInvoice() {
        BillingGatewayTransaction txn1 = new BillingGatewayTransaction();
        txn1.setInvoiceId(testInvoiceId);
        txn1.setGateway("RAZORPAY");
        txn1.setGatewayTransactionId("pay_part1_" + UUID.randomUUID().toString().substring(0, 6));
        txn1.setAmount(7500.0);
        transactionRepository.createTransaction(tenantContext, txn1);

        BillingGatewayTransaction txn2 = new BillingGatewayTransaction();
        txn2.setInvoiceId(testInvoiceId);
        txn2.setGateway("RAZORPAY");
        txn2.setGatewayTransactionId("pay_part2_" + UUID.randomUUID().toString().substring(0, 6));
        txn2.setAmount(7500.0);
        transactionRepository.createTransaction(tenantContext, txn2);

        List<BillingGatewayTransaction> list = transactionRepository.listTransactionsByInvoice(tenantContext, testInvoiceId);
        assertTrue(list.size() >= 2);
    }

    @Test
    public void testUpdateTransactionStatusAndReconciliation() {
        BillingGatewayTransaction txn = new BillingGatewayTransaction();
        txn.setInvoiceId(testInvoiceId);
        txn.setGateway("RAZORPAY");
        txn.setGatewayTransactionId("pay_reconcile_" + UUID.randomUUID().toString().substring(0, 6));
        txn.setAmount(10000.0);
        txn.setStatus("PENDING");
        BillingGatewayTransaction created = transactionRepository.createTransaction(tenantContext, txn);
        testTxnId = created.getId();

        long now = System.currentTimeMillis();
        BillingGatewayTransaction updated = transactionRepository.updateTransactionStatus(
                tenantContext, created.getId(), "SUCCESS", now, "{\"status\":\"captured\"}");
        assertNotNull(updated);
        assertEquals("SUCCESS", updated.getStatus());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testTenantIsolationRLS() {
        BillingGatewayTransaction txn = new BillingGatewayTransaction();
        txn.setInvoiceId(testInvoiceId);
        txn.setGateway("RAZORPAY");
        txn.setGatewayTransactionId("pay_iso_" + UUID.randomUUID().toString().substring(0, 6));
        txn.setAmount(10000.0);
        BillingGatewayTransaction created = transactionRepository.createTransaction(tenantContext, txn);
        testTxnId = created.getId();

        // Cross-tenant read should return null
        BillingGatewayTransaction crossTenant = transactionRepository.getTransactionById(otherTenantContext, created.getId());
        assertNull("Cross-tenant access must be blocked by PostgreSQL RLS", crossTenant);
    }

    @Test
    public void testDeleteTransactionSoftDelete() {
        BillingGatewayTransaction txn = new BillingGatewayTransaction();
        txn.setInvoiceId(testInvoiceId);
        txn.setGateway("RAZORPAY");
        txn.setGatewayTransactionId("pay_del_" + UUID.randomUUID().toString().substring(0, 6));
        txn.setAmount(2000.0);
        BillingGatewayTransaction created = transactionRepository.createTransaction(tenantContext, txn);

        boolean deleted = transactionRepository.deleteTransaction(tenantContext, created.getId());
        assertTrue(deleted);

        BillingGatewayTransaction fetched = transactionRepository.getTransactionById(tenantContext, created.getId());
        assertNull(fetched);
    }
}
