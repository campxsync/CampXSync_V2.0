-- Rollback: 20261125180100_iadm06_anl_schema.sql
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DROP TABLE IF EXISTS anl.export_requests CASCADE;
DROP TABLE IF EXISTS anl.metric_catalogue CASCADE;
DROP SCHEMA IF EXISTS anl CASCADE;

COMMIT;
