# Part L: Matrix Gaps & Discrepancy Register

**Document:** `docs/audits/rbac-coverage/Part-L_matrix-gaps.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Discrepancies Between Matrix v2.3 and Live System

**[CONFIRMED: Cross-comparison of Matrix v2.3 against DEV database and codebase]:**

| Item # | Governance Topic | Matrix v2.3 Prescription | Live System Reality | Classification Tag |
| :--- | :--- | :--- | :--- | :--- |
| **GAP-01** | **R32 / R33 Catalogue Identifiers** | R32 = `Applicant`<br>R33 = `Admissions Officer` | `iam.role_templates`: `APPLICANT` has `catalogue_id = 'R33'`, `ADMISSIONS_OFFICER` has `catalogue_id = 'R32'`. | **SYSTEM NEEDS UPDATE** |
| **GAP-02** | **Student Module Roles R34–R39** | R34 Department Staff, R35 Records Officer, R36 Document Verifier, R37 External Verifier, R38 IAM Operator, R39 Faculty Advisor defined. | None of these 6 roles exist in `iam.role_templates` or `iam.roles`. | **SYSTEM NEEDS UPDATE** |
| **GAP-03** | **System Admin (R02)** | R02 System Admin defined for platform technical operations. | Missing from `iam.role_templates` and `iam.roles`. Technical operations run via uncatalogued `service_role`. | **SYSTEM NEEDS UPDATE** |
| **GAP-04** | **API Client (R27) Role Entry** | Defined as canonical role R27 with 9 credential lifecycle rules. | Missing from `iam.role_templates`. Exists as machine schema `iam.api_clients` with 0 live records. | **SYSTEM NEEDS UPDATE** |
| **GAP-05** | **Auditor (R28) Read-Only Mandate** | R28 is strictly read-only (`V/AU/E`) on audit logs and all domains (§8, §21.2). | Live template assigns `audit.admin`, `audit.write`, `wf.write`, `core.comment`, and 5 HR write keys (`hrm02.write.own`..`hrm09.write.own`). | **SYSTEM NEEDS UPDATE** |
| **GAP-06** | **R04 Escalation Guard (§21.1)** | R04 College Admin may **not** assign R01–R07. | `iam.role_grant_rules` allows `COLLEGE_ADMIN` to grant `PRINCIPAL` (R05), `VICE_PRINCIPAL` (R06), and `DEAN` (R07). | **SYSTEM NEEDS UPDATE** |
| **GAP-07** | **Elimination of Tenant Admin Wildcard** | Owner decision IADM-D7: Collapse Tenant Admin into R03; eliminate wildcard `*`. | `TENANT_ADMIN` exists with wildcard `*` assigned across all 49 tenants. | **SYSTEM NEEDS UPDATE** |
| **GAP-08** | **Narrowing R04 from Tax & Payroll** | Owner decision D-RBAC-M12: R04 removed from Tax (FIN-04), Payroll (FIN-06), Performance (HRM-04), Fixation (HRM-05). | Live template assigns `fin04.view/write`, `fin06.view/write`, `hrm04.manage/review/view`, `hrm05.manage/view` to R04. | **SYSTEM NEEDS UPDATE** |
| **GAP-09** | **Domain Admin Ownership (ACD/LMS)** | Owner decision D-RBAC-M09: R07 Dean + R05 Principal own Academic & LMS modules. | College Admin holds `acd01.manage`..`acd09.manage`. Java controllers check `assertRole("COLLEGE_ADMIN")`. | **SYSTEM NEEDS UPDATE** |
| **GAP-10** | **Frontend Role Resolution Desks** | Matrix defines 39 distinct business roles. | `resolve-role.ts` only recognizes 5 desktop buckets. Remaining 34 roles fail closed with `no_role_assigned`. | **SYSTEM NEEDS UPDATE** |
| **GAP-11** | **Two-Person Rule for Institution Admin** | Matrix §21.1 Guard 3 requires dual-authorization to appoint or modify an R03. | Absent in code and DB. `TENANT_ADMIN` grants `INSTITUTION_ADMIN` unilaterally. | **SYSTEM NEEDS UPDATE** |
| **GAP-12** | **Dormant `adm03` Governance** | Matrix §14 & IADM-D5: `adm03` dormant outside IADM/CADM. | `adm03.manage` is on no role template; RLS blocks non-superuser deletions. | **MATRIX NEEDS UPDATE** |
