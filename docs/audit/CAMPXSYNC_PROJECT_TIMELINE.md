# CampXSync — Chronological Project History & Decision Timeline

**Document Identifier**: `docs/audit/CAMPXSYNC_PROJECT_TIMELINE.md`  
**Audit Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Governing Baseline**: Git HEAD (`ReviewBranch` commit `b307149`)  
**Cross-Reference Evidence Index**: [CAMPXSYNC_EVIDENCE_INDEX.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md)

---

## 1. Executive Summary

This timeline provides the complete chronological narrative of the CampXSync project from initial commit to the current working state. It synthesizes commit logs, architectural decision records (`DEC-01` through `DEC-15`), audit findings, staging execution logs, and user approvals into a unified historical record.

---

## 2. Chronological Milestones & Event Log

### Phase 1: Microservices Inception & Java / MongoDB Foundation (Early 2026)
- **Commit `5198dc9` — Initial Project Inception**: Version 2.0 of CampXSync initialized with Maven enterprise structure (`pom.xml` targeting Java 1.8).
- **Commit `0b6b0d4` — NoSQL & MongoDB Setup**: Early prototype persistence established using MongoDB and document seeders for academic records.
- **Commit `e13e2c2` — Enterprise Logger (`campx-logger`)**: Implemented asynchronous logging utility, PII/secret masking pipeline, and execution latency flow trackers.
- **Commit `7b9ed83` — Dual Admin Architecture & Java API Gateway**: Built embedded JDK HTTP Server API Gateway (`api-gateway` on port 8080) with prefix-based reverse proxy dispatch to Institute Admin (`localhost:8081`) and College Admin (`localhost:8082`).
- **Commit `7c2c462` — RFC 7807 Error Architecture**: Standardized domain exceptions across microservices using RFC 7807 Problem Details JSON format.
- **Commit `f2fc86c` to `01bbca2` — Academic Core Microservices Delivered**: Delivered Java microservices for ACD-01 (Course Management), ACD-03 (Subject Management), and ACD-04 (Batch Management) with in-memory state models.
- **Commit `992ae28` — Timetable & Attendance Microservices**: Added ACD-05 (Timetable Management) and ACD-06 (Attendance Management) with conflict detection engines.
- **Commit `e239866` — Academic Calendar Service**: Implemented ACD-07 Academic Calendar service with RFC 7807 handling and structured audit logging.

---

### Phase 2: The Relational Pivot to PostgreSQL & Supabase (Mid 2026)
- **Commit `d51264e` — Supabase Migration Feasibility Analysis**: Architectural impact study concluded that complex academic prerequisite DAGs, financial accounting ledgers, and multi-tenant isolation required a relational engine. Formal decision **`DEC-01`** abandoned MongoDB, Kafka, and Redis in favor of Supabase PostgreSQL.
- **Commit `9bf9ec4` — ADM-01 Supabase Integration**: Initial integration of PostgreSQL schemas for platform administration.
- **Commit `2782151` — Frontend Draft (Vite SPA)**: Initial draft of React 19 + Vite web frontend (`frontend-web`) connecting to `localhost:8080`.
- **Commit `2eec86b` — 28 ADM-01 Repositories Delivered**: Completed 28 PostgreSQL repository classes in `services/institute-admin-service` targeting Supabase schema `iam` and `plat`.

---

### Phase 3: Security Audit, CodeRabbit Review & Staging Guardrails (Oct 11 – 13, 2026)
- **October 11, 2026 — Comprehensive Security Audit (`CAMPXSYNC_AUDIT_REPORT.md`)**:
  - Uncovered critical vulnerabilities in commit `2eec86b`:
    1. Java API Gateway regex JWT claims extraction vulnerable to JSON claim spoofing (CWE-841 / `SEC-CRIT-01`).
    2. Java academic services completely lacked HMAC verification and defaulted to `ACADEMIC_ADMIN` when `X-User-Role` was missing (`SEC-CRIT-02`).
    3. 9 academic microservices had zero database persistence, running entirely in-memory (`SEC-CRIT-03`).
    4. 24 monthly partition child tables lacked Row-Level Security (`SEC-CRIT-04`).
- **Commit `cffb841` — CodeRabbit Security Corrections**:
  - Rewrote `SupabaseJwtValidator.java` to parse structured JSON AST and validate ES256 signatures (29 regression tests added).
  - Implemented `CollegeResolver.java` to eliminate phantom-college fallback (`COL_ENGG_01`).
  - Added `LocalPolicyRepository.java` enforcing server-side `DRAFT` status and authorized approvals (fixing CWE-841 bypass).
- **Commit `3bcd745` — Supabase Staging Guardrails (`TestEnvironmentGuard`)**:
  - Implemented `TestEnvironmentGuard.java` across Java services to blacklist production database ref `ekspahbsbwdpraaqfqui` and enforce staging target `kpwdvvcschankgggitnj`.
  - Added unit test suites verifying pooler URL parsing and staging assertions (74/74 passed).

---

### Phase 4: Unified Cloud MVP & Phase 1–11 Execution (Oct 13 – 16, 2026)
- **Commit `fa2ca31` & `DEC-13` — Unification into Next.js 15 Cloud MVP**:
  - Decided to build the deployable cloud MVP in `frontend/` using Next.js 15 App Router, React 19, Tailwind CSS, `@supabase/ssr`, and Edge Middleware.
  - Preserved legacy Java microservices in `services/*` as offline prototypes.
- **Commit `18896fb` — Phase 1 & 2 UI Foundation & Auth Routing**:
  - Created split-layout login page (`/login`), Edge Middleware route guards (`frontend/src/middleware.ts`), and role-specific dashboard navigation skeletons.
- **Commit `a92ac72` — Phase 3 Staging Gates & Dual-Tenant Seed**:
  - User approved database write ritual `GO PARTITION-RLS-FIX`: enabled RLS and revoked direct grants across all 24 monthly child partition tables on staging.
  - User approved `GO PROVISION-TEST-TENANT`: provisioned test tenant `CAMPX_STAGING` (`11111111-1111-1111-1111-111111111111`), triggering automatic seeding of 30 role templates and 2,138 role-permissions.
  - User approved `GO SEED-STAGING`: executed `staging_seed_college_a_and_b.sql`, populating dual-tenant fixtures across College A (`DIT`) and College B (`TCS`).
- **Commit `c4bd8d4` — Phase 4 & 5 Onboarding Saga & College Setup**:
  - Implemented 5-step Institute Onboarding Wizard (`/platform/institutes/new`) with duplicate conflict detection.
  - Delivered College Setup views: Academic Departments (`/college/departments`), Faculty Roster (`/college/faculty`), Student Roster (`/college/students`), and Batch Assignment (`/college/batches`).
- **Commit `ad191f7` — Phase 6 & 7 Faculty Attendance & 75% Shortage Analytics**:
  - Implemented Faculty Attendance Workspace (`/faculty/attendance`): timetable slot deduplication, keyboard marking (P/A/L), modal ledger submission, and permanent read-only sheet lock.
  - Implemented Audited Attendance Correction Drawer (`/college/attendance`): optimistic concurrency locking (`row_version`), mandatory justification (>= 5 chars), atomic database update, and immutable logging into `audit.change_log` and `audit.events`.
  - Implemented Institutional Shortage Analytics (`/api/college/attendance/report`): cumulative calculation, statutory 75% shortage alert trigger (`DEC-14`), and formula-safe CSV export.
- **Commit `2dc0d22` — Phase 8 & 9 Visual QA & Security Acceptance**:
  - Phase 8 Visual QA (`scripts/phase8_visual_qa.mjs`): 46/46 passed; 0 Axe accessibility violations; WCAG AA contrast; zero horizontal overflow across Desktop (1440px), Tablet (1024px), Mobile (390px).
  - Phase 9 Security Acceptance (`scripts/verify_phase9_security_browser.mjs`): 36/36 passed; fast-fail guards (inactive, no profile, suspended), cross-tenant IDOR rejection, and platform operator isolation verified.
- **Commit `b542340` — Phase 10 Vercel Cloud Acceptance & Phase 11 Trial Pack**:
  - Deployed Next.js 15 frontend to Vercel: Preview URL `https://frontend-n34dbtn83-camp-xs-ync.vercel.app` and Alias URL `https://frontend-murex-one-58.vercel.app`.
  - Real browser live acceptance suite (`scripts/verify_phase10_deployed_browser.mjs`): 10/10 passed on live Vercel cloud deployment.
  - Authored full Trial Documentation Pack (`docs/trial/`): `TRIAL_GUIDE.md`, `ARCHITECTURE_TRACEABILITY.md`, `DECISIONS.md` (DEC-01..15), `KNOWN_LIMITATIONS.md`, `POST_TRIAL_BACKLOG.md`, `DEMO_SCRIPT.md`.

---

### Phase 5: Login Hardening, Credential Isolation & Performance Fix (Oct 17 – 19, 2026)
- **Commit `deec9da` — Unconditional Test-Session Production Denial**:
  - Added `test-session-gate.ts` strictly returning `false` if `VERCEL_ENV === 'production'`.
- **Commit `a5aaa1a` & `b04c9e1` — PKCE Password Recovery**:
  - Routed password recovery through `/auth/callback` to preserve PKCE code verifiers and cookies across full page reloads.
- **Commit `25efffd` & `4006a99` — Login Hardening Scope Execution**:
  - Executed `docs/Login_Hardning_Scope.md`: removed unsafe default-tenant UUID fallbacks (`11111111-1111-1111-1111-111111111111`) from dashboard pages; eliminated email-prefix role inference; enforced password clearing on role selectors.
- **Commit `531a73c` — Faculty Account Linkage & Bypass Secret Exposure**:
  - Linked staging faculty account `faculty.campx@gmail.com` to Dr. Rajesh Sharma (`FAC-CSE-001`).
  - **Exposure Incident**: Committed a live Vercel automation protection bypass token in plaintext in `scripts/verify_faculty_auth_and_routing.mjs`.
- **Commit `afaff92` — PostgREST-to-Direct-SQL Role Resolution Pivot**:
  - Discovered that serial failed PostgREST calls against private schemas caused a 6-17s login delay.
  - Replaced failed PostgREST calls with direct parameterized SQL queries via `@/lib/db`.
  - Result: Median login latency dropped by **75–77%** across all roles (College Admin: 12.5s -> 2.88s; Platform Admin: 9.1s -> 2.14s; Faculty: 10.7s -> 2.67s).
- **Commit `62a5bcd` — Bypass Secret Remediation in Working Script**:
  - Updated `verify_faculty_auth_and_routing.mjs` to read bypass secret from `process.env.VERCEL_AUTOMATION_BYPASS_SECRET`.
  - Added `scripts/measure_login_performance.mjs` to benchmark login timing.
- **Commit `b4e2b99` — Regional Pinning Configuration (`sin1`)**:
  - Added `"regions": ["sin1"]` to `frontend/vercel.json` to pin functions to Singapore.
  - Updated `docs/staging/05-execution-log.md` with Step 17 documenting performance remediation.
- **Commit `b307149` (HEAD) — Script Documentation**:
  - Documented loader flag behavior in `scripts/verify_login_hardening.mjs`.

---

## 3. Architecture Decisions & Governance Traceability (DEC-01 through DEC-15)

| Decision ID | Ratified Date | Architectural Context & Problem Statement | Core Resolution Adopted | Direct Repository & Database Evidence |
| :---: | :---: | :--- | :--- | :--- |
| **DEC-01** | Mid 2026 | Legacy specs proposed MongoDB, Kafka, and Redis. Relational constraints and ACID transactions required. | Consolidated multi-schema PostgreSQL database on Supabase (20 schemas, 458 tables). No Kafka or Redis in MVP. | `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/` |
| **DEC-02** | Oct 2026 | ACD-08 and LMS-07 both specify learning resources, introducing duplicate entity risk. | Consolidated into a single `lms.resources` table in PostgreSQL. ACD-08 references this table. | Staging table `lms.resources` |
| **DEC-03** | Oct 2026 | ACD-01 course master overlaps conceptually with LMS-01 delivery offerings. | `acd.courses` owns canonical tenant course master. Delivery binds to `acd.course_sections` and timetable entries. | `acd.courses`, `acd.course_sections` |
| **DEC-04** | Oct 2026 | ACD-03 and LMS-03 both defined subject management structures in legacy specs. | Single authoritative subject master in `acd.subjects(tenant_id, id)`. Timetable references this table. LMS acts as projection. | Staging table `acd.subjects` |
| **DEC-05** | Oct 2026 | FIN-07 (Finance Payroll) and HRM-05 (Compensation) overlap in salary disbursement. | HRM-05 owns primary salary calculation; FIN-07 acts as general ledger accounting projection. | Schemas `hrm` and `fin` separation |
| **DEC-06** | Oct 2026 | Archive DOCX files for HRM-06 and HRM-07 overlapped in recruitment terminology. | Canonical split: HRM-06 = Applicant Tracking & Recruitment; HRM-07 = Employee Induction & Onboarding. | `docs/reference/product_lifecycle.md` |
| **DEC-07** | Oct 2026 | The `Communication&AnnnouncementModule/` directory in the design archive was completely empty. | Formally recorded as a source package gap rather than fabricating unreviewed code. Deferred to Phase 5. | `docs/trial/POST_TRIAL_BACKLOG.md` |
| **DEC-08** | Oct 2026 | Single-tier admin drafts failed to separate multi-tenant operator from college tenant. | Platform Admin operates at `tenant_id IS NULL` (`iam.is_platform_admin()`) with 0 read access to tenant rows. College Admin scoped to tenant. | `middleware.ts`, `PlatformTierIsolationRegressionTest.java` |
| **DEC-09** | Oct 2026 | Archive contained both `LMS-01.docx` (v1.0) and `LMS-01-v2.0.docx`. | `LMS-01-v2.0.docx` ratified as canonical; v1.0 formally marked superseded in specification register. | `docs/trial/ARCHITECTURE_TRACEABILITY.md` |
| **DEC-10** | Oct 2026 | Canonical specs define 63 modules that cannot all fit into the October 19 MVP deadline. | All 63 modules tracked in traceability matrix with explicit statuses (10 Verified, 1 Partial, 52 Deferred). | `docs/trial/ARCHITECTURE_TRACEABILITY.md` |
| **DEC-11** | Oct 2026 | Student master specification was missing from the AllModules design archive. | Codified database contract: `core.persons` (bio identity) + `stu.students` (academic profile) + `acd.batch_students`. | Staging tables `stu.students`, `core.persons` |
| **DEC-12** | Oct 2026 | Shared capabilities (IAM, audit, outbox, preferences) were scattered across services. | Centralized into dedicated PostgreSQL schemas `iam` and `audit` with server-side validation. | Staging schemas `iam`, `audit` |
| **DEC-13** | Oct 2026 | Monolithic Vite SPA and Java microservices could not run on Vercel serverless without containers. | Unified deployable cloud MVP into `frontend/` (Next.js 15 App Router) on Vercel; preserved Java microservices as offline prototypes. | `frontend/` directory, Vercel deployment |
| **DEC-14** | Oct 2026 | Master prompt required attendance shortage detection without an institutional threshold config. | Enforced statutory **75.0%** threshold (`SHORTAGE_THRESHOLD = 75`) across analytics, alerts, and CSV exports. | `frontend/src/app/api/college/attendance/report/route.ts` |
| **DEC-15** | Oct 2026 | Supabase PostgREST RLS restricted clients from reading role assignments before custom claims issuance. | Implemented authoritative server-side PostgreSQL fallback in `resolve-role.ts` and standardized errors to RFC 7807 Problem Details. | `frontend/src/lib/auth/resolve-role.ts`, `middleware.ts` |
