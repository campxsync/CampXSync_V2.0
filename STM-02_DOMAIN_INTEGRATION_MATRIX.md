# STM-02 — Admissions & Application Management Service
## Domain Interaction & Integration Matrix

**Document ID:** STM02-INT-001  
**Execution Context:** CampXSync Microservice Architecture & Event-Driven Backbone  
**Audit Date:** October 30, 2026  
**Status:** ARCHITECTURE AUDIT COMPLETE — INTEGRATION CONTRACT MATRIX  

---

## 1. Domain Ownership & Bounded Context Map

To preserve data integrity, prevent duplicate identity models, and eliminate conflicting transactional boundaries, domain ownership is strictly locked as follows:

| Domain | Authoritative Domain Responsibilities | Prohibited in STM-02 (Anti-Patterns) |
|---|---|---|
| **IAM** | Authentication, token issuance, identity principals, password policies, custom JWT claims hook. | STM-02 must NOT store user passwords, create parallel auth tokens, or manage login credentials. |
| **Core** | Tenancy, college/campus structures, human bio-demographic registry (`core.persons`), number sequences. | STM-02 must NOT create duplicate person profiles or maintain custom unconstrained tenant tables. |
| **ACD** | Academic structure, degree programs, curricula, subjects, academic years, batches, timetables. | STM-02 must NOT maintain a parallel program or academic year catalogue. |
| **Finance** | Fee structures, payment gateways, receipts, financial ledger, refund policies. | STM-02 must NOT create a parallel financial ledger or store credit card/bank credentials. |
| **STM-01** | Student Identity & Profile Master, enrollment numbers, student addresses, verified relationships. | STM-02 must NOT create `stu.students` records during application drafting or submission. |
| **STM-02** | Admissions application lifecycle, intake windows, eligibility screening, reviews, offers. | STM-02 is the SOLE owner of intake applications, evaluations, and offers. |
| **STM-03** | Course registration, cohort roster assignment, academic term enrollment. | STM-02 must NOT directly assign students to course sections or term enrollments. |
| **Doc** | Document metadata, storage object mapping, verification lifecycle, template rendering. | STM-02 must NOT store raw binary files in PostgreSQL or bypass Supabase Storage. |
| **Sys / Audit** | Transactional outbox (`sys.outbox_events`), idempotency keys, compliance audit trails. | STM-02 must NOT deploy custom Kafka or separate audit databases; reuse platform outbox and audit tables. |

---

## 2. End-to-End Cross-Domain Integration Matrix

The following matrix documents every interaction between STM-02 and surrounding CampXSync domains:

| # | Source Domain | Target Domain | Integration Purpose | Pattern & Transport | Payload Contract & Keys | Authorization & Trust Boundary | Idempotency & Concurrency | Failure & Retry Behavior |
|---|---|---|---|---|---|---|---|---|
| **1** | STM-02 | **ACD** | Validate programme existence, status, and academic year during intake configuration and application submission. | Synchronous REST / Internal HTTP client | `GET /api/v1/academics/programs/{id}`, `GET /api/v1/academics/calendars/years/{id}` | Internal Gateway HMAC, Tenant Context header. | Read-only. Safe to retry. Cacheable with 5-minute TTL. | Fail-closed: If ACD is unreachable, reject application creation with 503 Service Unavailable. |
| **2** | **IAM** / Gateway | STM-02 | Authenticate caller, validate tenant claims, enforce route permission, route request. | Reverse Proxy HTTP | Headers: `Authorization: Bearer <jwt>`, `X-Gateway-Timestamp`, `X-Gateway-Signature`, `X-Tenant-Id`, `X-User-Id`. | HMAC-SHA256 signature verification over request body and path. 300s replay window. | N/A (Ingress) | Unsigned or tampered requests rejected immediately with 401 Unauthorized / 403 Forbidden. |
| **3** | STM-02 | **DOC** | Register uploaded document metadata (transcripts, identity proofs) stored in Supabase Storage. | Synchronous REST + Object Ref | `POST /api/v1/college-admin/documents` -> returns `document_id`. Body: `{storageBucket, storagePath, mimeType, sizeBytes}`. | Bearer Token + Tenant Context. Restricted to allowed MIME types (PDF, JPEG, PNG, WEBP) <= 10MB. | Idempotent via storage path hash. | If Doc service fails, document link rolls back atomically in admissions transaction. |
| **4** | STM-02 | **FIN** | Initiate application fee collection intent; receive confirmed payment status. | Asynchronous Event + Webhook Callback | `admissions.payment.intent_created.v1` -> FIN generates gateway transaction. FIN emits `finance.payment.completed.v1`. | Webhook payload validated with HMAC signature from Finance/Payment Gateway. Never trust client `paid=true`. | Idempotent via `payment_intent_id`. Duplicate callbacks ignored safely. | If payment fails or times out, application remains in `PAYMENT_PENDING` until deadline. |
| **5** | STM-02 | **CAM** | Send email, SMS, and push notifications to applicants regarding submission, interview, decision, and offer. | Asynchronous Transactional Outbox | Outbox event: `admissions.application.submitted.v1`, `admissions.offer.issued.v1`. Consumed by CAM notification worker. | Internal service bus. Payload minimizes PII (applicant ID, first name, application number, safe status URL). | Idempotent event handling via `event_id` in CAM. | Outbox worker retries with exponential backoff; dead letters moved to `sys.dead_letter_events`. |
| **6** | STM-02 | **AUDIT** | Record privileged decisions, reviewer score changes, and late submission exceptions. | Synchronous Direct JDBC in mutation TX | `PostgresAuditRepository.recordEvent(...)` writing to `audit.events` and `audit.change_log`. | Runs within database transaction under `authenticated` role with tenant claims bound. | Atomic with business data mutation. | If audit write fails, entire business transaction rolls back (fail-closed integrity). |
| **7** | STM-02 | **SYS** | Publish domain events reliably to downstream consumers. | Synchronous Direct JDBC in mutation TX | `TransactionalOutboxWriter.write(...)` inserting into `sys.outbox_events`. | Database transaction boundary. | Guaranteed at-least-once delivery; consumer deduplication via `event_id`. | Failed dispatch retried by background polling job up to 5 times before dead-lettering. |
| **8** | STM-02 | **STM-01** | Create or link canonical student identity upon admission offer acceptance. | Synchronous REST / Internal Client with Idempotency Key | `POST /api/v1/students/enroll` with `EnrollStudentRequest` payload. | Mutual Gateway HMAC verification. Initiated strictly by STM-02 upon offer acceptance. | Mandatory `Idempotency-Key: stm02-handover-{offerId}`. Replays return existing profile. | Network failure triggers exponential retry; reconciliation job resolves pending handovers. |
| **9** | STM-02 | **STM-03** | Hand over accepted applicant for cohort assignment and academic course registration. | Asynchronous Outbox Event | `admissions.enrolment.handover_requested.v1` consumed by STM-03. Body: `{applicationId, studentId, programId, intakeId}`. | Internal outbox queue. Tenant and correlation context propagated in event headers. | Idempotent via `application_id`. STM-03 verifies student not already enrolled in cohort. | Reconciled via `stu.admission_handovers` table; unconfirmed handovers flagged for administrative review. |
| **10** | STM-02 | **AI / Intel** | Provide admissions funnel velocity, conversion rates, and intake capacity projections. | Asynchronous Context Event | `ControlledIntelligenceAdapter.emitEvent(...)`. Payload: `{tenantId, intakeId, totalApplications, offeredCount, acceptedCount}`. | Zero PII payload. Aggregated statistical data only. | Informational event stream. | Failed delivery logged; live delivery marked pending if endpoint unavailable. |

---

## 3. Detailed STM-02 to STM-01 Handover Contract

The transition from an **Accepted Applicant** to an **Official Student** is the critical boundary between STM-02 and STM-01.

### 3.1 Trigger Milestone
The handover is triggered strictly when an applicant executes:
`POST /api/v1/admissions/applications/{id}/offers/accept`
- Application status transitions from `OFFERED` to `ACCEPTED`.
- A transaction in STM-02 records the acceptance timestamp and creates a pending record in `stu.admission_handovers`.

### 3.2 Handover Ingress Payload to STM-01 (`EnrollStudentRequest`)
STM-02 maps the accepted application data to STM-01's verified contract:

```json
{
  "firstName": "Arun",
  "middleName": "Kumar",
  "lastName": "Sharma",
  "displayName": "Arun Sharma",
  "dateOfBirth": "2006-05-15",
  "gender": "M",
  "primaryEmail": "arun.sharma@example.com",
  "primaryPhone": "+919876543210",
  "enrollmentNo": "ENR-2026-08942",
  "admissionNo": "ADM-2026-00412",
  "admissionDate": "2026-08-01",
  "collegeId": "c01a8f92-5d41-4c91-9e28-1123456789ab",
  "programId": "b02d7e11-4a32-4b82-8d19-2234567890bc",
  "admissionAcademicYearId": "a03e6f22-3b21-4c73-7c18-3345678901cd",
  "guardian": {
    "name": "Rajesh Sharma",
    "relationship": "FATHER",
    "email": "rajesh.sharma@example.com",
    "phone": "+919876543211",
    "canViewAcademics": true,
    "canViewFees": true,
    "canPayFees": true
  }
}
```

### 3.3 HTTP Request Headers
```http
POST /api/v1/students/enroll HTTP/1.1
Host: localhost:8093
Authorization: Bearer <service-jwt>
X-Gateway-Timestamp: 1793345000
X-Gateway-Signature: 8f4a7c2e...
X-Tenant-Id: t01a1111-2222-3333-4444-555566667777
X-User-Id: u01b8888-9999-aaaa-bbbb-ccccddddeeee
X-User-Role: authenticated
Idempotency-Key: stm02-handover-offer-7a9b1c2d-3e4f
Content-Type: application/json
```

### 3.4 Handover Guarantee & Reconciliation
1. **Synchronous Attempt:** STM-02 calls STM-01 directly. If STM-01 returns `201 Created` or `200 OK` (with `StudentProfileResponse`), STM-02 updates `stu.admission_handovers.created_student_id` and marks `enrolment_status = 'STUDENT_CREATED'`.
2. **Asynchronous Fallback & Reconciliation:** If STM-01 is temporarily unavailable (502/503/timeout), the handover state remains `PENDING` with `retry_count` incremented.
3. A scheduled reconciliation worker (`STM02-EN-011`) queries unconfirmed handovers and retries using the exact same `Idempotency-Key`. Because STM-01 natively enforces idempotency on `enrollStudent`, retries will never create duplicate student profiles.

---

## 4. Event Schema Definitions (Transactional Outbox)

All events emitted by STM-02 follow the platform event envelope:

```json
{
  "eventId": "e01f9999-aaaa-bbbb-cccc-ddddffff0000",
  "tenantId": "t01a1111-2222-3333-4444-555566667777",
  "service": "admissions-service",
  "aggregateType": "ADMISSION_APPLICATION",
  "aggregateId": "app-8c7b6a5d-4e3f-2a1b-0c9d-8e7f6a5b4c3d",
  "eventType": "admissions.application.submitted.v1",
  "schemaVersion": "1.0.0",
  "occurredAt": "2026-10-30T14:30:00Z",
  "correlationId": "corr-11223344-5566-7788-99aa-bbccddeeff00",
  "headers": {
    "actorId": "u01a4444-5555-6666-7777-888899990000",
    "actorRole": "Applicant"
  },
  "payload": {
    "applicationId": "app-8c7b6a5d-4e3f-2a1b-0c9d-8e7f6a5b4c3d",
    "applicationNumber": "APP-2026-00124",
    "intakeId": "intake-9a8b7c6d-5e4f-3a2b-1c0d-9e8f7a6b5c4d",
    "programId": "b02d7e11-4a32-4b82-8d19-2234567890bc",
    "status": "SUBMITTED",
    "submittedAt": "2026-10-30T14:30:00Z"
  }
}
```
*(Sensitive fields such as full identity documents, reviewer confidential comments, and payment credentials are strictly excluded from event payloads).*

