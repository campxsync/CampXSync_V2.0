package com.campx.logger.api;

/**
 * Core logging contract for the CampXSync distributed microservices platform.
 * <p>
 * Exposes traditional multi-level logging, formatted parameter substitution,
 * dedicated compliance audit logging, fluent contextual builders, and distributed flow tracking.
 *
 * @see LogEvent
 * @see AuditEvent
 * @see FluentLogBuilder
 * @see FlowTracker
 */
public interface ILogger {

    /**
     * Returns the name assigned to this logger, typically the fully qualified class name.
     *
     * @return logger name
     */
    String getName();

    /**
     * Checks if logging is enabled for the specified log severity level.
     *
     * @param level level to evaluate
     * @return {@code true} if logging at or above this level is permitted
     */
    boolean isEnabled(LogLevel level);

    /**
     * Checks if {@link LogLevel#TRACE} logging is enabled.
     *
     * @return {@code true} if TRACE is enabled
     */
    boolean isTraceEnabled();

    /**
     * Checks if {@link LogLevel#DEBUG} logging is enabled.
     *
     * @return {@code true} if DEBUG is enabled
     */
    boolean isDebugEnabled();

    /**
     * Checks if {@link LogLevel#INFO} logging is enabled.
     *
     * @return {@code true} if INFO is enabled
     */
    boolean isInfoEnabled();

    /**
     * Checks if {@link LogLevel#WARN} logging is enabled.
     *
     * @return {@code true} if WARN is enabled
     */
    boolean isWarnEnabled();

    /**
     * Checks if {@link LogLevel#ERROR} logging is enabled.
     *
     * @return {@code true} if ERROR is enabled
     */
    boolean isErrorEnabled();

    // Trace
    /**
     * Logs a message at {@link LogLevel#TRACE} level.
     *
     * @param message the message string
     */
    void trace(String message);

    /**
     * Logs a formatted message with argument substitution at {@link LogLevel#TRACE} level.
     *
     * @param format pattern string with "{}" tokens
     * @param args   arguments to interpolate
     */
    void trace(String format, Object... args);

    /**
     * Logs a message and associated exception at {@link LogLevel#TRACE} level.
     *
     * @param message the message string
     * @param t       the exception cause
     */
    void trace(String message, Throwable t);

    // Debug
    /**
     * Logs a message at {@link LogLevel#DEBUG} level.
     *
     * @param message the message string
     */
    void debug(String message);

    /**
     * Logs a formatted message with argument substitution at {@link LogLevel#DEBUG} level.
     *
     * @param format pattern string with "{}" tokens
     * @param args   arguments to interpolate
     */
    void debug(String format, Object... args);

    /**
     * Logs a message and associated exception at {@link LogLevel#DEBUG} level.
     *
     * @param message the message string
     * @param t       the exception cause
     */
    void debug(String message, Throwable t);

    // Info
    /**
     * Logs a message at {@link LogLevel#INFO} level.
     *
     * @param message the message string
     */
    void info(String message);

    /**
     * Logs a formatted message with argument substitution at {@link LogLevel#INFO} level.
     *
     * @param format pattern string with "{}" tokens
     * @param args   arguments to interpolate
     */
    void info(String format, Object... args);

    /**
     * Logs a message and associated exception at {@link LogLevel#INFO} level.
     *
     * @param message the message string
     * @param t       the exception cause
     */
    void info(String message, Throwable t);

    // Warn
    /**
     * Logs a message at {@link LogLevel#WARN} level.
     *
     * @param message the message string
     */
    void warn(String message);

    /**
     * Logs a formatted message with argument substitution at {@link LogLevel#WARN} level.
     *
     * @param format pattern string with "{}" tokens
     * @param args   arguments to interpolate
     */
    void warn(String format, Object... args);

    /**
     * Logs a message and associated exception at {@link LogLevel#WARN} level.
     *
     * @param message the message string
     * @param t       the exception cause
     */
    void warn(String message, Throwable t);

    // Error
    /**
     * Logs a message at {@link LogLevel#ERROR} level.
     *
     * @param message the message string
     */
    void error(String message);

    /**
     * Logs a formatted message with argument substitution at {@link LogLevel#ERROR} level.
     *
     * @param format pattern string with "{}" tokens
     * @param args   arguments to interpolate
     */
    void error(String format, Object... args);

    /**
     * Logs a message and associated exception at {@link LogLevel#ERROR} level.
     *
     * @param message the message string
     * @param t       the exception cause
     */
    void error(String message, Throwable t);

    // Fatal
    /**
     * Logs a message at {@link LogLevel#FATAL} level.
     *
     * @param message the message string
     */
    void fatal(String message);

    /**
     * Logs a formatted message with argument substitution at {@link LogLevel#FATAL} level.
     *
     * @param format pattern string with "{}" tokens
     * @param args   arguments to interpolate
     */
    void fatal(String format, Object... args);

    /**
     * Logs a message and associated exception at {@link LogLevel#FATAL} level.
     *
     * @param message the message string
     * @param t       the exception cause
     */
    void fatal(String message, Throwable t);

    // Audit
    /**
     * Dispatches an immutable structured compliance audit event.
     *
     * @param auditEvent the audit record to persist and log
     */
    void audit(AuditEvent auditEvent);

    // Direct event logging
    /**
     * Emits a fully populated {@link LogEvent} directly through the logger pipeline.
     *
     * @param event the log event record
     */
    void log(LogEvent event);

    // Fluent API entry points
    /**
     * Creates a fluent log builder for the specified severity level.
     *
     * @param level the log level
     * @return a new {@link FluentLogBuilder} instance
     */
    FluentLogBuilder at(LogLevel level);

    /**
     * Creates a fluent log builder targeting {@link LogLevel#TRACE}.
     *
     * @return a fluent builder
     */
    FluentLogBuilder atTrace();

    /**
     * Creates a fluent log builder targeting {@link LogLevel#DEBUG}.
     *
     * @return a fluent builder
     */
    FluentLogBuilder atDebug();

    /**
     * Creates a fluent log builder targeting {@link LogLevel#INFO}.
     *
     * @return a fluent builder
     */
    FluentLogBuilder atInfo();

    /**
     * Creates a fluent log builder targeting {@link LogLevel#WARN}.
     *
     * @return a fluent builder
     */
    FluentLogBuilder atWarn();

    /**
     * Creates a fluent log builder targeting {@link LogLevel#ERROR}.
     *
     * @return a fluent builder
     */
    FluentLogBuilder atError();

    /**
     * Creates a fluent log builder targeting {@link LogLevel#FATAL}.
     *
     * @return a fluent builder
     */
    FluentLogBuilder atFatal();

    // Flow tracking
    /**
     * Starts an execution flow tracking block with an auto-generated flow identifier.
     *
     * @param operationName logical name of the operation being executed
     * @return an {@link AutoCloseable} {@link FlowTracker}
     */
    FlowTracker flow(String operationName);

    /**
     * Starts an execution flow tracking block with a specific flow identifier for cross-service correlation.
     *
     * @param operationName logical name of the operation being executed
     * @param flowId        distributed correlation or flow identifier
     * @return an {@link AutoCloseable} {@link FlowTracker}
     */
    FlowTracker flow(String operationName, String flowId);
}
