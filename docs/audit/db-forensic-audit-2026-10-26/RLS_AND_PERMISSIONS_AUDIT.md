# CampXSync — Row-Level Security & Permissions Forensic Audit
## Comprehensive Security & Tenant Isolation Analysis

**Audit Date:** 2026-10-26  
**Auditor:** Antigravity Agentic AI (Read-Only Forensic Mode)  
**Target Environment:** `CampXSync-Architecture-Dev` (Supabase Dev ref: `azulbaxszsqxymqmlwfe`)  

---

## 1. Executive Security Evaluation

The CampXSync database implements a Defense-in-Depth multi-tenant authorization architecture anchored directly within the PostgreSQL kernel:

1. **Kernel Enforcement**: 100% of application tables (458 of 458) have Row-Level Security (`relrowsecurity = true`) enabled.
2. **Strict Multi-Tenancy**: The primary tenant boundary is enforced via `core.current_tenant_id()`, which extracts the tenant UUID from PostgreSQL session parameter `request.jwt.claim.tenant_id` or `app.current_tenant_id`.
3. **Application Least-Privilege Role**: Microservices authenticate via `campx_app_server` (`NOSUPERUSER`, `NOBYPASSRLS`, `CONNECTION LIMIT 25`). Inside transactional boundaries, services issue `SET LOCAL ROLE authenticated` and bind claims via `SET LOCAL "request.jwt.claims" = '...'`.
4. **Append-Only Immutability**: Critical compliance and financial ledgers (`audit.events`, `audit.change_log`) enforce immutability at both the SQL privilege layer (`REVOKE UPDATE, DELETE, TRUNCATE`) and trigger layer (`trg_forbid_mutation`).
5. **Search Path Hijacking Protection**: 100% of security-definer functions in the application catalog have explicit `SET search_path = ''` configured, mitigating search-path hijacking attacks.

---

## 2. Row-Level Security (RLS) Policy Distribution

The live database contains **1,545 active RLS policies** across 20 application schemas:

| Schema | Table Count | Total Policies | SELECT | INSERT | UPDATE | DELETE | Key Policy Patterns |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`acd`** | 32 | 131 | 35 | 32 | 32 | 32 | Scoped by `tenant_id` + `perm_all` / `perm_department_ids` / `programs_in_scope` |
| **`audit`** | 32 | 50 | 25 | 25 | 0 | 0 | Scoped by `tenant_id`; zero UPDATE or DELETE policies (strictly append-only) |
| **`bud`** | 16 | 58 | 16 | 14 | 14 | 14 | Scoped by `tenant_id` + cost-center permissions |
| **`cam`** | 29 | 86 | 29 | 19 | 19 | 19 | Scoped by `tenant_id` + audience group ownership |
| **`cfg`** | 8 | 20 | 8 | 4 | 4 | 4 | Scoped by `tenant_id` + platform/college administration |
| **`core`** | 25 | 95 | 26 | 23 | 23 | 23 | Scoped by `tenant_id` + college/campus scope functions |
| **`doc`** | 13 | 47 | 14 | 11 | 11 | 11 | Scoped by `tenant_id` + document template ownership |
| **`exm`** | 36 | 141 | 37 | 35 | 35 | 34 | Scoped by `tenant_id` + exam session / valuation center permissions |
| **`fin`** | 44 | 166 | 45 | 41 | 41 | 39 | Scoped by `tenant_id` + fee structure & cashier authorizations |
| **`hrm`** | 62 | 220 | 62 | 53 | 53 | 52 | Scoped by `tenant_id` + department employee hierarchy |
| **`iam`** | 19 | 61 | 20 | 14 | 14 | 13 | Scoped by `tenant_id` + `iam.is_platform_admin()` / `can_grant_role` |
| **`lms`** | 66 | 231 | 67 | 55 | 55 | 54 | Scoped by `tenant_id` + course faculty / enrolled student visibility |
| **`obe`** | 7 | 26 | 7 | 7 | 6 | 6 | Scoped by `tenant_id` + program outcome mapping authority |
| **`pay`** | 17 | 68 | 17 | 17 | 17 | 17 | Scoped by `tenant_id` + payroll administrator permissions |
| **`plat`** | 11 | 31 | 11 | 7 | 7 | 6 | Scoped by `iam.is_platform_admin()` (cross-tenant platform management) |
| **`rpt`** | 9 | 30 | 9 | 7 | 7 | 7 | Scoped by `tenant_id` + reporting dashboard permissions |
| **`stu`** | 2 | 8 | 2 | 2 | 2 | 2 | Scoped by `tenant_id` + `students_in_scope` (`FORCE RLS` enabled) |
| **`sys`** | 11 | 4 | 1 | 1 | 1 | 1 | Restricted strictly to `sys.outbox_events` (10 tables fail closed) |
| **`tax`** | 10 | 38 | 10 | 10 | 9 | 9 | Scoped by `tenant_id` + statutory tax finance permissions |
| **`wf`** | 9 | 34 | 9 | 9 | 8 | 8 | Scoped by `tenant_id` + workflow task assignee authority |
| **TOTAL** | **458** | **1,545** | **423** | **379** | **374** | **369** | |

---

## 3. Tenant Context & Isolation Architecture

### 3.1 Tenant Context Resolution (`core.current_tenant_id()`)
Tenant isolation relies on `core.current_tenant_id()`, defined in `10_early_helpers.sql`:
```sql
CREATE OR REPLACE FUNCTION core.current_tenant_id()
RETURNS uuid LANGUAGE plpgsql STABLE AS $$
DECLARE
  v_tenant text;
BEGIN
  -- 1. Attempt resolution from Supabase JWT claims (standard web/API request path)
  v_tenant := current_setting('request.jwt.claim.tenant_id', true);
  IF v_tenant IS NOT NULL AND v_tenant <> '' THEN
    RETURN v_tenant::uuid;
  END IF;

  -- 2. Attempt resolution from session setting (Java connection pool path)
  v_tenant := current_setting('app.current_tenant_id', true);
  IF v_tenant IS NOT NULL AND v_tenant <> '' THEN
    RETURN v_tenant::uuid;
  END IF;

  -- 3. Fallback: resolve from authenticated user profile
  SELECT up.tenant_id INTO v_tenant
  FROM iam.user_profiles up
  WHERE up.id = auth.uid() AND up.deleted_at IS NULL AND up.status = 'ACTIVE';

  IF v_tenant IS NOT NULL THEN
    RETURN v_tenant::uuid;
  END IF;

  RETURN NULL;
END $$;
```

**Security Analysis**:
- If all three resolution mechanisms return `NULL`, `core.current_tenant_id()` returns `NULL`.
- In all RLS policies, the clause `tenant_id = (SELECT core.current_tenant_id())` evaluates to `tenant_id = NULL`, which in SQL tri-state logic evaluates to `UNKNOWN` (falsy).
- Consequently, any query issued without valid tenant context **fails closed**, returning 0 rows for reads and rejecting writes.

### 3.2 Multi-Tenant Boundary Enforcement
In live Wave 4, 5, 6, 7, 8, and 9 testing, cross-tenant isolation was verified under synthetic Tenant B claims:
- When a caller authenticated with Tenant A claims attempts to `SELECT` rows belonging to Tenant B, PostgreSQL kernel RLS returns exactly **0 rows**.
- When a caller attempts to `INSERT` a row with a foreign `tenant_id`, the operation is aborted with an RLS check violation (`new row violates row-level security policy`).
- The unprivileged role `campx_app_server` does NOT possess `BYPASSRLS`, ensuring Java application services are subject to kernel RLS enforcement.

---

## 4. Role Hierarchy & Permission Evaluation Functions

CampXSync decouples authorization logic into a hierarchical RBAC evaluation layer implemented as stored functions in schema `iam`:

### 4.1 Permission Functions Overview

| Function Name | Security Definer | Search Path | Grantee Roles | Purpose & Behavior |
| :--- | :--- | :--- | :--- | :--- |
| **`iam.is_platform_admin()`** | `true` | `""` | `authenticated`, `campx_app_server` | Returns `true` if current user holds active role `PLATFORM_ADMIN`. Bypasses tenant scoping for global operations. |
| **`iam.perm_all(p_code text)`** | `true` | `""` | `authenticated`, `campx_app_server` | Returns `true` if current user holds permission `p_code` across the ENTIRE tenant (e.g., `COLLEGE_ADMIN`). |
| **`iam.perm_colleges(p_code text)`** | `true` | `""` | `authenticated`, `campx_app_server` | Returns array of `college_id` UUIDs where caller holds permission `p_code`. |
| **`iam.perm_department_ids(p_code text)`** | `true` | `""` | `authenticated`, `campx_app_server` | Returns array of `department_id` UUIDs where caller holds permission `p_code` (e.g., `HOD`). |
| **`iam.has_permission(p_code text)`** | `true` | `""` | `authenticated`, `campx_app_server` | Returns `true` if user holds permission `p_code` in ANY active role assignment. |
| **`iam.has_permission_in(...)`** | `true` | `""` | `authenticated`, `campx_app_server` | Checks permission within specific college and department boundaries. |

### 4.2 Security Definer & Search Path Invariants
All 17 `iam` and `core` permission helper functions are declared `SECURITY DEFINER` and have their search path explicitly set to empty:
```sql
ALTER FUNCTION iam.perm_all(text) SET search_path = '';
```
**Rationale**: In PostgreSQL, `SECURITY DEFINER` functions execute with the privileges of their owner (`postgres`). Without `SET search_path = ''`, an attacker could create malicious objects in a temporary schema (or public schema) that could be invoked by the security definer function. Setting `search_path = ''` forces all function queries to use fully qualified schema names (e.g., `iam.roles`, `core.tenants`), eliminating search-path substitution vulnerabilities.

---

## 5. Security Findings & Risk Analysis

### Finding SEC-01: Permission Code Bug on `acd.courses` (High Risk)
* **Description**: Policies on `acd.courses` (`courses_select`, `courses_insert`, `courses_update`, `courses_delete`) evaluate `iam.perm_all('acd03.view')` instead of `iam.perm_all('acd01.view')`.
* **Risk**: Authorized Course Management coordinators holding `acd01.*` permissions cannot manage courses unless they also hold Subject Management (`acd03.*`) permissions.
* **Remediation**: Execute `V20261011_01__fix_acd01_courses_rls_policies.sql` during the initial migration sequence.

### Finding SEC-02: 10 `sys` Tables Completely Fail-Closed (Medium Risk)
* **Description**: Tables `sys.dead_letter_events`, `sys.inbox_events`, `sys.jobs`, `sys.idempotency_keys`, `sys.entity_locks`, `sys.export_jobs`, `sys.import_jobs`, `sys.import_rows`, `sys.id_map`, and `sys.data_quality_issues` have `relrowsecurity = true` with 0 policies.
* **Risk**: Any non-superuser background worker attempting to read or write to inbox or dead-letter tables is blocked.
* **Remediation**: Define tenant-scoped RLS policies on these tables modeled after `sys.outbox_events`.

### Finding SEC-03: `sys.ensure_partitions` Missing RLS Clause (Medium Risk)
* **Description**: The stored procedure `sys.ensure_partitions()` creates new monthly partition tables and revokes privileges from `anon` and `authenticated`, but does NOT execute `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`.
* **Risk**: Future dynamic partitions created by this procedure will not have RLS explicitly enabled, allowing bypass if direct table grants are inadvertently granted.
* **Remediation**: Update `sys.ensure_partitions()` with `ALTER TABLE nm ENABLE ROW LEVEL SECURITY`.

### Finding SEC-04: Tier 2 Demographic Tables Lack `FORCE RLS` (Low Risk)
* **Description**: Gate G1 applied `FORCE ROW LEVEL SECURITY` to 4 Tier 1 Core tables (`stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students`), but Tier 2 tables (`core.addresses`, `core.person_contacts`, `core.person_emergency_contacts`, `core.person_identifiers`, `acd.section_enrollments`) remain standard RLS.
* **Risk**: Table owners connecting with `BYPASSRLS` could view demographic child records without RLS filtering.
* **Remediation**: Apply Decision Gate DEC-G1-02B (`04_rls_hardening_proposal.sql`, Block 2) to extend `FORCE RLS` to Tier 2 tables.
