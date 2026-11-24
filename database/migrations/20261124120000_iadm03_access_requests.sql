-- Migration: 20261124120000_iadm03_access_requests.sql
-- Plain language: IADM-03 S3 access-request store (US-023…025).
-- ENABLE RLS (not FORCE — FORCE stays a separate owner approval).
-- Target: azulbaxszsqxymqmlwfe (DEV only)
-- Apply only after owner: APPROVE MIGRATION 20261124120000

BEGIN;

CREATE TABLE IF NOT EXISTS iam.access_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  college_id uuid,
  requester_user_id uuid NOT NULL,
  subject_user_id uuid NOT NULL,
  requested_role_code text NOT NULL,
  justification text NOT NULL,
  status text NOT NULL DEFAULT 'SUBMITTED',
  -- DRAFT | SUBMITTED | PENDING | APPROVED | REJECTED | EXPIRED
  valid_from date,
  valid_to date,
  decided_by uuid,
  decided_at timestamptz,
  decision_reason text,
  resulting_assignment_id uuid,
  idempotency_key text,
  correlation_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid,
  updated_by uuid,
  row_version integer NOT NULL DEFAULT 1,
  deleted_at timestamptz,
  CONSTRAINT chk_iam_access_requests_status CHECK (
    status IN ('DRAFT', 'SUBMITTED', 'PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')
  ),
  CONSTRAINT uq_iam_access_requests_idempotency UNIQUE (idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_iam_access_requests_tenant_status
  ON iam.access_requests (tenant_id, status)
  WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_iam_access_requests_requester
  ON iam.access_requests (requester_user_id)
  WHERE deleted_at IS NULL;

ALTER TABLE iam.access_requests ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS access_requests_select ON iam.access_requests;
CREATE POLICY access_requests_select ON iam.access_requests
  FOR SELECT TO authenticated
  USING (
    tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
    OR requester_user_id = (SELECT auth.uid())
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS access_requests_insert ON iam.access_requests;
CREATE POLICY access_requests_insert ON iam.access_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
    OR (SELECT iam.is_platform_admin())
  );

DROP POLICY IF EXISTS access_requests_update ON iam.access_requests;
CREATE POLICY access_requests_update ON iam.access_requests
  FOR UPDATE TO authenticated
  USING (
    tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
    OR (SELECT iam.is_platform_admin())
  )
  WITH CHECK (
    tenant_id = NULLIF(current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id', '')::uuid
    OR (SELECT iam.is_platform_admin())
  );

GRANT SELECT, INSERT, UPDATE ON iam.access_requests TO authenticated;
GRANT ALL ON iam.access_requests TO service_role;

COMMENT ON TABLE iam.access_requests IS 'IADM-03 S3 access requests (US-023…025); never grants by itself.';

COMMIT;
