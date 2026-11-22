# Part H: Audit Logging & Auditor (R28) Posture

**Document:** `docs/audits/rbac-coverage/Part-H_audit.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Audit Trail Coverage & Immutability

**[CONFIRMED: Database inspection of schema `audit` and table triggers on DEV `azulbaxszsqxymqmlwfe`]:**

### Audit Capture Trigger
Every mutation on security-critical IAM tables (`iam.role_assignments`, `iam.roles`, `iam.api_clients`) triggers PostgreSQL function `audit.capture()` via trigger `trg_audit`:
- **Captured Event Fields:**
  - `tenant_id` (Tenant isolation context)
  - `schema_name` and `table_name`
  - `operation` (`INSERT`, `UPDATE`, `DELETE`)
  - `record_id` (Primary key UUID of the affected entity)
  - `old_data` (JSONB snapshot of state before modification)
  - `new_data` (JSONB snapshot of state after modification)
  - `performed_by` (Resolved from `auth.uid()`)
  - `created_at` (Immutable server timestamp)

### Immutability Posture
- **Partitioned Storage:** Partitioned monthly across tables (e.g. `audit.change_log_p202611`, `audit.change_log_p202612`).
- **PostgreSQL RLS Policies on `audit.change_log`:**
  - `change_log_insert` (for `authenticated`): Allows trigger-driven appends.
  - `change_log_select` (for `authenticated`): Governed by `audit.read` permission.
  - **UPDATE / DELETE Policies:** **ZERO policies exist**. Neither standard users nor administrators holding tenant roles can execute `UPDATE` or `DELETE` against the audit log.
- **Bypass Exception:** Connections running as PostgreSQL superuser `postgres` (`BYPASSRLS`) or invoking database console tools can modify or truncate audit partitions directly.

---

## 2. Auditor (R28) Permission Holdings & Matrix Gaps

**[CONFIRMED: SQL query against `iam.role_template_permissions` for role `AUDITOR`]:**

Role `AUDITOR` (R28) holds **45 permission keys** in the live database template:

### Critical Security Discrepancy
**[CONFIRMED]:** The live database assigns administrative and mutation keys to `AUDITOR`:
- `audit.admin` (Audit Service Administration)
- `audit.write` (Direct Audit Log Write)
- `wf.write` (Workflow Modification)
- `core.comment` (User Comment Modification)
- 5 HR write keys: `hrm02.write.own`, `hrm03.write.own`, `hrm04.write.own`, `hrm07.write.own`, `hrm09.write.own`

### Comparison with Governing Decisions
Under Matrix v2.3 (§8, §12, §21.2, and §22 change 2):
1. **Mandatory Read-Only Mandate:** *“Auditor R28 changed from M to read-only on IADM-08 and shared Audit... Auditor stays independent of audit administration.”*
2. **Action Required:** `audit.admin` and `audit.write` **must be revoked** from `AUDITOR`. R28 must hold strictly `audit.read`, `audit.read.payload`, and scoped domain view (`V / AU / E`) keys.

---

## 3. Platform Admin (R01 / R02) Audit Posture

**[CONFIRMED: Cross-check against Matrix v2.3 §§8, 12, 21.2]:**

1. **R01 Super Admin & R02 System Admin:**
   - Matrix §8 assigns `M` (Manage) on `IADM-08 Audit, Health & Operations`.
   - Matrix §21.2 clarifies: *“R01/R02 M on Audit means operating the service (retention policies, archival health, observability), never editing or deleting records.”*
   - In the database, platform retention policies are defined in table `audit.retention_policies`, which R01/R02 administer.
