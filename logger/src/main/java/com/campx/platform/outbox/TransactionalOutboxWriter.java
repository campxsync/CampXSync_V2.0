package com.campx.platform.outbox;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.logger.context.LogContext;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

/**
 * Transaction-aware coordinator ensuring outbox events are written strictly within
 * the same active database transaction as the domain entity mutation.
 * <p>
 * Enforces architectural rule:
 * <ul>
 *   <li>Throws {@link IllegalStateException} if invoked on a connection with {@code autoCommit == true}.</li>
 *   <li>Guarantees atomicity: if the domain write rolls back, the outbox event also rolls back.</li>
 *   <li>Guarantees durability: if the domain write commits, the outbox event commits with it.</li>
 * </ul>
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class TransactionalOutboxWriter {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(TransactionalOutboxWriter.class);

    private final OutboxRepository repository;

    public TransactionalOutboxWriter() {
        this(new PostgresOutboxRepository());
    }

    public TransactionalOutboxWriter(OutboxRepository repository) {
        if (repository == null) throw new IllegalArgumentException("OutboxRepository cannot be null");
        this.repository = repository;
    }

    /**
     * Writes an outbox event within an existing transactional connection, validating
     * tenant boundary and enriching with LogContext.
     *
     * @param conn  active transactional connection with autoCommit == false
     * @param event outbox event to persist
     * @return the persisted, enriched outbox event
     * @throws IllegalStateException if conn.getAutoCommit() is true
     * @throws SecurityException     if LogContext tenant does not match event tenant
     * @throws SQLException          if database insert fails
     */
    public OutboxEvent write(Connection conn, OutboxEvent event) throws SQLException {
        assertTransactionActive(conn);
        if (event == null) throw new IllegalArgumentException("event cannot be null");

        String contextTenant = LogContext.getTenantId();
        if (contextTenant != null && !contextTenant.trim().isEmpty()) {
            if (!contextTenant.equalsIgnoreCase(event.getTenantId().toString())) {
                throw new SecurityException("Cross-tenant violation: current LogContext tenant [" + contextTenant
                        + "] does not match event tenant [" + event.getTenantId() + "]");
            }
        }

        OutboxEvent enriched = enrichWithContext(event);
        repository.save(conn, enriched);
        logger.debug("[TX_OUTBOX] Enqueued event [{}] type={} aggregate={}/{}",
                enriched.getId(), enriched.getEventType(), enriched.getAggregateType(), enriched.getAggregateId());
        return enriched;
    }

    /**
     * Writes an outbox event within an existing transactional connection.
     *
     * @param conn  active transactional connection with autoCommit == false
     * @param event outbox event to persist
     * @throws IllegalStateException if conn.getAutoCommit() is true
     * @throws SQLException          if database insert fails
     */
    public void writeInTransaction(Connection conn, OutboxEvent event) throws SQLException {
        write(conn, event);
    }

    /**
     * Writes multiple outbox events in batch within an existing transactional connection.
     *
     * @param conn   active transactional connection with autoCommit == false
     * @param events outbox events to persist
     * @throws IllegalStateException if conn.getAutoCommit() is true
     * @throws SQLException          if database insert fails
     */
    public void writeAllInTransaction(Connection conn, List<OutboxEvent> events) throws SQLException {
        assertTransactionActive(conn);
        if (events == null || events.isEmpty()) return;

        String contextTenant = LogContext.getTenantId();
        java.util.List<OutboxEvent> enrichedList = new java.util.ArrayList<>(events.size());
        for (OutboxEvent event : events) {
            if (event == null) throw new IllegalArgumentException("event cannot be null");
            if (contextTenant != null && !contextTenant.trim().isEmpty()) {
                if (!contextTenant.equalsIgnoreCase(event.getTenantId().toString())) {
                    throw new SecurityException("Cross-tenant violation: current LogContext tenant [" + contextTenant
                            + "] does not match event tenant [" + event.getTenantId() + "]");
                }
            }
            enrichedList.add(enrichWithContext(event));
        }

        repository.saveAll(conn, enrichedList);
        logger.debug("[TX_OUTBOX] Enqueued {} events in transaction", enrichedList.size());
    }

    /**
     * Builds and writes an outbox event in one transactional step, automatically enriching
     * metadata from {@link LogContext} if not explicitly supplied.
     */
    public OutboxEvent writeEvent(Connection conn, UUID tenantId, String service, String aggregateType,
                                  UUID aggregateId, String eventType, String payload) throws SQLException {
        assertTransactionActive(conn);

        String traceId = LogContext.getTraceId();
        String userId = LogContext.getUserId();

        OutboxHeaders headers = OutboxHeaders.builder()
                .correlationId(traceId != null ? traceId : UUID.randomUUID().toString())
                .traceId(traceId)
                .actorId(userId)
                .schemaVersion(OutboxHeaders.DEFAULT_SCHEMA_VERSION)
                .build();

        OutboxEvent event = OutboxEvent.builder()
                .tenantId(tenantId)
                .service(service)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload)
                .headers(headers)
                .build();

        return write(conn, event);
    }

    private void assertTransactionActive(Connection conn) throws SQLException {
        if (conn == null) {
            throw new IllegalArgumentException("Database connection cannot be null");
        }
        if (conn.isClosed()) {
            throw new IllegalStateException("Database connection is closed");
        }
        if (conn.getAutoCommit()) {
            throw new IllegalStateException("TransactionalOutboxWriter requires an active database transaction; connection autoCommit must be false");
        }
    }

    private OutboxEvent enrichWithContext(OutboxEvent event) {
        String traceId = LogContext.getTraceId();
        String userId = LogContext.getUserId();
        if (traceId == null && userId == null) {
            return event;
        }

        OutboxHeaders currentHeaders = event.getHeaders();
        OutboxHeaders enrichedHeaders = OutboxHeaders.builder()
                .correlationId(currentHeaders.getCorrelationId())
                .causationId(currentHeaders.getCausationId())
                .idempotencyKey(currentHeaders.getIdempotencyKey())
                .schemaVersion(currentHeaders.getSchemaVersion())
                .traceId(currentHeaders.getTraceId() != null ? currentHeaders.getTraceId() : traceId)
                .actorId(currentHeaders.getActorId() != null ? currentHeaders.getActorId() : userId)
                .eventTime(currentHeaders.getEventTime())
                .customHeaders(currentHeaders.getCustomHeaders())
                .build();

        return event.toBuilder().headers(enrichedHeaders).build();
    }
}
