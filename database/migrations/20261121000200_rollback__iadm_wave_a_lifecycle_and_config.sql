-- Rollback: 20261121000200_rollback__iadm_wave_a_lifecycle_and_config.sql
-- DEV only. Drops Wave A IADM objects. Does not restore row data.

BEGIN;

DROP FUNCTION IF EXISTS cfg.resolve_effective_setting(uuid, uuid, text);

DROP TABLE IF EXISTS cfg.setting_delegations CASCADE;
DROP TABLE IF EXISTS plat.institute_lifecycle_requests CASCADE;
DROP TABLE IF EXISTS plat.institute_lifecycle_events CASCADE;
DROP TABLE IF EXISTS plat.college_admin_handovers CASCADE;

ALTER TABLE cfg.setting_definitions DROP COLUMN IF EXISTS platform_locked;
ALTER TABLE cfg.setting_definitions DROP COLUMN IF EXISTS delegable_to_college;

COMMIT;
