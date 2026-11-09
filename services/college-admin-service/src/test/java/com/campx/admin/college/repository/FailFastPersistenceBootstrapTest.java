package com.campx.admin.college.repository;

import com.campx.admin.college.config.DatabaseConfig;
import com.campx.admin.college.config.DatabaseConnectionManager;
import com.campx.admin.college.service.CollegeAdminDomainService;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Regression test for fail-fast persistence bootstrapping.
 * Verifies that when {@code campx.persistence.mode=postgres} is specified,
 * any database configuration or connectivity failure results in an immediate {@link IllegalStateException}
 * rather than silently failing open or falling back to in-memory mode.
 */
public class FailFastPersistenceBootstrapTest {

    private String originalMode;
    private String originalSysUrl;
    private String originalEnvUrl;

    @Before
    public void setUp() {
        originalMode = System.getProperty("campx.persistence.mode");
        originalSysUrl = System.getProperty("campx.db.url");
        originalEnvUrl = System.getProperty("SUPABASE_JDBC_URL");
        DatabaseConnectionManager.resetInstance();
    }

    @After
    public void tearDown() {
        if (originalMode != null) {
            System.setProperty("campx.persistence.mode", originalMode);
        } else {
            System.clearProperty("campx.persistence.mode");
        }

        if (originalSysUrl != null) {
            System.setProperty("campx.db.url", originalSysUrl);
        } else {
            System.clearProperty("campx.db.url");
        }

        if (originalEnvUrl != null) {
            System.setProperty("SUPABASE_JDBC_URL", originalEnvUrl);
        } else {
            System.clearProperty("SUPABASE_JDBC_URL");
        }

        DatabaseConnectionManager.resetInstance();
    }

    @Test(expected = IllegalStateException.class)
    public void testFailFastWhenDatabaseMisconfiguredInPostgresMode() {
        System.setProperty("campx.persistence.mode", "postgres");
        // Point to unreachable port with tiny timeout
        DatabaseConfig badConfig = new DatabaseConfig();
        badConfig.setJdbcUrl("jdbc:postgresql://127.0.0.1:1/invalid_db");
        badConfig.setConnectionTimeoutMs(500);

        DatabaseConnectionManager badManager = new DatabaseConnectionManager(badConfig);

        // When initializing connection manager with bad config, it must fail fast
        badManager.initialize();
    }

    @Test
    public void testDomainServiceFailsFastWhenPostgresUrlMissing() {
        System.setProperty("campx.persistence.mode", "postgres");
        System.clearProperty("campx.db.url");
        System.clearProperty("SUPABASE_JDBC_URL");

        DatabaseConfig emptyConfig = new DatabaseConfig();
        emptyConfig.setJdbcUrl("");
        DatabaseConnectionManager manager = new DatabaseConnectionManager(emptyConfig);

        try {
            manager.initialize();
            fail("Expected IllegalStateException due to missing JDBC URL");
        } catch (IllegalStateException e) {
            assertTrue(e.getMessage().contains("Database JDBC URL is not configured"));
        }
    }
}
