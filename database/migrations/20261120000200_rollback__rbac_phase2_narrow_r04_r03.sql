-- Rollback: 20261120000200_rollback__rbac_phase2_narrow_r04_r03.sql
-- Description: Rollback for Phase 2 Narrow R04 and R03
-- Target DB: azulbaxszsqxymqmlwfe
-- Author: CampX Platform Engineering / Owner Approval Recorded Niranjan V.G. 2026-11-20

BEGIN;

-- 1. Restore R04 permissions
INSERT INTO iam.role_template_permissions (role_code, permission_code)
VALUES
  ('COLLEGE_ADMIN', 'acd01.manage'),
  ('COLLEGE_ADMIN', 'acd01.write'),
  ('COLLEGE_ADMIN', 'acd02.manage'),
  ('COLLEGE_ADMIN', 'acd02.write'),
  ('COLLEGE_ADMIN', 'acd05.manage'),
  ('COLLEGE_ADMIN', 'acd05.write'),
  ('COLLEGE_ADMIN', 'acd06.manage'),
  ('COLLEGE_ADMIN', 'acd06.write'),
  ('COLLEGE_ADMIN', 'acd07.manage'),
  ('COLLEGE_ADMIN', 'acd07.write'),
  ('COLLEGE_ADMIN', 'cam01.manage'),
  ('COLLEGE_ADMIN', 'cam01.write'),
  ('COLLEGE_ADMIN', 'cam02.manage'),
  ('COLLEGE_ADMIN', 'cam02.write'),
  ('COLLEGE_ADMIN', 'cam03.manage'),
  ('COLLEGE_ADMIN', 'cam03.write'),
  ('COLLEGE_ADMIN', 'cam04.manage'),
  ('COLLEGE_ADMIN', 'cam04.write'),
  ('COLLEGE_ADMIN', 'cam05.manage'),
  ('COLLEGE_ADMIN', 'cam05.write'),
  ('COLLEGE_ADMIN', 'cam06.manage'),
  ('COLLEGE_ADMIN', 'cam06.write'),
  ('COLLEGE_ADMIN', 'cam07.manage'),
  ('COLLEGE_ADMIN', 'cam07.write'),
  ('COLLEGE_ADMIN', 'cam07.write.own'),
  ('COLLEGE_ADMIN', 'fin03.write'),
  ('COLLEGE_ADMIN', 'fin05.write'),
  ('COLLEGE_ADMIN', 'fin09.write'),
  ('COLLEGE_ADMIN', 'fin04.view'),
  ('COLLEGE_ADMIN', 'fin04.view.all'),
  ('COLLEGE_ADMIN', 'fin06.view'),
  ('COLLEGE_ADMIN', 'fin06.view.all'),
  ('COLLEGE_ADMIN', 'hrm04.view'),
  ('COLLEGE_ADMIN', 'hrm04.view.all'),
  ('COLLEGE_ADMIN', 'hrm04.write.own'),
  ('COLLEGE_ADMIN', 'hrm05.view'),
  ('COLLEGE_ADMIN', 'hrm05.view.all'),
  ('COLLEGE_ADMIN', 'hrm01.manage'),
  ('COLLEGE_ADMIN', 'hrm01.write'),
  ('COLLEGE_ADMIN', 'hrm02.write.own'),
  ('COLLEGE_ADMIN', 'hrm03.write.own'),
  ('COLLEGE_ADMIN', 'hrm07.write.own'),
  ('COLLEGE_ADMIN', 'hrm09.write.own'),
  ('COLLEGE_ADMIN', 'core.pii.write'),
  ('COLLEGE_ADMIN', 'core.write'),
  ('COLLEGE_ADMIN', 'wf.write')
ON CONFLICT (role_code, permission_code) WHERE deleted_at IS NULL DO NOTHING;

DELETE FROM iam.role_template_permissions
WHERE role_code = 'COLLEGE_ADMIN' AND permission_code = 'adm05.manage';

-- 2. Restore Student and Parent grant rules
INSERT INTO iam.role_grant_rules (grantor_role, grantable_role)
VALUES
  ('COLLEGE_ADMIN', 'STUDENT'),
  ('COLLEGE_ADMIN', 'PARENT'),
  ('INSTITUTION_ADMIN', 'STUDENT'),
  ('INSTITUTION_ADMIN', 'PARENT')
ON CONFLICT (grantor_role, grantable_role) WHERE deleted_at IS NULL DO NOTHING;

-- 3. Remove DEV personas role assignments
DELETE FROM iam.role_assignments
WHERE user_id IN ('c05a0000-0000-4000-8000-000000000005'::uuid, 'c07a0000-0000-4000-8000-000000000007'::uuid);

-- 4. Resync all system role permissions across all 49 tenants
DO $$
DECLARE
  v_tenant record;
BEGIN
  FOR v_tenant IN (SELECT DISTINCT tenant_id FROM iam.roles WHERE deleted_at IS NULL) LOOP
    PERFORM iam.seed_system_roles(v_tenant.tenant_id);
  END LOOP;
END $$;

COMMIT;
