package com.campx.academic.timetable.config;

import java.net.URI;
import java.net.URISyntaxException;

import static org.junit.Assert.fail;
import static org.junit.Assume.assumeNotNull;

/**
 * Test environment safety guard for ACD-05 live PostgreSQL integration tests.
 * <p>
 * Enforces strict staging/development validation for Supabase connections:
 * 1. Requires explicitly designated test environment (e.g. CAMPX_TEST_ENV=development or staging).
 * 2. Blocks execution if configured environment is 'production' or 'prod'.
 * 3. Requires exact approved development project reference 'azulbaxszsqxymqmlwfe'.
 * 4. Strictly forbids production project reference 'ekspahbsbwdpraaqfqui'.
 * 5. Strictly forbids legacy staging reference 'kpwdvvcschankgggitnj'.
 * 6. Strictly rejects localhost, loopback addresses, missing, malformed, or ambiguous configurations.
 */
public final class TestEnvironmentGuard {

    public static final String APPROVED_DEV_PROJECT_REF = "azulbaxszsqxymqmlwfe";
    public static final String STAGING_PROJECT_REF = APPROVED_DEV_PROJECT_REF;
    public static final String PRODUCTION_PROJECT_REF = "ekspahbsbwdpraaqfqui";
    public static final String PROHIBITED_LEGACY_STAGING_REF = "kpwdvvcschankgggitnj";

    private TestEnvironmentGuard() {}

    public static void validateStagingConfiguration(String jdbcUrl, String username) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException("Safe development validation failed: Missing JDBC URL for live integration tests. Configure SUPABASE_JDBC_URL or CAMPX_DB_URL.");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException("Safe development validation failed: Missing database username for live integration tests.");
        }

        String trimmedUrl = jdbcUrl.trim();
        String trimmedUser = username.trim();

        if (trimmedUrl.contains(PRODUCTION_PROJECT_REF) || trimmedUser.contains(PRODUCTION_PROJECT_REF)) {
            throw new IllegalStateException("Security violation: Live integration tests cannot connect to production project ref '" + PRODUCTION_PROJECT_REF + "'");
        }

        if (trimmedUrl.contains(PROHIBITED_LEGACY_STAGING_REF) || trimmedUser.contains(PROHIBITED_LEGACY_STAGING_REF)) {
            throw new IllegalStateException("Security violation: Live integration tests cannot connect to prohibited legacy staging ref '" + PROHIBITED_LEGACY_STAGING_REF + "'");
        }

        if (!trimmedUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException("Safe development validation failed: Malformed PostgreSQL JDBC URL. Expected 'jdbc:postgresql://' prefix.");
        }

        String host;
        try {
            String cleanUrl = trimmedUrl.substring(5);
            URI uri = new URI(cleanUrl);
            host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException("Safe development validation failed: Unable to extract host from JDBC URL.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException("Safe development validation failed: Malformed PostgreSQL JDBC URL syntax: " + e.getReason());
        }

        String lowerHost = host.toLowerCase();

        if ("localhost".equals(lowerHost) ||
            "127.0.0.1".equals(lowerHost) ||
            "::1".equals(lowerHost) ||
            "[::1]".equals(lowerHost) ||
            "0.0.0.0".equals(lowerHost) ||
            lowerHost.startsWith("127.") ||
            lowerHost.endsWith(".localhost")) {
            throw new IllegalStateException("Safe development validation failed: Live integration tests cannot connect to localhost or loopback defaults: " + host);
        }

        if (lowerHost.endsWith(".supabase.co")) {
            String prefix = lowerHost.substring(0, lowerHost.length() - ".supabase.co".length());
            if (prefix.startsWith("db.")) {
                prefix = prefix.substring(3);
            }
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException("Safe development validation failed: Unauthorized project ref in host: '" + prefix + "'. Only designated development project '" + APPROVED_DEV_PROJECT_REF + "' is permitted.");
            }
            return;
        }

        if (lowerHost.endsWith(".pooler.supabase.com") || lowerHost.endsWith(".pooler.supabase.co") ||
            "pooler.supabase.com".equals(lowerHost) || "pooler.supabase.co".equals(lowerHost)) {
            int dotIdx = trimmedUser.lastIndexOf('.');
            if (dotIdx <= 0 || dotIdx == trimmedUser.length() - 1) {
                throw new IllegalStateException("Safe development validation failed: Supabase pooler connections require username formatted as '<role>.<project-ref>'. Found: '" + trimmedUser + "'");
            }
            String userProjectRef = trimmedUser.substring(dotIdx + 1);
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException("Safe development validation failed: Unauthorized project ref in pooler username: '" + userProjectRef + "'. Only designated development project '" + APPROVED_DEV_PROJECT_REF + "' is permitted.");
            }
            return;
        }

        throw new IllegalStateException("Safe development validation failed: Unrecognized host for live integration tests: '" + host + "'.");
    }

    public static void verifySafeStagingEnvironment() {
        String testEnv = System.getProperty("campx.test.env");
        if (testEnv == null || testEnv.trim().isEmpty()) {
            testEnv = System.getenv("CAMPX_TEST_ENV");
        }
        assumeNotNull("Live integration tests require an explicitly designated test environment via CAMPX_TEST_ENV or campx.test.env", testEnv);

        String trimmedEnv = testEnv.trim();
        if ("production".equalsIgnoreCase(trimmedEnv) || "prod".equalsIgnoreCase(trimmedEnv)) {
            fail("Live integration tests cannot run against an explicitly configured production environment: " + trimmedEnv);
        }
    }
}
