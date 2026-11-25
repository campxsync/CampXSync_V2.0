# IADM-05 US-021 — Preflight inventory and reconciliation

**Story:** Preflight inventory and reconciliation (IADM-05-US-021)  
**Mode:** Read-only · **Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration`  
**Environment:** DEV Supabase project `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev)

---

## 1. Working tree and branch

| Check | Result |
|---|---|
| Branch | `feature/java-backend-integration` (tracks origin; up to date) |
| HEAD | `858551b` — docs(iadm04): record S5 evidence 4a commit hash 0dbf35d |
| Dirty tracked files | None |
| Untracked of note | `docs/user-stories/IADM-05-user-stories.csv` (backlog input); assorted evidence/classpath noise from prior work |
| Push | Not performed (owner rule: local commits only) |

---

## 2. Input documents (located / missing)

| Input | Location | Status |
|---|---|---|
| IADM-05 Detailed Blueprint V1.2 (Markdown) | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-05_Detailed_Blueprint_V1.2.md` | Found |
| IADM-05 Blueprint V1.2 (PNG) | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-05_Blueprint_V1.2.png` | Found |
| IADM-05-user-stories.csv (32 rows, S0–S4) | `docs/user-stories/IADM-05-user-stories.csv` | Found |
| CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md | `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` (+ Documentation copy) | Found |
| IADM_Architecture_Design.md v2.0 | `D:\CampXSync\Documentation\Platform_InstituteAdmin\IADM_Architecture_Design.md` | Found (version label TBC in body — file present) |
| IADM-01 / IADM-02 / IADM-04 services | `services/administration-services/iadm-0{1,2,4}-*` | Found |
| Existing IADM-05 service / gateway / stack registration | — | **Not found** (expected) |

---

## 3. Conventions from IADM-01 / IADM-02 / IADM-04

| Concern | Convention found | IADM-05 decision (smallest inside boundaries) |
|---|---|---|
| Directory | `services/administration-services/iadm-0N-<kebab>-service` | `iadm-05-data-governance-service` |
| Port | 8099 / 8100 / 8101 / 8102 | **8103** (next free in sequence) |
| Gateway prefix | `/api/v1/iadm/0N` in `GatewayConfig.routeTable` | `/api/v1/iadm/05` → `http://localhost:8103/api/v1/iadm/05` |
| Package | `com.campx.iadm.<domain>` | `com.campx.iadm.governance` |
| Main class | `Iadm0NApplication` | `com.campx.iadm.governance.Iadm05Application` |
| Maven | Parent `campx-sync-parent`; deps `campx-logger`, PostgreSQL, HikariCP, JUnit | Same |
| Permission keys | IADM-01 `adm01.*`; IADM-02 `cfg.*`; IADM-04 **`iadm04.*`** (newest) | **`iadm05.*`** (follow newest IADM-04 precedent; closes gap G-01) |
| Migrations | `database/migrations/YYYYMMDDHHMMSS_iadm0N_*.sql` + paired `*_rollback__*.sql` | Same; target comment DEV only |
| RLS style | `ENABLE` + **`FORCE ROW LEVEL SECURITY`** on every domain table | Same |
| Request context | `GatewayHmacVerifier` + `UserSecurityContext`; `SET LOCAL ROLE authenticated` + `request.jwt.claims` | Same |
| Logging | `CampXLogger` / `CampXLoggerFactory` via `campx-logger` | Same |
| Frontend desk | `/platform/<desk>` + BFF `/api/platform/<desk>` → `fetchFromGateway`; `data-testid="iadm0N-..."` | `/platform/governance`, `/institution/governance`, `/college/governance`; `iadm05-*` |
| Stack scripts | `start_stack.ps1`, `check_stack.ps1`, `restart_service.ps1` | Register IADM-05 :8103 |
| DEMO simulator | IADM-04 `DevPaymentSimulator` labelled DEV; disable flag | DEMO ONLY domain simulator (S2) |

---

## 4. Shared audit capability (actually in use)

| Claim | Finding |
|---|---|
| Second audit store required? | **No** — reusable capability exists |
| Store | Schema `audit`, table **`audit.events`** (partitioned; DEV partitions hold events) |
| Writer API | SECURITY DEFINER function **`audit.log_event(...)`** |
| Java pattern | IADM-04 / IADM-03 **`DurableAuditWriter`** → same-TX `SELECT audit.log_event(...)`; fail-closed with `503 *_AUDIT_UNAVAILABLE` |
| Grant | Already granted to `authenticated` / `service_role` (IADM-01 migration `20261122010300_iadm01_grant_audit_log_event`) |

**US-025 decision:** Reuse `DurableAuditWriter` pattern against `audit.log_event`. No new audit store. Privileged actions refuse when audit cannot be guaranteed.

---

## 5. Identity and token claims

| Item | Finding |
|---|---|
| User auth | Supabase Auth → BFF Bearer / test-session |
| Gateway | Validates session; signs HMAC (`X-Gateway-Timestamp`, `X-Gateway-Signature`); forwards `X-User-Id`, `X-Tenant-Id`, `X-User-Role` |
| Downstream | HMAC verify; build `UserSecurityContext`; never trust client-only tenant without gateway identity |
| DB session | `SET LOCAL ROLE authenticated`; `set_config('request.jwt.claims', '{"sub","tenant_id",...}', true)` |
| Permission check | `iam.has_permission(?)`; platform check `iam.is_platform_admin()` |
| R01 | Via `plat.platform_admins` / platform admin path (not tenant role template alone) |

---

## 6. Database target verification (blocker gate)

| Check | Result |
|---|---|
| Supabase MCP `list_projects` | Only **`azulbaxszsqxymqmlwfe`** (CampXSync-Architecture-Dev, ACTIVE_HEALTHY) visible to this session |
| Production ref `ekspahbsbwdpraaqfqui` | **Not** targeted; forbidden |
| `.env.audit` `CAMPX_SUPABASE_URL` | Contains DEV ref `azulbaxszsqxymqmlwfe` |
| Local `SUPABASE_JDBC_URL` | Pooler host `aws-0-ap-south-1.pooler.supabase.com:6543` — **project ref not embedded in host** |
| Migration apply path for this build | **Supabase MCP `apply_migration` / `execute_sql` with `project_id=azulbaxszsqxymqmlwfe` only** (positively verified DEV) |

**Gate:** PASS for DEV migrations via MCP project id. Local JDBC pooler used only after service start with same DEV credentials already used by IADM-01…04 on this machine; if a connection ever resolves to a non-DEV project, stop.

---

## 7. Legacy tables noted (do not reuse as IADM-05 store)

`cfg.data_retention_policies` and `cfg.data_classifications` exist (empty). Per blueprint, all IADM-05 policy types are **IADM-05 governance records**, not IADM-02 configuration. New schema **`gov`** will own IADM-05 metadata only.

---

## 8. Build defaults recorded (owner kickoff)

| Gap | Assumption for this build |
|---|---|
| G-15 | Approver = Super Admin (R01) only; different account from author. System Admin (R02) may propose/submit, never approve |
| G-16 | Institution Admin (R03) adherence = **view-only** |
| G-02 | College Admin (R04) = view applicable policy + informational domain-workflow entry points; no trigger |
| G-05 | Erasure closure = technical rule only (all required domains final); legal criteria TBC |
| US-015 | Privacy/consent publish exists but **hard-blocked** (legal gate); no way to open in this build |
| G-06 / durations | DEMO ONLY labels; retention duration **TBC** — never invent numbers |
| Domain wiring | Versioned contract endpoints + DEMO ONLY simulator; real domains not wired |

---

## 9. Blockers?

| Potential blocker | Status |
|---|---|
| No reusable shared audit | **Cleared** — `audit.log_event` / `audit.events` in use |
| DB target not DEV | **Cleared** for MCP path (`azulbaxszsqxymqmlwfe`) |
| New infrastructure / tool | None required |
| Boundary contradiction | None found |

**Preflight conclusion:** Proceed to S0 (IADM-05-US-022 … US-025).

---

*Abbreviations: RLS = row-level security (database policies); FORCE RLS = policies apply even to table owner; HMAC = Hash-based Message Authentication Code (gateway→service trust); SoD = segregation of duties; E2E = end-to-end headed browser demo.*
