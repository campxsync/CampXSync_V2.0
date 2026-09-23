package com.campx.logger.api;

import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Fluent builder for contextual, structured, and parameterized log event creation.
 * <p>
 * Provides method chaining for enriching log statements with tags, MDC-like context entries,
 * operation and flow tracking identifiers, duration metrics, and exception causes:
 * <pre>{@code
 * logger.atInfo()
 *       .tag("SECURITY")
 *       .withContext("userId", "USR-1002")
 *       .withOperation("AUTHENTICATE")
 *       .withDuration(45)
 *       .log("User {} authenticated successfully from IP {}", username, ipAddress);
 * }</pre>
 *
 * @see ILogger#atLevel(LogLevel)
 * @see ILogger#atInfo()
 * @see ILogger#atError()
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

    /**
     * Constructs a fluent log builder attached to a target logger and log level.
     *
     * @param logger the destination logger for dispatching the completed event
     * @param level  the severity level of the event (defaults to {@link LogLevel#INFO} if null)
     */
    public FluentLogBuilder(ILogger logger, LogLevel level) {
        this.logger = logger;
        this.level = level != null ? level : LogLevel.INFO;
    }

    /**
     * Attaches an indexable tag to the log event for targeted filtering and analysis.
     *
     * @param tag the tag string to attach
     * @return this builder instance for method chaining
     */
    public FluentLogBuilder tag(String tag) {
        if (tag != null && !tag.trim().isEmpty()) {
            this.tags.add(tag.trim());
        }
        return this;
    }

    /**
     * Adds a key-value contextual metadata entry to the log event.
     *
     * @param key   the context property key
     * @param value the context property value
     * @return this builder instance for method chaining
     */
    public FluentLogBuilder withContext(String key, String value) {
        if (key != null && value != null) {
            this.context.put(key, value);
        }
        return this;
    }

    /**
     * Adds multiple key-value contextual metadata entries to the log event.
     *
     * @param entries map of context properties to merge into the event
     * @return this builder instance for method chaining
     */
    public FluentLogBuilder withContext(Map<String, String> entries) {
        if (entries != null) {
            this.context.putAll(entries);
        }
        return this;
    }

    /**
     * Sets the distributed execution flow identifier for correlating this log event across service boundaries.
     *
     * @param flowId the distributed flow UUID or correlation key
     * @return this builder instance for method chaining
     */
    public FluentLogBuilder withFlowId(String flowId) {
        this.flowId = flowId;
        return this;
    }

    /**
     * Sets the business or system operation name associated with this event.
     *
     * @param operation the operation name
     * @return this builder instance for method chaining
     */
    public FluentLogBuilder withOperation(String operation) {
        this.operation = operation;
        return this;
    }

    /**
     * Sets the execution latency duration in milliseconds.
     *
     * @param durationMs duration in milliseconds
     * @return this builder instance for method chaining
     */
    public FluentLogBuilder withDuration(long durationMs) {
        this.durationMs = durationMs;
        return this;
    }

    /**
     * Attaches an exception/throwable cause to the log event.
     *
     * @param throwable the root cause throwable
     * @return this builder instance for method chaining
     */
    public FluentLogBuilder withThrowable(Throwable throwable) {
        this.throwable = throwable;
        return this;
    }

    /**
     * Finalizes and dispatches the log event with a static message string.
     * If the target logger is not enabled for this builder's level, evaluation is short-circuited.
     *
     * @param message the message to log
     */
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

    /**
     * Finalizes and dispatches the log event with SLF4J-style "{}" parameter substitution.
     * If the trailing argument is a {@link Throwable} and no throwable has been explicitly set,
     * it will automatically be bound as the cause.
     *
     * @param format message pattern containing "{}" placeholders
     * @param args   arguments to interpolate into the pattern
     */
    public void log(String format, Object... args) {
        if (!logger.isEnabled(level)) {
            return;
        }
        String formattedMessage = formatMessage(format, args);
        log(formattedMessage);
    }

    /**
     * Finalizes and dispatches the log event with an empty message, typically used for pure structured events.
     */
    public void log() {
        log("");
    }

    /**
     * Interpolates SLF4J-style "{}" placeholder tokens with argument values.
     *
     * @param pattern message template with "{}" tokens
     * @param args    arguments to bind into template placeholders
     * @return the interpolated message string
     */
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
