-- IADM-01 S2: SECURITY DEFINER assign College Admin (R04) under RLS.
-- Applied on DEV azulbaxszsqxymqmlwfe via MCP 2026-11-22.

CREATE OR REPLACE FUNCTION plat.assign_college_admin(
  p_tenant_id uuid,
  p_college_id uuid,
  p_user_id uuid,
  p_assigned_by uuid DEFAULT NULL
)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_role_id uuid;
  v_assignment_id uuid;
  v_caller uuid := auth.uid();
  v_allowed boolean;
BEGIN
  IF v_caller IS NULL THEN
    RAISE EXCEPTION 'Access Denied: missing authenticated caller identity (auth.uid())';
  END IF;

  v_allowed := EXISTS (
    SELECT 1 FROM plat.platform_admins p
    WHERE p.user_id = v_caller AND p.status = 'ACTIVE' AND p.deleted_at IS NULL
  ) OR EXISTS (
    SELECT 1 FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL
    WHERE ra.user_id = v_caller AND ra.tenant_id = p_tenant_id AND ra.deleted_at IS NULL
      AND upper(r.code) = 'INSTITUTION_ADMIN'
  );
  IF NOT v_allowed THEN
    RAISE EXCEPTION 'Access Denied: Only platform or Institution Admin can assign COLLEGE_ADMIN';
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM core.colleges c
    WHERE c.id = p_college_id AND c.tenant_id = p_tenant_id AND c.deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'College % not found in tenant %', p_college_id, p_tenant_id;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM iam.user_profiles
    WHERE tenant_id = p_tenant_id AND id = p_user_id AND deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'User % does not exist in tenant %', p_user_id, p_tenant_id;
  END IF;

  SELECT id INTO v_role_id
  FROM iam.roles
  WHERE tenant_id = p_tenant_id AND code = 'COLLEGE_ADMIN' AND deleted_at IS NULL
  LIMIT 1;
  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'COLLEGE_ADMIN role not found for tenant %', p_tenant_id;
  END IF;

  SELECT id INTO v_assignment_id
  FROM iam.role_assignments
  WHERE tenant_id = p_tenant_id AND user_id = p_user_id AND role_id = v_role_id
    AND college_id = p_college_id AND deleted_at IS NULL
  LIMIT 1;

  IF v_assignment_id IS NULL THEN
    INSERT INTO iam.role_assignments (
      tenant_id, user_id, role_id, college_id, department_id, created_by
    ) VALUES (
      p_tenant_id, p_user_id, v_role_id, p_college_id, NULL, v_caller
    ) RETURNING id INTO v_assignment_id;
  END IF;

  RETURN v_assignment_id;
END;
$$;

REVOKE ALL ON FUNCTION plat.assign_college_admin(uuid, uuid, uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.assign_college_admin(uuid, uuid, uuid, uuid) TO authenticated, service_role;
