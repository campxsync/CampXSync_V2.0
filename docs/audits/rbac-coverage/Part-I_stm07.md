# Part I: Student Module Account Provisioning (STM-07)

**Document:** `docs/audits/rbac-coverage/Part-I_stm07.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Student (R25) & Guardian (R26) Provisioning Flow

**[CONFIRMED: Source code audit of `student-service`, `admissions-service`, and `institute-admin-service`]:**

The provisioning pipeline for student and guardian accounts connects admissions handover to the centralized IAM role assignment model:

```
[ STM-02 Admissions ] ──► [ STM-01 Student Service ] ──► [ STM-07 IAM Provisioning Port ] ──► [ iam.role_assignments ]
 (Handover Completed)       (Eligibility Validation)     (StudentRoleAssignmentService)       (R25 STUDENT Insert)
```

### Step-by-Step Provisioning Mechanism
1. **Admissions Handover (STM-02):**
   - File: `services/student-services/admissions-service/.../HandoverOrchestrationService.java:175`
   - Trigger: When an application is approved and enrolled, `AccountProvisioningPort` executes a fail-soft HTTP request to initialize login setup.
2. **Eligibility Verification (STM-01):**
   - File: `services/student-services/student-service/.../StudentDomainService.java:1320, 1492`
   - Enforcement: `validateEligibilityForProvisioning()` requires that the student profile exists, is `ACTIVE`, and has verified identity attributes.
3. **Execution via IAM (STM-07):**
   - File: `services/administration-services/institute-admin-service/.../StudentRoleAssignmentService.java:45-75`
   - Class: `StudentRoleAssignmentService.assignStudentRole(ctx, tenantId, studentUserId)`
   - Database Mutation: Inserts a row into `iam.role_assignments` binding `user_id` to the `STUDENT` (R25) role ID for the tenant.

### Security Boundaries & Escalation Guards
- **Can this path assign other roles?**
  **NO [CONFIRMED].** `StudentRoleAssignmentService.java` hardcodes the role lookup to `code = 'STUDENT'`. It accepts only a student entity ID and cannot be parameterized to grant administrative or faculty roles.
- **Who may trigger provisioning today?**
  `StudentDomainService.java:2103` enforces that the caller must hold `R01`, `R02`, `R03`, or have an active recorded delegation for permission `STM-07.ACCOUNT.A`.
- **Target Role Catalogue Gap:** Matrix §21.5 defines `R38 Provisioning / IAM Operator` as the dedicated operational role. In the live database, `R38` does not exist as an `iam.role_templates` entity.

---

## 2. Guardian (R26) Linking & Access

**[CONFIRMED: Audit of `guardian-service` (:8097) and schema `core`]:**

1. **Relationship Verification:**
   - Handled by `guardian-service` (STM-05).
   - Requires verified relationship documentation and student/parent consent.
2. **Role Assignment Status:**
   - Role `PARENT` exists in `iam.roles` (holding 10 permissions in template).
   - **Holder Count:** Currently **0 assigned users** in DEV `iam.role_assignments`.
   - Guardians cannot access portal features until relationship verification and account link are complete.

---

## 3. Applicant (R32) & External Verifier (R37) Posture

**[CONFIRMED: Cross-check between codebase, DEV database, and Matrix v2.3]:**

### Where Applicants Live Today
- **Application Records:** Applicants live inside `admissions-service` schema `adm.admissions_applications` and person identity records.
- **Identity & Role:** Role `APPLICANT` exists in `iam.roles` (with catalogue ID `R33` in database, though Matrix v2.3 designates it as `R32`).
- **Account State:** **0 active assignments** in `iam.role_assignments`. Pre-enrolment applicants interact via self-service draft application tokens rather than fully provisioned enterprise accounts.

### External Document Verifiers
- **Matrix Posture:** Matrix §15 defines `R37 External Verification Agency` (assigned documents only, no broad search, export, or cross-tenant visibility).
- **Live Database State:** `R37` is **MISSING** from `iam.role_templates`.
- **Operational Reality:** External verification currently relies on ad-hoc staff accounts or manual verification out-of-band by Registrar staff.
