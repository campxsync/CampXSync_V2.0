# CampXSync DB Forensic Audit — Addendum 01
## SCRATCH_REBUILD_DIFF: Local Scratch Database Rebuild Status

**Audit Baseline Date:** 2026-10-26  
**Addendum Date:** 2026-10-26  
**Task F Status:** **BLOCKED (Docker Runtime Unavailable Locally)**  

---

## 1. Execution Preflight & Safety Evaluation

Task F requires launching a throwaway local PostgreSQL 17 container via Docker to execute the repository scripts (`Scripts_V3.0` 00 → 85 plus known migrations) and produce a byte-for-byte catalog diff against `CampXSync-Architecture-Dev`.

### 1.1 Local Environment Check
Execution of terminal command:
```powershell
docker --version
```
**Result:**  
```
docker : The term 'docker' is not recognized as the name of a cmdlet, function, script file, or operable program.
```
- **Error Code:** 1 (CommandNotFoundException)
- **Status:** Docker daemon / Docker Desktop is not installed or not available in PATH on the host machine.

### 1.2 Safety Rule Enforcement
In accordance with **Safety Rule 0.1** and **Safety Rule 0.6**:
1. *NEVER point a rebuild or DDL execution at any remote database.*
2. *If any step requires a tool or privilege that is unavailable, STOP that step, record it as "BLOCKED: <reason>", and continue with the next step.*

Consequently, Task F was halted immediately. No remote connection was used for execution.

---

## 2. Mitigation & Future Execution Plan

Although a live scratch container diff could not be executed within this session, all required baseline catalog artifacts were successfully generated from `CampXSync-Architecture-Dev` under `addendum-01/catalog-dump/`:
1. `schema_only.sql`: Full DDL dump of the 20 application schemas.
2. `columns.csv`: 6,336 columns across all 458 tables.
3. `indexes.csv`: 2,026 application index definitions.
4. `functions.csv`: 207 function definitions and signatures.
5. `triggers.csv`: 799 trigger definitions.
6. `policies.csv`: 1,545 RLS policy definitions.

### 2.1 Rebuild Recipe for Future Execution
When Docker or a local PostgreSQL 17 instance is available:
```bash
# 1. Start local Postgres 17 container
docker run --name campx-scratch -e POSTGRES_PASSWORD=postgres -p 5433:5432 -d postgres:17

# 2. Apply Supabase shim (roles, auth schema, extensions)
psql -h localhost -p 5433 -U postgres -d postgres -f docs/audit/db-forensic-audit-2026-10-26/addendum-01/catalog-dump/roles.csv

# 3. Apply bootstrap scripts 00 -> 85 in order
for f in Documentation/DatabaseDesign/Supabase/Scripts_V3.0/*.sql; do
  psql -h localhost -p 5433 -U postgres -d postgres -f "$f"
done

# 4. Apply known dev migrations
psql -h localhost -p 5433 -U postgres -d postgres -f database/migrations/V20261024_01__asymmetric_course_program_map_rls.sql
psql -h localhost -p 5433 -U postgres -d postgres -f docs/reports/wave-4/g1-proposed-sql/02_application_role_proposal.sql
psql -h localhost -p 5433 -U postgres -d postgres -f docs/reports/wave-4/g1-proposed-sql/03_minimum_grants_proposal.sql
psql -h localhost -p 5433 -U postgres -d postgres -f docs/reports/wave-4/g1-proposed-sql/04_outbox_least_privilege_grants.sql

# 5. Extract catalog and diff against addendum-01/catalog-dump/
```
