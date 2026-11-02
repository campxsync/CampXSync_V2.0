# STM-02 — Admissions & Application Management Service
## Database Ownership & Reuse Matrix

**Document ID:** STM02-DB-001  
**Execution Context:** CampXSync Enterprise Multi-Tenant SaaS Platform  
**Audit Date:** October 30, 2026  
**Status:** FORENSIC AUDIT COMPLETE — CANDIDATE PERSISTENCE PROPOSAL  
**Security Classification:** Strict Multi-Tenant Isolation / Fail-Closed RLS  

---

## 1. Executive Summary & Forensic Baseline

A comprehensive forensic audit of the existing CampXSync PostgreSQL schema definitions was performed by analyzing:
- `database/migrations/Scripts_V3.0/00_extensions_schemas.sql`
- `database/migrations/Scripts_V3.0/20_tables.sql`
- `database/migrations/Scripts_V3.0/30_foreign_keys.sql`
- `database/migrations/Scripts_V3.0/60_rls.sql`
- `database/migrations/Scripts_V3.0/75_grants.sql`
- Standalone migration files in `database/migrations/`

### Critical Forensic Fact:
**Zero admissions management tables currently exist in PostgreSQL.**
- The `stu` schema currently contains only two physical tables: `stu.students` and `stu.guardianships`.
- While `stu.students` contains foreign keys to `core.persons` and reference fields (`admission_no`, `admission_date`, `admission_academic_year_id`), it represents an already-enrolled student's academic anchor, NOT an in-flight admission application.
- Legacy MongoDB migration manifests (`collection_mapping.csv`) explicitly noted that Student/Admissions modules were not part of the initial legacy dataset.
- Therefore, all candidate admissions entities proposed in `STM-02.docx` represent **GAPS** in physical persistence that must be created in PostgreSQL under strict architectural governance.

---

## 2. Existing Schema Ownership & Capability Inventory

To prevent redundant tables, duplicate profile models, or parallel financial ledgers, STM-02 must integrate with established schema owners:

| Schema | Authoritative Domain Ownership | Key Reusable Tables & Capabilities | STM-02 Responsibility Boundary |
|---|---|---|---|
| `core` | Tenancy, Organization, People Master, Calendars, Sequences | `tenants`, `colleges`, `campuses`, `departments`, `academic_years`, `calendars`, `persons`, `person_identifiers`, `addresses`, `number_sequences`. | STM-02 links applications to canonical `tenants`, `colleges`, and `academic_years`. Pre-admission identity links to `core.persons`. Application numbers use `number_sequences`. |
| `stu` | Student Domain Anchor | `students`, `guardianships`. | `stu` is the logical home for student services. STM-02 admissions tables should reside in `stu` (e.g. `stu.applications`), referencing `core.persons` and handing over to `stu.students`. |
| `acd` | Academic Structure & Curriculum | `programs`, `curricula`, `subjects`, `courses`, `batches`, `course_offerings`, `timetables`. | STM-02 validates application programme/intake choices against `acd.programs` and `acd.batches`. STM-02 MUST NOT duplicate academic catalogues. |
| `fin` | Finance & Fee Ledger | `fee_structures`, `fee_obligations`, `payments`, `receipts`, `gateway_transactions`. | FIN is the single source of truth for money movement. STM-02 stores only payment reference (`payment_id`) and status projections. No ledger tables in STM-02. |
| `doc` | Document Metadata & Storage | `document_types`, `documents`, `document_versions`, `verifications`, `lifecycle_requests`. | Storage binaries reside in Supabase Storage (`storage.objects`). `doc.documents` stores metadata. STM-02 stores references to `doc.documents`. |
| `wf` | Generic Workflow Engine | `definitions`, `definition_steps`, `instances`, `tasks`, `decisions`, `delegations`. | Multi-step review and approval workflows can project tasks into `wf.instances` or execute via an internal deterministic state machine. |
| `audit` | Platform Audit & Compliance | `events` (partitioned), `change_log` (partitioned), `compliance_rules`, `sod_rules`. | Immutable audit logging of application creation, status transitions, reviewer scores, decisions, and offers directly via `PostgresAuditRepository`. |
| `sys` | Platform Infrastructure | `outbox_events`, `idempotency_keys`, `dead_letter_events`, `entity_locks`. | Reliable outbox publishing via `sys.outbox_events` and request deduplication via `sys.idempotency_keys`. |
| `cam` | Unified Communications | `providers`, `campaigns`, `messages`, `deliveries`, `notification_rules`. | Email/SMS/WhatsApp admissions notifications triggered via outbox events consumed by `cam` workers. |

---

## 3. Candidate Admissions Entities: Reuse, Extend & Gap Analysis

Based on `STM-02.docx` (Section 6), each candidate logical entity is evaluated against the repository and database baseline:

| Candidate Entity | Classification | Proposed Physical Table | Authoritative Schema Owner | Reuse / Extend / Gap Rationale |
|---|---|---|---|---|
| **Intake Configuration** | **GAP** | `stu.admission_intakes` | `stu` (STM-02) | Manages application windows (open/close dates, intake capacity, application quotas) for an `acd.programs` offering in an `academic_year`. Does not exist today. |
| **Eligibility Criteria** | **GAP** | `stu.admission_eligibility_rules` | `stu` (STM-02) | Versioned eligibility evaluation rules (minimum age, prior qualifications, test thresholds). Versioned snapshot referenced by applications. |
| **Application Master** | **GAP** | `stu.applications` | `stu` (STM-02) | Core intake transaction record. Tracks `application_number`, `tenant_id`, `intake_id`, `applicant_person_id`, `current_status`, `row_version`. |
| **Applicant Bio-Demographics** | **REUSE / EXTEND** | `core.persons`, `core.person_identifiers`, `core.addresses` | `core` | **REUSE EXISTING.** Do NOT create `stu.applicant_profiles`. Applicant personal details (name, DOB, email, phone) reside in `core.persons` with `status = 'APPLICANT'`. |
| **Application Documents** | **EXTEND** | `stu.application_documents` | `stu` / `doc` | Links `stu.applications` to `doc.documents`. Stores document classification (`HIGH_SCHOOL_TRANSCRIPT`, `ID_PROOF`), verification status, and verifier remarks. |
| **Application Reviews** | **GAP** | `stu.application_reviews` | `stu` (STM-02) | Stores departmental evaluator rubric scores, feedback, recommendation (`RECOMMEND_ACCEPT`, `RECOMMEND_REJECT`, `WAITLIST`), and reviewer ID. |
| **Admission Decisions** | **GAP** | `stu.admission_decisions` | `stu` (STM-02) | Auditable decision record (`OFFER_APPROVED`, `REJECTED`, `DEFERRED`, `WAITLISTED`) committed by an authorized Admission Approver. |
| **Admission Offers** | **GAP** | `stu.admission_offers` | `stu` (STM-02) | Formal offer record with unique offer code, terms version, validity window (`valid_from`, `valid_to`), and status (`ISSUED`, `ACCEPTED`, `DECLINED`, `EXPIRED`). |
| **Application Fee Reference** | **REUSE** | `fin.payments`, `fin.fee_obligations` | `fin` | **REUSE EXISTING.** Application fee obligations and payment records are maintained in `fin`. `stu.applications` stores only `fee_obligation_id` and `payment_status`. |
| **Status History / Audit** | **REUSE** | `stu.application_status_history` + `audit.events` | `stu` / `audit` | Detailed state transitions recorded in `stu.application_status_history` for operational timeline views; privileged actions also emitted to `audit.events`. |
| **Enrolment Handover Record** | **GAP** | `stu.admission_handovers` | `stu` (STM-02) | Tracks downstream student creation (`stu.students`) and cohort enrolment (`acd.batch_students`) with idempotency keys and retry counters. |

---

## 4. Physical Persistence Architecture Design (Candidate DDL Model)

### 4.1 Schema Selection
The proposed physical tables should reside within the existing **`stu`** schema.
- **Why `stu`?** CampXSync groups student-centric microservices under the `stu` database domain. Introducing a separate `adm` schema would fragment cross-table foreign keys and RLS helper functions. The prefix `admission_` or `application_` within `stu` cleanly establishes ownership without collisions.

### 4.2 Composite Primary Keys & Enterprise Multi-Tenancy
To guarantee strict tenant isolation across all tables:
- **Rule 1:** Every admissions table must include `tenant_id uuid NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT`.
- **Rule 2:** Primary keys must be composite: `PRIMARY KEY (tenant_id, id)`.
- **Rule 3:** All foreign key constraints between tenant-owned tables must include both `tenant_id` and the referenced `id`:
  ```sql
  CONSTRAINT fk_application_intake FOREIGN KEY (tenant_id, intake_id)
      REFERENCES stu.admission_intakes(tenant_id, id) ON DELETE RESTRICT
  ```
  This eliminates any possibility of cross-tenant key leakage or orphan references.

### 4.3 Proposed Physical Table Specifications

#### 1. `stu.admission_intakes`
- `tenant_id` (UUID, PK)
- `id` (UUID, PK, default `gen_random_uuid()`)
- `college_id` (UUID, FK `core.colleges`)
- `program_id` (UUID, FK `acd.programs`)
- `academic_year_id` (UUID, FK `core.academic_years`)
- `intake_name` (VARCHAR(100), e.g. "Fall 2026 Regular Intake")
- `application_window_start` (TIMESTAMPTZ NOT NULL)
- `application_window_end` (TIMESTAMPTZ NOT NULL)
- `capacity` (INTEGER NOT NULL CHECK (capacity > 0))
- `is_active` (BOOLEAN DEFAULT true)
- `fee_required` (BOOLEAN DEFAULT false)
- `fee_amount` (NUMERIC(12,2) DEFAULT 0.00)
- `currency` (VARCHAR(3) DEFAULT 'INR')
- `created_at`, `updated_at`, `created_by`, `updated_by`, `row_version`

#### 2. `stu.applications`
- `tenant_id` (UUID, PK)
- `id` (UUID, PK, default `gen_random_uuid()`)
- `application_number` (VARCHAR(64) NOT NULL UNIQUE within `(tenant_id, intake_id)`)
- `intake_id` (UUID, FK `stu.admission_intakes`)
- `applicant_person_id` (UUID, FK `core.persons`)
- `status` (VARCHAR(32) NOT NULL DEFAULT 'DRAFT')  
  Allowed values: `DRAFT`, `SUBMITTED`, `RETURNED_FOR_INFORMATION`, `ELIGIBLE`, `UNDER_REVIEW`, `OFFERED`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `REJECTED`, `WITHDRAWN`, `HANDOVER_PENDING`, `HANDED_OVER`.
- `document_verification_status` (VARCHAR(32) DEFAULT 'PENDING')  
  Allowed values: `PENDING`, `IN_PROGRESS`, `VERIFIED`, `REJECTED`, `WAIVED`.
- `payment_status` (VARCHAR(32) DEFAULT 'PENDING')  
  Allowed values: `PENDING`, `PAID`, `WAIVED`, `EXEMPT`, `FAILED`.
- `fee_obligation_id` (UUID, nullable, FK `fin.fee_obligations`)
- `submission_timestamp` (TIMESTAMPTZ, nullable)
- `eligibility_rule_version` (VARCHAR(32), nullable)
- `eligibility_result` (VARCHAR(32), nullable)
- `assigned_department_id` (UUID, FK `core.departments`)
- `created_at`, `updated_at`, `created_by`, `updated_by`, `row_version`

#### 3. `stu.application_status_history`
- `tenant_id` (UUID, PK)
- `id` (UUID, PK, default `gen_random_uuid()`)
- `application_id` (UUID, FK `stu.applications`)
- `from_status` (VARCHAR(32) NOT NULL)
- `to_status` (VARCHAR(32) NOT NULL)
- `transition_reason` (TEXT)
- `actor_id` (UUID NOT NULL)
- `actor_role` (VARCHAR(64) NOT NULL)
- `correlation_id` (VARCHAR(64) NOT NULL)
- `created_at` (TIMESTAMPTZ DEFAULT clock_timestamp())

#### 4. `stu.application_documents`
- `tenant_id` (UUID, PK)
- `id` (UUID, PK, default `gen_random_uuid()`)
- `application_id` (UUID, FK `stu.applications`)
- `document_id` (UUID NOT NULL, FK `doc.documents`)
- `document_type` (VARCHAR(64) NOT NULL)
- `verification_status` (VARCHAR(32) DEFAULT 'PENDING')
- `verified_by` (UUID, nullable)
- `verified_at` (TIMESTAMPTZ, nullable)
- `rejection_reason` (TEXT, nullable)
- `created_at`, `updated_at`, `row_version`

#### 5. `stu.application_reviews`
- `tenant_id` (UUID, PK)
- `id` (UUID, PK, default `gen_random_uuid()`)
- `application_id` (UUID, FK `stu.applications`)
- `reviewer_id` (UUID NOT NULL)
- `rubric_version` (VARCHAR(32) NOT NULL)
- `rubric_scores` (JSONB NOT NULL DEFAULT '{}'::jsonb)
- `total_score` (NUMERIC(5,2))
- `recommendation` (VARCHAR(32) NOT NULL)
- `confidential_comments` (TEXT)
- `applicant_facing_remarks` (TEXT)
- `submitted_at` (TIMESTAMPTZ DEFAULT clock_timestamp())
- `created_at`, `updated_at`, `row_version`

#### 6. `stu.admission_offers`
- `tenant_id` (UUID, PK)
- `id` (UUID, PK, default `gen_random_uuid()`)
- `application_id` (UUID NOT NULL, FK `stu.applications`)
- `offer_code` (VARCHAR(64) NOT NULL UNIQUE within tenant)
- `offer_terms_version` (VARCHAR(32) NOT NULL)
- `valid_from` (TIMESTAMPTZ NOT NULL)
- `valid_to` (TIMESTAMPTZ NOT NULL)
- `status` (VARCHAR(32) DEFAULT 'ISSUED')  
  Allowed values: `ISSUED`, `ACCEPTED`, `DECLINED`, `EXPIRED`, `REVOKED`.
- `accepted_at` (TIMESTAMPTZ, nullable)
- `declined_at` (TIMESTAMPTZ, nullable)
- `decline_reason` (TEXT, nullable)
- `created_at`, `updated_at`, `created_by`, `row_version`

#### 7. `stu.admission_handovers`
- `tenant_id` (UUID, PK)
- `id` (UUID, PK, default `gen_random_uuid()`)
- `application_id` (UUID NOT NULL, FK `stu.applications`)
- `offer_id` (UUID NOT NULL, FK `stu.admission_offers`)
- `idempotency_key` (VARCHAR(128) NOT NULL UNIQUE within tenant)
- `created_student_id` (UUID, nullable, FK `stu.students`)
- `enrolment_status` (VARCHAR(32) DEFAULT 'PENDING')
- `handover_completed_at` (TIMESTAMPTZ, nullable)
- `retry_count` (INTEGER DEFAULT 0)
- `last_error` (TEXT, nullable)
- `created_at`, `updated_at`, `row_version`

---

## 5. Row-Level Security (RLS) & Grant Policies

### 5.1 Fail-Closed Multi-Tenant RLS Pattern
Every new table in `stu` must have RLS enabled and forced:
```sql
ALTER TABLE stu.applications ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.applications FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_applications ON stu.applications
    FOR ALL
    TO authenticated
    USING (
        tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid
    )
    WITH CHECK (
        tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid
    );
```

### 5.2 Actor-Level Security via Application Service
In CampXSync, the database RLS layer strictly enforces **Tenant Boundaries**, while the Java microservice layer enforces **Role and Attribute-Based Access Control (RBAC/ABAC)**. This prevents SQL RLS policies from becoming unmaintainable procedural bottlenecks while maintaining defense-in-depth:
- Applicants can only query `stu.applications` where `applicant_person_id` matches their authenticated `sub` claim.
- Reviewers can only query `stu.applications` assigned to their department scope.
- Confidential reviewer notes and rubric scores in `stu.application_reviews` are never projected to applicant callers.

---

## 6. Migration Sequence & Review Gate

When DDL implementation is formally authorized by the user, the migrations must follow this exact sequence:
1. `V20261101_01__create_admission_intakes_and_eligibility.sql`
2. `V20261101_02__create_applications_and_status_history.sql`
3. `V20261101_03__create_application_documents_and_reviews.sql`
4. `V20261101_04__create_admission_offers_and_handovers.sql`
5. `V20261101_05__enable_rls_and_grants_for_admissions.sql`

Each migration will have a corresponding `_rollback.sql` script verified in staging before production release.

**Current Status:** All DDL scripts remain unexecuted. Database write gate remains LOCKED.

