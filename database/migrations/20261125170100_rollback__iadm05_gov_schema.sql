-- Rollback: 20261125170100_rollback__iadm05_gov_schema.sql
-- Plain language: drop IADM-05 gov schema objects.
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DROP TABLE IF EXISTS gov.domain_outcomes CASCADE;
DROP TABLE IF EXISTS gov.governance_requests CASCADE;
DROP TABLE IF EXISTS gov.retention_rules CASCADE;
DROP TABLE IF EXISTS gov.classification_defs CASCADE;
DROP TABLE IF EXISTS gov.policy_decisions CASCADE;
DROP TABLE IF EXISTS gov.policy_revisions CASCADE;
DROP TABLE IF EXISTS gov.policies CASCADE;
DROP TABLE IF EXISTS gov.outage_flags CASCADE;
DROP SCHEMA IF EXISTS gov CASCADE;

COMMIT;
