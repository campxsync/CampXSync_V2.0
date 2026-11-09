# CampXSync — Complete Project History, Architecture & Implementation Audit Report

**Document Identifier**: `docs/audit/CAMPXSYNC_COMPLETE_PROJECT_REPORT.md`  
**Report Date**: October 19, 2026  
**Auditor**: Antigravity Autonomous Agent (Google DeepMind)  
**Baseline Git Branch**: `ReviewBranch`  
**Baseline Git Commit**: `b307149` (`docs(scripts): document loader flag in verify_login_hardening run comment`)  
**Target Staging Database**: Hosted Supabase PostgreSQL 17.11 (`kpwdvvcschankgggitnj` on AWS `ap-southeast-1`)  
**Production Isolation Guard**: Supabase Production Reference `ekspahbsbwdpraaqfqui` (Verified strictly isolated; zero production connections)  
**Cloud Deployment Baseline**: Vercel Live Alias [`https://frontend-murex-one-58.vercel.app`](https://frontend-murex-one-58.vercel.app) & Preview [`https://frontend-n34dbtn83-camp-xs-ync.vercel.app`](https://frontend-n34dbtn83-camp-xs-ync.vercel.app)  
**Cross-Reference Evidence Index**: [CAMPXSYNC_EVIDENCE_INDEX.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_EVIDENCE_INDEX.md)

---

## 1. Executive Summary

### 1.1 Mission & Scope of the Audit
This report establishes the definitive, evidence-based project baseline documenting all CampXSync work from the beginning of the project to its current working state. It provides an authoritative reference for architects, engineers, security auditors, and institutional stakeholders preparing for subsequent trials and production readiness.

The audit was executed under strict read-only parameters:
- Zero application code, migrations, seed scripts, or configuration files were modified.
- No live database writes or destructive actions were performed.
- All real credentials, passwords, tokens, and database connection strings were strictly protected and excluded from documentation.
- Every architectural and functional claim is classified using empirical evidence standards.

### 1.2 Honest Architectural Verdict

> [!IMPORTANT]
> **Executive Verdict: READY FOR INSTITUTIONAL STAGING TRIAL (WITH CRITICAL PRODUCTION GAPS)**
> 
> The CampXSync Minimum Viable Product (MVP) vertical slice is **demonstrably end-to-end functional** on the hosted Supabase Staging PostgreSQL instance (`kpwdvvcschankgggitnj`) and deployed Vercel preview/alias environments (`frontend-murex-one-58.vercel.app`). 
> 
> Core academic workflows—including multi-tenant platform operator governance, institutional college administrative setup, weekly conflict-free timetable scheduling, faculty attendance capture with permanent ledger locking, audited administrative attendance corrections with optimistic concurrency locking, and statutory 75% attendance shortage reporting—are operational and verified with live database persistence.
> 
> However, **the platform is NOT ready for multi-tenant production release** due to critical architectural divergence, unpromoted code, and security boundaries:
> 1. **Server Direct SQL Bypasses PostgreSQL Row-Level Security (RLS)**: The Next.js server connection pool (`frontend/src/lib/db.ts`) connects as the table owner `postgres.kpwdvvcschankgggitnj`. Because PostgreSQL RLS is bypassed by table owners unless `FORCE ROW LEVEL SECURITY` is applied (which is 0% enabled across all 458 tables), database queries executed by server components and route handlers do not benefit from database-level RLS. Tenant isolation depends entirely on application-level query predicates (`WHERE tenant_id = $1`).
> 2. **Unsigned Test-Session Cookie Vulnerability**: The `campx_test_session` cookie used for trial impersonation is raw, unencrypted, unsigned JSON. In any environment where `isTestSessionAllowed()` evaluates to true (such as Vercel Preview or local dev), an attacker can forge arbitrary roles (`SUPER_ADMIN`, `COLLEGE_ADMIN`) without password authentication.
> 3. **Unrevoked Vercel Bypass Secret in Git History**: Commit `531a73c` committed a live Vercel automation bypass token into Git history. While the working script was updated in `62a5bcd`, no evidence exists that the secret was rotated or revoked in Vercel project settings.
> 4. **Divergent Legacy Java Architecture vs Active Next.js Cloud App**: 18 Java microservices and an embedded Spring/JDK HTTP API Gateway remain in the repository but do not run on Vercel. 9 of these services have zero database integration and rely on ephemeral in-memory storage.
> 5. **Expansive Out-of-Scope Modules**: 51 of the 63 canonical modules (Examinations, Finance, Advanced LMS, full HRMS Payroll) are completely unimplemented in code.

---

## 2. Project Evolution & Architecture Pivot History

```mermaid
timeline
    title CampXSync Architecture Evolution & Pivot History
    section Phase 1 : Distributed Microservices
        Commit 5198dc9 : Initial Architecture
        Commit 0b6b0d4 : MongoDB & Spring Cloud Setup
        Commit 7b9ed83 : Dual Admin Services & API Gateway
        Commit f2fc86c : ACD-01 Course Management Java Service
        Commit 992ae28 : Timetable & Attendance Java Services
    section Phase 2 : Database Pivot
        Commit d51264e : Supabase PostgreSQL Feasibility Analysis
        Commit 9bf9ec4 : ADM-01 Supabase Integration
        Commit 2eec86b : 28 ADM-01 Repositories Delivered
    section Phase 3 : Security & Code Review
        Commit 2eec86b : Oct 11 Security Audit (Regex JWT & In-Memory Gaps)
        Commit cffb841 : CodeRabbit Fixes & ADM-02 College Admin
        Commit 3bcd745 : TestEnvironmentGuard & Production Isolation
    section Phase 4 : Unified Cloud MVP
        Commit fa2ca31 : MVP Governance & Track Split
        Commit 18896fb : Next.js 15 App Router & Supabase SSR
        Commit a92ac72 : Dual-Tenant Staging Seed (College A & B)
        Commit c4bd8d4 : Live Tenant Provisioning & Roster Views
        Commit ad191f7 : Faculty Attendance Locking & 75% Shortage Analytics
        Commit 2dc0d22 : Playwright Browser Acceptance (36/36 Pass)
        Commit b542340 : Vercel Cloud Deployment & Trial Pack
    section Phase 5 : Login Hardening & Performance
        Commit deec9da : Unconditional Test-Session Prod Denial
        Commit a5aaa1a : PKCE Password Recovery Flow
        Commit 25efffd : Authoritative Role Resolution & Account Selection
        Commit 531a73c : Faculty Staging Account Linkage
        Commit afaff92 : PostgREST -> Direct SQL Pivot (75% Speedup)
        Commit b4e2b99 : Region Pinning (sin1) & Staging Log Update
```

### 2.1 Era 1: Java Microservices & MongoDB (Inception to Mid-2026)
CampXSync was initially architected as an enterprise distributed system using:
- **Language / Runtime**: Java 8 (Oracle JDK 1.8.0_202), Apache Maven 3.9.9.
- **Microservices**: 18 planned Spring Boot services running on individual ports (8080 through 8092).
- **Ingress**: Custom JDK HTTP Server API Gateway (`api-gateway`) handling prefix-based path routing and JWT verification.
- **Persistence**: MongoDB for document storage, with early proposals for Kafka message buses and Redis caches.
- **Frontend Prototype**: Client-side React 19 + Vite Single Page Application (`frontend-web`) communicating with `http://localhost:8080`.

### 2.2 Era 2: The PostgreSQL & Supabase Pivot (Commit `d51264e` & `9bf9ec4`)
Recognizing the relational complexity of collegiate degree structures, statutory prerequisites, financial ledgers, and multi-tenant isolation, the architecture team conducted a formal feasibility study (`docs/architecture/SUPABASE_MIGRATION_AND_ARCHITECTURAL_IMPACT.md`).
- **Core Pivot Decision (`DEC-01`)**: Abandoned MongoDB, Redis, and Kafka in favor of a consolidated multi-schema PostgreSQL database managed via Supabase.
- **Schema Strategy**: Exactly 20 ERP application schemas were defined: `acd`, `audit`, `bud`, `cam`, `cfg`, `core`, `doc`, `exm`, `fin`, `hrm`, `iam`, `lms`, `obe`, `pay`, `plat`, `rpt`, `stu`, `sys`, `tax`, `wf`.
- **Relational Integrity**: 458 application tables, 458 primary keys, 403 unique constraints, 1,239 foreign keys, and 1,158 indexes were codified in a 13-script bootstrap sequence (`Documentation/DatabaseDesign/Supabase/Scripts_V3.0/`).

### 2.3 Era 3: October 11 Security Audit & CodeRabbit Remediation (Commits `2eec86b` to `3bcd745`)
A comprehensive audit (`CAMPXSYNC_AUDIT_REPORT.md`) on commit `2eec86b` exposed severe architectural risks:
- The Java API Gateway used regular expressions to parse JWT JSON payloads, allowing attackers to inject `sub`, `role`, and `tenant_id` claims via user-editable metadata (CWE-841).
- 8 of the 9 academic Java microservices lacked HMAC request signatures and defaulted to `ACADEMIC_ADMIN` when `X-User-Role` headers were missing.
- 9 academic services had zero database persistence, running entirely in-memory.
- 24 monthly partition child tables lacked Row-Level Security.
- **Remediation**: CodeRabbit CLI reviews and commits `cffb841` and `3bcd745` fixed gateway parsing, introduced `TestEnvironmentGuard` to prevent accidental connections to production database ref `ekspahbsbwdpraaqfqui`, and eliminated phantom-college fallbacks.

### 2.4 Era 4: The Unified Cloud MVP Architecture (Commits `fa2ca31` to `b542340`)
Under Architecture Decision `DEC-13`, the team recognized that hosting 18 Java microservices on Vercel was technically infeasible without complex external container infrastructure. To achieve an institutional trial deployment by October 19, 2026:
- The active cloud MVP was unified into a Next.js 15 App Router application located in `frontend/`.
- Edge Middleware (`frontend/src/middleware.ts`) was implemented for server-side route guards.
- Direct integration with Supabase Auth (`@supabase/ssr`) and PostgreSQL pooler replaced the Java API Gateway and Java microservices.
- The legacy Java code (`services/*`, `api-gateway/`) and Vite SPA (`frontend-web/`) were preserved in the repository for on-premise reference but deactivated in the Vercel cloud deployment.

### 2.5 Era 5: Login Hardening, Faculty Linkage & Performance Investigation (Commits `deec9da` to `b307149`)
In mid-October 2026, user testing revealed identity-mapping gaps and an observed ~6-second login delay:
- **Login Hardening (`docs/Login_Hardning_Scope.md`)**: Removed default-tenant fallbacks (`11111111-1111-1111-1111-111111111111`), eliminated email-prefix role inference, enforced independent credentials for College Admin, Platform Admin, and Faculty, and linked the staging faculty account (`531a73c`).
- **Performance Remediation (`afaff92`)**: Diagnostic probing identified that `resolve-role.ts` sequentially called Supabase PostgREST against private schemas (`plat`, `iam`, `core`), which failed with HTTP 404/PGRST106. Replacing these failed calls with direct PostgreSQL queries via `@/lib/db` dropped median login durations by 75-77% (e.g., College Admin from 12.5s to 2.88s).
- **Region Pinning (`b4e2b99`)**: Configured Vercel function region to Singapore (`sin1`) in `frontend/vercel.json` to minimize latency to AWS `ap-southeast-1` Supabase databases.

---

## 3. Original Intended Scope vs. Current Implementation

Per Section 15.C of the audit specification, this section documents the originally intended scope for every module family, citing canonical specification documents, current implementation status, and observed differences.

```
Total Modules in Architecture Surface: 63
├─ Staging-Verified in Code & Live DB: 10 (15.9%)
├─ Partially Implemented in Code & Live DB: 1 (1.6%)
└─ Specified in Canonical Docs (Not in MVP Slice): 52 (82.5%)
```

### 3.1 Academic Management (ACD Domain)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **ACD-01** | Course Management | `Documentation/AcadamicsModule/MicroServiceDesign/ACD-01.docx`<br>`ACD-01_Course_Management_User_Stories.csv` | Full course master, degree programs, accreditation compliance, course offerings, syllabus versions, credit systems. | **IMPLEMENTED & VERIFIED** | Implemented in Next.js (`/college/departments`) backed by `acd.courses` and `acd.course_sections`. Java service (`services/course-management-service`) also exists. Advanced syllabus PDF versions deferred. | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| **ACD-02** | Curriculum Structure | `Documentation/AcadamicsModule/MicroServiceDesign/ACD-02.docx` | Semester curricula, credit prerequisites DAG, elective baskets, degree audit rules. | **PLANNED / DOCUMENTED ONLY** | Business rules exist in Java `curriculum-service`, but 100% in-memory with zero DB integration. Omitted from Next.js cloud MVP. | `DOCUMENTED ONLY` |
| **ACD-03** | Subject Master | `docs/architecture/ACD-03_SUBJECT_MANAGEMENT_SPECIFICATION.md`<br>`ACD-03_Subject_Management_User_Stories.csv` | Authoritative subject master, theory/practical breakdown, prerequisite validation, LMS alignment (DEC-04). | **IMPLEMENTED & VERIFIED** | Authoritative subject catalog persisted in `acd.subjects` with composite key `(tenant_id, id)`. Timetable entries and batch curricula reference this table. | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| **ACD-04** | Academic Batches | `docs/architecture/ACD-04_BATCH_MANAGEMENT_SPECIFICATION.md`<br>`ACD-04_Batch_Management_User_Stories.csv` | Student cohorts, section divisions, student roster assignment, roll number generation, cohort splitting/merging. | **IMPLEMENTED & VERIFIED** | Cohort management active at `/college/batches` and `/api/college/students/assign-batch`. Persisted in `acd.batches` and `acd.batch_students`. | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| **ACD-05** | Timetable Scheduling | `Documentation/AcadamicsModule/UserStories/ACD-05_Timetable_Management_User_Stories.csv` | Conflict-free weekly scheduling, classroom allocation, faculty workload balance, room clash exclusion checks. | **IMPLEMENTED & VERIFIED** | Weekly calendar grid active at `/college/timetable`. Backed by `acd.timetable_entries`. Validates room and faculty conflicts. | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| **ACD-06** | Attendance Management | `Documentation/AcadamicsModule/UserStories/ACD-06_Attendance_Management_User_Stories.csv` | Faculty attendance marking, slot deduplication, ledger submission, read-only locking, audited administrative corrections with versioning. | **IMPLEMENTED & VERIFIED** | Fully implemented across `/faculty/attendance`, `/college/attendance`, and `/api/college/attendance/*`. Backed by `acd.attendance_sessions`, `records`, `corrections`. Optimistic lock verified. | `CODE VERIFIED`<br>`STAGING VERIFIED`<br>`DEPLOYMENT VERIFIED` |
| **ACD-07** | Academic Calendar | `Documentation/AcadamicsModule/UserStories/ACD-07_Academic_Calendar_User_Stories.csv` | Institutional terms, examination windows, statutory holidays, teaching day counters. | **PLANNED / DOCUMENTED ONLY** | Tables defined in Supabase staging schema (`acd.academic_calendars`, `acd.calendar_events`). Standalone Java service exists in-memory. UI deferred. | `DOCUMENTED ONLY` |
| **ACD-08** | Learning Resources | `docs/architecture/ACD-08_LEARNING_RESOURCES_SPECIFICATION.md`<br>`ACD-08_learning_resources_user_stories.csv` | Digital asset repository, lecture notes, syllabus references, multimedia uploads. | **PLANNED / DOCUMENTED ONLY** | Consolidated into `lms.resources` under DEC-02 to prevent duplicate entity masters. Schema exists in staging DB; frontend deferred. | `DOCUMENTED ONLY` |
| **ACD-09** | Assessment Mapping | `docs/architecture/ACD-09_ASSESSMENT_MAPPING_SPECIFICATION.md`<br>`ACD-09_assessment_mapping_user_stories.csv` | Bloom's taxonomy mapping, Course Outcomes (CO) to Program Outcomes (PO) attainment matrices. | **PLANNED / DOCUMENTED ONLY** | Schema defined in staging PostgreSQL (`obe` schema). In-memory Java prototype exists. Cloud MVP frontend deferred. | `DOCUMENTED ONLY` |
| **ACD-10** | Attendance Analytics & Reporting | `docs/architecture/ACD-10_REPORTING_ANALYTICS_SPECIFICATION.md`<br>`ACD-10_Reporting_Analytics_User_Stories.csv` | Statutory 75% shortage calculation (DEC-14), cumulative percentage views, 14-day attendance trend lines, formula-safe CSV export. | **IMPLEMENTED & VERIFIED** | Implemented in Next.js via view `acd.v_attendance_summary` and `/api/college/attendance/report`. Verified live in browser and staging DB. | `CODE VERIFIED`<br>`STAGING VERIFIED`<br>`DEPLOYMENT VERIFIED` |

### 3.2 Platform & College Administration (ADM Domain)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **ADM-Platform** | Platform Operator Administration | `Documentation/AdminModule/CampXSync_Platform_Tier_APIs_Documentation_v2.0.docx` | Multi-tenant institute registry, 5-step onboarding saga, subscription tiers, platform role templates, global audit logging. | **IMPLEMENTED & VERIFIED** | Implemented at `/platform`, `/platform/institutes`, `/platform/institutes/new`. Backed by `core.tenants`, `iam.roles`. Zero access to tenant student/faculty rows (HTTP 403). Live onboarding wizard operates in safe dry-run mode unless owner approval header provided. | `CODE VERIFIED`<br>`STAGING VERIFIED`<br>`DEPLOYMENT VERIFIED` |
| **ADM-College** | Institutional College Admin | `Documentation/AdminModule/CampSync_College_Admin_Module_APIs_Documentation_v1.0.docx` | College governance hub, department creation, faculty appointing, student admissions, timetable oversight, audited attendance corrections. | **IMPLEMENTED & VERIFIED** | Implemented at `/college` and nested paths. Backed by `core.departments`, `hrm.employees`, `stu.students`, `audit.change_log`. | `CODE VERIFIED`<br>`STAGING VERIFIED`<br>`DEPLOYMENT VERIFIED` |

### 3.3 Core Platform Capabilities (STU, IAM, AUD Domains)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **STU-01** | Student Master & Enrollment | `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/20_tables.sql` (DEC-11) | Student bio data, academic enrollment, batch assignment, soft deletion (`WITHDRAWN`). | **IMPLEMENTED & VERIFIED** | Implemented at `/college/students` and `/api/college/students`. Backed by `core.persons` and `stu.students`. Solves missing DOCX specification per DEC-11. | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| **IAM-01** | Identity, Auth & RBAC | `Documentation/AdminModule/CampXSync_ADM-01_Institute_Admin_Enterprise_ArchitectureV2.0.docx` | Supabase Auth password login, JWT tenant stamping, role resolution, RFC 7807 problem details, user preferences sync. | **IMPLEMENTED & VERIFIED** | Implemented via Next.js Edge Middleware, `@supabase/ssr`, `resolve-role.ts`, and direct PostgreSQL queries against `plat.platform_admins`, `iam.user_profiles`, `core.tenants`, `iam.role_assignments`. | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| **AUD-01** | Institutional Audit Trail | `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/20_tables.sql` | Immutable change logs, security events, outbox propagation, tamper-evident audit history. | **IMPLEMENTED & VERIFIED** | Implemented via `audit.events` and `audit.change_log`. Attendance corrections and privilege actions write immutable event rows. | `CODE VERIFIED`<br>`STAGING VERIFIED` |

### 3.4 Human Resource Management (HRM Domain)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **HRM-01** | Employee Information Master | `HRMS/HRM-01.docx` | Comprehensive employee profile, employment contracts, department links, IAM user links, soft deletion. | **PARTIAL & VERIFIED** | Core faculty profile implemented at `/college/faculty`. Backed by `hrm.employees` and `core.persons`. Full contract lifecycle deferred. | `CODE VERIFIED`<br>`STAGING VERIFIED` |
| **HRM-02..09** | Advanced HRMS Suite | `HRMS/HRM-02.docx` through `HRM-09.docx` | Staff leave management, biometric attendance, performance appraisals, compensation/payroll, recruitment, induction, training. | **PLANNED / DOCUMENTED ONLY** | Schemas exist in staging DB (`hrm.attendance_punches`, etc.). Intentionally deferred to Phase 5 under DEC-10. | `DOCUMENTED ONLY` |

### 3.5 Learning Management System (LMS Domain)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **LMS-01..10** | Comprehensive LMS Suite | `LearningManagementSystemModule/LMS-01-v2.0.docx` (DEC-09) through `LMS-10.docx` | Course content delivery, lesson planning, assignment submission, virtual classrooms, digital certificates, OBE attainment. | **PLANNED / DOCUMENTED ONLY** | Full `lms` schema exists in staging DB (`lms.resources`, `lms.progress_events`). Code implementation intentionally deferred to Phase 3. | `DOCUMENTED ONLY` |

### 3.6 Examination Management (EXM Domain)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **EXM-01..09** | Comprehensive Exam Suite | `ExaminationModule/EXM-01.docx` through `EXM-09.docx` | Hall tickets, seating arrangements, script valuation, maker-checker marks approval, transcripts, SGPA/CGPA calculation. | **PLANNED / DOCUMENTED ONLY** | Full `exm` schema exists in staging DB. Code implementation intentionally deferred to Phase 4 under DEC-10. | `DOCUMENTED ONLY` |

### 3.7 Finance & Accounts (FIN Domain)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **FIN-01..10** | Financial ERP Suite | `Finance&Accounts/FIN-01.docx` through `FIN-10.docx` | Fee structures, online payment reconciliation, fee receipts, scholarships, payroll accounting (DEC-05), trial balance, budgeting. | **PLANNED / DOCUMENTED ONLY** | Full `fin` schema exists in staging DB. Code implementation intentionally deferred to Phase 4 under DEC-10. | `DOCUMENTED ONLY` |

### 3.8 Campus Management & Communications (CAM Domain)

| Module ID | Module Name | Canonical Specification Path | Originally Intended Scope | Current Implementation Status | Differences / Gaps | Evidence Classification |
| :--- | :--- | :--- | :--- | :---: | :--- | :---: |
| **CAM-01** | Announcements & Communications | `Communication&AnnnouncementModule/` (Empty) | Multichannel campus broadcasts, emergency SMS/Email notifications, audience scoping. | **SOURCE NOT FOUND** | The design archive directory was completely empty (DEC-07). Backlog specification created in `docs/trial/POST_TRIAL_BACKLOG.md`. | `DOCUMENTED ONLY` |

---

## 4. Architectural Decisions Register Summary (DEC-01 through DEC-15)

The project is governed by 15 formal Architecture Decisions documented in [docs/trial/DECISIONS.md](file:///d:/CampXSync/CampXSync_V2.0/docs/trial/DECISIONS.md):

1. **DEC-01 (Supabase/PostgreSQL Source of Truth)**: Replaced MongoDB, Kafka, and Redis with a consolidated multi-schema Supabase PostgreSQL architecture.
2. **DEC-02 (Consolidated Learning Resource Master)**: Unified ACD-08 and LMS-07 into a single `lms.resources` table in PostgreSQL.
3. **DEC-03 (Course Master vs Delivery Offerings)**: Scoped `acd.courses` as the tenant course master; timetable and attendance bind to `acd.course_sections`.
4. **DEC-04 (Authoritative Subject Master)**: Designated `acd.subjects` as the single authoritative subject catalog with composite key `(tenant_id, id)`.
5. **DEC-05 (Payroll Transaction Ownership)**: Designated HRM-05 as the primary transaction owner for salary computation; FIN-07 acts as general ledger projection.
6. **DEC-06 (Recruitment & Induction Specification Split)**: Formally separated HRM-06 (Applicant Tracking) from HRM-07 (Employee Induction).
7. **DEC-07 (CAM Source Package Gap)**: Formally recorded the empty `Communication&AnnnouncementModule` archive directory as a source gap.
8. **DEC-08 (Platform Administration vs College Tenant Boundaries)**: Enforced strict separation: Platform Admin (`tenant_id IS NULL`, `iam.is_platform_admin()`) cannot read tenant operational rows; College Admin operates strictly within `tenant_id = core.current_tenant_id()`.
9. **DEC-09 (Canonical LMS Specification Versioning)**: Designated `LMS-01-v2.0.docx` as canonical, superseding `LMS-01.docx` (v1.0).
10. **DEC-10 (63-Module Traceability Baseline)**: Mandated tracking all 63 modules with explicit statuses to prevent false claims of completion.
11. **DEC-11 (Student Master Contract)**: Established student identity in `core.persons` + `stu.students`, resolving the missing specification in archive.
12. **DEC-12 (Global Shared Platform Capabilities)**: Centralized IAM, audit logging, outbox, and user preferences into dedicated `iam` and `audit` schemas.
13. **DEC-13 (Next.js 15 Unified Cloud MVP)**: Unified the deployable cloud MVP into `frontend/` (Next.js 15 App Router) on Vercel while preserving legacy Java microservices.
14. **DEC-14 (Statutory Attendance Shortage Threshold 75%)**: Codified statutory 75.0% attendance compliance threshold across reporting and alerts.
15. **DEC-15 (RFC 7807 Errors & Direct DB Role Resolution)**: Standardized error responses to RFC 7807 Problem Details and implemented authoritative server-side PostgreSQL fallback for role resolution.

---

## 5. Current Implementation State & Branch Baseline

### 5.1 Repository State
- **Branch**: `ReviewBranch` tracking `origin/ReviewBranch`
- **HEAD Commit**: `b307149` (`docs(scripts): document loader flag in verify_login_hardening run comment`)
- **Working Tree State**:
  - Tracked modifications: Clean (0 modified tracked files)
  - Untracked files preserved:
    - `CampXSync_Login_Performance_Investigation.docx`
    - `docs/CampXSync_GSD_Performance_Pack/`
    - `scripts/probe_resolve_role_latency.mjs`
    - `vercel.json`

### 5.2 Deployed Environments Baseline
- **Vercel Production / Live Alias**: `https://frontend-murex-one-58.vercel.app` (Deployment ID: `dpl_ExCRqLCy6Z88Bz3VsiPmv6FaKQbX`)
- **Vercel Preview**: `https://frontend-n34dbtn83-camp-xs-ync.vercel.app` (Deployment ID: `dpl_7oE8yDDTT6TAKJyDkkg8aAmpWdfe`)
- **Staging Database Target**: Supabase PostgreSQL 17.11 (`kpwdvvcschankgggitnj`)
- **Deployment Protection**: Vercel Automation Bypass Secret enabled on Preview deployments.

---

## 6. Known Incomplete Work and Unresolved Blockers

1. **Server Direct SQL RLS Bypass (CRITICAL ARCHITECTURAL GAP)**:
   - Server-side database operations in `frontend/src/lib/db.ts` connect as the PostgreSQL table owner (`postgres.kpwdvvcschankgggitnj`).
   - In PostgreSQL, Row-Level Security is **bypassed by table owners** unless `ALTER TABLE ... FORCE ROW LEVEL SECURITY` is applied.
   - Catalog introspection confirms that **0 of the 458 tables have forced RLS**.
   - Therefore, Row-Level Security does NOT protect any query executed through `frontend/src/lib/db.ts`. Tenant isolation relies entirely on application-level query predicates (`WHERE tenant_id = $1`).
2. **Unsigned Test-Session Cookie (CRITICAL SECURITY GAP)**:
   - The cookie `campx_test_session` is serialized as plain JSON without cryptographic signatures or HMAC verification.
   - On Vercel Preview (where `isTestSessionAllowed()` returns true if `ENABLE_TEST_SESSION=true`), any client can forge this cookie and assume `SUPER_ADMIN` privileges.
3. **Unrevoked Bypass Secret in Git History (CRITICAL CREDENTIAL RISK)**:
   - Commit `531a73c` exposed a Vercel automation bypass token in plaintext in `scripts/verify_faculty_auth_and_routing.mjs`.
   - The secret remains in Git history and has not been confirmed rotated or revoked in Vercel.
4. **Unpromoted Regional Pinning (`sin1`)**:
   - `frontend/vercel.json` pins function regions to `sin1` in commit `b4e2b99`.
   - The active Vercel deployment (`dpl_ExCRqLCy6Z88Bz3VsiPmv6FaKQbX`) was deployed from commit `b542340` (prior to `b4e2b99`), meaning the live deployment may still execute in default US regions (`iad1`).
5. **Student Dashboard Feature Gap**:
   - `/student` is a static/sample demonstration screen. Real student workflows (viewing personal session records, fee payments, elective selection) are completely unimplemented.
6. **Out-of-Scope Enterprise Modules (51 Modules)**:
   - Examinations, Finance, Advanced LMS, and full HRMS have database tables in Supabase Staging but zero frontend or server action implementation.

---

## 7. Audit Deliverables Cross-Reference

For exhaustive technical analysis, consult the companion deliverables in `docs/audit/`:
- **Feature Inventory Matrix**: [CAMPXSYNC_FEATURE_INVENTORY.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_FEATURE_INVENTORY.md) & [CAMPXSYNC_FEATURE_INVENTORY.csv](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_FEATURE_INVENTORY.csv)
- **API & Gateway Architecture**: [CAMPXSYNC_API_GATEWAY_AND_SERVICE_INVENTORY.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_API_GATEWAY_AND_SERVICE_INVENTORY.md)
- **Security & RLS Code Verification**: [CAMPXSYNC_SECURITY_AND_RLS_AUDIT.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_SECURITY_AND_RLS_AUDIT.md)
- **Database & Migration History**: [CAMPXSYNC_DATABASE_AND_MIGRATION_HISTORY.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_DATABASE_AND_MIGRATION_HISTORY.md)
- **Editable System Architecture Diagrams**: [CAMPXSYNC_ARCHITECTURE_FLOWS.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_ARCHITECTURE_FLOWS.md)
- **Consolidated Validation Register**: [CAMPXSYNC_VALIDATION_REGISTER.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_VALIDATION_REGISTER.md)
- **Gaps, Risks & Readiness Assessment**: [CAMPXSYNC_GAPS_AND_READINESS.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_GAPS_AND_READINESS.md)
- **Chronological Project Timeline**: [CAMPXSYNC_PROJECT_TIMELINE.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_PROJECT_TIMELINE.md)
- **Executive Handover Digest**: [CAMPXSYNC_HANDOVER_DIGEST.md](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/CAMPXSYNC_HANDOVER_DIGEST.md)
