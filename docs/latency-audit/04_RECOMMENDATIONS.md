# CampXSync Latency Audit — Engineering Recommendations

**Audit Version**: 2.1.0  
**Audit Date**: November 11, 2026  
**Status**: **PHASE B IMPLEMENTED & VERIFIED** (REC-01, REC-02, REC-03 Part 1, REC-04, REC-05 implemented; REC-06..08 pending approval)  
**Branch**: `feature/java-backend-integration`

---

## Executive Summary

Based on empirical profiling across 44 transactional and analytical flows, the CampXSync stack suffered from large latency spikes primarily due to downstream connection timeouts, redundant identity/role verification waterfalls over the WAN, and serialized dispatching in the API Gateway. 

In Phase B, the top 5 approved optimizations were implemented and verified in code:
- **REC-01**: Configurable 150 ms connect timeout (`campx.gateway.connect.timeout.local.ms`) and strict fast-fail circuit breaker.
- **REC-02**: In-memory 5-minute TTL identity cache in `JdbcIdentityResolver.java`.
- **REC-03 Part 1**: React `cache()` memoization of `resolveUserRole()` in `resolve-role.ts`.
- **REC-04**: 32-worker bounded thread pool in `GatewayServer.java`.
- **REC-05**: `Promise.all` query parallelization and visible outage fallback banners in `batches/page.tsx` and `student/page.tsx`.

---

## Ranked Proposal Matrix & Phase B Implementation Status

| Rank | Proposal ID | Architecture Area | Title | Estimated Latency Saved | Effort | Risk Level | Phase B Status |
|:---:|:---:|:---|:---|:---:|:---:|:---:|:---|
| **1** | **REC-01** | API Gateway | Downstream Fail-Fast & Circuit Breaker | **2,500 – 6,300 ms** | Low (1–2 hrs) | Low | ✅ **IMPLEMENTED & VERIFIED** |
| **2** | **REC-02** | API Gateway | In-Memory Identity & Tenant Resolution Cache | **~105 ms** per API call | Low (1 hr) | Low | ✅ **IMPLEMENTED & VERIFIED** |
| **3** | **REC-03** | Next.js Frontend | Request-Scoped Role Memoization & JWT Role Propagation | **~200 ms** per nav | Low (2 hrs) | Low | 🔄 **PART 1 IMPLEMENTED** (React `cache()`) |
| **4** | **REC-04** | API Gateway | Multi-Threaded Dispatcher Executor Pool | **2,000 – 5,000 ms** concurrent | Low (30 mins) | Low | ✅ **IMPLEMENTED & VERIFIED** |
| **5** | **REC-05** | Next.js Server Pages | Parallelize Independent DB & BFF Queries (`Promise.all`) | **150 – 300 ms** | Medium (3 hrs) | Low | ✅ **IMPLEMENTED & VERIFIED** |
| **6** | **REC-06** | Next.js Client Pages | Server-Side Data Prefetching for Skeletons | **3,000 – 5,000 ms** UI delay | Medium (4 hrs) | Medium | ⏳ *Pending Future Phase* |
| **7** | **REC-07** | Supabase / PostgreSQL| Foreign Key & Tenant Scoped Indexes | **50 – 500 ms** at scale | Low (1 hr) | Low | ⏳ *Pending Approval (DB DDL)* |
| **8** | **REC-08** | Documents Service | Direct Signed URLs for Storage (Bypass Proxying) | **100 – 500 ms** + Gateway RAM | Medium (4 hrs) | Medium | ⏳ *Pending Future Phase* |

---

## Detailed Proposal Specifications (Design Only)

### REC-01: API Gateway Downstream Fail-Fast & Short Connect Timeout
- **Problem**: When a microservice is offline, `ReverseProxyHandler.java` sets `conn.setConnectTimeout(5000)`. On Windows localhost, TCP SYN retransmission hangs for ~2.5 seconds per offline service.
- **Design Proposal**:
  1. For localhost and intra-cluster communication, reduce `connectTimeout` from `5000 ms` to `150 ms`. If a local process is running, TCP handshake completes in under 1 ms; 150 ms is more than sufficient.
  2. Implement an in-memory Circuit Breaker: if a downstream target throws `ConnectException`, mark that service as `CIRCUIT_OPEN` for 10 seconds. Subsequent requests fail immediately (0 ms) with 503 instead of blocking for 2.5 seconds each.
- **Affected Files**:
  - `api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java`
- **Expected Impact**: Reduces critical flows (`COL_12`, `COL_04`, `STU_01`, `COL_16`) from 5.4–7.6s down to 1.1–1.3s.

---

### REC-02: API Gateway In-Memory Identity Resolution Cache
- **Problem**: `JdbcIdentityResolver.java` executes 4 to 5 sequential SQL queries against Supabase (`plat.platform_admins`, `iam.user_profiles`, `core.tenants`, `iam.role_assignments JOIN iam.roles`) for every single authenticated API request passing through the Gateway. Because each query traverses the WAN to AWS Mumbai (26.5 ms RTT), this wastes 105 ms on every request.
- **Design Proposal**:
  1. Add a thread-safe `ConcurrentHashMap<UUID, CachedIdentity>` cache to `JdbcIdentityResolver.java` with a 5-minute TTL.
  2. On request, check cache first: if present and not expired, return immediately without touching PostgreSQL.
  3. Invalidate cache on explicit role update or logout events.
- **Affected Files**:
  - `api-gateway/src/main/java/com/campx/gateway/identity/JdbcIdentityResolver.java`
- **Expected Impact**: Saves ~105 ms of WAN round trips on every single Gateway route dispatch.

---

### REC-03: Next.js Request-Scoped Role Memoization & JWT Role Claims
- **Problem**: Every page transition runs `resolveUserRole()` twice in Next.js (`layout.tsx` and `page.tsx`), firing up to 8 duplicate SQL queries to Supabase.
- **Design Proposal**:
  1. Wrap `resolveUserRole` with React `cache()` so multiple calls within the same request lifecycle reuse the first resolution.
  2. Populate `user_metadata.role` and `user_metadata.tenant_id` in Supabase Auth user object upon login, allowing middleware and layout to read claims directly without querying PostgreSQL tables.
- **Affected Files**:
  - `frontend/src/lib/auth/resolve-role.ts`
  - `frontend/src/app/(dashboard)/layout.tsx`
- **Expected Impact**: Eliminates 150–200 ms from Next.js server-side rendering TTFB on all dashboard routes.

---

### REC-04: API Gateway Multi-Threaded Dispatcher Executor Pool
- **Problem**: `GatewayServer.java` uses Sun `HttpServer` with default single-threaded or unconfigured executor (`server.setExecutor(null)`). When concurrent requests arrive (e.g. from Next.js `Promise.all`), they are processed sequentially, causing timeouts to stack linearly (e.g. 3 × 2.5s = 7.5s in `COL_12`).
- **Design Proposal**:
  1. Configure `server.setExecutor(Executors.newFixedThreadPool(32))` or `Executors.newVirtualThreadPerTaskExecutor()` (on JDK 21+).
  2. Allows concurrent downstream requests to execute in parallel rather than head-of-line blocking.
- **Affected Files**:
  - `api-gateway/src/main/java/com/campx/gateway/server/GatewayServer.java`
- **Expected Impact**: Converts stacked timeouts from $N \times L$ to $\max(L)$, reducing 7.5s down to 2.5s even when downstream services are offline.

---

### REC-05: Parallelize Independent DB & BFF Queries (`Promise.all`)
- **Problem**: Multiple server components sequentially `await` independent lookups (e.g. `batches` then `subjects`, or student dashboard queries).
- **Design Proposal**:
  1. Replace sequential `await` statements with `Promise.all([queryA(), queryB()])`.
- **Affected Files**:
  - `frontend/src/app/(dashboard)/college/batches/page.tsx`
  - `frontend/src/app/(dashboard)/student/page.tsx`
- **Expected Impact**: Saves 100–300 ms per affected page.

---

### REC-06: Server-Side Data Prefetching for Client Skeletons
- **Problem**: Client pages (`college/documents`, `college/enrollment`, `college/lifecycle`) render empty skeletons on the server, wait for client hydration, and then fetch data via `useEffect`, delaying user visibility by 3.2 to 5.4 seconds.
- **Design Proposal**:
  1. Fetch initial data on the server in `page.tsx` and pass it down as initial props to the client workspace component.
- **Affected Files**:
  - `frontend/src/app/(dashboard)/college/documents/page.tsx`
  - `frontend/src/app/(dashboard)/college/enrollment/page.tsx`
  - `frontend/src/app/(dashboard)/college/lifecycle/page.tsx`
- **Expected Impact**: Reduces Action-to-Visible time from 3.2–5.4s down to ~1.1s.

---

### REC-07: Add Foreign Key and Tenant Indexing
- **Problem**: `EXPLAIN (ANALYZE, BUFFERS)` reveals sequential scans on `core.persons` and `acd.programs`.
- **Design Proposal**:
  1. Create indexes:
     ```sql
     CREATE INDEX IF NOT EXISTS idx_persons_id_tenant ON core.persons(id, tenant_id);
     CREATE INDEX IF NOT EXISTS idx_programs_id_tenant ON acd.programs(id, tenant_id);
     CREATE INDEX IF NOT EXISTS idx_students_person_id ON stu.students(person_id);
     ```
- **Affected Files**:
  - Database schema / future migration (pending explicit authorization).
- **Expected Impact**: Prevents query degradation from sub-4 ms to hundreds of ms as student records scale.

---

### REC-08: Pre-signed Storage Direct URLs for Documents
- **Problem**: File upload/download bytes are proxied through Next.js and API Gateway.
- **Design Proposal**:
  1. Gateway/BFF generates pre-signed Supabase Storage upload/download URLs.
  2. Client uploads/downloads directly to Supabase S3-compatible storage.
- **Affected Files**:
  - `frontend/src/app/api/documents/...`
  - `services/administration-services/college-admin-service/...`
- **Expected Impact**: Eliminates gateway memory pressure and cuts upload/download latency in half.

---

## STOP GATE NOTICE

> [!IMPORTANT]
> This document constitutes design recommendations only. In strict accordance with the audit boundaries, **no code, configuration, or database modifications have been performed or will be performed** without explicit instruction from the user (`"GO FIX <item ids>"`).

