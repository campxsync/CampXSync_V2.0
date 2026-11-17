# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-17 (STM-10 batch 5: EN-001 portal prefs DDL+API done; next US-012 widgets; D1/D7/D9/D10 recorded; **no push**)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — STM-10 batch 5: next Configure dashboard widgets (STM-10-US-012); optional Registrar visibility decision; new-service gate for STM-10-US-023 + STM-08 + STM-09.
2. `docs/project-control/PROGRESS.md` — source of truth for status and owner decisions (incl. STM-07-B2-D1..D3).

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says. **Known preflight leftovers (do not STOP):** `classpath.txt`, `docs/reports/_csv_status_*.txt`, `docs/reports/_verify_*.txt`, `docs/reports/_wt_compare_*.txt`, `docs/reports/**/video/*.webm`.
2. **Gateway:** Registrar (R31) recognised as business role only (`dffbf19`). No new route grants. Baseline failures unchanged (6); identity suite 32/0.
3. **Delegated Registrar staff-screen browser case:** still **FAIL** after gateway fix — JWT accepted; link-status 404 (student not visible under authenticated RLS for pilot Registrar). Evidence in `docs/reports/playwright-stm07-batch4/`.
4. **STM-07 batch 4:** EN-005 partial, EN-006 done. **STM-08 / STM-09 / STM-10-US-023** gated as **new services** — do not build without approval.
5. **Governing agreement:** `docs/AGREEMENT_V2.1.md`. Path C uses institute-admin (`CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081`).
6. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC. Do not weaken auth.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden. Service-role key never in frontend/Git/logs.
8. **CSV:** swap from `.csv.tmpwrite` only after owner says the backlog CSV is closed; delete `.tmpwrite` in the same commit.
9. **Kafka CROSS-EN-007** held. Live malware scanner = new tool (out of scope).
10. **Demos:** `docs/reports/playwright-stm07-batch4/` for Registrar/EN-005; `run_story_demo.mjs` for story demos.

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
