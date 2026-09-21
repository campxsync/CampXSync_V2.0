package com.campx.logger.formatter;

import com.campx.logger.api.LogEvent;

/**
 * Strategy interface for formatting LogEvent instances into string representations.
 */
public interface LogFormatter {
    String format(LogEvent event);
}
