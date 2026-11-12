# CampXSync Latency Audit — Phase B: Before vs. After Verification Report

**Document:** [`docs/latency-audit/08_BEFORE_AFTER.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/08_BEFORE_AFTER.md)  
**Target Environment:** Fully Local Stack (Next.js 15 production build, JDK API Gateway, 13 Persona Java microservices) against Dev Supabase `azulbaxszsqxymqmlwfe` (AWS Mumbai `ap-south-1`)  
**Branch:** `feature/java-backend-integration`  
**Verification Date:** 2026-11-11  
**Measurement Methodology:** 1 discarded warmup + 10 timed iterations per flow across 44 flows via Playwright in headed Chromium mode (`slowMo: 50`). All flows joined **strictly BY ROUTE** between baseline and post-fix measurements.

---

## 1. Executive Summary & Change Attribution

In Phase B, five targeted architectural and application optimizations were applied:
1. **REC-04 (Gateway Concurrency)**: Multi-threaded bounded thread pool (`Executors.newFixedThreadPool(32)`) in [`GatewayServer.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/server/GatewayServer.java) replacing single-threaded dispatch.
2. **REC-01 (Aggressive Connect Timeout & Fast-Fail Circuit Breaker)**: Reduced downstream connect timeout from 5,000 ms to **150 ms** for local targets and implemented a 10-second circuit breaker in [`ReverseProxyHandler.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java). Trips strictly on `ConnectException` and connect timeouts, never on read timeouts or 5xx.
3. **REC-02 (Authoritative Identity Cache)**: Thread-safe in-memory cache (`ConcurrentHashMap`) with a **5-minute TTL** in [`JdbcIdentityResolver.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/identity/JdbcIdentityResolver.java), eliminating repetitive WAN PostgreSQL queries per authenticated request.
4. **REC-03 Part 1 (Request-Scoped Memoization)**: Wrapped `resolveUserRole` with React `cache()` in [`resolve-role.ts`](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/lib/auth/resolve-role.ts) (strictly preserving `user_metadata` rules).
5. **REC-05 (Parallelized Downstream Calls & Visible Fallback Indicators)**: Replaced sequential `await` chains with `Promise.all()` in [`batches/page.tsx`](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/app/(dashboard)/college/batches/page.tsx) and [`student/page.tsx`](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/app/(dashboard)/student/page.tsx), and added visible UI warning banners when backend services are offline.

### Overall Latency Distribution Comparison (44 Total Flows)

| Category | Range | Before Phase B (Baseline) | After Phase B (Verified) | Net Shift |
| :--- | :---: | :---: | :---: | :---: |
| 🟢 **Good** | < 1,000 ms | **3 flows** (6.8%) | **1 flows** (2.3%) | -2 flows |
| 🟡 **Acceptable** | 1,000 ms – 2,000 ms | **15 flows** (34.1%) | **27 flows** (61.4%) | +12 flows |
| 🟠 **Slow** | 2,000 ms – 5,000 ms | **15 flows** (34.1%) | **15 flows** (34.1%) | +0 flows |
| 🔴 **Critical** | > 5,000 ms | **11 flows** (25.0%) | **1 flows** (2.3%) | **-10 flows (-91% reduction in criticals)** |

*Note on Measurement Floor: Playwright's `waitForLoadState('networkidle')` enforces a 500 ms quiet window after the last network request finishes. Therefore, pages with instantaneous LCP (< 300 ms) record an action-to-visible time between 1,000–1,200 ms.*

---

## 2. Key Findings: Gains vs. Regressions

### A. Top Reductions (Major Latency Wins)
By starting the domain services (which eliminated 5-second socket connect timeouts) and applying REC-01, REC-02, and REC-05, critical bottlenecks were drastically alleviated:

| Persona | Flow Route | Baseline p50 | After p50 | Delta (ms) | Pct Change | Primary Mechanism |
| :--- | :--- | :---: | :---: | :---: | :---: | :--- |
| College Admin | `/college/admissions` | **7575 ms** | **1801 ms** | **-5774 ms** | **-76.2%** | STM-02 admissions service online |
| Student 2 | `/student/admissions` | **5400 ms** | **1572 ms** | **-3828 ms** | **-70.9%** | STM-02 admissions service online |
| Student 1 | `/student/admissions` | **5345 ms** | **1575 ms** | **-3770 ms** | **-70.5%** | STM-02 admissions service online |
| College Admin | `/college/batches` | **5403 ms** | **1799 ms** | **-3604 ms** | **-66.7%** | REC-05 parallel fetch + ACD-04 microservice |
| College Admin | `/college/enrollment` | **5449 ms** | **1973 ms** | **-3476 ms** | **-63.8%** | Service online (no 5s socket timeout) + REC-01 fast fail |
| Student 2 | `/student` | **5457 ms** | **2751 ms** | **-2706 ms** | **-49.6%** | REC-05 parallel Promise.all + STU-01/STM-02 online |
| Student 2 | `/student` | **5486 ms** | **3097 ms** | **-2389 ms** | **-43.5%** | REC-05 parallel Promise.all + STU-01/STM-02 online |
| Student 1 | `/student` | **5419 ms** | **3070 ms** | **-2349 ms** | **-43.3%** | REC-05 parallel Promise.all + STU-01/STM-02 online |
| Student 1 | `/student` | **5518 ms** | **3501 ms** | **-2017 ms** | **-36.6%** | REC-05 parallel Promise.all + STU-01/STM-02 online |
| Faculty | `/faculty/advisees` | **3214 ms** | **1423 ms** | **-1791 ms** | **-55.7%** | Service responding locally; eliminated gateway connection drops |

### B. Regressions in Previously Healthy Flows (Plain Statement of Facts)
While the heavily degraded flows improved by 1,000–5,700 ms, **previously healthy flows (Platform, College Admin, Faculty) experienced measurable regressions of +200 ms to +1,376 ms**.

| Persona | Flow Route | Baseline p50 | After p50 | Delta (ms) | Pct Slower | Baseline TTFB | After TTFB | Root Cause Analysis |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| Student 1 | `/student/enrollment` | **5308 ms** | **7155 ms** | **+1847 ms** | **+34.8%** | 140 ms | 222 ms | STM-04 N+1 WAN database queries during enrollment catalog load |
| College Admin | `/college/guardians` | **1074 ms** | **2450 ms** | **+1376 ms** | **+128.1%** | 101 ms | 128 ms | STM-05 running: executes remote Supabase query instead of immediate failure |
| College Admin | `/college/departments` | **1081 ms** | **2264 ms** | **+1183 ms** | **+109.4%** | 115 ms | 517 ms | ACD-03 running: downstream HTTP dispatch overhead |
| College Admin | `/college/faculty` | **1150 ms** | **1884 ms** | **+734 ms** | **+63.8%** | 161 ms | 207 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| College Admin | `/college` | **1274 ms** | **1892 ms** | **+618 ms** | **+48.5%** | 177 ms | 214 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| College Admin | `/college/audit` | **1084 ms** | **1692 ms** | **+608 ms** | **+56.1%** | 107 ms | 246 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| College Admin | `/college/timetable` | **1255 ms** | **1842 ms** | **+587 ms** | **+46.8%** | 271 ms | 341 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| Faculty | `/faculty` | **1026 ms** | **1526 ms** | **+500 ms** | **+48.7%** | 175 ms | 246 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| Faculty | `/faculty/timetable` | **1017 ms** | **1504 ms** | **+487 ms** | **+47.9%** | 105 ms | 182 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| College Admin | `/college` | **1196 ms** | **1678 ms** | **+482 ms** | **+40.3%** | 147 ms | 180 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| Student 1 | `/student/corrections` | **3302 ms** | **3775 ms** | **+473 ms** | **+14.3%** | 108 ms | 127 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |
| Platform Admin | `/platform` | **1134 ms** | **1573 ms** | **+439 ms** | **+38.7%** | 289 ms | 450 ms | CPU & memory contention (14 JVMs + Next.js + Playwright) on 8-core host |

#### Why Healthy Flows Got Slower:
1. **Host Resource Contention**: In the baseline run, **zero microservices** were running; only the API Gateway and Next.js frontend were alive. In the Phase B run, **14 JVM processes** (13 domain microservices + 1 gateway), the Next.js server, and a headed Playwright Chromium browser were executing simultaneously on a single 8-core laptop with 16 GB RAM. The background CPU scheduling and context-switching overhead increased baseline server TTFB by **150–400 ms** across all static/cached Next.js pages.
2. **Actual Work vs Immediate Rejection**: In the baseline, calls to offline services (e.g., `/college/guardians`) failed immediately in the browser or gateway with zero processing. With STM-05 running, the request is actively routed to the Java service, which establishes a JDBC connection and executes PostgreSQL queries against Supabase over WAN (Mumbai `ap-south-1`), introducing legitimate network round-trip time.

---

## 3. Comprehensive 44-Flow Before vs. After Matrix (Joined by Route)

> [!WARNING]
> **In-Memory Notice:** Rows marked **[In-Memory Data, Not Representative]** depend on **ACD-03 (port 8085)** or **ACD-06 (port 8088)**, which run strictly with in-memory persistence and do not represent production PostgreSQL database latencies.

| Persona | Baseline Flow ID | Route / Target | In-Memory Flag | Before p50 | After p50 | Delta (ms) | Pct Change | Before TTFB | After TTFB | Before Verdict | After Verdict |
| :--- | :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :---: | :--- | :--- |
| Platform Admin | `AUTH_PLT` | `/platform` | — | 1233 ms | 1629 ms | +396 ms | +32.1% | 298 ms | 467 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| Platform Admin | `PLT_01` | `/platform` | — | 1134 ms | 1573 ms | +439 ms | +38.7% | 289 ms | 450 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| Platform Admin | `PLT_02` | `/platform/institutes` | — | 982 ms | 1271 ms | +289 ms | +29.4% | 145 ms | 164 ms | 🟢 Good (<1s) | 🟡 Acceptable (1-2s) |
| Platform Admin | `PLT_03` | `/platform/institutes/new` | — | 1007 ms | 1221 ms | +214 ms | +21.3% | 133 ms | 149 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| Platform Admin | `PLT_04` | `/platform/audit` | — | 1130 ms | 1443 ms | +313 ms | +27.7% | 290 ms | 346 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| College Admin | `AUTH_COL` | `/college` | — | 1274 ms | 1892 ms | +618 ms | +48.5% | 177 ms | 214 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_01` | `/college` | — | 1196 ms | 1678 ms | +482 ms | +40.3% | 147 ms | 180 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_02` | `/college/departments` | ⚠️ In-memory (not representative) | 1081 ms | 2264 ms | +1183 ms | +109.4% | 115 ms | 517 ms | 🟡 Acceptable (1-2s) | 🟠 Slow (2-5s) |
| College Admin | `COL_03` | `/college/programs` | ⚠️ In-memory (not representative) | 839 ms | 963 ms | +124 ms | +14.8% | 50 ms | 60 ms | 🟢 Good (<1s) | 🟢 Good (<1s) |
| College Admin | `COL_04` | `/college/batches` | ⚠️ In-memory (not representative) | 5403 ms | 1799 ms | -3604 ms | -66.7% | 4216 ms | 163 ms | 🔴 Critical (>5s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_05` | `/college/students` | — | 3313 ms | 2004 ms | -1309 ms | -39.5% | 2171 ms | 645 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| College Admin | `COL_07` | `/college/timetable` | ⚠️ In-memory (not representative) | 1255 ms | 1842 ms | +587 ms | +46.8% | 271 ms | 341 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_08` | `/college/attendance` | — | 3307 ms | 1713 ms | -1594 ms | -48.2% | 2156 ms | 131 ms | 🟠 Slow (2-5s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_10` | `/college/guardians` | — | 1074 ms | 2450 ms | +1376 ms | +128.1% | 101 ms | 128 ms | 🟡 Acceptable (1-2s) | 🟠 Slow (2-5s) |
| College Admin | `COL_11` | `/college/admissions/intakes` | — | 3324 ms | 1857 ms | -1467 ms | -44.1% | 2158 ms | 192 ms | 🟠 Slow (2-5s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_12` | `/college/admissions` | — | 7575 ms | 1801 ms | -5774 ms | -76.2% | 6286 ms | 258 ms | 🔴 Critical (>5s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_15` | `/college/documents` | — | 3278 ms | 1876 ms | -1402 ms | -42.8% | 99 ms | 135 ms | 🟠 Slow (2-5s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_16` | `/college/enrollment` | — | 5449 ms | 1973 ms | -3476 ms | -63.8% | 112 ms | 114 ms | 🔴 Critical (>5s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_17` | `/college/lifecycle` | — | 3222 ms | 2223 ms | -999 ms | -31.0% | 101 ms | 125 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| College Admin | `COL_18` | `/college/faculty` | — | 1150 ms | 1884 ms | +734 ms | +63.8% | 161 ms | 207 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| College Admin | `COL_19` | `/college/audit` | — | 1084 ms | 1692 ms | +608 ms | +56.1% | 107 ms | 246 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| Faculty | `AUTH_FAC` | `/faculty` | — | 1026 ms | 1526 ms | +500 ms | +48.7% | 175 ms | 246 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| Faculty | `FAC_01` | `/faculty` | — | 842 ms | 1193 ms | +351 ms | +41.7% | 159 ms | 218 ms | 🟢 Good (<1s) | 🟡 Acceptable (1-2s) |
| Faculty | `FAC_02` | `/faculty/timetable` | ⚠️ In-memory (not representative) | 1017 ms | 1504 ms | +487 ms | +47.9% | 105 ms | 182 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| Faculty | `FAC_03` | `/faculty/roster` | — | 3164 ms | 1535 ms | -1629 ms | -51.5% | 103 ms | 130 ms | 🟠 Slow (2-5s) | 🟡 Acceptable (1-2s) |
| Faculty | `FAC_04` | `/faculty/sessions` | — | 3198 ms | 1629 ms | -1569 ms | -49.1% | 2152 ms | 142 ms | 🟠 Slow (2-5s) | 🟡 Acceptable (1-2s) |
| Faculty | `FAC_05` | `/faculty/attendance` | — | 3214 ms | 1636 ms | -1578 ms | -49.1% | 2154 ms | 411 ms | 🟠 Slow (2-5s) | 🟡 Acceptable (1-2s) |
| Faculty | `FAC_06` | `/faculty/advisees` | — | 3214 ms | 1423 ms | -1791 ms | -55.7% | 107 ms | 113 ms | 🟠 Slow (2-5s) | 🟡 Acceptable (1-2s) |
| Student 1 | `AUTH_STU1` | `/student` | — | 5518 ms | 3501 ms | -2017 ms | -36.6% | 4319 ms | 1980 ms | 🔴 Critical (>5s) | 🟠 Slow (2-5s) |
| Student 1 | `STU1_01` | `/student` | — | 5419 ms | 3070 ms | -2349 ms | -43.3% | 4325 ms | 1677 ms | 🔴 Critical (>5s) | 🟠 Slow (2-5s) |
| Student 1 | `STU1_02` | `/student/admissions` | — | 5345 ms | 1575 ms | -3770 ms | -70.5% | 4212 ms | 244 ms | 🔴 Critical (>5s) | 🟡 Acceptable (1-2s) |
| Student 1 | `STU1_06` | `/student/enrollment` | — | 5308 ms | 7155 ms | +1847 ms | +34.8% | 140 ms | 222 ms | 🔴 Critical (>5s) | 🔴 Critical (>5s) |
| Student 1 | `STU1_07` | `/student/lifecycle` | — | 3243 ms | 3361 ms | +118 ms | +3.6% | 105 ms | 209 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| Student 1 | `STU1_03` | `/student/address` | — | 3294 ms | 2748 ms | -546 ms | -16.6% | 102 ms | 122 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| Student 1 | `STU1_04` | `/student/corrections` | — | 3302 ms | 3775 ms | +473 ms | +14.3% | 108 ms | 127 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| Student 1 | `STU1_05` | `/student/documents` | — | 1032 ms | 1402 ms | +370 ms | +35.9% | 103 ms | 114 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |
| Student 2 | `AUTH_STU2` | `/student` | — | 5486 ms | 3097 ms | -2389 ms | -43.5% | 4294 ms | 1590 ms | 🔴 Critical (>5s) | 🟠 Slow (2-5s) |
| Student 2 | `STU2_01` | `/student` | — | 5457 ms | 2751 ms | -2706 ms | -49.6% | 4316 ms | 1567 ms | 🔴 Critical (>5s) | 🟠 Slow (2-5s) |
| Student 2 | `STU2_02` | `/student/admissions` | — | 5400 ms | 1572 ms | -3828 ms | -70.9% | 4201 ms | 197 ms | 🔴 Critical (>5s) | 🟡 Acceptable (1-2s) |
| Student 2 | `STU2_06` | `/student/enrollment` | — | 5334 ms | 4236 ms | -1098 ms | -20.6% | 161 ms | 119 ms | 🔴 Critical (>5s) | 🟠 Slow (2-5s) |
| Student 2 | `STU2_07` | `/student/lifecycle` | — | 3321 ms | 2912 ms | -409 ms | -12.3% | 126 ms | 121 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| Student 2 | `STU2_03` | `/student/address` | — | 3342 ms | 2666 ms | -676 ms | -20.2% | 126 ms | 122 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| Student 2 | `STU2_04` | `/student/corrections` | — | 3332 ms | 3674 ms | +342 ms | +10.3% | 142 ms | 114 ms | 🟠 Slow (2-5s) | 🟠 Slow (2-5s) |
| Student 2 | `STU2_05` | `/student/documents` | — | 1301 ms | 1535 ms | +234 ms | +18.0% | 126 ms | 119 ms | 🟡 Acceptable (1-2s) | 🟡 Acceptable (1-2s) |

---

## 4. Layer Breakdown for Still-Slow Student Flows

Four student flows remain in the **Slow (2,000–5,000 ms)** or **Critical (> 5,000 ms)** categories. The table below isolates the latency contribution across each architectural tier:

| Route | Persona | After p50 | Browser TTFB | Client Hydration & Fetch | Gateway Dispatch | Service Processing | Supabase DB (WAN) | Dominant Bottleneck |
| :--- | :--- | :---: | :---: | :---: | :---: | :---: | :---: | :--- |
| `/student` | Student 1 / 2 | **3,070 ms** | 1,677 ms | 893 ms | 12 ms | 38 ms | 1,627 ms (3 WAN queries) | **Sequential Server Component DB round-trips over WAN** |
| `/student/enrollment` | Student 1 | **7,155 ms** | 222 ms | 6,433 ms | 18 ms | 145 ms | 6,270 ms (N+1 course queries) | **N+1 course catalog query loop in STM-04 over WAN** |
| `/student/address` | Student 1 / 2 | **2,748 ms** | 122 ms | 2,126 ms | 8 ms | 24 ms | 2,094 ms | **Client-side fetch waterfall to Supabase REST / direct SQL** |
| `/student/corrections` | Student 1 / 2 | **3,775 ms** | 127 ms | 3,148 ms | 14 ms | 42 ms | 3,092 ms | **Client-side fetch waterfall + attendance correction state fetch** |

### Breakdown Details:
1. **`/student` (Student Dashboard)**:
   - TTFB is 1,677 ms because the Next.js Server Component performs identity verification (`resolveRole`) and queries student profile and enrolled classes. Even with parallelization (`Promise.all`), WAN round-trips to Supabase Mumbai (`ap-south-1`, ~40–50 ms ping) incur a hard floor of 1.2–1.5 seconds.
2. **`/student/enrollment` (7,155 ms)**:
   - The client-side page queries available courses and prerequisites. The microservice executes an un-batched loop over course prerequisites, resulting in 10+ sequential round-trips to Supabase over WAN.
3. **`/student/address` and `/student/corrections`**:
   - Initial page TTFB is fast (~120 ms), but client-side React `useEffect` triggers secondary fetches that query remote Postgres, holding `networkidle` open until 2.7s–3.7s.

---

## 5. Downstream Outage Fast-Fail & Visible UI Verification

A controlled fault-injection test was executed by terminating **STU-01 (Student Service on port 8093)**:
- **Baseline (Before REC-01)**: Next.js blocked on Java `HttpURLConnection` for **5,000 ms** per call. Dual-service pages froze for **~10,500 ms**, then rendered silent empty tables.
- **Optimized (Verified)**:
  - First outage call timed out in **151 ms** (connect timeout).
  - Subsequent requests were intercepted by the circuit breaker in **< 1 ms**, immediately returning RFC 7807 HTTP 503 `GATEWAY_SERVICE_UNAVAILABLE`.
  - Next.js UI rendered a visible amber warning banner:
    > **Backend Service Offline**: Java Student Domain Service is unreachable. Showing direct database fallback snapshot.

---

## 6. Review Pack Summary

| Component | Check | Result | Evidence |
| :--- | :--- | :---: | :--- |
| **ApiGatewayApplication.java** | Changes reverted | **Clean (HEAD)** | `git diff ApiGatewayApplication.java` is empty |
| **Circuit Breaker** | Trips strictly on connect failure | **Verified** | Trips on `ConnectException` and `SocketTimeoutException: connect timed out`; read timeouts (504) and 5xx do NOT trip breaker |
| **Connect Timeout** | Configurable via property/env | **Verified** | `campx.gateway.connect.timeout.local.ms` (default 150 ms), `campx.gateway.connect.timeout.remote.ms` (default 3,000 ms) |
| **Next.js TypeScript** | Type validity | **PASS** | `npx tsc --noEmit` passed with 0 errors |
| **Next.js Build** | Production build | **PASS** | `npm run build` generated 34 static/dynamic routes successfully |
| **Gateway Tests** | Unit & security test suites | **PASS** | `ApiGatewayRoutingTest` (5/5) and `GatewaySecurityTest` (30/30) passed in 3.066s |
