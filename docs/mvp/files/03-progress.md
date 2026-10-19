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
  - CodeRabbit: not run; exact command not yet provided.
  - OPEN: confirm whether PUT/DELETE honor `If-Match` (ETag is returned on GET).
  - Live verification: platform-admin-only mutation and non-admin read behavior under the real RLS policy.
  - No commit proposed; work remains uncommitted.

---

## 2. Browser UI Verification

Environment: local Vite dev server, `http://127.0.0.1:3000/`, mock principal `CAMPX_ADMIN` (platform admin).
Evidence: recording `campx_ui_flow_1791901486467.webp`. Console errors: none observed.

| Journey | Status | Notes |
|---|---|---|
| Navigation smoke: Colleges, Academic Calendars, Number Sequences, Audit & Governance, Gateway & RLS Security tabs | SMOKE PASS | Tabs loaded; no data-changing action performed or verified. This is not a journey PASS. |
| Role template management (MVP-ADM01-03) | NOT IMPLEMENTED | Cause: `FRONTEND_NOT_IMPLEMENTED`. `App.jsx` has no tab, route or component for role templates. |
| User profile management UI | UNKNOWN | Not listed among tabs; see backlog ADM01-07. |
| Course management journeys | UNKNOWN | Not exercised; see backlog ACD01-03. |

Authorization-denial and validation scenarios through the UI: not tested (no applicable screen).

---

## 3. Module Health

| Module | Offline tests passed | Failures | Skipped (live-gated) | Live DB status |
|---|---|---|---|---|
| ADM-01 | 305 (REPORTED) | 0 | 0 | Pending test tenant |
| ADM-02 | 82 (REPORTED) | 0 | 31 | Pending test tenant |
| ACD-01 | 68 (REPORTED) | 0 | 22 | Pending test tenant |

Skipped tests are not passes.

---

## 4. Owner Decisions (2026-10-14)

1. **Provisioning retry (ADM01-04)**: FAILED → IN_PROGRESS only through an authorized, recorded, platform-admin retry operation with conditional updates; COMPLETED is final; all other backwards transitions rejected.
2. **Role template UI (ADM01-05)**: build the smallest usable UI only if existing prompt, user stories or `.planning` documents require it; otherwise record NOT IMPLEMENTED. Authorization intact either way.
3. **Approvals (ADM02-02)**: identical duplicate returns the same success with no repeated side effect, audit event or downstream action; incompatible request returns a conflict.

---

## 5. Open Items

- Housekeeping pending: commit proposal for uncommitted work (owner approval required), HANDOVER.md archive/CURRENT STATE block.
- CodeRabbit not yet run for MVP-ADM01-02 and MVP-ADM01-03.
- ADM01-02 exception-type deviation (section 1).
- Track A writes (`GO PARTITION-RLS-FIX`, `GO PROVISION-TEST-TENANT`) and Auth hook confirmation await the owner. All live verification depends on them.

## 6. Next Tasks

1. `MVP-ADM01-04` — Tenant provisioning state machine with authorized retry (LOCAL)
2. `MVP-ADM01-06` — Platform vs tenant tier isolation regression tests (LOCAL)
3. `MVP-ADM01-05` — Role template UI, decision gate first
4. `MVP-ADM02-02` — Governance immutability and idempotent approvals
5. `MVP-ADM02-03`, `MVP-ADM01-07`, `MVP-ACD01-03` — read-only scope and UI checks
6. `MVP-ACD01-02` — Course offering status transitions
