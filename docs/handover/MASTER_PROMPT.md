# CampXSync — Staging Completion + MVP Delivery (GSD-driven, phase-gated)

## 0. HOW TO RUN THIS
You are the technical lead for CampXSync.
Workspace: d:\CampXSync\CampXSync_V2.0 | Branch: ReviewBranch | OS: Windows (use PowerShell syntax, backslash paths).
Database: HOSTED Supabase (PostgreSQL). Do not use Docker or a local Supabase instance.

Use GSD for ALL planning, execution and state tracking. Use whichever GSD commands this installation provides (progress / plan-phase / execute-phase / verify-work or equivalents). Keep GSD state in .planning/ with two separately tracked tracks:
- TRACK-A: Staging setup and readiness
- TRACK-B: MVP implementation and validation

Work in PHASES. At the end of each phase:
1. Write outputs to files (docs/audit/, docs/staging/, docs/mvp/, docs/handover/), NOT as giant chat replies.
2. Update .planning/STATE.md.
3. Post a SHORT chat summary (max 25 lines) with file paths.
4. STOP and wait for my reply. Never start the next phase on your own.
Do one phase per session. If context gets long, write a handover to docs/handover/HANDOVER.md and stop.
Ask me questions only when a specific missing decision, permission, credential or dashboard action genuinely blocks the next step. Maximum 5 questions per message, in one message.

## 1. EVIDENCE AND SCOPE SOURCES (discover them yourself; do not ask me for paths)
Search the workspace (and d:\CampXSync\ if it is the parent folder) for the following. Do not assume paths. Report what you found and what is missing.
- Prior agent history, SQL run notes, handover files, CodeRabbit reports, test reports: look in docs/, .planning/, reports/, and any *handover*, *report*, *coderabbit*, *execution* files.
- Bootstrap SQL scripts: 00_extensions_schemas, 10_early_helpers, 20_tables, 30_foreign_keys, 40_functions, 45_partitions, 50_triggers, 55_scope_functions, 60_rls, 70_views, 75_grants, 80_seed, 85_auth_hook (and any rollback/migration scripts).
- MVP scope sources, by name or content: CampXSync ADM-01 Institute Admin and ADM-02 College Admin architecture/DDD docs, the ACD (academic) module docs, the "CampXSync 10,000-College Multi-Tenant Scalability Architecture" doc, relational_constraint_index_catalog.md, the Core Components Architecture DDD, any requirements/roadmap/backlog/user-story files (CSV or MD), and existing master execution prompts for ACD-01, ADM-01, ADM-02.
- ADM-01 "STEP 1" (User Profile Management on iam.user_profiles; endpoints GET/POST /api/v1/admin/users, GET/PUT /api/v1/admin/users/{id}, PATCH /api/v1/admin/users/{id}/status; Supabase Auth only, no new users table, existing IAM role/permission/grant_scopes model, RLS intact, tenant from security context only, respect deleted_at; role assignment and invitation/provisioning deferred). Determine its actual implementation state from code and tests.
PRECEDENCE: this prompt overrides any earlier master prompt for ORDERING. ACD-01, ADM-01 and ADM-02 are Track B (MVP) tasks and must NOT replace or delay staging work (Track A). If they are partly done, record their real status; do not redo completed work.
If the MVP scope is genuinely unclear after reading these sources, list the exact questions (max 5) in one message and wait. Do not invent scope.

## 2. ABSOLUTE SAFETY RULES
1. PRODUCTION project ref ekspahbsbwdpraaqfqui: never connect to, query, migrate, seed or run anything against it, including read-only queries. STAGING project ref is kpwdvvcschankgggitnj.
2. Never trust a variable name containing "STAGING". Positively identify the target from the non-secret project ref (in the URL, host, or pooler username of the form postgres.<ref>) before ANY database operation. If you cannot, stop that DB step, state the blocker precisely, and continue other work.
3. SECRETS: never open and print, copy, or echo values from .env*, application-*.properties/yml/yaml, or any credentials file into chat, reports, logs or commits. When inspecting config, list variable NAMES and whether they are set/unset only. If a file contains secrets, extract only the non-secret project ref. Do not paste connection strings, passwords, service-role keys, JWT secrets or access tokens anywhere.
4. Run a read-only repo search for the production ref string and report only file paths and line numbers (never surrounding secret values). Any occurrence in config/code/tests that the application or tests could use is a defect to report.
5. No commit, push, merge, rebase, reset, checkout/switch branch, stash, file deletion or overwrite of existing work without my explicit approval. ONE EXCEPTION, allowed only in Phase 0: create a backup branch at current HEAD with `git branch backup/<yyyy-mm-dd>-pre-audit` (do not switch to it) and save `git status --porcelain`, `git diff` and `git diff --staged` outputs into docs/audit/backup/. Make sure those saved diffs contain no secrets; if a diff touches a secrets file, exclude that file and note it.
6. No destructive or irreversible DB change (drop, truncate, reset, delete, schema wipe) without my explicit approval.
7. Never rerun 80_seed or any non-idempotent script without (a) evidence of current database state and (b) my approval. Seed-role duplication has happened before.
8. Never bypass RLS, tenant isolation, authorization or environment guards to make a test pass. Never add shared in-memory fallbacks that can leak data between tenants.
9. STAGING DB WRITES REQUIRE THIS EXACT RITUAL EVERY TIME:
   (a) state the verified project ref (non-secret) and how you verified it,
   (b) state the exact script or statement and why it is needed,
   (c) wait for me to reply "GO <script or step name>".
   One approval covers exactly that one step.
10. No production data or credentials for any development or testing.
11. Before running ANY mvn/test/app command, run a boolean-only environment check (SUPABASE_USER, SUPABASE_JDBC_URL, CAMPX_SUPABASE_JWT_ISSUER, CAMPX_TEST_ENV): print only whether each contains the staging ref and whether it contains the production ref, never the values. If any production check is True, or the staging check is False where a ref should be present, do NOT run the command; report it and stop.

## 3. EVIDENCE RULES
Label every claim: VERIFIED (you directly observed it: file, diff, command output, or SQL results I pasted), REPORTED (stated in a prior report or by another agent), ASSUMED, or UNKNOWN.
- "Success. No Rows Returned" proves only that the statement ran without error. It does not prove the intended objects exist.
- A skipped test is not a pass. A generated report is not completion. A successful exit code is not completion.
- Do not fabricate files, commands, database state, test results or progress.
- Do not repeat checks merely to create more reports. Do not restart completed work without evidence it is needed.
- If only one task was performed at a time, report the real sequence; never claim parallel execution that did not happen.
- Do not redesign working architecture without a concrete defect or requirement.

## PHASE 0 — PREFLIGHT (read-only, except the allowed backup in rule 5)
- Confirm repo root, current branch, HEAD, git status, staged/unstaged/untracked files, ahead/behind, local branches.
- Perform the allowed backup (rule 5).
- Confirm tooling: Java and build tool (Maven/Gradle wrapper) work on this Windows machine, test commands exist, whether CodeRabbit CLI is available here or needs WSL. Do not install anything.
- Confirm .planning/ exists and GSD commands work; create the Track A / Track B structure in .planning/ if missing.
- Output: docs/audit/00-preflight.md. STOP.

## PHASE 1 — AUDIT (strictly read-only) → files
Create:
1. docs/audit/01-change-inventory.md — every discoverable changed/added/deleted/renamed/untracked file relevant to CampXSync (Java source, services, endpoints, DTOs, repositories, config, tests, SQL scripts/migrations, RLS and permission functions, seeds, environment guards, build/CI, docs, generated reports), grouped by workstream. For each file: path, change type, what changed (functions/classes/policies/SQL objects), purpose, evidence, git status (committed / staged / uncommitted / reverted / unknown), tests and ACTUAL results (ran vs skipped), current status (complete/partial/pending/failed/blocked), follow-up. State honestly any limits of what can be reconstructed. Do not present superseded implementations as current.
2. docs/audit/02-prior-fixes.md — for each of: local policy authorization bypass; phantom-college fallback; cross-tenant in-memory fallback leakage; seed-role duplication; Supabase schema and bootstrap work; environment guard changes; migration and rollback scripts; RLS policy changes; service persistence changes; skipped live DB tests; CodeRabbit findings and their resolutions; anything another agent reported as complete. For each: original issue, code/config changed, present in current working tree?, committed?, validating tests, did the tests run or skip?, remaining work.
3. docs/audit/03-git-status.md — branch, relevant commits, diffs against appropriate baseline, uncommitted work, related branches.
Reuse existing evidence. Do not rerun the whole test suite just to produce a report; run only targeted checks needed to verify a specific claim.
STOP.

## PHASE 2 — STAGING AUDIT + VERIFICATION PACK (you cannot access my Supabase dashboard or SQL Editor history; do not run any DB command)
1. docs/staging/01-bootstrap-table.md — for each script in the 13-script sequence: exact path, purpose, dependencies, objects it creates (parsed from the SQL), idempotent? (yes/no/why), execution evidence (VERIFIED/REPORTED/UNKNOWN), recorded errors, next action. Previously, scripts through 75_grants, then 80_seed and 85_auth_hook, were reported as executed: treat that as REPORTED until verified.
2. docs/staging/verify-readonly.sql — a SELECT-ONLY script I will run in the STAGING SQL Editor, derived from the real scripts. Number every query so I can paste results back by number. Cover: extensions; expected schemas (the platform uses 20 schemas such as ACD, AUDIT, BUD, CAM, CFG, CORE, DOC, EXM, FIN, HRM, IAM, LMS, OBE, PAY, PLAT, RPT, STU, SYS, TAX, WF; use the actual names from the scripts); expected tables with row counts; PK/unique/FK counts compared with relational_constraint_index_catalog.md if present; indexes; functions including scope functions and the auth hook function with its exact signature; triggers; partitioned tables and child partitions; RLS enabled/forced per table; pg_policies per table; views; grants and EXECUTE privileges (including the auth hook function for the Supabase auth admin role); seeded tenants and roles with duplicate detection (GROUP BY … HAVING COUNT(*) > 1); any bootstrap/migration log table. No INSERT/UPDATE/DELETE/DDL anywhere in this file.
3. docs/staging/02-manual-checklist.md — steps only I can do in the Supabase STAGING dashboard, each with exact click-path and how to confirm: (a) browser URL shows project ref kpwdvvcschankgggitnj; (b) Authentication → Hooks: Custom Access Token hook enabled and pointing to the hook function from 85_auth_hook; (c) staging test users/tenant exist in Auth and in the app tables; (d) JDBC connection details for the staging POOLER (host, port, username format postgres.<ref>). Explain direct connection vs pooler (IPv4/IPv6), and session mode (port 5432 on the pooler) vs transaction mode (port 6543, prepared-statement issues), and recommend which to use for the Java tests.
4. docs/staging/03-config-audit.md — inspect services/course-management-service/src/test/java/com/campx/academic/course/config/TestEnvironmentGuard.java and related config WITHOUT exposing secrets. Report: does the guard truly block the production ref (read the logic; if feasible add/identify a unit test that feeds the production ref and expects rejection, using dummy credentials only)? Does it accept the pooler-style URL and username? List required env variable NAMES (JDBC URL, DB user, DB password, staging-environment flag, test tenant id, etc.), which are set/unset (do not read values), and where each belongs (shell env, .env.local, properties file). Report why existing tests are skipped and whether each skip is documented.
5. docs/staging/04-completion-criteria.md — a concrete pass/fail checklist with at minimum: staging ref assertion test; required schemas/tables/functions present; RLS denies unauthenticated/anon access; tenant A cannot read or write tenant B; auth hook claims present in an issued token; service-level read/write inside one tenant works; seed data has no duplicate roles; guard blocks production ref; application starts and connects to staging.
6. Classify every readiness item: VERIFIED COMPLETE / REPORTED COMPLETE — NOT VERIFIED / PARTIALLY COMPLETE / BLOCKED / NOT STARTED / UNKNOWN.
7. docs/staging/00-checkpoint.md — answer directly: last staging step proven successful; next unfinished step; exact blocker (file, variable, command, SQL object or dependency) and whether it is code, configuration, database, credentials, permissions or external access; minimum action to resolve; which completed steps must NOT be repeated.
In chat tell me: "Run docs/staging/verify-readonly.sql on STAGING, complete docs/staging/02-manual-checklist.md, and paste results by query number." STOP.

## PHASE 3 — STAGING COMPLETION (starts after I paste results)
- Reclassify readiness using ONLY what I pasted; anything I did not supply stays UNKNOWN.
- Propose the minimal remaining steps in order, add them to Track A in GSD, and execute one at a time. For anything that writes to the staging database, use the rule 9 ritual. Where you cannot run something yourself, give me the exact SQL or command and wait for the pasted output.
- Create any missing tests from 04-completion-criteria.md and make them runnable against staging with the environment guard ON. Run them only after I confirm the environment variables are set in my shell (you never see the values). Do not weaken the guard.
- Do not retry a failed step without new evidence; diagnose first. Do not rerun completed steps.
- Staging is COMPLETE only when every item in 04-completion-criteria.md has VERIFIED evidence and no production access occurred. Otherwise report INCOMPLETE or BLOCKED with the exact dependency.
- Output: docs/staging/05-execution-log.md (step, evidence, result), update .planning/STATE.md. STOP.

## PHASE 4 — MVP BASELINE AND BACKLOG (Track B; may run any time after Phase 1; independent of staging)
- Read the scope sources from section 1.

### MVP PRIORITY ORDER (user-defined, binding)
The agreed MVP modules are ADM-01 (Institute/platform admin), ADM-02 (College/tenant admin) and ACD-01, in that default order unless the ADM/ACD documents show a hard dependency that requires another order (state the dependency with evidence if you reorder).
For ADM-01 and ADM-02:
1. Derive the step sequence from their architecture docs and the existing step prompts (ADM-01 Step 1 = User Profile Management on iam.user_profiles; role assignment and invitation/provisioning are later steps). Create one GSD phase per step per module, each with its own acceptance criteria and tests.
2. Determine actual status of every step from code, tests and git history. Mark each step: DONE (code + passing tests), PARTIAL, NOT STARTED, or UNKNOWN. Never redo DONE steps.
3. Reuse the existing IAM role/permission/grant_scopes model and Supabase Auth. Do not create new user or RBAC tables. No schema, migration or function changes without my explicit approval; if a step seems to need one, stop that step, document why, and move on.
4. Tenant comes only from the security context. Respect deleted_at. Keep RLS intact. ADM-01 (platform tier) data and ADM-02 (tenant tier) data must stay isolated from each other; add regression tests for that boundary.
5. Steps that need live database proof (RLS behaviour, auth hook claims, cross-tenant denial) are implemented and unit/integration-tested locally now, then marked "LIVE VERIFICATION PENDING STAGING" in docs/mvp/03-progress.md. Re-run them once Track A is COMPLETE. Do not mark such a step fully DONE before that.
6. In docs/mvp/01-baseline.md include a table: module, step, status, files, tests (ran/skipped), live-verification status.

- docs/mvp/01-baseline.md — per module: working / partial / stub / mock-only / missing persistence / missing authorization or tenant checks / test coverage and whether tests actually ran. Include real status of ACD-01, ADM-01 (including Step 1 User Profile Management) and ADM-02.
- docs/mvp/02-backlog.md and mirror into the GSD roadmap/phases — per task: ID, module, current state, exact files, remaining work, dependencies, staging needed? (name the actual technical reason, otherwise classify as local), acceptance criteria, tests required, priority, effort with assumptions, risks. Classify: can proceed now / can proceed with local test infrastructure / blocked by staging / release-critical for the agreed MVP.
STOP.

## PHASE 5 — MVP IMPLEMENTATION LOOP (one task per cycle)
- Pick the highest-priority task (following the MVP PRIORITY ORDER) that does not need staging. Plan it with GSD. Implement the smallest complete deliverable. Add meaningful tests, including tenant-isolation and authorization regression coverage. Run targeted tests first, then the module suite when practical. Review the diff for regressions, tenant isolation, authorization and security.
- Run CodeRabbit if available (otherwise give me the exact command to run). Fix material findings; rerun affected tests.
- Do not commit. Present the changed-file list and diff summary, and ask me to approve a commit message.
- Record in docs/mvp/03-progress.md: task, files changed, acceptance criteria met/unmet, tests passed/failed/skipped, CodeRabbit findings and resolutions, remaining release-critical work, next task.
- Never mark a task complete if it is a stub, lacks persistence, or fails its acceptance criteria. If blocked by an unexpected dependency, record the evidence and move to the next independent task.
STOP after each task.

## PHASE 6 — MODULE MIGRATION CONTINUATION (only after Track A is COMPLETE)
Module by module: inspect current implementation and DB needs → write migration and code changes → apply to STAGING only, using the rule 9 ritual → targeted and integration tests → CodeRabbit review → fix material findings and add regression tests → report real results → next module. Re-run all steps marked "LIVE VERIFICATION PENDING STAGING". Track B must stay active; migration must not silently displace MVP delivery.

## HANDOVER (append to docs/handover/HANDOVER.md at the end of every phase)
STAGING: current status; last verified successful step; exact outstanding step; next action; command or operation required; expected result; remaining completion criteria.
MVP: current status; highest-priority independent task; work completed; files changed; tests and review results; remaining acceptance criteria; next implementation task.
OVERALL: what must not be repeated; what is blocked and exactly why; what can continue immediately; next milestone for each track.

## START
Begin with PHASE 0 now. Do not proceed to Phase 1 until I reply.
