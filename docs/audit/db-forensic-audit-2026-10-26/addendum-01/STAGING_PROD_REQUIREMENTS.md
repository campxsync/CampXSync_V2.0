# CampXSync DB Forensic Audit — Addendum 01
## STAGING_PROD_REQUIREMENTS: Read-Only Audit Specification for Staging & Production

**Audit Date:** 2026-10-26  
**Authorization Status:**  
- `STAGING_AUDIT_AUTHORIZED`: **NO**  
- `PRODUCTION_AUDIT_AUTHORIZED`: **NO**  
- **Action Taken:** In strict compliance with Safety Rules 0.3 and Task G instructions, **zero connections, queries, or inspections** were attempted against staging or production environments.

This document establishes the mandatory security prerequisites, the exact diagnostic query suite, and the unresolved architectural ambiguities regarding environment project references.

---

## 1. Read-Only Role & Connection Prerequisites

Prior to granting authorization for a read-only audit of staging or production, the database administrator must provision an unprivileged, read-only forensic audit role meeting these strict criteria:

### 1.1 Role Specification
```sql
-- Role Creation Template for Read-Only Audit
CREATE ROLE staging_audit_readonly WITH LOGIN PASSWORD '<secure-ephemeral-secret>' NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION;
ALTER ROLE staging_audit_readonly SET statement_timeout = '15s';

-- Read-Only Catalog & Data Access
GRANT pg_read_all_data TO staging_audit_readonly;
GRANT USAGE ON SCHEMA acd, audit, bud, cam, cfg, core, doc, exm, fin, hrm, iam, lms, obe, pay, plat, rpt, stu, sys, tax, wf TO staging_audit_readonly;
```

### 1.2 Mandatory Preflight Verification Query
Upon first connecting, the auditor MUST execute this verification check. If any privilege returns `true`, execution must abort immediately:
```sql
SELECT 
    rolname, 
    rolsuper, 
    rolcreaterole, 
    rolcreatedb,
    has_table_privilege(current_user, 'core.tenants', 'INSERT') AS can_insert,
    has_table_privilege(current_user, 'core.tenants', 'UPDATE') AS can_update,
    has_table_privilege(current_user, 'core.tenants', 'DELETE') AS can_delete
FROM pg_roles 
WHERE rolname = current_user;
-- MUST return: rolsuper=false, rolcreaterole=false, rolcreatedb=false, can_insert=false, can_update=false, can_delete=false
```

---

## 2. Standardized Audit Diagnostic Query Suite

When authorized, the auditor will execute the exact query battery established in Tasks A, B, and D:

### Query 1: Tenant Sanity & Target Environment Identification
```sql
SELECT current_database(), current_user, inet_server_addr(), inet_server_port();
SELECT id, code, name, is_active FROM core.tenants ORDER BY created_at LIMIT 5;
```

### Query 2: Authoritative Table Counts (Base vs Partitions)
```sql
SELECT 
    n.nspname AS schemaname,
    count(*) FILTER (WHERE c.relkind = 'r' AND NOT c.relispartition) AS ordinary_tables,
    count(*) FILTER (WHERE c.relkind = 'p') AS partitioned_parents,
    count(*) FILTER (WHERE c.relkind IN ('r', 'p') AND NOT c.relispartition) AS base_tables,
    count(*) FILTER (WHERE c.relispartition) AS child_partitions,
    count(*) AS total_tables
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
  AND c.relkind IN ('r', 'p')
GROUP BY n.nspname
ORDER BY n.nspname;
```

### Query 3: Constraint Counts (Separating Inherited Partition Clones)
```sql
SELECT 
    n.nspname AS schemaname,
    count(*) FILTER (WHERE co.contype = 'p' AND co.conparentid = 0) AS pk_base,
    count(*) FILTER (WHERE co.contype = 'p' AND co.conparentid <> 0) AS pk_inh,
    count(*) FILTER (WHERE co.contype = 'f' AND co.conparentid = 0) AS fk_base,
    count(*) FILTER (WHERE co.contype = 'f' AND co.conparentid <> 0) AS fk_inh,
    count(*) FILTER (WHERE co.contype = 'u') AS uq,
    count(*) FILTER (WHERE co.contype = 'c') AS ck,
    count(*) FILTER (WHERE co.contype = 'x') AS ex,
    count(*) FILTER (WHERE co.contype = 't') AS tg,
    count(*) FILTER (WHERE co.conparentid = 0) AS total_base_constraints,
    count(*) AS total_all_constraints
FROM pg_constraint co
JOIN pg_class cl ON cl.oid = co.conrelid
JOIN pg_namespace n ON n.oid = cl.relnamespace
WHERE n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
GROUP BY n.nspname
ORDER BY n.nspname;
```

### Query 4: Triggers Breakdown (Base vs Partition Clones)
```sql
SELECT 
    n.nspname AS schemaname,
    count(*) FILTER (WHERE t.tgparentid = 0) AS base_triggers,
    count(*) FILTER (WHERE t.tgparentid <> 0) AS inherited_triggers,
    count(*) AS total_triggers
FROM pg_trigger t
JOIN pg_class c ON c.oid = t.tgrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE NOT t.tgisinternal
  AND n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
GROUP BY n.nspname
ORDER BY n.nspname;
```

### Query 5: RLS Policies by Command (Capturing `ALL`)
```sql
SELECT 
    schemaname,
    count(*) FILTER (WHERE cmd = 'SELECT') AS select_policies,
    count(*) FILTER (WHERE cmd = 'INSERT') AS insert_policies,
    count(*) FILTER (WHERE cmd = 'UPDATE') AS update_policies,
    count(*) FILTER (WHERE cmd = 'DELETE') AS delete_policies,
    count(*) FILTER (WHERE cmd = 'ALL') AS all_policies,
    count(*) AS total_policies
FROM pg_policies
WHERE schemaname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
GROUP BY schemaname
ORDER BY schemaname;
```

### Query 6: Partition Row Counts & Default Partition Cleanliness
```sql
SELECT 
    table_schema, 
    table_name, 
    (xpath('/row/cnt/text()', xml_count))[1]::text::bigint AS row_count
FROM (
  SELECT 
    schemaname AS table_schema, 
    tablename AS table_name, 
    query_to_xml(format('SELECT count(*) AS cnt FROM %I.%I', schemaname, tablename), false, true, '') AS xml_count
  FROM pg_tables
  WHERE schemaname IN ('audit', 'cam', 'hrm', 'lms')
    AND (tablename LIKE '%_default' OR tablename ~ '.*_p[0-9]{6}$')
) t
ORDER BY table_schema, table_name;
```

### Query 7: Installed Extensions & Scheduler Availability
```sql
SELECT extname, extversion, nspname AS schema_name 
FROM pg_extension e JOIN pg_namespace n ON n.oid = e.extnamespace;

SELECT name, default_version, installed_version 
FROM pg_available_extensions WHERE name IN ('pg_cron', 'pg_net', 'pgmq');
```

---

## 3. Open Architectural Ambiguities: Staging Project Identity

Before staging can be audited, the Chief Architect must resolve the following open questions:

1. **Current Staging Project Reference:**
   - In `SCRIPT_AND_MIGRATION_INVENTORY.csv` and `database/migrations/V20261014_01` through `03`, project ref `kpwdvvcschankgggitnj` is explicitly designated as *"Legacy Staging"*.
   - However, other documentation references a *"future staging"* environment or mentions that staging was migrated.
   - **Required Input:** What is the authoritative active project ref for staging?
2. **Seed Data Divergence:**
   - Script `database/migrations/V20261014_02` hardcodes tenant ID `11111111-1111-1111-1111-111111111111` (`CAMPX_STAGING`).
   - Development uses `a0000000-0000-0000-0000-000000000001` (`DEV_TEST_TENANT`).
   - If staging was seeded with `11111111-...`, any integration tests or automated verification suites assuming `a0000000-...` will fail.
3. **Migration Sequence Alignment:**
   - Did staging run `V20261011_01` (the course RLS fix that dev never applied)?
   - Did staging run `V20261024_01` (Option C asymmetric curriculum RLS)?
   - Answering these questions requires executing the read-only query suite above against the confirmed staging ref once credentials are provided.
