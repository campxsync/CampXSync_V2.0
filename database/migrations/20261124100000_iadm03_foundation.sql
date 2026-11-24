-- Migration: 20261124100000_iadm03_foundation.sql
-- Plain language: IADM-03 Identity & Access Governance foundation —
-- new iam.* permission keys, template grants, governance idempotency table.
-- Does NOT activate adm03.*; does NOT FORCE RLS on existing iam tables (separate approval).
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Owner: S0 decision pack APPROVED 2026-11-24

BEGIN;

-- -------------------------------------------------------------------------
-- 1. Permission catalogue (NEW keys — owner-approved S0 D3)
-- -------------------------------------------------------------------------
INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description
FROM (VALUES
  ('iam.catalog.roles.view', 'iam', 'catalog.roles.view', 'View role catalogue (IADM-03)'),
  ('iam.catalog.permissions.view', 'iam', 'catalog.permissions.view', 'View permission catalogue and mappings (IADM-03)'),
  ('iam.access.overview.view', 'iam', 'access.overview.view', 'View access overview counts (IADM-03)'),
  ('iam.access.subject.view', 'iam', 'access.subject.view', 'View a person access detail (IADM-03)'),
  ('iam.profile.invite', 'iam', 'profile.invite', 'Invite or create institute administrator profile (IADM-03)'),
  ('iam.profile.view', 'iam', 'profile.view', 'List or read institute administrator profiles (IADM-03)'),
  ('iam.profile.status', 'iam', 'profile.status', 'Set institute administrator profile active/inactive (IADM-03)'),
  ('iam.grant.assign', 'iam', 'grant.assign', 'Assign eligible role within grantor ceiling (IADM-03)'),
  ('iam.grant.revoke', 'iam', 'grant.revoke', 'Revoke eligible role within grantor ceiling (IADM-03)'),
  ('iam.grant.r03.decide', 'iam', 'grant.r03.decide', 'Approve or reject R03 grant change (IADM-03)'),
  ('iam.request.submit', 'iam', 'request.submit', 'Submit access request (IADM-03)'),
  ('iam.request.decide', 'iam', 'request.decide', 'Decide access request (IADM-03)'),
  ('iam.delegation.create', 'iam', 'delegation.create', 'Create scoped delegation (IADM-03)'),
  ('iam.delegation.revoke', 'iam', 'delegation.revoke', 'Revoke delegation (IADM-03)'),
  ('iam.catalog.change.propose', 'iam', 'catalog.change.propose', 'Propose role-permission mapping change (IADM-03)'),
  ('iam.catalog.change.approve', 'iam', 'catalog.change.approve', 'Approve role-permission mapping change (IADM-03)'),
  ('iam.review.manage', 'iam', 'review.manage', 'Start or decide access review (IADM-03)'),
  ('iam.audit.view', 'iam', 'audit.view', 'Read access audit evidence (IADM-03)'),
  ('iam.governance.read', 'iam', 'governance.read', 'Consumer governance/ceiling read (IADM-03)'),
  ('iam.identity.technical.assign', 'iam', 'identity.technical.assign', 'Assign platform technical identity (R02) (IADM-03)')
) AS v(code, module, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- -------------------------------------------------------------------------
-- 2. Role template grants (no wildcards; adm03 stays dormant)
-- R01 Super Admin is plat.platform_admins — enforced in Java + iam.is_platform_admin
-- -------------------------------------------------------------------------
INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT x.role_code, x.permission_code
FROM (VALUES
  -- R02 System Admin
  ('SYSTEM_ADMIN', 'iam.catalog.roles.view'),
  ('SYSTEM_ADMIN', 'iam.catalog.permissions.view'),
  ('SYSTEM_ADMIN', 'iam.access.overview.view'),
  ('SYSTEM_ADMIN', 'iam.profile.view'),
  ('SYSTEM_ADMIN', 'iam.identity.technical.assign'),
  ('SYSTEM_ADMIN', 'iam.governance.read'),
  ('SYSTEM_ADMIN', 'iam.audit.view'),
  -- R03 Institution Admin
  ('INSTITUTION_ADMIN', 'iam.catalog.roles.view'),
  ('INSTITUTION_ADMIN', 'iam.catalog.permissions.view'),
  ('INSTITUTION_ADMIN', 'iam.access.overview.view'),
  ('INSTITUTION_ADMIN', 'iam.access.subject.view'),
  ('INSTITUTION_ADMIN', 'iam.profile.invite'),
  ('INSTITUTION_ADMIN', 'iam.profile.view'),
  ('INSTITUTION_ADMIN', 'iam.profile.status'),
  ('INSTITUTION_ADMIN', 'iam.grant.assign'),
  ('INSTITUTION_ADMIN', 'iam.grant.revoke'),
  ('INSTITUTION_ADMIN', 'iam.grant.r03.decide'),
  ('INSTITUTION_ADMIN', 'iam.request.submit'),
  ('INSTITUTION_ADMIN', 'iam.request.decide'),
  ('INSTITUTION_ADMIN', 'iam.delegation.create'),
  ('INSTITUTION_ADMIN', 'iam.delegation.revoke'),
  ('INSTITUTION_ADMIN', 'iam.review.manage'),
  ('INSTITUTION_ADMIN', 'iam.audit.view'),
  -- R04 College Admin — request only
  ('COLLEGE_ADMIN', 'iam.request.submit'),
  -- R28 Auditor — read only
  ('AUDITOR', 'iam.catalog.roles.view'),
  ('AUDITOR', 'iam.catalog.permissions.view'),
  ('AUDITOR', 'iam.access.subject.view'),
  ('AUDITOR', 'iam.profile.view'),
  ('AUDITOR', 'iam.audit.view')
) AS x(role_code, permission_code)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_template_permissions rtp
  WHERE rtp.role_code = x.role_code
    AND rtp.permission_code = x.permission_code
    AND rtp.deleted_at IS NULL
);

-- Propagate to live tenant roles
INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.deleted_at IS NULL
JOIN iam.role_template_permissions rtp
  ON rtp.role_code = r.code AND rtp.permission_code = p.code AND rtp.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND r.code IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'AUDITOR')
  AND p.code LIKE 'iam.%'
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.tenant_id = r.tenant_id
  );

-- -------------------------------------------------------------------------
-- 3. Idempotency store (US-006)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS iam.governance_idempotency (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  idempotency_key text NOT NULL,
  operation text NOT NULL,
  status text NOT NULL DEFAULT 'IN_PROGRESS',
  result_payload jsonb,
  correlation_id text,
  tenant_id uuid,
  actor_user_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT uq_iam_gov_idempotency_key UNIQUE (idempotency_key)
);

ALTER TABLE iam.governance_idempotency ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS governance_idempotency_select ON iam.governance_idempotency;
CREATE POLICY governance_idempotency_select ON iam.governance_idempotency
  FOR SELECT TO authenticated
  USING (
    actor_user_id = (SELECT auth.uid())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS governance_idempotency_insert ON iam.governance_idempotency;
CREATE POLICY governance_idempotency_insert ON iam.governance_idempotency
  FOR INSERT TO authenticated
  WITH CHECK (
    actor_user_id = (SELECT auth.uid())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS governance_idempotency_update ON iam.governance_idempotency;
CREATE POLICY governance_idempotency_update ON iam.governance_idempotency
  FOR UPDATE TO authenticated
  USING (
    actor_user_id = (SELECT auth.uid())
    OR (SELECT iam.is_platform_admin())
  )
  WITH CHECK (
    actor_user_id = (SELECT auth.uid())
    OR (SELECT iam.is_platform_admin())
  );

GRANT SELECT, INSERT, UPDATE ON iam.governance_idempotency TO authenticated;
GRANT ALL ON iam.governance_idempotency TO service_role;

COMMIT;
