-- Rollback: 20261120000100_rollback__rbac_phase1_database_foundation.sql
-- Description: Rollback for Phase 1 RBAC Database Foundation (DEV only)
-- Target DB: azulbaxszsqxymqmlwfe
-- Author: CampX Platform Engineering / Owner Approval Recorded Niranjan V.G. 2026-11-20

BEGIN;

-- 1. Restore swapped catalogue IDs
UPDATE iam.role_templates
SET catalogue_id = 'R33', updated_at = now()
WHERE code = 'APPLICANT';

UPDATE iam.role_templates
SET catalogue_id = 'R32', updated_at = now()
WHERE code = 'ADMISSIONS_OFFICER';

-- 2. Restore TENANT_ADMIN role template
INSERT INTO iam.role_templates (code, name, catalogue_id, kind, scope_note)
VALUES ('TENANT_ADMIN', 'Tenant Administrator', 'hierarchy', 'staff', 'Deprecated root tenant administrator')
ON CONFLICT (code) DO UPDATE
SET deleted_at = NULL;

-- 3. Restore TENANT_ADMIN template permissions (*)
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES ('TENANT_ADMIN', '*')
ON CONFLICT DO NOTHING;

-- 4. Restore AUDITOR write permissions in template
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  ('AUDITOR', 'audit.admin'),
  ('AUDITOR', 'audit.write'),
  ('AUDITOR', 'wf.write'),
  ('AUDITOR', 'core.comment'),
  ('AUDITOR', 'hrm02.write.own'),
  ('AUDITOR', 'hrm03.write.own'),
  ('AUDITOR', 'hrm04.write.own'),
  ('AUDITOR', 'hrm07.write.own'),
  ('AUDITOR', 'hrm09.write.own')
ON CONFLICT DO NOTHING;

-- 5. Restore COLLEGE_ADMIN grant rules
INSERT INTO iam.role_grant_rules (grantor_role, grantable_role)
VALUES
  ('COLLEGE_ADMIN', 'COLLEGE_ADMIN'),
  ('COLLEGE_ADMIN', 'PRINCIPAL'),
  ('COLLEGE_ADMIN', 'VICE_PRINCIPAL'),
  ('COLLEGE_ADMIN', 'DEAN')
ON CONFLICT DO NOTHING;

-- 6. Remove INSTITUTION_ADMIN grant rules added in Phase 1
DELETE FROM iam.role_grant_rules
WHERE grantor_role = 'INSTITUTION_ADMIN'
  AND grantable_role IN (
    'REGISTRAR', 'ADMISSIONS_OFFICER', 'DEPARTMENT_STAFF',
    'ACADEMIC_RECORDS_OFFICER', 'DOCUMENT_VERIFIER', 'EXTERNAL_VERIFIER',
    'IAM_OPERATOR', 'FACULTY_ADVISOR'
  );

-- 7. Remove R34-R39 templates and roles
DELETE FROM iam.role_templates
WHERE code IN (
  'DEPARTMENT_STAFF', 'ACADEMIC_RECORDS_OFFICER', 'DOCUMENT_VERIFIER',
  'EXTERNAL_VERIFIER', 'IAM_OPERATOR', 'FACULTY_ADVISOR'
);

DELETE FROM iam.roles
WHERE code IN (
  'DEPARTMENT_STAFF', 'ACADEMIC_RECORDS_OFFICER', 'DOCUMENT_VERIFIER',
  'EXTERNAL_VERIFIER', 'IAM_OPERATOR', 'FACULTY_ADVISOR'
);

-- 8. Resync system roles across all tenants
DO $$
DECLARE
  v_tenant record;
BEGIN
  FOR v_tenant IN (SELECT DISTINCT tenant_id FROM iam.roles WHERE deleted_at IS NULL) LOOP
    PERFORM iam.seed_system_roles(v_tenant.tenant_id);
  END LOOP;
END $$;

COMMIT;
