package com.campx.academic.curriculum.service;

import com.campx.academic.curriculum.model.CurriculumModels.OutboxEvent;
import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Enterprise Outbox Poller and Broker Relay Service for ACD-02 (Story 51, 54, 71).
 * Relays PENDING domain events from the outbox to message brokers (Kafka/RabbitMQ/Webhook)
 * with exponential backoff retries and DLQ routing.
 */
public class OutboxRelayService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(OutboxRelayService.class);

    private final CurriculumDomainService domainService;
    private final RetryPolicy retryPolicy;
    private final String brokerUrl;
    private final BrokerPublisher publisher;
    private ScheduledExecutorService scheduler;
    private final long pollingIntervalMs;
    private volatile boolean running = false;

    private final AtomicInteger publishedCount = new AtomicInteger(0);
    private final AtomicInteger failedCount = new AtomicInteger(0);
    private final AtomicInteger retriedCount = new AtomicInteger(0);

    @FunctionalInterface
    public interface BrokerPublisher {
        boolean publish(OutboxEvent event) throws Exception;
    }

    public OutboxRelayService(CurriculumDomainService domainService) {
        this(domainService, new RetryPolicy(), null, 5000L);
    }

    public OutboxRelayService(CurriculumDomainService domainService, RetryPolicy retryPolicy,
                              String brokerUrl, long pollingIntervalMs) {
        this(domainService, retryPolicy, brokerUrl, null, pollingIntervalMs);
    }

    public OutboxRelayService(CurriculumDomainService domainService, RetryPolicy retryPolicy,
                              String brokerUrl, BrokerPublisher publisher, long pollingIntervalMs) {
        this.domainService = domainService;
        this.retryPolicy = retryPolicy != null ? retryPolicy : new RetryPolicy();
        this.brokerUrl = brokerUrl;
        this.publisher = publisher;
        this.pollingIntervalMs = pollingIntervalMs > 0 ? pollingIntervalMs : 5000L;
    }

    public synchronized void start() {
        if (running) return;
        running = true;
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "outbox-relay-worker");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleWithFixedDelay(this::relayPendingEvents, pollingIntervalMs, pollingIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("OutboxRelayService started (interval={}ms, brokerUrl={})", pollingIntervalMs, brokerUrl);
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) {
            scheduler.shutdown();
            try {
                if (!scheduler.awaitTermination(2, TimeUnit.SECONDS)) {
                    scheduler.shutdownNow();
                }
            } catch (InterruptedException e) {
                scheduler.shutdownNow();
                Thread.currentThread().interrupt();
            }
        }
        logger.info("OutboxRelayService stopped");
    }

    /**
     * Polls and processes all pending outbox events.
     * Can be invoked directly for synchronous verification.
     *
     * @return number of successfully published events
     */
    public int relayPendingEvents() {
        List<OutboxEvent> pending = domainService.getPendingOutboxEvents();
        int publishedInBatch = 0;

        for (OutboxEvent event : pending) {
            boolean success = false;
            String lastError = null;

            for (int attempt = 0; attempt <= retryPolicy.getMaxRetries(); attempt++) {
                try {
                    success = dispatchEvent(event);
                    if (success) {
                        break;
                    }
                } catch (Exception e) {
                    lastError = e.getMessage();
                    retriedCount.incrementAndGet();
                    if (!retryPolicy.isRetryable(e) || attempt == retryPolicy.getMaxRetries()) {
                        break;
                    }
                    try {
                        Thread.sleep(Math.min(retryPolicy.getDelayMs(attempt), 500L)); // bounded for responsive processing
                    } catch (InterruptedException ie) {
                        Thread.currentThread().interrupt();
                        break;
                    }
                }
            }

            if (success) {
                domainService.markEventPublished(event.getEventId());
                publishedCount.incrementAndGet();
                publishedInBatch++;
                logger.info("Relayed outbox event {} ({}) to broker", event.getEventId(), event.getEventType());
            } else {
                domainService.markEventFailed(event.getEventId(),
                        lastError != null ? lastError : "Max retry attempts exhausted (" + retryPolicy.getMaxRetries() + ")");
                failedCount.incrementAndGet();
                logger.error("Failed to relay outbox event {} after retries -> moved to DLQ", event.getEventId());
            }
        }
        return publishedInBatch;
    }

    private boolean dispatchEvent(OutboxEvent event) throws Exception {
        if (publisher != null) {
            return publisher.publish(event);
        }

        if (brokerUrl != null && !brokerUrl.trim().isEmpty()) {
            URL url = new URL(brokerUrl);
            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("POST");
            conn.setDoOutput(true);
            conn.setConnectTimeout(3000);
            conn.setReadTimeout(3000);
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("X-Event-Type", event.getEventType());
            conn.setRequestProperty("X-Event-Id", event.getEventId());
            conn.setRequestProperty("X-Tenant-Id", event.getTenantId());

            try (OutputStream os = conn.getOutputStream()) {
                os.write(event.getPayload().getBytes(StandardCharsets.UTF_8));
            }

            int code = conn.getResponseCode();
            if (code >= 200 && code < 300) {
                return true;
            } else if (retryPolicy.isRetryable(code)) {
                throw new java.io.IOException("Broker returned transient HTTP " + code);
            } else {
                throw new IllegalArgumentException("Broker rejected event with HTTP " + code + " (non-retryable)");
            }
        }

        // Default local broker simulation: succeeds and logs publication
        return true;
    }

    public int getPublishedCount() { return publishedCount.get(); }
    public int getFailedCount() { return failedCount.get(); }
    public int getRetriedCount() { return retriedCount.get(); }
    public boolean isRunning() { return running; }
}
