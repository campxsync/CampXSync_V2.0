# CampXSync — Complete Feature & Module Inventory

**Document Identifier**: `docs/audit/CAMPXSYNC_FEATURE_INVENTORY.md`  
**Audit Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Governing Baseline**: Git HEAD (`ReviewBranch` commit `b307149`)  
**Cross-Reference Evidence Index**: [CAMPXSYNC_EVIDENCE_INDEX.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md)  
**Machine-Readable Companion**: [CAMPXSYNC_FEATURE_INVENTORY.csv](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_FEATURE_INVENTORY.csv)

---

## 1. Inventory Summary & Counting Methodology

### 1.1 Counting Methodology
- **Granularity**: The inventory evaluates all **63 canonical modules** defined across the 8 functional domains of the CampXSync Enterprise Architecture (`ACD`, `ADM`, `STU`, `IAM`, `AUD`, `HRM`, `LMS`, `EXM`, `FIN`, `CAM`), plus sub-features within active modules.
- **Classification Rules**:
  - `IMPLEMENTED`: Functionality exists in working code, handles real requests, persists state to database, and is verified in automated/browser tests.
  - `PARTIAL`: Core data entities and basic operational flows are implemented, but significant canonical capabilities are deferred or in-memory only.
  - `PLANNED`: Documented in specifications, user stories, or database DDL schemas, but lacking functional backend routes or frontend UI implementation in the active cloud deployment.
  - `ABSENT / SOURCE NOT FOUND`: Missing from design specifications, repository source, and database schemas.
- **Test / Verification Verification Rule**: A feature is marked `TEST VERIFIED` only if an automated test script, Playwright spec, or JUnit suite exists and has recorded pass logs. Mere existence of a test file with skipped tests does not count as verified.

### 1.2 Consolidated Module & Feature Counts

```
Total Modules Accounted For: 63
├─ Implemented & Staging-Verified: 10 (15.9%)
│  ├─ ACD-01 (Course Management)
│  ├─ ACD-03 (Subject Master)
│  ├─ ACD-04 (Academic Batches & Sections)
│  ├─ ACD-05 (Timetable Scheduling)
│  ├─ ACD-06 (Attendance & Audited Corrections)
│  ├─ ACD-10 (Attendance Analytics & Shortage Reporting)
│  ├─ ADM-Platform (Platform Operator Governance)
│  ├─ ADM-College (College Administrator Operations)
│  ├─ STU-01 (Student Master & Roster Enrollment)
│  ├─ IAM-01 (Identity, Auth, RBAC & Preferences)
│  └─ AUD-01 (Institutional Audit Trail & Outbox)
├─ Partially Implemented: 1 (1.6%)
│  └─ HRM-01 (Employee Info Master / Core Faculty)
├─ Planned (Specified in Docs/DB, Deferred from Cloud MVP): 51 (81.0%)
│  ├─ Academic Tier: ACD-02, ACD-07, ACD-08, ACD-09 (4 modules)
│  ├─ LMS Tier: LMS-01 through LMS-10 (10 modules)
│  ├─ Exam Tier: EXM-01 through EXM-09 (9 modules)
│  ├─ Finance Tier: FIN-01 through FIN-10 (10 modules)
│  └─ HRMS Tier: HRM-02 through HRM-09 (8 modules)
└─ Absent / Source Gap: 1 (1.6%)
   └─ CAM-01 (Announcements & Multichannel Broadcasts - Archive empty)
```

---

## 2. Granular Feature Inventory Matrix

| Module & Feature ID | Feature Name & Scope | Intended Users | Implementation Status | Source Evidence (Path & Symbols) | Validation Evidence (Test Suite & Results) | Deployment Evidence (URL / Target) | Known Limitations & Unresolved Issues |
| :--- | :--- | :--- | :---: | :--- | :--- | :--- | :--- |
| **ACD01-FEAT-01** | Academic Course Master & Degree Setup | College Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/college/departments/departments-manager.tsx`<br>`acd.courses`, `acd.course_sections` | `scripts/verify_phase5_college_setup.mjs` (31/31 PASS) | Vercel Alias (`/college/departments`) | Syllabus versioning PDF document uploads not implemented in cloud MVP. |
| **ACD02-FEAT-01** | Curriculum DAG & Prerequisite Engine | College Admin, Academic Dean | **PLANNED** | `services/curriculum-service/src/main/java/.../CurriculumDomainService.java`<br>`ACDModule/ACD-02.docx` | `CurriculumDomainServiceTest` (21/21 PASS offline in-memory) | None (Java service not running on Vercel) | 100% ephemeral in-memory storage; no PostgreSQL schema binding; not deployed to Vercel. |
| **ACD03-FEAT-01** | Authoritative Subject Catalog | College Admin, Faculty | **IMPLEMENTED** | `database/seed-data/staging_seed_college_a_and_b.sql`<br>Table: `acd.subjects`<br>Constraint: `(tenant_id, id)` | `scripts/verify_phase5_college_setup.mjs` | Supabase Staging (`kpwdvvcschankgggitnj`) | Managed via database seeding and batch linkage; dedicated subject CRUD view omitted from MVP. |
| **ACD04-FEAT-01** | Cohort Batches & Section Roster Allocation | College Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/college/batches/page.tsx`<br>`frontend/src/app/api/college/students/assign-batch/route.ts`<br>`acd.batches`, `acd.batch_students` | `scripts/verify_phase5_college_setup.mjs` (Test 18-22) | Vercel Alias (`/college/batches`) | Cohort split/merge workflow specified in Java backend not exposed in Next.js UI. |
| **ACD05-FEAT-01** | Conflict-Free Weekly Timetable Grid | College Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/college/timetable/timetable-grid.tsx`<br>`frontend/src/app/api/college/timetable/entries/route.ts`<br>`acd.timetable_entries` | `scripts/verify_phase5_college_setup.mjs` (Test 25-28) | Vercel Alias (`/college/timetable`) | Recurrent holiday exclusion depends on ACD-07 calendar service which is currently deferred. |
| **ACD06-FEAT-01** | Faculty Attendance Roster Capture & Modal Lock | Faculty Member | **IMPLEMENTED** | `frontend/src/app/(dashboard)/faculty/attendance/attendance-sheet.tsx`<br>`frontend/src/app/api/college/attendance/sessions/route.ts`<br>`acd.attendance_sessions`, `acd.attendance_records` | `scripts/verify_phase6_attendance.mjs` (37/37 PASS)<br>`verify_phase9_security_browser.mjs` (Req 10, 11) | Vercel Alias (`/faculty/attendance`) | Timetable slot deduplication enforced; submitted sessions are permanently read-only locked. |
| **ACD06-FEAT-02** | Audited Attendance Corrections Drawer | College Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/college/attendance/attendance-manager.tsx`<br>`frontend/src/app/api/college/attendance/corrections/route.ts`<br>`acd.attendance_corrections`, `audit.change_log` | `scripts/verify_phase6_attendance.mjs`<br>`verify_phase9_security_browser.mjs` (Req 8, 9, 11) | Vercel Alias (`/college/attendance`) | Requires College Admin privileges, mandatory justification (>= 5 chars), and optimistic concurrency lock (`row_version`). |
| **ACD07-FEAT-01** | Academic Terms, Holidays & Calendar | College Admin | **PLANNED** | `database/migrations/`<br>Table: `acd.academic_calendars`, `acd.calendar_events`<br>`ACDModule/ACD-07.docx` | Standalone Java service tests | None | Staging tables exist; frontend UI and timetable schedule integration deferred to Phase 3. |
| **ACD08-FEAT-01** | Learning Resource Digital Repository | Faculty, Student | **PLANNED** | Staging Table: `lms.resources`<br>`docs/architecture/ACD-08_LEARNING_RESOURCES_SPECIFICATION.md` | Schema introspection verified | None | Consolidated into `lms.resources` under DEC-02; blob storage integration (Supabase Storage) deferred. |
| **ACD09-FEAT-01** | Outcome-Based Education (OBE) Assessment Mapping | Academic Dean, Faculty | **PLANNED** | Staging Schemas: `obe.course_outcomes`, `obe.program_outcomes`<br>`ACDModule/ACD-09.docx` | Schema introspection verified | None | Bloom's taxonomy calculation engine exists only in Java prototype; cloud MVP UI deferred. |
| **ACD10-FEAT-01** | Institutional Shortage Analytics & CSV Export | College Admin | **IMPLEMENTED** | `frontend/src/app/api/college/attendance/report/route.ts`<br>View: `acd.v_attendance_summary`<br>`frontend/src/app/(dashboard)/college/attendance/attendance-manager.tsx` | `scripts/verify_phase7_dashboards_preferences.mjs` (26/26 PASS)<br>`verify_phase9_security_browser.mjs` (Req 14) | Vercel Alias (`/college`, `/college/attendance`) | Statutory 75% threshold enforced (DEC-14). CSV export formula-sanitized. |
| **ADM-PLAT-01** | Multi-Tenant Platform Operator Dashboard | Platform Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/platform/page.tsx`<br>`frontend/src/app/(dashboard)/platform/institutes/page.tsx`<br>`core.tenants`, `plat.platform_admins` | `scripts/verify_phase4_onboarding.mjs` (19/19 PASS)<br>`verify_phase9_security_browser.mjs` (Req 6, 7) | Vercel Alias (`/platform`, `/platform/institutes`) | Platform Admin strictly denied read access to raw tenant operational rows (HTTP 403). |
| **ADM-PLAT-02** | 5-Step Institute Onboarding Wizard | Platform Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/platform/institutes/new/onboarding-wizard.tsx`<br>`frontend/src/app/api/platform/onboard-tenant/route.ts` | `scripts/verify_phase4_onboarding.mjs`<br>Walkthrough: `phase4_onboarding_walkthrough_1792136712022.webp` | Vercel Alias (`/platform/institutes/new`) | In trial cloud deployment, executes in Dry-Run simulation mode unless header `x-provision-approval: GO PROVISION-TENANT` is supplied. |
| **ADM-COLL-01** | Institutional College Operations Hub | College Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/college/page.tsx`<br>`core.departments`, `hrm.employees`, `stu.students` | `scripts/verify_phase5_college_setup.mjs`<br>`verify_phase9_security_browser.mjs` (Req 12, 13) | Vercel Alias (`/college`) | Reconciles live PostgreSQL metrics (students, faculty, attendance %, 14-day Recharts trend). |
| **STU-FEAT-01** | Student Directory & Academic Profile Master | College Admin | **IMPLEMENTED** | `frontend/src/app/(dashboard)/college/students/student-roster.tsx`<br>`frontend/src/app/api/college/students/route.ts`<br>`core.persons`, `stu.students` | `scripts/verify_phase5_college_setup.mjs`<br>`verify_phase9_security_browser.mjs` (Req 8) | Vercel Alias (`/college/students`) | Solves missing archive specification per DEC-11. Supports enrollment, batch binding, and soft-delete (`WITHDRAWN`). |
| **STU-FEAT-02** | Student Self-Service Attendance View | Student | **PARTIAL** | `frontend/src/app/(dashboard)/student/page.tsx` | Visual inspection | Vercel Alias (`/student`) | Read-only demo screen showing sample metrics and shortage alert badges; interactive student features deferred. |
| **IAM-FEAT-01** | Supabase Auth Password Login & Session Handling | All Roles | **IMPLEMENTED** | `frontend/src/app/(auth)/login/page.tsx`<br>`frontend/src/middleware.ts`<br>`@supabase/ssr` cookies | `scripts/verify_login_hardening.mjs` (58/58 PASS)<br>`verify_independent_accounts.mjs` (7/7 PASS) | Vercel Alias (`/login`) | Supports independent credentials for College Admin, Platform Admin, and Faculty accounts. |
| **IAM-FEAT-02** | Authoritative Server Role Resolution | Platform Admin, College Admin, Faculty | **IMPLEMENTED** | `frontend/src/lib/auth/resolve-role.ts`<br>`plat.platform_admins`, `iam.user_profiles`, `core.tenants`, `iam.role_assignments` | `scripts/verify_login_hardening.mjs`<br>`verify_faculty_auth_and_routing.mjs` | Vercel Alias (Server-side) | Queries PostgreSQL directly via `@/lib/db`. Fails closed on inactive, missing profile, or suspended tenant. |
| **IAM-FEAT-03** | User Workspace Preferences Synchronization | All Authenticated Users | **IMPLEMENTED** | `frontend/src/app/api/user/preferences/route.ts`<br>Table: `iam.user_profiles.preferences` | `scripts/verify_phase7_dashboards_preferences.mjs`<br>`verify_phase9_security_browser.mjs` (Req 18) | Vercel Alias (`/api/user/preferences`) | Validates JSON schema v1 allowlist; persists typed preferences to PostgreSQL. |
| **AUD-FEAT-01** | Immutable Institutional Change Log & Security Events | Platform Admin, College Admin | **IMPLEMENTED** | Table: `audit.events`, `audit.change_log`<br>`frontend/src/app/(dashboard)/college/audit/page.tsx`<br>`frontend/src/app/(dashboard)/platform/audit/page.tsx` | `scripts/verify_phase9_security_browser.mjs` (Req 9, 11)<br>`docs/staging/05-execution-log.md` | Vercel Alias (`/college/audit`, `/platform/audit`) | Monthly partition tables hardened with RLS. Records actor, timestamp, version, and reason. |
| **HRM01-FEAT-01** | Faculty Roster & Department Appointment | College Admin | **PARTIAL** | `frontend/src/app/(dashboard)/college/faculty/faculty-roster.tsx`<br>`frontend/src/app/api/college/faculty/route.ts`<br>`hrm.employees`, `core.persons` | `scripts/verify_phase5_college_setup.mjs`<br>`verify_phase9_security_browser.mjs` (Req 8) | Vercel Alias (`/college/faculty`) | Core employment records active with soft-delete (`EXITED`). Full HRMS contracts, leave, and payroll deferred. |
| **HRM02..09** | Advanced HRMS Suite (Leave, Biometrics, Payroll) | Faculty, HR Admin | **PLANNED** | Staging Schemas: `hrm.attendance_punches`, `pay.*`<br>`HRMS/HRM-02.docx` through `HRM-09.docx` | Database catalog verified | None | Schemas present in Supabase Staging DB; code implementation deferred to Phase 5 roadmap. |
| **LMS01..10** | Learning Management Suite (OBE, Delivery, Quizzes) | Faculty, Student | **PLANNED** | Staging Schemas: `lms.*`<br>`LearningManagementSystemModule/LMS-01-v2.0.docx` through `LMS-10.docx` | Database catalog verified | None | Schemas present in Supabase Staging DB; code implementation deferred to Phase 3 roadmap. |
| **EXM01..09** | Examination Suite (Hall Tickets, Marks, SGPA) | Exam Controller, Faculty | **PLANNED** | Staging Schemas: `exm.*`<br>`ExaminationModule/EXM-01.docx` through `EXM-09.docx` | Database catalog verified | None | Schemas present in Supabase Staging DB; code implementation deferred to Phase 4 roadmap. |
| **FIN01..10** | Finance & Fee Accounting Suite | Finance Officer, Student | **PLANNED** | Staging Schemas: `fin.*`, `bud.*`<br>`Finance&Accounts/FIN-01.docx` through `FIN-10.docx` | Database catalog verified | None | Schemas present in Supabase Staging DB; code implementation deferred to Phase 4 roadmap. |
| **CAM01-FEAT-01** | Announcements & Multichannel Broadcasts | Campus Admin, All Users | **ABSENT** | `docs/trial/POST_TRIAL_BACKLOG.md` (DEC-07) | Architecture gap documented | None | Source archive package directory was empty. Requires full greenfield specification and delivery in Phase 5. |
