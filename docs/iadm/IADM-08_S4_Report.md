# IADM-08 S4 / Module close-out report

**Module:** Audit, Health & Operations Oversight (IADM-08)  
**Date:** 2026-11-26  
**Branch:** `feature/java-backend-integration`  
**Mode:** IMPLEMENT, DEV only (`azulbaxszsqxymqmlwfe`)

## Constants (verified)

| Item | Value | Meaning |
|---|---|---|
| Port | **8106** | Service listen port |
| Route | **`/api/v1/iadm/08`** | Gateway + service prefix |
| Keys | `iadm08.audit.platform.view`, `iadm08.audit.institute.view`, `iadm08.health.view`, `iadm08.alerts.view`, `iadm08.manage` (reserved) | Permission catalogue |
| Events | `IADM08_AUDIT_SEARCH`, `IADM08_AUDIT_DETAIL`, `IADM08_AUDIT_TIMELINE`, `IADM08_HEALTH_OVERVIEW`, `IADM08_HEALTH_DETAIL`, `IADM08_ALERTS_READ` | Read-recording actions |
| Max window | **31 days** (DEMO ONLY default, runtime configurable) | Over-max rejected |
| Page size | **50** (DEMO ONLY default) | Page size |
| Persistence | **none** | No IADM-08 schema/table/audit mirror |

## Permission catalogue before / after

| When | `iadm08.*` keys on DEV |
|---|---|
| Before | none |
| After | 5 keys listed above (manage reserved, not granted) |
| Legacy `adm08.*` | Untouched (still gate iam profiles/groups) |

Migration: `database/migrations/20261126180000_iadm08_foundation_keys.sql` + rollback twin. Applied on DEV via Supabase MCP.

## Denial events & IADM-07 alerts

| Capability | Available? | Notes |
|---|---|---|
| Denial events in shared audit | **Yes** | e.g. `IADM02_*_DENIED`; searchable via denial filter; never created by IADM-08 |
| IADM-07 alerts read | **Yes** | `GET /api/v1/iadm/07/alerts` with trusted scope |

## Decisions D1–D18 (build assumptions)

| ID | Assumed |
|---|---|
| D1 | No export feature/endpoint/key; attempts refused |
| D2 | `audit.events` safe-field query + trusted institute filter |
| D3 | Every listed read → `audit.log_event`; refuse if fail |
| D4 | 31d / 50 rows; reject over-max; IADM-05 OPERATIONS gate |
| D5 | Minimal labelled in-service health inventory (aligned with gateway routes) |
| D6 | Healthy/Degraded/Unavailable/Unknown; Stale only with source timestamp |
| D7 | No acknowledge; IADM-07 alerts via existing GET |
| D8 | No escalation; notifications non-blocking (service absent) |
| D9 | SLA = “not available” only |
| D10 | IADM-07 alerts included |
| D11 | CADM-07 college audit; R04 denied |
| D12 | Port 8106 / `/api/v1/iadm/08` |
| D13 | `iadm08.*` / `IADM08_*` |
| D14 | No IADM-08 tables/schema |
| D15 | DEMO-OPERATIONS-HANDLING RETENTION+OPERATIONS published (R02 author ≠ R01 approver) |
| D16 | Notifications optional |
| D17 | Reused demo personas + existing audit rows |
| D18 | No Intelligence consumer |

## Blueprint demo checklist

| Check | Evidence |
|---|---|
| R01 Operations Overview health | `docs/iadm/iadm-08/evidence/us023/R01_overview.png` |
| R01/R02 Audit Investigation | `R01_audit.png` (50 rows) |
| R03 own-institute only + health denied | `R03_institute.png` |
| R04 denied | `R04_deny.png` |
| R28 read-only + export denied | `R28_desk.png` + DEMO.md |
| SLA not available | DEMO.md |
| No IADM-08 schema | TECHNICAL.md + SQL verify |
| Export/ack absent | unit `OpsDeferredFeaturesTest` + headed |

**Headed film:** `docs/iadm/iadm-08/evidence/us023/` — **10/10 PASS** (`DEMO.md`)

## Unit / typecheck

| Suite | Result |
|---|---|
| `OpsPermissionMatrixTest` + `OpsScaffoldTest` + `OpsAuditFailClosedTest` + `OpsDeferredFeaturesTest` | **13/13 PASS** |
| Frontend `npx tsc --noEmit` | **0 errors** |

## Confirmation

- No IADM-08 table, schema, or audit mirror exists.
- IADM-01/02/04/05/06/07 code untouched (gateway route + stack scripts only additive).
- Local commits only; **not pushed**.

## 24-row status table

See final section in this report and `docs/user-stories/IADM-08-user-stories.csv` (working backlog reconstructed because owner CSV path was initially missing — noted in US-001 preflight).

| Story ID | Target | ACTUAL | Evidence |
|---|---|---|---|
| US-001 | API-ready | **completed** | `docs/iadm/iadm-08/US001_PREFLIGHT_REPORT.md` |
| US-016 | API-ready | **completed** | service :8106 health UP |
| US-017 | API-ready | **completed** | `OpsPermissionMatrix` + migration |
| US-018 | API-ready | **completed** | migration before/after |
| US-019 | API-ready | **completed** | `DurableAuditWriter` + fail-closed test |
| US-020 | API-ready | **completed** | limits 31/50; OPERATIONS policy |
| US-024 | Screen-ready | **completed** | reused audit rows (50 on desk) |
| US-002 | Demo-verified | **completed** | headed R01 audit 50 rows |
| US-003 | Demo-verified | **completed** | detail endpoint + UI click path |
| US-004 | Demo-verified | **completed** | timeline API |
| US-005 | Demo-verified | **completed** | headed R03 |
| US-006 | Demo-verified | **completed** | headed R28 |
| US-021 | API-ready | **completed** | `HealthTargetInventory` |
| US-007 | Demo-verified | **completed** | headed health overview |
| US-008 | Demo-verified | **completed** | health detail API |
| US-009 | Demo-verified | **completed** | alerts API + IADM-07 include |
| US-012 | Demo-verified | **completed** | `/iadm07/evidence` |
| US-011 | Demo-verified | **completed** | SLA not available |
| US-010 | Demo-verified | **completed** | ack absence headed+API |
| US-015 | Demo-verified | **completed** | export absence headed+API |
| US-022 | Demo-verified | **completed** | deferred tests + denial filter |
| US-013 | Demo-verified | **completed** | R04/R03/R28 headed |
| US-014 | Demo-verified | **completed** | outage flags + Unknown/Unavailable mapping |
| US-023 | Demo-verified | **completed** | this report + 10/10 headed |

**CSV note:** Owner file was absent at kickoff; working CSV written from kickoff+blueprint. Owner may replace authoritative CSV later.
