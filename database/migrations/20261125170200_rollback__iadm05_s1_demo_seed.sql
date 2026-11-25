-- Rollback: remove S1 DEMO ONLY seed rows (US-030).
-- Target: azulbaxszsqxymqmlwfe (DEV only)

BEGIN;

DELETE FROM gov.policy_decisions
WHERE policy_id IN (
  SELECT id FROM gov.policies
  WHERE code LIKE 'DEMO-%'
);

DELETE FROM gov.classification_defs
WHERE policy_id IN (
  SELECT id FROM gov.policies WHERE code LIKE 'DEMO-%'
);

DELETE FROM gov.retention_rules
WHERE policy_id IN (
  SELECT id FROM gov.policies WHERE code LIKE 'DEMO-%'
);

DELETE FROM gov.policy_revisions
WHERE policy_id IN (
  SELECT id FROM gov.policies WHERE code LIKE 'DEMO-%'
);

DELETE FROM gov.policies WHERE code LIKE 'DEMO-%';

COMMIT;
