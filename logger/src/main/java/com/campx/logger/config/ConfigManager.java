package com.campx.logger.config;

import com.campx.logger.api.LogLevel;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Central configuration loader for the CampXSync logging framework.
 * <p>
 * Employs a hierarchical configuration resolution strategy with strict precedence:
 * <ol>
 *   <li><b>Java System Properties</b> (e.g. {@code -Dcampx.logger.level=DEBUG})</li>
 *   <li><b>OS Environment Variables</b> (e.g. {@code CAMPX_LOG_LEVEL=DEBUG})</li>
 *   <li><b>Properties File</b> (file system or classpath {@code campx-logger.properties})</li>
 *   <li><b>Internal Defaults</b> defined on {@link LoggerConfig}</li>
 * </ol>
 *
 * @see LoggerConfig
 */
public final class ConfigManager {

    /** Default property configuration file name searched on file system and classpath. */
    private static final String DEFAULT_CONFIG_FILE = "campx-logger.properties";

    private ConfigManager() {}

    /**
     * Loads the logging configuration using the default configuration file name {@code "campx-logger.properties"}.
     *
     * @return populated {@link LoggerConfig}
     */
    public static LoggerConfig load() {
        return load(DEFAULT_CONFIG_FILE);
    }

    /**
     * Loads the logging configuration from the given properties file or classpath resource,
     * overlaying system properties and environment variables.
     *
     * @param filename configuration resource or file path
     * @return populated {@link LoggerConfig}
     */
    public static LoggerConfig load(String filename) {
        LoggerConfig config = new LoggerConfig();
        Properties props = new Properties();

        // 1. Try loading from file system
        File file = new File(filename);
        if (file.exists() && file.canRead()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (Exception e) {
                System.err.println("[ConfigManager] Could not load from " + filename + ": " + e.getMessage());
            }
        } else {
            // 2. Try loading from classpath
            try (InputStream in = ConfigManager.class.getClassLoader().getResourceAsStream(filename)) {
                if (in != null) {
                    props.load(in);
                }
            } catch (Exception ignored) {}
        }

        // Parse properties
        String levelStr = getVal(props, "campx.logger.level", "CAMPX_LOG_LEVEL");
        if (levelStr != null) {
            config.setRootLevel(LogLevel.fromString(levelStr, config.getRootLevel()));
        }

        String consoleStr = getVal(props, "campx.logger.console.enabled", "CAMPX_LOG_CONSOLE_ENABLED");
        if (consoleStr != null) {
            config.setConsoleEnabled(Boolean.parseBoolean(consoleStr));
        }

        String colorStr = getVal(props, "campx.logger.console.color", "CAMPX_LOG_CONSOLE_COLOR");
        if (colorStr != null) {
            config.setColorEnabled(Boolean.parseBoolean(colorStr));
        }

        String fileStr = getVal(props, "campx.logger.file.enabled", "CAMPX_LOG_FILE_ENABLED");
        if (fileStr != null) {
            config.setFileEnabled(Boolean.parseBoolean(fileStr));
        }

        String filePath = getVal(props, "campx.logger.file.path", "CAMPX_LOG_FILE_PATH");
        if (filePath != null && !filePath.trim().isEmpty()) {
            config.setFilePath(filePath.trim());
        }

        String jsonFileStr = getVal(props, "campx.logger.json.enabled", "CAMPX_LOG_JSON_ENABLED");
        if (jsonFileStr != null) {
            config.setJsonFileEnabled(Boolean.parseBoolean(jsonFileStr));
        }

        String jsonFilePath = getVal(props, "campx.logger.json.path", "CAMPX_LOG_JSON_PATH");
        if (jsonFilePath != null && !jsonFilePath.trim().isEmpty()) {
            config.setJsonFilePath(jsonFilePath.trim());
        }

        String maxSizeMb = getVal(props, "campx.logger.file.maxSizeMb", "CAMPX_LOG_MAX_SIZE_MB");
        if (maxSizeMb != null) {
            try {
                config.setMaxFileSize(Long.parseLong(maxSizeMb.trim()) * 1024 * 1024L);
            } catch (NumberFormatException ignored) {}
        }

        String maxBackup = getVal(props, "campx.logger.file.maxBackups", "CAMPX_LOG_MAX_BACKUPS");
        if (maxBackup != null) {
            try {
                config.setMaxBackupIndex(Integer.parseInt(maxBackup.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String asyncStr = getVal(props, "campx.logger.async.enabled", "CAMPX_LOG_ASYNC_ENABLED");
        if (asyncStr != null) {
            config.setAsyncEnabled(Boolean.parseBoolean(asyncStr));
        }

        String queueCap = getVal(props, "campx.logger.async.capacity", "CAMPX_LOG_ASYNC_CAPACITY");
        if (queueCap != null) {
            try {
                config.setQueueCapacity(Integer.parseInt(queueCap.trim()));
            } catch (NumberFormatException ignored) {}
        }

        String maskStr = getVal(props, "campx.logger.security.mask", "CAMPX_LOG_SECURITY_MASK");
        if (maskStr != null) {
            config.setMaskSecurityData(Boolean.parseBoolean(maskStr));
        }

        String apiServerStr = getVal(props, "campx.logger.server.enabled", "CAMPX_LOG_SERVER_ENABLED");
        if (apiServerStr != null) {
            config.setApiServerEnabled(Boolean.parseBoolean(apiServerStr));
        }

        String portStr = getVal(props, "campx.logger.server.port", "CAMPX_LOG_SERVER_PORT");
        if (portStr != null) {
            try {
                config.setApiServerPort(Integer.parseInt(portStr.trim()));
            } catch (NumberFormatException ignored) {}
        }

        return config;
    }

    /**
     * Resolves a configuration property value with system property -> env var -> file property precedence.
     *
     * @param props   properties object loaded from file or classpath
     * @param sysProp Java system property name
     * @param envVar  OS environment variable name
     * @return resolved value, or {@code null} if unset across all sources
     */
    private static String getVal(Properties props, String sysProp, String envVar) {
        // System property overrides
        String val = System.getProperty(sysProp);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        // Environment variable
        val = System.getenv(envVar);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        // Properties file
        val = props.getProperty(sysProp);
        if (val != null && !val.trim().isEmpty()) {
            return val.trim();
        }
        return null;
    }
}
