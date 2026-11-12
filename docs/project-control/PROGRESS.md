# CampXSync Java Backend Integration — Progress

**Updated:** 2026-11-07  
**Branch:** `feature/java-backend-integration`

> Based on user-provided execution reports and recorded context; not an independent re-verification of current repository or live database.

## Current checkpoint
- Last recorded committed Wave 0 baseline: `105122b6b5b17d3eadc70e7e8094008facb4285a`.
- Waves 1–10 complete/accepted; their uncommitted changes must be preserved.
- **Wave 10 Status:** **COMPLETE** (Frontend direct database-access retirement across 10 API routes and dashboard pages, production test session / mock impersonation gate hard-disabled, integrated cross-service regression verified across all 6 services with 345/345 tests passing, TypeScript compilation 0 errors).
- **Controller status:** `STOPPED_AT_WAVE_10`. Next wave (Wave 11 Production Readiness & Cutover) is strictly out of scope and halted until explicitly authorized.
- **STM-03 (Student track):** Targeted completion + live JDBC/HTTP hardening recorded in `docs/project-control/STM-03_TARGETED_COMPLETION_REPORT.md` and `docs/project-control/STM-03_HARDENING_AND_STM-04_COMPLETION_REPORT.md`. Live JDBC 1/1 PASS (rollback); HTTP E2E 1/1 PASS.
- **STM-04 (authorized 2026-11-07):** `lifecycle-service` :8096 covers status, leave, reinstatement, transfer, withdrawal, graduation, reports, and advisor assignment. Unit tests extended. Live JDBC suite for lifecycle is still not run. Kafka/notifications broker and STM-07/08/10 links remain external. See hardening/STM-04 report.
- Revised sequence: W4 Student (COMPLETE) → W5 Timetable (COMPLETE) → W6 Attendance (COMPLETE) → W7 Corrections (COMPLETE) → W8 Dashboards (COMPLETE) → W9 Onboarding (COMPLETE) → W10 Frontend DB retirement/E2E (COMPLETE) → W11 Production readiness (HALTED).
- No reset, clean, stash, branch switch, commit or push without explicit user authorization.

## Wave 1 — Shared Audit & Outbox Foundation
**Reported status:** Accepted with explicit limitations. Logger tests 58/58; `mvn clean install -pl logger` reported successful; downstream test compilation for `api-gateway` and `services/attendance-service` reported successful. Six correctness fixes were reported. Live PostgreSQL/RLS integration was not verified; background outbox relay remains deferred.

## Wave 2 — Preferences
**Reported status:** Complete with limitations. Preferences 26/26; gateway routing 6/6; logger 58/58; institute-admin tests 79/79; full `mvn test-compile` reported successful across 15 modules. Live DB integration and real Intelligence Layer endpoint remain pending/unverified.

## Wave 3 — Academic Departments and Programs
**Reported status:** Complete with live integration unverified. Reported: shared services 11 tests, controller 14, gateway 7, CollegeAdminService 39, tenant isolation regression 15, downstream security 7, API gateway 155; `mvn test-compile` reported successful across 15 modules. No production or legacy staging DB connection/writes were reported. Consult the Wave 3 completion report for details.

## Gates
- **G1 least-privilege DB role:** PASSED. Implemented and supported by live non-production test evidence on development ref `azulbaxszsqxymqmlwfe`:
  1. Dedicated unprivileged application role `campx_app_server` active (`LOGIN`, `NOSUPERUSER`, `NOBYPASSRLS`, `CONNECTION LIMIT 25`, membership in `authenticated`).
  2. `FORCE ROW LEVEL SECURITY` verified live on all Tier 1 Core tables (`stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students`).
  3. Least-privilege grant matrix applied: schema `USAGE` to `campx_app_server`, table DML to `authenticated`, `SELECT, INSERT` on `audit.events` to `authenticated`.
  4. Append-only ledger enforced: `UPDATE` and `DELETE` on `audit.events` denied (`SQLSTATE 42501`).
  5. RLS policy `events_insert` on `audit.events` active and verified.
  6. Live multi-tenant cross-boundary isolation verified: positive path succeeds, cross-tenant audit & student inserts denied, missing context fails closed.
  Student-domain persistence writes are now unblocked.
- **G2 isolated PostgreSQL CI suite:** OPEN / to be assessed by end of Wave 5. No claim that CI is already configured.
- **G3 JDK/framework assessment:** Not started; due before Wave 11 exit. Java 8 unchanged unless separately approved.

## Wave 4 — Student Domain Foundation & Roster
**Current status:** **COMPLETE (VERIFIED LIVE)** (Stage A: G1 Security Remediation PASSED; Stage B: Java Foundation & Gateway Routes Complete; Stage C: PostgreSQL Persistence, Mock JDBC Repository, and Frontend Roster Cutover Complete; Stage D: Live Application Role Authentication Verified; Live Student/Person Persistence Verified; Decision Gate DEC-W4-OUTBOX Applied; Outbox Live Persistence Verified Live on `azulbaxszsqxymqmlwfe`).
- Audit report: `docs/reports/wave-4/STUDENT_DATABASE_RLS_AUDIT.md` (Phase 1).
- Remediation proposal & verified executions: `docs/reports/wave-4/G1_SECURITY_REMEDIATION_PROPOSAL.md` (Stage A, G1 PASSED).
- Student Model Proposal: `docs/reports/wave-4/STUDENT_MODEL_PROPOSAL.md` (Approved under `APPROVE-WAVE4-STUDENT-MODEL-ALL`).
- Java Backend Module: `services/student-service` implemented with HikariCP `DatabaseConfig`, `DatabaseConnectionManager`, `UserSecurityContext.executeInTransaction`, `PostgresStudentRepository`, `PostgresPersonRepository`, `PostgresGuardianshipRepository`.
- API Gateway: Route mappings for `/api/v1/students`, `/v1/students`, `/v1/student-roster` active in `GatewayConfig.java` (verified via `StudentGatewayRouteTest`: 1/1 PASS).
- Unit & Mock Suite: **39/39 tests PASS** in `student-service` (`mvn test -pl services/student-service "-Dtest=!Live*"`).
- Live Database Execution Status on dev ref `azulbaxszsqxymqmlwfe`:
  - `LiveApplicationRoleAuthTest` (1/1 PASS, Verified Live): Application role `campx_app_server` authentication verified.
  - `LiveStudentRuntimeSecurityTest` (3/3 PASS, Verified Live): Live catalog checks on `azulbaxszsqxymqmlwfe` verified (direct app role auth, runtime security path, outbox catalog privileges/policies).
  - `LiveOutboxGrantsExecutionTest` (1/1 PASS, Verified Live): Owner-authorized DEC-W4-OUTBOX migration executed successfully, establishing schema `USAGE ON SCHEMA sys TO authenticated, campx_app_server`, table DML on `sys.outbox_events`, and 4 tenant-scoped RLS policies.
  - `LivePostgresStudentPersistenceTest` (2/2 PASS, Verified Live): End-to-end photo document, person, student, and outbox persistence verified within single transaction; joined read-back verified; same-transaction outbox read-back with payload verification verified; cross-tenant read denial verified (0 rows); cross-tenant write denial verified (RLS violation); live failure rollback verified; unconditional rollback leaving 0 persistent rows verified.
- Minimal Grant Migration Applied: `docs/reports/wave-4/g1-proposed-sql/04_outbox_least_privilege_grants.sql` via `LiveOutboxGrantsExecutionTest`.
- Completion report: `docs/reports/wave-4/COMPLETION_REPORT.md` (Status: COMPLETE).

## Wave 4 closeout / Decision Gate Outcome
1. **Decision Gate 1 (DEC-G1-04 - Credential Provisioning):** **PASSED**. Unprivileged role `campx_app_server` credential configured and verified live against `azulbaxszsqxymqmlwfe` via `LiveApplicationRoleAuthTest` (1/1 PASS).
2. **Decision Gate 2 (DEC-W4-OUTBOX - Outbox Schema Grant & RLS Policies):** **PASSED & VERIFIED LIVE**. Owner authorized execution of `04_outbox_least_privilege_grants.sql` via `LiveOutboxGrantsExecutionTest` (1/1 PASS) granting `USAGE ON SCHEMA sys TO authenticated, campx_app_server`, table DML on `sys.outbox_events`, and establishing 4 tenant-scoped RLS policies.
3. `LivePostgresStudentPersistenceTest` re-run: **2/2 PASS**. All Wave 4 acceptance criteria satisfied.

## Wave 5 — Timetable Domain & Relational Persistence
**Current status:** **COMPLETE (VERIFIED LIVE)** (Relational persistence, GiST conflict exclusion constraints, outbox emission, RLS isolation, gateway routing, and frontend cutover verified live on `azulbaxszsqxymqmlwfe`).
- **Java Domain Service:** `services/timetable-management-service` (ACD-05) implemented with HikariCP `DatabaseConfig`, `DatabaseConnectionManager`, `UserSecurityContext`, and `PostgresTimetableRepository`.
- **Relational Domain Models:** `TimetableEntity`, `TimetableVersionEntity`, `TimetableEntryEntity`, DTOs (`CreateTimetableSlotRequest`, `TimetableSlotResponse`).
- **Conflict Handling:** Atomic PostgreSQL GiST exclusion constraints (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) mapped to HTTP 409 `TimetableConflictException`.
- **API Gateway:** Routes `/api/v1/academics/timetables/**` and `/api/v1/timetables/**` dispatched to ACD-05 on port 8087 (verified via `TimetableGatewayRouteTest`: 1/1 PASS; gateway unit suite: 154/154 PASS).
- **Frontend Cutover:** `frontend/src/app/api/college/timetable/entries/route.ts` cut over from direct DB queries (`lib/db.ts`) to API Gateway proxy (`tsc --noEmit` exited 0).
- **Live Database Execution Status on dev ref `azulbaxszsqxymqmlwfe`:**
  - `LiveTimetableCatalogAuditTest` (1/1 PASS, Verified Live): Live catalog schema, columns, RLS enabled, and GiST indexes verified.
  - `LivePostgresTimetablePersistenceTest` (2/2 PASS, Verified Live):
    - Faculty double-booking rejected live by PostgreSQL GiST exclusion constraint `ex_tte_employee`.
    - Section double-booking rejected live by PostgreSQL GiST exclusion constraint `ex_tte_section`.
    - Live relational persistence for timetables, timetable versions, and slots under authenticated RLS role verified.
    - Transactional outbox event (`sys.outbox_events`) emitted and verified within same transaction.
    - Persisted read-back with multi-table JOINs (`acd.subjects`, `acd.course_sections`) verified.
    - Cross-tenant read denial under synthetic Tenant B claims verified (0 rows returned).
    - Unconditional rollback verified leaving zero residual test rows in development database.
- **Unit Suite:** **38/38 tests PASS** in `timetable-management-service` (`mvn test -pl services/timetable-management-service "-Dtest=!Live*"`).
- **Completion Report:** `docs/reports/wave-5/COMPLETION_REPORT.md` (Status: COMPLETE).

## Wave 6 — Attendance Domain & Session Lifecycle
**Current status:** **COMPLETE (VERIFIED LIVE)** (Attendance session lifecycle, relational persistence, duplicate constraints, transactional outbox emission, RLS isolation, gateway routing, and frontend cutover verified live on `azulbaxszsqxymqmlwfe`).
- **Java Domain Service:** `services/attendance-service` (ACD-06) enhanced with HikariCP `DatabaseConfig`, `DatabaseConnectionManager`, `UserSecurityContext`, and `PostgresAttendanceRepository`.
- **Relational Domain Models:** `AttendanceSessionEntity`, `AttendanceRecordEntity`, `AttendanceCorrectionEntity`, DTOs (`CreateAttendanceSessionRequest`, `MarkAttendanceRequest`).
- **Duplicate Prevention:**
  - Session level: unique constraint on `(tenant_id, section_id, session_date, start_time)` verified live.
  - Record level: unique constraint `uq_attendance_records_tenant_id_session_id_student_id` preventing duplicate marking per session verified live.
- **API Gateway:** Routes `/api/v1/academics/attendance/**` routed to ACD-06 on port 8086 with claims propagation (verified via `GatewayAttendanceIntegrationTest`: 3/3 PASS; gateway unit suite: 154/154 PASS).
- **Frontend Cutover:** `frontend/src/app/api/college/attendance/sessions/route.ts` and `frontend/src/app/api/college/attendance/records/route.ts` cut over from direct DB access to API Gateway proxy (`tsc --noEmit` exited 0).
- **Live Database Execution Status on dev ref `azulbaxszsqxymqmlwfe`:**
  - `LiveAttendanceCatalogAuditTest` (1/1 PASS, Verified Live): Schema, RLS, and grants on `acd.attendance_sessions`, `acd.attendance_records`, `acd.attendance_corrections` verified.
  - `LivePostgresAttendancePersistenceTest` (1/1 PASS, Verified Live):
    - Session insertion under authenticated RLS role verified.
    - Duplicate session rejected by PostgreSQL unique constraint.
    - Student attendance marked under session.
    - Duplicate attendance record rejected by PostgreSQL unique constraint.
    - Outbox events (`attendance.session_created.v1`, `attendance.marked.v1`) persisted within same transaction.
    - Committed read-back verified.
    - Cross-tenant read denial under synthetic Tenant B claims verified (0 rows returned).
    - Unconditional rollback verified leaving zero residual test rows.
- **Unit Suite:** **30/30 tests PASS** in `attendance-service` (`mvn test -pl services/attendance-service "-Dtest=!Live*"`).
- **Completion Report:** `docs/reports/wave-6/COMPLETION_REPORT.md` (Status: COMPLETE).

## Wave 7 — Attendance Corrections
**Current status:** **COMPLETE (VERIFIED LIVE)** (Governed attendance correction lifecycle, requester != approver separation, self-approval prevention at app & DB trigger layers, append-only audit trail in `acd.attendance_corrections`, optimistic locking via `row_version`, transactional outbox emission, frontend route cutover, and live verification on `azulbaxszsqxymqmlwfe`).
- **Java Domain Service:** `services/attendance-service` (ACD-06) enhanced with correction domain logic, DTOs, and RBAC guards.
- **Relational Domain Models & Persistence:**
  - `PostgresAttendanceRepository`: Implemented `requestCorrection`, `decideCorrection`, `findCorrectionById`, `findCorrectionsBySessionId`, `findCorrectionsByRecordId`.
  - Self-approval prevention: fails closed with `AttendanceCorrectionForbiddenException` (HTTP 403) if requester attempts approval.
  - Concurrency & idempotency: replay decision on non-`PENDING` correction throws `IllegalStateException`.
  - Optimistic locking: updates `acd.attendance_records` status, remarks, and increments `row_version = row_version + 1`.
  - Transactional outbox events: emits `attendance.correction_requested.v1`, `attendance.correction_applied.v1`, and `attendance.correction_rejected.v1` into `sys.outbox_events` in the same transaction.
- **Controller & Endpoints:**
  - `POST /corrections`: creates/submits correction request.
  - `POST /corrections/{id}/decide`: handles decision (APPROVED/REJECTED) with RBAC validation.
  - `GET /corrections/{id}` and `GET /sessions/{id}/corrections`: queries audit history.
  - RBAC: `COLLEGE_ADMIN` full authority; `HOD` permitted; `FACULTY` strictly forbidden from deciding corrections.
- **Frontend Cutover:** `frontend/src/app/api/college/attendance/corrections/route.ts` cut over from direct DB query (`lib/db.ts`) to API Gateway proxy (`/api/v1/academics/attendance/corrections`). TypeScript check (`tsc --noEmit`) exited 0.
- **Live Database Execution Status on dev ref `azulbaxszsqxymqmlwfe`:**
  - `LivePostgresAttendanceCorrectionsTest` (1/1 PASS, Verified Live):
    - Relational fixtures provisioned in transaction under authenticated RLS role.
    - Initial attendance record marked (ABSENT, v1).
    - Correction request created in PENDING status.
    - Transactional outbox event (`attendance.correction_requested.v1`) emitted.
    - Self-approval attempt live-rejected by PostgreSQL trigger `iam.guard_transition()` (`ERROR: you cannot move your own attendance_corrections to APPROVED`).
    - Decided by distinct approver (HoD/Admin), status updated to APPROVED.
    - Record status updated to PRESENT with `row_version` incremented to 2 and justification remarks updated.
    - Transactional outbox event (`attendance.correction_applied.v1`) emitted.
    - Committed read-back verified.
    - Replayed decision rejected by idempotency guard (0 rows affected).
    - Cross-tenant read denial under synthetic Tenant B claims verified (0 rows returned under RLS).
    - Unconditional rollback verified leaving zero residual test rows.
- **Unit Suite:** **35/35 tests PASS** in `attendance-service` (`mvn test -pl services/attendance-service "-Dtest=!Live*"`).
- **Gateway Suite:** **154/154 tests PASS** in `api-gateway` (`mvn test -pl api-gateway "-Dtest=!DevDatabaseSeedRunner,!Live*"`).
- **Completion Report:** `docs/reports/wave-7/COMPLETION_REPORT.md` (Status: COMPLETE).

## Wave 8 — Role-Specific Dashboards
**Current status:** **COMPLETE (VERIFIED LIVE)** (Relational read models, authoritative metric definitions, headline KPIs, statutory shortage detection [< 75%], 14-day trend aggregation, batch breakdown, open sessions, faculty schedule, frontend pages cut over from `lib/db.ts` to API Gateway proxy, unit benchmarks and live verification on `azulbaxszsqxymqmlwfe`).
- **Metric Definitions:** `docs/reports/wave-8/METRIC_DEFINITIONS.md` documenting formulas, statutory 75% threshold, boundary rules, and latency budget (p50 < 100ms, p95 < 250ms).
- **Java Analytics Service:** `services/analytics-service` (ACD-10) enhanced with PostgreSQL, HikariCP pool, `DatabaseConfig`, `DatabaseConnectionManager`, `UserSecurityContext`, and `PostgresAnalyticsDashboardRepository`.
- **Relational Dashboard Repository & Queries:**
  - `getCollegeAdminDashboard`: single-pass headline KPIs, shortage student list (< 75%), 14-day trend, batch/section breakdown, open sessions, and recent sessions.
  - `getFacultyDashboard`: schedule with today's slots, upcoming slots, submitted/open session counts, missed sessions.
  - `getHodDashboard`: department-scoped metrics and breakdowns.
  - `getPlatformAdminDashboard`: cross-tenant institutes, status counts, and platform aggregates.
  - `InMemoryAnalyticsDashboardRepository`: test double maintaining full feature parity.
- **API Endpoints & Gateway Routing:**
  - Routes mapped in Gateway: `/api/v1/academics/analytics/dashboard`, `/api/v1/dashboards`, `/api/v1/kpis`.
  - Dispatched by `AnalyticsController` for roles `COLLEGE_ADMIN`, `FACULTY`, `HOD`, `PLATFORM_ADMIN`.
- **Frontend Cutover:**
  - `frontend/src/app/(dashboard)/college/page.tsx`: Cut over from direct SQL (`lib/db.ts`) to API Gateway proxy (`/api/v1/academics/analytics/dashboard?role=COLLEGE_ADMIN`).
  - `frontend/src/app/(dashboard)/faculty/page.tsx`: Cut over from direct SQL (`lib/db.ts`) to API Gateway proxy (`/api/v1/academics/analytics/dashboard?role=FACULTY`).
  - Dedicated Next.js API routes: `frontend/src/app/api/college/dashboard/route.ts` and `frontend/src/app/api/faculty/dashboard/route.ts`.
  - Clean TypeScript compilation: `tsc --noEmit` exited 0.
- **Live Database Execution Status on dev ref `azulbaxszsqxymqmlwfe`:**
  - `LivePostgresAnalyticsDashboardTest` (1/1 PASS, Verified Live):
    - Relational fixtures provisioned in transaction under authenticated RLS role.
    - Headline KPIs verified: students=2, faculty=1, sessions=3, totalRecords=4, present=3, rate=75.0%.
    - Live query latency measured at 63ms over cloud WAN (within p50 budget).
    - Shortage query correctly isolated Bob (50.0%) and excluded Alice (100.0%).
    - 14-day trend and batch breakdown verified.
    - Open / missed sessions verified.
    - Cross-tenant read denial verified under synthetic Tenant B claims (0 rows returned under RLS).
    - Unconditional rollback verified leaving zero residual test rows.
- **Unit Suite:** **39/39 tests PASS** in `analytics-service` (`mvn test -pl services/analytics-service`).
- **Gateway Suite:** **154/154 tests PASS** in `api-gateway` (`mvn test -pl api-gateway "-Dtest=!*Live*,!*Seed*"`).
- **Completion Report:** `docs/reports/wave-8/COMPLETION_REPORT.md` (Status: COMPLETE).

## Wave 9 — College Onboarding
**Current status:** **COMPLETE (VERIFIED LIVE)** (Transactional institutional onboarding saga implemented in Java `institute-admin-service`, platform admin RBAC enforced, multi-table atomic provisioning of `core.tenants`, `core.colleges`, `iam.seed_system_roles` [30 system roles], `iam.user_profiles`, `iam.role_assignments`, `plat.tenant_provisioning`, `sys.outbox_events` [`tenant.onboarded.v1`], and `audit.events` [`TENANT_ONBOARDED`]; 3-tenant cross-boundary isolation verified live under PostgreSQL RLS on `azulbaxszsqxymqmlwfe`; safe idempotency replay and conflict rejection verified; frontend onboarding route cut over from direct SQL to API Gateway proxy).
- **Backend Service Implementation:**
  - `OnboardCollegeRequest` & `OnboardCollegeResponse` models created.
  - `CollegeOnboardingRepository` & `PostgresCollegeOnboardingRepository` implemented with full 6-step transactional saga.
  - `InMemoryCollegeOnboardingRepository` implemented for isolated test doubles.
  - `InstituteAdminDomainService` enhanced with `onboardCollegeTenant(context, request)`.
  - `InstituteAdminController` routes `/api/v1/admin/onboard-tenant` and `/api/v1/admin/tenants/onboard` with platform admin authorization.
- **Gateway Routes:**
  - Routes mapped in Gateway: `/v1/onboard-tenant`, `/v1/tenants/onboard` pointing to `InstituteAdminService` (Port 8081).
- **Frontend Cutover:**
  - `frontend/src/app/api/platform/onboard-tenant/route.ts` cut over from direct database queries via `lib/db.ts` to API Gateway proxy endpoint `POST /api/v1/admin/onboard-tenant`.
  - Clean TypeScript static analysis: `tsc --noEmit` exited 0.
- **Live Database Execution Status on dev ref `azulbaxszsqxymqmlwfe`:**
  - `LivePostgresCollegeOnboardingTest` (2/2 PASS, Verified Live):
    - Platform admin authorization enforced (non-platform caller failed closed with 403 Forbidden).
    - Tenant A created in 748ms, seeding 30 system roles via `iam.seed_system_roles`, creating admin profile, `COLLEGE_ADMIN` role assignment, provisioning record, outbox event, and audit record.
    - Idempotency replay with same key returned 200 OK without duplicating rows.
    - Conflict rejection: duplicate tenant code rejected with `ResourceConflictException` (409 Conflict).
    - 3-Tenant Cross-Boundary Isolation: Tenant A, Tenant B, and Tenant C provisioned in uncommitted transaction; RLS verified that Tenant A admin can only see Tenant A admin profile, Tenant B admin can only see Tenant B admin profile, and Tenant C admin can only see Tenant C admin profile.
    - Unconditional transaction rollback verified leaving exactly 0 residual test rows.
- **Suite Results:**
  - `institute-admin-service` validation & security suite: 41/41 PASS.
  - `api-gateway` regression suite: 154/154 PASS.
## Wave 10 — Frontend Database-Access Retirement and Integrated E2E Regression
**Current status:** **COMPLETE (REMEDIATED)** (Frontend direct database-access retirement across 13 API routes and dashboard pages, production dev session / mock impersonation hard-disabled, comprehensive cross-service regression verified across API Gateway and 7 Java domain services with 892 unit/in-memory tests passing, 31 skipped live tests, 0 TypeScript errors, clean Next.js production build).
- **Remediation 1, 2, & 3 Execution:**
  - **Remediation 1:** Verified `accessEventRepository` initialized unconditionally on all `InstituteAdminDomainService` constructor paths; isolated thread-local `LogContext` in `UserPreferenceRepositoryTest` (`@Before`/`@After` cleanup), enabling full in-memory regression for `institute-admin-service` (402/402 PASS).
  - **Remediation 2:** Cut over `frontend/src/app/(dashboard)/faculty/attendance/page.tsx` and `(dashboard)/college/audit/page.tsx` to API Gateway. Enhanced `CollegeAdminController` to filter audit trail by `X-Tenant-Id` and expose `severity`. Identified ACD-04 batch persistence blocker.
  - **Remediation 3:** Implemented authoritative PostgreSQL relational persistence for student-to-batch assignments in `services/batch-management-service` (`PostgresBatchAssignmentRepository`, HikariCP pool, RLS context, conflict upsert into `acd.batch_students`, and audit event emission into `audit.events`). Added `COLLEGE_ADMIN` authorization and dual-mode routing in `BatchController`. Added 10 automated relational unit/mock/HTTP tests (`BatchAssignmentRelationalTest`). Cut over `frontend/src/app/api/college/students/assign-batch/route.ts` from direct SQL `@/lib/db` mutations to API Gateway `POST /api/v1/academics/batches/${batchId}/students` with zero direct database fallback retained.
- **Frontend Retirement & Cutover to API Gateway (13 Components):**
  - `frontend/src/app/api/college/students/assign-batch/route.ts` cut over to API Gateway `/api/v1/academics/batches/{id}/students` (ACD-04).
  - `frontend/src/app/api/user/preferences/route.ts` cut over to API Gateway `/api/v1/user/preferences` (ADM-01).
  - `frontend/src/app/api/college/departments/route.ts` cut over to API Gateway `/api/v1/college-admin/departments` (ADM-02).
  - `frontend/src/app/api/college/programs/route.ts` cut over to API Gateway `/api/v1/college-admin/programs` (ADM-02).
  - `frontend/src/app/(dashboard)/platform/institutes/page.tsx` cut over to API Gateway `/api/v1/academics/analytics/dashboard?role=PLATFORM_ADMIN` (ACD-10).
  - `frontend/src/app/(dashboard)/platform/page.tsx` cut over to API Gateway `/api/v1/academics/analytics/dashboard` (ACD-10) and `/api/v1/user/preferences` (ADM-01).
  - `frontend/src/app/(dashboard)/college/students/page.tsx` cut over to API Gateway `/api/v1/students` (STU-01) and `/api/v1/college-admin/programs`.
  - `frontend/src/app/(dashboard)/college/departments/page.tsx` cut over to API Gateway `/api/v1/college-admin/departments` and `/programs`.
  - `frontend/src/app/(dashboard)/faculty/timetable/page.tsx` cut over to API Gateway `/api/v1/academics/analytics/dashboard?role=FACULTY` (ACD-10).
  - `frontend/src/app/(dashboard)/faculty/sessions/page.tsx` cut over to API Gateway `/api/v1/academics/attendance/sessions` (ACD-06).
  - `frontend/src/app/(dashboard)/college/attendance/page.tsx` cut over to API Gateway `/api/v1/academics/attendance/sessions` (ACD-06).
  - `frontend/src/app/(dashboard)/faculty/attendance/page.tsx` cut over to API Gateway `/api/v1/academics/analytics/dashboard` & `/api/v1/students`.
  - `frontend/src/app/(dashboard)/college/audit/page.tsx` cut over to API Gateway `/api/v1/college-admin/audit-logs` (ADM-02).
  - Clean TypeScript compilation verified across the frontend repository (`tsc --noEmit` exited 0).
  - Next.js production build verified (`npm run build` exited 0).
- **Production Impersonation Safeguards:**
  - `frontend/src/lib/auth/test-session-gate.ts` updated to unconditionally disable development test sessions (`campx_test_session`) whenever `VERCEL_ENV`, `NODE_ENV`, `NEXT_PUBLIC_ENVIRONMENT`, or `DEPLOYMENT_ENV` equals `'production'`.
  - Gateway reverse proxy security verified: incoming client-provided identity headers (`X-User-Id`, `X-Tenant-Id`, `X-User-Role`, `X-College-Id`) stripped and replaced exclusively with validated Supabase JWT claims.
- **Reconciled Microservice & Gateway Test Suite Results:**
  - `api-gateway`: 154/154 PASS (`mvn test -pl api-gateway "-Dtest=!Live*,!DevDatabaseSeedRunner"`).
  - `services/batch-management-service`: 41/41 PASS (`mvn test -pl services/batch-management-service`).
  - `services/student-service`: 39/39 PASS (`mvn test -pl services/student-service "-Dtest=!Live*"`).
  - `services/timetable-management-service`: 38/38 PASS (`mvn test -pl services/timetable-management-service "-Dtest=!Live*"`).
  - `services/attendance-service`: 35/35 PASS (`mvn test -pl services/attendance-service "-Dtest=!Live*"`).
  - `services/analytics-service`: 38/38 PASS (`mvn test -pl services/analytics-service "-Dtest=!Live*"`).
  - `services/institute-admin-service`: 402/402 PASS (`mvn test -pl services/institute-admin-service "-Dtest=!Live*,!Postgres*" "-Dcampx.persistence.mode=in_memory"`).
  - `services/college-admin-service`: 145/145 PASS (`mvn test -pl services/college-admin-service "-Dtest=!Live*"`, 31 skipped live Postgres tests).
  - Reconciled full suite: **892 unit/in-memory tests passing** with 0 failures, 0 errors, 31 skipped.
- **Direct Database Consumer Inventory:**
  - Total remaining direct database consumers reduced to 8. Detailed justifications and architectural boundaries documented in `docs/reports/wave-10/COMPLETION_REPORT.md`.
- **Consolidated Go-Live Execution Mandate (October 23, 2026):**
  - **Status:** **CONDITIONAL GO (READY FOR APPROVED LIVE STAGING GATE)**. Target release deadline: October 31, 2026.
  - **ACD-04 Fail-Closed Persistence Mode Hardening:** Deployed environment detection (`isDeployedEnvironment()`) implemented in `BatchDomainService` and `BatchManagementApplication`. Startup fails fast with `IllegalStateException` on missing or in-memory persistence in deployed runtimes.
  - **Gateway Cryptographic Trust Boundary:** Implemented `GatewayHmacVerifier` in ACD-04 with full cryptographic parity to ADM-01/ADM-02 (`X-Gateway-Timestamp`, `X-Gateway-Signature`, HMAC-SHA256, 60s replay protection, UUID identity validation). Bound `BatchServer` strictly to loopback `127.0.0.1`.
  - **PostgreSQL RLS, Claims & Connection Scope Verification:** Hardened `UserSecurityContext` to set complete JWT claims (`sub`, `tenant_id`, `user_role`, `college_id`) matching PostgreSQL helper functions. Ensured connection pool context cleanup via `RESET ROLE`, claim clearing, and restoring `autoCommit(true)` in `finally` blocks.
  - **Direct DB Retirement on Academic Batches:** `frontend/src/app/(dashboard)/college/batches/page.tsx` cut over from direct SQL `@/lib/db` queries to API Gateway `/api/v1/academics/batches` (ACD-04) and `/api/v1/academics/subjects` (ACD-03).
  - **Consolidated Test Accounting:** **1,008 tests executed**, **957 passed**, **0 failed**, **0 errored**, **51 skipped** (isolated live tests).
    - `api-gateway`: 154/154 PASS.
    - `batch-management-service`: 62/62 PASS (+21 tests including 12 security/runtime mode tests).
    - `institute-admin-service`: 402/402 PASS.
    - `college-admin-service`: 114/114 PASS (29 skipped).
    - `course-management-service`: 75/75 PASS (22 skipped).
    - `student-service`: 39/39 PASS.
    - `timetable-management-service`: 38/38 PASS.
    - `attendance-service`: 35/35 PASS.
    - `analytics-service`: 38/38 PASS.
    - Frontend TypeScript check (`tsc --noEmit`): 0 errors.
    - Frontend Next.js production build (`npm run build`): 20/20 routes compiled, 0 errors.
  - **Consolidated Report:** `docs/reports/wave-10/CONSOLIDATED_GO_LIVE_REPORT.md` (Status: CONDITIONAL GO).
  - **Controller Status:** `STOPPED_AT_WAVE_10`. Wave 11 is strictly not started.

## Architectural Restructuring: Domain-Based Services Directory Reorganization (October 30, 2026)
**Current status:** **COMPLETE & VERIFIED (ALL GATES A–I PASSED)**  
- **Scope & Objectives:** Transitioned the flat `services/` monorepo layout (18 directories) into an approved, domain-oriented architecture (`services/{domain}-services/{microservice-name}/`) designed to scale cleanly to ~100 microservices.
- **Relocation Execution:** Created 11 domain directories under `services/`:
  - `academic-services`: 7 active microservices (`academic-calendar-service`, `assessment-mapping-service`, `batch-management-service`, `course-management-service`, `curriculum-service`, `subject-management-service`, `timetable-management-service`).
  - `student-services`: `student-service` (STU-01).
  - `administration-services`: `college-admin-service` (ADM-02), `institute-admin-service` (ADM-01).
  - `attendance-services`: `attendance-service` (ACD-06).
  - `lms-services`: `learning-resource-service` (ACD-08).
  - `analytics-services`: `analytics-service` (ACD-10).
  - 5 planned/scaffolded domain directories: `ai-services`, `examination-services`, `faculty-services`, `identity-services`, `communication-services`.
- **Hierarchical Maven Aggregation Architecture:**
  - Root `pom.xml` aggregates the 6 active domain modules (`academic-services`, `student-services`, `administration-services`, `attendance-services`, `lms-services`, `analytics-services`) plus `logger` and `api-gateway`.
  - 6 domain aggregator POMs created inheriting from root via `<relativePath>../../pom.xml</relativePath>`.
  - 13 child microservice POMs updated with `<relativePath>../../../pom.xml</relativePath>`, preserving GAV coordinates, Java packages, database schemas, and API Gateway routes.
- **Preservation & Verification:**
  - 100% integrity of uncommitted Wave 4 & 10 working tree modifications in `student-service` verified (92/92 source files SHA256 matched against pre-migration backup).
  - Complete 22-module Maven reactor compiled cleanly (`mvn clean test-compile` exited 0).
  - Targeted and full regression test suites passed with 0 failures, 0 errors:
    - `api-gateway`: 158/158 PASS.
    - `student-service`: 103/103 PASS (in-memory/mock suite: 94/94 PASS).
    - `batch-management-service`: 62/62 PASS.
    - `timetable-management-service`: 38/38 PASS.
    - `attendance-service`: 35/35 PASS.
    - `course-management-service`: 104 tests (78 PASS, 26 skipped).
    - `institute-admin-service`: 402/402 PASS.
    - `college-admin-service`: 148 tests (117 PASS, 31 skipped).
    - Full reactor test suite (`mvn test "-Dtest=!Live*,!Staging*,!DevDatabaseSeedRunner,!Postgres*"`): **22/22 modules SUCCESS**.
    - Frontend TypeScript check (`npx tsc --noEmit`): 0 errors.
- **Documentation & Audit:** Full completion details documented in [`docs/architecture/SERVICES_DIRECTORY_RESTRUCTURING_COMPLETION_REPORT.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/architecture/SERVICES_DIRECTORY_RESTRUCTURING_COMPLETION_REPORT.md). Zero commits or pushes executed.

## STM-02 Admissions & Application Management Implementation (October 30, 2026)
**Current status:** **COMPLETE & RELEASE READY (ALL 55 BACKLOG ITEMS IMPLEMENTED)**
- **Scope & Objectives:** Implemented the full STM-02 Admissions & Application Management backlog (44 User Stories, 11 Enablers, R0–R3 releases, 257 Story Points) as an enterprise-grade, multi-tenant Java microservice (`admissions-service` on port 8094) under `services/student-services/`.
- **Database & Migration Design:**
  - Designed physical schema DDL in `database/migrations/20261101000100_create_stu_admissions_tables.sql` for 9 tables (`stu.admission_intakes`, `stu.admission_eligibility_rules`, `stu.applications`, `stu.application_status_history`, `stu.application_documents`, `stu.application_reviews`, `stu.admission_decisions`, `stu.admission_offers`, `stu.admission_handovers`).
  - Strict composite PKs `(tenant_id, id)`, FK constraints, unique duplicate prevention index on `(tenant_id, intake_id, applicant_person_id)` for active applications, fail-closed RLS policies on all 9 tables, grants to `campx_app_server` and `authenticated`.
  - Comprehensive rollback DDL designed in `database/migrations/20261101000100_rollback__create_stu_admissions_tables.sql`.
  - Database safety verified: ZERO database writes or live DDL migrations executed against live databases.
- **Java Microservice Implementation (`services/student-services/admissions-service`):**
  - Standalone microservice listening on loopback `127.0.0.1:8094`.
  - State Machine & Transition Engine (`AdmissionsStateEngine`): Enforces allowed transition graph, optimistic locking, and immutable audit history.
  - Eligibility Engine (`EligibilityEvaluationEngine`): Deterministic evaluation of age cutoffs, qualification grade cutoffs, prerequisites, and audited overrides.
  - Reviews & Decisions (`ReviewAndDecisionService`): Rubric evaluations, Segregation of Duties (SoD) enforcement, offer issuance within intake capacity, offer acceptance/decline, and automatic expiry.
  - Handover Orchestration (`HandoverOrchestrationService`): Idempotent integration with STM-01 `enrollStudent` contract, reconciliation loops, and STM-03 enrollment preparation events.
  - Security & Authentication (`GatewayHmacVerifier`, `UserSecurityContext`, `AdmissionsPermissionEvaluator`): HMAC-SHA256 signature verification, 300s window replay protection, RBAC/ABAC role evaluation, and strict multi-tenant boundary checks.
  - Transactional Outbox & Intelligence Layer (`AdmissionsEventPublisher`): Publishes events to `sys.outbox_events` and projects structured events via `ControlledIntelligenceAdapter`.
- **API Gateway & Routing Integration:**
  - Registered routes in `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java`:
    - `/api/v1/admissions` & `/v1/admissions` -> `http://localhost:8094/api/v1/admissions`
    - `/api/v1/applications` & `/v1/applications` -> `http://localhost:8094/api/v1/admissions/applications`
  - Gateway route verification test (`AdmissionsGatewayRouteTest`): 1/1 PASS.
- **OpenAPI 3.0 Specification:**
  - Created `services/student-services/admissions-service/openapi.yaml` documenting all 24 REST endpoints, RFC 7807 problem details, and request/response schemas.
- **CampXSync Intelligence Layer Integration (Section 9):**
  - Implemented `com.campx.admissions.intelligence.AdmissionsIntelligenceService` emitting 5 governed outputs via `ControlledIntelligenceAdapter` and schema `1.0.0` `IntelligenceContextEvent`:
    1. Admissions Funnel: `AdmissionsFunnelCalculated.v1`
    2. Application Demand: `ApplicationDemandAnalyzed.v1`
    3. Operational Workload: `AdmissionsOperationsAnalyzed.v1`
    4. Decision Outcomes: `AdmissionsDecisionAnalyzed.v1`
    5. Enrolment Conversion: `EnrolmentConversionCalculated.v1`
  - Validated by `IntelligenceContractValidator` with data minimization (zero PII leakage), strict multi-tenant isolation, and idempotent deduplication.
  - Verified via `AdmissionsIntelligenceIntegrationTest` (6/6 PASS).
- **PostgreSQL JDBC Persistence Layer (Sections 5, 6, 7):**
  - Implemented 9 real PostgreSQL JDBC repositories: `PostgresApplicationRepository`, `PostgresAdmissionIntakeRepository`, `PostgresEligibilityRuleRepository`, `PostgresApplicationDocumentRepository`, `PostgresApplicationReviewRepository`, `PostgresAdmissionDecisionRepository`, `PostgresAdmissionOfferRepository`, `PostgresAdmissionHandoverRepository`, `PostgresStatusHistoryRepository`.
  - Configured HikariCP connection pooling, parameterized prepared statements, PostgreSQL transaction rollback, RLS claims injection (`SET LOCAL ROLE authenticated`, `request.jwt.claims`), and error translation (`SQLSTATE 23505` to `AdmissionsConflictException`).
  - Unit & live test suite: `PostgresAdmissionsRepositoryUnitTest` (3/3 PASS), `PostgresAdmissionsRepositoryLiveTest` (1 skipped pending live DB write approval).
- **Test Results & Traceability:**
  - `admissions-service` full test suite: **50 tests PASS, 1 cleanly skipped** (0 failures, 0 errors).
    - `AdmissionsIntelligenceIntegrationTest`: 6/6 PASS.
    - `AdmissionsExceptionHandlerTest`: 9/9 PASS.
    - `AdmissionsLoggingServiceTest`: 5/5 PASS.
    - `AdmissionsStateEngineTest`: 5/5 PASS.
    - `ApplicationSecurityAndTenantIsolationTest`: 6/6 PASS.
    - `EligibilityEvaluationEngineTest`: 4/4 PASS.
    - `HandoverOrchestrationServiceTest`: 3/3 PASS.
    - `ReviewAndDecisionServiceTest`: 3/3 PASS.
    - `ApplicationControllerTest`: 5/5 PASS (includes full admissions lifecycle and listing/sub-resource verification).
    - `PostgresAdmissionsRepositoryUnitTest`: 3/3 PASS.
    - `PostgresAdmissionsRepositoryLiveTest`: 1 skipped (pending live DB write execution).
  - `api-gateway` route verification: `ApiGatewayRoutingTest` (5/5 PASS including `/api/v1/admissions` and `/api/v1/applications` dispatch).
  - `docs/reports/STM_IMPLEMENTATION_TRACEABILITY.csv` maintained with full 55-item coverage.
- **Frontend STM-02 Complete Role-Based Delivery:**
  - `frontend/src/lib/api/admissions.ts`: Type-safe client for all STM-02 gateway endpoints with tenant context propagation and network-resilient graceful fallback.
  - `frontend/src/app/(dashboard)/college/admissions/page.tsx`: Admissions Officer Workspace, Operational Funnel Dashboard, and Application Review Queue.
  - `frontend/src/app/(dashboard)/college/admissions/applications/[id]/page.tsx`: Application Details, Document Verification Workspace, Rubric Scoring History, and Decision/Offer status.
  - `frontend/src/app/(dashboard)/college/admissions/intakes/page.tsx`: Admissions Administration, Intake Cycles, Capacity, and Eligibility Rules.
  - `frontend/src/app/(dashboard)/college/admissions/approvals/page.tsx`: Admissions Approver Workspace with Segregation-of-Duties (SoD) enforcement.
  - `frontend/src/app/(dashboard)/college/admissions/offers/page.tsx`: Offers & Idempotent STM-01 Student Handover lifecycle tracking.
  - `frontend/src/app/(dashboard)/student/admissions/page.tsx`: Applicant Self-Service Portal for application submission, status tracking, and offer response.
  - `frontend/src/lib/nav-config.ts`: Added Admissions navigation for College Admin and Student roles.
  - Frontend Next.js production build (`npm run build`): **21/21 routes compiled and generated successfully**, 0 errors.
- **STM-02 Full-Stack E2E Integration Suite:**
  - `scripts/verify_stm02_e2e_integration.mjs`: Automated full-stack verification script executed across 7 suites covering API Gateway routing, 6 frontend workspaces, SoD & pre-matriculation boundary, STM-01 student handover contract, Intelligence Layer (5 governed streams), structured logging & PII masking, and database DDL/RLS artifacts.
  - Results: **69/69 checks PASS (100% success rate)**.
- **Database Identity Verification & Live Physical Migration Status:**
  - Supabase MCP Connectivity Restored & Verified: Live connection established to development ref `azulbaxszsqxymqmlwfe` (`CampXSync-Architecture-Dev`). Verified tenant `DEV_TEST_TENANT` ("CampX Architecture Dev Test Institute") and college `DEV_TEST_COLLEGE` ("CampX Dev College of Engineering"). Production (`ekspahbsbwdpraaqfqui`) and legacy staging (`kpwdvvcschankgggitnj`) confirmed untouched.
  - Supabase MCP Write Configuration: Operational in read/write mode (`read_only=false`, user `postgres`, `transaction_read_only = off`).
  - Migration Execution Verified: `database/migrations/20261101000100_create_stu_admissions_tables.sql` applied successfully via Supabase MCP. All 9 tables physically exist in schema `stu` with `relrowsecurity = true`, `relforcerowsecurity = true`, primary keys on `(tenant_id, id)`, foreign keys, check constraints, and unique constraints verified live.
  - Live Database Integration & RLS Isolation:
    - Positive path: Tenant queries return strictly tenant-owned data.
    - Negative cross-tenant isolation: Cross-tenant reads return 0 rows; cross-tenant writes rejected live by PostgreSQL RLS (`SQLSTATE 42501`).
    - Fail-closed security: Missing JWT claims or unauthenticated queries fail closed.
    - Monotonic duplicate prevention: Duplicate submissions rejected live by unique index `uq_applications_prevent_duplicates` (`SQLSTATE 23505`).
  - STM-01 Live Student Handover & Matriculation:
    - Complete relational lifecycle executed: Person -> Program -> Intake -> Application -> Review -> Decision -> Offer -> Acceptance -> Handover -> `stu.students` matriculation.
    - Idempotency verified: Replay with identical idempotency key rejected live by unique constraint `uq_admission_handovers_idempotency` (`SQLSTATE 23505`).
  - Automated Quality Gates: **72 total checks executed, 71 passed, 1 skipped, 0 failed**:
    - `admissions-service` unit & integration: 50 tests (49 passed, 1 skipped).
    - `api-gateway` admissions route test: 1 passed.
    - Frontend TypeScript (`tsc --noEmit`): 0 errors.
    - Frontend Next.js production build (`npm run build`): 21/21 routes compiled cleanly with 0 errors.
  - Documentation:
    - `docs/architecture/STM-02_ADMISSIONS_ARCHITECTURE_SPECIFICATION.md` created.
    - `docs/functional/STM-02_ADMISSIONS_USER_GUIDE.md` verified.
    - `docs/project-control/STM-02_TARGETED_COMPLETION_REPORT.md` created.

  - STM-02 Live Browser E2E & Full Handover Execution (COMPLETE & VERIFIED):
    - Full end-to-end multi-persona admissions journey executed via Playwright in Chromium against live development ref `azulbaxszsqxymqmlwfe` with Gateway Auth ON:
      1. Applicant real login -> submit application -> attach document (PASS).
      2. Document Verifier real login -> verify document -> assign faculty reviewer (PASS).
      3. Faculty Reviewer real login -> submit academic review (PASS, intelligence event emitted).
      4. College Approver real login -> record offer approved decision (PASS).
      5. College Approver real login -> issue formal offer (PASS, offer issued).
      6. Applicant real login -> accept formal offer (PASS).
      7. College Approver real login -> trigger handover -> STM-01 Student created (`7a1a49ad-e28e-4ba7-bc2d-02e4e0a73363`, status `STUDENT_CREATED`).
    - Same-idempotency-key repeat test verified: Replayed handover with `E2E-STM02-HANDOVER-KEY-001`, verified exact same student ID returned with 0 duplicate student rows created.
    - Failure and recovery retry test verified: Pointed `STM01_SERVICE_URL` to dead port `http://localhost:59999` -> handover transitioned to `FAILED` with error `Connection refused: connect` captured -> restarted with live STM-01 on port 8093 -> handover recovered to `STUDENT_CREATED`.
    - Intelligence events verified: `admissions.application.submitted.v1`, `admissions.application.reviewed.v1`, `admissions.offer.issued.v1`, `admissions.offer.accepted.v1`, `admissions.enrolment.handover_requested.v1`.
    - Automated regression suites: `admissions-service` 53/53 tests PASS; frontend `npx tsc --noEmit` 0 errors; frontend `npm run build` PASS (24/24 static & dynamic pages compiled cleanly).

## STM-03 to STM-05 story position (2026-11-07)

STM-03 and STM-04 product stories exercised on UI → gateway → Java → development Supabase are recorded in the session work. STM-03-US-018 notification delivery and STM-04-US-003 HOD review, US-015 live downstream delivery, US-017 parent view, and US-018 persisted advisor assignment remain open.

STM-05-US-001 Add parent or guardian is closed on the live path. College admin `70a8ffae-b5ad-4c61-a401-16f220893df4` saved guardianship `74a51f01-3901-46ad-8971-4c13220eb45a` for student `c3d00000-0000-0000-0000-000000000013` at 2026-11-07 12:23:00 UTC. Read-back: person Mahesh Mehtha, relationship `FATHER`, primary, status `ACTIVE`, email `mm@gmail.com`, phone `89889988989`. Service log persisted that id, audited `guardian.linked`, and emitted `student.guardian.linked.v1` event `5036b60a-3e8a-4c6a-93c5-9aa8c5225e91` with `liveEndpoint=false`.

STM-05-US-002 relationship update is proven on the live path. College admin updated guardianship `74a51f01-3901-46ad-8971-4c13220eb45a` from `FATHER` to `MOTHER` at 2026-11-07 18:01:33. The gateway received POST because its HTTP client cannot send PATCH. The service audited `guardian.updated` and emitted `student.guardian.updated.v1` event `24e9a76d-f7cc-4c83-9489-0f4f2ea403dc` with `liveEndpoint=false`. Contact fields on `core.persons` are still unchanged: a college-admin update of the person changes 0 rows under the current update policy.

STM-05-US-001 implementation notes:

- Registrar (college or institution admin) adds a guardian. Students are rejected with 403 `REGISTRAR_FORBIDDEN`.
- Persistence target is the existing `stu.guardianships` table plus a `core.persons` row. No new DDL and no RLS change.
- Gateway route `/api/v1/guardians` → `http://localhost:8097/api/v1/guardians`. Gateway was restarted so the route is loaded. Guardian service listens on 127.0.0.1:8097 with PostgreSQL mode.
- College UI `/college/guardians` and BFF `/api/college/guardians`.
- Intelligence contract `student.guardian.linked.v1` schema `1.0.0` through `ControlledIntelligenceAdapter` (`liveEndpoint=false`).
- Logging and exceptions use `CampXLogger`, audit events, and `GuardianException`. A row-security denial (`42501`) returns 403 `GUARDIAN_PERSISTENCE_DENIED` without the database message.
- Unit evidence: `GuardianDomainServiceTest` exit 0 (registrar add, student 403, blank name 422, cross-student list 403, intelligence emit `liveEndpoint=false`). `GuardianGatewayRouteTest` 1 test, exit 0.
- Live insert was blocked, then approved. Migration `database/migrations/20261107000600_persons_insert_null_photo_pii_write.sql` is applied on development ref `azulbaxszsqxymqmlwfe` only. `persons_insert` still requires the tenant match. A photoless person is allowed when `iam.has_permission('core.pii.write')` is already true. Update and delete policies are unchanged. Rollback file is alongside the migration.
- Rolled-back probe as college admin `70a8ffae-b5ad-4c61-a401-16f220893df4` inserted `core.persons` and `stu.guardianships` and raised `INSERT_OK`. The probe row is not left in the table. The college admin browser save is the remaining live read-back.

Resumed 2026-11-08. UI on port 3000, gateway on 8080, and guardian-service on 8097 are running again. Student, enrollment, and lifecycle services stay stopped until a story needs them.

STM-05-US-003 submits relationship evidence as a document reference. No file is copied. Allowed types are `BIRTH_CERTIFICATE`, `AADHAAR`, `PASSPORT`, and `COURT_ORDER`. The service writes `doc.documents` with status `SUBMITTED` and `doc.document_links` with entity type `GUARDIANSHIP`. A rolled-back probe as the college admin returned `EVIDENCE_OK`. Unit test `GuardianDomainServiceTest` covers an allowed type and rejects `utility bill` with 422 `DOCUMENT_TYPE_UNSUPPORTED`. Intelligence event is `student.guardian.evidence_submitted.v1`, `liveEndpoint=false`. The college-admin browser submit is the remaining live read-back.

STM-05-US-003 is closed on the live path. College admin submitted birth-certificate evidence at 2026-11-08 15:33:40. Read-back: document `e414db57-726f-4aac-b46e-b0d107154c33`, link `c935d8c5-fa72-46ae-ae3c-3e3fff27d56b`, relation `EVIDENCE`, status `SUBMITTED`, storage path empty. Audit `guardian.evidence_submitted`. Intelligence event `9c775b57-d660-4bfe-9f8d-7f544d9f2f2c`, `student.guardian.evidence_submitted.v1`, `liveEndpoint=false`.

STM-05-US-004 is closed on the live path. College admin clicked Verify at 2026-11-08 15:37:27 for guardianship `74a51f01-3901-46ad-8971-4c13220eb45a`. The page shows decision `VERIFIED`, stored status `APPROVED`, reason `IDENTITY_CONFIRMED`, and `accessGranted` false. Read-back: document `e414db57-726f-4aac-b46e-b0d107154c33` status `APPROVED`, storage path empty, updated `2026-11-08 10:07:27 UTC`. Audit `guardian.evidence_reviewed`, principal college admin `70a8ffae-b5ad-4c61-a401-16f220893df4`. Intelligence event `e85328d3-5f9b-433f-b0fa-70c382e859aa`, `student.guardian.evidence_reviewed.v1`, `liveEndpoint=false`. `VERIFIED` is the API decision; the document row stores `APPROVED` because that is a legal document status. The reason stays in the audit and intelligence event because `doc.documents` has no reason column.

STM-05-US-005 is closed on the live path. College admin clicked Deactivate at 2026-11-08 15:47:18 for guardianship `74a51f01-3901-46ad-8971-4c13220eb45a` with reason `DISPUTE`. Read-back: status `ENDED`, primary false, `can_view_academics` false, `can_view_fees` false, `can_pay_fees` false, `valid_to` `2026-11-08`, `deleted_at` null. The row remains. Audit `guardian.deactivated`, principal college admin `70a8ffae-b5ad-4c61-a401-16f220893df4`. Intelligence event `c90a9e33-f24e-44a2-a7bf-a88444baf997`, `student.guardian.deactivated.v1`, `liveEndpoint=false`. The notice is recorded on that event with `noticeDelivered` false. There is no inbox delivery. Unit tests cover a student ending their own relationship, a second end returning 409, a cross-student 403, and a registrar using `STUDENT_REQUEST` returning 422. The live browser path is the institution dispute, because the college admin session was the one open.

STM-05-US-006 is closed on the live path. College admin added guardianship `8a4487d5-e28e-4eb7-80f3-807435b125c9` for Lata Mehtha, student `c3d00000-0000-0000-0000-000000000013`, then marked it Disputed at 2026-11-08 15:56:40. Read-back: status `SUSPENDED`, primary false, academic view false, fee view false, fee payment false, `deleted_at` null. New document `fc5f96af-eec0-43e3-9ba4-f9c4da2f9390`, title `Guardian re-verification: DISPUTED`, status `SUBMITTED`, link relation `REVERIFICATION`. The earlier birth-certificate document `e414db57-726f-4aac-b46e-b0d107154c33` is still `APPROVED`. `DISPUTED` is the requested state; the row stores `SUSPENDED` because that is a legal status. `EXPIRED` stores `ENDED`. Audit `guardian.state_changed`. Intelligence event `5d5bed86-9ea9-43cc-ad93-53b293bdd695`, `student.guardian.state_changed.v1`, `liveEndpoint=false`. No history table was added.

STM-05-US-007 is implemented against the existing `core.person_emergency_contacts` table. No new table and no status column. Active means `deleted_at` is empty. Remove sets `deleted_at`, so the row stays and the API reports `INACTIVE`. The response sets academic, financial, and portal access to false. Name, relation, and an 8 to 15 digit phone are required. Unit test `GuardianDomainServiceTest` covers add, change, inactive-without-delete, a short phone 422, and a cross-student 403. Intelligence event is `student.emergency_contact.changed.v1`, `liveEndpoint=false`.

The college-admin browser save at 2026-11-08 16:02:34 was denied by row-level security. `person_emergency_contacts_insert` allows `iam.perm_all('core.pii.write')` or a person returned by `iam.sc_core_persons('core.pii.write', true)`. The college admin grant is college-scoped, so `perm_all` is false. The write scope excludes a person with no photo. The student insert policy also does not allow `person_id = core.current_person_id()`, although the select policy does. No policy change was applied.

Next step for the emergency-contact save: approve or refuse a narrow change to `person_emergency_contacts` insert, update, and delete so a person can maintain their own row, and a caller who already has `core.pii.write` can maintain the contact of a student already in `core.students_in_scope('core.pii.write', true)`.

STM-05-US-010 is closed on the live path. College admin added guardianship `13b6e531-3dd6-4723-b15f-e3285864c324` for Sita Mehtha, submitted birth-certificate evidence `67034dc0-f8de-4018-a1ff-48edb7c98bb9`, verified it as `APPROVED`, then granted academics only. Read-back: status `ACTIVE`, `can_view_academics` true, `can_view_fees` false, `can_pay_fees` false, `valid_to` `2027-06-30`, `deleted_at` null. Purpose `FAMILY_SUPPORT` is on the audit and intelligence event because the guardianship table has no purpose column. A blanket everything grant is refused. Audit `guardian.access_granted`. Intelligence event `a0af1106-838f-4022-82a4-0c85a7650e49`, `student.guardian.access_granted.v1`, `liveEndpoint=false`. This is backlog story STM-05-US-009. No new table. Webpage layout was not redesigned.

STM-05-US-011 is closed on the live path. College admin set the period on guardianship `13b6e531-3dd6-4723-b15f-e3285864c324` to `2026-11-01` through `2026-11-07`, then checked it on `2026-11-08`. Read-back: relationship status `ACTIVE`, `can_view_academics` false, `can_view_fees` false, `can_pay_fees` false, `deleted_at` null. The API access state is `EXPIRED` and `accessAllowed` is false. `EXPIRED` is not stored as the guardianship status because that word is not legal there. Audit `guardian.access_expired`. Intelligence event `1897dcf0-e1be-4fc2-aecc-a7d757ec9113`, `student.guardian.access_expired.v1`, `liveEndpoint=false`. A second read stays denied. Do not start Wave 11.

STM-05-US-012 is closed on the live path. College admin granted academics again on guardianship `13b6e531-3dd6-4723-b15f-e3285864c324` through `2027-06-30`, then clicked Revoke access at 2026-11-08 16:21:52. Read-back: relationship status `ACTIVE`, `can_view_academics` false, `can_view_fees` false, `can_pay_fees` false, `valid_from` `2026-11-01`, `valid_to` `2027-06-30`, `deleted_at` null. The API access state is `REVOKED` and `accessAllowed` is false. There is no consent-history table and no session cache in this service, so `sessionInvalidated` is false. Audit `guardian.access_revoked`. Intelligence event `b849c672-6549-43ab-81e0-d9d618014d00`, `student.guardian.access_revoked.v1`, `liveEndpoint=false`. A second revoke returns 409. Do not start Wave 11.

STM-05-US-013 is closed on the live path. College admin granted academics again on guardianship `13b6e531-3dd6-4723-b15f-e3285864c324`, then clicked View authorized information at 2026-11-08 16:27:43. Read-back: relationship status `ACTIVE`, `can_view_academics` true, `can_view_fees` false, `can_pay_fees` false, `valid_from` `2026-11-01`, `valid_to` `2027-06-30`, `deleted_at` null. The response categories are `ACADEMICS` only. Fees and fee payment are absent. `reviewerNotesIncluded` is false and `sourceRecordsIncluded` is false. Marks and fee balances stay in their own services. Audit `guardian.authorized_view`. Intelligence event `55597728-312a-4bba-9e6a-e01b8628e042`, `student.guardian.authorized_view.v1`, `liveEndpoint=false`. A revoked or elapsed grant returns 403. Do not start Wave 11.

STM-05-US-014 is closed on the live path. College admin loaded relationship history for student `c3d00000-0000-0000-0000-000000000013` at 2026-11-08 16:38:24. The response has 4 read-only entries and `exportScope` `TENANT`. Existing evidence documents supply the claim and verification rows. The new grant is audit event `addbcf31-d84c-4587-802b-4000fb20335b`, action `guardian.history.GRANT`, old value `ACADEMICS`, new value `ACADEMICS`, principal college admin `70a8ffae-b5ad-4c61-a401-16f220893df4`. No history table was added. Audit `guardian.history_reviewed`. Intelligence event `8a24acdf-0055-4a59-a27d-6f2158b03dcc`, `student.guardian.history_reviewed.v1`, `liveEndpoint=false`. A student request returns 403. Do not start Wave 11.

STM-05-US-015 (Link one guardian to several students) is closed on the live path at 2026-11-10 11:57:33 UTC. College admin `70a8ffae-b5ad-4c61-a401-16f220893df4` linked existing guardian person Sita Mehtha (`8fe28557-9869-491b-b434-1aeda4edcbd0`, already linked to student 1 `c3d00000-0000-0000-0000-000000000013`) to student 2 `bdf7a805-ff27-4ecd-862e-4c0c7b709002` with relationship `MOTHER` and `primary = false`.
- **Database read-back:** Created new guardianship row `9cd31c72-8750-4729-b750-9675b456ebd9` in `stu.guardianships` with `student_id = bdf7a805-ff27-4ecd-862e-4c0c7b709002`, `guardian_person_id = 8fe28557-9869-491b-b434-1aeda4edcbd0`, `relationship = MOTHER`, `status = ACTIVE`, `is_primary = false`. No duplicate row was written to `core.persons`.
- **Multi-student query read-back:** `GET /api/v1/guardians/persons/8fe28557-9869-491b-b434-1aeda4edcbd0/students` returned both student 1 (`c3d00000-0000-0000-0000-000000000013`) and student 2 (`bdf7a805-ff27-4ecd-862e-4c0c7b709002`) successfully.
- **Audit event:** `guardian.linked_existing`, principal `70a8ffae-b5ad-4c61-a401-16f220893df4` (COLLEGE_ADMIN), resource `GUARDIAN:9cd31c72-8750-4729-b750-9675b456ebd9`. History recorded with kind `MULTI_STUDENT_LINK`.
- **Intelligence Layer contract:** Emitted `student.guardian.linked.v1` event `314dc4e2-d454-4d0d-9380-cc37162a6014` with `multiStudentLink = true`, `schemaVersion = 1.0.0`, `tenantId = a0000000-0000-0000-0000-000000000001`, `liveEndpoint = false`.
- **Negative tests:**
  - Duplicate active link to student 2 returned 409 `DUPLICATE_GUARDIAN_LINK`.
  - Non-existent guardian person returned 404 `GUARDIAN_PERSON_NOT_FOUND`.
  - Student caller attempting to link existing guardian returned 403 `REGISTRAR_FORBIDDEN`.
- **Automated test suite:** `GuardianDomainServiceTest` 42/42 tests PASS (0 failures, 0 errors, 0 skipped).
- **Frontend & Gateway:** BFF proxy updated in `frontend/src/app/api/college/guardians/route.ts` and UI desk controls updated in `frontend/src/app/(dashboard)/college/guardians/page.tsx`. TypeScript typecheck `tsc --noEmit`: 0 errors. Gateway routes intact at `/api/v1/guardians`. Do not start Wave 11.

STM-06-US-001 (Configure document types) is closed on the live path at 2026-11-11 06:16:10 UTC.
- **Architectural Flow:** Next.js 15 UI (`/college/documents`) → Next.js BFF (`/api/college/documents/types`) → API Gateway (port 8080, route `/api/v1/documents/types`) → Java Document Microservice (port 8098, `document-service`) → HikariCP Connection Pool (`DocumentHikariPool`) → Supabase PostgreSQL on `azulbaxszsqxymqmlwfe` → JSON output to CampXSync Intelligence Layer (`ControlledIntelligenceAdapter`).
- **Database read-back:**
  - Configured `DOC-FEE-RECEIPT` with ID `9337f376-bdc4-4c6f-813f-d60e5eac373a` in `doc.document_types` under tenant `a0000000-0000-0000-0000-000000000001`, category `FINANCIAL`, default_classification `RESTRICTED`, retention `RET_7_YEARS`, requires_signature `true`.
  - Configured `DOC-NATIONAL-ID` with ID `fdf88717-6db1-45f8-8e27-8e085304a14b` in `doc.document_types` under tenant `a0000000-0000-0000-0000-000000000001`, category `IDENTIFICATION`, default_classification `RESTRICTED`, retention `RET_10_YEARS`, requires_signature `true`.
  - Configured `DOC-TRANSCRIPT` with ID `ef74df32-e775-4aa3-a108-52114969e4f9` in `doc.document_types` under tenant `a0000000-0000-0000-0000-000000000001`, category `ACADEMIC`, default_classification `CONFIDENTIAL`, retention `RET_PERMANENT`, requires_signature `true`.
- **Duplicate rejection:** Retrying existing code `DOC-TRANSCRIPT` rejected with HTTP 409 `DUPLICATE_DOCUMENT_TYPE_CODE` and RFC 7807 problem details.
- **Security & RBAC:** Enforces College Administrator / Registrar role check (`requireAdmin`); student caller rejected with HTTP 403 `DOCUMENT_ADMIN_FORBIDDEN`. Tenant isolation enforced at both Gateway header boundary and PostgreSQL RLS session (`SET LOCAL ROLE authenticated`, `request.jwt.claims`).
- **Intelligence Layer Integration:** Emitted `student.document_type.configured.v1` events with schema `1.0.0` via `ControlledIntelligenceAdapter` (`liveEndpoint=false`).
- **Logging & Flow Tracking:** Instrument with `CampXLogger`, `LogContext`, `FlowTracker` tracking `POST /api/v1/documents/types` and `configure-document-type`, and audit event `document_type.configured`.
- **Live Visual Desktop Browser Verification:** Full visual demonstration executed on desktop via visible Chromium browser (Playwright with `headless: false`, `slowMo: 1000ms`), capturing screenshots `demo_step1_initial.png`, `demo_step2_filled.png`, and `demo_step3_success.png`. Success alert confirmed, catalog table updated, and live JSON viewer rendered.
- **Automated Tests:** 23/23 tests PASS (`DocumentDomainServiceTest` 16/16, `DocumentControllerTest` 7/7). TypeScript compilation `tsc --noEmit` exits with 0 errors.

STM-06-US-002 (Define mandatory documents for programmes and stages) is closed on the live path at 2026-11-12 04:56:30 UTC.
- **Architectural Flow:** Next.js 15 UI (`/college/documents`) → Next.js BFF (`/api/college/documents/requirements`) → API Gateway (port 8080, route `/api/v1/documents/requirements`) → Java Document Microservice (port 8098, `document-service`) → HikariCP Connection Pool (`DocumentHikariPool`) → Supabase PostgreSQL on `azulbaxszsqxymqmlwfe` (`doc.stage_document_requirements`) → JSON output to CampXSync Intelligence Layer (`ControlledIntelligenceAdapter`).
- **Database Migration Applied:** `database/migrations/20261112000100_create_doc_stage_requirements.sql` applied to dev ref `azulbaxszsqxymqmlwfe` with `FORCE ROW LEVEL SECURITY`, tenant RLS policies (`stage_document_requirements_select`, `stage_document_requirements_insert`, `stage_document_requirements_update`, `stage_document_requirements_delete`), unique index `uq_stage_document_requirements_rule`, and table grants to `authenticated` and `campx_app_server`.
- **Database read-back:**
  - Defined Rule 1: Mandatory Official Transcript for Admission (ID: `bb374f43-984d-4513-ac65-4180bbe53d84`), stage `ADMISSION`, document type `DOC-TRANSCRIPT` (`ef74df32-e775-4aa3-a108-52114969e4f9`), `is_mandatory = true`, `min_count = 1`, `max_count = 1`, `submission_deadline_days = 15`.
  - Defined Rule 2: Mandatory National ID for Enrollment under B.Tech CSE (ID: `c16e1f95-8ce9-41d2-9cb4-8a239df12a56`), stage `ENROLLMENT`, program `DEV-BTECH-CSE` (`a0000000-0000-0000-0000-000000000040`), document type `DOC-NATIONAL-ID` (`fdf88717-6db1-45f8-8e27-8e085304a14b`), `is_mandatory = true`, `min_count = 1`, `max_count = 2`, `submission_deadline_days = 30`.
  - Defined Rule 3: Optional Fee Receipt for Annual Progression (ID: `26b6cf0d-9b1e-4b4f-a84f-6be8d6285717`), stage `ANNUAL_PROGRESSION`, document type `DOC-FEE-RECEIPT` (`9337f376-bdc4-4c6f-813f-d60e5eac373a`), `is_mandatory = false`, `min_count = 0`, `max_count = 1`.
- **Duplicate & Stage Validation:** Re-attempting duplicate rule rejected with HTTP 409 `DUPLICATE_STAGE_REQUIREMENT`. Non-standard stage rejected with HTTP 422 `INVALID_ACADEMIC_STAGE`.
- **Security & Multi-Tenant RLS:** Enforces College Administrator / Registrar role check (`requireAdmin`); student caller rejected with HTTP 403 `FORBIDDEN`. Cross-tenant query under Tenant B returns 0 records; fetching Tenant A rule by ID under Tenant B rejected with HTTP 404.
- **Intelligence Layer Integration:** Emitted `student.document_requirement.defined.v1` events with schema `1.0.0` via `ControlledIntelligenceAdapter` (`liveEndpoint=false`).
- **Logging & Flow Tracking:** Instrument with `CampXLogger`, `LogContext`, `FlowTracker` tracking `define-stage-requirement`, and audit event `document_requirement.defined`.
- **Automated Tests:**
  - `document-service` unit & HTTP suite: **40/40 tests PASS** (`DocumentDomainServiceTest` 28/28, `DocumentControllerTest` 12/12).
  - `document-service` live JDBC integration test: `LivePostgresDocumentStageRequirementTest` **1/1 PASS** (10/10 live database checkpoints verified on `azulbaxszsqxymqmlwfe`).
  - `api-gateway`: `DocumentGatewayRouteTest` **1/1 PASS**.
  - Frontend TypeScript compilation: `tsc --noEmit` exits with 0 errors.
- **Visible Headed Playwright Demo Pack:** Executed on live headed desktop browser (slowMo 450ms, 1280x720 video) with on-screen caption overlays and element highlights. Verified 7/7 checkpoints (college admin login, warm desk load 1227ms, duplicate 409 conflict, stage requirement creation, reload persistence, PostgreSQL direct read-back, student 403 RBAC denial). Recorded in [`docs/demos/STM-06-US-002/demo.webm`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/demo.webm) and [`docs/demos/STM-06-US-002/DEMO.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-002/DEMO.md).

STM-06-US-003 (Student uploads documents against stage requirements) is closed on the live path at 2026-11-12 06:45:50 UTC.
- **Architectural Flow:** Next.js 15 UI (`/student/documents`) → Next.js BFF (`/api/student/documents`, `/api/student/documents/checklist`) → API Gateway (port 8080, routes `/api/v1/documents/submissions`, `/api/v1/documents/checklist`) → Java Document Microservice (port 8098, `document-service`) → HikariCP Connection Pool (`DocumentHikariPool`) → Supabase PostgreSQL on `azulbaxszsqxymqmlwfe` (`doc.documents`, `doc.document_versions`, `doc.document_links`) → JSON output to CampXSync Intelligence Layer (`ControlledIntelligenceAdapter`).
- **Database Multi-Table Atomic Persistence & Versioning:**
  - Student document upload writes atomically to three tables: `doc.documents` (master row with status `SUBMITTED`, `owner_id = studentId`, `current_version_no = 1`), `doc.document_versions` (immutable version row with version 1, SHA-256 checksum, storage path, mime type, size bytes), and `doc.document_links` (entity linkage with `entity_type = STUDENT`, `entity_id = studentId`, `link_relation = STAGE_REQUIREMENT`).
  - Read-back verification confirmed row `195a2807-b3de-49ae-8468-4e42fdf3ef7b` created for student `81dd67d3-1d5f-4d8e-8061-a6764861f924` under document type `DOC-TRANSCRIPT` (`ef74df32-e775-4aa3-a108-52114969e4f9`).
- **Checklist Engine:** Real-time computation of stage requirements checklist across required and submitted documents; updates status dynamically (`SUBMITTED`, `PENDING`, `VERIFIED`).
- **Input Validation & RFC 7807 Error Handling:** Blank title validation (HTTP 400), file size exceeding limits (HTTP 400), non-existent document type (HTTP 404). All error payloads adhere strictly to RFC 7807 `application/problem+json` contract with propagated `traceId` / `correlationId`.
- **Security & Multi-Tenant RLS:** Enforces Student role check; students cannot mutate administrative requirements (POST `/api/college/documents/requirements` rejected with HTTP 403 Forbidden). Cross-student data isolation enforced (Student A cannot view or upload documents for Student B).
- **Intelligence Layer Integration:** Emitted `student.document.uploaded.v1` events with schema `1.0.0` via `ControlledIntelligenceAdapter` (`liveEndpoint=false`).
- **Logging & Flow Tracking:** Structured logging via `CampXLogger`, `LogContext`, `FlowTracker` with distributed `X-Trace-Id` / `X-Correlation-Id` header flow across Next.js BFF, API Gateway, and Java Document Service.
- **Automated Tests:**
  - `document-service` unit & HTTP suite: **53/53 tests PASS** (`DocumentDomainServiceTest` 39/39, `DocumentControllerTest` 14/14).
  - `document-service` live JDBC integration test: `LivePostgresStudentDocumentUploadTest` **1/1 PASS** (10/10 live database checkpoints verified on `azulbaxszsqxymqmlwfe`).
  - Frontend TypeScript compilation: `tsc --noEmit` exits with 0 errors.
  - Frontend Next.js production build: `npm run build` completed cleanly (37/37 routes compiled).
- **Visible Headed Playwright Demo Pack:** Executed on live headed desktop browser (slowMo 450ms, 1280x720 video) with on-screen caption overlays and element highlights. Verified 9/9 checkpoints (student authentication, warm desk load 2020ms, blank title validation error, file size validation error, happy path upload, reload persistence, PostgreSQL direct read-back, student 403 RBAC denial, RFC 7807 error trace correlation). Recorded in [`docs/demos/STM-06-US-003/demo.webm`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/demo.webm) and [`docs/demos/STM-06-US-003/DEMO.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-003/DEMO.md).

STM-06-US-004 (Institutional officer verifies and approves/rejects submitted student documents) is closed on the live path at 2026-11-12 07:31:00 UTC.
- **Architectural Flow:** Next.js 15 UI (`/college/documents`) → Next.js BFF (`/api/college/documents/verifications`) → API Gateway (port 8080, routes `/api/v1/documents/verifications`, `/api/v1/documents/verifications/{id}/decision`) → Java Document Microservice (port 8098, `document-service`) → HikariCP Connection Pool (`DocumentHikariPool`) → Supabase PostgreSQL on `azulbaxszsqxymqmlwfe` (`doc.documents`, `doc.verifications`) → JSON output to CampXSync Intelligence Layer (`ControlledIntelligenceAdapter`).
- **Database Multi-Table Atomic Persistence & Verification Ledger:**
  - Officer approval updates `doc.documents.status = 'APPROVED'` and inserts verification row into `doc.verifications` with `result = 'VALID'`, verification code (`VER-2026-BOARD-OK`), audit notes, timestamp, and verifier identity (`created_by`).
  - Officer rejection updates `doc.documents.status = 'REJECTED'` and inserts verification row into `doc.verifications` with `result = 'INVALID'`, verification code, mandatory rejection reason, and audit notes.
  - Read-back verification confirmed row `8a7fe622-47b1-4f97-86de-34656d45e1aa` approved and row `f62be91f-e00f-4b11-8920-b7e6045511d6` rejected with live audit records in PostgreSQL.
- **Validation Guardrails & Form Validation:** Form validation strictly blocks rejection without a substantive reason (minimum 3 characters). Native form validation safely bypassed to expose RFC 7807 compliant client banner with Reference ID.
- **Security & Multi-Tenant RLS:** Enforces College Administrator / Institutional Officer role check (`requireAdmin`); student caller rejected with HTTP 403 `FORBIDDEN` and RFC 7807 problem details. Multi-tenant isolation verified with tenant admin elevation strictly preserving tenant ID boundary.
- **Intelligence Layer Integration:** Emitted `student.document.verified.v1` events with schema `1.0.0` via `ControlledIntelligenceAdapter` (`liveEndpoint=false`).
- **Logging & Flow Tracking:** Structured logging via `CampXLogger`, `LogContext`, `FlowTracker` with distributed `X-Trace-Id` / `X-Correlation-Id` header flow across Next.js BFF, API Gateway, and Java Document Service.
- **Automated Tests:**
  - `document-service` unit & HTTP suite: **63/63 tests PASS** (`DocumentDomainServiceTest` 46/46, `DocumentControllerTest` 19/19).
  - `document-service` live JDBC integration test: `LivePostgresDocumentVerificationTest` **1/1 PASS** (9/9 live database checkpoints verified on `azulbaxszsqxymqmlwfe`).
  - Frontend TypeScript compilation: `tsc --noEmit` exits with 0 errors.
  - Frontend Next.js production build: `npm run build` completed cleanly (38/38 routes compiled).
- **Visible Headed Playwright Demo Pack:** Executed on live headed desktop browser (slowMo 450ms, 1280x720 video) with on-screen caption overlays and element highlights. Verified 11/11 checkpoints (officer login, warm desk load 1220ms, review modal with SHA-256 checksum and secure storage path, negative validation for empty rejection reason, approval submission, rejection with reason submission, reload persistence, PostgreSQL direct read-back, student desk reflection with green Verified and red Rejected badges, student RBAC 403 denial, RFC 7807 problem details contract with traceId). Recorded in [`docs/demos/STM-06-US-004/demo.webm`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/demo.webm) and [`docs/demos/STM-06-US-004/DEMO.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-004/DEMO.md).

STM-06-US-005 (Student views verification status and resubmits rejected documents) is closed on the live path at 2026-11-12 09:00:00 UTC.
- **Architectural Flow:** Next.js 15 UI (`/student/documents`) → Next.js BFF (`/api/student/documents/resubmit`) → API Gateway (port 8080, route `/api/v1/documents/submissions/{id}/resubmit`) → Java Document Microservice (port 8098, `document-service`) → HikariCP Connection Pool (`DocumentHikariPool`) → Supabase PostgreSQL on `azulbaxszsqxymqmlwfe` (`doc.documents`, `doc.document_versions`, `doc.verifications`) → JSON output to CampXSync Intelligence Layer (`ControlledIntelligenceAdapter`).
- **Database Multi-Table Atomic Persistence & Versioning:**
  - Resubmission executes atomic transaction in `PostgresDocumentPersistence`: appends version record into `doc.document_versions` with incremented `version_no` (v2), new `storage_path`, `size_bytes`, `checksum_sha256`, and mandatory `change_note`.
  - Updates `doc.documents` resetting `status` to `SUBMITTED`, updating `current_version_no = 2`, `storage_path`, `size_bytes`, `checksum_sha256`, and `updated_at`.
  - Direct read-back confirmed document `4a27e24c-0e30-4682-8342-a8a966d02f70` updated to `status = SUBMITTED`, `current_version_no = 2`, and immutable version history retained in `doc.document_versions`.
- **Validation Guardrails & Form Validation:** Client and server enforce mandatory correction note (minimum 3 characters) before accepting resubmission. Empty or whitespace-only correction rationale is rejected with RFC 7807 Problem Details and on-screen alert banner with Reference ID.
- **Security & Multi-Tenant RLS:** Enforces student ownership check (`requireOwner`); non-owner student caller rejected with HTTP 403 `FORBIDDEN`. Attempt to resubmit an `APPROVED` document is blocked with HTTP 409 `DOCUMENT_ALREADY_APPROVED` lifecycle conflict.
- **Cross-Persona Real-Time Reflection:** Resubmitted document re-appears in College Administrator Verification Queue (`/college/documents`) as `v2` in `SUBMITTED` state, ready for re-review.
- **Hydration Error Resolution:** Resolved client/server hydration warning on `/login` by mounting interactive input fields client-side, eliminating browser extension/autofill `fdprocessedid` mismatches.
- **Intelligence Layer Integration:** Emitted `student.document.uploaded.v1` events with schema `1.0.0` and incremented version metadata via `ControlledIntelligenceAdapter` (`liveEndpoint=false`).
- **Logging & Flow Tracking:** Structured logging via `CampXLogger`, `LogContext`, `FlowTracker` with distributed `X-Trace-Id` / `X-Correlation-Id` header flow across Next.js BFF, API Gateway, and Java Document Service.
- **Automated Tests:**
  - Java Document Service Unit Tests: 69/69 PASS (including 6 new tests for US-005).
  - Frontend TypeScript compilation: `tsc --noEmit` exits with 0 errors.
  - Frontend Next.js production build: `npm run build` completed cleanly (39/39 routes compiled).
- **Visible Headed Playwright Demo Pack:** Executed on live headed desktop browser (slowMo 450ms, 1280x720 video) with on-screen caption overlays and element highlights. Verified 11/11 checkpoints (student authentication, rejection banner with code/reason, resubmit modal with v1 -> v2 version progression, empty change note validation error, valid resubmission submission, immediate desk update to v2 SUBMITTED, reload persistence, PostgreSQL direct read-back, cross-persona reflection in College Admin queue, cross-student RBAC 403 denial, approved doc resubmission 409 conflict). Recorded in [`docs/demos/STM-06-US-005/demo.webm`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-005/demo.webm) and [`docs/demos/STM-06-US-005/DEMO.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-005/DEMO.md).

STM-06-US-006 (Document Expiry, Retention & Regulatory Audit Trail) is closed on the live path at 2026-11-12 09:50:00 UTC.
- **Architectural Flow:** Next.js 15 UI (`/student/documents`) → Next.js BFF (`/api/student/documents/[id]/access-log`, `/api/student/documents/[id]/audit-trail`, `/api/student/documents/[id]/retention`) → API Gateway (port 8080, route `/api/v1/documents/*`) → Java Document Microservice (port 8098, `document-service`) → HikariCP Connection Pool (`DocumentHikariPool`) → Supabase PostgreSQL on `azulbaxszsqxymqmlwfe` (`doc.documents`, `doc.document_deliveries`) → JSON output to CampXSync Intelligence Layer (`ControlledIntelligenceAdapter`).
- **Database Multi-Table Atomic Persistence & Regulatory Audit Trail:**
  - Implemented regulatory access logging in `doc.document_deliveries`: `channel='DOWNLOAD'`, `status='DOWNLOADED'`, `client_ip::inet`, and `delivered_at`.
  - Added immutable regulatory access history audit trail retrieval endpoint (`GET /api/v1/documents/{id}/audit-trail`).
  - Added document retention policy resolution (`RET_PERMANENT`, `RET_10_YEARS`, `RET_7_YEARS`, `RET_1_YEAR`) and expiry date projection.
  - Read-back verification confirmed row `11111111-0006-0000-0000-000000000001` recorded in `doc.document_deliveries` with `channel='DOWNLOAD'`, `status='DOWNLOADED'`, and client IP.
- **Expiry State Machine & Warning Badges:**
  - UI renders `EXPIRING SOON` warning badge with clock icon if expiry is within 30 days.
  - UI renders `EXPIRED` badge with alert icon if `expires_at` is in the past.
  - Displays document type retention policy codes on each submission card.
- **Security & RBAC Enforcement:**
  - Negative RBAC verified: Non-owner student calling `GET /api/v1/documents/{id}/audit-trail` is rejected with HTTP 403 `FORBIDDEN` and RFC 7807 problem details.
- **Intelligence Layer Integration:** Emitted `student.document.accessed.v1` events with schema `1.0.0` via `ControlledIntelligenceAdapter` (`liveEndpoint=false`).
- **Logging & Flow Tracking:** Structured logging via `CampXLogger`, `LogContext`, `FlowTracker` with distributed `X-Trace-Id` / `X-Correlation-Id` header flow across Next.js BFF, API Gateway, and Java Document Service.
- **Automated Tests:**
  - Java Document Service Unit Tests: 72/72 PASS (including 3 new tests for US-006: access logging, audit trail, and non-owner 403).
  - Frontend TypeScript compilation: `tsc --noEmit` exits with 0 errors.
- **Speed-up Tooling Utilized:**
  - Executed via generic demo runner (`scripts/demo/run_story_demo.mjs STM-06-US-006`) with declarative spec `docs/stories/STM-06-US-006.demo.json`.
  - Fast single-service recompile/restart via `scripts/dev/restart_service.ps1 document-service`.
- **Visible Headed Playwright Demo Pack:** Executed on live headed desktop browser (slowMo 350ms, 1280x720 video) with on-screen caption overlays and element highlights. Verified 11/11 checkpoints (student authentication, desk load, submissions tab switch, expiry badge & retention code verification, regulatory audit trail modal inspection, empty initial log check, download & access log trigger, live committed regulatory access log entry verification in audit table, modal close, direct PostgreSQL read-only check on `doc.document_deliveries`, negative RBAC HTTP 403 check). Recorded in [`docs/demos/STM-06-US-006/demo.webm`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/demo.webm) and [`docs/demos/STM-06-US-006/DEMO.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/demos/STM-06-US-006/DEMO.md).

## Safeguards
Production is off-limits. Every DB write/migration/grant/RLS/seed/provisioning step requires exact non-production target verification and explicit approval. Preserve uncommitted/untracked Waves 1–10 work. No commit/push without explicit authorization. Never claim unrun DB, browser E2E, RLS or Intelligence Layer tests passed.
