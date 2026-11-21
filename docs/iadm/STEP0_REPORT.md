# IADM Step 0 Report — Preflight

**Date:** 2026-11-21  
**Owner approval:** Niranjan V.G. (IADM Waves A–D in DEV)  
**Branch:** `feature/java-backend-integration`  
**DB:** DEV Supabase `azulbaxszsqxymqmlwfe` only  
**Tags:** CONFIRMED / INFERRED / NOT FOUND

---

## 0.1 Appointment-function security check

| Item | Result |
|---|---|
| Function | `plat.assign_institution_admin(p_tenant_id, p_user_id, p_assigned_by)` |
| Pre-fix auth | Trusted **`p_assigned_by`** + `CURRENT_USER NOT IN ('authenticated','anon')` bypass [CONFIRMED: `20261120000300_rbac_phase3_code_alignment.sql:42-50`] |
| Grants | `EXECUTE` to `authenticated`, `service_role` [CONFIRMED live] |
| Exploit | College Admin (R04) `70a8ffae-…` as `auth.uid()`, spoofing Platform Admin `54e8308c-…` as `p_assigned_by` → **VULNERABLE** assignment `ae37117f-…` [CONFIRMED SQL] |
| Fix | Migration `20261121000100_iadm_assign_institution_admin_auth_uid.sql` (+ rollback). Caller = `auth.uid()`; parameter ignored. |
| Post-fix spoof | **REFUSED** [CONFIRMED] |
| Post-fix legit | Platform admin allowed, then cleaned [CONFIRMED] |
| Cleanup | `ba77b8f7-…` INSTITUTION_ADMIN leftovers removed; remains COLLEGE_ADMIN only [CONFIRMED] |

---

## 0.2 Request-context leakage check

| Service / controller | Pattern | Action |
|---|---|---|
| AttendanceController | finally removes shared HttpContext body attrs | Already fixed (Phase 4) [CONFIRMED] |
| SubjectController | same body attrs, no cleanup | **Fixed** (identical pattern) |
| AnalyticsController | same | **Fixed** (identical pattern) |
| InstituteAdminController | `campx.request.body` | Logged FINDING-IADM-SEC-02 MEDIUM |
| CollegeAdminController | body + `campx.auth.result` | Logged MEDIUM |
| CourseController | body + auth.result | Logged MEDIUM |
| CurriculumController | `tenantId` / `userId` on context | Logged **HIGH** |

ThreadLocal identity stores used as request-scoped security contexts were reviewed; production controllers clear `LogContext` in finally. Curriculum identity-on-HttpContext is the outstanding high-severity leak.

---

## 0.3 Story reconciliation

**Sources:** Draft `IADM_Implementation_Plan_and_User_Stories_DRAFT.md` (unchanged); `IADM_Architecture_Design.md` v2.0 at `D:\CampXSync\Documentation\Platform_InstituteAdmin\` [CONFIRMED outside repo]; legacy ADM-01 CSV (34 stories / 8 epics); live code under `institute-admin-service`.

**Output:** `docs/user-stories/IADM_User_Stories.md` — wave order unchanged (A→B→C→D). Deferred items stay deferred (IADM-03-US-008/009 break-glass & two-person; IADM-05-US-007 guardian consent / D-RBAC-M17; Kafka; `adm03` / API Client R27 per IADM-D5).

No dependency forced a wave reorder. CADM contracts published early per draft §4.

---

## 0.4 Code inventory (per IADM service)

Host today: **`institute-admin-service` :8081** — `InstituteAdminController` `/api/v1/admin/**` [CONFIRMED].

| Service | Port (plan) | Existing surface | Story status summary |
|---|---|---|---|
| IADM-01 Institute & Tenant Lifecycle | **8099** | institutes, colleges, onboard-tenant, tenants/provision | US-001 PARTIAL; US-002 EXISTS (fn hardened); US-003 PARTIAL; US-004 NEW; US-005 PARTIAL; US-006/007/008 NEW |
| IADM-02 Platform Configuration & Policy | **8100** | configuration, snapshot, versions, rollback; `cfg.setting_*`; `cfg.effective_setting()` | US-001–003 PARTIAL; US-004–006 NEW (lock/delegate/effective contract); US-007 PARTIAL; US-008 NEW |
| IADM-03 Platform Identity & Access | **8101** | users, roles, bindings, reviews, delegations, role-templates | US-001/003/005/006 PARTIAL; US-002/004 NEW; US-008/009 DEFERRED |
| IADM-04 Subscription / Billing / Entitlements | **8102** | billing/plans|subscriptions|invoices|transactions; `adm01.manage` gated | US-001/002/005 EXISTS; US-003/006 PARTIAL; US-004/007 NEW |
| IADM-05 Data Governance & Privacy | **8103** | data-governance/retention|classifications, exports | US-001–006/008 mostly NEW/PARTIAL; US-007 DEFERRED |
| IADM-06 Platform Analytics & Usage | **8104** | usage metrics endpoints | All NEW / thin PARTIAL on usage |
| IADM-07 Security & Compliance | **8105** | policies (global) partial | Mostly NEW |
| IADM-08 Audit, Health & Operations | **8106** | audit-logs, access-events, change-log, operations/health|alerts | US-001–003 PARTIAL; US-004–006 NEW |

Tables: `core.tenants/colleges`, `iam.*`, `cfg.*`, `plat.*` (billing, health, alerts, platform_admins, tenant_provisioning), `audit.*` — baseline `Scripts_V3.0/20_tables.sql` [CONFIRMED].

---

## 0.5 Permission-key map

| Story family | Keys (existing first) | New keys |
|---|---|---|
| IADM-01 | `adm01.manage`, `adm01.write`, `adm01.view`, `adm01.view.*` | none for Wave A |
| IADM-02 | `adm05.manage`, `adm05.write`, `adm05.view` | none; lock/delegate are flags not keys |
| IADM-03 | `adm08.*`, `adm09.*` | none |
| IADM-04 | keep `adm01.manage` until dedicated family | propose `iadm04.view` / `iadm04.write` / `iadm04.manage` when US needs them |
| IADM-05 | none live | create `iadm05.view` / `iadm05.write` / `iadm05.execute` / `iadm05.manage` when built |
| IADM-06 | none dedicated | propose `iadm06.view` / `iadm06.export` when built |
| IADM-07 | policy keys as used | propose `iadm07.view` / `iadm07.write` / `iadm07.manage` when built |
| IADM-08 | audit view keys in use | keep shared audit; no second store |

**IADM-D5:** `adm03.*` / API Client (R27) stay dormant.

---

## 0.6 Service plan (extract from :8081)

| Module | Port | Gateway prefix | Extraction |
|---|---|---|---|
| `iadm-01-tenant-lifecycle-service` | 8099 | `/api/v1/iadm/01` | Move institute/college/onboard/lifecycle handlers; keep `/api/v1/admin` on 8081 until switched |
| `iadm-02-platform-config-service` | 8100 | `/api/v1/iadm/02` | Move configuration handlers + effective resolve |
| `iadm-03-identity-access-service` | 8101 | `/api/v1/iadm/03` | Move users/roles/bindings/reviews/delegations |
| `iadm-04-billing-entitlements-service` | 8102 | `/api/v1/iadm/04` | Move billing/* |
| `iadm-05-data-governance-service` | 8103 | `/api/v1/iadm/05` | Move data-governance + exports |
| `iadm-06-platform-analytics-service` | 8104 | `/api/v1/iadm/06` | Move usage + new aggregates |
| `iadm-07-security-compliance-service` | 8105 | `/api/v1/iadm/07` | New posture/findings + policies subset |
| `iadm-08-audit-ops-service` | 8106 | `/api/v1/iadm/08` | Move audit-logs + operations |

Ports **8080–8098 taken** [CONFIRMED: `GatewayConfig.java`]. Next free **8099+**.

During move: register specific `/api/v1/iadm/0X` routes **before** `/api/v1/admin` catch-all; monolith remains operational.

---

## Exit → Wave A

Step 0.1–0.6 complete. Continuing automatically to Wave A (IADM-01 + IADM-02).
