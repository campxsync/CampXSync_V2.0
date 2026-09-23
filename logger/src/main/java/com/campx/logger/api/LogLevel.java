package com.campx.logger.api;

/**
 * Standard log levels supported by the CampXSync Logger framework.
 * <p>
 * Log levels are ordered by ascending numeric severity. When a minimum logging
 * threshold is configured, any log event with severity greater than or equal to
 * the threshold will be accepted for processing.
 * </p>
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public enum LogLevel {

    /** Detailed trace logging for low-level protocol diagnostics (severity = 10). */
    TRACE(10),

    /** Diagnostic information for development and debugging (severity = 20). */
    DEBUG(20),

    /** Normal operational status messages and lifecycle events (severity = 30). */
    INFO(30),

    /** Recoverable anomalies, degraded states, or potential problems (severity = 40). */
    WARN(40),

    /** Handled exceptions, transaction aborts, or operational errors (severity = 50). */
    ERROR(50),

    /** Unrecoverable system or component failure leading to shutdown (severity = 60). */
    FATAL(60),

    /** High-priority regulatory and compliance audit events (severity = 70). */
    AUDIT(70);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    /**
     * Gets the integer severity rating of this log level.
     *
     * @return Integer severity value.
     */
    public int getSeverity() {
        return severity;
    }

    /**
     * Determines whether an event of this level should be logged given the configured threshold.
     *
     * @param threshold The configured minimum log level, or {@code null} to allow all.
     * @return {@code true} if this level meets or exceeds the threshold severity.
     */
    public boolean isEnabledFor(LogLevel threshold) {
        if (threshold == null) {
            return true;
        }
        return this.severity >= threshold.severity;
    }

    /**
     * Parses a string name into a LogLevel, falling back to a default level if invalid.
     *
     * @param levelStr     Case-insensitive level name (e.g., "INFO", "WARN").
     * @param defaultLevel Fallback level to return if the input is null or unparseable.
     * @return The resolved LogLevel enum value.
     */
    public static LogLevel fromString(String levelStr, LogLevel defaultLevel) {
        if (levelStr == null || levelStr.trim().isEmpty()) {
            return defaultLevel;
        }
        try {
            return LogLevel.valueOf(levelStr.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            return defaultLevel;
        }
    }
}
