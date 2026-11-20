# CampXSync — Enterprise RBAC Matrix (IADM / CADM Edition)

**Document:** `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v2.md`  
**Version:** 2.0 Draft  
**Date:** 2026-11-20  
**Status:** DRAFT for owner review — **not** permission-catalog, seed, or database change authorization  
**Supersedes (Admin sections only):** `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` (2026-11-15) §§11–12 and Admin rows of §4; hierarchy Tenant Admin node  
**Preserves:** R01–R31 catalogue, STM-01…STM-10 action matrix, ACD/LMS/EXM/FIN/HRM/CAM domain matrices (with R03/R04 ceiling notes)  
**Companion:** `CampXSync_Admin_RBAC_Redesign_Draft_v1.md`  
**Prior STM proposal (historical):** `docs/audit/rbac-foundation-2026-11-07/PROPOSED_ENTERPRISE_RBAC_MATRIX.md`

---

## 1. Purpose and scope

This document is the **next enterprise RBAC matrix** for CampXSync. It keeps the hybrid **RBAC + ABAC** model: RBAC defines what a role may do; attribute and policy controls define the data scope in which that action is valid.

What this edition changes:

1. Administration is expressed as **IADM-01…IADM-08** and **CADM-01…CADM-09**, not as undifferentiated “Platform Admin / College Admin” blobs.
2. **Institution Admin (R03)** is the institute-scoped IADM operator; **College Admin (R04)** is narrowed to the CADM boundary.
3. **Tenant Admin (`*`)** is collapsed into R03 — no wildcard escape hatch (owner decision IADM-D7).
4. Functional modules (Academic, Exam, Finance, HRM, LMS, Communication) remain owned by their domain roles — not by CADM.

What this edition does **not** do:

- Change live `iam.*` grants, seeds, or Postgres.
- Rename `adm*` permission keys (optional later migration).
- Remediate Security Findings Register items (separate approvals).

---

## 2. Governing owner decisions

| ID | Decision | Matrix impact |
|---|---|---|
| IADM-D1 | Platform Super Admin ops described inside IADM | R01 on cross-institute IADM rows |
| IADM-D2 | Institute Super Admin = **R03** | R03 is primary IADM tenant operator |
| IADM-D3 | Narrow R03 to IADM boundary | R03 loses non-IADM module ownership |
| IADM-D7 | Narrow Tenant Admin → R03; no `*` | Hierarchy and ceilings updated |
| CADM-D1 | Timetable = CADM-09 (existing :8087) | Timetable under CADM-09 |
| CADM-D3 | Narrow R04 to CADM boundary | R04 loses Academic/Exam/Finance/HR **M** |
| IADM-D5 | `adm03` dormant outside IADM/CADM | R27 / API-client grants unchanged for now |
| Prior | Registrar = **R31** | STM admin rows use R31 |

---

## 3. Enterprise role catalogue (canonical)

| Code | Role | Primary scope | Notes in v2 |
|---|---|---|---|
| R01 | Super Admin | Platform / cross-institute | ≈ Platform Super Admin for IADM |
| R02 | System Admin | Platform technical | Shared services, gateway, ops |
| R03 | Institution Admin | One institute (tenant) | **IADM ceiling only** |
| R04 | College Admin | One college / campus | **CADM ceiling only** |
| R05 | Principal | Institution / college | Absorbs some work removed from R04 |
| R06 | Vice Principal | Institution | As prior matrix |
| R07 | Dean | Academic / institution | As prior matrix |
| R08 | HOD | Department | As prior matrix |
| R09 | Program Coordinator | Program / course | As prior matrix |
| R10 | Faculty | Assigned subjects / courses | As prior matrix |
| R11 | Class / Section Coordinator | Assigned section | As prior matrix |
| R12 | Controller of Examinations | Examination | As prior matrix |
| R13 | Exam Cell Officer | Examination | As prior matrix |
| R14 | Valuator / Examiner | Assigned evaluation | As prior matrix |
| R15 | Finance Manager | Finance | As prior matrix |
| R16 | Finance Officer / Accountant | Finance | As prior matrix |
| R17 | HR Manager | HR | As prior matrix |
| R18 | HR Officer | HR | As prior matrix |
| R19 | Communication Manager | Communication | As prior matrix |
| R20 | Placement Officer | Placement | As prior matrix |
| R21 | Librarian | Library | As prior matrix |
| R22 | Hostel Warden | Hostel | As prior matrix |
| R23 | Transport Manager | Transport | As prior matrix |
| R24 | Facility Manager | Facilities | Room master ownership deferred vs CADM-05 |
| R25 | Student | Own academic / financial data | As prior matrix |
| R26 | Parent / Guardian | Linked student | Consent + relationship required |
| R27 | API Client | External integration | Machine identity; `adm03` dormant |
| R28 | Auditor | Read / audit | As prior matrix |
| R29 | Reporting / Analytics User | Reports | As prior matrix |
| R30 | Guest | Restricted read | As prior matrix |
| R31 | Registrar | Assigned campus | STM principal; not widened by Admin redesign |

**Retired posture:** Tenant Admin as a superior `*`-granting role is **not** part of the target catalogue. Existing DEV seeds that use it must be mapped to R03 via the coverage map before any apply.

---

## 4. Standard permission vocabulary

| Code | Permission | Meaning |
|---|---|---|
| V | View | Read permitted data |
| C | Create | Create records |
| U | Update | Modify records |
| D | Delete | Delete / void where permitted |
| A | Approve | Approve workflow |
| R | Reject | Reject workflow |
| P | Publish | Make information officially available |
| X | Execute | Execute an operational action |
| E | Export | Export permitted data |
| I | Import | Import data |
| AS | Assign | Assign users / resources |
| RV | Review | Review / verify before approval |
| AU | Audit | View audit information |
| CFG | Configure | Configure service |
| ADM | Administer | Full service administration within ceiling |
| M | Manage | Composite administer / configure within service (ownership tables) |
| W | Write | Create / update operational data |
| — | None | No direct access |

---

## 5. Effective access model

```
Effective Access =
  Role Permission
  + Role Hierarchy (informational; permissions are not inherited unless explicitly granted)
  + Tenant Scope
  + Institution Scope
  + Campus Scope
  + Department Scope
  + Course / Section Scope
  + Resource Ownership
  + ABAC Policy
  + Time / Validity Constraints
  + Delegation (limited to recorded role + delegator scope)
```

Architectural locks:

1. Roles do not grant unrestricted microservice access.
2. ABAC / RLS enforce data scope; UI hiding is not a security boundary.
3. High-risk workflows enforce Segregation of Duties.
4. Tenant boundaries are enforced in Java services and PostgreSQL RLS.
5. Portal (STM-10) is not a source of truth and not an authZ bypass.
6. API clients (R27) need explicit scopes; never silent `*`.

---

## 6. Target role hierarchy (Admin redesign)

```
R01 Super Admin (platform / cross-institute)
 └── R02 System Admin (platform technical)
      └── R03 Institution Admin (IADM, one institute)
           └── R04 College Admin (CADM, one college)
                └── R05 Principal / R06 Vice Principal
                     ├── R07 Dean
                     └── R08 HOD
                          ├── R09 Program Coordinator
                          └── R10 Faculty
                               └── R25 Student
                                    └── R26 Parent/Guardian
```

Specialized roles operate **alongside** this hierarchy (not under College Admin as owners of those domains): R12–R24, R28–R31.

**Removed from prior diagram:** `TENANT ADMIN` node above Institution Admin.

---

## 7. Master microservice ownership matrix (v2)

Legend: primary administrative owner vs operational consumers.  
**Change from 2026-11-15:** Admin rows use IADM/CADM; Academic Admin / College Admin are no longer listed as owners of ACD/EXM/FIN/HRM.

| Service group | Domain | Primary administrative roles | Operational roles |
|---|---|---|---|
| IADM-01…08 | Institute / platform administration | R01, R02, R03 (per §8) | Scoped auditors / operators |
| CADM-01…09 | College administration | R04 (per §9); R05/R08/R31 where noted | Principal, HOD, Registrar, staff |
| ACD-01…10 | Academic services | Domain academic operators (Principal / HOD / Coordinator); **not R04 as module owner** | Faculty, Student |
| LMS-01…10 | Learning management | Academic operators | HOD / Coordinator / Faculty / Student |
| EXM-01…09 | Examination | R12 Controller of Examinations | Exam Cell / Valuator / HOD / Faculty / Student |
| FIN-01…10 | Finance & accounts | R15 Finance Manager | Accountant / Principal / Student / Parent |
| HRM-01…09 | Human resources | R17 HR Manager | HR Officer / Principal / HOD / Employee |
| CAM-01…08 | Communication | R19 Communication Manager | Principal / HOD / Faculty / Student / Parent |
| Shared services | Identity, RBAC, Audit, Gateway, Reporting | R01 / R02 | Scoped business roles |
| STM-01 | Student identity & profile | R31 Registrar / Admissions; R04 scoped | Student; authorized staff; Faculty (assigned); Guardian |
| STM-02 | Admissions | R31 / Admissions; delegated approver | Applicant/Student; dept reviewer; Faculty (review); Guardian |
| STM-03 | Enrolment | R31 / delegated enrolment approver | Student; dept staff; Faculty (roster) |
| STM-04 | Lifecycle | R31; delegated Principal/Dean/HOD | Student; assigned staff; Faculty; Guardian |
| STM-05 | Guardian & relationships | R31 / delegated verifier | Student; verified Guardian |
| STM-06 | Documents | Delegated verifier / R31 | Student; assigned staff; Auditor |
| STM-07 | Account provisioning | Existing IAM; delegated R04 or R31 | Student; provisioning service; IAM operators (R01–R03 per prior decisions) |
| STM-08 | Academic history | Academic records staff; source approvers | Student; Faculty; Exam Cell; Dean; Guardian |
| STM-09 | Service requests | Request-type delegated approver | Student; Guardian; reviewer; R31 / dept staff |
| STM-10 | Portal & experience | Scoped R04 / portal config owner; R19 for announcements | Student; Guardian; Faculty/staff |

---

## 8. IADM services × admin roles

| Service | R01 | R02 | R03 | R04 | R28 Auditor | Notes |
|---|---|---|---|---|---|---|
| IADM-01 Institute & Tenant Lifecycle | M (cross-institute) | W/V | M (own institute) | — | V | First College Admin handover → CADM-02 |
| IADM-02 Platform Configuration & Policy | M | M | W within allowed institute settings | V effective only | V | R04 cannot override IADM values |
| IADM-03 Platform Identity & Access Governance | M | M | M within institute ceiling | request only | V | Ceilings for college roles |
| IADM-04 Subscription, Billing & Entitlements | M | W | V / limited W own subscription | — | V | Student fees stay FIN |
| IADM-05 Data Governance & Privacy | M | W | W own institute adherence | V / execute under policy | V | Domain services execute erasure/export |
| IADM-06 Platform Analytics & Usage | M/E | V/E | V/E own institute aggregate | — | V | No raw cross-college browse |
| IADM-07 Security & Compliance Governance | M | M | W own institute posture | V | V | Technical enforcement in shared services |
| IADM-08 Audit, Health & Operations | M | M | V own institute | — | M/V | Shared audit store; no second store |

---

## 9. CADM services × college roles

| Service | R04 | R05 Principal | R08 HOD | R31 Registrar | R03 | Notes |
|---|---|---|---|---|---|---|
| CADM-01 College Profile & Configuration | M (delegated only) | V | — | V | V | Fail closed if delegation missing |
| CADM-02 College User Administration | M | V/A as policy | V limited | V/AS where STM allows | — after handover | Post first-admin |
| CADM-03 College Roles & Access Admin | M within ceiling | A limited | — | AS where delegated | Ceiling via IADM-03 | Cannot exceed IADM ceiling |
| CADM-04 Student & Faculty Admin Directory | M admin attrs only | V | V dept | V/W STM-aligned | — | Not Student/HR master |
| CADM-05 Campus Resources & Operations | M provisional | V | V | V | — | Room master deferred vs Facilities (R24) |
| CADM-06 Reporting & Analytics | M/E college | V/E | V/E dept | V/E | — | Consumes domain APIs |
| CADM-07 Audit & Compliance | V/W college | V | V | V | — | Shared audit source |
| CADM-08 Integration & Data Sync | M college | V | — | V/I where assigned | — | Kafka deferred |
| CADM-09 Timetable Management | M | A/V | V/W dept | V | — | Existing :8087; exam TT stays EXM |

---

## 10. R03 / R04 ceiling — what must leave (design target)

These are **targets for the coverage map**, not an applied grant list.

| Removed from | Example access that must leave | Likely new owner (confirm per key) |
|---|---|---|
| R03 | Academic structure M/W, exam ops, fee ops, HR ops, LMS admin | ACD/EXM/FIN/HRM roles; R05–R08 / R12–R18 |
| R04 | ACD-01…10 **M**, EXM manage, FIN manage, HRM manage, LMS admin | Same functional roles; Principal / HOD / CoE / Finance Manager / HR Manager |
| Tenant Admin | Entire `*` grant; any “above R03” posture | Collapse to R03 |

**Hard rule:** Do not remove a live grant until the coverage map names a surviving role (or owner accepts “no access”).

---

## 11. Domain matrices (preserved with Admin ceiling notes)

Domain tables below are carried forward from the 2026-11-15 combined matrix.  
**v2 rule:** where a column shows **College Admin = M** on ACD/LMS/FIN/HRM/CAM as if CADM owned the module, treat that as **legacy to be narrowed** — operational view (V) or delegated workflow only, after coverage map. Module **M** belongs to the domain admin roles named in §7.

### 11.1 Academic — ACD-01…ACD-10

| Microservice | Principal | HOD | Coordinator | Faculty | Student | College Admin (v2 target) | Domain admin |
|---|---|---|---|---|---|---|---|
| ACD-01 Academic Structure | M | W | V | V | V | V (not M) | Domain academic admin |
| ACD-02 Program Management | A | M | W | V | V | V | Domain academic admin |
| ACD-03 Course/Curriculum | A | M | W | W | V | V | Domain academic admin |
| ACD-04 Subject Management | V | M | W | W | V | V | Domain academic admin |
| ACD-05 Faculty Allocation | A | W | W | V | — | V / AS only if delegated | Domain academic admin |
| ACD-06 Student Academic Profile | V | W | W | W | V | V scoped | Domain academic admin |
| ACD-07 Section/Batch Management | V | M | M | W | V | V | Domain academic admin |
| ACD-08 Attendance Management | V | RV | W | W | V | V | Domain academic admin |
| ACD-09 Academic Planning | A | M | W | W | V | V | Domain academic admin |
| ACD-10 Academic Monitoring & Analytics | V | RV | RV | V | V | V/E | Domain academic admin |

Scope rule: Faculty access resolves to assigned course/subject/section, not institution-wide academic administration.

### 11.2 LMS — LMS-01…LMS-10

| Microservice | Faculty | HOD | Coordinator | Student | Parent | Principal | Domain admin |
|---|---|---|---|---|---|---|---|
| LMS-01 Course Management | W | M | M | V | V | V | M |
| LMS-02 Course Content | M | RV | RV | V | V | V | M |
| LMS-03 Subject Management | W | M | M | V | V | V | M |
| LMS-04 Lesson Planning | M | RV | RV | V | V | V | M |
| LMS-05 Assignments | M | RV | RV | W/X | V | V | M |
| LMS-06 Virtual Classroom | M | V | V | X | V | V | M |
| LMS-07 Resources Management | W | M | M | V | V | V | M |
| LMS-08 Progress Tracking | W | RV | RV | V | V | V | M |
| LMS-09 Certification & Badges | RV | A | A | V | V | V | M |
| LMS-10 CO-TLO-PO | W | RV | M | V | — | A | M |

**v2:** R04 is not LMS module owner (— or V only after coverage map).

### 11.3 Examination — EXM-01…EXM-09

| Microservice | Exam Controller | Exam Cell | Faculty | Valuator | HOD | Student | Principal |
|---|---|---|---|---|---|---|---|
| EXM-01 Student Exam Registration | M | W | V | — | RV | W | V |
| EXM-02 Exam Scheduling | A | W | V | V | V | V | A |
| EXM-03 Hall Ticket | A | W/P | V | — | V | V | V |
| EXM-04 Valuation Management | M | W | — | X/W | RV | — | — |
| EXM-05 Marks Approval | A | RV | W | W | RV | — | V |
| EXM-06 Mark Cards | A | W/P | V | — | V | V | V |
| EXM-07 Script Valuation | M | W | — | X/W | RV | — | — |
| EXM-08 Results Management | A | W | V | — | RV | V | A |
| EXM-09 Examination Reporting | M/E | E | E | V | E | V | E |

SoD: Valuator ≠ Marks Approver ≠ Result Publisher.  
**v2:** Examination timetables stay in EXM — not CADM-09.

### 11.4 Finance — FIN-01…FIN-10

| Microservice | Finance Manager | Accountant | Principal | College Admin (v2) | HOD | Student | Parent |
|---|---|---|---|---|---|---|---|
| FIN-01 Fee Structure | M | W | A | V | V | V | V |
| FIN-02 Fee Collection | M | W/X | V | V | — | X/V | X/V |
| FIN-03 Expense Management | M | W | A | V (not W/M) | W | — | — |
| FIN-04 Tax Management | M | W | A | V | — | — | — |
| FIN-05 Scholarship Management | A | W | A | V / W only if delegated | RV | W | V |
| FIN-06 Payroll | M | W/X | A | V | — | — | — |
| FIN-07 Finance Reports | M/E | E | E | E scoped | V | V | V |
| FIN-08 Audit & Compliance | M | W | V | V | — | — | — |
| FIN-09 Budget Management | M | W | A | V | RV | — | — |
| FIN-10 Financial Analytics | M/E | E | E | E scoped | V | V | — |

### 11.5 HRM — HRM-01…HRM-09

| Microservice | HR Manager | HR Officer | Principal | HOD | Employee/Faculty | College Admin (v2) |
|---|---|---|---|---|---|---|
| HRM-01 Employee Information | M | W | V | V | V | V (directory attrs via CADM-04 only) |
| HRM-02 Leave Management | M | W/X | A | A | W | V |
| HRM-03 Attendance & Timesheet | M | W | V | RV | W | V |
| HRM-04 Performance Management | M | W | A | RV | W | V |
| HRM-05 Payroll Fixation | M | W | A | — | V | V |
| HRM-06 Recruitment & Onboarding | M | W/X | A | RV | — | V |
| HRM-07 Exit Management | M | W | A | RV | W | V |
| HRM-08 HR Analytics & Reports | M/E | E | E | E | V | E scoped |
| HRM-09 Training & Development | M | W | A | RV | W/V | V |

### 11.6 Communication — CAM-01…CAM-08

| Microservice | Communication Manager | Principal | HOD | Faculty | Student | Parent | College Admin (v2) |
|---|---|---|---|---|---|---|---|
| CAM-01 Announcement Groups | M | A | W | V | V | V | CFG college prefs only |
| CAM-02 Targeted Messaging | M | A | W | W | V | V | CFG college prefs only |
| CAM-03 Email/SMS Gateway | M | A | W | W | V | V | — (shared notification service) |
| CAM-04 Push Notification | M | A | W | W | V | V | — |
| CAM-05 Event Notification | M | A | W | W | V | V | CFG college prefs only |
| CAM-06 Survey & Feedback | M | A | W | W | X | X | V |
| CAM-07 Emergency Alerts | M | A | A | X | V | V | X college scope if delegated |
| CAM-08 Communication Analytics | M/E | E | E | V | V | V | E scoped |

**v2 note:** IADM-02 has no notification authority; one shared Notification Service; each CADM configures college preferences only.

---

## 12. Shared services

| Service | R01 | R02 | R03 | R04 | R28 | Business roles |
|---|---|---|---|---|---|---|
| Identity / Auth | M | M | W within institute | W college users (CADM-02) | V | V |
| RBAC / Authorization | M | M | M within IADM ceiling | M within CADM ceiling | V | — |
| Audit | M | M | V own institute | V own college | M | V* |
| Notification (shared technical) | M | M | V | CFG college prefs | V | X |
| API Gateway | M | M | CFG limited | CFG limited | V | — |
| Configuration (platform) | M | M | — / W allowed only | — | V | — |
| Reporting | M | M | M institute | M college | V | E/V |
| Analytics / BI | M | M | M institute aggregate | M college | V | E/V |
| Observability | M | M | V | V | V | — |

\* Business-role audit access is scoped and purpose-limited.

---

## 13. Student & parent access boundary

| Domain / resource | Student (R25) | Parent / Guardian (R26) |
|---|---|---|
| Own profile | V/U* | V |
| Academic structure | V | V |
| Own courses | V | V |
| Own attendance | V | V |
| Own assignments | V/W/X | V |
| Own LMS progress | V | V |
| Own examination registration | W/X | V |
| Own hall ticket | V | V |
| Own results | V | V |
| Own mark card | V | V |
| Own fees | V/X | V/X |
| Own scholarship | V/W | V |
| Announcements | V | V |
| Surveys | X | X |
| Notifications | V | V |
| Other students’ data / marks | — | — |
| Faculty / HR / admin data | — | — |
| Financial / platform reports | — | — |

\* Subject to permitted profile fields and privacy policy. Guardian access requires verified relationship and applicable consent; revocation must take effect.

---

## 14. API client (R27)

| Capability | API Client |
|---|---|
| Authentication | Required |
| Service-to-service access | Scoped |
| Read / write domain data | Scoped by grant |
| Bulk import / export | Scoped |
| Administrative configuration | — by default |
| RBAC administration | — |
| Audit logs | Scoped |
| Platform configuration | — |
| Wildcard `*` | **Forbidden** |

`adm03` API-client permission family remains **dormant / outside IADM–CADM** until a separate owner decision (IADM-D5).

---

## 15. Student Module — action-level matrix (STM-01…STM-10)

Preserved from the 2026-11-15 combined matrix; Registrar shown as **R31**. All rows remain subject to tenant / campus / assignment / consent scope.

| Service / capability | Role | Permission posture | Admin capability | Mandatory constraint |
|---|---|---|---|---|
| STM-01 Identity/Profile | Student | V own; U permitted fields | — | No self-verification or duplicate merge |
| STM-01 Identity/Profile | R31 / Admissions | C, U, RV; A verification if delegated | — | Sensitive fields and merges require explicit policy/audit |
| STM-01 Identity/Profile | Faculty / Advisor | V limited assigned advisees | — | No institution-wide student browsing by default |
| STM-01 Identity/Profile | R04 College Admin | V/U scoped administration | CFG/ADM scoped | No tenant-boundary bypass |
| STM-02 Admissions | Applicant / Student | C/U draft; X submit; V own status | — | Cannot approve own application |
| STM-02 Admissions | R31 / Admissions Staff | C/U/RV; A/R when assigned | — | Separate verification and decision when required |
| STM-02 Admissions | Department reviewer / Faculty | RV assigned applications | — | Recommendation ≠ final admission approval |
| STM-02 Admissions | Parent / Guardian | V limited status if linked and allowed | — | Relationship and consent policy applies |
| STM-03 Enrolment | Student | V own; X request/select courses | — | Eligibility, prerequisites, capacity, duplicates |
| STM-03 Enrolment | R31 / Department Staff | C/U/RV; A when delegated | — | Term/programme constraints enforced |
| STM-03 Enrolment | Faculty / Advisor | V assigned roster and status | — | Assigned course/section scope |
| STM-04 Lifecycle | Student | V own status; X permitted request | — | Cannot directly approve status transition |
| STM-04 Lifecycle | R31 / delegated approver | C/U/RV; A/R per workflow | — | Effective-dated; retain history |
| STM-04 Lifecycle | Parent / Guardian | V limited linked status | — | Consent and relationship restrictions |
| STM-05 Guardian/Relationships | Student / Guardian | V permitted relationship; C request changes | — | No self-granting of consent |
| STM-05 Guardian/Relationships | R31 / verifier | C/U/RV; A verification when delegated | — | Emergency contact ≠ guardian login |
| STM-06 Documents | Student | C upload; V own status; X re-verify request | — | Private storage; short-lived access |
| STM-06 Documents | Assigned verifier / R31 | V/RV; A/R when authorized | — | Uploader ≠ verifier |
| STM-06 Documents | External Verification Agency | RV assigned documents only | — | No broad search/export/cross-tenant |
| STM-06 Documents | Auditor | AU scoped | — | Audit read-only by default |
| STM-07 Account Provisioning | Student | V own link status; X request if enabled | — | No self-assignment of roles |
| STM-07 Account Provisioning | Provisioning / IAM operator | X link/unlink; AS approved tenant roles | ADM scoped | Use existing IAM; audit role changes |
| STM-07 Account Provisioning | R04 / R31 | Request or trigger only when delegated | AS scoped | No parallel identity authority |
| STM-08 Academic History | Student | V own verified history; X correction request | — | Official results owned by EXM |
| STM-08 Academic History | Faculty / Department Staff | V assigned; RV only if assigned | — | No unauthorized result changes |
| STM-08 Academic History | Exam Cell / Controller / Dean | RV/A/I under assigned source workflow | — | Preserve provenance |
| STM-08 Academic History | Parent / Guardian | V limited linked history if permitted | — | Consent and relationship scope |
| STM-09 Service Requests | Student | C/U draft; X submit; V own; cancel if allowed | — | Requester ≠ approver where required |
| STM-09 Service Requests | Assigned reviewer / approver | RV; A/R by request type | — | Chain and SLA per request type |
| STM-09 Service Requests | R31 / Department Staff | Process assigned request types | — | Generated docs link to STM-06 |
| STM-10 Portal/Experience | Student | V own dashboard; U preferences; X allowed self-service | — | Portal is not a source of truth |
| STM-10 Portal/Experience | Parent / Guardian | V permitted linked views | — | Downstream services re-authorize |
| STM-10 Portal/Experience | Faculty / staff | V role-scoped portal data | — | Assigned course/section/dept scope |
| STM-10 Portal/Experience | R04 / R19 | CFG/ADM portal settings; publish via CAM | — | Audience, tenant, validity window |

---

## 16. Four levels of authorization

| Level | Question |
|---|---|
| 1 — Service | Can this role access the microservice? |
| 2 — Operation | Can the role CREATE / APPROVE / PUBLISH / …? |
| 3 — Data scope | Which tenant, college, department, course, section, or resource? |
| 4 — Field | Which fields are visible or editable? |

---

## 17. Coverage map (gate before any catalog change)

Export (DEV read-only) every permission currently held by **R03**, **R04**, and **Tenant Admin**, then fill:

| Permission key | Today’s holder | Keep on R03? | Keep on R04? | If removed → new holder | IADM/CADM/ACD/… service | Owner decision |
|---|---|---|---|---|---|---|
| | | | | | | Pending |

Run order:

1. Read-only DEV export of role-template grants.
2. Classify each key: IADM / CADM / functional / shared / dormant.
3. Owner approves the map.
4. Prepare DEV-only migration proposal (separate approval).
5. Only then edit seeds / role templates.

---

## 18. Owner decision checklist for this matrix

| # | Question | Proposed default | Owner decision |
|---|---|---|---|
| D-RBAC-M01 | Adopt this v2 matrix as the Admin redesign baseline (docs only)? | **Yes** | Pending |
| D-RBAC-M02 | Confirm R01 = Platform Super Admin for matrix purposes? | **Yes** | Pending |
| D-RBAC-M03 | Confirm Tenant Admin collapsed to R03 (IADM-D7)? | **Yes** | Recorded — reconfirm for publish |
| D-RBAC-M04 | Authorize read-only DEV coverage-map export for R03/R04/Tenant Admin? | **Yes** | Pending |
| D-RBAC-M05 | When R04 loses Academic **M**, default coverage = Principal + HOD + domain admins? | **Yes, refine per key** | Pending |
| D-RBAC-M06 | Update the Word `.docx` only after coverage map approval? | **Yes** | Pending |
| D-RBAC-M07 | Dual identity store for Platform Super Admin? | Defer to IAM discussion | Pending |

---

## 19. Explicit non-goals

- No production or staging writes.
- No permission seed apply from this document.
- No security-defect remediation (Security Findings Register).
- No Kafka enablement.
- No change to R25/R26 rights except where a later coverage map proves an accidental dependency.
- No automatic rename of `adm*` keys.

---

## 20. Traceability

| Artifact | Role |
|---|---|
| `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` (2026-11-15) | Prior combined enterprise + STM matrix |
| `CampXSync_Admin_RBAC_Redesign_Draft_v1.md` | Admin redesign outline |
| IADM / CADM Architecture Design v2.0 | Service boundaries and owner decisions |
| `docs/audit/rbac-foundation-2026-11-07/*` | Prior STM RBAC foundation audit |
| `CampXSync_Security_Findings_Register_v1.md` | Separate security defects |

---

## 21. Document control

| Version | Date | Change |
|---|---|---|
| 2.0 Draft | 2026-11-20 | New enterprise matrix edition: IADM/CADM Admin sections, Tenant Admin → R03, R03/R04 ceilings, preserved STM and domain matrices with narrowing notes. |
