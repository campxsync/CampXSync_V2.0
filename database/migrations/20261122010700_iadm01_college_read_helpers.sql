-- IADM-01 S2: SECURITY DEFINER college read helpers (core.colleges RLS has no SELECT policies).
-- Applied on DEV azulbaxszsqxymqmlwfe via MCP 2026-11-22.

CREATE OR REPLACE FUNCTION plat.iadm01_get_college(p_college_id uuid)
RETURNS TABLE (
  id uuid, tenant_id uuid, code text, name text, status text,
  provisioning_status text, profile jsonb, created_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_allowed boolean; v_tenant uuid;
BEGIN
  SELECT c.tenant_id INTO v_tenant FROM core.colleges c WHERE c.id = p_college_id AND c.deleted_at IS NULL;
  IF v_tenant IS NULL THEN
    RETURN;
  END IF;
  v_allowed := iam.is_platform_admin()
    OR EXISTS (
      SELECT 1 FROM iam.role_assignments ra
      JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL
      WHERE ra.user_id = auth.uid() AND ra.tenant_id = v_tenant AND ra.deleted_at IS NULL
        AND upper(r.code) IN ('INSTITUTION_ADMIN','COLLEGE_ADMIN')
    );
  IF NOT coalesce(v_allowed, false) THEN
    RAISE EXCEPTION 'IADM01_FORBIDDEN: not authorized to view this college';
  END IF;
  RETURN QUERY
  SELECT c.id, c.tenant_id, c.code, c.name, c.status, c.provisioning_status, c.profile, c.created_at
  FROM core.colleges c WHERE c.id = p_college_id AND c.deleted_at IS NULL;
END;
$$;

CREATE OR REPLACE FUNCTION plat.iadm01_list_colleges(p_tenant_id uuid, p_status text DEFAULT NULL)
RETURNS TABLE (
  id uuid, tenant_id uuid, code text, name text, status text,
  provisioning_status text, profile jsonb, created_at timestamptz,
  handover_status text
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_allowed boolean;
BEGIN
  v_allowed := iam.is_platform_admin()
    OR EXISTS (
      SELECT 1 FROM iam.role_assignments ra
      JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL
      WHERE ra.user_id = auth.uid() AND ra.tenant_id = p_tenant_id AND ra.deleted_at IS NULL
        AND upper(r.code) = 'INSTITUTION_ADMIN'
    );
  IF NOT coalesce(v_allowed, false) THEN
    RAISE EXCEPTION 'IADM01_FORBIDDEN: not authorized to list colleges';
  END IF;
  RETURN QUERY
  SELECT c.id, c.tenant_id, c.code, c.name, c.status, c.provisioning_status, c.profile, c.created_at,
         h.status AS handover_status
  FROM core.colleges c
  LEFT JOIN LATERAL (
    SELECT hh.status FROM plat.college_cadm_handovers hh
    WHERE hh.college_id = c.id AND hh.tenant_id = c.tenant_id AND hh.deleted_at IS NULL
    ORDER BY hh.created_at DESC LIMIT 1
  ) h ON true
  WHERE c.tenant_id = p_tenant_id AND c.deleted_at IS NULL
    AND (p_status IS NULL OR p_status = '' OR upper(p_status) = 'ALL' OR upper(c.status) = upper(p_status))
  ORDER BY c.created_at DESC
  LIMIT 200;
END;
$$;

REVOKE ALL ON FUNCTION plat.iadm01_get_college(uuid) FROM PUBLIC;
REVOKE ALL ON FUNCTION plat.iadm01_list_colleges(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.iadm01_get_college(uuid) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION plat.iadm01_list_colleges(uuid, text) TO authenticated, service_role;
