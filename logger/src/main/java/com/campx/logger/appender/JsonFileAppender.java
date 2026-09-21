package com.campx.logger.appender;

import com.campx.logger.formatter.JsonFormatter;

/**
 * Appender specialized for emitting JSON-lines log files for flow tracing
 * and centralized log ingestion systems.
 */
public class JsonFileAppender extends RollingFileAppender {

    public JsonFileAppender(String filePath) {
        super("JSON_FILE", filePath, 10 * 1024 * 1024L, 10, new JsonFormatter(true));
    }

    public JsonFileAppender(String name, String filePath, long maxFileSize, int maxBackupIndex) {
        super(name, filePath, maxFileSize, maxBackupIndex, new JsonFormatter(true));
    }
}
