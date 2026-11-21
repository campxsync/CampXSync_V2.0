# IADM Wave A Report

**Date:** 2026-11-21  
**Services:** IADM-01 Institute & Tenant Lifecycle (:8099), IADM-02 Platform Configuration & Policy (:8100)  
**DB:** DEV `azulbaxszsqxymqmlwfe` only  

## Stories

| ID | Plain meaning | Status |
|---|---|---|
| IADM-01-US-001 | Register institute (tenant) | implemented (new service endpoint) |
| IADM-01-US-002 | Appoint Institution Admin (R03) via hardened `plat.assign_institution_admin` | implemented + Step 0.1 fix |
| IADM-01-US-003 | Onboard college + first College Admin (R04) | implemented (handover row PENDING) |
| IADM-01-US-004 | Complete college handover record | implemented |
| IADM-01-US-005 | View institute profile/status | implemented |
| IADM-01-US-006 | Suspend/reactivate institute | implemented |
| IADM-01-US-007 | Lifecycle request (R03) / decide (R01) | implemented |
| IADM-01-US-008 | Lifecycle history (Auditor/view) | implemented |
| IADM-02-US-001 | Configuration catalogue with lock/delegable flags | implemented |
| IADM-02-US-002 | Platform locks via `platform_locked` | implemented (definition upsert) |
| IADM-02-US-003 | Institute values allow-list/lock enforced | implemented |
| IADM-02-US-004 | Delegate setting to college | implemented |
| IADM-02-US-005 | Effective resolve API (`cfg.resolve_effective_setting`) | implemented (CADM-01 contract) |
| IADM-02-US-006 | Effective view for College Admin path | via effective endpoint + `adm05.view` |
| IADM-02-US-007 | History/rollback | PARTIAL — still on monolith `/api/v1/admin/configuration/*` until move completes |
| IADM-02-US-008 | Auditor read | PARTIAL — uses `adm05.view`; dedicated auditor desk later |

## Migrations

| Forward | Rollback |
|---|---|
| `20261121000100_iadm_assign_institution_admin_auth_uid.sql` | `20261121000100_rollback__iadm_assign_institution_admin_auth_uid.sql` |
| `20261121000200_iadm_wave_a_lifecycle_and_config.sql` | `20261121000200_rollback__iadm_wave_a_lifecycle_and_config.sql` |

Applied to DEV via Supabase MCP [CONFIRMED]. After snapshot: tables present, `platform_locked` col yes, `resolve_effective_setting` yes.

## Endpoints (gateway)

| Prefix | Downstream |
|---|---|
| `/api/v1/iadm/01` | `http://localhost:8099/api/v1/iadm/01` |
| `/api/v1/iadm/02` | `http://localhost:8100/api/v1/iadm/02` |
| `/api/v1/admin` | still `:8081` (kept working during move) |

## Tests

| Module | Result |
|---|---|
| `iadm-01-tenant-lifecycle-service` (`TenantLifecycleRbacTest` + `TenantLifecycleLiveDevTest`) | **PASS** |
| `iadm-02-platform-config-service` (`PlatformConfigLiveDevTest`) | **PASS** |

## Deviations / exceptions

- Frontend out of scope (per kickoff).
- IADM-02 history/rollback remains on monolith until Wave cleanup move (PARTIAL).
- First College Admin Auth link (SEC-11) still open — logged in `docs/iadm/FINDINGS.md`.
- Monolith handlers not yet deleted (by design: keep working until route switch).

## Findings

See `docs/iadm/FINDINGS.md` (SEC-01 fixed; SEC-02 partial).
