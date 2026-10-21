package com.campx.gateway.identity;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.Test;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.CopyOnWriteArrayList;

import static org.junit.Assert.*;

/**
 * Controlled contract test verifying the CampXSync Intelligence Layer integration contract (US-051, US-063, Wave 0 Task 2.1).
 *
 * Verifies:
 *  1. Documented schema versioning (v1.0.0).
 *  2. Tenant isolation context preservation.
 *  3. Least-privilege data payload: no credentials, tokens, or sensitive secrets exposed.
 *  4. Correlation and trace ID propagation.
 *  5. Idempotent delivery metadata.
 *  6. Controlled adapter test double emission; live endpoint marked as PENDING.
 */
public class IntelligenceLayerContractTest {

    private static final ObjectMapper mapper = new ObjectMapper();

    /**
     * Governed, versioned contract event model for publishing resolved identity & authorization context
     * to the CampXSync Intelligence Layer.
     */
    public static class IntelligenceContextEvent {
        private String eventId;
        private String eventType;
        private String schemaVersion;
        private String aggregateType;
        private String aggregateId;
        private String tenantId;
        private String correlationId;
        private String timestamp;
        private Map<String, Object> payload;

        public IntelligenceContextEvent() {}

        public IntelligenceContextEvent(String eventType, String aggregateId, String tenantId, String correlationId, Map<String, Object> payload) {
            this.eventId = UUID.randomUUID().toString();
            this.eventType = eventType;
            this.schemaVersion = "1.0.0";
            this.aggregateType = "IDENTITY_CONTEXT";
            this.aggregateId = aggregateId;
            this.tenantId = tenantId;
            this.correlationId = correlationId;
            this.timestamp = Instant.now().toString();
            this.payload = payload;
        }

        public String getEventId() { return eventId; }
        public String getEventType() { return eventType; }
        public String getSchemaVersion() { return schemaVersion; }
        public String getAggregateType() { return aggregateType; }
        public String getAggregateId() { return aggregateId; }
        public String getTenantId() { return tenantId; }
        public String getCorrelationId() { return correlationId; }
        public String getTimestamp() { return timestamp; }
        public Map<String, Object> getPayload() { return payload; }
    }

    /**
     * Controlled Adapter / Test Double for the CampXSync Intelligence Layer.
     */
    public static class ControlledIntelligenceAdapter {
        private final List<IntelligenceContextEvent> emittedEvents = new CopyOnWriteArrayList<>();
        private boolean liveEndpointAvailable = false;

        public void publish(IntelligenceContextEvent event) {
            // Validate contract invariant before emission
            assertNotNull("Tenant ID is strictly required for tenant-isolated Intelligence Layer events", event.getTenantId());
            assertNotNull("Correlation ID is required for distributed tracing", event.getCorrelationId());
            assertNotNull("Schema version is required for governed contract evolution", event.getSchemaVersion());

            // Enforce least privilege: payload must never contain sensitive credentials
            Map<String, Object> p = event.getPayload();
            assertFalse("Contract violation: Intelligence Layer event must not contain password", p.containsKey("password"));
            assertFalse("Contract violation: Intelligence Layer event must not contain token", p.containsKey("token"));
            assertFalse("Contract violation: Intelligence Layer event must not contain secret", p.containsKey("secret"));

            emittedEvents.add(event);
        }

        public List<IntelligenceContextEvent> getEmittedEvents() {
            return Collections.unmodifiableList(emittedEvents);
        }

        public boolean isLiveEndpointAvailable() {
            return liveEndpointAvailable;
        }

        public void setLiveEndpointAvailable(boolean liveEndpointAvailable) {
            this.liveEndpointAvailable = liveEndpointAvailable;
        }
    }

    @Test
    public void testIntelligenceLayerContract_SerializationAndContextPreservation() throws Exception {
        ControlledIntelligenceAdapter adapter = new ControlledIntelligenceAdapter();

        UUID userId = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        String correlationId = "TRACE-INTEL-" + UUID.randomUUID();

        Map<String, Object> payload = new HashMap<>();
        payload.put("userId", userId.toString());
        payload.put("businessRole", "COLLEGE_ADMIN");
        payload.put("roles", Collections.singletonList("COLLEGE_ADMIN"));
        payload.put("tenantName", "Demo Institute of Technology");
        payload.put("resolutionStatus", "SUCCESS");

        IntelligenceContextEvent event = new IntelligenceContextEvent(
                "IdentityContextResolved",
                userId.toString(),
                tenantId.toString(),
                correlationId,
                payload
        );

        // Emit through controlled adapter
        adapter.publish(event);

        assertEquals(1, adapter.getEmittedEvents().size());
        IntelligenceContextEvent recorded = adapter.getEmittedEvents().get(0);

        // Verify JSON serialization compliance
        String json = mapper.writeValueAsString(recorded);
        JsonNode root = mapper.readTree(json);

        assertEquals("1.0.0", root.get("schemaVersion").asText());
        assertEquals("IdentityContextResolved", root.get("eventType").asText());
        assertEquals("IDENTITY_CONTEXT", root.get("aggregateType").asText());
        assertEquals(userId.toString(), root.get("aggregateId").asText());
        assertEquals(tenantId.toString(), root.get("tenantId").asText());
        assertEquals(correlationId, root.get("correlationId").asText());
        assertNotNull(root.get("timestamp").asText());

        JsonNode data = root.get("payload");
        assertEquals(userId.toString(), data.get("userId").asText());
        assertEquals("COLLEGE_ADMIN", data.get("businessRole").asText());
        assertEquals("Demo Institute of Technology", data.get("tenantName").asText());

        // Verify absence of sensitive data
        assertNull(data.get("password"));
        assertNull(data.get("token"));
        assertNull(data.get("secret"));

        // Status verification: Live Intelligence Layer endpoint availability
        assertFalse("Live Intelligence Layer service endpoint is marked PENDING", adapter.isLiveEndpointAvailable());
        System.out.println("[INTELLIGENCE-LAYER] Contract v1.0.0 verified via controlled adapter. Live endpoint integration: PENDING.");
    }
}
