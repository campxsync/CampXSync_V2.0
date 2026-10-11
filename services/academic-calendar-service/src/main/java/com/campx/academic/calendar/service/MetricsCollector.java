package com.campx.academic.calendar.service;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory Prometheus-compatible metrics collector for ACD-07 Academic Calendar Service (US-055).
 */
public class MetricsCollector {

    private static final MetricsCollector INSTANCE = new MetricsCollector();

    public static MetricsCollector getInstance() {
        return INSTANCE;
    }

    private final AtomicLong requestCounter = new AtomicLong(0);
    private final AtomicLong idempotencyHits = new AtomicLong(0);
    private final Map<String, AtomicLong> errorsByCode = new ConcurrentHashMap<>();
    private final Map<Integer, AtomicLong> requestsByStatus = new ConcurrentHashMap<>();
    private final AtomicLong totalDurationMs = new AtomicLong(0);

    private MetricsCollector() {}

    public void recordRequest(String method, String path, int statusCode, long durationMs) {
        requestCounter.incrementAndGet();
        requestsByStatus.computeIfAbsent(statusCode, s -> new AtomicLong(0)).incrementAndGet();
        totalDurationMs.addAndGet(durationMs);
    }

    public void recordError(String errorCode) {
        if (errorCode != null) {
            errorsByCode.computeIfAbsent(errorCode, e -> new AtomicLong(0)).incrementAndGet();
        }
    }

    public void recordIdempotencyHit() {
        idempotencyHits.incrementAndGet();
    }

    public long getRequestCount() {
        return requestCounter.get();
    }

    public long getIdempotencyHits() {
        return idempotencyHits.get();
    }

    public String toPrometheusFormat(CalendarDomainService domainService) {
        StringBuilder sb = new StringBuilder();
        sb.append("# HELP acd07_requests_total Total number of HTTP requests processed\n");
        sb.append("# TYPE acd07_requests_total counter\n");
        sb.append("acd07_requests_total ").append(requestCounter.get()).append("\n\n");

        sb.append("# HELP acd07_idempotency_hits_total Total number of idempotency cache hits\n");
        sb.append("# TYPE acd07_idempotency_hits_total counter\n");
        sb.append("acd07_idempotency_hits_total ").append(idempotencyHits.get()).append("\n\n");

        sb.append("# HELP acd07_requests_duration_ms_total Sum of duration for all requests in ms\n");
        sb.append("# TYPE acd07_requests_duration_ms_total counter\n");
        sb.append("acd07_requests_duration_ms_total ").append(totalDurationMs.get()).append("\n\n");

        sb.append("# HELP acd07_requests_by_status Total HTTP requests partitioned by status\n");
        sb.append("# TYPE acd07_requests_by_status counter\n");
        for (Map.Entry<Integer, AtomicLong> e : requestsByStatus.entrySet()) {
            sb.append("acd07_requests_by_status{status=\"").append(e.getKey()).append("\"} ")
                    .append(e.getValue().get()).append("\n");
        }
        sb.append("\n");

        sb.append("# HELP acd07_errors_by_code Total domain errors partitioned by code\n");
        sb.append("# TYPE acd07_errors_by_code counter\n");
        for (Map.Entry<String, AtomicLong> e : errorsByCode.entrySet()) {
            sb.append("acd07_errors_by_code{code=\"").append(e.getKey()).append("\"} ")
                    .append(e.getValue().get()).append("\n");
        }
        sb.append("\n");

        if (domainService != null) {
            sb.append("# HELP acd07_calendars_total Current total academic calendars in storage\n");
            sb.append("# TYPE acd07_calendars_total gauge\n");
            sb.append("acd07_calendars_total ").append(domainService.getCalendarCount()).append("\n\n");

            sb.append("# HELP acd07_terms_total Current total calendar terms\n");
            sb.append("# TYPE acd07_terms_total gauge\n");
            sb.append("acd07_terms_total ").append(domainService.getTermCount()).append("\n\n");

            sb.append("# HELP acd07_events_total Current total calendar events\n");
            sb.append("# TYPE acd07_events_total gauge\n");
            sb.append("acd07_events_total ").append(domainService.getEventCount()).append("\n\n");

            sb.append("# HELP acd07_holidays_total Current total declared holidays\n");
            sb.append("# TYPE acd07_holidays_total gauge\n");
            sb.append("acd07_holidays_total ").append(domainService.getHolidayCount()).append("\n\n");

            sb.append("# HELP acd07_outbox_backlog Pending outbox events awaiting broker relay\n");
            sb.append("# TYPE acd07_outbox_backlog gauge\n");
            sb.append("acd07_outbox_backlog ").append(domainService.getPendingOutboxCount()).append("\n\n");

            sb.append("# HELP acd07_dlq_total Events routed to dead-letter queue\n");
            sb.append("# TYPE acd07_dlq_total gauge\n");
            sb.append("acd07_dlq_total ").append(domainService.getDlqCount()).append("\n");
        }

        return sb.toString();
    }
}
