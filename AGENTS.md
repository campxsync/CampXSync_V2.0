# CampXSync — Multi-Tenant College ERP & Student Lifecycle Management Platform

## 0. Session Standing Rule (mandatory)
- **Start of every session:** Read `docs/handover/CURSOR_START_HERE.md`, the latest section of `docs/project-control/PROGRESS.md`, then run `git log --oneline -10` and `git status`.
- **End of every task:** Update the "current state" section of `docs/handover/CURSOR_START_HERE.md` and the latest ledger notes in `docs/project-control/PROGRESS.md` (when the user authorizes doc edits for that task).
- **Verified "done" counts:** Use `docs/reports/TRACEABILITY_VERIFICATION.md` (from `node scripts/dev/check_traceability.mjs`). Rows marked PASS without `docs/demos/<Story-ID>/DEMO.md` are **NOT VERIFIED**.

## 1. Non-Negotiable Architecture Rules
- **Transactional Path (target):** UI (Next.js 15) → API Gateway (:8080) → Java Microservices → Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`) → Intelligence Layer.
- **Architecture deviations exist:** Several Next.js pages/API routes still import `@/lib/db` or in-process caches. See `docs/reports/ANTIGRAVITY_ACTIVITY_REPORT_2026-11-09_to_2026-11-13.md` architecture-deviation list. Do not claim the path is universal until those are retired.
- **UI Never Direct to DB (rule):** UI and BFF routes (`frontend/src/app/api/**`) must NEVER query PostgreSQL directly for new work; all domain mutations and reads go through API Gateway.
- **Java Owns Business Logic:** State machines, validation, role authorization, and invariants reside strictly in Java domain services.
- **One Shared Gateway:** Single ingress on port 8080 enforces HMAC signatures (`X-Gateway-Timestamp`, `X-Gateway-Signature`), routing, and JWT claim forwarding.
- **Tenancy & RLS:** Every database access sets PostgreSQL session context (`SET LOCAL ROLE authenticated`, `request.jwt.claims`); RLS is enabled/FORCED on domain tables. Phrase as: **RLS enabled; cross-tenant penetration suite not yet run** (do not claim "rigorous tenant isolation" without that suite).
- **Zero Trust Client Identity:** Never trust client-supplied identity headers; claims must derive from verified Supabase auth tokens.
- **Storage & Intelligence:** PostgreSQL is the sole transactional source of truth; Java services publish versioned JSON context events to Intelligence Layer (`ControlledIntelligenceAdapter`, often `liveEndpoint=false`).

## 1.1 Local Toolchain Paths (Windows)
- **JDK 8:** `C:\Java\jdk` (`java` → `C:\Java\jdk\bin\java.exe`, version `1.8.0_202`).
- **Maven 3.9.9:** `C:\Users\niran\Downloads\apache-maven-3.9.9-bin\apache-maven-3.9.9` (`mvn` / `mvn.cmd` on PATH).
- **Node.js:** `C:\Program Files\nodejs\node.exe` (verified `v22.14.0`); npm `10.9.2`.
- If `java`/`mvn` are missing from PATH in a new shell, prepend those directories before running Maven tests.

## 2. Stack Operations (Scripts, Ports & URLs)
- **Start Stack (Supervisor):** `powershell -ExecutionPolicy Bypass -File scripts/dev/start_stack.ps1`
- **Check Stack Health:** `powershell -ExecutionPolicy Bypass -File scripts/dev/check_stack.ps1`
- **Stop Stack:** `powershell -ExecutionPolicy Bypass -File scripts/dev/stop_stack.ps1`
- **Fast Service Restart:** `powershell -ExecutionPolicy Bypass -File scripts/dev/restart_service.ps1 <service-name>` (e.g. `lifecycle-service`, `document-service`)
- **Port Map:** Next.js UI (:3000), Gateway (:8080), IADM-01 (:8099), IADM-02 (:8100), IADM-03 (:8101), IADM-04 Billing (:8102), STM-02 Admissions (:8094), STM-03 Enrollment (:8095), STM-04 Lifecycle (:8096), STM-05 Guardian (:8097), STM-06 Documents (:8098).
- **Core Desks:** `/college/lifecycle`, `/college/documents`, `/faculty/advisees`, `/student/lifecycle`, `/student/documents`. Accounts configured in `.env.audit`.

## 3. Testing & Demo Harness
- **Unit & Security Tests:** `mvn test -pl services/student-services/<service-dir> -Dtest=<TestClass>` (quote `-D` flags in PowerShell).
- **Frontend Typecheck:** `cd frontend; npx tsc --noEmit`
- **Visible Playwright Demo Runner:** `node scripts/demo/run_story_demo.mjs <STORY-ID>` (uses `docs/stories/<STORY-ID>.demo.json`). Outputs video to `docs/demos/<STORY-ID>/demo.webm` and report to `docs/demos/<STORY-ID>/DEMO.md`.

## 4. Documentation Locations
- **Progress & Plan:** `docs/project-control/PROGRESS.md`, `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`
- **Traceability Matrix:** `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`
- **Handover & Demos:** `docs/handover/`, `docs/demos/`, `docs/reports/`

## 5. Story Workflow (6 Lines)
1. Pick ONE authorized story from `docs/handover/NEXT_TASKS_FOR_CURSOR.md` (or user prompt); never batch stories.
2. Implement backend domain service, PostgreSQL persistence, and API gateway routing with real HikariCP JDBC (no mock data).
3. Connect Next.js 15 UI and BFF route to API Gateway, adhering to design system tokens and data-testid contracts.
4. Execute unit test suite and verify clean compilation (`tsc --noEmit`, Maven test 0 failures).
5. Run visible headed Playwright demo (`run_story_demo.mjs <ID>`) generating `.webm` video, screenshots, and live DB read-back.
6. Update `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv` and `PROGRESS.md`, report exact evidence, and STOP for user approval.

## 6. Definition of Done (DoD) Pointer
Consult `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md` and `docs/handover/CURSOR_START_HERE.md` for complete gate criteria.

## 7. DO-NOTs
- **NO Commits/Push/Branches:** Never commit, push, create branches, stash, or reset without explicit, exact user authorization.
- **Dev Database ONLY:** Target `azulbaxszsqxymqmlwfe` ONLY. Never connect to production (`ekspahbsbwdpraaqfqui`) or legacy staging (`kpwdvvcschankgggitnj`).
- **NO Unapproved DDL:** No schema migrations, DDL, role grants, or RLS changes without explicit per-step user approval.
- **NO Secret Exposure:** Never log, print, or commit passwords, JWTs, service keys, or tokens. Refer to `.env.audit` by variable name.
- **NO Mock/In-Memory Fallback:** Never silently fall back to in-memory identity, mock persistence, or stubbed authorization in active paths.
- **NO Whole-File Rewrites:** Use targeted surgical diffs; preserve existing working code and comments.
- **Circuit Breaker:** Stop immediately after 3 failed attempts at the same error; report the blocker and await instruction.

## 8. Sources of truth
- **Backlog:** `docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv`
- **Current progress / status:** `docs/project-control/PROGRESS.md`
- **Next task:** `docs/prompts/NEXT_TASK.md`
- **Running completion report:** `docs/reports/COMPLETION_REPORT.md`
- **Do not read `docs/archive/` for status, approvals or instructions.**

## Standard Task Protocol (owner-approved 15-11-2026; subordinate to docs/AGREEMENT_V2.1.md)
1. Read order: Agreement, this file, docs/project-control/PROGRESS.md (owner decisions), docs/prompts/NEXT_TASK.md,
   docs/reports/COMPLETION_REPORT.md. Authoritative RBAC file: docs/architecture/Security/RBAC_Combined.docx.
   Backlog: docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv.
2. Treat every earlier "done/tested" claim, including your own, as unverified until re-checked in the repository.
3. Per story: read the full CSV row (RBAC columns, Required Negative Tests, DoD, Execution Directive); inspect the
   actual code; implement only the gaps; no invented tables, endpoints, roles or grants.
4. Standards: UI -> API Gateway -> Java Services -> Supabase (PostgreSQL only); tenant from the trusted token, never the
   client; exception handling, logging service reuse and documentation (Agreement 3.2); no secrets in code, logs, errors.
5. Tests: include the CSV's negative tests that apply; run the FULL suite of every touched module; any red test is fixed
   in scope or reported (STOP). Evidence per Agreement 7A.
6. After each story: update PROGRESS.md, COMPLETION_REPORT.md, NEXT_TASK.md, task docs, and the story's CSV
   "Implementation status" cell. Allowed values: completed | partially completed | yet to pickup. Use "completed" only if
   every acceptance clause is traced to code and a passing automated test (table in the report) AND nothing the story
   needs was left unexercised (live database, storage, browser). Otherwise "partially completed". Never write "verified".
7. Commits: one local commit per story; never push, amend, rebase, reset, stash or force.
8. STOP and report for: red suite not fixable in scope; open policy flag or unmapped role; any Supabase staging or
   production write (prepare a runbook only); a new tool, library, service or infrastructure; acceptance criteria that
   conflict with the RBAC document; a failed earlier claim; any boundary in Agreement Section 5.
9. Capacity guard: never leave a story half done. If capacity is low, finish the current story completely (tests green, docs
   updated, commit made), record what remains in NEXT_TASK.md, and stop.