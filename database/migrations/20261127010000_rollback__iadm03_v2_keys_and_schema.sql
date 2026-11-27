-- Rollback: 20261127010000_rollback__iadm03_v2_keys_and_schema.sql
-- Purpose: Reverses 20261127010000_iadm03_v2_keys_and_schema.sql on DEV only.

DROP SCHEMA IF EXISTS iadm03 CASCADE;

DELETE FROM iam.role_permissions
WHERE permission_id IN (SELECT id FROM iam.permissions WHERE code LIKE 'iadm03.%');

DELETE FROM iam.role_template_permissions
WHERE permission_code LIKE 'iadm03.%';

DELETE FROM iam.permissions
WHERE code LIKE 'iadm03.%';
