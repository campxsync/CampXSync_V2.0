# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-21 (US-009 **APPROVED**; US-008 implemented — await `APPROVED IADM-03-US-008`; Wave F still await approve)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — Wave F STOP / then **CADM**.
2. `docs/project-control/PROGRESS.md` — source of truth for status and owner decisions.
3. Evidence: `docs/iadm/WAVE_F_REPORT.md`, `WAVE_E_REPORT.md`, `WAVE_E_CONTRACTS.md`, `IADM_User_Stories.md`.
4. Deferred remaining: under-18 / R27; Kafka; commercial AV. Break-glass + two-person built (US-008 await approve).

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says.
2. **Gateway:** Active ingress on port 8080 routing to Java domain microservices with HMAC and Bearer claims forwarding.
3. **Delegated Registrar staff-screen browser case:** **PASS** (`07_registrar.spec.mjs`) — Pilot Registrar holding `STM-07.ACCOUNT.A` delegation successfully reads link status (`"ACTIVE"`) via D13 Java service path without relaxing Postgres RLS.
4. **Student One Login Link:** **ACTIVE** — verified through normal operations path using approved document verifications; mapped and persisted to `stu.student_iam_links`.
5. **Governing agreement:** `docs/AGREEMENT_V2.1.md`. Path C uses institute-admin (`CAMPX_IAM_DELEGATION_BASE_URL=http://127.0.0.1:8081`).
6. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC. Auth rules and Postgres RLS strictly enforced.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`. Production / legacy staging forbidden. Service-role key never in frontend/Git/logs.
8. **Automated Verification:** Cross-Service E2E (`CROSS-EN-010`) 32/32 pass; Cross-Tenant Pen Test 34/34 pass; Playwright final demos 7/7 pass (`docs/reports/playwright-final-stm07-stm10/FINAL_DEMO_RUN_REPORT.json`).
9. **Kafka CROSS-EN-007** held. Live malware scanner = new tool (out of scope).
10. **Demos:** `docs/reports/playwright-final-stm07-stm10/` contains screenshots, videos, DEMO_GUIDE.md, and run report.

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
