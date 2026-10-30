# Cross-Module Integration Specification & Contract Guide
## Service Name: CampXSync Curriculum Management Service (ACD-02) (`services/curriculum-service`)

### 1. Integration Topology & Architecture Overview
The CampXSync Curriculum Management Service (ACD-02) is the authoritative system of record for academic structure, semester compositions, credit requirements, learning outcomes, and degree completion roadmaps. It interfaces with upstream course/subject catalog services and downstream scheduling, enrollment, and examination modules via asynchronous transactional outbox events and synchronous REST APIs mediated by the CampXSync API Gateway.

```
       ┌──────────────────────────────┐          ┌──────────────────────────────┐
       │            ACD-01            │          │            ACD-03            │
       │   Course Management Service  │          │   Subject Management Service │
       └──────────────┬───────────────┘          └──────────────┬───────────────┘
                      │ CourseDeactivated                       │ SubjectDeactivated
                      ▼                                         ▼
           ═══════════════════════════════════════════════════════════════
                             INBOUND MESSAGE BROKER TOPICS
           ═══════════════════════════════════════════════════════════════
                                         │
                                         ▼
                      ┌─────────────────────────────────────┐
                      │               ACD-02                │
                      │    Curriculum Management Service    │
                      └──────────────────┬──────────────────┘
                                         │
                                         │ Transactional Outbox
                                         ▼
           ═══════════════════════════════════════════════════════════════
                            OUTBOUND MESSAGE BROKER TOPICS
           ═══════════════════════════════════════════════════════════════
                 │                               │                       │
                 ▼                               ▼                       ▼
       ┌───────────────────┐           ┌───────────────────┐   ┌───────────────────┐
       │      ACD-04       │           │      ACD-05       │   │      EXM-01       │
       │ Academic Calendar │           │ Student Enrollment│   │  Examination Core │
       │   & Timetables    │           │  & Degree Audit   │   │  & Question Bank  │
       └───────────────────┘           └───────────────────┘   └───────────────────┘
```

---

### 2. Inbound Module Contracts (Upstream Dependencies)

#### 2.1 ACD-01: Course Management Service
- **Synchronous Dependency**: Before creating a curriculum, ACD-02 validates that the `courseId` exists and is active in ACD-01 (`BR-05`).
- **Asynchronous Inbound Events**:
  - **Topic**: `academic.course.events`
  - **Event Type**: `CourseDeactivated` / `CourseArchived`
  - **Schema**:
    ```json
    {
      "eventId": "EVT-ACD-01-000492",
      "eventType": "CourseDeactivated",
      "courseId": "COURSE-001",
      "tenantId": "TENANT-001",
      "reason": "Program restructured into new degree code",
      "occurredAt": 1774332800000
    }
    ```
  - **ACD-02 Reaction**:
    - Flags all active curricula referencing this course (`isFlaggedForReview = true`).
    - Sets `flagReason = "Referenced course COURSE-001 was deactivated/archived in ACD-01"`.
    - Prohibits state progression to `PUBLISHED` (`PublicationBlockedException`).

#### 2.2 ACD-03: Subject / Syllabus Management Service
- **Synchronous Dependency**: Subject IDs mapped to semesters must resolve to active subjects in ACD-03 (`BR-03`, `BR-06`).
- **Asynchronous Inbound Events**:
  - **Topic**: `academic.subject.events`
  - **Event Type**: `SubjectDeactivated`
  - **Schema**:
    ```json
    {
      "eventId": "EVT-ACD-03-001048",
      "eventType": "SubjectDeactivated",
      "subjectId": "SUB-101",
      "tenantId": "TENANT-001",
      "reason": "Deprecated by Academic Council",
      "occurredAt": 1774332900000
    }
    ```
  - **ACD-02 Reaction**:
    - Scans all draft and approved versions containing mappings for `SUB-101`.
    - Marks mappings as `isFlagged = true`.
    - Automatically blocks publication of any curriculum version containing flagged subjects with HTTP 422 `ACD2_PUBLICATION_BLOCKED`.

---

### 3. Outbound Module Contracts (Downstream Consumers)

All outbound events are persisted to the atomic `outbox_events` collection within the same database transaction as the aggregate change, guaranteeing At-Least-Once delivery.

#### 3.1 Event Catalog

| Event Type | Aggregate Type | Trigger Condition | Downstream Consumers |
| :--- | :--- | :--- | :--- |
| **`CurriculumCreated`** | `Curriculum` | New curriculum aggregate created | Audit, Reporting, Admin Dashboard |
| **`CurriculumSubmitted`** | `CurriculumVersion` | Version submitted for review | Department Head Notification |
| **`CurriculumApproved`** | `CurriculumVersion` | Version approved by Committee | Registrar Sign-Off Queue |
| **`CurriculumPublished`** | `CurriculumVersion` | Version published by Registrar | **ACD-04, ACD-05, EXM-01** |
| **`CurriculumVersionCreated`**| `CurriculumVersion` | Annual revision draft cloned | Department Curriculum Coordinator |
| **`CurriculumRetired`** | `Curriculum` | Curriculum retired | Degree Audit, Accreditation Archive |
| **`CurriculumSubjectMapped`** | `CurriculumVersion` | Subject mapped to semester | Subject Load Calculator |
| **`CurriculumSyllabusUpdated`**| `CurriculumVersion` | Syllabus modules modified | LMS / Learning Management System |

#### 3.2 Key Outbound Payload Schemas

##### 3.2.1 `CurriculumPublished` (Primary Operational Contract)
```json
{
  "eventId": "EVT-ACD-02-000042",
  "eventType": "CurriculumPublished",
  "aggregateId": "CURR-MCA-2026",
  "tenantId": "TENANT-001",
  "occurredAt": 1774333500000,
  "payload": {
    "curriculumId": "CURR-MCA-2026",
    "versionNo": 1,
    "courseId": "COURSE-001",
    "totalCredits": 80.0,
    "status": "PUBLISHED"
  }
}
```
- **ACD-04 Consumer Action**: Generates timetable scheduling slots for all mapped semester subjects.
- **ACD-05 Consumer Action**: Configures student registration baskets and prerequisite rules for upcoming term enrollment.
- **EXM-01 Consumer Action**: Configures examination grade-scale boundaries and assessment blueprints.

##### 3.2.2 `CurriculumCreated`
```json
{
  "eventId": "EVT-ACD-02-000001",
  "eventType": "CurriculumCreated",
  "aggregateId": "CURR-527CDFA3",
  "tenantId": "TENANT-001",
  "occurredAt": 1774332800000,
  "payload": {
    "curriculumId": "CURR-527CDFA3",
    "courseId": "COURSE-001",
    "academicPattern": "CBCS",
    "academicYear": "2026-2027",
    "status": "DRAFT",
    "currentVersion": 1
  }
}
```

---

### 4. Accreditation & Compliance Synchronous Integration (ACC-01)
For external accreditation auditors (NBA, NAAC, ABET) and institutional academic committees, ACD-02 provides a dedicated composite endpoint:
- **URI**: `GET /api/v1/academics/curricula/{id}/compliance-view`
- **Required RBAC Permissions**: `CURRICULUM_VIEW`, `CURRICULUM_EXPORT`
- **Data Returned**:
  - Curriculum identity, regulation, and lifecycle history.
  - Complete semester breakdown with mandatory vs elective credits.
  - Granular syllabus modules with lecture/tutorial/practical contact hours.
  - Full Bloom's Taxonomy learning outcomes matrix mapped to subjects.
  - Pre-computed SHA-256 cryptographic verification checksum ensuring document tamper-evidence.

---

### 5. Distributed Tracing & Correlation Standards
All cross-module synchronous and asynchronous interactions adhere to the CampXSync enterprise header propagation standard:
- `X-Trace-Id`: Monotonically preserved across HTTP hops and embedded into outbox event envelopes (`correlationId`).
- `X-Tenant-Id`: Ensures multi-tenant isolation across all bounded contexts.
- `X-User-Role`: Enforces role-based permissions at API Gateway and downstream services.
