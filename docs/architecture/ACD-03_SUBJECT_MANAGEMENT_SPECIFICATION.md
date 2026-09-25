# ACD-03 Subject Management Service - Enterprise Technical Specification

> **Module ID**: ACD-03  
> **Service Name**: Subject Management Service  
> **Tier**: Academic Management Tier  
> **Status**: Production Ready  
> **Version**: 2.0.0  

---

## 1. Executive Summary & Purpose

The **ACD-03 Subject Management Service** is the authoritative master for reusable academic subject definitions across the CampXSync College ERP platform. It owns subject identity, institution-scoped code uniqueness, department ownership, credits, taxonomy classifications, prerequisite and co-requisite DAG graphs, monotonic versioning, and lifecycle governance.

Other academic modules—including **ACD-01 (Courses)**, **ACD-02 (Curriculum)**, **ACD-04 (Batch Allocation)**, **ACD-05 (Timetable)**, and **EXM (Examinations)**—reference subject identifiers and subscribe to domain events without directly mutating the ACD-03 database.

---

## 2. Bounded Context & Ownership Boundary

```
                     +---------------------------------------+
                     |         API Gateway (Port 8080)       |
                     +-------------------+-------------------+
                                         |
                                         v
                     +---------------------------------------+
                     |  ACD-03 Subject Service (Port 8085)   |
                     |  - Reusable Subject Definitions       |
                     |  - Taxonomy & Credit Boundaries       |
                     |  - Prerequisite & Co-requisite DAG    |
                     |  - Monotonic Versioning Engine        |
                     |  - Governed Lifecycle State Machine   |
                     |  - Transactional Outbox Publisher     |
                     +-------------------+-------------------+
                                         |
                     +-------------------+-------------------+
                     |                                       |
                     v                                       v
         +-----------------------+               +-----------------------+
         | MongoDB (CampXSync)   |               | Event Broker (Kafka)  |
         | - 9 Collections       |               | - SubjectCreated      |
         | - (ACD03__*)          |               | - SubjectUpdated      |
         | - Outbox & DLQ        |               | - SubjectPublished    |
         | - Versions & DAG      |               | - SubjectDeactivated  |
         +-----------------------+               +-----------+-----------+
                                                             |
                   +----------------+----------------+-------+--------+
                   |                |                |                |
                   v                v                v                v
               [ACD-01]         [ACD-02]         [ACD-05]          [EXM]
                Courses        Curriculum       Timetable          Exams
```

### ACD-03 Owns
- Authoritative subject aggregate: `subjectCode`, `name`, `credits`, `contactHours`, `subjectType`, `classification`.
- Monotonically-numbered immutable historical snapshots in `subject_versions`.
- Directed Acyclic Graph (DAG) for subject prerequisites and co-requisites.
- Extended metadata: delivery mode, assessment mode, regulatory codes.
- Transactional outbox events for all mutations.

### ACD-03 References (Does Not Own)
- `departmentId`: Owned by ADM-02 (College Admin Service).
- `courseId`: Owned by ACD-01 (Course Management Service).
- `curriculumId`: Owned by ACD-02 (Curriculum Management Service).

---

## 3. Key Business Rules (BR-01 to BR-14)

| Rule ID | Statement | Enforcement Layer | HTTP Error |
|---|---|---|---|
| **BR-01** | `subjectCode` must be unique within `tenantId + institutionId`. | Domain & Unique Index | `409 Conflict` |
| **BR-02** | Credits must be positive and within institutional range (0 - 20). | Validation Engine | `422 Unprocessable` |
| **BR-03** | Contact hours must be non-negative and compliant with type policy. | Validation Engine | `422 Unprocessable` |
| **BR-04** | Subject type must belong to configured taxonomy (`CORE`, `ELECTIVE`, `PRACTICAL`, `PROJECT`, `AUDIT`). | Taxonomy Validator | `422 Unprocessable` |
| **BR-05** | Cannot delete subject if used in curriculum or active batches. | Reference Guard | `409 Conflict` |
| **BR-06** | Published subject versions are immutable; revisions require new version. | Version Manager | `409 Conflict` |
| **BR-07** | A new published academic definition increments monotonic version number. | Version Manager | Business Logic |
| **BR-08** | Subject must be associated with an active, existing department. | Department Validator | `422 Unprocessable` |
| **BR-09** | Deactivation blocks new usage while preserving full historical context. | Lifecycle Engine | `200 OK` |
| **BR-10** | Version numbers are monotonic and never reused. | Version Manager | Integrity Guard |
| **BR-11** | Deprecated/deactivated subjects cannot be newly mapped to curriculum. | Consumer Guard | `422 Unprocessable` |
| **BR-12** | Optimistic concurrency locking is mandatory on mutable master updates. | Concurrency Filter | `409 Conflict` |
| **BR-13** | All lifecycle transitions must be authorized and audited. | RBAC / Audit Interceptor | `403 Forbidden` |
| **BR-14** | Domain events must be emitted through transactional outbox pattern. | Outbox Service | System Invariant |

---

## 4. Subject Lifecycle State Machine

```
      +-------------+
      |    DRAFT    |
      +------+------+
             |
             v
+------>  ACTIVE  <-------+ (Reactivate)
|            |            |
|      +-----+-----+      |
|      |           |      |
|      v           v      |
|  DEPRECATED  DEACTIVATED+
|      |           |
|      v           v
+----->   RETIRED  <------+
```

1. **DRAFT**: Initial creation state or preparation before formal publication.
2. **ACTIVE**: Published and effective; available for curriculum mapping, timetable scheduling, and enrollment.
3. **DEACTIVATED**: Temporarily paused; existing batch references remain valid, but new curriculum mapping is prohibited.
4. **DEPRECATED**: Signaled for phase-out; warning emitted on curriculum consumption.
5. **RETIRED**: Terminal lifecycle state; closed to all new usages while remaining queryable for historical transcripts and audit compliance.

---

## 5. Prerequisite & Co-requisite DAG Validation

Prerequisites form a **Directed Acyclic Graph (DAG)**. When a new relationship edge `A -> B` (Subject A requires B) is submitted:
1. Self-reference check (`A != B`).
2. Target status check (target subject must not be deactivated or retired).
3. Duplicate active relationship check.
4. Depth-First Search (DFS) reachability test: if path from `B` to `A` already exists, adding `A -> B` would create a cycle and is rejected with `409 Conflict` (`ACD_PREREQUISITE_CYCLE`).

---

## 6. Technical Collections Schema & Data Dictionary

The ACD-03 Subject Management Service operates with **9 dedicated collections** in the consolidated `CampXSync` MongoDB database under the `ACD03__` physical namespace (or `campx_subject` in isolated standalone environments).

### 6.1 Consolidated Collection Inventory

| # | Logical Collection | Physical Collection (`CampXSync`) | Purpose & Architectural Role | Primary Invariants |
|---|---|---|---|---|
| 1 | `subjects` | `ACD03__subjects` | Aggregate Root: Core identity, code uniqueness, department ownership, credits, taxonomy, lifecycle status | BR-01, BR-02, BR-03, BR-04, BR-08, BR-12 |
| 2 | `subject_versions` | `ACD03__subject_versions` | Monotonic immutable definitions, syllabus units, OBE outcomes, CO-PO matrix, board approval resolutions | BR-06, BR-07, BR-10 |
| 3 | `subject_metadata` | `ACD03__subject_metadata` | Extended catalog metadata, national IDs (ABC, AICTE, NPTEL), bibliographies, campus delivery rules, sensitivity | L1-L3 sensitivity classification |
| 4 | `subject_prerequisites` | `ACD03__subject_prerequisites` | Directed Acyclic Graph (DAG) edges: prerequisites, co-requisites, advisory recommendations | BR-06 DAG cycle prevention |
| 5 | `subject_equivalences` | `ACD03__subject_equivalences` | Academic transfer mappings: lateral entry, NEP 2020 credit transfers, external articulations, branch changes | Monotonic validity, credit multiplier |
| 6 | `subject_history` | `ACD03__subject_history` | Immutable temporal audit log of all subject lifecycle mutations and field-level change deltas | BR-13 append-only audit trail |
| 7 | `outbox_events` | `ACD03__outbox_events` | Transactional outbox table for at-least-once guaranteed asynchronous event publishing | BR-14 transactional outbox |
| 8 | `idempotency_records` | `ACD03__idempotency_records` | Mutating endpoint idempotency cache with automatic 24-hour TTL expiration | Distributed mutation deduplication |
| 9 | `dead_letter_events` | `ACD03__dead_letter_events` | Dead Letter Queue (DLQ) storage for unroutable or failed domain event dispatches | Quarantine & diagnostic replay |

---

### 6.2 Detailed Collection Specifications

#### 1. `ACD03__subjects` (Aggregate Root)
Owns authoritative master identity, taxonomy, credit allocations, and lifecycle status.

```json
{
  "_id": "ObjectId / UUID",
  "subjectId": "String (e.g. SUB-ACD-0001)",
  "tenantId": "String (Tenant isolation key)",
  "institutionId": "String (Institutional partition)",
  "campusId": "String (Optional campus scope)",
  "subjectCode": "String (e.g. CS301, unique in tenant + inst)",
  "name": "String (e.g. Data Structures & Algorithms)",
  "departmentId": "String (Owning department ref from ADM-02)",
  "crossListedDepartmentIds": ["String"],
  "subjectType": "Enum [CORE, ELECTIVE, PRACTICAL, PROJECT, AUDIT]",
  "classification": "Enum [THEORY, PRACTICAL, TUTORIAL, PROJECT, ELECTIVE, AUDIT]",
  "credits": 4.0,
  "contactHours": 45.0,
  "status": "Enum [DRAFT, ACTIVE, DEPRECATED, DEACTIVATED, RETIRED]",
  "currentVersion": 1,
  "version": 1,
  "description": "String",
  "deleted": false,
  "createdAt": "ISODate",
  "updatedAt": "ISODate",
  "createdBy": "String",
  "updatedBy": "String"
}
```
**Indexes**:
- `ux_tenant_inst_subject_code` (Unique): `{ tenantId: 1, institutionId: 1, subjectCode: 1 }`
- `ix_tenant_dept_subject_status`: `{ tenantId: 1, departmentId: 1, status: 1 }`
- `ix_tenant_cross_listed_depts` (Sparse): `{ tenantId: 1, crossListedDepartmentIds: 1 }`
- `ix_tenant_subject_type`: `{ tenantId: 1, subjectType: 1 }`
- `ux_subject_id` (Unique, Sparse): `{ subjectId: 1 }`
- `ix_tenant_status_updated`: `{ tenantId: 1, status: 1, updatedAt: -1 }`

---

#### 2. `ACD03__subject_versions` (Immutable Academic Snapshots)
Maintains monotonically increasing, immutable academic definitions, syllabus breakdown, and OBE accreditation alignments.

```json
{
  "_id": "ObjectId / UUID",
  "subjectId": "String (SUB-ACD-0001)",
  "tenantId": "String",
  "versionNo": 1,
  "status": "Enum [DRAFT, REVIEW, APPROVED, PUBLISHED, SUPERSEDED, RETIRED]",
  "credits": 4.0,
  "contactHours": 45.0,
  "checksum": "String (SHA-256 canonical hash of academic definition)",
  "courseOutcomes": [
    {
      "outcomeCode": "CO1",
      "statement": "Analyze asymptotic computational complexity of algorithms",
      "bloomLevel": "APPLY",
      "targetAttainment": 75.0
    }
  ],
  "coPoMatrix": [
    {
      "outcomeCode": "CO1",
      "programOutcomeCode": "PO1",
      "correlationStrength": 3
    }
  ],
  "syllabusUnits": [
    {
      "unitNumber": 1,
      "title": "Asymptotic Analysis & Elementary Data Structures",
      "topics": ["Big-O, Big-Omega, Big-Theta", "Stacks, Queues, Linked Lists"],
      "hours": 9.0
    }
  ],
  "approvalResolution": {
    "resolutionNumber": "BOS-CS-2026-03",
    "approvedByBoard": "Board of Studies - Computer Science",
    "meetingDate": "ISODate(2026-06-15T00:00:00Z)",
    "minutesUrl": "https://docs.campx.edu/bos/resolutions/2026-03.pdf"
  },
  "publishedAt": "ISODate",
  "publishedBy": "String (Registrar User ID)",
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```
**Indexes**:
- `ux_subject_version_no` (Unique): `{ subjectId: 1, versionNo: 1 }`
- `ix_subject_version_status`: `{ subjectId: 1, status: 1 }`
- `ix_subject_version_tenant_status`: `{ tenantId: 1, status: 1 }`

---

#### 3. `ACD03__subject_metadata` (Extended Catalog & Delivery Attributes)
Houses pedagogical descriptors, regulatory identifiers, textbooks, and campus delivery rules.

```json
{
  "_id": "ObjectId / UUID",
  "subjectId": "String (SUB-ACD-0001)",
  "tenantId": "String",
  "institutionId": "String",
  "description": "Comprehensive study of algorithm design and analysis...",
  "objective": "To develop foundational analytical skills for algorithmic thinking...",
  "learningOutcomes": ["Master tree and graph representations..."],
  "textbooks": ["Introduction to Algorithms, Cormen et al."],
  "references": ["The Algorithm Design Manual, Skiena"],
  "evaluationScheme": {
    "continuousInternalAssessmentWeight": 40,
    "endSemesterExamWeight": 60,
    "minimumPassingGrade": "D"
  },
  "nationalIdentifiers": [
    {
      "scheme": "ABC_COURSE_ID",
      "identifierValue": "ABC-CS-301-2026",
      "registeredDate": "ISODate(2026-01-10T00:00:00Z)"
    },
    {
      "scheme": "SWAYAM_NPTEL_ID",
      "identifierValue": "noc26-cs45",
      "registeredDate": "ISODate(2026-01-10T00:00:00Z)"
    }
  ],
  "bibliographies": [
    {
      "title": "Introduction to Algorithms",
      "authors": ["Cormen, T.", "Leiserson, C.", "Rivest, R.", "Stein, C."],
      "isbn": "978-0262033848",
      "edition": "3rd",
      "publisher": "MIT Press",
      "isTextbook": true
    }
  ],
  "campusDeliveryRules": [
    {
      "campusId": "CMP-MAIN",
      "deliveryMode": "IN_PERSON",
      "labFacilityRequired": true
    },
    {
      "campusId": "CMP-CITY",
      "deliveryMode": "HYBRID",
      "labFacilityRequired": true
    }
  ],
  "sensitivityLevel": "Enum [L1_PUBLIC, L2_INTERNAL, L3_RESTRICTED]",
  "tags": ["core-cs", "algorithms", "theory"],
  "attributes": {},
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```
**Indexes**:
- `ux_subject_metadata_id` (Unique): `{ subjectId: 1 }`
- `ix_subject_meta_sensitivity`: `{ tenantId: 1, sensitivityLevel: 1 }`
- `ix_subject_national_id` (Sparse): `{ "nationalIdentifiers.identifierValue": 1, "nationalIdentifiers.scheme": 1 }`

---

#### 4. `ACD03__subject_prerequisites` (DAG Dependency Graph)
Maintains directed acyclic graph edges connecting subject dependencies.

```json
{
  "_id": "String / UUID",
  "tenantId": "String",
  "subjectId": "String (Target subject, e.g. CS301)",
  "prerequisiteSubjectId": "String (Preceding requirement, e.g. CS101)",
  "relationshipType": "Enum [PREREQUISITE, CO_REQUISITE, RECOMMENDED]",
  "mandatory": true,
  "minimumGrade": "C",
  "status": "Enum [ACTIVE, INACTIVE]",
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```
**Indexes**:
- `ux_subject_prerequisite_edge` (Unique): `{ subjectId: 1, prerequisiteSubjectId: 1 }`
- `ix_subject_reverse_prereq`: `{ prerequisiteSubjectId: 1 }`
- `ix_subject_prereq_type_status`: `{ subjectId: 1, relationshipType: 1, status: 1 }`

---

#### 5. `ACD03__subject_equivalences` (Credit Transfers & NEP 2020 Articulations)
Governs subject substitution equivalences for lateral entry, inter-departmental transfers, and external college credits.

```json
{
  "_id": "String / UUID",
  "tenantId": "String",
  "sourceSubjectId": "String (SUB-CS-301)",
  "targetSubjectId": "String (SUB-IT-301)",
  "equivalenceType": "Enum [DIRECT_SUBSTITUTION, LATERAL_ENTRY_TRANSFER, NEP_CREDIT_TRANSFER, EXTERNAL_ARTICULATION, BRANCH_CHANGE]",
  "minimumGrade": "C",
  "transferMultiplier": 1.0,
  "externalInstitutionName": "Autonomous Polytechnic Institute",
  "effectiveFrom": "ISODate(2026-07-01T00:00:00Z)",
  "effectiveTo": null,
  "approvalAuthority": "Academic Council Resolution 2026/04",
  "status": "Enum [ACTIVE, REVOKED, EXPIRED]",
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```
**Indexes**:
- `ux_subject_equivalence_edge` (Unique): `{ sourceSubjectId: 1, targetSubjectId: 1, equivalenceType: 1 }`
- `ix_subject_reverse_equivalence`: `{ targetSubjectId: 1 }`
- `ix_subject_equivalence_type`: `{ tenantId: 1, equivalenceType: 1, status: 1 }`

---

#### 6. `ACD03__subject_history` (Audit Log & Temporal Change Records)
Captures every mutation, version transition, and administrative lifecycle change.

```json
{
  "_id": "String / UUID",
  "tenantId": "String",
  "subjectId": "String (SUB-ACD-0001)",
  "entityType": "Enum [SUBJECT, SUBJECT_VERSION, METADATA, PREREQUISITE, EQUIVALENCE]",
  "action": "Enum [CREATE, UPDATE, VERSION_CREATED, VERSION_PUBLISHED, DEACTIVATE, REACTIVATE, DEPRECATE, RETIRE, PREREQUISITE_ADDED, PREREQUISITE_REMOVED, EQUIVALENCE_ADDED, EQUIVALENCE_REMOVED, METADATA_UPDATED, DELETE]",
  "actorId": "String (User ID or Service Account)",
  "changedFields": ["credits", "contactHours"],
  "snapshot": { ... },
  "timestamp": "ISODate",
  "createdAt": "ISODate"
}
```
**Indexes**:
- `ix_subject_history_timeline`: `{ subjectId: 1, createdAt: -1 }`
- `ix_subject_history_actor`: `{ tenantId: 1, actorId: 1 }`
- `ix_audit_history_time`: `{ tenantId: 1, createdAt: -1 }`

---

#### 7. `ACD03__outbox_events` (Transactional Event Outbox)
Guarantees transactional event publishing with at-least-once delivery semantics.

```json
{
  "_id": "String / UUID",
  "eventId": "String (e.g. EVT-ACD-03-2026-0001)",
  "tenantId": "String",
  "aggregateType": "SUBJECT",
  "aggregateId": "String (SUB-ACD-0001)",
  "eventType": "SubjectVersionPublished",
  "payload": "JSON String or Document",
  "status": "Enum [PENDING, DISPATCHED, FAILED]",
  "attempts": 0,
  "correlationId": "String (CORR-12345)",
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```
**Indexes**:
- `ux_outbox_event_id` (Unique, Sparse): `{ eventId: 1 }`
- `ix_outbox_dispatch_queue`: `{ tenantId: 1, status: 1, createdAt: 1 }`

---

#### 8. `ACD03__idempotency_records` (API Mutation Deduplication)
Protects all mutating API calls against duplicate processing under network retries.

```json
{
  "_id": "String / UUID",
  "tenantId": "String",
  "idempotencyKey": "String (Client-supplied UUID or unique token)",
  "targetEndpoint": "POST /api/v1/academics/subjects",
  "responseStatus": 201,
  "responseBody": "JSON String cached response",
  "expiresAt": "ISODate (+24 hours)",
  "createdAt": "ISODate"
}
```
**Indexes**:
- `ux_tenant_idempotency` (Unique, Sparse): `{ tenantId: 1, idempotencyKey: 1 }`
- `ttl_idempotency` (TTL: 0s after expiresAt): `{ expiresAt: 1 }`

---

#### 9. `ACD03__dead_letter_events` (DLQ Quarantine & Diagnostic Queue)
Isolates failed dispatches or corrupted event payloads for inspection and automated replay.

```json
{
  "_id": "String / UUID",
  "tenantId": "String",
  "eventId": "String (EVT-ACD-03-2026-0001)",
  "failureReason": "Kafka broker unreachable after 5 retries",
  "status": "Enum [FAILED, REPLAYED, DISCARDED]",
  "attempts": 5,
  "payload": { ... },
  "createdAt": "ISODate",
  "updatedAt": "ISODate"
}
```
**Indexes**:
- `ux_dead_letter_event_id` (Unique, Sparse): `{ eventId: 1 }`
- `ix_dlq_replay_queue`: `{ tenantId: 1, status: 1, createdAt: -1 }`
- `ttl_dead_letter` (Sparse TTL): `{ expiresAt: 1 }`

---

### 6.3 Consolidated Schema Validation & Index Bootstrap

All 9 collections, their `$jsonSchema` validation rules, and business indexes are bootstrapped automatically via:
- **Consolidated Enterprise Database**: [CampXSync_MongoDB_Compass_Setup_FINAL.js](file:///d:/CampXSync/CampXSync_V2.0/database/mongodb/CampXSync_MongoDB_Compass_Setup_FINAL.js) (Database: `CampXSync`, 885 domain collections)
- **Standalone Service Setup**: [mongodb_indexes.js](file:///d:/CampXSync/CampXSync_V2.0/services/subject-management-service/mongodb_indexes.js) (Database: `campx_subject`)

---

## 7. Implementation of 10 Candidate User Stories

The ACD-03 Subject Management Service has been extended with full production support across models, domain services, REST endpoints, and automated tests for the 10 agreed candidate user stories:

### 7.1 Story 1: Course Outcomes (CO) Definition & Bloom's Taxonomy (OBE / Accreditation)
- **Model**: `SubjectModels.CourseOutcome` (`outcomeCode`, `statement`, `bloomLevel`, `targetAttainment`).
- **Validation**: Strict validation of Bloom's Taxonomy cognitive levels (`K1_REMEMBER` through `K6_CREATE`, or `K1`–`K6`). Target attainment bound between 0.0% and 100.0%.
- **Immutability (BR-06)**: Published versions cannot have their course outcomes altered without incrementing to a new draft version.
- **REST Endpoints**:
  - `PUT /api/v1/academics/subjects/{id}/versions/{v}/course-outcomes`
  - `GET /api/v1/academics/subjects/{id}/versions/{v}/course-outcomes`
- **Events**: Outbox event `SubjectCourseOutcomesUpdated` emitted on every mutation.

### 7.2 Story 2: CO-to-PO Articulation Matrix (OBE / NBA / NAAC Accreditation)
- **Model**: `SubjectModels.CoPoMapping` (`outcomeCode`, `programOutcomeCode`, `correlationStrength`).
- **Validation**: Correlation strength validated to 1 (Slight/Low), 2 (Moderate/Medium), or 3 (Substantial/High).
- **REST Endpoints**:
  - `PUT /api/v1/academics/subjects/{id}/versions/{v}/co-po-matrix`
  - `GET /api/v1/academics/subjects/{id}/versions/{v}/co-po-matrix`
- **Events**: Outbox event `SubjectCoPoMatrixUpdated` emitted.

### 7.3 Story 3: Subject Equivalence & Credit Transfer Mapping (NEP 2020 / Multi-Disciplinary)
- **Model**: `SubjectModels.SubjectEquivalence` (`id`, `sourceSubjectId`, `targetSubjectId`, `equivalenceType`, `minimumGrade`, `transferMultiplier`, `externalInstitutionName`, `effectiveFrom`, `effectiveTo`, `status`).
- **Equivalence Types**: `DIRECT_SUBSTITUTION`, `LATERAL_ENTRY`, `SWAYAM_NPTEL_TRANSFER`, `INTERNAL_ELECTIVE_SWAP`, `LEGACY_CURRICULUM_EQUIVALENCE`.
- **Collection**: Stored in `ACD03__subject_equivalences`.
- **REST Endpoints**:
  - `POST /api/v1/academics/subjects/{id}/equivalences`
  - `GET /api/v1/academics/subjects/{id}/equivalences`
  - `DELETE /api/v1/academics/subjects/{id}/equivalences/{equivalenceId}` (soft-revocation)
- **Events**: Outbox events `SubjectEquivalenceCreated` and `SubjectEquivalenceRevoked` emitted.

### 7.4 Story 4: National Academic Registries (ABC / APAAR / AICTE / SWAYAM NPTEL)
- **Model**: `SubjectModels.NationalIdentifier` (`scheme`, `identifierValue`, `registeredDate`, `validationStatus`).
- **Supported Schemes**: `ABC_COURSE_ID` (Academic Bank of Credits), `APAAR_SKILL_ID`, `AICTE_MODEL_CURRICULUM_ID`, `SWAYAM_NPTEL_ID`.
- **Storage**: Maintained inside `SubjectMetadata.nationalIdentifiers`.
- **REST Endpoints**: Managed via `PUT/GET /api/v1/academics/subjects/{id}/metadata`.

### 7.5 Story 5: Modular Syllabus Units & Planned Instructional Hours Breakdown
- **Model**: `SubjectModels.SyllabusUnit` (`unitNumber`, `title`, `topics`, `hours`).
- **Validation**: `unitNumber` > 0, mandatory title, non-negative hours. Immutability enforced for published versions.
- **REST Endpoints**:
  - `PUT /api/v1/academics/subjects/{id}/versions/{v}/syllabus-units`
  - `GET /api/v1/academics/subjects/{id}/versions/{v}/syllabus-units`
- **Events**: Outbox event `SubjectSyllabusUpdated` emitted.

### 7.6 Story 6: Prescribed Textbooks & Reference Bibliography Records
- **Model**: `SubjectModels.BibliographyItem` (`title`, `authors`, `isbn`, `edition`, `publisher`, `year`, `textbook`).
- **Storage**: Embedded inside `SubjectMetadata.bibliographies`.
- **Classification**: Distinguishes between prescribed textbooks (`textbook = true`) and recommended reference books (`textbook = false`).
- **REST Endpoints**: Managed via `PUT/GET /api/v1/academics/subjects/{id}/metadata`.

### 7.7 Story 7: Multi-Department Subject Cross-Listing
- **Fields**: `crossListedDepartmentIds` on aggregate root `Subject`.
- **Business Rules**:
  - All departments in `crossListedDepartmentIds` must exist and be active.
  - Primary `departmentId` cannot be included in `crossListedDepartmentIds`.
  - Search queries with `departmentId={id}` transparently return subjects where `{id}` is either the primary department OR a cross-listed department.
- **REST Endpoints**: `POST/PUT /api/v1/academics/subjects`, `GET /api/v1/academics/subjects/search?departmentId={id}`.

### 7.8 Story 8: Multi-Campus Delivery Constraints & Lab Facility Rules
- **Model**: `SubjectModels.CampusDeliveryRule` (`campusId`, `deliveryMode`, `labFacilityRequired`, `maxBatchSize`, `notes`).
- **Modes**: `OFFLINE`, `ONLINE`, `HYBRID`.
- **Storage**: Embedded inside `SubjectMetadata.campusDeliveryRules`.
- **REST Endpoints**: Managed via `PUT/GET /api/v1/academics/subjects/{id}/metadata`.

### 7.9 Story 9: Board of Studies (BoS) Governance Resolution & Gazette Metadata
- **Model**: `SubjectModels.ApprovalResolution` (`resolutionNumber`, `approvedByBoard`, `meetingDate`, `minutesUrl`, `gazetteNotificationNumber`).
- **Storage**: Bound to `SubjectVersion.approvalResolution`.
- **REST Endpoints**:
  - `PUT /api/v1/academics/subjects/{id}/versions/{v}/resolution`
  - `GET /api/v1/academics/subjects/{id}/versions/{v}/resolution`
- **Events**: Outbox event `SubjectResolutionUpdated` emitted.

### 7.10 Story 10: Visual Side-by-Side Subject Version Diff Engine
- **Model**: `SubjectModels.VersionDiffResult` (`subjectId`, `version1`, `version2`, `differences`, `summary`).
- **Engine Logic**: Compares fields across credits, contact hours, taxonomy, status, academic year, course outcomes count, CO-PO mappings count, syllabus units count, and BoS resolution.
- **REST Endpoint**:
  - `GET /api/v1/academics/subjects/{id}/versions/diff?v1={v1}&v2={v2}`
- **Security**: Available to `ACADEMIC_ADMIN`, `SUPER_ADMIN`, `REGISTRAR`, `DEPARTMENT_HEAD`, `AUDITOR`.


