# STM-10 batch 5 — headed browser demo guide (preferences)

**Date:** 2026-11-17  
**Project:** `azulbaxszsqxymqmlwfe`  
**Accounts:** only existing test-session / `.env.audit` student personas. No invented credentials.

## Re-run headed

```powershell
cd d:\CampXSync\CampXSync_V2.0
powershell -ExecutionPolicy Bypass -File scripts\dev\start_stack.ps1
# Confirm student-service :8093 and UI :3000 are up
node docs/reports/playwright-stm10-batch5/run_batch5_demo.mjs
```

`slowMo` defaults to **700 ms**. Browser stays open ~4 s after each spec.

## Specs

| Spec file | Plain name | Code |
|---|---|---|
| `01_us012_widgets.spec.mjs` | Configure dashboard widgets | STM-10-US-012 |
| `02_us013_shortcuts.spec.mjs` | Save shortcuts | STM-10-US-013 |
| `03_us014_display.spec.mjs` | Configure language and display preferences | STM-10-US-014 |

## Notes

- Durable save requires student-service on **:8093** and API Gateway on **:8080**.
- Cross-student isolation for preferences is covered by Java security tests; the headed specs use the shared student test-session role and show save/reload UX.
- Second-student isolation with a distinct account is exercised in `Stm10PortalPreferencesEn001Test` / story suites (not by creating new Auth users).

## Demo run result (2026-11-17)

**All three specs STOPPED at sign-in.**

| Spec | Stopped step | Exact missing piece |
|---|---|---|
| Configure dashboard widgets | Sign in as student | `/api/auth/test-session?role=student&returnTo=/student` and Quick Dev link `Student — Documents Desk` both bounce back to `/login` (no workspace session established in headed Playwright). |
| Save shortcuts | Sign in as student | Same — session cookie / JWT-backed test-session for student persona did not stick in this runner. |
| Configure language and display preferences | Sign in as student | Same — landed on `login?returnTo=%2Fstudent%2Fdocuments`. |

**Do not invent credentials.** Acceptance for persistence/isolation remains covered by Java suites (`Stm10PortalPreferencesEn001Test`, `Stm10Us012*`, `Stm10Us013*`, `Stm10Us014*`). Re-run headed after owner confirms student Quick Dev / audit persona sign-in works in a manual browser.
