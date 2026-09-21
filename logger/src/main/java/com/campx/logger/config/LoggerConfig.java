package com.campx.logger.config;

import com.campx.logger.api.LogLevel;

/**
 * Strongly-typed configuration options for the CampXSync Logger.
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

    public LogLevel getRootLevel() { return rootLevel; }
    public void setRootLevel(LogLevel rootLevel) { this.rootLevel = rootLevel; }

    public boolean isConsoleEnabled() { return consoleEnabled; }
    public void setConsoleEnabled(boolean consoleEnabled) { this.consoleEnabled = consoleEnabled; }

    public boolean isColorEnabled() { return colorEnabled; }
    public void setColorEnabled(boolean colorEnabled) { this.colorEnabled = colorEnabled; }

    public boolean isFileEnabled() { return fileEnabled; }
    public void setFileEnabled(boolean fileEnabled) { this.fileEnabled = fileEnabled; }

    public String getFilePath() { return filePath; }
    public void setFilePath(String filePath) { this.filePath = filePath; }

    public boolean isJsonFileEnabled() { return jsonFileEnabled; }
    public void setJsonFileEnabled(boolean jsonFileEnabled) { this.jsonFileEnabled = jsonFileEnabled; }

    public String getJsonFilePath() { return jsonFilePath; }
    public void setJsonFilePath(String jsonFilePath) { this.jsonFilePath = jsonFilePath; }

    public long getMaxFileSize() { return maxFileSize; }
    public void setMaxFileSize(long maxFileSize) { this.maxFileSize = maxFileSize; }

    public int getMaxBackupIndex() { return maxBackupIndex; }
    public void setMaxBackupIndex(int maxBackupIndex) { this.maxBackupIndex = maxBackupIndex; }

    public boolean isAsyncEnabled() { return asyncEnabled; }
    public void setAsyncEnabled(boolean asyncEnabled) { this.asyncEnabled = asyncEnabled; }

    public int getQueueCapacity() { return queueCapacity; }
    public void setQueueCapacity(int queueCapacity) { this.queueCapacity = queueCapacity; }

    public boolean isMaskSecurityData() { return maskSecurityData; }
    public void setMaskSecurityData(boolean maskSecurityData) { this.maskSecurityData = maskSecurityData; }

    public boolean isApiServerEnabled() { return apiServerEnabled; }
    public void setApiServerEnabled(boolean apiServerEnabled) { this.apiServerEnabled = apiServerEnabled; }

    public int getApiServerPort() { return apiServerPort; }
    public void setApiServerPort(int apiServerPort) { this.apiServerPort = apiServerPort; }
}
