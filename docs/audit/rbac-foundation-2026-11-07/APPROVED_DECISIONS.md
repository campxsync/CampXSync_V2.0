# Approved RBAC Decisions — 2026-11-07

**Approver:** CampXSync (Product Manager)  
**Approval statement:** “I would go with your proposal, go ahead and do the rest.”  
**Effective contract:** `PROPOSED_ENTERPRISE_RBAC_MATRIX.md` as approved (this file records the decisions).  
**Environment for DDL:** development only `azulbaxszsqxymqmlwfe`.

| Decision ID | Disposition |
| :--- | :--- |
| D-RBAC-01 | **Approved:** Add catalogue roles **R31 Registrar**, **R32 Admissions Officer**, **R33 Applicant**. |
| D-RBAC-02 | **Approved:** Introduce `stu01`–`stu10` permission families; STM-03 codes listed in the approved matrix are authoritative for enrolment. |
| D-RBAC-03 | **Approved:** No Advisor/Faculty **Approve** on STM-03. Faculty remains `stu03.roster.view` (assigned section) only. |
| D-RBAC-04 | **Approved:** Ratify R31/R32/R33; STM-02 evaluators may keep those codes as catalogue addendum (aliases mapped). |
| D-RBAC-05 | **Approved defaults:** keep matrix deny principles; remaining open workflow details (guardian defaults, service-request chains, etc.) stay fail-closed until a later signed addendum. |
| D-RBAC-06 | **Approved interim:** EXTRA Scripts roles (`ACADEMIC_ADMIN`, `EMPLOYEE`, `TENANT_ADMIN`) remain for legacy ACD/HRM; no new STM grants via EXTRA. Full retirement deferred. |
| D-RBAC-07 | **Approved:** Apply STM-03 enrolment DDL + RBAC seed to **`azulbaxszsqxymqmlwfe` only**. Production remains forbidden. |

## STM-03 grants (approved)

| Role | Permissions |
| :--- | :--- |
| R25 / STUDENT | `stu03.enrolment.view.own`, `stu03.register.execute`, `stu03.waitlist.join` |
| R10 / FACULTY | `stu03.roster.view` (assigned section); **deny** approve |
| R31 / REGISTRAR | `stu03.term.cfg`, `stu03.window.cfg`, `stu03.enrolment.create`, `stu03.enrolment.approve`, `stu03.waitlist.promote`, plus review/view staff paths |
| R07 / DEAN, R08 / HOD | `stu03.section.allocate` (scoped) |
| R32 | Admissions officer (STM-02); not STM-03 CFG by default |
| R33 | Applicant (STM-02); not STM-03 student APIs |

Implementation must fail closed for any permission not listed above.
