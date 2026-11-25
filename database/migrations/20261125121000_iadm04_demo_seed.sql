-- Migration: 20261125121000_iadm04_demo_seed.sql
-- Plain language: IADM-04 S1 DEMO catalogue, feature flags, trial policy keys,
-- and ACTIVE subscription for every non-deleted institute.
-- Target: azulbaxszsqxymqmlwfe (DEV only). All values labelled DEMO.

BEGIN;

-- -------------------------------------------------------------------------
-- 0. Soft-deprecate legacy wave/test plans (keep rows; do not delete)
-- -------------------------------------------------------------------------
UPDATE plat.subscription_plans
SET plan_status = 'DEPRECATED',
    demo_label = COALESCE(demo_label, 'LEGACY_TEST'),
    updated_at = now()
WHERE deleted_at IS NULL
  AND plan_code NOT IN ('BASIC', 'STANDARD', 'PREMIUM')
  AND plan_status <> 'DEPRECATED';

-- -------------------------------------------------------------------------
-- 1. DEMO plans BASIC / STANDARD / PREMIUM (v1)
-- -------------------------------------------------------------------------
INSERT INTO plat.subscription_plans (
  plan_code, name, billing_cycle, price, currency_code, is_published,
  version_no, plan_status, price_minor_units, demo_label
)
SELECT 'BASIC', 'DEMO Basic', 'MONTHLY', 4999.00, 'INR', true, 1, 'ACTIVE', 499900, 'DEMO'
WHERE NOT EXISTS (
  SELECT 1 FROM plat.subscription_plans p
  WHERE p.plan_code = 'BASIC' AND p.version_no = 1 AND p.deleted_at IS NULL
);

INSERT INTO plat.subscription_plans (
  plan_code, name, billing_cycle, price, currency_code, is_published,
  version_no, plan_status, price_minor_units, demo_label
)
SELECT 'STANDARD', 'DEMO Standard', 'MONTHLY', 9999.00, 'INR', true, 1, 'ACTIVE', 999900, 'DEMO'
WHERE NOT EXISTS (
  SELECT 1 FROM plat.subscription_plans p
  WHERE p.plan_code = 'STANDARD' AND p.version_no = 1 AND p.deleted_at IS NULL
);

INSERT INTO plat.subscription_plans (
  plan_code, name, billing_cycle, price, currency_code, is_published,
  version_no, plan_status, price_minor_units, demo_label
)
SELECT 'PREMIUM', 'DEMO Premium', 'MONTHLY', 19999.00, 'INR', true, 1, 'ACTIVE', 1999900, 'DEMO'
WHERE NOT EXISTS (
  SELECT 1 FROM plat.subscription_plans p
  WHERE p.plan_code = 'PREMIUM' AND p.version_no = 1 AND p.deleted_at IS NULL
);

-- -------------------------------------------------------------------------
-- 2. DEMO feature flags (IADM-02 store; used by plan entitlement mapping)
-- -------------------------------------------------------------------------
INSERT INTO cfg.feature_flags (flag_key, name, description, enabled, rollout_percentage)
SELECT 'billing.demo.students_portal', 'DEMO Students Portal', 'DEMO entitlement flag for student portal access', true, 100
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.feature_flags f WHERE f.flag_key = 'billing.demo.students_portal' AND f.deleted_at IS NULL
);

INSERT INTO cfg.feature_flags (flag_key, name, description, enabled, rollout_percentage)
SELECT 'billing.demo.faculty_desk', 'DEMO Faculty Desk', 'DEMO entitlement flag for faculty desk', true, 100
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.feature_flags f WHERE f.flag_key = 'billing.demo.faculty_desk' AND f.deleted_at IS NULL
);

INSERT INTO cfg.feature_flags (flag_key, name, description, enabled, rollout_percentage)
SELECT 'billing.demo.analytics', 'DEMO Analytics', 'DEMO entitlement flag for analytics pack', true, 100
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.feature_flags f WHERE f.flag_key = 'billing.demo.analytics' AND f.deleted_at IS NULL
);

INSERT INTO cfg.feature_flags (flag_key, name, description, enabled, rollout_percentage)
SELECT 'billing.demo.documents', 'DEMO Documents', 'DEMO entitlement flag for document module', true, 100
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.feature_flags f WHERE f.flag_key = 'billing.demo.documents' AND f.deleted_at IS NULL
);

-- -------------------------------------------------------------------------
-- 3. Plan entitlements (quotas + feature flags)
-- -------------------------------------------------------------------------
-- BASIC
INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.students_portal', 500, true, 'seats', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'BASIC' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.students_portal' AND e.deleted_at IS NULL
  );

INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.documents', 1, true, 'flag', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'BASIC' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.documents' AND e.deleted_at IS NULL
  );

-- STANDARD
INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.students_portal', 2000, true, 'seats', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'STANDARD' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.students_portal' AND e.deleted_at IS NULL
  );

INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.faculty_desk', 1, true, 'flag', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'STANDARD' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.faculty_desk' AND e.deleted_at IS NULL
  );

INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.documents', 1, true, 'flag', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'STANDARD' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.documents' AND e.deleted_at IS NULL
  );

-- PREMIUM
INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.students_portal', 10000, true, 'seats', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'PREMIUM' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.students_portal' AND e.deleted_at IS NULL
  );

INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.faculty_desk', 1, true, 'flag', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'PREMIUM' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.faculty_desk' AND e.deleted_at IS NULL
  );

INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.analytics', 1, true, 'flag', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'PREMIUM' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.analytics' AND e.deleted_at IS NULL
  );

INSERT INTO plat.plan_entitlements (plan_id, entitlement_key, limit_value, is_enabled, limit_unit, demo_label)
SELECT p.id, 'billing.demo.documents', 1, true, 'flag', 'DEMO'
FROM plat.subscription_plans p
WHERE p.plan_code = 'PREMIUM' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.plan_entitlements e
    WHERE e.plan_id = p.id AND e.entitlement_key = 'billing.demo.documents' AND e.deleted_at IS NULL
  );

-- -------------------------------------------------------------------------
-- 4. IADM-02 billing policy keys (DEMO defaults; fail-closed if absent)
-- -------------------------------------------------------------------------
INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT 'billing.timezone', 'STRING', '"Asia/Kolkata"'::jsonb, false, ARRAY['PLATFORM']::text[],
  'IADM-04 DEMO: billing day boundary timezone (D20).'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d WHERE d.setting_key = 'billing.timezone' AND d.deleted_at IS NULL
);

INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT 'billing.trial.eligibility', 'STRING', '"NEW_INSTITUTE_ONLY"'::jsonb, false, ARRAY['PLATFORM']::text[],
  'IADM-04 DEMO: trial eligibility rule (D18).'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d WHERE d.setting_key = 'billing.trial.eligibility' AND d.deleted_at IS NULL
);

INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT 'billing.trial.duration_days', 'INTEGER', '30'::jsonb, false, ARRAY['PLATFORM']::text[],
  'IADM-04 DEMO: free trial length in days.'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d WHERE d.setting_key = 'billing.trial.duration_days' AND d.deleted_at IS NULL
);

INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT 'billing.trial.conversion_mode', 'STRING', '"INVOICE_ON_END"'::jsonb, false, ARRAY['PLATFORM']::text[],
  'IADM-04 DEMO: when trial conversion invoice is raised.'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d WHERE d.setting_key = 'billing.trial.conversion_mode' AND d.deleted_at IS NULL
);

INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT 'billing.grace.days', 'INTEGER', '7'::jsonb, false, ARRAY['PLATFORM']::text[],
  'IADM-04 DEMO: subscription grace days after renewal miss (D6).'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d WHERE d.setting_key = 'billing.grace.days' AND d.deleted_at IS NULL
);

INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT 'billing.renewal.reminder_days', 'INTEGER', '14'::jsonb, false, ARRAY['PLATFORM']::text[],
  'IADM-04 DEMO: renewal reminder lead time in days.'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d WHERE d.setting_key = 'billing.renewal.reminder_days' AND d.deleted_at IS NULL
);

INSERT INTO cfg.setting_definitions (
  setting_key, data_type, default_value, is_secret, allowed_scopes, description
)
SELECT 'billing.renewal.auto_invoice', 'BOOLEAN', 'true'::jsonb, false, ARRAY['PLATFORM']::text[],
  'IADM-04 DEMO: whether renewal auto-raises invoice.'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.setting_definitions d WHERE d.setting_key = 'billing.renewal.auto_invoice' AND d.deleted_at IS NULL
);

-- -------------------------------------------------------------------------
-- 5. DEMO ACTIVE subscription for every non-deleted institute (STANDARD)
--    Skips institutes that already have TRIAL/PENDING/ACTIVE.
-- -------------------------------------------------------------------------
INSERT INTO plat.subscriptions (
  tenant_id, plan_id, start_date, end_date, auto_renew, status,
  demo_label, idempotency_key, status_reason
)
SELECT
  t.id,
  p.id,
  CURRENT_DATE,
  CURRENT_DATE + INTERVAL '1 year',
  true,
  'ACTIVE',
  'DEMO',
  'iadm04-demo-active-' || t.id::text,
  'IADM-04 S1 DEMO seed ACTIVE'
FROM core.tenants t
CROSS JOIN LATERAL (
  SELECT id FROM plat.subscription_plans
  WHERE plan_code = 'STANDARD' AND version_no = 1 AND deleted_at IS NULL
  LIMIT 1
) p
WHERE t.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.subscriptions s
    WHERE s.tenant_id = t.id
      AND s.deleted_at IS NULL
      AND s.status IN ('TRIAL', 'PENDING', 'ACTIVE')
  );

INSERT INTO plat.subscription_history (
  tenant_id, subscription_id, from_status, to_status, reason, correlation_id
)
SELECT s.tenant_id, s.id, NULL, 'ACTIVE', 'IADM-04 S1 DEMO seed', 'iadm04-s1-seed'
FROM plat.subscriptions s
WHERE s.demo_label = 'DEMO'
  AND s.idempotency_key LIKE 'iadm04-demo-active-%'
  AND NOT EXISTS (
    SELECT 1 FROM plat.subscription_history h WHERE h.subscription_id = s.id
  );

-- -------------------------------------------------------------------------
-- 6. Access helper: commercial gate (institute activated ∧ TRIAL|ACTIVE)
-- -------------------------------------------------------------------------
CREATE OR REPLACE FUNCTION plat.subscription_allows_access(p_tenant_id uuid)
RETURNS boolean
LANGUAGE sql
STABLE
SECURITY DEFINER
SET search_path = plat, core, public
AS $$
  SELECT EXISTS (
    SELECT 1
    FROM core.tenants t
    JOIN plat.subscriptions s
      ON s.tenant_id = t.id
     AND s.deleted_at IS NULL
     AND s.status IN ('TRIAL', 'ACTIVE')
    WHERE t.id = p_tenant_id
      AND t.deleted_at IS NULL
      AND t.status = 'ACTIVE'
  );
$$;

COMMENT ON FUNCTION plat.subscription_allows_access(uuid) IS
  'IADM-04 dual access gate: institute ACTIVE and subscription TRIAL or ACTIVE. PENDING/others = false.';

GRANT EXECUTE ON FUNCTION plat.subscription_allows_access(uuid) TO authenticated, service_role;

COMMIT;
