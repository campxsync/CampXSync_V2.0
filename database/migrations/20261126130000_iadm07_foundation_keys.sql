-- Migration: 20261126130000_iadm07_foundation_keys.sql
-- Plain language: IADM-07 permission keys and role-template grants (IADM-07-US-023).
-- Additive only. Legacy adm07.* (departments) untouched. No wildcard.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description
FROM (VALUES
  ('iadm07.posture.view', 'iadm07', 'posture.view', 'View security posture and indicators (IADM-07)'),
  ('iadm07.register.read', 'iadm07', 'register.read', 'Read security registers (IADM-07)'),
  ('iadm07.register.write', 'iadm07', 'register.write', 'Write findings/risks/alerts/triage (IADM-07)'),
  ('iadm07.standard.propose', 'iadm07', 'standard.propose', 'Propose security standard (IADM-07)'),
  ('iadm07.standard.approve', 'iadm07', 'standard.approve', 'Approve/publish security standard — R01 only (IADM-07)'),
  ('iadm07.exception.request', 'iadm07', 'exception.request', 'Request policy exception (IADM-07)'),
  ('iadm07.exception.decide', 'iadm07', 'exception.decide', 'Decide policy exception — R01 only (IADM-07)'),
  ('iadm07.signal.intake', 'iadm07', 'signal.intake', 'Authenticated service signal/enforcement intake (IADM-07)'),
  ('iadm07.report.view', 'iadm07', 'report.view', 'View on-screen security reports (IADM-07)'),
  ('iadm07.audit.view', 'iadm07', 'audit.view', 'View shared audit evidence for IADM-07 mutations'),
  ('iadm07.summary.view', 'iadm07', 'summary.view', 'College Admin published standards + institute posture summary counts (IADM-07)')
) AS v(code, module, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- R02 System Admin: manage/triage/propose; never approve/decide
INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT x.role_code, x.permission_code
FROM (VALUES
  ('SYSTEM_ADMIN', 'iadm07.posture.view'),
  ('SYSTEM_ADMIN', 'iadm07.register.read'),
  ('SYSTEM_ADMIN', 'iadm07.register.write'),
  ('SYSTEM_ADMIN', 'iadm07.standard.propose'),
  ('SYSTEM_ADMIN', 'iadm07.exception.request'),
  ('SYSTEM_ADMIN', 'iadm07.report.view'),
  ('SYSTEM_ADMIN', 'iadm07.audit.view'),
  -- R03 Institution Admin: own institute limited writes
  ('INSTITUTION_ADMIN', 'iadm07.posture.view'),
  ('INSTITUTION_ADMIN', 'iadm07.register.read'),
  ('INSTITUTION_ADMIN', 'iadm07.register.write'),
  ('INSTITUTION_ADMIN', 'iadm07.exception.request'),
  ('INSTITUTION_ADMIN', 'iadm07.report.view'),
  ('INSTITUTION_ADMIN', 'iadm07.audit.view'),
  -- R04 College Admin: summary only
  ('COLLEGE_ADMIN', 'iadm07.summary.view'),
  -- R28 Auditor: read-only, no export
  ('AUDITOR', 'iadm07.posture.view'),
  ('AUDITOR', 'iadm07.register.read'),
  ('AUDITOR', 'iadm07.report.view'),
  ('AUDITOR', 'iadm07.audit.view')
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
  AND r.code IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'AUDITOR')
  AND p.code LIKE 'iadm07.%'
  AND p.code NOT IN ('iadm07.standard.approve', 'iadm07.exception.decide', 'iadm07.signal.intake')
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id AND rp.permission_id = p.id
  );

COMMIT;
