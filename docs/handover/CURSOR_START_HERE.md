# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-25 (IADM-05 **S4 headed US-032 21/21 PASS** — await owner approval; not pushed)

---

## What to do next

1. Review S4 report `docs/iadm/IADM-05_S4_Report.md` and evidence `docs/iadm/iadm-05/evidence/us032/`.
2. Owner: `APPROVED IADM-05 S4` or `APPROVED IADM-05 S4 COMMIT` / `CHANGES IADM-05 S4: …`.
3. Do **not** push until owner says.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — IADM-05 S0–S4 work local; **not pushed**.
2. **IADM-05:** `:8103` `/api/v1/iadm/05` — policy governance only; schema `gov` FORCE RLS on DEV.
3. **IADM-05 commits:** `d3c2e74` (S0), `854d8f1` (S1), `2b566df` (S2/S3), plus S4 local commit when made.
4. **Desks:** `/platform/governance`, `/institution/governance`, `/college/governance`, `/platform/governance/audit`.
5. **Demo seeds on DEV:** classification/retention + export/erasure/sharing queues (DEMO ONLY; durations TBC).
6. **Privacy publish:** hard-blocked `IADM05_LEGAL_GATE` (D-RBAC-M17) — no open path.
7. **Shared audit:** `audit.log_event` fail-closed; no second audit store.
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
9. **US-032 headed:** **21/21 PASS** — personas R01/R02/R03/R04/R28 with real propose/approve/submit/close work.
10. **IADM-04** remains prior closed work (S5 local); do not push without owner.

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
