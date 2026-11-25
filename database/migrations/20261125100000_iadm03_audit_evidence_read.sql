-- Migration: 20261125100000_iadm03_audit_evidence_read.sql
-- Plain language: IADM-03 US-033 — allow callers with iam.audit.view to list
-- IADM-03 audit evidence via SECURITY DEFINER (RLS on audit.events uses audit.read).
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Owner: Pre-step P for IADM-04 kickoff

BEGIN;

CREATE OR REPLACE FUNCTION audit.list_iadm03_evidence(p_limit integer DEFAULT 50)
RETURNS TABLE (
  id uuid,
  tenant_id uuid,
  action text,
  principal_id uuid,
  resource_type text,
  resource_id uuid,
  status text,
  severity text,
  detail jsonb,
  created_at timestamptz
)
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path = audit, iam, public
AS $$
DECLARE
  lim integer := GREATEST(1, LEAST(COALESCE(p_limit, 50), 200));
BEGIN
  IF NOT (SELECT iam.has_permission('iam.audit.view'))
     AND NOT (SELECT iam.is_platform_admin()) THEN
    RAISE EXCEPTION 'Caller lacks iam.audit.view'
      USING ERRCODE = '42501';
  END IF;

  RETURN QUERY
  SELECT e.id, e.tenant_id, e.action, e.principal_id, e.resource_type, e.resource_id,
         e.status, e.severity, e.detail, e.created_at
  FROM audit.events e
  WHERE e.action LIKE 'IADM03_%'
  ORDER BY e.created_at DESC
  LIMIT lim;
END;
$$;

COMMENT ON FUNCTION audit.list_iadm03_evidence(integer) IS
  'IADM-03-US-033: list IADM03_* audit.events for callers with iam.audit.view (or platform admin).';

GRANT EXECUTE ON FUNCTION audit.list_iadm03_evidence(integer) TO authenticated;
GRANT EXECUTE ON FUNCTION audit.list_iadm03_evidence(integer) TO service_role;

COMMIT;
