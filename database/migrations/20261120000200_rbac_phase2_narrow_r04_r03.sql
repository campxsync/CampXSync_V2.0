-- Migration: 20261120000200_rbac_phase2_narrow_r04_r03.sql
-- Description: Phase 2 Narrow R04 and R03 with Add-Before-Remove Coverage
-- Target DB: azulbaxszsqxymqmlwfe
-- Author: CampX Platform Engineering / Owner Approval Recorded Niranjan V.G. 2026-11-20
-- Governing: CampXSync Enterprise RBAC Matrix v3.0 §7, §8, §9, §10, §21, §22

BEGIN;

-- =========================================================================
-- 2.1 Coverage for New Owners (Add Before Remove)
-- =========================================================================

-- A. R31 Registrar: grant document keys
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  ('REGISTRAR', 'doc.admin'),
  ('REGISTRAR', 'doc.manage'),
  ('REGISTRAR', 'doc.write'),
  ('REGISTRAR', 'doc.view')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

-- B. R05 Principal & R07 Dean: Domain Admins for ACD & LMS (D-RBAC-M09)
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  ('PRINCIPAL', 'acd01.manage'),
  ('PRINCIPAL', 'acd01.write'),
  ('PRINCIPAL', 'acd01.review'),
  ('PRINCIPAL', 'acd01.view'),
  ('PRINCIPAL', 'acd01.view.all'),
  ('PRINCIPAL', 'acd02.manage'),
  ('PRINCIPAL', 'acd02.write'),
  ('PRINCIPAL', 'acd02.review'),
  ('PRINCIPAL', 'acd02.view'),
  ('PRINCIPAL', 'acd02.view.all'),
  ('PRINCIPAL', 'acd02.approve'),
  ('PRINCIPAL', 'acd03.manage'),
  ('PRINCIPAL', 'acd03.write'),
  ('PRINCIPAL', 'acd03.review'),
  ('PRINCIPAL', 'acd03.view'),
  ('PRINCIPAL', 'acd03.view.all'),
  ('PRINCIPAL', 'acd03.approve'),
  ('PRINCIPAL', 'acd04.manage'),
  ('PRINCIPAL', 'acd04.write'),
  ('PRINCIPAL', 'acd04.review'),
  ('PRINCIPAL', 'acd04.view'),
  ('PRINCIPAL', 'acd04.view.all'),
  ('PRINCIPAL', 'acd05.manage'),
  ('PRINCIPAL', 'acd05.write'),
  ('PRINCIPAL', 'acd05.review'),
  ('PRINCIPAL', 'acd05.view'),
  ('PRINCIPAL', 'acd05.view.all'),
  ('PRINCIPAL', 'acd05.approve'),
  ('PRINCIPAL', 'acd06.manage'),
  ('PRINCIPAL', 'acd06.write'),
  ('PRINCIPAL', 'acd06.review'),
  ('PRINCIPAL', 'acd06.view'),
  ('PRINCIPAL', 'acd06.view.all'),
  ('PRINCIPAL', 'acd07.manage'),
  ('PRINCIPAL', 'acd07.write'),
  ('PRINCIPAL', 'acd07.review'),
  ('PRINCIPAL', 'acd07.view'),
  ('PRINCIPAL', 'acd07.view.all'),
  ('PRINCIPAL', 'acd08.manage'),
  ('PRINCIPAL', 'acd08.write'),
  ('PRINCIPAL', 'acd08.review'),
  ('PRINCIPAL', 'acd08.view'),
  ('PRINCIPAL', 'acd08.view.all'),
  ('PRINCIPAL', 'acd09.manage'),
  ('PRINCIPAL', 'acd09.write'),
  ('PRINCIPAL', 'acd09.review'),
  ('PRINCIPAL', 'acd09.view'),
  ('PRINCIPAL', 'acd09.view.all'),
  ('PRINCIPAL', 'acd09.approve'),
  ('PRINCIPAL', 'acd10.manage'),
  ('PRINCIPAL', 'acd10.write'),
  ('PRINCIPAL', 'acd10.review'),
  ('PRINCIPAL', 'acd10.view'),
  ('PRINCIPAL', 'acd10.view.all'),
  ('PRINCIPAL', 'lms01.manage'),
  ('PRINCIPAL', 'lms01.write'),
  ('PRINCIPAL', 'lms01.review'),
  ('PRINCIPAL', 'lms01.view'),
  ('PRINCIPAL', 'lms01.view.all'),
  ('PRINCIPAL', 'lms02.manage'),
  ('PRINCIPAL', 'lms02.write'),
  ('PRINCIPAL', 'lms02.review'),
  ('PRINCIPAL', 'lms02.view'),
  ('PRINCIPAL', 'lms02.view.all'),
  ('PRINCIPAL', 'lms03.manage'),
  ('PRINCIPAL', 'lms03.write'),
  ('PRINCIPAL', 'lms03.review'),
  ('PRINCIPAL', 'lms03.view'),
  ('PRINCIPAL', 'lms03.view.all'),
  ('PRINCIPAL', 'lms04.manage'),
  ('PRINCIPAL', 'lms04.write'),
  ('PRINCIPAL', 'lms04.review'),
  ('PRINCIPAL', 'lms04.view'),
  ('PRINCIPAL', 'lms04.view.all'),
  ('PRINCIPAL', 'lms05.manage'),
  ('PRINCIPAL', 'lms05.write'),
  ('PRINCIPAL', 'lms05.review'),
  ('PRINCIPAL', 'lms05.view'),
  ('PRINCIPAL', 'lms05.view.all'),
  ('PRINCIPAL', 'lms06.manage'),
  ('PRINCIPAL', 'lms06.write'),
  ('PRINCIPAL', 'lms06.review'),
  ('PRINCIPAL', 'lms06.view'),
  ('PRINCIPAL', 'lms06.view.all'),
  ('PRINCIPAL', 'lms07.manage'),
  ('PRINCIPAL', 'lms07.write'),
  ('PRINCIPAL', 'lms07.review'),
  ('PRINCIPAL', 'lms07.view'),
  ('PRINCIPAL', 'lms07.view.all'),
  ('PRINCIPAL', 'lms08.manage'),
  ('PRINCIPAL', 'lms08.write'),
  ('PRINCIPAL', 'lms08.review'),
  ('PRINCIPAL', 'lms08.view'),
  ('PRINCIPAL', 'lms08.view.all'),
  ('PRINCIPAL', 'lms09.manage'),
  ('PRINCIPAL', 'lms09.write'),
  ('PRINCIPAL', 'lms09.review'),
  ('PRINCIPAL', 'lms09.view'),
  ('PRINCIPAL', 'lms09.view.all'),
  ('PRINCIPAL', 'lms09.approve'),
  ('PRINCIPAL', 'lms10.manage'),
  ('PRINCIPAL', 'lms10.write'),
  ('PRINCIPAL', 'lms10.review'),
  ('PRINCIPAL', 'lms10.view'),
  ('PRINCIPAL', 'lms10.view.all'),
  ('PRINCIPAL', 'lms10.approve'),
  ('DEAN', 'acd01.manage'),
  ('DEAN', 'acd01.write'),
  ('DEAN', 'acd01.review'),
  ('DEAN', 'acd01.view'),
  ('DEAN', 'acd01.view.all'),
  ('DEAN', 'acd02.manage'),
  ('DEAN', 'acd02.write'),
  ('DEAN', 'acd02.review'),
  ('DEAN', 'acd02.view'),
  ('DEAN', 'acd02.view.all'),
  ('DEAN', 'acd02.approve'),
  ('DEAN', 'acd03.manage'),
  ('DEAN', 'acd03.write'),
  ('DEAN', 'acd03.review'),
  ('DEAN', 'acd03.view'),
  ('DEAN', 'acd03.view.all'),
  ('DEAN', 'acd03.approve'),
  ('DEAN', 'acd04.manage'),
  ('DEAN', 'acd04.write'),
  ('DEAN', 'acd04.review'),
  ('DEAN', 'acd04.view'),
  ('DEAN', 'acd04.view.all'),
  ('DEAN', 'acd05.manage'),
  ('DEAN', 'acd05.write'),
  ('DEAN', 'acd05.review'),
  ('DEAN', 'acd05.view'),
  ('DEAN', 'acd05.view.all'),
  ('DEAN', 'acd05.approve'),
  ('DEAN', 'acd06.manage'),
  ('DEAN', 'acd06.write'),
  ('DEAN', 'acd06.review'),
  ('DEAN', 'acd06.view'),
  ('DEAN', 'acd06.view.all'),
  ('DEAN', 'acd07.manage'),
  ('DEAN', 'acd07.write'),
  ('DEAN', 'acd07.review'),
  ('DEAN', 'acd07.view'),
  ('DEAN', 'acd07.view.all'),
  ('DEAN', 'acd08.manage'),
  ('DEAN', 'acd08.write'),
  ('DEAN', 'acd08.review'),
  ('DEAN', 'acd08.view'),
  ('DEAN', 'acd08.view.all'),
  ('DEAN', 'acd09.manage'),
  ('DEAN', 'acd09.write'),
  ('DEAN', 'acd09.review'),
  ('DEAN', 'acd09.view'),
  ('DEAN', 'acd09.view.all'),
  ('DEAN', 'acd09.approve'),
  ('DEAN', 'acd10.manage'),
  ('DEAN', 'acd10.write'),
  ('DEAN', 'acd10.review'),
  ('DEAN', 'acd10.view'),
  ('DEAN', 'acd10.view.all'),
  ('DEAN', 'lms01.manage'),
  ('DEAN', 'lms01.write'),
  ('DEAN', 'lms01.review'),
  ('DEAN', 'lms01.view'),
  ('DEAN', 'lms01.view.all'),
  ('DEAN', 'lms02.manage'),
  ('DEAN', 'lms02.write'),
  ('DEAN', 'lms02.review'),
  ('DEAN', 'lms02.view'),
  ('DEAN', 'lms02.view.all'),
  ('DEAN', 'lms03.manage'),
  ('DEAN', 'lms03.write'),
  ('DEAN', 'lms03.review'),
  ('DEAN', 'lms03.view'),
  ('DEAN', 'lms03.view.all'),
  ('DEAN', 'lms04.manage'),
  ('DEAN', 'lms04.write'),
  ('DEAN', 'lms04.review'),
  ('DEAN', 'lms04.view'),
  ('DEAN', 'lms04.view.all'),
  ('DEAN', 'lms05.manage'),
  ('DEAN', 'lms05.write'),
  ('DEAN', 'lms05.review'),
  ('DEAN', 'lms05.view'),
  ('DEAN', 'lms05.view.all'),
  ('DEAN', 'lms06.manage'),
  ('DEAN', 'lms06.write'),
  ('DEAN', 'lms06.review'),
  ('DEAN', 'lms06.view'),
  ('DEAN', 'lms06.view.all'),
  ('DEAN', 'lms07.manage'),
  ('DEAN', 'lms07.write'),
  ('DEAN', 'lms07.review'),
  ('DEAN', 'lms07.view'),
  ('DEAN', 'lms07.view.all'),
  ('DEAN', 'lms08.manage'),
  ('DEAN', 'lms08.write'),
  ('DEAN', 'lms08.review'),
  ('DEAN', 'lms08.view'),
  ('DEAN', 'lms08.view.all'),
  ('DEAN', 'lms09.manage'),
  ('DEAN', 'lms09.write'),
  ('DEAN', 'lms09.review'),
  ('DEAN', 'lms09.view'),
  ('DEAN', 'lms09.view.all'),
  ('DEAN', 'lms09.approve'),
  ('DEAN', 'lms10.manage'),
  ('DEAN', 'lms10.write'),
  ('DEAN', 'lms10.review'),
  ('DEAN', 'lms10.view'),
  ('DEAN', 'lms10.view.all'),
  ('DEAN', 'lms10.approve')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

-- C. R15 Finance Manager: grant finance write keys
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  ('FINANCE_MANAGER', 'fin03.write'),
  ('FINANCE_MANAGER', 'fin05.write'),
  ('FINANCE_MANAGER', 'fin09.write')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

-- D. R17 HR Manager: grant HR manage/write keys
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  ('HR_MANAGER', 'hrm01.manage'),
  ('HR_MANAGER', 'hrm01.write')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

-- E. R19 Communication Manager: grant communication manage/write keys
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  ('COMM_MANAGER', 'cam01.manage'),
  ('COMM_MANAGER', 'cam01.write'),
  ('COMM_MANAGER', 'cam02.manage'),
  ('COMM_MANAGER', 'cam02.write'),
  ('COMM_MANAGER', 'cam03.manage'),
  ('COMM_MANAGER', 'cam03.write'),
  ('COMM_MANAGER', 'cam04.manage'),
  ('COMM_MANAGER', 'cam04.write'),
  ('COMM_MANAGER', 'cam05.manage'),
  ('COMM_MANAGER', 'cam05.write'),
  ('COMM_MANAGER', 'cam06.manage'),
  ('COMM_MANAGER', 'cam06.write'),
  ('COMM_MANAGER', 'cam07.manage'),
  ('COMM_MANAGER', 'cam07.write')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

-- F. R04 College Admin: grant adm05.manage (provisional per matrix §22.2 finding)
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES ('COLLEGE_ADMIN', 'adm05.manage')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

-- =========================================================================
-- 2.2 R04 College Admin: Remove Keys Flagged REMOVE & D-RBAC-M12
-- =========================================================================
DELETE FROM iam.role_template_permissions
WHERE role_code = 'COLLEGE_ADMIN'
  AND permission_code IN (
    'acd01.manage',
    'acd01.write',
    'acd02.manage',
    'acd02.write',
    'acd05.manage',
    'acd05.write',
    'acd06.manage',
    'acd06.write',
    'acd07.manage',
    'acd07.write',
    'cam01.manage',
    'cam01.write',
    'cam02.manage',
    'cam02.write',
    'cam03.manage',
    'cam03.write',
    'cam04.manage',
    'cam04.write',
    'cam05.manage',
    'cam05.write',
    'cam06.manage',
    'cam06.write',
    'cam07.manage',
    'cam07.write',
    'cam07.write.own',
    'fin03.write',
    'fin05.write',
    'fin09.write',
    'fin04.view',
    'fin04.view.all',
    'fin06.view',
    'fin06.view.all',
    'hrm04.view',
    'hrm04.view.all',
    'hrm04.write.own',
    'hrm05.view',
    'hrm05.view.all',
    'hrm01.manage',
    'hrm01.write',
    'hrm02.write.own',
    'hrm03.write.own',
    'hrm07.write.own',
    'hrm09.write.own',
    'core.pii.write',
    'core.write',
    'wf.write'
  );

DELETE FROM iam.role_permissions rp
USING iam.roles r, iam.permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.code = 'COLLEGE_ADMIN'
  AND p.code IN (
    'acd01.manage',
    'acd01.write',
    'acd02.manage',
    'acd02.write',
    'acd05.manage',
    'acd05.write',
    'acd06.manage',
    'acd06.write',
    'acd07.manage',
    'acd07.write',
    'cam01.manage',
    'cam01.write',
    'cam02.manage',
    'cam02.write',
    'cam03.manage',
    'cam03.write',
    'cam04.manage',
    'cam04.write',
    'cam05.manage',
    'cam05.write',
    'cam06.manage',
    'cam06.write',
    'cam07.manage',
    'cam07.write',
    'cam07.write.own',
    'fin03.write',
    'fin05.write',
    'fin09.write',
    'fin04.view',
    'fin04.view.all',
    'fin06.view',
    'fin06.view.all',
    'hrm04.view',
    'hrm04.view.all',
    'hrm04.write.own',
    'hrm05.view',
    'hrm05.view.all',
    'hrm01.manage',
    'hrm01.write',
    'hrm02.write.own',
    'hrm03.write.own',
    'hrm07.write.own',
    'hrm09.write.own',
    'core.pii.write',
    'core.write',
    'wf.write'
  );

-- =========================================================================
-- 2.3 R03 Institution Admin: Narrow to IADM Ceiling
-- Retain adm01..adm09, shared defaults §22.3, and doc.view
-- Remove doc.admin, doc.manage, doc.write (now held by R31)
-- Remove all non-IADM domain service keys (ACD, LMS, EXM, FIN, HRM, CAM)
-- =========================================================================
DELETE FROM iam.role_template_permissions
WHERE role_code = 'INSTITUTION_ADMIN'
  AND (
    permission_code IN ('doc.admin', 'doc.manage', 'doc.write')
    OR permission_code LIKE 'acd%'
    OR permission_code LIKE 'lms%'
    OR permission_code LIKE 'exm%'
    OR permission_code LIKE 'fin%'
    OR permission_code LIKE 'hrm%'
    OR permission_code LIKE 'cam%'
  );

DELETE FROM iam.role_permissions rp
USING iam.roles r, iam.permissions p
WHERE rp.role_id = r.id
  AND rp.permission_id = p.id
  AND r.code = 'INSTITUTION_ADMIN'
  AND (
    p.code IN ('doc.admin', 'doc.manage', 'doc.write')
    OR p.code LIKE 'acd%'
    OR p.code LIKE 'lms%'
    OR p.code LIKE 'exm%'
    OR p.code LIKE 'fin%'
    OR p.code LIKE 'hrm%'
    OR p.code LIKE 'cam%'
  );

-- =========================================================================
-- 2.4 STM-07 Check: Remove Direct Admin -> Student/Parent Grant Rules
-- (StudentRoleAssignmentService provisions R25/R26 independently)
-- =========================================================================
DELETE FROM iam.role_grant_rules
WHERE (grantor_role = 'COLLEGE_ADMIN' AND grantable_role IN ('STUDENT', 'PARENT'))
   OR (grantor_role = 'INSTITUTION_ADMIN' AND grantable_role IN ('STUDENT', 'PARENT'));

-- =========================================================================
-- 2.5 DEV Personas: Add PRINCIPAL (R05) and DEAN (R07) Test Personas
-- =========================================================================
-- 1. Create user profiles for Principal and Dean if not existing
INSERT INTO iam.user_profiles (id, tenant_id, full_name, email, status, college_id)
VALUES
  ('c05a0000-0000-4000-8000-000000000005', 'a0000000-0000-0000-0000-000000000001', 'Dr. Dev Principal', 'principal.demo@campxsync.test', 'ACTIVE', 'a0000000-0000-0000-0000-000000000002'),
  ('c07a0000-0000-4000-8000-000000000007', 'a0000000-0000-0000-0000-000000000001', 'Prof. Dev Dean', 'dean.demo@campxsync.test', 'ACTIVE', 'a0000000-0000-0000-0000-000000000002')
ON CONFLICT (id) DO UPDATE
SET full_name = EXCLUDED.full_name,
    email = EXCLUDED.email,
    status = 'ACTIVE',
    college_id = EXCLUDED.college_id,
    deleted_at = NULL;

-- 2. Assign PRINCIPAL role to Principal persona
INSERT INTO iam.role_assignments (tenant_id, role_id, user_id, college_id)
SELECT r.tenant_id, r.id, 'c05a0000-0000-4000-8000-000000000005'::uuid, 'a0000000-0000-0000-0000-000000000002'::uuid
FROM iam.roles r
WHERE r.tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND r.code = 'PRINCIPAL'
  AND r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_assignments existing
    WHERE existing.user_id = 'c05a0000-0000-4000-8000-000000000005'::uuid
      AND existing.role_id = r.id
      AND existing.deleted_at IS NULL
  );

-- 3. Assign DEAN role to Dean persona
INSERT INTO iam.role_assignments (tenant_id, role_id, user_id, college_id)
SELECT r.tenant_id, r.id, 'c07a0000-0000-4000-8000-000000000007'::uuid, 'a0000000-0000-0000-0000-000000000002'::uuid
FROM iam.roles r
WHERE r.tenant_id = 'a0000000-0000-0000-0000-000000000001'::uuid
  AND r.code = 'DEAN'
  AND r.deleted_at IS NULL
  AND NOT EXISTS (
    SELECT 1 FROM iam.role_assignments existing
    WHERE existing.user_id = 'c07a0000-0000-4000-8000-000000000007'::uuid
      AND existing.role_id = r.id
      AND existing.deleted_at IS NULL
  );

-- Resync all system role permissions across all 49 tenants
DO $$
DECLARE
  v_tenant record;
BEGIN
  FOR v_tenant IN (SELECT DISTINCT tenant_id FROM iam.roles WHERE deleted_at IS NULL) LOOP
    PERFORM iam.seed_system_roles(v_tenant.tenant_id);
  END LOOP;
END $$;

COMMIT;
