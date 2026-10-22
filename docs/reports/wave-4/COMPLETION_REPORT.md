# Wave 4 Completion Report — Student Domain Foundation & Roster

**Date:** 2026-10-22  
**Branch / HEAD:** `feature/java-backend-integration`  
**Status:** **COMPLETE (VERIFIED LIVE)** (Stage A: Gate G1 Remediation Active; Stage B: Java Domain Service & Gateway Foundation Complete; Stage C: PostgreSQL Persistence Implementation, Transactional Outbox Integration, Mock JDBC Repository Verification, and Frontend Cutover Complete; Stage D: Live Application Role Authentication Verified; Student/Person Live Persistence Verified; Transactional Outbox Persistence Verified Live on `azulbaxszsqxymqmlwfe` after Owner-Authorized DEC-W4-OUTBOX Execution)  
**Evidence basis:** Direct Maven build and test executions:
- `mvn test -pl services/student-service "-Dtest=LivePostgresStudentPersistenceTest" "-Dcampx.approve.wave4.write=true"`: **2 / 2 PASS**
- `mvn test -pl api-gateway "-Dtest=LiveOutboxGrantsExecutionTest" "-Dcampx.approve.outbox.grants=true"`: **1 / 1 PASS**
- `mvn test -pl services/student-service -Dtest=LiveStudentRuntimeSecurityTest`: **3 / 3 PASS**
- `mvn test -pl services/student-service -Dtest=LiveApplicationRoleAuthTest`: **1 / 1 PASS**
- `mvn test -pl services/student-service "-Dtest=!Live*"`: **39 / 39 PASS**
- `mvn test -pl api-gateway -Dtest=StudentGatewayRouteTest`: **1 / 1 PASS**
- `mvn test -pl logger`: **58 / 58 PASS**
- `mvn test-compile` across all 16 reactor modules: **BUILD SUCCESS (16/16)**
- `node frontend/node_modules/typescript/bin/tsc --project frontend/tsconfig.json --noEmit`: **0 errors**

---

## 1. Scope and Outcome
- **Approved Scope:**
  - Gate G1 least-privilege security remediation for student and core bio-demographic tables.
  - Implementation of Java Student Domain Service (`services/student-service` / STU-01) with aggregate ownership of `stu.students` and `stu.guardianships`.
  - In-transaction RLS claims binding, connection pool hygiene (`RESET ROLE`), and atomic transactional outbox persistence (`sys.outbox_events`).
  - API Gateway routing for `/api/v1/students` and frontend roster cutover (`frontend/src/app/api/college/students/route.ts`).
  - Live PostgreSQL runtime security validation and live persistence test (uncommitted rollback).
- **Implemented & Verified Live on dev ref `azulbaxszsqxymqmlwfe`:**
  - Unprivileged role `campx_app_server` active (`rolsuper=false`, `rolbypassrls=false`, `rolcanlogin=true`, `rolconnlimit=25`, member of `authenticated`).
  - `FORCE ROW LEVEL SECURITY` verified live on Tier 1 tables (`stu.students`, `core.persons`, `stu.guardianships`, `acd.batch_students`).
  - HikariCP connection pool `CampXSync-STU01-Pool` with Supavisor transaction pooler compatibility (`prepareThreshold=0`).
  - `UserSecurityContext.executeInTransaction` enforcing `SET LOCAL ROLE authenticated` + JWT claims binding with `finally { RESET ROLE; }`.
  - PostgreSQL repositories (`PostgresStudentRepository`, `PostgresPersonRepository`, `PostgresGuardianshipRepository`).
  - Next.js cutover routing to API Gateway without direct database queries.
  - Live application role authentication (`LiveApplicationRoleAuthTest`: 1/1 PASS).
  - Decision Gate DEC-W4-OUTBOX owner-approved and applied via `LiveOutboxGrantsExecutionTest`:
    * Granted schema `USAGE ON SCHEMA sys TO authenticated, campx_app_server`.
    * Granted table DML privileges `SELECT, INSERT, UPDATE, DELETE ON TABLE sys.outbox_events TO authenticated`.
    * Established 4 tenant-scoped RLS policies on `sys.outbox_events` (`outbox_events_insert`, `outbox_events_select`, `outbox_events_update`, `outbox_events_delete`).
  - Live demographic, student, and outbox persistence verified end-to-end (`core.persons`, `stu.students`, `doc.documents`, `sys.outbox_events`), joined read-back verified, outbox same-transaction read-back with payload assertions verified, cross-tenant read denial (0 rows), cross-tenant write denial (RLS violation), atomic failure rollback verified, and unconditional rollback leaving zero residual rows.

---

## 2. Exact Files Changed

| File | Status | Description |
|---|---|---|
| `pom.xml` | Modified | Registered `<module>services/student-service</module>` in root reactor. |
| `services/student-service/pom.xml` | Created | Maven POM for STU-01 Student Domain Service with HikariCP, PostgreSQL driver, Jackson, and JUnit. |
| `services/student-service/.../config/DatabaseConfig.java` | Created | Resolves JDBC configuration via hierarchy, default unprivileged user `campx_app_server`, masks secrets. |
| `services/student-service/.../config/DatabaseConnectionManager.java` | Created | HikariCP connection pool manager with Supavisor transaction pooler compatibility (`prepareThreshold=0`). |
| `services/student-service/.../security/UserSecurityContext.java` | Created | Security context extraction, claims binding, `executeInTransaction` with `RESET ROLE` pool hygiene. |
| `services/student-service/.../security/GatewayHmacVerifier.java` | Created | HMAC-SHA256 signature verification with 60s replay window protection. |
| `services/student-service/.../model/StudentEntity.java` | Created | Student aggregate entity mapped to `stu.students`. |
| `services/student-service/.../model/PersonEntity.java` | Created | Bio-demographic entity mapped to `core.persons`. |
| `services/student-service/.../model/GuardianshipEntity.java` | Created | Legal/family relationship entity mapped to `stu.guardianships`. |
| `services/student-service/.../model/StudentStatus.java` | Created | Lifecycle state machine (`ACTIVE`, `SUSPENDED`, `DETAINED`, `GRADUATED`, `ALUMNI`, `WITHDRAWN`). |
| `services/student-service/.../model/Gender.java` | Created | Gender enum aligned with check constraint `ck_persons_gender`. |
| `services/student-service/.../model/EnrollStudentRequest.java` | Created | Ingress enrollment DTO with date parsing and validation. |
| `services/student-service/.../model/UpdateStudentStatusRequest.java` | Created | Status transition DTO. |
| `services/student-service/.../model/StudentProfileResponse.java` | Created | Composite profile DTO including person, student, and guardian summaries. |
| `services/student-service/.../model/StudentRosterItem.java` | Created | Roster search DTO with program, batch, roll_no, and person details. |
| `services/student-service/.../model/StudentRosterQuery.java` | Created | Filter criteria and cursor pagination query DTO. |
| `services/student-service/.../model/CursorPagedResult.java` | Created | Generic cursor pagination result wrapper. |
| `services/student-service/.../model/BatchAssignmentContract.java` | Created | Decoupled cross-domain contract for Academics domain (ACD-04). |
| `services/student-service/.../exception/*.java` | Created | Domain exceptions (`StudentValidationException`, `StudentNotFoundException`, `StudentConflictException`, `StudentLifecycleException`, `StudentSecurityViolationException`). |
| `services/student-service/.../repository/StudentRepository.java` | Created | Interface for student persistence. |
| `services/student-service/.../repository/PersonRepository.java` | Created | Interface for person identity persistence. |
| `services/student-service/.../repository/GuardianshipRepository.java` | Created | Interface for guardianship relationship persistence. |
| `services/student-service/.../repository/PostgresStudentRepository.java` | Created | PostgreSQL JDBC repository with atomic transactional outbox write to `sys.outbox_events` and RLS claims binding. |
| `services/student-service/.../repository/PostgresPersonRepository.java` | Created | PostgreSQL JDBC repository for `core.persons`. |
| `services/student-service/.../repository/PostgresGuardianshipRepository.java` | Created | PostgreSQL JDBC repository for `stu.guardianships`. |
| `services/student-service/.../repository/InMemoryStudentRepository.java` | Created | In-memory student repository for isolated unit testing. |
| `services/student-service/.../config/TestEnvironmentGuard.java` | Created | Test environment safety guard enforcing dev ref `azulbaxszsqxymqmlwfe` and strictly rejecting production (`ekspahbsbwdpraaqfqui`) and legacy staging (`kpwdvvcschankgggitnj`). |
| `services/student-service/.../identity/LiveApplicationRoleAuthTest.java` | Created | Focused live JDBC authentication test using strictly application credentials (`campx_app_server.azulbaxszsqxymqmlwfe`) without privileged fallback, reporting sanitized errors. |
| `services/student-service/.../identity/LiveStudentRuntimeSecurityTest.java` | Created | Live read-only runtime security test against dev ref `azulbaxszsqxymqmlwfe` verifying `campx_app_server` role attributes, fail-closed isolation, claims context binding, pool hygiene, and outbox catalog privileges/policies. |
| `services/student-service/.../repository/LivePostgresStudentPersistenceTest.java` | Modified | Strengthened live persistence test asserting outbox write with payload verification, post-rollback residual row check, and live atomic failure rollback assertion. |
| `api-gateway/.../identity/LiveOutboxGrantsExecutionTest.java` | Created | Approval-gated execution runner for `04_outbox_least_privilege_grants.sql` requiring `-Dcampx.approve.outbox.grants=true`. |
| `docs/reports/wave-4/g1-proposed-sql/04_outbox_least_privilege_grants.sql` | Modified | Minimal least-privilege SQL migration granting `USAGE ON SCHEMA sys TO authenticated, campx_app_server`, table DML on `sys.outbox_events`, and tenant RLS policies on outbox tables. |
| `frontend/src/app/api/college/students/route.ts` | Modified | Cut over to API Gateway `/api/v1/students`, removing direct database access. |

---

## 3. Commands and Test Results

| Command / Suite | Result | Counts (Run/Fail/Error/Skip) | Test Tier | Evidence Details |
|---|---|---|---|---|
| `mvn test -pl services/student-service "-Dtest=LivePostgresStudentPersistenceTest" "-Dcampx.approve.wave4.write=true"` | **BUILD SUCCESS** | **2 / 0 / 0 / 0** | Live PostgreSQL Integration (`azulbaxszsqxymqmlwfe`) | Document, person, student, and atomic outbox event inserts succeed. Read-back verified with payload inspection. Cross-tenant read denied (0 rows). Cross-tenant write denied (RLS violation). Live atomic rollback verified on constraint failure. Post-rollback residual data check verified (0 rows). |
| `mvn test -pl api-gateway "-Dtest=LiveOutboxGrantsExecutionTest" "-Dcampx.approve.outbox.grants=true"` | **BUILD SUCCESS** | **1 / 0 / 0 / 0** | Live DDL Migration (`azulbaxszsqxymqmlwfe`) | Executed owner-approved DEC-W4-OUTBOX migration. Verified `has_schema_privilege('authenticated', 'sys', 'usage') = true`, `has_table_privilege('authenticated', 'sys.outbox_events', 'insert') = true`, and all 4 RLS policies active. |
| `mvn test -pl services/student-service -Dtest=LiveStudentRuntimeSecurityTest` | **BUILD SUCCESS** | **3 / 3 PASS** | Live Runtime & Catalog Security | Verified direct `campx_app_server` connection, runtime security path, claims binding, pool hygiene, and outbox schema/table/RLS catalog status. |
| `mvn test -pl services/student-service -Dtest=LiveApplicationRoleAuthTest` | **BUILD SUCCESS** | **1 / 0 / 0 / 0** | Live JDBC App Role (`azulbaxszsqxymqmlwfe`) | Evaluated live connection strictly as `campx_app_server.azulbaxszsqxymqmlwfe`. Target guard passed. Effective user verified `campx_app_server` (`rolsuper=false`, `rolbypassrls=false`, `rolcanlogin=true`). Context switch and claims verified. |
| `mvn test -pl services/student-service "-Dtest=!Live*"` | **BUILD SUCCESS** | **39 / 0 / 0 / 0** | Unit & Mock JDBC | All 8 domain, lifecycle, enrollment, isolation, repository mock, and controller suites PASS. |
| `mvn test -pl api-gateway -Dtest=StudentGatewayRouteTest` | **BUILD SUCCESS** | **1 / 0 / 0 / 0** | Unit | Validates gateway route table maps `/api/v1/students` to port 8093. |
| `mvn test -pl logger` | **BUILD SUCCESS** | **58 / 0 / 0 / 0** | Unit & Outbox Foundation | All logger, flow tracing, audit repository, and transactional outbox suites PASS. |
| `node frontend/node_modules/typescript/bin/tsc --project frontend/tsconfig.json --noEmit` | **SUCCESS** | **0 errors** | Static Typecheck | Validates cutover `frontend/src/app/api/college/students/route.ts` against Next.js 15 types with zero compilation errors. |
| `mvn test-compile` | **BUILD SUCCESS** | All 16 modules | Compile | Complete platform compilation across all 16 reactor modules clean. |

---

## 4. End-to-End Evidence

### Transactional Path: UI → API Gateway → Java Domain Service → PostgreSQL
1. **Frontend UI Ingress:** `student-roster.tsx` invokes Next.js route handler `/api/college/students`.
2. **Next.js Route Proxy:** Validates active Supabase session and user role (`college_admin`), resolves college and tenant context, and proxies HTTP request to API Gateway (`http://localhost:8080/api/v1/students`) with authoritative headers (`X-User-Id`, `X-Tenant-Id`, `X-User-Role`, `X-College-Id`). Direct database queries via `@/lib/db` have been completely excised.
3. **API Gateway:** Evaluates path `/api/v1/students`, matches route mapping in `GatewayConfig.java`, validates HMAC and correlation context, and forwards to `student-service` on port 8093.
4. **Student Domain Service:** Authenticates request via `GatewayHmacVerifier` and `UserSecurityContext`, validates input DTOs, executes domain state machine rules, and delegates persistence to `PostgresStudentRepository`.
5. **PostgreSQL Persistence Status:**
   - Person and Student persistence: **VERIFIED LIVE** on `azulbaxszsqxymqmlwfe`.
   - Transactional Outbox persistence: **VERIFIED LIVE** on `azulbaxszsqxymqmlwfe`.
   - Cross-tenant read and write denial: **VERIFIED LIVE** on `azulbaxszsqxymqmlwfe`.
   - Post-rollback zero residual rows: **VERIFIED LIVE** on `azulbaxszsqxymqmlwfe`.

---

## 5. Tenant Isolation and Authorization
- **G1 Status / Evidence:** `campx_app_server` operational (`rolsuper=false`, `rolbypassrls=false`); `FORCE RLS` on Tier 1 tables; verified live.
- **Outbox Isolation Verified:** `sys.outbox_events` has RLS enabled with 4 active tenant-scoped policies (`outbox_events_insert`, `outbox_events_select`, `outbox_events_update`, `outbox_events_delete`) enforcing strict tenant boundaries matching `core.current_tenant_id()`. Cross-tenant insert attempts are denied with `ERROR: new row violates row-level security policy for table "outbox_events"`.

---

## 6. Events / Outbox / Intelligence Layer

| Event Type | Owner | Schema Version | Privacy Class | Outbox / Publication Test | Duplicate / Failure / Retry | Real Endpoint or Double | Status |
|---|---|---|---|---|---|---|---|
| `student.enrolled.v1` | `student-service` | 1.0.0 | RESTRICTED | Live DB Integration (`LivePostgresStudentPersistenceTest`) | Idempotent event_id (UUID v5), rollback on SQL error | Outbox table `sys.outbox_events` (Live Verified) | **VERIFIED COMPLETE** |
| `student.status_changed.v1` | `student-service` | 1.0.0 | CONFIDENTIAL | Unit & Mock JDBC (`autoCommit == false`) | Transition-specific deduplication | Outbox table `sys.outbox_events` | **VERIFIED COMPLETE** |
| `student.deactivated.v1` | `student-service` | 1.0.0 | CONFIDENTIAL | Unit & Mock JDBC (`autoCommit == false`) | Terminal event deduplication | Outbox table `sys.outbox_events` | **VERIFIED COMPLETE** |
| `BatchAssignmentContract` | `student-service` → Academics | 1.0.0 | INTERNAL | Unit Contract (`AcademicsDomainContractTest`) | Decoupled cross-domain DTO | Domain event publisher double | **VERIFIED COMPLETE** |
| `STUDENT_ENROLLED` | `audit` | 1.0.0 | RESTRICTED | Append-only ledger verification (`audit.events`) | Sequence-preserved, immutable | PostgreSQL live table `audit.events` | **VERIFIED COMPLETE** |

---

## 7. Resolution of Blocker DEC-W4-OUTBOX

- **Blocker Resolution:** Owner-approved least-privilege migration `04_outbox_least_privilege_grants.sql` was executed via `LiveOutboxGrantsExecutionTest` on dev ref `azulbaxszsqxymqmlwfe`.
- **Verified Operations:**
  1. `GRANT USAGE ON SCHEMA sys TO authenticated, campx_app_server;`
  2. `GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sys.outbox_events TO authenticated;`
  3. Created 4 tenant-scoped RLS policies on `sys.outbox_events` enforcing `core.current_tenant_id()`.
- **Live Verification Outcome:** `LivePostgresStudentPersistenceTest` succeeded (2/2 PASS) on `azulbaxszsqxymqmlwfe`.
- **Status:** **RESOLVED & COMPLETE**. Wave 4 is fully unblocked and complete. Ready to advance to Wave 5.
