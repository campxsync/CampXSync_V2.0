-- ==============================================================================
-- CAMPXSYNC G1 SECURITY REMEDIATION: SCRIPT 01 (READ-ONLY PREFLIGHT)
-- Description: Introspects database connection identity, role attributes,
--              table ownership, RLS flags (enabled & forced), and grants.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Execution Safety: READ-ONLY. Zero state changes.
-- ==============================================================================

-- 1. Verify Connection Identity & Target Database
SELECT 
    current_database() AS current_database,
    current_user AS session_user,
    session_user AS physical_login_user,
    inet_server_addr() AS server_ip,
    inet_server_port() AS server_port,
    version() AS postgres_version;

-- 2. Inspect Target Tenant & College Presence (Non-Production Verification)
SELECT 
    t.id AS tenant_id,
    t.code AS tenant_code,
    t.name AS tenant_name,
    c.id AS college_id,
    c.code AS college_code,
    c.name AS college_name
FROM core.tenants t
LEFT JOIN core.colleges c ON c.tenant_id = t.id AND c.deleted_at IS NULL
WHERE t.deleted_at IS NULL
ORDER BY t.created_at ASC;

-- 3. Audit Role Attributes for Connecting Role & Application Candidates
-- Checks for BYPASSRLS, SUPERUSER, and LOGIN flags
SELECT 
    rolname,
    rolsuper AS is_superuser,
    rolinherit AS can_inherit,
    rolcreaterole AS can_create_role,
    rolcreatedb AS can_create_db,
    rolcanlogin AS can_login,
    rolreplication AS can_replicate,
    rolbypassrls AS bypasses_rls,
    rolconnlimit AS conn_limit
FROM pg_roles
WHERE rolname IN ('postgres', 'authenticated', 'anon', 'service_role', 'campx_app_server')
ORDER BY rolname;

-- 4. Audit Table Ownership, RLS Enabled & RLS Forced Flags
-- For all student and associated core/academic operational tables
SELECT 
    n.nspname AS schema_name,
    c.relname AS table_name,
    r.rolname AS table_owner,
    c.relrowsecurity AS rls_enabled,
    c.relforcerowsecurity AS rls_forced,
    c.relkind AS object_type
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
JOIN pg_roles r ON r.oid = c.relowner
WHERE n.nspname IN ('stu', 'core', 'acd', 'iam', 'audit', 'sys')
  AND c.relname IN (
      'students', 'guardianships',
      'persons', 'addresses', 'person_identifiers', 'person_contacts', 'person_emergency_contacts',
      'batches', 'batch_students', 'course_sections', 'section_enrollments', 'programs', 'colleges',
      'user_profiles', 'role_assignments',
      'events', 'change_log', 'outbox_events'
  )
ORDER BY n.nspname, c.relname;

-- 5. Audit Existing Row-Level Security Policies on Student & Core Tables
SELECT 
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd,
    qual AS using_expression,
    with_check AS with_check_expression
FROM pg_policies
WHERE (schemaname IN ('stu', 'core', 'acd') AND tablename IN ('students', 'guardianships', 'persons', 'batch_students', 'section_enrollments', 'addresses', 'person_contacts', 'person_identifiers'))
   OR (schemaname = 'audit' AND tablename = 'events')
ORDER BY schemaname, tablename, policyname;

-- 6. Audit Direct Table Privileges Granted to 'authenticated' and 'anon'
SELECT 
    table_schema,
    table_name,
    grantee,
    string_agg(privilege_type, ', ' ORDER BY privilege_type) AS granted_privileges
FROM information_schema.role_table_grants
WHERE (table_schema IN ('stu', 'core', 'acd') AND table_name IN ('students', 'guardianships', 'persons', 'batch_students'))
   OR (table_schema = 'audit' AND table_name = 'events')
  AND grantee IN ('authenticated', 'anon', 'PUBLIC', 'campx_app_server')
GROUP BY table_schema, table_name, grantee
ORDER BY table_schema, table_name, grantee;

-- 7. Audit Availability of Helper Functions Used in Student RLS Policies
SELECT 
    n.nspname AS schema_name,
    p.proname AS function_name,
    pg_get_function_identity_arguments(p.oid) AS arguments,
    pg_get_function_result(p.oid) AS return_type,
    p.prosecdef AS is_security_definer,
    r.rolname AS function_owner
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
JOIN pg_roles r ON r.oid = p.proowner
WHERE (n.nspname = 'core' AND p.proname IN ('current_tenant_id', 'current_person_id', 'current_student_id', 'my_student_ids', 'faculty_student_ids', 'students_in_scope', 'programs_in_scope'))
   OR (n.nspname = 'iam' AND p.proname IN ('has_permission', 'perm_all', 'perm_colleges', 'perm_department_ids'))
ORDER BY n.nspname, p.proname;

-- 8. Audit View Security: Verify whether views enforce security_invoker = true
SELECT 
    schemaname,
    viewname,
    viewowner,
    definition
FROM pg_views
WHERE schemaname IN ('stu', 'core', 'acd')
ORDER BY schemaname, viewname;

