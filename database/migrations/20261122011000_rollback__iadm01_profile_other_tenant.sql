-- Rollback restores the prior body from 20261122010500 (without other-tenant guard).
CREATE OR REPLACE FUNCTION plat.iadm01_create_institute_user_profile(
  p_tenant_id uuid,
  p_auth_user_id uuid,
  p_email text,
  p_display_name text
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_person_id uuid;
  v_existing uuid;
  v_first text;
  v_last text;
  v_name text;
  v_allowed boolean;
BEGIN
  IF p_tenant_id IS NULL OR p_auth_user_id IS NULL THEN
    RAISE EXCEPTION 'IADM01_VALIDATION: tenant and auth user are required';
  END IF;

  v_allowed := iam.is_platform_admin() OR EXISTS (
    SELECT 1
    FROM iam.role_assignments ra
    JOIN iam.roles r ON r.id = ra.role_id AND r.deleted_at IS NULL
    WHERE ra.user_id = auth.uid()
      AND ra.tenant_id = p_tenant_id
      AND ra.deleted_at IS NULL
      AND upper(r.code) = 'INSTITUTION_ADMIN'
  );
  IF NOT v_allowed THEN
    RAISE EXCEPTION 'IADM01_FORBIDDEN: only platform or Institution Admin may create institute profiles';
  END IF;

  IF NOT plat.is_auth_linked_user(p_auth_user_id) THEN
    RAISE EXCEPTION 'IADM01_AUTH_USER_REQUIRED: target must be a linked Supabase Auth user';
  END IF;

  SELECT id INTO v_existing
  FROM iam.user_profiles
  WHERE id = p_auth_user_id
    AND tenant_id = p_tenant_id
    AND deleted_at IS NULL
  LIMIT 1;
  IF v_existing IS NOT NULL THEN
    RETURN v_existing;
  END IF;

  v_name := COALESCE(NULLIF(trim(p_display_name), ''), NULLIF(trim(p_email), ''), 'College Admin');
  v_first := split_part(v_name, ' ', 1);
  v_last := NULLIF(trim(substr(v_name, length(v_first) + 1)), '');
  IF v_last IS NULL OR v_last = '' THEN
    v_last := 'Admin';
  END IF;

  v_person_id := gen_random_uuid();
  INSERT INTO core.persons (
    id, tenant_id, first_name, last_name, display_name, primary_email, status, created_by
  ) VALUES (
    v_person_id, p_tenant_id, v_first, v_last, v_name, p_email, 'ACTIVE', auth.uid()
  );

  INSERT INTO iam.user_profiles (
    id, tenant_id, person_id, username, email, full_name, status, created_by
  ) VALUES (
    p_auth_user_id, p_tenant_id, v_person_id, COALESCE(p_email, p_auth_user_id::text),
    p_email, v_name, 'ACTIVE', auth.uid()
  );

  RETURN p_auth_user_id;
END;
$$;
