-- Migration: 20261121000300_iadm_wave_b_identity_and_audit.sql
-- IADM Wave B: role ceilings + access-change requests + audit self-check helper
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Stories: IADM-03-US-002/004, IADM-08-US-006

BEGIN;

-- Role ceilings: max roles/keys a College Admin may assign (CADM-03 contract)
CREATE TABLE IF NOT EXISTS iam.role_ceilings (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL DEFAULT core.current_tenant_id(),
  college_id uuid,
  role_code text NOT NULL,
  allowed_permission_keys text[] NOT NULL DEFAULT '{}',
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT uq_role_ceilings_tenant_id UNIQUE (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_role_ceilings_active
  ON iam.role_ceilings (tenant_id, COALESCE(college_id, '00000000-0000-0000-0000-000000000000'::uuid), role_code)
  WHERE deleted_at IS NULL;

COMMENT ON TABLE iam.role_ceilings IS
  'IADM-03-US-002: Institution Admin sets max roles/keys College Admin may assign (CADM-03 ceiling lookup)';

ALTER TABLE iam.role_ceilings ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam.role_ceilings FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS role_ceilings_select ON iam.role_ceilings;
CREATE POLICY role_ceilings_select ON iam.role_ceilings FOR SELECT TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()));

DROP POLICY IF EXISTS role_ceilings_write ON iam.role_ceilings;
CREATE POLICY role_ceilings_write ON iam.role_ceilings FOR ALL TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm09.manage')) OR (SELECT iam.perm_all('adm09.write')) OR (SELECT iam.is_platform_admin()))
  )
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm09.manage')) OR (SELECT iam.perm_all('adm09.write')) OR (SELECT iam.is_platform_admin()))
  );

GRANT SELECT, INSERT, UPDATE ON iam.role_ceilings TO authenticated;
GRANT ALL ON iam.role_ceilings TO service_role;

-- Access change requests (R04 request / R03 decide)
CREATE TABLE IF NOT EXISTS iam.access_change_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL DEFAULT core.current_tenant_id(),
  college_id uuid,
  requested_by uuid NOT NULL,
  request_type text NOT NULL,
  payload jsonb NOT NULL DEFAULT '{}'::jsonb,
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
  CONSTRAINT ck_access_change_requests_type CHECK (request_type IN ('ROLE_ASSIGN', 'ROLE_REVOKE', 'CEILING_CHANGE')),
  CONSTRAINT ck_access_change_requests_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  CONSTRAINT uq_access_change_requests_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_access_change_requests_tenant_status
  ON iam.access_change_requests (tenant_id, status) WHERE deleted_at IS NULL;

ALTER TABLE iam.access_change_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE iam.access_change_requests FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS access_change_requests_select ON iam.access_change_requests;
CREATE POLICY access_change_requests_select ON iam.access_change_requests FOR SELECT TO authenticated
  USING (tenant_id = (SELECT core.current_tenant_id()) OR (SELECT iam.is_platform_admin()));

DROP POLICY IF EXISTS access_change_requests_insert ON iam.access_change_requests;
CREATE POLICY access_change_requests_insert ON iam.access_change_requests FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND (
      (SELECT iam.has_permission('adm09.write'))
      OR (SELECT iam.perm_all('adm09.manage'))
      OR (SELECT iam.is_platform_admin())
      OR requested_by = auth.uid()
    )
  );

DROP POLICY IF EXISTS access_change_requests_update ON iam.access_change_requests;
CREATE POLICY access_change_requests_update ON iam.access_change_requests FOR UPDATE TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm09.manage')) OR (SELECT iam.perm_all('adm09.write')) OR (SELECT iam.is_platform_admin()))
  )
  WITH CHECK (
    tenant_id = (SELECT core.current_tenant_id())
    AND ((SELECT iam.perm_all('adm09.manage')) OR (SELECT iam.perm_all('adm09.write')) OR (SELECT iam.is_platform_admin()))
  );

GRANT SELECT, INSERT, UPDATE ON iam.access_change_requests TO authenticated;
GRANT ALL ON iam.access_change_requests TO service_role;

-- Audit self-check: report whether UPDATE/DELETE privileges exist on audit.events for authenticated
CREATE OR REPLACE FUNCTION audit.self_check_immutable()
RETURNS TABLE (check_name text, passed boolean, detail text)
LANGUAGE plpgsql
SECURITY INVOKER
SET search_path = ''
AS $$
BEGIN
  check_name := 'no_update_policy_on_audit_events';
  passed := NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'audit' AND tablename = 'events' AND cmd = 'UPDATE'
  );
  detail := CASE WHEN passed THEN 'No UPDATE policy on audit.events' ELSE 'UPDATE policy present on audit.events' END;
  RETURN NEXT;

  check_name := 'no_delete_policy_on_audit_events';
  passed := NOT EXISTS (
    SELECT 1 FROM pg_policies
    WHERE schemaname = 'audit' AND tablename = 'events' AND cmd = 'DELETE'
  );
  detail := CASE WHEN passed THEN 'No DELETE policy on audit.events' ELSE 'DELETE policy present on audit.events' END;
  RETURN NEXT;
END;
$$;

REVOKE ALL ON FUNCTION audit.self_check_immutable() FROM PUBLIC;
GRANT EXECUTE ON FUNCTION audit.self_check_immutable() TO authenticated, service_role;

COMMIT;
