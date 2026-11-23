# IADM-02 S0 Verification Report (read-only)

**Story:** Produce a read-only verification report of what already exists for platform configuration before anything is changed (`IADM-02-US-001`)  
**Date:** 2026-11-23  
**Environment:** Development Supabase PostgreSQL only (`azulbaxszsqxymqmlwfe` — CampXSync-Architecture-Dev)  
**Branch:** `feature/java-backend-integration` (HEAD at inspection: `f81c1a8`)  
**Database writes in this step:** **None** (SELECT-only inspection via Supabase MCP + repository reads)  
**Status:** Awaiting owner review of this report and `S0_DECISION_PACK.md` before any S0 build (`IADM-02-US-002`…`007`) or S1

---

## 1. Plain-language verdict

Platform configuration code already exists, but it is the wrong shape for IADM-02:

1. **Durable tables exist** in schema `cfg` (configuration), including a four-key catalogue on DEV.
2. **HTTP APIs still live inside the institute-admin monolith** on port **8081** under legacy path `/api/v1/admin/configuration`, and the live path mostly writes **in-memory maps**, with PostgreSQL treated as best-effort (failures are logged and swallowed).
3. **IADM-01** (Institute & Tenant Lifecycle) is the correct pattern to copy for a separate service: port **8099**, gateway prefix `/api/v1/iadm/01`, permission-key checks, same-transaction durable audit via `audit.log_event`.
4. **No IADM-02 service module**, no `/api/v1/iadm/02` gateway route, no platform-configuration desk in the Next.js UI, and no consumer “effective configuration” contract exist today.
5. **IADM-01 does not call any platform-configuration service** for defaults today (hardcoded JVM flag / local `plat.iadm01_feature_flags` / comments pointing at future IADM-04). That is the baseline for story “IADM-01 reads platform defaults” (`IADM-02-US-026`).

**Do not start coding until the owner answers the open decisions in `S0_DECISION_PACK.md` (especially D1, D3, D6, D7, D9, D10).**

---

## 2. Inputs read (order)

| # | Input | Path / location | Note |
|---|---|---|---|
| 1 | IADM-02 user stories (32) | `docs/user-stories/CampXSync_IADM-02_User_Stories.csv` | Slices S0–S4; US-031 PARKED |
| 2 | IADM-02 detailed blueprint | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-02_Detailed_Blueprint_V2.1.md` | Document title says **V1.0**; Markdown wins over PNG |
| 3 | Blueprint PNG (visual index only) | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync IADM-02Blueprint.png` | Not used as implementation fact |
| 4 | Governance | `AGENTS.md`, `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`, `docs/reports/COMPLETION_REPORT.md`, `docs/AGREEMENT_V2.1.md` | Agreement V2.1 binds UI → Gateway → Java → Supabase |
| 5 | IADM architecture v2.0 | `D:\CampXSync\Documentation\Platform_InstituteAdmin\IADM_Architecture_Design.md` | §5.2 IADM-02; inventory fact on `cfg.setting_definitions.allowed_scopes` |
| 6 | RBAC matrix v3.0 | `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` | §8 IADM-02 role posture (ceilings still open as D3) |
| 7 | IADM-01 implementation | `services/administration-services/iadm-01-tenant-lifecycle-service/` | Pattern source (S0–S4 approved) |
| 8 | Legacy ADM-01 configuration stories | `docs/user-stories/ADM-01_institute_admin_user_stories.csv` | Traceability only |

---

## 3. Blueprint item disposition (REUSE / MOVE / NEW / NOT FOUND)

| Blueprint item | Evidence today | Disposition |
|---|---|---|
| Separate IADM-02 Java service with own gateway prefix | No `iadm-02-*` module under `services/`; gateway has `/api/v1/iadm/01` only | **NEW** service shell (copy IADM-01 pattern); proposed prefix `/api/v1/iadm/02` — **no clash** with `/api/v1/admin` or `/api/v1/iadm/01` |
| Port assignment | IADM-01 uses **8099**; Phase 0 note reserved **8100–8106**; blueprint says port assigned at build time (old blanket plan rolled back) | **NEW** — candidate port **8100** (owner confirm in decision pack) |
| Configuration catalogue persistence | `cfg.setting_definitions` in `database/migrations/Scripts_V3.0/20_tables.sql`; DEV has **4** rows | **REUSE** table |
| Typed values / overrides | `cfg.setting_values` (institute/college scoped); DEV row count **0** | **REUSE** table; **owner D2 = no institute overrides** means write path to this table for platform defaults must be redesigned/restricted (proposal only) |
| Feature flags | `cfg.feature_flags`, `cfg.feature_flag_rules`, `cfg.feature_overrides`; DEV flags **0** | **REUSE** tables; move Java ownership into IADM-02 |
| Policy definitions | `cfg.policies`; DEV has **2** rows that look like security-test forged drafts (`POL_SEC_*`), not production policy catalogue | **REUSE** table with caution; clean/replace only under approved DEV seed |
| Data governance cfg tables | `cfg.data_retention_policies`, `cfg.data_classifications` | **REUSE** for storage; ownership may later sit with IADM-05 — do not expand scope without owner say-so |
| Draft / unpublished version store | `cfg.config_drafts`, `cfg.setting_value_versions`, `cfg.setting_delegations` → **not present** on DEV (`to_regclass` false). Rolled-back copies live only under `docs/iadm/rollback/` | **NOT FOUND** in live schema → any draft/version table is **NEW proposal (D6)** |
| In-memory configuration HTTP in monolith | `InstituteAdminController` routes under `/api/v1/admin/configuration*`; `InstituteAdminDomainService` ConcurrentHashMaps; Postgres save failures swallowed | **MOVE** (do not copy) into IADM-02; retire monolith config routes after cutover |
| Postgres repositories for settings/policies/flags | `PostgresGlobalSettingRepository`, `PostgresGlobalPolicyRepository`, `PostgresFeatureFlagRepository` | **MOVE** / adapt under IADM-02 |
| Permission-key checks (not role names) | IADM-01 `UserSecurityContext.checkPermission`; catalogue has `cfg.view`, `cfg.view.own` only for cfg family | **REUSE** check pattern; **NEW keys likely** for draft/approve/publish (D3) |
| Shared durable audit | IADM-01 `appendDurableAudit` → `SELECT audit.log_event(...)` same TX, fail closed; `logger` module `PostgresAuditRepository` / `AuditToOutboxBridge` | **REUSE** IADM-01 same-TX pattern as preferred; decide atomicity in D9 |
| IAM / service identity | `iam.api_clients`, `iam.api_client_grants`; Java repo in institute-admin; **no** IADM-02 consumer grant contract | **REUSE** IAM tables; **NEW** consumer read contract (D7); R27 remains out of scope |
| Consumer effective-config API | Not found | **NEW** |
| Frontend configuration desk / BFF | Not found under `frontend/src` for platform config; nav has Institutes/Onboard/Audit only | **NEW** screens + BFF after S0 |
| Notifications (COM) | Architecture: best effort; Kafka deferred | **REUSE** posture (non-blocking); no Kafka |
| IADM-01 consumption of platform defaults | JVM property `campx.iadm01.r03.secondApproverRequired` and/or `plat.iadm01_feature_flags`; no read of `cfg.setting_definitions` | Recorded for `IADM-02-US-026`; consumer wiring is later (S3) |

---

## 4. Gateway and port map (verified)

| Prefix / port | Target today | Clash with proposed `/api/v1/iadm/02`? |
|---|---|---|
| Gateway `:8080` | Ingress | — |
| `/api/v1/iadm/01` → `:8099` | IADM-01 | **No** (sibling) |
| `/api/v1/admin` → `:8081` | Institute Admin monolith (includes configuration) | **No** (different tree) |
| `/v1/platform-configs` → `:8081` `/api/v1/admin/configuration` | Legacy alias | **No** path clash; **must be redirected or retired** when config moves (cutover plan) |
| `/api/v1/iadm/02` | **Not registered** | **Free** |

Evidence: `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java` (IADM-01 route before `/api/v1/admin`; platform-configs alias at lines ~139–140).

---

## 5. DEV schema & RLS (SELECT evidence)

**Project:** `azulbaxszsqxymqmlwfe` only.

### 5.1 Tables in schema `cfg`

| Table | RLS enabled | FORCE RLS | Policy count | Notes |
|---|---|---|---|---|
| `setting_definitions` | yes | **no** | 2 | Platform catalogue |
| `setting_values` | yes | **no** | 4 | Override store |
| `feature_flags` | yes | **no** | 2 | Empty on DEV |
| `feature_flag_rules` | yes | **no** | 2 | |
| `feature_overrides` | yes | **no** | 4 | |
| `policies` | yes | **no** | 2 | Two `POL_SEC_*` test-like rows |
| `data_retention_policies` | yes | **no** | 2 | |
| `data_classifications` | yes | **no** | 2 | |

**Finding (must not silently “fix” without approval):** project rules require FORCE RLS on domain tables; `cfg.*` today has ENABLE but **not FORCE**. Changing that is a **proposed migration (D6)**, not done in S0.

### 5.2 Policy expressions (summary)

- `setting_definitions` / `policies` **SELECT**: `using (true)` for role `authenticated` → any authenticated session can read the platform catalogue.
- `setting_definitions` / `policies` **ALL**: `iam.is_platform_admin()`.
- `setting_values` **SELECT**: tenant match + `cfg.view` (or college-scoped variant).
- `setting_values` **INSERT/UPDATE/DELETE**: tenant match + **`adm05.write` / `adm05.manage`**.

**Finding:** seed grants `adm05.write` / `cfg.view` to **Institution Admin** and **College Admin** templates. That conflicts with owner default **D2 (no institute overrides)** and story “Institution Admin / College Admin cannot change platform defaults” (`IADM-02-US-029`). RLS realignment is part of D3/D6 proposals — **no change applied**.

### 5.3 Catalogue keys present on DEV (`cfg.setting_definitions`)

| setting_key | data_type | default_value | allowed_scopes | description |
|---|---|---|---|---|
| `attendance.min_pct` | INTEGER | 75 | INSTITUTE, COLLEGE | Minimum attendance % for exam eligibility |
| `exam.grace_marks_max` | INTEGER | 5 | INSTITUTE, COLLEGE | Maximum grace marks per result |
| `fees.late_fee_grace_days` | INTEGER | 7 | INSTITUTE, COLLEGE | Grace days before late fee |
| `security.session_timeout_min` | INTEGER | 30 | INSTITUTE, COLLEGE | Idle timeout |

Seed source: `database/migrations/Scripts_V3.0/80_seed.sql` (~lines 2928–2933).

### 5.4 Missing objects (NOT FOUND on DEV)

- `cfg.setting_value_versions`
- `cfg.setting_delegations`
- `cfg.config_drafts` (or any draft table by that name)

---

## 6. Permission catalogue (verified codes)

From DEV `iam.permissions` (codes that matter for IADM-02 discussion):

| Code | Module | Present? |
|---|---|---|
| `cfg.view` | cfg | Yes |
| `cfg.view.own` | cfg | Yes |
| `cfg.write` / `cfg.draft` / `cfg.approve` / `cfg.publish` / `cfg.manage` | — | **NOT FOUND** |
| `adm05.view` / `adm05.write` / `adm05.manage` / … | adm05 | Yes (used by RLS on `setting_values`) |
| `plat.read` / `plat.write` | plat | Yes |
| `adm01.*` | adm01 | Yes — **IADM-01 lifecycle only; do not reuse for IADM-02 config** |
| `adm02.*` | adm02 | Yes — **college/campus (CADM/ADM-02); do not treat as IADM-02** |

---

## 7. How IADM-01 gets “platform defaults” today (`IADM-02-US-026` baseline)

| Behaviour | Source in code | Uses IADM-02 / `cfg.*`? |
|---|---|---|
| R03 second-approver gate | JVM boolean `campx.iadm01.r03.secondApproverRequired` **or** row in `plat.iadm01_feature_flags` (`R03_SECOND_APPROVER_REQUIRED`) | **No** |
| Plan / entitlement on institute create | Link/reference only; comment that IADM-04 owns entitlements | **No** |
| API client max validity (90 days) | Hardcoded in `PostgresApiClientRepository` (`90L * 24 * …`) inside institute-admin | **No** (hardcoded constant) |
| Seeded `cfg.setting_definitions` | Exist in DB | **IADM-01 does not read them** |

**Conclusion:** there is **no** duplicate platform-default store inside IADM-01 beyond local feature flags / JVM props. Connecting IADM-01 to IADM-02 is new work after D1/D7.

---

## 8. Shared audit contract (verified)

| Mechanism | Where | Durable? | Fit for IADM-02 |
|---|---|---|---|
| Same-TX `audit.log_event(...)` fail-closed | IADM-01 `TenantLifecycleService.appendDurableAudit` | Yes, if TX rolls back on failure | **Preferred REUSE pattern** |
| `logger` → `PostgresAuditRepository` / change_log | `logger` module | Yes when wired | Available alternate |
| `AuditToOutboxBridge` | `logger` module | Outbox pattern exists | Candidate if D9 chooses outbox |
| In-memory `auditTrail` + `recordAudit` in institute-admin domain service | Monolith | **Not durable** | **Do not carry forward** as success path |
| Platform `/platform/audit` UI | Frontend | Still tied to incomplete / in-memory search (known FINDING-IADM01-AUD-01) | Not the mutation path |

---

## 9. IADM-01 patterns to reuse (do not redesign)

Copy these from `iadm-01-tenant-lifecycle-service` when S0 build is authorized:

1. Maven module under `services/administration-services/` + parent `pom.xml` entry.
2. JDK 8 HttpServer entry (`Iadm01Application` / `Iadm01Server`) with `/health`.
3. Gateway longest-prefix registration **before** `/api/v1/admin`.
4. `start_stack.ps1` / `check_stack.ps1` / `restart_service.ps1` blocks.
5. `UserSecurityContext` + `checkPermission(conn, key)` fail-closed.
6. `executeInTransaction` with `SET LOCAL ROLE authenticated` + JWT claims for RLS.
7. `appendDurableAudit` same-TX via `audit.log_event`.
8. Allowed/denied unit tests (`TenantLifecycleRbacTest` style).

**Keep (do not undo):** Institution Admin appointment function fix; request-context leak fixes (K2 / related keeps from rollback).

---

## 10. Frontend / BFF

| Area | Status |
|---|---|
| Platform institutes / onboarding / audit desks | Exist; talk mostly to `/api/v1/iadm/01` |
| Platform configuration catalogue / draft / publish UI | **NOT FOUND** |
| BFF route `/api/platform/configuration` or `/api/iadm/02/**` | **NOT FOUND** |
| Direct DB from frontend for new work | Forbidden by Agreement / AGENTS; no evidence of a config desk bypass (none to bypass) |

---

## 11. Traceability reconciliation

### 11.1 CSV (authoritative backlog) ↔ Blueprint §14 story IDs

Blueprint Part B §14 uses short IDs `IADM02-US-001`…`014`. The CSV uses full IDs `IADM-02-US-001`…`032` and includes TECH stories + parked override.

| CSV story (plain name) | CSV ID | Blueprint §14 ID | Notes |
|---|---|---|---|
| Read-only verification report | IADM-02-US-001 | Part D Phase 0 / §13 | This report |
| Separate service + health | IADM-02-US-002 | C12 / Part A microservices | Build after owner checkpoint |
| Permission map + ceilings | IADM-02-US-003 | §8 / C3 / C13 | Blocked on D3 |
| Durable audit or no success | IADM-02-US-004 | §12 / C3 | Blocked on D9 |
| Reuse storage; isolate drafts | IADM-02-US-005 | §7 / C1 / C9 | Blocked on D6 |
| Idempotent mutations | IADM-02-US-006 | §6 / §9 | After service exists |
| DEV demo seed | IADM-02-US-007 | §15 | Blocked on D1 |
| Catalogue search | IADM-02-US-008 | IADM02-US-001 | Blocked on D1 |
| Key detail | IADM-02-US-009 | IADM02-US-002 | Blocked on D1 |
| Dashboard | IADM-02-US-010 | §5A screen | — |
| Create draft | IADM-02-US-011 | IADM02-US-003 | Blocked on D3 |
| Edit draft | IADM-02-US-012 | §6 PATCH | Blocked on D3 |
| Validate draft | IADM-02-US-013 | IADM02-US-004 | — |
| Policy catalogue / policy draft | IADM-02-US-014 | §5A / §6 | Blocked on D3 |
| R02 limited posture | IADM-02-US-015 | §4 / rule 17 | Blocked on D3 |
| Submit / decide / publish / history / retry | IADM-02-US-016…020 | IADM02-US-005…007, 013 | D4/D5 already defaulted by owner |
| Consumer identity / read / deny / stale / diagnostics | IADM-02-US-021…025 | IADM02-US-009…010; §5B | Blocked on D7 (D8 defaulted fail-closed) |
| IADM-01 reads platform defaults | IADM-02-US-026 | §12 IADM-01 row / C7 | Blocked on D1; baseline §7 above |
| Corrective publish | IADM-02-US-027 | IADM02-US-008 | D11 defaulted (new version) |
| Auditor read-only | IADM-02-US-028 | IADM02-US-011 | Blocked on D3 |
| R03/R04 denial | IADM-02-US-029 | IADM02-US-012 | Blocked on D3; RLS conflict noted |
| COM-down non-blocking | IADM-02-US-030 | IADM02-US-014 | D12 defaulted |
| Institute override | IADM-02-US-031 | **Not in Markdown**; PNG only | **PARKED** (owner D2) |
| Full demo evidence pack | IADM-02-US-032 | §15 / Part D Phase 5 | End of S4 |

### 11.2 Architecture Design v2.0 ↔ IADM-02

| Architecture claim | Verification |
|---|---|
| IADM-02 owns platform settings/policies, versioning, effective resolution, CADM delegation metadata | Design intent; **not implemented** as separate service |
| `cfg.setting_definitions.allowed_scopes` exists | **Confirmed** on DEV |
| “IADM wins” / no silent CADM override | **Not enforced** in Java today (architecture cites SEC-07); owner D2 = no overrides at all for now |
| Notification exclusion from IADM-02 | Aligns with CSV / blueprint |
| Kafka deferred | Aligns; do not add |

### 11.3 Legacy ADM-01 Configuration & Policy epic ↔ IADM-02

| Legacy ADM-01 story theme | Legacy API / store name | IADM-02 disposition |
|---|---|---|
| Define/update global setting | `ADM01_global_settings`; `/api/v1/admin/configuration` | **MOVE** into IADM-02; durable `cfg.setting_*` |
| View effective global configuration | GET configuration | Becomes catalogue + consumer effective read |
| Feature flags + overrides | `ADM01_feature_flags` | **MOVE**; overrides constrained by D2 |
| Publish immutable governance policy | `ADM01_global_policies` | **MOVE** to policy draft/publish path |
| Snapshot / rollback versions | `ADM01_configuration_versions` (in-memory today) | **NEW** durable version model (D6) |

Historical docs that claim IADM-02 “completed” (older wave notes) are **superseded by the 2026-11-22 IADM full rollback**. Live truth is this inventory.

### 11.4 PNG vs Markdown (binding rule)

| PNG tendency | Markdown / owner rule | Action |
|---|---|---|
| Institute overrides | D2 / Markdown: **no overrides by default**; US-031 PARKED | **Do not implement** |
| Concrete permission keys / table names / `/iadm/v1/...` paths | Markdown: TBC; proposed gateway `/api/v1/iadm/02` | Prefer Markdown + verified code; propose gaps for approval |

---

## 12. Story readiness after S0 verification only

| Story | API-ready | Screen-ready | Demo-verified | Evidence |
|---|---|---|---|---|
| IADM-02-US-001 Read-only verification | n/a (report) | n/a | **Pending owner acceptance** | This file + `S0_DECISION_PACK.md` |
| IADM-02-US-002…007 | no | no | no | Blocked until owner decisions + build authorization |
| IADM-02-US-008…032 | no | no | no | Not started; US-031 PARKED |

---

## 13. Risks / blockers (plain language)

1. **Open decisions D1, D3, D6, D7, D9, D10** block catalogue seed, permission grants, draft/version DDL, consumer identity, audit atomicity, and payload versioning.
2. **Existing `adm05.*` RLS on `setting_values`** lets Institution Admin / College Admin write override rows today — conflicts with D2 and US-029 until redesigned (proposal only).
3. **FORCE RLS missing** on `cfg.*` vs project standard.
4. **Monolith config path reports success even when PostgreSQL write fails** — must not be preserved as the success contract.
5. **DEV `cfg.policies` content** looks like security-test residue — seed plan must not treat it as real catalogue without owner review.
6. **Two Super Admin (R01) demo users** required by D5 — confirm accounts in `.env.audit` / seed plan (names only in decision pack; no passwords in docs).
7. **Legacy `/v1/platform-configs` alias** will keep hitting the monolith until explicitly redirected after MOVE.

---

## 14. What happens next (stop gate)

1. Owner reviews this report.
2. Owner answers `S0_DECISION_PACK.md` (accept / amend recommendations).
3. Only then: build remaining S0 stories (`IADM-02-US-002`…`007`) including approved DEV seed — still **no push**, DEV only.
4. Do **not** start S1 until that S0 build is done and the S1 demo checkpoint is scheduled.

**RBAC v3.0 Compliant badge:** **not claimed**. Design matrix exists; code/gateway/catalogue/RLS/tests/demo are not yet certified for IADM-02.
