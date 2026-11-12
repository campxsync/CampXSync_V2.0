# CampXSync Full Stack Local Startup Guide

**Document:** [`docs/latency-audit/START_LOCAL_STACK.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/START_LOCAL_STACK.md)  
**Target Environment:** Local Stack with Dev Supabase Project `azulbaxszsqxymqmlwfe` (Mumbai AWS `ap-south-1`, pooler at `aws-0-ap-south-1.pooler.supabase.com:6543`)  
**Branch:** `feature/java-backend-integration` (`ef6e7dbed9dfde7cd492c5b7768542ce958d3a62`)  
**Audit Date:** 2026-11-11  

---

## 1. Verified Prerequisites on Host

| Prerequisite | Detected Version & Path | Status |
| :--- | :--- | :--- |
| **Java Development Kit** | Java SE 1.8.0_202-b08 (Java HotSpot(TM) 64-Bit Server VM, runtime: `C:\Java\jdk\jre`) | PASS |
| **Apache Maven** | Apache Maven 3.9.9 (`C:\Users\niran\Downloads\apache-maven-3.9.9-bin\apache-maven-3.9.9\bin\mvn.cmd`) | PASS |
| **Node.js Runtime** | Node.js v22.14.0 | PASS |
| **npm Package Manager** | npm 10.9.2 | PASS |
| **Application Ports** | 8080–8098 and 3000 verified free via `netstat -ano` prior to launch | PASS |

---

## 2. Environment Variables & Untracked Files Used (Names Only)

Zero files outside `docs/latency-audit/` were modified. The stack connects to the agreed Dev Supabase project `azulbaxszsqxymqmlwfe` using ambient untracked configurations:
- **System / Shell Environment**:
  - `SUPABASE_JDBC_URL` (Supavisor pooler port 6543)
  - `SUPABASE_USER` (Scoped to dev ref `azulbaxszsqxymqmlwfe`)
  - `SUPABASE_PASSWORD`
  - `CAMPX_INTERNAL_SECRET`
  - `CAMPX_SUPABASE_JWT_ISSUER`
  - `CAMPX_TEST_ENV=true`
- **Frontend Environment (`frontend/.env.local`)**:
  - `NEXT_PUBLIC_SUPABASE_URL`
  - `NEXT_PUBLIC_SUPABASE_ANON_KEY`
  - `API_GATEWAY_URL=http://localhost:8080`
  - `SUPABASE_JDBC_URL`, `SUPABASE_USER`, `SUPABASE_PASSWORD`

---

## 3. Strict Startup Order & Architecture Topology

1. **Phase 1: Domain Microservices (Ports 8081–8098)**:
   All 18 Java microservices establish connection pools with HikariCP, apply RLS context, and expose REST & Prometheus endpoints.
2. **Phase 2: API Gateway (Port 8080)**:
   Unified reverse proxy that mounts route dispatches, verifies PostgreSQL identity resolution, and binds to loopback `127.0.0.1:8080`.
3. **Phase 3: Next.js Web Frontend (Port 3000)**:
   Compiled production Next.js 15 build (`next start`) on port 3000, routing client requests and server-side fetches through the local API Gateway.

---

## 4. Component Matrix

Common classpath:
`$commonJars = "C:\Users\niran\.m2\repository\org\postgresql\postgresql\42.7.3\postgresql-42.7.3.jar;C:\Users\niran\.m2\repository\org\checkerframework\checker-qual\3.42.0\checker-qual-3.42.0.jar;C:\Users\niran\.m2\repository\com\zaxxer\HikariCP\4.0.3\HikariCP-4.0.3.jar;C:\Users\niran\.m2\repository\org\slf4j\slf4j-api\1.7.30\slf4j-api-1.7.30.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-databind\2.15.4\jackson-databind-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-annotations\2.15.4\jackson-annotations-2.15.4.jar;C:\Users\niran\.m2\repository\com\fasterxml\jackson\core\jackson-core\2.15.4\jackson-core-2.15.4.jar;D:\CampXSync\CampXSync_V2.0\logger\target\classes"`

| Component | Port | Initial Audit Status | Persona Call Status | Health / Probe URL | Working Directory | Start Command |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ADM-01 Institute Admin** | 8081 | STOPPED during initial audit | Required by Platform Admin | `http://127.0.0.1:8081/api/v1/user/preferences` | `services/administration-services/institute-admin-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.admin.institute.InstituteAdminApplication 8081` |
| **ADM-02 College Admin** | 8082 | STOPPED during initial audit | Required by College Admin | `http://127.0.0.1:8082/api/v1/college-admin/profile` | `services/administration-services/college-admin-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.admin.college.CollegeAdminApplication 8082` |
| **ACD-01 Course Management** | 8083 | STOPPED during initial audit | Unused by current 5 personas | `http://127.0.0.1:8083/api/v1/courses` | `services/academic-services/course-management-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.course.CourseManagementApplication 8083` |
| **ACD-02 Curriculum Management** | 8084 | STOPPED during initial audit | Unused by current 5 personas | `http://127.0.0.1:8084/metrics` | `services/academic-services/curriculum-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.curriculum.CurriculumManagementApplication 8084` |
| **ACD-03 Subject Management** | 8085 | STOPPED during initial audit | Required by College Admin | `http://127.0.0.1:8085/metrics` | `services/academic-services/subject-management-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.subject.SubjectManagementApplication 8085` |
| **ACD-04 Batch Management** | 8086 | RUNNING (PID 20032) | Required by College Admin & Student | `http://127.0.0.1:8086/metrics` | `services/academic-services/batch-management-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.batch.BatchManagementApplication 8086` |
| **ACD-05 Timetable Management** | 8087 | STOPPED during initial audit | Required by College Admin & Faculty | `http://127.0.0.1:8087/metrics` | `services/academic-services/timetable-management-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.timetable.TimetableManagementApplication 8087` |
| **ACD-06 Attendance Management** | 8088 | STOPPED during initial audit | Required by College Admin & Faculty | `http://127.0.0.1:8088/metrics` | `services/attendance-services/attendance-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.attendance.AttendanceManagementApplication 8088` |
| **ACD-07 Academic Calendar** | 8089 | STOPPED during initial audit | Unused by current 5 personas | `http://127.0.0.1:8089/metrics` | `services/academic-services/academic-calendar-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.calendar.AcademicCalendarApplication 8089` |
| **ACD-08 Learning Resource** | 8090 | STOPPED during initial audit | Unused by current 5 personas | `http://127.0.0.1:8090/metrics` | `services/lms-services/learning-resource-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.resource.LearningResourceApplication 8090` |
| **ACD-09 Assessment Mapping** | 8091 | STOPPED during initial audit | Unused by current 5 personas | `http://127.0.0.1:8091/metrics` | `services/academic-services/assessment-mapping-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.assessment.AssessmentMappingApplication 8091` |
| **ACD-10 Reporting & Analytics** | 8092 | STOPPED during initial audit | Required by Dashboards (returns mock) | `http://127.0.0.1:8092/metrics` | `services/analytics-services/analytics-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.academic.analytics.AnalyticsApplication 8092` |
| **STU-01 Student Domain** | 8093 | STOPPED during initial audit | Required by College Admin, Faculty, Student | `http://127.0.0.1:8093/actuator/health` | `services/student-services/student-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.student.StudentApplication 8093` |
| **STM-02 Admissions & Applications** | 8094 | STOPPED during initial audit | Required by College Admin & Student | `http://127.0.0.1:8094/actuator/health` | `services/student-services/admissions-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.admissions.AdmissionsApplication 8094` |
| **STM-03 Enrollment & Registration** | 8095 | STOPPED during initial audit | Required by College Admin & Student | `http://127.0.0.1:8095/actuator/health` | `services/student-services/enrollment-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.enrollment.EnrollmentApplication 8095` |
| **STM-04 Student Lifecycle** | 8096 | STOPPED during initial audit | Required by College Admin & Student | `http://127.0.0.1:8096/actuator/health` | `services/student-services/lifecycle-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.lifecycle.LifecycleApplication 8096` |
| **STM-05 Guardian & Relationships** | 8097 | STOPPED during initial audit | Required by College Admin | `http://127.0.0.1:8097/actuator/health` | `services/student-services/guardian-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.guardian.GuardianApplication 8097` |
| **STM-06 Student Documents** | 8098 | STOPPED during initial audit | Required by College Admin | `http://127.0.0.1:8098/actuator/health` | `services/student-services/document-service` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;$commonJars" com.campx.document.DocumentApplication 8098` |
| **API Gateway** | 8080 | RUNNING | Core Ingress Proxy | `http://127.0.0.1:8080/actuator/health` | `api-gateway` | `java "-Dcampx.persistence.mode=postgres" "-Dcampx.internal.auth.enabled=false" -cp "target\classes;(cat classpath.txt)" com.campx.gateway.ApiGatewayApplication 8080` |
| **Next.js Web Frontend** | 3000 | RUNNING | UI Presentation Layer | `http://127.0.0.1:3000/login` | `frontend` | `npm run start` |

---

## 5. End-to-End Verification Evidence & Mock Data Caveat

- **Browser Flow**: Verified visually using Playwright in headed Chromium browser mode.
- **Login Action**: Navigated to `http://localhost:3000/login`, authenticated as Platform Admin (`platformadmin.campx@gmail.com`).
- **Destination Verification**: Successfully redirected to `http://localhost:3000/platform`.
- **Card Metrics & Mock Data Reality**:
  - The UI displays `ONBOARDED INSTITUTES: 3`, `ACTIVE TENANCIES: 3`, and `SYSTEM ROLE TEMPLATES: 30`.
  - **Audit Finding**: These values are NOT live ingestions from `plat.tenants`. They originate from `InMemoryAnalyticsDashboardRepository` inside `analytics-service` (Port 8092), which returns static hardcoded mock constants:
    ```java
    summary.put("tenantCount", 3);
    statusCounts.put("ACTIVE", 3);
    summary.put("statusCounts", statusCounts);
    payload.put("institutes", Collections.emptyList());
    ```
  - Because `institutes` is an empty list, the bottom table displays *"No institutes onboarded yet"*, while the top card hardcodes `3`.
  - Zero "fetch failed" toasts appeared only because the Gateway handled the empty response cleanly.

---

## 6. Flows Bypassing Java via Direct SQL

Multiple Next.js pages and API route handlers bypass the Java API Gateway entirely or implement silent fallbacks to direct Supabase PostgreSQL queries when Java microservices are unavailable:

1. **`frontend/src/app/(dashboard)/student/page.tsx`**:
   - Primary profile and enrollment stats query Supabase PostgreSQL directly using `@supabase/ssr` client (`sbClient.from('profiles').select(...)`, `sbClient.from('enrollments').select(...)`).
   - Only secondary tabs call Java domain services.
2. **`frontend/src/app/(dashboard)/college/faculty/page.tsx`**:
   - Queries `auth.users` and `campus_staff` directly through Supabase SSR client instead of routing through `ADM-02` or `STU-01`.
3. **`frontend/src/app/(dashboard)/college/timetable/page.tsx`**:
   - Directly queries `timetable_slots` and `course_offerings` tables when `ACD-05` (8087) is not reachable, masking the backend outage.
4. **BFF Route Handlers (`frontend/src/app/api/platform/institutes/route.ts`)**:
   - Implements try/catch around Gateway fetch (`http://localhost:8080/api/v1/institutes`); when it encounters `ConnectException` or fetch failure, it falls back to `createClient().from('plat_tenants').select('*')` without alerting the user.
- **Visual Evidence Artifact**: Saved full-page screenshot at [`docs/latency-audit/evidence_platform_dashboard.png`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/evidence_platform_dashboard.png).

---

## 6. Stop Procedure

To terminate all 20 running background processes on ports 8080–8098 and 3000:

```powershell
$ports = 8080..8098 + 3000
foreach ($port in $ports) {
    $conns = Get-NetTCPConnection -LocalPort $port -ErrorAction SilentlyContinue
    if ($conns) {
        $pids = $conns | Select-Object -ExpandProperty OwningProcess -Unique
        foreach ($p in $pids) {
            Stop-Process -Id $p -Force -ErrorAction SilentlyContinue
        }
    }
}
```

---

## 7. Operational Troubleshooting Summary

During stack initialization, three root friction points were identified:
1. **PowerShell Argument Parsing with JVM `-D` Flags**: When launching `java` via PowerShell strings, `-Dcampx.persistence.mode=postgres` must be enclosed in explicit quotes (`"-Dcampx.persistence.mode=postgres"`). When unquoted, PowerShell treats `-D` as a switch parameter, causing Java to misinterpret `.persistence.mode=postgres` as an unfindable main class.
2. **Next.js Server Component Gateway Ingress Authentication**: When `CAMPX_INTERNAL_AUTH_ENABLED=true`, the API Gateway strictly demands an `Authorization: Bearer <jwt>` header and rejects requests lacking it with HTTP 401. However, Next.js Server Components in `platform/page.tsx` and `college/page.tsx` only pass trusted contextual headers (`X-User-Id`, `X-User-Role`, `X-Tenant-Id`) without the raw client JWT. In local dev mode, setting `-Dcampx.internal.auth.enabled=false` enables the Gateway and downstream microservices to accept the server's trusted headers without failing closed.
3. **HikariCP Connection Startup**: The development PostgreSQL database is hosted on AWS Mumbai (`ap-south-1`), with initial TLS connection handshakes and pool acquisition taking ~2–4 seconds per service. Health polling checks must allow at least 15–20 seconds to prevent premature timeout declarations during cold JVM startup.
