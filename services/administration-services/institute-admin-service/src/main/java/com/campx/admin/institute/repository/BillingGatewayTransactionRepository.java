package com.campx.admin.institute.repository;

import com.campx.admin.institute.model.InstituteModels.BillingGatewayTransaction;
import com.campx.admin.institute.security.UserSecurityContext;

import java.util.List;

/**
 * Repository interface for managing {@code plat.gateway_transactions} persistence (ADM-01 Item 13).
 */
public interface BillingGatewayTransactionRepository {

    /**
     * Records a new payment gateway transaction.
     *
     * @param context caller security context
     * @param txn     transaction details
     * @return persisted transaction
     */
    BillingGatewayTransaction createTransaction(UserSecurityContext context, BillingGatewayTransaction txn);

    /**
     * Finds a transaction by its unique internal ID.
     *
     * @param context caller security context
     * @param id      transaction UUID
     * @return transaction if found, null otherwise
     */
    BillingGatewayTransaction getTransactionById(UserSecurityContext context, String id);

    /**
     * Finds a transaction by external gateway reference under the tenant.
     *
     * @param context    caller security context
     * @param gateway    gateway identifier (e.g. "RAZORPAY", "STRIPE")
     * @param gatewayRef external transaction reference ID
     * @return transaction if found, null otherwise
     */
    BillingGatewayTransaction getTransactionByReference(UserSecurityContext context, String gateway, String gatewayRef);

    /**
     * Lists all transactions associated with a specific invoice.
     *
     * @param context   caller security context
     * @param invoiceId invoice UUID
     * @return list of transactions
     */
    List<BillingGatewayTransaction> listTransactionsByInvoice(UserSecurityContext context, String invoiceId);

    /**
     * Lists all transactions within the tenant.
     *
     * @param context caller security context
     * @return list of transactions
     */
    List<BillingGatewayTransaction> listTransactions(UserSecurityContext context);

    /**
     * Updates transaction reconciliation status.
     *
     * @param context      caller security context
     * @param id           transaction UUID
     * @param status       new status (e.g. "SUCCESS", "FAILED")
     * @param reconciledAt optional reconciliation epoch millis
     * @param rawResponse  optional JSON payload from payment gateway
     * @return updated transaction
     */
    BillingGatewayTransaction updateTransactionStatus(UserSecurityContext context, String id, String status, Long reconciledAt, String rawResponse);

    /**
     * Soft-deletes a gateway transaction.
     *
     * @param context caller security context
     * @param id      transaction UUID
     * @return true if deleted, false if not found
     */
    boolean deleteTransaction(UserSecurityContext context, String id);
}
