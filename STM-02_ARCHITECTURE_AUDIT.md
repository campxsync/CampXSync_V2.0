# STM-02 — Admissions & Application Management Service
## Master Architecture Audit & Baseline Report

**Document ID:** STM02-AUDIT-001  
**Execution Context:** CampXSync Enterprise College ERP (Multi-Tenant SaaS)  
**Execution Role:** Lead Solution Architect, Senior Java Backend Architect, Database Architect, Security Architect & Implementation Lead  
**Authorization Mode:** READ-ONLY AUDIT (Phase 1 / Gate 0–3) — Zero Database Writes, Zero Migrations, Zero Branch Changes  
**Audit Date:** October 30, 2026  
**Active Git Branch:** `feature/java-backend-integration`  
**Git Working Tree State:** Clean branch tracking `origin/feature/java-backend-integration` with preserved local working tree modifications and untracked files  
**Target Environment:** Safe Non-Production Read-Only Inspection (Production `ekspahbsbwdpraaqfqui` and Legacy Staging `kpwdvvcschankgggitnj` STRICTLY PROHIBITED; Development Reference `azulbaxszsqxymqmlwfe` referenced safely without writes)

---

## 1. Executive Summary

STM-02 (Admissions & Application Management Service) is responsible for governing the comprehensive student intake journey within the CampXSync platform. Its bounded lifecycle encompasses intake configuration, public applicant engagement, application drafting, validation, completeness checks, versioned eligibility evaluation, document verification, departmental review, rubric assessment, admission decision-making, offer generation, applicant acceptance/decline/expiry, fee reconciliation, and idempotent handover to Student Identity (STM-01) and Student Enrolment (STM-03).

This architecture audit was conducted under strict **Audit-Before-Implementation** governance to ensure that STM-02 fits seamlessly into CampXSync's enterprise multi-tenant architecture, reuses established platform capabilities, respects existing service boundaries, and introduces zero architectural debt, security vulnerabilities, or duplicate persistence structures.

### Key Audit Findings:
1. **Database Persistence Reality:** No admissions tables or schemas currently exist in PostgreSQL/Supabase. While `stu.students`, `core.persons`, `core.tenants`, `doc.documents`, `wf.instances`, and `sys.outbox_events` are established, all candidate admissions entities (applications, reviews, offers, eligibility rules) are currently **PROPOSED** and require formal schema design within the `stu` schema under approved ADRs.
2. **Platform Component Reuse:** The platform already features a production-grade Transactional Outbox (`com.campx.platform.outbox.TransactionalOutboxWriter` backed by `sys.outbox_events`), Platform Audit Repository (`com.campx.platform.audit.PostgresAuditRepository` backed by `audit.events` and `audit.change_log`), and Controlled Intelligence Adapter (`com.campx.platform.intelligence.ControlledIntelligenceAdapter`). STM-02 will directly reuse these shared capabilities.
3. **Service Boundary Determination:** STM-02 must be established as an independent bounded microservice (`services/student-services/admissions-service`, port 8094) rather than coupling it into STM-01 (`student-service`). This preserves security isolation (protecting enrolled student records from public applicant traffic surges) and adheres to the multi-service domain layout.
4. **Applicant Identity Model (ADR D-01):** Applicants are pre-admission individuals who must NOT prematurely generate `stu.students` profiles. Pre-admission bio-demographics map to `core.persons` with a dedicated `APPLICANT` persona or staging record. Canonical `stu.students` creation occurs strictly upon admission offer acceptance via STM-01's verified `enrollStudent` contract.
5. **RBAC & Missing Canonical Roles (ADR D-13):** The enterprise catalogue R01–R30 does not currently define `Applicant`, `Admissions Staff`, or `Admission Approver`. These actors must be formalized through approved role mapping and ABAC data scopes before implementation.
6. **Implementation Readiness:** STM-02 is classified as **NOT READY FOR CODE IMPLEMENTATION**. Read-only audit is complete. Architecture decisions (D-01 through D-15) must be formally approved before physical DDL migrations or service implementation begins.

---

## 2. Phase 0: Repository and Governance Baseline

### 2.1 Git Status & Branch Verification
- **Current Branch:** `feature/java-backend-integration` (Verified via `git status`).
- **Remote Tracking:** Up to date with `origin/feature/java-backend-integration`.
- **Pre-existing Working Tree State:** Uncommitted changes in `docs/project-control/PROGRESS.md`, `docs/reports/wave-4/COMPLETION_REPORT.md`, `frontend/src/app/(dashboard)/platform/audit/page.tsx`, `frontend/src/app/(dashboard)/student/page.tsx`, `frontend/src/app/api/college/departments/route.ts`, `frontend/src/app/api/college/programs/route.ts`, and `prompts/NEXT_ANTIGRAVITY_TASK.md` were preserved with zero resets, stashes, or overwrites.
- **Untracked Files:** Preserved completely, including `UserStories/STM-02_Updated_Architect_User_Stories.csv`.

### 2.2 Authoritative Document Inspection
| Document | Location Verified | Status / Relevance |
|---|---|---|
| Working Backlog CSV | `UserStories/STM-02_Updated_Architect_User_Stories.csv` | Authoritative STM-02 Backlog (55 items: 44 Stories, 11 Enablers, 257 SP). |
| Technical Design Spec | `D:\CampXSync\Documentation\StudentModule\MicroServices\STM-02.docx` | Design intent for STM-02 service, state machine, entities, candidate APIs. |
| Enterprise RBAC Matrix | `docs/architecture/Security/CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` | Authoritative R01–R30 catalogue, action vocabulary, and STM permission table. |
| Wave Control Rules | `.agent/rules/campxsync-wave-control.md` | Persistent rules: single bounded task, safety controls, no unapproved DB writes. |
| Progress Checkpoint | `docs/project-control/PROGRESS.md` | Platform Wave 10 complete; domain reorganization documented. |
| Next Task Directive | `prompts/NEXT_ANTIGRAVITY_TASK.md` | Wave 11 pending user authorization; STM-02 execution prompted. |

### 2.3 Domain Services Directory Structure
On October 30, 2026, the microservices directory was successfully reorganized into domain hierarchies (`services/{domain}-services/{microservice-name}/`):
```
services/
├── academic-services/          (course, curriculum, subject, batch, timetable, calendar, assessment)
├── administration-services/    (institute-admin-service, college-admin-service)
├── ai-services/                (advisory, intelligence models)
├── analytics-services/         (analytics-service - ACD-10)
├── attendance-services/        (attendance-service - ACD-06)
├── communication-services/     (notifications, messaging)
├── examination-services/       (valuation, exam scheduling, hall tickets)
├── faculty-services/           (faculty allocation, profile)
├── identity-services/          (IAM, custom access token hook)
├── lms-services/               (learning-resource-service - ACD-08)
└── student-services/           (student-service - STM-01)
```
The root Maven `pom.xml` orchestrates 22 reactor modules compiled against Java 1.8 (`<source>1.8</source>`, `<target>1.8</target>`).

---

## 3. Phase 1: Database Forensic Audit

### 3.1 Schema Inventory & Ownership Analysis
A complete forensic review of `database/migrations/Scripts_V3.0/00_extensions_schemas.sql` and `20_tables.sql` was conducted:

| Schema | Tables Count | Bounded Context & Existing Ownership |
|---|---|---|
| `core` | 25 | Multi-tenancy (`tenants`), organizational hierarchy (`colleges`, `campuses`, `departments`), academic references (`academic_years`, `calendars`), human bio-demographics (`persons`, `person_identifiers`, `person_contacts`, `person_qualifications`, `addresses`), number sequences (`number_sequences`). |
| `stu` | 2 | Student domain anchor: `students` (institutional enrollment anchor referencing `core.persons`) and `guardianships` (family relationships). **Zero admissions tables exist.** |
| `iam` | 19 | Identity & Access Management: `permissions`, `roles`, `role_permissions`, `user_profiles`, `user_groups`, `role_assignments`, `department_access`, `api_clients`. |
| `acd` | 32 | Academic Master: `programs`, `curricula`, `subjects`, `courses`, `batches`, `course_offerings`, `timetables`, `attendance_sessions`. |
| `fin` | 44 | Financial Ledger & Transactions: `fee_structures`, `fee_components`, `fee_obligations`, `payments`, `receipts`, `gateway_transactions`, `scholarships`. |
| `doc` | 13 | Document Metadata & Storage: `document_types`, `documents`, `document_versions`, `verifications`, `templates`, `signatures`. Binary files reside in Supabase Object Storage (`storage.objects`). |
| `wf` | 9 | Generic Workflow & Approval Engine: `definitions`, `definition_steps`, `policies`, `instances`, `tasks`, `decisions`. |
| `audit` | 20 | Platform Audit & Compliance: `events` (partitioned), `change_log` (partitioned), `access_events`, `compliance_rules`, `sod_rules`, `evidence`. |
| `sys` | 11 | Platform Shared Infrastructure: `outbox_events`, `inbox_events`, `idempotency_keys`, `dead_letter_events`, `entity_locks`, `jobs`. |
| `cam` | 25 | Unified Communications: `providers`, `campaigns`, `messages`, `deliveries`, `notification_rules`. |

### 3.2 Admissions Persistence Gap
- **Finding:** There are **NO tables, foreign keys, or indexes** related to admissions applications, intake windows, applicant reviews, or admission offers anywhere in PostgreSQL or migration scripts.
- **Traceability:** Legacy MongoDB collections (`collection_mapping.csv`) did not include admissions (`STU/IAM modules were not in the uploaded files`). STM-01 was established directly in PostgreSQL.
- **Verification:** Candidate entities in `STM-02.docx` (`applications`, `application_documents`, `application_status_history`, `application_reviews`, `admission_offers`) are logical candidates only and have not been created in physical DDL.

---

## 4. Phase 2: Java Service & API Architecture Audit

### 4.1 Existing Service Analysis
1. **STM-01 (`services/student-services/student-service`, Port 8093):**
   - Implements 49 user stories governing student profiles, document metadata, guardianships, and lifecycle transitions.
   - Persistence layer: `PostgresStudentRepository`, `PostgresPersonRepository`, `PostgresPersonIdentifierRepository`, `PostgresStudentAddressRepository`, `PostgresStudentDocumentRepository`, `PostgresGuardianshipRepository`.
   - Security: `GatewayHmacVerifier` (HMAC-SHA256, 300s window) and `UserSecurityContext` (transaction-scoped role switching to `authenticated` with `request.jwt.claims`).
   - Handover Contract: Provides `enrollStudent(UserSecurityContext context, EnrollStudentRequest request, String idempotencyKey)` returning `StudentProfileResponse`.
2. **API Gateway (`api-gateway`, Port 8080):**
   - Reverse proxy routing table (`GatewayConfig.java`) routes `/api/v1/students`, `/v1/students`, and `/v1/student-roster` to `student-service` (port 8093).
   - Routes academic requests to ACD-01 through ACD-10 (ports 8083–8092).
   - **Gap:** Zero routes exist for `/api/v1/admissions` or `/v1/admissions`.
3. **Platform Shared Infrastructure (`logger/src/main/java/com/campx/platform/`):**
   - `TransactionalOutboxWriter` writes atomically to `sys.outbox_events`.
   - `PostgresAuditRepository` writes immutable audit entries to `audit.events` and `audit.change_log`.
   - `ControlledIntelligenceAdapter` emits versioned domain context events to the CampXSync Intelligence Layer.

### 4.2 Microservice Boundary Decision
- **Architecture Decision:** STM-02 shall be built as a distinct Spring Boot microservice:  
  `services/student-services/admissions-service` (target port: 8094).
- **Architectural Justification:**
  - **Security & Attack Surface:** Admissions portals are exposed to unauthenticated or guest prospective applicants. Combining this ingress with `student-service` would expose internal enrolled student records to public traffic.
  - **Traffic Patterns:** Admissions experience massive seasonal spikes during application intake deadlines. Isolating STM-02 allows independent scaling without impacting daily student campus operations.
  - **Domain Separation:** An applicant is not a student. STM-02 coordinates the intake lifecycle; STM-01 is the authoritative master anchor for enrolled students.

---

## 5. Phase 3: Security, RBAC & ABAC Audit

### 5.1 Role Catalogue Alignment (R01–R30)
The authoritative catalogue in `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` defines roles R01–R30. Comparing this with STM-02 requirements:
- **R25 (Student):** Currently restricted to enrolled student self-service. An applicant is NOT yet a student.
- **Applicant Persona:** Missing from R01–R30. Must be resolved via ADR D-13.
- **Registrar / Admissions Staff:** Missing from canonical R01–R30 roles. Currently handled informally under College Admin (R04) or Institution Admin (R03).
- **Authorized Admission Approver:** Missing from canonical R01–R30 roles. Represents delegated institutional authority.
- **Department Reviewer:** Mapped to Faculty (R10) / HOD (R08) / Dean (R07) with assigned ABAC reviewer scope.

### 5.2 Multi-Tenancy & Data Isolation
- Strict server-side resolution of `tenant_id` from validated JWT claims.
- Client-supplied `tenant_id` in request bodies must be rejected.
- Composite primary keys `(tenant_id, id)` on all admissions tables.
- Database RLS policies using `tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid`.
- Two-tenant automated test harness required as a merge gate (EN-003).

---

## 6. Phase 4: Domain Integration Architecture

| Integration Path | Source Domain | Target Domain | Integration Mechanism | Data Ownership & Responsibilities |
|---|---|---|---|---|
| Academic Intake Validation | STM-02 | ACD | Synchronous REST / Internal Client | ACD owns `acd.programs`, `acd.academic_years`, `acd.batches`. STM-02 validates intake eligibility windows against authoritative ACD master data. |
| Identity & Auth Gateway | IAM / Gateway | STM-02 | Bearer JWT + Gateway HMAC | Gateway validates JWT, signs request with HMAC-SHA256, injects `X-User-Id`, `X-Tenant-Id`, `X-College-Id`, `X-User-Role`. |
| Document Metadata & KYC | STM-02 | DOC | Sync REST + Storage Reference | `doc.documents` stores metadata; binary uploaded to private Supabase Storage buckets. STM-02 stores document metadata references only. |
| Application Fee Intent | STM-02 | FIN | Asynchronous Outbox / Provider Webhook | FIN owns `fin.fee_structures`, `fin.payments`, `fin.gateway_transactions`. STM-02 maintains payment intent ID and read-only status projection. |
| Notifications & Alerts | STM-02 | CAM | Transactional Outbox Event | CAM unified gateway dispatches email/SMS/push based on `admissions.application.submitted.v1` and `admissions.offer.issued.v1` events. |
| Student Creation Handover | STM-02 | STM-01 | Idempotent REST (`/api/v1/students/enroll`) | STM-01 creates `core.persons` and `stu.students`. Triggered strictly upon offer acceptance with `Idempotency-Key`. |
| Enrolment Handover | STM-02 | STM-03 | Transactional Outbox Event | STM-03 registers student into academic cohort/term upon confirmed STM-01 creation. |
| Platform Audit & Outbox | STM-02 | SYS / AUDIT | Direct JDBC (`PostgresOutboxRepository`) | Atomic database transaction writes entity state, `sys.outbox_events`, and `audit.events`. |
| Intelligence Reporting | STM-02 | Intelligence Layer | Versioned Context Events | `ControlledIntelligenceAdapter` emits anonymized application funnel and conversion velocity events. |

---

## 7. Phase 5: Backlog Quality & Governance

The authoritative CSV at `UserStories/STM-02_Updated_Architect_User_Stories.csv` was parsed and audited:
- **Total Backlog Items:** 55 items.
- **Classification:** 11 Enablers (E00, E09, E10, E11) and 44 User Stories (E01–E11).
- **ID Uniqueness:** 100% unique IDs (`STM02-EN-001` through `STM02-EN-011`; `STM02-US-001` through `STM02-US-049`).
- **Release Allocation:**
  - **R0 (Architecture & Foundation):** 2 items, 8 Story Points (EN-001, EN-002).
  - **R1 (Core Admissions MVP):** 33 items, 152 Story Points (5 Enablers, 28 Stories).
  - **R2 (Integrations & Handover):** 11 items, 53 Story Points (4 Enablers, 7 Stories).
  - **R3 (Advanced Governance & Lifecycle):** 9 items, 44 Story Points (0 Enablers, 9 Stories).
  - **Total Effort:** 257 indicative Story Points.
- **Acceptance Criteria:** Every item contains 4–6 testable, verifiable criteria including negative security tests, optimistic locking, and tenant boundary enforcement.
- **Open Architectural Decisions:** 15 distinct ADRs referenced (`D-01` through `D-15`).

---

## 8. Summary of Architectural Decisions Requiring Approval

Before code implementation or physical database migrations begin, the following primary decisions must be formally approved:

| ADR ID | Decision Title | Recommended Option | Impact |
|---|---|---|---|
| **D-01** | Applicant Identity Model | Pre-admission Person Record in `core.persons` with `APPLICANT` persona; link/create `stu.students` only upon offer acceptance. | Prevents phantom student profile pollution. |
| **D-02** | Canonical State Machine | Strict server-side state machine with dedicated action endpoints; payment & document verification are independent orthogonal dimensions. | Prevents invalid client status patching. |
| **D-06** | Fee Requirement & Timing | Configurable per intake: Optional, Mandatory at submission, or Waived. | Accommodates public university and private college workflows. |
| **D-07** | Decision Authority Model | Two-tier separation of duties: Department Reviewer recommends/scores; Admissions Approver commits final decision. | Prevents academic review fraud. |
| **D-09** | STM-01 Creation Timing | Strictly at `admissions.offer.accepted.v1` milestone via STM-01 `enrollStudent` idempotent contract. | Eliminates duplicate student records. |
| **D-13** | Canonical Role Mapping | Formally introduce R31 (Applicant), R32 (Admissions Officer), and R33 (Admissions Approver) into enterprise catalogue. | Resolves RBAC authorization compliance. |

---

## 9. Implementation Safety Compliance

This audit adhered strictly to all non-negotiable safety rules:
- **No Database Modifications:** Zero SQL DDL, DML, seed scripts, or schema changes executed.
- **Target Safety:** Production (`ekspahbsbwdpraaqfqui`) and Legacy Staging (`kpwdvvcschankgggitnj`) were never accessed.
- **Working Tree Integrity:** Preserved current branch (`feature/java-backend-integration`), uncommitted working tree modifications, and untracked files. Zero git stash, commit, push, or reset operations executed.
- **Read-Only Gate:** Completed Gates 0 through 3 (Baseline, Forensics, Code Audit, Gap Analysis). Gates 4 through 7 remain locked.

---

## 10. Next Recommended Action

1. Review and approve the 8 audit deliverables:
   - `STM-02_ARCHITECTURE_AUDIT.md` (This document)
   - `STM-02_DATABASE_OWNERSHIP_AND_REUSE_MATRIX.md`
   - `STM-02_SERVICE_REUSE_AND_GAP_ANALYSIS.md`
   - `STM-02_RBAC_ABAC_VALIDATION.md`
   - `STM-02_DOMAIN_INTEGRATION_MATRIX.md`
   - `STM-02_BACKLOG_VALIDATION.md`
   - `STM-02_ARCHITECTURAL_DECISIONS.md`
   - `STM-02_IMPLEMENTATION_READINESS.md`
2. Formally approve ADRs D-01, D-02, D-06, D-07, D-09, and D-13.
3. Upon receiving user approval, proceed to physical DDL drafting and R0/R1 implementation under a dedicated implementation prompt.

