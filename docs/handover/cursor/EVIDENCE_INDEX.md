# CampXSync — Master Engineering Evidence Index
**Document:** `docs/handover/cursor/EVIDENCE_INDEX.md`  
**Governing Branch:** `feature/java-backend-integration`  
**Baseline Commit:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`  
**Generated On:** November 7, 2026  
**Auditor / Author:** Antigravity AI Platform Architecture Team  

---

## 1. Evidence Verification Methodology

Every claim in the CampXSync handover is mapped to concrete, verifiable source artifacts in the repository:
1. **Source Code Artifacts:** Java classes, methods, controllers, DTOs, and React/Next.js pages.
2. **Database Migrations:** Physical SQL DDL and paired reversible rollback scripts.
3. **Automated Test Evidence:** JUnit test classes, assertion suites, and live database runners.
4. **Git Commit History:** Specific commit SHAs documenting the introduction and verification of features.
5. **Configuration Files:** Gateway route tables, Maven aggregator POMs, and Next.js configs.

---

## 2. Master Implementation Evidence Matrix

| Claim / Functional Area | Source Code File & Symbol | Database Migration / Catalog Artifact | Test Suite / Execution Evidence | Git Commit SHA |
| :--- | :--- | :--- | :--- | :--- |
| **API Gateway Reverse Proxy & Routing** | [`GatewayConfig.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java#L49-L208)<br>`ReverseProxyHandler.java` | N/A (In-memory route table, 45+ prefixes) | `ApiGatewayRoutingTest`<br>`StudentGatewayRouteTest`<br>`AdmissionsGatewayRouteTest` | `cfb809f`<br>`67c1a25` |
| **Gateway HMAC Trust Boundary** | [`GatewayConfig.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java#L251-L350)<br>`GatewayHmacVerifier.java` | N/A (Cryptographic HMAC-SHA256 headers) | `GatewayHmacSecurityTest`<br>158/158 Gateway tests PASS | `44d7909`<br>`cfb809f` |
| **STM-02 Admissions Microservice** | [`AdmissionsStateEngine.java`](file:///d:/CampXSync/CampXSync_V2.0/services/student-services/admissions-service/src/main/java/com/campx/admissions/domain/AdmissionsStateEngine.java)<br>`EligibilityEvaluationEngine.java`<br>`ReviewAndDecisionService.java` | `database/migrations/20261101000100_create_stu_admissions_tables.sql`<br>Rollback: `20261101000100_rollback__create_stu_admissions_tables.sql` | `ApplicationControllerTest` (5/5 PASS)<br>`AdmissionsStateEngineTest` (5/5 PASS)<br>`verify_stm02_e2e_integration.mjs` (69/69 PASS) | `b21d3c7`<br>`3525819` |
| **STM-02 Intelligence Integration** | [`AdmissionsIntelligenceService.java`](file:///d:/CampXSync/CampXSync_V2.0/services/student-services/admissions-service/src/main/java/com/campx/admissions/intelligence/AdmissionsIntelligenceService.java)<br>`ControlledIntelligenceAdapter.java` | Emits 5 streams to `sys.outbox_events` | `AdmissionsIntelligenceIntegrationTest` (6/6 PASS) | `b21d3c7` |
| **STM-02 PostgreSQL JDBC Persistence** | `PostgresApplicationRepository.java`<br>`PostgresAdmissionIntakeRepository.java` (9 repos) | 9 tables in `stu` schema on `azulbaxszsqxymqmlwfe` | `PostgresAdmissionsRepositoryUnitTest` (3/3 PASS)<br>Live browser Playwright E2E | `b21d3c7` |
| **STM-01 Student Master & Self-Service** | [`student-service/src/.../PostgresPersonRepository.java`](file:///d:/CampXSync/CampXSync_V2.0/services/student-services/student-service/src/main/java/com/campx/student/repository/PostgresPersonRepository.java)<br>`PostgresStudentRepository.java` | `20261105000100_stm01_student_ownership_rls.sql`<br>`core.persons`, `stu.students`, `core.addresses` | `LivePostgresStudentPersistenceTest` (3/3 PASS)<br>`verify_stm01_suite.mjs` | `46cd60a`<br>`1a03355` |
| **STM-01 Applicant Document Upload** | `frontend/src/app/(dashboard)/student/documents/page.tsx`<br>`api/student/admissions/documents/route.ts` | `20261105000200_stm01_applicant_documents_rls.sql`<br>`20261105000300_seed_student2_applicant.sql` | `verify_phase3b_documents.mjs` (9/9 PASS)<br>`verify_phase4_matrix.mjs` (8/8 PASS) | `d758a1e`<br>`91cb726` |
| **Institutional Onboarding Saga (ADM-01)** | [`PostgresCollegeOnboardingRepository.java`](file:///d:/CampXSync/CampXSync_V2.0/services/administration-services/institute-admin-service/src/main/java/com/campx/institute/repository/PostgresCollegeOnboardingRepository.java)<br>`InstituteAdminDomainService.java` | `20261105000400_provision_apex_sci.sql`<br>`core.tenants`, `core.colleges`, `plat.tenant_provisioning` | `LivePostgresCollegeOnboardingTest` (2/2 PASS)<br>`verify_apex_sci_provisioning.mjs` (31/31 PASS) | `e111f8a` |
| **Batch Management Relational (ACD-04)** | `PostgresBatchAssignmentRepository.java`<br>`BatchDomainService.java` | `acd.batch_students`, `audit.events`<br>`FORCE ROW LEVEL SECURITY` | `BatchAssignmentRelationalTest`<br>62/62 tests PASS in ACD-04 | Wave 10 Baseline |
| **Timetable GiST Conflict Prevention (ACD-05)** | `PostgresTimetableRepository.java`<br>`TimetableDomainService.java` | `acd.timetables`, `acd.timetable_entries`<br>GiST constraints: `ex_tte_employee`, `ex_tte_section`, `ex_tte_room` | `LivePostgresTimetablePersistenceTest` (2/2 PASS)<br>Double-booking rejected live (HTTP 409) | Wave 5 Baseline |
| **Attendance Session & Corrections (ACD-06)** | `PostgresAttendanceRepository.java`<br>`AttendanceDomainService.java` | `acd.attendance_sessions`, `acd.attendance_records`, `acd.attendance_corrections` | `LivePostgresAttendancePersistenceTest` (1/1 PASS)<br>`LivePostgresAttendanceCorrectionsTest` (1/1 PASS) | Wave 6 & 7 Baseline |
| **Role Dashboards & Analytics (ACD-10)** | `PostgresAnalyticsDashboardRepository.java`<br>`AnalyticsDomainService.java` | Relational read queries on views & tables; statutory 75% shortage alert | `LivePostgresAnalyticsDashboardTest` (1/1 PASS)<br>38/38 tests PASS in ACD-10 | Wave 8 Baseline |
| **Domain-Based Directory Restructuring** | Root [`pom.xml`](file:///d:/CampXSync/CampXSync_V2.0/pom.xml)<br>6 Domain aggregator POMs | Relocation of 18 services to `services/{domain}-services/` | Full 22-module Maven reactor clean compilation; 0 test regressions | `cfb809f`<br>`67c1a25` |
| **Frontend Static Analysis & Build** | `frontend/tsconfig.json`<br>`frontend/package.json` | 26 dynamic & static routes generated | `npx tsc --noEmit` (0 errors)<br>`npm run build` (26/26 routes generated) | Freshly Executed 2026-11-07 |
| **Gate B Role Routing Matrix** | `frontend/src/middleware.ts`<br>`frontend/src/lib/auth/resolve-role.ts` | Real JWT role resolution for 4 roles | `scripts/run_role_routing_suite.ps1` (32/32 assertions PASS) | `9f038ae`<br>`e76e9c7` |
| **Test Session Production Safeguard** | `frontend/src/lib/auth/test-session-gate.ts` | Test session cookie disabled unconditionally in prod | Direct unit verification: returns 403 when `NODE_ENV === 'production'` | Wave 10 Baseline |

---

## 3. Discrepancies, Historical Contradictions, and Unverified Claims

To ensure complete accuracy, the following discrepancies between historical documentation and current code are highlighted:

1. **Course Management RLS Permission Code Discrepancy:**
   - *Claim:* Some historical reports claimed ACD-01 RLS policy bug was resolved.
   - *Evidence:* Forensic audit confirmed `V20261011_01__fix_acd01_courses_rls_policies.sql` is present in the repository but **was NEVER applied to live development ref `azulbaxszsqxymqmlwfe`**. Live catalog still checks `acd03.*` instead of `acd01.*`.
2. **Academic Services In-Memory vs PostgreSQL Persistence:**
   - *Claim:* Early prompts assumed all microservices write directly to PostgreSQL.
   - *Evidence:* Code audit reveals that ACD-01 (Courses), ACD-02 (Curricula), ACD-03 (Subjects), ACD-07 (Calendar), ACD-08 (Resources), and ACD-09 (Assessments) use in-memory collections as default implementations. Only ACD-04 (Batches), ACD-05 (Timetable), ACD-06 (Attendance), ACD-10 (Analytics), ADM-01/02, and STM-01/02 have PostgreSQL JDBC repositories implemented.
3. **Staging Database Reference Retirement:**
   - *Claim:* Older documents reference Supabase ref `kpwdvvcschankgggitnj` and College A / College B seeds.
   - *Evidence:* `kpwdvvcschankgggitnj` has been retired. The authorized active development reference is `azulbaxszsqxymqmlwfe`, which hosts `DEV_TEST_TENANT` and `APEX_SCI`.
4. **Retired Frontend Prototype:**
   - *Claim:* Some external documentation referenced `frontend-web/` (Vite).
   - *Evidence:* Formal deprecation notice exists in [`frontend-web/README.md`](file:///d:/CampXSync/CampXSync_V2.0/frontend-web/README.md). Canonical frontend is exclusively `frontend/` (Next.js 15).

---

## 4. Key Files & Configuration Registry

| Configuration | Repository Path | Critical Directives |
| :--- | :--- | :--- |
| **Root Maven Reactor** | [`pom.xml`](file:///d:/CampXSync/CampXSync_V2.0/pom.xml) | Java 8 (`<source>1.8</source>`), aggregates `logger`, `api-gateway`, and 6 active domain service modules. |
| **API Gateway Routes** | [`api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java`](file:///d:/CampXSync/CampXSync_V2.0/api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java) | Route table mappings, reverse proxy targets, HMAC security settings, JWKS config. |
| **Frontend Config** | `frontend/next.config.mjs`<br>`frontend/package.json` | Next.js 15, React 19, strict route compilation. |
| **Database Schemas** | `database/migrations/Scripts_V3.0/` | Base DDL for 20 schemas, tables, triggers, and RLS policies. |
| **Active Dev Ref Guard**| `frontend/src/lib/db.ts` | Asserts target database is NOT production ref `ekspahbsbwdpraaqfqui`. |
| **Wave Governance** | [`.agent/rules/campxsync-wave-control.md`](file:///d:/CampXSync/CampXSync_V2.0/.agent/rules/campxsync-wave-control.md)<br>[`AGENTS.md`](file:///d:/CampXSync/CampXSync_V2.0/AGENTS.md) | Mandatory wave sequence, architectural constraints, and safety controls. |
| **Next Antigravity Task** | [`prompts/NEXT_ANTIGRAVITY_TASK.md`](file:///d:/CampXSync/CampXSync_V2.0/prompts/NEXT_ANTIGRAVITY_TASK.md) | Baseline halted at Wave 10 completion. |

