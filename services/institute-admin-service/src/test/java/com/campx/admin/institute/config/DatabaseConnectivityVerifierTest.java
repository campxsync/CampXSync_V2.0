package com.campx.admin.institute.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Unit tests verifying DatabaseConnectivityVerifier behavior and resource cleanup.
 */
public class DatabaseConnectivityVerifierTest {

    @Before
    @After
    public void cleanup() {
        System.clearProperty("campx.db.url");
        System.clearProperty("db.url");
        System.clearProperty("campx.db.username");
        System.clearProperty("campx.db.password");
        DatabaseConnectionManager.resetInstance();
    }

    @Test
    public void testNullConfigFailsGracefully() {
        DatabaseConnectivityVerifier.VerificationResult result = DatabaseConnectivityVerifier.verify(null);
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getMessage());
        assertNotNull(result.getError());
    }

    @Test
    public void testUnreachableHostFailsCleanlyAndClosesDatasource() {
        DatabaseConfig config = new DatabaseConfig();
        config.setJdbcUrl("jdbc:postgresql://127.0.0.1:59999/dummy_db");
        config.setConnectionTimeoutMs(1000L); // fast fail for test
        config.setPoolName("TestFailPool");

        DatabaseConnectivityVerifier.VerificationResult result = DatabaseConnectivityVerifier.verify(config);
        assertNotNull(result);
        assertFalse(result.isSuccess());
        assertNotNull(result.getSanitizedJdbcUrl());
        assertTrue(result.isDatasourceClosed());
        assertNotNull(result.getError());
    }
}
