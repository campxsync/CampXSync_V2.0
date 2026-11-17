# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-17 (STM-07 batch 3 COMPLETED — US-003/005/006/012; Playwright 4/4; pen 34/34; E2E 32/32)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — next: remaining unblocked STM-07 enablers / STM-10; STM-08/09 gated; live AV / Kafka / FLAG-002/003/007 gated.
2. `docs/project-control/PROGRESS.md` — source of truth for status and owner decisions (incl. STM-07-B2-D1..D3).

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says. **Known preflight leftovers (do not STOP):** `classpath.txt`, `docs/reports/_csv_status_*.txt`, `docs/reports/_verify_*.txt`, `docs/reports/_wt_compare_*.txt`, `docs/reports/**/video/*.webm`.
2. **STM-06-EN-002 / US-015 / CROSS-EN-010 / cross-tenant pen:** **completed**.
3. **STM-CROSS-EN-014 / STM-03-EN-007:** **partially completed** — ops pack for STM-03..06.
4. **STM-07 batch 2 + verify:** US-008/009 (`2675dea`), EN-002 (`57dc0ff`), EN-004 (`bf8cf6d`), US-001 **completed** (`912a6f9`). Role security `8412134`. **STM-08/09** BLOCKED. OWNER still must confirm institute-admin hosts role endpoint (**approved** vs **move**).
5. **Governing agreement:** `docs/AGREEMENT_V2.1.md`. Path C + STM-07 R25 role ports use institute-admin (`CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081`).
6. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC. Do not weaken auth.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden. Service-role key never in frontend/Git/logs.
8. **Stack:** `start_stack.ps1` now starts institute-admin (:8081) and student-service (:8093) (`a480396`). STM-02/03/05 still omitted for RAM; start them for CROSS-EN-010 / pen when needed.
9. **Kafka CROSS-EN-007** held. Live malware scanner = new tool (out of scope).
10. **Demos:** `docs/reports/playwright-stm07-batch2/` for batch 2; `run_story_demo.mjs` for story demos.

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
