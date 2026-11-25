# IADM-06 Technical notes

**Service:** Platform Analytics & Usage Insights  
**Port:** 8104  
**Gateway prefix:** `/api/v1/iadm/06`  
**Package:** `com.campx.iadm.analytics`  
**Schema:** `anl` (metric catalogue + export-request metadata only)

## Health

`GET http://127.0.0.1:8104/api/v1/iadm/06/health`

## Endpoints (permission keys)

| Method | Path | Key | Audit event |
|---|---|---|---|
| GET | `/catalogue` | `iadm06.catalogue.view` | `IADM06_CATALOGUE_VIEW` |
| GET | `/overview/platform` | `iadm06.platform.view` | `IADM06_PLATFORM_VIEW` |
| GET | `/overview/institute` | `iadm06.institute.view` | `IADM06_INSTITUTE_VIEW` |
| GET | `/comparison` | `iadm06.platform.view` | `IADM06_PLATFORM_VIEW` |
| GET | `/entitlements` | `iadm06.entitlement.view` | `IADM06_ENTITLEMENT_VIEW` |
| GET | `/trends` | platform or institute view | `IADM06_PLATFORM_VIEW` |
| POST | `/exports` | `iadm06.export.request` | `IADM06_EXPORT_REQUEST` |
| GET | `/exports` / `/exports/{id}` | `iadm06.export.view` | `IADM06_EXPORT_RESULT` |
| GET | `/exports/{id}/csv` | export view | streamed CSV (not stored) |

## Source contracts

- IADM-01 additive: `GET /api/v1/iadm/01/analytics-aggregates`
- IADM-04 additive: `GET /api/v1/iadm/04/analytics-aggregates`
- IADM-05 resolve: `GET /api/v1/iadm/05/domain/resolve-policy?policyType=RETENTION&domainCode=ANALYTICS` (data-handling)
- IADM-05 export gate: `policyType=EXPORT` (DEMO-ANALYTICS-EXPORT)

## Runtime DEMO ONLY settings

- Suppression threshold: `POST /settings/suppression` `{ "threshold": N }` — not a privacy standard
- Outage flags: `POST /dev/outage-flags`

## UI desks

- `/platform/analytics` — R01 / R02
- `/platform/analytics/audit` — R28
- `/institution/analytics` — R03
