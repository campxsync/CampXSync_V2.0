# IADM-04 S3 — Online payment simulator (US-026…030)

**Status:** **`APPROVED S3 COMMIT`**  
**Date:** 2026-11-25  
**DB:** DEV `azulbaxszsqxymqmlwfe` only  
**Service:** `iadm-04-billing-entitlements-service` `:8102` `/api/v1/iadm/04`  
**Prior:** S2 `APPROVED S2 COMMIT` `6b9ae23`

## Story status

| Story | Status | Evidence |
|---|---|---|
| US-026 DEV payment adapter + simulator | **completed** | `DevPaymentSimulator` + `PaymentProviderAdapter`; labelled `DEV_SIMULATOR`; no network |
| US-027 R01 initiate online payment | **completed** | `POST /online-payments/initiate`; intent+audit **before** adapter; duplicate-safe |
| US-028 Signed callback + replay | **completed** | `POST /payments/callback`; signature / window / duplicate / mismatch |
| US-029 R01 reconcile unmatched/mismatched | **completed** | `POST /online-payments/{id}/reconcile` (`FORCE_MATCH` / `MARK_FAILED` / `DISMISS`) |
| US-030 R02 inspect/retry failed callbacks | **completed** | `GET /payment-callbacks/failures`; `POST .../retry`; BFF allows R02 retry only |

## Headed demo

- Script: `node scripts/demo/iadm04_s3_headed.mjs`
- Results: `docs/iadm/iadm-04/evidence/s3/S3_HEADED_RESULTS.json` — **PASS**
- Shots: `s3_online_payment_desk.png`, `s3_ops_callback_desk.png`

Path filmed: open invoice → initiate (DEV sim) → invalid signature rejected → valid callback settles (PAID) → duplicate ACK → mismatch hold → R01 FORCE_MATCH → R02 retry → desks.

## Tests

| Check | Result |
|---|---|
| `BillingPermissionMatrixTest` + `DevPaymentSimulatorTest` | PASS |
| `cd frontend; npx tsc --noEmit` | 0 errors |
| Headed Playwright | PASS |

## Migration (DEV applied)

- `database/migrations/20261125140000_iadm04_s3_online_payments.sql`
- Rollback: `..._rollback__iadm04_s3_online_payments.sql`
- Tables: `plat.online_payment_intents`, `plat.payment_callback_events`, `plat.online_payment_reconciliations`

## Explicitly out of scope (S4+)

- Real payment provider / credentials / network
- GST / invoice numbering / proration / refunds
- Renewal/expiry jobs (US-031+)
- Usage quotas (US-033+)

## Notes

- Initiation is **not** settlement; signed callback (or R01 reconcile) settles.
- Rejected-signature callbacks may be retried with the same `eventId` after a valid signature (supports R02 retry).
- DEV seed lists `systemadmin.campx@gmail.com` in `plat.platform_admins`, so gateway-layer commercial deny for that account is not meaningful; BFF still restricts initiate/reconcile to `platform_admin`, and R02 is limited to callback retry on POST.

## Stop gate

**`APPROVED S3 COMMIT` recorded.** Do **not** start S4 until newly authorized. Do **not** push unless owner says.
