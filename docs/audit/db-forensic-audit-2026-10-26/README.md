# CampXSync — Read-Only Supabase Database Forensic Audit
## Executive Summary & Baseline Investigation

**Audit Execution Date:** 2026-10-26  
**Auditor:** Antigravity Agentic AI (Read-Only Forensic Audit Mode)  
**Target Repository:** `D:\CampXSync\CampXSync_V2.0`  
**Governing Branch:** `feature/java-backend-integration` (Commit: `87b85c1bae70c67cd52e1538fe7dba26f7733359`)  
**Target Database Target:** `CampXSync-Architecture-Dev` (Project Reference: `azulbaxszsqxymqmlwfe`)  
**Database Engine:** PostgreSQL 17.11 on x86_64-pc-linux-gnu, compiled by gcc (GCC) 15.2.0, 64-bit  
**Connection User:** `supabase_read_only_user` (Strictly read-only database catalog connection)  

---

## 1. Executive Summary

This forensic audit was commissioned to establish the empirical evidence base required to design a new, reliable, and authoritative migration sequence for the CampXSync platform.

Over recent development waves (Waves 0 through 10), CampXSync has evolved from an initial set of 13 external bootstrap scripts into a distributed multi-service architecture spanning 8 Java domain microservices, a versioned API Gateway reverse proxy, and a Next.js 15 web application. Significant database modifications were introduced during this period—including least-privilege role provisioning (`campx_app_server`), `FORCE ROW LEVEL SECURITY` hardening, transactional outbox routing (`sys.outbox_events`), append-only audit enforcement, asymmetric curriculum RLS, and GiST exclusion constraints for timetable scheduling.

However, these changes were applied through a mixture of direct psql scripts, gated wave proposals, and automated test runners without an overarching database migration management engine (such as Flyway, Liquibase, or Supabase CLI migrations). Consequently, the repository's migration files diverge materially from the live development catalog, and no migration history tracking table exists on the database.

This report establishes the ground-truth state of the development database, reconstructs the history of applied changes, identifies discrepancies between repository scripts and live schema, and recommends an authoritative migration strategy for the Chief Architect.

---

## 2. Mandatory Safety & Non-Mutation Verification

In strict accordance with the safety rules defined in the audit specification:

1. **Target Verification:** The target database was positively verified as `CampXSync-Architecture-Dev` on development reference `azulbaxszsqxymqmlwfe`. Verification was established via:
   - Live query of `core.tenants` returning `a0000000-0000-0000-0000-000000000001` (`DEV_TEST_TENANT` — *"CampX Architecture Dev Test Institute"*).
   - Live query of `core.colleges` returning `a0000000-0000-0000-0000-000000000002` (`DEV_TEST_COLLEGE` — *"CampX Dev College of Engineering"*).
   - Presence of the dedicated unprivileged application role `campx_app_server` (`CONNECTION LIMIT 25`, `NOBYPASSRLS`).
2. **Prohibited Targets:** Zero connections, network calls, or queries were made to production ref `ekspahbsbwdpraaqfqui` or legacy staging ref `kpwdvvcschankgggitnj`.
3. **Zero Database Mutations:** Zero DDL (`CREATE`, `ALTER`, `DROP`, `TRUNCATE`), zero DML (`INSERT`, `UPDATE`, `DELETE`, `UPSERT`), zero seed executions, and zero migration applications were performed. The connection role used for catalog inspection was `supabase_read_only_user`.
4. **Zero Repository State Modifications:**
   - Active branch `feature/java-backend-integration` was preserved.
   - Zero branch switches, commits, pushes, stashes, or resets were performed without explicit user authorization. (The pre-audit checkout from `feature/landing-page-redesign` was explicitly authorized and committed by user direction).
   - Zero application source code, configuration files, or environment files were modified.

---

## 3. High-Level Database Catalog Summary

A complete catalog audit of the live development database revealed the following reconciled object counts across 20 application schemas:

| Object Category | Count | Verification Source |
| :--- | :--- | :--- |
| **Application Schemas** | **20** | `pg_namespace` (`acd`, `audit`, `bud`, `cam`, `cfg`, `core`, `doc`, `exm`, `fin`, `hrm`, `iam`, `lms`, `obe`, `pay`, `plat`, `rpt`, `stu`, `sys`, `tax`, `wf`) |
| **Application Tables** | **458** | `pg_tables` (excludes `auth`, `storage`, `realtime`, `graphql`, `pg_catalog`, `information_schema`) |
| **Partitioned Parent Tables** | **6** | `pg_partitioned_table` (`audit.access_events`, `audit.change_log`, `audit.events`, `cam.delivery_events`, `hrm.attendance_punches`, `lms.progress_events`) |
| **Child Partitions** | **30** | `pg_inherits` (5 partitions per parent: 1 `DEFAULT` + 4 monthly partitions `p202610` through `p202701`) |
| **Primary Keys** | **458** | `pg_constraint` (`contype = 'p'`) — exactly 1 PK per application table (all UUID-based) |
| **Foreign Keys** | **1,239** | `pg_constraint` (`contype = 'f'`) |
| **Unique Constraints** | **403** | `pg_constraint` (`contype = 'u'`) |
| **Check Constraints** | **585** | `pg_constraint` (`contype = 'c'`) |
| **Exclusion Constraints (GiST)** | **3** | `pg_constraint` (`contype = 'x'`) on `acd.timetable_entries` (`ex_tte_employee`, `ex_tte_section`, `ex_tte_room`) |
| **Total Database Indexes** | **2,521** | `pg_index` (including 1,618 unique, 231 partial, and 3 expression indexes) |
| **Database Views** | **14** | `information_schema.views` (0 materialized views) |
| **Custom Enums / Domains** | **0** | `pg_type` (all domain statuses use `text` with `CHECK` constraints) |
| **Database Triggers** | **799** | `pg_trigger` (including 352 `touch_row`, 335 `capture` audit CDC, 31 `forbid_mutation`, 30 `guard_transition`) |
| **Row-Level Security Policies** | **1,545** | `pg_policies` across all 20 application schemas |
| **Tables with RLS Enabled** | **458 of 458** | 100% of application tables have `relrowsecurity = true` |
| **Tables with FORCE RLS** | **4** | `acd.batch_students`, `core.persons`, `stu.guardianships`, `stu.students` (Gate G1 Tier 1 Core tables) |
| **Tables with 0 Policies (Fail-Closed)** | **40** | 30 child partitions + 10 `sys` tables (`sys.dead_letter_events`, `sys.inbox_events`, `sys.jobs`, etc.) |
| **Installed Extensions** | **7** | `uuid-ossp`, `pgcrypto`, `btree_gist`, `pg_trgm`, `pg_stat_statements`, `plpgsql`, `supabase_vault` |
| **Migration History Table** | **0 (ABSENT)**| `supabase_migrations.schema_migrations` does NOT exist in the database |

---

## 4. Key Forensic Findings

### Finding 1: Absence of Migration History & Version Tracking
The live development database does not contain a `supabase_migrations.schema_migrations` table or any other migration tracker (such as Flyway's `flyway_schema_history` or Liquibase's `databasechangelog`). While 6 incremental migration files exist in `database/migrations/`, there is no transactional record of which files have been executed, when, or with what checksum.

### Finding 2: Unapplied RLS Bug Fix on `acd.courses`
The migration `database/migrations/V20261011_01__fix_acd01_courses_rls_policies.sql` was prepared to fix a critical permission code mismatch where `acd.courses` RLS policies incorrectly check `acd03.*` (Subject Management) instead of `acd01.*` (Course Management). **Catalog inspection reveals that this migration was NEVER applied to the development database.** The live policies on `acd.courses` still check `'acd03.view'`, `'acd03.write'`, and `'acd03.manage'`.

### Finding 3: Applied Asymmetric Curriculum RLS Migration (`V20261024_01`)
In contrast to Finding 2, `database/migrations/V20261024_01__asymmetric_course_program_map_rls.sql` **WAS applied to the development database**. The helper function `core.is_active_course_in_tenant(uuid)` exists in the catalog, and the policies on `acd.course_program_map` conform exactly to Option C asymmetric RLS (program-driven curriculum membership with universal course validity checks).

### Finding 4: Wave 4 Gate G1 Least-Privilege Role & Outbox Policies
The unprivileged application login role `campx_app_server` was successfully provisioned on `CampXSync-Architecture-Dev` per Gate G1. In addition, the 4 tenant-scoped RLS policies on `sys.outbox_events` and schema `USAGE ON SCHEMA sys` were applied per Decision Gate DEC-W4-OUTBOX (`04_outbox_least_privilege_grants.sql`).

### Finding 5: 10 `sys` Module Tables Remain Fail-Closed
While `sys.outbox_events` has 4 active RLS policies, the remaining 10 tables in schema `sys` (`sys.data_quality_issues`, `sys.dead_letter_events`, `sys.entity_locks`, `sys.export_jobs`, `sys.id_map`, `sys.idempotency_keys`, `sys.import_jobs`, `sys.import_rows`, `sys.inbox_events`, `sys.jobs`) have `relrowsecurity = true` but **zero RLS policies**. Any non-superuser query (including `campx_app_server` or `authenticated`) attempting to write or read from these tables will fail closed immediately.

### Finding 6: Partition Expiration Horizon (2027-02-01)
The 6 range-partitioned tables (`audit.access_events`, `audit.change_log`, `audit.events`, `cam.delivery_events`, `hrm.attendance_punches`, `lms.progress_events`) only have partitions provisioned through **January 2027 (`p202701`, ending 2027-02-01)**. Furthermore, PostgreSQL extension `pg_cron` is not installed, meaning no automated maintenance job exists. Starting February 1, 2027, all partition writes will fall into the `_default` partition. Additionally, catalog inspection shows that `sys.ensure_partitions()` does not contain the `ALTER TABLE ... ENABLE ROW LEVEL SECURITY` clause proposed in `V20261020_01`.

### Finding 7: Custom Access Token Hook Lacks Role & College Claims
Function `iam.custom_access_token_hook(event jsonb)` only injects `tenant_id` into JWT `app_metadata`. It does not inject `user_role` or `college_id`. This explains why the API Gateway requires PostgreSQL JDBC identity resolution via `JdbcIdentityResolver` to populate full security contexts (`X-User-Role`, `X-College-Id`).

### Finding 8: Staging Seed Script Incompatibility
The repository seed scripts `database/seed-data/staging_seed_college_a_and_b.sql` and migration proposals `V20261014_02` / `V20261014_03` are hardcoded for tenant `11111111-1111-1111-1111-111111111111` (`CAMPX_STAGING`) on the legacy prohibited staging ref `kpwdvvcschankgggitnj`. They cannot be run on `CampXSync-Architecture-Dev` (which uses `a0000000-0000-0000-0000-000000000001` / `DEV_TEST_TENANT`).

---

## 5. Audit Deliverables Index

The detailed forensic evidence, catalogs, and recommendations are organized across the following deliverables in this directory:

1. [`LIVE_SCHEMA_INVENTORY.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/LIVE_SCHEMA_INVENTORY.md) — Comprehensive schema-by-schema inventory of all 458 tables, 2,689 constraints, 2,521 indexes, 14 views, and database functions.
2. [`SCRIPT_AND_MIGRATION_INVENTORY.csv`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/SCRIPT_AND_MIGRATION_INVENTORY.csv) — Register of all bootstrap scripts, incremental migrations, seed scripts, and G1 remediation proposals with evidence-based status classifications.
3. [`SCHEMA_DIFFERENCES.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/SCHEMA_DIFFERENCES.md) — In-depth discrepancy analysis comparing original bootstrap scripts, live development catalog, and application code expectations.
4. [`RLS_AND_PERMISSIONS_AUDIT.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/RLS_AND_PERMISSIONS_AUDIT.md) — Security evaluation of 1,545 RLS policies, role hierarchy, `FORCE RLS` enforcement, security-definer search paths, and tenant isolation invariants.
5. [`SEED_AND_PARTITION_AUDIT.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/SEED_AND_PARTITION_AUDIT.md) — Detailed analysis of reference data, system role seeding, partition bounds, maintenance functions, and partition expiration risks.
6. [`MODULE_DATABASE_DEPENDENCIES.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/MODULE_DATABASE_DEPENDENCIES.md) — Cross-module dependency graph, foreign-key relationships, and topological execution order for repeatable initialization.
7. [`PROPOSED_MIGRATION_STRATEGY.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/PROPOSED_MIGRATION_STRATEGY.md) — Evaluation of migration strategies and recommendation of a Hybrid Two-Track Architecture (Consolidated Baseline + Forward Reconciliation).
8. [`MIGRATION_RECONSTRUCTION_REGISTER.csv`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/MIGRATION_RECONSTRUCTION_REGISTER.csv) — Actionable register of all discrepancies requiring remediation by the Chief Architect.
9. [`EVIDENCE_INDEX.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audit/db-forensic-audit-2026-10-26/EVIDENCE_INDEX.md) — Exhaustive cross-reference of SQL catalog queries, repository paths, commit hashes, and confidence levels.
