package com.campx.guardian.config;

/**
 * JDBC settings for the approved development database. Production and previous staging refs are refused.
 */
public class DatabaseConfig {

    private static final String APPROVED_DEV_REF = "azulbaxszsqxymqmlwfe";
    private static final String PRODUCTION_REF = "ekspahbsbwdpraaqfqui";
    private static final String OLD_STAGING_REF = "kpwdvvcschankgggitnj";

    private final String jdbcUrl;
    private final String username;
    private final String password;

    public DatabaseConfig() {
        String url = System.getenv("CAMPX_DB_URL");
        if (url == null || url.trim().isEmpty()) {
            url = System.getenv("SUPABASE_JDBC_URL");
        }
        if (url != null && (url.contains(PRODUCTION_REF) || url.contains(OLD_STAGING_REF))) {
            throw new IllegalStateException("Guardian service refuses a production or previous staging database");
        }
        if (url != null && url.contains("ap-southeast-1")) {
            url = url.replace("ap-southeast-1", "ap-south-1");
        }
        if (url != null && (url.contains("pooler.supabase.com") || url.contains("pooler.supabase.co"))
                && !url.contains("prepareThreshold")) {
            url = url.contains("?") ? url + "&prepareThreshold=0" : url + "?prepareThreshold=0";
        }
        this.jdbcUrl = url;
        String user = System.getenv("CAMPX_DB_USERNAME");
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("CAMPX_DB_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            user = url != null && (url.contains(".pooler.supabase.com") || url.contains(".pooler.supabase.co"))
                    ? "campx_app_server." + APPROVED_DEV_REF : "campx_app_server";
        }
        this.username = user;
        String pass = System.getenv("CAMPX_DB_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) {
            pass = System.getenv("SUPABASE_PASSWORD");
        }
        this.password = pass;
    }

    public boolean isConfigured() {
        return jdbcUrl != null && !jdbcUrl.trim().isEmpty() && password != null && !password.trim().isEmpty();
    }

    public String getJdbcUrl() {
        return jdbcUrl;
    }

    public String getUsername() {
        return username;
    }

    public String getPassword() {
        return password;
    }
}
