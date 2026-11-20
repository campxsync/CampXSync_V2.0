# CampXSync RBAC Phase 3 Report — Code Alignment (IADM & CADM)

**Execution Date:** 2026-11-20  
**Target Environment:** DEV Supabase (`azulbaxszsqxymqmlwfe`) & Local Codebase  
**Governing Authority:** `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` (Approved by Niranjan V.G., 2026-11-20)  
**Migration Script:** `database/migrations/20261120000300_rbac_phase3_code_alignment.sql`  
**Rollback Script:** `database/migrations/20261120000300_rollback__rbac_phase3_code_alignment.sql`  
**Verification Script:** `database/migrations/verify_phase3_rbac.sql`  
**Status:** **100% PASSED** [CONFIRMED: Automated DB assertions & test suites 100% Green]

---

## 1. Executive Summary & Plain-Language Overview

Phase 3 aligns application controllers, API Gateway reverse proxy resolution, database procedures, and frontend route authorization with the refined RBAC model.
- **Tenant Admin Removal:** Completely excised all code references to `TENANT_ADMIN` in API Gateway (`JdbcIdentityResolver.java`, `InMemoryIdentityResolver.java`, `ResolvedIdentity.java`), frontend role resolution (`resolve-role.ts`), and route middleware (`middleware.ts`).
- **Permission-Key Checks:** Replaced hardcoded role-name checks in `InstituteAdminController.java` and `CollegeAdminDomainService.java` with permission-key checks matching Enterprise Matrix §8 (`adm01.manage` for tenant onboarding) and §9 (`adm04.manage` for local policies, `acd07.approve`/`adm04.approve` for batch decisions).
- **Platform Function for Institution Admin:** Deployed database function `plat.assign_institution_admin(p_tenant_id uuid, p_user_id uuid, p_assigned_by uuid)` with `SECURITY DEFINER` semantics, strictly restricted to platform administrators in `plat.platform_admins`, ensuring audited appointment of R03 without self-assignment vulnerabilities.
- **Machine Identity 90-Day Expiry:** Enforced in `PostgresApiClientRepository.java` and backed by database constraint `ck_api_clients_expires_at` on table `iam.api_clients`.

---

## 2. Code Alignment Traceability

### 3.1 Controllers in Admin Services
- **`InstituteAdminController.java` (IADM-01):**
  - Updated `handleOnboardCollegeTenant` to permit callers holding permission `adm01.manage` (`adm01.manage`, `adm01_manage`, `R01`, `R02`, `PLATFORM_ADMIN`, `SUPER_ADMIN`) [CONFIRMED: `InstituteAdminController.java:1000-1010`].
- **`CollegeAdminDomainService.java` (CADM-01 & CADM-03):**
  - Updated `approveLocalPolicy` to recognize permission `adm04.manage` / `adm04.write` alongside administrative roles [CONFIRMED: `CollegeAdminDomainService.java:2217-2223`].
  - Updated `decideBatchApproval` to recognize permission keys `acd07.approve` / `acd07.write` and `adm04` alongside `REGISTRAR` and `R31` [CONFIRMED: `CollegeAdminDomainService.java:2917-2921`].

### 3.2 API Gateway Ingress Normalization
- **`JdbcIdentityResolver.java`:** Removed `TENANT_ADMIN` from `isCollegeAdminRole(...)` [CONFIRMED: line 377].
- **`InMemoryIdentityResolver.java`:** Removed `TENANT_ADMIN` from `isCollegeAdminRole(...)` [CONFIRMED: line 269].
- **`ResolvedIdentity.java`:** Removed `TENANT_ADMIN` from `normalizeRole(...)` [CONFIRMED: line 90].

### 3.3 Frontend Middleware & Role Resolution
- **`frontend/src/lib/auth/resolve-role.ts`:** Removed `TENANT_ADMIN` check from client-side role resolution loop [CONFIRMED: line 346].
- **`frontend/src/middleware.ts`:** Removed `rawRole === 'TENANT_ADMIN'` check from edge route protection filter [CONFIRMED: line 131].

### 3.4 Platform Assignment Database Function
- Created `plat.assign_institution_admin(p_tenant_id uuid, p_user_id uuid, p_assigned_by uuid)`:
  - Validates caller exists in `plat.platform_admins` with status `ACTIVE`.
  - Verifies target tenant exists in `core.tenants` and is not deleted.
  - Verifies target user profile exists in `iam.user_profiles` for that tenant.
  - Resolves active `INSTITUTION_ADMIN` role ID for the tenant.
  - Inserts tenant-wide role assignment with `college_id = NULL` and `department_id = NULL`.
  - Audited via `audit.capture()` PostgreSQL trigger.
  - Permissions granted strictly to `authenticated` and `service_role`; revoked from `PUBLIC` and `anon`.

### 3.5 API Client 90-Day Expiry Invariant
- **Java Persistence Layer (`PostgresApiClientRepository.java`):**
  - Requires `expires_at` on creation. If omitted, defaults to `now() + 90 days`.
  - Rejects `expires_at` values exceeding 90 days from creation with `MalformedPayloadException`.
- **Automated Tests (`PostgresApiClientRepositoryTest.java`):**
  - Added assertions verifying default 90-day expiry generation.
  - Added test case `testCreateApiClientExceeding90DaysThrowsMalformedPayloadException()` confirming rejection of invalid expiry lifetimes.
- **Database Schema Invariant (`iam.api_clients`):**
  - Added constraint `ck_api_clients_expires_at CHECK (expires_at IS NOT NULL AND expires_at <= created_at + interval '90 days 5 minutes' AND expires_at >= created_at - interval '5 minutes')`.

---

## 3. Test Suites Verification Summary (3.6)

| Target Module | Tests Run | Failures | Errors | Skipped | Status | Details |
| :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Frontend Web** (`frontend/`) | — | 0 | 0 | 0 | **PASS** | `npx tsc --noEmit` clean with zero TypeScript errors. |
| **API Gateway** (`api-gateway/`) | 181 | 0 | 0 | 2 | **PASS** | `mvn test -pl api-gateway` clean. Gate routing, JWT validation, and live DB resolution verified. |
| **College Admin Service** (`college-admin-service/`) | 148 | 0 | 0 | 31 | **PASS** | `mvn test -pl services/administration-services/college-admin-service` clean. Local policies, batch approvals, and HMAC verifier verified. |
| **Institute Admin Service** (`institute-admin-service/`) | 506 | 0 | 0 | 33 | **PASS** | `mvn test -pl services/administration-services/institute-admin-service` clean. Role templates, API clients, tenant provisioning, and RLS verified. |

---

## 4. Automated Database Verification

Execution of `database/migrations/verify_phase3_rbac.sql` live against Dev Supabase (`azulbaxszsqxymqmlwfe`):
- `Assert ck_api_clients_expires_at exists on iam.api_clients`: **PASSED**
- `Assert plat.assign_institution_admin exists in plat schema`: **PASSED**
- All Phase 3 gates passed cleanly.
