# CampXSync V2.0 - API Gateway Route Catalog & Endpoint Specification

> **Document Version**: 2.0.0  
> **Target Gateway**: `api-gateway` (`http://localhost:8080`)  
> **Architecture Reference**: Microservice Ingress Reverse Proxy & Correlation Router  
> **Status**: Production Ready  

---

## 1. Executive Summary & Gateway Overview

The **CampXSync API Gateway** functions as the unified reverse proxy, perimeter security layer, and correlation dispatcher for the entire CampXSync microservices ecosystem. It routes external client calls to the appropriate downstream services, manages distributed tracing headers, enforces resilience timeouts, and standardizes error responses according to the **RFC 7807 Problem Details** specification.

```
+---------------------------------------------------------------------------------------------------+
|                                        External Clients                                           |
|                     (Web Applications, Mobile Apps, Third-Party Integrations)                     |
+-------------------------------------------------+-------------------------------------------------+
                                                  |
                                                  | HTTP REST (JSON) / Metrics Ingress
                                                  v
+---------------------------------------------------------------------------------------------------+
|                                       CampXSync API Gateway                                       |
|                                       Port: 8080 (Ingress)                                        |
|                                                                                                   |
|  * Correlation Tracking (X-Trace-Id, X-Tenant-Id, X-User-Id, X-User-Role, X-Forwarded-For)        |
|  * Longest-Prefix Match (LPM) Dynamic Route Resolution                                            |
|  * Reverse Proxy Streaming & Payload Forwarding                                                   |
|  * Network Resilience (Connect: 5,000ms, Read: 10,000ms)                                          |
|  * Downstream Error Pass-Through & RFC 7807 Fault Handling                                        |
|  * Liveness & Route Introspection (/actuator/health, /api/v1/gateway/routes)                       |
+--------+------------------+-------------------+-------------------+-------------------+---------------+
         |                  |                   |                   |                   |               |
    /api/v1/admin/**   /api/v1/college-    /api/v1/courses/**  /api/v1/curricula/**/api/v1/subjects//api/v1/batches
    /v1/institutes/**    admin/**          /v1/courses/**      /api/v1/academics/  /v1/subjects/**  /v1/batches/**
    /v1/platform-**    /v1/college-**      /v1/course-catalog    curricula/**      /v1/subject-     /v1/batch-catalog
    /v1/billing-**     /v1/departments/**                      /v1/curricula/**      catalog        /metrics
    /v1/audit-logs     /v1/programs/**                         /v1/curriculum-catalog
         |                  |                   |                   |                   |               |
         v                  v                   v                   v                   v               v
+------------------+ +------------------+ +------------------+ +------------------+ +---------------+ +---------------+
| ADM-01 Institute | |  ADM-02 College  | |  ACD-01 Course   | | ACD-02 Curriculum| | ACD-03 Subject| | ACD-04 Batch  |
|  Admin Service   | |  Admin Service   | |Management Service| |Management Service| |Management Svc | |Management Svc |
|  (Platform Tier) | |  (College Tier)  | |  (Academic Tier) | |  (Academic Tier) | |(Academic Tier)| |(Academic Tier)|
| Port: 8081       | | Port: 8082       | | Port: 8083       | | Port: 8084       | | Port: 8085    | | Port: 8086    |
+------------------+ +------------------+ +------------------+ +------------------+ +---------------+ +---------------+
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
| **GW-18** | `/api/v1/curricula/metrics` | `http://localhost:8084/metrics` | ACD-02 Curriculum Management | 8084 | Prometheus Metrics Endpoint |
| **GW-19** | `/api/v1/curricula` | `http://localhost:8084/api/v1/curricula` | ACD-02 Curriculum Management | 8084 | Academic Tier (Direct Full Ingress) |
| **GW-20** | `/api/v1/academics/curricula` | `http://localhost:8084/api/v1/academics/curricula` | ACD-02 Curriculum Management | 8084 | Academic Tier (Namespace Ingress) |
| **GW-21** | `/api/v1/active` | `http://localhost:8084/api/v1/curricula/active` | ACD-02 Curriculum Management | 8084 | Active Curriculum Ingress |
| **GW-22** | `/v1/curricula` | `http://localhost:8084/api/v1/curricula` | ACD-02 Curriculum Management | 8084 | Curriculum Management Canonical Alias |
| **GW-23** | `/v1/curriculum-catalog` | `http://localhost:8084/api/v1/curricula/active` | ACD-02 Curriculum Management | 8084 | Published Curriculum Catalog Alias |
| **GW-24** | `/api/v1/subjects/metrics` | `http://localhost:8085/metrics` | ACD-03 Subject Management | 8085 | Prometheus Metrics Endpoint |
| **GW-25** | `/api/v1/academics/subjects/metrics` | `http://localhost:8085/metrics` | ACD-03 Subject Management | 8085 | Prometheus Metrics Endpoint |
| **GW-26** | `/api/v1/subjects` | `http://localhost:8085/api/v1/subjects` | ACD-03 Subject Management | 8085 | Academic Tier (Direct Full Ingress) |
| **GW-27** | `/api/v1/academics/subjects` | `http://localhost:8085/api/v1/academics/subjects` | ACD-03 Subject Management | 8085 | Academic Tier (Namespace Ingress) |
| **GW-28** | `/v1/subjects` | `http://localhost:8085/api/v1/subjects` | ACD-03 Subject Management | 8085 | Subject Management Canonical Alias |
| **GW-29** | `/v1/subject-catalog` | `http://localhost:8085/api/v1/academics/subjects/catalog` | ACD-03 Subject Management | 8085 | Published Subject Catalog Alias |
| **GW-30** | `/v1/college-workflows` | `http://localhost:8082/api/v1/college-admin/workflows` | ADM-02 College Admin | 8082 | Cross-Module Governance Workflows |
| **GW-31** | `/v1/batch-approvals` | `http://localhost:8082/api/v1/college-admin/workflows/batch-approvals` | ADM-02 College Admin | 8082 | Cross-Module Batch Split/Merge Approvals (ACD-04) |
| **GW-32** | `/v1/college-approvals` | `http://localhost:8082/api/v1/college-admin/approvals` | ADM-02 College Admin | 8082 | Academic Governance Approval Requests |
| **GW-33** | `/v1/college-roles` | `http://localhost:8082/api/v1/college-admin/roles` | ADM-02 College Admin | 8082 | College Role Master (with Separation of Duties) |
| **GW-34** | `/v1/college-permissions` | `http://localhost:8082/api/v1/college-admin/permissions` | ADM-02 College Admin | 8082 | College Permissions Master (incl. Cross-Module Codes) |
| **GW-35** | `/v1/college-audit-logs` | `http://localhost:8082/api/v1/college-admin/audit-logs` | ADM-02 College Admin | 8082 | Cryptographic Tamper-Evident Audit Trail |
| **GW-36** | `/api/v1/batches/metrics` | `http://localhost:8086/metrics` | ACD-04 Batch Management | 8086 | Prometheus Metrics Endpoint |
| **GW-37** | `/api/v1/academics/batches/metrics` | `http://localhost:8086/metrics` | ACD-04 Batch Management | 8086 | Prometheus Metrics Endpoint |
| **GW-38** | `/api/v1/batches` | `http://localhost:8086/api/v1/academics/batches` | ACD-04 Batch Management | 8086 | Academic Tier (Direct Full Ingress) |
| **GW-39** | `/api/v1/academics/batches` | `http://localhost:8086/api/v1/academics/batches` | ACD-04 Batch Management | 8086 | Academic Tier (Namespace Ingress) |
| **GW-40** | `/v1/batches` | `http://localhost:8086/api/v1/academics/batches` | ACD-04 Batch Management | 8086 | Batch Management Canonical Alias |
| **GW-41** | `/v1/batch-catalog` | `http://localhost:8086/api/v1/academics/batches` | ACD-04 Batch Management | 8086 | Published Batch Catalog Alias |
| **GW-42** | `/api/v1/timetables/metrics` | `http://localhost:8087/metrics` | ACD-05 Timetable Management | 8087 | Prometheus Metrics Endpoint |
| **GW-43** | `/api/v1/academics/timetables/metrics` | `http://localhost:8087/metrics` | ACD-05 Timetable Management | 8087 | Prometheus Metrics Endpoint |
| **GW-44** | `/api/v1/timetables` | `http://localhost:8087/api/v1/academics/timetables` | ACD-05 Timetable Management | 8087 | Academic Tier (Direct Ingress) |
| **GW-45** | `/api/v1/academics/timetables` | `http://localhost:8087/api/v1/academics/timetables` | ACD-05 Timetable Management | 8087 | Academic Tier (Namespace Ingress) |
| **GW-46** | `/api/v1/export` | `http://localhost:8087/api/v1/academics/timetables/export` | ACD-05 Timetable Management | 8087 | Timetable Export Ingress |
| **GW-47** | `/v1/timetables` | `http://localhost:8087/api/v1/academics/timetables` | ACD-05 Timetable Management | 8087 | Timetable Management Canonical Alias |
| **GW-48** | `/v1/timetable-catalog` | `http://localhost:8087/api/v1/academics/timetables` | ACD-05 Timetable Management | 8087 | Published Timetable Catalog Alias |
| **GW-49** | `/api/v1/attendance/metrics` | `http://localhost:8088/metrics` | ACD-06 Attendance Management | 8088 | Prometheus Metrics Endpoint |
| **GW-50** | `/api/v1/academics/attendance/metrics` | `http://localhost:8088/metrics` | ACD-06 Attendance Management | 8088 | Prometheus Metrics Endpoint |
| **GW-51** | `/api/v1/attendance` | `http://localhost:8088/api/v1/academics/attendance` | ACD-06 Attendance Management | 8088 | Academic Tier (Direct Ingress) |
| **GW-52** | `/api/v1/academics/attendance` | `http://localhost:8088/api/v1/academics/attendance` | ACD-06 Attendance Management | 8088 | Academic Tier (Namespace Ingress) |
| **GW-53** | `/v1/attendance` | `http://localhost:8088/api/v1/academics/attendance` | ACD-06 Attendance Management | 8088 | Attendance Management Canonical Alias |
| **GW-54** | `/v1/attendance-sessions` | `http://localhost:8088/api/v1/academics/attendance/sessions` | ACD-06 Attendance Management | 8088 | Attendance Sessions Alias |
| **GW-55** | `/v1/attendance-summaries` | `http://localhost:8088/api/v1/academics/attendance/summaries` | ACD-06 Attendance Management | 8088 | Attendance Summaries Alias |
| **GW-56** | `/v1/attendance-reports` | `http://localhost:8088/api/v1/academics/attendance/report` | ACD-06 Attendance Management | 8088 | Attendance Reports Alias |

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
    { "prefix": "/api/v1/courses", "target": "http://localhost:8083/api/v1/courses" },
    { "prefix": "/api/v1/academics/courses", "target": "http://localhost:8083/api/v1/academics/courses" },
    { "prefix": "/api/v1/curricula/metrics", "target": "http://localhost:8084/metrics" },
    { "prefix": "/api/v1/curricula", "target": "http://localhost:8084/api/v1/curricula" },
    { "prefix": "/api/v1/academics/curricula", "target": "http://localhost:8084/api/v1/academics/curricula" },
    { "prefix": "/api/v1/active", "target": "http://localhost:8084/api/v1/curricula/active" },
    { "prefix": "/api/v1/subjects/metrics", "target": "http://localhost:8085/metrics" },
    { "prefix": "/api/v1/academics/subjects/metrics", "target": "http://localhost:8085/metrics" },
    { "prefix": "/api/v1/subjects", "target": "http://localhost:8085/api/v1/subjects" },
    { "prefix": "/api/v1/academics/subjects", "target": "http://localhost:8085/api/v1/academics/subjects" },
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
    { "prefix": "/v1/documents", "target": "http://localhost:8082/api/v1/college-admin/documents" },
    { "prefix": "/v1/courses", "target": "http://localhost:8083/api/v1/courses" },
    { "prefix": "/v1/course-catalog", "target": "http://localhost:8083/api/v1/courses/catalog" },
    { "prefix": "/v1/curricula", "target": "http://localhost:8084/api/v1/curricula" },
    { "prefix": "/v1/curriculum-catalog", "target": "http://localhost:8084/api/v1/curricula/active" },
    { "prefix": "/v1/subjects", "target": "http://localhost:8085/api/v1/subjects" },
    { "prefix": "/v1/subject-catalog", "target": "http://localhost:8085/api/v1/academics/subjects/catalog" }
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

#### 14. Ingest Cross-Module Batch Split / Merge Approval Event (ACD-04)
- **User Story**: CSV Line 40 (`ADM02_workflow_instances`, `ADM02_approval_requests`, `ADM02_inbox_events`)
- **Gateway Path**: `POST /api/v1/college-admin/workflows/batch-approvals/events` or `POST /v1/batch-approvals/events`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/workflows/batch-approvals/events`
- **Deduplication**: Deduplicated exactly-once on `eventId` via `ADM02_inbox_events`. Replays return `200 OK` with `status: "DUPLICATE_IGNORED"`.
- **Request Body (Batch Split)**:
```json
{
  "eventId": "EVT_SPLIT_20260925_001",
  "eventType": "BatchSplitApprovalRequested",
  "eventVersion": "1.0",
  "tenantId": "CAMPUS_MAIN",
  "institutionId": "INST_001",
  "correlationId": "TRACE_SPLIT_001",
  "timestamp": 1789973200000,
  "sourceService": "ACD-04",
  "data": {
    "requestId": "REQ_SPLIT_9812",
    "requestType": "SPLIT",
    "sourceBatchId": "BAT-2026-CS-A",
    "sourceBatchCode": "CS-A",
    "departmentId": "DEP_CS",
    "campusId": "MAIN",
    "requestedBy": "ACAD_ADMIN_USER",
    "requestedAt": 1789973200000,
    "reason": "Split cohort into lab sections A1 and A2",
    "proposedSections": ["A1", "A2"],
    "approverRole": "REGISTRAR"
  }
}
```
- **Response**: `201 Created`
```json
{
  "status": "PENDING",
  "requestId": "REQ_SPLIT_9812",
  "requestType": "SPLIT",
  "workflowInstanceId": "CWF_BATCH_REQ_SPLIT_9812",
  "approverRole": "REGISTRAR"
}
```

#### 15. Query Batch Split / Merge Approval Requests
- **User Story**: CSV Line 40 (`ADM02_approval_requests`)
- **Gateway Path**: `GET /api/v1/college-admin/workflows/batch-approvals` or `GET /v1/batch-approvals`
- **Target**: `GET http://localhost:8082/api/v1/college-admin/workflows/batch-approvals`
- **Query Params**: `?status=PENDING` (optional)
- **Response**: `200 OK`
```json
{
  "batchApprovals": [
    {
      "requestId": "REQ_SPLIT_9812",
      "requestType": "SPLIT",
      "sourceBatchId": "BAT-2026-CS-A",
      "status": "PENDING",
      "approverRole": "REGISTRAR"
    }
  ]
}
```

#### 16. Record Registrar Approval / Rejection Decision with Cryptographic Audit Trail
- **User Story**: CSV Line 40 & 41 (`ADM02_audit_logs`, `ADM02_outbox_events`, `ADM02_workflow_instances`)
- **Gateway Path**: `POST /api/v1/college-admin/workflows/batch-approvals/{requestId}/decide` or `POST /v1/batch-approvals/{requestId}/decide`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/workflows/batch-approvals/{requestId}/decide`
- **Headers**: `X-User-Role: REGISTRAR` (enforced via HTTP 403 Forbidden for non-Registrar; anti-self-certification enforced via HTTP 400 Bad Request)
- **Request Body**:
```json
{
  "decision": "APPROVED",
  "decidedBy": "REGISTRAR_DR_SMITH",
  "reason": "Lab capacity and faculty allocation verified"
}
```
- **Response**: `200 OK`
```json
{
  "status": "APPROVED",
  "requestId": "REQ_SPLIT_9812",
  "decision": "APPROVED",
  "decidedBy": "REGISTRAR_DR_SMITH",
  "decidedAt": 1789973210000,
  "reason": "Lab capacity and faculty allocation verified",
  "auditRecordId": "692c8172-881b-49ef-9b21-4f90117a2201",
  "beforeHash": "GENESIS_HASH_0000000000000000",
  "afterHash": "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855"
}
```

#### 17. Register Role with Separation of Duties (SoD) Enforcement
- **User Story**: CSV Line 39 (`ADM02_permissions`, `ADM02_role_bindings`)
- **Gateway Path**: `POST /api/v1/college-admin/roles` or `POST /v1/college-roles`
- **Target**: `POST http://localhost:8082/api/v1/college-admin/roles`
- **Rules**:
  - `BATCH_SPLIT_REQUEST` cannot be combined with `BATCH_SPLIT_APPROVE` in the same role or principal binding.
  - `BATCH_MERGE_REQUEST` cannot be combined with `BATCH_MERGE_APPROVE`.
- **Conflict Response**: `400 Bad Request` with `errorCode: "ADM02_SEPARATION_OF_DUTIES_VIOLATION"`

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

### 4.4 ACD-02 Curriculum Management Service Endpoints (`http://localhost:8084`)

#### 1. Create Master Curriculum
- **Gateway Path**: `POST /api/v1/curricula` or `POST /api/v1/academics/curricula`
- **Target**: `POST http://localhost:8084/api/v1/curricula`
- **Headers**: `X-Trace-Id`, `X-Tenant-Id`, `X-User-Role: ACADEMIC_ADMIN`, `X-User-Id`
- **Request Body**:
```json
{
  "courseId": "CRS_CS101",
  "name": "B.Tech Computer Science Curriculum 2026",
  "academicPattern": "CBCS",
  "academicYear": "2026-2027",
  "departmentId": "DEP_CSE_01",
  "campusId": "MAIN",
  "institutionId": "INST_VIT_001"
}
```
- **Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "curriculumId": "CUR-9F8A2B1C",
    "id": "CUR-9F8A2B1C",
    "courseId": "CRS_CS101",
    "version": 1,
    "status": "DRAFT",
    "academicPattern": "CBCS",
    "name": "B.Tech Computer Science Curriculum 2026"
  }
}
```

#### 2. Query Active Curriculum Catalog
- **Gateway Path**: `GET /api/v1/curricula/active`, `GET /api/v1/active`, or `GET /v1/curriculum-catalog`
- **Target**: `GET http://localhost:8084/api/v1/curricula/active`
- **Response**: `200 OK`
```json
{
  "activeCurricula": [
    {
      "id": "CUR-9F8A2B1C",
      "courseId": "CRS_CS101",
      "status": "ACTIVE",
      "currentVersion": 1,
      "name": "B.Tech Computer Science Curriculum 2026"
    }
  ],
  "count": 1
}
```

#### 3. Search and Filter Curricula
- **Gateway Path**: `GET /api/v1/curricula?courseId=CRS_CS101&status=ACTIVE` or `GET /api/v1/academics/curricula?departmentId=DEP_CSE_01`
- **Target**: `GET http://localhost:8084/api/v1/curricula`
- **Response**: `200 OK`

#### 4. Get Curriculum by ID
- **Gateway Path**: `GET /api/v1/curricula/{id}` or `GET /api/v1/academics/curricula/{id}`
- **Target**: `GET http://localhost:8084/api/v1/curricula/{id}`
- **Response**: `200 OK`

#### 5. Curriculum Lifecycle Governance & Transitions
- **Gateway Path**:
  - `POST /api/v1/curricula/{id}/submit` &rarr; Transition `DRAFT` &rarr; `UNDER_REVIEW`
  - `POST /api/v1/curricula/{id}/review` &rarr; Records peer review feedback
  - `POST /api/v1/curricula/{id}/approve` &rarr; Transition `UNDER_REVIEW` &rarr; `APPROVED` (Requires `ACADEMIC_DEAN` / `BOARD_OF_STUDIES`)
  - `POST /api/v1/curricula/{id}/publish` &rarr; Transition `APPROVED` &rarr; `ACTIVE` (Auto-supersedes prior version)
  - `POST /api/v1/curricula/{id}/retire` &rarr; Transition `ACTIVE` &rarr; `RETIRED`
  - `POST /api/v1/curricula/{id}/annual-revision` &rarr; Spawns new incremental minor/major draft version
  - `DELETE /api/v1/curricula/{id}` &rarr; Hard delete (permitted only in `DRAFT` status)
- **Response**: `200 OK`

#### 6. Semester Structure & Credit Allocation
- **Gateway Path**: `POST /api/v1/curricula/{id}/versions/{v}/semesters`
- **Target**: `POST http://localhost:8084/api/v1/curricula/{id}/versions/{v}/semesters`
- **Request Body**:
```json
{
  "semesterNumber": 3,
  "minCredits": 18,
  "maxCredits": 24,
  "academicTerm": "ODD_SEMESTER_2026"
}
```
- **Response**: `200 OK`

#### 7. Subject Mapping into Curriculum Version
- **Gateway Path**: `POST /api/v1/curricula/{id}/versions/{v}/subjects`
- **Target**: `POST http://localhost:8084/api/v1/curricula/{id}/versions/{v}/subjects`
- **Request Body**:
```json
{
  "subjectId": "SUB-D93B2F10",
  "subjectCode": "CS201",
  "semesterNumber": 3,
  "subjectType": "CORE",
  "credits": 4.0
}
```
- **Response**: `201 Created`
- **Sub-resources**:
  - `GET /api/v1/curricula/{id}/versions/{v}/subjects` &rarr; List mapped subjects
  - `DELETE /api/v1/curricula/{id}/versions/{v}/subjects/{mappingId}` &rarr; Unmap subject

#### 8. Syllabus Modules & Units Definition
- **Gateway Path**: `PUT /api/v1/curricula/{id}/versions/{v}/syllabus`
- **Target**: `PUT http://localhost:8084/api/v1/curricula/{id}/versions/{v}/syllabus`
- **Request Body**:
```json
{
  "subjectId": "SUB-D93B2F10",
  "units": [
    {
      "unitNumber": 1,
      "title": "Asymptotic Complexity & Elementary Structures",
      "hours": 12,
      "topics": ["Big-O notation", "Recurrence Relations", "Linked Lists"]
    }
  ]
}
```
- **Response**: `200 OK`

#### 9. Program Outcomes (PO) & Educational Objectives (Story 60)
- **Gateway Path**: `POST /api/v1/curricula/{id}/versions/{v}/outcomes`
- **Target**: `POST http://localhost:8084/api/v1/curricula/{id}/versions/{v}/outcomes`
- **Request Body**:
```json
{
  "outcomeCode": "PO1",
  "description": "Apply engineering knowledge to solve complex computing problems.",
  "category": "PROGRAM_OUTCOME",
  "targetAttainment": 75.0
}
```
- **Response**: `201 Created`

#### 10. Curriculum Prerequisites (Cross-Course Progression)
- **Gateway Path**: `POST /api/v1/curricula/{id}/prerequisites`
- **Target**: `POST http://localhost:8084/api/v1/curricula/{id}/prerequisites`
- **Response**: `201 Created` / `409 Conflict` (if circular dependency detected)

#### 11. External API Key Validation & Read-Only Access (Story 63)
- **Gateway Path**: `GET /api/v1/curricula/{id}` or `GET /api/v1/curricula/active`
- **Headers**: `X-API-Key: campx_live_ak_99a8b7c6d5e4`
- **Behavior**: Gateway forwards `X-API-Key` downstream. Downstream validates token, binds caller as `EXTERNAL_API`, and rejects any mutation requests (`POST`/`PUT`/`DELETE`) with `401 Unauthorized` or `403 Forbidden`.

#### 12. Service Prometheus Metrics (Story 69)
- **Gateway Path**: `GET /api/v1/curricula/metrics`
- **Target**: `GET http://localhost:8084/metrics`
- **Response**: `200 OK` (Prometheus exposition text: `acd02_request_total`, `acd02_request_duration_seconds`, `acd02_error_total`, etc.)

---

### 4.5 ACD-03 Subject Management Service Endpoints (`http://localhost:8085`)

#### 1. Create Subject Master
- **Gateway Path**: `POST /api/v1/academics/subjects` or `POST /v1/subjects`
- **Target**: `POST http://localhost:8085/api/v1/academics/subjects`
- **Headers**: `X-Trace-Id`, `X-Tenant-Id`, `X-User-Role: ACADEMIC_ADMIN`, `Idempotency-Key`
- **Request Body**:
```json
{
  "subjectCode": "CS201",
  "name": "Data Structures & Algorithms",
  "departmentId": "DEP_CSE_01",
  "campusId": "MAIN",
  "subjectType": "CORE",
  "classification": "THEORY",
  "credits": 4.0,
  "contactHours": 60.0,
  "elective": false,
  "academicYear": "2026-2027"
}
```
- **Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "subjectId": "SUB-D93B2F10",
    "subjectCode": "CS201",
    "name": "Data Structures & Algorithms",
    "version": 1,
    "status": "ACTIVE"
  },
  "meta": {
    "requestId": "TRACE-GW-SUB-001",
    "correlationId": "TRACE-GW-SUB-001",
    "timestamp": 1789973100000
  }
}
```

#### 2. Query Published Subject Catalog
- **Gateway Path**: `GET /api/v1/academics/subjects/catalog` or `GET /v1/subject-catalog`
- **Target**: `GET http://localhost:8085/api/v1/academics/subjects/catalog`
- **Response**: `200 OK` (Public catalog; accessible to Students, Parents, External API)

#### 3. Search Subjects with Filters
- **Gateway Path**: `GET /api/v1/academics/subjects?departmentId=DEP_CSE_01&subjectType=CORE`
- **Target**: `GET http://localhost:8085/api/v1/academics/subjects`
- **Response**: `200 OK`

#### 4. Add Subject Prerequisite (with DAG Cycle Prevention)
- **Gateway Path**: `POST /api/v1/academics/subjects/{id}/prerequisites`
- **Target**: `POST http://localhost:8085/api/v1/academics/subjects/{id}/prerequisites`
- **Request Body**:
```json
{
  "prerequisiteSubjectId": "SUB-CS101",
  "relationshipType": "PREREQUISITE",
  "mandatory": true,
  "minimumGrade": "C"
}
```
- **Response**: `201 Created` / `409 Conflict` (if cycle detected)

#### 5. Subject Lifecycle Governance
- **Gateway Path**:
  - `POST /api/v1/academics/subjects/{id}/deactivate` &rarr; Transition to `DEACTIVATED`
  - `POST /api/v1/academics/subjects/{id}/reactivate` &rarr; Transition to `ACTIVE`
  - `POST /api/v1/academics/subjects/{id}/deprecate` &rarr; Transition to `DEPRECATED`
  - `POST /api/v1/academics/subjects/{id}/retire` &rarr; Transition to `RETIRED`
  - `DELETE /api/v1/academics/subjects/{id}` &rarr; Hard delete (blocked with 409 if in use)
- **Response**: `200 OK`

#### 6. Visual Side-by-Side Version Diff (Story 10)
- **Gateway Path**: `GET /api/v1/academics/subjects/{id}/versions/diff?v1={v1}&v2={v2}`
- **Target**: `GET http://localhost:8085/api/v1/academics/subjects/{id}/versions/diff?v1={v1}&v2={v2}`
- **Response**: `200 OK`
```json
{
  "subjectId": "SUB-D93B2F10",
  "version1": 1,
  "version2": 2,
  "summary": "Compared version 1 and 2 of subject SUB-D93B2F10: 2 field(s) changed",
  "differences": [
    {
      "fieldName": "credits",
      "version1Value": "4.0",
      "version2Value": "5.0",
      "changed": true
    }
  ]
}
```

#### 7. Course Outcomes (CO) Definition (Story 1)
- **Gateway Path**: `PUT /api/v1/academics/subjects/{id}/versions/{v}/course-outcomes`
- **Target**: `PUT http://localhost:8085/api/v1/academics/subjects/{id}/versions/{v}/course-outcomes`
- **Request Body**:
```json
[
  {
    "outcomeCode": "CO1",
    "statement": "Understand supervised and unsupervised learning algorithms",
    "bloomLevel": "K2_UNDERSTAND",
    "targetAttainment": 75.0
  }
]
```
- **Response**: `200 OK`

#### 8. CO-to-PO Articulation Matrix (Story 2)
- **Gateway Path**: `PUT /api/v1/academics/subjects/{id}/versions/{v}/co-po-matrix`
- **Target**: `PUT http://localhost:8085/api/v1/academics/subjects/{id}/versions/{v}/co-po-matrix`
- **Request Body**:
```json
[
  {
    "outcomeCode": "CO1",
    "programOutcomeCode": "PO1",
    "correlationStrength": 3
  }
]
```
- **Response**: `200 OK`

#### 9. Modular Syllabus Units Breakdown (Story 5)
- **Gateway Path**: `PUT /api/v1/academics/subjects/{id}/versions/{v}/syllabus-units`
- **Target**: `PUT http://localhost:8085/api/v1/academics/subjects/{id}/versions/{v}/syllabus-units`
- **Request Body**:
```json
[
  {
    "unitNumber": 1,
    "title": "Virtualization and Containers",
    "topics": ["Hypervisors", "Docker", "Kubernetes"],
    "hours": 12.0
  }
]
```
- **Response**: `200 OK`

#### 10. Subject Equivalences & Credit Transfer (Story 3)
- **Gateway Path**: `POST /api/v1/academics/subjects/{id}/equivalences`
- **Target**: `POST http://localhost:8085/api/v1/academics/subjects/{id}/equivalences`
- **Request Body**:
```json
{
  "targetSubjectId": "SUB-TARGET-99",
  "equivalenceType": "DIRECT_SUBSTITUTION",
  "transferMultiplier": 1.0,
  "minimumGrade": "C",
  "externalInstitutionName": "State Technical University",
  "effectiveFrom": "2026-2027",
  "effectiveTo": "2030-2031",
  "status": "ACTIVE"
}
```
- **Response**: `201 Created`

#### 11. Board of Studies (BoS) Governance Resolution (Story 9)
- **Gateway Path**: `PUT /api/v1/academics/subjects/{id}/versions/{v}/resolution`
- **Target**: `PUT http://localhost:8085/api/v1/academics/subjects/{id}/versions/{v}/resolution`
- **Request Body**:
```json
{
  "resolutionNumber": "BOS-CSE-2026-R09",
  "approvedByBoard": "Board of Studies",
  "meetingDate": "2026-09-01",
  "minutesUrl": "https://campx.edu/minutes/bos-2026-001",
  "gazetteNotificationNumber": "GZ-2026-99"
}
```
- **Response**: `200 OK`

#### 12. Audit History & Temporal Trail (Story 63)
- **Gateway Path**: `GET /api/v1/academics/subjects/{id}/history`
- **Target**: `GET http://localhost:8085/api/v1/academics/subjects/{id}/history`
- **Response**: `200 OK`

#### 13. Service Prometheus Metrics
- **Gateway Path**: `GET /api/v1/subjects/metrics`
- **Target**: `GET http://localhost:8085/metrics`
- **Response**: `200 OK` (Prometheus exposition text: `acd03_request_total`, `acd03_request_duration_seconds`, `acd03_error_total`, etc.)

---

### 4.5 ACD-04: Batch Management Service Endpoints (Academic Tier, Port 8086)

The **Batch Management Service (ACD-04)** serves as the authoritative custodian of student cohort groupings, sections, configured capacity limits, authorized overrides, student roster enrollments, point-in-time audit history, and cross-module split/merge orchestrations gated by Registrar approval via ADM-02.

```
       +-------------------------------------------------------------+
       |                  ACD-04 Batch Aggregate Root                |
       |  (batchCode, courseId, semesterNo, capacity, status, ver)   |
       +------------------------------+------------------------------+
                                      |
         +----------------------------+----------------------------+
         |                            |                            |
         v                            v                            v
+------------------+         +------------------+         +------------------+
|   BatchSection   |         |   BatchRoster    |         | CapacityOverride |
|  (secCode, cap,  |         | (studentId, stat,|         | (ovrCap, reason, |
|    facultyId)    |         |   effectiveFrom) |         |  status: ACTIVE) |
+------------------+         +------------------+         +------------------+
```

#### 1. Create Draft Batch (Story 3)
- **Gateway Path**: `POST /api/v1/academics/batches` or `POST /api/v1/batches`
- **Target**: `POST http://localhost:8086/api/v1/academics/batches`
- **Required Headers**: `Content-Type: application/json`, `X-User-Role: ACADEMIC_ADMIN`, `X-Trace-Id`, `X-Tenant-Id`
- **Optional Header**: `Idempotency-Key` (Story 48)
- **Request Body**:
```json
{
  "batchCode": "CS-2024-A",
  "name": "Computer Science Cohort 2024 Section A",
  "courseId": "CRS-CS101",
  "curriculumId": "CUR-9F8A2B1C",
  "departmentId": "DEP-CS",
  "campusId": "MAIN",
  "academicYear": "2024-2025",
  "semesterNo": 1,
  "capacity": 60
}
```
- **Response**: `201 Created`
```json
{
  "success": true,
  "data": {
    "batchId": "BATCH-101",
    "batchCode": "CS-2024-A",
    "name": "Computer Science Cohort 2024 Section A",
    "status": "DRAFT",
    "rosterCount": 0,
    "capacity": 60,
    "version": 1
  },
  "meta": {
    "requestId": "REQ-7b89f012",
    "correlationId": "TRACE-GW-BATCH-001",
    "timestamp": 1789973000000
  }
}
```

#### 2. Search & Catalog Batches (Story 10)
- **Gateway Path**: `GET /api/v1/academics/batches`, `GET /api/v1/batches`, or `GET /v1/batch-catalog`
- **Target**: `GET http://localhost:8086/api/v1/academics/batches`
- **Query Parameters**: `courseId`, `academicYear`, `semesterNo`, `departmentId`, `status`, `campusId`, `page`, `pageSize`
- **Response**: `200 OK`

#### 3. View Batch Detail (Story 8, 11)
- **Gateway Path**: `GET /api/v1/academics/batches/{id}` or `GET /api/v1/batches/{id}`
- **Target**: `GET http://localhost:8086/api/v1/academics/batches/{id}`
- **Security Scoping**: Enforces Student view-own-batch and Parent read-only child batch context (Stories 11, 53).
- **Response**: `200 OK`

#### 4. Update Batch Details (Story 7, 49)
- **Gateway Path**: `PUT /api/v1/academics/batches/{id}` or `PUT /api/v1/batches/{id}`
- **Target**: `PUT http://localhost:8086/api/v1/academics/batches/{id}`
- **Optimistic Concurrency**: Enforces version matching (BR-11, 409 `ACD_BATCH_VERSION_CONFLICT` on mismatch).
- **Response**: `200 OK`

#### 5. Batch Section Management (Stories 13-16)
- **Create Section**: `POST /api/v1/batches/{id}/sections` &rarr; `201 Created`
- **List Sections**: `GET /api/v1/batches/{id}/sections` &rarr; `200 OK`
- **Assign Faculty (Optional)**: `POST /api/v1/batches/{id}/sections/{sectionId}/faculty` &rarr; `200 OK`

#### 6. Capacity Management & Overrides (Stories 18-21, 72)
- **Update Capacity**: `PUT /api/v1/academics/batches/{id}/capacity` (Must be positive and &ge; current `rosterCount`)
- **Grant Capacity Override**: `POST /api/v1/academics/batches/{id}/capacity-override` (Story 20, 72)
```json
{
  "overrideCapacity": 75,
  "reason": "Dean approved expansion for transfer students",
  "effectiveFrom": "2024-09-01",
  "effectiveTo": "2025-12-31"
}
```
- **Revoke Override**: `DELETE /api/v1/academics/batches/{id}/capacity-override/{overrideId}` &rarr; `200 OK`

#### 7. Roster & Student Enrollment Management (Stories 23-29, 72, 73)
- **Add Student to Roster**: `POST /api/v1/academics/batches/{id}/students`
  - Validates student eligibility (BR-05; 422 if ineligible).
  - Enforces duplicate assignment check (BR-06; 409 if already active).
  - Enforces capacity limit (BR-04; 409 `ACD_BATCH_CAPACITY_EXCEEDED` unless active override).
  - Blocks addition if batch is CLOSED/ARCHIVED (BR-07; 409 `ACD_BATCH_CLOSED`).
- **Remove Student from Roster**: `DELETE /api/v1/academics/batches/{id}/students/{studentId}` (Soft-ends membership, preserves historical records).
- **Get Current Active Roster**: `GET /api/v1/academics/batches/{id}/roster`
- **View Point-in-Time Roster History**: `GET /api/v1/academics/batches/{id}/history?pointInTime=1789972800000`

#### 8. Batch Lifecycle State Machine (Stories 31-35, 73)
- **Open / Activate**: `POST /api/v1/academics/batches/{id}/open` &rarr; `ACTIVE`
- **Close Batch**: `POST /api/v1/academics/batches/{id}/close` &rarr; `CLOSED`
- **Reopen Batch**: `POST /api/v1/academics/batches/{id}/reopen` &rarr; `ACTIVE`
- **Archive Batch**: `POST /api/v1/academics/batches/{id}/archive` &rarr; `ARCHIVED`

#### 9. Batch Split & Merge Cross-Module Integration with ADM-02 (Stories 37-39, 75, 76, 77)
- **Request Split**: `POST /api/v1/batches/{id}/split` (Academic Admin initiates, enters `PENDING_SPLIT_APPROVAL`, emits `BatchSplitApprovalRequested` to ADM-02).
- **Request Merge**: `POST /api/v1/batches/merge` (Source batches enter `PENDING_MERGE_APPROVAL`, emits `BatchMergeApprovalRequested` to ADM-02).
- **Consume ADM-02 Approval Decision**: `POST /api/v1/academics/batches/events/approval-decision` (Consumes `BatchSplitApprovalDecided` / `BatchMergeApprovalDecided` from ADM-02 workflow engine; executes split/merge with full student reassignment map or reverts status on rejection).

#### 10. Service Prometheus Metrics & Roster Reconciliation (Stories 61, 62)
- **Gateway Path**: `GET /api/v1/batches/metrics`
- **Target**: `GET http://localhost:8086/metrics`
- **Metrics Tracked**: `acd04_request_total`, `acd04_request_duration_seconds`, `acd04_error_total`, `acd04_capacity_conflicts_total`, `acd04_roster_reconciliation_mismatches`, `acd04_outbox_backlog`, `acd04_dlq_events_total`.

---

### 4.7 ACD-05 Timetable Management Service Endpoints (`http://localhost:8087`)

ACD-05 serves as the authoritative weekly timetable service, owning draft aggregates, slot entries, version freeze snapshots, deterministic conflict detection (faculty, room, batch, calendar, availability, duplicate-entry), and atomic publication state machines.

#### 1. Create Timetable Draft (Story 1, FR-01)
- **Gateway Path**: `POST /api/v1/academics/timetables` or `POST /v1/timetables`
- **Target**: `POST http://localhost:8087/api/v1/academics/timetables`
- **Headers**: `X-Trace-Id`, `X-Tenant-Id: TENANT-001`, `X-User-Role: ACADEMIC_ADMIN`, `X-User-Id`
- **Request Body**:
```json
{
  "timetableCode": "TT_CSE_2026_S1",
  "name": "B.Tech Computer Science Weekly Schedule 2026",
  "academicYear": "2026-2027",
  "semester": "1",
  "departmentId": "DEP-CSE",
  "programId": "PROG-BTECH-CSE",
  "effectiveFrom": "2026-08-15",
  "effectiveTo": "2026-12-15"
}
```
- **Response**: `201 Created`
```json
{
  "success": true,
  "message": "Timetable draft created successfully",
  "data": {
    "id": "TT-4CC5F01B",
    "timetableCode": "TT_CSE_2026_S1",
    "name": "B.Tech Computer Science Weekly Schedule 2026",
    "status": "DRAFT",
    "currentVersionNo": 1,
    "version": 1
  },
  "meta": { "requestId": "TRACE-001", "correlationId": "TRACE-001" }
}
```

#### 2. Query and Filter Timetables Catalog (Story 5, FR-11)
- **Gateway Path**: `GET /api/v1/academics/timetables?departmentId=DEP-CSE&academicYear=2026-2027&status=PUBLISHED`
- **Target**: `GET http://localhost:8087/api/v1/academics/timetables?...`
- **Response**: `200 OK`

#### 3. Manage Slot Entries (Stories 11-15, FR-02)
- **Add Slot Entry**: `POST /api/v1/academics/timetables/{id}/entries` &rarr; `201 Created`
  - Validates upstream batch (ACD-04), subject (ACD-03), faculty (HRM), room (Facilities) references.
  - Enforces cross-tenant prohibition (BR-09; 422 if foreign tenant).
  - Enforces faculty lab qualification rights for `entryType=LAB` (Story 36; 422 `ACD_TIMETABLE_LAB_RIGHTS_MISSING`).
- **Update Slot Entry**: `PUT /api/v1/academics/timetables/{id}/entries/{entryId}` &rarr; `200 OK`
- **Remove Slot Entry**: `DELETE /api/v1/academics/timetables/{id}/entries/{entryId}` &rarr; `200 OK`
- **Bulk Slot Entries**: `POST /api/v1/academics/timetables/{id}/entries/bulk` &rarr; `201 Created` (Row-level errors reported without aborting entire batch).

#### 4. Run Deterministic Conflict Detection Engine (Stories 19-27, FR-04, FR-10, BR-01, BR-02, BR-03, BR-06, BR-07, BR-11)
- **Gateway Path**: `POST /api/v1/academics/timetables/{id}/validate`
- **Target**: `POST http://localhost:8087/api/v1/academics/timetables/{id}/validate`
- **Conflict Checks**:
  - `FACULTY`: Faculty double-booked in same day + period slot.
  - `ROOM`: Room double-booked in same day + period slot.
  - `BATCH`: Batch double-booked in same day + period slot.
  - `DUPLICATE_ENTRY`: Redundant identical timetable + slot + subject/batch key.
  - `CALENDAR`: Effective date outside institutional academic calendar window.
  - `AVAILABILITY`: Faculty or room scheduled inside blocked unavailability periods.
- **State Transition**: Transitions to `VALIDATING` then `VALIDATED` (if 0 blocking conflicts) or `INVALID` (if conflicts exist).
- **Diagnostics**: `GET /api/v1/academics/timetables/{id}/conflicts`

#### 5. Immutable Publication & Atomic Supersession (Stories 28-35, FR-06, BR-05, BR-08, BR-12)
- **Gateway Path**: `POST /api/v1/academics/timetables/{id}/publish`
- **Target**: `POST http://localhost:8087/api/v1/academics/timetables/{id}/publish`
- **Guards**: Strictly blocks unvalidated or conflicting drafts (422 `ACD_TIMETABLE_UNVALIDATED` / `ACD_TIMETABLE_CONFLICT`).
- **Outcome**: Freezes immutable version entries snapshot marked `PUBLISHED`, atomically marks prior effective version `SUPERSEDED`, and emits `TimetablePublished`, `TimetableSuperseded`, and `TimetableChanged` domain events to transactional outbox.

#### 6. Mid-Term Change Workflow (Story 39)
- **Clone Published Version**: `POST /api/v1/academics/timetables/{id}/clone`
- Pre-populates new draft version (v2) with all slots from current effective version for mid-term adjustments without disrupting live operations.

#### 7. Operational Role & Resource Views (Stories 6-9)
- **Batch Schedule**: `GET /api/v1/academics/timetables/batch/{batchId}` (Only published effective schedule).
- **Faculty Schedule**: `GET /api/v1/academics/timetables/faculty/{facultyId}` (Assigned teaching schedule).
- **Room Utilization**: `GET /api/v1/academics/timetables/room/{roomId}` (Room allocation and maintenance view).
- **Department View**: `GET /api/v1/academics/timetables/department/{departmentId}`
- **Export Timetable**: `GET /api/v1/export?timetableId={id}&format=pdf` (Story 10, emits `TimetableExported`).

#### 8. Service Prometheus Metrics & Outbox Observability (Stories 54, 55)
- **Gateway Path**: `GET /api/v1/timetables/metrics` or `GET /api/v1/academics/timetables/metrics`
- **Target**: `GET http://localhost:8087/metrics`
- **Metrics Tracked**: `acd05_request_total`, `acd05_request_duration_seconds`, `acd05_error_total`, `acd05_conflicts_total`, `acd05_publish_total`, `acd05_validation_duration_seconds`, `acd05_outbox_backlog`, `acd05_dlq_events_total`, `acd05_stale_drafts_total`.

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

### 7.6 Create Course via Gateway (ACD-01)
```bash
curl -X POST http://localhost:8080/v1/courses \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-004" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "courseCode": "CS101",
    "courseName": "Introduction to Computer Science",
    "departmentId": "DEP_CSE_01",
    "durationYears": 4,
    "totalCredits": 4.0,
    "courseType": "THEORY",
    "courseCategory": "CORE"
  }'
```

### 7.7 Create Curriculum Master via Gateway (ACD-02)
```bash
curl -X POST http://localhost:8080/api/v1/curricula \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-005" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "courseId": "CRS_CS101",
    "name": "B.Tech Computer Science Curriculum 2026",
    "academicPattern": "CBCS",
    "academicYear": "2026-2027",
    "departmentId": "DEP_CSE_01",
    "campusId": "MAIN",
    "institutionId": "INST_VIT_001"
  }'
```

### 7.8 Ingest Curriculum Prometheus Metrics via Gateway (ACD-02)
```bash
curl -X GET http://localhost:8080/api/v1/curricula/metrics \
  -H "X-Trace-Id: TRACE-TEST-006"
```

### 7.9 Create Subject Master via Gateway (ACD-03)
```bash
curl -X POST http://localhost:8080/v1/subjects \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-007" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "subjectCode": "CS201",
    "name": "Data Structures & Algorithms",
    "departmentId": "DEP_CSE_01",
    "campusId": "MAIN",
    "subjectType": "CORE",
    "classification": "THEORY",
    "credits": 4.0,
    "contactHours": 60.0,
    "elective": false,
    "academicYear": "2026-2027"
  }'
```

### 7.10 Ingest Subject Prometheus Metrics via Gateway (ACD-03)
```bash
curl -X GET http://localhost:8080/api/v1/subjects/metrics \
  -H "X-Trace-Id: TRACE-TEST-008"
```

### 7.11 Create Academic Batch via Gateway (ACD-04)
```bash
curl -X POST http://localhost:8080/v1/batches \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-009" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "batchCode": "CS-2024-A",
    "name": "Computer Science Cohort 2024 Section A",
    "courseId": "CRS-CS101",
    "curriculumId": "CUR-9F8A2B1C",
    "departmentId": "DEP-CS",
    "campusId": "MAIN",
    "academicYear": "2024-2025",
    "semesterNo": 1,
    "capacity": 60
  }'
```

### 7.12 Enroll Student into Batch via Gateway (ACD-04)
```bash
curl -X POST http://localhost:8080/api/v1/batches/BATCH-101/students \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-010" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "studentId": "STU-1001",
    "effectiveFrom": "2024-09-01",
    "membershipType": "REGULAR"
  }'
```

### 7.13 Grant Governed Capacity Override via Gateway (ACD-04)
```bash
curl -X POST http://localhost:8080/api/v1/batches/BATCH-101/capacity-override \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-011" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "overrideCapacity": 75,
    "reason": "Authorized transfer student enrollment increase",
    "effectiveFrom": "2024-09-01",
    "effectiveTo": "2025-12-31"
  }'
```

### 7.14 Request Batch Split via Gateway (ACD-04)
```bash
curl -X POST http://localhost:8080/api/v1/batches/BATCH-101/split \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TEST-012" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "reason": "Exceeded laboratory workstation capacity",
    "proposedSections": [
      { "sectionCode": "A1", "capacity": 35 },
      { "sectionCode": "A2", "capacity": 35 }
    ]
  }'
```

### 7.15 Scrape Batch Management Prometheus Metrics via Gateway (ACD-04)
```bash
curl -X GET http://localhost:8080/api/v1/batches/metrics \
  -H "X-Trace-Id: TRACE-TEST-013"
```

### 7.16 Create Timetable Draft via Gateway (ACD-05)
```bash
curl -X POST http://localhost:8080/v1/timetables \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TT-001" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "timetableCode": "TT_CSE_2026_S1",
    "name": "B.Tech Computer Science Weekly Schedule 2026",
    "academicYear": "2026-2027",
    "semester": "1",
    "departmentId": "DEP_CSE_01",
    "programId": "PROG_BTECH_CSE",
    "effectiveFrom": "2026-08-15",
    "effectiveTo": "2026-12-15"
  }'
```

### 7.17 Add Timetable Slot Entry via Gateway (ACD-05)
```bash
curl -X POST http://localhost:8080/api/v1/academics/timetables/TT-001/entries \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TT-002" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "batchId": "BATCH-001",
    "subjectId": "SUB-201",
    "facultyId": "FAC-100",
    "roomId": "ROOM-12",
    "dayOfWeek": "MONDAY",
    "period": 2,
    "entryType": "TH"
  }'
```

### 7.18 Run Conflict Validation via Gateway (ACD-05)
```bash
curl -X POST http://localhost:8080/api/v1/academics/timetables/TT-001/validate \
  -H "X-Trace-Id: TRACE-TT-003" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN"
```

### 7.19 Publish Validated Timetable via Gateway (ACD-05)
```bash
curl -X POST http://localhost:8080/api/v1/academics/timetables/TT-001/publish \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-TT-004" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '{
    "effectiveFrom": "2026-08-15"
  }'
```

### 7.20 Scrape Timetable Prometheus Metrics via Gateway (ACD-05)
```bash
curl -X GET http://localhost:8080/api/v1/timetables/metrics \
  -H "X-Trace-Id: TRACE-TT-005"
```

### 7.21 Create Attendance Session via Gateway (ACD-06)
```bash
curl -X POST http://localhost:8080/api/v1/academics/attendance/sessions \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-ATT-001" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: FACULTY" \
  -H "X-User-Id: FAC-100" \
  -d '{
    "batchId": "BATCH-001",
    "subjectId": "SUB-201",
    "timetableEntryId": "SLOT-001",
    "attendanceDate": "2026-10-15",
    "periodNo": 1,
    "startTime": "09:00",
    "endTime": "10:00"
  }'
```

### 7.22 Mark Attendance for Roster via Gateway (ACD-06)
```bash
curl -X POST http://localhost:8080/api/v1/academics/attendance/sessions/ATT-SESS-1001/records \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-ATT-002" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: FACULTY" \
  -H "X-User-Id: FAC-100" \
  -d '{
    "submittedBy": "FAC-100",
    "records": [
      { "studentId": "STU-001", "status": "PRESENT" },
      { "studentId": "STU-002", "status": "ABSENT" },
      { "studentId": "STU-003", "status": "LEAVE", "statusReason": "Medical appointment" }
    ]
  }'
```

### 7.23 Correct Attendance Record with Mandatory Audit Reason (ACD-06)
```bash
curl -X PUT http://localhost:8080/api/v1/academics/attendance/sessions/ATT-SESS-1001/records/STU-002/correct \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-ATT-003" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: FACULTY" \
  -H "X-User-Id: FAC-100" \
  -d '{
    "newStatus": "PRESENT",
    "reason": "Late arrival due to laboratory setup approved by instructor",
    "expectedVersion": 1
  }'
```

### 7.24 Submit Attendance Session (ACD-06)
```bash
curl -X POST http://localhost:8080/api/v1/academics/attendance/sessions/ATT-SESS-1001/submit \
  -H "X-Trace-Id: TRACE-ATT-004" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: FACULTY" \
  -H "X-User-Id: FAC-100"
```

### 7.25 Lock Attendance Session (ACD-06)
```bash
curl -X POST http://localhost:8080/api/v1/academics/attendance/sessions/ATT-SESS-1001/lock \
  -H "X-Trace-Id: TRACE-ATT-005" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -H "X-User-Id: ADMIN-001"
```

### 7.26 Query Student Attendance Summary & Shortage via Gateway (ACD-06)
```bash
curl -X GET http://localhost:8080/v1/attendance-summaries/student/STU-001 \
  -H "X-Trace-Id: TRACE-ATT-006" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN"
```

### 7.27 Bulk Import External Attendance Records via Gateway (ACD-06)
```bash
curl -X POST http://localhost:8080/api/v1/academics/attendance/import \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-ATT-007" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: ACADEMIC_ADMIN" \
  -d '[
    {
      "batchId": "BATCH-001",
      "subjectId": "SUB-201",
      "attendanceDate": "2026-10-15",
      "periodNo": 1,
      "studentId": "STU-001",
      "status": "PRESENT"
    }
  ]'
```

### 7.28 Ingest RFID / Biometric Device Capture via Gateway (ACD-06)
```bash
curl -X POST http://localhost:8080/api/v1/academics/attendance/device/capture \
  -H "Content-Type: application/json" \
  -H "X-Trace-Id: TRACE-ATT-008" \
  -H "X-Tenant-Id: VIT_CAMPUS" \
  -H "X-User-Role: SYSTEM" \
  -d '{
    "deviceId": "READER-LH-101",
    "cardUid": "RFID-992384",
    "studentId": "STU-001",
    "readerLocation": "Lecture Hall 101"
  }'
```

### 7.29 Scrape Attendance Prometheus Metrics via Gateway (ACD-06)
```bash
curl -X GET http://localhost:8080/api/v1/attendance/metrics \
  -H "X-Trace-Id: TRACE-ATT-009"
```




