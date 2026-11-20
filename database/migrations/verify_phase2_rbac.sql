-- Verification SQL: verify_phase2_rbac.sql
-- Description: Automated assertions verifying Phase 2 RBAC gates
-- Target DB: azulbaxszsqxymqmlwfe

DO $$
DECLARE
  v_r04_m12_count int;
  v_r04_removed_mw_count int;
  v_r03_domain_keys_count int;
  v_r03_doc_write_count int;
  v_r31_doc_keys_count int;
  v_r05_acd_keys_count int;
  v_r07_acd_keys_count int;
  v_direct_student_grants int;
  v_principal_persona_count int;
  v_dean_persona_count int;
  v_users_without_roles int;
BEGIN
  -- 1. R04 holds none of the D-RBAC-M12 keys
  SELECT count(*) INTO v_r04_m12_count
  FROM iam.role_template_permissions
  WHERE role_code = 'COLLEGE_ADMIN'
    AND permission_code IN (
      'fin04.view', 'fin04.view.all',
      'fin06.view', 'fin06.view.all',
      'hrm04.view', 'hrm04.view.all', 'hrm04.write.own',
      'hrm05.view', 'hrm05.view.all'
    )
    AND deleted_at IS NULL;

  IF v_r04_m12_count > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: R04 still holds D-RBAC-M12 keys (count: %)', v_r04_m12_count;
  END IF;

  -- 2. R04 holds none of the removed domain write/manage or broad write keys
  SELECT count(*) INTO v_r04_removed_mw_count
  FROM iam.role_template_permissions
  WHERE role_code = 'COLLEGE_ADMIN'
    AND permission_code IN (
      'acd01.manage', 'acd01.write', 'acd02.manage', 'acd02.write',
      'acd05.manage', 'acd05.write', 'acd06.manage', 'acd06.write',
      'acd07.manage', 'acd07.write',
      'cam01.manage', 'cam01.write', 'cam02.manage', 'cam02.write',
      'cam03.manage', 'cam03.write', 'cam04.manage', 'cam04.write',
      'cam05.manage', 'cam05.write', 'cam06.manage', 'cam06.write',
      'cam07.manage', 'cam07.write',
      'fin03.write', 'fin05.write', 'fin09.write',
      'hrm01.manage', 'hrm01.write',
      'core.pii.write', 'core.write', 'wf.write'
    )
    AND deleted_at IS NULL;

  IF v_r04_removed_mw_count > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: R04 still holds removed domain write/manage keys (count: %)', v_r04_removed_mw_count;
  END IF;

  -- 3. R03 holds none of the non-IADM domain keys
  SELECT count(*) INTO v_r03_domain_keys_count
  FROM iam.role_template_permissions
  WHERE role_code = 'INSTITUTION_ADMIN'
    AND (
      permission_code LIKE 'acd%' OR
      permission_code LIKE 'lms%' OR
      permission_code LIKE 'exm%' OR
      permission_code LIKE 'fin%' OR
      permission_code LIKE 'hrm%' OR
      permission_code LIKE 'cam%'
    )
    AND deleted_at IS NULL;

  IF v_r03_domain_keys_count > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: R03 still holds non-IADM domain keys (count: %)', v_r03_domain_keys_count;
  END IF;

  -- 4. R03 does not hold doc.admin, doc.manage, doc.write (moved to R31)
  SELECT count(*) INTO v_r03_doc_write_count
  FROM iam.role_template_permissions
  WHERE role_code = 'INSTITUTION_ADMIN'
    AND permission_code IN ('doc.admin', 'doc.manage', 'doc.write')
    AND deleted_at IS NULL;

  IF v_r03_doc_write_count > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: R03 still holds doc write/admin keys (count: %)', v_r03_doc_write_count;
  END IF;

  -- 5. R31 Registrar holds doc.admin, doc.manage, doc.write, doc.view
  SELECT count(*) INTO v_r31_doc_keys_count
  FROM iam.role_template_permissions
  WHERE role_code = 'REGISTRAR'
    AND permission_code IN ('doc.admin', 'doc.manage', 'doc.write', 'doc.view')
    AND deleted_at IS NULL;

  IF v_r31_doc_keys_count < 4 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: R31 Registrar missing doc keys (count: % of 4)', v_r31_doc_keys_count;
  END IF;

  -- 6. R05 Principal holds ACD/LMS keys
  SELECT count(*) INTO v_r05_acd_keys_count
  FROM iam.role_template_permissions
  WHERE role_code = 'PRINCIPAL'
    AND (permission_code LIKE 'acd%' OR permission_code LIKE 'lms%')
    AND deleted_at IS NULL;

  IF v_r05_acd_keys_count < 50 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: R05 Principal missing ACD/LMS domain admin keys (count: %)', v_r05_acd_keys_count;
  END IF;

  -- 7. R07 Dean holds ACD/LMS keys
  SELECT count(*) INTO v_r07_acd_keys_count
  FROM iam.role_template_permissions
  WHERE role_code = 'DEAN'
    AND (permission_code LIKE 'acd%' OR permission_code LIKE 'lms%')
    AND deleted_at IS NULL;

  IF v_r07_acd_keys_count < 50 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: R07 Dean missing ACD/LMS domain admin keys (count: %)', v_r07_acd_keys_count;
  END IF;

  -- 8. Direct Admin -> Student / Parent grant rules removed
  SELECT count(*) INTO v_direct_student_grants
  FROM iam.role_grant_rules
  WHERE (grantor_role = 'COLLEGE_ADMIN' AND grantable_role IN ('STUDENT', 'PARENT'))
     OR (grantor_role = 'INSTITUTION_ADMIN' AND grantable_role IN ('STUDENT', 'PARENT'))
    AND deleted_at IS NULL;

  IF v_direct_student_grants > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Direct Admin -> Student/Parent grant rules still present (count: %)', v_direct_student_grants;
  END IF;

  -- 9. DEV test personas exist and have active role assignments
  SELECT count(*) INTO v_principal_persona_count
  FROM iam.role_assignments ra
  JOIN iam.roles r ON ra.role_id = r.id
  WHERE ra.user_id = 'c05a0000-0000-4000-8000-000000000005'::uuid
    AND r.code = 'PRINCIPAL'
    AND ra.deleted_at IS NULL;

  SELECT count(*) INTO v_dean_persona_count
  FROM iam.role_assignments ra
  JOIN iam.roles r ON ra.role_id = r.id
  WHERE ra.user_id = 'c07a0000-0000-4000-8000-000000000007'::uuid
    AND r.code = 'DEAN'
    AND ra.deleted_at IS NULL;

  IF v_principal_persona_count = 0 OR v_dean_persona_count = 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: DEV test personas missing role assignments (Principal: %, Dean: %)',
      v_principal_persona_count, v_dean_persona_count;
  END IF;

  -- 10. No user has zero active roles
  SELECT count(*) INTO v_users_without_roles
  FROM iam.user_profiles up
  WHERE up.deleted_at IS NULL
    AND NOT EXISTS (
      SELECT 1 FROM iam.role_assignments ra
      WHERE ra.user_id = up.id AND ra.deleted_at IS NULL
    );

  IF v_users_without_roles > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Users exist without active roles (count: %)', v_users_without_roles;
  END IF;

  RAISE NOTICE 'SUCCESS: ALL PHASE 2 RBAC VERIFICATIONS PASSED';
END $$;
