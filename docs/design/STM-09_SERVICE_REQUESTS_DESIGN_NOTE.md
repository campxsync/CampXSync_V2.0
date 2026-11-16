# STM-09 Student Service Requests — Design Note

**Status:** BLOCKED — awaiting owner approval to create a new microservice (no code under `services/` for STM-09).  
**Date:** 2026-11-16  
**Queue rule:** New Service Gate (Agreement V2.1 / owner queue). Do not implement stubs or alternative hosts.

---

## 1. Purpose

Configurable student service-request catalogue, draft/submit/verify/approve workflows, SLA/escalation, certificate delivery routing, and cross-module handoffs (STM-01 profile corrections, STM-08 academic history).

## 2. Proposed data model (Supabase PostgreSQL only)

| Table | Role |
|---|---|
| `stu.service_request_types` | Tenant-configured request types |
| `stu.service_requests` | Request instances + status |
| `stu.service_request_attachments` | Links to STM-06 documents |
| `stu.service_request_approvals` | Workflow steps / decisions |
| `stu.service_request_sla` | SLA configuration and breach markers |

All new tenant tables: `ENABLE` + `FORCE ROW LEVEL SECURITY`.

## 3. API list (draft)

- `GET/POST /api/v1/service-requests/types`
- `GET/POST /api/v1/service-requests`
- `POST /api/v1/service-requests/{id}/submit`
- `POST /api/v1/service-requests/{id}/verify`
- `POST /api/v1/service-requests/{id}/decide`
- `GET /api/v1/service-requests/{id}/history`
- SLA report and escalation endpoints per EN stories

## 4. RBAC rows used

Backlog CSV STM-09-US-001..021 / EN-001..008. FLAG-002 (CFG assignees) and FLAG-007 remain owner-parked — block configure stories until decided.

## 5. Dependencies

- Gateway/IAM, STM-01/03/04/06/08/10
- Shared notification + document generation (existing only)
- No Kafka until CROSS-EN-007 unblocked

## 6. Test plan

- Unit: state machine, SoD, tenant isolation, CSV negative tests
- Browser E2E after UI desks exist
- Dev DB `azulbaxszsqxymqmlwfe` only

## 7. Deployment needs (existing patterns only)

- New Java service under `services/` after owner approval
- Gateway HMAC route, stack scripts, OpenAPI

## 8. Open decisions (owner)

1. Approve `service-request-service` (or equivalent) creation.
2. Decide FLAG-002 configure-role assignees for STM-09.
3. Confirm certificate generation host (STM-06 vs new).
4. Confirm SLA escalation without Kafka (polling vs deferred bus).

### Open items delta

- 2026-11-16: Gate applied; no implementation until owner approval.
