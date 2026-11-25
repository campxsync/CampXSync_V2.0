# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-25 (IADM-04 **S5 + evidence 4a local commits — not pushed**)

---

## What to do next

1. Review local commits for S5 and evidence 4a (`docs/iadm/IADM-04_S5_Report.md`, `docs/iadm/iadm-04/evidence/s5/`).
2. Owner: authorize push, or `CHANGES S5: …` / further work.
3. Do **not** push until owner says.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — S5 + 4a committed locally; **not pushed**.
2. **Gateway:** `:8080`; IADM-04 `:8102` `/api/v1/iadm/04`.
3. **IADM-04 S0–S4** previously `APPROVED … COMMIT`.
4. **IADM-04 S5** close-out: US-038 headed a/b/c PASS; US-040 cutover 410; FORCE RLS; evidence 4a–4d.
5. **Evidence 4a closed:** R03 DEMO persona + headed PENDING/pay/ACTIVE + NONE deny + TRIAL allow — **12/12 PASS**.
6. **Governing agreement:** `docs/AGREEMENT_V2.1.md`.
7. **Internal auth ON:** Bearer JWT → gateway → HMAC.
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
9. **Monolith billing routes:** HTTP 410 `IADM04_CUTOVER_GONE`.
10. **Stopped:** await owner review / push authorization.

---

## 2. File Map & Project Artifacts

- **IADM-04 S5:** `docs/iadm/IADM-04_S5_Report.md`, evidence `docs/iadm/iadm-04/evidence/s5/`
- **Headed:** `node scripts/demo/iadm04_s5_headed.mjs`; 4a: `node scripts/demo/iadm04_s5_evidence_4a_headed.mjs`
- **R03 DEMO seed:** `scripts/dev/iadm04_seed_r03_demo.mjs` (password only in gitignored `.local/seed_accounts.json`)
- **Progress / next task:** `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`
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
10. **Role cache vs subscription gate:** Cache hits must re-check `subscriptionAllowsAccess` after PENDING/ACTIVE flips.
