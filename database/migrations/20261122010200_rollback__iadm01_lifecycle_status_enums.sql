-- Rollback status enum expansion (DEV). Maps ONBOARDING/PENDING_HANDOVER back to prior values.

BEGIN;

UPDATE core.tenants
SET status = 'INACTIVE', updated_at = now()
WHERE deleted_at IS NULL AND upper(status) = 'ONBOARDING';

UPDATE core.colleges
SET status = 'INACTIVE', updated_at = now()
WHERE deleted_at IS NULL AND upper(status) IN ('ONBOARDING', 'PENDING_HANDOVER');

ALTER TABLE core.tenants DROP CONSTRAINT IF EXISTS ck_tenants_status;
ALTER TABLE core.tenants
  ADD CONSTRAINT ck_tenants_status
  CHECK (status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text]));

ALTER TABLE core.colleges DROP CONSTRAINT IF EXISTS ck_colleges_status;
-- Restore prior college status set if known; otherwise keep a safe ACTIVE/SUSPENDED/INACTIVE/ARCHIVED set
ALTER TABLE core.colleges
  ADD CONSTRAINT ck_colleges_status
  CHECK (status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text]));

COMMIT;
