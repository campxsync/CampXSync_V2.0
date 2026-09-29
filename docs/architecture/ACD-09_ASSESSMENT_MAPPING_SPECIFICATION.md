# ACD-09 Assessment Mapping Service - Enterprise Technical Specification & User Story Blueprint

> **Module ID**: ACD-09  
> **Service Name**: Assessment Mapping Service  
> **Tier**: Academic Management Tier  
> **Authoritative Database**: `assessment_mapping_db` (MongoDB)  
> **Ingress Port**: `8091` (Service Direct) / `8080` (API Gateway Unified Ingress)  
> **Status**: Production Ready  
> **Version**: 2.0.0  
> **Traceability Source**: `ACD-09_assessment_mapping_user_stories.csv` (ACD09-US-001 through ACD09-US-070)

---

## 1. Executive Summary & Purpose

The **ACD-09 Assessment Mapping Service** is the authoritative master for academic assessment structure definitions, component weightage reconciliation, course/program outcome mapping (CO/PO/LO/PSO), optimistic concurrency versioning, and lifecycle governance across the CampXSync College ERP platform.

It is responsible for:
- Defining authoritative assessment structures per subject, course, curriculum, academic year, and term.
- Managing assessment components (tests, quizzes, assignments, midterms, end-term exams, practicals, projects, vivas) with sequence ordering, maximum marks, passing marks, and percentage weightages.
- Reconciling component weightage sums to ensure deterministic mathematical equality with configured assessment totals (100%).
- Mapping components and structures to Course Outcomes (CO), Program Outcomes (PO), and Learning Outcomes (LO) with mapping levels (LOW, MEDIUM, HIGH, DIRECT, INDIRECT) and weights for NBA/NAAC accreditation attainment.
- Governing multi-stage approval lifecycles (`DRAFT` $\to$ `REVIEW` $\to$ `APPROVED` $\to$ `PUBLISHED` $\to$ `RETIRED`).
- Enforcing single effective published versions per academic context and keeping published definitions strictly immutable.
- Emitting canonical transactional outbox events (`AssessmentStructureCreated`, `AssessmentMappingUpdated`, `AssessmentMappingPublished`, `AssessmentStructureRetired`) to feed downstream Examination (EXM), Learning Management (LMS), and Analytics (ACD-10) systems without direct database sharing.

### Fundamental Architectural Tenet: Separation of Definition vs Examination Execution
In accordance with Enterprise Architecture Directives:
- **ACD-09 owns assessment structure metadata, component schemas, outcome matrices, and weightage policies in MongoDB (`assessment_mapping_db`).**
- **EXM (Examinations Service) owns exam schedule execution, room allocations, student seating, question paper generation, and student grade/marks entries.**
- EXM and LMS read published assessment structures via authorized REST APIs or asynchronous domain events; they **cannot directly mutate or share MongoDB collections with ACD-09**.

---

## 2. Bounded Context & Architecture Overview

```
                            +-------------------------------------------+
                            |          External Clients / Portals       |
                            |   (Academic Admins, Faculty, HODs, EXM)   |
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
|                         ACD-09 Assessment Mapping Service (Port 8091)                              |
|                                                                                                    |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |     AssessmentController       |  |   AssessmentValidationEngine   |  | RateLimiter & Metrics|  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |     AssessmentDomainService    |  |   WeightageValidationEngine    |  | OutboxPublisher      |  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |     AssessmentVersionManager   |  |      OutcomeMappingEngine      |  | AuditAdapter         |  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
|  |     AssessmentQueryService     |  |   Reference Clients (ACD01..03)|  | DLQ & Replay Manager |  |
|  +--------------------------------+  +--------------------------------+  +----------------------+  |
+----------------------------------------------------------------------------------------------------+
                                                  |
                                                  v
+----------------------------------------------------------------------------------------------------+
|                               Authoritative MongoDB Collections                                    |
|  * assessment_structures  * assessment_components  * outcome_mappings  * assessment_history        |
|  * outbox_events          * idempotency_records    * dead_letter_events                           |
+----------------------------------------------------------------------------------------------------+
```

---

## 3. Complete 70 User Story Traceability Matrix

| Story ID | Epic | Actor | User Story Summary | Implemented Route & Method | Component / Rule Engine | MongoDB Collection | Priority |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ACD09-US-001** | Assessment Structure | Academic Admin | Create assessment structure for subject | `POST /api/v1/academics/assessments` | `AssessmentController`, `AssessmentDomainService` | `assessment_structures`, `assessment_history` | High |
| **ACD09-US-002** | Assessment Structure | Faculty | Create assessment structure within subject scope | `POST /api/v1/academics/assessments` | `AssessmentDomainService`, ABAC validation | `assessment_structures`, `assessment_history` | High |
| **ACD09-US-003** | Assessment Structure | System | Prevent duplicate assessment identity | `POST /api/v1/academics/assessments` | Unique index tenant+code+year+subject | `assessment_structures` | High |
| **ACD09-US-004** | Assessment Structure | Academic Admin | Configure assessment types (Internal, External, etc.) | `POST /api/v1/academics/assessments` | `AssessmentValidationEngine` (enum check) | `assessment_structures` | Medium |
| **ACD09-US-005** | Assessment Structure | Academic Admin | Associate subject, course, curriculum, term, year | `POST /api/v1/academics/assessments` | `Subject/Course/CurriculumReferenceClient` | `assessment_structures` | High |
| **ACD09-US-006** | Assessment Structure | Faculty | Update a draft assessment structure | `PUT /api/v1/academics/assessments/{id}` | `AssessmentDomainService`, `VersionManager` | `assessment_structures`, `assessment_history` | High |
| **ACD09-US-007** | Components | Faculty | Add tests, assignments, exams, practicals | `POST /api/v1/academics/assessments/{id}/components` | `AssessmentDomainService`, `ValidationEngine` | `assessment_components`, `assessment_history` | High |
| **ACD09-US-008** | Components | Faculty | Configure maximum marks for each component | `POST/PUT components API` | `AssessmentValidationEngine` (maxMarks > 0) | `assessment_components`, `assessment_history` | High |
| **ACD09-US-009** | Components | Faculty | Configure component weightage | `POST/PUT components API` | `WeightageValidationEngine` | `assessment_components` | High |
| **ACD09-US-010** | Components | Faculty | Configure passing marks (passing <= max) | `POST/PUT components API` | `AssessmentValidationEngine` (passing <= max) | `assessment_components` | High |
| **ACD09-US-011** | Components | Faculty | Configure component sequence/order | `POST/PUT components API` | Unique sequenceNo per assessment | `assessment_components` | Medium |
| **ACD09-US-012** | Components | Faculty | Configure rubric, evaluation method, attempt policy | `POST/PUT components API` | `AssessmentValidationEngine` | `assessment_components` | High |
| **ACD09-US-013** | Components | Academic Admin | Retrieve components for an assessment | `GET /api/v1/academics/assessments/{id}/components` | `AssessmentQueryService` | `assessment_components` | Medium |
| **ACD09-US-014** | Components | Faculty | Update a draft component with version check | `PUT .../components/{componentId}` | `AssessmentDomainService`, `VersionManager` | `assessment_components`, `assessment_history` | High |
| **ACD09-US-015** | Components | Academic Admin | Delete an invalid draft component | `DELETE .../components/{componentId}` | `AssessmentDomainService` (cleans mappings) | `assessment_components`, `outcome_mappings` | Medium |
| **ACD09-US-016** | Weightage | System | Reconcile component weightages with total | `GET .../assessments/{id}/validate` | `WeightageValidationEngine` | `assessment_structures`, `assessment_components` | High |
| **ACD09-US-017** | Weightage | System | Reconcile maximum-mark totals | `GET .../assessments/{id}/validate` | `WeightageValidationEngine` | `assessment_structures`, `assessment_components` | High |
| **ACD09-US-018** | Weightage | System | Validate passing-mark constraints | `GET .../assessments/{id}/validate` | `AssessmentValidationEngine` | `assessment_components` | High |
| **ACD09-US-019** | Validation | Faculty | Pre-submission assessment validation | `GET .../assessments/{id}/validate` | `WeightageValidationEngine`, `OutcomeEngine` | `assessment_structures`, `assessment_components` | High |
| **ACD09-US-020** | Outcome Mapping | Faculty | Map component to CO, PO, or LO | `POST .../assessments/{id}/mappings` | `OutcomeMappingEngine` | `outcome_mappings`, `assessment_history` | High |
| **ACD09-US-021** | Outcome Mapping | Academic Admin | Support component-level and assessment-level mappings | `POST .../assessments/{id}/mappings` | `OutcomeMappingEngine` (nullable componentId) | `outcome_mappings` | High |
| **ACD09-US-022** | Outcome Mapping | Faculty | Assign mapping weight to outcome relationship | `POST .../assessments/{id}/mappings` | `OutcomeMappingEngine` (0.0 < weight <= 100.0) | `outcome_mappings` | High |
| **ACD09-US-023** | Outcome Mapping | Academic Admin | Configure attainment policy reference | `POST .../assessments/{id}/mappings` | `OutcomeMappingEngine` | `outcome_mappings` | Medium |
| **ACD09-US-024** | Outcome Mapping | System | Prevent duplicate outcome mappings | `POST .../assessments/{id}/mappings` | Unique assessment+comp+outcome combo | `outcome_mappings` | High |
| **ACD09-US-025** | Outcome Mapping | Academic Admin | Retrieve assessment outcome mappings | `GET .../assessments/{id}/mappings` | `AssessmentQueryService` | `outcome_mappings` | Medium |
| **ACD09-US-026** | Subject Mapping | Faculty | Retrieve effective assessment map for subject | `GET .../assessments/subject/{subjectId}` | `AssessmentQueryService` | `assessment_structures`, `components`, `mappings` | High |
| **ACD09-US-027** | EXM Context | Exam Coord | Retrieve approved assessment context for EXM | `GET .../assessments/subject/{subjectId}` | `AssessmentQueryService` (read-only for EXM) | `assessment_structures`, `components`, `mappings` | High |
| **ACD09-US-028** | Lifecycle | Academic Admin | Manage Draft->Review->Approved->Published->Retired | Lifecycle APIs (`submit-review`, `retire`) | `AssessmentDomainService` | `assessment_structures`, `assessment_history` | High |
| **ACD09-US-029** | Approval | Dept Head | Review and approve departmental assessment structures | `POST .../assessments/{id}/approve` | `AssessmentDomainService` (requires DEPT_HEAD) | `assessment_structures`, `assessment_history` | High |
| **ACD09-US-030** | Publication | Academic Admin | Publish validated assessment mapping | `POST .../assessments/{id}/publish` | `AssessmentDomainService`, `WeightageEngine` | `assessment_structures`, `outbox_events` | High |
| **ACD09-US-031** | Versioning | Academic Admin | Keep published definitions immutable | All mutation APIs | `AssessmentVersionManager` (blocks publish edit)| `assessment_structures` | High |
| **ACD09-US-032** | Versioning | System | Prevent concurrent edits with optimistic locking | `PUT`, `POST publish` | `AssessmentVersionManager` (expectedVersion) | `assessment_structures` | High |
| **ACD09-US-033** | Versioning | Academic Admin | View assessment version and audit history | `GET .../assessments/{id}/history` | `AssessmentQueryService`, `AuditAdapter` | `assessment_history` | Medium |
| **ACD09-US-034** | Audit | Auditor | Audit component, mapping, lifecycle changes | All mutation operations | `AuditAdapter` | `assessment_history` | High |
| **ACD09-US-035** | Audit | Auditor | Audit authorization failures | Controller interceptor | `AuditAdapter.recordAuthFailure` | `assessment_history` | High |
| **ACD09-US-036** | Events | EXM/Analytics | Emit AssessmentStructureCreated events | Structure creation | `OutboxPublisher` | `outbox_events` | High |
| **ACD09-US-037** | Events | EXM/Analytics | Emit AssessmentMappingUpdated events | Outcome mapping mutation | `OutboxPublisher` | `outbox_events` | High |
| **ACD09-US-038** | Events | EXM/Analytics | Emit AssessmentMappingPublished events | Structure publication | `OutboxPublisher` | `outbox_events` | High |
| **ACD09-US-039** | Event Consumption | System | Consume SubjectPublished events | `POST .../events/consume` | `SubjectReferenceClient` | `assessment_structures` | Medium |
| **ACD09-US-040** | Event Consumption | System | Consume CurriculumPublished events | `POST .../events/consume` | Flags `reviewNeeded=true` on affected items | `assessment_structures`, `assessment_history` | High |
| **ACD09-US-041** | Event Consumption | System | Consume CourseUpdated events | `POST .../events/consume` | `CourseReferenceClient` | `assessment_structures`, `assessment_history` | Medium |
| **ACD09-US-042** | Reliability | System | Commit business changes & outbox records atomically | Transaction boundary | `OutboxPublisher` | `outbox_events`, `assessment_structures` | High |
| **ACD09-US-043** | Reliability | System | Protect retryable writes with Idempotency-Key | `Idempotency-Key` header | `idempotency_records` cache | `idempotency_records` | High |
| **ACD09-US-044** | Reliability | Operator | Retry transient broker failures with backoff | Internal dispatch | `OutboxPublisher` | `outbox_events` | High |
| **ACD09-US-045** | Reliability | Operator | Store poison events in dead-letter collection | DLQ failure boundary | `OutboxPublisher.handlePoisonEvent` | `dead_letter_events` | High |
| **ACD09-US-046** | Reliability | Operator | Replay remediated dead-letter events safely | `POST .../admin/dlq/replay` | `OutboxPublisher.replayDeadLetterEvent` | `dead_letter_events`, `outbox_events` | High |
| **ACD09-US-047** | Security | Academic Admin | Enforce tenant, department, subject scope | Request headers | `AssessmentDomainService.checkRbac` | All collections | High |
| **ACD09-US-048** | Security | Exam Coord | Provide read-only approved context to EXM | `GET .../subject/{subjectId}` | `AssessmentQueryService` | `assessment_structures`, `components` | High |
| **ACD09-US-049** | Architecture | System | Prevent direct database sharing | Architectural boundary | REST APIs & canonical outbox events | All 7 collections | High |
| **ACD09-US-050** | Analytics | Academic Admin | Coverage and completeness analytics | `GET .../analytics/coverage` | `AssessmentQueryService.getCoverageAnalytics` | `structures`, `components`, `mappings` | Medium |
| **ACD09-US-051** | Analytics | Academic Admin | Assessment-load and balance insights | `GET .../analytics/balance` | `AssessmentQueryService.getBalanceInsights` | `structures`, `components` | Medium |
| **ACD09-US-052** | Reporting | Academic Admin | Export assessment structures (JSON / CSV) | `GET .../export?format=csv` | `AssessmentQueryService.exportAssessments` | `structures`, `components`, `mappings` | Medium |
| **ACD09-US-053** | Query | Academic Admin | Query assessments with filtering/pagination | `GET /api/v1/academics/assessments` | `AssessmentQueryService.queryAssessments` | `assessment_structures` | Medium |
| **ACD09-US-054** | Effective Version | System | Single effective published version per context | `POST .../publish` | `AssessmentVersionManager` | `assessment_structures` | High |
| **ACD09-US-055** | Lifecycle | Academic Admin | Retire obsolete assessment mapping | `POST .../retire` | `AssessmentDomainService` | `assessment_structures`, `outbox_events` | Medium |
| **ACD09-US-056** | Change Impact | Academic Admin | Identify assessments affected by curriculum | Event consumer | `CurriculumReferenceClient` | `assessment_structures` | High |
| **ACD09-US-057** | Change Impact | Faculty | Review assessments affected by course/curr | Query filter & reviewNotes | `AssessmentQueryService` | `assessment_structures` | Medium |
| **ACD09-US-058** | Data Integrity | System | Validate subject, course, curriculum refs | Creation & update validation | Reference clients (ACD-01, 02, 03) | `assessment_structures` | High |
| **ACD09-US-059** | Performance | Operator | P95 latency targets (<300ms read, <2s complex) | Query indexing & caching | `MetricsCollector` | `assessment_structures` | Medium |
| **ACD09-US-060** | Observability | Operator | Monitor creations, validations, outbox, DLQ | `GET /metrics` | `MetricsCollector` | In-memory atomic telemetry | Medium |
| **ACD09-US-061** | Backup & DR | Operator | Encrypted backup drills & version integrity | Operational DR model | Invariant validation on restore | All collections | High |
| **ACD09-US-062** | Retention | Academic Admin | Apply institutional retention to history | Governance lifecycle | `AssessmentHistory`, `OutboxEvents` | Audit collections | Medium |
| **ACD09-US-063** | RBAC | Academic Admin | Full administration within authorized scope | Controller RBAC check | Role enforcement (`ACADEMIC_ADMIN`) | All collections | High |
| **ACD09-US-064** | RBAC | Dept Head | Departmental review and approval | `POST .../approve` | Role enforcement (`DEPT_HEAD`) | `assessment_structures` | High |
| **ACD09-US-065** | API Contract | System | Standardized envelopes, errors, pagination | RFC 7807 problem details | `ErrorResponse`, `AssessmentController` | Error format | Medium |
| **ACD09-US-066** | Security | System | Data protection, encryption, isolation | Multi-tenant isolation | Tenant ID scoping & sanitization | All collections | High |
| **ACD09-US-067** | LMS Integration | LMS | LMS receives approved context read-only | REST API & Outbox events | `AssessmentQueryService` | `assessment_structures` | Medium |
| **ACD09-US-068** | Validation | Academic Admin | Detect missing component-to-outcome mappings | Validation engine warnings | `OutcomeMappingEngine` | `outcome_mappings` | High |
| **ACD09-US-069** | Templates | Academic Admin | Reuse assessment templates to create drafts | `POST .../templates/{id}/clone` | `AssessmentDomainService.cloneFromTemplate` | `structures`, `components` | Medium |
| **ACD09-US-070** | Multi-program | Academic Admin | Support multi-program assessment contexts | `programIds` list & ABAC checks | `AssessmentStructure.programIds` | `assessment_structures` | Medium |

---

## 4. MongoDB Collections Schema Definition

ACD-09 manages 7 distinct collections in the `assessment_mapping_db` database:

### 1. `assessment_structures` (Aggregate Root)
```json
{
  "_id": "UUID string",
  "tenantId": "string (indexed)",
  "institutionId": "string",
  "departmentId": "string",
  "subjectId": "string (indexed)",
  "courseId": "string (indexed)",
  "curriculumId": "string",
  "academicYear": "string (e.g., '2026-2027')",
  "termId": "string (e.g., 'TERM-1')",
  "assessmentCode": "string (unique per tenant+subject+academicYear)",
  "assessmentName": "string",
  "assessmentType": "INTERNAL | EXTERNAL | PRACTICAL | CONTINUOUS | PROJECT | VIVA | LAB",
  "totalMarks": 100.0,
  "totalWeightage": 100.0,
  "status": "DRAFT | REVIEW | APPROVED | PUBLISHED | RETIRED | REJECTED | CANCELLED",
  "currentVersion": 1,
  "effectiveVersion": 0,
  "approvalRef": "string",
  "reviewNotes": "string",
  "reviewNeeded": false,
  "templateRef": "UUID string (optional)",
  "programIds": ["string"],
  "createdAt": "ISO-8601 string",
  "updatedAt": "ISO-8601 string",
  "publishedAt": "ISO-8601 string (null if not published)",
  "retiredAt": "ISO-8601 string (null if not retired)",
  "createdBy": "string",
  "updatedBy": "string"
}
```
**Indexes**:
- `{ tenantId: 1, assessmentCode: 1, subjectId: 1, academicYear: 1 }` (UNIQUE)
- `{ tenantId: 1, subjectId: 1, status: 1, academicYear: 1, termId: 1 }`
- `{ tenantId: 1, courseId: 1, academicYear: 1 }`

### 2. `assessment_components`
```json
{
  "_id": "UUID string",
  "assessmentId": "UUID string (indexed)",
  "tenantId": "string (indexed)",
  "componentCode": "string (unique per assessment)",
  "componentName": "string",
  "componentType": "TEST | ASSIGNMENT | PRACTICAL | MIDTERM | ENDTERM | PROJECT | QUIZ | VIVA | LAB_WORK",
  "sequenceNo": 1,
  "maxMarks": 40.0,
  "passingMarks": 16.0,
  "weightage": 40.0,
  "evaluationMethod": "MANUAL | RUBRIC | AUTOMATED | HYBRID",
  "rubricRef": "string (optional)",
  "attemptPolicy": "SINGLE | MULTIPLE | BEST_OF | AVERAGE",
  "maxAttempts": 1,
  "version": 1,
  "createdAt": "ISO-8601 string",
  "updatedAt": "ISO-8601 string",
  "createdBy": "string",
  "updatedBy": "string"
}
```
**Indexes**:
- `{ assessmentId: 1, sequenceNo: 1 }` (UNIQUE)
- `{ assessmentId: 1, componentCode: 1 }` (UNIQUE)

### 3. `outcome_mappings`
```json
{
  "_id": "UUID string",
  "assessmentId": "UUID string (indexed)",
  "componentId": "UUID string (optional, empty for assessment-level)",
  "tenantId": "string (indexed)",
  "outcomeType": "CO | PO | LO | PSO",
  "outcomeCode": "string (e.g., 'CO1', 'PO2')",
  "mappingLevel": "LOW | MEDIUM | HIGH | DIRECT | INDIRECT",
  "weight": 3.0,
  "attainmentPolicyRef": "string (optional)",
  "createdAt": "ISO-8601 string",
  "updatedAt": "ISO-8601 string",
  "createdBy": "string",
  "updatedBy": "string"
}
```
**Indexes**:
- `{ assessmentId: 1, componentId: 1, outcomeType: 1, outcomeCode: 1 }` (UNIQUE)

### 4. `assessment_history` (Immutable Audit Trail)
```json
{
  "_id": "UUID string",
  "assessmentId": "UUID string (indexed)",
  "tenantId": "string (indexed)",
  "version": 1,
  "action": "CREATE | UPDATE | COMPONENT_ADD | COMPONENT_UPDATE | COMPONENT_DELETE | MAPPING_ADD | MAPPING_UPDATE | MAPPING_DELETE | SUBMIT_REVIEW | APPROVE | PUBLISH | RETIRE | EVENT_CONSUMED | CLONE | AUTH_FAILURE",
  "fromStatus": "string",
  "toStatus": "string",
  "changedBy": "string",
  "changedAt": "ISO-8601 string",
  "reason": "string",
  "approvalRef": "string",
  "correlationId": "string",
  "diffSummary": "string"
}
```
**Indexes**:
- `{ assessmentId: 1, version: -1, changedAt: -1 }`

### 5. `outbox_events` (Transactional Outbox)
```json
{
  "_id": "UUID string",
  "aggregateType": "AssessmentStructure",
  "aggregateId": "UUID string",
  "eventType": "AssessmentStructureCreated | AssessmentMappingUpdated | AssessmentMappingPublished | AssessmentStructureRetired",
  "payload": {},
  "status": "PENDING | SENT | FAILED",
  "attempts": 1,
  "correlationId": "string",
  "createdAt": "ISO-8601 string",
  "sentAt": "ISO-8601 string"
}
```

### 6. `idempotency_records`
```json
{
  "key": "string (primary key)",
  "tenantId": "string",
  "requestHash": "SHA-256 string",
  "statusCode": 201,
  "responseBody": "string",
  "createdAt": "ISO-8601 string",
  "expiresAt": "ISO-8601 string (TTL 24 hours)"
}
```

### 7. `dead_letter_events` (Poison Event Storage)
```json
{
  "_id": "UUID string",
  "eventId": "UUID string",
  "aggregateId": "UUID string",
  "eventType": "string",
  "payload": "string",
  "failureReason": "string",
  "attempts": 3,
  "replayCount": 0,
  "lastAttemptAt": "ISO-8601 string",
  "correlationId": "string",
  "status": "PENDING_REVIEW | REPLAYED | DISCARDED"
}
```

---

## 5. Weightage & Outcome Mapping Validation Engines

### Mathematical Weightage Reconciliation Rule
For any assessment structure $S$ with configured total weightage $W_{total}$ (typically $100\%$) and total marks $M_{total}$:
1. **Component Weightage Sum**:
   $$\sum_{i=1}^{n} w_i = W_{total} \pm 0.001$$
2. **Component Maximum Marks Sum**:
   $$\sum_{i=1}^{n} m_i = M_{total} \pm 0.001$$
3. **Passing Marks Invariant**:
   $$0 \le p_i \le m_i \quad \forall i \in [1, n]$$
If any condition fails, pre-validation returns deterministic diagnostics (`ACD_ASSESSMENT_VALIDATION_FAILED`), and publication is strictly blocked (`ACD_ASSESSMENT_PUBLISH_FORBIDDEN` / `ACD_ASSESSMENT_WEIGHTAGE_MISMATCH`).

### Outcome Mapping Matrix & NBA/NAAC Attainment
- Components map to Course Outcomes (CO1..CO6) and Program Outcomes (PO1..PO12).
- Mappings carry `mappingLevel` (`LOW`: 1, `MEDIUM`: 2, `HIGH`: 3) and custom weights.
- Uniqueness is enforced on `(assessmentId, componentId, outcomeType, outcomeCode)`.
- US-068 detects components lacking outcome mapping and warns before publication.

---

## 6. Lifecycle State Machine & Concurrency

```
  +-----------+  submitForReview()  +------------+    approve()    +--------------+
  |   DRAFT   | ------------------> |   REVIEW   | --------------> |   APPROVED   |
  +-----------+                     +------------+                 +--------------+
        ^                                                                  |
        | cloneFromTemplate()                                              | publish()
        |                                                                  v
  +-----------+                       retire()                     +--------------+
  | TEMPLATE  |                                                    |  PUBLISHED   |
  +-----------+                                                    +--------------+
                                                                           |
                                                                           v
                                                                   +--------------+
                                                                   |   RETIRED    |
                                                                   +--------------+
```

1. **DRAFT**: Modifiable by Faculty and Academic Admin. Components and mappings can be added, updated, or removed.
2. **REVIEW**: Read-only validation state. Submitted by Faculty; awaiting Department Head review.
3. **APPROVED**: Endorsed by Department Head (`DEPT_HEAD`) with mandatory `approvalRef`.
4. **PUBLISHED**: Authoritative version for EXM and LMS. Changes require creating a new version.
5. **RETIRED**: Obsolete assessment definition replaced by revised curriculum or new academic year.

---

## 7. API Gateway Routes & Endpoints

| Route Pattern | Target Microservice Endpoint | Auth Roles | Description |
| :--- | :--- | :--- | :--- |
| `POST /api/v1/academics/assessments` | `http://localhost:8091/api/v1/academics/assessments` | `ACADEMIC_ADMIN`, `FACULTY` | Create new assessment structure |
| `GET /api/v1/academics/assessments` | `http://localhost:8091/api/v1/academics/assessments` | `ACADEMIC_ADMIN`, `FACULTY`, `EXM` | Query assessments with filters |
| `GET /api/v1/academics/assessments/{id}` | `http://localhost:8091/api/v1/academics/assessments/{id}` | Any authenticated | Get assessment by ID |
| `PUT /api/v1/academics/assessments/{id}` | `http://localhost:8091/api/v1/academics/assessments/{id}` | `ACADEMIC_ADMIN`, `FACULTY` | Update draft assessment structure |
| `POST .../assessments/{id}/components` | `http://localhost:8091/.../components` | `ACADEMIC_ADMIN`, `FACULTY` | Add component to assessment |
| `GET .../assessments/{id}/components` | `http://localhost:8091/.../components` | Any authenticated | List components |
| `PUT .../components/{componentId}` | `http://localhost:8091/.../components/{cId}` | `ACADEMIC_ADMIN`, `FACULTY` | Update draft component |
| `DELETE .../components/{componentId}` | `http://localhost:8091/.../components/{cId}` | `ACADEMIC_ADMIN`, `FACULTY` | Delete draft component |
| `POST .../assessments/{id}/mappings` | `http://localhost:8091/.../mappings` | `ACADEMIC_ADMIN`, `FACULTY` | Add outcome mapping (CO/PO/LO) |
| `GET .../assessments/{id}/mappings` | `http://localhost:8091/.../mappings` | Any authenticated | Query outcome mappings |
| `DELETE .../mappings/{mappingId}` | `http://localhost:8091/.../mappings/{mId}` | `ACADEMIC_ADMIN`, `FACULTY` | Delete outcome mapping |
| `GET .../assessments/{id}/validate` | `http://localhost:8091/.../validate` | `ACADEMIC_ADMIN`, `FACULTY` | Reconcile weightage & validate |
| `POST .../assessments/{id}/submit-review`| `http://localhost:8091/.../submit-review` | `ACADEMIC_ADMIN`, `FACULTY` | Submit DRAFT for review |
| `POST .../assessments/{id}/approve` | `http://localhost:8091/.../approve` | `DEPT_HEAD`, `ACADEMIC_ADMIN` | Approve assessment structure |
| `POST .../assessments/{id}/publish` | `http://localhost:8091/.../publish` | `ACADEMIC_ADMIN`, `DEPT_HEAD` | Publish effective structure |
| `POST .../assessments/{id}/retire` | `http://localhost:8091/.../retire` | `ACADEMIC_ADMIN`, `DEPT_HEAD` | Retire obsolete definition |
| `GET .../assessments/{id}/history` | `http://localhost:8091/.../history` | `ACADEMIC_ADMIN`, `AUDITOR` | View audit/version history |
| `GET .../subject/{subjectId}` | `http://localhost:8091/.../subject/{sId}` | Any authenticated, `EXM` | Get effective map for subject |
| `GET .../analytics/coverage` | `http://localhost:8091/.../analytics/coverage` | `ACADEMIC_ADMIN`, `DEPT_HEAD` | Assessment & outcome coverage |
| `GET .../analytics/balance` | `http://localhost:8091/.../analytics/balance` | `ACADEMIC_ADMIN`, `DEPT_HEAD` | Assessment load/balance insights |
| `GET .../export` | `http://localhost:8091/.../export` | `ACADEMIC_ADMIN` | Export structures (JSON / CSV) |
| `POST .../templates/{id}/clone` | `http://localhost:8091/.../clone` | `ACADEMIC_ADMIN`, `FACULTY` | Clone assessment template |
| `POST .../events/consume` | `http://localhost:8091/.../consume` | Platform internal | Ingest Subject/Curriculum events |
| `POST .../admin/dlq/replay` | `http://localhost:8091/.../dlq/replay` | `SYSTEM_OPERATOR`, `ADMIN` | Replay dead-letter event |
| `GET /api/v1/academics/assessments/metrics` | `http://localhost:8091/metrics` | Platform internal | Prometheus/JSON telemetry |

---

## 8. Standard Error Taxonomy (RFC 7807)

```json
{
  "type": "https://campx.com/errors/acd_assessment_weightage_mismatch",
  "title": "Assessment Request Error",
  "status": 400,
  "detail": "Total component weightage (70.0%) does not match required assessment total weightage (100.0%).",
  "instance": "/api/v1/academics/assessments/ASM-001/validate",
  "errorCode": "ACD_ASSESSMENT_WEIGHTAGE_MISMATCH",
  "code": "ACD_ASSESSMENT_WEIGHTAGE_MISMATCH",
  "timestamp": "2026-09-29T11:20:00.000Z",
  "invalidParams": []
}
```

| HTTP Status | Error Code | Trigger Condition |
| :--- | :--- | :--- |
| **400** | `ACD_ASSESSMENT_VALIDATION_FAILED` | Missing mandatory field, negative marks, passingMarks > maxMarks |
| **400** | `ACD_ASSESSMENT_WEIGHTAGE_MISMATCH` | Sum of component weightages != 100.0% or marks sum != totalMarks |
| **400** | `ACD_ASSESSMENT_OUTCOME_MAPPING_INVALID`| Invalid outcome type, negative weight, or duplicate mapping |
| **400** | `ACD_ASSESSMENT_PUBLISH_FORBIDDEN` | Publication attempted while not in APPROVED status or validation failed |
| **401** | `ACD_ASSESSMENT_UNAUTHORIZED` | Missing authentication headers or invalid caller identity |
| **403** | `ACD_ASSESSMENT_FORBIDDEN` | Caller lacks required RBAC role or department/tenant scope |
| **404** | `ACD_ASSESSMENT_NOT_FOUND` | Assessment structure, component, or mapping ID not found |
| **409** | `ACD_ASSESSMENT_DUPLICATE_IDENTITY` | Duplicate assessmentCode within tenant, subject, and academic year |
| **409** | `ACD_ASSESSMENT_VERSION_CONFLICT` | Stale expectedVersion supplied during concurrent update |
| **409** | `ACD_ASSESSMENT_IDEMPOTENCY_CONFLICT` | Idempotency-Key reused with differing request payload |
| **429** | `ACD_ASSESSMENT_RATE_LIMIT_EXCEEDED` | Request frequency exceeds 600 requests/minute per client |
| **500** | `ACD_ASSESSMENT_INTERNAL_ERROR` | Unexpected internal server failure |
