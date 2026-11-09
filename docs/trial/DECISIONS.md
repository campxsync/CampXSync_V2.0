# CampXSync — Architecture & Implementation Decisions Register (Trial Baseline)

**Document:** `docs/trial/DECISIONS.md`  
**Branch:** `ReviewBranch`  
**Target Environment:** Supabase Staging (`kpwdvvcschankgggitnj`) & Vercel Deployments  
**Authority Order:**
1. Environment-safety and production-isolation rules (Prompt Section 1)
2. Current approved architecture decisions and security/data-ownership policies
3. Repository implementation, migration history, and test evidence
4. Canonical service specifications (AllModules)
5. Superseded/duplicate designs and diagrams
6. AI-generated interpretations or unverified status claims

---

## Decision Entries

### DEC-01: Multi-Schema Supabase/PostgreSQL vs Outdated Spec Infrastructure (MongoDB, Kafka, Redis)
- **Context:** AllModules canonical service specifications describe legacy architectures involving MongoDB, Redis, and Apache Kafka.
- **Resolution:** As ratified in the project governance baseline, the active source of truth is Supabase/PostgreSQL with a consolidated multi-schema architecture (`core`, `iam`, `acd`, `stu`, `hrm`, `audit`, `adm`). All persistence, row-level security, constraints, and audit logging execute directly inside PostgreSQL. Asynchronous message buses (Kafka) are mapped to PostgreSQL transactional outbox tables and `audit.events`. No Redis or MongoDB brokers are created for the MVP slice.
- **Repository Evidence:**
  - `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/` (schemas, tables, functions, RLS).
  - `Documentation/relational_constraint_index_catalog.md`.
  - Staging database schema catalog (`core`, `iam`, `acd`, `stu`, `hrm`, `audit`) verified live on `kpwdvvcschankgggitnj`.

---

### DEC-02: Consolidated Learning Resource Master (`lms.resources` vs ACD-08)
- **Context:** ACD-08 and LMS-07 both specify learning resource management, introducing duplicate entity risk.
- **Resolution:** A single consolidated resource repository is defined in table `lms.resources` within the Supabase PostgreSQL database. ACD-08 references resource identity from `lms.resources` without creating a duplicate academic resource master.
- **Repository Evidence:**
  - Staging table `lms.resources` schema definition.
  - Foreign key and cross-schema reference policies in Supabase Staging.

---

### DEC-03: Academic Course Management (`acd.courses`) vs Delivery Offerings (`acd.course_offerings`)
- **Context:** ACD-01 (Course Management) overlaps conceptually with LMS-01 course delivery.
- **Resolution:** `acd.courses` holds canonical, tenant-scoped academic course master records. Course delivery sessions and terms bind to `acd.course_offerings` and `acd.course_sections`. For the MVP slice, batch scheduling and attendance bind directly from `acd.course_sections` to timetable entries and attendance sessions.
- **Repository Evidence:**
  - `acd.courses` and `acd.course_sections` table schemas in Supabase staging.
  - Foreign key constraints: `fk_timetable_entries_section_id` links `acd.timetable_entries` to `acd.course_sections`.

---

### DEC-04: Authoritative Subject Master (`acd.subjects`)
- **Context:** ACD-03 and LMS-03 both defined subject management structures in legacy specifications.
- **Resolution:** A single authoritative subject master is maintained in `acd.subjects`. Timetable entries and batch curricula reference `acd.subjects(id)` with composite tenant scoping `(tenant_id, subject_id)`. LMS-03 acts solely as a learning projection.
- **Repository Evidence:**
  - Staging table `acd.subjects` with composite unique/foreign keys.
  - `fk_timetable_entries_subject_id` references `acd.subjects(tenant_id, id)`.

---

### DEC-05: Payroll Transaction Ownership (HRM-05 vs FIN-07)
- **Context:** FIN-07 (Finance Payroll) and HRM-05 (Compensation and Payroll) overlap in salary disbursement specifications.
- **Resolution:** HRM-05 is designated as the primary transaction owner for compensation structures, allowances, deductions, and payslip generation. FIN-07 acts as a general ledger accounting projection for financial statement roll-ups and tax withholdings.
- **Repository Evidence:**
  - `docs/reference/product_lifecycle.md` Section 5.5 & Section 19.
  - Staging schema isolation between `hrm` and `fin`.

---

### DEC-06: Recruitment & Onboarding Specification Numbering Split (HRM-06 & HRM-07)
- **Context:** Both `HRM-06.docx` and `HRM-07.docx` opened with recruitment and onboarding terminology in the archive.
- **Resolution:** Canonical domain scope is split: `HRM-06` is designated as Applicant Tracking & Recruitment (requisitions, candidate pipeline, interviews, offers); `HRM-07` is designated as Employee Induction & Onboarding (document collection, statutory compliance, asset allocation).
- **Repository Evidence:**
  - `docs/reference/product_lifecycle.md` Section 5.6 & Section 19.

---

### DEC-07: Communication & Announcements Source Package Gap (CAM)
- **Context:** The `Communication&AnnnouncementModule` directory in the design package archive was completely empty.
- **Resolution:** Formally recorded as a source package gap rather than fabricating unreviewed code. CAM is deferred to Phase 5 of the product lifecycle roadmap, with explicit requirements for multichannel delivery, template management, and recipient scoping defined in `docs/trial/POST_TRIAL_BACKLOG.md`.
- **Repository Evidence:**
  - `docs/reference/product_lifecycle.md` Section 5.7 & Appendix A.

---

### DEC-08: Platform Administration vs College Tenant Boundaries
- **Context:** Administration is divided into Platform Tier (CampSync multi-tenant operator) and College Tier (individual institute tenant).
- **Resolution:** 
  - Platform Admin operates at `tenant_id IS NULL`. Governed by `iam.is_platform_admin()`. Manages `core.tenants`, `iam.role_templates`, and platform-wide audit. Platform Admin has zero read access to tenant student/faculty operational rows (HTTP 403).
  - College Admin operates strictly within a single tenant boundary where `tenant_id = core.current_tenant_id()`. Manages departments, user roles, faculty, students, timetables, and attendance corrections.
- **Repository Evidence:**
  - RLS policies on `core.tenants` (`tenants_platform` vs `tenants_read`).
  - Unit tests in `services/institute-admin-service/src/test/java/com/campx/admin/institute/security/PlatformTierIsolationRegressionTest.java`.
  - Database function `iam.is_platform_admin()`.
  - Phase 9 Automated Acceptance Suite (`scripts/verify_phase9_security_browser.mjs` Tests 6, 7, 13).

---

### DEC-09: Canonical Specification Versioning (LMS-01 v2.0 vs v1.0)
- **Context:** The source archive contained both `LMS-01.docx` (v1.0) and `LMS-01-v2.0.docx`.
- **Resolution:** `LMS-01-v2.0.docx` is designated as the canonical specification. `LMS-01.docx` is formally marked superseded in the canonical source-of-truth register.
- **Repository Evidence:**
  - `docs/trial/ARCHITECTURE_TRACEABILITY.md` Section 2.
  - `docs/reference/product_lifecycle.md` Appendix A.

---

### DEC-10: Traceability Matrix Distinguishing Specified vs Implemented
- **Context:** Service specifications describe expansive capabilities (62 modules) that may not yet exist in the MVP code.
- **Resolution:** To prevent false completion claims while ensuring zero architecture is lost, all 62 modules are tracked in `docs/trial/ARCHITECTURE_TRACEABILITY.md` with explicit statuses (`Staging-verified`, `Partial`, or `Specified, not in MVP slice`).
- **Repository Evidence:**
  - `docs/trial/ARCHITECTURE_TRACEABILITY.md`.

---

### DEC-11: Student Master Contract (`stu.students` + `core.persons`)
- **Context:** Student management specification is absent from the AllModules design archive.
- **Resolution:** The slice relies on the implemented database contract in schema `stu` and `core`:
  - `core.persons` holds base human identity (`first_name`, `last_name`, `email`, `phone`, `gender`, `date_of_birth`).
  - `stu.students` anchors academic student profile (`tenant_id`, `person_id`, `college_id`, `program_id`, `enrollment_no`, `status`).
  - Batch membership is stored in `acd.batch_students(tenant_id, batch_id, student_id)`.
  - Attendance records reference `stu.students(tenant_id, id)` via `fk_attendance_records_student_id`.
- **Repository Evidence:**
  - Live staging table `stu.students` and `core.persons`.
  - Foreign key constraint `fk_attendance_records_student_id` in `acd.attendance_records`.
  - Foreign key constraint `fk_batch_students_student_id` in `acd.batch_students`.

---

### DEC-12: Global Shared Capabilities Platform Baseline
- **Context:** Core infrastructure capabilities (IAM, audit ledger, workflow outbox, preferences) were scattered across services.
- **Resolution:** Consolidated into dedicated PostgreSQL schemas `iam` and `audit` with authoritative server-side execution:
  - `iam.user_profiles` with typed JSONB `preferences` (v1 schema with allowlist validation).
  - `audit.events` and `audit.change_log` recording immutable actor, timestamp, reason, and state transitions.
- **Repository Evidence:**
  - Staging tables `iam.user_profiles`, `audit.events`, `audit.change_log`.
  - Frontend preference API `/api/user/preferences`.

---

### DEC-13: Frontend Architecture and Vercel Deployment Strategy
- **Context:** The repository currently has a monolithic Vite SPA in `frontend-web/` connecting to local Java microservices via `http://localhost:8080`. The MVP must be trial-ready and live on Vercel with server-side route guards, role resolution, and direct Supabase staging persistence.
- **Comparison & Rationale:**
  - *Option 1: Existing Vite SPA (`frontend-web/`) on Vercel:*
    - Vercel can host static Vite bundles, but the Java API Gateway and Spring Boot microservices (`services/*`) cannot run on Vercel serverless without external container hosting.
    - Direct browser-to-Supabase communication from Vite executes entirely client-side. Server-side page route protection (`/platform`, `/college`, `/faculty`), HTTP-only cookie session persistence, and server action validations are not possible in a client-only SPA without an extra Node/Edge server layer.
  - *Option 2: Next.js App Router (`frontend/`):*
    - Natively supported and optimized on Vercel.
    - Provides Edge/Node Middleware for true server-side route guards before serving any protected HTML, preventing client-side route tampering.
    - Uses `@supabase/ssr` for secure cookie-based session persistence across full page reloads.
    - Directly queries Supabase PostgreSQL (via Supabase client with user JWT), executing RLS policies authoritatively in staging.
    - Fulfills Section 5 requirements: Design tokens, Tailwind CSS, Accessible forms (react-hook-form + zod), and Recharts.
- **Resolution:** Build the production-ready Next.js application in `frontend/` while preserving the existing `frontend-web/` and Java microservices untouched. This provides the smallest, most secure, and most deployable path to a live Vercel MVP.
- **Repository Evidence:**
  - `frontend-web/` inspection showing monolithic client-only Vite SPA.
  - Java microservices requiring `localhost:8080` (incompatible with serverless Vercel hosting).
  - Vercel deployment requirements in prompt Section 4 (Phase 1, 2, 10).

---

### DEC-14: Statutory Attendance Shortage Threshold (75%) Baseline Assumption
- **Context:** CampXSync MVP Master Prompt V4 Phase 6 specifies: "Shortage detection updates the summary flag using the configured threshold. If none is configured, use 75% and record it in DECISIONS.md as an assumption."
- **Resolution:** In the absence of institution-specific custom academic regulatory overrides in database configuration for the current semester, a statutory shortage threshold of **75.0%** (`SHORTAGE_THRESHOLD = 75`) is enforced across all institutional attendance analytics, student compliance tracking, and administrative oversight drawers.
- **Rules Enforced:**
  - Attended marks are computed as `PRESENT`, `LATE`, and `ON_DUTY` per `acd.v_attendance_summary`.
  - Non-attended marks include `ABSENT` and `LEAVE`.
  - Cumulative attendance percentage is calculated as `round((attended / total) * 100)`.
  - Any student whose cumulative attendance falls strictly below 75% is flagged with `hasShortage: true` and triggers shortage warnings in reporting interfaces and administrative drawers.
- **Repository Evidence:**
  - `frontend/src/app/api/college/attendance/report/route.ts` (enforcing statutory `SHORTAGE_THRESHOLD = 75`).
  - `frontend/src/app/(dashboard)/college/attendance/attendance-manager.tsx` (shortage filter toggle and badge alerts).
  - Database analytical view `acd.v_attendance_summary`.

---

### DEC-15: RFC 7807 Problem Details and Direct DB Pooler Fallback for Role Resolution
- **Context:** Supabase Auth issues JWTs directly from client authentication, but default PostgREST RLS on `iam.role_assignments` may restrict authenticated users from reading their own role mappings prior to custom claims propagation.
- **Resolution:**
  - In `frontend/src/lib/auth/resolve-role.ts`, implement an authoritative fallback querying the Supabase Staging PostgreSQL connection pool directly using secure server-side credentials when PostgREST RLS returns empty assignments for a verified Supabase Auth user (`campxsync@gmail.com`).
  - Standardize all API error responses to RFC 7807 Problem Details (`application/problem+json`) with standard fields: `type`, `title`, `status`, `detail`, `instance`.
- **Repository Evidence:**
  - `frontend/src/lib/auth/resolve-role.ts` (direct DB pooler query fallback).
  - `frontend/src/middleware.ts` (RFC 7807 error format on fast-fail security guards).
  - Phase 9 Security Suite (`scripts/verify_phase9_security_browser.mjs` Tests 4, 5, 6, 7).
