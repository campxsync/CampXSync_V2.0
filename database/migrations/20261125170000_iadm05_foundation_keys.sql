-- Migration: 20261125170000_iadm05_foundation_keys.sql
-- Plain language: IADM-05 permission keys and role-template grants (US-023).
-- Closes catalogue gap G-01. No wildcard. View does not imply export/execute/delete/approve.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description
FROM (VALUES
  ('iadm05.classification.view', 'iadm05', 'classification.view', 'View classification catalogue (IADM-05)'),
  ('iadm05.classification.propose', 'iadm05', 'classification.propose', 'Propose classification revision (IADM-05)'),
  ('iadm05.retention.view', 'iadm05', 'retention.view', 'View retention register (IADM-05)'),
  ('iadm05.retention.propose', 'iadm05', 'retention.propose', 'Propose retention rule (IADM-05)'),
  ('iadm05.privacy.view', 'iadm05', 'privacy.view', 'View privacy/consent register (IADM-05)'),
  ('iadm05.privacy.propose', 'iadm05', 'privacy.propose', 'Propose privacy/consent revision (IADM-05)'),
  ('iadm05.policy.approve', 'iadm05', 'policy.approve', 'Approve or reject policy revision — R01 only (IADM-05)'),
  ('iadm05.policy.publish', 'iadm05', 'policy.publish', 'Publish approved policy version — R01 only (IADM-05)'),
  ('iadm05.policy.history', 'iadm05', 'policy.history', 'Inspect policy version history (IADM-05)'),
  ('iadm05.export.request', 'iadm05', 'export.request', 'Submit governed export request (IADM-05)'),
  ('iadm05.export.decide', 'iadm05', 'export.decide', 'Approve or reject export request — R01 (IADM-05)'),
  ('iadm05.erasure.request', 'iadm05', 'erasure.request', 'Submit erasure governance request (IADM-05)'),
  ('iadm05.erasure.decide', 'iadm05', 'erasure.decide', 'Review erasure outcomes (IADM-05)'),
  ('iadm05.erasure.close', 'iadm05', 'erasure.close', 'Close erasure when domains final — R01 (IADM-05)'),
  ('iadm05.sharing.view', 'iadm05', 'sharing.view', 'View data-sharing register (IADM-05)'),
  ('iadm05.sharing.propose', 'iadm05', 'sharing.propose', 'Propose data-sharing agreement (IADM-05)'),
  ('iadm05.sharing.decide', 'iadm05', 'sharing.decide', 'Approve or reject sharing agreement — R01 (IADM-05)'),
  ('iadm05.sharing.lifecycle', 'iadm05', 'sharing.lifecycle', 'Suspend/revoke/expire sharing agreement (IADM-05)'),
  ('iadm05.requests.operate', 'iadm05', 'requests.operate', 'Operate partial/failed domain dispatches (IADM-05)'),
  ('iadm05.adherence.view', 'iadm05', 'adherence.view', 'View own-institute policy adherence — R03 (IADM-05)'),
  ('iadm05.college.view', 'iadm05', 'college.view', 'View applicable policy and domain entry points — R04 (IADM-05)'),
  ('iadm05.audit.view', 'iadm05', 'audit.view', 'View governance audit evidence — R28 (IADM-05)'),
  ('iadm05.domain.contract', 'iadm05', 'domain.contract', 'Domain-service contract caller (IADM-05)')
) AS v(code, module, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- R02 System Admin: write/propose/request — never approve/publish/decide/close
INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT x.role_code, x.permission_code
FROM (VALUES
  ('SYSTEM_ADMIN', 'iadm05.classification.view'),
  ('SYSTEM_ADMIN', 'iadm05.classification.propose'),
  ('SYSTEM_ADMIN', 'iadm05.retention.view'),
  ('SYSTEM_ADMIN', 'iadm05.retention.propose'),
  ('SYSTEM_ADMIN', 'iadm05.privacy.view'),
  ('SYSTEM_ADMIN', 'iadm05.privacy.propose'),
  ('SYSTEM_ADMIN', 'iadm05.policy.history'),
  ('SYSTEM_ADMIN', 'iadm05.export.request'),
  ('SYSTEM_ADMIN', 'iadm05.erasure.request'),
  ('SYSTEM_ADMIN', 'iadm05.erasure.decide'),
  ('SYSTEM_ADMIN', 'iadm05.sharing.view'),
  ('SYSTEM_ADMIN', 'iadm05.sharing.propose'),
  ('SYSTEM_ADMIN', 'iadm05.requests.operate'),
  ('SYSTEM_ADMIN', 'iadm05.audit.view'),
  -- R03 Institution Admin: view-only adherence (G-16)
  ('INSTITUTION_ADMIN', 'iadm05.adherence.view'),
  ('INSTITUTION_ADMIN', 'iadm05.classification.view'),
  ('INSTITUTION_ADMIN', 'iadm05.retention.view'),
  ('INSTITUTION_ADMIN', 'iadm05.privacy.view'),
  ('INSTITUTION_ADMIN', 'iadm05.sharing.view'),
  ('INSTITUTION_ADMIN', 'iadm05.policy.history'),
  -- R04 College Admin: view + informational entry points (G-02)
  ('COLLEGE_ADMIN', 'iadm05.college.view'),
  ('COLLEGE_ADMIN', 'iadm05.classification.view'),
  ('COLLEGE_ADMIN', 'iadm05.retention.view'),
  ('COLLEGE_ADMIN', 'iadm05.privacy.view'),
  -- R28 Auditor: read-only
  ('AUDITOR', 'iadm05.classification.view'),
  ('AUDITOR', 'iadm05.retention.view'),
  ('AUDITOR', 'iadm05.privacy.view'),
  ('AUDITOR', 'iadm05.sharing.view'),
  ('AUDITOR', 'iadm05.policy.history'),
  ('AUDITOR', 'iadm05.audit.view')
) AS x(role_code, permission_code)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_template_permissions rtp
  WHERE rtp.role_code = x.role_code
    AND rtp.permission_code = x.permission_code
    AND rtp.deleted_at IS NULL
);

-- Propagate template grants into per-tenant role_permissions
INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.deleted_at IS NULL
JOIN iam.role_template_permissions rtp
  ON rtp.role_code = r.code AND rtp.permission_code = p.code AND rtp.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND r.code IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'AUDITOR')
  AND p.code LIKE 'iadm05.%'
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id AND rp.permission_id = p.id
  );

COMMIT;
