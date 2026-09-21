package com.campx.logger;

import com.campx.logger.api.LogLevel;
import com.campx.logger.core.LogManager;

/**
 * Primary factory and access point for obtaining CampXLogger instances across
 * CampXSync services (Student, Faculty, Exam, Attendance, Analytics, etc.).
 */
public final class CampXLoggerFactory {

    private CampXLoggerFactory() {}

    /**
     * Obtains a logger named after the given class.
     */
    public static CampXLogger getLogger(Class<?> clazz) {
        return LogManager.getInstance().getLogger(clazz != null ? clazz.getName() : "ROOT");
    }

    /**
     * Obtains a logger with a custom name.
     */
    public static CampXLogger getLogger(String name) {
        return LogManager.getInstance().getLogger(name);
    }

    /**
     * Sets the global root log level at runtime.
     */
    public static void setRootLevel(LogLevel level) {
        LogManager.getInstance().setRootLevel(level);
    }

    /**
     * Sets the log level for a specific logger or package at runtime.
     */
    public static void setLoggerLevel(String loggerName, LogLevel level) {
        LogManager.getInstance().setLoggerLevel(loggerName, level);
    }

    /**
     * Flushes all pending async log events to disk/console.
     */
    public static void flush() {
        LogManager.getInstance().flush();
    }

    /**
     * Shuts down the logger subsystem and flushes pending writes.
     */
    public static void shutdown() {
        LogManager.getInstance().shutdown();
    }
}
