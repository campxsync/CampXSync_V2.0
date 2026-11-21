-- Rollback: 20261120000400_rollback__rbac_phase4_verification_and_patch.sql
-- Description: Reverts Phase 4 database changes:
--   1. Deletes restored role_grant_rules for STUDENT and PARENT
--   2. Restores ba77b8f7-ab32-46bd-85d2-5aade3526880 to plat.platform_admins
--   3. Restores INSTITUTION_ADMIN assignment for ba77b8f7-ab32-46bd-85d2-5aade3526880
--   4. Removes seeded R03 persona c03a0000-0000-4000-8000-000000000003

BEGIN;

-- 1. Remove restored role grant rules
DELETE FROM iam.role_grant_rules
WHERE (grantor_role = 'INSTITUTION_ADMIN' AND grantable_role IN ('STUDENT', 'PARENT'))
   OR (grantor_role = 'IAM_OPERATOR' AND grantable_role IN ('STUDENT', 'PARENT'));

-- 2. Restore ba77b8f7 to plat.platform_admins
INSERT INTO plat.platform_admins (
  id, user_id, full_name, role_code, status, created_at, updated_at, row_version
) VALUES (
  'a0000000-0000-0000-0000-000000000005',
  'ba77b8f7-ab32-46bd-85d2-5aade3526880',
  'CampX Dev Test Admin',
  'SUPER_ADMIN',
  'ACTIVE',
  now(),
  now(),
  1
) ON CONFLICT (user_id) DO NOTHING;

-- 3. Restore INSTITUTION_ADMIN assignment for ba77b8f7-ab32-46bd-85d2-5aade3526880
INSERT INTO iam.role_assignments (
  id, tenant_id, role_id, user_id, college_id, granted_by, created_at, updated_at, created_by, updated_by, row_version
)
SELECT 
  gen_random_uuid(),
  r.tenant_id,
  r.id,
  'ba77b8f7-ab32-46bd-85d2-5aade3526880'::uuid,
  NULL,
  'ba77b8f7-ab32-46bd-85d2-5aade3526880'::uuid,
  now(),
  now(),
  'ba77b8f7-ab32-46bd-85d2-5aade3526880'::uuid,
  'ba77b8f7-ab32-46bd-85d2-5aade3526880'::uuid,
  1
FROM iam.roles r
WHERE r.tenant_id = 'a0000000-0000-0000-0000-000000000001'
  AND r.code = 'INSTITUTION_ADMIN'
  AND r.deleted_at IS NULL
ON CONFLICT DO NOTHING;

-- 4. Delete seeded R03 persona assignment and profile
DELETE FROM iam.role_assignments
WHERE user_id = 'c03a0000-0000-4000-8000-000000000003'::uuid;

DELETE FROM iam.user_profiles
WHERE id = 'c03a0000-0000-4000-8000-000000000003'::uuid;

COMMIT;
