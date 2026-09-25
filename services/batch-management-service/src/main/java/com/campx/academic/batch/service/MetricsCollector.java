package com.campx.academic.batch.service;

import com.campx.academic.batch.model.BatchModels.Batch;
import com.campx.academic.batch.model.BatchModels.BatchStatus;
import com.campx.academic.batch.model.BatchModels.OutboxEvent;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-Memory Metrics Collector exposing Prometheus text exposition format (Stories 61, 62, §52-53).
 * Tracks request rates, latencies, error codes, capacity conflicts, outbox backlog, DLQ,
 * and roster-count-vs-active-membership reconciliation status.
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    private final Map<String, AtomicLong> requestTotals = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> requestDurationsMs = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorTotals = new ConcurrentHashMap<>();
    private final AtomicLong capacityConflicts = new AtomicLong(0);
    private final AtomicLong idempotencyHits = new AtomicLong(0);
    private final AtomicLong validationFailures = new AtomicLong(0);

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
            if ("ACD_BATCH_CAPACITY_EXCEEDED".equals(errorCode)) {
                capacityConflicts.incrementAndGet();
            }
        }
    }

    /**
     * Records a capacity conflict.
     */
    public void recordCapacityConflict() {
        capacityConflicts.incrementAndGet();
    }

    /**
     * Records an idempotency cache hit.
     */
    public void recordIdempotencyHit() {
        idempotencyHits.incrementAndGet();
    }

    /**
     * Records a validation failure.
     */
    public void recordValidationFailure() {
        validationFailures.incrementAndGet();
    }

    /**
     * Generates Prometheus exposition format output.
     */
    public String toPrometheusFormat(BatchDomainService domainService) {
        StringBuilder sb = new StringBuilder();

        // 1. acd04_request_total
        sb.append("# HELP acd04_request_total Total HTTP requests handled by ACD-04\n");
        sb.append("# TYPE acd04_request_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestTotals.entrySet()) {
            sb.append(String.format("acd04_request_total{%s} %d\n", entry.getKey(), entry.getValue().get()));
        }
        if (requestTotals.isEmpty()) {
            sb.append("acd04_request_total{method=\"GET\",path=\"/api/v1/academics/batches\",status=\"200\"} 0\n");
        }

        // 2. acd04_request_duration_seconds
        sb.append("# HELP acd04_request_duration_seconds Total request latency in seconds\n");
        sb.append("# TYPE acd04_request_duration_seconds counter\n");
        for (Map.Entry<String, AtomicLong> entry : requestDurationsMs.entrySet()) {
            double seconds = entry.getValue().get() / 1000.0;
            sb.append(String.format("acd04_request_duration_seconds{%s} %.3f\n", entry.getKey(), seconds));
        }

        // 3. acd04_error_total
        sb.append("# HELP acd04_error_total Total errors classified by errorCode\n");
        sb.append("# TYPE acd04_error_total counter\n");
        for (Map.Entry<String, AtomicLong> entry : errorTotals.entrySet()) {
            sb.append(String.format("acd04_error_total{errorCode=\"%s\"} %d\n", entry.getKey(), entry.getValue().get()));
        }

        // 4. acd04_capacity_conflicts_total
        sb.append("# HELP acd04_capacity_conflicts_total Total capacity exceeded rejection events\n");
        sb.append("# TYPE acd04_capacity_conflicts_total counter\n");
        sb.append(String.format("acd04_capacity_conflicts_total %d\n", capacityConflicts.get()));

        // 5. acd04_idempotency_hits_total
        sb.append("# HELP acd04_idempotency_hits_total Total requests served via cached idempotency records\n");
        sb.append("# TYPE acd04_idempotency_hits_total counter\n");
        sb.append(String.format("acd04_idempotency_hits_total %d\n", idempotencyHits.get()));

        if (domainService != null) {
            // 6. acd04_batch_count
            sb.append("# HELP acd04_batch_count Current number of batches by status\n");
            sb.append("# TYPE acd04_batch_count gauge\n");
            Map<BatchStatus, Long> statusCounts = domainService.countBatchesByStatus();
            for (BatchStatus status : BatchStatus.values()) {
                long count = statusCounts.getOrDefault(status, 0L);
                sb.append(String.format("acd04_batch_count{status=\"%s\"} %d\n", status.name(), count));
            }

            // 7. acd04_roster_members_active
            sb.append("# HELP acd04_roster_members_active Current active enrolled student memberships\n");
            sb.append("# TYPE acd04_roster_members_active gauge\n");
            sb.append(String.format("acd04_roster_members_active %d\n", domainService.countActiveRosterMemberships()));

            // 8. acd04_capacity_overrides_active
            sb.append("# HELP acd04_capacity_overrides_active Current active capacity overrides\n");
            sb.append("# TYPE acd04_capacity_overrides_active gauge\n");
            sb.append(String.format("acd04_capacity_overrides_active %d\n", domainService.countActiveCapacityOverrides()));

            // 9. acd04_outbox_backlog
            sb.append("# HELP acd04_outbox_backlog Pending outbox events awaiting broker relay\n");
            sb.append("# TYPE acd04_outbox_backlog gauge\n");
            sb.append(String.format("acd04_outbox_backlog %d\n", domainService.getPendingOutboxCount()));

            // 10. acd04_dlq_events_total
            sb.append("# HELP acd04_dlq_events_total Total dead letter queue events\n");
            sb.append("# TYPE acd04_dlq_events_total counter\n");
            sb.append(String.format("acd04_dlq_events_total %d\n", domainService.getDeadLetterQueueCount()));

            // 11. acd04_roster_reconciliation_mismatches (Story 62)
            sb.append("# HELP acd04_roster_reconciliation_mismatches Count of batches where rosterCount does not equal active roster documents\n");
            sb.append("# TYPE acd04_roster_reconciliation_mismatches gauge\n");
            sb.append(String.format("acd04_roster_reconciliation_mismatches %d\n", domainService.checkRosterReconciliationMismatches()));
        }

        return sb.toString();
    }

    private String normalizePathForMetrics(String path) {
        if (path == null) return "/";
        // Normalize IDs like BATCH-123 or UUID to {id}
        return path.replaceAll("/BATCH-[a-zA-Z0-9_-]+", "/{id}")
                   .replaceAll("/SEC-[a-zA-Z0-9_-]+", "/{sectionId}")
                   .replaceAll("/STU-[a-zA-Z0-9_-]+", "/{studentId}")
                   .replaceAll("/OVR-[a-zA-Z0-9_-]+", "/{overrideId}");
    }

    public void reset() {
        requestTotals.clear();
        requestDurationsMs.clear();
        errorTotals.clear();
        capacityConflicts.set(0);
        idempotencyHits.set(0);
        validationFailures.set(0);
    }
}
