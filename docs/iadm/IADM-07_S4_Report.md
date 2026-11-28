# IADM-07 S4 close-out — Security & Compliance Governance (Proof of Fixes)

**Date:** 2026-11-28  
**Branch:** `feature/java-backend-integration` (local commit for fixes + proof; awaiting owner approval)  
**Service:** `iadm-07-security-compliance-service` · port **8105** · route **`/api/v1/iadm/07`** · schema **`sec`**  
**DEV DB:** `azulbaxszsqxymqmlwfe` only  
**Plain-language module name:** Security & Compliance Governance (IADM-07)

## What was proven

Remediation of the reconciliation gaps (cross-institute writes, exception Separation of Duties, service-only signal intake, versioning, indicators, R03 limits, reports, denial feed) with **named evidence** — not new product features.

### 1. RLS migration on DEV

| Item | Value |
|---|---|
| Forward | `database/migrations/20261127140000_iadm07_rls_write_tenant_guard.sql` |
| Rollback kept | `database/migrations/20261127140000_rollback__iadm07_rls_write_tenant_guard.sql` |
| Apply method | Supabase MCP `apply_migration` name `iadm07_rls_write_tenant_guard` (same method as prior IADM migrations) |
| Before/after + DB insert refusal | `docs/iadm/iadm-07/evidence/us032/RLS_WRITE_TENANT_GUARD.md` |

**DB-level proof (not Java only):** Institution Admin (R03) session for institute IADM04_DEMO (`d4000000-…-0004`) attempting insert into `sec.findings` / `sec.risks` / `sec.alerts` for MAC (`72ecbfe3-…-311b`) → `new row violates row-level security policy` for all three.

### 2. Fix proofs a–h (named evidence)

| # | Claim | Evidence |
|---|---|---|
| a | Institution Admin (R03) create finding/risk/alert for another institute refused in Java **and** RLS | `API_PROOF.md` `a_r03_cross_institute_*` (403 Cross-institute write refused) + `RLS_WRITE_TENANT_GUARD.md` |
| b | Super Admin (R01) deciding own exception → `IADM07_SOD_VIOLATION`; different decider succeeds; missing/inverted validity refused | `API_PROOF.md` `b_*` |
| c | R01/R02 `/intake/signals` refused; service caller `SVC-CONSUMER-01` accepted; DEMO simulator through intake path accepted; duplicate no second row; conflict audits `IADM07_SIGNAL_CONFLICT` | `API_PROOF.md` `c_*` |
| d | Change to published standard → version 2 Draft; old → Superseded | `API_PROOF.md` `d_*` |
| e | Expiring-exceptions indicator excludes already expired (`valid_to > now()`) | `PostureIndicatorEngine` + `SecurityFailureTableTest.expiringExceptionsExcludesExpiredRows` + posture overview live |
| f | R03 only Assigned→Remediation In Progress→Pending Verification; ack alerts only; never Resolved | `API_PROOF.md` `f_*` + `R03A_institute.png` |
| g | Reports: by status, severity, monthly created/resolved, freshness; no export control | `API_PROOF.md` `g_reports_breakdown` + `R01_reports.png` + headed “no export” |
| h | Denial events safe fields for R01/R02; not visible to R03 (403) | `API_PROOF.md` `h_*` + `R01_signals.png` (denial panel) |

### 3. Headed film

**25/25 PASS** — `docs/iadm/iadm-07/evidence/us032/DEMO.md`  
**API companion:** **24/24 PASS** — `docs/iadm/iadm-07/evidence/us032/API_PROOF.md`  
**Video:** `docs/iadm/iadm-07/evidence/us032/*.webm`

Personas filmed: Super Admin (**R01**), System Admin (**R02**), Institution Admin (**R03**) × two institutes (IADM04_DEMO + MAC), College Admin (**R04**), Auditor (**R28**).

Tabs opened: Signals, Controls, Exceptions, Enforcement, Reports (plus Findings/Alerts/Posture/Standards).

Five faults filmed:

| Fault | Screenshot |
|---|---|
| Audit off (mutation refused) then recovery | `R01_fault_audit_off.png`, `R01_fault_audit_recovery.png` |
| Stale register STALE / never healthy | `R01_fault_stale.png` |
| Notifications off, alerts still work | `R01_fault_notifications_off.png` |
| Invalid transition refused | `R01_fault_invalid_transition.png` |
| Self-approval refused | `R01_fault_self_approval.png` |

### 4. Tests

| Suite | Result |
|---|---|
| Maven unit suite `iadm-07-security-compliance-service` (`SecurityFailureTableTest`, `SecurityPermissionMatrixTest`, `RegisterLifecycleEngineTest`, `SecurityAuditFailClosedTest`, `SecurityScaffoldTest`) | **31/31 PASS** |
| Frontend `npx tsc --noEmit` | **0 errors** |

---

## All 32 rows — target versus ACTUAL

| Story ID | Title (plain) | Target | ACTUAL | Named evidence |
|---|---|---|---|---|
| IADM-07-US-021 | Preflight inventory | API-ready | **API-ready** | `docs/iadm/iadm-07/US021_PREFLIGHT_REPORT.md` |
| IADM-07-US-022 | Service scaffold | API-ready | **API-ready** | `SecurityScaffoldTest`; headed health `:8105` |
| IADM-07-US-023 | Permission keys | API-ready | **API-ready** | Foundation keys migration; `SecurityPermissionMatrixTest` (7); service intake refusal `c_r01_intake_refused` |
| IADM-07-US-024 | Schema + FORCE RLS | API-ready | **Demo-verified** | `RLS_WRITE_TENANT_GUARD.md` before/after + cross-tenant insert refusal |
| IADM-07-US-025 | Shared audit write | API-ready | **Demo-verified** | `SecurityAuditFailClosedTest`; headed audit-off fault; `IADM07_SIGNAL_CONFLICT` in `API_PROOF.md` |
| IADM-07-US-001 | R01 platform posture | Demo-verified | **Demo-verified** | `R01_findings.png`, `R01_fault_stale.png`, posture overview |
| IADM-07-US-002 | R02 manage ops | Demo-verified | **Demo-verified** | `R02_desk.png`, `R02_reports.png`; propose/decide paths in API proof |
| IADM-07-US-004 | R04 summary | Demo-verified | **Demo-verified** | `R04_college.png` |
| IADM-07-US-015 | Propose/approve/publish standard | Demo-verified | **Demo-verified** | `API_PROOF.md` `d_*` (v2 Draft + Superseded) |
| IADM-07-US-018 | Posture indicators by scope | Demo-verified | **Demo-verified** | `R01_fault_stale.png` (1440 / STALE); R03A/R04 screens |
| IADM-07-US-026 | Lifecycle engine | API-ready | **API-ready** | `RegisterLifecycleEngineTest` (5) + SoD/R03 unit tests |
| IADM-07-US-028 | Demo dataset | Demo-verified | **Demo-verified** | Seeded `SEC-*` rows on film screenshots |
| IADM-07-US-029 | Posture engine | API-ready | **API-ready** | `expiringExceptions` SQL + unit test; live posture |
| IADM-07-US-003 | R03 own-institute posture | Demo-verified | **Demo-verified** | `R03A_institute.png`, `R03B_institute.png`; `f_*` API |
| IADM-07-US-006 | Triage signals | Demo-verified | **Demo-verified** | `R01_signals.png` denial panel; Signals tab filmed |
| IADM-07-US-007 | Track findings | Demo-verified | **Demo-verified** | `R01_findings.png`; R03A advance; invalid transition fault |
| IADM-07-US-009 | Enforcement status | Demo-verified | **partially completed** | `R01_enforcement.png` filmed; **no live enforcing-service contract** (honest partial) |
| IADM-07-US-011 | Alerts in-app | Demo-verified | **Demo-verified** | Alerts listed; notifications-off fault; R03A ack |
| IADM-07-US-019 | Submit signal (simulator) | Demo-verified | **Demo-verified** | `c_service_intake_accepted`, `c_simulator_through_intake_path`, duplicate/conflict |
| IADM-07-US-027 | Signal intake endpoint | API-ready | **API-ready** | `requireServiceIntake` + `c_*` |
| IADM-07-US-030 | Intake simulator | Demo-verified | **Demo-verified** | Simulator through intake path (`simulator=true`) in `API_PROOF.md` |
| IADM-07-US-008 | Controls & evidence | Demo-verified | **Demo-verified** | `R01_controls.png` |
| IADM-07-US-010 | Request exception | Demo-verified | **Demo-verified** | Mandatory validity `b_*`; Exceptions tab `R01_exceptions.png` |
| IADM-07-US-016 | Decide exception | Demo-verified | **Demo-verified** | SoD + different decider `b_*`; self-approval fault screenshot |
| IADM-07-US-017 | View control catalogue | Demo-verified | **Demo-verified** | `R01_controls.png` |
| IADM-07-US-020 | On-screen reports | Demo-verified | **Demo-verified** | `R01_reports.png`, `g_reports_breakdown`; no export |
| IADM-07-US-005 | R28 read-only | Demo-verified | **Demo-verified** | `R28_audit.png` |
| IADM-07-US-012 | Trace via shared audit | Demo-verified | **Demo-verified** | `R28_audit.png`; conflict audit row; audit-off fault |
| IADM-07-US-013 | Ownership boundaries | Demo-verified | **API-ready** | Schema inventory in preflight/S0; registers-only `sec.*` |
| IADM-07-US-014 | Isolation & role boundaries | Demo-verified | **Demo-verified** | R03A/B + R04 + R28 screens; Java + RLS cross-tenant proofs |
| IADM-07-US-031 | Failure suite | API-ready | **API-ready** | **31/31** unit + live API/RLS proofs |
| IADM-07-US-032 | Evidence pack | Demo-verified | **Demo-verified** | `DEMO.md` **25/25**; 5/5 faults; this report |

---

## STOP

Awaiting owner: `APPROVED IADM-07` or `APPROVED IADM-07 COMMIT`.  
**No push** unless instructed. Local commit only for fixes + proof.
