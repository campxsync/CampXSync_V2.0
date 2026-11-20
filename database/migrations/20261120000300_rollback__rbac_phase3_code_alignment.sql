-- Rollback: 20261120000300_rollback__rbac_phase3_code_alignment.sql
-- Description: Rollback for Phase 3 Code Alignment DB elements
-- Target DB: azulbaxszsqxymqmlwfe (Dev Supabase)
-- Author: CampX Platform Engineering / Owner Approval Recorded Niranjan V.G. 2026-11-20

BEGIN;

-- 1. Remove CHECK constraint on iam.api_clients
ALTER TABLE iam.api_clients
  DROP CONSTRAINT IF EXISTS ck_api_clients_expires_at;

-- 2. Drop plat.assign_institution_admin function
DROP FUNCTION IF EXISTS plat.assign_institution_admin(uuid, uuid, uuid);

COMMIT;
