-- Rollback: 20261126180000_iadm08_foundation_keys.sql
-- Plain language: remove only IADM-08 additive keys/grants. Legacy adm08.* untouched.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DELETE FROM iam.role_permissions rp
USING iam.permissions p
WHERE rp.permission_id = p.id
  AND p.code LIKE 'iadm08.%';

DELETE FROM iam.role_template_permissions
WHERE permission_code LIKE 'iadm08.%';

DELETE FROM iam.permissions
WHERE code LIKE 'iadm08.%'
  AND deleted_at IS NULL;

COMMIT;
