-- Rollback: 20261125180000_iadm06_foundation_keys.sql
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DELETE FROM iam.role_permissions rp
USING iam.permissions p
WHERE rp.permission_id = p.id AND p.code LIKE 'iadm06.%';

DELETE FROM iam.role_template_permissions
WHERE permission_code LIKE 'iadm06.%';

DELETE FROM iam.permissions
WHERE code LIKE 'iadm06.%' AND deleted_at IS NULL;

COMMIT;
