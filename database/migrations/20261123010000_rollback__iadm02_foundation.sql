-- Rollback: 20261123010000_rollback__iadm02_foundation.sql
-- Plain language: reverse IADM-02 foundation (DEV only).

BEGIN;

-- Restore open read on setting_definitions (pre-IADM-02 posture)
DROP POLICY IF EXISTS setting_definitions_read ON cfg.setting_definitions;
CREATE POLICY setting_definitions_read ON cfg.setting_definitions
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS setting_definitions_platform ON cfg.setting_definitions;
CREATE POLICY setting_definitions_platform ON cfg.setting_definitions
  FOR ALL TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

ALTER TABLE cfg.setting_definitions NO FORCE ROW LEVEL SECURITY;

DROP TABLE IF EXISTS cfg.config_drafts CASCADE;
DROP TABLE IF EXISTS cfg.config_versions CASCADE;

UPDATE cfg.setting_definitions
SET deleted_at = now()
WHERE setting_key = 'iam.delegation.max_validity_days' AND deleted_at IS NULL;

DELETE FROM iam.role_permissions rp
USING iam.roles r, iam.permissions p
WHERE rp.role_id = r.id AND rp.permission_id = p.id
  AND p.code IN (
    'cfg.catalog.view', 'cfg.draft', 'cfg.approve', 'cfg.publish',
    'cfg.policy.draft', 'cfg.consumer.read'
  );

DELETE FROM iam.role_template_permissions
WHERE permission_code IN (
  'cfg.catalog.view', 'cfg.draft', 'cfg.approve', 'cfg.publish',
  'cfg.policy.draft', 'cfg.consumer.read'
)
AND deleted_at IS NULL;

UPDATE iam.permissions
SET deleted_at = now()
WHERE code IN (
  'cfg.catalog.view', 'cfg.draft', 'cfg.approve', 'cfg.publish',
  'cfg.policy.draft', 'cfg.consumer.read'
)
AND deleted_at IS NULL;

COMMIT;
