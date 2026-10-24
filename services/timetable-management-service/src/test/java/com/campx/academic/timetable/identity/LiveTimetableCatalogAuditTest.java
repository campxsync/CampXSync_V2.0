package com.campx.academic.timetable.identity;

import com.campx.academic.timetable.config.TestEnvironmentGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Read-only catalog inspection of Timetable domain structures on development ref azulbaxszsqxymqmlwfe.
 * Strictly READ-ONLY: zero database writes, zero DDL, zero DML.
 */
public class LiveTimetableCatalogAuditTest {

    private String getDevJdbcUrl() {
        String envUrl = System.getenv("CAMPX_DB_URL");
        if (envUrl == null || envUrl.trim().isEmpty()) {
            envUrl = System.getenv("SUPABASE_JDBC_URL");
        }
        if (envUrl != null && envUrl.contains("ap-southeast-1")) {
            return envUrl.replace("ap-southeast-1", "ap-south-1");
        }
        return envUrl;
    }

    private HikariDataSource createDataSource() {
        String jdbcUrl = getDevJdbcUrl();
        String user = System.getenv("CAMPX_DB_USER");
        String pass = System.getenv("CAMPX_DB_PASSWORD");

        // Fall back to SUPABASE credentials if dedicated not present
        if (user == null || user.trim().isEmpty()) {
            user = System.getenv("SUPABASE_USER");
            pass = System.getenv("SUPABASE_PASSWORD");
        }

        if (jdbcUrl == null || user == null || pass == null) {
            return null;
        }

        TestEnvironmentGuard.validateStagingConfiguration(jdbcUrl, user);

        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(jdbcUrl);
        config.setUsername(user);
        config.setPassword(pass);
        config.setDriverClassName("org.postgresql.Driver");
        config.setMaximumPoolSize(2);
        config.setMinimumIdle(1);
        config.setConnectionTimeout(10000);
        config.setPoolName("Timetable-Audit-Pool");
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    @Test
    public void auditLiveTimetableCatalog() throws Exception {
        HikariDataSource ds = createDataSource();
        assertNotNull("Database credentials must be present in environment", ds);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            // 1. Target verification
            System.out.println("=== 1. TARGET IDENTITY VERIFICATION ===");
            try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, session_user, inet_server_addr()")) {
                assertTrue(rs.next());
                System.out.println("Database: " + rs.getString(1));
                System.out.println("Current User: " + rs.getString(2));
                System.out.println("Session User: " + rs.getString(3));
                System.out.println("Server IP: " + rs.getString(4));
            }

            // 2. Timetable Tables Columns
            System.out.println("\n=== 2. TIMETABLE TABLES COLUMN STRUCTURE ===");
            String[] tables = {"timetables", "timetable_versions", "timetable_entries", "timetable_conflicts", "section_faculty", "course_sections"};
            for (String tbl : tables) {
                System.out.println("\n--- Columns in acd." + tbl + " ---");
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT column_name, data_type, is_nullable, column_default " +
                        "FROM information_schema.columns " +
                        "WHERE table_schema = 'acd' AND table_name = '" + tbl + "' " +
                        "ORDER BY ordinal_position")) {
                    while (rs.next()) {
                        System.out.printf("  %-25s %-20s null=%-5s default=%s%n",
                                rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4));
                    }
                }
            }

            // 3. Constraints (PK, FK, Check, Exclusion)
            System.out.println("\n=== 3. CONSTRAINTS ON acd.timetable_* TABLES ===");
            String constraintSql =
                    "SELECT conname, contype, conrelid::regclass AS table_name, pg_get_constraintdef(c.oid) AS def " +
                    "FROM pg_constraint c " +
                    "JOIN pg_namespace n ON n.oid = c.connamespace " +
                    "WHERE n.nspname = 'acd' AND conrelid::regclass::text LIKE '%timetable%' " +
                    "ORDER BY conrelid::regclass::text, conname";
            try (ResultSet rs = stmt.executeQuery(constraintSql)) {
                while (rs.next()) {
                    System.out.printf("  Table: %-25s Constraint: %-25s Type: %-2s Def: %s%n",
                            rs.getString("table_name"), rs.getString("conname"), rs.getString("contype"), rs.getString("def"));
                }
            }

            // 4. Triggers and Functions on acd.timetable_* tables
            System.out.println("\n=== 4. TRIGGERS & FUNCTIONS ON acd.timetable_* TABLES ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT p.proname, pg_get_functiondef(p.oid) " +
                    "FROM pg_proc p " +
                    "JOIN pg_namespace n ON n.oid = p.pronamespace " +
                    "WHERE n.nspname = 'acd' AND p.proname LIKE '%timetable%'")) {
                while (rs.next()) {
                    System.out.println("--- FUNCTION: " + rs.getString(1) + " ---\n" + rs.getString(2));
                }
            }

            // 5. Table Grants for authenticated and campx_app_server
            System.out.println("\n=== 5. PRIVILEGES FOR authenticated & campx_app_server ===");
            for (String tbl : tables) {
                try (ResultSet rs = stmt.executeQuery(
                        "SELECT grantee, privilege_type " +
                        "FROM information_schema.table_privileges " +
                        "WHERE table_schema = 'acd' AND table_name = '" + tbl + "' " +
                        "  AND grantee IN ('authenticated', 'campx_app_server') " +
                        "ORDER BY grantee, privilege_type")) {
                    while (rs.next()) {
                        System.out.printf("  Table: acd.%-20s Grantee: %-18s Privilege: %s%n",
                                tbl, rs.getString(1), rs.getString(2));
                    }
                }
            }

            // 6. Row Level Security & Policies
            System.out.println("\n=== 6. RLS STATUS & POLICIES ON acd.timetable_* ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity " +
                    "FROM pg_class c " +
                    "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE n.nspname = 'acd' AND c.relname LIKE 'timetable%' " +
                    "ORDER BY c.relname")) {
                while (rs.next()) {
                    System.out.printf("  Table: acd.%-20s RLS: %-5s FORCE_RLS: %-5s%n",
                            rs.getString(1), rs.getBoolean(2), rs.getBoolean(3));
                }
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT tablename, policyname, roles, cmd, qual, with_check " +
                    "FROM pg_policies " +
                    "WHERE schemaname = 'acd' AND tablename LIKE 'timetable%' " +
                    "ORDER BY tablename, policyname")) {
                while (rs.next()) {
                    System.out.printf("  Table: acd.%-20s Policy: %-30s Roles: %-15s Cmd: %-8s%n    USING: %s%n    WITH CHECK: %s%n",
                            rs.getString(1), rs.getString(2), rs.getString(3), rs.getString(4),
                            rs.getString(5), rs.getString(6));
                }
            }

            // 8. Completed audit
            System.out.println("\n=== 8. TIMETABLE CATALOG AUDIT COMPLETE ===");

            System.out.println("\n[SUCCESS] Live Timetable Catalog Audit Complete.");
        } finally {
            ds.close();
        }
    }
}
