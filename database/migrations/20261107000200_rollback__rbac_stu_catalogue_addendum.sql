-- Rollback for 20261107000200_rbac_stu_catalogue_addendum.sql

UPDATE iam.role_template_permissions
   SET deleted_at = clock_timestamp()
 WHERE permission_code LIKE 'stu0%'
    OR role_code IN ('REGISTRAR', 'ADMISSIONS_OFFICER', 'APPLICANT');

UPDATE iam.role_templates
   SET deleted_at = clock_timestamp()
 WHERE code IN ('REGISTRAR', 'ADMISSIONS_OFFICER', 'APPLICANT')
   AND deleted_at IS NULL;

UPDATE iam.permissions
   SET deleted_at = clock_timestamp()
 WHERE code LIKE 'stu0%'
   AND deleted_at IS NULL;

-- Re-seed to drop withdrawn template permissions from tenant role_permissions
SELECT iam.resync_system_roles();
