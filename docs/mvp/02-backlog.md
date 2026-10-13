# Track B — MVP Execution Backlog

**Document**: `docs/mvp/02-backlog.md`  
**Date**: 2026-10-13  
**Branch**: `ReviewBranch`  
**Binding Roadmap**: `docs/MASTER_EXECUTION_PROMPT.md` (Phase 4 & Phase 5)  
**Priority Hierarchy**: `ADM-01` → `ADM-02` → `ACD-01`

---

## 1. Backlog Governance & Classification Rules

In accordance with Phase 4:
- Every task includes: ID, module, current state, exact files, remaining work, dependencies, staging requirement, acceptance criteria, required tests, priority, effort, and risks.
- Classification Categories:
  - `CAN PROCEED NOW (LOCAL)`: Independent of database writes; can be implemented and tested immediately.
  - `PROCEED WITH LOCAL INFRASTRUCTURE`: Requires unit or mock test infrastructure.
  - `BLOCKED BY STAGING (LIVE GATED)`: Requires approved staging database write (`GO <STEP>`) or live test tenant fixtures.
  - `RELEASE-CRITICAL`: Essential for MVP production signoff.

---

## 2. Actionable MVP Tasks

### Module 1: ADM-01 (Institute Admin Service — Platform Tier)

#### Task `MVP-ADM01-01`
- **Module**: `ADM-01`
- **Title**: ADM-01 Step 1 Live Staging Verification
- **Current State**: Implemented locally (`InstituteAdminController`, `UserProfileRepository`). 47 unit/HTTP tests passing.
- **Exact Files**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/UserProfileRepository.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/UserProfileRepositoryLiveIntegrationTest.java`
- **Remaining Work**: Run live integration test suite against staging pooler once test tenant is provisioned.
- **Dependencies**: Track A `GO PROVISION-TEST-TENANT` (`CRIT-14`).
- **Staging Needed?**: YES (Live DB write & test tenant required).
- **Classification**: `BLOCKED BY STAGING (LIVE GATED)` / `RELEASE-CRITICAL`.
- **Acceptance Criteria**: Live CRUD against `iam.user_profiles` succeeds with unprivileged `authenticated` role and tenant context.

#### Task `MVP-ADM01-02`
- **Module**: `ADM-01`
- **Title**: Step 2: Role Template Authorization Enforcement & Validation Suite
- **Current State**: `RoleTemplateRepository`, `PostgresRoleTemplateRepository`, and `InMemoryRoleTemplateRepository` implemented. Basic test exists (`InMemoryRoleTemplateRepositoryTest`). Missing strict `isPlatformAdmin` authorization enforcement on mutations and comprehensive validation test suite.
- **Exact Files**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/InMemoryRoleTemplateRepository.java`
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresRoleTemplateRepository.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/RoleTemplateRepositoryValidationTest.java` (New)
- **Remaining Work**: Enforce `SecurityViolationException` for non-platform admin mutations; implement comprehensive validation tests covering payload validation, unique constraints, and soft deletion.
- **Dependencies**: None.
- **Staging Needed?**: NO (Pure application logic and unit tests).
- **Classification**: `CAN PROCEED NOW (LOCAL)` / `RELEASE-CRITICAL`.
- **Acceptance Criteria**: All mutation methods reject non-platform administrators with `SecurityViolationException`; validation tests pass 100%.

#### Task `MVP-ADM01-03`
- **Module**: `ADM-01`
- **Title**: Step 3: Tenant Provisioning State Machine Transition Safety
- **Current State**: `PostgresTenantProvisioningRepository` and `InMemoryTenantProvisioningRepository` exist.
- **Exact Files**:
  - `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/TenantProvisioningRepository.java`
  - `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/TenantProvisioningValidationTest.java`
- **Remaining Work**: Add formal state machine transition validation (REQUESTED → IN_PROGRESS → COMPLETED / FAILED) rejecting illegal backwards transitions.
- **Dependencies**: None.
- **Staging Needed?**: NO (Local validation test).
- **Classification**: `CAN PROCEED NOW (LOCAL)`.
- **Acceptance Criteria**: Illegal status transitions throw `InvalidStatusTransitionException`.

---

### Module 2: ADM-02 (College Admin Service — Tenant Tier)

#### Task `MVP-ADM02-01`
- **Module**: `ADM-02`
- **Title**: ADM-02 Live College Admin Staging Verification
- **Current State**: Implemented with multi-tenant isolation, fail-fast persistence, and zero phantom-college fallbacks. 12/12 unit tests passing.
- **Exact Files**:
  - `services/college-admin-service/src/test/java/com/campx/admin/college/CollegeResolverLiveRegressionTest.java`
  - `services/college-admin-service/src/test/java/com/campx/admin/college/JsonbSerializationSecurityTest.java`
- **Remaining Work**: Execute live regression tests against Supabase staging pooler.
- **Dependencies**: Track A `GO PROVISION-TEST-TENANT` (`CRIT-14`).
- **Staging Needed?**: YES (Requires live test tenant in `core.tenants`).
- **Classification**: `BLOCKED BY STAGING (LIVE GATED)` / `RELEASE-CRITICAL`.
- **Acceptance Criteria**: College resolution and JSONB security tests pass cleanly against live staging database.

#### Task `MVP-ADM02-02`
- **Module**: `ADM-02`
- **Title**: Governance Document Immutability & Workflow Error Handling
- **Current State**: Implemented in domain service; unit tests verify publication flow.
- **Exact Files**:
  - `services/college-admin-service/src/main/java/com/campx/admin/college/service/CollegeAdminDomainService.java`
  - `services/college-admin-service/src/test/java/com/campx/admin/college/CollegeAdminTenantIsolationRegressionTest.java`
- **Remaining Work**: Add edge-case test coverage for tampered hash chains and duplicate approval workflow triggers.
- **Dependencies**: None.
- **Staging Needed?**: NO (Local test infrastructure).
- **Classification**: `CAN PROCEED NOW (LOCAL)`.
- **Acceptance Criteria**: Hash-chain tampering is detected and throws security exception.

---

### Module 3: ACD-01 (Course Management Service)

#### Task `MVP-ACD01-01`
- **Module**: `ACD-01`
- **Title**: Live RLS Policy Enforcement Verification (`acd.courses`)
- **Current State**: Policies active on staging (`V20261011_01`); `PostgresCourseRestrictedIdentityRlsIntegrationTest` implemented (12 tests) with `SET LOCAL ROLE authenticated`.
- **Exact Files**:
  - `services/course-management-service/src/test/java/com/campx/academic/course/repository/PostgresCourseRestrictedIdentityRlsIntegrationTest.java`
- **Remaining Work**: Run test suite against staging database with ambient credentials.
- **Dependencies**: Track A `GO PROVISION-TEST-TENANT` (`CRIT-14`).
- **Staging Needed?**: YES (Live test tenant and courses fixture).
- **Classification**: `BLOCKED BY STAGING (LIVE GATED)` / `RELEASE-CRITICAL`.
- **Acceptance Criteria**: 12/12 live RLS tests pass, verifying unauthenticated denial, tenant-A vs tenant-B isolation, and fine-grained permission enforcement (`acd01.view`, `acd01.write`, `acd01.manage`).

#### Task `MVP-ACD01-02`
- **Module**: `ACD-01`
- **Title**: Course Code Pattern & Unique Constraint Validation Suite
- **Current State**: `CourseDomainServiceTest` and `CoursePersistenceAndTenantIsolationRegressionTest` pass 30/30 unit tests.
- **Exact Files**:
  - `services/course-management-service/src/main/java/com/campx/academic/course/service/CourseDomainService.java`
  - `services/course-management-service/src/test/java/com/campx/academic/course/CourseDomainServiceTest.java`
- **Remaining Work**: Harden course offering status transition validation (UPCOMING → ACTIVE → COMPLETED).
- **Dependencies**: None.
- **Staging Needed?**: NO (Local test infrastructure).
- **Classification**: `CAN PROCEED NOW (LOCAL)`.
- **Acceptance Criteria**: Course offering lifecycle adheres to strict domain validation rules.

---

## 3. Immediate Execution Target

Following the strict MVP priority order (`ADM-01` → `ADM-02` → `ACD-01`), the highest-priority independent task that can proceed immediately without staging database writes is:

**Task `MVP-ADM01-02`: Step 2 Platform Role Template Authorization Enforcement & Validation Suite**

- **Target Files**:
  1. `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/InMemoryRoleTemplateRepository.java`
  2. `services/institute-admin-service/src/main/java/com/campx/admin/institute/repository/PostgresRoleTemplateRepository.java`
  3. `services/institute-admin-service/src/test/java/com/campx/admin/institute/repository/RoleTemplateRepositoryValidationTest.java` (New)
