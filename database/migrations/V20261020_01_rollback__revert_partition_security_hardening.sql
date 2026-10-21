-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION ROLLBACK
-- Migration: V20261020_01_rollback__revert_partition_security_hardening.sql
-- Description: Gated recovery / rollback plan for partition security hardening.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Safety Policy:
--   1. NEVER blindly disable RLS or restore wide-open grants.
--   2. If direct child table access is strictly required by legacy or external tools,
--      attach an explicit tenant isolation policy (p_child_tenant_isolation) rather
--      than exposing partitions without tenant isolation.
--   3. Reverts sys.ensure_partitions() to baseline 45_partitions.sql behavior.
-- ==============================================================================

DO $$
DECLARE
  v_child text;
  v_parent_tables text[] := ARRAY[
    'audit.access_events',
    'audit.change_log',
    'audit.events',
    'cam.delivery_events',
    'hrm.attendance_punches',
    'lms.progress_events'
  ];
  v_policy_exists boolean;
BEGIN
  -- Safe rollback strategy for child and default partitions:
  -- If direct queries are legitimately demanded, maintain RLS with explicit tenant isolation
  FOR v_child IN
    SELECT format('%I.%I', n.nspname, c.relname)
    FROM pg_inherits inh
    JOIN pg_class c ON c.oid = inh.inhrelid
    JOIN pg_class p ON p.oid = inh.inhparent
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_namespace pn ON pn.oid = p.relnamespace
    WHERE format('%I.%I', pn.nspname, p.relname) = ANY(v_parent_tables)
  LOOP
    -- Ensure RLS remains enabled
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_child);

    -- Check if tenant isolation policy already exists on child table
    SELECT EXISTS (
      SELECT 1 FROM pg_policies 
      WHERE schemaname || '.' || tablename = v_child 
        AND policyname = 'p_child_tenant_isolation'
    ) INTO v_policy_exists;

    IF NOT v_policy_exists THEN
      EXECUTE format(
        'CREATE POLICY p_child_tenant_isolation ON %s ' ||
        'FOR ALL TO authenticated ' ||
        'USING (tenant_id = core.current_tenant_id()) ' ||
        'WITH CHECK (tenant_id = core.current_tenant_id())',
        v_child
      );
    END IF;

    -- Grant access bound by the tenant isolation policy
    EXECUTE format('GRANT SELECT, INSERT ON TABLE %s TO authenticated', v_child);
  END LOOP;

  RAISE NOTICE 'Rollback applied: child tables remain protected by RLS with explicit tenant isolation policies.';
END $$;

-- Revert sys.ensure_partitions() to original 45_partitions.sql behavior
CREATE OR REPLACE FUNCTION sys.ensure_partitions(p_months_ahead int default 3) RETURNS void
LANGUAGE plpgsql SECURITY DEFINER SET search_path = '' AS $$
DECLARE
  t text;
  m date;
  nm text;
  i int;
BEGIN
  FOREACH t IN ARRAY ARRAY[
    'audit.change_log',
    'audit.events',
    'audit.access_events',
    'lms.progress_events',
    'hrm.attendance_punches',
    'cam.delivery_events'
  ] LOOP
    FOR i IN 0..p_months_ahead LOOP
      m  := (date_trunc('month', now() AT TIME ZONE 'UTC') + make_interval(months => i))::date;
      nm := format('%s_p%s', t, to_char(m, 'YYYYMM'));
      IF to_regclass(nm) IS NULL THEN
        EXECUTE format('CREATE TABLE %s PARTITION OF %s FOR VALUES FROM (%L) TO (%L)', nm, t,
                       (m::timestamp AT TIME ZONE 'UTC'), ((m + interval '1 month')::timestamp AT TIME ZONE 'UTC'));
        EXECUTE format('REVOKE ALL ON %s FROM anon, authenticated', nm);
      END IF;
    END LOOP;
  END LOOP;
END $$;
