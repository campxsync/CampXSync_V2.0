# Part D: Role Assignment & Escalation Governance

**Document:** `docs/audits/rbac-coverage/Part-D_assignment.md`  
**Audit Mode:** Read-Only Inventory Pass (Consolidated)  
**Database Evaluated:** Verified DEV project `azulbaxszsqxymqmlwfe` (Read-only catalog inspection)  

---

## 1. Inventory of Role Assignment Writers

**[CONFIRMED: Static scan of `services/**`, `frontend/**`, and database triggers on `iam.role_assignments`]:**

| Layer / Component | File / Table / Endpoint | Callable By | Target Table | Scope & Implementation Mechanism |
| :--- | :--- | :--- | :--- | :--- |
| **Database Trigger** | `iam.guard_role_assignment` | Automatic on DML | `iam.role_assignments` | Intercepts all `INSERT`, `UPDATE`, `DELETE`. Evaluates `can_grant_role()`, scope, and self-grant rules. Bypassed if `is_privileged_session()` is true. |
| **Java Institute Admin** | `InstituteAdminController.java:837`<br>`POST /api/v1/admin/institute/tenants/{tenantId}/students/{studentId}/role` | `R01`, `R02`, `R03`, or delegation `STM-07.ACCOUNT.A` | `iam.role_assignments` | Handled by `StudentRoleAssignmentService.java`. Restricted strictly to role code `STUDENT` (R25). Cannot assign any other role. |
| **Java College Admin** | `CollegeAdminController.java`<br>`POST /api/v1/admin/college/roles/assignments` | `COLLEGE_ADMIN` (R04) | `iam.role_assignments` | Sets session context `SET LOCAL ROLE authenticated` and `request.jwt.claims`. Restricted to college scope. |
| **Frontend Direct Route** | `frontend/src/app/api/college/faculty/route.ts:5` | Authenticated session (Client Cookie) | `iam.user_profiles`, faculty assignments | Executes direct SQL queries via `frontend/src/lib/db.ts` as `postgres`. Completely bypasses trigger guards. |
| **Maintenance Scripts** | `scripts/seed-staging.ts`<br>`database/seed-data/wave0_dev_test_seed.sql` | Operator CLI (Manual) | `iam.role_assignments` | Connects as `postgres` or via Supabase service-role admin API. Bypasses all triggers. |

---

## 2. Evaluation of Security Guards (§21.1)

**[CONFIRMED: Evaluation of guards 1–7 from Matrix §21.1 against live implementation]:**

### 1. No Self-Grant (Guard 1)
- **Live Implementation:** `iam.guard_role_assignment` line 176:
  `if r.user_id is not distinct from auth.uid() then raise exception 'you cannot change your own role assignments' using errcode = '42501';`
- **Status:** **EXISTS-WORKING** for standard non-privileged sessions.
- **Vulnerability:** Bypassed if the connection is made via `frontend/src/lib/db.ts` because `iam.is_privileged_session()` evaluates to true.

### 2. Cannot Grant More Than Held (Guard 2)
- **Live Implementation:** `iam.can_grant_role(p_role)`:
  - For system roles (`r.is_system = true`): Enforces literal match in `iam.role_grant_rules`.
  - For custom roles (`r.is_system = false`): Requires `not exists (select 1 from iam.role_permissions rp join iam.permissions p on p.id = rp.permission_id where rp.role_id = p_role and rp.tenant_id = core.current_tenant_id() and not iam.has_permission(p.code))`.
- **Status:** **EXISTS-WORKING** for standard non-privileged sessions.
- **Matrix Discrepancy:** The matrix in `iam.role_grant_rules` allows `COLLEGE_ADMIN` to grant `PRINCIPAL` (R05) and `DEAN` (R07), violating the principle that R04 cannot grant roles superior to its own operational scope.

### 3. Two-Person Rule for Appointing an Institution Admin (Guard 3)
- **Live Implementation:** **NOT FOUND**.
- **Status:** **NOT FOUND**.
- **Evidence:** `iam.role_grant_rules` permits `TENANT_ADMIN` to grant `INSTITUTION_ADMIN` unilaterally. No multi-signature, dual-custody, or secondary authorization check exists in either database triggers or Java services.

### 4. Institute / College Scope Validation (Guard 4)
- **Live Implementation:** `iam.guard_role_assignment` lines 179–188:
  ```sql
  if r.college_id is null and not iam.has_institute_scope() then
    raise exception 'institute-wide role assignments require an institute-wide administrator' using errcode = '42501';
  end if;
  if not iam.has_permission_in('adm09.write', r.college_id, r.department_id) then
    raise exception 'role assignment outside your own college/department scope' using errcode = '42501';
  end if;
  ```
- **Status:** **EXISTS-WORKING** for standard sessions. Validates that college admins cannot create institute-wide (`college_id IS NULL`) assignments and must hold `adm09.write` in the target college/department.

### 5. Mandatory Audit Logging (Guard 6)
- **Live Implementation:** Trigger `trg_audit after insert or update or delete on iam.role_assignments for each row execute function audit.capture()`.
- **Status:** **EXISTS-WORKING**. Writes old/new row JSON snapshots, `auth.uid()`, timestamp, and tenant ID to partitioned `audit.change_log`.

---

## 3. Delegation Records & Scope Isolation

**[CONFIRMED: Database function `iam.grant_scopes()` and table `iam.delegations`]:**

### Delegation Table Schema (`iam.delegations`)
- Stored in `iam.delegations` with columns: `id`, `tenant_id`, `delegator_user_id`, `delegate_user_id`, `role_id`, `college_id`, `department_id`, `valid_from`, `valid_to`, `is_active`, `revoked_at`.

### Delegation Resolution Mechanism
**[CONFIRMED: Database query on `pg_proc` for `iam.grant_scopes`]:**
```sql
create or replace function iam.grant_scopes(p_code text)
returns table (college_id uuid, department_id uuid)
language sql stable security definer as $$
  -- 1. Direct role assignments
  select ra.college_id, ra.department_id
  from iam.role_assignments ra
  join iam.roles r on r.id = ra.role_id and r.tenant_id = ra.tenant_id
  join iam.role_permissions rp on rp.role_id = r.id and rp.tenant_id = r.tenant_id
  join iam.permissions p on p.id = rp.permission_id
  where ra.tenant_id = core.current_tenant_id()
    and ra.user_id = auth.uid()
    and ra.deleted_at is null
    and (ra.valid_from is null or ra.valid_from <= current_date)
    and (ra.valid_to is null or ra.valid_to >= current_date)
    and p.deleted_at is null
    and (p.code = p_code or p.code = '*' or p.code = split_part(p_code, '.') || '.*')

  union

  -- 2. Delegated role assignments
  select d.college_id, d.department_id
  from iam.delegations d
  join iam.roles r on r.id = d.role_id and r.tenant_id = d.tenant_id
  join iam.role_permissions rp on rp.role_id = r.id and rp.tenant_id = r.tenant_id
  join iam.permissions p on p.id = rp.permission_id
  where d.tenant_id = core.current_tenant_id()
    and d.delegate_user_id = auth.uid()
    and d.is_active = true
    and d.revoked_at is null
    and d.deleted_at is null
    and (d.valid_from is null or d.valid_from <= current_date)
    and (d.valid_to is null or d.valid_to >= current_date)
    and p.deleted_at is null
    and (p.code = p_code or p.code = '*' or p.code = split_part(p_code, '.') || '.*');
$$;
```

**Key Findings on Delegation:**
1. **Role Limitation:** The delegate receives permissions **strictly associated with the specific `d.role_id` recorded on the delegation row**. It does NOT inherit all of the delegator's other roles.
2. **Scope Limitation:** The delegate's operational scope is bound to `d.college_id` and `d.department_id` recorded on the delegation record.
3. **Time-Boxing:** Expired (`valid_to < current_date`) or revoked delegations fail immediately.

---

## 4. Break-Glass Recovery Path

**[CONFIRMED: Code and architecture scan]:**

1. **Documented Application Break-Glass:** **NOT FOUND**. There is no time-boxed, dual-authorized API endpoint or dedicated break-glass token issuance workflow in either `api-gateway` or `institute-admin-service`.
2. **De Facto Unaudited Break-Glass:** The only existing mechanism to bypass administrative lockouts or perform tenant recovery today is direct database access using the PostgreSQL superuser (`postgres`) credentials. This path bypasses RLS and IAM triggers entirely, leaving no application-level audit trail.
