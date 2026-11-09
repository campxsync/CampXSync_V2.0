# Track B — MVP E2E Readiness Report (MVP-E2E-00)

**Document**: `docs/mvp/04-e2e-readiness.md`  
**Date**: 2026-10-14  
**Branch**: `ReviewBranch`  
**Scope**: End-to-End Architectural Trace and Readiness Verification (Frontend → Gateway → Downstream Microservices → Supabase PostgreSQL Staging)  
**Safety Policy**: Absolute adherence to Rules 1–12 (No production access, no unapproved DB writes, zero credentials logged)

---

## 1. Executive Summary & Verification Labels

This document establishes the verified architectural trace across the CampXSync system boundary, resolving backlog task `MVP-E2E-00`. Every claim is strictly grounded in workspace source code, configuration files, test execution logs, and live read-only introspection records.

### Evidence Labels:
- **[VERIFIED]**: Confirmed directly by inspecting code, running non-modifying commands, or live read-only catalog introspection.
- **[REPORTED]**: Documented in verified execution records or test run outputs.
- **[INFERRED]**: Logically deduced from configuration or architectural boundaries; pending live execution proof.
- **[NOT CHECKED]**: Intentionally unverified in this cycle to adhere to safety rules or execution scope.

### Core Verdict:
- **Genuine End-to-End Status**: **0 journeys are currently operational end-to-end from browser UI to Supabase database.**
- **Root Causes**:
  1. Frontend currently uses a synthetic mock identity context (`DEFAULT_CONTEXT`) with zero Supabase Auth login flow **[VERIFIED]**.
  2. All API methods in `api.js` catch errors and silently return hardcoded mock in-memory fixtures **[VERIFIED]**.
  3. Services boot in in-memory mode by default unless `CAMPX_PERSISTENCE_MODE=postgres` is explicitly provided **[VERIFIED]**.
  4. Staging database contains 0 tenant fixtures in `core.tenants` pending owner approval (`GO PROVISION-TEST-TENANT`) **[VERIFIED]**.

---

## 2. End-to-End Architectural Trace

```
[Browser Client]
       │
       ▼ (Port 3000 - Vite Dev Server / Proxy)
  frontend-web (React + Vite)
       │
       ▼ HTTP (Port 8080 - Proxied via /api, /v1, /actuator)
  api-gateway (GatewayServer + ReverseProxyHandler)
       │
       ├──► JWT Validation via JWKS (Supabase Staging Issuer)
       ├──► Header Sanitization (Strips inbound X-User-Id / X-Tenant-Id)
       └──► Gateway HMAC-SHA256 Signing (Injected X-Gateway-* Headers)
       │
       ├──► Port 8081: ADM-01 Institute Admin Service
       ├──► Port 8082: ADM-02 College Admin Service
       ├──► Port 8083: ACD-01 Course Management Service
       └──► Ports 8084-8092: ACD-02 through ACD-10 Services
       │
       ▼ HikariCP Connection Pool (Session Mode 5432)
  Supabase PostgreSQL Staging (ref: kpwdvvcschankgggitnj)
       │
       ├──► SET LOCAL ROLE authenticated;
       ├──► SELECT set_config('request.jwt.claims', ..., true);
       └──► RLS Kernel Policy Evaluation (core, iam, acd schemas)
```

---

## 3. Detailed Component Evaluation

### 3.1 Frontend Web (`frontend-web`)

#### A. Authentication & Login Flow Status: **NOT IMPLEMENTED [VERIFIED]**
- **File Reference**: [frontend-web/src/services/api.js](file:///d:/CampXSync/CampXSync_V2.0/frontend-web/src/services/api.js#L8-L26)
- **Current Behavior**:
  - Zero login screen, route, or Supabase Auth JS SDK (`@supabase/supabase-js`) integration exists in `frontend-web`.
  - Identity is hardcoded in `DEFAULT_CONTEXT`:
    ```javascript
    export const DEFAULT_CONTEXT = {
      tenantId: '0c914bb2-f63b-472c-a99a-39977112935d',
      tenantName: 'CampXSync Demo Institute',
      tenantCode: 'CAMPXSYNC',
      userId: 'ba77b8f7-ab32-46bd-85d2-5aade3526880',
      userName: 'CampXSync Administrator',
      userEmail: 'campxsync@gmail.com',
      userRole: 'SUPER_ADMIN'
    };
    ```
  - Outgoing HTTP headers in `getHeaders()` inject `X-Tenant-Id`, `X-User-Id`, and `X-User-Role`.
  - **No `Authorization: Bearer <token>` header is sent** by the browser client.

#### B. UI Tab & API Integration Matrix: **[VERIFIED]**

| Tab / View | Route / State | Target Backend API | Error Fallback Behavior | Actual Data Source Today |
|---|---|---|---|---|
| **Overview** | `overview` | `/actuator/health`, `/api/v1/gateway/routes` | Shows OFFLINE / empty routes | Client aggregated state + Mock |
| **Colleges** | `colleges` | `GET/POST /api/v1/admin/colleges` | Lines 89–113: returns 2 mock colleges (`SOET`, `SOAS`) | **Mock fallback [VERIFIED]** |
| **Departments** | `departments` | `GET/POST/PUT /api/v1/college-admin/departments` | Lines 325–352: returns 3 mock depts (`CSE`, `ECE`, `MATH`) | **Mock fallback [VERIFIED]** |
| **Programs** | `programs` | `GET/POST /api/v1/college-admin/programs` | Lines 396–428: returns 3 mock programs (`BTECH_CSE`, etc.) | **Mock fallback [VERIFIED]** |
| **Courses** | `courses` | `GET/POST/PUT /api/v1/courses` | Lines 458–505: returns 4 mock courses (`CS101`, `CS201`, etc.) | **Mock fallback [VERIFIED]** |
| **Calendars** | `calendars` | `GET/POST /api/v1/admin/calendars`, `/events` | Lines 552–560: returns 1 mock calendar (`AY2026_CAL`) | **Mock fallback [VERIFIED]** |
| **Sequences** | `sequences` | `GET/POST /api/v1/admin/number-sequences`, `/next` | Lines 627–632: returns 3 mock sequences (`STUDENT_REG`, etc.) | **Mock fallback [VERIFIED]** |
| **Governance** | `governance` | `GET/PUT /api/v1/college-admin/profile`, `/settings`, `/policies`, `/documents` | Profile: throws error (no mock); Settings/Policies/Docs: return empty array | **Mixed (Unshielded Error / Empty) [VERIFIED]** |
| **Diagnostics** | `diagnostics` | `/actuator/health`, `/api/v1/gateway/routes` | Returns `{ status: 'OFFLINE' }` | **Mock fallback [VERIFIED]** |
| **Role Templates** | *(None)* | `/api/v1/admin/role-templates` | No frontend tab, route, or component exists | **NOT IMPLEMENTED [VERIFIED]** |
| **User Profiles** | *(None)* | `/api/v1/admin/users` | No frontend tab, route, or component exists | **NOT IMPLEMENTED [VERIFIED]** |
| **Provisioning** | *(None)* | Internal domain / repository only | No frontend tab, route, or component exists | **NOT IMPLEMENTED [VERIFIED]** |

---

### 3.2 API Gateway (`api-gateway`)

#### A. Port, Host, and Proxy Routing: **[VERIFIED]**
- **Vite Proxy Config**: [frontend-web/vite.config.js](file:///d:/CampXSync/CampXSync_V2.0/frontend-web/vite.config.js#L7-L24)
  - Vite dev server binds to `127.0.0.1:3000`.
  - Proxies `/api`, `/v1`, `/actuator` to `http://localhost:8080`.
- **Gateway Server**: [api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java#L36-L101)
  - Default listening port: `8080`.
  - Route Dispatch Table:
    - `/api/v1/admin` → `http://localhost:8081/api/v1/admin` (ADM-01)
    - `/api/v1/college-admin` → `http://localhost:8082/api/v1/college-admin` (ADM-02)
    - `/api/v1/courses` → `http://localhost:8083/api/v1/courses` (ACD-01)
    - `/api/v1/curricula` → `http://localhost:8084/api/v1/curricula` (ACD-02)
    - `/api/v1/subjects` → `http://localhost:8085/api/v1/subjects` (ACD-03)
    - `/api/v1/batches` → `http://localhost:8086/api/v1/academics/batches` (ACD-04)
    - `/api/v1/timetables` → `http://localhost:8087/api/v1/academics/timetables` (ACD-05)
    - `/api/v1/attendance` → `http://localhost:8088/api/v1/academics/attendance` (ACD-06)
    - `/api/v1/calendars` → `http://localhost:8089/api/v1/academics/calendars` (ACD-07)
    - `/api/v1/resources` → `http://localhost:8090/api/v1/academics/resources` (ACD-08)
    - `/api/v1/analytics` → `http://localhost:8092/api/v1/analytics` (ACD-10)

#### B. Authentication & Identity Propagation: **[VERIFIED]**
- **File Reference**: [api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java#L153-L165) and [lines 260–289](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java#L260-L289)
- **Security Modes**:
  1. **When `CAMPX_INTERNAL_AUTH_ENABLED=true`**:
     - Client must send `Authorization: Bearer <Supabase-JWT>`.
     - Gateway validates token against JWKS at `https://kpwdvvcschankgggitnj.supabase.co/auth/v1/.well-known/jwks.json`.
     - Inbound client identity headers (`X-User-Id`, `X-Tenant-Id`, `X-User-Role`) are **stripped**.
     - Gateway computes HMAC-SHA256 signature using `CAMPX_INTERNAL_SECRET` over canonical payload `(timestamp, method, path, userId, tenantId, bodySha256)`.
     - Gateway injects verified headers:
       - `X-Gateway-User-Id`
       - `X-Gateway-Tenant-Id`
       - `X-Gateway-User-Role`
       - `X-Gateway-Timestamp`
       - `X-Gateway-Signature`
  2. **When `CAMPX_INTERNAL_AUTH_ENABLED=false` (Default/Dev Mode)**:
     - No JWT validation is enforced at the Gateway.
     - Unchecked client headers (`X-Tenant-Id`, `X-User-Id`, `X-User-Role`) pass straight through.
     - No Gateway HMAC signature is added.

---

### 3.3 Downstream Microservices (ADM-01, ADM-02, ACD-01)

#### A. Internal Gateway HMAC Verifier: **[VERIFIED]**
- **File Reference**: [services/institute-admin-service/.../GatewayHmacVerifier.java](file:///d:/CampXSync/CampXSync_V2.0/services/institute-admin-service/src/main/java/com/campx/admin/institute/security/GatewayHmacVerifier.java#L122-L157)
- **Behavior**:
  - If `CAMPX_INTERNAL_AUTH_ENABLED=false`: trusts incoming `userId`, `tenantId`, `userRole` directly without cryptographic check.
  - If `CAMPX_INTERNAL_AUTH_ENABLED=true`: strictly enforces `X-Gateway-Timestamp` (within 60s replay window) and verifies `X-Gateway-Signature` against `CAMPX_INTERNAL_SECRET`. Rejects unsigned requests with HTTP 401.

#### B. Persistence Mode Switching: **[VERIFIED]**
Each service inspects environment variables and system properties to decide between in-memory mock repositories and real PostgreSQL JDBC repositories:

1. **ADM-01 (Institute Admin Service)**:
   - [InstituteAdminDomainService.java](file:///d:/CampXSync/CampXSync_V2.0/services/institute-admin-service/src/main/java/com/campx/admin/institute/service/InstituteAdminDomainService.java#L221-L236)
   - Checks `campx.persistence.mode` or `CAMPX_PERSISTENCE_MODE` for `"postgres"`.
   - Checks `campx.db.enabled=true` or presence of `campx.db.url`.
   - Defaults to `InMemory*` repositories when unset.
2. **ADM-02 (College Admin Service)**:
   - [CollegeAdminDomainService.java](file:///d:/CampXSync/CampXSync_V2.0/services/college-admin-service/src/main/java/com/campx/admin/college/service/CollegeAdminDomainService.java#L163-L180)
   - Checks `CAMPX_PERSISTENCE_MODE=postgres` or `-Dcampx.persistence.mode=postgres`.
   - In postgres mode, executes fail-fast connection pool initialization: `DatabaseConnectionManager.getInstance().initialize()`.
   - Defaults to `InMemory*` repositories when unset.
3. **ACD-01 (Course Management Service)**:
   - [CourseDomainService.java](file:///d:/CampXSync/CampXSync_V2.0/services/course-management-service/src/main/java/com/campx/academic/course/service/CourseDomainService.java#L57-L82)
   - Checks `CAMPX_PERSISTENCE_MODE=postgres`.
   - In postgres mode, instantiates `PostgresCourseRepository()`.
   - Defaults to `InMemoryCourseRepository` when unset.

#### C. Database Configuration Variable Names (No Values Logged): **[VERIFIED]**
- [DatabaseConfig.java](file:///d:/CampXSync/CampXSync_V2.0/services/institute-admin-service/src/main/java/com/campx/admin/institute/config/DatabaseConfig.java#L70-L89):
  - `CAMPX_DB_URL` / `SUPABASE_JDBC_URL` / `DATABASE_URL`
  - `CAMPX_DB_USERNAME` / `SUPABASE_USER` / `DB_USER`
  - `CAMPX_DB_PASSWORD` / `SUPABASE_PASSWORD` / `DB_PASSWORD`
  - `CAMPX_TEST_ENV` (must equal `"staging"`)
  - `CAMPX_TEST_TENANT_ID` (target tenant UUID)
  - `CAMPX_TEST_USER_ID` (target user UUID)

---

## 4. Browser Testing Evidence Assessment

### 4.1 Existing Evidence: **[VERIFIED / REPORTED]**
- **Recording Artifact**: `campx_ui_flow_1791901486467.webp`
- **Scope**: Local Vite server (`http://127.0.0.1:3000/`) with synthetic `DEFAULT_CONTEXT`.
- **Exercised Journeys**: Clicked tabs for Colleges, Academic Calendars, Number Sequences, Audit & Governance, and Gateway & RLS Security diagnostics.
- **Classification**: **SMOKE PASS ONLY**. Proves pages load and components mount without fatal JavaScript syntax/render crashes.
- **Critical Limitation**: **Does NOT prove end-to-end functionality, real backend connectivity, authentication, RLS, or database persistence.**

### 4.2 Unverified Items: **[VERIFIED]**
1. Form submission and mutation flows (creating a College, Calendar, Sequence, Setting, Policy, Document, or Course).
2. Live HTTP status code and RFC 7807 error rendering in UI (400, 401, 403, 404, 409).
3. Authorization failure presentation when a non-admin or cross-tenant identity accesses a restricted view.
4. Soft delete visibility and status updates in UI tables.
5. Real session lifecycle (login, token refresh, logout).

---

## 5. Comprehensive Blocker Classification

| Blocker ID | Description | Classification | Impact |
|---|---|---|---|
| **BLK-01** | Frontend has no Supabase Auth login screen or JWT token injector | **CODE** | Browser sends synthetic mock headers, cannot authenticate against gateway |
| **BLK-02** | Frontend `api.js` catches all fetch errors and returns hardcoded mock fixtures | **CODE** | UI displays fake data even when gateway/backend is completely down |
| **BLK-03** | UI lacks tabs/components for Role Templates (`ADM01-03/05`), User Profiles (`ADM01-01`), and Provisioning (`ADM01-04`) | **CODE** | These slices cannot be tested in browser without frontend implementation |
| **BLK-04** | Staging database has 0 tenant records in `core.tenants` | **DATA FIXTURE / STAGING WRITE** | Backend JDBC queries fail or return empty; cannot bind tenant context |
| **BLK-05** | Staging has 0 users in `auth.users` and 0 profiles in `iam.user_profiles` | **DATA FIXTURE / AUTH** | No real identities exist to issue valid Supabase JWTs |
| **BLK-06** | 24 monthly partition child tables lack RLS and have overprivileged grants to `authenticated` | **STAGING WRITE** | Database security vulnerability (Finding 1) blocks safe live writes |
| **BLK-07** | Custom Access Token Hook (`iam.custom_access_token_hook`) toggle unconfirmed in Dashboard | **OWNER ACTION / CONFIGURATION** | Issued JWTs will lack `app_metadata.tenant_id` claims required by RLS |
| **BLK-08** | Staging write approvals (`GO PARTITION-RLS-FIX`, `GO PROVISION-TEST-TENANT`) pending | **OWNER ACTION** | Direct SQL writes to staging remain locked by safety rules |

---

## 6. Minimum Path to Slice E2E-02 (Colleges Slice)

To achieve the definition of "Done" for the first end-to-end slice (**Slice E2E-02: Colleges UI to Supabase DB**) without staging write violations:

### Minimum Action Checklist:

1. **Owner Action (Dashboard Hook)**:
   - Confirm in Supabase Dashboard (Authentication → Hooks) that `Custom Access Token (JWT) Hook` is enabled and points to `iam.custom_access_token_hook`.
2. **Owner Action (Staging DB Fixes)**:
   - Approve `GO PARTITION-RLS-FIX` to secure the 24 monthly partition child tables.
   - Approve `GO PROVISION-TEST-TENANT` to create test tenant `CAMPX_STAGING` (`11111111-1111-1111-1111-111111111111`).
3. **Owner Action (Auth Test Users)**:
   - In Supabase Dashboard (Authentication → Users), create two test users:
     - User 1: `staging-admin@campx.edu` (Platform Admin)
     - User 2: `staging-user@campx.edu` (Regular / Tenant User)
   - Insert their UUIDs into `iam.user_profiles` linked to `CAMPX_STAGING` and appropriate role bindings.
4. **Code Action (Frontend Auth & API Integration)**:
   - Introduce a minimal Supabase Auth client or login token selector in `frontend-web` that obtains a real Supabase access token.
   - Update `api.js` to set `Authorization: Bearer <token>` in `getHeaders()`.
   - Remove or bypass the silent catch fallback in `api.getColleges()` and `api.createCollege()` so real backend errors surface.
5. **Configuration Action (Service Boot)**:
   - Start `api-gateway` on port `8080` with `CAMPX_INTERNAL_AUTH_ENABLED=true` and `CAMPX_INTERNAL_SECRET`.
   - Start `institute-admin-service` (ADM-01) on port `8081` with:
     - `CAMPX_PERSISTENCE_MODE=postgres`
     - `SUPABASE_JDBC_URL` (pooler port 5432)
     - `SUPABASE_USER` & `SUPABASE_PASSWORD`
     - `CAMPX_TEST_ENV=staging`
     - `CAMPX_INTERNAL_AUTH_ENABLED=true`
     - `CAMPX_INTERNAL_SECRET`
6. **Execution & Proof**:
   - Perform real browser login as `staging-admin@campx.edu`.
   - Open Colleges tab, verify live colleges loaded from `core.colleges`.
   - Create a test college through the UI modal; verify HTTP 201 response.
   - Execute read-only SELECT via Supabase MCP to confirm the row, outbox event, and audit log.
   - Repeat request with non-admin identity; verify HTTP 403 / RLS rejection in UI and zero database mutation.

---

## 7. Staging Write Proposals & Review (Gated — No Writes Executed)

### 7.1 Target Environment & Production Guard Verification
- **Target Staging Project Reference**: `kpwdvvcschankgggitnj` [VERIFIED via MCP read-only connection and ambient shell].
- **Production Project Reference**: `ekspahbsbwdpraaqfqui` [EXPLICITLY REJECTED & ISOLATED].
- **Rule 11 Assertion**: Verified `ContainsProdRef=False` across all environment variables, `ContainsStagingRef=True`, and `CAMPX_TEST_ENV=staging`.

### 7.2 Proposal 1: `GO PARTITION-RLS-FIX`
- **Target Migration File (to be created)**: `database/migrations/V20261014_01__enable_rls_on_monthly_partition_children.sql`
- **Affected Tables (24 monthly partition children)**:
  - `audit.access_events_p202610`, `_p202611`, `_p202612`, `_p202701`
  - `audit.change_log_p202610`, `_p202611`, `_p202612`, `_p202701`
  - `audit.events_p202610`, `_p202611`, `_p202612`, `_p202701`
  - `cam.delivery_events_p202610`, `_p202611`, `_p202612`, `_p202701`
  - `hrm.attendance_punches_p202610`, `_p202611`, `_p202612`, `_p202701`
  - `lms.progress_events_p202610`, `_p202611`, `_p202612`, `_p202701`
- **Current Grants & Security State**:
  - `relrowsecurity = false` on all 24 children.
  - Direct `SELECT` and `INSERT` granted to `authenticated` in `information_schema.role_table_grants`.
  - 0 policies on child partitions (bypasses parent RLS).
- **Proposed SQL Changes**:
  ```sql
  DO $$
  DECLARE
    t text;
  BEGIN
    FOR t IN
      SELECT format('%I.%I', n.nspname, c.relname)
      FROM pg_inherits inh
      JOIN pg_class c ON c.oid = inh.inhrelid
      JOIN pg_class p ON p.oid = inh.inhparent
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname IN ('audit', 'cam', 'hrm', 'lms')
        AND c.relname LIKE '%_p20%'
    LOOP
      EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', t);
      EXECUTE format('REVOKE ALL ON %s FROM anon, authenticated', t);
    END LOOP;
  END $$;
  ```
- **Rollback SQL (`V20261014_01_rollback__revert_rls_on_monthly_partition_children.sql`)**:
  ```sql
  DO $$
  DECLARE
    t text;
  BEGIN
    FOR t IN
      SELECT format('%I.%I', n.nspname, c.relname)
      FROM pg_inherits inh
      JOIN pg_class c ON c.oid = inh.inhrelid
      JOIN pg_namespace n ON n.oid = c.relnamespace
      WHERE n.nspname IN ('audit', 'cam', 'hrm', 'lms')
        AND c.relname LIKE '%_p20%'
    LOOP
      EXECUTE format('ALTER TABLE %s DISABLE ROW LEVEL SECURITY', t);
      EXECUTE format('GRANT SELECT, INSERT ON %s TO authenticated', t);
    END LOOP;
  END $$;
  ```
- **Tests Establishing Tenant Isolation**:
  - `PostgresAccessEventRepositoryTest`
  - `PostgresAuditChangeLogRepositoryTest`
  - `PostgresSystemAuditLogRepositoryTest`

### 7.3 Proposal 2: `GO PROVISION-TEST-TENANT`
- **Target Staging Project Reference**: `kpwdvvcschankgggitnj`
- **Prerequisites**:
  1. `core.tenants` confirmed 0 rows.
  2. Column constraint `ck_tenants_x1` validated: regex `^[A-Z0-9_-]{2,30}$` requires column `code = 'CAMPX_STAGING'` (replaces legacy non-existent `slug` column).
- **Proposed SQL Operation**:
  ```sql
  INSERT INTO core.tenants (id, code, name, status, created_at, updated_at)
  VALUES (
    '11111111-1111-1111-1111-111111111111'::uuid,
    'CAMPX_STAGING',
    'CampX Staging Tenant',
    'ACTIVE',
    now(),
    now()
  )
  ON CONFLICT (id) DO NOTHING;
  ```
- **Records Created**:
  1. Exactly 1 row in `core.tenants`: `id = '11111111-1111-1111-1111-111111111111'`.
  2. Automatic trigger execution `trg_seed_roles` calling `iam.on_tenant_created()`:
     - 30 system roles seeded into `iam.roles` scoped to `11111111-1111-1111-1111-111111111111`.
     - 2,138 role-permission mappings seeded into `iam.role_permissions`.
- **Verification SQL**:
  ```sql
  SELECT id, code, name, status FROM core.tenants WHERE id = '11111111-1111-1111-1111-111111111111'::uuid;
  SELECT count(*) FROM iam.roles WHERE tenant_id = '11111111-1111-1111-1111-111111111111'::uuid; -- Expect 30
  SELECT count(*) FROM iam.role_permissions WHERE tenant_id = '11111111-1111-1111-1111-111111111111'::uuid; -- Expect 2138
  ```
- **Rollback SQL**:
  ```sql
  DELETE FROM core.tenants WHERE id = '11111111-1111-1111-1111-111111111111'::uuid;
  ```

### 7.4 Custom Access Token Hook Review (`iam.custom_access_token_hook`)
- **Signature & Security**: `iam.custom_access_token_hook(event jsonb) RETURNS jsonb` (`SECURITY DEFINER`).
- **Grants**: EXECUTE granted solely to `supabase_auth_admin`; revoked from `anon`, `authenticated`, `service_role`, and `public` [VERIFIED in P3-15].
- **Expected Claims in Issued JWT**:
  - `claims.sub`: Auth User UUID (matches `auth.users.id`).
  - `claims.app_metadata.tenant_id`: Active Tenant UUID (matches `core.tenants.id`).
  - `claims.app_metadata.user_id`: Application profile UUID (matches `iam.user_profiles.id`).
  - `claims.role`: `"authenticated"`.
- **Dashboard Action**: Confirm toggle in Supabase Dashboard under `Authentication -> Hooks -> Custom Access Token` pointing to `iam.custom_access_token_hook`.

---

## 8. Local MVP Parallel Implementation Plan

### 8.1 Plan: Real Supabase Frontend Authentication (`frontend-web`)
1. **Add Supabase Client Factory**:
   - Create `frontend-web/src/services/supabaseClient.js` configuring `createClient` using `VITE_SUPABASE_URL` and `VITE_SUPABASE_ANON_KEY`.
   - Provide a safe offline mode when keys are unset.
2. **Add Auth Provider & Modal**:
   - Create `src/context/AuthContext.jsx` exposing `session`, `user`, `tenantId`, `loginWithPassword`, `logout`, and `isMockAuth`.
   - When authenticated, store the real session JWT and extract `tenant_id` from `session.user.app_metadata.tenant_id`.
3. **Inject Real JWT into API Calls**:
   - Update `getHeaders()` in `frontend-web/src/services/api.js`:
     ```javascript
     const getHeaders = (context) => {
       const headers = {
         'Content-Type': 'application/json',
         'Accept': 'application/json',
         'X-Trace-Id': 'web-' + Math.random().toString(36).substring(2, 10)
       };
       if (context && context.accessToken) {
         headers['Authorization'] = `Bearer ${context.accessToken}`;
       }
       return headers;
     };
     ```

### 8.2 Plan: Removal of Silent Mock-Data Fallbacks
1. Refactor `api.js` methods (`getColleges`, `getDepartments`, `getPrograms`, `getCourses`, `getCalendars`, `getNumberSequences`):
   - Remove silent `catch (e)` fallback to hardcoded objects.
   - Throw normalized API errors or return `{ error: err.message, status: res.status }`.
2. Add explicit environment gate for demo mode:
   - Fallback to mock data only if `VITE_ENABLE_MOCK_FALLBACK=true` is explicitly enabled in `.env.local`.
   - In standard development and test modes, render genuine backend errors in the UI.

### 8.3 Plan: Verified PostgreSQL Persistence Configuration
1. Standardize local start scripts for running services against staging:
   - Provide `scripts/start-staging-stack.ps1` with Rule 11 assertion.
   - Starts `api-gateway` on port `8080` with `CAMPX_INTERNAL_AUTH_ENABLED=true`.
   - Starts `institute-admin-service` (port `8081`), `college-admin-service` (port `8082`), and `course-management-service` (port `8083`) with:
     - `CAMPX_PERSISTENCE_MODE=postgres`
     - `SUPABASE_JDBC_URL`
     - `SUPABASE_USER` & `SUPABASE_PASSWORD`
     - `CAMPX_TEST_ENV=staging`

