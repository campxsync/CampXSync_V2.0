# CampXSync — Completion Report (running)

This is the **single running completion report** for ongoing work under Agreement V2.1.  
Historical per-wave reports remain under `docs/reports/wave-*/COMPLETION_REPORT.md` and are **history only**, not the live assignment or status source.

---

## Entry 1 — Repository consolidation (2026-11-14)

**Task ID:** REPOSITORY_CONSOLIDATION_V2  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Backup branch:** `backup/pre-consolidation-2026-11-14` @ `6673d0d`  
**Filesystem backup:** `D:\CampXSync\_backup\pre-consolidation-2026-11-14\`

### Scope completed
- Added Agreement V2.1 and RBAC-validated backlog CSV as canonical story backlog.
- Archived superseded progress/status ledgers under `docs/archive/`.
- Removed duplicate/superseded next-task and Wave_Control_Revision copies (identical SHA-256 or authorized stale copies).
- Created live `PROGRESS.md`, rewritten `NEXT_TASK.md` (PROPOSED), and this running `COMPLETION_REPORT.md`.
- Appended `Implementation status` column to the **repo** backlog CSV (owner override: STM-06-US-002 → partially completed). Documentation-folder CSV untouched.
- Updated `AGENTS.md` Sources of truth and `CURSOR_START_HERE.md` next-task pointers.

### Database
None.

### Tests
None required for consolidation (documentation/git only).

### Proposed next story (not implemented)
`STM-03-US-001` — Convert approved admission into enrolment (STATUS: PROPOSED – awaiting owner approval).

### Deviations / STOP items
- Root untracked `classpath.txt` **not deleted**: repo greps match `classpath.txt` (service-local classpath files and docs). Left in working tree for owner decision.
- Documentation-folder `CampXSync_STM-03_to_STM-10_Final_User_Stories.csv` was already absent; repo copy was backed up then deleted as SUPERSEDED.

### Unverified
Audit scoreboard remains NOT owner-verified.

---

## Entry 2 — STM-03-US-001 Convert approved admission into enrolment (2026-11-14)

**Task ID:** STM-03-US-001  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Base HEAD:** `9fb4727` (pushed consolidation); story changes **uncommitted** pending approval.

### Scope completed
- Confirmed AC + required negative tests against enrollment-service; closed gaps only.
- `openFromHandover`: hydrate persistence before duplicate/idempotent checks; publish `student.enrolment.opened.v1`.
- Aligned enrollment `GatewayHmacVerifier` to `GatewayHmacProtocol` (epoch ms/seconds + body SHA-256).
- Idempotent `createTerm` on unique `(tenant, year_code, term_code)`.
- College enrolment desk `data-testid` contracts + `docs/stories/STM-03-US-001.demo.json`.
- Did **not** invent `DEPARTMENT_STAFF` grants (ROLE-CATALOGUE GAP).

### Database
- Dev ref `azulbaxszsqxymqmlwfe` only.
- Demo seed cleaned prior demo enrolment rows; live read-back found PENDING `stu.enrolments` for application `c3010000-0000-4000-8000-000000000001`.

### Tests (§7A)

| Command | Environment | Exit | Result |
|---|---|---:|---|
| `mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest` | JDK 8 / Maven; offline unit | 0 | 32 run, 0 fail (incl. 10 STM-03-US-001 cases) |
| `cd frontend; npx tsc --noEmit` | Node 22 | 0 | 0 errors |
| `node scripts/demo/run_story_demo.mjs STM-03-US-001` | Local UI:3000, GW:8080, enrollment:8095, headed Playwright | 0 | 11/11 PASS; `docs/demos/STM-03-US-001/demo.webm` + `DEMO.md` |

Working-tree state at demo: uncommitted enrollment/HMAC/UI/demo files on top of `9fb4727`.

### Evidence paths
- `docs/demos/STM-03-US-001/DEMO.md`
- `docs/demos/STM-03-US-001/demo.webm`
- `docs/demos/STM-03-US-001/screenshots/`

### Owner decision
**APPROVED STM-03-US-001 COMMIT** (2026-11-14). CSV status set to `completed`. Next proposed: `STM-03-US-002`.

---

## Entry 3 — STM-03-US-002 View enrolment details (2026-11-14)

**Task ID:** STM-03-US-002  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Base HEAD:** `1f41a7d` (US-001); story changes **uncommitted** pending approval.

### Scope completed
- Gap-closed student view-own enrolment details vs AC + required negatives (no invented DEPARTMENT_STAFF grants).
- `getEnrolment` / list enrich with term labels (`yearCode`, `termCode`, `termName`); audit `enrolment.viewed`.
- Student enrollment UI: client-side load (no SSR gateway identity spoof); details panel `data-testid` contracts.
- BFF continues to set `X-Student-Record-Id` from Gateway `/api/v1/students/me` (requires STM-01 on :8093).

### Database
- Dev ref `azulbaxszsqxymqmlwfe` only.
- Demo DB check: enrolment `a1000000-0000-0000-0000-000000000031` owned by student `81dd67d3-1d5f-4d8e-8061-a67648610002` (auth user `…f924` linked via person_id).

### Tests (§7A)

| Command | Environment | Exit | Result |
|---|---|---:|---|
| `mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest` | JDK 8 / Maven; offline unit | 0 | 39 run, 0 fail (7 `stm03Us002_*`) |
| `cd frontend; npx tsc --noEmit` | Node 22 | 0 | 0 errors |
| `node scripts/demo/run_story_demo.mjs STM-03-US-002` | UI:3000, GW:8080, student:8093, enrollment:8095, headed Playwright | 0 | 10/10 PASS; `docs/demos/STM-03-US-002/demo.webm` + `DEMO.md` |

Working-tree state at demo: uncommitted enrollment domain/controller/tests + student enrollment UI/demo on top of `1f41a7d`.

### Evidence paths
- `docs/demos/STM-03-US-002/DEMO.md`
- `docs/demos/STM-03-US-002/demo.webm`
- `docs/demos/STM-03-US-002/screenshots/`
- `docs/stories/STM-03-US-002.demo.json`

### Owner decision
**APPROVED STM-03-US-002 COMMIT** (2026-11-14). CSV status set to `completed`. Next proposed: `STM-03-US-003`.

---

## Entry 4 — STM-03-US-003 Validate enrolment eligibility (2026-11-14)

**Task ID:** STM-03-US-003  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Base HEAD:** `0365218` (US-002).

### Scope completed
- Registrar-only `evaluateEligibility` (window + active status); PENDING → ELIGIBLE when clean; no DEPARTMENT_STAFF invent; blocks student self-validate.
- College desk uses BFF `eligibility` action (not blind status mark); shows failures panel.
- Headed demo + live DB ELIGIBLE read-back.

### Tests (§7A)

| Command | Exit | Result |
|---|---:|---|
| `mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest` | 0 | 48 run, 0 fail (9 `stm03Us003_*`) |
| `cd frontend; npx tsc --noEmit` | 0 | 0 errors |
| `node scripts/demo/run_story_demo.mjs STM-03-US-003` | 0 | 14/14 PASS |

### Evidence paths
- `docs/demos/STM-03-US-003/`

### Owner decision
Owner directive **start / test / commit** (2026-11-14). CSV → `completed`. Next proposed: `STM-03-US-004`.

---

## Entry 5 — STM-03-US-004 Update enrolment status (2026-11-14)

**Task ID:** STM-03-US-004  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Base HEAD:** `1f6835d` (US-003); story changes **uncommitted** pending approval.

### Scope completed
- Registrar status updates (ELIGIBLE/SUSPENDED/REJECTED) with append-only history (`actorRole` in API history).
- College desk Suspend / Reject / Set eligible controls; BFF `status` action.
- Negatives: student self-update, faculty, department staff, cross-tenant, unauthenticated, invalid status.
- No invented DEPARTMENT_STAFF grants.

### Tests (§7A)

| Command | Exit | Result |
|---|---:|---|
| `mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest` | 0 | 56 run, 0 fail (8 `stm03Us004_*`) |
| `cd frontend; npx tsc --noEmit` | 0 | 0 errors |
| `node scripts/demo/run_story_demo.mjs STM-03-US-004` | 0 | 14/14 PASS |

### Evidence paths
- `docs/demos/STM-03-US-004/`

### Owner decision
**APPROVED STM-03-US-004 COMMIT** (2026-11-14). CSV status set to `completed`. Next: `STM-03-US-005`.

---

## Entry 6 — STM-03-US-005 View enrolment history (2026-11-14)

**Task ID:** STM-03-US-005  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Base HEAD:** `f6c1edb` (US-004); story changes **uncommitted** pending approval.

### Scope completed
- Student/registrar view of append-only enrolment history (status, action, actorRole, at).
- Student desk history panel with `data-testid` contracts; actorRole shown when present.
- Negatives: other student, faculty, cross-tenant, unauthenticated.

### Tests (§7A)

| Command | Exit | Result |
|---|---:|---|
| `mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest` | 0 | 63 run, 0 fail (7 `stm03Us005_*`) |
| `cd frontend; npx tsc --noEmit` | 0 | 0 errors |
| `node scripts/demo/run_story_demo.mjs STM-03-US-005` | 0 | 9/9 PASS |

### Evidence paths
- `docs/demos/STM-03-US-005/`

### Owner decision
**Approved STM-03-US-005** (2026-11-14). CSV → `completed`. Owner then directed clearing remaining STM-03 partials.

---

## Entry 7 — STM-03-US-006…019 batch close of remaining STM-03 partials (2026-11-14)

**Mode:** IMPLEMENT (owner: continue partially completed one-by-one until lane clear)  
**Base HEAD:** `f6c1edb`

### Scope completed
- Gap-closed / story-scoped unit + negative coverage for catalogue, sections, prerequisites, capacity, drop, allocate, waitlist join/promote/position, windows, terms, change history, in-service alerts, faculty roster.
- UI contracts: student catalogue/sections/waitlist/alerts testids; faculty roster desk testids.
- CSV `Implementation status` set to `completed` for `STM-03-US-005` … `STM-03-US-019`.
- Did **not** invent DEPARTMENT_STAFF grants.
- **STM-03-US-018** limited to in-service alerts (not external email/push).

### Tests (§7A)

| Command | Exit | Result |
|---|---:|---|
| `mvn test -pl services/student-services/enrollment-service -Dtest=EnrollmentDomainServiceTest` | 0 | **90 run, 0 fail** |
| `cd frontend; npx tsc --noEmit` | 0 | 0 errors |

Headed demos previously recorded for US-001…005; US-006…019 not re-demoed headed in this batch.

### CSV completed count (after this batch)

- **Overall completed:** **19**
- **STM-03 completed:** **19** (US-001…US-019)
- **Remaining partially completed (all services):** **52**
- **Yet to pickup:** **81**

---

## Entry 8 — STM-06-EN-002 Phase A (Storage abstraction + content validation) (2026-11-15)

**Task ID:** STM-06-EN-002 (PHASE A)  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Owner decisions recorded:** D8, D9, D11 (PROGRESS.md, 15-11-2026, Niranjan)

### Scope completed
- Document object-storage abstraction: `DocumentObjectStorage`, `DocumentStorageConfig`, `FakeDocumentObjectStorage`, `SupabaseStorageHttpClient` (JDK `HttpURLConnection` only; no new deps).
- Optional JSON upload field `contentBase64`; required when storage configured; metadata-only allowed for in-memory/test (unconfigured) profile.
- Validation: decoded size == `sizeBytes`; SHA-256 == `checksumSha256`; MIME allowlist + magic-byte signature check. Reused existing limits: **10 MB**; `application/pdf`, `image/jpeg`, `image/png`, `image/webp`.
- Download streams bytes on existing `GET /api/v1/documents/download` after signed-token check (`?mode=metadata` keeps JSON). No Supabase signed URLs to clients; bytes never in relational tables.
- Staging runbook prepared (not applied): `docs/runbooks/STM-06-EN-002_STAGING_STORAGE_SETUP.md`.
- Service README updated with config keys and contract notes.
- CSV: `STM-06-EN-002` and `STM-06-EN-003` → `partially completed` (only those two status cells; 220 rows / 23 columns verified).
- Queue: `NEXT_TASK.md` item **1b** frontend `contentBase64` wiring.
- Malware AV live scanner remains out of scope (open gap).

### Database
None (no DDL; no Staging Storage apply; no live Supabase Storage write).

### Tests (§7A)

| Command | Exit | Result |
|---|---:|---|
| `mvn test -pl services/student-services/document-service -Dtest=DocumentDomainServiceTest#stm06En002*` | 0 | EN-002 storage tests PASS (fake client; no live Supabase) |

### Evidence paths
- Runbook: `docs/runbooks/STM-06-EN-002_STAGING_STORAGE_SETUP.md`
- Service docs: `services/student-services/document-service/README.md`
- Decisions / status: `docs/project-control/PROGRESS.md` (D8/D9/D11)

### Deviations / STOP items
- Phase B (apply staging bucket + live write check) **not started** — awaits owner.
- Frontend does not yet send `contentBase64` (queue 1b); optional field does not break existing metadata-only proxies while storage remains unconfigured.
- Signature checks implemented with JDK magic-byte inspection — **no new library**.

### Owner decision
Awaiting `APPROVED STM-06-EN-002` / Phase B authorization. Local commits only; **no push**.

---

## Entry 9 — EN-002 regression fix + R31 Registrar Phase A (2026-11-15)

**Task ID:** EN-002 REGRESSION + R31 REGISTRAR (PHASE A, REVISED)  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`  
**Owner decisions recorded:** D10, D12a–D12e (PROGRESS.md, 15-11-2026, Niranjan)

### Part 1 — EN-002 regression
- Full `DocumentDomainServiceTest` on HEAD initially green; parent worktree `94368d0` US-007 test also PASS.
- Intermittent VALID/INVALID root cause: equal `verifiedAt` Instant ms in `findLatestVerification` preferred older REJECT.
- Fix: InMemory tie-break (last equal-or-after wins); Postgres `ORDER BY verified_at DESC, created_at DESC, id DESC`; live checklist assertion hardened for prior APPROVED rows.
- Full document-service module after fix: **102 run, 0 fail** (JDK 1.8.0_202).

### Part 2 — R31 Registrar Phase A
- `DelegationPort` + `FakeDelegationPort` + `FailClosedDelegationPort` + `LegacyRegistrarStandin` in document, enrollment, lifecycle, guardian, admissions.
- Actor `collegeId` from `X-College-Id`; Registrar = `R31`|`REGISTRAR` unless legacy flag ON.
- A/R fail-closed without delegation; campus required; document same-actor verify→approve without resubmit denied.
- College Admin catalogue/config retained where matrix grants; Registrar no longer configures document types (D12d).
- Runbook prepared (not applied): `docs/runbooks/R31_REGISTRAR_STAGING_SETUP.md`.
- Queue: NEXT_TASK items **1c** (frontend Registrar mapping) and **1d** (R31 Phase B).

### Tests (§7A) — full modules touched

| Module | Exit | Result |
|---|---:|---|
| document-service | 0 | 102 run, 0 fail |
| enrollment-service | 0 | 105 run, 0 fail |
| lifecycle-service | 0 | 48 run, 0 fail |
| guardian-service | 0 | 65 run, 0 fail |
| admissions-service | 0 | 58 run, 0 fail |

### Database
None (no IAM seed apply; no Supabase write).

### Deviations / STOP items
- Live IAM delegation adapter not built (D12a fail-closed port only) — Phase B design note required for cross-schema read.
- Frontend/BFF Registrar mapping out of scope (1c).
- Legacy flag expiry date still to be set by owner at first real college onboarding.

### Owner decision
Awaiting Phase B authorization (R31 IAM seed + optional live delegation adapter). Local commits only; **no push**.

---

## Entry 10 — IAM delegation design note + queue preflight STOP (2026-11-15)

**Task ID:** 0a/0b + RUN THE QUEUE (batch 4)  
**Mode:** IMPLEMENT (docs) / STOP (queue)

### Done
- Created `docs/design/IAM_DELEGATION_ADAPTER_DESIGN_NOTE.md` — adapter marked **BLOCKED** until owner approves Path B (cross-schema) or Path C (new IAM check contract) and mapping M1/M2/M3.
- Documented: existing IAM capabilities vs cross-schema vs new contract/infra; port interface; claims; caching/revocation; legacy flag **must not** bypass delegation/campus/SoD/matrix; SoD plan for five STM modules; prepare-only staging steps.
- NEXT_TASK: added **1e R31-SoD**; marked 1/1b/1c/1d/US-006 Phase-B-dependent items **BLOCKED**; pointed live adapter at the design note.

### Queue selection (not started — preflight STOP)
Would-be next unblocked same-service candidates after clear: **1e R31-SoD** (cross-module) then document-service stories **US-010** / **US-015** (US-006 remains BLOCKED pending pilot Registrar / owner waive).

### Preflight STOP
Unexpected dirty: `docs/runbooks/R31_REGISTRAR_STAGING_SETUP.md`, `docs/runbooks/STM-06-EN-002_STAGING_STORAGE_SETUP.md` (not in known-leftover list). No story implementation, no module suite re-run for queue work.

### Tests (§7A)
None for this entry (docs-only + STOP). Prior green full-suite evidence remains Entry 9.

### Owner decision needed
1. Keep / discard / authorize commit of the two dirty runbooks.  
2. Approve or CHANGES on `IAM_DELEGATION_ADAPTER_DESIGN_NOTE.md` (Path + mapping).  
3. Clear preflight → authorize queue resume (batch starts at **1e R31-SoD** or next unblocked).

---

## Entry 11 — Queue batch: R31-SoD + CROSS-EN-010 + US-010 + US-007 + US-015 (2026-11-15)

**Mode:** IMPLEMENT | **Branch:** `feature/java-backend-integration`

### Stories

| ID | Status | Commit | §7A |
|---|---|---|---|
| R31-SoD (1e) | completed / tested | `dfc6788` | admissions 59; enrollment 106; lifecycle 49; guardian 66 (at SoD commit; later suites grew) |
| STM-CROSS-EN-010 | partially completed | *(with docs commit)* | enrollment 108; lifecycle 51; guardian 75; document 115 — all exit 0 |
| STM-06-US-010 | completed | `0b1f73d` | document-service 107 → later 115 |
| STM-05-US-007 | completed | `4b2154b` | guardian-service 73 → later 75 |
| STM-06-US-015 | partially completed | *(with US-015 code commit)* | document-service 115 / 0 fail |

### Acceptance → code → test (summary)

| Clause | Code | Test |
|---|---|---|
| Same actor cannot verify+approve (enrollment eligibility→status) | `EnrollmentDomainService.updateStatus` | `stm03R31SoD_sameActorCannotVerifyEligibilityThenApproveOrReject` |
| Endorse≠decide (lifecycle) | `LifecycleDomainService.decideRequest` | `stm04R31SoD_sameActorCannotEndorseThenApprove` |
| Verify≠grant (guardian) | `GuardianDomainService.grantAccess` | `stm05R31SoD_sameActorCannotVerifyThenGrantAccess` |
| Review≠decide (admissions) | `AdmissionsPermissionEvaluator` + `ReviewAndDecisionService` | `stm02R31SoD_sameActorCannotReviewThenApprove` |
| Student re-verification request | `DocumentDomainService.requestReverification` | `stm06Us010_*` |
| Primary/secondary emergency contacts | `GuardianDomainService.designateEmergencyContactPriorities` | `stm05Us007_*` |
| In-app expiry notifications | `DocumentDomainService.listExpiringDocuments` / notifications | `stm06Us015_*` |
| CROSS tenant/role negatives (03..06) | named `stmCrossEn010_*` | coverage: `docs/reports/STM_CROSS_EN_010_STM03_06_COVERAGE.md` |

### Gaps
- CROSS: no unified multi-service e2e harness (BLOCKED pending owner).
- US-015: in-app notification list is process-local (not DB; DDL BLOCKED).
- Design note adapter still BLOCKED.

### Owner decisions still needed
- Approve IAM delegation design note (Path B/C + M1/M2/M3).
- Phase B applies (EN-002 storage, R31 IAM seed).
- Authorize CROSS e2e harness / US-015 notification DDL / CROSS-EN-014; push when ready.

---

## Entry 12 — Queue: STM-06-US-006 metadata correction (2026-11-15)

**Mode:** IMPLEMENT | **Branch:** `feature/java-backend-integration`  
**Commit:** `24473ff`

### Selection
- Preflight: known leftovers only.
- Baseline `document-service` suite: **115 run, 0 fail**, exit 0.
- CROSS-EN-010 remaining e2e → **BLOCKED** (new harness).
- US-015 DB persist → **BLOCKED** (no table / DDL).
- **STM-06-US-006** selected (document-service; D9 + R31 Phase A).

### Acceptance → code → test

| Clause | Code | Test |
|---|---|---|
| Registrar corrects metadata (title/classification/expires/type) | `DocumentDomainService.updateDocumentMetadata` | `stm06Us006_registrarUpdatesSubmittedDocumentMetadata` |
| Works on REJECTED | same | `stm06Us006_registrarUpdatesRejectedDocumentClassification` |
| Never on already-verified (APPROVED) | same → `DOCUMENT_ALREADY_VERIFIED` | `stm06Us006_deniesApprovedDocument` |
| Audited correction note | audit `document.metadata_corrected` | success tests |
| Student/faculty denied | campus/role gates | `stm06Us006_studentAndFacultyDenied` |
| Cross-tenant / wrong campus | same | `stm06Us006_crossTenantAndWrongCampusDenied` |
| Null actor / blank note / bad classification | same | `stm06Us006_nullActorBlankNoteInvalidClassification` |

### Tests (§7A)
`mvn test -pl services/student-services/document-service` → **exit 0**, **121 run, 0 fail** (JDK 1.8.0_202).

### Database
Uses existing `doc.documents` columns only (no DDL).

### Status
CSV `STM-06-US-006` → `completed`. Queue batch end: no further same-service unblocked items.

---

## Entry 13 — Queue run: empty batch + CSV sync (2026-11-15)

**Mode:** IMPLEMENT | **Branch:** `feature/java-backend-integration`

### Preflight
Known leftovers only (`classpath.txt`, `_csv_status_*.txt`, CSV scratch). No unexpected dirty files.

### Selection
No items eligible (all DONE, BLOCKED, DEFERRED, or partially completed with remaining work BLOCKED). No module suite re-run (nothing touched).

### CSV sync (coordinator only)
Updated `CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` Implementation status / Notes to match NEXT_TASK:
- completed: US-006, US-010, US-007 (hashes in notes)
- partially completed + remaining BLOCKED notes: EN-002, EN-003, US-015, CROSS-EN-010
- BLOCKED: CROSS-EN-014, STM-03-EN-007
- DEFERRED: CROSS-EN-007, US-017

### Owner decisions needed (unchanged)
Phase B applies; IAM design note Path B/C; CROSS e2e harness; US-015 notification DDL; schedule CROSS-EN-014.

---

## Entry 14 — IAM delegation adapter M1 + Path C investigation STOP (2026-11-15)

**Task ID:** IAM_DELEGATION_ADAPTER (D12a follow-on)  
**Mode:** IMPLEMENT (stopped at authorized infrastructure boundary)  
**Branch:** `feature/java-backend-integration`

### Owner approvals recorded
| Decision | Value |
|---|---|
| Mapping | **M1** (active REGISTRAR/R31 role delegation → STM `*.A` in-tenant) |
| Integration | **Path C** (central IAM contract; no STM cross-schema `iam.delegations` reads) |
| Cache | **None** |
| Revocation | Authoritative IAM check on every A/R |
| Failure | **Fail closed** |

### Investigation — what exists
- STM `DelegationPort` / `FakeDelegationPort` / `FailClosedDelegationPort` in all five STM services (document, enrollment, lifecycle, guardian, admissions); live constructors remain FailClosed.
- A/R gates, campus scope, SoD same-actor deny, legacy stand-in role-recognition-only already in Phase A.
- `institute-admin-service`: `DelegationRepository`, `PostgresDelegationRepository`, `InMemoryDelegationRepository` (CRUD against `iam.delegations`).
- API Gateway routes institute-admin admin paths; **no** delegation-check route.

### Investigation — what is missing (verified)
1. No institute-admin HTTP controller for delegations / active-check.
2. No domain service wiring repository → authorization decision for STM permission keys.
3. No gateway route for such a contract.
4. No STM `HttpIamDelegationPort` client (correctly absent — contract does not exist).

### Why existing infrastructure cannot finish Path C
STM must not JDBC-read `iam.*` (Path C / anti–Path B). The only IAM-side code is a **repository**, not a callable check API. Inventing a URL + response shape would violate “do not invent an endpoint.”

### Status
**partially completed** at architecture/docs layer; **live adapter BLOCKED** pending separate owner approval for **Path C Phase 2**:
- Internal active-delegation check API on existing `institute-admin-service` (M1 logic)
- Gateway registration
- Five STM HTTP clients; Fake/FailClosed preserved for tests
- No DDL, seed apply, new containers, or Path B

### Acceptance clause → code → test (current boundary)

| Clause | Code location | Test / evidence |
|---|---|---|
| M1 + Path C decisions recorded | `docs/design/IAM_DELEGATION_ADAPTER_DESIGN_NOTE.md` §0, §5, §14 | Design note review |
| Port contract present | `…/security/DelegationPort.java` (five services) | Existing Fake/FailClosed unit suites |
| Live deny without IAM API | `FailClosedDelegationPort` in production constructors | Always `false` → A/R denied |
| No Path B introduced | No STM JDBC to `iam.delegations` | Repo inspection |
| Campus / SoD / legacy flag limits | Phase A domain services | Existing R31 / SoD tests (prior commits) |
| Http live adapter + IAM check API | **Not built** (STOP §14) | N/A until Phase 2 approval |

### Tests (§7A)
No Java modules modified this entry — no suite re-run required. Prior Phase A suites remain the evidence for Fake/FailClosed behaviour.

### Database / infra
None touched. No DDL. No staging/production writes. No push.

### Docs / CSV
- Design note updated (approved decisions + §14 STOP).
- `PROGRESS.md`, `NEXT_TASK.md`, this entry.
- CSV `STM-CROSS-EN-010` Implementation Notes appended with IAM 2026-11-15 STOP note.

### Owner decision needed next
**Approve Path C Phase 2** (exact ask in design note §14.5): institute-admin internal active-check API + gateway route + STM `HttpIamDelegationPort` wiring under M1 / no cache / fail closed.

---

## Entry 15 — IAM Path C Phase 2 implementation (2026-11-15)

**Task ID:** IAM_DELEGATION_ADAPTER_PATH_C_PHASE2  
**Mode:** IMPLEMENT  
**Branch:** `feature/java-backend-integration`

### Scope completed
- Institute-admin M1 `DelegationAuthorizationService` + `GET /api/v1/admin/internal/delegations/active-check`.
- `RoleCodeLookup` (InMemory + Postgres `iam.roles`) — no STM cross-schema reads.
- Gateway explicit route `/api/v1/admin/internal/delegations`.
- `HttpIamDelegationPort` + `DelegationPortFactory` in admissions, enrollment, lifecycle, guardian, document.
- Applications wire factory; unset `CAMPX_IAM_DELEGATION_BASE_URL` → FailClosed (no silent in-memory auth).
- Fake/FailClosed preserved for unit tests. No cache. Fail closed on IAM errors.

### Acceptance → code → test

| Clause | Code | Test |
|---|---|---|
| Active valid R31 delegation | `DelegationAuthorizationService.checkActive` | `allowsActiveValidDelegation` / `httpAllowsActiveDelegation` |
| Missing delegation | same | `deniesMissingDelegation` / `httpDeniesMissingDelegation` |
| Expired / revoked | same | `deniesExpiredDelegation` / `deniesRevokedDelegation` |
| Wrong tenant | same | `deniesWrongTenant` |
| Unauthorized permission key | same | `deniesUnauthorizedPermissionKey` / HTTP |
| IAM unavailable | `HttpIamDelegationPort` | `failClosedWhenIamUnavailable` / `failClosedWhenHostUnreachable` |
| No cache | Http client | `noCacheEachCallHitsIam` |
| Caller≠subject | service | `deniesCallerSubjectMismatch` |
| Cross-service client | five `HttpIamDelegationPort` | per-service `HttpIamDelegationPortTest` |
| Gateway route | `GatewayConfig` | `DelegationGatewayRouteTest` |
| Campus / SoD / legacy | prior Phase A (unchanged) | existing R31/SoD suites |

### Tests (§7A)

| Module | Result |
|---|---|
| institute-admin-service (new tests) | DelegationAuthorizationServiceTest + DelegationActiveCheckHttpTest **PASS** |
| institute-admin-service (full) | **497 run, 0 fail, 11 errors, 31 skipped** — errors are pre-existing Postgres college/provisioning integration (`InstituteNotFound` / platform-admin privilege), unrelated to Path C |
| document-service | **exit 0** |
| enrollment-service | **exit 0** |
| lifecycle-service | **exit 0** |
| guardian-service | **exit 0** |
| admissions-service | **exit 0** |
| api-gateway `DelegationGatewayRouteTest` | **exit 0** |

### Database / infra
No DDL. No seed apply. No push. No new dependencies.

### Status
CSV `STM-CROSS-EN-010` notes updated — Path C Phase 2 **partially completed** (live A/R still needs R31 seed + env URL). Design note §14.6.
