package com.campx.academic.analytics.service;

import com.campx.academic.analytics.exception.AnalyticsExceptions.AnalyticsValidationException;
import com.campx.academic.analytics.model.AnalyticsModels.*;

import java.time.Instant;
import java.util.*;

/**
 * Fact Normalizer for ACD-10: Reporting & Analytics Service.
 * Validates domain event envelopes and transforms heterogeneous events
 * into canonical AnalyticalFact representations (ACD10-US-022, ACD10-US-028).
 */
public class FactNormalizer {

    public AnalyticalFact normalize(EventEnvelope envelope) {
        validateEnvelope(envelope);

        AnalyticalFact fact = new AnalyticalFact();
        fact.setFactId("FACT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        fact.setTenantId(envelope.getTenantId());
        fact.setSourceEventId(envelope.getEventId());
        fact.setSourceEventType(envelope.getEventType());
        fact.setSourceService(envelope.getSource());
        fact.setEntityType(envelope.getEntityType());
        fact.setEntityId(envelope.getEntityId());
        fact.setOccurredAt(envelope.getOccurredAt());
        fact.setProcessedAt(Instant.now().toString());
        fact.setCreatedAt(Instant.now().toString());
        fact.setSchemaVersion(envelope.getVersion() != null ? envelope.getVersion() : "1.0");

        Map<String, Object> data = envelope.getData() != null ? envelope.getData() : Collections.emptyMap();

        // Extract common dimensions and entity references
        if (data.containsKey("batchId")) fact.setBatchRef(String.valueOf(data.get("batchId")));
        if (data.containsKey("batchRef")) fact.setBatchRef(String.valueOf(data.get("batchRef")));
        if (data.containsKey("subjectId")) fact.setSubjectRef(String.valueOf(data.get("subjectId")));
        if (data.containsKey("subjectRef")) fact.setSubjectRef(String.valueOf(data.get("subjectRef")));
        if (data.containsKey("courseId")) fact.setCourseRef(String.valueOf(data.get("courseId")));
        if (data.containsKey("courseRef")) fact.setCourseRef(String.valueOf(data.get("courseRef")));
        if (data.containsKey("facultyId")) fact.setFacultyRef(String.valueOf(data.get("facultyId")));
        if (data.containsKey("facultyRef")) fact.setFacultyRef(String.valueOf(data.get("facultyRef")));
        if (data.containsKey("termId")) fact.setTermRef(String.valueOf(data.get("termId")));
        if (data.containsKey("periodKey")) fact.setTermRef(String.valueOf(data.get("periodKey")));

        // Map canonical fact type and default classifications
        String eventType = envelope.getEventType();
        switch (eventType) {
            case "CourseCreated":
                fact.setFactType("COURSE_CREATED");
                fact.setDataClassification(DataClassification.PUBLIC);
                fact.getDimensions().put("departmentId", data.get("departmentId"));
                fact.getDimensions().put("credits", data.get("credits"));
                fact.getMeasures().put("count", 1);
                break;

            case "CurriculumPublished":
                fact.setFactType("CURRICULUM_PUBLISHED");
                fact.setDataClassification(DataClassification.INTERNAL);
                fact.getDimensions().put("curriculumId", data.get("curriculumId"));
                fact.getDimensions().put("totalUnits", data.get("totalUnits"));
                fact.getMeasures().put("plannedUnits", toInt(data.get("totalUnits"), 10));
                break;

            case "BatchCreated":
                fact.setFactType("BATCH_CREATED");
                fact.setDataClassification(DataClassification.INTERNAL);
                fact.getDimensions().put("batchName", data.get("batchName"));
                fact.getDimensions().put("departmentId", data.get("departmentId"));
                fact.getMeasures().put("studentCount", toInt(data.get("studentCount"), 0));
                break;

            case "TimetablePublished":
                fact.setFactType("TIMETABLE_PUBLISHED");
                fact.setDataClassification(DataClassification.INTERNAL);
                fact.getDimensions().put("departmentId", data.get("departmentId"));
                fact.getDimensions().put("roomRef", data.get("roomId"));
                fact.getDimensions().put("slotType", data.get("slotType"));
                fact.getMeasures().put("scheduledSlots", toInt(data.get("scheduledSlots"), 1));
                fact.getMeasures().put("executedSlots", toInt(data.get("executedSlots"), 0));
                fact.getMeasures().put("cancelledSlots", toInt(data.get("cancelledSlots"), 0));
                break;

            case "AttendanceMarked":
                fact.setFactType("ATTENDANCE_MARK");
                fact.setDataClassification(DataClassification.CONFIDENTIAL);
                fact.getDimensions().put("sessionId", data.get("sessionId"));
                fact.getDimensions().put("status", data.get("status"));
                fact.getDimensions().put("periodKey", data.getOrDefault("periodKey", "2026-27"));
                fact.getMeasures().put("presentCount", toInt(data.get("presentCount"), 0));
                fact.getMeasures().put("absentCount", toInt(data.get("absentCount"), 0));
                fact.getMeasures().put("leaveCount", toInt(data.get("leaveCount"), 0));
                fact.getMeasures().put("count", 1);
                break;

            case "AcademicCalendarPublished":
                fact.setFactType("CALENDAR_PUBLISHED");
                fact.setDataClassification(DataClassification.PUBLIC);
                fact.getDimensions().put("calendarId", data.get("calendarId"));
                fact.getDimensions().put("academicYear", data.get("academicYear"));
                fact.getMeasures().put("workingDays", toInt(data.get("workingDays"), 90));
                break;

            case "AssessmentMappingPublished":
                fact.setFactType("ASSESSMENT_MAPPING_PUBLISHED");
                fact.setDataClassification(DataClassification.INTERNAL);
                fact.getDimensions().put("assessmentId", data.get("assessmentId"));
                fact.getMeasures().put("weightage", toDouble(data.get("weightage"), 100.0));
                fact.getMeasures().put("coveragePercentage", toDouble(data.get("coveragePercentage"), 75.0));
                break;

            default:
                fact.setFactType("GENERIC_ANALYTICAL_FACT");
                fact.setDataClassification(DataClassification.INTERNAL);
                fact.getDimensions().putAll(data);
                break;
        }

        return fact;
    }

    public void validateEnvelope(EventEnvelope envelope) {
        if (envelope == null) {
            throw new AnalyticsValidationException("Event envelope cannot be null", "ACD10_INVALID_EVENT_ENVELOPE");
        }
        if (envelope.getEventId() == null || envelope.getEventId().trim().isEmpty()) {
            throw new AnalyticsValidationException("Event envelope missing required eventId", "ACD10_MISSING_EVENT_ID");
        }
        if (envelope.getEventType() == null || envelope.getEventType().trim().isEmpty()) {
            throw new AnalyticsValidationException("Event envelope missing required eventType", "ACD10_MISSING_EVENT_TYPE");
        }
        if (envelope.getSource() == null || envelope.getSource().trim().isEmpty()) {
            throw new AnalyticsValidationException("Event envelope missing required source", "ACD10_MISSING_EVENT_SOURCE");
        }
        if (envelope.getOccurredAt() == null || envelope.getOccurredAt().trim().isEmpty()) {
            throw new AnalyticsValidationException("Event envelope missing required occurredAt", "ACD10_MISSING_OCCURRED_AT");
        }
        if (envelope.getTenantId() == null || envelope.getTenantId().trim().isEmpty()) {
            throw new AnalyticsValidationException("Event envelope missing required tenantId", "ACD10_MISSING_TENANT_ID");
        }
        if (envelope.getCorrelationId() == null || envelope.getCorrelationId().trim().isEmpty()) {
            throw new AnalyticsValidationException("Event envelope missing required correlationId", "ACD10_MISSING_CORRELATION_ID");
        }
    }

    private int toInt(Object obj, int defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).intValue();
        try {
            return Integer.parseInt(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }

    private double toDouble(Object obj, double defaultValue) {
        if (obj == null) return defaultValue;
        if (obj instanceof Number) return ((Number) obj).doubleValue();
        try {
            return Double.parseDouble(String.valueOf(obj));
        } catch (NumberFormatException e) {
            return defaultValue;
        }
    }
}
