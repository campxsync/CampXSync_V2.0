# Part A: Enterprise Role Reconciliation (R01–R39 vs Live DB)

**Document:** `docs/audits/rbac-coverage/Part-A_roles.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Master Role Reconciliation Table (R01–R39 vs Database State)

**[CONFIRMED: SQL query against `iam.role_templates`, `iam.roles`, `iam.role_assignments`, and `plat.platform_admins` on DEV `azulbaxszsqxymqmlwfe`]:**

| Matrix Code | Canonical Role Name | Scope Level | Live DB Code | Live DB Scope Note | Active Flag | DEV Holder Count | Status Tag | Notes & Discrepancies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **R01** | Super Admin | Platform / Cross-Institute | `SUPER_ADMIN` | Platform Admin (`plat.platform_admins`) | `ACTIVE` | 1 | **MATCH** | Stored in `plat.platform_admins` table outside tenant IAM. |
| **R02** | System Admin | Platform Technical | `NOT FOUND` | `NOT FOUND` | — | 0 | **MISSING** | Missing from `iam.role_templates` and `iam.roles`. Technical operations use service accounts / `service_role`. |
| **R03** | Institution Admin | Institution (Tenant) | `INSTITUTION_ADMIN` | Institution (assign institute-wide) | `ACTIVE` | 0 | **MATCH** | Exists in all 49 tenants; currently has 0 assigned holders. |
| **R04** | College Admin | College / Campus | `COLLEGE_ADMIN` | College / Campus | `ACTIVE` | 3 | **MATCH** | Primary operational admin across DEV tenants. |
| **R05** | Principal | Institution / College | `PRINCIPAL` | Institution | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R06** | Vice Principal | Institution | `VICE_PRINCIPAL` | Institution (ASSUMED = Principal) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R07** | Dean | Academic / Institution | `DEAN` | Academic/Institution (ASSUMED = Principal on academic) | `ACTIVE` | 0 | **MATCH** | Domain-admin owner with Principal in Matrix v2.3. |
| **R08** | Head of Department (HOD) | Department | `HOD` | Department (assign with department_id) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R09** | Program Coordinator | Program / Course | `PROGRAM_COORDINATOR` | Program/Course | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R10** | Faculty | Assigned Subjects / Sections | `FACULTY` | Assigned subjects/courses/sections | `ACTIVE` | 2 | **MATCH** | Active test personas in DEV. |
| **R11** | Class / Section Coordinator | Assigned Section | `SECTION_COORDINATOR` | Assigned section (ASSUMED = Faculty) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R12** | Controller of Examinations | Examination | `EXAM_CONTROLLER` | Examination | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R13** | Exam Cell Officer | Examination | `EXAM_CELL` | Examination | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R14** | Valuator / Examiner | Assigned Evaluation | `VALUATOR` | Assigned evaluation | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R15** | Finance Manager | Finance | `FINANCE_MANAGER` | Finance | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R16** | Accountant / Finance Officer | Finance | `ACCOUNTANT` | Finance | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R17** | HR Manager | HR | `HR_MANAGER` | HR | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R18** | HR Officer | HR | `HR_OFFICER` | HR | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R19** | Communication Manager | Communication | `COMM_MANAGER` | Communication | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R20** | Placement Officer | Placement | `PLACEMENT_OFFICER` | Placement (base access only) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R21** | Librarian | Library | `LIBRARIAN` | Library (base access only) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R22** | Hostel Warden | Hostel | `HOSTEL_WARDEN` | Hostel (base access only) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R23** | Transport Manager | Transport | `TRANSPORT_MANAGER` | Transport (base access only) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R24** | Facility Manager | Facilities | `FACILITY_MANAGER` | Facilities (base access only) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R25** | Student | Own Academic / Financial Data | `STUDENT` | Own academic/financial data | `ACTIVE` | 2 | **MATCH** | Active test personas in DEV. |
| **R26** | Parent / Guardian | Linked Student | `PARENT` | Linked student(s) | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R27** | API Client | Machine Integration | `NOT FOUND` | Table `iam.api_clients` (0 rows) | — | 0 | **MISSING** | Missing as an `iam.role_templates` entry. Implemented as dedicated schema `iam.api_clients` / `iam.api_client_grants` (currently 0 live records). |
| **R28** | Auditor | Read / Audit | `AUDITOR` | Read/Audit | `ACTIVE` | 0 | **MATCH** | Holds `audit.admin` and `audit.write` in DB (conflicts with Matrix v2.3 read-only mandate). |
| **R29** | Reporting / Analytics User | Reports | `REPORT_USER` | Reports | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R30** | Guest | Restricted Read | `GUEST` | Restricted read | `ACTIVE` | 0 | **MATCH** | Present in all 49 tenants; 0 assigned holders. |
| **R31** | Registrar | Assigned Campus | `REGISTRAR` | STM principal for enrolment, lifecycle, docs | `ACTIVE` | 1 | **MATCH** | Seeded with 14 permissions; has 1 active holder in `DEV_TEST_TENANT`. |
| **R32** | Applicant | Pre-Enrolment Application | `APPLICANT` | Pre-enrolment applicant identity | `ACTIVE` | 0 | **RENAMED / ID SWAP** | In Matrix v2.3, Applicant is **R32**. In `iam.role_templates.catalogue_id`, it is recorded as **R33**. |
| **R33** | Admissions Officer | Admissions Desk / Cycle | `ADMISSIONS_OFFICER` | STM-02 admissions operations | `ACTIVE` | 0 | **RENAMED / ID SWAP** | In Matrix v2.3, Admissions Officer is **R33**. In `iam.role_templates.catalogue_id`, it is recorded as **R32**. |
| **R34** | Department Staff | Department Processing | `NOT FOUND` | `NOT FOUND` | — | 0 | **MISSING** | Missing from `iam.role_templates` and `iam.roles`. Delegated to HOD/Coordinator currently. |
| **R35** | Academic Records Officer | Academic History Desk | `NOT FOUND` | `NOT FOUND` | — | 0 | **MISSING** | Missing from `iam.role_templates` and `iam.roles`. |
| **R36** | Document Verifier | Document Verification Desk | `NOT FOUND` | `NOT FOUND` | — | 0 | **MISSING** | Missing from `iam.role_templates` and `iam.roles`. Implemented via Registrar / ad-hoc staff delegation. |
| **R37** | External Verification Agency | External Document Verifier | `NOT FOUND` | `NOT FOUND` | — | 0 | **MISSING** | Missing from `iam.role_templates` and `iam.roles`. |
| **R38** | Provisioning / IAM Operator | STM-07 Account Linking | `NOT FOUND` | `NOT FOUND` | — | 0 | **MISSING** | Missing from `iam.role_templates` and `iam.roles`. Java code checks `R01/R02/R03` or delegation `STM-07.ACCOUNT.A`. |
| **R39** | Faculty Advisor | Advisee Oversight | `NOT FOUND` | `NOT FOUND` | — | 0 | **MISSING** | Missing from `iam.role_templates` and `iam.roles`. Operates under `FACULTY` + advising assignment. |

---

## 2. Extra Roles Found in Live Database

**[CONFIRMED: Database inspection of `iam.role_templates`]:**

| Live DB Code | Role Name | Live `catalogue_id` | Holder Count | Reason Present in Live DB | Target Treatment per Matrix v2.3 |
| :--- | :--- | :--- | :--- | :--- | :--- |
| `TENANT_ADMIN` | Tenant Administrator | `hierarchy` | 1 | Bootstrap superuser created during initial Wave 0 seeds. Holds wildcard `*`. | **COLLAPSE TO R03** (Owner decision IADM-D7; remove `*`). |
| `ACADEMIC_ADMIN` | Academic Admin | `EXTRA` | 0 | Legacy role from earlier ACD/LMS drafts holding 122 permissions. | **RETIRE** (Ownership moved to R07 Dean + R05 Principal per D-RBAC-M09). |
| `EMPLOYEE` | Employee (Self-Service) | `EXTRA` | 0 | HRM self-service role holding 16 permissions. | **RETIRE / RENAME** to align with canonical staff base access. |

---

## 3. Explicit Report on R27 and R32–R39

### R27 API Client
- **Status:** **MISSING as a role template**; implemented as dedicated relational tables `iam.api_clients` and `iam.api_client_grants`.
- **Database Presence:** Tables exist with foreign keys to `auth.users` and `iam.user_profiles`. Live row count in DEV is **0**.
- **Governance Gap:** `iam.api_clients` has an `owner_user_id` column and `expires_at` column, satisfying Matrix §21.7 rules 1 and 2 in schema, but there are zero active clients and the `adm03` permission family is dormant.

### R32 Applicant & R33 Admissions Officer
- **Status:** **SWAPPED CATALOGUE IDs**.
  - In `iam.role_templates`:
    - `ADMISSIONS_OFFICER` has `catalogue_id = 'R32'` (Matrix says R33).
    - `APPLICANT` has `catalogue_id = 'R33'` (Matrix says R32).
- **Grant Rule Gap:** Both roles have **zero rows** in `iam.role_grant_rules`. No administrator can assign `ADMISSIONS_OFFICER` through standard non-privileged APIs.

### R34–R39 (Student Module Extension Roles)
- **Status:** **ALL 6 ROLES MISSING FROM LIVE DATABASE**.
- **Current Operational Reality:**
  - `R34 Department Staff`: Does not exist. Department staff currently use `FACULTY` or `HOD`.
  - `R35 Academic Records Officer`: Does not exist. Records operations are executed by `REGISTRAR` (R31).
  - `R36 Document Verifier`: Does not exist. Verified by `REGISTRAR` or delegated staff.
  - `R37 External Verification Agency`: Does not exist. No external portal credentials exist.
  - `R38 Provisioning / IAM Operator`: Does not exist. The Java service `StudentRoleAssignmentService.java` checks whether the caller is `R01`, `R02`, `R03`, or holds the explicit delegation string `STM-07.ACCOUNT.A`.
  - `R39 Faculty Advisor`: Does not exist. Faculty advising is handled via ABAC advisee assignment records linked to `FACULTY` (R10).
