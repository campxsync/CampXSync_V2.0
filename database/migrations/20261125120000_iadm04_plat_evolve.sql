-- Migration: 20261125120000_iadm04_plat_evolve.sql
-- Plain language: Evolve plat.subscription_plans / plat.subscriptions for IADM-04 S1.
-- Adds PENDING/GRACE/EXPIRED; price_minor_units; plan version/status; one-current rule;
-- subscription history. Keeps legacy price numeric for monolith until cutover.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

-- Snapshot helper note (before): see docs/iadm/iadm-04/evidence/s1/ before apply scripts.

-- -------------------------------------------------------------------------
-- 1. Plans: versioning + minor units (keep price for monolith compatibility)
-- -------------------------------------------------------------------------
ALTER TABLE plat.subscription_plans
  ADD COLUMN IF NOT EXISTS version_no integer NOT NULL DEFAULT 1,
  ADD COLUMN IF NOT EXISTS plan_status text NOT NULL DEFAULT 'ACTIVE',
  ADD COLUMN IF NOT EXISTS price_minor_units bigint,
  ADD COLUMN IF NOT EXISTS demo_label text;

UPDATE plat.subscription_plans
SET price_minor_units = ROUND(price * 100)::bigint
WHERE price_minor_units IS NULL AND price IS NOT NULL;

UPDATE plat.subscription_plans
SET price_minor_units = 0
WHERE price_minor_units IS NULL;

ALTER TABLE plat.subscription_plans
  ALTER COLUMN price_minor_units SET NOT NULL,
  ALTER COLUMN price_minor_units SET DEFAULT 0;

ALTER TABLE plat.subscription_plans
  DROP CONSTRAINT IF EXISTS ck_subscription_plans_plan_status;
ALTER TABLE plat.subscription_plans
  ADD CONSTRAINT ck_subscription_plans_plan_status
  CHECK (plan_status IN ('DRAFT', 'ACTIVE', 'DEPRECATED'));

ALTER TABLE plat.subscription_plans
  DROP CONSTRAINT IF EXISTS ck_subscription_plans_price_minor;
ALTER TABLE plat.subscription_plans
  ADD CONSTRAINT ck_subscription_plans_price_minor
  CHECK (price_minor_units >= 0);

-- Allow plan_code + version uniqueness (soft-delete aware)
DROP INDEX IF EXISTS uq_subscription_plans_plan_code;
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_plans_code_version
  ON plat.subscription_plans (plan_code, version_no)
  WHERE deleted_at IS NULL;

-- -------------------------------------------------------------------------
-- 2. Subscriptions: expand status set + provenance columns
-- -------------------------------------------------------------------------
ALTER TABLE plat.subscriptions
  DROP CONSTRAINT IF EXISTS ck_subscriptions_status;

ALTER TABLE plat.subscriptions
  ADD CONSTRAINT ck_subscriptions_status
  CHECK (status IN (
    'TRIAL', 'PENDING', 'ACTIVE', 'GRACE', 'EXPIRED',
    'PAST_DUE', 'SUSPENDED', 'CANCELLED'
  ));

ALTER TABLE plat.subscriptions
  ADD COLUMN IF NOT EXISTS demo_label text,
  ADD COLUMN IF NOT EXISTS idempotency_key text,
  ADD COLUMN IF NOT EXISTS trial_ends_on date,
  ADD COLUMN IF NOT EXISTS status_reason text;

CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_idempotency
  ON plat.subscriptions (idempotency_key)
  WHERE idempotency_key IS NOT NULL AND deleted_at IS NULL;

-- One current subscription per institute across TRIAL/PENDING/ACTIVE (owner-confirmed)
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscriptions_one_current
  ON plat.subscriptions (tenant_id)
  WHERE deleted_at IS NULL
    AND status IN ('TRIAL', 'PENDING', 'ACTIVE');

-- -------------------------------------------------------------------------
-- 3. Subscription lifecycle history
-- -------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS plat.subscription_history (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  tenant_id uuid NOT NULL,
  subscription_id uuid NOT NULL,
  from_status text,
  to_status text NOT NULL,
  reason text,
  actor_user_id uuid,
  correlation_id text,
  created_at timestamptz NOT NULL DEFAULT now(),
  created_by uuid DEFAULT auth.uid()
);

CREATE INDEX IF NOT EXISTS ix_subscription_history_sub
  ON plat.subscription_history (subscription_id, created_at DESC);

ALTER TABLE plat.subscription_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE plat.subscription_history FORCE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS subscription_history_select ON plat.subscription_history;
CREATE POLICY subscription_history_select ON plat.subscription_history
  FOR SELECT TO authenticated
  USING (
    tenant_id = (SELECT core.current_tenant_id())
    OR (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.audit.view'))
    OR (SELECT iam.has_permission('iadm04.subscriptions.view'))
  );

DROP POLICY IF EXISTS subscription_history_insert ON plat.subscription_history;
CREATE POLICY subscription_history_insert ON plat.subscription_history
  FOR INSERT TO authenticated
  WITH CHECK (
    (SELECT iam.is_platform_admin())
    OR (SELECT iam.has_permission('iadm04.subscriptions.attach'))
  );

GRANT SELECT, INSERT ON plat.subscription_history TO authenticated;
GRANT ALL ON plat.subscription_history TO service_role;

-- -------------------------------------------------------------------------
-- 4. Plan entitlements: optional unit label
-- -------------------------------------------------------------------------
ALTER TABLE plat.plan_entitlements
  ADD COLUMN IF NOT EXISTS limit_unit text,
  ADD COLUMN IF NOT EXISTS demo_label text;

COMMENT ON COLUMN plat.subscription_plans.price_minor_units IS
  'IADM-04: whole minor units; legacy price numeric kept for monolith until cutover';

COMMIT;
