package com.campx.logger.api;

/**
 * Standard log levels supported by CampXSync Logger.
 * Ordered by increasing severity.
 */
public enum LogLevel {
    TRACE(10),
    DEBUG(20),
    INFO(30),
    WARN(40),
    ERROR(50),
    FATAL(60),
    AUDIT(70);

    private final int severity;

    LogLevel(int severity) {
        this.severity = severity;
    }

    public int getSeverity() {
        return severity;
    }

    public boolean isEnabledFor(LogLevel threshold) {
        if (threshold == null) {
            return true;
        }
        return this.severity >= threshold.severity;
    }

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
