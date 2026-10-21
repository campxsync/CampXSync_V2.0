package com.campx.platform.outbox;

import java.sql.Connection;
import java.sql.SQLException;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Transaction-aware repository contract for {@code sys.outbox_events} and {@code sys.dead_letter_events}.
 * All mutating methods accept an active JDBC {@link Connection} to ensure outbox events
 * commit atomically alongside domain entity state changes.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public interface OutboxRepository {

    /**
     * Persists an outbox event within the active transaction on {@code conn}.
     *
     * @param conn  active transactional database connection (autoCommit must be false)
     * @param event the event to persist
     * @throws SQLException if a database error occurs
     */
    void save(Connection conn, OutboxEvent event) throws SQLException;

    /**
     * Persists multiple outbox events in a single batch within the active transaction on {@code conn}.
     *
     * @param conn   active transactional database connection
     * @param events list of events to persist
     * @throws SQLException if a database error occurs
     */
    void saveAll(Connection conn, List<OutboxEvent> events) throws SQLException;

    /**
     * Retrieves an outbox event by tenant ID and event ID.
     *
     * @param conn     database connection
     * @param tenantId multi-tenant boundary ID
     * @param eventId  event primary key UUID
     * @return optional containing the event if present
     * @throws SQLException if a database error occurs
     */
    Optional<OutboxEvent> findById(Connection conn, UUID tenantId, UUID eventId) throws SQLException;

    /**
     * Queries pending outbox events ready for dispatch for a specific tenant.
     *
     * @param conn     database connection
     * @param tenantId multi-tenant boundary ID
     * @param limit    maximum number of events to fetch
     * @return list of pending outbox events ordered by created_at ascending
     * @throws SQLException if a database error occurs
     */
    List<OutboxEvent> findPendingEvents(Connection conn, UUID tenantId, int limit) throws SQLException;

    /**
     * Marks an outbox event as DISPATCHED with current timestamp.
     *
     * @param conn         database connection
     * @param tenantId     multi-tenant boundary ID
     * @param eventId      event primary key UUID
     * @param dispatchedAt timestamp when dispatch was completed
     * @throws SQLException if a database error occurs
     */
    void markDispatched(Connection conn, UUID tenantId, UUID eventId, Instant dispatchedAt) throws SQLException;

    /**
     * Marks an outbox event as DISPATCHED using current timestamp.
     */
    default void markDispatched(Connection conn, UUID tenantId, UUID eventId) throws SQLException {
        markDispatched(conn, tenantId, eventId, Instant.now());
    }

    /**
     * Updates an outbox event with incremented retry attempts and scheduled next attempt time.
     *
     * @param conn            database connection
     * @param tenantId        multi-tenant boundary ID
     * @param eventId         event primary key UUID
     * @param currentAttempts current attempt count
     * @param nextAttemptAt   timestamp for next retry attempt
     * @param errorDetails    optional error explanation
     * @throws SQLException if a database error occurs
     */
    void recordFailure(Connection conn, UUID tenantId, UUID eventId, int currentAttempts, Instant nextAttemptAt, String errorDetails) throws SQLException;

    /**
     * Updates an outbox event with incremented retry attempts and scheduled next attempt time without error explanation.
     */
    default void recordFailure(Connection conn, UUID tenantId, UUID eventId, int currentAttempts, Instant nextAttemptAt) throws SQLException {
        recordFailure(conn, tenantId, eventId, currentAttempts, nextAttemptAt, null);
    }

    /**
     * Atomically records an event to {@code sys.dead_letter_events} and marks the outbox event as FAILED.
     *
     * @param conn          active transactional database connection
     * @param event         the failed outbox event
     * @param failureReason explanation of the fatal or exhausted failure
     * @throws SQLException if a database error occurs
     */
    void moveToDeadLetter(Connection conn, OutboxEvent event, String failureReason) throws SQLException;

    /**
     * Atomically moves an event to dead letter by tenant ID and event ID.
     */
    default void moveToDeadLetter(Connection conn, UUID tenantId, UUID eventId, String failureReason) throws SQLException {
        Optional<OutboxEvent> opt = findById(conn, tenantId, eventId);
        if (opt.isPresent()) {
            moveToDeadLetter(conn, opt.get(), failureReason);
        }
    }

    /**
     * Purges dispatched outbox events older than the specified retention cutoff.
     *
     * @param conn      database connection
     * @param tenantId  multi-tenant boundary ID
     * @param olderThan retention cutoff timestamp
     * @return number of purged records
     * @throws SQLException if a database error occurs
     */
    int deleteDispatchedEvents(Connection conn, UUID tenantId, Instant olderThan) throws SQLException;
}
