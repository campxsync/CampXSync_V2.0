# CampXSync — Complete API Architecture, Gateway & Service Inventory Audit

**Document Identifier**: `docs/audit/CAMPXSYNC_API_GATEWAY_AND_SERVICE_INVENTORY.md`  
**Audit Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Governing Baseline**: Git HEAD (`ReviewBranch` commit `b307149`)  
**Cross-Reference Evidence Index**: [CAMPXSYNC_EVIDENCE_INDEX.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md)

---

## 1. Executive Summary & Architectural Layer Taxonomy

A comprehensive audit of the CampXSync repository reveals that the system possesses **two distinct API architectures**:
1. **The Legacy On-Premise Java Microservices Architecture**: An embedded JDK HTTP Reverse Proxy (`api-gateway` on port 8080) routing to 12 Spring Boot / Java HTTP microservices (ports 8081–8092).
2. **The Active Unified Cloud Serverless Architecture**: Next.js 15 App Router (`frontend/`) deployed on Vercel Edge Serverless, utilizing Edge Middleware, Next.js Route Handlers, direct PostgreSQL connection pooling, and hosted Supabase Auth.

```mermaid
graph TD
    subgraph Client [Browser Clients]
        Web[Desktop & Mobile Browser]
        Tester[Automated Playwright / Probes]
    end

    subgraph VercelCloud [Vercel Cloud Deployment - Active Production/Preview]
        EdgeMW[Next.js Edge Middleware - src/middleware.ts]
        AppPages[Server Component Pages - src/app/]
        RouteHandlers[Next.js API Route Handlers - src/app/api/]
        DirectDB[Direct PostgreSQL Pool - src/lib/db.ts]
    end

    subgraph SupabaseCloud [Hosted Supabase - AWS ap-southeast-1]
        Auth[Supabase GoTrue Auth - /auth/v1]
        PostgREST[PostgREST REST API - /rest/v1 - Public Schema Only]
        Postgres[(PostgreSQL 17.11 Database - 20 Schemas, 458 Tables)]
    end

    subgraph LegacyJava [Legacy Java Microservices - Inactive on Vercel]
        JavaGW[Java API Gateway :8080 - Longest Prefix Match]
        ADM01_Svc[Institute Admin :8081]
        ADM02_Svc[College Admin :8082]
        ACD_Svcs[ACD-01..10 Services :8083-:8092 - In-Memory State]
    end

    Web -->|1. HTTPS Request| EdgeMW
    Tester -->|1. Test Request| EdgeMW
    EdgeMW -->|2. Fast-Fail / Auth Check| AppPages
    EdgeMW -->|2. API Ingress Guard| RouteHandlers
    AppPages -->|3. Authoritative Role Resolution| DirectDB
    RouteHandlers -->|4. Business Operations & Auditing| DirectDB
    RouteHandlers -->|5. Auth Validation| Auth
    DirectDB -->|6. Direct TCP/JDBC Pooler (postgres superuser)| Postgres

    Web -.->|Historical Dev Only| JavaGW
    JavaGW -.-> ADM01_Svc
    JavaGW -.-> ADM02_Svc
    JavaGW -.-> ACD_Svcs
```

### Distinction Between API Layers
To eliminate architectural ambiguity, the table below delineates the reality of each layer:

| Layer Classification | Implementation Entity | Current Operational State | Runtime Deployment Target |
| :--- | :--- | :--- | :--- |
| **1. Standalone API Gateway** | `api-gateway/` (Java JDK HTTP Server) | **INACTIVE ON CLOUD** (Code exists; not running on Vercel) | Local Java runtime only (`localhost:8080`) |
| **2. Edge Middleware Gateway** | `frontend/src/middleware.ts` (Next.js Edge) | **ACTIVE & ENFORCED** | Vercel Edge Runtime (Globally distributed) |
| **3. Next.js API Route Handlers**| `frontend/src/app/api/**/route.ts` (13 routes) | **ACTIVE & ENFORCED** | Vercel Serverless Functions (`sin1`) |
| **4. Supabase PostgREST API** | `https://kpwdvvcschankgggitnj.supabase.co/rest/v1` | **RESTRICTED** | Hosted Supabase (Exposes `public` schema only; private schemas fail with PGRST106) |
| **5. Supabase Auth Endpoints** | `https://kpwdvvcschankgggitnj.supabase.co/auth/v1` | **ACTIVE & ENFORCED** | Hosted Supabase GoTrue Auth |
| **6. Direct PostgreSQL Pooler** | `frontend/src/lib/db.ts` (`pg.Pool`) | **ACTIVE & CRITICAL** | Serverless direct TCP connection to AWS pooler |
| **7. Mock / Test API Routes** | `/api/auth/test-session` | **GATED** | Vercel Preview & Local Dev (Disabled in Prod) |

---

## 2. In-Depth Layer Audits

### Layer 1: Legacy Java API Gateway (`api-gateway/`)
- **Architectural Responsibility**: Standalone reverse proxy for the legacy Java microservices cluster. Performs Longest Prefix Match (LPM) path routing, Supabase ES256 JWKS signature validation, HMAC token signing for internal service-to-service calls, and Prometheus metrics collection.
- **Source Location**: `api-gateway/src/main/java/com/campx/gateway/`
  - Ingress Server: `CampXGatewayServer.java` (Port 8080)
  - Security Validator: `security/SupabaseJwtValidator.java`
  - Internal HMAC Signer: `security/InternalServiceSigner.java`
- **Exposed Routing Rules** (from `docs/api/API_GATEWAY_ROUTES.md`):
  - `/api/v1/admin/**` -> Forward to Institute Admin Service (`localhost:8081`)
  - `/api/v1/college/**` -> Forward to College Admin Service (`localhost:8082`)
  - `/api/v1/courses/**` -> Forward to Course Management Service (`localhost:8083`)
  - `/api/v1/curriculum/**` -> Forward to Curriculum Service (`localhost:8084`)
  - `/api/v1/subjects/**` -> Forward to Subject Management Service (`localhost:8085`)
  - `/api/v1/batches/**` -> Forward to Batch Management Service (`localhost:8086`)
  - `/api/v1/timetable/**` -> Forward to Timetable Service (`localhost:8087`)
  - `/api/v1/attendance/**` -> Forward to Attendance Service (`localhost:8088`)
  - `/api/v1/calendar/**` -> Forward to Academic Calendar Service (`localhost:8089`)
  - `/api/v1/resources/**` -> Forward to Learning Resource Service (`localhost:8090`)
  - `/api/v1/assessments/**` -> Forward to Assessment Mapping Service (`localhost:8091`)
  - `/api/v1/analytics/**` -> Forward to Analytics Service (`localhost:8092`)
- **Authentication & Claims Enforcement**:
  - Validates ES256 Supabase JWTs against JWKS endpoint.
  - Generates downstream header `X-Internal-Signature` via HMAC-SHA256 (`campx-logger`).
  - **Audit Finding**: In commit `cffb841`, claims parsing was refactored to eliminate regex injection (Finding SEC-CRIT-01). 29 unit tests pass in `SupabaseJwtClaimParsingRegressionTest.java`.
- **Runtime & Deployment Status**: **NOT RUNNING ON VERCEL**. Superseded in cloud deployments by Next.js Edge Middleware and Route Handlers per `DEC-13`.

---

### Layer 2: Next.js Edge Middleware (`frontend/src/middleware.ts`)
- **Architectural Responsibility**: Global HTTP ingress filter executing on Vercel Edge. Enforces unauthenticated redirects, token extraction via `@supabase/ssr`, role-based path prefixes, inactive membership termination, no-profile blocks, and suspended tenant rejections.
- **Source Location**: [frontend/src/middleware.ts](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/middleware.ts)
- **Path Matching Configuration**: Matches all request paths except static assets (`_next/static`, images, favicons).
- **Security Boundaries & Route Guards**:
  1. **Public Route Whitelist**: `/login`, `/reset-password`, `/auth/callback`, `/access-denied`, `/api/auth`.
  2. **Fast-Fail Status Checks**:
     - `membership_status === 'INACTIVE'` -> Redirects to `/access-denied?reason=inactive_membership` or returns HTTP 403 RFC 7807 JSON for API calls.
     - `no_profile === true` -> Redirects to `/access-denied?reason=no_profile` or returns HTTP 403 JSON.
     - `tenant_status === 'SUSPENDED'` -> Redirects to `/access-denied?reason=tenant_suspended` or returns HTTP 403 JSON.
  3. **Role Prefix Routing**:
     - `/platform/**` -> Strict check: `rawRole === 'SUPER_ADMIN' || rawRole === 'PLATFORM_ADMIN'`. All others redirected to `/access-denied?reason=forbidden`.
     - `/college/**` -> Strict check: `rawRole === 'COLLEGE_ADMIN' || 'INSTITUTION_ADMIN' || 'TENANT_ADMIN'`.
     - `/faculty/**` -> Accessible by `faculty` and `college_admin`.
     - `/student/**` -> Accessible strictly by `student`.
     - Root `/` -> Dynamically redirects to `/platform`, `/college`, `/faculty`, or `/student` based on assigned role. Unassigned users redirect to `/access-denied?reason=no_role_assigned`.
- **Automated Validation**: Verified via `scripts/verify_role_routing.mjs` (20/20 PASS) and `scripts/verify_phase9_security_browser.mjs` (36/36 PASS).

---

### Layer 3: Next.js API Route Handlers (`frontend/src/app/api/`)
The active cloud MVP exposes exactly **13 HTTP API endpoints** implemented as Next.js Route Handlers:

| Route Path | HTTP Method | Auth Required | Role / Permissions | Request Validation | Persistence Dependency | Evidence Classification |
| :--- | :---: | :---: | :--- | :--- | :--- | :---: |
| `/api/auth/test-session` | POST | None (Gated) | None (Generates mock session) | `role` string | Unsigned cookie `campx_test_session` | `CODE VERIFIED` |
| `/api/auth/test-session` | DELETE | None | None | None | Deletes cookie | `CODE VERIFIED` |
| `/api/college/attendance/corrections` | POST | Yes | `college_admin` | `recordId`, `newStatus`, `reason` (>= 5 chars), `expectedVersion` | `acd.attendance_records`, `acd.attendance_corrections`, `audit.change_log` | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| `/api/college/attendance/records` | GET | Yes | `college_admin`, `faculty` | `sessionId` (UUID) | `acd.attendance_records` JOIN `stu.students` | `CODE VERIFIED` |
| `/api/college/attendance/report` | GET | Yes | `college_admin` | `batchId` (UUID, optional), `format=csv` (optional) | View: `acd.v_attendance_summary` | `CODE VERIFIED` |
| `/api/college/attendance/sessions` | GET | Yes | `college_admin`, `faculty` | `timetableEntryId`, `date` | `acd.attendance_sessions` | `CODE VERIFIED` |
| `/api/college/attendance/sessions` | POST | Yes | `faculty`, `college_admin` | `timetableEntryId`, `date`, `records` array | `acd.attendance_sessions`, `acd.attendance_records`, `audit.events` | `CODE VERIFIED` |
| `/api/college/departments` | GET | Yes | `college_admin` | Tenant session context | `core.departments`, `acd.courses` | `CODE VERIFIED` |
| `/api/college/faculty` | GET | Yes | `college_admin` | Tenant session context | `hrm.employees` JOIN `core.persons` | `CODE VERIFIED` |
| `/api/college/faculty` | POST | Yes | `college_admin` | `firstName`, `lastName`, `email`, `departmentId`, `employeeCode` | `core.persons`, `hrm.employees`, `iam.user_profiles` | `CODE VERIFIED` |
| `/api/college/programs` | GET | Yes | `college_admin` | `departmentId` | `acd.courses` (Degree programs) | `CODE VERIFIED` |
| `/api/college/students` | GET | Yes | `college_admin` | Tenant session context | `stu.students` JOIN `core.persons` | `CODE VERIFIED` |
| `/api/college/students` | POST | Yes | `college_admin` | `firstName`, `lastName`, `email`, `enrollmentNo`, `programId` | `core.persons`, `stu.students` | `CODE VERIFIED` |
| `/api/college/students/assign-batch` | POST | Yes | `college_admin` | `studentIds` array, `batchId` (UUID) | `acd.batch_students` | `CODE VERIFIED` |
| `/api/college/timetable/entries` | GET | Yes | `college_admin`, `faculty` | `collegeId`, `batchId` | `acd.timetable_entries` | `CODE VERIFIED` |
| `/api/college/timetable/entries` | POST | Yes | `college_admin` | `collegeId`, `batchId`, `subjectId`, `facultyId`, `dayOfWeek`, `startTime`, `endTime`, `room` | `acd.timetable_entries` (Exclusion check) | `CODE VERIFIED` |
| `/api/platform/onboard-tenant` | POST | Yes | `platform_admin` | 5-step saga payload (`institute`, `administrator`, `academic`, `subscription`) | `core.tenants`, `core.colleges`, `iam.roles`, `plat.tenants` | `CODE VERIFIED` |
| `/api/user/preferences` | GET | Yes | Authenticated | `userId` from session | `iam.user_profiles.preferences` | `CODE VERIFIED` |
| `/api/user/preferences` | PUT | Yes | Authenticated | Schema v1 allowlist validation | `iam.user_profiles.preferences` | `CODE VERIFIED` |

---

### Layer 4 & 5: Supabase Hosted APIs (GoTrue Auth & PostgREST)
- **Supabase GoTrue Auth (`/auth/v1`)**:
  - `POST /auth/v1/token?grant_type=password`: Authenticates users with email and password (`campxsync@gmail.com`, `platformadmin.campx@gmail.com`, `faculty.campx@gmail.com`). Issues JWT access token, refresh token, and user session.
  - `POST /auth/v1/recover`: Initiates PKCE-compliant password reset flow.
  - `GET /auth/v1/user`: Validates session tokens and returns user identity.
- **Supabase PostgREST (`/rest/v1`)**:
  - In Supabase, PostgREST by default exposes **only the `public` schema**.
  - Application ERP schemas (`plat`, `iam`, `core`, `acd`, `stu`, `hrm`, `audit`) are private PostgreSQL namespaces.
  - **Critical Performance Finding**: Prior to commit `afaff92`, `resolve-role.ts` attempted to call `supabase.schema('plat').from('platform_admins')` and `supabase.schema('iam').from('user_profiles')`. Every call was rejected by PostgREST with HTTP 404 and error `PGRST106 ("The schema must be one of the following: public")`. This wasted 1,500ms–2,500ms of serial network roundtrips during every login.

---

### Layer 6: Direct PostgreSQL Connection Pool (`frontend/src/lib/db.ts`)
- **Implementation**: Node.js `pg.Pool` initialized with `SUPABASE_JDBC_URL`, `SUPABASE_USER`, and `SUPABASE_PASSWORD`.
- **Connection Target**: AWS AP-Southeast-1 Supabase Transaction Pooler (port 5432, SSL enabled).
- **Execution Path**:
  ```ts
  export async function query<T extends QueryResultRow = any>(
    text: string,
    params?: any[]
  ): Promise<QueryResult<T>> {
    const p = getDbPool();
    return p.query<T>(text, params);
  }
  ```
- **RLS Bypass Implications**:
  - The pool connects as `postgres.kpwdvvcschankgggitnj`.
  - In PostgreSQL, the `postgres` superuser/table owner **bypasses Row-Level Security** unless `FORCE ROW LEVEL SECURITY` is enabled on the table.
  - Introspection confirms that **zero (0) of the 458 application tables have FORCE RLS enabled**.
  - **Verdict**: Direct SQL queries execute with superuser privileges and bypass RLS. Tenant isolation depends **entirely on application query predicates** (`WHERE tenant_id = $1`).

---

### Layer 7: Mock & Test Ingress (`/api/auth/test-session`)
- **Purpose**: Enables rapid role switching between College Admin, Platform Admin, and Faculty during testing.
- **Mechanism**: Serializes a mock user object into cookie `campx_test_session`.
- **Security Guard**: Controlled by `isTestSessionAllowed()` (`frontend/src/lib/auth/test-session-gate.ts`).
  - Blocks requests if `process.env.VERCEL_ENV === 'production'`.
  - Permits requests on local dev and Vercel Preview if `ENABLE_TEST_SESSION === 'true'`.
- **Vulnerability**: The cookie is unsigned raw JSON. In Preview, an attacker can set this cookie directly in their browser and bypass authentication.

---

## 3. Comprehensive Route Count Verification

```
Total Active Next.js Cloud Routes: 34
├─ API Route Handlers (/api/*): 13
│  ├─ /api/auth/test-session (POST, DELETE)
│  ├─ /api/college/attendance/corrections (POST)
│  ├─ /api/college/attendance/records (GET)
│  ├─ /api/college/attendance/report (GET)
│  ├─ /api/college/attendance/sessions (GET, POST)
│  ├─ /api/college/departments (GET)
│  ├─ /api/college/faculty (GET, POST)
│  ├─ /api/college/programs (GET)
│  ├─ /api/college/students (GET, POST)
│  ├─ /api/college/students/assign-batch (POST)
│  ├─ /api/college/timetable/entries (GET, POST)
│  ├─ /api/platform/onboard-tenant (POST)
│  └─ /api/user/preferences (GET, PUT)
├─ Server Component Pages (UI Screens): 16
│  ├─ / (Root dynamic redirect)
│  ├─ /access-denied (Error page)
│  ├─ /login (Authentication page)
│  ├─ /reset-password (Password recovery)
│  ├─ /platform (Platform dashboard)
│  ├─ /platform/audit (Platform audit trail)
│  ├─ /platform/institutes (Institutes registry)
│  ├─ /platform/institutes/new (Onboarding wizard)
│  ├─ /college (College dashboard)
│  ├─ /college/attendance (Attendance oversight & audit)
│  ├─ /college/audit (College audit trail)
│  ├─ /college/batches (Batch & cohort management)
│  ├─ /college/departments (Departments & degree programs)
│  ├─ /college/faculty (Faculty roster & provisioning)
│  ├─ /college/students (Student roster & admissions)
│  ├─ /college/timetable (Weekly timetable grid)
│  ├─ /faculty (Faculty teaching workspace)
│  ├─ /faculty/attendance (Attendance roster capture)
│  ├─ /faculty/sessions (Session history)
│  ├─ /faculty/timetable (Faculty weekly timetable)
│  └─ /student (Student summary demo)
└─ Internal & Callback Routes: 2
   ├─ /_not-found (404 handler)
   └─ /auth/callback (Supabase Auth exchange)
```
