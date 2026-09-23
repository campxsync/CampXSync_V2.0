package com.campx.logger.analysis;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Diagnostic tool and code flow analyzer for CampXSync JSON-lines log files.
 * <p>
 * Parses structured JSONL log entries to reconstruct end-to-end execution timelines,
 * pinpoint latency bottlenecks exceeding customizable millisecond thresholds,
 * and isolate cascading errors across distributed service boundaries.
 *
 * @see LogAnalysisSummary
 */
public class LogAnalyzer {

    /** Regex extracting JSON "level" attribute. */
    private static final Pattern LEVEL_PATTERN = Pattern.compile("\"level\"\\s*:\\s*\"([^\"]+)\"");
    /** Regex extracting JSON "message" attribute. */
    private static final Pattern MESSAGE_PATTERN = Pattern.compile("\"message\"\\s*:\\s*\"([^\"]*)\"");
    /** Regex extracting JSON "flowId" attribute. */
    private static final Pattern FLOW_ID_PATTERN = Pattern.compile("\"flowId\"\\s*:\\s*\"([^\"]+)\"");
    /** Regex extracting JSON "operation" attribute. */
    private static final Pattern OPERATION_PATTERN = Pattern.compile("\"operation\"\\s*:\\s*\"([^\"]+)\"");
    /** Regex extracting JSON "durationMs" attribute. */
    private static final Pattern DURATION_PATTERN = Pattern.compile("\"durationMs\"\\s*:\\s*(\\d+)");
    /** Regex extracting JSON "traceId" attribute. */
    private static final Pattern TRACE_ID_PATTERN = Pattern.compile("\"traceId\"\\s*:\\s*\"([^\"]+)\"");

    /**
     * Analyzes the specified JSON-lines log file using a default 100ms latency bottleneck threshold.
     *
     * @param logFile the JSONL file to analyze
     * @return populated {@link LogAnalysisSummary}
     * @throws IOException if file reading fails
     */
    public static LogAnalysisSummary analyze(File logFile) throws IOException {
        return analyze(logFile, 100); // default 100ms threshold for slow operations
    }

    /**
     * Analyzes the specified JSON-lines log file with a custom latency bottleneck threshold.
     *
     * @param logFile         the JSONL file to analyze
     * @param slowThresholdMs duration threshold in milliseconds above which operations are flagged
     * @return populated {@link LogAnalysisSummary}
     * @throws IOException              if file reading fails
     * @throws IllegalArgumentException if the file does not exist
     */
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

    /**
     * Extracts the first regex capturing group from the input string, or returns the default value if no match.
     *
     * @param pattern      regex pattern containing at least one capturing group
     * @param text         target text to match against
     * @param defaultValue fallback string if no match is found
     * @return captured value or defaultValue
     */
    private static String extract(Pattern pattern, String text, String defaultValue) {
        Matcher m = pattern.matcher(text);
        if (m.find()) {
            return m.group(1);
        }
        return defaultValue;
    }

    /**
     * Standalone CLI runner allowing operators and engineers to analyze log files directly from the command line.
     * <pre>{@code
     * java com.campx.logger.analysis.LogAnalyzer logs/campx-flow.jsonl 100
     * }</pre>
     *
     * @param args optional arguments: args[0] = log file path, args[1] = slow threshold in ms
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
