-- Migration: 20261125150000_iadm04_s4_lifecycle_usage.sql
-- Plain language: IADM-04 S4 — job checkpoints, usage measurements, renewal desk support.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

-- -------------------------------------------------------------------------
-- 1. Job checkpoints (idempotent scheduled work)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.billing_job_checkpoints (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  job_name text NOT NULL,
  checkpoint_key text NOT NULL,
  status text NOT NULL DEFAULT 'SUCCEEDED',
  detail text,
  ran_at timestamptz NOT NULL DEFAULT now(),
  actor_user_id uuid,
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  deleted_at timestamptz,
  CONSTRAINT ck_billing_job_status CHECK (status IN ('SUCCEEDED', 'SKIPPED', 'FAILED')),
  CONSTRAINT uq_billing_job_checkpoint UNIQUE (job_name, checkpoint_key)
);

CREATE INDEX IF NOT EXISTS ix_billing_job_ran
  ON plat.billing_job_checkpoints (job_name, ran_at DESC)
  WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 2. Usage measurements (idempotent by dedupe key)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.usage_measurements (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  source text NOT NULL,
  period_start date NOT NULL,
  period_end date NOT NULL,
  entitlement_key text NOT NULL,
  quantity numeric NOT NULL DEFAULT 0,
  unit text,
  dedupe_key text NOT NULL,
  incomplete boolean NOT NULL DEFAULT false,
  incomplete_reason text,
  recorded_at timestamptz NOT NULL DEFAULT now(),
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  deleted_at timestamptz,
  CONSTRAINT ck_usage_quantity CHECK (quantity >= 0),
  CONSTRAINT uq_usage_dedupe UNIQUE (dedupe_key)
);

CREATE INDEX IF NOT EXISTS ix_usage_tenant_period
  ON plat.usage_measurements (tenant_id, period_start, period_end)
  WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 3. Renewal action audit log (desk decisions)
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.renewal_actions (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  subscription_id uuid NOT NULL,
  action text NOT NULL,
  reason text,
  actor_user_id uuid NOT NULL,
  acted_at timestamptz NOT NULL DEFAULT now(),
  demo_label text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid(),
  deleted_at timestamptz,
  CONSTRAINT ck_renewal_action CHECK (action IN (
    'MARK_GRACE', 'MARK_EXPIRED', 'MARK_RENEWED', 'SUSPEND', 'REMINDER_NOTED'
  ))
);

CREATE INDEX IF NOT EXISTS ix_renewal_actions_sub
  ON plat.renewal_actions (subscription_id, acted_at DESC)
  WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 4. RLS
-- -------------------------------------------------------------------------
ALTER TABLE plat.billing_job_checkpoints ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.billing_job_checkpoints FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.usage_measurements ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.usage_measurements FORCE ROW LEVEL SECURITY;
ALTER TABLE plat.renewal_actions ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.renewal_actions FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS billing_job_select ON plat.billing_job_checkpoints;
CREATE POLICY billing_job_select ON plat.billing_job_checkpoints
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.jobs.run'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
  );

DROP POLICY IF EXISTS billing_job_write ON plat.billing_job_checkpoints;
CREATE POLICY billing_job_write ON plat.billing_job_checkpoints
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.jobs.run'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.jobs.run'))
  );

DROP POLICY IF EXISTS usage_select ON plat.usage_measurements;
CREATE POLICY usage_select ON plat.usage_measurements
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.usage.record'))
    OR (SELECT iam.has_permission('iadm04.reports.view'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
    OR (
      tenant_id = (SELECT core.current_tenant_id())
      AND (SELECT iam.has_permission('iadm04.subscriptions.view.own'))
    )
  );

DROP POLICY IF EXISTS usage_write ON plat.usage_measurements;
CREATE POLICY usage_write ON plat.usage_measurements
  FOR ALL TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.usage.record'))
  )
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.usage.record'))
  );

DROP POLICY IF EXISTS renewal_action_select ON plat.renewal_actions;
CREATE POLICY renewal_action_select ON plat.renewal_actions
  FOR SELECT TO authenticated
  USING (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
    OR (SELECT iam.has_permission('iadm04.audit.view'))
  );

DROP POLICY IF EXISTS renewal_action_insert ON plat.renewal_actions;
CREATE POLICY renewal_action_insert ON plat.renewal_actions
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
  );

GRANT SELECT, INSERT, UPDATE ON plat.billing_job_checkpoints TO authenticated;
GRANT SELECT, INSERT, UPDATE ON plat.usage_measurements TO authenticated;
GRANT SELECT, INSERT ON plat.renewal_actions TO authenticated;
GRANT ALL ON plat.billing_job_checkpoints TO service_role;
GRANT ALL ON plat.usage_measurements TO service_role;
GRANT ALL ON plat.renewal_actions TO service_role;

COMMENT ON TABLE plat.billing_job_checkpoints IS
  'IADM-04 S4: idempotent checkpoints for renewal/trial/expiry jobs';
COMMENT ON TABLE plat.usage_measurements IS
  'IADM-04 S4: usage against plan quotas; dedupe_key unique; no overage charge';
COMMENT ON TABLE plat.renewal_actions IS
  'IADM-04 S4: Super Admin renewal/expiry desk actions';

COMMIT;
