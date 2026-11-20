-- Migration: 20261120000100_rbac_phase1_database_foundation.sql
-- Description: Phase 1 RBAC Database Foundation (DEV only)
-- Target DB: azulbaxszsqxymqmlwfe
-- Author: CampX Platform Engineering / Owner Approval Recorded Niranjan V.G. 2026-11-20
-- Governing: CampXSync Enterprise RBAC Matrix v3.0 §22.1, §22.2

BEGIN;

-- =========================================================================
-- 1.1 Fix swapped catalogue IDs in iam.role_templates
-- APPLICANT = R32, ADMISSIONS_OFFICER = R33
-- =========================================================================
UPDATE iam.role_templates
SET catalogue_id = 'R32', updated_at = now()
WHERE code = 'APPLICANT';

UPDATE iam.role_templates
SET catalogue_id = 'R33', updated_at = now()
WHERE code = 'ADMISSIONS_OFFICER';

-- =========================================================================
-- 1.2 Create role templates for R34-R39 with NO permissions yet
-- Copy into every tenant (49 tenants in DEV)
-- =========================================================================
INSERT INTO iam.role_templates (code, name, catalogue_id, kind, scope_note)
VALUES
  ('DEPARTMENT_STAFF', 'Department Staff', 'R34', 'staff', 'Departmental operational staff for course offerings, sections, and requests'),
  ('ACADEMIC_RECORDS_OFFICER', 'Academic Records Officer', 'R35', 'staff', 'Campus academic history and permanent records maintenance (STM-08)'),
  ('DOCUMENT_VERIFIER', 'Document Verifier', 'R36', 'staff', 'Assigned document review and verification (STM-06)'),
  ('EXTERNAL_VERIFIER', 'External Verification Agency', 'R37', 'staff', 'External verification agency for assigned student documents only (STM-06)'),
  ('IAM_OPERATOR', 'Provisioning / IAM Operator', 'R38', 'staff', 'Account linking and student/guardian provisioning operator (STM-07)'),
  ('FACULTY_ADVISOR', 'Faculty Advisor', 'R39', 'faculty', 'Faculty member assigned to student advising and mentorship (STM-01, STM-03)')
ON CONFLICT (code) WHERE deleted_at IS NULL DO UPDATE
SET name = EXCLUDED.name,
    catalogue_id = EXCLUDED.catalogue_id,
    kind = EXCLUDED.kind,
    scope_note = EXCLUDED.scope_note,
    deleted_at = NULL,
    updated_at = now();

-- Copy new templates into iam.roles for all tenants
DO $$
DECLARE
  v_tenant record;
BEGIN
  FOR v_tenant IN (SELECT DISTINCT tenant_id FROM iam.roles WHERE deleted_at IS NULL) LOOP
    PERFORM iam.seed_system_roles(v_tenant.tenant_id);
  END LOOP;
END $$;

-- =========================================================================
-- 1.3 Grant rules (iam.role_grant_rules)
-- a) Delete COLLEGE_ADMIN -> COLLEGE_ADMIN, PRINCIPAL, VICE_PRINCIPAL, DEAN
-- b) Add INSTITUTION_ADMIN -> REGISTRAR, ADMISSIONS_OFFICER and R34-R39
-- c) Leave STUDENT and PARENT grant rows intact for Phase 2
-- =========================================================================
DELETE FROM iam.role_grant_rules
WHERE grantor_role = 'COLLEGE_ADMIN'
  AND grantable_role IN ('COLLEGE_ADMIN', 'PRINCIPAL', 'VICE_PRINCIPAL', 'DEAN');

INSERT INTO iam.role_grant_rules (grantor_role, grantable_role)
VALUES
  ('INSTITUTION_ADMIN', 'REGISTRAR'),
  ('INSTITUTION_ADMIN', 'ADMISSIONS_OFFICER'),
  ('INSTITUTION_ADMIN', 'DEPARTMENT_STAFF'),
  ('INSTITUTION_ADMIN', 'ACADEMIC_RECORDS_OFFICER'),
  ('INSTITUTION_ADMIN', 'DOCUMENT_VERIFIER'),
  ('INSTITUTION_ADMIN', 'EXTERNAL_VERIFIER'),
  ('INSTITUTION_ADMIN', 'IAM_OPERATOR'),
  ('INSTITUTION_ADMIN', 'FACULTY_ADVISOR')
ON CONFLICT (grantor_role, grantable_role) WHERE deleted_at IS NULL DO NOTHING;

-- =========================================================================
-- 1.4 AUDITOR: remove write/manage/admin/comment keys
-- Remove audit.admin, audit.write, wf.write, core.comment and the five HR "write own" keys
-- =========================================================================
DELETE FROM iam.role_template_permissions
WHERE role_code = 'AUDITOR'
  AND permission_code IN (
    'audit.admin', 'audit.write', 'wf.write', 'core.comment',
    'hrm02.write.own', 'hrm03.write.own', 'hrm04.write.own', 'hrm07.write.own', 'hrm09.write.own'
  );

DELETE FROM iam.role_permissions rp
USING iam.roles r, iam.permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.code = 'AUDITOR'
  AND p.code IN (
    'audit.admin', 'audit.write', 'wf.write', 'core.comment',
    'hrm02.write.own', 'hrm03.write.own', 'hrm04.write.own', 'hrm07.write.own', 'hrm09.write.own'
  );

-- =========================================================================
-- 1.5 Tenant Admin collapse (matrix IADM-D7)
-- b) Give the single DEV Tenant Admin holder an INSTITUTION_ADMIN assignment
-- c) Remove wildcard rows, assignments, roles, template, grant rules
-- =========================================================================
-- Assign INSTITUTION_ADMIN to any user holding TENANT_ADMIN in that tenant if not already assigned
INSERT INTO iam.role_assignments (tenant_id, role_id, user_id, college_id)
SELECT ra.tenant_id, r_inst.id, ra.user_id, ra.college_id
FROM iam.role_assignments ra
JOIN iam.roles r_ta ON ra.role_id = r_ta.id AND r_ta.code = 'TENANT_ADMIN'
JOIN iam.roles r_inst ON r_inst.tenant_id = ra.tenant_id AND r_inst.code = 'INSTITUTION_ADMIN' AND r_inst.deleted_at IS NULL
WHERE ra.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_assignments existing
    WHERE existing.tenant_id = ra.tenant_id
      AND existing.role_id = r_inst.id
      AND existing.user_id = ra.user_id
      AND existing.deleted_at IS NULL
  );

-- Remove TENANT_ADMIN role assignments
DELETE FROM iam.role_assignments ra
USING iam.roles r
WHERE ra.role_id = r.id AND r.code = 'TENANT_ADMIN';

-- Remove wildcard permission from role_permissions
DELETE FROM iam.role_permissions rp
USING iam.roles r, iam.permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND (p.code = '*' OR r.code = 'TENANT_ADMIN');

-- Remove wildcard from role_template_permissions
DELETE FROM iam.role_template_permissions
WHERE permission_code = '*' OR role_code = 'TENANT_ADMIN';

-- Remove TENANT_ADMIN from role_grant_rules
DELETE FROM iam.role_grant_rules
WHERE grantor_role = 'TENANT_ADMIN' OR grantable_role = 'TENANT_ADMIN';

-- Remove TENANT_ADMIN from role_hierarchy if present
DELETE FROM iam.role_hierarchy
WHERE parent_role = 'TENANT_ADMIN' OR child_role = 'TENANT_ADMIN';

-- Remove TENANT_ADMIN from iam.roles across all tenants
DELETE FROM iam.roles
WHERE code = 'TENANT_ADMIN';

-- Remove TENANT_ADMIN from iam.role_templates
DELETE FROM iam.role_templates
WHERE code = 'TENANT_ADMIN';

COMMIT;
