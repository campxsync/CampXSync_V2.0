-- Rollback Wave B IADM objects (DEV only)
BEGIN;
DROP FUNCTION IF EXISTS audit.self_check_immutable();
DROP TABLE IF EXISTS iam.access_change_requests CASCADE;
DROP TABLE IF EXISTS iam.role_ceilings CASCADE;
COMMIT;
