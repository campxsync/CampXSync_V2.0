-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.
-- Generated helper sets used by the RLS policies (definer; parents first).
create or replace function iam.sc_core_calendars(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from core.calendars t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_doc_documents(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from doc.documents t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_core_persons(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from core.persons t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.photo_document_id in (select iam.sc_doc_documents(p_perm, p_write)) or (not p_write and t.photo_document_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_iam_roles(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from iam.roles t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_iam_user_profiles(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from iam.user_profiles t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))) or (t.department_id in (select unnest(iam.perm_department_ids(p_perm))) or (not p_write and t.department_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_iam_access_reviews(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from iam.access_reviews t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.reviewer_id in (select iam.sc_iam_user_profiles(p_perm, p_write)) or (not p_write and t.reviewer_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_wf_definitions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from wf.definitions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_wf_instances(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from wf.instances t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_wf_tasks(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from wf.tasks t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_doc_templates(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from doc.templates t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_doc_print_jobs(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from doc.print_jobs t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.document_id in (select iam.sc_doc_documents(p_perm, p_write)) or (not p_write and t.document_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_sys_import_jobs(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from sys.import_jobs t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_audit_cases(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from audit.cases t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.opened_by in (select iam.sc_iam_user_profiles(p_perm, p_write)) or (not p_write and t.opened_by is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_core_campuses(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from core.campuses t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm)))))
$$;
create or replace function iam.sc_acd_curricula(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.curricula t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.program_id in (select core.programs_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_acd_batches(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.batches t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm)))) or (t.program_id in (select core.programs_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_acd_course_offerings(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.course_offerings t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm)))) or (t.course_id in (select core.courses_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_acd_timetables(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.timetables t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm)))))
$$;
create or replace function iam.sc_acd_timetable_versions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.timetable_versions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.timetable_id in (select iam.sc_acd_timetables(p_perm, p_write))))
$$;
create or replace function core.own_acd_attendance_sessions() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.attendance_sessions t where t.tenant_id = core.current_tenant_id() and (employee_id = (select core.current_employee_id()))
$$;
create or replace function iam.sc_acd_attendance_records(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.attendance_records t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function core.own_acd_attendance_records() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.attendance_records t where t.tenant_id = core.current_tenant_id() and session_id in (select core.own_acd_attendance_sessions())
$$;
create or replace function iam.sc_acd_assessment_structures(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.assessment_structures t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.subject_id in (select core.subjects_in_scope(p_perm, p_write)) or (not p_write and t.subject_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_lms_contents(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.contents t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.course_id in (select core.courses_in_scope(p_perm, p_write)) or (not p_write and t.course_id is null and (select iam.has_permission(p_perm)))) or (t.subject_id in (select core.subjects_in_scope(p_perm, p_write)) or (not p_write and t.subject_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_lms_contents() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.contents t where t.tenant_id = core.current_tenant_id() and (owner_employee_id = (select core.current_employee_id()) or course_id in (select core.faculty_course_ids()) or subject_id in (select core.faculty_subject_ids()))
$$;
create or replace function core.vis_lms_contents() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.contents t where t.tenant_id = core.current_tenant_id() and (status = 'PUBLISHED' and (course_id in (select core.my_course_ids()) or subject_id in (select core.my_subject_ids())))
$$;
create or replace function iam.sc_lms_content_modules(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.content_modules t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.content_id in (select iam.sc_lms_contents(p_perm, p_write))))
$$;
create or replace function core.own_lms_content_modules() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.content_modules t where t.tenant_id = core.current_tenant_id() and content_id in (select core.own_lms_contents())
$$;
create or replace function core.vis_lms_content_modules() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.content_modules t where t.tenant_id = core.current_tenant_id() and content_id in (select core.vis_lms_contents())
$$;
create or replace function iam.sc_lms_content_topics(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.content_topics t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.module_id in (select iam.sc_lms_content_modules(p_perm, p_write))))
$$;
create or replace function core.own_lms_content_topics() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.content_topics t where t.tenant_id = core.current_tenant_id() and module_id in (select core.own_lms_content_modules())
$$;
create or replace function core.vis_lms_content_topics() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.content_topics t where t.tenant_id = core.current_tenant_id() and module_id in (select core.vis_lms_content_modules())
$$;
create or replace function iam.sc_lms_resources(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.resources t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_lms_resources() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.resources t where t.tenant_id = core.current_tenant_id() and (owner_employee_id = (select core.current_employee_id()))
$$;
create or replace function iam.sc_hrm_employees(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.employees t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm)))) or (t.department_id in (select unnest(iam.perm_department_ids(p_perm))) or (not p_write and t.department_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_lms_resource_collections(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.resource_collections t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.owner_employee_id in (select iam.sc_hrm_employees(p_perm, p_write)) or (not p_write and t.owner_employee_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_lms_resource_collections() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.resource_collections t where t.tenant_id = core.current_tenant_id() and (owner_employee_id = (select core.current_employee_id()))
$$;
create or replace function iam.sc_lms_lesson_plans(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plans t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.section_id in (select core.sections_in_scope(p_perm, p_write)) or (not p_write and t.section_id is null and (select iam.has_permission(p_perm)))) or (t.subject_id in (select core.subjects_in_scope(p_perm, p_write)) or (not p_write and t.subject_id is null and (select iam.has_permission(p_perm)))) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write))))
$$;
create or replace function core.own_lms_lesson_plans() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plans t where t.tenant_id = core.current_tenant_id() and (employee_id = (select core.current_employee_id()) or section_id in (select core.faculty_section_ids()))
$$;
create or replace function core.vis_lms_lesson_plans() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plans t where t.tenant_id = core.current_tenant_id() and (status in ('APPROVED','COMPLETED') and section_id in (select core.my_section_ids()))
$$;
create or replace function iam.sc_lms_lesson_plan_modules(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plan_modules t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.plan_id in (select iam.sc_lms_lesson_plans(p_perm, p_write))))
$$;
create or replace function core.own_lms_lesson_plan_modules() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plan_modules t where t.tenant_id = core.current_tenant_id() and plan_id in (select core.own_lms_lesson_plans())
$$;
create or replace function core.vis_lms_lesson_plan_modules() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plan_modules t where t.tenant_id = core.current_tenant_id() and plan_id in (select core.vis_lms_lesson_plans())
$$;
create or replace function iam.sc_lms_lesson_sessions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_sessions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.plan_id in (select iam.sc_lms_lesson_plans(p_perm, p_write))))
$$;
create or replace function core.own_lms_lesson_sessions() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_sessions t where t.tenant_id = core.current_tenant_id() and plan_id in (select core.own_lms_lesson_plans())
$$;
create or replace function iam.sc_lms_lesson_plan_topics(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plan_topics t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.module_id in (select iam.sc_lms_lesson_plan_modules(p_perm, p_write))))
$$;
create or replace function core.own_lms_lesson_plan_topics() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.lesson_plan_topics t where t.tenant_id = core.current_tenant_id() and module_id in (select core.own_lms_lesson_plan_modules())
$$;
create or replace function iam.sc_lms_rubrics(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.rubrics t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.owner_employee_id in (select iam.sc_hrm_employees(p_perm, p_write)) or (not p_write and t.owner_employee_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_lms_rubrics() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.rubrics t where t.tenant_id = core.current_tenant_id() and (owner_employee_id = (select core.current_employee_id()))
$$;
create or replace function iam.sc_lms_rubric_criteria(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.rubric_criteria t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.rubric_id in (select iam.sc_lms_rubrics(p_perm, p_write))))
$$;
create or replace function core.own_lms_rubric_criteria() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.rubric_criteria t where t.tenant_id = core.current_tenant_id() and rubric_id in (select core.own_lms_rubrics())
$$;
create or replace function iam.sc_lms_assignments(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.assignments t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.section_id in (select core.sections_in_scope(p_perm, p_write)) or (not p_write and t.section_id is null and (select iam.has_permission(p_perm)))) or (t.course_id in (select core.courses_in_scope(p_perm, p_write)) or (not p_write and t.course_id is null and (select iam.has_permission(p_perm)))) or (t.subject_id in (select core.subjects_in_scope(p_perm, p_write)) or (not p_write and t.subject_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_lms_assignments() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.assignments t where t.tenant_id = core.current_tenant_id() and (created_by_employee_id = (select core.current_employee_id()) or section_id in (select core.faculty_section_ids()) or course_id in (select core.faculty_course_ids()))
$$;
create or replace function core.vis_lms_assignments() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.assignments t where t.tenant_id = core.current_tenant_id() and (status in ('PUBLISHED','CLOSED') and (section_id in (select core.my_section_ids()) or course_id in (select core.my_course_ids()) or id in (select assignment_id from lms.student_assignments where student_id in (select core.my_student_ids()))))
$$;
create or replace function core.own_lms_student_assignments() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.student_assignments t where t.tenant_id = core.current_tenant_id() and assignment_id in (select core.own_lms_assignments())
$$;
create or replace function iam.sc_lms_submissions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.submissions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function core.own_lms_submissions() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.submissions t where t.tenant_id = core.current_tenant_id() and student_assignment_id in (select core.own_lms_student_assignments())
$$;
create or replace function iam.sc_lms_evaluations(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.evaluations t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.submission_id in (select iam.sc_lms_submissions(p_perm, p_write))))
$$;
create or replace function core.own_lms_evaluations() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.evaluations t where t.tenant_id = core.current_tenant_id() and submission_id in (select core.own_lms_submissions())
$$;
create or replace function iam.sc_lms_student_assignments(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.student_assignments t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_lms_virtual_classrooms(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.virtual_classrooms t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.section_id in (select core.sections_in_scope(p_perm, p_write)) or (not p_write and t.section_id is null and (select iam.has_permission(p_perm)))) or (t.course_id in (select core.courses_in_scope(p_perm, p_write)) or (not p_write and t.course_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_lms_virtual_classrooms() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.virtual_classrooms t where t.tenant_id = core.current_tenant_id() and (owner_employee_id = (select core.current_employee_id()) or section_id in (select core.faculty_section_ids()))
$$;
create or replace function core.vis_lms_virtual_classrooms() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.virtual_classrooms t where t.tenant_id = core.current_tenant_id() and (section_id in (select core.my_section_ids()))
$$;
create or replace function iam.sc_lms_virtual_sessions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.virtual_sessions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.classroom_id in (select iam.sc_lms_virtual_classrooms(p_perm, p_write))))
$$;
create or replace function core.own_lms_virtual_sessions() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.virtual_sessions t where t.tenant_id = core.current_tenant_id() and classroom_id in (select core.own_lms_virtual_classrooms())
$$;
create or replace function core.vis_lms_virtual_sessions() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.virtual_sessions t where t.tenant_id = core.current_tenant_id() and classroom_id in (select core.vis_lms_virtual_classrooms())
$$;
create or replace function iam.sc_lms_session_polls(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.session_polls t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.session_id in (select iam.sc_lms_virtual_sessions(p_perm, p_write))))
$$;
create or replace function core.own_lms_session_polls() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.session_polls t where t.tenant_id = core.current_tenant_id() and session_id in (select core.own_lms_virtual_sessions())
$$;
create or replace function iam.sc_lms_breakout_rooms(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.breakout_rooms t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.session_id in (select iam.sc_lms_virtual_sessions(p_perm, p_write))))
$$;
create or replace function core.own_lms_breakout_rooms() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.breakout_rooms t where t.tenant_id = core.current_tenant_id() and session_id in (select core.own_lms_virtual_sessions())
$$;
create or replace function iam.sc_lms_certifications(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.certifications t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.program_id in (select core.programs_in_scope(p_perm, p_write)) or (not p_write and t.program_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_lms_certification_applications(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.certification_applications t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function core.own_lms_certification_applications() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.certification_applications t where t.tenant_id = core.current_tenant_id() and (student_id in (select core.faculty_student_ids()))
$$;
create or replace function iam.sc_lms_eligibility_evaluations(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.eligibility_evaluations t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.application_id in (select iam.sc_lms_certification_applications(p_perm, p_write))))
$$;
create or replace function core.own_lms_eligibility_evaluations() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.eligibility_evaluations t where t.tenant_id = core.current_tenant_id() and application_id in (select core.own_lms_certification_applications())
$$;
create or replace function iam.sc_lms_credentials(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.credentials t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_lms_badges(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from lms.badges t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.icon_document_id in (select iam.sc_doc_documents(p_perm, p_write)) or (not p_write and t.icon_document_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_obe_outcomes(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from obe.outcomes t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.program_id in (select core.programs_in_scope(p_perm, p_write)) or (not p_write and t.program_id is null and (select iam.has_permission(p_perm)))) or (t.subject_id in (select core.subjects_in_scope(p_perm, p_write)) or (not p_write and t.subject_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_obe_outcomes() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from obe.outcomes t where t.tenant_id = core.current_tenant_id() and (subject_id in (select core.faculty_subject_ids()))
$$;
create or replace function iam.sc_obe_attainment_rules(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from obe.attainment_rules t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.program_id in (select core.programs_in_scope(p_perm, p_write)) or (not p_write and t.program_id is null and (select iam.has_permission(p_perm)))) or (t.subject_id in (select core.subjects_in_scope(p_perm, p_write)) or (not p_write and t.subject_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_obe_attainment_rules() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from obe.attainment_rules t where t.tenant_id = core.current_tenant_id() and (subject_id in (select core.faculty_subject_ids()))
$$;
create or replace function iam.sc_exm_registrations(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.registrations t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_exm_exam_centres(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.exam_centres t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_exm_examinations(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.examinations t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm)))))
$$;
create or replace function iam.sc_exm_schedule_versions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.schedule_versions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.examination_id in (select iam.sc_exm_examinations(p_perm, p_write))))
$$;
create or replace function iam.sc_exm_hall_tickets(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.hall_tickets t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_exm_exam_subjects(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.exam_subjects t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.subject_id in (select core.subjects_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_exm_exam_sessions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.exam_sessions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.examination_id in (select iam.sc_exm_examinations(p_perm, p_write))))
$$;
create or replace function iam.sc_exm_answer_scripts(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.answer_scripts t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.exam_session_id in (select iam.sc_exm_exam_sessions(p_perm, p_write))))
$$;
create or replace function core.own_exm_valuation_sessions() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.valuation_sessions t where t.tenant_id = core.current_tenant_id() and (employee_id = (select core.current_employee_id()))
$$;
create or replace function iam.sc_exm_marks_entries(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.marks_entries t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.answer_script_id in (select iam.sc_exm_answer_scripts(p_perm, p_write))))
$$;
create or replace function core.own_exm_marks_entries() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.marks_entries t where t.tenant_id = core.current_tenant_id() and valuation_session_id in (select core.own_exm_valuation_sessions())
$$;
create or replace function iam.sc_exm_script_packets(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.script_packets t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.exam_session_id in (select iam.sc_exm_exam_sessions(p_perm, p_write))))
$$;
create or replace function iam.sc_exm_results(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from exm.results t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_acd_calendar_terms(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from acd.calendar_terms t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.calendar_id in (select iam.sc_core_calendars(p_perm, p_write))))
$$;
create or replace function iam.sc_fin_fee_structures(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.fee_structures t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm)))))
$$;
create or replace function iam.sc_fin_fee_structure_versions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.fee_structure_versions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.structure_id in (select iam.sc_fin_fee_structures(p_perm, p_write))))
$$;
create or replace function iam.sc_fin_fee_obligations(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.fee_obligations t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_fin_payments(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.payments t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_fin_receipts(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.receipts t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write)) or (not p_write and t.student_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_fin_scholarship_schemes(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.scholarship_schemes t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_fin_scholarship_applications(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.scholarship_applications t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_fin_scholarship_awards(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.scholarship_awards t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.student_id in (select core.students_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_fin_scholarship_award_components(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.scholarship_award_components t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.award_id in (select iam.sc_fin_scholarship_awards(p_perm, p_write))))
$$;
create or replace function iam.sc_fin_vendors(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.vendors t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.contact_person_id in (select iam.sc_core_persons(p_perm, p_write)) or (not p_write and t.contact_person_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_fin_expenses(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.expenses t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))) or (t.department_id in (select unnest(iam.perm_department_ids(p_perm))) or (not p_write and t.department_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_fin_payment_requests(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.payment_requests t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.workflow_instance_id in (select iam.sc_wf_instances(p_perm, p_write)) or (not p_write and t.workflow_instance_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_fin_disbursements(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from fin.disbursements t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.payment_request_id in (select iam.sc_fin_payment_requests(p_perm, p_write)) or (not p_write and t.payment_request_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_bud_budgets(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from bud.budgets t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_bud_budget_lines(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from bud.budget_lines t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.department_id in (select unnest(iam.perm_department_ids(p_perm))) or (not p_write and t.department_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_bud_budget_controls(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from bud.budget_controls t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.budget_id in (select iam.sc_bud_budgets(p_perm, p_write)) or (not p_write and t.budget_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_bud_scenarios(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from bud.scenarios t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.budget_id in (select iam.sc_bud_budgets(p_perm, p_write)) or (not p_write and t.budget_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_pay_payroll_groups(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from pay.payroll_groups t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_pay_salary_structures(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from pay.salary_structures t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.payroll_group_id in (select iam.sc_pay_payroll_groups(p_perm, p_write)) or (not p_write and t.payroll_group_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_pay_salary_structure_versions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from pay.salary_structure_versions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.structure_id in (select iam.sc_pay_salary_structures(p_perm, p_write))))
$$;
create or replace function iam.sc_pay_employee_compensations(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from pay.employee_compensations t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_pay_payroll_periods(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from pay.payroll_periods t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.payroll_group_id in (select iam.sc_pay_payroll_groups(p_perm, p_write)) or (not p_write and t.payroll_group_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_pay_payslips(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from pay.payslips t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_pay_employee_loans(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from pay.employee_loans t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_leave_applications(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.leave_applications t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_performance_plans(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.performance_plans t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_performance_goals(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.performance_goals t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.plan_id in (select iam.sc_hrm_performance_plans(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_performance_reviews(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.performance_reviews t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.plan_id in (select iam.sc_hrm_performance_plans(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_requisitions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.requisitions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))) or (t.department_id in (select unnest(iam.perm_department_ids(p_perm))) or (not p_write and t.department_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_hrm_candidates(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.candidates t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.person_id in (select iam.sc_core_persons(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_job_postings(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.job_postings t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.requisition_id in (select iam.sc_hrm_requisitions(p_perm, p_write)) or (not p_write and t.requisition_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_hrm_candidate_applications(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.candidate_applications t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.candidate_id in (select iam.sc_hrm_candidates(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_interviews(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.interviews t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.application_id in (select iam.sc_hrm_candidate_applications(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_onboardings(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.onboardings t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write)) or (not p_write and t.employee_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_hrm_training_sessions(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.training_sessions t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.course_id in (select core.courses_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_training_assessments(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.training_assessments t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.course_id in (select core.courses_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_hrm_training_enrollments(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from hrm.training_enrollments t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.course_id in (select core.courses_in_scope(p_perm, p_write))) or (t.employee_id in (select core.employees_in_scope(p_perm, p_write))))
$$;
create or replace function iam.sc_cam_audience_groups(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.audience_groups t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_cam_audience_groups() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.audience_groups t where t.tenant_id = core.current_tenant_id() and (created_by = (select auth.uid()))
$$;
create or replace function iam.sc_cam_messages(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.messages t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_cam_messages() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.messages t where t.tenant_id = core.current_tenant_id() and (created_by = (select auth.uid()))
$$;
create or replace function iam.sc_cam_devices(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.devices t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.person_id in (select iam.sc_core_persons(p_perm, p_write))))
$$;
create or replace function iam.sc_cam_deliveries(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.deliveries t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.message_id in (select iam.sc_cam_messages(p_perm, p_write)) or (not p_write and t.message_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_cam_notification_rules(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.notification_rules t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.template_id in (select iam.sc_doc_templates(p_perm, p_write)) or (not p_write and t.template_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_cam_surveys(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.surveys t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function core.own_cam_surveys() returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.surveys t where t.tenant_id = core.current_tenant_id() and (created_by = (select auth.uid()))
$$;
create or replace function iam.sc_cam_survey_responses(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.survey_responses t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.survey_id in (select iam.sc_cam_surveys(p_perm, p_write))))
$$;
create or replace function iam.sc_cam_emergency_alerts(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from cam.emergency_alerts t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.college_id in (select unnest(iam.perm_colleges(p_perm))) or (not p_write and t.college_id is null and (select iam.has_permission(p_perm)))))
$$;
create or replace function iam.sc_rpt_dashboards(p_perm text, p_write boolean default false) returns setof uuid
language sql stable security definer set search_path = '' as $$
  select t.id from rpt.dashboards t where t.tenant_id = core.current_tenant_id() and ((select iam.perm_all(p_perm)) or (t.owner_role_id in (select iam.sc_iam_roles(p_perm, p_write)) or (not p_write and t.owner_role_id is null and (select iam.has_permission(p_perm)))))
$$;
