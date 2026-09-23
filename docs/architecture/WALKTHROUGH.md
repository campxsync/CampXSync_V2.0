# CampXSync V2.0 - Complete Architecture & Technical Walkthrough

## Executive Summary
This document serves as the authoritative architectural blueprint and technical walkthrough for the **CampXSync College ERP Version 2.0** core platform. It details the system topology, microservice design, distributed logging integration, and the enterprise-grade RFC 7807 exception handling architecture.

---

## 1. System Topology & Architecture

```
                                      +---------------------------------------------+
                                      |           Client Applications               |
                                      |   (Web Portal, Mobile App, Microservices)   |
                                      +----------------------+----------------------+
                                                             |
                                                             v HTTP Requests
                                      +---------------------------------------------+
                                      |                 API Gateway                 |
                                      |             (Default Port: 8080)            |
                                      |                                             |
                                      | - Injects & Validates X-Trace-Id / TenantId |
                                      | - Auth & Correlation Filter                 |
                                      | - Reverse Proxy & Route Dispatcher          |
                                      | - Downstream Error Preservation             |
                                      | - 503/504 Network Resilience Handler        |
                                      +-------------+-----------------+-------------+
                                                    |                 |
                        /api/v1/admin/**            |                 |   /api/v1/college-admin/**
                                                    v                 v
                 +------------------------------------+             +------------------------------------+
                 |   ADM-01: Institute Admin Service  |             |    ADM-02: College Admin Service   |
                 |      (CampXSync Platform Tier)     |             |       (College Operational Tier)   |
                 |              Port: 8081            |             |              Port: 8082            |
                 +------------------------------------+             +------------------------------------+
                 | - Institutes & Colleges Registry   |             | - College Profile & Accreditations |
                 | - Tenant Provisioning State Machine|             | - Departments & Programs Master    |
                 | - Global Settings & Feature Flags  |             | - Bulk Data Import Pipeline        |
                 | - Platform RBAC & Access Reviews   |             | - Classified Documents & Approvals |
                 | - Commercial Plans & Subscriptions |             | - Local Settings & Safe Overrides  |
                 | - Audit Logging & Data Governance  |             | - Audit Logging & Reliability Layer|
                 +-----------------+------------------+             +-----------------+------------------+
                                   |                                                  |
                                   +-------------------------+------------------------+
                                                             |
                                                             v
                                             +-------------------------------+
                                             |       CampXSync Logger        |
                                             |  - High-Throughput Async I/O  |
                                             |  - TraceId / TenantId Context |
                                             |  - FlowTracker Step Profiling |
                                             |  - Automatic PII/Secret Mask  |
                                             |  - Compliance Audit Events    |
                                             +-------------------------------+
```

---

## 2. Microservice Specifications

### A. API Gateway (`api-gateway`)
- **Port**: `8080` (Configurable via `GatewayConfig`)
- **Reverse Proxy Dispatcher**: [`ReverseProxyHandler`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/router/ReverseProxyHandler.java)
  - Dynamically routes requests matching route prefix tables.
  - Injects `X-Trace-Id` into request headers downstream and response headers upstream.
  - Preserves exact downstream HTTP status codes, headers, and error bodies.
  - Intercepts connection refusals (`ConnectException`) $\to$ **503 Service Unavailable**.
  - Intercepts read/connect timeouts (`SocketTimeoutException`) $\to$ **504 Gateway Timeout**.
  - Handles unmatched routes $\to$ **404 Not Found** with RFC 7807 error schema.
- **Built-in System Endpoints**:
  - `GET /actuator/health` $\to$ Returns `{"status":"UP","gateway":"CampXSync-API-Gateway"}`.
  - `GET /api/v1/gateway/routes` $\to$ Returns active route table bindings.

### B. ADM-01: Institute Admin Service (`services/institute-admin-service`)
- **Port**: `8081` | **Base Path**: `/api/v1/admin`
- **Scope**: Platform-level administration across all institutions and tenants.
- **Key Endpoints**:
  - `POST /api/v1/admin/institutes`: Registers tenant institution with unique `instituteCode`.
  - `GET /api/v1/admin/institutes`: Lists all registered institutions.
  - `PUT /api/v1/admin/institutes/{id}`: Updates profile; rejects modification of immutable `instituteCode`.
  - `POST /api/v1/admin/colleges`: Registers a college under an active parent institution.
  - `POST /api/v1/admin/tenants/{id}/provision`: Executes multi-stage provisioning pipeline (`REQUESTED` $\to$ `VALIDATED` $\to$ `PROVISIONING` $\to$ `COMPLETED`) with idempotency key deduplication.
  - `GET /api/v1/admin/tenants/provisioning`: Inspects status of provisioning jobs.
  - `POST /api/v1/admin/configuration`: Sets scoped global settings; validates that secrets use KMS/vault references (`vault:`, `secret://`).
  - `GET /api/v1/admin/configuration`: Fetches global configuration table.
  - `GET /api/v1/admin/billing/plans`: Exposes commercial subscription plans and module entitlements.
  - `GET /api/v1/admin/audit-logs`: Retrieves immutable compliance audit log records.

### C. ADM-02: College Admin Service (`services/college-admin-service`)
- **Port**: `8082` | **Base Path**: `/api/v1/college-admin`
- **Scope**: College-level operational workflows, academic structures, and data governance.
- **Key Endpoints**:
  - `GET /api/v1/college-admin/profile`: Fetches authoritative college identity and accreditation data.
  - `POST /api/v1/college-admin/profile`: Updates profile; protects immutable identity codes.
  - `POST /api/v1/college-admin/departments`: Registers an academic department with unique `departmentCode`.
  - `GET /api/v1/college-admin/departments`: Lists all departments.
  - `DELETE /api/v1/college-admin/departments/{id}`: Soft-retires department; checks and blocks retirement if active programs reference it (HTTP 422).
  - `POST /api/v1/college-admin/programs`: Creates degree program tied to an active department with immutable versioning.
  - `GET /api/v1/college-admin/programs`: Lists degree programs.
  - `POST /api/v1/college-admin/imports`: Submits bulk data import job with row-level validation and error logging.
  - `GET /api/v1/college-admin/imports/{id}`: Inspects batch import progress.
  - `POST /api/v1/college-admin/documents`: Registers governance documents with mandatory classification (`PUBLIC`, `INTERNAL`, `CONFIDENTIAL`) and SHA-256 checksums.
  - `POST /api/v1/college-admin/documents/{id}/submit`: Executes formal multi-step approval workflow.
  - `GET /api/v1/college-admin/documents`: Lists governance documents.
  - `GET /api/v1/college-admin/audit-logs`: Queries college operational audit trail.

---

## 3. Distributed Logger Integration (`campx-logger`)

Each service natively integrates with the `campx-logger` library:
- **Thread Context Propagation**: `LogContext` stores `traceId`, `tenantId`, `userId`, and `userRole` in thread-local storage, formatting them in every log line:
  ```
  [2026-09-21 19:57:50.280] WARN [Thread-7] [trace=TRACE-GATEWAY-ERR-001] com.campx.admin.institute.controller.InstituteAdminController - [InstituteAdminService] Conflict [POST /api/v1/admin/institutes]: Uniqueness violation: Institute with instituteCode 'INST_GW_DUP_01' already exists
  ```
- **Flow Latency Tracking**: Using `logger.flow("OperationName", flowId)` and `try-with-resources`, microservice executions automatically record step timings and emit summary metrics upon completion or failure (`flow.markFailed(ex)`).
- **Compliance Audit Logging**: Core state transitions and administrative mutations generate dedicated `AuditEvent` records with cryptographic traceability.

---

## 4. Enterprise RFC 7807 Error Handling Architecture

### Standardized Error Contract
Every error emitted by the API Gateway or downstream admin services conforms to RFC 7807:

```json
{
  "timestamp": 1790000570281,
  "status": 409,
  "error": "Conflict",
  "errorCode": "ADM01_DUPLICATE_RESOURCE",
  "message": "Uniqueness violation: Institute with instituteCode 'INST_GW_DUP_01' already exists",
  "path": "/api/v1/admin/institutes",
  "traceId": "TRACE-GATEWAY-ERR-001"
}
```

### Semantic Status and Error Code Matrix

| Service | HTTP Status | Error Code | Description / Trigger |
| :--- | :---: | :--- | :--- |
| **Institute Admin** | `404 Not Found` | `ADM01_RESOURCE_NOT_FOUND` | Institution or parent record does not exist |
| **Institute Admin** | `409 Conflict` | `ADM01_DUPLICATE_RESOURCE` | Duplicate `instituteCode` or `collegeCode` |
| **Institute Admin** | `422 Unprocessable Entity` | `ADM01_INVALID_TENANT_STATE` | Attempting to operate on inactive/suspended institution |
| **Institute Admin** | `400 Bad Request` | `ADM01_PLAINTEXT_SECRET_REJECTED` | Configuration secret passed without vault/KMS reference |
| **Institute Admin** | `400 Bad Request` | `ADM01_IMMUTABLE_KEY_MODIFICATION` | Modification of immutable identity key `instituteCode` |
| **Institute Admin** | `400 Bad Request` | `ADM01_MALFORMED_PAYLOAD` | Missing mandatory fields or malformed JSON |
| **College Admin** | `404 Not Found` | `ADM02_RESOURCE_NOT_FOUND` | Department, program, or document not found |
| **College Admin** | `409 Conflict` | `ADM02_DUPLICATE_RESOURCE` | Department code conflict (`departmentCode`) |
| **College Admin** | `422 Unprocessable Entity` | `ADM02_INVALID_LIFECYCLE_STATE` | Retiring department with active academic programs |
| **College Admin** | `400 Bad Request` | `ADM02_DOCUMENT_GOVERNANCE_ERROR` | Missing required document classification |
| **College Admin** | `400 Bad Request` | `ADM02_MALFORMED_PAYLOAD` | Invalid duration or malformed body |
| **API Gateway** | `404 Not Found` | `GATEWAY_ROUTE_NOT_FOUND` | No route prefix matches request path |
| **API Gateway** | `503 Service Unavailable`| `GATEWAY_SERVICE_UNAVAILABLE` | Downstream microservice down / connection refused |
| **API Gateway** | `504 Gateway Timeout` | `GATEWAY_DOWNSTREAM_TIMEOUT` | Downstream read/connect timeout exceeded |
| **API Gateway** | `500 Internal Error` | `GATEWAY_CONFIG_ERROR` | Invalid or malformed downstream destination URL |
| **API Gateway** | `502 Bad Gateway` | `GATEWAY_PROXY_ERROR` | General downstream proxy communication error |

---

## 5. Verification & Test Execution Results

The entire project compiles with JDK 1.8 and Maven 3.9+, with 100% test pass rates across all reactor modules:

```bash
mvn clean test
```

```
[INFO] ------------------------------------------------------------------------
[INFO] Reactor Summary for CampXSync Platform V2.0 Parent 2.0.0-SNAPSHOT:
[INFO] 
[INFO] CampXSync Logger Utility ........................... SUCCESS [  2.425 s]
[INFO] CampXSync Platform V2.0 Parent ..................... SUCCESS [  0.000 s]
[INFO] CampXSync Institute Admin Service (ADM-01) ......... SUCCESS [  0.850 s]
[INFO] CampXSync College Admin Service (ADM-02) ........... SUCCESS [  1.029 s]
[INFO] CampXSync API Gateway .............................. SUCCESS [  3.350 s]
[INFO] ------------------------------------------------------------------------
[INFO] BUILD SUCCESS
[INFO] Total time:  7.804 s
[INFO] ------------------------------------------------------------------------
```

### Verified Test Suites
1. **`CampXLoggerTest` & `FlowTracingTest`**: Verifies asynchronous log queue draining, multi-threaded in-flight tracking, and PII masking.
2. **`InstituteAdminServiceTest`**: Validates registration, provisioning, 409 duplicate checks, 404 updates, and 400 plaintext secret rejection.
3. **`CollegeAdminServiceTest`**: Validates profile updates, 409 duplicate department codes, 404 missing department retirements, 422 lifecycle protection on active programs, and 400 governance classification validation.
4. **`ApiGatewayRoutingTest`**: Verifies health endpoint, routes table discovery, 404 route not found error responses, and 503 offline downstream handling.
5. **`GatewayEndToEndIntegrationTest`**: Boots all 3 HTTP servers concurrently on separate ports, testing distributed request routing, downstream 201/200 success pass-through, downstream 409 conflict pass-through, and end-to-end `X-Trace-Id` correlation retention.

---

## 6. Git Synchronization
All codebase changes, domain models, tests, and configurations are committed and synchronized:
- **Repository**: `https://github.com/campxsync/CampXSync_V2.0.git`
- **Branch**: `Dev1.0Branch`
- **Head Commit**: `7c2c462`
