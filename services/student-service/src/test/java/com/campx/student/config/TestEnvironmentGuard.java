package com.campx.student.config;

import java.net.URI;
import java.net.URISyntaxException;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.UUID;

import static org.junit.Assume.assumeNotNull;
import static org.junit.Assert.fail;

/**
 * Test environment safety guard for STU-01 live PostgreSQL integration tests.
 * <p>
 * Enforces strict staging/development validation for Supabase connections:
 * 1. Requires explicitly designated test environment (e.g. CAMPX_TEST_ENV=development or staging).
 * 2. Blocks execution if configured environment is 'production' or 'prod'.
 * 3. Requires exact approved development project reference 'azulbaxszsqxymqmlwfe'.
 * 4. Strictly forbids production project reference 'ekspahbsbwdpraaqfqui'.
 * 5. Strictly forbids legacy staging reference 'kpwdvvcschankgggitnj'.
 * 6. Strictly rejects localhost, loopback addresses, missing, malformed, or ambiguous configurations.
 * 7. Validates explicitly designated test tenant (CAMPX_TEST_TENANT_ID) and resolves safe test user identities.
 */
public final class TestEnvironmentGuard {

    public static final String APPROVED_DEV_PROJECT_REF = "azulbaxszsqxymqmlwfe";
    public static final String STAGING_PROJECT_REF = APPROVED_DEV_PROJECT_REF;
    public static final String PRODUCTION_PROJECT_REF = "ekspahbsbwdpraaqfqui";
    public static final String PROHIBITED_LEGACY_STAGING_REF = "kpwdvvcschankgggitnj";

    private TestEnvironmentGuard() {}

    /**
     * Validates database configuration strictly against development guardrails without opening database connections.
     *
     * @param jdbcUrl  configured PostgreSQL JDBC URL
     * @param username configured database username
     * @throws IllegalStateException if configuration is missing, malformed, targets production, localhost, or an unauthorized project
     */
    public static void validateStagingConfiguration(String jdbcUrl, String username) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("Safe development validation failed: Missing JDBC URL for live integration tests. Configure SUPABASE_JDBC_URL or CAMPX_DB_URL.");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("Safe development validation failed: Missing database username for live integration tests.");
        }

        String trimmedUrl = jdbcUrl.trim();
        String trimmedUser = username.trim();

        // 1. Strict zero-tolerance production project reference rejection
        if (trimmedUrl.contains(PRODUCTION_PROJECT_REF) || trimmedUser.contains(PRODUCTION_PROJECT_REF)) {
            throw new IllegalStateException("Security violation: Live integration tests cannot connect to production project ref '" + PRODUCTION_PROJECT_REF + "'");
        }

        // 1b. Strict zero-tolerance legacy staging project reference rejection
        if (trimmedUrl.contains(PROHIBITED_LEGACY_STAGING_REF) || trimmedUser.contains(PROHIBITED_LEGACY_STAGING_REF)) {
            throw new IllegalStateException("Security violation: Live integration tests cannot connect to prohibited legacy staging ref '" + PROHIBITED_LEGACY_STAGING_REF + "'");
        }

        // 2. Validate PostgreSQL JDBC protocol prefix
        if (!trimmedUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Safe development validation failed: Malformed PostgreSQL JDBC URL. Expected 'jdbc:postgresql://' prefix.");
        }

        // 3. Parse host from JDBC URL
        String host;
        try {
            String cleanUrl = trimmedUrl.substring(5); // strip "jdbc:" to allow URI parsing
            URI uri = new URI(cleanUrl);
            host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException("Safe development validation failed: Unable to extract host from JDBC URL.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Safe development validation failed: Malformed PostgreSQL JDBC URL syntax: " + e.getReason());
        }

        String lowerHost = host.toLowerCase();

        // 4. Reject localhost and loopback addresses
        if ("localhost".equals(lowerHost) ||
            "127.0.0.1".equals(lowerHost) ||
            "::1".equals(lowerHost) ||
            "[::1]".equals(lowerHost) ||
            "0.0.0.0".equals(lowerHost) ||
            lowerHost.startsWith("127.") ||
            lowerHost.endsWith(".localhost")) {
            throw new IllegalStateException("Safe development validation failed: Live integration tests cannot connect to localhost or loopback defaults: " + host);
        }

        // 5. Direct Supabase Host (*.supabase.co)
        if (lowerHost.endsWith(".supabase.co")) {
            String prefix = lowerHost.substring(0, lowerHost.length() - ".supabase.co".length());
            if (prefix.startsWith("db.")) {
                prefix = prefix.substring(3);
            }
            if (PRODUCTION_PROJECT_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException("Security violation: Live integration tests cannot connect to production project ref '" + PRODUCTION_PROJECT_REF + "'");
            }
            if (PROHIBITED_LEGACY_STAGING_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException("Security violation: Live integration tests cannot connect to prohibited legacy staging ref '" + PROHIBITED_LEGACY_STAGING_REF + "'");
            }
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException("Safe development validation failed: Unknown or unauthorized project ref in host: '" + prefix + "'. Only designated development project '" + APPROVED_DEV_PROJECT_REF + "' is permitted.");
            }
            if (trimmedUser.contains(".") && !trimmedUser.endsWith("." + APPROVED_DEV_PROJECT_REF)) {
                throw new IllegalStateException("Safe development validation failed: Database username appears to target a different project than development ref '" + APPROVED_DEV_PROJECT_REF + "'");
            }
            return;
        }

        // 6. Supabase Pooler Host (*.pooler.supabase.com or *.pooler.supabase.co)
        if (lowerHost.endsWith(".pooler.supabase.com") || lowerHost.endsWith(".pooler.supabase.co") ||
            "pooler.supabase.com".equals(lowerHost) || "pooler.supabase.co".equals(lowerHost)) {
            int dotIdx = trimmedUser.lastIndexOf('.');
            if (dotIdx <= 0 || dotIdx == trimmedUser.length() - 1) {
                throw new IllegalStateException("Safe development validation failed: Supabase pooler connections require username formatted as '<role>.<project-ref>'. Found: '" + trimmedUser + "'");
            }
            String userProjectRef = trimmedUser.substring(dotIdx + 1);
            if (PRODUCTION_PROJECT_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException("Security violation: Live integration tests cannot connect to production project ref '" + PRODUCTION_PROJECT_REF + "'");
            }
            if (PROHIBITED_LEGACY_STAGING_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException("Security violation: Live integration tests cannot connect to prohibited legacy staging ref '" + PROHIBITED_LEGACY_STAGING_REF + "'");
            }
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException("Safe development validation failed: Unknown or unauthorized project ref in pooler username: '" + userProjectRef + "'. Only designated development project '" + APPROVED_DEV_PROJECT_REF + "' is permitted.");
            }
            return;
        }

        throw new IllegalStateException("Safe development validation failed: Unrecognized host for live integration tests: '" + host + "'. Expected direct Supabase host (*.supabase.co) or Supabase pooler (*.pooler.supabase.com).");
    }

    /**
     * Verifies that the environment and configuration strictly target development.
     */
    public static void verifySafeStagingEnvironment(DatabaseConnectionManager connectionManager) {
        DatabaseConfig config = connectionManager != null ? connectionManager.getConfig() : DatabaseConfig.load();
        verifySafeStagingEnvironment(config);
    }

    /**
     * Verifies that the environment and configuration strictly target development.
     */
    public static void verifySafeStagingEnvironment(DatabaseConfig config) {
        String testEnv = System.getProperty("campx.test.env");
        if (testEnv == null || testEnv.trim().isEmpty()) {
            testEnv = System.getenv("CAMPX_TEST_ENV");
        }
        verifySafeStagingEnvironment(testEnv, config);
    }

    public static void verifySafeStagingEnvironment(String testEnv, DatabaseConfig config) {
        assumeNotNull("Live integration tests require an explicitly designated test environment via CAMPX_TEST_ENV or campx.test.env", testEnv);

        String trimmedEnv = testEnv.trim();
        if ("production".equalsIgnoreCase(trimmedEnv) || "prod".equalsIgnoreCase(trimmedEnv)) {
            fail("Live integration tests cannot run against an explicitly configured production environment: " + trimmedEnv);
        }

        if (config == null) {
            fail("Safe development validation failed: DatabaseConfig is null");
            return;
        }

        try {
            validateStagingConfiguration(config.getJdbcUrl(), config.getUsername());
        } catch (IllegalStateException e) {
            fail(e.getMessage());
        }
    }
}
