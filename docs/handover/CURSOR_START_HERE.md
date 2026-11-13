# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  

---

## 1. Current State in 15 Bullets

1. **Transactional Path Active:** The canonical flow $\text{Next.js 15 UI} \rightarrow \text{API Gateway (:8080)} \rightarrow \text{Java Services} \rightarrow \text{Supabase PostgreSQL} \rightarrow \text{Intelligence Layer}$ is operational and enforced.
2. **Microservices Operational:** Five core domain services are active on loopback: Admissions (:8094), Enrollment (:8095), Lifecycle (:8096), Guardian (:8097), Document (:8098).
3. **API Gateway Active:** Port 8080 serves as single ingress, enforcing HMAC-SHA256 request signing and forwarding JWT claims.
4. **Next.js 15 Frontend Active:** Port 3000 serves all college, student, and faculty portals using React Server Components and client desks.
5. **STM-04 Student Lifecycle:** Stories US-007 through US-014 and US-003 work end-to-end with real database commits, status state machine enforcement, and HOD endorsement workflows.
6. **STM-06 Student Documents:** Stories US-001 through US-006 work end-to-end with stage requirement checklists, multi-version uploads, verification decisions, and retention audit logging.
7. **STM-02 Admissions:** All 55 backlog items implemented and verified against PostgreSQL persistence.
8. **In-Memory / Mock Remnants:** Early academic services (ACD-01/02/03) still have in-memory repository fallbacks that need Postgres hardening before pilot.
9. **Internal Auth Posture:** Microservice-to-microservice mutual token verification is relaxed (`-Dcampx.internal.auth.enabled=false`) for local loopback development, relying on Gateway HMAC headers.
10. **Active Supabase Project:** Development ref is strictly `azulbaxszsqxymqmlwfe`. Production (`ekspahbsbwdpraaqfqui`) is strictly off-limits.
11. **Uncommitted Work Status:** Working tree contains uncommitted changes for `STM-04-US-003` (HOD Endorsement) including `LifecycleDomainService.java`, `LifecycleController.java`, `page.tsx`, and demo assets. **Do NOT commit or reset without user instruction.**
12. **Running Processes:** Stack supervisor (`scripts/dev/start_stack.ps1`) runs in the background monitoring ports 3000, 8080, 8096, and 8098.
13. **Traceability Accounting:** 185 total items verified with live evidence in `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`.
14. **Test Accounts:** Configured in `.env.audit` for College Admin, Faculty Advisor, Student, and Platform Admin.
15. **Halt Condition:** The current task is stopped at user request; do not start new feature work without authorization.

---

## 2. File Map & Project Artifacts

- **Database Migrations & Schemas:** `database/migrations/` and `docs/audit/`
- **Story Demo Specifications:** `docs/stories/<STORY-ID>.demo.json`
- **Recorded Headed Demos & Reports:** `docs/demos/<STORY-ID>/demo.webm` and `.../DEMO.md`
- **Development & Health Scripts:** `scripts/dev/` (`start_stack.ps1`, `check_stack.ps1`, `stop_stack.ps1`, `restart_service.ps1`)
- **Playwright Demo Harness:** `scripts/demo/run_story_demo.mjs`
- **Master Progress Tracker:** `docs/project-control/PROGRESS.md`
- **Traceability Matrix:** `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`
- **Architecture & Rules:** `AGENTS.md` and `.cursor/rules/*.mdc`

---

## 3. Ten Most Important Lessons Learned

1. **Never Bypass the Gateway:** Writing direct SQL queries in Next.js bypasses Java state machines, audit outbox events, and institutional security checks.
2. **Column Size Invariants:** PostgreSQL `stu.lifecycle_history.action` is `VARCHAR(64)`. Prefixing action strings with long descriptions causes `422/500` database errors. Keep action codes concise (e.g. `hod.endorsement:<TYPE>`).
3. **Status State Machine Invariants:** Students cannot transition from `ON_LEAVE` directly to `ON_LEAVE` or `TRANSFERRED`. Always verify current standing in UI before enabling decision buttons.
4. **Append-Only Triggers:** Tables like `doc.document_versions` and `stu.lifecycle_history` have immutable triggers. Never execute `DELETE` or `UPDATE` on these tables.
5. **Browser Extension Hydration Hazards:** Edge autofill and password managers inject `fdprocessedid="..."` into buttons. Use `mounted` gating and `suppressHydrationWarning`.
6. **Windows RAM Constraints:** Running 6+ JVMs concurrently on Windows will exhaust memory if unconstrained. Bound JVM heaps to `-Xms32m -Xmx96m`.
7. **Surgical Service Restarts:** Use `scripts/dev/restart_service.ps1 <service>` to recompile and restart an individual service in 4 seconds instead of restarting the entire stack.
8. **PowerShell Quoting Rules:** Paths containing parentheses (like `(dashboard)`) and Maven flags with `-D` must always be quoted in PowerShell.
9. **Deterministic Seeding:** When verifying complex workflows, seed fresh test records with distinct tags to avoid collisions with prior test runs.
10. **Live Database Read-Backs are Mandatory:** A passing unit test with mocks does not prove that PostgreSQL committed the row. Always perform a real JDBC/pg read-back.
