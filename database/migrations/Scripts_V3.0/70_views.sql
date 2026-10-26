-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.

create or replace view hrm.v_leave_balance with (security_invoker = true) as
select l.tenant_id, l.employee_id, l.leave_type_id, extract(year from l.effective_date)::int as calendar_year, sum(l.days) as balance_days
from hrm.leave_ledger l group by l.tenant_id, l.employee_id, l.leave_type_id, extract(year from l.effective_date);
comment on view hrm.v_leave_balance is 'Leave balance = sum of ledger (replaces employee_leave_balances)';
create or replace view hrm.v_attendance_monthly with (security_invoker = true) as
select d.tenant_id, d.employee_id, date_trunc('month', d.attendance_date)::date as month,
  count(*) filter (where d.status in ('PRESENT','ON_DUTY')) as present_days,
  count(*) filter (where d.status = 'HALF_DAY') as half_days,
  count(*) filter (where d.status = 'ABSENT') as absent_days,
  count(*) filter (where d.status = 'LEAVE') as leave_days,
  count(*) filter (where d.status = 'LOP') as lop_days,
  count(*) filter (where d.is_late) as late_marks,
  coalesce(sum(d.overtime_minutes),0) as overtime_minutes
from hrm.attendance_days d group by d.tenant_id, d.employee_id, date_trunc('month', d.attendance_date);
comment on view hrm.v_attendance_monthly is 'Monthly attendance summary (replaces attendance_monthly_summaries)';
create or replace view acd.v_attendance_summary with (security_invoker = true) as
select r.tenant_id, s.section_id, r.student_id,
  count(*) as sessions_recorded,
  count(*) filter (where r.status in ('PRESENT','LATE','ON_DUTY')) as attended,
  round(100.0 * count(*) filter (where r.status in ('PRESENT','LATE','ON_DUTY')) / nullif(count(*),0), 2) as attendance_pct
from acd.attendance_records r join acd.attendance_sessions s on s.id = r.session_id and s.tenant_id = r.tenant_id
group by r.tenant_id, s.section_id, r.student_id;
comment on view acd.v_attendance_summary is 'Student attendance % per section (replaces attendance_summaries)';
create or replace view lms.v_lesson_variance with (security_invoker = true) as
select p.tenant_id, p.id as plan_id, p.employee_id,
  count(ls.id) as planned_sessions,
  count(ls.id) filter (where ls.status = 'DELIVERED') as delivered_sessions,
  count(ls.id) filter (where ls.status in ('MISSED','RESCHEDULED')) as slipped_sessions,
  round(avg(d.coverage_pct),2) as avg_coverage_pct
from lms.lesson_plans p left join lms.lesson_sessions ls on ls.plan_id = p.id and ls.tenant_id = p.tenant_id
left join lms.lesson_deliveries d on d.session_id = ls.id and d.tenant_id = ls.tenant_id
group by p.tenant_id, p.id, p.employee_id;
comment on view lms.v_lesson_variance is 'Planned vs delivered lessons (replaces lesson_variances)';
create or replace view fin.v_student_balance with (security_invoker = true) as
select o.tenant_id, o.student_id, sum(o.net_amount) as total_due, sum(o.paid_amount) as total_paid,
  sum(o.net_amount - o.paid_amount) as balance,
  sum(o.net_amount - o.paid_amount) filter (where o.status = 'OVERDUE') as overdue
from fin.fee_obligations o where o.status not in ('CANCELLED','WAIVED') group by o.tenant_id, o.student_id;
comment on view fin.v_student_balance is 'Outstanding fee position per student';
create or replace view bud.v_variance with (security_invoker = true) as
select l.tenant_id, l.id as budget_line_id, l.budget_version_id, l.department_id, l.category_id, l.planned_amount,
  l.allocated_amount, l.committed_amount, l.actual_amount,
  l.allocated_amount - l.committed_amount - l.actual_amount as available,
  l.planned_amount - l.actual_amount as variance,
  round(100.0 * l.actual_amount / nullif(l.planned_amount,0), 2) as utilisation_pct
from bud.budget_lines l;
comment on view bud.v_variance is 'Budget variance (replaces budget_variances)';
create or replace view tax.v_monthly_tax_sheet with (security_invoker = true) as
select t.tenant_id, p.id as period_id, date_trunc('month', t.txn_date)::date as month, t.tax_code_id, t.direction,
  sum(t.taxable_amount) as taxable_amount, sum(t.tax_amount) as tax_amount
from tax.tax_transactions t left join tax.tax_periods p on p.id = t.period_id and p.tenant_id = t.tenant_id
group by t.tenant_id, p.id, date_trunc('month', t.txn_date), t.tax_code_id, t.direction;
comment on view tax.v_monthly_tax_sheet is 'Replaces monthly_tax_sheets / yearly_tax_sheets';
create or replace view cam.v_delivery_metrics with (security_invoker = true) as
select d.tenant_id, d.channel, date_trunc('day', d.created_at)::date as day, count(*) as total,
  count(*) filter (where d.status in ('SENT','DELIVERED','READ')) as sent,
  count(*) filter (where d.status in ('DELIVERED','READ')) as delivered,
  count(*) filter (where d.status = 'READ') as read,
  count(*) filter (where d.status in ('FAILED','BOUNCED')) as failed
from cam.deliveries d group by d.tenant_id, d.channel, date_trunc('day', d.created_at);
comment on view cam.v_delivery_metrics is 'Replaces communication/delivery/channel metrics collections';
create or replace view cam.v_survey_results with (security_invoker = true) as
select a.tenant_id, r.survey_id, a.question_id, count(*) as responses, avg(a.answer_value) as avg_value
from cam.survey_answers a join cam.survey_responses r on r.id = a.response_id and r.tenant_id = a.tenant_id
where r.status = 'SUBMITTED' group by a.tenant_id, r.survey_id, a.question_id;
comment on view cam.v_survey_results is 'Replaces survey_results';
create or replace view cfg.v_setting_history with (security_invoker = true) as
select c.tenant_id, c.record_id, c.action, c.changed_fields, c.old_data, c.new_data, c.actor_id, c.created_at
from audit.change_log c where c.table_schema = 'cfg' and c.table_name = 'setting_values';
comment on view cfg.v_setting_history is 'Setting change history (replaces configuration_versions)';
create or replace view audit.v_change_log with (security_invoker = false) as
select c.id, c.tenant_id, c.table_schema, c.table_name, c.record_id, c.action, c.changed_fields, c.actor_id, c.request_id, c.created_at
from audit.change_log c
where c.tenant_id = core.current_tenant_id()
  and (iam.has_permission('audit.read') or (iam.has_permission('audit.read.own') and c.actor_id = auth.uid()));
comment on view audit.v_change_log is 'Audit trail WITHOUT before/after payloads (who changed what, when). Payloads need audit.read.payload on audit.change_log itself';
create or replace view stu.v_roster with (security_invoker = false) as
select s.id as student_id, s.enrollment_no, p.display_name, s.program_id, s.status, se.section_id
from acd.section_enrollments se
join stu.students s on s.id = se.student_id and s.tenant_id = se.tenant_id
join core.persons p on p.id = s.person_id and p.tenant_id = s.tenant_id
where se.tenant_id = core.current_tenant_id() and se.deleted_at is null and se.status = 'ENROLLED'
  and iam.has_permission('acd06.view.own')
  and se.student_id in (select core.faculty_student_ids());
comment on view stu.v_roster is 'Faculty roster: only students enrolled in sections the caller teaches; identity fields only (no contact details)';
create or replace view fin.v_student_lookup with (security_invoker = false) as
select s.id as student_id, s.enrollment_no, p.display_name, s.program_id, s.college_id, s.status
from stu.students s join core.persons p on p.id = s.person_id and p.tenant_id = s.tenant_id
where s.tenant_id = core.current_tenant_id() and s.deleted_at is null and iam.has_permission('fin02.view')
  and (s.college_id is null or core.can_access_college(s.college_id));
comment on view fin.v_student_lookup is 'Finance: student identity needed to bill/collect, without the rest of the student record or person PII';
create or replace view core.v_person_directory with (security_invoker = false) as
select p.id, p.tenant_id, p.display_name, p.status
from core.persons p
where p.tenant_id = core.current_tenant_id() and p.deleted_at is null
  and (iam.has_permission('core.directory.view') or iam.has_permission('core.pii.read'));
comment on view core.v_person_directory is 'Names only. Staff who need to pick a person (messaging, exams, HR) use this; contact details and IDs need core.pii.read on core.persons';
