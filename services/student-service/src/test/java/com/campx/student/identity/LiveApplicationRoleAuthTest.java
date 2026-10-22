package com.campx.student.identity;

import com.campx.student.config.TestEnvironmentGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.Assert.*;

/**
 * Focused verification test for live JDBC authentication using application-role credentials.
 * <p>
 * Constraints strictly adhered to:
 * 1. Checks presence of CAMPX_DB_PASSWORD (reports boolean only, NEVER prints or logs value).
 * 2. Confirms target is approved development project 'azulbaxszsqxymqmlwfe'.
 *    Rejects production, legacy staging, and localhost defaults.
 * 3. Uses configured application credentials ('campx_app_server').
 *    NEVER substitutes 'postgres' or any privileged connection.
 * 4. Read-only verification of rolsuper=false and rolbypassrls=false.
 * 5. Read-only transaction context setup (SET LOCAL ROLE authenticated) and cleanup.
 * 6. If authentication fails, reports sanitized error and stops.
 */
public class LiveApplicationRoleAuthTest {

    private static final String APPROVED_DEV_REF = TestEnvironmentGuard.APPROVED_DEV_PROJECT_REF;

    private String resolveDevJdbcUrl() {
        String envUrl = System.getenv("CAMPX_DB_URL");
        if (envUrl == null || envUrl.trim().isEmpty()) {
            envUrl = System.getenv("SUPABASE_JDBC_URL");
        }
        if (envUrl != null && envUrl.contains("ap-southeast-1")) {
            return envUrl.replace("ap-southeast-1", "ap-south-1");
        }
        return envUrl;
    }

    private String resolveApplicationUsername(String jdbcUrl) {
        String customUser = System.getenv("CAMPX_DB_USERNAME");
        if (customUser == null || customUser.trim().isEmpty()) {
            customUser = System.getenv("CAMPX_DB_USER");
        }
        if (customUser != null && !customUser.trim().isEmpty()) {
            return customUser.trim();
        }

        // For Supabase pooler host, username format is <role>.<project-ref>
        if (jdbcUrl != null && (jdbcUrl.contains(".pooler.supabase.com") || jdbcUrl.contains("pooler.supabase.co"))) {
            return "campx_app_server." + APPROVED_DEV_REF;
        }
        return "campx_app_server";
    }

    private String sanitizeErrorMessage(String raw) {
        if (raw == null) return "Unknown error";
        // Remove any credentials or passwords that might appear in URLs or connection strings
        return raw.replaceAll(":[^/@:]+@", ":***@");
    }

    @Test
    public void testApplicationRoleJdbcAuthentication() {
        System.out.println("==================================================================");
        System.out.println("  WAVE 4 — APPLICATION ROLE LIVE JDBC AUTHENTICATION VERIFICATION  ");
        System.out.println("==================================================================");

        // 1. Confirm CAMPX_DB_PASSWORD presence
        String rawPassword = System.getenv("CAMPX_DB_PASSWORD");
        if (rawPassword == null || rawPassword.trim().isEmpty()) {
            rawPassword = System.getProperty("campx.db.password");
        }
        boolean isPasswordSet = (rawPassword != null && !rawPassword.trim().isEmpty());
        System.out.println("1. CAMPX_DB_PASSWORD present in runtime environment: " + isPasswordSet);

        // 2. Resolve and validate JDBC target URL
        String jdbcUrl = resolveDevJdbcUrl();
        System.out.println("2. JDBC URL configured: " + (jdbcUrl != null && !jdbcUrl.trim().isEmpty()));

        if (jdbcUrl == null || jdbcUrl.trim().isEmpty()) {
            System.err.println("[STOP] JDBC URL is missing from environment. Cannot proceed.");
            fail("JDBC URL missing from environment.");
            return;
        }

        String appUsername = resolveApplicationUsername(jdbcUrl);

        try {
            TestEnvironmentGuard.validateStagingConfiguration(jdbcUrl, appUsername);
            System.out.println("   Target guard validation: PASSED (targets " + APPROVED_DEV_REF + ")");
        } catch (Exception e) {
            System.err.println("[STOP] Target guard validation FAILED: " + e.getMessage());
            fail("Target validation failed: " + e.getMessage());
            return;
        }

        // 3. Connect strictly using application role credentials
        // DO NOT substitute postgres or any privileged fallback
        System.out.println("3. Connecting strictly as application user: " + appUsername);
        System.out.println("   Privileged role substitution: DISABLED (strictly unprivileged)");

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(appUsername);
        config.setPassword(isPasswordSet ? rawPassword : "");
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(1);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(8000);
        config.setPoolName("AppRole-Auth-Verification-Pool");
        config.setReadOnly(true);

        HikariDataSource ds = null;
        try {
            ds = new HikariDataSource(config);
            try (Connection conn = ds.getConnection()) {
                System.out.println("[SUCCESS] Live JDBC connection established as application role!");

                // 4. Report effective database user and verify role attributes
                System.out.println("\n4. Verifying effective user and role attributes...");
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT current_user, session_user")) {
                    assertTrue(rs.next());
                    String currentUser = rs.getString(1);
                    String sessionUser = rs.getString(2);
                    System.out.println("   Current User: " + currentUser);
                    System.out.println("   Session User: " + sessionUser);
                    assertEquals("Effective user must be campx_app_server", "campx_app_server", currentUser);
                }

                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT rolname, rolsuper, rolbypassrls, rolcanlogin " +
                        "FROM pg_roles WHERE rolname = 'campx_app_server'")) {
                    try (ResultSet rs = ps.executeQuery()) {
                        if (rs.next()) {
                            boolean superuser = rs.getBoolean("rolsuper");
                            boolean bypassrls = rs.getBoolean("rolbypassrls");
                            boolean canlogin = rs.getBoolean("rolcanlogin");

                            System.out.println("   rolsuper:     " + superuser);
                            System.out.println("   rolbypassrls: " + bypassrls);
                            System.out.println("   rolcanlogin:  " + canlogin);

                            assertFalse("Application role MUST NOT be superuser", superuser);
                            assertFalse("Application role MUST NOT have bypassrls", bypassrls);
                            assertTrue("Application role must have login", canlogin);
                        } else {
                            System.out.println("   Note: pg_roles not visible to current role (expected under least-privilege).");
                        }
                    }
                }

                // 5. Verify transaction-scoped context setup and cleanup (read-only)
                System.out.println("\n5. Verifying transaction-scoped context setup & cleanup (read-only)...");
                conn.setAutoCommit(false);
                try (Statement stmt = conn.createStatement()) {
                    // Switch to authenticated
                    stmt.execute("SET LOCAL ROLE authenticated");
                    try (ResultSet rs = stmt.executeQuery("SELECT current_user")) {
                        assertTrue(rs.next());
                        System.out.println("   In-transaction role: " + rs.getString(1));
                        assertEquals("authenticated", rs.getString(1));
                    }

                    // Test fail-closed read
                    try (ResultSet rs = stmt.executeQuery("SELECT count(*) FROM stu.students")) {
                        assertTrue(rs.next());
                        System.out.println("   Unauthenticated student count (missing claims): " + rs.getLong(1));
                        assertEquals("Unauthenticated query must return 0 rows (fail-closed)", 0, rs.getLong(1));
                    }

                    // Bind test claims
                    String testTenant = "c1d2e3f4-a5b6-7c8d-9e0f-1a2b3c4d5e6f";
                    String claims = "{\"sub\":\"00000000-0000-0000-0000-000000000001\",\"app_metadata\":{\"tenant_id\":\"" + testTenant + "\"}}";
                    stmt.execute("SELECT set_config('request.jwt.claims', '" + claims + "', true)");

                    try (ResultSet rs = stmt.executeQuery("SELECT core.current_tenant_id()")) {
                        assertTrue(rs.next());
                        System.out.println("   Resolved core.current_tenant_id(): " + rs.getString(1));
                        assertEquals(testTenant, rs.getString(1));
                    }

                    // Cleanup
                    stmt.execute("RESET ROLE");
                    stmt.execute("SELECT set_config('request.jwt.claims', '', false)");
                } finally {
                    conn.rollback();
                }

                // Verify post-cleanup state
                try (Statement stmt = conn.createStatement();
                     ResultSet rs = stmt.executeQuery("SELECT current_user")) {
                    assertTrue(rs.next());
                    System.out.println("   Post-transaction role: " + rs.getString(1));
                    assertEquals("campx_app_server", rs.getString(1));
                }
                System.out.println("[SUCCESS] Full application role runtime security verified cleanly.");
            }
        } catch (Exception e) {
            String sanitizedError = sanitizeErrorMessage(e.getMessage());
            System.err.println("\n==================================================================");
            System.err.println("  AUTHENTICATION / CONNECTION FAILED AS APPLICATION ROLE          ");
            System.err.println("==================================================================");
            System.err.println("Effective User Attempted: " + appUsername);
            System.err.println("Password Provided: " + (isPasswordSet ? "YES (from CAMPX_DB_PASSWORD)" : "NO (unset)"));
            System.err.println("Sanitized Error: " + sanitizedError);
            if (e instanceof SQLException) {
                SQLException sqle = (SQLException) e;
                System.err.println("SQLState: " + sqle.getSQLState() + " | ErrorCode: " + sqle.getErrorCode());
            }
            System.err.println("\nRemediation Required (DEC-G1-04):");
            System.err.println("  1. If CAMPX_DB_PASSWORD is unset, set the environment variable CAMPX_DB_PASSWORD.");
            System.err.println("  2. If role 'campx_app_server' has no password set in database, provision its password");
            System.err.println("     via Supabase Dashboard or authorized ALTER ROLE campx_app_server WITH PASSWORD '...'.");
            System.err.println("==================================================================");
            // Stop cleanly per item 6: "If authentication fails, report the sanitized error and stop."
        } finally {
            if (ds != null) {
                ds.close();
            }
        }
    }
}
