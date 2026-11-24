# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-24 (IADM-03 S2 grants — **STOP for APPROVED S2**)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — S2 awaiting `APPROVED S2` / `APPROVED S2 COMMIT`.
2. `docs/project-control/PROGRESS.md` — source of truth.
3. Spec: `docs/user-stories/CampXSync_IADM-03_User_Stories.csv`; report `docs/iadm/iadm-03/S2_BUILD_REPORT.md`.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says.
2. **Gateway:** Ingress `:8080`; **IADM-01** `:8099`; **IADM-02** `:8100`; **IADM-03** `/api/v1/iadm/03` → `:8101`.
3. **IADM-01 S0–S4 `APPROVED S4`:** onboarding, colleges/R04/INTERIM handover, lifecycle, history oversight.
4. **Working ACTIVE demo institute:** `MAC` / `72ecbfe3-2077-41b5-8a20-b6d3e045311b` (R03 `maharani.campx@gmail.com` — Auth `app_metadata.tenant_id` fixed 2026-11-24).
5. **Governing agreement:** `docs/AGREEMENT_V2.1.md`. Path C uses institute-admin (`CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081`).
6. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden.
8. **IADM-02 close-out done** (2026-11-24).
9. **IADM-03 S0 + S1** committed locally after owner APPROVED … COMMIT.
10. **IADM-03 S2 grants ready for approval:** appoint/assign/revoke/ceilings/claims; headed PASS; FACULTY grant `0b690be3-…` on DEV.

---

## 2. File Map & Project Artifacts

- **Progress / next task / reports:** `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`, `docs/reports/COMPLETION_REPORT.md`
- **IADM-03:** `docs/iadm/iadm-03/S2_BUILD_REPORT.md`, evidence under `docs/iadm/iadm-03/evidence/s2/`
- **Demo:** `node scripts/demo/iadm03_s2_headed.mjs`
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
