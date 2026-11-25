-- Migration: 20261125110000_iadm04_foundation_keys.sql
-- Plain language: IADM-04 permission keys (owner-approved). Idempotent — safe if
-- rolled-back Wave D left orphan iadm04.* rows.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description
FROM (VALUES
  ('iadm04.plans.view', 'iadm04', 'plans.view', 'View subscription plan catalogue (IADM-04)'),
  ('iadm04.plans.manage', 'iadm04', 'plans.manage', 'Create/update/deprecate plans and pricing (IADM-04)'),
  ('iadm04.entitlements.map', 'iadm04', 'entitlements.map', 'Map plans to feature flags and quotas (IADM-04)'),
  ('iadm04.subscriptions.view', 'iadm04', 'subscriptions.view', 'View subscriptions across institutes (IADM-04)'),
  ('iadm04.subscriptions.view.own', 'iadm04', 'subscriptions.view.own', 'View own institute subscription (IADM-04)'),
  ('iadm04.plan_change.request', 'iadm04', 'plan_change.request', 'Request plan change for own institute (IADM-04)'),
  ('iadm04.plan_change.decide', 'iadm04', 'plan_change.decide', 'Approve or reject plan-change request (IADM-04)'),
  ('iadm04.subscriptions.attach', 'iadm04', 'subscriptions.attach', 'Attach plan/trial during onboarding (IADM-04)'),
  ('iadm04.entitlements.check', 'iadm04', 'entitlements.check', 'Service entitlement-check contract (IADM-04)'),
  ('iadm04.invoices.manage', 'iadm04', 'invoices.manage', 'Generate/view/void invoices (IADM-04)'),
  ('iadm04.invoices.view.own', 'iadm04', 'invoices.view.own', 'View own institute invoices (IADM-04)'),
  ('iadm04.payments.offline.record', 'iadm04', 'payments.offline.record', 'Record offline payment evidence (IADM-04)'),
  ('iadm04.payments.offline.reconcile', 'iadm04', 'payments.offline.reconcile', 'Reconcile offline payment evidence (IADM-04)'),
  ('iadm04.payments.online.initiate', 'iadm04', 'payments.online.initiate', 'Initiate online payment (IADM-04)'),
  ('iadm04.payments.callback', 'iadm04', 'payments.callback', 'Payment provider callback ingress (IADM-04)'),
  ('iadm04.payments.callback.retry', 'iadm04', 'payments.callback.retry', 'Retry failed payment callback (IADM-04)'),
  ('iadm04.usage.record', 'iadm04', 'usage.record', 'Record usage measurements (IADM-04)'),
  ('iadm04.reports.view', 'iadm04', 'reports.view', 'View billing reports (IADM-04)'),
  ('iadm04.audit.view', 'iadm04', 'audit.view', 'View billing audit evidence (IADM-04)'),
  ('iadm04.jobs.run', 'iadm04', 'jobs.run', 'Run billing scheduled jobs (IADM-04)')
) AS v(code, module, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- Soft-revive any soft-deleted orphan keys from the rolled-back Wave D attempt
UPDATE iam.permissions
SET deleted_at = NULL,
    description = COALESCE(description, 'IADM-04 (revived)'),
    updated_at = now()
WHERE code LIKE 'iadm04.%'
  AND deleted_at IS NOT NULL;

-- Role template grants (R01 via platform_admins; templates for R02/R03/R28)
INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT x.role_code, x.permission_code
FROM (VALUES
  ('SYSTEM_ADMIN', 'iadm04.plans.view'),
  ('SYSTEM_ADMIN', 'iadm04.subscriptions.view'),
  ('SYSTEM_ADMIN', 'iadm04.entitlements.check'),
  ('SYSTEM_ADMIN', 'iadm04.payments.callback.retry'),
  ('SYSTEM_ADMIN', 'iadm04.reports.view'),
  ('SYSTEM_ADMIN', 'iadm04.audit.view'),
  ('INSTITUTION_ADMIN', 'iadm04.subscriptions.view.own'),
  ('INSTITUTION_ADMIN', 'iadm04.plan_change.request'),
  ('INSTITUTION_ADMIN', 'iadm04.invoices.view.own'),
  ('INSTITUTION_ADMIN', 'iadm04.reports.view'),
  ('AUDITOR', 'iadm04.plans.view'),
  ('AUDITOR', 'iadm04.subscriptions.view'),
  ('AUDITOR', 'iadm04.reports.view'),
  ('AUDITOR', 'iadm04.audit.view')
) AS x(role_code, permission_code)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_template_permissions rtp
  WHERE rtp.role_code = x.role_code
    AND rtp.permission_code = x.permission_code
    AND rtp.deleted_at IS NULL
);

INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.deleted_at IS NULL
JOIN iam.role_template_permissions rtp
  ON rtp.role_code = r.code AND rtp.permission_code = p.code AND rtp.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND r.code IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'AUDITOR')
  AND p.code LIKE 'iadm04.%'
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id AND rp.permission_id = p.id
  );

COMMIT;
