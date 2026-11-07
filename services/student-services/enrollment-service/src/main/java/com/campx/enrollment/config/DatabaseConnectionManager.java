package com.campx.enrollment.config;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import java.sql.Connection;
import java.sql.SQLException;

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
            logger.warn("No JDBC URL/password configured; enrollment persistence remains uninitialized");
            return;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setPoolName("EnrollmentHikariPool");

        this.dataSource = new HikariDataSource(hikariConfig);
        logger.info("Enrollment HikariCP connection pool initialized");
    }

    public boolean isInitialized() {
        return dataSource != null && !dataSource.isClosed();
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
            dataSource = null;
            logger.info("Enrollment HikariCP connection pool closed");
        }
    }
}
