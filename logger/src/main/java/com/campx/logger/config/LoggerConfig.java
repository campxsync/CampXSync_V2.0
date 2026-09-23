package com.campx.logger.config;

import com.campx.logger.api.LogLevel;

/**
 * Strongly-typed configuration POJO for the CampXSync Logger.
 * <p>
 * Encapsulates global root severity, console appender ANSI colors, rolling log file paths,
 * JSON-lines output paths, file rotation limits, asynchronous ring buffer capacity,
 * PII masking controls, and embedded HTTP management server ports.
 *
 * @see ConfigManager
 * @see LogManager
 */
public class LoggerConfig {
    private LogLevel rootLevel = LogLevel.INFO;
    private boolean consoleEnabled = true;
    private boolean colorEnabled = true;

    private boolean fileEnabled = true;
    private String filePath = "logs/campx-app.log";

    private boolean jsonFileEnabled = true;
    private String jsonFilePath = "logs/campx-flow.jsonl";

    private long maxFileSize = 10 * 1024 * 1024L; // 10 MB
    private int maxBackupIndex = 10;

    private boolean asyncEnabled = true;
    private int queueCapacity = 10000;

    private boolean maskSecurityData = true;

    private boolean apiServerEnabled = true;
    private int apiServerPort = 9898;

    /**
     * Returns the global root logging severity threshold.
     *
     * @return root log level
     */
    public LogLevel getRootLevel() { return rootLevel; }

    /**
     * Sets the global root logging severity threshold.
     *
     * @param rootLevel new root level
     */
    public void setRootLevel(LogLevel rootLevel) { this.rootLevel = rootLevel; }

    /**
     * Checks if console appender output is enabled.
     *
     * @return {@code true} if console appender is active
     */
    public boolean isConsoleEnabled() { return consoleEnabled; }

    /**
     * Enables or disables console appender output.
     *
     * @param consoleEnabled console active state
     */
    public void setConsoleEnabled(boolean consoleEnabled) { this.consoleEnabled = consoleEnabled; }

    /**
     * Checks if ANSI terminal color sequences are enabled for console output.
     *
     * @return {@code true} if color is enabled
     */
    public boolean isColorEnabled() { return colorEnabled; }

    /**
     * Enables or disables ANSI color escape codes in console output.
     *
     * @param colorEnabled color state
     */
    public void setColorEnabled(boolean colorEnabled) { this.colorEnabled = colorEnabled; }

    /**
     * Checks if standard text file logging is enabled.
     *
     * @return {@code true} if file appender is active
     */
    public boolean isFileEnabled() { return fileEnabled; }

    /**
     * Enables or disables standard text file logging.
     *
     * @param fileEnabled file active state
     */
    public void setFileEnabled(boolean fileEnabled) { this.fileEnabled = fileEnabled; }

    /**
     * Returns the file system path for the active plain-text log file.
     *
     * @return plain text log path
     */
    public String getFilePath() { return filePath; }

    /**
     * Sets the destination path for the active plain-text log file.
     *
     * @param filePath log file destination
     */
    public void setFilePath(String filePath) { this.filePath = filePath; }

    /**
     * Checks if structured JSON lines (JSONL) file logging is enabled.
     *
     * @return {@code true} if JSON logging is active
     */
    public boolean isJsonFileEnabled() { return jsonFileEnabled; }

    /**
     * Enables or disables structured JSON lines (JSONL) file logging.
     *
     * @param jsonFileEnabled JSON active state
     */
    public void setJsonFileEnabled(boolean jsonFileEnabled) { this.jsonFileEnabled = jsonFileEnabled; }

    /**
     * Returns the file system path for the structured JSON lines log file.
     *
     * @return JSON log path
     */
    public String getJsonFilePath() { return jsonFilePath; }

    /**
     * Sets the destination path for the structured JSON lines log file.
     *
     * @param jsonFilePath JSON file path
     */
    public void setJsonFilePath(String jsonFilePath) { this.jsonFilePath = jsonFilePath; }

    /**
     * Returns the maximum file size in bytes before file rotation is triggered.
     *
     * @return maximum file size in bytes
     */
    public long getMaxFileSize() { return maxFileSize; }

    /**
     * Sets the maximum file size threshold in bytes before rotating.
     *
     * @param maxFileSize byte size threshold
     */
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

    /**
     * Returns the maximum number of historical backup files to retain.
     *
     * @return backup index retention limit
     */
    public int getMaxBackupIndex() { return maxBackupIndex; }

    /**
     * Sets the maximum number of historical rotated files to retain on disk.
     *
     * @param maxBackupIndex backup retention count
     */
    public void setMaxBackupIndex(int maxBackupIndex) { this.maxBackupIndex = maxBackupIndex; }

    /**
     * Checks if asynchronous background log dispatching is enabled.
     *
     * @return {@code true} if async engine is active
     */
    public boolean isAsyncEnabled() { return asyncEnabled; }

    /**
     * Enables or disables asynchronous background log dispatching.
     *
     * @param asyncEnabled async processing flag
     */
    public void setAsyncEnabled(boolean asyncEnabled) { this.asyncEnabled = asyncEnabled; }

    /**
     * Returns the capacity of the asynchronous blocking queue ring buffer.
     *
     * @return queue capacity
     */
    public int getQueueCapacity() { return queueCapacity; }

    /**
     * Sets the capacity of the asynchronous worker blocking queue.
     *
     * @param queueCapacity buffer capacity
     */
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    /**
     * Checks if sensitive data and PII masking is enabled.
     *
     * @return {@code true} if credentials are masked
     */
    public boolean isMaskSecurityData() { return maskSecurityData; }

    /**
     * Enables or disables sensitive credential and PII redaction.
     *
     * @param maskSecurityData masking active state
     */
    public void setMaskSecurityData(boolean maskSecurityData) { this.maskSecurityData = maskSecurityData; }

    /**
     * Checks if the embedded HTTP management API server is enabled.
     *
     * @return {@code true} if API server is active
     */
    public boolean isApiServerEnabled() { return apiServerEnabled; }

    /**
     * Enables or disables the embedded HTTP management API server.
     *
     * @param apiServerEnabled server active state
     */
    public void setApiServerEnabled(boolean apiServerEnabled) { this.apiServerEnabled = apiServerEnabled; }

    /**
     * Returns the TCP port on which the embedded HTTP management API server listens.
     *
     * @return HTTP port number
     */
    public int getApiServerPort() { return apiServerPort; }

    /**
     * Sets the TCP port for the embedded management HTTP API server.
     *
     * @param apiServerPort HTTP port number
     */
    public void setApiServerPort(int apiServerPort) { this.apiServerPort = apiServerPort; }
}
