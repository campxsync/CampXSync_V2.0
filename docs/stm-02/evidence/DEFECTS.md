# STM-02 Verification Defects Log

This document records all defects identified during STM-02 Phase B verification.
Per verification instructions, defects are recorded with: What, Where, How to Reproduce, and Suggested Fix.
No source fixes are applied during this phase.

| Defect ID | Title | Severity | Location | Status |
|---|---|---|---|---|
| DEF-STM02-01 | Hard SQL DELETE statement in PostgresApplicationDocumentRepository | Medium | `services/student-services/admissions-service/src/main/java/com/campx/admissions/repository/PostgresApplicationDocumentRepository.java:116` | LOGGED (NO FIX APPLIED) |
| DEF-STM02-02 | Silent fallback to InMemory repositories on missing DB URL in AdmissionsApplication | High | `services/student-services/admissions-service/src/main/java/com/campx/admissions/AdmissionsApplication.java:53-63` | LOGGED (NO FIX APPLIED) |
| DEF-STM02-03 | Over-permissive default ACL grants DELETE on schema stu to authenticated | Medium | PostgreSQL Catalog `pg_default_acl` on schema `stu` (`defaclacl: {authenticated=arwd/postgres}`) | LOGGED (NO FIX APPLIED) |
| DEF-STM02-04 | Role postgres lacks set_option on campx_app_server preventing SET ROLE | Medium | PostgreSQL Catalog `pg_auth_members` (`member: postgres, role: campx_app_server, set_option: false`) | LOGGED (NO FIX APPLIED) |

---

### Defect Details

#### DEF-STM02-01: Hard SQL DELETE in `PostgresApplicationDocumentRepository`
- **What:** Method `delete(UUID tenantId, UUID id)` executes a physical SQL DELETE statement:
  ```java
  String sql = "DELETE FROM stu.application_documents WHERE tenant_id = ? AND id = ?";
  ```
  This triggers Gate failure under B0 step 1 (prohibiting DELETE in `src/main`).
- **Where:** `services/student-services/admissions-service/src/main/java/com/campx/admissions/repository/PostgresApplicationDocumentRepository.java`, line 116.
- **How to Reproduce:** Case-insensitive search for `"delete from"` across `services/student-services/admissions-service/src/main`.
- **Suggested Fix:** Replace physical deletion with soft delete (`UPDATE stu.application_documents SET deleted_at = clock_timestamp() WHERE ...`) or remove physical `delete()` method in accordance with enterprise append-only/retention policies.

---

#### DEF-STM02-02: Silent Fallback to InMemory Repositories
- **What:** In `AdmissionsApplication.java`, if `CAMPX_DB_URL` / `SUPABASE_JDBC_URL` is not supplied, the application logs a warning and silently instantiates `InMemory*` repositories:
  ```java
  logger.warn("CAMPX_DB_URL not configured; falling back to in-memory repositories for local/mock execution.");
  appRepo = new InMemoryApplicationRepository();
  // ...
  ```
  It lacks the fail-closed `isDeployedEnvironment()` check mandated for ACD-04 and STM-01.
- **Where:** `services/student-services/admissions-service/src/main/java/com/campx/admissions/AdmissionsApplication.java`, lines 52–63.
- **How to Reproduce:** Launch `AdmissionsApplication` without setting `CAMPX_DB_URL` or `SUPABASE_JDBC_URL`. The service starts on port 8094 using ephemeral in-memory maps without failing.
- **Suggested Fix:** Add runtime mode detection (`isDeployedEnvironment()`), failing fast with `IllegalStateException("Authoritative PostgreSQL persistence required in deployed environment")` matching ACD-04 / STM-01 standards.

---

#### DEF-STM02-03: Over-Permissive Default ACL on Schema `stu`
- **What:** PostgreSQL `pg_default_acl` for schema `stu` contains `{authenticated=arwd/postgres}`, which grants `DELETE` privilege (`d`) automatically to `authenticated` on all tables in schema `stu`. Migration `20261101000100_create_stu_admissions_tables.sql` only intended `GRANT SELECT, INSERT, UPDATE`.
- **Where:** Database catalog `pg_default_acl` entry for `nsp: stu`.
- **How to Reproduce:** Run `SELECT * FROM pg_default_acl WHERE defaclnamespace = 'stu'::regnamespace;`.
- **Suggested Fix:** Alter default privileges on schema `stu` (`ALTER DEFAULT PRIVILEGES IN SCHEMA stu REVOKE DELETE ON TABLES FROM authenticated;`) and revoke existing table-level `DELETE` privileges.

---

#### DEF-STM02-04: Role `postgres` Lacks `set_option` on `campx_app_server`
- **What:** PostgreSQL 16+ requires `pg_auth_members.set_option = true` for a role to execute `SET ROLE <target_role>`. In development database `azulbaxszsqxymqmlwfe`, `postgres` has `admin_option=true`, `inherit_option=false`, `set_option=false` on `campx_app_server`. As a result, attempts by `postgres` connections (such as Supabase MCP or SQL clients) to execute `SET LOCAL ROLE campx_app_server` fail closed with `ERROR: 42501: permission denied to set role "campx_app_server"`.
- **Where:** PostgreSQL Catalog `pg_auth_members` entry for member `postgres` and role `campx_app_server`.
- **How to Reproduce:** Run `BEGIN; SET LOCAL ROLE campx_app_server; ROLLBACK;` as user `postgres`.
- **Suggested Fix:** Execute `GRANT campx_app_server TO postgres WITH SET TRUE;` once approved by platform owner.

