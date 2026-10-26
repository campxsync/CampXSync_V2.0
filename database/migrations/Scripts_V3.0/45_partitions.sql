-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.
-- Monthly partitions for high-volume append-only tables. Run once at deploy, then monthly (pg_cron).
create or replace function sys.ensure_partitions(p_months_ahead int default 3) returns void
language plpgsql security definer set search_path = '' as $$
declare t text; m date; nm text; i int;
begin
  foreach t in array array['audit.change_log', 'audit.events', 'audit.access_events', 'lms.progress_events', 'hrm.attendance_punches', 'cam.delivery_events'] loop
    for i in 0..p_months_ahead loop
      m  := (date_trunc('month', now() at time zone 'UTC') + make_interval(months => i))::date;
      nm := format('%s_p%s', t, to_char(m, 'YYYYMM'));
      if to_regclass(nm) is null then
        execute format('create table %s partition of %s for values from (%L) to (%L)', nm, t,
                       (m::timestamp at time zone 'UTC'), ((m + interval '1 month')::timestamp at time zone 'UTC'));
        execute format('revoke all on %s from anon, authenticated', nm);   -- RLS lives on the parent; never expose partitions directly
      end if;
    end loop;
  end loop;
end $$;
select sys.ensure_partitions(3);   -- run BEFORE any audited row is written (rows already in the DEFAULT partition block new partitions)
-- select cron.schedule('ensure-partitions', '0 2 25 * *', $$select sys.ensure_partitions(3)$$);
