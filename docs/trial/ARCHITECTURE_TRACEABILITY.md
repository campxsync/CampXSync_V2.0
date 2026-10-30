# CampXSync — Architecture Traceability Matrix & Specification Register

**Document**: `docs/trial/ARCHITECTURE_TRACEABILITY.md`  
**Governance Baseline**: `docs/reference/product_lifecycle.md`  
**Target Environment**: Supabase Staging (`kpwdvvcschankgggitnj`) & Vercel Preview/Live Deployments  
**Branch**: `ReviewBranch`  

---

## 1. Executive Summary & Traceability Status Summary

The CampXSync platform architecture is defined across 8 core capability domains and 62 distinct service modules. In accordance with the governance principles established in `docs/reference/product_lifecycle.md` and the Master Execution Prompt V4, this matrix tracks **every single module** in the canonical architecture.

To guarantee that no architectural design is lost or silently omitted while preventing false claims of completion:
- Modules included in the active MVP slice provide concrete repository, database, API, and staging verification evidence.
- Modules outside the MVP vertical slice are explicitly classified as **`Specified, not in MVP slice`** with their boundary notes and future dependencies documented.

### Status Count Breakdown

| Status Category | Definition | Count | Modules |
| :--- | :--- | :---: | :--- |
| **Staging-verified** | Code complete, unit/integration tested, verified live in Supabase Staging with real browser evidence. | **10** | ACD-01, ACD-03, ACD-04, ACD-05, ACD-06, ACD-10, ADM-Platform, ADM-College, STU-01 (Core), IAM-01 (Core), AUD-01 (Core) |
| **Partial (Staging-verified)** | Core entities, schemas, and live operational endpoints implemented for slice needs; advanced features deferred. | **1** | HRM-01 |
| **Specified, not in MVP slice** | Canonical specification exists in architecture archive; intentionally deferred to subsequent product lifecycle phases. | **51** | ACD-02, ACD-07, ACD-08, ACD-09, LMS-01..10, EXM-01..09, FIN-01..10, HRM-02..09, CAM |
| **Total Architectural Modules** | Full product capability surface | **63** | 100% Accounted For |

---

## 2. Canonical Source-of-Truth Register

This register identifies the authoritative specification files from the `AllModules` design package, their canonical version, and superseded documents.

| Domain | Canonical Specification File | Version | Superseded Files / Archive Notes | Decision Reference |
| :--- | :--- | :---: | :--- | :--- |
| **ACD** | `ACDModule/ACD-01.docx` through `ACD-10.docx` | v1.0 | `ACD-10-extracted.txt` (raw text export) | Canonical academic suite baseline |
| **ADM** | `ADMModule/CampSync_Admin_Module_Microservice_Architecture_plarform.docx`<br>`ADMModule/CampSync_College_Admin_Module_Microservice_Architecture.docx` | v2.0 | Early single-tier admin drafts; unified via platform vs tenant tier split | DEC-08 |
| **LMS** | `LearningManagementSystemModule/LMS-01-v2.0.docx`<br>`LMS-02.docx` through `LMS-10.docx` | v2.0 | `LMS-01.docx` (v1.0 superseded by `LMS-01-v2.0.docx`) | DEC-09 |
| **EXM** | `ExaminationModule/EXM-01.docx` through `EXM-09.docx` | v1.0 | Associated exam flow architecture diagrams | Canonical examination baseline |
| **FIN** | `Finance&Accounts/FIN-01.docx` through `FIN-10.docx` | v1.0 | Associated financial ledger architecture diagrams | DEC-05 (Payroll reconciliation) |
| **HRM** | `HRMS/HRM-01.docx` through `HRM-09.docx` | v1.0 | Associated HRMS process diagrams | DEC-06 (HRM-06/07 recruitment split) |
| **CAM** | *(None — Archive Directory Empty)* | N/A | `Communication&AnnnouncementModule/` directory empty | DEC-07 (Documented source gap) |
| **Data/DB** | `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/`<br>`Documentation/relational_constraint_index_catalog.md` | v3.0 | Legacy MongoDB/Kafka/Redis architecture proposals in DOCX files | DEC-01 (Supabase/PostgreSQL source of truth) |

---

## 3. Detailed Architecture Traceability Matrix

### 3.1 Academic Management (ACD)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **ACD-01** | Course & Program Management | **Staging-verified** | Tables: `acd.courses`, `acd.course_sections`<br>Frontend: `/college/departments`<br>Tests: `scripts/verify_phase5_college_setup.mjs` | Owns course master and academic degree programs; scoped to tenant boundary with RLS. |
| **ACD-02** | Curriculum & Syllabus Structure | Specified, not in MVP slice | Spec: `ACDModule/ACD-02.docx` | References course and subject masters; deferred to Phase 3 of lifecycle roadmap. |
| **ACD-03** | Authoritative Subject Master | **Staging-verified** | Tables: `acd.subjects`<br>Constraints: `fk_timetable_entries_subject_id`<br>Seed: `database/seed-data/staging_seed_college_a_and_b.sql` | Authoritative subject master for scheduling and attendance per DEC-04; eliminates duplicate LMS subject master. |
| **ACD-04** | Academic Batches & Sections | **Staging-verified** | Tables: `acd.batches`, `acd.batch_students`<br>Frontend: `/college/batches`<br>Tests: `scripts/verify_phase5_college_setup.mjs` | Scopes student rosters to academic cohorts without creating shadow student profiles. |
| **ACD-05** | Timetable & Scheduling | **Staging-verified** | Tables: `acd.timetable_entries`<br>Frontend: `/college/timetable`<br>Tests: `scripts/verify_phase5_college_setup.mjs` | Validates time slot, room, faculty, and batch conflicts; provides authoritative timetable slot for attendance capture. |
| **ACD-06** | Attendance Sessions & Audited Corrections | **Staging-verified** | Tables: `acd.attendance_sessions`, `acd.attendance_records`, `acd.attendance_corrections`<br>API: `/api/college/attendance/*`<br>Frontend: `/faculty/attendance`, `/college/attendance`<br>Tests: `scripts/verify_phase6_attendance.mjs`, `scripts/verify_phase9_security_browser.mjs` | Full lifecycle implemented: slot deduplication, student roster validation, ledger modal submission, read-only lock, optimistic lock correction drawer (v3->v4), immutable audit logging. |
| **ACD-07** | Academic Terms & Calendar | Specified, not in MVP slice | Spec: `ACDModule/ACD-07.docx` | Supplies institutional working days and holidays to timetable; deferred to Phase 3. |
| **ACD-08** | Academic Learning Resources | Specified, not in MVP slice | Spec: `ACDModule/ACD-08.docx`<br>Decision: DEC-02 | Consolidated into `lms.resources` table in Supabase staging schema to prevent duplicate resource masters. |
| **ACD-09** | Assessment Component Mapping | Specified, not in MVP slice | Spec: `ACDModule/ACD-09.docx` | Maps assessment items to courses; deferred to Phase 3/4. |
| **ACD-10** | Institutional Attendance Analytics | **Staging-verified** | Views: `acd.v_attendance_summary`<br>API: `/api/college/attendance/report`<br>Frontend: `/college`, `/college/attendance`<br>Tests: `scripts/verify_phase7_dashboards_preferences.mjs` | Real-time cumulative attendance calculation, statutory 75% shortage alert trigger (DEC-14), 14-day trend analytics, injection-safe CSV export. |

---

### 3.2 Platform & Institute Administration (ADM)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **ADM-Platform** | Multi-Tenant Platform Operator | **Staging-verified** | Tables: `core.tenants`, `iam.roles`, `iam.role_templates`<br>Frontend: `/platform`, `/platform/institutes`, `/platform/institutes/new`<br>Tests: `scripts/verify_phase4_onboarding.mjs`, `scripts/verify_phase9_security_browser.mjs` | Multi-tenant tenant lifecycle management, 5-step transactional onboarding saga, aggregate-only metrics. Strictly denied read access to tenant student/faculty rows (HTTP 403). |
| **ADM-College** | Institutional College Admin | **Staging-verified** | Tables: `core.departments`, `hrm.employees`, `stu.students`<br>Frontend: `/college`, `/college/departments`, `/college/faculty`, `/college/students`<br>Tests: `scripts/verify_phase5_college_setup.mjs`, `scripts/verify_phase9_security_browser.mjs` | Intra-tenant academic governance, department and degree program hierarchy, staff and student enrollment, timetable scheduling, and audited attendance correction drawer. |

---

### 3.3 Learning Management System (LMS)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **LMS-01** | LMS Course Delivery | Specified, not in MVP slice | Canonical Spec: `LMS-01-v2.0.docx` (DEC-09) | Configures delivery courses referencing `acd.courses`; deferred to Phase 3. |
| **LMS-02** | Course Content Management | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-02.docx` | Syllabus syllabus modules and lesson assets; deferred to Phase 3. |
| **LMS-03** | Subject Delivery Projections | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-03.docx` | Reconciled with authoritative `acd.subjects` per DEC-04; acts as learning projection. |
| **LMS-04** | Lesson Planning & Syllabus Tracking | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-04.docx` | Faculty lesson schedule and completion milestones; deferred to Phase 3. |
| **LMS-05** | Assignment Management | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-05.docx` | Assignment creation, submission, and rubrics; deferred to Phase 3. |
| **LMS-06** | Virtual Classroom Integration | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-06.docx` | Video conference provider integration; deferred to Phase 3. |
| **LMS-07** | Learning Resource Library | Specified, not in MVP slice | Table: `lms.resources` in Staging DB (DEC-02) | Single consolidated resource repository in PostgreSQL; deferred to Phase 3. |
| **LMS-08** | Learner Progress Tracking | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-08.docx` | Course completion and activity event aggregation; deferred to Phase 3. |
| **LMS-09** | Digital Certifications & Badges | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-09.docx` | Verifiable credentials; deferred to Phase 3. |
| **LMS-10** | Outcome-Based Education (OBE) | Specified, not in MVP slice | Spec: `LearningManagementSystemModule/LMS-10.docx` | CO/PO/PSO attainment computation; deferred to Phase 3. |

---

### 3.4 Examination Management (EXM)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **EXM-01** | Exam Registration & Eligibility | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-01.docx` | Enforces prerequisite attendance threshold (consumes ACD-10); deferred to Phase 4. |
| **EXM-02** | Exam Scheduling & Hall Allocation | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-02.docx` | Date sheets and seating arrangements; deferred to Phase 4. |
| **EXM-03** | Secure Hall Ticket Generation | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-03.docx` | Cryptographically signed admit cards; deferred to Phase 4. |
| **EXM-04** | Script Valuation & Marks Entry | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-04.docx` | Blind evaluation and marks recording; deferred to Phase 4. |
| **EXM-05** | Marks Approval (Maker-Checker) | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-05.docx` | Dual-authorization finalization; deferred to Phase 4. |
| **EXM-06** | Transcripts & Marks Cards | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-06.docx` | Tamper-evident grade cards; deferred to Phase 4. |
| **EXM-07** | Script Barcode & Tracking | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-07.docx` | Physical answer book custody logging; deferred to Phase 4. |
| **EXM-08** | Result Processing & Publication | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-08.docx` | SGPA/CGPA calculation and gazette publication; deferred to Phase 4. |
| **EXM-09** | Examination Reporting & Analytics | Specified, not in MVP slice | Spec: `ExaminationModule/EXM-09.docx` | Pass percentage trends and institutional metrics; deferred to Phase 4. |

---

### 3.5 Finance & Accounts (FIN)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **FIN-01** | Fee Structures & Rules | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-01.docx` | Quota, program, and category fee schedules; deferred to Phase 4. |
| **FIN-02** | Fee Collection & Reconciliation | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-02.docx` | Online gateway and cash counter collection; deferred to Phase 4. |
| **FIN-03** | Expense Management | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-03.docx` | Institutional disbursements; deferred to Phase 4. |
| **FIN-04** | Tax Management & GST/TDS | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-04.docx` | Statutory tax compliance; deferred to Phase 4. |
| **FIN-05** | Fee Receipts & Ledger | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-05.docx` | Numbered, auditable student receipts; deferred to Phase 4. |
| **FIN-06** | Scholarship & Concession Admin | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-06.docx` | Merit/need-based fee waivers; deferred to Phase 4. |
| **FIN-07** | Financial Payroll Accounting | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-07.docx`<br>Decision: DEC-05 | General ledger payroll projection; HRM-05 owns primary salary calculation. |
| **FIN-08** | Financial Reporting & Balance Sheets | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-08.docx` | Dues aging, collections, and trial balance; deferred to Phase 4. |
| **FIN-09** | Financial Compliance & Audit | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-09.docx` | Fiscal controls and external audit logs; deferred to Phase 4. |
| **FIN-10** | Budgeting & Fund Allocation | Specified, not in MVP slice | Spec: `Finance&Accounts/FIN-10.docx` | Departmental budget ceilings; deferred to Phase 4. |

---

### 3.6 Human Resource Management (HRM)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **HRM-01** | Employee Information Master | **Partial (Staging-verified)** | Tables: `hrm.employees`, `core.persons`<br>Frontend: `/college/faculty`<br>Tests: `scripts/verify_phase5_college_setup.mjs`, `scripts/verify_phase9_security_browser.mjs` | Basic faculty master, department assignment, IAM user link, and soft-delete (`EXITED`) implemented for MVP slice. Full employment records deferred to Phase 5. |
| **HRM-02** | Staff Leave Management | Specified, not in MVP slice | Spec: `HRMS/HRM-02.docx` | Faculty leave applications and accruals; deferred to Phase 5. |
| **HRM-03** | Staff Attendance & Biometrics | Specified, not in MVP slice | Spec: `HRMS/HRM-03.docx` | Staff check-in and biometric capture; deferred to Phase 5. |
| **HRM-04** | Staff Performance & Appraisals | Specified, not in MVP slice | Spec: `HRMS/HRM-04.docx` | Annual appraisal and student feedback; deferred to Phase 5. |
| **HRM-05** | Compensation & Payroll Processing | Specified, not in MVP slice | Spec: `HRMS/HRM-05.docx`<br>Decision: DEC-05 | Authoritative salary structure and payslip generation; deferred to Phase 5. |
| **HRM-06** | Recruitment & Applicant Tracking | Specified, not in MVP slice | Spec: `HRMS/HRM-06.docx`<br>Decision: DEC-06 | Job requisition, interview stages, and offers; deferred to Phase 5. |
| **HRM-07** | Staff Induction & Onboarding | Specified, not in MVP slice | Spec: `HRMS/HRM-07.docx`<br>Decision: DEC-06 | Document verification and background checks; deferred to Phase 5. |
| **HRM-08** | HR Analytics & Staff Ratio | Specified, not in MVP slice | Spec: `HRMS/HRM-08.docx` | Cadre ratios and compliance metrics; deferred to Phase 5. |
| **HRM-09** | Faculty Development & Training | Specified, not in MVP slice | Spec: `HRMS/HRM-09.docx` | FDP tracking and certifications; deferred to Phase 5. |

---

### 3.7 Communication & Announcements (CAM)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **CAM-01** | Announcements & Multichannel Notifications | Specified, not in MVP slice | Directory: `Communication&AnnnouncementModule/` (Empty)<br>Decision: DEC-07 | Explicitly recorded as source package gap. Full specification and backlog created for future implementation. |

---

### 3.8 Cross-Cutting Core Extensions (Essential Platform Services)

| Service ID | Service Name | Status | Evidence / Repository References | Architecture & Boundary Note |
| :--- | :--- | :---: | :--- | :--- |
| **STU-01** | Student Master & Enrollment | **Staging-verified** | Tables: `stu.students`, `core.persons`<br>Frontend: `/college/students`<br>Tests: `scripts/verify_phase5_college_setup.mjs`, `scripts/verify_phase9_security_browser.mjs`<br>Decision: DEC-11 | Authoritative student identity, degree enrollment, and batch scoping. Solves missing spec in archive. |
| **IAM-01** | Identity, Auth & Permissions | **Staging-verified** | Tables: `iam.user_profiles`, `iam.roles`, `iam.role_assignments`, `iam.role_permissions`<br>Frontend: Edge middleware, `@supabase/ssr`<br>Tests: 36/36 tests in `scripts/verify_phase9_security_browser.mjs` | Supabase Auth password login, JWT tenant stamping, RFC 7807 problem details, direct DB fallback, user preferences sync. |
| **AUD-01** | Institutional Audit Trail | **Staging-verified** | Tables: `audit.events`, `audit.change_log`<br>Frontend: `/platform/audit`, correction drawer history | Tamper-evident ledger logging tenant onboarding, attendance corrections, and privileged actions. |
