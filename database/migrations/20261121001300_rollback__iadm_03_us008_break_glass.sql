-- Rollback: 20261121001300_iadm_03_us008_break_glass.sql
-- DEV only

BEGIN;

DROP FUNCTION IF EXISTS plat.break_glass_requester_role(uuid, uuid);
DROP TABLE IF EXISTS plat.break_glass_actions;
DROP TABLE IF EXISTS plat.break_glass_sessions;
DROP TABLE IF EXISTS plat.break_glass_requests;

COMMIT;
