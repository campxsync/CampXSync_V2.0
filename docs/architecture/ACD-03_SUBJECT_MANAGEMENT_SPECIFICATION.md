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
         | MongoDB (Subject DB)  |               | Event Broker (Kafka)  |
         | - subjects            |               | - SubjectCreated      |
         | - subject_versions    |               | - SubjectUpdated      |
         | - subject_metadata    |               | - SubjectPublished    |
         | - outbox_events       |               | - SubjectDeactivated  |
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

## 6. Technical Collections Schema

### 6.1 `subjects` (Aggregate Root)
- `_id`: String / ObjectId
- `tenantId`: String (Indexed)
- `institutionId`: String (Indexed)
- `subjectCode`: String (Unique with tenantId + institutionId)
- `name`: String
- `departmentId`: String (Indexed)
- `subjectType`: Enum (`CORE`, `ELECTIVE`, `PRACTICAL`, `PROJECT`, `AUDIT`)
- `classification`: Enum (`THEORY`, `PRACTICAL`, `TUTORIAL`, `PROJECT`, `ELECTIVE`, `AUDIT`)
- `credits`: Double
- `contactHours`: Double
- `status`: Enum (`DRAFT`, `ACTIVE`, `DEPRECATED`, `DEACTIVATED`, `RETIRED`)
- `currentVersion`: Integer
- `version`: Long (Optimistic lock)

### 6.2 `subject_versions`
- `_id`: String / ObjectId
- `subjectId`: String (Indexed)
- `versionNo`: Integer (Monotonic)
- `status`: Enum (`DRAFT`, `REVIEW`, `APPROVED`, `PUBLISHED`, `SUPERSEDED`, `RETIRED`)
- `credits`: Double
- `contactHours`: Double
- `checksum`: String (SHA-256 canonical hash)
- `publishedAt`: Timestamp
- `publishedBy`: String

### 6.3 `subject_prerequisites`
- `_id`: String
- `subjectId`: String (Indexed)
- `prerequisiteSubjectId`: String (Indexed)
- `relationshipType`: Enum (`PREREQUISITE`, `CO_REQUISITE`, `RECOMMENDED`)
- `mandatory`: Boolean
- `minimumGrade`: String
- `status`: String (`ACTIVE`, `INACTIVE`)

### 6.4 `outbox_events`
- `_id`: String
- `eventId`: String (e.g. `EVT-ACD-03-000001`)
- `eventType`: String
- `aggregateId`: String
- `payload`: JSON String
- `status`: `PENDING` | `PUBLISHED` | `FAILED`
- `attempts`: Integer
- `correlationId`: String
