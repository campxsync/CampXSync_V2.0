-- Rollback for 20261122010100_iadm01_demo_seed_s1.sql (DEV only).
-- Soft-deletes demo institute INST-DEMO-001 and its demo college; does not touch other tenants.

UPDATE core.colleges c
SET deleted_at = now(), updated_at = now()
FROM core.tenants t
WHERE c.tenant_id = t.id
  AND t.code = 'INST-DEMO-001'
  AND c.code = 'COL-DEMO-001'
  AND c.deleted_at IS NULL;

UPDATE core.tenants
SET deleted_at = now(), updated_at = now()
WHERE code = 'INST-DEMO-001'
  AND deleted_at IS NULL;
