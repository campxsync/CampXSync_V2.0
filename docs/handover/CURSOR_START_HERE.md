# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-26 (IADM-08 S0–S4 built; awaiting owner approval)

---

## What to do next

1. **IADM-08** implementation complete on DEV — STOP for `APPROVED IADM-08` / `APPROVED IADM-08 COMMIT`.
2. Report: `docs/iadm/IADM-08_S4_Report.md`; headed **10/10** at `docs/iadm/iadm-08/evidence/us023/`.
3. Do **not** push unless owner authorizes.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — IADM-08 S0–S4 implemented; local commit pending/owner approval.
2. **IADM-08:** `:8106` `/api/v1/iadm/08` — **no owned schema**; read-only audit/health oversight.
3. **Headed US-023:** **10/10 PASS** (R01/R02/R03/R04/R28).
4. **Catalogue:** 5 `iadm08.*` keys additive; legacy `adm08.*` untouched; `iadm08.manage` reserved not granted.
5. **Limits DEMO ONLY:** max window **31 days**, page size **50**.
6. **Shared audit:** read via `audit.events` (platform elevate); every read recorded `IADM08_*` fail-closed.
7. **IADM-05 gate:** DEMO-OPERATIONS-HANDLING (OPERATIONS domain) published.
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
9. **CSV:** working backlog at `docs/user-stories/IADM-08-user-stories.csv` (owner path was missing at preflight; reconstructed).
10. **Aggregator:** `iadm-08-audit-health-ops-service` registered.
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
