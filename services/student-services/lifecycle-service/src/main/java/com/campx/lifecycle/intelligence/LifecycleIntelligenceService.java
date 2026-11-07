package com.campx.lifecycle.intelligence;

import com.campx.lifecycle.LifecycleDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

public class LifecycleIntelligenceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(LifecycleIntelligenceService.class);
    private static final String SERVICE_NAME = "lifecycle-service";

    private final ControlledIntelligenceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public LifecycleIntelligenceService() {
        this(new ControlledIntelligenceAdapter());
    }

    public LifecycleIntelligenceService(ControlledIntelligenceAdapter adapter) {
        this.adapter = adapter == null ? new ControlledIntelligenceAdapter() : adapter;
    }

    public void publishLifecycleChanged(Map<String, Object> domainEvent) {
        if (domainEvent == null || domainEvent.get("tenantId") == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("studentId", String.valueOf(domainEvent.get("studentId")));
            payload.put("previousStatus", domainEvent.get("previousStatus"));
            payload.put("status", String.valueOf(domainEvent.get("status")));
            payload.put("action", domainEvent.get("action"));
            payload.put("requestId", domainEvent.get("requestId"));
            payload.put("correlationId", domainEvent.get("correlationId"));

            UUID tenantId = (UUID) domainEvent.get("tenantId");
            String studentId = String.valueOf(domainEvent.get("studentId"));
            String eventKey = String.valueOf(domainEvent.get("occurredAt"));
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(LifecycleDomainService.LIFECYCLE_CHANGED)
                    .schemaVersion(LifecycleDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_LIFECYCLE")
                    .aggregateId(studentId)
                    .tenantId(tenantId)
                    .sourceService(SERVICE_NAME)
                    .correlationId(String.valueOf(domainEvent.get("correlationId")))
                    .idempotencyKey("intel-student.lifecycle.changed.v1-" + tenantId + "-" + studentId + "-" + eventKey)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm04 intelligence contract emitted eventType={} tenantId={}",
                    LifecycleDomainService.LIFECYCLE_CHANGED, tenantId);
        } catch (Exception ex) {
            logger.error("Failed to emit STM-04 intelligence event: {}", ex.getMessage(), ex);
        }
    }

    public ControlledIntelligenceAdapter getAdapter() {
        return adapter;
    }
}
