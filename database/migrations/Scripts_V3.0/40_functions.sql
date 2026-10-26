-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.
-- ============================================================ generic row triggers
create or replace function sys.touch_row() returns trigger language plpgsql as $$
begin
  new.updated_at := now();
  new.updated_by := auth.uid();
  new.row_version := coalesce(old.row_version, 0) + 1;   -- optimistic concurrency: client sends row_version in WHERE
  return new;
end $$;

create or replace function sys.touch_row_lean() returns trigger language plpgsql as $$
begin new.updated_at := now(); return new; end $$;

create or replace function sys.forbid_mutation() returns trigger language plpgsql as $$
begin
  raise exception 'table %.% is append-only (immutable)', tg_table_schema, tg_table_name using errcode = 'restrict_violation';
end $$;

-- ============================================================ identity / authorisation helpers
-- true for migrations / service_role / superuser sessions; false for Data API users (PostgREST switches to authenticated / anon)
create or replace function iam.is_privileged_session() returns boolean
language sql stable as $$ select current_user not in ('authenticated', 'anon') $$;

create or replace function iam.is_platform_admin() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from plat.platform_admins p
                 where p.user_id = auth.uid() and p.status = 'ACTIVE' and p.deleted_at is null)
$$;

create or replace function core.can_access_college(p_college uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select p_college is null
      or exists (select 1 from iam.role_assignments ra
                 where ra.tenant_id = core.current_tenant_id() and ra.deleted_at is null
                   and (ra.valid_from is null or ra.valid_from <= current_date)
                   and (ra.valid_to   is null or ra.valid_to   >= current_date)
                   and (ra.user_id = auth.uid()
                        or ra.group_id in (select gm.group_id from iam.user_group_members gm
                                           where gm.user_id = auth.uid() and gm.tenant_id = ra.tenant_id))
                   and (ra.college_id is null or ra.college_id = p_college))
      or exists (select 1 from iam.user_profiles up
                 where up.id = auth.uid() and up.tenant_id = core.current_tenant_id() and up.college_id = p_college)
$$;

create or replace function core.current_person_id() returns uuid
language sql stable security definer set search_path = '' as $$
  select up.person_id from iam.user_profiles up
  where up.id = auth.uid() and up.tenant_id = core.current_tenant_id() and up.deleted_at is null
$$;

create or replace function core.current_student_id() returns uuid
language sql stable security definer set search_path = '' as $$
  select s.id from stu.students s
  where s.person_id = core.current_person_id() and s.tenant_id = core.current_tenant_id() and s.deleted_at is null
  order by (s.status = 'ACTIVE') desc, s.created_at desc limit 1
$$;

create or replace function core.current_employee_id() returns uuid
language sql stable security definer set search_path = '' as $$
  select e.id from hrm.employees e
  where e.person_id = core.current_person_id() and e.tenant_id = core.current_tenant_id() and e.deleted_at is null
  order by (e.status = 'ACTIVE') desc, e.created_at desc limit 1
$$;

-- ============================================================ audit trigger (redacting)
create or replace function audit.sanitize(p_row jsonb, p_schema text, p_table text) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare
  v_mode text; v_cols text[]; r jsonb := '{}'::jsonb; k text; v jsonb;
  c_sensitive constant text := '(^|_)(email|phone|mobile|dob|date_of_birth|address|line1|line2|landmark|postal_code|latitude|longitude|ciphertext|hash|password|secret|token|ifsc|blood_group|nationality|marital_status|photo)($|_)|^(masked_value|account_last4|bank_last4|account_holder)$';
begin
  if p_row is null then return null; end if;
  select f.mode, f.columns into v_mode, v_cols from audit.field_policies f
   where f.table_schema = p_schema and f.table_name = p_table and f.deleted_at is null;
  if v_mode = 'METADATA_ONLY' then
    return jsonb_build_object('id', p_row -> 'id', 'tenant_id', p_row -> 'tenant_id');
  end if;
  for k, v in select * from jsonb_each(p_row) loop
    if v_mode = 'ALLOWLIST' then
      if k = any (v_cols) or k in ('id', 'tenant_id') then r := r || jsonb_build_object(k, v); end if;
    elsif k ~* c_sensitive and v <> 'null'::jsonb then
      r := r || jsonb_build_object(k, '[REDACTED]');
    else
      r := r || jsonb_build_object(k, v);
    end if;
  end loop;
  return r;
end $$;
comment on function audit.sanitize is 'Applies audit.field_policies (default: redact personal/contact/secret-looking columns) before anything reaches audit.change_log';

create or replace function audit.capture() returns trigger
language plpgsql security definer set search_path = '' as $$
declare
  v_old jsonb; v_new jsonb; v_id uuid; v_tenant uuid; v_changed text[];
  v_noise text[] := array['updated_at','updated_by','row_version'];
begin
  if tg_op = 'INSERT' then
    v_new := to_jsonb(new); v_id := (v_new ->> 'id')::uuid;
  elsif tg_op = 'UPDATE' then
    v_old := to_jsonb(old); v_new := to_jsonb(new); v_id := (v_new ->> 'id')::uuid;
    select coalesce(array_agg(e.key order by e.key), '{}') into v_changed
      from jsonb_each(v_new) e where v_old -> e.key is distinct from e.value and not (e.key = any (v_noise));
    if coalesce(array_length(v_changed, 1), 0) = 0 then return new; end if;
  else
    v_old := to_jsonb(old); v_id := (v_old ->> 'id')::uuid;
  end if;
  v_tenant := coalesce((coalesce(v_new, v_old) ->> 'tenant_id')::uuid, case when tg_table_name = 'tenants' then v_id end);
  insert into audit.change_log (tenant_id, table_schema, table_name, record_id, action, changed_fields,
                                old_data, new_data, actor_id, request_id)
  values (v_tenant, tg_table_schema, tg_table_name, v_id, left(tg_op, 1), v_changed,
          audit.sanitize(v_old, tg_table_schema, tg_table_name), audit.sanitize(v_new, tg_table_schema, tg_table_name),
          auth.uid(), nullif(current_setting('request.headers', true), '')::jsonb ->> 'x-request-id');
  return coalesce(new, old);
end $$;

-- Trusted writer for semantic audit events. service_role ONLY (Edge Functions / server code); clients cannot insert audit rows.
create or replace function audit.log_event(
  p_tenant uuid, p_principal uuid, p_action text, p_resource_type text default null, p_resource_id uuid default null,
  p_status text default null, p_severity text default 'INFO', p_ip inet default null, p_detail jsonb default null, p_college uuid default null)
returns uuid language plpgsql security definer set search_path = '' as $$
declare v_id uuid;
begin
  insert into audit.events (tenant_id, college_id, action, principal_id, resource_type, resource_id, status, severity, ip, detail)
  values (p_tenant, p_college, p_action, p_principal, p_resource_type, p_resource_id, p_status, p_severity, p_ip, p_detail)
  returning id into v_id;
  return v_id;
end $$;

-- ============================================================ numbering + settings
create or replace function core.next_number(p_scope text, p_college uuid default null) returns text
language plpgsql security definer set search_path = '' as $$
declare r core.number_sequences%rowtype; v bigint; reset boolean := false;
begin
  select * into r from core.number_sequences
   where tenant_id = core.current_tenant_id() and scope_key = p_scope
     and college_id is not distinct from p_college and deleted_at is null
   for update;
  if not found then raise exception 'number sequence "%" is not configured', p_scope; end if;
  if not iam.has_permission(coalesce(r.required_permission, 'core.write')) then
    raise exception 'permission denied for number sequence %', p_scope using errcode = '42501';
  end if;
  reset := case r.reset_policy
    when 'YEARLY'  then r.last_reset_on is null or extract(year from r.last_reset_on) <> extract(year from current_date)
    when 'MONTHLY' then r.last_reset_on is null or date_trunc('month', r.last_reset_on) <> date_trunc('month', current_date)
    else false end;
  v := case when reset then 1 else r.next_value end;
  update core.number_sequences set next_value = v + 1, last_reset_on = case when reset then current_date else last_reset_on end
   where id = r.id;
  return coalesce(r.prefix, '') || lpad(v::text, r.padding, '0') || coalesce(r.suffix, '');
end $$;
comment on function core.next_number is 'Gap-free per-tenant numbering (row lock). Call inside the same transaction that inserts the numbered row.';

create or replace function cfg.effective_setting(p_key text, p_college uuid default null) returns jsonb
language sql stable as $$
  select coalesce(
    (select v.value from cfg.setting_values v where v.setting_key = p_key and v.college_id = p_college and v.deleted_at is null),
    (select v.value from cfg.setting_values v where v.setting_key = p_key and v.college_id is null and v.deleted_at is null),
    (select d.default_value from cfg.setting_definitions d where d.setting_key = p_key and d.deleted_at is null))
$$;

-- ============================================================ role administration guards
create or replace function iam.has_institute_scope() returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from iam.role_assignments ra
                 where ra.tenant_id = core.current_tenant_id() and ra.deleted_at is null and ra.college_id is null
                   and (ra.valid_from is null or ra.valid_from <= current_date) and (ra.valid_to is null or ra.valid_to >= current_date)
                   and (ra.user_id = auth.uid() or ra.group_id in (select gm.group_id from iam.user_group_members gm
                                                                    where gm.user_id = auth.uid() and gm.tenant_id = ra.tenant_id)))
$$;

create or replace function iam.guard_role_assignment() returns trigger language plpgsql as $$
declare r iam.role_assignments := coalesce(new, old);
begin
  if iam.is_privileged_session() then return coalesce(new, old); end if;
  if r.user_id is not distinct from auth.uid() then
    raise exception 'you cannot change your own role assignments' using errcode = '42501';
  end if;
  if r.college_id is null and not iam.has_institute_scope() then
    raise exception 'institute-wide role assignments require an institute-wide administrator' using errcode = '42501';
  end if;
  if not iam.can_grant_role(r.role_id) then
    raise exception 'you may not grant or revoke this role' using errcode = '42501';
  end if;
  if not iam.has_permission_in('adm09.write', r.college_id, r.department_id) then
    raise exception 'role assignment outside your own college/department scope' using errcode = '42501';
  end if;
  return coalesce(new, old);
end $$;

create or replace function iam.guard_role_permission() returns trigger language plpgsql as $$
declare r iam.role_permissions := coalesce(new, old); v_system boolean; v_code text;
begin
  if iam.is_privileged_session() then return coalesce(new, old); end if;
  select ro.is_system into v_system from iam.roles ro where ro.id = r.role_id and ro.tenant_id = r.tenant_id;
  if coalesce(v_system, false) then raise exception 'system roles can only be changed by the platform' using errcode = '42501'; end if;
  select p.code into v_code from iam.permissions p where p.id = r.permission_id;
  if not iam.has_permission(v_code) then
    raise exception 'you cannot grant permission % because you do not hold it', v_code using errcode = '42501';
  end if;
  return coalesce(new, old);
end $$;

create or replace function iam.guard_role() returns trigger language plpgsql as $$
begin
  if iam.is_privileged_session() then return coalesce(new, old); end if;
  if tg_op = 'INSERT' and new.is_system then raise exception 'clients cannot create system roles' using errcode = '42501'; end if;
  if tg_op in ('UPDATE', 'DELETE') and old.is_system then raise exception 'system roles are managed by the platform' using errcode = '42501'; end if;
  if tg_op = 'UPDATE' and new.is_system then raise exception 'clients cannot mark a role as system' using errcode = '42501'; end if;
  return coalesce(new, old);
end $$;

create or replace function iam.guard_group_member() returns trigger language plpgsql as $$
declare r iam.user_group_members := coalesce(new, old);
begin
  if iam.is_privileged_session() then return coalesce(new, old); end if;
  if r.user_id = auth.uid() then raise exception 'you cannot change your own group membership' using errcode = '42501'; end if;
  if exists (select 1 from iam.role_assignments ra where ra.group_id = r.group_id and ra.tenant_id = r.tenant_id
             and ra.deleted_at is null and not iam.can_grant_role(ra.role_id)) then
    raise exception 'this group carries roles you are not allowed to grant' using errcode = '42501';
  end if;
  return coalesce(new, old);
end $$;

-- ============================================================ scoped access helpers (faculty)
create or replace function core.faculty_student_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select se.student_id from acd.section_enrollments se
  join acd.section_faculty sf on sf.section_id = se.section_id and sf.tenant_id = se.tenant_id
  where sf.tenant_id = core.current_tenant_id() and sf.employee_id = core.current_employee_id()
    and sf.deleted_at is null and se.deleted_at is null and se.status = 'ENROLLED'
    and (sf.valid_to is null or sf.valid_to >= current_date)
$$;

create or replace function core.own_attendance_session_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select s.id from acd.attendance_sessions s
  where s.tenant_id = core.current_tenant_id() and s.employee_id = core.current_employee_id() and s.deleted_at is null
$$;

-- ============================================================ financial posting API
create or replace function fin.allocate_payment(p_payment uuid, p_obligation uuid, p_amount numeric, p_item uuid default null) returns uuid
language plpgsql security definer set search_path = '' as $$
declare v uuid;
begin
  if not (iam.has_permission('fin02.execute') or iam.has_permission('fin02.manage')) then raise exception 'permission denied' using errcode = '42501'; end if;
  insert into fin.payment_allocations (tenant_id, payment_id, obligation_id, obligation_item_id, amount)
  values (core.current_tenant_id(), p_payment, p_obligation, p_item, p_amount) returning id into v;
  return v;
end $$;
comment on function fin.allocate_payment is 'Preferred entry point for posting an allocation; all invariants are also enforced by triggers on fin.payment_allocations';

create or replace function fin.reverse_allocation(p_allocation uuid, p_reason text) returns uuid
language plpgsql security definer set search_path = '' as $$
declare a fin.payment_allocations%rowtype; v uuid;
begin
  if not (iam.has_permission('fin02.execute') or iam.has_permission('fin02.manage')) then raise exception 'permission denied' using errcode = '42501'; end if;
  if coalesce(trim(p_reason), '') = '' then raise exception 'a reason is required to reverse an allocation'; end if;
  select * into a from fin.payment_allocations where id = p_allocation and tenant_id = core.current_tenant_id();
  if not found then raise exception 'allocation not found'; end if;
  insert into fin.payment_allocations (tenant_id, payment_id, obligation_id, obligation_item_id, amount, reverses_allocation_id, reason)
  values (a.tenant_id, a.payment_id, a.obligation_id, a.obligation_item_id, -a.amount, a.id, p_reason) returning id into v;
  return v;
end $$;

-- ============================================================ RBAC core: scoped grants
-- A "grant" = (permission, college, department) obtained through: direct/group role assignments, active delegations, or API-client grants.
--   college NULL + department NULL  => institute-wide
--   college set                      => that college
--   department set                   => that department only
create or replace function iam.grant_scopes(p_code text) returns table (college_id uuid, department_id uuid)
language sql stable security definer set search_path = '' as $$
  with me as (select auth.uid() as uid, core.current_tenant_id() as tid),
  assigned as (
    select ra.role_id, ra.college_id, ra.department_id
      from iam.role_assignments ra, me
     where ra.tenant_id = me.tid and ra.deleted_at is null
       and (ra.valid_from is null or ra.valid_from <= current_date) and (ra.valid_to is null or ra.valid_to >= current_date)
       and (ra.user_id = me.uid or ra.group_id in (select gm.group_id from iam.user_group_members gm where gm.user_id = me.uid and gm.tenant_id = me.tid))
    union all
    select ra.role_id, ra.college_id, ra.department_id
      from iam.delegations d
      join iam.role_assignments ra on ra.user_id = d.from_user_id and ra.tenant_id = d.tenant_id and ra.deleted_at is null and ra.role_id = d.role_id, me
     where d.tenant_id = me.tid and d.to_user_id = me.uid and d.deleted_at is null and d.status = 'ACTIVE'
       and now() between d.valid_from and d.valid_to
       and (ra.valid_from is null or ra.valid_from <= current_date) and (ra.valid_to is null or ra.valid_to >= current_date)
  )
  select a.college_id, a.department_id
    from assigned a
    join iam.role_permissions rp on rp.role_id = a.role_id and rp.tenant_id = (select tid from me)
    join iam.permissions p on p.id = rp.permission_id
   where p.deleted_at is null and (p.code = p_code or p.code = '*' or p.code = split_part(p_code, '.', 1) || '.*')
  union
  select g.college_id, g.department_id
    from iam.api_client_grants g
    join iam.api_clients c on c.id = g.client_id and c.tenant_id = g.tenant_id and c.status = 'ACTIVE' and c.deleted_at is null
    join iam.permissions p on p.id = g.permission_id, me
   where g.tenant_id = me.tid and c.user_id = me.uid and g.deleted_at is null and (g.valid_to is null or g.valid_to >= now())
     and p.deleted_at is null and (p.code = p_code or p.code = '*' or p.code = split_part(p_code, '.', 1) || '.*')
$$;
comment on function iam.grant_scopes is 'Every (college, department) scope in which the caller holds permission p_code. Wildcards: exact code, "<module>.*", "*".';

create or replace function iam.has_permission(p_code text) returns boolean
language sql stable security definer set search_path = '' as $$ select exists (select 1 from iam.grant_scopes(p_code)) $$;

create or replace function iam.perm_all(p_code text) returns boolean
language sql stable security definer set search_path = '' as $$
  select exists (select 1 from iam.grant_scopes(p_code) g where g.college_id is null and g.department_id is null) $$;

create or replace function iam.perm_colleges(p_code text) returns uuid[]
language sql stable security definer set search_path = '' as $$
  select coalesce(array(select g.college_id from iam.grant_scopes(p_code) g where g.college_id is not null and g.department_id is null), '{}'::uuid[]) $$;

create or replace function iam.perm_department_ids(p_code text) returns uuid[]
language sql stable security definer set search_path = '' as $$
  select coalesce(array(
    select g.department_id from iam.grant_scopes(p_code) g where g.department_id is not null
    union
    select d.id from core.departments d
     where d.tenant_id = core.current_tenant_id() and d.deleted_at is null
       and d.college_id in (select g.college_id from iam.grant_scopes(p_code) g where g.college_id is not null and g.department_id is null)
  ), '{}'::uuid[]) $$;

-- Row-level check used by triggers: does the caller hold p_code for a row in (p_college, p_department)?
create or replace function iam.has_permission_in(p_code text, p_college uuid, p_department uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select case when p_college is null and p_department is null then exists (select 1 from iam.grant_scopes(p_code))
  else exists (select 1 from iam.grant_scopes(p_code) g
    where (g.college_id is null and g.department_id is null)
       or (g.department_id is not null and g.department_id = p_department)
       or (g.department_id is null and g.college_id is not null and g.college_id = p_college)
       or (g.department_id is null and g.college_id is not null and p_department is not null
           and p_department in (select d.id from core.departments d where d.college_id = g.college_id))) end $$;

-- ============================================================ scope sets (definer; used by policies)
create or replace function core.programs_in_scope(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select p.id from acd.programs p where p.tenant_id = core.current_tenant_id()
     and (iam.perm_all(p_perm) or p.college_id = any (iam.perm_colleges(p_perm)) or p.department_id = any (iam.perm_department_ids(p_perm))) $$;

create or replace function core.courses_in_scope(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select c.id from acd.courses c where c.tenant_id = core.current_tenant_id()
     and (iam.perm_all(p_perm) or c.department_id = any (iam.perm_department_ids(p_perm)) or (not p_write and c.department_id is null)) $$;

create or replace function core.subjects_in_scope(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select s.id from acd.subjects s where s.tenant_id = core.current_tenant_id()
     and (iam.perm_all(p_perm) or s.department_id = any (iam.perm_department_ids(p_perm)) or (not p_write and s.department_id is null)) $$;

create or replace function core.sections_in_scope(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select s.id from acd.course_sections s
    join acd.course_offerings o on o.id = s.offering_id and o.tenant_id = s.tenant_id
    join acd.courses c on c.id = o.course_id and c.tenant_id = o.tenant_id
   where s.tenant_id = core.current_tenant_id()
     and (iam.perm_all(p_perm) or o.college_id = any (iam.perm_colleges(p_perm)) or c.department_id = any (iam.perm_department_ids(p_perm))) $$;

create or replace function core.students_in_scope(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select s.id from stu.students s where s.tenant_id = core.current_tenant_id()
     and (iam.perm_all(p_perm) or s.college_id = any (iam.perm_colleges(p_perm))
          or s.program_id in (select core.programs_in_scope(p_perm, p_write))) $$;

create or replace function core.employees_in_scope(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select e.id from hrm.employees e where e.tenant_id = core.current_tenant_id()
     and (iam.perm_all(p_perm) or e.college_id = any (iam.perm_colleges(p_perm)) or e.department_id = any (iam.perm_department_ids(p_perm))) $$;

-- ============================================================ self / enrolment / faculty-assignment helpers
create or replace function core.my_student_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select s.id from stu.students s where s.tenant_id = core.current_tenant_id() and s.person_id = core.current_person_id() and s.deleted_at is null
  union
  select g.student_id from stu.guardianships g
   where g.tenant_id = core.current_tenant_id() and g.guardian_person_id = core.current_person_id() and g.deleted_at is null
     and g.status = 'ACTIVE' and (g.valid_from is null or g.valid_from <= current_date) and (g.valid_to is null or g.valid_to >= current_date) $$;

create or replace function core.my_section_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select se.section_id from acd.section_enrollments se
   where se.tenant_id = core.current_tenant_id() and se.deleted_at is null and se.status = 'ENROLLED'
     and se.student_id in (select core.my_student_ids()) $$;

create or replace function core.my_course_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select o.course_id from acd.course_sections s join acd.course_offerings o on o.id = s.offering_id and o.tenant_id = s.tenant_id
   where s.tenant_id = core.current_tenant_id() and s.id in (select core.my_section_ids()) $$;

create or replace function core.my_subject_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select m.subject_id from acd.subject_course_map m where m.tenant_id = core.current_tenant_id() and m.course_id in (select core.my_course_ids()) $$;

create or replace function core.faculty_section_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select sf.section_id from acd.section_faculty sf
   where sf.tenant_id = core.current_tenant_id() and sf.employee_id = core.current_employee_id() and sf.deleted_at is null
     and (sf.valid_to is null or sf.valid_to >= current_date) $$;

create or replace function core.faculty_course_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select o.course_id from acd.course_sections s join acd.course_offerings o on o.id = s.offering_id and o.tenant_id = s.tenant_id
   where s.tenant_id = core.current_tenant_id() and s.id in (select core.faculty_section_ids()) $$;

create or replace function core.faculty_subject_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select m.subject_id from acd.subject_course_map m where m.tenant_id = core.current_tenant_id() and m.course_id in (select core.faculty_course_ids()) $$;

-- ============================================================ separation of duties + workflow transitions
create or replace function iam.guard_transition() returns trigger language plpgsql as $$
declare
  r iam.transition_rules%rowtype; v_new jsonb := to_jsonb(new); v_old jsonb; v_status text; v_old_status text;
  v_college uuid; v_dept uuid; v_write_perm text := tg_argv[0]; v_own_pred text := coalesce(tg_argv[1], 'false'); v_full boolean; v_changed text[]; v_allowed text[]; v_col text; v_who text; v_me uuid;
begin
  if iam.is_privileged_session() then return new; end if;
  if tg_op = 'UPDATE' then v_old := to_jsonb(old); end if;
  v_college := nullif(v_new ->> 'college_id', '')::uuid; v_dept := nullif(v_new ->> 'department_id', '')::uuid;
  v_allowed := array['updated_at','updated_by','row_version','approved_by','approved_at','published_at','published_by','finalized_by','finalized_at',
                     'workflow_instance_id','locked_at','verified_at','reviewed_by','reviewed_at','decided_at','closed_at','completed_at','generated_at'];
  for r in select * from iam.transition_rules x where x.table_schema = tg_table_schema and x.table_name = tg_table_name and x.deleted_at is null loop
    v_allowed := v_allowed || r.status_column;
    v_status := v_new ->> r.status_column; v_old_status := v_old ->> r.status_column;
    if v_status is distinct from v_old_status and v_status = r.to_status and (r.from_status is null or r.from_status = v_old_status) then
      if cardinality(r.any_permission) > 0 and not exists (select 1 from unnest(r.any_permission) p where iam.has_permission_in(p, v_college, v_dept)) then
        raise exception 'moving % to % requires one of: %', tg_table_name, r.to_status, array_to_string(r.any_permission, ', ') using errcode = '42501';
      end if;
      if r.block_self is not null then
        v_col := split_part(r.block_self, ':', 1); v_who := split_part(r.block_self, ':', 2);
        if (v_who = 'employee' and nullif(v_new ->> v_col, '')::uuid is not distinct from core.current_employee_id())
           or (v_who = 'student' and nullif(v_new ->> v_col, '')::uuid in (select core.my_student_ids()))
           or (v_who = 'user' and nullif(v_new ->> v_col, '')::uuid is not distinct from auth.uid()) then
          raise exception 'you cannot move your own % to %', tg_table_name, r.to_status using errcode = '42501';
        end if;
      end if;
      if cardinality(r.sod_distinct_from) > 0 and exists (
           select 1 from iam.transition_log l
            where l.table_schema = tg_table_schema and l.table_name = tg_table_name and l.record_id = (v_new ->> 'id')::uuid
              and l.status_column = r.status_column and l.to_status = any (r.sod_distinct_from) and l.actor_id = auth.uid()) then
        raise exception 'separation of duties: the person who handled % cannot also move it to %', array_to_string(r.sod_distinct_from, '/'), r.to_status
          using errcode = '42501';
      end if;
    end if;
  end loop;
  -- an actor who holds only workflow permissions (review/approve/publish/execute) may change workflow fields, nothing else.
  -- Full edit needs the broad write permission, or the ".own" permission on a row that is genuinely theirs.
  if tg_op = 'UPDATE' and v_write_perm <> 'NONE' then
    v_full := iam.has_permission_in(v_write_perm, v_college, v_dept);
    if not v_full and v_own_pred <> 'false' and iam.has_permission_in(v_write_perm || '.own', v_college, v_dept) then
      execute format('select exists (select 1 from %I.%I x where x.id = $1 and (%s))', tg_table_schema, tg_table_name, v_own_pred) into v_full using old.id;
    end if;
    if not v_full then
      select coalesce(array_agg(e.key), '{}') into v_changed from jsonb_each(v_new) e where v_old -> e.key is distinct from e.value;
      if exists (select 1 from unnest(v_changed) c where c <> all (v_allowed)) then
        raise exception 'you may only change workflow fields (status/approval) on this record' using errcode = '42501';
      end if;
    end if;
  end if;
  return new;
end $$;

create or replace function iam.log_transition() returns trigger language plpgsql security definer set search_path = '' as $$
declare r record; v_new jsonb := to_jsonb(new); v_old jsonb; v_to text; v_from text;
begin
  if tg_op = 'UPDATE' then v_old := to_jsonb(old); end if;
  for r in select distinct x.status_column from iam.transition_rules x where x.table_schema = tg_table_schema and x.table_name = tg_table_name and x.deleted_at is null loop
    v_to := v_new ->> r.status_column; v_from := v_old ->> r.status_column;
    if tg_op = 'INSERT' or v_to is distinct from v_from then
      insert into iam.transition_log (tenant_id, table_schema, table_name, record_id, status_column, from_status, to_status, actor_id)
      values ((v_new ->> 'tenant_id')::uuid, tg_table_schema, tg_table_name, (v_new ->> 'id')::uuid, r.status_column, v_from, v_to, auth.uid());
    end if;
  end loop;
  return null;
end $$;

-- ============================================================ who may assign which role (matrix section 12: "Role Assignment")
create or replace function iam.can_grant_role(p_role uuid) returns boolean
language sql stable security definer set search_path = '' as $$
  select case when r.is_system then
      exists (select 1 from iam.role_assignments ra
                join iam.roles gr on gr.id = ra.role_id and gr.tenant_id = ra.tenant_id
                join iam.role_grant_rules rr on rr.grantor_role = gr.code and rr.grantable_role = r.code
               where ra.tenant_id = core.current_tenant_id() and ra.user_id = auth.uid() and ra.deleted_at is null
                 and (ra.valid_from is null or ra.valid_from <= current_date) and (ra.valid_to is null or ra.valid_to >= current_date))
    else not exists (select 1 from iam.role_permissions rp join iam.permissions p on p.id = rp.permission_id
                      where rp.role_id = p_role and rp.tenant_id = core.current_tenant_id() and not iam.has_permission(p.code)) end
  from iam.roles r where r.id = p_role and r.tenant_id = core.current_tenant_id()
$$;

-- ============================================================ delegation guard
create or replace function iam.guard_delegation() returns trigger language plpgsql as $$
begin
  if iam.is_privileged_session() then return new; end if;
  if new.from_user_id <> auth.uid() and not iam.has_permission('adm09.write') then
    raise exception 'you can only delegate your own authority' using errcode = '42501';
  end if;
  if new.to_user_id = new.from_user_id then raise exception 'cannot delegate to yourself' using errcode = '23514'; end if;
  if new.valid_to - new.valid_from > interval '90 days' then raise exception 'a delegation may last at most 90 days' using errcode = '23514'; end if;
  if not exists (select 1 from iam.role_assignments ra where ra.tenant_id = new.tenant_id and ra.user_id = new.from_user_id and ra.role_id = new.role_id and ra.deleted_at is null) then
    raise exception 'you can only delegate a role you hold' using errcode = '42501';
  end if;
  return new;
end $$;

-- ============================================================ fee payment entry point for students / guardians (matrix section 14: "Own Fees V/X")
create or replace function fin.start_payment(p_obligation uuid, p_method uuid, p_amount numeric) returns uuid
language plpgsql security definer set search_path = '' as $$
declare o fin.fee_obligations%rowtype; v uuid; ok boolean;
begin
  select * into o from fin.fee_obligations where id = p_obligation and tenant_id = core.current_tenant_id();
  if not found then raise exception 'obligation not found'; end if;
  ok := coalesce(o.student_id in (select core.my_student_ids()) and (
          o.student_id = core.current_student_id()
          or exists (select 1 from stu.guardianships g where g.student_id = o.student_id and g.tenant_id = o.tenant_id and g.deleted_at is null
                      and g.guardian_person_id = core.current_person_id() and g.can_pay_fees and g.status = 'ACTIVE')), false)
        or iam.has_permission_in('fin02.execute', null, null);
  if ok is not true then raise exception 'not allowed to pay this obligation' using errcode = '42501'; end if;
  if p_amount is null or p_amount <= 0 or p_amount > o.net_amount - o.paid_amount then raise exception 'invalid amount'; end if;
  insert into fin.payments (tenant_id, student_id, payer_person_id, payment_method_id, payment_no, amount, status)
  values (o.tenant_id, o.student_id, core.current_person_id(), p_method, 'PAY-' || to_char(now(), 'YYYYMMDD') || '-' || substr(gen_random_uuid()::text, 1, 8), p_amount, 'INITIATED')
  returning id into v;
  return v;
end $$;
comment on function fin.start_payment is 'Student/guardian starts a payment. It stays INITIATED until the gateway webhook (service_role) marks it SUCCESS; allocation then goes through fin.payment_allocations.';

-- scripts assigned to the calling valuator (answer scripts carry no student identity for them)
create or replace function core.valuator_script_ids() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select sa.answer_script_id from exm.script_assignments sa
    join exm.valuation_assignments va on va.id = sa.valuation_assignment_id and va.tenant_id = sa.tenant_id
   where sa.tenant_id = core.current_tenant_id() and va.employee_id = core.current_employee_id() and va.deleted_at is null $$;
