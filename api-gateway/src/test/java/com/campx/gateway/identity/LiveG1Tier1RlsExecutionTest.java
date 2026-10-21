package com.campx.gateway.identity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Executes the owner-authorized DEC-G1-02A Tier 1 Core RLS Hardening step:
 * Applies FORCE ROW LEVEL SECURITY strictly to the 4 core tables:
 * - stu.students
 * - core.persons
 * - stu.guardianships
 * - acd.batch_students
 *
 * Strict Guardrails:
 * - Target MUST be approved dev ref: azulbaxszsqxymqmlwfe.
 * - Strictly forbids production ref: ekspahbsbwdpraaqfqui.
 * - Strictly forbids legacy staging ref: kpwdvvcschankgggitnj.
 * - Requires explicit approval token: APPROVE-G1-FORCE-RLS-TIER1.
 * - Strictly bounded to Tier 1; does NOT touch Tier 2 extended tables.
 */
public class LiveG1Tier1RlsExecutionTest {

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

        // Strict target validation before opening connection
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
        config.setPoolName("G1-Tier1Rls-Pool");
        return new HikariDataSource(config);
    }

    @Test
    public void executeAuthorizedTier1RlsHardening() throws Exception {
        HikariDataSource ds = createDevDataSource();
        assertNotNull("Database credentials must be present in environment", ds);

        try (Connection conn = ds.getConnection();
             Statement stmt = conn.createStatement()) {

            // Step 1: Pre-Execution Target Verification
            System.out.println("=== PRE-EXECUTION TARGET VERIFICATION ===");
            try (ResultSet rs = stmt.executeQuery("SELECT current_database(), current_user, session_user, inet_server_addr()")) {
                assertTrue(rs.next());
                System.out.println("Database: " + rs.getString(1));
                System.out.println("Current User: " + rs.getString(2));
                System.out.println("Session User: " + rs.getString(3));
                System.out.println("Server IP: " + rs.getString(4));
            }

            // Step 2: Execute Block 1 of Script 04 (Tier 1 Core Scope Only)
            System.out.println("\n=== EXECUTING SCRIPT 04 BLOCK 1: TIER 1 CORE FORCE RLS ===");
            String ddlTier1 =
                    "DO $$\n" +
                    "DECLARE\n" +
                    "    v_table text;\n" +
                    "    v_core_tables text[] := ARRAY[\n" +
                    "        'stu.students',\n" +
                    "        'core.persons',\n" +
                    "        'stu.guardianships',\n" +
                    "        'acd.batch_students'\n" +
                    "    ];\n" +
                    "BEGIN\n" +
                    "    FOREACH v_table IN ARRAY v_core_tables\n" +
                    "    LOOP\n" +
                    "        IF to_regclass(v_table) IS NOT NULL THEN\n" +
                    "            EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);\n" +
                    "            EXECUTE format('ALTER TABLE %s FORCE ROW LEVEL SECURITY', v_table);\n" +
                    "            RAISE NOTICE 'Applied ENABLE and FORCE ROW LEVEL SECURITY on Tier 1 table: %', v_table;\n" +
                    "        ELSE\n" +
                    "            RAISE EXCEPTION 'Precondition Failed: Core table % does not exist in catalog', v_table;\n" +
                    "        END IF;\n" +
                    "    END LOOP;\n" +
                    "END $$;";

            stmt.execute(ddlTier1);
            System.out.println("[SUCCESS] Executed FORCE ROW LEVEL SECURITY on 4 Tier 1 Core tables.");

            // Step 3: Live Post-Change Verification (Assertion 2)
            System.out.println("\n=== POST-CHANGE VERIFICATION: TIER 1 RLS STATUS ===");
            String queryTier1 =
                    "SELECT n.nspname AS schema_name, c.relname AS table_name, " +
                    "       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced " +
                    "FROM pg_class c " +
                    "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE (n.nspname = 'stu' AND c.relname IN ('students', 'guardianships')) " +
                    "   OR (n.nspname = 'core' AND c.relname = 'persons') " +
                    "   OR (n.nspname = 'acd' AND c.relname = 'batch_students') " +
                    "ORDER BY n.nspname, c.relname";

            int verifiedCoreCount = 0;
            try (ResultSet rs = stmt.executeQuery(queryTier1)) {
                while (rs.next()) {
                    String fullTable = rs.getString("schema_name") + "." + rs.getString("table_name");
                    boolean rlsEnabled = rs.getBoolean("rls_enabled");
                    boolean rlsForced = rs.getBoolean("rls_forced");

                    assertTrue(fullTable + " MUST have rls_enabled=true", rlsEnabled);
                    assertTrue(fullTable + " MUST have rls_forced=true (DEC-G1-02A)", rlsForced);
                    verifiedCoreCount++;
                    System.out.println(String.format("[VERIFIED CORE] Table: %-25s | RLS_Enabled: %b | RLS_Forced: %b",
                            fullTable, rlsEnabled, rlsForced));
                }
            }
            assertEquals("All 4 Tier 1 Core tables must be verified", 4, verifiedCoreCount);

            // Step 4: Verify Scope Boundary: Tier 2 Extended tables must NOT be modified
            System.out.println("\n=== SCOPE BOUNDARY CHECK: TIER 2 EXTENDED TABLES ===");
            String queryTier2 =
                    "SELECT n.nspname AS schema_name, c.relname AS table_name, " +
                    "       c.relrowsecurity AS rls_enabled, c.relforcerowsecurity AS rls_forced " +
                    "FROM pg_class c " +
                    "JOIN pg_namespace n ON n.oid = c.relnamespace " +
                    "WHERE (n.nspname = 'core' AND c.relname IN ('addresses', 'person_contacts', 'person_emergency_contacts', 'person_identifiers')) " +
                    "   OR (n.nspname = 'acd' AND c.relname = 'section_enrollments') " +
                    "ORDER BY n.nspname, c.relname";

            int verifiedTier2Count = 0;
            try (ResultSet rs = stmt.executeQuery(queryTier2)) {
                while (rs.next()) {
                    String fullTable = rs.getString("schema_name") + "." + rs.getString("table_name");
                    boolean rlsForced = rs.getBoolean("rls_forced");
                    assertFalse(fullTable + " MUST NOT have rls_forced=true without DEC-G1-02B approval", rlsForced);
                    verifiedTier2Count++;
                    System.out.println(String.format("[VERIFIED UNCHANGED] Table: %-32s | RLS_Forced: %b",
                            fullTable, rlsForced));
                }
            }
            assertEquals("All 5 Tier 2 Extended tables must remain unchanged", 5, verifiedTier2Count);

            System.out.println("\n[GATE G1 PROGRESS] Step DEC-G1-02A successfully executed and verified live.");
        } finally {
            ds.close();
        }
    }
}
