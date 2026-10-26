# CampXSync DB Forensic Audit — Addendum 01
## PROVENANCE_REPORT: Live Database to Repository Provenance Audit

**Target Environment:** `CampXSync-Architecture-Dev` (Project Ref: `azulbaxszsqxymqmlwfe`)  
**Auditor Role:** `supabase_read_only_user`  
**Audit Date:** 2026-10-26  
**Scope:** Reverse mapping of all live database objects (`pg_proc`, `pg_constraint`, `pg_trigger`, `pg_policies`, `pg_event_trigger`) against repository SQL scripts (`Documentation/DatabaseDesign/Supabase/Scripts_V3.0/`, `database/migrations/`, `database/seed-data/`, and `docs/reports/wave-4/g1-proposed-sql/`).

---

## 1. Executive Summary & Top Provenance Findings

While the baseline audit tested forward script execution (repo → live), Addendum 01 executes the first reverse provenance analysis (live → repo).

| Live Object Category | Total Live Objects | Found in Repo SQL | Not Found in Repo SQL | Provenance Status |
| :--- | :--- | :--- | :--- | :--- |
| **Domain & Utility Functions** | **207** | **206** | **1** (`sys.enable_rls_on_new_tables`) | **99.5% tracked**. 1 untracked event trigger function discovered. |
| **Event Triggers** | **8** (system + user) | **0 user scripts** | **1** (`enable_rls_on_new_tables`) | **UNTRACKED LIVE ARTIFACT**. |
| **Application Triggers (Base)** | **784** | **784** | **0** | **100% tracked** in `50_triggers.sql`. |
| **RLS Policies** | **1,545** | **1,545** | **0** | **100% tracked** across `60_rls.sql`, `03_minimum_grants`, `04_outbox`, and `V20261024_01`. |
| **Relational Constraints (Base)** | **2,614** | **2,186** explicit | **428** inline PKs | **100% accounted for** (428 PKs generated inline via `id uuid primary key`). |

> [!CAUTION]
> **PROMINENT DISCOVERY: UNTRACKED LIVE EVENT TRIGGER**  
> Direct catalog inspection of `pg_event_trigger` and `pg_proc` revealed a live custom event trigger and function on `CampXSync-Architecture-Dev` with **NO SCRIPT IN THE REPOSITORY**:
> - **Event Trigger:** `enable_rls_on_new_tables` (`ddl_command_end`, tags: `['CREATE TABLE']`, owner: `postgres`)
> - **Function:** `sys.enable_rls_on_new_tables()` (`RETURNS event_trigger`, owner: `postgres`)
> - **Definition:** Loops through newly created tables in `audit`, `cam`, `hrm`, and `lms` and executes `ALTER TABLE <tbl> ENABLE ROW LEVEL SECURITY;`.
> - **Provenance Assessment:** This was applied directly in the live database without an accompanying migration script or commit. It explains why child partitions dynamically created in `audit` had RLS enabled even when `sys.ensure_partitions()` lacked the DDL.

---

## 2. Investigation of Seven Key Live Objects

Each of the seven objects specified for deep forensic tracing was located in repository scripts and git commit history:

### 2.1 The 3 GiST Exclusion Constraints
- **Live Object Names:**
  - `acd.timetable_entries.ex_tte_employee`
  - `acd.timetable_entries.ex_tte_room`
  - `acd.timetable_entries.ex_tte_section`
- **Definition in Repo:**  
  `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/50_triggers.sql`, lines 923–934:
  ```sql
  alter table acd.timetable_entries add constraint ex_tte_employee exclude using gist (
    tenant_id with =, timetable_version_id with =, employee_id with =, day_of_week with =,
    int4range(extract(epoch from start_time)::int, extract(epoch from end_time)::int) with &&)
    where (employee_id is not null and deleted_at is null and entry_type <> 'BREAK');
  ```
- **First Git Commit:** `a92ac72` (*"feat: complete Phase 3 staging gates, role dashboards, attendance workflow, and shortage analytics"*) and verified in Wave 0 baseline `105122b`.
- **Status:** **FOUND** (`Documentation/DatabaseDesign/Supabase/Scripts_V3.0/50_triggers.sql:923-934`).

### 2.2 `core.is_active_course_in_tenant`
- **Live Object Name:** `core.is_active_course_in_tenant(uuid)`
- **Definition in Repo:**  
  `database/migrations/V20261024_01__asymmetric_course_program_map_rls.sql`, lines 27–41:
  ```sql
  CREATE OR REPLACE FUNCTION core.is_active_course_in_tenant(p_course_id uuid)
  RETURNS boolean
  LANGUAGE sql
  STABLE
  SECURITY DEFINER
  SET search_path = pg_catalog, core
  ...
  ```
- **First Git Commit:** `87b85c1` (*"feat(acd-01): apply Option C asymmetric RLS migration and complete live test verification"*).
- **Status:** **FOUND** (`database/migrations/V20261024_01__asymmetric_course_program_map_rls.sql:27-41`).

### 2.3 `campx_app_server` Application Role and Grants
- **Live Object Name:** `campx_app_server` (Role, `rolcanlogin=true`, `rolconnlimit=25`, `rolbypassrls=false`, member of `authenticated`)
- **Definition in Repo:**  
  `docs/reports/wave-4/g1-proposed-sql/02_application_role_proposal.sql`, lines 14–26 (Role creation) and `03_minimum_grants_proposal.sql`, lines 11–76 (Schema USAGE and Table DML grants).
- **First Git Commit:** `d417e71` (*"feat(wave-4): pass Gate G1 database security remediation and update test environment guards"*).
- **Status:** **FOUND** (`docs/reports/wave-4/g1-proposed-sql/02_application_role_proposal.sql:14`).

### 2.4 `trg_forbid_mutation` on Audit Tables
- **Live Object Name:** `trg_immutable` executing `sys.forbid_mutation()` (Note: The prompt refers to this colloquially as `trg_forbid_mutation`; the live and script name is `trg_immutable`).
- **Definition in Repo:**  
  - Function: `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/40_functions.sql`, line 15 (`sys.forbid_mutation()`).
  - Triggers: `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/50_triggers.sql`, lines 147–149:
    ```sql
    create trigger trg_immutable before update or delete on audit.change_log for each row execute function sys.forbid_mutation();
    create trigger trg_immutable before update or delete on audit.events for each row execute function sys.forbid_mutation();
    create trigger trg_immutable before update or delete on audit.access_events for each row execute function sys.forbid_mutation();
    ```
- **First Git Commit:** `2782151` (*"First MVP checkin, front end draft"*).
- **Status:** **FOUND** (`Documentation/DatabaseDesign/Supabase/Scripts_V3.0/50_triggers.sql:147-149`).

### 2.5 `audit.events_insert` Policy
- **Live Object Name:** `audit.events` policy `events_insert` (`FOR INSERT TO authenticated WITH CHECK (tenant_id = core.current_tenant_id())`)
- **Definition in Repo:**  
  `docs/reports/wave-4/g1-proposed-sql/03_minimum_grants_proposal.sql`, line 81:
  ```sql
  create policy events_insert on audit.events for insert to authenticated with check (tenant_id = core.current_tenant_id());
  ```
- **First Git Commit:** `d417e71` (*"feat(wave-4): pass Gate G1 database security remediation and update test environment guards"*).
- **Status:** **FOUND** (`docs/reports/wave-4/g1-proposed-sql/03_minimum_grants_proposal.sql:81`).

### 2.6 The 4 `sys.outbox_events` Policies
- **Live Object Names:**
  - `outbox_events_select`
  - `outbox_events_insert`
  - `outbox_events_update`
  - `outbox_events_delete`
- **Definition in Repo:**  
  `docs/reports/wave-4/g1-proposed-sql/04_outbox_least_privilege_grants.sql`, lines 27–42:
  ```sql
  CREATE POLICY outbox_events_select ON sys.outbox_events FOR SELECT TO authenticated USING (tenant_id = core.current_tenant_id());
  CREATE POLICY outbox_events_insert ON sys.outbox_events FOR INSERT TO authenticated WITH CHECK (tenant_id = core.current_tenant_id());
  CREATE POLICY outbox_events_update ON sys.outbox_events FOR UPDATE TO authenticated USING (tenant_id = core.current_tenant_id()) WITH CHECK (tenant_id = core.current_tenant_id());
  CREATE POLICY outbox_events_delete ON sys.outbox_events FOR DELETE TO authenticated USING (tenant_id = core.current_tenant_id());
  ```
- **First Git Commit:** `5952ea4` (*"feat(wave-4): complete Student Domain & Roster, outbox persistence, and live verification"*).
- **Status:** **FOUND** (`docs/reports/wave-4/g1-proposed-sql/04_outbox_least_privilege_grants.sql:27-42`).

### 2.7 `sys` Schema USAGE Grants
- **Live Object Grants:** `USAGE ON SCHEMA sys TO campx_app_server`
- **Definition in Repo:**  
  `docs/reports/wave-4/g1-proposed-sql/04_outbox_least_privilege_grants.sql`, line 14:
  ```sql
  GRANT USAGE ON SCHEMA sys TO campx_app_server;
  GRANT SELECT, INSERT, UPDATE, DELETE ON TABLE sys.outbox_events TO campx_app_server;
  ```
- **First Git Commit:** `5952ea4` (*"feat(wave-4): complete Student Domain & Roster, outbox persistence, and live verification"*).
- **Status:** **FOUND** (`docs/reports/wave-4/g1-proposed-sql/04_outbox_least_privilege_grants.sql:14-15`).

---

## 3. Evaluation of Exclusion Constraints Claim

In `docs/audit/db-forensic-audit-2026-10-26/README.md` and `MODULE_DATABASE_DEPENDENCIES.md`, it was asserted that the 3 GiST exclusion constraints (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) were *"introduced during Waves 0–10"* and that a missing script `35_exclusion_constraints.sql` represented an undocumented migration.

### Forensic Finding:
1. **The claim is REFUTED by the repository code.**
2. The 3 GiST exclusion constraints are explicitly defined in `Documentation/DatabaseDesign/Supabase/Scripts_V3.0/50_triggers.sql`, lines 923–934.
3. In `docs/reports/wave-5/COMPLETION_REPORT.md` (lines 128), the Wave 5 closeout report explicitly states:  
   *"Database changes: Zero DDL migrations required. Existing PostgreSQL GiST exclusion constraints (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) on `acd.timetable_entries` were verified and successfully used."*
4. **Conclusion:** The exclusion constraints were **not introduced during Waves 0–10**. They existed from Day 1 in `Scripts_V3.0/50_triggers.sql`. The hypothesis in `MODULE_DATABASE_DEPENDENCIES.md` that a script `35_exclusion_constraints.sql` was executed was an ungrounded inference.

---

## 4. Live-to-Repo Scan: The NOT FOUND Set

A comprehensive reverse scan was conducted comparing all 207 functions, 2,614 base constraints, 784 base triggers, and 1,545 policies against all SQL files in the workspace.

### 4.1 The Complete NOT FOUND Object Inventory

| Object Type | Schema | Object Name | Live Definition / Purpose | Provenance Assessment |
| :--- | :--- | :--- | :--- | :--- |
| **Function** | `sys` | `enable_rls_on_new_tables()` | `CREATE OR REPLACE FUNCTION sys.enable_rls_on_new_tables() RETURNS event_trigger LANGUAGE plpgsql AS ...` | **NOT FOUND IN REPO**. Manual dev console execution. |
| **Event Trigger** | `(database)` | `enable_rls_on_new_tables` | `CREATE EVENT TRIGGER enable_rls_on_new_tables ON ddl_command_end WHEN TAG IN ('CREATE TABLE') EXECUTE FUNCTION sys.enable_rls_on_new_tables();` | **NOT FOUND IN REPO**. Manual dev console execution. |
| **Primary Keys** | All 20 schemas | 428 `<table>_pkey` constraints | Implicit PK constraints generated from `id uuid PRIMARY KEY DEFAULT gen_random_uuid()` | **FOUND AS INLINE SYNTAX** in `20_tables.sql` (synthetically named by PostgreSQL). |

### 4.2 Detailed Analysis of the Untracked Event Trigger
- **SQL Definition extracted from catalog:**
  ```sql
  CREATE OR REPLACE FUNCTION sys.enable_rls_on_new_tables()
   RETURNS event_trigger
   LANGUAGE plpgsql
  AS $function$
  declare r record;
  begin
    for r in
      select objid::regclass as tbl
      from pg_event_trigger_ddl_commands()
      where command_tag = 'CREATE TABLE'
        and schema_name in ('audit','cam','hrm','lms')
    loop
      execute format('alter table %s enable row level security', r.tbl);
    end loop;
  end $function$;
  ```
- **Operational Impact:**  
  Whenever any table is created in schemas `audit`, `cam`, `hrm`, or `lms`, this trigger fires and executes `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`. This explains why partitions dynamically attached in development had RLS enabled despite `sys.ensure_partitions` lacking explicit RLS statements.
- **Architectural Risk:**  
  Because this trigger exists *only* on `CampXSync-Architecture-Dev`, deploying the repository scripts onto a new scratch database or staging environment will **fail to replicate this behavior** unless formal migration SQL is authored and committed.
