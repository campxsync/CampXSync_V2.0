package com.campx.academic.timetable.config;

import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.Properties;

/**
 * Configuration holder and loader for PostgreSQL and HikariCP connection pooling
 * in ACD-05: Timetable Management Service.
 * <p>
 * Follows the CampXSync configuration resolution hierarchy with strict precedence:
 * <ol>
 *   <li><b>Java System Properties</b> (e.g. {@code -Dcampx.db.url=...} or {@code -Ddb.url=...})</li>
 *   <li><b>OS Environment Variables</b> (e.g. {@code CAMPX_DB_URL}, {@code DATABASE_URL}, {@code CAMPX_DB_PASSWORD})</li>
 *   <li><b>Properties File</b> (file system or classpath {@code campx-database.properties} or {@code application.properties})</li>
 *   <li><b>Internal Defaults</b> (unprivileged {@code campx_app_server} default user; zero credentials hard-coded)</li>
 * </ol>
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
    private String poolName = "CampXSync-ACD05-Pool";

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
                new String[]{"campx.db.username", "db.username", "database.username", "supabase.user", "supabase.db.user"},
                new String[]{"CAMPX_DB_USERNAME", "DB_USERNAME", "DB_USER", "SUPABASE_DB_USER", "SUPABASE_USER"},
                "campx_app_server");
        config.setUsername(user);

        String pass = resolve(props,
                new String[]{"campx.db.password", "db.password", "database.password", "supabase.password", "supabase.db.password"},
                new String[]{"CAMPX_DB_PASSWORD", "DB_PASSWORD", "SUPABASE_DB_PASSWORD", "SUPABASE_PASSWORD"},
                "");
        config.setPassword(pass);

        config.setDriverClassName(resolve(props,
                new String[]{"campx.db.driver", "db.driver"},
                new String[]{"CAMPX_DB_DRIVER", "DB_DRIVER"},
                "org.postgresql.Driver"));

        return config;
    }

    private static String normalizeJdbcUrl(String url) {
        if (url == null) return null;
        if (url.contains("ap-southeast-1")) {
            return url.replace("ap-southeast-1", "ap-south-1");
        }
        return url;
    }

    private static void loadProperties(Properties props, String path) {
        if (path == null || path.trim().isEmpty()) return;

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

    private static String resolve(Properties props, String[] propKeys, String[] envKeys, String defaultValue) {
        for (String k : propKeys) {
            String val = System.getProperty(k);
            if (val != null && !val.trim().isEmpty()) return val.trim();
        }

        for (String k : envKeys) {
            String val = System.getenv(k);
            if (val != null && !val.trim().isEmpty()) return val.trim();
        }

        if (props != null) {
            for (String k : propKeys) {
                String val = props.getProperty(k);
                if (val != null && !val.trim().isEmpty()) return val.trim();
            }
        }

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
}
