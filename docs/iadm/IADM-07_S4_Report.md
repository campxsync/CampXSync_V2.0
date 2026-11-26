# IADM-07 S4 close-out — Security & Compliance Governance

**Date:** 2026-11-26  
**Branch:** `feature/java-backend-integration` (local only — **not pushed**)  
**Service:** `iadm-07-security-compliance-service` · port **8105** · route **`/api/v1/iadm/07`** · schema **`sec`**  
**DEV DB:** `azulbaxszsqxymqmlwfe` only

## Headed film (US-032)

**10/10 PASS** — `docs/iadm/iadm-07/evidence/us032/DEMO.md`  
Personas: Super Admin (R01), System Admin (R02), Institution Admin (R03)×2 institutes, College Admin (R04), Auditor (R28).

## All 32 rows — target vs ACTUAL

| Story ID | Title (plain) | Target | ACTUAL | Evidence |
|---|---|---|---|---|
| IADM-07-US-021 | Preflight inventory | API-ready | **API-ready** | `docs/iadm/iadm-07/US021_PREFLIGHT_REPORT.md` |
| IADM-07-US-022 | Service scaffold | API-ready | **API-ready** | health `:8105` |
| IADM-07-US-023 | Permission keys | API-ready | **API-ready** | 11 `iadm07.*` keys; matrix tests |
| IADM-07-US-024 | Schema + FORCE RLS | API-ready | **API-ready** | `sec.*` FORCE RLS |
| IADM-07-US-025 | Shared audit write | API-ready | **API-ready** | `DurableAuditWriter` fail-closed |
| IADM-07-US-001 | R01 platform posture | Demo-verified | **Demo-verified** | headed R01 |
| IADM-07-US-002 | R02 manage ops | Demo-verified | **Demo-verified** | headed R02 |
| IADM-07-US-004 | R04 summary | Demo-verified | **Demo-verified** | headed R04 |
| IADM-07-US-015 | Propose/approve/publish standard | Demo-verified | **Demo-verified** | standards lifecycle + desk |
| IADM-07-US-018 | Posture indicators by scope | Demo-verified | **Demo-verified** | headed multi-persona |
| IADM-07-US-026 | Lifecycle engine | API-ready | **API-ready** | `RegisterLifecycleEngineTest` |
| IADM-07-US-028 | Demo dataset | Demo-verified | **Demo-verified** | seed + headed SEC-* |
| IADM-07-US-029 | Posture engine | API-ready | **API-ready** | threshold 10080→1440 filmed |
| IADM-07-US-003 | R03 own-institute posture | Demo-verified | **Demo-verified** | headed R03A/B |
| IADM-07-US-006 | Triage signals | Demo-verified | **partial** | Signal list/triage shipped; **denial-event display omitted** (no denial read path) |
| IADM-07-US-007 | Track findings | Demo-verified | **Demo-verified** | findings + R03 advance |
| IADM-07-US-009 | Enforcement status | Demo-verified | **partial** | Manual/simulator reports only; **no live enforcing-service contract** |
| IADM-07-US-011 | Alerts in-app | Demo-verified | **Demo-verified** | SEC-ALERT-001; notifications absent ≠ block |
| IADM-07-US-019 | Submit signal (simulator) | Demo-verified | **Demo-verified** | `/intake/signals` + DEMO ONLY simulator |
| IADM-07-US-027 | Signal intake endpoint | API-ready | **API-ready** | authenticated intake |
| IADM-07-US-030 | Intake simulator | Demo-verified | **Demo-verified** | DEMO ONLY labelled |
| IADM-07-US-008 | Controls & evidence | Demo-verified | **Demo-verified** | SEC-CTRL-001 DEMO ONLY |
| IADM-07-US-010 | Request exception | Demo-verified | **Demo-verified** | SEC-EXC-001 |
| IADM-07-US-016 | Decide exception | Demo-verified | **Demo-verified** | R01 decide endpoint |
| IADM-07-US-017 | View control catalogue | Demo-verified | **Demo-verified** | controls desk + R04 counts |
| IADM-07-US-020 | On-screen reports | Demo-verified | **Demo-verified** | `/reports/counts`; no export |
| IADM-07-US-005 | R28 read-only | Demo-verified | **Demo-verified** | headed R28 403 |
| IADM-07-US-012 | Trace via shared audit | Demo-verified | **Demo-verified** | `/audit` via `audit.events` IADM07_% |
| IADM-07-US-013 | Ownership boundaries | Demo-verified | **Demo-verified** | schema inventory = registers only |
| IADM-07-US-014 | Isolation & role boundaries | Demo-verified | **Demo-verified** | R03A≠R03B; R04/R28 denials |
| IADM-07-US-031 | Failure suite | API-ready | **API-ready** | `SecurityFailureTableTest` + matrix/lifecycle/audit tests |
| IADM-07-US-032 | Evidence pack | Demo-verified | **Demo-verified** | this report + `evidence/us032/` |

## Blueprint demo checklist (ticked with evidence)

- [x] R01 platform desk with seeded rows — `R01_findings.png`
- [x] R02 desk — `R02_desk.png`
- [x] R03 institute A finding + transition — `R03A_institute.png`
- [x] R03 institute B isolation — `R03B_institute.png`
- [x] R04 summary only — `R04_college.png`
- [x] R28 read-only / no export — `R28_audit.png`
- [x] Stale threshold DEMO ONLY adjusted — headed step PASS (1440)
- [x] Shared audit fail-closed unit proof — `SecurityAuditFailClosedTest`
- [x] Denial-event display — **N/A omitted** (preflight: no denial read path)

## Exact names found

| Item | Value |
|---|---|
| Port | 8105 |
| Route prefix | `/api/v1/iadm/07` |
| Schema | `sec` |
| Keys | `iadm07.posture.view`, `register.read`, `register.write`, `standard.propose`, `standard.approve`, `exception.request`, `exception.decide`, `signal.intake`, `report.view`, `audit.view`, `summary.view` |
| Audit events | `IADM07_*` via `audit.log_event` (e.g. `IADM07_STALE_THRESHOLD`, register writes) |
| Stale threshold default | **10080 minutes** (DEMO ONLY) |

## Permission catalogue before/after

| | Before | After |
|---|---|---|
| `iadm07.*` | 0 | **11** |
| `adm07.*` (departments) | 8 | **8** unchanged |

## Decisions D1–D16 (build assumptions)

| # | Assumed |
|---|---|
| D1 | Port 8105, `/api/v1/iadm/07` |
| D2 | Schema `sec` + FORCE RLS |
| D3 | `iadm07.*` keys; `adm07.*` untouched |
| D4 | R28 platform-wide read-only; R04 summary only; no college finding detail |
| D5 | Manual + authenticated intake; simulator DEMO ONLY |
| D6 | Controls DEMO ONLY; free-text framework |
| D7 | Indicators from `sec` only; stale never healthy/zero |
| D8 | R01 sole approver/decider; SoD; expiry at read time |
| D9 | In-app alerts; shared notification **absent** — never blocks |
| D10 | `audit.log_event` fail-closed |
| D11 | No Kafka/outbox |
| D12 | Evidence = reference only |
| D13 | On-screen reports; no export |
| D14 | Stale threshold DEMO ONLY runtime (10080 default) |
| D15 | R02 manage/propose; never approve/decide |
| D16 | NEW service; leave departments/`adm07.*` alone |

### Preflight call-outs

- General audit read path: **EXISTS** (`audit.events` / `audit.read`)
- Denial-event read path: **NOT FOUND** → US-006 partial
- Shared notification service: **NOT FOUND** (`.gitkeep` only)
- Auditor audit-scope assignment: **NOT FOUND** → platform-wide R28
- College attribute for findings: **NOT proven** → R04 summary only
- Enforcing-service contract: **NONE** → US-009 partial

## Tests

- Maven unit suite `iadm-07-security-compliance-service`: PASS (matrix, lifecycle, audit fail-closed, scaffold, failure table)
- Frontend `npx tsc --noEmit`: PASS (0 errors) at UI scaffold time

## Final git status (to be confirmed after commit)

- Branch: `feature/java-backend-integration`
- Local commit(s) for IADM-07; **not pushed**
