# CampXSync — Current Progress (source of truth for status)

**Branch:** `feature/java-backend-integration`  
**Updated:** 2026-11-18 (STM-10 batch 6 start; D11 recorded; sign-in worktree removed; **no push**)

## Owner decisions – STM-10 batch 6 (18-11-2026)

**Approved by:** Niranjan (product owner). Final; do not re-ask.

| ID | Decision |
|---|---|
| **D11** | Student portal reads only through existing APIs and existing database rules. No rule is relaxed for any role. |

## Owner decisions – STM-10 batch 5 (17-11-2026)

**Approved by:** Niranjan (product owner). Final; do not re-ask.

| ID | Decision |
|---|---|
| **D1** | IAM operator = Super Admin (R01), System Admin (R02), Institution Admin (R03). |
| **D7** | Portal preferences tables approved: create them in the existing student-service schema, following the same pattern as the login-link tables from Save portal preferences / login-link enabler (STM-07-EN-001) (college column, FORCE RLS on every table, a student can read and write only their own row), in Supabase project `azulbaxszsqxymqmlwfe` only, with a DDL script and evidence under `docs/reports/`. No other tables. |
| **D9** | Platform Admin (R01) cannot use the staff screens (they are for roles tied to one college: Institution Admin R03 and the delegated Registrar R31). No college selector is to be built. |
| **D10** | Student portal announcements (Configure portal settings and publish announcements, STM-10-US-023): publishers per the matrix are College Admin and Communication Manager (not the Registrar). It is **BLOCKED** because the communication service does not exist; it joins Academic history (STM-08) and Student service requests (STM-09) as a new-service item awaiting owner approval. Build nothing for it. |

## Owner decisions – STM-07 batch 2 (16-11-2026)

**Approved by:** Niranjan (product owner). Final; do not re-ask.

| ID | Decision |
|---|---|
| **STM-07-B2-D1** | "IAM operator" for STM-07 = roles **R01, R02, R03**. Update student-service role check to include **R01**. |
| **STM-07-B2-D2** | Provisioning mapping: student role **R25** applies while the student is linked and **ACTIVE**. Access is revoked on **SUSPENDED**, **DETAINED** and **WITHDRAWN**. **GRADUATED** and **ALUMNI** are **NOT** revoked automatically. Closes matrix item "student-status-to-IAM-role mapping and suspension/deprovisioning triggers". |
| **STM-07-B2-D3** | US-008/009 **UNBLOCKED**. Add a role assign/revoke endpoint inside the **existing IAM service** (institute-admin-service owns `iam.*`; identity-service remains scaffold-only), limited to role **R25**, tenant-scoped, audited, no self-assignment, only for a student linked and **ACTIVE**. student-service calls via port/adapter; **never** writes `iam` schema directly (Path B banned). Explicit exception to same-service selection: US-008/009 may span IAM + student-service. STOP if no IAM code under `services/` or if a new table/schema beyond existing role-assignment tables is required. |

## Owner decisions – STM-07 batch 3 (17-11-2026)

**Approved by:** Niranjan (product owner). Final; do not re-ask.

| ID | Decision |
|---|---|
| **STM-07-B3-D3** | Role assign/revoke endpoint hosted in **institute-admin**: **approved**. |
| **STM-07-B3-D4** | Linking requires the student's identity to be **VERIFIED**. |
| **STM-07-B3-D5** | Staff view of link status (STM-07-US-003) and See past login requests (STM-07-US-006): minimal read-only staff screen in existing frontend patterns; visible to **R01–R03** and to a **Registrar (R31)** delegated through **STM-07.ACCOUNT.A**. |
| **STM-07-B3-D6** | Audit who changed accounts and roles (STM-07-US-012): read-only, tenant-scoped list of account link/unlink and R25 assign/revoke events from **existing** audit logs, for **R01–R03**; **no new audit store**. |

## Scoreboard (session)

| Lane | Status |
|---|---|
| R31-SoD (1e) | **tested** (`dfc6788`) |
| STM-CROSS-EN-010 | **completed** — Gateway Bearer E2E harness 31/31 (`138e8d9`) |
| STM-CROSS-EN-014 | **partially completed** — STM-03..06 ops pack + regression 5/5 |
| STM-03-EN-007 | **partially completed** — README/runbook/`stm03En007_*` (enrollment 116/0) |
| STM-06-US-010 | **tested** (`0b1f73d`) |
| STM-05-US-007 | **tested** (`4b2154b`) |
| STM-06-US-006 | **tested** (`24473ff`) |
| STM-06-US-015 | **completed** — `doc.document_expiry_notifications` (`5fd1f8a`, suite 139/0) |
| Cross-tenant pen | **completed** — 34/34 (`5cfe378`) |
| RBAC FLAG-004/005 | **completed** (D8/D9); FLAG-002/003/007 open |
| STM-07-EN-001 | **completed** — `stu.student_iam_links` + provisioning requests |
| STM-07-US-001 | **completed** — `VERIFICATION_NOT_VERIFIED` gate (`912a6f9`) |
| STM-07-US-002 | **completed** — provisioning eligibility API |
| STM-07-US-003 | **completed** — staff link-status desk + B3-D5 auth (R01–R03 / R31+ACCOUNT.A) |
| STM-07-US-004 | **completed** — eligibility-gated provisioning request |
| STM-07-US-005 | **completed** — retry FAILED provisioning (desk + Path C / IAM operator) |
| STM-07-US-006 | **completed** — past login / provisioning request history (B3-D5) |
| STM-07-US-012 | **completed** — account/role audit list from existing logs (B3-D6, R01–R03) |
| STM-07-US-010 | **completed** — lifecycle revoke of IAM link |
| STM-07-US-013 | **completed** — student link-status portal strip |
| STM-07-US-008 | **completed** — assign R25 via IAM (`2675dea`) |
| STM-07-US-009 | **completed** — revoke R25 via IAM (`2675dea`) |
| STM-07-EN-002 | **completed** — existing IAM integration guards (`57dc0ff`) |
| STM-07-EN-004 | **completed** — idempotent provisioning + FAILED retry (`bf8cf6d`) |
| STM-07-EN-005 | **partially completed** — student/portal/lifecycle notify wired; admission auto-provision + event bus uncovered |
| STM-07-EN-006 | **completed** — go-live readiness checklist + security readiness tests (gaps listed, not claimed done) |
| STM-08 / STM-09 | **BLOCKED** — new service gate (design notes created) |
| IAM live delegation adapter | **partially completed** — Path C smoke **17/17 PASS**; frontend 1c Registrar mapping DONE |
| STM-06-EN-002 | **completed** — live Storage + orphan cleanup (12/12) |
| STM-06-EN-003 | **partially completed** — controlled scanner DONE; live commercial AV gated |
| STM-CROSS-EN-007 | **yet to pickup** (Kafka hold) |
| STM-10-EN-001 | **completed** — `stu.student_portal_preferences` FORCE RLS (Dev); portal-preferences API |
| STM-10-US-012 | **completed** — durable widgets via portal BFF + StudentWidgetGate (`0d4e6a8`) |
| STM-10-US-013 | **completed** — shortcuts via portal prefs + StudentShortcutsStrip (`f8a7a22`) |
| STM-10-US-014 | **completed** — language/display via portal prefs + StudentDisplayEffects |
| STM-10-EN-002 | **completed** — BFF portal-dashboard aggregation (timeouts, cache, partial failure) (`414105f`) |
| STM-10-US-018 | **completed** — partial portal UX on one-service outage + plain retry |
| STM-10-US-023 | **BLOCKED** — D10 communication service missing (with STM-08/09 new-service gate) |

## Evidence highlights

- US-012: `docs/demos/STM-06-US-012/`
- US-013: `docs/demos/STM-06-US-013/`
- EN-003: `docs/demos/STM-06-EN-003/` — HMAC signed URL + ControlledMalwareScanAdapter
- Traceability: `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`

## Caveats

- No Kafka invent (CROSS-EN-007 held).
- Malware: controlled content/heuristic scanner + fail-closed states DONE; live commercial AV deferred (new tool).
- Scan outcomes via `doc.document_links`. US-015 notification DDL applied on Dev only.
- Cross-tenant penetration suite **run** — `CROSS_TENANT_PEN_2026-11-16.json` **34/34 PASS**, 0 vulns.
- Master owner auth (2026-11-16): continue non-infra work without routine re-approval; no push unless separately authorized.
- RBAC reconcile: FLAG-004/005 **completed** (D8/D9); FLAG-002/003/007 still open.

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

### RBAC policy flags — reconcile pass (16-11-2026)

| Flag | Owner decision | CSV status |
|---|---|---|
| STM-RBAC-FLAG-004 | **D8** (15-11-2026) | `completed` — matrix stands; R10 V-only STM-03; R31 delegated exception approvals |
| STM-RBAC-FLAG-005 | **D9** (15-11-2026) | `completed` — R31 metadata U (not verified); no delete; no retention widening |
| STM-RBAC-FLAG-003 | **D3 partial** (consent owner R03 pilot only) | `yet to pickup` — guardian defaults / revocation suspension still open |
| STM-RBAC-FLAG-002 | *(none)* | `yet to pickup` — CFG configure role per service unassigned |
| STM-RBAC-FLAG-007 | D6/D12a related only (not full close) | `yet to pickup` — matrix policy-items table still open |

Backlog cells updated in `CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv`; no Java RBAC code changes (consistent with D8 “no code change”).

### Open (not decided)

- "Registrar / Admissions" in STM-01 and STM-02 is outside the decision table above; owner to confirm it also maps to Registrar.
- Pickup order for remaining open stories: awaiting owner.
- Policy items listed in the "Policy items requiring confirmation" table of `docs/architecture/Security/RBAC_Combined.docx`: awaiting owner (**STM-RBAC-FLAG-007**).
- **STM-RBAC-FLAG-002** — configure (CFG) role assignees per STM-04/06/07/09/10.
- **STM-RBAC-FLAG-003** — guardian consent defaults and immediate suspension on revocation.
- Live malware AV scanner for STM-06 (new tool) — open gap per D11.
