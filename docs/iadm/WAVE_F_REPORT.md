# IADM Wave F Report — PARTIAL Close

**Date:** 2026-11-21  
**Branch:** `feature/java-backend-integration`  
**DEV:** `azulbaxszsqxymqmlwfe`  
**Owner:** complete non-deferred PARTIAL/NEW before CADM

## Exit summary

| Phase | Status |
|---|---|
| F0 Inventory | **completed** — `WAVE_F_PLAN.md` |
| F1 IADM-02 | **completed** — versions + real rollback; type validation; Wave F live test |
| F2 IADM-03 + IADM-08 | **completed** — grant-table assign; roles+perms; retention via `audit.retention_policies` + RLS patch |
| F3 IADM-04 + IADM-06 | **completed** — plan-change, assign/transition, issue/reconcile, usage college/ingest |
| F4 IADM-05 + IADM-07 | **completed** — notices, DSR+hold, security mins, retention floor (`plat.retention_minimums`) |
| F5 Docs + suites | **completed** — this report; Maven green on IADM-02…08 |
| F-pending close | **completed** — migration `20261121001100`; live tests green |

**Deferred (unchanged):** IADM-03-US-008/009, IADM-05-US-007, R27/adm03, Kafka.

## Migrations applied (DEV)

| Forward | Purpose |
|---|---|
| `20261121000900_iadm_wave_f_partial_close.sql` | `cfg.setting_value_versions` + trigger; `plat.plan_change_requests`; `usage_metrics.college_id`; `plat.platform_security_minimums` |
| `20261121001000_iadm_wave_f_audit_retention_rls.sql` | Platform admin / `adm01.manage` on `audit.retention_policies` |

## Story outcomes (non-deferred)

| Service | completed | partially completed | Notes |
|---|---|---|---|
| IADM-01 | 8 | 0 | Already done Wave E |
| IADM-02 | 8 | 0 | Rollback proven in `Iadm02WaveFLiveDevTest` |
| IADM-03 | 7 | 0 | US-008/009 DEFERRED |
| IADM-08 | 6 | 0 | Retention uses existing audit table |
| IADM-04 | 7 | 0 | assign/transition + issue/reconcile closed |
| IADM-06 | 5 | 0 | college_id + ingest |
| IADM-05 | 7 | 0 | US-001 floor closed; US-007 DEFERRED |
| IADM-07 | 6 | 0 | Minimums + floor enforce |

## Maven evidence

`mvn test` on `iadm-02`…`iadm-08` → **BUILD SUCCESS** (includes `Iadm02WaveFLiveDevTest` rollback path).

## Remaining (non-story)

- Path-C / monolith `:8081` shim retained for internal delegations.
- **DEFERRED stories only:** IADM-03-US-008/009, IADM-05-US-007.

## Next

**CADM** Step 0 reconcile → Waves A–D (IADM contracts remain PASS in `WAVE_E_CONTRACTS.md`).

**STOP for owner approval** (`APPROVED WAVE-F` / `APPROVED WAVE-F COMMIT`).
