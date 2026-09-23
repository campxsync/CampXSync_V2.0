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
 * Central singleton managing logger lifecycle, configuration bootstrap, appender dispatching,
 * and optional embedded HTTP management API server.
 * <p>
 * Implements double-checked locking for thread-safe singleton initialization.
 * Automatically attaches a JVM shutdown hook to guarantee that buffered logs are flushed
 * and file appenders cleanly closed upon service termination.
 *
 * @see LoggerConfig
 * @see AsyncLogProcessor
 * @see LoggerApiServer
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

    /**
     * Returns the global singleton instance of the log manager.
     *
     * @return active {@link LogManager} instance
     */
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

    /**
     * Builds and registers console, rolling file, and JSON file appenders according to active configuration.
     */
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

    /**
     * Starts the embedded management HTTP API server if enabled in configuration.
     */
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

    /**
     * Registers a JVM shutdown hook to trigger graceful logging shutdown and buffer flushing.
     */
    private void registerShutdownHook() {
        Runtime.getRuntime().addShutdownHook(new Thread(() -> {
            shutdown();
        }, "CampX-LogShutdownHook"));
    }

    /**
     * Retrieves or creates a named {@link LoggerImpl} instance.
     *
     * @param name category or class name
     * @return logger instance
     */
    public LoggerImpl getLogger(String name) {
        String loggerName = (name != null && !name.trim().isEmpty()) ? name : "ROOT";
        return loggers.computeIfAbsent(loggerName, LoggerImpl::new);
    }

    /**
     * Dispatches an event to the asynchronous processor queue or directly to appenders if async is disabled.
     *
     * @param event log event to dispatch
     */
    public void dispatch(LogEvent event) {
        if (config.isAsyncEnabled()) {
            asyncProcessor.enqueue(event);
        } else {
            for (LogAppender appender : asyncProcessor.getAppenders()) {
                appender.append(event);
            }
        }
    }

    /**
     * Dynamically updates the global root logging severity threshold.
     *
     * @param level new root severity level
     */
    public void setRootLevel(LogLevel level) {
        if (level != null) {
            this.config.setRootLevel(level);
        }
    }

    /**
     * Dynamically updates the logging threshold for a specific named logger category.
     *
     * @param name  logger category name
     * @param level new severity level
     */
    public void setLoggerLevel(String name, LogLevel level) {
        LoggerImpl logger = getLogger(name);
        logger.setLevel(level);
    }

    /**
     * Triggers manual archive rotation on all active rolling file appenders.
     */
    public void rotateAppenders() {
        for (LogAppender appender : asyncProcessor.getAppenders()) {
            if (appender instanceof RollingFileAppender) {
                ((RollingFileAppender) appender).rotate();
            }
        }
    }

    /**
     * Flushes all internal async queues and appender write buffers.
     */
    public void flush() {
        asyncProcessor.flush();
    }

    /**
     * Gracefully stops the embedded API server, flushes queues, and terminates appenders.
     */
    public void shutdown() {
        if (apiServer != null) {
            apiServer.stop();
        }
        asyncProcessor.shutdown();
    }

    /**
     * Returns the active logger configuration object.
     *
     * @return active {@link LoggerConfig}
     */
    public LoggerConfig getConfig() {
        return config;
    }

    /**
     * Returns the underlying asynchronous processor instance.
     *
     * @return {@link AsyncLogProcessor}
     */
    public AsyncLogProcessor getAsyncProcessor() {
        return asyncProcessor;
    }

    /**
     * Returns the embedded management HTTP API server instance, or {@code null} if not enabled.
     *
     * @return active {@link LoggerApiServer} or {@code null}
     */
    public LoggerApiServer getApiServer() {
        return apiServer;
    }
}
