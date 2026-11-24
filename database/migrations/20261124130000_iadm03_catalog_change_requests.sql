-- Migration: 20261124130000_iadm03_catalog_change_requests.sql
-- Plain language: IADM-03 US-030 store for R01→R01 role-permission mapping proposals.
-- Does NOT add roles to R01–R39. ENABLE RLS only (no FORCE).
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Apply only after owner: APPROVE MIGRATION 20261124130000

BEGIN;

CREATE TABLE IF NOT EXISTS iam.catalog_change_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  change_type text NOT NULL,
  -- ADD_MAPPING | REMOVE_MAPPING (ADD_ROLE forbidden by Java + CHECK)
  role_code text NOT NULL,
  permission_code text NOT NULL,
  justification text NOT NULL,
  status text NOT NULL DEFAULT 'PROPOSED',
  -- PROPOSED | APPROVED | REJECTED | APPLIED | CANCELLED
  impact_preview jsonb NOT NULL DEFAULT '{}'::jsonb,
  proposed_by uuid NOT NULL,
  proposed_at timestamptz NOT NULL DEFAULT now(),
  decided_by uuid,
  decided_at timestamptz,
  decision_reason text,
  applied_at timestamptz,
  resulting_template_permission_id uuid,
  idempotency_key text,
  correlation_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT chk_iam_catalog_change_type CHECK (
    change_type IN ('ADD_MAPPING', 'REMOVE_MAPPING')
  ),
  CONSTRAINT chk_iam_catalog_change_status CHECK (
    status IN ('PROPOSED', 'APPROVED', 'REJECTED', 'APPLIED', 'CANCELLED')
  ),
  CONSTRAINT uq_iam_catalog_change_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_iam_catalog_change_status
  ON iam.catalog_change_requests (status)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_iam_catalog_change_role
  ON iam.catalog_change_requests (role_code, permission_code)
  WHERE deleted_at IS NULL;

ALTER TABLE iam.catalog_change_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS catalog_change_select ON iam.catalog_change_requests;
CREATE POLICY catalog_change_select ON iam.catalog_change_requests
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR proposed_by = (SELECT auth.uid())
  );

DROP POLICY IF EXISTS catalog_change_insert ON iam.catalog_change_requests;
CREATE POLICY catalog_change_insert ON iam.catalog_change_requests
  FOR INSERT TO authenticated
  WITH CHECK ((SELECT iam.is_platform_admin()));

DROP POLICY IF EXISTS catalog_change_update ON iam.catalog_change_requests;
CREATE POLICY catalog_change_update ON iam.catalog_change_requests
  FOR UPDATE TO authenticated
  USING ((SELECT iam.is_platform_admin()))
  WITH CHECK ((SELECT iam.is_platform_admin()));

GRANT SELECT, INSERT, UPDATE ON iam.catalog_change_requests TO authenticated;
GRANT ALL ON iam.catalog_change_requests TO service_role;

COMMENT ON TABLE iam.catalog_change_requests IS
  'IADM-03 US-030 R01 propose / different R01 approve role↔permission mapping changes; never creates roles.';

COMMIT;
