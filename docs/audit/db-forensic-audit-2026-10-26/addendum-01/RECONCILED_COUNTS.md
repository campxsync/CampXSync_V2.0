# CampXSync DB Forensic Audit — Addendum 01
## RECONCILED_COUNTS: Authoritative Schema & Object Distribution

**Target Environment:** `CampXSync-Architecture-Dev` (Project Ref: `azulbaxszsqxymqmlwfe`)  
**Auditor Role:** `supabase_read_only_user`  
**Catalog Source:** PostgreSQL 17.11 System Catalogs (`pg_class`, `pg_constraint`, `pg_trigger`, `pg_indexes`, `pg_policies`, `pg_namespace`)  
**Audit Date:** 2026-10-26  

---

## 1. Executive Summary of Reconciled Counts

A systematic forensic verification of all 20 application schemas was conducted to resolve discrepancies between headline figures and per-schema rows in the original audit (`LIVE_SCHEMA_INVENTORY.md`).

| Object Category | Base Objects (Excluding Partitions) | Partition Children / Inherited | Authoritative Total | Original Headline | Original Per-Schema Sum | Discrepancy Status |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **Tables (All in `pg_tables`)** | **428** (422 ord + 6 parents) | **30** child partitions | **458** | 458 | 458 | **Reconciled**: Headline includes child partitions. |
| **Primary Keys (`contype='p'`)** | **428** | **30** inherited | **458** | 458 | 458 | **Reconciled**: Matches table count exactly. |
| **Foreign Keys (`contype='f'`)** | **1,194** | **45** inherited | **1,239** | 1,239 | 1,245 | **Reconciled**: Original per-schema row sum was miscalculated (+6). |
| **Unique Constraints (`contype='u'`)** | **403** | **0** | **403** | 403 | 409 | **Reconciled**: Original per-schema row sum was miscalculated (+6). |
| **Check Constraints (`contype='c'`)** | **585** | **0** | **585** | 585 | 539 | **Reconciled**: Original per-schema row sum was miscalculated (-46). |
| **GiST Exclusions (`contype='x'`)** | **3** | **0** | **3** | 3 | 3 | **Exact match** (`acd` timetable exclusion constraints). |
| **Trigger Constraints (`contype='t'`)** | **1** | **0** | **1** | 1 | 0 (footnote) | **Exact match** (in `fin.ledger_accounts`). |
| **TOTAL CONSTRAINTS (`pg_constraint`)** | **2,614** | **75** inherited | **2,689** | 2,689 | 2,735 (sum) | **Reconciled**: Sums match catalog count (2,689) exactly. |
| **Triggers (Non-Internal)** | **784** | **15** inherited (`audit`) | **799** | 799 | 778 | **Reconciled**: Original per-schema table omitted 21 triggers (mostly inherited `audit`). |
| **Indexes (App Table Indexes)** | **1,926** | **100** on partition children | **2,026** | 2,521 | Not tabulated | **Reconciled**: Original 2,521 included 495 internal `pg_toast` table indexes. |
| **RLS Policies** | **1,545** (430 S, 392 I, 352 U, 352 D, 19 ALL) | N/A | **1,545** | 1,545 | 1,545 (Cols: 1,545) | **Reconciled**: Original column headers omitted `cmd='ALL'` (19 policies). |
| **Views** | **14** | N/A | **14** | 14 | 14 | **Exact match**. |
| **Sequences** | **0** | N/A | **0** | 0 | 0 | **Exact match** (UUID PK architecture). |

---

## 2. Table Reconciliation: Headline "458 Tables" Clarification

### 2.1 The Definitive Counting Methodology
- **Ordinary Standalone Tables** (`relkind = 'r' AND NOT relispartition`): **422**
- **Partitioned Parent Tables** (`relkind = 'p'`): **6**
  - `audit.access_events`
  - `audit.change_log`
  - `audit.events`
  - `cam.delivery_events`
  - `hrm.attendance_punches`
  - `lms.progress_events`
- **Total Base Application Tables (Excluding Partitions):** **428** (422 + 6)
- **Child Partitions** (`relispartition = true`): **30** (5 partitions for each of the 6 parents: 1 `_default` + 4 monthly partitions `p202610`, `p202611`, `p202612`, `p202701`)
- **Total Registered Tables in `pg_tables`:** **458** (428 + 30)

> [!IMPORTANT]
> **Authoritative Statement on Headline Table Count:**  
> The headline figure of **458 application tables** reported in the baseline audit **INCLUDES the 30 child partitions and the 6 partitioned parent tables**.  
> The true number of distinct application logical tables (excluding child partitions) is **428** (422 standalone ordinary tables and 6 partitioned parents).  
> The claim in `SCRIPT_AND_MIGRATION_INVENTORY.csv` that `20_tables.sql` "creates all 458 tables" is physically impossible without `45_partitions.sql`: `20_tables.sql` creates only the 428 base tables; the 30 child partitions are created by `45_partitions.sql`.

### 2.2 Table Counts Grouped by Schema

| Schema | Ordinary Tables (`relkind='r'`) | Partitioned Parents (`relkind='p'`) | Base Tables (Excl. Partitions) | Child Partitions (`relispartition`) | Total in `pg_tables` |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **`acd`** | 32 | 0 | **32** | 0 | 32 |
| **`audit`** | 14 | 3 | **17** | 15 | 32 |
| **`bud`** | 16 | 0 | **16** | 0 | 16 |
| **`cam`** | 23 | 1 | **24** | 5 | 29 |
| **`cfg`** | 8 | 0 | **8** | 0 | 8 |
| **`core`** | 25 | 0 | **25** | 0 | 25 |
| **`doc`** | 13 | 0 | **13** | 0 | 13 |
| **`exm`** | 36 | 0 | **36** | 0 | 36 |
| **`fin`** | 44 | 0 | **44** | 0 | 44 |
| **`hrm`** | 56 | 1 | **57** | 5 | 62 |
| **`iam`** | 19 | 0 | **19** | 0 | 19 |
| **`lms`** | 60 | 1 | **61** | 5 | 66 |
| **`obe`** | 7 | 0 | **7** | 0 | 7 |
| **`pay`** | 17 | 0 | **17** | 0 | 17 |
| **`plat`** | 11 | 0 | **11** | 0 | 11 |
| **`rpt`** | 9 | 0 | **9** | 0 | 9 |
| **`stu`** | 2 | 0 | **2** | 0 | 2 |
| **`sys`** | 11 | 0 | **11** | 0 | 11 |
| **`tax`** | 10 | 0 | **10** | 0 | 10 |
| **`wf`** | 9 | 0 | **9** | 0 | 9 |
| **TOTAL** | **422** | **6** | **428** | **30** | **458** |

---

## 3. Constraints Reconciliation: Impact of Partition Inheritance

In PostgreSQL, declarative partitioning automatically clones primary keys and foreign keys from parent tables onto each attached child partition. In `pg_constraint`, these inherited constraints are distinct rows with `conparentid <> 0`.

### 3.1 Authoritative Constraint Breakdown by Schema
- **PK(base):** Primary key on base/parent table (`conparentid = 0`)
- **PK(inh):** Primary key inherited on child partition (`conparentid <> 0`)
- **FK(base):** Foreign key on base/parent table (`conparentid = 0`)
- **FK(inh):** Foreign key inherited on child partition (`conparentid <> 0`)
- **UQ:** Unique constraints (none inherited)
- **CK:** Check constraints (none inherited)
- **EX:** GiST exclusion constraints
- **TG:** Trigger-backed constraints

| Schema | PK (Base) | PK (Inh) | PK Total | FK (Base) | FK (Inh) | FK Total | UQ | CK | EX | TG | Base Constraints (No Inh) | Total Catalog Constraints |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`acd`** | 32 | 0 | 32 | 104 | 0 | 104 | 32 | 65 | 3 | 0 | 236 | 236 |
| **`audit`** | 17 | 15 | 32 | 31 | 20 | 51 | 13 | 21 | 0 | 0 | 82 | 117 |
| **`bud`** | 16 | 0 | 16 | 48 | 0 | 48 | 16 | 20 | 0 | 0 | 100 | 100 |
| **`cam`** | 24 | 5 | 29 | 67 | 15 | 82 | 23 | 35 | 0 | 0 | 149 | 169 |
| **`cfg`** | 8 | 0 | 8 | 6 | 0 | 6 | 2 | 8 | 0 | 0 | 24 | 24 |
| **`core`** | 25 | 0 | 25 | 52 | 0 | 52 | 24 | 28 | 0 | 0 | 129 | 129 |
| **`doc`** | 13 | 0 | 13 | 35 | 0 | 35 | 13 | 19 | 0 | 0 | 80 | 80 |
| **`exm`** | 36 | 0 | 36 | 119 | 0 | 119 | 36 | 45 | 0 | 0 | 236 | 236 |
| **`fin`** | 44 | 0 | 44 | 144 | 0 | 144 | 44 | 89 | 0 | 1 | 322 | 322 |
| **`hrm`** | 57 | 5 | 62 | 174 | 5 | 179 | 56 | 76 | 0 | 0 | 363 | 373 |
| **`iam`** | 19 | 0 | 19 | 41 | 0 | 41 | 13 | 10 | 0 | 0 | 83 | 83 |
| **`lms`** | 61 | 5 | 66 | 174 | 5 | 179 | 60 | 62 | 0 | 0 | 357 | 367 |
| **`obe`** | 7 | 0 | 7 | 25 | 0 | 25 | 7 | 19 | 0 | 0 | 58 | 58 |
| **`pay`** | 17 | 0 | 17 | 57 | 0 | 57 | 17 | 33 | 0 | 0 | 124 | 124 |
| **`plat`** | 11 | 0 | 11 | 15 | 0 | 15 | 6 | 17 | 0 | 0 | 49 | 49 |
| **`rpt`** | 9 | 0 | 9 | 19 | 0 | 19 | 9 | 3 | 0 | 0 | 40 | 40 |
| **`stu`** | 2 | 0 | 2 | 8 | 0 | 8 | 2 | 2 | 0 | 0 | 14 | 14 |
| **`sys`** | 11 | 0 | 11 | 20 | 0 | 20 | 11 | 13 | 0 | 0 | 55 | 55 |
| **`tax`** | 10 | 0 | 10 | 24 | 0 | 24 | 10 | 11 | 0 | 0 | 55 | 55 |
| **`wf`** | 9 | 0 | 9 | 31 | 0 | 31 | 9 | 9 | 0 | 0 | 58 | 58 |
| **TOTAL** | **428** | **30** | **458** | **1,194** | **45** | **1,239** | **403** | **585** | **3** | **1** | **2,614** | **2,689** |

### 3.2 Explanation of Original Discrepancies
1. **Foreign Keys:** The headline reported **1,239**, but summing the per-schema rows in the original report produced **1,245**. The true catalog count of FK constraints is **1,239** (1,194 base FKs + 45 inherited FKs across child partitions). The original report mis-summed the rows.
2. **Unique Constraints:** The headline reported **403**, but the per-schema rows summed to **409**. The true catalog count is **403**.
3. **Check Constraints:** The headline reported **585**, but the per-schema rows summed to **539**. The true catalog count is **585**.
4. **Inheritance Inflation:** Exactly **75 constraints** in `pg_constraint` are synthetic child partition duplicates (30 PKs and 45 FKs). The base database schema defines **2,614 constraints**.

---

## 4. Triggers Reconciliation: Base vs Inherited Triggers

### 4.1 Authoritative Trigger Counts Grouped by Schema

| Schema | Base Triggers (`tgparentid = 0`) | Inherited Partition Triggers (`tgparentid <> 0`) | Total Triggers |
| :--- | :--- | :--- | :--- |
| **`acd`** | 66 | 0 | 66 |
| **`audit`** | 14 | 15 | 29 |
| **`bud`** | 32 | 0 | 32 |
| **`cam`** | 43 | 0 | 43 |
| **`cfg`** | 16 | 0 | 16 |
| **`core`** | 48 | 0 | 48 |
| **`doc`** | 20 | 0 | 20 |
| **`exm`** | 75 | 0 | 75 |
| **`fin`** | 92 | 0 | 92 |
| **`hrm`** | 109 | 0 | 109 |
| **`iam`** | 38 | 0 | 38 |
| **`lms`** | 101 | 0 | 101 |
| **`obe`** | 12 | 0 | 12 |
| **`pay`** | 36 | 0 | 36 |
| **`plat`** | 22 | 0 | 22 |
| **`rpt`** | 11 | 0 | 11 |
| **`stu`** | 4 | 0 | 4 |
| **`sys`** | 10 | 0 | 10 |
| **`tax`** | 18 | 0 | 18 |
| **`wf`** | 17 | 0 | 17 |
| **TOTAL** | **784** | **15** | **799** |

### 4.2 Explanation of Original Discrepancies
- The original headline reported **799 triggers**, but the per-schema table summed to **778** (a 21-trigger difference).
- The discrepancy arose because the original audit table listed only **6 triggers** for `audit` instead of **29** (14 base triggers + 15 inherited partition triggers from `trg_forbid_mutation` attached to each child partition of `access_events`, `change_log`, and `events`).
- When all non-internal triggers in `pg_trigger` are counted, the true total is **799** (784 base + 15 inherited).

---

## 5. Indexes Reconciliation: Application vs TOAST Indexes

### 5.1 Authoritative Index Counts Grouped by Schema

| Schema | Base Table Indexes | Child Partition Indexes | Total Application Indexes |
| :--- | :--- | :--- | :--- |
| **`acd`** | 164 | 0 | 164 |
| **`audit`** | 56 | 55 | 111 |
| **`bud`** | 71 | 0 | 71 |
| **`cam`** | 105 | 20 | 125 |
| **`cfg`** | 23 | 0 | 23 |
| **`core`** | 102 | 0 | 102 |
| **`doc`** | 55 | 0 | 55 |
| **`exm`** | 180 | 0 | 180 |
| **`fin`** | 226 | 0 | 226 |
| **`hrm`** | 266 | 10 | 276 |
| **`iam`** | 77 | 0 | 77 |
| **`lms`** | 268 | 15 | 283 |
| **`obe`** | 38 | 0 | 38 |
| **`pay`** | 86 | 0 | 86 |
| **`plat`** | 36 | 0 | 36 |
| **`rpt`** | 34 | 0 | 34 |
| **`stu`** | 13 | 0 | 13 |
| **`sys`** | 43 | 0 | 43 |
| **`tax`** | 39 | 0 | 39 |
| **`wf`** | 44 | 0 | 44 |
| **TOTAL** | **1,926** | **100** | **2,026** |

### 5.2 Resolution of the "2,521 Indexes" Headline
- Direct catalog inspection of `pg_class` reveals:
  - Indexes residing in the 20 application schemas: **2,026**
  - Indexes residing in `pg_toast`: **495**
  - **Sum:** 2,026 + 495 = **2,521**
- **Conclusion:** The headline figure of **2,521** in the original audit inadvertently included the internal storage indexes of the `pg_toast` schema that support large column values (JSONB, TEXT) in the application tables. The true count of user-space application indexes is **2,026** (1,926 on base tables + 100 on child partitions).

---

## 6. RLS Policies Reconciliation: Resolution of Missing `cmd='ALL'`

### 6.1 Authoritative Policy Distribution by Schema and Command

| Schema | SELECT | INSERT | UPDATE | DELETE | ALL (Platform Admin) | Total Policies |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| **`acd`** | 35 | 32 | 32 | 32 | 0 | 131 |
| **`audit`** | 16 | 13 | 10 | 10 | 1 | 50 |
| **`bud`** | 16 | 16 | 13 | 13 | 0 | 58 |
| **`cam`** | 24 | 24 | 19 | 19 | 0 | 86 |
| **`cfg`** | 8 | 2 | 2 | 2 | 6 | 20 |
| **`core`** | 25 | 23 | 23 | 23 | 1 | 95 |
| **`doc`** | 14 | 13 | 10 | 10 | 0 | 47 |
| **`exm`** | 41 | 36 | 32 | 32 | 0 | 141 |
| **`fin`** | 50 | 42 | 37 | 37 | 0 | 166 |
| **`hrm`** | 57 | 57 | 53 | 53 | 0 | 220 |
| **`iam`** | 19 | 12 | 12 | 12 | 6 | 61 |
| **`lms`** | 62 | 61 | 54 | 54 | 0 | 231 |
| **`obe`** | 7 | 7 | 6 | 6 | 0 | 26 |
| **`pay`** | 17 | 17 | 17 | 17 | 0 | 68 |
| **`plat`** | 8 | 6 | 6 | 6 | 5 | 31 |
| **`rpt`** | 9 | 9 | 6 | 6 | 0 | 30 |
| **`stu`** | 2 | 2 | 2 | 2 | 0 | 8 |
| **`sys`** | 1 | 1 | 1 | 1 | 0 | 4 |
| **`tax`** | 10 | 10 | 9 | 9 | 0 | 38 |
| **`wf`** | 9 | 9 | 8 | 8 | 0 | 34 |
| **TOTAL** | **430** | **392** | **352** | **352** | **19** | **1,545** |

### 6.2 Explanation of Original Discrepancies
- The original audit listed column totals of `423 / 379 / 374 / 369` with per-schema rows summing to `~450 / 386 / 358 / 351`.
- The root cause is that PostgreSQL RLS policies can have `cmd = 'ALL'`. The original audit omitted an `ALL` column and failed to categorize the **19 platform administrator policies** (e.g. `cfg.feature_flags_platform_admin`, `iam.roles_platform_admin`, `plat.integrations_platform_admin`).
- With `ALL` isolated, the column totals sum cleanly to the headline:  
  **430 + 392 + 352 + 352 + 19 = 1,545**.

---

## 7. SQL Catalog Queries Used

```sql
-- 1. Table Counts Breakdown
SELECT 
    n.nspname AS schemaname,
    count(*) FILTER (WHERE c.relkind = 'r' AND NOT c.relispartition) AS ordinary_tables,
    count(*) FILTER (WHERE c.relkind = 'p') AS partitioned_parents,
    count(*) FILTER (WHERE c.relkind IN ('r', 'p') AND NOT c.relispartition) AS base_tables,
    count(*) FILTER (WHERE c.relispartition) AS child_partitions,
    count(*) AS total_pg_tables
FROM pg_class c
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
  AND c.relkind IN ('r', 'p')
GROUP BY n.nspname
ORDER BY n.nspname;

-- 2. Constraints Breakdown
SELECT 
    n.nspname AS schemaname,
    count(*) FILTER (WHERE co.contype = 'p' AND co.conparentid = 0) AS pk_base,
    count(*) FILTER (WHERE co.contype = 'p' AND co.conparentid <> 0) AS pk_inh,
    count(*) FILTER (WHERE co.contype = 'f' AND co.conparentid = 0) AS fk_base,
    count(*) FILTER (WHERE co.contype = 'f' AND co.conparentid <> 0) AS fk_inh,
    count(*) FILTER (WHERE co.contype = 'u') AS uq,
    count(*) FILTER (WHERE co.contype = 'c') AS ck,
    count(*) FILTER (WHERE co.contype = 'x') AS ex,
    count(*) FILTER (WHERE co.contype = 't') AS tg,
    count(*) FILTER (WHERE co.conparentid = 0) AS total_base,
    count(*) AS total_all
FROM pg_constraint co
JOIN pg_class cl ON cl.oid = co.conrelid
JOIN pg_namespace n ON n.oid = cl.relnamespace
WHERE n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
GROUP BY n.nspname
ORDER BY n.nspname;

-- 3. Triggers Breakdown
SELECT 
    n.nspname AS schemaname,
    count(*) FILTER (WHERE t.tgparentid = 0) AS base_triggers,
    count(*) FILTER (WHERE t.tgparentid <> 0) AS inherited_triggers,
    count(*) AS total_triggers
FROM pg_trigger t
JOIN pg_class c ON c.oid = t.tgrelid
JOIN pg_namespace n ON n.oid = c.relnamespace
WHERE NOT t.tgisinternal
  AND n.nspname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
GROUP BY n.nspname
ORDER BY n.nspname;

-- 4. Indexes Breakdown
SELECT 
    i.schemaname,
    count(*) FILTER (WHERE NOT c.relispartition) AS base_indexes,
    count(*) FILTER (WHERE c.relispartition) AS child_partition_indexes,
    count(*) AS total_indexes
FROM pg_indexes i
JOIN pg_class c ON c.relname = i.tablename AND c.relnamespace = (SELECT oid FROM pg_namespace WHERE nspname = i.schemaname)
WHERE i.schemaname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
GROUP BY i.schemaname
ORDER BY i.schemaname;

-- 5. Policies Breakdown
SELECT 
    schemaname,
    count(*) FILTER (WHERE cmd = 'SELECT') AS select_policies,
    count(*) FILTER (WHERE cmd = 'INSERT') AS insert_policies,
    count(*) FILTER (WHERE cmd = 'UPDATE') AS update_policies,
    count(*) FILTER (WHERE cmd = 'DELETE') AS delete_policies,
    count(*) FILTER (WHERE cmd = 'ALL') AS all_policies,
    count(*) AS total_policies
FROM pg_policies
WHERE schemaname IN ('acd', 'audit', 'bud', 'cam', 'cfg', 'core', 'doc', 'exm', 'fin', 'hrm', 'iam', 'lms', 'obe', 'pay', 'plat', 'rpt', 'stu', 'sys', 'tax', 'wf')
GROUP BY schemaname
ORDER BY schemaname;
```
