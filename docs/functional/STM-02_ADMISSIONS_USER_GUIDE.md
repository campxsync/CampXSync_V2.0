# CampXSync — STM-02 Admissions & Application Management Functional & User Guide

**Module:** STM-02 Admissions & Application Management  
**Release:** V2.0 / V3.0  
**Status:** Implemented & Verified  
**Governing ADR:** `STM-02_ARCHITECTURAL_DECISIONS.md`  

---

## 1. Executive Overview

The **STM-02 Admissions & Application Management** module coordinates the end-to-end prospective student journey from intake configuration and multi-step application submission to document verification, faculty rubric scoring, executive decision approvals, formal offer management, and idempotent student matriculation into STM-01.

All workflows enforce:
- **Tenant Context Isolation:** Composite primary keys `(tenant_id, id)` and fail-closed RLS policies across all 9 admissions tables.
- **Segregation of Duties (SoD):** A faculty reviewer who scores an application is cryptographically and procedurally prevented from approving the binding admission decision.
- **Zero-Trust Identity Boundary:** Prospective applicants interact using lightweight pre-matriculation applicant identities; canonical student profiles are created in `stu.students` exclusively upon verified offer acceptance.
- **Intelligence Layer Integration:** Real-time admissions event telemetry flows into the CampXSync Intelligence Layer via governed schemas (`AdmissionsFunnelCalculated.v1`, `ApplicationDemandAnalyzed.v1`, `AdmissionsOperationsAnalyzed.v1`, `AdmissionsDecisionAnalyzed.v1`, `EnrolmentConversionCalculated.v1`).

---

## 2. Role-Based Workspaces & Navigation

| Persona | Primary Workspace Route | Authorized Responsibilities |
| :--- | :--- | :--- |
| **Applicant** | `/student/admissions` | Discover active intakes, create application drafts, submit declarations & documents, pay application fees, review offer letters, accept or decline admission. |
| **Admissions Officer** | `/college/admissions` | Monitor operational funnel KPIs, manage candidate application queue, verify submitted qualifications & credentials, assign reviewers, return applications for information. |
| **Faculty Reviewer** | `/college/admissions` | Evaluate candidate academic portfolios, assign rubric scores across criteria, record confidential remarks, and submit offer recommendations. |
| **Admissions Approver** | `/college/admissions/approvals` | Review faculty scoring summaries, verify decision authority and SoD boundaries, record binding decisions (`OFFER_APPROVED`, `REJECTED`, `WAITLISTED`), and authorize offer issuance. |
| **Admissions Admin** | `/college/admissions/intakes` | Configure intake cycles, seat capacities, application windows, fee timing policies, and automated eligibility rule cutoffs. |
| **Enrolment Officer** | `/college/admissions/offers` | Monitor accepted offers, trigger idempotent student matriculation into STM-01, monitor failed handover queues, and execute safe retries. |

---

## 3. Detailed Screen Workflows

### 3.1 Applicant Portal (`/student/admissions`)
1. **Intake Discovery:** Browse active intakes filtered by academic department, application window dates, and capacity.
2. **Draft Application Creation:** Initialize draft with personal, contact, and academic details.
3. **Document Attachments:** Upload required verification documents (PDF, JPEG, PNG, WEBP <= 10MB) with SHA-256 integrity fingerprinting.
4. **Fee Payment & Declaration:** Accept legal declarations, privacy consent, and complete fee payment if required by intake policy.
5. **Submission & Tracking:** Submit application; track real-time progression through `SUBMITTED`, `UNDER_REVIEW`, `OFFERED`, `ACCEPTED`, and `HANDED_OVER`.
6. **Offer Decision:** View formal offer letter, terms & conditions, and validity window. One-click **Accept Offer** or **Decline Offer**.

### 3.2 Admissions Officer Workspace (`/college/admissions`)
1. **Operational Dashboard:** Real-time visibility into Total Applications, Pending Verification, In Review, Offers Issued, Offers Accepted, and Enrolment Conversion Rate.
2. **Queue Filtering:** Instant search by application number or applicant ID; tab-filtered by lifecycle status (`SUBMITTED`, `UNDER_REVIEW`, `OFFERED`, `ACCEPTED`).
3. **Application Review (`/college/admissions/applications/[id]`):**
   - Inspect submitted profile details and automated eligibility check results.
   - Review uploaded documents; mark as `VERIFIED` or `REJECTED` with clear rejection reasons.
   - Assign application to departmental faculty reviewer.

### 3.3 Admissions Approver Workspace (`/college/admissions/approvals`)
1. **Pending Approvals Queue:** Filtered list of applications in `UNDER_REVIEW` state with completed reviewer rubric scores.
2. **Separation-of-Duties Check:** Validates that the logged-in approver is not the reviewer who scored the application.
3. **Decision Recording:** Record decision type (`OFFER_APPROVED`, `REJECTED`, `WAITLISTED`) with justification notes.
4. **Offer Issuance:** Authorize formal offer with validity window (e.g. 14 or 21 days) and reserved capacity seat.

### 3.4 Offers & Handover Workspace (`/college/admissions/offers`)
1. **Offer Tracking:** Monitor active, accepted, and expired offers.
2. **Idempotent STM-01 Handover:** Once an offer is accepted, trigger student identity creation in STM-01.
3. **Handover Status & Verification:** View created `student_id` in `stu.students` and cross-reference with `/college/students` roster.
4. **Failure Recovery:** Reconcile pending or delayed handovers with automated retry tracking and dead-letter visibility.

---

## 4. Error Handling & Recovery Runbook

| Error Code | HTTP Status | Scenario | Recommended Recovery Action |
| :--- | :--- | :--- | :--- |
| `VALIDATION_FAILED` | 400 | Missing mandatory applicant fields or unaccepted declarations | Complete all mandatory form fields and check privacy consent. |
| `SECURITY_VIOLATION` | 403 | Missing tenant header or Segregation-of-Duties violation | Re-authenticate; ensure reviewer does not approve own evaluation. |
| `NOT_FOUND` | 404 | Application or intake ID does not exist | Verify URI resource identifier or refresh queue. |
| `CONFLICT` | 409 | Duplicate application in intake or optimistic concurrency clash | Reload current record version before submitting modifications. |
| `LIFECYCLE_VIOLATION` | 422 | Attempting invalid transition (e.g. Draft -> Handed Over) | Follow linear state machine: Draft -> Submitted -> Review -> Offer -> Accept -> Handover. |
| `RATE_LIMIT_EXCEEDED` | 429 | Burst submissions exceeding token bucket quota | Wait for `Retry-After` seconds and resubmit. |
| `GATEWAY_DOWNSTREAM_UNAVAILABLE` | 503 | Admissions service offline or restarting | Verify port 8094 process health via `/actuator/health`. |

