PHASE 5 — CYCLE 1 (Track B). Do not restart Phases 0–4. Follow docs/MASTER_EXECUTION_PROMPT.md, including all safety rules. Use GSD for planning and state. Windows/PowerShell.

READ ONLY THESE FIRST (do not re-read the whole master prompt repeatedly):
- docs/handover/HANDOVER.md (latest section only)
- .planning/STATE.md and .planning/workstreams/track-b/STATE.md
- docs/mvp/02-backlog.md (revised 2026-10-14; contains owner decisions in section 0.1)
- docs/mvp/03-progress.md (revised 2026-10-14)

STEP 0 — HOUSEKEEPING (documentation only, no source changes)
a. I am replacing docs/mvp/02-backlog.md and docs/mvp/03-progress.md with revised versions. Do not overwrite them. Confirm their content matches the files in the workspace and mirror the revised task IDs and statuses into the GSD roadmap and .planning/STATE.md.
b. In docs/handover/HANDOVER.md, keep a CURRENT STATE block at the top (max 40 lines). Move the Phase 0–3 sections to docs/handover/archive/ (copy then trim; do not delete anything without asking me).
c. Run read-only `git status` and prepare a commit proposal grouped by workstream (source, tests, docs, planning). Do NOT commit. Show me the file list and a proposed message, then continue.

STEP 1 — IMPLEMENT ONE TASK: MVP-ADM01-04, Tenant Provisioning State Machine with authorized retry
Scope and owner-approved rules are in backlog section 0.1 and the ADM01-04 entry. Summary:
- Allowed: REQUESTED→IN_PROGRESS, IN_PROGRESS→COMPLETED, IN_PROGRESS→FAILED.
- FAILED→IN_PROGRESS only via a distinct, authorized retry operation (platform admin only) that records the retry (who, when, attempt, prior failure reason) through the existing audit and outbox mechanisms.
- COMPLETED→any other state rejected. All other pairs rejected with InvalidStatusTransitionException.
- One transition table in one place. Postgres repository uses conditional updates (expected current status in the WHERE clause, check affected rows); a lost race returns a conflict, never a silent success. In-memory repository must give the same semantics under concurrency and must not share state across tenants.
- Tenant only from the security context. Respect deleted_at. RLS intact. NO schema, migration or function changes; if the retry record needs one, stop that part, document why, and record the retry in audit/outbox only.
Files: TenantProvisioningRepository, PostgresTenantProvisioningRepository, InMemoryTenantProvisioningRepository, TenantProvisioningValidationTest (new), plus the minimal controller or service change if the existing code exposes status updates over HTTP (check first).

Tests required: every legal transition; every illegal pair; COMPLETED immutability; FAILED→IN_PROGRESS rejected by plain update but allowed via retry; retry recorded; non-platform-admin denied; concurrent double-retry (one succeeds, one conflicts); soft-deleted record; one ADM-01 vs ADM-02 boundary check (a college-admin identity cannot trigger provisioning transitions).

RUN RULES
- Before any mvn command, run the Rule 11 boolean environment check and print only True/False. If any check fails, do not run the command; report which check failed.
- Run the targeted test class first, then the ADM-01 offline suite (`mvn test "-Dtest=!Postgres*Test,!*IntegrationTest"`). Report ran / passed / failed / skipped separately. A skipped test is not a pass.
- Review your own diff for authorization, tenant isolation and concurrency regressions.
- CodeRabbit: run it if available here; otherwise give me the exact command to run. Apply the same to MVP-ADM01-02 and MVP-ADM01-03, which have not been reviewed.
- Browser UI: this task has no UI. Report the browser status as NOT IMPLEMENTED with the cause code. Do not report a page-load check as a journey PASS.
- No commit, push, branch switch, stash or deletion. No staging DB access or writes in this cycle.

RECORD
- Update docs/mvp/03-progress.md: task, files changed, acceptance criteria met/unmet, tests ran/passed/failed/skipped, CodeRabbit status, status = DONE-LOCAL / LIVE VERIFICATION PENDING STAGING if it touches the database write path, next task.
- Update .planning/STATE.md and append a short Phase 5 Cycle 1 section to HANDOVER.md (update the CURRENT STATE block).

CLOSE
Post a chat summary of at most 25 lines with file paths, the commit proposal, and any questions (max 5, one message). Then STOP. Do not start MVP-ADM01-06 or any other task.
