# IADM Findings Register

**Created:** 2026-11-21  
**Updated:** 2026-11-22 (IADM-01 Slice S2 colleges/handover)  
**Scope:** IADM Step 0 / Waves A–D (historical) and IADM-01 S0–S4 rework (DEV only)  
**Companion:** `docs/audits/rbac-implementation/FINDINGS_REGISTER_OPEN.md`

---

## IADM-01 rework findings (2026-11-22)

### FINDING-IADM01-DOC-01 — Blueprint files outside git repo (LOW)

- **Plain language:** `CampXSync_IADM-01_Detailed_Blueprint_V2.1.md` and `IADM_Service_Blueprint_Template_v1.md` live under `D:\CampXSync\Documentation\Platform_InstituteAdmin\`, not under `docs/user-stories/`.
- **Action:** Follow CSV + fixed decisions; use external blueprint as design reference.
- **Status:** OPEN (documentation location).

### FINDING-IADM01-SEC-01 — `core.tenants` / `core.colleges` RLS not FORCED (MEDIUM)

- **Plain language:** Row-level security is enabled but not forced, so table owners / bypass roles could skip policies.
- **CONFIRMED:** `relforcerowsecurity=false` [SQL 2026-11-22].
- **Action:** Do not change without owner approval (DDL). Logged only.
- **Status:** OPEN.

### FINDING-IADM01-AUD-01 — Platform audit search uses in-memory trail (HIGH)

- **Plain language:** `/platform/audit` search path calls `InstituteAdminDomainService.searchAudit*` which reads an in-memory list, not `audit.events`.
- **CONFIRMED:** `InstituteAdminDomainService.java` search methods.
- **Action:** IADM-01 mutations write `audit.events` in-transaction. US-025 / Ops search may need a dedicated `audit.events` reader — do not claim Platform audit page finds IADM actions until rewired.
- **Status:** OPEN.

### FINDING-IADM01-AUTH-01 — `plat.is_auth_linked_user` was missing after rollback (HIGH → FIXED in DEV)

- **Plain language:** Auth-link helper was removed with IADM DB rollback.
- **Fix:** Recreated in migration `20261122010000` (applied via MCP as `iadm01_lifecycle_foundation`).
- **Status:** FIXED in DEV [CONFIRMED SQL].

### FINDING-IADM01-UI-01 — Institution Admin (R03) portal routing conflated with College Admin (HIGH → FIXED)

- **Plain language:** Middleware previously treated `INSTITUTION_ADMIN` as college-admin.
- **Fix:** Dedicated `/institution` home, `institution_admin` role in resolve-role / role-policy / nav / middleware.
- **Status:** FIXED in code [CONFIRMED]. Owner must still confirm first invitee lands on `/institution` after opening the Supabase Auth invite email (test-session has no R03 persona yet).

### FINDING-IADM01-AUD-02 — Duplicate `IADM01_R03_APPOINTED` audit rows (LOW)

- **Plain language:** Appointment writes audit twice (step helper + appoint path).
- **CONFIRMED:** S1F institute `61e9b962-…` has two `IADM01_R03_APPOINTED` rows [SQL].
- **Action:** Deduplicate in a later polish; not a blocker for S1 gate.
- **Status:** OPEN.

### FINDING-IADM01-SUM-01 — Summary `colleges` count returned 0 while seed college exists (MEDIUM)

- **Plain language:** `GET /institutes/summary` reported `colleges: 0` while `INST-DEMO-001` has `COL-DEMO-001`.
- **INFERRED:** RLS on `core.colleges` under platform-scoped JWT (null tenant) may hide rows from the count query.
- **Action:** S2 added list/get SECURITY DEFINER helpers; summary counter not yet switched to a DEFINER aggregate. Still OPEN.
- **Status:** OPEN.

### FINDING-IADM01-AUD-03 — Duplicate `IADM01_R04_APPOINTED` audit rows (LOW)

- **Plain language:** Same double-write pattern as AUD-02 on College Admin appoint.
- **CONFIRMED:** College `a787519c-…` has two `IADM01_R04_APPOINTED` rows [SQL 2026-11-22].
- **Action:** Deduplicate with AUD-02 in polish.
- **Status:** OPEN.

### FINDING-IADM01-RLS-01 — `core.colleges` / `iam.roles` lack usable policies for IADM path (HIGH → mitigated)

- **Plain language:** RLS enabled with no SELECT/INSERT policies blocked college create/list/appoint until SECURITY DEFINER helpers.
- **Fix (DEV):** `plat.iadm01_create_college`, `update_identity`, `set_status`, `get_college`, `list_colleges`, `assign_college_admin`.
- **Status:** MITIGATED in DEV; proper RLS policies still preferred long-term (DDL needs owner approval).

### FINDING-IADM01-CADM-01 — CADM-01/02 absent; INTERIM handover acks (MEDIUM)

- **Plain language:** College Administration modules are not built. Handover records both acknowledgements with `cadm01_interim` / `cadm02_interim = true`.
- **Status:** OPEN (by design until CADM exists).

### FINDING-IADM-REWORK-02 — SUPERSEDED by IADM-01 US-002

- Prior default required an existing Auth-linked profile before invite. Owner task now requires backend Auth Admin invite/create first. Implemented via `SupabaseAuthAdminClient` (env-gated).
- **Status:** SUPERSEDED.

---

## Security findings (historical)

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

### OWNER-DEC-BG-01 — Break-glass (IADM-03-US-008) implemented (2026-11-21)

**Owner:** Niranjan V.G. — policy locked then authorized after `APPROVED IADM-03-US-009`.

| Gate | Rule |
|---|---|
| Request | Authenticated **R03, R04, R05, R31**, plus **R01, R02**. **Anonymous = no.** |
| Approve / open session | **R01 and R02 only**. R03/R04 may request and see status — **not** approve. |
| Session | Reason required; 15–240 min; `plat.break_glass_actions` audit; close/expire enforced. |
| Build | **completed** — migration `20261121001300`; `Iadm03BreakGlassLiveDevTest` PASS |

### OWNER-DEC-TP-01 — Two-person R03 appoint/remove (IADM-03-US-009) policy frozen (2026-11-21)

**Owner:** Niranjan V.G. — quorum = **R01 and R02**; **not yet authorized to implement**.

| Gate | Rule |
|---|---|
| Action | Appoint or remove Institution Admin (R03) |
| Quorum | **Two different people**: one **R01 (Super Admin)** and one **R02 (System Admin)** |
| Separation | Requestor ≠ approver (same person cannot complete both steps) |
| Build | **completed** 2026-11-21 — `APPROVED IADM-03-US-009` |

### FINDING-IADM-REWORK-01 — Student Account Provisioning left on Platform nav (2026-11-21)

- **Plain language:** Platform desk still shows **Student Account Provisioning** (`/college/account-provisioning`) — this is Student Module (STM) IAM linking, not IADM institute admin work.
- **Default:** Leave in place; labelled Student Module; later owner decision whether to remove from Platform nav.
- **Status:** OPEN (product decision).

### FINDING-IADM-REWORK-02 — Invite-by-email for R03 uses Auth-linked profiles only (2026-11-21)

- **Plain language:** Onboard / appoint Institution Admin (R03) “invite by email” resolves `iam.user_profiles` email and requires `plat.is_auth_linked_user` — no new invite tool. If the person has not completed Supabase Auth signup/link, appoint fails closed with `IADM01_INVITE_USER_NOT_FOUND` / `IADM01_AUTH_USER_REQUIRED`.
- **Status:** Documented default (Slice 1).

### FINDING-IADM-RBAC-01 — PLATFORM_ADMIN / SYSTEM_ADMIN not in role_templates (MEDIUM)

- **Plain language:** Super Admin (R01) and System Admin (R02) are not rows in `iam.role_templates`. Migration `20261121000600` inserted `role_template_permissions` for codes `PLATFORM_ADMIN` / `SYSTEM_ADMIN`, but `iam.resync_system_roles()` only seeds codes that exist in templates — so those template rows do not become live `iam.role_permissions`.
- **CONFIRMED:** Live grants for `iadm0*` appear on `INSTITUTION_ADMIN` and `AUDITOR` only after resync. Platform admins continue to use `iam.is_platform_admin()` in Java/`UserSecurityContext`.
- **Action:** Do not invent PLATFORM_ADMIN/SYSTEM_ADMIN system roles without an owner design decision. Prefer `is_platform_admin()` for R01/R02 until matrix names a template code.
- **Status:** OPEN (documented; no silent role invention).
