-- Migration: 20261127010000_iadm03_v2_keys_and_schema.sql
-- Purpose: IADM-03 S0 (US-018, US-019) — Additive iadm03.* permission keys + iadm03 schema with FORCE RLS.
-- Scope: DEV only (azulbaxszsqxymqmlwfe). Zero changes to existing keys, grants, roles, or legacy adm*.* keys.
-- Rollback: 20261127010000_rollback__iadm03_v2_keys_and_schema.sql

-- 1. Additive iadm03.* permission keys (no wildcards, no apply-grant key)
INSERT INTO iam.permissions (code, module, resource, action, description)
SELECT v.code, v.module, v.resource, v.action, v.description
FROM (VALUES
  ('iadm03.register.view', 'iadm03', 'register', 'view', 'View read-only permission-key register (IADM-03)'),
  ('iadm03.matrix.view', 'iadm03', 'matrix', 'view', 'View R01-R39 role-to-permission matrix (IADM-03)'),
  ('iadm03.holders.view', 'iadm03', 'holders', 'view', 'View holder-level access and institute evidence (IADM-03)'),
  ('iadm03.grant.request.submit', 'iadm03', 'grant.request', 'submit', 'Submit record-only grant-change request (IADM-03)'),
  ('iadm03.grant.review', 'iadm03', 'grant', 'review', 'Validate and prepare grant-change diff without approving (IADM-03)'),
  ('iadm03.grant.approve', 'iadm03', 'grant', 'approve', 'Approve or reject grant-change request, record-only (IADM-03, R01 only)'),
  ('iadm03.review.manage', 'iadm03', 'review', 'manage', 'Manage access-review campaigns and decisions (IADM-03)'),
  ('iadm03.delegation.manage', 'iadm03', 'delegation', 'manage', 'Create and revoke recorded scoped delegations (IADM-03)'),
  ('iadm03.registration.submit', 'iadm03', 'registration', 'submit', 'Submit additive service permission-key manifest (IADM-03)'),
  ('iadm03.registration.approve', 'iadm03', 'registration', 'approve', 'Approve additive service permission-key manifest (IADM-03, R01 only)'),
  ('iadm03.handover.view', 'iadm03', 'handover', 'view', 'View first-admin appointment and CADM-02 handover status (IADM-03)'),
  ('iadm03.evidence.view', 'iadm03', 'evidence', 'view', 'Read-only access evidence and audit view (IADM-03)')
) AS v(code, module, resource, action, description)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.permissions p WHERE p.code = v.code AND p.deleted_at IS NULL
);

-- 2. Additive role_template_permissions (R02, R03, R04, R28 only; R01 is in plat.platform_admins)
INSERT INTO iam.role_template_permissions (role_code, permission_code)
SELECT v.role_code, v.permission_code
FROM (VALUES
  -- R02 System Admin (operate/validate/submit; never approve)
  ('SYSTEM_ADMIN', 'iadm03.register.view'),
  ('SYSTEM_ADMIN', 'iadm03.matrix.view'),
  ('SYSTEM_ADMIN', 'iadm03.holders.view'),
  ('SYSTEM_ADMIN', 'iadm03.grant.request.submit'),
  ('SYSTEM_ADMIN', 'iadm03.grant.review'),
  ('SYSTEM_ADMIN', 'iadm03.review.manage'),
  ('SYSTEM_ADMIN', 'iadm03.registration.submit'),
  ('SYSTEM_ADMIN', 'iadm03.handover.view'),
  ('SYSTEM_ADMIN', 'iadm03.evidence.view'),
  -- R03 Institution Admin (own institute only: view holders/handover, submit requests, manage own-institute delegations)
  ('INSTITUTION_ADMIN', 'iadm03.holders.view'),
  ('INSTITUTION_ADMIN', 'iadm03.grant.request.submit'),
  ('INSTITUTION_ADMIN', 'iadm03.delegation.manage'),
  ('INSTITUTION_ADMIN', 'iadm03.handover.view'),
  -- R04 College Admin (request-only)
  ('COLLEGE_ADMIN', 'iadm03.grant.request.submit'),
  -- R28 Auditor (read-only)
  ('AUDITOR', 'iadm03.register.view'),
  ('AUDITOR', 'iadm03.matrix.view'),
  ('AUDITOR', 'iadm03.holders.view'),
  ('AUDITOR', 'iadm03.handover.view'),
  ('AUDITOR', 'iadm03.evidence.view')
) AS v(role_code, permission_code)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_template_permissions rtp
  WHERE rtp.role_code = v.role_code AND rtp.permission_code = v.permission_code
);

-- 3. Propagate additive iadm03.* grants to existing tenant iam.roles for SYSTEM_ADMIN, INSTITUTION_ADMIN, COLLEGE_ADMIN, AUDITOR
INSERT INTO iam.role_permissions (tenant_id, role_id, permission_id)
SELECT r.tenant_id, r.id, p.id
FROM iam.roles r
JOIN iam.role_template_permissions rtp ON rtp.role_code = upper(r.code)
JOIN iam.permissions p ON p.code = rtp.permission_code AND p.deleted_at IS NULL
WHERE r.deleted_at IS NULL
  AND p.code LIKE 'iadm03.%'
  AND upper(r.code) IN ('SYSTEM_ADMIN', 'INSTITUTION_ADMIN', 'COLLEGE_ADMIN', 'AUDITOR')
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_permissions rp
    WHERE rp.tenant_id = r.tenant_id AND rp.role_id = r.id AND rp.permission_id = p.id
  );

-- 4. IADM-03 owned schema (no catalogue/holders/matrix/audit/handover copies)
CREATE SCHEMA IF NOT EXISTS iadm03;

CREATE TABLE IF NOT EXISTS iadm03.grant_requests (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NULL,
  college_id uuid NULL,
  requester_user_id uuid NOT NULL,
  subject_user_id uuid NOT NULL,
  target_kind text NOT NULL CHECK (target_kind IN ('ROLE', 'PERMISSION')),
  target_code text NOT NULL,
  scope_type text NOT NULL CHECK (scope_type IN ('PLATFORM', 'INSTITUTE', 'COLLEGE')),
  scope_id uuid NULL,
  valid_from timestamptz NOT NULL DEFAULT now(),
  valid_to timestamptz NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'REQUESTED' CHECK (status IN ('REQUESTED', 'VALIDATED', 'APPROVED', 'REJECTED', 'RECORDED')),
  validation_diff jsonb NULL,
  validated_by uuid NULL,
  validated_at timestamptz NULL,
  decided_by uuid NULL,
  decided_at timestamptz NULL,
  decision_reason text NULL,
  recorded_at timestamptz NULL,
  audit_event_id uuid NULL,
  correlation_id text NULL,
  idempotency_key text NULL UNIQUE,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS iadm03.grant_decisions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NULL,
  request_id uuid NOT NULL REFERENCES iadm03.grant_requests(id),
  approver_user_id uuid NOT NULL,
  decision text NOT NULL CHECK (decision IN ('APPROVED', 'REJECTED')),
  reason text NOT NULL,
  before_state jsonb NOT NULL,
  after_state jsonb NOT NULL,
  recorded_only boolean NOT NULL DEFAULT true CHECK (recorded_only = true),
  audit_event_id uuid NOT NULL,
  correlation_id text NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS iadm03.access_review_campaigns (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NULL,
  title text NOT NULL,
  scope_type text NOT NULL CHECK (scope_type IN ('PLATFORM', 'INSTITUTE')),
  scope_id uuid NULL,
  reviewers jsonb NOT NULL DEFAULT '["R01","R02"]'::jsonb,
  due_at timestamptz NOT NULL,
  cadence_source text NOT NULL DEFAULT 'DEMO_DEFAULT_90D',
  status text NOT NULL DEFAULT 'OPEN' CHECK (status IN ('OPEN', 'CLOSED')),
  created_by uuid NOT NULL,
  audit_event_id uuid NULL,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS iadm03.access_review_items (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  campaign_id uuid NOT NULL REFERENCES iadm03.access_review_campaigns(id),
  tenant_id uuid NULL,
  subject_user_id uuid NOT NULL,
  role_code text NOT NULL,
  permission_key text NULL,
  owning_service text NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NULL,
  grantor_user_id uuid NULL,
  valid_to timestamptz NULL,
  decision text NOT NULL DEFAULT 'PENDING' CHECK (decision IN ('PENDING', 'ATTEST', 'REVOKE', 'EXCEPTION', 'OVERDUE')),
  reviewer_user_id uuid NULL,
  reason text NULL,
  exception_owner text NULL,
  exception_expires_at timestamptz NULL,
  revocation_request_id uuid NULL REFERENCES iadm03.grant_requests(id),
  decided_at timestamptz NULL,
  audit_event_id uuid NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS iadm03.delegations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NULL,
  delegator_user_id uuid NOT NULL,
  delegate_user_id uuid NOT NULL,
  allowed_action text NOT NULL,
  target_service text NOT NULL,
  scope_type text NOT NULL CHECK (scope_type IN ('PLATFORM', 'INSTITUTE', 'COLLEGE')),
  scope_id uuid NULL,
  starts_at timestamptz NOT NULL DEFAULT now(),
  expires_at timestamptz NOT NULL,
  reason text NOT NULL,
  status text NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('PENDING', 'ACTIVE', 'REVOKED')),
  approved_by uuid NULL,
  approved_at timestamptz NULL,
  revoked_by uuid NULL,
  revoked_at timestamptz NULL,
  revoke_reason text NULL,
  audit_event_id uuid NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS iadm03.service_registrations (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NULL,
  service_code text NOT NULL,
  owner_user_id uuid NOT NULL,
  key_manifest jsonb NOT NULL,
  role_grant_proposal jsonb NOT NULL,
  holder_impact jsonb NOT NULL DEFAULT '{}'::jsonb,
  migration_ref text NOT NULL,
  rollback_ref text NOT NULL,
  test_evidence_ref text NOT NULL,
  audit_evidence_ref text NOT NULL,
  status text NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN ('SUBMITTED', 'VALIDATED', 'APPROVED', 'REJECTED')),
  validated_by uuid NULL,
  validated_at timestamptz NULL,
  validation_notes text NULL,
  decided_by uuid NULL,
  decided_at timestamptz NULL,
  decision_reason text NULL,
  audit_event_id uuid NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

-- 5. Enable and FORCE Row-Level Security on every IADM-03 owned table
DO $$
DECLARE
  tbl text;
BEGIN
  FOREACH tbl IN ARRAY ARRAY[
    'grant_requests',
    'grant_decisions',
    'access_review_campaigns',
    'access_review_items',
    'delegations',
    'service_registrations'
  ]
  LOOP
    EXECUTE format('ALTER TABLE iadm03.%I ENABLE ROW LEVEL SECURITY', tbl);
    EXECUTE format('ALTER TABLE iadm03.%I FORCE ROW LEVEL SECURITY', tbl);

    EXECUTE format('DROP POLICY IF EXISTS %I_select ON iadm03.%I', tbl, tbl);
    EXECUTE format('DROP POLICY IF EXISTS %I_insert ON iadm03.%I', tbl, tbl);
    EXECUTE format('DROP POLICY IF EXISTS %I_update ON iadm03.%I', tbl, tbl);
    EXECUTE format('DROP POLICY IF EXISTS %I_delete ON iadm03.%I', tbl, tbl);

    EXECUTE format(
      'CREATE POLICY %I_select ON iadm03.%I FOR SELECT USING (
        iam.is_platform_admin()
        OR (
          tenant_id IS NOT NULL
          AND NULLIF(current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'', '''') IS NOT NULL
          AND tenant_id = (current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'')::uuid
        )
      )', tbl, tbl);

    EXECUTE format(
      'CREATE POLICY %I_insert ON iadm03.%I FOR INSERT WITH CHECK (
        iam.is_platform_admin()
        OR (
          tenant_id IS NOT NULL
          AND NULLIF(current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'', '''') IS NOT NULL
          AND tenant_id = (current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'')::uuid
        )
      )', tbl, tbl);

    EXECUTE format(
      'CREATE POLICY %I_update ON iadm03.%I FOR UPDATE USING (
        iam.is_platform_admin()
        OR (
          tenant_id IS NOT NULL
          AND NULLIF(current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'', '''') IS NOT NULL
          AND tenant_id = (current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'')::uuid
        )
      ) WITH CHECK (
        iam.is_platform_admin()
        OR (
          tenant_id IS NOT NULL
          AND NULLIF(current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'', '''') IS NOT NULL
          AND tenant_id = (current_setting(''request.jwt.claims'', true)::jsonb ->> ''tenant_id'')::uuid
        )
      )', tbl, tbl);

    EXECUTE format(
      'CREATE POLICY %I_delete ON iadm03.%I FOR DELETE USING (false)',
      tbl, tbl);
  END LOOP;
END $$;

GRANT USAGE ON SCHEMA iadm03 TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE ON ALL TABLES IN SCHEMA iadm03 TO authenticated, service_role;
