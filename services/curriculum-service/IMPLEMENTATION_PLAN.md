# ACD-02 Curriculum Management Service — Implementation Plan for Gap Remediation

**Document ID:** ACD-02-IMPL-PLAN-001  
**Version:** 1.0.0  
**Date:** 23 September 2026  
**Author:** CampX Platform Engineering Team  
**Service:** ACD-02 Curriculum Management Service (`services/curriculum-service`)  
**Status:** Pending Approval  
**Reference:** [ACD-02 Gap Analysis Report](./GAP_ANALYSIS.md) | [User Stories CSV](../../Documentation/AcadamicsModule/UserStories/ACD-02_Curriculum_Management_User_Stories.csv)

---

## Table of Contents

1. [Executive Summary](#1-executive-summary)
2. [Scope & Objectives](#2-scope--objectives)
3. [Implementation Phases Overview](#3-implementation-phases-overview)
4. [Phase 1 — Critical Functional Fixes](#4-phase-1--critical-functional-fixes-sprint-1)
5. [Phase 2 — Security & Data Governance Hardening](#5-phase-2--security--data-governance-hardening-sprint-2)
6. [Phase 3 — Event Infrastructure & Reliability](#6-phase-3--event-infrastructure--reliability-sprint-3)
7. [Phase 4 — Observability & Performance](#7-phase-4--observability--performance-sprint-4)
8. [Phase 5 — E2E Integration & Production Readiness](#8-phase-5--e2e-integration--production-readiness-sprint-5)
9. [Risk Register](#9-risk-register)
10. [Verification & Sign-Off Matrix](#10-verification--sign-off-matrix)
11. [Appendix A — File Change Inventory](#11-appendix-a--file-change-inventory)

---

## 1. Executive Summary

The ACD-02 Curriculum Management Service currently achieves **~70% full implementation coverage** (50 out of 71 user stories) against the approved ACD-02 User Stories specification. The core business logic across all 9 domain epics is functional. However, **21 stories** remain partially or fully unimplemented, primarily in the areas of:

- **Syllabus body parsing** (controller defect)
- **Security hardening** (External API auth, data classification, encryption)
- **Event infrastructure** (broker relay, retry/DLQ strategy)
- **Observability** (metrics, tracing, alerting)
- **Production readiness** (DR, scaling, cross-module E2E tests)

This document defines a **5-phase implementation plan** to achieve 100% story coverage and production readiness.

---

## 2. Scope & Objectives

### 2.1 In-Scope

| Area | Stories Covered |
|---|---|
| Critical functional fixes (syllabus parsing, accreditation view) | Stories 29, 62 |
| Security & data governance (API key auth, classification, encryption) | Stories 63, 65, 66 |
| Event reliability (broker relay, retry, DLQ, idempotency cleanup) | Stories 51, 54, 57, 71 |
| Observability & performance (metrics, tracing, alerting, indexes, DR) | Stories 68, 69, 70, 72, 73, 74 |
| API contract (OpenAPI spec) | Story 76 |
| E2E cross-module integration testing | Stories 79, 80, 81, 82 |

### 2.2 Out-of-Scope

- Implementation of downstream modules (ACD-03, ACD-04, EXM, STM, HRM, COM, ACD-10) — these are separate service initiatives.
- UI/frontend portal development.
- Production deployment pipeline setup (CI/CD).

### 2.3 Success Criteria

- All 71 user stories marked as ✅ **Fully Implemented** in an updated gap analysis.
- All new/modified code has corresponding automated test coverage.
- Service compiles and passes `mvn test` with **0 failures**.
- API Gateway routes verified end-to-end for all new endpoints.

---

## 3. Implementation Phases Overview

```
┌─────────────────────────────────────────────────────────────────────────────┐
│                    ACD-02 Gap Remediation Roadmap                          │
├─────────────┬───────────────┬───────────────┬──────────────┬───────────────┤
│  Phase 1    │   Phase 2     │   Phase 3     │  Phase 4     │   Phase 5     │
│  Critical   │   Security &  │   Event Infra │  Observ. &   │   E2E &       │
│  Functional │   Data Gov.   │   & Reliab.   │  Performance │   Prod Ready  │
│  Fixes      │   Hardening   │               │              │               │
├─────────────┼───────────────┼───────────────┼──────────────┼───────────────┤
│  Sprint 1   │   Sprint 2    │   Sprint 3    │  Sprint 4    │   Sprint 5    │
│  ~3 days    │   ~5 days     │   ~5 days     │  ~5 days     │   ~3 days     │
├─────────────┼───────────────┼───────────────┼──────────────┼───────────────┤
│ Stories:    │ Stories:      │ Stories:      │ Stories:     │ Stories:      │
│ 29, 62, 76  │ 63, 65, 66   │ 51, 54, 57,  │ 68, 69, 70,  │ 79, 80, 81,  │
│             │               │ 58, 71       │ 72, 73, 74   │ 82            │
│ Effort: 16h │ Effort: 24h  │ Effort: 32h  │ Effort: 32h │ Effort: 20h  │
└─────────────┴───────────────┴───────────────┴──────────────┴───────────────┘
                                                        Total: ~124 hours
```

---

## 4. Phase 1 — Critical Functional Fixes (Sprint 1)

**Duration:** ~3 days (16 hours)  
**Priority:** 🔴 Critical  
**Prerequisite:** None  

### 4.1 Task 1.1 — Fix Syllabus Body Parsing (Story 29)

**Problem:** `CurriculumController.handleUpdateSyllabus()` hardcodes a single `SyllabusModule` instead of parsing the request body's actual module/topic JSON structure.

**Current Code (Lines 526–534 of `CurriculumController.java`):**
```java
private void handleUpdateSyllabus(...) throws IOException {
    String body = readBody(exchange);
    // Simple module parsing
    List<SyllabusModule> modules = new ArrayList<>();
    modules.add(new SyllabusModule("MOD-1", "Core Foundations", 1,
        Arrays.asList("Introduction", "Advanced Concepts"), 30.0));
    domainService.updateSyllabus(curriculumId, versionNo, modules, userId, userRole);
    ...
}
```

**Proposed Change:**

```java
private void handleUpdateSyllabus(...) throws IOException {
    String body = readBody(exchange);
    List<SyllabusModule> modules = parseSyllabusModules(body);
    if (modules.isEmpty()) {
        throw new CurriculumException(400, "ACD2_VALIDATION_ERROR",
            "Syllabus must contain at least one module with moduleId, title, order, and topics");
    }
    domainService.updateSyllabus(curriculumId, versionNo, modules, userId, userRole);
    sendStandardResponse(exchange, 200,
        "{\"status\":\"UPDATED\",\"moduleCount\":" + modules.size() + "}");
}

/**
 * Parses syllabus module/topic structure from the JSON request body.
 * Expected format: { "modules": [ { "moduleId", "title", "order", "topics": [...], "hours" } ] }
 */
private List<SyllabusModule> parseSyllabusModules(String body) {
    List<SyllabusModule> modules = new ArrayList<>();
    // Parse "modules" array from JSON body using regex-based extraction
    // For each module object: extract moduleId, title, order, topics array, hours
    // Return parsed list (or empty list if body is malformed)
    ...
    return modules;
}
```

**Files Modified:**

| File | Change Type | Description |
|---|---|---|
| `CurriculumController.java` | MODIFY | Replace hardcoded syllabus with JSON body parser |

**Acceptance Criteria:**
- [ ] `PUT /api/v1/academics/curricula/{id}/versions/{v}/syllabus` accepts JSON body with `modules` array
- [ ] Each module's `moduleId`, `title`, `order`, `topics[]`, and `hours` are persisted
- [ ] Empty or malformed body returns `400 ACD2_VALIDATION_ERROR`
- [ ] `CurriculumSyllabusUpdated` outbox event emitted with correct `moduleCount`
- [ ] Unit test: `testSyllabusBodyParsing` validates parsed structure

---

### 4.2 Task 1.2 — Add Accreditation Portal View (Story 62)

**Problem:** Accreditation Team role has `CURRICULUM_VIEW` and `CURRICULUM_EXPORT` permissions, but no specialized compliance/audit endpoint exists.

**Proposed Change:**

Add a new endpoint: `GET /api/v1/academics/curricula/{id}/compliance-view`

This returns a read-only composite view combining:
- Curriculum detail (filtered to L1/L2 fields)
- All versions with outcomes
- Audit history (full trail)
- Subject mappings

**Files Modified:**

| File | Change Type | Description |
|---|---|---|
| `CurriculumController.java` | MODIFY | Add `handleComplianceView()` route handler |
| `CurriculumDomainService.java` | MODIFY | Add `getComplianceView()` method returning composite DTO |
| `CurriculumModels.java` | MODIFY | Add `ComplianceView` static inner class |

**Acceptance Criteria:**
- [ ] `GET /api/v1/academics/curricula/{id}/compliance-view` returns composite structure
- [ ] Only accessible by roles with `CURRICULUM_VIEW` + `CURRICULUM_EXPORT` permissions
- [ ] Students and unauthorized roles receive `403 ACD2_FORBIDDEN`
- [ ] Response includes: curriculum summary, all versions, outcomes, subject mappings, full audit history
- [ ] API Gateway route registered in `GatewayConfig.java`

---

### 4.3 Task 1.3 — Generate OpenAPI Specification (Story 76)

**Problem:** No formal machine-readable API contract exists.

**Proposed Change:**

Create `services/curriculum-service/openapi.yaml` documenting all endpoints with:
- Request/response schemas for all 20+ endpoints
- Error code taxonomy with examples
- Authentication headers (`X-Tenant-Id`, `X-User-Role`, `X-Trace-Id`, `Idempotency-Key`)
- Pagination parameters

**Files Created:**

| File | Change Type | Description |
|---|---|---|
| `openapi.yaml` | NEW | OpenAPI 3.0 specification for ACD-02 |

**Acceptance Criteria:**
- [ ] Valid OpenAPI 3.0 YAML covering every documented endpoint
- [ ] All 11 error codes documented with example responses
- [ ] Schemas defined for: Curriculum, CurriculumVersion, CurriculumSubject, CurriculumOutcome, CurriculumPrerequisite, SyllabusModule, CurriculumHistory, OutboxEvent, DeadLetterEvent
- [ ] Validates cleanly via `swagger-cli validate openapi.yaml`

---

## 5. Phase 2 — Security & Data Governance Hardening (Sprint 2)

**Duration:** ~5 days (24 hours)  
**Priority:** 🔴 Critical  
**Prerequisite:** Phase 1 completed  

### 5.1 Task 2.1 — External API Consumer Authentication (Story 63)

**Problem:** No API key-based authentication or scope-limited External API consumer role exists.

**Proposed Change:**

Implement API key authentication via an `X-API-Key` header with scope definitions:

```
┌─────────────────────────────────────────────────────────┐
│              External API Auth Flow                      │
├─────────────────────────────────────────────────────────┤
│  Client ──► X-API-Key: <key> ──► CurriculumController  │
│                                         │                │
│                              ┌──────────▼──────────┐    │
│                              │  API Key Registry    │    │
│                              │  ┌────────────────┐  │    │
│                              │  │ Key: abc123     │  │    │
│                              │  │ Scope: READ     │  │    │
│                              │  │ TenantId: T-001 │  │    │
│                              │  │ ExpiresAt: ...  │  │    │
│                              │  └────────────────┘  │    │
│                              └──────────────────────┘    │
│                                         │                │
│                              Scope validated ──► proceed │
│                              No key / invalid ──► 401    │
│                              Scope exceeded ──► 403      │
└─────────────────────────────────────────────────────────┘
```

**Design Details:**

1. Add `ApiKeyRecord` model to `CurriculumModels.java`:
   - `keyId`, `hashedKey`, `tenantId`, `scopes[]`, `createdAt`, `expiresAt`, `isActive`
2. Add `apiKeyRegistry: Map<String, ApiKeyRecord>` to `CurriculumDomainService.java`
3. In `CurriculumController.handle()`, if `X-API-Key` header is present:
   - Validate key exists and is not expired
   - Set `userRole = "EXTERNAL_API"` with scoped permissions
   - Reject write operations (POST/PUT/DELETE) with `403 ACD2_FORBIDDEN`
4. Add `EXTERNAL_API` role to `getRolePermissions()` with `CURRICULUM_VIEW` only

**Files Modified:**

| File | Change Type | Description |
|---|---|---|
| `CurriculumModels.java` | MODIFY | Add `ApiKeyRecord` model |
| `CurriculumDomainService.java` | MODIFY | Add API key registry, validation, EXTERNAL_API role |
| `CurriculumController.java` | MODIFY | Add API key extraction and validation in `handle()` |
| `CurriculumControllerIntegrationTest.java` | MODIFY | Add test for API key auth flow |

**Acceptance Criteria:**
- [ ] `X-API-Key` header authentication works for external consumers
- [ ] External API consumers get read-only access limited to their tenant scope
- [ ] Write operations rejected with `403 ACD2_FORBIDDEN`
- [ ] Invalid or expired API keys return `401 Unauthorized`
- [ ] Test: `testExternalApiKeyReadOnlyAccess`
- [ ] Test: `testExternalApiKeyWriteRejected`

---

### 5.2 Task 2.2 — Data Sensitivity Classification & Field Filtering (Story 65)

**Problem:** No L1/L2/L3/L4 field-level data classification exists. All fields are returned regardless of the caller's clearance level.

**Proposed Change:**

Implement a field-level classification system:

| Level | Classification | Fields | Accessible By |
|---|---|---|---|
| **L1** Public | `id`, `name`, `courseId`, `status`, `academicPattern`, `departmentId` | All roles including Student |
| **L2** Internal | `currentVersion`, `effectiveFrom`, `effectiveTo`, `totalCredits`, `semesters`, `syllabus` | Faculty, Admin, Dept Head, Registrar |
| **L3** Confidential | `createdBy`, `updatedBy`, `approvedBy`, `publishedBy`, audit history, actor metadata | Admin, Registrar, Accreditation Team |
| **L4** Highly Restricted | `tenantId`, `institutionId`, internal config, API keys, encryption keys | Super Admin, System |

**Design Details:**

1. Add `DataClassification` enum to `CurriculumModels.java`: `PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED`
2. Add `getClassificationLevel(String role)` to `CurriculumDomainService.java`
3. Add response filtering utility: `filterFieldsByClassification(String json, DataClassification level)`
4. Apply filtering in `CurriculumController` response handlers before sending to client

**Files Modified:**

| File | Change Type | Description |
|---|---|---|
| `CurriculumModels.java` | MODIFY | Add `DataClassification` enum |
| `CurriculumDomainService.java` | MODIFY | Add classification level resolution per role |
| `CurriculumController.java` | MODIFY | Apply field filtering in response serialization |
| `CurriculumDomainServiceTest.java` | MODIFY | Add classification tests |

**Acceptance Criteria:**
- [ ] Student responses contain only L1 Public fields
- [ ] Faculty responses contain L1 + L2 Internal fields
- [ ] Admin/Registrar responses contain L1 + L2 + L3 Confidential fields
- [ ] L4 fields (tenantId, institutionId) excluded from all non-Super Admin responses
- [ ] Test: `testStudentSeesOnlyPublicFields`
- [ ] Test: `testAdminSeesConfidentialFields`

---

### 5.3 Task 2.3 — TLS & Encryption Configuration (Story 66)

**Problem:** No TLS configuration or encryption-at-rest settings.

**Proposed Change:**

1. Add TLS configuration support to `CurriculumServer.java`:
   - Support `HTTPS_KEYSTORE_PATH`, `HTTPS_KEYSTORE_PASSWORD` environment variables
   - Create `HttpsServer` when keystore is configured, fallback to `HttpServer` for dev mode
2. Add sensitive field masking in log output
3. Document MongoDB encryption-at-rest configuration

**Files Modified:**

| File | Change Type | Description |
|---|---|---|
| `CurriculumServer.java` | MODIFY | Add TLS/HTTPS server creation support |
| `CurriculumController.java` | MODIFY | Mask sensitive fields in log statements |
| `DEPLOYMENT_SECURITY.md` | NEW | Document MongoDB encryption-at-rest, TLS setup |

**Acceptance Criteria:**
- [ ] Service starts with HTTPS when keystore is provided
- [ ] Service falls back to HTTP in development mode (no keystore)
- [ ] Sensitive audit fields (actorId, approvedBy) masked in log output
- [ ] `DEPLOYMENT_SECURITY.md` documents: TLS setup, MongoDB encryption, backup encryption, log masking
- [ ] Test: Verify service starts successfully in both HTTP and HTTPS modes

---

## 6. Phase 3 — Event Infrastructure & Reliability (Sprint 3)

**Duration:** ~5 days (32 hours)  
**Priority:** 🟡 High  
**Prerequisite:** Phase 1 completed  

### 6.1 Task 3.1 — Outbox Event Relay to Message Broker (Story 51)

**Problem:** Outbox events are written to an in-memory list but never relayed to a message broker.

**Proposed Change:**

Implement an `OutboxRelayService` that polls the outbox and publishes to a configurable broker endpoint:

```
┌───────────────────────────────────────────────────────┐
│               Outbox Relay Architecture                │
├───────────────────────────────────────────────────────┤
│                                                       │
│  Domain Service ──► outbox_events (in-memory/MongoDB) │
│                              │                        │
│                    ┌─────────▼──────────┐             │
│                    │  OutboxRelayService │             │
│                    │  (Scheduled Poller) │             │
│                    │                    │             │
│                    │  Poll PENDING      │             │
│                    │  Publish to broker │             │
│                    │  Mark PUBLISHED    │             │
│                    │  On failure: retry │             │
│                    │  On exhaust: DLQ   │             │
│                    └────────────────────┘             │
│                              │                        │
│                    ┌─────────▼──────────┐             │
│                    │  Kafka / RabbitMQ   │             │
│                    │  (Configurable)     │             │
│                    └────────────────────┘             │
└───────────────────────────────────────────────────────┘
```

**Design Details:**

1. Create `OutboxRelayService.java`:
   - Scheduled polling loop (configurable interval, default 5 seconds)
   - Reads PENDING events from outbox
   - Publishes to broker endpoint (HTTP POST to configurable URL)
   - Updates event status to PUBLISHED on success
   - On failure: increments `attempts`, applies exponential backoff
   - After max retries (configurable, default 5): routes to DLQ
2. Add relay configuration: broker URL, polling interval, max retries, backoff base/max
3. Start relay as daemon thread in `CurriculumServer.java`

**Files Created/Modified:**

| File | Change Type | Description |
|---|---|---|
| `OutboxRelayService.java` | NEW | Outbox polling and broker publishing service |
| `CurriculumServer.java` | MODIFY | Start OutboxRelayService as daemon thread |
| `CurriculumDomainService.java` | MODIFY | Add methods: `getPendingOutboxEvents()`, `markEventPublished()`, `markEventFailed()` |

**Acceptance Criteria:**
- [ ] PENDING outbox events are polled at configurable interval
- [ ] Events published to broker endpoint (HTTP POST)
- [ ] Event status transitions: PENDING → PUBLISHED (on success), PENDING → FAILED (on exhausted retries)
- [ ] Failed events routed to dead_letter_events after max retries
- [ ] Relay is non-blocking and does not affect request processing
- [ ] Test: `testOutboxRelayPublishesEvents`
- [ ] Test: `testOutboxRelayRetriesOnFailure`

---

### 6.2 Task 3.2 — Retry with Exponential Backoff (Stories 54, 71)

**Problem:** No retry logic for transient failures. Events go directly to DLQ without retries.

**Proposed Change:**

Implement a `RetryPolicy` utility class:

```java
/**
 * Bounded exponential backoff retry policy with jitter.
 * Never retries validation/authorization errors (4xx).
 */
public class RetryPolicy {
    private final int maxRetries;        // Default: 5
    private final long baseDelayMs;      // Default: 1000ms
    private final long maxDelayMs;       // Default: 30000ms
    private final double jitterFactor;   // Default: 0.1

    public long getDelayMs(int attempt) {
        long delay = (long)(baseDelayMs * Math.pow(2, attempt));
        delay = Math.min(delay, maxDelayMs);
        delay += (long)(delay * jitterFactor * Math.random());
        return delay;
    }

    public boolean isRetryable(int httpStatus) {
        return httpStatus >= 500 || httpStatus == 408 || httpStatus == 429;
    }
}
```

**Files Created/Modified:**

| File | Change Type | Description |
|---|---|---|
| `RetryPolicy.java` | NEW | Exponential backoff retry policy utility |
| `OutboxRelayService.java` | MODIFY | Use RetryPolicy for broker publish retries |
| `CurriculumDomainService.java` | MODIFY | Use RetryPolicy in event consumption |

**Acceptance Criteria:**
- [ ] Retries use exponential backoff with jitter
- [ ] Maximum retry bound is configurable (default 5)
- [ ] 4xx errors (validation, auth) are never retried
- [ ] 5xx, 408, 429 errors are retried
- [ ] After max retries exhausted, event routes to DLQ with failure context
- [ ] Test: `testRetryPolicyBackoffCalculation`
- [ ] Test: `testNonRetryableErrorsSkipRetry`

---

### 6.3 Task 3.3 — Background Idempotency Key Cleanup (Story 57)

**Problem:** Expired idempotency keys are only evicted on re-access, not proactively purged.

**Proposed Change:**

Add a scheduled cleanup task in `CurriculumServer.java`:

```java
/**
 * Scheduled daemon task that purges expired idempotency records
 * from the idempotency_records collection every hour.
 */
ScheduledExecutorService cleanupScheduler = Executors.newSingleThreadScheduledExecutor(r -> {
    Thread t = new Thread(r, "idempotency-cleanup");
    t.setDaemon(true);
    return t;
});
cleanupScheduler.scheduleAtFixedRate(
    () -> domainService.purgeExpiredIdempotencyRecords(),
    1, 1, TimeUnit.HOURS
);
```

**Files Modified:**

| File | Change Type | Description |
|---|---|---|
| `CurriculumDomainService.java` | MODIFY | Add `purgeExpiredIdempotencyRecords()` method |
| `CurriculumServer.java` | MODIFY | Add scheduled cleanup executor |

**Acceptance Criteria:**
- [ ] Expired idempotency records purged every hour (configurable)
- [ ] Purged keys become available for legitimate reuse
- [ ] Cleanup is non-blocking, runs on daemon thread
- [ ] Cleanup logs count of purged records
- [ ] Test: `testExpiredIdempotencyKeysPurged`

---

### 6.4 Task 3.4 — MongoDB Transaction Simulation Enhancement (Story 58)

**Problem:** Current `synchronized` blocks provide atomicity for the in-memory store but don't model MongoDB multi-document transactions.

**Proposed Change:**

Create a `TransactionContext` wrapper that:
- Groups writes into a unit-of-work
- Provides rollback on exception
- Logs transaction boundaries for eventual MongoDB migration

**Files Created/Modified:**

| File | Change Type | Description |
|---|---|---|
| `TransactionContext.java` | NEW | Unit-of-work transaction wrapper |
| `CurriculumDomainService.java` | MODIFY | Wrap coupled writes in TransactionContext |

**Acceptance Criteria:**
- [ ] Coupled writes (curriculum + version + history + outbox) use TransactionContext
- [ ] On exception, all writes in the transaction are rolled back
- [ ] Transaction boundaries logged with start/commit/rollback markers
- [ ] Test: `testTransactionRollbackOnFailure`

---

## 7. Phase 4 — Observability & Performance (Sprint 4)

**Duration:** ~5 days (32 hours)  
**Priority:** 🟡 High  
**Prerequisite:** Phase 1 completed  

### 7.1 Task 4.1 — Structured Logging Enhancement (Story 68)

**Problem:** Not all log entries contain the full required structured field set.

**Proposed Change:**

Create a `StructuredLogEntry` builder that ensures every log contains:
- `timestamp`, `service`, `tenantId`, `requestId`, `correlationId`, `actorId`, `operation`, `outcome`, `durationMs`

Wrap every controller handler with entry/exit logging using this structure.

**Files Created/Modified:**

| File | Change Type | Description |
|---|---|---|
| `StructuredLogEntry.java` | NEW | Structured log entry builder |
| `CurriculumController.java` | MODIFY | Add structured entry/exit logging to all handlers |

**Acceptance Criteria:**
- [ ] Every API request emits structured log with all 9 required fields
- [ ] `correlationId` propagates through to published outbox events
- [ ] Log output is valid JSON when structured logging is enabled
- [ ] Test: Verify log output contains required fields

---

### 7.2 Task 4.2 — Operational Metrics Endpoint (Story 69)

**Problem:** No Prometheus metrics endpoint or metric collection.

**Proposed Change:**

Implement a `/metrics` endpoint exposing:
- `acd02_request_total{method, path, status}` — request counter
- `acd02_request_duration_seconds{method, path}` — latency histogram
- `acd02_error_total{errorCode}` — error counter
- `acd02_outbox_pending_count` — pending outbox events
- `acd02_outbox_published_total` — published events counter
- `acd02_dlq_count` — dead letter queue size
- `acd02_curricula_total{status}` — curricula by status
- `acd02_validation_failure_total{type}` — validation failure counter

**Files Created/Modified:**

| File | Change Type | Description |
|---|---|---|
| `MetricsCollector.java` | NEW | In-memory metrics collection and Prometheus exposition |
| `CurriculumController.java` | MODIFY | Add `/metrics` endpoint, instrument handlers |
| `CurriculumServer.java` | MODIFY | Register `/metrics` context |
| `GatewayConfig.java` | MODIFY | Add metrics route to gateway |

**Acceptance Criteria:**
- [ ] `GET /metrics` returns Prometheus exposition format
- [ ] All 8 metric families exposed with correct labels
- [ ] Request duration measured with nanosecond precision
- [ ] Metrics endpoint does not require authentication
- [ ] Test: `testMetricsEndpointExposesCounters`

---

### 7.3 Task 4.3 — Alerting Configuration Document (Story 70)

**Problem:** No alerting rules defined.

**Proposed Change:**

Create `ALERTING_RULES.md` and a sample `alerting_rules.yml` (Prometheus Alertmanager format) covering:
- 5xx spike (>5% error rate in 5m window)
- P95 latency breach (>500ms)
- Outbox backlog age (>5 minutes)
- DLQ growth (>0 events in 1h)
- Unauthorized access spikes (>10 in 5m)

**Files Created:**

| File | Change Type | Description |
|---|---|---|
| `alerting_rules.yml` | NEW | Prometheus alerting rules |
| `ALERTING_RULES.md` | NEW | Alert documentation with runbooks |

**Acceptance Criteria:**
- [ ] All 11 alert signals from Story 70 have defined rules
- [ ] Each alert includes: expression, threshold, duration, severity, runbook link
- [ ] Rules validate against `promtool check rules alerting_rules.yml`

---

### 7.4 Task 4.4 — MongoDB Index Definitions (Story 72)

**Problem:** No MongoDB index definitions exist for performance targets.

**Proposed Change:**

Create `mongodb_indexes.js` with index definitions for all collections:

```javascript
// curricula collection
db.curricula.createIndex({ tenantId: 1, courseId: 1, academicPattern: 1 }, { unique: true });
db.curricula.createIndex({ tenantId: 1, status: 1 });
db.curricula.createIndex({ departmentId: 1, status: 1 });

// curriculum_versions collection
db.curriculum_versions.createIndex({ curriculumId: 1, versionNo: 1 }, { unique: true });
db.curriculum_versions.createIndex({ curriculumId: 1, status: 1 });

// curriculum_subjects collection
db.curriculum_subjects.createIndex({ curriculumId: 1, versionNo: 1, semesterNo: 1 });
db.curriculum_subjects.createIndex({ subjectId: 1, status: 1 });

// curriculum_history collection
db.curriculum_history.createIndex({ curriculumId: 1, sequenceNo: 1 });
db.curriculum_history.createIndex({ tenantId: 1, occurredAt: -1 });

// outbox_events collection
db.outbox_events.createIndex({ status: 1, occurredAt: 1 });

// idempotency_records collection
db.idempotency_records.createIndex({ tenantId: 1, idempotencyKey: 1 }, { unique: true });
db.idempotency_records.createIndex({ expiresAt: 1 }, { expireAfterSeconds: 0 });
```

**Files Created:**

| File | Change Type | Description |
|---|---|---|
| `mongodb_indexes.js` | NEW | MongoDB index creation script |
| `PERFORMANCE_TARGETS.md` | NEW | P95 latency targets and verification plan |

**Acceptance Criteria:**
- [ ] All 7 collections have appropriate indexes defined
- [ ] Unique constraints match business rules (BR-07, BR-08)
- [ ] TTL index on `idempotency_records.expiresAt` for automatic cleanup
- [ ] Performance targets documented: API read P95 <300ms, search P95 <500ms

---

### 7.5 Task 4.5 — Disaster Recovery Documentation (Story 73)

**Problem:** No backup/restore procedures documented.

**Proposed Change:**

Create `DISASTER_RECOVERY.md` documenting:
- MongoDB backup strategy (mongodump/mongoexport schedule)
- RPO/RTO targets
- Restore procedure with verification steps
- Outbox event replay procedure

**Files Created:**

| File | Change Type | Description |
|---|---|---|
| `DISASTER_RECOVERY.md` | NEW | DR procedures and verification plan |

---

### 7.6 Task 4.6 — Horizontal Scaling Documentation (Story 74)

**Problem:** No autoscaling configuration documented.

**Proposed Change:**

Create `SCALING_GUIDE.md` documenting:
- Stateless service tier design verification
- Kubernetes HPA configuration examples
- Event throughput scaling (consumer group partitioning)
- Zero-DB-sharing invariant verification

**Files Created:**

| File | Change Type | Description |
|---|---|---|
| `SCALING_GUIDE.md` | NEW | Horizontal scaling guide |

---

## 8. Phase 5 — E2E Integration & Production Readiness (Sprint 5)

**Duration:** ~3 days (20 hours)  
**Priority:** 🟢 Medium  
**Prerequisite:** Phases 1–4 completed  

### 8.1 Task 5.1 — E2E Lifecycle Test (Stories 79, 80)

**Problem:** No true E2E test covering full lifecycle with downstream event verification.

**Proposed Change:**

Create `CurriculumE2ELifecycleTest.java`:
- **Test 1:** Create → Add Semesters → Map Subjects → Define Syllabus → Add Outcomes → Submit → Review → Approve → Publish → Verify all outbox events emitted in sequence
- **Test 2:** Publish V1 → Clone for Annual Revision → Modify V2 → Publish V2 → Verify V1 is SUPERSEDED → Verify V1 remains queryable

**Files Created:**

| File | Change Type | Description |
|---|---|---|
| `CurriculumE2ELifecycleTest.java` | NEW | Full lifecycle E2E test |

**Acceptance Criteria:**
- [ ] Complete DRAFT→PUBLISHED lifecycle verified
- [ ] All 11 event types verified in outbox
- [ ] Annual revision preserves prior version as SUPERSEDED
- [ ] Prior version remains queryable and unchanged

---

### 8.2 Task 5.2 — E2E Subject Deactivation Test (Story 81)

**Problem:** Need end-to-end verification that SubjectDeactivated event consumption blocks publication.

**Proposed Change:**

Add to `CurriculumE2ELifecycleTest.java`:
- **Test 3:** Create curriculum → Map subject SUB-101 → Consume SubjectDeactivated for SUB-101 → Attempt publish → Verify blocked with `ACD2_PUBLICATION_BLOCKED`

**Acceptance Criteria:**
- [ ] Mapping flagged after SubjectDeactivated event
- [ ] Publication rejected with correct error code
- [ ] Flagged mapping visible in subject mappings list

---

### 8.3 Task 5.3 — Cross-Module Integration Contract Tests (Story 82)

**Problem:** No integration tests against downstream modules (most not yet built).

**Proposed Change:**

Create `CrossModuleContractTest.java` with mock/stub verifications:
- **ACD-01 contract:** Verify `CurriculumPublished` event schema matches ACD-01 expected format
- **ACD-03 contract:** Verify `CurriculumSubjectMapped` event includes required fields
- **Event schema validation:** Verify all outbox event payloads contain `eventId`, `entityId`, `tenantId`, `version`

Create `CROSS_MODULE_INTEGRATION.md` documenting:
- Contract requirements for each of the 8 downstream modules
- Event schema expectations per consumer
- Stub/mock strategy until modules are available

**Files Created:**

| File | Change Type | Description |
|---|---|---|
| `CrossModuleContractTest.java` | NEW | Event contract schema validation tests |
| `CROSS_MODULE_INTEGRATION.md` | NEW | Integration contract documentation |

---

## 9. Risk Register

| # | Risk | Probability | Impact | Mitigation |
|---|---|---|---|---|
| R1 | Syllabus JSON parsing fails on edge-case nested structures | Medium | Medium | Comprehensive test cases for nested topics, empty arrays, special characters |
| R2 | External API key mechanism insufficient for production OAuth2 requirements | Medium | High | Design API key as interim; document migration path to OAuth2/JWT |
| R3 | Outbox relay performance degrades under high event volume | Low | High | Configurable batch size, backpressure mechanism, monitoring via metrics |
| R4 | Data classification filtering adds latency to all responses | Low | Medium | Implement as response interceptor, benchmark overhead |
| R5 | MongoDB index definitions may need tuning after production load testing | Medium | Medium | Include index analysis in performance test suite |
| R6 | Downstream modules not available for E2E testing | High | Medium | Use contract tests with stubs; defer full E2E to integration environment |

---

## 10. Verification & Sign-Off Matrix

| Phase | Verification Method | Sign-Off Authority | Gate Criteria |
|---|---|---|---|
| Phase 1 | `mvn test` + manual API curl | Tech Lead | All new tests pass, syllabus parsing works |
| Phase 2 | `mvn test` + security review | Security Architect + Tech Lead | RBAC extended, data classification active |
| Phase 3 | `mvn test` + relay integration test | Tech Lead | Outbox relay functional, retry policy validated |
| Phase 4 | `mvn test` + `/metrics` verification | Platform Architect | Metrics exposed, alerts defined, indexes documented |
| Phase 5 | Full `mvn test` + E2E test suite | QA Lead + Tech Lead | All 71 stories verified, 0 test failures |

---

## 11. Appendix A — File Change Inventory

### New Files (12)

| File Path | Phase | Description |
|---|---|---|
| `src/main/java/.../service/OutboxRelayService.java` | 3 | Outbox event broker relay |
| `src/main/java/.../service/RetryPolicy.java` | 3 | Exponential backoff retry policy |
| `src/main/java/.../service/TransactionContext.java` | 3 | Unit-of-work transaction wrapper |
| `src/main/java/.../service/MetricsCollector.java` | 4 | Prometheus metrics collector |
| `src/main/java/.../service/StructuredLogEntry.java` | 4 | Structured log entry builder |
| `src/test/.../CurriculumE2ELifecycleTest.java` | 5 | E2E lifecycle tests |
| `src/test/.../CrossModuleContractTest.java` | 5 | Event contract validation tests |
| `openapi.yaml` | 1 | OpenAPI 3.0 specification |
| `mongodb_indexes.js` | 4 | MongoDB index definitions |
| `alerting_rules.yml` | 4 | Prometheus alerting rules |
| `DEPLOYMENT_SECURITY.md` | 2 | Security deployment guide |
| `DISASTER_RECOVERY.md` | 4 | DR procedures |
| `SCALING_GUIDE.md` | 4 | Horizontal scaling guide |
| `CROSS_MODULE_INTEGRATION.md` | 5 | Cross-module contract docs |
| `PERFORMANCE_TARGETS.md` | 4 | Performance targets and benchmarks |

### Modified Files (7)

| File Path | Phases | Description |
|---|---|---|
| `CurriculumController.java` | 1, 2, 4 | Syllabus parsing, compliance view, API key auth, metrics, structured logging |
| `CurriculumDomainService.java` | 1, 2, 3 | Compliance view, API key registry, idempotency cleanup, transaction context |
| `CurriculumModels.java` | 1, 2 | ComplianceView DTO, ApiKeyRecord, DataClassification enum |
| `CurriculumServer.java` | 3, 4 | Outbox relay, idempotency cleanup, metrics endpoint |
| `GatewayConfig.java` | 1, 4 | Compliance view route, metrics route |
| `CurriculumControllerIntegrationTest.java` | 2 | API key auth tests |
| `CurriculumDomainServiceTest.java` | 1, 2, 3 | Syllabus, classification, retry, transaction tests |

---

**End of Document**

*This implementation plan is subject to review and approval before execution begins.*
