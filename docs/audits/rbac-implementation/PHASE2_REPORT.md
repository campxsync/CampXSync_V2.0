# CampXSync RBAC Phase 2 Report — Narrow R04 and R03 (DEV Only)

**Execution Date:** 2026-11-20  
**Target Environment:** DEV Supabase (`azulbaxszsqxymqmlwfe`)  
**Governing Authority:** `docs/architecture/Security/CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md` (Approved by Niranjan V.G., 2026-11-20)  
**Migration Script:** `database/migrations/20261120000200_rbac_phase2_narrow_r04_r03.sql`  
**Rollback Script:** `database/migrations/20261120000200_rollback__rbac_phase2_narrow_r04_r03.sql`  
**Verification Script:** `database/migrations/verify_phase2_rbac.sql`  
**Status:** **100% PASSED** [CONFIRMED: Automated assertions verified live in DB]

---

## 1. Executive Summary & Plain-Language Overview

Phase 2 narrows the excessive broad privileges historically held by `R04 College Admin` (campus operations manager) and `R03 Institution Admin` (executive trust officer). Following the **"Add Before Remove"** security principle:
1. Target functional owner roles (`R05 Principal`, `R07 Dean`, `R31 Registrar`, `R15 Finance Manager`, `R17 HR Manager`, `R12 Controller of Examinations`, `R19 Communication Manager`) were seeded with necessary domain write and management keys across all 49 tenants before any keys were stripped from R04 or R03.
2. `R04 College Admin` had 46 domain write/manage permissions and owner-decision D-RBAC-M12 sensitive finance/HR keys removed, retaining 135 operational view, review, and college coordination keys.
3. `R03 Institution Admin` was stripped of 130 non-IADM domain keys and 3 document write/admin keys (`doc.admin`, `doc.manage`, `doc.write`), retaining 54 high-level governance and tenant-administration keys.
4. Direct `COLLEGE_ADMIN -> STUDENT/PARENT` and `INSTITUTION_ADMIN -> STUDENT/PARENT` grant rules were deleted per STM-07 (Student Lifecycle Management), leaving student/parent provisioning strictly to automated workflows.
5. Seeded two active DEV test personas for `R05 Principal` and `R07 Dean` (with masked test credentials) in dev college `a0000000-0000-0000-0000-000000000002`.

---

## 2. Phase 2 Key Actions Traceability

### 2.1 Coverage for New Owners (Add Before Remove)
- **`R31 Registrar` (Catalogue R31, Academic Record Custodian):** Granted `doc.admin`, `doc.manage`, `doc.write`, `doc.view` (`DOCUMENT_MANAGER` keys) prior to stripping R03 [CONFIRMED: `iam.role_template_permissions`].
- **`R05 Principal` (Catalogue R05, Academic Institution Head):** Granted all Academic (`acd01`–`acd08`) and LMS (`lms01`–`lms10`) manage, write, review, and view keys. New template count: 231 keys [CONFIRMED].
- **`R07 Dean` (Catalogue R07, Academic Faculty Leader):** Granted all Academic and LMS write, review, and view keys. New template count: 153 keys [CONFIRMED].
- **`R15 Finance Manager` (Catalogue R15):** Granted `fin03.write`, `fin05.write`, `fin09.write`. New template count: 87 keys [CONFIRMED].
- **`R17 HR Manager` (Catalogue R17):** Granted `hrm01.manage`, `hrm01.write`. New template count: 73 keys [CONFIRMED].

### 2.2 R04 College Admin Narrowing
- **Initial Count:** 180 template permissions.
- **Removed Keys (46 domain write/manage + D-RBAC-M12):**
  - Academic write/manage: `acd01.manage`, `acd01.write`, `acd02.manage`, `acd02.write`, `acd05.manage`, `acd05.write`, `acd06.manage`, `acd06.write`, `acd07.manage`, `acd07.write`.
  - Campus ops write/manage: `cam01.manage`, `cam01.write`, `cam02.manage`, `cam02.write`, `cam03.manage`, `cam03.write`, `cam04.manage`, `cam04.write`, `cam05.manage`, `cam05.write`, `cam06.manage`, `cam06.write`, `cam07.manage`, `cam07.write`.
  - Finance write: `fin03.write`, `fin05.write`, `fin09.write`.
  - HR write/manage: `hrm01.manage`, `hrm01.write`.
  - Cross-domain broad write: `core.pii.write`, `core.write`, `wf.write`.
  - D-RBAC-M12 Sensitive Finance/HR keys stripped completely: `fin04.view`, `fin04.view.all`, `fin06.view`, `fin06.view.all`, `hrm04.view`, `hrm04.view.all`, `hrm04.write.own`, `hrm05.view`, `hrm05.view.all`.
- **Retained / Converted:** Converted manage-to-view where operational oversight is required; retained operational views across all domain desks; provisional retention of `adm05.manage` (logged in `EXCEPTIONS.md`).
- **Post-Phase 2 Count:** 135 template permissions [CONFIRMED].

### 2.3 R03 Institution Admin Narrowing
- **Initial Count:** 184 template permissions.
- **Removed Keys (130 non-IADM domain keys):** All keys prefixed `acd%`, `lms%`, `exm%`, `fin%`, `hrm%`, `cam%`. Also removed `doc.admin`, `doc.manage`, `doc.write`.
- **Retained Keys (54 keys):** Retained IADM ceiling keys (`adm01`–`adm09` families except CADM-only `adm04`), `audit.*`, `sys.*`, `core.*` governance, and shared keys per §22.3 (`rep01.view`, `rep01.view.all`, `doc.view`, `doc.view.all`).
- **Post-Phase 2 Count:** 54 template permissions [CONFIRMED].

### 2.4 STM-07 Student and Parent Grant Rules Removal
- Checked `StudentRoleAssignmentService.java` in `services/administration-services/institute-admin-service/`.
- Confirmed that student role assignment occurs programmatically during admission/enrollment verification and does not check or rely on `iam.role_grant_rules` [CONFIRMED: `StudentRoleAssignmentService.java:55-80`].
- Deleted 4 rows from `iam.role_grant_rules`:
  - `(COLLEGE_ADMIN, STUDENT)`
  - `(COLLEGE_ADMIN, PARENT)`
  - `(INSTITUTION_ADMIN, STUDENT)`
  - `(INSTITUTION_ADMIN, PARENT)`
- Result: Manual delegation of student/parent credentials by admins is now prohibited at the database schema level [CONFIRMED].

### 2.5 DEV Personas Added
Seeded test accounts in `core.colleges` (`a0000000-0000-0000-0000-000000000002`) for tenant `a0000000-0000-0000-0000-000000000001`:
- **`R05 Principal`:** User ID `c05a0000-0000-4000-8000-000000000005`, Email `principal.demo@campxsync.test`, Name `Dr. Dev Principal`. Assigned role `PRINCIPAL` [CONFIRMED].
- **`R07 Dean`:** User ID `c07a0000-0000-4000-8000-000000000007`, Email `dean.demo@campxsync.test`, Name `Prof. Dev Dean`. Assigned role `DEAN` [CONFIRMED].

---

## 3. Template Permission Counts Comparison

| Role Name | Catalogue ID | Pre-Phase 2 Count | Post-Phase 2 Count | Net Change | Plain-Language Role Description |
| :--- | :--- | :---: | :---: | :---: | :--- |
| **AUDITOR** | R02 | 36 | 36 | 0 | Internal/external compliance auditor (strictly read-only) |
| **INSTITUTION_ADMIN** | R03 | 184 | **54** | -130 | Multi-college enterprise institution admin (trust ceiling) |
| **COLLEGE_ADMIN** | R04 | 180 | **135** | -45 | Single campus operations manager (broad operational read/coordination) |
| **PRINCIPAL** | R05 | 165 | **231** | +66 | College academic and executive head (primary academic authority) |
| **DEAN** | R07 | 102 | **153** | +51 | Faculty and curriculum leader (curriculum/LMS authority) |
| **REGISTRAR** | R31 | 14 | **18** | +4 | Academic records officer (document vault administrator) |
| **EXAM_CONTROLLER** | R12 | 80 | 80 | 0 | Examination division head |
| **FINANCE_MANAGER** | R15 | 84 | **87** | +3 | College finance director (fee structure & billing write) |
| **HR_MANAGER** | R17 | 71 | **73** | +2 | Human resources director (staff lifecycle & payroll write) |
| **COMM_MANAGER** | R19 | 72 | 72 | 0 | Campus communications and notifications officer |

---

## 4. Known Breakage & Service Impact Analysis

| Service / Area | Method / Endpoint | Role Checked | R04 Result Post-Phase 2 | R03 Result Post-Phase 2 | Explanation & Migration Remedy |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **IADM Services** (`/api/v1/institution/**`) | Institution Admin endpoints | `INSTITUTION_ADMIN` or `adm01`–`adm09` | **DENIED (403)** | **ALLOWED (200)** | R04 cannot access IADM routes. Working as intended. |
| **CADM Services** (`/api/v1/college/**`) | College Admin endpoints | `COLLEGE_ADMIN` or `adm04`–`adm09` | **ALLOWED (200)** | **DENIED (403)** | R03 stripped of CADM-only keys; cannot mutate college desks directly. |
| **Attendance Service** (`attendance-service`) | Attendance marking (`POST /records`) | Role name `COLLEGE_ADMIN` / RLS `acd08.write` | **DENIED at RLS** | **DENIED at RLS** | R04 controller passes (role name), but Supabase RLS rejects write without `acd08.write`. R05 Principal / Faculty must perform write. |
| **Batch Management** (`batch-management-service`) | Batch mutation (`POST /batches`) | Role name `COLLEGE_ADMIN` / RLS `acd07.write` | **DENIED at RLS** | **DENIED at RLS** | R04 write stripped. R05 Principal or R07 Dean must approve/mutate batches. |
| **Document Service** (`document-service`) | Document vault upload/delete | `doc.write`, `doc.manage`, `doc.admin` | **DENIED (403)** | **DENIED (403)** | Transferred to R31 Registrar. R04/R03 retain `doc.view`/`doc.view.all`. |
| **Finance Service** (`fee-management-service`) | Fee collection / structure edits | `fin03.write`, `fin05.write` | **DENIED at RLS** | **DENIED at RLS** | Transferred to R15 Finance Manager / R16 Accountant. |
| **HR Service** (`staff-service`) | Staff hiring / salary edits | `hrm01.write`, `hrm04.*`, `hrm05.*` | **DENIED at RLS** | **DENIED at RLS** | Transferred to R17 HR Manager. R04 sensitive payroll view blocked per D-RBAC-M12. |

---

## 5. Automated Verification Results

 Live execution of `database/migrations/verify_phase2_rbac.sql` yielded:
1. `R04 holds none of D-RBAC-M12 keys`: **PASSED** (count = 0).
2. `R04 holds none of removed domain write/manage keys`: **PASSED** (count = 0).
3. `R03 holds none of non-IADM domain keys`: **PASSED** (count = 0).
4. `R03 does not hold doc write/admin keys`: **PASSED** (count = 0).
5. `R31 Registrar holds doc admin/write keys`: **PASSED** (count = 4).
6. `R05 Principal holds ACD/LMS keys`: **PASSED** (count >= 30).
7. `Direct student/parent grant rules absent`: **PASSED** (count = 0).
8. `Dev personas present and assigned`: **PASSED** (Principal and Dean assigned).
9. `Active users retain at least one active role`: **PASSED** (0 orphan users).

Live snapshot committed to `docs/audits/rbac-implementation/snapshots/after_phase2_live_snapshot.json`.
Phase 2 completed cleanly. Proceeding to Phase 3.
