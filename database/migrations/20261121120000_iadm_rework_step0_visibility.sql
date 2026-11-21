-- Migration: 20261121120000_iadm_rework_step0_visibility.sql
-- IADM Rework Step 0: platform-visible RLS, heartbeat upsert, demo seed (DEV only)
-- Target: azulbaxszsqxymqmlwfe

BEGIN;

-- D1: Platform Operator (R01/R02 via iam.is_platform_admin) can count institute users
DROP POLICY IF EXISTS user_profiles_select_platform ON iam.user_profiles;
CREATE POLICY user_profiles_select_platform ON iam.user_profiles
  FOR SELECT TO authenticated
  USING (iam.is_platform_admin());

-- D3: Platform Operator can read audit tables without a tenant claim
DROP POLICY IF EXISTS events_select_platform ON audit.events;
CREATE POLICY events_select_platform ON audit.events
  FOR SELECT TO authenticated
  USING (iam.is_platform_admin());

DROP POLICY IF EXISTS change_log_select_platform ON audit.change_log;
CREATE POLICY change_log_select_platform ON audit.change_log
  FOR SELECT TO authenticated
  USING (iam.is_platform_admin());

-- D2: Heartbeat upsert usable by services (SECURITY DEFINER; no credentials in app files)
CREATE OR REPLACE FUNCTION plat.upsert_service_heartbeat(
  p_service_name text,
  p_status text DEFAULT 'HEALTHY',
  p_latency_ms integer DEFAULT NULL
) RETURNS uuid
LANGUAGE plpgsql
SECURITY DEFINER
SET search_path TO ''
AS $$
DECLARE
  v_id uuid;
  v_status text := upper(coalesce(nullif(trim(p_status), ''), 'HEALTHY'));
  v_name text := upper(trim(p_service_name));
BEGIN
  IF v_name IS NULL OR v_name = '' THEN
    RAISE EXCEPTION 'service_name required';
  END IF;
  IF v_status NOT IN ('HEALTHY', 'DEGRADED', 'UNHEALTHY') THEN
    v_status := 'HEALTHY';
  END IF;

  INSERT INTO plat.service_health (
    id, service_name, status, latency_ms, last_heartbeat, created_by, updated_by, row_version, deleted_at
  ) VALUES (
    gen_random_uuid(), v_name, v_status, p_latency_ms, now(), auth.uid(), auth.uid(), 1, NULL
  )
  ON CONFLICT (service_name) WHERE (deleted_at IS NULL)
  DO UPDATE SET
    status = EXCLUDED.status,
    latency_ms = COALESCE(EXCLUDED.latency_ms, plat.service_health.latency_ms),
    last_heartbeat = now(),
    updated_at = now(),
    updated_by = COALESCE(auth.uid(), plat.service_health.updated_by)
  RETURNING id INTO v_id;

  RETURN v_id;
END;
$$;

REVOKE ALL ON FUNCTION plat.upsert_service_heartbeat(text, text, integer) FROM PUBLIC;
GRANT EXECUTE ON FUNCTION plat.upsert_service_heartbeat(text, text, integer) TO authenticated;
GRANT EXECUTE ON FUNCTION plat.upsert_service_heartbeat(text, text, integer) TO service_role;

COMMENT ON FUNCTION plat.upsert_service_heartbeat(text, text, integer) IS
  'IADM Rework D2: upsert plat.service_health heartbeat for platform ops desk';

-- Seed heartbeats for IADM-01…08 + gateway (so Ops desk is never empty on day one)
SELECT plat.upsert_service_heartbeat('IADM-01', 'HEALTHY', 12);
SELECT plat.upsert_service_heartbeat('IADM-02', 'HEALTHY', 11);
SELECT plat.upsert_service_heartbeat('IADM-03', 'HEALTHY', 14);
SELECT plat.upsert_service_heartbeat('IADM-04', 'HEALTHY', 13);
SELECT plat.upsert_service_heartbeat('IADM-05', 'HEALTHY', 15);
SELECT plat.upsert_service_heartbeat('IADM-06', 'HEALTHY', 16);
SELECT plat.upsert_service_heartbeat('IADM-07', 'HEALTHY', 17);
SELECT plat.upsert_service_heartbeat('IADM-08', 'HEALTHY', 10);
SELECT plat.upsert_service_heartbeat('API-GATEWAY', 'HEALTHY', 8);
SELECT plat.upsert_service_heartbeat('INSTITUTE-ADMIN', 'HEALTHY', 20);

-- Demo pending items (section 7) — use existing DEV demo tenant and personas; no passwords
DO $$
DECLARE
  v_tenant uuid := 'a0000000-0000-0000-0000-000000000001'::uuid;
  v_r01 uuid;
  v_r03 uuid := 'c03a0000-0000-4000-8000-000000000003'::uuid;
  v_pending int;
BEGIN
  SELECT user_id INTO v_r01 FROM plat.platform_admins
   WHERE status = 'ACTIVE' AND deleted_at IS NULL
   ORDER BY created_at NULLS LAST LIMIT 1;

  IF v_r01 IS NULL THEN
    RAISE NOTICE 'No platform admin row; skip quorum/break-glass demo seed';
    RETURN;
  END IF;

  SELECT COUNT(*) INTO v_pending FROM plat.institution_admin_quorum_requests
   WHERE tenant_id = v_tenant AND status = 'PENDING' AND deleted_at IS NULL;
  IF v_pending = 0 AND EXISTS (SELECT 1 FROM iam.user_profiles WHERE id = v_r03 AND deleted_at IS NULL) THEN
    INSERT INTO plat.institution_admin_quorum_requests (
      id, tenant_id, target_user_id, action, status, reason, requested_by, requested_track, created_by
    ) VALUES (
      gen_random_uuid(), v_tenant, v_r03, 'APPOINT', 'PENDING',
      'Rework Step 0 demo: appoint Institution Admin (R03)', v_r01, 'R01', v_r01
    );
  END IF;

  SELECT COUNT(*) INTO v_pending FROM plat.break_glass_requests
   WHERE tenant_id = v_tenant AND status = 'PENDING' AND deleted_at IS NULL;
  IF v_pending = 0 THEN
    INSERT INTO plat.break_glass_requests (
      id, tenant_id, requested_by, requester_role, reason, requested_minutes, status, created_by
    ) VALUES (
      gen_random_uuid(), v_tenant, v_r01, 'R01',
      'Rework Step 0 demo: break-glass recovery drill', 60, 'PENDING', v_r01
    );
  END IF;

  IF to_regclass('plat.institute_lifecycle_requests') IS NOT NULL THEN
    SELECT COUNT(*) INTO v_pending FROM plat.institute_lifecycle_requests
     WHERE tenant_id = v_tenant AND status = 'PENDING' AND deleted_at IS NULL;
    IF v_pending = 0 THEN
      BEGIN
        INSERT INTO plat.institute_lifecycle_requests (
          id, tenant_id, request_type, status, reason, requested_by, created_by
        ) VALUES (
          gen_random_uuid(), v_tenant, 'SUSPEND', 'PENDING',
          'Rework Step 0 demo: institute suspend request', COALESCE(v_r03, v_r01), COALESCE(v_r03, v_r01)
        );
      EXCEPTION WHEN OTHERS THEN
        RAISE NOTICE 'Skip lifecycle demo seed: %', SQLERRM;
      END;
    END IF;
  END IF;
END $$;

COMMIT;
