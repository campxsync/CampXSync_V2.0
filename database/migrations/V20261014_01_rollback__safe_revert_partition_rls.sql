-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION ROLLBACK (GATED RECOVERY PLAN)
-- ==============================================================================
-- Target Environment: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Target Schemas: audit, cam, hrm, lms
-- Required Ritual: Explicit owner authorization for rollback
-- Safety Policy:
--   DO NOT blindly disable RLS or restore wide-open grants.
--   If recovery is required because client queries explicitly target child partitions
--   directly (bypassing the parent partitioned table), establish tenant-isolated policies
--   rather than opening unprotected tables to cross-tenant data leaks.
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
  v_table text;
  v_policy_exists boolean;
BEGIN
  -- Safe recovery strategy:
  -- 1. Keep RLS enabled on all child partition tables.
  -- 2. If direct queries on child partitions are legitimately required by an external driver,
  --    attach an explicit tenant isolation policy rather than disabling RLS.
  -- 3. Grant minimal necessary DML (SELECT, INSERT) to authenticated role WITH tenant isolation.
  FOREACH v_table IN ARRAY v_expected_tables
  LOOP
    -- Ensure RLS remains enabled
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_table);

    -- Check if tenant isolation policy already exists on child table
    SELECT EXISTS (
      SELECT 1 FROM pg_policies 
      WHERE schemaname || '.' || tablename = v_table 
        AND policyname = 'p_child_tenant_isolation'
    ) INTO v_policy_exists;

    IF NOT v_policy_exists THEN
      EXECUTE format(
        'CREATE POLICY p_child_tenant_isolation ON %s ' ||
        'FOR ALL TO authenticated ' ||
        'USING (tenant_id = core.current_tenant_id()) ' ||
        'WITH CHECK (tenant_id = core.current_tenant_id())',
        v_table
      );
    END IF;

    -- Grant access bound by the RLS policy
    EXECUTE format('GRANT SELECT, INSERT ON TABLE %s TO authenticated', v_table);
  END LOOP;

  RAISE NOTICE 'Safe recovery completed: Child tables remain protected by RLS with explicit tenant isolation policies.';
END $$;
