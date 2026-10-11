# ACD-02 Curriculum Management Service - API Gateway Specification & Route Catalog

> **Service Identifier**: `ACD-02` (Curriculum Management Service)  
> **Service Port**: `8084`  
> **Gateway Ingress Port**: `8080` (`http://localhost:8080`)  
> **Document Location**: `services/curriculum-service/API_GATEWAY_ROUTES.md`  
> **Status**: Production Ready  

---

## 1. Gateway Routing Architecture & Ingress Topology

The **CampXSync API Gateway** (Port 8080) acts as the unified reverse proxy, security perimeter, and correlation token dispatcher for the **ACD-02 Curriculum Management Service** (Port 8084). All external calls from web portals, mobile apps, and upstream microservices flow through the API Gateway.

```
+-------------------------------------------------------------------------------+
|                             External Clients                                  |
|         (Academic Admin Portal, Student App, Faculty Portal, APIs)           |
+---------------------------------------+---------------------------------------+
                                        |
                                        | HTTP REST (JSON)
                                        v
+-------------------------------------------------------------------------------+
|                            CampXSync API Gateway                              |
|                            Port: 8080 (Ingress)                               |
|                                                                               |
|  * Correlation Extraction & Injection (X-Trace-Id, X-Tenant-Id, X-User-Role)  |
|  * Reverse Proxy Route Resolution                                             |
|  * Timeouts & Resilience (5000ms Connect, 10000ms Read)                       |
|  * Downstream RFC 7807 & Standard Envelope Error Pass-Through                 |
+---------------------------------------+---------------------------------------+
                                        |
      /api/v1/curricula/**              |  /v1/curricula/**
      /api/v1/academics/curricula/**    |  /v1/curriculum-catalog
                                        |
                                        v Reverse Proxy
+-------------------------------------------------------------------------------+
|             ACD-02: Curriculum Management Service (Port 8084)                 |
|                                                                               |
|  * Authoritative Curriculum Aggregate & Scope Consistency (BR-05, BR-13)      |
|  * Immutable Published Versions & Historical Reconstruction (BR-01, BR-02)     |
|  * Semester & Subject Mapping Governance (BR-03, BR-06, BR-07, BR-08)          |
|  * Credit Policy Validation (BR-09) & Syllabus Module Structuring              |
|  * Learning Outcomes (CO/PO) & Prerequisite DAG Cycle Detection (BR-10)       |
|  * Governed Approval Workflow (Submit -> Review -> Approve -> Publish)         |
|  * Transactional Outbox Engine & Inbound Event Consumption                     |
|  * Granular RBAC + Department Head ABAC Scoping                               |
+-------------------------------------------------------------------------------+
```

---

## 2. Gateway Route Registry Table for ACD-02

| Route ID | Ingress Path (Port 8080) | Downstream Target (Port 8084) | Method(s) | Description / Capability |
|---|---|---|---|---|
| **GW-ACD02-01** | `/api/v1/academics/curricula` | `http://localhost:8084/api/v1/academics/curricula` | `POST`, `GET` | Core Curriculum Ingress (Create & Search) |
| **GW-ACD02-02** | `/api/v1/academics/curricula/*` | `http://localhost:8084/api/v1/academics/curricula/*` | `GET`, `PUT`, `POST`, `DELETE` | Versions, Semesters, Subjects, Syllabus, Workflow |
| **GW-ACD02-03** | `/api/v1/curricula` | `http://localhost:8084/api/v1/curricula` | `POST`, `GET` | Curriculum Aggregate Alias |
| **GW-ACD02-04** | `/api/v1/curricula/*` | `http://localhost:8084/api/v1/curricula/*` | `ANY` | Sub-resource and lifecycle operations |
| **GW-ACD02-05** | `/v1/curricula` | `http://localhost:8084/api/v1/curricula` | `POST`, `GET` | Canonical Short Gateway Alias for Curricula Master |
| **GW-ACD02-06** | `/v1/curriculum-catalog` | `http://localhost:8084/api/v1/curricula/active` | `GET` | Public / Student Canonical Short Alias for Active Catalog |
| **GW-ACD02-07** | `/api/v1/active` | `http://localhost:8084/api/v1/curricula/active` | `GET` | Active Curricula Catalog Ingress |

---

## 3. RFC 7807 & Standard Error Code Catalog for ACD-02

| Status Code | Error Code (`errorCode`) | Cause / Trigger |
|---|---|---|
| `404 Not Found` | `ACD2_CURRICULUM_NOT_FOUND` | Curriculum, version, or mapping ID does not exist |
| `422 Unprocessable` | `ACD2_COURSE_INVALID` | Referenced courseId does not exist or is inactive in ACD-01 (BR-05) |
| `422 Unprocessable` | `ACD2_SUBJECT_INVALID` | Referenced subjectId does not exist or is inactive in ACD-03 (BR-03, BR-06) |
| `409 Conflict` | `ACD2_VERSION_IMMUTABLE` | Mutation attempted on published immutable version (BR-02, BR-12) |
| `409 Conflict` | `ACD2_VERSION_CONFLICT` | Stale optimistic version reference submitted (concurrency collision) |
| `409 Conflict` | `ACD2_PUBLICATION_BLOCKED` | Publication blocked: missing approval, credit violation, or deactivated subject |
| `409 Conflict` | `ACD2_DUPLICATE_MAPPING` | Duplicate subject mapped to same semester (BR-08) or duplicate curriculum |
| `422 Unprocessable` | `ACD2_CREDIT_POLICY` | Aggregated total credits violate policy min/max bounds (BR-09) |
| `422 Unprocessable` | `ACD2_PREREQUISITE_CYCLE` | Proposed prerequisite creates a directed cycle in prerequisite graph (BR-10) |
| `403 Forbidden` | `ACD2_FORBIDDEN` | Missing RBAC permission or Department Head accessing different department |
| `403/422 Error` | `ACD2_TENANT_MISMATCH` | Scope mismatch between curriculum, campus, and referenced course (BR-13) |
| `503 Unavailable` | `ACD2_DEPENDENCY_UNAVAILABLE`| Downstream dependency (broker, ACD-01/03) unavailable |

---

## 4. Sample Verification cURL Commands via Gateway

### 4.1 Create Draft Curriculum
```bash
curl -X POST http://localhost:8080/api/v1/academics/curricula \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-GW-CURR-001" \
  -H "X-Tenant-Id: TENANT-001" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "courseId": "COURSE-001",
    "academicPattern": "CBCS",
    "academicYear": "2026-2027",
    "departmentId": "DEPT-CA",
    "campusId": "MAIN",
    "name": "Master of Computer Applications 2026-27"
  }'
```

### 4.2 Map Subject to Semester
```bash
curl -X POST http://localhost:8080/api/v1/academics/curricula/CURR-12345/versions/1/subjects \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: TENANT-001" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "subjectId": "SUB-101",
    "semesterNo": 1,
    "credits": 4.0,
    "contactHours": 60.0,
    "mandatory": true
  }'
```

### 4.3 Submit for Approval & Publish
```bash
# Submit
curl -X POST http://localhost:8080/api/v1/academics/curricula/CURR-12345/submit \
  -H "X-User-Role: ACADEMIC_ADMIN"

# Approve
curl -X POST http://localhost:8080/api/v1/academics/curricula/CURR-12345/approve \
  -H "X-User-Role: REGISTRAR"

# Publish
curl -X POST http://localhost:8080/api/v1/academics/curricula/CURR-12345/publish \
  -H "X-User-Role: REGISTRAR"
```

### 4.4 Retrieve Active Catalog (Canonical Short Alias)
```bash
curl -X GET http://localhost:8080/v1/curriculum-catalog \
  -H "X-Trace-Id: TRACE-GW-CATALOG-001"
```
