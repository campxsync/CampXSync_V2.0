# STM-02 — Admissions & Application Management Service
## Service Reuse & Gap Analysis

**Document ID:** STM02-SRV-001  
**Execution Context:** CampXSync Enterprise Microservices Architecture  
**Audit Date:** October 30, 2026  
**Status:** ARCHITECTURE AUDIT COMPLETE — SERVICE GAP INVENTORY  

---

## 1. Microservice Boundary & Architectural Positioning

### 1.1 The Service Boundary Decision
A foundational architecture question for STM-02 is:
*Should STM-02 admissions logic be implemented inside the existing `student-service` (STM-01) or as an independent bounded microservice (`services/student-services/admissions-service`)?*

### 1.2 Architectural Evaluation

| Architectural Dimension | Option A: Monolithic Student Service (Combine STM-01 & STM-02) | Option B: Independent Microservice (`admissions-service`, Port 8094) [RECOMMENDED] |
|---|---|---|
| **Security & Attack Surface** | High Risk. Public, unauthenticated, or guest applicants interact directly with the process hosting confidential enrolled student records. | **Superior Isolation.** Admissions ingress is physically isolated from enrolled student records, reducing blast radius. |
| **Traffic Characteristics** | Poor Fit. Admissions traffic is highly spiky (bursts during application deadlines), while student service traffic follows steady daily campus routines. | **Elastic Scalability.** Admissions service can be horizontally scaled or rate-limited during intake windows without impacting campus ERP. |
| **Domain Model Purity** | High Coupling. Conflates prospective applicant bio-demographics with official student academic status. | **Clean Bounded Context.** STM-02 manages the intake lifecycle; STM-01 owns the canonical student master anchor. |
| **Codebase & Module Layout** | Violates Domain Structure. Swells `student-service` beyond single-responsibility principles. | **Aligns with Domain Hierarchy.** Fits cleanly into `services/student-services/admissions-service`, matching other domains. |

**Verdict:** STM-02 will be implemented as an independent bounded microservice:  
`services/student-services/admissions-service`, running on target port **8094**.

---

## 2. Platform Capability Reuse Inventory

Rather than introducing duplicate infrastructure, STM-02 will reuse established, verified platform components:

| Platform Component | Repository Location | Capability Provided | STM-02 Reuse Strategy |
|---|---|---|---|
| **Transactional Outbox Writer** | `logger/src/main/java/com/campx/platform/outbox/TransactionalOutboxWriter.java` | Atomically writes events to `sys.outbox_events` in the same JDBC transaction as business data mutations. | **Direct Reuse.** Every status transition, document verification, offer issuance, and handover will publish via `TransactionalOutboxWriter`. |
| **Postgres Audit Repository** | `logger/src/main/java/com/campx/platform/audit/PostgresAuditRepository.java` | Writes immutable records to partitioned `audit.events` and `audit.change_log`. | **Direct Reuse.** Privileged admissions decisions, eligibility overrides, and reviewer score submissions will be audited using this repository. |
| **Controlled Intelligence Adapter** | `logger/src/main/java/com/campx/platform/intelligence/ControlledIntelligenceAdapter.java` | Emits versioned context events to the CampXSync Intelligence Layer without leaking sensitive PII. | **Direct Reuse.** Emits funnel velocity, intake fill rates, and anonymized conversion statistics. |
| **Gateway HMAC Verifier** | Reusable security pattern from `services/student-services/student-service/src/main/java/com/campx/student/security/GatewayHmacVerifier.java` | Cryptographically validates `X-Gateway-Timestamp` and `X-Gateway-Signature` with HMAC-SHA256 replay protection. | **Pattern Reuse.** Adopt identical HMAC verification to guarantee requests originate from the trusted API Gateway. |
| **User Security Context** | Reusable security pattern from `services/student-services/student-service/src/main/java/com/campx/student/security/UserSecurityContext.java` | Extracts claims, enforces tenant ID, switches JDBC role to `authenticated`, binds `request.jwt.claims`. | **Pattern Reuse.** Adopt identical transaction-scoped context management for safe PostgreSQL connection hygiene. |
| **Token Bucket Rate Limiter** | `services/student-services/student-service/src/main/java/com/campx/student/service/RateLimiter.java` | In-memory token bucket rate limiting per caller, operation, and tenant with HTTP 429 Retry-After. | **Pattern Reuse.** Protect public applicant submission and document upload endpoints from brute-force floods. |
| **Problem Details Sanitizer** | RFC 7807 Error Response pattern across all services | Standard JSON error contract containing `type`, `title`, `status`, `detail`, `instance`, `correlationId`. | **Standard Reuse.** Standardizes error payloads and prevents database stack trace leakage. |

---

## 3. API Gateway Routing & Route Gap Analysis

### 3.1 Gateway Status
A forensic audit of `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java` confirmed:
- Zero admissions routes currently exist in the API Gateway.
- Academic routes `/api/v1/academics/*` map to ports 8083–8092.
- Student routes `/api/v1/students/*` map to port 8093.

### 3.2 Candidate API Endpoints vs Implementation Status

| HTTP Method | Candidate Route | Purpose / Scope | API Gateway Status | Java Backend Status | Gap Severity |
|---|---|---|---|---|---|
| `GET` | `/api/v1/admissions/intakes` | List open intake windows for available programmes | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/intakes` | Configure intake window, dates, capacity, fees (Staff/Admin) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications` | Create a draft application (resumable intake) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `GET` | `/api/v1/admissions/applications` | List/search applications within caller scope | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `GET` | `/api/v1/admissions/applications/{id}` | Retrieve application details (applicant/staff view) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `PATCH` | `/api/v1/admissions/applications/{id}` | Update draft application fields | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/submit` | Validate completeness and formally submit | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `GET` | `/api/v1/admissions/applications/{id}/status` | Safe timeline and status projection for applicant | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/documents` | Attach uploaded document reference (from Storage) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `GET` | `/api/v1/admissions/applications/{id}/documents` | List document metadata and verification state | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/documents/{docId}/verify` | Record document verification decision (Staff) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/evaluate-eligibility` | Trigger automated versioned eligibility evaluation | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/assign` | Assign application to department reviewer (Staff) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/reviews` | Submit reviewer assessment and rubric scores | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `GET` | `/api/v1/admissions/applications/{id}/reviews` | List review assessments (Staff/Reviewers only) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/decision` | Record admission decision (Approve/Reject/Waitlist) | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/offers` | Generate and issue admission offer with validity | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/offers/accept` | Applicant accepts valid offer; triggers handover | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/offers/decline` | Applicant declines offer | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/withdraw` | Applicant withdraws application | **MISSING** | **GAP** (Unimplemented) | **Critical** (R1) |
| `POST` | `/api/v1/admissions/applications/{id}/payment-intent` | Request application fee payment intent from Finance | **MISSING** | **GAP** (Unimplemented) | **Major** (R2) |
| `GET` | `/api/v1/admissions/applications/{id}/payment-status` | Read confirmed payment status projection | **MISSING** | **GAP** (Unimplemented) | **Major** (R2) |
| `POST` | `/api/v1/admissions/applications/{id}/handover` | Retry or reconcile STM-01/STM-03 handover | **MISSING** | **GAP** (Unimplemented) | **Major** (R2) |
| `GET` | `/api/v1/admissions/reports/funnel` | Aggregated application intake funnel metrics | **MISSING** | **GAP** (Unimplemented) | **Moderate** (R3) |

---

## 4. Required Service Component Architecture

When implementation is authorized, `services/student-services/admissions-service` will be structured following CampXSync conventions:

```
services/student-services/admissions-service/
├── pom.xml                                       (Java 1.8, Spring Boot / JDK HttpServer, CampXLogger)
├── src/main/java/com/campx/admissions/
│   ├── AdmissionsApplication.java                (Bootstrap entry point)
│   ├── config/
│   │   ├── DatabaseConfig.java
│   │   └── DatabaseConnectionManager.java
│   ├── controller/
│   │   ├── ApplicationController.java            (Application intake, drafting, submission)
│   │   ├── ReviewController.java                 (Evaluator scoring, assignments)
│   │   ├── DecisionController.java               (Decisions, offers, acceptance)
│   │   └── ConfigurationController.java          (Intake windows, eligibility rules)
│   ├── model/
│   │   ├── ApplicationEntity.java
│   │   ├── AdmissionIntakeEntity.java
│   │   ├── ApplicationReviewEntity.java
│   │   ├── AdmissionOfferEntity.java
│   │   ├── ApplicationDocumentEntity.java
│   │   ├── ApplicationStatus.java                (Strict state machine enum)
│   │   └── dto/                                  (Ingress/Egress request/response models)
│   ├── repository/
│   │   ├── ApplicationRepository.java            (Interface)
│   │   ├── PostgresApplicationRepository.java    (JDBC with tenant claims & outbox)
│   │   ├── AdmissionIntakeRepository.java
│   │   ├── PostgresAdmissionIntakeRepository.java
│   │   ├── ApplicationReviewRepository.java
│   │   └── AdmissionOfferRepository.java
│   ├── security/
│   │   ├── GatewayHmacVerifier.java              (Cryptographic boundary check)
│   │   ├── UserSecurityContext.java              (Tenant context & Local role switching)
│   │   └── AdmissionsPermissionEvaluator.java    (RBAC & ABAC scope validation)
│   ├── server/
│   │   └── AdmissionsServer.java                 (Embedded HTTP server bound to loopback)
│   └── service/
│       ├── ApplicationDomainService.java         (State transitions, business rules)
│       ├── EligibilityEvaluationEngine.java      (Versioned criteria evaluation)
│       ├── ReviewAndDecisionService.java         (Evaluations and offer lifecycle)
│       ├── HandoverOrchestrationService.java     (STM-01/STM-03 idempotent coordination)
│       └── AdmissionsEventPublisher.java         (Transactional outbox publisher)
└── src/test/java/com/campx/admissions/           (Unit, integration, tenant-isolation, security tests)
```

---

## 5. Implementation Roadmap & Readiness Summary

1. **Service Registration:** Register `/api/v1/admissions` route in `GatewayConfig.java` pointing to `http://localhost:8094`.
2. **Pom Parent Linkage:** Add `<module>services/student-services/admissions-service</module>` to `pom.xml` under root and `services/student-services/pom.xml`.
3. **Fail-Closed Runtime Mode:** Ensure `AdmissionsServer` fails fast with `IllegalStateException` if run in a deployed environment without PostgreSQL credentials or with mock repositories.
4. **Current Status:** All components listed above are currently **UNIMPLEMENTED GAPS**. No code has been created during this read-only audit.

