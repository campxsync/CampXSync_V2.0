PHASE 5 — CYCLE 2 (Track B): COMMIT, TOOLING, END-TO-END READINESS TRACE. Do not restart earlier phases or redo MVP-ADM01-02/03/04. Follow docs/MASTER_EXECUTION_PROMPT.md and all safety rules. Use GSD. Windows/PowerShell.

READ ONLY: the CURRENT STATE block of docs/handover/HANDOVER.md, .planning/STATE.md, .planning/workstreams/track-b/STATE.md, docs/mvp/02-backlog.md, docs/mvp/03-progress.md.

STEP 0 — VERIFY BEFORE COMMITTING (read-only)
Run and show me, in the report file (not in chat): `git status --porcelain`, `git diff --stat`, `git diff --staged --stat`, and `git log --oneline -5`.
Cycle 1 proposed one commit labelled MVP-ADM01-04 that also listed RoleTemplateControllerHttpTest.java and InstituteAdminController.java, but the cycle 1 log shows no edit to either. Establish from git (not from memory) which files belong to MVP-ADM01-02, MVP-ADM01-03, MVP-ADM01-04 and docs/planning. Report any file whose owner task is unclear. Report whether any of these are present and uncommitted: target/ output, surefire dump files, scratch/ files, .env* files, credentials. Never stage them.

[OWNER APPROVAL BLOCK — keep it only if you approve; otherwise delete it and the agent must wait]
I approve LOCAL commits only on ReviewBranch, as separate commits, in this order, after you show me the exact `git add` file lists and I see no unexpected files:
 1. feat(adm-01): role template authorization and validation suite (MVP-ADM01-02)
 2. feat(adm-01): role template HTTP endpoints, outbox and audit (MVP-ADM01-03)
 3. feat(adm-01): tenant provisioning state machine with authorized retry (MVP-ADM01-04)
 4. docs(mvp): backlog, progress, handover archive and GSD state
No push, no amend, no rebase. Use explicit file paths with `git add`, never `git add .` or `-A`.
[END APPROVAL BLOCK]

STEP 1 — TOOLING FIXES (small, no product code)
a. Add scripts\rule11-check.ps1 (I am supplying the file; do not rewrite its logic). It prints booleans only and exits 0 on pass, 1 on fail. From now on every mvn command must be run as:
   `.\scripts\rule11-check.ps1; if ($LASTEXITCODE -eq 0) { mvn <args> } else { Write-Output "Rule 11 failed; mvn not run" }`
   Do not create further ad-hoc env-check scripts. If a check fails, stop and report which one.
b. Add `scratch/` to .gitignore. Do not delete anything in scratch/.
c. Build hygiene: if the compile step shows stale classes again, run `mvn clean test-compile` instead of editing file timestamps.
d. If a surefire .dump or .dumpstream file exists from the cycle 1 run, report its name and whether it indicates a JVM fork crash. Make sure target/ is ignored.

STEP 2 — MVP-E2E-00: END-TO-END READINESS TRACE (read-only, no database access)
Owner requirement (2026-10-14, see backlog section 0.1 and 2b): features must be proven end to end, browser UI to gateway to service to Supabase staging, not only by offline tests with in-memory repositories. Role-template UI is now REQUIRED (ADM01-05). Do not implement MVP-ADM01-06 this cycle; it moves to a later cycle.
Produce docs/mvp/04-e2e-readiness.md. Trace the real path with file and line evidence (label VERIFIED / REPORTED / ASSUMED / UNKNOWN):
1. Frontend: which tabs in frontend-web/src/App.jsx call real APIs through api.js and the Vite proxy, and which use mock data or the mock principal CAMPX_ADMIN. Which journeys could a real browser run write to a database today?
2. Authentication: how the UI authenticates today; what real Supabase Auth login would need (test users, Custom Access Token hook, token storage, how the token reaches the gateway).
3. Gateway: api-gateway routing to each service, how identity and tenant propagate (JWT passthrough or service identity), CORS and ports.
4. Services: for institute-admin-service, college-admin-service and course-management-service, how persistence mode is chosen (Postgres vs in-memory), the fail-fast behavior, the environment variable NAMES needed to start against staging (names and set/unset only, never values), and the exact start commands.
5. DB identity: confirm the service connects as the restricted authenticated identity with claims injected (SET LOCAL ROLE authenticated), not a privileged role.
6. Verification path: for one candidate slice, the read-only SELECT statements (numbered, SELECT-only, in a file docs/mvp/04-e2e-verify.sql for me to run in the staging SQL Editor) that would confirm the resulting row, audit record and outbox row.
7. Recommend the simplest first slice (an existing UI tab) and the second slice (role templates), with reasons.
Classify every blocker as CODE, CONFIGURATION, AUTH, DATA FIXTURE, OWNER ACTION or STAGING WRITE, and end with a numbered list of the minimum changes and owner actions needed to run slice 1. Include a proposed text for docs/staging/e2e-approved.md (a policy allowing only app-mediated writes by the running application, as the restricted authenticated role, inside the CAMPX_STAGING test tenant); do not treat it as approved until I say so.
Do not start any service against staging, do not call the Supabase MCP, do not run SQL, do not change source code in this step. If a fact cannot be established from the repo, mark it UNKNOWN.

RUN RULES
- No mvn command is needed unless you must verify a claim; if you run one, use the Rule 11 wrapper above.
- CodeRabbit: cycle 1 reported "API failed on unpushed local branch" and then listed the same failing command for me to run, so it has NOT run. After the local commits, run `coderabbit review --help`, then try again with a --base that exists on the remote if one does. Report the exact non-secret error text. If it still fails, say plainly that CodeRabbit cannot run until the branch is pushed (push needs my approval). Do not describe a failed attempt as a completed review.
- No staging DB access, no writes, no push, no branch switch, no deletion.

RECORD
Update docs/mvp/03-progress.md (cycle summary, and for MVP-ADM01-04: whether a retry attempt count or failure reason is stored or only emitted via audit/outbox, whether any schema change was needed, whether the retry is exposed over HTTP, and which test proves the concurrent double-retry), .planning/STATE.md, and the CURRENT STATE block in HANDOVER.md. Mirror the new E2E tasks and the replaced backlog into the GSD roadmap.

CLOSE
Chat summary max 25 lines with file paths, then up to 5 questions in one message. STOP. Do not start MVP-E2E-01 or any implementation task.
