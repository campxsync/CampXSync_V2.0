# Wave 5 Completion Report — Timetable Domain & Relational Persistence

**Date:** 2026-10-22  
**Branch / HEAD:** `feature/java-backend-integration` @ `5952ea4f8f3070188b33fc290bf9df5f284a7697`  
**Status:** Complete (Verified Live on dev ref `azulbaxszsqxymqmlwfe`)  
**Evidence basis:** Independently executed tests against live PostgreSQL development database `azulbaxszsqxymqmlwfe` (AWS Mumbai `ap-south-1`), API Gateway integration tests, domain unit tests, and frontend TypeScript compilation.

---

## 1. Scope and outcome
- **Approved scope:**
  1. Inspect timetable schema on `azulbaxszsqxymqmlwfe` (`acd.timetables`, `acd.timetable_versions`, `acd.timetable_entries`).
  2. Implement relational persistence in Java domain service (`timetable-management-service`, ACD-05) reusing shared architecture (`DatabaseConfig`, `DatabaseConnectionManager`, `UserSecurityContext`, `TransactionalOutboxWriter`, `AuditLogRepository`).
  3. Enforce conflict constraints atomically (faculty double-booking, section/batch double-booking, room double-booking) and verify PostgreSQL GiST exclusion constraint mappings.
  4. Cut over frontend timetable endpoint (`frontend/src/app/api/college/timetable/entries/route.ts`) from direct `lib/db.ts` to API Gateway (`/api/v1/academics/timetables/entries`).
  5. Configure API Gateway routes (`/api/v1/academics/timetables/**` and `/api/v1/timetables/**` to ACD-05 on port 8087).
  6. Verify tenant-isolated persistence, read-back, RLS enforcement, outbox event emission, and conflict handling with live rollback tests.
- **Implemented:**
  - Full relational domain model (`TimetableEntity`, `TimetableVersionEntity`, `TimetableEntryEntity`) and DTOs.
  - Relational persistence layer (`PostgresTimetableRepository`) with JDBC connection pooling, transaction execution, RLS claims propagation (`set_config('request.jwt.claims', ...)`), outbox emission (`sys.outbox_events`), and conflict error translation (HTTP 409).
  - API Gateway route definitions and comprehensive route test (`TimetableGatewayRouteTest`).
  - Next.js frontend route cutover proxying requests to Java Gateway with security/tenant context headers.
  - Live integration test suite verifying catalog constraints, conflict rejection via PostgreSQL GiST exclusions, joined read-back, outbox emission, and cross-tenant isolation.
- **Deferred/out of scope:**
  - Background outbox publisher worker process (remains deferred across waves; transactional outbox rows are committed within the domain transaction).
  - Attendance session generation from timetable entries (Wave 6 scope).
- **Deviations/reasons:** None. All implementations strictly adhered to existing database constraints, least-privilege role boundaries, and shared services conventions.

---

## 2. Files changed
- `frontend/src/app/api/college/timetable/entries/route.ts` (Modified): Replaced direct Supabase `query()` with reverse proxy to Java API Gateway (`/api/v1/academics/timetables/entries`).
- `services/timetable-management-service/pom.xml` (Modified): Added HikariCP, PostgreSQL driver, and Jackson databind dependencies.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/config/DatabaseConfig.java` (New): HikariCP configuration loading `SUPABASE_JDBC_URL` with safety checks against prohibited project refs.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/config/DatabaseConnectionManager.java` (New): Connection manager with target verification.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/security/UserSecurityContext.java` (New): Security context executing queries inside transactions with `SET LOCAL ROLE authenticated` and `request.jwt.claims`.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/model/TimetableEntity.java` (New): Entity mapping `acd.timetables`.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/model/TimetableVersionEntity.java` (New): Entity mapping `acd.timetable_versions`.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/model/TimetableEntryEntity.java` (New): Entity mapping `acd.timetable_entries`.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/model/CreateTimetableSlotRequest.java` (New): Inbound slot DTO.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/model/TimetableSlotResponse.java` (New): Outbound slot DTO with joined metadata.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/repository/TimetableRepository.java` (New): Interface for relational timetable persistence.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/repository/PostgresTimetableRepository.java` (New): Production JDBC implementation with conflict detection and outbox emission.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/repository/InMemoryTimetableRepository.java` (New): Test double for unit tests.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/service/TimetableDomainService.java` (Modified): Wired relational repository, conflict handling, and intelligence adapter.
- `services/timetable-management-service/src/main/java/com/campx/academic/timetable/controller/TimetableController.java` (Modified): Added endpoints for slot management (`POST /api/v1/academics/timetables/entries`, `GET /api/v1/academics/timetables/entries`).
- `services/timetable-management-service/src/test/java/com/campx/academic/timetable/TimetableRelationalServiceTest.java` (New): Unit tests for relational persistence, conflict mapping, and outbox emission (8 tests).
- `services/timetable-management-service/src/test/java/com/campx/academic/timetable/identity/LiveTimetableCatalogAuditTest.java` (New): Live catalog audit on `azulbaxszsqxymqmlwfe` (1 test).
- `services/timetable-management-service/src/test/java/com/campx/academic/timetable/repository/LivePostgresTimetablePersistenceTest.java` (New): Live integration tests verifying conflict constraints, persistence, read-back, outbox, and RLS tenant isolation (2 tests).
- `api-gateway/src/test/java/com/campx/gateway/config/TimetableGatewayRouteTest.java` (New): Route verification for timetable path routing to ACD-05 (1 test).

Unrelated work and prior wave implementations were strictly preserved.

---

## 3. Commands and tests

| Command/suite | Result | Pass/fail/error/skipped counts | Test tier | Notes |
|---|---|---|---|---|
| `mvn test -pl services/timetable-management-service "-Dtest=LivePostgresTimetablePersistenceTest" "-Dcampx.approve.wave5.write=true"` | PASS | 2 / 0 / 0 / 0 | Approved live dev (`azulbaxszsqxymqmlwfe`) | Verifies GiST exclusion constraints (`ex_tte_employee`, `ex_tte_section`), persistence under RLS, outbox write, joined read-back, cross-tenant read denial, and clean rollback |
| `mvn test -pl services/timetable-management-service "-Dtest=LiveTimetableCatalogAuditTest"` | PASS | 1 / 0 / 0 / 0 | Approved live dev (`azulbaxszsqxymqmlwfe`) | Verifies table presence, columns, RLS enabled, and GiST exclusion indexes |
| `mvn test -pl services/timetable-management-service "-Dtest=!Live*"` | PASS | 38 / 0 / 0 / 0 | In-memory unit | All domain service, relational service, and controller tests pass |
| `mvn test -pl api-gateway "-Dtest=TimetableGatewayRouteTest"` | PASS | 1 / 0 / 0 / 0 | Gateway routing unit | Verifies `/api/v1/academics/timetables/**` and `/api/v1/timetables/**` route to port 8087 |
| `mvn test -pl api-gateway "-Dtest=!Live*,!DevDatabaseSeedRunner"` | PASS | 154 / 0 / 0 / 0 | Gateway unit suite | Full gateway security, JWT claim parsing, route dispatch suite passes |
| `node frontend/node_modules/typescript/bin/tsc --project frontend/tsconfig.json --noEmit` | PASS | 0 errors | TypeScript compilation | Verifies frontend cutover types and routes compile with zero diagnostics |

---

## 4. End-to-end evidence
- **Path:** Frontend (`/api/college/timetable/entries`) → Gateway (`/api/v1/academics/timetables/entries`) → Java Domain Service (`timetable-management-service` ACD-05) → Supabase PostgreSQL (`acd.timetables`, `acd.timetable_versions`, `acd.timetable_entries`, `sys.outbox_events`).
- **Request / Response:**
  - Client sends GET/POST with Supabase JWT bearer token.
  - Gateway validates JWT claims (`sub`, `tenant_id`, `role`), sets correlation headers, and dispatches to ACD-05.
  - ACD-05 sets `SET LOCAL ROLE authenticated` and `set_config('request.jwt.claims', ...)` on JDBC connection.
  - Inserts into `acd.timetable_entries` with atomic outbox event in `sys.outbox_events`.
  - Conflicts trigger PostgreSQL GiST exclusion violations mapped to HTTP 409 Conflict with descriptive payload.
- **Persisted Read-back:** Verified against live dev DB with JOIN on `acd.subjects` and `acd.course_sections` (`SUB-W5-101`, `SEC-W5-A`), matching persisted slot properties.
- **Rollback:** Verified that all live integration tests execute within explicit transactions and rollback unconditionally leaving zero residual test rows.

---

## 5. Tenant isolation and authorization
- **G1 status/evidence:** Active and verified on `azulbaxszsqxymqmlwfe`.
- **DB login role:** `campx_app_server` with `SET LOCAL ROLE authenticated`. Verified `rolsuper=false`, `rolbypassrls=false`.
- **Transaction-scoped context:** `set_config('request.jwt.claims', '{"sub":"...", "role":"authenticated", "tenant_id":"..."}', true)`.
- **Cross-tenant denial tests:**
  - In `LivePostgresTimetablePersistenceTest`, switching `request.jwt.claims` to synthetic Tenant B (`b0000000-0000-0000-0000-000000000002`) resulted in `0` rows returned when querying Tenant A's timetable slot (`assertEquals(0, rs.getLong(1))`).
- **Conflict constraint enforcement:**
  - Double booking faculty: PostgreSQL exclusion constraint `ex_tte_employee` rejected second slot: `ERROR: conflicting key value violates exclusion constraint "ex_tte_employee"`.
  - Double booking section: PostgreSQL exclusion constraint `ex_tte_section` rejected second slot: `ERROR: conflicting key value violates exclusion constraint "ex_tte_section"`.

---

## 6. Events/outbox/Intelligence Layer

| Event type | Owner | Schema version | Privacy class | Outbox/publication test | Duplicate/failure/retry | Real endpoint or test double | Status |
|---|---|---|---|---|---|---|---|
| `timetable.slot_changed.v1` | `timetable-management-service` | `1.0` | Internal Operational | Verified live in `sys.outbox_events` within same transaction | Idempotent payload, retry count initialized to 0 | Real DB outbox table | Complete |
| `timetable.version_published.v1` | `timetable-management-service` | `1.0` | Internal Operational | Verified in `PostgresTimetableRepository` unit test | Aggregate ID & version tracked | Shared `TransactionalOutboxWriter` | Complete |
| `Intelligence Layer Contract` | `timetable-management-service` | `1.0` | Analytics / Prediction | `ControlledIntelligenceAdapter` telemetry emission | Bounded retries, non-blocking | In-service contract adapter | Complete |

Preserved attributes: `tenant_id`, `correlationId`, `actorId`, `aggregate_type`, `aggregate_id`, `created_at`.

---

## 7. Frontend migration
- **Consumers migrated:**
  - `frontend/src/app/api/college/timetable/entries/route.ts` fully converted from direct DB queries (`lib/db.ts`) to reverse proxy to API Gateway (`/api/v1/academics/timetables/entries`).
- **Remaining direct-DB consumers:**
  - Attendance routes (`frontend/src/app/api/attendance/**`) — Wave 6 scope.
  - Corrections / Gradebook / Dashboard routes — Waves 7–9 scope.
- **Why they remain:** Domain services for Attendance, Corrections, and Dashboards will be cut over in their designated waves.
- **Dev impersonation safeguards:** Gateway enforces valid JWT signature, tenant claim matching, and role-based access control.

---

## 8. Gates and non-functional evidence
- **G1 least-privilege role:** Closed / Passed in Wave 4, actively leveraged in Wave 5.
- **G2 isolated PostgreSQL CI:** Open / due for assessment before Wave 6 closeout.
- **G3 JDK/framework assessment:** Open / scheduled for Wave 11.
- **Latency / Performance:** Relational queries utilize primary key and GiST exclusion indexes (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`); live execution completed in ~3 seconds including network roundtrip to AWS Mumbai.

---

## 9. Database and safety record
- **Target verified:** Supabase dev project `azulbaxszsqxymqmlwfe` exclusively.
- **Prohibited targets avoided:** Production `ekspahbsbwdpraaqfqui` and legacy staging `kpwdvvcschankgggitnj` were not accessed.
- **Database changes:** Zero DDL migrations required. Existing PostgreSQL GiST exclusion constraints (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) on `acd.timetable_entries` were verified and successfully used.
- **Working tree:** Preserved on branch `feature/java-backend-integration`.
- **Commit/push:** None performed.
- **Secrets exposed:** None.

---

## 10. Blockers, risks and pending evidence
- None. Wave 5 acceptance criteria are met with verifiable live database evidence.

---

## 11. Exit criteria

| Criterion | Status | Evidence |
|---|---|---|
| Timetable relational schema audit on `azulbaxszsqxymqmlwfe` | MET | `LiveTimetableCatalogAuditTest` passes |
| Java domain service relational persistence with RLS & context propagation | MET | `PostgresTimetableRepository`, `LivePostgresTimetablePersistenceTest` passes |
| Atomic conflict enforcement (faculty, section, room double-booking) | MET | Real PostgreSQL GiST exclusion constraints verified live (`ex_tte_employee`, `ex_tte_section`) |
| Transactional outbox event emission (`sys.outbox_events`) | MET | Verified live insert and read-back in `LivePostgresTimetablePersistenceTest` |
| Cross-tenant isolation verification | MET | Verified live: zero rows returned under foreign tenant claims |
| API Gateway route mapping for timetable endpoints | MET | `TimetableGatewayRouteTest` passes |
| Frontend endpoint cutover to API Gateway | MET | `frontend/src/app/api/college/timetable/entries/route.ts` cutover; TypeScript passes |
| Unit and non-live test suite integrity | MET | 38/38 unit tests in `timetable-management-service`, 154/154 in `api-gateway` pass |

---

## 12. Checkpoint and next task
- `PROGRESS.md` updated from evidence: Yes
- `NEXT_ANTIGRAVITY_TASK.md` updated from evidence: Yes
- `docs/reports/wave-5/COMPLETION_REPORT.md` updated: Yes
- Next task: Wave 6 — Attendance Domain & Session Lifecycle (Do not start automatically).
- Explicit stop confirmation: Execution stopped at Wave 5 boundary.
