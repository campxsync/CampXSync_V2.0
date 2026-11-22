# Part C: Wildcard Gaps & Privileged Session Deep-Dive

**Document:** `docs/audits/rbac-coverage/Part-C_wildcard-gaps.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## C1. Full Contents of `iam.role_grant_rules` vs Matrix §21.1

### Live Database State (`iam.role_grant_rules`)
**[CONFIRMED: SQL query `SELECT grantor_role, grantable_role FROM iam.role_grant_rules ORDER BY grantor_role, grantable_role` on DEV `azulbaxszsqxymqmlwfe`]:**
The table contains exactly **84 rows** across 3 grantor roles:

| Grantor Role (Live Code) | Plain-Language Label | Grantable Role (Live Code) | Plain-Language Label |
| :--- | :--- | :--- | :--- |
| `COLLEGE_ADMIN` | College Administrator (R04) | `ACADEMIC_ADMIN` | Academic Administrator (Legacy) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `ACCOUNTANT` | Accountant / Finance Officer (R16) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `COLLEGE_ADMIN` | College Administrator (R04) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `COMM_MANAGER` | Communication Manager (R19) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `DEAN` | Dean (R07) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `EMPLOYEE` | Employee Self-Service |
| `COLLEGE_ADMIN` | College Administrator (R04) | `EXAM_CELL` | Examination Cell Officer (R13) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `EXAM_CONTROLLER` | Controller of Examinations (R12) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `FACILITY_MANAGER` | Facility Manager (R24) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `FACULTY` | Faculty / Teacher (R10) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `FINANCE_MANAGER` | Finance Manager (R15) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `GUEST` | Guest User (R30) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `HOD` | Head of Department (R08) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `HOSTEL_WARDEN` | Hostel Warden (R22) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `HR_MANAGER` | Human Resources Manager (R17) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `HR_OFFICER` | Human Resources Officer (R18) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `LIBRARIAN` | Librarian (R21) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `PARENT` | Parent / Guardian (R26) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `PLACEMENT_OFFICER` | Placement Officer (R20) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `PRINCIPAL` | Principal (R05) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `PROGRAM_COORDINATOR` | Program Coordinator (R09) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `REPORT_USER` | Reporting User (R29) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `SECTION_COORDINATOR` | Class / Section Coordinator (R11) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `STUDENT` | Student (R25) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `TRANSPORT_MANAGER` | Transport Manager (R23) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `VALUATOR` | Valuator / Examiner (R14) |
| `COLLEGE_ADMIN` | College Administrator (R04) | `VICE_PRINCIPAL` | Vice Principal (R06) |
| `INSTITUTION_ADMIN` | Institution Administrator (R03) | All 27 roles above PLUS `AUDITOR` (R28) (Total = 28 roles) |
| `TENANT_ADMIN` | Tenant Super Administrator | All 28 roles above PLUS `INSTITUTION_ADMIN` (R03) and `TENANT_ADMIN` (Total = 29 roles) |

### Comparison with Matrix §21.1 (Owner Decision D-RBAC-M10)
**[CONFIRMED: Discrepancy analysis against Matrix §21.1]:**
1. **R04 Over-Granting Violation:** In the live database, `COLLEGE_ADMIN` can grant `PRINCIPAL` (R05), `VICE_PRINCIPAL` (R06), and `DEAN` (R07). Matrix §21.1 explicitly mandates: *“R04 College Admin may not assign R01–R07, R27, R28, R37, R38.”*
2. **Missing Matrix Grantors:** Matrix §21.1 defines `R01 Super Admin` (appoints R02, R03) and `R02 System Admin` (platform technical service identities only). Neither `R01` nor `R02` exists in `iam.role_grant_rules`.
3. **Orphaned Roles:** Three active roles in `iam.roles` (`REGISTRAR`, `ADMISSIONS_OFFICER`, `APPLICANT`) have **zero grantors** in `iam.role_grant_rules`. No non-superuser actor can assign them through standard IAM grant procedures.

---

## C2. Roles Holding Wildcard `*` or Domain Wildcards (`<domain>.*`)

**[CONFIRMED: SQL query `SELECT role_code, permission_code FROM iam.role_template_permissions WHERE permission_code LIKE '%*%'` and live table query on `iam.role_permissions`]:**

1. **Universal Wildcard (`*`):**
   - In `iam.role_template_permissions`: Exactly **1 template** holds `*`: `TENANT_ADMIN`.
   - In `iam.role_permissions`: Exactly **49 rows** across all 49 tenants hold `*` (1 per tenant, all assigned to `TENANT_ADMIN`).
   - Zero custom roles hold `*`.
2. **Domain Prefix Wildcards (`<domain>.*`):**
   - **Zero** roles in `iam.role_template_permissions` or `iam.role_permissions` hold any domain wildcard such as `adm.*`, `acd.*`, `fin.*`, etc.
   - The permission resolution logic in `iam.has_permission(p_code)` supports prefix wildcards (`split_part(p_code, '.') || '.*'`), but no such keys are seeded or assigned.

---

## C3. The Administration Management Permission Keys

Earlier reports referred to 7 administration management keys. An authoritative query against `iam.permissions` reveals the exact status of `adm01.manage` through `adm09.manage`:

**[CONFIRMED: SQL query `SELECT code, module, action FROM iam.permissions WHERE code LIKE 'adm0%.manage'`]:**

| Permission Key | Plain-Language Meaning | In `iam.permissions`? | Present on Role Templates? | Endpoints / Screens / Policies Using Key |
| :--- | :--- | :--- | :--- | :--- |
| `adm01.manage` | Institute & Tenant Lifecycle Administration | YES | `INSTITUTION_ADMIN` (R03) only | `api-gateway` / `institute-admin-service` tenant creation; RLS on `core.tenants` |
| `adm02.manage` | Platform Configuration & Global Policy Management | YES | `INSTITUTION_ADMIN` (R03), `COLLEGE_ADMIN` (R04) | Configuration desks; RLS on `cfg.system_settings` |
| `adm03.manage` | API Client & Machine Identity Lifecycle Management | YES | **NO TEMPLATE** (Orphaned) | RLS DELETE policy on `iam.api_clients` (`60_rls.sql:1951`) |
| `adm04.manage` | Student & Faculty Directory Administration | **NOT FOUND** | **NOT FOUND** | Does not exist in DB (only `adm04.view`, `adm04.view.all` exist) |
| `adm05.manage` | Campus Resources & Operations Management | YES | **NO TEMPLATE** (Orphaned) | RLS on `cam.facilities` (legacy facilities desk) |
| `adm06.manage` | Platform Reporting & Analytics Administration | YES | `INSTITUTION_ADMIN` (R03), `COLLEGE_ADMIN` (R04) | Analytics reporting export endpoints |
| `adm07.manage` | Security & Compliance Governance Administration | YES | `INSTITUTION_ADMIN` (R03), `COLLEGE_ADMIN` (R04) | Security compliance audit desks |
| `adm08.manage` | Audit, Health & Operations Oversight Management | YES | `INSTITUTION_ADMIN` (R03), `COLLEGE_ADMIN` (R04) | Health check and audit trail configuration |
| `adm09.manage` | User Administration & Timetable Management | YES | `INSTITUTION_ADMIN` (R03), `COLLEGE_ADMIN` (R04) | College user role assignment desks; timetable scheduler |

**Summary of Gaps:**
- `adm03.manage` and `adm05.manage` exist in `iam.permissions` and guard RLS policies, but are assigned to **NO role template**. Only a user holding the wildcard `*` (`TENANT_ADMIN`) can execute actions guarded by them.
- `adm04.manage` does not exist in the database at all.

---

## C4. Coarse Administrative Checks in Java Services & Gateway

**[CONFIRMED: Static scan of `services/**` for `ROLE_ADMIN`, `ADMIN`, `SUPER_ADMIN`, and `COLLEGE_ADMIN`]:**

### Java Microservices Using Raw Role Strings
1. **Academic Calendar Service (`academic-calendar-service`):**
   - File: `CalendarController.java:427`
   - Check: `if ("SUPER_ADMIN".equals(role) || "ACADEMIC_ADMIN".equals(role) || "ADMIN".equals(role))`
2. **Batch Management Service (`batch-management-service`):**
   - File: `BatchController.java:474, 518, 597`
   - Check: `assertRole(userRole, "COLLEGE_ADMIN", "ACADEMIC_ADMIN", "DEPARTMENT_HEAD", "SUPER_ADMIN")`
3. **Attendance Service (`attendance-service`):**
   - File: `AttendanceController.java:641`
   - Check: `if ("SUPER_ADMIN".equals(role) || "ACADEMIC_ADMIN".equals(role) || "COLLEGE_ADMIN".equals(role) || "INSTITUTE_ADMIN".equals(role))`
4. **Learning Resource Service (`learning-resource-service`):**
   - File: `ResourceAccessPolicyService.java:136`
   - Check: `if ("SUPER_ADMIN".equalsIgnoreCase(userRole) || "ADMIN".equalsIgnoreCase(userRole))`
5. **Admissions Service (`admissions-service`):**
   - File: `AdmissionsPermissionEvaluator.java:22, 54`
   - Check: Checks role set `{"R01", "R02", "R03", "R04", "SUPER_ADMIN", "SYSTEM_ADMIN", "INSTITUTION_ADMIN", "COLLEGE_ADMIN"}` for administrative overrides.

### API Gateway Administrative Role Tier
- File: `services/api-gateway/.../JdbcIdentityResolver.java:291`
- Mapping: Maps `SUPER_ADMIN`, `TENANT_ADMIN`, `INSTITUTION_ADMIN`, and `COLLEGE_ADMIN` into an administrative tier that forwards trusted claims (`X-User-Role`, `X-User-Id`, `X-Tenant-Id`) downstream.

### Impact of Narrowing R04 Permission Keys
**[CONFIRMED]:** Narrowing `COLLEGE_ADMIN` (R04) permission keys in `iam.role_permissions` alone **WOULD NOT** restrict R04 in services checking `X-User-Role` via raw string equals (`assertRole(...)`). For instance, `batch-management-service` and `attendance-service` would continue allowing `COLLEGE_ADMIN` mutations regardless of the database permissions catalog until Java controller checks are refactored to check permissions.

---

## C5. Privileged Session Bypass (Static Code Scan)

**[CONFIRMED: Static scan of `frontend/src/lib/db.ts`, `frontend/src/app/api/**`, `services/**`, and PostgreSQL function definitions]:**

### Definition of Privileged Session in Database
**[CONFIRMED: `pg_proc` definition for `iam.is_privileged_session()`]:**
```sql
select current_user not in ('authenticated', 'anon')
```
Any connection running as `postgres`, `postgres.<project_ref>`, or `service_role` evaluates to `true`.

### Frontend Direct Database Pool (`frontend/src/lib/db.ts`)
- **File & Lines:** `frontend/src/lib/db.ts:11-58`
- **Credential Used:** `process.env.SUPABASE_USER` (configured as `postgres.azulbaxszsqxymqmlwfe`) and `process.env.SUPABASE_PASSWORD`.
- **Session State:** Connects directly via PostgreSQL wire protocol without issuing `SET LOCAL ROLE authenticated` or setting `request.jwt.claims`.
- **Database Identity:** The active user is `postgres` (PostgreSQL superuser).
- **Consequences:**
  1. `current_user` is `postgres` → `iam.is_privileged_session()` returns `true`.
  2. The `postgres` role has the `BYPASSRLS` attribute enabled in Supabase PostgreSQL → **all RLS policies on all schemas (`core`, `iam`, `cam`, `audit`, `plat`) are completely bypassed**.
  3. The trigger `iam.guard_role_assignment` executes line 3:
     `if iam.is_privileged_session() then return coalesce(new, old); end if;`
     → **Bypasses self-grant block, bypasses institute-scope checks, bypasses `can_grant_role` checks, and bypasses `adm09.write` scope validation**.

### Files Invoking Direct Privileged Database Queries
1. `frontend/src/app/api/college/faculty/route.ts:5` — Executes direct `INSERT`, `UPDATE`, and `DELETE` on faculty records. Applies manual `tenant_id` WHERE clause in application code, but DB-level RLS and triggers are bypassed.
2. `frontend/src/app/api/college/attendance/report/route.ts:4` — Direct SELECT queries bypassing attendance RLS.
3. `frontend/src/app/api/student/[id]/address/route.ts:6` — Direct student address queries.
4. `frontend/src/app/api/student/[id]/corrections/route.ts:6` — Direct correction queries.
5. `frontend/src/app/api/student/[id]/history/route.ts:5` — Direct history queries.
6. `frontend/src/app/api/student/admissions/documents/route.ts:6` — Direct admissions document queries.
7. `frontend/src/app/(dashboard)/college/faculty/page.tsx:5` — Server Component direct DB query.
8. `frontend/src/app/(dashboard)/college/timetable/page.tsx:5` — Server Component direct DB query.
9. `frontend/src/app/(dashboard)/student/page.tsx:17` — Server Component direct DB query.
10. `frontend/src/lib/auth/resolve-role.ts:4, 131, 187` — Direct profile and role queries.

### Service-Role Key in Frontend Server Code
**[CONFIRMED]:** Zero frontend server files consume `SUPABASE_SERVICE_ROLE_KEY` or `CAMPX_SUPABASE_SERVICE_ROLE_KEY`. The file `frontend/src/lib/documents/upload-payload-guard.ts:34` only inspects incoming request payloads to ensure clients do not submit credential field names. The service-role key is consumed exclusively by `services/student-services/document-service/.../DocumentStorageConfig.java` and maintenance scripts (`scripts/seed-staging.ts`, `scripts/demo/run_en002_live_orphan_cleanup.mjs`).

---

## C6. `INSTITUTION_ADMIN` (R03) Presence & Creation Across 49 Tenants

**[CONFIRMED: SQL query `SELECT count(DISTINCT tenant_id) FROM iam.roles WHERE code = 'INSTITUTION_ADMIN'` and `SELECT ra.tenant_id, count(*) FROM iam.role_assignments ra JOIN iam.roles r ON r.id = ra.role_id WHERE r.code = 'INSTITUTION_ADMIN' AND ra.deleted_at IS NULL GROUP BY ra.tenant_id`]:**

1. **Role Existence:** `INSTITUTION_ADMIN` exists as an active role entity in **all 49 DEV tenants**.
2. **Holder Counts:** **0 holders** exist across all 49 tenants in `iam.role_assignments`.
3. **First Institution Admin Creation:**
   - Under `iam.role_grant_rules`, **ONLY** `TENANT_ADMIN` can grant `INSTITUTION_ADMIN`.
   - `COLLEGE_ADMIN` cannot grant `INSTITUTION_ADMIN`.
   - `INSTITUTION_ADMIN` cannot grant `INSTITUTION_ADMIN`.
   - In tenant onboarding (`PostgresCollegeOnboardingRepository.java:191-200`), the onboarding process automatically provisions a `COLLEGE_ADMIN`, never an `INSTITUTION_ADMIN`.
   - Result: In the current system, an `INSTITUTION_ADMIN` can only be appointed by the single user holding `TENANT_ADMIN` in `DEV_TEST_TENANT` or via direct superuser (`postgres`) script insertion.

---

## C7. `frontend/src/lib/auth/resolve-role.ts` Routing & Misrouting Risks

**[CONFIRMED: Code inspection of `frontend/src/lib/auth/resolve-role.ts:336-396`]:**

### Authoritative Role Resolution Mapping (Live Flow)
| Role Code Pattern in DB | Mapped Frontend `UserRole` | Portal Desktop Bucket |
| :--- | :--- | :--- |
| `SUPER_ADMIN` (in `plat.platform_admins`) | `platform_admin` | `/platform/*` |
| `COLLEGE_ADMIN`, `INSTITUTION_ADMIN`, `TENANT_ADMIN`, `PRINCIPAL`, `ACADEMIC_ADMIN` | `college_admin` | `/college/*` |
| `REGISTRAR`, `R31` | `registrar` | `/college/lifecycle` (Registrar Desk) |
| `FACULTY`, `HOD`, `DEAN`, `TEACHER`, `SECTION_COORDINATOR` | `faculty` | `/faculty/*` |
| `STUDENT` | `student` | `/student/*` |
| Any other role (e.g. `AUDITOR`, `FINANCE_MANAGER`, `HR_MANAGER`, etc.) | **Unmapped** | **Fails closed (`no_role_assigned`)** |

### Misrouting Vulnerabilities
1. **Misrouting of Administrative Roles:** Both `INSTITUTION_ADMIN` (R03) and `PRINCIPAL` (R05) are collapsed into `college_admin`. An Institution Admin sees college-level desks instead of institute-level governance desks.
2. **Test Session Mode (`isTestSessionAllowed()`):**
   - Line 236: `else if (rawRole.includes('ADMIN') && rawRole !== 'SUPER_ADMIN') { effectiveRole = 'college_admin'; }`
   - In test mode, ANY role containing "ADMIN" (e.g., `SYSTEM_ADMIN`, `ADMISSIONS_ADMIN`, `EXAM_ADMIN`) is automatically misrouted to `college_admin`.

---

## C8. True Source of Truth for IAM Functions (`test_rbac.sql`)

**[CONFIRMED: Inspection of `database/migrations/Scripts_V3.0/test_rbac.sql` and `pg_proc`]:**

1. **Is `test_rbac.sql` applied to DEV as a migration?**
   - **YES.** The function signatures and trigger definitions currently running in PostgreSQL namespace `iam` (`has_permission`, `can_grant_role`, `guard_role_assignment`, `grant_scopes`) exactly match the source text in `database/migrations/Scripts_V3.0/test_rbac.sql:100-195`.
2. **Comparison with earlier files:**
   - In `database/migrations/001_initial_schema.sql`, `has_permission` was a basic stub.
   - `test_rbac.sql` is currently the **true operational definition** in the live database for `iam.has_permission`, `iam.can_grant_role`, and `iam.guard_role_assignment`.
   - Architectural hazard: Despite its filename (`test_rbac.sql`), it contains core production security DDL rather than disposable unit test assertions.
