-- =============================================================================
-- Migration: 20261121000800_iadm_wave_e_handover_and_suspend.sql
-- Description: IADM Wave E1-A — college handover gate + tenant suspend session gate
-- Environment: DEV ONLY (azulbaxszsqxymqmlwfe)
-- =============================================================================

-- -------------------------------------------------------------------------
-- 1. plat.is_college_handed_over(college_id) — R03 loses college user-admin
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION plat.is_college_handed_over(p_college_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = plat, pg_temp
AS $$
BEGIN
  IF p_college_id IS NULL THEN
    RETURN false;
  END IF;
  RETURN EXISTS (
    SELECT 1
    FROM plat.college_admin_handovers h
    WHERE h.college_id = p_college_id
      AND h.status = 'COMPLETED'
      AND h.deleted_at IS NULL
  );
END;
$$;

COMMENT ON FUNCTION plat.is_college_handed_over(uuid) IS
  'IADM-01-US-004 / IADM-03: true when college handover is COMPLETED — Institution Admin must not assign college-scoped users';

REVOKE ALL ON FUNCTION plat.is_college_handed_over(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.is_college_handed_over(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION plat.is_college_handed_over(uuid) TO service_role;

-- -------------------------------------------------------------------------
-- 2. plat.is_tenant_suspended(tenant_id) — sessions blocked while suspended
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION plat.is_tenant_suspended(p_tenant_id uuid)
RETURNS boolean
LANGUAGE plpgsql
STABLE
SECURITY DEFINER
SET search_path = core, plat, pg_temp
AS $$
DECLARE
  v_status text;
BEGIN
  IF p_tenant_id IS NULL THEN
    RETURN false;
  END IF;
  SELECT t.status INTO v_status
  FROM core.tenants t
  WHERE t.id = p_tenant_id
    AND t.deleted_at IS NULL;
  IF v_status IS NULL THEN
    RETURN false;
  END IF;
  RETURN upper(v_status) = 'SUSPENDED';
END;
$$;

COMMENT ON FUNCTION plat.is_tenant_suspended(uuid) IS
  'IADM-01-US-006: true when institute tenant status is SUSPENDED — callers must fail closed';

REVOKE ALL ON FUNCTION plat.is_tenant_suspended(uuid) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.is_tenant_suspended(uuid) TO authenticated;
GRANT EXECUTE ON FUNCTION plat.is_tenant_suspended(uuid) TO service_role;

-- -------------------------------------------------------------------------
-- 3. Session block ledger (audit trail for suspend/reactivate)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.tenant_session_blocks (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  blocked boolean NOT NULL DEFAULT true,
  reason text NOT NULL,
  actor_id uuid,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  CONSTRAINT uq_tenant_session_blocks_tenant_id UNIQUE (tenant_id, id)
);

CREATE INDEX IF NOT EXISTS ix_tenant_session_blocks_tenant_created
  ON plat.tenant_session_blocks (tenant_id, created_at DESC);

COMMENT ON TABLE plat.tenant_session_blocks IS
  'IADM-01-US-006: append-only session block events when institute is suspended/reactivated';

ALTER TABLE plat.tenant_session_blocks ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.tenant_session_blocks FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_session_blocks_select ON plat.tenant_session_blocks;
CREATE POLICY tenant_session_blocks_select ON plat.tenant_session_blocks
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS tenant_session_blocks_insert ON plat.tenant_session_blocks;
CREATE POLICY tenant_session_blocks_insert ON plat.tenant_session_blocks
  FOR INSERT TO authenticated
  WITH CHECK ((SELECT iam.is_platform_admin()));

GRANT SELECT, INSERT ON plat.tenant_session_blocks TO authenticated;
GRANT ALL ON plat.tenant_session_blocks TO service_role;
