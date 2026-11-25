-- Rollback: 20261125180200_iadm06_catalogue_and_policy_seed.sql
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DELETE FROM gov.retention_rules
 WHERE domain_code = 'ANALYTICS'
   AND policy_id IN (SELECT id FROM gov.policies WHERE code = 'DEMO-ANALYTICS-HANDLING');

DELETE FROM gov.policies
 WHERE code IN ('DEMO-ANALYTICS-HANDLING', 'DEMO-ANALYTICS-EXPORT');

DELETE FROM anl.metric_catalogue
 WHERE catalogue_version = '1.0'
   AND metric_code IN ('M01','M02','M03','M04','M05','M06','M07','M08','M09','M10','DEMO-ADOPT');

COMMIT;
