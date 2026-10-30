package com.campx.academic.batch.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.UUID;

/**
 * Unit-of-Work Transaction Coordinator for ACD-04 Batch Management Service (Story 50, §57).
 * Coordinates atomic commit across batches, batch_rosters, batch_history, and outbox_events.
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
}
