package com.campx.admin.institute.config;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the explicit lifecycle of the PostgreSQL HikariCP connection pool
 * for ADM-01: Institute Admin Service.
 * <p>
 * Provides explicit initialize and close operations so the datasource can be cleanly
 * managed during service bootstrap and shutdown hooks without Spring/JPA dependencies.
 *
 * @see DatabaseConfig
 */
public class DatabaseConnectionManager implements AutoCloseable {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(DatabaseConnectionManager.class);

    private static volatile DatabaseConnectionManager defaultInstance;

    private final DatabaseConfig config;
    private HikariDataSource dataSource;
    private boolean initialized = false;
    private boolean closed = false;

    /**
     * Initializes the manager with configuration resolved from environment and system properties.
     */
    public DatabaseConnectionManager() {
        this(DatabaseConfig.load());
    }

    /**
     * Initializes the manager with explicit database configuration.
     *
     * @param config database and connection pool configuration
     */
    public DatabaseConnectionManager(DatabaseConfig config) {
        if (config == null) {
            throw new IllegalArgumentException("DatabaseConfig cannot be null");
        }
        this.config = config;
    }

    /**
     * Wraps an existing pre-configured {@link HikariDataSource} (useful for testing and mocks).
     *
     * @param dataSource pre-existing HikariDataSource
     */
    public DatabaseConnectionManager(HikariDataSource dataSource) {
        if (dataSource == null) {
            throw new IllegalArgumentException("HikariDataSource cannot be null");
        }
        this.dataSource = dataSource;
        this.config = new DatabaseConfig();
        this.config.setPoolName(dataSource.getPoolName());
        this.initialized = true;
    }

    /**
     * Returns the singleton instance of {@link DatabaseConnectionManager}, lazily instantiated.
     *
     * @return shared database connection manager
     */
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

    /**
     * Closes and resets the singleton instance (primarily for test teardown and re-configuration).
     */
    public static synchronized void resetInstance() {
        if (defaultInstance != null) {
            defaultInstance.close();
            defaultInstance = null;
        }
    }

    /**
     * Explicitly initializes the underlying HikariCP connection pool using the configured settings.
     *
     * @throws IllegalStateException if the manager has already been closed
     */
    public synchronized void initialize() {
        if (closed) {
            throw new IllegalStateException("DatabaseConnectionManager has already been closed");
        }
        if (initialized && dataSource != null && !dataSource.isClosed()) {
            return;
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

        // PostgreSQL driver configuration for transaction poolers (Supavisor)
        hikariConfig.addDataSourceProperty("prepareThreshold", "0");

        this.dataSource = new HikariDataSource(hikariConfig);
        this.initialized = true;

        logger.info("Initialized HikariCP datasource [{}] connecting to [{}] (maxPoolSize={}, minIdle={})",
                config.getPoolName(), config.getSanitizedJdbcUrl(), config.getMaximumPoolSize(), config.getMinimumIdle());
    }

    /**
     * Returns the underlying {@link DataSource}, initializing it lazily if not yet created.
     *
     * @return active {@link DataSource}
     * @throws IllegalStateException if this manager has been closed
     */
    public synchronized DataSource getDataSource() {
        if (closed) {
            throw new IllegalStateException("DatabaseConnectionManager has been closed");
        }
        if (!initialized || dataSource == null) {
            initialize();
        }
        return dataSource;
    }

    /**
     * Obtains a physical SQL connection from the underlying connection pool.
     *
     * @return active {@link Connection}
     * @throws SQLException if a database access error occurs
     */
    public Connection getConnection() throws SQLException {
        return getDataSource().getConnection();
    }

    /**
     * Explicitly shuts down the HikariCP connection pool and releases all database connections.
     */
    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Closed HikariCP datasource [{}]", config != null ? config.getPoolName() : "unknown");
        }
        closed = true;
        initialized = false;
    }

    /**
     * Checks if this manager or its underlying datasource has been closed.
     *
     * @return {@code true} if closed
     */
    public synchronized boolean isClosed() {
        return closed || (dataSource != null && dataSource.isClosed());
    }

    /**
     * Checks if the connection pool has been actively initialized.
     *
     * @return {@code true} if initialized and active
     */
    public synchronized boolean isInitialized() {
        return initialized && dataSource != null && !dataSource.isClosed();
    }

    /**
     * Returns the configuration backing this connection manager.
     *
     * @return {@link DatabaseConfig}
     */
    public DatabaseConfig getConfig() {
        return config;
    }
}
