package com.campx.academic.timetable.config;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the explicit lifecycle of the PostgreSQL HikariCP connection pool
 * for ACD-05: Timetable Management Service.
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
            defaultInstance.close();
            defaultInstance = null;
        }
    }

    public synchronized void initialize() {
        if (closed) {
            throw new IllegalStateException("DatabaseConnectionManager has already been closed");
        }
        if (initialized) {
            return;
        }

        try {
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

            hikariConfig.addDataSourceProperty("cachePrepStmts", "true");
            hikariConfig.addDataSourceProperty("prepStmtCacheSize", "250");
            hikariConfig.addDataSourceProperty("prepStmtCacheSqlLimit", "2048");

            this.dataSource = new HikariDataSource(hikariConfig);
            this.initialized = true;
            logger.info("Initialized HikariCP connection pool '{}' targeting {}",
                    config.getPoolName(), config.getJdbcUrl());
        } catch (Exception e) {
            logger.error("Failed to initialize HikariCP connection pool: {}", e.getMessage());
            throw new IllegalStateException("Database initialization failure: " + e.getMessage(), e);
        }
    }

    public Connection getConnection() throws SQLException {
        if (!initialized) {
            initialize();
        }
        if (closed || dataSource == null || dataSource.isClosed()) {
            throw new SQLException("Connection pool is closed or unavailable");
        }
        return dataSource.getConnection();
    }

    public DataSource getDataSource() {
        if (!initialized) {
            initialize();
        }
        return dataSource;
    }

    public DatabaseConfig getConfig() {
        return config;
    }

    public boolean isInitialized() {
        return initialized;
    }

    public boolean isClosed() {
        return closed;
    }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        initialized = false;
        if (dataSource != null && !dataSource.isClosed()) {
            try {
                dataSource.close();
                logger.info("HikariCP connection pool '{}' successfully shut down", config.getPoolName());
            } catch (Exception e) {
                logger.warn("Error shutting down HikariCP pool: {}", e.getMessage());
            }
        }
    }
}
