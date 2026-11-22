-- =============================================================================
-- Migration: 20261121000900_iadm_wave_f_partial_close.sql
-- Description: IADM Wave F — DDL to close PARTIAL acceptance gaps
-- Environment: DEV ONLY (azulbaxszsqxymqmlwfe)
-- Stories: IADM-02-US-007, IADM-04-US-004, IADM-06-US-002, IADM-07-US-001/004
-- Note: IADM-08-US-003 uses existing audit.retention_policies (no new table)
-- =============================================================================

-- -------------------------------------------------------------------------
-- 1. cfg.setting_value_versions + snapshot trigger (IADM-02-US-007)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cfg.setting_value_versions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  setting_key text NOT NULL,
  scope text NOT NULL,
  college_id uuid,
  value jsonb NOT NULL,
  setting_value_id uuid,
  version_no integer NOT NULL DEFAULT 1,
  changed_by uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  CONSTRAINT ck_setting_value_versions_scope CHECK (scope IN ('INSTITUTE', 'COLLEGE')),
  CONSTRAINT uq_setting_value_versions_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_setting_value_versions_lookup
  ON cfg.setting_value_versions (tenant_id, setting_key, created_at DESC);

COMMENT ON TABLE cfg.setting_value_versions IS
  'IADM-02-US-007: append-only value versions for soft rollback';

ALTER TABLE cfg.setting_value_versions ENABLE ROW LEVEL SECURITY;
ALTER TABLE cfg.setting_value_versions FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS setting_value_versions_select ON cfg.setting_value_versions;
CREATE POLICY setting_value_versions_select ON cfg.setting_value_versions
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS setting_value_versions_insert ON cfg.setting_value_versions;
CREATE POLICY setting_value_versions_insert ON cfg.setting_value_versions
  FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

GRANT SELECT, INSERT ON cfg.setting_value_versions TO authenticated;
GRANT ALL ON cfg.setting_value_versions TO service_role;

CREATE OR REPLACE FUNCTION cfg.snapshot_setting_value_version()
RETURNS trigger
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = cfg, pg_temp
AS $$
DECLARE
  v_no integer;
BEGIN
  IF TG_OP = 'DELETE' THEN
    RETURN OLD;
  END IF;
  SELECT COALESCE(MAX(version_no), 0) + 1 INTO v_no
  FROM cfg.setting_value_versions
  WHERE tenant_id = NEW.tenant_id
    AND setting_key = NEW.setting_key
    AND scope = NEW.scope
    AND (college_id IS NOT DISTINCT FROM NEW.college_id);
  INSERT INTO cfg.setting_value_versions (
    tenant_id, setting_key, scope, college_id, value, setting_value_id, version_no, changed_by, created_by
  ) VALUES (
    NEW.tenant_id, NEW.setting_key, NEW.scope, NEW.college_id, NEW.value, NEW.id, v_no,
    COALESCE(NEW.updated_by, NEW.created_by), COALESCE(NEW.updated_by, NEW.created_by)
  );
  RETURN NEW;
END;
$$;

DROP TRIGGER IF EXISTS trg_setting_value_version ON cfg.setting_values;
CREATE TRIGGER trg_setting_value_version
  AFTER INSERT OR UPDATE OF value ON cfg.setting_values
  FOR EACH ROW
  WHEN (NEW.deleted_at IS NULL)
  EXECUTE FUNCTION cfg.snapshot_setting_value_version();

-- -------------------------------------------------------------------------
-- 2. plat.plan_change_requests (IADM-04-US-004)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.plan_change_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  requested_by uuid NOT NULL,
  current_plan_code text,
  requested_plan_code text NOT NULL,
  request_type text NOT NULL DEFAULT 'PLAN_CHANGE',
  status text NOT NULL DEFAULT 'PENDING',
  reason text,
  decided_by uuid,
  decided_at timestamptz,
  decision_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_plan_change_requests_type CHECK (request_type IN ('PLAN_CHANGE', 'ADD_ON')),
  CONSTRAINT ck_plan_change_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT uq_plan_change_requests_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_plan_change_requests_tenant_status
  ON plat.plan_change_requests (tenant_id, status)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.plan_change_requests IS
  'IADM-04-US-004: Institution Admin plan-change / add-on requests; Super Admin decides';

ALTER TABLE plat.plan_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.plan_change_requests FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS plan_change_requests_select ON plat.plan_change_requests;
CREATE POLICY plan_change_requests_select ON plat.plan_change_requests
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS plan_change_requests_insert ON plat.plan_change_requests;
CREATE POLICY plan_change_requests_insert ON plat.plan_change_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('iadm04.write')) OR (SELECT iam.perm_all('adm01.write'))
      OR (SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.is_platform_admin()))
  );

DROP POLICY IF EXISTS plan_change_requests_update ON plat.plan_change_requests;
CREATE POLICY plan_change_requests_update ON plat.plan_change_requests
  FOR UPDATE TO authenticated
  USING ((SELECT iam.is_platform_admin()) OR (SELECT iam.perm_all('adm01.manage')))
  WITH CHECK ((SELECT iam.is_platform_admin()) OR (SELECT iam.perm_all('adm01.manage')));

GRANT SELECT, INSERT, UPDATE ON plat.plan_change_requests TO authenticated;
GRANT ALL ON plat.plan_change_requests TO service_role;

-- -------------------------------------------------------------------------
-- 3. plat.usage_metrics.college_id (IADM-06-US-002)
-- -------------------------------------------------------------------------
ALTER TABLE plat.usage_metrics
  ADD COLUMN IF NOT EXISTS college_id uuid;

CREATE INDEX IF NOT EXISTS ix_usage_metrics_tenant_college
  ON plat.usage_metrics (tenant_id, college_id)
  WHERE deleted_at IS NULL AND college_id IS NOT NULL;

COMMENT ON COLUMN plat.usage_metrics.college_id IS
  'IADM-06-US-002: optional college scope for institute usage breakdown';

-- -------------------------------------------------------------------------
-- 4. plat.platform_security_minimums (IADM-07-US-001/004)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.platform_security_minimums (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_key text NOT NULL,
  minimum_value jsonb NOT NULL DEFAULT '{}'::jsonb,
  description text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_platform_security_minimums_key
  ON plat.platform_security_minimums (policy_key)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.platform_security_minimums IS
  'IADM-07-US-004: platform floors; institute policies must meet or exceed';

ALTER TABLE plat.platform_security_minimums ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.platform_security_minimums FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS platform_security_minimums_select ON plat.platform_security_minimums;
CREATE POLICY platform_security_minimums_select ON plat.platform_security_minimums
  FOR SELECT TO authenticated
  USING (true);

DROP POLICY IF EXISTS platform_security_minimums_write ON plat.platform_security_minimums;
CREATE POLICY platform_security_minimums_write ON plat.platform_security_minimums
  FOR ALL TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

GRANT SELECT ON plat.platform_security_minimums TO authenticated;
GRANT ALL ON plat.platform_security_minimums TO service_role;
