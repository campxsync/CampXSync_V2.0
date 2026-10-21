# Wave 4 — Phase 1: Student Database Schema, RLS & Tenant-Isolation Audit Report
## Architectural, Security, and Schema Preflight Investigation

**Date:** 2026-10-21  
**Auditor:** Antigravity Autonomous Agent  
**Branch:** `feature/java-backend-integration`  
**Governing Documents:** root `AGENTS.md`, `.agent/rules/campxsync-wave-control.md`, `docs/project-control/WAVE_IMPLEMENTATION_PLAN.md`, `docs/project-control/PROGRESS.md`, `prompts/NEXT_ANTIGRAVITY_TASK.md`  
**Database Reference Target:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`)  
**Scope Mode:** READ-ONLY INVESTIGATION ONLY — Zero Java Backend Code Implemented, Zero Database Modifications, Zero DDL/DML Migrations Executed.

---

## A. Executive Summary

This audit establishes the ground-truth technical state of the Student Module persistence layer, relational foreign key graph, Row-Level Security (RLS) enforcement, database role permissions, tenant context propagation, and isolation controls in CampXSync.

### Key Audit Findings:
1. **Schema Reality vs. Common Assumptions:**
   - The authoritative student domain is anchored by exactly **three core tables**: `stu.students` (academic record), `core.persons` (base human identity), and `stu.guardianships` (parent/guardian linkage).
   - In the academic operations tier, student cohort membership is tracked in `acd.batch_students` (batch/roster linking) and course section participation in `acd.section_enrollments`.
   - **Non-Existent Structures:** There are **NO dedicated student admission application tables** (e.g. `stu.admissions` or `stu.admission_applications` do not exist; admission numbers and dates are flat attributes on `stu.students`), **NO term-registration tables** (`acd.term_registrations` does not exist), and **NO duplicate student credential tables** (student authentication is delegated to `auth.users` and `iam.user_profiles` linked via `person_id`).
2. **Row-Level Security (RLS) Posture & Bypasses:**
   - PostgreSQL RLS is enabled on `stu.students`, `core.persons`, `stu.guardianships`, and `acd.batch_students`.
   - **CRITICAL GAP:** `FORCE ROW LEVEL SECURITY` is **NOT enabled** on ANY table in the catalog.
   - The application database connection configured in `frontend/src/lib/db.ts` and Java `DatabaseConfig.load()` connects using `SUPABASE_USER = postgres.azulbaxszsqxymqmlwfe` (the PostgreSQL superuser and table owner).
   - Under standard PostgreSQL semantics, table owners completely bypass RLS. Therefore, **all direct SQL queries in Next.js completely bypass RLS**. Tenant isolation currently rests entirely on application-level query predicates (`WHERE tenant_id = $1`).
3. **G1 Gate Status: FAILING / NOT VERIFIED (OPEN):**
   - Gate G1 strictly requires a dedicated least-privilege application login role without `BYPASSRLS` and transaction-scoped tenant/user context before enabling shared database persistence.
   - **Finding:** No dedicated application login role (e.g., `campx_app_server` or `campx_student_service`) has been created in the database or migration catalog. All backend connections connect as superuser `postgres`.
   - While the Java college-admin service introduced an in-transaction role switch (`SET LOCAL ROLE authenticated` + `SELECT set_config('request.jwt.claims', ...)` in `UserSecurityContext`), the initial connection is established by a superuser, and no dedicated unprivileged login role exists.
4. **Student Java Backend Readiness:**
   - Currently, `services/student-service` is an empty directory containing only `.gitkeep`.
   - The API Gateway (`api-gateway`) has no ingress routes mapped for `/v1/students` or `/api/v1/students`.
   - Zero Java domain models, repositories, controllers, or tests exist for the Student Module.
5. **Readiness Decision:**
   - **NOT READY; CRITICAL PREREQUISITES REMAIN.**
   - Before implementing student persistence in Java, the student domain model and schema foundation must be explicitly approved, and G1 security prerequisites (dedicated least-privilege role and `FORCE ROW LEVEL SECURITY`) must be reviewed and approved by the owner.

---

## B. Schema Inventory

The Student domain is designed as a normalized, shared-identity architecture where canonical human bio-demographic attributes live in `core.persons`, institutional academic standing lives in `stu.students`, and family/legal relationships live in `stu.guardianships`.

### 1. `stu.students` (Canonical Student Academic Anchor)
- **Schema & Table:** `stu.students`
- **Catalog Status:** Defined in `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/20_tables.sql` (Line 587). Confirmed present on live dev ref `azulbaxszsqxymqmlwfe`.
- **Columns & Data Types:**
  - `id` (uuid, PK, default `gen_random_uuid()`)
  - `tenant_id` (uuid, NOT NULL, default `core.current_tenant_id()`)
  - `person_id` (uuid, NOT NULL) — Foreign key to `core.persons(tenant_id, id)`
  - `college_id` (uuid, NOT NULL) — Foreign key to `core.colleges(tenant_id, id)`
  - `program_id` (uuid, NULLABLE) — Foreign key to `acd.programs(tenant_id, id)`
  - `enrollment_no` (text, NOT NULL) — Institutional roll/registration identifier
  - `admission_no` (text, NULLABLE) — Institutional admission application/docket number
  - `admission_date` (date, NULLABLE) — Date of matriculation
  - `admission_academic_year_id` (uuid, NULLABLE) — Foreign key to `core.academic_years(tenant_id, id)`
  - `status` (text, NOT NULL, default `'ACTIVE'`)
  - `created_at` (timestamptz, NOT NULL, default `now()`)
  - `updated_at` (timestamptz, NOT NULL, default `now()`)
  - `created_by` (uuid, NULLABLE, default `auth.uid()`)
  - `updated_by` (uuid, NULLABLE)
  - `row_version` (integer, NOT NULL, default 1)
  - `deleted_at` (timestamptz, NULLABLE) — Soft deletion lifecycle timestamp
- **Constraints:**
  - PK: `PRIMARY KEY (id)`
  - Tenant Uniqueness: `CONSTRAINT uq_students_tenant_id UNIQUE (tenant_id, id)`
  - Status Check: `CONSTRAINT ck_students_status CHECK (status IN ('ACTIVE', 'ALUMNI', 'SUSPENDED', 'WITHDRAWN', 'DETAINED', 'GRADUATED'))`
- **Foreign Keys:**
  - `fk_students_tenant`: `(tenant_id) REFERENCES core.tenants (id) ON DELETE RESTRICT`
  - `fk_students_person_id`: `(tenant_id, person_id) REFERENCES core.persons (tenant_id, id) ON DELETE RESTRICT`
  - `fk_students_college_id`: `(tenant_id, college_id) REFERENCES core.colleges (tenant_id, id) ON DELETE RESTRICT`
  - `fk_students_program_id`: `(tenant_id, program_id) REFERENCES acd.programs (tenant_id, id) ON DELETE RESTRICT`
  - `fk_students_admission_academic_year_id`: `(tenant_id, admission_academic_year_id) REFERENCES core.academic_years (tenant_id, id) ON DELETE RESTRICT`
- **Indexes:**
  - `uq_students_tenant_id_enrollment_no`: UNIQUE `(tenant_id, enrollment_no) WHERE deleted_at IS NULL`
  - `uq_students_tenant_id_person_id_college_id`: UNIQUE `(tenant_id, person_id, college_id) WHERE deleted_at IS NULL`
  - `ix_students_tenant_id_college_id_program_id_status`: `(tenant_id, college_id, program_id, status)`
  - `ix_students_person_id`: `(tenant_id, person_id)`
  - `ix_students_program_id`: `(tenant_id, program_id)`
  - `ix_students_admission_academic_year_id`: `(tenant_id, admission_academic_year_id)`
- **Triggers:**
  - `trg_touch`: `BEFORE UPDATE FOR EACH ROW EXECUTE FUNCTION sys.touch_row()` (bumps `updated_at`, `row_version`, `updated_by`)
  - `trg_audit`: `AFTER INSERT OR UPDATE OR DELETE FOR EACH ROW EXECUTE FUNCTION audit.capture()` (captures row-level diff into `audit.change_log`)

---

### 2. `core.persons` (Base Human Bio-Demographic Identity)
- **Schema & Table:** `core.persons`
- **Catalog Status:** Defined in `20_tables.sql` (Line 247). Confirmed present on live dev ref `azulbaxszsqxymqmlwfe`.
- **Columns & Data Types:**
  - `id` (uuid, PK, default `gen_random_uuid()`)
  - `tenant_id` (uuid, NOT NULL, default `core.current_tenant_id()`)
  - `first_name` (text, NOT NULL)
  - `middle_name` (text, NULLABLE)
  - `last_name` (text, NULLABLE)
  - `display_name` (text, NOT NULL)
  - `date_of_birth` (date, NULLABLE)
  - `gender` (text, NULLABLE)
  - `blood_group` (text, NULLABLE)
  - `nationality` (text, NULLABLE)
  - `marital_status` (text, NULLABLE)
  - `primary_email` (text, NULLABLE)
  - `primary_phone` (text, NULLABLE)
  - `photo_document_id` (uuid, NULLABLE) — Foreign key to `doc.documents(tenant_id, id)`
  - `permanent_address_id` (uuid, NULLABLE) — Foreign key to `core.addresses(tenant_id, id)`
  - `current_address_id` (uuid, NULLABLE) — Foreign key to `core.addresses(tenant_id, id)`
  - `status` (text, NOT NULL, default `'ACTIVE'`)
  - `created_at` (timestamptz, NOT NULL, default `now()`)
  - `updated_at` (timestamptz, NOT NULL, default `now()`)
  - `created_by` (uuid, NULLABLE, default `auth.uid()`)
  - `updated_by` (uuid, NULLABLE)
  - `row_version` (integer, NOT NULL, default 1)
  - `deleted_at` (timestamptz, NULLABLE)
- **Constraints:**
  - PK: `PRIMARY KEY (id)`
  - Tenant Uniqueness: `CONSTRAINT uq_persons_tenant_id UNIQUE (tenant_id, id)`
  - Gender Check: `CONSTRAINT ck_persons_gender CHECK (gender IN ('M', 'F', 'O', 'U'))`
  - Status Check: `CONSTRAINT ck_persons_status CHECK (status IN ('ACTIVE', 'INACTIVE'))`
- **Foreign Keys:**
  - `fk_persons_tenant`: `(tenant_id) REFERENCES core.tenants (id) ON DELETE RESTRICT`
  - `fk_persons_photo_document_id`: `(tenant_id, photo_document_id) REFERENCES doc.documents (tenant_id, id) ON DELETE RESTRICT`
  - `fk_persons_permanent_address_id`: `(tenant_id, permanent_address_id) REFERENCES core.addresses (tenant_id, id) ON DELETE RESTRICT`
  - `fk_persons_current_address_id`: `(tenant_id, current_address_id) REFERENCES core.addresses (tenant_id, id) ON DELETE RESTRICT`
- **Indexes:**
  - `ix_persons_tenant_id_display_name`: `(tenant_id, display_name)`
  - `ix_persons_tenant_id_primary_email`: `(tenant_id, primary_email)`
  - `ix_persons_tenant_id_primary_phone`: `(tenant_id, primary_phone)`
  - `ix_persons_photo_document_id`: `(tenant_id, photo_document_id)`
  - `ix_persons_permanent_address_id`: `(tenant_id, permanent_address_id)`
  - `ix_persons_current_address_id`: `(tenant_id, current_address_id)`
- **Triggers:**
  - `trg_touch`: `BEFORE UPDATE FOR EACH ROW EXECUTE FUNCTION sys.touch_row()`
  - `trg_audit`: `AFTER INSERT OR UPDATE OR DELETE FOR EACH ROW EXECUTE FUNCTION audit.capture()`

---

### 3. `stu.guardianships` (Parent & Legal Guardian Relationships)
- **Schema & Table:** `stu.guardianships`
- **Catalog Status:** Defined in `20_tables.sql` (Line 615). Confirmed present on live dev ref `azulbaxszsqxymqmlwfe`.
- **Columns & Data Types:**
  - `id` (uuid, PK, default `gen_random_uuid()`)
  - `tenant_id` (uuid, NOT NULL, default `core.current_tenant_id()`)
  - `student_id` (uuid, NOT NULL) — Foreign key to `stu.students(tenant_id, id)`
  - `guardian_person_id` (uuid, NOT NULL) — Foreign key to `core.persons(tenant_id, id)`
  - `relationship` (text, NOT NULL) — e.g. `'FATHER'`, `'MOTHER'`, `'LEGAL_GUARDIAN'`, `'SPONSOR'`
  - `is_primary` (boolean, NOT NULL, default `false`)
  - `can_view_academics` (boolean, NOT NULL, default `true`)
  - `can_view_fees` (boolean, NOT NULL, default `true`)
  - `can_pay_fees` (boolean, NOT NULL, default `false`)
  - `valid_from` (date, NULLABLE)
  - `valid_to` (date, NULLABLE)
  - `status` (text, NOT NULL, default `'ACTIVE'`)
  - `created_at` (timestamptz, NOT NULL, default `now()`)
  - `updated_at` (timestamptz, NOT NULL, default `now()`)
  - `created_by` (uuid, NULLABLE, default `auth.uid()`)
  - `updated_by` (uuid, NULLABLE)
  - `row_version` (integer, NOT NULL, default 1)
  - `deleted_at` (timestamptz, NULLABLE)
- **Constraints:**
  - PK: `PRIMARY KEY (id)`
  - Tenant Uniqueness: `CONSTRAINT uq_guardianships_tenant_id UNIQUE (tenant_id, id)`
  - Status Check: `CONSTRAINT ck_guardianships_status CHECK (status IN ('ACTIVE', 'SUSPENDED', 'ENDED'))`
- **Foreign Keys:**
  - `fk_guardianships_tenant`: `(tenant_id) REFERENCES core.tenants (id) ON DELETE RESTRICT`
  - `fk_guardianships_student_id`: `(tenant_id, student_id) REFERENCES stu.students (tenant_id, id) ON DELETE CASCADE`
  - `fk_guardianships_guardian_person_id`: `(tenant_id, guardian_person_id) REFERENCES core.persons (tenant_id, id) ON DELETE RESTRICT`
- **Indexes:**
  - `uq_guardianships_tenant_id_student_id_guardian_person_id`: UNIQUE `(tenant_id, student_id, guardian_person_id) WHERE deleted_at IS NULL`
  - `ix_guardianships_tenant_id_guardian_person_id`: `(tenant_id, guardian_person_id)`
  - `ix_guardianships_student_id`: `(tenant_id, student_id)`
- **Triggers:**
  - `trg_touch`: `BEFORE UPDATE FOR EACH ROW EXECUTE FUNCTION sys.touch_row()`
  - `trg_audit`: `AFTER INSERT OR UPDATE OR DELETE FOR EACH ROW EXECUTE FUNCTION audit.capture()`

---

### 4. Closely Associated Academic & Identity Tables

| Schema.Table | Role / Description | Relation to Student | Tenant Isolation Column |
|---|---|---|---|
| `acd.batch_students` | Cohort membership table. Connects a student to an academic batch (`acd.batches`) with a cohort-specific `roll_no`. | `(tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT` | `tenant_id uuid NOT NULL` |
| `acd.section_enrollments` | Course section enrollment table. Connects a student to a teaching section (`acd.course_sections`). Required anchor for attendance and continuous assessment. | `(tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT` | `tenant_id uuid NOT NULL` |
| `core.person_identifiers` | Statutory government IDs (Aadhaar, PAN, APAAR, Passport) with ciphertext (`bytea`), SHA-256 hash (`bytea`), and masked preview (`text`). | `(tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` | `tenant_id uuid NOT NULL` |
| `core.person_contacts` | Multichannel communication endpoints (personal email, institutional email, phone, WhatsApp). | `(tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` | `tenant_id uuid NOT NULL` |
| `core.person_emergency_contacts` | Emergency contact points with relationship, priority, and phone. | `(tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE` | `tenant_id uuid NOT NULL` |
| `iam.user_profiles` | Supabase Auth identity link (`id = auth.uid()`), binding user to `person_id`, `college_id`, `department_id`, and `tenant_id`. | `(tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT` | `tenant_id uuid NOT NULL` |
| `iam.role_assignments` | Binds `iam.user_profiles.id` to institutional roles (`STUDENT`, `COLLEGE_ADMIN`, `FACULTY`). | Relates to student's user profile | `tenant_id uuid NOT NULL` |

---

### 5. Non-Existent or Unverified Structures
- **Admissions Pipeline:** There are **NO** tables named `stu.admissions`, `stu.admission_applications`, `stu.candidatures`, or `stu.enrolment_applications`. Admission docket tracking is handled strictly as scalar columns (`admission_no`, `admission_date`, `admission_academic_year_id`) on `stu.students`.
- **Term Registration:** There is **NO** table named `acd.term_registrations` or `stu.term_registrations`. Term enrollment is represented by cohort assignment in `acd.batch_students` and section membership in `acd.section_enrollments`.
- **Separate Student Login Credentials:** There are **NO** tables named `stu.credentials` or `stu.logins`. User authentication is centralized under GoTrue `auth.users`, bound to `core.persons` via `iam.user_profiles`.

---

## C. RLS and Permission Matrix

| Table | RLS Enabled? | Forced RLS? | Policy Name | Command | Target Role | USING Expression | WITH CHECK Expression | Evidence Source |
|---|---|---|---|---|---|---|---|---|
| `stu.students` | **YES** | **NO** | `students_select` | `SELECT` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.view.all')) OR (college_id IN (SELECT unnest(iam.perm_colleges('acd06.view.all')))) OR (program_id IN (SELECT core.programs_in_scope('acd06.view.all', false)) OR (NOT false AND program_id IS NULL AND (SELECT iam.has_permission('acd06.view.all'))))) OR ((SELECT iam.has_permission('acd06.view.own')) AND ((SELECT iam.perm_all('acd06.view.own')) OR (college_id IN (SELECT unnest(iam.perm_colleges('acd06.view.own')))) OR (program_id IN (SELECT core.programs_in_scope('acd06.view.own', false)) OR (NOT false AND program_id IS NULL AND (SELECT iam.has_permission('acd06.view.own'))))) AND (id IN (SELECT core.faculty_student_ids()))) OR (id IN (SELECT core.my_student_ids())))` | — | `60_rls.sql` (line 117) |
| `stu.students` | **YES** | **NO** | `students_insert` | `INSERT` | `authenticated` | — | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.write')) OR (college_id IN (SELECT unnest(iam.perm_colleges('acd06.write')))) OR (program_id IN (SELECT core.programs_in_scope('acd06.write', true)) OR (NOT true AND program_id IS NULL AND (SELECT iam.has_permission('acd06.write'))))))` | `60_rls.sql` (line 118) |
| `stu.students` | **YES** | **NO** | `students_update` | `UPDATE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.write')) OR (college_id IN (SELECT unnest(iam.perm_colleges('acd06.write')))) OR (program_id IN (SELECT core.programs_in_scope('acd06.write', true)) OR (NOT true AND program_id IS NULL AND (SELECT iam.has_permission('acd06.write'))))))` | (Same as USING expression) | `60_rls.sql` (line 119) |
| `stu.students` | **YES** | **NO** | `students_delete` | `DELETE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.manage')) OR (college_id IN (SELECT unnest(iam.perm_colleges('acd06.manage')))) OR (program_id IN (SELECT core.programs_in_scope('acd06.manage', true)) OR (NOT true AND program_id IS NULL AND (SELECT iam.has_permission('acd06.manage'))))))` | — | `60_rls.sql` (line 120) |
| `stu.guardianships` | **YES** | **NO** | `guardianships_select` | `SELECT` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.view.all')) OR (student_id IN (SELECT core.students_in_scope('acd06.view.all', false)))) OR (guardian_person_id = (SELECT core.current_person_id())))` | — | `60_rls.sql` (line 1938) |
| `stu.guardianships` | **YES** | **NO** | `guardianships_insert` | `INSERT` | `authenticated` | — | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.write')) OR (student_id IN (SELECT core.students_in_scope('acd06.write', true)))))` | `60_rls.sql` (line 1939) |
| `stu.guardianships` | **YES** | **NO** | `guardianships_update` | `UPDATE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.write')) OR (student_id IN (SELECT core.students_in_scope('acd06.write', true)))))` | (Same as USING expression) | `60_rls.sql` (line 1940) |
| `stu.guardianships` | **YES** | **NO** | `guardianships_delete` | `DELETE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd06.manage')) OR (student_id IN (SELECT core.students_in_scope('acd06.manage', true)))))` | — | `60_rls.sql` (line 1941) |
| `core.persons` | **YES** | **NO** | `persons_select` | `SELECT` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('core.pii.read')) OR (photo_document_id IN (SELECT iam.sc_doc_documents('core.pii.read', false)) OR (NOT false AND photo_document_id IS NULL AND (SELECT iam.has_permission('core.pii.read'))))) OR (id = (SELECT core.current_person_id())))` | — | `60_rls.sql` (line 55) |
| `core.persons` | **YES** | **NO** | `persons_insert` | `INSERT` | `authenticated` | — | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('core.pii.write')) OR (photo_document_id IN (SELECT iam.sc_doc_documents('core.pii.write', true)) OR (NOT true AND photo_document_id IS NULL AND (SELECT iam.has_permission('core.pii.write'))))))` | `60_rls.sql` (line 56) |
| `core.persons` | **YES** | **NO** | `persons_update` | `UPDATE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('core.pii.write')) OR (photo_document_id IN (SELECT iam.sc_doc_documents('core.pii.write', true)) OR (NOT true AND photo_document_id IS NULL AND (SELECT iam.has_permission('core.pii.write'))))))` | (Same as USING expression) | `60_rls.sql` (line 57) |
| `core.persons` | **YES** | **NO** | `persons_delete` | `DELETE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('core.pii.write')) OR (photo_document_id IN (SELECT iam.sc_doc_documents('core.pii.write', true)) OR (NOT true AND photo_document_id IS NULL AND (SELECT iam.has_permission('core.pii.write'))))))` | — | `60_rls.sql` (line 58) |
| `acd.batch_students` | **YES** | **NO** | `batch_students_select` | `SELECT` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd07.view.all')) OR (student_id IN (SELECT core.students_in_scope('acd07.view.all', false)))) OR (student_id IN (SELECT core.my_student_ids())))` | — | `60_rls.sql` (line 509) |
| `acd.batch_students` | **YES** | **NO** | `batch_students_insert` | `INSERT` | `authenticated` | — | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd07.write')) OR (student_id IN (SELECT core.students_in_scope('acd07.write', true)))))` | `60_rls.sql` (line 510) |
| `acd.batch_students` | **YES** | **NO** | `batch_students_update` | `UPDATE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd07.write')) OR (student_id IN (SELECT core.students_in_scope('acd07.write', true)))))` | (Same as USING expression) | `60_rls.sql` (line 511) |
| `acd.batch_students` | **YES** | **NO** | `batch_students_delete` | `DELETE` | `authenticated` | `tenant_id = (SELECT core.current_tenant_id()) AND (((SELECT iam.perm_all('acd07.manage')) OR (student_id IN (SELECT core.students_in_scope('acd07.manage', true)))))` | — | `60_rls.sql` (line 512) |

### Grants Analysis (`75_grants.sql`):
- Schema `stu`: `GRANT USAGE TO authenticated, service_role;`
- Tables in `stu`: `GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA stu TO authenticated;`
- Default Privileges in `stu`: `ALTER DEFAULT PRIVILEGES IN SCHEMA stu GRANT SELECT, INSERT, UPDATE, DELETE ON TABLES TO authenticated;`
- Anonymous Role: `REVOKE ALL ON ALL TABLES IN SCHEMA stu FROM anon;` (Strict Deny-All for public/unauthenticated).
- Helper Functions: All functions executed by RLS (`core.current_tenant_id`, `core.current_person_id`, `core.my_student_ids`, `core.faculty_student_ids`, `core.students_in_scope`, `iam.has_permission`, `iam.perm_all`) are explicitly granted `EXECUTE` to `authenticated` and compiled with `SECURITY DEFINER SET search_path = ''`.

---

## D. Tenant-Context Flow

The end-to-end trace from client authentication to database row isolation:

```
[Browser Client]
       │
       │ Bearer Supabase JWT (signed with ES256)
       ▼
[Edge / API Gateway (port 8080)]
       │ 1. Validates Supabase JWT signature via JWKS
       │ 2. Extracts authoritative claims: sub (userId), tenant_id, role
       │ 3. Strips untrusted client headers (X-User-*, X-Tenant-*)
       │ 4. Injects authoritative verified headers:
       │      X-User-Id: <UUID>
       │      X-Tenant-Id: <UUID>
       │      X-User-Role: <ROLE>
       │ 5. HMAC-SHA256 signs payload + headers + timestamp (60s replay window)
       ▼
[Java Domain Service Boundary (e.g. college-admin / student-service)]
       │ 1. GatewayHmacVerifier verifies signature & timestamp freshness
       │ 2. UserSecurityContext extracted strictly from verified headers
       ▼
[Transaction Context Wrapper]
       │ 1. Connection acquired from connection pool (HikariCP)
       │ 2. conn.setAutoCommit(false);
       │ 3. UserSecurityContext.applyClaimsAndRole(conn):
       │      SET LOCAL ROLE authenticated;
       │      SELECT set_config('request.jwt.claims', '{"sub":"...","role":"authenticated","tenant_id":"..."}', true);
       ▼
[Domain Repository Layer (JDBC)]
       │ 1. Explicit SQL Predicates: WHERE s.tenant_id = ?
       │ 2. Submits SQL to PostgreSQL engine
       ▼
[PostgreSQL Kernel Enforcement]
       │ 1. Evaluates Table RLS policies (e.g. students_select)
       │ 2. core.current_tenant_id() reads set_config('request.jwt.claims')
       │ 3. Evaluates scope functions: iam.perm_all(), core.programs_in_scope()
       │ 4. Fails closed (returns 0 rows or denies write) if tenant mismatched
       ▼
[Shared Services & Persistence Commit]
       │ 1. Atomically writes domain audit event to audit.events
       │ 2. Atomically enqueues outbox event in sys.outbox_events
       │ 3. conn.commit();
       │ 4. Publishes IntelligenceContextEvent via ControlledIntelligenceAdapter
```

### Critical Flow Gaps:
1. **Next.js Direct Query Path:**
   `frontend/src/lib/db.ts` uses `pg.Pool` authenticated directly as `postgres.azulbaxszsqxymqmlwfe`. Because `postgres` is superuser and `FORCE ROW LEVEL SECURITY` is disabled, the PostgreSQL Kernel Enforcement step is completely bypassed.
2. **Missing Student Java Gateway Endpoint:**
   `GatewayConfig.java` has no route mapped for `/v1/students` or `/api/v1/students`.
3. **Empty Java Student Service:**
   `services/student-service` is an empty skeleton. The entire Java domain service and repository layers are unwritten.

---

## E. Existing Test Evidence

| Test Suite / Script | Location | Evidence Tier | Scenarios Covered | Observed Result | Tenant-Isolation Assurance Level |
|---|---|---|---|---|---|
| `scripts/verify_phase5_college_setup.mjs` | `scripts/` | Live Node Script (Staging Pooler) | 1. Student enrollment with initial batch assignment (`POST /api/college/students`).<br>2. Duplicate enrollment number conflict (HTTP 409).<br>3. Duplicate email conflict (HTTP 409).<br>4. Batch reassignment (`POST /api/college/students/assign-batch`).<br>5. Audited soft-delete (`DELETE /api/college/students` -> `WITHDRAWN`).<br>6. Cross-tenant isolation (College A vs College B). | **PASSED** (all assertions green) | **APPLICATION TIER ONLY**. Connects via superuser `postgres`; does NOT test PostgreSQL RLS kernel policies. |
| `scripts/verify_phase9_security_browser.mjs` | `scripts/` | Live Browser E2E (Playwright) | 1. Access denial for unauthenticated requests.<br>2. Role routing for College Admin vs Faculty vs Student.<br>3. Platform Admin forbidden from College Admin views (HTTP 403). | **PASSED** | **INGRESS & MIDDLEWARE TIER ONLY**. Proves edge redirect; does not test DB isolation. |
| `services/course-management-service/src/test/.../StagingPrerequisitesExecutionTest.java` | `services/` | Live JDBC Integration (Dev Ref) | Read-only count verification of seeded students (`WHERE enrollment_no LIKE '2024-CSE-%'`, `WHERE tenant_id = ?`). | **PASSED** (Executed in previous wave checkpoints) | **READ-ONLY SMOKE CHECK**. Verifies student rows exist on dev DB. |
| `services/college-admin-service/src/test/.../DepartmentProgramSharedServicesTest.java` | `services/` | Mock JDBC Connection | Wave 1 audit & outbox pattern on college entities; cross-tenant rejection fail-closed. | **11 / 11 PASS** | **JAVA REPOSITORY ARCHITECTURE ONLY**. Proves pattern works on departments/programs, not yet implemented on students. |
| `services/college-admin-service/src/test/.../CollegeAdminTenantIsolationRegressionTest.java` | `services/` | In-Memory Doubles | Multi-tenant isolation, anti-spoofing header rejection, secret masking. | **15 / 15 PASS** | **DOMAIN LAYER MOCK ONLY**. Unit test proof; does not connect to PostgreSQL. |
| **Java Student Domain Tests** | N/A | None | Any student lifecycle, student roster, or student batch isolation in Java. | **ZERO TESTS EXIST** | **UNVERIFIED**. Code is entirely unwritten. |

---

## F. Gap Register

| ID | Category | Severity | Finding & Impact | Recommended Next Step |
|---|---|---|---|---|
| **GAP-SEC-G1** | Security / Gate G1 | **CRITICAL** | **G1 Gate Open: No Dedicated Application Role**: All database connections (Java HikariCP and Next.js `lib/db.ts`) connect as superuser `postgres.azulbaxszsqxymqmlwfe`. Superusers bypass RLS by default. RLS kernel enforcement is not active for database connections. | Create a dedicated unprivileged PostgreSQL login role (`campx_app_server` or `campx_student_service`) with `NOBYPASSRLS`; grant membership in `authenticated`; enforce `ALTER TABLE ... FORCE ROW LEVEL SECURITY` on all student tables. |
| **GAP-RLS-FORCE** | Database / RLS | **CRITICAL** | **FORCE RLS Disabled on Student Tables**: `FORCE ROW LEVEL SECURITY` is disabled across `stu.students`, `core.persons`, and `stu.guardianships`. Any connection using table owner or superuser ignores RLS. | Include `ALTER TABLE stu.students FORCE ROW LEVEL SECURITY;` and `ALTER TABLE core.persons FORCE ROW LEVEL SECURITY;` in a reviewed and approved migration script. |
| **GAP-STU-SRV** | Architecture / Java | **HIGH** | **Student Java Service is Empty Skeleton**: `services/student-service` contains only `.gitkeep`. It is not registered in root `pom.xml`, has no HTTP server, no domain service, and no repositories. | Either implement the Student Module inside `college-admin-service` (which already owns college operational entities) OR establish `student-service` as an approved module in `pom.xml` with port assignment (e.g. port 8093). |
| **GAP-GW-ROUTE** | Ingress / Gateway | **HIGH** | **API Gateway Lacks Student Routes**: `api-gateway/src/main/java/com/campx/gateway/config/GatewayConfig.java` has no route entries for `/v1/students` or `/api/v1/students`. | Add canonical routing in `GatewayConfig` dispatching `/v1/students` to the designated operational backend service with HMAC signing. |
| **GAP-FE-BYPASS** | Frontend / RLS | **HIGH** | **Direct Next.js Frontend Direct SQL Access**: Frontend routes (`/api/college/students`) directly execute SQL via `lib/db.ts` without passing through API Gateway or Java services. | Migrate frontend student consumers to call the versioned API Gateway `/v1/students` endpoints, retiring raw SQL access in alignment with Wave 10 requirements. |
| **GAP-PII-ENCR** | Data Protection / Compliance | **MEDIUM** | **Student PII Unencrypted in Persons Table**: Full name, email, phone, date of birth in `core.persons` are stored in plaintext without DPDP/GDPR field-level encryption. | Ensure sensitive statutory IDs remain strictly in `core.person_identifiers` (which has `value_ciphertext` bytea). Consider field encryption for student contact fields. |
| **GAP-ADMISSIONS** | Domain Scope | **INFO** | **Absence of Admissions Pipeline Schema**: No tables exist for applicant workflows or admission forms. System design assumes students are enrolled directly with an `enrollment_no` and optional `admission_no`. | Formalize that Wave 4 scope is strictly **Student Master & Roster Foundation**, not a full multi-stage admissions CRM. |

---

## G. Proposed Student Database Foundation

To establish a production-grade, tenant-isolated foundation for Wave 4 without duplicating entities or destabilizing existing schemas, the following minimal additions and reuse boundaries are proposed:

### 1. Reuse of Existing Schema (Zero Schema Changes Needed for Basic Roster):
- **Canonical Student Profiles:** Directly reuse `stu.students` without altering column definitions.
- **Human Identity:** Directly reuse `core.persons` for names, demographic details, and address linkages.
- **Guardian Relationships:** Directly reuse `stu.guardianships` for parent/guardian links.
- **Cohort Membership:** Directly reuse `acd.batch_students` for section/batch grouping and `roll_no` assignment.
- **Audit & Outbox:** Directly reuse `audit.events`, `audit.change_log`, and `sys.outbox_events`.

### 2. Required Security Hardening (Approval-Gated):
Before enabling Java shared-DB persistence:
1. **Dedicated Application Role:** Provision `campx_app_server` with `NOBYPASSRLS` and `LOGIN` privileges.
2. **Forced RLS:** Execute `ALTER TABLE stu.students FORCE ROW LEVEL SECURITY;`, `ALTER TABLE core.persons FORCE ROW LEVEL SECURITY;`, `ALTER TABLE stu.guardianships FORCE ROW LEVEL SECURITY;`, `ALTER TABLE acd.batch_students FORCE ROW LEVEL SECURITY;`.
3. **Session Role Execution:** Enforce that all application connection pools set connection-level or transaction-level role to `authenticated` with bound `request.jwt.claims`.

### 3. Proposed Java Backend Ownership:
- **Service Hosting Option A (Recommended):** Host Student Domain inside `college-admin-service` (port 8082).
  * *Rationale:* `college-admin-service` already owns College, Department, and Program domains, and hosts the existing `UserSecurityContext`, `TransactionalOutboxWriter`, and `ControlledIntelligenceAdapter`.
- **Service Hosting Option B:** Activate `services/student-service` (port 8093) as a standalone microservice.
  * *Rationale:* Maintains strict single-responsibility microservice boundaries, but requires adding module boilerplate, port management, and duplicating shared security verifiers.

---

## H. Approval Checklist

The following actions require separate design decisions and explicit owner approval before execution:

- [ ] **DEC-W4-01: Student Service Architecture Boundary:** Decide whether to host the Student domain inside `college-admin-service` (port 8082) or activate `services/student-service` as an independent service (port 8093).
- [ ] **DEC-W4-02: G1 Least-Privilege Application Role Provisioning:** Review and approve SQL migration script provisioning `campx_app_server` role without `BYPASSRLS` on development ref `azulbaxszsqxymqmlwfe`.
- [ ] **DEC-W4-03: FORCE RLS Activation:** Review and approve script applying `FORCE ROW LEVEL SECURITY` on `stu.students`, `core.persons`, `stu.guardianships`, and `acd.batch_students`.
- [ ] **DEC-W4-04: Gateway Route Registration:** Approve route mapping `/v1/students` in `api-gateway`.
- [ ] **DEC-W4-05: Wave 4 Student Canonical Model:** Approve student lifecycle states (`ACTIVE`, `ALUMNI`, `SUSPENDED`, `WITHDRAWN`, `DETAINED`, `GRADUATED`) and data payload contracts before Java implementation.

---

## I. Readiness Decision

**Decision:** **NOT READY; CRITICAL PREREQUISITES REMAIN.**

### Exact Rationale:
1. **G1 Gate is Open:** Connecting to the database via superuser `postgres` without forced RLS leaves tenant isolation unverified by the database kernel. Wave rules strictly forbid claiming tenant isolation while G1 is open.
2. **Missing Architectural Target:** It must be decided whether the Student Java domain will reside in `college-admin-service` or a new standalone `student-service`.
3. **Zero Existing Java Code:** No Java backend code currently exists for the Student module; starting implementation requires approval of the canonical student model proposal.

**Recommended Immediate Next Step:** Submit the Wave 4 Student Domain Model & Architecture Proposal for owner review and decision. Do not implement Java persistence or execute database writes until G1 prerequisites are approved and satisfied.
