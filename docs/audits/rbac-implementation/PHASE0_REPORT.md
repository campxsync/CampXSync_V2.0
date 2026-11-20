# Phase 0 Verification Report: RBAC Implementation Baseline

**Document:** `docs/audits/rbac-implementation/PHASE0_REPORT.md`  
**Execution Timestamp:** 2026-11-20T14:59:37+05:30  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`) [CONFIRMED: live connection via `scripts/dev/rbac_snapshot.cjs`]  
**Governing Document:** `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` (Version 3.0 FINAL baseline)  
**Authoritative Evidence:** `docs/audits/rbac-coverage/` (00_INDEX_AND_SUMMARY.md, Parts A–M, Part-B_permission-map.csv)  
**Snapshot Evidence:** `docs/audits/rbac-implementation/snapshots/before_phase1_live_snapshot.json` [CONFIRMED]  

---

## Plain-Language Legend & Abbreviations

- **R01 Super Admin**: Platform Super Administrator across all institutions / tenants.
- **R02 System Admin**: Platform technical operations, shared infrastructure, and gateway administrator.
- **R03 Institution Admin**: Institution-level administrator governing IADM services for an entire institute/tenant.
- **R04 College Admin**: College/campus operational administrator governing CADM services within a college scope.
- **R05 Principal**: Head of institution/college; academic and administrative executive; academic domain admin with R07 Dean.
- **R06 Vice Principal**: Deputy executive assisting Principal; academic executive authority.
- **R07 Dean**: Academic leader across faculty; academic & LMS domain administrator with R05 Principal.
- **R08 HOD**: Head of Department governing departmental courses, faculty, and students.
- **R10 Faculty**: Teaching staff responsible for assigned courses, sections, and advisees.
- **R12 Controller of Examinations (CoE)**: Chief authority for exam scheduling, valuation, and official result approval.
- **R13 Exam Cell Officer**: Operational examination staff executing hall tickets, mark cards, and results publication.
- **R15 Finance Manager**: Primary administrative owner for institutional fees, expenses, budgets, and payroll.
- **R16 Accountant / Finance Officer**: Operational finance staff processing collections, expenses, and invoices.
- **R17 HR Manager**: Primary administrative owner for employee master, leave policies, performance, and payroll fixation.
- **R18 HR Officer**: Operational HR staff managing leave records, attendance, and onboarding workflows.
- **R19 Communication Manager**: Primary administrative owner for announcements, messaging templates, and surveys.
- **R25 Student**: Enrolled student accessing own profile, academic progress, attendance, and fee receipts.
- **R26 Parent / Guardian**: Verified parent/guardian accessing linked dependent student's records under consent.
- **R27 API Client**: Automated machine identity accessing scoped services without interactive user session.
- **R28 Auditor**: Independent oversight role holding strictly read-only and audit-log inspection capabilities.
- **R31 Registrar**: Custodian of student records, academic lifecycle transitions, and document approvals.
- **R32 Applicant**: Prospective student submitting and tracking an admission application.
- **R33 Admissions Officer**: Staff processing admission verifications and applications under Registrar delegation.
- **R34 Department Staff**: Departmental operational staff processing student records and service requests.
- **R35 Academic Records Officer**: Campus academic history and record maintenance staff.
- **R36 Document Verifier**: Staff reviewing and verifying student uploaded verification documents.
- **R37 External Verification Agency**: External human verifier inspecting assigned student credentials.
- **R38 Provisioning / IAM Operator**: Staff executing student account linking and R25/R26 role provisioning.
- **R39 Faculty Advisor**: Faculty member assigned to mentor and advise specific student cohorts.
- **IADM (IADM-01…IADM-08)**: Institute Administration Services (Platform & Institute Tenant Operations).
- **CADM (CADM-01…CADM-09)**: College Administration Services (Campus, College Users, Rooms, Timetables).
- **RLS**: Row-Level Security in PostgreSQL ensuring tenant and data-scope isolation at the database layer.
- **SoD**: Segregation of Duties ensuring distinct actors for creation, verification, approval, and publication.
- **HMAC**: Hash-based Message Authentication Code (`X-Gateway-Signature`) ensuring gateway-to-microservice trust.

---

## 0.1 Key Families Table (`adm01` … `adm09`)

Evaluated against Java microservice endpoints, PostgreSQL RLS policies in `database/migrations/Scripts_V3.0/60_rls.sql`, and Next.js frontend routes.

| Permission Family | Plain-Language Domain | Java Endpoints / Repositories Using Key | Database RLS Policies / Tables Using Key | Frontend Screens Referencing Key | Scope Decision (IADM vs CADM vs Shared) | Evidence & Notes |
|---|---|---|---|---|---|---|
| **adm01** (`adm01.view`, `adm01.view.all`, `adm01.write`, `adm01.review`, `adm01.manage`) | Institute & Tenant Lifecycle / College Registration | `InstituteAdminController.java:1003` (`ADM01_FORBIDDEN` on `/api/v1/admin/onboard/college-tenant`) [CONFIRMED: file inspection] | `stu.application_documents` (`20261105000200_stm01_applicant_documents_rls.sql:24-70`) fallback for staff review [CONFIRMED] | None directly in UI codebase [CONFIRMED: `git grep` 0 hits in `frontend/`] | **Shared (separated by scope)**: IADM-01 owns full lifecycle (`manage`, `write`); R04 College Admin retains operational read (`view`, `view.all`) for own college profile. | Live seeds: R03 has `manage`, `write`, `review`, `view`, `view.all`. R04 has `view`, `view.all`. R28 has `view`, `view.all`. [CONFIRMED: `80_seed.sql:849`, `snapshot.json`] |
| **adm02** (`adm02.view`, `adm02.view.all`, `adm02.view.own`, `adm02.write`, `adm02.write.own`, `adm02.review`, `adm02.manage`) | Campus, Rooms & Facilities Operations | `CampusRepository.java:11` (`iam.perm_all('adm02.*')`), `CollegeAdminController.java:2929` (`ADM02_UNAUTHORIZED_APPROVER`) [CONFIRMED] | `core.campuses` (`campuses_select`, `insert`, `update`, `delete`), `core.rooms` (`rooms_select`, `insert`, `update`, `delete`) in `60_rls.sql:20-23, 414-417` [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **Shared (separated by scope)**: CADM-05 manages campus physical rooms; IADM-01 manages institutional campuses across colleges. | Both R03 and R04 hold `manage`, `write`, `review`, `view`, `view.all` in seed [CONFIRMED: `80_seed.sql:851, 1911`]. |
| **adm03** (`adm03.view`, `adm03.view.all`, `adm03.view.own`, `adm03.write`, `adm03.write.own`, `adm03.manage`) | External API Clients & Messaging Providers | `ApiClientRepository.java:12` (`iam.has_permission('adm03.*')`) [CONFIRMED] | `cam.providers`, `cam.provider_routes`, `cam.provider_health`, `iam.api_clients`, `iam.api_client_grants` in `60_rls.sql:1821-1832, 1948-1956` [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **Shared (dormant outside IADM/CADM per IADM-D5)**: When activated, R01/R03 manage clients; R04 requests college-scoped clients. | `adm03.manage` is currently on 0 roles in seed [CONFIRMED: `snapshot.json`]. Both R03 and R04 hold `view`, `view.all`, `write`. |
| **adm04** (`adm04.view`, `adm04.view.all`) | College Profile & Directory Attributes | None in Java code directly; domain attributes handled by `CollegeProfileRepository.java` [CONFIRMED] | None directly in `60_rls.sql` (attributes live on `core.colleges`) [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **CADM only (college operational view)**: College Admin maintains college attributes under CADM-01/04; R03 retains supervisory view. | Only `view` and `view.all` exist in catalog; held by R03, R04, R28 [CONFIRMED: `80_seed.sql:859, 1919`]. |
| **adm05** (`adm05.view`, `adm05.view.all`, `adm05.write`, `adm05.write.own`, `adm05.manage`) | Configuration Settings & Feature Overrides | None in Java code directly; handled by `PostgresCollegeSettingRepository.java` and `PostgresFeatureOverrideRepository.java` [CONFIRMED] | `cfg.setting_values` (`setting_values_insert`, `update`, `delete`), `cfg.feature_overrides` (`feature_overrides_insert`, `update`, `delete`) in `60_rls.sql:174-187` [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **Shared (separated by scope)**: IADM-02 sets institute-level baseline config (`adm05.write`); CADM-01 configures college feature overrides (`adm05.write` within college). | `adm05.manage` on 0 roles in seed; R03 and R04 hold `view`, `view.all`, `write`. `adm05.manage` granted to R04 provisional in Phase 2 per matrix §22.2 [CONFIRMED]. |
| **adm06** (`adm06.view`, `adm06.view.all`, `adm06.view.own`, `adm06.write`, `adm06.write.own`, `adm06.review`, `adm06.manage`) | Colleges, Accreditations & Number Sequences | None in Java controllers directly; read in `PostgresNumberSequenceRepository.java` [CONFIRMED] | `core.colleges` (`colleges_select`, `insert`, `update`, `delete`), `core.college_accreditations` (`insert`, `update`, `delete`), `core.number_sequences` in `60_rls.sql:15-28, 95-98` [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **Shared (separated by scope)**: IADM-01 registers and provisions colleges; CADM-01 updates college accreditation and operational number sequences. | R03 and R04 both hold `manage`, `write`, `review`, `view`, `view.all`. R05, R06, R07 hold `view`, `view.all` [CONFIRMED]. |
| **adm07** (`adm07.view`, `adm07.view.all`, `adm07.view.own`, `adm07.write`, `adm07.write.own`, `adm07.review`, `adm07.approve`, `adm07.manage`) | Departments & Academic Units Structure | None in Java controllers directly; accessed via `PostgresDepartmentRepository.java` [CONFIRMED] | `core.departments` (`departments_select`, `insert`, `update`, `delete`) in `60_rls.sql:30-33` [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **Shared (separated by scope)**: CADM-01/04 maintains college department setup; IADM-01 oversees tenant-wide departments. | R03 and R04 hold `manage`, `write`, `review`, `view`, `view.all`. R05/R06 hold `approve`. R08 holds `write`. R10/R11 hold `view` [CONFIRMED]. |
| **adm08** (`adm08.view`, `adm08.view.all`, `adm08.view.own`, `adm08.write`, `adm08.write.own`, `adm08.review`, `adm08.manage`) | User Accounts, Profiles & Directory Groups | `UserProfileRepository.java:59, 89, 238, 327, 402` (checks `adm08.view.all`, `adm08.write`), `UserGroupRepository.java:12` [CONFIRMED] | `iam.user_profiles` (`user_profiles_select`, `insert`, `update`, `delete`), `iam.user_groups`, `iam.user_group_members` in `60_rls.sql:125-147` [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **Shared (separated by scope)**: IADM-03 governs all institute user profiles; CADM-02/04 administers college-bound user profiles within college scope. | R03 and R04 hold `manage`, `write`, `review`, `view`, `view.all`. R05, R06, R08 hold `view`, `view.all` [CONFIRMED]. |
| **adm09** (`adm09.view`, `adm09.view.all`, `adm09.view.own`, `adm09.write`, `adm09.write.own`, `adm09.review`, `adm09.approve`, `adm09.manage`) | Roles, Permissions, Delegations & Access Reviews | `DelegationRepository.java:12` (`iam.perm_all('adm09.*')`), `PostgresDelegationRepository.java:269` (`adm09.view`) [CONFIRMED] | `iam.roles`, `iam.role_permissions`, `iam.role_assignments`, `iam.delegations`, `iam.access_reviews` in `60_rls.sql:130-160`, `40_functions.sql:185, 493` (`adm09.write`) [CONFIRMED] | None directly in UI codebase [CONFIRMED] | **Shared (separated by scope)**: IADM-03 defines tenant roles/delegations within IADM ceiling; CADM-03 assigns operator roles within college scope without exceeding ceiling. | R03 and R04 hold `manage`, `write`, `review`, `view`, `view.all`. R05/R06 hold `approve`. R07, R08, R12, R15, R17, R19 hold `write.own` [CONFIRMED]. |

---

## 0.2 Java versus RLS Analysis (`batch-management-service` & `attendance-service`)

Evaluated by static code inspection of controllers, security contexts, connection managers, and PostgreSQL RLS policies in `60_rls.sql`.

| Service | Controller Role Check in Code | Database Connection Role / Session Context | Target Tables Written | RLS Policies on Target Tables | What happens if permission key removed from R04? | Blocked where? |
|---|---|---|---|---|---|---|
| **batch-management-service** | `BatchController.java:474, 518`: `assertRole(userRole, "COLLEGE_ADMIN", "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN")` [CONFIRMED] | `UserSecurityContext.java:108`: executes `SET LOCAL ROLE authenticated` and `SELECT set_config('request.jwt.claims', ..., true)` [CONFIRMED] | `acd.batches`, `acd.batch_students` via `PostgresBatchAssignmentRepository.java:102, 264` [CONFIRMED] | `60_rls.sql:505, 510`: `batches_insert` checks `iam.perm_all('acd07.write')` or college in `perm_colleges('acd07.write')`; `batch_students_insert` checks `iam.perm_all('acd07.write')` [CONFIRMED] | Controller check checks role name `"COLLEGE_ADMIN"`, which passes. But the SQL insert/update against PostgreSQL runs as `authenticated` with JWT claims and evaluates RLS. RLS evaluates `iam.perm_all('acd07.write')` which returns `FALSE`. The insert/update is rejected by PostgreSQL. | **Blocked by RLS** (Controller permits request; DB rejects) [CONFIRMED] |
| **attendance-service** | `AttendanceController.java:641`: checks `"SUPER_ADMIN".equals(role) \|\| "ACADEMIC_ADMIN".equals(role) \|\| "COLLEGE_ADMIN".equals(role) \|\| "INSTITUTE_ADMIN".equals(role)` [CONFIRMED] | `UserSecurityContext.java`: executes `SET LOCAL ROLE authenticated` and `SELECT set_config('request.jwt.claims', ..., true)` [CONFIRMED] | `acd.attendance_sessions`, `acd.attendance_records`, `acd.attendance_corrections` via `PostgresAttendanceRepository.java:83, 278, 319, 474, 564` [CONFIRMED] | `60_rls.sql:535-580`: `attendance_sessions_insert`, `attendance_records_insert`, `attendance_corrections_insert` check `iam.perm_all('acd08.write')` or `acd08.manage` [CONFIRMED] | Controller check checks role name `"COLLEGE_ADMIN"`, which passes. But PostgreSQL RLS checks `iam.perm_all('acd08.write')` on the session context. If `acd08.write` is removed from R04, the write operation is rejected by PostgreSQL RLS. | **Blocked by RLS** (Controller permits request; DB rejects) [CONFIRMED] |
| **Asymmetric Observation** | If a domain role (e.g. R07 Dean or R05 Principal) is granted `acd07.write` or `acd08.write` in the database, but calls `batch-management-service` or `attendance-service`: | `authenticated` session context with RLS permissions present | Same tables | RLS allows the operation | Controller asserts `COLLEGE_ADMIN` or `ACADEMIC_ADMIN`. The controller rejects Dean/Principal with HTTP 403 before reaching the database. | **Blocked at Controller** [CONFIRMED] |

**Conclusion for 0.2**: The Java services currently enforce role-name guards while PostgreSQL enforces fine-grained permission keys (`acd07.write`, `acd08.write`). Removing write keys from R04 correctly disables write mutations at the RLS layer even if the controller check remains. Phase 3 aligns the controllers to inspect permission keys rather than role names.

---

## 0.3 Gateway Role Header Resolution (`api-gateway`)

Evaluated by static code inspection of `api-gateway/src/main/java/com/campx/gateway/identity/JdbcIdentityResolver.java`, `ResolvedIdentity.java`, and `ReverseProxyHandler.java`.

| Role Assignment in DB (`iam.roles.code`) | Code Evaluator in `JdbcIdentityResolver.java` | String Placed in `X-User-Role` Inbound Header | Normalized Role (`ResolvedIdentity.getNormalizedRole()`) | Part of Gateway "Administrative Tier"? | Evidence & Code Location |
|---|---|---|---|---|---|
| Platform Super Admin (`plat.platform_admins`) | `platSql` lookup (line 124) | Exact DB role code (e.g. `SUPER_ADMIN`, `PLATFORM_ADMIN`) | `platform_admin` | **Yes** (Platform Tier) | `JdbcIdentityResolver.java:124-131` [CONFIRMED] |
| `COLLEGE_ADMIN` | `isCollegeAdminRole(code)` (line 291, 377) | `COLLEGE_ADMIN` | `college_admin` | **Yes** (Administrative Tier) | `JdbcIdentityResolver.java:292, 377` [CONFIRMED] |
| `INSTITUTION_ADMIN` | `isCollegeAdminRole(code)` (line 291, 377) | `INSTITUTION_ADMIN` | `college_admin` | **Yes** (Administrative Tier) | `JdbcIdentityResolver.java:292, 377` [CONFIRMED] |
| `TENANT_ADMIN` | `isCollegeAdminRole(code)` (line 291, 378) | `TENANT_ADMIN` | `college_admin` | **Yes** (Administrative Tier - to be removed in Phase 3.2) | `JdbcIdentityResolver.java:292, 378` [CONFIRMED] |
| `PRINCIPAL` | `isCollegeAdminRole(code)` (line 291, 378) | `PRINCIPAL` | `college_admin` | **Yes** (Administrative Tier) | `JdbcIdentityResolver.java:292, 378` [CONFIRMED] |
| `ACADEMIC_ADMIN` | `isCollegeAdminRole(code)` (line 291, 379) | `ACADEMIC_ADMIN` | `college_admin` | **Yes** (Administrative Tier) | `JdbcIdentityResolver.java:292, 379` [CONFIRMED] |
| `REGISTRAR` / `R31` | `isRegistrarRole(code)` (line 298, 398) | `REGISTRAR` (or `R31`) | `registrar` | **No** (Operational Registrar; never elevated to Admin) | `JdbcIdentityResolver.java:302, 398` [CONFIRMED] |
| `FACULTY`, `HOD`, `DEAN`, `TEACHER`, `SECTION_COORDINATOR` | `isFacultyRole(code)` (line 308, 382) | Exact role code (e.g. `FACULTY`, `HOD`, `DEAN`) | `faculty` | **No** (Academic Tier) | `JdbcIdentityResolver.java:310, 386` [CONFIRMED] |
| `STUDENT` | `isStudentRole(code)` (line 316, 390) | `STUDENT` | `student` | **No** (Student Tier) | `JdbcIdentityResolver.java:318, 394` [CONFIRMED] |

**Key Findings for 0.3**:
1. `X-User-Role` receives the **exact uppercase role code** from the database (e.g. `COLLEGE_ADMIN`, `INSTITUTION_ADMIN`, `TENANT_ADMIN`, `REGISTRAR`, `FACULTY`), **NOT a catalogue ID** like `R04` or `R03` [CONFIRMED: `JdbcIdentityResolver.java:292, 302, 310, 318`].
2. The Gateway's internal "administrative tier" is defined in `isCollegeAdminRole(code)` (`COLLEGE_ADMIN`, `INSTITUTION_ADMIN`, `TENANT_ADMIN`, `PRINCIPAL`, `ACADEMIC_ADMIN`). In Phase 3.2, `TENANT_ADMIN` is removed from this tier.

---

## 0.4 Recount of R04 and R03 Keys

Evaluated by executing `scripts/dev/analyze_rbac_csv.cjs` against `docs/audits/rbac-coverage/Part-B_permission-map.csv` and comparing with live DEV snapshot `before_phase1_live_snapshot.json`.

| Role | Total Keys Currently Held in DEV | Removals Planned (CSV / Matrix Baseline) | Keys Kept (Operational View / Shared Scope / IADM Ceiling) | Keys Kept Under "Retain When Uncertain" Rule | Resulting Target Key Count |
|---|---|---|---|---|---|
| **R04 College Admin** | **180** [CONFIRMED: `analyze_rbac_csv.cjs` & `snapshot.json`] | **49** removed (34 `REMOVE M/W` domain write/manage + 9 D-RBAC-M12 payroll/tax/perf + 6 domain admin keys) [CONFIRMED] | **131** keys kept (operational scoped views across academic, attendance, course, department, facilities, reporting, and college admin preferences) [CONFIRMED] | Retain all 43 `KEEP (V/E scoped)`, 25 `KEEP (CFG college prefs)`, 16 `KEEP (V directory only)`, 15 `KEEP (college scope)`, 5 `KEEP (compliance records)`, 4 `KEEP (scoped)`, 3 `KEEP (provisional)`, 2 `KEEP (admin attrs)` [CONFIRMED] | **131** |
| **R03 Institution Admin** | **184** [CONFIRMED: `analyze_rbac_csv.cjs` & `snapshot.json`] | **145** removed from domain services (ACD, LMS, EXM, FIN, HRM operational keys) [CONFIRMED: CSV line actions] | **39** keys kept: all `adm01`…`adm09` (27 keys), shared defaults (`core.directory.view`, `cfg.view`, `wf.view`, `core.comment`, `rpt.*`, `core.pii.*`, `core.write`, `wf.write`, `core.view.all` - 11 keys), and `doc.view` (1 key) [CONFIRMED: Matrix §22.3] | `doc.admin`, `doc.manage`, `doc.write` retained temporarily until Phase 2 confirms R31 Registrar holds them; `adm03.*` retained dormant. | **39** (after doc write migration to R31) |

### D-RBAC-M12 Owner Decision Keys Held by R04 (Confirmed 9 Keys to Remove):
1. `fin04.view` (Tax Management: view)
2. `fin04.view.all` (Tax Management: view all)
3. `fin06.view` (Payroll: view)
4. `fin06.view.all` (Payroll: view all)
5. `hrm04.view` (Performance Management: view)
6. `hrm04.view.all` (Performance Management: view all)
7. `hrm04.write.own` (Performance Management: write own)
8. `hrm05.view` (Payroll Fixation: view)
9. `hrm05.view.all` (Payroll Fixation: view all)
[CONFIRMED: `scripts/dev/analyze_rbac_csv.cjs` line 44; live in DEV seeds]

### Auditor R28 Keys to Strip (Confirmed 9 Write/Admin Keys):
1. `audit.admin`
2. `audit.write`
3. `core.comment`
4. `hrm02.write.own`
5. `hrm03.write.own`
6. `hrm04.write.own`
7. `hrm07.write.own`
8. `hrm09.write.own`
9. `wf.write`
[CONFIRMED: `scripts/dev/analyze_rbac_csv.cjs` line 52; verified on DEV live database in `before_phase1_live_snapshot.json`]

---

## 0.5 Status of IADM-04 (Billing) & IADM-05 (Data Governance)

| Service | Existing Code in Repository | Existing Permission Keys Used Today | Permission Keys Needed in Target Architecture | Action Required in Phases 1–3 |
|---|---|---|---|---|
| **IADM-04 Subscription, Billing & Entitlements** | Commercial billing handlers in `InstituteAdminController.java:551-598, 1908-1980` (`/api/v1/admin/billing/plans`, `/subscriptions`, `/invoices`, `/transactions`) and repositories (`PostgresSubscriptionPlanRepository`, `PostgresSubscriptionRepository`, `PostgresPlanEntitlementRepository`). [CONFIRMED] | **None**. Current handlers execute without checking any permission key; repositories query `plat.subscription_plans` and `plat.subscriptions`. [CONFIRMED] | `iadm04.view`, `iadm04.write`, `iadm04.manage` (or `sub.view`, `sub.write`, `sub.manage`, `bill.view`, `bill.manage`). | None. Per Matrix §22.2 finding (line 695): "New keys are created when those services are built; not a blocker." |
| **IADM-05 Data Governance & Privacy** | None. Only student admissions privacy consent flag in STM-02 (`ApplicationEntity.java`) and `StudentProjectionResponse.java` egress sanitization. [CONFIRMED] | **None**. [CONFIRMED] | `iadm05.view`, `iadm05.write`, `iadm05.execute`, `iadm05.manage` (for DSAR requests, personal data erasure, compliance exports). | None. Tracked in open findings register; do not create orphan keys without service implementations. |

---

## What This Means for Phases 1–3

Phase 0 confirms that the live DEV database matches the audit findings: catalogue IDs for Applicant and Admissions Officer are swapped (`R32`/`R33`), the Tenant Admin wildcard `*` exists in exactly 49 tenant role rows and 1 template row, and Auditor holds exactly 9 write/manage permissions. Removing domain write/manage permissions from R04 College Admin in Phase 2 will immediately stop write mutations at the PostgreSQL RLS boundary, while preserving operational visibility across college desks. 

In Phase 1, we fix the swapped catalogue IDs, create clean templates for R34–R39, clean the grant rules, strip Auditor's 9 write keys, and safely collapse the single DEV Tenant Admin holder into Institution Admin (R03). In Phase 2, we execute add-before-remove for R05, R07, R31, R12, R15, R17, R19 before narrowing R04 and R03. In Phase 3, we align the controller checks in `institute-admin-service` and `college-admin-service` from role strings to fine-grained permission keys, remove `TENANT_ADMIN` references from Gateway and Frontend, and install the `plat.assign_institution_admin` platform function.
