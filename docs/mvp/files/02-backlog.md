# Track B — MVP Execution Backlog (REVISED 2026-10-14)

**Document**: `docs/mvp/02-backlog.md`
**Branch**: `ReviewBranch`
**Binding roadmap**: `docs/MASTER_EXECUTION_PROMPT.md` (Phase 5 loop)
**Priority**: ADM-01 → ADM-02 → ACD-01

## 0. What changed in this revision

1. **ID reconciliation.** The original backlog defined `MVP-ADM01-03` as *Tenant Provisioning State Machine*. The implementation run instead used `MVP-ADM01-03` for *Role Template HTTP endpoints* (never in the backlog) and called provisioning `MVP-ADM01-04`. This revision adopts the IDs used in code, progress and handover files. Provisioning is now `MVP-ADM01-04`.
2. **Status vocabulary.** `DONE-LOCAL` = code + passing offline tests, no live proof. `DONE` is reserved for tasks that also have live verification (or never needed it). Tasks needing live RLS/auth-hook/cross-tenant proof stay `LIVE VERIFICATION PENDING STAGING` until Track A is COMPLETE.
3. **Stale counts fixed.** ADM-02 offline suite = 82 passed / 31 skipped; ACD-01 = 68 passed / 22 skipped; ADM-01 = 305 passed / 0 skipped (REPORTED by the execution log; re-verify when next run).
4. **Added tasks** marked `PROPOSED` are not yet confirmed against the ADM/ACD design docs; the first action of each is to confirm scope from the docs. Do not invent scope.
5. **Critical-path warning.** After the local backend tasks below, every remaining release-critical task is staging-gated. Track A decisions (`GO PARTITION-RLS-FIX`, `GO PROVISION-TEST-TENANT`, Auth hook confirmation) must be made before the local queue empties.

6. **Owner decisions recorded (2026-10-14).** See section 0.1. They are binding for ADM01-04, ADM01-05 and ADM02-02.

### 0.1 Owner decisions

- **Provisioning retries (ADM01-04).** FAILED may return to IN_PROGRESS only through an explicit, authorized retry operation. No other backwards transition is allowed. COMPLETED is final. The retry must be recorded, restricted to platform admins, and protected by conditional updates against concurrency.
- **Role template UI (ADM01-05).** Conditional on the existing master prompt, user stories and `.planning` documents requiring it. If required, build the smallest usable UI on the existing endpoints. If not required, record the journey as NOT IMPLEMENTED and do not invent a requirement. Authorization stays intact either way.
- **Duplicate approvals (ADM02-02).** Idempotent for identical requests, conflict for incompatible requests, and no repeated side effect, audit event or downstream action. Exact semantics are established by the implementation and its tests.

Classification: `LOCAL` (no staging), `LOCAL+INFRA` (needs test scaffolding), `STAGING-GATED`, `RELEASE-CRITICAL`.

---

## 1. Status board

| ID | Module | Title | Status | Class | Live verification |
|---|---|---|---|---|---|
| ADM01-01 | ADM-01 | Step 1 user profiles: live verification | DONE-LOCAL | STAGING-GATED / RELEASE-CRITICAL | Pending (CRIT-14) |
| ADM01-02 | ADM-01 | Role template authorization + validation suite | DONE-LOCAL | RELEASE-CRITICAL | Pending (RLS SELECT policy) |
| ADM01-03 | ADM-01 | Role template HTTP endpoints, outbox, audit | DONE-LOCAL | RELEASE-CRITICAL | Pending; **UI NOT IMPLEMENTED** |
| ADM01-04 | ADM-01 | Tenant provisioning state machine safety | NOT STARTED | LOCAL | Likely pending (DB write path) |
| ADM01-05 | ADM-01 | Role template UI (CONDITIONAL on requirements) | NOT STARTED | LOCAL+INFRA | n/a until backend live |
| ADM01-06 | ADM-01/02 | Platform vs tenant tier isolation regression tests (PROPOSED) | NOT STARTED | LOCAL | Pending |
| ADM01-07 | ADM-01 | User profile UI gap check (PROPOSED) | UNKNOWN | LOCAL | n/a |
| ADM02-01 | ADM-02 | Live college admin verification | DONE-LOCAL | STAGING-GATED / RELEASE-CRITICAL | Pending (CRIT-14) |
| ADM02-02 | ADM-02 | Governance immutability and idempotent approvals | PARTIAL | LOCAL | Pending |
| ADM02-03 | ADM-02 | Scope gap analysis: profile, departments, programs, settings (PROPOSED) | UNKNOWN | LOCAL (read-only) | n/a |
| ACD01-01 | ACD-01 | Live RLS enforcement on `acd.courses` | DONE-LOCAL | STAGING-GATED / RELEASE-CRITICAL | Pending (CRIT-14) |
| ACD01-02 | ACD-01 | Course offering status transitions (renamed) | PARTIAL | LOCAL | Pending |
| ACD01-03 | ACD-01 | Course UI journey check (PROPOSED) | UNKNOWN | LOCAL | n/a |

---

## 2. Task details

### ADM-01

#### MVP-ADM01-01 — Step 1 live verification
- **State**: Implemented locally, 47 tests (REPORTED).
- **Files**: `UserProfileRepository.java`, `UserProfileRepositoryLiveIntegrationTest.java` (path per original backlog; confirm).
- **Remaining**: run against staging pooler as the restricted `authenticated` identity once the test tenant exists.
- **Depends on**: `GO PROVISION-TEST-TENANT`, Auth hook confirmed.
- **Acceptance**: CRUD on `iam.user_profiles` succeeds under `authenticated` + tenant context; cross-tenant access denied; soft-deleted rows invisible.

#### MVP-ADM01-02 — Role template authorization (DONE-LOCAL)
- **Deviation to record**: original acceptance said `SecurityViolationException`; implementation throws `UserProfileAccessDeniedException` (mapped to 403). Either update the acceptance text to match, or confirm the exception type with the design docs. Do not silently leave the mismatch.
- **Tests**: 33 + 6 passed (REPORTED).

#### MVP-ADM01-03 — Role template HTTP endpoints (DONE-LOCAL)
- **Added outside original backlog.** Eight endpoints under `/api/v1/admin/role-templates`, five outbox event types, five audit events, RFC 7807 errors, ETag/Location.
- **Tests**: `RoleTemplateControllerHttpTest` 12/12 (REPORTED).
- **Open items**:
  - No frontend (see ADM01-05). Browser journey status = NOT IMPLEMENTED.
  - ETag is returned on GET; confirm PUT/DELETE honor `If-Match` (optimistic locking) or record that they don't.
  - Live check: platform-admin-only mutations and the RLS SELECT policy for non-admin callers.
  - CodeRabbit not yet run.

#### MVP-ADM01-04 — Tenant provisioning state machine safety
- **Files**: `TenantProvisioningRepository.java`, `PostgresTenantProvisioningRepository.java`, `InMemoryTenantProvisioningRepository.java`, `TenantProvisioningValidationTest.java` (new).
- **Remaining**: formal transition table REQUESTED → IN_PROGRESS → COMPLETED / FAILED; reject illegal and backwards transitions with `InvalidStatusTransitionException`.
- **Transition table (owner-approved)**:

  | From | To | Rule |
  |---|---|---|
  | REQUESTED | IN_PROGRESS | Allow |
  | IN_PROGRESS | COMPLETED | Allow |
  | IN_PROGRESS | FAILED | Allow |
  | FAILED | IN_PROGRESS | Allow only via the authorized retry operation |
  | COMPLETED | any other state | Reject |
  | any other pair | | Reject with `InvalidStatusTransitionException` |

- **Retry operation**: a distinct operation (not a plain status update). Platform-admin only. Records the retry (who, when, attempt count, prior failure reason) through the existing audit and outbox mechanisms. If recording the attempt count would need a schema change, stop, document it, and record the retry in the audit/outbox event only.
- **Concurrency**: the Postgres update is conditional on the expected current status (for example `UPDATE ... WHERE id = ? AND status = ?` and check the affected row count). A lost race returns a conflict, never a silent success. The in-memory repository must give the same semantics under concurrent calls. No shared in-memory fallback across tenants.
- **Implementation notes**: one transition table in one place. No schema change.
- **Tests**: every legal transition, every illegal pair, COMPLETED immutability, FAILED to IN_PROGRESS rejected via plain update but allowed via retry, retry recorded, non-admin denied, concurrent double-retry (one wins, one conflicts), soft-deleted record.
- **Staging**: no.

#### MVP-ADM01-05 — Role template UI (CONDITIONAL)
- **Step 1, decision gate**: read the master prompt, user stories and `.planning` documents. Record whether role-template management is required as a UI journey, with file and line evidence.
- **If required**: build the smallest usable UI on the existing endpoints, following the `App.jsx` tab pattern (`#nav-tab-*`) and `api.js`. Scope: list, create (code and name validation), edit, soft delete, bind/unbind permission, visible 403 state for non-platform-admins.
- **If not required**: record the journey as NOT IMPLEMENTED in `03-progress.md`. Do not build it and do not invent a requirement.
- **Acceptance (if built)**: real browser run covering a successful create, a validation error, a 403, and a visible result for each mutation. Console and network errors checked.
- **Either way**: authorization is not weakened.

#### MVP-ADM01-06 — Tier isolation regression tests (PROPOSED)
- **Source**: master prompt rule 4 (ADM-01 vs ADM-02 isolation). No such test is recorded.
- **Scope**: a college-admin identity cannot call platform-tier mutations (role templates, provisioning); a platform identity does not read tenant-tier data without tenant context; tenant comes only from the security context.
- **Staging**: offline version now; live variant pending.

#### MVP-ADM01-07 — User profile UI gap check (PROPOSED)
- **Why**: the browser run listed Colleges, Calendars, Sequences, Governance, Diagnostics. No users tab was mentioned (inference, not verified).
- **Action**: read-only check of `App.jsx` and routes; record result; add UI task only if the user stories require it.

### ADM-02

#### MVP-ADM02-01 — Live verification
- Same gating as ADM01-01. Files: `CollegeResolverLiveRegressionTest.java`, `JsonbSerializationSecurityTest.java`.

#### MVP-ADM02-02 — Governance immutability and idempotent approvals
- **Files**: `CollegeAdminDomainService.java`, `CollegeAdminTenantIsolationRegressionTest.java`.
- **Remaining**: tampered hash-chain detection, plus idempotent approval handling.
- **Approval semantics (owner-approved)**:
  - Same approval submitted twice: return the same successful result; do not repeat the business operation.
  - Conflicting approval for the same workflow: return a conflict response.
  - Duplicate request: no second side effect, audit event, outbox event or downstream action.
  - Define "same" precisely (identity of workflow, approver, decision, and any idempotency key) and document it in the tests. Idempotency is scoped per tenant from the security context only.
- **Acceptance**: tampering throws a security exception; the three approval cases above are covered by tests, including a concurrent double-submit; tenant isolation tests still pass.
- **Staging**: no.

#### MVP-ADM02-03 — Scope gap analysis (PROPOSED, read-only)
- **Why**: the baseline marks ADM-02 as "implemented," but the master prompt names college profile, departments, programs and settings as expected journeys, and the backlog has no tasks for them.
- **Output**: a table of ADM-02 DDD requirements vs endpoints vs tests vs UI, with new backlog tasks for real gaps. No code.

### ACD-01

#### MVP-ACD01-01 — Live RLS verification
- Gating as above. 12 tests, restricted identity, `SET LOCAL ROLE authenticated`. Acceptance unchanged.

#### MVP-ACD01-02 — Course offering status transitions
- **Rename**: original title (code pattern and unique constraints) does not match its remaining work. If code-pattern/unique validation is already covered by existing tests, record that; the remaining work is offering lifecycle (UPCOMING → ACTIVE → COMPLETED).
- **Files**: `CourseDomainService.java`, `CourseDomainServiceTest.java`.
- **Acceptance**: illegal transitions rejected; tests for each pair; tenant isolation tests unchanged.

#### MVP-ACD01-03 — Course UI journey check (PROPOSED)
- **Action**: determine from `App.jsx` whether any course creation, draft, edit or publish screen exists. Record PASS / NOT IMPLEMENTED per journey. Add UI tasks only if user stories require them.

---

## 3. Recommended execution order (Phase 5 cycles)

| Cycle | Task | Why this order |
|---|---|---|
| 0 | Housekeeping: apply this backlog, correct `03-progress.md`, archive old handover phases, propose commit | Records are currently inconsistent |
| 1 | ADM01-04 | Highest-priority local task |
| 2 | ADM01-06 | Rule 4 boundary regression; small |
| 3 | ADM01-05 | Decision gate first; build only if required |
| 4 | ADM02-02 | Next local ADM-02 task |
| 5 | ADM02-03, ADM01-07, ACD01-03 | Read-only scope checks that may add tasks |
| 6 | ACD01-02 | Last known local ACD-01 task |
| 7 | All STAGING-GATED tasks | After Track A COMPLETE |

## 4. Definition of done per cycle

1. Targeted tests, then module suite; Rule 11 boolean check before any `mvn` command.
2. Authorization, tenant-isolation and `deleted_at` coverage in the new tests.
3. Browser test where a UI exists; otherwise status NOT IMPLEMENTED (a page load is SMOKE, never a journey PASS).
4. CodeRabbit run, or the exact command handed to the owner.
5. Progress file updated with ran/skipped counts; no commit without owner approval.
