package com.campx.platform.outbox;

import org.junit.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.Assert.*;

public class OutboxEventTest {

    private static final UUID TENANT_ID = UUID.fromString("11111111-1111-1111-1111-111111111111");

    @Test
    public void testValidOutboxEventCreation() {
        UUID aggregateId = UUID.randomUUID();
        OutboxHeaders headers = OutboxHeaders.builder()
                .correlationId("corr-12345")
                .causationId("cause-67890")
                .idempotencyKey("idem-99999")
                .actorId("user-admin-1")
                .addHeader("sourceIp", "192.168.1.100")
                .build();

        OutboxEvent event = OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(aggregateId)
                .eventType("StudentEnrolled")
                .payload("{\"enrollmentNo\":\"STU-2026-001\",\"department\":\"CSE\"}")
                .headers(headers)
                .build();

        assertNotNull(event.getId());
        assertEquals(TENANT_ID, event.getTenantId());
        assertEquals("student-service", event.getService());
        assertEquals("Student", event.getAggregateType());
        assertEquals(aggregateId, event.getAggregateId());
        assertEquals("StudentEnrolled", event.getEventType());
        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertEquals(0, event.getAttempts());
        assertNotNull(event.getCreatedAt());
        assertEquals("corr-12345", event.getHeaders().getCorrelationId());
        assertEquals("1.0.0", event.getHeaders().getSchemaVersion());
        assertEquals("user-admin-1", event.getHeaders().getActorId());
        assertTrue(event.getHeaders().toJson().contains("\"correlationId\":\"corr-12345\""));
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsNullTenantId() {
        OutboxEvent.builder()
                .tenantId(null) // STRICT TENANT ISOLATION VIOLATION
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("StudentEnrolled")
                .payload("{\"status\":\"active\"}")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsMissingService() {
        OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("   ")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("StudentEnrolled")
                .payload("{\"status\":\"active\"}")
                .build();
    }

    @Test(expected = IllegalArgumentException.class)
    public void testRejectsMissingEventType() {
        OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("student-service")
                .aggregateType("Student")
                .aggregateId(UUID.randomUUID())
                .eventType("")
                .payload("{\"status\":\"active\"}")
                .build();
    }

    @Test(expected = SecurityException.class)
    public void testRejectsPayloadWithUnmaskedSecrets() {
        OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("auth-service")
                .aggregateType("User")
                .aggregateId(UUID.randomUUID())
                .eventType("UserLogin")
                .payload("{\"username\":\"admin\",\"password\":\"plaintextSecretPassword\"}") // LEAST PRIVILEGE VIOLATION
                .build();
    }

    @Test
    public void testStatusTransitionsAndRetry() {
        OutboxEvent event = OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("attendance-service")
                .aggregateType("AttendanceRecord")
                .aggregateId(UUID.randomUUID())
                .eventType("AttendanceCorrected")
                .payload("{\"status\":\"PRESENT\"}")
                .build();

        assertEquals(OutboxStatus.PENDING, event.getStatus());
        assertNull(event.getDispatchedAt());

        // Mark dispatched
        OutboxEvent dispatched = event.markDispatched();
        assertEquals(OutboxStatus.DISPATCHED, dispatched.getStatus());
        assertNotNull(dispatched.getDispatchedAt());
        assertEquals(event.getId(), dispatched.getId());

        // Record failure
        Instant nextRetry = Instant.now().plusSeconds(60);
        OutboxEvent failedAttempt = event.recordFailure(3, nextRetry);
        assertEquals(OutboxStatus.PENDING, failedAttempt.getStatus());
        assertEquals(3, failedAttempt.getAttempts());
        assertEquals(nextRetry, failedAttempt.getNextAttemptAt());
    }

    @Test
    public void testDeadLetterConversion() {
        OutboxEvent event = OutboxEvent.builder()
                .tenantId(TENANT_ID)
                .service("attendance-service")
                .aggregateType("AttendanceRecord")
                .aggregateId(UUID.randomUUID())
                .eventType("AttendanceCorrected")
                .payload("{\"status\":\"PRESENT\"}")
                .build();

        DeadLetterEvent dlq = event.toDeadLetterEvent("Max delivery retry threshold exceeded");
        assertNotNull(dlq.getId());
        assertEquals(TENANT_ID, dlq.getTenantId());
        assertEquals("attendance-service", dlq.getSourceService());
        assertEquals(event.getId().toString(), dlq.getOriginalEventId());
        assertEquals("Max delivery retry threshold exceeded", dlq.getFailureReason());
        assertEquals(DeadLetterStatus.FAILED, dlq.getStatus());
        assertNotNull(dlq.getExpiresAt());
    }
}
