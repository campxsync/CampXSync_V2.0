package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConfig;
import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.model.InstituteModels.Invoice;
import com.campx.admin.institute.model.InstituteModels.InvoiceLine;
import com.campx.admin.institute.model.InstituteModels.Subscription;
import com.campx.admin.institute.model.InstituteModels.SubscriptionPlan;
import com.campx.admin.institute.security.UserSecurityContext;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeNotNull;

/**
 * Live integration tests for {@link PostgresInvoiceRepository} against PostgreSQL (ADM-01 Item 12).
 */
public class PostgresInvoiceRepositoryTest {

    private DatabaseConnectionManager connectionManager;
    private PostgresSubscriptionPlanRepository planRepository;
    private PostgresSubscriptionRepository subscriptionRepository;
    private PostgresInvoiceRepository invoiceRepository;

    private UserSecurityContext tenantContext;
    private UserSecurityContext otherTenantContext;

    private UUID tenantId;
    private String testPlanId;
    private String testSubId;
    private String testInvoiceId;

    @Before
    public void setUp() throws Exception {
        DatabaseConfig config = DatabaseConfig.load();
        assumeNotNull("Live integration tests require SUPABASE_JDBC_URL or CAMPX_JDBC_URL", config.getJdbcUrl());

        connectionManager = DatabaseConnectionManager.getInstance();
        planRepository = new PostgresSubscriptionPlanRepository(connectionManager);
        subscriptionRepository = new PostgresSubscriptionRepository(connectionManager);
        invoiceRepository = new PostgresInvoiceRepository(connectionManager);

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

        // Create prerequisite plan
        UserSecurityContext platformAdmin = UserSecurityContext.forPlatformAdmin(adminUserId);
        SubscriptionPlan plan = new SubscriptionPlan();
        plan.setPlanCode("PLAN_INV_" + UUID.randomUUID().toString().substring(0, 6).toUpperCase());
        plan.setName("Invoice Test Plan");
        plan.setBillingCycle("MONTHLY");
        plan.setPrice(12000.0);
        SubscriptionPlan createdPlan = planRepository.createPlan(platformAdmin, plan);
        testPlanId = createdPlan.getId();

        // Create prerequisite subscription
        Subscription sub = new Subscription();
        sub.setTenantId(tenantId.toString());
        sub.setPlanId(testPlanId);
        sub.setSeatCount(100);
        Subscription createdSub = subscriptionRepository.createSubscription(tenantContext, sub);
        testSubId = createdSub.getId();
    }

    @After
    public void tearDown() throws Exception {
        if (connectionManager == null) return;
        try (Connection conn = connectionManager.getConnection()) {
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
    public void testCreateAndGetInvoiceWithLinesLiveDb() {
        String invNum = "INV-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase();

        Invoice invoice = new Invoice();
        invoice.setInvoiceNo(invNum);
        invoice.setSubscriptionId(testSubId);
        invoice.setStatus("ISSUED");
        invoice.setCurrencyCode("INR");

        List<InvoiceLine> lines = new ArrayList<>();
        InvoiceLine line1 = new InvoiceLine();
        line1.setDescription("Campus Enterprise Base Subscription");
        line1.setQuantity(1.0);
        line1.setUnitPrice(10000.0);
        line1.setAmount(10000.0);
        lines.add(line1);

        InvoiceLine line2 = new InvoiceLine();
        line2.setDescription("Additional Student Seat Licenses (x20)");
        line2.setQuantity(20.0);
        line2.setUnitPrice(100.0);
        line2.setAmount(2000.0);
        lines.add(line2);

        invoice.setLines(lines);

        Invoice created = invoiceRepository.createInvoice(tenantContext, invoice);
        assertNotNull(created);
        assertNotNull(created.getId());
        assertEquals(invNum, created.getInvoiceNo());
        assertEquals(tenantId.toString(), created.getTenantId());
        assertEquals(12000.0, created.getTotal(), 0.01);
        assertEquals("ISSUED", created.getStatus());
        assertNotNull(created.getLines());
        assertEquals(2, created.getLines().size());
        testInvoiceId = created.getId();

        // Retrieve by ID
        Invoice fetched = invoiceRepository.getInvoiceById(tenantContext, created.getId());
        assertNotNull(fetched);
        assertEquals(created.getId(), fetched.getId());
        assertEquals(12000.0, fetched.getTotal(), 0.01);
        assertEquals(2, fetched.getLines().size());

        // Retrieve by Invoice Number
        Invoice fetchedByNo = invoiceRepository.getInvoiceByNumber(tenantContext, invNum);
        assertNotNull(fetchedByNo);
        assertEquals(created.getId(), fetchedByNo.getId());
    }

    @Test(expected = ResourceConflictException.class)
    public void testDuplicateInvoiceNumberConflict() {
        String invNum = "INV-DUP-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Invoice inv1 = new Invoice();
        inv1.setInvoiceNo(invNum);
        inv1.setSubscriptionId(testSubId);
        inv1.setTotal(5000.0);
        Invoice created = invoiceRepository.createInvoice(tenantContext, inv1);
        testInvoiceId = created.getId();

        Invoice inv2 = new Invoice();
        inv2.setInvoiceNo(invNum);
        inv2.setSubscriptionId(testSubId);
        inv2.setTotal(6000.0);
        invoiceRepository.createInvoice(tenantContext, inv2);
    }

    @Test
    public void testUpdateInvoiceStatusAndPayment() {
        String invNum = "INV-PAY-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Invoice inv = new Invoice();
        inv.setInvoiceNo(invNum);
        inv.setSubscriptionId(testSubId);
        inv.setStatus("ISSUED");
        inv.setTotal(8500.0);
        Invoice created = invoiceRepository.createInvoice(tenantContext, inv);
        testInvoiceId = created.getId();

        long now = System.currentTimeMillis();
        Invoice updated = invoiceRepository.updateInvoiceStatus(tenantContext, created.getId(), "PAID", now);
        assertNotNull(updated);
        assertEquals("PAID", updated.getStatus());
        assertNotNull(updated.getPaidAt());
        assertEquals(2, updated.getRowVersion());
    }

    @Test
    public void testTenantIsolationRLS() {
        String invNum = "INV-ISO-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Invoice inv = new Invoice();
        inv.setInvoiceNo(invNum);
        inv.setSubscriptionId(testSubId);
        inv.setTotal(7000.0);
        Invoice created = invoiceRepository.createInvoice(tenantContext, inv);
        testInvoiceId = created.getId();

        // Cross-tenant read should return null / empty
        Invoice crossTenant = invoiceRepository.getInvoiceById(otherTenantContext, created.getId());
        assertNull("Cross-tenant access must be prevented by RLS", crossTenant);

        List<Invoice> otherInvoices = invoiceRepository.listInvoices(otherTenantContext);
        for (Invoice item : otherInvoices) {
            assertNotEquals(created.getId(), item.getId());
        }
    }

    @Test
    public void testDeleteInvoiceSoftDelete() {
        String invNum = "INV-DEL-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();

        Invoice inv = new Invoice();
        inv.setInvoiceNo(invNum);
        inv.setSubscriptionId(testSubId);
        inv.setTotal(3000.0);
        Invoice created = invoiceRepository.createInvoice(tenantContext, inv);

        boolean deleted = invoiceRepository.deleteInvoice(tenantContext, created.getId());
        assertTrue(deleted);

        Invoice fetched = invoiceRepository.getInvoiceById(tenantContext, created.getId());
        assertNull(fetched);
    }
}
