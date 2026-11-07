# CampXSync — Architecture and Database Handover
**Document:** `docs/handover/cursor/ARCHITECTURE_AND_DATABASE.md`  
**Governing Branch:** `feature/java-backend-integration`  
**Baseline Commit:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`  
**Target Development Reference:** `azulbaxszsqxymqmlwfe` (`CampXSync-Architecture-Dev`)  
**Generated On:** November 7, 2026  
**Auditor / Author:** Antigravity AI Platform Architecture Team  

---

## 1. System Architecture Overview

CampXSync enforces an enterprise four-tier transactional architecture:

$$\text{Client / Browser} \xrightarrow[\text{JWT Bearer}]{\text{HTTPS}} \text{API Gateway (8080)} \xrightarrow[\text{HMAC-SHA256}]{\text{Internal HTTP}} \text{Java Domain Microservices} \xrightarrow[\text{Tenant Claims}]{\text{HikariCP JDBC}} \text{PostgreSQL 17 (Supabase)}$$

```
                                      [ Browser Client / Next.js 15 ]
                                                    │
                                                    ▼
                                      [ API Gateway (Port 8080) ]
                                    (JWT Validation & HMAC Signing)
                                                    │
                      ┌─────────────────────────────┼─────────────────────────────┐
                      ▼                             ▼                             ▼
           [ Platform & College ]              [ Academic ]                   [ Student ]
             ADM-01: 8081 (Inst)          ACD-01: 8083 (Courses)         STU-01: 8093 (Student)
             ADM-02: 8082 (College)       ACD-02: 8084 (Curricula)       STM-02: 8094 (Admissions)
                                          ACD-03: 8085 (Subjects)
                                          ACD-04: 8086 (Batches)
                                          ACD-05: 8087 (Timetable)
                                          ACD-06: 8088 (Attendance)
                                          ACD-07: 8089 (Calendar)
                                          ACD-08: 8090 (Resources)
                                          ACD-09: 8091 (Assessments)
                                          ACD-10: 8092 (Analytics)
                      │                             │                             │
                      └─────────────────────────────┼─────────────────────────────┘
                                                    ▼
                                    [ Supabase / PostgreSQL 17 ]
                                      (Development: azulbaxszsqxymqmlwfe)
                                    (App Role: campx_app_server / RLS)
```

### 1.1 Core Architectural Principles
1. **PostgreSQL as Authoritative Source of Truth:** All persistent state, constraints, and Row-Level Security reside in PostgreSQL. In-memory data structures are test doubles only and are prohibited from being authoritative in production runtimes.
2. **Java Microservices as Business Authority:** All validation, lifecycle transitions, state machines, and business rules execute within Java microservices running on Java 8 (`services/{domain}-services/`).
3. **API Gateway Trust Boundary:** The API Gateway (`api-gateway/`, Port 8080) acts as the unified reverse proxy. It validates client Supabase JWT tokens, resolves tenant and role claims, strips client identity headers, and cryptographically signs downstream requests using HMAC-SHA256 (`X-Gateway-Timestamp`, `X-Gateway-Signature`).
4. **Multi-Tenant Isolation:** Enforced at every layer:
   - Gateway resolves `tenant_id` from validated JWT claims.
   - Microservices propagate `tenant_id` via `UserSecurityContext`.
   - JDBC connections set PostgreSQL session parameters:
     ```sql
     SET LOCAL ROLE authenticated;
     SELECT set_config('request.jwt.claims', '{"sub":"...","tenant_id":"...","user_role":"...","college_id":"..."}', true);
     ```
   - PostgreSQL RLS filters all rows fail-closed.
5. **Transactional Outbox & Intelligence Layer:** Domain state changes emit events into `sys.outbox_events` within the same database transaction. The CampXSync Intelligence Layer ingests versioned event streams without bypassing transactional boundaries.

---

## 2. Java Microservice Inventory & Ports

| Service Name | Code | Domain Directory | Port | Persistence Engine | Gateway Ingress Prefix |
| :--- | :---: | :--- | :---: | :--- | :--- |
| **API Gateway** | GW | `api-gateway/` | **8080** | N/A (Reverse Proxy) | `/api/v1/**`, `/v1/**` |
| **Institute Admin Service** | ADM-01 | `services/administration-services/institute-admin-service` | **8081** | PostgreSQL JDBC + In-Memory | `/v1/institutes`, `/v1/onboard-tenant`, `/api/v1/user/preferences` |
| **College Admin Service** | ADM-02 | `services/administration-services/college-admin-service` | **8082** | PostgreSQL JDBC + In-Memory | `/v1/departments`, `/v1/programs`, `/v1/college-profile`, `/v1/college-audit-logs` |
| **Course Management Service** | ACD-01 | `services/academic-services/course-management-service` | **8083** | In-Memory (Postgres pending) | `/api/v1/courses`, `/v1/courses`, `/v1/course-catalog` |
| **Curriculum Service** | ACD-02 | `services/academic-services/curriculum-service` | **8084** | In-Memory (Postgres pending) | `/api/v1/curricula`, `/v1/curricula`, `/v1/curriculum-catalog` |
| **Subject Management Service** | ACD-03 | `services/academic-services/subject-management-service` | **8085** | In-Memory (Postgres pending) | `/api/v1/subjects`, `/api/v1/academics/subjects`, `/v1/subjects` |
| **Batch Management Service** | ACD-04 | `services/academic-services/batch-management-service` | **8086** | **PostgreSQL JDBC (HikariCP)** | `/api/v1/academics/batches`, `/v1/batches`, `/v1/batch-catalog` |
| **Timetable Management Service** | ACD-05 | `services/academic-services/timetable-management-service` | **8087** | **PostgreSQL JDBC (HikariCP)** | `/api/v1/academics/timetables`, `/v1/timetables`, `/v1/timetable-catalog` |
| **Attendance Service** | ACD-06 | `services/attendance-services/attendance-service` | **8088** | **PostgreSQL JDBC (HikariCP)** | `/api/v1/academics/attendance`, `/v1/attendance`, `/v1/attendance-sessions` |
| **Academic Calendar Service** | ACD-07 | `services/academic-services/academic-calendar-service` | **8089** | In-Memory (Postgres pending) | `/api/v1/calendars`, `/v1/calendars`, `/v1/calendar-terms` |
| **Learning Resource Service** | ACD-08 | `services/lms-services/learning-resource-service` | **8090** | In-Memory (Postgres pending) | `/api/v1/resources`, `/v1/resources`, `/v1/resource-versions` |
| **Assessment Mapping Service** | ACD-09 | `services/academic-services/assessment-mapping-service` | **8091** | In-Memory (Postgres pending) | `/api/v1/assessments`, `/v1/assessments`, `/v1/assessment-mappings` |
| **Analytics Service** | ACD-10 | `services/analytics-services/analytics-service` | **8092** | **PostgreSQL JDBC (HikariCP)** | `/api/v1/academics/analytics/dashboard`, `/v1/dashboards`, `/v1/kpis` |
| **Student Service** | STU-01 | `services/student-services/student-service` | **8093** | **PostgreSQL JDBC (HikariCP)** | `/api/v1/students`, `/v1/students`, `/v1/student-roster` |
| **Admissions Service** | STM-02 | `services/student-services/admissions-service` | **8094** | **PostgreSQL JDBC (HikariCP)** | `/api/v1/admissions`, `/v1/admissions`, `/api/v1/applications` |

### Scaffolded Services (Awaiting Implementation):
- `services/ai-services/ai-service`
- `services/communication-services/notification-service`
- `services/examination-services/examination-service`
- `services/faculty-services/faculty-service`
- `services/identity-services/identity-service`

---

## 3. Database Inventory & Migration Reality Check

### 3.1 Forensic Catalog Baseline (Development Reference: `azulbaxszsqxymqmlwfe`)
Based on the empirical catalog audit of 2026-10-26 and subsequent verified live migrations:
- **Application Schemas (20):** `acd`, `audit`, `bud`, `cam`, `cfg`, `core`, `doc`, `exm`, `fin`, `hrm`, `iam`, `lms`, `obe`, `pay`, `plat`, `rpt`, `stu`, `sys`, `tax`, `wf`.
- **Application Tables (467):** 458 baseline tables + 9 admissions tables in `stu` schema.
- **Partitioned Parent Tables (6):** `audit.events`, `audit.change_log`, `audit.access_events`, `cam.delivery_events`, `hrm.attendance_punches`, `lms.progress_events`.
- **Child Partitions (30):** 5 partitions per parent table (1 `DEFAULT` + 4 monthly partitions `p202610` through `p202701`).
- **Row-Level Security (RLS):** Enabled on **100% of application tables** (`relrowsecurity = true`).
- **FORCE ROW LEVEL SECURITY:** Enabled on 4 Tier 1 Core tables (`core.persons`, `stu.students`, `stu.guardianships`, `acd.batch_students`) ensuring table owners cannot bypass RLS.
- **Database Triggers (799+):** `touch_row` (updated_at timestamp stamping), `capture` (CDC change-log capture to `audit.change_log`), `guard_transition` (SoD state transition guards), `forbid_mutation` (immutable table guards).
- **Installed PostgreSQL Extensions (7):** `uuid-ossp`, `pgcrypto`, `btree_gist`, `pg_trgm`, `pg_stat_statements`, `plpgsql`, `supabase_vault`.

---

### 3.2 Script Distinction Matrix

To eliminate historical confusion, repository SQL artifacts are explicitly distinguished below:

| Migration Script | Location | Stated Purpose | Actual Verification Status on `azulbaxszsqxymqmlwfe` |
| :--- | :--- | :--- | :--- |
| `Scripts_V3.0/` (00 to 85) | `database/migrations/Scripts_V3.0/` | Initial enterprise schema bootstrap (20 schemas, RBAC matrix, tables, triggers, initial seed). | **APPLIED & VERIFIED** (Baseline catalog reflects these 458 tables, 799 triggers, and 1,545 policies). |
| `V20261011_01__fix_acd01_courses_rls_policies.sql` | `database/migrations/` | Bug fix: change `acd.courses` RLS checks from `acd03.*` to `acd01.*`. | **PRESENT IN REPO — NOT APPLIED**. Forensic audit verified live catalog still checks `acd03.*`. |
| `V20261014_01__enable_rls_on_monthly_partition_children.sql` | `database/migrations/` | Enable RLS on 24 monthly partition child tables. | **APPLIED & VERIFIED**. Confirmed `relrowsecurity = true` on all partition children. |
| `V20261014_02__provision_test_tenant_staging.sql` | `database/migrations/` | Provision test tenant on staging. | **HISTORICAL (STAGING)**. Applied to retired ref `kpwdvvcschankgggitnj`; obsolete for dev. |
| `V20261020_01__complete_partition_security_hardening.sql` | `database/migrations/` | Add missing partition child policies and tighten grants. | **APPLIED & VERIFIED**. |
| `V20261024_01__asymmetric_course_program_map_rls.sql` | `database/migrations/` | Option C asymmetric RLS on `acd.course_program_map`. | **APPLIED & VERIFIED**. Function `core.is_active_course_in_tenant()` verified in catalog. |
| `20261027000300_extend_partitions_and_schedule_maintenance.sql` | `database/migrations/` | Extend monthly partitions through 2027. | **APPLIED & VERIFIED**. |
| `20261101000100_create_stu_admissions_tables.sql` | `database/migrations/` | Create 9 STM-02 admissions tables in `stu` schema. | **APPLIED & VERIFIED LIVE**. All 9 tables exist with PKs, FKs, unique indexes, and RLS. |
| `20261102000100_stu_admissions_tighten_grants.sql` | `database/migrations/` | Tighten DML grants on admissions tables. | **APPLIED & VERIFIED LIVE**. |
| `20261105000100_stm01_student_ownership_rls.sql` | `database/migrations/` | Student ownership-scoped RLS policies on `stu.students` & `core.addresses`. | **APPLIED & VERIFIED LIVE**. Verified via `verify_stm01_suite.mjs`. |
| `20261105000200_stm01_applicant_documents_rls.sql` | `database/migrations/` | Applicant document upload & read RLS policies on `stu.application_documents`. | **APPLIED & VERIFIED LIVE**. Verified via `verify_phase3b_documents.mjs`. |
| `20261105000300_seed_student2_applicant.sql` | `database/migrations/` | Seed Student 2 applicant persona (`student2.campx@gmail.com`) with 1 rejected doc. | **APPLIED & VERIFIED LIVE**. Record exists in `stu.applications`. |
| `20261105000400_provision_apex_sci.sql` | `database/migrations/` | Provision second live institutional tenant `APEX_SCI` and college `SOC`. | **APPLIED & VERIFIED LIVE**. Verified via `verify_apex_sci_provisioning.mjs`. |

---

## 4. Security & Cryptographic Boundaries

### 4.1 Ingress Authentication Flow (Supabase JWT)
1. Browser signs in via Supabase Auth client (`frontend/src/lib/auth/`).
2. Browser receives standard Supabase JWT containing `sub` (User UUID).
3. Browser invokes API Gateway (`http://localhost:8080/api/v1/...`) passing `Authorization: Bearer <token>`.
4. `SupabaseJwtValidator` in API Gateway validates token signature using JWKS (`JwksClient`) or JWT secret.
5. `IdentityResolver` resolves user metadata from `iam.user_profiles` and active role assignments from `iam.role_assignments`.

### 4.2 Gateway-to-Service Trust Boundary (HMAC-SHA256)
Internal microservices bind to loopback (`127.0.0.1`) and never trust client-supplied identity headers.
1. The Gateway injects verified identity headers:
   - `X-User-Id`: Resolved user UUID.
   - `X-Tenant-Id`: Resolved tenant UUID.
   - `X-User-Role`: Resolved system role (e.g. `COLLEGE_ADMIN`, `FACULTY`, `STUDENT`).
   - `X-College-Id`: College UUID.
   - `X-Gateway-Timestamp`: Epoch millisecond timestamp.
2. The Gateway computes HMAC-SHA256 signature over method, URI, timestamp, user ID, tenant ID, and body:
   $$\text{Signature} = \text{HMAC-SHA256}(K_{\text{internal}}, \text{Payload})$$
   and attaches `X-Gateway-Signature`.
3. Downstream microservices enforce HMAC verification using `GatewayHmacVerifier`:
   - Checks signature matches.
   - Enforces replay window (rejects timestamps older than 60s to 300s).
   - Rejects missing or forged credentials with HTTP 401/403.

### 4.3 Database Session Security & Application Role
Downstream Java services connect to PostgreSQL via HikariCP using the dedicated application role:
- **Role Name:** `campx_app_server`
- **Privileges:** `LOGIN`, `NOSUPERUSER`, `NOBYPASSRLS`, `CONNECTION LIMIT 25`.
- **Session Setup Pattern:**
  ```java
  try (Connection conn = dataSource.getConnection()) {
      conn.setAutoCommit(false);
      try (Statement stmt = conn.createStatement()) {
          stmt.execute("SET LOCAL ROLE authenticated;");
          String claimsJson = String.format(
              "{\"sub\":\"%s\",\"tenant_id\":\"%s\",\"user_role\":\"%s\",\"college_id\":\"%s\"}",
              context.getUserId(), context.getTenantId(), context.getUserRole(), context.getCollegeId()
          );
          stmt.execute("SELECT set_config('request.jwt.claims', '" + claimsJson + "', true);");
      }
      // Execute repository operations...
      conn.commit();
  } catch (Exception e) {
      conn.rollback();
      throw e;
  }
  ```
- **Cleanup Guarantee:** In `finally` blocks, `RESET ROLE` is called, claims are cleared, and `autoCommit(true)` is restored before releasing the connection back to the HikariCP pool.

