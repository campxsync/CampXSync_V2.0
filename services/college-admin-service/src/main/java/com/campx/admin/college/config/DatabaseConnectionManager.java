package com.campx.admin.college.config;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the explicit lifecycle of the PostgreSQL HikariCP connection pool
 * for ADM-02: College Admin Service.
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

        if (config.getJdbcUrl() == null || config.getJdbcUrl().trim().isEmpty()) {
            throw new IllegalStateException("Database JDBC URL is not configured. Set SUPABASE_JDBC_URL or CAMPX_JDBC_URL.");
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(config.getJdbcUrl());
        if (config.getUsername() != null) hikariConfig.setUsername(config.getUsername());
        if (config.getPassword() != null) hikariConfig.setPassword(config.getPassword());
        hikariConfig.setMaximumPoolSize(config.getMaximumPoolSize());
        hikariConfig.setMinimumIdle(config.getMinimumIdle());
        hikariConfig.setConnectionTimeout(config.getConnectionTimeoutMs());
        hikariConfig.setIdleTimeout(config.getIdleTimeoutMs());
        hikariConfig.setMaxLifetime(config.getMaxLifetimeMs());
        hikariConfig.setPoolName(config.getPoolName());

        this.dataSource = new HikariDataSource(hikariConfig);
        this.initialized = true;
        logger.info("DatabaseConnectionManager initialized connection pool [{}] -> {}",
                config.getPoolName(), config.getJdbcUrl());
    }

    public DataSource getDataSource() {
        if (!initialized) {
            initialize();
        }
        return dataSource;
    }

    public Connection getConnection() throws SQLException {
        if (!initialized) {
            initialize();
        }
        return dataSource.getConnection();
    }

    public boolean isInitialized() { return initialized; }
    public boolean isClosed() { return closed; }

    @Override
    public synchronized void close() {
        if (closed) return;
        closed = true;
        if (dataSource != null && !dataSource.isClosed()) {
            logger.info("Closing HikariCP connection pool: {}", config.getPoolName());
            dataSource.close();
        }
        initialized = false;
    }
}
