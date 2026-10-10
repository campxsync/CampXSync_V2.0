package com.campx.admin.college.config;

/**
 * Configuration bean managing database connection properties and connection pool sizing
 * for ADM-02: College Admin Service.
 */
public class DatabaseConfig {

    private String jdbcUrl;
    private String username;
    private String password;
    private int maximumPoolSize = 10;
    private int minimumIdle = 2;
    private long connectionTimeoutMs = 30000;
    private long idleTimeoutMs = 600000;
    private long maxLifetimeMs = 1800000;
    private String poolName = "CampX-CollegeAdmin-Pool";

    public static DatabaseConfig load() {
        DatabaseConfig config = new DatabaseConfig();

        String url = System.getProperty("campx.jdbc.url");
        if (url == null || url.trim().isEmpty()) {
            url = System.getenv("SUPABASE_JDBC_URL");
        }
        if (url == null || url.trim().isEmpty()) {
            url = System.getenv("CAMPX_JDBC_URL");
        }
        config.setJdbcUrl(url);

        String user = System.getProperty("campx.db.username");
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("SUPABASE_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("CAMPX_DB_USER");
        }
        config.setUsername(user);

        String pass = System.getProperty("campx.db.password");
        if (pass == null || pass.trim().isEmpty()) {
            pass = System.getenv("SUPABASE_PASSWORD");
        }
        if (pass == null || pass.trim().isEmpty()) {
            pass = System.getenv("CAMPX_DB_PASSWORD");
        }
        config.setPassword(pass);

        return config;
    }

    public String getJdbcUrl() { return jdbcUrl; }
    public void setJdbcUrl(String jdbcUrl) { this.jdbcUrl = jdbcUrl; }

    public String getUsername() { return username; }
    public void setUsername(String username) { this.username = username; }

    public String getPassword() { return password; }
    public void setPassword(String password) { this.password = password; }

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
