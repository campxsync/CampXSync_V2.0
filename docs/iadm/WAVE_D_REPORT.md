# IADM Wave D Report

**Date:** 2026-11-21  
**Services:** IADM-04 Subscription, Billing & Entitlements (:8102), IADM-06 Platform Analytics & Usage (:8104)

## Plain-language map

| Code | Meaning |
|---|---|
| IADM-04 | Subscription, Billing & Entitlements |
| IADM-06 | Platform Analytics & Usage |
| `plat.check_entitlement` | Fail-closed entitlement check for CADM and all services |
| `iadm04.*` / `iadm06.*` | Permission keys |

## Migrations (applied DEV)

| Forward | Rollback |
|---|---|
| `20261121000500_iadm_wave_d_billing_and_analytics.sql` | `20261121000500_rollback__iadm_wave_d_billing_and_analytics.sql` |
| `20261121000600_iadm_wave_cd_role_grants.sql` | `20261121000600_rollback__iadm_wave_cd_role_grants.sql` |

## CADM / cross-service contract

`plat.check_entitlement(tenant_id, entitlement_key)` — **CONFIRMED** live: unknown key → `allowed=false`, `deny_reason=UNKNOWN_ENTITLEMENT` (fail closed).

## Stories

| ID | Status | Notes |
|---|---|---|
| IADM-04-US-001/002/005 | EXISTS | Remain on monolith `/api/v1/admin/billing/*` (:8081) during move |
| IADM-04-US-003/004/007 | PARTIAL | View/request/auditor paths deepen on :8102 next pass |
| IADM-04-US-006 Entitlement check | done | `GET /api/v1/iadm/04/entitlements/check?key=` + SQL function |
| IADM-06-US-001…005 | PARTIAL→core | `GET /api/v1/iadm/06/usage` aggregates `plat.usage_metrics` with `iadm06.view` |

## Gateway

`/api/v1/iadm/04` → :8102 · `/api/v1/iadm/06` → :8104 [CONFIRMED GatewayConfig.java]

## Tests

| Suite | Result |
|---|---|
| `iadm-04` `Iadm04PortTest` | PASS 1/0 |
| `iadm-06` `Iadm06PortTest` | PASS 1/0 |
| Live DEV entitlement fail-closed | PASS (SQL) |

## Deviations

- Billing plan/subscription CRUD not duplicated onto :8102 yet (EXISTS on monolith; gateway `/api/v1/admin` still routes there).
- Analytics export (`iadm06.export`) key granted; dedicated export endpoint not yet separate from `/usage`.
