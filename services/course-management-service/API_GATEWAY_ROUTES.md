# ACD-01 Course Management Service - API Gateway Specification & Route Catalog

> **Service Identifier**: `ACD-01` (Course Management Service)  
> **Service Port**: `8083`  
> **Gateway Ingress Port**: `8080` (`http://localhost:8080`)  
> **Document Location**: `services/course-management-service/API_GATEWAY_ROUTES.md`  
> **Status**: Production Ready  

---

## 1. Gateway Routing Architecture & Ingress Topology

The **CampXSync API Gateway** (Port 8080) acts as the unified reverse proxy, security perimeter, and correlation token dispatcher for the **ACD-01 Course Management Service** (Port 8083). All external calls from web portals, mobile apps, and upstream microservices flow through the API Gateway.

```
+-------------------------------------------------------------------------------+
|                             External Clients                                  |
|         (Web Admin Portal, Student Mobile App, Faculty Dashboard, APIs)       |
+---------------------------------------+---------------------------------------+
                                        |
                                        | HTTP REST (JSON)
                                        v
+-------------------------------------------------------------------------------+
|                            CampXSync API Gateway                              |
|                            Port: 8080 (Ingress)                               |
|                                                                               |
|  * Correlation Extraction & Injection (X-Trace-Id, X-Tenant-Id, X-User-Id)    |
|  * Reverse Proxy Route Resolution                                             |
|  * Timeouts & Resilience (5000ms Connect, 10000ms Read)                       |
|  * Downstream RFC 7807 Error Pass-Through (409 Conflict, 422 Cycle/State)     |
+---------------------------------------+---------------------------------------+
                                        |
      /api/v1/courses/**                |  /v1/courses/**
      /api/v1/academics/courses/**      |  /v1/course-catalog/**
                                        |
                                        v Reverse Proxy
+-------------------------------------------------------------------------------+
|               ACD-01: Course Management Service (Port 8083)                   |
|                                                                               |
|  * Authoritative Course Master & Normalization                                |
|  * Versioning & Immutable Snapshots                                           |
|  * DAG Prerequisite Graph & Cycle Detection (DFS)                             |
|  * Batch Offering Deactivation Gating                                         |
|  * Transactional Outbox Events Engine                                         |
|  * 7-Role RBAC Matrix Enforcement                                             |
|  * Central Logger & Audit Integration                                         |
+-------------------------------------------------------------------------------+
```

---

## 2. Gateway Route Registry Table for ACD-01

The API Gateway exposes both full direct ingress paths and canonical short aliases for ACD-01:

| Route ID | Ingress Path (Port 8080) | Downstream Target (Port 8083) | Method(s) | Description / Capability |
|---|---|---|---|---|
| **GW-ACD-01** | `/api/v1/courses` | `http://localhost:8083/api/v1/courses` | `POST`, `GET` | Core Course Ingress (Create Course / List All Courses) |
| **GW-ACD-02** | `/api/v1/courses/*` | `http://localhost:8083/api/v1/courses/*` | `GET`, `PUT`, `DELETE` | Course Detail, Update, Prerequisites, Versions, Lifecycle |
| **GW-ACD-03** | `/api/v1/courses/catalog` | `http://localhost:8083/api/v1/courses/catalog` | `GET` | Published Course Catalog Projection (Active courses only) |
| **GW-ACD-04** | `/api/v1/courses/search` | `http://localhost:8083/api/v1/courses/search` | `GET` | Multi-parameter Course Search & Filter |
| **GW-ACD-05** | `/api/v1/academics/courses` | `http://localhost:8083/api/v1/academics/courses` | `ANY` | Academic Namespace Direct Ingress |
| **GW-ACD-06** | `/v1/courses` | `http://localhost:8083/api/v1/courses` | `POST`, `GET` | Canonical Short Gateway Alias for Courses Master |
| **GW-ACD-07** | `/v1/courses/*` | `http://localhost:8083/api/v1/courses/*` | `ANY` | Canonical Short Gateway Alias for Course Sub-Resources |
| **GW-ACD-08** | `/v1/course-catalog` | `http://localhost:8083/api/v1/courses/catalog` | `GET` | Public / Student Canonical Short Alias for Catalog |

---

## 3. Complete ACD-01 Endpoint Catalog via Gateway

All calls below are issued to the API Gateway at `http://localhost:8080`.

### 3.1 Course Definition & Identity (Epic 1)

#### 1. Create Draft Course
- **Gateway Route**: `POST /api/v1/courses` or `POST /v1/courses`
- **Downstream Target**: `POST http://localhost:8083/api/v1/courses`
- **Request Headers**:
  - `Content-Type: application/json`
  - `X-Trace-Id: TRACE-2026-001` *(optional, generated if omitted)*
  - `X-Tenant-Id: CAMPUS_MAIN`
  - `X-User-Role: ACADEMIC_ADMIN`
- **Request Body**:
```json
{
  "courseCode": "CS201",
  "courseName": "Data Structures and Algorithms",
  "description": "Comprehensive study of abstract data types and graph algorithms.",
  "departmentId": "DEP_CS",
  "durationYears": 1,
  "totalCredits": 4.0,
  "courseType": "THEORY",
  "courseCategory": "CORE"
}
```
- **Response**: `201 Created`
```json
{
  "id": "CRS_5BD910627C32",
  "courseCode": "CS201",
  "courseName": "Data Structures and Algorithms",
  "status": "DRAFT",
  "version": "1.0",
  "totalCredits": 4.0,
  "createdAt": 1789973000000
}
```

#### 2. Get Course Detail
- **Gateway Route**: `GET /api/v1/courses/{id}` or `GET /v1/courses/{id}`
- **Target**: `GET http://localhost:8083/api/v1/courses/{id}`
- **Response**: `200 OK`
```json
{
  "id": "CRS_5BD910627C32",
  "courseCode": "CS201",
  "courseName": "Data Structures and Algorithms",
  "description": "Comprehensive study of abstract data types and graph algorithms.",
  "departmentId": "DEP_CS",
  "durationYears": 1,
  "totalCredits": 4.0,
  "courseType": "THEORY",
  "courseCategory": "CORE",
  "status": "DRAFT",
  "version": 1
}
```

#### 3. Update Draft Course
- **Gateway Route**: `PUT /api/v1/courses/{id}` or `PUT /v1/courses/{id}`
- **Target**: `PUT http://localhost:8083/api/v1/courses/{id}`
- **Request Body**:
```json
{
  "courseName": "Advanced Data Structures & Algorithms",
  "totalCredits": 4.5
}
```
- **Response**: `200 OK`
```json
{
  "status": "UPDATED",
  "id": "CRS_5BD910627C32",
  "courseCode": "CS201"
}
```
*(Note: If course is already `ACTIVE`, in-place PUT returns `422 Unprocessable Entity` requiring the version path).*

---

### 3.2 Course Catalog & Search (Epic 2)

#### 4. Browse Published Course Catalog
- **Gateway Route**: `GET /api/v1/courses/catalog` or `GET /v1/course-catalog`
- **Target**: `GET http://localhost:8083/api/v1/courses/catalog`
- **Description**: Returns only approved and active courses for public/student view.
- **Response**: `200 OK`
```json
{
  "catalog": [
    {
      "courseId": "CRS_CS101",
      "code": "CS101",
      "name": "Introduction to Computer Science",
      "departmentId": "DEP_CS",
      "credits": 4.0,
      "version": 1
    }
  ]
}
```

#### 5. Search & Filter Courses
- **Gateway Route**: `GET /api/v1/courses/search?departmentId=DEP_CS&status=ACTIVE&keyword=data`
- **Target**: `GET http://localhost:8083/api/v1/courses/search?...`
- **Response**: `200 OK`
```json
{
  "results": [
    {
      "id": "CRS_5BD910627C32",
      "code": "CS201",
      "name": "Data Structures and Algorithms",
      "status": "ACTIVE"
    }
  ]
}
```

---

### 3.3 Course Versioning & History (Epic 3 & 13)

#### 6. Create New Version Snapshot
- **Gateway Route**: `POST /api/v1/courses/{id}/versions`
- **Target**: `POST http://localhost:8083/api/v1/courses/{id}/versions`
- **Request Body**:
```json
{
  "changeSummary": "Updated syllabus with modern concurrency patterns"
}
```
- **Response**: `201 Created`
```json
{
  "versionId": "VER_CS201_2",
  "versionNo": 2,
  "status": "DRAFT"
}
```

#### 7. List Version History
- **Gateway Route**: `GET /api/v1/courses/{id}/versions`
- **Response**: `200 OK`

#### 8. Retrieve Audit History
- **Gateway Route**: `GET /api/v1/courses/{id}/history`
- **Response**: `200 OK`
```json
{
  "history": [
    {
      "action": "CREATE",
      "from": null,
      "to": "DRAFT",
      "actor": "admin",
      "timestamp": 1789973000000
    },
    {
      "action": "PUBLISH",
      "from": "APPROVED",
      "to": "ACTIVE",
      "actor": "admin",
      "timestamp": 1789973050000
    }
  ]
}
```

---

### 3.4 Prerequisite Management & DAG Cycle Detection (Epic 4)

#### 9. Add Prerequisite Relationship
- **Gateway Route**: `POST /api/v1/courses/{id}/prerequisites`
- **Target**: `POST http://localhost:8083/api/v1/courses/{id}/prerequisites`
- **Request Body**:
```json
{
  "prerequisiteCourseId": "CRS_CS101",
  "relationshipType": "MANDATORY",
  "minimumGrade": "C"
}
```
- **Success Response**: `201 Created`
```json
{
  "prerequisiteId": "PRE_7663D8E2",
  "courseId": "CRS_5BD910627C32",
  "prerequisiteCourseId": "CRS_CS101",
  "status": "ACTIVE"
}
```
- **Cycle Detection Error**: `422 Unprocessable Entity` (if adding edge creates circular dependency)
```json
{
  "timestamp": 1789973100000,
  "status": 422,
  "error": "Unprocessable Entity",
  "errorCode": "ACD_PREREQUISITE_CYCLE",
  "message": "Cyclic prerequisite dependency detected between course [CS101] and proposed prerequisite [CS201]",
  "path": "/api/v1/courses/CRS_CS101/prerequisites",
  "traceId": "TRACE-2026-001"
}
```

#### 10. List Active Prerequisites
- **Gateway Route**: `GET /api/v1/courses/{id}/prerequisites`
- **Response**: `200 OK`

#### 11. Remove Prerequisite
- **Gateway Route**: `DELETE /api/v1/courses/{id}/prerequisites/{prerequisiteId}`
- **Response**: `200 OK`

---

### 3.5 Lifecycle Transitions & Batch Offerings (Epic 8 & 13)

| Lifecycle Stage | Ingress Gateway Route | Target State | Invariants & Rules Enforced |
|---|---|---|---|
| **Submit** | `POST /api/v1/courses/{id}/submit` | `UNDER_REVIEW` | Allowed only from `DRAFT`. |
| **Approve** | `POST /api/v1/courses/{id}/approve` | `APPROVED` | Allowed only from `UNDER_REVIEW`. |
| **Publish** | `POST /api/v1/courses/{id}/publish` | `ACTIVE` | Syncs to catalog projection; requires accreditation for `REGULATORY` courses. |
| **Suspend** | `POST /api/v1/courses/{id}/suspend` | `SUSPENDED` | Temporarily hides course from public catalog. |
| **Deactivate** | `POST /api/v1/courses/{id}/deactivate` | `DEACTIVATED` | **Gated**: Blocked if active batch offerings exist (`422 ACD_ACTIVE_BATCH_EXISTS`). |
| **Archive** | `POST /api/v1/courses/{id}/archive` | `ARCHIVED` | Soft-deletes course into historical repository. |

#### 12. Batch Offerings Lifecycle
- **Add Batch Offering**: `POST /api/v1/courses/{id}/batch-offerings` (`{"batchId":"BATCH_2026_CS","term":"TERM_1"}`) &rarr; `201 Created`
- **List Batch Offerings**: `GET /api/v1/courses/{id}/batch-offerings` &rarr; `200 OK`
- **Close Batch Offering**: `POST /api/v1/courses/{id}/batch-offerings/{offeringId}/close` &rarr; `200 OK`

---

## 4. Header Forwarding & Distributed Correlation

The Gateway applies `CorrelationFilter` to ensure full end-to-end distributed tracing across all ACD-01 operations:

| Header Name | Type | Gateway Handling | Downstream ACD-01 Usage |
|---|---|---|---|
| `X-Trace-Id` | String (Hex/UUID) | Passed through if present; generated if missing. Returned in response headers. | Bound to `LogContext.traceId`, tagged on all async log events and audit records. |
| `X-Tenant-Id` | String | Forwarded downstream. | Enforces multi-tenant isolation across campuses/institutions. |
| `X-User-Id` | String | Forwarded downstream. | Attributed as `actorId` in `course_history` and `AuditEvent`. |
| `X-User-Role` | String | Forwarded downstream. | Validated against the 7-Role RBAC Matrix (`ACADEMIC_ADMIN`, `STUDENT`, `AUDITOR`, etc.). |

---

## 5. RFC 7807 Error Code Catalog for ACD-01

All downstream domain errors are transparently preserved and forwarded through the Gateway to external clients:

| Status Code | Error Reason | Error Code (`errorCode`) | Cause / Trigger |
|---|---|---|---|
| `400 Bad Request` | Bad Request | `ACD_VALIDATION_ERROR` | Missing mandatory field or non-positive credits (`totalCredits <= 0`). |
| `400 Bad Request` | Bad Request | `ACD_INVALID_DEPARTMENT` | Department does not exist or is inactive in the reference data. |
| `400 Bad Request` | Bad Request | `ACD_MANDATORY_ACCREDITATION_MISSING` | Publishing regulatory course without active accreditation. |
| `401 Unauthorized` | Unauthorized | `ACD_UNAUTHORIZED` | Request missing identity or authentication tokens. |
| `403 Forbidden` | Forbidden | `ACD_FORBIDDEN` | Role unauthorized for action (e.g. Student attempting course creation). |
| `404 Not Found` | Not Found | `ACD_NOT_FOUND` | Course, version, or prerequisite ID does not exist. |
| `409 Conflict` | Conflict | `ACD_COURSE_CODE_EXISTS` | Course code already exists within normalized scope. |
| `422 Unprocessable` | Unprocessable Entity | `ACD_PREREQUISITE_CYCLE` | Cycle detected in Directed Acyclic Graph of prerequisites. |
| `422 Unprocessable` | Unprocessable Entity | `ACD_INVALID_STATE` | Illegal status transition or direct in-place update of published course. |
| `422 Unprocessable` | Unprocessable Entity | `ACD_ACTIVE_BATCH_EXISTS` | Course deactivation blocked while active batch offerings exist. |
| `503 Unavailable` | Service Unavailable | `GATEWAY_SERVICE_UNAVAILABLE` | Gateway cannot reach downstream Course service on port 8083. |
| `504 Timeout` | Gateway Timeout | `GATEWAY_DOWNSTREAM_TIMEOUT` | Course service response exceeded 10,000ms read timeout. |

---

## 6. Sample Verification cURL Commands via Gateway

### 6.1 Create Course through Gateway
```bash
curl -X POST http://localhost:8080/api/v1/courses \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-GW-TEST-001" \
  -H "X-Tenant-Id: CAMPUS_MAIN" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "courseCode": "CS301",
    "courseName": "Database Systems",
    "departmentId": "DEP_CS",
    "durationYears": 1,
    "totalCredits": 4.0,
    "courseType": "THEORY"
  }'
```

### 6.2 Retrieve Published Course Catalog (Canonical Short Alias)
```bash
curl -X GET http://localhost:8080/v1/course-catalog \
  -H "X-Trace-Id: TRACE-GW-TEST-002"
```

### 6.3 Add Prerequisite through Gateway
```bash
curl -X POST http://localhost:8080/api/v1/courses/CS301/prerequisites \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-GW-TEST-003" \
  -d '{
    "prerequisiteCourseId": "CS101",
    "relationshipType": "MANDATORY"
  }'
```

### 6.4 Query Course Audit History through Gateway
```bash
curl -X GET http://localhost:8080/api/v1/courses/CS301/history \
  -H "X-Trace-Id: TRACE-GW-TEST-004" \
  -H "X-User-Role: AUDITOR"
```
