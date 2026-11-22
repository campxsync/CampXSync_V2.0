-- =============================================================================
-- Migration: 20261121001100_iadm_wave_f_pending_close.sql
-- Stories: IADM-04-US-002/005, IADM-05-US-001
-- Environment: DEV ONLY
-- =============================================================================

-- 1. Platform retention floors + institute retention policies
CREATE TABLE IF NOT EXISTS plat.retention_minimums (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  entity_type text NOT NULL,
  min_days integer NOT NULL,
  description text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_retention_minimums_days CHECK (min_days > 0)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_retention_minimums_entity
  ON plat.retention_minimums (entity_type) WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.retention_minimums IS
  'IADM-05-US-001: platform floor — institute retention_days must be >= min_days';

ALTER TABLE plat.retention_minimums ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.retention_minimums FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS retention_minimums_select ON plat.retention_minimums;
CREATE POLICY retention_minimums_select ON plat.retention_minimums
  FOR SELECT TO authenticated USING (true);

DROP POLICY IF EXISTS retention_minimums_write ON plat.retention_minimums;
CREATE POLICY retention_minimums_write ON plat.retention_minimums
  FOR ALL TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

GRANT SELECT ON plat.retention_minimums TO authenticated;
GRANT ALL ON plat.retention_minimums TO service_role;

CREATE TABLE IF NOT EXISTS plat.institute_retention_policies (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  policy_code text NOT NULL,
  entity_type text NOT NULL,
  retention_days integer NOT NULL,
  action text NOT NULL DEFAULT 'ARCHIVE',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_institute_retention_days CHECK (retention_days > 0),
  CONSTRAINT ck_institute_retention_action CHECK (action IN ('ARCHIVE', 'DELETE', 'ANONYMIZE')),
  CONSTRAINT uq_institute_retention_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_institute_retention_code
  ON plat.institute_retention_policies (tenant_id, policy_code) WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.institute_retention_policies IS
  'IADM-05-US-001: institute retention rules; must meet plat.retention_minimums';

ALTER TABLE plat.institute_retention_policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.institute_retention_policies FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS institute_retention_select ON plat.institute_retention_policies;
CREATE POLICY institute_retention_select ON plat.institute_retention_policies
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS institute_retention_insert ON plat.institute_retention_policies;
CREATE POLICY institute_retention_insert ON plat.institute_retention_policies
  FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('iadm05.write')) OR (SELECT iam.is_platform_admin()))
  );

DROP POLICY IF EXISTS institute_retention_update ON plat.institute_retention_policies;
CREATE POLICY institute_retention_update ON plat.institute_retention_policies
  FOR UPDATE TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('iadm05.write')) OR (SELECT iam.is_platform_admin()))
  )
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('iadm05.write')) OR (SELECT iam.is_platform_admin()))
  );

GRANT SELECT, INSERT, UPDATE ON plat.institute_retention_policies TO authenticated;
GRANT ALL ON plat.institute_retention_policies TO service_role;

INSERT INTO plat.retention_minimums (entity_type, min_days, description)
SELECT v.entity_type, v.min_days, v.description
FROM (VALUES
  ('AUDIT_EVENTS', 365, 'Minimum audit event retention'),
  ('GENERIC', 90, 'Default minimum for unclassified entities')
) AS v(entity_type, min_days, description)
WHERE NOT EXISTS (
  SELECT 1 FROM plat.retention_minimums m WHERE m.entity_type = v.entity_type AND m.deleted_at IS NULL
);

-- 2. Billing RLS: platform admin + adm01.manage can mutate subscriptions/invoices
DROP POLICY IF EXISTS subscriptions_select ON plat.subscriptions;
CREATE POLICY subscriptions_select ON plat.subscriptions
  FOR SELECT TO authenticated
  USING (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.read'))
      OR (SELECT iam.perm_all('iadm04.view'))
      OR (SELECT iam.perm_all('adm01.manage'))
      OR (SELECT iam.perm_all('adm01.view'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS subscriptions_insert ON plat.subscriptions;
CREATE POLICY subscriptions_insert ON plat.subscriptions
  FOR INSERT TO authenticated
  WITH CHECK (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.write'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS subscriptions_update ON plat.subscriptions;
CREATE POLICY subscriptions_update ON plat.subscriptions
  FOR UPDATE TO authenticated
  USING (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.write'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  )
  WITH CHECK (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.write'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS invoices_select ON plat.invoices;
CREATE POLICY invoices_select ON plat.invoices
  FOR SELECT TO authenticated
  USING (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.read'))
      OR (SELECT iam.perm_all('iadm04.view'))
      OR (SELECT iam.perm_all('adm01.manage'))
      OR (SELECT iam.perm_all('adm01.view'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS invoices_insert ON plat.invoices;
CREATE POLICY invoices_insert ON plat.invoices
  FOR INSERT TO authenticated
  WITH CHECK (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.write'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS invoices_update ON plat.invoices;
CREATE POLICY invoices_update ON plat.invoices
  FOR UPDATE TO authenticated
  USING (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.write'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  )
  WITH CHECK (
    (tenant_id = (SELECT core.current_tenant_id()) AND (
      (SELECT iam.has_permission('plat.write'))
      OR (SELECT iam.perm_all('adm01.manage'))
    ))
    OR (SELECT iam.is_platform_admin())
  );
