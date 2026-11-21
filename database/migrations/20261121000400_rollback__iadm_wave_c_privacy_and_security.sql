BEGIN;
DROP TABLE IF EXISTS plat.institute_security_policies CASCADE;
DROP TABLE IF EXISTS plat.security_findings CASCADE;
DROP TABLE IF EXISTS plat.legal_holds CASCADE;
DROP TABLE IF EXISTS plat.data_subject_requests CASCADE;
-- permission keys left in place (safe); delete only if unused
DELETE FROM iam.permissions WHERE code IN ('iadm05.view','iadm05.write','iadm05.execute','iadm05.manage','iadm07.view','iadm07.write','iadm07.manage')
  AND NOT EXISTS (SELECT 1 FROM iam.role_permissions rp WHERE rp.permission_id = iam.permissions.id);
COMMIT;
