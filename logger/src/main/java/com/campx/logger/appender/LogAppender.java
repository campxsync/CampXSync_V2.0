package com.campx.logger.appender;

import com.campx.logger.api.LogEvent;

/**
 * Interface representing a destination for formatted log events.
 */
public interface LogAppender {
    String getName();
    void append(LogEvent event);
    void flush();
    void close();
}
