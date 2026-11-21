-- Verification SQL: verify_phase4_rbac.sql
-- Description: Automated assertion suite for Phase 4 verification and patch against Dev Supabase (azulbaxszsqxymqmlwfe)

DO $$
DECLARE
  v_count integer;
  v_bool boolean;
  v_role_id uuid;
BEGIN
  -- Assertion 1: INSTITUTION_ADMIN -> STUDENT in iam.role_grant_rules
  SELECT count(*) INTO v_count FROM iam.role_grant_rules
  WHERE grantor_role = 'INSTITUTION_ADMIN' AND grantable_role = 'STUDENT';
  ASSERT v_count = 1, 'Assertion 1 FAILED: INSTITUTION_ADMIN -> STUDENT grant rule missing';

  -- Assertion 2: INSTITUTION_ADMIN -> PARENT in iam.role_grant_rules
  SELECT count(*) INTO v_count FROM iam.role_grant_rules
  WHERE grantor_role = 'INSTITUTION_ADMIN' AND grantable_role = 'PARENT';
  ASSERT v_count = 1, 'Assertion 2 FAILED: INSTITUTION_ADMIN -> PARENT grant rule missing';

  -- Assertion 3: IAM_OPERATOR -> STUDENT in iam.role_grant_rules
  SELECT count(*) INTO v_count FROM iam.role_grant_rules
  WHERE grantor_role = 'IAM_OPERATOR' AND grantable_role = 'STUDENT';
  ASSERT v_count = 1, 'Assertion 3 FAILED: IAM_OPERATOR -> STUDENT grant rule missing';

  -- Assertion 4: IAM_OPERATOR -> PARENT in iam.role_grant_rules
  SELECT count(*) INTO v_count FROM iam.role_grant_rules
  WHERE grantor_role = 'IAM_OPERATOR' AND grantable_role = 'PARENT';
  ASSERT v_count = 1, 'Assertion 4 FAILED: IAM_OPERATOR -> PARENT grant rule missing';

  -- Assertion 5: COLLEGE_ADMIN cannot grant STUDENT or PARENT
  SELECT count(*) INTO v_count FROM iam.role_grant_rules
  WHERE grantor_role = 'COLLEGE_ADMIN' AND grantable_role IN ('STUDENT', 'PARENT');
  ASSERT v_count = 0, 'Assertion 5 FAILED: COLLEGE_ADMIN grant rule for STUDENT/PARENT must remain deleted';

  -- Assertion 6: ba77b8f7 removed from plat.platform_admins
  SELECT count(*) INTO v_count FROM plat.platform_admins
  WHERE user_id = 'ba77b8f7-ab32-46bd-85d2-5aade3526880' AND deleted_at IS NULL;
  ASSERT v_count = 0, 'Assertion 6 FAILED: ba77b8f7 must not exist in plat.platform_admins';

  -- Assertion 7: Dedicated R03 persona exists
  SELECT count(*) INTO v_count FROM iam.user_profiles
  WHERE id = 'c03a0000-0000-4000-8000-000000000003' AND status = 'ACTIVE';
  ASSERT v_count = 1, 'Assertion 7 FAILED: Dedicated R03 user profile missing';

  -- Assertion 8: Dedicated R03 persona has active INSTITUTION_ADMIN assignment
  SELECT count(*) INTO v_count FROM iam.role_assignments ra
  JOIN iam.roles r ON r.id = ra.role_id
  WHERE ra.user_id = 'c03a0000-0000-4000-8000-000000000003'::uuid
    AND r.code = 'INSTITUTION_ADMIN' AND ra.deleted_at IS NULL;
  ASSERT v_count = 1, 'Assertion 8 FAILED: Dedicated R03 role assignment missing';

  -- Assertion 9: Auditor catalogue ID is R28
  SELECT count(*) INTO v_count FROM iam.role_templates
  WHERE code = 'AUDITOR' AND catalogue_id = 'R28';
  ASSERT v_count = 1, 'Assertion 9 FAILED: AUDITOR catalogue_id in iam.role_templates is not R28';

  RAISE NOTICE 'ALL PHASE 4 DATABASE ASSERTIONS PASSED (CONFIRMED).';
END $$;
