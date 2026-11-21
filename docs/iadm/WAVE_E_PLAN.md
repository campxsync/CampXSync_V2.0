# IADM Wave E — Parity & Cutover Plan

**Authorized priority:** Owner (Niranjan V.G.) 2026-11-21 — IADM Wave E before CADM.  
**Branch:** `feature/java-backend-integration` · **DEV only** (`azulbaxszsqxymqmlwfe`) · No production.  
**Goal:** Finish PARTIAL acceptance on ports 8099–8106, prove CADM contracts, close open IADM findings, cut traffic off overlapping monolith `:8081` routes when parity is green.

## Plain-language map

| Code | Meaning |
|---|---|
| Wave E | IADM completion pass after Waves A–D scaffolds |
| Monolith | `institute-admin-service` on port **8081** (`/api/v1/admin`) |
| Split services | IADM-01…08 on **8099–8106** (`/api/v1/iadm/01`…`08`) |
| CADM | College Administration Module (consumes IADM contracts; starts after Wave E exit) |

## Out of scope (still deferred)

| ID | Plain language | Why |
|---|---|---|
| IADM-03-US-008 | Break-glass tenant recovery | Matrix §22.5; **policy frozen** 2026-11-21 (OWNER-DEC-BG-01) — not authorized to build yet |
| IADM-03-US-009 | Two-person appoint Institution Admin | **implemented** 2026-11-21 (OWNER-DEC-TP-01; migration `20261121001200`) — await story approval |
| IADM-05-US-007 | Guardian-consent under-18 | D-RBAC-M17 legal |
| IADM-D5 / adm03 / R27 | API client issuing | Dormant by matrix |
| Kafka | Event bus | Deferred platform-wide |

## Exit criteria (DoD)

1. Each story below meets its acceptance clauses **or** is explicitly PARTIAL with a tracked gap in `WAVE_E_REPORT.md`.
2. Five CADM contracts have live DEV evidence (allowed + denied) in `docs/iadm/WAVE_E_CONTRACTS.md`.
3. Open security findings from Wave E scope either FIXED or re-logged with severity (Curriculum leak at minimum addressed or STOP with blocker).
4. `start_stack.ps1` / `check_stack.ps1` know ports 8099–8106; gateway routes remain registered.
5. Overlapping monolith handlers for moved routes are disabled or documented as shim-only with switch date.
6. Full Maven suites for touched IADM modules green; local commit; **STOP for approval** before CADM.

---

## E0 — Contract proof (do first)

| Contract | Story | Endpoint / artifact | Evidence required |
|---|---|---|---|
| First College Admin + handover | IADM-01-US-003, US-004 | `/api/v1/iadm/01/.../colleges/onboard`, `.../handover` | Live DEV + Auth-linked admin gap closed or STOP (SEC-11) |
| Effective config | IADM-02-US-005 | `/api/v1/iadm/02/.../settings/{key}/effective` | Precedence platform > institute > college; lock wins |
| Role ceiling | IADM-03-US-002 | `/api/v1/iadm/03/ceilings` | Set + get; CADM cannot exceed |
| Shared audit read | IADM-08-US-001 | `/api/v1/iadm/08/audit/search` | R03 allowed; denied without `adm08`/`view` key |
| Entitlement check | IADM-04-US-006 | `/api/v1/iadm/04/entitlements/check` + `plat.check_entitlement` | Fail closed on unknown key |

Deliverable: `docs/iadm/WAVE_E_CONTRACTS.md`.

---

## E1 — Story parity (move, do not copy)

Work **one service cluster at a time**. Keep `/api/v1/admin` working until the specific route is switched at gateway.

### E1-A · IADM-01 Institute & Tenant Lifecycle (:8099)

| Story | Action |
|---|---|
| US-001 | Complete register institute parity vs monolith |
| US-003 | College onboard + first College Admin **Auth-linked** (SEC-11) |
| US-004 | Handover record; R03 loses direct college user admin after handover |
| US-005 | Get institute profile own-tenant only |
| US-006 | Suspend/reactivate blocks sessions while suspended |
| US-007 | Lifecycle request + R01 decide |
| US-008 | Auditor read-only history |

### E1-B · IADM-02 Platform Configuration (:8100)

| Story | Action |
|---|---|
| US-001…003 | Catalogue / platform lock / institute allow-list complete |
| US-004 | Delegate college settings |
| US-005…006 | Effective resolve + R04 read flags |
| US-007…008 | History/rollback + auditor read |

### E1-C · IADM-03 Identity (:8101) + IADM-08 Audit/Ops (:8106)

| Story | Action |
|---|---|
| US-001,003,005,006 | Catalogue, assign/remove, delegations, access reviews **moved** |
| US-004,007 | Access-request decide; auditor views |
| IADM-08 US-001…006 | Search/export/retention/health/self-check complete on :8106 |

### E1-D · IADM-04 Billing (:8102) + IADM-06 Analytics (:8104)

| Story | Action |
|---|---|
| US-001,002,005 | Move plan/subscription/invoice from monolith |
| US-003,004,007 | R03 view/request; auditor read |
| US-006 | Entitlement check already core — expand denied tests |
| IADM-06 US-001…005 | Aggregates, export, metering feed |

### E1-E · IADM-05 Privacy (:8103) + IADM-07 Security (:8105)

| Story | Action |
|---|---|
| US-001…006,008 | Retention, notices, DSR, execute, legal hold, auditor |
| US-001…006 | Policies, posture, findings, platform mins, R04/R28 views |

---

## E2 — Security findings

| Finding | Severity | Wave E action |
|---|---|---|
| FINDING-IADM-SEC-02 CurriculumController identity on HttpContext | HIGH | Fix (clear attributes in finally) or equivalent fail-closed |
| FINDING-IADM-SEC-02 Institute/College/Course body attrs | MEDIUM | Same finally-remove pattern |
| FINDING-IADM-ARCH-01 / SEC-11 Auth-linked first College Admin | HIGH product | Implement in E1-A US-003 |
| FINDING-IADM-RBAC-01 PLATFORM_ADMIN template codes | MEDIUM | No invent; keep `is_platform_admin()`; doc only unless owner decides |

---

## E3 — Ops cutover

1. Add IADM-01…08 to `scripts/dev/start_stack.ps1`, `check_stack.ps1`, `stop_stack.ps1`, `restart_service.ps1`.
2. Confirm gateway `/api/v1/iadm/0X` before `/api/v1/admin`.
3. After parity: point moved routes only at split services; leave thin shim or 410 on monolith for those paths.
4. Update `docs/iadm/FILE_LIST.md` + `WAVE_E_REPORT.md`.

---

## Implementation rules (unchanged)

- UI → Gateway (:8080) → Java → Supabase PostgreSQL; permission keys not role names; audit on mutations; fail closed; new migrations + rollbacks only.
- `campx-logger` + Agreement §3.2 (`docs/iadm/ENGINEERING_STANDARDS.md`).
- Frontend out of scope unless a story explicitly needs a screen.
- Local commits per cluster (E0, E1-A, …); push only on owner request.
- Circuit breaker: 3 failed attempts on same error → STOP.

## Sequence

```
E0 contracts → E1-A → E1-B → E1-C → E1-D → E1-E → E2 findings → E3 cutover → WAVE_E_REPORT → STOP (then CADM)
```
