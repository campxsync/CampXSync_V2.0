# STM-07 batch 3 — headed browser demo guide

**Date:** 2026-11-17  
**Project:** `azulbaxszsqxymqmlwfe`  
**Accounts:** only those in `.env.audit`. No invented credentials.

## Stories shown

| Spec | Plain name | ID |
|---|---|---|
| `01_us003_staff_link_status.spec.mjs` | Staff view of account link status | STM-07-US-003 |
| `02_us005_retry.spec.mjs` | Retry failed login setup | STM-07-US-005 |
| `03_us006_history.spec.mjs` | See past login requests | STM-07-US-006 |
| `04_us012_audit.spec.mjs` | Audit who changed accounts and roles | STM-07-US-012 |
| `05_negative_auth.spec.mjs` | Faculty denied; student denied; Registrar without ACCOUNT.A (see note) | negatives |

## Registrar-without-delegation note (plain words)

The Registrar (R31) without-delegation browser case was **rejected at the API Gateway as a malformed login token** (`gateway_malformed_jwt`), **not** at the Java STM-07.ACCOUNT.A delegation check.

**Why the token was malformed:** the pilot Registrar Auth user (`REGISTRAR_PILOT_EMAIL` in `.env.audit`) signs in successfully, but its Supabase access token’s `app_metadata` has **no `tenant_id`**. The gateway requires `app_metadata.tenant_id` (UUID) on every Bearer JWT. Without it, the gateway returns 401 before student-service runs. The staff desk page can still open because page middleware accepts the local `campx_test_session` cookie; only the BFF → gateway call needs the real JWT.

**Stopped cases (need owner Auth decision — no account/metadata changes made):**

1. Delegated pilot Registrar **succeeds** on the staff screen (needs a JWT the gateway accepts, then an active STM-07.ACCOUNT.A grant).
2. Registrar without delegation gets a **Java** delegation denial (needs the same valid JWT so the request reaches student-service).

## Re-run headed

```powershell
cd d:\CampXSync\CampXSync_V2.0
powershell -ExecutionPolicy Bypass -File scripts\dev\start_stack.ps1
node docs/reports/playwright-stm07-batch3/run_batch3_demo.mjs
```

`slowMo` defaults to **700 ms**. Browser holds ~4 s after each spec.
