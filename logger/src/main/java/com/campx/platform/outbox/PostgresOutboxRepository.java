package com.campx.platform.outbox;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.sql.*;
import java.time.Instant;
import java.util.*;

/**
 * Production PostgreSQL JDBC implementation of {@link OutboxRepository} backed by
 * {@code sys.outbox_events} and {@code sys.dead_letter_events}.
 * <p>
 * Ensures strict multi-tenant boundary isolation: every query is filtered by {@code tenant_id}.
 * Supports transactional execution within an existing {@link Connection} boundary.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class PostgresOutboxRepository implements OutboxRepository {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(PostgresOutboxRepository.class);

    private static final String SQL_INSERT =
            "INSERT INTO sys.outbox_events (" +
            "  id, tenant_id, service, aggregate_type, aggregate_id, event_type, " +
            "  payload, headers, status, attempts, next_attempt_at, dispatched_at, created_at, updated_at" +
            ") VALUES (?, ?, ?, ?, ?, ?, ?::jsonb, ?::jsonb, ?, ?, ?, ?, ?, ?)";

    private static final String SQL_FIND_BY_ID =
            "SELECT id, tenant_id, service, aggregate_type, aggregate_id, event_type, " +
            "  payload::text AS payload, headers::text AS headers, status, attempts, " +
            "  next_attempt_at, dispatched_at, created_at, updated_at " +
            "FROM sys.outbox_events " +
            "WHERE tenant_id = ? AND id = ?";

    private static final String SQL_FIND_PENDING =
            "SELECT id, tenant_id, service, aggregate_type, aggregate_id, event_type, " +
            "  payload::text AS payload, headers::text AS headers, status, attempts, " +
            "  next_attempt_at, dispatched_at, created_at, updated_at " +
            "FROM sys.outbox_events " +
            "WHERE tenant_id = ? AND status = 'PENDING' AND (next_attempt_at IS NULL OR next_attempt_at <= now()) " +
            "ORDER BY created_at ASC " +
            "LIMIT ?";

    private static final String SQL_MARK_DISPATCHED =
            "UPDATE sys.outbox_events " +
            "SET status = 'DISPATCHED', dispatched_at = ?, updated_at = now() " +
            "WHERE tenant_id = ? AND id = ?";

    private static final String SQL_RECORD_FAILURE =
            "UPDATE sys.outbox_events " +
            "SET status = 'PENDING', attempts = ?, next_attempt_at = ?, updated_at = now() " +
            "WHERE tenant_id = ? AND id = ?";

    private static final String SQL_INSERT_DEAD_LETTER =
            "INSERT INTO sys.dead_letter_events (" +
            "  id, tenant_id, source_service, original_event_id, failure_reason, payload, status, created_at, updated_at" +
            ") VALUES (?, ?, ?, ?, ?, ?::jsonb, 'FAILED', now(), now())";

    private static final String SQL_MARK_FAILED =
            "UPDATE sys.outbox_events " +
            "SET status = 'FAILED', updated_at = now() " +
            "WHERE tenant_id = ? AND id = ?";

    private static final String SQL_PURGE_DISPATCHED =
            "DELETE FROM sys.outbox_events " +
            "WHERE tenant_id = ? AND status = 'DISPATCHED' AND dispatched_at < ?";

    @Override
    public void save(Connection conn, OutboxEvent event) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (event == null) throw new IllegalArgumentException("OutboxEvent cannot be null");

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            bindEventParams(ps, event);
            ps.executeUpdate();
        }
        logger.debug("Persisted outbox event [{}] type={} tenant={}", event.getId(), event.getEventType(), event.getTenantId());
    }

    @Override
    public void saveAll(Connection conn, List<OutboxEvent> events) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (events == null || events.isEmpty()) return;

        try (PreparedStatement ps = conn.prepareStatement(SQL_INSERT)) {
            for (OutboxEvent event : events) {
                bindEventParams(ps, event);
                ps.addBatch();
            }
            ps.executeBatch();
        }
        logger.debug("Batch persisted {} outbox events", events.size());
    }

    @Override
    public Optional<OutboxEvent> findById(Connection conn, UUID tenantId, UUID eventId) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (tenantId == null || eventId == null) return Optional.empty();

        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_BY_ID)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, eventId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(mapRowToEvent(rs));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<OutboxEvent> findPendingEvents(Connection conn, UUID tenantId, int limit) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (tenantId == null) throw new IllegalArgumentException("tenantId is required for pending outbox query");
        int fetchLimit = limit > 0 ? limit : 50;

        List<OutboxEvent> list = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(SQL_FIND_PENDING)) {
            ps.setObject(1, tenantId);
            ps.setInt(2, fetchLimit);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    list.add(mapRowToEvent(rs));
                }
            }
        }
        return list;
    }

    @Override
    public void markDispatched(Connection conn, UUID tenantId, UUID eventId, Instant dispatchedAt) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (tenantId == null || eventId == null) throw new IllegalArgumentException("tenantId and eventId required");

        Timestamp dispatchedTs = dispatchedAt != null ? Timestamp.from(dispatchedAt) : Timestamp.from(Instant.now());
        try (PreparedStatement ps = conn.prepareStatement(SQL_MARK_DISPATCHED)) {
            ps.setTimestamp(1, dispatchedTs);
            ps.setObject(2, tenantId);
            ps.setObject(3, eventId);
            ps.executeUpdate();
        }
        logger.debug("Marked outbox event [{}] as DISPATCHED for tenant [{}]", eventId, tenantId);
    }

    public void markDispatched(Connection conn, UUID tenantId, UUID eventId) throws SQLException {
        markDispatched(conn, tenantId, eventId, Instant.now());
    }

    @Override
    public void recordFailure(Connection conn, UUID tenantId, UUID eventId, int currentAttempts, Instant nextAttemptAt, String errorDetails) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (tenantId == null || eventId == null) throw new IllegalArgumentException("tenantId and eventId required");

        Timestamp nextTs = nextAttemptAt != null ? Timestamp.from(nextAttemptAt) : null;
        try (PreparedStatement ps = conn.prepareStatement(SQL_RECORD_FAILURE)) {
            ps.setInt(1, currentAttempts);
            ps.setTimestamp(2, nextTs);
            ps.setObject(3, tenantId);
            ps.setObject(4, eventId);
            ps.executeUpdate();
        }
        logger.warn("Recorded failure for outbox event [{}] attempt={} nextAttemptAt={}", eventId, currentAttempts, nextAttemptAt);
    }

    public void recordFailure(Connection conn, UUID tenantId, UUID eventId, int currentAttempts, Instant nextAttemptAt) throws SQLException {
        recordFailure(conn, tenantId, eventId, currentAttempts, nextAttemptAt, null);
    }

    @Override
    public void moveToDeadLetter(Connection conn, OutboxEvent event, String failureReason) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (event == null) throw new IllegalArgumentException("OutboxEvent cannot be null");

        UUID deadLetterId = UUID.randomUUID();
        try (PreparedStatement psDl = conn.prepareStatement(SQL_INSERT_DEAD_LETTER)) {
            psDl.setObject(1, deadLetterId);
            psDl.setObject(2, event.getTenantId());
            psDl.setString(3, event.getService());
            psDl.setString(4, event.getId().toString());
            psDl.setString(5, failureReason != null ? failureReason : "Retries exhausted");
            psDl.setString(6, event.getPayload());
            psDl.executeUpdate();
        }

        try (PreparedStatement psFail = conn.prepareStatement(SQL_MARK_FAILED)) {
            psFail.setObject(1, event.getTenantId());
            psFail.setObject(2, event.getId());
            psFail.executeUpdate();
        }

        logger.error("Moved outbox event [{}] to dead-letter queue [{}] reason: {}", event.getId(), deadLetterId, failureReason);
    }

    @Override
    public int deleteDispatchedEvents(Connection conn, UUID tenantId, Instant olderThan) throws SQLException {
        if (conn == null) throw new IllegalArgumentException("Connection cannot be null");
        if (tenantId == null || olderThan == null) throw new IllegalArgumentException("tenantId and olderThan required");

        try (PreparedStatement ps = conn.prepareStatement(SQL_PURGE_DISPATCHED)) {
            ps.setObject(1, tenantId);
            ps.setTimestamp(2, Timestamp.from(olderThan));
            return ps.executeUpdate();
        }
    }

    private void bindEventParams(PreparedStatement ps, OutboxEvent event) throws SQLException {
        ps.setObject(1, event.getId());
        ps.setObject(2, event.getTenantId());
        ps.setString(3, event.getService());
        ps.setString(4, event.getAggregateType());
        setNullableUuid(ps, 5, event.getAggregateId());
        ps.setString(6, event.getEventType());
        ps.setString(7, event.getPayload());
        ps.setString(8, event.getHeaders().toJson());
        ps.setString(9, event.getStatus().name());
        ps.setInt(10, event.getAttempts());

        if (event.getNextAttemptAt() != null) {
            ps.setTimestamp(11, Timestamp.from(event.getNextAttemptAt()));
        } else {
            ps.setNull(11, Types.TIMESTAMP);
        }

        if (event.getDispatchedAt() != null) {
            ps.setTimestamp(12, Timestamp.from(event.getDispatchedAt()));
        } else {
            ps.setNull(12, Types.TIMESTAMP);
        }

        ps.setTimestamp(13, Timestamp.from(event.getCreatedAt()));
        ps.setTimestamp(14, Timestamp.from(event.getUpdatedAt()));
    }

    private OutboxEvent mapRowToEvent(ResultSet rs) throws SQLException {
        UUID id = getUuid(rs, "id");
        UUID tenantId = getUuid(rs, "tenant_id");
        String service = rs.getString("service");
        String aggregateType = rs.getString("aggregate_type");
        UUID aggregateId = getUuid(rs, "aggregate_id");
        String eventType = rs.getString("event_type");
        String payload = rs.getString("payload");
        String headersJson = rs.getString("headers");
        String statusStr = rs.getString("status");
        int attempts = rs.getInt("attempts");
        Instant nextAttemptTs = getInstant(rs, "next_attempt_at");
        Instant dispatchedTs = getInstant(rs, "dispatched_at");
        Instant createdTs = getInstant(rs, "created_at");
        Instant updatedTs = getInstant(rs, "updated_at");

        OutboxStatus status = OutboxStatus.PENDING;
        if (statusStr != null) {
            try {
                status = OutboxStatus.valueOf(statusStr.toUpperCase(Locale.ROOT));
            } catch (IllegalArgumentException ignored) {}
        }

        OutboxHeaders headers = OutboxHeaders.fromJson(headersJson);

        return OutboxEvent.builder()
                .id(id)
                .tenantId(tenantId)
                .service(service)
                .aggregateType(aggregateType)
                .aggregateId(aggregateId)
                .eventType(eventType)
                .payload(payload != null ? payload : "{}")
                .headers(headers)
                .status(status)
                .attempts(attempts)
                .nextAttemptAt(nextAttemptTs)
                .dispatchedAt(dispatchedTs)
                .createdAt(createdTs != null ? createdTs : Instant.now())
                .updatedAt(updatedTs != null ? updatedTs : Instant.now())
                .build();
    }

    private void setNullableUuid(PreparedStatement ps, int paramIndex, UUID uuid) throws SQLException {
        if (uuid != null) {
            ps.setObject(paramIndex, uuid);
        } else {
            ps.setNull(paramIndex, Types.OTHER);
        }
    }

    private UUID getUuid(ResultSet rs, String col) throws SQLException {
        Object val = rs.getObject(col);
        if (val == null) return null;
        if (val instanceof UUID) return (UUID) val;
        try {
            return UUID.fromString(val.toString());
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private Instant getInstant(ResultSet rs, String col) throws SQLException {
        Timestamp ts = rs.getTimestamp(col);
        return ts != null ? ts.toInstant() : null;
    }
}
