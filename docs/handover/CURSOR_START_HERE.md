# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-26 (IADM-07 approved + commit/push authorized; next module awaiting owner)

---

## What to do next

1. IADM-07 closed (`APPROVED IADM-07` + owner **commit and push**).
2. **Next module not auto-picked** — no IADM-08 Detailed Blueprint/CSV in the MicroServices folder (IADM-01…07 only). Await owner choice: IADM-08 rework, CADM, or named STM/other ID.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — IADM-07 approved; CSV statuses + handover committed; owner-authorized push.
2. **IADM-07:** `:8105` `/api/v1/iadm/07` — schema `sec` FORCE RLS on DEV; registers only (never enforces).
3. **Headed US-032:** **10/10 PASS** (R01/R02/R03×2/R04/R28); stale threshold DEMO ONLY default 10080 → filmed 1440.
4. **Partials:** US-006 denial-event display omitted; US-009 no live enforcing-service contract (CSV: `partially completed`).
5. **Catalogue:** 11 `iadm07.*` keys additive; legacy `adm07.*` (8) untouched.
6. **Shared audit:** `audit.log_event` fail-closed; general read via `audit.events` `IADM07_%`.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
8. **IADM-06** remains local S4 (`fad012b`); do not push without owner.
9. **IADM-08:** Wave B/F stories marked completed in `IADM_User_Stories.md`; **no** `iadm-08-*` service / `:8106` route in current tree; no Detailed Blueprint for a full rework.
10. **Root `pom.xml`:** unchanged (aggregator `services/administration-services` already includes IADM-07).
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
