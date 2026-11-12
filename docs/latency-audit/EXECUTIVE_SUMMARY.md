# CampXSync Latency Audit — Executive Summary

**Date**: November 11, 2026  
**Auditor**: Senior Performance Engineering Team  
**Scope**: Full Stack Performance Audit (Next.js 15, Java API Gateway, Java Microservices, Supabase PostgreSQL)  
**Database**: Dev Supabase Project `azulbaxszsqxymqmlwfe` (AWS Mumbai, `aws-0-ap-south-1.pooler.supabase.com:6543`)  
**Commit SHA**: `ef6e7dbed9dfde7cd492c5b7768542ce958d3a62` (`feature/java-backend-integration`)  

---

## 1. Headline Metrics

Across 44 distinct transactional and analytical flows systematically audited using headed, visible browser instrumentation against a local production build (`npm run start`):

- **Total Flows Benchmarked**: 44 flows across 5 distinct personas (Platform Admin, College Admin, Faculty, Student 1, Student 2).
- **Sampling Rigor**: 1 discarded warmup + 10 timed iterations per flow (>480 total automated browser transactions).
- **Latency Distribution**:
  - **Good (500 – 1,000 ms)**: 3 flows (6.8%) — Academic Programs (`839 ms`), Faculty Dashboard (`842 ms`), Platform Institutes Registry (`982 ms`).
  - **Acceptable (1,000 – 2,000 ms)**: 15 flows (34.1%) — Platform Onboard/Audit, College Dashboard/Departments/Timetable/Guardians/Faculty/Audit, Faculty Timetable, Student Documents (`1,007 – 1,301 ms`).
  - **Slow (2,000 – 5,000 ms)**: 15 flows (34.1%) — Student Roster, Attendance Oversight, Admissions Intakes, Document Types, Lifecycle Operations, Faculty Roster/Sessions/Attendance/Advisees, Student Profile/Corrections/Lifecycle (`3,164 – 3,342 ms`).
  - **Critical (>5,000 ms)**: 11 flows (25.0%) — Academic Batches, Admissions Applications, Enrollment Management, Student 1/2 Logins, Student 1/2 Dashboards, Student 1/2 Admissions, Student 1/2 Course Enrollment (`5,308 – 7,575 ms`).
- **Cold JVM Start Penalty**: First requests to newly booted Java microservices experience a **~2,370 ms** latency tax due to HikariCP pool establishment across the WAN, Metaspace classloading, and initial HotSpot JIT interpretation.
- **Database Compute vs. Network RTT**: Direct PostgreSQL query compute is **under 4 ms** for even complex 5-table joins (`0.08 ms` for indexed lookups). Network packet transit over the WAN to AWS Mumbai (`26.5 ms` RTT) accounts for **85% to 99%** of database interaction time.

---

## 2. Where the Time Goes: Layer Attribution Summary

*(Note: Measured with 15 of 18 microservices not running during the baseline audit run)*

```
[████████████████████████████████████████] 72.4%  Gateway Downstream Timeouts (Measured with 15 of 18 services not running)
[████████]                                 14.2%  Browser Paint, Hydration & Skeletons
[████]                                      8.1%  WAN Network RTT to Supabase (Auth + DB)
[██]                                        4.1%  Next.js SSR & Server Component Logic
[█]                                         1.0%  Gateway Internal (JWT & JDBC Identity)
[]                                          0.2%  PostgreSQL Database Execution
```

**Key Takeaway**: The database is fast. Frontend bundles are lean (zero external Google fonts, zero tracking pixels). In the baseline audit state, over 72% of all user-visible delay was caused by HTTP connection timeouts in the API Gateway trying to connect to microservices that were offline or not listening on their expected ports.

---

## 3. Top 5 Root Causes (Empirically Proven)

1. **RC-1: Gateway Downstream Connection Timeouts on Unbound Microservices (Hypothesis H13)**
   - When Next.js requests data via Gateway for domains where microservices are offline (e.g. STU-01 on 8093, STM-02 on 8094, ACD-03 on 8085, ACD-04 on 8086), `ReverseProxyHandler` blocks for ~2.5 seconds per connection attempt.
   - For pages issuing 2 or 3 calls, timeouts stack linearly: 1 timeout = ~3.3s; 2 timeouts = ~5.4s; 3 timeouts = **7.575s** (`COL_12` Admissions Applications).
2. **RC-2: Serialized Request Dispatching in Sun HttpServer (Hypothesis H11)**
   - API Gateway runs on pure JDK `HttpServer`. Concurrent `Promise.all` requests issued by Next.js are dispatched sequentially. One blocking downstream connection halts subsequent requests in the queue, creating a 7.5-second total delay.
3. **RC-3: Redundant Triple Role Resolution & Uncached JDBC Identity (Hypothesis H4)**
   - Every single navigation executes role verification 4 times: Next.js `middleware.ts` (Supabase Auth HTTPS), Next.js `layout.tsx` (DB lookup), Next.js `page.tsx` (DB lookup), and Gateway `JdbcIdentityResolver` (4 sequential SQL queries).
   - Because `JdbcIdentityResolver` has **zero in-memory caching**, every API request incurs 4 WAN round trips (~105 ms) just to verify identity.
4. **RC-4: Post-Hydration Client Fetch Waterfalls (Hypothesis H10)**
   - Client pages (`college/documents`, `college/enrollment`, `college/lifecycle`) render fast shells (TTFB ~100 ms) and then fire `useEffect` fetches in the browser. Users stare at loading spinners for 3.2 to 5.4 seconds while background fetches complete.
5. **RC-5: WAN Network RTT Dominating Sequential Database Queries (Hypothesis H1 & H3)**
   - Sequential `await` statements in Next.js Server Components turn each 26.5 ms WAN round trip into cumulative delay. 8 sequential queries add ~212 ms of pure network waiting.

---

## 4. Top 5 Ranked Quick Wins (Highest ROI)

| Rank | Fix Proposal | Layer | Action Required | Latency Saved | Effort |
|:---:|:---|:---|:---|:---:|:---:|
| **1** | **Gateway Connect Timeout & Circuit Breaker** | API Gateway | Reduce `connectTimeout` from 5,000 ms to 150 ms for localhost; trip open circuit on `ConnectException` | **2,500 – 6,300 ms** on slow flows | 1–2 hours |
| **2** | **Gateway Multi-Threaded Executor Pool** | API Gateway | Configure `server.setExecutor(Executors.newFixedThreadPool(32))` in `GatewayServer.java` | **2,000 – 5,000 ms** under concurrency | 30 minutes |
| **3** | **Gateway Identity Resolution In-Memory Cache** | API Gateway | Add 5-minute `ConcurrentHashMap` cache in `JdbcIdentityResolver.java` | **~105 ms** on every API call | 1 hour |
| **4** | **Next.js Role Memoization & JWT Claims** | Frontend BFF | Wrap `resolveUserRole` with React `cache()` and embed role claims in JWT | **~200 ms** on every navigation | 2 hours |
| **5** | **Parallelize Server Component Lookups** | Frontend Pages | Replace sequential `await` queries with `Promise.all()` in batches and student pages | **150 – 300 ms** per page | 2 hours |

---

## 5. Audit Deliverables Catalog

All comprehensive audit artifacts and trace data have been created under `docs/latency-audit/`:
1. [`00_INVENTORY.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/00_INVENTORY.md): Complete catalog of 44 flows, routes, BFF handlers, and DB tables.
2. [`01_LATENCY_BY_MODULE.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/01_LATENCY_BY_MODULE.md): Master statistical tables across all 44 flows and personas.
3. [`02_LAYER_BREAKDOWN.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/02_LAYER_BREAKDOWN.md): Microsecond layer attribution and ASCII waterfalls for top 10 slowest flows.
4. [`03_ROOT_CAUSES.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/03_ROOT_CAUSES.md): Deep-dive empirical evaluation of hypotheses H1 through H16.
5. [`04_RECOMMENDATIONS.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/04_RECOMMENDATIONS.md): Ranked fix proposals and Phase B status.
6. [`05_MEASUREMENT_GAPS.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/05_MEASUREMENT_GAPS.md): Inventory of unmeasured flows and production variance analysis.
7. [`06_RAW_DATA_INDEX.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/06_RAW_DATA_INDEX.md): Index of raw JSON trace files and zero-write attestation.
8. [`07_VERIFICATION.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/07_VERIFICATION.md): Phase A verification findings, listening ports, and memory analysis.
9. [`08_BEFORE_AFTER.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/08_BEFORE_AFTER.md): Full 44-flow before vs after benchmark verification joined strictly by route.
10. [`09_AUTH_PATH_DESIGN.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/09_AUTH_PATH_DESIGN.md): Authoritative architectural blueprint for Gateway/Supabase JWT authentication.
11. [`START_LOCAL_STACK.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/START_LOCAL_STACK.md): Full local stack start/stop procedures and health matrix.

---

## 6. Phase B Post-Implementation Outcomes & Attribution

Following authorized implementation of REC-01, REC-02, REC-03 Part 1, REC-04, and REC-05, all 44 flows were re-benchmarked under identical conditions (joined strictly by route):

| Distribution Category | Baseline (Phase A) | Post-Fix (Phase B) | Net Impact |
|:---|:---:|:---:|:---:|
| 🟢 **Good (< 1,000 ms)** | 3 flows (6.8%) | 1 flow (2.3%) | -2 flows |
| 🟡 **Acceptable (1,000 – 2,000 ms)** | 15 flows (34.1%) | 27 flows (61.4%) | **+12 flows (+80% shift)** |
| 🟠 **Slow (2,000 – 5,000 ms)** | 15 flows (34.1%) | 15 flows (34.1%) | 0 flows |
| 🔴 **Critical (> 5,000 ms)** | 11 flows (25.0%) | 1 flow (2.3%) | **-10 flows (-91% reduction)** |

### Latency Gain Attribution
1. **Starting the Domain Services**: Contributed **75% – 85%** of the raw latency reduction on critical flows by replacing multi-second TCP SYN retransmission drops with live HTTP 200 responses.
2. **The 5 Code Optimizations**: Contributed **15% – 25%** of the steady-state latency reduction via thread concurrency (REC-04), 0 ms cached identity resolution (REC-02), React memoization (REC-03), and query parallelization (REC-05). During outages, **100%** of fail-fast protection (151 ms vs 5,000 ms) and circuit breaker (< 1 ms) is provided by REC-01.

### Regression Attribution
Previously healthy flows (Platform Admin, College Admin, Faculty) experienced regressions of **+200 ms to +1,376 ms**. This was caused by:
- **Host Resource Pressure**: Running 14 concurrent JVMs + Next.js SSR + Playwright browser on a 16 GB laptop left only ~1.87–2.38 GB of free RAM, introducing CPU scheduling and page fault delays (+150–400 ms TTFB floor).
- **Live WAN Persistence**: Services now execute real JDBC queries to Supabase Mumbai over WAN (26.5 ms RTT) instead of failing immediately.


