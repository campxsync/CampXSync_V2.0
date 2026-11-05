-- Migration: 20261105000400_provision_apex_sci.sql
-- Description: Live provisioning of institute APEX_SCI (Apex Institute of Science & Technology)
-- Authorization: Explicit owner command "GO PROVISION-TENANT"
-- Target Environment: Development ONLY (azulbaxszsqxymqmlwfe)
-- Tag: APEX_SCI_LIVE_PROVISIONING
-- Safety:
--   1. NEVER EXECUTE ON PROHIBITED PROD (ekspahbsbwdpraaqfqui) OR LEGACY STAGING (kpwdvvcschankgggitnj).
--   2. Enforces pre-flight collision detection (tenant code, admin email).
--   3. Executes complete 5-step Institute Onboarding Saga atomically in a single transaction.

BEGIN;

DO $$
DECLARE
  v_tenant_id uuid := 'c33a407e-3bda-464e-9543-ca46a011aead'::uuid;
  v_tenant_code text := 'APEX_SCI';
  v_tenant_name text := 'Apex Institute of Science & Technology';
  
  v_college_id uuid := 'c33a407e-3bda-464e-9543-ca46a0110001'::uuid;
  v_college_code text := 'SOC';
  v_college_name text := 'School of Computing';
  
  v_admin_id uuid := 'c33a407e-3bda-464e-9543-ca46a0110002'::uuid;
  v_admin_email text := 'robert.vance@apex.edu';
  v_admin_name text := 'Prof. Robert Vance';
  
  v_job_id uuid := 'c33a407e-3bda-464e-9543-ca46a0110003'::uuid;
  v_idempotency_key text := 'idem-live-provision-apex-sci-001';
  
  v_existing_id uuid;
  v_existing_email_id uuid;
  v_college_admin_role_id uuid;
  v_outbox_id uuid := gen_random_uuid();
  v_payload jsonb;
BEGIN
  -- ----------------------------------------------------------------------------
  -- PRECONDITION 1: Safe Collision Detection
  -- ----------------------------------------------------------------------------
  SELECT id INTO v_existing_id 
  FROM core.tenants 
  WHERE code = v_tenant_code AND deleted_at IS NULL;

  IF v_existing_id IS NOT NULL AND v_existing_id <> v_tenant_id THEN
    RAISE EXCEPTION 'Precondition Failed: Tenant code "%" already exists with different UUID %', v_tenant_code, v_existing_id;
  END IF;

  SELECT id INTO v_existing_email_id 
  FROM iam.user_profiles 
  WHERE email = v_admin_email AND deleted_at IS NULL;

  IF v_existing_email_id IS NOT NULL AND v_existing_email_id <> v_admin_id THEN
    RAISE EXCEPTION 'Precondition Failed: Admin email "%" already exists with different UUID %', v_admin_email, v_existing_email_id;
  END IF;

  -- ----------------------------------------------------------------------------
  -- STEP 1: Insert Tenant into core.tenants
  -- ----------------------------------------------------------------------------
  INSERT INTO core.tenants (
    id, code, name, legal_name, status, currency_code, timezone, locale, profile, onboarded_at, created_at, updated_at, row_version
  ) VALUES (
    v_tenant_id,
    v_tenant_code,
    v_tenant_name,
    v_tenant_name,
    'ACTIVE',
    'INR',
    'Asia/Kolkata',
    'en_IN',
    jsonb_build_object('plan', 'ENTERPRISE', 'subdomain', 'apex', 'provisioned_by', 'GO PROVISION-TENANT', 'tag', 'APEX_SCI_LIVE_PROVISIONING'),
    now(),
    now(),
    now(),
    1
  ) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = 'ACTIVE',
    updated_at = now();

  -- ----------------------------------------------------------------------------
  -- STEP 2: Insert Primary College into core.colleges
  -- ----------------------------------------------------------------------------
  INSERT INTO core.colleges (
    id, tenant_id, code, name, legal_name, status, provisioning_status, email, profile, created_at, updated_at, row_version
  ) VALUES (
    v_college_id,
    v_tenant_id,
    v_college_code,
    v_college_name,
    v_college_name,
    'ACTIVE',
    'ACTIVE',
    v_admin_email,
    jsonb_build_object('tag', 'APEX_SCI_LIVE_PROVISIONING'),
    now(),
    now(),
    1
  ) ON CONFLICT (id) DO UPDATE SET
    name = EXCLUDED.name,
    status = 'ACTIVE',
    provisioning_status = 'ACTIVE',
    updated_at = now();

  -- ----------------------------------------------------------------------------
  -- STEP 3: Initialize & Complete Saga Job in plat.tenant_provisioning
  -- ----------------------------------------------------------------------------
  INSERT INTO plat.tenant_provisioning (
    id, tenant_id, college_id, current_step, attempt, status, idempotency_key, created_at, updated_at, row_version
  ) VALUES (
    v_job_id,
    v_tenant_id,
    v_college_id,
    'COMPLETED',
    1,
    'COMPLETED',
    v_idempotency_key,
    now(),
    now(),
    1
  ) ON CONFLICT (id) DO UPDATE SET
    status = 'COMPLETED',
    current_step = 'COMPLETED',
    updated_at = now();

  -- ----------------------------------------------------------------------------
  -- STEP 4: Seed System Role Templates for Tenant
  -- ----------------------------------------------------------------------------
  PERFORM iam.seed_system_roles(v_tenant_id);

  -- ----------------------------------------------------------------------------
  -- STEP 5: Provision Administrator Identity (core.persons + iam.user_profiles)
  -- ----------------------------------------------------------------------------
  INSERT INTO core.persons (
    id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at
  ) VALUES (
    v_admin_id,
    v_tenant_id,
    'Robert',
    'Vance',
    v_admin_name,
    v_admin_email,
    'ACTIVE',
    now(),
    now()
  ) ON CONFLICT (id) DO UPDATE SET
    display_name = EXCLUDED.display_name,
    primary_email = EXCLUDED.primary_email,
    status = 'ACTIVE',
    updated_at = now();

  INSERT INTO iam.user_profiles (
    id, tenant_id, person_id, college_id, username, email, full_name, status, preferences, created_at, updated_at, row_version
  ) VALUES (
    v_admin_id,
    v_tenant_id,
    v_admin_id,
    v_college_id,
    'robert.vance@apex.edu',
    v_admin_email,
    v_admin_name,
    'ACTIVE',
    jsonb_build_object('theme', 'system', 'tag', 'APEX_SCI_LIVE_PROVISIONING'),
    now(),
    now(),
    1
  ) ON CONFLICT (id) DO UPDATE SET
    full_name = EXCLUDED.full_name,
    status = 'ACTIVE',
    updated_at = now();

  -- ----------------------------------------------------------------------------
  -- STEP 6: Assign COLLEGE_ADMIN Role in iam.role_assignments
  -- ----------------------------------------------------------------------------
  SELECT id INTO v_college_admin_role_id
  FROM iam.roles
  WHERE tenant_id = v_tenant_id AND code = 'COLLEGE_ADMIN' AND deleted_at IS NULL
  LIMIT 1;

  IF v_college_admin_role_id IS NULL THEN
    RAISE EXCEPTION 'Postcondition Failed: COLLEGE_ADMIN role was not seeded for tenant %', v_tenant_id;
  END IF;

  INSERT INTO iam.role_assignments (
    id, tenant_id, role_id, user_id, college_id, created_at, updated_at, row_version
  ) VALUES (
    gen_random_uuid(),
    v_tenant_id,
    v_college_admin_role_id,
    v_admin_id,
    v_college_id,
    now(),
    now(),
    1
  ) ON CONFLICT DO NOTHING;

  -- ----------------------------------------------------------------------------
  -- STEP 7: Transactional Outbox Event in sys.outbox_events
  -- ----------------------------------------------------------------------------
  v_payload := jsonb_build_object(
    'tenantId', v_tenant_id,
    'tenantCode', v_tenant_code,
    'tenantName', v_tenant_name,
    'collegeId', v_college_id,
    'collegeCode', v_college_code,
    'collegeName', v_college_name,
    'adminUserId', v_admin_id,
    'adminEmail', v_admin_email,
    'status', 'ACTIVE',
    'tag', 'APEX_SCI_LIVE_PROVISIONING'
  );

  INSERT INTO sys.outbox_events (
    id, tenant_id, service, aggregate_type, aggregate_id, event_type, payload, status, created_at, updated_at
  ) VALUES (
    v_outbox_id,
    v_tenant_id,
    'institute-admin-service',
    'TENANT',
    v_tenant_id,
    'tenant.onboarded.v1',
    v_payload,
    'PENDING',
    now(),
    now()
  );

  -- ----------------------------------------------------------------------------
  -- STEP 8: Immutable Platform Audit Log in audit.events
  -- ----------------------------------------------------------------------------
  INSERT INTO audit.events (
    tenant_id, college_id, event_time, action, principal_type, resource_type, resource_id, status, severity, detail
  ) VALUES (
    v_tenant_id,
    v_college_id,
    now(),
    'TENANT_ONBOARDED',
    'PLATFORM_ADMIN',
    'TENANT',
    v_tenant_id,
    'SUCCESS',
    'INFO',
    v_payload
  );

  RAISE NOTICE 'SUCCESS: Institutional tenant APEX_SCI provisioned with TenantId=%, CollegeId=%, AdminId=%', v_tenant_id, v_college_id, v_admin_id;
END $$;

COMMIT;
