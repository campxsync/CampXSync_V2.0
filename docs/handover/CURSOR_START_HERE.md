# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-23 (IADM-02 **S2 demo ready — owner checkpoint**; S1 `APPROVED S1 COMMIT`; IADM-01 S0–S4 still `APPROVED S4`; **no push**)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — **IADM-02 S2 owner checkpoint**. Demo: `docs/iadm/iadm-02/SLICE_S2_DEMO_REPORT.md`. Do not start S3 until `APPROVED S2`.
2. `docs/project-control/PROGRESS.md` — source of truth.
3. Spec: `docs/user-stories/CampXSync_IADM-02_User_Stories.csv`; blueprint under Documentation `MicroServices/CampXSync_IADM-02_Detailed_Blueprint_V2.1.md` (doc says V1.0).

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says.
2. **Gateway:** Ingress `:8080`; **IADM-01** `/api/v1/iadm/01` → `:8099`; JDK 8 PATCH proxy fixed.
3. **IADM-01 S0–S4 `APPROVED S4`:** onboarding, colleges/R04/INTERIM handover, lifecycle, history oversight. Reports in `docs/iadm/SLICE_S*.md`.
4. **Working ACTIVE demo institute:** `MAC` / `72ecbfe3-2077-41b5-8a20-b6d3e045311b` (R03 `maharani.campx@gmail.com`). Duplicate `MAC181627` archived.
5. **Governing agreement:** `docs/AGREEMENT_V2.1.md`. Path C uses institute-admin (`CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081`).
6. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC. Auth rules and Postgres RLS strictly enforced.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden. Service-role key never in frontend/Git/logs.
8. **Automated Verification:** Cross-Service E2E (`CROSS-EN-010`) 32/32 pass; Cross-Tenant Pen Test 34/34 pass; Playwright final demos 7/7 pass (`docs/reports/playwright-final-stm07-stm10/FINAL_DEMO_RUN_REPORT.json`).
9. **Kafka CROSS-EN-007** held. Live malware scanner = new tool (out of scope). CADM / IADM-03…08 not authorized. **IADM-02 S2** demo ready (`docs/iadm/iadm-02/SLICE_S2_DEMO_REPORT.md`); S3+ blocked on `APPROVED S2`.
10. **Demos:** `docs/iadm/` slice reports (IADM-02 S1/S2) + `docs/reports/playwright-final-stm07-stm10/`.

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
