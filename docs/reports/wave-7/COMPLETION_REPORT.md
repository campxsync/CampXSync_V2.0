# Wave 7 Completion Report — Attendance Corrections

**Date:** 2026-10-23  
**Branch / HEAD:** `feature/java-backend-integration` / `5952ea4f8f3070188b33fc290bf9df5f284a7697`  
**Status:** Complete  
**Evidence basis:** Independently executed Maven builds, JUnit tests (in-memory unit/relational tests and live target database verification on `azulbaxszsqxymqmlwfe`), and TypeScript compiler validations.

---

## 1. Scope and outcome

- **Approved scope:**
  - Governed attendance correction requests and complete lifecycle (`PENDING` -> `APPROVED` / `REJECTED`).
  - Separation of requester and approver: requester cannot self-approve their own request (failing closed with `AttendanceCorrectionForbiddenException` at application layer, and verified enforced by PostgreSQL trigger `iam.guard_transition()` at database layer).
  - Authority and RBAC checks: `COLLEGE_ADMIN` has full correction authority; `HOD` is permitted to decide department corrections; `FACULTY` role is strictly forbidden from deciding corrections (`403 Forbidden`).
  - Concurrency control and repeat-safe decision handling: replaying or re-deciding an already approved/rejected correction request affects 0 rows and throws `IllegalStateException`.
  - Optimistic locking and version increment: applying approved corrections increments `row_version = row_version + 1` on `acd.attendance_records` and updates remarks with justification.
  - Append-only audit history in `acd.attendance_corrections` preserving original status, new status, reason, requested_by, approved_by, and timestamp.
  - Transactional outbox events: emits `attendance.correction_requested.v1`, `attendance.correction_applied.v1`, and `attendance.correction_rejected.v1` into `sys.outbox_events` in the same transaction.
  - API Gateway and Frontend route cutover: `frontend/src/app/api/college/attendance/corrections/route.ts` cut over from direct DB query (`lib/db.ts`) to API Gateway proxy (`/api/v1/academics/attendance/corrections`).
  - Tenant isolation and RLS: cross-tenant access denial verified at both application context and PostgreSQL RLS levels.

- **Implemented:**
  - **Relational Repository Layer:**
    - `PostgresAttendanceRepository.java`: Implemented `requestCorrection`, `decideCorrection`, `findCorrectionById`, `findCorrectionsBySessionId`, `findCorrectionsByRecordId`.
    - Enforced self-approval prevention: throws `AttendanceCorrectionForbiddenException` if requester equals decider.
    - Idempotency guard: enforces `WHERE status = 'PENDING'` preventing replay attacks.
    - Optimistic lock guard: validates `expectedVersion` and increments `row_version`.
    - Dual outbox emission: emits `attendance.correction_requested.v1` and `attendance.correction_applied.v1` / `attendance.correction_rejected.v1`.
    - `InMemoryAttendanceRepository.java`: Full parity implementation for in-memory unit tests.
  - **Service & Security Layer:**
    - `AttendanceDomainService.java`: Exposed correction methods (`requestCorrection`, `decideCorrection`, `getCorrectionHistory`) delegating to active repository.
    - `UserSecurityContext.java`: Added constructor support for `(userId, tenantId, role)`.
    - `AttendanceCorrectionForbiddenException.java`: Domain exception mapping to HTTP 403.
  - **Controller & API Endpoints:**
    - `AttendanceController.java`:
      - `POST /corrections`: creates or submits correction requests.
      - `POST /corrections/{id}/decide`: handles approval or rejection with authority validation.
      - `GET /corrections/{id}` and `GET /sessions/{id}/corrections`: queries audit history.
      - RBAC guards: blocks non-admin/non-HOD roles from deciding corrections.
  - **Frontend Cutover:**
    - `frontend/src/app/api/college/attendance/corrections/route.ts`: Cut over from direct PostgreSQL queries (`query(...)` via `lib/db.ts`) to authenticated API Gateway forwarding (`/api/v1/academics/attendance/corrections`).
  - **Live Verification Test:**
    - `LivePostgresAttendanceCorrectionsTest.java`: End-to-end integration test against development ref `azulbaxszsqxymqmlwfe` in an uncommitted transaction terminating in `conn.rollback()`.

- **Deferred/out of scope:** Wave 8 dashboards, Wave 9 college onboarding, Wave 10 final DB access retirement.
- **Deviations/reasons:** None. Database schema and triggers (`iam.guard_transition()`) already existed on `azulbaxszsqxymqmlwfe` with appropriate RLS; zero DDL migrations required.

---

## 2. Files changed

- `services/attendance-service/src/main/java/com/campx/academic/attendance/repository/AttendanceRepository.java` (added correction interface methods)
- `services/attendance-service/src/main/java/com/campx/academic/attendance/repository/InMemoryAttendanceRepository.java` (in-memory correction tracking)
- `services/attendance-service/src/main/java/com/campx/academic/attendance/repository/PostgresAttendanceRepository.java` (PostgreSQL correction implementation with outbox and RLS)
- `services/attendance-service/src/main/java/com/campx/academic/attendance/service/AttendanceDomainService.java` (delegation methods for corrections)
- `services/attendance-service/src/main/java/com/campx/academic/attendance/security/UserSecurityContext.java` (convenience constructor)
- `services/attendance-service/src/main/java/com/campx/academic/attendance/controller/AttendanceController.java` (endpoints for corrections and decide)
- `services/attendance-service/src/test/java/com/campx/academic/attendance/AttendanceRelationalServiceTest.java` (5 new unit tests for Wave 7)
- `services/attendance-service/src/test/java/com/campx/academic/attendance/identity/LiveAttendanceCatalogAuditTest.java` (added pg_policies inspection)
- `services/attendance-service/src/test/java/com/campx/academic/attendance/repository/LivePostgresAttendanceCorrectionsTest.java` (live rollback integration test)
- `frontend/src/app/api/college/attendance/corrections/route.ts` (cut over to API Gateway)
- `docs/CAMPXSYNC_AUTONOMOUS_WAVE_EXECUTION.md` (checkpoint tracking)

Confirm unrelated work was preserved: All untracked and modified files from previous waves remain intact.

---

## 3. Commands and tests

| Command/suite | Result | Pass/fail/error/skipped counts | Test tier | Notes |
|---|---|---|---|---|
| `mvn test -pl services/attendance-service "-Dtest=!Live*"` | PASS | 35 passed, 0 failed, 0 errors | In-memory / Unit | Includes 10 relational tests covering all Wave 7 invariant checks |
| `mvn test -pl api-gateway "-Dtest=!DevDatabaseSeedRunner,!Live*"` | PASS | 154 passed, 0 failed, 0 errors | In-memory / Mock | Gateway routing, security, HMAC, claim parsing |
| `mvn test -pl services/attendance-service -Dtest=LiveAttendanceCatalogAuditTest` | PASS | 1 passed, 0 failed, 0 errors | Live read-only DB | RLS policies and catalog constraints verified on `azulbaxszsqxymqmlwfe` |
| `mvn test -pl services/attendance-service "-Dtest=LivePostgresAttendanceCorrectionsTest" "-Dcampx.approve.wave7.write=true"` | PASS | 1 passed, 0 failed, 0 errors | Live DB with rollback | Target verified, self-approval rejected by DB trigger, distinct approval committed, outbox events emitted, row_version incremented, cross-tenant denial verified, clean rollback |
| `npx tsc --noEmit` | PASS | 0 errors | Frontend compile | Clean TypeScript compilation for cutover routes |

---

## 4. Architecture and verification evidence

### 4.1 Transactional path
- Client / Frontend (`frontend/src/app/api/college/attendance/corrections/route.ts`)
  -> API Gateway (`/api/v1/academics/attendance/corrections`)
  -> Java Attendance Service (`AttendanceController` -> `AttendanceDomainService` -> `PostgresAttendanceRepository`)
  -> Supabase PostgreSQL (`acd.attendance_corrections`, `acd.attendance_records`, `sys.outbox_events`).

### 4.2 Authorization and Tenant Isolation
- **Tenant Context:** propagated via `X-Tenant-ID` header and verified against Supabase JWT claims (`app_metadata.tenant_id`).
- **PostgreSQL RLS:** Session claims set via `SELECT set_config('request.jwt.claims', ..., true)` and `SELECT set_config('request.jwt.claim.tenant_id', ..., true)`.
- **Negative Isolation Evidence:** When synthetic Tenant B claims (`b0000000-0000-0000-0000-000000000002`) queried `acd.attendance_corrections`, 0 rows were returned under RLS.
- **Authority Separation:** When `requesterId` attempted self-approval, database trigger `iam.guard_transition()` raised `ERROR: you cannot move your own attendance_corrections to APPROVED`. Only switching to distinct HoD / Admin user profile permitted approval.

### 4.3 Intelligence Layer Contract
- Transactional outbox writes into `sys.outbox_events`:
  - `attendance.correction_requested.v1`
  - `attendance.correction_applied.v1`
  - `attendance.correction_rejected.v1`
- Preserves idempotency, event registration, and aggregates state change without bypassing Gateway authorization or PostgreSQL transactional boundaries.

---

## 5. Security and safety compliance

- **Target project reference:** `azulbaxszsqxymqmlwfe` (Designated development ref).
- **Protected targets:** `ekspahbsbwdpraaqfqui` (Production) and `kpwdvvcschankgggitnj` (Legacy Staging) were NOT accessed.
- **Approval Phrase:** `-Dcampx.approve.wave7.write=true` used for live rollback test.
- **Rollback Safety:** Live test terminated in `conn.rollback()` inside `finally` block; zero dirty records committed to database.
- **Secrets:** Zero credentials or JWT secrets exposed in reports or logs.

---

## 6. Next steps

- Advance execution to **Wave 8 — Role-Specific Dashboards**.
- Implement role-specific read models (College Admin, HoD, Faculty) aggregating persisted student, timetable, attendance, and correction data via Gateway from Java analytics/reporting services.
- Verify latency budget, metric definitions, empty datasets, and cross-tenant isolation.
