# CampXSync ID Glossary (STM-07 to STM-10)

**Purpose:** Plain-language meanings for story, enabler, flag and role IDs used in owner reports.
**Sources:** Backlog CSV `CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv`; RBAC matrix dump `docs/audit/RBAC_MATRIX_STUDENT_DUMP.txt`; `docs/project-control/PROGRESS.md` owner decisions.
**Rule:** Where docs do not say, entry is `UNKNOWN` plus the open question.

---

## Role codes (R01-R31)

| Code | Plain name | One-line meaning |
|---|---|---|
| R01 | Super Admin / Platform Admin | Platform-wide administrator (owner D1: IAM operator) |
| R02 | System Admin | Platform system administrator (owner D1: IAM operator) |
| R03 | Institution Admin | Institution-scoped administrator (owner D1: IAM operator) |
| R04 | College Admin | College/campus administrator |
| R05 | Principal | Institution principal |
| R06 | Vice Principal | Institution vice principal |
| R07 | Dean | Academic/institution dean |
| R08 | HOD | Head of department |
| R09 | Program Coordinator | Program/course coordinator |
| R10 | Faculty | Faculty for assigned subjects/courses |
| R11 | Class/Section Coordinator | Coordinator for an assigned section |
| R12 | Controller of Examinations | Examination controller |
| R13 | Exam Cell Officer | Examination cell officer |
| R14 | Valuator/Examiner | Assigned evaluation examiner |
| R15 | Finance Manager | Finance manager |
| R16 | Finance Officer/Accountant | Finance officer / accountant |
| R17 | HR Manager | Human resources manager |
| R18 | HR Officer | Human resources officer |
| R19 | Communication Manager | Communications manager |
| R20 | Placement Officer | Placement officer |
| R21 | Librarian | Librarian |
| R22 | Hostel Warden | Hostel warden |
| R23 | Transport Manager | Transport manager |
| R24 | Facility Manager | Facilities manager |
| R25 | Student | Student — own academic/financial data; approved portal role for linked ACTIVE students |
| R26 | Parent/Guardian | Parent/guardian for a linked student |
| R27 | API Client | Machine identity for external integration |
| R28 | Auditor | Read/audit access |
| R29 | Reporting/Analytics User | Reports and analytics user |
| R30 | Guest | Restricted read-only guest |
| R31 | Registrar | College registrar (pilot catalogue extension for STM staff paths; Path C STM-07.ACCOUNT.A) |

**Note:** Registrar (R31) is a pilot catalogue extension used in STM staff paths; enterprise matrix Table 1 originally listed R01-R30 only (see Roles used by the STM matrix are not in R01-R30 (STM-RBAC-FLAG-001)).

---

## RBAC policy flags

### Roles used by the STM matrix are not in R01-R30 (STM-RBAC-FLAG-001)
- **Decision needed:** How do STM matrix roles that are not in R01-R30 map to the catalogue?
- **Who decides:** Product owner + RBAC owner
- **Blocks:** Any story that invents or grants unmapped role codes; staff paths that relied on invented codes

### Role-configuration / configure permission missing in the matrix (STM-RBAC-FLAG-002)
- **Decision needed:** Who may hold CFG (configure) for lifecycle rules, document types, role mappings, request types, workflows and SLAs?
- **Who decides:** Product owner (must name configure assignees per service)
- **Blocks:** Set which roles apply per college (STM-07-US-011); College role rules and RLS (STM-07-EN-003); other CFG stories in STM-04/06/09/10

### Student consent and self-granting rules (STM-RBAC-FLAG-003)
- **Decision needed:** Who owns consent policy; may students/guardians self-grant; what happens on revocation/suspension?
- **Who decides:** Product owner (partial: D3 maps pilot consent owner to Institution Admin (R03))
- **Blocks:** Consent self-grant features; guardian consent defaults until closed

### Advisor approval removed from enrolment path (STM-RBAC-FLAG-004)
- **Decision needed:** Confirm Faculty/Advisor has no Approve on enrolment (matrix Faculty=View).
- **Who decides:** Product owner — decided D8; completed
- **Blocks:** None (closed)

### Update, delete and retention rights on documents (STM-RBAC-FLAG-005)
- **Decision needed:** Who may update/delete verified document metadata and retention.
- **Who decides:** Product owner — decided D9; completed
- **Blocks:** None (closed for STM-06 document retention scope)

### Auditor access beyond document service (STM-RBAC-FLAG-006)
- **Decision needed:** Confirm Auditor (R28) audit-view scope for STM-03, 04, 05, 07, 08, 09 beyond documents.
- **Who decides:** Product owner + RBAC owner
- **Blocks:** Auditor AU grants outside document service until decided

### Open policy items still listed in the RBAC matrix (STM-RBAC-FLAG-007)
- **Decision needed:** Close remaining matrix 'policy items requiring confirmation' (beyond D6/D12a).
- **Who decides:** Product owner
- **Blocks:** Lifecycle/provisioning/history/request items still listed as open in the matrix

---

## Stories and enablers (STM-07 to STM-10 + STM cross-cutting)

| ID | Type | Plain name | Backlog summary (source) |
|---|---|---|---|
| STM-07-US-001 | Story | Link a student record to an existing login | Link eligible student record to existing IAM identity |
| STM-07-US-002 | Story | Check whether a student is eligible for a login | Validate identity and provisioning eligibility |
| STM-07-US-003 | Story | Staff view of account link status | View account-link status |
| STM-07-US-004 | Story | Request a new login for an eligible student | Initiate eligible student account provisioning |
| STM-07-US-005 | Story | Retry a failed login setup | Retry failed provisioning requests |
| STM-07-US-006 | Story | See past login / provisioning requests | View provisioning history |
| STM-07-US-007 | Story | Get told when an account is activated | Receive account activation notifications |
| STM-07-US-008 | Story | Assign approved student role (R25) | Assign approved student roles |
| STM-07-US-009 | Story | Revoke student role (R25) | Revoke student roles |
| STM-07-US-010 | Story | Cut portal access when student status changes | Suspend or deactivate access from authorized lifecycle events |
| STM-07-US-011 | Story | Set which roles apply per college | Configure tenant-specific role mappings |
| STM-07-US-012 | Story | Audit who changed accounts and roles | Review account and role assignment history |
| STM-07-EN-001 | Enabler | Database tables for student-login links and provisioning | Supabase student-account linkage and provisioning metadata persistence |
| STM-07-EN-002 | Enabler | Use existing login system only (no duplicate auth) | Existing IAM integration; no duplicate authentication, credential or user-role system |
| STM-07-EN-003 | Enabler | College role rules and database security (RLS) | Tenant-scoped role mapping, authorization and RLS |
| STM-07-EN-004 | Enabler | Safe retries and recovery for login setup | Idempotent provisioning, retries, failure recovery and reconciliation |
| STM-07-EN-005 | Enabler | Connect account setup to student, admission, lifecycle and portal | STM-01, STM-02, STM-04 and STM-10 integration |
| STM-07-EN-006 | Enabler | Security tests, logs and go-live readiness for account setup | Security testing, centralized logging, audit, documentation and operational readiness |
| STM-08-US-001 | Story | Integrate approved EXM-01 results into academic history | Integrate approved EXM-01 results into academic history |
| STM-08-US-002 | Story | Validate incoming results against student/programme/term | Validate incoming results against student/programme/term |
| STM-08-US-003 | Story | View verified academic records | View verified academic records |
| STM-08-US-004 | Story | Preserve source result references | Preserve source result references |
| STM-08-US-005 | Story | View earned academic credits | View earned academic credits |
| STM-08-US-006 | Story | View academic progress by programme and term | View academic progress by programme and term |
| STM-08-US-007 | Story | Evaluate progress against approved programme requirements | Evaluate progress against approved programme requirements |
| STM-08-US-008 | Story | View academic performance reports | View academic performance reports |
| STM-08-US-009 | Story | Request academic transcript | Request academic transcript |
| STM-08-US-010 | Story | Generate transcript from approved records | Generate transcript from approved records |
| STM-08-US-011 | Story | View transcript generation and verification status | View transcript generation and verification status |
| STM-08-US-012 | Story | Record academic awards | Record academic awards |
| STM-08-US-013 | Story | View result revaluation status | View result revaluation status |
| STM-08-US-014 | Story | Consume approved revaluation outcomes | Consume approved revaluation outcomes |
| STM-08-US-015 | Story | View previous and revised academic record values | View previous and revised academic record values |
| STM-08-US-016 | Story | Verify academic records | Verify academic records |
| STM-08-EN-001 | Enabler | Supabase academic-history persistence and CRUD integration | Supabase academic-history persistence and CRUD integration |
| STM-08-EN-002 | Enabler | EXM-01 official result API/event integration and source validation | EXM-01 official result API/event integration and source validation |
| STM-08-EN-003 | Enabler | Idempotent result ingestion, correction history and transactional integrity | Idempotent result ingestion, correction history and transactional integrity |
| STM-08-EN-004 | Enabler | RLS, tenant isolation and student-record authorization | RLS, tenant isolation and student-record authorization |
| STM-08-EN-005 | Enabler | STM-03, STM-04, ACD, EXM-01, STM-06 and STM-09 integration | STM-03, STM-04, ACD, EXM-01, STM-06 and STM-09 integration |
| STM-08-EN-006 | Enabler | Transcript generation, shared storage integration and automated tests | Transcript generation, shared storage integration and automated tests |
| STM-08-EN-007 | Enabler | Frontend, audit, centralized logging, documentation and operational readiness | Frontend, audit, centralized logging, documentation and operational readiness |
| STM-09-US-001 | Story | Configure student service request types | Configure student service request types |
| STM-09-US-002 | Story | Browse available service requests | Browse available service requests |
| STM-09-US-003 | Story | Create service request | Create service request |
| STM-09-US-004 | Story | Save request as draft | Save request as draft |
| STM-09-US-005 | Story | Attach supporting documents | Attach supporting documents |
| STM-09-US-006 | Story | Submit request for verification | Submit request for verification |
| STM-09-US-007 | Story | Verify submitted requests | Verify submitted requests |
| STM-09-US-008 | Story | Approve or reject requests | Approve or reject requests |
| STM-09-US-009 | Story | Configure approval workflows | Configure approval workflows |
| STM-09-US-010 | Story | Delegate approval responsibilities | Delegate approval responsibilities |
| STM-09-US-011 | Story | View request status and history | View request status and history |
| STM-09-US-012 | Story | Cancel eligible request | Cancel eligible request |
| STM-09-US-013 | Story | Configure service-level agreements | Configure service-level agreements |
| STM-09-US-014 | Story | Escalate overdue requests | Escalate overdue requests |
| STM-09-US-015 | Story | View SLA performance reports | View SLA performance reports |
| STM-09-US-016 | Story | Receive approved certificates and letters | Receive approved certificates and letters |
| STM-09-US-017 | Story | Route profile corrections to STM-01 | Route profile corrections to STM-01 |
| STM-09-US-018 | Story | Route academic-history requests to STM-08 | Route academic-history requests to STM-08 |
| STM-09-US-019 | Story | Receive request outcome notifications | Receive request outcome notifications |
| STM-09-US-020 | Story | Review request actions and approval history | Review request actions and approval history |
| STM-09-EN-001 | Enabler | Supabase request catalog, workflow and SLA persistence | Supabase request catalog, workflow and SLA persistence |
| STM-09-EN-002 | Enabler | Tenant isolation, RLS and role/resource-scoped authorization | Tenant isolation, RLS and role/resource-scoped authorization |
| STM-09-EN-003 | Enabler | Configurable approval engine, idempotency and retry handling | Configurable approval engine, idempotency and retry handling |
| STM-09-EN-004 | Enabler | STM-01, STM-03, STM-04, STM-06, STM-08 and STM-10 integration | STM-01, STM-03, STM-04, STM-06, STM-08 and STM-10 integration |
| STM-09-EN-005 | Enabler | Shared notification, document generation, storage and audit integration | Shared notification, document generation, storage and audit integration |
| STM-09-EN-006 | Enabler | Frontend request catalog, tracking, approval UI and automated E2E testing | Frontend request catalog, tracking, approval UI and automated E2E testing |
| STM-09-EN-007 | Enabler | Intelligence Services integration for approved classification/workload analytics; AI cannot bypass authorization or approval | Intelligence Services integration for approved classification/workload analytics; AI cannot bypass authorization or approval |
| STM-09-EN-008 | Enabler | Documentation, centralized logging, observability and operational readiness | Documentation, centralized logging, observability and operational readiness |
| STM-10-US-001 | Story | Access portal using existing IAM | Access portal using existing IAM |
| STM-10-US-002 | Story | View personalized dashboard | View personalized dashboard |
| STM-10-US-003 | Story | View academic profile | View academic profile |
| STM-10-US-004 | Story | View timetable | View timetable |
| STM-10-US-005 | Story | View enrolled courses and learning resources | View enrolled courses and learning resources |
| STM-10-US-006 | Story | View examination schedule and results | View examination schedule and results |
| STM-10-US-007 | Story | View fee summaries and payment status | View fee summaries and payment status |
| STM-10-US-008 | Story | View document summaries | View document summaries |
| STM-10-US-009 | Story | View service requests | View service requests |
| STM-10-US-010 | Story | View academic progress and transcript status | View academic progress and transcript status |
| STM-10-US-011 | Story | View notifications and announcements | View notifications and announcements |
| STM-10-US-012 | Story | Configure dashboard widgets | Configure dashboard widgets |
| STM-10-US-013 | Story | Save shortcuts | Save shortcuts |
| STM-10-US-014 | Story | Configure language and display preferences | Configure language and display preferences |
| STM-10-US-015 | Story | View recent portal activities | View recent portal activities |
| STM-10-US-016 | Story | Use portal across desktop/tablet/mobile | Use portal across desktop/tablet/mobile |
| STM-10-US-017 | Story | See clear loading, empty and error states | See clear loading, empty and error states |
| STM-10-US-018 | Story | Use portal during one integrated service outage | Use portal during one integrated service outage |
| STM-10-US-019 | Story | Use accessible portal navigation | Use accessible portal navigation |
| STM-10-US-020 | Story | Log out securely | Log out securely |
| STM-10-EN-001 | Enabler | Supabase persistence for portal-owned preferences/configuration only | Supabase persistence for portal-owned preferences/configuration only |
| STM-10-EN-002 | Enabler | BFF/API aggregation with bounded calls, timeouts, caching and partial-failure handling | BFF/API aggregation with bounded calls, timeouts, caching and partial-failure handling |
| STM-10-EN-003 | Enabler | Existing IAM, Gateway and tenant context integration | Existing IAM, Gateway and tenant context integration |
| STM-10-EN-004 | Enabler | STM-01 through STM-09, ACD, LMS, EXM and FIN integration | STM-01 through STM-09, ACD, LMS, EXM and FIN integration |
| STM-10-EN-005 | Enabler | Responsive frontend, accessibility validation and browser E2E tests | Responsive frontend, accessibility validation and browser E2E tests |
| STM-10-EN-006 | Enabler | RLS, security testing, centralized logging, observability and documentation | RLS, security testing, centralized logging, observability and documentation |
| STM-10-EN-007 | Enabler | Portal performance, operational readiness and authorized Intelligence Services dashboard analytics | Portal performance, operational readiness and authorized Intelligence Services dashboard analytics |
| STM-CROSS-EN-001 | Enabler | Audit existing Supabase/PostgreSQL schemas, migrations, tables, foreign keys, grants and RLS before implementation | Audit existing Supabase/PostgreSQL schemas, migrations, tables, foreign keys, grants and RLS before implementation |
| STM-CROSS-EN-002 | Enabler | Establish service-owned data boundaries; avoid duplicate tables and domain ownership | Establish service-owned data boundaries; avoid duplicate tables and domain ownership |
| STM-CROSS-EN-003 | Enabler | Implement approved Supabase persistence and CRUD operations for each service-owned domain | Implement approved Supabase persistence and CRUD operations for each service-owned domain |
| STM-CROSS-EN-004 | Enabler | Implement tenant isolation, RLS policies and authorization regression tests | Implement tenant isolation, RLS policies and authorization regression tests |
| STM-CROSS-EN-005 | Enabler | Implement centralized exception handling, consistent API error contracts and validation | Implement centralized exception handling, consistent API error contracts and validation |
| STM-CROSS-EN-006 | Enabler | Implement API Gateway integration, OpenAPI specifications and versioned API contracts | Implement API Gateway integration, OpenAPI specifications and versioned API contracts |
| STM-CROSS-EN-007 | Enabler | Shared event bus / Kafka and notifications | Integrate shared Kafka/event bus, notification and audit infrastructure |
| STM-CROSS-EN-008 | Enabler | Integrate Intelligence Services where applicable with domain authorization and safeguards | Integrate Intelligence Services where applicable with domain authorization and safeguards |
| STM-CROSS-EN-009 | Enabler | Implement frontend integration and complete browser workflow validation | Implement frontend integration and complete browser workflow validation |
| STM-CROSS-EN-010 | Enabler | Implement automated unit, integration, tenant-isolation, security and end-to-end tests | Implement automated unit, integration, tenant-isolation, security and end-to-end tests |
| STM-CROSS-EN-011 | Enabler | Maintain technical documentation, API docs, data models and integration specifications | Maintain technical documentation, API docs, data models and integration specifications |
| STM-CROSS-EN-012 | Enabler | Implement centralized structured logging, metrics, tracing, health checks and operational alerts | Implement centralized structured logging, metrics, tracing, health checks and operational alerts |
| STM-CROSS-EN-013 | Enabler | Implement idempotency, concurrency, retry/recovery and transaction integrity where applicable | Implement idempotency, concurrency, retry/recovery and transaction integrity where applicable |
| STM-CROSS-EN-014 | Enabler | Complete operational readiness, deployment configuration, environment validation and regression testing | Complete operational readiness, deployment configuration, environment validation and regression testing |
| STM-07-US-013 | Story | Student sees own link status | View my account-link status |
| STM-07-US-014 | Story | Student asks for portal access (if enabled) | Request portal access where self-request is enabled |
| STM-08-US-017 | Story | Request correction of my academic record | Request correction of my academic record |
| STM-08-US-018 | Story | View limited academic history of my linked student | View limited academic history of my linked student |
| STM-09-US-021 | Story | Raise and track requests for my linked student | Raise and track requests for my linked student |
| STM-10-US-021 | Story | View permitted linked views for my student | View permitted linked views for my student |
| STM-10-US-022 | Story | View role-scoped portal data | View role-scoped portal data |
| STM-10-US-023 | Story | Configure portal settings and publish announcements | Configure portal settings and publish announcements |
| STM-RBAC-EN-001 | Enabler | Central RBAC and ABAC enforcement for all STM services | Central RBAC and ABAC enforcement for all STM services |

### Parking notes (from owner / PROGRESS)
- Academic history service (STM-08-*): **BLOCKED** — needs a new service approval before implementation.
- Student service requests (STM-09-*): **BLOCKED** — needs a new service approval before implementation.
- Get told when an account is activated (STM-07-US-007): **BLOCKED** — needs Shared event bus / Kafka (STM-CROSS-EN-007).
- Set which roles apply per college (STM-07-US-011) and College role rules and database security (STM-07-EN-003): **BLOCKED** — wait on Role-configuration / configure permission missing in the matrix (STM-RBAC-FLAG-002).
- Student asks for portal access (STM-07-US-014): **BLOCKED** — the "if enabled" setting is not defined. **UNKNOWN:** What exact setting key/UI toggles student self-request, and who may enable it?

---

## UNKNOWN items (docs do not fully say)
| Topic | Question |
|---|---|
| Student self-request enablement (STM-07-US-014) | What is the exact setting name, default, and which role may turn it on? |
| Role-configuration assignees (STM-RBAC-FLAG-002) | Which catalogue role(s) receive CFG for STM-07 role mappings? |
| Auditor beyond documents (STM-RBAC-FLAG-006) | Does Auditor (R28) get read-audit on STM-07 account events, or only documents? |
| Open matrix policy list (STM-RBAC-FLAG-007) | Which remaining matrix "policy items requiring confirmation" rows still apply to STM-07 after D1-D6? |

