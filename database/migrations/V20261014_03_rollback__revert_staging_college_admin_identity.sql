-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION ROLLBACK (STAGING IDENTITY TEARDOWN)
-- ==============================================================================
-- Target Environment: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Target Schemas: iam
-- Purpose:
--   Soft-deletes or removes staging role assignment and user profile for
--   campxsync@gmail.com (70a8ffae-b5ad-4c61-a401-16f220893df4).
-- ==============================================================================

DO $$
DECLARE
  v_tenant_id uuid := '11111111-1111-1111-1111-111111111111'::uuid;
  v_user_id uuid := '70a8ffae-b5ad-4c61-a401-16f220893df4'::uuid;
BEGIN
  -- 1. Remove role assignments
  DELETE FROM iam.role_assignments
  WHERE tenant_id = v_tenant_id AND user_id = v_user_id;

  -- 2. Remove user profile
  DELETE FROM iam.user_profiles
  WHERE tenant_id = v_tenant_id AND id = v_user_id;
END $$;
