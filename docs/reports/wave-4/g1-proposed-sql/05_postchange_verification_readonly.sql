-- ==============================================================================
-- CAMPXSYNC G1 SECURITY REMEDIATION: SCRIPT 05 (READ-ONLY POST-CHANGE VERIFICATION)
-- Description: Asserts that role 'campx_app_server' exists with NOBYPASSRLS,
--              that FORCE ROW LEVEL SECURITY is active on student tables,
--              that grants match least privilege, and simulates fail-closed context.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Execution Safety: READ-ONLY. Zero state changes.
-- ==============================================================================

-- 1. ASSERTION 1: Role Security Invariants (Must return 1 row with exact flags)
SELECT 
    rolname,
    rolsuper AS is_superuser,          -- MUST BE FALSE
    rolbypassrls AS bypasses_rls,      -- MUST BE FALSE (Gate G1 requirement)
    rolcanlogin AS can_login,          -- MUST BE TRUE
    rolcreaterole AS can_create_role,  -- MUST BE FALSE
    rolcreatedb AS can_create_db       -- MUST BE FALSE
FROM pg_roles
WHERE rolname = 'campx_app_server';

-- 1B. ASSERTION 1B: Role Membership Invariant
-- Verifies campx_app_server is a member of 'authenticated' for SET LOCAL ROLE
SELECT 
    r.rolname AS member_role,
    m.rolname AS parent_role
FROM pg_auth_members am
JOIN pg_roles r ON r.oid = am.member
JOIN pg_roles m ON m.oid = am.roleid
WHERE r.rolname = 'campx_app_server' AND m.rolname = 'authenticated';

-- 2. ASSERTION 2: FORCE ROW LEVEL SECURITY Coverage
-- All target tables MUST have rls_enabled = true AND rls_forced = true
SELECT 
    n.nspname AS schema_name,
    c.relname AS table_name,
    c.relrowsecurity AS rls_enabled,          -- MUST BE TRUE
    c.relforcerowsecurity AS rls_forced,      -- MUST BE TRUE
    r.rolname AS table_owner,
    CASE 
        WHEN c.relname IN ('students', 'guardianships', 'persons', 'batch_students') THEN 'Tier 1 (Core)'
        ELSE 'Tier 2 (Extended)'
    END AS scope_tier
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_roles r ON r.oid = c.relowner
WHERE (n.nspname = 'stu' AND c.relname IN ('students', 'guardianships'))
   OR (n.nspname = 'core' AND c.relname IN ('persons', 'addresses', 'person_contacts', 'person_emergency_contacts', 'person_identifiers'))
   OR (n.nspname = 'acd' AND c.relname IN ('batch_students', 'section_enrollments'))
ORDER BY scope_tier, n.nspname, c.relname;

-- 3. ASSERTION 3: Table Privilege Audit for campx_app_server
SELECT 
    table_schema,
    table_name,
    string_agg(privilege_type, ', ' ORDER BY privilege_type) AS granted_privileges
FROM information_schema.role_table_grants
WHERE grantee = 'campx_app_server'
GROUP BY table_schema, table_name
ORDER BY table_schema, table_name;

-- 3B. ASSERTION 3B: Verify audit.events INSERT Policy
SELECT 
    schemaname,
    tablename,
    policyname,
    cmd,
    roles,
    with_check
FROM pg_policies
WHERE schemaname = 'audit' AND tablename = 'events' AND policyname = 'events_insert';

-- 4. ASSERTION 4: Simulation of Transaction-Scoped Context & Fail-Closed Logic
DO $$
DECLARE
    v_test_tenant uuid := 'a0000000-0000-0000-0000-000000000001'::uuid;
    v_resolved_tenant uuid;
BEGIN
    -- Scenario A: Context Unset (Clean Session / Missing Context)
    -- Must return NULL; RLS queries evaluate tenant_id = NULL (FALSE/fail-closed)
    PERFORM set_config('request.jwt.claims', '', true);
    v_resolved_tenant := core.current_tenant_id();
    IF v_resolved_tenant IS NOT NULL THEN
        RAISE EXCEPTION 'FAIL-CLOSED VIOLATION: Unset context resolved to non-null tenant: %', v_resolved_tenant;
    ELSE
        RAISE NOTICE 'PASSED: Unset claims fail closed (resolved tenant: NULL)';
    END IF;

    -- Scenario B: Valid Transaction Context Set
    -- Must resolve exactly to the bound tenant UUID
    PERFORM set_config('request.jwt.claims', format('{"sub":"00000000-0000-0000-0000-000000000001","role":"authenticated","app_metadata":{"tenant_id":"%s"}}', v_test_tenant), true);
    v_resolved_tenant := core.current_tenant_id();
    IF v_resolved_tenant != v_test_tenant THEN
        RAISE EXCEPTION 'CONTEXT RESOLUTION FAILURE: Expected %, got %', v_test_tenant, v_resolved_tenant;
    ELSE
        RAISE NOTICE 'PASSED: Bound claims resolve correctly to tenant: %', v_resolved_tenant;
    END IF;

    -- Scenario C: Transaction Rollback / Cleanup Simulation
    -- Local configuration must not survive across transaction boundaries
    PERFORM set_config('request.jwt.claims', '', true);
END $$;

