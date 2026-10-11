package com.campx.academic.resource.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Observability adapter collecting Prometheus metrics for ACD-08 Learning Resource Service.
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    private final AtomicLong registrationsTotal = new AtomicLong(0);
    private final AtomicLong publicationsTotal = new AtomicLong(0);
    private final AtomicLong archivesTotal = new AtomicLong(0);
    private final AtomicLong downloadsTotal = new AtomicLong(0);
    private final AtomicLong versionsCreatedTotal = new AtomicLong(0);
    private final AtomicLong policyDenialsTotal = new AtomicLong(0);
    private final AtomicLong outboxLag = new AtomicLong(0);
    private final AtomicLong dlqDepth = new AtomicLong(0);

    private final Map<String, AtomicLong> requestCounters = new ConcurrentHashMap<>();
    private final Map<String, AtomicLong> errorCounters = new ConcurrentHashMap<>();
    private final AtomicLong totalDurationMs = new AtomicLong(0);
    private final AtomicLong totalRequests = new AtomicLong(0);

    private MetricsCollector() {}

    public void recordRegistration() {
        registrationsTotal.incrementAndGet();
    }

    public void recordPublication() {
        publicationsTotal.incrementAndGet();
    }

    public void recordArchive() {
        archivesTotal.incrementAndGet();
    }

    public void recordDownload() {
        downloadsTotal.incrementAndGet();
    }

    public void recordVersionCreation() {
        versionsCreatedTotal.incrementAndGet();
    }

    public void recordPolicyDenial() {
        policyDenialsTotal.incrementAndGet();
    }

    public void setOutboxLag(long lag) {
        outboxLag.set(lag);
    }

    public void setDlqDepth(long depth) {
        dlqDepth.set(depth);
    }

    public void recordRequest(String method, String path, int statusCode, long durationMs) {
        String key = method + " " + statusCode;
        requestCounters.computeIfAbsent(key, k -> new AtomicLong(0)).incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
        totalRequests.incrementAndGet();
    }

    public void recordError(String errorCode) {
        if (errorCode == null) errorCode = "UNKNOWN_ERROR";
        errorCounters.computeIfAbsent(errorCode, k -> new AtomicLong(0)).incrementAndGet();
    }

    public String toPrometheusFormat() {
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP acd08_resource_registrations_total Total number of registered learning resources\n");
        sb.append("# TYPE acd08_resource_registrations_total counter\n");
        sb.append("acd08_resource_registrations_total ").append(registrationsTotal.get()).append("\n\n");

        sb.append("# HELP acd08_resource_publications_total Total published learning resource versions\n");
        sb.append("# TYPE acd08_resource_publications_total counter\n");
        sb.append("acd08_resource_publications_total ").append(publicationsTotal.get()).append("\n\n");

        sb.append("# HELP acd08_resource_archives_total Total archived learning resources\n");
        sb.append("# TYPE acd08_resource_archives_total counter\n");
        sb.append("acd08_resource_archives_total ").append(archivesTotal.get()).append("\n\n");

        sb.append("# HELP acd08_resource_downloads_total Total authorized download requests\n");
        sb.append("# TYPE acd08_resource_downloads_total counter\n");
        sb.append("acd08_resource_downloads_total ").append(downloadsTotal.get()).append("\n\n");

        sb.append("# HELP acd08_resource_versions_total Total versions created\n");
        sb.append("# TYPE acd08_resource_versions_total counter\n");
        sb.append("acd08_resource_versions_total ").append(versionsCreatedTotal.get()).append("\n\n");

        sb.append("# HELP acd08_policy_denials_total Total access policy denials\n");
        sb.append("# TYPE acd08_policy_denials_total counter\n");
        sb.append("acd08_policy_denials_total ").append(policyDenialsTotal.get()).append("\n\n");

        sb.append("# HELP acd08_outbox_lag Current pending outbox events\n");
        sb.append("# TYPE acd08_outbox_lag gauge\n");
        sb.append("acd08_outbox_lag ").append(outboxLag.get()).append("\n\n");

        sb.append("# HELP acd08_dlq_depth Current dead letter events depth\n");
        sb.append("# TYPE acd08_dlq_depth gauge\n");
        sb.append("acd08_dlq_depth ").append(dlqDepth.get()).append("\n\n");

        sb.append("# HELP acd08_http_requests_total Total HTTP requests handled\n");
        sb.append("# TYPE acd08_http_requests_total counter\n");
        for (Map.Entry<String, AtomicLong> e : requestCounters.entrySet()) {
            sb.append("acd08_http_requests_total{route=\"").append(e.getKey()).append("\"} ")
                    .append(e.getValue().get()).append("\n");
        }
        sb.append("\n");

        sb.append("# HELP acd08_errors_total Total errors by code\n");
        sb.append("# TYPE acd08_errors_total counter\n");
        for (Map.Entry<String, AtomicLong> e : errorCounters.entrySet()) {
            sb.append("acd08_errors_total{error_code=\"").append(e.getKey()).append("\"} ")
                    .append(e.getValue().get()).append("\n");
        }
        sb.append("\n");

        long count = totalRequests.get();
        double avgLatency = count > 0 ? (double) totalDurationMs.get() / count : 0.0;
        sb.append("# HELP acd08_http_latency_ms_avg Average request latency in milliseconds\n");
        sb.append("# TYPE acd08_http_latency_ms_avg gauge\n");
        sb.append("acd08_http_latency_ms_avg ").append(String.format(java.util.Locale.US, "%.2f", avgLatency)).append("\n");

        return sb.toString();
    }
}
