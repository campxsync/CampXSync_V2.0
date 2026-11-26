# IADM-08-US-001 — Preflight inventory and reconciliation

**Story:** Preflight inventory and reconciliation (IADM-08-US-001)  
**Mode:** Read-only · **Date:** 2026-11-26  
**Branch:** `feature/java-backend-integration`  
**Environment:** DEV Supabase project `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev)

---

## 1. Working tree and branch

| Check | Result |
|---|---|
| Branch | `feature/java-backend-integration` (tracks origin; up to date at session start) |
| HEAD at preflight | `ef12036` — docs(iadm07): record APPROVED status, CSV Implementation status, and push close-out |
| Dirty tracked files | None |
| Untracked of note | Prior evidence/classpath noise only |
| Push | Not performed (owner rule: local commits only) |

---

## 2. Input documents (located / missing)

| Input | Location | Status |
|---|---|---|
| IADM-08 Detailed Blueprint V1.2 (Markdown) | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-08_Detailed_Blueprint_V1_2_FINAL.md` | **Found** |
| IADM-08 Blueprint V1.2 (PNG) | Same folder `CampXSync_IADM-08_Blueprint_V1_2_FINAL.png` | **Found** |
| IADM-08 Blueprint V1.1 (Markdown + PNG) | Same folder | **Found** (CSV precedence over V1.1 wording) |
| IADM-08-user-stories.csv (24 rows, S0–S4) | `docs/user-stories/IADM-08-user-stories.csv` | **NOT FOUND** — TBC; working backlog reconstructed from owner kickoff work order + Blueprint V1.2 panel 14 + clarifications (see §12) |
| CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md | `D:\CampXSync\Documentation\OverAllArchitecture\` | **Found** — §8 IADM-08: R01 **M**, R02 **M**, R03 **V** own institute, R04 **—**, R28 **V/AU/E (read-only)** |
| IADM_Architecture_Design.md v2.0 | `D:\CampXSync\Documentation\Platform_InstituteAdmin\IADM_Architecture_Design.md` | **Found** — §5.8 Audit, Health & Operations Oversight |
| IADM-01 / 02 / 04 / 05 / 06 / 07 services | `services/administration-services/iadm-0{1,2,4,5,6,7}-*` | **Found** |
| Existing IADM-08 service / gateway / stack registration | — | **Not found** (expected) |

**Conflict note (precedence):** Kickoff states CSV wins over Blueprint V1.1. Because the CSV file is absent, build uses **owner kickoff + Blueprint V1.2**. Owner may replace `docs/user-stories/IADM-08-user-stories.csv` later; reconstructed rows are labelled accordingly.

---

## 3. Conventions from IADM-01…07 → IADM-08 decision

| Concern | Convention found | IADM-08 decision |
|---|---|---|
| Directory | `services/administration-services/iadm-0N-<kebab>-service` | `iadm-08-audit-health-ops-service` |
| Port | 8099…**8105** occupied | **8106** (next free; matches blueprint proposal) |
| Gateway prefix | `/api/v1/iadm/0N` in `api-gateway/.../GatewayConfig.routeTable` (before legacy `/api/v1/admin`) | `/api/v1/iadm/08` → `http://localhost:8106/api/v1/iadm/08` |
| Package | `com.campx.iadm.<domain>` | `com.campx.iadm.ops` |
| Main class | `Iadm0NApplication` | `Iadm08Application` |
| Maven | Parent `campx-sync-parent`; deps `campx-logger`, PostgreSQL, HikariCP, JUnit | Same |
| Permission keys | Newest pattern **`iadm0N.*`** | **`iadm08.*`** (matches blueprint family) |
| Schema naming | Short domain labels (`gov`, `anl`, `sec`) | **No IADM-08 schema or tables in V1** (boundary 1 / D14) |
| Migrations | Numbered + paired rollback; DEV target comment | **One additive permission-key migration only** |
| RLS style | ENABLE + FORCE on owned tables | N/A (no owned tables); shared `audit.events` RLS reused |
| Request context | `GatewayHmacVerifier` + `UserSecurityContext`; `SET LOCAL ROLE authenticated` + `request.jwt.claims` | Same |
| Logging | `CampXLogger` / `CampXLoggerFactory` | Same |
| Read recording | IADM-06 pattern: every privileged read → `audit.log_event` fail-closed | Same for all six IADM-08 reads (D3) |
| IADM-05 gate | IADM-06 `Iadm05PolicyClient` → `/domain/resolve-policy` | Same; DEMO ONLY domain **OPERATIONS** if none published |
| Frontend desk | `/platform/<desk>` + BFF `/api/platform/<desk>` → `fetchFromGateway`; `data-testid="iadm0N-..."` | `/platform/operations`, `/institution/operations`, `/platform/operations/audit`; `iadm08-*` |
| Stack scripts | `start_stack.ps1`, `check_stack.ps1`, `restart_service.ps1` | Register IADM-08 **:8106** |

**Route collision check:** `/api/v1/iadm/08` is **absent** from `GatewayConfig.routeTable`. Legacy `/api/v1/admin` is a separate namespace. **No collision.**

---

## 4. Shared audit (D2 / blocker gate)

| Claim | Finding |
|---|---|
| Second audit store required? | **No** — reusable capability exists |
| Store | Schema `audit`, table `audit.events` |
| Columns (verified) | `id`, `tenant_id`, `college_id`, `event_time`, `action`, `principal_id`, `principal_type`, `resource_type`, `resource_id`, `status`, `severity`, `ip`, `user_agent`, `correlation_id`, `detail` (jsonb), `created_at`, `created_by` |
| Writer API | `audit.log_event(...)` (SECURITY DEFINER) — **EXISTS** |
| Fail-closed pattern | IADM-04/05/06/07 `DurableAuditWriter` → same-TX `SELECT audit.log_event(...)`; `503 *_AUDIT_UNAVAILABLE` |
| Live volume | **521** events; **12** distinct `tenant_id` values with events |
| Read pattern (IADM-01 / IADM-07) | `SELECT … FROM audit.events WHERE … action LIKE 'IADM0N_%'` (+ institute filter where required) |
| Safe field projection (D4) | Map to: event id, time, actor (`principal_id`), action, target (`resource_type`/`resource_id`), outcome (`status`), correlation id, institute (`tenant_id`). Never return `detail` / raw payloads |

**Decision:** Reuse `DurableAuditWriter` + `audit.events` SELECT. No second audit store. **Blocker cleared.**

### 4a. Denial-type events (clarification 8)

| Path | Finding | Consequence |
|---|---|---|
| Denial events in shared audit | **EXIST** — e.g. `IADM02_CONSUMER_READ_DENIED` (3), `IADM02_CONFIG_DRAFT_DECIDE_DENIED` (1) | Audit search may surface them when they match filters; IADM-08 never creates denial events |
| Dedicated denial contract | **Not found** | No separate denial API; filter shared audit by action patterns containing DENY/DENIED |

---

## 5. Shared identity claims

| Item | Finding |
|---|---|
| User auth | Supabase Auth → BFF Bearer / test-session |
| Gateway | HMAC (`X-Gateway-Timestamp`, `X-Gateway-Signature`); forwards `X-User-Id`, `X-Tenant-Id`, `X-User-Role` |
| Downstream | HMAC verify; `UserSecurityContext`; never trust client-only tenant |
| DB session | `SET LOCAL ROLE authenticated`; `request.jwt.claims` |
| Permission check | `iam.has_permission(?)`; platform via `iam.is_platform_admin()` / `plat.platform_admins` |

---

## 6. IADM-05 policy gate (D4 / D15)

| Item | Finding |
|---|---|
| Resolve contract | `GET /api/v1/iadm/05/domain/resolve-policy?policyType=&domainCode=` |
| Domain filter | For `RETENTION`, joins `gov.retention_rules.domain_code` |
| Published ANALYTICS | `DEMO-ANALYTICS-HANDLING` RETENTION v2 **EXISTS** |
| Published OPERATIONS / AUDIT_OVERSIGHT | **Not found** |
| Build plan | Create DEMO ONLY RETENTION + domain `OPERATIONS` through IADM-05 lifecycle (data only; author ≠ approver). If lifecycle cannot express domain binding, record finding and withhold until resolved |

---

## 7. IADM-07 alerts / evidence (D7 / D10)

| Item | Finding |
|---|---|
| Read endpoint | **EXISTS** — `GET /api/v1/iadm/07/alerts` with trusted scope (`tenantId` query + permission keys) |
| Create endpoint | `POST /alerts` exists on IADM-07 — IADM-08 must **not** call it; read-only proxy only |
| Evidence / registers | Findings/risks/standards list endpoints exist for linkage (US-012) |
| US-009 plan | Include IADM-07 security alerts via that read path with forwarded trusted identity |

---

## 8. Health targets (D5 / D6)

| Item | Finding |
|---|---|
| Gateway route registry | `GatewayConfig.routeTable` lists IADM-01…07 + other services — usable as **server-side** inventory source (preferred) |
| DEV health family | `/api/v1/iadm/0N/health` (IADM) and gateway `/actuator/health`; start script also checks `/health` on several ports |
| Client-supplied address | Forbidden — ignore/refuse any client host/URL parameter |
| Status mapping | Healthy / Degraded / Unavailable / Unknown; Stale only if source timestamp present |
| Persistence | None |

**Fallback label:** If route-registry read in-process is impractical, use a **minimal read-only in-service list** labelled as such (not a settings table).

---

## 9. Shared notification service (D8 / D16)

| Check | Result |
|---|---|
| `services/communication-services/notification-service` | Empty placeholder (`.gitkeep` only) — matches IADM-07 preflight |
| Decision | Best effort only; absence never blocks; never makes a story partial |

---

## 10. Database target verification (blocker gate)

| Check | Result |
|---|---|
| Supabase MCP `list_projects` | Only **`azulbaxszsqxymqmlwfe`** (CampXSync-Architecture-Dev, ACTIVE_HEALTHY) visible |
| Production ref `ekspahbsbwdpraaqfqui` | **Not** targeted; forbidden |
| Schemas `iadm08` / `ops` | **Do not exist** (and will not be created) |
| Legacy `adm08.*` keys | **Present** — gate `iam.user_profiles` / `iam.user_groups` (identity/CADM path). **Leave untouched.** New fine-grained `iadm08.*` keys only |

**Gate:** PASS for DEV permission migration via MCP `project_id=azulbaxszsqxymqmlwfe` only.

---

## 11. RBAC Auditor (R28) scope and export (D1)

| Check | Result |
|---|---|
| Matrix §8 IADM-08 | R01 **M**, R02 **M**, R03 **V** own institute, R04 **—**, R28 **V/AU/E (read-only)** |
| Auditor audit-scope assignment table / active assignment | **Not found** (same as IADM-06/07) |
| BUILD DEFAULT | Auditor (R28) = **platform-wide read-only** for audit, health and alerts; **no mutation**; **no export** |
| R01/R02 manage | Manage key **reserved, not implemented** (view/investigate only) |
| R03 | Own-institute audit only; unattributed events hidden; **no health** |
| R04 | Deny all IADM-08 routes |
| Export (D1 OWNER-OPEN) | Safe default: **no export key/endpoint**; verify absence (US-015) |

---

## 12. Working backlog reconstruction (CSV absent)

Owner path `docs/user-stories/IADM-08-user-stories.csv` was **not found**. A working 24-row CSV is written from:

1. Kickoff work-order story IDs and slice order  
2. Blueprint V1.2 panel 14 themes (US-001…015)  
3. Kickoff clarifications 1–12 and non-negotiable boundaries  
4. Technical-row analogy to IADM-06/07 (adapted: **no schema**)

| Story ID | Slice | Title (plain language) |
|---|---|---|
| IADM-08-US-001 | S0 | Preflight inventory and reconciliation |
| IADM-08-US-016 | S0 | IADM-08 service scaffold |
| IADM-08-US-017 | S0 | Permission keys and role mapping |
| IADM-08-US-018 | S0 | Additive permission-key migration (no schema) |
| IADM-08-US-019 | S0 | Shared audit read + fail-closed read recording |
| IADM-08-US-020 | S0 | Query limits, safe fields, IADM-05 policy client |
| IADM-08-US-024 | S1 | Demo readiness — reuse existing audit entries |
| IADM-08-US-002 | S1 | Search platform audit records |
| IADM-08-US-003 | S1 | Audit event detail |
| IADM-08-US-004 | S1 | Administrator activity timeline |
| IADM-08-US-005 | S1 | Institution Admin own-institute activity |
| IADM-08-US-006 | S1 | Auditor investigation |
| IADM-08-US-021 | S2 | Health target inventory (server-side) |
| IADM-08-US-007 | S2 | Service health overview |
| IADM-08-US-008 | S2 | Service health detail |
| IADM-08-US-009 | S2 | Operational alerts (incl. IADM-07 if available) |
| IADM-08-US-012 | S2 | IADM-07 evidence linkage |
| IADM-08-US-011 | S3 | SLA / availability “not available” |
| IADM-08-US-010 | S3 | Alert acknowledgement absence |
| IADM-08-US-015 | S3 | Export absence |
| IADM-08-US-022 | S3 | Deferred-feature and denial-event verification |
| IADM-08-US-013 | S4 | Access-denial and isolation tests |
| IADM-08-US-014 | S4 | Source-failure handling |
| IADM-08-US-023 | S4 | Evidence pack and close-out |

---

## 13. Demo personas (D17)

| Persona | Source | Notes |
|---|---|---|
| Super Admin (R01) | `.env.audit` `PLATFORM_ADMIN_EMAIL` = `platformadmin.campx@gmail.com` | Reuse |
| System Admin (R02) | Seed `systemadmin.campx@gmail.com` (IADM-07 headed) | Reuse; password in gitignored seed |
| Institution Admin (R03) | IADM-04/07 demo institutes A/B | Reuse |
| College Admin (R04) | `.env.audit` / prior headed | Reuse; expect deny |
| Auditor (R28) | IADM-07 headed seed | Reuse |
| Invented emails/IDs | — | **Forbidden** |

---

## 14. Proposed build constants (STEP 0 confirmed)

| Constant | Value | Meaning |
|---|---|---|
| Port | **8106** | IADM-08 service listen port |
| Route prefix | **`/api/v1/iadm/08`** | Gateway + service path |
| Key family | **`iadm08.*`** | Permission catalogue |
| Event names | **`IADM08_*`** | Shared audit actions for read recording |
| Max window (DEMO ONLY default) | **31 days** | Reject wider ranges (D4) |
| Page size (DEMO ONLY default) | **50 rows** | Configurable at runtime |
| IADM-05 domain | **OPERATIONS** (RETENTION) | Create DEMO ONLY if missing |
| Persistence | **None** | No IADM-08 table/schema/audit mirror |

### Proposed permission keys (US-017; exact strings reported at S0)

| Key | Roles | Meaning |
|---|---|---|
| `iadm08.audit.platform.view` | R01, R02, R28 | Platform audit search/detail/timeline |
| `iadm08.audit.institute.view` | R03, R28 | Own-institute audit activity |
| `iadm08.health.view` | R01, R02, R28 | Health overview/detail (not R03) |
| `iadm08.alerts.view` | R01, R02, R28 (+ R03 own-institute alert read via IADM-07 scope) | Read-only alerts |
| `iadm08.manage` | **reserved, not granted, not implemented** | Manage key reserved |

### Proposed read-audit event names (D3)

| Read | Event action |
|---|---|
| Audit search | `IADM08_AUDIT_SEARCH` |
| Event detail | `IADM08_AUDIT_DETAIL` |
| Activity timeline | `IADM08_AUDIT_TIMELINE` |
| Health overview | `IADM08_HEALTH_OVERVIEW` |
| Health detail | `IADM08_HEALTH_DETAIL` |
| Alerts read | `IADM08_ALERTS_READ` |

---

## 15. Decisions D1–D18 (what build will assume)

| ID | Assumption |
|---|---|
| D1 | No export feature/endpoint/key; verify absence |
| D2 | Query `audit.events` with safe fields + trusted institute filter |
| D3 | Every listed read recorded via `audit.log_event`; refuse if fail |
| D4 | 31-day / 50-row DEMO defaults; reject over-max window; IADM-05 gate |
| D5 | Health targets from gateway route table + known health paths; never client URL |
| D6 | Healthy/Degraded/Unavailable/Unknown; Stale only with source timestamp |
| D7 | No acknowledge; IADM-07 alerts via existing GET |
| D8 | No escalation; notifications non-blocking |
| D9 | SLA section = “not available” only |
| D10 | IADM-07 GET `/alerts` verified — include |
| D11 | CADM-07 college audit; R04 denied on IADM-08 |
| D12 | Port 8106, route `/api/v1/iadm/08` |
| D13 | Keys `iadm08.*`, events `IADM08_*` |
| D14 | No IADM-08 tables/schema |
| D15 | Exactly one published OPERATIONS retention policy or withhold |
| D16 | Notifications optional |
| D17 | Reuse existing demo personas and audit rows; never insert into shared audit |
| D18 | No Intelligence consumer invented |

---

## 16. Blocker gate summary

| Gate | Status |
|---|---|
| `audit.events` readable | **PASS** (521 rows) |
| `audit.log_event` reusable | **PASS** |
| DB target = DEV `azulbaxszsqxymqmlwfe` | **PASS** |
| CSV file present | **FAIL → TBC** (reconstructed working backlog; not a hard stop per kickoff “TBC and continue”) |
| New infrastructure required | **No** |

**Preflight complete. Continuing to S0.**
