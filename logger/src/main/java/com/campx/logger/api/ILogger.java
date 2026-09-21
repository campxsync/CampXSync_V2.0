package com.campx.logger.api;

/**
 * Core interface for the CampXSync Logger.
 * Exposes traditional logging, fluent contextual logging, and flow tracking.
 */
public interface ILogger {

    String getName();

    boolean isEnabled(LogLevel level);

    boolean isTraceEnabled();
    boolean isDebugEnabled();
    boolean isInfoEnabled();
    boolean isWarnEnabled();
    boolean isErrorEnabled();

    // Trace
    void trace(String message);
    void trace(String format, Object... args);
    void trace(String message, Throwable t);

    // Debug
    void debug(String message);
    void debug(String format, Object... args);
    void debug(String message, Throwable t);

    // Info
    void info(String message);
    void info(String format, Object... args);
    void info(String message, Throwable t);

    // Warn
    void warn(String message);
    void warn(String format, Object... args);
    void warn(String message, Throwable t);

    // Error
    void error(String message);
    void error(String format, Object... args);
    void error(String message, Throwable t);

    // Fatal
    void fatal(String message);
    void fatal(String format, Object... args);
    void fatal(String message, Throwable t);

    // Audit
    void audit(AuditEvent auditEvent);

    // Direct event logging
    void log(LogEvent event);

    // Fluent API entry points
    FluentLogBuilder at(LogLevel level);
    FluentLogBuilder atTrace();
    FluentLogBuilder atDebug();
    FluentLogBuilder atInfo();
    FluentLogBuilder atWarn();
    FluentLogBuilder atError();
    FluentLogBuilder atFatal();

    // Flow tracking
    FlowTracker flow(String operationName);
    FlowTracker flow(String operationName, String flowId);
}
