# Next Task

**STATUS: STOP. No push. Await owner on CSV close + optional next fix.**

## Done this turn

1. **Gateway Registrar recognition (approved, limited):** commit `dffbf19`.
   - Baseline gateway suite: **6 failures / 7 errors** (`docs/reports/_verify_gateway_baseline_pre_registrar.txt`).
   - After change: **6 failures / 8 errors** on full module run — failure count unchanged; +1 error was `LiveG1GrantsExecutionTest` prepared-statement flake (alone: 0 errors). Identity suite **32/0**. New tests: Registrar recognised; College Admin-only route still denied; Faculty/Student/College Admin unchanged; no-role still rejected.
   - No new route grants. Registrar without delegation still denied in Java when the student row is visible.
2. **Browser re-run (delegated Registrar success only):** **FAIL** after gateway fix. Desk + JWT OK; link-status **404 Student not found** (RLS/visibility under authenticated role for pilot Registrar). Screenshot refreshed; `DEMO_GUIDE.md` updated. No further code without approval.
3. **Path C finding (STM-03…06 delegated Registrar approvals):** exercised as **IAM Path C HTTP active-checks / service-client smoke** (e.g. `docs/reports/R31_PATH_C_STACK_SMOKE_2026-11-15.json`), **not** as headed browser desk approvals through UI → gateway. No fix in this turn.
4. **CSV:** `.csv.tmpwrite` ready. **Do not swap** until owner says the backlog CSV is closed in the editor.
5. **STM-10-US-023** (Configure portal settings and publish announcements): **BLOCKED** — communication service does not exist (`communication-services/notification-service/.gitkeep` only). Do not build.

## Owner approval needed (new services) — do not start

Treat these as one approval gate for missing services:

| Item | Why blocked |
|---|---|
| Student portal announcements (STM-10-US-023) | No communication service implementation |
| Academic history (STM-08) | No academic-history service in repo |
| Student service requests (STM-09) | No service-requests service in repo |

## Optional unblock for Registrar staff-screen success

Pilot Registrar can pass the gateway but cannot see `stu.students` under RLS the way College Admin can. Needs a separate owner decision (grants / visibility), not more gateway route access.

## Rules

No push unless authorized.
