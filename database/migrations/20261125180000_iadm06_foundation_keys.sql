-- Migration: 20261125180000_iadm06_foundation_keys.sql
-- Plain language: IADM-06 permission keys and role-template grants (US-018).
-- No wildcard. View does not imply export. Manage key reserved and NOT granted.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description
FROM (VALUES
  ('iadm06.platform.view', 'iadm06', 'platform.view', 'View platform aggregate analytics (IADM-06)'),
  ('iadm06.institute.view', 'iadm06', 'institute.view', 'View own-institute aggregate analytics (IADM-06)'),
  ('iadm06.entitlement.view', 'iadm06', 'entitlement.view', 'View entitlement-usage aggregates (IADM-06)'),
  ('iadm06.export.request', 'iadm06', 'export.request', 'Request aggregate CSV export (IADM-06)'),
  ('iadm06.export.view', 'iadm06', 'export.view', 'View own authorized export status/result (IADM-06)'),
  ('iadm06.catalogue.view', 'iadm06', 'catalogue.view', 'View read-only metric catalogue (IADM-06)'),
  ('iadm06.catalogue.manage', 'iadm06', 'catalogue.manage', 'RESERVED — manage metric definitions (not implemented V1)')
) AS v(code, module, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- R02 System Admin: view + export; no manage
INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT x.role_code, x.permission_code
FROM (VALUES
  ('SYSTEM_ADMIN', 'iadm06.platform.view'),
  ('SYSTEM_ADMIN', 'iadm06.institute.view'),
  ('SYSTEM_ADMIN', 'iadm06.entitlement.view'),
  ('SYSTEM_ADMIN', 'iadm06.export.request'),
  ('SYSTEM_ADMIN', 'iadm06.export.view'),
  ('SYSTEM_ADMIN', 'iadm06.catalogue.view'),
  -- R03 Institution Admin: own institute
  ('INSTITUTION_ADMIN', 'iadm06.institute.view'),
  ('INSTITUTION_ADMIN', 'iadm06.entitlement.view'),
  ('INSTITUTION_ADMIN', 'iadm06.export.request'),
  ('INSTITUTION_ADMIN', 'iadm06.export.view'),
  ('INSTITUTION_ADMIN', 'iadm06.catalogue.view'),
  -- R28 Auditor: view only, never export
  ('AUDITOR', 'iadm06.platform.view'),
  ('AUDITOR', 'iadm06.institute.view'),
  ('AUDITOR', 'iadm06.entitlement.view'),
  ('AUDITOR', 'iadm06.catalogue.view')
) AS x(role_code, permission_code)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_template_permissions rtp
  WHERE rtp.role_code = x.role_code
    AND rtp.permission_code = x.permission_code
    AND rtp.deleted_at IS NULL
);

-- Propagate into per-tenant role_permissions (R01 uses platform_admins path separately)
INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.deleted_at IS NULL
JOIN iam.role_template_permissions rtp
  ON rtp.role_code = r.code AND rtp.permission_code = p.code AND rtp.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND r.code IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'AUDITOR')
  AND p.code LIKE 'iadm06.%'
  AND p.code <> 'iadm06.catalogue.manage'
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id AND rp.permission_id = p.id
  );

COMMIT;
