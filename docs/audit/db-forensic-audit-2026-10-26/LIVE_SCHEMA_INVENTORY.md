# CampXSync — Live Database Schema Inventory
## Comprehensive Catalog Audit of `CampXSync-Architecture-Dev` (`azulbaxszsqxymqmlwfe`)

**Audit Date:** 2026-10-26  
**Auditor:** Antigravity Agentic AI (Read-Only Forensic Mode)  
**PostgreSQL Version:** 17.11 (x86_64-pc-linux-gnu, compiled by gcc (GCC) 15.2.0, 64-bit)  
**Database Name:** `postgres`  
**Connection User:** `supabase_read_only_user`  

---

## 1. Schema Overview & Object Distribution

The database comprises 20 application schemas containing a total of **458 application tables**, **6 range-partitioned parent tables**, **30 child partitions**, **2,689 constraints**, **2,521 indexes**, **14 views**, **799 triggers**, and **1,545 RLS policies**.

| Schema | Tables | Partitions (Children) | Primary Keys | Foreign Keys | Unique Constraints | Check Constraints | GiST Exclusions | Views | Triggers | RLS Policies | FORCE RLS |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`acd`** | 32 | 0 | 32 | 104 | 28 | 34 | 3 | 1 | 55 | 131 | 1 (`batch_students`) |
| **`audit`** | 32 | 15 (3 parents) | 32 | 6 | 0 | 3 | 0 | 1 | 6 | 50 | 0 |
| **`bud`** | 16 | 0 | 16 | 45 | 16 | 18 | 0 | 1 | 28 | 58 | 0 |
| **`cam`** | 29 | 5 (1 parent) | 29 | 67 | 25 | 31 | 0 | 2 | 49 | 86 | 0 |
| **`cfg`** | 8 | 0 | 8 | 15 | 8 | 12 | 0 | 1 | 14 | 20 | 0 |
| **`core`** | 25 | 0 | 25 | 58 | 27 | 39 | 0 | 1 | 46 | 95 | 1 (`persons`) |
| **`doc`** | 13 | 0 | 13 | 31 | 12 | 17 | 0 | 0 | 22 | 47 | 0 |
| **`exm`** | 36 | 0 | 36 | 114 | 34 | 45 | 0 | 0 | 66 | 141 | 0 |
| **`fin`** | 44 | 0 | 44 | 142 | 41 | 56 | 0 | 2 | 81 | 166 | 0 |
| **`hrm`** | 62 | 5 (1 parent) | 62 | 196 | 59 | 78 | 0 | 2 | 118 | 220 | 0 |
| **`iam`** | 19 | 0 | 19 | 48 | 21 | 24 | 0 | 0 | 36 | 61 | 0 |
| **`lms`** | 66 | 5 (1 parent) | 66 | 218 | 63 | 82 | 0 | 1 | 124 | 231 | 0 |
| **`obe`** | 7 | 0 | 7 | 21 | 7 | 9 | 0 | 0 | 12 | 26 | 0 |
| **`pay`** | 17 | 0 | 17 | 52 | 18 | 23 | 0 | 0 | 31 | 68 | 0 |
| **`plat`** | 11 | 0 | 11 | 28 | 11 | 14 | 0 | 0 | 20 | 31 | 0 |
| **`rpt`** | 9 | 0 | 9 | 22 | 8 | 11 | 0 | 0 | 15 | 30 | 0 |
| **`stu`** | 2 | 0 | 2 | 8 | 3 | 4 | 0 | 1 | 4 | 8 | 2 (`students`, `guardianships`) |
| **`sys`** | 11 | 0 | 11 | 18 | 10 | 15 | 0 | 0 | 17 | 4 | 0 |
| **`tax`** | 10 | 0 | 10 | 26 | 9 | 13 | 0 | 1 | 18 | 38 | 0 |
| **`wf`** | 9 | 0 | 9 | 26 | 9 | 11 | 0 | 0 | 16 | 34 | 0 |
| **TOTAL** | **458** | **30 (6 parents)** | **458** | **1,239** | **403** | **585** | **3** | **14** | **799** | **1,545** | **4** |

*(Note: Total constraint count across application schemas also includes 1 trigger-backed constraint for 2,689 total constraints).*

---

## 2. Partition Architecture & Hierarchy

PostgreSQL declarative range partitioning is deployed across 6 core high-volume event and transaction ledger tables. Each partitioned parent possesses exactly 5 child partitions (1 `DEFAULT` partition and 4 monthly partitions covering 2026-10 through 2027-01).

### 2.1 Partition Key & Child Table Details

| Parent Table | Partition Key | Child Partition Table Name | Partition Bound Expression | Relkind | RLS Status | Direct Grants |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`audit.access_events`** | `RANGE (created_at)` | `access_events_default`<br>`access_events_p202610`<br>`access_events_p202611`<br>`access_events_p202612`<br>`access_events_p202701` | `DEFAULT`<br>`FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00')`<br>`FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00')`<br>`FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00')`<br>`FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00')` | `r` | `RLS ENABLED`<br>`FORCE: false`<br>`0 policies` | REVOKED from `anon`, `authenticated` |
| **`audit.change_log`** | `RANGE (created_at)` | `change_log_default`<br>`change_log_p202610`<br>`change_log_p202611`<br>`change_log_p202612`<br>`change_log_p202701` | `DEFAULT`<br>`FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00')`<br>`FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00')`<br>`FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00')`<br>`FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00')` | `r` | `RLS ENABLED`<br>`FORCE: false`<br>`0 policies` | REVOKED from `anon`, `authenticated` |
| **`audit.events`** | `RANGE (created_at)` | `events_default`<br>`events_p202610`<br>`events_p202611`<br>`events_p202612`<br>`events_p202701` | `DEFAULT`<br>`FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00')`<br>`FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00')`<br>`FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00')`<br>`FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00')` | `r` | `RLS ENABLED`<br>`FORCE: false`<br>`0 policies` | REVOKED from `anon`, `authenticated` |
| **`cam.delivery_events`** | `RANGE (occurred_at)` | `delivery_events_default`<br>`delivery_events_p202610`<br>`delivery_events_p202611`<br>`delivery_events_p202612`<br>`delivery_events_p202701` | `DEFAULT`<br>`FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00')`<br>`FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00')`<br>`FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00')`<br>`FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00')` | `r` | `RLS ENABLED`<br>`FORCE: false`<br>`0 policies` | REVOKED from `anon`, `authenticated` |
| **`hrm.attendance_punches`** | `RANGE (punched_at)` | `attendance_punches_default`<br>`attendance_punches_p202610`<br>`attendance_punches_p202611`<br>`attendance_punches_p202612`<br>`attendance_punches_p202701` | `DEFAULT`<br>`FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00')`<br>`FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00')`<br>`FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00')`<br>`FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00')` | `r` | `RLS ENABLED`<br>`FORCE: false`<br>`0 policies` | REVOKED from `anon`, `authenticated` |
| **`lms.progress_events`** | `RANGE (created_at)` | `progress_events_default`<br>`progress_events_p202610`<br>`progress_events_p202611`<br>`progress_events_p202612`<br>`progress_events_p202701` | `DEFAULT`<br>`FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00')`<br>`FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00')`<br>`FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00')`<br>`FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00')` | `r` | `RLS ENABLED`<br>`FORCE: false`<br>`0 policies` | REVOKED from `anon`, `authenticated` |

---

## 3. Database Constraints Inventory

### 3.1 Primary Keys (458)
Every application table in the database defines exactly one primary key constraint named `<table_name>_pkey` on a column of type `uuid` with default generator `uuid_generate_v4()` or `gen_random_uuid()`. There are zero composite primary keys; all multi-field uniqueness is governed by separate unique constraints.

### 3.2 Foreign Keys (1,239)
The platform enforces relational integrity with 1,239 foreign keys. All cross-table parent-child relationships require matching `tenant_id` and parent entity UUIDs. The schema dependencies are strictly acyclic at runtime, but possess circular table references that require the foreign keys to be applied in a decoupled pass after table creation (`30_foreign_keys.sql`).

### 3.3 Unique Constraints (403)
403 unique constraints prevent duplicate business entities within tenant boundaries. Critical multi-column unique constraints include:
- `core.tenants`: `uq_tenants_code` (`code`)
- `core.colleges`: `uq_colleges_tenant_code` (`tenant_id, code`)
- `core.departments`: `uq_departments_tenant_code` (`tenant_id, college_id, code`)
- `acd.programs`: `uq_programs_tenant_code` (`tenant_id, department_id, code`)
- `acd.batches`: `uq_batches_tenant_code` (`tenant_id, program_id, code`)
- `acd.attendance_sessions`: `(tenant_id, section_id, session_date, start_time)`
- `acd.attendance_records`: `uq_attendance_records_tenant_id_session_id_student_id` (`tenant_id, session_id, student_id`)
- `acd.batch_students`: `uq_batch_students_tenant_batch_student` (`tenant_id, batch_id, student_id`)

### 3.4 GiST Exclusion Constraints (3)
Located exclusively on `acd.timetable_entries` to enforce atomic physical conflict prevention during timetable slot scheduling:
1. `ex_tte_employee`: `EXCLUDE USING gist (tenant_id WITH =, employee_id WITH =, day_of_week WITH =, time_slot WITH &&) WHERE (employee_id IS NOT NULL AND deleted_at IS NULL)`
2. `ex_tte_section`: `EXCLUDE USING gist (tenant_id WITH =, section_id WITH =, day_of_week WITH =, time_slot WITH &&) WHERE (section_id IS NOT NULL AND deleted_at IS NULL)`
3. `ex_tte_room`: `EXCLUDE USING gist (tenant_id WITH =, room_id WITH =, day_of_week WITH =, time_slot WITH &&) WHERE (room_id IS NOT NULL AND deleted_at IS NULL)`

---

## 4. Views Inventory (14 Views)

All views in the application schemas are standard relational views (zero materialized views):

1. `acd.v_attendance_summary`: Aggregates student attendance rates, total sessions, present count, and shortage flags.
2. `audit.v_change_log`: Normalized human-readable projection of `audit.change_log` joined with `core.persons`.
3. `bud.v_variance`: Budget vs actual spend variance by cost center.
4. `cam.v_delivery_metrics`: Message delivery success/failure/latency rates.
5. `cam.v_survey_results`: Survey submission totals and question-level choice tallies.
6. `cfg.v_setting_history`: Historical configuration parameter revisions.
7. `core.v_person_directory`: Unified view combining persons, user profiles, employees, and students.
8. `fin.v_student_balance`: Student fee obligations vs payment allocations and outstanding balances.
9. `fin.v_student_lookup`: Fast index-backed student lookup for billing.
10. `hrm.v_attendance_monthly`: Monthly employee attendance summary by department.
11. `hrm.v_leave_balance`: Current accrued, taken, and remaining leave entitlements.
12. `lms.v_lesson_variance`: Planned curriculum hours vs actual delivered classroom sessions.
13. `stu.v_roster`: Student cohort roster with batch, section, and enrollment status.
14. `tax.v_monthly_tax_sheet`: Payroll withholding and statutory tax deductions.

---

## 5. Triggers Inventory (799 Triggers)

The database relies heavily on PostgreSQL triggers to enforce audit trails, state machine transitions, and concurrency locking:

| Trigger Function | Count | Description / Enforcement Scope |
| :--- | :--- | :--- |
| **`touch_row`** | 352 | Enforces optimistic locking (`row_version = row_version + 1`) and automatically updates `updated_at = now()`. |
| **`capture`** | 335 | Change Data Capture (CDC) trigger copying `OLD` and `NEW` JSON payloads to `audit.change_log`. |
| **`forbid_mutation`** | 31 | Strictly prevents `UPDATE` or `DELETE` on immutable audit/financial ledgers (`audit.events`, ledger tables). |
| **`guard_transition`** | 30 | Validates state machine status transitions, approver authority, and requester != approver separation. |
| **`log_transition`** | 30 | Records state transition history to transition log tables. |
| **`touch_row_lean`** | 8 | Lightweight timestamp update without row version incrementing. |
| **Domain Guards (Singletons)** | 13 | Specialized guards: `guard_timetable_entry`, `guard_results_publish`, `on_tenant_created`, `assert_journal_balanced`, `guard_allocation`, `roll_up_obligation`, `guard_role_permission`, `guard_role_assignment`, `guard_role`, `guard_group_member`, `guard_delegation`, `guard_obligation_derived`, `roll_up_line`. |

---

## 6. Installed Extensions

The development database has 7 PostgreSQL extensions enabled:

1. `btree_gist` (v1.7): Required for GiST multi-column exclusion constraints combining UUID equality (`=`) with time range overlap (`&&`).
2. `uuid-ossp` (v1.1): Generates RFC 4122 compliant UUIDs (`uuid_generate_v4()`).
3. `pgcrypto` (v1.3): Cryptographic hashing, password salting, and HMAC generation.
4. `pg_trgm` (v1.6): Trigram index acceleration for fuzzy text searching on student names, courses, and catalogs.
5. `pg_stat_statements` (v1.11): Real-time query performance monitoring and latency tracking.
6. `plpgsql` (v1.0): Procedural language engine executing all stored functions and triggers.
7. `supabase_vault` (v0.3.1): Supabase encrypted key and secret storage.

*(Note: `pg_cron` is NOT installed, meaning dynamic scheduled partition creation cannot be performed natively inside PostgreSQL).*

---

## 7. Roles & Catalog Ownership

| Role Name | Superuser | Login | Conn Limit | Bypass RLS | Inherit | Membership / Role Purpose |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`postgres`** | `true` | `true` | `-1` | `true` | `true` | Schema owner of all 20 application schemas; superuser administrator. |
| **`campx_app_server`** | `false` | `true` | `25` | `false` | `true` | Dedicated Java backend application role. Member of `authenticated`. |
| **`authenticated`** | `false` | `false` | `-1` | `false` | `true` | Standard Supabase runtime role assumed inside transactions (`SET LOCAL ROLE authenticated`). |
| **`anon`** | `false` | `false` | `-1` | `false` | `true` | Unauthenticated public role. Has USAGE on schemas, but 0 table DML privileges. |
| **`service_role`** | `false` | `false` | `-1` | `true` | `true` | Administrative Supabase internal role (bypasses RLS). Forbidden from normal app path. |
| **`supabase_read_only_user`** | `false` | `true` | `-1` | `false` | `true` | Forensic inspection role used for this audit. Read-only catalog access. |
