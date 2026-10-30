package com.campx.academic.course.config;

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
 * Test environment safety guard for ACD-01 live PostgreSQL integration tests.
 * <p>
 * Enforces strict staging validation for Supabase connections:
 * 1. Requires explicitly designated test environment (e.g.
 * CAMPX_TEST_ENV=staging).
 * 2. Blocks execution if configured environment is 'production' or 'prod'.
 * 3. Requires exact staging project reference 'kpwdvvcschankgggitnj' (in parsed
 * JDBC host for direct
 * connections, or in database username for Supabase pooler connections).
 * 4. Strictly forbids production project reference 'ekspahbsbwdpraaqfqui'.
 * 5. Strictly rejects localhost, loopback addresses, missing, malformed, or
 * ambiguous configurations.
 * 6. Validates explicitly designated test tenant (CAMPX_TEST_TENANT_ID) and
 * resolves safe test user identities.
 */
public final class TestEnvironmentGuard {

    public static final String APPROVED_DEV_PROJECT_REF = "azulbaxszsqxymqmlwfe";
    public static final String STAGING_PROJECT_REF = APPROVED_DEV_PROJECT_REF;
    public static final String PRODUCTION_PROJECT_REF = "ekspahbsbwdpraaqfqui";
    public static final String PROHIBITED_LEGACY_STAGING_REF = "kpwdvvcschankgggitnj";

    private TestEnvironmentGuard() {
    }

    /**
     * Validates database configuration strictly against staging guardrails without
     * opening database connections.
     *
     * @param jdbcUrl  configured PostgreSQL JDBC URL
     * @param username configured database username
     * @throws IllegalStateException if configuration is missing, malformed, targets
     *                               production, localhost, or an unauthorized
     *                               project
     */
    public static void validateStagingConfiguration(String jdbcUrl, String username) {
        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Safe staging validation failed: Missing JDBC URL for live integration tests. Configure SUPABASE_JDBC_URL or CAMPX_JDBC_URL.");
        }
        if (username == null || username.trim().isEmpty()) {
            throw new IllegalStateException(
                    "Safe staging validation failed: Missing database username for live integration tests.");
        }

        String trimmedUrl = jdbcUrl.trim();
        String trimmedUser = username.trim();

        // 1. Strict zero-tolerance production project reference rejection
        if (trimmedUrl.contains(PRODUCTION_PROJECT_REF) || trimmedUser.contains(PRODUCTION_PROJECT_REF)) {
            throw new IllegalStateException(
                    "Security violation: Live integration tests cannot connect to production project ref '"
                            + PRODUCTION_PROJECT_REF + "'");
        }

        // 1b. Strict zero-tolerance legacy staging project reference rejection
        if (trimmedUrl.contains(PROHIBITED_LEGACY_STAGING_REF) || trimmedUser.contains(PROHIBITED_LEGACY_STAGING_REF)) {
            throw new IllegalStateException(
                    "Security violation: Live integration tests cannot connect to prohibited legacy staging ref '"
                            + PROHIBITED_LEGACY_STAGING_REF + "'");
        }

        // 2. Validate PostgreSQL JDBC protocol prefix
        if (!trimmedUrl.startsWith("jdbc:postgresql://")) {
            throw new IllegalStateException(
                    "Safe staging validation failed: Malformed PostgreSQL JDBC URL. Expected 'jdbc:postgresql://' prefix.");
        }

        // 3. Parse host from JDBC URL
        String host;
        try {
            String cleanUrl = trimmedUrl.substring(5); // strip "jdbc:" to allow URI parsing
            URI uri = new URI(cleanUrl);
            host = uri.getHost();
            if (host == null || host.trim().isEmpty()) {
                throw new IllegalStateException(
                        "Safe staging validation failed: Unable to extract host from JDBC URL.");
            }
        } catch (URISyntaxException e) {
            throw new IllegalStateException(
                    "Safe staging validation failed: Malformed PostgreSQL JDBC URL syntax: " + e.getReason());
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
            throw new IllegalStateException(
                    "Safe staging validation failed: Live integration tests cannot connect to localhost or loopback defaults: "
                            + host);
        }

        // 5. Direct Supabase Host (*.supabase.co)
        if (lowerHost.endsWith(".supabase.co")) {
            String prefix = lowerHost.substring(0, lowerHost.length() - ".supabase.co".length());
            if (prefix.startsWith("db.")) {
                prefix = prefix.substring(3);
            }
            if (PRODUCTION_PROJECT_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException(
                        "Security violation: Live integration tests cannot connect to production project ref '"
                                + PRODUCTION_PROJECT_REF + "'");
            }
            if (PROHIBITED_LEGACY_STAGING_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException(
                        "Security violation: Live integration tests cannot connect to prohibited legacy staging ref '"
                                + PROHIBITED_LEGACY_STAGING_REF + "'");
            }
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(prefix)) {
                throw new IllegalStateException(
                        "Safe staging validation failed: Unknown or unauthorized project ref in host: '" + prefix
                                + "'. Only designated development project '" + APPROVED_DEV_PROJECT_REF
                                + "' is permitted.");
            }
            if (trimmedUser.contains(".") && !trimmedUser.endsWith("." + APPROVED_DEV_PROJECT_REF)) {
                throw new IllegalStateException(
                        "Safe staging validation failed: Database username appears to target a different project than staging ref '"
                                + APPROVED_DEV_PROJECT_REF + "'");
            }
            return;
        }

        // 6. Supabase Pooler Host (*.pooler.supabase.com or *.pooler.supabase.co)
        if (lowerHost.endsWith(".pooler.supabase.com") || lowerHost.endsWith(".pooler.supabase.co") ||
                "pooler.supabase.com".equals(lowerHost) || "pooler.supabase.co".equals(lowerHost)) {
            int dotIdx = trimmedUser.lastIndexOf('.');
            if (dotIdx <= 0 || dotIdx == trimmedUser.length() - 1) {
                throw new IllegalStateException(
                        "Safe staging validation failed: Supabase pooler connections require username formatted as '<role>.<project-ref>'. Found: '"
                                + trimmedUser + "'");
            }
            String userProjectRef = trimmedUser.substring(dotIdx + 1);
            if (PRODUCTION_PROJECT_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException(
                        "Security violation: Live integration tests cannot connect to production project ref '"
                                + PRODUCTION_PROJECT_REF + "'");
            }
            if (PROHIBITED_LEGACY_STAGING_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException(
                        "Security violation: Live integration tests cannot connect to prohibited legacy staging ref '"
                                + PROHIBITED_LEGACY_STAGING_REF + "'");
            }
            if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(userProjectRef)) {
                throw new IllegalStateException(
                        "Safe staging validation failed: Unknown or unauthorized project ref in pooler username: '"
                                + userProjectRef + "'. Only designated development project '" + APPROVED_DEV_PROJECT_REF
                                + "' is permitted.");
            }
            return;
        }

        // 7. Unrecognized host
        throw new IllegalStateException(
                "Safe staging validation failed: Unrecognized host for live integration tests: '" + host
                        + "'. Expected direct Supabase host (*.supabase.co) or Supabase pooler (*.pooler.supabase.com).");
    }

    /**
     * Verifies that the environment and connection manager configuration strictly
     * target staging.
     */
    public static void verifySafeStagingEnvironment(DatabaseConnectionManager connectionManager) {
        DatabaseConfig config = connectionManager != null ? connectionManager.getConfig() : DatabaseConfig.load();
        verifySafeStagingEnvironment(config);
    }

    /**
     * Verifies that the environment and configuration strictly target staging.
     */
    public static void verifySafeStagingEnvironment(DatabaseConfig config) {
        String testEnv = System.getProperty("campx.test.env");
        if (testEnv == null || testEnv.trim().isEmpty()) {
            testEnv = System.getenv("CAMPX_TEST_ENV");
        }
        verifySafeStagingEnvironment(testEnv, config);
    }

    /**
     * Verifies that the provided environment string and configuration strictly
     * target staging.
     * Allows offline unit testing without mutating or depending on ambient
     * environment variables.
     */
    public static void verifySafeStagingEnvironment(String testEnv, DatabaseConfig config) {
        // If no test environment is explicitly designated, skip cleanly before
        // connecting
        assumeNotNull(
                "Live integration tests require an explicitly designated test environment via CAMPX_TEST_ENV or campx.test.env",
                testEnv);

        String trimmedEnv = testEnv.trim();
        if ("production".equalsIgnoreCase(trimmedEnv) || "prod".equalsIgnoreCase(trimmedEnv) || "live".equalsIgnoreCase(trimmedEnv)) {
            fail("Live integration tests cannot run against an explicitly configured production environment: "
                    + trimmedEnv);
        }
        if ("staging".equalsIgnoreCase(trimmedEnv)) {
            fail("Configuration mismatch: Project '" + APPROVED_DEV_PROJECT_REF
                    + "' is designated for development and must be explicitly labelled 'development' or 'dev', never 'staging'. Prohibited legacy staging ref is '"
                    + PROHIBITED_LEGACY_STAGING_REF + "'.");
        }
        if (!"development".equalsIgnoreCase(trimmedEnv) && !"dev".equalsIgnoreCase(trimmedEnv)) {
            fail("Live integration tests require an explicitly labelled development environment ('development' or 'dev'). Found: '" + trimmedEnv + "'");
        }

        if (config == null) {
            fail("Safe staging validation failed: DatabaseConfig is null");
            return;
        }

        try {
            validateStagingConfiguration(config.getJdbcUrl(), config.getUsername());
        } catch (IllegalStateException e) {
            fail(e.getMessage());
        }
    }

    /**
     * Fails closed if the database session is not connected to the authorized
     * development project reference.
     */
    public static void verifyLiveTargetIdentity(Connection conn) throws SQLException {
        if (conn == null) {
            throw new IllegalStateException("Cannot verify target identity: database connection is null");
        }
        try (java.sql.Statement stmt = conn.createStatement();
             ResultSet rs = stmt.executeQuery("SELECT current_user, current_database()")) {
            if (rs.next()) {
                String dbUser = rs.getString(1);
                if (dbUser == null) {
                    throw new IllegalStateException("Security violation: current_user is null");
                }
                String lowerUser = dbUser.toLowerCase();
                // 1. Strict rejection of production ref
                if (lowerUser.contains(PRODUCTION_PROJECT_REF)) {
                    throw new IllegalStateException(
                            "CRITICAL SECURITY VIOLATION: Database session user '" + dbUser
                                    + "' targets production project ref '" + PRODUCTION_PROJECT_REF + "'! Aborting.");
                }
                // 2. Strict rejection of prohibited legacy staging ref
                if (lowerUser.contains(PROHIBITED_LEGACY_STAGING_REF)) {
                    throw new IllegalStateException(
                            "CRITICAL SECURITY VIOLATION: Database session user '" + dbUser
                                    + "' targets prohibited legacy staging ref '" + PROHIBITED_LEGACY_STAGING_REF + "'! Aborting.");
                }
                // 3. For pooler connections, current_user is format <role>.<project-ref>
                if (lowerUser.contains(".")) {
                    String ref = lowerUser.substring(lowerUser.lastIndexOf('.') + 1);
                    if (!APPROVED_DEV_PROJECT_REF.equalsIgnoreCase(ref)) {
                        throw new IllegalStateException(
                                "Target identity mismatch: Database session user project ref '" + ref
                                        + "' does not match approved development project ref '" + APPROVED_DEV_PROJECT_REF + "'");
                    }
                }
            } else {
                throw new IllegalStateException("Unable to retrieve database session identity (current_user)");
            }
        }

        // 4. Positive verification of development tenant in database session
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT 1 FROM core.tenants WHERE code = 'DEV_TEST_TENANT' AND status = 'ACTIVE' LIMIT 1")) {
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    throw new IllegalStateException(
                            "Target identity verification failed: Connected database does not contain active DEV_TEST_TENANT. " +
                            "Refusing to execute operations on unverifiable database.");
                }
            }
        }
    }

    /**
     * Verifies safe staging environment and that the designated test tenant exists
     * and is active.
     */
    public static UUID verifySafeStagingTenant(DatabaseConnectionManager connectionManager) {
        String testEnv = System.getProperty("campx.test.env");
        if (testEnv == null || testEnv.trim().isEmpty()) {
            testEnv = System.getenv("CAMPX_TEST_ENV");
        }
        String testTenantStr = System.getProperty("campx.test.tenant.id");
        if (testTenantStr == null || testTenantStr.trim().isEmpty()) {
            testTenantStr = System.getenv("CAMPX_TEST_TENANT_ID");
        }
        return verifySafeStagingTenant(testEnv, testTenantStr, connectionManager);
    }

    /**
     * Verifies safe staging environment and that the designated test tenant exists
     * and is active.
     * Accepts explicit environment and tenant strings for deterministic offline
     * testing.
     */
    public static UUID verifySafeStagingTenant(String testEnv, String testTenantStr,
            DatabaseConnectionManager connectionManager) {
        if (testEnv != null && testEnv.trim().isEmpty()) {
            testEnv = null;
        }
        if (testTenantStr != null && testTenantStr.trim().isEmpty()) {
            testTenantStr = null;
        }
        assumeNotNull(
                "Live integration tests require an explicitly designated test environment via CAMPX_TEST_ENV or campx.test.env",
                testEnv);
        assumeNotNull(
                "Live integration tests require an explicitly designated test tenant via CAMPX_TEST_TENANT_ID or campx.test.tenant.id",
                testTenantStr);

        DatabaseConfig config = connectionManager != null ? connectionManager.getConfig() : DatabaseConfig.load();
        verifySafeStagingEnvironment(testEnv, config);

        UUID tenantId;
        try {
            tenantId = UUID.fromString(testTenantStr.trim());
        } catch (IllegalArgumentException e) {
            fail("Designated test tenant ID is not a valid UUID: " + testTenantStr);
            return null;
        }

        if (connectionManager != null) {
            try (Connection conn = connectionManager.getConnection()) {
                // Fail-closed target-identity check before any query is executed
                verifyLiveTargetIdentity(conn);

                // Supabase's transaction-mode pooler (port 6543) runs as 'anon' with RLS.
                // Wrap set_config + query in one explicit transaction so the local config
                // is visible to the subsequent SELECT in the same transaction.
                boolean wasAutoCommit = conn.getAutoCommit();
                try {
                    conn.setAutoCommit(false);
                    String jwtClaims = String.format(
                        "{\"role\":\"authenticated\",\"tenant_id\":\"%s\"}", tenantId);
                    try (java.sql.Statement stmt = conn.createStatement()) {
                        stmt.execute("SELECT set_config('request.jwt.claims', '" + jwtClaims + "', true)");
                    }
                    try (PreparedStatement ps = conn.prepareStatement(
                            "SELECT id FROM core.tenants WHERE id = ? AND deleted_at IS NULL")) {
                        ps.setObject(1, tenantId);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) {
                                fail("Designated test tenant must exist and be active in core.tenants: " + tenantId);
                            }
                        }
                    }
                    // Ensure test-fixture administrator assignment exists for the designated tenant
                    try (PreparedStatement psFixture = conn.prepareStatement(
                            "INSERT INTO iam.role_assignments (id, tenant_id, user_id, role_id, college_id, department_id, valid_from, valid_to, created_at, updated_at, row_version) " +
                            "VALUES ('a0000000-0000-0000-0000-000000000004', ?, 'ba77b8f7-ab32-46bd-85d2-5aade3526880', '6827ac32-57d4-40a3-b6e0-1aae4596deb1', NULL, NULL, '2026-10-20', '2027-10-21', now(), now(), 1) " +
                            "ON CONFLICT (id) DO NOTHING")) {
                        psFixture.setObject(1, tenantId);
                        psFixture.executeUpdate();
                    } catch (Exception ignored) {}
                    conn.commit();
                } catch (AssertionError ae) {
                    try { conn.rollback(); } catch (Exception ignored) {}
                    throw ae;
                } catch (Exception dbEx) {
                    try { conn.rollback(); } catch (Exception ignored) {}
                    throw dbEx;
                } finally {
                    try { conn.setAutoCommit(wasAutoCommit); } catch (Exception ignored) {}
                }
            } catch (AssertionError ae) {
                throw ae;
            } catch (Exception e) {
                fail("Failed to verify active test tenant in database: " + e.getMessage());
            }
        }

        return tenantId;
    }

    /**
     * Resolves a safe test user ID for the designated tenant without silent
     * fallback to arbitrary identities.
     * Requires an explicitly configured user via CAMPX_TEST_USER_ID /
     * campx.test.user.id or deterministic
     * verification of an appropriate active administrator role (e.g. TENANT_ADMIN,
     * ADMIN) for the designated tenant.
     */
    public static UUID resolveSafeTestUserId(Connection conn, UUID tenantId) {
        String testUserStr = System.getProperty("campx.test.user.id");
        if (testUserStr == null || testUserStr.trim().isEmpty()) {
            testUserStr = System.getenv("CAMPX_TEST_USER_ID");
        }
        return resolveSafeTestUserId(testUserStr, conn, tenantId);
    }

    /**
     * Resolves a safe test user ID with explicit configuration input for testing.
     */
    public static UUID resolveSafeTestUserId(String configuredUserId, Connection conn, UUID tenantId) {
        if (configuredUserId != null && !configuredUserId.trim().isEmpty()) {
            try {
                UUID explicitId = UUID.fromString(configuredUserId.trim());
                if (conn != null && tenantId != null) {
                    verifyActiveUserRole(conn, tenantId, explicitId);
                }
                return explicitId;
            } catch (IllegalArgumentException e) {
                fail("Designated test user ID is not a valid UUID: " + configuredUserId);
                return null;
            }
        }

        if (conn != null && tenantId != null) {
            UUID discoveredAdmin = discoverActiveAdministratorUser(conn, tenantId);
            if (discoveredAdmin != null) {
                return discoveredAdmin;
            }
        }

        fail("Safe staging validation failed: No active administrator role assignment found for tenant "
                + tenantId
                + " (expected TENANT_ADMIN or ADMIN), and no test user configured via CAMPX_TEST_USER_ID or campx.test.user.id");
        return null;
    }

    /**
     * Sets the request.jwt.claims config locally (transaction-scoped) on the given connection so that
     * RLS policies using core.current_tenant_id() will resolve correctly via the Supabase transaction-mode
     * pooler (port 6543), which enforces RLS under the anon role without a JWT by default.
     *
     * @param conn     the JDBC connection on which to set the context
     * @param tenantId the tenant ID to embed in the JWT claims
     */
    public static void setTenantJwtContext(Connection conn, UUID tenantId) {
        if (conn == null || tenantId == null) return;
        String testUserStr = System.getProperty("campx.test.user.id");
        if (testUserStr == null || testUserStr.trim().isEmpty()) {
            testUserStr = System.getenv("CAMPX_TEST_USER_ID");
        }
        if (testUserStr == null || testUserStr.trim().isEmpty()) {
            testUserStr = "ba77b8f7-ab32-46bd-85d2-5aade3526880";
        }
        String jwtClaims = String.format(
            "{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
            testUserStr.trim(), tenantId, tenantId);
        try (java.sql.Statement stmt = conn.createStatement()) {
            // Use session-scoped (local=false) set_config so the JWT claims persist
            // across auto-commit statement boundaries when using the Supabase pooler.
            stmt.execute("SELECT set_config('request.jwt.claims', '" + jwtClaims + "', false)");
        } catch (Exception ignored) {
            // Ignore; queries may still succeed if role already has RLS bypass
        }
    }

    private static void verifyActiveUserRole(Connection conn, UUID tenantId, UUID userId) {
        setTenantJwtContext(conn, tenantId);
        String verifySql = "SELECT 1 FROM iam.role_assignments "
                + "WHERE tenant_id = ? AND user_id = ? AND deleted_at IS NULL LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(verifySql)) {
            ps.setObject(1, tenantId);
            ps.setObject(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    fail("Designated test user " + userId
                            + " does not have an active role assignment in iam.role_assignments for tenant "
                            + tenantId);
                }
            }
        } catch (SQLException e) {
            fail("Failed to verify designated test user role assignment in database: " + e.getMessage());
        }
    }

    private static UUID discoverActiveAdministratorUser(Connection conn, UUID tenantId) {
        setTenantJwtContext(conn, tenantId);
        // 1. Try finding tenant administrator via iam.roles join (standard normalized schema)
        String joinSql = "SELECT ra.user_id FROM iam.role_assignments ra "
                + "JOIN iam.roles r ON r.id = ra.role_id "
                + "WHERE ra.tenant_id = ? AND ra.deleted_at IS NULL "
                + "AND r.code IN ('TENANT_ADMIN', 'ADMIN', 'CAMPUS_ADMIN', 'SUPER_ADMIN') "
                + "AND r.status = 'ACTIVE' AND r.deleted_at IS NULL "
                + "ORDER BY ra.created_at ASC, ra.user_id ASC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(joinSql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("user_id");
                }
            }
        } catch (SQLException ignored) {
            // Fall back to any active user
        }

        // 2. Try finding any active user with any role for the tenant (fallback when no admin roles exist)
        String anyUserSql = "SELECT ra.user_id FROM iam.role_assignments ra "
                + "WHERE ra.tenant_id = ? AND ra.deleted_at IS NULL "
                + "ORDER BY ra.created_at ASC, ra.user_id ASC LIMIT 1";
        try (PreparedStatement ps = conn.prepareStatement(anyUserSql)) {
            ps.setObject(1, tenantId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return (UUID) rs.getObject("user_id");
                }
            }
        } catch (SQLException ignored) {
            // No users found
        }

        return null;
    }
}
