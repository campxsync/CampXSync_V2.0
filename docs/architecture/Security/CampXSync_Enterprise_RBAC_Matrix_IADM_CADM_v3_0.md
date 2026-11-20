# CampXSync — Enterprise RBAC Matrix (IADM / CADM Edition)

**Document:** `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md`  
**Version:** 3.0 FINAL baseline (owner-directed 2026-11-20; incorporates the coverage audit — see §22; corrections continue as implementation proceeds)  
**Date:** 2026-11-20  
**Status:** FINAL implementation baseline for **DEV only** (owner direction 2026-11-20). Production changes need separate approval. Items not yet perfect are listed in §22.5 and are corrected as we proceed.  
**Supersedes (Admin sections only):** `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` (2026-11-15) §§11–12 and Admin rows of §4; hierarchy Tenant Admin node  
**Preserves:** R01–R31 catalogue (extended with R32–R39 in v2.2), STM-01…STM-10 action matrix, ACD/LMS/EXM/FIN/HRM/CAM domain matrices (with R03/R04 ceiling notes)  
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
| R05 | Principal | Institution / college | Absorbs some work removed from R04; ACD/LMS domain-admin owner with R07 |
| R06 | Vice Principal | Institution | As prior matrix |
| R07 | Dean | Academic / institution | ACD/LMS domain-admin owner with R05 (owner decision D-RBAC-M09) |
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
| R32 | Applicant | Own application only | Pre-enrolment; self-registers through STM-02; not assigned by an admin |
| R33 | Admissions Officer | Assigned campus / admission cycle | STM-02 staff; delegate of R31 |
| R34 | Department Staff | Own department | STM-03 / STM-08 / STM-09 processing; delegate of R31 / R08 / R09 |
| R35 | Academic Records Officer | Assigned campus | STM-08 records staff; posture defined in §15 (STM-08 row) |
| R36 | Document Verifier | Assigned documents | STM-06 verifier; uploader ≠ verifier |
| R37 | External Verification Agency | Assigned documents only | External human party; assigned by R03 only; no search, export or cross-tenant access |
| R38 | Provisioning / IAM Operator | One institute (R03) or platform (R02) | STM-07 only: link/unlink accounts and assign R25/R26; never a parallel identity authority |
| R39 | Faculty Advisor | Assigned advisees | Held together with R10 Faculty plus an advisor assignment |

R32–R39 were added in v2.2 (owner decision D-RBAC-M14) so the Student Module actors are catalogue roles. “Assigned approver / reviewer / delegated approver” is **not** a role: it is a recorded delegation held by an existing role (Principal, Dean, HOD, Registrar).

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

`ADM` in this table means the permission “Administer”. It is unrelated to the retired ADM-01 / ADM-02 service group and to the legacy `adm*` permission keys.

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
PLATFORM TRACK (cross-institute)
R01 Super Admin
 └── R02 System Admin (platform technical operations)

INSTITUTE TRACK (one institute / tenant — admin ceilings)
R01 appoints ──► R03 Institution Admin (IADM operator)
                  └── R04 College Admin (CADM operator, inside R03's ceiling)

ACADEMIC AUTHORITY TRACK (reporting line — not an admin ceiling)
R05 Principal / R06 Vice Principal
 ├── R07 Dean
 └── R08 HOD
      ├── R09 Program Coordinator
      └── R10 Faculty

End users (own-data access, not part of any hierarchy): R25 Student, R26 Parent/Guardian
```

v2.1: the single chain in v2.0 implied R02 > R03 > R04 > Principal and Faculty > Student > Parent. R02 (technical) and R03 (tenant admin) are different axes, R04 is not the reporting superior of the Principal, and students/parents sit outside the staff hierarchy. Permissions are still not inherited along any track.

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
| ACD-01…10 | Academic services | R07 Dean + R05 Principal (domain admin, owner decision D-RBAC-M09); HOD / Coordinator operate in their department; **not R04 as module owner** | Faculty, Student |
| LMS-01…10 | Learning management | R07 Dean + R05 Principal (domain admin); academic operators | HOD / Coordinator / Faculty / Student |
| EXM-01…09 | Examination | R12 Controller of Examinations | Exam Cell / Valuator / HOD / Faculty / Student |
| FIN-01…10 | Finance & accounts | R15 Finance Manager | Accountant / Principal / Student / Parent |
| HRM-01…09 | Human resources | R17 HR Manager | HR Officer / Principal / HOD / Employee |
| CAM-01…08 | Communication | R19 Communication Manager | Principal / HOD / Faculty / Student / Parent |
| Shared services | Identity, RBAC, Audit, Gateway, Reporting | R01 / R02 | Scoped business roles |
| STM-01 | Student identity & profile | R31 Registrar / R33 Admissions Officer; R04 scoped (admin attributes only) | Student; authorized staff; Faculty (assigned); Guardian |
| STM-02 | Admissions | R31 / R33 Admissions Officer; delegated approver | R32 Applicant / R25 Student; dept reviewer; Faculty (review); Guardian |
| STM-03 | Enrolment | R31 / delegated enrolment approver | Student; dept staff; Faculty (roster) |
| STM-04 | Lifecycle | R31; delegated Principal/Dean/HOD | Student; assigned staff; Faculty; Guardian |
| STM-05 | Guardian & relationships | R31 / delegated verifier | Student; verified Guardian |
| STM-06 | Documents | Delegated verifier / R31 | Student; assigned staff; Auditor |
| STM-07 | Account provisioning | Existing IAM; delegated R04 or R31 | Student; provisioning service; IAM operators (R38 Provisioning / IAM Operator; R01–R03 per prior decisions) |
| STM-08 | Academic history | R35 Academic Records Officer; source approvers | Student; Faculty; Exam Cell; Dean; Guardian |
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
| IADM-08 Audit, Health & Operations | M | M | V own institute | — | V/AU/E (read-only) | Shared audit store; no second store; Auditor stays independent of audit administration |

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
| CADM-07 Audit & Compliance | V/W college | V | V | V | — | Shared audit source; W = compliance records/evidence only — the audit log itself is append-only |
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

| Microservice | Principal | HOD | Coordinator | Faculty | Student | College Admin (v2 target) | Domain admin (R07 Dean / R05 Principal) |
|---|---|---|---|---|---|---|---|
| ACD-01 Academic Structure | M | W | V | V | V | V (not M) | R07 Dean / R05 Principal |
| ACD-02 Program Management | A | M | W | V | V | V | R07 Dean / R05 Principal |
| ACD-03 Course/Curriculum | A | M | W | W | V | V | R07 Dean / R05 Principal |
| ACD-04 Subject Management | V | M | W | W | V | V | R07 Dean / R05 Principal |
| ACD-05 Faculty Allocation | A | W | W | V | — | V / AS only if delegated | R07 Dean / R05 Principal |
| ACD-06 Student Academic Profile | V | W | W | W | V | V scoped | R07 Dean / R05 Principal |
| ACD-07 Section/Batch Management | V | M | M | W | V | V | R07 Dean / R05 Principal |
| ACD-08 Attendance Management | V | RV | W | W | V | V | R07 Dean / R05 Principal |
| ACD-09 Academic Planning | A | M | W | W | V | V | R07 Dean / R05 Principal |
| ACD-10 Academic Monitoring & Analytics | V | RV | RV | V | V | V/E | R07 Dean / R05 Principal |

Scope rule: Faculty access resolves to assigned course/subject/section, not institution-wide academic administration.

**v2.2:** Domain admin = R07 Dean (institute-wide academic structure) with R05 Principal. Per-service permission letters for R07 are fixed per key in the coverage map. ACD-06 holds academic-profile fields only; STM owns identity and lifecycle; Faculty **W** on ACD-06 is limited to assigned students.

### 11.2 LMS — LMS-01…LMS-10

| Microservice | Faculty | HOD | Coordinator | Student | Parent | Principal | Domain admin (R07 Dean / R05 Principal) |
|---|---|---|---|---|---|---|---|
| LMS-01 Course Management | W | M | M | V | V | V | M |
| LMS-02 Course Content | M | RV | RV | V | V (linked student) | V | M |
| LMS-03 Subject Management | W | M | M | V | V | V | M |
| LMS-04 Lesson Planning | M | RV | RV | V | V (linked student) | V | M |
| LMS-05 Assignments | M | RV | RV | W/X | V | V | M |
| LMS-06 Virtual Classroom | M | V | V | X | V | V | M |
| LMS-07 Resources Management | W | M | M | V | V (linked student) | V | M |
| LMS-08 Progress Tracking | W | RV | RV | V | V | V | M |
| LMS-09 Certification & Badges | RV | A | A | V | V | V | M |
| LMS-10 CO-TLO-PO | W | RV | M | V | — | A | M |

**v2:** R04 is not LMS module owner (— or V only after coverage map).  
**v2.2:** Parent access to Course Content, Lesson Planning and Resources is limited to the linked student's courses. Parent keeps assignment status (LMS-05) and progress (LMS-08).

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
| EXM-08 Results Management | A | W/P | V | — | RV | V | A |
| EXM-09 Examination Reporting | M/E | E | E | V | E | V | E |

SoD: Valuator ≠ Marks Approver ≠ Result Publisher.  
**v2:** Examination timetables stay in EXM — not CADM-09.  
**v2.1:** EXM-08 now shows Exam Cell **P** (publish after Controller **A**) so the “Result Publisher” in the SoD rule is an explicit role, matching EXM-03 and EXM-06.

### 11.4 Finance — FIN-01…FIN-10

| Microservice | Finance Manager | Accountant | Principal | College Admin (v2) | HOD | Student | Parent |
|---|---|---|---|---|---|---|---|
| FIN-01 Fee Structure | M | W | A | V | V | V | V |
| FIN-02 Fee Collection | M | W/X | V | V | — | X/V | X/V |
| FIN-03 Expense Management | M | W | A | V (not W/M) | W | — | — |
| FIN-04 Tax Management | M | W | A | — | — | — | — |
| FIN-05 Scholarship Management | A | W | A | V / W only if delegated | RV | W | V |
| FIN-06 Payroll | M | W/X | A | — | — | — | — |
| FIN-07 Finance Reports | M/E | E | E | E scoped | V | V | V |
| FIN-08 Audit & Compliance | M | W | V | V | — | — | — |
| FIN-09 Budget Management | M | W | A | V | RV | — | — |
| FIN-10 Financial Analytics | M/E | E | E | E scoped | V | V | — |

**v2.2 (owner decision):** Student keeps read-only **V** on FIN-07 Finance Reports and FIN-10 Financial Analytics (Parent keeps **V** on FIN-07 only); no create/update. The v2.1 change to “—” was reversed and §13 is aligned. Scope of what is shown (published / own-scope reports) is set by ABAC, not by the role.  
**v2.2 (owner decision):** College Admin (R04) removed from Tax (FIN-04) and Payroll (FIN-06). **SoD (adopted):** the creator of an expense, scholarship award or payroll run is not its approver; fee-structure author (FIN-01) ≠ approver; reconciliation / audit (FIN-08) ≠ payment execution.

### 11.5 HRM — HRM-01…HRM-09

| Microservice | HR Manager | HR Officer | Principal | HOD | Employee/Faculty | College Admin (v2) |
|---|---|---|---|---|---|---|
| HRM-01 Employee Information | M | W | V | V | V | V (directory attrs via CADM-04 only) |
| HRM-02 Leave Management | M | W/X | A | A | W | V |
| HRM-03 Attendance & Timesheet | M | W | V | RV | W | V |
| HRM-04 Performance Management | M | W | A | RV | W | — |
| HRM-05 Payroll Fixation | M | W | A | — | V | — |
| HRM-06 Recruitment & Onboarding | M | W/X | A | RV | — | V |
| HRM-07 Exit Management | M | W | A | RV | W | V |
| HRM-08 HR Analytics & Reports | M/E | E | E | E | V | E scoped |
| HRM-09 Training & Development | M | W | A | RV | W/V | V |

**v2.2 (owner decision):** College Admin (R04) removed from Performance Management (HRM-04) and Payroll Fixation (HRM-05; treated as part of “Payroll”). Recruitment (HRM-06) and Exit (HRM-07) are unchanged.  
**SoD (adopted):** Payroll Fixation (HRM-05, HR Manager) ≠ Payroll run (FIN-06, Finance Manager); requester ≠ approver for leave and performance (a HOD's own leave goes to the Principal); recruitment reviewer ≠ approver.

### 11.6 Communication — CAM-01…CAM-08

| Microservice | Communication Manager | Principal | HOD | Faculty | Student | Parent | College Admin (v2) |
|---|---|---|---|---|---|---|---|
| CAM-01 Announcement Groups | M | A | W | V | V | V | CFG college prefs only |
| CAM-02 Targeted Messaging | M | A | W | W | V | V | CFG college prefs only |
| CAM-03 Email/SMS Gateway | M | A | W (own dept) | W (own section) | V | V | — (shared notification service) |
| CAM-04 Push Notification | M | A | W (own dept) | W (own section) | V | V | — |
| CAM-05 Event Notification | M | A | W | W | V | V | CFG college prefs only |
| CAM-06 Survey & Feedback | M | A | W | W | X | X | V |
| CAM-07 Emergency Alerts | M | A | A | X (own section) | V | V | X college scope if delegated |
| CAM-08 Communication Analytics | M/E | E | E | V | V | V | E scoped |

**v2.2 (owner decision):** HOD and Faculty write access to Email/SMS and Push reaches only their own department / section audience; wider reach needs Principal **A**. Faculty Emergency Alert **X** is limited to own section and flagged to the Communication Manager (R19).  
**v2 note:** IADM-02 has no notification authority; one shared Notification Service; each CADM configures college preferences only.

---

## 12. Shared services

| Service | R01 | R02 | R03 | R04 | R28 | Business roles |
|---|---|---|---|---|---|---|
| Identity / Auth | M | M | W within institute | W college users (CADM-02) | V | V |
| RBAC / Authorization | M | M | M within IADM ceiling | M within CADM ceiling | V | — |
| Audit | M | M | V own institute | V own college | V/AU/E (read-only) | V* |
| Notification (shared technical) | M | M | V | CFG college prefs | V | X |
| API Gateway | M | M | CFG limited | CFG limited | V | — |
| Configuration (platform) | M | M | W (allow-listed institute settings only) | — | V | — |
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
| Financial reports (FIN-07 / FIN-10) | V (read-only) | V (FIN-07 only) |
| Platform reports | — | — |

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
| STM-01 Identity/Profile | R31 / R33 Admissions Officer | C, U, RV; A verification if delegated | — | Sensitive fields and merges require explicit policy/audit |
| STM-01 Identity/Profile | R39 Faculty Advisor | V limited assigned advisees | — | No institution-wide student browsing by default |
| STM-01 Identity/Profile | R04 College Admin | V; U administrative attributes only (CADM-04) | CFG scoped | Not the student master (R31 owns it); no tenant-boundary bypass |
| STM-02 Admissions | R32 Applicant / R25 Student | C/U draft; X submit; V own status | — | Cannot approve own application |
| STM-02 Admissions | R31 / R33 Admissions Officer | C/U/RV; A/R when assigned | — | Separate verification and decision when required |
| STM-02 Admissions | Department reviewer / Faculty | RV assigned applications | — | Recommendation ≠ final admission approval |
| STM-02 Admissions | Parent / Guardian | V limited status if linked and allowed | — | Relationship and consent policy applies |
| STM-03 Enrolment | Student | V own; X request/select courses | — | Eligibility, prerequisites, capacity, duplicates |
| STM-03 Enrolment | R31 / R34 Department Staff | C/U/RV; A when delegated | — | Term/programme constraints enforced |
| STM-03 Enrolment | R39 Faculty Advisor | V assigned roster and status | — | Assigned course/section scope |
| STM-04 Lifecycle | Student | V own status; X permitted request | — | Cannot directly approve status transition |
| STM-04 Lifecycle | R31 / delegated approver | C/U/RV; A/R per workflow | — | Effective-dated; retain history |
| STM-04 Lifecycle | Parent / Guardian | V limited linked status | — | Consent and relationship restrictions |
| STM-05 Guardian/Relationships | Student / Guardian | V permitted relationship; C request changes | — | No self-granting of consent |
| STM-05 Guardian/Relationships | R31 / verifier | C/U/RV; A verification when delegated | — | Emergency contact ≠ guardian login |
| STM-06 Documents | Student | C upload; V own status; X re-verify request | — | Private storage; short-lived access |
| STM-06 Documents | R36 Document Verifier / R31 | V/RV; A/R when authorized | — | Uploader ≠ verifier |
| STM-06 Documents | R37 External Verification Agency | RV assigned documents only | — | No broad search/export/cross-tenant |
| STM-06 Documents | Auditor | AU scoped | — | Audit read-only by default |
| STM-07 Account Provisioning | Student | V own link status; X request if enabled | — | No self-assignment of roles |
| STM-07 Account Provisioning | R38 Provisioning / IAM Operator | X link/unlink; AS R25/R26 only | — | Use existing IAM; audit role changes; never R01–R04, R27, R31 |
| STM-07 Account Provisioning | R04 / R31 | Request or trigger only when delegated | — | No parallel identity authority; no direct role assignment — the request goes to the provisioning / IAM operator |
| STM-08 Academic History | Student | V own verified history; X correction request | — | Official results owned by EXM |
| STM-08 Academic History | R35 Academic Records Officer | V/C/U/RV campus academic history; A only when delegated; no direct result rewrite | — | Official results stay EXM; provenance preserved; uploader/editor ≠ final certifier where SoD applies |
| STM-08 Academic History | Faculty / R34 Department Staff | V assigned; RV only if assigned | — | No unauthorized result changes |
| STM-08 Academic History | Exam Cell / Controller / Dean | RV/A/I under assigned source workflow | — | Preserve provenance |
| STM-08 Academic History | Parent / Guardian | V limited linked history if permitted | — | Consent and relationship scope |
| STM-09 Service Requests | Student | C/U draft; X submit; V own; cancel if allowed | — | Requester ≠ approver where required |
| STM-09 Service Requests | Assigned reviewer / approver | RV; A/R by request type | — | Chain and SLA per request type |
| STM-09 Service Requests | R31 / R34 Department Staff | Process assigned request types | — | Generated docs link to STM-06 |
| STM-10 Portal/Experience | Student | V own dashboard; U preferences; X allowed self-service | — | Portal is not a source of truth |
| STM-10 Portal/Experience | Parent / Guardian | V permitted linked views | — | Downstream services re-authorize |
| STM-10 Portal/Experience | Faculty / staff | V role-scoped portal data | — | Assigned course/section/dept scope |
| STM-10 Portal/Experience | R04 / R19 | R04: CFG college portal settings; R19: publish via CAM | — | Audience, tenant, validity window |

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
6. Before any apply, run DEV negative tests: tenant isolation holds, no path to R01–R03 by escalation, no wildcard `*` remains, every current Tenant Admin holder lands on an R03 equivalent, and no user is left with zero effective roles.
7. Snapshot the current role templates first so the change can be rolled back.

---

## 18. Owner decision checklist for this matrix

| # | Question | Proposed default | Owner decision |
|---|---|---|---|
| D-RBAC-M01 | Adopt this matrix as the Admin redesign baseline? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M02 | Confirm R01 = Platform Super Admin for matrix purposes? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M03 | Confirm Tenant Admin collapsed to R03 (IADM-D7)? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M04 | Authorize read-only DEV coverage-map export for R03/R04/Tenant Admin? | **Yes** | **Done** — audit received (docs/audits/rbac-coverage/) |
| D-RBAC-M05 | When R04 loses Academic **M**, default coverage = Principal + HOD + domain admins? | **Yes, refine per key** | **Closed 2026-11-20** (domain admins = R07 Dean + R05 Principal) |
| D-RBAC-M06 | Update the Word `.docx` only after coverage map approval? | **Yes** | Coverage map accepted; `.docx` update is a later documentation task |
| D-RBAC-M07 | Dual identity store for Platform Super Admin? | Defer to IAM discussion | Deferred (revisit list §22.5) |
| D-RBAC-M08 | Accept the v2.1 corrections (§23)? | **Decided 2026-11-20:** accepted, except FIN-07 / FIN-10 Student **V** restored | Closed |
| D-RBAC-M09 | Owner of ACD / LMS “Domain admin”? | **Decided:** R07 Dean + R05 Principal; no new role | Closed |
| D-RBAC-M10 | Role-assignment authority + escalation guards (§21.1)? | **Decided:** adopted | Closed |
| D-RBAC-M11 | R02 on IADM-03 / IADM-07? | **Decided:** R02 keeps **M**; no change | Closed |
| D-RBAC-M12 | Remove R04 view of payroll / tax / performance? | **Decided:** removed from FIN-04, FIN-06, HRM-04, HRM-05; others unchanged | Closed |
| D-RBAC-M13 | Segregation-of-Duties rules for Finance and HR? | **Decided:** adopted (§21.4) | Closed |
| D-RBAC-M14 | STM actors in the catalogue? | **Decided:** added as R32–R39 (§3, §21.5) | Closed |
| D-RBAC-M15 | Scope limits on broad-write rows? | **Decided:** adopted (§21.6) | Closed |
| D-RBAC-M16 | API-client (R27) credential lifecycle? | **Decided 2026-11-20:** all nine rules adopted, with the R01 / R03 issue-versus-create clarification (§21.7) | Closed |
| D-RBAC-M17 | Legal check on children's / guardian data (§21.8)? | Owner to brief counsel; parallel workstream, **not a gate** on the coverage-map export (D-RBAC-M04) | Open action |
| D-RBAC-M18 | Legacy `adm01`–`adm09` key families: shared by IADM and CADM (separated by scope) until Phase 0 proves otherwise? | **Yes**; Phase 0 result overrides (§22.1) | **Closed 2026-11-20** |
| D-RBAC-M19 | Conservative rule: remove a key from R03/R04 only when safe; otherwise keep and log (§22.1)? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M20 | Shared-key defaults for R03/R04 (§22.3)? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M21 | Grant-rule cleanup in two phases (§22.2)? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M22 | R02 System Admin = platform-scope identity, no tenant role template yet? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M23 | First R03 appointed by R01 through a platform-admin action; two-person rule deferred to before production? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M24 | Frontend direct-superuser database access handled on a separate security-findings lane, not blocking this migration? | **Yes** | **Closed 2026-11-20** |
| D-RBAC-M25 | Java role-name checks replaced by permission checks first in the IADM/CADM services; other domain services follow later? | **Yes** | **Closed 2026-11-20** |

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

## 21. Owner-reviewed additions (v2.2)

Status per item: **ADOPTED** or **DECIDED** (owner decision 2026-11-20), **RECOMMENDED** (awaiting owner confirmation), **OPEN** (action outstanding). Adopted items are already reflected in the tables above where a cell changed.  
Rows for Access & Identity Governance (IADM-03) and College Roles & Access Admin (CADM-03) stay **provisional** until the separate owner-led IAM/RBAC discussion and the on-hold deep inventory (Part B) are done.

### 21.1 Role-assignment authority and escalation guards (D-RBAC-M10) — ADOPTED

| Grantor | May assign | May not assign |
|---|---|---|
| R01 Super Admin | R02, R03 (any institute); creates and elevates R27 clients (alone or with a second approver, §21.7) | Extra roles to self without a second approver |
| R02 System Admin | Platform technical service identities only | Any tenant business role |
| R03 Institution Admin | R04–R24, R28–R31, R33–R39 inside own institute; R25/R26 only through STM-07 (R38); R32 self-registers through STM-02 | R01, R02, R03; R27 only as in §21.7 (may issue/rotate secrets for an already-approved client; may not create, elevate or freely assign R27) |
| R04 College Admin | Operator roles inside own college and inside the CADM ceiling | R01–R07, R27, R28, R37, R38 |

Guards:

1. Nobody edits their own grants or approves their own access request.
2. A role cannot assign a role holding more permissions than the assigner holds.
3. Appointing or removing an R03, or changing R03 grants, needs R01 or a second R03's approval.
4. Destructive Institute & Tenant Lifecycle (IADM-01) actions — suspend, decommission, whole-institute export or delete — are R01 or dual-approval; R03 may only request.
5. Because Tenant Admin `*` is gone, define a time-boxed, fully audited R01 break-glass path for tenant recovery (ties to D-RBAC-M07).
6. Every assignment or removal is written to the shared audit store with assigner, assignee, scope and validity window.
7. R38 Provisioning / IAM Operator (STM-07) may assign only Student (R25) and Guardian (R26) — never R01–R04, R27 or R31.

### 21.2 R02 System Admin ceiling (D-RBAC-M11) — DECIDED: R02 keeps M

R02 keeps **M** on Access & Identity Governance (IADM-03) and Security & Compliance Governance (IADM-07). No cell changed. Compensating controls already in this document: guard 1 (no self-grant), guard 6 (every change audited) and an independent read-only Auditor (R28). Recommendation, no cell changed: R01/R02 **M** on Audit means operating the service (retention, health), never editing or deleting records.

### 21.3 R04 College Admin view access (D-RBAC-M12) — DECIDED

| Service | Before | Now |
|---|---|---|
| FIN-04 Tax Management | V | — |
| FIN-06 Payroll | V | — |
| HRM-04 Performance Management | V | — |
| HRM-05 Payroll Fixation | V | — (treated as Payroll; revert if you meant FIN-06 only) |

Unchanged (R04 keeps V): FIN-08 Audit & Compliance, FIN-09 Budget Management, HRM-06 Recruitment & Onboarding, HRM-07 Exit Management.

### 21.4 Segregation of Duties beyond Examination (D-RBAC-M13) — ADOPTED

- **Finance:** the creator of an expense, scholarship award or payroll run is not its approver; fee-structure author (FIN-01) ≠ approver; reconciliation / audit (FIN-08) ≠ payment execution.
- **HR:** Payroll Fixation (HRM-05, HR Manager) ≠ Payroll run (FIN-06, Finance Manager); requester ≠ approver for leave and performance (a HOD's own leave goes to the Principal); recruitment reviewer ≠ approver.
- **Access:** requester ≠ approver for any role change (guard 1 in §21.1).

### 21.5 Student Module actors added to the catalogue (D-RBAC-M14) — DECIDED

| New role | Replaces label | Where used | Note |
|---|---|---|---|
| R32 Applicant | Applicant | STM-02 | Own application only; self-registered |
| R33 Admissions Officer | Admissions Staff | STM-02 | Delegate of R31 |
| R34 Department Staff | Department Staff | STM-03, STM-08, STM-09 | Own department |
| R35 Academic Records Officer | Academic records staff | STM-08 | Posture defined in the §15 STM-08 row (owner decision 2026-11-20) |
| R36 Document Verifier | Assigned verifier | STM-06 | Uploader ≠ verifier |
| R37 External Verification Agency | External Verification Agency | STM-06 | External human party; machine integrations stay R27 |
| R38 Provisioning / IAM Operator | Provisioning / IAM operator | STM-07 | Assigns R25/R26 only (guard 7) |
| R39 Faculty Advisor | Faculty / Advisor | STM-01, STM-03 | Held with R10 Faculty |

Not added as roles: Assigned approver / reviewer / delegated approver (recorded delegations on existing roles) and Department reviewer (that is Faculty, R10).

### 21.6 Scope limits on broad rows (D-RBAC-M15) — ADOPTED

- **CAM-03 / CAM-04 (Email-SMS, Push):** HOD and Faculty **W** → own department / own section audience; wider reach needs Principal **A**.
- **CAM-07 Emergency Alerts:** Faculty **X** → own section only, flagged to the Communication Manager (R19).
- **LMS-02 / LMS-04 / LMS-07:** Parent **V** → linked student's courses only (Parent keeps LMS-05 and LMS-08).
- **ACD-06 vs STM-01 / STM-08:** STM owns identity and lifecycle; ACD-06 holds academic-profile fields only; Faculty **W** limited to assigned students.

### 21.7 API client (R27) credential lifecycle (D-RBAC-M16) — ADOPTED

R27 is the one role that holds standing credentials with no person behind each request, and the Tenant Admin wildcard (`*`) is gone, so lifecycle rules are mandatory. All nine rules adopted (owner decision 2026-11-20):

1. Every credential has a named human owner of record; no owner means the credential is disabled.
2. Every credential expires after at most 90 days and is renewed by its owner.
3. Secrets are rotated on schedule and immediately when the owner leaves or changes role.
4. Scopes are explicit per service and per tenant; no wildcard; no RBAC administration or platform configuration (already in §14).
5. Issuing, creating and elevating are separate powers:
   - **Create or elevate an R27 identity** (new client, new or wider scopes): R01, or R01 with a second approver.
   - **Issue or rotate secrets** for an R27 client already approved for that institute: R01 or R03 (own institute only).
   - **R03 may not assign the R27 role freely**, and may not create or elevate a client. R04 never issues, creates, elevates or rotates.
   - Guards 1 and 2 in §21.1 apply to every case.
6. Every call is audited against the client identity; rate limits apply; abnormal use triggers revocation.
7. Revocation is immediate and audited.
8. R27 is a machine identity only; an external human party uses R37.
9. The dormant `adm03` family stays unused until IADM-D5 is revisited.

### 21.8 Legal check — children's and guardian data (D-RBAC-M17) — OPEN (parallel workstream; not a gate on the coverage-map export)

Action for the owner: brief counsel. I am not a lawyer; this is a question list, not advice. Questions:

1. Does any programme enrol students under 18 (diploma, integrated, pre-university)? If not, the issue narrows to applicants who are minors at the time of application.
2. India's Digital Personal Data Protection Act 2023 and Rules 2025 require verifiable parental consent for children's data, with a narrow exemption for educational institutions (tracking / behavioural monitoring for educational activities or child safety). Does that exemption cover Guardian portal views, notifications, document verification and fee data, or only some of them?
3. Timing: commentary indicates the children's-data provisions commence in phases, with section 9 scheduled for 13 May 2027. Counsel should confirm the date and what applies before it.
4. Guardian access to adult students (18+): what consent is needed, and how is revocation honoured? (§13 already requires revocation to take effect.)
5. Persons with disabilities with a lawful guardian: the consent regime differs; confirm how guardian relationships (STM-05) must be verified.
6. R37 External Verification Agency: what contract, purpose limitation and data-sharing terms are needed.
7. Retention, erasure and breach notification: how Data Governance & Privacy (IADM-05) must support them.

---

## 22. Final decisions from the coverage audit and implementation defaults (v3.0)

Source: `docs/audits/rbac-coverage/` (index plus Parts A–M and the 571-key CSV), reviewed 2026-11-20. Owner direction: finish, implement IADM and CADM, avoid blockers; perfection is not required and corrections continue as we proceed.

### 22.1 Implementation rules (so nothing blocks progress)

1. **DEV only.** No production change without separate approval.
2. **Add before remove.** A new owner receives a key before R03 or R04 loses it.
3. **Retain when uncertain.** Remove a key from R03/R04 only when (a) the matrix names another holder and (b) no live IADM/CADM service uses it. Otherwise keep it and log it on the exceptions list (§22.5). Uncertainty is never a reason to stop.
4. **Reversible.** Every database change is a new numbered migration with a rollback script and a before/after snapshot of role and permission tables. Existing migrations (including `test_rbac.sql`) are not edited.
5. **Legacy key families.** The `adm01`–`adm09` permission families are treated as shared by IADM (institute-wide) and CADM (college), separated by scope, until Phase 0 proves otherwise. The Phase 0 result overrides this default. The audit's two views (Part B maps `adm04`–`adm09` to CADM; Part C labels `adm06`–`adm08` as IADM) conflict, so this must be settled from code in Phase 0.

### 22.2 Resolution of audit findings

| Audit finding (plain language) | Decision | Phase | Lane |
|---|---|---|---|
| Frontend server connects as database superuser (SEC-RBAC-01) | Separate findings lane; no new frontend direct-database routes meanwhile; fix needs its own approval | — | Security findings |
| Tenant Admin wildcard `*` (SEC-RBAC-02) | Collapse into R03: confirm R03 holds explicit keys, move the one DEV holder to R03, then remove wildcard rows, the Tenant Admin role and its grant rules | 1 | RBAC migration |
| College Admin may grant Principal, Vice Principal, Dean (SEC-RBAC-03) | Remove R04 → R04, R05, R06, R07 grant rows now. R03/R04 → Student and Parent grant rows removed later, once the Student Module provisioning path (STM-07) is verified to work without them | 1 and 2 | RBAC migration |
| Auditor holds write/admin keys (SEC-RBAC-04) | Strip `audit.admin`, `audit.write`, `wf.write`, `core.comment` and the five HR “write own” keys; any other write/manage key found is also stripped | 1 | RBAC migration |
| Java services check role names, not permission keys (SEC-RBAC-05) | IADM and CADM services move to permission checks in Phase 3; other domain services (academic, attendance, batch…) follow later; the temporary mismatch is accepted | 3 | RBAC migration / backlog |
| R04 holds payroll, tax, performance keys (SEC-RBAC-06) | Remove per D-RBAC-M12 | 2 | RBAC migration |
| No two-person rule to appoint an R03 (SEC-RBAC-07) | Deferred; accepted risk in DEV; required before production | later | Revisit list |
| Security SQL lives in a file named `test_rbac.sql` (SEC-RBAC-08) | Treat as current source of truth; new work goes in new numbered migrations; rename/split later | — | Revisit list |
| Applicant and Admissions Officer catalogue IDs swapped (SEC-RBAC-09) | Fix: Applicant = R32, Admissions Officer = R33 | 1 | RBAC migration |
| Ten `sys` tables without row-level security (SEC-RBAC-10) | Separate findings lane | — | Security findings |
| R34–R39 missing | Create role templates with no permissions yet; permissions attach as Student Module stories are built; R35 uses §15 posture | 1 | RBAC migration |
| R02 System Admin missing | Platform-scope identity; no tenant role template yet | — | Closed |
| R27 API Client is tables only | Keep as tables; add the 90-day expiry rule when the module is activated (or earlier if the repository already always sets an expiry) | 1 or later | RBAC migration |
| `adm03.manage` and `adm05.manage` on no role | `adm03` stays dormant (when activated: R01 + R03 hold manage). `adm05.manage` stays with R04 provisional (§9) and is granted in Phase 2 | 2 | RBAC migration |
| First R03 can only be appointed by Tenant Admin | Interim: DEV holder moved by migration. Then a platform-admin function lets R01 appoint an R03, writing an audit entry | 1 and 3 | RBAC migration |
| New owners hold few of the keys moving to them | Add-before-remove: grant R05, R07, R31, R12/R13, R15/R16, R17/R18, R19 the keys they will own | 2 | RBAC migration |
| Removal count unclear (148 vs about 50) | Phase 0 recount; the CSV (about 50 removals, about 114 keeps) is the starting point | 0 | RBAC migration |
| IADM-04 Subscription & Billing and IADM-05 Data Governance have no keys | New keys are created when those services are built; not a blocker | build | Revisit list |
| Only 5 portals recognised in the frontend; 34 roles fail closed | Unchanged for now; fail-closed is acceptable | — | Revisit list |

### 22.3 Shared-key defaults

| Key | R04 College Admin | R03 Institution Admin |
|---|---|---|
| `core.directory.view`, `cfg.view`, `wf.view`, `core.comment` | Keep | Keep |
| `rpt.view`, `rpt.view.all`, `rpt.manage`, `rpt.review`, `rpt.write` | Keep (college reporting, CADM-06) | Keep |
| `core.pii.read` | Keep (college scope, audited) | Keep |
| `core.pii.write`, `core.write` | Remove | Keep |
| `wf.write` | Remove | Keep |
| `doc.admin`, `doc.manage`, `doc.write` | n/a | Remove once R31 Registrar holds them; keep `doc.view` |
| `core.view.all` | n/a | Keep |

### 22.4 Phase plan (details in the Antigravity implementation prompt)

| Phase | What | Exit check |
|---|---|---|
| 0 | Read-only verification: key-family usage, Java-versus-row-level-security behaviour, gateway role header, recount | Report file; rules in §22.1 resolve every uncertainty |
| 1 | Database foundation: fix IDs, add R34–R39, fix grant rules, strip Auditor, collapse Tenant Admin | Verification SQL passes; rollback tested |
| 2 | Narrow R04/R03 with add-before-remove; Dean/Principal/Registrar test personas | Before/after counts; known-breakage list |
| 3 | IADM/CADM services use permission checks; gateway and frontend drop Tenant Admin; R01 appoints R03 | Service tests pass; phase report |

### 22.5 Accepted risks and revisit list

Two-person rule for appointing R03; break-glass path (superuser only today); frontend direct-superuser access; Java role-name checks in non-IADM/CADM services; `test_rbac.sql` naming; keys for IADM-04 and IADM-05; unrouted roles in the frontend; `sys` tables without row-level security; dual identity store for Super Admin (D-RBAC-M07); legal check (D-RBAC-M17); legacy roles Academic Admin and Employee to retire. Each is tracked in `docs/audits/rbac-implementation/FINDINGS_REGISTER_OPEN.md` and corrected as we proceed.

---

## 23. Document control

| Version | Date | Change |
|---|---|---|
| 2.0 Draft | 2026-11-20 | New enterprise matrix edition: IADM/CADM Admin sections, Tenant Admin → R03, R03/R04 ceilings, preserved STM and domain matrices with narrowing notes. |
| 2.1 Draft | 2026-11-20 | Review pass. **Applied:** (1) §6 hierarchy split into three tracks; (2) Auditor R28 changed from M to read-only on IADM-08 and shared Audit; (3) CADM-07 W clarified, audit log append-only; (4) platform Configuration R03 cell made unambiguous; (5) FIN-07/FIN-10 Student and Parent set to — (conflict with §13); (6) EXM-08 Exam Cell W→W/P so a Result Publisher exists; (7) STM-01 R04, STM-07 R04/R31 and STM-10 R04 rows aligned with the narrowed R04 ceiling; (8) §4 note on the ADM code; (9) §17 steps 6–7 added; (10) §18 decisions M08–M16 and §21 proposals added. |
| 2.2 Draft | 2026-11-20 | Owner decisions incorporated: FIN-07/FIN-10 Student V restored (and §13 aligned); R07 Dean + R05 Principal named ACD/LMS domain-admin owners; role-assignment grant table adopted; R02 keeps M; R04 removed from FIN-04, FIN-06, HRM-04, HRM-05; Finance/HR SoD adopted; STM actors added as R32–R39; broad-write scope limits applied to CAM and LMS cells; R27 lifecycle recommended; legal-check question list added (§21.8). |
| 2.3 Draft | 2026-11-20 | R27 credential lifecycle (§21.7) adopted in full with the clarification that R03 issues/rotates secrets for already-approved R27 clients while R27 creation/elevation stays R01 (or R01 + second approver); §21.1 grant table aligned; STM-08 row added for R35 Academic Records Officer (V/C/U/RV, A only when delegated, no direct result rewrite); D-RBAC-M16 closed; D-RBAC-M17 recorded as a parallel workstream, not a gate. |
| 3.0 FINAL baseline | 2026-11-20 | Coverage audit reviewed; §22 added (implementation rules, audit-finding resolutions, shared-key defaults, phase plan, revisit list); D-RBAC-M01–M07 closed or deferred; D-RBAC-M18–M25 added; document control renumbered to §23. |
