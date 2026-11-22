# Part K: Impact Preview & Migration Readiness

**Document:** `docs/audits/rbac-coverage/Part-K_impact.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. User Role Distribution in DEV Database

**[CONFIRMED: SQL query on `iam.role_assignments` and `plat.platform_admins`]:**

| Role Code | Canonical Name | Live User Count | Percentage of Total Users |
| :--- | :--- | :--- | :--- |
| `COLLEGE_ADMIN` | College Admin (R04) | 3 | 33.3% |
| `FACULTY` | Faculty (R10) | 2 | 22.2% |
| `STUDENT` | Student (R25) | 2 | 22.2% |
| `TENANT_ADMIN` | Tenant Admin (Wildcard) | 1 | 11.1% |
| `REGISTRAR` | Registrar (R31) | 1 | 11.1% |
| **All Other 28 Roles** | Specialized Domain Roles | **0** | 0.0% |

- **Users Holding Multiple Roles:** Exactly **1 user** (`ba77b8f7-ab32-46bd-85d2-5aade3526880`), holding `COLLEGE_ADMIN` and `TENANT_ADMIN`.
- **Total Assigned Profiles:** 8 unique user identities across 9 role assignment records.

---

## 2. Blast Radius: Narrowing R03 & R04 to IADM / CADM Boundaries

**[CONFIRMED: Cross-analysis between `Part-B_permission-map.csv` and microservice endpoint catalogs]:**

### Keys to be Revoked from `COLLEGE_ADMIN` (R04)
Under Matrix v2.3, **148 permission keys** are marked for removal from `COLLEGE_ADMIN`:
- **Academic Services (`ACD-01`…`ACD-10`):** 35 management and write keys (e.g. `acd01.manage`, `acd03.write`, `acd07.manage`).
- **Finance Services (`FIN-01`…`FIN-10`):** 26 keys, including full revocation from Tax (`fin04`) and Payroll (`fin06`).
- **Human Resources (`HRM-01`…`HRM-09`):** 27 keys, including full revocation from Performance (`hrm04`) and Payroll Fixation (`hrm05`).
- **Communication (`CAM-01`…`CAM-08`):** 41 broad write and alert broadcast keys.
- **Administration & Shared:** 19 keys outside the CADM-01…CADM-09 boundary.

### Broken Callers & Operational Impact
1. **Academic Management Desks:**
   - **Impact:** College Admin currently configures programs, curriculums, and course allocations. If removed, these UI desks will fail closed unless re-assigned to **R07 Dean** and **R05 Principal**.
   - **Mitigation:** Dean and Principal test personas must be seeded and assigned before revoking ACD keys from R04.
2. **Payroll & Tax Ingress:**
   - **Impact:** Any College Admin currently viewing payroll or tax reports will be denied.
   - **Mitigation:** Seamless per Matrix D-RBAC-M12; access shifts strictly to Finance Manager (R15) and Accountant (R16).
3. **Java Endpoint Coarse-Check Decoupling:**
   - **Finding:** Because Java controllers (e.g., `batch-management-service:474`) check `assertRole(userRole, "COLLEGE_ADMIN")`, calls to those REST endpoints will NOT fail until Java code is updated to check permission tokens.

---

## 3. Transition of Current Tenant Admin Holder

**[CONFIRMED: Analysis of user `ba77b8f7-ab32-46bd-85d2-5aade3526880`]:**

1. **Current State:** User holds `TENANT_ADMIN` (with wildcard `*`) and `COLLEGE_ADMIN`.
2. **Matrix Transition (Decision IADM-D7):**
   - Wildcard `*` is eliminated.
   - `TENANT_ADMIN` is collapsed into `INSTITUTION_ADMIN` (R03).
3. **Outcome:**
   - The user transitions from `TENANT_ADMIN` to `INSTITUTION_ADMIN` (R03).
   - Because the user also holds `COLLEGE_ADMIN` (R04), they retain administrative authority over both IADM (Institute) and CADM (College) operations.
   - **Zero Effective Role Hazard:** **SAFE**. The user will **NOT** end up with zero effective roles.

---

## 4. Required Schema Enhancements for Future Migration

**[CONFIRMED: Catalog of pending DDL requirements — analysis only, no design]:**

| Architectural Requirement | Target Table | Required DDL / Schema Modification |
| :--- | :--- | :--- |
| **R32 / R33 Identifier Swap** | `iam.role_templates` | Update `catalogue_id` for `APPLICANT` to `'R32'` and `ADMISSIONS_OFFICER` to `'R33'`. |
| **New Roles R34–R39** | `iam.role_templates` / `iam.roles` | Insert template definitions for `R34 Department Staff`, `R35 Academic Records Officer`, `R36 Document Verifier`, `R37 External Verification Agency`, `R38 Provisioning / IAM Operator`, `R39 Faculty Advisor`. |
| **Role Grant Rules Alignment** | `iam.role_grant_rules` | Delete rows allowing `COLLEGE_ADMIN` to grant `PRINCIPAL`, `VICE_PRINCIPAL`, `DEAN`. Add grant rules for `R01`, `R02`, `R03` over R32–R39. |
| **API Client 90-Day Ceiling** | `iam.api_clients` | Add CHECK constraint: `CHECK (expires_at <= created_at + interval '90 days')`. |
| **Dual-Approval Institution Admin** | `iam.user_role_assignments` | Add multi-signature / secondary approval workflow columns or tracking table for R03 appointment. |
