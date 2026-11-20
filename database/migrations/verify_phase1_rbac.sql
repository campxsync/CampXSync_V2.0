-- Verification SQL: verify_phase1_rbac.sql
-- Description: Automated assertions verifying Phase 1 RBAC Database Foundation gates
-- Target DB: azulbaxszsqxymqmlwfe

DO $$
DECLARE
  v_wildcard_count int;
  v_tenant_admin_templates int;
  v_tenant_admin_roles int;
  v_tenant_admin_grants int;
  v_distinct_roles_per_tenant int;
  v_min_roles int;
  v_max_roles int;
  v_ca_illegal_grants int;
  v_ia_expected_grants int;
  v_auditor_write_perms int;
  v_applicant_cat text;
  v_admissions_cat text;
  v_users_without_roles int;
BEGIN
  -- 1. Wildcard * held by zero roles
  SELECT count(*) INTO v_wildcard_count
  FROM iam.role_template_permissions
  WHERE permission_code = '*' AND deleted_at IS NULL;
  
  IF v_wildcard_count > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Wildcard * held in role_template_permissions (count: %)', v_wildcard_count;
  END IF;

  SELECT count(*) INTO v_wildcard_count
  FROM iam.role_permissions rp
  JOIN iam.permissions p ON rp.permission_id = p.id
  WHERE p.code = '*';

  IF v_wildcard_count > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Wildcard * held in iam.role_permissions (count: %)', v_wildcard_count;
  END IF;

  -- 2. TENANT_ADMIN absent
  SELECT count(*) INTO v_tenant_admin_templates FROM iam.role_templates WHERE code = 'TENANT_ADMIN' AND deleted_at IS NULL;
  SELECT count(*) INTO v_tenant_admin_roles FROM iam.roles WHERE code = 'TENANT_ADMIN' AND deleted_at IS NULL;
  SELECT count(*) INTO v_tenant_admin_grants FROM iam.role_grant_rules WHERE (grantor_role = 'TENANT_ADMIN' OR grantable_role = 'TENANT_ADMIN') AND deleted_at IS NULL;

  IF v_tenant_admin_templates > 0 OR v_tenant_admin_roles > 0 OR v_tenant_admin_grants > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: TENANT_ADMIN still present (templates: %, roles: %, grants: %)', 
      v_tenant_admin_templates, v_tenant_admin_roles, v_tenant_admin_grants;
  END IF;

  -- 3. All 49 tenants have identical role counts
  SELECT min(c), max(c) INTO v_min_roles, v_max_roles
  FROM (
    SELECT count(*) as c
    FROM iam.roles
    WHERE deleted_at IS NULL
    GROUP BY tenant_id
  ) s;

  IF v_min_roles <> v_max_roles OR v_min_roles < 38 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Tenant role counts not identical or incomplete (min: %, max: %)', v_min_roles, v_max_roles;
  END IF;

  -- 4. R04 cannot grant R04, R05, R06, R07
  SELECT count(*) INTO v_ca_illegal_grants
  FROM iam.role_grant_rules
  WHERE grantor_role = 'COLLEGE_ADMIN'
    AND grantable_role IN ('COLLEGE_ADMIN', 'PRINCIPAL', 'VICE_PRINCIPAL', 'DEAN')
    AND deleted_at IS NULL;

  IF v_ca_illegal_grants > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: COLLEGE_ADMIN has illegal grant rules (count: %)', v_ca_illegal_grants;
  END IF;

  -- 5. R03 can grant R33-R39 except APPLICANT
  SELECT count(*) INTO v_ia_expected_grants
  FROM iam.role_grant_rules
  WHERE grantor_role = 'INSTITUTION_ADMIN'
    AND grantable_role IN (
      'REGISTRAR', 'ADMISSIONS_OFFICER', 'DEPARTMENT_STAFF',
      'ACADEMIC_RECORDS_OFFICER', 'DOCUMENT_VERIFIER', 'EXTERNAL_VERIFIER',
      'IAM_OPERATOR', 'FACULTY_ADVISOR'
    )
    AND deleted_at IS NULL;

  IF v_ia_expected_grants < 8 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: INSTITUTION_ADMIN missing expected grant rules (count: % of 8)', v_ia_expected_grants;
  END IF;

  -- 6. AUDITOR has no write/manage/admin/comment keys
  SELECT count(*) INTO v_auditor_write_perms
  FROM iam.role_template_permissions
  WHERE role_code = 'AUDITOR'
    AND (
      permission_code LIKE '%.write%' OR
      permission_code LIKE '%.manage%' OR
      permission_code LIKE '%.admin%' OR
      permission_code LIKE '%.approve%' OR
      permission_code LIKE '%.comment%'
    )
    AND deleted_at IS NULL;

  IF v_auditor_write_perms > 0 THEN
    RAISE EXCEPTION 'ASSERTION FAILED: AUDITOR still holds write/admin permissions (count: %)', v_auditor_write_perms;
  END IF;

  -- 7. Swapped catalogue IDs fixed
  SELECT catalogue_id INTO v_applicant_cat FROM iam.role_templates WHERE code = 'APPLICANT';
  SELECT catalogue_id INTO v_admissions_cat FROM iam.role_templates WHERE code = 'ADMISSIONS_OFFICER';

  IF v_applicant_cat <> 'R32' OR v_admissions_cat <> 'R33' THEN
    RAISE EXCEPTION 'ASSERTION FAILED: Catalogue IDs not properly fixed (APPLICANT: %, ADMISSIONS_OFFICER: %)', v_applicant_cat, v_admissions_cat;
  END IF;

  -- 8. Every user still has at least one role
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

  RAISE NOTICE 'SUCCESS: ALL PHASE 1 RBAC VERIFICATIONS PASSED';
END $$;
