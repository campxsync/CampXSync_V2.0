package com.campx.logger.appender;

import com.campx.logger.formatter.JsonFormatter;

/**
 * Appender specialized for emitting JSON-lines formatted log files for flow tracing,
 * observability analysis, and centralized log ingestion pipelines (e.g. ELK, Splunk).
 * <p>
 * Inherits rolling file management from {@link RollingFileAppender} while pre-configuring
 * {@link JsonFormatter} for structured JSON output.
 *
 * @see RollingFileAppender
 * @see JsonFormatter
 */
public class JsonFileAppender extends RollingFileAppender {

    /**
     * Initializes a JSON file appender with default name {@code "JSON_FILE"},
     * 10MB file roll size, and 10 backup archives.
     *
     * @param filePath destination path of the active JSON log file
     */
    public JsonFileAppender(String filePath) {
        super("JSON_FILE", filePath, 10 * 1024 * 1024L, 10, new JsonFormatter(true));
    }

    /**
     * Initializes a JSON file appender with customizable rotation parameters.
     *
     * @param name           the logical appender identifier
     * @param filePath       destination path of the active JSON log file
     * @param maxFileSize    maximum file size threshold in bytes before triggering rotation
     * @param maxBackupIndex maximum number of historical backup files to retain
     */
    public JsonFileAppender(String name, String filePath, long maxFileSize, int maxBackupIndex) {
        super(name, filePath, maxFileSize, maxBackupIndex, new JsonFormatter(true));
    }
}
