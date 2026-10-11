# ACD-04 Batch Management Service - Enterprise Technical Specification

> **Module ID**: ACD-04  
> **Service Name**: Batch Management Service  
> **Tier**: Academic Management Tier  
> **Status**: Production Ready  
> **Version**: 2.0.0  
> **Default Port**: 8086  

---

## 1. Executive Summary & Purpose

The **ACD-04 Batch Management Service** is the authoritative master for academic cohort grouping, sections, capacity limits, authorized over-capacity exceptions, and student roster enrollments across the CampXSync College ERP platform. It guarantees strict invariant enforcement so that:
1. Every batch is tied to an active, validated course in **ACD-01 (Course Management)**.
2. Batch codes are strictly unique within tenant/institution and academic scopes (BR-02).
3. Student enrollments are bounded by configured capacity limits, with governed time-bound overrides (BR-03, BR-04).
4. Duplicate active assignments are prevented (BR-06) and inactive/closed batches reject enrollment (BR-07).
5. Historical roster composition and audit trails are append-only and never deleted (BR-08, BR-09).
6. Complex cohort splitting and merging are orchestrated through asynchronous events and gated by Registrar approval delegated to **ADM-02 (College Admin)**.

---

## 2. Bounded Context & Ownership Boundary

```
                     +---------------------------------------+
                     |         API Gateway (Port 8080)       |
                     +-------------------+-------------------+
                                         |
                                         v
                     +---------------------------------------+
                     |   ACD-04 Batch Service (Port 8086)    |
                     |  - Authoritative Batch Aggregate      |
                     |  - BatchSection Sub-Entities          |
                     |  - Capacity Limits & Overrides        |
                     |  - Student Roster Memberships         |
                     |  - Lifecycle State Machine            |
                     |  - Split & Merge Orchestration        |
                     |  - Transactional Outbox Publisher     |
                     +-------------------+-------------------+
                                         |
                     +-------------------+-------------------+
                     |                                       |
                     v                                       v
         +-----------------------+               +-----------------------+
         | MongoDB (CampXSync)   |               | Event Broker (Kafka)  |
         | - batches             |               | - BatchCreated        |
         | - batch_rosters       |               | - BatchActivated      |
         | - batch_overrides     |               | - StudentAddedToBatch |
         | - batch_history       |               | - BatchSplitRequested |
         | - outbox_events       |               | - BatchMerged         |
         +-----------------------+               +-----------+-----------+
                                                             |
                   +----------------+----------------+-------+--------+
                   |                |                |                |
                   v                v                v                v
               [ACD-05]          [STM]             [HRM]            [EXM]
              Timetable         Students          Faculty           Exams
```

### ACD-04 Owns
- Authoritative batch aggregate: `batchCode`, `name`, `courseId`, `curriculumId`, `departmentId`, `campusId`, `academicYear`, `semesterNo`, `capacity`, `rosterCount`, `status`.
- Sub-entities in `sections`: `sectionCode`, `sectionName`, `capacity`, `facultyId` (logical reference only).
- Enrolled student memberships in `batch_rosters`: `membershipId`, `studentId`, `effectiveFrom`, `effectiveTo`, `membershipType`, `status`.
- Governed exceptions in `batch_capacity_overrides`: `overrideCapacity`, `reason`, `approvedBy`, `effectiveFrom`, `effectiveTo`, `status`.
- Immutable append-only audit trail in `batch_history`.
- Transactional outbox events for all mutations.

### ACD-04 Does NOT Own
- Student Master Data (Owned by **STM**; ACD-04 stores only `studentId`).
- Faculty Master Data (Owned by **HRM**; ACD-04 stores only logical `facultyId`).
- Course and Program Master Data (Owned by **ACD-01**; ACD-04 validates references).
- Workflow Approval Engine (Delegated to **ADM-02**; ACD-04 exposes no local approve/reject endpoint).

---

## 3. Data Dictionary & Storage Model

### 3.1 Primary Collections

| Collection | Schema Entity | Purpose | Key Indexes |
|---|---|---|---|
| `batches` | `Batch` | Authoritative batch aggregate root | `{tenantId: 1, batchCode: 1}` (Unique), `{courseId: 1, academicYear: 1, semesterNo: 1}`, `{status: 1}` |
| `batch_rosters` | `BatchRoster` | Enrolled student memberships | `{batchId: 1, studentId: 1, status: 1}`, `{studentId: 1, status: 1}` |
| `batch_capacity_overrides` | `BatchCapacityOverride` | Authorized exceptions beyond standard capacity | `{batchId: 1, status: 1}`, `{effectiveTo: 1}` |
| `batch_history` | `BatchHistory` | Append-only audit trail | `{batchId: 1, timestamp: -1}`, `{correlationId: 1}` |
| `outbox_events` | `OutboxEvent` | Transactional outbox table | `{status: 1, occurredAt: 1}`, `{eventId: 1}` (Unique) |
| `inbox_events` | `InboxEvent` | Inbound deduplication store | `{eventId: 1}` (Unique), `{processedAt: 1}` |
| `dead_letter_events` | `DeadLetterEvent` | Unprocessable poison events | `{eventId: 1}`, `{createdAt: 1}` |
| `idempotency_records` | `IdempotencyRecord` | Safe client retry cache | `{tenantId: 1, idempotencyKey: 1}` (Unique), `{expiresAt: 1}` |

---

## 4. Key Business Rules & Invariants

- **BR-01 (Course & Semester Validation)**: A batch cannot be created or activated without resolving to an active course in ACD-01 and a valid semester context (1–12).
- **BR-02 (Batch Code Uniqueness)**: `batchCode` must be unique per tenant and institution scope (HTTP 409 `ACD_BATCH_CODE_DUPLICATE`).
- **BR-03 (Capacity Positive Boundary)**: Batch capacity must be strictly greater than zero and cannot be set below the current active `rosterCount`.
- **BR-04 (Capacity Enforcement)**: Total active students in `batch_rosters` cannot exceed configured capacity unless an authorized, active, unexpired override exists in `batch_capacity_overrides` (HTTP 409 `ACD_BATCH_CAPACITY_EXCEEDED`).
- **BR-05 (Student Eligibility)**: Students must be validated as active and eligible with Student Management (STM) before enrollment (HTTP 422 `ACD_BATCH_STUDENT_INELIGIBLE`).
- **BR-06 (No Duplicate Active Assignment)**: A student can hold at most one active membership in a given batch aggregate at any point in time (HTTP 409 `ACD_BATCH_ALREADY_ASSIGNED`).
- **BR-07 (Closure Enrollment Block)**: Batches in status `CLOSED` or `ARCHIVED` reject any new student additions (HTTP 409 `ACD_BATCH_CLOSED`).
- **BR-08 (Historical Retention)**: Batch closure or student removal never deletes roster records; memberships are soft-ended (`status = ENDED`, `effectiveTo` timestamp set).
- **BR-09 (Append-Only Audit)**: All mutations write immutable context records to `batch_history`.
- **BR-11 (Optimistic Locking)**: Concurrent batch modifications must provide matching `version` numbers; stale edits are rejected with HTTP 409 `ACD_BATCH_VERSION_CONFLICT`.
- **BR-14 (Course Deactivation Policy)**: Deactivation of an upstream course flags referencing batches as `courseActive=false` and blocks new roster enrollment without deleting historical data.

---

## 5. Batch Lifecycle State Machine

```
      +-----------+
      |   DRAFT   | <--- (createBatch)
      +-----+-----+
            |
            | openBatch()
            v
      +-----------+
      |  ACTIVE   | <-----------------------+
      +--+-----+--+                         |
         |     |                            |
         |     | closeBatch()               | reopenBatch()
         |     v                            |
         |  +-----------+                   |
         |  |  CLOSED   +-------------------+
         |  +-----+-----+
         |        |
         |        | archiveBatch()
         |        v
         |  +-----------+
         |  | ARCHIVED  |
         |  +-----------+
         |
         +---+ (requestSplit / requestMerge)
             |
             +--> [PENDING_SPLIT_APPROVAL / PENDING_MERGE_APPROVAL]
                       |
                       +-- Decision=APPROVED --> Split/Merged Executed (Source CLOSED)
                       |
                       +-- Decision=REJECTED --> Reverts to ACTIVE (History logged)
```

---

## 6. Cross-Module Split & Merge Orchestration (with ADM-02)

To maintain strict separation of duties, **ACD-04 exposes no local approve/reject endpoints**. Approval authority is granted exclusively to the **Registrar** role in the platform role catalog and executed via **ADM-02 College Admin's** generic workflow engine (`/v1/batch-approvals`):

1. **Initiation**: Academic Admin calls `POST /api/v1/batches/{id}/split` or `POST /api/v1/batches/merge`.
2. **Transition**: Affected batches enter `PENDING_SPLIT_APPROVAL` or `PENDING_MERGE_APPROVAL`.
3. **Outbox Event Emitted**: ACD-04 emits `BatchSplitApprovalRequested` or `BatchMergeApprovalRequested` targeting the Registrar.
4. **Registrar Review**: The Registrar reviews and approves/rejects the request inside ADM-02.
5. **Decision Ingestion**: ACD-04 ingests `BatchSplitApprovalDecided` or `BatchMergeApprovalDecided`:
   - If `APPROVED`:
     - Creates new target batches.
     - Reassigns target students with updated `batchId` references.
     - Closes source batches.
     - Emits `BatchSplitApproved` and `BatchSplit` (or `BatchMergeApproved` and `BatchMerged`) containing a complete **roster-reassignment map** (`{studentId, oldBatchId, oldSectionId, newBatchId, newSectionId}`) so downstream modules (ACD-05, STM, HRM, EXM) reconcile independently without callbacks.
   - If `REJECTED`:
     - Reverts source batches back to prior status (`ACTIVE`).
     - Logs rejection reason to `batch_history`.

---

## 7. Role-Based Access Control (RBAC) Matrix

| Operation | Super Admin | Academic Admin | Department Head | Registrar | Faculty | Student | Parent | Accreditation | External API |
|---|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|:---:|
| Create Batch Draft | Yes | Yes | Yes (Own Dept) | No | No | No | No | No | No |
| Edit Batch | Yes | Yes | Yes (Own Dept) | No | No | No | No | No | No |
| View Batch Detail | Yes | Yes | Yes | Yes | Yes | Own Batch | Child Batch | Yes | Read-Only |
| Search Batches | Yes | Yes | Yes | Yes | Yes | Own Batch | Child Batch | Yes | Read-Only |
| Manage Sections | Yes | Yes | Yes (Own Dept) | No | No | No | No | No | No |
| Manage Capacity | Yes | Yes | Yes (Own Dept) | No | No | No | No | No | No |
| Grant Capacity Override | Yes | Yes | No | Yes | No | No | No | No | No |
| Add / Remove Student | Yes | Yes | Yes (Own Dept) | No | No | No | No | No | No |
| View Active Roster | Yes | Yes | Yes | Yes | Assigned | Own Entry | Child Entry | Yes | Masked L3 |
| Open / Activate Batch | Yes | Yes | Yes (Own Dept) | No | No | No | No | No | No |
| Close Batch | Yes | Yes | No | Yes | No | No | No | No | No |
| Archive Batch | Yes | No | No | Yes | No | No | No | No | No |
| Split/Merge Request | Yes | Yes | Yes (Own Dept) | No | No | No | No | No | No |
| Split/Merge Approval | No | No | No | Yes (in ADM-02)| No | No | No | No | No |
| View Point-in-Time History | Yes | Yes | Yes | Yes | No | No | No | Yes | No |

---

## 8. Standard Response & RFC 7807 Error Shapes

### Standard Success Response
```json
{
  "success": true,
  "data": { ... },
  "meta": {
    "requestId": "REQ-7b89f012",
    "correlationId": "TRACE-9b1deb4d",
    "timestamp": 1789973000000,
    "page": 1,
    "pageSize": 20,
    "totalCount": 1
  }
}
```

### RFC 7807 Error Response
```json
{
  "success": false,
  "error": {
    "code": "ACD_BATCH_CAPACITY_EXCEEDED",
    "message": "Total enrolled students (60) has reached capacity limit (60)"
  },
  "meta": {
    "requestId": "REQ-9c81a234",
    "correlationId": "TRACE-9b1deb4d",
    "timestamp": 1789973000100
  }
}
```

---

## 9. Observability & Prometheus Metrics

Exposed at `GET /metrics` and reverse-proxied via `GET /api/v1/batches/metrics`:

- `acd04_request_total{method, path, status}`: Total HTTP requests.
- `acd04_request_duration_seconds{method, path}`: Cumulative request latency.
- `acd04_error_total{errorCode}`: Total errors by taxonomy.
- `acd04_capacity_conflicts_total`: Counter for capacity exceeded rejections.
- `acd04_roster_reconciliation_mismatches`: Count of batches where `rosterCount != count(active batch_rosters)`.
- `acd04_batch_count{status}`: Current count of batches by lifecycle state.
- `acd04_roster_members_active`: Total active student enrollments.
- `acd04_capacity_overrides_active`: Total active capacity overrides.
- `acd04_outbox_backlog`: Count of pending outbox events.
- `acd04_dlq_events_total`: Count of dead letter queue events.

---

## 10. Verification & Test Coverage

The ACD-04 test suite provides 100% coverage across all 8 epics:
1. `BatchServiceTest.java`: 20 unit and domain service tests verifying state machines, capacity invariants, eligibility, duplicate rejections, split/merge requests, and ADM-02 decision consumption.
2. `BatchControllerIntegrationTest.java`: 8 HTTP REST integration tests verifying status codes (200, 201, 202, 403, 409, 422), correlation headers, idempotency replay, and metrics scraping.
3. `GatewayBatchIntegrationTest.java`: 4 end-to-end integration tests routing through the API Gateway reverse proxy on port 8080/8095.
