-- Migration: 20261122010000_iadm01_lifecycle_foundation.sql
-- Plain language: Recreate IADM-01 (Institute & Tenant Lifecycle) tables after the 2026-11-22 rollback.
-- New version number — do not reuse rolled-back 20261121000200… versions still listed in schema_migrations.
-- Target DB: azulbaxszsqxymqmlwfe (DEV only)
-- Owner approval: Niranjan V.G. 2026-11-22 for IADM-01 S0–S4 DEV

BEGIN;

-- -------------------------------------------------------------------------
-- 0. Auth-linked helper (was 20261121000700; removed by rollback)
-- Plain language: true when the UUID exists as a Supabase Auth login (auth.users).
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION plat.is_auth_linked_user(p_user_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
  SELECT CASE
    WHEN p_user_id IS NULL THEN false
    ELSE EXISTS (SELECT 1 FROM auth.users u WHERE u.id = p_user_id)
  END;
$$;

COMMENT ON FUNCTION plat.is_auth_linked_user(uuid) IS
  'IADM-01: true when user id exists in auth.users (Supabase Auth login).';

REVOKE ALL ON FUNCTION plat.is_auth_linked_user(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.is_auth_linked_user(uuid) TO authenticated, service_role;

-- -------------------------------------------------------------------------
-- 1. Suspended-institute helper (Java session gate)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION plat.is_tenant_suspended(p_tenant_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY INVOKER
SET search_path = ''
AS $$
  SELECT EXISTS (
    SELECT 1 FROM core.tenants t
    WHERE t.id = p_tenant_id
      AND t.deleted_at IS NULL
      AND upper(t.status) = 'SUSPENDED'
  );
$$;

COMMENT ON FUNCTION plat.is_tenant_suspended(uuid) IS
  'IADM-01: true when institute (tenant) status is SUSPENDED.';

REVOKE ALL ON FUNCTION plat.is_tenant_suspended(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.is_tenant_suspended(uuid) TO authenticated, service_role;

-- -------------------------------------------------------------------------
-- 2. Onboarding step store (TECH-006 / US-013)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.iadm01_onboarding_steps (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid,
  workflow_kind text NOT NULL DEFAULT 'FIRST_R03',
  step_key text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  correlation_id text,
  last_error text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_iadm01_onboarding_workflow
    CHECK (workflow_kind IN ('FIRST_R03', 'FIRST_R04')),
  CONSTRAINT ck_iadm01_onboarding_status
    CHECK (status IN ('PENDING', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'RETRYABLE')),
  CONSTRAINT uq_iadm01_onboarding_steps_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_iadm01_onboarding_active_step_institute
  ON plat.iadm01_onboarding_steps (tenant_id, workflow_kind, step_key)
  WHERE deleted_at IS NULL AND college_id IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_iadm01_onboarding_active_step_college
  ON plat.iadm01_onboarding_steps (tenant_id, workflow_kind, step_key, college_id)
  WHERE deleted_at IS NULL AND college_id IS NOT NULL;

CREATE INDEX IF NOT EXISTS ix_iadm01_onboarding_tenant
  ON plat.iadm01_onboarding_steps (tenant_id)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.iadm01_onboarding_steps IS
  'IADM-01: per-step onboarding status for first Institution Admin (R03) / College Admin (R04).';

ALTER TABLE plat.iadm01_onboarding_steps ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.iadm01_onboarding_steps FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS iadm01_onboarding_steps_select ON plat.iadm01_onboarding_steps;
CREATE POLICY iadm01_onboarding_steps_select ON plat.iadm01_onboarding_steps
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS iadm01_onboarding_steps_insert ON plat.iadm01_onboarding_steps;
CREATE POLICY iadm01_onboarding_steps_insert ON plat.iadm01_onboarding_steps
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.perm_all('adm01.write')))
    )
  );

DROP POLICY IF EXISTS iadm01_onboarding_steps_update ON plat.iadm01_onboarding_steps;
CREATE POLICY iadm01_onboarding_steps_update ON plat.iadm01_onboarding_steps
  FOR UPDATE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.perm_all('adm01.write')))
    )
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.perm_all('adm01.write')))
    )
  );

GRANT SELECT, INSERT, UPDATE ON plat.iadm01_onboarding_steps TO authenticated;
GRANT ALL ON plat.iadm01_onboarding_steps TO service_role;

-- -------------------------------------------------------------------------
-- 3. Lifecycle events (append-only timeline)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.institute_lifecycle_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid,
  event_type text NOT NULL,
  actor_id uuid,
  reason text,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  CONSTRAINT ck_institute_lifecycle_events_type CHECK (event_type IN (
    'CREATE', 'APPOINT', 'SUSPEND', 'REACTIVATE', 'ARCHIVE',
    'DECOMMISSION_REQUEST', 'EXPORT_REQUEST', 'HANDOVER', 'STATUS_CHANGE',
    'ONBOARDING_STEP', 'COLLEGE_CREATE', 'COLLEGE_UPDATE', 'R04_APPOINT'
  ))
);

CREATE INDEX IF NOT EXISTS ix_institute_lifecycle_events_tenant_created
  ON plat.institute_lifecycle_events (tenant_id, created_at DESC);

COMMENT ON TABLE plat.institute_lifecycle_events IS
  'IADM-01: append-only institute/college lifecycle timeline.';

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
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (
        (SELECT iam.perm_all('adm01.manage'))
        OR (SELECT iam.perm_all('adm01.write'))
        OR (SELECT iam.perm_all('adm06.manage'))
        OR (SELECT iam.perm_all('adm06.write'))
      )
    )
  );

GRANT SELECT, INSERT ON plat.institute_lifecycle_events TO authenticated;
GRANT ALL ON plat.institute_lifecycle_events TO service_role;

-- -------------------------------------------------------------------------
-- 4. Lifecycle requests (R03 request / R01 decide)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.institute_lifecycle_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid,
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
    CHECK (request_type IN ('SUSPEND', 'ARCHIVE', 'DECOMMISSION', 'FULL_EXPORT')),
  CONSTRAINT ck_institute_lifecycle_requests_status
    CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED', 'APPROVED_AWAITING_EXECUTION')),
  CONSTRAINT uq_institute_lifecycle_requests_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_institute_lifecycle_requests_tenant_status
  ON plat.institute_lifecycle_requests (tenant_id, status)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.institute_lifecycle_requests IS
  'IADM-01: Institution Admin (R03) requests; Super Admin (R01) decides.';

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
-- 5. CADM dual-acknowledgement handover (US-008) with INTERIM when CADM absent
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.college_cadm_handovers (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid NOT NULL,
  first_college_admin_user_id uuid,
  handed_over_by uuid NOT NULL,
  handed_over_at timestamptz,
  status text NOT NULL DEFAULT 'PENDING',
  cadm01_ack_at timestamptz,
  cadm01_ack_by uuid,
  cadm01_interim boolean NOT NULL DEFAULT false,
  cadm02_ack_at timestamptz,
  cadm02_ack_by uuid,
  cadm02_interim boolean NOT NULL DEFAULT false,
  notes text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT ck_college_cadm_handovers_status
    CHECK (status IN ('PENDING', 'PARTIAL', 'COMPLETED', 'REVOKED')),
  CONSTRAINT uq_college_cadm_handovers_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_college_cadm_handovers_active_college
  ON plat.college_cadm_handovers (tenant_id, college_id)
  WHERE deleted_at IS NULL AND status IN ('PENDING', 'PARTIAL', 'COMPLETED');

CREATE INDEX IF NOT EXISTS ix_college_cadm_handovers_tenant
  ON plat.college_cadm_handovers (tenant_id);

COMMENT ON TABLE plat.college_cadm_handovers IS
  'IADM-01 US-008: handover to CADM-01 (profile) and CADM-02 (users); interim acks when CADM absent.';

ALTER TABLE plat.college_cadm_handovers ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.college_cadm_handovers FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS college_cadm_handovers_select ON plat.college_cadm_handovers;
CREATE POLICY college_cadm_handovers_select ON plat.college_cadm_handovers
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS college_cadm_handovers_write ON plat.college_cadm_handovers;
CREATE POLICY college_cadm_handovers_write ON plat.college_cadm_handovers
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.perm_all('adm06.manage')))
    )
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND ((SELECT iam.perm_all('adm01.manage')) OR (SELECT iam.perm_all('adm06.manage')))
    )
  );

GRANT SELECT, INSERT, UPDATE ON plat.college_cadm_handovers TO authenticated;
GRANT ALL ON plat.college_cadm_handovers TO service_role;

-- -------------------------------------------------------------------------
-- 6. Second-approver hook flag (TECH-007) — default OFF
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.iadm01_feature_flags (
  flag_key text PRIMARY KEY,
  enabled boolean NOT NULL DEFAULT false,
  description text,
  updated_at timestamptz NOT NULL DEFAULT now(),
  updated_by uuid
);

INSERT INTO plat.iadm01_feature_flags (flag_key, enabled, description)
VALUES (
  'R03_SECOND_APPROVER_REQUIRED',
  false,
  'When true, first Institution Admin (R03) appointment waits for a second authorized approver (TECH-007). Default OFF.'
)
ON CONFLICT (flag_key) DO NOTHING;

ALTER TABLE plat.iadm01_feature_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.iadm01_feature_flags FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS iadm01_feature_flags_select ON plat.iadm01_feature_flags;
CREATE POLICY iadm01_feature_flags_select ON plat.iadm01_feature_flags
  FOR SELECT TO authenticated
  USING (true);

DROP POLICY IF EXISTS iadm01_feature_flags_update ON plat.iadm01_feature_flags;
CREATE POLICY iadm01_feature_flags_update ON plat.iadm01_feature_flags
  FOR UPDATE TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

GRANT SELECT ON plat.iadm01_feature_flags TO authenticated;
GRANT SELECT, UPDATE ON plat.iadm01_feature_flags TO service_role;

COMMIT;
