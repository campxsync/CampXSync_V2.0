-- Rollback: 20261123010100_rollback__iadm02_demo_seed.sql

BEGIN;

DELETE FROM iam.api_client_grants g
USING iam.api_clients c
WHERE g.client_id = c.id AND c.name = 'SVC-CONSUMER-01';

UPDATE iam.api_clients
SET deleted_at = now(), status = 'REVOKED'
WHERE name = 'SVC-CONSUMER-01' AND deleted_at IS NULL;

UPDATE cfg.policies
SET deleted_at = now()
WHERE policy_code = 'IADM02_POL_PLATFORM_BASELINE' AND deleted_at IS NULL;

UPDATE cfg.config_drafts
SET deleted_at = now()
WHERE idempotency_key IN ('iadm02-seed-draft-001', 'iadm02-seed-draft-002')
  AND deleted_at IS NULL;

UPDATE cfg.config_versions
SET deleted_at = now(), status = 'SUPERSEDED'
WHERE version_label = 'VER-001' AND deleted_at IS NULL;

-- Do NOT restore POL_SEC_* forged rows.

COMMIT;
