-- IADM-01 TECH-005: DEV-only idempotent demo seed (no credentials).
-- Plain language: ensures labelled demo institute INST-DEMO-001 exists for list/dashboard walks.
-- Does NOT create Auth users or passwords. Rollback: 20261122010100_rollback__iadm01_demo_seed_s1.sql

DO $$
DECLARE
  v_tenant_id uuid;
  v_actor uuid := '00000000-0000-0000-0000-000000000001';
BEGIN
  SELECT id INTO v_tenant_id FROM core.tenants WHERE code = 'INST-DEMO-001' AND deleted_at IS NULL LIMIT 1;
  IF v_tenant_id IS NOT NULL THEN
    RAISE NOTICE 'IADM01 demo seed: INST-DEMO-001 already present (%)', v_tenant_id;
    RETURN;
  END IF;

  v_tenant_id := gen_random_uuid();
  INSERT INTO core.tenants (
    id, code, name, status, profile, created_by, updated_by
  ) VALUES (
    v_tenant_id,
    'INST-DEMO-001',
    'IADM-01 Demo Institute (ACTIVE)',
    'ACTIVE',
    '{"type":"DEMO","planReference":"ENTERPRISE","seed":"TECH-005"}'::jsonb,
    v_actor,
    v_actor
  );

  IF NOT EXISTS (
    SELECT 1 FROM core.colleges WHERE tenant_id = v_tenant_id AND code = 'COL-DEMO-001' AND deleted_at IS NULL
  ) THEN
    INSERT INTO core.colleges (
      id, tenant_id, code, name, status, provisioning_status, created_by, updated_by
    ) VALUES (
      gen_random_uuid(),
      v_tenant_id,
      'COL-DEMO-001',
      'IADM-01 Demo College',
      'ACTIVE',
      'ACTIVE',
      v_actor,
      v_actor
    );
  END IF;

  RAISE NOTICE 'IADM01 demo seed: created INST-DEMO-001 / COL-DEMO-001';
END $$;
