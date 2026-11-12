# CampXSync Latency Audit — Phase A Verification Report

**Document**: `docs/latency-audit/07_VERIFICATION.md`  
**Date**: November 11, 2026  
**Auditor**: Senior Performance Engineering Team  
**Branch**: `feature/java-backend-integration` (`ef6e7dbed9dfde7cd492c5b7768542ce958d3a62`)  
**Status**: **PHASE A VERIFIED — READ-ONLY COMPLETED — STOP GATE ACTIVE**  

---

## 1. Services Listening During Audit & Gateway Routing Matrix

### 1.1 Netstat Listening Audit for Ports 3000 & 8080–8098

A live network port audit (`netstat -ano | findstr LISTENING`) on the Windows host reveals the exact process topology that was active during the benchmark execution:

| Port | Bound Component | PID | Process / Archetype | Operational Status During Benchmark |
|:---:|:---|:---:|:---|:---:|
| **3000** | Next.js 15 Web Frontend | `16968` | Node.js Production Build (`npm run start`) | **LISTENING (HEALTHY)** |
| **8080** | CampXSync API Gateway | `35340` | `ApiGatewayApplication` (Sun `HttpServer`) | **LISTENING (HEALTHY)** |
| **8081** | ADM-01 Institute Admin Service | `25264` | `InstituteAdminApplication` | **LISTENING (HEALTHY)** |
| **8082** | ADM-02 College Admin Service | `24520` | `CollegeAdminApplication` | **LISTENING (HEALTHY)** |
| **8083** | ACD-01 Course Management Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8084** | ACD-02 Curriculum Management Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8085** | ACD-03 Subject Management Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8086** | ACD-04 Batch Management Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8087** | ACD-05 Timetable Management Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8088** | ACD-06 Attendance Management Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8089** | ACD-07 Academic Calendar Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8090** | ACD-08 Learning Resource Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8091** | ACD-09 Assessment Mapping Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8092** | ACD-10 Analytics Service | `39328` | `AnalyticsApplication` | **LISTENING (HEALTHY)** |
| **8093** | STU-01 Student Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8094** | STM-02 Admissions & Applications Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8095** | STM-03 Enrollment & Registration Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8096** | STM-04 Lifecycle Management Service | — | — | **NOT RUNNING (OFFLINE)** |
| **8097** | STM-05 Guardian & Relationships Service| — | — | **NOT RUNNING (OFFLINE)** |
| **8098** | STM-06 Student Documents Service | — | — | **NOT RUNNING (OFFLINE)** |

**Summary**: Only **3 of 18** domain microservices (`ADM-01` on 8081, `ADM-02` on 8082, and `ACD-10` on 8092) plus the API Gateway (`8080`) and Next.js (`3000`) were listening. **15 domain microservices were offline.**

---

### 1.2 Gateway Log Evidence: ConnectException Lines & Target Ports

Inspection of the active Gateway process log (`task-445.log`) corroborates that when Next.js proxied requests for student, batch, subject, admissions, or enrollment domains, the Gateway repeatedly incurred `java.net.ConnectException: Connection refused: connect`:

```
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/students/me]: Connection refused: connect (Target: port 8093)
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/admissions/applications]: Connection refused: connect (Target: port 8094)
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/academics/batches]: Connection refused: connect (Target: port 8086)
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/academics/subjects]: Connection refused: connect (Target: port 8085)
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/enrollment/enrolments]: Connection refused: connect (Target: port 8095)
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/lifecycle]: Connection refused: connect (Target: port 8096)
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/documents]: Connection refused: connect (Target: port 8098)
com.campx.gateway.router.ReverseProxyHandler - Downstream service unreachable for path [/api/v1/attendance]: Connection refused: connect (Target: port 8088)
```

Each `ConnectException` forced the Gateway thread to hang in `HttpURLConnection.connect()` for ~2,100 to 2,550 ms before returning HTTP 503 `GATEWAY_SERVICE_UNAVAILABLE`.

---

### 1.3 Complete Gateway Route Table → Target Port Mapping

Configured in `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java`:

| Gateway Ingress Prefix | Downstream Target URL | Target Port | Assigned Microservice | Status in Audit |
|:---|:---|:---:|:---|:---:|
| `/api/v1/admin` | `http://localhost:8081/api/v1/admin` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/api/v1/user/preferences` | `http://localhost:8081/api/v1/user/preferences` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/api/v1/user` | `http://localhost:8081/api/v1/user` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/v1/institutes` | `http://localhost:8081/api/v1/admin/institutes` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/v1/platform-configs` | `http://localhost:8081/api/v1/admin/configuration` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/v1/platform-roles` | `http://localhost:8081/api/v1/admin/roles` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/v1/billing-accounts` | `http://localhost:8081/api/v1/admin/billing/plans` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/v1/platform-audit-logs`| `http://localhost:8081/api/v1/admin/audit-logs` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/v1/onboard-tenant` | `http://localhost:8081/api/v1/admin/onboard-tenant` | **8081** | ADM-01 Institute Admin | **LISTENING** |
| `/api/v1/college-admin` | `http://localhost:8082/api/v1/college-admin` | **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/college-profile` | `http://localhost:8082/api/v1/college-admin/profile` | **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/departments` | `http://localhost:8082/api/v1/college-admin/departments`| **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/programs` | `http://localhost:8082/api/v1/college-admin/programs` | **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/college-configs` | `http://localhost:8082/api/v1/college-admin/settings` | **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/imports` | `http://localhost:8082/api/v1/college-admin/imports` | **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/college-workflows` | `http://localhost:8082/api/v1/college-admin/workflows`| **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/batch-approvals` | `http://localhost:8082/api/v1/college-admin/workflows/batch-approvals` | **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/college-approvals` | `http://localhost:8082/api/v1/college-admin/approvals`| **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/college-roles` | `http://localhost:8082/api/v1/college-admin/roles` | **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/college-permissions`| `http://localhost:8082/api/v1/college-admin/permissions`| **8082** | ADM-02 College Admin | **LISTENING** |
| `/v1/college-audit-logs`| `http://localhost:8082/api/v1/college-admin/audit-logs`| **8082** | ADM-02 College Admin | **LISTENING** |
| `/api/v1/courses` | `http://localhost:8083/api/v1/courses` | **8083** | ACD-01 Course Management | OFFLINE |
| `/v1/courses` | `http://localhost:8083/api/v1/courses` | **8083** | ACD-01 Course Management | OFFLINE |
| `/api/v1/curricula` | `http://localhost:8084/api/v1/curricula` | **8084** | ACD-02 Curriculum Management | OFFLINE |
| `/v1/curricula` | `http://localhost:8084/api/v1/curricula` | **8084** | ACD-02 Curriculum Management | OFFLINE |
| `/api/v1/subjects` | `http://localhost:8085/api/v1/subjects` | **8085** | ACD-03 Subject Management | OFFLINE |
| `/v1/subjects` | `http://localhost:8085/api/v1/subjects` | **8085** | ACD-03 Subject Management | OFFLINE |
| `/api/v1/batches` | `http://localhost:8086/api/v1/academics/batches` | **8086** | ACD-04 Batch Management | OFFLINE |
| `/v1/batches` | `http://localhost:8086/api/v1/academics/batches` | **8086** | ACD-04 Batch Management | OFFLINE |
| `/api/v1/timetables` | `http://localhost:8087/api/v1/academics/timetables` | **8087** | ACD-05 Timetable Management | OFFLINE |
| `/v1/timetables` | `http://localhost:8087/api/v1/academics/timetables` | **8087** | ACD-05 Timetable Management | OFFLINE |
| `/api/v1/attendance` | `http://localhost:8088/api/v1/academics/attendance` | **8088** | ACD-06 Attendance Management | OFFLINE |
| `/v1/attendance` | `http://localhost:8088/api/v1/academics/attendance` | **8088** | ACD-06 Attendance Management | OFFLINE |
| `/api/v1/calendars` | `http://localhost:8089/api/v1/academics/calendars` | **8089** | ACD-07 Academic Calendar | OFFLINE |
| `/v1/calendars` | `http://localhost:8089/api/v1/academics/calendars` | **8089** | ACD-07 Academic Calendar | OFFLINE |
| `/api/v1/resources` | `http://localhost:8090/api/v1/academics/resources` | **8090** | ACD-08 Learning Resource | OFFLINE |
| `/v1/resources` | `http://localhost:8090/api/v1/academics/resources` | **8090** | ACD-08 Learning Resource | OFFLINE |
| `/api/v1/assessments` | `http://localhost:8091/api/v1/academics/assessments`| **8091** | ACD-09 Assessment Mapping | OFFLINE |
| `/v1/assessments` | `http://localhost:8091/api/v1/academics/assessments`| **8091** | ACD-09 Assessment Mapping | OFFLINE |
| `/api/v1/academics/analytics` | `http://localhost:8092/api/v1/academics/analytics`| **8092** | ACD-10 Analytics | **LISTENING** |
| `/v1/analytics` | `http://localhost:8092/api/v1/academics/analytics`| **8092** | ACD-10 Analytics | **LISTENING** |
| `/api/v1/dashboards` | `http://localhost:8092/api/v1/academics/analytics/dashboard` | **8092** | ACD-10 Analytics | **LISTENING** |
| `/api/v1/students` | `http://localhost:8093/api/v1/students` | **8093** | STU-01 Student Service | OFFLINE |
| `/v1/students` | `http://localhost:8093/api/v1/students` | **8093** | STU-01 Student Service | OFFLINE |
| `/api/v1/admissions` | `http://localhost:8094/api/v1/admissions` | **8094** | STM-02 Admissions Management | OFFLINE |
| `/v1/admissions` | `http://localhost:8094/api/v1/admissions` | **8094** | STM-02 Admissions Management | OFFLINE |
| `/api/v1/applications` | `http://localhost:8094/api/v1/admissions/applications` | **8094** | STM-02 Admissions Management | OFFLINE |
| `/api/v1/enrollment` | `http://localhost:8095/api/v1/enrollment` | **8095** | STM-03 Enrollment Service | OFFLINE |
| `/v1/enrollment` | `http://localhost:8095/api/v1/enrollment` | **8095** | STM-03 Enrollment Service | OFFLINE |
| `/api/v1/lifecycle` | `http://localhost:8096/api/v1/lifecycle` | **8096** | STM-04 Lifecycle Service | OFFLINE |
| `/v1/lifecycle` | `http://localhost:8096/api/v1/lifecycle` | **8096** | STM-04 Lifecycle Service | OFFLINE |
| `/api/v1/guardians` | `http://localhost:8097/api/v1/guardians` | **8097** | STM-05 Guardian Service | OFFLINE |
| `/v1/guardians` | `http://localhost:8097/api/v1/guardians` | **8097** | STM-05 Guardian Service | OFFLINE |
| `/api/v1/documents` | `http://localhost:8098/api/v1/documents` | **8098** | STM-06 Documents Service | OFFLINE |
| `/v1/documents` | `http://localhost:8082/api/v1/college-admin/documents` | **8082** | ADM-02 College Admin | **LISTENING** |

---

## 2. Memory Footprint & Recommended `-Xmx` Limits

### 2.1 Current Memory Measurements (Live Stack)

Measured via Windows Process Management APIs:

| Component | Port | Process ID | Working Set (RAM) | Paged Memory (Virtual) | Configured `-Xmx` |
|:---|:---:|:---:|:---:|:---:|:---:|
| **API Gateway** | 8080 | `35340` | **131.3 MB** | 461.2 MB | *Unset (Default ~4 GB)* |
| **ADM-01 (Institute Admin)** | 8081 | `25264` | **92.5 MB** | 457.1 MB | *Unset (Default ~4 GB)* |
| **ADM-02 (College Admin)** | 8082 | `24520` | **101.1 MB** | 459.7 MB | *Unset (Default ~4 GB)* |
| **ACD-10 (Analytics Service)**| 8092 | `39328` | **78.4 MB** | 444.9 MB | *Unset (Default ~4 GB)* |
| **Next.js Production BFF** | 3000 | `16968` | **184.6 MB** | 188.5 MB | Node default |

**Root Cause of High RAM Pressure (93%)**: By default, the 64-bit HotSpot JVM reserves 25% of physical RAM as max heap per JVM. Running 18 Java services without explicit `-Xmx` causes each JVM to reserve ~450 MB of virtual/paged address space and expand aggressively, exhausting physical memory.

### 2.2 Recommended Per-Service `-Xmx` Allocations

To run all required microservices simultaneously on this 16 GB machine without exceeding 80% RAM utilization (~12.5 GB max threshold):

| Service Tier / Role | Ports | Service Count | Recommended `-Xmx` | Recommended `-Xms` | Expected Resident Set (RAM) |
|:---|:---:|:---:|:---:|:---:|:---:|
| **API Gateway** | 8080 | 1 | `-Xmx192m` | `-Xms64m` | ~160 MB |
| **High-Volume Microservices** (ADM-01, ADM-02, ACD-10, STU-01) | 8081, 8082, 8092, 8093 | 4 | `-Xmx160m` | `-Xms48m` | ~130 MB each (520 MB total) |
| **Domain Microservices** (ACD-01..09, STM-02..06) | 8083–8091, 8094–8098 | 14 | `-Xmx128m` | `-Xms32m` | ~100 MB each (1,400 MB total) |
| **Total Java Stack (19 JVMs)** | 8080–8098 | 19 | — | — | **~2,080 MB (~2.1 GB)** |
| **Next.js 15 Production Build** | 3000 | 1 | `--max-old-space-size=384` | — | **~220 MB** |
| **Playwright Headed Chromium** | — | 1 | — | — | **~450 MB** |
| **Host OS + IDE / Background** | — | — | — | — | **~7,000 MB (~7.0 GB)** |
| **Total Projected Memory Under Full Stack** | — | — | — | — | **~9,750 MB (~9.8 GB = 62.0% RAM)** |

**Recommendation**: Launching all Java processes with `-Xms32m -Xmx128m` (and `-Xmx192m` for Gateway) provides a comfortable **3.5 GB buffer below the 80% RAM ceiling**.

---

## 3. Definition of "Action-to-Visible" in Benchmark Harness

### 3.1 Playwright Implementation Analysis

In `scratch/run_audit_measurements.mjs`:
- **For Login (`AUTH_*`)**:
  ```javascript
  const submitT0 = Date.now();
  await lp.click('button[type="submit"]');
  await lp.waitForURL(`**${flow.destUrl}*`, { timeout: 25000 });
  await lp.waitForLoadState('networkidle');
  actionToVisible = Date.now() - submitT0;
  ```
- **For Navigation (`PLT_*`, `COL_*`, `FAC_*`, `STU_*`)**:
  ```javascript
  const navT0 = Date.now();
  const response = await iterPage.goto(flow.url, { waitUntil: 'networkidle', timeout: 25000 });
  actionToVisible = Date.now() - navT0;
  ```

### 3.2 Fixed Floor Finding (`networkidle`)
- **Does it wait for `networkidle`?** **YES.** Both login and navigation explicitly invoke `{ waitUntil: 'networkidle' }`.
- **The Fixed Floor Mechanism**: According to Playwright's specification, `'networkidle'` waits until there are **no network connections for at least 500 ms**.
- **Explanation of Discrepancy**:
  - Browser Largest Contentful Paint (LCP) was measured via `PerformanceObserver` at **~250 ms to 410 ms**.
  - `actionToVisible` was measured at **~840 ms to 1,150 ms** on fast pages.
  - The ~500 ms difference represents the **mandatory 500 ms network idle observation window** added by Playwright after the final network byte is received before resolving the promise.
  - Therefore, user-visible visual readiness occurred approximately **500 ms earlier** than the reported `actionToVisible` number.

---

## 4. Supabase Project Region Confirmation & Corrections

### 4.1 Empirical Region Confirmation
- Project Reference: `azulbaxszsqxymqmlwfe`
- Resolved Database Pooler: `aws-0-ap-south-1.pooler.supabase.com:6543`
- Resolved IP Addresses: `65.0.195.55`, `3.111.105.85` (Amazon AWS AP-South-1 CIDR block)
- Physical Location: **AWS Mumbai, India (`ap-south-1`)**
- Measured Network RTT from Bengaluru: **25.06 ms – 29.14 ms** (consistent with domestic fiber; Singapore would be 60–80 ms).

### 4.2 Documentation Corrections Made
1. [`docs/latency-audit/00_INVENTORY.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/00_INVENTORY.md): Corrected line 7, 22, and architecture diagrams from `AWS ap-southeast-1 Singapore` to `AWS ap-south-1 Mumbai`.
2. [`docs/latency-audit/START_LOCAL_STACK.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/START_LOCAL_STACK.md): Corrected lines 4 and 119 from `Singapore AWS ap-southeast-1` to `Mumbai AWS ap-south-1`.
3. Confirmed that earlier references to Singapore in project history belonged strictly to legacy staging `kpwdvvcschankgggitnj` and Vercel edge functions (`sin1`), whereas dev database `azulbaxszsqxymqmlwfe` is in Mumbai.

---

## 5. Silent Direct SQL Fallbacks & Empty State Analysis

### 5.1 Next.js Pages with Silent Direct SQL Fallbacks

The following pages catch Java service failures and fall back to querying PostgreSQL directly via `from '@/lib/db'`:

1. **Student Dashboard (`frontend/src/app/(dashboard)/student/page.tsx`)**:
   - Lines 51–64: Attempts to call Gateway `http://localhost:8080/api/v1/students/me`.
   - Lines 80–84: If Gateway call fails or throws `_gwErr`, silently falls back to line 85:
     ```typescript
     if (!student) {
       const studentRes = await query(`SELECT s.id, p.first_name, ... FROM stu.students s ...`);
     }
     ```
   - Lines 117 & 137: Also executes direct fallback queries on `stu.subject_attendance_summary` and `stu.attendance_sessions`.
2. **College Faculty Page (`frontend/src/app/(dashboard)/college/faculty/page.tsx`)**:
   - Directly executes `await query(...)` on `core.departments` and `hrm.employees` without calling the Gateway or College Admin service.
3. **College Timetable Page (`frontend/src/app/(dashboard)/college/timetable/page.tsx`)**:
   - Directly executes `await query(...)` on `acd.timetables`, `acd.timetable_versions`, and `acd.timetable_entries`.
4. **BFF API Route Handlers (`frontend/src/app/api/...`)**:
   - `api/college/attendance/report/route.ts` -> direct SQL fallback.
   - `api/college/faculty/route.ts` -> direct SQL fallback.
   - `api/student/admissions/documents/route.ts` -> direct SQL fallback.
   - `api/student/[id]/address/route.ts` -> direct SQL fallback.
   - `api/student/[id]/corrections/route.ts` -> direct SQL fallback.
   - `api/student/[id]/history/route.ts` -> direct SQL fallback.

---

### 5.2 Root Cause of `/platform` Anomaly ("Total Users = 0", "No institutes onboarded yet" while cards show 3 institutes)

On `/platform`:
1. `frontend/src/app/(dashboard)/platform/page.tsx` calls Gateway:
   `GET /api/v1/academics/analytics/dashboard?role=PLATFORM_ADMIN`
2. Gateway routes this to Port 8092 (`ACD-10` Analytics Service).
3. In `services/analytics-services/analytics-service/src/main/java/com/campx/academic/analytics/service/AnalyticsDomainService.java`:
   Lines 84–86 instantiate the repository using:
   ```java
   public AnalyticsDomainService() {
       this(new InMemoryAnalyticsDashboardRepository());
   }
   ```
4. In `InMemoryAnalyticsDashboardRepository.java` lines 130–140:
   ```java
   @Override
   public Map<String, Object> getPlatformAdminDashboard(UserSecurityContext context) {
       Map<String, Object> result = new LinkedHashMap<>();
       result.put("tenantCount", 3);
       result.put("institutes", Collections.emptyList());
       Map<String, Integer> statusCounts = new HashMap<>();
       statusCounts.put("ACTIVE", 3);
       result.put("statusCounts", statusCounts);
       result.put("roleTemplateCount", 30);
       return result;
   }
   ```
5. **The Discrepancy Explained**:
   - The mock repository returns `tenantCount: 3` and `statusCounts.ACTIVE: 3`. The top KPI cards render `3`.
   - The mock repository returns `institutes: []` (empty list).
   - In `platform/page.tsx` line 60: `totalUserCount = institutes.reduce(...) = 0`. Thus **"Total Platform Users = 0"**.
   - In `platform-dashboard-view.tsx` line 199: `{institutes.length === 0 ? <p>No institutes onboarded yet.</p> : ...}`. Thus **"No institutes onboarded yet."** renders right below the card showing 3 institutes!

---

## 6. Audit Documentation Corrections Completed

In compliance with instructions:
1. [`EXECUTIVE_SUMMARY.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/EXECUTIVE_SUMMARY.md):
   - Verified flow count is exactly **44 flows** matching the master table.
   - Relabeled the "72.4%" attribution as: `Gateway Downstream Timeouts (Measured with 15 of 18 services not running)`.
   - Corrected dev database region to AWS Mumbai (`ap-south-1`).
2. [`01_LATENCY_BY_MODULE.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/01_LATENCY_BY_MODULE.md):
   - Replaced Student 2 estimate (`~5,450 ms`) with the exact measured p50: **`5,486 ms`**.
   - In Section 3 (Historical Baseline Comparison), removed all comparative verdicts and explicitly relabeled them: **`not comparable (local vs deployed Preview)`** with a prominent explanatory note explaining the infrastructure differences between deployed Vercel functions and local execution over WAN Wi-Fi.

---

## STOP GATE NOTICE

> [!IMPORTANT]
> **PHASE A VERIFICATION COMPLETE**  
> All six verification items have been investigated, evidenced, and documented. No application code has been modified.  
> 
> **Awaiting user approval to proceed**:  
> Reply with `"GO PHASE B"` to apply the approved set of fixes (REC-04, REC-01, REC-02, REC-03 part 1, REC-05).

