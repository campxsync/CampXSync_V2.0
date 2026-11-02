package com.campx.admissions.service;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxHeaders;
import com.campx.platform.outbox.TransactionalOutboxWriter;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.sql.Connection;
import java.util.*;

public class AdmissionsEventPublisher {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(AdmissionsEventPublisher.class);
    private static final String SERVICE_NAME = "admissions-service";
    private static final String AGGREGATE_TYPE = "ADMISSION_APPLICATION";
    private static final String SCHEMA_VERSION = "1.0.0";

    private final TransactionalOutboxWriter outboxWriter;
    private final ControlledIntelligenceAdapter intelligenceAdapter;
    private final List<OutboxEvent> publishedEvents = Collections.synchronizedList(new ArrayList<>());
    private final ObjectMapper objectMapper = new ObjectMapper();

    public AdmissionsEventPublisher() {
        this(new TransactionalOutboxWriter(), new ControlledIntelligenceAdapter());
    }

    public AdmissionsEventPublisher(TransactionalOutboxWriter outboxWriter,
            ControlledIntelligenceAdapter intelligenceAdapter) {
        this.outboxWriter = outboxWriter != null ? outboxWriter : new TransactionalOutboxWriter();
        this.intelligenceAdapter = intelligenceAdapter != null ? intelligenceAdapter
                : new ControlledIntelligenceAdapter();
    }

    public void publishEvent(Connection connection, UUID tenantId, UUID applicationId, String eventType,
            Map<String, Object> payload, String actorRole, String correlationId) {
        String corrId = correlationId != null ? correlationId : UUID.randomUUID().toString();
        String payloadJson = "{}";
        try {
            if (payload != null) {
                payloadJson = objectMapper.writeValueAsString(payload);
            }
        } catch (Exception ignored) {
        }

        try {
            OutboxHeaders headers = OutboxHeaders.builder()
                    .correlationId(corrId)
                    .actorId(actorRole != null ? actorRole : "system")
                    .schemaVersion(SCHEMA_VERSION)
                    .build();

            OutboxEvent event = OutboxEvent.builder()
                    .tenantId(tenantId)
                    .service(SERVICE_NAME)
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(applicationId)
                    .eventType(eventType)
                    .headers(headers)
                    .payload(payloadJson)
                    .build();

            publishedEvents.add(event);

            if (connection != null) {
                outboxWriter.write(connection, event);
            }

            // Emit to Intelligence Layer adapter
            IntelligenceContextEvent intelEvent = IntelligenceContextEvent.builder()
                    .tenantId(tenantId != null ? tenantId.toString() : null)
                    .sourceService(SERVICE_NAME)
                    .aggregateType(AGGREGATE_TYPE)
                    .aggregateId(applicationId != null ? applicationId.toString() : "N/A")
                    .eventType(eventType)
                    .schemaVersion(SCHEMA_VERSION)
                    .correlationId(corrId)
                    .payloadJson(payloadJson)
                    .build();

            intelligenceAdapter.publish(intelEvent);

        } catch (Exception e) {
            logger.warn("Failed to publish event [{}]: {}", eventType, e.getMessage());
        }
    }

    public List<OutboxEvent> getPublishedEvents() {
        return Collections.unmodifiableList(publishedEvents);
    }

    public ControlledIntelligenceAdapter getIntelligenceAdapter() {
        return intelligenceAdapter;
    }

    public void clearPublishedEvents() {
        publishedEvents.clear();
    }
}
