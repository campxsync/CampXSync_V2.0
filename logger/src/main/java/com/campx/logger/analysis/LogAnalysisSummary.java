package com.campx.logger.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the aggregated statistical and diagnostic results of an offline or live log analysis session.
 * <p>
 * Aggregates parsed record counts, warnings, errors, performance bottlenecks violating latency thresholds,
 * and reconstructed sequential flow timelines per flow/trace identifier.
 *
 * @see LogAnalyzer
 */
public class LogAnalysisSummary {
    private int totalRecordsParsed = 0;
    private int traceCount = 0;
    private int flowCount = 0;
    private int errorCount = 0;
    private int warnCount = 0;
    private final List<String> slowOperations = new ArrayList<>();
    private final List<String> errorSummaries = new ArrayList<>();
    private final Map<String, List<String>> flowTimelines = new HashMap<>();

    /**
     * Returns total log records ingested during the analysis session.
     *
     * @return record count
     */
    public int getTotalRecordsParsed() { return totalRecordsParsed; }

    /**
     * Increments the total ingested record counter by one.
     */
    public void incrementTotalRecords() { this.totalRecordsParsed++; }

    /**
     * Returns the number of distinct trace correlation contexts identified.
     *
     * @return trace count
     */
    public int getTraceCount() { return traceCount; }

    /**
     * Sets the number of distinct trace correlation contexts identified.
     *
     * @param traceCount trace count
     */
    public void setTraceCount(int traceCount) { this.traceCount = traceCount; }

    /**
     * Returns the count of distinct flow tracking executions detected.
     *
     * @return flow count
     */
    public int getFlowCount() { return flowCount; }

    /**
     * Sets the count of distinct flow tracking executions detected.
     *
     * @param flowCount flow count
     */
    public void setFlowCount(int flowCount) { this.flowCount = flowCount; }

    /**
     * Returns the total count of error and fatal level events detected.
     *
     * @return error count
     */
    public int getErrorCount() { return errorCount; }

    /**
     * Increments the error event counter by one.
     */
    public void incrementErrorCount() { this.errorCount++; }

    /**
     * Returns the total count of warning level events detected.
     *
     * @return warning count
     */
    public int getWarnCount() { return warnCount; }

    /**
     * Increments the warning event counter by one.
     */
    public void incrementWarnCount() { this.warnCount++; }

    /**
     * Returns the list of bottleneck operations that exceeded latency thresholds.
     *
     * @return list of slow operation diagnostic strings
     */
    public List<String> getSlowOperations() { return slowOperations; }

    /**
     * Records a detected performance bottleneck.
     *
     * @param slowOp diagnostic description of the slow operation
     */
    public void addSlowOperation(String slowOp) { this.slowOperations.add(slowOp); }

    /**
     * Returns the list of captured error and exception messages.
     *
     * @return list of error diagnostic summaries
     */
    public List<String> getErrorSummaries() { return errorSummaries; }

    /**
     * Records an error or failure summary.
     *
     * @param error error diagnostic summary
     */
    public void addErrorSummary(String error) { this.errorSummaries.add(error); }

    /**
     * Returns the map of reconstructed sequential execution timelines grouped by flow/trace ID.
     *
     * @return map of flow timelines
     */
    public Map<String, List<String>> getFlowTimelines() { return flowTimelines; }

    /**
     * Appends a milestone step to the specified flow timeline.
     *
     * @param flowKey         flow or trace identifier
     * @param stepDescription step milestone detail
     */
    public void addFlowStep(String flowKey, String stepDescription) {
        this.flowTimelines.computeIfAbsent(flowKey, k -> new ArrayList<>()).add(stepDescription);
    }

    /**
     * Compiles and formats a human-readable ASCII telemetry and flow analysis report.
     *
     * @return formatted report string
     */
    public String generateReport() {
        StringBuilder sb = new StringBuilder(512);
        sb.append("=================================================================\n");
        sb.append("               CAMPXSYNC LOG & FLOW ANALYSIS REPORT              \n");
        sb.append("=================================================================\n");
        sb.append("Total Records Parsed : ").append(totalRecordsParsed).append("\n");
        sb.append("Distinct Flows Traced: ").append(flowTimelines.size()).append("\n");
        sb.append("Errors Detected      : ").append(errorCount).append("\n");
        sb.append("Warnings Detected    : ").append(warnCount).append("\n");
        sb.append("Slow Operations      : ").append(slowOperations.size()).append("\n");
        sb.append("-----------------------------------------------------------------\n");

        if (!slowOperations.isEmpty()) {
            sb.append("\n[!] DETECTED BOTTLENECKS (> threshold):\n");
            for (String slow : slowOperations) {
                sb.append("  - ").append(slow).append("\n");
            }
        }

        if (!errorSummaries.isEmpty()) {
            sb.append("\n[!] ERROR & FAILURE LOGS:\n");
            for (String err : errorSummaries) {
                sb.append("  - ").append(err).append("\n");
            }
        }

        if (!flowTimelines.isEmpty()) {
            sb.append("\n[*] RECONSTRUCTED CODE FLOW TIMELINES:\n");
            for (Map.Entry<String, List<String>> entry : flowTimelines.entrySet()) {
                sb.append("  Flow: ").append(entry.getKey()).append("\n");
                for (String step : entry.getValue()) {
                    sb.append("    -> ").append(step).append("\n");
                }
            }
        }
        sb.append("=================================================================\n");
        return sb.toString();
    }
}
