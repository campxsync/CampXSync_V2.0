# CampXSync — Security Architecture, Threat-Control & RLS Audit

**Document Identifier**: `docs/audit/CAMPXSYNC_SECURITY_AND_RLS_AUDIT.md`  
**Audit Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Governing Baseline**: Git HEAD (`ReviewBranch` commit `b307149`)  
**Cross-Reference Evidence Index**: [CAMPXSYNC_EVIDENCE_INDEX.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md)

---

## 1. Executive Summary & Security Posture

This audit provides an independent, evidence-based evaluation of all security controls, multi-tenant isolation guarantees, Row-Level Security (RLS) policies, authentication flows, and threat-mitigation mechanisms implemented across the CampXSync platform.

While significant hardening has been achieved—including elimination of default-tenant fallbacks, independent credentials for institutional roles, tamper-proof attendance ledger locking, and 100% RLS coverage on partition tables in Supabase Staging—**critical security vulnerabilities and architectural gaps remain unmitigated**:

1. **Direct SQL Database Access Completely Bypasses RLS**: All server-side database operations in `frontend/src/lib/db.ts` connect using `postgres.kpwdvvcschankgggitnj` (the superuser/table owner). Because PostgreSQL table owners bypass RLS by default and `FORCE ROW LEVEL SECURITY` is enabled on exactly **0 of the 458 tables**, RLS is not evaluated for any server query. Tenant isolation rests entirely on application query predicates.
2. **Unsigned Test-Session Cookie Allows Arbitrary Impersonation on Preview**: Cookie `campx_test_session` is serialized as plain JSON without cryptographic signatures or HMAC integrity checks. On Vercel Preview deployments, any remote client can forge this cookie and assume `SUPER_ADMIN` privileges.
3. **Unrevoked Vercel Automation Bypass Secret in Git History**: Commit `531a73c` committed a live Vercel protection bypass token in plaintext. The token remains in Git history and has not been confirmed rotated or revoked on Vercel.

---

## 2. Mandatory Security & Performance Topic Investigations

Per Section 15.E of the audit specification, this section details the five mandatory in-depth investigations:

### 2.1 Topic 1: Unsigned Test-Session Cookie Path
- **Vulnerability Title**: Unsigned Trial Session Cookie Insecure Deserialization & Privilege Escalation
- **Affected Components**:
  - `frontend/src/app/api/auth/test-session/route.ts` (Cookie issuance)
  - `frontend/src/middleware.ts` (Edge request parsing)
  - `frontend/src/lib/auth/resolve-role.ts` (Server role resolution)
  - `frontend/src/lib/auth/test-session-gate.ts` (Environment guard)
- **Detailed Mechanism**:
  1. **Issuance**: When `/api/auth/test-session` is invoked with `{ "role": "platform_admin" }`, line 115 executes:
     ```ts
     res.cookies.set('campx_test_session', JSON.stringify(userSession), {
       path: '/',
       httpOnly: true,
       sameSite: 'lax',
       secure: process.env.NODE_ENV === 'production',
       maxAge: 86400,
     });
     ```
     The cookie contains raw JSON with `userSession.id`, `email`, and `app_metadata.role`.
  2. **No Cryptographic Signature**: The cookie is neither signed with HMAC nor encrypted. There is no session token hash or server-side session table validation.
  3. **Middleware Parsing**: `frontend/src/middleware.ts` (lines 53-61):
     ```ts
     if (!user && isTestSessionAllowed()) {
       const testCookie = request.cookies.get('campx_test_session')?.value;
       if (testCookie) {
         try { user = JSON.parse(testCookie); } catch {}
       }
     }
     ```
  4. **Server Role Resolution**: `frontend/src/lib/auth/resolve-role.ts` (lines 98-114):
     ```ts
     if (isTestUser && isTestSessionAllowed() && user.app_metadata?.role === 'SUPER_ADMIN') {
       return {
         success: true,
         session: {
           userId,
           email: userEmail,
           role: 'platform_admin',
           isPlatformAdmin: true,
           ...
         }
       };
     }
     ```
  5. **Environment Gates**: In `test-session-gate.ts`:
     ```ts
     export function isTestSessionAllowed(env: NodeJS.ProcessEnv = process.env): boolean {
       if (env.VERCEL_ENV === 'production') return false;
       if (env.NODE_ENV !== 'production') return true;
       return env.ENABLE_TEST_SESSION === 'true';
     }
     ```
- **Access Assessment**:
  - **On Vercel Production**: `VERCEL_ENV === 'production'` unconditionally evaluates to false. Code-level protection prevents cookie simulation in Vercel Production.
  - **On Vercel Preview**: `VERCEL_ENV === 'preview'`. Because `ENABLE_TEST_SESSION === 'true'` is set in Preview settings, `isTestSessionAllowed()` returns `true`! An attacker over the public internet can construct an HTTP request with `Cookie: campx_test_session={"id":"...", "app_metadata":{"role":"SUPER_ADMIN"}}` and achieve full unauthenticated Platform Admin access.
  - **Code vs Deployment Protection**: Only Vercel's Deployment Protection (password or automation bypass header) stands between public ingress and unauthenticated superuser impersonation on Preview deployments.
- **Remediation Required**: Cryptographically sign test-session cookies with a server secret (`crypto.createHmac`), or validate test sessions against a server-side transient database table.

---

### 2.2 Topic 2: Vercel Bypass-Secret Exposure in Commit `531a73c`
- **Exposure Event**:
  - Commit Hash: `531a73c2a99583a27a4e91738e0f54d9e375bef5`
  - Author: `campxsync <campxsync@gmail.com>`
  - Date: Sun Oct 18 18:17:54 2026 +0530
  - File: `scripts/verify_faculty_auth_and_routing.mjs` (Line 18)
  - Hardcoded Statement: `const VERCEL_BYPASS_TOKEN = '<REDACTED_BYPASS_SECRET>';`
- **Subsequent Modification**:
  - In commit `62a5bcd` (Mon Oct 19 13:25:39 2026), the script was modified to read the token from `process.env.VERCEL_AUTOMATION_BYPASS_SECRET || ''`.
  - In `scratch/audit_secret_locations.mjs`, an audit script searched the working tree and confirmed the secret is no longer present in tracked files outside `.env.local`.
- **Evidence of Rotation or Revocation**:
  - **CRITICAL FINDING: ZERO EVIDENCE OF ROTATION OR REVOCATION**.
  - Commit `531a73c` remains permanently in the Git repository history. Anyone with read access to the repository can extract the bypass token.
  - There is no log, Vercel CLI record, or commit documenting that the Vercel Automation Bypass Secret was regenerated or revoked in Vercel Project Settings > Security > Deployment Protection.
- **Unresolved Risk**:
  - Possessing this bypass token allows an attacker to bypass Vercel Deployment Protection on all Preview deployments (`x-vercel-protection-bypass`), granting unrestricted access to test-session impersonation endpoints.
- **Remediation Required**:
  1. Immediately rotate the Automation Bypass Secret in the Vercel Dashboard (`Settings > Deployment Protection`).
  2. Purge commit `531a73c` from Git history using `git filter-repo` or BFG Repo-Cleaner before open-sourcing or sharing the repository.

---

### 2.3 Topic 3: PostgREST-to-Direct-SQL Role Resolution & RLS Bypass Implications
- **Architectural Change Context**:
  - Prior to commit `afaff92`, `resolve-role.ts` attempted to query user profiles and platform admins via Supabase PostgREST client (`supabase.schema('plat').from(...)`).
  - Because Supabase PostgREST only exposes the `public` schema, all calls failed over HTTP with `PGRST106` errors, generating a 1.5s–2.5s latency penalty on every login.
  - In commit `afaff92`, the failed PostgREST calls were replaced with direct parameterized PostgreSQL queries executed via `frontend/src/lib/db.ts` (`query()`).
- **Actual Queries Executed in `resolve-role.ts`**:
  1. `SELECT id, status, role_code FROM plat.platform_admins WHERE user_id = $1 AND role_code = $2 AND status = $3 AND deleted_at IS NULL LIMIT 1` (Parameters: `[userId, 'SUPER_ADMIN', 'ACTIVE']`)
  2. `SELECT id, tenant_id, status, deleted_at FROM iam.user_profiles WHERE id = $1 AND deleted_at IS NULL LIMIT 1` (Parameters: `[userId]`)
  3. `SELECT ra.id, ra.college_id, ra.department_id, ra.valid_from, ra.valid_to, r.code as role_code, r.name as role_name FROM iam.role_assignments ra JOIN iam.roles r ON ra.role_id = r.id WHERE ra.user_id = $1 AND ra.deleted_at IS NULL AND r.deleted_at IS NULL` (Parameters: `[userId]`)
  4. `SELECT id, name, code, status, deleted_at FROM core.tenants WHERE id = $1 LIMIT 1` (Parameters: `[tenantId]`)
  5. `SELECT id FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL LIMIT 1` (Parameters: `[tenantId]`)
- **Connection Implementation & RLS Bypass**:
  - `frontend/src/lib/db.ts` uses `pg.Pool` with connection credentials `SUPABASE_USER = postgres.kpwdvvcschankgggitnj`.
  - In PostgreSQL, the `postgres` role owns all application schemas and tables. Table owners **completely bypass Row-Level Security** unless `FORCE ROW LEVEL SECURITY` is enabled.
  - Database introspection confirms that `FORCE ROW LEVEL SECURITY` is **NOT enabled** on any table in the database.
  - Furthermore, `query()` in `frontend/src/lib/db.ts` does NOT execute `SET LOCAL ROLE authenticated;` or `SELECT set_config('request.jwt.claims', ..., true);`.
- **Tenant Isolation Mechanism**:
  - Because RLS is completely bypassed on direct SQL queries, tenant isolation does NOT rely on PostgreSQL kernel policies.
  - Tenant isolation relies **100% on application-level query predicates** (`WHERE tenant_id = $1`) and server-side role resolution checks.
  - If any server action or route handler executes a query without an explicit `tenant_id` filter, cross-tenant data leakage will occur without PostgreSQL RLS intervention.

---

### 2.4 Topic 4: Vercel Function Region Configuration vs. Runtime Evidence
- **Configuration Evidence**:
  - `frontend/vercel.json` contains:
    ```json
    {
      "$schema": "https://openapi.vercel.sh/vercel.json",
      "framework": "nextjs",
      "regions": ["sin1"]
    }
    ```
  - Root `vercel.json` contains `"regions": ["sin1"]`.
  - Both files pin serverless function execution to Singapore (`sin1`), colocated with the Supabase PostgreSQL database on AWS `ap-southeast-1`.
- **Deployed Runtime Evidence**:
  - Commit `b4e2b99` (`chore(deploy): pin Vercel functions to sin1`) was committed to `ReviewBranch` on Mon Oct 19 13:26:00 2026.
  - However, the active Vercel deployments recorded in `docs/staging/05-execution-log.md` (Preview `dpl_7oE8yDDTT6TAKJyDkkg8aAmpWdfe` and Alias `dpl_ExCRqLCy6Z88Bz3VsiPmv6FaKQbX`) were deployed on October 16, 2026 from commit `b542340`.
  - **Verdict**: The `sin1` region pinning is **VERIFIED IN SOURCE CONFIGURATION ONLY**. It is **UNVERIFIED IN ACTIVE DEPLOYED RUNTIME** until a new Vercel deployment is promoted from commit `b4e2b99` or later. The currently live deployment may still execute in Vercel's default US East region (`iad1`).

---

### 2.5 Topic 5: Login Performance Investigation & Statistical Limitations
- **Investigation Background**:
  - Following reports of a ~6-second login delay, systematic latency measurements were conducted across three roles (`College Admin`, `Platform Admin`, `Faculty`) against both the live preview URL and local instrumented probes (`scripts/measure_login_performance.mjs` and `scripts/probe_resolve_role_latency.mjs`).
- **Preserved Before & After Measurements**:

| Role Tested | Pre-Fix Cold Load | Pre-Fix Repeat Median | Post-Fix Cold Load | Post-Fix Repeat Median | Latency Reduction |
| :--- | :---: | :---: | :---: | :---: | :---: |
| **College Admin** | 17,037 ms | 12,544 ms | 5,371 ms | 2,880 ms | **77% (9.7s faster)** |
| **Platform Admin** | 9,583 ms | 9,137 ms | 2,262 ms | 2,145 ms | **76% (7.0s faster)** |
| **Faculty Member** | 11,284 ms | 10,757 ms | 2,380 ms | 2,675 ms | **75% (8.1s faster)** |

- **Sub-Request Timing Reductions (Post-Fix)**:
  - Platform Admin `GET /`: Dropped from 3,128ms to **324ms** (90% reduction).
  - Platform Admin `GET /platform`: Dropped from 4,560ms to **792ms** (83% reduction).
  - Faculty `GET /`: Dropped from 3,989ms to **517ms** (87% reduction).
  - Faculty `GET /faculty`: Dropped from 5,477ms to **781ms** (86% reduction).
  - College Admin `GET /`: Dropped from 7,375ms to **1,844ms** (75% reduction).
  - College Admin `GET /college`: Dropped from 6,461ms to **1,534ms** (76% reduction).
- **Statistical Limitations & Caveats**:
  1. **Limited Sample Size**: Exactly 10 runs per role were conducted. A sample size of 10 observations provides insufficient statistical confidence for high-percentile SLA guarantees (p95 / p99).
  2. **First Browser Run != True Serverless Cold Start**: The recorded "cold" measurements represent the first browser request executed in a fresh Playwright session. In serverless environments, Vercel Lambda microVM initialization (true cold start) can introduce 1,000ms–3,000ms of additional un-cached container initialization latency not isolated from browser startup overhead.
  3. **Target Environment**: Measurements were conducted against the deployed Vercel preview URL using the bypass header and direct Supabase Staging pooler. Production performance under concurrent institutional load remains unverified.

---

## 3. Independent Code Verification of 20 High-Impact Claims

Per Section 15.D, the table below documents the direct code verification of the 20 highest-impact architectural and security claims:

| Claim # | Exact Claim Evaluated | Source File & Relevant Symbols | Actual Code Implementation Behavior | Evidence Classification | Verification Verdict | Security & Operational Implications |
| :---: | :--- | :--- | :--- | :---: | :---: | :--- |
| **1** | Spring Cloud / JDK HTTP API Gateway validates Supabase JWTs with full ES256 cryptographic signature validation and secure claims parsing. | `api-gateway/src/main/java/com/campx/gateway/security/SupabaseJwtValidator.java` | Refactored in commit `cffb841` to parse structured JSON AST and validate ES256 signatures via JWKS. Tested in `SupabaseJwtClaimParsingRegressionTest` (29/29 pass). | `CODE VERIFIED` | **PARTIALLY SUPPORTS** | While the Java code was fixed, this gateway is NOT deployed on Vercel; it is completely inactive in the cloud runtime. |
| **2** | Java Academic Microservices (ACD-02..10) enforce HMAC request signatures and RBAC authorization. | `services/attendance-service/src/main/java/.../AttendanceController.java` | Controllers inspect `X-User-Role` without validating HMAC signatures, and default to `ACADEMIC_ADMIN` if the header is missing. | `CODE VERIFIED` | **CONTRADICTS** | High vulnerability in Java services (Finding SEC-CRIT-02). Any caller with direct network access to Java ports can forge admin privileges. |
| **3** | All 12 Java microservices persist domain state to PostgreSQL. | `services/*/src/main/java/.../repository/` | Only 3 services (`institute-admin`, `college-admin`, `course-management`) have PostgreSQL repositories. The remaining 9 store data in ephemeral `ConcurrentHashMap` collections. | `CODE VERIFIED` | **CONTRADICTS** | Restarting Java services wipes all academic state. Superceded by Next.js/PostgreSQL cloud architecture. |
| **4** | Next.js Server Components and Route Handlers enforce PostgreSQL Row-Level Security (RLS) on all database access. | `frontend/src/lib/db.ts` (lines 8-49) | Connects as superuser/table owner `postgres.kpwdvvcschankgggitnj`. Table owners bypass RLS in PostgreSQL unless FORCE RLS is set. FORCE RLS is enabled on 0 tables. | `CODE VERIFIED` | **CONTRADICTS** | RLS is 100% bypassed on direct SQL queries. Tenant isolation relies solely on application query predicates (`WHERE tenant_id = $1`). |
| **5** | All 24 monthly partition child tables across `audit`, `cam`, `hrm`, and `lms` have Row-Level Security enabled with zero direct access grants. | `database/migrations/V20261014_01__enable_rls_on_monthly_partition_children.sql`; `docs/staging/05-execution-log.md` (P3-07, P3-13) | `GO PARTITION-RLS-FIX` executed `ENABLE ROW LEVEL SECURITY` and `REVOKE ALL` on all 24 tables. Catalog introspection confirmed `relrowsecurity = true` and 0 direct grants. | `STAGING VERIFIED` | **SUPPORTS** | Partition tables on Staging are fully protected. Migration must be executed on production prior to go-live. |
| **6** | Next.js Edge Middleware unconditionally denies test-session impersonation in production. | `frontend/src/lib/auth/test-session-gate.ts` (lines 6-16); `frontend/src/middleware.ts` | Checks `if (env.VERCEL_ENV === 'production') return false;`. Unconditionally denies on Vercel Production. However, if deployed on non-Vercel Node where `VERCEL_ENV` is unset, it permits test sessions if `ENABLE_TEST_SESSION === 'true'`. | `CODE VERIFIED` | **PARTIALLY SUPPORTS** | Code protects Vercel Production, but relies on `VERCEL_ENV` environment variable presence. |
| **7** | The `campx_test_session` cookie is cryptographically signed and tamper-proof. | `frontend/src/app/api/auth/test-session/route.ts` (line 115) | Sets `res.cookies.set('campx_test_session', JSON.stringify(userSession), ...)`. Raw JSON without cryptographic signature, HMAC, or encryption. | `CODE VERIFIED` | **CONTRADICTS** | High vulnerability on Preview deployments. Anyone can forge arbitrary roles without password verification. |
| **8** | Platform Admin is strictly isolated from accessing tenant-level operational student and faculty records (HTTP 403 Forbidden). | `frontend/src/middleware.ts` (lines 188-198); `scripts/verify_phase9_security_browser.mjs` (Req 6, 7) | `middleware.ts` redirects Platform Admin away from `/college` and `/faculty` to `/access-denied?reason=forbidden`. Automated test `P10-SEC-01` verified HTTP 403. | `CODE VERIFIED`<br>`TEST VERIFIED` | **SUPPORTS** | Platform Admin tier is strictly partitioned from institutional tenant data. |
| **9** | Platform Admin role is authoritatively verified against `plat.platform_admins` and cannot be claimed via email prefix or user metadata. | `frontend/src/lib/auth/resolve-role.ts` (lines 68-77) | Queries `plat.platform_admins WHERE user_id = $1 AND role_code = 'SUPER_ADMIN' AND status = 'ACTIVE' AND deleted_at IS NULL`. Insecure email prefix fallbacks were completely removed. | `CODE VERIFIED` | **SUPPORTS** | Prevents arbitrary privilege escalation via user metadata or crafted email addresses. |
| **10** | Role resolution authoritatively verifies tenant status in `core.tenants` and fails closed if a tenant is suspended or deleted. | `frontend/src/lib/auth/resolve-role.ts` (lines 204-222) | Queries `core.tenants WHERE id = $1 LIMIT 1`. If `deleted_at IS NOT NULL` or `status === 'SUSPENDED'` or tenant missing, returns `{ success: false, reason: 'tenant_suspended' }`. | `CODE VERIFIED` | **SUPPORTS** | Guarantees that suspended or offboarded educational institutions are locked out immediately. |
| **11** | Missing or unassigned user roles fail closed to `/access-denied?reason=no_role_assigned` without falling back to a default tenant or role. | `frontend/src/lib/auth/resolve-role.ts` (lines 228-230, 291-294); `frontend/src/middleware.ts` (line 184) | If role assignments are missing, `resolveUserRole` returns `no_role_assigned`. Visiting root `/` redirects to `/access-denied?reason=no_role_assigned`. Hardcoded tenant fallbacks removed. | `CODE VERIFIED` | **SUPPORTS** | Fails closed safely. Unassigned users cannot access any institutional data. |
| **12** | Faculty Attendance submission permanently locks the session sheet as read-only and prevents subsequent edits. | `frontend/src/app/(dashboard)/faculty/attendance/attendance-sheet.tsx`; `frontend/src/app/api/college/attendance/sessions/route.ts` | Once marked `CLOSED`, inputs and radio buttons are disabled in the UI. Server route rejects edits on closed sessions with HTTP 409 Conflict. | `CODE VERIFIED`<br>`TEST VERIFIED` | **SUPPORTS** | Guarantees tamper-evident academic ledger locking. Prevents retroactive grade/attendance manipulation. |
| **13** | Administrative Attendance Corrections require College Admin authorization, mandatory justification (>= 5 chars), and optimistic concurrency locking (`row_version`). | `frontend/src/app/api/college/attendance/corrections/route.ts` (lines 28-95) | Verifies `currentRole === 'college_admin'`, asserts `expectedVersion === currentVersion`, validates `reason.trim().length >= 5`, increments version, and writes immutable change log. | `CODE VERIFIED`<br>`TEST VERIFIED` | **SUPPORTS** | Full audit compliance and concurrency safety for statutory attendance corrections. |
| **14** | Formula injection in cumulative attendance CSV exports is prevented by sanitizing special formula prefixes. | `frontend/src/app/api/college/attendance/report/route.ts` (lines 42-55) | Prepends a single quote `'` to fields beginning with `=`, `+`, `-`, `@`, `\t`, `\r`. | `CODE VERIFIED`<br>`TEST VERIFIED` | **SUPPORTS** | Neutralizes CSV formula injection (CWE-1236) in Microsoft Excel and Google Sheets. |
| **15** | The Supabase Custom Access Token Hook dynamically injects tenant and role claims into issued JWTs. | `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/85_auth_hook.sql`; `docs/staging/05-execution-log.md` (P3-21) | Function `iam.custom_access_token_hook(event jsonb)` is deployed on staging. However, tokens issued by Supabase Auth were found to omit `role`, necessitating direct PostgreSQL resolution in `resolve-role.ts`. | `STAGING VERIFIED` | **PARTIALLY SUPPORTS** | Hook is compiled on staging, but runtime claims propagation required server-side direct SQL fallback. |
| **16** | The Vercel automation bypass secret exposed in commit `531a73c` has been rotated and revoked. | `scripts/verify_faculty_auth_and_routing.mjs`; commit `531a73c`; commit `62a5bcd` | Commit `62a5bcd` removed the token from the script file, but commit `531a73c` remains in Git history. Zero evidence of rotation in Vercel project settings. | `CODE VERIFIED` | **CONTRADICTS** | Critical risk. Secret is permanently exposed in Git history and unconfirmed revoked on Vercel. |
| **17** | Vercel serverless function region is pinned to `sin1` (Singapore) in runtime production. | `frontend/vercel.json`; `vercel.json`; commit `b4e2b99` | Configured in `frontend/vercel.json`, but commit `b4e2b99` is an un-promoted local commit. Deployed Vercel alias was built from older commit `b542340`. | `CODE VERIFIED` | **PARTIALLY SUPPORTS** | Verified in source configuration only; unverified in active deployed runtime. |
| **18** | Student Dashboard (`/student`) is fully implemented with real student database persistence and interactive workflows. | `frontend/src/app/(dashboard)/student/page.tsx` | View is a lightweight static demonstration with mock metric cards and alert badges. No student database persistence or interactive actions exist. | `CODE VERIFIED` | **CONTRADICTS** | Student Dashboard is incomplete and scaffolded only. |
| **19** | Dual-tenant realistic staging fixtures for College A and College B are isolated and idempotent on re-execution. | `database/seed-data/staging_seed_college_a_and_b.sql`; `docs/staging/05-execution-log.md` (P3-23, P3-26) | Inserts use deterministic UUIDs with `ON CONFLICT (id) DO NOTHING`. Multi-table live catalog query confirmed 0 duplicates across 9 business tables. | `STAGING VERIFIED` | **SUPPORTS** | Guarantees clean, isolated test data across College A (`11111111-...`) and College B (`22222222-...`). |
| **20** | Login performance optimization (replacing PostgREST private schema queries with direct SQL) reduced login delay by 75-77% across all roles. | `frontend/src/lib/auth/resolve-role.ts`; commit `afaff92`; `scripts/measure_login_performance.mjs` | Replacing failed PostgREST calls with direct parameterized SQL dropped median login times from 12.5s to 2.88s (College Admin) and 10.7s to 2.67s (Faculty). | `TEST VERIFIED`<br>`STAGING VERIFIED` | **SUPPORTS** | Demonstrates substantial performance improvement, subject to limited sample size caveats (10 runs). |

---

## 4. Summary of Claims Contradicted by Source Code

The direct code audit explicitly contradicts **5 major claims**:
1. **Claim 2 (Java Microservices HMAC & RBAC)**: Java academic controllers completely lack HMAC request signature verification and default to `ACADEMIC_ADMIN` when `X-User-Role` is omitted.
2. **Claim 3 (Java Microservices Persistence)**: 9 of the 12 Java microservices have zero database integration, running entirely in-memory.
3. **Claim 4 (Next.js Server PostgreSQL RLS Enforcement)**: Server-side queries execute via `pg.Pool` as the table owner `postgres`, completely bypassing PostgreSQL Row-Level Security.
4. **Claim 7 (Tamper-Proof Test-Session Cookie)**: The `campx_test_session` cookie is raw, unencrypted, unsigned JSON.
5. **Claim 16 (Bypass Secret Revocation)**: The Vercel bypass secret exposed in commit `531a73c` remains in Git history and has not been confirmed rotated or revoked in Vercel settings.
6. **Claim 18 (Student Dashboard Implementation)**: `/student` is a static demo page lacking interactive workflows or real database persistence.

---

## 5. Disposition of Historical Security Audit Findings

| Audit ID | Finding Description | Initial Severity | Current Status | Current Evidence & Disposition |
| :---: | :--- | :---: | :---: | :--- |
| **CRIT-01** | Gateway JWT Regex Claim Injection | Critical | **SUPERSEDED** | Java gateway fixed in `cffb841`, but superseded in production by Next.js Edge Middleware and `@supabase/ssr`. |
| **CRIT-02** | Academic Services Fail-Open Roles | Critical | **SUPERSEDED** | Java controllers superseded by Next.js Server Components and route handlers enforcing strict RBAC. |
| **CRIT-03** | In-Memory Ephemeral Storage | Critical | **RESOLVED IN CLOUD MVP** | Active MVP slice persists to Supabase PostgreSQL (`acd.courses`, `acd.attendance_records`, etc.). |
| **CRIT-04** | Monthly Partition Tables Lack RLS | Critical | **RESOLVED ON STAGING** | `GO PARTITION-RLS-FIX` applied RLS to all 24 child partition tables on Staging (Pending on Production). |
| **HIGH-01** | Cross-Tenant IDOR Vulnerability | High | **RESOLVED IN CODE** | Route handlers validate tenant context and reject cross-tenant IDs with HTTP 404/403. |
| **HIGH-02** | Staging Test Password Exposure | High | **RESOLVED IN CODE** | Passwords moved to `.env.local` and environment variables. |
| **NEW-01** | Direct SQL Server RLS Bypass | High | **OPEN / UNRESOLVED** | Direct SQL queries bypass RLS because the connection uses the table owner role without forced RLS. |
| **NEW-02** | Unsigned Test-Session Cookie on Preview | High | **OPEN / UNRESOLVED** | Cookie is unsigned JSON. In Preview, any remote attacker can forge `SUPER_ADMIN` privileges. |
| **NEW-03** | Exposed Vercel Bypass Secret in Git History | High | **OPEN / UNRESOLVED** | Commit `531a73c` permanently contains the token in Git history; rotation unverified. |
