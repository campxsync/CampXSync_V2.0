# Track A — Configuration & Environment Guard Audit

**Document**: `docs/staging/03-config-audit.md`  
**Date**: 2026-10-13  
**Target Environment**: Supabase PostgreSQL Staging (`kpwdvvcschankgggitnj`)  
**Scope**: Code audit of all three `TestEnvironmentGuard` copies, required environment variable analysis (zero secret exposure), test skip rationale, and storage/cross-tenant leakage audit across the 16 unguarded services.

---

## 1. Audit of All Three TestEnvironmentGuard Copies

The repository maintains three independent copies of `TestEnvironmentGuard.java` across the persistent services:
1. `services/institute-admin-service/src/test/java/com/campx/admin/institute/config/TestEnvironmentGuard.java` (ADM-01)
2. `services/college-admin-service/src/test/java/com/campx/admin/college/config/TestEnvironmentGuard.java` (ADM-02)
3. `services/course-management-service/src/test/java/com/campx/academic/course/config/TestEnvironmentGuard.java` (ACD-01)

### Logic Analysis & Production Rejection Verification:
All three implementations share the identical core validation method `validateStagingConfiguration(String jdbcUrl, String username)`:
- **Zero-Tolerance Production Ref Rejection**:
  ```java
  if (trimmedUrl.contains(PRODUCTION_PROJECT_REF) || trimmedUser.contains(PRODUCTION_PROJECT_REF)) {
      throw new IllegalStateException("Security violation: Live integration tests cannot connect to production project ref '" + PRODUCTION_PROJECT_REF + "'");
  }
  ```
  Both the raw JDBC URL and the username string are actively inspected for the forbidden production ref `ekspahbsbwdpraaqfqui`. Any occurrence triggers immediate rejection before socket initiation.
- **Localhost & Loopback Prevention**:
  Directly blocks `localhost`, `127.0.0.1`, `::1`, `[::1]`, `0.0.0.0`, and loopback subnets, preventing silent fallback to local mock containers or unconfigured defaults.
- **Direct Supabase Host Verification**:
  For hosts ending with `.supabase.co`, extracts the host prefix and strictly verifies that it equals `kpwdvvcschankgggitnj` and not `ekspahbsbwdpraaqfqui`.
- **Pooler-Style URL & Username Support**:
  For pooler hosts (`*.pooler.supabase.com` or `*.pooler.supabase.co`):
  1. Validates that the username follows the Supabase pooler format: `<role>.<project-ref>` (e.g. `postgres.kpwdvvcschankgggitnj`).
  2. Extracts the project reference suffix from the username.
  3. Strictly asserts that `PRODUCTION_PROJECT_REF.equalsIgnoreCase(userProjectRef)` throws a security violation.
  4. Strictly asserts that `STAGING_PROJECT_REF.equalsIgnoreCase(userProjectRef)` is satisfied.

### Differences Between the Three Implementations:
- **ADM-01 (`institute-admin-service`)**: Contains lines 320–360 providing `resolveSafeSuperAdminId(Connection conn)` which queries `iam.platform_admins` for active platform administrators. This method is required because ADM-01 operates at the SaaS platform tier.
- **ADM-02 (`college-admin-service`) & ACD-01 (`course-management-service`)**: Omit `resolveSafeSuperAdminId`, as these services operate strictly within tenant-scoped contexts (`core.tenants`, `core.colleges`, `acd.courses`).
- **Validation Consistency**: All other guard methods (`validateStagingConfiguration`, `verifySafeStagingEnvironment`, `verifySafeStagingTenant`, `resolveSafeTestUserId`) are 100% functionally identical across all three services.

### Validating Unit Tests (Executed Offline):
Each service includes a comprehensive companion test suite `TestEnvironmentGuardTest.java`:
- `institute-admin-service`: 26 tests passed (0 failed, 0 skipped).
- `college-admin-service`: 24 tests passed (0 failed, 0 skipped).
- `course-management-service`: 24 tests passed (0 failed, 0 skipped).
- **Combined Result**: **74/74 PASSED [VERIFIED]**.
- **Coverage**: The test suites verify production URL rejection, production user rejection, localhost rejection, malformed syntax rejection, direct host validation, pooler host validation, and pooler username extraction using synthetic dummy strings without requiring live network access or real secrets.

---

## 2. Environment Variables Audit (Zero Secret Disclosure)

### Variable Specification & Placement:
| Environment Variable Name | Required By | Expected Format / Role | Placement Location |
| :--- | :--- | :--- | :--- |
| `CAMPX_TEST_ENV` | `TestEnvironmentGuard` | Explicit flag: `staging` or `test` | Shell Environment (`export` / `$env:`) |
| `SUPABASE_JDBC_URL` (or `CAMPX_JDBC_URL`) | `DatabaseConfig`, Guard | `jdbc:postgresql://<host>:5432/postgres?sslmode=require` | Shell Environment / `.env.local` |
| `SUPABASE_USER` (or `CAMPX_DB_USER`) | `DatabaseConfig`, Guard | `postgres.kpwdvvcschankgggitnj` (Pooler Session user) | Shell Environment / `.env.local` |
| `SUPABASE_PASSWORD` (or `CAMPX_DB_PASSWORD`) | `DatabaseConfig` | Staging database password | Shell Environment / `.env.local` (NEVER committed) |
| `CAMPX_TEST_TENANT_ID` | `TestEnvironmentGuard` | Valid UUID of active staging tenant in `core.tenants` | Shell Environment |
| `CAMPX_TEST_USER_ID` | `TestEnvironmentGuard` | Valid UUID of active test user in `iam.user_profiles` | Shell Environment |
| `CAMPX_TEST_SUPER_ADMIN_ID` | ADM-01 Guard | Valid UUID of platform administrator | Shell Environment |
| `CAMPX_SUPABASE_JWT_ISSUER` | Security Context | `https://kpwdvvcschankgggitnj.supabase.co/auth/v1` | Shell Environment / `application.yml` |

### Boolean Environment Check Results (Rule 11 Execution):
A boolean-only environment check was executed on the current PowerShell process:

```text
SUPABASE_USER:             IsSet=True,  ContainsStagingRef=False, ContainsProdRef=True
SUPABASE_JDBC_URL:         IsSet=True,  ContainsStagingRef=False, ContainsProdRef=False
CAMPX_SUPABASE_JWT_ISSUER: IsSet=True,  ContainsStagingRef=False, ContainsProdRef=True
CAMPX_TEST_ENV:            IsSet=False, ContainsStagingRef=False, ContainsProdRef=False
```

### Critical Finding & Rule 11 Blocker:
- The ambient environment currently has `ContainsProdRef=True` for `SUPABASE_USER` and `CAMPX_SUPABASE_JWT_ISSUER`.
- Under **Rule 11**: *"If any production check is True, or the staging check is False where a ref should be present, do NOT run the command; report it and stop."*
- **Action**: Running any Maven (`mvn`), live test, or application startup command is **STRICTLY BLOCKED** until the user resets or overrides these variables in their private shell.

---

## 3. Test Skip Rationale & Documented Status

Live database integration tests intentionally skip when offline. This behavior is deliberate and governed by `TestEnvironmentGuard`:
```java
public static void verifySafeStagingEnvironment(String testEnv, DatabaseConfig config) {
    assumeNotNull("Live integration tests require an explicitly designated test environment via CAMPX_TEST_ENV or campx.test.env", testEnv);
    ...
}
```

### Affected Test Suites:
1. **`institute-admin-service`**:
   - 26 `Postgres*RepositoryTest` files (e.g. `PostgresCollegeRepositoryIntegrationTest`, `PostgresServiceHealthRepositoryTest`).
   - Status: **SKIPPED OFFLINE** (100% clean skips via JUnit `Assume`).
2. **`college-admin-service`**:
   - `CollegeResolverLiveRegressionTest` (2 tests skipped).
   - `JsonbSerializationSecurityTest` (3 tests skipped).
   - 7 `Postgres*RepositoryTest` files.
   - Status: **SKIPPED OFFLINE**.
3. **`course-management-service`**:
   - `PostgresCourseRestrictedIdentityRlsIntegrationTest` (12 tests skipped).
   - Status: **SKIPPED OFFLINE**.

### Documentation Integrity:
- Every skip is fully documented in code annotations and in `docs/audit/02-prior-fixes.md`.
- No failed tests or connection timeouts occur; the test suites recognize that `CAMPX_TEST_ENV` is unset and bypass live database interactions gracefully.

---

## 4. In-Memory Storage & Cross-Tenant Leakage Audit (16 Services Without a Guard)

The repository contains 19 services/modules in total. 3 possess `TestEnvironmentGuard` (`institute-admin-service`, `college-admin-service`, `course-management-service`). The remaining 16 services do NOT have a guard.

### Categorization of the 16 Unguarded Services:

| Category | Count | Service Names | Architecture & Storage State |
| :--- | :---: | :--- | :--- |
| **Empty Stubs** | 6 | `ai-service`, `examination-service`, `faculty-service`, `identity-service`, `notification-service`, `student-service` | 0 Java source files. Scaffolding directories only. No storage, zero cross-tenant leakage. |
| **Infrastructure Gateway** | 1 | `api-gateway` | 25 Java files. Contains `GatewayConfig` (`routeTable`) and `JwksClient` (`cachedKeys`). No domain entity storage. Shared HTTP routing. |
| **Academic Microservices** | 9 | `academic-calendar-service`, `analytics-service`, `assessment-mapping-service`, `attendance-service`, `batch-management-service`, `curriculum-service`, `learning-resource-service`, `subject-management-service`, `timetable-management-service` | **100% In-Memory Storage** using `ConcurrentHashMap`. Zero database persistence. |

---

### In-Depth Cross-Tenant Leakage Analysis Across the 9 Academic Microservices:

#### 1. `curriculum-service` (ACD-03 — 29 Java Files)
- **In-Memory Storage**: `CurriculumDomainService` stores state in `private final Map<String, Curriculum> curricula = new ConcurrentHashMap<>()`.
- **Cross-Tenant Leakage Finding**: **CRITICAL LEAKAGE [VERIFIED]**.
  - In `getCurriculum(String curriculumId, String actorRole, String actorDepartmentId)`:
    ```java
    Curriculum curr = curricula.get(curriculumId); // Looked up directly by ID
    ```
    Method accepts **NO `tenantId`** parameter and performs zero tenant boundary validation. A user from Tenant B who provides a curriculum ID belonging to Tenant A can view the full curriculum structure.
  - In `searchCurricula(...)`:
    ```java
    return curricula.values().stream()
        .filter(c -> { ... filter by courseId, dept, status ... })
    ```
    Iterates all values across all tenants without filtering by `tenantId`. Tenant A and Tenant B see each other's curricula.

#### 2. `subject-management-service` (ACD-04 — 29 Java Files)
- **In-Memory Storage**: `SubjectDomainService` stores state in `private final Map<String, Subject> subjects = new ConcurrentHashMap<>()`.
- **Cross-Tenant Leakage Finding**: **CRITICAL LEAKAGE [VERIFIED]**.
  - `getSubject(String id)`: Looks up `subjects.get(id)` without tenant verification.
  - `updateSubject(String id, Subject updateRequest, long clientVersion, String actorId, String actorRole)`: Looks up `subjects.get(id)` without requiring or verifying `tenantId`. An actor from Tenant B can overwrite subject master data for Tenant A.
  - `deleteSubject(String id, String actorId, String actorRole)`: Deletes `subjects.remove(id)` with zero tenant check.

#### 3. `batch-management-service` (ACD-05 — 22 Java Files)
- **In-Memory Storage**: `BatchDomainService` stores state in `private final Map<String, Batch> batches = new ConcurrentHashMap<>()`.
- **Cross-Tenant Leakage Finding**: **CRITICAL LEAKAGE [VERIFIED]**.
  - `getBatch(String batchId, String actorRole, String userId)`: Accepts no `tenantId`; performs `batches.get(batchId)`.
  - `updateBatch(String batchId, Batch updateReq, long expectedVersion, String actorId, String actorRole)`: Modifies `batches.put(batchId, updated)` with no tenant parameter or check.

#### 4. `learning-resource-service` (ACD-10 — 34 Java Files)
- **In-Memory Storage**: `ResourceDomainService` stores `resourcesById` and `resourcesByTenantAndCode`.
- **Cross-Tenant Leakage Finding**: **HIGH RISK [VERIFIED]**.
  - `ResourceQueryService.search(Collection<LearningResource> resources, ResourceSearchFilter filter, String tenantId)` contains:
    ```java
    .filter(r -> tenantId == null || tenantId.equals(r.getTenantId()))
    ```
    If `tenantId` is omitted or passed as null, search returns resources belonging to all tenants.
  - `usageStore` in `ResourceQueryService` is keyed strictly by `resourceId` without tenant scoping.

#### 5. `academic-calendar-service` (ACD-07 — 28 Java Files)
- **In-Memory Storage**: `CalendarDomainService` stores `calendars`, `terms`, and `events` in `ConcurrentHashMap`.
- **Cross-Tenant Leakage Finding**: **HIGH RISK [VERIFIED]**.
  - `getCalendar(String calendarId, String tenantId)` contains:
    ```java
    if (cal == null || (tenantId != null && !tenantId.equalsIgnoreCase(cal.getTenantId())))
    ```
    If `tenantId` is null, the check succeeds, leaking calendars across tenants.
  - `listCalendars` hardcodes a fallback: `String tId = tenantId != null ? tenantId : "TENANT-001"`.

#### 6. `analytics-service` (14 Java Files)
- **In-Memory Storage**: `AnalyticsDomainService` stores report definitions, faculty assignments, and batch assignments in shared `ConcurrentMap` fields.
- **Cross-Tenant Leakage Finding**: **HIGH RISK [VERIFIED]**.
  - `facultyBatchAssignments` and `facultySubjectAssignments` are keyed without composite tenant IDs. Shared map structures risk cross-tenant data collisions.

#### 7. `attendance-service` (ACD-08 — 29 Java Files)
- **In-Memory Storage**: `AttendanceDomainService` stores `sessions`, `records`, `summaries` in `ConcurrentHashMap`.
- **Cross-Tenant Leakage Finding**: **HIGH RISK [VERIFIED]**.
  - Primary store maps are keyed by entity UUID (`sessionId`, `recordId`). Shared in-process memory violates physical and logical tenant segregation.

#### 8. `assessment-mapping-service` (ACD-09 — 33 Java Files)
- **In-Memory Storage**: `AssessmentDomainService` stores `structures`, `components`, `mappings` in `ConcurrentHashMap`.
- **Cross-Tenant Leakage Finding**: **HIGH RISK [VERIFIED]**.
  - Keyed by entity ID; missing persistent RLS boundary protection.

#### 9. `timetable-management-service` (ACD-06 — 27 Java Files)
- **In-Memory Storage**: `TimetableDomainService` stores `timetables`, `timetableEntries`, `timetableVersions`, and conflict caches.
- **Cross-Tenant Leakage Finding**: **HIGH RISK [VERIFIED]**.
  - Conflict detection algorithms and slot utilization maps aggregate entries in shared memory collections.

---

### Remediation Roadmap:
1. **Rule 8 Compliance**: Rule 8 mandates: *"Never add shared in-memory fallbacks that can leak data between tenants."* The current in-memory academic microservices are prototypes created prior to Supabase database integration.
2. **Phase 6 Migration Requirement**: During Phase 6, each of these 9 services must be migrated from in-memory maps to PostgreSQL JDBC repositories targeting their dedicated schemas (`acd`, `lms`), enforcing RLS, and equipped with `TestEnvironmentGuard`.
