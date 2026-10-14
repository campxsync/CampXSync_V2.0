# CampXSync — Phase Handover Register

## CURRENT STATE (Phase 5, Cycle 1 — 2026-10-14)
- **Active Workstreams**: Track A (Staging Readiness) & Track B (MVP Delivery)
- **Branch / Commit**: `ReviewBranch` (`3bcd745ed5c7e3fed578a60a8c680072cfd55343`), uncommitted Phase 4/5 changes.
- **Rule 11 Environment Status**: CLEAN (`ContainsProdRef=False` across all variables; `ContainsStagingRef=True`; `CAMPX_TEST_ENV=staging`).
- **Track A Status**: Catalog & read-only introspection 100% verified (20 schemas, 458 tables). Database writes gated awaiting user GO approval (`GO PARTITION-RLS-FIX`, `GO PROVISION-TEST-TENANT`) & dashboard hook toggle.
- **Track B Status**: MVP implementation in progress.
  - `MVP-ADM01-01`: DONE-LOCAL (47 tests passed, live DB verification pending staging test tenant).
  - `MVP-ADM01-02`: DONE-LOCAL (39 validation tests passed, live DB verification pending).
  - `MVP-ADM01-03`: DONE-LOCAL (12 HTTP tests passed, 305/305 ADM-01 passed; UI NOT IMPLEMENTED).
  - `MVP-ADM01-04`: DONE-LOCAL / LIVE VERIFICATION PENDING STAGING (Tenant Provisioning State Machine Transition Safety with authorized retry; 51 targeted tests passed, 356/356 ADM-01 passed; UI NOT IMPLEMENTED).
- **Module Health**: ADM-01 (356 passed, 0 skipped), ADM-02 (82 passed, 31 skipped), ACD-01 (68 passed, 22 skipped). Skipped tests are cleanly gated offline pending staging tenant.
- **Absolute Blockers**: Staging writes blocked pending explicit user `GO` approvals; no live DB writes or prod access permitted.
- **Immediate Next Task**: `MVP-ADM01-06` — Platform vs tenant tier isolation regression tests.
- **Historical Archive**: Phases 0–3 archived to [HANDOVER_PHASES_0_3.md](file:///d:/CampXSync/CampXSync_V2.0/docs/handover/archive/HANDOVER_PHASES_0_3.md).

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

---

## Phase 4 Continuation — Handover (2026-10-13)

### 1. STAGING (Track A)
- **Current Status**: READ-ONLY VERIFICATION COMPLETE / WRITES GATED AWAITING USER APPROVAL.
- **Last Verified Successful Step**: Clean Rule 11 Boolean environment assertion, full read-only catalog introspection match, explicit allowlist of 24 monthly partition child tables identified, `core.tenants` schema column check corrected (`code` instead of `slug`).
- **Exact Outstanding Steps**:
  1. User approval for `GO PARTITION-RLS-FIX` (applies RLS and revokes direct `authenticated` privileges on the 24 identified monthly child partitions).
  2. User approval for `GO PROVISION-TEST-TENANT` (provisions staging test tenant `CAMPX_STAGING` with UUID `11111111-1111-1111-1111-111111111111`).
  3. User confirmation in Supabase Dashboard for Custom Access Token hook (`iam.custom_access_token_hook`).
- **Command / Operation Required**: Awaiting explicit user prompt phrases `GO PARTITION-RLS-FIX` and `GO PROVISION-TEST-TENANT`.
- **Expected Result**: Child partitions secured with zero public/authenticated access leaks, test tenant fixture ready for live database integration tests.
- **Remaining Completion Criteria**: `CRIT-06`, `CRIT-09`, `CRIT-10`, `CRIT-14`, `CRIT-17`, `CRIT-18`, `CRIT-19`, `CRIT-20`.

### 2. MVP (Track B)
- **Current Status**: `MVP-ADM01-03` COMPLETED & VALIDATED; BROWSER UI VERIFICATION EXECUTED.
- **Highest-Priority Independent Task**: `MVP-ADM01-04` (Tenant Provisioning State Machine Transition Safety) or `MVP-ADM02-02` (College Governance Document Immutability).
- **Work Completed**:
  - Implemented `MVP-ADM01-03`:
    - Added HTTP REST endpoints in `InstituteAdminController.java` for `/api/v1/admin/role-templates` (GET, POST) and `/api/v1/admin/role-templates/{idOrCode}` (GET, PUT, DELETE) and permissions (GET, POST, DELETE).
    - Added comprehensive input validation: required fields, forbidden fields (`id`, `createdAt`, `updatedAt`, `deletedAt`, `version`, `etag`), RFC 7807 error responses, Location & ETag headers.
    - Added transactional outbox propagation for aggregate `RoleTemplate`: `RoleTemplateCreated`, `RoleTemplateUpdated`, `RoleTemplateDeleted`, `RoleTemplatePermissionBound`, `RoleTemplatePermissionUnbound`.
    - Added audit event logging via `CampXLogger.audit` with `AuditEvent.builder()` for all template actions.
    - Enforced platform administrator authorization (`UserProfileAccessDeniedException` mapped to HTTP 403 Forbidden).
    - Created `RoleTemplateControllerHttpTest.java` running 12 integration tests over real HTTP server sockets.
- **Automated Tests Executed**:
  - `RoleTemplateControllerHttpTest`: 12/12 PASSED (0 failures, 0 errors, 0 skipped).
  - ADM-01 Full Unit/Validation Suite: 305/305 PASSED (0 failures, 0 errors, 0 skipped).
  - ADM-02 Full Unit/Regression Suite: 82/82 PASSED (0 failures, 0 errors, 31 live tests cleanly skipped).
  - ACD-01 Full Unit/Regression Suite: 68/68 PASSED (0 failures, 0 errors, 22 live tests cleanly skipped).
- **Browser UI Testing**:
  - Environment: Local Vite dev server at `http://127.0.0.1:3000/`.
  - Inspected frontend routes in `frontend-web/src/App.jsx`.
  - Role Template UI Status: `FRONTEND_NOT_IMPLEMENTED` (confirmed no tab or route exists for role templates).
  - Supported UI Journeys Exercised: Successfully navigated and verified Colleges (`#nav-tab-colleges`), Academic Calendars (`#nav-tab-calendars`), Number Sequences (`#nav-tab-sequences`), Audit & Governance (`#nav-tab-governance`), and Gateway & RLS Security (`#nav-tab-diagnostics`). All verified `PASS`.
  - Recorded session: `campx_ui_flow_1791901486467.webp`.
- **Files Changed in Source Code**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/controller/InstituteAdminController.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/controller/RoleTemplateControllerHttpTest.java` (New)
  - `docs/mvp/03-progress.md` (New)
  - `.planning/STATE.md`
  - `.planning/workstreams/track-b/STATE.md`
  - `docs/handover/HANDOVER.md`
- **Next Implementation Task**: `MVP-ADM01-04` (Tenant Provisioning State Machine Transition Safety).

### 3. OVERALL
- **What Must NOT Be Repeated**:
  - Do NOT rerun completed catalog checks or schema audits.
  - Do NOT rerun `80_seed.sql` or `30_foreign_keys.sql`.
  - Do NOT modify production (`ekspahbsbwdpraaqfqui`).
  - Do NOT reimplement completed Role Template repository or controller endpoints.
- **What is Blocked and Exactly Why**:
  - Staging database writes are blocked pending explicit user approval phrases `GO PARTITION-RLS-FIX` and `GO PROVISION-TEST-TENANT`.
  - Live PostgreSQL integration tests against Supabase staging are gated until test tenant `11111111-1111-1111-1111-111111111111` and test users are provisioned.
- **What Can Continue Immediately**:
  - Track B local MVP tasks can proceed completely independently without waiting for database writes.
- **Next Milestone for Each Track**:
  - **Track A**: User approval of `GO PARTITION-RLS-FIX` and `GO PROVISION-TEST-TENANT`.
  - **Track B**: `MVP-ADM01-04` (Tenant Provisioning State Machine Transition Safety) — COMPLETED.

---

## Phase 5 Cycle 1 — MVP Implementation Handover (2026-10-14)

### 1. STAGING (Track A)
- **Status**: UNCHANGED / READ-ONLY VERIFICATION COMPLETE / WRITES GATED AWAITING USER APPROVAL.
- No writes or database modifications executed.

### 2. MVP (Track B)
- **Status**: `MVP-ADM01-04` COMPLETED & VALIDATED.
- **Implemented**:
  - Pure state machine `TenantProvisioningStateMachine`: transitions allowed `REQUESTED->IN_PROGRESS`, `IN_PROGRESS->COMPLETED`, `IN_PROGRESS->FAILED`. `COMPLETED` final; all other pairs reject with `InvalidStatusTransitionException`.
  - Authorized retry: `FAILED->IN_PROGRESS` allowed only via platform-admin-restricted `retryProvisioningJob(...)`; records prior failure reason, attempt increment, timestamp, user; emits `TenantProvisioningRetried` outbox and audit events.
  - Concurrency & isolation: Postgres conditional updates with HTTP 409 conflict detection; InMemory repository partitioned by tenant ID (`Map<UUID, Map<UUID, TenantProvisioning>>`) with thread-safe synchronized job locking.
- **Tests**:
  - Targeted tests: 51/51 PASSED (`TenantProvisioningStateMachineTest` 20/20, `TenantProvisioningValidationTest` 31/31).
  - ADM-01 offline test suite: 356/356 PASSED, 0 failures, 0 skipped.
- **Next Task**: `MVP-ADM01-06` (Platform vs tenant tier isolation regression tests).
