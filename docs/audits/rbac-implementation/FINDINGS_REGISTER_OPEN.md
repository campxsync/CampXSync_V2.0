# CampXSync RBAC Open Findings Register

**Status:** ACTIVE  
**Last Updated:** 2026-11-21  
**Context:** Captured during Phases 0–4 of Enterprise RBAC (IADM & CADM) implementation in DEV.  
**IADM Wave companion:** Also see `docs/iadm/FINDINGS.md` (SEC-01 fixed; SEC-02 partial; RBAC-01 platform role_template gap).

---

## 1. Resolved Findings (Phase 4)

### FINDING-RBAC-01: Ambiguous Test User Role in Dev Database (Conflated Dev Admin Identity)
- **Impacted Components:** `api-gateway` (`JdbcIdentityResolver.java`), `plat.platform_admins`, `iam.role_assignments`.
- **Description:** Test user `ba77b8f7-ab32-46bd-85d2-5aade3526880` was present in `plat.platform_admins` as `SUPER_ADMIN` and in `iam.role_assignments` as `COLLEGE_ADMIN` and `INSTITUTION_ADMIN`. Because `JdbcIdentityResolver` checks `plat.platform_admins` first, all calls by this user resolved to `SUPER_ADMIN`, masking tenant college admin authorization rules.
- **Resolution (Phase 4 Task 4.7):** Applied migration `20261120000400_rbac_phase4_verification_and_patch.sql`:
  1. Removed `ba77b8f7` from `plat.platform_admins`. The dedicated platform admin `54e8308c-ada3-4065-a9fe-51814af6b971` serves as the sole platform admin.
  2. Removed duplicate `INSTITUTION_ADMIN` assignment from `ba77b8f7`, making it solely `COLLEGE_ADMIN`.
  3. Seeded dedicated R03 Institution Admin persona `c03a0000-0000-4000-8000-000000000003` (`institution.admin.demo@campxsync.test`) with pure `INSTITUTION_ADMIN` role.
- **Status:** **RESOLVED** [CONFIRMED: `verify_phase4_rbac.sql:28-40`].

### FINDING-RBAC-04: IADM-04 Billing Endpoints Lack Authorization Annotations
- **Impacted Components:** `services/administration-services/institute-admin-service` (`InstituteAdminController.java`).
- **Description:** Identified in Phase 0.5: billing endpoints (plans, subscriptions, invoices, transactions) executed without checking caller role or permissions, allowing any administrative tier caller to invoke them.
- **Resolution (Phase 4 Task 4.3):**
  1. Patched `InstituteAdminController.java`: enforced `adm01.manage` on all billing routes (`handleCreateSubscription`, `handleListSubscriptions`, `handleTransitionSubscription`, `handleIssueInvoice`, `handleListInvoices`, `handleReconcileTransaction`, `handleListTransactions`).
  2. Enforced platform-admin only (`PLATFORM_ADMIN`, `SUPER_ADMIN`, `R01`, `R02`) for creating and publishing commercial plans (`handleCreatePlan`, `handlePublishPlan`).
  3. Deployed comprehensive test suite `BillingRbacSecurityHttpTest.java` verifying 403 Forbidden for College Admin and Principal, 200/201 for Institution Admin holding `adm01.manage`, and 403 for non-platform admin creating plans.
- **Status:** **RESOLVED** [CONFIRMED: `BillingRbacSecurityHttpTest.java` 4/4 passing].

---

## 2. Open Findings

### FINDING-RBAC-02: Hardcoded Route Destination Scheme in GatewayConfig
- **Impacted Components:** `api-gateway` (`GatewayConfig.java`).
- **Description:** While most services map to `http://localhost:<PORT>`, ports 8096 (Lifecycle) and 8098 (Documents) were hardcoded to `http://127.0.0.1:<PORT>`. Tests checking exact URLs required adjustment.
- **Severity:** LOW.
- **Current State:** Mitigated in tests; recommendation is to normalize all routes to use configurable host property (e.g., `${DOWNSTREAM_HOST:localhost}`).
- **Owner Decision Pointer:** Architecture standard.

### FINDING-RBAC-03: Hardcoded Non-Existent Tenant in Legacy Integration Test
- **Impacted Components:** `services/administration-services/institute-admin-service/src/test/.../PostgresCollegeRepositoryIntegrationTest.java`.
- **Description:** Test had hardcoded tenant UUID `0c914bb2-f63b-472c-a99a-39977112935d` which did not exist in dev database.
- **Severity:** LOW.
- **Current State:** Resolved in test scope by querying dynamic staging tenant via `TestEnvironmentGuard.verifySafeStagingTenant(...)`.

### FINDING-RBAC-05: Unrouted Roles in Frontend Routing Table
- **Impacted Components:** `frontend/src/middleware.ts`, `frontend/src/lib/auth/resolve-role.ts`.
- **Description:** Frontend portal currently recognises 5 core desk paths (`/college/lifecycle`, `/college/documents`, `/faculty/advisees`, `/student/lifecycle`, `/student/documents`). The other 34 enterprise roles fail closed to login/unauthorized.
- **Severity:** LOW.
- **Current State:** Fail-closed is secure and approved in Matrix §22.3 / §22.5.
- **Recommended Fix:** Add dedicated portal desk views as subsequent waves onboard new roles.
- **Owner Decision Pointer:** Matrix §22.5 Accepted Risks.

### FINDING-RBAC-06: Direct Database Connection (`@/lib/db`) in Frontend BFF Routes
- **Impacted Components:** `frontend/src/app/api/**`.
- **Description:** Architectural deviation where several Next.js BFF routes query PostgreSQL directly rather than routing through API Gateway (`:8080`).
- **Severity:** MEDIUM.
- **Current State:** Tracked in activity report; non-blocking for RBAC Phase 4.
- **Recommended Fix:** Transition remaining BFF routes to call API Gateway (`:8080`).
- **Owner Decision Pointer:** `AGENTS.md` §1 & Session Standing Rule.

### FINDING-RBAC-07: Single Platform Admin Appointing Institution Admin (Two-Person Quorum Pending)
- **Impacted Components:** `database/migrations/20261120000300_rbac_phase3_code_alignment.sql` (`plat.assign_institution_admin`).
- **Description:** Matrix §21.7 proposes a two-person quorum for appointing R03 Institution Admin. Current implementation executes via single platform administrator function `plat.assign_institution_admin(...)`.
- **Severity:** LOW.
- **Current State:** **FIXED in DEV** 2026-11-21 (IADM-03-US-009). Quorum table `plat.institution_admin_quorum_requests` + `plat.platform_admin_track`; IADM-03 request/decide APIs; IADM-01 `appoint-admin` opens quorum only (no single-signer assign). Live test `Iadm03QuorumLiveDevTest` PASS. Spoof hole previously FIXED (`20261121000100`).
- **Owner policy (OWNER-DEC-TP-01):** Quorum = one **R01** + one **R02**, requestor ≠ approver, appoint and remove.
- **Owner Decision Pointer:** OWNER-DEC-TP-01; `docs/iadm/FINDINGS.md`.

### FINDING-IADM-SEC-02: HttpContext attribute leakage outside AttendanceController
- **Impacted Components:** SubjectController (fixed), AnalyticsController (fixed); InstituteAdminController, CollegeAdminController, CourseController, CurriculumController (open).
- **Description:** JDK HttpServer shares HttpContext attributes across requests. Curriculum stores `tenantId`/`userId` (HIGH).
- **Severity:** HIGH (Curriculum); MEDIUM (others).
- **Current State:** Identical-pattern fixes applied to Subject/Analytics; others logged in `docs/iadm/FINDINGS.md`.
- **Owner Decision Pointer:** IADM kickoff Step 0.2.
