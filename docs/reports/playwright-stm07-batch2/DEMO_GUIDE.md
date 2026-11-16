# STM-07 batch 2 — headed browser demo guide

**Date:** 2026-11-16  
**Project:** `azulbaxszsqxymqmlwfe`  
**Accounts:** only those in `.env.audit` (Platform Admin, College Admin, Faculty, student1). No invented credentials.

## Re-run headed

```powershell
cd d:\CampXSync\CampXSync_V2.0
powershell -ExecutionPolicy Bypass -File scripts\dev\start_stack.ps1
# Optional (NOT in start_stack — required for STM-07 student APIs on :8093):
#   manually start student-service on 8093 with CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081
node docs/reports/playwright-stm07-batch2/run_batch2_demo.mjs
```

`slowMo` defaults to **700 ms**. Browser stays open ~4 s after each spec.

## Specs

| Spec file | Shows |
|---|---|
| `01_prior_features.spec.mjs` | Already-done STM-07: student link-status strip, eligibility/login request/suspend (API via BFF when :8093 up) |
| `02_us008_assign_role.spec.mjs` | Assign R25 path + Faculty denied + non-R25 denied messaging |
| `03_us009_revoke_role.spec.mjs` | Revoke R25 path + access cut on suspension narrative |
| `04_en002_iam_only.spec.mjs` | Confirms no local credential UI; IAM operator / Platform Admin desk reachability |
| `05_en004_retry.spec.mjs` | Provisioning history / retry failed request path |

## Known STOP — missing stack piece

**Missing:** `student-service` on **:8093** is **not** started by `scripts/dev/start_stack.ps1`.  
Gateway already routes `/api/v1/students` → `http://localhost:8093/...`.

**Exact change needed (do not apply in this batch per owner rule):** add a `Start-JavaService` block for `student-service` (port 8093, workdir `services/student-services/student-service`, classpath file present) in `start_stack.ps1`, and expose it in `check_stack.ps1`.

When :8093 is down, specs that call student APIs record **SKIPPED_MISSING_8093** and continue.

## Negative cases covered on screen

- Faculty denied STM-07 account actions (expects forbidden / no manage controls).
- Student cannot link/provision (student desk has no staff link controls; self-assign blocked).
- Non-IAM role assign rejected (API response visible when :8093+:8081 up).
