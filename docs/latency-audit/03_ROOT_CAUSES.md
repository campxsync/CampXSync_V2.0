# CampXSync Latency Audit — Root Causes Analysis

**Audit Version**: 2.0.0  
**Audit Date**: November 11, 2026  
**Target Environment**: Fully Local Stack (Next.js 15, Java API Gateway, Java Microservices)  
**Database**: Dev Supabase Project `azulbaxszsqxymqmlwfe` (AWS Mumbai, `aws-0-ap-south-1.pooler.supabase.com:6543`)  
**Commit SHA**: `ef6e7dbed9dfde7cd492c5b7768542ce958d3a62` (`feature/java-backend-integration`)  

---

## Executive Summary of Findings

Across 44 distinct transactional and analytical flows audited across 5 personas, measured action-to-visible latencies fall into four distinct tiers:

1. **Sub-Second to Acceptable (800 – 1,250 ms)**: Flows touching running microservices (ADM-01 on 8081, ADM-02 on 8082, ACD-10 on 8092) or performing direct, non-waterfall database queries.
2. **Delayed Tier (~3,200 – 3,350 ms)**: Flows making exactly **one downstream Gateway request** to an offline or delayed microservice, incurring a Gateway connection timeout before falling back to database queries.
3. **Critical Tier (~5,400 – 5,750 ms)**: Flows making **two downstream Gateway requests** sequentially, incurring two stacked connection timeouts (~2 × 2.5 s) before completing.
4. **Severe Degraded Tier (~7,575 ms)**: Flows making **three downstream Gateway requests** concurrently in the UI (`Promise.all`), which serialize in the single-threaded Gateway dispatcher and stack three connection timeouts (~3 × 2.5 s).

The root cause investigation rigorously evaluated sixteen specific hypotheses (H1 through H16) with concrete empirical evidence, code references, and network traces.

---

## Hypothesis Evaluation Matrix

| Hypothesis | Description | Audit Finding | Confidence | Primary Attribution Layer | Latency Impact |
|:---|:---|:---:|:---:|:---|:---:|
| **H1** | Network RTT Dominates DB Execution | **CONFIRMED** | High | Network (WAN to Supabase) | 26 ms per query round trip |
| **H2** | Cold JVM / JIT Warmup Overhead | **CONFIRMED** | High | Java Service / JVM Runtime | +2,300 ms on first request |
| **H3** | Sequential Waterfalls in BFF/Server Components | **CONFIRMED** | High | Next.js Server Components | 100–300 ms serialized wait |
| **H4** | Triple Role Resolution & Uncached Gateway Identity | **CONFIRMED** | High | Next.js + Gateway Middleware | ~350–385 ms per navigation |
| **H5** | Supabase Auth HTTP Round Trips in Middleware | **CONFIRMED** | High | Next.js Edge Middleware | 50–90 ms per navigation |
| **H6** | Gateway JWKS Fetched Per Request | **REJECTED** | High | API Gateway Security | 0 ms (cached 10 min TTL) |
| **H7** | DB Connection Overhead & Session Variables | **CONFIRMED** | Medium | HikariCP / pg.Pool | 25–50 ms per checkout |
| **H8** | Slow SQL Queries & Missing Indexes | **PARTIAL** | Medium | Supabase / PostgreSQL | <5 ms dev; high risk at scale |
| **H9** | Over-fetching in SELECT Statements | **CONFIRMED** | Medium | Next.js / Java Services | 50–150 ms payload/serialization |
| **H10** | Dynamic Rendering & Post-Hydration Fetch Waterfalls | **CONFIRMED** | High | Next.js Client Components | 3,000–5,000 ms UI delay |
| **H11** | Single-Threaded Java HttpServer Serializing Requests | **CONFIRMED** | High | API Gateway Architecture | Stacks concurrent timeouts |
| **H12** | Synchronous Audit/Intelligence Delivery Overhead | **REJECTED** | High | Intelligence Integration Layer | 0 ms (asynchronous queue) |
| **H13** | Downstream Unreachable Retries & Long Timeouts | **CONFIRMED** | High | API Gateway Reverse Proxy | **+2,500 ms per missing service** |
| **H14** | Documents Binary Proxying vs Direct Storage | **CONFIRMED** | Medium | Storage / BFF Architecture | Architecture defect |
| **H15** | Third-Party Fonts & Unoptimized External Assets | **REJECTED** | High | Frontend Assets | 0 ms (zero external fonts/tags) |
| **H16** | Local Machine Resource Constraints | **CONFIRMED** | Medium | Host Environment | Host CPU/RAM starvation |

---

## Detailed Root Cause Analyses

### H1: Network RTT Dominates Database Execution Time (CONFIRMED)

- **Empirical Measurement**:
  - Measured TCP connection RTT to `aws-0-ap-south-1.pooler.supabase.com:6543`:
    - First TCP Handshake + TLS: `91.13 ms`
    - Warm TCP Handshake: `25.06 ms – 29.14 ms` (median: `26.5 ms`)
  - Direct PostgreSQL query execution time measured via `EXPLAIN (ANALYZE, BUFFERS)`:
    - Primary key index scan (`plat.platform_admins`): `0.078 ms` execution (`0.620 ms` planning)
    - 5-table joined query (`stu.students` + `persons` + `colleges` + `programs`): `3.941 ms` execution (`8.549 ms` planning)
- **Ratio**:
  - Query compute time inside PostgreSQL represents **0.3% to 15%** of the round trip.
  - Network packet transit over the WAN represents **85% to 99.7%** of the total database latency.
- **Impact**: Any architecture that issues multiple sequential database queries across the WAN incurs a mandatory 26.5 ms network tax per query, regardless of how fast PostgreSQL executes.

---

### H2: Cold JVM / JIT Warmup Overhead (CONFIRMED)

- **Empirical Measurement**:
  - Cold start of Platform Admin login (`AUTH_PLT`): `3,604 ms` action-to-visible.
  - Warm steady-state of Platform Admin login (`AUTH_PLT`): `1,233 ms` (p50).
  - Cold start penalty: **~2,371 ms**.
- **Mechanisms**:
  1. **HikariCP Connection Pool Initialization**: When the JVM starts, HikariCP lazily creates initial database connections upon receiving the first request. The initial connection requires DNS resolution, TCP handshake to AWS Mumbai, and TLS negotiation, consuming ~200–300 ms.
  2. **Classloading & Dynamic Bytecode Verification**: Sun `HttpServer`, Jackson ObjectMapper, JDBC driver classes, and Java domain models are loaded into Metaspace during the first transaction.
  3. **HotSpot C1/C2 JIT Compilation**: Methods are initially interpreted by the HotSpot interpreter; tiered compilation compiles methods to native code only after several hundred invocations.

---

### H3: Sequential Waterfalls in Next.js Server Components & BFF (CONFIRMED)

- **Code Evidence**:
  - `frontend/src/app/(dashboard)/college/batches/page.tsx`:
    ```typescript
    // Sequential awaiting of dependent lookups
    const batches = await fetchBatches(...);
    const subjects = await fetchSubjects(...);
    ```
  - `frontend/src/app/(dashboard)/student/page.tsx`:
    ```typescript
    // Line 61: Awaits student profile via Gateway
    const gwRes = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/me`, ...);
    // Line 85: If profile missing, awaits SQL student query
    const studentRes = await query(...);
    // Line 125: Awaits attendance summary SQL query
    const attendanceRes = await query(...);
    // Line 155: Awaits recent sessions SQL query
    const sessionsRes = await query(...);
    // Line 175: Awaits applications via Gateway
    const apps = await fetchApplications(...);
    ```
- **Impact**: Instead of firing independent requests concurrently, sequential `await` statements turn $N$ remote operations of $L$ latency into $N \times L$ latency. In the student dashboard, this adds 350–500 ms of serialized waiting.

---

### H4: Triple Role Resolution & Uncached Gateway JDBC Identity (CONFIRMED)

- **Architecture Traversal**:
  Every authenticated user navigation traverses three independent layers, each performing its own role/identity check against the remote database:
  1. **Next.js Middleware (`frontend/src/middleware.ts`)**:
     Calls `supabase.auth.getUser()`, executing an HTTPS round trip to `https://azulbaxszsqxymqmlwfe.supabase.co/auth/v1/user` (~50–80 ms).
  2. **Next.js Dashboard Layout (`frontend/src/app/(dashboard)/layout.tsx`)**:
     Calls `resolveUserRole(supabase)`, which queries `plat.platform_admins`, `iam.user_profiles`, and `iam.role_assignments` (~100 ms).
  3. **Next.js Server Page Component (e.g. `college/page.tsx`, `student/page.tsx`)**:
     Re-invokes `resolveUserRole(supabase)` independently, executing the exact same database queries a second time (~100 ms).
  4. **API Gateway (`api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java`)**:
     Line 188 invokes `config.getIdentityResolver().resolveIdentity(userUuid)`.
     In `JdbcIdentityResolver.java`, this executes:
     - Query 1: `SELECT ... FROM plat.platform_admins WHERE user_id = ?` (26 ms)
     - Query 2: `SELECT ... FROM iam.user_profiles WHERE id = ?` (26 ms)
     - Query 3: `SELECT ... FROM core.tenants WHERE id = ?` (26 ms)
     - Query 4: `SELECT ... FROM iam.role_assignments JOIN iam.roles ...` (26 ms)
     - Query 5: (Fallback) `SELECT id FROM core.colleges WHERE tenant_id = ?` (26 ms)
     **Crucially, `JdbcIdentityResolver` contains NO in-memory caching whatsoever!**
- **Impact**: A single page load executes **10 to 14 sequential database round trips** solely to verify the identity of an already-authenticated user. At 26.5 ms per WAN round trip, this wastes **~350–385 ms per request**.

---

### H5: Supabase Auth HTTP Round Trips in Middleware (CONFIRMED)

- **Code Evidence**:
  - In `frontend/src/middleware.ts`:
    ```typescript
    const { data: { user }, error } = await supabase.auth.getUser();
    ```
- **Impact**: `supabase.auth.getUser()` makes an external HTTPS request over the public internet to Supabase Auth servers on every route navigation that is not an asset or static file. This adds 50–90 ms of network latency to the HTTP TTFB before any server rendering or database access can begin.

---

### H6: Gateway JWKS Fetched Per Request (REJECTED)

- **Code Evidence**:
  - `api-gateway/src/main/java/com/campx/gateway/security/JwksClient.java`:
    ```java
    private final ConcurrentHashMap<String, PublicKey> keyCache = new ConcurrentHashMap<>();
    private static final long DEFAULT_CACHE_TTL_MS = 600000L; // 10 minutes
    ```
- **Finding**: Public RSA keys are cached in memory with a 10-minute TTL. Only the very first request after Gateway boot fetches keys from Supabase; subsequent requests validate JWTs purely in memory using cached keys.
- **Latency Contribution**: `0 ms` (warm steady-state).

---

### H7: Database Connection Handling & RLS Session Variables (CONFIRMED)

- **Code Evidence**:
  - Next.js direct connection pool (`frontend/src/lib/db.ts`):
    `max: 3` connections. Under multiple concurrent server component queries, connection acquisition queues up.
  - Java microservices HikariCP configuration:
    `maximumPoolSize = 10`, `connectionTimeout = 30000`.
  - In both layers, every RLS transaction must issue:
    ```sql
    SET LOCAL ROLE authenticated;
    SET LOCAL "request.jwt.claims" = '{...}';
    ```
- **Impact**: If not pipelined in a single query batch, setting session variables adds an extra round trip (~26.5 ms) per transaction.

---

### H8: Slow SQL Queries & Missing Indexes (PARTIALLY CONFIRMED)

- **Empirical Measurement**:
  - On the current development database, tables contain under 100 rows. Execution times are sub-4 ms even on complex joins.
  - However, query plans (`EXPLAIN (ANALYZE, BUFFERS)`) reveal that:
    - `core.persons` is scanned using `Seq Scan` rather than an index scan on `person_id`.
    - `acd.programs` is scanned using `Seq Scan`.
- **Verdict**: In dev, this contributes under 5 ms. In production with thousands of students, sequential scans will become a severe bottleneck.

---

### H9: Over-fetching in SELECT Statements (CONFIRMED)

- **Code Evidence**:
  - `frontend/src/app/(dashboard)/college/batches/page.tsx` fetches all batches and all subjects without limit or pagination.
  - `frontend/src/app/(dashboard)/student/page.tsx` loads entire JSON structures into memory.
- **Impact**: Bloats payload sizes (up to 300 KB), increases JSON serialization time in Java services, and wastes network bandwidth.

---

### H10: Dynamic Rendering & Post-Hydration Fetch Waterfalls (CONFIRMED)

- **Architecture Pattern**:
  - All dashboard pages enforce `export const dynamic = 'force-dynamic'`.
  - Pages like `college/documents/page.tsx`, `college/enrollment/page.tsx`, and `college/lifecycle/page.tsx` are built as `'use client'` React components with internal `useEffect` hooks.
  - The initial server response returns an empty shell/skeleton in ~100 ms (fast TTFB).
  - After browser hydration, the client fires secondary AJAX fetch requests to Next.js route handlers, which then call the Gateway.
- **Measurement**:
  - `COL_15` (Documents): TTFB is `99 ms`, but Action-to-Visible is `3,278 ms`.
  - `COL_16` (Enrollment): TTFB is `112 ms`, but Action-to-Visible is `5,449 ms`.
  - `COL_17` (Lifecycle): TTFB is `101 ms`, but Action-to-Visible is `3,222 ms`.
- **Finding**: Users stare at loading skeletons for 3.2 to 5.4 seconds while post-hydration fetch chains execute.

---

### H11: Single-Threaded Java HttpServer Serializing Requests (CONFIRMED)

- **Code Evidence**:
  - `api-gateway/src/main/java/com/campx/gateway/server/GatewayServer.java`:
    Uses `com.sun.net.httpserver.HttpServer`.
  - When Next.js issues concurrent requests via `Promise.all` (e.g. in Admissions with 3 requests), the Gateway dispatcher handles them on a single thread or small worker pool.
  - When request #1 blocks on an unreachable downstream connection, requests #2 and #3 queue behind it.
- **Impact**: Directly causes the 7,575 ms latency seen in `COL_12` (Admissions Applications).

---

### H12: Synchronous Audit & Intelligence Delivery Overhead (REJECTED)

- **Code Evidence**:
  - `ControlledIntelligenceAdapter.java`:
    `liveEndpointAvailable` is hardcoded to `false`. Events are added to an in-memory `CopyOnWriteArrayList` and return immediately.
- **Finding**: No synchronous network calls are made to external intelligence services during transactional requests.
- **Latency Contribution**: `0 ms`.

---

### H13: Downstream Unreachable Retries & Long Timeouts (CONFIRMED - PRIMARY ROOT CAUSE)

- **Code Evidence**:
  - `api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java`:
    ```java
    conn.setConnectTimeout(5000);
    conn.setReadTimeout(10000);
    ```
  - When a downstream microservice is not listening on its assigned port (e.g. STU-01 on 8093, STM-02 on 8094, ACD-03 on 8085, ACD-04 on 8086), `HttpURLConnection` attempts connection establishment and blocks until the OS TCP stack rejects or the timeout elapses (~2,500 ms on Windows localhost).
- **Correlation with Measured Latencies**:
  - 0 missing microservices: **~1,000 – 1,200 ms** (Platform Admin, College Admin baseline)
  - 1 missing microservice: **~3,200 – 3,350 ms** (Student Roster, Attendance, Intakes, Student Profile)
  - 2 missing microservices: **~5,400 – 5,750 ms** (Academic Batches, Student Dashboard, Enrollment)
  - 3 missing microservices: **~7,575 ms** (Admissions Applications)
- **Verdict**: This single architectural failure accounts for **over 70% of all user-visible latency** across slow and critical flows!

---

### H14: Documents Binary Proxying vs Direct Storage (CONFIRMED)

- **Architecture Pattern**:
  - Document uploads and downloads proxy raw file byte streams through the Next.js BFF and the Java Gateway rather than issuing pre-signed S3/Supabase Storage direct upload URLs.
- **Impact**: Doubles network hops, holds Gateway threads occupied during large transfers, and increases memory pressure.

---

### H15: Third-Party Fonts & Unoptimized External Assets (REJECTED)

- **Bundle Analysis**:
  - `next/font/google` is disabled; fonts are local system sans-serif stacks.
  - Zero external tracking pixels (Google Analytics, Sentry, Segment) are embedded.
  - All icons are local Lucide React SVGs bundled in client chunks.
- **Latency Contribution**: `0 ms`.

---

### H16: Local Machine Resource Constraints (CONFIRMED)

- **Host Specs**:
  - CPU: Intel Core i5-1235U (10 cores: 2 Performance, 8 Efficient)
  - RAM: 15.73 GB Total Physical RAM.
- **Under Audit Load**:
  - Simultaneous processes: Next.js production server, Java Gateway, 3 Java microservices, Playwright headed Chromium instance, IDE / agent tool processes.
  - Host memory load reached **93%**, with available RAM dropping to **~1.06 GB**.
- **Impact**: Causes thread scheduling delays, Windows memory swapping, and JVM garbage collection stalls.

---

## Root Cause Synthesis

The latency problems in CampXSync are not caused by slow database queries or large frontend bundles. They are driven by three compound architectural flaws:

1. **Unreachable Downstream Services & Unforgiving Timeouts (H13 + H11)**: Accounts for 2.5 s to 6.3 s of the delays on every slow/critical page.
2. **Redundant, Uncached Role & Identity Waterfalls (H4 + H5 + H1)**: Accounts for ~385 ms of mandatory WAN latency on every navigation.
3. **Sequential Server Component & Post-Hydration Fetch Waterfalls (H3 + H10)**: Converts fast server rendering into multi-second loading skeletons.

