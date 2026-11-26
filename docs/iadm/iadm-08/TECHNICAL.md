# IADM-08 Technical notes

**Service:** `iadm-08-audit-health-ops-service`  
**Port:** `8106`  
**Gateway prefix:** `/api/v1/iadm/08`  
**Package:** `com.campx.iadm.ops`

## Responsibility

Read-only audit investigation over shared `audit.events` and on-request health aggregation. Creates no audit events (except read-recording via `audit.log_event`), stores no health samples, and owns **no schema/tables**.

## Endpoints

| Method | Path | Meaning |
|---|---|---|
| GET | `/health` | Process health (unauthenticated) |
| GET | `/limits` | DEMO ONLY query limits (31 days / 50 rows defaults) |
| GET | `/audit/search?from&to&action&tenantId&denialOnly&page` | Platform/institute audit search |
| GET | `/audit/events/{id}` | Safe-field event detail |
| GET | `/audit/timeline/{actorId}?from&to` | Actor activity timeline |
| GET | `/health/inventory` | Server-side health target list |
| GET | `/health/overview` | On-request health probe of inventory |
| GET | `/health/targets/{id}` | One target detail |
| GET | `/alerts` | Read-only alerts (+ IADM-07 GET /alerts) |
| GET | `/iadm07/evidence?kind=` | Link/preview IADM-07 evidence |
| GET | `/sla` | Honest “not available” placeholder |
| POST | `/dev/outage-flags` | DEMO ONLY fault switches |
| — | `/export`, `/acknowledge` | **Absent** — refused as not available |

## Permission keys (`iadm08.*`)

| Key | Meaning |
|---|---|
| `iadm08.audit.platform.view` | Platform audit investigation |
| `iadm08.audit.institute.view` | Own-institute audit |
| `iadm08.health.view` | Health overview/detail |
| `iadm08.alerts.view` | Read-only alerts |
| `iadm08.manage` | Reserved; not granted; not implemented |

Legacy `adm08.*` (iam profiles/groups) untouched.

## Read-audit event names

`IADM08_AUDIT_SEARCH`, `IADM08_AUDIT_DETAIL`, `IADM08_AUDIT_TIMELINE`, `IADM08_HEALTH_OVERVIEW`, `IADM08_HEALTH_DETAIL`, `IADM08_ALERTS_READ`

## IADM-05 gate

Resolves `RETENTION` + domain `OPERATIONS` via IADM-05 `/domain/resolve-policy`. DEMO ONLY policy `DEMO-OPERATIONS-HANDLING` v1 published (author R02 ≠ approver R01).

## Persistence

**None.** Confirmed: no `iadm08` / `ops` schema.
