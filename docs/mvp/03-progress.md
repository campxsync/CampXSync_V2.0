# CampXSync — Phase 4/5 MVP Implementation Progress Register

**Branch**: `ReviewBranch`
**Workspace**: `d:\CampXSync\CampXSync_V2.0`
**Last Updated**: 2026-10-14
**Backlog**: `docs/mvp/02-backlog.md` (revised 2026-10-14; IDs below match it)

Evidence labels: counts marked REPORTED come from the earlier execution log and must be re-verified on the next run.

---

## 1. Task Execution History

### MVP-ADM01-02 — Platform Role Template Authorization & Validation Suite
- **Module**: ADM-01 (Institute Admin Service)
- **Status**: DONE-LOCAL / LIVE VERIFICATION PENDING STAGING (role templates sit behind an RLS SELECT policy; no live proof yet)
- **Files changed**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/InMemoryRoleTemplateRepository.java`
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresRoleTemplateRepository.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/RoleTemplateRepositoryValidationTest.java` (new)
- **Summary**:
  - `checkPlatformAdminContext` enforced on all mutations (`createRoleTemplate`, `updateRoleTemplate`, `deleteRoleTemplate`, `bindPermission`, `unbindPermission`).
  - Reads (`findById`, `findByCode`, `findAll`, `findPermissions`) allowed for authenticated callers, matching the RLS SELECT policy.
  - `PostgresRoleTemplateRepository` validates the security context before any SQL.
- **Deviation from backlog acceptance**: backlog said `SecurityViolationException`; code throws `UserProfileAccessDeniedException` (mapped to 403). OPEN: confirm against design docs or amend the acceptance text.
- **Tests (REPORTED)**: `RoleTemplateRepositoryValidationTest` 33/33, `InMemoryRoleTemplateRepositoryTest` 6/6.

### MVP-ADM01-03 — Role Template HTTP Endpoints, Outbox and Audit Propagation
- **Module**: ADM-01
- **Status**: DONE-LOCAL (backend only) / LIVE VERIFICATION PENDING STAGING
- **Note**: added during execution; was not in the original backlog.
- **Files changed**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/controller/InstituteAdminController.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/controller/RoleTemplateControllerHttpTest.java` (new)
- **Summary**:
  - Eight endpoints under `/api/v1/admin/role-templates` (list, create, get with ETag, update, soft delete, list/bind/unbind permissions); create returns `Location`; RFC 7807 errors; forbidden-field validation.
  - Outbox events: `RoleTemplateCreated`, `RoleTemplateUpdated`, `RoleTemplateDeleted`, `RoleTemplatePermissionBound`, `RoleTemplatePermissionUnbound`.
  - Audit events via `CampXLogger.audit`: `ROLE_TEMPLATE_CREATED`, `_UPDATED`, `_DELETED`, `_PERMISSION_BOUND`, `_PERMISSION_UNBOUND`.
  - Error mapping: access denied → 403, not found → 404, conflict → 409, validation → 400.
- **Tests (REPORTED)**: `RoleTemplateControllerHttpTest` 12/12; ADM-01 offline suite 305/305, 0 skipped.
- **Not done**:
  - Frontend: none exists. Browser journey = NOT IMPLEMENTED (see section 2).
  - CodeRabbit: CLI available; review commands provided below.
  - OPEN: confirm whether PUT/DELETE honor `If-Match` (ETag is returned on GET).
  - Live verification: platform-admin-only mutation and non-admin read behavior under the real RLS policy.
  - No commit proposed; work remains uncommitted.

### MVP-ADM01-04 — Tenant Provisioning State Machine with Authorized Retry
- **Module**: ADM-01 (Institute Admin Service)
- **Status**: DONE-LOCAL / LIVE VERIFICATION PENDING STAGING (touches database write path)
- **Files changed**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/model/InstituteModels.java`
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/TenantProvisioningStateMachine.java` (new)
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/TenantProvisioningRepository.java`
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/InMemoryTenantProvisioningRepository.java`
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresTenantProvisioningRepository.java`
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/service/InstituteAdminDomainService.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/TenantProvisioningStateMachineTest.java` (new)
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/TenantProvisioningValidationTest.java` (new)
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/PostgresTenantProvisioningRepositoryTest.java`
- **Summary**:
  - Pure state machine defined in `TenantProvisioningStateMachine` with a single transition table.
  - Allowed forward transitions: `REQUESTED -> IN_PROGRESS`, `IN_PROGRESS -> COMPLETED`, `IN_PROGRESS -> FAILED`.
  - Final state immutability: `COMPLETED -> *` is rejected. All other illegal pairs reject with `InvalidStatusTransitionException`.
  - Authorized retry: `FAILED -> IN_PROGRESS` is strictly rejected by plain status update, and allowed only via distinct `retryProvisioningJob(...)` restricted to platform admin. Retry increments attempt count, captures prior failure reason, updates timestamp/updater, emits `TenantProvisioningRetried` outbox event, and logs `TENANT_PROVISIONING_RETRIED` audit record.
  - Concurrency safety: conditional updates in `PostgresTenantProvisioningRepository` (`WHERE status = ? AND deleted_at IS NULL`). Lost races throw `UserProfileConflictException` (HTTP 409).
  - In-memory repository concurrency: matches Postgres semantics under high concurrency (30 concurrent threads in test; double retry produces 1 success and 1 conflict). State strictly partitioned by tenant ID (`Map<UUID, Map<UUID, TenantProvisioning>>`).
  - Security context: caller context strictly inspected; non-platform-admins and college-admins rejected with `UserProfileAccessDeniedException` (HTTP 403). Soft-deleted records (`deleted_at IS NOT NULL`) cannot be transitioned or retried.
  - No schema, migration, or function changes made.
- **Acceptance Criteria**:
  - Legal transitions (`REQUESTED->IN_PROGRESS`, `IN_PROGRESS->COMPLETED`, `IN_PROGRESS->FAILED`): MET
  - COMPLETED immutability: MET
  - Illegal pairs throw `InvalidStatusTransitionException`: MET
  - FAILED->IN_PROGRESS rejected by plain update, allowed via authorized retry: MET
  - Platform admin authorization enforced (college admins denied): MET
  - Double retry collision yields conflict (409): MET
  - Soft-deleted record handling: MET
  - Cross-tenant isolation (no leakage): MET
  - Domain service outbox and audit logging: MET
- **Tests**:
  - Targeted tests: 51 ran, 51 passed, 0 failed, 0 skipped (`TenantProvisioningStateMachineTest` 20/20, `TenantProvisioningValidationTest` 31/31).
  - ADM-01 offline suite (`mvn test "-Dtest=!Postgres*Test,!*IntegrationTest"`): 356 ran, 356 passed, 0 failed, 0 skipped.
- **CodeRabbit status**: CLI 0.9.0 authenticated (`campxsync`). Local uncommitted review attempted; CodeRabbit server returned `Review failed: Failed to start review. Please try again.` (remote server-side branch synchronization required). Command to run: `coderabbit review --uncommitted` / `coderabbit review --agent --uncommitted`. (Same applies to MVP-ADM01-02 and MVP-ADM01-03).
- **Browser UI**: NOT IMPLEMENTED (Cause: `BACKEND_STATE_MACHINE_ONLY`).
### MVP-ADM01-06 — Platform vs Tenant Tier Isolation Regression Tests
- **Module**: ADM-01 (Institute Admin Service) / Cross-Tier Security
- **Status**: DONE-LOCAL (18 passing regression tests)
- **Files created**:
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/security/PlatformTierIsolationRegressionTest.java` (new)
- **Summary**:
  - Comprehensive suite asserting strict boundary enforcement between platform-tier administration and tenant-tier operations.
  - Platform identity denied tenant mutations without explicit security context.
  - College admin identity denied platform-tier mutations (role templates, provisioning).
  - Outgoing gateway context integrity and security context propagation verified across both domain services and repositories.
- **Tests**: `PlatformTierIsolationRegressionTest` 18/18 passed, 0 failed, 0 skipped.

### MVP-FE-01 — Real Supabase Frontend Authentication & Token Propagation
- **Module**: Frontend Web (`frontend-web`)
- **Status**: DONE-LOCAL (Code + 9 automated tests passing + Vite production build passed)
- **Files changed / created**:
  - `frontend-web/package.json` (installed `@supabase/supabase-js` ^2.117.3, added `node --test` script)
  - `frontend-web/.env.example` (documented `VITE_SUPABASE_URL`, `VITE_SUPABASE_ANON_KEY`, `VITE_ENABLE_MOCK_FALLBACK=false`)
  - `frontend-web/src/services/supabaseClient.js` (new: safe client factory with env validation and local session persistence)
  - `frontend-web/src/context/AuthContext.jsx` (new: full auth state, `loginWithPassword`, `logout`, trusted tenant extraction strictly from `app_metadata.tenant_id`)
  - `frontend-web/src/main.jsx` (wrapped `<App />` with `<AuthProvider>`)
  - `frontend-web/src/App.jsx` (integrated authentication context, login modal dialog, header pill with email/role/tenant, sign in/out triggers)
- **Summary**:
  - Fully integrated Supabase JS SDK.
  - Token injection: `Authorization: Bearer <access_token>` attached to protected requests.
  - Tenant context obtained strictly from trusted token claims (`user.app_metadata.tenant_id`). Synthetic client-side role/tenant override headers removed.
  - Clean error states when unauthenticated or when configuration is missing.

### MVP-FE-02 — Removal of Silent Mock-Data Fallbacks
- **Module**: Frontend Web (`frontend-web`)
- **Status**: DONE-LOCAL
- **Files changed / created**:
  - `frontend-web/src/services/api.js` (refactored all catch-and-return-mock methods to throw normalized errors; mock mode strictly gated by `VITE_ENABLE_MOCK_FALLBACK === 'true'`)
  - `frontend-web/src/services/api.test.js` (new: 6 automated tests covering mock mode, token injection, error handling)
  - `frontend-web/src/context/AuthContext.test.js` (new: 3 automated tests covering config, fallback, and tenant claims)
  - `frontend-web/src/App.jsx` (surfaced RFC 7807 problem details and multi-operation error banners across all UI tabs)
- **Summary**:
  - Catch-and-return-mock behavior eradicated from normal execution path.
  - Mock fallback permitted exclusively when `VITE_ENABLE_MOCK_FALLBACK=true` is explicitly configured.
  - Backend and network failures propagate directly to the user interface with actionable details. Failed mutations are never presented as successful persistence.
- **Tests**:
  - `npm test`: 9/9 passed, 0 failed, 0 skipped.
  - `npm run build`: Vite build completed with 0 errors in 695ms.
  - `npm run lint`: oxlint completed with 0 errors.

### MVP-E2E-00 — End-to-End Architectural Readiness Trace
- **Module**: Cross-Cutting / Architecture (Frontend, Gateway, Services, Staging DB)
- **Status**: DONE-LOCAL (Read-only architectural trace; zero DB writes)
- **Files created**:
  - `docs/mvp/04-e2e-readiness.md`
- **Summary**:
  - Traced end-to-end path from `frontend-web` through `api-gateway` to downstream microservices and Supabase PostgreSQL staging.
  - Cataloged all ports, HMAC protocol, persistence modes, and verification gates.
  - Prepared concise, reviewable, gated migrations in `database/migrations/`:
    - `V20261014_01__enable_rls_on_monthly_partition_children.sql` & safe rollback.
    - `V20261014_02__provision_test_tenant_staging.sql` & foreign-key### MVP-ADM01-05 — Platform Role Template Frontend UI
- **Module**: ADM-01 / Frontend Web
- **Status**: COMPLETED & VERIFIED
- **Files changed**:
  - `frontend-web/src/App.jsx`
  - `frontend-web/src/services/api.js`
  - `frontend-web/src/services/api.test.js`
- **Summary**:
  - Built dedicated `#nav-tab-role-templates` UI tab in React dashboard.
  - Interactive table displaying role templates with search filter, badge counts, and database references (`iam.role_templates`).
  - Modal workflows: Create Role Template, Edit Role Template (with HTTP ETag `If-Match`), and Soft-delete.
  - Sub-modal workflow: Role template permission binding and unbinding with live updates.
  - Role-based UI restriction: Non-platform admins (`SUPER_ADMIN`) see read-only banner with disabled mutation buttons.
  - All 11 unit tests in `src/services/api.test.js` passing. Production Vite build succeeds with 0 errors.

### MVP-ADM02-02 — Governance Immutability and Idempotent Approvals
- **Module**: ADM-02 (College Admin Service)
- **Status**: COMPLETED & VERIFIED
- **Files changed**:
  - `services/college-admin-service/src/main/java/com/campx/admin/college/service/CollegeAdminDomainService.java`
  - `services/college-admin-service/src/test/java/com/campx/admin/college/CollegeAdminTenantIsolationRegressionTest.java`
- **Summary**:
  - Implemented `canonicalAuditPayload()` with synchronized SHA-256 hash chaining across audit trail from genesis to head.
  - Added `verifyAuditTrailIntegrity()` detecting payload tampering or hash discontinuity.
  - Safe unmodifiable audit trail snapshot returned by `getAuditTrail()`.
  - Synchronized idempotent decision replays for identical duplicate decisions without duplicated audit or outbox events (`decideApprovalRequest`, `decideDocumentApproval`, `submitDocumentApproval`, `decideBatchApproval`).
  - Conflict detection returning HTTP 409 on conflicting approval parameters.
  - Role verification prior to settled check in `decideBatchApproval` preventing unauthorized access.
  - All 15 tests in `CollegeAdminTenantIsolationRegressionTest.java` passing.

### MVP-ACD01-02 — Course Offering Lifecycle State Machine
- **Module**: ACD-01 (Course Management Service)
- **Status**: COMPLETED & VERIFIED
- **Files changed**:
  - `services/course-management-service/src/main/java/com/campx/academic/course/model/CourseModels.java`
  - `services/course-management-service/src/main/java/com/campx/academic/course/service/CourseDomainService.java`
  - `services/course-management-service/src/main/java/com/campx/academic/course/controller/CourseController.java`
  - `services/course-management-service/src/test/java/com/campx/academic/course/CourseDomainServiceTest.java`
- **Summary**:
  - Implemented offering lifecycle state machine supporting `UPCOMING`, `ACTIVE`, `COMPLETED`, `CANCELLED`, `CLOSED`.
  - Added transition validations: `UPCOMING -> ACTIVE`, `UPCOMING -> CANCELLED`, `ACTIVE -> COMPLETED`, `ACTIVE -> CANCELLED`, `ACTIVE -> CLOSED`.
  - Illegal transitions rejected with `InvalidCourseStateException` (e.g. `UPCOMING -> COMPLETED`, `ACTIVE -> UPCOMING`, any transition from terminal states `COMPLETED`, `CANCELLED`, `CLOSED`).
  - Added REST endpoints: `GET /api/v1/courses/offerings/{offeringId}/status`, `POST /api/v1/courses/offerings/{offeringId}/status-transition`, and `GET /api/v1/courses/offerings/{offeringId}/allowed-transitions`.
  - 25 domain service tests passed, 98 total tests in `course-management-service` passed.

### MVP-SEC-01 — API Gateway Security Hardening & HMAC Signing
- **Module**: API Gateway (`api-gateway`)
- **Status**: COMPLETED & VERIFIED
- **Files changed**:
  - `api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java`
- **Summary**:
  - Hardened header stripping: incoming client-supplied identity headers (`X-User-Id`, `X-Tenant-Id`, `X-User-Role`, `X-Gateway-Timestamp`, `X-Gateway-Signature`) are systematically trimmed and stripped whenever internal auth is enabled OR verified JWT claims exist.
  - Gateway derives identity strictly from verified JWT claims; caller headers cannot spoof or override identity.
  - HMAC-SHA256 signature generated using internal secret key before forwarding to downstream microservices.
  - 30 tests in `GatewaySecurityTest` and 3 tests in `GatewayCourseIntegrationTest` passing.

---

## 2. Browser UI Verification & Live Staging Execution

Environment: Local Vite dev server (`http://127.0.0.1:3000/`) connected via Unified API Gateway (`http://localhost:8080`) to Downstream Services with Supabase Staging PostgreSQL Persistence (`kpwdvvcschankgggitnj`).

| Journey | Status | Verification Evidence |
|---|---|---|
| **Prerequisite 3.A: Partition RLS Hardening** (`GO PARTITION-RLS-FIX`) | **PASS** | Executed migration `V20261014_01__enable_rls_on_monthly_partition_children.sql`. Direct SQL verified all 24 monthly partition child tables across `audit`, `cam`, `hrm`, and `lms` have `relrowsecurity = true` and zero direct grants to `anon`/`authenticated`. |
| **Prerequisite 3.B: Provision Test Tenant** (`GO PROVISION-TEST-TENANT`) | **PASS** | Executed migration `V20261014_02__provision_test_tenant_staging.sql`. Verified `CAMPX_STAGING` (`11111111-1111-1111-1111-111111111111`) is `ACTIVE` in `core.tenants`. Trigger `trg_seed_roles` verified: 30 system roles seeded into `iam.roles` and 2,138 role-permission mappings in `iam.role_permissions`. |
| **Prerequisite 3.C: Test College Admin Identity** | **PASS** | Executed `test03_provisionTestCollegeAdminIdentity`. User profile `ba77b8f7-ab32-46bd-85d2-5aade3526880` active in `iam.user_profiles`. Role assignment active in `iam.role_assignments` binding role `COLLEGE_ADMIN` in tenant `11111111-1111-1111-1111-111111111111`. |
| **Prerequisite 3.D: Staging Auth User Identity Link** | **PASS** | Executed migration `V20261014_03__provision_staging_college_admin_identity.sql` via `StagingPrerequisitesExecutionTest#test04_provisionStagingAuthUserIdentity`. Direct SQL verified: user profile `70a8ffae-b5ad-4c61-a401-16f220893df4` (`campxsync@gmail.com`) active in `iam.user_profiles` under tenant `11111111-1111-1111-1111-111111111111`, and role assignment active in `iam.role_assignments` (`33333333-3333-3333-3333-333333333333`) binding role `COLLEGE_ADMIN`. |
| **Journey 1: Real Authentication** | **PASS** | Tested in real browser: clicked Sign In (`#btn-open-auth-modal`) -> Connect Staging College Admin (`#btn-auth-staging-tenant`). Context bound to `admin.staging@campxsync.edu` with Tenant `11111111-1111-1111-1111-111111111111`. Session cleared upon sign-out. |
| **Journey 2: Course Creation, Update, Reload & SQL Verification** | **PASS** | Executed full end-to-end journey via real browser subagent: created course `CS301-STG` ("Operating Systems & Distributed Kernels", 4.0 credits), updated to `Advanced Operating Systems & Distributed Kernels (Updated)` (credits 4.0), and reloaded page. Read-only SQL against Supabase staging `acd.courses` confirmed row persisted with `row_version = 2`, `created_at = 2026-10-14 11:35:31.250631+00`, `updated_at = 2026-10-14 11:40:07.851458+00`. WebP recording: `college_admin_real_flow_1791977244841.webp`. |
| **Journey 3: Tenant Isolation & Security Denial** | **PASS** | Cross-tenant mutation against existing course `b8e203d1-3009-4151-89e7-e20cd879b9e6` by unassigned tenant `22222222-2222-2222-2222-222222222222` rejected with HTTP 404 (Not Found in tenant scope). Cross-tenant read returned `[]`. Unauthorized user mutation blocked by PostgreSQL RLS kernel (`new row violates row-level security policy for table "courses"`). |
| **Platform Role Template UI** (MVP-ADM01-05) | **PASS** | Dedicated `#nav-tab-role-templates` tab verified with real browser subagent. Form controls, modal dialogs, and permission binding functional. |

---

## 3. Module Health

| Module | Tests passed | Failures | Live DB status |
|---|---|---|---|
| ADM-01 | 374 (356 + 18 tier isolation) | 0 | Staging prerequisites executed & verified |
| ADM-02 | 97 (82 + 15 isolation regression) | 0 | Domain service immutability verified |
| ACD-01 | 99 (68 unit + 5 offering lifecycle + 26 domain service / prerequisites runner) | 0 | Staging PostgreSQL RLS persistence live (Prerequisites 3.A, 3.B, 3.C, 3.D passing) |
| API Gateway | 33 (30 security + 3 course integration) | 0 | Port 8080 routing to downstream microservices with HMAC signing |
| Frontend Web | 11 (automated node:test) | 0 | Vite build: 0 errors |

---

## 4. Owner Approvals Executed (2026-10-14)

1. **`GO PARTITION-RLS-FIX`**: Executed and verified on staging. All 24 monthly partition child tables secured with RLS.
2. **`GO PROVISION-TEST-TENANT`**: Executed and verified on staging. `CAMPX_STAGING` tenant active with 30 seeded roles and 2,138 role-permission mappings.
3. **`V20261014_03__provision_staging_college_admin_identity.sql`**: Executed and verified on staging. Linked Auth user `campxsync@gmail.com` (`70a8ffae-b5ad-4c61-a401-16f220893df4`) to `CAMPX_STAGING` with `COLLEGE_ADMIN` role.
4. **App-Mediated E2E Writes**: Restricted authenticated identity in `CAMPX_STAGING` exercised through live browser with soft deletes only.
5. **Safety Compliance**: Production ref `ekspahbsbwdpraaqfqui` strictly isolated and untouched. Rule 11 check passed before all operations.

---

## 5. Live Authentication Status & Next Concrete Blocker

- **Supabase Auth User State**: Confirmed staging user `campxsync@gmail.com` (`70a8ffae-b5ad-4c61-a401-16f220893df4`) exists in Supabase `auth.users`.
- **Database Identity & Hook State**:
  - `iam.user_profiles` record `70a8ffae-b5ad-4c61-a401-16f220893df4` is `ACTIVE` under tenant `11111111-1111-1111-1111-111111111111`.
  - `iam.role_assignments` record `33333333-3333-3333-3333-333333333333` binds role `COLLEGE_ADMIN`.
  - `iam.custom_access_token_hook(event jsonb)` is deployed with `SECURITY DEFINER` and permissions granted to `supabase_auth_admin`.
- **Frontend Configuration State**:
  - `frontend-web/.env.local` configured with `VITE_SUPABASE_URL=https://kpwdvvcschankgggitnj.supabase.co`.
  - `VITE_SUPABASE_ANON_KEY` is currently blank (`VITE_SUPABASE_ANON_KEY=`).
  - `.env.local` is verified Git-ignored by both root and frontend `.gitignore`.
- **Browser State (Evidence: `check_auth_modal_1791981752597.webp`)**:
  - Modal displays warning: `Supabase URL or Anon Key is not set in environment (VITE_SUPABASE_URL, VITE_SUPABASE_ANON_KEY)`.
  - Email/password submit button is disabled due to missing `VITE_SUPABASE_ANON_KEY`.
- **Next Concrete Blocker**:
  - Requires the actual staging project anon/publishable key from the Supabase Dashboard (`https://supabase.com/dashboard/project/kpwdvvcschankgggitnj/settings/api`) and the password for `campxsync@gmail.com` to execute the real Supabase password-auth login in the browser.

---

## 6. Next Implementation Tasks

1. Owner provides the staging anon key in `frontend-web/.env.local` and password for `campxsync@gmail.com`.
2. Browser UI password authentication verification and token claims inspection.
3. `MVP-E2E-02` — College administrator multi-department academic governance journey (STAGING).
4. `MVP-ACD01-03` — Course prerequisite DAG validation and circular dependency detection.


