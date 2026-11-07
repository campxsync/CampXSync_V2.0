# CampXSync — RBAC Implementation Alignment Report

**Audit ID:** `RBAC-FOUNDATION-2026-11-07`  
**Purpose:** Map *current* authorization behavior to layers and list changes required **after** RBAC decisions are approved.  
**Constraint (audit phase):** No implementation changes during the audit freeze.  
**Post-approval (2026-11-07):** D-RBAC-01…07 implemented — see `APPROVED_DECISIONS.md` and `IMPLEMENTATION_CLOSEOUT.md`.

---

## 1. Layer map (current vs required)

| Layer | Current behavior | Aligned target (post-approval) | Gap IDs |
| :--- | :--- | :--- | :--- |
| Frontend | Role routing via `resolve-role`; proxies to Gateway; UI not enforcement | Keep UI cosmetic only; never sole authZ | GAP-022 |
| API Gateway | JWT validate, IdentityResolver, strip/spoof-protect headers, HMAC to services; route table includes `/api/v1/enrollment` → 8095 | Keep authN + identity; optional coarse route ACLs; services remain authoritative | GAP-022, GAP-023 |
| JWT / claims | Supabase JWT `sub`; gateway resolves tenant/role/college | Claims must carry approved role codes / permission hooks; no user_metadata for authZ | GAP-010, GAP-013 |
| Java STM-02 | Hardcoded role sets including invented R31–R33 / admissions aliases | Permission checks against approved catalogue + SoD | GAP-010, GAP-011, GAP-020 |
| Java STM-03 | **Updated:** R31 CFG/approve/promote; R07/R08 allocate; R25/R10 as approved; unlisted still denied | Approved grants live; keep deny for unassigned | GAP-001–GAP-009 closed for STM-03 admin |
| Java ACD/ADM | Mixed role asserts (`REGISTRAR`, `COLLEGE_ADMIN`, …) | Prefer coded permissions (`acd*`, `adm*`) already in Scripts | GAP-020 |
| IAM SQL | `iam.has_permission`, `iam.perm_all`, `iam.perm_department_ids`, `iam.has_permission_in` | Bind STM policies to new `stu*` codes | GAP-012 |
| Scripts seed | 468 perms; no `stu*`; missing R01/R02/R27/R30 rows; no Registrar | Seed approved roles/perms | GAP-012–GAP-014 |
| PostgreSQL RLS | Tenant isolation strong; ownership on some STM-01 tables; STM-03 DDL unapplied | Apply only after approval; add section/dept predicates where required | GAP-021 |
| Storage | Supabase Storage for blobs (STM-06 intent) | Short-lived URLs; uploader≠verifier | GAP-015, GAP-016 |
| Intelligence | Outbox JSON contracts | No authZ grants via events | — |
| Tests | STM-03 unit denials/allows; limited live STM authZ | Expand per validation strategy | see strategy doc |
| Docs | Matrix + backlog + this audit pack | Update authoritative matrix/CSV only after approval | D-RBAC-* |

---

## 2. File-level alignment notes (non-destructive)

| Artifact | Alignment note |
| :--- | :--- |
| `api-gateway/.../GatewayConfig.java` | Enrolment routes present; no STM permission filter (acceptable if services enforce). |
| `enrollment-service/.../EnrollmentDomainService.java` | Unlocked for R31/R07/R08 per D-RBAC-01/03; JDBC persistence optional. |
| `admissions-service/.../AdmissionsPermissionEvaluator.java` | Catalogue R31–R33 ratified; evaluator still should prefer `stu02.*` codes next. |
| `database/migrations/20261107000100_create_stu_enrollment_tables.sql` | **Applied** to `azulbaxszsqxymqmlwfe` with FORCE RLS. |
| `database/migrations/20261107000200_rbac_stu_catalogue_addendum.sql` | **Applied**; `iam.resync_system_roles()` executed. |
| `database/migrations/Scripts_V3.0/rbac_role_permissions.csv` | Authoritative coded seed for non-STM domains; incomplete for STM. |
| `docs/architecture/STM-03_ENROLLMENT_SPECIFICATION.md` | Documents R25/R10 + POLICY_UNASSIGNED; update after approval. |

---

## 3. Working-tree preservation (audit-time)

Branch `feature/java-backend-integration` @ `44d7909` with local STM-03 and handover files **left intact**. Alignment work must not reset/revert these files.

Modified/untracked at audit start (abbreviated):

- Modified: `GatewayConfig.java`, `student/page.tsx`, `student-services/pom.xml`
- Untracked: `enrollment-service/**`, enrolment frontend/API, STM-03 migrations, STM-03 spec, `docs/handover/cursor/**`, gateway enrolment route test

---

## 4. Post-approval implementation sequence (proposal)

1. Approve D-RBAC-01…07.  
2. Update authoritative matrix + validated CSV stamps.  
3. Seed IAM roles/permissions (`stu*` + missing R0x).  
4. Refactor Java evaluators to permission codes.  
5. Apply RLS migrations on **dev** `azulbaxszsqxymqmlwfe` only with explicit approval.  
6. Run authorization validation strategy; then unfreeze STM-03 admin + STM-04+.  

No step above is authorized by this report alone.
