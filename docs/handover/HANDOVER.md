# CampXSync — Phase Handover Register

## Phase 0 — Preflight Handover (2026-10-13)

### 1. STAGING (Track A)
- **Current Status**: INITIALIZED (Preflight Complete).
- **Last Verified Successful Step**: Preflight verification, tooling audit, production ref (`ekspahbsbwdpraaqfqui`) isolation check, pre-audit backup branch creation.
- **Exact Outstanding Step**: Phase 1 Audit of changes and prior fixes, followed by Phase 2 Staging Audit & Verification Pack generation (`verify-readonly.sql`).
- **Next Action**: Awaiting user approval to proceed with Phase 1 (Audit).
- **Command / Operation Required**: None for DB yet; read-only file audit in Phase 1.
- **Expected Result**: Complete change inventory and prior fixes analysis without executing database commands or modifying files.
- **Remaining Completion Criteria**:
  - Live staging schema verification via `verify-readonly.sql`.
  - Dashboard configuration checklist execution (Auth hooks, pooler JDBC credentials).
  - Environment guard validation tests against staging pooler.
  - Zero cross-tenant leaks and zero duplicate seed roles verified.

### 2. MVP (Track B)
- **Current Status**: INITIALIZED (Preflight Complete).
- **Highest-Priority Independent Task**: Baseline and backlog synthesis for ADM-01 -> ADM-02 -> ACD-01 (Phase 4).
- **Work Completed**: Preflight inventory verified. Confirmed ADM-01 Step 1 (User Profile Management on `iam.user_profiles`) has full controller, model, and PostgreSQL repository code implemented locally.
- **Files Changed in Phase 0**: None in source code (`.planning/*`, `docs/audit/*`, `docs/handover/*` created).
- **Tests and Review Results**:
  - `UserProfileControllerHttpTest`: 23/23 PASSED.
  - `UserProfileRepositoryValidationTest`: 24/24 PASSED.
  - `CourseDomainServiceTest`: 20/20 PASSED.
- **Remaining Acceptance Criteria**: Live database verification against Supabase Staging (`kpwdvvcschankgggitnj`) pending Track A completion.
- **Next Implementation Task**: Proceed to Phase 1 Audit, then Phase 4 MVP Baseline and Backlog.

### 3. OVERALL
- **What Must NOT Be Repeated**:
  - Do not re-run 80_seed without current DB verification (prior seed duplication risk).
  - Do not execute database commands without non-secret project ref verification and explicit user "GO" approval.
  - Do not re-run test suites blindly; use targeted test execution.
  - Do not reimplement ADM-01 Step 1 (already verified complete locally).
- **What is Blocked and Exactly Why**:
  - Live database tests against Supabase Staging are blocked pending Phase 2/3 verification pack and user staging credentials confirmation.
- **What Can Continue Immediately**:
  - Phase 1 Audit (strictly read-only repository and file inspection).
- **Next Milestone for Each Track**:
  - **Track A**: Phase 1 Audit (`docs/audit/01-change-inventory.md`, `02-prior-fixes.md`, `03-git-status.md`) and Phase 2 Verification Pack (`verify-readonly.sql`).
  - **Track B**: Phase 4 MVP Baseline and Backlog (`docs/mvp/01-baseline.md`, `02-backlog.md`).

---

## Phase 1 — Audit Handover (2026-10-13)

### 1. STAGING (Track A)
- **Current Status**: PHASE 1 COMPLETE (Read-Only Audit & Change Inventory Delivered).
- **Last Verified Successful Step**: Completion and cross-verification of `docs/audit/01-change-inventory.md`, `docs/audit/02-prior-fixes.md`, and `docs/audit/03-git-status.md`.
- **Exact Outstanding Step**: Phase 2 Staging Audit & Verification Pack (`01-bootstrap-table.md`, `verify-readonly.sql`, `02-manual-checklist.md`, `03-config-audit.md`, `04-completion-criteria.md`, `00-checkpoint.md`).
- **Next Action**: Awaiting user approval to generate Phase 2 artifacts.
- **Command / Operation Required**: None against DB yet. `verify-readonly.sql` will be delivered for the user to run in Supabase SQL editor.
- **Expected Result**: Complete read-only verification pack with numbered SELECT queries.
- **Remaining Completion Criteria**: Live verification of tables/schemas, RLS policies, zero duplicate seed roles, auth token hook function existence, JDBC pooler connectivity.

### 2. MVP (Track B)
- **Current Status**: PHASE 1 COMPLETE (Baseline state and prior fixes audited).
- **Highest-Priority Independent Task**: Phase 4 baseline and backlog synthesis across ADM-01 -> ADM-02 -> ACD-01.
- **Work Completed**:
  - Full audit of all 92 files modified between `Dev1.0Branch` and `ReviewBranch`.
  - Audited `TestEnvironmentGuard` in all services (verified present in ADM-01, ADM-02, ACD-01; verified absent in 9 in-memory academic services).
  - Confirmed 74/74 unit tests passing for `TestEnvironmentGuard`.
  - Identified that all live database integration tests skip cleanly offline via `assumeNotNull(...)`.
- **Files Changed in Phase 1**: Generated audit reports only (`docs/audit/01-change-inventory.md`, `02-prior-fixes.md`, `03-git-status.md`); updated `00-preflight.md`, `.planning/STATE.md`, and `HANDOVER.md`. Zero code modifications.
- **Tests and Review Results**:
  - `TestEnvironmentGuardTest`: 74/74 PASSED across 3 services.
  - `SupabaseJwtClaimParsingRegressionTest`: 29/29 PASSED.
  - `CollegeAdminTenantIsolationRegressionTest`: 10/10 PASSED.
  - `FailFastPersistenceBootstrapTest`: 2/2 PASSED.
  - Live tests (`CollegeResolverLiveRegressionTest`, `JsonbSerializationSecurityTest`, `PostgresCourseRestrictedIdentityRlsIntegrationTest`, `Postgres*RepositoryTest`): 100% cleanly skipped offline.
- **Remaining Acceptance Criteria**: Live database verification against Supabase Staging (`kpwdvvcschankgggitnj`) in Track A.
- **Next Implementation Task**: Phase 4 MVP Baseline and Backlog specification.

### 3. OVERALL
- **What Must NOT Be Repeated**:
  - Never assume a skipped test is a pass; verify actual test counts.
  - Never execute DB write scripts or migrations without non-secret project ref verification and user "GO" approval.
  - Never modify files in academic modules without establishing persistence architecture.
- **What is Blocked and Exactly Why**:
  - Live database execution is gated until Track A Phase 2 read-only verification results are returned by the user.
- **What Can Continue Immediately**:
  - Phase 2 Staging Verification Pack generation.
- **Next Milestone for Each Track**:
  - **Track A**: Phase 2 Staging Audit & Verification Pack.
  - **Track B**: Phase 4 MVP Baseline & Backlog.

---

## Phase 2 — Staging Audit & Verification Pack Handover (2026-10-13)

### 1. STAGING (Track A)
- **Current Status**: PHASE 2 COMPLETE (Verification Pack & Checkpoint Artifacts Delivered).
- **Last Verified Successful Step**:
  - Created 6 Phase 2 deliverables: `docs/staging/00-checkpoint.md`, `docs/staging/01-bootstrap-table.md`, `docs/staging/verify-readonly.sql`, `docs/staging/02-manual-checklist.md`, `docs/staging/03-config-audit.md`, and `docs/staging/04-completion-criteria.md`.
  - Audited all three `TestEnvironmentGuard` copies (74/74 unit tests passing offline).
  - Executed Rule 11 boolean check: identified critical finding where ambient shell contains production ref in `SUPABASE_USER` and `CAMPX_SUPABASE_JWT_ISSUER`.
  - Validated that `verify-readonly.sql` is 100% SELECT-only (20 numbered queries, zero DDL/DML).
  - Audited all 16 unguarded services: 9 academic microservices use in-memory `ConcurrentHashMap` with critical cross-tenant data leakage identified in `curriculum-service`, `subject-management-service`, and `batch-management-service`.
- **Exact Outstanding Step**: Execution of `verify-readonly.sql` on Supabase Staging SQL Editor by user and pasting of query results.
- **Next Action**: Awaiting user query results and dashboard confirmation.
- **Command / Operation Required**:
  - Run `docs/staging/verify-readonly.sql` in Supabase Staging SQL Editor.
  - Complete `docs/staging/02-manual-checklist.md` in Supabase Dashboard.
  - Set staging pooler credentials in private shell.
- **Expected Result**: Introspection numbers for all 20 queries proving existence of 20 schemas, 458 tables, 403 UQ, 1239 FK, 1158 indexes, forced RLS, and 0 duplicate seed roles.
- **Remaining Completion Criteria**: 18 of 20 completion criteria currently pending live evidence or user dashboard/shell actions.

### 2. MVP (Track B)
- **Current Status**: PHASE 1 COMPLETE / PHASE 4 READY.
- **Highest-Priority Independent Task**: Phase 4 MVP Baseline and Backlog specification (ADM-01 -> ADM-02 -> ACD-01).
- **Work Completed**:
  - ADM-01 Step 1 (`iam.user_profiles`) remains complete and verified locally (47 unit/HTTP tests passing).
  - Cross-tenant leakage risks in academic tier documented; architectural requirements for Phase 6 identified.
- **Files Changed in Phase 2**: `docs/staging/*` (6 files created), `docs/handover/HANDOVER.md`, `.planning/STATE.md`. Zero code modifications.
- **Tests and Review Results**:
  - `TestEnvironmentGuardTest`: 74/74 PASSED across all 3 services.
  - Rule 11 boolean environment check: executed without errors.
- **Remaining Acceptance Criteria**: Live database verification against Supabase Staging (`kpwdvvcschankgggitnj`) in Track A.
- **Next Implementation Task**: Proceed to Phase 4 (MVP Baseline and Backlog) or await Staging results.

### 3. OVERALL
- **What Must NOT Be Repeated**:
  - **DO NOT re-run `80_seed.sql`**: Risk of duplicate roles in `iam.roles`.
  - **DO NOT re-run `30_foreign_keys.sql`**: Non-idempotent script.
  - **DO NOT run `mvn` or live tests while ambient shell has `ContainsProdRef=True`**: Rule 11 strictly prohibits execution.
  - **DO NOT add shared in-memory fallbacks**: Identified severe leakage in existing academic modules.
- **What is Blocked and Exactly Why**:
  - Live database execution is strictly blocked under Rule 11 due to `ContainsProdRef=True` in ambient environment variables, and pending user execution of `verify-readonly.sql`.
- **What Can Continue Immediately**:
  - User runs `verify-readonly.sql` in Supabase SQL Editor and provides results.
  - User completes `02-manual-checklist.md`.
  - User exports staging pooler credentials in shell.
  - Alternatively, Phase 4 (Track B MVP Baseline & Backlog) can proceed independently.
- **Next Milestone for Each Track**:
  - **Track A**: Phase 3 Staging Completion (triggered by user pasting query results).
  - **Track B**: Phase 4 MVP Baseline & Backlog (`docs/mvp/01-baseline.md`, `02-backlog.md`).

---

## Phase 3 — Staging Completion Handover (2026-10-13)

### 1. STAGING (Track A)
- **Current Status**: SUBSTANTIALLY ADVANCED (Read-Only Catalog & Security Inspection Complete; Writes Gated).
- **Last Verified Successful Step**:
  - Reconnected Supabase MCP to project `kpwdvvcschankgggitnj` in read-only mode (`supabase_read_only_user`).
  - Directly verified 20 ERP schemas, 458 application tables, 458 PKs, 403 Unique constraints, 1,239 FKs, and 1,158 non-constraint indexes (100% catalog match).
  - Directly verified 9 foundational functions, 106 `sc_*` scope functions, 39 `own_/vis_` functions, and `iam.custom_access_token_hook` execution privileges (granted solely to `supabase_auth_admin`).
  - Directly verified `anon` table privileges: 0 across all 20 schemas.
  - Directly verified 0 duplicate roles, 0 duplicate permissions, and 547 active permissions.
  - Confirmed active `acd.courses` policies on staging match hardened migration `V20261011_01`.
  - Resolved Rule 11 blocker: ambient shell verified clean with `ContainsProdRef=False` across all variables, `ContainsStagingRef=True` for user/issuer, and `CAMPX_TEST_ENV=staging`.
  - Resolved Finding 2: confirmed Java integration tests execute under `SET LOCAL ROLE authenticated` and inject JWT claims via `UserSecurityContext`, proving genuine non-privileged RLS enforcement.
  - Confirmed Finding 5 (`sys` tables) is by design (reserved for `service_role` and workers).
- **Exact Outstanding Step**: User approves two pending database writes (`GO PARTITION-RLS-FIX` and `GO PROVISION-TEST-TENANT`) and confirms Custom Access Token hook in Dashboard.
- **Next Action**: Awaiting user approval for Write Step 1 (`GO PARTITION-RLS-FIX`) or Write Step 2 (`GO PROVISION-TEST-TENANT`).
- **Command / Operation Required**:
  - Write 1: `DO $$ ... ALTER TABLE ... ENABLE ROW LEVEL SECURITY; REVOKE ALL ON ... FROM anon, authenticated; ... $$;` for 24 monthly partition child tables.
  - Write 2: `INSERT INTO core.tenants (id, name, slug, status, ...) VALUES ('11111111-1111-1111-1111-111111111111'::uuid, ...);`
- **Expected Result**:
  - Write 1 closes the RLS bypass on 24 monthly partitions.
  - Write 2 provisions the test tenant and automatically triggers `iam.seed_system_roles` to seed all 30 system roles and 2,138 permissions.
- **Remaining Completion Criteria**:
  - CRIT-06 / CRIT-10 (gated on `GO PARTITION-RLS-FIX`).
  - CRIT-09 (gated on user dashboard hook confirmation).
  - CRIT-14, CRIT-17, CRIT-18, CRIT-19, CRIT-20 (gated on `GO PROVISION-TEST-TENANT` and live test execution).

### 2. MVP (Track B)
- **Current Status**: BASELINE AUDITED / READY TO ADVANCE INDEPENDENTLY.
- **Highest-Priority Independent Task**: Phase 4 MVP Baseline and Backlog specification (`docs/mvp/01-baseline.md`, `02-backlog.md`) across ADM-01 -> ADM-02 -> ACD-01.
- **Work Completed**:
  - ADM-01 Step 1 (`iam.user_profiles`) remains complete and verified locally (47 unit/HTTP tests passing).
  - RLS integration tests and repository implementations ready for live execution once test tenant is provisioned.
- **Files Changed in Phase 3**: `docs/staging/05-execution-log.md` (created), `docs/staging/04-completion-criteria.md`, `docs/staging/00-checkpoint.md`, `docs/handover/HANDOVER.md`, `.planning/STATE.md`. Zero code modifications.
- **Remaining Acceptance Criteria**: Live database verification against Supabase Staging (`kpwdvvcschankgggitnj`).
- **Next Implementation Task**: Proceed to Phase 4 (MVP Baseline & Backlog) independently.

### 3. OVERALL
- **What Must NOT Be Repeated**:
  - **DO NOT re-run `80_seed.sql`**: Seed permissions (547) and role templates (30) are already verified intact.
  - **DO NOT re-run `30_foreign_keys.sql`**: Non-idempotent; 1,239 FKs verified.
  - **DO NOT re-run completed catalog queries**: 458 tables, 458 PKs, 403 UQ, 1,239 FKs, 1,158 non-constraint indexes verified.
  - **DO NOT re-implement ADM-01 Step 1**: Already complete and verified locally.
- **What is Blocked and Exactly Why**:
  - Staging database writes are blocked pending explicit user approval ritual (`GO <step>`).
  - Live staging integration tests are blocked pending test tenant creation (`CRIT-14`) and dashboard hook confirmation (`CRIT-09`).
- **What Can Continue Immediately**:
  - User can provide `GO PARTITION-RLS-FIX` and `GO PROVISION-TEST-TENANT`.
  - User can confirm Dashboard Custom Access Token hook toggle.
  - Track B Phase 4 (MVP Baseline and Backlog) can proceed independently right now.
- **Next Milestone for Each Track**:
  - **Track A**: Execute approved writes, verify test tenant, run targeted live integration tests.
  - **Track B**: Phase 4 MVP Baseline & Backlog (`docs/mvp/01-baseline.md`, `02-backlog.md`).

---

## Phase 4 — MVP Baseline, Backlog & Task Implementation Handover (2026-10-13)

### 1. STAGING (Track A)
- **Current Status**: READ-ONLY VERIFICATION COMPLETE / WRITES GATED AWAITING USER APPROVAL.
- **Last Verified Successful Step**:
  - Re-verified Rule 11 Boolean environment cleanliness (`ContainsProdRef = False` across all variables; `ContainsStagingRef = True`; `CAMPX_TEST_ENV = staging`).
  - Read-only introspection of 24 monthly partition child tables across `audit`, `cam`, `hrm`, and `lms` confirmed all 24 lack RLS and have direct `SELECT`/`INSERT` granted to `authenticated`. Default partitions already have RLS and zero privileges.
  - Read-only inspection of `core.tenants` confirmed 0 rows, and UUID `11111111-1111-1111-1111-111111111111` is unused.
  - Corrected critical schema constraint bug: prior proposal used non-existent column `slug = 'campx-staging'`. Schema defines `code` column with regex `^[A-Z0-9_-]{2,30}$`. Corrected SQL uses `code = 'CAMPX_STAGING'`.
- **Exact Outstanding Steps**:
  1. User approves `GO PARTITION-RLS-FIX` (Write Proposal 1).
  2. User approves `GO PROVISION-TEST-TENANT` (Write Proposal 2).
  3. User confirms Custom Access Token hook in Supabase Dashboard (`iam.custom_access_token_hook`).
- **Remaining Completion Criteria**: CRIT-06/10 (gated on Write 1), CRIT-09 (dashboard manual), CRIT-14/17-20 (gated on Write 2 & live tests).

### 2. MVP (Track B)
- **Current Status**: PHASE 4 BASELINE & BACKLOG COMPLETE; TASK MVP-ADM01-02 IMPLEMENTED & VALIDATED.
- **Work Completed**:
  - Authored `docs/mvp/01-baseline.md` auditing ADM-01, ADM-02, and ACD-01 modules.
  - Authored `docs/mvp/02-backlog.md` detailing granular prioritized MVP execution backlog.
  - Implemented `MVP-ADM01-02`: Platform Role Template Authorization Enforcement and Comprehensive Validation Suite.
    - Updated `InMemoryRoleTemplateRepository.java` to enforce `checkPlatformAdminContext` on mutations while permitting reads for authenticated non-admin callers (matching database RLS SELECT policy).
    - Updated `PostgresRoleTemplateRepository.java` to enforce fail-fast security context validation before SQL execution and enabled offline validation testing.
    - Created `RoleTemplateRepositoryValidationTest.java` with 33 unit/validation tests covering CRUD, permission binding, optimistic locking, and platform administrator authorization.
- **Files Changed in Source Code**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/InMemoryRoleTemplateRepository.java`
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresRoleTemplateRepository.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/RoleTemplateRepositoryValidationTest.java`
- **Tests and Review Results**:
  - `RoleTemplateRepositoryValidationTest`: 33/33 PASSED (0 failures, 0 errors).
  - `InMemoryRoleTemplateRepositoryTest`: 6/6 PASSED (0 failures, 0 errors).
  - ADM-01 Full Unit/Validation Suite: 293/293 PASSED (0 failures, 0 errors).
  - ADM-02 Full Unit/Regression Suite: 82/82 PASSED (0 failures, 0 errors, 31 live tests cleanly skipped).
  - ACD-01 Full Unit/Regression Suite: 68/68 PASSED (0 failures, 0 errors, 22 live tests cleanly skipped).
- **Next Implementation Task**: `MVP-ADM01-03` (Institute Admin Controller Role Template HTTP Endpoints & Audit Event Propagation).

### 3. OVERALL
- **What Must NOT Be Repeated**:
  - Do NOT rerun completed catalog checks or schema audits.
  - Do NOT rerun `80_seed.sql` or `30_foreign_keys.sql`.
  - Do NOT modify production (`ekspahbsbwdpraaqfqui`).
- **What is Blocked and Exactly Why**:
  - Database writes are blocked pending explicit user approval (`GO PARTITION-RLS-FIX` and `GO PROVISION-TEST-TENANT`).
  - Live PostgreSQL integration tests against Supabase staging are gated until test tenant `11111111-1111-1111-1111-111111111111` and test users are provisioned.
- **What Can Continue Immediately**:
  - Track B local MVP tasks can proceed completely independently without waiting for database writes.
- **Next Milestone for Each Track**:
  - **Track A**: User approval of `GO PARTITION-RLS-FIX` and `GO PROVISION-TEST-TENANT`.
  - **Track B**: `MVP-ADM01-03` (HTTP controller endpoints for Role Template management).



