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
| F3 IADM-04 + IADM-06 | **partially completed** — plan-change + plans CRUD + usage college/ingest; subscription **assign** & invoice **issue** still thin |
| F4 IADM-05 + IADM-07 | **partially completed** — notices proxy, DSR execute+hold, platform security mins; retention floor not separate table |
| F5 Docs + suites | **completed** — this report; Maven green on IADM-02…08 |

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
| IADM-04 | 5 | 2 | US-002 assign subscription, US-005 issue invoice remain thin on split |
| IADM-06 | 5 | 0 | college_id + ingest |
| IADM-05 | 6 | 1 | US-001 no separate retention floor table; US-007 DEFERRED |
| IADM-07 | 6 | 0 | Minimums + floor enforce |

## Maven evidence

`mvn test` on `iadm-02`…`iadm-08` → **BUILD SUCCESS** (includes `Iadm02WaveFLiveDevTest` rollback path).

## Remaining explicit gaps (do not block CADM contracts)

1. **IADM-04-US-002** — subscription assign/state machine depth on `:8102` (reads + plan-change exist).
2. **IADM-04-US-005** — invoice issue/reconcile still primarily monolith / read on split.
3. **IADM-05-US-001** — institute retention CRUD without separate platform retention-days floor.
4. Path-C / monolith `:8081` shim retained for internal delegations.

## Next

**CADM** Step 0 reconcile → Waves A–D (IADM contracts remain PASS in `WAVE_E_CONTRACTS.md`).

**STOP for owner approval** (`APPROVED WAVE-F` / `APPROVED WAVE-F COMMIT`).
