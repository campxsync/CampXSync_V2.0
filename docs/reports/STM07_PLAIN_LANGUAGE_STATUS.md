# STM-07 plain-language status (VERIFY ONLY)

**Date:** 2026-11-16  
**Branch:** `feature/java-backend-integration`  
**Sources read:** `docs/prompts/NEXT_TASK.md`, backlog CSV, `docs/reports/COMPLETION_REPORT.md` (Entry 33–34), `docs/design/STM-08_*` / `STM-09_*`, commit `7ace09f`  
**Mode:** Report only. No status cells changed by this document.

Status values below map CSV/`NEXT_TASK.md` as: `completed` → **DONE**, `BLOCKED` → **BLOCKED**, `yet to pickup` / `partially completed` remainders → **OPEN**.

---

## 1. Every STM-07 item

| ID | Feature name (plain words) | What the user can do with it | Which role uses it | Status | DONE: commit + tests / BLOCKED or OPEN: blocker |
|---|---|---|---|---|---|
| STM-07-US-001 | Link student to an existing login | Staff can attach an eligible student record to an already-existing CampXSync login so the student can use the portal | Registrar or delegated college administrator (story text); IAM operator also in matrix; Student cannot self-link | **DONE** | Commit `7ace09f`. Tests: `stm07Us001_linkRequiresEligibilityAndPersistsStatus`, `stm07Us001_cannotLinkOtherUserWhenAlreadyLinked`, `stm07Us001_cannotLinkIamUserAlreadyUsedElsewhere` (plus earlier `testIamAccountLinkageAndIdempotentRelink`, `testStudentCannotLinkOwnIamAccountWithoutPrivilege`, `stm07En001_linkPersistsAndIsReadable` from EN-001 era) |
| STM-07-US-002 | Check if a student is ready for a login link | Staff can run an eligibility check before linking or requesting a new login, to stop duplicates and invalid links | Authorised IAM operator (story text); students denied | **DONE** | Commit `7ace09f`. Tests: `stm07Us002_eligibleActiveStudentPasses`, `stm07Us002_suspendedStudentNotEligible`, `stm07Us002_studentDeniedEligibilityCheck`, `stm07Us002_wrongTenantIsolation` |
| STM-07-US-003 | Staff view of link status | An IAM operator can see whether a student’s record is linked and how provisioning is progressing | Authorised IAM operator | **OPEN** | Not marked done in CSV/`NEXT_TASK.md`. API `getAccountLinkStatus` exists from `7ace09f` but US-003 itself remains open. UNKNOWN: whether a dedicated staff desk UI is required beyond the API — owner/product to confirm |
| STM-07-US-004 | Request a new student login | Registrar or college admin can start a provisioning request for an eligible student who does not yet have a linked login | Registrar or college administrator (scoped); students/guardians denied | **DONE** | Commit `7ace09f`. Tests: `stm07Us004_provisioningAllowedWhenEligible`, `stm07Us004_provisioningDeniedWhenAlreadyLinked`, `stm07Us004_studentCannotProvision`, `stm07Us004_guardianCannotProvision` |
| STM-07-US-005 | Retry a failed login setup | Staff can retry when a previous account-setup request failed | Registrar or delegated college administrator | **OPEN** | Not implemented in this batch. Depends on failure/retry workflow (related to EN-004). No dedicated retry API called out as done in repo docs |
| STM-07-US-006 | See past account-setup requests | Staff can read the history of provisioning requests for a student | Authorised IAM operator | **OPEN** | Provisioning rows exist (`stu.account_provisioning_requests` from EN-001) but a staff “history view” story is not marked done. UNKNOWN: whether count-only APIs are enough — needs a list/history endpoint/UI decision |
| STM-07-US-007 | Get told when an account is activated | Staff get a notification when student portal access becomes available | IAM operator or delegated college administrator | **OPEN** | Notification delivery not delivered in STM-07 batch. May depend on shared notification/Kafka (CROSS-EN-007 deferred). Owner must decide notification channel without inventing new bus |
| STM-07-US-008 | Give the student the approved portal role | An administrator assigns the approved student role so access matches college policy | Authorised administrator / IAM operator | **BLOCKED** | Needs an existing IAM role-assignment API; student-service must not invent a second role store (EN-002). **Who decides:** product owner. **Decision:** which existing IAM contract assigns student role R25 without cross-schema Path B writes? |
| STM-07-US-009 | Take away the student’s portal role | An IAM operator removes a student role when access should stop | Authorised IAM operator | **BLOCKED** | Same as US-008 — needs IAM role revoke API. **Who decides:** product owner. **Decision:** which existing IAM contract revokes R25? |
| STM-07-US-010 | Cut portal access when student status changes | When a student is suspended, detained, or withdrawn, their login link is revoked so access matches status | Authorised IAM operator / staff who change lifecycle status | **DONE** | Commit `7ace09f`. Test: `stm07Us010_suspendRevokesActiveLink` |
| STM-07-US-011 | Set which roles apply per college | An IAM operator configures college-specific role mappings | Authorised IAM operator | **OPEN** | Tied to FLAG-002 / EN-003 configure-role policy. Owner must assign configure (CFG) roles before implementation |
| STM-07-US-012 | Audit who changed accounts and roles | An IAM operator reviews history of account and role changes for investigations | Authorised IAM operator | **OPEN** | Account-link audits partially exist via privileged audit actions; role-assignment history depends on US-008/009 IAM. UNKNOWN: exact audit UI/API scope for “role assignment history” |
| STM-07-US-013 | See my own link status | A student can see whether their record is linked and whether portal access is active | Student (own record only) | **DONE** | Commit `7ace09f`. Tests: `stm07Us013_studentCanViewOwnLinkStatus`, `stm07Us013_studentCannotViewOtherLinkStatus`. Portal strip + BFF `/api/student/link-status` in same commit |
| STM-07-US-014 | Ask for portal access myself | Where the college allows it, a student can request portal access | Student (only if self-request enabled) | **OPEN** | Self-request flag/policy not decided in repo for STM-07. UNKNOWN: which setting turns self-request on — owner must define |
| STM-07-EN-001 | Store login-link and setup-request records in the database | System persists durable student↔login links and provisioning request rows with tenant security | Engineering (supports all STM-07 features) | **DONE** | Commit `1fd3ceb` (Entry 33). Tests noted there: `stm07En001_linkPersistsAndIsReadable`, `stm07En001_provisioningRequestPersisted`, plus existing IAM linkage tests |
| STM-07-EN-002 | Use only the existing login system | System integrates with existing IAM; does not build a second password or role system | Engineering | **OPEN** | Queued next in `NEXT_TASK.md`. Partial practice exists (no local credentials in student-service path) but story not marked completed. UNKNOWN: remaining checklist items beyond “no duplicate auth” |
| STM-07-EN-003 | College-scoped role rules and database security | Engineering for tenant role mapping, authorisation, and row-level security | Engineering | **BLOCKED** | Owner infrastructure/policy: FLAG-002 configure-role assignees pending. **Who decides:** product owner. **Decision:** who may configure role mappings for STM-07? |
| STM-07-EN-004 | Safe retries when login setup fails | Engineering for idempotent provisioning, retries, recovery, reconciliation | Engineering | **OPEN** | Not done in this batch. Blocks/pairs with US-005. No completed claim in CSV |
| STM-07-EN-005 | Connect account setup to student, admission, lifecycle, and portal | Engineering integration across STM-01, STM-02, STM-04, STM-10 | Engineering | **OPEN** | Cross-module integration not marked done. Parts of STM-01/lifecycle touch exist; full EN-005 DoD UNKNOWN |
| STM-07-EN-006 | Security tests, logs, docs, and go-live readiness for account setup | Engineering readiness pack for STM-07 | Engineering | **OPEN** | Not marked done. Ops pack for STM-07 explicitly deferred in CROSS-EN-014 notes |

---

## 2. Shorter table — STM-08, STM-09, STM-10

| Service | What it is for (plain words) | Why blocked or open |
|---|---|---|
| **STM-08** Academic history & transcripts | Holds official exam results, credits, progress, transcripts, awards, and revaluation outcomes for students | **BLOCKED** — no code under `services/` for this service; owner must approve creating a new microservice (`docs/design/STM-08_ACADEMIC_HISTORY_DESIGN_NOTE.md`). All STM-08 CSV rows marked BLOCKED |
| **STM-09** Student service requests | Catalogue of certificates/letters and other requests, with submit/verify/approve, SLAs, and tracking | **BLOCKED** — same new-service gate (`docs/design/STM-09_SERVICE_REQUESTS_DESIGN_NOTE.md`). Configure stories also wait on FLAG-002. All STM-09 CSV rows marked BLOCKED |
| **STM-10** Student portal | The student-facing dashboard and related desks (profile, documents, preferences, announcements, etc.) | **OPEN / mixed** — several portal stories already **completed** in CSV (e.g. US-001/002/003/005/008/011/012/017/019, EN-005/006/007). Remaining OPEN examples: timetable, exams, fees, service-request views, EN-001 preferences persistence, EN-002 aggregation, logout, guardian/faculty portal views. Not blocked by the STM-08/09 new-service gate; next pickup note points at EN-001 preferences when independent |

---

## 3. Acceptance → code → test for stories in commit `7ace09f`

Stories in that commit (per `NEXT_TASK.md` / Entry 34): **US-001, US-002, US-004, US-010, US-013**.

Generic CSV “Given/When/Then” clauses are broad. Rows below use the concrete acceptance points traced in Entry 34 plus story intent. Any clause with no code or no test is **UNCOVERED**.

### STM-07-US-001 — Link eligible student to existing login

| Acceptance clause | Code location | Test name |
|---|---|---|
| Staff can link an eligible active student to an IAM user id | `StudentDomainService.linkIamAccount` | `stm07Us001_linkRequiresEligibilityAndPersistsStatus` |
| Link blocked when student not eligible (e.g. not ACTIVE) | `evaluateProvisioningEligibility` used inside `linkIamAccount` | Covered indirectly via US-002 `stm07Us002_suspendedStudentNotEligible` + link path validation; dedicated “link after suspend” assertion = **UNCOVERED** as named US-001 test |
| Cannot replace an existing link with a different IAM user | eligibility `ALREADY_LINKED_OTHER_USER` | `stm07Us001_cannotLinkOtherUserWhenAlreadyLinked` |
| Cannot link an IAM user already linked to another student | eligibility `IAM_USER_LINKED_ELSEWHERE` | `stm07Us001_cannotLinkIamUserAlreadyUsedElsewhere` |
| Student cannot self-link | `linkIamAccount` student role check | `testStudentCannotLinkOwnIamAccountWithoutPrivilege` (pre-existing; still valid) |
| Persist/read link status for portal | `getAccountLinkStatus` + student dashboard strip + BFF `frontend/src/app/api/student/link-status/route.ts` | `stm07Us001_linkRequiresEligibilityAndPersistsStatus`; portal UI itself has **no automated browser test in 7ace09f** → browser DoD **UNCOVERED** |
| Registrar/College Admin “only when delegated” (matrix) via Path C live-check `STM-07.*.A` | **No** `DelegationPort` / Path C call in student-service | **UNCOVERED** |

### STM-07-US-002 — Validate eligibility

| Acceptance clause | Code location | Test name |
|---|---|---|
| Staff can evaluate eligibility and get pass/fail + failure codes | `StudentDomainService.evaluateProvisioningEligibility`; `GET .../provisioning-eligibility` | `stm07Us002_eligibleActiveStudentPasses`, `stm07Us002_suspendedStudentNotEligible` |
| Student cannot run eligibility check | same | `stm07Us002_studentDeniedEligibilityCheck` |
| Cross-tenant evaluation denied/not found | repository find under other tenant | `stm07Us002_wrongTenantIsolation` |
| “Validate student identity” as verificationStatus=VERIFIED gate | **Not implemented** as a failure code in 7ace09f | **UNCOVERED** (eligibility uses ACTIVE / soft-delete / duplicate-link rules only) |
| Path C Registrar delegation live-check required for this evaluation | **Not wired** | **UNCOVERED** / not depended on by current code |

### STM-07-US-004 — Initiate provisioning

| Acceptance clause | Code location | Test name |
|---|---|---|
| Staff can request provisioning when eligible and not already linked | `requestAccountProvisioning` | `stm07Us004_provisioningAllowedWhenEligible` |
| Denied when already linked | same | `stm07Us004_provisioningDeniedWhenAlreadyLinked` |
| Student cannot initiate | same | `stm07Us004_studentCannotProvision` |
| Guardian cannot initiate | `assertCanManageAccountProvisioning` | `stm07Us004_guardianCannotProvision` |
| Actual creation of Auth/IAM user credentials in Supabase Auth | **Not in student-service**; request row + outbox event only | **UNCOVERED** (credential mint out of scope / needs IAM operator path — UNKNOWN exact live adapter) |

### STM-07-US-010 — Suspend/deactivate access from lifecycle

| Acceptance clause | Code location | Test name |
|---|---|---|
| SUSPENDED revokes active IAM link | `revokeAccessForLifecycleEvent` from `transitionLifecycle` | `stm07Us010_suspendRevokesActiveLink` |
| DETAINED / WITHDRAWN also revoke | same method branches | **UNCOVERED** as separate named tests (code path present; only SUSPENDED tested) |
| GRADUATED/ALUMNI keep or revoke | code does not revoke for those statuses | Documented behaviour; explicit test **UNCOVERED** |
| IAM role revocation (catalogue role) on lifecycle | not implemented (US-009 blocked) | **UNCOVERED** |

### STM-07-US-013 — Student views own link status

| Acceptance clause | Code location | Test name |
|---|---|---|
| Student can read own link status | `getAccountLinkStatus` | `stm07Us013_studentCanViewOwnLinkStatus` |
| Student cannot read another student’s status | `enforceReadAccess` | `stm07Us013_studentCannotViewOtherLinkStatus` |
| Portal shows link status | `frontend/.../student/page.tsx` strip `data-testid="student-account-link-status"` | **UNCOVERED** by automated browser/demo test in 7ace09f |

---

## 4. Path C / Registrar delegation dependence (report only)

**Question:** Does any clause of US-001 or US-002 depend on the Registrar delegation live-check (Path C allowlist `STM-07.*.A`)?

**Answer from repo code/docs:**

- Path C allowlist in institute-admin (as documented in earlier completion entries) covers **STM-02..STM-06 `*.A` keys only**.
- Commit `7ace09f` student-service code has **no** `DelegationPort` / `HttpIamDelegationPort` usage.
- `assertCanManageAccountProvisioning` only rejects empty role, student, and guardian — it does **not** call the live Path C active-check.

Therefore: **No implemented US-001 or US-002 acceptance clause currently depends on Path C `STM-07.*.A`.**  
The RBAC matrix text still says College Admin / Registrar act “only when delegated”; that live Path C enforcement for STM-07 is **UNCOVERED** (gap reported, status cells not changed by this document).

---

## 5. Commit note for this file

Intended commit message: `STM-07 plain-language status` (this file only).
