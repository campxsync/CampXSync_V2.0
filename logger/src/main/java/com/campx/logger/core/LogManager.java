package com.campx.logger.core;

import com.campx.logger.api.LogEvent;
import com.campx.logger.api.LogLevel;
import com.campx.logger.appender.ConsoleAppender;
import com.campx.logger.appender.JsonFileAppender;
import com.campx.logger.appender.LogAppender;
import com.campx.logger.appender.RollingFileAppender;
import com.campx.logger.config.ConfigManager;
import com.campx.logger.config.LoggerConfig;
import com.campx.logger.formatter.JsonFormatter;
import com.campx.logger.formatter.PatternFormatter;
import com.campx.logger.server.LoggerApiServer;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;

/**
 * Central singleton managing logger lifecycle, configuration, appender dispatching,
 * and optional embedded HTTP management API.
 */
public class LogManager {

    private static volatile LogManager instance;
    private static final Object LOCK = new Object();

    private final LoggerConfig config;
    private final AsyncLogProcessor asyncProcessor;
    private final ConcurrentMap<String, LoggerImpl> loggers = new ConcurrentHashMap<>();
    private LoggerApiServer apiServer;

    private LogManager() {
        this.config = ConfigManager.load();
        this.asyncProcessor = new AsyncLogProcessor(config.getQueueCapacity());

        configureAppenders();
        startApiServerIfEnabled();
        registerShutdownHook();
    }

    public static LogManager getInstance() {
        if (instance == null) {
            synchronized (LOCK) {
                if (instance == null) {
                    instance = new LogManager();
                }
            }
        }
        return instance;
    }

    private void configureAppenders() {
        if (config.isConsoleEnabled()) {
            PatternFormatter consoleFormatter = new PatternFormatter(config.isColorEnabled(), config.isMaskSecurityData());
            asyncProcessor.addAppender(new ConsoleAppender("CONSOLE", consoleFormatter));
        }

        if (config.isFileEnabled() && config.getFilePath() != null) {
            PatternFormatter fileFormatter = new PatternFormatter(false, config.isMaskSecurityData());
            RollingFileAppender fileAppender = new RollingFileAppender(
                    "FILE",
                    config.getFilePath(),
                    config.getMaxFileSize(),
                    config.getMaxBackupIndex(),
                    fileFormatter
            );
            asyncProcessor.addAppender(fileAppender);
        }

        if (config.isJsonFileEnabled() && config.getJsonFilePath() != null) {
            RollingFileAppender jsonAppender = new RollingFileAppender(
                    "JSON_FILE",
                    config.getJsonFilePath(),
                    config.getMaxFileSize(),
                    config.getMaxBackupIndex(),
                    new JsonFormatter(config.isMaskSecurityData())
            );
            asyncProcessor.addAppender(jsonAppender);
        }
    }

    private void startApiServerIfEnabled() {
        if (config.isApiServerEnabled()) {
            try {
                this.apiServer = new LoggerApiServer(config.getApiServerPort());
                this.apiServer.start();
                System.out.println("[LogManager] CampXSync Logger API Server listening on port " + config.getApiServerPort());
            } catch (Exception e) {
                System.err.println("[LogManager] Failed to start Logger API Server: " + e.getMessage());
            }
        }
    }

    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }, "CampX-LogShutdownHook"));
    }

    public LoggerImpl getLogger(String name) {
        String loggerName = (name != null && !name.trim().isEmpty()) ? name : "ROOT";
        return loggers.computeIfAbsent(loggerName, LoggerImpl::new);
    }

    public void dispatch(LogEvent event) {
        if (config.isAsyncEnabled()) {
            asyncProcessor.enqueue(event);
        } else {
            for (LogAppender appender : asyncProcessor.getAppenders()) {
                appender.append(event);
            }
        }
    }

    public void setRootLevel(LogLevel level) {
        if (level != null) {
            this.config.setRootLevel(level);
        }
    }

    public void setLoggerLevel(String name, LogLevel level) {
        LoggerImpl logger = getLogger(name);
        logger.setLevel(level);
    }

    public void rotateAppenders() {
        for (LogAppender appender : asyncProcessor.getAppenders()) {
            if (appender instanceof RollingFileAppender) {
                ((RollingFileAppender) appender).rotate();
            }
        }
    }

    public void flush() {
        asyncProcessor.flush();
    }

    public void shutdown() {
        if (apiServer != null) {
            apiServer.stop();
        }
        asyncProcessor.shutdown();
    }

    public LoggerConfig getConfig() {
        return config;
    }

    public AsyncLogProcessor getAsyncProcessor() {
        return asyncProcessor;
    }

    public LoggerApiServer getApiServer() {
        return apiServer;
    }
}
