package com.campx.academic.analytics.config;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the explicit lifecycle of the PostgreSQL HikariCP connection pool
 * for ACD-10: Reporting & Analytics Service.
 */
public class DatabaseConnectionManager implements AutoCloseable {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DatabaseConnectionManager.class);

    private static volatile DatabaseConnectionManager defaultInstance;

    private final DatabaseConfig config;
    private HikariDataSource dataSource;
    private boolean initialized = false;
    private boolean closed = false;

    public DatabaseConnectionManager() {
        this(DatabaseConfig.load());
    }

    public DatabaseConnectionManager(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DatabaseConfig cannot be null");
        }
        this.config = config;
    }

    public DatabaseConnectionManager(HikariDataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("HikariDataSource cannot be null");
        }
        this.dataSource = dataSource;
        this.config = new DatabaseConfig();
        this.config.setPoolName(dataSource.getPoolName());
        this.initialized = true;
    }

    public static DatabaseConnectionManager getInstance() {
        if (defaultInstance == null) {
            synchronized (DatabaseConnectionManager.class) {
                if (defaultInstance == null) {
                    defaultInstance = new DatabaseConnectionManager();
                }
            }
        }
        return defaultInstance;
    }

    public static synchronized void resetInstance() {
        if (defaultInstance != null) {
            try {
                defaultInstance.close();
            } catch (Exception ignored) {}
            defaultInstance = null;
        }
    }

    public synchronized void initialize() {
        if (initialized) {
            return;
        }
        if (closed) {
            throw new IllegalStateException("Cannot initialize a closed DatabaseConnectionManager");
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        hikariConfig.setUsername(config.getUsername());
        hikariConfig.setPassword(config.getPassword());
        hikariConfig.setDriverClassName(config.getDriverClassName());
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
        hikariConfig.setPoolName(config.getPoolName());

        hikariConfig.setAutoCommit(true);
        hikariConfig.addDataSourceProperty("ApplicationName", "CampXSync-ACD10-AnalyticsService");

        this.dataSource = new HikariDataSource(hikariConfig);
        this.initialized = true;
        logger.info("Initialized ACD-10 HikariCP DataSource: pool={} url={}", config.getPoolName(), config.getJdbcUrl());
    }

    public Connection getConnection() throws SQLException {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    initialize();
                }
            }
        }
        if (closed || dataSource == null || dataSource.isClosed()) {
            throw new SQLException("DataSource is closed");
        }
        return dataSource.getConnection();
    }

    public DataSource getDataSource() {
        if (!initialized) {
            synchronized (this) {
                if (!initialized) {
                    initialize();
                }
            }
        }
        return dataSource;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Closed ACD-10 HikariCP DataSource: pool={}", config.getPoolName());
        }
    }
}
