-- Rollback Wave C/D IADM role template grants
BEGIN;

DELETE FROM iam.role_template_permissions
WHERE permission_code IN (
  'iadm04.view','iadm04.write','iadm04.manage',
  'iadm05.view','iadm05.write','iadm05.execute','iadm05.manage',
  'iadm06.view','iadm06.export',
  'iadm07.view','iadm07.write','iadm07.manage'
);

-- Remove corresponding live grants; leave permissions rows (owned by 00400/00500)
DELETE FROM iam.role_permissions rp
USING iam.permissions p
WHERE rp.permission_id = p.id
  AND p.code IN (
    'iadm04.view','iadm04.write','iadm04.manage',
    'iadm05.view','iadm05.write','iadm05.execute','iadm05.manage',
    'iadm06.view','iadm06.export',
    'iadm07.view','iadm07.write','iadm07.manage'
  );

COMMIT;
