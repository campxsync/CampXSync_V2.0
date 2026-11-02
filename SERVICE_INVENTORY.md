# CampXSync — Microservices Inventory & Domain Classification Audit (Executed & Verified)
**Document:** `SERVICE_INVENTORY.md`  
**Monorepo:** `CampXSync_V2.0`  
**Current Branch:** `feature/java-backend-integration`  
**Audit Date:** 30 October 2026  
**Execution Status:** **EXECUTED & VERIFIED** (30 October 2026)  
**Auditor / Engineer:** Antigravity AI Architecture Assistant  

---

> [!NOTE]
> **RESTRUCTURING COMPLETION NOTICE:**
> The domain-based directory restructuring outlined in this document has been **fully executed and verified**. All 18 microservice directories have been relocated to their target domain directories, the hierarchical Maven aggregator POM architecture is active and passing all reactor verification gates (22 modules, 0 compilation errors, 0 test failures), and 100% of uncommitted working-tree assets have been preserved. See [`docs/architecture/SERVICES_DIRECTORY_RESTRUCTURING_COMPLETION_REPORT.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/architecture/SERVICES_DIRECTORY_RESTRUCTURING_COMPLETION_REPORT.md) for full execution evidence.  

---

## 1. Executive Summary & Inventory Methodology

This audit provides a comprehensive, ground-truth inventory of the microservices ecosystem in `services/` to establish the blueprint for restructuring into a domain-oriented directory layout:
`services/{module}-services/{microservice-name}-service/`

### 1.1 Discovery Methodology
Every directory under `services/` was examined directly for:
- Maven project descriptors (`pom.xml`), Maven coordinate definitions (`groupId`, `artifactId`, `version`), and parent POM linkage.
- Java source trees (`src/main/java`), package hierarchies, controllers, domain models, entity mappings, and persistence repositories.
- API Gateway route definitions (`GatewayConfig.java`), port allocations, reverse proxy mappings, and cryptographic HMAC trust boundaries.
- Database access layers (PostgreSQL schemas `adm`, `acd`, `stu`, `core`, `plat`, `sys`), HikariCP connection pooling, and Row-Level Security (RLS) enforcement.
- Test suites (`src/test/java`), surefire test runners, integration tests, and live staging verification harnesses.
- Repository state, git-tracked files, uncommitted changes, and placeholder directories.

### 1.2 Monorepo Discovery Summary
- **Total directories under `services/`:** **18 directories**
  - **Active Java Microservices:** **13 microservices** (fully implemented with Java source code, Maven POM, unit/integration test suites, and API Gateway route registrations).
  - **Scaffolded / Placeholder Directories:** **5 directories** (`ai-service`, `examination-service`, `faculty-service`, `identity-service`, `notification-service` — each containing only `.gitkeep`, with 0 Java files and no `pom.xml`).
- **Root-level Java Architectural Components:**
  - `api-gateway`: Unified HTTP reverse proxy, JWT claim verification, and HMAC security boundary (Port 8080).
  - `logger`: Platform logging and distributed tracing library (`com.campx.platform:campx-logger`).
- **Scaffolded Platform Libraries & Infrastructure:**
  - `platform/`: 5 scaffolded modules (`common-library`, `config-server`, `discovery-server`, `security-library`, `shared-kernel`).
  - `infrastructure/`: 7 infrastructure directories (`docker`, `helm`, `kubernetes`, `logging`, `mongodb`, `monitoring`, `security`), where `mongodb` houses Docker Compose setup and the other 6 are `.gitkeep` scaffolds.

---

## 2. Complete Service Inventory Table

| # | Current Service Directory | Service ID | Port | Primary CampXSync Domain | Proposed Destination Directory | Impl Status | Confidence | Ownership Notes / Ambiguities |
|---|---------------------------|------------|------|--------------------------|--------------------------------|-------------|------------|-------------------------------|
| 1 | `academic-calendar-service` | ACD-07 | 8089 | Academic Services | `services/academic-services/academic-calendar-service` | Active (25 src / 3 test) | Definitive (95%) | Minor overlap with early ADM-01 calendar tables; ACD-07 is authoritative academic calendar. |
| 2 | `ai-service` | AI-01 | N/A | AI Services | `services/ai-services/ai-service` | Scaffolded (.gitkeep) | High (95%) | Placeholder for future CampXSync Intelligence Layer adapters and ML agents. |
| 3 | `analytics-service` | ACD-10 | 8092 | Analytics Services (or Academic) | `services/analytics-services/analytics-service` | Active (18 src / 4 test) | Medium (75%) | Java package is `com.campx.academic.analytics` (ACD-10). Currently focused on academic facts, but natural home is cross-domain `analytics-services`. |
| 4 | `assessment-mapping-service` | ACD-09 | 8091 | Academic Services (or Examination) | `services/academic-services/assessment-mapping-service` | Active (31 src / 2 test) | Medium-High (80%) | Manages Outcome-Based Education (OBE) CO/PO and rubric weights. Could be argued for `examination-services`. |
| 5 | `attendance-service` | ACD-06 | 8088 | Attendance Services (or Academic) | `services/attendance-services/attendance-service` | Active (39 src / 7 test) | High (85%) | Packaged as `com.campx.academic.attendance`. Candidate for standalone `attendance-services` domain or staying under `academic-services`. |
| 6 | `batch-management-service` | ACD-04 | 8086 | Academic Services | `services/academic-services/batch-management-service` | Active (28 src / 4 test) | Definitive (100%) | Core academic domain: sections, student-batch assignment, capacity tracking. |
| 7 | `college-admin-service` | ADM-02 | 8082 | Administration Services | `services/administration-services/college-admin-service` | Active (39 src / 18 test) | Definitive (100%) | College operational tier: departments, degree programs, local policies, settings. |
| 8 | `course-management-service` | ACD-01 | 8083 | Academic Services | `services/academic-services/course-management-service` | Active (21 src / 9 test) | Definitive (100%) | Master course catalog, prerequisite directed acyclic graphs (DAGs), credits. |
| 9 | `curriculum-service` | ACD-02 | 8084 | Academic Services | `services/academic-services/curriculum-service` | Active (25 src / 4 test) | Definitive (100%) | Degree curricula, study schemes, semester syllabus matrices, credit governance. |
| 10 | `examination-service` | EXM-01 | N/A | Examination Services | `services/examination-services/examination-service` | Scaffolded (.gitkeep) | High (95%) | Placeholder for future exams, hall tickets, grading, transcripts. |
| 11 | `faculty-service` | FAC-01 | N/A | Faculty Services (or HRM) | `services/faculty-services/faculty-service` | Scaffolded (.gitkeep) | High (90%) | Placeholder for faculty profiles, workload, designations. Cross-cutting with HRM. |
| 12 | `identity-service` | IAM-01 | N/A | Identity Services | `services/identity-services/identity-service` | Scaffolded (.gitkeep) | High (95%) | Placeholder for centralized IAM/SSO token exchange. Gateway currently uses direct JDBC. |
| 13 | `institute-admin-service` | ADM-01 | 8081 | Administration Services (or Platform) | `services/administration-services/institute-admin-service` | Active (122 src / 62 test) | High (85%) | Multi-tenant onboarding, subscriptions, global policies. Dual-role: Platform vs Administration. |
| 14 | `learning-resource-service` | ACD-08 | 8090 | LMS Services (or Academic) | `services/lms-services/learning-resource-service` | Active (31 src / 3 test) | Medium-High (80%) | Packaged as `com.campx.academic.resource`. Manages digital content, study materials, syllabi. Classic LMS domain. |
| 15 | `notification-service` | NOTIF-01 | N/A | Communication Services | `services/communication-services/notification-service` | Scaffolded (.gitkeep) | High (95%) | Placeholder for multi-channel notifications (SMS, Email, Push, WhatsApp). |
| 16 | `student-service` | STU-01 | 8093 | Student Services | `services/student-services/student-service` | Active (69 src / 23 test) | Definitive (100%) | Student master record, guardian info, address, document verification, corrections roster. |
| 17 | `subject-management-service` | ACD-03 | 8085 | Academic Services | `services/academic-services/subject-management-service` | Active (26 src / 3 test) | Definitive (100%) | Subject definitions, course-subject mappings, Bloom's taxonomy levels, theory/lab credits. |
| 18 | `timetable-management-service` | ACD-05 | 8087 | Academic Services | `services/academic-services/timetable-management-service` | Active (37 src / 6 test) | Definitive (100%) | Timetable scheduling, room/faculty conflict prevention via PostgreSQL GiST exclusion. |

---

## 3. Detailed Profiles: Active Java Microservices

### 3.1 `academic-calendar-service` (ACD-07)
- **Current Directory:** `services/academic-calendar-service/`
- **Proposed Destination:** `services/academic-services/academic-calendar-service/`
- **Primary Domain:** `academic-services`
- **Classification Confidence:** **Definitive (95%)**
- **Java Root Package:** `com.campx.academic.calendar`
- **Main Server / Application:** `AcademicCalendarApplication`, `CalendarServer` (Port 8089)
- **Key Classes:** `CalendarDomainService`, `CalendarController`, `CalendarValidationEngine`, `CalendarModels`
- **Core Responsibilities:**
  - Manages academic calendars, terms (semesters/trimesters), working day matrices, and institutional event schedules.
  - Enforces term non-overlap policies and working-day overrides (e.g. Saturday working days vs holidays).
  - Versioning of academic calendars (`DRAFT` → `SUBMITTED` → `APPROVED` → `PUBLISHED`).
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `junit:junit`.
  - Database: In-memory collection engine with outbox event schema; pending PostgreSQL migration to `acd.academic_calendars`.
  - Downstream Consumers: `attendance-service` (validates sessions against term dates and holidays), `timetable-management-service` (validates timetable slot calendar validity), `learning-resource-service` (term alignment).
- **Ownership Notes:** In Wave 1/2, `institute-admin-service` maintained early tenant setup tables (`adm.academic_years`, `adm.calendars`), but ACD-07 is the authoritative academic operational calendar.

---

### 3.2 `analytics-service` (ACD-10)
- **Current Directory:** `services/analytics-service/`
- **Proposed Destination:** `services/analytics-services/analytics-service/` (Alternative: `services/academic-services/analytics-service/`)
- **Primary Domain:** `analytics-services`
- **Classification Confidence:** **Medium (75%)**
- **Java Root Package:** `com.campx.academic.analytics`
- **Main Server / Application:** `AnalyticsApplication`, `AnalyticsServer` (Port 8092)
- **Key Classes:** `AnalyticsDomainService`, `AnalyticsController`, `PostgresAnalyticsDashboardRepository`, `FactNormalizer`, `RuleEngine`, `ExportService`
- **Core Responsibilities:**
  - Ingestion and normalization of analytical facts (`analytics_facts`, `attendance_metrics`, `academic_metrics`, `timetable_metrics`).
  - Pre-aggregation of operational dashboards, executive KPIs, and student performance metrics.
  - Asynchronous report generation (`CSV`, `XLSX`, `PDF`) via `ReportJob` lifecycle.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `com.fasterxml.jackson.core:jackson-databind`, `junit:junit`.
  - Database: PostgreSQL tables for metric facts, dashboard projections, and `sys.outbox_events`.
  - Integration: Ingests events from `attendance-service`, `batch-management-service`, and `student-service`.
- **Classification Ambiguity:** The Java package name is `com.campx.academic.analytics` and the architectural code is `ACD-10`. Currently, its analytical facts cover academic operations (attendance, batch progress, timetable utilization). If CampXSync maintains a centralized enterprise analytics domain (finance, admissions, HR, academics), it should reside in `analytics-services/`. If analytics is decentralized per domain, this should be renamed/classified as `academic-services/academic-analytics-service`.

---

### 3.3 `assessment-mapping-service` (ACD-09)
- **Current Directory:** `services/assessment-mapping-service/`
- **Proposed Destination:** `services/academic-services/assessment-mapping-service/` (Alternative: `services/examination-services/assessment-mapping-service/`)
- **Primary Domain:** `academic-services`
- **Classification Confidence:** **Medium-High (80%)**
- **Java Root Package:** `com.campx.academic.assessment`
- **Main Server / Application:** `AssessmentMappingApplication`, `AssessmentServer` (Port 8091)
- **Key Classes:** `AssessmentDomainService`, `AssessmentController`, `OutcomeMappingEngine`, `WeightageValidationEngine`, `AssessmentVersionManager`
- **Core Responsibilities:**
  - Outcome-Based Education (OBE) curriculum alignment: Course Outcomes (CO) to Program Outcomes (PO) and Program Specific Outcomes (PSO).
  - Assessment structure definition: Continuous Internal Evaluation (CIE) vs Semester End Examination (SEE) weightages.
  - Bloom's Revised Taxonomy cognitive level tagging across evaluation components.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `junit:junit`.
  - Internal Clients: `CourseReferenceClient`, `CurriculumReferenceClient`, `SubjectReferenceClient`.
- **Classification Ambiguity:** In university governance, curriculum outcome mapping and syllabus rubrics are managed by Academic Boards (`academic-services`), while exam scheduling, question papers, and marks processing are managed by the Controller of Examinations (`examination-services`). Given ACD-09's focus on curriculum outcome mapping and weightage schemes, `academic-services` is the recommended primary owner.

---

### 3.4 `attendance-service` (ACD-06)
- **Current Directory:** `services/attendance-service/`
- **Proposed Destination:** `services/attendance-services/attendance-service/` (Alternative: `services/academic-services/attendance-service/`)
- **Primary Domain:** `attendance-services`
- **Classification Confidence:** **High (85%)**
- **Java Root Package:** `com.campx.academic.attendance`
- **Main Server / Application:** `AttendanceManagementApplication`, `AttendanceServer` (Port 8088)
- **Key Classes:** `AttendanceDomainService`, `AttendanceController`, `PostgresAttendanceRepository`, `GatewayHmacVerifier`, `UserSecurityContext`
- **Core Responsibilities:**
  - Daily session lifecycle: initialization, active marking, review, closure, and lock.
  - Roster attendance marking with statuses: `PRESENT`, `ABSENT`, `LATE`, `EXCUSED`.
  - Student attendance correction requests, audit trails, and multi-tenant RLS isolation.
  - Enforces duplicate session prevention and duplicate student marking constraints.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `com.fasterxml.jackson.core:jackson-databind`, `junit:junit`.
  - Database: PostgreSQL tables `acd.attendance_sessions`, `acd.attendance_records`, `acd.attendance_corrections`, `sys.outbox_events`.
  - Upstream Links: Relies on `timetable-management-service` for scheduled slots and `batch-management-service` for active student rosters.
- **Classification Ambiguity:** Package prefix is `com.campx.academic.attendance`. However, enterprise ERP designs frequently split Attendance into a top-level domain (`attendance-services`) to incorporate biometric device sync, faculty/staff attendance, and condonation processing alongside student attendance.

---

### 3.5 `batch-management-service` (ACD-04)
- **Current Directory:** `services/batch-management-service/`
- **Proposed Destination:** `services/academic-services/batch-management-service/`
- **Primary Domain:** `academic-services`
- **Classification Confidence:** **Definitive (100%)**
- **Java Root Package:** `com.campx.academic.batch`
- **Main Server / Application:** `BatchManagementApplication`, `BatchServer` (Port 8086)
- **Key Classes:** `BatchDomainService`, `BatchController`, `PostgresBatchAssignmentRepository`, `GatewayHmacVerifier`, `UserSecurityContext`
- **Core Responsibilities:**
  - Academic year cohorts, sections (e.g., Section A, Section B), and practical batch divisions.
  - Student batch assignment (`acd.batch_students`) and capacity ceiling enforcement.
  - Batch split/merge lifecycle governance with administrative approval gates.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `com.fasterxml.jackson.core:jackson-databind`, `junit:junit`.
  - Database: PostgreSQL tables `acd.batch_students`, `sys.outbox_events`.
  - Cross-Domain: Bridges `academic-services` with `student-services` (`stu.students`).

---

### 3.6 `college-admin-service` (ADM-02)
- **Current Directory:** `services/college-admin-service/`
- **Proposed Destination:** `services/administration-services/college-admin-service/`
- **Primary Domain:** `administration-services`
- **Classification Confidence:** **Definitive (100%)**
- **Java Root Package:** `com.campx.admin.college`
- **Main Server / Application:** `CollegeAdminApplication`, `CollegeAdminServer` (Port 8082)
- **Key Classes:** `CollegeAdminDomainService`, `CollegeAdminController`, `PostgresDepartmentRepository`, `PostgresProgramRepository`, `PostgresCollegeProfileRepository`, `GatewayHmacVerifier`
- **Core Responsibilities:**
  - College operational administration within multi-college university trusts.
  - Academic departments (`adm.departments`) and degree programs (`adm.programs`).
  - College-level configuration settings, governance documents, and localized policy overrides.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `com.fasterxml.jackson.core:jackson-databind`, `junit:junit`.
  - Database: PostgreSQL schemas `adm` and `plat`.

---

### 3.7 `course-management-service` (ACD-01)
- **Current Directory:** `services/course-management-service/`
- **Proposed Destination:** `services/academic-services/course-management-service/`
- **Primary Domain:** `academic-services`
- **Classification Confidence:** **Definitive (100%)**
- **Java Root Package:** `com.campx.academic.course`
- **Main Server / Application:** `CourseManagementApplication`, `CourseServer` (Port 8083)
- **Key Classes:** `CourseDomainService`, `CourseController`, `PostgresCourseRepository`, `GatewayHmacVerifier`, `UserSecurityContext`
- **Core Responsibilities:**
  - Master course catalog: course codes, credit values, prerequisites, and co-requisites.
  - Prerequisite validation and cycle detection engine preventing circular course dependencies.
  - Course offering status lifecycle (`DRAFT`, `ACTIVE`, `RETIRED`).
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `junit:junit`.
  - Database: PostgreSQL table `acd.courses`, RLS policies.

---

### 3.8 `curriculum-service` (ACD-02)
- **Current Directory:** `services/curriculum-service/`
- **Proposed Destination:** `services/academic-services/curriculum-service/`
- **Primary Domain:** `academic-services`
- **Classification Confidence:** **Definitive (100%)**
- **Java Root Package:** `com.campx.academic.curriculum`
- **Main Server / Application:** `CurriculumManagementApplication`, `CurriculumServer` (Port 8084)
- **Key Classes:** `CurriculumDomainService`, `CurriculumController`, `CurriculumModels`, `OutboxRelayService`
- **Core Responsibilities:**
  - Degree program curricula and schemes of study across semesters/academic terms.
  - Total degree credit policies, minimum elective credits, and core requirements.
  - Curriculum versioning and immutable publishing controls.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `junit:junit`.
  - Cross-Service: Validates against `course-management-service` and `subject-management-service`.

---

### 3.9 `institute-admin-service` (ADM-01)
- **Current Directory:** `services/institute-admin-service/`
- **Proposed Destination:** `services/administration-services/institute-admin-service/` (Alternative: `services/platform-services/institute-admin-service/`)
- **Primary Domain:** `administration-services`
- **Classification Confidence:** **High (85%)**
- **Java Root Package:** `com.campx.admin.institute`
- **Main Server / Application:** `InstituteAdminApplication`, `InstituteAdminServer` (Port 8081)
- **Key Classes:** `InstituteAdminDomainService`, `InstituteAdminController`, `UserPreferenceController`, `TenantProvisioningStateMachine`, `PostgresTenantRepository`, `PostgresSubscriptionRepository`
- **Core Responsibilities:**
  - Multi-tenant institute lifecycle: tenant onboarding, provisioning, and suspension.
  - Subscription tier management, feature flag entitlements, and billing gateway invoices.
  - Enterprise audit change log, security access events, global lookup values, and number sequences.
  - User preferences and theme configurations across all portal personas.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `junit:junit`.
  - Database: PostgreSQL schemas `adm` and `plat` (tables `plat.tenants`, `plat.subscriptions`, `plat.invoices`, etc.).
- **Classification Ambiguity:** `institute-admin-service` acts as both the SaaS platform provider console (super-admin, billing, tenant lifecycle) and the multi-college trust administrative console. Placing it in `administration-services` pairs it with `college-admin-service`. Placing it in `platform-services` reflects its tenant-provisioning and SaaS billing responsibilities.

---

### 3.10 `learning-resource-service` (ACD-08)
- **Current Directory:** `services/learning-resource-service/`
- **Proposed Destination:** `services/lms-services/learning-resource-service/` (Alternative: `services/academic-services/learning-resource-service/`)
- **Primary Domain:** `lms-services`
- **Classification Confidence:** **Medium-High (80%)**
- **Java Root Package:** `com.campx.academic.resource`
- **Main Server / Application:** `LearningResourceApplication`, `ResourceServer` (Port 8090)
- **Key Classes:** `ResourceDomainService`, `ResourceController`, `ResourceAccessPolicyService`, `ResourceVersionManager`, `ObjectStorageAdapter`
- **Core Responsibilities:**
  - Digital learning assets: lecture notes, study materials, lab manuals, question banks, and video references.
  - Role-based and department-scoped access governance for digital courseware.
  - Version management and binary object storage metadata linkage.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `junit:junit`.
  - Cross-Service Clients: `CurriculumReferenceClient`, `SubjectReferenceClient`, `AcademicCalendarReferenceClient`.
- **Classification Ambiguity:** Originally numbered `ACD-08` under the Academic Tier. However, managing study materials, course content, and student access policies is the defining role of an LMS (Learning Management System). Classifying it under `lms-services` creates the foundation for the CampXSync LMS domain.

---

### 3.11 `student-service` (STU-01)
- **Current Directory:** `services/student-service/`
- **Proposed Destination:** `services/student-services/student-service/`
- **Primary Domain:** `student-services`
- **Classification Confidence:** **Definitive (100%)**
- **Java Root Package:** `com.campx.student`
- **Main Server / Application:** `StudentApplication`, `StudentServer` (Port 8093)
- **Key Classes:** `StudentDomainService`, `StudentController`, `PostgresStudentRepository`, `PostgresPersonRepository`, `PostgresGuardianshipRepository`, `PostgresStudentDocumentRepository`, `StudentEventPublisher`
- **Core Responsibilities:**
  - Student Master Registry: student profile lifecycle, admission numbering, enrollment status.
  - Biographical and Person Master records (`core.persons`), parent/guardian linkages (`stu.guardianships`).
  - Document attachment, biometric/photo metadata, and multi-stage verification workflows.
  - Student self-service profile corrections, audit logs, and roster queries with multi-tenant RLS.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `com.fasterxml.jackson.core:jackson-databind`, `junit:junit`.
  - Database: PostgreSQL schemas `stu`, `core`, and `sys` (`sys.outbox_events`).

---

### 3.12 `subject-management-service` (ACD-03)
- **Current Directory:** `services/subject-management-service/`
- **Proposed Destination:** `services/academic-services/subject-management-service/`
- **Primary Domain:** `academic-services`
- **Classification Confidence:** **Definitive (100%)**
- **Java Root Package:** `com.campx.academic.subject`
- **Main Server / Application:** `SubjectManagementApplication`, `SubjectServer` (Port 8085)
- **Key Classes:** `SubjectDomainService`, `SubjectController`, `SubjectModels`, `OutboxRelayService`
- **Core Responsibilities:**
  - Master subject directory: subject codes, credit hours, lecture/tutorial/practical split.
  - Bloom's taxonomy tagging and subject prerequisite validations.
  - Subject catalog versioning and retirement governance.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `junit:junit`.
  - Downstream Consumers: `curriculum-service`, `assessment-mapping-service`, `timetable-management-service`.

---

### 3.13 `timetable-management-service` (ACD-05)
- **Current Directory:** `services/timetable-management-service/`
- **Proposed Destination:** `services/academic-services/timetable-management-service/`
- **Primary Domain:** `academic-services`
- **Classification Confidence:** **Definitive (100%)**
- **Java Root Package:** `com.campx.academic.timetable`
- **Main Server / Application:** `TimetableManagementApplication`, `TimetableServer` (Port 8087)
- **Key Classes:** `TimetableDomainService`, `TimetableController`, `PostgresTimetableRepository`, `GatewayHmacVerifier`, `UserSecurityContext`
- **Core Responsibilities:**
  - Institutional schedule management: master timetables, versions, entries, and slot allocations.
  - Real-time conflict exclusion: faculty double-booking, section double-booking, room overlap.
  - Backed by PostgreSQL GiST exclusion constraints (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) mapped to HTTP 409 Conflict.
- **Dependencies:**
  - Maven: `com.campx.platform:campx-logger`, `org.postgresql:postgresql`, `com.zaxxer:HikariCP`, `com.fasterxml.jackson.core:jackson-databind`, `junit:junit`.
  - Database: PostgreSQL tables `acd.timetables`, `acd.timetable_versions`, `acd.timetable_entries`, `sys.outbox_events`.

---

## 4. Scaffolded / Placeholder Directory Profiles

The following 5 directories currently exist under `services/` as empty scaffolding (containing only `.gitkeep`, with 0 Java files and no `pom.xml`):

### 4.1 `ai-service`
- **Current Directory:** `services/ai-service/`
- **Proposed Destination:** `services/ai-services/ai-service/`
- **Primary Domain:** `ai-services`
- **Target Role:** Host adapters for the CampXSync Intelligence Layer, AI copilot agents, automated timetable generation, and predictive student intervention models.

### 4.2 `examination-service`
- **Current Directory:** `services/examination-service/`
- **Proposed Destination:** `services/examination-services/examination-service/`
- **Primary Domain:** `examination-services`
- **Target Role:** Controller of Examinations (COE) portal, exam session scheduling, hall ticket generation, invigilation duty roster, marks processing, grade points calculation, and transcripts.

### 4.3 `faculty-service`
- **Current Directory:** `services/faculty-service/`
- **Proposed Destination:** `services/faculty-services/faculty-service/`
- **Primary Domain:** `faculty-services` (Alternative: `hrm-services`)
- **Target Role:** Faculty profiles, academic qualifications, research publications, teaching workload allocation, and department assignments.

### 4.4 `identity-service`
- **Current Directory:** `services/identity-service/`
- **Proposed Destination:** `services/identity-services/identity-service/`
- **Primary Domain:** `identity-services`
- **Target Role:** Centralized Identity and Access Management (IAM), OAuth2/OIDC token exchange, role resolution caching, and SSO directory synchronization.

### 4.5 `notification-service`
- **Current Directory:** `services/notification-service/`
- **Proposed Destination:** `services/communication-services/notification-service/`
- **Primary Domain:** `communication-services` (or `cam-services`)
- **Target Role:** Multi-channel notification engine (SMS, Email, WhatsApp, Mobile Push) consuming transactional outbox events from PostgreSQL (`sys.outbox_events`).

---

## 5. Non-Microservice Architectural Components

The following key directories exist outside `services/` and must NOT be moved into domain service directories:

1. **`api-gateway/`**:
   - Monorepo Location: Root directory (`api-gateway/`).
   - Role: Ingress reverse proxy, JWT claim extraction, route dispatching, and cryptographic HMAC trust boundary.
   - Recommendation: Preserve at root or relocate to `platform/gateway` in a separate platform phase. Must remain distinct from domain microservices.

2. **`logger/`**:
   - Monorepo Location: Root directory (`logger/`).
   - Role: Zero-dependency enterprise logging and distributed trace utility (`campx-logger`).
   - Recommendation: Retain at root or relocate to `platform/common-library/logger` as part of a formal platform library consolidation.

3. **`platform/`**:
   - Subdirectories: `common-library`, `config-server`, `discovery-server`, `security-library`, `shared-kernel`.
   - Role: Scaffolded enterprise libraries and infrastructure servers.
   - Recommendation: Retain as platform-level scaffolding.

4. **`infrastructure/`**:
   - Subdirectories: `mongodb`, `docker`, `helm`, `kubernetes`, `logging`, `monitoring`, `security`.
   - Role: Monorepo infrastructure, container definitions, and database provisioning scripts.
   - Recommendation: Retain as infrastructure management root.

5. **`frontend/` & `frontend-web/`**:
   - Role: Next.js and React dashboard applications.
   - Recommendation: Retain at root. Direct API calls target API Gateway (`http://localhost:8080`), not service directories.

6. **`database/`**:
   - Role: Flyway/Supabase SQL migrations and seed data.
   - Recommendation: Retain at root.

---

## 6. Summary of Classification Decisions & Open Governance Questions

1. **Academic Services Domain Boundary:**
   - **Included (6 Active Services):** `course-management-service` (ACD-01), `curriculum-service` (ACD-02), `subject-management-service` (ACD-03), `batch-management-service` (ACD-04), `timetable-management-service` (ACD-05), `academic-calendar-service` (ACD-07).
   - **Boundary Decision:** All 6 are directly coupled to academic governance, course catalogs, section enrollments, and timetables.

2. **Attendance Domain Decision:**
   - **Decision:** Classified under `services/attendance-services/attendance-service` to establish a dedicated domain for institutional attendance. If the team prefers minimizing top-level directories, it can sit under `academic-services/attendance-service`.

3. **LMS Domain Decision:**
   - **Decision:** `learning-resource-service` (ACD-08) is classified under `services/lms-services/learning-resource-service` because courseware content, versions, and digital assets represent the core of LMS functionality.

4. **Analytics Domain Decision:**
   - **Decision:** `analytics-service` (ACD-10) is classified under `services/analytics-services/analytics-service` because CampXSync's analytics roadmap spans academic metrics, financial reporting, and executive dashboards.

5. **Administration Domain Decision:**
   - **Decision:** `institute-admin-service` (ADM-01) and `college-admin-service` (ADM-02) are grouped under `services/administration-services/` representing the two-tier administrative hierarchy (Trust/Institute vs Campus/College).

