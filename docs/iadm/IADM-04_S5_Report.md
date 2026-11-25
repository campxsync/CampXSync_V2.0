# IADM-04 S5 Close-out Report (DEV)

**Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration`  
**Scope:** US-038 outage demos, US-040 evidence + monolith 410 cutover, evidence checks 4a–4d  
**Stop gate:** local commits only — **do not push** until owner says

---

## Story status (honest)

| Story | Plain name | Status | Evidence |
|---|---|---|---|
| US-038 | Audit trace + audit/notification outage | **completed** | Headed a/b/c all PASS — `docs/iadm/iadm-04/evidence/s5/S5_HEADED_RESULTS.json` |
| US-040 | Final evidence pack + retire moved routes | **completed** | This report + 410 cutover executed + FORCE RLS |

US-040 was treated as **partially completed** during S5 until US-038 passed; both are now completed in CSV.

---

## 1. US-038 — three headed demos

### a) Full payment path traced in audit desk
- Invoice → online initiate → signed DEV callback settle → Auditor desk screenshot  
- Files: `s5_us038a_audit_trace.png`, results JSON steps `US-038a *`

### b) Audit disabled
- DEV flag `auditDisabled=true` via `POST /api/v1/iadm/04/dev/outage-flags`  
- Fresh invoice (created while audit still on) → initiate returns **503** `IADM04_AUDIT_UNAVAILABLE`  
- Payment simulator call count **unchanged** (adapter not called)  
- Admin-visible error captured: `s5_us038b_audit_disabled_error.png`  
- Re-enable → retry same idempotency key → **same intent, no duplicate** (`replay=true`)

### c) Notifications disabled
- Flag `notificationsDisabled=true` → settle enqueues notice **DELAYED**  
- Billing subscription state remained **ACTIVE** (unchanged by notice path)  
- Re-enable → flush → notices **DELIVERED** (`flushedCount=1`)  
- Screenshots: `s5_us038c_notifications_delayed.png`, `s5_us038c_notifications_delivered.png`

---

## 2. Monolith billing cutover (owner-agreed)

Gateway returns **HTTP 410** `IADM04_CUTOVER_GONE` for:

| Retired route | Replacement |
|---|---|
| `/api/v1/admin/billing/*` | `/api/v1/iadm/04` (BFF `/api/platform/billing*`) |
| `/api/v1/admin/usage` | `/api/v1/iadm/04/usage` |
| `/v1/billing-accounts` | `/api/v1/iadm/04/plans` |

`plat.*` tables **kept**; IADM-04 owns writes. Monolith Java handlers not deleted (out of scope).

### FORCE row-level security
Migration `20261125160000_iadm04_s5_force_rls_notices` applied on DEV `azulbaxszsqxymqmlwfe`.

| Table | RLS enabled | FORCE |
|---|---|---|
| subscription_plans, plan_entitlements, subscriptions, invoices, invoice_lines, gateway_transactions, usage_metrics, billing_notices, subscription_history | yes | **yes** |

### System Admin (R02) cannot write plans
- IADM-04 `POST /plans` as R02 → **403** `IADM04_FORBIDDEN` (Super Admin bypass narrowed to `role_code = SUPER_ADMIN` only)  
- Monolith plan route → **410**  
- Offline matrix: `BillingOutageFlagsTest.r02CannotManagePlansByAnyPermissionKey` PASS  
- BFF already blocks non–Super-Admin POSTs except callback retry

---

## 3. Evidence checks 4a–4d

### a) Access gate choke — headed R03 film (gap closed)
**DEMO institute:** `IADM04_DEMO` / tenant `d4000000-0000-4000-8000-000000000004` (ACTIVE institute).  
**Persona:** Institution Admin (R03) `institution.iadm04.demo@campx.local` — password stored only under gitignored `docs/iadm/iadm-02/evidence/.local/seed_accounts.json` via `scripts/dev/iadm04_seed_r03_demo.mjs` (never printed).  
**SQL:** `database/migrations/20261125161000_iadm04_s5_r03_demo_persona.sql` + rollback.

| Phase | What was shown | Result |
|---|---|---|
| PENDING | ACTIVE institute + PENDING sub; login as R03; Subscription Desk + profile + sign-out only; other routes → access-denied; desk shows pending payment + DEV simulator; simulated pay → ACTIVE restores access | **PASS** |
| NONE | Soft-deleted subscription row (no live sub); same login; gate deny + desk deny UI; colleges blocked | **PASS** |
| TRIAL | Subscription `TRIAL`; same login; gate allow; colleges reachable | **PASS** |

- Runner: `node scripts/demo/iadm04_s5_evidence_4a_headed.mjs` (`IADM04_4A_PHASE=pending|none|trial`)  
- Results: `docs/iadm/iadm-04/evidence/s5/S5_4A_HEADED_RESULTS.json` — **12/12 PASS**, 0 fail  
- Screenshots: `s5_4a_pending_*.png`, `s5_4a_active_access_restored.png`, `s5_4a_none_*.png`, `s5_4a_trial_*.png`  
- Earlier API probe on MAC (PENDING flip) remains documented; MAC restored ACTIVE after that probe

### b) DEMO ACTIVE seed + rollback
- Seed: `database/migrations/20261125121000_iadm04_demo_seed.sql`  
- Rollback: `database/migrations/20261125121000_rollback__iadm04_demo_seed.sql`  
- Live DEV count at S5: **13** DEMO ACTIVE subscriptions

### c) Onboarding wizard catalogue + trial policy
- Wizard loads live ACTIVE plans from IADM-04  
- Trial label duration comes from `billing.trial.duration_days` via `view=trial-policy` (not hard-coded 30 in the live path)  
- Screenshot: `s5_evidence_4c_onboarding_catalogue.png`

### d) Asia/Kolkata via IADM-02 config; jobs fail closed when unset
- Seeded DEMO value: `billing.timezone` = `Asia/Kolkata` (cfg.setting_definitions)  
- With TZ present: jobs `ran=true`  
- With TZ blanked: `ran=false`, `IADM04_BILLING_TZ_INVALID` — evidence `s5_jobs_tz_fail_closed.json`  
- TZ restored to Asia/Kolkata after probe

---

## 4. Verification commands

```text
mvn -pl services/administration-services/iadm-04-billing-entitlements-service test "-Dtest=BillingOutageFlagsTest,BillingPermissionMatrixTest,BillingLifecyclePolicyTest,DevPaymentSimulatorTest"
cd frontend; npx tsc --noEmit
node scripts/demo/iadm04_s5_headed.mjs
node scripts/demo/iadm04_s5_evidence_4a_headed.mjs   # phases pending|none|trial
```

All green on this run: **27/27** US-038 headed steps; **12/12** evidence 4a steps; unit tests 0 failures; `tsc` 0 errors.

---

## 5. DEMO / simulator values still in use (not production decisions)

| Topic | Current DEMO / simulator value | Owner decision still open? |
|---|---|---|
| Payment provider | DEV simulator only (`DEV_SIMULATOR`) | Yes — real provider choice |
| Tax / GST | None — amounts are minor units + currency only | Yes |
| Invoice numbering | Internal UUID / demo ref; no statutory series | Yes (Finance) |
| Proration | Not calculated on plan change | Yes |
| Refunds / credits | Not implemented | Yes |
| Invoice retention | No retention policy coded | Yes |
| Overage charges | Usage recorded; no overage billing / throttle | Yes |
| Trial duration | DEMO `billing.trial.duration_days` = 30 | Configurable; confirm production |
| Grace days | DEMO `billing.grace.days` = 7 | Configurable; confirm production |
| Renewal reminder | DEMO `billing.renewal.reminder_days` = 14 | Configurable; confirm production |
| Billing time zone | DEMO `billing.timezone` = Asia/Kolkata via IADM-02 | Confirm production TZ |
| Outage flags | In-memory DEV toggles `/dev/outage-flags` | DEV-only; not for production |
| Billing notices | `plat.billing_notices` DEMO best-effort | Confirm notification channel |

---

## 6. Files touched (summary)

- IADM-04: outage flags, durable audit check, billing notices, Super Admin-only commercial bypass  
- Gateway: 410 cutover for billing/usage/billing-accounts  
- Frontend: R03 limited session, wizard trial policy, auditor billing evidence nav, BFF outage/notices views  
- DB: FORCE RLS + `plat.billing_notices`  
- Demo: `scripts/demo/iadm04_s5_headed.mjs`, `scripts/demo/iadm04_s5_evidence_4a_headed.mjs`  
- DEV persona seed: `scripts/dev/iadm04_seed_r03_demo.mjs` + `20261125161000_iadm04_s5_r03_demo_persona`  
- CSV: US-038 + US-040 → completed  

**IADM-03 stories / badge:** not touched.

---

## 7. Local commits (not pushed)

1. `97eaba6` — `feat(iadm04): S5 close-out — US-038/040, 410 cutover, FORCE RLS`  
2. (this commit) — `test(iadm04): S5 evidence 4a — R03 DEMO persona and headed gate film`  

No push until owner authorizes.
