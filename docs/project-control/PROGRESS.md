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

## Owner decisions – RBAC roles (STM-RBAC-FLAG-001)

**Date:** 15-11-2026  
**Approved by:** Niranjan (product owner)

D1. Registrar: APPROVED as a NEW canonical catalogue role (option A). Catalogue code to be assigned by the owner; do not invent one. Department staff act on Registrar functions by delegation from the HOD (R08). Labels that map to Registrar: "Registrar / Department Staff", "Registrar / delegated approver", "Authorized Registrar / verifier", and "Assigned verifier / Registrar" (limited to assigned documents). Status: DECIDED, NOT YET CREATED in the system. Creating the role is a separate IMPLEMENT task needing its own approval. Existing STM-03..06 behavior for the Registrar must be matched when the role is created.

D2. "Assigned reviewer / approver": not a role. Resolved by assignment to existing roles (R08, R10, Registrar).

D3. "Consent/access policy owner": R03 Institution Admin, scoped to consent decisions, for the pilot.

D4. "External Verification Agency": a new restricted external role, to be approved later. Until then no access (fail-closed). Not R27 API Client and not R14. STM-06-US-017 is deferred.

D5. "Faculty / Department Staff": Faculty = R10; department staff by HOD delegation.

D6. "Provisioning service / IAM operator": the human operator = R02 System Admin (or R03 within one institution). The provisioning service is a service identity, not a role.

D7. Named in the flag text: Academic Dean -> R07 Dean; Faculty / Advisor -> R10 Faculty; Exam Cell / Controller -> R13 and R12.

### Open (not decided)

- "Registrar / Admissions" in STM-01 and STM-02 is outside the decision table above; owner to confirm it also maps to Registrar.
- STM-RBAC-FLAG-004 and STM-RBAC-FLAG-005 defaults, and the pickup order for the open stories: awaiting owner.
- Policy items listed in the "Policy items requiring confirmation" table of `docs/architecture/Security/RBAC_Combined.docx`: awaiting owner.
