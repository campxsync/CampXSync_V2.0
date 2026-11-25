# IADM-04 S4 — Renewal, usage, reports & close-out verification (US-031…040)

**Status:** **`APPROVED S4 COMMIT`**  
**Date:** 2026-11-25  
**DB:** DEV `azulbaxszsqxymqmlwfe` only  
**Service:** `iadm-04-billing-entitlements-service` `:8102` `/api/v1/iadm/04`  
**Prior:** S3 `APPROVED S3 COMMIT` `e86e925`

## Story status

| Story | Status | Evidence |
|---|---|---|
| US-031 Scheduled jobs | **completed** | `POST /jobs/run`; checkpoints; fail closed without `billing.timezone` |
| US-032 Renewal & expiry desk | **completed** | `GET /renewals`; `POST /renewals/{id}/act`; platform desk |
| US-033 Usage ingest | **completed** | `POST /usage`; dedupe_key unique; no overage/throttle |
| US-034 Own usage vs quota | **completed** | `GET /usage`; institution desk section |
| US-035 Scoped billing reports | **completed** | `GET /reports/billing`; platform desk |
| US-036 Auditor evidence read-only | **completed** | `GET /audit-evidence`; `/platform/billing/audit` + mutation probe |
| US-037 Tenant/role denial matrix | **completed** | `BillingLifecyclePolicyTest` + matrix; College Admin denied |
| US-038 Audit/outage behaviour | **partially completed** | Durable audit on mutations; full notification-outage demo not re-run this slice |
| US-039 Student fees stay in FIN | **completed** | No `/student-fees` route (404); no FIN sync code in IADM-04 |
| US-040 Evidence pack | **completed** | This report + `docs/iadm/iadm-04/evidence/s4/` |

## Headed demo

- Script: `node scripts/demo/iadm04_s4_headed.mjs`
- Results: `docs/iadm/iadm-04/evidence/s4/S4_HEADED_RESULTS.json` — **PASS**
- Shots: `s4_renewal_desk.png`, `s4_billing_reports.png`, `s4_audit_evidence.png`

## Tests

| Check | Result |
|---|---|
| Permission + lifecycle matrix unit tests | PASS |
| `DevPaymentSimulatorTest` | PASS |
| `cd frontend; npx tsc --noEmit` | 0 errors |
| Headed Playwright | PASS |

## Migration (DEV applied)

- `database/migrations/20261125150000_iadm04_s4_lifecycle_usage.sql`
- Tables: `plat.billing_job_checkpoints`, `plat.usage_measurements`, `plat.renewal_actions`

## Policy keys used (IADM-02)

- `billing.timezone` (required — jobs fail closed if absent)
- `billing.grace.days`, `billing.renewal.reminder_days` (seeded DEMO defaults)

## Monolith retirement (US-040 proposal only)

Legacy institute-admin billing routes remain in place (addition D). Propose `410 Gone` cutover **after** owner agrees and new desks stay demo-verified — **not executed** in S4.

## Stop gate

**`APPROVED S4 COMMIT` recorded.** Do **not** push unless owner says.
