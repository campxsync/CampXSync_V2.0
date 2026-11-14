# STM Status Audit — 2026-11-14 (REDO)

**Task ID:** STM_STATUS_AUDIT_2026-11-14_REDO
**Task mode:** IMPLEMENT (narrow: AUDIT ONLY)
**Workhorse:** Cursor
**Governing document:** `docs/AGREEMENT_V2.1.md` (V2.1)
**Working branch:** `feature/java-backend-integration`
**HEAD:** `6673d0d2aada7256dfb93728e5948db711f4e8f3`
**Deliverable:** `docs/audit/STM_STATUS_AUDIT_2026-11-14.md` (this file replaces the earlier draft that used the wrong CSV)

> **Source correction:** The first write of this audit used `CampXSync_STM-03_to_STM-10_Final_User_Stories.csv` (169 rows). Owner directed redo against **`CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv`** (220 rows). Issue IDs are **not** 1:1 with the Final User Stories file (titles remapped; enablers/RBAC flags added; no PILOT H-1..H-4 rows).
>
> Proposed statuses are **not** owner-verified (§7). Never treat this as "verified".

---

## Part A — Repository state

| Item | Value |
|---|---|
| Branch | `feature/java-backend-integration` |
| HEAD | `6673d0d2aada7256dfb93728e5948db711f4e8f3` |
| Remote tracking | up to date with `origin/feature/java-backend-integration` |

### Last 20 commits

```
6673d0d feat(hardening): complete pilot H-1..H-4 Postgres, auth, and onboard CLI
44e502e docs: correct Antigravity claims and add traceability verification
31f66c9 docs(reports): Update STM traceability matrix and PROGRESS ledger for STM-04-US-003
41f8bde docs(handover): Establish Cursor AI handover pack, AGENTS.md, and project rules
91dab15 chore(scripts): Add US-003 demo seed utility, demo step specification, and test evidence
be033af feat(stm-04): Implement STM-04-US-003 HOD Endorsement & Departmental Review of Student Leave Petitions
9df3e80 chore(gitignore): ignore env files, test-results, raw_video, node_modules, and editor state
f161cfe feat(stm): Complete STM-04 Student Lifecycle (US-007..014), STM-06 Stage Requirements, and resolve hydration & standing transitions
395276c STM-06-US-006: Document Expiry, Retention & Regulatory Audit Trail
ef6e7db Fix the student lifecycle build by sending the student role.
17f464a Record guardian evidence, access, and relationship history through the gateway.
5fa9e7c Add enrollment, lifecycle, and guardian services through the gateway.
44d7909 Evaluation for Cursor & Claude Code
e111f8a feat(tenancy): execute live provisioning and verification of institute APEX_SCI
803d2c1 docs(evidence): document Phase 3b and Phase 4 student dashboard verification
d758a1e feat(student): implement application documents UI and route proxy for Phase 3b
91cb726 feat(admissions): add scoped RLS and student 2 applicant seed for STM-01 Phase 3b
46cd60a feat(student): reinstate address linking in PostgresPersonRepository with live persistence tests
aefc1d6 docs(stm-01): student dashboard phase 0-3a evidence, field mapping, and designs
82ca9fa feat(stm-01): student self-service pages, route proxies, and navigation
```

### Uncommitted / working-tree files (left untouched)

| State | Path |
|---|---|
| deleted | `docs/CampXSync_Wave_Control_Revision/prompts/NEXT_ANTIGRAVITY_TASK.md` |
| deleted | `docs/prompts/NEXT_ANTIGRAVITY_TASK.md` |
| untracked | `classpath.txt` |
| untracked | `docs/AGREEMENT_V2.1.md` |
| untracked | `docs/CampXSync_Wave_Control_Revision/prompts/NEXT_TASK.md` |
| untracked | `docs/prompts/NEXT_TASK.md` |
| untracked | `docs/user-stories/CampXSync_STM-03_to_STM-10_Final_User_Stories.csv` (wrong source for this redo; still present) |
| untracked | `docs/audit/STM_STATUS_AUDIT_2026-11-14.md` (this deliverable) |

---

## Part B — Status-bearing document inventory

### Authoritative backlog for THIS redo

| Path | Tracked? | Size | SHA-256 |
|---|---|---|---|
| `D:\CampXSync\Documentation\StudentModule\UserStories\CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` | outside repo | 829915 | `3DD016233E1AFAF1F050B19F63DCAD48D1EEDE59D3E8F90C32526BDD11D6FD8D` |
| Repo copy | **MISSING** | — | — |

**Exact column headers (22):**

0. `Service ID`
1. `Service Name`
2. `Issue Type`
3. `Epic`
4. `Issue ID`
5. `Summary`
6. `Description`
7. `Priority`
8. `Acceptance Criteria (Draft Given/When/Then)`
9. `Dependencies`
10. `Implementation Notes`
11. `RBAC Matrix Role(s)`
12. `RBAC Permission`
13. `Authorization Scope / Constraints`
14. `RBAC Alignment Verdict`
15. `RBAC Change / Rationale`
16. `Canonical RBAC Role Code(s)`
17. `Canonical RBAC Source`
18. `Authorization Enforcement Layers`
19. `Required Negative Tests`
20. `Definition of Done (RBAC)`
21. `Cursor Execution Directive`

**Status-like columns:** There is **no** implementation/completion status column. Closest fields:
- `Priority` — distinct: `P0` | `P1` | `P2`
- `RBAC Alignment Verdict` — distinct: `MATRIX-DEFINED; ROLE-CATALOGUE GAP` | `MATRIX-ALIGNED; VERIFY SCOPE/WORKFLOW` | `MATRIX-ALIGNED CONTROL; ROLE-CATALOGUE GAPS PRESERVED` | `POLICY DECISION REQUIRED`
- `Cursor Execution Directive` — single shared directive (inspect repo first; delivery-first vertical slice; do not invent role grants / production writes)

**Row counts by Service ID:** STM-03=27, STM-04=25, STM-05=20, STM-06=24, STM-07=20, STM-08=25, STM-09=29, STM-10=30, STM-CROSS=14, All=3, multi-service FLAG rows=3. **Total 220. Unique Issue IDs 220.**

**Not in this CSV:** PILOT `H-1`..`H-4` (those appeared only in the Final User Stories CSV used earlier by mistake).

### Other status / next-task documents (unchanged inventory highlights)

| Path | Claims | Conflicts with |
|---|---|---|
| `docs/project-control/PROGRESS.md` | Many stories "closed"; H-1..H-4 APPROVED | This RBAC backlog; audit statuses below |
| `docs/handover/NEXT_TASKS_FOR_CURSOR.md` | No new user stories; pilot track | Untracked `NEXT_TASK.md` Wave 5; this CSV as product backlog |
| `docs/prompts/NEXT_TASK.md` (untracked) | Next = Wave 5 Timetable | `wave-5/COMPLETION_REPORT.md` already Complete; pilot handover |
| `prompts/NEXT_ANTIGRAVITY_TASK.md` | SUPERSEDED header + STM-04-US-017 body | NEXT_TASKS_FOR_CURSOR; Wave 5 NEXT_TASK |
| `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv` | PASS rows / legacy STM03 IDs | RBAC CSV remapped titles; TRACEABILITY_VERIFICATION |
| `docs/reports/TRACEABILITY_VERIFICATION.md` | 183 marked done; 14 with demos | PROGRESS/traceability PASS claims |
| `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md` | **MISSING** at canonical path | Cited by NEXT_TASK / AGENTS |
| Per-wave `COMPLETION_REPORT.md` | Wave N complete | No single COMPLETION_REPORT.md |
| Wrong CSV `...Final_User_Stories.csv` | 169 stories; different ID↔title map; includes H-1..H-4 | **Superseded for audit by RBAC-validated CSV** |

Demo packs under `docs/demos/*/DEMO.md`: H-1, H-2, H-3, H-4, STM-04-US-003, STM-04-US-007, STM-04-US-008, STM-04-US-009, STM-04-US-010, STM-04-US-011, STM-04-US-012, STM-04-US-013, STM-04-US-014, STM-06-US-002, STM-06-US-003, STM-06-US-004, STM-06-US-005, STM-06-US-006.

---

## Part C — Backlog conflicts (quotes only)

### C0. Wrong vs correct backlog file

- Earlier audit source: `CampXSync_STM-03_to_STM-10_Final_User_Stories.csv` (169 rows, includes PILOT).
- Correct source (owner): `CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` (220 rows, RBAC columns, no PILOT).
- Overlap of Issue ID strings: 158; Final-only examples: `STM-03-US-020`..`025`, `H-1`..`H-4`; RBAC-only examples: extra EN rows, `STM-04-US-017`..`019`, `STM-RBAC-FLAG-*`.
- **Same Issue ID can mean a different Summary** (e.g. Final `STM-03-US-002` = validate eligibility; RBAC `STM-03-US-002` = view enrolment details).

### C1. Next task

> `docs/handover/NEXT_TASKS_FOR_CURSOR.md` L6: Do **NOT** pick up new user stories or add speculative features.
>
> `docs/prompts/NEXT_TASK.md` L20–22: ## 2. Next Task: Wave 5 — Timetable… proceed to Wave 5
>
> `docs/reports/wave-5/COMPLETION_REPORT.md` L5: **Status:** Complete (Verified Live…)
>
> `prompts/NEXT_ANTIGRAVITY_TASK.md` L32: ## 2. Authorized Next Story for Tomorrow: `STM-04-US-017`
>
> RBAC CSV `Cursor Execution Directive`: Inspect repository and current Supabase schema first; implement delivery-first vertical slice…

### C2. Approved / done claims

> `docs/handover/CURSOR_START_HERE.md` L12: **H-1, H-2, H-3, H-4 APPROVED** (sequence complete).
>
> `docs/project-control/PROGRESS.md`: repeated "is closed on the live path" for STM-04/05/06 stories.
>
> `docs/reports/TRACEABILITY_VERIFICATION.md` L12–15: Done rows with demo pack **14**; without demo pack (**NOT VERIFIED**) **169**.

---

## Part D — Per-story audit (all 220)

### Method

1. Story list + AC from **RBAC-validated CSV only**.
2. Evidence: code, migrations, automated tests, demo pack paths. Not PROGRESS/COMPLETION claims.
3. Because Final CSV remapped titles, probes use **this CSV’s Summary/AC**, not prior ID assumptions.
4. `completed` = all audit probes for that story traced in code **and** automated test evidence; used sparingly. STM-03/04/05 never marked completed in this redo even when probes match (AC gaps / missing demos).
5. `RBAC Alignment Verdict` recorded in Notes; `POLICY DECISION REQUIRED` flags → yet to pickup.
6. Acceptance column is draft GWT; `n of m` = probes traced of probes defined (or GWT marker count when no probes).

| Issue ID | Proposed status | Acceptance clauses traced (n of m) | Implementation evidence | Test evidence | Tests executed in this audit | Notes |
|---|---|---|---|---|---|---|
| STM-03-US-001 | partially completed | 2 of 2 | enrollment-service openFromHandover; POST /api/v1/enrollment/handovers | EnrollmentDomainServiceTest (indirect) | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: Convert approved admission into enrolment. Traced: handover/create; persist enrolment. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-002 | partially completed | 2 of 2 | GET /api/v1/enrollment/enrolments/{id} | EnrollmentDomainServiceTest | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: View enrolment details. Traced: get enrolment; 404/403 isolation. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-003 | partially completed | 1 of 1 | POST .../eligibility | window/eligibility related unit coverage partial | not run | Summary: Validate enrolment eligibility. Traced: eligibility. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-004 | partially completed | 1 of 1 | POST .../status | registrarCanApproveStatus | not run | Summary: Update enrolment status. Traced: update status. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-005 | partially completed | 1 of 1 | GET .../history | studentCanListCoursesAndHistory | not run | Summary: View enrolment history. Traced: history. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-006 | partially completed | 1 of 1 | GET catalogue/courses | studentCanListOwnEnrolmentsAndRegisterViaCatalogue | not run | Summary: Select programme and term courses. Traced: catalogue/courses. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-007 | partially completed | 1 of 1 | GET .../sections | catalogue/register tests | not run | Summary: Select available course sections. Traced: sections. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-008 | partially completed | 1 of 1 | assertPrerequisites in register | prerequisiteBlocksRegistration | not run | Summary: Enforce course prerequisites. Traced: prerequisites. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-009 | partially completed | 1 of 1 | tryReserveSeat | capacityBlocksSecondStudent | not run | Summary: Enforce section capacity. Traced: capacity. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-010 | partially completed | 1 of 1 | POST .../drop | (no dedicated drop test) | not run | Summary: Remove course during permitted period. Traced: drop. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-011 | partially completed | 1 of 1 | POST /allocations | hodCanMoveSectionWhenCapacityAllows | not run | Summary: Allocate students to sections. Traced: allocate. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-012 | partially completed | 1 of 1 | POST waitlist/join | fullSectionWaitlistRecordsStudentPosition | not run | Summary: Join full-section waitlist. Traced: join waitlist. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-013 | partially completed | 1 of 1 | POST waitlist/promote | registrarCanPromoteWaitlist | not run | Summary: Promote eligible waitlisted students. Traced: promote. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-014 | partially completed | 1 of 1 | GET waitlist/position | studentCanViewWaitlistPosition | not run | Summary: View waitlist position and status. Traced: waitlist position. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-015 | partially completed | 1 of 1 | POST /windows | registrationOutsideWindowIsRejected | not run | Summary: Configure registration windows. Traced: windows. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-016 | partially completed | 1 of 1 | POST/GET /terms | term creation paths in domain tests | not run | Summary: Configure academic years and terms. Traced: terms. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-017 | partially completed | 1 of 1 | GET history | studentCanListCoursesAndHistory | not run | Summary: View enrolment change history. Traced: change history. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-US-018 | partially completed | 1 of 1 | GET /alerts | intelligence event test (not delivery) | not run | Summary: Receive enrolment confirmations and alerts. Traced: alerts. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-EN-001 | partially completed | 2 of 2 | PostgresEnrollmentPersistence; migrations 20261107000100* | LivePostgresEnrollmentPersistenceTest (DB not run) | not run | Summary: Supabase persistence, CRUD, schema ownership audit and database integration. Traced: postgres persist; schema migration. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-EN-002 | partially completed | 1 of 1 | security context + fail-closed reads | crossTenantReadFailsClosed | not run | Summary: Tenant isolation, RLS policies, grants and authorization regression tests. Traced: RLS/tenant. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-EN-003 | partially completed | 1 of 1 | tryReserveSeat + register idempotency | capacityBlocksSecondStudent; idempotent register | not run | Summary: Concurrent seat allocation, transaction integrity and idempotent registration. Traced: seat/idempotency. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-EN-004 | partially completed | 1 of 1 | GatewayConfig → :8095; EnrollmentGatewayRouteTest | EnrollmentGatewayRouteTest | not run | Summary: API Gateway integration, OpenAPI documentation and service contracts. Traced: gateway enrollment route. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-EN-005 | partially completed | 1 of 1 | frontend student/college/faculty enrollment pages+BFF | no STM-03 Playwright demo | not run | Summary: Frontend integration, enrolment UI and browser E2E testing. Traced: enrollment UI/BFF. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-EN-006 | partially completed | 1 of 1 | EnrollmentIntelligenceService; logger | successfulRegistrationEmitsOneIntelligenceEvent | not run | Summary: Kafka events, notifications, centralized logging and observability. Traced: intelligence/events. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-03-EN-007 | yet to pickup | 0 of 3 | (none) | (none) | not run | operational readiness/docs enabler — no dedicated DoD artifact tied to ID \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-04-US-001 | partially completed | 1 of 1 | GET .../students/{id}/status | studentCanViewOwnStatus | not run | Summary: View current lifecycle status. Traced: get status. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-002 | partially completed | 1 of 1 | POST requests / transition | registrarApprovesLeave | not run | Summary: Update lifecycle status through authorized workflow. Traced: transition/request. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-003 | partially completed | 1 of 1 | endorse + advisor requests; demo STM-04-US-003 | advisorCanListAdviseeRequestsAndEndorsePendingPetition | not run | Summary: Review lifecycle status changes. Traced: review/endorse. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-003 exists=true |
| STM-04-US-004 | partially completed | 1 of 1 | GET .../history | history via transition tests | not run | Summary: View complete lifecycle history. Traced: history. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-005 | partially completed | 1 of 1 | POST requests LEAVE | registrarApprovesLeave | not run | Summary: Submit leave-of-absence request. Traced: leave submit. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-008 exists=true |
| STM-04-US-006 | partially completed | 1 of 1 | POST .../decide | registrarApprovesLeave | not run | Summary: Approve or reject leave requests. Traced: decide leave. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-009 exists=true |
| STM-04-US-007 | partially completed | 1 of 1 | REINSTATE request type | transferWithdrawalAndReinstatementFollowApproval | not run | Summary: Request reinstatement after leave. Traced: reinstatement request. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. \| demo STM-04-US-007 historically TRANSFER — title drift |
| STM-04-US-008 | partially completed | 1 of 1 | approve REINSTATE → ACTIVE | composite reinstatement test | not run | Summary: Reinstate eligible students. Traced: reinstate. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-009 | partially completed | 1 of 1 | POST TRANSFER | transfer branch | not run | Summary: Request internal or external transfer. Traced: transfer request. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-007 exists=true |
| STM-04-US-010 | partially completed | 1 of 1 | decide on TRANSFER | transfer branch | not run | Summary: Process transfer requests. Traced: process transfer. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-011 | partially completed | 1 of 1 | POST WITHDRAWAL | withdrawalRequiresEffectiveDate | not run | Summary: Request withdrawal. Traced: withdrawal request. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-010 exists=true |
| STM-04-US-012 | partially completed | 1 of 1 | POST graduation/validate | registrarRecordsGraduationOnlyWhenTheStudentIsActive | not run | Summary: Validate graduation eligibility. Traced: grad validate. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-012 exists=true |
| STM-04-US-013 | partially completed | 1 of 1 | POST graduation/record | graduation test | not run | Summary: Record graduation details. Traced: record graduation. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-013 exists=true |
| STM-04-US-014 | partially completed | 1 of 1 | POST /rules | (thin/no dedicated configureRule test) | not run | Summary: Configure lifecycle transition rules. Traced: configure rules. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-014 exists=true |
| STM-04-US-015 | partially completed | 1 of 1 | LifecycleIntelligenceService | events asserted in approve tests | not run | Summary: Trigger downstream events for lifecycle changes. Traced: intelligence event. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-016 | partially completed | 1 of 1 | GET reports/summary | registrarReportCountsStatusesAndAdvisorSeesOnlyAdvisees | not run | Summary: View lifecycle reports. Traced: reports. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-04-US-014 exists=true |
| STM-04-EN-001 | partially completed | 1 of 1 | PostgresLifecyclePersistence | unit + demos use live path | not run | Summary: Supabase lifecycle persistence and CRUD integration. Traced: postgres lifecycle. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-EN-002 | partially completed | 1 of 1 | date/409 guards | leaveEffectiveDateCannotBeInPast | not run | Summary: Lifecycle transition validation, transactional integrity and RLS. Traced: transition validation. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-EN-003 | partially completed | 1 of 1 | approve/endorse + intelligence | endorse/approve tests | not run | Summary: Approval workflow, notifications and Kafka integration. Traced: workflow/events. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-EN-004 | yet to pickup | 0 of 3 | (none) | (none) | not run | cross STM-03/07/08/10 contracts not found in lifecycle-service \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-04-EN-005 | partially completed | 1 of 1 | student/college lifecycle + faculty advisees | Playwright demos STM-04-* | not run | Summary: Frontend lifecycle management and student self-service. Traced: lifecycle UI. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-EN-006 | partially completed | 1 of 1 | history + logger + unit tests | LifecycleDomainServiceTest | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: Audit history, automated tests, centralized logging, documentation and operational readiness. Traced: audit/tests/docs. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-001 | partially completed | 1 of 1 | POST /api/v1/guardians | GuardianDomainServiceTest | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: Add parent or guardian. Traced: add guardian. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-002 | partially completed | 1 of 1 | POST/PATCH /guardians/{id} | registrarUpdatesRelationshipAndPrimaryFlag | not run | Summary: Update guardian information. Traced: update guardian. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-003 | partially completed | 1 of 1 | evidence submit/review | registrarVerifiesSubmittedEvidence | not run | Summary: Verify guardian relationship. Traced: verify relationship. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-004 | partially completed | 1 of 1 | POST .../review | reviewerCannotDecideTheirOwnRelationship | not run | Summary: Review guardian verification requests. Traced: review verification. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-005 | partially completed | 1 of 1 | POST .../deactivate | studentEndsOwnRelationshipWithoutDeletingIt | not run | Summary: Deactivate guardian relationship. Traced: deactivate. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-006 | partially completed | 1 of 1 | emergency-contacts routes | studentMaintainsAnEmergencyContactWithoutGrantingAccess | not run | Summary: Maintain emergency contacts. Traced: emergency contacts. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-007 | yet to pickup | 0 of 3 | (none) | (none) | not run | primary/secondary emergency ordering not found \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-05-US-008 | partially completed | 1 of 1 | GET emergency-contacts | indirect | not run | Summary: Retrieve emergency contact information. Traced: retrieve emergency. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-009 | partially completed | 1 of 1 | POST .../access | registrarGrantsSelectedAccessAfterVerification | not run | Summary: Grant guardians access to selected information. Traced: grant access. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-010 | partially completed | 1 of 1 | consent-related domain if present | may be thin | not run | Summary: Give consent for guardian access to selected information. Traced: consent. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-011 | partially completed | 1 of 1 | POST .../revoke | studentRevokesOwnGuardianAccessAndASecondRevokeIsRejected | not run | Summary: Revoke guardian permissions. Traced: revoke. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-012 | partially completed | 1 of 1 | GET guardians/{id}/students | authorizedViewReturnsOnlyGrantedCategories | not run | Summary: View only authorized student information. Traced: authorized view. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-013 | partially completed | 1 of 1 | GET relationship-history | auditorReviewsClaimVerificationGrantAndRevocation | not run | Summary: Review relationship and consent history. Traced: history. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-EN-001 | partially completed | 1 of 1 | PostgresGuardianPersistence | GuardianDomainServiceTest | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: Supabase relationship, contact, consent and permission persistence. Traced: postgres guardian. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-EN-002 | yet to pickup | 0 of 3 | (none) | (none) | not run | no dedicated RLS/cross-tenant grant suite in guardian-service \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-05-EN-003 | partially completed | 1 of 1 | grant/period/revoke | elapsedAccessPeriodExpiresOnReadAndStaysDenied | not run | Summary: Consent lifecycle, verification and revocation enforcement. Traced: consent/revoke. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-EN-004 | yet to pickup | 0 of 3 | (none) | (none) | not run | STM-01/06/07/IAM integration contracts not evidenced as complete \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-05-EN-005 | partially completed | 1 of 1 | college/guardians page+BFF | no STM-05 demos | not run | Summary: Student/guardian frontend and accessible consent management. Traced: guardians UI. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-EN-006 | partially completed | 1 of 1 | 42 unit tests + audit logs | GuardianDomainServiceTest | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: Security tests, audit logging, documentation and operational readiness. Traced: tests/logging. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-06-US-001 | partially completed | 1 of 1 | POST/GET/PUT /documents/types; college documents UI | DocumentControllerTest / DomainServiceTest | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: Configure document types. Traced: document types. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-002 | completed | 1 of 1 | requirements API; demo STM-06-US-002 | LivePostgresDocumentStageRequirementTest (DB); unit tests | not run | Summary: Define mandatory documents for programmes and stages. Traced: stage requirements. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-002 exists=true |
| STM-06-US-003 | partially completed | 1 of 1 | GET submissions/checklist | DocumentDomainServiceTest | not run | Summary: View required documents. Traced: checklist. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-004 | partially completed | 1 of 1 | POST submissions | upload tests; demo labeled US-003 historically | not run | Summary: Upload required documents. Traced: upload. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-003 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-005 | partially completed | 1 of 1 | GET submissions + verification | verification status tests | not run | Summary: View document upload status. Traced: upload status. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-005 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-006 | yet to pickup | 0 of 3 | (none) | (none) | not run | PATCH metadata not found in DocumentController \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-US-007 | partially completed | 1 of 1 | POST .../resubmit | resubmit suite | not run | Summary: Upload replacement document. Traced: resubmit. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-005 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-008 | partially completed | 1 of 1 | GET /verifications | DocumentControllerTest | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: Review uploaded documents. Traced: verification queue. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-004 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-009 | partially completed | 1 of 1 | POST .../decision | LivePostgresDocumentVerificationTest (DB); unit | not run | Summary: Approve or reject documents with remarks. Traced: approve/reject. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-004 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-010 | yet to pickup | 0 of 3 | (none) | (none) | not run | no reverify endpoint \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-US-011 | partially completed | 1 of 1 | GET audit-trail | getDocumentAuditTrail tests | not run | Summary: Track verification history. Traced: audit trail. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-006 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-012 | yet to pickup | 0 of 3 | (none) | (none) | not run | no per-document access grants \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-US-013 | yet to pickup | 0 of 3 | (none) | (none) | not run | no document-sharing consent endpoints \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-US-014 | partially completed | 1 of 1 | retention/expiry read-side | retention tests | not run | Summary: Configure document expiry rules. Traced: expiry rules. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-006 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-US-015 | yet to pickup | 0 of 3 | (none) | (none) | not run | expiry notifications not found \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-US-016 | partially completed | 1 of 1 | retentionPolicyCode read-side | RET_* tests | not run | Summary: Enforce document retention policies. Traced: retention policy. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. Demo:STM-06-US-006 exists=true \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-EN-001 | partially completed | 1 of 1 | PostgresDocumentPersistence | Document*Test | not run | Summary: Supabase document metadata persistence and CRUD integration. Traced: postgres docs. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-EN-002 | yet to pickup | 0 of 3 | (none) | (none) | not run | storage bytes/signed URLs not implemented in document-service \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-EN-003 | yet to pickup | 0 of 3 | (none) | (none) | not run | malware/quarantine pipeline not found \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-EN-004 | partially completed | 1 of 1 | service authz checks | DocumentControllerTest RBAC cases | yes if suite listed (see §7A); story-specific methods not re-isolated | Summary: RLS, tenant isolation and document-level authorization. Traced: tenant/authz. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-06-EN-005 | partially completed | 0 of 3 | (none) | (none) | not run | partial desk integrations exist; full STM-02/03/05/09/10 contracts NOT VERIFIED \| RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-06-EN-006 | partially completed | 1 of 1 | DocumentIntelligenceService | controller tests emit events | not run | Summary: Automated document workflow tests, frontend, observability, documentation and operational readiness. Traced: intelligence/logging. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. \| Conservative: completed reserved for STM-06-US-002 only in this redo. |
| STM-07-US-001 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-002 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-003 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-004 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-005 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-006 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-007 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-008 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-009 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-010 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-011 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-012 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-EN-001 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-EN-002 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-EN-003 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-EN-004 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-EN-005 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-EN-006 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-08-US-001 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Integrate approved EXM-01 results into academic history |
| STM-08-US-002 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Validate incoming results against student/programme/term |
| STM-08-US-003 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View verified academic records |
| STM-08-US-004 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Preserve source result references |
| STM-08-US-005 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View earned academic credits |
| STM-08-US-006 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View academic progress by programme and term |
| STM-08-US-007 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Evaluate progress against approved programme requirements |
| STM-08-US-008 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View academic performance reports |
| STM-08-US-009 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Request academic transcript |
| STM-08-US-010 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Generate transcript from approved records |
| STM-08-US-011 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View transcript generation and verification status |
| STM-08-US-012 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Record academic awards |
| STM-08-US-013 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View result revaluation status |
| STM-08-US-014 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Consume approved revaluation outcomes |
| STM-08-US-015 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View previous and revised academic record values |
| STM-08-US-016 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Verify academic records |
| STM-08-EN-001 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Supabase academic-history persistence and CRUD integration |
| STM-08-EN-002 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | EXM-01 official result API/event integration and source validation |
| STM-08-EN-003 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Idempotent result ingestion, correction history and transactional integrity |
| STM-08-EN-004 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | RLS, tenant isolation and student-record authorization |
| STM-08-EN-005 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | STM-03, STM-04, ACD, EXM-01, STM-06 and STM-09 integration |
| STM-08-EN-006 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Transcript generation, shared storage integration and automated tests |
| STM-08-EN-007 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Frontend, audit, centralized logging, documentation and operational readiness |
| STM-09-US-001 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Configure student service request types |
| STM-09-US-002 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Browse available service requests |
| STM-09-US-003 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Create service request |
| STM-09-US-004 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Save request as draft |
| STM-09-US-005 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Attach supporting documents |
| STM-09-US-006 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Submit request for verification |
| STM-09-US-007 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Verify submitted requests |
| STM-09-US-008 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Approve or reject requests |
| STM-09-US-009 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Configure approval workflows |
| STM-09-US-010 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Delegate approval responsibilities |
| STM-09-US-011 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | View request status and history |
| STM-09-US-012 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Cancel eligible request |
| STM-09-US-013 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Configure service-level agreements |
| STM-09-US-014 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Escalate overdue requests |
| STM-09-US-015 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | View SLA performance reports |
| STM-09-US-016 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Receive approved certificates and letters |
| STM-09-US-017 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Route profile corrections to STM-01 |
| STM-09-US-018 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Route academic-history requests to STM-08 |
| STM-09-US-019 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Receive request outcome notifications |
| STM-09-US-020 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Review request actions and approval history |
| STM-09-EN-001 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Supabase request catalog, workflow and SLA persistence |
| STM-09-EN-002 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Tenant isolation, RLS and role/resource-scoped authorization |
| STM-09-EN-003 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Configurable approval engine, idempotency and retry handling |
| STM-09-EN-004 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | STM-01, STM-03, STM-04, STM-06, STM-08 and STM-10 integration |
| STM-09-EN-005 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Shared notification, document generation, storage and audit integration |
| STM-09-EN-006 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Frontend request catalog, tracking, approval UI and automated E2E testing |
| STM-09-EN-007 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Intelligence Services integration for approved classification/workload analytics; AI cannot bypass authorization or approval |
| STM-09-EN-008 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Documentation, centralized logging, observability and operational readiness |
| STM-10-US-001 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-002 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-003 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-004 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-005 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-006 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-007 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-008 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-009 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-010 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-011 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-012 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-013 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-014 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-015 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-016 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-017 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-018 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-019 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-US-020 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-EN-001 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-EN-002 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-EN-003 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-EN-004 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-EN-005 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-EN-006 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-10-EN-007 | partially completed | 1 of 2 | frontend auth/login (not STM-10 portal BFF) | (none STM-10-specific) | not run | No GET /portal/* service |
| STM-CROSS-EN-001 | partially completed | 1 of 2 | schema/migrations audit docs (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-002 | partially completed | 1 of 2 | service ownership boundaries (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-003 | partially completed | 1 of 2 | postgres CRUD in STM services (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-004 | partially completed | 1 of 2 | RLS/tenant context (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-005 | partially completed | 1 of 2 | exception/error handling (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-006 | partially completed | 1 of 2 | API Gateway (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-007 | partially completed | 1 of 2 | events/notifications (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-008 | partially completed | 1 of 2 | Intelligence Services (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-009 | partially completed | 1 of 2 | frontend+demos (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-010 | yet to pickup | 0 of 2 | (none) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-011 | partially completed | 1 of 2 | technical docs (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-012 | partially completed | 1 of 2 | logging/metrics (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-013 | partially completed | 1 of 2 | idempotency/concurrency (capability; Issue ID not in source) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-CROSS-EN-014 | yet to pickup | 0 of 2 | (none) | module tests / demos as applicable | not run | CROSS enabler scored by platform capability; STM-07–10 scope incomplete |
| STM-03-US-019 | partially completed | 1 of 1 | roster routes in EnrollmentController | roster forbid tests if present | not run | Summary: View my assigned course roster. Traced: roster. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-017 | partially completed | 1 of 1 | GET .../linked/{id}/status | parentLinkedViewReturnsLimitedStatus | not run | Summary: View limited status of my linked student. Traced: parent linked status. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. \| no parent UI/BFF found previously |
| STM-04-US-018 | partially completed | 1 of 1 | GET /advisees | advisor advisee tests | not run | Summary: View limited status of my advisees. Traced: advisee status. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-04-US-019 | partially completed | 1 of 1 | decide WITHDRAWAL | withdrawal approval path | not run | Summary: Approve and process a withdrawal. Traced: process withdrawal. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-05-US-014 | partially completed | 1 of 1 | grant/period/revoke paths | grant/period tests | not run | Summary: Activate or refuse guardian access grants. Traced: activate/refuse grant. Missing: —. RBAC: MATRIX-DEFINED; ROLE-CATALOGUE GAP. |
| STM-06-US-017 | yet to pickup | 0 of 3 | (none found for new US-017 in prior audit) | (none) | not run | Verify only the documents assigned to my agency \| MATRIX-DEFINED; ROLE-CATALOGUE GAP |
| STM-07-US-013 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-07-US-014 | yet to pickup | 0 of 3 | (none — no STM-07 service) | (none) | not run | Issue ID only in backlog CSV |
| STM-08-US-017 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | Request correction of my academic record |
| STM-08-US-018 | yet to pickup | 0 of 3 | (none — no academic-history service) | (none) | not run | View limited academic history of my linked student |
| STM-09-US-021 | yet to pickup | 0 of 3 | (none — no service-requests microservice) | (none) | not run | Raise and track requests for my linked student |
| STM-10-US-021 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-022 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-10-US-023 | yet to pickup | 0 of 2 | (no STM-10 portal BFF capability found for this story) | (none STM-10-specific) | not run | No GET /portal/* service \| Downgraded: no evidence for timetable/fees/exam/transcript/service-request portal widgets. |
| STM-RBAC-EN-001 | partially completed | 1 of 2 | Gateway/HMAC + per-service security contexts (not full central ABAC engine) | GatewaySecurityTest / service tests | not run | MATRIX-ALIGNED CONTROL; ROLE-CATALOGUE GAPS PRESERVED |
| STM-RBAC-FLAG-001 | yet to pickup | 0 of 1 | (governance / policy flag — not an implementation story until decided) | (none) | not run | RBAC verdict: POLICY DECISION REQUIRED. Roles used by the STM matrix are not in R01 to R30 |
| STM-RBAC-FLAG-002 | yet to pickup | 0 of 1 | (governance / policy flag — not an implementation story until decided) | (none) | not run | RBAC verdict: POLICY DECISION REQUIRED. No configure permission in the matrix |
| STM-RBAC-FLAG-003 | yet to pickup | 0 of 1 | (governance / policy flag — not an implementation story until decided) | (none) | not run | RBAC verdict: POLICY DECISION REQUIRED. Student consent and self-granting |
| STM-RBAC-FLAG-004 | yet to pickup | 0 of 3 | (none) | (none) | not run | POLICY DECISION REQUIRED — governance flag, not implementable until owner decision \| RBAC: POLICY DECISION REQUIRED |
| STM-RBAC-FLAG-005 | yet to pickup | 0 of 1 | (governance / policy flag — not an implementation story until decided) | (none) | not run | RBAC verdict: POLICY DECISION REQUIRED. Update, delete and retention rights |
| STM-RBAC-FLAG-006 | yet to pickup | 0 of 1 | (governance / policy flag — not an implementation story until decided) | (none) | not run | RBAC verdict: POLICY DECISION REQUIRED. Auditor beyond STM-06 |
| STM-RBAC-FLAG-007 | yet to pickup | 0 of 1 | (governance / policy flag — not an implementation story until decided) | (none) | not run | RBAC verdict: POLICY DECISION REQUIRED. Open policy items from the matrix |

### §7A — Tests executed (offline; same HEAD)

| Exact command | Commit / WT | Environment | Exit | Results | Skipped |
|---|---|---|---|---|---|
| `mvn -pl services/student-services/enrollment-service "-Dtest=EnrollmentDomainServiceTest" test` | `6673d0d…` + WT Part A | Win; JDK 1.8.0_202; Maven 3.9.9 | 0 | 22/22 | LivePostgres not selected |
| `mvn -pl services/student-services/lifecycle-service "-Dtest=LifecycleDomainServiceTest" test` | same | same | 0 | 11/11 | Live DB not selected |
| `mvn -pl services/student-services/document-service "-Dtest=DocumentControllerTest" test` | same | same | 0 | 21/21 | LivePostgres not selected |
| `mvn -pl services/student-services/guardian-service "-Dtest=GuardianDomainServiceTest" test` | same | same | 0 | 42/42 | Live DB not selected |

DB-gated / Playwright demos: **not run** (no DB write / production; live connectivity not required for offline audit).

---

## Part E — Counts and claim disagreements

### Counts by proposed status (220 stories)

| Proposed status | Count |
|---|---|
| completed | 1 |
| partially completed | 105 |
| yet to pickup | 114 |
| **Total** | **220** |

### Counts by Service ID × status

| Service ID | completed | partially completed | yet to pickup |
|---|---:|---:|---:|
| STM-03 | 0 | 25 | 2 |
| STM-04 | 0 | 24 | 1 |
| STM-05 | 0 | 17 | 3 |
| STM-06 | 1 | 14 | 9 |
| STM-07 | 0 | 0 | 20 |
| STM-08 | 0 | 0 | 25 |
| STM-09 | 0 | 0 | 29 |
| STM-10 | 0 | 12 | 18 |
| STM-CROSS | 0 | 12 | 2 |
| All | 0 | 1 | 2 |
| STM-04, 06, 07, 09 | 0 | 0 | 1 |
| STM-05 / STM-06 | 0 | 0 | 1 |
| STM-04, 07, 08, 09 | 0 | 0 | 1 |

### Document claims completion / APPROVED but this audit disagrees

| Issue ID | Document claim | Audit proposed status |
|---|---|---|
| STM-05-US-001 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-003 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-004 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-005 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-006 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-010 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-009 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-011 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-012 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-013 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-014 | PROGRESS closed/APPROVED | partially completed |
| STM-05-US-015 | PROGRESS closed/APPROVED | (not in RBAC-validated backlog CSV) |
| STM-06-US-001 | PROGRESS closed/APPROVED | partially completed |
| STM-06-US-003 | PROGRESS closed/APPROVED | partially completed |
| STM-06-US-004 | PROGRESS closed/APPROVED | partially completed |
| STM-06-US-005 | PROGRESS closed/APPROVED | partially completed |
| STM-06-US-006 | PROGRESS closed/APPROVED | yet to pickup |
| STM-04-US-007 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-008 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-009 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-010 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-011 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-012 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-013 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-014 | PROGRESS closed/APPROVED | partially completed |
| STM-04-US-003 | PROGRESS closed/APPROVED | partially completed |

Note: Only `STM-06-US-002` is audit-`completed` under this redo’s conservative rule.

---

## Part F — Unresolved / open questions

1. Confirm RBAC-validated CSV is the sole backlog for future IMPLEMENT tasks (retire Final User Stories CSV for sequencing).
2. How to store §3.3C `proposed|implemented|tested` — CSV has no status column (only RBAC verdicts).
3. `POLICY DECISION REQUIRED` flags (`STM-RBAC-FLAG-*`) block implementation — owner decisions needed.
4. Same Issue ID means different stories across the two CSVs — do not reuse Final-CSV traceability rows without retitling.
5. Canonical `WAVE_IMPLEMENTATION_PLAN.md` still missing under `docs/project-control/`.
6. Next-task authority still conflicted (pilot track vs Wave 5 vs STM-04-US-017 vs CSV order).
7. STM-07/08/09: no dedicated microservices found — confirm intentional deferral.
8. PILOT H-1..H-4 are outside this CSV; track separately if still in scope.

---

## Part G — Consolidation PROPOSAL (recommendation only)

Unchanged in substance from prior proposal:
1. **ONE** current-progress file: `docs/project-control/CURRENT_PROGRESS.md` (scoreboard + link to this audit).
2. **ONE** next-task file: `docs/prompts/NEXT_TASK.md` (rewrite; retire Antigravity duplicates).
3. Treat **RBAC-validated CSV** as master backlog; archive or clearly demote Final User Stories CSV.
4. Keep per-wave COMPLETION_REPORTs as history; keep TRACEABILITY_VERIFICATION as anti-hallucination.
5. Agreement §3.3: fix WAVE plan path; define COMPLETION_REPORT live file; register this audit + backlog CSV path.

---

## Audit completeness

- All **220** Issue IDs from the RBAC-validated CSV appear in Part D.
- Prior explore evidence reused only where Summary/AC still match; ID remapping called out.
- Only this audit file updated for the redo; no CSV/code/commit operations.

**END OF REDO AUDIT — waiting for owner decision.**
