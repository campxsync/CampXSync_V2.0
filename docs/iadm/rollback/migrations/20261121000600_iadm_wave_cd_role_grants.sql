-- Wave C/D follow-up: grant new IADM permission keys via role templates + resync
-- Plain language: Platform Admin (R01)=PLATFORM_ADMIN; System Admin (R02)=SYSTEM_ADMIN;
-- Institution Admin (R03)=INSTITUTION_ADMIN; Auditor (R28)=AUDITOR
-- DEV only: azulbaxszsqxymqmlwfe

BEGIN;

INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  -- Super Admin / Platform Admin (R01)
  ('PLATFORM_ADMIN', 'iadm04.view'),
  ('PLATFORM_ADMIN', 'iadm04.write'),
  ('PLATFORM_ADMIN', 'iadm04.manage'),
  ('PLATFORM_ADMIN', 'iadm05.view'),
  ('PLATFORM_ADMIN', 'iadm05.write'),
  ('PLATFORM_ADMIN', 'iadm05.execute'),
  ('PLATFORM_ADMIN', 'iadm05.manage'),
  ('PLATFORM_ADMIN', 'iadm06.view'),
  ('PLATFORM_ADMIN', 'iadm06.export'),
  ('PLATFORM_ADMIN', 'iadm07.view'),
  ('PLATFORM_ADMIN', 'iadm07.write'),
  ('PLATFORM_ADMIN', 'iadm07.manage'),
  -- System Admin (R02)
  ('SYSTEM_ADMIN', 'iadm04.view'),
  ('SYSTEM_ADMIN', 'iadm04.write'),
  ('SYSTEM_ADMIN', 'iadm04.manage'),
  ('SYSTEM_ADMIN', 'iadm05.view'),
  ('SYSTEM_ADMIN', 'iadm05.write'),
  ('SYSTEM_ADMIN', 'iadm05.execute'),
  ('SYSTEM_ADMIN', 'iadm05.manage'),
  ('SYSTEM_ADMIN', 'iadm06.view'),
  ('SYSTEM_ADMIN', 'iadm06.export'),
  ('SYSTEM_ADMIN', 'iadm07.view'),
  ('SYSTEM_ADMIN', 'iadm07.write'),
  ('SYSTEM_ADMIN', 'iadm07.manage'),
  -- Institution Admin (R03) — institute-scoped keys
  ('INSTITUTION_ADMIN', 'iadm04.view'),
  ('INSTITUTION_ADMIN', 'iadm05.view'),
  ('INSTITUTION_ADMIN', 'iadm05.write'),
  ('INSTITUTION_ADMIN', 'iadm05.execute'),
  ('INSTITUTION_ADMIN', 'iadm05.manage'),
  ('INSTITUTION_ADMIN', 'iadm06.view'),
  ('INSTITUTION_ADMIN', 'iadm07.view'),
  ('INSTITUTION_ADMIN', 'iadm07.write'),
  -- Auditor (R28) — view only
  ('AUDITOR', 'iadm04.view'),
  ('AUDITOR', 'iadm05.view'),
  ('AUDITOR', 'iadm06.view'),
  ('AUDITOR', 'iadm07.view')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

SELECT iam.resync_system_roles();

COMMIT;
