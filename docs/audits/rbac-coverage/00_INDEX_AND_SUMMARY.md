# Consolidated RBAC Coverage Audit: Index & Executive Summary

**Document:** `docs/audits/rbac-coverage/00_INDEX_AND_SUMMARY.md`  
**Audit Pass:** Second Consolidated Read-Only Audit  
**Date:** 2026-11-20  
**Authority:** Project Owner, Niranjan V.G. (Agreement V2.1, AGENTS.md, PROGRESS.md, NEXT_TASK.md)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog & table queries; zero writes)  

---

## 1. Audit Deliverables Index

All deliverables for this audit pass have been written into `docs/audits/rbac-coverage/`:

| File Name | Purpose & Primary Scope |
| :--- | :--- |
| **[`00_INDEX_AND_SUMMARY.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/00_INDEX_AND_SUMMARY.md)** | Master file index, top 10 governance findings, non-verified items, query logs, and compliance confirmation. |
| **[`Part-A_roles.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-A_roles.md)** | Full role reconciliation: R01–R39 vs live database templates, active flags, holder counts, and explicit status on R27 and R32–R39. |
| **[`Part-B_permission-map.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-B_permission-map.md)** | Detailed analysis of the 571 permission catalog, R03/R04 holdings, and owner decisions of 2026-11-20. |
| **[`Part-B_permission-map.csv`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-B_permission-map.csv)** | Complete 571-row CSV mapping every permission key, holding roles, target services, R03/R04 actions, and new owners. |
| **[`Part-C_wildcard-gaps.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-C_wildcard-gaps.md)** | Deep-dive on `iam.role_grant_rules`, wildcard `*`, orphaned `adm` keys, privileged session bypass, and `test_rbac.sql` source of truth. |
| **[`Part-D_assignment.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-D_assignment.md)** | Analysis of role assignment writers, evaluation of guards 1–7 (§21.1), delegation records, and break-glass pathways. |
| **[`Part-E_enforcement.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-E_enforcement.md)** | Multi-tier enforcement review (Gateway, Java microservices, PostgreSQL RLS, Next.js frontend), coarse role checks, and top 20 file:line anchors. |
| **[`Part-F_sod.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-F_sod.md)** | Segregation of Duties evaluation across Examination, Finance, HR, Admissions, and Access Governance. |
| **[`Part-G_api-clients.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-G_api-clients.md)** | Machine identity review (R27): `iam.api_clients`, 90-day expiry, human ownership, explicit scopes, and dormant `adm03` status. |
| **[`Part-H_audit.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-H_audit.md)** | Audit logging immutability, `audit.capture()` trigger, and Auditor (R28) permission over-granting. |
| **[`Part-I_stm07.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-I_stm07.md)** | Student (R25) & Guardian (R26) provisioning pipeline, `StudentRoleAssignmentService.java`, and Applicant / External Verifier handling. |
| **[`Part-J_seeds-drift.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-J_seeds-drift.md)** | Seed and migration lineage, 0-drift cross-tenant analysis, single source of truth, and DEV test personas. |
| **[`Part-K_impact.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-K_impact.md)** | Blast radius of narrowing R03/R04, caller breakage analysis, Tenant Admin transition safety, and required DDL inventory. |
| **[`Part-L_matrix-gaps.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-L_matrix-gaps.md)** | Discrepancy register between Matrix v2.3 and live implementation tagged by SYSTEM NEEDS UPDATE / MATRIX NEEDS UPDATE. |
| **[`Part-M_findings-register.md`](file:///d:/CampXSync/CampXSync_V2.0/docs/audits/rbac-coverage/Part-M_findings-register.md)** | Security defect register (SEC-RBAC-01 through SEC-RBAC-10) with severity ratings and architectural citations. |

---

## 2. Top 10 Findings Most Affecting the Final Matrix

1. **Privileged Session Superuser Bypass (Critical - SEC-RBAC-01):** `frontend/src/lib/db.ts` connects directly as PostgreSQL user `postgres`. Because `current_user not in ('authenticated', 'anon')`, `iam.is_privileged_session()` returns `true`, completely disabling PostgreSQL Row-Level Security (`BYPASSRLS`) and bypassing trigger `iam.guard_role_assignment` (skipping self-grant, scope, and grant rules).
2. **Wildcard `*` Confined to Single User (High - SEC-RBAC-02):** Although `TENANT_ADMIN` holds wildcard `*` across all 49 tenants, only **1 user** (`ba77b8f7-ab32-46bd-85d2-5aade3526880`) holds this role in DEV. Collapsing Tenant Admin into `INSTITUTION_ADMIN` (R03) per Decision IADM-D7 will eliminate wildcard `*` with zero impact on the other 48 tenants.
3. **College Admin Over-Granting Vulnerability (High - SEC-RBAC-03):** Table `iam.role_grant_rules` allows `COLLEGE_ADMIN` to grant `PRINCIPAL` (R05), `VICE_PRINCIPAL` (R06), and `DEAN` (R07), violating the escalation guard in Matrix §21.1.
4. **Auditor Role Mutation Over-Granting (High - SEC-RBAC-04):** Role `AUDITOR` (R28) holds `audit.admin`, `audit.write`, `wf.write`, `core.comment`, and 5 HR write keys, directly violating the independent read-only mandate in Matrix §8, §12, and §21.2.
5. **Java Service Coarse Role String Decoupling (Medium - SEC-RBAC-05):** Multiple Java microservices (e.g. `batch-management-service:474`, `attendance-service:641`) check `assertRole("COLLEGE_ADMIN")` directly. Narrowing permission rows in `iam.role_permissions` will NOT restrict College Admin operations until Java controllers evaluate permission tokens.
6. **Zero Live Institution Admins (Medium):** Across all 49 DEV tenants, `INSTITUTION_ADMIN` (R03) has **0 assigned holders**. Under live grant rules, an Institution Admin can only be appointed by `TENANT_ADMIN` or superuser script.
7. **Swapped Role Template Identifiers (Low - SEC-RBAC-09):** In `iam.role_templates`, `APPLICANT` is tagged as `catalogue_id = 'R33'` (Matrix says R32), and `ADMISSIONS_OFFICER` is tagged as `catalogue_id = 'R32'` (Matrix says R33).
8. **Missing Student Module Roles R34–R39 (Medium):** Roles `R34 Department Staff`, `R35 Academic Records Officer`, `R36 Document Verifier`, `R37 External Verification Agency`, `R38 Provisioning / IAM Operator`, and `R39 Faculty Advisor` do not exist in the database.
9. **Frontend Desk Fail-Closed Gap (Medium):** `frontend/src/lib/auth/resolve-role.ts` only routes 5 desktop buckets (`platform_admin`, `college_admin`, `registrar`, `faculty`, `student`). The remaining 34 enterprise roles fail closed with `no_role_assigned`.
10. **Dormant `adm03` Family (Low):** Machine identity management (`adm03.*`) is dormant per Decision IADM-D5. `adm03.manage` is on no role template, preventing standard administrators from deleting API clients.

---

## 3. Items NOT VERIFIED (Out of Scope / Telemetry Gaps)

1. **Live Production / Staging Traffic Bypass Volume:** The exact percentage of live HTTP requests invoking direct superuser routes vs API Gateway routes cannot be measured without live APM runtime telemetry.
2. **Network Perimeter Isolation in Cloud Deployments:** Verification was conducted against local configurations and DEV PostgreSQL `azulbaxszsqxymqmlwfe`. Physical VPC network firewalling between port 8080 and downstream service ports in production cloud environments was not inspected.

---

## 4. Exact Read-Only Queries & Commands Executed

All database queries were executed using the Supabase MCP tool in read-only SELECT mode against DEV project `azulbaxszsqxymqmlwfe`:
```sql
-- Role grant rules verification
SELECT grantor_role, grantable_role FROM iam.role_grant_rules ORDER BY grantor_role, grantable_role;

-- Wildcard holdings in templates and live permissions
SELECT role_code, permission_code FROM iam.role_template_permissions WHERE permission_code LIKE '%*%';
SELECT r.code, p.code as perm_code, count(*) FROM iam.role_permissions rp JOIN iam.roles r ON r.id = rp.role_id JOIN iam.permissions p ON p.id = rp.permission_id WHERE p.code LIKE '%*%' GROUP BY r.code, p.code;

-- Role template catalog and holder counts
SELECT rt.code, rt.name, rt.catalogue_id, rt.kind, rt.scope_note, count(ra.id) as holder_count FROM iam.role_templates rt LEFT JOIN iam.roles r ON r.code = rt.code LEFT JOIN iam.role_assignments ra ON ra.role_id = r.id AND ra.deleted_at IS NULL GROUP BY rt.code, rt.name, rt.catalogue_id, rt.kind, rt.scope_note ORDER BY rt.catalogue_id, rt.code;

-- Permission catalog coverage and holdings
SELECT p.code, count(DISTINCT rtp.role_code) as role_count FROM iam.permissions p LEFT JOIN iam.role_template_permissions rtp ON rtp.permission_code = p.code AND rtp.deleted_at IS NULL WHERE p.deleted_at IS NULL GROUP BY p.code ORDER BY p.code;
SELECT rtp.role_code, rtp.permission_code FROM iam.role_template_permissions rtp WHERE rtp.role_code IN ('INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'DEAN', 'AUDITOR', 'REGISTRAR') AND rtp.deleted_at IS NULL ORDER BY rtp.role_code, rtp.permission_code;

-- Function source code verification
SELECT proname, prosrc FROM pg_proc JOIN pg_namespace ON pg_proc.pronamespace = pg_namespace.oid WHERE nspname = 'iam' AND proname IN ('has_permission', 'can_grant_role', 'guard_role_assignment', 'is_privileged_session', 'grant_scopes');
```

---

## 5. Compliance & Working Tree Confirmation

- **Zero Existing Files Modified:** No existing repository code, scripts, configurations, or documentation files were edited or deleted.
- **Zero Git Changes:** No Git commits, branches, stashes, or resets were performed.
- **Zero Database Writes:** Zero `INSERT`, `UPDATE`, `DELETE`, `ALTER`, `DROP`, or DDL statements were executed.
- **Scope Limit Observed:** All outputs were written strictly as new markdown and CSV report files inside `docs/audits/rbac-coverage/`.
