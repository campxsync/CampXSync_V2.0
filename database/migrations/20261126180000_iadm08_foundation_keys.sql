-- Migration: 20261126180000_iadm08_foundation_keys.sql
-- Plain language: IADM-08 permission keys and role-template grants (IADM-08-US-017 / US-018).
-- Additive only. Legacy adm08.* (iam.user_profiles / user_groups) untouched. No wildcard.
-- No IADM-08 schema or tables (D14).
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description
FROM (VALUES
  ('iadm08.audit.platform.view', 'iadm08', 'audit.platform.view', 'View/search platform audit investigation (IADM-08)'),
  ('iadm08.audit.institute.view', 'iadm08', 'audit.institute.view', 'View own-institute audit activity (IADM-08)'),
  ('iadm08.health.view', 'iadm08', 'health.view', 'View on-request service health overview/detail (IADM-08)'),
  ('iadm08.alerts.view', 'iadm08', 'alerts.view', 'View read-only operational / IADM-07 security alerts (IADM-08)'),
  ('iadm08.manage', 'iadm08', 'manage', 'RESERVED manage key — not granted / not implemented in V1 (IADM-08)')
) AS v(code, module, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- Role templates: R01 uses platform_admins path; R02/R03/R28 via templates. R04 none. manage never granted.
INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT x.role_code, x.permission_code
FROM (VALUES
  ('SYSTEM_ADMIN', 'iadm08.audit.platform.view'),
  ('SYSTEM_ADMIN', 'iadm08.audit.institute.view'),
  ('SYSTEM_ADMIN', 'iadm08.health.view'),
  ('SYSTEM_ADMIN', 'iadm08.alerts.view'),
  ('INSTITUTION_ADMIN', 'iadm08.audit.institute.view'),
  ('INSTITUTION_ADMIN', 'iadm08.alerts.view'),
  ('AUDITOR', 'iadm08.audit.platform.view'),
  ('AUDITOR', 'iadm08.audit.institute.view'),
  ('AUDITOR', 'iadm08.health.view'),
  ('AUDITOR', 'iadm08.alerts.view')
) AS x(role_code, permission_code)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_template_permissions rtp
  WHERE rtp.role_code = x.role_code
    AND rtp.permission_code = x.permission_code
    AND rtp.deleted_at IS NULL
);

-- Propagate into per-tenant role_permissions
INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.deleted_at IS NULL
JOIN iam.role_template_permissions rtp
  ON rtp.role_code = r.code AND rtp.permission_code = p.code AND rtp.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND r.code IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'AUDITOR')
  AND p.code LIKE 'iadm08.%'
  AND p.code <> 'iadm08.manage'
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id AND rp.permission_id = p.id
  );

COMMIT;
