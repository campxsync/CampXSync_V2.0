-- Migration: 20261123010000_iadm02_foundation.sql
-- Plain language: IADM-02 Platform Configuration foundation — new draft/version tables,
-- permission keys, catalogue key for delegation max validity, FORCE RLS on new tables
-- and tightened setting_definitions (no other service references it).
-- DOES NOT: FORCE/tighten cfg.policies; freeze cfg.setting_values (other services write them).
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Owner decisions: IADM-02 S0 2026-11-23

BEGIN;

-- -------------------------------------------------------------------------
-- 1. Permission catalogue (NEW keys)
-- -------------------------------------------------------------------------
INSERT INTO iam.permissions (code, module, action, description)
SELECT v.code, v.module, v.action, v.description
FROM (VALUES
  ('cfg.catalog.view', 'cfg', 'catalog.view', 'View platform configuration catalogue (IADM-02)'),
  ('cfg.draft', 'cfg', 'draft', 'Create/update/validate configuration drafts'),
  ('cfg.approve', 'cfg', 'approve', 'Approve or reject configuration drafts'),
  ('cfg.publish', 'cfg', 'publish', 'Publish configuration versions'),
  ('cfg.policy.draft', 'cfg', 'policy.draft', 'Propose policy-definition drafts'),
  ('cfg.consumer.read', 'cfg', 'consumer.read', 'Service identity effective-configuration read')
) AS v(code, module, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- -------------------------------------------------------------------------
-- 2. Role template grants (no wildcards)
-- R01 Super Admin is plat.platform_admins (SUPER_ADMIN) — enforced in Java + RLS is_platform_admin
-- R02 System Admin: template SYSTEM_ADMIN if/when present + platform_admins SYSTEM_ADMIN in Java
-- -------------------------------------------------------------------------
INSERT INTO iam.role_templates (code, name, catalogue_id, kind, scope_note)
SELECT 'SYSTEM_ADMIN', 'System Admin', 'R02', 'PLATFORM', 'IADM-02 technical ceiling'
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_templates t WHERE t.code = 'SYSTEM_ADMIN' AND t.deleted_at IS NULL
);

INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT x.role_code, x.permission_code
FROM (VALUES
  ('SYSTEM_ADMIN', 'cfg.catalog.view'),
  ('SYSTEM_ADMIN', 'cfg.draft'),
  ('INSTITUTION_ADMIN', 'cfg.catalog.view'),
  ('AUDITOR', 'cfg.catalog.view')
) AS x(role_code, permission_code)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_template_permissions rtp
  WHERE rtp.role_code = x.role_code
    AND rtp.permission_code = x.permission_code
    AND rtp.deleted_at IS NULL
);

-- Propagate to live tenant roles (INSTITUTION_ADMIN, AUDITOR, SYSTEM_ADMIN)
INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND (
    (r.code = 'INSTITUTION_ADMIN' AND p.code = 'cfg.catalog.view')
    OR (r.code = 'AUDITOR' AND p.code = 'cfg.catalog.view')
    OR (r.code = 'SYSTEM_ADMIN' AND p.code IN ('cfg.catalog.view', 'cfg.draft'))
  )
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.tenant_id = r.tenant_id
  );

-- Ensure SYSTEM_ADMIN role row exists in every tenant that has role templates seeded
INSERT INTO iam.roles (tenant_id, code, name)
SELECT DISTINCT r.tenant_id, 'SYSTEM_ADMIN', 'System Admin'
FROM iam.roles r
WHERE r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM iam.roles x
    WHERE x.tenant_id = r.tenant_id AND x.code = 'SYSTEM_ADMIN' AND x.deleted_at IS NULL
  );

INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.permissions p ON p.code IN ('cfg.catalog.view', 'cfg.draft') AND p.deleted_at IS NULL
WHERE r.code = 'SYSTEM_ADMIN' AND r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.role_id = r.id AND rp.permission_id = p.id AND rp.tenant_id = r.tenant_id
  );

-- -------------------------------------------------------------------------
-- 3. New key: iam.delegation.max_validity_days
-- -------------------------------------------------------------------------
INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT
  'iam.delegation.max_validity_days',
  'INTEGER',
  '90'::jsonb,
  false,
  ARRAY['INSTITUTE']::text[],
  'Maximum IAM delegation validity in days. Validation bounds: INTEGER 1..365.'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d
  WHERE d.setting_key = 'iam.delegation.max_validity_days' AND d.deleted_at IS NULL
);

-- -------------------------------------------------------------------------
-- 4. Draft + version tables
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cfg.config_drafts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  setting_key text,
  policy_code text,
  proposed_value jsonb,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'DRAFT',
  validation_errors jsonb NOT NULL DEFAULT '[]'::jsonb,
  author_user_id uuid NOT NULL,
  approver_user_id uuid,
  decision_reason text,
  idempotency_key text,
  correlation_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_config_drafts_status CHECK (status IN (
    'DRAFT', 'VALIDATED', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED'
  )),
  CONSTRAINT ck_config_drafts_target CHECK (
    (setting_key IS NOT NULL AND policy_code IS NULL)
    OR (setting_key IS NULL AND policy_code IS NOT NULL)
  )
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_config_drafts_idempotency
  ON cfg.config_drafts (idempotency_key)
  WHERE deleted_at IS NULL AND idempotency_key IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_config_drafts_setting_key
  ON cfg.config_drafts (setting_key)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_config_drafts_status
  ON cfg.config_drafts (status)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE cfg.config_drafts IS
  'IADM-02: unpublished configuration/policy drafts. Not visible to consumers.';

CREATE TABLE IF NOT EXISTS cfg.config_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  version_label text NOT NULL,
  status text NOT NULL DEFAULT 'PUBLISHED',
  effective_from timestamptz NOT NULL DEFAULT now(),
  release_notes text,
  published_by uuid,
  supersedes_version_id uuid,
  checksum text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  idempotency_key text,
  correlation_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_config_versions_status CHECK (status IN ('PUBLISHED', 'SUPERSEDED'))
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_config_versions_idempotency
  ON cfg.config_versions (idempotency_key)
  WHERE deleted_at IS NULL AND idempotency_key IS NOT NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_config_versions_active
  ON cfg.config_versions ((status))
  WHERE deleted_at IS NULL AND status = 'PUBLISHED';

CREATE INDEX IF NOT EXISTS ix_config_versions_label
  ON cfg.config_versions (version_label)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE cfg.config_versions IS
  'IADM-02: immutable published configuration snapshots. Effective value = active PUBLISHED row.';

-- -------------------------------------------------------------------------
-- 5. RLS — new tables FORCE; drafts only for draft/approve/publish holders or platform admin
-- -------------------------------------------------------------------------
ALTER TABLE cfg.config_drafts ENABLE ROW LEVEL SECURITY;
ALTER TABLE cfg.config_drafts FORCE ROW LEVEL SECURITY;
ALTER TABLE cfg.config_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE cfg.config_versions FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS config_drafts_select ON cfg.config_drafts;
CREATE POLICY config_drafts_select ON cfg.config_drafts
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.draft'))
    OR (SELECT iam.has_permission('cfg.approve'))
    OR (SELECT iam.has_permission('cfg.publish'))
  );

DROP POLICY IF EXISTS config_drafts_write ON cfg.config_drafts;
CREATE POLICY config_drafts_write ON cfg.config_drafts
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.draft'))
    OR (SELECT iam.has_permission('cfg.approve'))
    OR (SELECT iam.has_permission('cfg.publish'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.draft'))
    OR (SELECT iam.has_permission('cfg.approve'))
    OR (SELECT iam.has_permission('cfg.publish'))
  );

DROP POLICY IF EXISTS config_versions_select ON cfg.config_versions;
CREATE POLICY config_versions_select ON cfg.config_versions
  FOR SELECT TO authenticated
  USING (
    (status = 'PUBLISHED' AND (
      (SELECT iam.is_platform_admin())
      OR (SELECT iam.has_permission('cfg.catalog.view'))
      OR (SELECT iam.has_permission('cfg.view'))
      OR (SELECT iam.has_permission('cfg.consumer.read'))
    ))
    OR (
      (SELECT iam.is_platform_admin())
      OR (SELECT iam.has_permission('cfg.draft'))
      OR (SELECT iam.has_permission('cfg.approve'))
      OR (SELECT iam.has_permission('cfg.publish'))
    )
  );

DROP POLICY IF EXISTS config_versions_write ON cfg.config_versions;
CREATE POLICY config_versions_write ON cfg.config_versions
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.publish'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.publish'))
  );

GRANT SELECT, INSERT, UPDATE, DELETE ON cfg.config_drafts TO authenticated;
GRANT SELECT, INSERT, UPDATE, DELETE ON cfg.config_versions TO authenticated;

-- -------------------------------------------------------------------------
-- 6. setting_definitions — FORCE RLS + tighten (no service Java references found)
-- -------------------------------------------------------------------------
ALTER TABLE cfg.setting_definitions FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS setting_definitions_read ON cfg.setting_definitions;
CREATE POLICY setting_definitions_read ON cfg.setting_definitions
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.catalog.view'))
    OR (SELECT iam.has_permission('cfg.view'))
    OR (SELECT iam.has_permission('cfg.consumer.read'))
  );

DROP POLICY IF EXISTS setting_definitions_platform ON cfg.setting_definitions;
CREATE POLICY setting_definitions_platform ON cfg.setting_definitions
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.publish'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('cfg.publish'))
  );

COMMIT;
