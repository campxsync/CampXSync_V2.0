-- Rollback: 20261126130000_rollback__iadm07_foundation_keys.sql
-- Plain language: remove only the IADM-07 keys added by the paired migration.
-- Does not touch legacy adm07.* department keys.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DELETE FROM iam.role_permissions rp
USING iam.permissions p
WHERE rp.permission_id = p.id
  AND p.code LIKE 'iadm07.%';

DELETE FROM iam.role_template_permissions
WHERE permission_code LIKE 'iadm07.%';

DELETE FROM iam.permissions
WHERE code LIKE 'iadm07.%';

COMMIT;
