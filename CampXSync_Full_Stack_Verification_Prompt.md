# CAMPXSYNC — FULL-STACK VERIFICATION RUN
### Vercel  →  Supabase  →  Application  →  UI  →  Go / No-Go

Paste into Antigravity. This is a **test-and-report run**, not a build phase. It proves what works, records what doesn't, and ends with a clear Go/No-Go for the Monday trial.

---

## THE PURPOSE

CampXSync Monday release = a real person can open a **live Vercel link**, sign in, see **only their own college's data**, and complete **one real workflow (attendance)**, on the real architecture, against **staging**.

This run answers one question: **"Is that true right now, and if not, exactly what is in the way?"**

## HOW THIS RUN BEHAVES (read first)

| Rule | Meaning |
|---|---|
| **One pass** | Run Stages 0 to 9 in order. Do not loop back unless a Blocker fix changes an earlier result. |
| **Two attempts max** | A failing check gets at most two honest fix attempts. Then log it and move on. |
| **Fix only Blockers** | Blocker = prevents login, the attendance flow, the live link, or breaks tenant isolation. Everything else goes to the defect log. |
| **Honest statuses** | PASS, FAIL, BLOCKED (needs my action), NOT RUN (feature not built yet). A skipped check is never a PASS. A feature that does not exist yet is NOT RUN, not FAIL. |
| **Staging only** | Every connection, deploy and variable must resolve to the staging project `kpwdvvcschankgggitnj`. Anything else, including an unknown target, is a STOP. Never print credentials. |
| **No surprises** | No commits or pushes. No schema changes or admin writes without my exact `GO` phrase. Application actions through the UI (adding a record, marking attendance) are allowed on the seeded demo colleges and must be labelled `TEST-RUN` where a free-text field exists. |
| **Short output** | Progress lines while working. Final report follows the template in Stage 9. Long output goes to `docs/trial/evidence/`. |

If something needs me (Vercel login, a dashboard setting), print the **exact manual action**, mark that check BLOCKED, and continue with everything independent.

---

## STAGE 0 — PREFLIGHT  *(5 min)*

| ID | Check | Expected |
|---|---|---|
| P-01 | Node, npm, `vercel` CLI, Playwright present | Versions printed; install if missing |
| P-02 | `vercel whoami` | Logged in. If not: BLOCKED, tell me to run `vercel login` |
| P-03 | Env file resolves to the **staging** project (compare against the allowed ref, no key printing) | Match |
| P-04 | `.env.local` is git-ignored; no secrets tracked in the repo | Confirmed |
| P-05 | Supabase MCP (if connected) points at staging only | Confirmed, note if read-only |
| P-06 | Test accounts available per role (from `docs/trial/TRIAL_GUIDE.md` or seed output); passwords come from env, never printed | List of roles available |

## STAGE 1 — LOCAL HEALTH  *(10 min)*

| ID | Check | Expected |
|---|---|---|
| L-01 | `npm run lint` and typecheck | Clean, or errors listed |
| L-02 | `npm run build` (production build) | Succeeds. A failing build is a Blocker: fix first |
| L-03 | `npm run dev`, open `/login` | Page renders, no console errors |
| L-04 | Sign in locally as each available role | Correct landing page per role |
| L-05 | Sign out | Session cleared, redirected to `/login` |

## STAGE 2 — SUPABASE LAYER  *(15 min, read-only)*

| ID | Check | Expected |
|---|---|---|
| S-01 | Auth settings reachable; email+password enabled | Yes |
| S-02 | Seeded auth users exist for each role; one **no-profile** user and one **inactive-membership** user exist (if not, mark NOT RUN) | Listed |
| S-03 | **Connection identity:** which database role does the app's server-side DB access use (`current_user`)? Superuser, table owner or BYPASSRLS? | Stated plainly |
| S-04 | **RLS proof (one pass):** inside a rolled-back transaction, with `SET LOCAL ROLE authenticated` and each identity's JWT claims, count students, faculty, timetable and attendance rows as: College A admin, College A faculty, College B admin, no-profile user, anon | B sees 0 A rows and vice versa; no-profile and anon see 0 |
| S-05 | Every API route / server action: tenant derived from the session, never from client input; role checked server-side | Table of routes with pass/fail |
| S-06 | Secret scan of app code and repo: `service_role`, DB passwords/connection strings, hardcoded emails, hardcoded tenant UUIDs, `rejectUnauthorized: false` | None found, or listed |
| S-07 | Record the Supabase region | Stated (needed for Stage 3) |

**If S-03 shows RLS is bypassed and S-04 fails:** classify as Critical, record "RLS NOT ENFORCED ON SERVER QUERIES" at the top of `KNOWN_LIMITATIONS.md`, propose the smallest fix (per-request transaction with `SET LOCAL ROLE authenticated` and the user's claims, or exposing the schemas with grants and RLS) to `supabase/migrations_proposed/`, and **continue the run**. I will decide fix-now or ship-with-limitation after the report.

## STAGE 3 — VERCEL DEPLOY  *(15–20 min)*

| ID | Check | Expected |
|---|---|---|
| V-01 | Link/create the Vercel project from the app root (confirm the root directory) | Project linked |
| V-02 | Environment variables set for **Production and Preview**, staging values only. Only `NEXT_PUBLIC_SUPABASE_URL` and the publishable/anon key are public. DB credentials marked sensitive; no service-role key as a public variable | Names listed in `docs/trial/VERCEL_ENV.md` (no values) |
| V-03 | Database connection string suitable for serverless (pooler / transaction mode) | Confirmed |
| V-04 | Function region set close to the Supabase region from S-07 (`vercel.json` `regions`) | Set, or documented why not |
| V-05 | Deploy with `vercel --prod`; wait for completion | Deployment READY, URL recorded |
| V-06 | Print the **exact Supabase Auth URL settings** I must apply: Site URL = the live URL; Redirect URLs = the live URL, preview pattern, `http://localhost:3000/**` | Printed. Mark V-07 BLOCKED until I confirm |
| V-07 | After I confirm, login works on the live URL | PASS / FAIL |

## STAGE 4 — LIVE SMOKE TEST  *(Playwright against the deployed URL)*

| ID | Check | Expected |
|---|---|---|
| E-01 | Unauthenticated visit to any protected route | Redirects to `/login` |
| E-02 | Each role signs in | Lands on its own home |
| E-03 | Each role's navigation shows only its allowed items | Verified |
| E-04 | Direct URL to another role's route | Friendly no-access page, no data shown |
| E-05 | No-profile and inactive-membership users sign in | Clear "access not available" message, no crash |
| E-06 | Reload while signed in | Session persists |
| E-07 | Sign out | Session cleared; Back button does not reveal protected pages |

## STAGE 5 — CORE JOURNEYS  *(on the live URL)*

| ID | Journey | Expected |
|---|---|---|
| J-01 | **Platform Admin:** dashboard and institutes list; onboarding wizard if built | Real data; otherwise NOT RUN |
| J-02 | **College Admin:** dashboard KPIs, departments, faculty, students, timetable | Values match direct counts from the source tables |
| J-03 | **College Admin:** add one faculty and one student (labelled `TEST-RUN`) | Confirmed by the backend, visible after reload |
| J-04 | **Faculty:** today's/next timetable slot, open attendance sheet, mark, submit | Persisted after reload |
| J-05 | **Faculty:** open the same slot again | Existing session opens; **no duplicate** created; double-click submit creates no duplicate |
| J-06 | **Faculty:** edit a submitted session | Blocked for faculty |
| J-07 | **College Admin:** attendance report and dashboard reflect the new session | Totals consistent |
| J-08 | **College Admin:** correct a submitted record with a reason (if built) | Reason required; history visible |

## STAGE 6 — TENANT ISOLATION ON THE LIVE APP

| ID | Check | Expected |
|---|---|---|
| T-01 | As a College A user, request College B record IDs through each API route and page URL (IDOR) | Denied or empty |
| T-02 | Send a forged tenant id (query, body, header) | Ignored or rejected |
| T-03 | Faculty calls admin-only endpoints | Denied |
| T-04 | Platform Admin views tenant data | Aggregates only, no student/faculty rows |
| T-05 | Attempt a cross-tenant write (insert/update) through the app | Rejected. Run only against the demo colleges; mark BLOCKED if it needs an admin write |

## STAGE 7 — UI QUALITY

Capture screenshots at **1440, 1024 and 390** widths for: login, each role home, faculty attendance sheet, dashboard, a table with data, an empty state, a no-access page and a 404. Save to `docs/trial/screenshots/`.

| ID | Check | Expected |
|---|---|---|
| U-01 | Consistent colours, type, spacing, buttons across pages (one design system) | Visual check with screenshots |
| U-02 | Loading, empty and error states exist on lists and dashboards | Verified |
| U-03 | Forms: validation, loading state, readable errors, success only after backend confirmation | Verified |
| U-04 | No dead buttons or links (crawl every nav item and primary button) | None, or listed |
| U-05 | No horizontal scroll or broken layout at 390px; attendance sheet usable on a phone | Verified |
| U-06 | Accessibility scan (`@axe-core/playwright`) on login, dashboard, attendance sheet | No serious or critical violations |
| U-07 | Browser console and network: no errors, no 4xx/5xx on normal journeys | Clean, or listed |
| U-08 | Professional look: would a college administrator trust this? Give an honest 1–5 score per role home with one-line reasons | Scored |

## STAGE 8 — SPEED SANITY

| ID | Check | Expected |
|---|---|---|
| F-01 | Load time of login, each role home, attendance sheet on the live URL (3 runs each, median) | Recorded; flag any over 3 seconds |
| F-02 | Slowest query/route and likely cause (region mismatch, missing index, N+1) | Noted, not fixed unless it is a Blocker |

## STAGE 9 — REPORT AND GO / NO-GO

Write `docs/trial/VERIFICATION_REPORT.md` (and print a 30-line summary):

```
CAMPXSYNC VERIFICATION REPORT — <date/time>
Live URL:          <url>              Build/revision: <id>
Environment:       staging confirmed (yes/no)

SCORECARD
Stage 0 Preflight     PASS x / FAIL x / BLOCKED x / NOT RUN x
Stage 1 Local         ...
Stage 2 Supabase      ...
Stage 3 Vercel        ...
Stage 4 Live smoke    ...
Stage 5 Journeys      ...
Stage 6 Isolation     ...
Stage 7 UI            ...
Stage 8 Speed         ...

TOP FINDINGS (max 8): ID | severity | what | proposed fix | effort (S/M/L)
OPEN BLOCKERS:  <none | list>
MANUAL ACTIONS NEEDED FROM ME: <exact steps>
KNOWN LIMITATIONS ADDED: <list>
```

Append every non-Blocker finding to `docs/trial/DEFECT_LOG.csv` (columns: `ID, Date, Reporter, Role Used, Page/URL, Steps To Reproduce, Expected, Actual, Severity, Screenshot Link, Status, Owner, Notes`) and every deferred idea to `docs/trial/POST_TRIAL_BACKLOG.md`.

### GO / NO-GO RULE

**GO for the Monday trial** when all of these are true:
1. The live URL is reachable and login works for every available role (V-07, E-02).
2. The attendance flow persists, with no duplicates (J-04, J-05).
3. Tenant isolation either PASSES (S-04, T-01, T-02) **or** is recorded as a documented limitation that I have explicitly accepted, and the trial uses demo data only.
4. No secret is exposed (S-06).
5. No Blocker remains open.

**NO-GO** otherwise. List exactly which check failed and the smallest next action.

---

BEGIN WITH STAGE 0. Print one progress line per check. Do not wait for me unless a check is BLOCKED on a manual action.
