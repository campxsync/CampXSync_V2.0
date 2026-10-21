-- ==============================================================================
-- CAMPXSYNC DATABASE MIGRATION
-- Migration: V20261020_01__complete_partition_security_hardening.sql
-- Description: Comprehensive Partition Security Hardening Across Parent, Default,
--              Existing Monthly, and Future Dynamic Partitions.
-- Target Architecture: PostgreSQL 15+ (Supabase)
-- Target Project (Dev): CampXSync-Architecture-Dev (ref: azulbaxszsqxymqmlwfe)
-- Governing Tables (6 Parents):
--   1. audit.access_events
--   2. audit.change_log
--   3. audit.events
--   4. cam.delivery_events
--   5. hrm.attendance_punches
--   6. lms.progress_events
-- ==============================================================================

DO $$
DECLARE
  v_parent text;
  v_default_table text;
  v_child text;
  v_parent_tables text[] := ARRAY[
    'audit.access_events',
    'audit.change_log',
    'audit.events',
    'cam.delivery_events',
    'hrm.attendance_punches',
    'lms.progress_events'
  ];
  v_default_tables text[] := ARRAY[
    'audit.access_events_default',
    'audit.change_log_default',
    'audit.events_default',
    'cam.delivery_events_default',
    'hrm.attendance_punches_default',
    'lms.progress_events_default'
  ];
BEGIN
  -- 1. Ensure Row-Level Security is enabled on all 6 parent partitioned tables
  FOREACH v_parent IN ARRAY v_parent_tables
  LOOP
    IF to_regclass(v_parent) IS NOT NULL THEN
      EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_parent);
    ELSE
      RAISE EXCEPTION 'Parent partitioned table % does not exist in catalog', v_parent;
    END IF;
  END LOOP;

  -- 2. Harden all 6 default partition tables: Enable RLS and revoke direct privileges
  FOREACH v_default_table IN ARRAY v_default_tables
  LOOP
    IF to_regclass(v_default_table) IS NOT NULL THEN
      EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_default_table);
      EXECUTE format('REVOKE ALL ON TABLE %s FROM anon, authenticated', v_default_table);
    END IF;
  END LOOP;

  -- 3. Dynamically discover and harden all existing monthly child partitions
  FOR v_child IN
    SELECT format('%I.%I', n.nspname, c.relname)
    FROM pg_inherits inh
    JOIN pg_class c ON c.oid = inh.inhrelid
    JOIN pg_class p ON p.oid = inh.inhparent
    JOIN pg_namespace n ON n.oid = c.relnamespace
    JOIN pg_namespace pn ON pn.oid = p.relnamespace
    WHERE format('%I.%I', pn.nspname, p.relname) = ANY(v_parent_tables)
      AND c.relname NOT LIKE '%_default'
  LOOP
    EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', v_child);
    EXECUTE format('REVOKE ALL ON TABLE %s FROM anon, authenticated', v_child);
  END LOOP;

  -- 4. Re-assert parent table DML grants for authenticated role to guarantee
  -- legitimate routing through parent tables for Java services and backend tasks
  EXECUTE 'GRANT SELECT, INSERT ON TABLE audit.access_events TO authenticated';
  EXECUTE 'GRANT SELECT, INSERT ON TABLE audit.change_log TO authenticated';
  EXECUTE 'GRANT SELECT, INSERT ON TABLE audit.events TO authenticated';
  EXECUTE 'GRANT SELECT, INSERT ON TABLE cam.delivery_events TO authenticated';
  EXECUTE 'GRANT SELECT, INSERT ON TABLE hrm.attendance_punches TO authenticated';
  EXECUTE 'GRANT SELECT, INSERT ON TABLE lms.progress_events TO authenticated';

  RAISE NOTICE 'Partition security hardening applied successfully across parents, defaults, and monthly children.';
END $$;

-- 5. Harden future partition creation: update sys.ensure_partitions()
-- Retains exact signature, UTC bounds, and month calculations while enforcing
-- ENABLE ROW LEVEL SECURITY and REVOKE ALL on every newly created partition.
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
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', nm);
        EXECUTE format('REVOKE ALL ON TABLE %s FROM anon, authenticated', nm);
      ELSE
        -- Ensure already existing partitions also have RLS enabled and grants revoked
        EXECUTE format('ALTER TABLE %s ENABLE ROW LEVEL SECURITY', nm);
        EXECUTE format('REVOKE ALL ON TABLE %s FROM anon, authenticated', nm);
      END IF;
    END LOOP;
  END LOOP;
END $$;
