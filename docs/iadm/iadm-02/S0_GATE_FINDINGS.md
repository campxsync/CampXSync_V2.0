# IADM-02 S0 gate findings (owner stop conditions)

**Date:** 2026-11-23  
**Decision:** Continue S0/S1 on the **safe subset** below; do **not** apply the blocked actions until the owner decides.

## D6 — FORCE RLS / tightening on existing `cfg.*`

| Object | Other readers/writers in `services/`? | Action taken |
|---|---|---|
| `cfg.config_drafts` / `cfg.config_versions` (NEW) | n/a | **FORCE RLS YES** (as approved) |
| `cfg.setting_definitions` | **None** in Java/TS services (grep clean) | **FORCE RLS + SELECT/write tightening ALLOWED** |
| `cfg.policies` | **YES** — `institute-admin` `PostgresGlobalPolicyRepository`; `college-admin` `PostgresLocalPolicyRepository` (+ tests) | **STOPPED** — no FORCE/tighten (would break monolith/CADM paths left running until cutover) |
| Other existing `cfg.*` (feature_flags, setting_values, …) | YES — institute-admin and/or college-admin | **Not tightened** (same reason) |

## D6 — Freeze `cfg.setting_values` writes for platform-catalogue keys

| Path | Evidence | Action |
|---|---|---|
| Institute Admin | `PostgresGlobalSettingRepository` INSERT/UPDATE `cfg.setting_values` | Live path (monolith config left unchanged per owner) |
| College Admin | `PostgresCollegeSettingRepository` INSERT/UPDATE `cfg.setting_values` | Live CADM path |

**STOPPED:** no freeze migration applied. `adm05` grants untouched. Owner must decide how to freeze platform-catalogue keys without breaking college-admin / monolith until cutover.

## D7 — Consumer grant columns

`iam.api_client_grants.resource_scope` is **jsonb** — can express `{"domains":["security","attendance"],"keys":[...]}` without DDL. **Proceed seed-only.**

## Proceeding

S0 build continues: service `:8100`, `/api/v1/iadm/02`, new tables + permissions + seed + S1; monolith configuration routes unchanged.
