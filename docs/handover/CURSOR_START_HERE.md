# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-25 (IADM-04 **S2 COMMITTED**; **S3 in progress**)

---

## What to do next

1. Complete IADM-04 **S3** (US-026…030): DEV payment simulator, initiate, signed callback, reconcile, R02 retry.
2. Headed demo + report → STOP for `APPROVED S3` / `APPROVED S3 COMMIT` / `CHANGES S3`.
3. Do **not** push unless owner says. Do **not** start S4 until S3 is approved.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says.
2. **Gateway:** `:8080`; IADM-01 `:8099`; IADM-02 `:8100`; IADM-03 `:8101`; **IADM-04 `:8102`** `/api/v1/iadm/04`.
3. **IADM-01 / IADM-02 / IADM-03** close-out / US-040 done on DEV (prior commits).
4. **IADM-04 S1** `APPROVED S1 COMMIT` `74ef789`; **S2** `APPROVED S2 COMMIT` (this session).
5. **Working ACTIVE demo institute:** `MAC` / `72ecbfe3-2077-41b5-8a20-b6d3e045311b`.
6. **Governing agreement:** `docs/AGREEMENT_V2.1.md`.
7. **Internal auth ON:** Bearer JWT → gateway → HMAC.
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
9. **S2 headed:** offline SoD (R01-A/B) + trial convert + PENDING→ACTIVE PASS.
10. **Now:** S3 online payment simulator (US-026…030) — authorized after S2 COMMIT.

---

## 2. File Map & Project Artifacts

- **IADM-04 S1:** `docs/iadm/IADM-04_S1_Report.md`, evidence `docs/iadm/iadm-04/evidence/s1/`
- **Headed:** `node scripts/demo/iadm04_s1_headed.mjs` (set `IADM04_S1_PENDING_FLIPPED=1` after MCP PENDING flip for D15)
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
10. **Live DB read-backs mandatory** for story DoD when claiming persistence.
