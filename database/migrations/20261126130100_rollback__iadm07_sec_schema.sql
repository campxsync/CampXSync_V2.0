-- Rollback: 20261126130100_rollback__iadm07_sec_schema.sql
-- Plain language: drop only IADM-07 sec schema objects created by the paired migration.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DROP SCHEMA IF EXISTS sec CASCADE;

COMMIT;
