package com.campx.lifecycle.config;

public class DatabaseConfig {

    private static final String APPROVED_DEV_REF = "azulbaxszsqxymqmlwfe";

    private String jdbcUrl;
    private String username;
    private String password;
    private int maxPoolSize = 10;
    private int minIdle = 2;

    public DatabaseConfig() {
        String url = System.getenv("CAMPX_DB_URL");
        if (url == null || url.trim().isEmpty()) {
            url = System.getenv("SUPABASE_JDBC_URL");
        }
        if (url != null && url.contains("ap-southeast-1")) {
            url = url.replace("ap-southeast-1", "ap-south-1");
        }
        if (url != null && (url.contains("pooler.supabase.com") || url.contains("pooler.supabase.co"))) {
            if (!url.contains("prepareThreshold")) {
                url = url.contains("?") ? url + "&prepareThreshold=0" : url + "?prepareThreshold=0";
            }
        }
        this.jdbcUrl = url;

        String user = System.getenv("CAMPX_DB_USERNAME");
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("CAMPX_DB_USER");
        }
        if (user == null || user.trim().isEmpty()) {
            if (url != null && (url.contains(".pooler.supabase.com") || url.contains(".pooler.supabase.co"))) {
                user = "campx_app_server." + APPROVED_DEV_REF;
            } else {
                user = "campx_app_server";
            }
        }
        this.username = user;

        String pass = System.getenv("CAMPX_DB_PASSWORD");
        if (pass == null || pass.trim().isEmpty()) {
            pass = System.getenv("SUPABASE_PASSWORD");
        }
        this.password = pass;
    }

    public boolean isConfigured() {
        return jdbcUrl != null && !jdbcUrl.trim().isEmpty()
                && password != null && !password.trim().isEmpty();
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

    public int getMaxPoolSize() {
        return maxPoolSize;
    }

    public int getMinIdle() {
        return minIdle;
    }
}
