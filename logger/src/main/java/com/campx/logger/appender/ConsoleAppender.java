package com.campx.logger.appender;

import com.campx.logger.api.LogEvent;
import com.campx.logger.api.LogLevel;
import com.campx.logger.formatter.LogFormatter;
import com.campx.logger.formatter.PatternFormatter;

/**
 * {@link LogAppender} implementation that writes formatted log events to console streams.
 * <p>
 * Routes {@link LogLevel#ERROR} and {@link LogLevel#FATAL} events to {@link System#err},
 * while all other severities are directed to {@link System#out}. Supports ANSI color formatting
 * when configured with a supporting {@link LogFormatter}.
 *
 * @see LogAppender
 * @see PatternFormatter
 */
public class ConsoleAppender implements LogAppender {

    private final String name;
    private final LogFormatter formatter;

    /**
     * Initializes a default console appender with name {@code "CONSOLE"} and ANSI color enabled.
     */
    public ConsoleAppender() {
        this("CONSOLE", new PatternFormatter(true, true));
    }

    /**
     * Initializes a console appender with a custom name and formatter.
     *
     * @param name      the logical appender identifier
     * @param formatter the formatter responsible for serializing {@link LogEvent} records
     */
    public ConsoleAppender(String name, LogFormatter formatter) {
        this.name = name;
        this.formatter = formatter != null ? formatter : new PatternFormatter(true, true);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public String getName() {
        return name;
    }

    /**
     * Formats the event and outputs it to standard error for ERROR/FATAL levels, or standard out otherwise.
     *
     * @param event the log event to append
     */
    @Override
    public void append(LogEvent event) {
        String formatted = formatter.format(event);
        if (event.getLevel() == LogLevel.ERROR || event.getLevel() == LogLevel.FATAL) {
            System.err.print(formatted);
        } else {
            System.out.print(formatted);
        }
    }

    /**
     * Flushes both standard output and standard error streams.
     */
    @Override
    public void flush() {
        System.out.flush();
        System.err.flush();
    }

    /**
     * Closes the appender by ensuring all buffered console output is flushed.
     */
    @Override
    public void close() {
        flush();
    }
}
