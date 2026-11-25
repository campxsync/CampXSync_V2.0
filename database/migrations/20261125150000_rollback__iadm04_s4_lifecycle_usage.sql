-- Rollback: 20261125150000_iadm04_s4_lifecycle_usage.sql
BEGIN;
DROP TABLE IF EXISTS plat.renewal_actions CASCADE;
DROP TABLE IF EXISTS plat.usage_measurements CASCADE;
DROP TABLE IF EXISTS plat.billing_job_checkpoints CASCADE;
COMMIT;
