-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION (GATED PROPOSAL — DO NOT EXECUTE WITHOUT APPROVAL)
-- ==============================================================================
-- Target Environment: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Target Schemas: core, iam
-- Required Ritual: Explicit owner command "GO PROVISION-TEST-TENANT"
-- Safety Policy:
--   1. NEVER EXECUTE AUTOMATICALLY OR ON FORBIDDEN PROD (ekspahbsbwdpraaqfqui).
--   2. Enforces strict preconditions (unique constraint safety, collision detection).
--   3. Validates trigger execution and side effect counts (30 system roles, 2,138 role-permissions).
-- ==============================================================================

DO $$
DECLARE
  v_tenant_id uuid := '11111111-1111-1111-1111-111111111111'::uuid;
  v_tenant_code text := 'CAMPX_STAGING';
  v_existing_id uuid;
  v_existing_code text;
  v_role_count integer;
  v_perm_count integer;
BEGIN
  -- ----------------------------------------------------------------------------
  -- PRECONDITION 1: Safe Collision Detection
  -- ----------------------------------------------------------------------------
  -- Check if code 'CAMPX_STAGING' already exists under a DIFFERENT tenant ID
  SELECT id INTO v_existing_id 
  FROM core.tenants 
  WHERE code = v_tenant_code AND deleted_at IS NULL;

  IF v_existing_id IS NOT NULL AND v_existing_id <> v_tenant_id THEN
    RAISE EXCEPTION 'Precondition Failed: Tenant code "%" already exists with different UUID %', v_tenant_code, v_existing_id;
  END IF;

  -- Check if ID already exists under a DIFFERENT tenant code
  SELECT code INTO v_existing_code 
  FROM core.tenants 
  WHERE id = v_tenant_id;

  IF v_existing_code IS NOT NULL AND v_existing_code <> v_tenant_code THEN
    RAISE EXCEPTION 'Precondition Failed: Tenant ID % already exists with different code "%"', v_tenant_id, v_existing_code;
  END IF;

  -- ----------------------------------------------------------------------------
  -- PRECONDITION 2: Trigger Availability Check
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (
    SELECT 1 FROM pg_trigger t
    JOIN pg_class c ON c.oid = t.tgrelid
    JOIN pg_namespace n ON n.oid = c.relnamespace
    WHERE n.nspname = 'core' AND c.relname = 'tenants' AND t.tgname = 'trg_seed_roles'
  ) THEN
    RAISE EXCEPTION 'Precondition Failed: Required role-seeding trigger "trg_seed_roles" not found on core.tenants';
  END IF;

  -- ----------------------------------------------------------------------------
  -- EXECUTION: Idempotent Insert into core.tenants
  -- Note: Column constraint ck_tenants_x1 validates regex ^[A-Z0-9_-]{2,30}$
  -- ----------------------------------------------------------------------------
  INSERT INTO core.tenants (
    id,
    code,
    name,
    legal_name,
    status,
    currency_code,
    timezone,
    locale,
    profile,
    created_at,
    updated_at
  )
  VALUES (
    v_tenant_id,
    v_tenant_code,
    'CampX Staging Tenant',
    'CampXSync Educational Network Staging Ltd.',
    'ACTIVE',
    'INR',
    'Asia/Kolkata',
    'en-IN',
    '{"tier": "ENTERPRISE", "environment": "staging"}'::jsonb,
    now(),
    now()
  )
  ON CONFLICT (id) DO NOTHING;

  -- ----------------------------------------------------------------------------
  -- POSTCONDITION 1: Verify Tenant Row Presence and State
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (
    SELECT 1 FROM core.tenants 
    WHERE id = v_tenant_id AND status = 'ACTIVE' AND deleted_at IS NULL
  ) THEN
    RAISE EXCEPTION 'Postcondition Failed: Tenant % was not inserted or is not ACTIVE', v_tenant_id;
  END IF;

  -- ----------------------------------------------------------------------------
  -- POSTCONDITION 2: Verify Trigger-Driven Side Effects (iam.roles & iam.role_permissions)
  -- trg_seed_roles invokes iam.on_tenant_created() -> iam.seed_system_roles(NEW.id)
  -- ----------------------------------------------------------------------------
  SELECT count(*) INTO v_role_count FROM iam.roles WHERE tenant_id = v_tenant_id AND is_system = true AND deleted_at IS NULL;
  SELECT count(*) INTO v_perm_count FROM iam.role_permissions WHERE tenant_id = v_tenant_id;

  RAISE NOTICE 'Tenant % provisioned successfully. Seeded roles: %, role-permissions: %', v_tenant_id, v_role_count, v_perm_count;

  IF v_role_count <> 30 THEN
    RAISE WARNING 'Expected 30 seeded system roles, found %', v_role_count;
  END IF;
  IF v_perm_count <> 2138 THEN
    RAISE WARNING 'Expected 2,138 seeded role-permission bindings, found %', v_perm_count;
  END IF;
END $$;
