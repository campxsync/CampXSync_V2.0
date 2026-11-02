-- =============================================================================
-- 20261027000300_extend_partitions_and_schedule_maintenance.sql
-- Supabase CLI migration (forward). Rollback: supabase/rollbacks/20261027000300_rollback.sql
-- Requires 20261027000200 (hardened sys.ensure_partitions).
--
-- WHAT:
--  1. Pre-flight: aborts if any of the 6 _default partitions contains rows. (A monthly
--     partition cannot be attached while the default partition holds rows in its range.)
--  2. Creates monthly partitions for the current month + 12 months ahead on all 6
--     partitioned tables (dev had p202610..p202701 only; horizon ended 2027-02-01).
--  3. If pg_cron is available (it is on Supabase; it was available-but-not-installed on dev),
--     installs it and schedules sys.ensure_partitions(12) at 02:00 UTC on the 1st of each month.
--     If pg_cron is not available, a WARNING is raised and the migration still succeeds:
--     schedule `select sys.ensure_partitions(12);` externally every month.
-- NOTE: if CREATE EXTENSION is refused for lack of privilege, enable pg_cron once in the
--     Supabase dashboard (Integrations > Cron) and re-run `supabase db push`.
-- Idempotent: partitions are created only if missing; cron.schedule upserts by job name.
-- =============================================================================
begin;

do $$
declare t text; v_has boolean;
begin
  foreach t in array array['audit.change_log_default','audit.events_default','audit.access_events_default',
                           'lms.progress_events_default','hrm.attendance_punches_default','cam.delivery_events_default'] loop
    if to_regclass(t) is null then
      raise exception 'precondition failed: default partition % not found', t;
    end if;
    execute format('select exists (select 1 from %s)', t) into v_has;
    if v_has then
      raise exception 'precondition failed: % contains rows. Move them to a correctly-bounded partition first, then re-run.', t;
    end if;
  end loop;
end $$;

select sys.ensure_partitions(12);

do $$
declare t text; v_missing text;
begin
  foreach t in array array['audit.change_log','audit.events','audit.access_events',
                           'lms.progress_events','hrm.attendance_punches','cam.delivery_events'] loop
    if to_regclass(format('%s_p%s', t, to_char((date_trunc('month', now() at time zone 'UTC') + interval '12 months')::date, 'YYYYMM'))) is null then
      raise exception 'postcondition failed: 12-month-ahead partition missing for %', t;
    end if;
  end loop;
end $$;

do $$
begin
  if exists (select 1 from pg_available_extensions where name = 'pg_cron') then
    create extension if not exists pg_cron with schema pg_catalog;
    execute $q$select cron.schedule('campx-ensure-partitions', '0 2 1 * *', 'select sys.ensure_partitions(12)')$q$;
  else
    raise warning 'pg_cron is not available in this database. Schedule "select sys.ensure_partitions(12);" externally, monthly.';
  end if;
end $$;

commit;
