# IADM Wave E0 — CADM Contract Proof

**Date:** 2026-11-21 · **DEV:** `azulbaxszsqxymqmlwfe` · Plan: `WAVE_E_PLAN.md`  
**Status:** **E0 COMPLETE** (BUILD SUCCESS)

## Summary

| # | Contract (plain language) | Story | Evidence | E0 status |
|---|---|---|---|---|
| 1 | First College Admin + handover | IADM-01-US-003/004 | `Iadm01ContractLiveDevTest` (3/0) + `plat.is_auth_linked_user` | **PASS** |
| 2 | Effective configuration resolve | IADM-02-US-005 | `PlatformConfigLiveDevTest` (3/0) | **PASS** |
| 3 | Role ceiling lookup | IADM-03-US-002 | `Iadm03ContractLiveDevTest` (2/0) | **PASS** |
| 4 | Shared audit read | IADM-08-US-001 | `Iadm08ContractLiveDevTest` (3/0); audit SQL fixed | **PASS** |
| 5 | Entitlement check (fail closed) | IADM-04-US-006 | `Iadm04ContractLiveDevTest` (4/0) | **PASS** |

**Maven:** modules IADM-01/02/03/04/08 contract + existing suites → **BUILD SUCCESS** (exit 0).

## Evidence detail

### 1. Handover / first College Admin [CONFIRMED]

- Schema: `plat.college_admin_handovers`.
- SEC-11: `plat.is_auth_linked_user(uuid)` SECURITY DEFINER (`20261121000700`) — `authenticated` cannot `SELECT auth.users` directly.
- Allowed: Institution Admin (R03) `c03a0000-…` onboard + complete handover with Auth-linked College Admin `70a8ffae-…`; cleanup soft-delete.
- Denied: College Admin (R04) lacks `adm01.manage`; fake UUID → `IADM01_AUTH_USER_REQUIRED`.
- Test: `Iadm01ContractLiveDevTest` **3/0**.

### 2. Effective config [CONFIRMED]

- `cfg.resolve_effective_setting` → source `PLATFORM` for catalogue default.
- Locked definition → `platform_locked=true`, source still `PLATFORM`.
- Test: `PlatformConfigLiveDevTest` **3/0**.

### 3. Role ceilings [CONFIRMED]

- Allowed: R03 holds `adm09.manage`; rolled-back INSERT/SELECT on `iam.role_ceilings`.
- Denied: Student (R25) `81dd67d3-…` lacks `adm09.manage`.
- Test: `Iadm03ContractLiveDevTest` **2/0**.

### 4. Audit search [CONFIRMED]

- Fixed `Iadm08DomainService.searchAudit` to use `principal_id` / `event_time` (no `actor_id`/`deleted_at`); fail closed (no swallowed errors).
- Allowed: R03 `searchAudit` returns `items` + `count`.
- Denied: Student lacks `adm01.view`; domain call fails closed.
- Test: `Iadm08ContractLiveDevTest` **3/0**.

### 5. Entitlement [CONFIRMED]

- SQL: unknown key → `allowed=false`, `deny_reason=UNKNOWN_ENTITLEMENT`.
- Domain: R03 `checkEntitlement` same fail-closed.
- Denied: Student lacks `adm01.manage` and `iadm04.view`; College Admin lacks `iadm04.view`.
- Test: `Iadm04ContractLiveDevTest` **4/0**.

## Migrations this phase

| Forward | Rollback |
|---|---|
| `20261121000700_iadm_auth_linked_user_check.sql` | `20261121000700_rollback__iadm_auth_linked_user_check.sql` |

## Remaining for E1 (not E0 blockers)

- R03 lose college user-admin after COMPLETED handover (product rule depth).
- Move monolith billing/identity handlers; stack script ports.
- HttpContext SEC-02 Curriculum leak (E2).

## Tag legend

- **CONFIRMED** — named automated test or SQL run
- **PARTIAL** — not used at E0 exit
- **NOT FOUND** — not used at E0 exit
