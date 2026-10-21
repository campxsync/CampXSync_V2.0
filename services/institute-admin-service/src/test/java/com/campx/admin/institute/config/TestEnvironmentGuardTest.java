package com.campx.admin.institute.config;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.AssumptionViolatedException;
import java.util.UUID;
import static org.junit.Assert.assertEquals;

/**
 * Focused, offline unit tests for Institute Admin live-test safety guardrails.
 * <p>
 * Validates:
 * - Direct Supabase staging connection acceptance (with and without db. prefix)
 * - Supabase pooler staging connection acceptance (with project ref in username)
 * - Strict rejection of production project ref 'ekspahbsbwdpraaqfqui' (in URL or username)
 * - Strict rejection of localhost and loopback IPv4/IPv6 addresses (preventing silent fallback to DatabaseConfig defaults)
 * - Rejection of unknown/unauthorized Supabase project references
 * - Rejection of arbitrary external database hosts
 * - Rejection of malformed JDBC URLs
 * - Rejection of missing/empty JDBC URL or username
 * - Rejection of explicit production test environment
 * - Clean skip when test environment is unconfigured
 * <p>
 * Crucially: These tests run purely in-memory and NEVER open network sockets or database connections.
 */
public class TestEnvironmentGuardTest {

    private String originalTestEnv;
    private String originalSysUrl;
    private String originalSysUser;

    @Before
    public void setUp() {
        originalTestEnv = System.getProperty("campx.test.env");
        originalSysUrl = System.getProperty("campx.db.url");
        originalSysUser = System.getProperty("campx.db.username");
    }

    @After
    public void tearDown() {
        if (originalTestEnv != null) {
            System.setProperty("campx.test.env", originalTestEnv);
        } else {
            System.clearProperty("campx.test.env");
        }

        if (originalSysUrl != null) {
            System.setProperty("campx.db.url", originalSysUrl);
        } else {
            System.clearProperty("campx.db.url");
        }

        if (originalSysUser != null) {
            System.setProperty("campx.db.username", originalSysUser);
        } else {
            System.clearProperty("campx.db.username");
        }
    }

    @Test
    public void testValidDirectStagingConnectionAccepted() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://db.azulbaxszsqxymqmlwfe.supabase.co:5432/postgres",
                "postgres");
    }

    @Test
    public void testValidDirectStagingConnectionWithoutDbPrefixAccepted() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://azulbaxszsqxymqmlwfe.supabase.co:5432/postgres?sslmode=require",
                "postgres");
    }

    @Test
    public void testValidPoolerStagingConnectionAccepted() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres",
                "postgres.azulbaxszsqxymqmlwfe");
    }

    @Test
    public void testValidPoolerStagingWithCustomRoleAccepted() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:5432/postgres?sslmode=require",
                "campx_app_server.azulbaxszsqxymqmlwfe");
    }

    @Test(expected = IllegalStateException.class)
    public void testLegacyStagingProjectRefRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://db.kpwdvvcschankgggitnj.supabase.co:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testLegacyStagingPoolerUsernameRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres",
                "postgres.kpwdvvcschankgggitnj");
    }

    @Test(expected = IllegalStateException.class)
    public void testProductionHostInDirectConnectionRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://db.ekspahbsbwdpraaqfqui.supabase.co:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testProductionHostWithoutDbPrefixRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://ekspahbsbwdpraaqfqui.supabase.co:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testProductionUsernameInPoolerRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres",
                "postgres.ekspahbsbwdpraaqfqui");
    }

    @Test(expected = IllegalStateException.class)
    public void testLocalhostNamedHostRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://localhost:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testLoopbackIpv4Rejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://127.0.0.1:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testLoopbackIpv6Rejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://[::1]:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testUnknownDirectSupabaseProjectRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://db.abcdefghijklmnopqrst.supabase.co:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testUnknownPoolerProjectRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://aws-0-us-east-1.pooler.supabase.com:6543/postgres",
                "postgres.abcdefghijklmnopqrst");
    }

    @Test(expected = IllegalStateException.class)
    public void testArbitraryExternalHostRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://database.external-company.com:5432/postgres",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testMalformedJdbcUrlRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "not-a-valid-jdbc-url",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingJdbcUrlRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                null,
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testEmptyJdbcUrlRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "   ",
                "postgres");
    }

    @Test(expected = IllegalStateException.class)
    public void testMissingUsernameRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://db.azulbaxszsqxymqmlwfe.supabase.co:5432/postgres",
                null);
    }

    @Test(expected = IllegalStateException.class)
    public void testPoolerMissingProjectSuffixInUsernameRejected() {
        TestEnvironmentGuard.validateStagingConfiguration(
                "jdbc:postgresql://aws-0-ap-south-1.pooler.supabase.com:6543/postgres",
                "postgres");
    }

    @Test(expected = AssertionError.class)
    public void testProductionEnvironmentStringRejected() {
        DatabaseConfig config = new DatabaseConfig();
        config.setJdbcUrl("jdbc:postgresql://db.azulbaxszsqxymqmlwfe.supabase.co:5432/postgres");
        config.setUsername("postgres");

        TestEnvironmentGuard.verifySafeStagingEnvironment("production", config);
    }

    @Test(expected = AssumptionViolatedException.class)
    public void testUnconfiguredEnvironmentSkipsSafely() {
        DatabaseConfig config = new DatabaseConfig();
        config.setJdbcUrl("jdbc:postgresql://db.azulbaxszsqxymqmlwfe.supabase.co:5432/postgres");
        config.setUsername("postgres");

        TestEnvironmentGuard.verifySafeStagingEnvironment((String) null, config);
    }

    @Test(expected = AssertionError.class)
    public void testStagingEnvironmentFailsOnDefaultLocalhostConfig() {
        DatabaseConfig config = new DatabaseConfig();
        // default DatabaseConfig() has localhost URL
        TestEnvironmentGuard.verifySafeStagingEnvironment("staging", config);
    }

    @Test
    public void testResolveSafeTestUserIdWithExplicitConfig() {
        UUID expected = UUID.randomUUID();
        UUID actual = TestEnvironmentGuard.resolveSafeTestUserId(expected.toString(), null, UUID.randomUUID());
        assertEquals(expected, actual);
    }

    @Test(expected = AssertionError.class)
    public void testResolveSafeTestUserIdWithInvalidUuidThrows() {
        TestEnvironmentGuard.resolveSafeTestUserId("not-a-valid-uuid", null, UUID.randomUUID());
    }

    @Test(expected = AssertionError.class)
    public void testResolveSafeTestUserIdWithoutExplicitUserOrAdminFails() {
        TestEnvironmentGuard.resolveSafeTestUserId((String) null, null, UUID.randomUUID());
    }

    @Test
    public void testResolveSafeSuperAdminIdWithExplicitConfig() {
        UUID expected = UUID.randomUUID();
        UUID actual = TestEnvironmentGuard.resolveSafeSuperAdminId(expected.toString(), null);
        assertEquals(expected, actual);
    }

    @Test(expected = AssertionError.class)
    public void testResolveSafeSuperAdminIdWithInvalidUuidThrows() {
        TestEnvironmentGuard.resolveSafeSuperAdminId("not-a-valid-uuid", null);
    }
}
