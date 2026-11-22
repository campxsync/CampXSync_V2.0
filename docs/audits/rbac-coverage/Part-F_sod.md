# Part F: Segregation of Duties (SoD) Analysis

**Document:** `docs/audits/rbac-coverage/Part-F_sod.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Conflicting Role Combinations in DEV Database

**[CONFIRMED: SQL query `SELECT user_id, count(DISTINCT role_id), string_agg(r.code, ', ') FROM iam.role_assignments ra JOIN iam.roles r ON r.id = ra.role_id WHERE ra.deleted_at IS NULL GROUP BY user_id HAVING count(DISTINCT role_id) > 1` on DEV `azulbaxszsqxymqmlwfe`]:**

Across all user profiles in the DEV environment, exactly **1 user** holds more than one role assignment:
- **User ID:** `ba77b8f7-ab32-46bd-85d2-5aade3526880` (`dev.admin@campxsync.test`)
- **Roles Held:** `COLLEGE_ADMIN` (R04) and `TENANT_ADMIN` (Bootstrap Wildcard).
- **All Other Users:** Hold exactly 0 or 1 role assignment.

### Conflicting Role Holder Counts Today
| Domain Conflict Area | Conflicting Roles Defined in Matrix | Live Users Holding Conflicting Roles |
| :--- | :--- | :--- |
| **Examination** | Valuator (R14) + Marks Approver (R12/R13) + Result Publisher (R12/R13) | **0 users** |
| **Finance** | Fee/Expense Creator (R16) + Approver (R15/R05) | **0 users** |
| **HR / Payroll** | Payroll Fixation (R17) + Payroll Execution (R15) | **0 users** |
| **HR / Leave** | Leave Requester (R10/Staff) + Leave Approver (R08/R05) | **0 users** |
| **Access Governance** | Role Requester + Role Grantor | **1 user** (Holds both College Admin and Tenant Admin) |

---

## 2. Enforcement Status in Code and Database

**[CONFIRMED: Source code inspection of microservices and PostgreSQL triggers]:**

| Conflict Area | Enforced in Live Code? | Enforced in Live DB? | Evidence / File Reference | Status Tag |
| :--- | :--- | :--- | :--- | :--- |
| **Examination SoD**<br>Valuator ≠ Marks Approver ≠ Result Publisher | **NO** | **NO** | No checks in `exm` schemas or Java controllers prevent multi-role or self-approval in exam valuation. | **NOT FOUND** |
| **Finance SoD**<br>Expense/Payroll Creator ≠ Approver | **NO** | **NO** | No checks in `fin` schemas or Java controllers enforce separation between payroll fixation and execution. | **NOT FOUND** |
| **HR SoD**<br>Requester ≠ Approver for Leave / Performance | **NO** | **NO** | No checks in `hrm` schemas or Java controllers enforce self-leave escalation to Principal. | **NOT FOUND** |
| **Admissions SoD (STM-02)**<br>Applicant ≠ Approver; Reviewer ≠ Approver | **YES** | **NO** | `AdmissionsPermissionEvaluator.java:112` blocks applicant self-approval; `ReviewAndDecisionService.java:65` blocks reviewer approving own review. | **CONFIRMED (Code Only)** |
| **Profile Corrections SoD (STM-01)**<br>Requester ≠ Approver | **YES** | **NO** | `StudentDomainService.java:520` throws `StudentSecurityViolationException` if requester approves own correction. | **CONFIRMED (Code Only)** |
| **Document Verification SoD (STM-06)**<br>Uploader ≠ Verifier | **YES** | **NO** | Document service checks uploader UUID against verifier session context. | **CONFIRMED (Code Only)** |
| **Access Self-Grant (IAM)**<br>User cannot grant roles to self | **NO** | **YES** | Trigger `iam.guard_role_assignment` line 176 blocks self-modification (`r.user_id = auth.uid()`). Bypassed if privileged session. | **CONFIRMED (DB Trigger)** |
| **Access Multi-Signature (IAM)**<br>Two-person rule for Institution Admin | **NO** | **NO** | Unilateral grant allowed for `TENANT_ADMIN`. No dual-approval check. | **NOT FOUND** |
