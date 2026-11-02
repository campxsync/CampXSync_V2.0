# STM-02 Phase B — B0 Read-Only Checks Report

**Target Database Scope:** Development project `azulbaxszsqxymqmlwfe` (`CampXSync-Architecture-Dev`)  
**Execution Timestamp:** 2026-11-02T13:06:00Z  
**Repository Branch:** `feature/java-backend-integration`  

---

## 1. Source Code Scan for Forbidden Mutators (`src/main`)

A case-insensitive scanner analyzed all 90 Java source files under `services/student-services/admissions-service/src/main/java`:
- Search terms: `"delete from"`, `"delete  from"` (across line breaks), `".delete("`, `"truncate"`, and any `UPDATE` or `DELETE` touching `application_status_history`.

### Findings:
- **`DELETE FROM` Hits:** **1 HIT**
  - **File:** `services/student-services/admissions-service/src/main/java/com/campx/admissions/repository/PostgresApplicationDocumentRepository.java`
  - **Line:** 116
  - **Snippet:** `String sql = "DELETE FROM stu.application_documents WHERE tenant_id = ? AND id = ?";`
- **`.delete(` Hits:** 0
- **`TRUNCATE` Hits:** 0
- **`application_status_history` UPDATE/DELETE Hits:** 0 (Table is treated as strictly append-only in code; only `INSERT` and `SELECT` statements exist).

---

## 2. PostgreSQL Role Catalog & Exposed API Schemas

Query against `pg_roles` on `azulbaxszsqxymqmlwfe`:
```sql
SELECT rolname, rolsuper, rolbypassrls, rolinherit, rolcanlogin, rolconfig
FROM pg_roles 
WHERE rolname IN ('campx_app_server', 'authenticated', 'service_role', 'authenticator');
```

| Role Name | Superuser (`rolsuper`) | Bypass RLS (`rolbypassrls`) | Inherit (`rolinherit`) | Can Login (`rolcanlogin`) | Role Configuration (`rolconfig`) |
|---|---|---|---|---|---|
| `campx_app_server` | `false` | `false` | `true` | `true` | `["search_path=core, stu, acd, iam, doc, audit, sys, public", "statement_timeout=15s", "idle_in_transaction_session_timeout=30s"]` |
| `authenticated` | `false` | `false` | `true` | `false` | `["statement_timeout=8s"]` |
| `service_role` | `false` | `true` | `true` | `false` | `null` |
| `authenticator` | `false` | `false` | `false` | `true` | `["session_preload_libraries=supautils, safeupdate", "statement_timeout=8s", "lock_timeout=8s"]` |

### `pg_default_acl` Inspection:
- Default ACL for schema `stu` owned by `postgres`:
  `defaclacl: {authenticated=arwd/postgres}`
  *(Note: This explains why table privileges in Phase A showed DELETE (`d`) on newly created admissions tables).*

### Exposed API Schemas (`authenticator` / PostgREST):
- `SELECT rolconfig FROM pg_roles WHERE rolname = 'authenticator'` returns `["session_preload_libraries=supautils, safeupdate", "statement_timeout=8s", "lock_timeout=8s"]`.
- `pgrst.db_schemas` is NOT set in `authenticator.rolconfig` or database settings (defaults strictly to `public`).
- Direct catalog check: `pg_catalog.has_schema_privilege('anon', 'stu', 'USAGE') = false`.
- **Conclusion:** Schema `stu` is **NOT exposed** to PostgREST/public data APIs.

---

## 3. Service Database Connection Role & Claims Setting

### Connection Role:
- Configured in `DatabaseConfig.java` (lines 25–36):
  Defaults to unprivileged application role: `campx_app_server` (or `campx_app_server.azulbaxszsqxymqmlwfe` when connecting through Supabase Transaction Pooler).

### Transaction Claims Binding:
- Implemented in `UserSecurityContext.java` (lines 128–152):
  ```java
  public void applyPostgresRoleContext(Connection connection) throws SQLException {
      try (Statement stmt = connection.createStatement()) {
          stmt.execute("SET LOCAL ROLE authenticated");
      }
      // ... claimsJson build with sub, tenant_id, user_role, college_id ...
      try (PreparedStatement pstmt = connection.prepareStatement("SELECT set_config('request.jwt.claims', ?, true)")) {
          pstmt.setString(1, claimsJson.toString());
          pstmt.execute();
      }
  }
  ```
- **Confirmation:** `set_config` is invoked with `is_local = true` (parameter 3 is literal `true`), ensuring claims and role remain strictly scoped to the active local transaction.

---

## 4. Repository Wiring & Silent Fallback Assessment

### Wiring in `AdmissionsApplication.java` (lines 39–63):
- **When Database URL is Configured (`hasDb == true`):**
  Wires all 9 real PostgreSQL JDBC repositories:
  - `PostgresApplicationRepository`
  - `PostgresAdmissionIntakeRepository`
  - `PostgresEligibilityRuleRepository`
  - `PostgresApplicationDocumentRepository`
  - `PostgresApplicationReviewRepository`
  - `PostgresAdmissionDecisionRepository`
  - `PostgresAdmissionOfferRepository`
  - `PostgresAdmissionHandoverRepository`
  - `PostgresStatusHistoryRepository`
- **When Database URL is Missing (`hasDb == false`):**
  Logs `logger.warn("CAMPX_DB_URL not configured; falling back to in-memory repositories for local/mock execution.");` and instantiates `InMemory*` repositories.
- **Silent Fallback Assessment:**
  **DEFECT IDENTIFIED:** Unlike ACD-04 and STM-01, `AdmissionsApplication` does **not** check `isDeployedEnvironment()`. In an environment where the database URL is missing or unset, the service can silently start in-memory without failing fast. Logged as `DEF-STM02-02`.

---

## 5. Column Recount & Phase A Reconciliation

Recounted from `information_schema.columns` for schema `stu`:
1. `admission_intakes`: **23 columns**
2. `admission_eligibility_rules`: **16 columns**
3. `applications`: **33 columns**
4. `application_status_history`: **11 columns**
5. `application_documents`: **18 columns**
6. `application_reviews`: **14 columns**
7. `admission_decisions`: **10 columns**
8. `admission_offers`: **19 columns**
9. `admission_handovers`: **13 columns**

**Total Physical Column Count:** **157 columns**  
**Reconciliation with Phase A "144":**  
The Phase A report narrative stated 144 columns. A detailed recount shows:
`23 + 16 + 33 + 11 + 18 + 14 + 10 + 19 = 144` (the exact sum of the first 8 tables). The 9th table, `admission_handovers` (13 columns), was omitted from the narrative sum `(144 + 13 = 157)`. All 157 physical columns in `information_schema.columns` are verified and match the DDL migration file 100%.

---

## 6. Migration File Integrity

- **File:** `database/migrations/20261101000100_create_stu_admissions_tables.sql`
- **SHA-256 Hash:** `D92422DD99D0CD0A662D11DD94612C2560A299C90C159EA2DD2E81C05723C4E5`

---

## Gate Assessment

GATE = FAIL

*(Reason: Step 1 identified a physical SQL `DELETE FROM stu.application_documents` statement in `PostgresApplicationDocumentRepository.java:116`. Per Phase B instructions, GATE = PASS requires zero DELETE/TRUNCATE in `src/main` and zero UPDATE/DELETE on `application_status_history`).*

