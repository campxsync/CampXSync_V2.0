-- Rollback: 20261125170000_rollback__iadm05_foundation_keys.sql
-- Plain language: remove IADM-05 permission grants and keys.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DELETE FROM iam.role_permissions rp
USING iam.permissions p
WHERE rp.permission_id = p.id
  AND p.code LIKE 'iadm05.%';

DELETE FROM iam.role_template_permissions
WHERE permission_code LIKE 'iadm05.%';

UPDATE iam.permissions
SET deleted_at = now(), updated_at = now()
WHERE code LIKE 'iadm05.%'
  AND deleted_at IS NULL;

COMMIT;
