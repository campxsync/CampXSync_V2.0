package com.campx.academic.assessment.service;

import com.campx.academic.assessment.model.AssessmentModels.*;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Transactional outbox event publisher and Dead-Letter Queue (DLQ) manager (US-036, US-037, US-038, US-042, US-044, US-045, US-046).
 */
public class OutboxPublisher {

    private static final CampXLogger log = CampXLoggerFactory.getLogger(OutboxPublisher.class);

    private final List<OutboxEvent> outboxEvents = new CopyOnWriteArrayList<>();
    private final Map<String, DeadLetterEvent> deadLetterEvents = new ConcurrentHashMap<>();
    private final MetricsCollector metricsCollector;

    public OutboxPublisher(MetricsCollector metricsCollector) {
        this.metricsCollector = metricsCollector;
    }

    public OutboxEvent publish(String aggregateId, String eventType, String payload, String correlationId) {
        OutboxEvent event = new OutboxEvent();
        event.setAggregateId(aggregateId);
        event.setEventType(eventType);
        event.setPayload(payload);
        event.setCorrelationId(correlationId);
        event.setStatus("SENT");
        event.setAttempts(1);
        event.setSentAt(Instant.now().toString());

        outboxEvents.add(event);
        if (metricsCollector != null) {
            metricsCollector.recordOutboxEvent();
        }
        log.info("Outbox event recorded and emitted: type=" + eventType + ", aggId=" + aggregateId + ", corr=" + correlationId);
        return event;
    }

    public void handlePoisonEvent(String eventId, String aggregateId, String eventType, String payload, String failureReason, String correlationId) {
        DeadLetterEvent dlq = new DeadLetterEvent();
        dlq.setEventId(eventId);
        dlq.setAggregateId(aggregateId);
        dlq.setEventType(eventType);
        dlq.setPayload(payload);
        dlq.setFailureReason(failureReason);
        dlq.setAttempts(3);
        dlq.setCorrelationId(correlationId);
        dlq.setStatus("PENDING_REVIEW");

        deadLetterEvents.put(dlq.getId(), dlq);
        if (metricsCollector != null) {
            metricsCollector.recordDlqEvent();
        }
        log.error("Poison event sent to DLQ: id=" + dlq.getId() + ", eventType=" + eventType + ", reason=" + failureReason);
    }

    public boolean replayDeadLetterEvent(String dlqEventId) {
        DeadLetterEvent dlq = deadLetterEvents.get(dlqEventId);
        if (dlq == null) {
            return false;
        }
        dlq.setReplayCount(dlq.getReplayCount() + 1);
        dlq.setStatus("REPLAYED");
        dlq.setLastAttemptAt(Instant.now().toString());

        // Emit new outbox event
        publish(dlq.getAggregateId(), dlq.getEventType(), dlq.getPayload(), dlq.getCorrelationId());
        if (metricsCollector != null) {
            metricsCollector.recordDlqReplay();
        }
        log.info("DLQ event replayed successfully: id=" + dlqEventId);
        return true;
    }

    public List<OutboxEvent> getOutboxEvents() {
        return Collections.unmodifiableList(outboxEvents);
    }

    public List<DeadLetterEvent> getDeadLetterEvents() {
        return new ArrayList<>(deadLetterEvents.values());
    }

    public DeadLetterEvent getDeadLetterEvent(String id) {
        return deadLetterEvents.get(id);
    }
}
