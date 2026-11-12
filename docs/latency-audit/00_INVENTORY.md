# CampXSync Latency Audit — Phase 0: System Architecture & Inventory

**Audit Date:** 2026-11-11  
**Audit Target:** Full Stack CampXSync (Next.js 15 Web Frontend, API Gateway, Java Microservices, Supabase PostgreSQL)  
**Target Git Branch:** `feature/java-backend-integration`  
**Commit SHA:** `ef6e7dbed9dfde7cd492c5b7768542ce958d3a62`  
**Database Target:** Hosted Dev Supabase Project `azulbaxszsqxymqmlwfe` (AWS `ap-south-1` Mumbai, `aws-0-ap-south-1.pooler.supabase.com:6543`)  
**Execution Environment:** Fully local application stack with remote Supabase database  

---

## 1. Machine & Environmental Baseline

| Metric / Parameter | Value / Specification | Notes / Evidence |
| :--- | :--- | :--- |
| **Operating System** | Windows 11 Pro 64-bit (Windows_NT) | PowerShell Host |
| **CPU Architecture** | 12th Gen Intel(R) Core(TM) i5-1235U | 10 Cores (2 P-Cores, 8 E-Cores), 12 Threads, Base 1.30 GHz, Max Boost 4.40 GHz |
| **Host Total RAM** | 15.73 GB Total Physical RAM | ~3.00 GB Available at audit baseline |
| **Java / JDK Version** | Java SE 1.8.0_202-b08 (64-Bit Server VM) | Mixed mode, JIT compiler active, Java 8 bytecode compatibility (`pom.xml`) |
| **Node.js Runtime** | Node.js v22.14.0 | Next.js 15.2.1 runtime |
| **Local Network** | Wi-Fi IPv4 `192.168.1.10`, Gateway `192.168.1.1` | Client physical location: Bengaluru, Karnataka, India |
| **Remote Target Region** | Mumbai (`ap-south-1`) | Supabase Dev ref `azulbaxszsqxymqmlwfe` (pooler at `aws-0-ap-south-1.pooler.supabase.com:6543`) |
| **Network Hop Distance** | Bengaluru, India ↔ Mumbai, India | Domestic fiber round-trip time (RTT): 25.06–29.14 ms (median 26.5 ms) |

---

## 2. Service Hosting & Deployment Topology

### 2.1 Java API Gateway & Java Microservices
- **Cloud Hosting Status:** **NOT HOSTED** (0 cloud containers deployed on AWS, GCP, Render, Fly.io, Railway, or Heroku).
- **Runtime Archetype:** Embedded JDK `com.sun.net.httpserver.HttpServer` bound to loopback `127.0.0.1` or `0.0.0.0`.
- **Framework Overhead:** Custom lightweight HTTP controllers; **no Spring Boot** (pure JDK HTTP + HikariCP + Jackson).
- **Concurrency & Dispatch:** `server.setExecutor(null)` is configured across the Gateway and all domain microservices. In the Sun/JDK HTTP server implementation, passing `null` delegates handling to a single default dispatcher thread, effectively serializing concurrent requests on connections and creating head-of-line blocking under load.
- **Port Allocations:**
  - `8080`: API Gateway (`api-gateway`)
  - `8081`: ADM-01 Institute Admin Service
  - `8082`: ADM-02 College Admin Service
  - `8083`: ACD-01 Course Management Service
  - `8084`: ACD-02 Curriculum Management Service
  - `8085`: ACD-03 Subject Management Service
  - `8086`: ACD-04 Batch Management Service
  - `8087`: ACD-05 Timetable Management Service
  - `8088`: ACD-06 Attendance Management Service
  - `8089`: ACD-07 Academic Calendar Service
  - `8090`: ACD-08 Learning Resource Service
  - `8091`: ACD-09 Assessment Mapping Service
  - `8092`: ACD-10 Reporting & Analytics Service
  - `8093`: STU-01 Student Service
  - `8094`: STM-02 Admissions & Applications Service
  - `8095`: STM-03 Enrollment & Registration Service
  - `8096`: STM-04 Student Lifecycle Service
  - `8097`: STM-05 Guardian & Relationship Service
  - `8098`: STM-06 Student Documents Service

### 2.2 Next.js Web Frontend & BFF
- **Hosting & Framework:** Next.js 15.2.1 App Router with React 19.0.0.
- **Vercel Region Pinning:** Explicitly pinned to `sin1` (Singapore) in `vercel.json` and `frontend/vercel.json`.
- **Rendering Modes:**
  - Route segments explicitly enforce `export const dynamic = 'force-dynamic';` across layouts and dashboards (`(dashboard)/layout.tsx`, `college/page.tsx`, `student/page.tsx`, `faculty/page.tsx`, `platform/page.tsx`, `login/page.tsx`).
  - **Zero SSG/ISR caching**; every page navigation triggers on-demand dynamic Server-Side Rendering (SSR).
- **Middleware Overhead (`frontend/src/middleware.ts`):**
  - Intercepts all non-static requests via broad regex matcher.
  - Invokes `supabase.auth.getUser()` on every request (unless the optional test session cookie `campx_test_session` is present).
  - In `@supabase/ssr`, `getUser()` makes an external HTTPS network request to Supabase Auth (`https://azulbaxszsqxymqmlwfe.supabase.co/auth/v1/user`), adding an international network hop before any page or API route code begins execution.

### 2.3 Database & Connection Pool Configurations
- **Next.js Direct Database Connection (`frontend/src/lib/db.ts`):**
  - Driver: `pg.Pool` (node-postgres 8.23.1).
  - Pool Size: `max: 3` connections.
  - Timeouts: `connectionTimeoutMillis: 10000` (10s), `idleTimeoutMillis: 30000` (30s).
  - Critical Implementation Anomaly: Lines 29–31 and 37–39 in `frontend/src/lib/db.ts` programmatically replace `ap-southeast-1` with `ap-south-1` in connection strings, risking connection failures or routing anomalies if connecting directly to Supabase Singapore hosts.
- **Java Microservices Database Connection Pool (`HikariCP` 4.0.3):**
  - Driver: `org.postgresql.Driver` 42.7.3.
  - Pool Size: `maximumPoolSize = 10`, `minimumIdle = 2`.
  - Timeouts: `connectionTimeoutMs = 30000` (30s), `idleTimeoutMs = 600000` (10m), `maxLifetimeMs = 1800000` (30m).
  - Pooler Compatibility: Configured with `dataSourceProperty("prepareThreshold", "0")` to prevent PostgreSQL prepared statement errors when transacting through Supavisor transaction poolers (port 6543).
  - Context Setup: Per-request connection acquisition applies RLS context via `SET LOCAL ROLE authenticated` and `SET LOCAL "request.jwt.claims" = '...'` before query execution, cleaned up with `RESET ROLE` in `finally` blocks.

---

## 3. End-to-End Request Path Architecture

```
[Browser Client (Bengaluru, India)]
        │
        ▼ (HTTPS / Fiber RTT ~45-70ms)
[Next.js 15 Web Server (Local / Vercel sin1)]
   ├── 1. Middleware: supabase.auth.getUser() ──────────► [Supabase Auth API (Cloudflare Edge)]
   ├── 2. DashboardLayout: resolveUserRole() ───────────► [Supabase Auth + Direct pg.Pool (Mumbai)]
   ├── 3. Page Component: resolveUserRole() (repeat) ───► [Memory Cache / Postgres (Mumbai)]
   └── 4. BFF API / Server Fetch:
           │
           ▼ (HTTP loopback ~0.5ms or Cross-Network)
   [CampXSync API Gateway (Port 8080)]
      ├── JWT Validation: SupabaseJwtValidator (JwksClient in-memory cache)
      ├── Gateway Filters: CorrelationFilter (X-Correlation-Id)
      ├── Security Boundary: HMAC-SHA256 Signing (X-Gateway-Signature, 60s window)
      └── ReverseProxyHandler (Prefix matching & routing)
           │
           ▼ (HTTP loopback ~0.5ms)
   [Java Domain Microservice (Ports 8081-8098)]
      ├── HMAC Signature Verification: GatewayHmacVerifier
      ├── UserSecurityContext: Role & Tenant Extraction
      ├── HikariCP Connection Acquisition: Pool checkout
      ├── Transaction & RLS Scope: SET LOCAL ROLE & claims
      ├── SQL Domain Execution: Prepared statements
      └── Intelligence Event Delivery: ControlledIntelligenceAdapter (Async / Outbox)
           │
           ▼ (TLS JDBC / Port 6543 / RTT ~26.5ms)
   [Supabase PostgreSQL 17.11 (AWS Mumbai ap-south-1)]
```

---

## 4. Master Flow & System Inventory

The following table catalogs every user flow across all five system personas, identifying the UI route, Next.js API / BFF route, API Gateway route, downstream Java microservice, and underlying database objects touched:

| Flow Identifier | Persona | UI Route | Next.js API / BFF Route | Gateway Ingress Route | Java Service / Endpoint | DB Objects (Tables, Views, Policies) |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **AUTH-01** Login Page Render | All | `/login` | N/A (Server Component) | N/A | N/A | None (Static HTML + Client Hydration) |
| **AUTH-02** Password Authentication | All | `/login` (Form Submit) | Client Supabase GoTrue | N/A | N/A | `auth.users`, `auth.identities`, GoTrue auth schema |
| **AUTH-03** Post-Login Destination Resolution | All | Client redirect | `GET /api/auth/resolve` | N/A (Direct DB query in BFF) | N/A | `plat.platform_admins`, `iam.user_profiles`, `iam.user_roles`, `core.colleges` |
| **AUTH-04** Root Path Router | All | `/` | Handled by `middleware.ts` / `page.tsx` | N/A | N/A | `auth.users`, `iam.user_profiles` |
| **AUTH-05** Access Denied Guard | All | `/access-denied` | N/A | N/A | N/A | None |
| **PLT-01** Platform Dashboard | Platform Admin | `/platform` | Server Component Fetch | `GET /api/v1/academics/analytics/dashboard?role=PLATFORM_ADMIN`<br>`GET /api/v1/user/preferences?role=platform_admin` | ACD-10 Analytics Service (8092)<br>ADM-01 Institute Admin (8081) | `plat.tenants`, `plat.audit_logs`, `iam.user_profiles`, `core.colleges` |
| **PLT-02** Institutes Registry | Platform Admin | `/platform/institutes` | Server Component Fetch | `GET /v1/institutes` | ADM-01 Institute Admin (8081) `/api/v1/admin/institutes` | `plat.tenants`, `core.colleges`, `plat.configurations` |
| **PLT-03** Tenant Onboarding | Platform Admin | `/platform/institutes/new` | `POST /api/platform/onboard-tenant` | `POST /v1/onboard-tenant` | ADM-01 Institute Admin (8081) `/api/v1/admin/onboard-tenant` | `plat.tenants`, `core.colleges`, `iam.user_profiles`, `iam.user_roles`, `plat.audit_logs` |
| **PLT-04** Platform Audit Trail | Platform Admin | `/platform/audit` | Server Component Fetch | `GET /v1/platform-audit-logs` | ADM-01 Institute Admin (8081) `/api/v1/admin/audit-logs` | `plat.audit_logs` |
| **COL-01** College Admin Dashboard | College Admin | `/college` | Server Component Fetch | `GET /api/v1/academics/analytics/dashboard?role=COLLEGE_ADMIN` | ACD-10 Analytics Service (8092) `/api/v1/academics/analytics/dashboard` | `stu.students`, `hrm.employees`, `acd.attendance_sessions`, `acd.attendance_records`, `acd.batches`, `iam.user_profiles` |
| **COL-02** Departments Management | College Admin | `/college/departments` | `GET/POST /api/college/departments` | `GET/POST /v1/departments` | ADM-02 College Admin (8082) `/api/v1/college-admin/departments` | `core.departments`, `core.colleges` |
| **COL-03** Academic Programs Management | College Admin | `/college/programs` | `GET/POST /api/college/programs` | `GET/POST /v1/programs` | ADM-02 College Admin (8082) `/api/v1/college-admin/programs` | `acd.programs`, `core.departments` |
| **COL-04** Academic Batches Management | College Admin | `/college/batches` | Server Component Fetch | `GET /api/v1/academics/batches`<br>`GET /api/v1/academics/subjects` | ACD-04 Batch Service (8086)<br>ACD-03 Subject Service (8085) | `acd.batches`, `acd.programs`, `acd.subjects`, `acd.batch_students` |
| **COL-05** Student Roster Inspection | College Admin | `/college/students` | `GET /api/college/students` | `GET /api/v1/students` | STU-01 Student Service (8093) `/api/v1/students` | `stu.students`, `core.persons`, `acd.programs`, `acd.batches` |
| **COL-06** Student Batch Assignment | College Admin | `/college/students` (Modal) | `POST /api/college/students/assign-batch` | `POST /api/v1/students/{id}/assign-batch` | STU-01 Student Service (8093) | `acd.batch_students`, `stu.students`, `stu.student_lifecycle_events` |
| **COL-07** Timetable Management | College Admin | `/college/timetable` | `GET /api/college/timetable`<br>`GET /api/college/timetable/entries` | `GET /api/v1/academics/timetables` | ACD-05 Timetable Service (8087) `/api/v1/academics/timetables` | `acd.timetable_entries`, `acd.batches`, `acd.subjects`, `hrm.employees` |
| **COL-08** Attendance Oversight & Reports | College Admin | `/college/attendance` | `GET /api/college/attendance/sessions`<br>`GET /api/college/attendance/report` | `GET /api/v1/academics/attendance/sessions`<br>`GET /api/v1/academics/attendance/report` | ACD-06 Attendance Service (8088) | `acd.attendance_sessions`, `acd.attendance_records`, `stu.students` |
| **COL-09** Attendance Correction Review | College Admin | `/college/attendance` (Drawer) | `GET/POST /api/college/attendance/corrections` | `GET/POST /api/v1/attendance/corrections` | ACD-06 Attendance Service (8088) | `acd.attendance_corrections`, `acd.attendance_records` |
| **COL-10** Guardian Registry & Multi-Linking | College Admin | `/college/guardians` | `GET/POST /api/college/guardians` | `GET/POST /api/v1/guardians`<br>`POST /api/v1/guardians/link-existing` | STM-05 Guardian Service (8097) | `stu.guardians`, `stu.guardianships`, `core.persons`, `stu.students` |
| **COL-11** Admissions Intake Management | College Admin | `/college/admissions/intakes` | `GET/POST /api/college/admissions/intakes` | `GET/POST /api/v1/admissions/intakes` | STM-02 Admissions Service (8094) | `stu.admissions_intakes`, `acd.programs` |
| **COL-12** Admissions Applications List | College Admin | `/college/admissions` | `GET /api/college/admissions/applications` | `GET /api/v1/admissions/applications` | STM-02 Admissions Service (8094) | `stu.admissions_applications`, `core.persons`, `stu.admissions_intakes` |
| **COL-13** Application Review & Decision | College Admin | `/college/admissions/applications/[id]` | `POST /api/college/admissions/applications/[id]/decision` | `POST /api/v1/admissions/applications/{id}/decision` | STM-02 Admissions Service (8094) | `stu.admissions_applications`, `stu.admissions_reviews`, `stu.admissions_offers` |
| **COL-14** Application Handover to Student | College Admin | `/college/admissions/applications/[id]` | `POST /api/college/admissions/applications/[id]/handover` | `POST /api/v1/admissions/applications/{id}/handover` | STM-02 Admissions Service (8094) | `stu.admissions_applications`, `stu.students`, `core.persons`, `iam.user_profiles` |
| **COL-15** Document Types Configuration | College Admin | `/college/documents` | `GET/POST /api/college/documents/types` | `GET/POST /api/v1/documents/types` | STM-06 Documents Service (8098) | `stu.student_document_types`, `core.documents` |
| **COL-16** Enrollment Management | College Admin | `/college/enrollment` | `GET/POST /api/college/enrollment` | `GET/POST /api/v1/enrollment` | STM-03 Enrollment Service (8095) | `stu.student_enrollments`, `stu.students`, `acd.academic_terms` |
| **COL-17** Lifecycle Operations | College Admin | `/college/lifecycle` | `GET/POST /api/college/lifecycle` | `GET/POST /api/v1/lifecycle` | STM-04 Lifecycle Service (8096) | `stu.student_lifecycle_events`, `stu.students` |
| **COL-18** Faculty Directory | College Admin | `/college/faculty` | `GET /api/college/faculty` | `GET /api/v1/college-admin/faculty` | ADM-02 College Admin (8082) | `hrm.employees`, `core.persons`, `core.departments` |
| **COL-19** College Audit Trail | College Admin | `/college/audit` | Server Component Fetch | `GET /v1/college-audit-logs` | ADM-02 College Admin (8082) | `core.audit_logs` |
| **FAC-01** Faculty Dashboard | Faculty | `/faculty` | Server Component Fetch | `GET /api/v1/academics/analytics/dashboard?role=FACULTY` | ACD-10 Analytics Service (8092) `/api/v1/academics/analytics/dashboard` | `acd.timetable_entries`, `acd.attendance_sessions`, `acd.subjects`, `hrm.employees` |
| **FAC-02** Faculty Assigned Timetable | Faculty | `/faculty/timetable` | Server Component Fetch | `GET /api/v1/academics/timetables` | ACD-05 Timetable Service (8087) | `acd.timetable_entries`, `acd.subjects`, `acd.batches` |
| **FAC-03** Faculty Class Roster | Faculty | `/faculty/roster` | Server Component Fetch | `GET /api/v1/students` | STU-01 Student Service (8093) | `stu.students`, `core.persons`, `acd.batch_students` |
| **FAC-04** Faculty Teaching Sessions | Faculty | `/faculty/sessions` | Server Component Fetch | `GET /api/v1/academics/attendance/sessions` | ACD-06 Attendance Service (8088) | `acd.attendance_sessions`, `acd.timetable_entries` |
| **FAC-05** Attendance Marking | Faculty | `/faculty/attendance` | `POST /api/college/attendance/records` | `POST /api/v1/academics/attendance/records` | ACD-06 Attendance Service (8088) | `acd.attendance_records`, `acd.attendance_sessions` |
| **FAC-06** Student Advisees | Faculty | `/faculty/advisees` | Server Component Fetch | `GET /api/v1/students` | STU-01 Student Service (8093) | `stu.students`, `core.persons`, `hrm.employees` |
| **STU-01** Student Dashboard | Student 1 / 2 | `/student` | Server Component Fetch | `GET /api/v1/students/me` (fallback direct SQL) | STU-01 Student Service (8093)<br>BFF Fallback: Direct PostgreSQL `query(...)` | `stu.students`, `core.persons`, `core.colleges`, `acd.programs`, `acd.subjects`, `acd.timetable_entries`, `acd.attendance_sessions`, `acd.attendance_records` |
| **STU-02** Admissions Applications & Offers | Student 1 / 2 | `/student/admissions` | `GET /api/student/admissions/applications`<br>`POST .../accept-offer` | `GET /api/v1/admissions/applications`<br>`POST .../accept-offer` | STM-02 Admissions Service (8094) | `stu.admissions_applications`, `stu.admissions_offers`, `stu.admissions_documents` |
| **STU-03** Profile & Address Management | Student 1 / 2 | `/student/address` | `GET/POST /api/student/[id]/address` | `GET/POST /api/v1/students/{id}/address` | STU-01 Student Service (8093) | `core.addresses`, `core.persons`, `stu.students` |
| **STU-04** Attendance Corrections | Student 1 / 2 | `/student/corrections` | `GET/POST /api/student/[id]/corrections` | `GET/POST /api/v1/attendance/corrections` | ACD-06 Attendance Service (8088) | `acd.attendance_corrections`, `acd.attendance_records` |
| **STU-05** Student Documents Upload | Student 1 / 2 | `/student/documents` | `GET/POST /api/student/admissions/documents` | `GET/POST /api/v1/documents` | STM-06 Documents Service (8098) | `stu.student_documents`, `stu.student_document_types` |
| **STU-06** Student Course Enrollment | Student 1 / 2 | `/student/enrollment` | `GET/POST /api/student/enrollment` | `GET/POST /api/v1/enrollment` | STM-03 Enrollment Service (8095) | `stu.student_enrollments`, `acd.courses`, `acd.academic_terms` |
| **STU-07** Student Lifecycle Tracking | Student 1 / 2 | `/student/lifecycle` | `GET /api/student/lifecycle` | `GET /api/v1/lifecycle` | STM-04 Lifecycle Service (8096) | `stu.student_lifecycle_events`, `stu.students` |

---

## 5. Architectural Findings & Latency Hotspots Observed in Inventory

1. **Triple Role & Profile Resolution Waterfall on Every Dashboard View:**
   - On every authenticated navigation, `frontend/src/middleware.ts` makes a remote network call to Supabase Auth (`supabase.auth.getUser()`).
   - Next, `(dashboard)/layout.tsx` invokes `resolveUserRole(supabase)`, which executes `supabase.auth.getUser()` a second time plus up to 4 direct PostgreSQL queries (`plat.platform_admins`, `iam.user_profiles`, `iam.user_roles`, `core.colleges`).
   - Then, the destination page Server Component (e.g. `college/page.tsx` line 13, `student/page.tsx` line 29, `faculty/page.tsx` line 13, `platform/page.tsx` line 13) calls `resolveUserRole(supabase)` a third time.
2. **Missing Local / Cloud Java Hosting:**
   - The Java API Gateway and all 18 Java domain microservices are not hosted on any cloud infrastructure.
   - In production or Vercel preview environments, calling `http://localhost:8080` fails or hangs until timeout, forcing code fallbacks or throwing 500 errors.
3. **Synchronous Single-Threaded HTTP Dispatch:**
   - In `GatewayServer.java` and all microservice server classes (e.g. `StudentServer.java`), `server.setExecutor(null)` is configured. Under concurrent requests or sequential waterfalls, the embedded JDK HTTP server handles connections synchronously on a single dispatcher thread without thread pool distribution.
4. **Network RTT Amplification:**
   - The development Supabase PostgreSQL database is hosted in AWS Mumbai (`ap-south-1`, pooler at `aws-0-ap-south-1.pooler.supabase.com:6543`), yielding a ~26.5 ms TCP round-trip from Bengaluru. A single user action that performs 4 sequential database queries and 2 Supabase Auth API calls incurs at least 6 round trips (~160 ms in pure network propagation delay alone, excluding query execution and TLS handshakes).
5. **No Data Caching or Static Optimization:**
   - All dashboard routes enforce `export const dynamic = 'force-dynamic'`. Every navigation triggers dynamic SSR recalculation without ISR, React Server Component caching, or HTTP cache headers.

