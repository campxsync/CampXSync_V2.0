# Part E: Multi-Tier Authorization Enforcement Points

**Document:** `docs/audits/rbac-coverage/Part-E_enforcement.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Multi-Tier Authorization Architecture

CampXSync operates a four-tier defense-in-depth model, though enforcement mechanisms vary significantly between service tiers:

```
[ Tier 1: Client / UI ] ──► [ Tier 2: API Gateway ] ──► [ Tier 3: Java Domain Services ] ──► [ Tier 4: PostgreSQL RLS ]
 (Next.js 15 / BFF)           (Port :8080 HMAC)              (18 Leaf Microservices)               (Supabase DB)
```

| Enforcement Tier | Primary Mechanism | Authorization Artifact Checked | Trusted Context Forwarded |
| :--- | :--- | :--- | :--- |
| **Tier 1: Frontend (Next.js)** | UI Navigation & Route Handlers | `session.role` ('college_admin', 'faculty', etc.) | Supabase Auth JWT Cookie / Test Session |
| **Tier 2: API Gateway (:8080)** | `JdbcIdentityResolver.java` | Supabase JWT `sub`, DB role lookup | HMAC signature (`X-Gateway-Signature`), `X-User-Role`, `X-Tenant-Id` |
| **Tier 3: Java Microservices** | Filters & Controller Asserts | `X-User-Role`, `X-User-Id`, `X-Tenant-Id` | JDBC Connection Context (`SET LOCAL ROLE authenticated`) |
| **Tier 4: PostgreSQL Database** | RLS Policies & DB Triggers | `core.current_tenant_id()`, `iam.has_permission()` | PostgreSQL session context & `request.jwt.claims` |

---

## 2. Hardcoded Role-Name Checks vs Fine-Grained Permissions

**[CONFIRMED: Static scan of `services/**` for role-name checks]:**

A major architectural finding is that **the majority of Java microservices enforce authorization via coarse role-NAME string comparisons rather than fine-grained IAM permission keys**:

| Microservice | Location | Checked Header / Variable | Checked Role Strings |
| :--- | :--- | :--- | :--- |
| `academic-calendar-service` | `CalendarController.java:427` | `X-User-Role` | `"SUPER_ADMIN"`, `"ACADEMIC_ADMIN"`, `"ADMIN"` |
| `batch-management-service` | `BatchController.java:249, 474` | `X-User-Role` | `"COLLEGE_ADMIN"`, `"ACADEMIC_ADMIN"`, `"DEPARTMENT_HEAD"`, `"SUPER_ADMIN"` |
| `course-management-service` | `CourseDomainService.java:1174` | `X-User-Role` | `"SUPER_ADMIN"`, `"SYSTEM_ADMIN"` |
| `attendance-service` | `AttendanceController.java:641` | `X-User-Role` | `"SUPER_ADMIN"`, `"ACADEMIC_ADMIN"`, `"COLLEGE_ADMIN"`, `"INSTITUTE_ADMIN"` |
| `learning-resource-service`| `ResourceAccessPolicyService.java:136`| `X-User-Role` | `"SUPER_ADMIN"`, `"ADMIN"` |
| `admissions-service` | `AdmissionsPermissionEvaluator.java:22`| `X-User-Role` | `"R01"`, `"R02"`, `"R03"`, `"R04"`, `"SUPER_ADMIN"`, `"COLLEGE_ADMIN"` |
| `student-service` | `StudentDomainService.java:2103` | `X-User-Role` | `"SUPER_ADMIN"`, `"R01"`, `"R02"`, `"R03"` |

**Implication:** Merely updating permission rows in `iam.role_permissions` does NOT restrict access on these endpoints. Complete enforcement requires refactoring Java controllers to evaluate permission tokens rather than raw role names.

---

## 3. Client-Side-Only Checks & UI Boundary Risks

**[CONFIRMED: Code inspection of `frontend/src/**`]:**

1. **Dashboard Shell Navigation:**
   - Files: `frontend/src/components/layout/Sidebar.tsx`, `AppHeader.tsx`.
   - Logic: Evaluates `session.role` to hide/show navigation links (e.g. `/college/faculty`, `/college/timetable`).
   - Risk: **UI hiding is not a security boundary**. Direct URL navigation or REST calls to endpoints reach backend services directly.
2. **Direct Route Handlers (BFF Bypass):**
   - Several Next.js BFF routes (e.g. `frontend/src/app/api/college/faculty/route.ts`) execute direct database queries via `frontend/src/lib/db.ts` rather than proxying through API Gateway.
   - These direct routes enforce permissions only in TypeScript code, bypassing PostgreSQL RLS and triggers.

---

## 4. Tenant Isolation & Bypass Vectors

**[CONFIRMED: Audit of multi-tenant isolation mechanisms]:**

1. **Standard Gateway-to-Service Path (Secure):**
   - Gateway verifies Supabase JWT and injects `X-Tenant-Id`.
   - Java service extracts `X-Tenant-Id`, validates UUID format, and binds `WHERE tenant_id = ?` on every JDBC query.
   - Service executes `SET LOCAL ROLE authenticated;` and `SET LOCAL request.jwt.claims = '{"sub":"...", "tenant_id":"..."}';`.
   - PostgreSQL RLS enforces `tenant_id = core.current_tenant_id()`.
2. **Direct Superuser Connection Bypass (Vulnerability):**
   - Any query executing via `frontend/src/lib/db.ts` connects as user `postgres`.
   - Because `current_user` is `postgres`, PostgreSQL's `BYPASSRLS` attribute is active.
   - The query bypasses all RLS tenant checks. If the application-level SQL query omits `WHERE tenant_id = $1`, cross-tenant data leakage occurs immediately.
3. **Unprotected Schemas:**
   - 10 tables in schema `sys` (e.g., `sys.migration_logs`, `sys.error_logs`) have **zero RLS policies enabled**.

---

## 5. Top 20 Authorization Enforcement File:Line References

**[CONFIRMED: Authoritative code references across the repository]:**

1. `services/api-gateway/.../JdbcIdentityResolver.java:291` — Gateway role resolution and administrative tier mapping.
2. `services/api-gateway/.../HMACSigner.java:45` — Calculation of `X-Gateway-Signature` for downstream trust.
3. `services/student-services/admissions-service/.../AdmissionsPermissionEvaluator.java:22` — Hardcoded administrative override role list.
4. `services/student-services/admissions-service/.../AdmissionsPermissionEvaluator.java:112` — Segregation of Duties check on admissions decisions.
5. `services/student-services/student-service/.../StudentDomainService.java:520` — SoD check: requester cannot approve own profile correction request.
6. `services/student-services/student-service/.../StudentDomainService.java:2103` — Gate for STM-07 account actions (R01/R02/R03).
7. `services/academic-services/batch-management-service/.../BatchController.java:474` — Coarse role assert on `COLLEGE_ADMIN`.
8. `services/academic-services/academic-calendar-service/.../CalendarController.java:427` — Coarse role check on `SUPER_ADMIN` / `ADMIN`.
9. `services/attendance-services/attendance-service/.../AttendanceController.java:641` — Hardcoded role check on `COLLEGE_ADMIN`.
10. `services/lms-services/learning-resource-service/.../ResourceAccessPolicyService.java:136` — Coarse check on `SUPER_ADMIN` / `ADMIN`.
11. `services/administration-services/institute-admin-service/.../StudentRoleAssignmentService.java:132` — Enforces assignment of R25 `STUDENT` only.
12. `services/administration-services/institute-admin-service/.../DelegationAuthorizationService.java:36` — Validates active `STM-07.ACCOUNT.A` delegation.
13. `frontend/src/lib/auth/resolve-role.ts:131` — Direct SQL query to verify Platform Super Admin in `plat.platform_admins`.
14. `frontend/src/lib/auth/resolve-role.ts:191` — Direct SQL query to resolve active tenant role assignments.
15. `frontend/src/lib/auth/resolve-role.ts:344` — Bucket mapping collapsing `INSTITUTION_ADMIN`, `PRINCIPAL`, and `COLLEGE_ADMIN` into `college_admin`.
16. `frontend/src/lib/db.ts:42` — Creation of direct PostgreSQL pool running as superuser `postgres`.
17. `frontend/src/app/api/college/faculty/route.ts:5` — Direct BFF mutation route executing SQL outside API Gateway.
18. `database/migrations/Scripts_V3.0/test_rbac.sql:108` — True operational definition of `iam.grant_scopes()`.
19. `database/migrations/Scripts_V3.0/test_rbac.sql:135` — True operational definition of `iam.can_grant_role()`.
20. `database/migrations/Scripts_V3.0/test_rbac.sql:165` — True operational definition of trigger `iam.guard_role_assignment`.
