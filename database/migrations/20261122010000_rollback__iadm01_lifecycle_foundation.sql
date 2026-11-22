-- Rollback: 20261122010000_iadm01_lifecycle_foundation.sql
-- DEV only. Does not touch plat.assign_institution_admin (K1).

BEGIN;

DROP TABLE IF EXISTS plat.iadm01_feature_flags CASCADE;
DROP TABLE IF EXISTS plat.college_cadm_handovers CASCADE;
DROP TABLE IF EXISTS plat.institute_lifecycle_requests CASCADE;
DROP TABLE IF EXISTS plat.institute_lifecycle_events CASCADE;
DROP TABLE IF EXISTS plat.iadm01_onboarding_steps CASCADE;

DROP FUNCTION IF EXISTS plat.is_tenant_suspended(uuid);
DROP FUNCTION IF EXISTS plat.is_auth_linked_user(uuid);

COMMIT;
