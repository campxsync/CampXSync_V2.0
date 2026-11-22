# Part M: Security Findings Register

**Document:** `docs/audits/rbac-coverage/Part-M_findings-register.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Security Defect Inventory

**[CONFIRMED: Static code analysis, catalog review, and database trigger inspection]:**

| Finding ID | Severity | Location | What | Why It Matters | RBAC Decision / Section Touched |
| :--- | :--- | :--- | :--- | :--- | :--- |
| **SEC-RBAC-01** | **CRITICAL** | `frontend/src/lib/db.ts:42`<br>`frontend/src/app/api/college/faculty/route.ts:5` | Frontend server pool connects as PostgreSQL superuser `postgres`. `iam.is_privileged_session()` returns `true`. | Bypasses PostgreSQL Row-Level Security (`BYPASSRLS`) across all schemas and completely disables trigger `iam.guard_role_assignment` (self-grant block, scope checks, and `can_grant_role` checks are skipped). | Non-Negotiable Architecture Rules; Matrix §5; Part C5 |
| **SEC-RBAC-02** | **HIGH** | `iam.role_template_permissions:18`<br>`iam.role_permissions` | Wildcard permission `*` is assigned to role `TENANT_ADMIN` across all 49 DEV tenants. | Any user holding `TENANT_ADMIN` satisfies every permission check in the platform, including unassigned and administrative management actions. | Owner Decision IADM-D7; Matrix §2, §10 |
| **SEC-RBAC-03** | **HIGH** | `iam.role_grant_rules:5, 20, 27` | `COLLEGE_ADMIN` is permitted to grant `PRINCIPAL` (R05), `VICE_PRINCIPAL` (R06), and `DEAN` (R07). | Allows a college-level operator to grant superior academic roles, creating a privilege escalation path that violates the hierarchical ceiling. | Owner Decision D-RBAC-M10; Matrix §21.1 |
| **SEC-RBAC-04** | **HIGH** | `iam.role_template_permissions` for `AUDITOR` | `AUDITOR` (R28) is assigned `audit.admin`, `audit.write`, `wf.write`, `core.comment`, and 5 HR write keys (`hrm02.write.own`..`hrm09.write.own`). | Directly violates the independent read-only mandate for the Auditor role, allowing auditors to alter audit logs and operational workflows. | Matrix §8, §12, §21.2, §22 change 2 |
| **SEC-RBAC-05** | **MEDIUM** | `services/academic-services/batch-management-service/.../BatchController.java:474`<br>`services/attendance-services/.../AttendanceController.java:641` | Multiple Java controllers enforce access via coarse string comparisons on `X-User-Role` (`assertRole("COLLEGE_ADMIN")`). | Narrowing `COLLEGE_ADMIN` permissions in `iam.role_permissions` will NOT prevent R04 from mutating academic records until Java code evaluates permissions. | Owner Decision CADM-D3; Matrix §11.1 |
| **SEC-RBAC-06** | **MEDIUM** | `iam.role_template_permissions` for `COLLEGE_ADMIN` | `COLLEGE_ADMIN` holds permissions for Tax (`fin04`), Payroll (`fin06`), Performance (`hrm04`), and Payroll Fixation (`hrm05`). | Violates explicit owner decision removing College Admin from sensitive compensation, tax, and performance data. | Owner Decision D-RBAC-M12; Matrix §21.3 |
| **SEC-RBAC-07** | **MEDIUM** | `iam.role_grant_rules:72`<br>`iam.guard_role_assignment` | Single-signoff appointment of `INSTITUTION_ADMIN` (R03) by `TENANT_ADMIN`. | Lacks a two-person rule / multi-signature authorization check, allowing unilateral appointment of top-level tenant administrators. | Matrix §21.1 Guard 3 |
| **SEC-RBAC-08** | **MEDIUM** | `database/migrations/Scripts_V3.0/test_rbac.sql` | Production security DDL, templates, and triggers reside in a file labeled `test_rbac.sql`. | Creates confusion over whether the file is disposable test data or production schema, creating high drift risk. | Non-Negotiable Architecture Rules; Part C8 |
| **SEC-RBAC-09** | **LOW** | `iam.role_templates` rows for `ADMISSIONS_OFFICER` and `APPLICANT` | Swapped `catalogue_id` attributes (`ADMISSIONS_OFFICER` = R32, `APPLICANT` = R33). | Misaligns database template metadata with canonical Matrix v2.3 identifiers (where Applicant is R32 and Admissions Officer is R33). | Matrix §3, §21.5 |
| **SEC-RBAC-10** | **LOW** | Database schema `sys` (10 tables) | 10 tables in schema `sys` (e.g. `sys.migration_logs`, `sys.error_logs`) have Row-Level Security disabled. | Operational logs lack database-level tenant isolation if queried outside application filters. | Non-Negotiable Architecture Rules |
