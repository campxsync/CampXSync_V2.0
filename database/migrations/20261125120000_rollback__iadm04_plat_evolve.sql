-- Rollback: 20261125120000_iadm04_plat_evolve.sql
-- WARNING: drops IADM-04 history and new columns; restores prior status check without PENDING/GRACE/EXPIRED.
BEGIN;

DROP POLICY IF EXISTS subscription_history_insert ON plat.subscription_history;
DROP POLICY IF EXISTS subscription_history_select ON plat.subscription_history;
DROP TABLE IF EXISTS plat.subscription_history;

DROP INDEX IF EXISTS uq_subscriptions_one_current;
DROP INDEX IF EXISTS uq_subscriptions_idempotency;

ALTER TABLE plat.subscriptions
  DROP COLUMN IF EXISTS demo_label,
  DROP COLUMN IF EXISTS idempotency_key,
  DROP COLUMN IF EXISTS trial_ends_on,
  DROP COLUMN IF EXISTS status_reason;

-- Move any PENDING/GRACE/EXPIRED back before restoring old check
UPDATE plat.subscriptions SET status = 'SUSPENDED'
WHERE status IN ('PENDING', 'GRACE', 'EXPIRED') AND deleted_at IS NULL;

ALTER TABLE plat.subscriptions DROP CONSTRAINT IF EXISTS ck_subscriptions_status;
ALTER TABLE plat.subscriptions
  ADD CONSTRAINT ck_subscriptions_status
  CHECK (status IN ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED'));

ALTER TABLE plat.plan_entitlements
  DROP COLUMN IF EXISTS limit_unit,
  DROP COLUMN IF EXISTS demo_label;

DROP INDEX IF EXISTS uq_subscription_plans_code_version;
CREATE UNIQUE INDEX IF NOT EXISTS uq_subscription_plans_plan_code
  ON plat.subscription_plans (plan_code) WHERE deleted_at IS NULL;

ALTER TABLE plat.subscription_plans
  DROP CONSTRAINT IF EXISTS ck_subscription_plans_price_minor,
  DROP CONSTRAINT IF EXISTS ck_subscription_plans_plan_status;

ALTER TABLE plat.subscription_plans
  DROP COLUMN IF EXISTS version_no,
  DROP COLUMN IF EXISTS plan_status,
  DROP COLUMN IF EXISTS price_minor_units,
  DROP COLUMN IF EXISTS demo_label;

COMMIT;
