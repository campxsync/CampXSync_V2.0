# CampXSync Performance Task — Login Delay (~6 Seconds)

**Status:** Ready to investigate  
**Priority:** High  
**Scope:** Measure, diagnose, and fix the demonstrated login-path bottleneck only  
**Owner:** Antigravity agent, with user approval for gated actions

## 1. Current objective
The user manually tested CampXSync and observed that the login flow now takes approximately **6 seconds**. Determine exactly where the time is spent and apply the smallest safe fix supported by evidence.

Do not assume caching is the cause. Possible causes include Supabase Auth latency, repeated session/profile/role requests, redirects or middleware, dashboard data loading, render blocking, network latency, and Vercel cold starts.

## 2. Existing context to preserve
- Repository: `campxsync/CampXSync_V2.0`
- Branch: `ReviewBranch`
- Frontend: `frontend/`
- Vercel project root must remain `frontend`
- Staging Supabase ref: `kpwdvvcschankgggitnj`
- Production Supabase ref: `ekspahbsbwdpraaqfqui` — never access or modify.
- Preserve existing working Platform Admin, College Admin, and Faculty login flows and the faculty staging linkage.
- The user manually confirmed that Faculty login worked and reached the Faculty Teaching Workspace/timetable after the staging linkage.
- Separate test credential variables already exist for College Admin, Platform Admin, and Faculty. Never print or reveal their values.
- Existing master prompt, GSD workflow, repository state, local changes, tests, docs, and staging checkpoints take precedence over this brief if they impose stricter controls.

## 3. Measurement plan
Use existing browser/IDE tooling and the deployed Preview that is currently associated with the branch. First confirm the current Preview URL from the existing deployment evidence; do not assume a historical URL is current.

Measure at least:
1. Time from Login click to the first visible response.
2. Supabase Auth request start/end and result.
3. Time from auth success to role/profile resolution.
4. Redirect/navigation duration.
5. Time from route navigation to the dashboard being usable.
6. Major dashboard data requests and render completion.

Test representative roles without exposing credentials:
- Platform Admin → `/platform`
- College Admin → college dashboard
- Faculty → `/faculty`

Compare a first attempt with at least two repeat attempts. Record whether each attempt is a cold or repeat load where evidence allows. Capture request timing evidence (URL path or request type, status, duration; redact tokens, cookies, emails if not needed, and all secrets). Use the median and slowest observed duration; clearly state sample size and conditions.

Do not claim a bottleneck based on intuition. Identify the specific slow request, code path, or runtime behavior that explains the delay.

## 4. Safe fix boundaries
After the cause is demonstrated:
- Make the smallest targeted change.
- Avoid duplicate requests and unnecessary sequential waits where evidence supports it.
- Do not add cross-user or cross-tenant caching for profile, role, membership, permissions, or tenant data.
- Do not cache authorization decisions in a way that survives a user/tenant/session change.
- Do not bypass Supabase Auth, role checks, RLS, tenant scoping, or route guards.
- Do not weaken error handling or make the UI appear authenticated before auth has succeeded.
- Do not add new infrastructure or dependencies unless the evidence makes it necessary and the user approves.
- Do not change unrelated UI, modules, schemas, RLS policies, or login requirements.

## 5. Safety and approval rules
- Never connect to, query, or modify production Supabase ref `ekspahbsbwdpraaqfqui`.
- Staging ref `kpwdvvcschankgggitnj` only, and only when needed.
- Follow the existing `MASTER_EXECUTION_PROMPT.md` / v4 approval rules for every database write. A prompt in this file is not database-write approval.
- No user creation, password reset, seed execution, schema migration, or other database write without the specific existing approval phrase and exact staging verification required by the project instructions.
- Do not commit, push, or deploy unless the existing project approval rules authorize that exact action.
- Do not reset, clean, stash, discard, or overwrite local work. Inspect Git status and preserve all current changes.
- Never print secrets, access tokens, passwords, or `.env.local` values.

## 6. Validation
After a targeted fix:
- Run focused tests for the changed path.
- Run the frontend build using the project's existing command.
- Re-run the exact same timing observations and compare before/after.
- Re-test all three roles sufficiently to detect regressions, including the Faculty route.
- Report any checks that could not run and why; do not present skipped tests as passed.

## 7. Completion criteria
This task is complete only when the evidence includes:
- Before timings and after timings, with sample count and test conditions.
- Identified root cause supported by Network/performance/code evidence.
- Exact files changed and why.
- Focused test/build results.
- Confirmation that Platform Admin, College Admin, and Faculty login/routing remain functional.
- Current Preview URL/commit only if verified; otherwise state that deployment was not performed.
- Updated GSD state/checkpoint and execution log, plus this task log.
- Any remaining blocker stated explicitly.

## 8. Out of scope
- Independent Claude gap analysis (do this after performance work).
- Student login/dashboard work (later in the agreed sequence).
- Broad security audit, architecture redesign, new module work, or re-auditing completed phases.
