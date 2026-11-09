-- CampXSyncV2.0 partition children: enable RLS and remove direct anon/authenticated access.
-- Parent tables already have RLS. Queries through those parents keep using the parent policies.
-- Aborts unless the live catalog has exactly these 30 child tables without RLS.

DO $$
DECLARE
  v_expected text[] := ARRAY[
    'audit.access_events_default',
    'audit.access_events_p202610',
    'audit.access_events_p202611',
    'audit.access_events_p202612',
    'audit.access_events_p202701',
    'audit.change_log_default',
    'audit.change_log_p202610',
    'audit.change_log_p202611',
    'audit.change_log_p202612',
    'audit.change_log_p202701',
    'audit.events_default',
    'audit.events_p202610',
    'audit.events_p202611',
    'audit.events_p202612',
    'audit.events_p202701',
    'cam.delivery_events_default',
    'cam.delivery_events_p202610',
    'cam.delivery_events_p202611',
    'cam.delivery_events_p202612',
    'cam.delivery_events_p202701',
    'hrm.attendance_punches_default',
    'hrm.attendance_punches_p202610',
    'hrm.attendance_punches_p202611',
    'hrm.attendance_punches_p202612',
    'hrm.attendance_punches_p202701',
    'lms.progress_events_default',
    'lms.progress_events_p202610',
    'lms.progress_events_p202611',
    'lms.progress_events_p202612',
    'lms.progress_events_p202701'
  ];
  v_found integer;
  v_table text;
BEGIN
  SELECT count(*)
  INTO v_found
  FROM pg_class c
  JOIN pg_namespace n ON n.oid = c.relnamespace
  WHERE c.relkind = 'r'
    AND c.relispartition
    AND NOT c.relrowsecurity
    AND format('%I.%I', n.nspname, c.relname) = ANY (v_expected);

  IF v_found <> 30 THEN
    RAISE EXCEPTION 'Partition RLS aborted: expected 30 child tables without RLS, found %.', v_found;
  END IF;

  FOREACH v_table IN ARRAY v_expected
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);
    EXECUTE format('REVOKE ALL ON TABLE %s FROM anon, authenticated', v_table);
  END LOOP;
END $$;
