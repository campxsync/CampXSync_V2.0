# CampXSync — Phase Change Log

**Repository**: `CampXSync_V2.0`  
**Branch**: `ReviewBranch`  

---

## Phase 1 — Baseline Discovery (Read-Only)
- **Files Created**:
  - `docs/trial/DECISIONS.md`: Initial architecture decision records (DEC-01, DEC-03, DEC-04, DEC-08, DEC-11, DEC-13).
  - `docs/trial/CHANGES.md`: Change tracking log across phases.
- **Files Inspected**:
  - `frontend-web/package.json`, `vite.config.js`, `src/App.jsx`, `src/context/AuthContext.jsx`, `src/services/api.js`, `src/services/supabaseClient.js`.
  - `.planning/STATE.md`, `docs/handover/HANDOVER.md`, `docs/mvp/03-progress.md`, `docs/reference/product_lifecycle.md`.
  - `Documentation/relational_constraint_index_catalog.md`, `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/`.
- **Database Introspection (Read-Only via MCP)**:
  - Introspected tables, views, constraints, RLS policies, indexes, and functions across schemas `core`, `iam`, `acd`, `stu`, `hrm`, `audit`.
  - Verified Supabase staging ref `kpwdvvcschankgggitnj`; confirmed 0 connections to production.

---

## Phase 2 — UI Foundation, Auth, Role Routing, Shell, Early Deploy
- **Files Created**:
  - `frontend/package.json`, `tsconfig.json`, `next.config.mjs`, `tailwind.config.ts`, `postcss.config.mjs`, `vercel.json`, `.gitignore`, `.env.example`.
  - `frontend/src/app/globals.css`: Full Section 5 design system CSS variables and tokens (`--navy-950`, `--navy-900`, `--blue-600`, etc.).
  - `frontend/src/lib/utils.ts`: `cn`, date formatting, time formatting, initials.
  - `frontend/src/lib/supabase/client.ts` & `server.ts`: `@supabase/ssr` browser and server clients with cookie handling.
  - `frontend/src/lib/auth/resolve-role.ts`: Server-side role resolution normalizing to `platform_admin | college_admin | faculty | student`.
  - `frontend/src/lib/nav-config.ts`: Purpose-built navigation configuration per role.
  - `frontend/src/middleware.ts`: Next.js Edge Middleware for server-side route guards.
  - `frontend/src/lib/preferences/preference-model.ts`: Typed, versioned preference model and client adapter with storage disclosure.
  - `frontend/src/components/ui/`: Reusable components (`button.tsx`, `input.tsx`, `card.tsx`, `stat-card.tsx`, `badge.tsx`, `empty-state.tsx`, `error-state.tsx`).
  - `frontend/src/components/shell/`: `sidebar.tsx`, `topbar.tsx`, `breadcrumbs.tsx`.
  - `frontend/src/components/preferences/preference-modal.tsx`: Workspace preferences modal with theme, density, date range, accessibility.
  - `frontend/src/app/layout.tsx` & `page.tsx`: Root layout with font and root role redirect.
  - `frontend/src/app/(auth)/login/page.tsx`: Professional split-layout login page with demo account shortcuts.
  - `frontend/src/app/access-denied/page.tsx`: Friendly no-profile / inactive membership page.
  - `frontend/src/app/(dashboard)/layout.tsx` & `layout-client.tsx`: Server-guarded dashboard shell.
  - `frontend/src/app/(dashboard)/platform/page.tsx`: Platform Admin home with real aggregate queries.
  - `frontend/src/app/(dashboard)/college/page.tsx`: College Admin home with real aggregate queries.
  - `frontend/src/app/(dashboard)/faculty/page.tsx`: Faculty teaching workspace with timetable slot cues.
  - `frontend/src/app/(dashboard)/student/page.tsx`: Student attendance summary (stretch).
  - `frontend/src/app/not-found.tsx` & `error.tsx`: Custom 404 and error boundaries in app design.
  - `scripts/seed-staging.ts` & `database/seed-data/staging_seed_college_a_and_b.sql`: Idempotent seed script and SQL definition for Phase 3.
- **Build & Verification**:
  - Next.js 15 production build compiled in 4.9s with 0 errors across all 8 static and dynamic routes.

---

## Phase 3 — Staging Verification & State Recovery
- **Live Staging Audit (PostgreSQL 17.11 on aarch64, project `kpwdvvcschankgggitnj`)**:
  - Partition RLS Status: Directly inspected all 24 monthly partition child tables (`audit`, `cam`, `hrm`, `lms`). Confirmed `relrowsecurity = true` across all 24 tables (Finding 1 resolved). Confirmed 0 role table grants for `anon` and `authenticated`.
  - Test Tenant Provisioning: Confirmed `CAMPX_STAGING` (`11111111-1111-1111-1111-111111111111`) is active in `core.tenants`. Trigger `trg_seed_roles` created 30 system roles in `iam.roles` and 2,138 role-permission mappings.
  - Staging Identities: Confirmed `ba77b8f7-ab32-46bd-85d2-5aade3526880` and `70a8ffae-b5ad-4c61-a401-16f220893df4` (`campxsync@gmail.com`) active in `iam.user_profiles` with `COLLEGE_ADMIN` role assignment in `iam.role_assignments`.
  - Auth Hook: Introspected `iam.custom_access_token_hook` confirming `app_metadata.tenant_id` stamping.
  - Pending Staging Gate: CRIT-09 Dashboard hook toggle confirmation in Supabase Dashboard. Dual-tenant staging seed (`staging_seed_college_a_and_b.sql`) awaiting explicit user approval phrase `GO SEED-STAGING`.

---

## Phase 4 — Platform → College Onboarding (ADM)
- **Files Created**:
  - `frontend/src/app/(dashboard)/platform/institutes/institutes-registry-client.tsx`: Interactive client registry with status filter tabs (`All`, `Active`, `Onboarding`, `Suspended`, `Offboarded`) and real-time search by institute name, code, and UUID.
  - `scripts/verify_phase4_onboarding.mjs`: Automated Node.js verification test suite covering 19 test cases across 7 test suites (Role boundaries, suspension denial, API auth gate, input validation, conflict detection, 5-step saga progression, aggregate cross-tenant data isolation).
- **Files Modified**:
  - `frontend/src/components/ui/badge.tsx`: Extended `Badge` component and styling variants to support `OFFBOARDED`, `INACTIVE`, and `ARCHIVED` statuses.
  - `frontend/src/middleware.ts`: Implemented tenant suspension guard detecting suspended status and issuing HTTP 307 redirect to `/access-denied?reason=tenant_suspended`.
  - `frontend/src/app/(dashboard)/platform/page.tsx`: Completed Platform Operator dashboard with real aggregate queries (total platform users, active tenancies, role templates), status breakdown cards, cross-tenant distribution table (strictly aggregate counts, 0 student/faculty rows), onboarding activity timeline, and platform security audit events feed with link to `/platform/audit`.
  - `frontend/src/app/(dashboard)/platform/institutes/page.tsx`: Updated page to query database aggregates (`user_count`, `college_count`) and pass data to `institutes-registry-client.tsx`.
  - `frontend/src/app/(dashboard)/platform/institutes/new/onboarding-wizard.tsx`: Enhanced multi-step wizard to include:
    - Step 1: Institute Identity (`tenantName`, `tenantCode`, `subdomain`, `plan`).
    - Step 2: Primary College (`collegeName`, `collegeCode`).
    - Step 3: Administrator Details (`adminName`, `adminEmail`, `adminPhone`).
    - Step 4: Review & Confirmation step before submission.
    - Step 5: Saga Execution View displaying 5 milestones, animated status indicators, failure pin-pointing, and safe idempotent retry.
  - `frontend/src/app/api/platform/onboard-tenant/route.ts`: Rewrote saga orchestration endpoint with Zod schema validation, pre-flight conflict detection (`failedStep: 1` on duplicate code, `failedStep: 3` on duplicate email), strict Staging Safety Write Boundary (`req.headers['x-provision-approval'] === 'GO PROVISION-TENANT'`), 5 transactional milestones, idempotent partial unique index matching (`ON CONFLICT (code) WHERE deleted_at IS NULL`), and audit logging into `audit.events`.
- **Verification Evidence**:
  - Automated Suite: 19/19 tests passed (`scripts/verify_phase4_onboarding.mjs`).
  - TypeScript Type Check: `npx tsc --noEmit` clean with 0 errors.
  - Browser Walkthrough: Autonomous walkthrough recorded in `phase4_onboarding_walkthrough_1792136712022.webp` and live provisioned verification recorded in `phase4_live_provisioned_verified_1792137949832.webp`.
  - Live Staging Write Status: **PASS**. User provided approval phrase `GO PROVISION-TENANT`. Executed live onboarding saga against staging (`kpwdvvcschankgggitnj`): tenant `APEX_SCI` created, 30 system roles seeded, admin `robert.vance@apex.edu` provisioned, `COLLEGE_ADMIN` role granted, immutable event in `audit.events`, idempotency verified with pre-flight duplicate interception (HTTP 409).

---

## Phase 5 — College Setup & Roster Views
- **Files Created**:
  - `frontend/src/app/(dashboard)/college/departments/page.tsx`: Server-guarded College Admin view querying `core.departments` and `acd.programs` strictly for authenticated `tenant_id`.
  - `frontend/src/app/(dashboard)/college/departments/departments-manager.tsx`: Interactive client component with tabbed views for Departments and Programs, creation modal forms, and real-time status alerts.
  - `frontend/src/app/api/college/departments/route.ts`: API route for department creation under tenant boundary.
  - `frontend/src/app/api/college/programs/route.ts`: API route for degree program creation under tenant boundary.
  - `frontend/src/app/(dashboard)/college/faculty/page.tsx`: Server-guarded Faculty Roster view joining `hrm.employees`, `core.persons`, and `core.departments` under tenant scope.
  - `frontend/src/app/(dashboard)/college/faculty/faculty-roster.tsx`: Interactive client component with search, department filtering, and faculty enrollment form.
  - `frontend/src/app/api/college/faculty/route.ts`: API route creating `core.persons` + `hrm.employees` with tenant scoping.
  - `frontend/src/app/(dashboard)/college/students/page.tsx`: Server-guarded Student Body Roster view joining `stu.students`, `core.persons`, and `acd.programs` under tenant scope.
  - `frontend/src/app/(dashboard)/college/students/student-roster.tsx`: Interactive client component with search, program filtering, and student enrollment form.
  - `frontend/src/app/api/college/students/route.ts`: API route creating `core.persons` + `stu.students` with tenant scoping.
- **Files Modified**:
  - `frontend/src/lib/nav-config.ts`: Updated sidebar navigation for Platform Admin and College Admin to include new routes (`/platform/institutes`, `/platform/institutes/new`, `/college/departments`, `/college/faculty`, `/college/students`).
  - `frontend/src/app/(dashboard)/platform/page.tsx`: Linked 'Onboard New Institute' button directly to `/platform/institutes/new`.
- **Build & Verification**:
  - Next.js 15 production build compiled in 6.9s with 0 errors across all 18 static, dynamic, and API routes.

---

## Phase 5 (Continued) — Timetable Grid & Attendance Session Workflow
- **Seed Script Safety & Catalog Audit**:
  - Audited `database/seed-data/staging_seed_college_a_and_b.sql` against live staging catalog schemas.
  - Corrected 7 catalog defects:
    1. Fixed `core.persons` column from `email` to `primary_email`.
    2. Fixed `acd.batches` column from `academic_year_id` to `admission_year_id`.
    3. Added mandatory not-null `subject_type` (Constraint `ck_subjects_subject_type`) and `department_id` to `acd.subjects`.
    4. Corrected `acd.course_offerings` status from invalid `'ACTIVE'` to `'OPEN'`, added mandatory not-null `college_id`, and linked `term_id` to `acd.calendar_terms`.
    5. Added missing prerequisite records in `core.calendars` and `acd.calendar_terms` required for FKs.
    6. Added mandatory `term_id` to `acd.timetables`.
    7. Enforced strict idempotency with deterministic UUIDs and partial unique index conflict targets on `acd.batch_students` and `acd.attendance_records`.
- **Files Created**:
  - `frontend/src/app/api/college/timetable/entries/route.ts`: API route creating timetable entries under tenant scope with exclusion constraint clash detection (`ex_tte_employee`).
  - `frontend/src/app/(dashboard)/college/timetable/page.tsx`: Server component querying published timetable versions and weekly slots.
  - `frontend/src/app/(dashboard)/college/timetable/timetable-grid.tsx`: Interactive weekly calendar grid component with day filters and slot scheduling drawer.
  - `frontend/src/app/api/college/attendance/sessions/route.ts`: API route for opening and retrieving attendance sessions anchored to timetable entries.
  - `frontend/src/app/api/college/attendance/records/route.ts`: API route for submitting and persisting student attendance marks into `acd.attendance_records` with idempotent upsert.
  - `frontend/src/app/(dashboard)/college/attendance/page.tsx`: College Admin attendance oversight view with real session breakdown and attendance percentage metrics.
  - `frontend/src/app/(dashboard)/college/attendance/attendance-manager.tsx`: Client component for inspecting attendance session statuses.
  - `frontend/src/app/(dashboard)/faculty/attendance/page.tsx`: Faculty attendance capture page with live timetable slot selection.
  - `frontend/src/app/(dashboard)/faculty/attendance/faculty-attendance-view.tsx`: Fast-marking interactive roster component with Present/Absent/Leave toggles, "Mark All Present", and finalized locking.
- **Build & Verification**:
  - Next.js 15 production build compiled cleanly in 4.2s with 0 errors across all 24 static, dynamic, and API routes.

---

## Phase 6 & 7 — Audited Attendance Corrections & Statutory Shortage Analytics
- **Files Created**:
  - `frontend/src/app/api/college/attendance/corrections/route.ts`: API route for audited attendance corrections with Zod validation, optimistic concurrency locking (`row_version`), immutable audit trail creation in `audit.change_log` (`action = 'U'`), and governance event logging in `audit.events` (`action = 'ATTENDANCE_CORRECTED'`).
  - `frontend/src/app/api/college/attendance/report/route.ts`: API route computing cumulative student attendance percentages across published courses, evaluating the statutory 75% shortage threshold, and returning actionable analytics.
- **Files Modified**:
  - `frontend/src/app/api/college/attendance/records/route.ts`: Added `GET` endpoint returning detailed student roster for a session, along with historical corrections from `audit.change_log` and real-time attendance percentage recalculation.
  - `frontend/src/app/(dashboard)/college/attendance/attendance-manager.tsx`: Complete overhaul with two primary tabs: (1) Sessions Oversight & Governance with row-level session record expansion, slide-over correction drawer with mandatory reason enforcement and concurrency conflict alerts, and historical correction timeline; (2) Institutional Attendance & Shortage Report with summary KPI cards, statutory shortage indicators, interactive shortage-only filtering, and client-side CSV export.
- **Verification & Testing**:
  - `npx tsc --noEmit`: 100% clean, 0 type errors.
  - `npm run build`: Production build succeeded across all 30 routes in 37.5s.
  - Real browser walkthrough (`attendance_audit_verified_1792131796219.webp`): Verified session inspection, correction drawer submission with justification `"Audited administrative review complete"`, live `row_version` increment from `v2` to `v3`, session percentage update (90%), shortage report filtering, and immutable ledger verification on `/college/audit`.
  - Staging PostgreSQL assertions: Read-only MCP query confirmed `audit.events` and `audit.change_log` entries present and linked to tenant `11111111-1111-1111-1111-111111111111`.

## Phase 3 — Gate A (Seed Idempotency) & Gate B (Role Routing Verification)
- **Files Modified**:
  - `frontend/src/middleware.ts`: Refined server-side route guards and role boundary definitions for `platform_admin`, `college_admin`, `faculty`, and `student`.
  - `docs/staging/00-checkpoint.md`: Updated Phase 3 gate statuses (Gate A: BLOCKED awaiting write approval; Gate B: PASS).
  - `docs/staging/05-execution-log.md`: Recorded Steps P3-25 and P3-26 and detailed Step 8 and Step 9 logs.
  - `.planning/STATE.md`: Reconciled Phase 3 state to reflect Gate A write blocker.
- **Files Created**:
  - `scripts/verify_role_routing.mjs`: Automated 20-case test matrix validating all role routes, access boundaries, unauthenticated redirects, and negative no-profile user denials.
  - `scripts/verify_real_supabase_auth_routing.mjs`: Live Supabase Auth sign-in script verifying JWT claims and profile association on staging.
- **Verification & Testing**:
  - Gate B (Role Routing): 20/20 tests passed on live Next.js server (`http://localhost:3000`).
  - Real Supabase Auth: Successfully signed in as staging administrator `campxsync@gmail.com` against `https://kpwdvvcschankgggitnj.supabase.co`.
  - Gate A (Seed Idempotency): PASS. User provided explicit write authorization phrase `GO SEED-IDEMPOTENCY-TEST`. Executed second run of `database/seed-data/staging_seed_college_a_and_b.sql` via `StagingPrerequisitesExecutionTest#test05_executeDualTenantStagingSeed` against staging (`kpwdvvcschankgggitnj`). Verified 0 duplicates across 9 business tables, 100% stable pre/post entity counts, and confirmed intentional `ON CONFLICT DO UPDATE` state behavior on `acd.attendance_records`. Phase 3 is 100% COMPLETE.

---

## Frontend Presentation & Shared Styling Defect Resolution
- **Issue**:
  - Browser rendering at `http://localhost:3000/platform` defaulted to raw browser serif typography (Times New Roman), blue underlined links, unstyled native buttons, and vertically collapsed layout elements (`document.styleSheets.length === 0`).
- **Root Cause Analysis**:
  - Frontend styling architecture (`frontend/src/app/globals.css`, `frontend/src/app/layout.tsx`, `frontend/tailwind.config.ts`, `frontend/postcss.config.mjs`) was structurally complete and correct (`npx tailwindcss` produced a valid 37KB bundle).
  - During the preceding Phase 3 gate execution, a production build (`npm run build`) was run concurrently in the workspace while the Next.js dev server was actively serving requests in the background.
  - `next build` purged and rebuilt the `.next/` build artifact directory with production webpack chunks, corrupting the active dev server's in-memory chunk map (`Cannot find module './897.js'`).
  - The running dev server subsequently served stale CSS link tags with outdated compilation hashes (`/_next/static/css/app/layout.css?v=...`), which Chromium was unable to bind to the active document (`sheet === null`), causing a fallback to default browser styling.
  - Additionally, catalog discrepancies in `faculty/page.tsx` and `faculty/timetable/page.tsx` queried nonexistent table `acd.sections` and column `tte.room_number`, which threw server-side query errors during faculty dashboard rendering.
- **Files Modified**:
  - `frontend/src/app/(dashboard)/faculty/page.tsx`: Corrected join to canonical `acd.course_sections sec` using `sec.section_code`, removed nonexistent `tte.room_number`.
  - `frontend/src/app/(dashboard)/faculty/timetable/page.tsx`: Corrected join to `acd.course_sections sec` using `sec.section_code`, removed nonexistent `tte.room_number`.
  - `docs/trial/CHANGES.md`: Recorded incident root cause, files changed, checks, and verification artifacts.
- **Checks Performed**:
  - Confirmed Tailwind compilation directly via CLI (`npx tailwindcss -i src/app/globals.css`).
  - Terminated corrupted dev server background task (`task-208`).
  - Spawned fresh Next.js dev server instance on port 3000 (`task-483`).
  - `npx tsc --noEmit`: 100% clean type check across all frontend components.
  - Live dev server compilation: Verified clean 200 responses with zero webpack chunk missing errors.
- **Browser Verification Results**:
  - DOM stylesheet inspection: Verified `document.styleSheets.length > 0` with 1,768 compiled CSS rules, `--navy-950` `#0B1F3A` and `--blue-600` `#1F6FEB` custom properties, Inter typography, and proper flex/grid layout containment.
  - Role dashboards verified:
    - `/platform`: Full enterprise presentation restored (navy sidebar, KPI stat cards, institute status badges, responsive grid). Verified via artifact `platform_dashboard_final_verified_1792134846312.png`.
    - `/college`: Restored metric cards, department/program stats, and timetable section. Verified via artifact `college_dashboard_verified_1792134737152.png`.
    - `/faculty`: Restored weekly schedule timetable cards with status badges and attendance action triggers. Verified via artifact `faculty_dashboard_verified_1792134765869.png`.
    - `/student`: Server-side route boundary guard verified. Verified via artifact `student_dashboard_initial_1792134806272.png`.
  - Comprehensive browser recording: `verify_all_dashboards_1792134615032.webp`.



---

## Phase 4 — Platform → College Onboarding (ADM Saga) Live Provisioning
- **Files Modified**:
  - `frontend/src/app/(dashboard)/platform/page.tsx`: Aggregated metrics per tenant and college counts.
  - `frontend/src/app/(dashboard)/platform/institutes/page.tsx`: Tenancy registry view with client component.
  - `frontend/src/app/(dashboard)/platform/institutes/new/onboarding-wizard.tsx`: 4-step wizard with preflight conflict detection.
  - `frontend/src/app/api/platform/onboard-tenant/route.ts`: 5-milestone ADM saga with idempotent retry and write boundary.
  - `frontend/src/components/ui/badge.tsx`: Support for institutional statuses.
  - `frontend/src/middleware.ts`: Suspended tenant routing guard.
  - `scripts/execute_phase4_live_provisioning.mjs`: Authorized live provisioning script for staging.
  - `scripts/verify_phase4_onboarding.mjs`: Automated 19-test verification suite.
- **Verification Results**:
  - Live staging provisioning executed under explicit approval phrase `GO PROVISION-TENANT`.
  - Staging catalog verified: Tenant `APEX_SCI`, College `SOC`, 30 system roles, 2,138 role-permission mappings, admin profile `robert.vance@apex.edu`, and `COLLEGE_ADMIN` role assignment.
  - Automated suite: 19/19 tests passed.
  - Browser walkthrough: `phase4_live_provisioned_verified_1792137949832.webp`.

---

## Phase 5 — College Admin Setup Views, People, and Assignments
- **Files Modified**:
  - `frontend/src/app/(dashboard)/college/page.tsx`: Real aggregate metric queries for College Admin operations dashboard.
  - `frontend/src/app/(dashboard)/college/departments/page.tsx`: Read-only view for academic departments and degree programs.
  - `frontend/src/app/(dashboard)/college/batches/page.tsx`: Read-only view for academic cohorts and curriculum subject catalog.
  - `frontend/src/app/(dashboard)/college/timetable/page.tsx` & `timetable-grid.tsx`: Dual presentation modes (Week Grid & List View) and slot creation.
  - `frontend/src/app/(dashboard)/college/faculty/faculty-roster.tsx`: Teaching faculty roster, filter by department, enrollment form drawer, and deactivation confirmation modal. Aligned with DB check constraint `ck_employees_employment_type` (`PERMANENT`).
  - `frontend/src/app/(dashboard)/college/students/page.tsx` & `student-roster.tsx`: Student body roster, batch badges, filter by batch and program, enrollment form drawer with batch selector, assign/reassign batch modal, and deactivation confirmation modal.
  - `frontend/src/app/api/college/faculty/route.ts`: `GET` (list faculty), `POST` (enforces duplicate employee ID and email checks, creates `core.persons`, `hrm.employees`, provisions `iam.user_profiles`, grants `FACULTY` in `iam.role_assignments`, logs `FACULTY_CREATED` in `audit.events`), `DELETE` (soft delete setting `deleted_at = now()`, `status = 'EXITED'`, deactivates user profile, logs `FACULTY_DELETED`).
  - `frontend/src/app/api/college/students/route.ts`: `GET` (list students with batch and roll no), `POST` (enforces duplicate enrollment no and email checks, creates `core.persons`, `stu.students`, inserts initial batch membership into `acd.batch_students`, logs `STUDENT_ENROLLED` in `audit.events`), `DELETE` (soft delete setting `deleted_at = now()`, `status = 'WITHDRAWN'`, sets `acd.batch_students.status = 'DROPPED'`, sets `core.persons.status = 'INACTIVE'`, logs `STUDENT_DELETED`).
  - `frontend/src/app/api/college/students/assign-batch/route.ts`: Endpoint for assigning or reassigning student to batch in `acd.batch_students` with optional roll number and immutable audit event logging (`STUDENT_BATCH_ASSIGNED`).
  - `frontend/src/app/api/college/timetable/entries/route.ts`: Enhanced with immutable audit event logging (`TIMETABLE_ENTRY_CREATED`).
  - `scripts/verify_phase5_college_setup.mjs`: Comprehensive automated verification suite.
- **Verification Results**:
  - Automated verification suite: **31/31 PASSED (0 FAILED)** across route guarding, API listings, faculty provisioning, student enrollment, batch reassignment, audited soft deletion, and cross-tenant isolation.
  - TypeScript compilation: 100% clean (`npx tsc --noEmit` completed with 0 errors).
  - Autonomous browser walkthrough: Recorded video session `phase5_college_setup_1792139744282.webp` and 11 visual screenshots across all College Admin screens.
  - Status: **PASS (100% COMPLETE)**.

---

## Phase 6 — Faculty Attendance (ACD-06) & Corrections

- **Files Created / Modified**:
  - `frontend/src/app/(dashboard)/faculty/page.tsx`: Complete overhaul of Faculty Workspace. Displays today's timetable slots with live attendance state (`Not started`, `Open (In Progress)`, `Submitted`), "Start Attendance" / "Resume Sheet" / "View Session" direct triggers, upcoming timetable slots forward schedule, and missed/overdue sessions alert banner with quick completion trigger.
  - `frontend/src/app/(dashboard)/faculty/attendance/page.tsx`: Server component supporting `searchParams` (`slotId`, `sessionId`), resolving authenticated faculty identity, timetable entries, and tenant student roster.
  - `frontend/src/app/(dashboard)/faculty/attendance/faculty-attendance-view.tsx`: Fast interactive attendance capture sheet with running counts breakdown (Present, Absent, Leave, Late, On Duty, Overall Percentage), unsaved changes guard (`beforeunload` + slot/date change confirmation), submission confirmation modal with counts summary and ledger warning, and post-submission read-only locked sheet mode with "Submitted & Read-Only" status badge.
  - `frontend/src/app/(dashboard)/faculty/sessions/page.tsx`: Fixed database schema join to canonical `acd.course_sections` (`sec.section_code`), integrated direct review links to `/faculty/attendance?slotId=...&sessionId=...`, and added session headcount metrics.
  - `frontend/src/app/api/college/attendance/sessions/route.ts`: Backend enforcement verifying timetable slot existence and tenant scoping, duplicate session prevention (returns existing session with `isExisting: true`), and immutable audit event logging (`ATTENDANCE_SESSION_OPENED`).
  - `frontend/src/app/api/college/attendance/records/route.ts`: Roster student membership validation, locked session enforcement (HTTP 409 if modified post-closure), partial index conflict target `ON CONFLICT (tenant_id, session_id, student_id) WHERE (deleted_at IS NULL)`, and immutable audit event logging (`ATTENDANCE_SUBMITTED`).
  - `frontend/src/app/api/college/attendance/corrections/route.ts`: Audited corrections with optimistic concurrency locking (`expectedVersion` vs `row_version`), mandatory justification validation (min 5 characters), domain persistence into `acd.attendance_corrections`, change logging in `audit.change_log`, audit event logging in `audit.events` (`ATTENDANCE_CORRECTED`), and asynchronous domain event emission into `sys.outbox_events` (`AttendanceCorrected`).
  - `docs/trial/DECISIONS.md`: Added `DEC-14: Statutory Attendance Shortage Threshold (75%) Baseline Assumption` per V4 requirements.
  - `scripts/verify_phase6_attendance.mjs`: Automated 37-test verification suite covering access control, session deduplication, roster validation, idempotent submission, read-only locking, correction permissions, optimistic locking conflicts, domain persistence, outbox event emission, 75% shortage threshold, and cross-tenant data isolation.
- **Verification Results**:
  - Automated verification suite: **37/37 PASSED (0 FAILED)** across 5 suites:
    1. Route Guarding & Role Boundaries: 7/7 PASS
    2. Session Opening & Duplicate Prevention: 6/6 PASS
    3. Roster Validation & Attendance Submission: 8/8 PASS
    4. Audited Attendance Corrections (ACD-06): 11/11 PASS
    5. Shortage Reporting & Cross-Tenant Isolation: 5/5 PASS
  - TypeScript compilation: **100% clean** (`npx tsc --noEmit` exited with 0 errors).
  - Next.js production build: **100% clean** (`npm run build` compiled all 33 routes successfully in 11.1s).
  - Autonomous browser walkthrough: Recorded video session `phase6_attendance_walkthrough_1792141848113.webp` and captured screenshots (`faculty_dashboard_1792141895895.png`, `faculty_attendance_sheet_1792141941950.png`, `attendance_page_loaded_1792142154003.png`).
  - Database persistence confirmed: Staging PostgreSQL assertions verified rows in `acd.attendance_corrections`, `audit.change_log`, `audit.events`, and `sys.outbox_events`.
  - Status: **PASS (100% COMPLETE)**.

---

## Phase 7 — Role-Specific Dashboards, Preferences & Reports (ACD-10)

- **Files Created / Modified**:
  - `frontend/src/app/(dashboard)/college/page.tsx`: Complete College Operations Dashboard querying real PostgreSQL counts for enrolled students (42), appointed faculty (5), attendance sessions (6), overall attendance percentage (80.8%), shortage students (4), 14-day attendance trend line points, and batch/subject distribution.
  - `frontend/src/app/(dashboard)/college/college-dashboard-view.tsx`: Client dashboard view integrating Recharts responsive 14-day trend line chart, batch/subject bar chart, shortage student table with percentage badges, unsubmitted missed sessions alert, recent sessions table, and dynamic widget reordering.
  - `frontend/src/app/(dashboard)/platform/page.tsx` & `platform-dashboard-view.tsx`: Platform Operator dashboard with aggregate-only metrics (institutes by lifecycle status: Active, Onboarding, Suspended, Offboarded; total platform users; system roles; recent platform audit events) with strict isolation preventing any exposure of raw tenant student or faculty rows.
  - `frontend/src/app/(dashboard)/faculty/page.tsx` & `faculty-dashboard-view.tsx`: Faculty workspace with today's timetable slots, attendance states (`Not started`, `Open`, `Submitted`), quick Start/Resume actions, upcoming schedule slots, and missed sessions alerts.
  - `frontend/src/lib/preferences/preference-model.ts`: Enhanced typed, versioned UserPreferences schema (version 1) supporting theme, table density (`comfortable` / `compact`), date range (7, 14, 30 days), accessibility options (high contrast, reduced motion), and role-isolated widget configuration (`college_admin`, `faculty`, `platform_admin`). Implemented `ServerPreferenceAdapter` providing authoritative PostgreSQL persistence with device local storage fallback.
  - `frontend/src/components/preferences/preference-modal.tsx`: Workspace preferences modal with Display & Accessibility tab, Dashboard Widgets tab (ordering/hiding widgets), reset-to-defaults, live theme and DOM reactivity, and clear persistence disclosure.
  - `frontend/src/app/api/user/preferences/route.ts`: New API route with Zod validation. `GET` loads typed preferences from `iam.user_profiles.preferences`. `PUT` updates `iam.user_profiles.preferences` in PostgreSQL, incrementing `row_version` and ensuring role widget isolation without escalation.
  - `frontend/src/app/api/college/attendance/report/route.ts`: Enhanced ACD-10 cumulative attendance report API supporting query filters (`sectionId`, `subjectId`, `startDate`, `endDate`, `studentId`, `shortageOnly`) and formula-safe CSV export (`format=csv`).
  - `frontend/src/components/ui/badge.tsx`: Added `label?: string` support in `BadgeProps` for custom badges.
  - `frontend/src/middleware.ts`: Guarded unauthenticated API requests returning HTTP 401 JSON instead of page redirection.
  - `scripts/verify_phase7_dashboards_preferences.mjs`: Automated 26-test verification suite covering role dashboard authorization, data reconciliation, Platform aggregate-only compliance, typed preference validation, PostgreSQL profile persistence, role isolation, and ACD-10 reports.
- **Verification Results**:
  - Automated verification suite: **26/26 PASSED (0 FAILED)** across 5 test suites:
    1. Role Dashboard Route Guarding: 6/6 PASS
    2. Platform Admin Aggregate-Only Compliance: 2/2 PASS
    3. College Admin Data Reconciliation with PostgreSQL: 6/6 PASS
    4. Preferences Foundation & PostgreSQL Persistence: 8/8 PASS
    5. Cumulative Attendance Report & CSV Export: 4/4 PASS
  - TypeScript compilation: **100% clean** (`npx tsc --noEmit` exited with 0 errors).
  - Next.js production build: **100% clean** (`npm run build` compiled all 34 routes successfully with code 0).
  - Autonomous browser walkthrough: Recorded video `phase7_dashboards_walkthrough_1792144886693.webp` and 5 screenshots across College Admin dashboard, Preference Modal, Cumulative Report, and Faculty dashboard.
  - Status: **PASS (100% COMPLETE)**.


