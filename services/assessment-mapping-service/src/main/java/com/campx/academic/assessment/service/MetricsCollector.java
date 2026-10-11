package com.campx.academic.assessment.service;

import java.util.concurrent.atomic.AtomicLong;

/**
 * In-memory metrics aggregator for ACD-09 Assessment Mapping Service (US-060, US-061).
 * Tracks structure creations, component additions, outcome mappings, validation failures,
 * weightage conflicts, publication latency, and outbox/DLQ counts.
 */
public class MetricsCollector {

    private final AtomicLong totalRequests = new AtomicLong(0);
    private final AtomicLong structureCreations = new AtomicLong(0);
    private final AtomicLong componentOperations = new AtomicLong(0);
    private final AtomicLong outcomeMappingOperations = new AtomicLong(0);
    private final AtomicLong validationFailures = new AtomicLong(0);
    private final AtomicLong weightageConflicts = new AtomicLong(0);
    private final AtomicLong publications = new AtomicLong(0);
    private final AtomicLong retirements = new AtomicLong(0);
    private final AtomicLong outboxEventsEmitted = new AtomicLong(0);
    private final AtomicLong dlqEventsStored = new AtomicLong(0);
    private final AtomicLong dlqReplays = new AtomicLong(0);
    private final AtomicLong totalLatencyMs = new AtomicLong(0);

    public void recordRequest(long durationMs) {
        totalRequests.incrementAndGet();
        totalLatencyMs.addAndGet(durationMs);
    }

    public void recordStructureCreation() {
        structureCreations.incrementAndGet();
    }

    public void recordComponentOperation() {
        componentOperations.incrementAndGet();
    }

    public void recordOutcomeMappingOperation() {
        outcomeMappingOperations.incrementAndGet();
    }

    public void recordValidationFailure() {
        validationFailures.incrementAndGet();
    }

    public void recordWeightageConflict() {
        weightageConflicts.incrementAndGet();
    }

    public void recordPublication() {
        publications.incrementAndGet();
    }

    public void recordRetirement() {
        retirements.incrementAndGet();
    }

    public void recordOutboxEvent() {
        outboxEventsEmitted.incrementAndGet();
    }

    public void recordDlqEvent() {
        dlqEventsStored.incrementAndGet();
    }

    public void recordDlqReplay() {
        dlqReplays.incrementAndGet();
    }

    public long getTotalRequests() { return totalRequests.get(); }
    public long getStructureCreations() { return structureCreations.get(); }
    public long getComponentOperations() { return componentOperations.get(); }
    public long getOutcomeMappingOperations() { return outcomeMappingOperations.get(); }
    public long getValidationFailures() { return validationFailures.get(); }
    public long getWeightageConflicts() { return weightageConflicts.get(); }
    public long getPublications() { return publications.get(); }
    public long getRetirements() { return retirements.get(); }
    public long getOutboxEventsEmitted() { return outboxEventsEmitted.get(); }
    public long getDlqEventsStored() { return dlqEventsStored.get(); }
    public long getDlqReplays() { return dlqReplays.get(); }

    public double getAverageLatencyMs() {
        long reqs = totalRequests.get();
        return reqs == 0 ? 0.0 : (double) totalLatencyMs.get() / reqs;
    }

    public String toJson() {
        return "{"
                + "\"totalRequests\":" + totalRequests.get() + ","
                + "\"structureCreations\":" + structureCreations.get() + ","
                + "\"componentOperations\":" + componentOperations.get() + ","
                + "\"outcomeMappingOperations\":" + outcomeMappingOperations.get() + ","
                + "\"validationFailures\":" + validationFailures.get() + ","
                + "\"weightageConflicts\":" + weightageConflicts.get() + ","
                + "\"publications\":" + publications.get() + ","
                + "\"retirements\":" + retirements.get() + ","
                + "\"outboxEventsEmitted\":" + outboxEventsEmitted.get() + ","
                + "\"dlqEventsStored\":" + dlqEventsStored.get() + ","
                + "\"dlqReplays\":" + dlqReplays.get() + ","
                + "\"averageLatencyMs\":" + String.format("%.2f", getAverageLatencyMs())
                + "}";
    }
}
