package com.campx.logger.formatter;

import com.campx.logger.api.LogEvent;
import com.campx.logger.api.LogLevel;
import com.campx.logger.context.LogContext;
import com.campx.logger.context.SecurityMasker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;

/**
 * High-performance pattern formatter for human-readable log files and terminal console output.
 * <p>
 * Formats events into standard readable patterns:
 * <pre>{@code
 * [2026-09-23 10:15:30.123] INFO  [main] [trace=TRC-101 tenant=TEN-01 user=USR-50] com.campx.service.Worker - Task completed (duration=15ms)
 * }</pre>
 * Supports configurable ANSI terminal colorization, contextual MDC metadata interpolation,
 * execution duration metrics, and automated regex-driven sensitive data masking.
 *
 * @see LogFormatter
 * @see LogContext
 * @see SecurityMasker
 */
public class PatternFormatter implements LogFormatter {

    /** ANSI control code to reset terminal formatting styles and colors. */
    public static final String ANSI_RESET = "\u001B[0m";
    /** ANSI control code for bold red text. */
    public static final String ANSI_RED = "\u001B[31m";
    /** ANSI control code for bold green text. */
    public static final String ANSI_GREEN = "\u001B[32m";
    /** ANSI control code for bold yellow text. */
    public static final String ANSI_YELLOW = "\u001B[33m";
    /** ANSI control code for bold blue text. */
    public static final String ANSI_BLUE = "\u001B[34m";
    /** ANSI control code for bold purple text. */
    public static final String ANSI_PURPLE = "\u001B[35m";
    /** ANSI control code for bold cyan text. */
    public static final String ANSI_CYAN = "\u001B[36m";
    /** ANSI control code for bold white text. */
    public static final String ANSI_WHITE = "\u001B[37m";

    private final boolean colorEnabled;
    private final boolean maskSecurityData;
    private final ThreadLocal<SimpleDateFormat> dateFormat = ThreadLocal.withInitial(() ->
            new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS")
    );

    /**
     * Initializes a monochrome pattern formatter with security data masking enabled.
     */
    public PatternFormatter() {
        this(false, true);
    }

    /**
     * Initializes a pattern formatter with explicit color and masking configurations.
     *
     * @param colorEnabled     {@code true} to embed ANSI color escape sequences in output
     * @param maskSecurityData {@code true} to mask sensitive credentials in output
     */
    public PatternFormatter(boolean colorEnabled, boolean maskSecurityData) {
        this.colorEnabled = colorEnabled;
        this.maskSecurityData = maskSecurityData;
    }

    /**
     * Formats the log event according to standard CampXSync pattern conventions.
     *
     * @param event the structured log event to format
     * @return the serialized textual log entry ending with system newline
     */
    @Override
    public String format(LogEvent event) {
        StringBuilder sb = new StringBuilder(256);

        String timestampStr = dateFormat.get().format(new Date(event.getTimestamp()));

        if (colorEnabled) {
            sb.append(ANSI_WHITE).append("[").append(timestampStr).append("] ").append(ANSI_RESET);
            sb.append(getColorForLevel(event.getLevel()))
              .append(String.format("%-5s", event.getLevel().name()))
              .append(ANSI_RESET)
              .append(" ");
        } else {
            sb.append("[").append(timestampStr).append("] ")
              .append(String.format("%-5s", event.getLevel().name()))
              .append(" ");
        }

        // Thread
        sb.append("[").append(event.getThreadName()).append("] ");

        // Correlation context (traceId, tenantId, userId)
        Map<String, String> ctx = event.getContext();
        String traceId = ctx.get(LogContext.KEY_TRACE_ID);
        String tenantId = ctx.get(LogContext.KEY_TENANT_ID);
        String userId = ctx.get(LogContext.KEY_USER_ID);

        sb.append("[");
        if (traceId != null) {
            sb.append("trace=").append(traceId).append(" ");
        }
        if (tenantId != null) {
            sb.append("tenant=").append(tenantId).append(" ");
        }
        if (userId != null) {
            sb.append("user=").append(userId).append(" ");
        }
        if (event.getFlowId() != null) {
            sb.append("flow=").append(event.getFlowId()).append(" ");
        }
        if (sb.charAt(sb.length() - 1) == ' ') {
            sb.setLength(sb.length() - 1);
        }
        sb.append("] ");

        // Logger name
        sb.append(event.getLoggerName()).append(" - ");

        // Message with masking
        String rawMessage = event.getMessage();
        String finalMessage = maskSecurityData ? SecurityMasker.mask(rawMessage) : rawMessage;
        sb.append(finalMessage);

        if (event.getDurationMs() != null) {
            sb.append(" (duration=").append(event.getDurationMs()).append("ms)");
        }

        sb.append(System.lineSeparator());

        // Stack trace if present
        if (event.getThrowable() != null) {
            sb.append(event.getStackTraceAsString());
        }

        return sb.toString();
    }

    /**
     * Maps a {@link LogLevel} to its corresponding ANSI terminal color sequence.
     *
     * @param level severity level to colorize
     * @return ANSI color code string
     */
    private String getColorForLevel(LogLevel level) {
        switch (level) {
            case TRACE: return ANSI_CYAN;
            case DEBUG: return ANSI_BLUE;
            case INFO:  return ANSI_GREEN;
            case WARN:  return ANSI_YELLOW;
            case ERROR:
            case FATAL: return ANSI_RED;
            case AUDIT: return ANSI_PURPLE;
            default:    return ANSI_RESET;
        }
    }
}
