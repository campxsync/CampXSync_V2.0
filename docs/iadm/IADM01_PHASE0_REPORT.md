# IADM-01 Phase 0 Report (read-only verification)

**Date:** 2026-11-22  
**Story:** IADM01-TECH-001 (Phase 0 read-only verification)  
**Owner authorization:** Niranjan V.G. — IADM-01 slices S0–S4 (DEV only), recorded 2026-11-22  
**DEV project:** `azulbaxszsqxymqmlwfe` (CampXSync-Architecture-Dev). Production not touched.  
**Tags:** CONFIRMED / INFERRED / NOT FOUND  
**Plain language:** Phase 0 = inspect what already exists before writing code so nothing is invented or duplicated.

---

## Owner notice — blueprint path mismatch

Task listed `docs/user-stories/CampXSync_IADM-01_Detailed_Blueprint_V2_1.md`.  
**NOT FOUND** in the git repo.  
**CONFIRMED** at external path:  
`D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-01_Detailed_Blueprint_V2.1.md`  
Same for `IADM_Service_Blueprint_Template_v1.md` (external Documentation folder).  
**Action taken:** Follow CSV + fixed decisions in the task; use external blueprint as design reference. Logged as FINDING-IADM01-DOC-01.

---

## 1. Blueprint item map (16 panels) — EXISTS / NEW

| # | Blueprint panel | Verdict | Evidence |
|---|---|---|---|
| 1 | Purpose & scope | EXISTS (design) | External blueprint V2.1 [CONFIRMED] |
| 2 | Architecture UI→BFF→GW→Java→DB | EXISTS (pattern) | Live path for Platform desks via `/api/v1/admin/**` → institute-admin `:8081` [CONFIRMED: `GatewayConfig.java:54-58`] |
| 3 | Responsibilities | NEW module needed | No `iadm-01` module on HEAD after rollback [CONFIRMED: `administration-services/pom.xml` modules = institute-admin + college-admin only] |
| 4 | Actors & access (R01/R02/R03/R04/R28) | EXISTS (RBAC) | Matrix v3.0 + Phase 0–4 reports [CONFIRMED] |
| 5 | Screens & journeys | PARTIAL | Platform Institutes list/detail/wizard exist under `/platform/institutes*` [CONFIRMED]; Institution Admin (R03) home desk **NOT FOUND** as dedicated IADM desk; middleware maps `INSTITUTION_ADMIN` into college-admin portal [CONFIRMED: `frontend/src/middleware.ts:130-131`] |
| 6 | API `/api/v1/iadm/01/**` | NEW | Gateway has **no** `/api/v1/iadm/01` route [CONFIRMED: `GatewayConfig.java`]; legacy institutes under `/api/v1/admin/institutes` [CONFIRMED] |
| 7 | Data model | PARTIAL | `core.tenants` (institute = tenant) and `core.colleges` **EXIST** [CONFIRMED SQL]. Lifecycle/onboarding/handover tables from prior IADM wave **gone** after rollback [CONFIRMED: `to_regclass` null for handovers/events/requests/onboarding] |
| 8 | Permission keys | EXISTS | `adm01.*` and `adm06.*` in `iam.permissions` [CONFIRMED SQL below] |
| 9 | Onboarding resume sequence | NEW | No `plat.institute_onboarding_steps` (or equivalent) after rollback [CONFIRMED] |
| 10 | Business rules / states | PARTIAL | `core.tenants.status` / `core.colleges.status` columns exist (text) [CONFIRMED]; allowed values currently mixed / not locked to ONBOARDING/ACTIVE/SUSPENDED/ARCHIVED set |
| 11 | Failure handling messages | NEW (product copy) | Must be implemented in API + UI |
| 12 | Integrations Auth/IAM/CADM/Audit | PARTIAL | `plat.assign_institution_admin` EXISTS (K1) [CONFIRMED]; `plat.is_auth_linked_user` **NOT FOUND** after rollback [CONFIRMED SQL]; CADM services **NOT FOUND**; shared audit tables EXIST |
| 13 | Exists vs New summary | See §2–§7 | — |
| 14 | User stories | Tracker CSV | `docs/user-stories/IADM-01_User_Stories.csv` [CONFIRMED] |
| 15 | Demo data & checklist | NEW for this attempt | Prior demo seed rolled back; test institutes remain [CONFIRMED: `docs/iadm/rollback/iadm_test_tenants_since_2026-11-21.csv`] |
| 16 | Non-goals / CADM wait | CONFIRMED | CADM not built; handover uses INTERIM acknowledgements per fixed decisions |

---

## 2. Table-name map

| Concept | Table / object | Status | Notes |
|---|---|---|---|
| Institute (tenant) | `core.tenants` | **EXISTS** | Columns: id, code, name, legal_name, status, currency_code, timezone, locale, profile, onboarded_at, … deleted_at [CONFIRMED SQL] |
| College | `core.colleges` | **EXISTS** | Columns include tenant_id, code, name, status, provisioning_status, profile, … [CONFIRMED SQL] |
| RLS on institute/college | ENABLE | **EXISTS** | `relrowsecurity=true` [CONFIRMED] |
| FORCE RLS | — | **GAP** | `relforcerowsecurity=false` on both tables [CONFIRMED] — FINDING-IADM01-SEC-01 |
| Appointment function | `plat.assign_institution_admin` | **EXISTS (K1)** | Uses `auth.uid()`; spoofed `p_assigned_by` ignored [ROLLBACK_REPORT + prior exploit fix] |
| Auth-linked check | `plat.is_auth_linked_user` | **NOT FOUND** | Rolled back with Wave C/D migration — migration gap |
| Onboarding step store | (none) | **NEW needed** | For TECH-006 / US-013 resume |
| Lifecycle requests | (none) | **NEW needed** | For US-010/011/023 |
| Lifecycle history | (none dedicated) | **NEW needed** | Prior `plat.institute_lifecycle_events` removed |
| CADM handover | (none) | **NEW needed** | Prior `plat.college_admin_handovers` removed; redesign for dual CADM-01/CADM-02 acknowledgements + INTERIM |
| Shared audit events | `audit.events` (+ partitions) | **EXISTS** | Written by `PostgresAuditRepository.appendAuditEvent` [CONFIRMED: `logger/.../PostgresAuditRepository.java:26-32`] |
| Shared audit change log | `audit.change_log` (+ partitions) | **EXISTS** | Written by `appendChangeLog` [CONFIRMED: same file:44-49]; also DB triggers historically |
| Access events | `audit.access_events` | **EXISTS** | Separate from mutation audit |
| Provisioning ledger | `plat.tenant_provisioning` | **EXISTS** | Legacy onboard path [CONFIRMED table listing] |

---

## 3. Permission-key map (no new keys invented)

| Capability | Exact key (CONFIRMED in `iam.permissions.code`) | Actors (matrix / CSV) |
|---|---|---|
| Institute manage / create / destructive decide | `adm01.manage` | Super Admin (R01); platform operators with manage |
| Institute write / update identity | `adm01.write` | R01; Institution Admin (R03) own institute |
| Institute view | `adm01.view`, `adm01.view.all` | R01, R02, R03 (scoped), Auditor (R28) read |
| Institute review | `adm01.review` | As catalogue |
| College manage / create (institute-wide) | `adm06.manage` | R03 own institute; R01 |
| College write / update | `adm06.write`, `adm06.write.own` | R03; R04 own college (CADM scope — must not unlock IADM-01 institute ops) |
| College view | `adm06.view`, `adm06.view.all`, `adm06.view.own` | Scoped reads |
| College review | `adm06.review` | As catalogue |

**R04 College Admin still holds `adm01.view` / `adm01.view.all`** (EXCEPTIONS.md — CADM profile view / RLS fallback) [CONFIRMED: `docs/audits/rbac-implementation/EXCEPTIONS.md`]. Java IADM-01 institute **mutations and platform list** must deny R04 explicitly (US-015); do not invent a new key.

---

## 4. Route-collision check

| Prefix | Target today | Collision with `/api/v1/iadm/01/**`? |
|---|---|---|
| `/api/v1/admin/**` | institute-admin `:8081` [CONFIRMED: `GatewayConfig.java:58`] | **No** if IADM-01 uses `/api/v1/iadm/01/**` and is registered **before** any broader catch-all |
| `/api/v1/iadm/01/**` | **NOT FOUND** on HEAD | Free — assign port **8099** (prior plan; free after rollback) [CONFIRMED ROLLBACK_REPORT] |
| `/v1/institutes`, `/v1/onboard-tenant` | aliases → `:8081` [CONFIRMED: `GatewayConfig.java:136-142`] | Leave legacy; new UI BFF must call `/api/v1/iadm/01/...` |

---

## 5. Audit: which table search reads vs what mutations must write

| Path | What it reads / writes | Verdict |
|---|---|---|
| Shared logger `PostgresAuditRepository` | **Writes** `audit.events` and `audit.change_log` [CONFIRMED] | Use this for IADM-01 mutations (durable, same connection) |
| Platform page `/platform/audit` | Calls gateway `GET /api/v1/admin/audit-logs` [CONFIRMED: `frontend/.../platform/audit/page.tsx`] | — |
| `InstituteAdminDomainService.searchAudit*` | Searches **in-memory** `auditTrail` list, **not** PostgreSQL [CONFIRMED: `InstituteAdminDomainService.java:113-123`, `2209-2224`] | **FINDING-IADM01-AUD-01** — explains “audit search found nothing” |
| `PostgresSystemAuditLogRepository` | Reads `audit.events` [CONFIRMED] | Exists but not wired into the Platform audit search path used by the UI |
| Triggers → `audit.change_log` | Historical pattern | Secondary; IADM-01 must not rely on triggers alone |

**Decision for IADM-01:** Every mutation appends to `audit.events` (action = `IADM01_*`) via shared logger in the same DB transaction. Institute Audit trail tab and Ops search for IADM-01 history will read `audit.events` (and/or `plat.institute_lifecycle_events` once recreated). Fixing the legacy in-memory Platform audit search is **out of unrelated-defect scope** unless required for US-025 demo — then wire a minimal read of `audit.events` for IADM actions only.

---

## 6. Supabase Auth admin capability

| Item | Status |
|---|---|
| Document service uses `CAMPX_SUPABASE_SERVICE_ROLE_KEY` for Storage | EXISTS [CONFIRMED: `DocumentStorageConfig.java`] |
| Backend Auth Admin invite/create user client in IADM path | **NOT FOUND** on HEAD after rollback |
| Prior Wave REWORK finding | Invite-by-email required existing Auth-linked profile [FINDINGS.md REWORK-02] — **rejected by this task**; login must be created/invited first |
| Blocker rule | If service-role / Auth admin env cannot be configured safely → STOP and report; never hard-code or print keys |

---

## 7. Reusable code on tag `backup-iadm-before-rollback`

| Artifact | Reuse? | Caveat |
|---|---|---|
| Module `iadm-01-tenant-lifecycle-service` (port 8099, HMAC, Hikari, controller skeleton) | **Reuse patterns** after re-verify | Do **not** restore wholesale |
| `TenantLifecycleService.registerInstitute` | Partial | Inserted status **`ACTIVE`** [CONFIRMED backup source] — must be **`ONBOARDING`** per fixed decisions |
| Appoint path | Partial | Wired to two-person quorum — **default OFF** for TECH-007 |
| Gateway / stack script entries | Reuse shape | Re-register `/api/v1/iadm/01` before `/api/v1/admin` |
| Wave A SQL under `docs/iadm/rollback/migrations/` | Reference only | Re-apply as **new** version numbers (`20261122…`); extend for dual CADM acknowledgements + onboarding steps |

---

## 8. Gaps needing a migration (NEW version numbers)

Tracker still lists rolled-back IADM versions as applied — **do not reuse** `20261121000200`…`120000`. Proposed new series:

| Version (proposed) | Purpose |
|---|---|
| `20261122010000` | Recreate IADM-01 lifecycle tables: onboarding steps, lifecycle events, lifecycle requests, dual-ack handovers; RLS + FORCE; grants to `authenticated` |
| `20261122010100` | Recreate `plat.is_auth_linked_user` (or equivalent Auth-link helper) if required by appointment path |
| `20261122010200` | Optional: FORCE RLS on `core.tenants` / `core.colleges` only if owner accepts (logged as security finding; may be separate) |
| `20261122010300` | DEV-only demo seed for INST-001 / COL-001 personas (TECH-005); refuse non-DEV |

---

## 9. Frontend / portal routing (US-005)

| Check | Result |
|---|---|
| Platform nav Institutes & Onboard | EXISTS [CONFIRMED: `nav-config.ts`] |
| R03 Institution Admin dedicated home | **NOT FOUND** |
| Middleware | `INSTITUTION_ADMIN` treated as college-admin user [CONFIRMED: `middleware.ts:131`] — **must change** so R03 lands on Institution Admin home |
| Stale BFF after rollback | Some `/api/platform/institutes/.../appoint-admin` still references `/api/v1/iadm/01` and `/api/v1/iadm/03` [CONFIRMED grep] — rewire to new IADM-01 only |

---

## 10. Port registry decision

| Service | Port | Gateway prefix |
|---|---|---|
| IADM-01 Institute & Tenant Lifecycle | **8099** | `/api/v1/iadm/01` |
| Legacy Institute Admin (IAM + remaining admin) | 8081 | `/api/v1/admin` |

Ports 8100–8106 reserved for future IADM-02…08 (out of this task).

---

## 11. Findings filed (see also `docs/iadm/FINDINGS.md`)

| ID | Severity | Summary |
|---|---|---|
| FINDING-IADM01-DOC-01 | LOW | Blueprint files live outside git repo |
| FINDING-IADM01-SEC-01 | MEDIUM | `core.tenants` / `core.colleges` RLS enabled but not FORCED |
| FINDING-IADM01-AUD-01 | HIGH | Platform audit search uses in-memory trail, not `audit.events` |
| FINDING-IADM01-AUTH-01 | HIGH | `plat.is_auth_linked_user` missing after rollback — blocks Auth-link checks until migration |
| FINDING-IADM01-UI-01 | HIGH | R03 portal routing conflated with College Admin |
| FINDING-IADM01-CADM-01 | MEDIUM | CADM-01/02 absent — INTERIM handover acknowledgements required |

---

## 12. TECH-001 exit criteria

- [x] Institute/college tables + RLS inspected (DEV)  
- [x] Existing endpoints + gateway registry inspected  
- [x] Permission keys mapped (no invent)  
- [x] Auth admin capability status recorded  
- [x] Shared-audit write/read contract clarified  
- [x] Backup tag reusable code noted with caveats  
- [x] Route collision checked  
- [x] Migration gaps listed  
- [x] No database writes / no code changes in this story  

**Next:** IADM01-TECH-002 (microservice + gateway + scripts) automatically.
