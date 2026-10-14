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
    - `V20261014_02__provision_test_tenant_staging.sql` & foreign-key aware rollback.

---

## 2. Browser UI Verification

Environment: local Vite dev server, `http://127.0.0.1:3000/`.

| Journey | Status | Notes |
|---|---|---|
| Navigation smoke: Colleges, Academic Calendars, Number Sequences, Audit & Governance, Gateway & RLS Security tabs | SMOKE PASS | Tabs loaded; UI feedback displays real connection/API errors when backend is offline instead of silent mock fallbacks. |
| Supabase Auth Modal & State | PASS-LOCAL | Modal renders on click, validates credentials via `@supabase/supabase-js`, attaches Bearer token, shows dynamic header pill with role/tenant. |
| Role template management (MVP-ADM01-03) | API READY / UI PENDING | Endpoints active in `api.js` (`getRoleTemplates`, `createRoleTemplate`, `updateRoleTemplate`, `deleteRoleTemplate`). Dedicated tab pending ADM01-05. |
| Tenant provisioning state machine (MVP-ADM01-04) | BACKEND COMPLETE | State machine and repository safety verified with 51 tests. |

---

## 3. Module Health

| Module | Offline tests passed | Failures | Skipped (live-gated) | Live DB status |
|---|---|---|---|---|
| ADM-01 | 374 (356 + 18 tier isolation) | 0 | 0 | Pending test tenant |
| ADM-02 | 82 (REPORTED) | 0 | 31 | Pending test tenant |
| ACD-01 | 68 (REPORTED) | 0 | 22 | Pending test tenant |
| Frontend Web | 9 (automated node:test) | 0 | 0 | Vite build: 0 errors |

---

## 4. Owner Decisions (2026-10-14)

1. **Provisioning retry (ADM01-04)**: FAILED → IN_PROGRESS only through an authorized, recorded, platform-admin retry operation with conditional updates; COMPLETED is final; all other backwards transitions rejected.
2. **Role template UI (ADM01-05)**: build the smallest usable UI on the existing endpoints; authorization intact.
3. **Approvals (ADM02-02)**: identical duplicate returns the same success with no repeated side effect, audit event or downstream action; incompatible request returns a conflict.

---

## 5. Open Items & Gated Staging Actions

- **Staging Partition RLS Migration**: `database/migrations/V20261014_01__enable_rls_on_monthly_partition_children.sql` prepared. Requires owner command `GO PARTITION-RLS-FIX`.
- **Staging Test Tenant Provisioning**: `database/migrations/V20261014_02__provision_test_tenant_staging.sql` prepared. Requires owner command `GO PROVISION-TEST-TENANT`.
- **Supabase Dashboard**: Confirm Custom Access Token hook toggle in Dashboard under `Authentication -> Hooks -> Custom Access Token` pointing to `iam.custom_access_token_hook`.
- **CodeRabbit**: CLI available; run `coderabbit review --uncommitted` once server-side branch sync is available.

---

## 6. Next Implementation Tasks

1. `MVP-ADM02-02` — Governance immutability and idempotent approvals (LOCAL)
2. `MVP-ADM01-05` — Role template management tab in frontend UI (LOCAL)
3. `MVP-ACD01-02` — Course offering status transitions (LOCAL)
4. `MVP-E2E-01` / `MVP-E2E-02` — Staging remediation execution & Live E2E Colleges slice (STAGING-GATED)


