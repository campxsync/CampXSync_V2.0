# Track B — MVP Baseline Assessment

**Document**: `docs/mvp/01-baseline.md`  
**Date**: 2026-10-13  
**Branch**: `ReviewBranch`  
**Binding Roadmap**: `docs/MASTER_EXECUTION_PROMPT.md` (Phase 4)  
**Priority Order**: `ADM-01` (Institute Admin) → `ADM-02` (College Admin) → `ACD-01` (Course Management)

---

## 1. Executive Summary

This document establishes the verified baseline of CampXSync V2.0 MVP modules, reconciling repository source code, tests, and execution records against the authoritative requirements in `docs/MASTER_EXECUTION_PROMPT.md`.

In accordance with Phase 4 evidence rules:
- Every claim is labeled: `VERIFIED`, `REPORTED`, `ASSUMED`, or `UNKNOWN`.
- A skipped test is **not a pass**. Tests executing offline via in-memory mock or stub repositories are classified as **VERIFIED LOCALLY / LIVE VERIFICATION PENDING STAGING**.
- No completed work is restarted.

---

## 2. Module Implementation Baseline Matrix

| Module ID | Module Name | Scope / Key Entities | Implementation State | Test Suite & Observed Results | Live DB Verification Status |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **ADM-01** | **Institute Admin (Platform Tier)** | • Step 1: User Profiles (`iam.user_profiles`)<br>• Step 2: Role Templates (`iam.role_templates`)<br>• Step 3: Tenants & Colleges (`core.tenants`, `core.colleges`)<br>• Step 4: System Lookups & Sequences (`core.number_sequences`, `core.lookup_values`)<br>• Step 5: Commercial & Billing (`plat.subscriptions`, `plat.invoices`) | **PARTIALLY COMPLETE**<br>- Step 1 User Profiles: Fully implemented with JDBC persistence & RLS.<br>- Steps 2–5: Repositories exist (`Postgres*` + `InMemory*`), HTTP controller routes wired. | • `UserProfileControllerHttpTest`: 23/23 PASSED [VERIFIED]<br>• `UserProfileRepositoryValidationTest`: 24/24 PASSED [VERIFIED]<br>• `InstituteAdminServiceTest`: 48/48 PASSED [VERIFIED]<br>• `InstituteAdminNewEndpointsHttpTest`: 4/4 PASSED [VERIFIED]<br>• Total Unit Suite: 260/260 PASSED [VERIFIED]<br>• Live Postgres Tests: 26 SKIPPED offline [VERIFIED] | **LIVE VERIFICATION PENDING STAGING**<br>(Gated on Track A `GO PROVISION-TEST-TENANT`) |
| **ADM-02** | **College Admin (Tenant Tier)** | • College Profile (`core.colleges`)<br>• College Settings (`cfg.setting_values`)<br>• Feature Overrides (`cfg.feature_overrides`)<br>• Local Governance Policies (`core.local_policies`)<br>• Governance Documents (`doc.documents`)<br>• Departments & Programs (`core.departments`) | **PARTIALLY COMPLETE**<br>- Service wired with both `InMemory*` and `Postgres*` repositories.<br>- Tenant security context enforced (`CollegeResolver` eliminates synthetic fallback).<br>- Fail-fast bootstrap without silent fallback. | • `CollegeAdminTenantIsolationRegressionTest`: 10/10 PASSED [VERIFIED]<br>• `FailFastPersistenceBootstrapTest`: 2/2 PASSED [VERIFIED]<br>• `CollegeAdminServiceTest`: 52/52 PASSED [VERIFIED]<br>• `CollegeResolverLiveRegressionTest`: 2 SKIPPED offline [VERIFIED]<br>• `JsonbSerializationSecurityTest`: 3 SKIPPED offline [VERIFIED]<br>• Live Postgres Tests: 7 SKIPPED offline [VERIFIED] | **LIVE VERIFICATION PENDING STAGING**<br>(Gated on Track A `GO PROVISION-TEST-TENANT`) |
| **ACD-01** | **Course Management Service** | • Course Catalog (`acd.courses`)<br>• Course Offerings (`acd.course_offerings`)<br>• State Lifecycle (DRAFT → SUBMITTED → APPROVED → PUBLISHED → SUSPENDED)<br>• RLS Security Migration `V20261011_01` | **PARTIALLY COMPLETE**<br>- `PostgresCourseRepository` and `InMemoryCourseRepository` implemented.<br>- Hardened RLS policies active on staging catalog.<br>- Domain service wired with fail-fast persistence. | • `CourseDomainServiceTest`: 20/20 PASSED [VERIFIED]<br>• `CoursePersistenceAndTenantIsolationRegressionTest`: 10/10 PASSED [VERIFIED]<br>• `CourseControllerIntegrationTest`: 14/14 PASSED [VERIFIED]<br>• `PostgresCourseRestrictedIdentityRlsIntegrationTest`: 12 SKIPPED offline [VERIFIED]<br>• `PostgresCourseRepositoryTest`: 8 SKIPPED offline [VERIFIED] | **LIVE VERIFICATION PENDING STAGING**<br>(Gated on Track A `GO PROVISION-TEST-TENANT`) |
| **ACD-02..10** | **Academic Microservices (9 Services)** | • Curricula, Subjects, Batches, Timetable, Attendance, Calendar, Resources, Assessment, Analytics | **NOT STARTED (PERSISTENCE)**<br>In-memory `ConcurrentHashMap` only; zero PostgreSQL code or environment guards. | In-memory unit tests pass in isolation. | **BLOCKED BY PHASE 6**<br>(Scheduled for post-Track A migration continuation) |

---

## 3. Detailed Per-Module Evaluation

### 3.1 ADM-01 Institute Admin Service

#### Architectural Classification:
- **Core Persistence**: Hybrid (PostgreSQL JDBC repositories implemented for 26 entities; `UserProfileRepository` actively handles Step 1).
- **Tenant & Security Context**: Enforced via `UserSecurityContext`, `GatewayHmacVerifier`, and `iam.sc_*` helper patterns.
- **Observed Test Execution**:
  - `UserProfileControllerHttpTest`: 23 ran, 23 passed, 0 skipped.
  - `UserProfileRepositoryValidationTest`: 24 ran, 24 passed, 0 skipped.
  - Overall unit test suite: 260 ran, 260 passed, 0 failed, 0 skipped.
  - Live PostgreSQL integration tests (`Postgres*RepositoryTest` across 26 files): Cleanly skipped offline via `assumeNotNull` in `TestEnvironmentGuard`.
- **Remaining MVP Gaps**:
  1. Live verification against Supabase staging after test tenant provisioning (`CRIT-14`).
  2. Integration of `RoleTemplateRepository` authorization checks (`isPlatformAdmin`) in offline repository fallback.
  3. Step 2 (Platform Role Templates) API contract hardening.

---

### 3.2 ADM-02 College Admin Service

#### Architectural Classification:
- **Core Persistence**: Wired with repository interfaces (`CollegeProfileRepository`, `CollegeSettingRepository`, `FeatureOverrideRepository`, `LocalPolicyRepository`, `GovernanceDocumentRepository`). Both `Postgres*` and `InMemory*` implementations exist.
- **Tenant & Security Context**: Resolves college from caller's tenant security context; `CollegeResolver` eliminates synthetic fallback `COL_ENGG_01`.
- **Observed Test Execution**:
  - `CollegeAdminTenantIsolationRegressionTest`: 10 ran, 10 passed, 0 skipped. Validates cross-tenant boundaries, policy approval workflows, and immutable published states.
  - `FailFastPersistenceBootstrapTest`: 2 ran, 2 passed, 0 skipped. Validates fail-fast exception on missing database configuration.
- **Remaining MVP Gaps**:
  1. Live database verification against Supabase staging with `postgres.<ref>` pooler connection and non-privileged role context.
  2. Integration testing of college-scoped role assignment boundaries.

---

### 3.3 ACD-01 Course Management Service

#### Architectural Classification:
- **Core Persistence**: `PostgresCourseRepository` targeting `acd.courses` with tenant filtering (`tenant_id = ?`) and RLS policies.
- **Database Catalog**: Migration `V20261011_01` verified active on staging database catalog; policies enforce `acd01.view`, `acd01.write`, `acd01.manage`.
- **Observed Test Execution**:
  - `CourseDomainServiceTest`: 20 ran, 20 passed, 0 skipped.
  - `CoursePersistenceAndTenantIsolationRegressionTest`: 10 ran, 10 passed, 0 skipped.
  - `PostgresCourseRestrictedIdentityRlsIntegrationTest`: 12 tests implemented with `SET LOCAL ROLE authenticated` and JWT claim injection; cleanly skipped offline awaiting live test tenant credentials.
- **Remaining MVP Gaps**:
  1. Live execution of `PostgresCourseRestrictedIdentityRlsIntegrationTest` against staging once test tenant `11111111-1111-1111-1111-111111111111` is provisioned.
  2. API contract alignment for course batch offering integration (ACD-04 dependency).
