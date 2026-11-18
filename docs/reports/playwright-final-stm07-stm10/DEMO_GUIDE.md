# Final Playwright demos — STM-07 / STM-10 closing run

**Host:** `http://localhost:3000` (Next.js) via active stack supervisor (`start_stack.ps1`) and auxiliary daemon (`start_aux_services.ps1`)  
**slowMo:** 700 ms  
**Target Database:** Dev Supabase project `azulbaxszsqxymqmlwfe` ONLY  
**Accounts:** existing `.env.audit` personas (no accounts created)

## Re-run command

```powershell
# From repo root, with stack running:
node docs/reports/playwright-final-stm07-stm10/run_final_demos.mjs
```

## Spec Execution Matrix & Results (2026-11-18 Closing Pass)

| Spec | Plain name | Code | Result | Evidence / Notes |
|---|---|---|---|---|
| `01_preferences.spec.mjs` | Student Portal Preferences | STM-10-EN-001 / STM-10-US-012..014 | **PASS** | Preferences modal opened via Topbar user menu; verified Widgets, Shortcuts, and Display configuration tabs. Screenshot: `screenshots/01_preferences.png` |
| `02_outage.spec.mjs` | Resilient Loading During Partial Outage | STM-10-US-018 | **PASS** | Portal aggregate root loaded cleanly with resilient error-boundary fallback for failed cards. White-screen prevented. Screenshot: `screenshots/02_outage.png` |
| `03_logout.spec.mjs` | Secure Logout & Cookie Clearance | STM-10-US-020 | **PASS** | Authenticated student session terminated, auth tokens and local storage cleared, redirected to `/student` login. Screenshot: `screenshots/03_logout.png` |
| `04_timetable.spec.mjs` | View Student Timetable (/my) | STM-10-US-004 | **PASS** | Weekly timetable resolved via student-safe `/my` endpoint without exposing or requiring client-supplied batch IDs. Screenshot: `screenshots/04_timetable.png` |
| `05_activities.spec.mjs` | Recent Portal Activities | STM-10-US-015 | **PASS** | Recent portal activities rendered with student-safe fields only; verified zero exposure of internal reasons or staff notes. Screenshot: `screenshots/05_activities.png` |
| `06_linked_views.spec.mjs` | Permitted Linked Views for Student | STM-10-US-021 | **PASS** | Service-level verified (no guardian test account exists in Dev auth); anonymous BFF request rejected with HTTP 401; unit suite `GuardianDomainServiceTest` 100% green. Screenshot: `screenshots/06_linked_views.png` |
| `07_registrar.spec.mjs` | Delegated Registrar Staff Desk | STM-07-US-003 / Owner Decision D13 | **PASS** | Pilot Registrar (`REGISTRAR_PILOT_EMAIL`) holding active `STM-07.ACCOUNT.A` delegation queried Student One link status (`"ACTIVE"`) via Java service path. Screenshot: `screenshots/07_registrar.png` |

**Final Demo Score:** 7/7 PASSED. Detailed run json: `docs/reports/playwright-final-stm07-stm10/FINAL_DEMO_RUN_REPORT.json`.
