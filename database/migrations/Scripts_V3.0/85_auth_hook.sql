-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.
-- Auth Hook: stamp tenant_id into the access token so RLS can read it from the JWT.
-- Enable in Dashboard > Authentication > Hooks > Custom Access Token > pg-functions://postgres/iam/custom_access_token_hook
create or replace function iam.custom_access_token_hook(event jsonb) returns jsonb
language plpgsql stable security definer set search_path = '' as $$
declare claims jsonb := event -> 'claims'; v_tenant uuid;
begin
  select up.tenant_id into v_tenant from iam.user_profiles up
   where up.id = (event ->> 'user_id')::uuid and up.deleted_at is null and up.status = 'ACTIVE';
  if v_tenant is not null then
    claims := jsonb_set(claims, '{app_metadata}', coalesce(claims -> 'app_metadata', '{}'::jsonb) || jsonb_build_object('tenant_id', v_tenant));
  end if;
  return jsonb_set(event, '{claims}', claims);
end $$;

revoke execute on function iam.custom_access_token_hook(jsonb) from public, anon, authenticated;
do $$ begin
  if exists (select 1 from pg_roles where rolname = 'supabase_auth_admin') then
    grant usage on schema iam to supabase_auth_admin;
    grant execute on function iam.custom_access_token_hook(jsonb) to supabase_auth_admin;
    -- the hook reads iam.user_profiles; policies do not apply to it because the function is SECURITY DEFINER
  end if;
end $$;
