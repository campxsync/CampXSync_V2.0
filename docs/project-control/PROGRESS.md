# CampXSync Java Backend Integration — Progress

**Updated:** 2026-10-22  
**Branch:** `feature/java-backend-integration`

> Based on user-provided execution reports and recorded context; not an independent re-verification of current repository or live database.

## Current checkpoint
- Last recorded committed Wave 0 baseline: `105122b6b5b17d3eadc70e7e8094008facb4285a`.
- Waves 1–10 complete/accepted; their uncommitted changes must be preserved.
- **Wave 10 Status:** **COMPLETE** (Frontend direct database-access retirement across 10 API routes and dashboard pages, production test session / mock impersonation gate hard-disabled, integrated cross-service regression verified across all 6 services with 345/345 tests passing, TypeScript compilation 0 errors).
- **Controller status:** `STOPPED_AT_WAVE_10`. Next wave (Wave 11 Production Readiness & Cutover) is strictly out of scope and halted until explicitly authorized.
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

## Safeguards
Production is off-limits. Every DB write/migration/grant/RLS/seed/provisioning step requires exact non-production target verification and explicit approval. Preserve uncommitted/untracked Waves 1–10 work. No commit/push without explicit authorization. Never claim unrun DB, browser E2E, RLS or Intelligence Layer tests passed.
