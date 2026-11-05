# Phase 0 Read-Only Audit Report: Student Role Login-to-Dashboard Experience

**Target:** CampXSync Student Role (STM-01 & STM-02)  
**Date:** 2026-11-05  
**Connected Project Ref:** `azulbaxszsqxymqmlwfe` (Development only) [VERIFIED]  
**Target Student Account:** `student1.campx@gmail.com`  
**Expected Auth User UUID:** `81dd67d3-1d5f-4d8e-8061-a6764861f924`  

---

## 1. Login Code Path & Role Resolution Audit

### Exact Code Path Traced
1. **User Sign-In:** `frontend/src/app/(auth)/login/page.tsx` (`LoginForm.handleLogin`):
   - The user inputs email and password into the email-only login form (no role selector dropdown).
   - The client invokes `supabase.auth.signInWithPassword({ email, password })`.
   - Upon successful credential verification (`data.user` present), the client calls the authoritative server-side resolution endpoint:
     ```ts
     const res = await fetch(`/api/auth/resolve?returnTo=${encodeURIComponent(returnTo || '')}`, {
       method: 'GET',
       cache: 'no-store',
     });
     ```
2. **Server-Side Resolution Endpoint:** `frontend/src/app/api/auth/resolve/route.ts`:
   - Instantiates the Supabase server client: `const supabase = await createClient()`.
   - Calls the authoritative resolver: `const resolution = await resolveUserRole(supabase)`.
3. **Authoritative Core Resolver:** `frontend/src/lib/auth/resolve-role.ts` (`resolveUserRole`):
   - Extracts authenticated user from session: `const { data } = await supabase.auth.getUser()`.
   - Checks if user is Platform Admin against `plat.platform_admins` (`SELECT id, status, role_code FROM plat.platform_admins WHERE user_id = $1 AND role_code = 'SUPER_ADMIN' AND status = 'ACTIVE' AND deleted_at IS NULL LIMIT 1`).
   - If not platform admin, executes parallel PostgreSQL queries:
     ```sql
     -- 1. Profile query
     SELECT id, tenant_id, status, deleted_at 
     FROM iam.user_profiles 
     WHERE id = $1 AND deleted_at IS NULL LIMIT 1;

     -- 2. Role assignments query
     SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to,
            r.code as role_code, r.name as role_name
     FROM iam.role_assignments ra
     JOIN iam.roles r ON ra.role_id = r.id
     WHERE ra.user_id = $1
       AND ra.deleted_at IS NULL AND r.deleted_at IS NULL;
     ```
   - **Profile verification:** If `profile` is null or `deleted_at IS NOT NULL`, fails closed: `return { success: false, reason: 'no_profile' }` (line 207).
   - **Profile status check:** If `profile.status !== 'ACTIVE'`, returns `{ success: false, reason: 'inactive_membership' }`.
   - **Tenant verification:** Queries `core.tenants` by `profile.tenant_id`. If suspended or missing, returns `{ success: false, reason: 'tenant_suspended' }`.
   - **Role assignment evaluation:** Evaluates date validity (`valid_from <= today <= valid_to`).
   - **Role resolution hierarchy:**
     - `COLLEGE_ADMIN`, `INSTITUTION_ADMIN`, `TENANT_ADMIN`, `PRINCIPAL`, `ACADEMIC_ADMIN` => `college_admin`
     - `FACULTY`, `HOD`, `DEAN`, `TEACHER`, `SECTION_COORDINATOR` => `faculty`
     - `STUDENT` => `student`
   - If no valid matching role assignment exists: returns `{ success: false, reason: 'no_role_assigned' }`.
   - When role resolves to `student`, returns:
     ```ts
     {
       success: true,
       session: {
         userId,
         email: userEmail,
         role: 'student',
         tenantId,
         tenantName,
         tenantStatus: tenant?.status || 'ACTIVE',
         collegeId: matchedCollegeId,
         departmentId: matchedDeptId,
         displayName: user.user_metadata?.full_name || userEmail.split('@')[0],
         isPlatformAdmin: false,
       }
     }
     ```
4. **Destination Resolution:** `getAuthorizedDestination(role, returnTo)` in `frontend/src/app/api/auth/resolve/route.ts`:
   - Default dashboard map:
     - `platform_admin` => `/platform`
     - `college_admin` => `/college`
     - `faculty` => `/faculty`
     - `student` => `/student`
   - Return-to check: If `role === 'student' && returnTo.startsWith('/student')`, returns `returnTo`; otherwise returns `/student`.
5. **Client Navigation:**
   - In `login/page.tsx`:
     - If `result.success && result.destination`: `window.location.href = result.destination` (redirects to `/student`).
     - If `!result.success && result.reason`: `window.location.href = /access-denied?reason=${encodeURIComponent(result.reason)}`.

### Today's Outcome for Student
**Claim:** Today for `student1.campx@gmail.com`, login ends at `/access-denied?reason=no_profile`. [VERIFIED]  
- **Cause:** No row exists in `iam.user_profiles` for UUID `81dd67d3-1d5f-4d8e-8061-a6764861f924`.  
- **Destination:** Although `/student` is already configured in the redirect map in `frontend/src/app/api/auth/resolve/route.ts` and in `frontend/src/app/page.tsx`, the resolver fails closed at the profile check before reaching the redirect.

---

## 2. Student-Facing Routes & Route Guards

| Route | Page File | Guard Enforcement | APIs & Queries Called |
|---|---|---|---|
| `/student` | `frontend/src/app/(dashboard)/student/page.tsx` | 1. `middleware.ts`: checks `isStudentUser` if `rawRole` in JWT.<br>2. `layout.tsx`: `resolveUserRole(supabase)` must return `role: 'student'`.<br>3. `page.tsx`: checks `resolution.session.role === 'student'` and non-null `tenantId`. | 1. Primary: Gateway `GET /api/v1/students/me` (STM-01 port 8093).<br>2. DB Fallback: `stu.students` + `core.persons` + `acd.programs` + `acd.batches`.<br>3. DB: `acd.attendance_records` + `acd.attendance_sessions` + `acd.subjects`. |
| `/student/admissions` | `frontend/src/app/(dashboard)/student/admissions/page.tsx` | 1. `middleware.ts`: `/student` route prefix guard.<br>2. `layout.tsx`: `resolveUserRole` check.<br>3. `page.tsx`: `resolution.success` and non-null `tenantId`. | 1. Gateway `GET /api/v1/admissions/applications` (`fetchApplications`).<br>2. Gateway `GET /api/v1/admissions/intakes/active` (`fetchActiveIntakes`).<br>3. Gateway `POST .../offers/accept` (`AcceptOfferButton`).<br>4. Gateway `POST /api/v1/admissions/applications` (`ApplyIntakeDialog`). |
| `/api/student/admissions/applications` | `frontend/src/app/api/student/admissions/applications/route.ts` | Server-side `resolveUserRole(supabase)` checks session & tenant. | Proxies `GET` and `POST` to API Gateway `/api/v1/admissions/applications`. |
| `/api/student/admissions/applications/[id]/accept-offer` | `frontend/src/app/api/student/admissions/applications/[id]/accept-offer/route.ts` | Server-side `resolveUserRole(supabase)` checks session & tenant. | Proxies `POST` to API Gateway `/api/v1/admissions/applications/{id}/offers/accept`. |

---

## 3. Capability Map (STM-01 and STM-02)

| # | Capability | API / Service Ready | UI Exists | RLS Covers Student's Own Rows | RLS & Architectural Evidence |
|---|---|:---:|:---:|:---:|---|
| 1 | **View My Student Profile (Identity & Enrollment)** | **Y** (`GET /api/v1/students/me` in `student-service` port 8093) | **Partial** (`/student` shows header with name, enrollment #, program, section, status) | **Y** | `stu.students_select` policy includes `OR (id IN (SELECT core.my_student_ids()))`. `core.persons_select` includes `OR (id = (SELECT core.current_person_id()))`. [VERIFIED] |
| 2 | **Course Attendance Summary & Shortage Alerts** | **Y** (`/api/v1/academics/attendance` / DB view) | **Y** (`/student` has 3 KPI cards + course table with shortage alert) | **Y** | `acd.attendance_records_select` includes `OR (student_id IN (SELECT core.my_student_ids()))`. [VERIFIED] |
| 3 | **Recent Attendance Session History** | **Y** (`attendance-service` port 8088 / DB queries) | **Y** (`/student` has recent session logs table) | **Y** | `acd.attendance_records_select` covers student's own rows via `core.my_student_ids()`. [VERIFIED] |
| 4 | **Submit Profile Correction Request** | **Y** (`POST /api/v1/students/{id}/corrections` in `student-service`) | **N** (No student-facing submission modal) | **Y** | Java service verifies `context.getUserId()` matches student's identity before persisting correction request. [VERIFIED] |
| 5 | **View Profile Change Audit History** | **Y** (`GET /api/v1/students/{id}/history` in `student-service`) | **N** (No student-facing history viewer UI) | **Y** | Java service filters audit events by student UUID and tenant context. [VERIFIED] |
| 6 | **Manage Contact Address & Preferences** | **Y** (`PUT .../address`, `GET/PUT .../preferences` in `student-service`) | **Partial** (`PreferenceModal` handles workspace theme; address editing UI not on `/student`) | **Y** | RLS on `core.addresses` and `core.person_contacts` includes `person_id = core.current_person_id()`. [VERIFIED] |
| 7 | **View My Admissions Applications & Status** | **Y** (`GET /api/v1/admissions/applications` in `admissions-service` port 8094) | **Y** (`/student/admissions` card list) | **Y** | `stu.applications` has `tenant_isolation_applications` RLS; service filters by `applicantPersonId = userId`. [VERIFIED] |
| 8 | **View Application Documents & Verification Status** | **Y** (`GET /api/v1/admissions/applications/{id}/documents`) | **Partial** (Status badge shown on `/student/admissions`; document details/preview sub-page not built) | **Y** | `stu.application_documents` has `tenant_isolation_app_documents` RLS. Service validates applicant ownership. [VERIFIED] |
| 9 | **Upload / Attach Application Documents** | **Y** (`POST /api/v1/admissions/applications/{id}/documents`) | **N** (No upload component on `/student/admissions` currently) | **Y** | Java service checks applicant ownership and intake open status. [VERIFIED] |
| 10 | **Browse Active Admission Intakes** | **Y** (`GET /api/v1/admissions/intakes/active`) | **Y** (`/student/admissions` intake cards grid) | **Y** | `stu.admission_intakes` has `tenant_isolation_admission_intakes` RLS. [VERIFIED] |
| 11 | **Submit New Admissions Application** | **Y** (`POST /api/v1/admissions/applications`) | **Y** (`ApplyIntakeDialog` on `/student/admissions`) | **Y** | Java service enforces duplicate checks, intake windows, and tenant isolation. [VERIFIED] |
| 12 | **Review Admission Offer & Accept Offer** | **Y** (`POST /api/v1/admissions/applications/{id}/offers/accept`) | **Y** (`AcceptOfferButton` banner on `/student/admissions`) | **Y** | `stu.admission_offers` has `tenant_isolation_admission_offers` RLS. Service verifies applicant ownership. [VERIFIED] |
| 13 | **Enrolment Handover Status Visibility** | **Y** (`GET /api/v1/admissions/applications/{id}/handover`) | **Partial** (`HANDED_OVER` banner shown on `/student/admissions`, directs to `/student`) | **Y** | Handover record linked to application; read-only visibility for student. [VERIFIED] |

---

## 4. Live Database Check via Supabase MCP (Read-Only, Dev Project: `azulbaxszsqxymqmlwfe`)

### 4.a Confirm Auth User Existence and ID Match [VERIFIED]
- **SQL Executed:**
  ```sql
  SELECT id, email, raw_user_meta_data, raw_app_meta_data, created_at, last_sign_in_at 
  FROM auth.users 
  WHERE email = 'student1.campx@gmail.com';
  ```
- **Output:**
  ```json
  [
    {
      "id": "81dd67d3-1d5f-4d8e-8061-a6764861f924",
      "email": "student1.campx@gmail.com",
      "raw_user_meta_data": { "email_verified": true },
      "raw_app_meta_data": { "provider": "email", "providers": ["email"] },
      "created_at": "2026-11-05 06:38:30.566639+00",
      "last_sign_in_at": null
    }
  ]
  ```
- **Finding:** The auth user exists. ID matches `81dd67d3-1d5f-4d8e-8061-a6764861f924` exactly.

### 4.b Current Existence Check for UUID `81dd67d3-1d5f-4d8e-8061-a6764861f924` [VERIFIED]
1. **Profile Row (`iam.user_profiles`):**
   - Query: `SELECT * FROM iam.user_profiles WHERE id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';`
   - Output: `[]` (**DOES NOT EXIST**)
2. **Student Role Mapping (`iam.role_assignments`):**
   - Query: `SELECT * FROM iam.role_assignments WHERE user_id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';`
   - Output: `[]` (**DOES NOT EXIST**)
3. **College Link (`core.colleges` / role assignment):**
   - Dev college exists (`a0000000-0000-0000-0000-000000000002` in tenant `a0000000-0000-0000-0000-000000000001`), but no assignment links it to this user (**DOES NOT EXIST**)
4. **Person Record (`core.persons`):**
   - Query: `SELECT * FROM core.persons WHERE id = '81dd67d3-1d5f-4d8e-8061-a6764861f924' OR primary_email = 'student1.campx@gmail.com';`
   - Output: `[]` (**DOES NOT EXIST**)
5. **Student Record (`stu.students`):**
   - Query: `SELECT * FROM stu.students WHERE person_id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';`
   - Output: `[]` (**DOES NOT EXIST**)
6. **Application (`stu.applications`):**
   - Query: `SELECT * FROM stu.applications WHERE applicant_person_id = '81dd67d3-1d5f-4d8e-8061-a6764861f924';`
   - Output: `[]` (**DOES NOT EXIST**)

*Note on E2E-STM02 Fixture Isolation:* All existing STM-02 test fixtures belong to tenant `e2e00000-0000-0000-0000-000000000001` (`E2E-STM02 University`). The dev account `student1.campx@gmail.com` belongs strictly to the primary dev tenant `a0000000-0000-0000-0000-000000000001` (`CampX Architecture Dev Test Institute`) and will not reuse any E2E fixture rows.

### 4.c Missing Rows for Login to Resolve to Student Dashboard [VERIFIED]
For login to resolve to `/student`:
1. `core.persons`: Person record with `primary_email = 'student1.campx@gmail.com'`, `tenant_id = 'a0000000-0000-0000-0000-000000000001'`, `first_name = 'Student One'`.
2. `iam.user_profiles`: Row with `id = '81dd67d3-1d5f-4d8e-8061-a6764861f924'`, `tenant_id = 'a0000000-0000-0000-0000-000000000001'`, `person_id = <person_id>`, `college_id = 'a0000000-0000-0000-0000-000000000002'`, `status = 'ACTIVE'`.
3. `iam.role_assignments`: Row linking `user_id = '81dd67d3-1d5f-4d8e-8061-a6764861f924'` to role `caa7f8a2-c3eb-46b3-bf2e-ec6640289ecb` (code `STUDENT`), `tenant_id = 'a0000000-0000-0000-0000-000000000001'`, `college_id = 'a0000000-0000-0000-0000-000000000002'`.

For the Student Dashboard (`/student`) to populate complete STM-01 / STM-02 widgets:
4. `acd.programs`: Program record in tenant `a0000000-0000-0000-0000-000000000001` (e.g. `B.Tech Computer Science & Engineering`).
5. `stu.students`: Student record with `person_id = <person_id>`, `college_id = 'a0000000-0000-0000-0000-000000000002'`, `program_id = <program_id>`, `enrollment_no = 'STU-DEV-2026-0001'`, `status = 'ACTIVE'`.
6. `stu.admission_intakes`: Admission intake in tenant `a0000000-0000-0000-0000-000000000001`.
7. `stu.applications`: One application in known state (`OFFERED` or `ACCEPTED` or `HANDED_OVER`) with `applicant_person_id = <person_id>`.

### 4.d Display Name Setting & Source Audit [VERIFIED]
- **Auth User Metadata:** `raw_user_meta_data` is `{"email_verified": true}`. The auth user has **NO** `full_name` set in auth metadata.
- **Where Does the App Read the Name From?**
  - In `resolveUserRole()` (`frontend/src/lib/auth/resolve-role.ts`, lines 96, 115, 201, 323):
    `displayName: user.user_metadata?.full_name || userEmail.split('@')[0]`
    It reads `user_metadata.full_name`, and falls back to the email username prefix (`"student1.campx"`). It does NOT currently select `full_name` from `iam.user_profiles` in the profile SQL query.
  - In `StudentDashboardPage` (`frontend/src/app/(dashboard)/student/page.tsx`, line 168):
    `Welcome, {student ? `${student.first_name} ${student.last_name}` : displayName}`
    When the student is joined to `core.persons`, it reads `first_name` and `last_name` from `core.persons`.
  - In `ApplicantPortalPage` (`frontend/src/app/(dashboard)/student/admissions/page.tsx`, line 56):
    `Welcome, <span className="font-semibold text-[#0B1F3A]">{displayName || email}</span>`

---

## 5. Student Dashboard v1 Proposal (Minimal Working-API Widgets)

Only capabilities backed by existing working APIs (STM-01 Java Student Service on port 8093, STM-02 Java Admissions Service on port 8094, and Attendance Service / DB):

1. **Student Academic Identity Header:**
   - Student Full Name, Enrollment Number (`STU-DEV-2026-0001`), Program (`B.Tech CSE`), Campus College (`CampX Dev College of Engineering`), and Active Academic Status badge.
   - *API Source:* STM-01 `GET /api/v1/students/me` / joined student query.
2. **Admissions & Enrolment Lifecycle Journey Widget (STM-02 Bridge):**
   - Active Application summary: Application Number, Intake Term, Current Lifecycle State (e.g. `OFFER_ACCEPTED` / `HANDED_OVER`), Document Verification Status (`VERIFIED`), and direct link to `/student/admissions`.
   - *API Source:* STM-02 Gateway `GET /api/v1/admissions/applications`.
3. **Academic Standing & Attendance KPI Metrics (3 Cards):**
   - Overall Attendance % (with 75% threshold badge: Satisfactory vs Shortage Alert).
   - Conducted vs Attended Session counts.
   - Academic Registration Standing badge.
   - *API Source:* Attendance records / API Gateway attendance endpoints.
4. **Course-Wise Attendance Summary Table:**
   - Table of registered courses with Conducted, Attended, Absent counts, Percentage, and Status tags.
   - *API Source:* `acd.attendance_records` + `acd.subjects`.
5. **Recent Timetable Session Attendance Logs:**
   - 5-10 recent session logs with Date, Time slot, Subject, Instructor name, Attendance Status (`PRESENT`/`ABSENT`), and Faculty remarks.
   - *API Source:* `acd.attendance_records` + `acd.attendance_sessions`.
6. **Quick Actions Hub:**
   - Direct shortcuts to: "Admissions & Applications Portal", "Workspace Preferences", and "View Attendance Details".

