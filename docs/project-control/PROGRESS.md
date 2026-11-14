# CampXSync — Current Progress (source of truth for status)

**Branch:** `feature/java-backend-integration`  
**Updated:** 2026-11-14 (STM-03 EN-001…006 cleared with headed demos)

## Scoreboard (session)

| Lane | Status |
|---|---|
| STM-03 US | completed (prior) |
| STM-03 EN-001…006 | **completed** + headed demos under `docs/demos/STM-03-EN-00x/` |
| Remaining partials | ~29 EN/CROSS (next: **STM-04-EN-001**) |

## Evidence highlights

- EN-001 LivePostgres **2/2**; EN-004 GatewayRouteTest **3/3** + OpenAPI **1/1**; Domain suite **97/97**
- Headed demos: EN-001…004 **7/7**, EN-005 **12/12**, EN-006 **7/7**
- EN-006: in-service alerts + intelligence emit (`liveEndpoint=false`); external Kafka not invented

## Caveats

- Prefer partially completed before yet to pickup.
- Headed Playwright demos required each story.
- No DEPARTMENT_STAFF invent; no unapproved DDL; no push without ask.

## Canonical files

| Role | Path |
|---|---|
| Backlog | `docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` |
| Next task | `docs/prompts/NEXT_TASK.md` |
