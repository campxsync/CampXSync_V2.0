# Final Playwright demos — STM-07 / STM-10 autonomous run

**Host:** `http://localhost:3000` (Next.js) via existing stack  
**slowMo:** 500–800 ms  
**Accounts:** use only existing `.env.audit` personas (never create accounts)

## Re-run command

```powershell
# From repo root, stack already up:
node docs/reports/playwright-final-stm07-stm10/run_final_demos.mjs
```

Optional individual specs (same folder):

```powershell
npx playwright test --config=docs/reports/playwright-final-stm07-stm10/playwright.config.mjs --headed --slow-mo=700
```

## Specs (plain step names)

| Spec | Plain name | Code | Expected this run |
|---|---|---|---|
| `01_preferences.spec.mjs` | Portal preferences | STM-10-EN-001 / US-012–014 | Built earlier; re-verify if stack up |
| `02_outage.spec.mjs` | Portal during one outage | STM-10-US-018 | Built earlier |
| `03_logout.spec.mjs` | Secure logout | STM-10-US-020 | Built earlier |
| `04_timetable.spec.mjs` | View timetable | STM-10-US-004 | May show **no data in Dev** |
| `05_activities.spec.mjs` | Recent portal activities | STM-10-US-015 | Empty or own history |
| `06_linked_views.spec.mjs` | Permitted linked views | STM-10-US-021 | **no test account** (guardian) |
| `07_registrar.spec.mjs` | Registrar staff-screen | (autonomy C) | **BLOCKED** (matrix silent on Registrar `V`) |

## Closing-pass result (2026-11-18)

Headed demos were **not re-executed in this closing pass** (capacity: suites + docs prioritized after A–G). Classify as **script not run this pass** — not a product defect report. Re-run the command above when the stack is healthy.

Unit/service evidence for this run is in `docs/reports/COMPLETION_REPORT.md` Entries 54–59 and `docs/reports/AUTONOMOUS_DECISIONS_LOG.md`.
