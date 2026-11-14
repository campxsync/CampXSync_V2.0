# CampXSync — Prioritized Backlog and Next Steps
**Document:** `docs/handover/cursor/BACKLOG_AND_NEXT_STEPS.md`  
**Governing Branch:** `feature/java-backend-integration`  
**Current HEAD Commit:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`  
**Effective Date:** November 7, 2026  
**Auditor / Author:** Antigravity AI Platform Architecture Team  

---

## 1. Executive Summary & Critical Path

The CampXSync platform has completed **Waves 1 through 10**, directory restructuring, STM-02 Admissions, and STM-01 Student Dashboard Phase 3b/4.

To achieve a trial-ready and production-ready College ERP product, development must focus strictly on **functional delivery** and **real PostgreSQL persistence** rather than unnecessary refactoring or theoretical redesigns.

### Critical Path Sequence:

$$\boxed{\text{Baseline Check}} \longrightarrow \boxed{\begin{matrix} \textbf{Option A: STM-03 Enrollment} \\ \text{(Course Registration Engine)} \end{matrix}} \longrightarrow \boxed{\begin{matrix} \textbf{ACD Relational Hardening} \\ \text{(ACD-01/02/03 Postgres JDBC)} \end{matrix}} \longrightarrow \boxed{\begin{matrix} \textbf{Option B: Wave 11} \\ \text{(Production Cutover \& G3)} \end{matrix}}$$

---

## 2. Decision Gate: Immediate Next Task Selection

The user must authorize either **Option A** or **Option B**:

| Option | Track | Core Objective | Key Deliverables | Estimated Effort |
| :---: | :--- | :--- | :--- | :---: |
| **Option A** | **Student Domain Track** | **STM-03 Academic Enrollment & Course Registration** | Consume `admissions.enrolment.handover_requested.v1`, register student in degree courses, allocate sections in ACD-04, check prerequisites in ACD-01. | 2–3 Days |
| **Option B** | **Platform Go-Live Track** | **Wave 11 Production Readiness & Cutover** | Formal Gate G3 JDK/license assessment, production schema sync, vulnerability audit, staging verification runbook. | 2 Days |

---

## 3. Prioritized Task Backlog Matrix

### Priority Legend
- **P0 (Critical / Blocker):** Must be completed before real college user trials or go-live.
- **P1 (High):** Core functionality required for complete semester operations.
- **P2 (Medium):** Operational enhancements, reporting, and secondary workflows.

---

### 3.1 Task Specifications

#### Task TASK-001: STM-03 Academic Enrollment & Course Registration Service
- **Module:** Student Services (`services/student-services/`)
- **Priority:** **P0**
- **Status:** **READY FOR EXECUTION (AWAITING AUTHORIZATION)**
- **Dependencies:** STM-01 (Student Master), STM-02 (Admissions Handover), ACD-01 (Course Catalog), ACD-04 (Batch/Section Allocation).
- **Source Location:** `services/student-services/`
- **Required Action:**
  1. Build Java microservice (or module inside `student-service`) listening on Port 8095 (or Port 8093).
  2. Implement event listener consuming `admissions.enrolment.handover_requested.v1` from `sys.outbox_events`.
  3. Implement course enrollment engine validating prerequisites, term credits, and core/elective rules.
  4. Implement section allocation calling ACD-04 (`/api/v1/academics/batches/{id}/students`).
  5. Register Gateway routes `/api/v1/enrollment/**` in `GatewayConfig.java`.
  6. Build frontend student course registration view under `/student/enrollment`.
- **Acceptance Criteria:**
  - Enrolled student can view mandatory core courses and select approved electives.
  - Prerequisite validation blocks course registration if prerequisite not cleared.
  - Credit limit validation enforces min/max semester credits.
  - Successful registration persists relational records in `stu.course_enrollments` and `acd.batch_students`.
  - Transactional outbox event `student.enrolled.v1` emitted.
- **Testing Requirements:** Unit test suite (min 30 tests), Gateway route test, live PostgreSQL persistence test with rollback, multi-tenant isolation test.
- **Documentation:** `docs/architecture/STM-03_ENROLLMENT_SPECIFICATION.md`.

---

#### Task TASK-002: Academic Domain Relational Persistence Hardening (ACD-01, ACD-02, ACD-03)
- **Module:** Academic Services (`services/academic-services/`)
- **Priority:** **P0**
- **Status:** **PENDING**
- **Dependencies:** PostgreSQL schema `acd` (`acd.courses`, `acd.curricula`, `acd.subjects`).
- **Source Location:**
  - `services/academic-services/course-management-service/`
  - `services/academic-services/curriculum-service/`
  - `services/academic-services/subject-management-service/`
- **Required Action:**
  1. Implement `PostgresCourseRepository` in ACD-01 replacing `InMemoryCourseRepository`.
  2. Implement `PostgresCurriculumRepository` in ACD-02.
  3. Implement `PostgresSubjectRepository` in ACD-03.
  4. Apply migration `V20261011_01__fix_acd01_courses_rls_policies.sql` to development database `azulbaxszsqxymqmlwfe` (fixing `acd03.*` to `acd01.*`).
  5. Implement HikariCP connection pool, `UserSecurityContext`, and `SET LOCAL ROLE authenticated`.
  6. Activate PostgreSQL live test suites (`LivePostgresCoursePersistenceTest`, etc.).
- **Acceptance Criteria:**
  - Deployed startup fails fast (`IllegalStateException`) if database connection is unavailable in deployed runtimes.
  - Courses, curricula, and subjects persist across restarts.
  - Multi-tenant isolation verified (Tenant A cannot see Tenant B courses).
- **Testing Requirements:** Full live persistence test suite on `azulbaxszsqxymqmlwfe` with 100% unconditional rollback.
- **Documentation:** `docs/reports/ACD_RELATIONAL_PERSISTENCE_REPORT.md`.

---

#### Task TASK-003: Wave 11 Production Readiness & Final Cutover
- **Module:** Platform / Cross-Domain
- **Priority:** **P0 (When Authorized)**
- **Status:** **HALTED AT WAVE 10 EXIT**
- **Dependencies:** User authorization phrase `GO WAVE-11`.
- **Source Location:** Root / All Services
- **Required Action:**
  1. **Step 1 (Preflight):** Verify environment secret propagation without exposing credentials; compile full Maven reactor cleanly; compile Next.js production build cleanly.
  2. **Step 2 (Gate G3):** Formal assessment of Java 8 compatibility, dependency vulnerability scanning, license compliance.
  3. **Step 3 (Production DB Check):** Verify zero unmigrated schema requirements against production target; verify RLS policies and `campx_app_server` grants match development ref `azulbaxszsqxymqmlwfe`.
  4. **Step 4 (Final Handover & Completion Report):** Produce `docs/reports/wave-11/COMPLETION_REPORT.md` and operational cutover runbook.
- **Acceptance Criteria:** All 4 gates pass; zero test failures; production migration script verified in dry-run mode.
- **Documentation:** `docs/reports/wave-11/COMPLETION_REPORT.md`.

---

#### Task TASK-004: Centralized Background Outbox Event Dispatcher
- **Module:** Shared Services (`sys.outbox_events`)
- **Priority:** **P1**
- **Status:** **PENDING**
- **Dependencies:** PostgreSQL table `sys.outbox_events`.
- **Source Location:** `services/administration-services/` or standalone worker
- **Required Action:**
  1. Build lightweight polling background worker (or Spring scheduled worker) querying `sys.outbox_events WHERE status = 'PENDING' ORDER BY created_at FOR UPDATE SKIP LOCKED`.
  2. Dispatch events to internal subscriber endpoints (e.g. `AdmissionsIntelligenceService`, notification handlers).
  3. Mark event as `PROCESSED` with `processed_at` timestamp, or increment `retry_count` and record `last_error`.
  4. Move poison messages to `sys.dead_letter_events` after 5 failed retries.
- **Acceptance Criteria:** Outbox events transition from `PENDING` $\rightarrow$ `PROCESSED` automatically; zero duplicate processing under concurrent workers.
- **Testing Requirements:** Concurrent dispatch unit test, crash-recovery test.

---

#### Task TASK-005: Faculty Management Service & Standalone Workload (FAC-01 / HRM)
- **Module:** Faculty Services (`services/faculty-services/faculty-service`)
- **Priority:** **P1**
- **Status:** **SCAFFOLDED**
- **Dependencies:** `core.persons`, `adm.departments`, `acd.timetable_entries`.
- **Source Location:** `services/faculty-services/faculty-service`
- **Required Action:**
  1. Implement Java microservice on Port 8096 for faculty workload, designations, and leave tracking.
  2. Implement Gateway routes `/api/v1/faculty/**`.
  3. Connect to PostgreSQL tables `core.employees`, `hrm.faculty_profiles`, `hrm.workload_allocations`.
- **Acceptance Criteria:** College Admin can view faculty teaching hours per week; conflict checks integrate with timetable.

---

#### Task TASK-006: Examination & Grading Service (EXM-01)
- **Module:** Examination Services (`services/examination-services/examination-service`)
- **Priority:** **P2**
- **Status:** **SCAFFOLDED**
- **Dependencies:** ACD-01 (Courses), ACD-09 (Assessments), STM-01 (Students).
- **Source Location:** `services/examination-services/examination-service`
- **Required Action:**
  1. Build Java microservice on Port 8097 for exam schedules, hall tickets, mark entries, and GPA calculation.
  2. Connect to PostgreSQL schema `exm`.
- **Acceptance Criteria:** Faculty can submit mid-term and end-term marks; grade calculations follow configured grading scales.

---

## 4. Recommended Execution Roadmap

```
Week 1 (Nov 7 - Nov 14, 2026):
  ├── Complete Baseline Audit Handover to Cursor (COMPLETE)
  ├── Authorize & Execute STM-03 Academic Enrollment (Task TASK-001)
  └── Apply Migration V20261011_01 on dev ref azulbaxszsqxymqmlwfe (Defect DEF-001)

Week 2 (Nov 15 - Nov 21, 2026):
  ├── ACD Relational Persistence Hardening (Task TASK-002: Courses, Curricula, Subjects)
  └── Implement Centralized Outbox Event Dispatcher (Task TASK-004)

Week 3 (Nov 22 - Nov 28, 2026):
  ├── Authorize Wave 11 Production Readiness & Gate G3 Assessment (Task TASK-003)
  └── Institutional User Trial Run on APEX_SCI & DEV_TEST_TENANT
```

