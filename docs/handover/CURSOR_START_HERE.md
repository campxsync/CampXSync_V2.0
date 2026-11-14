# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-14 (H-1…H-4 APPROVED + COMMITTED; not pushed)

---

## 1. Current State in 10 Bullets (verified / labeled)

1. **Branch:** `feature/java-backend-integration` — local commits include docs `44e502e` plus H-1…H-4 commit. **Not pushed** until instructed.
2. **Pilot hardening:** **H-1, H-2, H-3, H-4 APPROVED** (sequence complete).
3. **H-4 evidence:** Demo **16/16 PASS** — `docs/demos/H-4/demo.webm`, `DEMO.md`; CLI `scripts/dev/pilot_onboard.mjs`; runbook `docs/runbooks/PILOT_ONBOARDING_RUNBOOK.md`.
4. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC. Remaining `X-User-*` call sites still to migrate; do not weaken auth.
5. **H-2 gap:** platform `userCount`/`collegeCount` remain 0 under RLS (needs approved DDL).
6. **Stack note:** Restart with `scripts/dev/start_stack.ps1` when free RAM ≥3 GB before next Java work.
7. **Largest RAM consumer observed:** Red Hat Java LS (`-Xmx8G`) ~1.3 GB — close/restart Java extension if free RAM stays &lt;3 GB.
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden.
9. **Live tenant provision:** requires exact phrase `GO PROVISION-TENANT` (script `--approve` or BFF header).
10. **No push** without explicit user authorization.

---

## 2. File Map & Project Artifacts

- **Pilot onboard CLI:** `scripts/dev/pilot_onboard.mjs` + `scripts/dev/fixtures/pilot/`
- **Runbook:** `docs/runbooks/PILOT_ONBOARDING_RUNBOOK.md`
- **Story demos:** `docs/stories/<ID>.demo.json`, `docs/demos/<ID>/`
- **Stack scripts:** `scripts/dev/start_stack.ps1`, `check_stack.ps1`, `stop_stack.ps1`, `restart_service.ps1`
- **Progress / CSV:** `docs/project-control/PROGRESS.md`, `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`
- **Architecture & Rules:** `AGENTS.md` and `.cursor/rules/*.mdc`

---

## 3. Ten Most Important Lessons Learned

1. **Never Bypass the Gateway (for new work):** Legacy `@/lib/db` is a documented deviation, not a pattern to extend.
2. **Column Size Invariants:** `stu.lifecycle_history.action` is `VARCHAR(64)`.
3. **Status State Machine:** No `ON_LEAVE` → `ON_LEAVE` / `TRANSFERRED` shortcuts.
4. **Append-Only Triggers:** Never `DELETE`/`UPDATE` ledger tables.
5. **Hydration:** Gate autofill / `fdprocessedid` with `mounted`.
6. **RAM Gate:** Bound JVM heaps; require ≥3 GB free before stack; Java LS can starve demos.
7. **Surgical Restarts:** `restart_service.ps1` from repo root; refresh `classpath.txt` after logger changes.
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
