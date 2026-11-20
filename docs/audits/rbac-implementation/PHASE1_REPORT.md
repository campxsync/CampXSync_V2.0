# Phase 1 Report: RBAC Database Foundation (DEV)

**Document:** `docs/audits/rbac-implementation/PHASE1_REPORT.md`  
**Execution Timestamp:** 2026-11-20T15:05:15+05:30  
**Target Environment:** Development Supabase PostgreSQL (`azulbaxszsqxymqmlwfe`) [CONFIRMED]  
**Migration Applied:** `database/migrations/20261120000100_rbac_phase1_database_foundation.sql` [CONFIRMED]  
**Rollback Script:** `database/migrations/20261120000100_rollback__rbac_phase1_database_foundation.sql` [CONFIRMED]  
**Verification Script:** `database/migrations/verify_phase1_rbac.sql` [CONFIRMED: ALL ASSERTIONS PASSED]  
**Snapshots:**  
- Before: `docs/audits/rbac-implementation/snapshots/before_phase1_live_snapshot.json` [CONFIRMED]  
- After: `docs/audits/rbac-implementation/snapshots/after_phase1_live_snapshot.json` [CONFIRMED]  

---

## Plain-Language Legend & Abbreviations

- **R03 Institution Admin**: Institute-level administrator governing IADM services.
- **R04 College Admin**: College/campus operational administrator governing CADM services.
- **R28 Auditor**: Read-only oversight and compliance inspection role.
- **R32 Applicant**: Candidate self-registering and submitting admission application.
- **R33 Admissions Officer**: Staff member processing application documents and verifying submissions.
- **R34 Department Staff**: Departmental operational staff for course sections and requests.
- **R35 Academic Records Officer**: Campus academic history and record maintenance staff (STM-08).
- **R36 Document Verifier**: Staff reviewing and verifying student uploaded documents (STM-06).
- **R37 External Verification Agency**: External human verifier inspecting assigned student credentials.
- **R38 Provisioning / IAM Operator**: Staff executing student account linking and R25/R26 role provisioning (STM-07).
- **R39 Faculty Advisor**: Faculty member assigned to student advising and mentorship.
- **TENANT_ADMIN**: Deprecated root tenant role holding wildcard `*` permissions; completely collapsed and eliminated.
- **RLS**: Row-Level Security in PostgreSQL enforcing tenant and data boundaries.

---

## 1. Summary of Actions Executed

### 1.1 Swapped Catalogue IDs Fixed
- **Pre-condition**: `iam.role_templates` had `ADMISSIONS_OFFICER` mapped to `R32` and `APPLICANT` mapped to `R33`. [CONFIRMED: `before_phase1_live_snapshot.json`]
- **Action**: Updated `APPLICANT` catalogue ID to `R32` and `ADMISSIONS_OFFICER` catalogue ID to `R33`. [CONFIRMED]
- **Post-condition**: Verified `APPLICANT = R32` and `ADMISSIONS_OFFICER = R33` in `iam.role_templates`. [CONFIRMED: `verify_phase1_rbac.sql` assertion 7]

### 1.2 Created Role Templates for R34–R39
- **Action**: Created 6 role templates in `iam.role_templates` with zero initial permissions:
  1. `DEPARTMENT_STAFF` (catalogue ID `R34`, kind `staff`)
  2. `ACADEMIC_RECORDS_OFFICER` (catalogue ID `R35`, kind `staff`)
  3. `DOCUMENT_VERIFIER` (catalogue ID `R36`, kind `staff`)
  4. `EXTERNAL_VERIFIER` (catalogue ID `R37`, kind `staff`)
  5. `IAM_OPERATOR` (catalogue ID `R38`, kind `staff`)
  6. `FACULTY_ADVISOR` (catalogue ID `R39`, kind `faculty`)
- **Tenant Replication**: Iterated across all 49 tenants present in `iam.roles` via `iam.seed_system_roles()`. [CONFIRMED]
- **Post-condition**: Every one of the 49 tenants now contains active role definitions for R34–R39. Total distinct role codes per tenant increased from 33 to 38 (TENANT_ADMIN removed, 6 added). [CONFIRMED: `after_phase1_live_snapshot.json`]

### 1.3 Grant Rules Aligned
- **a) Deleted Escalation Rules**: Deleted 4 illegal `COLLEGE_ADMIN` grant rules:
  - `COLLEGE_ADMIN -> COLLEGE_ADMIN`
  - `COLLEGE_ADMIN -> PRINCIPAL`
  - `COLLEGE_ADMIN -> VICE_PRINCIPAL`
  - `COLLEGE_ADMIN -> DEAN`
  [CONFIRMED: 0 rows remaining in `iam.role_grant_rules`]
- **b) Added R03 Delegation Rules**: Added 8 grant rules for `INSTITUTION_ADMIN`:
  - `INSTITUTION_ADMIN -> REGISTRAR` (R31)
  - `INSTITUTION_ADMIN -> ADMISSIONS_OFFICER` (R33)
  - `INSTITUTION_ADMIN -> DEPARTMENT_STAFF` (R34)
  - `INSTITUTION_ADMIN -> ACADEMIC_RECORDS_OFFICER` (R35)
  - `INSTITUTION_ADMIN -> DOCUMENT_VERIFIER` (R36)
  - `INSTITUTION_ADMIN -> EXTERNAL_VERIFIER` (R37)
  - `INSTITUTION_ADMIN -> IAM_OPERATOR` (R38)
  - `INSTITUTION_ADMIN -> FACULTY_ADVISOR` (R39)
  *(Notice: `APPLICANT` (R32) is excluded as applicants self-register through STM-02 admissions portal).* [CONFIRMED]
- **c) End-User Roles Retained**: Preserved all `STUDENT` and `PARENT` grant rows pending Phase 2 evaluation. [CONFIRMED]

### 1.4 Auditor Stripped of Write/Admin Permissions
- **Pre-condition**: `AUDITOR` template held 45 permissions including 9 write/manage/comment keys (`audit.admin`, `audit.write`, `wf.write`, `core.comment`, `hrm02.write.own`, `hrm03.write.own`, `hrm04.write.own`, `hrm07.write.own`, `hrm09.write.own`). [CONFIRMED]
- **Action**: Deleted these 9 write permissions from `iam.role_template_permissions` and from `iam.role_permissions` across all tenants. [CONFIRMED]
- **Post-condition**: `AUDITOR` now holds exactly 36 permissions, all of which are strictly read/view/audit (`*.view`, `*.view.all`, `audit.view`, `audit.view.all`, `rpt.view`). Zero write or admin permissions remain. [CONFIRMED: `verify_phase1_rbac.sql` assertion 6]

### 1.5 Tenant Admin Collapse (IADM-D7)
- **a) Verification of R03 Keys**: Verified that `INSTITUTION_ADMIN` already holds 184 permissions, covering every explicit permission needed for IADM scope (`adm01`–`adm09`, `core.*`, `cfg.*`, `wf.*`, `rpt.*`). [CONFIRMED]
- **b) Re-assignment of DEV Persona**: The single active `TENANT_ADMIN` assignment in DEV (user `ba77b8f7-9c92-498c-8f47-73d749969f52` in tenant `a0000000-0000-0000-0000-000000000001`) was assigned `INSTITUTION_ADMIN` (while retaining `COLLEGE_ADMIN`). [CONFIRMED]
- **c) Total Removal of TENANT_ADMIN**:
  - Removed wildcard permission (`*`) rows from `iam.role_template_permissions` (1 row) and `iam.role_permissions` (49 rows).
  - Removed `TENANT_ADMIN` role assignments (1 row).
  - Removed `TENANT_ADMIN` role rows across all 49 tenants in `iam.roles` (49 rows).
  - Removed `TENANT_ADMIN` from `iam.role_grant_rules` (29 rows).
  - Removed `TENANT_ADMIN` from `iam.role_templates` (1 row).
- **Post-condition**: `TENANT_ADMIN` is completely absent from all database tables; wildcard `*` is held by zero roles. [CONFIRMED: `verify_phase1_rbac.sql` assertions 1 & 2]

### 1.6 API Client 90-Day Expiry Rule
- **Evaluation**: Static inspection of `PostgresApiClientRepository.java:86-90` and `PostgresApiClientRepositoryTest.java:95-102` confirmed that `PostgresApiClientRepository` passes `null` for `expires_at` when unset, and unit tests create clients with null `expires_at`.
- **Decision**: Per prompt rule 1.6 ("add the 90-day expiry rule ONLY IF PostgresApiClientRepository always sets expires_at; otherwise skip and list it as a Phase 3 item"), the DB CHECK constraint was **SKIPPED** in Phase 1 to prevent breaking existing tests. Repository and tests will be updated in Phase 3.5, followed by applying the CHECK constraint. [CONFIRMED]

---

## 2. Before / After Metrics Comparison

| Metric | Before Snapshot | After Snapshot | Delta | Status / Verification |
|---|---|---|---|---|
| **Role Templates** (`iam.role_templates`) | 33 | 38 | +5 (-1 TENANT_ADMIN, +6 R34–R39) | PASS (`verify_phase1_rbac.sql`) |
| **Active Tenants in Roles** (`iam.roles`) | 49 | 49 | 0 (all 49 tenants retained) | PASS |
| **Distinct Roles per Tenant** | 33 | 38 | +5 (identical across all 49 tenants) | PASS |
| **Wildcard (`*`) in Templates** | 1 (`TENANT_ADMIN`) | 0 | -1 | PASS (0 wildcards remain) |
| **Wildcard (`*`) in Role Permissions** | 49 (1 per tenant) | 0 | -49 | PASS (0 wildcards remain) |
| **Auditor Permissions Count** | 45 | 36 | -9 (write/admin keys stripped) | PASS (read-only verified) |
| **Grant Rules Count** | 85 | 59 | -26 (-4 CA illegal, -29 TA, +8 IA) | PASS |
| **Users with Active Roles** | 8 | 8 | 0 (no user lost active access) | PASS |
| **Applicant Catalogue ID** | R33 | R32 | Fixed | PASS |
| **Admissions Officer Catalogue ID** | R32 | R33 | Fixed | PASS |

---

## 3. Automated Verification Results (`verify_phase1_rbac.sql`)

The automated verification suite was executed against DEV Supabase (`azulbaxszsqxymqmlwfe`):

1. **Wildcard `*` held by zero roles**: **PASS** (count: 0 in templates, 0 in role permissions).
2. **`TENANT_ADMIN` completely absent**: **PASS** (0 in templates, 0 in roles, 0 in grant rules, 0 in assignments).
3. **All 49 tenants have identical role counts**: **PASS** (min: 38, max: 38 across all 49 tenants).
4. **`COLLEGE_ADMIN` cannot grant R04, R05, R06, R07**: **PASS** (0 illegal grant rules found).
5. **`INSTITUTION_ADMIN` can grant R33–R39 except APPLICANT**: **PASS** (all 8 grant rules present).
6. **`AUDITOR` has zero write/manage/admin/comment keys**: **PASS** (0 write keys found; 36 read keys retained).
7. **Swapped catalogue IDs fixed**: **PASS** (`APPLICANT = R32`, `ADMISSIONS_OFFICER = R33`).
8. **Every user still has at least one active role**: **PASS** (0 orphan users; all 8 users remain mapped).

**Overall Phase 1 Result**: **100% PASS**. Ready to proceed to Phase 2 automatically.
