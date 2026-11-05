# CampXSync Phase 3b & Phase 4 Verification & Completion Report

**Task:** STM-01 Student Dashboard Phase 3b (Application Documents - Applicant Journey) & Phase 4 (Full Student-Role Verification & Closeout)  
**Branch:** `feature/stm-01-student-dashboard`  
**Target Environment:** Development ONLY (`azulbaxszsqxymqmlwfe.supabase.co`)  
**Production / Staging Safety:** Production (`ekspahbsbwdpraaqfqui`) and legacy staging (`kpwdvvcschankgggitnj`) strictly untouched.  
**Date:** 2026-11-05  

---

## 1. Executive Summary

Phase 3b and Phase 4 conclude the delivery and verification of the Student role login-to-dashboard experience for STM-01 and STM-02.

- **STEP 0 (Prerequisites):** Minimal `PostgresPersonRepository.java` fix reinstated (`permanent_address_id`, `current_address_id` in SELECT and UPDATE). Rolled-back live persistence tests executed with zero residual records (`LivePostgresStudentPersistenceTest`: 3/3 PASS). Submitting profile corrections through the real Java path verified with `sys.outbox_events` emission. Test-session gate confirmed fail-closed in deployed environments.
- **Phase 3b (Application Documents):** Applicant persona model audited for STM-02. Ownership-scoped RLS policies deployed on `stu.applications` and `stu.application_documents` preventing forgery of verification status and blocking foreign application access. Student 2 applicant persona seeded idempotently (`STM01_PHASE3B_SEED`) with one rejected document for re-upload testing. Frontend `/student/documents` page built with backed fields only, responsive layout, loading/empty/error states, rejected document banner with reason, and read-only mode for handed-over students.
- **Phase 4 (Full Verification):** Full end-to-end verification across TypeScript, Next.js production build, Gate B role routing (32/32 tests), live document RLS (9/9 tests), cross-role & tenant isolation matrix (8/8 tests), Java student domain (103/103 tests), Java admissions domain (53/53 tests), and live PostgreSQL persistence (3/3 tests).

---

## 2. Personas & Verified Identities

| Persona | Email | Auth UUID (Dev Supabase) | Person / Student ID | State / Lifecycle Status |
| :--- | :--- | :--- | :--- | :--- |
| **Student 1** (Enrolled Student) | `student1.campx@gmail.com` | `81dd67d3-1d5f-4d8e-8061-a6764861f924` | Person: `81dd67d3-1d5f-4d8e-8061-a6764861f924`<br>Student: `81dd67d3-1d5f-4d8e-8061-a67648610002` | Enrolled / `HANDED_OVER`. Has active attendance and profile correction requests. Read-only application documents. |
| **Student 2** (Applicant Persona) | `student2.campx@gmail.com` | `bdf7a805-ff27-4ecd-862e-4c0c7b7090ae` | Person: `bdf7a805-ff27-4ecd-862e-4c0c7b7090ae`<br>Application: `bdf7a805-ff27-4ecd-862e-4c0c7b700003` | Applicant / `SUBMITTED`. Application `APP-DEV-2026-0002` in open intake `Dev Fall 2026 CSE Intake`. Has 1 rejected document (`HIGH_SCHOOL_TRANSCRIPT`) eligible for re-upload. |
| **College Admin** (Staff Persona) | `campxsync@gmail.com` | `70a8ffae-b5ad-4c61-a401-16f220893df4` | Profile: `70a8ffae-b5ad-4c61-a401-16f220893df4` | Staff permissions (`core.write`, `adm01.manage`). Approves profile corrections and verifies application documents. |

*Note: All passwords are typed directly by the operator; zero credentials or passwords are committed or printed.*

---

## 3. Physical Database Migrations & Seeds (Dev Ref: `azulbaxszsqxymqmlwfe`)

All database changes are confined to dev ref `azulbaxszsqxymqmlwfe`. Each migration has a paired reversible rollback script:

1. **`database/migrations/20261105000200_stm01_applicant_documents_rls.sql`**
   - **Rollback:** `database/migrations/20261105000200_rollback__stm01_applicant_documents_rls.sql`
   - **Policies Applied:**
     - `applications_staff_all` on `stu.applications`: Full management for staff permissions (`core.write`, `adm01.manage`, etc.) and system context (`auth.uid() IS NULL`).
     - `applications_student_select` on `stu.applications`: Students can SELECT applications where `applicant_person_id = auth.uid()` within their tenant.
     - `app_documents_staff_all` on `stu.application_documents`: Full management for staff and system context.
     - `app_documents_student_select` on `stu.application_documents`: Students can SELECT documents belonging to their own applications.
     - `app_documents_student_insert` on `stu.application_documents`: Students can INSERT documents into their own applications **ONLY** when `verification_status = 'PENDING'`, `verified_by IS NULL`, `verified_at IS NULL`, `rejection_reason IS NULL`, `is_fraudulent = false`, and `fraud_notes IS NULL`.
2. **`database/migrations/20261105000300_seed_student2_applicant.sql`**
   - **Rollback:** `database/migrations/20261105000300_rollback__seed_student2_applicant.sql`
   - **Tag:** `STM01_PHASE3B_SEED`
   - **Entities Created:**
     - `core.persons` for Student 2 (`bdf7a805-ff27-4ecd-862e-4c0c7b7090ae`).
     - `iam.user_profiles` and `iam.role_assignments` (role `STUDENT`).
     - `stu.applications`: Application `APP-DEV-2026-0002` in intake `a0000000-0000-0000-0000-000000000050` (`Dev Fall 2026 CSE Intake`, active window 2026-09-06 to 2027-03-05). Status: `SUBMITTED`.
     - `stu.application_documents`: 1 rejected document (`HIGH_SCHOOL_TRANSCRIPT`) with rejection reason: `"Illegible scan: text is blurred. Please provide a clear 300 DPI scan."`

---

## 4. Verification Suite Results

### 4.1 Frontend TypeScript & Production Build
- **Command:** `node frontend/node_modules/typescript/bin/tsc -p frontend/tsconfig.json --noEmit`
  - **Outcome:** **PASS** (Exit code 0, 0 compiler errors).
- **Command:** `npm --prefix frontend run build`
  - **Outcome:** **PASS** (Exit code 0, 26/26 routes generated dynamically/prerendered).
  - Routes compiled include:
    - `/student` (Attendance & Dashboard)
    - `/student/admissions` (Application Status)
    - `/student/documents` (Application Documents & Re-upload)
    - `/student/address` (Contact & Address Self-Service)
    - `/student/corrections` (Profile Corrections & History)
    - `/api/student/admissions/documents` (Route proxy with ownership checks)

### 4.2 Gate B Role Routing Matrix (32/32 PASS)
- **Suite:** `scripts/run_role_routing_suite.ps1` running `scripts/verify_role_routing.mjs` against Next.js server with test session:
  - **Platform Admin:** `/` -> `/platform` (307), `/platform` (200), `/college` (403), `/faculty` (403), `/student` (403), `/student/address` (403), `/student/corrections` (403), `/student/documents` (403). **PASS**
  - **College Admin:** `/` -> `/college` (307), `/platform` (403), `/college` (200), `/faculty` (200), `/student` (403), `/student/address` (403), `/student/corrections` (403), `/student/documents` (403). **PASS**
  - **Faculty:** `/` -> `/faculty` (307), `/platform` (403), `/college` (403), `/faculty` (200), `/student` (403), `/student/address` (403), `/student/corrections` (403), `/student/documents` (403). **PASS**
  - **Student:** `/` -> `/student` (307), `/platform` (403), `/college` (403), `/faculty` (403), `/student` (200), `/student/address` (200), `/student/corrections` (200), `/student/documents` (200). **PASS**
  - **Total:** 32 assertions, **32 passed, 0 failed**.

### 4.3 Phase 3b Live Document RLS Matrix (9/9 PASS)
- **Suite:** `scripts/verify_phase3b_documents.mjs` against live PostgreSQL (`azulbaxszsqxymqmlwfe`):
  - **Doc-01:** Student 2 can read own application (`APP-DEV-2026-0002`): **PASS**
  - **Doc-02:** Student 2 can list own application documents (finds 1 rejected document): **PASS**
  - **Doc-03:** Student 2 can insert new PENDING document for own application: **PASS**
  - **Doc-04:** Student 2 CANNOT insert document for foreign application (Student 1): **PASS** (RLS rejected)
  - **Doc-05:** Student 2 CANNOT set `verification_status = 'VERIFIED'` on insert: **PASS** (RLS rejected)
  - **Doc-06:** Student 2 CANNOT set `verified_by` or `verified_at` on insert: **PASS** (RLS rejected)
  - **Doc-07:** Student 1 cannot read Student 2's application documents: **PASS** (0 rows returned)
  - **Doc-08:** Student 2 cannot read Student 1's application documents: **PASS** (0 rows returned)
  - **Doc-09:** Staff (College Admin) can read and verify Student 2's document: **PASS** (Status updated to VERIFIED)
  - **Total:** 9 test cases, **9 passed, 0 failed**.

### 4.4 Phase 4 Cross-Role & Tenant Isolation Matrix (8/8 PASS)
- **Suite:** `scripts/verify_phase4_matrix.mjs` against live PostgreSQL:
  - **Matrix-01:** Student 2 cannot read Student 1 address: **PASS**
  - **Matrix-02:** Student 2 cannot update Student 1 address: **PASS**
  - **Matrix-03:** Student 2 cannot read Student 1 correction requests: **PASS**
  - **Matrix-04:** Student 2 cannot submit correction request targeting Student 1 record: **PASS**
  - **Matrix-05:** Cross-tenant isolation (Student 1 in Tenant A cannot see Tenant B students): **PASS**
  - **Matrix-06:** Cross-tenant isolation (Student 2 in Tenant A cannot see Tenant B application documents): **PASS**
  - **Matrix-07:** Student cannot read audit.change_log for other entities: **PASS**
  - **Matrix-08:** Student cannot bypass guard trigger to change DOB or gender: **PASS** (Fail-closed trigger rejected update)
  - **Total:** 8 test cases, **8 passed, 0 failed**.

### 4.5 Java Backend Suites
- **Student Domain Unit Suite:** `services/student-services/student-service`
  - **Result:** `Tests run: 103, Failures: 0, Errors: 0, Skipped: 0` -> **103/103 PASS**
- **Admissions Domain Unit Suite:** `services/student-services/admissions-service`
  - **Result:** `Tests run: 53, Failures: 0, Errors: 0, Skipped: 0` -> **53/53 PASS**
- **Live PostgreSQL Persistence Suite:** `LivePostgresStudentPersistenceTest`
  - `executeLiveStudentUpdateAddressAndRollback`: **PASS** (persists address, links person, verified rolled back, 0 residual rows)
  - `executeLiveStudentAddressLinkingAndRollback`: **PASS** (links residential address, verified rolled back, 0 residual rows)
  - `executeLiveStudentCorrectionSubmitAndRollback`: **PASS** (submits correction, verified rolled back, 0 residual rows)
  - **Result:** `Tests run: 3, Failures: 0, Errors: 0, Skipped: 0` -> **3/3 PASS**

---

## 5. Walkthrough Checklist for User (Manual Preview Testing)

Since the operator enters passwords in real browser sessions, execute the following walkthrough on the Vercel Preview URL or local deployment:

### Track A: Student 1 (Enrolled Student — `student1.campx@gmail.com`)
1. **Login & Redirection:**
   - Navigate to `/login`.
   - Enter `student1.campx@gmail.com` and your password.
   - Click Sign In.
   - **Expected:** Immediate redirect to `/student` (My Attendance & Dashboard).
2. **Dashboard Review (`/student`):**
   - Check attendance statistics (Overall Attendance, Conducted Hours, Attended Hours).
   - Verify Quick Action cards ("Update Contact & Address", "Request Profile Correction", "Admissions & Applications").
3. **Contact & Address Self-Service (`/student/address`):**
   - Click "Contact & Address" in sidebar navigation.
   - View current addresses (Permanent and Current Address).
   - Edit Address Line 1 / City and click "Save Addresses".
   - **Expected:** Success toast appears; updated address persists upon refresh.
4. **Profile Corrections (`/student/corrections`):**
   - Click "Profile Corrections" in sidebar navigation.
   - View Existing Requests table (status `APPROVED` or `PENDING`).
   - Submit a new correction request (e.g. Field: "Date of Birth", Proposed Value: "2004-05-15", Reason: "Birth certificate typo").
   - **Expected:** Request submits cleanly with status `PENDING` and appears in history.
5. **Documents View (`/student/documents`):**
   - Click "Documents" in sidebar navigation.
   - **Expected:** Read-only banner is displayed: *"Your application has completed admission handover. Documents are archived in your permanent student record and cannot be modified."*
   - Upload actions are disabled.

### Track B: Student 2 (Applicant Persona — `student2.campx@gmail.com`)
1. **Login & Redirection:**
   - Sign in with `student2.campx@gmail.com` and password.
   - **Expected:** Redirect to `/student`.
2. **Application Documents (`/student/documents`):**
   - Navigate to `/student/documents`.
   - **Expected:** Active application card shows `APP-DEV-2026-0002` (`Dev Fall 2026 CSE Intake`).
   - Rejected document alert appears:
     - Document: `HIGH_SCHOOL_TRANSCRIPT`
     - Status: `REJECTED`
     - Reason: *"Illegible scan: text is blurred. Please provide a clear 300 DPI scan."*
   - Click "Re-upload Document".
   - Choose a PDF or PNG file (< 10MB) and submit.
   - **Expected:** Document uploads successfully; status transitions to `PENDING` awaiting staff review.
3. **Negative Checks:**
   - Attempt to navigate to `/college` or `/platform`.
   - **Expected:** Redirected or 403 Forbidden.

---

## 6. Defect Log & Resolutions

| Defect ID | Description | Root Cause | Resolution | Status |
| :--- | :--- | :--- | :--- | :--- |
| **DEF-01** | `PostgresPersonRepository` omitted address columns in queries | `permanent_address_id` and `current_address_id` were missing from SELECT column list and UPDATE parameterized statement | Reinstated minimal address mapping in `PostgresPersonRepository.java` (SELECT, UPDATE, ResultSet mapper) | **RESOLVED** (Commit `46cd60a`) |
| **DEF-02** | Guard trigger rejected student address updates due to NULL gender coercion | Repository coerced `null` gender to `"U"`, triggering biographical modification guard | Preserved exact `null` value when updating unchanged gender field | **RESOLVED** (Commit `46cd60a`) |
| **DEF-03** | RLS policy compilation error on `iam.has_role()` | Function `iam.has_role()` is not declared in dev PostgreSQL schema | Replaced with standard permission pattern `(SELECT iam.has_permission('core.write'))` and `iam.has_permission('adm01.manage')` matching `60_rls.sql` | **RESOLVED** (Commit `91cb726`) |
| **DEF-04** | Document INSERT policy denied self-service uploads | Default policy required application document to be updated by staff | Created `app_documents_student_insert` with strict `WITH CHECK` enforcing `verification_status = 'PENDING'` and applicant ownership | **RESOLVED** (Commit `91cb726`) |

---

## 7. Out-of-Scope Modules & Boundaries

The following modules remain intentionally untouched and deferred to future authorized waves:
1. **STM-03 Academic Enrollment & Course Registration:** Term registration, course selection, credit prerequisites, and fee payment gates.
2. **ACD-05 Timetable Conflict Scheduling Engine:** Automated heuristic clash-free scheduling.
3. **Production Promotion:** Promotion of schema or features to production Supabase ref `ekspahbsbwdpraaqfqui` requires separate deployment authorization.
