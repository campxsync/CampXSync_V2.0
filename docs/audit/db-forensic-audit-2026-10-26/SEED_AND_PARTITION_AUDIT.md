# CampXSync — Seed & Partitioning Forensic Audit
## Reference Data Inventory, Idempotency Analysis & Partition Maintenance

**Audit Date:** 2026-10-26  
**Auditor:** Antigravity Agentic AI (Read-Only Forensic Mode)  
**Target Environment:** `CampXSync-Architecture-Dev` (Supabase Dev ref: `azulbaxszsqxymqmlwfe`)  

---

## 1. Seed & Reference Data Audit

### 1.1 Live Reference Data State on Development Target

Catalog inspection of `CampXSync-Architecture-Dev` reveals a tightly controlled development baseline:

| Table | Live Row Count | Entity Identification & Context |
| :--- | :--- | :--- |
| **`core.tenants`** | **2** | 1. `a0000000-0000-0000-0000-000000000001` (`DEV_TEST_TENANT` — *"CampX Architecture Dev Test Institute"*).<br>2. `b0000000-0000-0000-0000-000000000002` (`T_FOREIGN` — *"Foreign Test Tenant"* for isolation testing). |
| **`core.colleges`** | **1** | `a0000000-0000-0000-0000-000000000002` (`DEV_TEST_COLLEGE` — *"CampX Dev College of Engineering"*). |
| **`core.departments`** | **56** | Standard institutional academic departments provisioned during Wave 3 and Wave 9 testing. |
| **`iam.roles`** | **60** | Exactly 30 system roles provisioned for Tenant 1 + 30 system roles for Tenant 2 via `iam.seed_system_roles`. |
| **`iam.permissions`** | **547** | Complete platform permission catalog codified in `80_seed.sql`. |
| **`iam.role_permissions`** | **4,276** | Role-to-permission mappings across both active tenants (2,138 bindings per tenant). |
| **`iam.user_profiles`** | **1** | `ba77b8f7-ab32-46bd-85d2-5aade3526880` (`dev.admin@campxsync.test` — Dev Test College Admin). |
| **`iam.role_assignments`** | **2** | Active role assignments binding the administrator profile to `COLLEGE_ADMIN`. |
| **`audit.events`** | **53** | Compliance events emitted during Wave 3 testing (20 `DEPARTMENT_CREATED`, 11 `PROGRAM_CREATED`, etc.). |
| **`sys.outbox_events`** | **53** | Corresponding transactional outbox events emitted in tandem with `audit.events`. |
| **Domain Tables** | **0** | `stu.students`, `core.persons`, `acd.courses`, `acd.batches`, `acd.timetables`, `acd.attendance_sessions` = **0 rows**. |

**Key Finding**: Domain entity tables contain exactly **0 residual rows**. This confirms that all live integration test suites (`LivePostgresStudentPersistenceTest`, `LivePostgresTimetablePersistenceTest`, `LivePostgresAttendancePersistenceTest`, etc.) strictly execute in uncommitted transactions that roll back at test closeout.

---

### 1.2 Repository Seed Script Evaluation

| Script File Path | Target Environment | Design Strategy | Idempotency Model | Live Match Status |
| :--- | :--- | :--- | :--- | :--- |
| **`Documentation/DatabaseDesign/Supabase/Scripts_V3.0/80_seed.sql`** | Universal Bootstrap | Seeds 547 system permissions and declares `iam.seed_system_roles(uuid)`. | `ON CONFLICT (id) DO NOTHING` | **VERIFIED APPLIED**: All 547 permissions and procedure exist in live catalog. |
| **`database/seed-data/wave0_dev_test_seed.sql`** | `CampXSync-Architecture-Dev` (`azulbaxszsqxymqmlwfe`) | Provisions `DEV_TEST_TENANT`, `DEV_TEST_COLLEGE`, and `dev.admin@campxsync.test`. | **Precondition Assertion** (throws exception if tenant exists; pure `INSERT`). | **VERIFIED APPLIED**: Exact UUIDs, codes, and emails present in live database. |
| **`database/seed-data/staging_seed_college_a_and_b.sql`** | Legacy Staging (`kpwdvvcschankgggitnj`) | Multi-college test fixture (College A & B with 50 students, 4 faculty, timetables). | `ON CONFLICT (id) DO NOTHING` | **NOT FOUND**: Hardcoded for prohibited legacy staging tenant `11111111-...`. |
| **`database/migrations/V20261014_02__provision_test_tenant_staging.sql`** | Legacy Staging (`kpwdvvcschankgggitnj`) | Provisions staging tenant `CAMPX_STAGING` (`11111111-...`). | `ON CONFLICT (id) DO NOTHING` with postcondition checks. | **NOT FOUND**: Prohibited staging target. |
| **`database/migrations/V20261014_03__provision_staging_college_admin_identity.sql`** | Legacy Staging (`kpwdvvcschankgggitnj`) | Provisions `campxsync@gmail.com` as admin on `11111111-...`. | `ON CONFLICT (id) DO UPDATE` | **NOT FOUND**: Prohibited staging target. |

---

### 1.3 Seed Idempotency & Dependency Ordering

1. **System Permissions (`iam.permissions`)**: Fully idempotent via `ON CONFLICT (id) DO NOTHING`. Safe to rerun at any time.
2. **System Role Seeding (`iam.seed_system_roles(p_tenant uuid)`)**:
   - Automatically triggered upon tenant creation via trigger `trg_seed_roles` on `core.tenants`.
   - Inserts 30 system roles (`PLATFORM_ADMIN`, `COLLEGE_ADMIN`, `HOD`, `FACULTY`, `STUDENT`, `PARENT`, `ACCOUNTANT`, etc.) and 2,138 role-permission mappings.
   - Idempotent: Skips role insertion if role code already exists for tenant.
3. **Tenant & Identity Provisioning**:
   - `wave0_dev_test_seed.sql` uses a **fail-closed pre-write collision check**:
     ```sql
     IF EXISTS (SELECT 1 FROM core.tenants WHERE id = v_tenant_id OR code = v_tenant_code) THEN
       RAISE EXCEPTION 'Precondition Failed: Tenant already exists. Aborting without modifying data.';
     END IF;
     ```
   - While fail-closed checks prevent accidental data overwrites, they are **not repeatable** on an already seeded environment.
   - **Recommendation**: For the new migration sequence, separate structural seed data (permissions and role templates) from environment test fixtures. Test fixtures should support an explicit idempotent upsert mode (`ON CONFLICT (id) DO UPDATE`).

---

## 2. Partitioning Architecture & Maintenance Audit

### 2.1 Partitioned Tables Inventory

The CampXSync database utilizes declarative range partitioning across 6 tables designed for high-throughput append operations:

1. `audit.access_events` (Partition key: `RANGE (created_at)`)
2. `audit.change_log` (Partition key: `RANGE (created_at)`)
3. `audit.events` (Partition key: `RANGE (created_at)`)
4. `cam.delivery_events` (Partition key: `RANGE (occurred_at)`)
5. `hrm.attendance_punches` (Partition key: `RANGE (punched_at)`)
6. `lms.progress_events` (Partition key: `RANGE (created_at)`)

### 2.2 Live Child Partition Boundaries

Each partitioned parent table has exactly 5 child partitions attached:

```text
[Parent Table]
  ├── [table]_default   (DEFAULT partition — catches any values outside defined monthly ranges)
  ├── [table]_p202610   (FOR VALUES FROM ('2026-10-01 00:00:00+00') TO ('2026-11-01 00:00:00+00'))
  ├── [table]_p202611   (FOR VALUES FROM ('2026-11-01 00:00:00+00') TO ('2026-12-01 00:00:00+00'))
  ├── [table]_p202612   (FOR VALUES FROM ('2026-12-01 00:00:00+00') TO ('2027-01-01 00:00:00+00'))
  └── [table]_p202701   (FOR VALUES FROM ('2027-01-01 00:00:00+00') TO ('2027-02-01 00:00:00+00'))
```

---

### 2.3 Partition Expiration Horizon Risk (2027-02-01)

**CRITICAL OPERATIONAL FINDING**:
- Child partitions are provisioned only up to **2027-02-01 00:00:00+00** (`_p202701`).
- There are **zero partitions defined for February 2027 onwards**.
- In PostgreSQL, when a range-partitioned table has a `DEFAULT` partition, any row inserted with a timestamp beyond the existing range partitions will be routed directly into the `_default` partition.
- **Risk**: Once data enters the `_default` partition, attaching new monthly partitions for those periods in the future becomes impossible without first locking the parent table, detaching the default partition, and manually moving the data out.
- **Root Cause**: The PostgreSQL extension `pg_cron` is not installed on the Supabase project, meaning there is no automated database job running `sys.ensure_partitions()`.

---

### 2.4 Partition Maintenance Function Defect

The partition creation function `sys.ensure_partitions(p_months_ahead int DEFAULT 3)` is defined as:
```sql
CREATE OR REPLACE FUNCTION sys.ensure_partitions(p_months_ahead integer DEFAULT 3)
RETURNS void LANGUAGE plpgsql SECURITY DEFINER SET search_path TO '' AS $$
DECLARE t text; m date; nm text; i int;
BEGIN
  FOREACH t IN ARRAY ARRAY['audit.change_log', 'audit.events', 'audit.access_events', 
                           'lms.progress_events', 'hrm.attendance_punches', 'cam.delivery_events'] LOOP
    FOR i IN 0..p_months_ahead LOOP
      m  := (date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => i))::date;
      nm := format('%s_p%s', t, to_char(m, 'YYYYMM'));
      IF to_regclass(nm) IS NULL THEN
        EXECUTE format('CREATE TABLE %s PARTITION OF %s FOR VALUES FROM (%L) TO (%L)', nm, t,
                       (m::timestamp AT TIME ZONE 'UTC'), ((m + interval '1 month')::timestamp AT TIME ZONE 'UTC'));
        EXECUTE format('REVOKE ALL ON %s FROM anon, authenticated', nm);
      END IF;
    END LOOP;
  END LOOP;
END $$;
```

**Defect Analysis**:
1. When `sys.ensure_partitions` creates a new partition, it executes `REVOKE ALL`, but **does NOT execute `ALTER TABLE ... ENABLE ROW LEVEL SECURITY`**.
2. Although partition queries routed through parent tables inherit parent RLS in PostgreSQL 14+, child partitions that do not have RLS explicitly enabled can be queried directly if any grant is subsequently applied.
3. **Remediation**: Update `sys.ensure_partitions` to include `EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', nm);`.
4. Establish an external scheduler (e.g., Supabase Edge Function scheduled via GitHub Actions, AWS EventBridge, or Kubernetes CronJob) to call `SELECT sys.ensure_partitions(6);` monthly.
