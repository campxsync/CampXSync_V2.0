-- Migration: 20261121000200_iadm_wave_a_lifecycle_and_config.sql
-- Description: IADM Wave A — institute lifecycle handover/requests/events + config lock/delegate/effective resolve
-- Target DB: azulbaxszsqxymqmlwfe (Dev Supabase ONLY)
-- Owner approval: Niranjan V.G. 2026-11-21
-- Stories: IADM-01-US-003/004/006/007/008, IADM-02-US-001/004/005/006


-- -------------------------------------------------------------------------
-- 0. Before snapshot helpers (idempotent columns)
-- -------------------------------------------------------------------------
ALTER TABLE cfg.setting_definitions
  ADD COLUMN IF NOT EXISTS platform_locked boolean NOT NULL DEFAULT false;

ALTER TABLE cfg.setting_definitions
  ADD COLUMN IF NOT EXISTS delegable_to_college boolean NOT NULL DEFAULT false;

COMMENT ON COLUMN cfg.setting_definitions.platform_locked IS
  'IADM-02: when true, institute/college cannot override (platform lock wins)';
COMMENT ON COLUMN cfg.setting_definitions.delegable_to_college IS
  'IADM-02: when true, Institution Admin may delegate this setting to a college';

-- -------------------------------------------------------------------------
-- 1. College Admin handover records (CADM-02 contract)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.college_admin_handovers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid NOT NULL,
  first_college_admin_user_id uuid NOT NULL,
  handed_over_by uuid NOT NULL,
  handed_over_at timestamptz NOT NULL DEFAULT now(),
  status text NOT NULL DEFAULT 'COMPLETED',
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_college_admin_handovers_status
    CHECK (status IN ('PENDING', 'COMPLETED', 'REVOKED')),
  CONSTRAINT uq_college_admin_handovers_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_college_admin_handovers_active_college
  ON plat.college_admin_handovers (tenant_id, college_id)
  WHERE deleted_at IS NULL AND status = 'COMPLETED';

CREATE INDEX IF NOT EXISTS ix_college_admin_handovers_tenant
  ON plat.college_admin_handovers (tenant_id);

COMMENT ON TABLE plat.college_admin_handovers IS
  'IADM-01-US-003/004: first College Admin (R04) handover from Institution Admin (R03) to CADM-02';

ALTER TABLE plat.college_admin_handovers ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.college_admin_handovers FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS college_admin_handovers_select ON plat.college_admin_handovers;
CREATE POLICY college_admin_handovers_select ON plat.college_admin_handovers
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND (
      (SELECT iam.perm_all('adm01.view'))
      OR (SELECT iam.perm_all('adm01.manage'))
      OR (SELECT iam.is_platform_admin())
    )
  );

DROP POLICY IF EXISTS college_admin_handovers_insert ON plat.college_admin_handovers;
CREATE POLICY college_admin_handovers_insert ON plat.college_admin_handovers
  FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND (
      (SELECT iam.perm_all('adm01.manage'))
      OR (SELECT iam.is_platform_admin())
    )
  );

DROP POLICY IF EXISTS college_admin_handovers_update ON plat.college_admin_handovers;
CREATE POLICY college_admin_handovers_update ON plat.college_admin_handovers
  FOR UPDATE TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.is_platform_admin()))
  )
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.is_platform_admin()))
  );

GRANT SELECT, INSERT, UPDATE ON plat.college_admin_handovers TO authenticated;
GRANT ALL ON plat.college_admin_handovers TO service_role;

-- -------------------------------------------------------------------------
-- 2. Institute lifecycle events (append-only timeline for Auditor)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.institute_lifecycle_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  event_type text NOT NULL,
  actor_id uuid,
  reason text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  CONSTRAINT ck_institute_lifecycle_events_type CHECK (event_type IN (
    'CREATE', 'APPOINT', 'SUSPEND', 'REACTIVATE',
    'DECOMMISSION_REQUEST', 'EXPORT_REQUEST', 'HANDOVER', 'STATUS_CHANGE'
  ))
);

CREATE INDEX IF NOT EXISTS ix_institute_lifecycle_events_tenant_created
  ON plat.institute_lifecycle_events (tenant_id, created_at DESC);

COMMENT ON TABLE plat.institute_lifecycle_events IS
  'IADM-01-US-008: append-only institute lifecycle timeline (no UPDATE/DELETE from app role)';

ALTER TABLE plat.institute_lifecycle_events ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.institute_lifecycle_events FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS institute_lifecycle_events_select ON plat.institute_lifecycle_events;
CREATE POLICY institute_lifecycle_events_select ON plat.institute_lifecycle_events
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS institute_lifecycle_events_insert ON plat.institute_lifecycle_events;
CREATE POLICY institute_lifecycle_events_insert ON plat.institute_lifecycle_events
  FOR INSERT TO authenticated
  WITH CHECK (
    (tenant_id = (SELECT core.current_tenant_id())
      AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.perm_all('adm01.write')) OR (SELECT iam.is_platform_admin())))
    OR (SELECT iam.is_platform_admin())
  );

-- No UPDATE/DELETE policies ? fail closed for mutation of history
GRANT SELECT, INSERT ON plat.institute_lifecycle_events TO authenticated;
GRANT ALL ON plat.institute_lifecycle_events TO service_role;

-- -------------------------------------------------------------------------
-- 3. Lifecycle requests (R03 request / R01 decide)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.institute_lifecycle_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  request_type text NOT NULL,
  requested_by uuid NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  reason text NOT NULL,
  decided_by uuid,
  decided_at timestamptz,
  decision_note text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_institute_lifecycle_requests_type
    CHECK (request_type IN ('SUSPEND', 'DECOMMISSION', 'EXPORT')),
  CONSTRAINT ck_institute_lifecycle_requests_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT uq_institute_lifecycle_requests_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_institute_lifecycle_requests_tenant_status
  ON plat.institute_lifecycle_requests (tenant_id, status)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.institute_lifecycle_requests IS
  'IADM-01-US-007: Institution Admin (R03) requests; Super Admin (R01) decides';

ALTER TABLE plat.institute_lifecycle_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.institute_lifecycle_requests FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS institute_lifecycle_requests_select ON plat.institute_lifecycle_requests;
CREATE POLICY institute_lifecycle_requests_select ON plat.institute_lifecycle_requests
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS institute_lifecycle_requests_insert ON plat.institute_lifecycle_requests;
CREATE POLICY institute_lifecycle_requests_insert ON plat.institute_lifecycle_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm01.write')) OR (SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.is_platform_admin()))
  );

DROP POLICY IF EXISTS institute_lifecycle_requests_update ON plat.institute_lifecycle_requests;
CREATE POLICY institute_lifecycle_requests_update ON plat.institute_lifecycle_requests
  FOR UPDATE TO authenticated
  USING ((SELECT iam.is_platform_admin()) OR (SELECT iam.perm_all('adm01.manage')))
  WITH CHECK ((SELECT iam.is_platform_admin()) OR (SELECT iam.perm_all('adm01.manage')));

GRANT SELECT, INSERT, UPDATE ON plat.institute_lifecycle_requests TO authenticated;
GRANT ALL ON plat.institute_lifecycle_requests TO service_role;

-- -------------------------------------------------------------------------
-- 4. Setting delegations (IADM-02-US-004)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS cfg.setting_delegations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL DEFAULT core.current_tenant_id(),
  college_id uuid NOT NULL,
  setting_key text NOT NULL,
  delegated_by uuid NOT NULL,
  delegated_at timestamptz NOT NULL DEFAULT now(),
  active boolean NOT NULL DEFAULT true,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT uq_setting_delegations_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_setting_delegations_active
  ON cfg.setting_delegations (tenant_id, college_id, setting_key)
  WHERE deleted_at IS NULL AND active = true;

COMMENT ON TABLE cfg.setting_delegations IS
  'IADM-02-US-004: Institution Admin delegates a college-level setting to a college';

ALTER TABLE cfg.setting_delegations ENABLE ROW LEVEL SECURITY;
ALTER TABLE cfg.setting_delegations FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS setting_delegations_select ON cfg.setting_delegations;
CREATE POLICY setting_delegations_select ON cfg.setting_delegations
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND (
      (SELECT iam.has_permission('adm05.view'))
      OR (SELECT iam.perm_all('adm05.write'))
      OR (SELECT iam.perm_all('adm05.manage'))
      OR (SELECT iam.is_platform_admin())
    )
  );

DROP POLICY IF EXISTS setting_delegations_write ON cfg.setting_delegations;
CREATE POLICY setting_delegations_write ON cfg.setting_delegations
  FOR ALL TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm05.write')) OR (SELECT iam.perm_all('adm05.manage')) OR (SELECT iam.is_platform_admin()))
  )
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm05.write')) OR (SELECT iam.perm_all('adm05.manage')) OR (SELECT iam.is_platform_admin()))
  );

GRANT SELECT, INSERT, UPDATE ON cfg.setting_delegations TO authenticated;
GRANT ALL ON cfg.setting_delegations TO service_role;

-- -------------------------------------------------------------------------
-- 5. Effective setting resolution (CADM-01 contract) — platform > institute > college
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION cfg.resolve_effective_setting(
  p_tenant_id uuid,
  p_college_id uuid,
  p_setting_key text
) RETURNS TABLE (
  setting_key text,
  effective_value jsonb,
  source text,
  platform_locked boolean,
  delegated boolean,
  denied boolean,
  deny_reason text
)
LANGUAGE plpgsql
STABLE
SECURITY INVOKER
SET search_path = ''
AS $$
DECLARE
  v_def cfg.setting_definitions%ROWTYPE;
  v_platform_val jsonb;
  v_institute_val jsonb;
  v_college_val jsonb;
  v_delegated boolean := false;
BEGIN
  SELECT * INTO v_def
  FROM cfg.setting_definitions d
  WHERE d.setting_key = p_setting_key AND d.deleted_at IS NULL
  LIMIT 1;

  IF NOT FOUND THEN
    setting_key := p_setting_key;
    effective_value := NULL;
    source := 'DENY';
    platform_locked := false;
    delegated := false;
    denied := true;
    deny_reason := 'SETTING_NOT_FOUND';
    RETURN NEXT;
    RETURN;
  END IF;

  -- Fail closed: empty allowed_scopes is unusable
  IF v_def.allowed_scopes IS NULL OR cardinality(v_def.allowed_scopes) = 0 THEN
    setting_key := p_setting_key;
    effective_value := NULL;
    source := 'DENY';
    platform_locked := COALESCE(v_def.platform_locked, false);
    delegated := false;
    denied := true;
    deny_reason := 'AMBIGUOUS_SCOPE';
    RETURN NEXT;
    RETURN;
  END IF;

  v_platform_val := v_def.default_value;

  SELECT v.value INTO v_institute_val
  FROM cfg.setting_values v
  WHERE v.tenant_id = p_tenant_id
    AND v.setting_key = p_setting_key
    AND v.scope = 'INSTITUTE'
    AND v.college_id IS NULL
    AND v.deleted_at IS NULL
  LIMIT 1;

  IF p_college_id IS NOT NULL THEN
    SELECT EXISTS (
      SELECT 1 FROM cfg.setting_delegations sd
      WHERE sd.tenant_id = p_tenant_id
        AND sd.college_id = p_college_id
        AND sd.setting_key = p_setting_key
        AND sd.active = true
        AND sd.deleted_at IS NULL
    ) INTO v_delegated;

    SELECT v.value INTO v_college_val
    FROM cfg.setting_values v
    WHERE v.tenant_id = p_tenant_id
      AND v.setting_key = p_setting_key
      AND v.scope = 'COLLEGE'
      AND v.college_id = p_college_id
      AND v.deleted_at IS NULL
    LIMIT 1;
  END IF;

  setting_key := p_setting_key;
  platform_locked := COALESCE(v_def.platform_locked, false);
  delegated := COALESCE(v_delegated, false);
  denied := false;
  deny_reason := NULL;

  IF platform_locked THEN
    effective_value := v_platform_val;
    source := 'PLATFORM';
    RETURN NEXT;
    RETURN;
  END IF;

  IF v_college_val IS NOT NULL AND p_college_id IS NOT NULL THEN
    IF NOT v_def.delegable_to_college OR NOT v_delegated THEN
      -- College value present but not authorized ? fail closed (do not silently use it)
      effective_value := COALESCE(v_institute_val, v_platform_val);
      source := CASE WHEN v_institute_val IS NOT NULL THEN 'INSTITUTE' ELSE 'PLATFORM' END;
      denied := true;
      deny_reason := 'COLLEGE_VALUE_NOT_DELEGATED';
      RETURN NEXT;
      RETURN;
    END IF;
    effective_value := v_college_val;
    source := 'COLLEGE';
    RETURN NEXT;
    RETURN;
  END IF;

  IF v_institute_val IS NOT NULL THEN
    effective_value := v_institute_val;
    source := 'INSTITUTE';
    RETURN NEXT;
    RETURN;
  END IF;

  effective_value := v_platform_val;
  source := 'PLATFORM';
  RETURN NEXT;
END;
$$;

COMMENT ON FUNCTION cfg.resolve_effective_setting(uuid, uuid, text) IS
  'IADM-02-US-005: effective config resolution platform > institute > college with lock/delegation; fail closed';

REVOKE ALL ON FUNCTION cfg.resolve_effective_setting(uuid, uuid, text) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION cfg.resolve_effective_setting(uuid, uuid, text) TO authenticated, service_role;

