package com.campx.gateway.identity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * READ-ONLY live PostgreSQL catalog introspection for Wave 4 Gate G1.
 * <p>
 * Strictly read-only queries against pg_roles, pg_class, pg_policies,
 * information_schema.role_table_grants, pg_proc, and pg_views on dev ref azulbaxszsqxymqmlwfe.
 * Zero database writes, zero DDL, zero DML.
 * </p>
 */
public class LiveG1DatabasePreflightTest {

    private static final String APPROVED_DEV_REF = "azulbaxszsqxymqmlwfe";
    private static final String PROHIBITED_PROD_REF = "ekspahbsbwdpraaqfqui";
    private static final String PROHIBITED_STAGING_REF = "kpwdvvcschankgggitnj";

    private String getDevJdbcUrl() {
        String envUrl = System.getenv("SUPABASE_JDBC_URL");
        if (envUrl != null && envUrl.contains("ap-southeast-1")) {
            return envUrl.replace("ap-southeast-1", "ap-south-1");
        }
        return envUrl;
    }

    private HikariDataSource createDevDataSource() {
        String jdbcUrl = getDevJdbcUrl();
        String user = System.getenv("SUPABASE_USER");
        String pass = System.getenv("SUPABASE_PASSWORD");

        if (jdbcUrl == null || user == null || pass == null) {
            return null;
        }

        // Strict target validation before opening any connection
        if (!user.contains(APPROVED_DEV_REF)) {
            throw new IllegalStateException("Security violation: Username does not match approved dev ref: " + APPROVED_DEV_REF);
        }
        if (user.contains(PROHIBITED_PROD_REF) || jdbcUrl.contains(PROHIBITED_PROD_REF)) {
            throw new IllegalStateException("Security violation: Target is prohibited production ref: " + PROHIBITED_PROD_REF);
        }
        if (user.contains(PROHIBITED_STAGING_REF) || jdbcUrl.contains(PROHIBITED_STAGING_REF)) {
            throw new IllegalStateException("Security violation: Target is prohibited legacy staging ref: " + PROHIBITED_STAGING_REF);
        }

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setPoolName("G1-Preflight-Pool");
        config.setReadOnly(true); // Enforce read-only connection pool
        return new HikariDataSource(config);
    }

    @Test
    public void testReadOnlyCatalogPreflight() throws Exception {
        HikariDataSource ds = createDevDataSource();
        if (ds == null) {
            System.out.println("[G1-PREFLIGHT] [BLOCKED] Database credentials missing from environment");
            return;
        }

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Connection identity
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT current_database(), current_user, session_user, inet_server_addr(), version()")) {
                assertTrue(rs.next());
                System.out.println("=== 1. CONNECTION IDENTITY ===");
                System.out.println("Database: " + rs.getString(1));
                System.out.println("Current User: " + rs.getString(2));
                System.out.println("Session User: " + rs.getString(3));
                System.out.println("Server IP: " + rs.getString(4));
                System.out.println("PG Version: " + rs.getString(5));
            }

            // 2. Roles audit (BYPASSRLS, SUPERUSER, LOGIN)
            System.out.println("\n=== 2. ROLES AUDIT ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT rolname, rolsuper, rolinherit, rolcreaterole, rolcreatedb, rolcanlogin, rolbypassrls, rolconnlimit " +
                    "FROM pg_roles " +
                    "WHERE rolname IN ('postgres', 'authenticated', 'anon', 'service_role', 'campx_app_server') " +
                    "ORDER BY rolname")) {
                while (rs.next()) {
                    System.out.println(String.format("Role: %s | Superuser: %b | CanLogin: %b | BypassRLS: %b | ConnLimit: %d",
                            rs.getString("rolname"),
                            rs.getBoolean("rolsuper"),
                            rs.getBoolean("rolcanlogin"),
                            rs.getBoolean("rolbypassrls"),
                            rs.getInt("rolconnlimit")));
                }
            }

            // 3. Table ownership, RLS Enabled & RLS Forced
            System.out.println("\n=== 3. TABLE RLS AUDIT (TIER 1 & TIER 2) ===");
            String tableQuery =
                    "SELECT n.nspname AS schema_name, c.relname AS table_name, r.rolname AS table_owner, " +
                    "       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced " +
                    "FROM pg_class c " +
                    "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                    "JOIN pg_roles r ON r.oid = c.relowner " +
                    "WHERE (n.nspname = 'stu' AND c.relname IN ('students', 'guardianships')) " +
                    "   OR (n.nspname = 'core' AND c.relname IN ('persons', 'addresses', 'person_contacts', 'person_emergency_contacts', 'person_identifiers')) " +
                    "   OR (n.nspname = 'acd' AND c.relname IN ('batch_students', 'section_enrollments')) " +
                    "   OR (n.nspname IN ('audit', 'sys') AND c.relname IN ('events', 'outbox_events')) " +
                    "ORDER BY n.nspname, c.relname";
            try (ResultSet rs = stmt.executeQuery(tableQuery)) {
                while (rs.next()) {
                    System.out.println(String.format("Table: %s.%s | Owner: %s | RLS_Enabled: %b | RLS_Forced: %b",
                            rs.getString("schema_name"),
                            rs.getString("table_name"),
                            rs.getString("table_owner"),
                            rs.getBoolean("rls_enabled"),
                            rs.getBoolean("rls_forced")));
                }
            }

            // 4. Existing RLS Policies
            System.out.println("\n=== 4. EXISTING RLS POLICIES ===");
            String policyQuery =
                    "SELECT schemaname, tablename, policyname, cmd, roles, qual, with_check " +
                    "FROM pg_policies " +
                    "WHERE (schemaname = 'stu' AND tablename IN ('students', 'guardianships')) " +
                    "   OR (schemaname = 'core' AND tablename IN ('persons', 'addresses', 'person_contacts', 'person_identifiers')) " +
                    "   OR (schemaname = 'acd' AND tablename IN ('batch_students', 'section_enrollments')) " +
                    "   OR (schemaname = 'audit' AND tablename = 'events') " +
                    "ORDER BY schemaname, tablename, policyname";
            try (ResultSet rs = stmt.executeQuery(policyQuery)) {
                while (rs.next()) {
                    System.out.println(String.format("Policy: %s.%s -> %s [%s] roles=%s | USING: %s | WITH_CHECK: %s",
                            rs.getString("schemaname"),
                            rs.getString("tablename"),
                            rs.getString("policyname"),
                            rs.getString("cmd"),
                            rs.getString("roles"),
                            rs.getString("qual"),
                            rs.getString("with_check")));
                }
            }

            // 5. Existing Direct Grants
            System.out.println("\n=== 5. EXISTING DIRECT GRANTS ===");
            String grantsQuery =
                    "SELECT table_schema, table_name, grantee, string_agg(privilege_type, ', ') AS privs " +
                    "FROM information_schema.role_table_grants " +
                    "WHERE table_schema IN ('stu', 'core', 'acd', 'audit') " +
                    "  AND table_name IN ('students', 'guardianships', 'persons', 'batch_students', 'events') " +
                    "  AND grantee IN ('authenticated', 'anon', 'PUBLIC', 'campx_app_server') " +
                    "GROUP BY table_schema, table_name, grantee " +
                    "ORDER BY table_schema, table_name, grantee";
            try (ResultSet rs = stmt.executeQuery(grantsQuery)) {
                while (rs.next()) {
                    System.out.println(String.format("Grant: %s.%s to %s -> %s",
                            rs.getString("table_schema"),
                            rs.getString("table_name"),
                            rs.getString("grantee"),
                            rs.getString("privs")));
                }
            }

            // 6. Helper Functions in pg_proc
            System.out.println("\n=== 6. HELPER FUNCTIONS IN CATALOG ===");
            String procQuery =
                    "SELECT n.nspname, p.proname, p.prosecdef, r.rolname " +
                    "FROM pg_proc p " +
                    "JOIN pg_namespace n ON n.oid = p.pronamespace " +
                    "JOIN pg_roles r ON r.oid = p.proowner " +
                    "WHERE (n.nspname = 'core' AND p.proname IN ('current_tenant_id', 'current_person_id', 'my_student_ids', 'students_in_scope')) " +
                    "   OR (n.nspname = 'iam' AND p.proname IN ('has_permission', 'perm_all')) " +
                    "   OR (n.nspname = 'audit' AND p.proname IN ('log_event', 'capture')) " +
                    "ORDER BY n.nspname, p.proname";
            try (ResultSet rs = stmt.executeQuery(procQuery)) {
                while (rs.next()) {
                    System.out.println(String.format("Function: %s.%s() | Owner: %s | SecurityDefiner: %b",
                            rs.getString("nspname"),
                            rs.getString("proname"),
                            rs.getString("rolname"),
                            rs.getBoolean("prosecdef")));
                }
            }

            System.out.println("\n=== 7. PREFLIGHT COMPLETED SAFELY (ZERO WRITES) ===");
        } finally {
            ds.close();
        }
    }
}
