package com.campx.logger;

import com.campx.logger.api.LogLevel;
import com.campx.logger.core.LogManager;

/**
 * Primary static factory and access facade for obtaining {@link CampXLogger} instances.
 * <p>
 * Recommended usage pattern:
 * <pre>{@code
 * public class StudentService {
 *     private static final CampXLogger logger = CampXLoggerFactory.getLogger(StudentService.class);
 *
 *     public void enrollStudent(String studentId) {
 *         logger.atInfo()
 *               .withOperation("ENROLL_STUDENT")
 *               .log("Enrolling student {}", studentId);
 *     }
 * }
 * }</pre>
 *
 * @see CampXLogger
 * @see LogManager
 */
public final class CampXLoggerFactory {

    private CampXLoggerFactory() {}

    /**
     * Obtains a thread-safe logger named after the fully qualified class name of the given class.
     *
     * @param clazz the class whose name will be used as logger category
     * @return logger instance
     */
    public static CampXLogger getLogger(Class<?> clazz) {
        return LogManager.getInstance().getLogger(clazz != null ? clazz.getName() : "ROOT");
    }

    /**
     * Obtains a thread-safe logger with a custom logical category name.
     *
     * @param name custom logger or subsystem name
     * @return logger instance
     */
    public static CampXLogger getLogger(String name) {
        return LogManager.getInstance().getLogger(name);
    }

    /**
     * Sets the global root logging severity threshold dynamically at runtime.
     *
     * @param level new root severity level
     */
    public static void setRootLevel(LogLevel level) {
        LogManager.getInstance().setRootLevel(level);
    }

    /**
     * Dynamically sets the log level for a specific logger category or package name at runtime.
     *
     * @param loggerName category or package name
     * @param level      new severity level threshold
     */
    public static void setLoggerLevel(String loggerName, LogLevel level) {
        LogManager.getInstance().setLoggerLevel(loggerName, level);
    }

    /**
     * Synchronously flushes all pending asynchronous log events to active disk and console appenders.
     */
    public static void flush() {
        LogManager.getInstance().flush();
    }

    /**
     * Gracefully shuts down the logger subsystem, drains worker queues, and releases appender streams.
     */
    public static void shutdown() {
        LogManager.getInstance().shutdown();
    }
}
