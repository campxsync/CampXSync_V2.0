-- ======================================================================================
-- CampXSync Staging Database Read-Only Verification Script
-- Target Environment: Supabase PostgreSQL Staging (project ref: kpwdvvcschankgggitnj)
-- File: docs/staging/verify-readonly.sql
--
-- SAFETY NOTICE:
-- 1. This script is 100% SELECT-ONLY. Zero DDL (CREATE/ALTER/DROP), zero DML (INSERT/UPDATE/DELETE),
--    zero administrative transactions (DO blocks, SET commands, TRUNCATE).
-- 2. It inspects PostgreSQL system catalogs (pg_namespace, pg_class, pg_proc, pg_policy,
--    pg_constraint, pg_index, pg_partitioned_table, information_schema).
-- 3. Run this script in the Supabase SQL Editor and copy-paste the output by Query number.
-- ======================================================================================

-- ======================================================================================
-- Query 1: Extensions Installed
-- Expected: pgcrypto, pg_trgm, btree_gist present and installed.
-- ======================================================================================
SELECT 
    extname AS extension_name,
    extversion AS installed_version,
    extnamespace::regnamespace::text AS installed_schema
FROM pg_extension
WHERE extname IN ('pgcrypto', 'pg_trgm', 'btree_gist', 'pg_cron', 'pgmq', 'pgsodium', 'vault')
ORDER BY extname;

-- ======================================================================================
-- Query 2: Schemas Verification (20 Application Schemas)
-- Expected: Exactly 20 application schemas: acd, audit, bud, cam, cfg, core, doc,
--           exm, fin, hrm, iam, lms, obe, pay, plat, rpt, stu, sys, tax, wf.
-- ======================================================================================
WITH expected_schemas AS (
    SELECT unnest(ARRAY[
        'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
        'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
    ]) AS schema_name
)
SELECT 
    e.schema_name,
    CASE WHEN n.nspname IS NOT NULL THEN 'PRESENT' ELSE 'MISSING' END AS status
FROM expected_schemas e
LEFT JOIN pg_namespace n ON n.nspname = e.schema_name
ORDER BY e.schema_name;

-- ======================================================================================
-- Query 3: Table Counts & High-Level Row Counts Per Schema
-- Expected: ~458 tables across all 20 schemas (matching relational_constraint_index_catalog.md).
-- ======================================================================================
SELECT 
    n.nspname AS schema_name,
    count(c.relname) AS table_count,
    sum(coalesce(c.reltuples, 0)::bigint) AS estimated_total_rows
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
)
AND c.relkind IN ('r', 'p') -- regular tables and partitioned tables
GROUP BY n.nspname
ORDER BY n.nspname;

-- ======================================================================================
-- Query 4: Relational Constraints Summary Per Schema (PK, Unique, Foreign Key)
-- Expected Totals from Catalog: PK = 458, UQ = 403, FK = 1239
-- ======================================================================================
SELECT 
    n.nspname AS schema_name,
    count(con.oid) FILTER (WHERE con.contype = 'p') AS pk_count,
    count(con.oid) FILTER (WHERE con.contype = 'u') AS uq_count,
    count(con.oid) FILTER (WHERE con.contype = 'f') AS fk_count,
    count(con.oid) FILTER (WHERE con.contype = 'c') AS check_count,
    count(con.oid) AS total_constraints
FROM pg_namespace n
JOIN pg_class c ON c.relnamespace = n.oid
JOIN pg_constraint con ON con.conrelid = c.oid
WHERE n.nspname IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
)
GROUP BY n.nspname
ORDER BY n.nspname;

-- ======================================================================================
-- Query 5: Global Constraint Catalog Comparison Totals
-- Compare with relational_constraint_index_catalog.md: PK: 458, UQ: 403, FK: 1239
-- ======================================================================================
SELECT 
    count(con.oid) FILTER (WHERE con.contype = 'p') AS actual_pk_total,
    458 AS catalog_expected_pk,
    count(con.oid) FILTER (WHERE con.contype = 'u') AS actual_uq_total,
    403 AS catalog_expected_uq,
    count(con.oid) FILTER (WHERE con.contype = 'f') AS actual_fk_total,
    1239 AS catalog_expected_fk
FROM pg_constraint con
JOIN pg_class c ON c.oid = con.conrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
);

-- ======================================================================================
-- Query 6: Non-Constraint Indexes Per Schema
-- Compare with relational_constraint_index_catalog.md total: 1158 non-constraint indexes
-- ======================================================================================
SELECT 
    n.nspname AS schema_name,
    count(i.indexrelid) AS non_constraint_indexes_count
FROM pg_index i
JOIN pg_class c ON c.oid = i.indrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
)
AND NOT i.indisprimary
AND NOT i.indisunique
GROUP BY n.nspname
ORDER BY n.nspname;

-- ======================================================================================
-- Query 7: Partitioned Parent Tables & Child Partition Attachment
-- Expected: audit.change_log, audit.events, audit.access_events, lms.progress_events,
--           hrm.attendance_punches, cam.delivery_events partitioned with child tables.
-- ======================================================================================
SELECT 
    parent_ns.nspname AS parent_schema,
    parent.relname AS parent_table,
    count(child.relname) AS child_partition_count,
    string_agg(child.relname, ', ' ORDER BY child.relname) FILTER (WHERE child.relname IS NOT NULL) AS sample_partitions
FROM pg_partitioned_table pt
JOIN pg_class parent ON parent.oid = pt.partrelid
JOIN pg_namespace parent_ns ON parent_ns.oid = parent.relnamespace
LEFT JOIN pg_inherits inh ON inh.inhparent = parent.oid
LEFT JOIN pg_class child ON child.oid = inh.inhrelid
WHERE parent_ns.nspname IN ('audit', 'lms', 'hrm', 'cam')
GROUP BY parent_ns.nspname, parent.relname
ORDER BY parent_ns.nspname, parent.relname;

-- ======================================================================================
-- Query 8: Foundational Functions & Scope Functions Verification
-- Expected: core.current_tenant_id(), sys.ensure_partitions(), iam.custom_access_token_hook(),
--           plus iam.sc_* and core.own_* scope functions.
-- ======================================================================================
SELECT 
    n.nspname AS function_schema,
    p.proname AS function_name,
    pg_get_function_identity_arguments(p.oid) AS arguments,
    pg_get_function_result(p.oid) AS return_type,
    p.prosecdef AS is_security_definer
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE (n.nspname = 'core' AND p.proname IN ('current_tenant_id', 'can_access_college', 'next_number'))
   OR (n.nspname = 'sys' AND p.proname = 'ensure_partitions')
   OR (n.nspname = 'iam' AND p.proname IN ('custom_access_token_hook', 'seed_system_roles', 'has_permission', 'is_platform_admin'))
   OR (n.nspname = 'cfg' AND p.proname = 'effective_setting')
ORDER BY n.nspname, p.proname;

-- ======================================================================================
-- Query 9: Scope Functions Inventory Summary
-- Expected: 130+ scope functions across iam and core schemas.
-- ======================================================================================
SELECT 
    n.nspname AS schema_name,
    count(*) AS total_functions,
    count(*) FILTER (WHERE p.proname LIKE 'sc_%') AS scope_helper_count,
    count(*) FILTER (WHERE p.proname LIKE 'own_%' OR p.proname LIKE 'vis_%') AS ownership_helper_count
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
WHERE n.nspname IN ('iam', 'core')
GROUP BY n.nspname
ORDER BY n.nspname;

-- ======================================================================================
-- Query 10: Row Level Security (RLS) Status Across All Tables
-- Expected: relrowsecurity = TRUE and relforcerowsecurity = TRUE on ALL tables.
-- Any table with unforced or disabled RLS is a severe defect!
-- ======================================================================================
SELECT 
    n.nspname AS schema_name,
    count(c.relname) AS total_tables,
    count(c.relname) FILTER (WHERE c.relrowsecurity) AS rls_enabled_count,
    count(c.relname) FILTER (WHERE c.relforcerowsecurity) AS rls_forced_count,
    count(c.relname) FILTER (WHERE NOT c.relrowsecurity) AS unshielded_tables_count,
    count(c.relname) FILTER (WHERE c.relrowsecurity AND NOT c.relforcerowsecurity) AS rls_not_forced_count
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
)
AND c.relkind IN ('r', 'p')
GROUP BY n.nspname
ORDER BY n.nspname;

-- ======================================================================================
-- Query 11: Policy Counts Per Schema (pg_policies)
-- ======================================================================================
SELECT 
    schemaname,
    count(DISTINCT tablename) AS tables_with_policies,
    count(policyname) AS total_policies_count
FROM pg_policies
WHERE schemaname IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
)
GROUP BY schemaname
ORDER BY schemaname;

-- ======================================================================================
-- Query 12: Views Verification
-- Expected: Exactly 14 application views from 70_views.sql.
-- ======================================================================================
SELECT 
    table_schema,
    table_name,
    view_definition IS NOT NULL AS has_definition,
    check_option
FROM information_schema.views
WHERE table_schema IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
)
ORDER BY table_schema, table_name;

-- ======================================================================================
-- Query 13: Data API Grants: Verify "anon" Role Isolation
-- Expected: anon MUST have ZERO permissions on ERP application tables!
-- ======================================================================================
SELECT 
    table_schema,
    table_name,
    privilege_type
FROM information_schema.role_table_grants
WHERE grantee = 'anon'
  AND table_schema IN (
    'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
    'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
  );

-- ======================================================================================
-- Query 14: Function EXECUTE Grants: Verify iam.custom_access_token_hook
-- Expected: supabase_auth_admin has EXECUTE; public, anon, authenticated DO NOT.
-- ======================================================================================
SELECT 
    p.proname AS function_name,
    r.rolname AS grantee,
    has_function_privilege(r.rolname, p.oid, 'EXECUTE') AS has_execute_privilege
FROM pg_proc p
JOIN pg_namespace n ON n.oid = p.pronamespace
CROSS JOIN (
    SELECT rolname FROM pg_roles WHERE rolname IN ('public', 'anon', 'authenticated', 'service_role', 'supabase_auth_admin')
) r
WHERE n.nspname = 'iam' AND p.proname = 'custom_access_token_hook'
ORDER BY r.rolname;

-- ======================================================================================
-- Query 15: Seeded Tenants, Colleges & Users
-- Check existing seed rows in core tables.
-- ======================================================================================
SELECT 
    'core.tenants' AS entity_table,
    count(*) AS total_count,
    count(*) FILTER (WHERE deleted_at IS NULL) AS active_count
FROM core.tenants
UNION ALL
SELECT 
    'core.colleges' AS entity_table,
    count(*) AS total_count,
    count(*) FILTER (WHERE deleted_at IS NULL) AS active_count
FROM core.colleges
UNION ALL
SELECT 
    'iam.user_profiles' AS entity_table,
    count(*) AS total_count,
    count(*) FILTER (WHERE deleted_at IS NULL AND status = 'ACTIVE') AS active_count
FROM iam.user_profiles;

-- ======================================================================================
-- Query 16: Duplicate Role Detection (Crucial Rule 7 / Staging Check)
-- Expected: 0 rows returned. Any row indicates seed role duplication!
-- ======================================================================================
SELECT 
    coalesce(tenant_id::text, 'GLOBAL') AS tenant_scope,
    code AS role_code,
    name AS role_name,
    count(*) AS duplicate_count
FROM iam.roles
WHERE deleted_at IS NULL
GROUP BY tenant_id, code, name
HAVING count(*) > 1;

-- ======================================================================================
-- Query 17: Duplicate Permissions Detection
-- Expected: 0 rows returned.
-- ======================================================================================
SELECT 
    code AS permission_code,
    module,
    action,
    count(*) AS duplicate_count
FROM iam.permissions
WHERE deleted_at IS NULL
GROUP BY code, module, action
HAVING count(*) > 1;

-- ======================================================================================
-- Query 18: Migration History & Bootstrap Tracking
-- Check if migration tables or bootstrap logs exist.
-- ======================================================================================
SELECT 
    schemaname,
    tablename
FROM pg_tables
WHERE tablename IN ('schema_migrations', 'flyway_schema_history', 'bootstrap_log', 'system_bootstrap_log')
   OR schemaname = 'audit' AND tablename LIKE '%bootstrap%';

-- ======================================================================================
-- Query 19: ACD-01 Course Management RLS Policy Audit
-- Check whether original policies or hardened migration policies (V20261011_01) are active.
-- ======================================================================================
SELECT 
    schemaname,
    tablename,
    policyname,
    permissive,
    roles,
    cmd
FROM pg_policies
WHERE schemaname = 'acd' AND tablename = 'courses'
ORDER BY policyname;

-- ======================================================================================
-- Query 20: Overall Staging Schema Health Rollup
-- Summary scorecard checking core platform prerequisites.
-- ======================================================================================
SELECT 
    (SELECT count(*) FROM pg_namespace WHERE nspname IN (
        'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
        'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
    )) AS application_schemas_present,
    (SELECT count(*) FROM pg_class c JOIN pg_namespace n ON n.oid = c.relnamespace 
     WHERE n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
     AND c.relkind IN ('r', 'p')) AS total_tables_present,
    (SELECT count(*) FROM pg_constraint con JOIN pg_class c ON c.oid = con.conrelid JOIN pg_namespace n ON n.oid = c.relnamespace
     WHERE n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
     AND con.contype = 'p') AS total_primary_keys,
    (SELECT count(*) FROM pg_proc p JOIN pg_namespace n ON n.oid = p.pronamespace
     WHERE n.nspname = 'iam' AND p.proname = 'custom_access_token_hook') AS auth_hook_function_present,
    (SELECT count(*) FROM information_schema.views WHERE table_schema IN (
        'acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin',
        'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf'
    )) AS application_views_present;
