# Cursor Handover — Start Here
**Document:** `docs/handover/CURSOR_START_HERE.md`  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`), Local Windows Host  
**Governing Branch:** `feature/java-backend-integration`  
**Last current-state refresh:** 2026-11-28 (IADM-03 Legacy V1.4 restore verified; await owner approval)

---

## What to do next

1. **IADM-03 Legacy V1.4 restore** complete on DEV — STOP for `APPROVED IADM-03` / `APPROVED IADM-03 COMMIT`.
2. Report: `docs/iadm/iadm-03/IADM-03_FINAL_REPORT.md`; hash proof `docs/iadm/iadm-03/evidence/v2/v14_api_hash_proof.json`.
3. Do **not** push unless owner authorizes. **D-03 (apply vs record-only) stays with the owner.**

Do **not** use `docs/archive/` for status, approvals or instructions.

---

## 1. Current State in 10 Bullets (context)

1. **Branch:** `feature/java-backend-integration` — IADM-03 V1.4 restore verified; local commit pending owner approval.
2. **IADM-03:** `:8101` `/api/v1/iadm/03` — V2 record-only (`iadm03.*`) **coexists** with restored Legacy V1.4 apply routes.
3. **Legacy routes:** `/grants/appoint-r03|assign|revoke`, `/grants/r03-change/{id}/decide`, `/catalog-changes` (+ decide) marked `LEGACY V1.4`.
4. **20 `iam.*` keys** labeled **`IADM-03 V1.4 (legacy)`** (not Unknown).
5. **API hash proof PASS:** V2 decisions leave catalogue/grant hashes equal; only legacy mutators change grants.
6. **Headed demos PASS:** `iadm03_prestep_p_headed.mjs`, `iadm03_s2_headed.mjs`, `iadm03_s3b_headed.mjs`.
7. **Unrelated:** `appoint-r03` invented-tenant probe → `503 IADM03_AUTH_ADMIN_UNAVAILABLE`.
8. **Dev DB only:** `azulbaxszsqxymqmlwfe`.
9. **CSV:** `docs/user-stories/IADM-03-user-stories.csv` (US-021/022/024 claims adjusted for coexistence).
10. **IADM-07** previously APPROVED COMMIT & pushed (`3e92222` / `788e31c`).
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
