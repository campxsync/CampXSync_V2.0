package com.campx.logger.api;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fluent builder for contextual and structured log event creation.
 */
public class FluentLogBuilder {
    private final ILogger logger;
    private final LogLevel level;
    private final Map<String, String> context = new HashMap<>();
    private final Set<String> tags = new LinkedHashSet<>();
    private String flowId;
    private String operation;
    private Long durationMs;
    private Throwable throwable;

    public FluentLogBuilder(ILogger logger, LogLevel level) {
        this.logger = logger;
        this.level = level != null ? level : LogLevel.INFO;
    }

    public FluentLogBuilder tag(String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            this.tags.add(tag.trim());
        }
        return this;
    }

    public FluentLogBuilder withContext(String key, String value) {
        if (key != null && value != null) {
            this.context.put(key, value);
        }
        return this;
    }

    public FluentLogBuilder withContext(Map<String, String> entries) {
        if (entries != null) {
            this.context.putAll(entries);
        }
        return this;
    }

    public FluentLogBuilder withFlowId(String flowId) {
        this.flowId = flowId;
        return this;
    }

    public FluentLogBuilder withOperation(String operation) {
        this.operation = operation;
        return this;
    }

    public FluentLogBuilder withDuration(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    public FluentLogBuilder withThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    public void log(String message) {
        if (!logger.isEnabled(level)) {
            return;
        }
        LogEvent event = LogEvent.builder()
                .level(level)
                .loggerName(logger.getName())
                .message(message)
                .context(context)
                .tags(tags)
                .flowId(flowId)
                .operation(operation)
                .durationMs(durationMs)
                .throwable(throwable)
                .build();
        logger.log(event);
    }

    public void log(String format, Object... args) {
        if (!logger.isEnabled(level)) {
            return;
        }
        String formattedMessage = formatMessage(format, args);
        log(formattedMessage);
    }

    public void log() {
        log("");
    }

    private String formatMessage(String pattern, Object... args) {
        if (pattern == null || args == null || args.length == 0) {
            return pattern != null ? pattern : "";
        }
        StringBuilder sb = new StringBuilder();
        int argIndex = 0;
        int lastPos = 0;
        int placeholderPos;

        while ((placeholderPos = pattern.indexOf("{}", lastPos)) != -1) {
            sb.append(pattern, lastPos, placeholderPos);
            if (argIndex < args.length) {
                sb.append(args[argIndex++]);
            } else {
                sb.append("{}");
            }
            lastPos = placeholderPos + 2;
        }
        sb.append(pattern.substring(lastPos));

        // Append remaining args if any
        if (argIndex < args.length && args[argIndex] instanceof Throwable && this.throwable == null) {
            this.throwable = (Throwable) args[argIndex];
        }

        return sb.toString();
    }
}
