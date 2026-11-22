-- Rollback: 20261121120000_iadm_rework_step0_visibility.sql
BEGIN;

DELETE FROM plat.institution_admin_quorum_requests
 WHERE reason LIKE 'Rework Step 0 demo:%' AND deleted_at IS NULL;

DELETE FROM plat.break_glass_requests
 WHERE reason LIKE 'Rework Step 0 demo:%' AND deleted_at IS NULL;

DO $$
BEGIN
  IF to_regclass('plat.institute_lifecycle_requests') IS NOT NULL THEN
    DELETE FROM plat.institute_lifecycle_requests
     WHERE reason LIKE 'Rework Step 0 demo:%' AND deleted_at IS NULL;
  END IF;
EXCEPTION WHEN OTHERS THEN
  RAISE NOTICE 'lifecycle rollback skip: %', SQLERRM;
END $$;

DELETE FROM plat.service_health
 WHERE service_name IN (
   'IADM-01','IADM-02','IADM-03','IADM-04','IADM-05','IADM-06','IADM-07','IADM-08',
   'API-GATEWAY','INSTITUTE-ADMIN'
 ) AND deleted_at IS NULL;

DROP FUNCTION IF EXISTS plat.upsert_service_heartbeat(text, text, integer);

DROP POLICY IF EXISTS user_profiles_select_platform ON iam.user_profiles;
DROP POLICY IF EXISTS events_select_platform ON audit.events;
DROP POLICY IF EXISTS change_log_select_platform ON audit.change_log;

COMMIT;
