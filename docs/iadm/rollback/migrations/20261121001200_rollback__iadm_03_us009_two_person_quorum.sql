-- Rollback: 20261121001200_iadm_03_us009_two_person_quorum.sql
-- DEV only

BEGIN;

DROP FUNCTION IF EXISTS plat.platform_admin_track(uuid);
DROP TABLE IF EXISTS plat.institution_admin_quorum_requests;

COMMIT;
