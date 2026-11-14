# CampXSync — Current Progress (source of truth for status)

**Branch:** `feature/java-backend-integration`  
**Updated:** 2026-11-14 (CROSS/RBAC partials cleared; Kafka invent deferred)

## Scoreboard (session)

| Lane | Status |
|---|---|
| STM-03/04/05 EN (scoped) | **completed** (selected yet-to-pickup deferred) |
| STM-06-EN-001/004/005/006 | **completed** |
| STM-10-EN-005/006/007 | **completed** |
| STM-CROSS-EN-001…006,008,009,011…013 | **completed** |
| STM-RBAC-EN-001 | **completed** |
| STM-CROSS-EN-007 | **yet to pickup** (no Kafka invent) |

## Evidence highlights

- Schema audit: `docs/reports/STM_CROSS_EN_001_SCHEMA_AUDIT.md`
- Ownership map: `docs/reports/STM_CROSS_EN_002_SERVICE_OWNERSHIP.md`
- Demos under `docs/demos/STM-CROSS-EN-*` and `STM-RBAC-EN-001`

## Caveats

- Prefer partially completed before yet to pickup — **no remaining partials** except deferred invent/policy items.
- Headed Playwright demos required each story.
- No DEPARTMENT_STAFF / Kafka invent; no unapproved DDL.
- Cross-tenant penetration suite still not run.

## Canonical files

| Role | Path |
|---|---|
| Backlog | `docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` |
| Next task | `docs/prompts/NEXT_TASK.md` |
