# Traceability Verification Report

**Generated:** 2026-11-13T12:54:52.267Z
**Source CSV:** `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv`
**Branch check command:** `node scripts/dev/check_traceability.mjs`
**Done statuses counted:** PASS, DONE, VERIFIED, IMPLEMENTED, COMPLETE, CLOSED

## Summary counts

- Total CSV rows (stories/enablers listed): **187**
- Rows marked done: **183**
- Done rows with demo pack (`docs/demos/<ID>/DEMO.md`): **14**
- Done rows without demo pack (**NOT VERIFIED** for demo gate): **169**
- Done rows passing demo+test+mention checks: **14**
- Done rows failing one or more checks (**NOT VERIFIED**): **169**

## Counts per module

| Module | Total rows | Marked done | With demo pack | Without demo pack |
| :--- | ---: | ---: | ---: | ---: |
| STM-01 | 50 | 50 | 0 | 50 |
| STM-02 | 55 | 55 | 0 | 55 |
| STM-03 | 54 | 50 | 0 | 50 |
| STM-04 | 21 | 21 | 9 | 12 |
| STM-05 | 1 | 1 | 0 | 1 |
| STM-06 | 6 | 6 | 5 | 1 |

## Duplicate or conflicting Story IDs

### STM04-US-012 vs STM-04-US-012
- `STM04-US-012` rows: 1 — "Validate graduation eligibility" [PASS]
- `STM-04-US-012` rows: 1 — "Registrar Review and Graduation Eligibility Certification & Degree Clearance" [PASS]
- **Correct ID form:** `STM-04-US-012` (hyphenated module prefix matches Final User Stories / demo pack path `docs/demos/STM-04-US-012/`).
- `STM04-US-012` is a legacy/alternate spelling; treat as conflicting duplicate until CSV is cleaned in a later approved task.

### STM04-US-014 vs STM-04-US-014
- `STM04-US-014` rows: 1 — "Configure lifecycle transition rules" [PASS]
- `STM-04-US-014` rows: 1 — "Institutional Lifecycle Summary & Status Transition Analytics" [PASS]
- **Correct ID form:** `STM-04-US-014` (hyphenated module prefix matches Final User Stories / demo pack path `docs/demos/STM-04-US-014/`).
- `STM04-US-014` is a legacy/alternate spelling; treat as conflicting duplicate until CSV is cleaned in a later approved task.

### Additional repeated IDs in CSV
- `STM03-US-001` appears **2** times.
- `STM03-US-002` appears **2** times.
- `STM03-US-003` appears **2** times.
- `STM03-US-004` appears **2** times.
- `STM03-US-005` appears **2** times.
- `STM03-US-006` appears **2** times.
- `STM03-US-007` appears **2** times.
- `STM03-US-008` appears **2** times.
- `STM03-US-009` appears **2** times.
- `STM03-US-010` appears **2** times.
- `STM03-US-011` appears **2** times.
- `STM03-US-012` appears **2** times.
- `STM03-US-013` appears **2** times.
- `STM03-US-014` appears **2** times.
- `STM03-US-015` appears **2** times.
- `STM03-US-016` appears **2** times.
- `STM03-US-017` appears **2** times.
- `STM03-US-018` appears **2** times.
- `STM03-US-019` appears **2** times.
- `STM03-EN-001` appears **2** times.
- `STM03-EN-002` appears **2** times.
- `STM03-EN-003` appears **2** times.
- `STM03-EN-004` appears **2** times.
- `STM03-EN-005` appears **2** times.
- `STM03-EN-006` appears **2** times.
- `STM03-EN-007` appears **2** times.

## Title conflicts (STM-04 US-007 / 008 / 010 / 011)

Story source of truth for titles: `D:/CampXSync/Documentation/StudentModule/UserStories/CampXSync_STM-03_to_STM-10_Final_User_Stories.csv`.

### STM-04-US-007
- **Correct title (Final User Stories):** Request return from leave or reinstatement
- CSV title: "Program Transfer Request & Academic Standing" [PASS] — **CONFLICT**

### STM-04-US-008
- **Correct title (Final User Stories):** Reinstate eligible students
- CSV title: "Student Leave of Absence Request & Date Span Enforcement" [PASS] — **CONFLICT**

### STM-04-US-010
- **Correct title (Final User Stories):** Process transfer requests
- CSV title: "Student Voluntary Withdrawal Petition & Institutional Exit Request" [PASS] — **CONFLICT**

### STM-04-US-011
- **Correct title (Final User Stories):** Submit and process a withdrawal
- CSV title: "Student Reinstatement Request & Return to Active Academic Standing" [PASS] — **CONFLICT**

## Done rows without demo pack (NOT VERIFIED)

| Story ID | Title | Status | Test evidence | Commit/file mention |
| :--- | :--- | :---: | :---: | :---: |
| STM01-US-001 | Create student profile | PASS | YES | YES |
| STM01-US-002 | List and search student profiles | PASS | YES | YES |
| STM01-US-003 | View a student profile | PASS | YES | YES |
| STM01-US-004 | Update permitted profile fields | PASS | YES | YES |
| STM01-US-005 | Preserve Unicode and validate names | PASS | YES | YES |
| STM01-US-006 | Assign institutional student number | PASS | YES | YES |
| STM01-US-007 | Prevent duplicate student identity | PASS | YES | YES |
| STM01-US-008 | Manage alternate identifiers | PASS | YES | YES |
| STM01-US-009 | View identifier details with masking | PASS | YES | YES |
| STM01-US-010 | Submit profile correction request | PASS | YES | YES |
| STM01-US-011 | Review correction request | PASS | YES | YES |
| STM01-US-012 | Resubmit corrected profile | PASS | YES | YES |
| STM01-US-013 | Initiate profile verification | PASS | YES | YES |
| STM01-US-014 | Approve or reject verification | PASS | YES | YES |
| STM01-US-015 | Track profile lifecycle state | PASS | YES | YES |
| STM01-US-016 | Record and verify contact details | PASS | YES | YES |
| STM01-US-017 | Manage student addresses | PASS | YES | YES |
| STM01-US-018 | Link guardian or parent relationship | PASS | YES | YES |
| STM01-US-019 | Enforce guardian consent and visibility | PASS | YES | YES |
| STM01-US-020 | Attach document metadata reference | PASS | YES | YES |
| STM01-US-021 | Track document verification state | PASS | YES | YES |
| STM01-US-022 | Manage profile preferences | PASS | YES | YES |
| STM01-US-023 | Link profile to existing IAM identity | PASS | YES | YES |
| STM01-US-024 | Request account provisioning | PASS | YES | YES |
| STM01-US-025 | View profile change history | PASS | YES | YES |
| STM01-US-026 | Audit privileged profile actions | PASS | YES | YES |
| STM01-US-027 | Publish profile-created event | PASS | YES | YES |
| STM01-US-028 | Publish safe profile-updated event | PASS | YES | YES |
| STM01-US-029 | Notify student about verification or correction status | PASS | YES | YES |
| STM01-US-030 | Enforce tenant isolation on all profile operations | PASS | YES | YES |
| STM01-US-031 | Enforce role and field-level authorization | PASS | YES | YES |
| STM01-US-032 | Handle missing or invalid authentication context | PASS | YES | YES |
| STM01-US-033 | Support optimistic concurrency | PASS | YES | YES |
| STM01-US-034 | Make profile creation and identifier assignment atomic | PASS | YES | YES |
| STM01-US-035 | Handle idempotent create and retry requests | PASS | YES | YES |
| STM01-US-036 | Validate partial update null semantics | PASS | YES | YES |
| STM01-US-037 | Protect sensitive data in logs and errors | PASS | YES | YES |
| STM01-US-038 | Apply safe search and rate limits | PASS | YES | YES |
| STM01-US-039 | Preserve records and lifecycle history | PASS | YES | YES |
| STM01-US-040 | Handle dependency failures safely | PASS | YES | YES |
| STM01-US-041 | Use standard API error contracts | PASS | YES | YES |
| STM01-US-042 | Support bounded profile photo reference | PASS | YES | YES |
| STM01-US-043 | Coordinate with admissions without duplicate profiles | PASS | YES | YES |
| STM01-US-044 | Expose only authorized profile projection to downstream services | PASS | YES | YES |
| STM01-US-045 | Support accessibility and locale preferences | PASS | YES | YES |
| STM01-US-046 | Validate tenant and actor as server-controlled fields | PASS | YES | YES |
| STM01-US-047 | Enforce verification expiry and re-verification | PASS | YES | YES |
| STM01-US-048 | Ensure guardian relationship changes are audited | PASS | YES | YES |
| STM01-US-049 | Provide safe profile export/reference integration | PASS | YES | YES |
| STM01-US-050 | Maintain migration and schema compatibility | PASS | YES | YES |
| STM02-EN-001 | Read-only reuse-vs-gap audit of active branch | PASS | NO | YES |
| STM02-EN-002 | Decide applicant identity model before draft creation | PASS | YES | YES |
| STM02-EN-003 | Tenant isolation and RLS verification harness | PASS | YES | YES |
| STM02-EN-004 | Application state machine and transition engine | PASS | YES | YES |
| STM02-EN-005 | Idempotency, optimistic concurrency and transactional outbox | PASS | YES | YES |
| STM02-EN-006 | Application number generation and duplicate business key | PASS | YES | YES |
| STM02-EN-007 | Observability, correlation and abuse protection | PASS | YES | YES |
| STM02-US-001 | Configure admission intake | PASS | YES | YES |
| STM02-US-002 | Configure versioned eligibility criteria | PASS | YES | YES |
| STM02-US-003 | Configure required documents and fee requirements | PASS | YES | YES |
| STM02-US-004 | View available admission programmes | PASS | YES | YES |
| STM02-US-005 | Create a new application | PASS | YES | YES |
| STM02-US-006 | Save, resume and update an application draft | PASS | YES | YES |
| STM02-US-008 | Submit an application | PASS | YES | YES |
| STM02-US-009 | Evaluate application completeness | PASS | YES | YES |
| STM02-US-010 | Evaluate applicant eligibility | PASS | YES | YES |
| STM02-US-011 | Upload application documents | PASS | YES | YES |
| STM02-US-012 | Verify submitted documents | PASS | YES | YES |
| STM02-US-013 | Assign an application to a reviewer | PASS | YES | YES |
| STM02-US-014 | Review and assess an application | PASS | YES | YES |
| STM02-US-015 | Record an admission decision | PASS | YES | YES |
| STM02-US-016 | Generate and issue an admission offer | PASS | YES | YES |
| STM02-US-017 | Accept an admission offer | PASS | YES | YES |
| STM02-US-018 | Decline an admission offer | PASS | YES | YES |
| STM02-US-023 | Notify applicants of admission events | PASS | YES | YES |
| STM02-US-024 | View the application audit trail | PASS | YES | YES |
| STM02-US-026 | View own application status and timeline | PASS | YES | YES |
| STM02-US-027 | Search and retrieve applications within delegated scope | PASS | YES | YES |
| STM02-US-036 | Review, publish, version and retire intake configuration | PASS | YES | YES |
| STM02-US-040 | Record declaration and privacy consent at submission | PASS | YES | YES |
| STM02-US-041 | Respond to an information request and resubmit | PASS | YES | YES |
| STM02-US-044 | Track document checklist and replace rejected documents | PASS | YES | YES |
| STM02-US-045 | View my assigned applications | PASS | YES | YES |
| STM02-US-046 | View offer details | PASS | YES | YES |
| STM02-US-047 | Expire unaccepted offers automatically | PASS | YES | YES |
| STM02-EN-008 | Receive and reconcile verified payment status | PASS | YES | YES |
| STM02-EN-009 | Link or create canonical STM-01 student identity | PASS | YES | YES |
| STM02-EN-010 | Hand over an accepted applicant to STM-03 | PASS | YES | YES |
| STM02-EN-011 | Reconcile missed or delayed integration events | PASS | YES | YES |
| STM02-US-019 | Initiate application fee payment | PASS | YES | YES |
| STM02-US-028 | Withdraw an application (applicant) | PASS | YES | YES |
| STM02-US-033 | Monitor and recover failed downstream handovers | PASS | YES | YES |
| STM02-US-037 | Grant an audited late-submission exception | PASS | YES | YES |
| STM02-US-039 | Create an application on behalf of an applicant | PASS | YES | YES |
| STM02-US-043 | Record an audited eligibility override | PASS | YES | YES |
| STM02-US-048 | Extend or reopen an offer by exception | PASS | YES | YES |
| STM02-US-025 | View admissions operational reports | PASS | YES | YES |
| STM02-US-029 | Schedule and record interviews or admission assessments | PASS | YES | YES |
| STM02-US-030 | Manage waitlist ranking and offer progression | PASS | YES | YES |
| STM02-US-031 | Request and process an admission decision review (appeal) | PASS | YES | YES |
| STM02-US-032 | Request a fee waiver or refund via Finance | PASS | YES | YES |
| STM02-US-034 | Export admissions reports within authorized scope | PASS | YES | YES |
| STM02-US-038 | Guardian views limited application status | PASS | YES | YES |
| STM02-US-042 | Withdraw an application on applicant behalf | PASS | YES | YES |
| STM02-US-049 | Handle applicant data correction, retention and deletion | PASS | YES | YES |
| STM03-US-001 | Convert approved admission into enrolment | PASS | YES | YES |
| STM03-US-002 | View enrolment details | PASS | YES | YES |
| STM03-US-003 | Validate enrolment eligibility | PASS | YES | YES |
| STM03-US-004 | Update enrolment status | PASS | YES | YES |
| STM03-US-005 | View enrolment history | PASS | YES | YES |
| STM03-US-006 | Select programme and term courses | PASS | YES | YES |
| STM03-US-007 | Select available course sections | PASS | YES | YES |
| STM03-US-008 | Enforce course prerequisites | PASS | YES | YES |
| STM03-US-009 | Enforce section capacity | PASS | YES | YES |
| STM03-US-010 | Remove course during permitted period | PASS | YES | YES |
| STM03-US-011 | Allocate students to sections | PASS | YES | YES |
| STM03-US-012 | Join full-section waitlist | PASS | YES | YES |
| STM03-US-013 | Promote eligible waitlisted students | PASS | YES | YES |
| STM03-US-014 | View waitlist position and status | PASS | YES | YES |
| STM03-US-015 | Configure registration windows | PASS | YES | YES |
| STM03-US-016 | Configure academic years and terms | PASS | YES | YES |
| STM03-US-017 | View enrolment change history | PASS | YES | YES |
| STM03-US-019 | View my assigned course roster | PASS | YES | YES |
| STM03-EN-001 | Supabase persistence and schema ownership | PASS | YES | YES |
| STM03-EN-002 | Tenant isolation RLS and auth regression | PASS | YES | YES |
| STM03-EN-003 | Concurrent seat allocation and idempotency | PASS | YES | YES |
| STM03-EN-004 | API Gateway OpenAPI contracts | PASS | YES | YES |
| STM03-EN-005 | Frontend enrolment UI | PASS | YES | YES |
| STM03-EN-007 | Technical documentation | PASS | YES | YES |
| STM03-US-001 | Convert approved admission into enrolment | PASS | YES | YES |
| STM03-US-002 | View enrolment details | PASS | YES | YES |
| STM03-US-003 | Validate enrolment eligibility | PASS | YES | YES |
| STM03-US-004 | Update enrolment status | PASS | YES | YES |
| STM03-US-005 | View enrolment history | PASS | YES | YES |
| STM03-US-006 | Select programme and term courses | PASS | YES | YES |
| STM03-US-007 | Select available course sections | PASS | YES | YES |
| STM03-US-008 | Enforce course prerequisites | PASS | YES | YES |
| STM03-US-009 | Enforce section capacity | PASS | YES | YES |
| STM03-US-010 | Remove course during permitted period | PASS | YES | YES |
| STM03-US-011 | Allocate students to sections | PASS | YES | YES |
| STM03-US-012 | Join full-section waitlist | PASS | YES | YES |
| STM03-US-013 | Promote eligible waitlisted students | PASS | YES | YES |
| STM03-US-014 | View waitlist position and status | PASS | YES | YES |
| STM03-US-015 | Configure registration windows | PASS | YES | YES |
| STM03-US-016 | Configure academic years and terms | PASS | YES | YES |
| STM03-US-017 | View enrolment change history | PASS | YES | YES |
| STM03-US-019 | View my assigned course roster | PASS | YES | YES |
| STM03-EN-001 | Supabase persistence and schema ownership | PASS | YES | YES |
| STM03-EN-002 | Tenant isolation RLS and auth regression | PASS | YES | YES |
| STM03-EN-003 | Concurrent seat allocation and idempotency | PASS | YES | YES |
| STM03-EN-004 | API Gateway OpenAPI contracts | PASS | YES | YES |
| STM03-EN-005 | Frontend enrolment UI | PASS | YES | YES |
| STM03-EN-007 | Technical documentation | PASS | YES | YES |
| STM04-US-001 | View current lifecycle status | PASS | YES | YES |
| STM04-US-002 | Update lifecycle status through authorized workflow | PASS | YES | YES |
| STM04-US-004 | View complete lifecycle history | PASS | YES | YES |
| STM04-US-005 | Submit leave-of-absence request | PASS | YES | YES |
| STM04-US-006 | Approve or reject leave requests | PASS | YES | YES |
| STM04-US-012 | Validate graduation eligibility | PASS | NO | YES |
| STM04-US-013 | Record graduation details | PASS | NO | YES |
| STM04-US-014 | Configure lifecycle transition rules | PASS | YES | YES |
| STM04-US-015 | Trigger downstream events for lifecycle changes | PASS | YES | YES |
| STM04-EN-001 | Supabase lifecycle persistence | PASS | YES | YES |
| STM04-EN-002 | Lifecycle RLS and integrity | PASS | NO | YES |
| STM04-EN-005 | Frontend lifecycle self-service | PASS | YES | YES |
| STM03-LIVE-JDBC | STM-03 live JDBC hardening | PASS | YES | YES |
| STM03-HTTP-E2E | STM-03 HTTP register path hardening | PASS | YES | YES |
| STM-05-US-015 | Link one guardian to several students | PASS | YES | YES |
| STM-06-US-001 | Configure document types | PASS | YES | YES |

## Done rows failing other checks (demo present but still NOT VERIFIED)

_None._

## Demo packs present on disk

Found **14** demo packs with DEMO.md:
- `docs/demos/STM-04-US-003/DEMO.md`
- `docs/demos/STM-04-US-007/DEMO.md`
- `docs/demos/STM-04-US-008/DEMO.md`
- `docs/demos/STM-04-US-009/DEMO.md`
- `docs/demos/STM-04-US-010/DEMO.md`
- `docs/demos/STM-04-US-011/DEMO.md`
- `docs/demos/STM-04-US-012/DEMO.md`
- `docs/demos/STM-04-US-013/DEMO.md`
- `docs/demos/STM-04-US-014/DEMO.md`
- `docs/demos/STM-06-US-002/DEMO.md`
- `docs/demos/STM-06-US-003/DEMO.md`
- `docs/demos/STM-06-US-004/DEMO.md`
- `docs/demos/STM-06-US-005/DEMO.md`
- `docs/demos/STM-06-US-006/DEMO.md`

## Interpretation rule for downstream docs

- Use **done-with-demo** counts when stating verified story delivery.
- Any row marked PASS/DONE without `docs/demos/<Story-ID>/DEMO.md` must be labeled **NOT VERIFIED**.
- Do not treat legacy `STM04-US-*` IDs as the same verified row as hyphenated `STM-04-US-*` without explicit reconciliation.
