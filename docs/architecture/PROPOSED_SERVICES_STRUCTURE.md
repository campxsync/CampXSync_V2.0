# CampXSync — Microservices Directory Architecture (Implemented & Verified)
**Document:** `PROPOSED_SERVICES_STRUCTURE.md`  
**Monorepo:** `CampXSync_V2.0`  
**Current Architecture:** Domain-Oriented Microservices Architecture (`services/{domain}-services/{microservice-name}/`)  
**Scale Target:** ~100 Domain Microservices  
**Implementation Date:** 30 October 2026  
**Execution Status:** **IMPLEMENTED & VERIFIED** (All Gates A–I Passed)  

---

> [!NOTE]
> **ARCHITECTURE IMPLEMENTATION NOTICE:**
> The domain-based directory structure detailed below is **active and verified** in the repository. All 18 microservice directories are organized under 11 domain directories (`academic-services`, `student-services`, `administration-services`, `attendance-services`, `lms-services`, `analytics-services`, `ai-services`, `examination-services`, `faculty-services`, `identity-services`, `communication-services`). All 22 Maven reactor modules compile and pass all tests cleanly. See [`docs/architecture/SERVICES_DIRECTORY_RESTRUCTURING_COMPLETION_REPORT.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/architecture/SERVICES_DIRECTORY_RESTRUCTURING_COMPLETION_REPORT.md) for full execution details.  

---

## 1. Architectural Vision & Structural Principles

To scale the CampXSync backend to approximately 100 microservices without cognitive overload or build degradation, the repository is transitioning from a flat `services/` layout to a **Domain-Driven Directory Hierarchy**:

```
services/
  └── {domain}-services/
        ├── pom.xml                                   (Domain Parent/Aggregator POM)
        └── {microservice-name}-service/             (Individual Java Microservice)
              ├── pom.xml                             (Service POM)
              ├── src/main/java/...
              └── src/test/java/...
```

### Key Architectural Tenets:
1. **Single Business Domain Ownership:** Every microservice belongs to exactly one domain parent directory based on its primary transactional and bounded-context responsibility.
2. **Standardized Directory Naming:** All domain directories use the `{domain}-services` plural pattern (e.g. `academic-services`, `student-services`, `administration-services`).
3. **Preservation of Service Identities:** Service Maven `artifactId`, Java package namespaces, HTTP route endpoints, and PostgreSQL table schema names remain **completely unchanged**.
4. **Separation of Platform Infrastructure:** API Gateway, shared logging, cross-cutting kernel libraries, and container infrastructure remain separated from business domain services.
5. **Non-Microservice Isolation:** Web applications (`frontend`, `frontend-web`), mobile apps (`mobile-app`), SQL migrations (`database/`), and automation scripts (`scripts/`) remain top-level siblings.

---

## 2. Complete Proposed Directory Tree

```
CampXSync_V2.0/
├── .agent/                                  # AI agent configurations and persistent wave control rules
├── .vscode/                                 # Shared IDE workspace settings
│
├── api-gateway/                             # [PLATFORM INGRESS] Unified HTTP reverse proxy & auth boundary (Port 8080)
│   ├── pom.xml
│   └── src/
│
├── logger/                                  # [SHARED PLATFORM UTILITY] Enterprise logging & tracing library (campx-logger)
│   ├── pom.xml
│   └── src/
│
├── platform/                                # [PLATFORM COMMON SCAFFOLDING] Shared libraries & infrastructure servers
│   ├── common-library/                      # Shared Java helper utilities and common DTOs
│   ├── config-server/                       # Centralized runtime configuration server
│   ├── discovery-server/                    # Dynamic service registry and health discovery
│   ├── security-library/                    # Cryptographic helpers, token verifiers, and RLS binders
│   └── shared-kernel/                       # Global domain events, value objects, and error envelopes
│
├── infrastructure/                          # [INFRASTRUCTURE & ORCHESTRATION] Deployment & container configurations
│   ├── docker/                              # Base Dockerfiles and multi-stage container specs
│   ├── helm/                                # Kubernetes Helm charts for CampXSync microservices
│   ├── kubernetes/                          # Kubernetes manifests (deployments, services, ingress)
│   ├── logging/                             # Logstash / Fluentd / OpenTelemetry collection configs
│   ├── mongodb/                             # MongoDB Compass setup script & docker-compose.yml
│   ├── monitoring/                          # Prometheus / Grafana metrics dashboards
│   └── security/                            # Vault / Secret management configurations
│
├── database/                                # [PERSISTENCE LAYER] Supabase / PostgreSQL migrations & seeds
│   ├── migrations/                          # Flyway / Supabase versioned SQL migration scripts
│   ├── schema/                              # Schema dumps and DDL definitions
│   └── seeds/                               # Staging and test tenant seed data
│
├── frontend/                                # [WEB APPLICATION] Next.js 15 + React 19 main ERP dashboard
├── frontend-web/                            # [LEGACY / LIGHTWEIGHT WEB] React 19 + Vite dashboard
├── mobile-app/                              # [MOBILE CLIENT] Cross-platform mobile client scaffold
│
├── scripts/                                 # [AUTOMATION & TESTING] Operational verification scripts
├── UserStories/                             # [REQUIREMENTS TRACEABILITY] Business user stories & audits
├── docs/                                    # [DOCUMENTATION] Architecture specs, wave reports, project control
│
├── pom.xml                                  # [ROOT MAVEN AGGREGATOR] Monorepo root parent POM
│
└── services/                                # [BUSINESS DOMAIN MICROSERVICES]
    │
    ├── academic-services/                   # === Academic Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM (Pattern B)
    │   ├── academic-calendar-service/       # [ACD-07, Port 8089] Academic calendars, terms, holidays (ACTIVE)
    │   ├── assessment-mapping-service/      # [ACD-09, Port 8091] OBE CO/PO mapping, Bloom's taxonomy (ACTIVE)
    │   ├── batch-management-service/        # [ACD-04, Port 8086] Cohorts, sections, batch assignments (ACTIVE)
    │   ├── course-management-service/       # [ACD-01, Port 8083] Master courses, prerequisites, credits (ACTIVE)
    │   ├── curriculum-service/              # [ACD-02, Port 8084] Degree curricula, schemes of study (ACTIVE)
    │   ├── subject-management-service/      # [ACD-03, Port 8085] Master subjects, syllabi, credits (ACTIVE)
    │   ├── timetable-management-service/    # [ACD-05, Port 8087] Master timetables, GiST conflict checks (ACTIVE)
    │   └── [FUTURE SERVICES]               # Planned: elective-selection-service, grading-scheme-service
    │
    ├── student-services/                    # === Student Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── student-service/                 # [STU-01, Port 8093] Student master record, profile, verification (ACTIVE)
    │   └── [FUTURE SERVICES]               # Planned: student-onboarding-service, student-discipline-service
    │
    ├── administration-services/             # === Institutional Administration Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── college-admin-service/           # [ADM-02, Port 8082] College campus profile, depts, programs (ACTIVE)
    │   ├── institute-admin-service/         # [ADM-01, Port 8081] Multi-tenant onboarding, subscriptions, billing (ACTIVE)
    │   └── [FUTURE SERVICES]               # Planned: delegation-service, accreditation-audit-service
    │
    ├── attendance-services/                 # === Attendance & Biometric Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── attendance-service/              # [ACD-06, Port 8088] Daily attendance sessions, marking, corrections (ACTIVE)
    │   └── [FUTURE SERVICES]               # Planned: biometric-integration-service, leave-management-service
    │
    ├── lms-services/                        # === Learning Management System Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── learning-resource-service/       # [ACD-08, Port 8090] Lecture notes, study materials, courseware (ACTIVE)
    │   └── [FUTURE SERVICES]               # Planned: assignment-submission-service, quiz-engine-service
    │
    ├── analytics-services/                  # === Analytics & Reporting Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── analytics-service/               # [ACD-10, Port 8092] Fact normalization, KPI dashboards, exports (ACTIVE)
    │   └── [FUTURE SERVICES]               # Planned: predictive-retention-service, accreditation-analytics-service
    │
    ├── ai-services/                         # === Artificial Intelligence & Intelligence Layer Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── ai-service/                      # [AI-01] Intelligence Layer adapters & recommendation engine (SCAFFOLD)
    │   └── [FUTURE SERVICES]               # Planned: timetable-copilot-service, student-advisor-agent-service
    │
    ├── examination-services/                # === Examination & Evaluation Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── examination-service/             # [EXM-01] Exam scheduling, hall tickets, invigilation (SCAFFOLD)
    │   └── [FUTURE SERVICES]               # Planned: marks-entry-service, transcript-generation-service
    │
    ├── faculty-services/                    # === Faculty & Academic Staff Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── faculty-service/                 # [FAC-01] Faculty profiles, teaching workload, qualifications (SCAFFOLD)
    │   └── [FUTURE SERVICES]               # Planned: faculty-appraisal-service, publication-tracking-service
    │
    ├── identity-services/                   # === Identity & Access Governance Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── identity-service/                # [IAM-01] Centralized IAM, token exchange, SSO directory sync (SCAFFOLD)
    │   └── [FUTURE SERVICES]               # Planned: role-delegation-service, api-key-service
    │
    ├── communication-services/              # === Communication & Outreach Bounded Context ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   ├── notification-service/            # [NOTIF-01] Multi-channel notifications: SMS, Email, WhatsApp (SCAFFOLD)
    │   └── [FUTURE SERVICES]               # Planned: announcement-broadcast-service, parent-messaging-service
    │
    ├── finance-services/                    # === Financial & Student Billing Bounded Context (PLANNED) ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   └── [FUTURE SERVICES]               # Planned: fee-structure-service, student-billing-service, payment-gateway-service
    │
    ├── hrm-services/                        # === Human Resource Management Bounded Context (PLANNED) ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   └── [FUTURE SERVICES]               # Planned: staff-payroll-service, recruitment-service, staff-leave-service
    │
    ├── cam-services/                        # === Campus Asset & Facility Management Bounded Context (PLANNED) ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   └── [FUTURE SERVICES]               # Planned: hostel-management-service, transport-service, inventory-service
    │
    ├── integration-services/                # === External System Integrations Bounded Context (PLANNED) ===
    │   ├── pom.xml                          # Domain aggregator POM
    │   └── [FUTURE SERVICES]               # Planned: government-nad-digilocker-service, biometric-device-sync-service
    │
    └── platform-services/                   # === Cloud Platform & Operational Services (PLANNED) ===
        ├── pom.xml                          # Domain aggregator POM
        └── [FUTURE SERVICES]               # Planned: tenant-audit-archive-service, event-outbox-relay-service
```

---

## 3. Microservice Distribution Matrix Across Proposed Domains

| Proposed Domain Directory | Count (Active) | Count (Scaffold) | Total Services | Included Services |
|---------------------------|----------------|------------------|----------------|-------------------|
| `academic-services`       | 7              | 0                | 7              | `academic-calendar-service`, `assessment-mapping-service`, `batch-management-service`, `course-management-service`, `curriculum-service`, `subject-management-service`, `timetable-management-service` |
| `student-services`        | 1              | 0                | 1              | `student-service` |
| `administration-services` | 2              | 0                | 2              | `college-admin-service`, `institute-admin-service` |
| `attendance-services`     | 1              | 0                | 1              | `attendance-service` |
| `lms-services`            | 1              | 0                | 1              | `learning-resource-service` |
| `analytics-services`      | 1              | 0                | 1              | `analytics-service` |
| `ai-services`             | 0              | 1                | 1              | `ai-service` |
| `examination-services`    | 0              | 1                | 1              | `examination-service` |
| `faculty-services`        | 0              | 1                | 1              | `faculty-service` |
| `identity-services`       | 0              | 1                | 1              | `identity-service` |
| `communication-services`  | 0              | 1                | 1              | `notification-service` |
| `finance-services`        | 0              | 0                | 0              | Future Domain |
| `hrm-services`            | 0              | 0                | 0              | Future Domain |
| `cam-services`            | 0              | 0                | 0              | Future Domain |
| `integration-services`    | 0              | 0                | 0              | Future Domain |
| `platform-services`       | 0              | 0                | 0              | Future Domain |
| **TOTALS**                | **13**         | **5**            | **18**         | **18 Services Accounted For** |

---

## 4. Maven Aggregator Architecture Options: Pattern A vs Pattern B

A critical architectural decision is how Maven discovers and builds services once nested under domain directories.

### Pattern A: Flat Root Aggregator with Deeper Module Paths
In Pattern A, the root `pom.xml` directly references the nested subdirectories without intermediate POM files:
```xml
<!-- In root pom.xml -->
<modules>
    <module>logger</module>
    <module>api-gateway</module>
    <module>services/academic-services/course-management-service</module>
    <module>services/academic-services/curriculum-service</module>
    <module>services/student-services/student-service</module>
    <!-- ... all 100 services declared directly in root pom.xml ... -->
</modules>
```
- **Child POM `relativePath`:** Must be updated from `../../pom.xml` to `../../../pom.xml`.
- **Pros:**
  - Simple; does not introduce additional POM files.
  - Less Maven configuration overhead initially.
- **Cons:**
  - Root `pom.xml` becomes bloated with 100+ module lines.
  - No ability to run Maven commands at domain granularity (`mvn test` inside `academic-services/` will fail unless specified with `-pl`).

### Pattern B: Two-Tier Hierarchical Domain Aggregator POMs (RECOMMENDED FOR SCALE)
In Pattern B, each domain directory contains its own parent/aggregator `pom.xml`:
```xml
<!-- In root pom.xml -->
<modules>
    <module>logger</module>
    <module>api-gateway</module>
    <module>services/academic-services</module>
    <module>services/student-services</module>
    <module>services/administration-services</module>
    <module>services/attendance-services</module>
    <module>services/lms-services</module>
    <module>services/analytics-services</module>
    <!-- Future domains added as single lines -->
</modules>
```
And inside `services/academic-services/pom.xml`:
```xml
<project>
    <modelVersion>4.0.0</modelVersion>
    <parent>
        <groupId>com.campx</groupId>
        <artifactId>campx-sync-parent</artifactId>
        <version>2.0.0-SNAPSHOT</version>
        <relativePath>../../pom.xml</relativePath>
    </parent>
    <artifactId>campx-academic-services</artifactId>
    <packaging>pom</packaging>
    <name>CampXSync Academic Services Aggregator</name>

    <modules>
        <module>academic-calendar-service</module>
        <module>assessment-mapping-service</module>
        <module>batch-management-service</module>
        <module>course-management-service</module>
        <module>curriculum-service</module>
        <module>subject-management-service</module>
        <module>timetable-management-service</module>
    </modules>
</project>
```
- **Child POM `relativePath`:** Points to `../pom.xml` (its domain parent) or `../../../pom.xml` (root).
- **Pros:**
  - **Domain-Bounded Builds:** Developers working on the Academic domain can run `mvn test` directly inside `services/academic-services/` without building unrelated domains.
  - **CI/CD Optimization:** Workflows can trigger tests on `services/{domain}-services` when only files in that domain change.
  - **Clean Root Configuration:** Root POM only maintains ~16 domain entries rather than 100 microservice entries.
  - **Domain Dependency Governance:** Common dependencies for a domain (e.g. standard reporting tools or specific math solvers) can be managed at the domain level.
- **Recommendation:** Implement **Pattern B** as the primary blueprint for the restructuring.

---

## 5. Non-Microservice Directories: Preservation Policy

The following directories must remain outside `services/`:

1. **`api-gateway/`**: Must remain at the top level. It provides edge reverse proxying and cryptographic HMAC signing across all downstream domain services.
2. **`logger/`**: Must remain at the top level or move to `platform/common-library/logger` in a subsequent platform task. It must NOT be nested inside any business domain.
3. **`platform/`**: Reserved for cross-cutting runtime infrastructure (discovery, config, security).
4. **`infrastructure/`**: Reserved for deployment tooling (Docker, Kubernetes, Helm, MongoDB, Prometheus).
5. **`database/`**: Single source of truth for PostgreSQL schema migrations and RLS definitions.
6. **`frontend/` & `frontend-web/`**: Client presentation layers.
7. **`scripts/`**: Operational verification and automation scripts.
8. **`docs/` & `UserStories/`**: Governance, architecture specifications, and requirements traceability.

