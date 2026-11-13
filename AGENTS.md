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
- **Port Map:** Next.js UI (:3000), Gateway (:8080), STM-02 Admissions (:8094), STM-03 Enrollment (:8095), STM-04 Lifecycle (:8096), STM-05 Guardian (:8097), STM-06 Documents (:8098).
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
