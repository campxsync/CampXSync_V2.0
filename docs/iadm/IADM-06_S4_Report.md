# IADM-06 S4 Close-out — Platform Analytics & Usage Insights

**Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration` (local commits only; **not pushed**)  
**Database:** DEV `azulbaxszsqxymqmlwfe` only  

## Verdict

IADM-06 S0–S4 implemented and filmed on the local stack. Headed persona film **20/20 PASS**. Unit suite **18/18 PASS**. Additive IADM-01/04 aggregates confirmed. DEMO analytics policy recreated through IADM-05 lifecycle (author ≠ approver). Awaiting owner `APPROVED IADM-06` / `APPROVED IADM-06 COMMIT`.

---

## Table of all 28 rows

| ID | Story name | Target | ACTUAL today | Evidence |
|---|---|---|---|---|
| IADM-06-US-016 | Preflight inventory and reconciliation | API-ready | **API-ready / completed** | `docs/iadm/iadm-06/US016_PREFLIGHT_REPORT.md` |
| IADM-06-US-017 | IADM-06 service scaffold | API-ready | **API-ready** | `:8104`, gateway `/api/v1/iadm/06` |
| IADM-06-US-018 | Permission keys and role mapping | API-ready | **API-ready** | `iadm06.*` + matrix tests |
| IADM-06-US-019 | Schema, tenant scoping and RLS | API-ready | **API-ready** | `anl.*` FORCE RLS |
| IADM-06-US-020 | Shared audit integration | API-ready | **API-ready** | `DurableAuditWriter` fail-closed |
| IADM-06-US-021 | IADM-05 policy resolution client | API-ready | **API-ready (partial on view outcome)** | Resolve gate OK; view outcome via shared audit only — F-01 |
| IADM-06-US-022 | Source read clients IADM-01/04 | API-ready | **API-ready** | Clients + additive endpoints |
| IADM-06-US-023 | Metric catalogue seed M01–M10 | API-ready | **API-ready** | Seed + catalogue tab |
| IADM-06-US-024 | Aggregation + suppression | API-ready | **API-ready** | SuppressionEngine + headed raise |
| IADM-06-US-025 | Demo dataset and personas | Demo-verified | **Demo-verified** | Headed R01/R02/R03×2/R04/R28 |
| IADM-06-US-001 | View platform aggregate overview | Demo-verified | **Demo-verified** | `R01_overview.png` |
| IADM-06-US-010 | View read-only metric catalogue | Demo-verified | **Demo-verified** | `R01_catalogue.png` |
| IADM-06-US-013 | R02 view platform aggregates | Demo-verified | **Demo-verified** | `R02_overview.png` |
| IADM-06-US-015 | R04 access denied | Demo-verified | **Demo-verified** | `R04_denied.png` |
| IADM-06-US-002 | Compare institutes | Demo-verified | **Demo-verified** | `R01_comparison.png` |
| IADM-06-US-003 | R03 own-institute analytics | Demo-verified | **Demo-verified** | `R03A_institute.png`, `R03B_institute.png` |
| IADM-06-US-004 | Entitlement consumption | Demo-verified | **Demo-verified** | `R01_entitlements.png` |
| IADM-06-US-005 | Trend series | Demo-verified | **Demo-verified** | `R01_trends.png` |
| IADM-06-US-011 | Entitlement insight in scope | Demo-verified | **Demo-verified** | entitlements tab + institute desk |
| IADM-06-US-006 | Export platform aggregates | Demo-verified | **Demo-verified** | R01/R02 export |
| IADM-06-US-007 | Export own-institute aggregates | Demo-verified | **Demo-verified** | `R03A_export.png` |
| IADM-06-US-012 | No-data / unavailable / partial | Demo-verified | **Demo-verified** | source-stopped fault |
| IADM-06-US-014 | Export status and result | Demo-verified | **Demo-verified** | export list + retry |
| IADM-06-US-026 | Aggregate export engine | API-ready | **API-ready** | metadata-only CSV lifecycle |
| IADM-06-US-008 | R28 read-only analytics | Demo-verified | **Demo-verified** | `R28_audit.png` |
| IADM-06-US-009 | Enforce policy/isolation/faults | Demo-verified | **Demo-verified** | fault screenshots |
| IADM-06-US-027 | Failure isolation role suite | API-ready | **API-ready** | `evidence/us027/FAILURE_SUITE.md` |
| IADM-06-US-028 | Evidence pack and close-out | Demo-verified | **Demo-verified** | this report + `evidence/us028/` |

**Honest partial:** US-021 view outcome cannot call IADM-05 `reportOutcome` without a governance `request_id` (F-01). Resolve-before-release remains mandatory.

---

## Demo checklist (blueprint §15 style) — ticked

| # | Item | Tick | Evidence |
|---|---|---|---|
| 1 | Super Admin (R01) sees real seeded figures | [x] | `R01_overview.png`; metrics=6 |
| 2 | Metric catalogue M01–M10 | [x] | `R01_catalogue.png`; rows=10 |
| 3 | Compare institutes | [x] | `R01_comparison.png` |
| 4 | Entitlements / trends | [x] | `R01_entitlements.png`, `R01_trends.png` |
| 5 | DEMO ONLY threshold low then raise | [x] | low=**1**, raised=**50**; withheld=24 |
| 6 | System Admin (R02) view + export | [x] | `R02_overview.png`, `R02_export.png` |
| 7 | Institution Admin institute A | [x] | `R03A_institute.png` / export |
| 8 | Institution Admin institute B (no A data) | [x] | `R03B_institute.png` (MAC / maharani) |
| 9 | College Admin (R04) denied | [x] | `R04_denied.png` |
| 10 | Auditor (R28) read-only; export 403 | [x] | `R28_audit.png` |
| 11 | Unresolved policy withholds | [x] | `R01_fault_policy.png` |
| 12 | Source stopped → unavailable | [x] | `R01_fault_source.png` |
| 13 | Audit unavailable refuses | [x] | `R01_fault_audit.png` |
| 14 | Failed export retry | [x] | `R01_fault_export_failed.png`, `R01_export_retry.png` |
| 15 | No person-level rows on additive sources | [x] | `evidence/prefilm/ADDITIVE_ENDPOINTS.json` |

Headed runner: `node scripts/demo/iadm06_headed.mjs` → `docs/iadm/iadm-06/evidence/us028/DEMO.md` (**20/20 PASS**).

---

## Compliance items closed (exact values)

### 1) Permission keys with audit event names

| Permission key | Audit event name |
|---|---|
| `iadm06.platform.view` | `IADM06_PLATFORM_VIEW` |
| `iadm06.institute.view` | `IADM06_INSTITUTE_VIEW` |
| `iadm06.entitlement.view` | `IADM06_ENTITLEMENT_VIEW` |
| `iadm06.export.request` | `IADM06_EXPORT_REQUEST` |
| `iadm06.export.view` | `IADM06_EXPORT_RESULT` |
| `iadm06.catalogue.view` | `IADM06_CATALOGUE_VIEW` |
| `iadm06.catalogue.manage` | *reserved — not implemented* |

### 2) Route collision check

- Gateway prefix: **`/api/v1/iadm/06`** → `http://localhost:8104/api/v1/iadm/06`
- Port: **8104**
- **No collision** with legacy `/api/v1/admin/...` or academic `/api/v1/analytics*` (separate namespaces; verified in preflight + running gateway)

---

## Decisions D1–D11 (what the build assumed)

| # | Assumption used |
|---|---|
| D1 | Catalogue seeds **M01–M10** only (+ DEMO-ADOPT fixture labelled DEMO ONLY) |
| D2 | On-request compute; stores only `anl.metric_catalogue` + `anl.export_requests`; refresh = query time |
| D3 | Sources **IADM-01 + IADM-04** only via additive aggregate contracts |
| D4 | Suppress platform/cross-institute; **own-institute not suppressed**; runtime DEMO threshold **1** then raise **50**; documented default 5; production **OWNER-OPEN** |
| D5 | Schema `anl`; tables above |
| D6 | Port **8104**, route `/api/v1/iadm/06`, keys/events in compliance table |
| D7 | Aggregate CSV stream; metadata only; **no approval step**; export gate resolves IADM-05 published policy |
| D8 | Same JSON read endpoints; no named Intelligence consumer in this slice |
| D9 | Shared `audit.log_event`; **fail-closed** if audit unavailable |
| D10 | Academic analytics / `rpt.*` ignored for IADM-06 |
| D11 | Service name follows IADM-0N convention (`iadm-06-platform-analytics-service`) |

**D7 note (F-02):** IADM-05 has no propose path for `policy_type=EXPORT`. Export gate resolves **RETENTION + domain ANALYTICS** (same published DEMO analytics handling policy). Direct `DEMO-ANALYTICS-EXPORT` superseded.

---

## Pre-film checks

| Check | Result |
|---|---|
| IADM-01/04 additive aggregates only (no person fields) | PASS — `evidence/prefilm/` |
| Existing IADM-01 test suite | PASS (7/7) |
| Existing IADM-04 test suite | PASS (15/15) |
| IADM-06 unit suite | PASS (18/18) |
| DEMO policy lifecycle recreate (R02 author ≠ R01 approver) | PASS — `DEMO-ANALYTICS-HANDLING` **v2** PUBLISHED |
| Frontend `tsc --noEmit` | PASS (0 errors) |

---

## Findings kept partial

See `docs/iadm/iadm-06/FINDINGS.md` (F-01 report-outcome on views; F-02 EXPORT propose gap).

---

## Stack / tooling notes

- Stack started via `scripts/dev/start_stack.ps1` (IADM-01…06 HEALTHY).
- `restart_service.ps1` health wait hardened with wall-clock timeout (Invoke-WebRequest); IADM-06 aborts on health fail.
