-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.

-- Tenant is carried in the JWT app_metadata (server-controlled, not user-editable).
-- Set it with the Auth custom access token hook in 80_auth_hook.sql or via auth.admin.updateUserById(app_metadata).
create or replace function core.current_tenant_id() returns uuid
language sql stable parallel safe as $$
  select nullif(coalesce(auth.jwt() -> 'app_metadata' ->> 'tenant_id', auth.jwt() ->> 'tenant_id'), '')::uuid
$$;
