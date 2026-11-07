# CampXSync — Master Engineering Handover to Cursor AI
**Document:** `docs/handover/cursor/MASTER_HANDOVER.md`  
**Effective Date:** November 7, 2026  
**Incoming Coding Partner:** Cursor AI  
**Outgoing Assistant:** Antigravity AI Platform Architecture Team  
**Governing Branch:** `feature/java-backend-integration`  
**Current HEAD Commit:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`  
**Working Tree Status:** Clean (Verified)  

---

## 1. Executive Summary

This master engineering handover provides a comprehensive, empirical, and evidence-backed knowledge transfer of the CampXSync College ERP codebase to **Cursor AI**, which assumes the role of official coding partner on **November 7, 2026**.

CampXSync is an enterprise multi-tenant College Enterprise Resource Planning (ERP) platform designed for higher education institutions in India and global markets. The platform architecture enforces a strict four-tier transactional path:
$$\text{Next.js 15 UI} \longrightarrow \text{API Gateway (Port 8080)} \longrightarrow \text{Java Domain Microservices} \longrightarrow \text{Supabase / PostgreSQL 17}$$

Over Waves 0 through 10, the project transitioned from early monolithic prototypes into a distributed microservice ecosystem comprising:
- **14 Active Java Microservices** organized across 6 business domains under `services/{domain}-services/`.
- **1 Unified Java HTTP API Gateway** (`api-gateway/`, Port 8080) handling JWT identity resolution, routing, and HMAC-SHA256 request signing.
- **1 Canonical Frontend Application** (`frontend/`) built on Next.js 15 (React 19 App Router), with role-scoped portals for Platform Admin (`/platform`), College Admin (`/college`), Faculty (`/faculty`), and Student/Applicant (`/student`).
- **PostgreSQL 17 Database** on development Supabase instance `azulbaxszsqxymqmlwfe` housing 20 application schemas, 458 application tables, 1,545 RLS policies, 6 partitioned event tables, and a dedicated unprivileged application role (`campx_app_server`).
- **CampXSync Intelligence Layer Integration** with governed outbox projections (`sys.outbox_events`) and versioned domain event streams (e.g. 5 streams in Admissions STM-02).

The baseline commit for this handover is **`44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`**. Autonomous wave execution is currently **STOPPED AT WAVE 10 COMPLETION**. All Wave 1–10 deliverables, directory restructuring, STM-02 Admissions, and STM-01 Student Dashboard Phase 3b/4 are implemented, verified, and preserved.

---

## 2. Git Repository & Working Tree State

### 2.1 Branch Identity & HEAD Commit
- **Repository Path:** `D:\CampXSync\CampXSync_V2.0`
- **Mandatory Branch:** `feature/java-backend-integration`
- **Tracking Remote:** `origin/feature/java-backend-integration`
- **Current HEAD Commit SHA:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`
- **Recent Git History (Chronological Recent):**
  1. `44d7909` - *Evaluation for Cursor & Claude Code* (Verification scripts, screenshots, test harnesses)
  2. `e111f8a` - *feat(tenancy): execute live provisioning and verification of institute APEX_SCI*
  3. `803d2c1` - *docs(evidence): document Phase 3b and Phase 4 student dashboard verification*
  4. `d758a1e` - *feat(student): implement application documents UI and route proxy for Phase 3b*
  5. `91cb726` - *feat(admissions): add scoped RLS and student 2 applicant seed for STM-01 Phase 3b*
  6. `46cd60a` - *feat(student): reinstate address linking in PostgresPersonRepository with live persistence tests*
  7. `aefc1d6` - *docs(stm-01): student dashboard phase 0-3a evidence, field mapping, and designs*
  8. `82ca9fa` - *feat(stm-01): student self-service pages, route proxies, and navigation*
  9. `1a03355` - *feat(stm-01): student ownership RLS policies and fail-closed guard trigger*
  10. `9f038ae` - *feat(auth): redesign institutional login UI with Stitch MCP and preserve role routing*
  11. `b21d3c7` - *STM-02: E2E verified, grants tightened, handover fixes*
  12. `cfb809f` - *fix(build): update Maven reactor for domain service structure*
  13. `67c1a25` - *Services Directory Restructuring*
- **Working Tree Status:** `nothing to commit, working tree clean`.

### 2.2 Git Discipline & Safety Constraints
- **NO Branch Switching:** Do not checkout `main`, `Dev1.0Branch`, or create new branches.
- **NO Unapproved Commits/Pushes:** All code changes must be locally validated before committing. Do not push to remote without user sign-off.
- **NO Destructive Commands:** `git reset --hard`, `git clean -fd`, and `git checkout .` are prohibited.

---

## 3. Complete Architecture & Component Map

```
                                  ┌─────────────────────────────┐
                                  │      Client Browsers        │
                                  │  (Next.js 15 / React 19)    │
                                  └──────────────┬──────────────┘
                                                 │ HTTPS / REST
                                                 ▼
                                  ┌─────────────────────────────┐
                                  │     CampX API Gateway       │
                                  │      Port 8080 (Java)       │
                                  │  - Supabase JWT Validation  │
                                  │  - HMAC-SHA256 Signing      │
                                  │  - Identity Claim Injection │
                                  └──────────────┬──────────────┘
                                                 │ Internal HTTP + HMAC
        ┌───────────────────┬────────────────────┼───────────────────┬───────────────────┐
        ▼                   ▼                    ▼                   ▼                   ▼
┌───────────────┐   ┌───────────────┐    ┌───────────────┐   ┌───────────────┐   ┌───────────────┐
│ Administration│   │   Academic    │    │    Student    │   │  Attendance   │   │   Analytics   │
│   Services    │   │   Services    │    │   Services    │   │   Services    │   │   Services    │
│  ADM-01: 8081 │   │  ACD-01: 8083 │    │  STU-01: 8093 │   │  ACD-06: 8088 │   │  ACD-10: 8092 │
│  ADM-02: 8082 │   │  ACD-02: 8084 │    │  STM-02: 8094 │   └───────┬───────┘   └───────┬───────┘
└───────┬───────┘   │  ACD-03: 8085 │    └───────┬───────┘           │                   │
        │           │  ACD-04: 8086 │            │                   │                   │
        │           │  ACD-05: 8087 │            │                   │                   │
        │           │  ACD-07: 8089 │            │                   │                   │
        │           │  ACD-09: 8091 │            │                   │                   │
        │           └───────┬───────┘            │                   │                   │
        │                   │                    │                   │                   │
        └───────────────────┼────────────────────┴───────────────────┴───────────────────┘
                            ▼
      ┌─────────────────────────────────────────────────────────┐
      │               PostgreSQL 17 (Supabase)                  │
      │         Reference: azulbaxszsqxymqmlwfe                 │
      │  - Dedicated App Role: campx_app_server (Non-Superuser) │
      │  - Row-Level Security (RLS) on 458/458 Tables           │
      │  - Multi-Tenant Isolation via tenant_id Claims          │
      │  - Partitioned Append-Only Audit & Outbox Tables        │
      └─────────────────────────────────────────────────────────┘
```

### 3.1 Repository Inventory & Directory Layout
| Directory / File | Type | Technology | Purpose & Operational Role |
| :--- | :--- | :--- | :--- |
| `api-gateway/` | Java Microservice | Java 8, HttpServer, Supabase JWT | Ingress API Gateway, JWT verification, reverse proxy routing, HMAC-SHA256 signing (Port 8080). |
| `frontend/` | Web Application | Next.js 15, React 19, TailwindCSS | **Canonical CampXSync Frontend**. Role-based routing (`/platform`, `/college`, `/faculty`, `/student`). |
| `frontend-web/` | Web Prototype | Vite, React | **RETIRED & DEPRECATED**. Preserved for historical reference; do not deploy. |
| `logger/` | Java Shared Library | Java 8, SLF4J, Logback | `com.campx.platform:campx-logger` shared platform logging, MDC tracing, and security config. |
| `services/academic-services/` | Java Domain | Java 8, Maven Aggregator | Houses 7 academic microservices (`course`, `curriculum`, `subject`, `batch`, `timetable`, `calendar`, `assessment`). |
| `services/administration-services/`| Java Domain | Java 8, Maven Aggregator | Houses `institute-admin-service` (ADM-01) and `college-admin-service` (ADM-02). |
| `services/student-services/` | Java Domain | Java 8, Maven Aggregator | Houses `student-service` (STU-01) and `admissions-service` (STM-02). |
| `services/attendance-services/`| Java Domain | Java 8, Maven Aggregator | Houses `attendance-service` (ACD-06). |
| `services/lms-services/` | Java Domain | Java 8, Maven Aggregator | Houses `learning-resource-service` (ACD-08). |
| `services/analytics-services/` | Java Domain | Java 8, Maven Aggregator | Houses `analytics-service` (ACD-10). |
| `services/ai-services/` | Scaffolded (.gitkeep) | N/A | Placeholder for future Intelligence Layer adapters and ML agents. |
| `services/communication-services/`| Scaffolded (.gitkeep) | N/A | Placeholder for future notification service. |
| `services/examination-services/`| Scaffolded (.gitkeep) | N/A | Placeholder for future examination service. |
| `services/faculty-services/` | Scaffolded (.gitkeep) | N/A | Placeholder for future faculty service. |
| `services/identity-services/` | Scaffolded (.gitkeep) | N/A | Placeholder for centralized IAM/SSO token exchange. |
| `database/` | Database Artifacts | PostgreSQL SQL DDL | Schema scripts (`Scripts_V3.0/`), migrations, rollbacks, seeds, and forensic audit reports. |
| `docs/` | Engineering Knowledge | Markdown (.md) | 185 indexed documents covering architecture, wave reports, forensic audits, and specifications. |
| `UserStories/` | Business Specifications| CSV Matrix | Architecture-aligned User Story backlogs for STM-01 and STM-02. |
| `scripts/` | Verification & Tooling | Node.js, PowerShell | Live E2E test runners, role routing suites, provisioning scripts, and database verifiers. |

---

## 4. Current Implementation Status by Functional Domain

Every functional domain has been evaluated based on empirical code and database inspection:

| Functional Domain | Code | Active Microservices | Persistence Status | Status Classification | Key Highlights / Gaps |
| :--- | :---: | :--- | :--- | :---: | :--- |
| **Administration** | ADM | ADM-01 (8081), ADM-02 (8082) | PostgreSQL + In-Memory Fallback | **VERIFIED COMPLETE** | Institutional onboarding saga, 30 system roles seeded, college settings, department & program management. |
| **Academic Management** | ACD | ACD-01 to ACD-07, ACD-09, ACD-10 | PostgreSQL (ACD-04, 05, 06, 10); Mock/In-Memory (01, 02, 03, 07, 09) | **PARTIALLY IMPLEMENTED** | Relational persistence verified for Batches (04), Timetables (05), Attendance (06), Analytics (10). Courses (01), Curricula (02), Subjects (03) rely on mock/in-memory repositories in deployed runtimes. |
| **Student Management** | STM | STU-01 (8093), STM-02 (8094) | PostgreSQL JDBC | **VERIFIED COMPLETE** | Master student profiles (STM-01), full admissions lifecycle (STM-02), student documents RLS, and applicant self-service. |
| **Learning Management** | LMS | ACD-08 (8090) | In-Memory Engine | **PARTIALLY IMPLEMENTED** | Learning resource versioning & syllabus mapping operational in-memory; PostgreSQL persistence pending. |
| **Attendance Services** | ACD | ACD-06 (8088) | PostgreSQL JDBC | **VERIFIED COMPLETE** | Session capture, student marking, optimistic concurrency locking (`row_version`), and audited correction workflow. |
| **Analytics & Reporting** | ACD | ACD-10 (8092) | PostgreSQL JDBC | **VERIFIED COMPLETE** | Role-specific dashboards (`COLLEGE_ADMIN`, `FACULTY`, `HOD`, `PLATFORM_ADMIN`), statutory 75% shortage detection, 14-day trends. |
| **Examination Management**| EXM | EXM-01 (scaffolded) | Unimplemented | **NOT IMPLEMENTED** | Assessment mapping rules defined in ACD-09; physical examination scheduling and grading not implemented. |
| **Human Resource Management**| HRM | FAC-01 (scaffolded) | Partial (via ADM-02) | **PARTIALLY IMPLEMENTED** | Faculty profiles managed under `core.persons` and `adm.departments`; standalone workload/payroll unimplemented. |
| **Finance Management** | FIN | N/A (scaffolded) | Unimplemented | **NOT IMPLEMENTED** | Fee collection, student billing, and accounting modules not yet implemented. |
| **Campus Management** | CAM | N/A (scaffolded) | Unimplemented | **NOT IMPLEMENTED** | Facilities, room scheduling (partially in ACD-05), hostel, and transport modules not yet implemented. |
| **Shared Services** | SYS | `api-gateway`, `logger` | PostgreSQL (`sys.outbox_events`, `audit.events`) | **VERIFIED COMPLETE** | JWT validation, HMAC security boundary, correlation tracing, audit outbox ledger. |
| **Intelligence Services** | AI | `ai-service` (scaffolded) | Governed Outbox Adapter | **PARTIALLY IMPLEMENTED** | 5 governed admissions streams implemented via `ControlledIntelligenceAdapter`; physical AI engine pending. |

---

## 5. Database Architecture & Safety Status

### 5.1 Environment Safety Matrix
- **PRODUCTION (FORBIDDEN):** `ekspahbsbwdpraaqfqui` — Strictly prohibited. No reads, writes, or network requests.
- **LEGACY STAGING (RETIRED):** `kpwdvvcschankgggitnj` — Decommissioned. All tests targeting this ref are obsolete.
- **ACTIVE DEVELOPMENT (AUTHORIZED):** `azulbaxszsqxymqmlwfe` (`CampXSync-Architecture-Dev`). All live verification occurs here.

### 5.2 Database Catalog Highlights (Catalog Forensic Audit Baseline)
- **20 Application Schemas:** `acd`, `audit`, `bud`, `cam`, `cfg`, `core`, `doc`, `exm`, `fin`, `hrm`, `iam`, `lms`, `obe`, `pay`, `plat`, `rpt`, `stu`, `sys`, `tax`, `wf`.
- **458 Application Tables:** 100% have `relrowsecurity = true`.
- **4 Tier 1 Tables with FORCE RLS:** `core.persons`, `stu.students`, `stu.guardianships`, `acd.batch_students`.
- **Dedicated Application Role:** `campx_app_server` (`LOGIN`, `NOSUPERUSER`, `NOBYPASSRLS`, `CONNECTION LIMIT 25`).
- **6 Partitioned Parent Tables (30 Monthly Partitions):** `audit.events`, `audit.change_log`, `audit.access_events`, `cam.delivery_events`, `hrm.attendance_punches`, `lms.progress_events`.
- **GiST Exclusion Constraints:** Atomic exclusion constraints on `acd.timetable_entries` (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) preventing double-booking at the database engine level.

### 5.3 Migration State
- The database lacks an automated schema migration table (`supabase_migrations.schema_migrations`).
- DDL was applied incrementally via reviewed psql scripts and MCP tools.
- Recent verified migrations on `azulbaxszsqxymqmlwfe`:
  - `20261101000100_create_stu_admissions_tables.sql` (9 admissions tables).
  - `20261102000100_stu_admissions_tighten_grants.sql` (grant tightening).
  - `20261105000100_stm01_student_ownership_rls.sql` (student self-service RLS).
  - `20261105000200_stm01_applicant_documents_rls.sql` (applicant document upload RLS).
  - `20261105000300_seed_student2_applicant.sql` (applicant test persona).
  - `20261105000400_provision_apex_sci.sql` (tenant APEX_SCI provisioning).

---

## 6. Frontend Handover & Verification Status

- **Technology Stack:** Next.js 15.0.0, React 19, TypeScript 5, TailwindCSS 3.4.
- **Static Analysis:** `npx tsc --noEmit` completes with **0 compilation errors**.
- **Production Build:** `npm run build` completes with **26/26 routes successfully generated**.
- **Role Routing Matrix (Gate B):** Verified 32/32 assertions passing across `/platform`, `/college`, `/faculty`, and `/student`.
- **Direct Database Access Retirement:** Direct SQL imports (`@/lib/db`) retired across 13 major routes in favor of Gateway HTTP proxy calls.
- **Test Session Gate:** `frontend/src/lib/auth/test-session-gate.ts` unconditionally fails-closed in deployed/production environments (`process.env.NODE_ENV === 'production'`).

---

## 7. Known Issues & Critical Blockers

1. **Academic Services Running on In-Memory Fallbacks:**
   - `course-management-service` (ACD-01), `curriculum-service` (ACD-02), `subject-management-service` (ACD-03), and `academic-calendar-service` (ACD-07) contain in-memory test doubles that are active by default unless configured for PostgreSQL.
   - *Impact:* Changes made via ACD-01/02/03/07 do not persist across service restarts in deployed staging.
   - *Fix Needed:* Implement PostgreSQL JDBC repositories matching the pattern in `PostgresTimetableRepository` and `PostgresAttendanceRepository`.
2. **Missing Migration History Table:**
   - The database lacks a version tracking table (`schema_migrations`), making migration tracking reliant on git repository logs.
3. **Intelligence Layer Physical Service Pending:**
   - Domain microservices emit events via `ControlledIntelligenceAdapter` to `sys.outbox_events`. The physical consumer service (`ai-service`) is currently a scaffolded placeholder.
4. **Wave 11 Production Cutover Halted:**
   - Production readiness activities (formal Gate G3 JDK 8 audit, vulnerability remediation, production database synchronization) are stopped awaiting user authorization.

---

## 8. Prioritized Critical Path for Cursor AI

Cursor should prioritize functional completeness and platform stability in the following sequence:

```
[Wave 10 Baseline] ──► [Option A: STM-03 Enrollment Service] ──► [ACD Relational Hardening] ──► [Wave 11 Production Cutover]
```

1. **Step 1: Baseline Verification**
   - Verify working tree and execute `cd frontend && npx tsc --noEmit` to confirm zero static analysis regressions.
2. **Step 2: STM-03 Academic Enrollment & Course Registration**
   - Build `services/student-services/enrollment-service` (or integrate into `student-service`) to consume the `admissions.enrolment.handover_requested.v1` event contract from STM-02.
   - Integrate course section assignment with ACD-04 and prerequisite validation with ACD-01.
3. **Step 3: ACD Relational Persistence Hardening**
   - Implement PostgreSQL JDBC repositories for ACD-01 (Courses), ACD-02 (Curricula), and ACD-03 (Subjects) to retire in-memory defaults.
4. **Step 4: Execute Wave 11 Production Readiness (When Authorized)**
   - Complete Gate G3 assessment, synchronize production schema with development ref `azulbaxszsqxymqmlwfe`, and execute final staging cutover runbook.

