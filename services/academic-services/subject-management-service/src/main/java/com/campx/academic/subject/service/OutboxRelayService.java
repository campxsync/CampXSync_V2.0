package com.campx.academic.subject.service;

import com.campx.academic.subject.model.SubjectModels.OutboxEvent;
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
 * Enterprise Outbox Poller and Broker Relay Service for ACD-03 (Story 50, 54, §30-33).
 * Relays PENDING domain events from outbox_events to message brokers (Kafka/RabbitMQ/Webhook)
 * with exponential backoff retries and DLQ routing.
 */
public class OutboxRelayService {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(OutboxRelayService.class);

    private final SubjectDomainService domainService;
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

    public OutboxRelayService(SubjectDomainService domainService) {
        this(domainService, new RetryPolicy(), null, 5000L);
    }

    public OutboxRelayService(SubjectDomainService domainService, RetryPolicy retryPolicy,
                              String brokerUrl, long pollingIntervalMs) {
        this(domainService, retryPolicy, brokerUrl, null, pollingIntervalMs);
    }

    public OutboxRelayService(SubjectDomainService domainService, RetryPolicy retryPolicy,
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
            Thread t = new Thread(r, "acd03-outbox-relay");
            t.setDaemon(true);
            return t;
        });

        scheduler.scheduleWithFixedDelay(this::pollAndRelay, 1000L, pollingIntervalMs, TimeUnit.MILLISECONDS);
        logger.info("ACD-03 OutboxRelayService started (pollingInterval={}ms, brokerUrl={})",
                pollingIntervalMs, brokerUrl != null ? brokerUrl : "in-memory-simulation");
    }

    public synchronized void stop() {
        if (!running) return;
        running = false;
        if (scheduler != null) {
            scheduler.shutdownNow();
        }
        logger.info("ACD-03 OutboxRelayService stopped (published={}, retried={}, failed={})",
                publishedCount.get(), retriedCount.get(), failedCount.get());
    }

    /**
     * Polls pending outbox records and attempts relay to external message broker.
     */
    public int pollAndRelay() {
        if (domainService == null) return 0;
        List<OutboxEvent> pending = domainService.getPendingOutboxEvents();
        int relayed = 0;

        for (OutboxEvent event : pending) {
            boolean success = false;
            try {
                if (publisher != null) {
                    success = publisher.publish(event);
                } else if (brokerUrl != null && !brokerUrl.isEmpty()) {
                    success = postToBroker(brokerUrl, event);
                } else {
                    // Default in-memory simulated broker acknowledgment
                    success = true;
                }

                if (success) {
                    event.setStatus("PUBLISHED");
                    publishedCount.incrementAndGet();
                    relayed++;
                    logger.debug("[OutboxRelay] Event {} ({}) published to broker", event.getEventId(), event.getEventType());
                } else {
                    handleFailure(event, "Broker returned non-success response");
                }
            } catch (Exception e) {
                logger.warn("[OutboxRelay] Failed to publish event {} (attempt {}): {}",
                        event.getEventId(), event.getAttempts() + 1, e.getMessage());
                handleFailure(event, e.getMessage());
            }
        }
        return relayed;
    }

    private void handleFailure(OutboxEvent event, String reason) {
        int currentAttempts = event.getAttempts() + 1;
        event.setAttempts(currentAttempts);

        if (currentAttempts >= retryPolicy.getMaxRetries()) {
            event.setStatus("FAILED");
            failedCount.incrementAndGet();
            logger.error("[OutboxRelay] Outbox event {} exceeded max retries ({}). Routing to Dead Letter Queue: {}",
                    event.getEventId(), retryPolicy.getMaxRetries(), reason);
            domainService.routeToDeadLetterQueue(event, reason);
        } else {
            retriedCount.incrementAndGet();
            long delay = retryPolicy.getDelayMs(currentAttempts);
            logger.warn("[OutboxRelay] Will retry event {} after {}ms backoff (attempt {}/{})",
                    event.getEventId(), delay, currentAttempts, retryPolicy.getMaxRetries());
        }
    }

    private boolean postToBroker(String urlStr, OutboxEvent event) throws Exception {
        URL url = new URL(urlStr);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setConnectTimeout(5000);
        conn.setReadTimeout(10000);
        conn.setDoOutput(true);
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("X-Event-Id", event.getEventId());
        conn.setRequestProperty("X-Event-Type", event.getEventType());
        conn.setRequestProperty("X-Correlation-Id", event.getCorrelationId() != null ? event.getCorrelationId() : "");

        try (OutputStream os = conn.getOutputStream()) {
            os.write(event.getPayload().getBytes(StandardCharsets.UTF_8));
        }

        int code = conn.getResponseCode();
        return code >= 200 && code < 300;
    }

    public int getPublishedCount() { return publishedCount.get(); }
    public int getFailedCount() { return failedCount.get(); }
    public int getRetriedCount() { return retriedCount.get(); }
    public boolean isRunning() { return running; }
}
