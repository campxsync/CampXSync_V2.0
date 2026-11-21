# IADM Wave E0 — CADM Contract Proof

**Date:** 2026-11-21 · **DEV:** `azulbaxszsqxymqmlwfe` · Plan: `WAVE_E_PLAN.md`

## Summary

| # | Contract (plain language) | Story | Artifact | E0 status |
|---|---|---|---|---|
| 1 | First College Admin + handover record | IADM-01-US-003/004 | `plat.college_admin_handovers` + `/api/v1/iadm/01` onboard/handover | **PARTIAL→hardening** — table [CONFIRMED]; SEC-11 Auth link enforced in Java onboard |
| 2 | Effective configuration resolve | IADM-02-US-005 | `cfg.resolve_effective_setting` + `/api/v1/iadm/02/.../effective` | **PASS** live rolled-back resolve → `PLATFORM` [CONFIRMED: PlatformConfigLiveDevTest] |
| 3 | Role ceiling lookup | IADM-03-US-002 | `iam.role_ceilings` + `/api/v1/iadm/03/ceilings` | **PASS** schema [CONFIRMED: `to_regclass('iam.role_ceilings')`] |
| 4 | Shared audit read | IADM-08-US-001 | `/api/v1/iadm/08/audit/search` | **PARTIAL** — endpoint exists; deepen allowed/denied live suite in E1-C |
| 5 | Entitlement check (fail closed) | IADM-04-US-006 | `plat.check_entitlement` + `/api/v1/iadm/04/entitlements/check` | **PASS** unknown/empty key → `allowed=false`, `UNKNOWN_ENTITLEMENT` [CONFIRMED SQL] |

## Evidence detail

### 1. Handover / first College Admin

- Schema: `plat.college_admin_handovers` present [CONFIRMED SQL `to_regclass`].
- Service: `TenantLifecycleService.onboardCollege` / `completeHandover` on :8099.
- **Wave E change:** `firstCollegeAdminUserId` required; must exist in `auth.users` (`IADM01_AUTH_USER_REQUIRED` / SEC-11). Closes FINDING-IADM-ARCH-01 for the IADM-01 path.
- Remaining: live rolled-back onboard+handover suite with real Auth user; R03 lose college-user-admin after COMPLETED (E1-A).

### 2. Effective config

- Function: `cfg.resolve_effective_setting(uuid,uuid,text)` [CONFIRMED].
- Live test: `PlatformConfigLiveDevTest.resolveEffectiveFunctionExistsAndReturnsPlatformDefault_rolledBack` → source `PLATFORM`, denied false.

### 3. Role ceilings

- Table: `iam.role_ceilings` [CONFIRMED].
- API: `GET/POST .../iadm/03/ceilings` with `adm09.manage` / view keys.
- Remaining: live set/get + denied for College Admin in E1-C.

### 4. Audit search

- Endpoint: `GET .../iadm/08/audit/search`.
- Remaining: wire to shared audit store with permission-key deny path evidence.

### 5. Entitlement

```text
plat.check_entitlement(tenant, '')           → allowed=false, UNKNOWN_ENTITLEMENT
plat.check_entitlement(tenant, 'no_such_key') → allowed=false, UNKNOWN_ENTITLEMENT
```
[CONFIRMED via Supabase SQL on DEV]

## Tag legend

- **CONFIRMED** — SQL or named automated test
- **PARTIAL** — artifact exists; acceptance not fully exercised
- **NOT FOUND** — searched and absent
