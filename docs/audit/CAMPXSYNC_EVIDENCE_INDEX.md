# CampXSync — Master Evidence Index & Audit Baseline

**Document Identifier**: `docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md`  
**Audit Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Governing Baseline**: Read-Only Audit across Git HEAD (`ReviewBranch` commit `b307149`)  
**Target Environments Audited**:
- Repository Root: `d:\CampXSync\CampXSync_V2.0`
- Staging Supabase Reference: `kpwdvvcschankgggitnj` (PostgreSQL 17.11 on aarch64-linux, AWS `ap-southeast-1`)
- Production Supabase Reference: `ekspahbsbwdpraaqfqui` (Verified 100% Isolated / Zero Connections)
- Cloud Hosting Baseline: Vercel Project `camp-xs-ync/frontend` (Deployment `dpl_ExCRqLCy6Z88Bz3VsiPmv6FaKQbX` at alias `https://frontend-murex-one-58.vercel.app`)

---

## 1. Executive Summary & Purpose

This Evidence Index serves as the authoritative, non-destructive registry of all empirical evidence, source paths, commit hashes, database artifacts, test execution logs, and architecture documentation across the entire lifecycle of the CampXSync project.

Every claim, status tag, and architectural finding in the subsequent deliverables references this index. Per mandatory execution rules:
- No application code, configuration, or database state was modified during this audit.
- No live database writes or destructive scripts were executed.
- Real secret values, passwords, and tokens are strictly excluded.
- Evidence classifications strictly distinguish between `CODE VERIFIED`, `TEST VERIFIED`, `STAGING VERIFIED`, `DEPLOYMENT VERIFIED`, `DOCUMENTED ONLY`, and `UNVERIFIED`.

---

## 2. Planned Deliverables Tracking Matrix

| Deliverable ID | Filename | Target Path | Creation Status | Primary Scope & Contents |
| :---: | :--- | :--- | :---: | :--- |
| **DEL-01** | `CAMPXSYNC_EVIDENCE_INDEX.md` | `docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md` | **ACTIVE / CURRENT** | Master evidence registry, commit index, source coverage, and classification rules. |
| **DEL-02** | `CAMPXSYNC_COMPLETE_PROJECT_REPORT.md` | `docs/audit/CAMPXSYNC_COMPLETE_PROJECT_REPORT.md` | Planned (Next) | Comprehensive project history, original intended scope vs implementation, architecture evolution, and executive baselines. |
| **DEL-03** | `CAMPXSYNC_FEATURE_INVENTORY.md` | `docs/audit/CAMPXSYNC_FEATURE_INVENTORY.md` | Planned | Complete module and feature inventory covering all 63 modules (ACD, ADM, LMS, EXM, FIN, HRM, CAM, Core). |
| **DEL-04** | `CAMPXSYNC_FEATURE_INVENTORY.csv` | `docs/audit/CAMPXSYNC_FEATURE_INVENTORY.csv` | Planned | Structured CSV export of feature matrix with uniform status enumeration and escaping. |
| **DEL-05** | `CAMPXSYNC_API_GATEWAY_AND_SERVICE_INVENTORY.md` | `docs/audit/CAMPXSYNC_API_GATEWAY_AND_SERVICE_INVENTORY.md` | Planned | In-depth audit of all API layers: Java Spring Cloud Gateway, Next.js route handlers, PostgREST, direct SQL pooler. |
| **DEL-06** | `CAMPXSYNC_SECURITY_AND_RLS_AUDIT.md` | `docs/audit/CAMPXSYNC_SECURITY_AND_RLS_AUDIT.md` | Planned | Threat-control inventory, 20 high-impact code verifications, unsigned cookie analysis, bypass secret audit, and direct-SQL RLS implications. |
| **DEL-07** | `CAMPXSYNC_DATABASE_AND_MIGRATION_HISTORY.md` | `docs/audit/CAMPXSYNC_DATABASE_AND_MIGRATION_HISTORY.md` | Planned | Supabase schema catalog, table counts, constraints, partition policies, migrations, and seed scripts. |
| **DEL-08** | `CAMPXSYNC_ARCHITECTURE_FLOWS.md` | `docs/audit/CAMPXSYNC_ARCHITECTURE_FLOWS.md` | Planned | Editable Mermaid diagrams for System Context, Runtime, Auth & RBAC Sequence, Multi-Tenant Request, Gateway, and Deployment. |
| **DEL-09** | `CAMPXSYNC_VALIDATION_REGISTER.md` | `docs/audit/CAMPXSYNC_VALIDATION_REGISTER.md` | Planned | Consolidated inventory of all automated suites, Playwright browser tests, security scans, and login-performance runs. |
| **DEL-10** | `CAMPXSYNC_GAPS_AND_READINESS.md` | `docs/audit/CAMPXSYNC_GAPS_AND_READINESS.md` | Planned | Granular gap register, technical debt, risk assessment, and independent readiness verdicts across 8 dimensions. |
| **DEL-11** | `CAMPXSYNC_PROJECT_TIMELINE.md` | `docs/audit/CAMPXSYNC_PROJECT_TIMELINE.md` | Planned | Chronological timeline of milestones, pivots, decisions (DEC-01..15), and commit history from project inception. |
| **DEL-12** | `CAMPXSYNC_HANDOVER_DIGEST.md` | `docs/audit/CAMPXSYNC_HANDOVER_DIGEST.md` | Planned | Concise 4–5 page executive handover digest, one-line status per module, top 15 ranked risks, and caveats. |

---

## 3. Git Repository & Commit Evidence Baseline

### 3.1 Repository Identity
- **Repository URL / Origin**: `origin` -> `ReviewBranch` (tracking `origin/ReviewBranch`)
- **Active Working Branch**: `ReviewBranch`
- **Current HEAD Commit**: `b307149` (`docs(scripts): document loader flag in verify_login_hardening run comment`)
- **Total Commits on HEAD**: 49 commits (from `5198dc9` to `b307149`)

### 3.2 Chronological Key Commit Milestones

| Commit Hash | Commit Date | Author | Commit Message / Summary | Evidence Category |
| :--- | :--- | :--- | :--- | :--- |
| `5198dc9` | Early Baseline | Initial | Initial Commit for Version2.0 | Architecture Inception |
| `0b6b0d4` | Early Baseline | Initial | MongoDB Setup & Seeder | Legacy NoSQL Baseline |
| `7b9ed83` | Early Baseline | Initial | feat(admin,gateway): dual admin services, unified api-gateway, and logger integration | Java Microservices |
| `d51264e` | Mid Baseline | Core | docs: add Supabase (PostgreSQL) migration feasibility and architectural impact analysis | Critical Pivot: Supabase |
| `9bf9ec4` | Mid Baseline | Core | SupaBase ADM-01 Integration | Supabase PostgreSQL Pivot |
| `2782151` | Mid Baseline | Frontend | First MVP checkin, front end draft (Vite SPA prototype) | Frontend Draft |
| `2eec86b` | 2026-10-11 | Core | feat(platform): complete ADM-01 migrations (28/28), live gateway integration, and frontend delivery | Commit audited in Oct 11 report |
| `cffb841` | 2026-10-12 | Security | feat(adm-02): complete ADM-02 college admin implementation and CodeRabbit corrections | CodeRabbit Security Fixes |
| `3bcd745` | 2026-10-13 | Security | SubaBase Staging & Incorporated Code Rabbit Review | TestEnvironmentGuard Added |
| `5a1ecd9` | 2026-10-13 | Platform | feat(adm-01): implement platform role template authorization & Phase 4 MVP baseline | Phase 4 Kickoff |
| `fa2ca31` | 2026-10-14 | MVP | docs(mvp): update progress, backlog, readiness trace, handover, and tooling | MVP Governance Baseline |
| `18896fb` | 2026-10-15 | MVP | feat: implement Phase 1 & 2 UI foundation, auth routing, dashboard shell, and backend service enhancements | Next.js 15 App Router |
| `a92ac72` | 2026-10-15 | MVP | feat: complete Phase 3 staging gates, role dashboards, attendance workflow, and shortage analytics | Dual-Tenant Staging Seed |
| `c4bd8d4` | 2026-10-15 | MVP | feat: complete Phase 4 live tenant provisioning and Phase 5 college setup views, people, and assignments | College Roster Management |
| `ad191f7` | 2026-10-16 | MVP | feat(mvp): implement phase 6 faculty attendance & phase 7 role dashboards, preferences, reports (ACD-06, ACD-10) | Attendance & 75% Analytics |
| `2dc0d22` | 2026-10-16 | QA/Sec | feat(mvp): complete Phase 8 visual QA polish & Phase 9 security and real-browser acceptance | Playwright Browser Acceptance |
| `b542340` | 2026-10-16 | Release | feat(trial): complete Phase 10 Vercel acceptance and Phase 11 trial pack | Vercel Preview & Alias Deploy |
| `deec9da` | 2026-10-17 | Security | fix(security): unconditionally deny test-session impersonation in production | Test-Session Gate Hardening |
| `a5aaa1a` | 2026-10-17 | Auth | feat(auth): implement secure password recovery with hash routing and open-redirect protection | PKCE Password Recovery |
| `b04c9e1` | 2026-10-17 | Auth | fix(auth): route password recovery through callback to preserve PKCE verifier and session | Auth Callback Route |
| `25efffd` | 2026-10-18 | Auth | fix(auth): execute login hardening scope, authoritative role resolution, and credentials selection | Login Hardening Scope |
| `4006a99` | 2026-10-18 | Auth | fix(auth): enforce password clearing across account selectors and enable server-side role resolution | Credential Clearing Guard |
| `531a73c` | 2026-10-18 | Auth | feat(auth): link staging faculty account and enforce independent credentials | **Bypass Secret Exposure Commit** |
| `afaff92` | 2026-10-19 | Auth/Perf | fix(auth): replace private-schema PostgREST calls with direct SQL in resolveUserRole; fail closed on missing/deleted/suspended tenant | **PostgREST -> Direct SQL Pivot** |
| `62a5bcd` | 2026-10-19 | Scripts | chore(scripts): read Vercel bypass secret from session environment; add login performance measurement script | Remediation of Working Script |
| `b4e2b99` | 2026-10-19 | Deploy | chore(deploy): pin Vercel functions to sin1; ignore local Vercel and patch files; update staging log | Region Pinning Config |
| `b307149` | 2026-10-19 | Docs | docs(scripts): document loader flag in verify_login_hardening run comment | HEAD Documentation |

---

## 4. Primary Source Code Locations & Architectural Artifacts

### 4.1 Frontend Application Layer (`frontend/`)
- Target Framework: Next.js 15.0.0-rc.0 (App Router), React 19, TypeScript 5, Tailwind CSS
- Source Root: [frontend/src/](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/)
- Core Entry & Middleware:
  - [frontend/src/middleware.ts](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/middleware.ts) (Edge Middleware routing & fast-fail security)
  - [frontend/src/lib/auth/resolve-role.ts](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/lib/auth/resolve-role.ts) (Authoritative server-side role resolution)
  - [frontend/src/lib/auth/test-session-gate.ts](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/lib/auth/test-session-gate.ts) (Environment gate for test sessions)
  - [frontend/src/lib/db.ts](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/lib/db.ts) (Direct PostgreSQL connection pool via `pg`)
  - [frontend/src/lib/nav-config.ts](file:///d:/CampXSync/CampXSync_V2.0/frontend/src/lib/nav-config.ts) (Role-specific navigation specifications)
- Key Routes:
  - Auth: `/login`, `/reset-password`, `/auth/callback`, `/access-denied`
  - Platform Admin: `/platform`, `/platform/institutes`, `/platform/institutes/new`, `/platform/audit`
  - College Admin: `/college`, `/college/departments`, `/college/batches`, `/college/faculty`, `/college/students`, `/college/timetable`, `/college/attendance`, `/college/audit`
  - Faculty: `/faculty`, `/faculty/attendance`, `/faculty/sessions`, `/faculty/timetable`
  - Student: `/student` (Lightweight demo view)
  - API Routes: `/api/auth/test-session`, `/api/college/attendance/*`, `/api/college/departments`, `/api/college/faculty`, `/api/college/students`, `/api/college/timetable/entries`, `/api/platform/onboard-tenant`, `/api/user/preferences`

### 4.2 Legacy Java Microservices Layer (`services/` & `api-gateway/`)
- Gateway: [api-gateway/](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/) (Java JDK HTTP Server reverse proxy, Port 8080)
- Shared Logger: [logger/](file:///d:/CampXSync/CampXSync_V2.0/logger/) (`campx-logger` async logging & HMAC utility)
- Services with PostgreSQL Repositories:
  - [services/institute-admin-service/](file:///d:/CampXSync/CampXSync_V2.0/services/institute-admin-service/) (Port 8081)
  - [services/college-admin-service/](file:///d:/CampXSync/CampXSync_V2.0/services/college-admin-service/) (Port 8082)
  - [services/course-management-service/](file:///d:/CampXSync/CampXSync_V2.0/services/course-management-service/) (Port 8083)
- In-Memory Academic Services (ACD-02..10):
  - `services/curriculum-service` (Port 8084)
  - `services/subject-management-service` (Port 8085)
  - `services/batch-management-service` (Port 8086)
  - `services/timetable-management-service` (Port 8087)
  - `services/attendance-service` (Port 8088)
  - `services/academic-calendar-service` (Port 8089)
  - `services/learning-resource-service` (Port 8090)
  - `services/assessment-mapping-service` (Port 8091)
  - `services/analytics-service` (Port 8092)
- Scaffolded Empty Directories:
  - `services/ai-service`, `services/examination-service`, `services/faculty-service`, `services/identity-service`, `services/notification-service`, `services/student-service`, `mobile-app`, `platform/*`

### 4.3 Database Architecture & SQL Artifacts
- Database Engine: Hosted Supabase PostgreSQL 17.11 (`kpwdvvcschankgggitnj`)
- Bootstrap Scripts (13-Script Sequence):
  - Located in `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/` (`00_extensions_schemas.sql` through `85_auth_hook.sql`)
- Live Staging Seed Scripts:
  - [database/seed-data/staging_seed_college_a_and_b.sql](file:///d:/CampXSync/CampXSync_V2.0/database/seed-data/staging_seed_college_a_and_b.sql)
  - [database/seed-data/staging_seed_college_a_and_b_rollback.sql](file:///d:/CampXSync/CampXSync_V2.0/database/seed-data/staging_seed_college_a_and_b_rollback.sql)
- In-Repository Migrations:
  - `V20261011_01__fix_acd01_courses_rls_policies.sql` & rollback
  - `V20261014_01__enable_rls_on_monthly_partition_children.sql` & rollback
  - `V20261014_02__provision_test_tenant_staging.sql` & rollback
  - `V20261014_03__provision_staging_college_admin_identity.sql` & rollback

---

## 5. Historical Audit Reports & Execution Logs

1. **October 11, 2026 — Initial Full Code Review & Security Audit**:
   - Location: [CAMPXSYNC_AUDIT_REPORT.md](file:///d:/CampXSync/CampXSync_V2.0/CAMPXSYNC_AUDIT_REPORT.md)
   - Baseline: Commit `2eec86b`
   - Scope: 622 lines auditing API gateway regex injection, fail-open academic roles, in-memory storage, and partition RLS defects.
2. **October 13, 2026 — Preflight & Change Inventory**:
   - Locations: [docs/audit/00-preflight.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/00-preflight.md), [docs/audit/01-change-inventory.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/01-change-inventory.md), [docs/audit/02-prior-fixes.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/02-prior-fixes.md)
   - Baseline: Commit `3bcd745`
   - Scope: 92 files audited, `TestEnvironmentGuard` verification, read-only staging check.
3. **October 13–16, 2026 — Staging Execution Log**:
   - Location: [docs/staging/05-execution-log.md](file:///d:/CampXSync/CampXSync_V2.0/docs/staging/05-execution-log.md)
   - Scope: Steps P3-01 through Step 17 documenting live database catalog introspection, partition RLS fix, dual-tenant seed execution, and Phase 10 Vercel deployment.
4. **October 16, 2026 — MVP Consolidated Status Report**:
   - Location: [docs/CAMPXSYNC_MVP_CONSOLIDATED_STATUS_REPORT.md](file:///d:/CampXSync/CampXSync_V2.0/docs/CAMPXSYNC_MVP_CONSOLIDATED_STATUS_REPORT.md)
   - Baseline: Commit `b542340`
   - Scope: Executive verdict ("Ready with Known Limitations"), 63-module inventory, and Playwright verification summary.
5. **October 16, 2026 — Trial Documentation Pack**:
   - Locations: [docs/trial/ARCHITECTURE_TRACEABILITY.md](file:///d:/CampXSync/CampXSync_V2.0/docs/trial/ARCHITECTURE_TRACEABILITY.md), [docs/trial/DECISIONS.md](file:///d:/CampXSync/CampXSync_V2.0/docs/trial/DECISIONS.md), [docs/trial/TRIAL_GUIDE.md](file:///d:/CampXSync/CampXSync_V2.0/docs/trial/TRIAL_GUIDE.md), [docs/trial/KNOWN_LIMITATIONS.md](file:///d:/CampXSync/CampXSync_V2.0/docs/trial/KNOWN_LIMITATIONS.md), [docs/trial/POST_TRIAL_BACKLOG.md](file:///d:/CampXSync/CampXSync_V2.0/docs/trial/POST_TRIAL_BACKLOG.md)
   - Scope: Decisions DEC-01 through DEC-15, 62-module traceability matrix, evaluator demo scripts.
6. **October 18, 2026 — Login & Authorization Hardening Scope**:
   - Location: [docs/Login_Hardning_Scope.md](file:///d:/CampXSync/CampXSync_V2.0/docs/Login_Hardning_Scope.md)
   - Scope: Elimination of default-tenant fallbacks, role-escalation fallbacks, and independent credential enforcement.
7. **October 18–19, 2026 — Login Performance Investigation**:
   - Locations: `docs/staging/05-execution-log.md` Step 17, `docs/CampXSync_GSD_Performance_Pack/PERFORMANCE_TASK_BRIEF.md`, `scripts/measure_login_performance.mjs`
   - Scope: Identification of serial PostgREST failure bottleneck and verification of 75-77% speedup.

---

## 6. Verification Scripts & Automated Test Suite Evidence

| Script / Test Suite Path | Target Execution Scope | Tests Run | Result | Evidence Classification | Date |
| :--- | :--- | :---: | :---: | :---: | :---: |
| `scripts/verify_phase4_onboarding.mjs` | Platform Onboarding Saga | 19 | 19 Pass | TEST VERIFIED | 2026-10-16 |
| `scripts/verify_phase5_college_setup.mjs` | College Setup & Rosters | 31 | 31 Pass | TEST VERIFIED | 2026-10-16 |
| `scripts/verify_phase6_attendance.mjs` | Faculty Attendance & ACD-06 | 37 | 37 Pass | TEST VERIFIED | 2026-10-16 |
| `scripts/verify_phase7_dashboards_preferences.mjs` | Dashboards, Preferences, ACD-10 | 26 | 26 Pass | TEST VERIFIED | 2026-10-16 |
| `scripts/phase8_visual_qa.mjs` | Visual QA, WCAG AA, Viewports | 46 | 46 Pass | TEST VERIFIED | 2026-10-16 |
| `scripts/verify_phase9_security_browser.mjs` | Security & Browser Acceptance | 36 | 36 Pass | TEST VERIFIED | 2026-10-16 |
| `scripts/verify_phase10_deployed_browser.mjs` | Live Vercel Deployed Acceptance | 10 | 10 Pass | DEPLOYMENT VERIFIED | 2026-10-16 |
| `scripts/verify_role_routing.mjs` | Phase 3 Gate B Role Routing Matrix | 20 | 20 Pass | TEST VERIFIED | 2026-10-16 |
| `scripts/verify_login_hardening.mjs` | Login Hardening & Role Resolution | 58 | 58 Pass | TEST VERIFIED | 2026-10-19 |
| `scripts/verify_independent_accounts.mjs` | Independent Credentials Verification | 7 | 7 Pass | TEST VERIFIED | 2026-10-19 |
| `scripts/verify_faculty_auth_and_routing.mjs` | Faculty Staging Linkage & Guards | 12 | 12 Pass | TEST VERIFIED | 2026-10-19 |
| `scripts/measure_login_performance.mjs` | Login Latency Instrumentation | 10 runs/role | Recorded | STAGING VERIFIED | 2026-10-19 |

---

## 7. Evidence Classification Standard

Every claim in this audit is categorized using one of the following six strict standards:
1. `CODE VERIFIED`: Verified by direct inspection of current source code, routes, queries, or configuration in the repository.
2. `TEST VERIFIED`: Verified by examining recorded, reproducible automated test suite execution logs and assertions.
3. `STAGING VERIFIED`: Verified against recorded catalog introspection, schema objects, or queries executed against Supabase Staging (`kpwdvvcschankgggitnj`).
4. `DEPLOYMENT VERIFIED`: Verified against live deployed Vercel cloud endpoints (`frontend-murex-one-58.vercel.app`) using browser recordings or HTTP probes.
5. `DOCUMENTED ONLY`: Documented in specifications, architecture guides, or user story CSVs, but lacking functional implementation in the repository.
6. `UNVERIFIED`: Missing empirical evidence, contradictory reports, or runtime state that cannot be safely inspected offline.
