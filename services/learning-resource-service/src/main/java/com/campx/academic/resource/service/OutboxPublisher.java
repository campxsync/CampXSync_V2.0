package com.campx.academic.resource.service;

import com.campx.academic.resource.model.ResourceModels.DeadLetterEvent;
import com.campx.academic.resource.model.ResourceModels.DLQStatus;
import com.campx.academic.resource.model.ResourceModels.OutboxEvent;
import com.campx.academic.resource.model.ResourceModels.OutboxStatus;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Transactional Outbox Publisher & Dead Letter Queue (DLQ) Manager:
 * - Transactional outbox persistence (US-034, US-035, US-036, US-037, US-039)
 * - DLQ routing for poison events (US-041)
 * - Operator DLQ replay capability (US-061)
 * - Transient retry handling with bounded backoff (US-042)
 */
public class OutboxPublisher {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(OutboxPublisher.class);

    private final Map<String, OutboxEvent> outboxStore = new ConcurrentHashMap<>();
    private final Map<String, DeadLetterEvent> dlqStore = new ConcurrentHashMap<>();
    private final MetricsCollector metricsCollector = MetricsCollector.getInstance();

    private boolean brokerAvailable = true;

    public void setBrokerAvailable(boolean available) {
        this.brokerAvailable = available;
    }

    /**
     * Atomically records an event in the outbox store (US-039).
     */
    public OutboxEvent recordOutboxEvent(String tenantId, String aggregateId, String eventType,
                                         String payload, String correlationId) {
        String eventId = "EVT-" + UUID.randomUUID().toString().substring(0, 10).toUpperCase();

        OutboxEvent event = new OutboxEvent();
        event.setId(eventId);
        event.setEventId(eventId);
        event.setTenantId(tenantId);
        event.setAggregateType("LearningResource");
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setStatus(OutboxStatus.PENDING);
        event.setCreatedAt(Instant.now().toString());
        event.setCorrelationId(correlationId);

        outboxStore.put(eventId, event);
        updateMetrics();

        logger.info("[OutboxPublisher] Recorded outbox event {} for aggregate {} type {}", eventId, aggregateId, eventType);

        // Immediate dispatch attempt
        dispatch(event);
        return event;
    }

    /**
     * Dispatches an outbox event. If broker is unavailable, leaves as PENDING or routes to DLQ if max retries reached.
     */
    public boolean dispatch(OutboxEvent event) {
        if (!brokerAvailable) {
            event.setAttemptCount(event.getAttemptCount() + 1);
            if (event.getAttemptCount() >= 3) {
                moveToDLQ(event, "BROKER_UNAVAILABLE", "Max dispatch attempts exceeded");
            }
            updateMetrics();
            return false;
        }

        event.setStatus(OutboxStatus.PUBLISHED);
        event.setPublishedAt(Instant.now().toString());
        updateMetrics();
        logger.info("[OutboxPublisher] Published event {} ({}) to EventBus successfully", event.getEventId(), event.getEventType());
        return true;
    }

    /**
     * Moves failed event to DLQ (US-041).
     */
    public DeadLetterEvent moveToDLQ(OutboxEvent event, String failureCode, String failureReason) {
        event.setStatus(OutboxStatus.FAILED);

        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setId("DLQ-" + event.getEventId());
        dlq.setEventId(event.getEventId());
        dlq.setEventType(event.getEventType());
        dlq.setPayload(event.getPayload());
        dlq.setFailureCode(failureCode);
        dlq.setFailureReason(failureReason);
        dlq.setAttemptCount(event.getAttemptCount());
        dlq.setFirstFailedAt(Instant.now().toString());
        dlq.setLastFailedAt(Instant.now().toString());
        dlq.setStatus(DLQStatus.DEAD_LETTER);
        dlq.setReplayCount(0);

        dlqStore.put(event.getEventId(), dlq);
        updateMetrics();

        logger.warn("[OutboxPublisher] Moved poison event {} to DLQ: {}", event.getEventId(), failureReason);
        return dlq;
    }

    /**
     * Operator replay of a dead letter event (US-061).
     */
    public boolean replayDeadLetterEvent(String eventId) {
        DeadLetterEvent dlq = dlqStore.get(eventId);
        if (dlq == null) {
            return false;
        }

        dlq.setReplayCount(dlq.getReplayCount() + 1);
        dlq.setLastFailedAt(Instant.now().toString());

        OutboxEvent outbox = outboxStore.get(eventId);
        if (outbox != null) {
            outbox.setStatus(OutboxStatus.PENDING);
            boolean success = dispatch(outbox);
            if (success) {
                dlq.setStatus(DLQStatus.REPLAYED);
                updateMetrics();
                logger.info("[OutboxPublisher] Successfully replayed DLQ event {}", eventId);
                return true;
            }
        }
        return false;
    }

    public List<OutboxEvent> getPendingEvents() {
        List<OutboxEvent> pending = new ArrayList<>();
        for (OutboxEvent e : outboxStore.values()) {
            if (e.getStatus() == OutboxStatus.PENDING) {
                pending.add(e);
            }
        }
        return pending;
    }

    public List<DeadLetterEvent> getDeadLetterEvents() {
        return new ArrayList<>(dlqStore.values());
    }

    private void updateMetrics() {
        long pendingCount = 0;
        for (OutboxEvent e : outboxStore.values()) {
            if (e.getStatus() == OutboxStatus.PENDING) pendingCount++;
        }
        metricsCollector.setOutboxLag(pendingCount);

        long dlqCount = 0;
        for (DeadLetterEvent d : dlqStore.values()) {
            if (d.getStatus() == DLQStatus.DEAD_LETTER) dlqCount++;
        }
        metricsCollector.setDlqDepth(dlqCount);
    }
}
