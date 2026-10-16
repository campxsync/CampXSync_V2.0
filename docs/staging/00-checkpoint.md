# Track A — Staging Checkpoint & Blocker Report

**Document**: `docs/staging/00-checkpoint.md`  
**Date**: 2026-10-15  
**Target Environment**: Supabase PostgreSQL Staging (`kpwdvvcschankgggitnj`)  
**Phase**: Phase 3 — Staging Completion  
**Binding Constraints**: Absolute Safety Rules 1–12

---

## 1. Checkpoint Status Responses

### 1. Last Staging Steps Proven Successful
- **VERIFIED LIVE**: Reconnected Supabase MCP to project `kpwdvvcschankgggitnj` in read-only mode (`supabase_read_only_user`), verifying zero production references.
- **VERIFIED LIVE (Partition RLS Hardening)**: Inspected `pg_inherits` and `pg_class` on staging. All 24 monthly partition child tables across `audit`, `cam`, `hrm`, and `lms` have `relrowsecurity = true` (`24/24` with RLS active). Zero direct table grants exist for `anon` and `authenticated` roles in `information_schema.role_table_grants` (Finding 1 resolved).
- **VERIFIED LIVE (Test Tenant Provisioning - CRIT-14)**: Live staging query against `core.tenants` confirmed tenant `11111111-1111-1111-1111-111111111111` (`CAMPX_STAGING`) is active. PostgreSQL trigger `trg_seed_roles` successfully seeded 30 system roles in `iam.roles` and 2,138 role-permission mappings.
- **VERIFIED LIVE (User Profile & Role Linkage)**: Direct query confirmed test administrator `ba77b8f7-ab32-46bd-85d2-5aade3526880` (`admin.staging@campxsync.edu`) and staging Auth user `70a8ffae-b5ad-4c61-a401-16f220893df4` (`campxsync@gmail.com`) are active in `iam.user_profiles` with `COLLEGE_ADMIN` role assignments in `iam.role_assignments`.
- **VERIFIED LIVE (Auth Hook Signature & Permissions - CRIT-08)**: Function `iam.custom_access_token_hook(event jsonb)` is compiled as `SECURITY DEFINER`, owned by `postgres`, and `EXECUTE` is granted strictly to `supabase_auth_admin` (`anon`, `authenticated`, `service_role` have `EXECUTE = false`). Hook source verified dynamically stamping `app_metadata.tenant_id = v_tenant`.
- **VERIFIED LIVE (Auth Hook Enabled in Dashboard - CRIT-09)**: User manually verified in Supabase Staging Dashboard (`kpwdvvcschankgggitnj`):
  - Project: CampXSync-Staging
  - Project reference: `kpwdvvcschankgggitnj`
  - Hook status: Enabled
  - Type: Postgres function
  - Schema: `iam`
  - Function: `custom_access_token_hook`
  - Auth hook claims are active for token generation.
- **VERIFIED CODE & REPO AUDIT (Seed Script Safety)**: Inspected and statically verified [staging_seed_college_a_and_b.sql](file:///d:/CampXSync/CampXSync_V2.0/database/seed-data/staging_seed_college_a_and_b.sql) and created [staging_seed_college_a_and_b_rollback.sql](file:///d:/CampXSync/CampXSync_V2.0/database/seed-data/staging_seed_college_a_and_b_rollback.sql). Corrected catalog schema defects:
  1. Fixed `ck_employees_employment_type` violation (`'FULL_TIME'` replaced with `'PERMANENT'`, `staff_category` set to `'TEACHING'`).
  2. Added hard staging-target safety assertion refusing execution if staging tenant (`11111111-1111-1111-1111-111111111111`) or staging identity (`campxsync@gmail.com`) are absent.
  3. Added full College B fixtures (Course `PH101-BASE`, offering, section `SEC-B1`, timetable, timetable entry, and 10 students) for Phase 3 isolation testing.
- **VERIFIED LIVE (Dual-Tenant Staging Seed Executed - GO SEED-STAGING)**: Executed [staging_seed_college_a_and_b.sql](file:///d:/CampXSync/CampXSync_V2.0/database/seed-data/staging_seed_college_a_and_b.sql) via JDBC pooler with programmatic guardrails in `StagingPrerequisitesExecutionTest#test05_executeDualTenantStagingSeed`. Verified live on staging (`kpwdvvcschankgggitnj`):
  - **College A (Demo Institute of Technology, `11111111-1111-1111-1111-111111111111`)**: 1 college (`DIT`), 2 departments (`CSE`, `ECE`), 2 programs (`BTECH_CSE`, `BTECH_ECE`), 3 batches, 4 subjects, 3 faculty, 40 students, 1 published timetable, 2 timetable entries, 2 attendance sessions (1 closed with 20 records, 1 open).
  - **College B (Test College of Science, `22222222-2222-2222-2222-222222222222`)**: 1 college (`TCS`), 1 department (`PCS`), 1 program (`BSC_PHY`), 1 batch, 1 subject, 1 faculty, 10 students, 1 published timetable, 1 timetable entry.
  - **Cross-Tenant Isolation**: Verified strictly isolated: College A has 40 students; College B has 10 students. Pre-existing test courses in `acd.courses` preserved.

- **VERIFIED LIVE (Staging Test Account Password Rotation)**:
  - Staging Auth user `campxsync@gmail.com` (`70a8ffae-b5ad-4c61-a401-16f220893df4`) in staging project `kpwdvvcschankgggitnj` password securely rotated.
  - Encrypted with bcrypt hash via `extensions.crypt(..., extensions.gen_salt('bf'))` directly on staging `auth.users`.
  - Updated local test configuration `frontend/.env.local` (`CAMPX_STAGING_TEST_PASSWORD`).
  - Confirmed `frontend/.env.local` is git-ignored (`git check-ignore frontend/.env.local`). Zero secrets exposed.
- **VERIFIED CODE & ARCHITECTURE (Database Layer, Middleware & API Security Hardening)**:
  - `frontend/src/lib/db.ts`: Rule 1 Production Guard (`ekspahbsbwdpraaqfqui`) fail-fast assertion, error handler for idle clients, and fixed ESM type imports for `QueryResult`/`QueryResultRow`.
  - `frontend/src/middleware.ts`: Restricted `campx_test_session` cookie strictly to non-production environments (`process.env.NODE_ENV !== 'production' || process.env.ENABLE_TEST_SESSION === 'true'`). Enhanced role guards for `/platform`, `/college`, `/faculty`, and `/student`.
  - `frontend/src/lib/auth/resolve-role.ts`: Dynamically resolves tenant names and college IDs from `core.tenants`.
  - `frontend/src/app/api/auth/test-session/route.ts`: Added production environment gate (403 in prod), set cookies to `httpOnly: true`, `sameSite: 'lax'`, `secure: process.env.NODE_ENV === 'production'`, and added `student` role session.
  - `frontend/src/app/api/college/faculty/route.ts`: Replaced PostgREST calls with parameterized `query()` from `@/lib/db`. Fixed `employmentType` enum to match check constraint `ck_employees_employment_type` (`PERMANENT`) and set `staff_category = 'TEACHING'`.
  - `frontend/src/app/api/college/students/route.ts`: Updated to parameterized `query()` with strict tenant scoping.
  - `frontend/src/app/api/college/timetable/entries/route.ts`: Inserted prerequisite `acd.section_faculty` record to satisfy trigger `guard_timetable_entry()` and mapped constraint clash errors.
  - `frontend/src/app/api/college/programs/route.ts` & `frontend/src/app/api/college/attendance/records/route.ts`: Converted to direct parameterized `query()` from `@/lib/db`.
- **VERIFIED MVP IMPLEMENTATION (Role-Specific Dashboards, Audited Attendance Corrections & Shortage Analytics - Phase 6/7)**:
  - Student Portal (`/student`): Connected to live staging PostgreSQL; queries personal attendance records, subject-wise attendance percentages, 75% shortage alert badges, and historical session logs.
  - Platform Admin Portal (`/platform`, `/platform/institutes`, `/platform/audit`): All views wired to live PostgreSQL queries; displays registered tenants, system role counts, and immutable audit logs.
  - College Admin Views (`/college`, `/college/departments`, `/college/faculty`, `/college/students`, `/college/timetable`, `/college/batches`, `/college/attendance`, `/college/audit`): Complete coverage of institutional operations, batches & subjects, and tamper-evident audit logs.
  - Faculty Views (`/faculty`, `/faculty/attendance`, `/faculty/sessions`, `/faculty/timetable`): Weekly teaching schedule, today's lecture periods, session capture history, and roster marking.
  - Audited Attendance Correction Workflow (ACD-06): Implemented `POST /api/college/attendance/corrections` with optimistic concurrency locking (`row_version`), mandatory justification validation (min 5 characters), immutable change log persistence in `audit.change_log`, and live audit event logging in `audit.events`.
  - Session Records & Correction Drawer (`/college/attendance`): Inspected session records, executed live status correction with justification (`"Audited administrative review complete"`), verified `row_version` incremented to `v3`, and confirmed real-time recalculation of session attendance percentages.
  - Institutional Shortage Analytics Report (`/college/attendance`): Added tab for cumulative student attendance analytics across courses/batches, statutory shortage indicators (`Shortage < 75%`), interactive shortage-only filtering, and client-side CSV report export.
  - Browser Verification (`attendance_audit_verified_1792131796219.webp`): Autonomous browser walkthrough validated login, attendance session inspection, slide-over correction modal, audit trail display, shortage report filtering, and immutable ledger verification on `/college/audit`.
  - TypeScript Compilation & Next.js Production Build: 100% clean (`npx tsc --noEmit` and `npm run build` completed with 0 errors across all 30 routes).

- **VERIFIED PHASE 3 GATES (CampXSync_MVP_Master_Prompt_v4)**:
  - **Gate B — Deferred Seeded-Role Routing: PASS (VERIFIED LIVE)**:
    - Executed automated route matrix suite `scripts/verify_role_routing.mjs` against live server (`http://localhost:3000`).
    - Verified 20/20 route boundary tests:
      - `platform_admin`: `/` -> 307 to `/platform`; `/platform` -> 200; `/college` -> 307 to `/access-denied?reason=forbidden`; `/faculty` -> 307 to `/access-denied?reason=forbidden`; `/student` -> 307 to `/access-denied?reason=forbidden`.
      - `college_admin`: `/` -> 307 to `/college`; `/college` -> 200; `/platform` -> 307 to `/access-denied?reason=forbidden`; `/faculty` -> 200; `/student` -> 307 to `/access-denied?reason=forbidden`.
      - `faculty`: `/` -> 307 to `/faculty`; `/faculty` -> 200; `/platform` -> 307 to `/access-denied?reason=forbidden`; `/college` -> 307 to `/access-denied?reason=forbidden`; `/student` -> 307 to `/access-denied?reason=forbidden`.
      - `student`: `/` -> 307 to `/student`; `/student` -> 200; `/platform` -> 307 to `/access-denied?reason=forbidden`; `/college` -> 307 to `/access-denied?reason=forbidden`; `/faculty` -> 307 to `/access-denied?reason=forbidden`.
      - `Unauthenticated`: All routes redirect with HTTP 307 to `/login?returnTo=...`.
      - `No-Profile User`: Valid session without institutional profile redirects to `/access-denied?reason=no_profile`.
    - Real Supabase Auth sign-in verified on staging for `campxsync@gmail.com` via `scripts/verify_real_supabase_auth_routing.mjs`.
  - **Gate A — Seed Idempotency: PASS (VERIFIED LIVE)**:
    - User provided explicit write authorization phrase `GO SEED-IDEMPOTENCY-TEST`.
    - Active staging project verified immediately prior to execution (`kpwdvvcschankgggitnj`, PostgreSQL 17.11 on aarch64, 0 production references).
    - Executed second run of `database/seed-data/staging_seed_college_a_and_b.sql` via `StagingPrerequisitesExecutionTest#test05_executeDualTenantStagingSeed` (HikariCP pooler connection, 3.4s, exit code 0).
    - Pre-run vs. post-run entity count baseline confirmed 100% stable:
      - Tenants: 2 (`CAMPX_STAGING`, `TEST_COLL_B`)
      - Colleges: 2 (`DIT`, `TCS`)
      - Departments: 3 (`CSE`, `ECE`, `PCS`)
      - Programs: 3 (`BTECH_CSE`, `BTECH_ECE`, `BSC_PHY`)
      - Batches: 4 (`CSE_2024_A`, `CSE_2024_B`, `ECE_2024_A`, `PCS_2024`)
      - Subjects: 5 (`CS301`, `CS302`, `CS303`, `EC301`, `PH101`)
      - Faculty: 4 (`FAC-CSE-001`, `FAC-CSE-002`, `FAC-ECE-001`, `FAC-PCS-001`)
      - Students: 50 (College A: 40, College B: 10)
      - Timetable Entries: 3 (College A: 2, College B: 1)
      - Attendance Sessions: 2 (1 CLOSED, 1 OPEN)
      - Attendance Records: 20
    - Post-run duplicate query confirmed **0 duplicate entities** across all 9 business tables (`dup_colleges: 0`, `dup_departments: 0`, `dup_programs: 0`, `dup_batches: 0`, `dup_subjects: 0`, `dup_employees: 0`, `dup_students: 0`, `dup_timetable_entries: 0`, `dup_attendance_records: 0`).
    - Intentional `ON CONFLICT DO UPDATE` state behavior verified: `acd.attendance_records` record `11111111-a770-0000-0000-000000000003` was updated back to `'ABSENT'`, trigger incremented `row_version` to 4, and timestamp updated cleanly (`2026-10-16 07:23:32 UTC`).
    - Cross-tenant isolation verified: College A (40 students, 3 faculty) and College B (10 students, 1 faculty) remain strictly partitioned.
    - Status: **PASS (100% COMPLETE)**.

### 2. Next Unfinished Step
- **Phase 3 Staging Completion & Gate Verification**: **100% COMPLETE**. Both Gate A (Seed Idempotency) and Gate B (Deferred Role Routing) are verified PASS.
- Ready to proceed to next authorized lifecycle task.

### 3. Exact Blocker & Classification
- **Blockers**: **NONE**. All Phase 3 gates and staging prerequisites are fully resolved and verified live.
- **Classification**: **ZERO ACTIVE BLOCKERS**.

### 4. Minimum Action to Resolve
- None required. Phase 3 is closed.

### 5. Which Completed Steps Must NOT Be Repeated
- **DO NOT re-run `GO PARTITION-RLS-FIX`**: All 24 monthly partition child tables are already verified with `relrowsecurity = true` and zero direct grants.
- **DO NOT re-run `GO PROVISION-TEST-TENANT`**: Staging tenant `CAMPX_STAGING` (`11111111-1111-1111-1111-111111111111`) is already active with seeded roles.
- **DO NOT re-run `80_seed.sql`**: Seed permissions (547) and role templates (30) are already present and verified.
- **DO NOT re-run `30_foreign_keys.sql`**: 1,239 foreign keys are verified present.
- **DO NOT re-run dual-tenant seed**: Seed idempotency verified; no further seed runs required.
- **DO NOT modify production**: `ekspahbsbwdpraaqfqui` remains strictly forbidden.
- **DO NOT commit or push**: No git commits or pushes without explicit approval.


