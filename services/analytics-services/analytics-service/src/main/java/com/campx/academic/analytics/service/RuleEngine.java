package com.campx.academic.analytics.service;

import com.campx.academic.analytics.model.AnalyticsModels.*;

import java.time.Instant;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Deterministic Rules & Risk/Insight Engine for ACD-10: Reporting & Analytics Service.
 * Evaluates configurable institutional thresholds, prevents alert storms via state transitions,
 * and generates governed analytical events (ACD10-US-035 through ACD10-US-039).
 */
public class RuleEngine {

    // Configurable thresholds per tenant/institution
    private volatile double defaultAttendanceShortageThreshold = 75.0; // 75%
    private volatile double defaultUnderutilizationThreshold = 50.0;    // 50%
    private volatile double defaultProgressionTarget = 70.0;            // 70%

    // Deduplication tracking: key -> last alert timestamp / last risk level
    private final ConcurrentMap<String, RiskLevel> attendanceRiskStates = new ConcurrentHashMap<>();
    private final ConcurrentMap<String, Long> lastAlertTimestamps = new ConcurrentHashMap<>();
    private static final long DEDUP_WINDOW_MS = 3600000; // 1 hour deduplication window

    public double getAttendanceShortageThreshold() { return defaultAttendanceShortageThreshold; }
    public void setAttendanceShortageThreshold(double val) { this.defaultAttendanceShortageThreshold = val; }

    public double getUnderutilizationThreshold() { return defaultUnderutilizationThreshold; }
    public void setUnderutilizationThreshold(double val) { this.defaultUnderutilizationThreshold = val; }

    public double getProgressionTarget() { return defaultProgressionTarget; }
    public void setProgressionTarget(double val) { this.defaultProgressionTarget = val; }

    /**
     * Evaluates attendance metric for shortage risk.
     * Emits AttendanceRiskDetected event ONLY upon transition into alert state or after dedup window.
     */
    public List<EventEnvelope> evaluateAttendanceRisk(AttendanceMetric metric, String correlationId) {
        List<EventEnvelope> events = new ArrayList<>();
        String dedupKey = metric.getTenantId() + ":" + metric.getBatchRef() + ":" + metric.getSubjectRef();

        RiskLevel previousRisk = attendanceRiskStates.getOrDefault(dedupKey, RiskLevel.NONE);
        boolean isShortage = metric.getAttendancePercentage() < defaultAttendanceShortageThreshold &&
                (metric.getPresentCount() + metric.getAbsentCount() + metric.getLeaveCount()) > 0;

        RiskLevel newRisk = isShortage ? RiskLevel.HIGH : RiskLevel.NONE;
        metric.setRiskLevel(newRisk);
        metric.setShortageThreshold(defaultAttendanceShortageThreshold);

        // Check if transition occurred or if alert is warranted
        long now = System.currentTimeMillis();
        Long lastAlert = lastAlertTimestamps.get(dedupKey);
        boolean alertAllowed = (lastAlert == null) || (now - lastAlert > DEDUP_WINDOW_MS);

        if (newRisk == RiskLevel.HIGH && (previousRisk != RiskLevel.HIGH || alertAllowed)) {
            attendanceRiskStates.put(dedupKey, newRisk);
            lastAlertTimestamps.put(dedupKey, now);

            EventEnvelope alertEvent = new EventEnvelope();
            alertEvent.setEventId("EVT-RISK-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            alertEvent.setEventType("AttendanceRiskDetected");
            alertEvent.setTenantId(metric.getTenantId());
            alertEvent.setEntityId(metric.getMetricId());
            alertEvent.setOccurredAt(Instant.now().toString());
            alertEvent.setCorrelationId(correlationId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("batchId", metric.getBatchRef());
            data.put("subjectId", metric.getSubjectRef());
            data.put("attendancePercentage", metric.getAttendancePercentage());
            data.put("threshold", defaultAttendanceShortageThreshold);
            data.put("riskLevel", newRisk.name());
            data.put("asOf", metric.getAsOf());
            alertEvent.setData(data);

            events.add(alertEvent);
        } else if (newRisk != RiskLevel.HIGH) {
            attendanceRiskStates.put(dedupKey, newRisk);
        }

        // Canonical calculation event
        EventEnvelope calcEvent = createMetricCalculatedEvent(
                metric.getTenantId(), "ATTENDANCE_PERCENTAGE", "BATCH", metric.getBatchRef(),
                metric.getAttendancePercentage(), metric.getAsOf(), correlationId);
        events.add(calcEvent);

        return events;
    }

    /**
     * Evaluates timetable utilization metrics and emits TimetableUtilizationCalculated.
     */
    public List<EventEnvelope> evaluateTimetableUtilization(TimetableMetric metric, String correlationId) {
        List<EventEnvelope> events = new ArrayList<>();

        boolean underutilized = metric.getUtilizationPercentage() < defaultUnderutilizationThreshold &&
                metric.getScheduledSlots() > 0;

        EventEnvelope event = new EventEnvelope();
        event.setEventId("EVT-TT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        event.setEventType("TimetableUtilizationCalculated");
        event.setTenantId(metric.getTenantId());
        event.setEntityId(metric.getMetricId());
        event.setOccurredAt(Instant.now().toString());
        event.setCorrelationId(correlationId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("periodKey", metric.getPeriodKey());
        data.put("roomRef", metric.getRoomRef());
        data.put("facultyRef", metric.getFacultyRef());
        data.put("batchRef", metric.getBatchRef());
        data.put("slotType", metric.getSlotType());
        data.put("scheduledSlots", metric.getScheduledSlots());
        data.put("executedSlots", metric.getExecutedSlots());
        data.put("cancelledSlots", metric.getCancelledSlots());
        data.put("utilizationPercentage", metric.getUtilizationPercentage());
        data.put("isUnderutilized", underutilized);
        data.put("asOf", metric.getAsOf());
        event.setData(data);

        events.add(event);
        return events;
    }

    /**
     * Evaluates academic progression and emits AcademicInsightGenerated if targets missed.
     */
    public List<EventEnvelope> evaluateProgression(AcademicMetric metric, String correlationId) {
        List<EventEnvelope> events = new ArrayList<>();

        if (metric.getProgressionScore() < defaultProgressionTarget && metric.getPlannedUnits() > 0) {
            metric.setRiskLevel(RiskLevel.MEDIUM);

            EventEnvelope insight = new EventEnvelope();
            insight.setEventId("EVT-INSIGHT-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
            insight.setEventType("AcademicInsightGenerated");
            insight.setTenantId(metric.getTenantId());
            insight.setEntityId(metric.getMetricId());
            insight.setOccurredAt(Instant.now().toString());
            insight.setCorrelationId(correlationId);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("batchId", metric.getBatchRef());
            data.put("courseId", metric.getCourseRef());
            data.put("subjectId", metric.getSubjectRef());
            data.put("completionPercentage", metric.getCompletionPercentage());
            data.put("assessmentCoveragePercentage", metric.getAssessmentCoveragePercentage());
            data.put("progressionScore", metric.getProgressionScore());
            data.put("target", defaultProgressionTarget);
            data.put("insightType", "PROGRESSION_LAG_RISK");
            data.put("asOf", metric.getAsOf());
            insight.setData(data);

            events.add(insight);
        } else {
            metric.setRiskLevel(RiskLevel.NONE);
        }

        // Canonical calculation event
        EventEnvelope calcEvent = createMetricCalculatedEvent(
                metric.getTenantId(), "ACADEMIC_PROGRESSION", "BATCH", metric.getBatchRef(),
                metric.getProgressionScore(), metric.getAsOf(), correlationId);
        events.add(calcEvent);

        return events;
    }

    private EventEnvelope createMetricCalculatedEvent(
            String tenantId, String metricType, String scopeType, String scopeId,
            double value, String asOf, String correlationId) {
        EventEnvelope envelope = new EventEnvelope();
        envelope.setEventId("EVT-CALC-" + UUID.randomUUID().toString().substring(0, 8).toUpperCase());
        envelope.setEventType("AcademicMetricCalculated");
        envelope.setTenantId(tenantId);
        envelope.setOccurredAt(Instant.now().toString());
        envelope.setCorrelationId(correlationId);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("metricType", metricType);
        data.put("scopeType", scopeType);
        data.put("scopeId", scopeId);
        data.put("value", value);
        data.put("asOf", asOf != null ? asOf : Instant.now().toString());
        envelope.setData(data);

        return envelope;
    }
}
