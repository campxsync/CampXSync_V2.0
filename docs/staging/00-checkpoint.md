# Track A — Staging Checkpoint & Blocker Report

**Document**: `docs/staging/00-checkpoint.md`  
**Date**: 2026-10-13  
**Target Environment**: Supabase PostgreSQL Staging (`kpwdvvcschankgggitnj`)  
**Phase**: Phase 3 — Staging Completion  
**Binding Constraints**: Absolute Safety Rules 1–12

---

## 1. Checkpoint Status Responses

### 1. Last Staging Step Proven Successful
- **VERIFIED**: Reconnected Supabase MCP to project `kpwdvvcschankgggitnj` in read-only mode (`supabase_read_only_user`), verifying zero production references.
- **VERIFIED**: Introspected and confirmed 20 ERP schemas (CRIT-03), 458 application tables (CRIT-04), 458 PKs, 403 Unique constraints, 1,239 FKs (CRIT-05), and exactly 1,158 non-constraint indexes (catalog match).
- **VERIFIED**: Introspected foundational functions (9 functions), 106 `sc_*` scope helper functions in `iam`, and 39 `own_/vis_` functions in `core` (CRIT-07).
- **VERIFIED**: `iam.custom_access_token_hook` signature and permissions (`SECURITY DEFINER`, EXECUTE granted strictly to `supabase_auth_admin`, revoked from `anon`/`authenticated`/`service_role`) (CRIT-08).
- **VERIFIED**: `anon` role has 0 table permissions across all 20 schemas (CRIT-11).
- **VERIFIED**: 0 duplicate roles, 0 duplicate permissions, exactly 547 active permissions (CRIT-12, CRIT-13).
- **VERIFIED**: `acd.courses` RLS policies on staging actively check `acd01.view`, `acd01.write`, `acd01.manage` matching migration `V20261011_01`.
- **VERIFIED**: Ambient shell environment variables cleaned: `ContainsProdRef = False` across all variables, `ContainsStagingRef = True` for user and JWT issuer, `CAMPX_TEST_ENV = staging`, pooler JDBC URL and password set (CRIT-15, CRIT-16).
- **VERIFIED**: Finding 2 resolved: Java integration tests enforce genuine unprivileged RLS via `SET LOCAL ROLE authenticated;` and JWT claim injection within `UserSecurityContext`.

### 2. Next Unfinished Step
- **Step 3.1 (Write Ritual 1)**: User approves `GO PARTITION-RLS-FIX` to enable RLS and revoke direct privileges on 24 monthly partition child tables (resolving Finding 1).
- **Step 3.2 (Write Ritual 2)**: User approves `GO PROVISION-TEST-TENANT` to insert test tenant `11111111-1111-1111-1111-111111111111` into `core.tenants` using corrected schema column `code = 'CAMPX_STAGING'` (satisfying constraint `ck_tenants_x1`), triggering automatic system role seeding.
- **Step 3.3 (Dashboard Checklist)**: User confirms Custom Access Token hook toggle in Supabase Dashboard (`02-manual-checklist.md` Item 2).
- **Step 3.4 (Test Tenant Exports & Live Validation)**: Export `CAMPX_TEST_TENANT_ID` and run targeted live RLS tests (`PostgresCourseRestrictedIdentityRlsIntegrationTest`).

### 3. Exact Blocker & Classification
- **Primary Blocker 1 (Database Security Hardening — Finding 1)**:
  - 24 monthly partition child tables in `audit`, `cam`, `hrm`, `lms` lack RLS and have direct SELECT/INSERT granted to `authenticated`. Gated on user write approval (`GO PARTITION-RLS-FIX`).
- **Primary Blocker 2 (Database Fixtures — CRIT-14)**:
  - `core.tenants`, `auth.users`, and `iam.user_profiles` are currently empty in staging. Gated on user write approval (`GO PROVISION-TEST-TENANT`). Note: Previous proposal used non-existent column `slug = 'campx-staging'`; corrected SQL targets column `code = 'CAMPX_STAGING'` adhering to regex `^[A-Z0-9_-]{2,30}$`.
- **Primary Blocker 3 (Dashboard External Configuration — CRIT-09)**:
  - Auth Custom Access Token hook toggle in Supabase Dashboard requires user manual verification.
- **Classification**: **DATABASE WRITES AWAITING APPROVAL** (partition hardening & test tenant creation) + **DASHBOARD MANUAL CONFIRMATION**. Code and environment configurations are NOT blocked.

### 4. Minimum Action to Resolve
1. **User replies with approval**:
   `GO PARTITION-RLS-FIX`
   (Agent will execute the DO block to enable RLS and revoke direct table grants on all 24 monthly partitions).
2. **User replies with approval**:
   `GO PROVISION-TEST-TENANT`
   (Agent will insert the test tenant into `core.tenants` with `code = 'CAMPX_STAGING'`, which triggers `iam.on_tenant_created()` to seed all 30 system roles and 2,138 permissions).
3. **User verifies Dashboard Hook**:
   Follow `docs/staging/02-manual-checklist.md` Item 2 to ensure `iam.custom_access_token_hook` is toggled ON under Authentication → Hooks.

### 5. Which Completed Steps Must NOT Be Repeated
- **DO NOT re-run `80_seed.sql`**: Seed permissions (547) and role templates (30) are already present and verified. Re-running is strictly prohibited.
- **DO NOT re-run `30_foreign_keys.sql`**: 1,239 foreign keys are verified present; re-running causes duplicate constraint collisions.
- **DO NOT re-verify completed schema/catalog queries**: The 20 schemas, 458 tables, 458 PKs, 403 UQ, 1,239 FKs, and 1,158 non-constraint indexes are directly verified and recorded.
- **DO NOT re-implement ADM-01 Step 1**: User profile management is complete locally and verified with 47 unit/HTTP tests.
