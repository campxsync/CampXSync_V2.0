-- Migration: 20261121000100_iadm_assign_institution_admin_auth_uid.sql
-- Description: IADM Step 0.1 — harden plat.assign_institution_admin to trust auth.uid()
--   (caller login identity), never the client-supplied p_assigned_by parameter.
-- Target DB: azulbaxszsqxymqmlwfe (Dev Supabase ONLY)
-- Author: CampX Platform Engineering / Owner Approval Recorded Niranjan V.G. 2026-11-21
-- Story: IADM-01-US-002 / Step 0.1 Appointment-function security check

BEGIN;

-- Replace function: caller identity = auth.uid(); p_assigned_by ignored (signature kept for callers)
CREATE OR REPLACE FUNCTION plat.assign_institution_admin(
  p_tenant_id uuid,
  p_user_id uuid,
  p_assigned_by uuid DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role_id uuid;
  v_assignment_id uuid;
  v_caller uuid := auth.uid();
BEGIN
  -- 1. Fail closed if no authenticated login identity
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'Access Denied: missing authenticated caller identity (auth.uid())';
  END IF;

  -- 2. Authorization: caller must be an ACTIVE platform administrator
  --    Do NOT trust p_assigned_by — it is ignored to prevent spoofing.
  IF NOT EXISTS (
    SELECT 1 FROM plat.platform_admins p
    WHERE p.user_id = v_caller
      AND p.status = 'ACTIVE'
      AND p.deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'Access Denied: Only platform administrators can assign INSTITUTION_ADMIN';
  END IF;

  -- 3. Validate tenant exists
  IF NOT EXISTS (SELECT 1 FROM core.tenants WHERE id = p_tenant_id AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'Tenant % does not exist or is deleted', p_tenant_id;
  END IF;

  -- 4. Validate user profile exists for this tenant
  IF NOT EXISTS (
    SELECT 1 FROM iam.user_profiles
    WHERE tenant_id = p_tenant_id AND id = p_user_id AND deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'User % does not exist in tenant %', p_user_id, p_tenant_id;
  END IF;

  -- 5. Lookup INSTITUTION_ADMIN role for this tenant
  SELECT id INTO v_role_id
  FROM iam.roles
  WHERE tenant_id = p_tenant_id
    AND code = 'INSTITUTION_ADMIN'
    AND deleted_at IS NULL
  LIMIT 1;

  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'INSTITUTION_ADMIN role not found for tenant %', p_tenant_id;
  END IF;

  -- 6. Insert role assignment (idempotent if already assigned); created_by = real caller
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
      NULL,
      NULL,
      v_caller
    ) RETURNING id INTO v_assignment_id;
  END IF;

  RETURN v_assignment_id;
END;
$$;

COMMENT ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) IS
  'Secure platform-operator assignment of R03 Institution Admin. Caller identity from auth.uid() only; p_assigned_by ignored.';

-- Keep execute for authenticated (Java sessions use SET LOCAL ROLE authenticated + JWT)
-- and service_role. Deny PUBLIC and anon.
REVOKE ALL ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) TO authenticated, service_role;

COMMIT;
