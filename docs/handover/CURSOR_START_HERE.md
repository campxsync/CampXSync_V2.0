# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-14 (STM-03 EN cleared; STM-04-EN-001/002 completed; EN lane continuing)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — next assignment after US partials (PROPOSED: EN/CROSS).
2. `docs/project-control/PROGRESS.md` — source of truth for status and open policy decisions.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — EN lane in progress (user authorized commit/push this session).
2. **Canonical backlog:** `docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` — STM-03 EN + STM-04-EN-001/002 completed.
3. **Governing agreement:** `docs/AGREEMENT_V2.1.md`.
4. **Next:** STM-04-EN-003 (approval workflow / intelligence events; no Kafka invent).
5. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC. Remaining `X-User-*` call sites still to migrate; do not weaken auth.
6. **H-2 gap:** platform `userCount`/`collegeCount` remain 0 under RLS (needs approved DDL).
7. **Stack note:** STM-03 (:8095) and STM-01 student-service (:8093) are **not** in `start_stack.ps1`; both needed for student enrolment BFF (`X-Student-Record-Id` via `/students/me`).
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden.
9. **Live tenant provision:** requires exact phrase `GO PROVISION-TENANT` (script `--approve` or BFF header).
10. **Demos:** headed `run_story_demo.mjs` required every story; latest `docs/demos/STM-04-EN-002/`.

---

## 2. File Map & Project Artifacts

- **Progress / next task / reports:** `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`, `docs/reports/COMPLETION_REPORT.md`
- **Backlog CSV:** `docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv`
- **Audit (historical snapshot):** `docs/audit/STM_STATUS_AUDIT_2026-11-14.md`
- **Archive (never a source):** `docs/archive/`
- **Pilot onboard CLI:** `scripts/dev/pilot_onboard.mjs` + `scripts/dev/fixtures/pilot/`
- **Runbook:** `docs/runbooks/PILOT_ONBOARDING_RUNBOOK.md`
- **Story demos:** `docs/stories/<ID>.demo.json`, `docs/demos/<ID>/`
- **Stack scripts:** `scripts/dev/start_stack.ps1`, `check_stack.ps1`, `stop_stack.ps1`, `restart_service.ps1`
- **Architecture & Rules:** `AGENTS.md` and `.cursor/rules/*.mdc`

---

## 3. Ten Most Important Lessons Learned

1. **Never Bypass the Gateway (for new work):** Legacy `@/lib/db` is a documented deviation, not a pattern to extend.
2. **Column Size Invariants:** `stu.lifecycle_history.action` is `VARCHAR(64)`.
3. **Status State Machine:** No `ON_LEAVE` → `ON_LEAVE` / `TRANSFERRED` shortcuts.
4. **Append-Only Triggers:** Never `DELETE`/`UPDATE` ledger tables.
5. **Hydration:** Gate autofill / `fdprocessedid` with `mounted`.
6. **RAM Gate:** Bound JVM heaps; require ≥3 GB free before stack; Java LS can starve demos.
7. **Surgical Restarts:** `restart_service.ps1` from repo root; refresh service `classpath.txt` after logger changes.
8. **PowerShell:** Quote Maven `-D` flags; avoid `&&` chaining issues.
9. **Unit tests under auth ON:** Force `campx.internal.auth.enabled=false` in `@Before`.
10. **Live DB read-backs mandatory** for story DoD when claiming persistence.

---

## 4. Handover instructions that worked / failed (2026-11-14 H-4)

| Instruction | Result |
| :--- | :--- |
| Pilot CLI dry-run + invalid CSV reject | **Worked**. |
| Gated UI onboarding saga without live write | **Worked**. |
| Free RAM ≥3 GB with stack + Java LS | **Failed / tight** — stopped stack JVMs for demo; Java LS still ~1.3 GB. |
| Live Auth user mint for imported roster | **Out of scope** for H-4 without separate approval. |
