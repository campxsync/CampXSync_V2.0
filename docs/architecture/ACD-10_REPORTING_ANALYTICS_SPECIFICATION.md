# ACD-10 Reporting & Analytics Service - Enterprise Technical Specification & User Story Blueprint

> **Module ID**: ACD-10  
> **Service Name**: Reporting & Analytics Service  
> **Tier**: Academic Management Tier (Read Model, Projection Engine & Governed Feed Boundary)  
> **Authoritative Database**: `acd10_analytics_db` (MongoDB)  
> **Ingress Port**: `8092` (Service Direct) / `8080` (API Gateway Unified Ingress)  
> **Status**: Production Ready  
> **Version**: 2.0.0  
> **Traceability Source**: `ACD-10_Reporting_Analytics_User_Stories.csv` (ACD10-US-001 through ACD10-US-060)

---

## 1. Executive Summary & Purpose

The **ACD-10 Reporting & Analytics Service** is the authoritative analytical read-model boundary and intelligence feed provider for the Academic Management Module across the CampXSync College ERP platform.

In accordance with enterprise microservice design guidelines:
- **Reporting and analytics must remain a read-optimized, asynchronously updated capability and must NEVER become a synchronous dependency of operational academic transactions.**
- **ACD-10 does not own operational master data** (courses, curricula, batches, timetable schedules, attendance marking, calendars, or assessments).
- **ACD-10 does not query upstream operational databases directly.** It consumes academic domain events, normalizes them into immutable canonical facts, updates write-on-event materialized projections, calculates deterministic KPIs, detects analytical risk signals, serves role-filtered dashboards and queries, processes asynchronous report exports, and publishes governed intelligence feeds via a transactional outbox.

---

## 2. Core Architectural Principles

1. **Event-Fed Decoupled Ingestion (ADR-ACD10-01)**: Ingests domain events from Kafka/RabbitMQ emitted by ACD-01, 02, 04, 05, 06, 07, and 09.
2. **Rebuildable & Versioned Projections (ADR-ACD10-02)**: Analytical aggregates are derived from immutable canonical facts. In the event of schema evolution, disaster recovery, or projection repair, read models are rebuilt from retained events without operational database access. Projections carry monotonic versions and freshness timestamps (`asOf`, `projectionLagSeconds`).
3. **Asynchronous Report Jobs (ADR-ACD10-03)**: Report generation never blocks interactive API threads. Requests return `202 Accepted` with a `jobId` and `pollUri`. Completed artifacts are accessed through controlled, expiring references with append-only audit logging.
4. **Policy-Controlled Analytics & Data Classification (ADR-ACD10-04)**: Data is classified as `PUBLIC`, `INTERNAL`, `CONFIDENTIAL`, or `RESTRICTED`. Field-level scrubbing ensures non-privileged callers (students, parents, external consumers) never receive sensitive operational PII.
5. **Multi-Tenancy by Construction**: Every business document, technical collection, repository query, cache key, and export job is strictly scoped to `tenantId`.
6. **Deterministic Risk Rules & Alert Storm Prevention**: Threshold comparisons (e.g., attendance shortage < 75%) trigger events only upon state transition into an alert state, reinforced by a 1-hour deduplication window.

---

## 3. High-Level & Component Architecture

```
                       +---------------------------------------+
                       |      Channels, Portals & API Clients  |
                       |  (Admins, HODs, Faculty, Management)  |
                       +-------------------+-------------------+
                                           |
                                           v
                       +---------------------------------------+
                       |        API Gateway (Port 8080)        |
                       |  * Centralized Authentication (JWT)   |
                       |  * Correlation Tracing (X-Trace-Id)   |
                       |  * Token-Bucket Rate Limiting (429)   |
                       |  * Reverse Proxy & Timeout Resilience |
                       +-------------------+-------------------+
                                           |
                   +-----------------------+-----------------------+
                   |                                               |
                   v                                               v
     [Canonical Analytics Routes]                       [Gateway Architecture Aliases]
   /api/v1/academics/analytics/*                      /api/v1/dashboards, /kpis, /reports
                   |                                               |
                   +-----------------------+-----------------------+
                                           |
                                           v
+-----------------------------------------------------------------------------------------+
|                  ACD-10 Reporting & Analytics Service (Port 8092)                       |
|                                                                                         |
|  +------------------------+  +------------------------+  +---------------------------+  |
|  |   AnalyticsController  |  |    FactNormalizer      |  |    RateLimiter & Metrics  |  |
|  +------------------------+  +------------------------+  +---------------------------+  |
|  | AnalyticsDomainService |  |   ProjectionEngine     |  |    OutboxPublisherRelay   |  |
|  +------------------------+  +------------------------+  +---------------------------+  |
|  |       RuleEngine       |  |     ExportService      |  |    DLQ & Replay Manager   |  |
|  +------------------------+  +------------------------+  +---------------------------+  |
+-----------------------------------------------------------------------------------------+
       |                                          |                          ^
       | Write / Read                             | Publish Events           | Consumes Events
       v                                          v                          |
+-----------------------------+          +-------------------+     +-------------------+
|  MongoDB: acd10_analytics_db|          |  Transactional    |     | Event Bus: Kafka  |
|  * analytics_facts          |          |  Outbox Relay     |     | (ACD-01, 02, 04,  |
|  * attendance_metrics       |          +---------+---------+     |  05, 06, 07, 09)  |
|  * academic_metrics         |                    |               +-------------------+
|  * timetable_metrics        |                    v                         ^
|  * audience_metrics         |          +-------------------+               |
|  * outbox_events            |          | Published Events  |---------------+
|  * idempotency_records      |          | (MetricCalculated,|
|  * dead_letter_events       |          |  RiskDetected,    |
+-----------------------------+          |  InsightGenerated)|
                                         +-------------------+
```

---

## 4. Complete 60 User Story Traceability Matrix

| Story ID | Epic | Role | User Story Summary | Implemented Route / Artifact | Primary Collection | Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **ACD10-US-001** | Gateway & Access | All Users | APIs routed through Gateway with auth & rate limiting | `GatewayConfig.java`, `RateLimiter.java` | None | **VERIFIED** |
| **ACD10-US-002** | Gateway & Access | System | Defense-in-depth auth at gateway and service boundary | `AnalyticsController.java` | None | **VERIFIED** |
| **ACD10-US-003** | Tenant Isolation | All Roles | Strict tenant scoping on every query & storage record | `AnalyticsDomainService.java` | All 8 collections | **VERIFIED** |
| **ACD10-US-004** | RBAC & Privacy | Academic Admin | Institution-wide dashboards & policy-controlled exports | `GET /dashboard`, `POST /export` | `attendance_metrics`, `academic_metrics` | **VERIFIED** |
| **ACD10-US-005** | RBAC & Privacy | Dept Head | Department-scoped analytics; cross-department returns 403 | `GET /dashboard`, `GET /timetable` | `attendance_metrics`, `timetable_metrics` | **VERIFIED** |
| **ACD10-US-006** | RBAC & Privacy | Faculty | Assigned batch and subject analytics only | `GET /attendance`, `GET /progression` | `attendance_metrics`, `academic_metrics` | **VERIFIED** |
| **ACD10-US-007** | RBAC & Privacy | Student / Parent | Scoped personal views; bulk exports blocked with 403 | `GET /dashboard`, `GET /attendance` | `attendance_metrics`, `audience_metrics` | **VERIFIED** |
| **ACD10-US-008** | RBAC & Privacy | Auditor | Classification controls (PUBLIC, INTERNAL, CONFIDENTIAL, RESTRICTED) | `AnalyticsModels.java`, `ExportService.java` | `analytics_facts`, `audience_metrics` | **VERIFIED** |
| **ACD10-US-009** | Query APIs | API Consumer | Validated date ranges, deterministic sorting, bounded limits | `AnalyticsDomainService.java` | All metric collections | **VERIFIED** |
| **ACD10-US-010** | Query APIs | API Consumer | Versioned endpoints, RFC 7807 structured error responses | `ErrorResponse.java`, `AnalyticsExceptions.java` | None | **VERIFIED** |
| **ACD10-US-011** | Dashboard & KPIs | Admin / HOD | Role-filtered dashboard with freshness & lag seconds | `GET /api/v1/academics/analytics/dashboard` | `attendance_metrics`, `academic_metrics` | **VERIFIED** |
| **ACD10-US-012** | Dashboard & KPIs | Management | Aggregated executive trends without student PII | `GET /dashboard` | `academic_metrics`, `attendance_metrics` | **VERIFIED** |
| **ACD10-US-013** | Attendance | Admin / Faculty | Attendance analytics by batch, subject, date range | `GET /api/v1/academics/analytics/attendance` | `analytics_facts`, `attendance_metrics` | **VERIFIED** |
| **ACD10-US-014** | Attendance | Admin / Faculty | Attendance shortage risk signal (`ATTENDANCE_SHORTAGE_PATTERN`) | `RuleEngine.java` | `attendance_metrics`, `outbox_events` | **VERIFIED** |
| **ACD10-US-015** | Timetable | Admin / HOD | Slot utilization by room, faculty, batch, slot type | `GET /api/v1/academics/analytics/timetable` | `timetable_metrics` | **VERIFIED** |
| **ACD10-US-016** | Timetable | Management | Top underutilized rooms and faculty summaries | `GET /timetable` (`topUnderutilizedResources`) | `timetable_metrics` | **VERIFIED** |
| **ACD10-US-017** | Progression | Admin / Faculty | Course completion, curriculum coverage, assessment coverage | `GET /api/v1/academics/analytics/progression` | `academic_metrics` | **VERIFIED** |
| **ACD10-US-018** | Progression | Admin / HOD | Progression risk indicators (`PROGRESSION_LAG_RISK`) | `RuleEngine.java` | `academic_metrics`, `outbox_events` | **VERIFIED** |
| **ACD10-US-019** | Audience | Management | Role/group level engagement aggregates | `AudienceMetric` model | `audience_metrics` | **VERIFIED** |
| **ACD10-US-020** | Report Templates | Report Designer | Reusable report definitions and template catalog | `GET /reports/definitions` | `report_definitions` | **VERIFIED** |
| **ACD10-US-021** | Ingestion | Event Consumer | Consume 7 academic domain events without querying DB | `consumeEvent()` in `AnalyticsDomainService` | `analytics_facts` | **VERIFIED** |
| **ACD10-US-022** | Ingestion | Fact Normalizer | Normalize heterogeneous events into canonical facts | `FactNormalizer.java` | `analytics_facts` | **VERIFIED** |
| **ACD10-US-023** | Ingestion | Event Consumer | Enforce idempotency via `{tenantId, sourceEventId}` | `factsUniqueIndex`, `idempotency_records` | `analytics_facts`, `idempotency_records` | **VERIFIED** |
| **ACD10-US-024** | Ingestion | Event Consumer | Late & out-of-order event recomputation window | Monotonic `projectionVersion` increment | All metric collections | **VERIFIED** |
| **ACD10-US-025** | Ingestion | Operator | Poison message quarantine to Dead Letter Queue (DLQ) | `dead_letter_events` | `dead_letter_events` | **VERIFIED** |
| **ACD10-US-026** | Ingestion | Operator | Authorized, idempotent replay of dead-letter events | `POST /dlq/{eventId}/replay` | `dead_letter_events` | **VERIFIED** |
| **ACD10-US-027** | Ingestion | Operator | Consumer lag, processed/failed counts, throughput metrics | `MetricsCollector.java`, `/metrics` | None | **VERIFIED** |
| **ACD10-US-028** | Ingestion | Operator | Event envelope validation (eventId, type, tenant, etc.) | `FactNormalizer.validateEnvelope()` | `analytics_facts`, `dead_letter_events` | **VERIFIED** |
| **ACD10-US-029** | Projections | Projection Engine | Attendance aggregates with non-negative counters & % (0-100) | `AttendanceMetric.calculatePercentage()` | `attendance_metrics` | **VERIFIED** |
| **ACD10-US-030** | Projections | Projection Engine | Progression aggregates derived from ACD-01/02/04/09 | `AcademicMetric.recalculate()` | `academic_metrics` | **VERIFIED** |
| **ACD10-US-031** | Projections | Projection Engine | Timetable utilization projections from ACD-05/07 | `TimetableMetric.calculateUtilization()` | `timetable_metrics` | **VERIFIED** |
| **ACD10-US-032** | Projections | Operator | Rebuild projections from immutable canonical facts | `POST /rebuild`, `rebuildProjections()` | `analytics_facts`, all metrics | **VERIFIED** |
| **ACD10-US-033** | Projections | Operator | Expose freshness metadata (`asOf`, `projectionLagSeconds`) | `freshness` block in all query APIs | All metric collections | **VERIFIED** |
| **ACD10-US-034** | Projections | Operator | Atomically commit projection updates and outbox events | `updateProjections()` in `AnalyticsDomainService` | Metrics collections, `outbox_events` | **VERIFIED** |
| **ACD10-US-035** | Rules & Insights | Admin | Configurable KPI & risk thresholds by institution/policy | `RuleEngine.java` getters/setters | All metric collections | **VERIFIED** |
| **ACD10-US-036** | Rules & Insights | Event Publisher | Publish `AcademicMetricCalculated` event to outbox | `RuleEngine.createMetricCalculatedEvent()` | `outbox_events` | **VERIFIED** |
| **ACD10-US-037** | Rules & Insights | CAM Consumer | Emit `AttendanceRiskDetected` with deduplication window | `RuleEngine.evaluateAttendanceRisk()` | `attendance_metrics`, `outbox_events` | **VERIFIED** |
| **ACD10-US-038** | Rules & Insights | BI Consumer | Publish `TimetableUtilizationCalculated` event | `RuleEngine.evaluateTimetableUtilization()` | `timetable_metrics`, `outbox_events` | **VERIFIED** |
| **ACD10-US-039** | Rules & Insights | Intelligence | Publish `AcademicInsightGenerated` event | `RuleEngine.evaluateProgression()` | `academic_metrics`, `outbox_events` | **VERIFIED** |
| **ACD10-US-040** | Report Export | Report User | POST /export returns 202 Accepted, jobId, and pollUri | `POST /api/v1/academics/analytics/export` | `report_jobs`, `idempotency_records` | **VERIFIED** |
| **ACD10-US-041** | Report Export | Export Worker | Asynchronous job execution (QUEUED -> RUNNING -> COMPLETED) | `ExportService.java` thread pool | `report_jobs` | **VERIFIED** |
| **ACD10-US-042** | Report Export | Export Worker | Sensitive column scrubbing based on caller role | `ExportService.sanitizeData()` | `report_jobs` | **VERIFIED** |
| **ACD10-US-043** | Report Export | Report User | Poll export job status & metadata | `GET /export/{jobId}` | `report_jobs` | **VERIFIED** |
| **ACD10-US-044** | Report Export | Report User | Expiring object-storage download references | `GET /export/{jobId}/download?token=...` | `report_jobs` | **VERIFIED** |
| **ACD10-US-045** | Report Export | Auditor | Export request, completion, and download audit trail | `ExportService.recordAudit()`, `AuditLogEntry` | `audit_logs` | **VERIFIED** |
| **ACD10-US-046** | Report Export | Operator | Safe diagnostics & controlled retries on failed jobs | `JobStatus.FAILED`, `diagnosticMessage` | `report_jobs` | **VERIFIED** |
| **ACD10-US-047** | Report Export | Report User | Support CSV, XLSX, and PDF export formats | `ExportService.generateCsv/Xlsx/Pdf()` | `report_jobs` | **VERIFIED** |
| **ACD10-US-048** | Outbox | Event Publisher | Persist publication intent in outbox with retry fields | `OutboxEvent` model | `outbox_events` | **VERIFIED** |
| **ACD10-US-049** | Outbox | Operator | Outbox publishing relay with backoff simulation | `relayPendingOutboxEvents()` | `outbox_events` | **VERIFIED** |
| **ACD10-US-050** | Idempotency | API / Event | Idempotency records with SHA-256 payload hash check | `IdempotencyRecord`, 409 on hash mismatch | `idempotency_records` | **VERIFIED** |
| **ACD10-US-051** | DLQ Management | Operator | Preserve event payload, failure code, replay status | `DeadLetterEvent` model | `dead_letter_events` | **VERIFIED** |
| **ACD10-US-052** | Data Integrity | Service | MongoDB schema validators, immutable IDs, non-negative bounds | `AnalyticsModels.java` setters | All collections | **VERIFIED** |
| **ACD10-US-053** | Retention | Data Governance | Configurable retention periods & TTL expiry | `idempotency_records` 24h, exports 24h | Technical collections | **VERIFIED** |
| **ACD10-US-054** | Backup & Recovery | Operator | Documented restore & projection rebuild workflow | `rebuildProjections()` | All collections | **VERIFIED** |
| **ACD10-US-055** | Performance | API Consumer | Materialized projections queryable under 300 ms P95 | In-memory compound indexes & projections | All metric collections | **VERIFIED** |
| **ACD10-US-056** | Observability | Operator | Prometheus health & operational telemetry at /metrics | `MetricsCollector.java`, `/metrics` | None | **VERIFIED** |
| **ACD10-US-057** | Availability | All Users | Analytics degradation does not block upstream writes | Asynchronous decoupling architecture | All collections | **VERIFIED** |
| **ACD10-US-058** | Integration | BI / CAM | Governed event contracts without operational DB access | Outbox published events | `outbox_events` | **VERIFIED** |
| **ACD10-US-059** | Testing | QA / Eng | Unit, integration, replay, and gateway test suites | 32 service tests + 52 gateway tests | All collections | **VERIFIED** |
| **ACD10-US-060** | Route Mapping | Platform Owner | Gateway route mapping for canonical and alias routes | `GatewayConfig.java` route mappings | None | **VERIFIED** |

---

## 5. MongoDB 8-Collection Schema & Index Catalog

### 5.1 Business Collections

#### 1. `analytics_facts`
- **Purpose**: Canonical atomic fact store normalized from incoming domain events.
- **Compound Unique Index**: `{ tenantId: 1, sourceEventId: 1 }`
- **Secondary Indexes**:
  - `{ tenantId: 1, factType: 1, occurredAt: -1 }`
  - `{ tenantId: 1, batchRef: 1, subjectRef: 1, occurredAt: -1 }`
  - `{ tenantId: 1, sourceService: 1, sourceEventType: 1, occurredAt: -1 }`
- **Schema**:
```json
{
  "factId": "FACT-CCE5487D",
  "tenantId": "INST-001",
  "sourceEventId": "EVT-ACD06-00091",
  "sourceEventType": "AttendanceMarked",
  "sourceService": "ACD-06",
  "entityType": "AttendanceSession",
  "entityId": "ATT-2026-00091",
  "subjectRef": "SUB-101",
  "courseRef": "CRS-101",
  "batchRef": "BAT-CSE-3A",
  "facultyRef": "FAC-001",
  "termRef": "2026-27",
  "occurredAt": "2026-09-14T09:00:00Z",
  "processedAt": "2026-09-14T09:00:04Z",
  "factType": "ATTENDANCE_MARK",
  "dimensions": { "status": "PRESENT", "period": "2026-27" },
  "measures": { "presentCount": 42, "absentCount": 6, "leaveCount": 2, "count": 1 },
  "schemaVersion": "1.0",
  "dataClassification": "CONFIDENTIAL",
  "createdAt": "2026-09-14T09:00:04Z"
}
```

#### 2. `attendance_metrics`
- **Purpose**: Read-optimized attendance aggregates supporting dashboard and shortage queries.
- **Compound Unique Index**: `{ tenantId: 1, periodKey: 1, batchRef: 1, subjectRef: 1, facultyRef: 1, dateKey: 1 }`
- **Secondary Indexes**:
  - `{ tenantId: 1, batchRef: 1, periodKey: 1, subjectRef: 1 }`
  - `{ tenantId: 1, riskLevel: 1, attendancePercentage: 1 }`
  - `{ tenantId: 1, asOf: -1 }`
- **Schema**:
```json
{
  "metricId": "MET-ATT-001",
  "tenantId": "INST-001",
  "periodKey": "2026-27",
  "batchRef": "BAT-CSE-3A",
  "subjectRef": "SUB-101",
  "facultyRef": "FAC-001",
  "dateKey": "2026-09-14",
  "scheduledCount": 500,
  "presentCount": 420,
  "absentCount": 61,
  "leaveCount": 19,
  "attendancePercentage": 84.0,
  "shortageThreshold": 75.0,
  "riskLevel": "NONE",
  "asOf": "2026-09-14T09:30:00Z",
  "projectionVersion": 18,
  "updatedAt": "2026-09-14T09:30:00Z"
}
```

#### 3. `academic_metrics`
- **Purpose**: Curriculum coverage, course completion, and assessment progression measures.
- **Compound Unique Index**: `{ tenantId: 1, periodKey: 1, courseRef: 1, subjectRef: 1, batchRef: 1 }`
- **Secondary Indexes**:
  - `{ tenantId: 1, batchRef: 1, periodKey: 1 }`
  - `{ tenantId: 1, riskLevel: 1, progressionScore: 1 }`

#### 4. `timetable_metrics`
- **Purpose**: Slot, room, and faculty utilization analytics.
- **Compound Unique Index**: `{ tenantId: 1, periodKey: 1, batchRef: 1, roomRef: 1, facultyRef: 1, slotType: 1 }`
- **Secondary Indexes**:
  - `{ tenantId: 1, periodKey: 1, utilizationPercentage: 1 }`
  - `{ tenantId: 1, roomRef: 1, periodKey: 1 }`

#### 5. `audience_metrics`
- **Purpose**: Role/group-level aggregate engagement metrics without raw personal behavioral tracking.
- **Compound Unique Index**: `{ tenantId: 1, audienceType: 1, scopeRef: 1, metricType: 1, periodKey: 1 }`

---

### 5.2 Technical Collections

#### 6. `outbox_events`
- **Purpose**: Transactional outbox guaranteeing at-least-once publication of ACD-10 domain events.
- **Unique Index**: `{ eventId: 1 }`
- **Secondary Index**: `{ status: 1, nextAttemptAt: 1 }`

#### 7. `idempotency_records`
- **Purpose**: Prevents duplicate command execution and detects payload tampering via SHA-256 hash.
- **Unique Index**: `{ tenantId: 1, idempotencyKey: 1, operation: 1 }`
- **TTL Index**: `{ expiresAt: 1 }` (24-hour default retention)

#### 8. `dead_letter_events`
- **Purpose**: Quarantines malformed or unprocessable domain events with diagnostic codes and replay tracking.
- **Unique Index**: `{ tenantId: 1, eventId: 1 }`
- **Secondary Index**: `{ replayStatus: 1, lastFailedAt: -1 }`

---

## 6. API Reference Catalog

### 6.1 Academic Dashboard
- **Route**: `GET /api/v1/academics/analytics/dashboard`
- **Aliases**: `GET /api/v1/dashboards`, `GET /api/v1/kpis`, `GET /v1/dashboards`
- **Query Parameters**:
  - `period` (optional): Academic term/year, e.g. `2026-27`
  - `scope` (optional): `institution`, `department`, or `batch`
  - `scopeId` (optional): Scope identifier, e.g. `DEP-CSE`
- **Headers**:
  - `X-Tenant-Id`: Mandatory tenant partition key
  - `X-User-Role`: Required for RBAC (`ACADEMIC_ADMIN`, `DEPARTMENT_HEAD`, `FACULTY`, `STUDENT`)
  - `X-Department-Id`: Required for Department Head scope verification
- **Response `200 OK`**:
```json
{
  "dashboardId": "DB-2026-00091",
  "period": "2026-27",
  "scope": { "type": "department", "id": "DEP-CSE" },
  "metrics": {
    "attendancePercentage": 82.7,
    "timetableUtilization": 76.4,
    "activeCourses": 38,
    "activeBatches": 12
  },
  "freshness": {
    "asOf": "2026-09-30T09:30:00Z",
    "projectionLagSeconds": 18,
    "projectionVersion": 11
  }
}
```

### 6.2 Attendance Analytics
- **Route**: `GET /api/v1/academics/analytics/attendance`
- **Aliases**: `GET /api/v1/analytics/attendance`, `GET /v1/attendance-reports`
- **Query Parameters**:
  - `batchId`: Batch identifier (e.g. `BAT-CSE-3A`)
  - `from`, `to`: ISO-8601 dates (`YYYY-MM-DD`, maximum 730 days window)
  - `subjectId` (optional): Filter to specific subject
- **Response `200 OK`**:
```json
{
  "scope": { "batchId": "BAT-CSE-3A" },
  "period": { "from": "2026-08-01", "to": "2026-09-30" },
  "subjects": [
    { "subjectId": "SUB-101", "present": 420, "absent": 61, "leave": 19, "percentage": 84.0, "riskLevel": "NONE" },
    { "subjectId": "SUB-102", "present": 340, "absent": 140, "leave": 20, "percentage": 68.0, "riskLevel": "HIGH" }
  ],
  "riskSignals": [ "ATTENDANCE_SHORTAGE_PATTERN" ],
  "freshness": { "asOf": "2026-09-30T09:30:00Z" }
}
```

### 6.3 Timetable Utilization
- **Route**: `GET /api/v1/academics/analytics/timetable`
- **Aliases**: `GET /api/v1/analytics/schedules`
- **Query Parameters**: `from`, `to`, `departmentId`
- **Response `200 OK`**:
```json
{
  "period": { "from": "2026-08-01", "to": "2026-09-30" },
  "utilization": {
    "rooms": 78.2,
    "faculty": 71.4,
    "scheduledSlots": 1840,
    "executedSlots": 1732
  },
  "topUnderutilizedResources": [
    { "type": "ROOM", "id": "R-204", "utilization": 34.0 }
  ]
}
```

### 6.4 Academic Progression
- **Route**: `GET /api/v1/academics/analytics/progression`
- **Aliases**: `GET /api/v1/analytics/performance`
- **Query Parameters**: `batchId`, `period`
- **Response `200 OK`**:
```json
{
  "batchId": "BAT-CSE-3A",
  "period": "2026-27",
  "progression": {
    "courseCompletion": 70.0,
    "curriculumCoverage": 72.7,
    "assessmentCoverage": 65.0,
    "progressionScore": 68.0
  },
  "trend": [
    { "period": "2026-08", "completion": 43.7 },
    { "period": "2026-09", "completion": 70.0 }
  ]
}
```

### 6.5 Asynchronous Report Export Lifecycle
- **Step 1: Initiate Export Job**
  - `POST /api/v1/academics/analytics/export` (or `POST /api/v1/reports`, `POST /api/v1/analytics/exports`)
  - **Headers**: `Idempotency-Key`, `X-Tenant-Id`, `X-User-Role`
  - **Payload**:
    ```json
    {
      "reportType": "ATTENDANCE_SUMMARY",
      "format": "CSV",
      "filters": { "batchId": "BAT-CSE-3A" }
    }
    ```
  - **Response `202 Accepted`**:
    ```json
    {
      "jobId": "RPT-ACD10-8F261435",
      "status": "QUEUED",
      "pollUri": "/api/v1/academics/analytics/export/RPT-ACD10-8F261435"
    }
    ```

- **Step 2: Poll Status**
  - `GET /api/v1/academics/analytics/export/{jobId}`
  - **Response `200 OK`**:
    ```json
    {
      "jobId": "RPT-ACD10-8F261435",
      "reportType": "ATTENDANCE_SUMMARY",
      "format": "CSV",
      "status": "COMPLETED",
      "outputRef": "/api/v1/academics/analytics/export/RPT-ACD10-8F261435/download?token=...",
      "rowCount": 2,
      "fileSize": 184,
      "expiresAt": "2026-10-01T09:30:00Z"
    }
    ```

- **Step 3: Download Artifact**
  - `GET /api/v1/academics/analytics/export/{jobId}/download`
  - **Response `200 OK`**: Returns raw binary/text content (`Content-Type: application/octet-stream`).

---

## 7. Event-Driven Architecture & Contracts

### 7.1 Consumed Domain Events
ACD-10 subscribes to 7 core events from upstream academic services:

| Producer Service | Event Type | Envelope Key Extraction | ACD-10 Action Taken |
| :--- | :--- | :--- | :--- |
| **ACD-01** (Course) | `CourseCreated` | `courseId`, `departmentId`, `credits` | Registers active course dimension & metadata |
| **ACD-02** (Curriculum) | `CurriculumPublished` | `curriculumId`, `totalUnits`, `batchId` | Initializes curriculum coverage base targets |
| **ACD-04** (Batch) | `BatchCreated` | `batchId`, `departmentId`, `studentCount` | Creates analytical batch partition context |
| **ACD-05** (Timetable) | `TimetablePublished` | `roomId`, `slotType`, `scheduledSlots` | Updates scheduled slots & utilization baseline |
| **ACD-06** (Attendance) | `AttendanceMarked` | `batchId`, `subjectId`, `presentCount` | Increments counters, evaluates shortage risk |
| **ACD-07** (Calendar) | `AcademicCalendarPublished` | `calendarId`, `academicYear`, `workingDays` | Aligns term boundaries & working day totals |
| **ACD-09** (Assessment) | `AssessmentMappingPublished` | `assessmentId`, `coveragePercentage` | Updates progression assessment coverage metric |

### 7.2 Published Governing Events (Outbox Relay)
Published downstream via transactional outbox to CAM, Notification, BI, and Data Lake consumers:

1. **`AcademicMetricCalculated`**: Emitted whenever attendance, timetable, or progression aggregates are recalculated.
2. **`AttendanceRiskDetected`**: Emitted when attendance percentage drops below threshold (<75%) and crosses the state transition boundary (deduplicated).
3. **`TimetableUtilizationCalculated`**: Emitted when room or faculty utilization is recalculated, identifying underutilized resources.
4. **`AcademicInsightGenerated`**: Emitted when progression targets are missed, feeding intelligence and CAM alerts.

---

## 8. Security & RBAC / ABAC Matrix

| Role | Dashboard Scope | Attendance Scope | Timetable Scope | Progression Scope | Export Permission |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **Academic Admin** | Full Institution | Full Institution | Full Institution | Full Institution | All formats permitted |
| **Department Head** | Own Department only | Own Department | Own Department | Own Department | Department scope only |
| **Faculty** | Denied (403) | Assigned batches/subjects | Assigned batches | Assigned batches | Assigned scope only |
| **Student** | Self-scoped only | Self-scoped only | Published schedules | Self-scoped only | **Restricted (403)** |
| **Parent** | Authorized student | Authorized student | Published schedules | Authorized student | **Restricted (403)** |
| **Management** | Executive KPI aggregated | Aggregated trends | Aggregated trends | Executive trends | Aggregated only |
| **Platform Operator** | Diagnostics & Telemetry | Telemetry | Telemetry | Telemetry | DLQ Replay & Rebuild |

---

## 9. Operational Runbook & Disaster Recovery

### 9.1 Projection Rebuild Sequence
When projections become stale or during disaster recovery:
1. Ensure `analytics_facts` is intact.
2. Issue authenticated operator command:
   ```bash
   curl -X POST http://localhost:8080/api/v1/academics/analytics/rebuild \
     -H "X-Tenant-Id: INST-001" \
     -H "X-User-Role: OPERATOR" \
     -H "X-User-Id: op-1"
   ```
3. The engine will sequentially replay all canonical facts, rebuild `attendance_metrics`, `academic_metrics`, and `timetable_metrics`, update `projectionVersion`, and write an audit log entry.

### 9.2 Dead Letter Queue Replay Procedure
When poison or malformed events have been corrected upstream:
1. Inspect DLQ telemetry via `/metrics` (`acd10_collection_dead_letter_count`).
2. Replay specific quarantined event:
   ```bash
   curl -X POST http://localhost:8080/api/v1/academics/analytics/dlq/{eventId}/replay \
     -H "X-Tenant-Id: INST-001" \
     -H "X-User-Role: OPERATOR"
   ```
3. DLQ status transitions from `PENDING` to `REPLAYED`.

---

## 10. Verification & Test Execution Guide

To execute the test suite across the entire platform or within the analytics module:

```bash
# Run full Maven test suite across all 15 reactor modules
mvn test

# Run test suite specifically for ACD-10 Reporting & Analytics Service
mvn test -pl services/analytics-service

# Run API Gateway reverse proxy routing tests
mvn test -pl api-gateway -Dtest=GatewayAnalyticsIntegrationTest
```

### Verified Test Results Summary:
- **`analytics-service`**: 32 tests run, 32 passed, 0 failures, 0 errors, 0 skipped.
- **`api-gateway`**: 52 tests run, 52 passed, 0 failures, 0 errors, 0 skipped.
- **Overall reactor**: 15 modules built and tested successfully.
