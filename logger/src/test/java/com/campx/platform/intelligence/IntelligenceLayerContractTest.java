package com.campx.platform.intelligence;

import com.campx.platform.outbox.OutboxEvent;
import com.campx.platform.outbox.OutboxHeaders;
import org.junit.Before;
import org.junit.Test;

import java.util.UUID;

import static org.junit.Assert.*;

public class IntelligenceLayerContractTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    private ControlledIntelligenceAdapter adapter;

    @Before
    public void setUp() {
        adapter = new ControlledIntelligenceAdapter();
    }

    @Test
    public void testValidOutboxToIntelligenceEventMappingAndValidation() {
        UUID aggregateId = UUID.randomUUID();
        OutboxHeaders headers = OutboxHeaders.builder()
                .correlationId("corr-ai-100")
                .traceId("trace-ai-200")
                .actorId("user-dean-300")
                .schemaVersion("1.0.0")
                .build();

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("stu-service")
                .aggregateType("Student")
                .aggregateId(aggregateId)
                .eventType("StudentRiskProfileUpdated")
                .payload("{\"riskScore\":0.15,\"attendancePercentage\":92.5}")
                .headers(headers)
                .build();

        IntelligenceContextEvent intelEvent = IntelligenceContextEvent.fromOutboxEvent(outboxEvent);

        assertEquals(outboxEvent.getId().toString(), intelEvent.getEventId());
        assertEquals(TENANT_ID.toString(), intelEvent.getTenantId());
        assertEquals("stu-service", intelEvent.getSourceService());
        assertEquals("Student", intelEvent.getAggregateType());
        assertEquals(aggregateId.toString(), intelEvent.getAggregateId());
        assertEquals("StudentRiskProfileUpdated", intelEvent.getEventType());
        assertEquals("1.0.0", intelEvent.getSchemaVersion());
        assertEquals("corr-ai-100", intelEvent.getCorrelationId());
        assertEquals("trace-ai-200", intelEvent.getTraceId());
        assertEquals("user-dean-300", intelEvent.getActorId());
        assertEquals("{\"riskScore\":0.15,\"attendancePercentage\":92.5}", intelEvent.getPayloadJson());

        // Assert validator passes
        IntelligenceContractValidator.validate(intelEvent);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidatorRejectsUnsupportedSchemaVersion() {
        IntelligenceContextEvent invalidVersion = IntelligenceContextEvent.builder()
                .tenantId(TENANT_ID)
                .sourceService("ai-service")
                .aggregateType("Model")
                .aggregateId(UUID.randomUUID())
                .eventType("ModelInferenceCompleted")
                .schemaVersion("2.5.0-BETA") // Unsupported version
                .correlationId("corr-999")
                .payloadJson("{\"status\":\"ok\"}")
                .build();

        IntelligenceContractValidator.validate(invalidVersion);
    }

    @Test(expected = IllegalArgumentException.class)
    public void testValidatorRejectsMissingCorrelationId() {
        IntelligenceContextEvent missingCorrelation = IntelligenceContextEvent.builder()
                .tenantId(TENANT_ID)
                .sourceService("ai-service")
                .aggregateType("Model")
                .aggregateId(UUID.randomUUID())
                .eventType("ModelInferenceCompleted")
                .schemaVersion("1.0.0")
                .correlationId("   ") // Missing correlation
                .payloadJson("{\"status\":\"ok\"}")
                .build();

        IntelligenceContractValidator.validate(missingCorrelation);
    }

    @Test(expected = SecurityException.class)
    public void testValidatorRejectsUnmaskedSecretsInIntelligencePayload() {
        IntelligenceContextEvent sensitiveEvent = IntelligenceContextEvent.builder()
                .tenantId(TENANT_ID)
                .sourceService("ai-service")
                .aggregateType("Prediction")
                .aggregateId(UUID.randomUUID())
                .eventType("PredictionExecuted")
                .schemaVersion("1.0.0")
                .correlationId("corr-777")
                .payloadJson("{\"modelId\":\"risk-v1\",\"dbSecret\":\"top_secret_credentials\"}") // Secret in payload
                .build();

        IntelligenceContractValidator.validate(sensitiveEvent);
    }

    @Test
    public void testControlledIntelligenceAdapterExecution() {
        assertFalse("Controlled adapter in development must mark live endpoint as unavailable (pending)",
                adapter.isLiveEndpointAvailable());

        OutboxHeaders headers = OutboxHeaders.builder()
                .correlationId("corr-adapter-1")
                .schemaVersion("1.0.0")
                .build();

        OutboxEvent outboxEvent = OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("acd-service")
                .aggregateType("AttendanceRecord")
                .aggregateId(UUID.randomUUID())
                .eventType("AttendanceCorrected")
                .payload("{\"oldStatus\":\"ABSENT\",\"newStatus\":\"PRESENT\"}")
                .headers(headers)
                .build();

        IntelligenceContextEvent intelEvent = IntelligenceContextEvent.fromOutboxEvent(outboxEvent);

        adapter.publish(intelEvent);

        assertEquals(1, adapter.getCapturedCount());
        assertEquals(1, adapter.getCapturedEvents().size());
        assertEquals(intelEvent.getEventId(), adapter.getCapturedEvents().get(0).getEventId());

        adapter.clear();
        assertEquals(0, adapter.getCapturedCount());
    }
}
