-- Rollback: 20261125110000_iadm04_foundation_keys.sql
BEGIN;

DELETE FROM iam.role_permissions rp
USING iam.permissions p
WHERE rp.permission_id = p.id AND p.code LIKE 'iadm04.%';

DELETE FROM iam.role_template_permissions
WHERE permission_code LIKE 'iadm04.%';

UPDATE iam.permissions
SET deleted_at = now()
WHERE code LIKE 'iadm04.%' AND deleted_at IS NULL;

COMMIT;
