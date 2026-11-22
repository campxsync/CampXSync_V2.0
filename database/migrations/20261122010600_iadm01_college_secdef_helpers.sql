-- IADM-01 S2: SECURITY DEFINER helpers for college create/update under RLS.
-- Plain language: core.colleges has RLS enabled but no policies for authenticated,
-- so Java (SET LOCAL ROLE authenticated) cannot insert/update without a definer helper.
-- Rollback: 20261122010600_rollback__iadm01_college_secdef_helpers.sql

CREATE OR REPLACE FUNCTION plat.iadm01_create_college(
  p_tenant_id uuid,
  p_code text,
  p_name text,
  p_campus_code text,
  p_location text
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_id uuid;
  v_allowed boolean;
  v_profile jsonb;
BEGIN
  IF p_tenant_id IS NULL OR NULLIF(trim(p_code), '') IS NULL OR NULLIF(trim(p_name), '') IS NULL THEN
    RAISE EXCEPTION 'IADM01_VALIDATION: tenant, code and name are required';
  END IF;

  v_allowed := iam.is_platform_admin() OR EXISTS (
    SELECT 1 FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL
    WHERE ra.user_id = auth.uid() AND ra.tenant_id = p_tenant_id AND ra.deleted_at IS NULL
      AND upper(r.code) = 'INSTITUTION_ADMIN'
  ) OR iam.has_permission('adm06.manage') OR iam.has_permission('adm01.manage');
  IF NOT coalesce(v_allowed, false) THEN
    RAISE EXCEPTION 'IADM01_FORBIDDEN: not authorized to register a college';
  END IF;

  IF EXISTS (
    SELECT 1 FROM core.colleges c
    WHERE c.tenant_id = p_tenant_id AND c.code = upper(trim(p_code)) AND c.deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'IADM01_DUPLICATE_CODE: A college with this code already exists';
  END IF;

  v_id := gen_random_uuid();
  v_profile := jsonb_build_object(
    'campusCode', coalesce(nullif(trim(p_campus_code), ''), ''),
    'location', coalesce(nullif(trim(p_location), ''), '')
  );

  INSERT INTO core.colleges (
    id, tenant_id, code, name, status, provisioning_status, profile, created_by, updated_by
  ) VALUES (
    v_id, p_tenant_id, upper(trim(p_code)), trim(p_name), 'ONBOARDING', 'REQUESTED',
    v_profile, auth.uid(), auth.uid()
  );
  RETURN v_id;
END;
$$;

CREATE OR REPLACE FUNCTION plat.iadm01_update_college_identity(
  p_college_id uuid,
  p_name text,
  p_campus_code text,
  p_location text
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_tenant uuid;
  v_status text;
  v_allowed boolean;
  v_profile jsonb;
BEGIN
  SELECT tenant_id, status, coalesce(profile, '{}'::jsonb)
  INTO v_tenant, v_status, v_profile
  FROM core.colleges WHERE id = p_college_id AND deleted_at IS NULL;
  IF v_tenant IS NULL THEN
    RAISE EXCEPTION 'IADM01_NOT_FOUND: College not found';
  END IF;
  IF upper(v_status) = 'ACTIVE' AND EXISTS (
    SELECT 1 FROM plat.college_cadm_handovers h
    WHERE h.college_id = p_college_id AND h.status = 'COMPLETED' AND h.deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'IADM01_HANDED_OVER: College identity is read-only after handover';
  END IF;

  v_allowed := iam.is_platform_admin() OR EXISTS (
    SELECT 1 FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL
    WHERE ra.user_id = auth.uid() AND ra.tenant_id = v_tenant AND ra.deleted_at IS NULL
      AND upper(r.code) = 'INSTITUTION_ADMIN'
  );
  IF NOT coalesce(v_allowed, false) THEN
    RAISE EXCEPTION 'IADM01_FORBIDDEN: not authorized to update this college';
  END IF;

  IF p_name IS NOT NULL AND trim(p_name) <> '' THEN
    UPDATE core.colleges SET name = trim(p_name), updated_at = now(), updated_by = auth.uid()
    WHERE id = p_college_id;
  END IF;
  v_profile := v_profile
    || CASE WHEN p_campus_code IS NULL THEN '{}'::jsonb ELSE jsonb_build_object('campusCode', trim(p_campus_code)) END
    || CASE WHEN p_location IS NULL THEN '{}'::jsonb ELSE jsonb_build_object('location', trim(p_location)) END;
  UPDATE core.colleges SET profile = v_profile, updated_at = now(), updated_by = auth.uid()
  WHERE id = p_college_id;
END;
$$;

CREATE OR REPLACE FUNCTION plat.iadm01_set_college_status(
  p_college_id uuid,
  p_status text
) RETURNS void
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_tenant uuid;
  v_allowed boolean;
BEGIN
  IF p_status IS NULL OR upper(p_status) NOT IN ('ONBOARDING','PENDING_HANDOVER','ACTIVE','SUSPENDED','ARCHIVED') THEN
    RAISE EXCEPTION 'IADM01_VALIDATION: invalid college status';
  END IF;
  SELECT tenant_id INTO v_tenant FROM core.colleges WHERE id = p_college_id AND deleted_at IS NULL;
  IF v_tenant IS NULL THEN
    RAISE EXCEPTION 'IADM01_NOT_FOUND: College not found';
  END IF;
  v_allowed := iam.is_platform_admin() OR EXISTS (
    SELECT 1 FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL
    WHERE ra.user_id = auth.uid() AND ra.tenant_id = v_tenant AND ra.deleted_at IS NULL
      AND upper(r.code) = 'INSTITUTION_ADMIN'
  );
  IF NOT coalesce(v_allowed, false) THEN
    RAISE EXCEPTION 'IADM01_FORBIDDEN: not authorized to change college status';
  END IF;
  UPDATE core.colleges SET status = upper(p_status), updated_at = now(), updated_by = auth.uid()
  WHERE id = p_college_id;
END;
$$;

REVOKE ALL ON FUNCTION plat.iadm01_create_college(uuid, text, text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION plat.iadm01_update_college_identity(uuid, text, text, text) FROM PUBLIC;
REVOKE ALL ON FUNCTION plat.iadm01_set_college_status(uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.iadm01_create_college(uuid, text, text, text, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION plat.iadm01_update_college_identity(uuid, text, text, text) TO authenticated, service_role;
GRANT EXECUTE ON FUNCTION plat.iadm01_set_college_status(uuid, text) TO authenticated, service_role;
