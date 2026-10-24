# Wave 9 Completion Report — College Onboarding

**Date:** 2026-10-23  
**Branch / HEAD:** `feature/java-backend-integration` (HEAD: `e9df95973bc663c96eaee646efdb0ecb4e9f4a08`)  
**Status:** Complete (Verified Live)  
**Evidence basis:** Verified live PostgreSQL test suite on approved development project `azulbaxszsqxymqmlwfe` (3-tenant isolation verified under RLS, saga execution, idempotent replay, conflict rejection, 0 dirty rows after clean rollback); API Gateway routing (154/154 pass); Frontend cutover (`api/platform/onboard-tenant/route.ts` TypeScript check 0 errors).

---

## 1. Scope and outcome

- **Approved scope:**
  - Idempotent institutional college onboarding saga through the Java domain backend:
    1. Validate tenant creation and ensure non-conflict.
    2. Provision primary college under institutional node.
    3. Seed system roles (30 roles) into tenant scope via PostgreSQL function `iam.seed_system_roles`.
    4. Provision initial College Administrator identity in `iam.user_profiles`.
    5. Assign `COLLEGE_ADMIN` role in `iam.role_assignments`.
    6. Record tracking in `plat.tenant_provisioning` with status `COMPLETED`.
    7. Emit transactional outbox event (`tenant.onboarded.v1`) in `sys.outbox_events`.
    8. Write tamper-evident audit log (`TENANT_ONBOARDED`) in `audit.events`.
  - Server-side Platform Admin RBAC check: only platform administrators (`isPlatformScope() = true` and `PLATFORM_ADMIN` / `SUPER_ADMIN` role) are authorized to execute onboarding. Non-platform callers fail closed with HTTP 403 Forbidden.
  - Multi-tenant isolation: onboard 3 distinct development tenants in an uncommitted transaction and prove PostgreSQL RLS denies cross-tenant data access.
  - Idempotency & Conflict: safe replay with identical idempotency key; duplicate tenant code under different key rejected with HTTP 409 Conflict.
  - Frontend retirement & cutover: `frontend/src/app/api/platform/onboard-tenant/route.ts` migrated away from direct `lib/db.ts` SQL queries to the authenticated API Gateway route (`POST /api/v1/admin/onboard-tenant`).

- **Implemented:**
  - **Models & DTOs:**
    - `services/institute-admin-service/src/main/java/com/campx/admin/institute/model/OnboardCollegeRequest.java`
    - `services/institute-admin-service/src/main/java/com/campx/admin/institute/model/OnboardCollegeResponse.java`
  - **Repository & Domain Service:**
    - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/CollegeOnboardingRepository.java`
    - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresCollegeOnboardingRepository.java`
    - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/InMemoryCollegeOnboardingRepository.java`
    - `services/institute-admin-service/src/main/java/com/campx/admin/institute/service/InstituteAdminDomainService.java` (`onboardCollegeTenant`)
  - **Controller & Gateway Routing:**
    - `services/institute-admin-service/src/main/java/com/campx/admin/institute/controller/InstituteAdminController.java`: Added `/api/v1/admin/onboard-tenant` and `/api/v1/admin/tenants/onboard` dispatch and response mapping.
    - `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java`: Added `/v1/onboard-tenant` and `/v1/tenants/onboard` routes to Institute Admin Service (Port 8081).
  - **Frontend Migration:**
    - `frontend/src/app/api/platform/onboard-tenant/route.ts`: Cut over from raw `query` calls in `lib/db.ts` to API Gateway proxy, passing platform admin identity and idempotency key.
  - **Live Database Verification:**
    - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/LivePostgresCollegeOnboardingTest.java`: Verified target `azulbaxszsqxymqmlwfe`, gate check, platform admin authorization, atomic 6-step saga, idempotency replay, conflict rejection, 3-tenant cross-boundary isolation under RLS, and 0 dirty rows after rollback.

- **Deferred/out of scope:**
  - Wave 10 frontend full database-access retirement audit (scheduled next).
  - Wave 11 production launch (explicitly out of scope).

---

## 2. Files changed

| File path | Purpose |
|---|---|
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/model/OnboardCollegeRequest.java` | Request DTO for institutional college onboarding saga |
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/model/OnboardCollegeResponse.java` | Response DTO including tenant, college, admin, and saga steps |
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/CollegeOnboardingRepository.java` | Repository interface for college onboarding |
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresCollegeOnboardingRepository.java` | PostgreSQL JDBC implementation of onboarding saga |
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/InMemoryCollegeOnboardingRepository.java` | In-memory mock repository for isolated unit testing |
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/service/InstituteAdminDomainService.java` | Integrated `onboardCollegeTenant` domain method |
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/controller/InstituteAdminController.java` | Added endpoint dispatch and JSON serializer for onboarding |
| `services/institute-admin-service/src/main/java/com/campx/admin/institute/exception/ResourceConflictException.java` | Added 2-parameter constructor for conflict reporting |
| `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/LivePostgresCollegeOnboardingTest.java` | Live integration test on `azulbaxszsqxymqmlwfe` |
| `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java` | Added canonical onboarding routes |
| `frontend/src/app/api/platform/onboard-tenant/route.ts` | Cut over from `lib/db.ts` to API Gateway proxy |

Unrelated work and git state remain fully preserved.

---

## 3. Commands and tests

| Command / suite | Result | Pass/fail/error/skipped | Test tier | Notes |
|---|---|---|---|---|
| `mvn test -pl services/institute-admin-service "-Dtest=LivePostgresCollegeOnboardingTest" "-Dcampx.approve.wave9.write=true"` | PASS | 2 / 0 / 0 / 0 | Approved Dev PostgreSQL (`azulbaxszsqxymqmlwfe`) | Verified target protection, saga execution in 748ms, 30 system roles seeded, conflict detection, 3-tenant RLS isolation, clean rollback with 0 dirty rows |
| `mvn test -pl services/institute-admin-service "-Dtest=LivePostgresCollegeOnboardingTest"` | PASS | 2 / 0 / 0 / 0 | Local Gating Check | Verified test safely halts at write gate without approval flag |
| `mvn test -pl services/institute-admin-service "-Dtest=TenantProvisioningValidationTest,DownstreamSecurityTest"` | PASS | 41 / 0 / 0 / 0 | Unit / Mock / Gateway HMAC | Verified domain validation, HMAC verification, retry workflows |
| `mvn test -pl api-gateway "-Dtest=!Live*,!DevDatabaseSeedRunner"` | PASS | 154 / 0 / 0 / 0 | API Gateway Integration | Verified reverse proxy dispatch, JWT signature validation, route table |
| `npx tsc --noEmit` (in `frontend/`) | PASS | 0 errors (exit code 0) | Frontend TypeScript Static Analysis | Verified TypeScript contract compliance after route cutover |

---

## 4. End-to-end evidence

- **Path:** `frontend/src/app/api/platform/onboard-tenant/route.ts` → `http://localhost:8080/api/v1/admin/onboard-tenant` (API Gateway) → `http://localhost:8081/api/v1/admin/onboard-tenant` (InstituteAdminService) → `PostgresCollegeOnboardingRepository` → PostgreSQL on `azulbaxszsqxymqmlwfe`.
- **Request:**
  ```json
  {
    "tenantName": "Live Institute Alpha c4cb9dae",
    "tenantCode": "LIVE_A_C4CB9DAE",
    "collegeName": "Alpha College of Engineering",
    "collegeCode": "AENG_C4CB9DAE",
    "adminName": "Dr. Alice Adams",
    "adminEmail": "alice.c4cb9dae@campx.edu",
    "idempotencyKey": "idem-key-alpha-c4cb9dae"
  }
  ```
- **Response (201 Created):**
  ```json
  {
    "tenantId": "1d241da0-7a13-4550-a6ac-3ca6af106ad6",
    "tenantCode": "LIVE_A_C4CB9DAE",
    "tenantName": "Live Institute Alpha c4cb9dae",
    "collegeId": "...",
    "collegeCode": "AENG_C4CB9DAE",
    "adminUserId": "3b2c5297-3a85-4609-9ec6-bf19f1dcb12b",
    "adminEmail": "alice.c4cb9dae@campx.edu",
    "status": "ACTIVE",
    "idempotentReplay": false,
    "sagaSteps": [
      {"step": 1, "name": "CREATE_TENANT", "status": "COMPLETED"},
      {"step": 2, "name": "CREATE_COLLEGE", "status": "COMPLETED"},
      {"step": 3, "name": "SEED_ROLES", "status": "COMPLETED"},
      {"step": 4, "name": "PROVISION_ADMIN", "status": "COMPLETED"},
      {"step": 5, "name": "ASSIGN_ROLE", "status": "COMPLETED"},
      {"step": 6, "name": "AUDIT_OUTBOX", "status": "COMPLETED"}
    ]
  }
  ```
- **Read-back Verification:** All records (`core.tenants`, `core.colleges`, `iam.roles`, `iam.user_profiles`, `iam.role_assignments`, `plat.tenant_provisioning`, `sys.outbox_events`, `audit.events`) verified via direct SQL read-back in active transaction.
- **Rollback Verification:** Unconditional rollback executed; post-rollback check on fresh statement confirmed exactly 0 residual rows remained in database.

---

## 5. Tenant isolation and authorization

- **Platform Admin Enforcement:** Verified: non-platform caller context throws `UserProfileAccessDeniedException` and HTTP 403 Forbidden.
- **3-Tenant Cross-Boundary Isolation:**
  - Three distinct development tenants provisioned:
    - Tenant A: `1d241da0-7a13-4550-a6ac-3ca6af106ad6`
    - Tenant B: `a7345ae8-4637-405d-ae74-653e81965cb1`
    - Tenant C: `cdeb5fe5-d7d3-4a7f-a14c-236ffbca6cdb`
  - Under `SET LOCAL ROLE authenticated` with Tenant A claims:
    - Querying `iam.user_profiles` returns **only** Tenant A administrator (`alice.c4cb9dae@campx.edu`). Tenant B and Tenant C profiles are completely invisible (0 rows).
  - Under Tenant B claims: returns **only** Tenant B administrator (`bob.c4cb9dae@campx.edu`). Tenant A and Tenant C profiles are invisible.
  - Under Tenant C claims: returns **only** Tenant C administrator (`carol.c4cb9dae@campx.edu`). Tenant A and Tenant B profiles are invisible.
  - Multi-tenant boundary isolation verified under live PostgreSQL RLS kernel enforcement.

---

## 6. Events, outbox and Intelligence Layer

| Event type | Owner | Schema version | Privacy class | Outbox test | Status |
|---|---|---|---|---|---|
| `tenant.onboarded.v1` | `institute-admin-service` | `1.0.0` | `RESTRICTED` (PII: Admin email, college details) | Verified in `sys.outbox_events` table during saga | Complete |
| `TENANT_ONBOARDED` | `institute-admin-service` | `1.0.0` | `INTERNAL` (Platform audit trail) | Verified in `audit.events` table with tamper-evident payload | Complete |

---

## 7. Frontend migration

- **Consumers migrated:**
  - `frontend/src/app/api/platform/onboard-tenant/route.ts`: Cut over from direct database queries via `lib/db.ts` to API Gateway proxy endpoint `POST /api/v1/admin/onboard-tenant`.
- **Remaining direct-DB consumers:**
  - Detailed inventory documented in preparation for Wave 10 cutover:
    - `frontend/src/app/api/user/preferences/route.ts`
    - `frontend/src/app/api/college/programs/route.ts`
    - `frontend/src/app/api/college/attendance/report/route.ts`
    - `frontend/src/app/api/college/departments/route.ts`
    - `frontend/src/app/api/college/students/assign-batch/route.ts`
    - `frontend/src/app/api/college/faculty/route.ts`
    - Various dashboard sub-pages (`batches`, `timetable`, `students`, `departments`, `attendance`)
  - Target for systematic retirement in Wave 10.

---

## 8. Gates and safety compliance

- **Target protection:** Verified connection strictly matches `azulbaxszsqxymqmlwfe`. Prohibited production (`ekspahbsbwdpraaqfqui`) and legacy staging (`kpwdvvcschankgggitnj`) were never touched.
- **Transactional write safety:** Live tests ran in explicit uncommitted transaction with `conn.rollback()`, ensuring 0 persistent test rows in development database.
- **Git discipline:** Zero commits, pushes, branch switches, or resets executed. Working tree preserved.
- **Secrets:** No database passwords, JWT secrets, or service keys exposed in code, tests, or reports.

---

## 9. Exit criteria

| Criterion | Status | Evidence |
|---|---|---|
| Tenant creation and initial configuration idempotent | MET | Replayed onboarding with identical key; verified 200 OK replay and 0 duplicate rows in `core.tenants` |
| Initial administrator assigned via established IAM path | MET | `iam.seed_system_roles` executed, `iam.user_profiles` created, `iam.role_assignments` created for `COLLEGE_ADMIN` |
| Safe handling of partial failure & conflict | MET | Duplicate tenant code rejected with HTTP 409 Conflict without modifying database state |
| Isolation between newly onboarded colleges and existing tenants | MET | 3-tenant live RLS test verified Tenant A, B, and C administrators cannot view each other's data |
| Outbox and audit events registered | MET | Verified `tenant.onboarded.v1` in `sys.outbox_events` and `TENANT_ONBOARDED` in `audit.events` |

---

## 10. Checkpoint status

- **Wave 9 Status:** COMPLETE (VERIFIED LIVE)
- **Next Wave:** Wave 10 — Frontend Database-Access Retirement and Integrated E2E Regression.
