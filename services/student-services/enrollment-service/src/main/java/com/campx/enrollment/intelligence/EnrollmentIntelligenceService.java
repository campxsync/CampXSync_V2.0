package com.campx.enrollment.intelligence;

import com.campx.enrollment.EnrollmentDomainService;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.campx.platform.intelligence.ControlledIntelligenceAdapter;
import com.campx.platform.intelligence.IntelligenceContextEvent;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Publishes STM-03 enrolment intelligence contracts through the controlled adapter.
 * Live Intelligence Layer delivery remains pending when the remote endpoint is unavailable.
 */
public class EnrollmentIntelligenceService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(EnrollmentIntelligenceService.class);
    private static final String SERVICE_NAME = "enrollment-service";

    private final ControlledIntelligenceAdapter adapter;
    private final ObjectMapper objectMapper = new ObjectMapper();

    public EnrollmentIntelligenceService() {
        this(new ControlledIntelligenceAdapter());
    }

    public EnrollmentIntelligenceService(ControlledIntelligenceAdapter adapter) {
        this.adapter = adapter == null ? new ControlledIntelligenceAdapter() : adapter;
    }

    public void publishStudentEnrolled(Map<String, Object> domainEvent) {
        if (domainEvent == null || domainEvent.get("tenantId") == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("enrolmentId", String.valueOf(domainEvent.get("enrolmentId")));
            payload.put("studentId", String.valueOf(domainEvent.get("studentId")));
            payload.put("applicationId", String.valueOf(domainEvent.get("applicationId")));
            payload.put("termId", String.valueOf(domainEvent.get("termId")));
            payload.put("totalCredits", domainEvent.get("totalCredits"));
            payload.put("courseIds", domainEvent.get("courseIds"));
            payload.put("correlationId", domainEvent.get("correlationId"));

            UUID tenantId = (UUID) domainEvent.get("tenantId");
            String enrolmentId = String.valueOf(domainEvent.get("enrolmentId"));
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(EnrollmentDomainService.STUDENT_ENROLLED)
                    .schemaVersion(EnrollmentDomainService.SCHEMA_VERSION)
                    .aggregateType("ENROLMENT")
                    .aggregateId(enrolmentId)
                    .tenantId(tenantId)
                    .sourceService(SERVICE_NAME)
                    .correlationId(String.valueOf(domainEvent.get("correlationId")))
                    .idempotencyKey("intel-student.enrolled.v1-" + tenantId + "-" + enrolmentId)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm03 intelligence contract emitted eventType={} tenantId={}",
                    EnrollmentDomainService.STUDENT_ENROLLED, tenantId);
        } catch (Exception ex) {
            logger.error("Failed to emit STM-03 intelligence event: {}", ex.getMessage(), ex);
        }
    }

    /**
     * Publishes STM-03-US-001 admission→enrolment conversion contract.
     * Live endpoint delivery remains pending when the adapter remote is unavailable.
     */
    public void publishEnrolmentOpened(Map<String, Object> domainEvent) {
        if (domainEvent == null || domainEvent.get("tenantId") == null) {
            return;
        }
        try {
            Map<String, Object> payload = new LinkedHashMap<String, Object>();
            payload.put("enrolmentId", String.valueOf(domainEvent.get("enrolmentId")));
            payload.put("studentId", String.valueOf(domainEvent.get("studentId")));
            payload.put("applicationId", String.valueOf(domainEvent.get("applicationId")));
            payload.put("programId", String.valueOf(domainEvent.get("programId")));
            payload.put("termId", String.valueOf(domainEvent.get("termId")));
            payload.put("status", domainEvent.get("status"));
            payload.put("correlationId", domainEvent.get("correlationId"));

            UUID tenantId = (UUID) domainEvent.get("tenantId");
            String enrolmentId = String.valueOf(domainEvent.get("enrolmentId"));
            IntelligenceContextEvent event = IntelligenceContextEvent.builder()
                    .eventType(EnrollmentDomainService.ENROLMENT_OPENED)
                    .schemaVersion(EnrollmentDomainService.SCHEMA_VERSION)
                    .aggregateType("ENROLMENT")
                    .aggregateId(enrolmentId)
                    .tenantId(tenantId)
                    .sourceService(SERVICE_NAME)
                    .correlationId(String.valueOf(domainEvent.get("correlationId")))
                    .idempotencyKey("intel-student.enrolment.opened.v1-" + tenantId + "-" + enrolmentId)
                    .payloadJson(objectMapper.writeValueAsString(payload))
                    .build();
            adapter.publish(event);
            logger.info("stm03 intelligence contract emitted eventType={} tenantId={}",
                    EnrollmentDomainService.ENROLMENT_OPENED, tenantId);
        } catch (Exception ex) {
            logger.error("Failed to emit STM-03 enrolment-opened intelligence event: {}", ex.getMessage(), ex);
        }
    }

    public ControlledIntelligenceAdapter getAdapter() {
        return adapter;
    }
}
