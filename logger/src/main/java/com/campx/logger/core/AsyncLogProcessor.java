package com.campx.logger.core;

import com.campx.logger.api.LogEvent;
import com.campx.logger.appender.LogAppender;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * High-throughput asynchronous log processor.
 * Offloads disk and console I/O to a background worker thread
 * so business operations in CampXSync ERP are never blocked.
 */
public class AsyncLogProcessor {

    private final BlockingQueue<LogEvent> queue;
    private final List<LogAppender> appenders = new ArrayList<>();
    private final Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger inFlight = new AtomicInteger(0);

    public AsyncLogProcessor(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity > 0 ? capacity : 10000);
        this.workerThread = new Thread(this::processQueue, "CampX-AsyncLogWorker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    public synchronized void addAppender(LogAppender appender) {
        if (appender != null && !appenders.contains(appender)) {
            appenders.add(appender);
        }
    }

    public synchronized void removeAppender(LogAppender appender) {
        if (appender != null) {
            appenders.remove(appender);
        }
    }

    public synchronized List<LogAppender> getAppenders() {
        return new ArrayList<>(appenders);
    }

    public int getQueueSize() {
        return queue.size();
    }

    public int getQueueCapacity() {
        return queue.size() + queue.remainingCapacity();
    }

    /**
     * Enqueues a log event. If queue is full, attempts offer with short timeout,
     * otherwise dispatches synchronously to avoid dropping critical audit/error logs.
     */
    public void enqueue(LogEvent event) {
        if (!running.get() || event == null) {
            return;
        }

        inFlight.incrementAndGet();
        boolean enqueued = queue.offer(event);
        if (!enqueued) {
            // Queue is full: fall back to synchronous dispatch for this event
            try {
                dispatchToAppenders(event);
            } finally {
                inFlight.decrementAndGet();
            }
        }
    }

    private void processQueue() {
        while (running.get() || !queue.isEmpty()) {
            try {
                LogEvent event = queue.poll(200, TimeUnit.MILLISECONDS);
                if (event != null) {
                    try {
                        dispatchToAppenders(event);
                    } finally {
                        inFlight.decrementAndGet();
                    }
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                break;
            } catch (Exception e) {
                System.err.println("[AsyncLogProcessor] Error processing log event: " + e.getMessage());
            }
        }
    }

    private void dispatchToAppenders(LogEvent event) {
        List<LogAppender> targetAppenders;
        synchronized (this) {
            targetAppenders = new ArrayList<>(appenders);
        }
        for (LogAppender appender : targetAppenders) {
            try {
                appender.append(event);
            } catch (Exception e) {
                System.err.println("[AsyncLogProcessor] Failed to write to appender " + appender.getName() + ": " + e.getMessage());
            }
        }
    }

    /**
     * Flushes queue and appenders synchronously.
     */
    public void flush() {
        // Wait until queue drains and all in-flight dispatches complete
        int maxWait = 50; // max 5 seconds
        while ((!queue.isEmpty() || inFlight.get() > 0) && maxWait-- > 0) {
            try {
                Thread.sleep(50);
            } catch (InterruptedException ignored) {
                break;
            }
        }

        synchronized (this) {
            for (LogAppender appender : appenders) {
                try {
                    appender.flush();
                } catch (Exception ignored) {}
            }
        }
    }

    /**
     * Graceful shutdown of async processor and underlying appenders.
     */
    public void shutdown() {
        if (running.compareAndSet(true, false)) {
            flush();
            workerThread.interrupt();
            try {
                workerThread.join(2000);
            } catch (InterruptedException ignored) {}

            synchronized (this) {
                for (LogAppender appender : appenders) {
                    try {
                        appender.close();
                    } catch (Exception ignored) {}
                }
                appenders.clear();
            }
        }
    }
}
