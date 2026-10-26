-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.
-- Data API roles get table privileges; RLS is what actually restricts rows.
-- Functions: EXECUTE is revoked from PUBLIC/anon/authenticated everywhere, then granted one function at a time.

grant usage on schema core to authenticated, service_role;
revoke all on all tables in schema core from anon;
grant select, insert, update, delete on all tables in schema core to authenticated;
grant all on all tables in schema core to service_role;
alter default privileges in schema core grant select, insert, update, delete on tables to authenticated;
grant usage on schema stu to authenticated, service_role;
revoke all on all tables in schema stu from anon;
grant select, insert, update, delete on all tables in schema stu to authenticated;
grant all on all tables in schema stu to service_role;
alter default privileges in schema stu grant select, insert, update, delete on tables to authenticated;
grant usage on schema iam to authenticated, service_role;
revoke all on all tables in schema iam from anon;
grant select, insert, update, delete on all tables in schema iam to authenticated;
grant all on all tables in schema iam to service_role;
alter default privileges in schema iam grant select, insert, update, delete on tables to authenticated;
grant usage on schema cfg to authenticated, service_role;
revoke all on all tables in schema cfg from anon;
grant select, insert, update, delete on all tables in schema cfg to authenticated;
grant all on all tables in schema cfg to service_role;
alter default privileges in schema cfg grant select, insert, update, delete on tables to authenticated;
grant usage on schema plat to authenticated, service_role;
revoke all on all tables in schema plat from anon;
grant select, insert, update, delete on all tables in schema plat to authenticated;
grant all on all tables in schema plat to service_role;
alter default privileges in schema plat grant select, insert, update, delete on tables to authenticated;
grant usage on schema wf to authenticated, service_role;
revoke all on all tables in schema wf from anon;
grant select, insert, update, delete on all tables in schema wf to authenticated;
grant all on all tables in schema wf to service_role;
alter default privileges in schema wf grant select, insert, update, delete on tables to authenticated;
grant usage on schema doc to authenticated, service_role;
revoke all on all tables in schema doc from anon;
grant select, insert, update, delete on all tables in schema doc to authenticated;
grant all on all tables in schema doc to service_role;
alter default privileges in schema doc grant select, insert, update, delete on tables to authenticated;
grant usage on schema audit to authenticated, service_role;
revoke all on all tables in schema audit from anon;
grant select, insert, update, delete on all tables in schema audit to authenticated;
grant all on all tables in schema audit to service_role;
alter default privileges in schema audit grant select, insert, update, delete on tables to authenticated;
grant usage on schema acd to authenticated, service_role;
revoke all on all tables in schema acd from anon;
grant select, insert, update, delete on all tables in schema acd to authenticated;
grant all on all tables in schema acd to service_role;
alter default privileges in schema acd grant select, insert, update, delete on tables to authenticated;
grant usage on schema lms to authenticated, service_role;
revoke all on all tables in schema lms from anon;
grant select, insert, update, delete on all tables in schema lms to authenticated;
grant all on all tables in schema lms to service_role;
alter default privileges in schema lms grant select, insert, update, delete on tables to authenticated;
grant usage on schema obe to authenticated, service_role;
revoke all on all tables in schema obe from anon;
grant select, insert, update, delete on all tables in schema obe to authenticated;
grant all on all tables in schema obe to service_role;
alter default privileges in schema obe grant select, insert, update, delete on tables to authenticated;
grant usage on schema exm to authenticated, service_role;
revoke all on all tables in schema exm from anon;
grant select, insert, update, delete on all tables in schema exm to authenticated;
grant all on all tables in schema exm to service_role;
alter default privileges in schema exm grant select, insert, update, delete on tables to authenticated;
grant usage on schema fin to authenticated, service_role;
revoke all on all tables in schema fin from anon;
grant select, insert, update, delete on all tables in schema fin to authenticated;
grant all on all tables in schema fin to service_role;
alter default privileges in schema fin grant select, insert, update, delete on tables to authenticated;
grant usage on schema tax to authenticated, service_role;
revoke all on all tables in schema tax from anon;
grant select, insert, update, delete on all tables in schema tax to authenticated;
grant all on all tables in schema tax to service_role;
alter default privileges in schema tax grant select, insert, update, delete on tables to authenticated;
grant usage on schema bud to authenticated, service_role;
revoke all on all tables in schema bud from anon;
grant select, insert, update, delete on all tables in schema bud to authenticated;
grant all on all tables in schema bud to service_role;
alter default privileges in schema bud grant select, insert, update, delete on tables to authenticated;
grant usage on schema pay to authenticated, service_role;
revoke all on all tables in schema pay from anon;
grant select, insert, update, delete on all tables in schema pay to authenticated;
grant all on all tables in schema pay to service_role;
alter default privileges in schema pay grant select, insert, update, delete on tables to authenticated;
grant usage on schema hrm to authenticated, service_role;
revoke all on all tables in schema hrm from anon;
grant select, insert, update, delete on all tables in schema hrm to authenticated;
grant all on all tables in schema hrm to service_role;
alter default privileges in schema hrm grant select, insert, update, delete on tables to authenticated;
grant usage on schema cam to authenticated, service_role;
revoke all on all tables in schema cam from anon;
grant select, insert, update, delete on all tables in schema cam to authenticated;
grant all on all tables in schema cam to service_role;
alter default privileges in schema cam grant select, insert, update, delete on tables to authenticated;
grant usage on schema rpt to authenticated, service_role;
revoke all on all tables in schema rpt from anon;
grant select, insert, update, delete on all tables in schema rpt to authenticated;
grant all on all tables in schema rpt to service_role;
alter default privileges in schema rpt grant select, insert, update, delete on tables to authenticated;
grant usage on schema sys to service_role; grant all on all tables in schema sys to service_role;
revoke all on audit.change_log_default from anon, authenticated;
revoke all on audit.events_default from anon, authenticated;
revoke all on audit.access_events_default from anon, authenticated;
revoke all on lms.progress_events_default from anon, authenticated;
revoke all on hrm.attendance_punches_default from anon, authenticated;
revoke all on cam.delivery_events_default from anon, authenticated;
revoke insert, update, delete, truncate on audit.change_log from authenticated;
revoke insert, update, delete, truncate on audit.events from authenticated;
revoke insert, update, delete, truncate on audit.access_events from authenticated;
revoke insert, update, delete, truncate on audit.integrity_records from authenticated;
revoke insert, update, delete, truncate on audit.field_policies from authenticated;
revoke insert, update, delete, truncate on audit.field_policies from authenticated;

-- ---- functions: default deny, then explicit allow-list
do $$ declare s text; begin
  foreach s in array array['core', 'stu', 'iam', 'cfg', 'plat', 'wf', 'doc', 'sys', 'audit', 'acd', 'lms', 'obe', 'exm', 'fin', 'tax', 'bud', 'pay', 'hrm', 'cam', 'rpt'] loop
    execute format('revoke execute on all functions in schema %I from public, anon, authenticated', s);
    execute format('grant execute on all functions in schema %I to service_role', s);
  end loop;
end $$;
-- helpers that RLS policies / views / the API legitimately call as the signed-in user (each checks tenant + permission internally)
grant execute on function
  core.current_tenant_id(), core.current_person_id(), core.current_student_id(), core.current_employee_id(),
  core.can_access_college(uuid), core.faculty_student_ids(), core.own_attendance_session_ids(),
  core.faculty_section_ids(), core.faculty_course_ids(), core.faculty_subject_ids(), core.my_student_ids(), core.my_section_ids(),
  core.my_course_ids(), core.my_subject_ids(), core.valuator_script_ids(),
  core.programs_in_scope(text, boolean), core.courses_in_scope(text, boolean), core.subjects_in_scope(text, boolean),
  core.sections_in_scope(text, boolean), core.students_in_scope(text, boolean), core.employees_in_scope(text, boolean),
  iam.has_permission(text), iam.perm_all(text), iam.perm_colleges(text), iam.perm_department_ids(text), iam.has_permission_in(text, uuid, uuid),
  iam.grant_scopes(text), iam.is_platform_admin(), iam.has_institute_scope(), iam.can_grant_role(uuid), iam.is_privileged_session(),
  core.next_number(text, uuid), cfg.effective_setting(text, uuid),
  fin.allocate_payment(uuid, uuid, numeric, uuid), fin.reverse_allocation(uuid, text), fin.start_payment(uuid, uuid, numeric)
to authenticated;
-- trusted-only: iam.seed_system_roles, audit.log_event, sys.ensure_partitions, iam.custom_access_token_hook -> service_role / supabase_auth_admin only

-- generated scope helpers
grant execute on function iam.sc_core_calendars(text, boolean), iam.sc_doc_documents(text, boolean), iam.sc_core_persons(text, boolean), iam.sc_iam_roles(text, boolean), iam.sc_iam_user_profiles(text, boolean), iam.sc_iam_access_reviews(text, boolean), iam.sc_wf_definitions(text, boolean), iam.sc_wf_instances(text, boolean), iam.sc_wf_tasks(text, boolean), iam.sc_doc_templates(text, boolean), iam.sc_doc_print_jobs(text, boolean), iam.sc_sys_import_jobs(text, boolean), iam.sc_audit_cases(text, boolean), iam.sc_core_campuses(text, boolean), iam.sc_acd_curricula(text, boolean), iam.sc_acd_batches(text, boolean), iam.sc_acd_course_offerings(text, boolean), iam.sc_acd_timetables(text, boolean), iam.sc_acd_timetable_versions(text, boolean), core.own_acd_attendance_sessions(), iam.sc_acd_attendance_records(text, boolean), core.own_acd_attendance_records(), iam.sc_acd_assessment_structures(text, boolean), iam.sc_lms_contents(text, boolean), core.own_lms_contents(), core.vis_lms_contents(), iam.sc_lms_content_modules(text, boolean), core.own_lms_content_modules(), core.vis_lms_content_modules(), iam.sc_lms_content_topics(text, boolean), core.own_lms_content_topics(), core.vis_lms_content_topics(), iam.sc_lms_resources(text, boolean), core.own_lms_resources(), iam.sc_hrm_employees(text, boolean), iam.sc_lms_resource_collections(text, boolean), core.own_lms_resource_collections(), iam.sc_lms_lesson_plans(text, boolean), core.own_lms_lesson_plans(), core.vis_lms_lesson_plans(), iam.sc_lms_lesson_plan_modules(text, boolean), core.own_lms_lesson_plan_modules(), core.vis_lms_lesson_plan_modules(), iam.sc_lms_lesson_sessions(text, boolean), core.own_lms_lesson_sessions(), iam.sc_lms_lesson_plan_topics(text, boolean), core.own_lms_lesson_plan_topics(), iam.sc_lms_rubrics(text, boolean), core.own_lms_rubrics(), iam.sc_lms_rubric_criteria(text, boolean), core.own_lms_rubric_criteria(), iam.sc_lms_assignments(text, boolean), core.own_lms_assignments(), core.vis_lms_assignments(), core.own_lms_student_assignments(), iam.sc_lms_submissions(text, boolean), core.own_lms_submissions(), iam.sc_lms_evaluations(text, boolean), core.own_lms_evaluations(), iam.sc_lms_student_assignments(text, boolean), iam.sc_lms_virtual_classrooms(text, boolean), core.own_lms_virtual_classrooms(), core.vis_lms_virtual_classrooms(), iam.sc_lms_virtual_sessions(text, boolean), core.own_lms_virtual_sessions(), core.vis_lms_virtual_sessions(), iam.sc_lms_session_polls(text, boolean), core.own_lms_session_polls(), iam.sc_lms_breakout_rooms(text, boolean), core.own_lms_breakout_rooms(), iam.sc_lms_certifications(text, boolean), iam.sc_lms_certification_applications(text, boolean), core.own_lms_certification_applications(), iam.sc_lms_eligibility_evaluations(text, boolean), core.own_lms_eligibility_evaluations(), iam.sc_lms_credentials(text, boolean), iam.sc_lms_badges(text, boolean), iam.sc_obe_outcomes(text, boolean), core.own_obe_outcomes(), iam.sc_obe_attainment_rules(text, boolean), core.own_obe_attainment_rules(), iam.sc_exm_registrations(text, boolean), iam.sc_exm_exam_centres(text, boolean), iam.sc_exm_examinations(text, boolean), iam.sc_exm_schedule_versions(text, boolean), iam.sc_exm_hall_tickets(text, boolean), iam.sc_exm_exam_subjects(text, boolean), iam.sc_exm_exam_sessions(text, boolean), iam.sc_exm_answer_scripts(text, boolean), core.own_exm_valuation_sessions(), iam.sc_exm_marks_entries(text, boolean), core.own_exm_marks_entries(), iam.sc_exm_script_packets(text, boolean), iam.sc_exm_results(text, boolean), iam.sc_acd_calendar_terms(text, boolean), iam.sc_fin_fee_structures(text, boolean), iam.sc_fin_fee_structure_versions(text, boolean), iam.sc_fin_fee_obligations(text, boolean), iam.sc_fin_payments(text, boolean), iam.sc_fin_receipts(text, boolean), iam.sc_fin_scholarship_schemes(text, boolean), iam.sc_fin_scholarship_applications(text, boolean), iam.sc_fin_scholarship_awards(text, boolean), iam.sc_fin_scholarship_award_components(text, boolean), iam.sc_fin_vendors(text, boolean), iam.sc_fin_expenses(text, boolean), iam.sc_fin_payment_requests(text, boolean), iam.sc_fin_disbursements(text, boolean), iam.sc_bud_budgets(text, boolean), iam.sc_bud_budget_lines(text, boolean), iam.sc_bud_budget_controls(text, boolean), iam.sc_bud_scenarios(text, boolean), iam.sc_pay_payroll_groups(text, boolean), iam.sc_pay_salary_structures(text, boolean), iam.sc_pay_salary_structure_versions(text, boolean), iam.sc_pay_employee_compensations(text, boolean), iam.sc_pay_payroll_periods(text, boolean), iam.sc_pay_payslips(text, boolean), iam.sc_pay_employee_loans(text, boolean), iam.sc_hrm_leave_applications(text, boolean), iam.sc_hrm_performance_plans(text, boolean), iam.sc_hrm_performance_goals(text, boolean), iam.sc_hrm_performance_reviews(text, boolean), iam.sc_hrm_requisitions(text, boolean), iam.sc_hrm_candidates(text, boolean), iam.sc_hrm_job_postings(text, boolean), iam.sc_hrm_candidate_applications(text, boolean), iam.sc_hrm_interviews(text, boolean), iam.sc_hrm_onboardings(text, boolean), iam.sc_hrm_training_sessions(text, boolean), iam.sc_hrm_training_assessments(text, boolean), iam.sc_hrm_training_enrollments(text, boolean), iam.sc_cam_audience_groups(text, boolean), core.own_cam_audience_groups(), iam.sc_cam_messages(text, boolean), core.own_cam_messages(), iam.sc_cam_devices(text, boolean), iam.sc_cam_deliveries(text, boolean), iam.sc_cam_notification_rules(text, boolean), iam.sc_cam_surveys(text, boolean), core.own_cam_surveys(), iam.sc_cam_survey_responses(text, boolean), iam.sc_cam_emergency_alerts(text, boolean), iam.sc_rpt_dashboards(text, boolean) to authenticated;
