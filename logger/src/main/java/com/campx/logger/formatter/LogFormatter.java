package com.campx.logger.formatter;

import com.campx.logger.api.LogEvent;

/**
 * Strategy interface for serializing {@link LogEvent} instances into string representations.
 * <p>
 * Implementations format structured log records into plain text patterns, ANSI-colored terminal streams,
 * or structured newline-delimited JSON payloads.
 *
 * @see PatternFormatter
 * @see JsonFormatter
 */
public interface LogFormatter {

    /**
     * Serializes a structured log event into its string representation.
     *
     * @param event the log event record to format
     * @return the serialized log string, typically terminating with a newline
     */
    String format(LogEvent event);
}
