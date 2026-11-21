-- Migration: 20261121001300_iadm_03_us008_break_glass.sql
-- Story: IADM-03-US-008 / OWNER-DEC-BG-01 — break-glass tenant recovery
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

CREATE TABLE IF NOT EXISTS plat.break_glass_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES core.tenants(id),
  requested_by uuid NOT NULL,
  requester_role text NOT NULL,
  reason text NOT NULL,
  requested_minutes integer NOT NULL DEFAULT 60,
  status text NOT NULL DEFAULT 'PENDING',
  decided_by uuid,
  decision_note text,
  decided_at timestamptz,
  session_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_bgr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT ck_bgr_minutes CHECK (requested_minutes >= 15 AND requested_minutes <= 240),
  CONSTRAINT ck_bgr_requester_role CHECK (requester_role IN ('R01', 'R02', 'R03', 'R04', 'R05', 'R31'))
);

CREATE INDEX IF NOT EXISTS ix_bgr_tenant_status
  ON plat.break_glass_requests (tenant_id, status)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE plat.break_glass_requests IS
  'IADM-03-US-008: break-glass request; request=R03/R04/R05/R31+R01/R02; approve=R01/R02 only';

CREATE TABLE IF NOT EXISTS plat.break_glass_sessions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES core.tenants(id),
  request_id uuid NOT NULL REFERENCES plat.break_glass_requests(id),
  opened_by uuid NOT NULL,
  opener_track text NOT NULL,
  reason text NOT NULL,
  expires_at timestamptz NOT NULL,
  closed_at timestamptz,
  closed_by uuid,
  close_reason text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_bgs_opener_track CHECK (opener_track IN ('R01', 'R02'))
);

CREATE INDEX IF NOT EXISTS ix_bgs_tenant_active
  ON plat.break_glass_sessions (tenant_id, expires_at)
  WHERE deleted_at IS NULL AND closed_at IS NULL;

COMMENT ON TABLE plat.break_glass_sessions IS
  'IADM-03-US-008: time-boxed break-glass session opened by R01/R02 after approve';

CREATE TABLE IF NOT EXISTS plat.break_glass_actions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  session_id uuid NOT NULL REFERENCES plat.break_glass_sessions(id),
  tenant_id uuid NOT NULL,
  actor_id uuid NOT NULL,
  action_type text NOT NULL,
  detail jsonb NOT NULL DEFAULT '{}'::jsonb,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  CONSTRAINT ck_bga_action_type CHECK (action_type IN (
    'SESSION_OPENED', 'SESSION_CLOSED', 'RECOVERY_NOTE', 'EMERGENCY_APPOINT_QUORUM', 'STATUS_CHECK'
  ))
);

CREATE INDEX IF NOT EXISTS ix_bga_session
  ON plat.break_glass_actions (session_id, created_at);

COMMENT ON TABLE plat.break_glass_actions IS
  'IADM-03-US-008: append-only audit of elevated actions during break-glass';

ALTER TABLE plat.break_glass_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.break_glass_requests FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.break_glass_sessions ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.break_glass_sessions FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.break_glass_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.break_glass_actions FORCE ROW LEVEL SECURITY;

-- Requests: platform admins see all; tenant stakeholders see own-tenant rows they created or all in tenant if IA
DROP POLICY IF EXISTS bgr_select ON plat.break_glass_requests;
CREATE POLICY bgr_select ON plat.break_glass_requests
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (
        requested_by = auth.uid()
        OR EXISTS (
          SELECT 1 FROM iam.role_assignments ra
          JOIN iam.roles r ON r.id = ra.role_id
          WHERE ra.user_id = auth.uid() AND ra.tenant_id = plat.break_glass_requests.tenant_id
            AND ra.deleted_at IS NULL
            AND r.code IN ('INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'REGISTRAR')
        )
      )
    )
  );

DROP POLICY IF EXISTS bgr_insert ON plat.break_glass_requests;
CREATE POLICY bgr_insert ON plat.break_glass_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    requested_by = auth.uid()
    AND (
      (SELECT iam.is_platform_admin())
      OR (
        tenant_id = (SELECT core.current_tenant_id())
        AND EXISTS (
          SELECT 1 FROM iam.role_assignments ra
          JOIN iam.roles r ON r.id = ra.role_id
          WHERE ra.user_id = auth.uid() AND ra.tenant_id = plat.break_glass_requests.tenant_id
            AND ra.deleted_at IS NULL
            AND r.code IN ('INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'REGISTRAR')
        )
      )
    )
  );

DROP POLICY IF EXISTS bgr_update ON plat.break_glass_requests;
CREATE POLICY bgr_update ON plat.break_glass_requests
  FOR UPDATE TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

DROP POLICY IF EXISTS bgs_select ON plat.break_glass_sessions;
CREATE POLICY bgs_select ON plat.break_glass_sessions
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND EXISTS (
        SELECT 1 FROM iam.role_assignments ra
        JOIN iam.roles r ON r.id = ra.role_id
        WHERE ra.user_id = auth.uid() AND ra.tenant_id = plat.break_glass_sessions.tenant_id
          AND ra.deleted_at IS NULL
          AND r.code IN ('INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'REGISTRAR')
      )
    )
  );

DROP POLICY IF EXISTS bgs_write ON plat.break_glass_sessions;
CREATE POLICY bgs_write ON plat.break_glass_sessions
  FOR ALL TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

DROP POLICY IF EXISTS bga_select ON plat.break_glass_actions;
CREATE POLICY bga_select ON plat.break_glass_actions
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR tenant_id = (SELECT core.current_tenant_id())
  );

DROP POLICY IF EXISTS bga_insert ON plat.break_glass_actions;
CREATE POLICY bga_insert ON plat.break_glass_actions
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    AND actor_id = auth.uid()
  );

GRANT SELECT, INSERT, UPDATE ON plat.break_glass_requests TO authenticated;
GRANT SELECT, INSERT, UPDATE ON plat.break_glass_sessions TO authenticated;
GRANT SELECT, INSERT ON plat.break_glass_actions TO authenticated;
GRANT ALL ON plat.break_glass_requests TO service_role;
GRANT ALL ON plat.break_glass_sessions TO service_role;
GRANT ALL ON plat.break_glass_actions TO service_role;

-- Stakeholder role → matrix track for request gate
CREATE OR REPLACE FUNCTION plat.break_glass_requester_role(p_tenant_id uuid, p_user_id uuid DEFAULT NULL)
RETURNS text
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := COALESCE(p_user_id, auth.uid());
  v_track text;
  v_code text;
BEGIN
  IF v_uid IS NULL OR p_tenant_id IS NULL THEN
    RETURN NULL;
  END IF;
  v_track := plat.platform_admin_track(v_uid);
  IF v_track IS NOT NULL THEN
    RETURN v_track; -- R01 or R02
  END IF;
  SELECT r.code INTO v_code
  FROM iam.role_assignments ra
  JOIN iam.roles r ON r.id = ra.role_id
  WHERE ra.user_id = v_uid
    AND ra.tenant_id = p_tenant_id
    AND ra.deleted_at IS NULL
    AND r.code IN ('INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'PRINCIPAL', 'REGISTRAR')
  ORDER BY CASE r.code
    WHEN 'INSTITUTION_ADMIN' THEN 1
    WHEN 'COLLEGE_ADMIN' THEN 2
    WHEN 'PRINCIPAL' THEN 3
    WHEN 'REGISTRAR' THEN 4
    ELSE 9 END
  LIMIT 1;
  IF v_code = 'INSTITUTION_ADMIN' THEN RETURN 'R03'; END IF;
  IF v_code = 'COLLEGE_ADMIN' THEN RETURN 'R04'; END IF;
  IF v_code = 'PRINCIPAL' THEN RETURN 'R05'; END IF;
  IF v_code = 'REGISTRAR' THEN RETURN 'R31'; END IF;
  RETURN NULL;
END;
$$;

COMMENT ON FUNCTION plat.break_glass_requester_role(uuid, uuid) IS
  'OWNER-DEC-BG-01: allowed request roles R01/R02/R03/R04/R05/R31 for a tenant';

REVOKE ALL ON FUNCTION plat.break_glass_requester_role(uuid, uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.break_glass_requester_role(uuid, uuid) TO authenticated, service_role;

COMMIT;
