# IADM-03 S0 Verification Report (read-only)

**Story:** Produce a read-only verification report of what already exists for identity and access governance before anything is changed (`IADM-03-US-001`)  
**Date:** 2026-11-24  
**Environment:** Development Supabase PostgreSQL only (`azulbaxszsqxymqmlwfe` — CampXSync-Architecture-Dev)  
**Branch:** `feature/java-backend-integration` (HEAD at inspection: `9bfef65`)  
**Database writes in this step:** **None** (SELECT-only inspection via Supabase MCP + repository reads)  
**Status:** Awaiting owner review of this report and `S0_DECISION_PACK.md` before any S0 build (`IADM-03-US-002`…`007`) or S1

---

## 1. Plain-language verdict

Identity and access **data already lives** in schema `iam` (Identity and Access Management) on the DEV database, and much of the **runtime behaviour** still lives inside the **institute-admin monolith** on port **8081** under `/api/v1/admin`. There is **no** dedicated IADM-03 (Platform Identity & Access Governance) Java service and **no** gateway prefix `/api/v1/iadm/03` today.

1. **Reuse, do not rebuild:** the R03 (Institution Admin) appointment database function with its security fix; the Student (R25) assign/revoke endpoints; the Registrar (R31) delegation key `STM-07.ACCOUNT.A` and its active-check; the live R03 persona `maharani.campx@gmail.com` on institute MAC; the 56-cell persona matrix harness from IADM-02.
2. **Move into IADM-03 (proposed after owner decisions):** institute-admin IAM surfaces for user profiles, role catalogue/templates, general grants, delegations, access reviews — following the IADM-01 / IADM-02 “separate service + gateway prefix” pattern.
3. **Not found / gaps:** no IADM-03 module; no `/api/v1/iadm/03` route; no `access_control` schema; no `iam.access_requests` table; no IADM-03 UI desk; exact string “No profile for that email” is **not** in current code (root cause of the *class of failure* is documented below); gateway still normalises Institution Admin (R03) to `college_admin` (frontend was fixed; gateway was not).
4. **Claims today:** the API Gateway (port 8080) validates the Supabase JWT, then **re-resolves identity from PostgreSQL** (`JdbcIdentityResolver`) with a **5-minute in-memory cache**. Cache invalidation helpers exist but are **not called** from grant/revoke paths. Permission checks in Java services use `iam.has_permission(...)` under RLS session context — not a per-request call to IADM-03 (which does not exist yet).
5. **Do not start coding** until the owner answers the open items in `S0_DECISION_PACK.md` (especially P1, D1–D6, D11, D13, D15, and confirmation of the PROPOSED defaults).

**RBAC v3.0 Compliant:** **not** claimed. Design-aligned only.

---

## 2. Inputs read (order)

| # | Input | Path / location | Note |
|---|---|---|---|
| 1 | IADM-03 user stories (40) | `docs/user-stories/CampXSync_IADM-03_User_Stories.csv` | Slices S0–S4; 11 TECH / 29 USER; US-038/039 PARKED |
| 2 | IADM-03 detailed blueprint V1.4 | `D:\CampXSync\Documentation\Platform_InstituteAdmin\MicroServices\CampXSync_IADM-03_Detailed_Blueprint_V1_4.md` | Design source of truth; PNG ignored |
| 3 | Governance | `AGENTS.md`, `docs/project-control/PROGRESS.md`, `docs/prompts/NEXT_TASK.md`, `docs/reports/COMPLETION_REPORT.md`, `docs/AGREEMENT_V2.1.md` | Agreement binds UI → Gateway → Java → Supabase |
| 4 | IADM architecture v2.0 | `D:\CampXSync\Documentation\Platform_InstituteAdmin\IADM_Architecture_Design.md` | §5.3 IADM-03 still **PENDING** / provisional |
| 5 | Enterprise RBAC Matrix v3.0 FINAL | `D:\CampXSync\Documentation\OverAllArchitecture\CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` (repo copy under `docs/architecture/Security/`) | §§8, 21.1, 21.2, 21.7, 22.1; §21 opening note: IADM-03 row **provisional** |
| 6 | Finished IADM-01 | `services/administration-services/iadm-01-tenant-lifecycle-service/` (:8099, `/api/v1/iadm/01`) | Pattern + R03 onboarding invite/profile/appoint |
| 7 | Finished IADM-02 | `services/administration-services/iadm-02-platform-config-service/` (:8100, `/api/v1/iadm/02`) | Consumer read + `iam.delegation.max_validity_days` = 90 |
| 8 | Institute-admin / IAM Path C | `services/administration-services/institute-admin-service/` (:8081) | Profiles, roles, R25, delegations |
| 9 | API Gateway | `api-gateway/` (:8080) | Routes, JWT, identity resolution |

---

## 3. Mandatory reuse-item confirmation (Phase 0)

| Reported item | Evidence today | Disposition |
|---|---|---|
| **R03 appointment DB function + security fix** | DEV function `plat.assign_institution_admin(p_tenant_id, p_user_id, p_assigned_by)` exists; definition uses `auth.uid()` (hardened). Called from IADM-01 `TenantLifecycleService.appointR03`. Migration keep: `20261121000100_iadm_assign_institution_admin_auth_uid.sql` (K1). | **REUSE** — do not rebuild |
| **Student (R25) assign/revoke in institute-admin** | `POST /api/v1/admin/internal/roles/student-assign` and `…/student-revoke`; gateway prefix `/api/v1/admin/internal/roles` → `:8081`; `StudentRoleAssignmentService` writes `iam.role_assignments`. | **REUSE** — keep in place for STM-07; IADM-03 must not invent a parallel R25 path |
| **Registrar (R31) key `STM-07.ACCOUNT.A` + grant check** | `DelegationAuthorizationService` allow-list includes `STM-07.ACCOUNT.A`; `GET /api/v1/admin/internal/delegations/active-check`; student-service `DELEGATION_ACCOUNT_KEY`; Path C base URL `http://127.0.0.1:8081`. Backed by `iam.delegations` (1 row on DEV). | **REUSE** — D15 must reuse this check, not a per-request IADM-03 call |
| **Dedicated R03 test persona** | Live DEV: `maharani.campx@gmail.com`, status ACTIVE, role `INSTITUTION_ADMIN`, tenant MAC `72ecbfe3-2077-41b5-8a20-b6d3e045311b`. | **REUSE** live persona. **Gap:** `AuditPersona` / test-session has **no** `institution_admin` persona (only platform_admin, college_admin, registrar, faculty, student). Headed demos today use real login or invite email for R03. |
| **56-cell persona matrix** | IADM-02 harness `scripts/dev/iadm02_persona_matrix_56.mjs`; evidence `docs/iadm/iadm-02/evidence/PERSONA_MATRIX_56.md` (7 keys × 8 personas = 56; 26 allow / 30 deny). | **REUSE** harness; must still pass after every IADM-03 slice |

If the owner expected a JWT test-session R03 slot as “dedicated persona,” that slot is **not found** — ask before inventing a replacement.

---

## 4. Blueprint / architecture disposition (REUSE / MOVE / NEW / NOT FOUND)

| Item | Evidence today | Disposition |
|---|---|---|
| Separate IADM-03 Java service | No `iadm-03-*` under `services/`; `identity-services/identity-service/` is scaffold-only (`.gitkeep`) | **NEW** service shell (copy IADM-01/02 pattern) after D4 |
| Gateway prefix `/api/v1/iadm/03` | `GatewayConfig` has `/api/v1/iadm/01` → `:8099`, `/api/v1/iadm/02` → `:8100`; **no** `/api/v1/iadm/03` | **NEW** route — **no clash** with `/api/v1/admin` or siblings |
| Service port | Stack starts 8080, 8081, 8099, 8100, …; **8101** not reserved in `start_stack.ps1` | **NEW** — candidate **8101** (owner confirm in decision pack); do not invent if owner prefers another free port |
| Schema label `access_control` | Schema **not found** on DEV (`pg_namespace` has `iam`, `plat`, `audit`, `cfg` only) | Architecture label only → **use `iam.*`** |
| IAM tables | 19 tables in `iam` (profiles, roles, permissions, assignments, templates, hierarchy, grant rules, delegations, access_reviews(+items), api_clients(+grants), groups, department_access, transition_*) | **REUSE** |
| Access-request store | `iam.access_requests` **not found** | **NEW proposal only** (D2) — no create without owner approval |
| Role / permission catalogues | `iam.role_templates` **39** rows; `iam.permissions` **577** rows; mappings via `role_template_permissions` / `role_permissions` | **REUSE** |
| User profiles | `iam.user_profiles` **20** active rows; create/list/status in institute-admin `UserProfileRepository` + IADM-01 first-admin helper `plat.iadm01_create_first_admin_profile` | **REUSE** storage; **MOVE** institute-admin profile APIs into IADM-03 for institute-level admins (after P1) |
| Role assignments / grants | `iam.role_assignments` **24** rows; columns include `granted_by`, `valid_from`/`valid_to`, `college_id` | **REUSE** |
| Delegations | `iam.delegations` exists; Path C active-check on `:8081` | **REUSE** store + check |
| Access reviews | `iam.access_reviews` / `access_review_items` exist; **0** review rows | **REUSE** tables; Java ownership **MOVE**/build under IADM-03 |
| Platform dual identity store | `plat.platform_admins`: 2× `SUPER_ADMIN`, 1× `SYSTEM_ADMIN` ACTIVE | **REUSE** (architecture inventory fact; redesign not in this module) |
| `adm03.*` family | Keys exist; `adm03.manage` / `.view.own` / `.write.own` = **0** template grants; view/write still on some templates; RLS on `iam.api_clients` references `adm03.*` | **Keep dormant** (IADM-D5 / §21.7) — denial tests only for R27 creation |
| Shared durable audit | `audit.log_event` exists; IADM-01 same-TX fail-closed pattern | **REUSE** IADM-01 pattern (D11) |
| IADM-02 delegation default | `cfg.setting_definitions.setting_key = 'iam.delegation.max_validity_days'`, `default_value` **90**, type INTEGER; consumer exposes key | **REUSE** via IADM-02 consumer; IADM-03 must not hard-code |
| Frontend IADM-03 desk | No Platform Identity / Access Governance desk under `frontend/src/app` | **NEW** after S0 |
| Notifications | Best-effort posture elsewhere; invitations via Supabase Auth Admin in IADM-01 | **REUSE** posture; no email service invention |

---

## 5. Gateway and port map (verified)

| Prefix / port | Target today | Clash with proposed `/api/v1/iadm/03`? |
|---|---|---|
| Gateway `:8080` | Ingress | — |
| `/api/v1/iadm/01` → `:8099` | IADM-01 Institute & Tenant Lifecycle | No |
| `/api/v1/iadm/02` → `:8100` | IADM-02 Platform Configuration | No |
| `/api/v1/admin` → `:8081` | Institute Admin / IAM Path C (profiles, roles, R25, delegations) | No (different tree); cutover plan required when ownership moves |
| `/api/v1/admin/internal/roles` → `:8081` | R25 assign/revoke | Keep |
| `/api/v1/admin/internal/delegations` → `:8081` | Delegation active-check | Keep until D15 says otherwise |
| `/api/v1/iadm/03` | **Not registered** | **Free** |
| Candidate service port **8101** | **Not in** `start_stack.ps1` target list | Available candidate |

Evidence: `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java`; `scripts/dev/start_stack.ps1`.

---

## 6. DEV schema & RLS (SELECT evidence)

**Project:** `azulbaxszsqxymqmlwfe` only.

### 6.1 `iam` tables (all 19)

| Table | RLS enabled | FORCE RLS | Policy count |
|---|---|---|---|
| `user_profiles`, `roles`, `role_assignments`, `role_permissions`, `delegations`, `access_reviews`, `access_review_items`, `api_clients`, `api_client_grants`, `user_groups`, `user_group_members`, `department_access` | yes | **no** | 4 each (typical) |
| `permissions`, `role_templates`, `role_template_permissions`, `role_grant_rules`, `role_hierarchy`, `transition_rules` | yes | **no** | 2 |
| `transition_log` | yes | **no** | 1 |

**Finding:** All **19** `iam` tables have ENABLE RLS but **FORCE RLS = false** (`force_on=0`, `force_off=19`). Changing FORCE RLS is a **proposed migration (D2)**, not done in S0.

### 6.2 Demo institute fixture (IADM-01 reuse)

| Institute | id | status |
|---|---|---|
| MAC (Maharani College) | `72ecbfe3-2077-41b5-8a20-b6d3e045311b` | ACTIVE |
| MAC181627 | `14b6ced4-e312-4463-9edc-f343c65ae5d8` | ARCHIVED |

### 6.3 Counts (non-deleted)

profiles 20 · role_assignments 24 · delegations 1 · permissions 577 · role_templates 39 · access_reviews 0 · api_clients 1

### 6.4 Functions confirmed present

`plat.assign_institution_admin`, `plat.is_auth_linked_user`, `plat.iadm01_auth_user_id_by_email`, `iam.has_permission`, `iam.is_platform_admin`, `audit.log_event`

---

## 7. “No profile for that email” — root cause and today’s profile path

### 7.1 Exact string

The literal message **“No profile for that email”** is **not found** in the current repository (Java, TypeScript, or docs search). The blueprint §11 still names it as the failure mode that must not return silently.

### 7.2 Root cause of the *class of failure* (confirmed)

Earlier IADM invite/appoint-by-email behaviour (FINDING-IADM-REWORK-02, Wave reports):

1. Operator entered an email for Institution Admin (R03).
2. Code looked up **`iam.user_profiles` by email** and required **`plat.is_auth_linked_user`**.
3. If the person had **no Auth-linked profile yet**, appoint failed closed with codes such as `IADM01_INVITE_USER_NOT_FOUND` / `IADM01_AUTH_USER_REQUIRED` — i.e. “we only appoint people who already have a profile,” with no create/invite step offered.

That is the operational root cause behind the blueprint wording.

### 7.3 How profiles are created **today**

| Path | What it does |
|---|---|
| **IADM-01 first R03 onboarding** (current) | Idempotent steps: `INVITE_LOGIN` → `CREATE_PROFILE` → `APPOINT_R03`. Invite/create Auth via `SupabaseAuthAdminClient.inviteOrCreateUser` (`/auth/v1/invite`, fallback `/auth/v1/admin/users`). Profile via `plat.iadm01_create_first_admin_profile`. Role via `plat.assign_institution_admin`. |
| **Institute-admin user APIs** | `UserProfileRepository.createUser` / list / status against `iam.user_profiles` under `/api/v1/admin/...` (monolith). Does **not** send Auth invitation by itself. |
| **STM-07 / R25** | Assigns role onto an **existing** linked user; does not own institute-admin profile invite. |

Profile `id` is the Supabase Auth subject UUID (user profiles are keyed by Auth user id).

### 7.4 How Supabase Auth invitations work in DEV

- Server-side only: `CAMPX_SUPABASE_URL` + `CAMPX_SUPABASE_SERVICE_ROLE_KEY` (never logged).
- Prefer **invite** (Supabase Auth sends the email); on failure, **admin createUser** with `email_confirm:false`; if user already exists, **link existing**.
- Invitation email is **not** sent by the notification service.
- If Auth Admin env is missing → `IADM01_AUTH_ADMIN_UNAVAILABLE` (503).

### 7.5 Related messages still in code

| Message / code | Where |
|---|---|
| `User profile not found: {userId}` / `ADM01_USER_NOT_FOUND` | IADM-01 / institute-admin exceptions |
| `No active user profile found for identity: {userId}` | Gateway `JdbcIdentityResolver` |
| `Login exists; institute profile is incomplete.` | IADM-01 profile step failure |
| Frontend `no_profile` access-denied copy | `access-denied/page.tsx` |

---

## 8. Claims contract and refresh (today) — input to D5 / D13

| Layer | Behaviour verified |
|---|---|
| **JWT** | Gateway `SupabaseJwtValidator`: signature, `sub`, `exp`; requires `app_metadata.tenant_id` for tenant users. Does **not** trust client `X-User-*` headers. |
| **Authoritative identity** | After JWT OK, `JdbcIdentityResolver` reads `plat.platform_admins` **or** `iam.user_profiles` + `core.tenants` + `iam.role_assignments`. |
| **Permission enforcement** | Domain services: `UserSecurityContext.checkPermission` → `SELECT iam.has_permission(?)` inside `executeInTransaction` (RLS + JWT claims config). |
| **Cache** | Gateway identity cache TTL = **5 minutes**. `JdbcIdentityResolver.invalidate(userId)` / `invalidateAll()` exist. |
| **Refresh after grant/revoke** | **No callers** of `invalidate` found outside the gateway class itself. Practical revocation visibility for gateway-resolved role headers ≈ **up to 5 minutes** unless process restart. JWT `app_metadata.role` (if present) is **not** rewritten by IADM/IAM grant code found in this inspection. |
| **R03 normalisation gap** | Frontend `resolve-role.ts` keeps `INSTITUTION_ADMIN` as `institution_admin`. Gateway `ResolvedIdentity.normalizeRole` still maps `INSTITUTION_ADMIN` → **`college_admin`**. Preserve frontend fix; gateway fold is a **known defect / D5 note** — do not “fix” silently without owner acknowledgment. |
| **IADM-03 on the path?** | **No.** Runtime does not call an IADM-03 service (module absent). Matches owner decision. |

---

## 9. IADM-01 second-approver flag (guard 3 / D9 context)

- JVM flag `campx.iadm01.r03.secondApproverRequired` defaults **off** (`Boolean.getBoolean` → false).
- When on, requester cannot approve own R03 appointment (`IADM01_SELF_APPROVE`).
- Matrix §21.1 guard 3 and parked D9 (two-person appointment) are **not** fully enforced in DEV — accepted risk per matrix §22.5 / owner PARKED for appointment.
- S0 does **not** change this flag.

---

## 10. Permission-key inventory relevant to IADM-03

| Family / key | Status on DEV |
|---|---|
| `adm01.*` | Present; used heavily by IADM-01 (`adm01.manage`, view, write, …) |
| `adm02.*` | Present (college user-ish legacy family) |
| `adm03.*` | Present; **dormant for R27 lifecycle** (`adm03.manage` on **0** templates) |
| `adm04.*` | Present (CADM-aligned) |
| `cfg.*` | IADM-02 keys including `cfg.consumer.read` |
| Dedicated IADM-03 governance keys (profile invite, access-request, review start, …) | **NOT FOUND** as a distinct family — capabilities either use legacy `adm*` or are unimplemented |

Exact new keys (if any) are **proposals only** in `S0_DECISION_PACK.md` (D3).

---

## 11. Frontend / screens

| Desk | Status |
|---|---|
| Platform institutes / onboarding (IADM-01) | Exists |
| Platform configuration (IADM-02) | Exists |
| Institution home (R03) | Exists (IADM-01) |
| Platform Identity & Access Governance desk | **NOT FOUND** |
| College pages that query `user_profiles` via `@/lib/db` | Documented architecture deviation — **do not extend**; new IADM-03 UI must use BFF → Gateway |

---

## 12. Tests & security keeps

| Item | Note |
|---|---|
| Context-leak / K2 keeps | Must remain after IADM-03 work |
| `plat.assign_institution_admin` auth.uid() (K1) | Confirmed on DEV |
| Cross-tenant pen / Cross-EN-010 | Historical pass on prior work; re-run affected suites after slices |
| 56-cell matrix | Must pass after every slice |
| Unit auth | `campx.internal.auth.enabled=false` in `@Before` for local unit tests (IADM-01/02 lesson) |

---

## 13. Story readiness after this report only

| Story | API-ready | Screen-ready | Demo-verified | Note |
|---|---|---|---|---|
| IADM-03-US-001 | n/a | n/a | **Report ready for owner** | This document |
| US-002…007 | no | no | no | Blocked until owner decisions + seed approval |
| US-008+ | no | no | no | Not started |

---

## 14. Stop gate

**STOP.** Zero database writes performed. No service created. No migrations proposed as applied.

Next: owner reviews `docs/iadm/iadm-03/S0_DECISION_PACK.md`, confirms PROPOSED defaults, answers OPEN items (or accepts RECOMMENDATIONs), then authorizes S0 build US-002…007 and the DEV seed.
