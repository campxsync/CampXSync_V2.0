-- Wave E0 / SEC-11: Auth-linked user check callable under RLS role `authenticated`
-- Plain language: First College Admin must exist in Supabase Auth (auth.users).
-- `authenticated` cannot SELECT auth.users directly — use SECURITY DEFINER helper.
-- DEV only: azulbaxszsqxymqmlwfe

BEGIN;

CREATE OR REPLACE FUNCTION plat.is_auth_linked_user(p_user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT CASE
    WHEN p_user_id IS NULL THEN false
    ELSE EXISTS (SELECT 1 FROM auth.users u WHERE u.id = p_user_id)
  END;
$$;

COMMENT ON FUNCTION plat.is_auth_linked_user(uuid) IS
  'IADM-01 SEC-11: true when user id exists in auth.users (Supabase Auth login).';

REVOKE ALL ON FUNCTION plat.is_auth_linked_user(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.is_auth_linked_user(uuid) TO authenticated, service_role;

COMMIT;
