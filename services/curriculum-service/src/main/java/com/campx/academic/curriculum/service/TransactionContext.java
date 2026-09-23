package com.campx.academic.curriculum.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Unit-of-Work Transaction Coordinator for ACD-02 (Story 58).
 * Simulates MongoDB multi-document transactions in-memory with:
 * - Transaction boundaries (TX_START, TX_COMMIT, TX_ROLLBACK)
 * - Automatic compensation / rollback execution on runtime failures
 * - Distributed trace and correlation ID propagation
 */
public class TransactionContext {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TransactionContext.class);

    private final String transactionId;
    private final Deque<Runnable> rollbackActions = new ArrayDeque<>();
    private boolean active = false;
    private boolean committed = false;

    public TransactionContext() {
        this("TX-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
    }

    public TransactionContext(String transactionId) {
        this.transactionId = transactionId;
    }

    /**
     * Begins the transactional boundary.
     */
    public synchronized void begin() {
        if (active) {
            throw new IllegalStateException("Transaction " + transactionId + " is already active");
        }
        this.active = true;
        this.committed = false;
        logger.info("[TX_START] Initiated transaction boundary: id={}, traceId={}", transactionId, LogContext.getTraceId());
    }

    /**
     * Registers a compensation action to be executed if the transaction rolls back.
     *
     * @param compensation LIFO rollback runnable
     */
    public synchronized void addRollback(Runnable compensation) {
        if (!active || committed) {
            throw new IllegalStateException("Cannot register rollback for non-active transaction " + transactionId);
        }
        rollbackActions.push(compensation);
    }

    /**
     * Commits all modifications in the current transaction boundary.
     */
    public synchronized void commit() {
        if (!active || committed) {
            throw new IllegalStateException("Transaction " + transactionId + " is not active to commit");
        }
        this.committed = true;
        this.active = false;
        rollbackActions.clear();
        logger.info("[TX_COMMIT] Successfully committed transaction: id={}, traceId={}", transactionId, LogContext.getTraceId());
    }

    /**
     * Aborts the transaction and executes registered rollback compensations in reverse order.
     */
    public synchronized void rollback() {
        if (!active || committed) {
            return;
        }
        logger.warn("[TX_ROLLBACK] Aborting transaction id={}: executing {} compensation actions", transactionId, rollbackActions.size());
        while (!rollbackActions.isEmpty()) {
            Runnable action = rollbackActions.pop();
            try {
                action.run();
            } catch (Exception e) {
                logger.error("[TX_ROLLBACK_ERROR] Failed executing compensation action in tx {}: {}", transactionId, e.getMessage());
            }
        }
        this.active = false;
    }

    /**
     * Executes a unit of work inside a managed transaction lifecycle.
     */
    public <T> T executeInTransaction(Callable<T> work) throws Exception {
        begin();
        try {
            T result = work.call();
            commit();
            return result;
        } catch (Exception e) {
            rollback();
            throw e;
        }
    }

    /**
     * Executes a runnable unit of work inside a managed transaction lifecycle.
     */
    public void executeInTransaction(Runnable work) {
        begin();
        try {
            work.run();
            commit();
        } catch (RuntimeException e) {
            rollback();
            throw e;
        }
    }

    public String getTransactionId() {
        return transactionId;
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCommitted() {
        return committed;
    }
}
