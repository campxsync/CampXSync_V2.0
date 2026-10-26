# CampXSync — Schema Differences Report
## Script vs. Live Database vs. Application Code Reconciliation

**Audit Date:** 2026-10-26  
**Auditor:** Antigravity Agentic AI (Read-Only Forensic Mode)  
**Target Environment:** `CampXSync-Architecture-Dev` (Supabase Dev ref: `azulbaxszsqxymqmlwfe`)  

---

## 1. Discrepancy Matrix Summary

This section details every material difference discovered between the original bootstrap scripts (`Documentation/DatabaseDesign/Supabase/Scripts_V3.0/`), the live Supabase development database, and the active application codebase (`feature/java-backend-integration`).

| Finding ID | Schema / Module | Object Name & Type | Discrepancy Summary | Live Database State | Application Expectation | Recommended Disposition |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **DIFF-01** | `acd` (ACD-01) | `acd.courses` (Policies) | Permission code mismatch bug (`acd03.*` vs `acd01.*`) | **BUGGY**: Live policies check `acd03.*` | Expects `acd01.view`, `acd01.write`, `acd01.manage` | **Apply Fix Migration** |
| **DIFF-02** | `acd` (ACD-01) | `acd.course_program_map` (Policies) | Symmetric vs Asymmetric curriculum mapping | **UPDATED**: Option C asymmetric RLS active | Expects asymmetric curriculum authority | **Retain Live Definition** |
| **DIFF-03** | `core` (Foundation) | `core.is_active_course_in_tenant` (Func) | Missing helper function in bootstrap scripts | **PRESENT**: Security-definer helper active | Required by Option C curriculum RLS | **Codify in Baseline** |
| **DIFF-04** | `sys` (Shared) | `sys.outbox_events` (Policies) | Bootstrap had 0 policies; outbox failed closed | **UPDATED**: 4 tenant-scoped RLS policies active | All Java services write to `sys.outbox_events` | **Retain Live Definition** |
| **DIFF-05** | `sys` (Shared) | Schema `sys` (Grants) | Bootstrap had 0 USAGE grants to app roles | **UPDATED**: USAGE granted to `authenticated` | Java services require schema USAGE | **Retain Live Definition** |
| **DIFF-06** | `audit` (Shared) | `audit.events` (Policy & Grants) | Bootstrap lacked INSERT policy; append-only grants | **UPDATED**: `events_insert` active; DML restricted | Shared logger writes immutable audit records | **Retain Live Definition** |
| **DIFF-07** | `core` / `stu` / `acd` | Tier 1 Tables (RLS Setting) | Bootstrap had standard RLS; Gate G1 enforced FORCE RLS | **UPDATED**: `relforcerowsecurity = true` on 4 tables | Security requires table owner RLS compliance | **Codify in Baseline** |
| **DIFF-08** | `sys` (Partitioning) | `sys.ensure_partitions` (Function) | Catalog function lacks `ENABLE ROW LEVEL SECURITY` | **OUTDATED**: Function lacks RLS enablement | Dynamic partitions require automatic RLS | **Update Function** |
| **DIFF-09** | `sys` (Shared) | 10 `sys` Tables (Policies) | Tables have RLS enabled with 0 policies | **FAIL-CLOSED**: Non-superusers denied access | Dead letter & inbox consumers require policies | **Add RLS Policies** |
| **DIFF-10** | `iam` (Auth Hook) | `iam.custom_access_token_hook` (Func) | Hook omits `user_role` and `college_id` claims | **LIMITED**: Only injects `tenant_id` | Gateway forced to run fallback JDBC resolution | **Enhance Auth Hook** |
| **DIFF-11** | Partition Horizon | 6 Partitioned Tables (Partitions) | Partitions only provisioned through 2027-01 | **EXPIRING**: Partitions end 2027-02-01 | Operational stability beyond 2027-01 | **Execute Partition Job** |
| **DIFF-12** | Database Meta | `supabase_migrations` (Table) | Zero migration history tables in database | **ABSENT**: No migration runner metadata | CI/CD and deployment require versioning | **Initialize Migration Engine**|
| **DIFF-13** | Seed Alignment | `core.tenants` / `core.colleges` | Seed scripts hardcoded for legacy staging ref | **MISMATCHED**: Dev uses `DEV_TEST_TENANT` | Domain tests require clean baseline tenant | **Reconcile Seed Scripts** |

---

## 2. In-Depth Discrepancy Findings

### Finding DIFF-01: Unapplied RLS Permission Code Bug on `acd.courses`
* **Finding ID:** `DIFF-01`
* **Schema / Module:** `acd` / ACD-01 Course Management Service
* **Object Name & Type:** `acd.courses` (Row-Level Security Policies: `courses_select`, `courses_insert`, `courses_update`, `courses_delete`)
* **Original Definition / Expected State:** The migration `database/migrations/V20261011_01__fix_acd01_courses_rls_policies.sql` replaces the original policies with checks for `'acd01.view'`, `'acd01.write'`, and `'acd01.manage'`.
* **Actual Live Catalog State:**
  ```sql
  -- Actual live policy discovered on acd.courses:
  qual: ((tenant_id = (SELECT core.current_tenant_id())) AND 
        ((SELECT iam.perm_all('acd03.view'::text)) OR 
        (department_id IN (SELECT unnest(iam.perm_department_ids('acd03.view'::text)))) OR 
        ((NOT false) AND (department_id IS NULL) AND (SELECT iam.has_permission('acd03.view'::text)))))
  ```
  The live database is STILL running the original bootstrap policies from `60_rls.sql` (Line 183).
* **Current Application Expectation:** `CourseManagementService` (ACD-01) issues queries expecting users holding permission `acd01.view` (Course Management View) to be authorized. Currently, any non-administrator user holding only `acd01.view` is blocked by PostgreSQL kernel RLS unless they also happen to hold `acd03.view` (Subject Management).
* **Evidence Source:** `SELECT policyname, qual FROM pg_policies WHERE schemaname = 'acd' AND tablename = 'courses';`
* **Impact:** **HIGH (Security & Functional Defect)**. Non-admin academic coordinators are denied access to courses.
* **Recommended Disposition:** **Apply Fix Migration**. Codify `V20261011_01` into the new authoritative baseline.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-02: Asymmetric Curriculum RLS on `acd.course_program_map`
* **Finding ID:** `DIFF-02`
* **Schema / Module:** `acd` / ACD-01 Course Management Service
* **Object Name & Type:** `acd.course_program_map` (RLS Policies: `course_program_map_insert`, `course_program_map_update`, `course_program_map_delete`)
* **Original Definition / Expected State:** Bootstrap script `60_rls.sql` (Lines 232–260) enforced symmetric permission checking requiring the caller to own both the course AND the program:
  ```sql
  -- Original bootstrap policy:
  WITH CHECK ((program_id IN (SELECT core.programs_in_scope('acd01.write', true))) AND 
              (course_id IN (SELECT core.courses_in_scope('acd01.write', true))))
  ```
* **Actual Live Catalog State:**
  ```sql
  -- Live policy verified on acd.course_program_map:
  WITH CHECK ((tenant_id = (SELECT core.current_tenant_id())) AND 
              core.is_active_course_in_tenant(course_id) AND 
              ((SELECT iam.perm_all('acd01.write'::text)) OR 
               (program_id IN (SELECT core.programs_in_scope('acd01.write'::text, true)))))
  ```
  This reflects the Option C Asymmetric Curriculum Membership model applied via `V20261024_01__asymmetric_course_program_map_rls.sql`.
* **Current Application Expectation:** Allows a Department Head (e.g., Computer Science) to include an active service course (e.g., Mathematics `MATH101` from the Science Department) into their academic program curriculum without requiring write privileges on the Mathematics department's course catalog.
* **Evidence Source:** `SELECT policyname, with_check FROM pg_policies WHERE schemaname = 'acd' AND tablename = 'course_program_map';`
* **Impact:** **ARCHITECTURAL IMPROVEMENT**. Prevents cross-departmental deadlocks.
* **Recommended Disposition:** **Retain Live Definition**. Codify Option C into the authoritative baseline and deprecate the original `60_rls.sql` definition.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-03: Security-Definer Course Validity Helper `core.is_active_course_in_tenant`
* **Finding ID:** `DIFF-03`
* **Schema / Module:** `core` / Foundation Helper
* **Object Name & Type:** `core.is_active_course_in_tenant(p_course_id uuid)` (Function)
* **Original Definition / Expected State:** Absent from the 13 bootstrap scripts in `Scripts_V3.0/`.
* **Actual Live Catalog State:** Present in `pg_proc` under namespace `core`. Defined as:
  ```sql
  CREATE OR REPLACE FUNCTION core.is_active_course_in_tenant(p_course_id uuid)
  RETURNS boolean LANGUAGE sql STABLE SECURITY DEFINER SET search_path TO '' AS $$
    SELECT EXISTS (
      SELECT 1 FROM acd.courses c
      WHERE c.id = p_course_id
        AND c.tenant_id = (SELECT core.current_tenant_id())
        AND c.deleted_at IS NULL
    );
  $$;
  ```
* **Current Application Expectation:** Required by `DIFF-02` (`acd.course_program_map` RLS).
* **Evidence Source:** `SELECT proname, prosecdef, proconfig FROM pg_proc WHERE proname = 'is_active_course_in_tenant';`
* **Impact:** **SECURITY ENABLER**. Closes cross-tenant curriculum mapping leaks while preserving least privilege.
* **Recommended Disposition:** **Codify in Baseline**.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-04: Outbox Table RLS Policies & Fail-Closed State
* **Finding ID:** `DIFF-04`
* **Schema / Module:** `sys` / Shared Services Outbox Relay
* **Object Name & Type:** `sys.outbox_events` (RLS Policies)
* **Original Definition / Expected State:** Bootstrap script `20_tables.sql` created `sys.outbox_events` and `60_rls.sql` enabled RLS, but **zero policies were defined**. Any non-superuser attempt to insert or read an outbox event failed closed with `SQLSTATE 42501`.
* **Actual Live Catalog State:** 4 tenant-scoped policies active:
  - `outbox_events_select`
  - `outbox_events_insert`
  - `outbox_events_update`
  - `outbox_events_delete`
  All policies enforce `(tenant_id = core.current_tenant_id()) OR (tenant_id IS NULL AND iam.is_platform_admin())`.
* **Current Application Expectation:** All 8 Java microservices emit transactional outbox events (`sys.outbox_events`) in the same database transaction as domain entity persistence.
* **Evidence Source:** `SELECT policyname, cmd, qual, with_check FROM pg_policies WHERE schemaname = 'sys' AND tablename = 'outbox_events';`
* **Impact:** **CRITICAL**. Without these 4 policies, all transactional outbox event writes across all microservices fail.
* **Recommended Disposition:** **Retain Live Definition**.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-05: Missing Schema USAGE Grants on `sys`
* **Finding ID:** `DIFF-05`
* **Schema / Module:** `sys` / Foundation Permissions
* **Object Name & Type:** `sys` (Schema USAGE Grant)
* **Original Definition / Expected State:** Bootstrap script `75_grants.sql` granted schema USAGE on `core, stu, acd, iam, doc, audit`, but **omitted schema `sys`**.
* **Actual Live Catalog State:** `has_schema_privilege('campx_app_server', 'sys', 'USAGE') = true` and `has_schema_privilege('authenticated', 'sys', 'USAGE') = true`.
* **Current Application Expectation:** Java services resolving table names in SQL queries (`INSERT INTO sys.outbox_events ...`).
* **Evidence Source:** `SELECT has_schema_privilege('campx_app_server', 'sys', 'USAGE');`
* **Impact:** **CRITICAL**. Prevents SQLSTATE 42501 schema resolution errors.
* **Recommended Disposition:** **Retain Live Definition**.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-06: Shared Audit Log Immutability & `events_insert` Policy
* **Finding ID:** `DIFF-06`
* **Schema / Module:** `audit` / Shared Audit Foundation
* **Object Name & Type:** `audit.events` (Table Privileges & RLS Policy)
* **Original Definition / Expected State:** Bootstrap script `75_grants.sql` granted `SELECT` on `audit.events` to `authenticated`, but omitted `INSERT`. Furthermore, `60_rls.sql` had no `events_insert` policy for `authenticated`.
* **Actual Live Catalog State:**
  - Table Privileges: `SELECT, INSERT` granted to `authenticated` and `campx_app_server`. `UPDATE, DELETE, TRUNCATE` strictly REVOKED.
  - Policy: `events_insert` active: `WITH CHECK ((tenant_id = core.current_tenant_id()) OR ((tenant_id IS NULL) AND iam.is_platform_admin()))`.
  - Trigger: `trg_forbid_mutation` active, throwing an exception on any attempt to update or delete rows.
* **Current Application Expectation:** Java Shared Services (`AuditLogger`) appends structured compliance events within transactional boundaries.
* **Evidence Source:** `SELECT has_table_privilege('authenticated', 'audit.events', 'INSERT');`
* **Impact:** **SECURITY REQUIREMENT**. Implements immutable, tamper-evident append-only ledger.
* **Recommended Disposition:** **Retain Live Definition**.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-07: `FORCE ROW LEVEL SECURITY` on Tier 1 Tables
* **Finding ID:** `DIFF-07`
* **Schema / Module:** `stu`, `core`, `acd` / Gate G1 Security Remediation
* **Object Name & Type:** `stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students` (`relforcerowsecurity`)
* **Original Definition / Expected State:** Bootstrap script `60_rls.sql` executed `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`. In PostgreSQL, standard `ENABLE RLS` does not apply to table owners or roles holding `BYPASSRLS`.
* **Actual Live Catalog State:** All 4 Tier 1 Core tables have `relforcerowsecurity = true` in `pg_class`. (Tier 2 tables such as `core.addresses` have `relforcerowsecurity = false`).
* **Current Application Expectation:** Gate G1 invariant: All student and demographic queries must be filtered by PostgreSQL RLS regardless of database role attributes.
* **Evidence Source:** `SELECT relname, relrowsecurity, relforcerowsecurity FROM pg_class WHERE relforcerowsecurity = true;`
* **Impact:** **SECURITY HARDENING**. Prevents accidental tenant data exposure during maintenance or administrative connections.
* **Recommended Disposition:** **Codify in Baseline**.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-08: Partition Maintenance Function Lacks RLS Code
* **Finding ID:** `DIFF-08`
* **Schema / Module:** `sys` / Partitioning Maintenance
* **Object Name & Type:** `sys.ensure_partitions(p_months_ahead int)` (Stored Procedure)
* **Original Definition / Expected State:** In `database/migrations/V20261020_01__complete_partition_security_hardening.sql`, lines 110 & 114 proposed updating `sys.ensure_partitions` to automatically execute `ALTER TABLE nm ENABLE ROW LEVEL SECURITY` whenever a new partition is dynamically attached.
* **Actual Live Catalog State:** The function currently compiled in `pg_proc` matches the original bootstrap version (`45_partitions.sql`), which only executes `CREATE TABLE ... PARTITION OF` and `REVOKE ALL ... FROM anon, authenticated`, **omitting `ENABLE ROW LEVEL SECURITY`**.
* **Current Application Expectation:** Any dynamically created future child partition must automatically have RLS enabled to prevent unauthenticated access.
* **Evidence Source:** `SELECT pg_get_functiondef(oid) FROM pg_proc WHERE proname = 'ensure_partitions';`
* **Impact:** **MEDIUM**. Existing partitions have RLS enabled, but any future partitions generated by `sys.ensure_partitions()` would be created without RLS enabled.
* **Recommended Disposition:** **Update Function**. Apply step 5 of `V20261020_01` in the new baseline.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-09: 10 `sys` Tables Remain Fail-Closed with Zero Policies
* **Finding ID:** `DIFF-09`
* **Schema / Module:** `sys` / System Integration
* **Object Name & Type:** 10 tables: `sys.data_quality_issues`, `sys.dead_letter_events`, `sys.entity_locks`, `sys.export_jobs`, `sys.id_map`, `sys.idempotency_keys`, `sys.import_jobs`, `sys.import_rows`, `sys.inbox_events`, `sys.jobs`
* **Original Definition / Expected State:** Bootstrap script `60_rls.sql` enabled RLS across all tables in schema `sys`, but defined 0 policies for these 10 tables.
* **Actual Live Catalog State:** Tables have `relrowsecurity = true` and `count(pg_policy) = 0`.
* **Current Application Expectation:** While current wave implementations use in-memory and Redis-like constructs for some locks, future background workers for `sys.inbox_events` and `sys.dead_letter_events` will fail closed immediately upon connecting as non-superusers.
* **Evidence Source:** `SELECT tablename FROM pg_tables WHERE schemaname = 'sys' AND tablename NOT IN (SELECT tablename FROM pg_policies WHERE schemaname = 'sys');`
* **Impact:** **MEDIUM (Latent Failure)**. Blocks future asynchronous message processing and dead-letter queue analysis.
* **Recommended Disposition:** **Add Tenant-Scoped RLS Policies** matching the pattern established on `sys.outbox_events`.
* **Confidence Level:** **100% (Verified Live)**.

---

### Finding DIFF-10: Custom Access Token Hook Omission of Role & College Claims
* **Finding ID:** `DIFF-10`
* **Schema / Module:** `iam` / Authentication & Identity
* **Object Name & Type:** `iam.custom_access_token_hook(event jsonb)` (Authentication Hook)
* **Original Definition / Expected State:** Expected to inject full tenant, role, and college authorization claims into issued Supabase Auth JWTs.
* **Actual Live Catalog State:** The hook only extracts `tenant_id` from `iam.user_profiles` and injects `{"app_metadata": {"tenant_id": "..."}}`. It omits `user_role` and `college_id`.
* **Current Application Expectation:** API Gateway `JdbcIdentityResolver` must issue a direct JDBC SQL query against `iam.user_profiles` and `iam.role_assignments` on every request to populate `X-User-Role` and `X-College-Id` headers.
* **Evidence Source:** `SELECT pg_get_functiondef(oid) FROM pg_proc WHERE proname = 'custom_access_token_hook';`
* **Impact:** **PERFORMANCE / REDUNDANCY**. Requires an extra database round-trip in API Gateway that could otherwise be satisfied directly from JWT claims.
* **Recommended Disposition:** **Enhance Auth Hook** in Phase 2 to inject `user_role` and `college_id` into `app_metadata`.
* **Confidence Level:** **100% (Verified Live)**.
