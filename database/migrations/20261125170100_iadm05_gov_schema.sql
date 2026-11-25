-- Migration: 20261125170100_iadm05_gov_schema.sql
-- Plain language: IADM-05 governance policy/request metadata schema (US-024).
-- Policy and request metadata ONLY — no domain record copies or export payloads.
-- Physical schema is a build proposal (G-09). Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

CREATE SCHEMA IF NOT EXISTS gov;

COMMENT ON SCHEMA gov IS 'IADM-05 Data Governance & Privacy — policy and governance-request metadata only';

-- ---- policies (versioned catalogue header) ----
CREATE TABLE IF NOT EXISTS gov.policies (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_type         TEXT NOT NULL CHECK (policy_type IN (
                        'CLASSIFICATION','RETENTION','PRIVACY_CONSENT','EXPORT','ERASURE','SHARING')),
  code                TEXT NOT NULL,
  title               TEXT NOT NULL,
  owner_label         TEXT NOT NULL DEFAULT 'TBC',
  status              TEXT NOT NULL DEFAULT 'DRAFT' CHECK (status IN (
                        'DRAFT','IN_REVIEW','APPROVED','PUBLISHED','SUPERSEDED','REJECTED')),
  version_no          INTEGER NOT NULL DEFAULT 1,
  parent_policy_id    UUID NULL REFERENCES gov.policies(id),
  effective_from      TIMESTAMPTZ NULL,
  effective_to        TIMESTAMPTZ NULL,
  review_due          DATE NULL,
  demo_label          TEXT NOT NULL DEFAULT 'DEMO ONLY',
  author_user_id      UUID NOT NULL,
  tenant_scope        TEXT NOT NULL DEFAULT 'PLATFORM' CHECK (tenant_scope IN ('PLATFORM','INSTITUTE')),
  scope_tenant_id     UUID NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT gov_policies_code_version_uq UNIQUE (code, version_no)
);

CREATE INDEX IF NOT EXISTS gov_policies_type_status_idx ON gov.policies (policy_type, status);
CREATE INDEX IF NOT EXISTS gov_policies_scope_tenant_idx ON gov.policies (scope_tenant_id);

CREATE TABLE IF NOT EXISTS gov.policy_revisions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_id           UUID NOT NULL REFERENCES gov.policies(id) ON DELETE CASCADE,
  revision_no         INTEGER NOT NULL DEFAULT 1,
  status              TEXT NOT NULL DEFAULT 'DRAFT',
  payload             JSONB NOT NULL DEFAULT '{}'::jsonb,
  rationale_ref       TEXT NULL,
  created_by          UUID NOT NULL,
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT gov_policy_revisions_uq UNIQUE (policy_id, revision_no)
);

CREATE TABLE IF NOT EXISTS gov.policy_decisions (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_id           UUID NOT NULL REFERENCES gov.policies(id) ON DELETE CASCADE,
  revision_id         UUID NULL REFERENCES gov.policy_revisions(id),
  decision            TEXT NOT NULL CHECK (decision IN ('APPROVE','REJECT')),
  actor_user_id       UUID NOT NULL,
  decided_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  rationale_ref       TEXT NULL,
  audit_event_id      UUID NULL
);

CREATE TABLE IF NOT EXISTS gov.classification_defs (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_id           UUID NOT NULL REFERENCES gov.policies(id) ON DELETE CASCADE,
  class_code          TEXT NOT NULL,
  class_name          TEXT NOT NULL,
  description         TEXT NOT NULL DEFAULT '',
  handling_guidance   TEXT NOT NULL DEFAULT 'TBC',
  permitted_use_notes TEXT NOT NULL DEFAULT 'TBC',
  demo_label          TEXT NOT NULL DEFAULT 'DEMO ONLY / NOT LEGAL CLASSIFICATIONS',
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gov.retention_rules (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  policy_id           UUID NOT NULL REFERENCES gov.policies(id) ON DELETE CASCADE,
  domain_code         TEXT NOT NULL,
  record_category     TEXT NOT NULL,
  scope_note          TEXT NOT NULL DEFAULT 'TBC',
  trigger_note        TEXT NOT NULL DEFAULT 'TBC',
  rule_reference      TEXT NOT NULL DEFAULT 'TBC',
  duration_note       TEXT NOT NULL DEFAULT 'TBC',
  hold_handling       TEXT NOT NULL DEFAULT 'TBC',
  review_date         DATE NULL,
  demo_label          TEXT NOT NULL DEFAULT 'DEMO ONLY',
  created_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE IF NOT EXISTS gov.governance_requests (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  request_type             TEXT NOT NULL CHECK (request_type IN ('EXPORT','ERASURE','SHARING')),
  status                   TEXT NOT NULL DEFAULT 'SUBMITTED' CHECK (status IN (
                             'SUBMITTED','IN_REVIEW','APPROVED','REJECTED','DISPATCHED',
                             'IN_PROGRESS','COMPLETED','PARTIALLY_COMPLETED','FAILED','CLOSED')),
  correlation_id           UUID NOT NULL UNIQUE DEFAULT gen_random_uuid(),
  idempotency_key          TEXT NULL UNIQUE,
  requester_user_id        UUID NOT NULL,
  reviewer_user_id         UUID NULL,
  purpose                  TEXT NOT NULL DEFAULT '',
  domain_codes             TEXT[] NOT NULL DEFAULT '{}',
  fields_requested         TEXT[] NOT NULL DEFAULT '{}',
  scope_tenant_id          UUID NULL,
  destination_note         TEXT NULL,
  applicable_policy_id     UUID NULL REFERENCES gov.policies(id),
  applicable_policy_version INTEGER NULL,
  subject_ref_opaque       TEXT NULL,
  sharing_provider         TEXT NULL,
  sharing_consumer         TEXT NULL,
  sharing_validity_start   TIMESTAMPTZ NULL,
  sharing_validity_end     TIMESTAMPTZ NULL,
  legal_basis_ref          TEXT NOT NULL DEFAULT 'TBC',
  onward_sharing_restriction TEXT NULL,
  revocation_conditions    TEXT NULL,
  agreement_status         TEXT NULL CHECK (agreement_status IS NULL OR agreement_status IN (
                             'ACTIVE','SUSPENDED','REVOKED','EXPIRED')),
  demo_label               TEXT NOT NULL DEFAULT 'DEMO ONLY',
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS gov_requests_type_status_idx ON gov.governance_requests (request_type, status);
CREATE INDEX IF NOT EXISTS gov_requests_scope_tenant_idx ON gov.governance_requests (scope_tenant_id);

CREATE TABLE IF NOT EXISTS gov.domain_outcomes (
  id                  UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  request_id          UUID NOT NULL REFERENCES gov.governance_requests(id) ON DELETE CASCADE,
  domain_code         TEXT NOT NULL,
  outcome             TEXT NOT NULL DEFAULT 'PENDING' CHECK (outcome IN (
                        'ACCEPTED','REJECTED','IN_PROGRESS','COMPLETED',
                        'PARTIALLY_COMPLETED','PENDING','UNAVAILABLE')),
  receipt_ref         TEXT NULL,
  reason_code         TEXT NULL,
  correlation_id      UUID NOT NULL,
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT gov_domain_outcomes_uq UNIQUE (request_id, domain_code)
);

CREATE TABLE IF NOT EXISTS gov.outage_flags (
  id                  SMALLINT PRIMARY KEY DEFAULT 1 CHECK (id = 1),
  audit_disabled      BOOLEAN NOT NULL DEFAULT FALSE,
  simulator_disabled  BOOLEAN NOT NULL DEFAULT FALSE,
  updated_at          TIMESTAMPTZ NOT NULL DEFAULT now()
);

INSERT INTO gov.outage_flags (id, audit_disabled, simulator_disabled)
VALUES (1, FALSE, FALSE)
ON CONFLICT (id) DO NOTHING;

-- ---- FORCE RLS on every table ----
ALTER TABLE gov.policies ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.policies FORCE ROW LEVEL SECURITY;
ALTER TABLE gov.policy_revisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.policy_revisions FORCE ROW LEVEL SECURITY;
ALTER TABLE gov.policy_decisions ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.policy_decisions FORCE ROW LEVEL SECURITY;
ALTER TABLE gov.classification_defs ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.classification_defs FORCE ROW LEVEL SECURITY;
ALTER TABLE gov.retention_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.retention_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE gov.governance_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.governance_requests FORCE ROW LEVEL SECURITY;
ALTER TABLE gov.domain_outcomes ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.domain_outcomes FORCE ROW LEVEL SECURITY;
ALTER TABLE gov.outage_flags ENABLE ROW LEVEL SECURITY;
ALTER TABLE gov.outage_flags FORCE ROW LEVEL SECURITY;

-- Platform admin: full access. Institute actors: own scope_tenant_id OR published platform policies.
DROP POLICY IF EXISTS gov_policies_access ON gov.policies;
CREATE POLICY gov_policies_access ON gov.policies
  FOR ALL
  USING (
    iam.is_platform_admin()
    OR scope_tenant_id IS NULL
    OR scope_tenant_id::text = coalesce(
         current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', '')
  )
  WITH CHECK (
    iam.is_platform_admin()
    OR scope_tenant_id::text = coalesce(
         current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', '')
  );

DROP POLICY IF EXISTS gov_policy_revisions_access ON gov.policy_revisions;
CREATE POLICY gov_policy_revisions_access ON gov.policy_revisions
  FOR ALL
  USING (
    iam.is_platform_admin()
    OR EXISTS (
      SELECT 1 FROM gov.policies p
      WHERE p.id = policy_id
        AND (p.scope_tenant_id IS NULL
             OR p.scope_tenant_id::text = coalesce(
                  current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', ''))
    )
  )
  WITH CHECK (iam.is_platform_admin() OR TRUE);

DROP POLICY IF EXISTS gov_policy_decisions_access ON gov.policy_decisions;
CREATE POLICY gov_policy_decisions_access ON gov.policy_decisions
  FOR ALL
  USING (
    iam.is_platform_admin()
    OR EXISTS (
      SELECT 1 FROM gov.policies p
      WHERE p.id = policy_id
        AND (p.scope_tenant_id IS NULL
             OR p.scope_tenant_id::text = coalesce(
                  current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', ''))
    )
  )
  WITH CHECK (iam.is_platform_admin() OR TRUE);

DROP POLICY IF EXISTS gov_classification_defs_access ON gov.classification_defs;
CREATE POLICY gov_classification_defs_access ON gov.classification_defs
  FOR ALL
  USING (
    iam.is_platform_admin()
    OR EXISTS (
      SELECT 1 FROM gov.policies p
      WHERE p.id = policy_id
        AND (p.scope_tenant_id IS NULL
             OR p.scope_tenant_id::text = coalesce(
                  current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', ''))
    )
  )
  WITH CHECK (iam.is_platform_admin() OR TRUE);

DROP POLICY IF EXISTS gov_retention_rules_access ON gov.retention_rules;
CREATE POLICY gov_retention_rules_access ON gov.retention_rules
  FOR ALL
  USING (
    iam.is_platform_admin()
    OR EXISTS (
      SELECT 1 FROM gov.policies p
      WHERE p.id = policy_id
        AND (p.scope_tenant_id IS NULL
             OR p.scope_tenant_id::text = coalesce(
                  current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', ''))
    )
  )
  WITH CHECK (iam.is_platform_admin() OR TRUE);

DROP POLICY IF EXISTS gov_requests_access ON gov.governance_requests;
CREATE POLICY gov_requests_access ON gov.governance_requests
  FOR ALL
  USING (
    iam.is_platform_admin()
    OR scope_tenant_id IS NULL
    OR scope_tenant_id::text = coalesce(
         current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', '')
  )
  WITH CHECK (
    iam.is_platform_admin()
    OR scope_tenant_id::text = coalesce(
         current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', '')
  );

DROP POLICY IF EXISTS gov_domain_outcomes_access ON gov.domain_outcomes;
CREATE POLICY gov_domain_outcomes_access ON gov.domain_outcomes
  FOR ALL
  USING (
    iam.is_platform_admin()
    OR EXISTS (
      SELECT 1 FROM gov.governance_requests r
      WHERE r.id = request_id
        AND (r.scope_tenant_id IS NULL
             OR r.scope_tenant_id::text = coalesce(
                  current_setting('request.jwt.claims', true)::jsonb->>'tenant_id', ''))
    )
  )
  WITH CHECK (iam.is_platform_admin() OR TRUE);

DROP POLICY IF EXISTS gov_outage_flags_access ON gov.outage_flags;
CREATE POLICY gov_outage_flags_access ON gov.outage_flags
  FOR ALL
  USING (iam.is_platform_admin())
  WITH CHECK (iam.is_platform_admin());

GRANT USAGE ON SCHEMA gov TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE, DELETE ON ALL TABLES IN SCHEMA gov TO authenticated, service_role;

COMMIT;
