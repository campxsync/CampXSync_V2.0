# IADM Wave C Report

**Date:** 2026-11-21  
**Services:** IADM-05 Data Governance & Privacy (:8103), IADM-07 Security & Compliance (:8105)  
**Owner approval:** Niranjan V.G. 2026-11-21 (Waves A–D DEV only)

## Plain-language map

| Code | Meaning |
|---|---|
| IADM-05 | Data Governance & Privacy (retention, DSR, legal hold) |
| IADM-07 | Security & Compliance Governance (policies, findings) |
| `iadm05.*` / `iadm07.*` | Permission keys (never role names) |
| R03 | Institution Admin |
| R01 / R02 | Super Admin / System Admin (platform path via `iam.is_platform_admin()`, not role templates) |
| RLS | Row-level security in Supabase PostgreSQL |

## Migrations (applied DEV `azulbaxszsqxymqmlwfe`)

| Forward | Rollback |
|---|---|
| `20261121000400_iadm_wave_c_privacy_and_security.sql` | `20261121000400_rollback__iadm_wave_c_privacy_and_security.sql` |
| `20261121000600_iadm_wave_cd_role_grants.sql` (shared C/D grants) | `20261121000600_rollback__iadm_wave_cd_role_grants.sql` |

**Tables (FORCE RLS):** `plat.data_subject_requests`, `plat.legal_holds`, `plat.security_findings`, `plat.institute_security_policies`  
**Live smoke [CONFIRMED]:** rolled-back INSERT into `plat.data_subject_requests` returned `status=OPEN` then ROLLBACK.

## Stories

| ID (plain) | Status | Endpoints / notes |
|---|---|---|
| IADM-05-US-001/002 Retention & notices | PARTIAL | Schema ready; retention CRUD still on monolith paths where present |
| IADM-05-US-003 Data-subject requests | NEW→done (core) | `GET/POST /api/v1/iadm/05/dsr` + `iadm05.view`/`iadm05.write` |
| IADM-05-US-004/005 Erasure/export execute | PARTIAL | Keys `iadm05.execute` granted; handlers to deepen |
| IADM-05-US-006 Legal hold | NEW→done (core) | `POST /api/v1/iadm/05/legal-holds` |
| IADM-05-US-007 Guardian-consent under-18 | DEFERRED | D-RBAC-M17 unchanged |
| IADM-05-US-008 | PARTIAL | Classification tables path remains thin |
| IADM-07-US-001 Institute security policy | NEW→done (core) | `POST /api/v1/iadm/07/policies` + `iadm07.write` |
| IADM-07-US-002 Posture view | PARTIAL | View key granted; aggregate posture endpoint thin |
| IADM-07-US-003 Findings | NEW→done (core) | `POST /api/v1/iadm/07/findings` |
| IADM-07-US-004 Platform minimums | PARTIAL | R01/R02 via platform admin path |
| IADM-07-US-005/006 | PARTIAL | Remaining acceptance clauses documented for CADM follow-on |

## Gateway

`/api/v1/iadm/05` → `http://localhost:8103/api/v1/iadm/05`  
`/api/v1/iadm/07` → `http://localhost:8105/api/v1/iadm/07`  
[CONFIRMED: `api-gateway/.../GatewayConfig.java` lines 59, 61]

## Tests

| Suite | Result |
|---|---|
| `iadm-05` `Iadm05PortTest` | PASS 1/0 |
| `iadm-07` `Iadm07PortTest` | PASS 1/0 |
| Reactor with Waves B–D modules | exit 0 |

## Role grants [CONFIRMED]

- `INSTITUTION_ADMIN` (R03) and `AUDITOR` (R28) received `iadm05.*` / `iadm07.*` via `iam.resync_system_roles()`.
- Template rows for `PLATFORM_ADMIN` / `SYSTEM_ADMIN` do **not** map to `iam.role_templates` codes (those roles are not in the template catalogue). Platform authority remains `iam.is_platform_admin()`. Logged in `docs/iadm/FINDINGS.md`.

## Deviations / exceptions

- Full acceptance depth for every US clause not exhausted; core CADM-facing privacy/security tables + mutation endpoints shipped.
- Monolith `:8081` still serves overlapping admin routes during move (by design).

## Findings opened this wave

See `FINDING-IADM-RBAC-01` in `docs/iadm/FINDINGS.md`.
