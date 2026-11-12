# CampXSync Internal Ingress Authentication Architecture: Forwarding User JWT

**Document:** [`docs/latency-audit/09_AUTH_PATH_DESIGN.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/latency-audit/09_AUTH_PATH_DESIGN.md)  
**Target Environment:** Local Stack & Deployed Environments (Production/Staging/Dev)  
**Subject:** Resolving Next.js Server Component Ingress Authentication to Java API Gateway (`CAMPX_INTERNAL_AUTH_ENABLED=true`)  
**Author:** CampX Platform Engineering Team  
**Date:** 2026-11-11  

---

## 1. Context & Architectural Problem

### 1.1 The Current Conflict
During local stack startup and production hardening, an architectural impasse was observed:
1. **API Gateway Requirement (`CAMPX_INTERNAL_AUTH_ENABLED=true`)**:
   The Java API Gateway (`ReverseProxyHandler.java`) is designed as a Zero-Trust Ingress Proxy. When internal auth is enabled, it strictly demands an `Authorization: Bearer <jwt>` header on every incoming request. It validates the cryptographic signature against Supabase JWKS, extracts the authoritative `sub` UUID, verifies tenant and role in PostgreSQL (`JdbcIdentityResolver`), and strips all client-supplied identity headers to prevent header injection spoofing.
2. **Next.js Server Component Ingress Reality**:
   In Next.js 15 Server Components (e.g. `platform/page.tsx`, `college/page.tsx`, `college/students/page.tsx`), the server-rendered React component executes on Node.js. Currently, Server Components synthesize downstream HTTP calls using:
   ```ts
   const headers = {
     'Accept': 'application/json',
     'X-User-Id': session.userId,
     'X-Tenant-Id': session.tenantId,
     'X-User-Role': 'COLLEGE_ADMIN',
   };
   await fetch('http://localhost:8080/api/v1/students', { headers });
   ```
   **The Conflict**: Next.js Server Components pass trusted contextual headers **without** the raw user JWT. When `CAMPX_INTERNAL_AUTH_ENABLED=true`, the Gateway promptly rejects these calls with **HTTP 401 Unauthorized (`GATEWAY_UNAUTHORIZED`)**.
3. **Current Workaround in Dev**:
   Setting `-Dcampx.internal.auth.enabled=false` instructs the Gateway to bypass JWT cryptographic validation and trust whatever `X-User-Id` / `X-Tenant-Id` headers arrive. While viable for isolated local testing, this cannot be safely deployed to production or staging without opening the Gateway to spoofing.

---

## 2. Target Design: Dual-Channel Ingress Authentication

To enable `CAMPX_INTERNAL_AUTH_ENABLED=true` seamlessly across local, staging, and production environments, the platform must support two authenticated channels:

```
[Browser Client]
       │
       │ (1) Cookie-based session / Bearer JWT
       ▼
[Next.js Server Components / BFF Routes]
       │
       ├── Channel A (User-Context Flow): Forwards session.access_token as Bearer JWT
       │   OR
       └── Channel B (System-Level Machine Flow): Injects System Service HMAC Signature
       ▼
[CampXSync API Gateway (Port 8080)]
       │  • Cryptographic verification (JWKS / HMAC)
       │  • Authoritative identity resolution & caching (REC-02)
       │  • Strips untrusted inbound headers
       │  • Injects verified X-User-Id, X-Tenant-Id, X-User-Role + Gateway HMAC
       ▼
[Java Domain Microservices (Ports 8081–8098)]
       • Verifies Gateway HMAC signature (GatewayHmacProtocol)
       • Executes transactional logic with tenant isolation
```

---

## 3. Channel A: Forwarding User JWT from Next.js Server Components

### 3.1 Mechanism
When a user navigates to an authenticated dashboard page, Next.js Server Components have access to the user's active session via `@supabase/ssr`:

```ts
// frontend/src/lib/api/gateway-client.ts
import { createClient } from '@/lib/supabase/server';

export async function fetchFromGateway(endpoint: string, options: RequestInit = {}) {
  const supabase = await createClient();
  const { data: { session } } = await supabase.auth.getSession();

  const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || 'http://localhost:8080';
  const url = new URL(endpoint, GATEWAY_BASE_URL);

  const headers = new Headers(options.headers || {});
  headers.set('Accept', 'application/json');

  if (session?.access_token) {
    // Forward the cryptographically verifiable Supabase JWT to the Gateway
    headers.set('Authorization', `Bearer ${session.access_token}`);
  }

  // Preserve tracing correlation
  if (!headers.has('X-Trace-Id')) {
    headers.set('X-Trace-Id', crypto.randomUUID());
  }

  return fetch(url.toString(), {
    ...options,
    headers,
  });
}
```

### 3.2 Gateway Processing Path
1. Gateway receives `Authorization: Bearer <jwt>`.
2. Validates JWT against Supabase JWKS (ES256 public keys cached with 1-hour TTL).
3. Reads `sub` claim.
4. Checks in-memory identity cache (`JdbcIdentityResolver.IDENTITY_CACHE`, 5-minute TTL).
   - If cache hit: returns in `< 1 ms`.
   - If cache miss: queries `plat.platform_admins`, `iam.user_profiles`, `core.tenants`, `iam.role_assignments` and populates cache.
5. Injects verified `X-User-Id`, `X-Tenant-Id`, `X-User-Role` and signs downstream payload with `GatewayHmacProtocol`.

---

## 4. Channel B: Machine-to-Machine (M2M) System Ingress

For automated tasks, background jobs, webhooks, or unauthenticated public routes (e.g. initial tenant onboarding before admin user creation), a user JWT does not exist.

### 4.1 System Service HMAC Ingress
The Gateway must recognize a pre-shared internal system HMAC signature:
- **Header**: `X-CampX-System-Signature`
- **Payload**: `timestamp + "\n" + method + "\n" + path + "\n" + bodySha256`
- **Signing Key**: `CAMPX_INTERNAL_SECRET`

When `X-CampX-System-Signature` is verified:
1. Gateway skips user JWT validation.
2. Accepts system-level claims provided by the trusted BFF (`X-System-Actor: NextBFF`).
3. Dispatches request downstream with authorized system privileges.

---

## 5. Security & Performance Benefits

1. **Zero-Trust Ingress**: Eliminates the dangerous security gap where backend microservices trust unverified HTTP headers.
2. **Sub-Millisecond Verification Overhead**: With REC-02 (in-memory identity cache), passing user JWTs does **NOT** add database latency. Once cached, validation takes `< 1 ms`.
3. **Environment Agnostic**: Identical code path executes locally (`localhost:8080`) and in deployed containerized environments (`api.campxsync.internal`).
4. **Tenant Isolation Enforcement**: The Gateway guarantees that `X-Tenant-Id` forwarded downstream matches the cryptographic identity verified in PostgreSQL, making cross-tenant data leaks impossible.

---

## 6. Implementation Roadmap

| Phase | Milestone | Scope |
| :--- | :--- | :--- |
| **Phase 1** | **Gateway Client Utility** | Create `frontend/src/lib/api/gateway-client.ts` encapsulating `getSession()` token forwarding. |
| **Phase 2** | **Server Component Migration** | Migrate `college/batches`, `college/students`, and `student/page.tsx` from raw `fetch` to `fetchFromGateway`. |
| **Phase 3** | **Gateway M2M Protocol** | Add `SystemHmacValidator` to `ReverseProxyHandler.java` for non-user background flows. |
| **Phase 4** | **Enable Flag Globally** | Switch `CAMPX_INTERNAL_AUTH_ENABLED=true` in `.env.dev`, staging, and production environments. |

