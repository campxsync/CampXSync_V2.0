-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION ROLLBACK (GATED RECOVERY PLAN)
-- ==============================================================================
-- Target Environment: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Target Schemas: core, iam
-- Required Ritual: Explicit owner authorization for tenant teardown
-- Safety Policy:
--   Foreign keys on core.tenants(id) are defined with ON DELETE RESTRICT.
--   A simple "DELETE FROM core.tenants" will fail with FK constraint violations.
--   Rollback must verify no orphaned application entities exist, then tear down
--   seeded role permissions and roles in reverse dependency order.
-- ==============================================================================

DO $$
DECLARE
  v_tenant_id uuid := '11111111-1111-1111-1111-111111111111'::uuid;
  v_user_profiles_count integer;
  v_colleges_count integer;
BEGIN
  -- ----------------------------------------------------------------------------
  -- PRECONDITION: Safe Teardown Assertion (No Live Application Data)
  -- ----------------------------------------------------------------------------
  SELECT count(*) INTO v_user_profiles_count FROM iam.user_profiles WHERE tenant_id = v_tenant_id;
  SELECT count(*) INTO v_colleges_count FROM core.colleges WHERE tenant_id = v_tenant_id;

  IF v_user_profiles_count > 0 THEN
    RAISE EXCEPTION 'Teardown Aborted: Tenant % has % associated user profiles. Manual cleanup required.', v_tenant_id, v_user_profiles_count;
  END IF;

  IF v_colleges_count > 0 THEN
    RAISE EXCEPTION 'Teardown Aborted: Tenant % has % associated colleges. Manual cleanup required.', v_tenant_id, v_colleges_count;
  END IF;

  -- ----------------------------------------------------------------------------
  -- EXECUTION: Ordered Foreign-Key Aware Teardown
  -- ----------------------------------------------------------------------------
  -- 1. Remove seeded role permissions
  DELETE FROM iam.role_permissions WHERE tenant_id = v_tenant_id;

  -- 2. Remove seeded system roles
  DELETE FROM iam.roles WHERE tenant_id = v_tenant_id;

  -- 3. Remove tenant root record
  DELETE FROM core.tenants WHERE id = v_tenant_id;

  -- ----------------------------------------------------------------------------
  -- POSTCONDITION: Confirm Clean State
  -- ----------------------------------------------------------------------------
  IF EXISTS (SELECT 1 FROM core.tenants WHERE id = v_tenant_id) THEN
    RAISE EXCEPTION 'Teardown Failed: Tenant % was not removed.', v_tenant_id;
  END IF;

  RAISE NOTICE 'Tenant % and all seeded IAM roles/permissions successfully torn down.', v_tenant_id;
END $$;
