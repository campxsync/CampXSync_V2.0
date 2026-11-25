# IADM-04 S2 — Build Report (US-017 … US-025)

**Status:** built — **STOP for owner headed review**  
**Authorized after:** `APPROVED S1 COMMIT` `74ef789`  
**DEV:** `azulbaxszsqxymqmlwfe` · service `:8102`

## Stories

| Story | Result | Evidence |
|---|---|---|
| US-017 Plan-change request (R03) | **completed** | `POST /plan-changes` + institution BFF/desk |
| US-018 Plan-change decide (R01) | **completed** | `POST /plan-changes/{id}/decide`; self-approve denied; apply when effective ≤ today; **no proration** |
| US-019 Invoice generate + snapshot | **completed** | Minor units + `pricing_snapshot`; idempotent |
| US-020 Trial → PENDING + invoice | **completed** | Headed: attach TRIAL → convert → PENDING |
| US-021 Institution invoices | **completed** | `/api/institution/billing/invoices` + desk section |
| US-022 Super Admin invoice manage | **completed** | Generate/void APIs + platform desk |
| US-023 Offline evidence (R01-A) | **completed** | Immutable evidence; invoice **not** paid |
| US-024 Offline reconcile (R01-B ≠ A) | **completed** | Same-actor **403** `IADM04_SOD_SAME_ACTOR`; R01-B APPROVED |
| US-025 PENDING → ACTIVE on verified pay | **completed** | Headed: reconcile → `subscriptionStatus=ACTIVE` |

## Explicit non-claims
- No proration / credits / refunds (D9)
- No GST / external invoice numbering (D22)
- Online payment simulator = **S3**
- Schedulers for future-effective plan-change / trial jobs = later

## Schema (DEV)
- Migration `iadm04_s2_finance` (+ SQL files under `database/migrations/20261125130000_*`)
- Tables: `plat.plan_change_requests`, `plat.offline_payment_evidence`, `plat.offline_payment_reconciliations`
- `plat.invoices` evolved: `amount_minor_units`, `pricing_snapshot`, `idempotency_key`, `internal_ref`

## Verification
| Check | Result |
|---|---|
| `mvn compile` IADM-04 | PASS |
| `npx tsc --noEmit` | 0 errors |
| `node scripts/demo/iadm04_s2_headed.mjs` | **PASS** — `docs/iadm/iadm-04/evidence/s2/S2_HEADED_RESULTS.json` |
| Screenshots | `s2_platform_invoice_desk.png`, `s2_offline_desk.png` |

## STOP
Owner: `APPROVED S2` / `APPROVED S2 COMMIT` / `CHANGES S2: …`
