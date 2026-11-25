# IADM-05 S4 — Close-out report (US-010…012, US-031, US-032)

**Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration` (local only — not pushed)  
**DEV database:** Supabase project `azulbaxszsqxymqmlwfe`  
**Service:** IADM-05 Data Governance & Privacy — port **8103**, path `/api/v1/iadm/05`

Plain meanings used below: **API-ready** = Java + gateway + tests green without a headed film for that story; **Screen-ready** = UI/BFF wired; **Demo-verified** = headed persona film or live API smoke proved the acceptance path.

---

## 1. Thirty-two backlog rows — target vs actual

| Story ID (plain) | Story name | Target done level | ACTUAL done level | Evidence location |
|---|---|---|---|---|
| IADM-05-US-021 | Preflight inventory and reconciliation | API-ready | **Demo-verified** (read-only report) | `docs/iadm/iadm-05/US021_PREFLIGHT_REPORT.md` |
| IADM-05-US-022 | IADM-05 service scaffold | API-ready | **Demo-verified** | Health :8103; commit `d3c2e74` |
| IADM-05-US-023 | Permission-key catalogue and role mapping | API-ready | **Demo-verified** | Migration `iadm05_foundation_keys`; `GovernancePermissionMatrixTest` |
| IADM-05-US-024 | Schema, tenant scoping and row-level security (RLS = Postgres row filters; FORCE RLS = always on) | API-ready | **Demo-verified** | Migration `iadm05_gov_schema`; R03 isolation smoke |
| IADM-05-US-025 | Shared audit integration | API-ready | **Demo-verified** | `DurableAuditWriter` → `audit.log_event`; `GovernanceAuditFailClosedTest` |
| IADM-05-US-001 | View the classification catalogue | Demo-verified | **Demo-verified** | Headed R01/R02 desk screenshots `docs/iadm/iadm-05/evidence/us032/` |
| IADM-05-US-002 | Propose a classification revision | Demo-verified | **Demo-verified** | Headed R02 propose + submit; `R02_propose.png` |
| IADM-05-US-003 | Approve or reject a policy revision | Demo-verified | **Demo-verified** | Headed R01 approve (SoD = separation of duties); R02 approve 403 FORBIDDEN |
| IADM-05-US-004 | Define a retention rule | Demo-verified | **Demo-verified** | Desk retention form + seeded DEMO-RET rows; duration TBC |
| IADM-05-US-013 | Inspect policy history | Demo-verified | **Demo-verified** | Desk History control; API `/policies/{id}/history` |
| IADM-05-US-018 | Publish approved classification/retention | Demo-verified | **Demo-verified** | Headed R01 publish; `R01_publish.png` |
| IADM-05-US-026 | Policy lifecycle engine | API-ready | **Demo-verified** (exercised live) | `PolicyLifecycleService`; headed propose→submit→approve→publish |
| IADM-05-US-030 | Demo dataset (DEMO ONLY) | Demo-verified | **Demo-verified** | Seeds S1–S3 on DEV; queues non-empty in film |
| IADM-05-US-005 | Submit a governed export request | Demo-verified | **Demo-verified** | Headed R02 export submit; `R02_export_submit.png` |
| IADM-05-US-006 | Approve or reject an export request | Demo-verified | **Demo-verified** | Headed R01 request approve; `R01_req_approve.png` |
| IADM-05-US-014 | Identify failed/partial domain execution | Demo-verified | **Demo-verified** | Unresolved markers + re-dispatch; prior film + seeds |
| IADM-05-US-017 | Resolve policy version / report outcome | API-ready | **API-ready** | Domain contract + DEMO simulator caller (no real domain wiring — G-08) |
| IADM-05-US-027 | Governance request state machine | API-ready | **Demo-verified** (exercised live) | `GovernanceRequestService`; seeded statuses |
| IADM-05-US-028 | Domain contract endpoints | API-ready | **API-ready** | Contract endpoints present; real domain owners TBC (G-08) |
| IADM-05-US-029 | Domain execution simulator (DEMO ONLY) | Demo-verified | **Demo-verified** | `DomainExecutionSimulator`; dispatch in film |
| IADM-05-US-007 | Submit erasure governance request | Demo-verified | **Demo-verified** | Desk + S3 seeds; queue visible to R01 |
| IADM-05-US-008 | See per-domain erasure outcomes | Demo-verified | **Demo-verified** | Domain outcomes on request rows; partial seed |
| IADM-05-US-009 | Govern a data-sharing agreement | Demo-verified | **Demo-verified** | Sharing submit on desk + seeded agreements |
| IADM-05-US-015 | Publish privacy/consent (gated) | Demo-verified | **Demo-verified** | Headed R01 privacy publish → 423 `IADM05_LEGAL_GATE` |
| IADM-05-US-016 | Suspend/revoke/expire agreement | Demo-verified | **Demo-verified** | API lifecycle endpoints + seed expiry path |
| IADM-05-US-019 | View retention/consent/sharing registers | Demo-verified | **Demo-verified** | Platform desk + R03 adherence published set |
| IADM-05-US-020 | Close an erasure request | Demo-verified | **Demo-verified** | Headed close blocked while unresolved (technical rule G-05) |
| IADM-05-US-010 | R03 own-institute adherence (view-only) | Demo-verified | **Demo-verified** | Headed R03 + API smoke isolation vs second institute |
| IADM-05-US-011 | R04 college policy view (no edit) | Demo-verified | **Demo-verified** | Headed R04; BFF POST 403; `canEditPolicy=false` |
| IADM-05-US-012 | R28 auditor evidence (read-only) | Demo-verified | **Demo-verified** | Headed R28; gateway write 403; BFF approve 403 |
| IADM-05-US-031 | Failure-handling and isolation suite | API-ready | **Demo-verified** (suite + live isolation) | `GovernanceFailureTableTest` + `iadm05_s4_api_smoke.mjs` |
| IADM-05-US-032 | Evidence pack and close-out | Demo-verified | **Demo-verified** | This report + `docs/iadm/iadm-05/evidence/us032/` (21/21 headed PASS) |

**Honest partials:** US-017 and US-028 stay **API-ready** (not Demo-verified against real domain microservices) because domain execution remains the DEMO ONLY simulator by design (gap G-08).

---

## 2. Blueprint §15.2 checklist (ticked with evidence)

| # | Checklist item | Tick | Evidence |
|---|---|---|---|
| 1 | Super Admin (R01) platform scope ≠ Institution Admin (R03) institute scope | ✅ | Institute-scoped DEMO-RET-INST-A/B; R03 adherence never shows them (`iadm05_s4_api_smoke.mjs`, headed R03) |
| 2 | System Admin (R02) write ≠ policy approval | ✅ | Headed R02 propose/submit OK; approve returns **403 FORBIDDEN** (G-15 harden); BFF also blocks decide/publish |
| 3 | R03 cannot view/govern another institute | ✅ | Smoke + headed: no DEMO-RET-INST-A/B leak |
| 4 | R03 adherence view-only (gap G-16) | ✅ | Institution BFF POST → 403; desk has no write controls |
| 5 | College Admin (R04) cannot override IADM policy | ✅ | `canEditPolicy=false`; college BFF POST → 403 |
| 6 | R04 entry points informational only (gap G-02) | ✅ | College desk entry list `triggerable=false` |
| 7 | Auditor (R28) read-only | ✅ | Auditor desk; write/approve 403 (gateway + BFF) |
| 8 | No R05–R39 inferred IADM-05 access | ✅ | `GovernanceFailureTableTest.r05ThroughR39SampleDenied`; faculty (R10) smoke 403 |
| 9 | Cross-tenant sharing default deny | ✅ | Wildcard provider/consumer rejected; deny seed |
| 10 | View ≠ export / execute / delete / approve | ✅ | Matrix tests + role keys |
| 11 | Export approval ≠ execution | ✅ | Decide then DEMO dispatch/simulator |
| 12 | Erasure by domain owners, not IADM-05 | ✅ | Simulator / contract only; no domain DB access |
| 13 | Partial ≠ global success | ✅ | Aggregate status + unresolved UI + closure block |
| 14 | CADM-08 remains import owner | ✅ | Not built / not claimed |
| 15 | IADM-07 remains security owner | ✅ | Not built / not claimed |
| 16 | Shared audit used; no second store | ✅ | `DurableAuditWriter` → `audit.log_event` |
| 17 | Shared identity / authz / logging reused | ✅ | Gateway HMAC + `iam.has_permission` + CampXLogger |
| 18 | Missing tenant / policy fail closed | ✅ | Failure-table tests + `IADM05_NO_APPLICABLE_POLICY` |
| 19 | No sensitive payloads in logs | ✅ | `logLeakCheckOnErrorMessages`; controller avoids body dumps |
| 20 | No Kafka | ✅ | None introduced |
| 21 | Schema / API / keys treated as build proposal | ✅ | S0–S1 reports; gaps G-01/G-09 |
| 22 | Queues seeded (not empty mocks) | ✅ | Headed queue rows ≥ 8–10 |
| 23 | Retention / consent / sharing are IADM-05 records | ✅ | Schema `gov`; not IADM-02 `cfg.*` |

---

## 3. Open gaps G-01 to G-18 — what this build assumed

| Gap | Plain meaning | Assumed in this build |
|---|---|---|
| G-01 | Permission key naming | Catalogue `iadm05.*` (no wildcards) |
| G-02 | College Admin trigger capability | Informational entry points only; no trigger/edit/override |
| G-03 | Children’s-data legal check | Privacy publish stays hard-blocked |
| G-04 | Same legal gate / D-RBAC-M17 | API returns `IADM05_LEGAL_GATE` (HTTP 423); no open path |
| G-05 | Erasure legal closure criteria | **Technical rule only** — all required domains COMPLETED or REJECTED |
| G-06 | Classification taxonomy legality | Labels **DEMO ONLY / NOT LEGAL CLASSIFICATIONS** |
| G-07 | Export destination safeguards | Destination notes stored as TBC |
| G-08 | Real domain-service contracts | DEMO ONLY simulator; contracts TBC per domain |
| G-09 | Physical schema ownership | Schema `gov` as build proposal on DEV |
| G-10 | Applicable version resolution | Exactly one published version, else fail closed / conflict |
| G-11 | Audit field mapping | Reuse shared `audit.log_event` |
| G-12 | Request state machine shape | As implemented in `GovernanceRequestService` |
| G-13 | Legal holds | TBC text fields only |
| G-14 | Port / route convention | Port **8103**, prefix `/api/v1/iadm/05` |
| G-15 | Who may approve | **Super Admin (R01) only**; author ≠ approver; System Admin (R02) denied even if listed in `plat.platform_admins` |
| G-16 | Institution Admin writes | **View-only** adherence |
| G-17 | Data-subject access / correction / grievance | **Out of scope** for IADM-05 this build |
| G-18 | Invoice retention / college archive D1 | Placeholders only; owned elsewhere |

Build defaults recorded in `docs/iadm/iadm-05/BUILD_DEFAULTS.md`.

---

## 4. Verification evidence (S4)

| Check | Result |
|---|---|
| Stack UI :3000 + gateway :8080 + IADM-05 :8103 | Up for film |
| Maven unit tests (`GovernancePermissionMatrixTest`, `GovernanceAuditFailClosedTest`, `GovernanceFailureTableTest`) | PASS |
| Frontend TypeScript check (`npx tsc --noEmit`) | 0 errors |
| API smoke `node scripts/demo/iadm05_s4_api_smoke.mjs` | 10/10 PASS |
| Headed film `node scripts/demo/iadm05_headed.mjs` | **21/21 PASS** |
| Evidence dir | `docs/iadm/iadm-05/evidence/us032/` (DEMO.md, screenshots, `.webm`) |

### Headed personas (plain roles)

| Code | Plain role | Desk | Work shown |
|---|---|---|---|
| R01 | Super Admin | `/platform/governance` | Approve R02 proposal, publish, approve export, attempt erasure close |
| R02 | System Admin | `/platform/governance` | Propose + submit classification, submit export; approve denied |
| R03 | Institution Admin | `/institution/governance` | View adherence; write 403; isolated from second institute |
| R04 | College Admin | `/college/governance` | View policy + entry points; write 403 |
| R28 | Auditor | `/platform/governance/audit` | View evidence; write/approve 403 |

---

## 5. Parked / out of build

- Real domain microservice wiring (G-08)
- Legal durations, taxonomy, DPDP interpretation
- Opening the privacy legal gate (owner + counsel separate change)
- Push to origin (owner must authorize)

---

## 6. Owner decision

**`APPROVED IADM-05 S4 COMMIT`** (2026-11-25) — feature commit `6d31085`.  
**Do not push** unless the owner explicitly says so.
