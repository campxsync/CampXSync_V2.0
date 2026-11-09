# Copy/Paste Prompt for Antigravity — CampXSync Login Performance

Use the existing GSD workflow for this task. Read `README_START_HERE.md` and `PERFORMANCE_TASK_BRIEF.md` first.

## Non-negotiable instruction
**Resume the existing CampXSync project state. Do not initialize a new GSD project. Do not restart phases or repeat completed audits. Do not lose or overwrite current work.**

### Step 1 — Recover context before acting
1. Confirm the repository root, active branch, and `git status`. Preserve every existing change; do not reset, clean, stash, checkout over, or discard anything.
2. Read the existing `MASTER_EXECUTION_PROMPT.md` / v4 and follow its stricter instructions.
3. Inspect the existing GSD extension/runtime and identify its exact version and supported commands from its own help output. Do not guess command syntax, reinstall it, or initialize a new project.
4. Locate and read the existing GSD state and checkpoints, including relevant files if present: `.planning/STATE.md`, `docs/staging/00-checkpoint.md`, `docs/staging/04-completion-criteria.md`, `docs/staging/05-execution-log.md`, `docs/handover/HANDOVER.md`, and the existing GSD plan/roadmap/config files. Paths may differ; inspect rather than create duplicates.
5. Read relevant login scope and product lifecycle documents. Resolve the exact existing filenames from the repository.
6. Briefly state the current checkpoint, existing work relevant to login performance, GSD command/mode you will use, and the next action. Do not produce a broad audit or a long plan.

### Step 2 — Create durable task records
Create or update only the focused performance task records, without overwriting existing content:
- Add a task brief or link this supplied `PERFORMANCE_TASK_BRIEF.md` into the existing docs structure.
- Add a dated execution log entry for the performance investigation.
- Update the existing GSD task/state/checkpoint when a real task begins and after meaningful milestones.
- Record: hypothesis, evidence, timings, changes, tests, and exact next action.
- If the repository already has a canonical equivalent, update that file instead of creating duplicate tracking systems.
- Do not commit the supplied files or repository changes unless existing approval rules permit it.

### Step 3 — Measure first; do not guess about caching
Investigate the approximately six-second delay reported by the user. Confirm the current Preview URL from current repository/deployment evidence. Do not assume old Preview URLs are current.

Measure and separate:
- Login click → first visible UI response.
- Supabase Auth request duration.
- Auth success → profile/role/tenant resolution.
- Redirect and route navigation.
- Route navigation → dashboard usable.
- Dashboard data-fetch/render time.

Use browser Network/performance tooling already available. Compare a first attempt with at least two repeat attempts. Capture request type/path, status, duration, and whether the run is cold/repeat where observable. Redact secrets, cookies, tokens, and unnecessary personal data. Test Platform Admin, College Admin, and Faculty flows without exposing passwords.

Identify the exact slow request or code path. If timings cannot be captured, state the precise missing evidence and ask for only the minimum needed; do not invent numbers or make speculative code changes.

### Step 4 — Apply the smallest evidence-based fix
Only after the root cause is supported by measurements, make the minimal targeted fix. No unrelated refactoring. No blind cache additions.

Preserve:
- Supabase authentication and session semantics.
- Role resolution, route guards, tenant scoping, and RLS.
- Strict isolation among Platform Admin, College Admin, Faculty, and tenant data.
- All previously working login flows and local work.

Never cache user/role/permission/tenant authorization across users or sessions. Never make a route appear authenticated before the actual auth check succeeds.

### Step 5 — Validate and update GSD
Run focused tests and the frontend build. Repeat the same measurements under comparable conditions. Verify Platform Admin `/platform`, College Admin dashboard, and Faculty `/faculty`. Clearly distinguish pass, fail, skipped, and not run.

Update the existing GSD state/checkpoint and execution log with:
- Before/after timings and sample counts.
- Measured root cause.
- Exact files changed.
- Test/build results.
- Current Preview URL and commit only if verified.
- Remaining blockers and one exact next action.

### Safety constraints
- Production Supabase ref `ekspahbsbwdpraaqfqui`: **NEVER CONNECT, QUERY, OR MODIFY**.
- Staging ref `kpwdvvcschankgggitnj` only if necessary and only under the existing approval rules.
- This prompt is not approval for any database write, account creation, password reset, seed, migration, commit, push, or deployment.
- No secrets in source, logs, screenshots, reports, or Markdown.
- Do not weaken security controls to improve speed.
- Do not perform Claude's independent gap analysis yet. That is the next agreed step after performance work.
- Do not start student login/dashboard work yet.
- Do not finish with a report-only result: implement a demonstrated safe fix where possible and provide evidence. If blocked, name the precise blocker and the minimum action needed.

Begin by reading the existing state and verifying the GSD command syntax. Then proceed in step-by-step mode, pausing before any action that requires user approval.
