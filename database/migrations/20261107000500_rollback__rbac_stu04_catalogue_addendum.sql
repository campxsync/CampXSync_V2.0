-- Rollback for 20261107000500_rbac_stu04_catalogue_addendum.sql

UPDATE iam.role_template_permissions
   SET deleted_at = clock_timestamp()
 WHERE permission_code LIKE 'stu04.%'
   AND deleted_at IS NULL;

UPDATE iam.permissions
   SET deleted_at = clock_timestamp()
 WHERE code LIKE 'stu04.%'
   AND deleted_at IS NULL;

SELECT iam.resync_system_roles();
