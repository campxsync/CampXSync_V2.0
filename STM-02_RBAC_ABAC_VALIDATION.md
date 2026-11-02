# STM-02 — Admissions & Application Management Service
## RBAC & ABAC Security Validation

**Document ID:** STM02-SEC-001  
**Execution Context:** CampXSync Hybrid RBAC + ABAC Authorization Architecture  
**Audit Date:** October 30, 2026  
**Status:** SECURITY AUDIT COMPLETE — ROLE GOVERNANCE PROPOSAL  

---

## 1. Enterprise Role Catalogue Baseline (R01–R30)

CampXSync governs all platform operations through the canonical enterprise role catalogue documented in `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx`:

| Code | Role Name | Canonical Scope & Description |
|---|---|---|
| **R01** | Super Admin | Platform-wide SaaS operations |
| **R02** | System Admin | Technical platform configuration |
| **R03** | Institution Admin | Multi-college university / institution tier |
| **R04** | College Admin | College campus administration tier |
| **R05** | Principal | Head of College / Executive approval |
| **R06** | Vice Principal | Deputy Executive |
| **R07** | Dean | Faculty / Academic division head |
| **R08** | HOD | Academic department head |
| **R09** | Program Coordinator | Degree program coordinator |
| **R10** | Faculty | Teaching faculty (assigned subjects/courses) |
| **R11** | Class/Section Coordinator | Section faculty coordinator |
| **R12** | Controller of Examinations | Autonomous examination head |
| **R13** | Exam Cell Officer | Examination administration |
| **R14** | Valuator / Examiner | Assigned script evaluator |
| **R15** | Finance Manager | Finance ledger & policy head |
| **R16** | Finance Officer / Accountant | Operational fee collection & receipts |
| **R17** | HR Manager | Human resources leadership |
| **R18** | HR Officer | HR operations |
| **R19** | Communication Manager | Broadcast & campaign announcements |
| **R20** | Placement Officer | Campus recruitment |
| **R21** | Librarian | Library resources |
| **R22** | Hostel Warden | Student housing |
| **R23** | Transport Manager | Fleet & transit |
| **R24** | Facility Manager | Campus estate |
| **R25** | Student | Enrolled student self-service (own academic/fee data) |
| **R26** | Parent / Guardian | Linked student views (consent-governed) |
| **R27** | API Client | Machine-to-machine scoped integrations |
| **R28** | Auditor | Read-only compliance & audit log inspection |
| **R29** | Reporting / Analytics User | Aggregated KPI & analytical exports |
| **R30** | Guest | Public / unauthenticated restricted read |

---

## 2. Audit of STM-02 Backlog Actors vs R01–R30

An audit of the 55 backlog items in `UserStories/STM-02_Updated_Architect_User_Stories.csv` revealed that several key admissions actors do **NOT** map cleanly to R01–R30:

### 2.1 The "Applicant" Problem (Role Mismatch)
- **Observed Backlog Mapping:** `Applicant/Student (R25 - no Applicant role in catalogue)` or `Guest (R30) / Applicant`.
- **Architectural Reality:** An applicant is NOT an enrolled student. Granting an applicant role **R25** violates domain boundaries:
  - R25 grants access to enrolled campus services (course registrations, internal attendance, mark cards, hostel, LMS).
  - An applicant has no student enrollment number, no campus ID, and no enrolled tuition ledger.
  - Applicants must be restricted strictly to their own draft/submitted admissions application, document uploads, and offer acceptance.

### 2.2 The "Admissions Staff" & "Admission Approver" Problem
- **Observed Backlog Mapping:** `Registrar / Admissions Staff (not in R01-R30)` and `Authorized Admission Approver (delegated; not in R01-R30)`.
- **Architectural Reality:** Admissions operations (intake configuration, document verification, application screening, reviewer assignment, offer issuance) require dedicated operational and administrative roles distinct from general College Admin (R04) or Faculty (R10).

---

## 3. Decision D-13: Canonical Role Mapping Resolution

To eliminate ambiguous role mappings and enforce least-privilege security, Decision D-13 presents two viable alternatives:

### Alternative A: Extend Enterprise Catalogue with Formal Canonical Roles [RECOMMENDED]
Introduce three new canonical roles into the CampXSync enterprise catalogue:
1. **R31 — Applicant (Ingress Persona):** External prospective student authenticated via IAM. Can draft, update, submit, view status, upload documents, and accept/decline offers for their own applications only. Zero access to campus student portals.
2. **R32 — Admissions Officer / Staff:** Administrative role assigned to college admissions office. Can verify documents, screen applications, assign reviewers, configure intakes, and coordinate candidate communications.
3. **R33 — Admissions Approver:** Executive decision authority (e.g. Dean of Admissions, Principal delegate). Holds exclusive privilege to approve formal admission decisions and authorize offer issuance.

*Advantages:* Explicit, audit-compliant, eliminates role confusion, maps directly to UI portal navigation and API Gateway routing filters.

### Alternative B: Subsume via Delegated Scope Attributes on Existing Roles
- Treat Applicant as **R30 (Guest)** with an `applicant` persona claim in the JWT.
- Treat Admissions Staff as **R04 (College Admin)** with an `admissions:officer` functional delegation.
- Treat Admissions Approver as **R05 (Principal)** or **R07 (Dean)** with `admissions:approver` delegation.

*Disadvantages:* Conflates guest visitors with authenticated applicants; grants College Admins unnecessary admissions privileges or requires complex custom attribute parsing in every API endpoint.

**Architectural Recommendation:** Formally approve **Alternative A** and register R31, R32, R33 in `CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx`.

---

## 4. Four Levels of Authorization for STM-02

In accordance with Section 18 of the Enterprise RBAC Matrix, STM-02 enforces four distinct layers of authorization:

```
[Level 1: Service Ingress]  --> Can caller access STM-02 (/api/v1/admissions/*)?
         ↓
[Level 2: Operation]        --> Can role execute CREATE_DRAFT, VERIFY_DOC, APPROVE_DECISION?
         ↓
[Level 3: ABAC Data Scope]  --> Is application within caller's Tenant, College, Department, or Ownership?
         ↓
[Level 4: Field-Level Mask] --> Should caller see confidential reviewer notes, PII, or internal rubrics?
```

### 4.1 Level 1: Service Ingress Authorization
Enforced at the API Gateway via Bearer JWT and HMAC signature. Unauthenticated requests are rejected (401), except for public intake information endpoints (`GET /api/v1/admissions/intakes/public`).

### 4.2 Level 2: Operational Action Permissions
Mapped against standard CampXSync vocabulary:

| Permission Code | Meaning | STM-02 Specific Operations |
|---|---|---|
| **C** | Create | Create draft application, register document reference, draft intake window. |
| **U** | Update | Update draft fields, replace unverified documents, modify intake capacity. |
| **V** | View | View application projection, intake details, document verification status. |
| **RV** | Review / Verify | Record document verification result, evaluate rubric criteria, submit review score. |
| **A** | Approve | Formally approve admission decision, authorize offer issuance, approve late submission exception. |
| **R** | Reject | Formally reject application, return application for information, reject invalid document. |
| **X** | Execute | Submit application, accept admission offer, decline offer, trigger enrolment handover. |
| **AU** | Audit | Query immutable application change log and timeline history. |
| **CFG / ADM** | Configure / Admin | Configure intake quotas, versioned eligibility criteria, scoring rubrics. |

### 4.3 Level 3: ABAC Data Scoping
Operational permissions are bounded by attribute and context filters:
- **Tenant Scope:** Mandatory for all operations. `tenant_id` resolved server-side; cross-tenant requests return 404/403.
- **Resource Ownership Scope:** Applicants (R31) can only view/mutate applications where `applicant_person_id == context.userId`.
- **Department Scope:** Department Reviewers (R10 Faculty assigned as reviewers) can only access applications where `assigned_department_id` matches their departmental assignment.
- **Intake / Academic Year Scope:** Admissions Officers (R32) are constrained to intakes within their authorized college/campus.

### 4.4 Level 4: Field-Level Authorization & Masking
To prevent data leaks and maintain evaluation integrity:
- **Confidential Evaluator Notes:** Field `confidential_comments` in `stu.application_reviews` is visible ONLY to Admissions Approvers (R33) and College Admins (R04). Never projected to Applicant (R31).
- **Fraud & Verification Flags:** Internal verification risk flags are visible to Admissions Staff (R32) only.
- **PII Redaction in Events:** Transactional outbox events exclude national identification numbers, passwords, and raw document binaries.

---

## 5. Separation of Duties (SoD) & Conflict of Interest Rules

High-risk admissions operations enforce strict Segregation of Duties:

1. **Self-Approval Prohibition:**
   An applicant (R31) can NEVER approve their own application, verify their own documents, or modify their own eligibility evaluation score.
2. **Evaluator vs Decision-Maker Separation:**
   A Department Reviewer (R10) who submits a review score and recommendation cannot be the sole approver of the final admission decision (R33). Final admission approval requires independent authorization.
3. **Offer Generation Safeguard:**
   An admission offer cannot be generated unless:
   - An authorized decision (`OFFER_APPROVED`) is recorded by an Admissions Approver.
   - The intake capacity (`capacity - admitted_count > 0`) is validated.
   - Document verification status is `VERIFIED` or explicitly `WAIVED`.
4. **Financial Separation:**
   Admissions Staff (R32) cannot directly mutate payment records in `fin`. Application fee confirmation requires cryptographically verified callbacks from the payment gateway or direct reconciliation from Finance (R15/R16).

---

## 6. STM-02 Unified Authorization Matrix

| Backlog Operation | Actor | Approved Role | Permission | ABAC Scope Boundary | SoD / Audit Requirement |
|---|---|---|---|---|---|
| View Open Intakes | Public / Applicant | R30 Guest / R31 Applicant | `V` | Public active intakes only | None |
| Configure Intake Window | Admissions Administrator | R32 Staff / R04 College Admin | `CFG`, `C`, `U` | College / Tenant | Audited in `audit.events` |
| Create Draft Application | Applicant | R31 Applicant | `C` | Own application only | Rate limited |
| Update Draft Application | Applicant | R31 Applicant | `U` | Own draft; `status == 'DRAFT'` | Optimistic locking (`row_version`) |
| Submit Application | Applicant | R31 Applicant | `X` | Own draft; intake window open | Validates completeness & declarations |
| View Own Application Status | Applicant | R31 Applicant | `V` | Own application only | Sanitized timeline projection |
| Upload Document Reference | Applicant | R31 Applicant | `C` | Own application; allowed MIME | Validates 10MB limit; object ref |
| Verify Document | Admissions Verifier | R32 Staff | `RV`, `A`, `R` | Assigned intake / college | Verifier ID & remarks recorded |
| Evaluate Eligibility | System / Staff | R32 Staff / System | `X` | Assigned application | Versioned rule evaluation |
| Assign Reviewer | Admissions Manager | R32 Staff | `AS` | Department / Program | Cannot assign self to bypass SoD |
| Submit Review & Score | Department Reviewer | R10 Faculty (Assigned) | `RV`, `C` | Assigned department application | Evaluator score immutable once committed |
| Record Admission Decision | Admissions Approver | R33 Approver / R05 Principal | `A`, `R` | College / Program | Requires completed reviews |
| Issue Admission Offer | Admissions Approver | R33 Approver | `C`, `P` | Approved application; within capacity | Generates unique offer code & expiry |
| Accept Admission Offer | Applicant | R31 Applicant | `X` | Own application; offer valid & unexpired | Idempotent; triggers STM-01 handover |
| Decline Admission Offer | Applicant | R31 Applicant | `X` | Own application; offer valid | Reason code captured |
| Withdraw Application | Applicant | R31 Applicant | `X` | Own application; pre-acceptance | Terminal state transition |
| Grant Late Exception | Principal / Dean | R05 Principal / R07 Dean | `A`, `CFG` | College / Department | Mandatory audit justification |
| View Admissions Funnel | Reporting User | R29 Reporting / R04 Admin | `V`, `E` | Tenant / College | Anonymized aggregated metrics |

