# IADM-03 — IAM Inventory (read-only)

**Project:** CampXSync-Architecture-Dev (`azulbaxszsqxymqmlwfe`) only  
**Captured:** 2026-11-27  
**Mode:** Read-only (no code changes beyond this file; no DB writes; no commits required by this task)  
**Rule:** No emails, passwords, tokens, or personal data in this document.

---

## 1. How identity works today

### Where users, profiles, roles and permissions live

| Store | Table | Purpose (plain language) | Notable columns (names only) |
|---|---|---|---|
| Supabase Auth | `auth.users` | Login account (identity provider) | `id` (subject), status/timestamps (Auth-managed) |
| Profiles | `iam.user_profiles` | App profile linked to Auth user + tenant | `id`, `tenant_id`, `person_id`, `college_id`, `department_id`, `username`, `status`, `preferences`, `deleted_at` |
| People directory | `core.persons` | Person record (not the login) | `id`, `tenant_id`, `display_name`, `status`, … |
| Tenants | `core.tenants` | Institute / tenant | `id`, status fields |
| Platform operators | `plat.platform_admins` | Super Admin (**R01**) / System Admin (**R02**) outside tenant roles | `user_id`, `role_code` (`SUPER_ADMIN` / `SYSTEM_ADMIN`), `status`, `deleted_at` |
| Role catalogue | `iam.role_templates` | Platform role definitions (**R02**–**R39** as present; **R01**/**R27** absent as templates) | `code`, `name`, `catalogue_id`, `kind`, `scope_note` |
| Live tenant roles | `iam.roles` | Per-tenant role rows | `tenant_id`, `college_id`, `code`, `name`, `is_system`, `status` |
| Permission catalogue | `iam.permissions` | Global permission keys | `code`, `module`, `resource`, `action`, `description`, `deleted_at` |
| Template grants | `iam.role_template_permissions` | Default key ↔ role-template map | `role_code`, `permission_code`, `created_at`, `created_by` |
| Live grants | `iam.role_permissions` | Per-tenant role ↔ permission | `tenant_id`, `role_id`, `permission_id`, `created_at`, `created_by` |
| Assignments | `iam.role_assignments` | Who holds which role | `tenant_id`, `user_id`, `role_id`, `college_id`, `department_id`, `valid_from`, `valid_to`, `granted_by` |
| Grantor rules | `iam.role_grant_rules` | Which role may grant which role | `grantor_role`, `grantable_role` |
| Hierarchy (unused by key check) | `iam.role_hierarchy` | Parent/child role labels | `parent_role`, `child_role` |
| Access requests | `iam.access_requests` | Request → decide path | `requester_user_id`, `subject_user_id`, `requested_role_code`, `status`, `decided_by`, `decided_at` |
| Catalogue changes | `iam.catalog_change_requests` | Propose/approve template grant changes | `change_type`, `role_code`, `permission_code`, `proposed_by`, `decided_by`, `status` |

Runtime permission check function: `iam.has_permission(code)` → `iam.grant_scopes(code)` (looks at assignments, group membership, delegations, and `iam.role_permissions`; also matches `*` and `module.*` wildcards). Platform-admin helper: `iam.is_platform_admin()` reads `plat.platform_admins`.

### Login → claims → gateway → services

1. User signs in via Supabase Auth (UI `/login`, BFF helpers such as `frontend/src/app/api/auth/resolve/route.ts`, `frontend/src/app/api/auth/test-session/route.ts`, `frontend/src/app/auth/callback/route.ts`).
2. Browser/BFF sends `Authorization: Bearer <JWT>` to API Gateway (`:8080`).
3. Gateway validates JWT in `api-gateway/.../SupabaseJwtValidator.java` (ES256 / JWKS; audience `authenticated`).
4. Gateway resolves business identity from DB in `api-gateway/.../JdbcIdentityResolver.java` (`plat.platform_admins`, `iam.user_profiles`, `core.tenants`, `iam.role_assignments` / `iam.roles`) — not from client-supplied identity headers.
5. Gateway forwards trusted headers (`X-User-Id`, `X-Tenant-Id`, `X-User-Role`) plus HMAC (`X-Gateway-Timestamp`, `X-Gateway-Signature`) via `api-gateway/.../ReverseProxyHandler.java`.
6. Downstream services verify HMAC (`GatewayHmacVerifier.java` in each IADM service) and bind Postgres session claims in `UserSecurityContext.java` (`SET LOCAL ROLE authenticated`; `set_config('request.jwt.claims', …)` with `sub`, `tenant_id`, `user_role`).
7. Domain code checks keys with `SELECT iam.has_permission(?)` and/or platform-admin SQL against `plat.platform_admins` (file examples: `OpsDomainService.java`, `SecurityDomainService.java`, IADM-03 governance services).

### Where claims / keys are checked (file names only)

- `SupabaseJwtValidator.java` — JWT crypto + claim extract  
- `JdbcIdentityResolver.java` — authoritative user/tenant/role resolution  
- `ReverseProxyHandler.java` — inject trusted headers  
- `GatewayHmacVerifier.java` — internal HMAC verify (per service)  
- `UserSecurityContext.java` — bind `request.jwt.claims` for RLS  
- `GrantorCeiling.java` — §21.1 grantor ceiling (IADM-03)  
- `GrantGovernanceService.java` / `AccessRequestGovernanceService.java` / `CatalogChangeGovernanceService.java` — grant/decide + self-approve guards  
- `DurableAuditWriter.java` — writes via `audit.log_event` → `audit.events`  
- Offline matrices (documentation of expected keys, not runtime): `OpsPermissionMatrix.java`, `SecurityPermissionMatrix.java`, `AccessPermissionMatrix.java`

---

## 2. The permission catalogue

**Total active keys (`iam.permissions` where `deleted_at` is null): 663**

### Counts by requested prefix buckets

| Prefix | Count | Service that uses it (plain language) |
|---|---:|---|
| `adm01` | 5 | Legacy CADM/admin matrix keys; general admin desk catalogue (not an IADM microservice insert) |
| `adm02` | 7 | Same — CADM/admin catalogue |
| `adm03` | 6 | Same — CADM/admin catalogue (dormant relative to IADM-03’s `iam.*` keys) |
| `adm04` | 2 | Same — CADM/admin catalogue |
| `adm05` | 5 | Same — CADM/admin catalogue |
| `adm06` | 7 | Same — CADM/admin catalogue |
| `adm07` | 8 | Same — CADM/admin catalogue |
| `adm08` | 7 | Same — CADM/admin catalogue |
| **adm01–adm08 subtotal** | **47** | |
| `iadm01` | **0** | IADM-01 Tenant Lifecycle — no `iadm01.*` keys; uses `plat.*` + platform-admin checks |
| `iadm02` | **0** | IADM-02 uses **`cfg.*`** keys (counted under others) |
| `iadm03` | **0** | IADM-03 uses **`iam.*`** keys (counted under others) |
| `iadm04` | 20 | `iadm-04-billing-entitlements-service` |
| `iadm05` | 23 | `iadm-05-data-governance-service` |
| `iadm06` | 7 | `iadm-06-platform-analytics-service` |
| `iadm07` | 11 | `iadm-07-security-compliance-service` |
| `iadm08` | 5 | `iadm-08-audit-health-ops-service` |
| **iadm01–iadm08 literal subtotal** | **66** | |
| **others** | **550** | See subgroups below |

### Full key list — `adm01` … `adm08`

- **adm01** (institute/admin catalogue): `adm01.manage`, `adm01.review`, `adm01.view`, `adm01.view.all`, `adm01.write`
- **adm02**: `adm02.manage`, `adm02.review`, `adm02.view`, `adm02.view.all`, `adm02.view.own`, `adm02.write`, `adm02.write.own`
- **adm03**: `adm03.manage`, `adm03.view`, `adm03.view.all`, `adm03.view.own`, `adm03.write`, `adm03.write.own`
- **adm04**: `adm04.view`, `adm04.view.all`
- **adm05**: `adm05.manage`, `adm05.view`, `adm05.view.all`, `adm05.write`, `adm05.write.own`
- **adm06**: `adm06.manage`, `adm06.review`, `adm06.view`, `adm06.view.all`, `adm06.view.own`, `adm06.write`, `adm06.write.own`
- **adm07**: `adm07.approve`, `adm07.manage`, `adm07.review`, `adm07.view`, `adm07.view.all`, `adm07.view.own`, `adm07.write`, `adm07.write.own`
- **adm08**: `adm08.manage`, `adm08.review`, `adm08.view`, `adm08.view.all`, `adm08.view.own`, `adm08.write`, `adm08.write.own`

### Full key list — literal `iadm01` … `iadm08`

- **iadm01**: _(none)_
- **iadm02**: _(none — see `cfg.*` under others)_
- **iadm03**: _(none — see `iam.*` under others)_
- **iadm04** (billing): `iadm04.audit.view`, `iadm04.entitlements.check`, `iadm04.entitlements.map`, `iadm04.invoices.manage`, `iadm04.invoices.view.own`, `iadm04.jobs.run`, `iadm04.payments.callback`, `iadm04.payments.callback.retry`, `iadm04.payments.offline.reconcile`, `iadm04.payments.offline.record`, `iadm04.payments.online.initiate`, `iadm04.plan_change.decide`, `iadm04.plan_change.request`, `iadm04.plans.manage`, `iadm04.plans.view`, `iadm04.reports.view`, `iadm04.subscriptions.attach`, `iadm04.subscriptions.view`, `iadm04.subscriptions.view.own`, `iadm04.usage.record`
- **iadm05** (data governance): `iadm05.adherence.view`, `iadm05.audit.view`, `iadm05.classification.propose`, `iadm05.classification.view`, `iadm05.college.view`, `iadm05.domain.contract`, `iadm05.erasure.close`, `iadm05.erasure.decide`, `iadm05.erasure.request`, `iadm05.export.decide`, `iadm05.export.request`, `iadm05.policy.approve`, `iadm05.policy.history`, `iadm05.policy.publish`, `iadm05.privacy.propose`, `iadm05.privacy.view`, `iadm05.requests.operate`, `iadm05.retention.propose`, `iadm05.retention.view`, `iadm05.sharing.decide`, `iadm05.sharing.lifecycle`, `iadm05.sharing.propose`, `iadm05.sharing.view`
- **iadm06** (analytics): `iadm06.catalogue.manage`, `iadm06.catalogue.view`, `iadm06.entitlement.view`, `iadm06.export.request`, `iadm06.export.view`, `iadm06.institute.view`, `iadm06.platform.view`
- **iadm07** (security/compliance): `iadm07.audit.view`, `iadm07.exception.decide`, `iadm07.exception.request`, `iadm07.posture.view`, `iadm07.register.read`, `iadm07.register.write`, `iadm07.report.view`, `iadm07.signal.intake`, `iadm07.standard.approve`, `iadm07.standard.propose`, `iadm07.summary.view`
- **iadm08** (audit/health/ops): `iadm08.alerts.view`, `iadm08.audit.institute.view`, `iadm08.audit.platform.view`, `iadm08.health.view`, `iadm08.manage`

### Full key list — others (550), grouped by family → consumer

- **`*`** (1) — wildcard key matched by `iam.grant_scopes`: `*`
- **`cfg.*`** (8) — **IADM-02** Platform Config: `cfg.approve`, `cfg.catalog.view`, `cfg.consumer.read`, `cfg.draft`, `cfg.policy.draft`, `cfg.publish`, `cfg.view`, `cfg.view.own`
- **`iam.*`** (20) — **IADM-03** Identity & Access: `iam.access.overview.view`, `iam.access.subject.view`, `iam.audit.view`, `iam.catalog.change.approve`, `iam.catalog.change.propose`, `iam.catalog.permissions.view`, `iam.catalog.roles.view`, `iam.delegation.create`, `iam.delegation.revoke`, `iam.governance.read`, `iam.grant.assign`, `iam.grant.r03.decide`, `iam.grant.revoke`, `iam.identity.technical.assign`, `iam.profile.invite`, `iam.profile.status`, `iam.profile.view`, `iam.request.decide`, `iam.request.submit`, `iam.review.manage`
- **`adm09.*`** (8) — CADM scope key used by DB grant guard (`adm09.write` in `iam.guard_role_assignment`): `adm09.approve`, `adm09.manage`, `adm09.review`, `adm09.view`, `adm09.view.all`, `adm09.view.own`, `adm09.write`, `adm09.write.own`
- **`stu*`** (24) — Student lifecycle / admissions / enrolment (STM services): `stu01.view.own`, `stu01.write.own`, `stu02.application.review`, `stu02.decision.approve`, `stu02.intake.manage`, `stu03.enrolment.approve`, `stu03.enrolment.create`, `stu03.enrolment.view`, `stu03.enrolment.view.own`, `stu03.register.execute`, `stu03.roster.view`, `stu03.section.allocate`, `stu03.term.cfg`, `stu03.waitlist.join`, `stu03.waitlist.promote`, `stu03.window.cfg`, `stu04.lifecycle.graduation.record`, `stu04.lifecycle.graduation.validate`, `stu04.lifecycle.request.decide`, `stu04.lifecycle.request.submit`, `stu04.lifecycle.rule.cfg`, `stu04.lifecycle.view`, `stu04.lifecycle.view.linked`, `stu04.lifecycle.view.own`
- **`audit*`** (8) — shared audit read/write catalogue: `audit.admin`, `audit.admin.own`, `audit.read`, `audit.read.own`, `audit.read.payload`, `audit.read.payload.own`, `audit.write`, `audit.write.own`
- **`core*`** (10) — core directory/PII: `core.comment`, `core.comment.own`, `core.directory.view`, `core.pii.read`, `core.pii.read.own`, `core.pii.write`, `core.pii.write.own`, `core.view.all`, `core.write`, `core.write.own`
- **`doc*`** (8) — documents: `doc.admin`, `doc.admin.own`, `doc.manage`, `doc.view`, `doc.view.confidential`, `doc.view.own`, `doc.write`, `doc.write.own`
- **`plat*`** (4) — platform read/write: `plat.read`, `plat.read.own`, `plat.write`, `plat.write.own`
- **`rpt*`** (8) — reporting: `rpt.export`, `rpt.manage`, `rpt.review`, `rpt.view`, `rpt.view.all`, `rpt.view.own`, `rpt.write`, `rpt.write.own`
- **`wf*`** (4) — workflow: `wf.view`, `wf.view.own`, `wf.write`, `wf.write.own`
- **`recruit*`** (2): `recruit.read`, `recruit.write`
- **`acd*`** (73) — academic modules (future/CADM): `acd01.manage`, `acd01.review`, `acd01.view`, `acd01.view.all`, `acd01.view.own`, `acd01.write`, `acd01.write.own`, `acd02.approve`, `acd02.manage`, `acd02.review`, `acd02.view`, `acd02.view.all`, `acd02.view.own`, `acd02.write`, `acd02.write.own`, `acd03.approve`, `acd03.manage`, `acd03.review`, `acd03.view`, `acd03.view.all`, `acd03.view.own`, `acd03.write`, `acd03.write.own`, `acd04.manage`, `acd04.review`, `acd04.view`, `acd04.view.all`, `acd04.view.own`, `acd04.write`, `acd04.write.own`, `acd05.approve`, `acd05.manage`, `acd05.review`, `acd05.view`, `acd05.view.all`, `acd05.view.own`, `acd05.write`, `acd05.write.own`, `acd06.manage`, `acd06.review`, `acd06.view`, `acd06.view.all`, `acd06.view.own`, `acd06.write`, `acd06.write.own`, `acd07.manage`, `acd07.review`, `acd07.view`, `acd07.view.all`, `acd07.view.own`, `acd07.write`, `acd07.write.own`, `acd08.manage`, `acd08.review`, `acd08.view`, `acd08.view.all`, `acd08.view.own`, `acd08.write`, `acd08.write.own`, `acd09.approve`, `acd09.manage`, `acd09.review`, `acd09.view`, `acd09.view.all`, `acd09.view.own`, `acd09.write`, `acd09.write.own`, `acd10.manage`, `acd10.review`, `acd10.view`, `acd10.view.all`, `acd10.view.own`, `acd10.write`
- **`cam*`** (65) — campus modules: `cam01.approve`, `cam01.manage`, `cam01.review`, `cam01.view`, `cam01.view.all`, `cam01.view.own`, `cam01.write`, `cam01.write.own`, `cam02.approve`, `cam02.manage`, `cam02.review`, `cam02.view`, `cam02.view.all`, `cam02.view.own`, `cam02.write`, `cam02.write.own`, `cam03.approve`, `cam03.manage`, `cam03.review`, `cam03.view`, `cam03.view.all`, `cam03.view.own`, `cam03.write`, `cam03.write.own`, `cam04.approve`, `cam04.manage`, `cam04.review`, `cam04.view`, `cam04.view.all`, `cam04.view.own`, `cam04.write`, `cam04.write.own`, `cam05.approve`, `cam05.manage`, `cam05.review`, `cam05.view`, `cam05.view.all`, `cam05.view.own`, `cam05.write`, `cam05.write.own`, `cam06.approve`, `cam06.execute.own`, `cam06.manage`, `cam06.review`, `cam06.view`, `cam06.view.all`, `cam06.view.own`, `cam06.write`, `cam06.write.own`, `cam07.approve`, `cam07.execute.own`, `cam07.manage`, `cam07.review`, `cam07.view`, `cam07.view.all`, `cam07.view.own`, `cam07.write`, `cam07.write.own`, `cam08.export`, `cam08.manage`, `cam08.review`, `cam08.view`, `cam08.view.all`, `cam08.view.own`, `cam08.write`
- **`exm*`** (70) — examination modules: `exm01.manage`, `exm01.review`, `exm01.view`, `exm01.view.all`, `exm01.view.own`, `exm01.write`, `exm01.write.own`, `exm02.approve`, `exm02.manage`, `exm02.view`, `exm02.view.all`, `exm02.view.own`, `exm02.write`, `exm02.write.own`, `exm03.approve`, `exm03.manage`, `exm03.publish`, `exm03.view`, `exm03.view.all`, `exm03.view.own`, `exm03.write`, `exm03.write.own`, `exm04.execute.own`, `exm04.manage`, `exm04.review`, `exm04.view`, `exm04.view.all`, `exm04.view.own`, `exm04.write`, `exm04.write.own`, `exm05.approve`, `exm05.manage`, `exm05.review`, `exm05.review.own`, `exm05.view`, `exm05.view.all`, `exm05.view.own`, `exm05.write`, `exm05.write.own`, `exm06.approve`, `exm06.publish`, `exm06.view`, `exm06.view.all`, `exm06.view.own`, `exm06.write`, `exm07.execute.own`, `exm07.manage`, `exm07.review`, `exm07.view`, `exm07.view.all`, `exm07.view.own`, `exm07.write`, `exm07.write.own`, `exm08.approve`, `exm08.manage`, `exm08.publish`, `exm08.review`, `exm08.view`, `exm08.view.all`, `exm08.view.own`, `exm08.write`, `exm08.write.own`, `exm09.export`, `exm09.export.own`, `exm09.manage`, `exm09.review`, `exm09.view`, `exm09.view.all`, `exm09.view.own`, `exm09.write`
- **`fin*`** (84) — finance: `fin.bank.read`, `fin.bank.read.own`, `fin.bank.write`, `fin.bank.write.own`, `fin01.approve`, `fin01.manage`, `fin01.review`, `fin01.view`, `fin01.view.all`, `fin01.view.own`, `fin01.write`, `fin01.write.own`, `fin02.approve`, `fin02.execute`, `fin02.execute.own`, `fin02.manage`, `fin02.review`, `fin02.view`, `fin02.view.all`, `fin02.view.own`, `fin02.write`, `fin02.write.own`, `fin03.approve`, `fin03.execute`, `fin03.execute.own`, `fin03.manage`, `fin03.review`, `fin03.view`, `fin03.view.all`, `fin03.view.own`, `fin03.write`, `fin03.write.own`, `fin04.approve`, `fin04.manage`, `fin04.review`, `fin04.view`, `fin04.view.all`, `fin04.view.own`, `fin04.write`, `fin04.write.own`, `fin05.approve`, `fin05.manage`, `fin05.review`, `fin05.view`, `fin05.view.all`, `fin05.view.own`, `fin05.write`, `fin05.write.own`, `fin06.approve`, `fin06.execute`, `fin06.manage`, `fin06.review`, `fin06.view`, `fin06.view.all`, `fin06.view.own`, `fin06.write`, `fin06.write.own`, `fin07.export`, `fin07.manage`, `fin07.review`, `fin07.view`, `fin07.view.all`, `fin07.write`, `fin08.manage`, `fin08.review`, `fin08.view`, `fin08.view.all`, `fin08.view.own`, `fin08.write`, `fin08.write.own`, `fin09.approve`, `fin09.manage`, `fin09.review`, `fin09.view`, `fin09.view.all`, `fin09.view.own`, `fin09.write`, `fin09.write.own`, `fin10.export`, `fin10.manage`, `fin10.review`, `fin10.view`, `fin10.view.all`, `fin10.write`
- **`hrm*`** (69) — HR: `hrm01.manage`, `hrm01.review`, `hrm01.view`, `hrm01.view.all`, `hrm01.view.own`, `hrm01.write`, `hrm01.write.own`, `hrm02.approve`, `hrm02.execute`, `hrm02.manage`, `hrm02.review`, `hrm02.view`, `hrm02.view.all`, `hrm02.view.own`, `hrm02.write`, `hrm02.write.own`, `hrm03.manage`, `hrm03.review`, `hrm03.view`, `hrm03.view.all`, `hrm03.view.own`, `hrm03.write`, `hrm03.write.own`, `hrm04.approve`, `hrm04.manage`, `hrm04.review`, `hrm04.view`, `hrm04.view.all`, `hrm04.view.own`, `hrm04.write`, `hrm04.write.own`, `hrm05.approve`, `hrm05.manage`, `hrm05.review`, `hrm05.view`, `hrm05.view.all`, `hrm05.view.own`, `hrm05.write`, `hrm05.write.own`, `hrm06.approve`, `hrm06.execute`, `hrm06.manage`, `hrm06.review`, `hrm06.view`, `hrm06.view.all`, `hrm06.view.own`, `hrm06.write`, `hrm06.write.own`, `hrm07.approve`, `hrm07.manage`, `hrm07.review`, `hrm07.view`, `hrm07.view.all`, `hrm07.write`, `hrm07.write.own`, `hrm08.export`, `hrm08.manage`, `hrm08.review`, `hrm08.view`, `hrm08.view.all`, `hrm08.write`, `hrm09.approve`, `hrm09.manage`, `hrm09.review`, `hrm09.view`, `hrm09.view.all`, `hrm09.view.own`, `hrm09.write`, `hrm09.write.own`
- **`lms*`** (84) — LMS: `lms01.manage`, `lms01.review`, `lms01.view`, `lms01.view.all`, `lms01.view.own`, `lms01.write`, `lms01.write.own`, `lms02.manage`, `lms02.manage.own`, `lms02.review`, `lms02.review.own`, `lms02.view`, `lms02.view.all`, `lms02.view.own`, `lms02.write`, `lms02.write.own`, `lms03.manage`, `lms03.review`, `lms03.view`, `lms03.view.all`, `lms03.view.own`, `lms03.write`, `lms03.write.own`, `lms04.manage`, `lms04.manage.own`, `lms04.review`, `lms04.review.own`, `lms04.view`, `lms04.view.all`, `lms04.view.own`, `lms04.write`, `lms04.write.own`, `lms05.execute.own`, `lms05.manage`, `lms05.manage.own`, `lms05.review`, `lms05.review.own`, `lms05.view`, `lms05.view.all`, `lms05.view.own`, `lms05.write`, `lms05.write.own`, `lms06.execute.own`, `lms06.manage`, `lms06.manage.own`, `lms06.review`, `lms06.review.own`, `lms06.view`, `lms06.view.all`, `lms06.view.own`, `lms06.write`, `lms06.write.own`, `lms07.manage`, `lms07.review`, `lms07.view`, `lms07.view.all`, `lms07.view.own`, `lms07.write`, `lms07.write.own`, `lms08.manage`, `lms08.review`, `lms08.view`, `lms08.view.all`, `lms08.view.own`, `lms08.write`, `lms08.write.own`, `lms09.approve`, `lms09.approve.own`, `lms09.manage`, `lms09.review`, `lms09.review.own`, `lms09.view`, `lms09.view.all`, `lms09.view.own`, `lms09.write`, `lms09.write.own`, `lms10.approve`, `lms10.manage`, `lms10.review`, `lms10.view`, `lms10.view.all`, `lms10.view.own`, `lms10.write`, `lms10.write.own`

---

## 3. Roles

### Present catalogue roles (**R01**–**R39** as found)

| Catalogue | Template `code` | Keys granted (template) | Notes |
|---|---|---:|---|
| **R01** Super Admin | _(no `role_templates` row)_ | **0** via templates | Lives in `plat.platform_admins.role_code = SUPER_ADMIN`; Java/`is_platform_admin` bypass |
| **R02** System Admin | `SYSTEM_ADMIN` | 46 | Also may appear in `plat.platform_admins` as `SYSTEM_ADMIN` |
| **R03** Institution Admin | `INSTITUTION_ADMIN` | 94 | |
| **R04** College Admin | `COLLEGE_ADMIN` | 141 | |
| **R05** Principal | `PRINCIPAL` | 231 | |
| **R06** Vice Principal | `VICE_PRINCIPAL` | 173 | |
| **R07** Dean | `DEAN` | 153 | |
| **R08** HOD | `HOD` | 185 | |
| **R09** Program Coordinator | `PROGRAM_COORDINATOR` | 85 | |
| **R10** Faculty | `FACULTY` | 128 | |
| **R11** Section Coordinator | `SECTION_COORDINATOR` | 127 | |
| **R12** Exam Controller | `EXAM_CONTROLLER` | 80 | |
| **R13** Exam Cell | `EXAM_CELL` | 65 | |
| **R14** Valuator | `VALUATOR` | 32 | |
| **R15** Finance Manager | `FINANCE_MANAGER` | 87 | |
| **R16** Accountant | `ACCOUNTANT` | 58 | |
| **R17** HR Manager | `HR_MANAGER` | 73 | |
| **R18** HR Officer | `HR_OFFICER` | 50 | |
| **R19** Comm Manager | `COMM_MANAGER` | 72 | |
| **R20** Placement Officer | `PLACEMENT_OFFICER` | 15 | |
| **R21** Librarian | `LIBRARIAN` | 15 | |
| **R22** Hostel Warden | `HOSTEL_WARDEN` | 15 | |
| **R23** Transport Manager | `TRANSPORT_MANAGER` | 15 | |
| **R24** Facility Manager | `FACILITY_MANAGER` | 15 | |
| **R25** Student | `STUDENT` | 30 | |
| **R26** Parent | `PARENT` | 10 | |
| **R27** API Client | _(absent)_ | — | Parked; denied in `GrantorCeiling.java` |
| **R28** Auditor | `AUDITOR` | 65 | |
| **R29** Report User | `REPORT_USER` | 4 | |
| **R30** Guest | `GUEST` | 0 | |
| **R31** Registrar | `REGISTRAR` | 18 | |
| **R32** Applicant | `APPLICANT` | 1 | |
| **R33** Admissions Officer | `ADMISSIONS_OFFICER` | 2 | |
| **R34** Department Staff | `DEPARTMENT_STAFF` | 0 | |
| **R35** Academic Records Officer | `ACADEMIC_RECORDS_OFFICER` | 0 | |
| **R36** Document Verifier | `DOCUMENT_VERIFIER` | 0 | |
| **R37** External Verifier | `EXTERNAL_VERIFIER` | 0 | |
| **R38** IAM Operator | `IAM_OPERATOR` | 0 | Grant rules allow STUDENT/PARENT |
| **R39** Faculty Advisor | `FACULTY_ADVISOR` | 0 | |
| EXTRA | `ACADEMIC_ADMIN` | 122 | Not Rnn |
| EXTRA | `EMPLOYEE` | 16 | Not Rnn |

### `iadm*` keys held by focus personas (from `iam.role_template_permissions`)

**Super Admin (R01)** — none in templates (platform operator; not keyed via `iadm*`).

**System Admin (R02)** —  
`iadm04.audit.view`, `iadm04.entitlements.check`, `iadm04.payments.callback.retry`, `iadm04.plans.view`, `iadm04.reports.view`, `iadm04.subscriptions.view`, `iadm05.audit.view`, `iadm05.classification.propose`, `iadm05.classification.view`, `iadm05.erasure.decide`, `iadm05.erasure.request`, `iadm05.export.request`, `iadm05.policy.history`, `iadm05.privacy.propose`, `iadm05.privacy.view`, `iadm05.requests.operate`, `iadm05.retention.propose`, `iadm05.retention.view`, `iadm05.sharing.propose`, `iadm05.sharing.view`, `iadm06.catalogue.view`, `iadm06.entitlement.view`, `iadm06.export.request`, `iadm06.export.view`, `iadm06.institute.view`, `iadm06.platform.view`, `iadm07.audit.view`, `iadm07.exception.request`, `iadm07.posture.view`, `iadm07.register.read`, `iadm07.register.write`, `iadm07.report.view`, `iadm07.standard.propose`, `iadm08.alerts.view`, `iadm08.audit.institute.view`, `iadm08.audit.platform.view`, `iadm08.health.view`

**Institution Admin (R03)** —  
`iadm04.invoices.view.own`, `iadm04.plan_change.request`, `iadm04.reports.view`, `iadm04.subscriptions.view.own`, `iadm05.adherence.view`, `iadm05.classification.view`, `iadm05.policy.history`, `iadm05.privacy.view`, `iadm05.retention.view`, `iadm05.sharing.view`, `iadm06.catalogue.view`, `iadm06.entitlement.view`, `iadm06.export.request`, `iadm06.export.view`, `iadm06.institute.view`, `iadm07.audit.view`, `iadm07.exception.request`, `iadm07.posture.view`, `iadm07.register.read`, `iadm07.register.write`, `iadm07.report.view`, `iadm08.alerts.view`, `iadm08.audit.institute.view`

**College Admin (R04)** —  
`iadm05.classification.view`, `iadm05.college.view`, `iadm05.privacy.view`, `iadm05.retention.view`, `iadm07.summary.view`

**Auditor (R28)** —  
`iadm04.audit.view`, `iadm04.plans.view`, `iadm04.reports.view`, `iadm04.subscriptions.view`, `iadm05.audit.view`, `iadm05.classification.view`, `iadm05.policy.history`, `iadm05.privacy.view`, `iadm05.retention.view`, `iadm05.sharing.view`, `iadm06.catalogue.view`, `iadm06.entitlement.view`, `iadm06.institute.view`, `iadm06.platform.view`, `iadm07.audit.view`, `iadm07.posture.view`, `iadm07.register.read`, `iadm07.report.view`, `iadm08.alerts.view`, `iadm08.audit.institute.view`, `iadm08.audit.platform.view`, `iadm08.health.view`

---

## 4. Grant history

| Mechanism | Records who / when? | Assessment |
|---|---|---|
| `iam.role_permissions` | `created_at`, `created_by` only (no `updated_at`) | Thin insert trail; bulk loads often leave `created_by` null |
| `iam.role_template_permissions` | `created_at`, `created_by` | Almost entirely migration-seeded (`created_by` filled on ~1 of 2223 active rows) |
| `iam.role_assignments` | `granted_by`, `created_at`, `created_by` | Yes for live role assign/revoke |
| `iam.access_requests` | `requester_user_id`, `decided_by`, `decided_at` | Yes for request/decide path |
| `iam.catalog_change_requests` | `proposed_by`/`proposed_at`, `decided_by`/`decided_at`, `applied_at` | Yes for catalogue grant changes |
| `audit.events` via `audit.log_event` | `principal_id`, `event_time`, `action`, `detail` | Yes for IADM-03 mutations that call `DurableAuditWriter` |
| Foundation key migrations | SQL files in `database/migrations/` | **Most catalogue key inserts and template grants are migrations-only** |

**Summary:** Operational grant/decide paths can record actor + time (assignments, access requests, catalogue-change requests, shared audit). The large permission catalogue and template maps are **predominantly migrations-only**, not a full “who changed this key grant” ledger.

---

## 5. Guards (RBAC §21.1)

| Guard | Present? | Where |
|---|---|---|
| **No self-grant** | Yes (code + DB) | `GrantorCeiling.java` (`selfGrant` → deny); `iam.guard_role_assignment` trigger (“you cannot change your own role assignments”); grant services pass `self` when subject = caller |
| **No self-approval** | Yes (code + data columns) | `IADM03_SELF_APPROVE` in `CatalogChangeGovernanceService.java`, `AccessRequestGovernanceService.java`, `GrantGovernanceService.java`; tables keep distinct `proposed_by`/`requester_user_id` vs `decided_by` |
| **No inheritance** (permissions do not cascade via hierarchy) | Effectively yes at runtime | `iam.role_hierarchy` **exists** (9 parent/child rows) but `iam.grant_scopes` / `iam.has_permission` **do not walk it** — keys come only from assigned roles’ `role_permissions` (plus wildcards/`*`). Ceiling “who may assign whom” is encoded in `GrantorCeiling.java` + `iam.role_grant_rules` / `iam.can_grant_role`, not via hierarchy inheritance |

Additional DB guards: `iam.guard_role_permission`, `iam.guard_delegation`, `iam.guard_group_member`, `iam.guard_role`, `iam.guard_transition`.

---

## 6. Which services insert their own keys (migration order)

Chronological by migration filename (DEV-applied pattern). IADM-01 does **not** insert `iadm01.*` keys.

| Order | Migration file | Service / owner | Keys inserted |
|---:|---|---|---|
| 1 | `20261107000200_rbac_stu_catalogue_addendum.sql` | STM / RBAC addendum | `stu*` (partial) |
| 2 | `20261107000500_rbac_stu04_catalogue_addendum.sql` | STM-04 lifecycle | more `stu04.*` |
| 3 | `20261123010000_iadm02_foundation.sql` | **IADM-02** | `cfg.*` (new) |
| 4 | `20261124100000_iadm03_foundation.sql` | **IADM-03** | `iam.*` (20) |
| 5 | `20261125110000_iadm04_foundation_keys.sql` | **IADM-04** | `iadm04.*` |
| 6 | `20261125170000_iadm05_foundation_keys.sql` | **IADM-05** | `iadm05.*` |
| 7 | `20261125180000_iadm06_foundation_keys.sql` | **IADM-06** | `iadm06.*` |
| 8 | `20261126130000_iadm07_foundation_keys.sql` | **IADM-07** | `iadm07.*` |
| 9 | `20261126180000_iadm08_foundation_keys.sql` | **IADM-08** | `iadm08.*` |

**Not in this dated INSERT set:** the large base catalogue (`adm*`, `acd*`, `cam*`, `exm*`, `fin*`, `hrm*`, `lms*`, `audit*`, `core*`, `doc*`, `plat*`, `rpt*`, `wf*`, `*`) — present in DEV but loaded by earlier bootstrap / seed outside these IADM-dated migrations (see also `database/migrations/Scripts_V3.0/rbac_*.csv`).

---

## 7. Gaps and surprises (plain language)

1. **No `iadm01.*` / `iadm02.*` / `iadm03.*` literal prefixes** — IADM-02 uses `cfg.*`, IADM-03 uses `iam.*`, IADM-01 uses platform tables rather than permission keys.
2. **Super Admin (R01) has no role template and zero template keys** — authority is `plat.platform_admins` + Java checks, not `iam.has_permission` alone (`grant_scopes` does not auto-include platform admins).
3. **API Client (R27) is missing** from `iam.role_templates`; denied in code as parked.
4. **Several late catalogue roles (R34–R39, R30) have zero keys** — templates exist, grants empty.
5. **Wildcard `*` exists** in the live catalogue — powerful if ever granted to a tenant role.
6. **`iam.role_hierarchy` looks like inheritance but permission checks ignore it** — hierarchy is documentation/org chart data unless something else reads it.
7. **`adm03.*` keys still sit beside IADM-03’s `iam.*` keys** — two naming generations for “admin/IAM” concepts; `adm03` is not what IADM-03 services check.
8. **Template grant history is mostly migrations-only** — `created_by` nearly always null on `role_template_permissions`.
9. **Institution Admin holds `iadm07.register.write` and several operational keys** — broader than a pure “view own institute” mental model; worth reconciling with product intent.
10. **College Admin (R04) holds five `iadm*` view keys** despite IADM desks often treating R04 as denied for platform ops — matrix vs desk product rules may diverge.

---

## 8. Existing delegation contract & IADM-02 settings

- **Delegation table (`iam.delegations`):** Already exists in DEV with columns `id`, `tenant_id`, `from_user_id`, `to_user_id`, `role_id`, `valid_from`, `valid_to`, `reason`, `status`, `created_at`, `updated_at`, `created_by`, `updated_by`, `row_version`, `deleted_at`, guarded by `iam.guard_delegation`.
- **IADM-02 delegation setting:** `iam.delegation.max_validity_days` (`INTEGER`, default `90`) exists in `cfg.setting_definitions` and is readable via `/api/v1/iadm/02/consumer/effective-config?domains=iam,security`.
- **IADM-02 review cadence setting:** **None found** in `cfg.setting_definitions` (`%review%` / `%cadence%` returned 0 rows). IADM-03 will use a `DEMO ONLY` runtime default (`90` days) for review campaign due-date / cadence.

---

## 9. First-admin appointment & CADM-02 handover

- **First-admin appointment:** `plat.iadm01_create_first_admin_profile(...)` (in `20261122010400_iadm01_create_first_admin_profile.sql`) and `plat.iadm01_onboarding_steps` track tenant onboarding and first Institution Admin provisioning.
- **CADM-02 handover:** `plat.college_cadm_handovers` already exists in DEV (1 row present) with columns `id`, `tenant_id`, `college_id`, `first_college_admin_user_id`, `handed_over_by`, `handed_over_at`, `status`, `cadm01_ack_at`, `cadm01_ack_by`, `cadm01_interim`, `cadm02_ack_at`, `cadm02_ack_by`, `cadm02_interim`, `notes`.
- **Conclusion:** Existing sources exist in `plat.college_cadm_handovers` and `plat.iadm01_onboarding_steps`; IADM-03 reads handover status read-only without creating a duplicate handover table or appointment mechanism.

---

## 10. RBAC v3.0 matrix location & §8 cell verification

- **Authoritative file:** `D:\CampXSync\Documentation\OverAllArchitecture\CampXSync_Enterprise_RBAC_Matrix_IADM_CADM_v3_0.md`
- **§8 IADM-03 row (verified):**
  - **R01 (Super Admin):** `M`
  - **R02 (System Admin):** `M`
  - **R03 (Institution Admin):** `M within institute ceiling`
  - **R04 (College Admin):** `request only` (exact cell confirmed; no difference from Blueprint V1.1)
  - **R28 (Auditor):** `V`

---

## 11. Conventions of IADM-01 to IADM-08

| Service | Port | Route prefix | Key family | Schema |
|---|---|---|---|---|
| IADM-01 Tenant Lifecycle | `8099` | `/api/v1/iadm/01` | `plat.*` + platform-admin | `plat` |
| IADM-02 Platform Config | `8100` | `/api/v1/iadm/02` | `cfg.*` | `cfg` |
| IADM-03 Identity & Access (existing scaffold) | `8101` | `/api/v1/iadm/03` | `iam.*` (existing) + `iadm03.*` (new additive) | `iam` (existing) + `iam` / `iadm03` |
| IADM-04 Billing & Entitlements | `8102` | `/api/v1/iadm/04` | `iadm04.*` | `plat` |
| IADM-05 Data Governance | `8103` | `/api/v1/iadm/05` | `iadm05.*` | `gov` |
| IADM-06 Platform Analytics | `8104` | `/api/v1/iadm/06` | `iadm06.*` | `anl` |
| IADM-07 Security & Compliance | `8105` | `/api/v1/iadm/07` | `iadm07.*` | `sec` |
| IADM-08 Audit & Health Ops | `8106` | `/api/v1/iadm/08` | `iadm08.*` | _(none)_ |

- **Port & route choice:** `8101` and `/api/v1/iadm/03` are already wired in `api-gateway/GatewayConfig.java`, `scripts/dev/start_stack.ps1`, `scripts/dev/check_stack.ps1`, and `scripts/dev/restart_service.ps1` (next unused port after `8106` is `8107`, but `8101` is already dedicated to IADM-03 with no collision).
- **Demo-persona pattern:** `.env.audit` (`PLATFORM_ADMIN_*`, `COLLEGE_ADMIN_*`) + `docs/iadm/iadm-02/evidence/.local/seed_accounts.json` (`r01a`, `r01b`, `r02`, `auditor`, `r03demo`, `consumer`).

---

## 12. Demo institutes & personas (no PII / no secrets)

- **Active tenants (`core.tenants`):** `DEV_TEST_TENANT`, `T_FOREIGN`, `IADM04_DEMO`, `MAC`, `MWC`, `APEX_SCI`, `BEC`, `INST-DEMO-001`, `S1D175116`, `S1E175304`, `S1F175540`, `E2E-STM02-TENANT`.
- **Platform operators (`plat.platform_admins`):** 2 `SUPER_ADMIN` (`R01`) accounts (`r01a`, `r01b`) and 1 `SYSTEM_ADMIN` (`R02`) account.
- **Tenant role holders (`iam.role_assignments`):**
  - `INSTITUTION_ADMIN` (`R03`): 1 each in `DEV_TEST_TENANT`, `IADM04_DEMO`, `MAC`, `MWC`, `S1D175116`, `S1E175304`, `S1F175540`.
  - `COLLEGE_ADMIN` (`R04`): `DEV_TEST_TENANT` (8), `APEX_SCI` (1), `S1F175540` (1).
  - `AUDITOR` (`R28`): `MAC` (1).
- **Shared audit (`audit.log_event` / `audit.events`):** Verified present in DEV (`audit.log_event` routine exists; `audit.events` has 714 rows).
- **DB target confirmed:** DEV Supabase project `azulbaxszsqxymqmlwfe` (`postgres`).

---

*End of inventory. Source: DEV SQL read-backs on `azulbaxszsqxymqmlwfe` + repository file inspection. No secrets included.*
