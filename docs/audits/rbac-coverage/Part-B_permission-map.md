# Part B: Permission Coverage Map (Live Catalog vs Matrix v2.3)

**Document:** `docs/audits/rbac-coverage/Part-B_permission-map.md`  
**Companion File:** [`docs/audits/rbac-coverage/Part-B_permission-map.csv`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-B_permission-map.csv) (Complete 571-row CSV export)  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Catalog Summary & Key Counts

**[CONFIRMED: Database queries on `iam.permissions` and `iam.role_template_permissions`]:**
- **Total Unique Permission Keys in Live Catalog:** **571 keys** (including wildcard `*`).
- **Keys Assigned to at least One Role Template:** **493 keys**.
- **Orphaned Keys (Assigned to ZERO Role Templates):** **78 keys** (e.g. `adm03.manage`, `adm05.manage`, `.own` suffixes, etc.).

### Current Permission Counts Held by Key Roles

| Matrix Code | Role Name | Live Template Code | Permissions Held in DB | Target Ceiling per Matrix v2.3 | Notes |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **R01** | Super Admin | `SUPER_ADMIN` | **0** in template (Implicit all) | Platform / Cross-Institute | Evaluated via `plat.platform_admins` table; bypasses tenant IAM template. |
| **R02** | System Admin | `SYSTEM_ADMIN` | **0** (Role missing from DB) | Platform Technical Operations | Technical service identity; currently unseeded in `iam.role_templates`. |
| **R03** | Institution Admin | `INSTITUTION_ADMIN` | **184** | **IADM-01…IADM-08 only** | Holds all 180 College Admin keys PLUS `adm01.manage`, `adm01.review`, `adm01.write`, `core.view.all`. |
| **R04** | College Admin | `COLLEGE_ADMIN` | **180** | **CADM-01…CADM-09 only** | Massive cross-domain holding: spans Academic, Finance, HR, Communication. |
| **R05** | Principal | `PRINCIPAL` | **173** | Institution Academic & Ops | Domain-admin owner with R07 Dean for ACD/LMS; approval authority across domains. |
| **R07** | Dean | `DEAN` | **95** | Academic Domain Admin | Joint domain-admin owner with R05 Principal (Owner Decision D-RBAC-M09). |
| **R28** | Auditor | `AUDITOR` | **45** | Read-Only Audit (`V/AU/E`) | **Deviation:** Holds `audit.admin` and `audit.write` in DB. |
| **R31** | Registrar | `REGISTRAR` | **14** | STM Principal (Admissions/Enrolment/Lifecycle) | Seeded with 14 student lifecycle keys; holds 1 live user in DEV. |

---

## 2. Module Distribution of Live R03 & R04 Holdings

The live database reveals that `COLLEGE_ADMIN` (180 keys) is an exact subset of `INSTITUTION_ADMIN` (184 keys). Their distribution across domain prefixes is:

| Prefix Family | Service / Domain Description | Keys on R04 | Keys on R03 | Target Service per Matrix | Matrix Policy for R03 | Matrix Policy for R04 | Matrix Citation |
| :--- | :--- | :--- | :--- | :--- | :--- | :--- | :--- |
| `adm01` | Institute & Tenant Lifecycle | 2 | 5 | `IADM-01` | **KEEP** (own institute) | **REMOVE** | Matrix §8 |
| `adm02` | Platform Config & Policy | 5 | 5 | `IADM-02` | **KEEP** (allowed settings) | **REMOVE M** → Keep V effective | Matrix §8, §9 |
| `adm03` | API Clients & Machine IDs | 3 | 3 | `NO OWNER` | **DORMANT** (IADM-D5) | **DORMANT** (IADM-D5) | Matrix §2, §14 |
| `adm04` | Student & Faculty Directory | 2 | 2 | `CADM-04` | **REMOVE** after handover | **KEEP** (admin attrs only) | Matrix §9 |
| `adm05` | Campus Resources & Ops | 3 | 3 | `CADM-05` | **REMOVE** | **KEEP** (provisional vs R24) | Matrix §9 |
| `adm06` | Reporting & Analytics | 5 | 5 | `CADM-06` | **REMOVE** | **KEEP** (college scope) | Matrix §9 |
| `adm07` | Audit & Compliance | 5 | 5 | `CADM-07` | **REMOVE** | **KEEP** (compliance evidence) | Matrix §9 |
| `adm08` | Integration & Data Sync | 5 | 5 | `CADM-08` | **REMOVE** | **KEEP** (college scope) | Matrix §9 |
| `adm09` | Timetable Management | 5 | 5 | `CADM-09` | **REMOVE** | **KEEP** (college scope) | Matrix §2 (CADM-D1), §9 |
| `acd01`–`acd10` | Academic Services (10 microservices) | 35 | 35 | `ACD-01`…`ACD-10` | **REMOVE** | **REMOVE M/W** → Keep V/E scoped | Matrix §7, §10, §11.1 |
| `fin01`–`fin10` | Finance Services (10 microservices) | 26 | 26 | `FIN-01`…`FIN-10` | **REMOVE** | **REMOVE** from Tax/Payroll; V/E scoped on fees | Matrix §7, §10, §11.4 |
| `hrm01`–`hrm09` | Human Resources (9 microservices) | 27 | 27 | `HRM-01`…`HRM-09` | **REMOVE** | **REMOVE** from Performance/Fixation; V directory | Matrix §7, §10, §11.5 |
| `cam01`–`cam08` | Communication (8 microservices) | 41 | 41 | `CAM-01`…`CAM-08` | **REMOVE** | **REMOVE M/W** → CFG college prefs only | Matrix §7, §11.6 |
| `audit`, `cfg`, `core`, `doc`, `rpt`, `wf` | Shared Infrastructure Services | 16 | 17 | `SHARED` | Scoped governance | Scoped operational view | Matrix §8, §9, §12 |

---

## 3. Highlighting 2026-11-20 Owner Decisions

**[CONFIRMED: Cross-check against Matrix v2.3 §§18, 21, 22]:**

### 1. Decision D-RBAC-M12: R04 Removed from Tax, Payroll, and Performance
The live database currently assigns the following sensitive operational keys to `COLLEGE_ADMIN`. Under Decision D-RBAC-M12, all of these **must be revoked**:
- **Tax Management (`FIN-04`):** `fin04.view`, `fin04.write`
- **Payroll (`FIN-06`):** `fin06.view`, `fin06.write`
- **Performance Management (`HRM-04`):** `hrm04.manage`, `hrm04.review`, `hrm04.view`
- **Payroll Fixation (`HRM-05`):** `hrm05.manage`, `hrm05.view`

### 2. Decision D-RBAC-M09 & CADM-D3: R04 Deprecated as Academic & LMS Module Owner
- In the live DB, `COLLEGE_ADMIN` holds broad management keys: `acd01.manage`, `acd02.manage`, `acd03.manage`, `acd04.manage`, `acd05.manage`, `acd06.manage`, `acd07.manage`, `acd09.manage`.
- **Target Policy:** All `.manage` and `.write` rights move to **Domain Admin: R07 Dean + R05 Principal**. `COLLEGE_ADMIN` retains read-only (`V`) or scoped export (`E`) where operationally necessary.

### 3. Decision IADM-D7: Collapse Tenant Admin Wildcard (`*`)
- Wildcard `*` exists on exactly 1 role: `TENANT_ADMIN`.
- **Target Policy:** Eliminate wildcard `*` completely. Transition the single active user to `INSTITUTION_ADMIN` (R03) with explicit IADM-01…IADM-08 permissions.

---

## 4. Keys with No Owner in the Target Architecture

**[CONFIRMED: Catalog audit of orphaned keys]:**

1. **`adm03` Family (API Clients):**
   - Keys: `adm03.manage`, `adm03.view`, `adm03.write`, `adm03.view.all`, `adm03.view.own`, `adm03.write.own`.
   - **Status:** **DORMANT** per Owner Decision IADM-D5. `adm03.manage` is currently assigned to NO role template in the entire platform.
2. **`adm05.manage` (Campus Resources):**
   - Present in `iam.permissions`, but assigned to **NO role template**.
3. **`plat.*` Keys:**
   - Keys: `plat.read`, `plat.write`, `plat.read.own`, `plat.write.own`.
   - **Status:** Stored in `iam.permissions`, but assigned to **NO tenant role template** because platform administrators authenticate via `plat.platform_admins`.
4. **Self-Service / Own Keys (`.own` Suffixes):**
   - 28 permission keys with `.own` suffixes (e.g., `acd01.write.own`, `audit.admin.own`, `fin01.write.own`) are not mapped to any role templates in `iam.role_template_permissions`.

---

## 5. Summary Table of Full CSV Export

The complete 571-row permission-by-permission coverage map is stored in:
👉 [`docs/audits/rbac-coverage/Part-B_permission-map.csv`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-B_permission-map.csv)

The CSV provides full columns for every key:
- `permission_key`
- `plain_language_meaning`
- `owning_service_module`
- `roles_holding_count`
- `held_by_R01_super_admin` through `held_by_R31_registrar`
- `matrix_target_service`
- `matrix_R03_action`
- `matrix_R04_action`
- `matrix_new_holder`
- `matrix_citation`
- `decision_flags`
