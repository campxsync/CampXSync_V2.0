# IADM-06 S0 Report — Foundation (US-016…022)

**Date:** 2026-11-25  
**Branch:** `feature/java-backend-integration`  
**DB:** DEV `azulbaxszsqxymqmlwfe` only  
**Service:** IADM-06 Platform Analytics & Usage Insights — port **8104**, path `/api/v1/iadm/06`

## Story outcomes

| Story ID | Story name | Target | ACTUAL |
|---|---|---|---|
| IADM-06-US-016 | Preflight inventory and reconciliation | API-ready | **completed** — `docs/iadm/iadm-06/US016_PREFLIGHT_REPORT.md` |
| IADM-06-US-017 | IADM-06 service scaffold | API-ready | **API-ready** — `:8104`, gateway route, health, logging |
| IADM-06-US-018 | Permission keys and role mapping | API-ready | **API-ready** — `iadm06.*` keys + offline matrix tests |
| IADM-06-US-019 | Schema, tenant scoping and RLS | API-ready | **API-ready** — `anl.metric_catalogue`, `anl.export_requests` FORCE RLS |
| IADM-06-US-020 | Shared audit integration | API-ready | **API-ready** — `DurableAuditWriter` → `audit.log_event` fail-closed |
| IADM-06-US-021 | IADM-05 policy resolution client | API-ready | **API-ready** (resolve gate); outcome-on-view via request_id **partial** (finding) |
| IADM-06-US-022 | Source read clients IADM-01/04 | API-ready | **API-ready** — clients + additive owner endpoints |

## Conventions reported (D6)

| Item | Value |
|---|---|
| Port | **8104** |
| Gateway route | `/api/v1/iadm/06` → `http://localhost:8104/api/v1/iadm/06` |
| Route collision | **None** vs legacy `/api/v1/admin/...` |
| Permission keys | `iadm06.platform.view`, `iadm06.institute.view`, `iadm06.entitlement.view`, `iadm06.export.request`, `iadm06.export.view`, `iadm06.catalogue.view` (manage reserved) |
| Audit events | `IADM06_PLATFORM_VIEW`, `IADM06_INSTITUTE_VIEW`, `IADM06_ENTITLEMENT_VIEW`, `IADM06_EXPORT_REQUEST`, `IADM06_EXPORT_RESULT`, `IADM06_CATALOGUE_VIEW` |

## Migrations applied on DEV

| Migration | Meaning |
|---|---|
| `iadm06_foundation_keys` | Permission keys + role grants |
| `iadm06_anl_schema` | `anl` schema FORCE RLS |
| `iadm06_catalogue_and_policy_seed` | M01–M10 + DEMO analytics IADM-05 policies |

## Additive owner endpoints (separate commits)

- IADM-01: `GET /api/v1/iadm/01/analytics-aggregates`
- IADM-04: `GET /api/v1/iadm/04/analytics-aggregates`

## Findings carried forward

1. IADM-05 `reportOutcome` requires a governance `request_id` — dataset-view outcome callback remains **partial**.
2. Production suppression threshold remains **OWNER-OPEN**.

## Unit tests

- `AnalyticsPermissionMatrixTest` — role × key denials (US-018)
- `SuppressionEngineTest` — D4 small-group + subtraction guard (US-024 foundation)

## Next

Continue S1 (catalogue UI walk, aggregation engine demos, headed personas) without stopping for small approvals.
