# Part J: Seeds, Migrations & Database Drift Analysis

**Document:** `docs/audits/rbac-coverage/Part-J_seeds-drift.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Migration & Seed Source Artifacts

**[CONFIRMED: Repository scan and file inspection]:**

The role, permission, and grant structures in CampXSync originate from the following sequence of repository scripts:

| File Path | Purpose & Content | Target Database Objects |
| :--- | :--- | :--- |
| `database/migrations/001_initial_schema.sql` | Baseline platform DDL (Wave 0) | Core tables; initial stub functions for `iam.has_permission()`. |
| `database/migrations/Scripts_V3.0/20_tables.sql` | Enterprise schema tables | `iam.permissions`, `iam.roles`, `iam.role_templates`, `iam.role_template_permissions`, `iam.api_clients`. |
| `database/migrations/Scripts_V3.0/test_rbac.sql` | Authoritative RBAC DDL & Seeds | 33 role templates, 2,166 template permission bindings, 84 role grant rules, 9 hierarchy rows, and operational functions (`has_permission`, `can_grant_role`, `guard_role_assignment`). |
| `database/seed-data/wave0_dev_test_seed.sql` | Developer environment test seed | Initial tenant `a0000000-0000-0000-0000-000000000001`, bootstrap user profiles, and test role assignments. |

---

## 2. Drift Analysis: Live Database vs Migration Files

**[CONFIRMED: Automated comparison between `test_rbac.sql` and DEV `azulbaxszsqxymqmlwfe`]:**

### 1. Template & Permission Parity
- **Template Count:** 33 templates defined in `test_rbac.sql` ⟷ **33 templates** in DEV `iam.role_templates` (**0 drift**).
- **Template-Permission Links:** 2,166 links in `test_rbac.sql` ⟷ **2,166 links** in DEV `iam.role_template_permissions` (**0 drift**).
- **Role Grant Rules:** 84 rows in `test_rbac.sql` ⟷ **84 rows** in DEV `iam.role_grant_rules` (**0 drift**).
- **Tenant Replication Drift:** Evaluated across all **49 tenants** in `iam.roles`: every tenant holds exactly the same 33 roles cloned from templates (**0 drift across tenants**).

### 2. Operational Discrepancy vs Governance Documents
While the live database is 100% synchronized with `Scripts_V3.0/test_rbac.sql`, **both the file and the database diverge substantially from the target architecture in `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v2_3.md`**:
- Roles `R32` and `R33` catalogue IDs are swapped.
- Roles `R34` through `R39` and `R02`, `R27` do not exist as role templates.
- `COLLEGE_ADMIN` holds 180 permissions across Academic, Finance, and HR instead of being bounded to CADM-01…CADM-09.
- `AUDITOR` holds `audit.admin` and `audit.write` instead of being strictly read-only.
- `TENANT_ADMIN` exists with wildcard `*`.

---

## 3. The Single Source of Truth Today

**[CONFIRMED: Architectural classification]:**
1. **Live Operational Source of Truth (Database Runtime):**
   - The running PostgreSQL instance `azulbaxszsqxymqmlwfe`, seeded by `database/migrations/Scripts_V3.0/test_rbac.sql`.
2. **Canonical Governance Source of Truth (Design Target):**
   - `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v2_3.md` (Dated 2026-11-20, incorporating owner decisions D-RBAC-M01 through M16).
3. **Execution Gap:**
   - The live database has **not yet received migration scripts** to apply Matrix v2.3. Applying changes requires owner approval of the coverage map first (Matrix §17).

---

## 4. DEV Test Personas (Roles Only — Zero Credentials)

**[CONFIRMED: Inspection of `wave0_dev_test_seed.sql` and `iam.role_assignments` on DEV]:**

| Test Persona (Email Handle) | Assigned Role (Live Code) | Role Scope | Mapped Desktop Portal |
| :--- | :--- | :--- | :--- |
| `dev.admin@campxsync.test` | `TENANT_ADMIN`, `COLLEGE_ADMIN` | Tenant / College | `/college/*` (Admin Desk) |
| `dev.college_admin@campxsync.test` | `COLLEGE_ADMIN` | College | `/college/*` (Admin Desk) |
| `dev.registrar@campxsync.test` | `REGISTRAR` | Campus | `/college/lifecycle` (Registrar Desk) |
| `dev.faculty1@campxsync.test` | `FACULTY` | Department / Sections | `/faculty/*` (Faculty Desk) |
| `dev.faculty2@campxsync.test` | `FACULTY` | Department / Sections | `/faculty/*` (Faculty Desk) |
| `dev.student1@campxsync.test` | `STUDENT` | Own Student Profile | `/student/*` (Student Portal) |
| `dev.student2@campxsync.test` | `STUDENT` | Own Student Profile | `/student/*` (Student Portal) |
