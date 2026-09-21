package com.campx.logger.appender;

import com.campx.logger.api.LogEvent;
import com.campx.logger.api.LogLevel;
import com.campx.logger.formatter.LogFormatter;
import com.campx.logger.formatter.PatternFormatter;

/**
 * Appender that writes formatted log events to standard output/error.
 */
public class ConsoleAppender implements LogAppender {

    private final String name;
    private final LogFormatter formatter;

    public ConsoleAppender() {
        this("CONSOLE", new PatternFormatter(true, true));
    }

    public ConsoleAppender(String name, LogFormatter formatter) {
        this.name = name;
        this.formatter = formatter != null ? formatter : new PatternFormatter(true, true);
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public void append(LogEvent event) {
        String formatted = formatter.format(event);
        if (event.getLevel() == LogLevel.ERROR || event.getLevel() == LogLevel.FATAL) {
            System.err.print(formatted);
        } else {
            System.out.print(formatted);
        }
    }

    @Override
    public void flush() {
        System.out.flush();
        System.err.flush();
    }

    @Override
    public void close() {
        flush();
    }
}
