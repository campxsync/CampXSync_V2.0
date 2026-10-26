# CampXSync ERP on Supabase — v3 (RBAC matrix implemented)

883 MongoDB collections -> **428 PostgreSQL tables, 20 schemas, 14 views**. Collection-by-collection mapping: `collection_mapping.csv`.
v3 implements `CampXSync_Enterprise_RBAC_Role_Matrix.docx` (30 roles, services ACD/LMS/EXM/FIN/HRM/CAM, admin and shared services, scope rules, separation of duties).

## Read this first
* **Tested on local PostgreSQL 16 with stubbed Supabase auth. Never run on a real Supabase project.** Use a staging project before any real data.
* Only ADM-01/02 had field definitions in your upload; all other tables are my design from collection names. STU/IAM modules were not in the upload.
* RBAC is *implemented from the matrix as I read it*. Where the matrix is silent or ambiguous I made assumptions (list below). **Have the module owners confirm them.**
* **Query performance was not tested.** Policies call helper functions; run `EXPLAIN (ANALYZE)` on representative data.

## How the matrix maps to the database (the 4 authorization levels)
| Matrix level | Implementation |
|---|---|
| 1 Service access | A permission code per service and level, e.g. `fin03.approve`. Levels: `view`, `write`, `manage`, `review`, `approve`, `publish`, `execute`, `export`. Matrix letters expand as: V=view · W=view+write · M=view+write+manage+review · RV=view+review · A=view+approve · P=view+publish · X=view+execute · E=view+export. M does **not** include approve/publish/execute (separation of duties). |
| 2 Operation | RLS: select / insert / update / delete use view / write / write / manage. Approve, review, publish, execute are enforced as **workflow transitions** (`iam.transition_rules`, 56 rules): moving a record into a state needs that permission. A person holding only a workflow permission can change status/approval fields only. |
| 3 Data scope | Every grant carries a scope from the role assignment: institute-wide (`college_id` and `department_id` empty), one college, or one department. Policies resolve a row to its college / department / program / section / course / subject / student / employee (directly, or through its parent record) and check the scope **of the specific grant**, not "any grant anywhere". Faculty/valuator: ownership (`.own` permissions + assigned sections/courses/subjects/scripts). Student/parent: own or linked-child rows, enrolled sections, published content. |
| 4 Field level | Personal data needs `core.pii.read`; names-only `core.v_person_directory` for pickers; audit payloads redacted by field policy; bank/ID values are ciphertext-only. There is no per-column RLS. |

`.own` permission variants and `.view.all`: structural tables need `<svc>.view`; tables that belong to a student/employee need `<svc>.view.all` for staff-wide reads. Students and employees read their own rows through self paths, which need no permission.

## Roles (seeded per tenant from `iam.role_templates`)
TENANT_ADMIN (bootstrap, `*`), INSTITUTION_ADMIN, COLLEGE_ADMIN, PRINCIPAL, VICE_PRINCIPAL, DEAN, HOD, PROGRAM_COORDINATOR, FACULTY, SECTION_COORDINATOR, EXAM_CONTROLLER, EXAM_CELL, VALUATOR, FINANCE_MANAGER, ACCOUNTANT, HR_MANAGER, HR_OFFICER, COMM_MANAGER, PLACEMENT_OFFICER, LIBRARIAN, HOSTEL_WARDEN, TRANSPORT_MANAGER, FACILITY_MANAGER, STUDENT, PARENT, AUDITOR, REPORT_USER, GUEST, plus ACADEMIC_ADMIN and EMPLOYEE (used by the matrix columns but missing from its catalogue).
R01 Super Admin / R02 System Admin = platform operators (`plat.platform_admins`, service_role). R27 API Client = `iam.api_clients` + `iam.api_client_grants` (machine identity with explicit scoped grants, no role inheritance).

**How to assign:** `iam.role_assignments (user_id, role_id, college_id, department_id, valid_from, valid_to)`. Institute-wide = both empty. HOD = set `college_id` and `department_id`. Parents need a row in `stu.guardianships` (`can_pay_fees` controls payment). Who may assign what is in `iam.role_grant_rules` (College Admin: any role except TENANT_ADMIN / INSTITUTION_ADMIN / AUDITOR, inside its own college; Institution Admin: any except TENANT_ADMIN). Nobody can change their own assignments; custom roles may only contain permissions the creator holds.

## Separation of duties and workflow (see `rbac_workflow_rules.csv`)
26 transitions require the actor to differ from whoever handled earlier states (e.g. result: process -> approve -> publish each by a different person; marks valuator != approver; expense create != approve != pay; payroll approve != finalise != pay; budget, fee structure, requisition, offer approvals). 17 block self-action (nobody approves their own leave, scholarship application, expense claim, training nomination…). Delegations (`iam.delegations`, max 90 days, only a role you hold) give the delegate the delegator's scoped authority; the **delegate** is the actor, so separation of duties still applies.

## Assumptions and deviations from the matrix (please confirm)
1. Matrix has no "Institution" level between tenant and college. I treat the tenant as the institution; scope comes from the assignment (empty college = institute-wide). Principal/Dean "Institution" scope is therefore per assignment.
2. VICE_PRINCIPAL = PRINCIPAL; DEAN = PRINCIPAL on academic services only; SECTION_COORDINATOR = FACULTY; INSTITUTION_ADMIN = COLLEGE_ADMIN plus institution/campus/integration rights, assigned institute-wide; matrix "Coordinator" = PROGRAM_COORDINATOR.
3. Roles with no module in your inventory (Placement, Library, Hostel, Transport, Facility) get base staff access only. **HRM-07 Exit Management has no tables in the upload**, so nothing enforces it yet.
4. Matrix has no execute/publish for some steps; I added: Finance Manager may execute expense payments; EXM-08 approvers (Controller, Principal) may publish results (separation of duties still forces a different person from the approver).
5. Matrix lists no dependencies, but exam roles need student/programme/subject read access, HR/finance/communication roles need departments/programmes; I granted read-only access (`DEPENDENCY_VIEW` in `rbac_data.py`). Auditor additionally reads the ledger and, per matrix "Auditor M", audit payloads.
6. Faculty "M" on LMS means own sections/courses/content only (not tenant-wide). Faculty can still *view* other teachers' draft LMS records in their college (structural read); they cannot read other students' submissions/grades.
7. `core.write` (shared reference data) and `doc.*` are granted to the roles listed in `EXTRA_PERMS`; they are not matrix items.
8. Every staff role also has employee self-service (own leave/attendance/appraisal/training) and workflow participation (`wf.write`).

## Known limitations
* **Dept-scoped roles (HOD) see only tables that have a department path** (programmes, courses, subjects, students via programme, employees, attendance, leave, sections…). Tables with only a college path (e.g. messages, finance) are not visible to a purely department-scoped assignment.
* `core.persons` has no college/department column, so HR/admin roles with `core.pii.read` see persons across the whole tenant. Audit trail views are tenant-wide (permission-gated, not college-scoped).
* Student/parent visibility paths exist for the main LMS/ACD/EXM/FIN tables listed in `rbac_table_permissions.csv` (column "student_visibility"); other tables rely on self paths only.
* `iam.api_clients.allowed_ips` and expiry are stored, not enforced in SQL (enforce at the gateway / auth hook).
* Audit redaction is by column name and per-table policy; jsonb columns are not scanned. One user = one tenant (unchanged). `college_id IS NULL` rows are readable with the permission but writable only with an institute-wide grant (new, enforced).
* Business rules beyond status/ownership/separation of duties (fee waivers vs refunds, exam eligibility, leave sandwich rules, payroll statutory maths) are still structure only.
* Not verified: real Supabase behaviour (JWT claims, hook), load/performance, backup/restore.

## Tests (`tests/`)
`test_rbac.sql` (generated by `gen_tests_rbac.py`): **166 checks, 0 failures** on local Postgres 16 — scope by college/department/institute, faculty ownership, student/parent paths, exam/finance/HR separation of duties, self-approval blocks, role administration, delegation (effective and revoked), API client, audit access, cross-entity consistency, timetable rules, tenant isolation, function privileges, RLS on every table. `conc_rbac.sh`: two simultaneous sessions cannot over-pay an obligation or over-use a payment. Reproduce: `psql -f tests/test_boot.sql` (plain Postgres only — never on Supabase), apply the migrations in order, `psql -f tests/test_rbac.sql`, then `tests/conc_rbac.sh`.
The first test run found real bugs that are fixed: rows with an empty scope column leaked to users without the permission; a NULL comparison let a parent without `can_pay_fees` start a payment; a user with only "own" write rights could edit others' rows through a workflow permission.

## Deploy
Run in order: `00_extensions_schemas` -> `10_early_helpers` -> `20_tables` -> `30_foreign_keys` -> `40_functions` -> `45_partitions` -> `50_triggers` -> **`55_scope_functions`** -> `60_rls` -> `70_views` -> `75_grants` -> `80_seed` -> `85_auth_hook`. Then: enable the Custom Access Token hook (`iam.custom_access_token_hook`); create a tenant (roles seed automatically; run `select iam.resync_system_roles()` after changing templates); create the first profile + TENANT_ADMIN assignment with `service_role`; configure `core.number_sequences`; run `sys.ensure_partitions(3)` before any audited write. Expose only a thin `api` schema through the Data API.

## Files
`rbac_role_permissions.csv` (role -> permission), `rbac_table_permissions.csv` (table -> service, read/write/delete permissions, ownership/visibility flags), `rbac_workflow_rules.csv` (transition + separation-of-duties rules), `collection_mapping.csv`, `generator/` (edit `rbac_data.py` / `s08_rbac.py`, run `python3 gen.py <dir>`; paths assume `/home/claude/gen`).
