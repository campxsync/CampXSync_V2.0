package com.campx.admin.institute.repository;

import com.campx.admin.institute.exception.MalformedPayloadException;
import com.campx.admin.institute.exception.ResourceConflictException;
import com.campx.admin.institute.exception.ResourceNotFoundException;
import com.campx.admin.institute.model.InstituteModels.BillingGatewayTransaction;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * In-memory test double implementation of {@link BillingGatewayTransactionRepository}.
 */
public class InMemoryBillingGatewayTransactionRepository implements BillingGatewayTransactionRepository {

    private final Map<String, BillingGatewayTransaction> store = new ConcurrentHashMap<>();

    @Override
    public BillingGatewayTransaction createTransaction(UserSecurityContext context, BillingGatewayTransaction txn) {
        if (txn == null) {
            throw new MalformedPayloadException("Transaction payload cannot be null");
        }
        if (txn.getGateway() == null || txn.getGateway().trim().isEmpty()) {
            throw new MalformedPayloadException("Mandatory field 'gateway' is required");
        }
        if (txn.getAmount() < 0.0) {
            throw new MalformedPayloadException("Transaction amount cannot be negative");
        }

        String tenantId = (context != null && context.getTenantId() != null)
                ? context.getTenantId().toString() : "0c914bb2-f63b-472c-a99a-39977112935d";
        String gw = txn.getGateway().trim().toUpperCase(Locale.ROOT);
        String ref = txn.getGatewayTransactionId() != null ? txn.getGatewayTransactionId().trim() : null;

        if (ref != null) {
            for (BillingGatewayTransaction existing : store.values()) {
                if (tenantId.equalsIgnoreCase(existing.getTenantId())
                        && gw.equalsIgnoreCase(existing.getGateway())
                        && ref.equalsIgnoreCase(existing.getGatewayTransactionId())) {
                    throw new ResourceConflictException("BillingGatewayTransaction", "gatewayRef", ref);
                }
            }
        }

        if (txn.getId() == null || txn.getId().trim().isEmpty()) {
            txn.setId(UUID.randomUUID().toString());
        }
        txn.setTenantId(tenantId);
        txn.setGateway(gw);
        txn.setGatewayTransactionId(ref);
        txn.setCreatedAt(System.currentTimeMillis());
        txn.setUpdatedAt(txn.getCreatedAt());
        txn.setRowVersion(1);

        store.put(txn.getId(), txn);
        return txn;
    }

    @Override
    public BillingGatewayTransaction getTransactionById(UserSecurityContext context, String id) {
        if (id == null) return null;
        BillingGatewayTransaction txn = store.get(id);
        if (txn == null) return null;
        String tenantId = (context != null && context.getTenantId() != null) ? context.getTenantId().toString() : null;
        if (tenantId != null && !tenantId.equalsIgnoreCase(txn.getTenantId())) {
            return null;
        }
        return txn;
    }

    @Override
    public BillingGatewayTransaction getTransactionByReference(UserSecurityContext context, String gateway, String gatewayRef) {
        if (gateway == null || gatewayRef == null) return null;
        String tenantId = (context != null && context.getTenantId() != null) ? context.getTenantId().toString() : null;
        for (BillingGatewayTransaction txn : store.values()) {
            if (gateway.equalsIgnoreCase(txn.getGateway()) && gatewayRef.equalsIgnoreCase(txn.getGatewayTransactionId())) {
                if (tenantId == null || tenantId.equalsIgnoreCase(txn.getTenantId())) {
                    return txn;
                }
            }
        }
        return null;
    }

    @Override
    public List<BillingGatewayTransaction> listTransactionsByInvoice(UserSecurityContext context, String invoiceId) {
        if (invoiceId == null) return Collections.emptyList();
        String tenantId = (context != null && context.getTenantId() != null) ? context.getTenantId().toString() : null;
        List<BillingGatewayTransaction> list = new ArrayList<>();
        for (BillingGatewayTransaction txn : store.values()) {
            if (invoiceId.equalsIgnoreCase(txn.getInvoiceId())) {
                if (tenantId == null || tenantId.equalsIgnoreCase(txn.getTenantId())) {
                    list.add(txn);
                }
            }
        }
        return list;
    }

    @Override
    public List<BillingGatewayTransaction> listTransactions(UserSecurityContext context) {
        String tenantId = (context != null && context.getTenantId() != null) ? context.getTenantId().toString() : null;
        List<BillingGatewayTransaction> list = new ArrayList<>();
        for (BillingGatewayTransaction txn : store.values()) {
            if (tenantId == null || tenantId.equalsIgnoreCase(txn.getTenantId())) {
                list.add(txn);
            }
        }
        return list;
    }

    @Override
    public BillingGatewayTransaction updateTransactionStatus(UserSecurityContext context, String id, String status, Long reconciledAt, String rawResponse) {
        BillingGatewayTransaction txn = getTransactionById(context, id);
        if (txn == null) {
            throw new ResourceNotFoundException("BillingGatewayTransaction", id);
        }
        txn.setStatus(status);
        if (reconciledAt != null) txn.setReconciledAt(reconciledAt);
        if (rawResponse != null) txn.setRawReference(rawResponse);
        txn.setUpdatedAt(System.currentTimeMillis());
        txn.setRowVersion(txn.getRowVersion() + 1);
        return txn;
    }

    @Override
    public boolean deleteTransaction(UserSecurityContext context, String id) {
        BillingGatewayTransaction txn = getTransactionById(context, id);
        if (txn == null) return false;
        return store.remove(id) != null;
    }
}
