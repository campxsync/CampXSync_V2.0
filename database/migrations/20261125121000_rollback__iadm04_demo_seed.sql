-- Rollback: 20261125121000_iadm04_demo_seed.sql
BEGIN;

DROP FUNCTION IF EXISTS plat.subscription_allows_access(uuid);

DELETE FROM plat.subscription_history
WHERE correlation_id = 'iadm04-s1-seed'
   OR reason = 'IADM-04 S1 DEMO seed';

DELETE FROM plat.subscriptions
WHERE demo_label = 'DEMO'
  AND idempotency_key LIKE 'iadm04-demo-active-%';

DELETE FROM plat.plan_entitlements WHERE demo_label = 'DEMO';

DELETE FROM plat.subscription_plans
WHERE demo_label = 'DEMO'
  AND plan_code IN ('BASIC', 'STANDARD', 'PREMIUM');

DELETE FROM cfg.feature_flags
WHERE flag_key IN (
  'billing.demo.students_portal',
  'billing.demo.faculty_desk',
  'billing.demo.analytics',
  'billing.demo.documents'
);

DELETE FROM cfg.setting_definitions
WHERE setting_key IN (
  'billing.timezone',
  'billing.trial.eligibility',
  'billing.trial.duration_days',
  'billing.trial.conversion_mode',
  'billing.grace.days',
  'billing.renewal.reminder_days',
  'billing.renewal.auto_invoice'
);

-- Restore legacy plans that were soft-deprecated by seed (best-effort)
UPDATE plat.subscription_plans
SET plan_status = 'ACTIVE', demo_label = NULL
WHERE demo_label = 'LEGACY_TEST';

COMMIT;
