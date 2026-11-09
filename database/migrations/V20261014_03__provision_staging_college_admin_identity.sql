-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION (AUDITABLE STAGING IDENTITY PROVISIONING)
-- ==============================================================================
-- Target Environment: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Target Schemas: iam
-- Purpose:
--   Links Supabase Auth confirmed user campxsync@gmail.com (70a8ffae-b5ad-4c61-a401-16f220893df4)
--   to tenant CAMPX_STAGING (11111111-1111-1111-1111-111111111111) in iam.user_profiles
--   and assigns role COLLEGE_ADMIN in iam.role_assignments.
-- Safety Policy:
--   1. NEVER EXECUTE AUTOMATICALLY OR ON FORBIDDEN PROD (ekspahbsbwdpraaqfqui).
--   2. Strict precondition validation: verifies tenant is ACTIVE and COLLEGE_ADMIN role exists.
--   3. Strict postcondition assertions: verifies profile and assignment presence and active status.
--   4. Fully idempotent ON CONFLICT semantics preserving row_version and audit triggers.
-- ==============================================================================

DO $$
DECLARE
  v_tenant_id uuid := '11111111-1111-1111-1111-111111111111'::uuid;
  v_user_id uuid := '70a8ffae-b5ad-4c61-a401-16f220893df4'::uuid;
  v_email text := 'campxsync@gmail.com';
  v_full_name text := 'CampXSync Staging Admin';
  v_role_id uuid;
  v_assignment_id uuid := '33333333-3333-3333-3333-333333333333'::uuid;
BEGIN
  -- ----------------------------------------------------------------------------
  -- PRECONDITION 1: Staging Tenant Presence and Status
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (
    SELECT 1 FROM core.tenants WHERE id = v_tenant_id AND status = 'ACTIVE' AND deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'Precondition Failed: Tenant % does not exist or is not ACTIVE', v_tenant_id;
  END IF;

  -- ----------------------------------------------------------------------------
  -- PRECONDITION 2: COLLEGE_ADMIN Role Presence for Tenant
  -- ----------------------------------------------------------------------------
  SELECT id INTO v_role_id
  FROM iam.roles
  WHERE tenant_id = v_tenant_id AND code = 'COLLEGE_ADMIN' AND deleted_at IS NULL;

  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'Precondition Failed: COLLEGE_ADMIN role not found for tenant %', v_tenant_id;
  END IF;

  -- ----------------------------------------------------------------------------
  -- EXECUTION 1: Idempotent User Profile Upsert in iam.user_profiles
  -- ----------------------------------------------------------------------------
  INSERT INTO iam.user_profiles (
    id,
    tenant_id,
    email,
    full_name,
    status,
    created_at,
    updated_at,
    row_version
  )
  VALUES (
    v_user_id,
    v_tenant_id,
    v_email,
    v_full_name,
    'ACTIVE',
    now(),
    now(),
    1
  )
  ON CONFLICT (id) DO UPDATE
    SET email = EXCLUDED.email,
        full_name = EXCLUDED.full_name,
        status = 'ACTIVE',
        deleted_at = NULL,
        updated_at = now();

  -- ----------------------------------------------------------------------------
  -- EXECUTION 2: Idempotent Role Assignment Upsert in iam.role_assignments
  -- ----------------------------------------------------------------------------
  INSERT INTO iam.role_assignments (
    id,
    tenant_id,
    user_id,
    role_id,
    created_at,
    updated_at,
    row_version
  )
  VALUES (
    v_assignment_id,
    v_tenant_id,
    v_user_id,
    v_role_id,
    now(),
    now(),
    1
  )
  ON CONFLICT (id) DO UPDATE
    SET role_id = EXCLUDED.role_id,
        user_id = EXCLUDED.user_id,
        tenant_id = EXCLUDED.tenant_id,
        deleted_at = NULL,
        updated_at = now();

  -- ----------------------------------------------------------------------------
  -- POSTCONDITION 1: Verify User Profile Active State
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (
    SELECT 1 FROM iam.user_profiles 
    WHERE id = v_user_id 
      AND tenant_id = v_tenant_id 
      AND status = 'ACTIVE' 
      AND deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'Postcondition Failed: User profile % is not active in tenant %', v_user_id, v_tenant_id;
  END IF;

  -- ----------------------------------------------------------------------------
  -- POSTCONDITION 2: Verify COLLEGE_ADMIN Role Assignment Active State
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (
    SELECT 1 FROM iam.role_assignments
    WHERE user_id = v_user_id 
      AND tenant_id = v_tenant_id 
      AND role_id = v_role_id 
      AND deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'Postcondition Failed: COLLEGE_ADMIN role assignment not found for user % in tenant %', v_user_id, v_tenant_id;
  END IF;

END $$;
