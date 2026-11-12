package com.campx.admissions.config;

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

    private DatabaseConnectionManager() {}

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

        if (config.getJdbcUrl() == null || config.getJdbcUrl().trim().isEmpty()) {
            logger.warn("No JDBC URL configured; running in uninitialized/mock mode");
            return;
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getMaxPoolSize());
        hikariConfig.setMinimumIdle(config.getMinIdle());
        hikariConfig.setPoolName("AdmissionsHikariPool");

        this.dataSource = new HikariDataSource(hikariConfig);
        try (Connection c = dataSource.getConnection();
             java.sql.Statement s = c.createStatement()) {
            s.execute("SELECT 1");
            logger.info("Admissions HikariCP connection pool pre-warmed successfully");
        } catch (Exception ex) {
            logger.warn("Admissions database pre-warming check encountered error: {}", ex.getMessage());
        }
        logger.info("Admissions HikariCP connection pool initialized successfully");
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
            logger.info("Admissions HikariCP connection pool closed");
        }
    }
}

