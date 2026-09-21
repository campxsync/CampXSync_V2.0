package com.campx.logger.analysis;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Encapsulates the results of a log and code flow analysis session.
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

    public int getTotalRecordsParsed() { return totalRecordsParsed; }
    public void incrementTotalRecords() { this.totalRecordsParsed++; }

    public int getTraceCount() { return traceCount; }
    public void setTraceCount(int traceCount) { this.traceCount = traceCount; }

    public int getFlowCount() { return flowCount; }
    public void setFlowCount(int flowCount) { this.flowCount = flowCount; }

    public int getErrorCount() { return errorCount; }
    public void incrementErrorCount() { this.errorCount++; }

    public int getWarnCount() { return warnCount; }
    public void incrementWarnCount() { this.warnCount++; }

    public List<String> getSlowOperations() { return slowOperations; }
    public void addSlowOperation(String slowOp) { this.slowOperations.add(slowOp); }

    public List<String> getErrorSummaries() { return errorSummaries; }
    public void addErrorSummary(String error) { this.errorSummaries.add(error); }

    public Map<String, List<String>> getFlowTimelines() { return flowTimelines; }
    public void addFlowStep(String flowKey, String stepDescription) {
        this.flowTimelines.computeIfAbsent(flowKey, k -> new ArrayList<>()).add(stepDescription);
    }

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
