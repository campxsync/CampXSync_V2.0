-- Rollback: revert SVC-CONSUMER-01 scope to security+attendance only (does not delete client).
BEGIN;
UPDATE iam.api_client_grants g
SET resource_scope = '{"domains":["security","attendance"],"keys":["security.session_timeout_min","attendance.min_pct"]}'::jsonb,
    updated_at = now()
FROM iam.api_clients c, iam.permissions p
WHERE g.client_id = c.id
  AND g.permission_id = p.id
  AND c.name = 'SVC-CONSUMER-01'
  AND p.code = 'cfg.consumer.read'
  AND g.deleted_at IS NULL
  AND c.deleted_at IS NULL;
COMMIT;
