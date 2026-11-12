package com.campx.document.config;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

/**
 * HikariCP connection pool for the Student Documents Service (STM-06).
 */
public class DatabaseConnectionManager {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DatabaseConnectionManager.class);
    private static DatabaseConnectionManager instance;
    private HikariDataSource dataSource;

    private DatabaseConnectionManager() {
    }

    public static synchronized DatabaseConnectionManager getInstance() {
        if (instance == null) {
            instance = new DatabaseConnectionManager();
        }
        return instance;
    }

    public synchronized void initialize(DatabaseConfig config) {
        if (dataSource != null && !dataSource.isClosed()) {
            return;
        }
        if (config == null || !config.isConfigured()) {
            logger.warn("No JDBC URL/password configured; document persistence remains in-memory/uninitialized");
            return;
        }
        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(10);
        hikariConfig.setMinimumIdle(2);
        hikariConfig.setPoolName("DocumentHikariPool");
        this.dataSource = new HikariDataSource(hikariConfig);
        logger.info("Student Documents HikariCP connection pool initialized");
    }

    public Connection getConnection() throws SQLException {
        if (dataSource == null) {
            throw new SQLException("Database connection pool not initialized");
        }
        return dataSource.getConnection();
    }

    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Student Documents HikariCP connection pool closed");
        }
    }
}

