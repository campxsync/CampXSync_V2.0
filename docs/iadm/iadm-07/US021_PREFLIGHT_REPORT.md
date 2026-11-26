# IADM-07-US-021 — Preflight inventory and reconciliation

**Story:** Preflight inventory and reconciliation (IADM-07-US-021)  
**Mode:** Read-only · **Date:** 2026-11-26  
**Branch:** `feature/java-backend-integration`  
**Environment:** DEV Supabase project `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev)

---

## 1. Working tree and branch

| Check | Result |
|---|---|
| Branch | `feature/java-backend-integration` (tracks origin; up to date at session start) |
| HEAD at preflight | `fad012b` — feat(iadm06): S4 close-out — headed film, failure suite, policy lifecycle |
| Dirty tracked files | None |
| Untracked of note | `docs/user-stories/IADM-07-user-stories.csv`; prior evidence/classpath noise |
| Push | Not performed (owner rule: local commits only) |

---

## 2. Input documents (located / missing)

| Input | Location | Status |
|---|---|---|
| IADM-07 Detailed Blueprint V1.1 (Markdown) | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-07_Detailed_Blueprint_V1_1.md` | **Found** |
| IADM-07 Blueprint PNG | Same folder; names containing IADM-07 | **Not found** (TBC — Markdown is sole blueprint; architecture PNGs exist but are not IADM-07-specific) |
| IADM-07-user-stories.csv (32 rows, S0–S4) | `docs/user-stories/IADM-07-user-stories.csv` | **Found** |
| CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md | `D:\CampXSync\Documentation\OverAllArchitecture\` (+ repo `docs/architecture/Security/` copy) | **Found** |
| IADM_Architecture_Design.md v2.0 | `D:\CampXSync\Documentation\Platform_InstituteAdmin\IADM_Architecture_Design.md` | **Found** — §5.7 names **Security & Compliance Governance** |
| IADM-01 / 02 / 04 / 05 / 06 services | `services/administration-services/iadm-0{1,2,4,5,6}-*` | **Found** |
| Existing IADM-07 service / gateway / stack registration | — | **Not found** (expected) |

---

## 3. Conventions from IADM-01 / 02 / 04 / 05 / 06 → IADM-07 decision

| Concern | Convention found | IADM-07 decision |
|---|---|---|
| Directory | `services/administration-services/iadm-0N-<kebab>-service` | `iadm-07-security-compliance-service` |
| Port | 8099 / 8100 / 8101 / 8102 / 8103 / **8104** | **8105** (next free; matches blueprint) |
| Gateway prefix | `/api/v1/iadm/0N` in `api-gateway/.../GatewayConfig.routeTable` (before legacy `/api/v1/admin`) | `/api/v1/iadm/07` → `http://localhost:8105/api/v1/iadm/07` |
| Package | `com.campx.iadm.<domain>` | `com.campx.iadm.security` |
| Main class | `Iadm0NApplication` | `Iadm07Application` |
| Maven | Parent `campx-sync-parent`; deps `campx-logger`, PostgreSQL, HikariCP, JUnit | Same |
| Permission keys | Newest pattern **`iadm0N.*`** (IADM-04/05/06) | **`iadm07.*`** (matches blueprint family) |
| Schema naming | Short domain labels: IADM-05 `gov`, IADM-06 `anl` (not `iadm05`/`iadm06`) | **`sec`** — short name aligned with architecture label `security`. Blueprint proposal `iadm07` **does not match** short-schema convention → overridden by convention |
| Migrations | `database/migrations/YYYYMMDDHHMMSS_iadm0N_*.sql` + paired rollback | Same; DEV target comment required |
| RLS style | `ENABLE` + **`FORCE ROW LEVEL SECURITY`** | Same on every IADM-07 register table |
| Request context | `GatewayHmacVerifier` + `UserSecurityContext`; `SET LOCAL ROLE authenticated` + `request.jwt.claims` | Same |
| Logging | `CampXLogger` / `CampXLoggerFactory` | Same |
| Stale / runtime setting | IADM-06 DEMO ONLY runtime threshold (in-memory, adjustable) | DEMO ONLY **stale threshold** runtime setting in S1 (US-029); value reported at build |
| Frontend desk | `/platform/<desk>` + BFF `/api/platform/<desk>` → `fetchFromGateway`; `data-testid="iadm0N-..."` | `/platform/security`, `/institution/security`, `/college/security`, `/platform/security/audit`; `iadm07-*` |
| Stack scripts | `start_stack.ps1`, `check_stack.ps1`, `restart_service.ps1` | Register IADM-07 **:8105** |
| Demo personas | Seed accounts + `.env.audit` (never invent emails) | Reuse IADM-04/05/06 pattern |

**Route collision check:** `/api/v1/iadm/07` is **absent** from `GatewayConfig.routeTable`. Legacy `/api/v1/admin` is a separate namespace. **No collision.**

---

## 4. Shared audit (D10) — blocker gate

| Claim | Finding |
|---|---|
| Second audit store required? | **No** — reusable capability exists |
| Store | Schema `audit`, table `audit.events` (columns include `action`, `principal_id`, `correlation_id`, `detail`, `tenant_id`, `status`) |
| Writer API | `audit.log_event(...)` (SECURITY DEFINER) — **EXISTS** |
| Fail-closed pattern | IADM-04/05/06 `DurableAuditWriter` → same-TX `SELECT audit.log_event(...)`; `503 *_AUDIT_UNAVAILABLE` |
| Grant | Already granted to `authenticated` / `service_role` |

**Decision:** Reuse `DurableAuditWriter` pattern. No second audit store. **Blocker cleared.**

### 4a. Audit read paths (clarification 5)

| Path | Finding | Consequence |
|---|---|---|
| **General shared-audit read** | **EXISTS** — `audit.events` SELECT RLS via `audit.read`; IADM-01 reads `action LIKE 'IADM01_%'`; IADM-02 reads by action (platform elevate); IADM-03 uses `audit.list_iadm03_evidence` | US-012 proceeds using the **IADM-01-style** `audit.events` filter on `IADM07_%` actions (not the IADM-05/06 auditor desks, which are register read-only UIs, not shared-audit tabs) |
| **Denial-event read path** | **Not found** as a dedicated denial-type contract. No shared “denial events” list API. `audit.list_iadm03_evidence` is IADM-03-specific only | US-006 denial-event display **omitted**; that part of US-006 marked **partial** |

Note: IADM-05/06 `/platform/.../audit` pages are Auditor (R28) register desks, **not** a shared `audit.events` read path. CSV wording “same read path as IADM-05 and IADM-06 audit tabs” conflicts with code reality; build uses the **verified general path** (IADM-01/`audit.read`) and reports the conflict.

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

## 6. Service-caller authentication (IADM-05 domain contract pattern)

| Item | Finding |
|---|---|
| Contract key | `iadm05.domain.contract` |
| How accepted | Same gateway HMAC + identity headers; permission checked via `iam.has_permission('iadm05.domain.contract')` (or alternate view keys for resolve) |
| Machine persona | Offline matrix lists `SERVICE` holding only `iadm05.domain.contract` |
| IADM-07 intake | Mirror with **`iadm07.signal.intake`**; DEMO ONLY simulator calls the real intake endpoint with authenticated service identity |

---

## 7. Shared notification service (D9 / clarification 6)

| Check | Result |
|---|---|
| `services/communication-services/notification-service` | **Only `.gitkeep`** — no implemented shared notification service |
| IADM-04 pattern | In-app / delayed notice flags; notifications best-effort, never blocking |

**Decision:** In-app alerts only; shared delivery absent → **never blocks** alert workflows; never makes a story partial.

---

## 8. Database target verification (blocker gate)

| Check | Result |
|---|---|
| Supabase MCP `list_projects` | Only **`azulbaxszsqxymqmlwfe`** (CampXSync-Architecture-Dev, ACTIVE_HEALTHY) visible |
| Production ref `ekspahbsbwdpraaqfqui` | **Not** targeted; forbidden |
| Schema `iadm07` / `sec` / `security` | **Do not exist** (verified) |
| Schemas present | `gov`, `anl`, `audit`, `iam` (among others) |

**Gate:** PASS for DEV migrations via MCP `project_id=azulbaxszsqxymqmlwfe` only.

---

## 9. Legacy `adm07.*` and security governance code (D16)

| Artifact | DEV status | Disposition |
|---|---|---|
| `adm07.view`, `adm07.view.all`, `adm07.view.own`, `adm07.write`, `adm07.write.own`, `adm07.review`, `adm07.approve`, `adm07.manage` | **Present** on DEV | **Leave untouched.** These gate `core.departments` (Departments & Academic Units / CADM), **not** IADM-07 security governance |
| Wave-C coarse `iadm07.view` / `iadm07.write` / `iadm07.manage` (docs rollback migrations) | **Absent** from DEV catalogue | Do **not** resurrect. **NEW** fine-grained `iadm07.*` keys (IADM-05/06 style) |
| Dedicated IADM-07 Java service | **Not found** | **NEW** |
| Monolith `/api/v1/admin` security routes | Not claimed as IADM-07 owner | **Do not modify** |

---

## 10. RBAC Auditor (R28) scope and college attribute (D4)

| Check | Result |
|---|---|
| Matrix §8 IADM-07 | R01 **M**, R02 **M**, R03 **W** own institute posture, R04 **V**, R28 **V** |
| Auditor audit-scope assignment table / active assignment | **Not found** (no `*audit*scope*` table; matches IADM-06 preflight) |
| BUILD DEFAULT | Auditor (R28) = **platform-wide read-only**; **no mutation**; **no export** |
| College attribute for IADM-07 registers | College entities exist under institutes (IADM-01), but **no proven college-scoped security-register attribute** for findings/risks |
| R04 BUILD DEFAULT | Published standards + **institute posture summary counts only**; missing/ambiguous institute scope → **deny**. No individual findings/risks/signals/evidence |

---

## 11. Demo institutes and personas (reuse; invent no emails)

| Persona | Pattern (IADM-05/06 headed demos) | Notes |
|---|---|---|
| Super Admin (R01) | `platformadmin.campx@gmail.com` / `PLATFORM_ADMIN_*` | Password in `.env.audit` only — never print |
| System Admin (R02) | `systemadmin.campx@gmail.com` / seed `r02` | Found |
| Institution Admin (R03) A | `institution.iadm04.demo@campx.local` · tenant `d4000000-0000-4000-8000-000000000004` (`IADM04_DEMO`) | **Verified ACTIVE** on DEV |
| Institution Admin (R03) B | `maharani.campx@gmail.com` · tenant `72ecbfe3-2077-41b5-8a20-b6d3e045311b` (`MAC`) | **Verified ACTIVE**; used in IADM-06 headed isolation |
| College Admin (R04) | `campxsync@gmail.com` | Found |
| Auditor (R28) | `auditor.iadm02.dev@campx.local` | Found |

Also present: `INST-DEMO-001` (IADM-01 Demo Institute) — available if needed; prefer the two institutes already used in isolation films.

---

## 12. Proposed permission keys + audit event names (D3)

| Capability | Permission key | Audit event name |
|---|---|---|
| View posture / indicators | `iadm07.posture.view` | `IADM07_POSTURE_VIEW` |
| Read registers | `iadm07.register.read` | `IADM07_REGISTER_READ` |
| Write registers (findings/risks/alerts/triage) | `iadm07.register.write` | `IADM07_REGISTER_WRITE` |
| Propose security standard | `iadm07.standard.propose` | `IADM07_STANDARD_PROPOSE` |
| Approve / publish standard (R01 only) | `iadm07.standard.approve` | `IADM07_STANDARD_APPROVE` / `IADM07_STANDARD_PUBLISH` |
| Request exception | `iadm07.exception.request` | `IADM07_EXCEPTION_REQUEST` |
| Decide exception (R01 only) | `iadm07.exception.decide` | `IADM07_EXCEPTION_DECIDE` |
| Signal / enforcement intake (service caller) | `iadm07.signal.intake` | `IADM07_SIGNAL_INTAKE` / `IADM07_ENFORCEMENT_REPORT` |
| View reports (on screen) | `iadm07.report.view` | `IADM07_REPORT_VIEW` |
| View shared audit evidence | `iadm07.audit.view` | `IADM07_AUDIT_VIEW` |
| R04 summary (published standards + counts) | `iadm07.summary.view` | `IADM07_SUMMARY_VIEW` |

**Holders (no wildcard; no other role):**  
- **R01** Super Admin — all (platform-admin path + keys; sole approver/decider)  
- **R02** System Admin — all except `standard.approve` and `exception.decide`  
- **R03** Institution Admin — `posture.view`, `register.read`, `register.write` (limited fields), `exception.request`, `report.view`, `audit.view`  
- **R04** College Admin — `summary.view` only  
- **R28** Auditor — `posture.view`, `register.read`, `report.view`, `audit.view` (no write, no export)  
- **SERVICE** — `signal.intake` only  

Legacy `adm07.*` **untouched**.

---

## 13. Proposed physical schema (D2)

| Object | Name |
|---|---|
| Schema | **`sec`** (FORCE RLS on every table) |
| Registers | `sec.security_standards`, `sec.signals`, `sec.findings`, `sec.risks`, `sec.controls`, `sec.control_assessments`, `sec.evidence_refs`, `sec.exceptions`, `sec.alerts`, `sec.enforcement_status` |

Evidence columns store **reference only** (identifier/address, source, time) — never a file payload.  
No audit table, no identity table, no configuration/settings table, no copies of other services' data.

---

## 14. Build defaults assumed (D1–D16)

| # | Assumed for this build |
|---|---|
| D1 | Port **8105**, route `/api/v1/iadm/07`, module `iadm-07-security-compliance-service` |
| D2 | Schema **`sec`** with FORCE RLS (blueprint `iadm07` overridden by short-schema convention) |
| D3 | Key family `iadm07.*` as §12; legacy `adm07.*` untouched |
| D4 | Role/field map per kickoff; R28 platform-wide read-only; R04 summary counts only; no college-scoped finding detail |
| D5 | No external scanner/SIEM; manual + authenticated intake; simulator DEMO ONLY |
| D6 | Controls DEMO ONLY; free-text framework; no certification claim |
| D7 | Posture indicators from IADM-07 registers only; stale never shown healthy/zero; production stale policy OWNER-OPEN |
| D8 | R01 sole approver/decider; requester ≠ decider; validity window mandatory; expiry derived at read time |
| D9 | In-app alerts; shared notification absent → best effort N/A, never blocks |
| D10 | Shared `audit.log_event` fail-closed |
| D11 | No Kafka/outbox; intake authenticated; simulator DEMO ONLY |
| D12 | Evidence = reference only; privacy lifecycle stays IADM-05 |
| D13 | On-screen reports only; no export for any role |
| D14 | DEMO ONLY stale threshold runtime setting in S1; value reported |
| D15 | R02 manages/triages/proposes; never approves/decides; no self-grant |
| D16 | NEW service; leave `adm07.*` and departments alone; do not resurrect wave-C coarse keys |

---

## 15. CSV ↔ Blueprint conflicts reported (do not silently reconcile)

| Conflict | Resolution for build |
|---|---|
| Blueprint R04 “college scope” vs CSV “institute posture summary” | CSV wins → institute summary counts |
| Blueprint US-005 “assigned scope” vs CSV/build default platform-wide | Platform-wide (no audit-scope assignment found) |
| CSV “same audit path as IADM-05/06 audit tabs” vs code (those tabs are register desks) | Use verified general `audit.events` path (IADM-01 style) |
| Blueprint schema `iadm07` vs short-schema convention `gov`/`anl` | Use **`sec`** |

---

## 16. Blockers?

| Potential blocker | Status |
|---|---|
| No reusable shared audit | **Cleared** — `audit.log_event` / `audit.events` |
| DB target not DEV | **Cleared** (`azulbaxszsqxymqmlwfe`) |
| New infrastructure / tool | None required |
| Boundary contradiction | None found |

**Preflight conclusion:** Proceed to S0 (IADM-07-US-022 … US-025).

---

*Abbreviations: RLS = row-level security (database policies); FORCE RLS = policies apply even to table owner; HMAC = Hash-based Message Authentication Code (gateway→service trust); E2E = end-to-end headed browser demo; tsc = TypeScript compiler check (`npx tsc --noEmit`).*
