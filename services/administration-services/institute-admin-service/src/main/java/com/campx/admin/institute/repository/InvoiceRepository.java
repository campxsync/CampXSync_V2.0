package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.Invoice;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;

/**
 * Repository interface for managing {@code plat.invoices} and {@code plat.invoice_lines} persistence (ADM-01 Item 12).
 */
public interface InvoiceRepository {

    /**
     * Persists a new invoice along with its itemized lines.
     *
     * @param context caller security context
     * @param invoice invoice definition with lines
     * @return persisted invoice
     */
    Invoice createInvoice(UserSecurityContext context, Invoice invoice);

    /**
     * Finds an invoice by its unique ID, including lines.
     *
     * @param context caller security context
     * @param id      invoice UUID
     * @return invoice if found, null otherwise
     */
    Invoice getInvoiceById(UserSecurityContext context, String id);

    /**
     * Finds an invoice by invoice number within the tenant.
     *
     * @param context   caller security context
     * @param invoiceNo invoice number (e.g. "INV-2026-0001")
     * @return invoice if found, null otherwise
     */
    Invoice getInvoiceByNumber(UserSecurityContext context, String invoiceNo);

    /**
     * Lists all active invoices for the tenant.
     *
     * @param context caller security context
     * @return list of invoices
     */
    List<Invoice> listInvoices(UserSecurityContext context);

    /**
     * Updates invoice status (e.g. DRAFT -> ISSUED -> PAID / VOID).
     *
     * @param context caller security context
     * @param id      invoice UUID
     * @param status  new status string
     * @param paidAt  optional timestamp when payment was cleared (nullable)
     * @return updated invoice
     */
    Invoice updateInvoiceStatus(UserSecurityContext context, String id, String status, Long paidAt);

    /**
     * Soft-deletes an invoice and its lines.
     *
     * @param context caller security context
     * @param id      invoice UUID
     * @return true if deleted, false if not found
     */
    boolean deleteInvoice(UserSecurityContext context, String id);
}
