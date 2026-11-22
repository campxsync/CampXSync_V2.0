-- IADM-01: resolve existing Auth user by email when Admin list/filter misses them.
CREATE OR REPLACE FUNCTION plat.iadm01_auth_user_id_by_email(p_email text)
RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE v_id uuid;
BEGIN
  IF p_email IS NULL OR length(trim(p_email)) = 0 THEN
    RETURN NULL;
  END IF;
  SELECT u.id INTO v_id
  FROM auth.users u
  WHERE lower(u.email) = lower(trim(p_email))
  LIMIT 1;
  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION plat.iadm01_auth_user_id_by_email(text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.iadm01_auth_user_id_by_email(text) TO authenticated, service_role;
