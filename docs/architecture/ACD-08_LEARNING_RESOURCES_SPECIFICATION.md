# ACD-08 Learning Resources Service - Enterprise Technical Specification & User Story Blueprint

> **Module ID**: ACD-08  
> **Service Name**: Learning Resource Service  
> **Tier**: Academic Management Tier  
> **Authoritative Database**: `academic_resource_db` (MongoDB)  
> **Ingress Port**: `8090` (Service Direct) / `8080` (API Gateway Unified Ingress)  
> **Status**: Production Ready  
> **Version**: 2.0.0  
> **Traceability Source**: `ACD-08_learning_resources_user_stories_REFINED.csv` (US-001 to US-065)

---

## 1. Executive Summary & Purpose

The **ACD-08 Learning Resource Service** is the authoritative master for academic resource metadata, monotonic versioning, policy-driven access governance, and publication lifecycle across the CampXSync College ERP platform.

It manages metadata and content references for:
- Course and subject syllabi (with dual syllabus event dissemination)
- Faculty study materials, lecture slides, and notes
- Reference links and digital academic resources
- Lab manuals and assignment problem statements
- Question banks and revision repositories

### Fundamental Architectural Tenet: Metadata vs Binary Segregation
In accordance with Enterprise Architecture Directive ADR-ACD08-001:
- **ACD-08 owns metadata, versioning, access policy, and lifecycle state in MongoDB (`academic_resource_db`).**
- **Binary bytes remain strictly in shared document/object storage (S3 / GCS / Azure Blob).**
- ACD-08 maintains immutable storage object keys (`storageObjectRef`), MIME types, file sizes, and cryptographic checksums (`checksum` / `contentHash`).
- Authorized clients retrieve binary content via **pre-signed, short-lived, scoped retrieval URLs** issued by ACD-08 after evaluating access policy rules.
- Direct database sharing between services is strictly prohibited (ADR-ACD08-005); downstream systems (LMS, Search, Analytics, CAM) consume REST APIs or subscribe to domain events.

---

## 2. Bounded Context & Architecture Overview

```
                            +-------------------------------------------+
                            |          External Clients / Portals       |
                            |   (Faculty, Academic Admins, Students)    |
                            +---------------------+---------------------+
                                                  |
                                                  v
                            +-------------------------------------------+
                            |          API Gateway (Port 8080)          |
                            |  * Correlation Tracking (X-Trace-Id)      |
                            |  * Reverse Proxy & Timeout Resilience     |
                            |  * RFC 7807 Standard Error Formatting     |
                            +---------------------+---------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
|                             ACD-08 Learning Resource Service (Port 8090)                           |
|                                                                                                    |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |     ResourceController         |  |   ResourceValidationEngine     |  | RateLimiter & Metrics|  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |     ResourceDomainService      |  |   ResourceVersionManager       |  | ObjectStorageAdapter |  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |   ResourceAccessPolicyService  |  |      ResourceQueryService      |  | Reference Clients    |  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |          AuditAdapter          |  |       OutboxPublisher          |  | StructuredLogEntry   |  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
+-------------------+---------------------------------+----------------------------------+-----------+
                    |                                 |                                  |
                    v                                 v                                  v
+---------------------------------------+ +-----------------------+ +--------------------------------+
| MongoDB: academic_resource_db         | | Shared Object Storage | | Event Bus (Kafka / RabbitMQ)   |
| - resources (Authoritative Aggregate) | | - Binary Files        | | - LearningResourceCreated      |
| - resource_versions (Immutable Ver)   | | - PDF / Video / Notes | | - LearningResourcePublished    |
| - resource_access (Policy Grants)     | | - Pre-signed URLs     | | - LearningResourceArchived     |
| - resource_history (Audit Trail)      | +-----------------------+ | - SyllabusPublished            |
| - outbox_events (Transactional Outbox)|                           +---------------+----------------+
| - idempotency_records (Retry Store)   |                                           |
| - dead_letter_events (DLQ Failures)   |                                           v
+---------------------------------------+                          +---------------------------------+
                                                                   | Downstream Consumers:           |
                                                                   | LMS, Search, Analytics, CAM     |
                                                                   +---------------------------------+
```

---

## 3. MongoDB 7-Collection Enterprise Strategy

ACD-08 models its persistence layer into four authoritative business collections and three enterprise technical reliability collections:

| Collection Name | Category | Primary Responsibility | Primary Indexes |
|---|---|---|---|
| `resources` | Business | Authoritative resource aggregate & lifecycle pointers | `Unique(tenantId, resourceCode)`, `(tenantId, subjectId, status)`, `(courseId, status)`, `(resourceType, status)`, `(ownerId, updatedAt)` |
| `resource_versions` | Business | Monotonic, immutable version snapshots & storage references | `Unique(resourceId, versionNo)`, `(resourceId, createdAt)`, `(checksum)` |
| `resource_access` | Business | RBAC/ABAC access rules, time windows & DENY precedence | `(resourceId, status)`, `(principalType, principalId, status)`, `(scopeType, scopeId, resourceId)` |
| `resource_history` | Business/Audit | Append-only tamper-evident lifecycle audit history | `(resourceId, changedAt)`, `(changedBy, changedAt)` |
| `outbox_events` | Technical | Transactional outbox for reliable guaranteed event dispatch | `Unique(eventId)`, `(status, nextAttemptAt)`, `(aggregateId, createdAt)` |
| `idempotency_records` | Technical | Request hashing and idempotent retry store | `Unique(tenantId, idempotencyKey)`, `TTL(expiresAt)` |
| `dead_letter_events` | Technical | Dead letter queue store for exhausted retries | `(status, lastFailedAt)`, `(eventType, lastFailedAt)`, `(eventId)` |

---

## 4. Key Business Rules (BR-01 to BR-14)

| Rule ID | Rule Statement | Enforcement Component | HTTP Status Code |
|---|---|---|---|
| **BR-01** | `resourceCode` must be unique within a tenant. Duplicate business codes are rejected. | `ResourceDomainService` | `409 Conflict` (`ACD_RESOURCE_DUPLICATE`) |
| **BR-02** | `resourceType` must belong to supported taxonomy: `SYLLABUS`, `STUDY_MATERIAL`, `REFERENCE_LINK`, `LECTURE_NOTES`, `LAB_MANUAL`, `VIDEO`, `QUESTION_BANK`. | `ResourceValidationEngine` | `422 Unprocessable` (`ACD_RESOURCE_VALIDATION_ERROR`) |
| **BR-03** | Binary content metadata must contain non-negative `fileSize`, supported `mimeType`, and valid 32-64 hex `checksum`. | `ResourceValidationEngine` | `422 Unprocessable` (`ACD_RESOURCE_VALIDATION_ERROR`) |
| **BR-04** | Published versions are strictly immutable. Modifying content requires generating a new monotonic version (`versionNo = currentVersion + 1`). | `ResourceVersionManager` | `409 Conflict` (`ACD_RESOURCE_VERSION_CONFLICT`) |
| **BR-05** | Optimistic concurrency check is mandatory: `expectedVersion` must match active version. | `ResourceVersionManager` | `409 Conflict` (`ACD_RESOURCE_VERSION_CONFLICT`) |
| **BR-06** | Historical version restoration creates a new version preserving audit history without deleting historical records. | `ResourceVersionManager` | `200 OK` |
| **BR-07** | Publishing requires authorized role (`ACADEMIC_ADMIN`, `FACULTY` owner, `DEPARTMENT_HEAD`) and validated immutable storage reference. | `ResourceDomainService` | `403 Forbidden` (`ACD_RESOURCE_PUBLISH_FORBIDDEN`) |
| **BR-08** | Publishing a `SYLLABUS` resource automatically emits dual events: `LearningResourcePublished` and `SyllabusPublished`. | `ResourceDomainService` | Guaranteed via Outbox |
| **BR-09** | Access Policy Engine enforces strict `DENY` precedence: explicit DENY rule overrides any matching ALLOW grants. | `ResourceAccessPolicyService` | `403 Forbidden` (`ACD_RESOURCE_POLICY_DENIED`) |
| **BR-10** | Access grants enforce time validity: grants with `validFrom` in the future or `validTo` in the past are inactive. | `ResourceAccessPolicyService` | Time Engine Evaluation |
| **BR-11** | Students can only discover and download resources in `PUBLISHED` state matching their academic scope. | `ResourceAccessPolicyService` | Query Filter & Policy Guard |
| **BR-12** | Downloads return signed, short-lived (300 seconds) pre-authenticated URLs without exposing permanent credentials. | `ObjectStorageAdapter` | `200 OK` |
| **BR-13** | Idempotency keys (`Idempotency-Key`) protect mutations. Same key with different hash produces conflict. | `ResourceDomainService` | `409 Conflict` (`ACD_RESOURCE_IDEMPOTENCY_CONFLICT`) |
| **BR-14** | All lifecycle state transitions, access grant modifications, and policy denials are immutably audited with correlation IDs. | `AuditAdapter` & `StructuredLogEntry` | System Invariant |

---

## 5. Resource Lifecycle State Machine

```
      +-------------+
      |    DRAFT    |<-----------+ (Initial creation, editable metadata)
      +------+------+            |
             |                   |
             v                   |
      +-------------+            |
      |   REVIEW    |            |
      +------+------+            |
             |                   |
             v                   |
      +-------------+            |
      |  APPROVED   |            |
      +------+------+            |
             |                   |
             v                   |
      +-------------+            |
      |  PUBLISHED  |------------+ (New version / Restore creates new promoted version)
      +------+------+
             |
             v
      +-------------+
      |  ARCHIVED   | (Retired from catalog; preserved for governance retention)
      +-------------+
```

---

## 6. Access Policy Engine (ABAC & RBAC Specification)

The Access Policy Engine evaluates access in real time according to the following decision pipeline:
1. **Super Admin / Platform Admin Override**: Always granted access.
2. **Resource Ownership**: Faculty owner granted full administrative access.
3. **Department Head Scope**: Granted administrative governance over departmental resources.
4. **Lifecycle State Guard**: If caller is a `STUDENT` or unauthenticated reader, resource MUST be in `PUBLISHED` state. Drafts, reviews, and archived resources are hidden.
5. **DENY Evaluation (Highest Precedence)**: If ANY active grant matching the user, their role, or their department/batch/program scope has `effect: DENY`, access is immediately rejected (`403 ACD_RESOURCE_POLICY_DENIED`) and audited.
6. **Time Window Evaluation**: `validFrom <= current_time <= validTo`.
7. **ALLOW Evaluation**: An active matching grant with `effect: ALLOW` permits access.

---

## 7. Complete User Stories Traceability Matrix (ACD08-US-001 to ACD08-US-065)

| Story ID | Epic | User Story Summary | Acceptance Criteria | Implementation Component | API / Gateway Route | Verifying Test Case |
|---|---|---|---|---|---|---|
| **ACD08-US-001** | Resource Management | Register learning resource | Creates resource record with resourceId, version 1, and draft lifecycle status. | `ResourceDomainService` | `POST /api/v1/academics/resources` | `testCreateResourceSuccess` |
| **ACD08-US-002** | Resource Management | Register institutional resources | Gateway authenticates admin; persists under tenant/institution/campus. | `ResourceDomainService` | `POST /api/v1/academics/resources` | `testCreateResourceSuccess` |
| **ACD08-US-003** | Resource Management | Associate resource with subject/course/curriculum | Validates academic identifiers; rejects invalid references. | `SubjectReferenceClient`, `CurriculumReferenceClient` | `POST /api/v1/academics/resources` | `testCreateResourceSuccess` |
| **ACD08-US-004** | Resource Management | Classify by resource type | Only supported resource types accepted (e.g. SYLLABUS, STUDY_MATERIAL). | `ResourceValidationEngine` | `POST /api/v1/academics/resources` | `testInvalidResourceTypeFails` |
| **ACD08-US-005** | Resource Management | Add title, description, and tags | Metadata stored and tags indexed for search/catalog queries. | `ResourceDomainService`, `ResourceQueryService` | `POST /resources`, `GET /resources/search` | `testCreateResourceSuccess` |
| **ACD08-US-006** | Resource Management | Prevent duplicate resource code within tenant | Duplicate tenantId + resourceCode returns 409 Conflict. | `ResourceDomainService` | `POST /api/v1/academics/resources` | `testPreventDuplicateResourceCode` |
| **ACD08-US-007** | Content Integration | Link resource to binary content in shared storage | Valid immutable storageObjectRef registered; binaries not stored in Mongo. | `ObjectStorageAdapter` | `POST /api/v1/academics/resources` | `testCreateResourceSuccess` |
| **ACD08-US-008** | Content Integration | Verify object reference, MIME type, size, checksum | Invalid reference, unsupported MIME, negative size or bad hash rejected. | `ResourceValidationEngine` | `POST /api/v1/academics/resources` | `testInvalidChecksumFormatFails` |
| **ACD08-US-009** | Versioning | Create new version of resource | Creates new `resource_versions` record with monotonic versionNo. | `ResourceVersionManager` | `POST /api/v1/academics/resources/{id}/versions` | `testMonotonicVersionCreation` |
| **ACD08-US-010** | Versioning | View resource version details | Authorized users retrieve version metadata without exposing credentials. | `ResourceVersionManager` | `GET /api/v1/academics/versions/{id}` | `testMonotonicVersionCreation` |
| **ACD08-US-011** | Versioning | Restore previous version | Restores historical version as new promoted version preserving history. | `ResourceVersionManager` | `POST /resources/{id}/versions/{v}/restore` | `testRestoreHistoricalVersion` |
| **ACD08-US-012** | Versioning | Optimistic concurrency version checks | Stale expectedVersion returns 409 ACD_RESOURCE_VERSION_CONFLICT. | `ResourceVersionManager` | Version & Publish endpoints | `testOptimisticLockingOnVersionCreation` |
| **ACD08-US-013** | Access Control | Define view, download or manage grants | Principal, scope, permission, effect persisted as access grant. | `ResourceAccessPolicyService` | `POST /api/v1/academics/resources/{id}/access` | `testExplicitDenyRuleTakesPrecedence` |
| **ACD08-US-014** | Access Control | Department head manages departmental access | Gateway verifies department scope before permitting policy changes. | `ResourceAccessPolicyService` | `POST /resources/{id}/access` | `testExplicitDenyRuleTakesPrecedence` |
| **ACD08-US-015** | Access Control | Update existing access rule | Access rule updated within authorized scope and audited. | `ResourceAccessPolicyService` | `PUT /resources/{id}/access/{accessId}` | `testAccessGrantRevocation` |
| **ACD08-US-016** | Access Control | Revoke resource access | Grant transitions to REVOKED with audit; subsequent checks deny access. | `ResourceAccessPolicyService` | `DELETE /resources/{id}/access/{accessId}` | `testAccessGrantRevocation` |
| **ACD08-US-017** | Access Control | Students view authorized published resources | Unpublished resources or unpermitted scopes excluded for students. | `ResourceAccessPolicyService` | `GET /resources`, `GET /resources/{id}` | `testStudentCannotAccessDraftResource` |
| **ACD08-US-018** | Access Control | Students download permitted resources securely | Access checked before returning signed short-lived retrieval reference. | `ObjectStorageAdapter` | `GET /api/v1/academics/resources/{id}/download` | `testAuthorizedDownloadGeneratesSignedUrl` |
| **ACD08-US-019** | Access Control | Deny rules evaluated with highest precedence | Higher-precedence deny rule denies request and produces audit record. | `ResourceAccessPolicyService` | `GET /resources/{id}/download` | `testExplicitDenyRuleTakesPrecedence` |
| **ACD08-US-020** | Lifecycle | Resource begins in DRAFT state | Newly registered resource enters draft state and is not public. | `ResourceDomainService` | `POST /api/v1/academics/resources` | `testCreateResourceSuccess` |
| **ACD08-US-021** | Lifecycle | Departmental approval governance | Approver transitions resource through configured review/approval state. | `ResourceDomainService` | `POST /resources/{id}/publish` | `testPublishResourceAndSyllabusEvent` |
| **ACD08-US-022** | Lifecycle | Publish validated resource version | Requires authorization, expectedVersion, content reference check. | `ResourceDomainService` | `POST /api/v1/academics/resources/{id}/publish` | `testPublishResourceAndSyllabusEvent` |
| **ACD08-US-023** | Lifecycle | Archive published resource | Transitions to ARCHIVED, audited, emits `LearningResourceArchived`. | `ResourceDomainService` | `POST /api/v1/academics/resources/{id}/archive` | `testArchiveResource` |
| **ACD08-US-024** | Lifecycle | Prevent silent overwrite of published versions | Attempting to overwrite existing published version rejected. | `ResourceVersionManager` | Version APIs | `testMonotonicVersionCreation` |
| **ACD08-US-025** | Lifecycle | Explicit syllabus publication | Creates lifecycle record and emits `SyllabusPublished` reliably. | `ResourceDomainService`, `OutboxPublisher` | `POST /resources/{id}/publish` | `testPublishResourceAndSyllabusEvent` |
| **ACD08-US-026** | Search & Catalog | Search by subject, course, resource type | Supports filters, returns only permitted resources for caller. | `ResourceQueryService` | `GET /api/v1/academics/resources/search` | `testListAndSearchResourcesViaRest` |
| **ACD08-US-027** | Search & Catalog | Search by title, tags, publication status | Supports pagination, filtering, sorting on indexed fields. | `ResourceQueryService` | `GET /api/v1/academics/resources/search` | `testListAndSearchResourcesViaRest` |
| **ACD08-US-028** | Search & Catalog | Retrieve resource by ID | Returns metadata when authorized; 404 for unknown resource. | `ResourceDomainService` | `GET /api/v1/academics/resources/{id}` | `testNotFoundReturnsRfc7807ProblemDetails` |
| **ACD08-US-029** | Search & Catalog | Retrieve available tags | Returns available tags within caller's authorized tenant scope. | `ResourceQueryService` | `GET /api/v1/academics/resources/tags` | `testCreateResourceSuccess` |
| **ACD08-US-030** | Search & Catalog | Indexed resource queries performance target | Uses planned indexes to achieve search P95 < 2s. | `ResourceQueryService` | `GET /resources/search` | `testListAndSearchResourcesViaRest` |
| **ACD08-US-031** | Audit & Traceability | Record every lifecycle transition | Create, version, publish, syllabus, archive recorded in `resource_history`. | `AuditAdapter` | All mutation APIs | `testCreateResourceSuccess` |
| **ACD08-US-032** | Audit & Traceability | Inspect resource history | History is append-only and queryable without destructive edits. | `AuditAdapter` | `GET /resources/{id}/history` | `testCreateResourceSuccess` |
| **ACD08-US-033** | Audit & Traceability | Audit access failures & administrative changes | Policy denials, access failures, administrative changes audited. | `AuditAdapter`, `StructuredLogEntry` | All protected APIs | `testExplicitDenyRuleTakesPrecedence` |
| **ACD08-US-034** | Event Integration | Emit `LearningResourceCreated` event | Contains eventId, eventType, source, entityId, correlationId, data. | `OutboxPublisher` | Outbox transaction | `testCreateResourceSuccess` |
| **ACD08-US-035** | Event Integration | Emit `LearningResourcePublished` event | Emitted reliably upon publication with version identifier. | `OutboxPublisher` | Publish endpoint | `testPublishResourceAndSyllabusEvent` |
| **ACD08-US-036** | Event Integration | Emit `LearningResourceArchived` event | Emitted reliably upon resource archive. | `OutboxPublisher` | Archive endpoint | `testArchiveResource` |
| **ACD08-US-037** | Event Integration | Emit `SyllabusPublished` event | Dual event identifying authoritative syllabus for LMS/Search/Analytics. | `OutboxPublisher` | Publish endpoint | `testPublishResourceAndSyllabusEvent` |
| **ACD08-US-038** | Event Integration | Consume `SubjectCreated` & `CurriculumPublished` | Consumed events processed idempotently for catalog validation. | `ResourceDomainService` | `POST /resources/events` | `testCreateResourceSuccess` |
| **ACD08-US-039** | Reliability | Transactional outbox records | Lifecycle state and outbox events committed atomically. | `OutboxPublisher` | All mutation APIs | `testCreateResourceSuccess` |
| **ACD08-US-040** | Reliability | Idempotency key protection | Same tenantId + idempotencyKey returns cached response. | `ResourceDomainService` | `POST /resources` | `testIdempotencyOnResourceCreation` |
| **ACD08-US-041** | Reliability | Dead letter store for exhausted event retries | Exhausted retries moved to `dead_letter_events` with payload & reason. | `OutboxPublisher` | Outbox dispatcher | `testDeadLetterReplay` |
| **ACD08-US-042** | Reliability | Retry transient storage and broker failures | Bounded exponential backoff; validation/auth errors not retried. | `OutboxPublisher` | Outbox dispatcher | `testDeadLetterReplay` |
| **ACD08-US-043** | Reporting & Export | Export authorized resource metadata | Asynchronous export respects tenant/scope and returns job reference. | `ResourceQueryService` | `GET /resources/export` | `testCreateResourceSuccess` |
| **ACD08-US-044** | Analytics | Track resource usage demand | Increments view/download metrics without mutating lifecycle state. | `ResourceQueryService` | `GET /resources/{id}/usage` | `testAuthorizedDownloadGeneratesSignedUrl` |
| **ACD08-US-045** | Security & Compliance | Tenant and campus isolation | Cross-tenant access denied and audited across all endpoints. | `ResourceDomainService` | All endpoints | `testCreateResourceSuccess` |
| **ACD08-US-046** | Security & Compliance | Direct database sharing prohibited | Downstream services consume REST APIs / events, not database directly. | Architecture Boundary | Architectural Invariant | Specification Review |
| **ACD08-US-047** | Observability | Prometheus metrics & distributed tracing | Exposes registrations, publications, denials, latency, outbox lag. | `MetricsCollector` | `GET /metrics` | `testMetricsEndpoint` |
| **ACD08-US-048** | Backup & Recovery | DR metadata-to-object consistency tests | Verifies metadata references point to reachable storage objects. | `ResourceDomainService` | DR test operation | `testBulkImportAndDisasterRecovery` |
| **ACD08-US-049** | Registration Validation | Validate faculty ownership within academic scope | Validates tenant, institution, department context before registration. | `ResourceDomainService` | `POST /resources` | `testCreateResourceSuccess` |
| **ACD08-US-050** | Registration Validation | Deterministic metadata validation | Rejects unsupported resource type, MIME type, file size, or checksum. | `ResourceValidationEngine` | `POST /resources` | `testInvalidChecksumFormatFails` |
| **ACD08-US-051** | Storage Integration | Short-lived signed retrieval references | Generates pre-signed temporary URLs without exposing credentials. | `ObjectStorageAdapter` | `GET /resources/{id}/download` | `testAuthorizedDownloadGeneratesSignedUrl` |
| **ACD08-US-052** | Access Control | Time windows on access grants | validFrom and validTo enforced; expired grants treated as inactive. | `ResourceAccessPolicyService` | Access policy APIs | `testExplicitDenyRuleTakesPrecedence` |
| **ACD08-US-053** | Access Control | Conflict detection & DENY precedence | Conflicting grants resolved with strict DENY precedence and audited. | `ResourceAccessPolicyService` | Access policy APIs | `testExplicitDenyRuleTakesPrecedence` |
| **ACD08-US-054** | Access Control | Policy version tracking | Every access mutation records monotonic policyVersion. | `ResourceAccessPolicyService` | Access policy APIs | `testExplicitDenyRuleTakesPrecedence` |
| **ACD08-US-055** | Access Control | Scope resources to academic batch or program | Users outside configured batch/program cannot retrieve content. | `ResourceAccessPolicyService` | Access policy APIs | `testStudentCannotAccessDraftResource` |
| **ACD08-US-056** | Resource Integrity | Detect duplicate content using checksum | Detects matching contentHash within tenant scope and surfaces warning. | `ResourceVersionManager` | Version APIs | `testMonotonicVersionCreation` |
| **ACD08-US-057** | Search & Catalog | Lifecycle changes synchronized with search | Lifecycle events supply search indexing service without direct DB access. | `OutboxPublisher` | Event Bus | `testPublishResourceAndSyllabusEvent` |
| **ACD08-US-058** | Bulk Operations | Bulk import resource metadata | Validates records, isolates failures, preserves idempotency and audit. | `ResourceDomainService` | `POST /resources/import` | `testBulkImportAndDisasterRecovery` |
| **ACD08-US-059** | Bulk Operations | Trackable export jobs | Asynchronous export job tracking and completion metadata retrieval. | `ResourceQueryService` | `GET /resources/export` | `testCreateResourceSuccess` |
| **ACD08-US-060** | Event Reliability | Deduplicate consumed events by eventId | Previously processed eventId recognized and ignored idempotently. | `ResourceDomainService` | Event Consumer | `testIdempotencyOnResourceCreation` |
| **ACD08-US-061** | Event Reliability | Replay dead letter events | Authorized operator replays DLQ events without bypassing idempotency. | `OutboxPublisher` | `POST /resources/dlq/{id}/replay` | `testDeadLetterReplay` |
| **ACD08-US-062** | Retention & Governance | Retention rules applied across lifecycle | Published syllabus/history retained; drafts follow operational retention. | `ResourceDomainService` | Governance operation | Specification Review |
| **ACD08-US-063** | Intelligence & Analytics | Stale resource & demand insights | Surfaces stale resources, syllabus coverage, and type distribution. | `ResourceQueryService` | `GET /resources/analytics` | `testBulkImportAndDisasterRecovery` |
| **ACD08-US-064** | Academic Integration | Validate context against academic calendar | Validates referenced academic period against ACD-07 calendar service. | `AcademicCalendarReferenceClient` | `POST /resources` | `testCreateResourceSuccess` |
| **ACD08-US-065** | Disaster Recovery | DR tests verify metadata-to-object consistency | Verifies object references match storage provider without broken links. | `ResourceDomainService` | DR test operation | `testBulkImportAndDisasterRecovery` |

---

## 8. Canonical Domain Event Schemas

All domain events emitted by ACD-08 follow the standard CampXSync event envelope:

```json
{
  "eventId": "EVT-C9A38FC2-D",
  "eventType": "LearningResourcePublished",
  "source": "ACD-08",
  "occurredAt": "2026-09-29T10:45:00Z",
  "entityType": "LearningResource",
  "entityId": "RES-C0FABCCD-B",
  "version": "1.0",
  "correlationId": "TRACE-GW-RES-001",
  "data": {
    "resourceId": "RES-C0FABCCD-B",
    "resourceCode": "RES-CS201-SYL",
    "versionNo": 1,
    "status": "PUBLISHED",
    "publishedAt": "2026-09-29T10:45:00Z"
  }
}
```

### Syllabus Publication Event (`SyllabusPublished`):
```json
{
  "eventId": "EVT-2F09CF1F-E",
  "eventType": "SyllabusPublished",
  "source": "ACD-08",
  "occurredAt": "2026-09-29T10:45:00Z",
  "entityType": "LearningResource",
  "entityId": "RES-C0FABCCD-B",
  "version": "1.0",
  "correlationId": "TRACE-GW-RES-001",
  "data": {
    "resourceId": "RES-C0FABCCD-B",
    "resourceCode": "RES-CS201-SYL",
    "versionNo": 1,
    "curriculumId": "CUR_CSE_2026",
    "subjectId": "SUB_CS201"
  }
}
```

---

## 9. Error Taxonomy & RFC 7807 Mapping

| Error Code | HTTP Status | Problem Details Type URI | Trigger Rationale |
|---|---|---|---|
| `ACD_RESOURCE_NOT_FOUND` | 404 | `https://api.campx.internal/errors/acd_resource_not_found` | Resource, version, or access grant ID does not exist in tenant. |
| `ACD_RESOURCE_DUPLICATE` | 409 | `https://api.campx.internal/errors/acd_resource_duplicate` | Conflict on tenant-scoped business resourceCode. |
| `ACD_RESOURCE_VERSION_CONFLICT` | 409 | `https://api.campx.internal/errors/acd_resource_version_conflict` | Optimistic concurrency mismatch or immutable version overwrite. |
| `ACD_RESOURCE_IDEMPOTENCY_CONFLICT` | 409 | `https://api.campx.internal/errors/acd_resource_idempotency_conflict` | Idempotency-Key reused with different request payload hash. |
| `ACD_RESOURCE_VALIDATION_ERROR` | 422 | `https://api.campx.internal/errors/acd_resource_validation_error` | Invalid resourceType, bad checksum, negative file size, or date window. |
| `ACD_RESOURCE_POLICY_DENIED` | 403 | `https://api.campx.internal/errors/acd_resource_policy_denied` | Caller failed ABAC/RBAC rules or matched explicit DENY grant. |
| `ACD_RESOURCE_PUBLISH_FORBIDDEN` | 403 | `https://api.campx.internal/errors/acd_resource_publish_forbidden` | Unauthorized actor attempted publication. |
| `ACD_RESOURCE_UNAUTHORIZED` | 401 | `https://api.campx.internal/errors/acd_resource_unauthorized` | Missing, expired, or invalid API key / authentication token. |
| `ACD_RESOURCE_OBJECT_UNAVAILABLE` | 503 | `https://api.campx.internal/errors/acd_resource_object_unavailable` | Shared object storage unreachable or offline. |
| `ACD_RESOURCE_RATE_LIMIT_EXCEEDED` | 429 | `https://api.campx.internal/errors/acd_resource_rate_limit_exceeded` | Token bucket rate limit exceeded for caller. |
| `ACD_RESOURCE_INTERNAL_ERROR` | 500 | `https://api.campx.internal/errors/acd_resource_internal_error` | Unexpected unhandled server exception. |
