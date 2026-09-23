package com.campx.logger.api;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Immutable log event carrying all contextual and diagnostic details
 * for analysis, debugging, and audit trails.
 * <p>
 * Encapsulates timestamp, thread information, logger origin, severity level,
 * contextual key-value pairs (MDC), categorization tags, flow tracing metadata,
 * execution duration, stack trace, and caller source code location.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public final class LogEvent {

    /** Timestamp in epoch milliseconds when the event was recorded. */
    private final long timestamp;

    /** Name of the thread that generated the log event. */
    private final String threadName;

    /** Name of the logger or category that recorded the event. */
    private final String loggerName;

    /** Severity level of the event. */
    private final LogLevel level;

    /** Formatted message string. */
    private final String message;

    /** Immutable contextual key-value mappings (MDC properties). */
    private final Map<String, String> context;

    /** Immutable set of categorizing tags (e.g., "SECURITY", "PAYMENT"). */
    private final Set<String> tags;

    /** Distributed workflow or correlation flow identifier. */
    private final String flowId;

    /** High-level operation or business action name. */
    private final String operation;

    /** Execution latency in milliseconds, or null if not applicable. */
    private final Long durationMs;

    /** Optional associated exception/throwable cause. */
    private final Throwable throwable;

    /** Calling class name resolved from call stack. */
    private final String callerClass;

    /** Calling method name resolved from call stack. */
    private final String callerMethod;

    /** Calling source line number resolved from call stack. */
    private final int callerLineNumber;

    private LogEvent(Builder builder) {
        this.timestamp = builder.timestamp > 0 ? builder.timestamp : System.currentTimeMillis();
        this.threadName = builder.threadName != null ? builder.threadName : Thread.currentThread().getName();
        this.loggerName = builder.loggerName != null ? builder.loggerName : "ROOT";
        this.level = builder.level != null ? builder.level : LogLevel.INFO;
        this.message = builder.message != null ? builder.message : "";
        this.context = builder.context != null ? Collections.unmodifiableMap(new HashMap<>(builder.context)) : Collections.emptyMap();
        this.tags = builder.tags != null ? Collections.unmodifiableSet(new LinkedHashSet<>(builder.tags)) : Collections.emptySet();
        this.flowId = builder.flowId;
        this.operation = builder.operation;
        this.durationMs = builder.durationMs;
        this.throwable = builder.throwable;
        this.callerClass = builder.callerClass;
        this.callerMethod = builder.callerMethod;
        this.callerLineNumber = builder.callerLineNumber;
    }

    /** @return Event timestamp in milliseconds since epoch. */
    public long getTimestamp() {
        return timestamp;
    }

    /** @return Name of the generating thread. */
    public String getThreadName() {
        return threadName;
    }

    /** @return Name of the originating logger. */
    public String getLoggerName() {
        return loggerName;
    }

    /** @return Severity log level. */
    public LogLevel getLevel() {
        return level;
    }

    /** @return The formatted log message. */
    public String getMessage() {
        return message;
    }

    /** @return Immutable map of contextual key-value pairs. */
    public Map<String, String> getContext() {
        return context;
    }

    /** @return Immutable set of categorization tags. */
    public Set<String> getTags() {
        return tags;
    }

    /** @return Correlated flow or transaction ID. */
    public String getFlowId() {
        return flowId;
    }

    /** @return Business operation name. */
    public String getOperation() {
        return operation;
    }

    /** @return Execution duration in milliseconds, or null if not tracked. */
    public Long getDurationMs() {
        return durationMs;
    }

    /** @return Associated Throwable exception, or null. */
    public Throwable getThrowable() {
        return throwable;
    }

    /**
     * Renders the associated exception stack trace as a string.
     *
     * @return Formatted stack trace string, or null if no throwable is attached.
     */
    public String getStackTraceAsString() {
        if (throwable == null) {
            return null;
        }
        StringWriter sw = new StringWriter();
        PrintWriter pw = new PrintWriter(sw);
        throwable.printStackTrace(pw);
        return sw.toString();
    }

    public String getCallerClass() {
        return callerClass;
    }

    public String getCallerMethod() {
        return callerMethod;
    }

    public int getCallerLineNumber() {
        return callerLineNumber;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {
        private long timestamp;
        private String threadName;
        private String loggerName;
        private LogLevel level;
        private String message;
        private Map<String, String> context = new HashMap<>();
        private Set<String> tags = new LinkedHashSet<>();
        private String flowId;
        private String operation;
        private Long durationMs;
        private Throwable throwable;
        private String callerClass;
        private String callerMethod;
        private int callerLineNumber = -1;

        public Builder timestamp(long timestamp) {
            this.timestamp = timestamp;
            return this;
        }

        public Builder threadName(String threadName) {
            this.threadName = threadName;
            return this;
        }

        public Builder loggerName(String loggerName) {
            this.loggerName = loggerName;
            return this;
        }

        public Builder level(LogLevel level) {
            this.level = level;
            return this;
        }

        public Builder message(String message) {
            this.message = message;
            return this;
        }

        public Builder context(Map<String, String> context) {
            if (context != null) {
                this.context.putAll(context);
            }
            return this;
        }

        public Builder addContext(String key, String value) {
            if (key != null && value != null) {
                this.context.put(key, value);
            }
            return this;
        }

        public Builder tags(Set<String> tags) {
            if (tags != null) {
                this.tags.addAll(tags);
            }
            return this;
        }

        public Builder addTag(String tag) {
            if (tag != null && !tag.trim().isEmpty()) {
                this.tags.add(tag.trim());
            }
            return this;
        }

        public Builder flowId(String flowId) {
            this.flowId = flowId;
            return this;
        }

        public Builder operation(String operation) {
            this.operation = operation;
            return this;
        }

        public Builder durationMs(Long durationMs) {
            this.durationMs = durationMs;
            return this;
        }

        public Builder throwable(Throwable throwable) {
            this.throwable = throwable;
            return this;
        }

        public Builder callerClass(String callerClass) {
            this.callerClass = callerClass;
            return this;
        }

        public Builder callerMethod(String callerMethod) {
            this.callerMethod = callerMethod;
            return this;
        }

        public Builder callerLineNumber(int callerLineNumber) {
            this.callerLineNumber = callerLineNumber;
            return this;
        }

        public LogEvent build() {
            return new LogEvent(this);
        }
    }
}
