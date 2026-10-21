-- ==============================================================================
-- CAMPXSYNC WAVE 0 MINIMAL TEST SEED (APPROVAL-GATED — DO NOT EXECUTE WITHOUT APPROVAL)
-- ==============================================================================
-- Target Environment: Supabase Dev (Project Ref: azulbaxszsqxymqmlwfe)
-- Target Schemas: core, iam
-- Required Ritual: Explicit owner command "GO SEED-WAVE0-DEV"
-- Safety Policy:
--   1. NEVER EXECUTE AUTOMATICALLY OR ON FORBIDDEN PROD (ekspahbsbwdpraaqfqui)
--      OR PROHIBITED OLD STAGING (kpwdvvcschankgggitnj).
--   2. Strict collision detection: aborts immediately without modifying existing data.
--   3. Pure INSERT statements (no upsert or overwrite of pre-existing records).
--   4. Validates trigger execution and side effect counts (30 system roles, 2,138 role-permissions).
--   5. Executed in a single atomic transaction; any failure causes total rollback.
-- ==============================================================================

BEGIN;

DO $$
DECLARE
  v_tenant_id     uuid := 'a0000000-0000-0000-0000-000000000001'::uuid;
  v_tenant_code   text := 'DEV_TEST_TENANT';
  v_college_id    uuid := 'a0000000-0000-0000-0000-000000000002'::uuid;
  v_college_code  text := 'DEV_TEST_COLLEGE';
  v_user_id       uuid := 'ba77b8f7-ab32-46bd-85d2-5aade3526880'::uuid;
  v_user_email    text := 'dev.admin@campxsync.test';
  v_assign_id     uuid := 'a0000000-0000-0000-0000-000000000003'::uuid;
  v_role_id       uuid;
  v_role_count    integer;
  v_perm_count    integer;
BEGIN
  -- ----------------------------------------------------------------------------
  -- PRECONDITION 1: Trigger Availability Check
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
  -- PRECONDITION 2: Pre-Write Collision Detection (Fail Closed — Do Not Overwrite)
  -- ----------------------------------------------------------------------------
  IF EXISTS (SELECT 1 FROM core.tenants WHERE id = v_tenant_id OR code = v_tenant_code) THEN
    RAISE EXCEPTION 'Precondition Failed: Tenant ID % or code "%" already exists. Aborting without modifying data.', v_tenant_id, v_tenant_code;
  END IF;

  IF EXISTS (SELECT 1 FROM core.colleges WHERE id = v_college_id OR code = v_college_code) THEN
    RAISE EXCEPTION 'Precondition Failed: College ID % or code "%" already exists. Aborting without modifying data.', v_college_id, v_college_code;
  END IF;

  IF EXISTS (SELECT 1 FROM iam.user_profiles WHERE id = v_user_id OR email = v_user_email) THEN
    RAISE EXCEPTION 'Precondition Failed: User profile ID % or email "%" already exists. Aborting without modifying data.', v_user_id, v_user_email;
  END IF;

  IF EXISTS (SELECT 1 FROM iam.role_assignments WHERE id = v_assign_id) THEN
    RAISE EXCEPTION 'Precondition Failed: Role assignment ID % already exists. Aborting without modifying data.', v_assign_id;
  END IF;

  -- ----------------------------------------------------------------------------
  -- STEP 1: Insert Test Tenant (Pure INSERT — No Upsert)
  -- Trigger trg_seed_roles automatically fires iam.seed_system_roles(v_tenant_id)
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
    updated_at,
    row_version
  ) VALUES (
    v_tenant_id,
    v_tenant_code,
    'CampX Architecture Dev Test Institute',
    'CampX Architecture Development Testing Institute Ltd.',
    'ACTIVE',
    'INR',
    'Asia/Kolkata',
    'en-IN',
    '{"tier": "ENTERPRISE", "environment": "dev_test"}'::jsonb,
    now(),
    now(),
    1
  );

  -- ----------------------------------------------------------------------------
  -- STEP 2: Verify Trigger-Driven Side Effects (iam.roles & iam.role_permissions)
  -- ----------------------------------------------------------------------------
  SELECT count(*) INTO v_role_count 
  FROM iam.roles 
  WHERE tenant_id = v_tenant_id AND is_system = true AND deleted_at IS NULL;

  SELECT count(*) INTO v_perm_count 
  FROM iam.role_permissions 
  WHERE tenant_id = v_tenant_id;

  IF v_role_count <> 30 THEN
    RAISE EXCEPTION 'Postcondition Failed: Expected 30 trigger-seeded system roles, found %', v_role_count;
  END IF;

  IF v_perm_count <> 2138 THEN
    RAISE EXCEPTION 'Postcondition Failed: Expected 2,138 trigger-seeded role-permissions, found %', v_perm_count;
  END IF;

  -- Locate the trigger-generated COLLEGE_ADMIN role
  SELECT id INTO v_role_id
  FROM iam.roles
  WHERE tenant_id = v_tenant_id AND code = 'COLLEGE_ADMIN' AND deleted_at IS NULL;

  IF v_role_id IS NULL THEN
    RAISE EXCEPTION 'Postcondition Failed: COLLEGE_ADMIN role was not created by trg_seed_roles for tenant %', v_tenant_id;
  END IF;

  -- ----------------------------------------------------------------------------
  -- STEP 3: Insert Test College (Pure INSERT)
  -- ----------------------------------------------------------------------------
  INSERT INTO core.colleges (
    id,
    tenant_id,
    code,
    name,
    legal_name,
    status,
    provisioning_status,
    created_at,
    updated_at,
    row_version
  ) VALUES (
    v_college_id,
    v_tenant_id,
    v_college_code,
    'CampX Dev College of Engineering',
    'CampX Dev College of Engineering Education Trust',
    'ACTIVE',
    'ACTIVE',
    now(),
    now(),
    1
  );

  -- ----------------------------------------------------------------------------
  -- STEP 4: Insert Test User Profile (Pure INSERT)
  -- ID ba77b8f7-ab32-46bd-85d2-5aade3526880 matches existing live test JWT subject
  -- ----------------------------------------------------------------------------
  INSERT INTO iam.user_profiles (
    id,
    tenant_id,
    college_id,
    username,
    email,
    full_name,
    status,
    created_at,
    updated_at,
    row_version
  ) VALUES (
    v_user_id,
    v_tenant_id,
    v_college_id,
    'dev.college.admin',
    v_user_email,
    'Dev Test College Admin',
    'ACTIVE',
    now(),
    now(),
    1
  );

  -- ----------------------------------------------------------------------------
  -- STEP 5: Assign COLLEGE_ADMIN Role (Pure INSERT)
  -- ----------------------------------------------------------------------------
  INSERT INTO iam.role_assignments (
    id,
    tenant_id,
    user_id,
    role_id,
    college_id,
    valid_from,
    valid_to,
    created_at,
    updated_at,
    row_version
  ) VALUES (
    v_assign_id,
    v_tenant_id,
    v_user_id,
    v_role_id,
    v_college_id,
    CURRENT_DATE - INTERVAL '1 day',
    CURRENT_DATE + INTERVAL '365 days',
    now(),
    now(),
    1
  );

  -- ----------------------------------------------------------------------------
  -- POSTCONDITION: Explicit Validation of All Seeded Records
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (SELECT 1 FROM core.tenants WHERE id = v_tenant_id AND status = 'ACTIVE' AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'Postcondition Failed: Tenant row is missing or not active';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM core.colleges WHERE id = v_college_id AND status = 'ACTIVE' AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'Postcondition Failed: College row is missing or not active';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM iam.user_profiles WHERE id = v_user_id AND status = 'ACTIVE' AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'Postcondition Failed: User profile row is missing or not active';
  END IF;

  IF NOT EXISTS (SELECT 1 FROM iam.role_assignments WHERE id = v_assign_id AND role_id = v_role_id AND deleted_at IS NULL) THEN
    RAISE EXCEPTION 'Postcondition Failed: Role assignment row is missing or not active';
  END IF;

  RAISE NOTICE 'SUCCESS: Wave 0 minimal test seed completed safely and verified.';
END $$;

COMMIT;
