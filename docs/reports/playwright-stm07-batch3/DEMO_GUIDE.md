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

## Re-run headed

```powershell
cd d:\CampXSync\CampXSync_V2.0
powershell -ExecutionPolicy Bypass -File scripts\dev\start_stack.ps1
node docs/reports/playwright-stm07-batch3/run_batch3_demo.mjs
```

`slowMo` defaults to **700 ms**. Browser holds ~4 s after each spec.
