# IADM-08 S4 / Module close-out report

**Module:** Audit, Health & Operations Oversight (IADM-08)  
**Date:** 2026-11-27 (Part B verification)  
**Branch:** `feature/java-backend-integration`  
**Mode:** IMPLEMENT, DEV only (`azulbaxszsqxymqmlwfe`)

## Constants (verified)

| Item | Value | Meaning |
|---|---|---|
| Port | **8106** | Service listen port |
| Route | **`/api/v1/iadm/08`** | Gateway + service prefix |
| Keys | `iadm08.audit.platform.view` (platform-wide audit view), `iadm08.audit.institute.view` (own-institute audit view), `iadm08.health.view` (service health view), `iadm08.alerts.view` (read-only alerts view), `iadm08.manage` (reserved, not granted) | Permission catalogue |
| Events | `IADM08_AUDIT_SEARCH` (audit search read), `IADM08_AUDIT_DETAIL` (event detail read), `IADM08_AUDIT_TIMELINE` (actor timeline read), `IADM08_HEALTH_OVERVIEW` (health overview read), `IADM08_HEALTH_DETAIL` (health target detail read), `IADM08_ALERTS_READ` (alerts read) | Read-recording actions |
| Max window | **31 days** (DEMO ONLY default, runtime configurable) | Over-max rejected (`400 IADM08_RANGE_TOO_LARGE`) |
| Page size | **50** (DEMO ONLY default) | Page size |
| Persistence | **none** | No IADM-08 schema/table/audit mirror |

## Permission catalogue before / after

| When | `iadm08.*` keys on DEV |
|---|---|
| Before | none |
| After | 5 keys listed above (`iadm08.manage` reserved, not granted) |
| Legacy `adm08.*` | Untouched (still gate `iam` profiles/groups) |

Migration: `database/migrations/20261126180000_iadm08_foundation_keys.sql` + rollback twin. Applied on DEV via Supabase MCP.

## Denial events & IADM-07 alerts

| Capability | Available? | Notes |
|---|---|---|
| Denial events in shared audit | **Yes** | 4 existing `IADM02_*_DENIED` rows in `audit.events`; searchable via denial filter; never created by IADM-08 |
| IADM-07 alerts read | **Yes** | `GET /api/v1/iadm/07/alerts` with trusted scope (`OpsAlertsScope`) |

## Decisions D1–D18 (build assumptions)

| ID | Plain-language summary |
|---|---|
| D1 (no export) | No export feature, endpoint, or key; attempts refused (`404 IADM08_NOT_AVAILABLE`) |
| D2 (shared audit query) | `audit.events` safe-field query + trusted institute filter |
| D3 (fail-closed read recording) | Every listed read → `audit.log_event`; refuse (`503 IADM08_AUDIT_UNAVAILABLE`) if recording fails |
| D4 (limits & policy gate) | 31d / 50 rows; reject over-max; IADM-05 `OPERATIONS` gate |
| D5 (health inventory) | Minimal labelled in-service health inventory (gateway + IADM-01..08; client addresses rejected) |
| D6 (health status mapping) | `Healthy` / `Degraded` / `Unavailable` / `Unknown`; `Stale` only with source timestamp |
| D7 (no acknowledge) | No acknowledge control/endpoint; IADM-07 alerts via existing GET |
| D8 (no escalation) | No escalation; notifications non-blocking (service absent) |
| D9 (SLA placeholder) | SLA = “not available” only |
| D10 (IADM-07 alerts) | IADM-07 alerts included read-only |
| D11 (R03/R04 scope) | R03 (Institution Admin) own-institute only; R04 (College Admin) denied |
| D12 (port & route) | Port 8106 / `/api/v1/iadm/08` |
| D13 (keys & actions) | `iadm08.*` / `IADM08_*` |
| D14 (no schema) | No IADM-08 tables/schema |
| D15 (IADM-05 policy) | `DEMO-OPERATIONS-HANDLING` (`RETENTION`+`OPERATIONS`) published (R02 System Admin author ≠ R01 Super Admin approver) |
| D16 (notifications) | Notifications optional |
| D17 (reuse personas/audit) | Reused demo personas + existing audit rows; zero fabricated inserts |
| D18 (no Intelligence) | No Intelligence consumer |

## Part B Proof Checklist (headed + API)

| Check | Evidence |
|---|---|
| 6 reads recorded (`IADM08_*`) + `auditDisabled=true` → `503 IADM08_AUDIT_UNAVAILABLE` + recovery | `docs/iadm/iadm-08/evidence/us023/RESULTS.json` (`bd6e1766-97bd-4397-8349-b92808ea6659`, `e85101aa-c87a-4de2-a260-ecc26dfbab9a`, `917082a8-d9e4-4bc9-a81c-4ad07abd4e22`, `69ab2e87-feb4-4faf-907b-b56563e11cbf`, `fe2cb8fa-53bc-4fa5-8c86-67fd658b8780`, `bc978e59-e758-44f1-89bf-157a0a2571b4`) |
| R01 (Super Admin) Operations Overview health & Audit Investigation | `docs/iadm/iadm-08/evidence/us023/R01_overview.png`, `R01_audit.png` (50 rows) |
| R02 (System Admin) Platform Operations desk | `docs/iadm/iadm-08/evidence/us023/R02_desk.png` |
| R03 (Institution Admin) own-institute only, cross-institute `403 IADM08_SCOPE_DENIED`, unattributed `fa5c6e81-fde9-4455-89ae-7ae6a2b361a2` hidden (`404`), health denied | `docs/iadm/iadm-08/evidence/us023/R03_institute.png` + `RESULTS.json` |
| R04 (College Admin) denied platform desk | `docs/iadm/iadm-08/evidence/us023/R04_deny.png` |
| R28 (Auditor) read-only health/audit/alerts, search recorded (`e465b76d-0a63-46be-9480-c9702f3ae072`), export/ack refused (`404 IADM08_NOT_AVAILABLE`) | `docs/iadm/iadm-08/evidence/us023/R28_desk.png`, `R28_audit_detail.png`, `R28_alerts.png`, `R28_export_denied.png` |
| Stopped DEV service (`iadm-06`) → `Unavailable`; silent/delayed target → `Unknown`; recovery → `Healthy` | `docs/iadm/iadm-08/evidence/us023/failure_unavailable.png`, `failure_unknown.png`, `failure_recovered.png` |
| Denial-only audit search (`IADM02_*_DENIED`, 4 rows, none created) | `docs/iadm/iadm-08/evidence/us023/failure_denial_only.png` |
| SLA not available | `docs/iadm/iadm-08/evidence/us023/DEMO.md` |

**Headed film:** `docs/iadm/iadm-08/evidence/us023/DEMO.md` — **25/25 PASS**

## Unit / typecheck

| Suite | Result |
|---|---|
| `OpsAuditFailClosedTest` (1) + `OpsDeferredFeaturesTest` (3) + `OpsPartAContractTest` (7) + `OpsPermissionMatrixTest` (5) + `OpsScaffoldTest` (4) | **20/20 PASS** |
| Frontend `npx tsc --noEmit` | **0 errors** |

## Confirmation

- No IADM-08 table, schema, or audit mirror exists.
- IADM-01/02/04/05/06/07 code untouched.
- Local commits only; **not pushed**.

## 24-row status table (Target vs ACTUAL done level + named evidence)

| Story ID | Plain-language title | Target | ACTUAL done level | Status | Named evidence (file or audit row) |
|---|---|---|---|---|---|
| `IADM-08-US-001` (Preflight inventory) | Preflight inventory and reconciliation | API-ready | **API-ready** | completed | `docs/iadm/iadm-08/US001_PREFLIGHT_REPORT.md` |
| `IADM-08-US-016` (Service scaffold) | IADM-08 service scaffold on port 8106 | API-ready | **API-ready** | completed | `OpsScaffoldTest` (4/4 PASS) + `docs/iadm/iadm-08/evidence/us023/R01_overview.png` |
| `IADM-08-US-017` (Permission keys) | Permission keys and role mapping (`R01`/`R02`/`R03`/`R28`, `R04` denied) | API-ready | **API-ready** | completed | `OpsPermissionMatrixTest` (5/5 PASS) + `docs/iadm/iadm-08/evidence/us023/DEMO.md` |
| `IADM-08-US-018` (DEV migration) | Additive permission-key migration on DEV | API-ready | **API-ready** | completed | `database/migrations/20261126180000_iadm08_foundation_keys.sql` + `docs/iadm/iadm-08/US001_PREFLIGHT_REPORT.md` |
| `IADM-08-US-019` (Read recording) | Shared audit read & fail-closed read recording (`IADM08_*`) | API-ready | **Demo-verified** | completed | `OpsAuditFailClosedTest` (1/1 PASS) + audit rows `bd6e1766-97bd-4397-8349-b92808ea6659`, `e85101aa-c87a-4de2-a260-ecc26dfbab9a`, `917082a8-d9e4-4bc9-a81c-4ad07abd4e22`, `69ab2e87-feb4-4faf-907b-b56563e11cbf`, `fe2cb8fa-53bc-4fa5-8c86-67fd658b8780`, `bc978e59-e758-44f1-89bf-157a0a2571b4` + `docs/iadm/iadm-08/evidence/us023/RESULTS.json` (`503 IADM08_AUDIT_UNAVAILABLE`) |
| `IADM-08-US-020` (Limits & policy gate) | Query limits (31d/50), safe fields, IADM-05 `OPERATIONS` policy gate | API-ready | **API-ready** | completed | `OpsPartAContractTest` (7/7 PASS) + `docs/iadm/iadm-08/evidence/us023/R01_overview.png` |
| `IADM-08-US-024` (Reuse audit rows) | Demo readiness — reuse existing audit entries | Screen-ready | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R01_audit.png` (50 rows) + `docs/iadm/iadm-08/evidence/us023/R03_institute.png` (50 rows) |
| `IADM-08-US-002` (Platform audit search) | Search platform audit records (`R01` Super Admin / `R02` System Admin) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R01_audit.png` + audit row `bd6e1766-97bd-4397-8349-b92808ea6659` (`IADM08_AUDIT_SEARCH`) |
| `IADM-08-US-003` (Audit event detail) | Audit event detail inspection (`R01` / `R02` / `R28`) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R28_audit_detail.png` + audit row `e85101aa-c87a-4de2-a260-ecc26dfbab9a` (`IADM08_AUDIT_DETAIL`) |
| `IADM-08-US-004` (Activity timeline) | Administrator activity timeline (`R01` / `R02`) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/RESULTS.json` (`Part B.5: 6 reads return 200`) + audit row `917082a8-d9e4-4bc9-a81c-4ad07abd4e22` (`IADM08_AUDIT_TIMELINE`) |
| `IADM-08-US-005` (R03 own-institute) | Institution own-institute activity (`R03` Institution Admin) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R03_institute.png` + `docs/iadm/iadm-08/evidence/us023/RESULTS.json` (`Part B.6`: cross-institute `403 IADM08_SCOPE_DENIED`, unattributed `fa5c6e81-fde9-4455-89ae-7ae6a2b361a2` hidden `404`) |
| `IADM-08-US-006` (R28 investigation) | Auditor investigation (`R28` Auditor, read-only, no export) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R28_desk.png`, `R28_audit_detail.png`, `R28_alerts.png`, `R28_export_denied.png` + audit row `e465b76d-0a63-46be-9480-c9702f3ae072` (`IADM08_AUDIT_SEARCH`) |
| `IADM-08-US-021` (Health inventory) | Server-side health target inventory (rejects client addresses) | API-ready | **API-ready** | completed | `OpsPartAContractTest` (`rejectsClientSuppliedHealthTargetParameters`) + `HealthTargetInventory.java` |
| `IADM-08-US-007` (Health overview) | Service health overview (`R01` / `R02`; `R03` denied) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R01_overview.png` + audit row `69ab2e87-feb4-4faf-907b-b56563e11cbf` (`IADM08_HEALTH_OVERVIEW`) |
| `IADM-08-US-008` (Health detail) | Service health detail for verified target | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/RESULTS.json` (`Part B.5: 6 reads return 200`) + audit row `fe2cb8fa-53bc-4fa5-8c86-67fd658b8780` (`IADM08_HEALTH_DETAIL`) |
| `IADM-08-US-009` (Operational alerts) | Read-only operational alerts + IADM-07 alerts | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R28_alerts.png` + audit row `bc978e59-e758-44f1-89bf-157a0a2571b4` (`IADM08_ALERTS_READ`) |
| `IADM-08-US-012` (IADM-07 evidence) | IADM-07 evidence linkage (read-only) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R28_alerts.png` + `OpsPartAContractTest` |
| `IADM-08-US-011` (SLA placeholder) | SLA / availability honest “not available” placeholder | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/DEMO.md` (`R01 SLA not available: SLA: not available`) |
| `IADM-08-US-010` (Ack absence) | Alert acknowledgement absence (`404 IADM08_NOT_AVAILABLE`) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R28_alerts.png` (`ackControls=0`) + `RESULTS.json` (`404 IADM08_NOT_AVAILABLE`) + `OpsDeferredFeaturesTest` |
| `IADM-08-US-015` (Export absence) | Export absence (`404 IADM08_NOT_AVAILABLE`) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R28_export_denied.png` (`404 This feature is not available.`) + `OpsDeferredFeaturesTest` |
| `IADM-08-US-022` (Deferred & denials) | Deferred-feature absence + denial-event search (`IADM02_*_DENIED`) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/failure_denial_only.png` (4 `IADM02_*_DENIED` rows) + `OpsDeferredFeaturesTest` (3/3 PASS) |
| `IADM-08-US-013` (Access-denial/isolation) | Access-denial and isolation (`R04` College Admin, `R03` Institution Admin, `R28` Auditor) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/R04_deny.png`, `R03_institute.png`, `R28_export_denied.png` |
| `IADM-08-US-014` (Source-failure handling) | Source-failure handling (`Unavailable`, `Unknown`, recovery, read-audit `503`) | Demo-verified | **Demo-verified** | completed | `docs/iadm/iadm-08/evidence/us023/failure_unavailable.png`, `failure_unknown.png`, `failure_recovered.png` + `docs/iadm/iadm-08/evidence/us023/RESULTS.json` |
| `IADM-08-US-023` (Close-out & film) | Evidence pack and close-out | Demo-verified | **Demo-verified** | completed | `docs/iadm/IADM-08_S4_Report.md` + `docs/iadm/iadm-08/evidence/us023/DEMO.md` (25/25 PASS) |

**CSV note:** `docs/user-stories/IADM-08-user-stories.csv` updated with `Status`, `Actual done level`, `Named evidence`, and plain-language labels next to every code.
