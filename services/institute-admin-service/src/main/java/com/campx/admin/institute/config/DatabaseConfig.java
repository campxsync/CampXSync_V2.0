package com.campx.admin.institute.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration holder and loader for PostgreSQL and HikariCP connection pooling
 * in ADM-01: Institute Admin Service.
 * <p>
 * Follows the CampXSync configuration resolution hierarchy with strict precedence:
 * <ol>
 *   <li><b>Java System Properties</b> (e.g. {@code -Dcampx.db.url=...} or {@code -Ddb.url=...})</li>
 *   <li><b>OS Environment Variables</b> (e.g. {@code DATABASE_URL}, {@code DB_URL}, {@code DB_PASSWORD})</li>
 *   <li><b>Properties File</b> (file system or classpath {@code campx-database.properties} or {@code application.properties})</li>
 *   <li><b>Internal Defaults</b> (sensible defaults; zero credentials hard-coded)</li>
 * </ol>
 *
 * @see DatabaseConnectionManager
 */
public class DatabaseConfig {

    private static final String DEFAULT_PROPERTIES_FILE = "campx-database.properties";
    private static final String FALLBACK_PROPERTIES_FILE = "application.properties";

    private String jdbcUrl;
    private String username;
    private String password;
    private String driverClassName = "org.postgresql.Driver";
    private int maximumPoolSize = 10;
    private int minimumIdle = 2;
    private long connectionTimeoutMs = 30000L;
    private long idleTimeoutMs = 600000L;
    private long maxLifetimeMs = 1800000L;
    private String poolName = "CampXSync-ADM01-Pool";

    public DatabaseConfig() {}

    /**
     * Loads the database configuration using default property resolution.
     *
     * @return fully populated {@link DatabaseConfig}
     */
    public static DatabaseConfig load() {
        return load(null);
    }

    /**
     * Loads the database configuration from the specified properties file if present,
     * layered with environment variables and system properties.
     *
     * @param customPropertiesPath optional path to properties file
     * @return fully populated {@link DatabaseConfig}
     */
    public static DatabaseConfig load(String customPropertiesPath) {
        DatabaseConfig config = new DatabaseConfig();
        Properties props = new Properties();

        // 1. Attempt to load properties file if exists
        loadProperties(props, customPropertiesPath);
        if (props.isEmpty()) {
            loadProperties(props, DEFAULT_PROPERTIES_FILE);
        }
        if (props.isEmpty()) {
            loadProperties(props, FALLBACK_PROPERTIES_FILE);
        }

        // 2. Resolve JDBC URL
        String url = resolve(props,
                new String[]{"campx.db.url", "db.url", "database.url", "supabase.db.url", "supabase.url"},
                new String[]{"CAMPX_DB_URL", "DATABASE_URL", "DB_URL", "SUPABASE_DB_URL", "SUPABASE_JDBC_URL", "SUPABASE_URL"},
                "jdbc:postgresql://localhost:5432/postgres");
        config.setJdbcUrl(normalizeJdbcUrl(url));

        // 3. Resolve Username
        String user = resolve(props,
                new String[]{"campx.db.username", "db.username", "database.username", "supabase.user", "supabase.db.user"},
                new String[]{"CAMPX_DB_USERNAME", "DB_USERNAME", "DB_USER", "SUPABASE_DB_USER", "SUPABASE_USER", "POSTGRES_USER"},
                "postgres");
        config.setUsername(user);

        // 4. Resolve Password (default empty, NEVER hard-coded)
        String pass = resolve(props,
                new String[]{"campx.db.password", "db.password", "database.password", "supabase.password", "supabase.db.password"},
                new String[]{"CAMPX_DB_PASSWORD", "DB_PASSWORD", "SUPABASE_DB_PASSWORD", "SUPABASE_PASSWORD", "POSTGRES_PASSWORD"},
                "");
        config.setPassword(pass);

        // 5. Resolve Driver Class
        String driver = resolve(props,
                new String[]{"campx.db.driver", "db.driver", "db.driver-class-name"},
                new String[]{"CAMPX_DB_DRIVER", "DB_DRIVER"},
                "org.postgresql.Driver");
        config.setDriverClassName(driver);

        // 6. Resolve Maximum Pool Size
        String maxPool = resolve(props,
                new String[]{"campx.db.pool.max-size", "db.pool.maximumPoolSize", "db.pool.max-size"},
                new String[]{"CAMPX_DB_MAX_POOL_SIZE", "DB_POOL_MAX_SIZE", "DB_MAX_POOL_SIZE"},
                "10");
        try {
            config.setMaximumPoolSize(Integer.parseInt(maxPool.trim()));
        } catch (NumberFormatException ignored) {}

        // 7. Resolve Minimum Idle
        String minIdle = resolve(props,
                new String[]{"campx.db.pool.min-idle", "db.pool.minimumIdle", "db.pool.min-idle"},
                new String[]{"CAMPX_DB_MIN_IDLE", "DB_POOL_MIN_IDLE", "DB_MIN_IDLE"},
                "2");
        try {
            config.setMinimumIdle(Integer.parseInt(minIdle.trim()));
        } catch (NumberFormatException ignored) {}

        // 8. Resolve Connection Timeout
        String connTimeout = resolve(props,
                new String[]{"campx.db.pool.connection-timeout", "db.pool.connectionTimeout"},
                new String[]{"CAMPX_DB_CONNECTION_TIMEOUT", "DB_POOL_CONNECTION_TIMEOUT", "DB_CONNECTION_TIMEOUT"},
                "30000");
        try {
            config.setConnectionTimeoutMs(Long.parseLong(connTimeout.trim()));
        } catch (NumberFormatException ignored) {}

        // 9. Resolve Idle Timeout
        String idleTimeout = resolve(props,
                new String[]{"campx.db.pool.idle-timeout", "db.pool.idleTimeout"},
                new String[]{"CAMPX_DB_IDLE_TIMEOUT", "DB_POOL_IDLE_TIMEOUT"},
                "600000");
        try {
            config.setIdleTimeoutMs(Long.parseLong(idleTimeout.trim()));
        } catch (NumberFormatException ignored) {}

        // 10. Resolve Max Lifetime
        String maxLifetime = resolve(props,
                new String[]{"campx.db.pool.max-lifetime", "db.pool.maxLifetime"},
                new String[]{"CAMPX_DB_MAX_LIFETIME", "DB_POOL_MAX_LIFETIME"},
                "1800000");
        try {
            config.setMaxLifetimeMs(Long.parseLong(maxLifetime.trim()));
        } catch (NumberFormatException ignored) {}

        // 11. Resolve Pool Name
        String poolName = resolve(props,
                new String[]{"campx.db.pool.name", "db.pool.name"},
                new String[]{"CAMPX_DB_POOL_NAME", "DB_POOL_NAME"},
                "CampXSync-ADM01-Pool");
        config.setPoolName(poolName);

        return config;
    }

    /**
     * Normalizes JDBC URL by prefixing {@code jdbc:} if raw postgresql URI format was supplied.
     */
    private static String normalizeJdbcUrl(String url) {
        if (url == null || url.trim().isEmpty()) {
            return url;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("postgres://")) {
            return "jdbc:postgresql://" + trimmed.substring("postgres://".length());
        }
        if (trimmed.startsWith("postgresql://")) {
            return "jdbc:postgresql://" + trimmed.substring("postgresql://".length());
        }
        return trimmed;
    }

    private static void loadProperties(Properties props, String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        File file = new File(path);
        if (file.exists() && file.canRead()) {
            try (InputStream in = new FileInputStream(file)) {
                props.load(in);
            } catch (Exception ignored) {}
        } else {
            try (InputStream in = DatabaseConfig.class.getClassLoader().getResourceAsStream(path)) {
                if (in != null) {
                    props.load(in);
                }
            } catch (Exception ignored) {}
        }
    }

    private static String resolve(Properties props, String[] sysProps, String[] envVars, String defaultValue) {
        // 1. Java System Properties
        for (String sysProp : sysProps) {
            String val = System.getProperty(sysProp);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        // 2. OS Environment Variables
        for (String envVar : envVars) {
            String val = System.getenv(envVar);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        // 3. Properties File
        if (props != null) {
            for (String sysProp : sysProps) {
                String val = props.getProperty(sysProp);
                if (val != null && !val.trim().isEmpty()) {
                    return val.trim();
                }
            }
        }
        // 4. Default
        return defaultValue;
    }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

    public String getDriverClassName() { return driverClassName; }
    public void setDriverClassName(String driverClassName) { this.driverClassName = driverClassName; }

    public int getMaximumPoolSize() { return maximumPoolSize; }
    public void setMaximumPoolSize(int maximumPoolSize) { this.maximumPoolSize = maximumPoolSize; }

    public int getMinimumIdle() { return minimumIdle; }
    public void setMinimumIdle(int minimumIdle) { this.minimumIdle = minimumIdle; }

    public long getConnectionTimeoutMs() { return connectionTimeoutMs; }
    public void setConnectionTimeoutMs(long connectionTimeoutMs) { this.connectionTimeoutMs = connectionTimeoutMs; }

    public long getIdleTimeoutMs() { return idleTimeoutMs; }
    public void setIdleTimeoutMs(long idleTimeoutMs) { this.idleTimeoutMs = idleTimeoutMs; }

    public long getMaxLifetimeMs() { return maxLifetimeMs; }
    public void setMaxLifetimeMs(long maxLifetimeMs) { this.maxLifetimeMs = maxLifetimeMs; }

    public String getPoolName() { return poolName; }
    public void setPoolName(String poolName) { this.poolName = poolName; }

    /**
     * Returns a sanitized JDBC URL safe for logging (masks embedded password if any).
     *
     * @return safe JDBC URL representation
     */
    public String getSanitizedJdbcUrl() {
        if (jdbcUrl == null) {
            return "null";
        }
        return jdbcUrl.replaceAll(":[^/@:]+@", ":***@");
    }

    @Override
    public String toString() {
        return "DatabaseConfig{"
                + "jdbcUrl='" + getSanitizedJdbcUrl() + '\''
                + ", username='" + username + '\''
                + ", password='***'"
                + ", driverClassName='" + driverClassName + '\''
                + ", maximumPoolSize=" + maximumPoolSize
                + ", minimumIdle=" + minimumIdle
                + ", connectionTimeoutMs=" + connectionTimeoutMs
                + ", idleTimeoutMs=" + idleTimeoutMs
                + ", maxLifetimeMs=" + maxLifetimeMs
                + ", poolName='" + poolName + '\''
                + '}';
    }
}
