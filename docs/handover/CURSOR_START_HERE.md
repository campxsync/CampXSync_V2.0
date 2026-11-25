# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-25 (IADM-06 S0 foundation on DEV — not pushed)

---

## What to do next

1. Continue IADM-06 backlog S1→S4 (owner kickoff: no small-approval stops).
2. Local commits only on `feature/java-backend-integration`.
3. Do **not** push until owner says.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — IADM-06 S0 building; **not pushed**.
2. **IADM-06:** `:8104` `/api/v1/iadm/06` — aggregates only; schema `anl` FORCE RLS on DEV.
3. **Preflight:** `docs/iadm/iadm-06/US016_PREFLIGHT_REPORT.md` — DEV target verified; shared audit cleared.
4. **Desks started:** `/platform/analytics`, `/institution/analytics`, `/platform/analytics/audit`.
5. **Sources:** IADM-01/04 additive `/analytics-aggregates`; IADM-05 resolve RETENTION+ANALYTICS / EXPORT DEMO policies.
6. **Catalogue:** M01–M10 seeded; DEMO ONLY adoption fixture labelled.
7. **Shared audit:** `audit.log_event` fail-closed; no second audit store.
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
9. **IADM-05** remains approved S4 local (`6d31085`); do not push without owner.
10. **Suppression threshold:** DEMO ONLY runtime (low then raise for headed film); production OWNER-OPEN.

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
