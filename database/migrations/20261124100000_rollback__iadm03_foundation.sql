-- Rollback: 20261124100000_rollback__iadm03_foundation.sql
-- DEV only. Removes IADM-03 foundation keys/table; does not touch adm03.*.

BEGIN;

DROP POLICY IF EXISTS governance_idempotency_update ON iam.governance_idempotency;
DROP POLICY IF EXISTS governance_idempotency_insert ON iam.governance_idempotency;
DROP POLICY IF EXISTS governance_idempotency_select ON iam.governance_idempotency;
DROP TABLE IF EXISTS iam.governance_idempotency;

DELETE FROM iam.role_permissions rp
USING iam.permissions p
WHERE rp.permission_id = p.id
  AND p.code LIKE 'iam.%'
  AND p.code IN (
    'iam.catalog.roles.view','iam.catalog.permissions.view','iam.access.overview.view',
    'iam.access.subject.view','iam.profile.invite','iam.profile.view','iam.profile.status',
    'iam.grant.assign','iam.grant.revoke','iam.grant.r03.decide','iam.request.submit',
    'iam.request.decide','iam.delegation.create','iam.delegation.revoke',
    'iam.catalog.change.propose','iam.catalog.change.approve','iam.review.manage',
    'iam.audit.view','iam.governance.read','iam.identity.technical.assign'
  );

DELETE FROM iam.role_template_permissions
WHERE permission_code IN (
  'iam.catalog.roles.view','iam.catalog.permissions.view','iam.access.overview.view',
  'iam.access.subject.view','iam.profile.invite','iam.profile.view','iam.profile.status',
  'iam.grant.assign','iam.grant.revoke','iam.grant.r03.decide','iam.request.submit',
  'iam.request.decide','iam.delegation.create','iam.delegation.revoke',
  'iam.catalog.change.propose','iam.catalog.change.approve','iam.review.manage',
  'iam.audit.view','iam.governance.read','iam.identity.technical.assign'
);

UPDATE iam.permissions SET deleted_at = now(), updated_at = now()
WHERE code IN (
  'iam.catalog.roles.view','iam.catalog.permissions.view','iam.access.overview.view',
  'iam.access.subject.view','iam.profile.invite','iam.profile.view','iam.profile.status',
  'iam.grant.assign','iam.grant.revoke','iam.grant.r03.decide','iam.request.submit',
  'iam.request.decide','iam.delegation.create','iam.delegation.revoke',
  'iam.catalog.change.propose','iam.catalog.change.approve','iam.review.manage',
  'iam.audit.view','iam.governance.read','iam.identity.technical.assign'
)
AND deleted_at IS NULL;

COMMIT;
