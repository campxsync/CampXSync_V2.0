package com.campx.logger.formatter;

import com.campx.logger.api.LogEvent;
import com.campx.logger.context.SecurityMasker;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Map;
import java.util.Set;
import java.util.TimeZone;

/**
 * High-speed, zero-dependency JSON formatter producing structured JSON lines (JSONL).
 * Designed for downstream log aggregators (ELK, Loki, Splunk) and code flow analysis.
 */
public class JsonFormatter implements LogFormatter {

    private final boolean maskSecurityData;
    private final ThreadLocal<SimpleDateFormat> isoFormat = ThreadLocal.withInitial(() -> {
        SimpleDateFormat sdf = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSS'Z'");
        sdf.setTimeZone(TimeZone.getTimeZone("UTC"));
        return sdf;
    });

    public JsonFormatter() {
        this(true);
    }

    public JsonFormatter(boolean maskSecurityData) {
        this.maskSecurityData = maskSecurityData;
    }

    @Override
    public String format(LogEvent event) {
        StringBuilder sb = new StringBuilder(512);
        sb.append("{");

        // Timestamp
        sb.append("\"timestamp\":").append(event.getTimestamp()).append(",");
        sb.append("\"isoTime\":\"").append(isoFormat.get().format(new Date(event.getTimestamp()))).append("\",");

        // Severity
        sb.append("\"level\":\"").append(event.getLevel().name()).append("\",");

        // Logger & Thread
        sb.append("\"logger\":\"").append(escape(event.getLoggerName())).append("\",");
        sb.append("\"thread\":\"").append(escape(event.getThreadName())).append("\",");

        // Message
        String rawMessage = event.getMessage();
        String msg = maskSecurityData ? SecurityMasker.mask(rawMessage) : rawMessage;
        sb.append("\"message\":\"").append(escape(msg)).append("\"");

        // Optional flowId & operation
        if (event.getFlowId() != null) {
            sb.append(",\"flowId\":\"").append(escape(event.getFlowId())).append("\"");
        }
        if (event.getOperation() != null) {
            sb.append(",\"operation\":\"").append(escape(event.getOperation())).append("\"");
        }
        if (event.getDurationMs() != null) {
            sb.append(",\"durationMs\":").append(event.getDurationMs());
        }

        // Tags
        Set<String> tags = event.getTags();
        if (!tags.isEmpty()) {
            sb.append(",\"tags\":[");
            int i = 0;
            for (String tag : tags) {
                if (i > 0) sb.append(",");
                sb.append("\"").append(escape(tag)).append("\"");
                i++;
            }
            sb.append("]");
        }

        // Context (MDC)
        Map<String, String> ctx = event.getContext();
        if (!ctx.isEmpty()) {
            sb.append(",\"context\":{");
            int j = 0;
            for (Map.Entry<String, String> entry : ctx.entrySet()) {
                if (j > 0) sb.append(",");
                sb.append("\"").append(escape(entry.getKey())).append("\":\"")
                  .append(escape(entry.getValue())).append("\"");
                j++;
            }
            sb.append("}");
        }

        // Caller info if present
        if (event.getCallerClass() != null) {
            sb.append(",\"callerClass\":\"").append(escape(event.getCallerClass())).append("\"");
        }
        if (event.getCallerMethod() != null) {
            sb.append(",\"callerMethod\":\"").append(escape(event.getCallerMethod())).append("\"");
        }
        if (event.getCallerLineNumber() > 0) {
            sb.append(",\"callerLine\":").append(event.getCallerLineNumber());
        }

        // Exception details
        if (event.getThrowable() != null) {
            Throwable t = event.getThrowable();
            sb.append(",\"exception\":{");
            sb.append("\"class\":\"").append(escape(t.getClass().getName())).append("\",");
            sb.append("\"message\":\"").append(escape(t.getMessage() != null ? t.getMessage() : "")).append("\",");
            sb.append("\"stackTrace\":\"").append(escape(event.getStackTraceAsString())).append("\"");
            sb.append("}");
        }

        sb.append("}").append(System.lineSeparator());
        return sb.toString();
    }

    private static String escape(String s) {
        if (s == null) {
            return "";
        }
        StringBuilder sb = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"':  sb.append("\\\""); break;
                case '\\': sb.append("\\\\"); break;
                case '\b': sb.append("\\b");  break;
                case '\f': sb.append("\\f");  break;
                case '\n': sb.append("\\n");  break;
                case '\r': sb.append("\\r");  break;
                case '\t': sb.append("\\t");  break;
                default:
                    if (c < ' ') {
                        String t = "000" + Integer.toHexString(c);
                        sb.append("\\u").append(t.substring(t.length() - 4));
                    } else {
                        sb.append(c);
                    }
                    break;
            }
        }
        return sb.toString();
    }
}
