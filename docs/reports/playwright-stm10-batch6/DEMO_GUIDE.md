# STM-10 batch 6 — headed browser demo guide

**Date:** 2026-11-18  
**Project:** `azulbaxszsqxymqmlwfe`  
**Host:** `http://localhost:3000` (not `127.0.0.1` — cookies belong to localhost)  
**Accounts:** existing test-session / `.env.audit` student personas only.

## Re-run headed

```powershell
cd d:\CampXSync\CampXSync_V2.0
powershell -ExecutionPolicy Bypass -File scripts\dev\start_stack.ps1
# Confirm UI :3000 and gateway :8080
$env:CAMPX_UI_BASE='http://localhost:3000'
$env:CAMPX_DEMO_SLOWMO='700'
node docs/reports/playwright-stm10-batch6/run_batch6_demo.mjs
```

`slowMo` ~700 ms. Browser stays open ~4 s after each spec.

## Specs

| Spec file | Plain name | Code |
|---|---|---|
| `01_us004_timetable.spec.mjs` | View timetable | STM-10-US-004 |
| `02_us018_outage.spec.mjs` | Use portal during one integrated service outage | STM-10-US-018 |
| `03_us020_logout.spec.mjs` | Log out securely | STM-10-US-020 |

## Manual outage step (US-018)

To demonstrate a real one-service outage: stop document-service (or another section upstream) before the outage spec, confirm the aggregate panel shows a plain failure + retry, restart the service, click retry, confirm the section recovers.

## Demo run result (2026-11-18)

**Stack was not listening** when the batch finished (`:3000` / `:8080` / `:8093` all down). Headed specs were **not executed** against a live UI.

| Spec | Result | Exact missing piece |
|---|---|---|
| View timetable (STM-10-US-004) | STOPPED / BLOCKED | Item BLOCKED in backlog (no student-scoped timetable API). Spec records that and exits. |
| Use portal during one service outage (STM-10-US-018) | STOPPED | Next.js UI not on `http://localhost:3000` — run `start_stack.ps1` then re-run command above. |
| Log out securely (STM-10-US-020) | STOPPED | Same — UI / gateway not up for headed Playwright. |

Unit evidence for EN-002 / US-018 / US-020 remains **18/0** (`node --experimental-strip-types --test` under `frontend/src/lib/portal` + `secure-logout`). Re-run headed after stack is up.
