# IADM Findings Register

**Created:** 2026-11-21  
**Scope:** IADM Step 0 and Waves A–D (DEV only)  
**Companion:** `docs/audits/rbac-implementation/FINDINGS_REGISTER_OPEN.md`

---

## Security findings

### FINDING-IADM-SEC-01 — `plat.assign_institution_admin` trusted `p_assigned_by` (CRITICAL → FIXED)

- **Plain language:** The database function that lets Super Admin (R01) / platform admins appoint an Institution Admin (R03) trusted a caller-supplied UUID instead of the real login identity (`auth.uid()` = Supabase authenticated user id).
- **CONFIRMED exploit (pre-fix):** As College Admin (R04) `70a8ffae-b5ad-4c61-a401-16f220893df4` with `SET ROLE authenticated` and JWT `sub` = that user, calling `plat.assign_institution_admin(tenant, target, platform_admin_id)` returned `VULNERABLE:ae37117f-…` and inserted an INSTITUTION_ADMIN assignment.
- **Fix:** Migration `20261121000100_iadm_assign_institution_admin_auth_uid.sql` — caller = `auth.uid()`; `p_assigned_by` ignored; missing `auth.uid()` fails closed.
- **Post-fix:** Same spoof → `REFUSED:Access Denied: Only platform administrators can assign INSTITUTION_ADMIN`. Legitimate platform admin `54e8308c-…` → allowed (assignment cleaned).
- **Cleanup:** Unauthorized and leftover INSTITUTION_ADMIN rows for `ba77b8f7-…` deleted; user remains COLLEGE_ADMIN only.
- **Status:** **FIXED in DEV** [CONFIRMED].

### FINDING-IADM-SEC-02 — HttpContext attribute leakage (identity / body) — FIXED (Wave E2)

- **Plain language:** JDK `com.sun.net.httpserver` stores `HttpExchange.setAttribute` values on the shared `HttpContext` map, so one request’s data can leak into the next on the same path.
- **Phase 4 fix pattern:** `AttendanceController` finally removes `campx.cachedRequestBody` / `campx.cachedRequestBodyBytes`.
- **Wave E2 finally-remove (CONFIRMED):**
  - `SubjectController.java` / `AnalyticsController.java` (earlier)
  - `CurriculumController.java` — removes `tenantId`, `userId`, `traceId`, `startTime`, `method`, `path` — **HIGH cleared**
  - `InstituteAdminController.java` — removes `campx.request.body`
  - `CollegeAdminController.java` — removes `campx.request.body`, `campx.auth.result`
  - `CourseController.java` — removes `campx.request.body`, `campx.auth.result`
- **Status:** **FIXED** [CONFIRMED] Wave E2 2026-11-21.

---

## Non-security / tracking

### FINDING-IADM-DOC-01 — Architecture design outside git repo

- Canonical `IADM_Architecture_Design.md` v2.0 lives at `D:\CampXSync\Documentation\Platform_InstituteAdmin\` (NOT FOUND under `CampXSync_V2.0/docs/`). Used for Step 0 reconciliation [CONFIRMED path].

### FINDING-IADM-ARCH-01 — First College Admin Auth link (SEC-11)

- Architecture notes onboarding may mint a profile without a linked Supabase Auth user. Tracked for IADM-01-US-003; do not treat current monolith as the approved protocol.
- **Wave E (2026-11-21):** `TenantLifecycleService.onboardCollege` requires Auth-linked admin via `plat.is_auth_linked_user`. Authoritative path is `/api/v1/iadm/01` (:8099). Monolith `:8081` onboard remains legacy shim only.
- **Status:** **FIXED** on IADM-01 (E0/E1-A).

### FINDING-IADM-RBAC-01 — PLATFORM_ADMIN / SYSTEM_ADMIN not in role_templates (MEDIUM)

- **Plain language:** Super Admin (R01) and System Admin (R02) are not rows in `iam.role_templates`. Migration `20261121000600` inserted `role_template_permissions` for codes `PLATFORM_ADMIN` / `SYSTEM_ADMIN`, but `iam.resync_system_roles()` only seeds codes that exist in templates — so those template rows do not become live `iam.role_permissions`.
- **CONFIRMED:** Live grants for `iadm0*` appear on `INSTITUTION_ADMIN` and `AUDITOR` only after resync. Platform admins continue to use `iam.is_platform_admin()` in Java/`UserSecurityContext`.
- **Action:** Do not invent PLATFORM_ADMIN/SYSTEM_ADMIN system roles without an owner design decision. Prefer `is_platform_admin()` for R01/R02 until matrix names a template code.
- **Status:** OPEN (documented; no silent role invention).
