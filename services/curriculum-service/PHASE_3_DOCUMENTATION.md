# ACD-02 Curriculum Management Service — Phase 3 Documentation

**Service Name:** CampXSync Curriculum Management Service (ACD-02)  
**Service Path:** `services/curriculum-service`  
**Phase:** Phase 3 — Event Infrastructure & Reliability  
**Date:** 23 September 2026  
**Status:** Completed & Verified  

---

## 1. Overview of Phase 3

Phase 3 provides event infrastructure, resilience, automatic recovery, and transaction atomicity:
1. **Story 51:** Asynchronous Outbox Event Relay service polling pending domain events and dispatching to message brokers (Kafka/RabbitMQ/HTTP).
2. **Stories 54, 71:** Bounded Exponential Backoff retry policy with jitter, non-retryable 4xx exclusion, and automated dead-letter queue (DLQ) routing.
3. **Story 57:** Proactive scheduled background cleanup daemon purging expired idempotency keys from memory/cache.
4. **Story 58:** Unit-of-Work `TransactionContext` simulating multi-document MongoDB transactions with compensation/rollback stack.

---

## 2. Implemented Features & Code Changes

### 2.1 Task 3.1 — Outbox Event Relay Service (Story 51)
- **Component:** Created `OutboxRelayService.java`.
- **Workflow:**
  - Scheduled background daemon thread polling `getPendingOutboxEvents()`.
  - Dispatches events to configured broker endpoint (`OUTBOX_BROKER_URL`) or internal message bus delegate.
  - On broker acknowledgment (HTTP 2xx), marks event status as `PUBLISHED`.
  - Integrated into `CurriculumServer` lifecycle (`start()` and `stop()`).
  - Supports synchronous execution via `relayPendingEvents()` for deterministic testing and batch reconciliation.

### 2.2 Task 3.2 — Retry Policy with Exponential Backoff (Stories 54, 71)
- **Component:** Created `RetryPolicy.java`.
- **Failure Classification:**
  - **Retryable:** HTTP 5xx (500, 502, 503), 408 Request Timeout, 429 Too Many Requests, and network I/O (`IOException`, `SocketTimeoutException`).
  - **Non-retryable:** HTTP 4xx (400, 401, 403, 404, 409, 422) fail fast without retrying.
- **Backoff Equation:** `delay = min(baseDelay * 2^attempt, maxDelay) + jitter`.
- **DLQ Routing:** Once max retries (default 5) are exhausted, event status transitions to `FAILED` and payload is persisted into `dead_letter_events` with error context.

### 2.3 Task 3.3 — Background Idempotency Key Purging (Story 57)
- **Domain Service:** Added `purgeExpiredIdempotencyRecords()` in `CurriculumDomainService` to proactively evict records where `expiresAt < System.currentTimeMillis()`.
- **Scheduler:** `CurriculumServer` starts a daemon `ScheduledExecutorService` running hourly to evict stale keys and free resources without blocking HTTP request execution.

### 2.4 Task 3.4 — Multi-Document Transaction Simulation (Story 58)
- **Component:** Created `TransactionContext.java`.
- **Unit-of-Work Semantics:**
  - Explicit boundaries: `[TX_START]`, `[TX_COMMIT]`, `[TX_ROLLBACK]` logged with correlation trace identifiers.
  - LIFO compensation stack executes reverse rollback operations if an unhandled exception aborts execution midway.
  - Integrated into `CurriculumDomainService.createCurriculum` atomic writes (curriculum, initial version, audit history, and outbox event).

---

## 3. Verification & Test Results

### 3.1 Automated Tests Executed
- `CurriculumDomainServiceTest.testRetryPolicyCalculations`: Verifies backoff boundaries, jitter, and HTTP status retry classification.
- `CurriculumDomainServiceTest.testOutboxRelayServiceSuccess`: Verifies PENDING outbox events are dispatched and marked `PUBLISHED`.
- `CurriculumDomainServiceTest.testOutboxRelayServiceRetryAndDLQOnFailure`: Verifies transient errors trigger retries, and exhausted retries route to DLQ.
- `CurriculumDomainServiceTest.testPurgeExpiredIdempotencyRecords`: Verifies expired idempotency keys are purged while active keys are preserved.
- `CurriculumDomainServiceTest.testTransactionContextRollback`: Verifies compensation stack execution on transaction abort.

### 3.2 Test Run Output Summary
```
[INFO] Running com.campx.academic.curriculum.CurriculumControllerIntegrationTest
[INFO] Tests run: 12, Failures: 0, Errors: 0, Skipped: 0
[INFO] Running com.campx.academic.curriculum.CurriculumDomainServiceTest
[INFO] Tests run: 26, Failures: 0, Errors: 0, Skipped: 0
[INFO] BUILD SUCCESS - Total time: 4.619 s
```
All 38 service tests passed with **0 failures**.
