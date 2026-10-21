package com.campx.gateway.identity;

import com.campx.logger.CampXLogger;
import com.campx.logger.CampXLoggerFactory;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;

/**
 * Manages the HikariCP connection pool for API Gateway authoritative database operations.
 *
 * @author CampX Platform Engineering Team
 * @version 2.0.0
 * @since 2.0.0
 */
public class GatewayDatabaseManager implements AutoCloseable {

    private static final CampXLogger logger = CampXLoggerFactory.getLogger(GatewayDatabaseManager.class);

    private static volatile GatewayDatabaseManager defaultInstance;

    private HikariDataSource dataSource;
    private boolean initialized = false;
    private boolean closed = false;

    public GatewayDatabaseManager() {}

    public GatewayDatabaseManager(DataSource dataSource) {
        if (dataSource instanceof HikariDataSource) {
            this.dataSource = (HikariDataSource) dataSource;
        }
        this.initialized = true;
    }

    public static GatewayDatabaseManager getInstance() {
        if (defaultInstance == null) {
            synchronized (GatewayDatabaseManager.class) {
                if (defaultInstance == null) {
                    defaultInstance = new GatewayDatabaseManager();
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
            throw new IllegalStateException("GatewayDatabaseManager has been closed");
        }
        if (initialized && dataSource != null && !dataSource.isClosed()) {
            return;
        }

        String jdbcUrl = resolveJdbcUrl();
        String username = resolveUsername();
        String password = resolvePassword();

        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("Database JDBC URL not configured");
        }

        HikariConfig hikariConfig = new HikariConfig();
        hikariConfig.setJdbcUrl(jdbcUrl.trim());
        if (username != null) hikariConfig.setUsername(username.trim());
        if (password != null) hikariConfig.setPassword(password.trim());
        hikariConfig.setDriverClassName("org.postgresql.Driver");
        hikariConfig.setMaximumPoolSize(5);
        hikariConfig.setMinimumIdle(1);
        hikariConfig.setConnectionTimeout(15000);
        hikariConfig.setPoolName("CampXSync-Gateway-Pool");

        this.dataSource = new HikariDataSource(hikariConfig);
        this.initialized = true;
        logger.info("Gateway HikariCP connection pool initialized");
    }

    public Connection getConnection() throws SQLException {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            initialize();
        }
        return dataSource.getConnection();
    }

    public DataSource getDataSource() {
        if (!initialized || dataSource == null || dataSource.isClosed()) {
            initialize();
        }
        return dataSource;
    }

    public static String resolveJdbcUrl() {
        String enabled = System.getProperty("campx.db.enabled");
        if ("false".equalsIgnoreCase(enabled)) return null;

        String url = System.getProperty("campx.jdbc.url");
        if ("none".equalsIgnoreCase(url)) return null;
        if (url == null || url.trim().isEmpty()) url = System.getProperty("campx.db.url");
        if (url == null || url.trim().isEmpty()) url = System.getenv("SUPABASE_JDBC_URL");
        if (url == null || url.trim().isEmpty()) url = System.getenv("CAMPX_JDBC_URL");
        if (url == null || url.trim().isEmpty()) url = System.getenv("DATABASE_URL");
        return url;
    }

    public static String resolveUsername() {
        String user = System.getProperty("campx.db.username");
        if (user == null || user.trim().isEmpty()) user = System.getenv("SUPABASE_USER");
        if (user == null || user.trim().isEmpty()) user = System.getenv("CAMPX_DB_USER");
        return user;
    }

    public static String resolvePassword() {
        String pass = System.getProperty("campx.db.password");
        if (pass == null || pass.trim().isEmpty()) pass = System.getenv("SUPABASE_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) pass = System.getenv("CAMPX_DB_PASSWORD");
        return pass;
    }

    @Override
    public synchronized void close() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            logger.info("Gateway HikariCP connection pool closed");
        }
        closed = true;
        initialized = false;
    }
}
