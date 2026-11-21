# IADM Wave B Report

**Date:** 2026-11-21  
**Services:** IADM-03 Platform Identity & Access (:8101), IADM-08 Audit/Health/Ops (:8106)

## Stories

| ID | Status | Notes |
|---|---|---|
| IADM-03-US-001 | PARTIAL→service | `GET /roles` with `adm09.view` |
| IADM-03-US-002 | NEW→done | role ceilings table + API (CADM-03 contract) |
| IADM-03-US-003 | PARTIAL | still primarily on monolith; grant table enforcement exists in DB |
| IADM-03-US-004 | NEW→done | access-change requests + decide (no self-approve) |
| IADM-03-US-005/006 | PARTIAL | remain on monolith until move completes |
| IADM-03-US-007 | NEW | via audit/ceiling reads |
| IADM-03-US-008/009 | DEFERRED | unchanged |
| IADM-08-US-001 | PARTIAL→service | audit search endpoint |
| IADM-08-US-002/003 | PARTIAL | monolith audit paths still active |
| IADM-08-US-004/005 | NEW→done | ops health from `plat.service_health` |
| IADM-08-US-006 | NEW→done | `audit.self_check_immutable()` |

## Migrations

- `20261121000300_iadm_wave_b_identity_and_audit.sql` (+ rollback) — **applied DEV**

## Tests

| Suite | Result |
|---|---|
| `iadm-03` `Iadm03PortTest` | PASS 1/0 |
| `iadm-08` `Iadm08PortTest` | PASS 1/0 |
| Reactor with Waves B–D modules | exit 0 |

## Gateway

`/api/v1/iadm/03` → :8101 · `/api/v1/iadm/08` → :8106 · `/api/v1/admin` still :8081 (monolith during move)

## CADM contract

Role ceiling lookup: `GET/POST .../iadm/03/ceilings` · Shared audit search: `GET .../iadm/08/audit/search`
