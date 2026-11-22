-- Rollback Wave E0 Auth-linked helper
BEGIN;
DROP FUNCTION IF EXISTS plat.is_auth_linked_user(uuid);
COMMIT;
