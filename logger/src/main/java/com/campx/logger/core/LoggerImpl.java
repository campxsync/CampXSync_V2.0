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
 * Core implementation of the ILogger interface.
 */
public class LoggerImpl implements CampXLogger {

    private final String name;
    private volatile LogLevel levelOverride = null;

    public LoggerImpl(String name) {
        this.name = name != null ? name : "ROOT";
    }

    @Override
    public String getName() {
        return name;
    }

    public void setLevel(LogLevel level) {
        this.levelOverride = level;
    }

    public LogLevel getEffectiveLevel() {
        if (levelOverride != null) {
            return levelOverride;
        }
        return LogManager.getInstance().getConfig().getRootLevel();
    }

    @Override
    public boolean isEnabled(LogLevel level) {
        return level != null && level.isEnabledFor(getEffectiveLevel());
    }

    @Override
    public boolean isTraceEnabled() { return isEnabled(LogLevel.TRACE); }
    @Override
    public boolean isDebugEnabled() { return isEnabled(LogLevel.DEBUG); }
    @Override
    public boolean isInfoEnabled()  { return isEnabled(LogLevel.INFO); }
    @Override
    public boolean isWarnEnabled()  { return isEnabled(LogLevel.WARN); }
    @Override
    public boolean isErrorEnabled() { return isEnabled(LogLevel.ERROR); }

    @Override
    public void trace(String message) { logInternal(LogLevel.TRACE, message, null, null); }
    @Override
    public void trace(String format, Object... args) { logInternal(LogLevel.TRACE, format, args, null); }
    @Override
    public void trace(String message, Throwable t) { logInternal(LogLevel.TRACE, message, null, t); }

    @Override
    public void debug(String message) { logInternal(LogLevel.DEBUG, message, null, null); }
    @Override
    public void debug(String format, Object... args) { logInternal(LogLevel.DEBUG, format, args, null); }
    @Override
    public void debug(String message, Throwable t) { logInternal(LogLevel.DEBUG, message, null, t); }

    @Override
    public void info(String message) { logInternal(LogLevel.INFO, message, null, null); }
    @Override
    public void info(String format, Object... args) { logInternal(LogLevel.INFO, format, args, null); }
    @Override
    public void info(String message, Throwable t) { logInternal(LogLevel.INFO, message, null, t); }

    @Override
    public void warn(String message) { logInternal(LogLevel.WARN, message, null, null); }
    @Override
    public void warn(String format, Object... args) { logInternal(LogLevel.WARN, format, args, null); }
    @Override
    public void warn(String message, Throwable t) { logInternal(LogLevel.WARN, message, null, t); }

    @Override
    public void error(String message) { logInternal(LogLevel.ERROR, message, null, null); }
    @Override
    public void error(String format, Object... args) { logInternal(LogLevel.ERROR, format, args, null); }
    @Override
    public void error(String message, Throwable t) { logInternal(LogLevel.ERROR, message, null, t); }

    @Override
    public void fatal(String message) { logInternal(LogLevel.FATAL, message, null, null); }
    @Override
    public void fatal(String format, Object... args) { logInternal(LogLevel.FATAL, format, args, null); }
    @Override
    public void fatal(String message, Throwable t) { logInternal(LogLevel.FATAL, message, null, t); }

    @Override
    public void audit(AuditEvent auditEvent) {
        if (auditEvent != null) {
            log(auditEvent.toLogEvent(this.name));
        }
    }

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

    @Override
    public FluentLogBuilder at(LogLevel level) {
        return new FluentLogBuilder(this, level);
    }

    @Override
    public FluentLogBuilder atTrace() { return at(LogLevel.TRACE); }
    @Override
    public FluentLogBuilder atDebug() { return at(LogLevel.DEBUG); }
    @Override
    public FluentLogBuilder atInfo()  { return at(LogLevel.INFO); }
    @Override
    public FluentLogBuilder atWarn()  { return at(LogLevel.WARN); }
    @Override
    public FluentLogBuilder atError() { return at(LogLevel.ERROR); }
    @Override
    public FluentLogBuilder atFatal() { return at(LogLevel.FATAL); }

    @Override
    public FlowTracker flow(String operationName) {
        return new FlowTracker(this, operationName, null);
    }

    @Override
    public FlowTracker flow(String operationName, String flowId) {
        return new FlowTracker(this, operationName, flowId);
    }
}
