package com.campx.logger.core;

import com.campx.logger.CampXLogger;
import com.campx.logger.api.AuditEvent;
import com.campx.logger.api.FlowTracker;
import com.campx.logger.api.FluentLogBuilder;
import com.campx.logger.api.ILogger;
import com.campx.logger.api.LogEvent;
import com.campx.logger.api.LogLevel;
import com.campx.logger.context.LogContext;

import java.util.HashMap;
import java.util.Map;

/**
 * Core concrete implementation of the {@link CampXLogger} interface.
 * <p>
 * Supports category-based logging, dynamic runtime severity level overrides, SLF4J-style
 * "{}" argument interpolation, stack frame inspection for debug/error events,
 * and automatic context merging with {@link LogContext}.
 *
 * @see CampXLogger
 * @see ILogger
 * @see LogManager
 */
public class LoggerImpl implements CampXLogger {

    private final String name;
    private volatile LogLevel levelOverride = null;

    /**
     * Constructs a logger instance associated with the specified category name.
     *
     * @param name category or class name (defaults to {@code "ROOT"} if null)
     */
    public LoggerImpl(String name) {
        this.name = name != null ? name : "ROOT";
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Overrides the minimum logging severity threshold for this specific logger instance.
     *
     * @param level custom severity threshold, or {@code null} to inherit root configuration
     */
    public void setLevel(LogLevel level) {
        this.levelOverride = level;
    }

    /**
     * Computes the effective severity threshold, taking into account local overrides and root config.
     *
     * @return active {@link LogLevel}
     */
    public LogLevel getEffectiveLevel() {
        if (levelOverride != null) {
            return levelOverride;
        }
        return LogManager.getInstance().getConfig().getRootLevel();
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean isEnabled(LogLevel level) {
        return level != null && level.isEnabledFor(getEffectiveLevel());
    }

    /** {@inheritDoc} */
    @Override
    public boolean isTraceEnabled() { return isEnabled(LogLevel.TRACE); }
    /** {@inheritDoc} */
    @Override
    public boolean isDebugEnabled() { return isEnabled(LogLevel.DEBUG); }
    /** {@inheritDoc} */
    @Override
    public boolean isInfoEnabled()  { return isEnabled(LogLevel.INFO); }
    /** {@inheritDoc} */
    @Override
    public boolean isWarnEnabled()  { return isEnabled(LogLevel.WARN); }
    /** {@inheritDoc} */
    @Override
    public boolean isErrorEnabled() { return isEnabled(LogLevel.ERROR); }

    /** {@inheritDoc} */
    @Override
    public void trace(String message) { logInternal(LogLevel.TRACE, message, null, null); }
    /** {@inheritDoc} */
    @Override
    public void trace(String format, Object... args) { logInternal(LogLevel.TRACE, format, args, null); }
    /** {@inheritDoc} */
    @Override
    public void trace(String message, Throwable t) { logInternal(LogLevel.TRACE, message, null, t); }

    /** {@inheritDoc} */
    @Override
    public void debug(String message) { logInternal(LogLevel.DEBUG, message, null, null); }
    /** {@inheritDoc} */
    @Override
    public void debug(String format, Object... args) { logInternal(LogLevel.DEBUG, format, args, null); }
    /** {@inheritDoc} */
    @Override
    public void debug(String message, Throwable t) { logInternal(LogLevel.DEBUG, message, null, t); }

    /** {@inheritDoc} */
    @Override
    public void info(String message) { logInternal(LogLevel.INFO, message, null, null); }
    /** {@inheritDoc} */
    @Override
    public void info(String format, Object... args) { logInternal(LogLevel.INFO, format, args, null); }
    /** {@inheritDoc} */
    @Override
    public void info(String message, Throwable t) { logInternal(LogLevel.INFO, message, null, t); }

    /** {@inheritDoc} */
    @Override
    public void warn(String message) { logInternal(LogLevel.WARN, message, null, null); }
    /** {@inheritDoc} */
    @Override
    public void warn(String format, Object... args) { logInternal(LogLevel.WARN, format, args, null); }
    /** {@inheritDoc} */
    @Override
    public void warn(String message, Throwable t) { logInternal(LogLevel.WARN, message, null, t); }

    /** {@inheritDoc} */
    @Override
    public void error(String message) { logInternal(LogLevel.ERROR, message, null, null); }
    /** {@inheritDoc} */
    @Override
    public void error(String format, Object... args) { logInternal(LogLevel.ERROR, format, args, null); }
    /** {@inheritDoc} */
    @Override
    public void error(String message, Throwable t) { logInternal(LogLevel.ERROR, message, null, t); }

    /** {@inheritDoc} */
    @Override
    public void fatal(String message) { logInternal(LogLevel.FATAL, message, null, null); }
    /** {@inheritDoc} */
    @Override
    public void fatal(String format, Object... args) { logInternal(LogLevel.FATAL, format, args, null); }
    /** {@inheritDoc} */
    @Override
    public void fatal(String message, Throwable t) { logInternal(LogLevel.FATAL, message, null, t); }

    /** {@inheritDoc} */
    @Override
    public void audit(AuditEvent auditEvent) {
        if (auditEvent != null) {
            log(auditEvent.toLogEvent(this.name));
        }
    }

    /**
     * Enriches and dispatches the specified log event through the {@link LogManager}.
     * Merges current thread {@link LogContext} attributes into the event payload.
     *
     * @param event log event to dispatch
     */
    @Override
    public void log(LogEvent event) {
        if (event == null || !isEnabled(event.getLevel())) {
            return;
        }

        // Merge thread context if event doesn't already contain it
        Map<String, String> currentContext = LogContext.getCopyOfContextMap();
        Map<String, String> merged = new HashMap<>(currentContext);
        merged.putAll(event.getContext());

        LogEvent enrichedEvent = LogEvent.builder()
                .timestamp(event.getTimestamp())
                .threadName(event.getThreadName())
                .loggerName(event.getLoggerName() != null ? event.getLoggerName() : this.name)
                .level(event.getLevel())
                .message(event.getMessage())
                .context(merged)
                .tags(event.getTags())
                .flowId(event.getFlowId())
                .operation(event.getOperation())
                .durationMs(event.getDurationMs())
                .throwable(event.getThrowable())
                .callerClass(event.getCallerClass())
                .callerMethod(event.getCallerMethod())
                .callerLineNumber(event.getCallerLineNumber())
                .build();

        LogManager.getInstance().dispatch(enrichedEvent);
    }

    /**
     * Internal helper for parameter interpolation, caller stack frame inspection, and event construction.
     *
     * @param level            severity level
     * @param messageOrPattern message string or "{}" placeholder pattern
     * @param args             optional parameter arguments
     * @param t                optional throwable cause
     */
    private void logInternal(LogLevel level, String messageOrPattern, Object[] args, Throwable t) {
        if (!isEnabled(level)) {
            return;
        }

        String finalMessage;
        Throwable finalThrowable = t;

        if (args != null && args.length > 0) {
            StringBuilder sb = new StringBuilder();
            int argIdx = 0;
            int lastPos = 0;
            int pos;
            while ((pos = messageOrPattern.indexOf("{}", lastPos)) != -1) {
                sb.append(messageOrPattern, lastPos, pos);
                if (argIdx < args.length) {
                    sb.append(args[argIdx++]);
                } else {
                    sb.append("{}");
                }
                lastPos = pos + 2;
            }
            sb.append(messageOrPattern.substring(lastPos));

            if (argIdx < args.length && args[argIdx] instanceof Throwable && finalThrowable == null) {
                finalThrowable = (Throwable) args[argIdx];
            }
            finalMessage = sb.toString();
        } else {
            finalMessage = messageOrPattern;
        }

        LogEvent.Builder builder = LogEvent.builder()
                .level(level)
                .loggerName(this.name)
                .message(finalMessage)
                .throwable(finalThrowable)
                .context(LogContext.getCopyOfContextMap());

        // Capture caller details for debug/trace/error
        if (level == LogLevel.DEBUG || level == LogLevel.TRACE || level == LogLevel.ERROR) {
            StackTraceElement[] st = Thread.currentThread().getStackTrace();
            // Find caller outside of LoggerImpl
            for (int i = 2; i < st.length; i++) {
                String cls = st[i].getClassName();
                if (!cls.equals(LoggerImpl.class.getName()) && !cls.startsWith("com.campx.logger.api")) {
                    builder.callerClass(cls);
                    builder.callerMethod(st[i].getMethodName());
                    builder.callerLineNumber(st[i].getLineNumber());
                    break;
                }
            }
        }

        LogManager.getInstance().dispatch(builder.build());
    }

    /** {@inheritDoc} */
    @Override
    public FluentLogBuilder at(LogLevel level) {
        return new FluentLogBuilder(this, level);
    }

    /** {@inheritDoc} */
    @Override
    public FluentLogBuilder atTrace() { return at(LogLevel.TRACE); }
    /** {@inheritDoc} */
    @Override
    public FluentLogBuilder atDebug() { return at(LogLevel.DEBUG); }
    /** {@inheritDoc} */
    @Override
    public FluentLogBuilder atInfo()  { return at(LogLevel.INFO); }
    /** {@inheritDoc} */
    @Override
    public FluentLogBuilder atWarn()  { return at(LogLevel.WARN); }
    /** {@inheritDoc} */
    @Override
    public FluentLogBuilder atError() { return at(LogLevel.ERROR); }
    /** {@inheritDoc} */
    @Override
    public FluentLogBuilder atFatal() { return at(LogLevel.FATAL); }

    /** {@inheritDoc} */
    @Override
    public FlowTracker flow(String operationName) {
        return new FlowTracker(this, operationName, null);
    }

    /** {@inheritDoc} */
    @Override
    public FlowTracker flow(String operationName, String flowId) {
        return new FlowTracker(this, operationName, flowId);
    }
}
