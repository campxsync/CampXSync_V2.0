BEGIN;
DROP FUNCTION IF EXISTS plat.check_entitlement(uuid, text);
DELETE FROM iam.permissions WHERE code IN ('iadm04.view','iadm04.write','iadm04.manage','iadm06.view','iadm06.export')
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp WHERE rp.permission_id = iam.permissions.id);
COMMIT;
