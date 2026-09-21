package com.campx.logger.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnostic tool and code flow analyzer for CampXSync ERP log files.
 * Reconstructs distributed execution traces, pinpoints latency bottlenecks,
 * and extracts error cascades.
 */
public class LogAnalyzer {

    private static final Pattern LEVEL_PATTERN = Pattern.compile("\"level\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    private static final Pattern FLOW_ID_PATTERN = Pattern.compile("\"flowId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern OPERATION_PATTERN = Pattern.compile("\"operation\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DURATION_PATTERN = Pattern.compile("\"durationMs\"\\s*:\\s*(\\d+)");
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("\"traceId\"\\s*:\\s*\"([^\"]+)\"");

    public static LogAnalysisSummary analyze(File logFile) throws IOException {
        return analyze(logFile, 100); // default 100ms threshold for slow operations
    }

    public static LogAnalysisSummary analyze(File logFile, long slowThresholdMs) throws IOException {
        if (logFile == null || !logFile.exists()) {
            throw new IllegalArgumentException("Log file does not exist: " + (logFile != null ? logFile.getAbsolutePath() : "null"));
        }

        LogAnalysisSummary summary = new LogAnalysisSummary();

        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                line = line.trim();
                if (line.isEmpty()) continue;

                summary.incrementTotalRecords();

                String level = extract(LEVEL_PATTERN, line, "INFO");
                String message = extract(MESSAGE_PATTERN, line, "");
                String flowId = extract(FLOW_ID_PATTERN, line, null);
                String operation = extract(OPERATION_PATTERN, line, null);
                String traceId = extract(TRACE_ID_PATTERN, line, null);

                String durationStr = extract(DURATION_PATTERN, line, null);
                Long durationMs = null;
                if (durationStr != null) {
                    try {
                        durationMs = Long.parseLong(durationStr);
                    } catch (NumberFormatException ignored) {}
                }

                // Analyze level
                if ("ERROR".equalsIgnoreCase(level) || "FATAL".equalsIgnoreCase(level)) {
                    summary.incrementErrorCount();
                    summary.addErrorSummary("[" + level + "] " + (operation != null ? "(" + operation + ") " : "") + message);
                } else if ("WARN".equalsIgnoreCase(level)) {
                    summary.incrementWarnCount();
                }

                // Analyze slow operations
                if (durationMs != null && durationMs >= slowThresholdMs) {
                    summary.addSlowOperation("Op: " + (operation != null ? operation : "UNKNOWN") + " took " + durationMs + "ms (flow: " + flowId + ")");
                }

                // Reconstruct flow timelines
                String flowKey = flowId != null ? flowId : traceId;
                if (flowKey != null) {
                    String stepDesc = (operation != null ? "[" + operation + "] " : "") + message + (durationMs != null ? " (" + durationMs + "ms)" : "");
                    summary.addFlowStep(flowKey, stepDesc);
                }
            }
        }

        return summary;
    }

    private static String extract(Pattern pattern, String text, String defaultValue) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }

    /**
     * Standalone CLI runner for developers analyzing log files from the terminal.
     */
    public static void main(String[] args) {
        String filePath = args.length > 0 ? args[0] : "logs/campx-flow.jsonl";
        long threshold = args.length > 1 ? Long.parseLong(args[1]) : 50;

        File file = new File(filePath);
        if (!file.exists()) {
            System.err.println("File not found: " + file.getAbsolutePath());
            return;
        }

        try {
            LogAnalysisSummary summary = analyze(file, threshold);
            System.out.println(summary.generateReport());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
