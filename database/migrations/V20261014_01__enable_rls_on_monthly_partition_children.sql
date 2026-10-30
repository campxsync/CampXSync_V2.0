-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION (GATED PROPOSAL — DO NOT EXECUTE WITHOUT APPROVAL)
-- ==============================================================================
-- Target Environment: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Target Schemas: audit, cam, hrm, lms
-- Required Ritual: Explicit owner command "GO PARTITION-RLS-FIX"
-- Safety Policy:
--   1. NEVER EXECUTE AUTOMATICALLY OR ON FORBIDDEN PROD (ekspahbsbwdpraaqfqui).
--   2. Explicitly asserts discovered inventory matches the 24 expected monthly tables.
--   3. Closes unintended direct partition access without breaking parent table routing.
-- ==============================================================================

DO $$
DECLARE
  v_expected_tables text[] := ARRAY[
    'audit.access_events_p202610', 'audit.access_events_p202611', 'audit.access_events_p202612', 'audit.access_events_p202701',
    'audit.change_log_p202610',    'audit.change_log_p202611',    'audit.change_log_p202612',    'audit.change_log_p202701',
    'audit.events_p202610',        'audit.events_p202611',        'audit.events_p202612',        'audit.events_p202701',
    'cam.delivery_events_p202610', 'cam.delivery_events_p202611', 'cam.delivery_events_p202612', 'cam.delivery_events_p202701',
    'hrm.attendance_punches_p202610', 'hrm.attendance_punches_p202611', 'hrm.attendance_punches_p202612', 'hrm.attendance_punches_p202701',
    'lms.progress_events_p202610', 'lms.progress_events_p202611', 'lms.progress_events_p202612', 'lms.progress_events_p202701'
  ];
  v_discovered_count integer;
  v_table text;
BEGIN
  -- 1. Precondition: Verify that all 24 expected child partitions exist in the catalog
  SELECT count(*)
  INTO v_discovered_count
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE format('%I.%I', n.nspname, c.relname) = ANY(v_expected_tables);

  IF v_discovered_count <> 24 THEN
    RAISE EXCEPTION 'Partition RLS safety check aborted: Expected 24 child tables, but catalog search found %.', v_discovered_count;
  END IF;

  -- 2. Execution: Enable Row Level Security and revoke direct unprivileged access
  -- In PostgreSQL 14+, partition routing through parent tables inherits parent RLS.
  -- Direct queries targeting child tables will now be blocked for anon & authenticated.
  FOREACH v_table IN ARRAY v_expected_tables
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);
    EXECUTE format('REVOKE ALL ON TABLE %s FROM anon, authenticated', v_table);
  END LOOP;

  RAISE NOTICE 'Partition RLS fix applied successfully across all 24 verified monthly child tables.';
END $$;
