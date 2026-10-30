package com.campx.academic.attendance.identity;

import com.campx.academic.attendance.config.TestEnvironmentGuard;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Read-only catalog inspection of Attendance domain structures on development ref azulbaxszsqxymqmlwfe.
 * Strictly READ-ONLY: zero database writes, zero DDL, zero DML.
 */
public class LiveAttendanceCatalogAuditTest {

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
        config.setPoolName("Attendance-Audit-Pool");
        config.setReadOnly(true);
        return new HikariDataSource(config);
    }

    @Test
    public void auditLiveAttendanceCatalog() throws Exception {
        HikariDataSource ds = createDataSource();
        if (ds == null) {
            System.out.println("Skipping LiveAttendanceCatalogAuditTest: Database credentials not in environment.");
            return;
        }

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

            // 2. Attendance Tables Columns
            System.out.println("\n=== 2. ATTENDANCE TABLES COLUMN STRUCTURE ===");
            String[] tables = {"attendance_sessions", "attendance_records", "attendance_corrections"};
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

            // 3. Constraints (PK, FK, Check, Unique)
            System.out.println("\n=== 3. CONSTRAINTS ON acd.attendance_* TABLES ===");
            String constraintSql =
                    "SELECT conname, contype, conrelid::regclass AS table_name, pg_get_constraintdef(c.oid) AS def " +
                    "FROM pg_constraint c " +
                    "JOIN pg_namespace n ON n.oid = c.connamespace " +
                    "WHERE n.nspname = 'acd' AND conrelid::regclass::text LIKE '%attendance%' " +
                    "ORDER BY conrelid::regclass::text, conname";
            try (ResultSet rs = stmt.executeQuery(constraintSql)) {
                while (rs.next()) {
                    System.out.printf("  Table: %-25s Constraint: %-25s Type: %-2s Def: %s%n",
                            rs.getString("table_name"), rs.getString("conname"), rs.getString("contype"), rs.getString("def"));
                }
            }

            // 4. Row Level Security & Policies
            System.out.println("\n=== 4. RLS STATUS ON acd.attendance_* ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT c.relname, c.relrowsecurity, c.relforcerowsecurity " +
                    "FROM pg_class c " +
                    "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE n.nspname = 'acd' AND c.relname LIKE 'attendance%' " +
                    "ORDER BY c.relname")) {
                while (rs.next()) {
                    System.out.printf("  Table: acd.%-25s RLS: %-5s FORCE_RLS: %-5s%n",
                            rs.getString(1), rs.getBoolean(2), rs.getBoolean(3));
                }
            }

            System.out.println("\n=== 4b. RLS POLICIES ON acd.attendance_* ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT tablename, policyname, permissive, roles, cmd, qual, with_check " +
                    "FROM pg_policies " +
                    "WHERE schemaname = 'acd' AND tablename LIKE 'attendance%' " +
                    "ORDER BY tablename, policyname")) {
                while (rs.next()) {
                    System.out.printf("  Table: %-25s Policy: %-30s Cmd: %-6s Qual: %s | WithCheck: %s%n",
                            rs.getString("tablename"), rs.getString("policyname"), rs.getString("cmd"),
                            rs.getString("qual"), rs.getString("with_check"));
                }
            }

            // 5. Transactional Outbox Table Check
            System.out.println("\n=== 5. TRANSACTIONAL OUTBOX TABLE CHECK ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT c.relname, c.relrowsecurity FROM pg_class c " +
                    "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE n.nspname = 'sys' AND c.relname = 'outbox_events'")) {
                while (rs.next()) {
                    System.out.printf("  Table: sys.%-20s RLS: %-5s%n",
                            rs.getString(1), rs.getBoolean(2));
                }
            }
        } finally {
            ds.close();
        }
    }
}
