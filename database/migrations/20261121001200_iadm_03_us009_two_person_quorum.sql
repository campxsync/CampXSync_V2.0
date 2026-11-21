-- Migration: 20261121001200_iadm_03_us009_two_person_quorum.sql
-- Story: IADM-03-US-009 / OWNER-DEC-TP-01 — R01 + R02 two-person appoint/remove Institution Admin
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

CREATE TABLE IF NOT EXISTS plat.institution_admin_quorum_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL REFERENCES core.tenants(id),
  target_user_id uuid NOT NULL,
  action text NOT NULL,
  status text NOT NULL DEFAULT 'PENDING',
  reason text NOT NULL,
  requested_by uuid NOT NULL,
  requested_track text NOT NULL,
  decided_by uuid,
  decided_track text,
  decision_note text,
  decided_at timestamptz,
  assignment_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  deleted_at timestamptz,
  CONSTRAINT ck_iaqr_action CHECK (action IN ('APPOINT', 'REMOVE')),
  CONSTRAINT ck_iaqr_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT ck_iaqr_requested_track CHECK (requested_track IN ('R01', 'R02')),
  CONSTRAINT ck_iaqr_decided_track CHECK (decided_track IS NULL OR decided_track IN ('R01', 'R02'))
);

CREATE INDEX IF NOT EXISTS ix_iaqr_tenant_status
  ON plat.institution_admin_quorum_requests (tenant_id, status)
  WHERE deleted_at IS NULL;

CREATE UNIQUE INDEX IF NOT EXISTS uq_iaqr_pending_tenant_target_action
  ON plat.institution_admin_quorum_requests (tenant_id, target_user_id, action)
  WHERE deleted_at IS NULL AND status = 'PENDING';

COMMENT ON TABLE plat.institution_admin_quorum_requests IS
  'IADM-03-US-009: two-person (R01+R02) appoint/remove Institution Admin; requestor ≠ approver';

ALTER TABLE plat.institution_admin_quorum_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.institution_admin_quorum_requests FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS iaqr_select ON plat.institution_admin_quorum_requests;
CREATE POLICY iaqr_select ON plat.institution_admin_quorum_requests
  FOR SELECT TO authenticated
  USING ((SELECT iam.is_platform_admin()));

DROP POLICY IF EXISTS iaqr_insert ON plat.institution_admin_quorum_requests;
CREATE POLICY iaqr_insert ON plat.institution_admin_quorum_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    AND requested_by = auth.uid()
  );

DROP POLICY IF EXISTS iaqr_update ON plat.institution_admin_quorum_requests;
CREATE POLICY iaqr_update ON plat.institution_admin_quorum_requests
  FOR UPDATE TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

GRANT SELECT, INSERT, UPDATE ON plat.institution_admin_quorum_requests TO authenticated;
GRANT ALL ON plat.institution_admin_quorum_requests TO service_role;

-- Map platform_admins.role_code → R01 (Super) / R02 (System)
CREATE OR REPLACE FUNCTION plat.platform_admin_track(p_user_id uuid DEFAULT NULL)
RETURNS text
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = ''
AS $$
DECLARE
  v_uid uuid := COALESCE(p_user_id, auth.uid());
  v_code text;
BEGIN
  IF v_uid IS NULL THEN
    RETURN NULL;
  END IF;
  SELECT upper(pa.role_code) INTO v_code
  FROM plat.platform_admins pa
  WHERE pa.user_id = v_uid
    AND pa.status = 'ACTIVE'
    AND pa.deleted_at IS NULL
  LIMIT 1;
  IF v_code IS NULL THEN
    RETURN NULL;
  END IF;
  IF v_code IN ('SUPER_ADMIN', 'PLATFORM_ADMIN', 'R01') THEN
    RETURN 'R01';
  END IF;
  IF v_code IN ('SYSTEM_ADMIN', 'R02') THEN
    RETURN 'R02';
  END IF;
  RETURN NULL;
END;
$$;

COMMENT ON FUNCTION plat.platform_admin_track(uuid) IS
  'OWNER-DEC-TP-01: R01=SUPER_ADMIN/PLATFORM_ADMIN; R02=SYSTEM_ADMIN';

REVOKE ALL ON FUNCTION plat.platform_admin_track(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.platform_admin_track(uuid) TO authenticated, service_role;

COMMIT;
