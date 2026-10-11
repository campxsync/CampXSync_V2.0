package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.Invoice;
import com.campx.admin.institute.model.InstituteModels.InvoiceLine;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.invoices} and {@code plat.invoice_lines} (ADM-01 Item 12).
 * Executes commercial billing operations with RLS and permission checks.
 */
public class PostgresInvoiceRepository implements InvoiceRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresInvoiceRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresInvoiceRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresInvoiceRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

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

        UUID subUuid;
        try {
            subUuid = UUID.fromString(invoice.getSubscriptionId().trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid subscriptionId UUID: " + invoice.getSubscriptionId());
        }

        UUID invoiceUuid;
        if (invoice.getId() != null && !invoice.getId().trim().isEmpty()) {
            try {
                invoiceUuid = UUID.fromString(invoice.getId().trim());
            } catch (IllegalArgumentException e) {
                invoiceUuid = UUID.randomUUID();
            }
        } else {
            invoiceUuid = UUID.randomUUID();
        }

        String invNum = invoice.getInvoiceNo().trim();
        String status = (invoice.getStatus() != null && !invoice.getStatus().trim().isEmpty())
                ? invoice.getStatus().trim().toUpperCase(Locale.ROOT) : "DRAFT";
        String rawCurrency = (invoice.getCurrencyCode() != null && !invoice.getCurrencyCode().trim().isEmpty())
                ? invoice.getCurrencyCode().trim().toUpperCase(Locale.ROOT) : "INR";
        final String finalCurrency = rawCurrency.length() > 3 ? rawCurrency.substring(0, 3) : rawCurrency;

        if (context == null || context.getTenantId() == null) {
            throw new com.campx.admin.institute.exception.SecurityViolationException("Missing required security context: tenantId");
        }
        UUID finalInvoiceId = invoiceUuid;
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            // Check duplicate invoice number in tenant
            try (PreparedStatement checkPs = conn.prepareStatement(
                    "SELECT id FROM plat.invoices WHERE tenant_id = ? AND invoice_number = ? AND deleted_at IS NULL")) {
                checkPs.setObject(1, tenantId);
                checkPs.setString(2, invNum);
                try (ResultSet rs = checkPs.executeQuery()) {
                    if (rs.next()) {
                        throw new ResourceConflictException("Invoice", "invoiceNo", invNum);
                    }
                }
            }

            // Calculate total amount from lines if provided, or from total field
            double amount = invoice.getTotal();
            if (amount <= 0.0 && invoice.getLines() != null && !invoice.getLines().isEmpty()) {
                amount = invoice.getLines().stream().mapToDouble(InvoiceLine::getAmount).sum();
            }

            String insertSql = "INSERT INTO plat.invoices ("
                    + "id, tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, currency_code, status, paid_at, created_by, row_version, created_at, updated_at"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now()) "
                    + "RETURNING id, tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, currency_code, status, paid_at, row_version, created_at, updated_at";

            Invoice savedInvoice;
            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalInvoiceId);
                ps.setObject(2, tenantId);
                ps.setObject(3, subUuid);
                ps.setString(4, invNum);
                if (invoice.getIssuedOn() > 0) {
                    ps.setDate(5, new java.sql.Date(invoice.getIssuedOn()));
                } else {
                    ps.setDate(5, new java.sql.Date(System.currentTimeMillis()));
                }
                if (invoice.getDueDate() > 0) {
                    ps.setDate(6, new java.sql.Date(invoice.getDueDate()));
                } else {
                    ps.setDate(6, new java.sql.Date(System.currentTimeMillis() + 2592000000L)); // +30 days
                }
                ps.setBigDecimal(7, BigDecimal.valueOf(amount));
                ps.setString(8, finalCurrency);
                ps.setString(9, status);
                if (invoice.getPaidAt() > 0) {
                    ps.setTimestamp(10, new Timestamp(invoice.getPaidAt()));
                } else {
                    ps.setNull(10, Types.TIMESTAMP);
                }
                ps.setObject(11, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        savedInvoice = mapInvoiceRow(rs);
                    } else {
                        throw new RuntimeException("Failed to persist invoice: no row returned");
                    }
                }
            }

            // Persist itemized invoice lines
            List<InvoiceLine> savedLines = new ArrayList<>();
            if (invoice.getLines() != null && !invoice.getLines().isEmpty()) {
                String lineSql = "INSERT INTO plat.invoice_lines ("
                        + "id, tenant_id, invoice_id, description, quantity, unit_price, amount, created_by, row_version, created_at, updated_at"
                        + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, 1, now(), now()) "
                        + "RETURNING id, tenant_id, invoice_id, description, quantity, unit_price, amount, row_version, created_at, updated_at";

                for (InvoiceLine line : invoice.getLines()) {
                    UUID lineId = (line.getId() != null && !line.getId().trim().isEmpty())
                            ? UUID.fromString(line.getId().trim()) : UUID.randomUUID();
                    try (PreparedStatement linePs = conn.prepareStatement(lineSql)) {
                        linePs.setObject(1, lineId);
                        linePs.setObject(2, tenantId);
                        linePs.setObject(3, finalInvoiceId);
                        linePs.setString(4, line.getDescription() != null ? line.getDescription() : "Subscription Item");
                        linePs.setBigDecimal(5, BigDecimal.valueOf(line.getQuantity() > 0 ? line.getQuantity() : 1.0));
                        linePs.setBigDecimal(6, BigDecimal.valueOf(line.getUnitPrice()));
                        linePs.setBigDecimal(7, BigDecimal.valueOf(line.getAmount() > 0 ? line.getAmount() : (line.getQuantity() * line.getUnitPrice())));
                        linePs.setObject(8, context.getUserId());

                        try (ResultSet rs = linePs.executeQuery()) {
                            if (rs.next()) {
                                savedLines.add(mapLineRow(rs));
                            }
                        }
                    }
                }
            }
            savedInvoice.setLines(savedLines);
            return savedInvoice;
        });
    }

    @Override
    public Invoice getInvoiceById(UserSecurityContext context, String id) {
        if (id == null || id.trim().isEmpty()) return null;

        UUID invUuid;
        try {
            invUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (context == null || context.getTenantId() == null) {
            throw new com.campx.admin.institute.exception.SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, currency_code, status, paid_at, row_version, created_at, updated_at "
                    + "FROM plat.invoices WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";

            Invoice invoice = null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, invUuid);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        invoice = mapInvoiceRow(rs);
                    }
                }
            }

            if (invoice != null) {
                invoice.setLines(loadInvoiceLines(conn, invUuid, tenantId));
            }
            return invoice;
        });
    }

    @Override
    public Invoice getInvoiceByNumber(UserSecurityContext context, String invoiceNo) {
        if (invoiceNo == null || invoiceNo.trim().isEmpty()) return null;

        if (context == null || context.getTenantId() == null) {
            throw new com.campx.admin.institute.exception.SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, currency_code, status, paid_at, row_version, created_at, updated_at "
                    + "FROM plat.invoices WHERE invoice_number = ? AND tenant_id = ? AND deleted_at IS NULL LIMIT 1";

            Invoice invoice = null;
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, invoiceNo.trim());
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        invoice = mapInvoiceRow(rs);
                    }
                }
            }

            if (invoice != null) {
                invoice.setLines(loadInvoiceLines(conn, UUID.fromString(invoice.getId()), tenantId));
            }
            return invoice;
        });
    }

    @Override
    public List<Invoice> listInvoices(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new com.campx.admin.institute.exception.SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, currency_code, status, paid_at, row_version, created_at, updated_at "
                    + "FROM plat.invoices WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";

            List<Invoice> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapInvoiceRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public Invoice updateInvoiceStatus(UserSecurityContext context, String id, String status, Long paidAt) {
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Invoice ID required for status update");
        }

        UUID invUuid;
        try {
            invUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid invoice UUID: " + id);
        }

        if (context == null || context.getTenantId() == null) {
            throw new com.campx.admin.institute.exception.SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.invoices SET "
                    + "status = ?, paid_at = ?, updated_by = ?, updated_at = now(), row_version = row_version + 1 "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, subscription_id, invoice_number, issued_on, due_date, amount, currency_code, status, paid_at, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setString(1, status != null ? status.trim().toUpperCase(Locale.ROOT) : "DRAFT");
                if (paidAt != null && paidAt > 0) {
                    ps.setTimestamp(2, new Timestamp(paidAt));
                } else {
                    ps.setNull(2, Types.TIMESTAMP);
                }
                ps.setObject(3, context.getUserId());
                ps.setObject(4, invUuid);
                ps.setObject(5, tenantId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Invoice inv = mapInvoiceRow(rs);
                        inv.setLines(loadInvoiceLines(conn, invUuid, tenantId));
                        return inv;
                    }
                }
            }
            throw new ResourceNotFoundException("Invoice", id);
        });
    }

    @Override
    public boolean deleteInvoice(UserSecurityContext context, String id) {
        if (id == null || id.trim().isEmpty()) return false;

        UUID invUuid;
        try {
            invUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (context == null || context.getTenantId() == null) {
            throw new com.campx.admin.institute.exception.SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            try (PreparedStatement linePs = conn.prepareStatement(
                    "UPDATE plat.invoice_lines SET deleted_at = now(), updated_at = now() WHERE invoice_id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                linePs.setObject(1, invUuid);
                linePs.setObject(2, tenantId);
                linePs.executeUpdate();
            }

            try (PreparedStatement ps = conn.prepareStatement(
                    "UPDATE plat.invoices SET deleted_at = now(), updated_by = ?, updated_at = now(), row_version = row_version + 1 "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL")) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, invUuid);
                ps.setObject(3, tenantId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private List<InvoiceLine> loadInvoiceLines(Connection conn, UUID invoiceId, UUID tenantId) throws SQLException {
        String sql = "SELECT id, tenant_id, invoice_id, description, quantity, unit_price, amount, row_version, created_at, updated_at "
                + "FROM plat.invoice_lines WHERE invoice_id = ? AND tenant_id = ? AND deleted_at IS NULL";
        List<InvoiceLine> lines = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setObject(1, invoiceId);
            ps.setObject(2, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    lines.add(mapLineRow(rs));
                }
            }
        }
        return lines;
    }

    private Invoice mapInvoiceRow(ResultSet rs) throws SQLException {
        Invoice inv = new Invoice();
        Object idObj = rs.getObject("id");
        inv.setId(idObj != null ? idObj.toString() : null);

        Object tidObj = rs.getObject("tenant_id");
        inv.setTenantId(tidObj != null ? tidObj.toString() : null);

        Object subObj = rs.getObject("subscription_id");
        inv.setSubscriptionId(subObj != null ? subObj.toString() : null);

        inv.setInvoiceNo(rs.getString("invoice_number"));
        inv.setStatus(rs.getString("status"));
        inv.setCurrencyCode(rs.getString("currency_code"));

        BigDecimal amt = rs.getBigDecimal("amount");
        inv.setTotal(amt != null ? amt.doubleValue() : 0.0);
        inv.setSubtotal(inv.getTotal());

        java.sql.Date issued = rs.getDate("issued_on");
        if (issued != null) inv.setIssuedOn(issued.getTime());

        java.sql.Date due = rs.getDate("due_date");
        if (due != null) inv.setDueDate(due.getTime());

        Timestamp paid = rs.getTimestamp("paid_at");
        if (paid != null) inv.setPaidAt(paid.getTime());

        Timestamp createdAt = rs.getTimestamp("created_at");
        inv.setCreatedAt(createdAt != null ? createdAt.getTime() : System.currentTimeMillis());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        inv.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : System.currentTimeMillis());

        inv.setRowVersion(rs.getInt("row_version"));
        return inv;
    }

    private InvoiceLine mapLineRow(ResultSet rs) throws SQLException {
        InvoiceLine line = new InvoiceLine();
        Object idObj = rs.getObject("id");
        line.setId(idObj != null ? idObj.toString() : null);

        Object tidObj = rs.getObject("tenant_id");
        line.setTenantId(tidObj != null ? tidObj.toString() : null);

        Object invObj = rs.getObject("invoice_id");
        line.setInvoiceId(invObj != null ? invObj.toString() : null);

        line.setDescription(rs.getString("description"));

        BigDecimal qty = rs.getBigDecimal("quantity");
        line.setQuantity(qty != null ? qty.doubleValue() : 1.0);

        BigDecimal price = rs.getBigDecimal("unit_price");
        line.setUnitPrice(price != null ? price.doubleValue() : 0.0);

        BigDecimal amt = rs.getBigDecimal("amount");
        line.setAmount(amt != null ? amt.doubleValue() : 0.0);

        Timestamp createdAt = rs.getTimestamp("created_at");
        line.setCreatedAt(createdAt != null ? createdAt.getTime() : System.currentTimeMillis());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        line.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : System.currentTimeMillis());

        line.setRowVersion(rs.getInt("row_version"));
        return line;
    }
}
