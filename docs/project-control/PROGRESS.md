# CampXSync — Current Progress (source of truth for status)

**Branch:** `feature/java-backend-integration`  
**Updated:** 2026-11-15 (R31 pilot Auth+IAM applied on Dev; Path C live allow verified)

## Scoreboard (session)

| Lane | Status |
|---|---|
| R31-SoD (1e) | **tested** (`dfc6788`) |
| STM-CROSS-EN-010 | **partially completed** (`7a41325`); e2e harness **BLOCKED** |
| STM-06-US-010 | **tested** (`0b1f73d`) |
| STM-05-US-007 | **tested** (`4b2154b`) |
| STM-06-US-006 | **tested** (`24473ff`) |
| STM-06-US-015 | **partially completed** (`7a41325`); DB notifications **BLOCKED** (DDL) |
| IAM live delegation adapter | **partially completed** — Path C Phase 2 + privileged registrar join; pilot Auth+IAM applied on Dev after `APPROVED R31-PILOT-WRITE azulbaxszsqxymqmlwfe`; live Path C allow STM-02..06; full STM HTTP stack smoke **BLOCKED** (RAM ~1.36 GB free) |
| STM-06-EN-002 / EN-003 | **partially completed** (Phase B apply awaiting owner) |
| STM-CROSS-EN-007 | **yet to pickup** (Kafka hold) |

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

D1. Registrar: APPROVED as a NEW canonical catalogue role (option A). Catalogue code **R31** (D10). Department staff act on Registrar functions by delegation from the HOD (R08). Labels that map to Registrar: "Registrar / Department Staff", "Registrar / delegated approver", "Authorized Registrar / verifier", and "Assigned verifier / Registrar" (limited to assigned documents). Status: DECIDED; **Phase A service recognition implemented/tested**; IAM seed apply = Phase B (D12c).

D2. "Assigned reviewer / approver": not a role. Resolved by assignment to existing roles (R08, R10, Registrar).

D3. "Consent/access policy owner": R03 Institution Admin, scoped to consent decisions, for the pilot.

D4. "External Verification Agency": a new restricted external role, to be approved later. Until then no access (fail-closed). Not R27 API Client and not R14. STM-06-US-017 is deferred.

D5. "Faculty / Department Staff": Faculty = R10; department staff by HOD delegation.

D6. "Provisioning service / IAM operator": the human operator = R02 System Admin (or R03 within one institution). The provisioning service is a service identity, not a role.

D7. Named in the flag text: Academic Dean -> R07 Dean; Faculty / Advisor -> R10 Faculty; Exam Cell / Controller -> R13 and R12.

D8 (STM-RBAC-FLAG-004, decided 15-11-2026, Niranjan): The STM matrix stands. Faculty/Advisor is view-only on STM-03. Enrolment exception approvals are by the Registrar when delegated. If an institution needs a different approval authority, it is handled later through change control with policy owner, scope, effective date and audit trail. No code or matrix change now.

D9 (STM-RBAC-FLAG-005, decided 15-11-2026, Niranjan): A Registrar may correct document metadata (audited, never on an already-verified document). No delete right (archive only). No widening of retention writes. STM-06-US-006 is unblocked by this decision; R31 Phase A recognition is in place — **US-006 implemented/tested** (`24473ff`).

D11 (STM-06-EN-002 Phase A resume, decided 15-11-2026, Niranjan): Config key names approved for document-service: CAMPX_SUPABASE_URL, CAMPX_SUPABASE_SERVICE_ROLE_KEY, CAMPX_STORAGE_BUCKET (default "student-documents"). The service-role key is server-side only in the document-service environment; never in frontend or public env, Git, logs, errors, or reports. JDK built-in HTTP only (HttpURLConnection on the JDK in use); no new dependency. Add optional contentBase64 to the existing JSON upload. Bytes are REQUIRED whenever storage is configured; metadata-only upload is allowed only for the in-memory/test profile. Validate: decoded size == sizeBytes; SHA-256 == checksumSha256; type on allowlist with a signature check against the declared mimeType; reuse existing limits (10 MB; application/pdf, image/jpeg, image/png, image/webp). Download streams through the service on the existing route after the signed-token check; do not issue Supabase signed URLs to clients. Bytes never in a relational table; no direct client-to-Supabase path. Malware scanning stays OUT OF SCOPE (needs a scanner = new tool); record it as an open gap. Phase A uses a fake storage client in tests; no Supabase connection or write.

D10 (decided 15-11-2026, Niranjan): Registrar is canonical role R31, scoped to the assigned campus, separate from Academic Admin (inherits no Academic Admin/ACD/LMS rights), approvals by delegation only, no delete, same actor cannot verify and approve in one workflow. No permission outside the STM action-level table is granted; everything else fails closed.

D12a (decided 15-11-2026, Niranjan): Delegation: wire STM approve/reject to the existing IAM delegations through a small port; fake in unit tests; fail-closed when no active delegation. If reading delegations needs a cross-schema read, a new service contract or new infrastructure: STOP and give a design note; do not build it.

D12a-follow-on (decided 15-11-2026, Niranjan): **M1** mapping + **Path C** central IAM contract + **no cache** + authoritative check on every A/R + **fail closed**.

D12a-Phase2 (decided 15-11-2026, Niranjan): implement institute-admin active-check API, gateway route, STM `HttpIamDelegationPort` (M1, no cache, fail closed). **Implemented** — `GET /api/v1/admin/internal/delegations/active-check`; role lookup via `iam.roles` inside institute-admin; STM factory uses `CAMPX_IAM_DELEGATION_BASE_URL`. Path B / M2 / M3 not authorized. R31 seed apply still separate.

D12b (decided 15-11-2026, Niranjan): Campus: carry the assigned campus (IAM college_id) on the STM actor from gateway claims and enforce it for Registrar actions.

D12c (decided 15-11-2026, Niranjan): IAM seed: prepare, do not apply, docs/runbooks/R31_REGISTRAR_STAGING_SETUP.md (role template, seed, rollback).

D12d (decided 15-11-2026, Niranjan): College Admin and Institution Admin stop being treated as Registrar in STM-02..STM-06, except where the matrix grants College Admin explicitly (STM-01 scoped V/U, STM-07 delegated trigger, STM-10 portal settings). Temporary staging-only deviation: flag CAMPX_STM_LEGACY_REGISTRAR_STANDIN, default OFF in code, set ON only in the staging environment, documented as TEMPORARY with expiry to be set by the owner (tie to first real college onboarding).

D12e (decided 15-11-2026, Niranjan): From now on every task runs the FULL test suite of each module touched and reports every failure, including pre-existing ones.

### EN-002 / EN-003 partial reason

- STM-06-EN-002 and STM-06-EN-003 marked **partially completed** because before Phase A there was no byte store and no content validation against real bytes; EN-003 delivered HMAC signed URL + heuristic malware adapter (`liveEndpoint=false`) without Storage-backed bytes. Phase A adds the Storage abstraction and content validation with fake-backed tests; live Staging apply is Phase B.

### Open (not decided)

- "Registrar / Admissions" in STM-01 and STM-02 is outside the decision table above; owner to confirm it also maps to Registrar.
- Pickup order for remaining open stories: awaiting owner.
- Policy items listed in the "Policy items requiring confirmation" table of `docs/architecture/Security/RBAC_Combined.docx`: awaiting owner.
- Live malware AV scanner for STM-06 (new tool) — open gap per D11.
