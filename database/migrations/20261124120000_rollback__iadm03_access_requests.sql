-- Rollback: 20261124120000_iadm03_access_requests.sql
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DROP POLICY IF EXISTS access_requests_update ON iam.access_requests;
DROP POLICY IF EXISTS access_requests_insert ON iam.access_requests;
DROP POLICY IF EXISTS access_requests_select ON iam.access_requests;
DROP TABLE IF EXISTS iam.access_requests;

COMMIT;
