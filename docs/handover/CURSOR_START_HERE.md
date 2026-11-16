# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-16 (STM-06-US-015 DDL+persistence completed; next CROSS-EN-010)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — owner queue (CROSS-EN-010 next; live AV / Kafka still gated).
2. `docs/project-control/PROGRESS.md` — source of truth for status and owner decisions (D1–D12a-Phase2).

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says.
2. **STM-06-EN-002:** **completed** — includes live Dev orphan cleanup on `azulbaxszsqxymqmlwfe` (12/12 evidence).
3. **STM-06-US-015:** **completed** — `doc.document_expiry_notifications` applied on Dev; Java persistence wired; suite 139/0.
4. **Next (queue):** STM-CROSS-EN-010 e2e harness (authorized); then CROSS-EN-014 / STM-03-EN-007; cross-tenant suite; RBAC flags; STM-07+.
5. **Governing agreement:** `docs/AGREEMENT_V2.1.md`. Path C HTTP stack smoke passed (`docs/reports/R31_PATH_C_STACK_SMOKE_2026-11-15.json`); set `CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081` when STM services run.
6. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC. Do not weaken auth.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden. Service-role key never in frontend/Git/logs.
8. **Stack note:** STM-03 (:8095) and STM-01 student-service (:8093) are **not** in `start_stack.ps1`.
9. **Kafka CROSS-EN-007** held. Live malware scanner = new tool (out of scope).
10. **Demos:** headed `run_story_demo.mjs` required every story; EN-002 Phase A is unit-test + runbook only (no live Storage demo).

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
