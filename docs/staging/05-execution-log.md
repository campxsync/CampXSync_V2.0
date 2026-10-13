# Track A — Phase 3 Staging Execution Log

**Document**: `docs/staging/05-execution-log.md`  
**Date**: 2026-10-13  
**Target Project**: Supabase PostgreSQL Staging (`kpwdvvcschankgggitnj`)  
**MCP Target**: `https://mcp.supabase.com/mcp?project_ref=kpwdvvcschankgggitnj&read_only=true&features=database`  
**Safety Status**: Zero production access (`ekspahbsbwdpraaqfqui` strictly forbidden and isolated).

---

## 1. Execution Steps & Verified Evidence

| Step ID | Operation / Tool | Scope & Query | Target / Evidence | Result | Next Action |
| :---: | :--- | :--- | :--- | :--- | :--- |
| **P3-01** | `call_mcp_tool: execute_sql` | `SELECT current_database(), current_user, version();` | Hosted Supabase Staging | `current_user`: `supabase_read_only_user`<br>`current_database`: `postgres`<br>`version`: PostgreSQL 17.11 on aarch64-linux | Safe MCP read-only connection confirmed. |
| **P3-02** | `call_mcp_tool: execute_sql` | Query 1 & available extensions (`pg_available_extensions`) | Staging catalog | Installed: `pgcrypto` 1.3, `pg_trgm` 1.6, `btree_gist` 1.7.<br>`pg_cron` (1.6.4) & `pgmq` (1.5.1) available but NOT installed (`null`). | Finding 7 confirmed: `pg_cron` not installed. |
| **P3-03** | `call_mcp_tool: execute_sql` | Query 2: 20 ERP Schemas Presence | `pg_namespace` | All 20 schemas (`acd`, `audit`, `bud`, `cam`, `cfg`, `core`, `doc`, `exm`, `fin`, `hrm`, `iam`, `lms`, `obe`, `pay`, `plat`, `rpt`, `stu`, `sys`, `tax`, `wf`) returned `PRESENT`. | CRIT-03 VERIFIED COMPLETE. |
| **P3-04** | `call_mcp_tool: execute_sql` | Query 3: Table count across 20 schemas | `pg_class` (`relkind IN ('r', 'p')`) | Total tables: **458** across all 20 schemas. | CRIT-04 VERIFIED COMPLETE. |
| **P3-05** | `call_mcp_tool: execute_sql` | Query 4 & 5: Constraints Totals | `pg_constraint` | **458** Primary Keys (`p`), **403** Unique Constraints (`u`), **1,239** Foreign Keys (`f`). Exact match with design catalog! | CRIT-05 VERIFIED COMPLETE. |
| **P3-06** | `call_mcp_tool: execute_sql` | Existing Query 6: Index Counts | `pg_index` (`NOT EXISTS pg_constraint`) | **1,158** non-constraint indexes (2,026 total indexes: 458 PK + 665 unique non-PK + 903 regular non-unique). Exact match with catalog! | Catalog index verification VERIFIED COMPLETE. |
| **P3-07** | `call_mcp_tool: execute_sql` | Query A: Partition Grants & RLS (Finding 1) | `pg_partitioned_table`, `pg_inherits`, `pg_class` | 6 partitioned parents (`audit.access_events`, `change_log`, `events`, `cam.delivery_events`, `hrm.attendance_punches`, `lms.progress_events`).<br>- Default partitions: RLS enabled (`relrowsecurity = true`), deny-all.<br>- 24 monthly partitions (`*_p202610` to `*_p202701`): `relrowsecurity = false`, `auth_has_select = true`, `auth_has_insert = true`. | Finding 1 VERIFIED: monthly partitions lack RLS and have overprivileged grants to `authenticated`. |
| **P3-08** | `call_mcp_tool: execute_sql` | Query B: Index definitions on `iam.roles` & `iam.role_permissions` | `pg_indexes` | `iam.roles`: `uq_roles_tenant_id_code` (`WHERE deleted_at IS NULL AND college_id IS NULL`), `uq_roles_tenant_id_college_id_code` (`WHERE deleted_at IS NULL AND college_id IS NOT NULL`).<br>`iam.role_permissions`: `uq_role_permissions_tenant_id_role_id_permission_id`. | Finding 3 index structure VERIFIED. |
| **P3-09** | `call_mcp_tool: execute_sql` | Function introspection: `iam.seed_system_roles` | `pg_proc` | Uses `ON CONFLICT DO NOTHING` for inserting templates into `iam.roles` and `iam.role_permissions`. Deletes permissions from system roles if withdrawn from templates. | Finding 3 function behaviour VERIFIED. |
| **P3-10** | `call_mcp_tool: execute_sql` | Query C: Role Template counts | `iam.role_templates`, `iam.role_template_permissions` | `iam.role_templates`: 30 active rows.<br>`iam.role_template_permissions`: 2,138 active rows. | Role template baseline VERIFIED COMPLETE. |
| **P3-11** | `call_mcp_tool: execute_sql` | Query D: `acd.courses` RLS policies | `pg_policies` | Policies `courses_select`, `courses_insert`, `courses_update`, `courses_delete` actively check `acd01.view`, `acd01.write`, `acd01.manage`. | Migration `V20261011_01` VERIFIED ACTIVE on live staging! |
| **P3-12** | `call_mcp_tool: execute_sql` | Query 8 & 9: Foundational & Scope Functions | `pg_proc` | 9 foundational functions verified (`core.current_tenant_id`, `sys.ensure_partitions`, `iam.custom_access_token_hook`, etc.).<br>106 `sc_*` scope helpers in `iam`; 39 `own_/vis_` helpers in `core`. | CRIT-07 VERIFIED COMPLETE. |
| **P3-13** | `call_mcp_tool: execute_sql` | Query 10: RLS enabled/forced summary | `pg_class` | 434 tables with RLS enabled; 24 tables with RLS disabled (the 24 monthly partition child tables from Finding 1). 0 tables with forced RLS (intentional per Finding 6). | CRIT-10 PARTIALLY COMPLETE (Finding 1 fix required). |
| **P3-14** | `call_mcp_tool: execute_sql` | Query 13: `anon` role table grants | `information_schema.role_table_grants` | 0 rows returned. `anon` has zero table privileges across all 20 schemas. | CRIT-11 VERIFIED COMPLETE. |
| **P3-15** | `call_mcp_tool: execute_sql` | Query 14: `iam.custom_access_token_hook` grants | `has_function_privilege` | `supabase_auth_admin` has EXECUTE: `true`.<br>`anon`, `authenticated`, `service_role` have EXECUTE: `false`. | CRIT-08 VERIFIED COMPLETE. |
| **P3-16** | `call_mcp_tool: execute_sql` | Query 16 & 17: Duplicate role & permission checks | `iam.roles`, `iam.permissions` | 0 duplicate roles, 0 duplicate permissions. `iam.permissions` has exactly 547 active rows. | CRIT-12 & CRIT-13 VERIFIED COMPLETE. |
| **P3-17** | `call_mcp_tool: execute_sql` | Query 15 & Auth checks: Seed data state | `core.tenants`, `iam.user_profiles`, `auth.users` | `core.tenants`: 0 rows.<br>`iam.user_profiles`: 0 rows.<br>`auth.users`: 0 rows.<br>`iam.roles`: 0 rows.<br>`trg_seed_roles` trigger verified enabled on `core.tenants`. | CRIT-14 NOT STARTED (No test tenant provisioned yet). |
| **P3-18** | Local PowerShell script (`scratch/check_env.ps1`) | Rule 11 Boolean environment audit | Ambient process | `SUPABASE_USER`: Set=True, HasStaging=True, HasProd=False.<br>`SUPABASE_JDBC_URL`: Set=True, HasProd=False, IsPooler=True.<br>`CAMPX_SUPABASE_JWT_ISSUER`: Set=True, HasStaging=True, HasProd=False.<br>`CAMPX_TEST_ENV`: Set=True, IsStaging=True.<br>`SUPABASE_PASSWORD`: Set=True.<br>`CAMPX_TEST_TENANT_ID`: Set=False.<br>`CAMPX_TEST_USER_ID`: Set=False. | CRIT-15 & CRIT-16 VERIFIED COMPLETE. Previous Rule 11 production ref blocker resolved! |
| **P3-19** | Source audit (`60_rls.sql`) | Finding 5 (`sys.*` tables) inspection | `60_rls.sql` line 4 | 11 `sys.*` tables intentionally have RLS enabled with 0 policies; reserved strictly for `service_role` (which bypasses RLS) and backend jobs. Direct `authenticated` access intentionally denied. | Finding 5 confirmed as INTENTIONAL DESIGN. |
| **P3-20** | Source audit (`UserSecurityContext.java`) | Finding 2 (Connection & RLS test identity) | `UserSecurityContext.applyClaimsAndRole()` | Tests connect via session mode pooler as `postgres.<ref>`, but execute inside transactions running `SET LOCAL ROLE authenticated;` and `SELECT set_config('request.jwt.claims', ..., true);`. | Finding 2 resolved: tests enforce genuine non-admin RLS kernel evaluation without privileged bypass. |

---

## 2. Seven Findings Status Summary

1. **Finding 1 (Monthly Partitions Lack RLS)**: Confirmed. 24 monthly partition tables have RLS disabled and direct SELECT/INSERT granted to `authenticated`. Proposed fix awaiting user write approval (`PARTITION-RLS-FIX`).
2. **Finding 2 (Privileged PostgreSQL Connections & RLS Test Identity)**: Resolved. `UserSecurityContext.applyClaimsAndRole` executes `SET LOCAL ROLE authenticated` and sets `request.jwt.claims`, enforcing unprivileged kernel RLS during integration tests.
3. **Finding 3 (`seed_system_roles` Duplication & Deletion)**: Evaluated. Partial unique indexes on `iam.roles` enforce uniqueness for `(tenant_id, code)` where `college_id IS NULL` and `deleted_at IS NULL`. `ON CONFLICT DO NOTHING` protects against duplicates. Withdrawal deletion behavior is documented. Currently 0 roles and 0 duplicate rows.
4. **Finding 4 (`next_number` College Scoping)**: Recorded as backlog item. Same-tenant college-scoping issue; not a cross-tenant defect or staging blocker.
5. **Finding 5 (`sys` Tables Have RLS Without Policies)**: Resolved. Confirmed intentional design per `60_rls.sql`: reserved for `service_role` and internal workers; deny-all for normal roles is expected.
6. **Finding 6 (FORCE RLS Disabled)**: Preserved. Intentional design because security definer functions are owned by `postgres`.
7. **Finding 7 (Automatic Partition Creation Beyond Jan 2027)**: Confirmed. `pg_cron` is not installed; no cron jobs exist. Partition schedule proposal documented.

---

## 3. Pending Database Writes Awaiting User Approval

### Step 1: `GO PARTITION-RLS-FIX`
- **Verified Project Reference**: `kpwdvvcschankgggitnj` (verified via MCP `serverUrl` and `supabase_read_only_user` connection).
- **Exact SQL Statement**:
  ```sql
  DO $$
  DECLARE
    t text;
  BEGIN
    FOR t IN
      SELECT format('%I.%I', n.nspname, c.relname)
      FROM pg_inherits inh
      JOIN pg_class c ON c.oid = inh.inhrelid
      JOIN pg_class p ON p.oid = inh.inhparent
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname IN ('audit', 'cam', 'hrm', 'lms')
        AND c.relname LIKE '%_p20%'
    LOOP
      EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
      EXECUTE format('REVOKE ALL ON %s FROM anon, authenticated', t);
    END LOOP;
  END $$;
  ```
- **Purpose & Expected Effect**: Closes the RLS bypass on all 24 monthly partition children in `audit`, `cam`, `hrm`, and `lms`, bringing them to `relrowsecurity = true` and revoking direct table grants. All queries will route through parent tables where RLS policies are active.
- **Risk / Rollback**: Zero risk of data loss (tables are currently empty in staging). Rollback: `REVOKE` is idempotent; `ALTER TABLE ... DISABLE ROW LEVEL SECURITY` can revert if needed.

### Step 2: `GO PROVISION-TEST-TENANT`
- **Verified Project Reference**: `kpwdvvcschankgggitnj`
- **Exact SQL Statement**:
  ```sql
  INSERT INTO core.tenants (id, name, slug, status, created_at, updated_at)
  VALUES ('11111111-1111-1111-1111-111111111111'::uuid, 'CampX Staging Tenant', 'campx-staging', 'ACTIVE', now(), now())
  ON CONFLICT (id) DO NOTHING;
  ```
- **Purpose & Expected Effect**: Creates the foundational test tenant in `core.tenants`. This automatically activates trigger `trg_seed_roles`, invoking `iam.on_tenant_created()` to seed all 30 system role templates into `iam.roles` and 2,138 permissions into `iam.role_permissions`.
- **Risk / Rollback**: Isolated to test tenant UUID. Rollback: `DELETE FROM core.tenants WHERE id = '11111111-1111-1111-1111-111111111111'::uuid;`.
