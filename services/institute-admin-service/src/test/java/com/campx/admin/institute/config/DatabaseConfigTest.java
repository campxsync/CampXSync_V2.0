package com.campx.admin.institute.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests verifying DatabaseConfig property resolution and DatabaseConnectionManager lifecycle.
 */
public class DatabaseConfigTest {

    @Before
    @After
    public void cleanupProperties() {
        System.clearProperty("campx.db.url");
        System.clearProperty("db.url");
        System.clearProperty("campx.db.username");
        System.clearProperty("db.username");
        System.clearProperty("campx.db.password");
        System.clearProperty("db.password");
        System.clearProperty("campx.db.pool.max-size");
        System.clearProperty("campx.db.pool.min-idle");
        System.clearProperty("campx.db.pool.name");
        DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void testDefaultConfigResolution() {
        DatabaseConfig config = DatabaseConfig.load();
        assertNotNull(config);
        assertNotNull(config.getJdbcUrl());
        assertTrue(config.getJdbcUrl().startsWith("jdbc:postgresql://"));
        assertTrue(config.getUsername().startsWith("postgres"));
        assertNotNull(config.getPassword());
        assertEquals("org.postgresql.Driver", config.getDriverClassName());
        assertEquals(10, config.getMaximumPoolSize());
        assertEquals(2, config.getMinimumIdle());
        assertEquals(30000L, config.getConnectionTimeoutMs());
        assertEquals("CampXSync-ADM01-Pool", config.getPoolName());
    }

    @Test
    public void testSystemPropertyOverrides() {
        System.setProperty("campx.db.url", "jdbc:postgresql://supabase.custom.host:6543/postgres?sslmode=require");
        System.setProperty("campx.db.username", "custom_user");
        System.setProperty("campx.db.password", "secret_pass");
        System.setProperty("campx.db.pool.max-size", "25");
        System.setProperty("campx.db.pool.min-idle", "5");
        System.setProperty("campx.db.pool.name", "TestCustomPool");

        DatabaseConfig config = DatabaseConfig.load();
        assertEquals("jdbc:postgresql://supabase.custom.host:6543/postgres?sslmode=require", config.getJdbcUrl());
        assertEquals("custom_user", config.getUsername());
        assertEquals("secret_pass", config.getPassword());
        assertEquals(25, config.getMaximumPoolSize());
        assertEquals(5, config.getMinimumIdle());
        assertEquals("TestCustomPool", config.getPoolName());
    }

    @Test
    public void testMaskedStringAndSanitizedUrl() {
        DatabaseConfig config = new DatabaseConfig();
        config.setJdbcUrl("jdbc:postgresql://admin:secret123@aws-0.supabase.com:5432/postgres");
        config.setPassword("super_secret");

        String str = config.toString();
        assertFalse(str.contains("super_secret"));
        assertTrue(str.contains("password='***'"));

        String sanitizedUrl = config.getSanitizedJdbcUrl();
        assertFalse(sanitizedUrl.contains("secret123"));
        assertTrue(sanitizedUrl.contains("admin:***@aws-0.supabase.com"));
    }

    @Test
    public void testManagerLifecycle() {
        DatabaseConfig config = new DatabaseConfig();
        config.setJdbcUrl("jdbc:postgresql://invalid.dummy.host:5432/db");
        config.setPoolName("LifecycleTestPool");

        DatabaseConnectionManager manager = new DatabaseConnectionManager(config);
        assertFalse(manager.isInitialized());
        assertFalse(manager.isClosed());
        assertEquals("LifecycleTestPool", manager.getConfig().getPoolName());

        manager.close();
        assertTrue(manager.isClosed());
        assertFalse(manager.isInitialized());
    }
}
