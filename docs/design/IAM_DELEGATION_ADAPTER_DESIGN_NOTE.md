# IAM Delegation Adapter — Design Note (D12a)

**Status:** **BLOCKED at Path C infrastructure boundary** (architecture M1 + Path C **approved**; live adapter **not shippable** until the missing IAM check API is separately authorized and built).  
**Date:** 2026-11-15 (updated same day after M1 + Path C owner approval + repo investigation)  
**Owner decision:** D12a (`docs/project-control/PROGRESS.md`); M1 + Path C approved 15-11-2026 (Niranjan)  
**Related:** D10, D12b–d; Phase A ports already shipped (`FakeDelegationPort` / `FailClosedDelegationPort` in STM-02..06)

---

## 0. Approved decisions (15-11-2026, Niranjan) — recorded verbatim intent

| Topic | Decision |
|---|---|
| Mapping | **M1** — active delegation of role `REGISTRAR` / `R31` authorizes the delegate for applicable STM `*.A` keys in the same tenant |
| Integration | **Path C** — central IAM authorization/delegation contract; **no** STM cross-schema reads of `iam.delegations` |
| Caching | **None** initially — every A/R obtains an authoritative IAM decision |
| Revocation | Authoritative IAM validation on every approve/reject |
| Failure | **Fail closed** |

### M1 restrictions (mandatory)

- Applies only to STM approval actions permitted by the approved RBAC action-level matrix (`*.A` keys already used by STM services).
- Does **not** grant general Registrar catalogue rights beyond those A/R checks.
- Does **not** grant ACD / LMS / EXM / HRM / FIN permissions.
- Does **not** grant delete.
- Java services independently enforce tenant + assigned-campus scope.
- Delegation must be valid and active; expired / revoked / invalid → deny.
- Legacy Registrar stand-in may affect **role recognition only**; never bypasses delegation, campus, tenant, RBAC, or SoD.

### Path C restrictions (mandatory)

- STM services validate via a **central IAM contract**.
- Do **not** introduce direct cross-schema JDBC from STM into `iam.delegations` (Path B remains unauthorized).
- Do **not** invent or claim an endpoint without verifying it in the repository.

---

## 1. Purpose

Wire STM approve/reject (A/R) checks to **active** IAM delegations through the existing `DelegationPort`, replacing the fail-closed live default with a Path C client — **only** when the IAM check contract exists.

Phase A already:

- Defines `DelegationPort.hasActiveDelegation(tenantId, userId, permissionKey)`.
- Uses `FakeDelegationPort` in unit tests.
- Uses `FailClosedDelegationPort` in production constructors (always false → A/R denied).

---

## 2. Existing IAM model (facts)

### 2.1 Table `iam.delegations` (from `docs/engineering/DB_SCHEMA_SNAPSHOT.md`)

| Column | Role |
|---|---|
| `tenant_id`, `from_user_id`, `to_user_id`, `role_id` | Who delegated which catalogue **role** to whom |
| `valid_from`, `valid_to` | Time window |
| `status` | `ACTIVE` \| `REVOKED` \| `EXPIRED` |
| `deleted_at` | Soft delete |

Delegations are **role-scoped** (FK to `iam.roles`), not STM permission-key rows. There is **no** column for `STM-06.DOCUMENTS.A` today.

### 2.2 Existing code that already reads the table

- `institute-admin-service` → `PostgresDelegationRepository` (ADM01): insert/list/update/revoke against `iam.delegations` inside that service’s DB session.
- No gateway HTTP contract was found for STM services to query “does user U have an active delegation covering permission P?”.

### 2.3 STM permission keys used today (port contract)

| Service | Key (examples) |
|---|---|
| document-service | `STM-06.DOCUMENTS.A` |
| enrollment-service | `STM-03.ENROLMENT.A` |
| lifecycle-service | `STM-04.LIFECYCLE.A` |
| guardian-service | `STM-05.GUARDIAN.A` |
| admissions-service | `STM-02.ADMISSIONS.A` |

Phase A maps “has active delegation for this key” only inside fakes. Live mapping from `role_id` → STM A/R key must be specified in an approved adapter design (section 5).

---

## 3. Classification (D12a STOP dimensions)

| Path | What it means for STM | D12a classification |
|---|---|---|
| **A. Existing IAM capabilities only** | Keep `Fake` + `FailClosed`; seed `iam.delegations` / role assignments via prepare-only runbooks; gateway continues to forward role + `X-College-Id`. No STM JDBC to `iam.*`. | **Allowed now** (already done) |
| **B. Cross-schema read** | An STM service opens its Hikari pool and `SELECT`s from `iam.delegations` (and likely `iam.roles`) under the same JWT/`SET LOCAL` context | **STOP** — needs owner approval of this note **and** an explicit “approve cross-schema read” decision |
| **C. New service contract** | IAM / institute-admin exposes e.g. `GET /internal/delegations/active?userId=&permissionKey=` (HMAC, loopback); STM `DelegationPort` HTTP client calls it | **STOP** — new inter-service contract; needs owner approval |
| **D. New infrastructure** | Shared library module, Redis cache cluster, message bus for revocation, new DB view/materialized permission map, Docker/VM | **STOP** — out of scope unless separately approved |

**Rule (updated after M1 + Path C approval):** Architecture Path **C** + mapping **M1** are approved. **Do not** implement Path B or invent Path D. The Path C **HTTP check contract itself does not exist yet** → leave production on `FailClosedDelegationPort` until the owner separately authorizes §14 Phase 2 (API + gateway + STM clients). No alternative authorization mechanism (hard-coded allow lists, skipping delegation when R31 is present, treating legacy stand-in as delegation, etc.).

---

## 4. Chosen direction (approved)

**Path C — central IAM authorization/delegation contract** on the service that already owns `iam.delegations` (institute-admin), consumed by STM `DelegationPort` HTTP clients. **M1** mapping. **No cache.** **Fail closed.**

Rationale (unchanged):

- Preserves schema ownership (`iam` stays with IAM/admin).
- Avoids five STM services each gaining `iam` SELECT grants and cross-schema coupling.
- Fits existing Gateway → HMAC → loopback pattern.

**Implementation status:** architecture approved; **API missing** → see §14 STOP. **Path B is not authorized** and must not be substituted.

---

## 5. Required claims and evaluation rules

### 5.1 Caller / actor claims (already on STM Actor / gateway)

| Claim / header | Use |
|---|---|
| Tenant (`X-Tenant-Id` / JWT `tenant_id`) | Scope all lookups; never trust client-only tenant without gateway verification |
| User (`X-User-Id` / JWT `sub`) | Match `iam.delegations.to_user_id` |
| Role (`X-User-Role`) | Must be `R31` / `REGISTRAR` for Registrar A/R paths (legacy admin only if flag ON — §7) |
| Campus (`X-College-Id`) | Required for Registrar actions (D12b); must match record campus |

### 5.2 Active delegation predicate (proposed)

A delegation counts as active for STM A/R when **all** hold:

1. `deleted_at IS NULL`
2. `status = 'ACTIVE'`
3. `valid_from <= now() < valid_to` (or `<= valid_to` if owner prefers inclusive end — decide at approval)
4. `to_user_id = actor.userId`
5. `tenant_id = actor.tenantId`
6. Delegated `role_id` maps to an allowed STM A/R permission key for that service (mapping table approved with the adapter — **open decision**: map via role code `REGISTRAR`/`R31` only vs finer permission grants)

**Mapping decision:** **M1 approved.** M2 and M3 remain unauthorized alternatives (do not substitute).

M1 evaluation for STM `*.A` keys: IAM returns allow only when an active `REGISTRAR`/`R31` role delegation exists for `(tenant, to_user)`. Permission keys accepted by STM today remain:
`STM-02.ADMISSIONS.A`, `STM-03.ENROLMENT.A`, `STM-04.LIFECYCLE.A`, `STM-05.GUARDIAN.A`, `STM-06.DOCUMENTS.A`. Any other key → deny.

---

## 6. Caching and revocation

| Topic | Decision |
|---|---|
| Cache | **None** (approved) |
| Revocation | Authoritative IAM check on every A/R; `REVOKED` / soft-delete / outside `valid_from`–`valid_to` → deny |
| Logging | Correlation id, tenant, user, permissionKey, allow/deny — **never** secrets, tokens, JWT, or document bytes |

---

## 7. `CAMPX_STM_LEGACY_REGISTRAR_STANDIN` vs delegation

When the flag is **ON** (staging only; default **OFF** in code; expiry earlier of first real-college onboarding or **2026-12-31** per runbook):

| Allowed | Forbidden |
|---|---|
| Treat College Admin / Institution Admin as satisfying **Registrar role recognition** (`isRegistrar()` / admissions equivalent) | Bypass **active-delegation** checks for A/R |
| | Bypass **campus** / **tenant** scope |
| | Bypass **same-actor verify→approve** (SoD) |
| | Grant any right outside the STM action-level matrix |
| | Grant ACD/LMS/EXM/HRM/FIN or Academic Admin rights |

**Invariant:** `LegacyRegistrarStandin.isEnabled()` may only widen **who counts as Registrar for role checks**. Every A/R path must still call `DelegationPort.hasActiveDelegation(...)` and fail closed when false. Campus and SoD checks remain mandatory.

---

## 8. Delegation port interface (current — keep stable)

```java
public interface DelegationPort {
    boolean hasActiveDelegation(UUID tenantId, UUID userId, String permissionKey);
}
```

| Implementation | Role |
|---|---|
| `FakeDelegationPort` | Unit/integration tests |
| `FailClosedDelegationPort` | **Live default until adapter approved** |
| `LiveDelegationPort` (name TBD) | **BLOCKED** — Path B or C after approval |

No second authorization path that skips the port.

---

## 9. Same-actor verify → approve denial (D10) — plan for five STM modules

| Module | Current | Plan (implement under queue item **R31-SoD**, separate from this adapter) |
|---|---|---|
| **document-service** | **Done** — deny APPROVE when same `userId` already has verification and document not resubmitted (`SAME_ACTOR_VERIFY_APPROVE`) | Keep; regression tests remain |
| **enrollment-service** | Not enforced as verify→approve pair | Enforce on status A/R: approver identity ≠ recorded verifier/submitter-of-exception (or equivalent prior decision actor on the same request) |
| **lifecycle-service** | Not enforced on `decideRequest` | Enforce: approver ≠ prior faculty endorser / verifier identity stored on the request when both steps exist in one workflow |
| **guardian-service** | Partial (`REVIEWER_IS_PARTY` for guardian person) | Extend to Registrar review→approve: same Registrar user cannot complete both steps on one evidence/review workflow |
| **admissions-service** | Reviewer≠approver SoD exists (`validateSegregationOfDutiesOnDecision`) | Align naming/tests with D10; ensure Registrar path uses same SoD; do not weaken |

Adapter work **does not** implement SoD; SoD is service-layer identity comparison on persisted actors (queue **R31-SoD**).

---

## 10. Tests (when adapter is unblocked)

Fakes remain authoritative for CI without DB/IAM:

- Permitted A/R with active fake delegation + campus.
- Denied: no delegation; revoked/expired (fake); wrong campus; cross-tenant; same-actor SoD; delete; non-STM action; College/Institution Admin as Registrar when flag OFF; allowed role recognition when flag ON **still denied without delegation**; Academic Admin unaffected; unauthenticated / wrong role.

Live adapter tests (only after approval + staging seed): contract tests against fake HTTP IAM or approved staging read — **no production writes** from this note.

---

## 11. Staging steps — prepare-only runbook (do not apply)

Mirror / extend `docs/runbooks/R31_REGISTRAR_STAGING_SETUP.md` (prepare only):

1. Confirm R31 role template exists in catalogue (seed prepared, **not** applied under this note).
2. Prepare active `iam.delegations` row(s) for pilot Registrar `to_user_id` with valid window and `status=ACTIVE`.
3. Confirm gateway claim forwarding: role + `X-College-Id`.
4. Confirm STM services still use `FailClosedDelegationPort` until adapter approval → A/R remains denied in live even if DB rows exist (**expected** until Path B/C ships).
5. After owner approves this note **and** Path B or C: deploy adapter; re-run full module suites; staging smoke A/R with delegation present/absent.
6. Legacy flag: OFF by default; if ON on staging, verify it does **not** bypass delegation (negative test).
7. Rollback: unset adapter wiring → fail-closed; revoke delegation rows; disable legacy flag by expiry rule.

**This section does not authorize** IAM seed apply, cross-schema grants, or adapter deployment.

---

## 12. Owner decisions — status

| Decision | Status |
|---|---|
| Design note / Path C / M1 / no cache / fail-closed | **Approved** 15-11-2026 |
| R31 IAM seed apply (`R31_REGISTRAR_STAGING_SETUP.md`) | Still separate (not authorized here) |
| **New IAM check HTTP contract + gateway route + STM client wiring** | **STOP — separate approval required** (see §14) |

Keep `FailClosedDelegationPort` in live constructors until §14 is unblocked. Do not substitute Path B / M2 / M3. Do not invent a bypass.

---

## 13. Explicit non-goals

- Frontend / BFF role mapping (queue **1c**).  
- Supabase staging/production writes from this note.  
- New tools, dependencies, Docker/VM (beyond an authorized Path C API on existing institute-admin).  
- Weakening SoD, campus, tenant, or matrix permissions via the legacy flag.  
- Path B cross-schema reads from STM services.  
- M2 DDL permission scopes; M3 new gateway token claims.

---

## 14. Investigation result (15-11-2026) — STOP at Path C API boundary

### 14.1 What already exists

| Asset | Location | Notes |
|---|---|---|
| STM `DelegationPort` + Fake + FailClosed | document / enrollment / lifecycle / guardian / admissions `…/security/` | Live constructors use **FailClosed** |
| A/R call sites | `requireDelegationForApproveReject` / admissions `requireDecisionApprovalPermission` | Campus + SoD already separate |
| `iam.delegations` table | Supabase schema (snapshot) | Role-scoped rows; status ACTIVE/REVOKED/EXPIRED |
| `DelegationRepository` + Postgres/InMemory | `institute-admin-service` | CRUD/list/revoke against `iam.delegations` |
| Institute-admin gateway routes | `api-gateway` → `:8081` `/api/v1/admin/**` | **No** `/delegations` route |

### 14.2 What is missing (verified — not invented)

1. **No HTTP controller** in `institute-admin-service` exposes delegations (grep of controllers: zero `delegation` routes).  
2. **No domain service** wires `DelegationRepository` into an authorization “check” operation.  
3. **No API Gateway** route for a delegation-check endpoint.  
4. **No internal HMAC service-to-service check contract** of the form “does user U in tenant T have an active REGISTRAR/R31 delegation covering STM `*.A`?”.  
5. Repository `listDelegationsForUser` returns from/to rows but does **not** join `iam.roles` to filter `REGISTRAR`/`R31` or evaluate validity for an STM permission key.

Therefore Path C **cannot** be completed using only existing contracts. Building the check API would be a **new IAM API / service contract** on institute-admin (plus gateway registration and five STM HTTP clients).

### 14.3 Why existing infrastructure cannot satisfy the requirement

- STM services must not read `iam.delegations` (Path C / anti–Path B).  
- The only IAM-side persistence code is a **repository**, not a callable authorization API.  
- Calling a non-existent URL from an `HttpDelegationPort` would invent a contract and either fail closed forever or break against an undefined shape.  
- Gateway has no route to forward such a check.  
- No shared library already implements this client.

### 14.4 Minimum additional work (separate owner approval)

1. **Authorize** a new internal IAM check contract on existing `institute-admin-service` (not a new deployable), e.g.  
   `GET` or `POST` `/api/v1/admin/internal/delegations/active-check`  
   Inputs: tenantId, userId, permissionKey (STM `*.A`).  
   Logic: M1 — active `REGISTRAR`/`R31` delegation for `to_user_id` within validity; deny otherwise; fail closed on errors.  
2. Wire domain service over existing `DelegationRepository` + role code lookup (`iam.roles`).  
3. Register gateway loopback route (existing gateway pattern).  
4. Implement `HttpIamDelegationPort` (JDK `HttpURLConnection`, no new dependency) in each STM module (or shared module **only if** already used — prefer per-service copy to avoid new shared artifact unless approved).  
5. Wire live constructors to Http port when IAM base URL configured; otherwise remain FailClosed.  
6. Tests: Fake remains for unit; contract tests against Fake IAM HTTP; full suites; optional staging smoke after seed approval.

**Not included** unless separately approved: DDL, R31 seed apply, Path B, cache, new containers/deps, production.

### 14.5 Separate approval needed from owner

> **Approve Path C Phase 2:** implement the internal active-delegation check API on `institute-admin-service`, register it on the API Gateway, and wire STM `HttpIamDelegationPort` clients (M1, no cache, fail closed). Confirm whether role lookup may use existing `iam.roles` reads already available inside institute-admin.

Until that approval: live adapter remains **BLOCKED**; production stays on `FailClosedDelegationPort`.
