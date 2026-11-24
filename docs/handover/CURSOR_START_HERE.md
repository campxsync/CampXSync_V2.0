# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-24 (IADM-03 US-040 — **APPROVED US040 COMMIT**)

---

## What to do next

Read and follow only:

1. `docs/prompts/NEXT_TASK.md` — US-040 committed; owner picks next open item.
2. `docs/project-control/PROGRESS.md` — source of truth.
3. Report: `docs/iadm/iadm-03/US040_CLOSEOUT_REPORT.md`.

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — local commits when story completes; **no push** unless owner says.
2. **Gateway:** Ingress `:8080`; **IADM-01** `:8099`; **IADM-02** `:8100`; **IADM-03** `/api/v1/iadm/03` → `:8101`.
3. **IADM-01 / IADM-02** close-out done on DEV.
4. **Working ACTIVE demo institute:** `MAC` / `72ecbfe3-2077-41b5-8a20-b6d3e045311b`.
5. **Governing agreement:** `docs/AGREEMENT_V2.1.md`.
6. **Internal auth ON** (H-3): Bearer JWT → gateway → HMAC.
7. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
8. **IADM-03 S0–S4** committed locally after owner APPROVED … COMMIT.
9. **US-040 close-out:** **`APPROVED US040 COMMIT`**; hard checks PASS; 56-cell PASS; **RBAC v3.0 Compliant NOT CLAIMED**.
10. **Open:** US-033 (D3), US-036/037 later integration, US-038/039 PARKED.

---

## 2. File Map & Project Artifacts

- **Progress / next task / reports:** `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`
- **IADM-03 close-out:** `docs/iadm/iadm-03/US040_CLOSEOUT_REPORT.md`, `evidence/closeout/`
- **Demo:** `node scripts/demo/iadm03_us040_closeout.mjs`
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
