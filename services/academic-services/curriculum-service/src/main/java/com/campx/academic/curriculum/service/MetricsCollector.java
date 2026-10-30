package com.campx.academic.curriculum.service;

import com.campx.academic.curriculum.model.CurriculumModels.Curriculum;
import com.campx.academic.curriculum.model.CurriculumModels.CurriculumStatus;
import com.campx.academic.curriculum.model.CurriculumModels.OutboxEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-Memory Metrics Collector exposing Prometheus text exposition format (Story 69).
 * Tracks request rates, latencies, error codes, outbox backlog, and curriculum counts.
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
    public String toPrometheusFormat(CurriculumDomainService domainService) {
        StringBuilder sb = new StringBuilder();

        // 1. acd02_request_total
        sb.append("# HELP acd02_request_total Total HTTP requests handled by ACD-02\n");
        sb.append("# TYPE acd02_request_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestTotals.entrySet()) {
            sb.append(String.format("acd02_request_total{%s} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (requestTotals.isEmpty()) {
            sb.append("acd02_request_total{method=\"GET\",path=\"/api/v1/curricula\",status=\"200\"} 0\n");
        }

        // 2. acd02_request_duration_seconds
        sb.append("# HELP acd02_request_duration_seconds Total request latency in seconds\n");
        sb.append("# TYPE acd02_request_duration_seconds counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestDurationsMs.entrySet()) {
            double seconds = entry.getValue().get() / 1000.0;
            sb.append(String.format("acd02_request_duration_seconds{%s} %.3f\n", entry.getKey(), seconds));
        }

        // 3. acd02_error_total
        sb.append("# HELP acd02_error_total Total errors classified by errorCode\n");
        sb.append("# TYPE acd02_error_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : errorTotals.entrySet()) {
            sb.append(String.format("acd02_error_total{errorCode=\"%s\"} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (errorTotals.isEmpty()) {
            sb.append("acd02_error_total{errorCode=\"NONE\"} 0\n");
        }

        // 4. acd02_outbox_pending_count
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

        sb.append("# HELP acd02_outbox_pending_count Number of pending outbox events awaiting broker relay\n");
        sb.append("# TYPE acd02_outbox_pending_count gauge\n");
        sb.append(String.format("acd02_outbox_pending_count %d\n", pendingOutbox));

        // 5. acd02_outbox_published_total
        sb.append("# HELP acd02_outbox_published_total Total outbox events published to broker\n");
        sb.append("# TYPE acd02_outbox_published_total counter\n");
        sb.append(String.format("acd02_outbox_published_total %d\n", publishedOutbox));

        // 6. acd02_dlq_count
        sb.append("# HELP acd02_dlq_count Total dead letter queue events\n");
        sb.append("# TYPE acd02_dlq_count gauge\n");
        sb.append(String.format("acd02_dlq_count %d\n", dlqCount));

        // 7. acd02_curricula_total
        Map<CurriculumStatus, Integer> statusCounts = new ConcurrentHashMap<>();
        for (CurriculumStatus cs : CurriculumStatus.values()) {
            statusCounts.put(cs, 0);
        }
        if (domainService != null) {
            List<Curriculum> allCurricula = domainService.getAllCurricula();
            for (Curriculum c : allCurricula) {
                if (c.getStatus() != null) {
                    statusCounts.put(c.getStatus(), statusCounts.getOrDefault(c.getStatus(), 0) + 1);
                }
            }
        }

        sb.append("# HELP acd02_curricula_total Total curricula by lifecycle status\n");
        sb.append("# TYPE acd02_curricula_total gauge\n");
        for (Map.Entry<CurriculumStatus, Integer> entry : statusCounts.entrySet()) {
            sb.append(String.format("acd02_curricula_total{status=\"%s\"} %d\n", entry.getKey().name(), entry.getValue()));
        }

        // 8. acd02_validation_failure_total
        sb.append("# HELP acd02_validation_failure_total Total validation failures\n");
        sb.append("# TYPE acd02_validation_failure_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : validationFailures.entrySet()) {
            sb.append(String.format("acd02_validation_failure_total{type=\"%s\"} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (validationFailures.isEmpty()) {
            sb.append("acd02_validation_failure_total{type=\"SCHEMA\"} 0\n");
        }

        return sb.toString();
    }

    private String normalizePathForMetrics(String path) {
        if (path == null) return "/";
        if (path.startsWith("/api/v1/curricula/") || path.startsWith("/api/v1/academics/curricula/")) {
            return "/api/v1/curricula/{id}/*";
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
