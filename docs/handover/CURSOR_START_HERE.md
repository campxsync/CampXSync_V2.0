# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-25 (IADM-06 S4 filmed — awaiting owner approval; not pushed)

---

## What to do next

1. Await owner `APPROVED IADM-06` or `APPROVED IADM-06 COMMIT`.
2. Do **not** push until owner says.
3. Close-out report: `docs/iadm/IADM-06_S4_Report.md`; headed evidence `docs/iadm/iadm-06/evidence/us028/`.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — IADM-06 S4 close-out filmed; **not pushed**.
2. **IADM-06:** `:8104` `/api/v1/iadm/06` — aggregates only; schema `anl` FORCE RLS on DEV.
3. **Headed US-028:** **20/20 PASS** (R01/R02/R03×2/R04/R28 + faults); thresholds low=1 raised=50.
4. **US-027:** 18/18 unit PASS + headed failure film.
5. **DEMO policy:** `DEMO-ANALYTICS-HANDLING` v2 via IADM-05 lifecycle (R02≠R01); export gate uses RETENTION+ANALYTICS.
6. **Catalogue:** M01–M10 seeded; DEMO ONLY adoption fixture labelled.
7. **Shared audit:** `audit.log_event` fail-closed; view outcome via audit (F-01 partial).
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
9. **IADM-05** remains approved S4 local (`6d31085`); do not push without owner.
10. **Partial:** US-021 view outcome cannot use IADM-05 request_id (documented F-01).

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
