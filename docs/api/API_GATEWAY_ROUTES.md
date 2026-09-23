# CampXSync V2.0 - API Gateway Route Catalog & Endpoint Specification

> **Document Version**: 2.0.0  
> **Target Gateway**: `api-gateway` (`http://localhost:8080`)  
> **Architecture Reference**: Microservice Ingress Reverse Proxy & Correlation Router  
> **Status**: Production Ready  

---

## 1. Executive Summary & Gateway Overview

The **CampXSync API Gateway** functions as the unified reverse proxy, perimeter security layer, and correlation dispatcher for the entire CampXSync microservices ecosystem. It routes external client calls to the appropriate downstream services, manages distributed tracing headers, enforces resilience timeouts, and standardizes error responses according to the **RFC 7807 Problem Details** specification.

```
+-------------------------------------------------------------------------+
|                           External Clients                              |
|           (Web Applications, Mobile Apps, Third-Party Integrations)     |
+------------------------------------+------------------------------------+
                                     |
                                     | HTTP REST (JSON)
                                     v
+-------------------------------------------------------------------------+
|                        CampXSync API Gateway                            |
|                        Port: 8080 (Ingress)                             |
|                                                                         |
|  * Correlation Tracking (X-Trace-Id, X-Tenant-Id, X-User-Id)            |
|  * Context Dispatch & Route Resolution                                  |
|  * Reverse Proxy & Payload Forwarding                                   |
|  * Network Resilience (Connect: 5000ms, Read: 10000ms)                  |
|  * Downstream Error Pass-Through & RFC 7807 Fault Handling              |
|  * Health & Route Introspection (/actuator/health, /api/v1/gateway/routes)|
+--------------------+--------------------------------+--------------------+
                     |                                |
        /api/v1/admin/**              /api/v1/college-admin/**
        /v1/institutes/**             /v1/college-profile/**
        /v1/platform-configs/**       /v1/departments/**
        /v1/billing-accounts/**       /v1/programs/**
        /v1/platform-audit-logs/**    /v1/imports/**, /v1/documents/**
                     |                                |
                     v                                v
+------------------------------------+  +------------------------------------+
|  ADM-01 Institute Admin Service    |  |    ADM-02 College Admin Service    |
|  (Platform & Multi-Tenant Tier)    |  |     (College Operations Tier)      |
|  Target: http://localhost:8081     |  |   Target: http://localhost:8082    |
+------------------------------------+  +------------------------------------+
```

---

## 2. Gateway Route Registry Table

The API Gateway provides two route surfaces:
1. **Core Direct Ingress Routes**: Full path transparency mapping directly to downstream controller endpoints (`/api/v1/admin/*`, `/api/v1/college-admin/*`).
2. **Canonical Short Ingress Routes**: Clean, simplified public-facing aliases that resolve directly to the respective downstream microservice domain resources.

| # | Gateway Ingress Prefix | Target Downstream Base URL | Target Microservice | Service Port | Scope / Functional Domain |
|---|---|---|---|---|---|
| **GW-01** | `/api/v1/admin` | `http://localhost:8081/api/v1/admin` | ADM-01 Institute Admin | 8081 | Platform Tier (Direct Full Ingress) |
| **GW-02** | `/api/v1/college-admin` | `http://localhost:8082/api/v1/college-admin` | ADM-02 College Admin | 8082 | College Tier (Direct Full Ingress) |
| **GW-03** | `/v1/institutes` | `http://localhost:8081/api/v1/admin/institutes` | ADM-01 Institute Admin | 8081 | Multi-Tenant Institute Registry |
| **GW-04** | `/v1/platform-configs` | `http://localhost:8081/api/v1/admin/configuration` | ADM-01 Institute Admin | 8081 | Global System Configuration & Flags |
| **GW-05** | `/v1/platform-roles` | `http://localhost:8081/api/v1/admin/roles` | ADM-01 Institute Admin | 8081 | Platform Role Definitions & Permissions |
| **GW-06** | `/v1/billing-accounts` | `http://localhost:8081/api/v1/admin/billing/plans` | ADM-01 Institute Admin | 8081 | Commercial Plans & Billing |
| **GW-07** | `/v1/platform-audit-logs` | `http://localhost:8081/api/v1/admin/audit-logs` | ADM-01 Institute Admin | 8081 | Platform Audit Trail & Compliance |
| **GW-08** | `/v1/college-profile` | `http://localhost:8082/api/v1/college-admin/profile` | ADM-02 College Admin | 8082 | College Institutional Profile & Affiliation |
| **GW-09** | `/v1/departments` | `http://localhost:8082/api/v1/college-admin/departments` | ADM-02 College Admin | 8082 | Academic Departments Lifecycle |
| **GW-10** | `/v1/programs` | `http://localhost:8082/api/v1/college-admin/programs` | ADM-02 College Admin | 8082 | Academic Degrees & Programs Master |
| **GW-11** | `/v1/college-configs` | `http://localhost:8082/api/v1/college-admin/settings` | ADM-02 College Admin | 8082 | Campus Overrides & Academic Calendar |
| **GW-12** | `/v1/imports` | `http://localhost:8082/api/v1/college-admin/imports` | ADM-02 College Admin | 8082 | Bulk Data Migration & Ingestion Pipeline |
| **GW-13** | `/v1/documents` | `http://localhost:8082/api/v1/college-admin/documents` | ADM-02 College Admin | 8082 | Accreditation & Statutory Documents |
| **GW-14** | `/api/v1/courses` | `http://localhost:8083/api/v1/courses` | ACD-01 Course Management | 8083 | Academic Tier (Direct Full Ingress) |
| **GW-15** | `/api/v1/academics/courses` | `http://localhost:8083/api/v1/academics/courses` | ACD-01 Course Management | 8083 | Academic Tier (Namespace Ingress) |
| **GW-16** | `/v1/courses` | `http://localhost:8083/api/v1/courses` | ACD-01 Course Management | 8083 | Course Management Canonical Alias |
| **GW-17** | `/v1/course-catalog` | `http://localhost:8083/api/v1/courses/catalog` | ACD-01 Course Management | 8083 | Published Course Catalog Alias |

---

## 3. Direct Gateway Endpoints (Host-Managed)

These endpoints are handled directly by the API Gateway process itself without proxying downstream.

### 3.1 Health Probe
- **Endpoint**: `GET /actuator/health`
- **Purpose**: Kubernetes liveness/readiness probe and uptime monitor.
- **Response Code**: `200 OK`
- **Response Headers**: `Content-Type: application/json; charset=UTF-8`
- **Response Body**:
```json
{
  "status": "UP",
  "gateway": "CampXSync-API-Gateway"
}
```

### 3.2 Dynamic Route Registry Introspection
- **Endpoint**: `GET /api/v1/gateway/routes`
- **Purpose**: Exposes the live, active in-memory routing table for operational visibility and service mesh discovery.
- **Response Code**: `200 OK`
- **Response Headers**: `Content-Type: application/json; charset=UTF-8`
- **Response Body**:
```json
{
  "routes": [
    { "prefix": "/api/v1/admin", "target": "http://localhost:8081/api/v1/admin" },
    { "prefix": "/api/v1/college-admin", "target": "http://localhost:8082/api/v1/college-admin" },
    { "prefix": "/v1/institutes", "target": "http://localhost:8081/api/v1/admin/institutes" },
    { "prefix": "/v1/platform-configs", "target": "http://localhost:8081/api/v1/admin/configuration" },
    { "prefix": "/v1/platform-roles", "target": "http://localhost:8081/api/v1/admin/roles" },
    { "prefix": "/v1/billing-accounts", "target": "http://localhost:8081/api/v1/admin/billing/plans" },
    { "prefix": "/v1/platform-audit-logs", "target": "http://localhost:8081/api/v1/admin/audit-logs" },
    { "prefix": "/v1/college-profile", "target": "http://localhost:8082/api/v1/college-admin/profile" },
    { "prefix": "/v1/departments", "target": "http://localhost:8082/api/v1/college-admin/departments" },
    { "prefix": "/v1/programs", "target": "http://localhost:8082/api/v1/college-admin/programs" },
    { "prefix": "/v1/college-configs", "target": "http://localhost:8082/api/v1/college-admin/settings" },
    { "prefix": "/v1/imports", "target": "http://localhost:8082/api/v1/college-admin/imports" },
    { "prefix": "/v1/documents", "target": "http://localhost:8082/api/v1/college-admin/documents" }
  ]
}
```

---

## 4. Downstream Endpoint Inventory & API Specifications

All downstream microservice endpoints are fully accessible through the API Gateway at port `8080`.

### 4.1 ADM-01 Institute Admin Service Endpoints (`http://localhost:8081`)

#### 1. Register Institute
- **Gateway Path**: `POST /api/v1/admin/institutes` or `POST /v1/institutes`
- **Target**: `POST http://localhost:8081/api/v1/admin/institutes`
- **Request Body**:
```json
{
  "instituteCode": "INST_VIT_001",
  "legalName": "Vellore Institute of Technology",
  "displayName": "VIT Campus",
  "timezone": "Asia/Kolkata",
  "locale": "en_IN",
  "defaultCurrency": "INR"
}
```
- **Response**: `201 Created`
```json
{
  "id": "60d5ec49f1b2c82b8c8e4e10",
  "instituteCode": "INST_VIT_001",
  "legalName": "Vellore Institute of Technology",
  "displayName": "VIT Campus",
  "status": "ACTIVE",
  "version": 1
}
```

#### 2. List All Institutes
- **Gateway Path**: `GET /api/v1/admin/institutes` or `GET /v1/institutes`
- **Target**: `GET http://localhost:8081/api/v1/admin/institutes`
- **Response**: `200 OK`
```json
{
  "institutes": [
    {
      "id": "60d5ec49f1b2c82b8c8e4e10",
      "code": "INST_VIT_001",
      "name": "VIT Campus",
      "status": "ACTIVE"
    }
  ]
}
```

#### 3. Update Institute Metadata
- **Gateway Path**: `PUT /api/v1/admin/institutes/{id}` or `PUT /v1/institutes/{id}`
- **Target**: `PUT http://localhost:8081/api/v1/admin/institutes/{id}`
- **Request Body**:
```json
{
  "displayName": "VIT Deemed University",
  "status": "ACTIVE",
  "timezone": "Asia/Kolkata"
}
```
- **Response**: `200 OK`
```json
{
  "status": "UPDATED",
  "version": 2,
  "instituteId": "60d5ec49f1b2c82b8c8e4e10"
}
```

#### 4. Register College Under Institute
- **Gateway Path**: `POST /api/v1/admin/colleges`
- **Target**: `POST http://localhost:8081/api/v1/admin/colleges`
- **Request Body**:
```json
{
  "collegeCode": "COL_ENGG_01",
  "name": "School of Computer Science & Engineering",
  "instituteId": "60d5ec49f1b2c82b8c8e4e10"
}
```
- **Response**: `201 Created`
```json
{
  "id": "60d5ec49f1b2c82b8c8e4e11",
  "collegeCode": "COL_ENGG_01",
  "status": "ACTIVE"
}
```

#### 5. Trigger Tenant Provisioning
- **Gateway Path**: `POST /api/v1/admin/tenants/{tenantId}/provision`
- **Target**: `POST http://localhost:8081/api/v1/admin/tenants/{tenantId}/provision`
- **Headers**: `Idempotency-Key: IDEMP-20260922-001`
- **Request Body**:
```json
{
  "targetScope": "CAMPUS_MAIN",
  "planId": "ENTERPRISE_CAMPUS_2026",
  "idempotencyKey": "IDEMP-20260922-001"
}
```
- **Response**: `200 OK`
```json
{
  "provisioningId": "PROV_TENANT_VIT_CAMPUS_01",
  "tenantId": "VIT_CAMPUS",
  "status": "COMPLETED"
}
```

#### 6. List Tenant Provisioning Jobs
- **Gateway Path**: `GET /api/v1/admin/tenants/provisioning`
- **Target**: `GET http://localhost:8081/api/v1/admin/tenants/provisioning`
- **Response**: `200 OK`
```json
{
  "jobs": [
    {
      "id": "PROV_TENANT_VIT_CAMPUS_01",
      "tenant": "VIT_CAMPUS",
      "status": "COMPLETED"
    }
  ]
}
```

#### 7. Set Global System Configuration
- **Gateway Path**: `POST /api/v1/admin/configuration` or `POST /v1/platform-configs`
- **Target**: `POST http://localhost:8081/api/v1/admin/configuration`
- **Request Body**:
```json
{
  "key": "platform.auth.mfa.enforced",
  "value": "true",
  "dataType": "BOOLEAN",
  "scope": "GLOBAL",
  "isSecret": "false"
}
```
- **Response**: `200 OK`
```json
{
  "status": "SAVED",
  "key": "platform.auth.mfa.enforced"
}
```

#### 8. Retrieve Global System Configuration
- **Gateway Path**: `GET /api/v1/admin/configuration` or `GET /v1/platform-configs`
- **Target**: `GET http://localhost:8081/api/v1/admin/configuration`
- **Response**: `200 OK`
```json
{
  "configuration": [
    {
      "key": "platform.auth.mfa.enforced",
      "value": "true"
    }
  ]
}
```

#### 9. Query Commercial Billing Plans
- **Gateway Path**: `GET /api/v1/admin/billing/plans` or `GET /v1/billing-accounts`
- **Target**: `GET http://localhost:8081/api/v1/admin/billing/plans`
- **Response**: `200 OK`
```json
{
  "plans": [
    {
      "code": "ENTERPRISE_TIER_1",
      "name": "CampX Enterprise Multi-Campus Plan",
      "price": 1250000.0
    }
  ]
}
```

#### 10. Query Platform Audit Trail
- **Gateway Path**: `GET /api/v1/admin/audit-logs` or `GET /v1/platform-audit-logs`
- **Target**: `GET http://localhost:8081/api/v1/admin/audit-logs`
- **Response**: `200 OK`
```json
{
  "auditLogs": [
    {
      "action": "INSTITUTE_CREATED",
      "entity": "INSTITUTE",
      "entityId": "INST_VIT_001",
      "actor": "admin@campx.edu",
      "timestamp": 1789972800000
    }
  ]
}
```

---

### 4.2 ADM-02 College Admin Service Endpoints (`http://localhost:8082`)

#### 1. Retrieve College Profile
- **Gateway Path**: `GET /api/v1/college-admin/profile` or `GET /v1/college-profile`
- **Target**: `GET http://localhost:8082/api/v1/college-admin/profile`
- **Response**: `200 OK`
```json
{
  "code": "COL_ENGG_01",
  "name": "College of Engineering and Technology",
  "status": "ACTIVE",
  "affiliation": "NAAC_A_PLUS",
  "university": "State Technical University"
}
```

#### 2. Update College Profile
- **Gateway Path**: `POST /api/v1/college-admin/profile` or `PUT /v1/college-profile`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/profile`
- **Request Body**:
```json
{
  "legalName": "College of Engineering and Applied Sciences",
  "affiliation": "NAAC_A_PLUS_PLUS",
  "university": "State Technical University"
}
```
- **Response**: `200 OK`
```json
{
  "status": "UPDATED",
  "code": "COL_ENGG_01",
  "updatedAt": 1789972850000
}
```

#### 3. Create Academic Department
- **Gateway Path**: `POST /api/v1/college-admin/departments` or `POST /v1/departments`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/departments`
- **Request Body**:
```json
{
  "departmentCode": "DEP_CSE_01",
  "name": "Computer Science and Engineering",
  "hodName": "Dr. Sarah Jenkins",
  "capacity": 240
}
```
- **Response**: `201 Created`
```json
{
  "id": "60d5ec49f1b2c82b8c8e4e20",
  "departmentCode": "DEP_CSE_01",
  "status": "ACTIVE"
}
```

#### 4. List Academic Departments
- **Gateway Path**: `GET /api/v1/college-admin/departments` or `GET /v1/departments`
- **Target**: `GET http://localhost:8082/api/v1/college-admin/departments`
- **Response**: `200 OK`
```json
{
  "departments": [
    {
      "id": "60d5ec49f1b2c82b8c8e4e20",
      "code": "DEP_CSE_01",
      "name": "Computer Science and Engineering",
      "status": "ACTIVE"
    }
  ]
}
```

#### 5. Retire / Deactivate Department
- **Gateway Path**: `DELETE /api/v1/college-admin/departments/{id}` or `DELETE /v1/departments/{id}`
- **Target**: `DELETE http://localhost:8082/api/v1/college-admin/departments/{id}`
- **Response**: `200 OK`
```json
{
  "status": "DEACTIVATED",
  "departmentId": "60d5ec49f1b2c82b8c8e4e20"
}
```

#### 6. Create Academic Program / Degree
- **Gateway Path**: `POST /api/v1/college-admin/programs` or `POST /v1/programs`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/programs`
- **Request Body**:
```json
{
  "programCode": "PROG_BTECH_CSE",
  "name": "Bachelor of Technology in Computer Science",
  "degreeLevel": "UNDERGRADUATE",
  "departmentId": "60d5ec49f1b2c82b8c8e4e20",
  "totalCredits": 160
}
```
- **Response**: `201 Created`
```json
{
  "id": "60d5ec49f1b2c82b8c8e4e30",
  "programCode": "PROG_BTECH_CSE",
  "status": "ACTIVE"
}
```

#### 7. List Academic Programs
- **Gateway Path**: `GET /api/v1/college-admin/programs` or `GET /v1/programs`
- **Target**: `GET http://localhost:8082/api/v1/college-admin/programs`
- **Response**: `200 OK`
```json
{
  "programs": [
    {
      "id": "60d5ec49f1b2c82b8c8e4e30",
      "code": "PROG_BTECH_CSE",
      "name": "Bachelor of Technology in Computer Science",
      "status": "ACTIVE"
    }
  ]
}
```

#### 8. Submit Bulk Data Import Job
- **Gateway Path**: `POST /api/v1/college-admin/imports` or `POST /v1/imports`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/imports`
- **Request Body**:
```json
{
  "importType": "STUDENT_MASTER",
  "sourceFileName": "students_batch_2026.csv",
  "totalRecords": 450,
  "dryRun": false
}
```
- **Response**: `202 Accepted` / `201 Created`
```json
{
  "importJobId": "IMP_JOB_20260922_001",
  "status": "QUEUED",
  "type": "STUDENT_MASTER"
}
```

#### 9. Query Bulk Import Job Status
- **Gateway Path**: `GET /api/v1/college-admin/imports/{id}` or `GET /v1/imports/{id}`
- **Target**: `GET http://localhost:8082/api/v1/college-admin/imports/{id}`
- **Response**: `200 OK`
```json
{
  "importJobId": "IMP_JOB_20260922_001",
  "status": "COMPLETED",
  "processedRecords": 450,
  "failedRecords": 0
}
```

#### 10. Register Governance Document
- **Gateway Path**: `POST /api/v1/college-admin/documents` or `POST /v1/documents`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/documents`
- **Request Body**:
```json
{
  "docCode": "DOC_NAAC_SSR_2026",
  "title": "NAAC Self Study Report 2026",
  "category": "ACCREDITATION",
  "classificationLevel": "CONFIDENTIAL"
}
```
- **Response**: `201 Created`
```json
{
  "documentId": "DOC_NAAC_SSR_2026",
  "status": "DRAFT",
  "classification": "CONFIDENTIAL"
}
```

#### 11. List Governance Documents
- **Gateway Path**: `GET /api/v1/college-admin/documents` or `GET /v1/documents`
- **Target**: `GET http://localhost:8082/api/v1/college-admin/documents`
- **Response**: `200 OK`
```json
{
  "documents": [
    {
      "id": "DOC_NAAC_SSR_2026",
      "title": "NAAC Self Study Report 2026",
      "status": "DRAFT"
    }
  ]
}
```

#### 12. Submit / Approve Document
- **Gateway Path**: `POST /api/v1/college-admin/documents/{id}/submit` or `POST /v1/documents/{id}/submit`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/documents/{id}/submit`
- **Response**: `200 OK`
```json
{
  "documentId": "DOC_NAAC_SSR_2026",
  "status": "APPROVED",
  "approvedAt": 1789972900000
}
```

#### 13. Query College Audit Trail
- **Gateway Path**: `GET /api/v1/college-admin/audit-logs`
- **Target**: `GET http://localhost:8082/api/v1/college-admin/audit-logs`
- **Response**: `200 OK`
```json
{
  "auditLogs": [
    {
      "action": "DEPARTMENT_CREATED",
      "department": "DEP_CSE_01",
      "actor": "principal@college.edu",
      "timestamp": 1789972910000
    }
  ]
}
```

---

### 4.3 ACD-01 Course Management Service Endpoints (`http://localhost:8083`)

#### 1. Create Draft Course
- **Gateway Path**: `POST /api/v1/courses` or `POST /v1/courses`
- **Target**: `POST http://localhost:8083/api/v1/courses`
- **Request Body**:
```json
{
  "courseCode": "CS201",
  "courseName": "Data Structures and Algorithms",
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

#### 2. Query Published Course Catalog
- **Gateway Path**: `GET /api/v1/courses/catalog` or `GET /v1/course-catalog`
- **Target**: `GET http://localhost:8083/api/v1/courses/catalog`
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

#### 3. Search and Filter Courses
- **Gateway Path**: `GET /api/v1/courses/search?departmentId=DEP_CS&keyword=data`
- **Target**: `GET http://localhost:8083/api/v1/courses/search?departmentId=DEP_CS&keyword=data`
- **Response**: `200 OK`

#### 4. Add Course Prerequisite (with DAG Cycle Prevention)
- **Gateway Path**: `POST /api/v1/courses/{id}/prerequisites`
- **Target**: `POST http://localhost:8083/api/v1/courses/{id}/prerequisites`
- **Request Body**:
```json
{
  "prerequisiteCourseId": "CRS_CS101",
  "relationshipType": "MANDATORY",
  "minimumGrade": "C"
}
```
- **Response**: `201 Created` / `422 Unprocessable Entity` (if cycle detected)

#### 5. Course Lifecycle Governance
- **Gateway Path**:
  - `POST /api/v1/courses/{id}/submit` &rarr; Transition to `UNDER_REVIEW`
  - `POST /api/v1/courses/{id}/approve` &rarr; Transition to `APPROVED`
  - `POST /api/v1/courses/{id}/publish` &rarr; Transition to `ACTIVE`
  - `POST /api/v1/courses/{id}/deactivate` &rarr; Transition to `DEACTIVATED` (Guarded: blocked if active batch offerings exist)
  - `POST /api/v1/courses/{id}/archive` &rarr; Transition to `ARCHIVED`
- **Response**: `200 OK`

#### 6. Course Credits & Policies
- **Gateway Path**: `GET /api/v1/courses/{id}/credits`
- **Target**: `GET http://localhost:8083/api/v1/courses/{id}/credits`
- **Response**: `200 OK`
```json
{
  "courseId": "CRS_CS101",
  "courseCode": "CS101",
  "totalCredits": 4.0,
  "internalWeightage": 40.0,
  "externalWeightage": 60.0
}
```

---

## 5. Gateway Filter & Header Propagation Specification

The gateway utilizes `CorrelationFilter` to inspect and forward distributed context tokens across microservice boundaries.

```
Client Request
      |
      | Headers: [X-Trace-Id], [X-Tenant-Id], [X-User-Id], [X-User-Role]
      v
[CorrelationFilter]
      |
      |-- 1. Check X-Trace-Id: If missing, generate UUID (e.g. 9b1deb4d3b7d4bad9bdd2b0d7b3dcb6d)
      |-- 2. Bind traceId, tenantId, userId, userRole to LogContext
      |-- 3. Set X-Trace-Id header on Gateway Response
      |-- 4. Forward all request headers (excluding Host, Content-Length) downstream
      v
[ReverseProxyHandler] -> Downstream Service (8081 / 8082)
```

### 5.1 Propagated Correlation Headers

| Header Name | Type | Generation Rule | Downstream Consumption |
|---|---|---|---|
| `X-Trace-Id` | String (Hex/UUID) | Inherited from client if present; generated via `UUID.randomUUID()` if missing. | Injected into MDC/LogContext, forwarded in responses, recorded in all audit entries. |
| `X-Tenant-Id` | String | Extracted from client request header. | Enforces multi-tenant data isolation at the MongoDB collection / document level. |
| `X-User-Id` | String | Extracted from client authentication token. | Tied to entity creation, audits, and operational changes. |
| `X-User-Role` | String | Extracted from role authorization claims. | Inspected for RBAC authorization checks in domain services. |
| `Idempotency-Key` | String | Preserved directly on POST/PUT requests. | Prevents duplicate tenant provisioning or billing execution. |

---

## 6. Resilience, Timeout & RFC 7807 Error Catalog

The API Gateway enforces strict connection and read timeouts to prevent thread exhaustion and cascading failures:
- **Connection Timeout**: `5,000 ms`
- **Read Timeout**: `10,000 ms`

### 6.1 Gateway-Generated Error Statuses

| HTTP Status | Error Code | Trigger Condition | Example Payload |
|---|---|---|---|
| `404 Not Found` | `GATEWAY_ROUTE_NOT_FOUND` | Path does not match any prefix registered in `GatewayConfig`. | `{"status":404,"error":"Not Found","errorCode":"GATEWAY_ROUTE_NOT_FOUND","message":"No route registered for request path: /v1/invalid"}` |
| `502 Bad Gateway` | `GATEWAY_PROXY_ERROR` | Unexpected proxy I/O or connection reset during streaming. | `{"status":502,"error":"Bad Gateway","errorCode":"GATEWAY_PROXY_ERROR","message":"Gateway proxy failure: Connection reset"}` |
| `503 Service Unavailable` | `GATEWAY_SERVICE_UNAVAILABLE` | Downstream service process is offline or unreachable (`ConnectException`). | `{"status":503,"error":"Service Unavailable","errorCode":"GATEWAY_SERVICE_UNAVAILABLE","message":"Downstream microservice unreachable: Connection refused"}` |
| `504 Gateway Timeout` | `GATEWAY_DOWNSTREAM_TIMEOUT` | Downstream service took longer than 10,000 ms to respond (`SocketTimeoutException`). | `{"status":504,"error":"Gateway Timeout","errorCode":"GATEWAY_DOWNSTREAM_TIMEOUT","message":"Downstream microservice timed out: Read timed out"}` |

### 6.2 Transparent Downstream Error Pass-Through

When a downstream microservice raises a domain validation or conflict error (e.g. 400 Bad Request, 409 Conflict, 422 Unprocessable Entity), the Gateway:
1. **Preserves the exact HTTP status code** emitted by the downstream service.
2. **Streams the complete downstream response payload** without modification.
3. **Appends the correlation `X-Trace-Id`** to the response headers.

*Example (409 Conflict propagated transparently from Institute Admin Service):*
```json
{
  "timestamp": 1789972920150,
  "status": 409,
  "error": "Conflict",
  "errorCode": "ADM01_DUPLICATE_RESOURCE",
  "message": "Institute already exists with code: INST_VIT_001",
  "path": "/api/v1/admin/institutes",
  "traceId": "9b1deb4d3b7d4bad9bdd2b0d7b3dcb6d"
}
```

---

## 7. Operational Testing & Sample cURL Commands

### 7.1 Verify Gateway Liveness
```bash
curl -X GET http://localhost:8080/actuator/health
```

### 7.2 Inspect Active Route Registry
```bash
curl -X GET http://localhost:8080/api/v1/gateway/routes
```

### 7.3 Register Institute via Gateway (Direct Ingress)
```bash
curl -X POST http://localhost:8080/api/v1/admin/institutes \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-001" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -d '{
    "instituteCode": "INST_VIT_001",
    "legalName": "Vellore Institute of Technology",
    "displayName": "VIT Campus",
    "timezone": "Asia/Kolkata",
    "locale": "en_IN",
    "defaultCurrency": "INR"
  }'
```

### 7.4 Retrieve College Profile via Gateway (Short Alias)
```bash
curl -X GET http://localhost:8080/v1/college-profile \
  -H "X-Trace-Id: TRACE-TEST-002" \
  -H "X-Tenant-Id: VIT_CAMPUS"
```

### 7.5 Create Academic Department via Gateway
```bash
curl -X POST http://localhost:8080/v1/departments \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-003" \
  -d '{
    "departmentCode": "DEP_MECH_01",
    "name": "Department of Mechanical Engineering",
    "hodName": "Dr. Robert Vance",
    "capacity": 180
  }'
```
