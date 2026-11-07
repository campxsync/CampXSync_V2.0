# CampXSync — Enterprise RBAC Foundation Audit Report

**Audit ID:** `RBAC-FOUNDATION-2026-11-07`  
**Audit date:** 2026-11-07  
**Branch:** `feature/java-backend-integration`  
**HEAD:** `44d79099c0c37c277543c10dc9e99fe0ac4ba9e7`  
**Working tree:** existing STM-03 and handover files preserved; no reset, commit, push, migration apply, or permission inventing performed  
**Status:** **AUDIT COMPLETE — AUTHORIZATION FOUNDATION NOT APPROVED**  
**Disposition:** STOP at approval gate. STM-04–STM-10 and STM-03 administrative workflows remain frozen until decisions are approved.

---

## 1. Executive summary

CampXSync already has a documented hybrid RBAC + ABAC design, a 30-role enterprise catalogue (R01–R30), vocabulary codes (V/C/U/A/RV/…), IAM SQL helpers (`iam.has_permission`, `iam.perm_all`, `iam.perm_department_ids`), gateway JWT/HMAC trust boundaries, and substantial domain RLS. That foundation is **not complete enough** to authorize STM-03 through STM-10 delivery.

The dominant defect is a **role-catalogue gap**: the Student Module matrix repeatedly names **Registrar**, **Department Staff**, **Advisor**, **authorized enrolment approver**, and similar operational personas that are **not members of R01–R30**. The validated STM backlog therefore maps most stories to incomplete canonical codes (often only `R25` and `R10`) while retaining Registrar/Department Staff language in the matrix posture. Implementation either hardcodes invented role strings (STM-02) or returns `POLICY_UNASSIGNED` (STM-03 admin paths). Neither behavior is an approved authorization contract.

**Verdict:** Do not treat the current RBAC model as complete. Approve the decision register first.

---

## 2. Audit scope

| In scope | Out of scope |
| :--- | :--- |
| Enterprise student-module RBAC matrix | Implementing proposed grants |
| Validated STM-03–STM-10 backlog | Applying DB migrations / RLS edits |
| Scripts_V3.0 RBAC CSVs and IAM SQL | Production / staging DB writes |
| API Gateway identity and HMAC path | STM-04–STM-10 feature delivery |
| Java authorization patterns (STM-01/02/03, sample ADM/ACD) | Redesigning architecture |
| Documented RLS / forensic audits (read-only) | Committing or pushing |

---

## 3. Source documents inspected

### Authoritative (required)

| Source | Path | Status |
| :--- | :--- | :--- |
| Enterprise RBAC matrix | `docs/architecture/Security/CampXSync_Enterprise_RBAC_Combined_Matrix_Student_Module.docx` | Present. Text dump also at `docs/audit/RBAC_MATRIX_STUDENT_DUMP.txt`. Duplicate path under `D:\CampXSync\Documentation\OverAllArchitecture\`. |
| Validated STM backlog | `D:\CampXSync\Documentation\StudentModule\UserStories\CampXSync_STM-03_to_STM-10_Implementation_Backlog_RBAC_Validated.csv` | Present (220 rows). **Used as authoritative backlog.** |
| Older aligned CSV | `...\CampXSync_Student_Module_STM-03_to_STM-10_RBAC_Aligned.csv` | Present. **Not substituted** for decisions. |

### Repository governance / architecture

- `AGENTS.md`
- `.agent/rules/campxsync-wave-control.md`
- `docs/project-control/PROGRESS.md`
- `docs/project-control/WAVE_IMPLEMENTATION_PLAN_W4-W11.md`
- `prompts/NEXT_ANTIGRAVITY_TASK.md`
- `docs/reports/wave-10/COMPLETION_REPORT.md`
- `docs/architecture/STM-03_ENROLLMENT_SPECIFICATION.md`
- `STM-02_RBAC_ABAC_VALIDATION.md`
- `docs/audit/CAMPXSYNC_SECURITY_AND_RLS_AUDIT.md`
- `docs/audit/db-forensic-audit-2026-10-26/RLS_AND_PERMISSIONS_AUDIT.md`

### Implementation / schema evidence

- `database/migrations/Scripts_V3.0/rbac_role_permissions.csv` (2,138 mappings; 468 distinct permission codes)
- `database/migrations/Scripts_V3.0/rbac_table_permissions.csv`
- `database/migrations/Scripts_V3.0/40_functions.sql` (`iam.has_permission`, `iam.perm_all`, `iam.perm_department_ids`, …)
- `api-gateway` JWT / `IdentityResolver` / HMAC path
- `services/student-services/admissions-service/.../AdmissionsPermissionEvaluator.java`
- `services/student-services/enrollment-service/.../EnrollmentDomainService.java`
- STM-01 ownership / document RLS migrations under `database/migrations/20261105*`

### Source conflicts explicitly recorded

1. **Matrix STM ownership table** names Registrar as principal for STM-01–STM-07, STM-09. **R01–R30 catalogue has no Registrar.**
2. **Validated CSV** stamps STM-03 stories with canonical codes `R10; R25` while matrix posture text still requires Registrar / Department Staff C/U/RV/A.
3. **Scripts_V3.0 role CSV** has no `REGISTRAR` role and no `stu*` permission codes; it also omits catalogue rows for R01, R02, R27, R30 and adds EXTRA roles (`ACADEMIC_ADMIN`, `EMPLOYEE`, `TENANT_ADMIN`).
4. **STM-02 Java** uses invented codes `R31`/`R32`/`R33` and string aliases (`ADMISSIONS_OFFICER`, …) that are not in R01–R30.
5. Historical `docs/audit/CAMPXSYNC_SECURITY_AND_RLS_AUDIT.md` (2026-10-19) describes older staging/RLS issues; later waves remediating FORCE RLS and gateway cutover must not be confused with an approved STM role catalogue.

---

## 4. Current RBAC architecture (observed)

```
Browser / Next.js
    → API Gateway (8080): Supabase JWT validate + IdentityResolver + HMAC to services
        → Java domain service: business rules + role/permission checks (string-set or evaluator)
            → PostgreSQL: SET LOCAL ROLE authenticated + request.jwt.claims + RLS
```

Effective access, per the enterprise matrix, is intended as:

`Role permission + hierarchy + tenant/institution/campus/department/course/section/resource ownership + ABAC + time/delegation + SoD`.

Observed reality for STM:

- Gateway authenticates and injects identity headers; it does **not** evaluate STM fine-grained permission codes.
- Java services use **hardcoded role name sets**, not `stu*.view` / `stu*.write` permission codes (those codes do not exist in Scripts_V3.0).
- PostgreSQL helpers exist and are used heavily for ACD/core RLS patterns; STM admissions tables largely use **tenant_id isolation**, with student ownership policies added later for selected tables.
- Intelligence Layer is outbox/JSON contract based and must not become an auth bypass.

---

## 5. Canonical role catalogue (R01–R30)

Explicitly documented in the enterprise matrix:

| Code | Role | Primary scope | In Scripts_V3.0 `rbac_role_permissions.csv`? |
| :---: | :--- | :--- | :---: |
| R01 | Super Admin | Platform | Missing |
| R02 | System Admin | Platform | Missing |
| R03 | Institution Admin | Institution | Yes (`INSTITUTION_ADMIN`) |
| R04 | College Admin | College/Campus | Yes |
| R05 | Principal | Institution | Yes |
| R06 | Vice Principal | Institution | Yes |
| R07 | Dean | Academic/Institution | Yes |
| R08 | HOD | Department | Yes |
| R09 | Program Coordinator | Program/Course | Yes |
| R10 | Faculty | Assigned subjects/courses | Yes |
| R11 | Class/Section Coordinator | Assigned section | Yes |
| R12 | Controller of Examinations | Examination | Yes |
| R13 | Exam Cell Officer | Examination | Yes |
| R14 | Valuator/Examiner | Assigned evaluation | Yes |
| R15 | Finance Manager | Finance | Yes |
| R16 | Finance Officer/Accountant | Finance | Yes |
| R17 | HR Manager | HR | Yes |
| R18 | HR Officer | HR | Yes |
| R19 | Communication Manager | Communication | Yes |
| R20 | Placement Officer | Placement | Yes |
| R21 | Librarian | Library | Yes |
| R22 | Hostel Warden | Hostel | Yes |
| R23 | Transport Manager | Transport | Yes |
| R24 | Facility Manager | Facilities | Yes |
| R25 | Student | Own academic/financial data | Yes |
| R26 | Parent/Guardian | Linked student | Yes |
| R27 | API Client | External integration | Missing |
| R28 | Auditor | Read/audit | Yes |
| R29 | Reporting/Analytics User | Reports | Yes |
| R30 | Guest | Restricted read | Missing |

**Prose roles used by STM matrix but not in R01–R30 (not silently merged):**

- Registrar / Admissions / Admissions Staff  
- Department Staff  
- Authorized enrolment / admission / document / relationship verifier or approver  
- Faculty / Advisor (Advisor is not a separate catalogue code)  
- External Verification Agency  
- Consent/access policy owner  
- Provisioning service / IAM operator  
- Applicant (distinct from R25 Student; STM-02 already flagged this)

**Extra roles in Scripts_V3.0 CSV (not R01–R30):** `TENANT_ADMIN`, `ACADEMIC_ADMIN`, `EMPLOYEE`.

**Counts:** 30 canonical roles documented; 29 named roles in Scripts_V3.0 CSV; 0 Registrar rows.

---

## 6. Permission catalogue

### Enterprise vocabulary (action codes)

V, C, U, D, A, R, P, X, E, I, AS, RV, AU, CFG, ADM — defined in the matrix.

### Scripts_V3.0 coded permissions

- **468** distinct permission strings (e.g. `acd01.view`, `fin02.execute`, `core.write`).
- Prefix coverage: ACD, ADM, CAM, DOC, EXM, FIN, HRM, LMS, CFG, CORE, AUDIT, RPT, WF, recruit.
- **Zero** `stu*` / enrolment permission codes. STM-03–STM-10 have no coded permission namespace in the seeded RBAC CSV.

### Validated backlog posture

Most STM stories carry matrix posture text (Student V/X; Registrar C/U/RV/A; Faculty V roster) but canonical codes collapse to a subset of R01–R30. Verdict distribution on 220 rows:

| Verdict | Count |
| :--- | ---: |
| MATRIX-DEFINED; ROLE-CATALOGUE GAP | 168 |
| MATRIX-ALIGNED; VERIFY SCOPE/WORKFLOW | 30 |
| MATRIX-ALIGNED CONTROL; ROLE-CATALOGUE GAPS PRESERVED | 15 |
| POLICY DECISION REQUIRED | 7 |

---

## 7. Role–permission–scope analysis (STM-03 focus)

| Operation | Matrix posture | Canonical CSV codes | Classification | Implementation today |
| :--- | :--- | :--- | :--- | :--- |
| Student view own enrolment | Student V own | R25 | Explicitly authorized (matrix + R25) | Allowed for STUDENT/R25 own record |
| Student select courses / waitlist join | Student X | R25 | Explicitly authorized | Allowed with eligibility/capacity checks |
| Faculty view assigned roster | Faculty/Advisor V assigned | R10 | Explicitly authorized (V only) | Allowed for FACULTY/R10 assigned section |
| Faculty approve enrolment | Not granted A in STM-03 matrix table | R10 only | Explicitly **not** authorized as A | Denied / not implemented |
| Configure terms / windows | Registrar/Department Staff C/U; CFG flagged as gap | R10; R25 only | **Unassigned** to catalogue | `POLICY_UNASSIGNED` |
| Open handover / admin create enrolment | Registrar C | Unassigned | **Unassigned** | `POLICY_UNASSIGNED` |
| Update enrolment status (A) | Registrar A when delegated | Unassigned | Ambiguous + unassigned | `POLICY_UNASSIGNED` |
| Allocate section (HOD/Dean) | HOD/Dean scoped in ownership table | Unassigned in STM-03 canonical codes | Ambiguous (R07/R08 exist but not coded on STM-03 rows) | `POLICY_UNASSIGNED` |
| Promote waitlist | Registrar/Department Staff implied | Unassigned | Unassigned | `POLICY_UNASSIGNED` |

**Rule applied:** unassigned ≠ grant. Missing role ≠ invent replacement.

---

## 8. Administrative enrolment workflow findings

The enrolment lifecycle cannot be operated end-to-end under R01–R30 alone without an approved mapping for Registrar (or a designated R04/R05/R08/R07 substitute with explicit CFG/A scopes).

Blocked or fail-closed without decision:

1. Academic year/term configuration  
2. Registration window configuration  
3. Enrolment opening from STM-02 handover (staff path)  
4. Status approve/reject/suspend  
5. Administrative section reallocation  
6. Waitlist promotion  
7. Administrative override / cancellation  

Runnable under current approved-looking matrix rows without inventing roles:

1. Student course selection within window  
2. Prerequisite / capacity / duplicate checks  
3. Student waitlist join when full  
4. Faculty assigned roster view  

---

## 9. Cross-domain findings

| Domain | Observation |
| :--- | :--- |
| ACD | Rich coded permissions (`acd01`–`acd10`) and department-scoped RLS patterns. STM must not duplicate ACD ownership. |
| LMS / EXM / FIN / HRM / CAM | Own coded permission prefixes exist; STM portal/history/requests must re-authorize downstream, not cache authority. |
| Shared IAM | SQL permission helpers exist; STM Java does not call them. |
| Gateway | Trust boundary only; not STM permission engine. |
| Intelligence | Outbox contracts; no role grant. |
| STM-01–10 matrix | Same Registrar gap repeats across services. |

Inconsistent authorization for “same business idea”: e.g. section/batch writes are ACD-07 coded permissions for HOD/College Admin, while STM-03 section allocation is prose Registrar/HOD without catalogue mapping.

---

## 10. Java authorization implementation findings

| Finding | Severity | Evidence |
| :--- | :---: | :--- |
| STM-02 invents R31/R32/R33 and admissions role aliases | Critical | `AdmissionsPermissionEvaluator.java` |
| STM-03 admin paths fail closed with `POLICY_UNASSIGNED` | High (correct fail-closed; blocks delivery) | `EnrollmentDomainService.requireStaffWrite` |
| Hardcoded role string equality across services (`REGISTRAR`, `COLLEGE_ADMIN`, …) | High | Controllers/domain services |
| Gateway `InMemoryIdentityResolver` blocked outside test mode | Positive control | `GatewayConfig.getIdentityResolver` |
| HMAC missing secret skips verify in some services | High if deployed without secret | `GatewayHmacVerifier` patterns |
| Student ownership checks present for own-record paths | Medium positive | STM-01 / STM-03 student paths |
| No central PermissionService shared by STM modules | High | Architecture gap |

---

## 11. Supabase RLS alignment (read-only)

| Layer | STM observation |
| :--- | :--- |
| Tenant isolation | Present on STM-02 admissions tables and drafted STM-03 SQL (unapplied). |
| Own-record | Applied for selected STM-01 student/document policies via `iam.has_permission(...)` OR ownership predicates. |
| Department / section scope | Stronger in ACD RLS; **not** equivalently encoded for STM-03 roster/assignment in DB. |
| FORCE RLS | Verified historically on selected Tier-1 tables; not a substitute for missing Registrar grants. |
| Permission codes in RLS | ACD/core use `acd*`/`core*` codes; STM has no `stu*` codes to bind. |

Mismatch: application may intend Faculty section scope while DB policy is only tenant-scoped → must be closed after role/permission approval, not by silent RLS inventing during this audit.

---

## 12. Authorization test coverage findings

| Area | Evidence status |
| :--- | :--- |
| STM-03 student allow / faculty roster / POLICY_UNASSIGNED denials | Unit tests exist (`EnrollmentDomainServiceTest` 16/16) |
| STM-03 live RLS / tenant isolation DB | **Not verified** (migration unapplied) |
| Per-role matrix coverage for R01–R30 on STM | Missing |
| Unassigned permission negative suite as governance artifact | Partial (POLICY_UNASSIGNED) |
| Gateway STM-03 route map | Unit route test exists |
| Cross-tenant IDOR for enrolment | Unit-level not-found for wrong tenant; DB-level pending |
| Advisor approval positive path | Intentionally absent pending FLAG-004 |

Passing unit tests do **not** prove the RBAC model is complete.

---

## 13. Security findings by severity

| Severity | Count | Themes |
| :---: | ---: | :--- |
| Critical | 3 | Role-catalogue gap for STM admin; invented STM-02 role codes; no `stu*` permission namespace |
| High | 5 | Hardcoded role checks; HMAC secret skip risk; RLS/app scope mismatch potential; CFG gaps; fail-open if someone bypasses POLICY_UNASSIGNED |
| Medium | 4 | Alias inconsistency (R25 vs STUDENT); Advisor dual label; missing R01/R02/R27/R30 in Scripts CSV; incomplete negative suites |
| Low | 2 | Documentation drift between older security audit and Wave 10 remediations; duplicate matrix file locations |

---

## 14. STM policy flag dispositions

### STM-RBAC-FLAG-001 — Roles used by STM matrix not in R01–R30

- **Genuine gap:** Yes.  
- **Registrar in enterprise catalogue?** No.  
- **Required admin permissions defined as coded `stu*`?** No.  
- **Matrix still names Registrar as principal for STM-03?** Yes (ownership + action tables).  
- **Decision required:** Map each STM admin persona to (a) a new catalogue role code, or (b) an existing R0x with explicit scoped permissions, or (c) an explicit deny with alternate workflow.  
- **Until approved:** fail closed (current STM-03 behavior is correct).

### STM-RBAC-FLAG-004 — Advisor approval removed

- **Advisor as distinct R0x?** No. Matrix uses “Faculty / Advisor” label under R10 semantics.  
- **STM-03 action table grant to Faculty?** `V assigned roster` only — **no A**.  
- **Is advisor approval part of authoritative STM-03 permission table?** Not as Approve.  
- **Was removal inventing a deny against an explicit A grant?** No evidence of Faculty A in the STM-03 matrix table.  
- **Decision required:** Confirm whether any enrolment workflow step needs an Advisor **A/RV** beyond Faculty V roster; if yes, define role code, scope, and SoD; if no, document that student X + Registrar A (once mapped) is the approved path.

---

## 15. Recommended resolution approach (proposal only)

1. Approve a **Student Module operational role addendum** (or map Registrar → existing R0x) with codes, scopes, and SoD.  
2. Introduce a **`stu03`–`stu10` permission namespace** aligned to matrix action codes.  
3. Seed `iam.roles` / `iam.permissions` / `iam.role_permissions` consistently with R01–R30 + approved addendum.  
4. Replace hardcoded Java role sets with permission checks backed by IAM grants + ABAC scope.  
5. Align RLS predicates to the same permission/scope model.  
6. Execute the authorization validation strategy before unfreezing STM-04+.

---

## 16. Approval gate

This report does **not** approve any grant. Downstream STM implementation that depends on Registrar/Department Staff/Advisor approval remains frozen.
