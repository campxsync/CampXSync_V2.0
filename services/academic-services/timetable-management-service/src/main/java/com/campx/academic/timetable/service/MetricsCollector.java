package com.campx.academic.timetable.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-Memory Metrics Collector exposing Prometheus text exposition format (ACD-05, Story 55, §52).
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    private final Map<String, AtomicLong> requestTotals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestDurationsMs = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorTotals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> conflictTotals = new ConcurrentHashMap<>();
    private final AtomicLong publishSuccess = new AtomicLong(0);
    private final AtomicLong publishFailures = new AtomicLong(0);
    private final AtomicLong validationDurationsMs = new AtomicLong(0);
    private final AtomicLong validationRuns = new AtomicLong(0);
    private final AtomicLong idempotencyHits = new AtomicLong(0);

    public MetricsCollector() {}

    public void recordRequest(String method, String path, int status, long durationMs) {
        String normalizedPath = normalizePathForMetrics(path);
        String key = String.format("method=\"%s\",path=\"%s\",status=\"%d\"",
                method != null ? method : "UNKNOWN", normalizedPath, status);
        requestTotals.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();

        String durationKey = String.format("method=\"%s\",path=\"%s\"",
                method != null ? method : "UNKNOWN", normalizedPath);
        requestDurationsMs.computeIfAbsent(durationKey, k -> new AtomicLong(0)).addAndGet(durationMs);
    }

    public void recordError(String errorCode) {
        if (errorCode != null && !errorCode.isEmpty()) {
            errorTotals.computeIfAbsent(errorCode, k -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public void recordConflict(String conflictType, String severity) {
        String key = String.format("type=\"%s\",severity=\"%s\"", conflictType, severity);
        conflictTotals.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
    }

    public void recordPublish(boolean success) {
        if (success) {
            publishSuccess.incrementAndGet();
        } else {
            publishFailures.incrementAndGet();
        }
    }

    public void recordValidation(long durationMs) {
        validationRuns.incrementAndGet();
        validationDurationsMs.addAndGet(durationMs);
    }

    public void recordIdempotencyHit() {
        idempotencyHits.incrementAndGet();
    }

    public String toPrometheusFormat(TimetableDomainService domainService) {
        StringBuilder sb = new StringBuilder();

        // 1. acd05_request_total
        sb.append("# HELP acd05_request_total Total HTTP requests handled by ACD-05\n");
        sb.append("# TYPE acd05_request_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestTotals.entrySet()) {
            sb.append(String.format("acd05_request_total{%s} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (requestTotals.isEmpty()) {
            sb.append("acd05_request_total{method=\"GET\",path=\"/api/v1/academics/timetables\",status=\"200\"} 0\n");
        }

        // 2. acd05_request_duration_seconds
        sb.append("# HELP acd05_request_duration_seconds Total request latency in seconds\n");
        sb.append("# TYPE acd05_request_duration_seconds counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestDurationsMs.entrySet()) {
            double seconds = entry.getValue().get() / 1000.0;
            sb.append(String.format("acd05_request_duration_seconds{%s} %.3f\n", entry.getKey(), seconds));
        }

        // 3. acd05_error_total
        sb.append("# HELP acd05_error_total Total business and protocol errors\n");
        sb.append("# TYPE acd05_error_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : errorTotals.entrySet()) {
            sb.append(String.format("acd05_error_total{code=\"%s\"} %d\n", entry.getKey(), entry.getValue().get()));
        }

        // 4. acd05_conflicts_total
        sb.append("# HELP acd05_conflicts_total Total timetable conflict occurrences detected\n");
        sb.append("# TYPE acd05_conflicts_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : conflictTotals.entrySet()) {
            sb.append(String.format("acd05_conflicts_total{%s} %d\n", entry.getKey(), entry.getValue().get()));
        }

        // 5. acd05_publish_total
        sb.append("# HELP acd05_publish_total Total timetable publication events\n");
        sb.append("# TYPE acd05_publish_total counter\n");
        sb.append(String.format("acd05_publish_total{status=\"SUCCESS\"} %d\n", publishSuccess.get()));
        sb.append(String.format("acd05_publish_total{status=\"FAILED\"} %d\n", publishFailures.get()));

        // 6. acd05_validation_duration_seconds
        sb.append("# HELP acd05_validation_duration_seconds Total conflict validation processing seconds\n");
        sb.append("# TYPE acd05_validation_duration_seconds counter\n");
        sb.append(String.format("acd05_validation_duration_seconds %.3f\n", validationDurationsMs.get() / 1000.0));

        // 7. Gauges from domain service
        if (domainService != null) {
            sb.append("# HELP acd05_outbox_backlog Current number of pending outbox events\n");
            sb.append("# TYPE acd05_outbox_backlog gauge\n");
            sb.append(String.format("acd05_outbox_backlog %d\n", domainService.getPendingOutboxCount()));

            sb.append("# HELP acd05_dlq_events_total Total dead letter events\n");
            sb.append("# TYPE acd05_dlq_events_total gauge\n");
            sb.append(String.format("acd05_dlq_events_total %d\n", domainService.getDlqCount()));

            sb.append("# HELP acd05_stale_drafts_total Number of drafts older than stale threshold\n");
            sb.append("# TYPE acd05_stale_drafts_total gauge\n");
            sb.append(String.format("acd05_stale_drafts_total %d\n", domainService.getStaleDraftCount()));
        }

        return sb.toString();
    }

    private String normalizePathForMetrics(String path) {
        if (path == null) return "/";
        return path.replaceAll("/[0-9a-fA-F-]{8,}", "/{id}")
                   .replaceAll("/TT-[A-Za-z0-9_-]+", "/{id}")
                   .replaceAll("/ENT-[A-Za-z0-9_-]+", "/{entryId}")
                   .replaceAll("/BATCH-[A-Za-z0-9_-]+", "/{batchId}")
                   .replaceAll("/FAC-[A-Za-z0-9_-]+", "/{facultyId}")
                   .replaceAll("/ROOM-[A-Za-z0-9_-]+", "/{roomId}");
    }
}
