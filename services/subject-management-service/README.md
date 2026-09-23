# CampXSync Subject Management Service (ACD-03)

## 1. Executive Summary

The **Subject Management Service (ACD-03)** is the single source of truth for all reusable academic subject definitions across the CampXSync College ERP platform. It owns subject identity, institutional code uniqueness, department ownership, credits, taxonomy classifications, prerequisite and co-requisite DAG dependency graphs, immutable historical versions, and governed lifecycle states.

Downstream domains—including **ACD-01 Course Management**, **ACD-02 Curriculum Management**, **ACD-04 Batch Allocation**, **ACD-05 Timetable & Scheduling**, **EXM Examinations**, and **LMS**—consume subject facts via REST APIs and transactional outbox events without directly mutating the ACD-03 database.

---

## 2. Core Architectural Principles

1. **Authoritative Master**: Dedicated, multi-tenant database boundary (`academic_subject_db`). No direct database cross-talk.
2. **Institution-Scoped Uniqueness**: `subjectCode` uniqueness enforced within `tenantId + institutionId` (BR-01).
3. **Controlled Taxonomy**: Subjects classified as `CORE`, `ELECTIVE`, `PRACTICAL`, `PROJECT`, or `AUDIT`, with type categories `THEORY`, `PRACTICAL`, `TUTORIAL`, `PROJECT`, `ELECTIVE`, `AUDIT` (BR-04, FR-04).
4. **Credit Policy Enforcement**: Credit values bounded within configured ranges (0 - 20) with non-negative contact hours (BR-02, BR-03).
5. **DAG Cycle Prevention**: Prerequisite and co-requisite graphs validated with depth-first traversal to reject cyclic dependencies (BR-06).
6. **Immutable Published Versions**: Published versions are strictly read-only; revisions must be introduced via new monotonic versions (BR-06, BR-10).
7. **Safe Lifecycle Governance**: Hard deletion prohibited if referenced by curriculum or active batches (BR-05); deactivation blocks new usage while preserving full historical context.
8. **Transactional Outbox**: All domain events written atomically in the same transaction as state changes and relayed asynchronously to the message broker (BR-14).
9. **Zero Heavy Framework Footprint**: Built with Java 8, embedded `HttpServer`/`HttpsServer`, and `campx-logger`.

---

## 3. Microservice Ingress & Gateway Routes

| Method | Public Gateway Path | Direct Service Path (Port 8085) | Description |
|---|---|---|---|
| `POST` | `/api/v1/academics/subjects` | `/api/v1/academics/subjects` | Create new subject master & initial version |
| `GET` | `/api/v1/academics/subjects` | `/api/v1/academics/subjects` | Search/list subjects with pagination |
| `GET` | `/api/v1/academics/subjects/catalog` | `/api/v1/academics/subjects/catalog` | Retrieve published active catalog |
| `GET` | `/v1/subject-catalog` | `/api/v1/academics/subjects/catalog` | Canonical short alias for subject catalog |
| `GET` | `/v1/subjects` | `/api/v1/subjects` | Canonical short alias for subjects |
| `GET` | `/api/v1/academics/subjects/{id}` | `/api/v1/academics/subjects/{id}` | Retrieve subject details |
| `PUT` | `/api/v1/academics/subjects/{id}` | `/api/v1/academics/subjects/{id}` | Update master fields (optimistic lock) |
| `DELETE` | `/api/v1/academics/subjects/{id}` | `/api/v1/academics/subjects/{id}` | Hard delete (blocked if in use) |
| `POST` | `/api/v1/academics/subjects/{id}/versions` | `/api/v1/academics/subjects/{id}/versions` | Create draft version |
| `GET` | `/api/v1/academics/subjects/{id}/versions` | `/api/v1/academics/subjects/{id}/versions` | List all versions |
| `GET` | `/api/v1/academics/subjects/{id}/versions/{v}` | `/api/v1/academics/subjects/{id}/versions/{v}` | Get immutable version snapshot |
| `PUT` | `/api/v1/academics/subjects/{id}/versions/{v}` | `/api/v1/academics/subjects/{id}/versions/{v}` | Update draft version |
| `POST` | `/api/v1/academics/subjects/{id}/versions/{v}/publish` | `/api/v1/academics/subjects/{id}/versions/{v}/publish` | Publish version & supersede prior |
| `PUT` | `/api/v1/academics/subjects/{id}/metadata` | `/api/v1/academics/subjects/{id}/metadata` | Add/update extended metadata |
| `GET` | `/api/v1/academics/subjects/{id}/metadata` | `/api/v1/academics/subjects/{id}/metadata` | Get metadata (L1-L3 sensitivity filter) |
| `POST` | `/api/v1/academics/subjects/{id}/prerequisites` | `/api/v1/academics/subjects/{id}/prerequisites` | Add prerequisite (DAG validated) |
| `GET` | `/api/v1/academics/subjects/{id}/prerequisites` | `/api/v1/academics/subjects/{id}/prerequisites` | Forward/reverse prerequisite graph |
| `DELETE`| `/api/v1/academics/subjects/{id}/prerequisites/{pid}`| `/api/v1/academics/subjects/{id}/prerequisites/{pid}`| Soft-deactivate prerequisite |
| `POST` | `/api/v1/academics/subjects/{id}/deactivate` | `/api/v1/academics/subjects/{id}/deactivate` | Deactivate subject |
| `POST` | `/api/v1/academics/subjects/{id}/reactivate` | `/api/v1/academics/subjects/{id}/reactivate` | Reactivate subject |
| `POST` | `/api/v1/academics/subjects/{id}/deprecate` | `/api/v1/academics/subjects/{id}/deprecate` | Mark subject deprecated |
| `POST` | `/api/v1/academics/subjects/{id}/retire` | `/api/v1/academics/subjects/{id}/retire` | Retire subject permanently |
| `PUT` | `/api/v1/academics/subjects/{id}/status` | `/api/v1/academics/subjects/{id}/status` | Controlled lifecycle state change |
| `GET` | `/api/v1/academics/subjects/{id}/history` | `/api/v1/academics/subjects/{id}/history` | Retrieve full change audit trail |
| `POST` | `/api/v1/subjects/import` | `/api/v1/subjects/import` | Bulk import with per-row validation |
| `GET` | `/api/v1/subjects/export` | `/api/v1/subjects/export` | Export catalog (CSV) |
| `GET` | `/actuator/health` | `/actuator/health` | Liveness / readiness probe |
| `GET` | `/metrics` | `/metrics` | Prometheus text exposition metrics |

---

## 4. Role-Based Access Control (RBAC) Matrix

| Role | Permissions |
|---|---|
| **Super Admin** | Full access to all endpoints, configuration, and hard deletion. |
| **Academic Admin** | Create, edit, submit, publish, deactivate, bulk import/export. |
| **Department Head** | Create, edit, review, and manage prerequisites within department. |
| **Registrar** | Approve, publish, supersede versions, and retire subjects. |
| **Faculty** | View subject details, edit/submit where assigned. |
| **Student** | View published active subjects and catalog (read-only). |
| **Parent** | View published subject catalog (strictly read-only). |
| **Auditor** | View full history, audit logs, and metrics across all subjects (read-only). |
| **External API** | Scoped read-only access validated via API Key (`X-API-Key`). Mutations rejected with 403. |

---

## 5. Domain Event Catalog (Transactional Outbox)

| Event Name | Aggregate | Trigger Condition |
|---|---|---|
| `SubjectCreated` | `Subject` | Initial subject creation and version 1 insertion. |
| `SubjectUpdated` | `Subject` | Master field modification. |
| `SubjectVersionCreated` | `SubjectVersion` | New draft version created. |
| `SubjectVersionPublished`| `SubjectVersion` | Version approved and made effective. |
| `SubjectVersionSuperseded`| `SubjectVersion` | Prior published version replaced. |
| `SubjectDeactivated` | `Subject` | Subject availability paused. |
| `SubjectReactivated` | `Subject` | Deactivated subject restored to active state. |
| `SubjectDeprecated` | `Subject` | Subject signaled for phase-out. |
| `SubjectRetired` | `Subject` | Subject permanently closed to new academic mapping. |
| `SubjectPrerequisiteAdded`| `SubjectPrerequisite` | Prerequisite edge added to DAG. |
| `SubjectPrerequisiteRemoved`| `SubjectPrerequisite` | Prerequisite edge soft-deactivated. |

---

## 6. How to Run Locally

### Compile and Run Tests
```powershell
mvn clean test -pl services/subject-management-service
```

### Start the Service Standalone
```powershell
mvn exec:java -pl services/subject-management-service -Dexec.mainClass="com.campx.academic.subject.SubjectManagementApplication"
```
The server will bind to port `8085` (or a custom port passed as argument).
