# CampXSync — Testing, Quality Assurance, and Defects Register
**Document:** `docs/handover/cursor/TESTING_AND_DEFECTS.md`  
**Governing Branch:** `feature/java-backend-integration`  
**Current HEAD Commit:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`  
**Audit Date:** November 7, 2026  
**Auditor / Author:** Antigravity AI Platform Architecture Team  

---

## 1. Testing Frameworks & Execution Architecture

The CampXSync platform employs a multi-tiered testing strategy designed to validate unit logic, cryptographic boundaries, relational database persistence, multi-tenant isolation, and end-to-end browser workflows:

```
[Level 1: Unit & Domain Logic]      -> JUnit 4, Mockito, AssertJ (Java 8)
[Level 2: Cryptographic & Routing]  -> GatewayRouteTest, GatewayHmacSecurityTest
[Level 3: Relational Persistence]   -> HikariCP JDBC Live Tests (azulbaxszsqxymqmlwfe)
[Level 4: Tenant Isolation & RLS]   -> Postgres RLS Injection Tests (Synthetic Tenants)
[Level 5: Static Analysis & Build]  -> TypeScript (tsc --noEmit), Next.js Build (npm run build)
[Level 6: End-to-End Browser]       -> Playwright / Chromium Automated Persona Walkthroughs
```

---

## 2. Test Execution Inventory Matrix

The following matrix distinguishes **Freshly Executed Tests** (run directly during this handover audit on Nov 7, 2026) from **Historically Verified Tests** (executed and recorded during Waves 4–10, directory restructuring, and STM-01/STM-02 delivery).

### 2.1 Complete Test Accounting Table

| Component / Test Suite | Type | Execution Date | Exact Command | Execution Category | Total | Pass | Fail | Skip | Verification Source |
| :--- | :---: | :---: | :--- | :---: | :---: | :---: | :---: | :---: | :--- |
| **Frontend Static Analysis** | Typecheck | 2026-11-07 | `cd frontend && npx tsc --noEmit` | **FRESHLY EXECUTED** | 26 routes | **26** | **0** | **0** | Direct CLI Output (Exit code 0) |
| **Markdown Integrity Scan** | Audit | 2026-11-07 | `node scratch/build_doc_index.mjs` | **FRESHLY EXECUTED** | 185 files | **185**| **0** | **0** | Direct CLI Output |
| **API Gateway Unit & Routes**| Gateway | 2026-10-30 | `mvn test -pl api-gateway "-Dtest=!Live*,!DevDatabaseSeedRunner"` | Historically Verified | 158 | **158**| **0** | **0** | Restructuring Report (Wave 10) |
| **Admissions Service (STM-02)**| Service | 2026-10-30 | `mvn test -pl services/student-services/admissions-service` | Historically Verified | 50 | **49** | **0** | **1** | `STM-02_TARGETED_COMPLETION_REPORT.md` |
| **Student Service (STU-01)** | Service | 2026-10-30 | `mvn test -pl services/student-services/student-service "-Dtest=!Live*"` | Historically Verified | 103 | **103**| **0** | **0** | Restructuring Report (Wave 10) |
| **Batch Management (ACD-04)** | Service | 2026-10-30 | `mvn test -pl services/academic-services/batch-management-service` | Historically Verified | 62 | **62** | **0** | **0** | `CONSOLIDATED_GO_LIVE_REPORT.md` |
| **Timetable Management (ACD-05)**| Service | 2026-10-30 | `mvn test -pl services/academic-services/timetable-management-service "-Dtest=!Live*"` | Historically Verified | 38 | **38** | **0** | **0** | Wave 5 Completion Report |
| **Attendance Service (ACD-06)**| Service | 2026-10-30 | `mvn test -pl services/attendance-services/attendance-service "-Dtest=!Live*"` | Historically Verified | 35 | **35** | **0** | **0** | Wave 6/7 Completion Report |
| **Analytics Service (ACD-10)**| Service | 2026-10-30 | `mvn test -pl services/analytics-services/analytics-service "-Dtest=!Live*"` | Historically Verified | 38 | **38** | **0** | **0** | Wave 8 Completion Report |
| **Institute Admin (ADM-01)** | Service | 2026-10-30 | `mvn test -pl services/administration-services/institute-admin-service "-Dtest=!Live*,!Postgres*" "-Dcampx.persistence.mode=in_memory"` | Historically Verified | 402 | **402**| **0** | **0** | Wave 9/10 Completion Report |
| **College Admin (ADM-02)** | Service | 2026-10-30 | `mvn test -pl services/administration-services/college-admin-service "-Dtest=!Live*"` | Historically Verified | 148 | **117**| **0** | **31**| Wave 3/10 Completion Report |
| **Course Management (ACD-01)**| Service | 2026-10-30 | `mvn test -pl services/academic-services/course-management-service` | Historically Verified | 104 | **78** | **0** | **26**| Restructuring Report |
| **Subject Management (ACD-03)**| Service | 2026-10-30 | `mvn test -pl services/academic-services/subject-management-service` | Historically Verified | 3 | **3** | **0** | **0** | Restructuring Report |
| **Platform Logger** | Library | 2026-10-30 | `mvn test -pl logger` | Historically Verified | 58 | **58** | **0** | **0** | Wave 1 Completion Report |
| **Gate B Role Routing Matrix**| E2E/HTTP | 2026-11-05 | `scripts/run_role_routing_suite.ps1` | Historically Verified | 32 | **32** | **0** | **0** | `PHASE_3B_AND_4_VERIFICATION_REPORT.md` |
| **Phase 3b Document RLS Matrix**| Live DB | 2026-11-05 | `node scripts/verify_phase3b_documents.mjs` | Historically Verified | 9 | **9** | **0** | **0** | `PHASE_3B_AND_4_VERIFICATION_REPORT.md` |
| **Phase 4 Cross-Role Isolation**| Live DB | 2026-11-05 | `node scripts/verify_phase4_matrix.mjs` | Historically Verified | 8 | **8** | **0** | **0** | `PHASE_3B_AND_4_VERIFICATION_REPORT.md` |
| **Live Tenancy Provisioning** | Live DB | 2026-11-05 | `node scripts/verify_apex_sci_provisioning.mjs` | Historically Verified | 31 | **31** | **0** | **0** | `APEX_SCI_PROVISIONING_REPORT.md` |
| **STM-02 Full-Stack E2E** | E2E | 2026-10-30 | `node scripts/verify_stm02_e2e_integration.mjs` | Historically Verified | 69 | **69** | **0** | **0** | `STM-02_TARGETED_COMPLETION_REPORT.md` |
| **Live Student Persistence** | Live DB | 2026-11-05 | `mvn test -pl services/student-services/student-service -Dtest=LivePostgresStudentPersistenceTest` | Historically Verified | 3 | **3** | **0** | **0** | `PHASE_3B_AND_4_VERIFICATION_REPORT.md` |
| **Live Timetable Persistence**| Live DB | 2026-10-21 | `mvn test -pl services/academic-services/timetable-management-service -Dtest=LivePostgresTimetablePersistenceTest` | Historically Verified | 2 | **2** | **0** | **0** | Wave 5 Completion Report |
| **Live Attendance Persistence**| Live DB | 2026-10-22 | `mvn test -pl services/attendance-services/attendance-service -Dtest=LivePostgresAttendancePersistenceTest` | Historically Verified | 1 | **1** | **0** | **0** | Wave 6 Completion Report |
| **Live Attendance Corrections**| Live DB | 2026-10-22 | `mvn test -pl services/attendance-services/attendance-service -Dtest=LivePostgresAttendanceCorrectionsTest` | Historically Verified | 1 | **1** | **0** | **0** | Wave 7 Completion Report |
| **Live Dashboard Analytics** | Live DB | 2026-10-22 | `mvn test -pl services/analytics-services/analytics-service -Dtest=LivePostgresAnalyticsDashboardTest` | Historically Verified | 1 | **1** | **0** | **0** | Wave 8 Completion Report |
| **Live College Onboarding** | Live DB | 2026-10-22 | `mvn test -pl services/administration-services/institute-admin-service -Dtest=LivePostgresCollegeOnboardingTest` | Historically Verified | 2 | **2** | **0** | **0** | Wave 9 Completion Report |

---

## 3. Defects Register and Resolution Status

| Defect ID | Description | Source / Location | Discovery Date | Resolution Status | Verified Resolution Evidence |
| :---: | :--- | :--- | :---: | :---: | :--- |
| **DEF-001** | `acd.courses` RLS policies check `acd03.*` instead of `acd01.*`. | `database/migrations/V20261011_01__fix_acd01_courses_rls_policies.sql` | 2026-10-11 | **OPEN (PENDING LIVE RUN)** | Migration script present in repo, but forensic audit confirmed not yet applied to `azulbaxszsqxymqmlwfe`. |
| **DEF-002** | `PostgresPersonRepository` did not SELECT or UPDATE address link columns. | `services/student-services/student-service/src/.../PostgresPersonRepository.java` | 2026-11-04 | **RESOLVED & VERIFIED** | Reinstated `permanent_address_id` and `current_address_id`. Commit `46cd60a`. 3/3 live persistence tests PASS. |
| **DEF-003** | Frontend direct SQL queries in `/college/batches` bypassing Gateway. | `frontend/src/app/(dashboard)/college/batches/page.tsx` | 2026-10-23 | **RESOLVED & VERIFIED** | Cut over to Gateway `/api/v1/academics/batches` and `/api/v1/academics/subjects`. Commit in Wave 10. |
| **DEF-004** | Direct database mutation in `/api/college/students/assign-batch`. | `frontend/src/app/api/college/students/assign-batch/route.ts` | 2026-10-23 | **RESOLVED & VERIFIED** | Cut over to Gateway `POST /api/v1/academics/batches/${id}/students`. PostgreSQL conflict upsert implemented in ACD-04. |
| **DEF-005** | Development test session cookie active in deployed environments. | `frontend/src/lib/auth/test-session-gate.ts` | 2026-10-17 | **RESOLVED & VERIFIED** | Added unconditional fail-closed gate: returns 403 whenever `NODE_ENV === 'production'` or `VERCEL_ENV === 'production'`. |
| **DEF-006** | Student self-approval vulnerability in attendance corrections. | `services/attendance-services/attendance-service/` & DB triggers | 2026-10-22 | **RESOLVED & VERIFIED** | Application-level requester != approver validation + PostgreSQL trigger `iam.guard_transition()` reject self-approval. |
| **DEF-007** | Timetable scheduling allowed double-booking across faculty/rooms. | `services/academic-services/timetable-management-service/` | 2026-10-21 | **RESOLVED & VERIFIED** | PostgreSQL GiST exclusion constraints (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) enforce atomic conflict prevention. |
| **DEF-008** | Incomplete student ownership RLS allowing cross-student document read. | `database/migrations/20261105000200_stm01_applicant_documents_rls.sql` | 2026-11-05 | **RESOLVED & VERIFIED** | Applied ownership policies; verified Student 1 cannot read Student 2 documents (9/9 checks PASS). |

---

## 4. Known Architectural Limitations & Technical Debt

1. **Academic Microservices Defaulting to In-Memory Storage:**
   - ACD-01 (Courses), ACD-02 (Curricula), ACD-03 (Subjects), ACD-07 (Calendar), ACD-08 (Resources), and ACD-09 (Assessments) use in-memory data structures in default runtime configurations.
   - *Technical Debt:* These must be upgraded to PostgreSQL JDBC repositories following the pattern established in ACD-04, ACD-05, and ACD-06.
2. **Absence of `schema_migrations` Table:**
   - The PostgreSQL catalog lacks a centralized migration version tracking table. Migrations have been applied through psql or MCP tools without an automated runner (Flyway / Liquibase).
3. **Java 8 Runtime Baseline:**
   - All 14 microservices and API Gateway compile against Java 8 (`<source>1.8</source>`). A formal Gate G3 assessment is required prior to considering JDK upgrades.
4. **Scaffolded Modules in Reactor:**
   - `services/ai-services`, `examination-services`, `faculty-services`, `identity-services`, `communication-services` are scaffolded `.gitkeep` directories without code.

