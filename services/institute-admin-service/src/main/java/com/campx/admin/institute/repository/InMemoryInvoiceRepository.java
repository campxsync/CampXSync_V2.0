package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.Invoice;
import com.campx.admin.institute.model.InstituteModels.InvoiceLine;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test double implementation of {@link InvoiceRepository}.
 */
public class InMemoryInvoiceRepository implements InvoiceRepository {

    private final Map<String, Invoice> store = new ConcurrentHashMap<>();

    @Override
    public Invoice createInvoice(UserSecurityContext context, Invoice invoice) {
        if (invoice == null) {
            throw new MalformedPayloadException("Invoice payload cannot be null");
        }
        if (invoice.getInvoiceNo() == null || invoice.getInvoiceNo().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'invoiceNo' is required");
        }
        if (invoice.getSubscriptionId() == null || invoice.getSubscriptionId().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'subscriptionId' is required");
        }

        String tenantId = (context != null && context.getTenantId() != null)
                ? context.getTenantId().toString() : "0c914bb2-f63b-472c-a99a-39977112935d";
        String num = invoice.getInvoiceNo().trim();

        for (Invoice existing : store.values()) {
            if (tenantId.equalsIgnoreCase(existing.getTenantId()) && num.equalsIgnoreCase(existing.getInvoiceNo())) {
                throw new ResourceConflictException("Invoice", "invoiceNo", num);
            }
        }

        if (invoice.getId() == null || invoice.getId().trim().isEmpty()) {
            invoice.setId(UUID.randomUUID().toString());
        }
        invoice.setTenantId(tenantId);
        invoice.setInvoiceNo(num);
        invoice.setCreatedAt(System.currentTimeMillis());
        invoice.setUpdatedAt(invoice.getCreatedAt());
        invoice.setRowVersion(1);

        if (invoice.getLines() != null) {
            for (InvoiceLine line : invoice.getLines()) {
                if (line.getId() == null || line.getId().trim().isEmpty()) {
                    line.setId(UUID.randomUUID().toString());
                }
                line.setInvoiceId(invoice.getId());
                line.setTenantId(tenantId);
                line.setCreatedAt(invoice.getCreatedAt());
                line.setUpdatedAt(invoice.getCreatedAt());
                line.setRowVersion(1);
            }
        }

        store.put(invoice.getId(), invoice);
        return invoice;
    }

    @Override
    public Invoice getInvoiceById(UserSecurityContext context, String id) {
        if (id == null) return null;
        Invoice inv = store.get(id);
        if (inv == null) return null;
        String tenantId = (context != null && context.getTenantId() != null) ? context.getTenantId().toString() : null;
        if (tenantId != null && !tenantId.equalsIgnoreCase(inv.getTenantId())) {
            return null; // Tenant isolation
        }
        return inv;
    }

    @Override
    public Invoice getInvoiceByNumber(UserSecurityContext context, String invoiceNo) {
        if (invoiceNo == null) return null;
        String tenantId = (context != null && context.getTenantId() != null) ? context.getTenantId().toString() : null;
        for (Invoice inv : store.values()) {
            if (invoiceNo.equalsIgnoreCase(inv.getInvoiceNo())) {
                if (tenantId == null || tenantId.equalsIgnoreCase(inv.getTenantId())) {
                    return inv;
                }
            }
        }
        return null;
    }

    @Override
    public List<Invoice> listInvoices(UserSecurityContext context) {
        String tenantId = (context != null && context.getTenantId() != null) ? context.getTenantId().toString() : null;
        List<Invoice> list = new ArrayList<>();
        for (Invoice inv : store.values()) {
            if (tenantId == null || tenantId.equalsIgnoreCase(inv.getTenantId())) {
                list.add(inv);
            }
        }
        return list;
    }

    @Override
    public Invoice updateInvoiceStatus(UserSecurityContext context, String id, String status, Long paidAt) {
        Invoice inv = getInvoiceById(context, id);
        if (inv == null) {
            throw new ResourceNotFoundException("Invoice", id);
        }
        inv.setStatus(status);
        if (paidAt != null) {
            inv.setPaidAt(paidAt);
        }
        inv.setUpdatedAt(System.currentTimeMillis());
        inv.setRowVersion(inv.getRowVersion() + 1);
        return inv;
    }

    @Override
    public boolean deleteInvoice(UserSecurityContext context, String id) {
        Invoice inv = getInvoiceById(context, id);
        if (inv == null) return false;
        return store.remove(id) != null;
    }
}
