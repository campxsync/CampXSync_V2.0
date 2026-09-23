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
 * High-throughput asynchronous log event processing engine.
 * <p>
 * Offloads console rendering, pattern formatting, and disk I/O to a dedicated background
 * daemon worker thread ({@code "CampX-AsyncLogWorker"}) backed by a bounded {@link ArrayBlockingQueue}.
 * <p>
 * If the internal queue fills under severe load, the processor dynamically falls back to synchronous
 * dispatch to guarantee that critical error and compliance audit records are never discarded.
 *
 * @see LogAppender
 * @see LogEvent
 */
public class AsyncLogProcessor {

    private final BlockingQueue<LogEvent> queue;
    private final List<LogAppender> appenders = new ArrayList<>();
    private final Thread workerThread;
    private final AtomicBoolean running = new AtomicBoolean(true);
    private final AtomicInteger inFlight = new AtomicInteger(0);

    /**
     * Initializes the asynchronous log processor with the specified ring buffer capacity.
     *
     * @param capacity maximum pending log events held in memory (defaults to 10,000 if &le; 0)
     */
    public AsyncLogProcessor(int capacity) {
        this.queue = new ArrayBlockingQueue<>(capacity > 0 ? capacity : 10000);
        this.workerThread = new Thread(this::processQueue, "CampX-AsyncLogWorker");
        this.workerThread.setDaemon(true);
        this.workerThread.start();
    }

    /**
     * Registers a new output appender to receive dispatched log events.
     *
     * @param appender the appender instance to attach
     */
    public synchronized void addAppender(LogAppender appender) {
        if (appender != null && !appenders.contains(appender)) {
            appenders.add(appender);
        }
    }

    /**
     * Unregisters an output appender from receiving future events.
     *
     * @param appender the appender instance to remove
     */
    public synchronized void removeAppender(LogAppender appender) {
        if (appender != null) {
            appenders.remove(appender);
        }
    }

    /**
     * Returns a thread-safe snapshot copy of all currently registered appenders.
     *
     * @return list of active appenders
     */
    public synchronized List<LogAppender> getAppenders() {
        return new ArrayList<>(appenders);
    }

    /**
     * Returns the number of log events currently pending in the processing queue.
     *
     * @return pending queue size
     */
    public int getQueueSize() {
        return queue.size();
    }

    /**
     * Returns the total capacity of the internal blocking queue.
     *
     * @return maximum queue capacity
     */
    public int getQueueCapacity() {
        return queue.size() + queue.remainingCapacity();
    }

    /**
     * Enqueues a log event for asynchronous processing.
     * If the queue is saturated, falls back to synchronous dispatch to prevent message drops.
     *
     * @param event the log event record to process
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

    /**
     * Continuous background loop draining queued events and broadcasting to registered appenders.
     */
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

    /**
     * Dispatches a single log event to all attached appenders, isolating failures per appender.
     *
     * @param event the log event to broadcast
     */
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
     * Blocks until all queued and in-flight log events have been processed and appender buffers are flushed.
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
     * Performs graceful shutdown: drains queue, interrupts worker thread, and closes appender resources.
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
