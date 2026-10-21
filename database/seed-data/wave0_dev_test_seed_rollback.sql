-- ==============================================================================
-- CAMPXSYNC WAVE 0 TEST SEED ROLLBACK SCRIPT (APPROVAL-GATED — DO NOT EXECUTE WITHOUT APPROVAL)
-- ==============================================================================
-- Target Environment: Supabase Dev (Project Ref: azulbaxszsqxymqmlwfe)
-- Target Schemas: core, iam
-- Required Ritual: Explicit owner command "GO ROLLBACK-WAVE0-DEV"
-- Strictly Scoped: Removes ONLY the exact records created by wave0_dev_test_seed.sql.
-- Safety Policy:
--   1. Preconditions verify exact entity counts, lineage, and absence of foreign dependencies.
--   2. Aborts cleanup immediately if unexpected dependencies or additional records exist.
--   3. Deletes in strict reverse FK dependency order within an atomic transaction.
-- ==============================================================================

BEGIN;

DO $$
DECLARE
  v_tenant_id        uuid := 'a0000000-0000-0000-0000-000000000001'::uuid;
  v_tenant_code      text := 'DEV_TEST_TENANT';
  v_college_id       uuid := 'a0000000-0000-0000-0000-000000000002'::uuid;
  v_college_code     text := 'DEV_TEST_COLLEGE';
  v_user_id          uuid := 'ba77b8f7-ab32-46bd-85d2-5aade3526880'::uuid;
  v_user_email       text := 'dev.admin@campxsync.test';
  v_assign_id        uuid := 'a0000000-0000-0000-0000-000000000003'::uuid;
  
  v_assign_count     integer;
  v_profile_count    integer;
  v_college_count    integer;
  v_system_role_cnt  integer;
  v_custom_role_cnt  integer;
  v_perm_count       integer;
  v_dept_count       integer;
  v_course_count     integer;
  v_student_count    integer;
BEGIN
  -- ----------------------------------------------------------------------------
  -- PRECONDITION 1: Verify Exact Target Tenant Lineage
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (
    SELECT 1 FROM core.tenants WHERE id = v_tenant_id AND code = v_tenant_code
  ) THEN
    RAISE EXCEPTION 'Rollback Aborted: Test tenant % ("%") not found.', v_tenant_id, v_tenant_code;
  END IF;

  -- ----------------------------------------------------------------------------
  -- PRECONDITION 2: Enforce Strict Entity Counts (Abort on Unexpected Extra Data)
  -- ----------------------------------------------------------------------------
  -- Check Role Assignments: exactly 1 test assignment must exist for tenant
  SELECT count(*) INTO v_assign_count FROM iam.role_assignments WHERE tenant_id = v_tenant_id;
  IF v_assign_count <> 1 THEN
    RAISE EXCEPTION 'Rollback Aborted: Expected exactly 1 role assignment for test tenant, found %. Aborting to prevent data loss.', v_assign_count;
  END IF;

  -- Check User Profiles: exactly 1 test profile must exist for tenant
  SELECT count(*) INTO v_profile_count FROM iam.user_profiles WHERE tenant_id = v_tenant_id;
  IF v_profile_count <> 1 THEN
    RAISE EXCEPTION 'Rollback Aborted: Expected exactly 1 user profile for test tenant, found %. Aborting to prevent data loss.', v_profile_count;
  END IF;

  -- Check Colleges: exactly 1 test college must exist for tenant
  SELECT count(*) INTO v_college_count FROM core.colleges WHERE tenant_id = v_tenant_id;
  IF v_college_count <> 1 THEN
    RAISE EXCEPTION 'Rollback Aborted: Expected exactly 1 college for test tenant, found %. Aborting to prevent data loss.', v_college_count;
  END IF;

  -- Check Custom Roles: must be 0 (no non-system roles created)
  SELECT count(*) INTO v_custom_role_cnt FROM iam.roles WHERE tenant_id = v_tenant_id AND is_system = false;
  IF v_custom_role_cnt > 0 THEN
    RAISE EXCEPTION 'Rollback Aborted: Found % custom (non-system) roles in test tenant. Aborting.', v_custom_role_cnt;
  END IF;

  -- Check Trigger-Generated System Roles: must match exactly 30
  SELECT count(*) INTO v_system_role_cnt FROM iam.roles WHERE tenant_id = v_tenant_id AND is_system = true;
  IF v_system_role_cnt <> 30 THEN
    RAISE EXCEPTION 'Rollback Aborted: Expected exactly 30 trigger-generated system roles, found %.', v_system_role_cnt;
  END IF;

  -- Check Trigger-Generated Role Permissions: must match exactly 2,138
  SELECT count(*) INTO v_perm_count FROM iam.role_permissions WHERE tenant_id = v_tenant_id;
  IF v_perm_count <> 2138 THEN
    RAISE EXCEPTION 'Rollback Aborted: Expected exactly 2,138 trigger-generated role permissions, found %.', v_perm_count;
  END IF;

  -- ----------------------------------------------------------------------------
  -- PRECONDITION 3: Enforce Absence of Unexpected Downstream Dependencies
  -- ----------------------------------------------------------------------------
  SELECT count(*) INTO v_dept_count FROM core.departments WHERE tenant_id = v_tenant_id;
  IF v_dept_count > 0 THEN
    RAISE EXCEPTION 'Rollback Aborted: Found % unexpected departments under test tenant.', v_dept_count;
  END IF;

  SELECT count(*) INTO v_course_count FROM acd.courses WHERE tenant_id = v_tenant_id;
  IF v_course_count > 0 THEN
    RAISE EXCEPTION 'Rollback Aborted: Found % unexpected courses under test tenant.', v_course_count;
  END IF;

  SELECT count(*) INTO v_student_count FROM stu.students WHERE tenant_id = v_tenant_id;
  IF v_student_count > 0 THEN
    RAISE EXCEPTION 'Rollback Aborted: Found % unexpected students under test tenant.', v_student_count;
  END IF;

  -- ----------------------------------------------------------------------------
  -- REVERSE FK EXECUTION 1: Delete Specific Role Assignment
  -- ----------------------------------------------------------------------------
  DELETE FROM iam.role_assignments 
  WHERE id = v_assign_id 
    AND tenant_id = v_tenant_id 
    AND user_id = v_user_id;

  -- ----------------------------------------------------------------------------
  -- REVERSE FK EXECUTION 2: Delete Specific User Profile
  -- ----------------------------------------------------------------------------
  DELETE FROM iam.user_profiles 
  WHERE id = v_user_id 
    AND tenant_id = v_tenant_id 
    AND email = v_user_email;

  -- ----------------------------------------------------------------------------
  -- REVERSE FK EXECUTION 3: Delete Trigger-Generated Role Permissions for System Roles
  -- Explicitly joins system roles seeded from role templates (college_id IS NULL)
  -- ----------------------------------------------------------------------------
  DELETE FROM iam.role_permissions
  WHERE tenant_id = v_tenant_id
    AND role_id IN (
      SELECT r.id 
      FROM iam.roles r
      JOIN iam.role_templates t ON r.code = t.code
      WHERE r.tenant_id = v_tenant_id 
        AND r.is_system = true 
        AND r.college_id IS NULL
    );

  -- ----------------------------------------------------------------------------
  -- REVERSE FK EXECUTION 4: Delete Trigger-Generated System Roles
  -- Explicitly matches codes from iam.role_templates with is_system = true
  -- ----------------------------------------------------------------------------
  DELETE FROM iam.roles
  WHERE tenant_id = v_tenant_id
    AND is_system = true
    AND college_id IS NULL
    AND code IN (SELECT code FROM iam.role_templates WHERE deleted_at IS NULL);

  -- ----------------------------------------------------------------------------
  -- REVERSE FK EXECUTION 5: Delete Specific Test College
  -- ----------------------------------------------------------------------------
  DELETE FROM core.colleges 
  WHERE id = v_college_id 
    AND tenant_id = v_tenant_id 
    AND code = v_college_code;

  -- ----------------------------------------------------------------------------
  -- REVERSE FK EXECUTION 6: Delete Specific Test Tenant
  -- ----------------------------------------------------------------------------
  DELETE FROM core.tenants 
  WHERE id = v_tenant_id 
    AND code = v_tenant_code;

  -- ----------------------------------------------------------------------------
  -- POSTCONDITION: Assert Clean Baseline on Test Tenant
  -- ----------------------------------------------------------------------------
  IF EXISTS (SELECT 1 FROM core.tenants WHERE id = v_tenant_id) THEN
    RAISE EXCEPTION 'Rollback Postcondition Failed: Test tenant record remains.';
  END IF;

  RAISE NOTICE 'SUCCESS: Wave 0 test seed cleanly and safely rolled back.';
END $$;

COMMIT;
