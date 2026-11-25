-- Migration: 20261125180100_iadm06_anl_schema.sql
-- Plain language: IADM-06 stores ONLY the versioned metric catalogue and export-request metadata (US-019 / D2).
-- No analytics fact table, no snapshots, no copies of other services' data, no dashboard-config table.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

CREATE SCHEMA IF NOT EXISTS anl;
COMMENT ON SCHEMA anl IS 'IADM-06 Platform Analytics — metric catalogue + export-request metadata only';

CREATE TABLE IF NOT EXISTS anl.metric_catalogue (
  id                    UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  metric_code           TEXT NOT NULL,
  metric_name           TEXT NOT NULL,
  plain_definition      TEXT NOT NULL,
  source_owner          TEXT NOT NULL CHECK (source_owner IN ('IADM-01','IADM-04','DEMO_ONLY')),
  grain                 TEXT NOT NULL,
  time_windows          TEXT NOT NULL,
  visibility_roles      TEXT NOT NULL,
  small_group_rule      TEXT NOT NULL,
  catalogue_version     TEXT NOT NULL DEFAULT '1.0',
  demo_label            TEXT NULL,
  created_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at            TIMESTAMPTZ NOT NULL DEFAULT now(),
  CONSTRAINT anl_metric_catalogue_code_uq UNIQUE (metric_code, catalogue_version)
);

COMMENT ON TABLE anl.metric_catalogue IS 'Read-only versioned metric definitions (M01–M10). No write API in V1.';

CREATE TABLE IF NOT EXISTS anl.export_requests (
  id                       UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  requester_user_id        UUID NOT NULL,
  scope_type               TEXT NOT NULL CHECK (scope_type IN ('PLATFORM','INSTITUTE')),
  scope_tenant_id          UUID NULL,
  filter_snapshot          JSONB NOT NULL DEFAULT '{}'::jsonb,
  resolved_policy_id       UUID NULL,
  resolved_policy_code     TEXT NULL,
  resolved_policy_version  INTEGER NULL,
  policy_outcome           TEXT NULL,
  status                   TEXT NOT NULL DEFAULT 'REQUESTED'
                           CHECK (status IN ('REQUESTED','PROCESSING','COMPLETED','FAILED')),
  audit_correlation_id     UUID NULL,
  error_message            TEXT NULL,
  demo_label               TEXT NULL,
  created_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  updated_at               TIMESTAMPTZ NOT NULL DEFAULT now(),
  completed_at             TIMESTAMPTZ NULL
);

COMMENT ON TABLE anl.export_requests IS 'Export-request metadata only — no CSV file payload stored.';

CREATE INDEX IF NOT EXISTS anl_export_requests_requester_idx
  ON anl.export_requests (requester_user_id, created_at DESC);
CREATE INDEX IF NOT EXISTS anl_export_requests_scope_tenant_idx
  ON anl.export_requests (scope_tenant_id);

-- RLS + FORCE
ALTER TABLE anl.metric_catalogue ENABLE ROW LEVEL SECURITY;
ALTER TABLE anl.metric_catalogue FORCE ROW LEVEL SECURITY;
ALTER TABLE anl.export_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE anl.export_requests FORCE ROW LEVEL SECURITY;

-- Catalogue: readable by authenticated holders of catalogue/platform/institute view keys
DROP POLICY IF EXISTS anl_metric_catalogue_select ON anl.metric_catalogue;
CREATE POLICY anl_metric_catalogue_select ON anl.metric_catalogue
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.has_permission('iadm06.catalogue.view'))
    OR (SELECT iam.has_permission('iadm06.platform.view'))
    OR (SELECT iam.has_permission('iadm06.institute.view'))
    OR (SELECT iam.is_platform_admin())
  );

-- No insert/update/delete policies for catalogue via authenticated → write API impossible under RLS
-- (service_role used only by migrations)

DROP POLICY IF EXISTS anl_export_requests_select ON anl.export_requests;
CREATE POLICY anl_export_requests_select ON anl.export_requests
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      (SELECT iam.has_permission('iadm06.export.view'))
      AND (
        requester_user_id = (SELECT auth.uid())
        OR (
          scope_type = 'INSTITUTE'
          AND scope_tenant_id IS NOT NULL
          AND scope_tenant_id = (SELECT core.current_tenant_id())
          AND requester_user_id = (SELECT auth.uid())
        )
      )
    )
  );

DROP POLICY IF EXISTS anl_export_requests_insert ON anl.export_requests;
CREATE POLICY anl_export_requests_insert ON anl.export_requests
  FOR INSERT TO authenticated
  WITH CHECK (
    requester_user_id = (SELECT auth.uid())
    AND (
      (SELECT iam.is_platform_admin())
      OR (
        (SELECT iam.has_permission('iadm06.export.request'))
        AND scope_type = 'INSTITUTE'
        AND scope_tenant_id = (SELECT core.current_tenant_id())
      )
    )
  );

DROP POLICY IF EXISTS anl_export_requests_update ON anl.export_requests;
CREATE POLICY anl_export_requests_update ON anl.export_requests
  FOR UPDATE TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (
      ((SELECT iam.has_permission('iadm06.export.request')) OR (SELECT iam.has_permission('iadm06.export.view')))
      AND requester_user_id = (SELECT auth.uid())
    )
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR requester_user_id = (SELECT auth.uid())
  );

GRANT USAGE ON SCHEMA anl TO authenticated, service_role;
GRANT SELECT ON anl.metric_catalogue TO authenticated, service_role;
GRANT SELECT, INSERT, UPDATE ON anl.export_requests TO authenticated, service_role;

COMMIT;
