package com.campx.logger.appender;

import com.campx.logger.api.LogEvent;

/**
 * Contract representing an output destination for formatted log events in the logging subsystem.
 * <p>
 * Implementations may write to console standard out/err, synchronous/asynchronous disk files,
 * JSON structured stores, or external log aggregation endpoints.
 *
 * @see ConsoleAppender
 * @see JsonFileAppender
 * @see RollingFileAppender
 */
public interface LogAppender {

    /**
     * Returns the unique identifier assigned to this appender instance.
     *
     * @return appender name
     */
    String getName();

    /**
     * Appends a single log event to the underlying output target.
     *
     * @param event the structured log event to record
     */
    void append(LogEvent event);

    /**
     * Flushes any buffered log entries to the physical output stream or storage.
     */
    void flush();

    /**
     * Releases any underlying system resources, file handles, or active streams.
     */
    void close();
}
