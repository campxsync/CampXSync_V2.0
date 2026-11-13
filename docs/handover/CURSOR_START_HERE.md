# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-13 (docs-only verification pass; HEAD `31f66c9`)

---

## 1. Current State in 10 Bullets (verified / labeled)

1. **Branch / HEAD:** `feature/java-backend-integration` at `31f66c9` (`docs(reports): Update STM traceability matrix and PROGRESS ledger for STM-04-US-003`). Working tree may contain only allowed docs from the verification pass.
2. **Demo-verified stories (source of truth):** `docs/reports/TRACEABILITY_VERIFICATION.md` — **14** CSV rows marked done **and** having `docs/demos/<Story-ID>/DEMO.md` (STM-04-US-003, 007–014; STM-06-US-002–006). All other PASS rows are **NOT VERIFIED** under the demo gate.
3. **CSV accounting (do not treat as fully verified):** 187 rows total; 183 marked done; 169 done-without-demo (**NOT VERIFIED**). STM-01/02/03 have many PASS rows with **0** demo packs.
4. **ID / title conflicts (CSV not edited):** `STM04-US-012` vs `STM-04-US-012`, `STM04-US-014` vs `STM-04-US-014`; STM-04 US-007/008/010/011 titles conflict with Final User Stories source titles.
5. **Mock / dual persistence:** Academic services still ship `InMemory*` repositories alongside some `Postgres*` implementations (course, batch, timetable, attendance, analytics). Curriculum / subject / calendar / assessment: no Postgres repository files found → treat as in-memory / UNVERIFIED Postgres.
6. **Internal auth disabled locally:** `scripts/dev/start_stack.ps1` starts services with `-Dcampx.internal.auth.enabled=false` (multiple lines).
7. **Stack now:** `check_stack.ps1` on 2026-11-13 reported Frontend :3000, Gateway :8080, Document :8098 **NOT LISTENING**; free RAM warning 0.37 GB. Do not assume the stack is up.
8. **Unit test smoke:** `mvn -pl services/student-services/lifecycle-service "-Dtest=LifecycleDomainServiceTest"` exited **0** on 2026-11-13.
9. **Architecture deviations:** Direct Postgres from Next.js via `@/lib/db` remains in several pages/routes (student dashboard/address/corrections/history, college faculty/timetable/attendance report, `resolve-role.ts`) plus in-process caches under `frontend/src/lib/cache/`.
10. **Next planned work:** Launch Track Step 1 — Containerization & Deployment Orchestration (`docs/handover/NEXT_TASKS_FOR_CURSOR.md`). Do **not** start new STM feature stories unless the user re-authorizes.

---

## 2. File Map & Project Artifacts

- **Database Migrations & Schemas:** `database/migrations/` and `docs/audit/`
- **Story Demo Specifications:** `docs/stories/<STORY-ID>.demo.json`
- **Recorded Headed Demos & Reports:** `docs/demos/<STORY-ID>/demo.webm` and `.../DEMO.md`
- **Development & Health Scripts:** `scripts/dev/` (`start_stack.ps1`, `check_stack.ps1`, `stop_stack.ps1`, `restart_service.ps1`, `check_traceability.mjs`)
- **Playwright Demo Harness:** `scripts/demo/run_story_demo.mjs`
- **Master Progress Tracker:** `docs/project-control/PROGRESS.md`
- **Traceability Matrix:** `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`
- **Traceability Verification:** `docs/reports/TRACEABILITY_VERIFICATION.md`
- **Activity Report (corrected):** `docs/reports/ANTIGRAVITY_ACTIVITY_REPORT_2026-11-09_to_2026-11-13.md`
- **Architecture & Rules:** `AGENTS.md` and `.cursor/rules/*.mdc`

---

## 3. Ten Most Important Lessons Learned

1. **Never Bypass the Gateway (for new work):** Writing direct SQL in Next.js bypasses Java state machines, audit outbox events, and institutional security checks. Legacy `@/lib/db` usage is a documented deviation, not a pattern to extend.
2. **Column Size Invariants:** PostgreSQL `stu.lifecycle_history.action` is `VARCHAR(64)`. Keep action codes concise.
3. **Status State Machine Invariants:** Students cannot transition from `ON_LEAVE` directly to `ON_LEAVE` or `TRANSFERRED`. Verify current standing before enabling decision buttons.
4. **Append-Only Triggers:** Tables like `doc.document_versions` and `stu.lifecycle_history` / `audit.events` have immutable triggers. Never `DELETE`/`UPDATE` those ledgers.
5. **Browser Extension Hydration Hazards:** Edge autofill injects `fdprocessedid`. Use `mounted` gating and `suppressHydrationWarning`.
6. **Windows RAM Constraints:** Bound JVM heaps to `-Xms32m -Xmx96m`. `check_stack.ps1` warns below 3 GB free RAM.
7. **Surgical Service Restarts:** Use `scripts/dev/restart_service.ps1 <service>`.
8. **PowerShell Quoting Rules:** Quote paths with parentheses and always quote Maven `-D` flags.
9. **Deterministic Seeding:** Seed with distinct tags to avoid collisions.
10. **Live Database Read-Backs are Mandatory:** A passing unit test with mocks does not prove PostgreSQL committed the row.

---

## 4. Handover instructions that worked / failed (2026-11-13 verification)

| Instruction | Result |
| :--- | :--- |
| Read `AGENTS.md`, `.cursor/rules/*`, `CURSOR_START_HERE`, `KNOWN_PITFALLS`, `NEXT_TASKS_FOR_CURSOR` | **Worked** — files present and readable. |
| `git log --oneline -15` / `git status` | **Worked**. |
| `java` / `mvn` / `node` / `npm` on PATH | **Worked** (JDK `C:\Java\jdk`, Maven under Downloads, Node 22). |
| `scripts/dev/check_stack.ps1` | **Worked as a script**; reported stack **down** (ports not listening) + low RAM. |
| `LifecycleDomainServiceTest` via Maven | **Worked** — exit 0. |
| Claim “185 total items verified with live evidence” in prior handover bullets | **Failed / unsupported** — replace with TRACEABILITY_VERIFICATION counts (14 demo-verified). |
| Claim stack supervisor “runs in the background” | **Failed against current host** — `check_stack.ps1` showed services not listening. |
| Claim “no commits or pushes” in activity/latency docs for 9–13 Nov | **Failed** — commits `395276c`, `f161cfe`, `9df3e80`, `be033af`, `91dab15`, `41f8bde`, `31f66c9` exist on the branch. |
