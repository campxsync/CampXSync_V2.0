-- Migration: 20261123010100_iadm02_demo_seed.sql
-- Plain language: IADM-02 DEV demo seed (US-007). No passwords. Second R01 Auth user
-- is created by scripts/dev/iadm02_seed_second_r01.mjs (not this SQL).
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

-- 1. Remove security-test POL_SEC residue (snapshot in evidence before apply)
UPDATE cfg.policies
SET deleted_at = now(), updated_at = now()
WHERE policy_code IN ('POL_SEC_6C9FA1E8', 'POL_SEC_708CFFCF')
  AND deleted_at IS NULL;

-- 2. VER-001 initial published version from setting_definitions defaults
DO $$
DECLARE
  v_payload jsonb;
  v_checksum text;
BEGIN
  IF EXISTS (
    SELECT 1 FROM cfg.config_versions
    WHERE version_label = 'VER-001' AND deleted_at IS NULL
  ) THEN
    RAISE NOTICE 'IADM02 seed: VER-001 already present';
    RETURN;
  END IF;

  SELECT jsonb_object_agg(setting_key, default_value)
  INTO v_payload
  FROM cfg.setting_definitions
  WHERE deleted_at IS NULL
    AND setting_key IN (
      'attendance.min_pct',
      'exam.grace_marks_max',
      'fees.late_fee_grace_days',
      'security.session_timeout_min',
      'iam.delegation.max_validity_days'
    );

  v_checksum := md5(COALESCE(v_payload::text, '{}'));

  INSERT INTO cfg.config_versions (
    version_label, status, effective_from, release_notes, published_by,
    checksum, payload, idempotency_key, correlation_id
  ) VALUES (
    'VER-001',
    'PUBLISHED',
    now(),
    'IADM-02 initial published defaults (DEV seed)',
    '54e8308c-ada3-4065-a9fe-51814af6b971',
    v_checksum,
    COALESCE(v_payload, '{}'::jsonb),
    'iadm02-seed-ver-001',
    'iadm02-seed-correlation-ver-001'
  );
END $$;

-- 3. DRAFT-001 valid (security.session_timeout_min = 45)
INSERT INTO cfg.config_drafts (
  setting_key, proposed_value, reason, status, author_user_id,
  idempotency_key, correlation_id, validation_errors
)
SELECT
  'security.session_timeout_min',
  '45'::jsonb,
  'DEV seed DRAFT-001 valid timeout for demo',
  'VALIDATED',
  '54e8308c-ada3-4065-a9fe-51814af6b971',
  'iadm02-seed-draft-001',
  'iadm02-seed-correlation-draft-001',
  '[]'::jsonb
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.config_drafts d
  WHERE d.idempotency_key = 'iadm02-seed-draft-001' AND d.deleted_at IS NULL
);

-- 4. DRAFT-002 invalid (attendance.min_pct = 150)
INSERT INTO cfg.config_drafts (
  setting_key, proposed_value, reason, status, author_user_id,
  idempotency_key, correlation_id, validation_errors
)
SELECT
  'attendance.min_pct',
  '150'::jsonb,
  'DEV seed DRAFT-002 invalid attendance for validation demo',
  'DRAFT',
  '54e8308c-ada3-4065-a9fe-51814af6b971',
  'iadm02-seed-draft-002',
  'iadm02-seed-correlation-draft-002',
  '[{"field":"proposedValue","message":"The value does not match the required type or bounds (0..100)."}]'::jsonb
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.config_drafts d
  WHERE d.idempotency_key = 'iadm02-seed-draft-002' AND d.deleted_at IS NULL
);

-- 5. Clean demo policy for policy catalogue (not POL_SEC residue)
INSERT INTO cfg.policies (policy_code, name, category, rules, enforcement_mode)
SELECT
  'IADM02_POL_PLATFORM_BASELINE',
  'Platform baseline policy (DEV)',
  'PLATFORM',
  '{"schemaVersion":1,"rules":[{"key":"security.session_timeout_min","max":1440}]}'::jsonb,
  'ENFORCE'
WHERE NOT EXISTS (
  SELECT 1 FROM cfg.policies p
  WHERE p.policy_code = 'IADM02_POL_PLATFORM_BASELINE' AND p.deleted_at IS NULL
);

-- 6. SVC-CONSUMER-01 — seed-only api client + grant (no create/elevate API)
-- Uses a dedicated auth.users row created by the node seed script when missing.
-- Placeholder grant wired after client exists; idempotent block here for permission only.

DO $$
DECLARE
  v_client_id uuid;
  v_user_id uuid;
  v_perm_id uuid;
  v_tenant_id uuid := '72ecbfe3-2077-41b5-8a20-b6d3e045311b'; -- working MAC institute
BEGIN
  SELECT id INTO v_perm_id FROM iam.permissions
  WHERE code = 'cfg.consumer.read' AND deleted_at IS NULL LIMIT 1;

  SELECT id INTO v_client_id FROM iam.api_clients
  WHERE name = 'SVC-CONSUMER-01' AND deleted_at IS NULL LIMIT 1;

  IF v_client_id IS NULL THEN
    -- Look up Auth user created by node seed (email iadm02.consumer.dev@campx.local)
    SELECT id INTO v_user_id FROM auth.users
    WHERE email = 'iadm02.consumer.dev@campx.local' LIMIT 1;

    IF v_user_id IS NULL THEN
      RAISE NOTICE 'IADM02 seed: SVC-CONSUMER-01 Auth user not found yet — run scripts/dev/iadm02_seed_second_r01.mjs';
      RETURN;
    END IF;

    v_client_id := gen_random_uuid();
    INSERT INTO iam.api_clients (
      id, tenant_id, user_id, name, description, status, expires_at, created_by
    ) VALUES (
      v_client_id,
      v_tenant_id,
      v_user_id,
      'SVC-CONSUMER-01',
      'IADM-02 DEV consumer for effective-config read',
      'ACTIVE',
      now() + interval '90 days',
      '54e8308c-ada3-4065-a9fe-51814af6b971'
    );
  END IF;

  IF v_perm_id IS NOT NULL AND NOT EXISTS (
    SELECT 1 FROM iam.api_client_grants g
    WHERE g.client_id = v_client_id AND g.permission_id = v_perm_id AND g.deleted_at IS NULL
  ) THEN
    INSERT INTO iam.api_client_grants (
      tenant_id, client_id, permission_id, resource_scope, valid_to, created_by
    ) VALUES (
      v_tenant_id,
      v_client_id,
      v_perm_id,
      '{"domains":["security","attendance"],"keys":["security.session_timeout_min","attendance.min_pct"]}'::jsonb,
      now() + interval '90 days',
      '54e8308c-ada3-4065-a9fe-51814af6b971'
    );
  END IF;
END $$;

COMMIT;
