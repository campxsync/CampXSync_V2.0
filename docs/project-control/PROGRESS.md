# CampXSync — Current Progress (source of truth for status)

**Branch:** `feature/java-backend-integration`  
**Updated:** 2026-11-14 (Priority queue STM-05-EN-002 → US-012 → US-013 → EN-003 complete; no commit until authorized)

## Scoreboard (session)

| Lane | Status |
|---|---|
| STM-05-EN-002 | **completed** (demo 8/8; no commit) |
| STM-06-US-012 | **completed** (demo 7/7; no commit) |
| STM-06-US-013 | **completed** (demo 7/7; no commit) |
| STM-06-EN-003 | **completed** (demo 7/7; malware liveEndpoint=false; no commit) |
| STM-CROSS-EN-007 | **yet to pickup** (Kafka hold) |
| STM-06-EN-002 | **yet to pickup** (Storage bytes) |

## Evidence highlights

- US-012: `docs/demos/STM-06-US-012/`
- US-013: `docs/demos/STM-06-US-013/`
- EN-003: `docs/demos/STM-06-EN-003/` — HMAC signed URL + ControlledMalwareScanAdapter
- Traceability: `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`

## Caveats

- No Kafka invent (CROSS-EN-007 held).
- Malware AV live endpoint deferred (heuristic + EICAR gate only).
- No unapproved DDL; scan outcomes via `doc.document_links`.
- Cross-tenant penetration suite still not run.
- **Awaiting owner `APPROVED …` / `APPROVED … COMMIT`.**

## Canonical files

| Role | Path |
|---|---|
| Backlog | `docs/user-stories/CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` |
| Next task | `docs/prompts/NEXT_TASK.md` |
