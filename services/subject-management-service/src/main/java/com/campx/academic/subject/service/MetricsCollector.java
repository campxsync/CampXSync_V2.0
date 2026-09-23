package com.campx.academic.subject.service;

import com.campx.academic.subject.model.SubjectModels.Subject;
import com.campx.academic.subject.model.SubjectModels.SubjectStatus;
import com.campx.academic.subject.model.SubjectModels.OutboxEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-Memory Metrics Collector exposing Prometheus text exposition format (Story 68, §51-52).
 * Tracks request rates, latencies, error codes, outbox backlog, and subject lifecycle counts.
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    private final Map<String, AtomicLong> requestTotals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestDurationsMs = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorTotals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> validationFailures = new ConcurrentHashMap<>();

    public MetricsCollector() {}

    /**
     * Records an HTTP request completion with latency.
     */
    public void recordRequest(String method, String path, int status, long durationMs) {
        String normalizedPath = normalizePathForMetrics(path);
        String key = String.format("method=\"%s\",path=\"%s\",status=\"%d\"",
                method != null ? method : "UNKNOWN", normalizedPath, status);
        requestTotals.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();

        String durationKey = String.format("method=\"%s\",path=\"%s\"",
                method != null ? method : "UNKNOWN", normalizedPath);
        requestDurationsMs.computeIfAbsent(durationKey, k -> new AtomicLong(0)).addAndGet(durationMs);
    }

    /**
     * Records an enterprise error code occurrence.
     */
    public void recordError(String errorCode) {
        if (errorCode != null && !errorCode.isEmpty()) {
            errorTotals.computeIfAbsent(errorCode, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * Records a validation failure type.
     */
    public void recordValidationFailure(String type) {
        if (type != null && !type.isEmpty()) {
            validationFailures.computeIfAbsent(type, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    /**
     * Generates Prometheus exposition format output.
     */
    public String toPrometheusFormat(SubjectDomainService domainService) {
        StringBuilder sb = new StringBuilder();

        // 1. acd03_request_total
        sb.append("# HELP acd03_request_total Total HTTP requests handled by ACD-03\n");
        sb.append("# TYPE acd03_request_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestTotals.entrySet()) {
            sb.append(String.format("acd03_request_total{%s} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (requestTotals.isEmpty()) {
            sb.append("acd03_request_total{method=\"GET\",path=\"/api/v1/academics/subjects\",status=\"200\"} 0\n");
        }

        // 2. acd03_request_duration_seconds
        sb.append("# HELP acd03_request_duration_seconds Total request latency in seconds\n");
        sb.append("# TYPE acd03_request_duration_seconds counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestDurationsMs.entrySet()) {
            double seconds = entry.getValue().get() / 1000.0;
            sb.append(String.format("acd03_request_duration_seconds{%s} %.3f\n", entry.getKey(), seconds));
        }

        // 3. acd03_error_total
        sb.append("# HELP acd03_error_total Total errors classified by errorCode\n");
        sb.append("# TYPE acd03_error_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : errorTotals.entrySet()) {
            sb.append(String.format("acd03_error_total{errorCode=\"%s\"} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (errorTotals.isEmpty()) {
            sb.append("acd03_error_total{errorCode=\"NONE\"} 0\n");
        }

        // 4. acd03_outbox_pending_count
        long pendingOutbox = 0;
        long publishedOutbox = 0;
        long dlqCount = 0;
        if (domainService != null) {
            List<OutboxEvent> allOutbox = domainService.getOutboxEvents();
            for (OutboxEvent evt : allOutbox) {
                if ("PENDING".equalsIgnoreCase(evt.getStatus())) {
                    pendingOutbox++;
                } else if ("PUBLISHED".equalsIgnoreCase(evt.getStatus())) {
                    publishedOutbox++;
                }
            }
            dlqCount = domainService.getDeadLetterEvents().size();
        }

        sb.append("# HELP acd03_outbox_pending_count Number of pending outbox events awaiting broker relay\n");
        sb.append("# TYPE acd03_outbox_pending_count gauge\n");
        sb.append(String.format("acd03_outbox_pending_count %d\n", pendingOutbox));

        // 5. acd03_outbox_published_total
        sb.append("# HELP acd03_outbox_published_total Total outbox events published to broker\n");
        sb.append("# TYPE acd03_outbox_published_total counter\n");
        sb.append(String.format("acd03_outbox_published_total %d\n", publishedOutbox));

        // 6. acd03_dlq_count
        sb.append("# HELP acd03_dlq_count Total dead letter queue events\n");
        sb.append("# TYPE acd03_dlq_count gauge\n");
        sb.append(String.format("acd03_dlq_count %d\n", dlqCount));

        // 7. acd03_subjects_total
        Map<SubjectStatus, Integer> statusCounts = new ConcurrentHashMap<>();
        for (SubjectStatus ss : SubjectStatus.values()) {
            statusCounts.put(ss, 0);
        }
        if (domainService != null) {
            List<Subject> allSubjects = domainService.getAllSubjects();
            for (Subject s : allSubjects) {
                if (s.getStatus() != null) {
                    statusCounts.put(s.getStatus(), statusCounts.getOrDefault(s.getStatus(), 0) + 1);
                }
            }
        }

        sb.append("# HELP acd03_subjects_total Total subjects by lifecycle status\n");
        sb.append("# TYPE acd03_subjects_total gauge\n");
        for (Map.Entry<SubjectStatus, Integer> entry : statusCounts.entrySet()) {
            sb.append(String.format("acd03_subjects_total{status=\"%s\"} %d\n", entry.getKey().name(), entry.getValue()));
        }

        // 8. acd03_validation_failure_total
        sb.append("# HELP acd03_validation_failure_total Total validation failures\n");
        sb.append("# TYPE acd03_validation_failure_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : validationFailures.entrySet()) {
            sb.append(String.format("acd03_validation_failure_total{type=\"%s\"} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (validationFailures.isEmpty()) {
            sb.append("acd03_validation_failure_total{type=\"SCHEMA\"} 0\n");
        }

        return sb.toString();
    }

    private String normalizePathForMetrics(String path) {
        if (path == null) return "/";
        if (path.startsWith("/api/v1/academics/subjects/") || path.startsWith("/api/v1/subjects/")) {
            return "/api/v1/academics/subjects/{id}/*";
        }
        return path;
    }

    public void reset() {
        requestTotals.clear();
        requestDurationsMs.clear();
        errorTotals.clear();
        validationFailures.clear();
    }
}
