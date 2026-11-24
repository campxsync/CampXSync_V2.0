-- Rollback: 20261124130000_iadm03_catalog_change_requests.sql
-- DEV only. Does not reverse applied role_template_permissions changes.

BEGIN;

DROP POLICY IF EXISTS catalog_change_update ON iam.catalog_change_requests;
DROP POLICY IF EXISTS catalog_change_insert ON iam.catalog_change_requests;
DROP POLICY IF EXISTS catalog_change_select ON iam.catalog_change_requests;
DROP TABLE IF EXISTS iam.catalog_change_requests;

COMMIT;
