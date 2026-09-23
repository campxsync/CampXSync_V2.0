package com.campx.academic.subject.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;
import java.util.concurrent.Callable;

/**
 * Unit-of-Work Transaction Coordinator for ACD-03 Subject Management Service (Story 57, §56).
 * Simulates MongoDB multi-document transactions in-memory with:
 * - Transaction boundaries (TX_START, TX_COMMIT, TX_ROLLBACK)
 * - Atomic commit across subjects, subject_versions, subject_history, and outbox_events
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
     * Commits the active transaction.
     */
    public synchronized void commit() {
        if (!active || committed) {
            throw new IllegalStateException("Cannot commit inactive or already-committed transaction " + transactionId);
        }
        this.committed = true;
        this.active = false;
        rollbackActions.clear();
        logger.info("[TX_COMMIT] Committed transaction: id={}, traceId={}", transactionId, LogContext.getTraceId());
    }

    /**
     * Rolls back the transaction and executes registered compensation hooks in reverse order (LIFO).
     */
    public synchronized void rollback() {
        if (!active && !committed) {
            return;
        }
        this.active = false;
        logger.warn("[TX_ROLLBACK] Rolling back transaction: id={}, compensations={}", transactionId, rollbackActions.size());

        while (!rollbackActions.isEmpty()) {
            Runnable action = rollbackActions.pop();
            try {
                action.run();
            } catch (Exception ex) {
                logger.error("[TX_ROLLBACK_COMPENSATION_FAILED] Failed compensation in transaction {}: {}", transactionId, ex.getMessage(), ex);
            }
        }
    }

    public boolean isActive() {
        return active;
    }

    public boolean isCommitted() {
        return committed;
    }

    public String getTransactionId() {
        return transactionId;
    }

    /**
     * Executes an operation within a managed transaction block.
     * Automatically commits on success and rolls back on exception.
     *
     * @param action callable domain operation
     * @param <T> result type
     * @return operation result
     * @throws Exception rethrown domain exception
     */
    public static <T> T executeInTransaction(Callable<T> action) throws Exception {
        TransactionContext tx = new TransactionContext();
        tx.begin();
        try {
            T result = action.call();
            tx.commit();
            return result;
        } catch (Exception e) {
            tx.rollback();
            throw e;
        }
    }
}
