-- Rollback: 20261121000100_rollback__iadm_assign_institution_admin_auth_uid.sql
-- Restores the Phase 3 signature that trusted p_assigned_by (INSECURE — only for emergency rollback).
-- Prefer re-applying the forward migration rather than rolling back in DEV.

BEGIN;

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

  IF NOT EXISTS (SELECT 1 FROM core.tenants WHERE id = p_tenant_id AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'Tenant % does not exist or is deleted', p_tenant_id;
  END IF;

  IF NOT EXISTS (SELECT 1 FROM iam.user_profiles WHERE tenant_id = p_tenant_id AND id = p_user_id AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'User % does not exist in tenant %', p_user_id, p_tenant_id;
  END IF;

  SELECT id INTO v_role_id
  FROM iam.roles
  WHERE tenant_id = p_tenant_id
    AND code = 'INSTITUTION_ADMIN'
    AND deleted_at IS NULL
  LIMIT 1;

  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'INSTITUTION_ADMIN role not found for tenant %', p_tenant_id;
  END IF;

  SELECT id INTO v_assignment_id
  FROM iam.role_assignments
  WHERE tenant_id = p_tenant_id
    AND user_id = p_user_id
    AND role_id = v_role_id
    AND deleted_at IS NULL
  LIMIT 1;

  IF v_assignment_id IS NULL THEN
    INSERT INTO iam.role_assignments (
      tenant_id, user_id, role_id, college_id, department_id, created_by
    ) VALUES (
      p_tenant_id, p_user_id, v_role_id, NULL, NULL, p_assigned_by
    ) RETURNING id INTO v_assignment_id;
  END IF;

  RETURN v_assignment_id;
END;
$$;

REVOKE ALL ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) FROM anon;
GRANT EXECUTE ON FUNCTION plat.assign_institution_admin(uuid, uuid, uuid) TO authenticated, service_role;

COMMIT;
