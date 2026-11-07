package com.campx.guardian.intelligence;

import com.campx.guardian.Actor;
import com.campx.guardian.GuardianDomainService;
import com.campx.guardian.GuardianDomainService.GuardianLink;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Publishes the versioned guardian JSON contract. Live delivery stays false until the Intelligence endpoint is configured.
 */
public class GuardianIntelligenceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GuardianIntelligenceService.class);
    private static final String SERVICE_NAME = "guardian-service";
    private final ControlledIntelligenceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public GuardianIntelligenceService() {
        this(new ControlledIntelligenceAdapter());
    }

    public GuardianIntelligenceService(ControlledIntelligenceAdapter adapter) {
        this.adapter = adapter == null ? new ControlledIntelligenceAdapter() : adapter;
    }

    /**
     * Emits the before and after relationship for STM-05-US-002. There is no relationship-history table yet.
     */
    public void publishGuardianUpdated(Actor actor, GuardianLink before, GuardianLink after) {
        if (actor == null || before == null || after == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", after.id.toString());
            payload.put("studentId", after.studentId.toString());
            payload.put("beforeRelationship", before.relationship);
            payload.put("afterRelationship", after.relationship);
            payload.put("beforePrimary", before.primary);
            payload.put("afterPrimary", after.primary);
            payload.put("status", after.status);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_UPDATED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(after.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.updated.v1-" + actor.getTenantId() + "-" + after.id + "-" + after.linkedAt.toEpochMilli())
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_UPDATED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 guardian update event", ex);
        }
    }

    public void publishGuardianLinked(Actor actor, GuardianLink link) {
        if (actor == null || link == null || actor.getTenantId() == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("guardianshipId", link.id.toString());
            payload.put("studentId", link.studentId.toString());
            payload.put("guardianPersonId", link.guardianPersonId.toString());
            payload.put("relationship", link.relationship);
            payload.put("primary", link.primary);
            payload.put("status", link.status);
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(GuardianDomainService.GUARDIAN_LINKED)
                    .schemaVersion(GuardianDomainService.SCHEMA_VERSION)
                    .aggregateType("STUDENT_GUARDIAN")
                    .aggregateId(link.id.toString())
                    .tenantId(actor.getTenantId())
                    .sourceService(SERVICE_NAME)
                    .correlationId(actor.getCorrelationId())
                    .idempotencyKey("intel-student.guardian.linked.v1-" + actor.getTenantId() + "-" + link.id)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm05 intelligence contract emitted eventType={} tenantId={}",
                    GuardianDomainService.GUARDIAN_LINKED, actor.getTenantId());
        } catch (Exception ex) {
            logger.error("Failed to emit STM-05 intelligence event", ex);
        }
    }
}
