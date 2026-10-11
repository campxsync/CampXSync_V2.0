package com.campx.admin.institute.repository;

import com.campx.admin.institute.config.DatabaseConnectionManager;
import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.exception.SecurityViolationException;
import com.campx.admin.institute.model.InstituteModels.BillingGatewayTransaction;
import com.campx.admin.institute.security.UserSecurityContext;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.math.BigDecimal;
import java.sql.*;
import java.util.*;

/**
 * PostgreSQL JDBC repository for {@code plat.gateway_transactions} (ADM-01 Item 13).
 * Enforces RLS, tenant isolation, and optimistic concurrency.
 */
public class PostgresBillingGatewayTransactionRepository implements BillingGatewayTransactionRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresBillingGatewayTransactionRepository.class);

    private final DatabaseConnectionManager connectionManager;

    public PostgresBillingGatewayTransactionRepository() {
        this(DatabaseConnectionManager.getInstance());
    }

    public PostgresBillingGatewayTransactionRepository(DatabaseConnectionManager connectionManager) {
        this.connectionManager = connectionManager;
    }

    @Override
    public BillingGatewayTransaction createTransaction(UserSecurityContext context, BillingGatewayTransaction txn) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        if (txn == null) {
            throw new MalformedPayloadException("Transaction payload cannot be null");
        }
        if (txn.getGateway() == null || txn.getGateway().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'gateway' is required");
        }
        if (txn.getAmount() < 0.0) {
            throw new MalformedPayloadException("Transaction amount cannot be negative");
        }

        UUID invoiceUuid = null;
        if (txn.getInvoiceId() != null && !txn.getInvoiceId().trim().isEmpty()) {
            try {
                invoiceUuid = UUID.fromString(txn.getInvoiceId().trim());
            } catch (IllegalArgumentException e) {
                throw new MalformedPayloadException("Invalid invoiceId UUID format: " + txn.getInvoiceId());
            }
        }

        UUID txnUuid;
        if (txn.getId() != null && !txn.getId().trim().isEmpty()) {
            try {
                txnUuid = UUID.fromString(txn.getId().trim());
            } catch (IllegalArgumentException e) {
                txnUuid = UUID.randomUUID();
            }
        } else {
            txnUuid = UUID.randomUUID();
        }

        String gw = txn.getGateway().trim().toUpperCase(Locale.ROOT);
        String ref = txn.getGatewayTransactionId() != null && !txn.getGatewayTransactionId().trim().isEmpty()
                ? txn.getGatewayTransactionId().trim() : null;
        String status = txn.getStatus() != null && !txn.getStatus().trim().isEmpty()
                ? txn.getStatus().trim().toUpperCase(Locale.ROOT) : "PENDING";
        String rawCurrency = txn.getCurrency() != null && !txn.getCurrency().trim().isEmpty()
                ? txn.getCurrency().trim().toUpperCase(Locale.ROOT) : "INR";
        final String finalCurrency = rawCurrency.length() > 3 ? rawCurrency.substring(0, 3) : rawCurrency;

        UUID finalTxnId = txnUuid;
        UUID finalInvoiceId = invoiceUuid;
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            // Check duplicate gateway reference under tenant
            if (ref != null) {
                try (PreparedStatement checkPs = conn.prepareStatement(
                        "SELECT id FROM plat.gateway_transactions WHERE tenant_id = ? AND gateway = ? AND gateway_ref = ? AND deleted_at IS NULL")) {
                    checkPs.setObject(1, tenantId);
                    checkPs.setString(2, gw);
                    checkPs.setString(3, ref);
                    try (ResultSet rs = checkPs.executeQuery()) {
                        if (rs.next()) {
                            throw new ResourceConflictException("BillingGatewayTransaction", "gatewayRef", ref);
                        }
                    }
                }
            }

            String insertSql = "INSERT INTO plat.gateway_transactions ("
                    + "id, tenant_id, invoice_id, gateway, gateway_ref, amount, currency_code, status, raw_response, created_by, row_version, created_at, updated_at"
                    + ") VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?::jsonb, ?, 1, now(), now()) "
                    + "RETURNING id, tenant_id, invoice_id, gateway, gateway_ref, amount, currency_code, status, raw_response, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                ps.setObject(1, finalTxnId);
                ps.setObject(2, tenantId);
                ps.setObject(3, finalInvoiceId);
                ps.setString(4, gw);
                ps.setString(5, ref);
                ps.setBigDecimal(6, BigDecimal.valueOf(txn.getAmount()));
                ps.setString(7, finalCurrency);
                ps.setString(8, status);
                if (txn.getRawReference() != null && !txn.getRawReference().trim().isEmpty()) {
                    ps.setString(9, txn.getRawReference().trim());
                } else {
                    ps.setNull(9, Types.VARCHAR);
                }
                ps.setObject(10, context.getUserId());

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            throw new RuntimeException("Failed to persist gateway transaction: no row returned");
        });
    }

    @Override
    public BillingGatewayTransaction getTransactionById(UserSecurityContext context, String id) {
        if (id == null || id.trim().isEmpty()) return null;

        UUID txnUuid;
        try {
            txnUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return null;
        }

        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, invoice_id, gateway, gateway_ref, amount, currency_code, status, raw_response, row_version, created_at, updated_at "
                    + "FROM plat.gateway_transactions WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, txnUuid);
                ps.setObject(2, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public BillingGatewayTransaction getTransactionByReference(UserSecurityContext context, String gateway, String gatewayRef) {
        if (gateway == null || gatewayRef == null || gatewayRef.trim().isEmpty()) return null;

        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, invoice_id, gateway, gateway_ref, amount, currency_code, status, raw_response, row_version, created_at, updated_at "
                    + "FROM plat.gateway_transactions WHERE tenant_id = ? AND gateway = ? AND gateway_ref = ? AND deleted_at IS NULL LIMIT 1";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setString(2, gateway.trim().toUpperCase(Locale.ROOT));
                ps.setString(3, gatewayRef.trim());
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        return mapRow(rs);
                    }
                }
            }
            return null;
        });
    }

    @Override
    public List<BillingGatewayTransaction> listTransactionsByInvoice(UserSecurityContext context, String invoiceId) {
        if (invoiceId == null || invoiceId.trim().isEmpty()) return Collections.emptyList();

        UUID invUuid;
        try {
            invUuid = UUID.fromString(invoiceId.trim());
        } catch (IllegalArgumentException e) {
            return Collections.emptyList();
        }

        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, invoice_id, gateway, gateway_ref, amount, currency_code, status, raw_response, row_version, created_at, updated_at "
                    + "FROM plat.gateway_transactions WHERE tenant_id = ? AND invoice_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
            List<BillingGatewayTransaction> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                ps.setObject(2, invUuid);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public List<BillingGatewayTransaction> listTransactions(UserSecurityContext context) {
        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "SELECT id, tenant_id, invoice_id, gateway, gateway_ref, amount, currency_code, status, raw_response, row_version, created_at, updated_at "
                    + "FROM plat.gateway_transactions WHERE tenant_id = ? AND deleted_at IS NULL ORDER BY created_at DESC";
            List<BillingGatewayTransaction> list = new ArrayList<>();
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, tenantId);
                try (ResultSet rs = ps.executeQuery()) {
                    while (rs.next()) {
                        list.add(mapRow(rs));
                    }
                }
            }
            return list;
        });
    }

    @Override
    public BillingGatewayTransaction updateTransactionStatus(UserSecurityContext context, String id, String status, Long reconciledAt, String rawResponse) {
        if (id == null || id.trim().isEmpty()) {
            throw new MalformedPayloadException("Transaction ID required for update");
        }

        UUID txnUuid;
        try {
            txnUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            throw new MalformedPayloadException("Invalid transaction UUID: " + id);
        }

        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String updateSql = "UPDATE plat.gateway_transactions SET "
                    + "status = ?, raw_response = COALESCE(?::jsonb, raw_response), updated_by = ?, updated_at = now(), row_version = row_version + 1 "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL "
                    + "RETURNING id, tenant_id, invoice_id, gateway, gateway_ref, amount, currency_code, status, raw_response, row_version, created_at, updated_at";

            try (PreparedStatement ps = conn.prepareStatement(updateSql)) {
                ps.setString(1, status != null ? status.trim().toUpperCase(Locale.ROOT) : "PENDING");
                if (rawResponse != null && !rawResponse.trim().isEmpty()) {
                    ps.setString(2, rawResponse.trim());
                } else {
                    ps.setNull(2, Types.VARCHAR);
                }
                ps.setObject(3, context.getUserId());
                ps.setObject(4, txnUuid);
                ps.setObject(5, tenantId);

                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        BillingGatewayTransaction updated = mapRow(rs);
                        if (reconciledAt != null) {
                            updated.setReconciledAt(reconciledAt);
                        }
                        return updated;
                    }
                }
            }
            throw new ResourceNotFoundException("BillingGatewayTransaction", id);
        });
    }

    @Override
    public boolean deleteTransaction(UserSecurityContext context, String id) {
        if (id == null || id.trim().isEmpty()) return false;

        UUID txnUuid;
        try {
            txnUuid = UUID.fromString(id.trim());
        } catch (IllegalArgumentException e) {
            return false;
        }

        if (context == null || context.getTenantId() == null) {
            throw new SecurityViolationException("Missing required security context: tenantId");
        }
        UUID tenantId = context.getTenantId();

        return context.executeInTransaction(connectionManager, conn -> {
            String sql = "UPDATE plat.gateway_transactions SET deleted_at = now(), updated_by = ?, updated_at = now(), row_version = row_version + 1 "
                    + "WHERE id = ? AND tenant_id = ? AND deleted_at IS NULL";
            try (PreparedStatement ps = conn.prepareStatement(sql)) {
                ps.setObject(1, context.getUserId());
                ps.setObject(2, txnUuid);
                ps.setObject(3, tenantId);
                return ps.executeUpdate() > 0;
            }
        });
    }

    private BillingGatewayTransaction mapRow(ResultSet rs) throws SQLException {
        BillingGatewayTransaction txn = new BillingGatewayTransaction();
        Object idObj = rs.getObject("id");
        txn.setId(idObj != null ? idObj.toString() : null);

        Object tidObj = rs.getObject("tenant_id");
        txn.setTenantId(tidObj != null ? tidObj.toString() : null);

        Object invObj = rs.getObject("invoice_id");
        txn.setInvoiceId(invObj != null ? invObj.toString() : null);

        txn.setGateway(rs.getString("gateway"));
        txn.setGatewayTransactionId(rs.getString("gateway_ref"));

        BigDecimal amt = rs.getBigDecimal("amount");
        txn.setAmount(amt != null ? amt.doubleValue() : 0.0);

        txn.setCurrency(rs.getString("currency_code"));
        txn.setStatus(rs.getString("status"));
        txn.setRawReference(rs.getString("raw_response"));

        Timestamp createdAt = rs.getTimestamp("created_at");
        txn.setCreatedAt(createdAt != null ? createdAt.getTime() : System.currentTimeMillis());

        Timestamp updatedAt = rs.getTimestamp("updated_at");
        txn.setUpdatedAt(updatedAt != null ? updatedAt.getTime() : System.currentTimeMillis());

        txn.setRowVersion(rs.getInt("row_version"));
        return txn;
    }
}
