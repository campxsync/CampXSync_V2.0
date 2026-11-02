# STM-02 — Admissions & Application Management Service
## Architectural Decision Register (ADR)

**Document ID:** STM02-ADR-001  
**Execution Context:** CampXSync Enterprise College ERP Architecture  
**Audit Date:** October 30, 2026  
**Status:** FORMAL ARCHITECTURAL RECOMMENDATIONS — AWAITING USER APPROVAL  

---

## 1. Overview of Architectural Decisions

The STM-02 admissions backlog references 15 critical architectural decisions (`D-01` through `D-15`). These decisions resolve domain ownership boundaries, persistence schemas, identity lifecycles, and security models. 

Under CampXSync governance, **none of these decisions are assumed approved**. Each decision is documented below with technical rationale, trade-off analysis, database/security implications, and a recommended resolution awaiting formal user sign-off.

---

## 2. Decision Register: D-01 through D-15

---

### ADR D-01: Pre-Admission Applicant Identity Model
* **The Question:** Who is an applicant before student matriculation, and how is their identity represented across IAM, Core, and Student domains?
* **Why It Matters:** An applicant is not yet an enrolled student. Forcing every draft or unaccepted application to create an official `stu.students` profile pollutes the institutional academic roster with incomplete drafts, abandoned leads, and rejected candidates.
* **Affected Domains / Services:** IAM, Core (`core.persons`), STM-01 (`stu.students`), STM-02.
* **Database Implications:**
  - `stu.students` must NOT be created during application drafting or submission.
  - Applicant bio-demographics are registered in `core.persons` with a status indicating prospective status (`status = 'APPLICANT'`).
* **Security Implications:**
  - Prevents public applicants from acquiring student credentials (R25) or viewing campus LMS/academic structures.
  - Applicant authentication uses existing IAM Bearer tokens bound to the applicant's `sub` claim.
* **Viable Alternatives:**
  - *Option 1 (Anti-Pattern):* Create a `stu.students` profile on draft creation with status `PROSPECTIVE`.  
    *Disadvantage:* Massive database bloat; corrupts active student counts; triggers unwanted campus ERP automation.
  - *Option 2 (Isolation Model - RECOMMENDED):* Register applicant in `core.persons`. Maintain application in `stu.applications` referencing `core.persons(id)`. Link or instantiate `stu.students` strictly upon formal admission offer acceptance (`STM02-EN-009`).
* **Recommended Option:** **Option 2.**
* **Approval Authority:** Chief Architect & Lead Solution Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-02: Canonical Application State Machine & Action Endpoints
* **The Question:** What is the authoritative lifecycle state machine for an admission application, and how are status transitions governed via REST?
* **Why It Matters:** Admissions involves legally binding offers and regulatory compliance. Direct client patching of `status` allows malicious actors to forge approvals. Furthermore, payment status and document verification status are distinct dimensions and must not be conflated with application workflow states.
* **Affected Domains / Services:** STM-02 (`ApplicationDomainService`, `ApplicationController`), API Gateway.
* **Database Implications:**
  - `stu.applications.status` governed by strict check constraint:  
    `CHECK (status IN ('DRAFT', 'SUBMITTED', 'RETURNED_FOR_INFORMATION', 'ELIGIBLE', 'UNDER_REVIEW', 'OFFERED', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'REJECTED', 'WITHDRAWN', 'HANDOVER_PENDING', 'HANDED_OVER'))`.
  - Orthogonal columns: `document_verification_status` and `payment_status`.
  - Every transition writes atomically to `stu.application_status_history`.
* **Security Implications:** Direct `PATCH /applications/{id}` updates allowlisted form fields only; rejected if `status != 'DRAFT'`. Status changes require dedicated action endpoints (`/submit`, `/verify`, `/decision`, `/offers/accept`).
* **Viable Alternatives:**
  - *Option 1:* Generic `PATCH /applications/{id}` accepting `status`.  
    *Disadvantage:* Unsafe, un-auditable, prone to race conditions.
  - *Option 2 (Action-Driven State Machine - RECOMMENDED):* Server-enforced transition graph with explicit action endpoints (`/submit`, `/decision`, `/offers`, `/offers/accept`), optimistic concurrency (`If-Match` / `row_version`), and atomic history logging.
* **Recommended Option:** **Option 2.**
* **Approval Authority:** Lead Solution Architect & Senior Java Backend Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-03: Application Number Generation & Duplicate Detection Key
* **The Question:** What is the canonical format for application numbers, and what composite business key prevents duplicate applications under concurrent requests?
* **Why It Matters:** Race conditions during high-volume intake deadlines can create duplicate applications for the same candidate in the same intake window.
* **Affected Domains / Services:** STM-02, Core (`core.number_sequences`).
* **Database Implications:**
  - Uniqueness constraint: `UNIQUE (tenant_id, intake_id, application_number)`.
  - Business duplicate prevention constraint:  
    `UNIQUE (tenant_id, intake_id, applicant_person_id) WHERE deleted_at IS NULL AND status NOT IN ('WITHDRAWN', 'REJECTED')`.
  - Application number sequence: Generated via `core.number_sequences` with format `APP-{YEAR}-{PROGRAM_CODE}-{SEQUENCE:05d}`.
* **Security Implications:** Prevents concurrent duplicate submissions; rejects duplicate attempts with HTTP 409 Conflict mapped to RFC 7807 problem details.
* **Recommended Option:** Enforce the composite business key `(tenant_id, intake_id, applicant_person_id)` and sequential number generation.
* **Approval Authority:** Database Architect & Lead Solution Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-04: Versioned Eligibility Rule Evaluation Engine
* **The Question:** How should eligibility criteria (minimum aggregate percentage, subject prerequisites, age cutoffs) be evaluated and persisted?
* **Why It Matters:** Institutions change admission criteria between academic years. Prior admissions decisions must remain reproducible and auditable against the exact rule set in effect when evaluated.
* **Affected Domains / Services:** STM-02 (`EligibilityEvaluationEngine`), ACD.
* **Database Implications:**
  - Criteria stored in `stu.admission_eligibility_rules` with versioning (`version`, `effective_from`, `effective_to`).
  - Applications store `eligibility_rule_version` and JSONB `eligibility_evaluation_result` capturing itemized rule passes/fails.
* **Security Implications:** Rule updates do NOT retroactively alter existing application evaluation records. Manual eligibility overrides require role R33 and emit mandatory audit records (`STM02-US-043`).
* **Recommended Option:** Deterministic rule engine evaluating versioned JSON criteria, storing immutable evaluation snapshots.
* **Approval Authority:** Senior Java Backend Architect & Product Owner.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-05: Document Storage & Verification Boundary
* **The Question:** Where are uploaded application documents stored, and how is document verification governed?
* **Why It Matters:** Binary documents (transcripts, birth certificates) must not be stored in PostgreSQL tables. Furthermore, OCR/KYC signals are assistive and must not bypass human verification without explicit institutional policy.
* **Affected Domains / Services:** STM-02, Doc (`doc.documents`), Supabase Storage.
* **Database Implications:**
  - Binaries reside in private Supabase Storage buckets.
  - Document metadata stored in `doc.documents`.
  - STM-02 stores `stu.application_documents` linking `(application_id, document_id, document_type, verification_status)`.
* **Security Implications:** Upload URLs are short-lived pre-signed URLs generated by API Gateway. Verification decisions require Admissions Verifier role (R32) and log `verified_by` and `verified_at`.
* **Recommended Option:** Reuse `doc.documents` and private object storage; enforce two-state verification (Human Verifier required for official compliance).
* **Approval Authority:** Security Architect & Lead Solution Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-06: Application Fee Requirement & Timing
* **The Question:** Is the application fee mandatory upfront, post-eligibility, or optional/waivable?
* **Why It Matters:** Different institutions and programmes operate under varying financial policies (e.g. government quotas are free, private management quotas require fees before review).
* **Affected Domains / Services:** STM-02, Finance (`fin.payments`, `fin.fee_obligations`).
* **Database Implications:**
  - `stu.admission_intakes` includes `fee_required (boolean)`, `fee_amount (numeric)`, `fee_timing (VARCHAR)`.
  - Allowed timings: `AT_SUBMISSION`, `POST_ELIGIBILITY`, `UPON_OFFER_ACCEPTANCE`, `NONE`.
* **Security Implications:** Never trust a client request containing `paid=true`. Fee status updates occur strictly via verified webhook callbacks or internal Finance service events.
* **Recommended Option:** Configurable fee timing per intake in `stu.admission_intakes`. Finance remains authoritative ledger owner.
* **Approval Authority:** Finance Domain Lead & Product Owner.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-07: Admission Decision Authority & Segregation of Duties
* **The Question:** What approval workflow governs admission decisions (Approve, Reject, Waitlist, Defer)?
* **Why It Matters:** Academic evaluation must be protected from unilateral favoritism or tampering.
* **Affected Domains / Services:** STM-02, IAM.
* **Security Implications:**
  - Enforce two-tier Segregation of Duties:
    - Tier 1: Department Reviewer (R10) scores rubrics and recommends (`RECOMMEND_OFFER`, `RECOMMEND_REJECT`).
    - Tier 2: Admissions Approver (R33 / R05 Principal) reviews recommendations and commits the binding decision.
  - An individual cannot approve an application they personally submitted or assisted.
* **Recommended Option:** Mandatory two-tier separation between Evaluator and Approver; multi-reviewer panels supported via configurable threshold.
* **Approval Authority:** Security Architect & Academic Dean.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-08: Admission Offer Lifecycle, Expiry & Reopening Semantics
* **The Question:** How are admission offers issued, validated, expired, and accepted under concurrent access?
* **Why It Matters:** Offer acceptance commits institutional capacity. Race conditions could cause over-enrollment beyond physical batch capacities.
* **Affected Domains / Services:** STM-02 (`ReviewAndDecisionService`), ACD.
* **Database Implications:**
  - `stu.admission_offers` stores `valid_from`, `valid_to`, `status`.
  - Acceptance query uses atomic conditional update:
    ```sql
    UPDATE stu.admission_offers 
    SET status = 'ACCEPTED', accepted_at = now()
    WHERE id = ? AND tenant_id = ? AND status = 'ISSUED' AND valid_to > now();
    ```
  - Expired offers updated automatically via scheduled background worker (`STM02-US-047`).
* **Security Implications:** Reopening an expired offer requires an audited administrative exception (`STM02-US-048`) granted by role R33.
* **Recommended Option:** Atomic conditional acceptance with strict time-window enforcement.
* **Approval Authority:** Lead Solution Architect & Database Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-09: Timing & Contract of STM-01 Student Identity Creation
* **The Question:** At what exact lifecycle milestone is the official student record created in STM-01?
* **Why It Matters:** Creating student profiles prematurely corrupts academic statistics. Creating them too late prevents timely orientation and fee billing.
* **Affected Domains / Services:** STM-02, STM-01 (`services/student-services/student-service`).
* **Database Implications:** Creation occurs strictly upon transition to `ACCEPTED`.
* **Contract Specification:** STM-02 calls STM-01's verified REST endpoint `POST /api/v1/students/enroll` with mandatory `Idempotency-Key: stm02-handover-{offerId}`.
* **Recommended Option:** Handover strictly on offer acceptance (`admissions.offer.accepted.v1`); idempotent retry guarantees zero duplicate profiles.
* **Approval Authority:** Chief Architect & Student Domain Lead.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-10: Platform Transactional Outbox & Reliable Event Publishing
* **The Question:** How are admissions lifecycle events published reliably without dual-write inconsistencies or assuming unverified message brokers?
* **Why It Matters:** Writing to a database and publishing to Kafka in separate uncoordinated actions causes phantom events or silent data loss when crashes occur.
* **Affected Domains / Services:** STM-02, Platform Infrastructure (`sys.outbox_events`).
* **Database & Code Implications:**
  - STM-02 writes business data and outbox events in the SAME JDBC transaction using `com.campx.platform.outbox.TransactionalOutboxWriter`.
  - Target table: `sys.outbox_events`.
* **Recommended Option:** Standardize on `sys.outbox_events` and `TransactionalOutboxWriter`. Kafka is treated as an asynchronous dispatcher decoupled from the transactional boundary.
* **Approval Authority:** Senior Java Backend Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-11: Multi-Tenancy & Two-Tenant RLS Test Merge Gate
* **The Question:** How is strict tenant isolation proven across all admissions queries and joins?
* **Why It Matters:** In a multi-tenant SaaS platform serving 10,000+ institutions, cross-tenant data leakage is a catastrophic security failure.
* **Database & Testing Implications:**
  - Every admissions table enforces `FORCE ROW LEVEL SECURITY`.
  - CI test harness (`STM02-EN-003`) executes automated cross-tenant security suites using two active test tenants and real non-admin roles for every SELECT, INSERT, UPDATE, and JOIN.
* **Recommended Option:** Two-tenant RLS test suite enforced as a mandatory merge gate.
* **Approval Authority:** Security Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-12: Guardian Access & Minor Applicant Consent
* **The Question:** Under what conditions can a parent or guardian view or assist with an admission application?
* **Why It Matters:** Data privacy regulations (DPDP/GDPR) require verified relationships and explicit consent before disclosing candidate data to third parties.
* **Affected Domains / Services:** STM-02, STM-05 (`stu.guardianships`).
* **Security Implications:** Guardian access requires a linked relationship in `stu.guardianships` with active consent flags (`can_view_admissions = true`). For adult applicants (age >= 18), applicant consent is required.
* **Recommended Option:** Consent-governed read-only status access for verified guardians (`STM02-US-038`).
* **Approval Authority:** Security Architect & Legal / Compliance.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-13: Canonical Role Mapping Against Enterprise Catalogue (R01–R30)
* **The Question:** How are admissions-specific actors (`Applicant`, `Admissions Staff`, `Admissions Approver`) mapped against R01–R30?
* **Why It Matters:** The current enterprise catalogue lacks dedicated admissions roles, leading to insecure overloading of R25 (Student) or R30 (Guest).
* **Recommended Option:** Formally introduce three canonical roles into the enterprise catalogue:
  - **R31 — Applicant**
  - **R32 — Admissions Officer / Staff**
  - **R33 — Admissions Approver**
* **Approval Authority:** Enterprise Security Council & Chief Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-14: Reviewer Assignment & Department Evaluation Rubric
* **The Question:** How are faculty reviewers allocated, and how are rubric scores weighted?
* **Why It Matters:** Academic evaluation rubrics vary across disciplines (e.g. Engineering requires technical interviews; Arts requires portfolio assessments).
* **Affected Domains / Services:** STM-02 (`ReviewController`), Faculty (`services/faculty-services/`).
* **Database Implications:** Rubrics versioned per programme in `stu.admission_intakes`; individual scores persisted in `stu.application_reviews.rubric_scores (JSONB)`.
* **Recommended Option:** Configurable JSONB scoring rubrics per intake; assigned reviews restricted by department ABAC scope.
* **Approval Authority:** Academic Dean & Solution Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

---

### ADR D-15: Communications, Privacy Compliance, Appeals & Data Retention
* **The Question:** How are applicant data retention, privacy declarations, and admission appeals governed?
* **Why It Matters:** Statutory compliance requires that unaccepted applicant PII be retained only for a legally mandated period (e.g. 1 year) and then permanently redacted or deleted upon request.
* **Affected Domains / Services:** STM-02, Communication (`cam`), Audit (`audit`).
* **Database Implications:** Applications include `declaration_accepted_at`, `privacy_consent_at`, `retention_expiry_date`. Deletion requests perform audited anonymization rather than destructive SQL deletes that break financial audit trails.
* **Recommended Option:** Anonymized data redaction for expired applications; formal appeal workflow (`STM02-US-031`) with immutable audit trails.
* **Approval Authority:** Compliance Officer & Database Architect.
* **Status:** **RECOMMENDED — AWAITING FORMAL USER APPROVAL**.

