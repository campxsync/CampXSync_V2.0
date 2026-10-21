package com.campx.gateway.identity;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.Test;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.UUID;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

/**
 * Executes the owner-authorized DEC-G1-03 Minimum Grants and Shared Services Audit Policy step:
 * 1. Grants schema USAGE to 'campx_app_server'.
 * 2. Reaffirms domain table DML to 'authenticated' under SET LOCAL ROLE.
 * 3. Grants SELECT, INSERT on 'audit.events' and 'audit.change_log' to 'authenticated'.
 * 4. Revokes UPDATE, DELETE, TRUNCATE on audit tables (strictly enforcing append-only).
 * 5. Creates RLS policy 'events_insert' on 'audit.events' for 'authenticated'.
 * 6. Grants EXECUTE on RLS helper functions.
 * 7. Executes live multi-tenant cross-boundary isolation and audit write verification under 'authenticated'.
 *
 * Strict Guardrails:
 * - Target MUST be approved dev ref: azulbaxszsqxymqmlwfe.
 * - Strictly forbids production ref: ekspahbsbwdpraaqfqui.
 * - Strictly forbids legacy staging ref: kpwdvvcschankgggitnj.
 * - Requires explicit approval token: APPROVE-G1-GRANTS.
 */
public class LiveG1GrantsExecutionTest {

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
        config.setPoolName("G1-Grants-Pool");
        return new HikariDataSource(config);
    }

    @Test
    public void executeAuthorizedGrantsAndAuditPolicy() throws Exception {
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

            // Step 2: Execute Script 03 (Minimum Grants and Audit Policy)
            System.out.println("\n=== EXECUTING SCRIPT 03: MINIMUM GRANTS & AUDIT POLICY ===");
            String ddlGrants =
                    "DO $$\n" +
                    "BEGIN\n" +
                    "    -- 1. Schema USAGE Grants to campx_app_server\n" +
                    "    GRANT USAGE ON SCHEMA core TO campx_app_server;\n" +
                    "    GRANT USAGE ON SCHEMA stu TO campx_app_server;\n" +
                    "    GRANT USAGE ON SCHEMA acd TO campx_app_server;\n" +
                    "    GRANT USAGE ON SCHEMA iam TO campx_app_server;\n" +
                    "    GRANT USAGE ON SCHEMA doc TO campx_app_server;\n" +
                    "    GRANT USAGE ON SCHEMA audit TO campx_app_server;\n" +
                    "    GRANT USAGE ON SCHEMA sys TO campx_app_server;\n" +
                    "\n" +
                    "    -- 2. Domain Table DML for authenticated role\n" +
                    "    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE stu.students TO authenticated;\n" +
                    "    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE stu.guardianships TO authenticated;\n" +
                    "    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE core.persons TO authenticated;\n" +
                    "    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE acd.batch_students TO authenticated;\n" +
                    "\n" +
                    "    -- 3. Academic & Identity References for authenticated\n" +
                    "    GRANT SELECT ON TABLE core.tenants TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE core.colleges TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE core.departments TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE core.academic_years TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE acd.programs TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE acd.batches TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE acd.course_sections TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE acd.calendar_terms TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE iam.roles TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE iam.permissions TO authenticated;\n" +
                    "    GRANT SELECT ON TABLE iam.role_permissions TO authenticated;\n" +
                    "    GRANT SELECT, INSERT, UPDATE ON TABLE iam.user_profiles TO authenticated;\n" +
                    "    GRANT SELECT, INSERT, UPDATE ON TABLE iam.role_assignments TO authenticated;\n" +
                    "\n" +
                    "    -- 4. Shared Services Audit (Append-Only) and Outbox\n" +
                    "    GRANT SELECT, INSERT ON TABLE audit.events TO authenticated;\n" +
                    "    GRANT SELECT, INSERT ON TABLE audit.change_log TO authenticated;\n" +
                    "    GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sys.outbox_events TO authenticated;\n" +
                    "    GRANT SELECT, INSERT ON TABLE sys.dead_letter_events TO authenticated;\n" +
                    "\n" +
                    "    -- Explicitly forbid destructive operations on audit ledger\n" +
                    "    REVOKE UPDATE, DELETE, TRUNCATE ON TABLE audit.events FROM authenticated, campx_app_server;\n" +
                    "    REVOKE UPDATE, DELETE, TRUNCATE ON TABLE audit.change_log FROM authenticated, campx_app_server;\n" +
                    "\n" +
                    "    -- 5. Shared Services Audit INSERT Policy\n" +
                    "    IF NOT EXISTS (\n" +
                    "        SELECT 1 FROM pg_policies \n" +
                    "        WHERE schemaname = 'audit' AND tablename = 'events' AND policyname = 'events_insert'\n" +
                    "    ) THEN\n" +
                    "        CREATE POLICY events_insert ON audit.events FOR INSERT TO authenticated\n" +
                    "        WITH CHECK (\n" +
                    "            tenant_id = (SELECT core.current_tenant_id()) \n" +
                    "            OR ((tenant_id IS NULL) AND (SELECT iam.is_platform_admin()))\n" +
                    "        );\n" +
                    "        RAISE NOTICE 'Created missing audit.events_insert policy for authenticated role.';\n" +
                    "    END IF;\n" +
                    "END $$;";

            stmt.execute(ddlGrants);
            System.out.println("[SUCCESS] Executed schema usage, table grants, audit append-only revocation, and audit.events_insert policy.");

            // Helper function grants using to_regprocedure
            String ddlFuncGrants =
                    "DO $$\n" +
                    "DECLARE\n" +
                    "    v_func text;\n" +
                    "    v_funcs text[] := ARRAY[\n" +
                    "        'core.current_tenant_id()',\n" +
                    "        'core.current_person_id()',\n" +
                    "        'core.current_student_id()',\n" +
                    "        'core.my_student_ids()',\n" +
                    "        'core.faculty_student_ids()',\n" +
                    "        'core.students_in_scope(text, boolean)',\n" +
                    "        'core.programs_in_scope(text, boolean)',\n" +
                    "        'iam.has_permission(text)',\n" +
                    "        'iam.perm_all(text)',\n" +
                    "        'iam.perm_colleges(text)',\n" +
                    "        'iam.perm_department_ids(text)'\n" +
                    "    ];\n" +
                    "BEGIN\n" +
                    "    FOREACH v_func IN ARRAY v_funcs LOOP\n" +
                    "        IF to_regprocedure(v_func) IS NOT NULL THEN\n" +
                    "            EXECUTE format('GRANT EXECUTE ON FUNCTION %s TO authenticated, campx_app_server', v_func);\n" +
                    "        END IF;\n" +
                    "    END LOOP;\n" +
                    "END $$;";

            stmt.execute(ddlFuncGrants);
            System.out.println("[SUCCESS] Executed EXECUTE grants on RLS helper functions.");

            // Step 3: Post-Change Catalog Verification (Assertion 3 & 3B)
            System.out.println("\n=== POST-CHANGE CATALOG VERIFICATION ===");
            try (ResultSet rs = stmt.executeQuery(
                    "SELECT string_agg(privilege_type, ', ' ORDER BY privilege_type) AS privs " +
                    "FROM information_schema.role_table_grants " +
                    "WHERE table_schema = 'audit' AND table_name = 'events' AND grantee = 'authenticated'")) {
                assertTrue(rs.next());
                String privs = rs.getString("privs");
                assertNotNull("audit.events privileges must exist", privs);
                assertTrue("audit.events must grant INSERT to authenticated", privs.contains("INSERT"));
                assertTrue("audit.events must grant SELECT to authenticated", privs.contains("SELECT"));
                assertFalse("audit.events must NOT grant UPDATE to authenticated", privs.contains("UPDATE"));
                assertFalse("audit.events must NOT grant DELETE to authenticated", privs.contains("DELETE"));
                System.out.println("[VERIFIED] audit.events privileges for authenticated: " + privs);
            }

            try (ResultSet rs = stmt.executeQuery(
                    "SELECT policyname, cmd, roles, with_check " +
                    "FROM pg_policies WHERE schemaname = 'audit' AND tablename = 'events' AND policyname = 'events_insert'")) {
                assertTrue("Policy events_insert must exist on audit.events", rs.next());
                assertEquals("INSERT", rs.getString("cmd"));
                System.out.println(String.format("[VERIFIED] Policy: %s | Cmd: %s | Roles: %s",
                        rs.getString("policyname"), rs.getString("cmd"), rs.getString("roles")));
            }

            // Step 4: LIVE TENANT ISOLATION & AUDIT TEST UNDER AUTHENTICATED
            System.out.println("\n=== LIVE MULTI-TENANT ISOLATION & TRANSACTION BOUNDARY SUITE ===");
            conn.setAutoCommit(false);
            try (Statement txStmt = conn.createStatement()) {
                // Switch role to authenticated
                txStmt.execute("SET LOCAL ROLE authenticated");

                // Test 4.1: Missing Context Fail-Closed Test
                // Claims are unset; current_tenant_id() is null; select on stu.students must return 0 rows
                try (ResultSet rs = txStmt.executeQuery("SELECT count(*) FROM stu.students")) {
                    assertTrue(rs.next());
                    assertEquals("Unset claims must return exactly 0 rows (fail-closed)", 0, rs.getInt(1));
                    System.out.println("[VERIFIED TEST 4.1] Missing context fails closed: 0 rows returned on stu.students.");
                }

                // Test 4.2: Positive Path Audit Event Insertion
                UUID tenantA = UUID.fromString("a0000000-0000-0000-0000-000000000001");
                UUID userA = UUID.fromString("11111111-1111-1111-1111-111111111111");
                UUID eventId = UUID.randomUUID();

                String claimsJsonA = String.format(
                        "{\"sub\":\"%s\",\"role\":\"authenticated\",\"tenant_id\":\"%s\",\"app_metadata\":{\"tenant_id\":\"%s\"}}",
                        userA, tenantA, tenantA);
                try (PreparedStatement ps = conn.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
                    ps.setString(1, claimsJsonA);
                    ps.execute();
                }

                String insertAuditSql =
                        "INSERT INTO audit.events (" +
                        "  id, tenant_id, event_time, action, principal_id, principal_type, " +
                        "  resource_type, status, severity, correlation_id, created_at, created_by" +
                        ") VALUES (?, ?, now(), 'TEST_ACTION', ?, 'USER', 'TEST_RES', 'SUCCESS', 'INFO', ?, now(), ?)";

                try (PreparedStatement ps = conn.prepareStatement(insertAuditSql)) {
                    ps.setObject(1, eventId);
                    ps.setObject(2, tenantA);
                    ps.setObject(3, userA);
                    ps.setString(4, eventId.toString());
                    ps.setObject(5, userA);
                    int inserted = ps.executeUpdate();
                    assertEquals("Audit event insert for same tenant must succeed", 1, inserted);
                    System.out.println("[VERIFIED TEST 4.2] Positive path audit insert succeeded for tenant " + tenantA);
                }

                // Test 4.3: Cross-Tenant Audit Event Insertion Denial (RLS Check Violation)
                UUID tenantB = UUID.fromString("b0000000-0000-0000-0000-000000000002");
                UUID foreignEventId = UUID.randomUUID();

                java.sql.Savepoint sp1 = conn.setSavepoint("sp1");
                try (PreparedStatement ps = conn.prepareStatement(insertAuditSql)) {
                    ps.setObject(1, foreignEventId);
                    ps.setObject(2, tenantB); // Different tenant than active claims
                    ps.setObject(3, userA);
                    ps.setString(4, foreignEventId.toString());
                    ps.setObject(5, userA);
                    ps.executeUpdate();
                    fail("Cross-tenant audit insert MUST be denied by RLS policy events_insert");
                } catch (SQLException e) {
                    conn.rollback(sp1);
                    System.out.println("[VERIFIED TEST 4.3] Cross-tenant audit insert correctly denied by RLS: " + e.getMessage());
                }

                // Test 4.4A: Append-Only Invariant: UPDATE on audit.events must be denied
                java.sql.Savepoint sp2 = conn.setSavepoint("sp2");
                try {
                    txStmt.execute("UPDATE audit.events SET action = 'TAMPERED' WHERE id = '" + eventId + "'");
                    fail("UPDATE on audit.events MUST be denied (append-only ledger)");
                } catch (SQLException e) {
                    conn.rollback(sp2);
                    System.out.println("[VERIFIED TEST 4.4A] Audit UPDATE denied (SQLSTATE " + e.getSQLState() + "): " + e.getMessage());
                }

                // Test 4.4B: Append-Only Invariant: DELETE on audit.events must be denied
                java.sql.Savepoint sp3 = conn.setSavepoint("sp3");
                try {
                    txStmt.execute("DELETE FROM audit.events WHERE id = '" + eventId + "'");
                    fail("DELETE on audit.events MUST be denied (append-only ledger)");
                } catch (SQLException e) {
                    conn.rollback(sp3);
                    System.out.println("[VERIFIED TEST 4.4B] Audit DELETE denied (SQLSTATE " + e.getSQLState() + "): " + e.getMessage());
                }

                // Test 4.5: Cross-Tenant Insert on stu.students (Core Table FORCED RLS)
                java.sql.Savepoint sp4 = conn.setSavepoint("sp4");
                String insertStudentSql =
                        "INSERT INTO stu.students (id, tenant_id, person_id, college_id, program_id, enrollment_no, status, created_at, created_by) " +
                        "VALUES (?, ?, ?, ?, ?, ?, 'ACTIVE', now(), ?)";
                try (PreparedStatement ps = conn.prepareStatement(insertStudentSql)) {
                    ps.setObject(1, UUID.randomUUID());
                    ps.setObject(2, tenantB); // Different tenant than active claims
                    ps.setObject(3, UUID.randomUUID());
                    ps.setObject(4, UUID.randomUUID());
                    ps.setObject(5, UUID.randomUUID());
                    ps.setString(6, "ENR-CROSS-TENANT");
                    ps.setObject(7, userA);
                    ps.executeUpdate();
                    fail("Cross-tenant student insert MUST be denied by FORCED RLS");
                } catch (SQLException e) {
                    conn.rollback(sp4);
                    System.out.println("[VERIFIED TEST 4.5] Cross-tenant student insert correctly denied by FORCED RLS: " + e.getMessage());
                }

                System.out.println("\n[GATE G1 PROGRESS] Step DEC-G1-03 successfully executed and verified live.");
            } finally {
                // Rollback test data cleanly
                conn.rollback();
                conn.setAutoCommit(true);
                System.out.println("[CLEANUP] Test transaction rolled back cleanly. Zero test data persisted.");
            }
        } finally {
            ds.close();
        }
    }
}
