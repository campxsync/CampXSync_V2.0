-- Migration: 20261120000400_rbac_phase4_verification_and_patch.sql
-- Description: Phase 4 verification and patch for Enterprise RBAC Matrix v3.0:
--   1. Restore INSTITUTION_ADMIN -> STUDENT, INSTITUTION_ADMIN -> PARENT, IAM_OPERATOR -> STUDENT, IAM_OPERATOR -> PARENT in iam.role_grant_rules
--   2. De-conflate platform admin identity: remove ba77b8f7-ab32-46bd-85d2-5aade3526880 from plat.platform_admins
--   3. De-duplicate ba77b8f7-ab32-46bd-85d2-5aade3526880: remove INSTITUTION_ADMIN assignment so it is solely COLLEGE_ADMIN
--   4. Seed dedicated R03 Institution Admin persona c03a0000-0000-4000-8000-000000000003 in tenant a0000000-0000-0000-0000-000000000001

BEGIN;

-- 1. Restore grant rules for STUDENT and PARENT
-- INSTITUTION_ADMIN (R03) and IAM_OPERATOR (R38) may grant STUDENT (R25) and PARENT (R26)
-- COLLEGE_ADMIN -> STUDENT and COLLEGE_ADMIN -> PARENT remain DELETED per Matrix §21.1 and §22.1
INSERT INTO iam.role_grant_rules (grantor_role, grantable_role)
SELECT v.grantor_role, v.grantable_role
FROM (VALUES 
  ('INSTITUTION_ADMIN', 'STUDENT'),
  ('INSTITUTION_ADMIN', 'PARENT'),
  ('IAM_OPERATOR', 'STUDENT'),
  ('IAM_OPERATOR', 'PARENT')
) AS v(grantor_role, grantable_role)
WHERE NOT EXISTS (
  SELECT 1 FROM iam.role_grant_rules r
  WHERE r.grantor_role = v.grantor_role AND r.grantable_role = v.grantable_role AND r.deleted_at IS NULL
);

-- 2. De-conflate platform admin test identity
-- Remove ba77b8f7-ab32-46bd-85d2-5aade3526880 from plat.platform_admins so it resolves strictly as tenant college admin
DELETE FROM plat.platform_admins
WHERE user_id = 'ba77b8f7-ab32-46bd-85d2-5aade3526880';

-- 3. De-duplicate ba77b8f7 role assignments: remove INSTITUTION_ADMIN assignment so user is solely COLLEGE_ADMIN
DELETE FROM iam.role_assignments ra
USING iam.roles r
WHERE ra.role_id = r.id
  AND ra.user_id = 'ba77b8f7-ab32-46bd-85d2-5aade3526880'
  AND r.code = 'INSTITUTION_ADMIN';

-- 4. Seed dedicated R03 Institution Admin persona
-- ID: c03a0000-0000-4000-8000-000000000003
INSERT INTO iam.user_profiles (id, tenant_id, full_name, email, status, college_id, created_at, updated_at, row_version)
VALUES (
  'c03a0000-0000-4000-8000-000000000003',
  'a0000000-0000-0000-0000-000000000001',
  'Dr. Dev Institution Admin',
  'institution.admin.demo@campxsync.test',
  'ACTIVE',
  NULL,
  now(),
  now(),
  1
)
ON CONFLICT (id) DO UPDATE SET
  full_name = EXCLUDED.full_name,
  email = EXCLUDED.email,
  status = EXCLUDED.status,
  college_id = EXCLUDED.college_id,
  updated_at = now();

-- Assign INSTITUTION_ADMIN role to c03a0000-0000-4000-8000-000000000003
INSERT INTO iam.role_assignments (
  id, tenant_id, role_id, user_id, college_id, granted_by, created_at, updated_at, created_by, updated_by, row_version
)
SELECT 
  'c03a0000-0000-4000-8000-000000000004'::uuid,
  r.tenant_id,
  r.id,
  'c03a0000-0000-4000-8000-000000000003'::uuid,
  NULL,
  'c03a0000-0000-4000-8000-000000000003'::uuid,
  now(),
  now(),
  'c03a0000-0000-4000-8000-000000000003'::uuid,
  'c03a0000-0000-4000-8000-000000000003'::uuid,
  1
FROM iam.roles r
WHERE r.tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND r.code = 'INSTITUTION_ADMIN'
  AND r.deleted_at IS NULL
ON CONFLICT (id) DO NOTHING;

COMMIT;
