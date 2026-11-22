-- IADM-01: expand institute/college lifecycle status enums to match approved story language.
-- Plain language: institutes use ONBOARDING/ACTIVE/SUSPENDED/ARCHIVED (not OFFBOARDED).
-- Colleges use ONBOARDING/PENDING_HANDOVER/ACTIVE/SUSPENDED/ARCHIVED.
-- DEV only (azulbaxszsqxymqmlwfe). Rollback: 20261122010200_rollback__iadm01_lifecycle_status_enums.sql

BEGIN;

-- Map legacy INACTIVE → ONBOARDING for institute rows already present
UPDATE core.tenants
SET status = 'ONBOARDING', updated_at = now()
WHERE deleted_at IS NULL AND upper(status) = 'INACTIVE';

ALTER TABLE core.tenants DROP CONSTRAINT IF EXISTS ck_tenants_status;
ALTER TABLE core.tenants
  ADD CONSTRAINT ck_tenants_status
  CHECK (status = ANY (ARRAY[
    'ONBOARDING'::text,
    'ACTIVE'::text,
    'SUSPENDED'::text,
    'ARCHIVED'::text,
    'INACTIVE'::text  -- legacy alias retained temporarily for any unread code paths
  ]));

ALTER TABLE core.colleges DROP CONSTRAINT IF EXISTS ck_colleges_status;
ALTER TABLE core.colleges
  ADD CONSTRAINT ck_colleges_status
  CHECK (status = ANY (ARRAY[
    'ONBOARDING'::text,
    'PENDING_HANDOVER'::text,
    'ACTIVE'::text,
    'SUSPENDED'::text,
    'ARCHIVED'::text,
    'INACTIVE'::text
  ]));

COMMIT;
