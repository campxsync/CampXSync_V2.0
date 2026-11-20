-- Migration: 20261120000300_rbac_phase3_code_alignment.sql
-- Description: Phase 3 DB Foundation - plat.assign_institution_admin function and iam.api_clients 90-day expiry constraint
-- Target DB: azulbaxszsqxymqmlwfe (Dev Supabase)
-- Author: CampX Platform Engineering / Owner Approval Recorded Niranjan V.G. 2026-11-20

BEGIN;

-- =========================================================================
-- 1. iam.api_clients 90-day expiry constraint
-- =========================================================================
ALTER TABLE iam.api_clients
  DROP CONSTRAINT IF EXISTS ck_api_clients_expires_at;

ALTER TABLE iam.api_clients
  ADD CONSTRAINT ck_api_clients_expires_at
  CHECK (
    expires_at IS NOT NULL
    AND expires_at <= created_at + interval '90 days 5 minutes'
    AND expires_at >= created_at - interval '5 minutes'
  );

COMMENT ON CONSTRAINT ck_api_clients_expires_at ON iam.api_clients IS
  'Mandatory expiry: machine identities must expire within 90 days of creation';

-- =========================================================================
-- 2. plat.assign_institution_admin Function
-- =========================================================================
CREATE OR REPLACE FUNCTION plat.assign_institution_admin(
  p_tenant_id uuid,
  p_user_id uuid,
  p_assigned_by uuid
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role_id uuid;
  v_assignment_id uuid;
  v_is_platform_admin boolean;
BEGIN
  -- 1. Authorization: Platform Admin only (plat.platform_admins or privileged session)
  SELECT (CURRENT_USER NOT IN ('authenticated', 'anon'))
      OR EXISTS (
        SELECT 1 FROM plat.platform_admins p
        WHERE p.user_id = p_assigned_by
          AND p.status = 'ACTIVE'
          AND p.deleted_at IS NULL
      )
  INTO v_is_platform_admin;

  IF NOT v_is_platform_admin THEN
    RAISE EXCEPTION 'Access Denied: Only platform administrators can assign INSTITUTION_ADMIN';
  END IF;

  -- 2. Validate tenant exists
  IF NOT EXISTS (SELECT 1 FROM core.tenants WHERE id = p_tenant_id AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'Tenant % does not exist or is deleted', p_tenant_id;
  END IF;

  -- 3. Validate user profile exists for this tenant
  IF NOT EXISTS (SELECT 1 FROM iam.user_profiles WHERE tenant_id = p_tenant_id AND id = p_user_id AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'User % does not exist in tenant %', p_user_id, p_tenant_id;
  END IF;

  -- 4. Lookup INSTITUTION_ADMIN role for this tenant
  SELECT id INTO v_role_id
  FROM iam.roles
  WHERE tenant_id = p_tenant_id
    AND code = 'INSTITUTION_ADMIN'
    AND deleted_at IS NULL
  LIMIT 1;

  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'INSTITUTION_ADMIN role not found for tenant %', p_tenant_id;
  END IF;

  -- 5. Insert role assignment (idempotent if already assigned)
  SELECT id INTO v_assignment_id
  FROM iam.role_assignments
  WHERE tenant_id = p_tenant_id
    AND user_id = p_user_id
    AND role_id = v_role_id
    AND deleted_at IS NULL
  LIMIT 1;

  IF v_assignment_id IS NULL THEN
    INSERT INTO iam.role_assignments (
      tenant_id,
      user_id,
      role_id,
      college_id,
      department_id,
      created_by
    ) VALUES (
      p_tenant_id,
      p_user_id,
      v_role_id,
      NULL, -- tenant-wide
      NULL,
      p_assigned_by
    ) RETURNING id INTO v_assignment_id;
  END IF;

  RETURN v_assignment_id;
END;
$$;

COMMENT ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) IS
  'Secure platform-operator assignment of R03 Institution Admin, replacing Tenant Admin self-assignment';

REVOKE ALL ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) TO authenticated, service_role;

COMMIT;
