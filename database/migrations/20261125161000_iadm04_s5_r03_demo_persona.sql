-- IADM-04 S5 evidence 4a: DEMO institute + Institution Admin (R03) persona (DEV only).
-- Auth user is created by scripts/dev/iadm04_seed_r03_demo.mjs; this SQL wires tenant/profile/role/sub.

-- 1. DEMO institute (ACTIVE)
INSERT INTO core.tenants (
  id, code, name, legal_name, status, currency_code, timezone, locale, profile
)
VALUES (
  'd4000000-0000-4000-8000-000000000004',
  'IADM04_DEMO',
  'IADM-04 DEMO Institute',
  'IADM-04 DEMO Institute (DEV)',
  'ACTIVE',
  'INR',
  'Asia/Kolkata',
  'en-IN',
  '{"demo_label":"DEMO","iadm04_s5":true}'::jsonb
)
ON CONFLICT (id) DO UPDATE
SET status = 'ACTIVE',
    deleted_at = NULL,
    updated_at = now(),
    name = EXCLUDED.name,
    code = EXCLUDED.code;

-- 2. INSTITUTION_ADMIN role for this tenant
INSERT INTO iam.roles (tenant_id, code, name, description, is_system, status)
SELECT
  'd4000000-0000-4000-8000-000000000004'::uuid,
  'INSTITUTION_ADMIN',
  'Institution Admin',
  'IADM-04 S5 DEMO R03 role',
  true,
  'ACTIVE'
WHERE NOT EXISTS (
  SELECT 1 FROM iam.roles r
  WHERE r.tenant_id = 'd4000000-0000-4000-8000-000000000004'
    AND r.code = 'INSTITUTION_ADMIN'
    AND r.deleted_at IS NULL
);

-- 3. Profile + role for Auth user created by seed script
DO $$
DECLARE
  v_user uuid;
  v_role uuid;
BEGIN
  SELECT id INTO v_user FROM auth.users WHERE email = 'institution.iadm04.demo@campx.local' LIMIT 1;
  IF v_user IS NULL THEN
    RAISE NOTICE 'IADM-04 S5: Auth user not found yet — run iadm04_seed_r03_demo.mjs then re-apply this migration/block.';
    RETURN;
  END IF;

  SELECT id INTO v_role FROM iam.roles
  WHERE tenant_id = 'd4000000-0000-4000-8000-000000000004'
    AND code = 'INSTITUTION_ADMIN' AND deleted_at IS NULL
  LIMIT 1;

  INSERT INTO iam.user_profiles (id, tenant_id, email, full_name, username, status)
  VALUES (
    v_user,
    'd4000000-0000-4000-8000-000000000004',
    'institution.iadm04.demo@campx.local',
    'IADM-04 DEMO Institution Admin (R03)',
    'iadm04_demo_r03',
    'ACTIVE'
  )
  ON CONFLICT (id) DO UPDATE
  SET tenant_id = EXCLUDED.tenant_id,
      email = EXCLUDED.email,
      full_name = EXCLUDED.full_name,
      status = 'ACTIVE',
      deleted_at = NULL,
      updated_at = now();

  IF v_role IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM iam.role_assignments ra
    WHERE ra.user_id = v_user AND ra.role_id = v_role AND ra.deleted_at IS NULL
  ) THEN
    INSERT INTO iam.role_assignments (tenant_id, user_id, role_id)
    VALUES ('d4000000-0000-4000-8000-000000000004', v_user, v_role);
  END IF;
END $$;

-- 4. DEMO PENDING subscription (one current)
INSERT INTO plat.subscriptions (
  tenant_id, plan_id, start_date, end_date, auto_renew, status,
  demo_label, idempotency_key, status_reason
)
SELECT
  'd4000000-0000-4000-8000-000000000004'::uuid,
  p.id,
  CURRENT_DATE,
  CURRENT_DATE + INTERVAL '1 year',
  true,
  'PENDING',
  'DEMO',
  'iadm04-s5-r03-demo-pending',
  'IADM-04 S5 DEMO PENDING for evidence 4a'
FROM plat.subscription_plans p
WHERE p.plan_code = 'STANDARD' AND p.version_no = 1 AND p.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.subscriptions s
    WHERE s.tenant_id = 'd4000000-0000-4000-8000-000000000004'
      AND s.deleted_at IS NULL
      AND s.status IN ('TRIAL','PENDING','ACTIVE')
  )
LIMIT 1;

-- Ensure status is PENDING if seed re-run left ACTIVE/TRIAL from a prior film
UPDATE plat.subscriptions
SET status = 'PENDING',
    status_reason = 'IADM-04 S5 DEMO PENDING for evidence 4a',
    trial_ends_on = NULL,
    updated_at = now()
WHERE tenant_id = 'd4000000-0000-4000-8000-000000000004'
  AND idempotency_key = 'iadm04-s5-r03-demo-pending'
  AND deleted_at IS NULL;

INSERT INTO plat.invoices (
  tenant_id, subscription_id, invoice_number, status, amount_minor_units, currency_code,
  amount, issued_on, due_date, internal_ref, pricing_snapshot, demo_label, idempotency_key
)
SELECT
  s.tenant_id,
  s.id,
  'DEMO-IADM04-S5-R03-001',
  'ISSUED',
  COALESCE(p.price_minor_units, 100000),
  COALESCE(p.currency_code, 'INR'),
  (COALESCE(p.price_minor_units, 100000)::numeric / 100.0),
  CURRENT_DATE,
  CURRENT_DATE + 14,
  'DEMO-IADM04-S5-R03-INV',
  jsonb_build_object('planCode', p.plan_code, 'priceMinorUnits', p.price_minor_units, 'demo', true),
  'DEMO',
  'iadm04-s5-r03-demo-invoice'
FROM plat.subscriptions s
JOIN plat.subscription_plans p ON p.id = s.plan_id
WHERE s.tenant_id = 'd4000000-0000-4000-8000-000000000004'
  AND s.status = 'PENDING'
  AND s.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM plat.invoices i
    WHERE i.idempotency_key = 'iadm04-s5-r03-demo-invoice' AND i.deleted_at IS NULL
  )
LIMIT 1;
