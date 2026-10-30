package com.campx.academic.analytics.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration holder and loader for PostgreSQL and HikariCP connection pooling
 * in ACD-10: Reporting & Analytics Service.
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
    private String poolName = "CampXSync-ACD10-Pool";

    public DatabaseConfig() {}

    public static DatabaseConfig load() {
        return load(null);
    }

    public static DatabaseConfig load(String customPropertiesPath) {
        DatabaseConfig config = new DatabaseConfig();
        Properties props = new Properties();

        loadProperties(props, customPropertiesPath);
        if (props.isEmpty()) {
            loadProperties(props, DEFAULT_PROPERTIES_FILE);
        }
        if (props.isEmpty()) {
            loadProperties(props, FALLBACK_PROPERTIES_FILE);
        }

        String url = resolve(props,
                new String[]{"campx.db.url", "db.url", "database.url", "supabase.db.url", "supabase.url"},
                new String[]{"CAMPX_DB_URL", "DATABASE_URL", "DB_URL", "SUPABASE_DB_URL", "SUPABASE_JDBC_URL", "SUPABASE_URL"},
                "jdbc:postgresql://localhost:5432/postgres");
        config.setJdbcUrl(normalizeJdbcUrl(url));

        String user = resolve(props,
                new String[]{"campx.db.user", "campx.db.username", "db.user", "db.username", "database.username", "supabase.db.user"},
                new String[]{"CAMPX_DB_USER", "CAMPX_DB_USERNAME", "DATABASE_USER", "DATABASE_USERNAME", "DB_USER", "SUPABASE_DB_USER"},
                "campx_app_server");
        config.setUsername(user);

        String pass = resolve(props,
                new String[]{"campx.db.password", "db.password", "database.password", "supabase.db.password"},
                new String[]{"CAMPX_DB_PASSWORD", "DATABASE_PASSWORD", "DB_PASSWORD", "SUPABASE_DB_PASSWORD", "SUPABASE_PASSWORD"},
                "");
        config.setPassword(pass);

        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(10);
        config.setMinimumIdle(2);
        config.setConnectionTimeoutMs(30000L);
        config.setIdleTimeoutMs(600000L);
        config.setMaxLifetimeMs(1800000L);
        config.setPoolName("CampXSync-ACD10-Pool");

        return config;
    }

    private static void loadProperties(Properties props, String path) {
        if (path == null || path.trim().isEmpty()) {
            return;
        }
        File f = new File(path);
        if (f.exists() && f.isFile()) {
            try (InputStream in = new FileInputStream(f)) {
                props.load(in);
                return;
            } catch (Exception ignored) {}
        }
        try (InputStream in = DatabaseConfig.class.getClassLoader().getResourceAsStream(path)) {
            if (in != null) {
                props.load(in);
            }
        } catch (Exception ignored) {}
    }

    private static String resolve(Properties props, String[] sysAndPropKeys, String[] envKeys, String fallback) {
        for (String key : sysAndPropKeys) {
            String val = System.getProperty(key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        for (String key : envKeys) {
            String val = System.getenv(key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        for (String key : sysAndPropKeys) {
            String val = props.getProperty(key);
            if (val != null && !val.trim().isEmpty()) {
                return val.trim();
            }
        }
        return fallback;
    }

    private static String normalizeJdbcUrl(String raw) {
        if (raw == null) return "jdbc:postgresql://localhost:5432/postgres";
        String trimmed = raw.trim();
        if (trimmed.startsWith("postgresql://")) {
            return "jdbc:" + trimmed;
        }
        if (trimmed.startsWith("postgres://")) {
            return "jdbc:postgresql://" + trimmed.substring("postgres://".length());
        }
        return trimmed;
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
}
