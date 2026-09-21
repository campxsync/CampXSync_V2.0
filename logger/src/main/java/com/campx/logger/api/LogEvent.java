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
 */
public final class LogEvent {
    private final long timestamp;
    private final String threadName;
    private final String loggerName;
    private final LogLevel level;
    private final String message;
    private final Map<String, String> context;
    private final Set<String> tags;
    private final String flowId;
    private final String operation;
    private final Long durationMs;
    private final Throwable throwable;
    private final String callerClass;
    private final String callerMethod;
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

    public long getTimestamp() {
        return timestamp;
    }

    public String getThreadName() {
        return threadName;
    }

    public String getLoggerName() {
        return loggerName;
    }

    public LogLevel getLevel() {
        return level;
    }

    public String getMessage() {
        return message;
    }

    public Map<String, String> getContext() {
        return context;
    }

    public Set<String> getTags() {
        return tags;
    }

    public String getFlowId() {
        return flowId;
    }

    public String getOperation() {
        return operation;
    }

    public Long getDurationMs() {
        return durationMs;
    }

    public Throwable getThrowable() {
        return throwable;
    }

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
