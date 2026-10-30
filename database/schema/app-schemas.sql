-- App schemas exported from CampXSync-Staging before any project replacement.
-- Auth, storage, and row data are not included.

CREATE SCHEMA IF NOT EXISTS plat;
CREATE SCHEMA IF NOT EXISTS core;
CREATE SCHEMA IF NOT EXISTS iam;
CREATE SCHEMA IF NOT EXISTS cfg;
CREATE SCHEMA IF NOT EXISTS acd;
CREATE SCHEMA IF NOT EXISTS stu;
CREATE SCHEMA IF NOT EXISTS hrm;
CREATE SCHEMA IF NOT EXISTS lms;
CREATE SCHEMA IF NOT EXISTS obe;
CREATE SCHEMA IF NOT EXISTS fin;
CREATE SCHEMA IF NOT EXISTS tax;
CREATE SCHEMA IF NOT EXISTS bud;
CREATE SCHEMA IF NOT EXISTS pay;
CREATE SCHEMA IF NOT EXISTS exm;
CREATE SCHEMA IF NOT EXISTS cam;
CREATE SCHEMA IF NOT EXISTS doc;
CREATE SCHEMA IF NOT EXISTS rpt;
CREATE SCHEMA IF NOT EXISTS wf;
CREATE SCHEMA IF NOT EXISTS sys;
CREATE SCHEMA IF NOT EXISTS audit;

CREATE TABLE IF NOT EXISTS acd.assessment_components (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  component_type text NOT NULL,
  is_external boolean DEFAULT false NOT NULL,
  max_marks numeric(18,2) NOT NULL,
  min_marks numeric(18,2),
  weightage_pct numeric(9,4),
  sequence smallint DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.assessment_components ADD CONSTRAINT ck_assessment_components_component_type CHECK ((component_type = ANY (ARRAY['INTERNAL_TEST'::text, 'ASSIGNMENT'::text, 'LAB'::text, 'VIVA'::text, 'PROJECT'::text, 'ATTENDANCE'::text, 'END_SEMESTER'::text, 'OTHER'::text])));
ALTER TABLE acd.assessment_components ADD CONSTRAINT ck_assessment_components_max_marks CHECK ((max_marks >= (0)::numeric));
ALTER TABLE acd.assessment_components ADD CONSTRAINT ck_assessment_components_min_marks CHECK ((min_marks >= (0)::numeric));
ALTER TABLE acd.assessment_components ADD CONSTRAINT ck_assessment_components_weightage_pct CHECK (((weightage_pct >= (0)::numeric) AND (weightage_pct <= (100)::numeric)));
ALTER TABLE acd.assessment_components ADD CONSTRAINT assessment_components_pkey PRIMARY KEY (id);
ALTER TABLE acd.assessment_components ADD CONSTRAINT uq_assessment_components_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.assessment_structures (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  subject_id uuid,
  curriculum_version_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  total_marks numeric(18,2) NOT NULL,
  pass_marks numeric(18,2),
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.assessment_structures ADD CONSTRAINT ck_assessment_structures_pass_marks CHECK ((pass_marks >= (0)::numeric));
ALTER TABLE acd.assessment_structures ADD CONSTRAINT ck_assessment_structures_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE acd.assessment_structures ADD CONSTRAINT ck_assessment_structures_total_marks CHECK ((total_marks >= (0)::numeric));
ALTER TABLE acd.assessment_structures ADD CONSTRAINT assessment_structures_pkey PRIMARY KEY (id);
ALTER TABLE acd.assessment_structures ADD CONSTRAINT uq_assessment_structures_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.attendance_corrections (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  record_id uuid NOT NULL,
  old_status text NOT NULL,
  new_status text NOT NULL,
  reason text NOT NULL,
  requested_by uuid,
  approved_by uuid,
  workflow_instance_id uuid,
  status text DEFAULT 'PENDING'::text NOT NULL,
  decided_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT ck_attendance_corrections_new_status CHECK ((new_status = ANY (ARRAY['PRESENT'::text, 'ABSENT'::text, 'LATE'::text, 'ON_DUTY'::text, 'LEAVE'::text, 'EXCUSED'::text])));
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT ck_attendance_corrections_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text])));
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT attendance_corrections_pkey PRIMARY KEY (id);
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT uq_attendance_corrections_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.attendance_records (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  student_id uuid NOT NULL,
  status text NOT NULL,
  method text DEFAULT 'MANUAL'::text NOT NULL,
  marked_by uuid,
  marked_at timestamp with time zone DEFAULT now() NOT NULL,
  remarks text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.attendance_records ADD CONSTRAINT ck_attendance_records_method CHECK ((method = ANY (ARRAY['MANUAL'::text, 'QR'::text, 'BIOMETRIC'::text, 'GEOFENCE'::text, 'IMPORT'::text])));
ALTER TABLE acd.attendance_records ADD CONSTRAINT ck_attendance_records_status CHECK ((status = ANY (ARRAY['PRESENT'::text, 'ABSENT'::text, 'LATE'::text, 'ON_DUTY'::text, 'LEAVE'::text, 'EXCUSED'::text])));
ALTER TABLE acd.attendance_records ADD CONSTRAINT attendance_records_pkey PRIMARY KEY (id);
ALTER TABLE acd.attendance_records ADD CONSTRAINT uq_attendance_records_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.attendance_sessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  section_id uuid NOT NULL,
  timetable_entry_id uuid,
  employee_id uuid,
  session_date date NOT NULL,
  start_time time without time zone,
  end_time time without time zone,
  status text DEFAULT 'SCHEDULED'::text NOT NULL,
  locked_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.attendance_sessions ADD CONSTRAINT ck_attendance_sessions_status CHECK ((status = ANY (ARRAY['SCHEDULED'::text, 'OPEN'::text, 'CLOSED'::text, 'CANCELLED'::text])));
ALTER TABLE acd.attendance_sessions ADD CONSTRAINT attendance_sessions_pkey PRIMARY KEY (id);
ALTER TABLE acd.attendance_sessions ADD CONSTRAINT uq_attendance_sessions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.batch_capacity_overrides (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  batch_id uuid NOT NULL,
  term_id uuid,
  capacity integer NOT NULL,
  reason text,
  approved_by uuid,
  valid_from date,
  valid_to date,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.batch_capacity_overrides ADD CONSTRAINT ck_batch_capacity_overrides_capacity CHECK ((capacity >= 0));
ALTER TABLE acd.batch_capacity_overrides ADD CONSTRAINT batch_capacity_overrides_pkey PRIMARY KEY (id);
ALTER TABLE acd.batch_capacity_overrides ADD CONSTRAINT uq_batch_capacity_overrides_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.batch_students (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  batch_id uuid NOT NULL,
  student_id uuid NOT NULL,
  roll_no text,
  joined_on date,
  left_on date,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.batch_students ADD CONSTRAINT ck_batch_students_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'TRANSFERRED'::text, 'DETAINED'::text, 'COMPLETED'::text, 'DROPPED'::text])));
ALTER TABLE acd.batch_students ADD CONSTRAINT batch_students_pkey PRIMARY KEY (id);
ALTER TABLE acd.batch_students ADD CONSTRAINT uq_batch_students_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.batches (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  program_id uuid NOT NULL,
  curriculum_version_id uuid,
  admission_year_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  current_term_no smallint,
  capacity integer,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.batches ADD CONSTRAINT ck_batches_capacity CHECK ((capacity >= 0));
ALTER TABLE acd.batches ADD CONSTRAINT ck_batches_status CHECK ((status = ANY (ARRAY['PLANNED'::text, 'ACTIVE'::text, 'GRADUATED'::text, 'CLOSED'::text])));
ALTER TABLE acd.batches ADD CONSTRAINT batches_pkey PRIMARY KEY (id);
ALTER TABLE acd.batches ADD CONSTRAINT uq_batches_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.calendar_terms (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  calendar_id uuid NOT NULL,
  term_no smallint NOT NULL,
  name text NOT NULL,
  term_type text DEFAULT 'SEMESTER'::text NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  status text DEFAULT 'PLANNED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.calendar_terms ADD CONSTRAINT ck_calendar_terms_status CHECK ((status = ANY (ARRAY['PLANNED'::text, 'ACTIVE'::text, 'CLOSED'::text])));
ALTER TABLE acd.calendar_terms ADD CONSTRAINT ck_calendar_terms_term_no CHECK ((term_no > 0));
ALTER TABLE acd.calendar_terms ADD CONSTRAINT ck_calendar_terms_term_type CHECK ((term_type = ANY (ARRAY['SEMESTER'::text, 'TRIMESTER'::text, 'QUARTER'::text, 'SUMMER'::text, 'WINTER'::text])));
ALTER TABLE acd.calendar_terms ADD CONSTRAINT ck_calendar_terms_x1 CHECK ((end_date > start_date));
ALTER TABLE acd.calendar_terms ADD CONSTRAINT calendar_terms_pkey PRIMARY KEY (id);
ALTER TABLE acd.calendar_terms ADD CONSTRAINT uq_calendar_terms_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.course_offerings (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  course_id uuid NOT NULL,
  term_id uuid NOT NULL,
  start_date date,
  end_date date,
  capacity integer,
  status text DEFAULT 'PLANNED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.course_offerings ADD CONSTRAINT ck_course_offerings_capacity CHECK ((capacity >= 0));
ALTER TABLE acd.course_offerings ADD CONSTRAINT ck_course_offerings_status CHECK ((status = ANY (ARRAY['PLANNED'::text, 'OPEN'::text, 'RUNNING'::text, 'COMPLETED'::text, 'CANCELLED'::text])));
ALTER TABLE acd.course_offerings ADD CONSTRAINT course_offerings_pkey PRIMARY KEY (id);
ALTER TABLE acd.course_offerings ADD CONSTRAINT uq_course_offerings_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.course_prerequisites (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid NOT NULL,
  prerequisite_course_id uuid NOT NULL,
  relation text DEFAULT 'PREREQUISITE'::text NOT NULL,
  min_grade text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.course_prerequisites ADD CONSTRAINT ck_course_prerequisites_relation CHECK ((relation = ANY (ARRAY['PREREQUISITE'::text, 'COREQUISITE'::text])));
ALTER TABLE acd.course_prerequisites ADD CONSTRAINT ck_course_prerequisites_x1 CHECK ((course_id <> prerequisite_course_id));
ALTER TABLE acd.course_prerequisites ADD CONSTRAINT course_prerequisites_pkey PRIMARY KEY (id);
ALTER TABLE acd.course_prerequisites ADD CONSTRAINT uq_course_prerequisites_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.course_program_map (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid NOT NULL,
  program_id uuid NOT NULL,
  term_no smallint,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE acd.course_program_map ADD CONSTRAINT course_program_map_pkey PRIMARY KEY (id);
ALTER TABLE acd.course_program_map ADD CONSTRAINT uq_course_program_map_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.course_sections (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  offering_id uuid NOT NULL,
  batch_id uuid,
  section_code text NOT NULL,
  capacity integer,
  default_room_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.course_sections ADD CONSTRAINT ck_course_sections_capacity CHECK ((capacity >= 0));
ALTER TABLE acd.course_sections ADD CONSTRAINT ck_course_sections_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'CLOSED'::text, 'CANCELLED'::text])));
ALTER TABLE acd.course_sections ADD CONSTRAINT course_sections_pkey PRIMARY KEY (id);
ALTER TABLE acd.course_sections ADD CONSTRAINT uq_course_sections_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.course_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid NOT NULL,
  version_no integer NOT NULL,
  effective_from date,
  effective_to date,
  content_outline jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.course_versions ADD CONSTRAINT ck_course_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text])));
ALTER TABLE acd.course_versions ADD CONSTRAINT ck_course_versions_version_no CHECK ((version_no > 0));
ALTER TABLE acd.course_versions ADD CONSTRAINT course_versions_pkey PRIMARY KEY (id);
ALTER TABLE acd.course_versions ADD CONSTRAINT uq_course_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.courses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  department_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  description text,
  level text,
  credits numeric(18,2),
  status text DEFAULT 'DRAFT'::text NOT NULL,
  metadata jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.courses ADD CONSTRAINT ck_courses_credits CHECK ((credits >= (0)::numeric));
ALTER TABLE acd.courses ADD CONSTRAINT ck_courses_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE acd.courses ADD CONSTRAINT courses_pkey PRIMARY KEY (id);
ALTER TABLE acd.courses ADD CONSTRAINT uq_courses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.curricula (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  program_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  regulation_year smallint,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.curricula ADD CONSTRAINT ck_curricula_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE acd.curricula ADD CONSTRAINT curricula_pkey PRIMARY KEY (id);
ALTER TABLE acd.curricula ADD CONSTRAINT uq_curricula_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.curriculum_subjects (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  curriculum_version_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  term_no smallint NOT NULL,
  category text DEFAULT 'CORE'::text NOT NULL,
  credits numeric(18,2),
  elective_group text,
  display_order smallint DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT ck_curriculum_subjects_category CHECK ((category = ANY (ARRAY['CORE'::text, 'ELECTIVE'::text, 'OPEN_ELECTIVE'::text, 'MANDATORY_NON_CREDIT'::text, 'PROJECT'::text])));
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT ck_curriculum_subjects_credits CHECK ((credits >= (0)::numeric));
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT ck_curriculum_subjects_term_no CHECK ((term_no > 0));
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT curriculum_subjects_pkey PRIMARY KEY (id);
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT uq_curriculum_subjects_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.curriculum_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  curriculum_id uuid NOT NULL,
  version_no integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  total_credits numeric(18,2),
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT ck_curriculum_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text, 'RETIRED'::text])));
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT ck_curriculum_versions_total_credits CHECK ((total_credits >= (0)::numeric));
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT ck_curriculum_versions_version_no CHECK ((version_no > 0));
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT ck_curriculum_versions_x1 CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT curriculum_versions_pkey PRIMARY KEY (id);
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT uq_curriculum_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.enrollment_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  offering_id uuid NOT NULL,
  rule_type text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.enrollment_rules ADD CONSTRAINT enrollment_rules_pkey PRIMARY KEY (id);
ALTER TABLE acd.enrollment_rules ADD CONSTRAINT uq_enrollment_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.programs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  department_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  level text NOT NULL,
  duration_years numeric(9,4) NOT NULL,
  total_terms smallint,
  published boolean DEFAULT false NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.programs ADD CONSTRAINT ck_programs_duration_years CHECK ((duration_years > (0)::numeric));
ALTER TABLE acd.programs ADD CONSTRAINT ck_programs_level CHECK ((level = ANY (ARRAY['CERTIFICATE'::text, 'DIPLOMA'::text, 'UG'::text, 'PG'::text, 'PHD'::text, 'INTEGRATED'::text])));
ALTER TABLE acd.programs ADD CONSTRAINT ck_programs_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'DISCONTINUED'::text, 'DRAFT'::text])));
ALTER TABLE acd.programs ADD CONSTRAINT programs_pkey PRIMARY KEY (id);
ALTER TABLE acd.programs ADD CONSTRAINT uq_programs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.section_enrollments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  section_id uuid NOT NULL,
  student_id uuid NOT NULL,
  status text DEFAULT 'ENROLLED'::text NOT NULL,
  enrolled_at timestamp with time zone DEFAULT now() NOT NULL,
  dropped_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.section_enrollments ADD CONSTRAINT ck_section_enrollments_status CHECK ((status = ANY (ARRAY['ENROLLED'::text, 'DROPPED'::text, 'WITHDRAWN'::text, 'COMPLETED'::text, 'AUDIT'::text])));
ALTER TABLE acd.section_enrollments ADD CONSTRAINT section_enrollments_pkey PRIMARY KEY (id);
ALTER TABLE acd.section_enrollments ADD CONSTRAINT uq_section_enrollments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.section_faculty (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  section_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  role text DEFAULT 'PRIMARY'::text NOT NULL,
  valid_from date,
  valid_to date,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.section_faculty ADD CONSTRAINT ck_section_faculty_role CHECK ((role = ANY (ARRAY['PRIMARY'::text, 'CO_INSTRUCTOR'::text, 'LAB_INSTRUCTOR'::text, 'TEACHING_ASSISTANT'::text, 'MENTOR'::text])));
ALTER TABLE acd.section_faculty ADD CONSTRAINT section_faculty_pkey PRIMARY KEY (id);
ALTER TABLE acd.section_faculty ADD CONSTRAINT uq_section_faculty_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.subject_change_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  subject_id uuid NOT NULL,
  change_type text NOT NULL,
  proposed_changes jsonb DEFAULT '{}'::jsonb NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  requested_by uuid,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.subject_change_requests ADD CONSTRAINT ck_subject_change_requests_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'APPLIED'::text])));
ALTER TABLE acd.subject_change_requests ADD CONSTRAINT subject_change_requests_pkey PRIMARY KEY (id);
ALTER TABLE acd.subject_change_requests ADD CONSTRAINT uq_subject_change_requests_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.subject_course_map (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  subject_id uuid NOT NULL,
  course_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE acd.subject_course_map ADD CONSTRAINT subject_course_map_pkey PRIMARY KEY (id);
ALTER TABLE acd.subject_course_map ADD CONSTRAINT uq_subject_course_map_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.subject_equivalencies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  subject_id uuid NOT NULL,
  equivalent_subject_id uuid NOT NULL,
  valid_from date,
  valid_to date,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.subject_equivalencies ADD CONSTRAINT ck_subject_equivalencies_x1 CHECK ((subject_id <> equivalent_subject_id));
ALTER TABLE acd.subject_equivalencies ADD CONSTRAINT subject_equivalencies_pkey PRIMARY KEY (id);
ALTER TABLE acd.subject_equivalencies ADD CONSTRAINT uq_subject_equivalencies_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.subject_prerequisites (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  subject_id uuid NOT NULL,
  prerequisite_subject_id uuid NOT NULL,
  relation text DEFAULT 'PREREQUISITE'::text NOT NULL,
  min_grade text,
  curriculum_version_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT ck_subject_prerequisites_relation CHECK ((relation = ANY (ARRAY['PREREQUISITE'::text, 'COREQUISITE'::text])));
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT ck_subject_prerequisites_x1 CHECK ((subject_id <> prerequisite_subject_id));
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT subject_prerequisites_pkey PRIMARY KEY (id);
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT uq_subject_prerequisites_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.subject_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  subject_id uuid NOT NULL,
  version_no integer NOT NULL,
  effective_from date,
  effective_to date,
  credits numeric(18,2),
  syllabus jsonb DEFAULT '{}'::jsonb,
  reference_books jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.subject_versions ADD CONSTRAINT ck_subject_versions_credits CHECK ((credits >= (0)::numeric));
ALTER TABLE acd.subject_versions ADD CONSTRAINT ck_subject_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text])));
ALTER TABLE acd.subject_versions ADD CONSTRAINT ck_subject_versions_version_no CHECK ((version_no > 0));
ALTER TABLE acd.subject_versions ADD CONSTRAINT subject_versions_pkey PRIMARY KEY (id);
ALTER TABLE acd.subject_versions ADD CONSTRAINT uq_subject_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.subjects (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  department_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  subject_type text DEFAULT 'THEORY'::text NOT NULL,
  category_id uuid,
  credits numeric(18,2),
  lecture_hours smallint,
  tutorial_hours smallint,
  practical_hours smallint,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  metadata jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.subjects ADD CONSTRAINT ck_subjects_credits CHECK ((credits >= (0)::numeric));
ALTER TABLE acd.subjects ADD CONSTRAINT ck_subjects_lecture_hours CHECK ((lecture_hours >= 0));
ALTER TABLE acd.subjects ADD CONSTRAINT ck_subjects_practical_hours CHECK ((practical_hours >= 0));
ALTER TABLE acd.subjects ADD CONSTRAINT ck_subjects_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE acd.subjects ADD CONSTRAINT ck_subjects_subject_type CHECK ((subject_type = ANY (ARRAY['THEORY'::text, 'LAB'::text, 'PROJECT'::text, 'SEMINAR'::text, 'INTERNSHIP'::text, 'MOOC'::text, 'AUDIT'::text])));
ALTER TABLE acd.subjects ADD CONSTRAINT ck_subjects_tutorial_hours CHECK ((tutorial_hours >= 0));
ALTER TABLE acd.subjects ADD CONSTRAINT subjects_pkey PRIMARY KEY (id);
ALTER TABLE acd.subjects ADD CONSTRAINT uq_subjects_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.timetable_conflicts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  timetable_version_id uuid NOT NULL,
  conflict_type text NOT NULL,
  entry_a_id uuid,
  entry_b_id uuid,
  severity text DEFAULT 'ERROR'::text NOT NULL,
  detail jsonb,
  resolved_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT ck_timetable_conflicts_conflict_type CHECK ((conflict_type = ANY (ARRAY['FACULTY'::text, 'ROOM'::text, 'BATCH'::text, 'CAPACITY'::text, 'OTHER'::text])));
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT ck_timetable_conflicts_severity CHECK ((severity = ANY (ARRAY['WARNING'::text, 'ERROR'::text])));
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT timetable_conflicts_pkey PRIMARY KEY (id);
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT uq_timetable_conflicts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.timetable_entries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  timetable_version_id uuid NOT NULL,
  section_id uuid,
  subject_id uuid,
  employee_id uuid,
  room_id uuid,
  day_of_week smallint NOT NULL,
  start_time time without time zone NOT NULL,
  end_time time without time zone NOT NULL,
  entry_type text DEFAULT 'LECTURE'::text NOT NULL,
  valid_from date,
  valid_to date,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.timetable_entries ADD CONSTRAINT ck_timetable_entries_day_of_week CHECK ((day_of_week >= 0));
ALTER TABLE acd.timetable_entries ADD CONSTRAINT ck_timetable_entries_entry_type CHECK ((entry_type = ANY (ARRAY['LECTURE'::text, 'LAB'::text, 'TUTORIAL'::text, 'SEMINAR'::text, 'BREAK'::text])));
ALTER TABLE acd.timetable_entries ADD CONSTRAINT ck_timetable_entries_x1 CHECK ((end_time > start_time));
ALTER TABLE acd.timetable_entries ADD CONSTRAINT ck_timetable_entries_x2 CHECK (((day_of_week >= 0) AND (day_of_week <= 6)));
ALTER TABLE acd.timetable_entries ADD CONSTRAINT timetable_entries_pkey PRIMARY KEY (id);
ALTER TABLE acd.timetable_entries ADD CONSTRAINT uq_timetable_entries_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.timetable_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  timetable_id uuid NOT NULL,
  version_no integer NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  published_at timestamp with time zone,
  change_note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.timetable_versions ADD CONSTRAINT ck_timetable_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'SUPERSEDED'::text])));
ALTER TABLE acd.timetable_versions ADD CONSTRAINT ck_timetable_versions_version_no CHECK ((version_no > 0));
ALTER TABLE acd.timetable_versions ADD CONSTRAINT timetable_versions_pkey PRIMARY KEY (id);
ALTER TABLE acd.timetable_versions ADD CONSTRAINT uq_timetable_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS acd.timetables (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  term_id uuid NOT NULL,
  batch_id uuid,
  name text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE acd.timetables ADD CONSTRAINT ck_timetables_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'ARCHIVED'::text])));
ALTER TABLE acd.timetables ADD CONSTRAINT timetables_pkey PRIMARY KEY (id);
ALTER TABLE acd.timetables ADD CONSTRAINT uq_timetables_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.access_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  principal_id uuid,
  resource_type text NOT NULL,
  resource_id uuid,
  access_type text NOT NULL,
  sensitivity text,
  ip inet,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.access_events ADD CONSTRAINT ck_access_events_access_type CHECK ((access_type = ANY (ARRAY['READ'::text, 'EXPORT'::text, 'PRINT'::text, 'DOWNLOAD'::text])));
ALTER TABLE audit.access_events ADD CONSTRAINT access_events_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.access_events_default (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  principal_id uuid,
  resource_type text NOT NULL,
  resource_id uuid,
  access_type text NOT NULL,
  sensitivity text,
  ip inet,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.access_events_default ADD CONSTRAINT ck_access_events_access_type CHECK ((access_type = ANY (ARRAY['READ'::text, 'EXPORT'::text, 'PRINT'::text, 'DOWNLOAD'::text])));
ALTER TABLE audit.access_events_default ADD CONSTRAINT access_events_default_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.access_events_p202610 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  principal_id uuid,
  resource_type text NOT NULL,
  resource_id uuid,
  access_type text NOT NULL,
  sensitivity text,
  ip inet,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.access_events_p202610 ADD CONSTRAINT ck_access_events_access_type CHECK ((access_type = ANY (ARRAY['READ'::text, 'EXPORT'::text, 'PRINT'::text, 'DOWNLOAD'::text])));
ALTER TABLE audit.access_events_p202610 ADD CONSTRAINT access_events_p202610_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.access_events_p202611 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  principal_id uuid,
  resource_type text NOT NULL,
  resource_id uuid,
  access_type text NOT NULL,
  sensitivity text,
  ip inet,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.access_events_p202611 ADD CONSTRAINT ck_access_events_access_type CHECK ((access_type = ANY (ARRAY['READ'::text, 'EXPORT'::text, 'PRINT'::text, 'DOWNLOAD'::text])));
ALTER TABLE audit.access_events_p202611 ADD CONSTRAINT access_events_p202611_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.access_events_p202612 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  principal_id uuid,
  resource_type text NOT NULL,
  resource_id uuid,
  access_type text NOT NULL,
  sensitivity text,
  ip inet,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.access_events_p202612 ADD CONSTRAINT ck_access_events_access_type CHECK ((access_type = ANY (ARRAY['READ'::text, 'EXPORT'::text, 'PRINT'::text, 'DOWNLOAD'::text])));
ALTER TABLE audit.access_events_p202612 ADD CONSTRAINT access_events_p202612_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.access_events_p202701 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  principal_id uuid,
  resource_type text NOT NULL,
  resource_id uuid,
  access_type text NOT NULL,
  sensitivity text,
  ip inet,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.access_events_p202701 ADD CONSTRAINT ck_access_events_access_type CHECK ((access_type = ANY (ARRAY['READ'::text, 'EXPORT'::text, 'PRINT'::text, 'DOWNLOAD'::text])));
ALTER TABLE audit.access_events_p202701 ADD CONSTRAINT access_events_p202701_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.archives (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  period_from date NOT NULL,
  period_to date NOT NULL,
  document_id uuid,
  row_count bigint,
  sha256 text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.archives ADD CONSTRAINT archives_pkey PRIMARY KEY (id);
ALTER TABLE audit.archives ADD CONSTRAINT uq_archives_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.case_actions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  case_id uuid NOT NULL,
  action text NOT NULL,
  actor_id uuid,
  detail text,
  acted_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.case_actions ADD CONSTRAINT case_actions_pkey PRIMARY KEY (id);
ALTER TABLE audit.case_actions ADD CONSTRAINT uq_case_actions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.case_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  case_id uuid NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.case_items ADD CONSTRAINT case_items_pkey PRIMARY KEY (id);
ALTER TABLE audit.case_items ADD CONSTRAINT uq_case_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.cases (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  case_no text NOT NULL,
  title text NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  severity text,
  opened_by uuid,
  closed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.cases ADD CONSTRAINT ck_cases_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'INVESTIGATING'::text, 'CLOSED'::text])));
ALTER TABLE audit.cases ADD CONSTRAINT cases_pkey PRIMARY KEY (id);
ALTER TABLE audit.cases ADD CONSTRAINT uq_cases_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.change_log (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  table_schema text NOT NULL,
  table_name text NOT NULL,
  record_id uuid,
  action text NOT NULL,
  changed_fields text[],
  old_data jsonb,
  new_data jsonb,
  actor_id uuid,
  request_id text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.change_log ADD CONSTRAINT ck_change_log_action CHECK ((action = ANY (ARRAY['I'::text, 'U'::text, 'D'::text])));
ALTER TABLE audit.change_log ADD CONSTRAINT change_log_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.change_log_default (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  table_schema text NOT NULL,
  table_name text NOT NULL,
  record_id uuid,
  action text NOT NULL,
  changed_fields text[],
  old_data jsonb,
  new_data jsonb,
  actor_id uuid,
  request_id text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.change_log_default ADD CONSTRAINT ck_change_log_action CHECK ((action = ANY (ARRAY['I'::text, 'U'::text, 'D'::text])));
ALTER TABLE audit.change_log_default ADD CONSTRAINT change_log_default_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.change_log_p202610 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  table_schema text NOT NULL,
  table_name text NOT NULL,
  record_id uuid,
  action text NOT NULL,
  changed_fields text[],
  old_data jsonb,
  new_data jsonb,
  actor_id uuid,
  request_id text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.change_log_p202610 ADD CONSTRAINT ck_change_log_action CHECK ((action = ANY (ARRAY['I'::text, 'U'::text, 'D'::text])));
ALTER TABLE audit.change_log_p202610 ADD CONSTRAINT change_log_p202610_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.change_log_p202611 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  table_schema text NOT NULL,
  table_name text NOT NULL,
  record_id uuid,
  action text NOT NULL,
  changed_fields text[],
  old_data jsonb,
  new_data jsonb,
  actor_id uuid,
  request_id text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.change_log_p202611 ADD CONSTRAINT ck_change_log_action CHECK ((action = ANY (ARRAY['I'::text, 'U'::text, 'D'::text])));
ALTER TABLE audit.change_log_p202611 ADD CONSTRAINT change_log_p202611_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.change_log_p202612 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  table_schema text NOT NULL,
  table_name text NOT NULL,
  record_id uuid,
  action text NOT NULL,
  changed_fields text[],
  old_data jsonb,
  new_data jsonb,
  actor_id uuid,
  request_id text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.change_log_p202612 ADD CONSTRAINT ck_change_log_action CHECK ((action = ANY (ARRAY['I'::text, 'U'::text, 'D'::text])));
ALTER TABLE audit.change_log_p202612 ADD CONSTRAINT change_log_p202612_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.change_log_p202701 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  table_schema text NOT NULL,
  table_name text NOT NULL,
  record_id uuid,
  action text NOT NULL,
  changed_fields text[],
  old_data jsonb,
  new_data jsonb,
  actor_id uuid,
  request_id text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.change_log_p202701 ADD CONSTRAINT ck_change_log_action CHECK ((action = ANY (ARRAY['I'::text, 'U'::text, 'D'::text])));
ALTER TABLE audit.change_log_p202701 ADD CONSTRAINT change_log_p202701_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.compliance_evaluations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  rule_id uuid NOT NULL,
  entity_type text,
  entity_id uuid,
  result text NOT NULL,
  detail jsonb,
  evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.compliance_evaluations ADD CONSTRAINT ck_compliance_evaluations_result CHECK ((result = ANY (ARRAY['PASS'::text, 'FAIL'::text, 'WARN'::text])));
ALTER TABLE audit.compliance_evaluations ADD CONSTRAINT compliance_evaluations_pkey PRIMARY KEY (id);
ALTER TABLE audit.compliance_evaluations ADD CONSTRAINT uq_compliance_evaluations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.compliance_exceptions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  violation_id uuid NOT NULL,
  reason text NOT NULL,
  approved_by uuid,
  valid_until timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.compliance_exceptions ADD CONSTRAINT compliance_exceptions_pkey PRIMARY KEY (id);
ALTER TABLE audit.compliance_exceptions ADD CONSTRAINT uq_compliance_exceptions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.compliance_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  rule_type text NOT NULL,
  severity text DEFAULT 'MEDIUM'::text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb,
  active boolean DEFAULT true NOT NULL,
  version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.compliance_rules ADD CONSTRAINT ck_compliance_rules_severity CHECK ((severity = ANY (ARRAY['LOW'::text, 'MEDIUM'::text, 'HIGH'::text, 'CRITICAL'::text])));
ALTER TABLE audit.compliance_rules ADD CONSTRAINT compliance_rules_pkey PRIMARY KEY (id);
ALTER TABLE audit.compliance_rules ADD CONSTRAINT uq_compliance_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.compliance_violations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  rule_id uuid NOT NULL,
  evaluation_id uuid,
  entity_type text,
  entity_id uuid,
  severity text NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  detail jsonb,
  resolved_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.compliance_violations ADD CONSTRAINT ck_compliance_violations_severity CHECK ((severity = ANY (ARRAY['LOW'::text, 'MEDIUM'::text, 'HIGH'::text, 'CRITICAL'::text])));
ALTER TABLE audit.compliance_violations ADD CONSTRAINT ck_compliance_violations_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'UNDER_REVIEW'::text, 'EXCEPTION_GRANTED'::text, 'RESOLVED'::text])));
ALTER TABLE audit.compliance_violations ADD CONSTRAINT compliance_violations_pkey PRIMARY KEY (id);
ALTER TABLE audit.compliance_violations ADD CONSTRAINT uq_compliance_violations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  college_id uuid,
  event_time timestamp with time zone DEFAULT now() NOT NULL,
  action text NOT NULL,
  principal_id uuid,
  principal_type text DEFAULT 'USER'::text,
  resource_type text,
  resource_id uuid,
  status text,
  severity text DEFAULT 'INFO'::text,
  ip inet,
  user_agent text,
  correlation_id text,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.events ADD CONSTRAINT events_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.events_default (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  college_id uuid,
  event_time timestamp with time zone DEFAULT now() NOT NULL,
  action text NOT NULL,
  principal_id uuid,
  principal_type text DEFAULT 'USER'::text,
  resource_type text,
  resource_id uuid,
  status text,
  severity text DEFAULT 'INFO'::text,
  ip inet,
  user_agent text,
  correlation_id text,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.events_default ADD CONSTRAINT events_default_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.events_p202610 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  college_id uuid,
  event_time timestamp with time zone DEFAULT now() NOT NULL,
  action text NOT NULL,
  principal_id uuid,
  principal_type text DEFAULT 'USER'::text,
  resource_type text,
  resource_id uuid,
  status text,
  severity text DEFAULT 'INFO'::text,
  ip inet,
  user_agent text,
  correlation_id text,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.events_p202610 ADD CONSTRAINT events_p202610_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.events_p202611 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  college_id uuid,
  event_time timestamp with time zone DEFAULT now() NOT NULL,
  action text NOT NULL,
  principal_id uuid,
  principal_type text DEFAULT 'USER'::text,
  resource_type text,
  resource_id uuid,
  status text,
  severity text DEFAULT 'INFO'::text,
  ip inet,
  user_agent text,
  correlation_id text,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.events_p202611 ADD CONSTRAINT events_p202611_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.events_p202612 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  college_id uuid,
  event_time timestamp with time zone DEFAULT now() NOT NULL,
  action text NOT NULL,
  principal_id uuid,
  principal_type text DEFAULT 'USER'::text,
  resource_type text,
  resource_id uuid,
  status text,
  severity text DEFAULT 'INFO'::text,
  ip inet,
  user_agent text,
  correlation_id text,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.events_p202612 ADD CONSTRAINT events_p202612_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.events_p202701 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  college_id uuid,
  event_time timestamp with time zone DEFAULT now() NOT NULL,
  action text NOT NULL,
  principal_id uuid,
  principal_type text DEFAULT 'USER'::text,
  resource_type text,
  resource_id uuid,
  status text,
  severity text DEFAULT 'INFO'::text,
  ip inet,
  user_agent text,
  correlation_id text,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.events_p202701 ADD CONSTRAINT events_p202701_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS audit.evidence (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  case_id uuid,
  document_id uuid,
  entity_type text,
  entity_id uuid,
  sha256 text,
  collected_at timestamp with time zone DEFAULT now() NOT NULL,
  chain_of_custody jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.evidence ADD CONSTRAINT evidence_pkey PRIMARY KEY (id);
ALTER TABLE audit.evidence ADD CONSTRAINT uq_evidence_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.field_policies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  table_schema text NOT NULL,
  table_name text NOT NULL,
  mode text DEFAULT 'REDACT'::text NOT NULL,
  columns text[] DEFAULT '{}'::text[],
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.field_policies ADD CONSTRAINT ck_field_policies_mode CHECK ((mode = ANY (ARRAY['REDACT'::text, 'ALLOWLIST'::text, 'METADATA_ONLY'::text])));
ALTER TABLE audit.field_policies ADD CONSTRAINT field_policies_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS audit.integrity_records (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  checked_from timestamp with time zone NOT NULL,
  checked_to timestamp with time zone NOT NULL,
  chain_hash text,
  status text NOT NULL,
  checked_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE audit.integrity_records ADD CONSTRAINT ck_integrity_records_status CHECK ((status = ANY (ARRAY['VERIFIED'::text, 'BROKEN'::text])));
ALTER TABLE audit.integrity_records ADD CONSTRAINT integrity_records_pkey PRIMARY KEY (id);
ALTER TABLE audit.integrity_records ADD CONSTRAINT uq_integrity_records_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.retention_policies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  entity_type text NOT NULL,
  retention_days integer NOT NULL,
  action text DEFAULT 'ARCHIVE'::text NOT NULL,
  legal_hold boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.retention_policies ADD CONSTRAINT ck_retention_policies_action CHECK ((action = ANY (ARRAY['ARCHIVE'::text, 'DELETE'::text, 'ANONYMIZE'::text])));
ALTER TABLE audit.retention_policies ADD CONSTRAINT ck_retention_policies_retention_days CHECK ((retention_days > 0));
ALTER TABLE audit.retention_policies ADD CONSTRAINT retention_policies_pkey PRIMARY KEY (id);
ALTER TABLE audit.retention_policies ADD CONSTRAINT uq_retention_policies_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.sod_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  conflicting_permissions text[] DEFAULT '{}'::text[] NOT NULL,
  severity text DEFAULT 'HIGH'::text NOT NULL,
  active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.sod_rules ADD CONSTRAINT sod_rules_pkey PRIMARY KEY (id);
ALTER TABLE audit.sod_rules ADD CONSTRAINT uq_sod_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS audit.sod_violations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  rule_id uuid NOT NULL,
  user_id uuid,
  entity_type text,
  entity_id uuid,
  status text DEFAULT 'OPEN'::text NOT NULL,
  detected_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE audit.sod_violations ADD CONSTRAINT sod_violations_pkey PRIMARY KEY (id);
ALTER TABLE audit.sod_violations ADD CONSTRAINT uq_sod_violations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.assumptions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  owner_type text NOT NULL,
  owner_id uuid NOT NULL,
  key text NOT NULL,
  value jsonb DEFAULT '{}'::jsonb NOT NULL,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.assumptions ADD CONSTRAINT ck_assumptions_owner_type CHECK ((owner_type = ANY (ARRAY['FORECAST'::text, 'SCENARIO'::text])));
ALTER TABLE bud.assumptions ADD CONSTRAINT assumptions_pkey PRIMARY KEY (id);
ALTER TABLE bud.assumptions ADD CONSTRAINT uq_assumptions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.budget_controls (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_id uuid,
  category_id uuid,
  control_type text NOT NULL,
  threshold_pct numeric(9,4),
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.budget_controls ADD CONSTRAINT ck_budget_controls_control_type CHECK ((control_type = ANY (ARRAY['HARD_STOP'::text, 'WARN'::text, 'APPROVAL_REQUIRED'::text])));
ALTER TABLE bud.budget_controls ADD CONSTRAINT budget_controls_pkey PRIMARY KEY (id);
ALTER TABLE bud.budget_controls ADD CONSTRAINT uq_budget_controls_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.budget_lines (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_version_id uuid NOT NULL,
  period_id uuid,
  category_id uuid,
  department_id uuid,
  cost_centre_id uuid,
  description text,
  planned_amount numeric(18,2) NOT NULL,
  allocated_amount numeric(18,2) DEFAULT 0 NOT NULL,
  committed_amount numeric(18,2) DEFAULT 0 NOT NULL,
  actual_amount numeric(18,2) DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.budget_lines ADD CONSTRAINT ck_budget_lines_actual_amount CHECK ((actual_amount >= (0)::numeric));
ALTER TABLE bud.budget_lines ADD CONSTRAINT ck_budget_lines_allocated_amount CHECK ((allocated_amount >= (0)::numeric));
ALTER TABLE bud.budget_lines ADD CONSTRAINT ck_budget_lines_committed_amount CHECK ((committed_amount >= (0)::numeric));
ALTER TABLE bud.budget_lines ADD CONSTRAINT ck_budget_lines_planned_amount CHECK ((planned_amount >= (0)::numeric));
ALTER TABLE bud.budget_lines ADD CONSTRAINT budget_lines_pkey PRIMARY KEY (id);
ALTER TABLE bud.budget_lines ADD CONSTRAINT uq_budget_lines_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.budget_periods (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_id uuid NOT NULL,
  period_no smallint NOT NULL,
  name text,
  start_date date NOT NULL,
  end_date date NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.budget_periods ADD CONSTRAINT ck_budget_periods_period_no CHECK ((period_no > 0));
ALTER TABLE bud.budget_periods ADD CONSTRAINT budget_periods_pkey PRIMARY KEY (id);
ALTER TABLE bud.budget_periods ADD CONSTRAINT uq_budget_periods_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.budget_transfers (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  from_line_id uuid NOT NULL,
  to_line_id uuid NOT NULL,
  amount numeric(18,2) NOT NULL,
  reason text,
  status text DEFAULT 'PENDING'::text NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.budget_transfers ADD CONSTRAINT ck_budget_transfers_amount CHECK ((amount > (0)::numeric));
ALTER TABLE bud.budget_transfers ADD CONSTRAINT ck_budget_transfers_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'APPLIED'::text])));
ALTER TABLE bud.budget_transfers ADD CONSTRAINT ck_budget_transfers_x1 CHECK ((from_line_id <> to_line_id));
ALTER TABLE bud.budget_transfers ADD CONSTRAINT budget_transfers_pkey PRIMARY KEY (id);
ALTER TABLE bud.budget_transfers ADD CONSTRAINT uq_budget_transfers_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.budget_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_id uuid NOT NULL,
  version_no integer NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  total_amount numeric(18,2),
  notes text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.budget_versions ADD CONSTRAINT ck_budget_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text])));
ALTER TABLE bud.budget_versions ADD CONSTRAINT ck_budget_versions_total_amount CHECK ((total_amount >= (0)::numeric));
ALTER TABLE bud.budget_versions ADD CONSTRAINT ck_budget_versions_version_no CHECK ((version_no > 0));
ALTER TABLE bud.budget_versions ADD CONSTRAINT budget_versions_pkey PRIMARY KEY (id);
ALTER TABLE bud.budget_versions ADD CONSTRAINT uq_budget_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.budgets (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  academic_year_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  budget_type text DEFAULT 'OPERATING'::text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  workflow_instance_id uuid,
  total_amount numeric(18,2),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.budgets ADD CONSTRAINT ck_budgets_budget_type CHECK ((budget_type = ANY (ARRAY['OPERATING'::text, 'CAPITAL'::text, 'PROJECT'::text])));
ALTER TABLE bud.budgets ADD CONSTRAINT ck_budgets_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PENDING_APPROVAL'::text, 'APPROVED'::text, 'ACTIVE'::text, 'CLOSED'::text])));
ALTER TABLE bud.budgets ADD CONSTRAINT ck_budgets_total_amount CHECK ((total_amount >= (0)::numeric));
ALTER TABLE bud.budgets ADD CONSTRAINT budgets_pkey PRIMARY KEY (id);
ALTER TABLE bud.budgets ADD CONSTRAINT uq_budgets_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.categories (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  parent_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  ledger_account_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.categories ADD CONSTRAINT categories_pkey PRIMARY KEY (id);
ALTER TABLE bud.categories ADD CONSTRAINT uq_categories_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.commitments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_line_id uuid NOT NULL,
  entry_type text NOT NULL,
  source_type text,
  source_id uuid,
  amount numeric(18,2) NOT NULL,
  entry_date date DEFAULT CURRENT_DATE NOT NULL,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE bud.commitments ADD CONSTRAINT ck_commitments_entry_type CHECK ((entry_type = ANY (ARRAY['RESERVATION'::text, 'COMMITMENT'::text, 'RELEASE'::text, 'ACTUAL'::text])));
ALTER TABLE bud.commitments ADD CONSTRAINT commitments_pkey PRIMARY KEY (id);
ALTER TABLE bud.commitments ADD CONSTRAINT uq_commitments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.control_results (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  control_id uuid,
  budget_line_id uuid,
  source_type text,
  source_id uuid,
  result text NOT NULL,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE bud.control_results ADD CONSTRAINT ck_control_results_result CHECK ((result = ANY (ARRAY['PASS'::text, 'WARN'::text, 'BLOCK'::text])));
ALTER TABLE bud.control_results ADD CONSTRAINT control_results_pkey PRIMARY KEY (id);
ALTER TABLE bud.control_results ADD CONSTRAINT uq_control_results_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.cost_centres (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  department_id uuid,
  parent_id uuid,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.cost_centres ADD CONSTRAINT cost_centres_pkey PRIMARY KEY (id);
ALTER TABLE bud.cost_centres ADD CONSTRAINT uq_cost_centres_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.forecast_lines (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  forecast_id uuid NOT NULL,
  version_no integer DEFAULT 1 NOT NULL,
  period_id uuid,
  category_id uuid,
  department_id uuid,
  amount numeric(18,2) NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.forecast_lines ADD CONSTRAINT forecast_lines_pkey PRIMARY KEY (id);
ALTER TABLE bud.forecast_lines ADD CONSTRAINT uq_forecast_lines_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.forecasts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_id uuid,
  name text NOT NULL,
  method text,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.forecasts ADD CONSTRAINT ck_forecasts_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'ARCHIVED'::text])));
ALTER TABLE bud.forecasts ADD CONSTRAINT forecasts_pkey PRIMARY KEY (id);
ALTER TABLE bud.forecasts ADD CONSTRAINT uq_forecasts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.scenario_results (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scenario_id uuid NOT NULL,
  version_no integer DEFAULT 1 NOT NULL,
  metric text NOT NULL,
  value numeric(18,2),
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE bud.scenario_results ADD CONSTRAINT scenario_results_pkey PRIMARY KEY (id);
ALTER TABLE bud.scenario_results ADD CONSTRAINT uq_scenario_results_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.scenarios (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_id uuid,
  name text NOT NULL,
  description text,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.scenarios ADD CONSTRAINT ck_scenarios_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'ARCHIVED'::text])));
ALTER TABLE bud.scenarios ADD CONSTRAINT scenarios_pkey PRIMARY KEY (id);
ALTER TABLE bud.scenarios ADD CONSTRAINT uq_scenarios_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS bud.variance_explanations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  budget_line_id uuid NOT NULL,
  period_id uuid,
  explanation text NOT NULL,
  explained_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE bud.variance_explanations ADD CONSTRAINT variance_explanations_pkey PRIMARY KEY (id);
ALTER TABLE bud.variance_explanations ADD CONSTRAINT uq_variance_explanations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.alert_acknowledgements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  alert_id uuid NOT NULL,
  person_id uuid NOT NULL,
  acknowledged_at timestamp with time zone DEFAULT now() NOT NULL,
  status text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.alert_acknowledgements ADD CONSTRAINT ck_alert_acknowledgements_status CHECK ((status = ANY (ARRAY['SAFE'::text, 'NEED_HELP'::text, 'ACKNOWLEDGED'::text])));
ALTER TABLE cam.alert_acknowledgements ADD CONSTRAINT alert_acknowledgements_pkey PRIMARY KEY (id);
ALTER TABLE cam.alert_acknowledgements ADD CONSTRAINT uq_alert_acknowledgements_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.alert_escalation_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  alert_type text,
  severity text,
  after_minutes integer NOT NULL,
  escalate_to_group_id uuid,
  channels text[] DEFAULT '{}'::text[],
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.alert_escalation_rules ADD CONSTRAINT ck_alert_escalation_rules_after_minutes CHECK ((after_minutes > 0));
ALTER TABLE cam.alert_escalation_rules ADD CONSTRAINT alert_escalation_rules_pkey PRIMARY KEY (id);
ALTER TABLE cam.alert_escalation_rules ADD CONSTRAINT uq_alert_escalation_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.audience_groups (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  group_type text DEFAULT 'STATIC'::text NOT NULL,
  description text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.audience_groups ADD CONSTRAINT ck_audience_groups_group_type CHECK ((group_type = ANY (ARRAY['STATIC'::text, 'DYNAMIC'::text])));
ALTER TABLE cam.audience_groups ADD CONSTRAINT ck_audience_groups_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])));
ALTER TABLE cam.audience_groups ADD CONSTRAINT audience_groups_pkey PRIMARY KEY (id);
ALTER TABLE cam.audience_groups ADD CONSTRAINT uq_audience_groups_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.audience_members (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  group_id uuid NOT NULL,
  person_id uuid NOT NULL,
  source text DEFAULT 'MANUAL'::text NOT NULL,
  joined_at timestamp with time zone DEFAULT now() NOT NULL,
  left_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.audience_members ADD CONSTRAINT ck_audience_members_source CHECK ((source = ANY (ARRAY['MANUAL'::text, 'RULE'::text, 'IMPORT'::text, 'SELF_SUBSCRIBED'::text])));
ALTER TABLE cam.audience_members ADD CONSTRAINT audience_members_pkey PRIMARY KEY (id);
ALTER TABLE cam.audience_members ADD CONSTRAINT uq_audience_members_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.audience_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  group_id uuid NOT NULL,
  rule_type text NOT NULL,
  criteria jsonb DEFAULT '{}'::jsonb NOT NULL,
  priority integer DEFAULT 100 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.audience_rules ADD CONSTRAINT audience_rules_pkey PRIMARY KEY (id);
ALTER TABLE cam.audience_rules ADD CONSTRAINT uq_audience_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.audience_snapshots (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  owner_type text NOT NULL,
  owner_id uuid NOT NULL,
  person_count integer NOT NULL,
  person_ids uuid[],
  criteria jsonb,
  frozen_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.audience_snapshots ADD CONSTRAINT ck_audience_snapshots_owner_type CHECK ((owner_type = ANY (ARRAY['CAMPAIGN'::text, 'MESSAGE'::text, 'SURVEY'::text, 'ALERT'::text, 'NOTIFICATION_RULE'::text])));
ALTER TABLE cam.audience_snapshots ADD CONSTRAINT ck_audience_snapshots_person_count CHECK ((person_count >= 0));
ALTER TABLE cam.audience_snapshots ADD CONSTRAINT audience_snapshots_pkey PRIMARY KEY (id);
ALTER TABLE cam.audience_snapshots ADD CONSTRAINT uq_audience_snapshots_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.campaigns (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  name text NOT NULL,
  campaign_type text DEFAULT 'ANNOUNCEMENT'::text NOT NULL,
  audience_group_id uuid,
  scheduled_at timestamp with time zone,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.campaigns ADD CONSTRAINT ck_campaigns_campaign_type CHECK ((campaign_type = ANY (ARRAY['ANNOUNCEMENT'::text, 'REMINDER'::text, 'MARKETING'::text, 'ALERT'::text, 'EVENT'::text])));
ALTER TABLE cam.campaigns ADD CONSTRAINT ck_campaigns_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SCHEDULED'::text, 'SENDING'::text, 'COMPLETED'::text, 'CANCELLED'::text, 'FAILED'::text])));
ALTER TABLE cam.campaigns ADD CONSTRAINT campaigns_pkey PRIMARY KEY (id);
ALTER TABLE cam.campaigns ADD CONSTRAINT uq_campaigns_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.deliveries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  message_id uuid,
  recipient_id uuid,
  person_id uuid,
  source_type text,
  source_id uuid,
  channel text NOT NULL,
  provider_id uuid,
  to_address text,
  provider_message_id text,
  status text DEFAULT 'QUEUED'::text NOT NULL,
  attempts integer DEFAULT 0 NOT NULL,
  next_attempt_at timestamp with time zone,
  sent_at timestamp with time zone,
  delivered_at timestamp with time zone,
  failure_reason text,
  idempotency_key text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.deliveries ADD CONSTRAINT ck_deliveries_channel CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'PUSH'::text, 'WHATSAPP'::text, 'IN_APP'::text, 'VOICE'::text])));
ALTER TABLE cam.deliveries ADD CONSTRAINT ck_deliveries_status CHECK ((status = ANY (ARRAY['QUEUED'::text, 'SENT'::text, 'DELIVERED'::text, 'READ'::text, 'FAILED'::text, 'BOUNCED'::text, 'EXPIRED'::text])));
ALTER TABLE cam.deliveries ADD CONSTRAINT deliveries_pkey PRIMARY KEY (id);
ALTER TABLE cam.deliveries ADD CONSTRAINT uq_deliveries_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.delivery_attempts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  delivery_id uuid NOT NULL,
  attempt_no smallint NOT NULL,
  provider_id uuid,
  status text NOT NULL,
  response_code text,
  error text,
  latency_ms integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.delivery_attempts ADD CONSTRAINT ck_delivery_attempts_attempt_no CHECK ((attempt_no > 0));
ALTER TABLE cam.delivery_attempts ADD CONSTRAINT ck_delivery_attempts_status CHECK ((status = ANY (ARRAY['SUCCESS'::text, 'FAILED'::text, 'TIMEOUT'::text])));
ALTER TABLE cam.delivery_attempts ADD CONSTRAINT delivery_attempts_pkey PRIMARY KEY (id);
ALTER TABLE cam.delivery_attempts ADD CONSTRAINT uq_delivery_attempts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.delivery_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  delivery_id uuid,
  provider_id uuid,
  provider_message_id text,
  event_type text NOT NULL,
  payload jsonb,
  occurred_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.delivery_events ADD CONSTRAINT ck_delivery_events_event_type CHECK ((event_type = ANY (ARRAY['SENT'::text, 'DELIVERED'::text, 'READ'::text, 'CLICKED'::text, 'BOUNCED'::text, 'COMPLAINED'::text, 'FAILED'::text, 'OPTED_OUT'::text])));
ALTER TABLE cam.delivery_events ADD CONSTRAINT delivery_events_pkey PRIMARY KEY (id, occurred_at);

CREATE TABLE IF NOT EXISTS cam.delivery_events_default (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  delivery_id uuid,
  provider_id uuid,
  provider_message_id text,
  event_type text NOT NULL,
  payload jsonb,
  occurred_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.delivery_events_default ADD CONSTRAINT ck_delivery_events_event_type CHECK ((event_type = ANY (ARRAY['SENT'::text, 'DELIVERED'::text, 'READ'::text, 'CLICKED'::text, 'BOUNCED'::text, 'COMPLAINED'::text, 'FAILED'::text, 'OPTED_OUT'::text])));
ALTER TABLE cam.delivery_events_default ADD CONSTRAINT delivery_events_default_pkey PRIMARY KEY (id, occurred_at);

CREATE TABLE IF NOT EXISTS cam.delivery_events_p202610 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  delivery_id uuid,
  provider_id uuid,
  provider_message_id text,
  event_type text NOT NULL,
  payload jsonb,
  occurred_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.delivery_events_p202610 ADD CONSTRAINT ck_delivery_events_event_type CHECK ((event_type = ANY (ARRAY['SENT'::text, 'DELIVERED'::text, 'READ'::text, 'CLICKED'::text, 'BOUNCED'::text, 'COMPLAINED'::text, 'FAILED'::text, 'OPTED_OUT'::text])));
ALTER TABLE cam.delivery_events_p202610 ADD CONSTRAINT delivery_events_p202610_pkey PRIMARY KEY (id, occurred_at);

CREATE TABLE IF NOT EXISTS cam.delivery_events_p202611 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  delivery_id uuid,
  provider_id uuid,
  provider_message_id text,
  event_type text NOT NULL,
  payload jsonb,
  occurred_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.delivery_events_p202611 ADD CONSTRAINT ck_delivery_events_event_type CHECK ((event_type = ANY (ARRAY['SENT'::text, 'DELIVERED'::text, 'READ'::text, 'CLICKED'::text, 'BOUNCED'::text, 'COMPLAINED'::text, 'FAILED'::text, 'OPTED_OUT'::text])));
ALTER TABLE cam.delivery_events_p202611 ADD CONSTRAINT delivery_events_p202611_pkey PRIMARY KEY (id, occurred_at);

CREATE TABLE IF NOT EXISTS cam.delivery_events_p202612 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  delivery_id uuid,
  provider_id uuid,
  provider_message_id text,
  event_type text NOT NULL,
  payload jsonb,
  occurred_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.delivery_events_p202612 ADD CONSTRAINT ck_delivery_events_event_type CHECK ((event_type = ANY (ARRAY['SENT'::text, 'DELIVERED'::text, 'READ'::text, 'CLICKED'::text, 'BOUNCED'::text, 'COMPLAINED'::text, 'FAILED'::text, 'OPTED_OUT'::text])));
ALTER TABLE cam.delivery_events_p202612 ADD CONSTRAINT delivery_events_p202612_pkey PRIMARY KEY (id, occurred_at);

CREATE TABLE IF NOT EXISTS cam.delivery_events_p202701 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  delivery_id uuid,
  provider_id uuid,
  provider_message_id text,
  event_type text NOT NULL,
  payload jsonb,
  occurred_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.delivery_events_p202701 ADD CONSTRAINT ck_delivery_events_event_type CHECK ((event_type = ANY (ARRAY['SENT'::text, 'DELIVERED'::text, 'READ'::text, 'CLICKED'::text, 'BOUNCED'::text, 'COMPLAINED'::text, 'FAILED'::text, 'OPTED_OUT'::text])));
ALTER TABLE cam.delivery_events_p202701 ADD CONSTRAINT delivery_events_p202701_pkey PRIMARY KEY (id, occurred_at);

CREATE TABLE IF NOT EXISTS cam.device_tokens (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  device_id uuid NOT NULL,
  provider_id uuid,
  token text NOT NULL,
  token_type text DEFAULT 'FCM'::text NOT NULL,
  subscription jsonb,
  is_valid boolean DEFAULT true NOT NULL,
  invalidated_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.device_tokens ADD CONSTRAINT ck_device_tokens_token_type CHECK ((token_type = ANY (ARRAY['FCM'::text, 'APNS'::text, 'WEBPUSH'::text])));
ALTER TABLE cam.device_tokens ADD CONSTRAINT device_tokens_pkey PRIMARY KEY (id);
ALTER TABLE cam.device_tokens ADD CONSTRAINT uq_device_tokens_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.devices (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  platform text NOT NULL,
  device_name text,
  app_version text,
  last_seen_at timestamp with time zone,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.devices ADD CONSTRAINT ck_devices_platform CHECK ((platform = ANY (ARRAY['ANDROID'::text, 'IOS'::text, 'WEB'::text])));
ALTER TABLE cam.devices ADD CONSTRAINT devices_pkey PRIMARY KEY (id);
ALTER TABLE cam.devices ADD CONSTRAINT uq_devices_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.emergency_alerts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  alert_type text NOT NULL,
  severity text DEFAULT 'HIGH'::text NOT NULL,
  title text NOT NULL,
  body text NOT NULL,
  audience_group_id uuid,
  triggered_by uuid,
  triggered_at timestamp with time zone DEFAULT now() NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  all_clear_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT ck_emergency_alerts_alert_type CHECK ((alert_type = ANY (ARRAY['FIRE'::text, 'WEATHER'::text, 'SECURITY'::text, 'MEDICAL'::text, 'CAMPUS_CLOSURE'::text, 'OTHER'::text])));
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT ck_emergency_alerts_severity CHECK ((severity = ANY (ARRAY['LOW'::text, 'MEDIUM'::text, 'HIGH'::text, 'CRITICAL'::text])));
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT ck_emergency_alerts_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ESCALATED'::text, 'ALL_CLEAR'::text, 'CANCELLED'::text])));
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT emergency_alerts_pkey PRIMARY KEY (id);
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT uq_emergency_alerts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.message_recipients (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  message_id uuid NOT NULL,
  person_id uuid NOT NULL,
  address text,
  status text DEFAULT 'PENDING'::text NOT NULL,
  read_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.message_recipients ADD CONSTRAINT ck_message_recipients_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'QUEUED'::text, 'SENT'::text, 'DELIVERED'::text, 'READ'::text, 'FAILED'::text, 'BOUNCED'::text, 'OPTED_OUT'::text])));
ALTER TABLE cam.message_recipients ADD CONSTRAINT message_recipients_pkey PRIMARY KEY (id);
ALTER TABLE cam.message_recipients ADD CONSTRAINT uq_message_recipients_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.messages (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  campaign_id uuid,
  template_id uuid,
  channel text NOT NULL,
  subject text,
  body text,
  payload jsonb DEFAULT '{}'::jsonb,
  priority text DEFAULT 'NORMAL'::text NOT NULL,
  scheduled_at timestamp with time zone,
  expires_at timestamp with time zone,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_by_user_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.messages ADD CONSTRAINT ck_messages_channel CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'PUSH'::text, 'WHATSAPP'::text, 'IN_APP'::text])));
ALTER TABLE cam.messages ADD CONSTRAINT ck_messages_priority CHECK ((priority = ANY (ARRAY['LOW'::text, 'NORMAL'::text, 'HIGH'::text, 'URGENT'::text])));
ALTER TABLE cam.messages ADD CONSTRAINT ck_messages_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'QUEUED'::text, 'SENDING'::text, 'SENT'::text, 'PARTIALLY_SENT'::text, 'FAILED'::text, 'CANCELLED'::text])));
ALTER TABLE cam.messages ADD CONSTRAINT messages_pkey PRIMARY KEY (id);
ALTER TABLE cam.messages ADD CONSTRAINT uq_messages_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.notification_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  event_type text NOT NULL,
  condition jsonb DEFAULT '{}'::jsonb,
  template_id uuid,
  channels text[] DEFAULT '{}'::text[],
  audience_group_id uuid,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.notification_rules ADD CONSTRAINT notification_rules_pkey PRIMARY KEY (id);
ALTER TABLE cam.notification_rules ADD CONSTRAINT uq_notification_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.provider_health (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  provider_id uuid NOT NULL,
  status text NOT NULL,
  latency_ms integer,
  success_rate_pct numeric(9,4),
  checked_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.provider_health ADD CONSTRAINT ck_provider_health_status CHECK ((status = ANY (ARRAY['HEALTHY'::text, 'DEGRADED'::text, 'DOWN'::text])));
ALTER TABLE cam.provider_health ADD CONSTRAINT provider_health_pkey PRIMARY KEY (id);
ALTER TABLE cam.provider_health ADD CONSTRAINT uq_provider_health_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.provider_routes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  channel text NOT NULL,
  provider_id uuid NOT NULL,
  match_rule jsonb DEFAULT '{}'::jsonb,
  priority integer DEFAULT 100 NOT NULL,
  is_fallback boolean DEFAULT false NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.provider_routes ADD CONSTRAINT provider_routes_pkey PRIMARY KEY (id);
ALTER TABLE cam.provider_routes ADD CONSTRAINT uq_provider_routes_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.providers (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  channel text NOT NULL,
  provider_type text,
  priority integer DEFAULT 100 NOT NULL,
  secret_ref uuid,
  config jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.providers ADD CONSTRAINT ck_providers_channel CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'PUSH'::text, 'WHATSAPP'::text, 'VOICE'::text])));
ALTER TABLE cam.providers ADD CONSTRAINT ck_providers_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'DISABLED'::text, 'DEGRADED'::text])));
ALTER TABLE cam.providers ADD CONSTRAINT providers_pkey PRIMARY KEY (id);
ALTER TABLE cam.providers ADD CONSTRAINT uq_providers_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.rule_evaluations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  rule_id uuid NOT NULL,
  event_ref text,
  matched boolean NOT NULL,
  detail jsonb,
  evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE cam.rule_evaluations ADD CONSTRAINT rule_evaluations_pkey PRIMARY KEY (id);
ALTER TABLE cam.rule_evaluations ADD CONSTRAINT uq_rule_evaluations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.survey_answers (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  response_id uuid NOT NULL,
  question_id uuid NOT NULL,
  answer_text text,
  answer_value numeric(18,2),
  answer_options text[],
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.survey_answers ADD CONSTRAINT survey_answers_pkey PRIMARY KEY (id);
ALTER TABLE cam.survey_answers ADD CONSTRAINT uq_survey_answers_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.survey_questions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  survey_id uuid NOT NULL,
  seq integer DEFAULT 1 NOT NULL,
  question_type text NOT NULL,
  prompt text NOT NULL,
  options jsonb,
  is_required boolean DEFAULT true NOT NULL,
  outcome_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.survey_questions ADD CONSTRAINT ck_survey_questions_question_type CHECK ((question_type = ANY (ARRAY['SINGLE'::text, 'MULTI'::text, 'TEXT'::text, 'RATING'::text, 'SCALE'::text, 'YES_NO'::text])));
ALTER TABLE cam.survey_questions ADD CONSTRAINT survey_questions_pkey PRIMARY KEY (id);
ALTER TABLE cam.survey_questions ADD CONSTRAINT uq_survey_questions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.survey_responses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  survey_id uuid NOT NULL,
  respondent_person_id uuid,
  submitted_at timestamp with time zone,
  status text DEFAULT 'IN_PROGRESS'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.survey_responses ADD CONSTRAINT ck_survey_responses_status CHECK ((status = ANY (ARRAY['IN_PROGRESS'::text, 'SUBMITTED'::text])));
ALTER TABLE cam.survey_responses ADD CONSTRAINT survey_responses_pkey PRIMARY KEY (id);
ALTER TABLE cam.survey_responses ADD CONSTRAINT uq_survey_responses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cam.surveys (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  title text NOT NULL,
  description text,
  survey_type text,
  audience_group_id uuid,
  opens_at timestamp with time zone,
  closes_at timestamp with time zone,
  is_anonymous boolean DEFAULT false NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cam.surveys ADD CONSTRAINT ck_surveys_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'OPEN'::text, 'CLOSED'::text, 'ARCHIVED'::text])));
ALTER TABLE cam.surveys ADD CONSTRAINT ck_surveys_survey_type CHECK ((survey_type = ANY (ARRAY['FEEDBACK'::text, 'COURSE_EXIT'::text, 'PULSE'::text, 'OBE_INDIRECT'::text, 'STAKEHOLDER'::text])));
ALTER TABLE cam.surveys ADD CONSTRAINT surveys_pkey PRIMARY KEY (id);
ALTER TABLE cam.surveys ADD CONSTRAINT uq_surveys_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cfg.data_classifications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  classification_code text NOT NULL,
  sensitivity_level text NOT NULL,
  encryption_required boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.data_classifications ADD CONSTRAINT ck_data_classifications_sensitivity_level CHECK ((sensitivity_level = ANY (ARRAY['LOW'::text, 'MEDIUM'::text, 'HIGH'::text, 'RESTRICTED'::text])));
ALTER TABLE cfg.data_classifications ADD CONSTRAINT data_classifications_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS cfg.data_retention_policies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  policy_code text NOT NULL,
  entity_type text NOT NULL,
  retention_days integer NOT NULL,
  action text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.data_retention_policies ADD CONSTRAINT ck_data_retention_policies_action CHECK ((action = ANY (ARRAY['ARCHIVE'::text, 'DELETE'::text, 'ANONYMIZE'::text])));
ALTER TABLE cfg.data_retention_policies ADD CONSTRAINT ck_data_retention_policies_retention_days CHECK ((retention_days > 0));
ALTER TABLE cfg.data_retention_policies ADD CONSTRAINT data_retention_policies_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS cfg.feature_flag_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  flag_id uuid NOT NULL,
  rule_type text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb,
  priority integer DEFAULT 100 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.feature_flag_rules ADD CONSTRAINT feature_flag_rules_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS cfg.feature_flags (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  flag_key text NOT NULL,
  name text,
  description text,
  enabled boolean DEFAULT false NOT NULL,
  rollout_percentage smallint,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.feature_flags ADD CONSTRAINT ck_feature_flags_rollout_percentage CHECK (((rollout_percentage >= 0) AND (rollout_percentage <= 100)));
ALTER TABLE cfg.feature_flags ADD CONSTRAINT feature_flags_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS cfg.feature_overrides (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  flag_id uuid NOT NULL,
  college_id uuid,
  enabled boolean NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.feature_overrides ADD CONSTRAINT feature_overrides_pkey PRIMARY KEY (id);
ALTER TABLE cfg.feature_overrides ADD CONSTRAINT uq_feature_overrides_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS cfg.policies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  policy_code text NOT NULL,
  name text,
  category text,
  rules jsonb DEFAULT '{}'::jsonb NOT NULL,
  enforcement_mode text DEFAULT 'ENFORCE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.policies ADD CONSTRAINT ck_policies_enforcement_mode CHECK ((enforcement_mode = ANY (ARRAY['ENFORCE'::text, 'AUDIT'::text, 'DISABLED'::text])));
ALTER TABLE cfg.policies ADD CONSTRAINT policies_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS cfg.setting_definitions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  setting_key text NOT NULL,
  data_type text NOT NULL,
  default_value jsonb,
  is_secret boolean DEFAULT false NOT NULL,
  allowed_scopes text[] DEFAULT '{INSTITUTE,COLLEGE}'::text[] NOT NULL,
  description text,
  effective_from date,
  effective_to date,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.setting_definitions ADD CONSTRAINT ck_setting_definitions_data_type CHECK ((data_type = ANY (ARRAY['STRING'::text, 'INTEGER'::text, 'BOOLEAN'::text, 'JSON'::text, 'SECRET'::text])));
ALTER TABLE cfg.setting_definitions ADD CONSTRAINT setting_definitions_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS cfg.setting_values (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  setting_key text NOT NULL,
  scope text NOT NULL,
  college_id uuid,
  value jsonb,
  secret_ref uuid,
  is_overridden boolean DEFAULT false NOT NULL,
  effective_from date,
  effective_to date,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE cfg.setting_values ADD CONSTRAINT ck_setting_values_scope CHECK ((scope = ANY (ARRAY['INSTITUTE'::text, 'COLLEGE'::text])));
ALTER TABLE cfg.setting_values ADD CONSTRAINT ck_setting_values_x1 CHECK (((scope = 'COLLEGE'::text) = (college_id IS NOT NULL)));
ALTER TABLE cfg.setting_values ADD CONSTRAINT setting_values_pkey PRIMARY KEY (id);
ALTER TABLE cfg.setting_values ADD CONSTRAINT uq_setting_values_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.academic_years (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  is_current boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.academic_years ADD CONSTRAINT ck_academic_years_x1 CHECK ((end_date > start_date));
ALTER TABLE core.academic_years ADD CONSTRAINT academic_years_pkey PRIMARY KEY (id);
ALTER TABLE core.academic_years ADD CONSTRAINT uq_academic_years_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.addresses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  line1 text NOT NULL,
  line2 text,
  landmark text,
  city text,
  district text,
  state_code text,
  postal_code text,
  country_code character(3) DEFAULT 'IND'::bpchar NOT NULL,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.addresses ADD CONSTRAINT addresses_pkey PRIMARY KEY (id);
ALTER TABLE core.addresses ADD CONSTRAINT uq_addresses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.calendar_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  calendar_id uuid NOT NULL,
  event_type text NOT NULL,
  title text NOT NULL,
  description text,
  start_date date NOT NULL,
  end_date date NOT NULL,
  is_holiday boolean DEFAULT false NOT NULL,
  applies_to jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.calendar_events ADD CONSTRAINT ck_calendar_events_event_type CHECK ((event_type = ANY (ARRAY['HOLIDAY'::text, 'TERM_START'::text, 'TERM_END'::text, 'EXAM'::text, 'COMMENCEMENT'::text, 'EVENT'::text, 'DEADLINE'::text, 'OTHER'::text])));
ALTER TABLE core.calendar_events ADD CONSTRAINT ck_calendar_events_x1 CHECK ((end_date >= start_date));
ALTER TABLE core.calendar_events ADD CONSTRAINT calendar_events_pkey PRIMARY KEY (id);
ALTER TABLE core.calendar_events ADD CONSTRAINT uq_calendar_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.calendar_working_days (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  calendar_id uuid NOT NULL,
  day_of_week smallint NOT NULL,
  is_working boolean DEFAULT true NOT NULL,
  is_half_day boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.calendar_working_days ADD CONSTRAINT ck_calendar_working_days_day_of_week CHECK ((day_of_week >= 0));
ALTER TABLE core.calendar_working_days ADD CONSTRAINT ck_calendar_working_days_x1 CHECK (((day_of_week >= 0) AND (day_of_week <= 6)));
ALTER TABLE core.calendar_working_days ADD CONSTRAINT calendar_working_days_pkey PRIMARY KEY (id);
ALTER TABLE core.calendar_working_days ADD CONSTRAINT uq_calendar_working_days_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.calendars (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  academic_year_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  calendar_type text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  published_at timestamp with time zone,
  week_start_day smallint DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.calendars ADD CONSTRAINT ck_calendars_calendar_type CHECK ((calendar_type = ANY (ARRAY['ACADEMIC'::text, 'HOLIDAY'::text, 'EXAM'::text, 'ADMINISTRATIVE'::text])));
ALTER TABLE core.calendars ADD CONSTRAINT ck_calendars_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'ARCHIVED'::text])));
ALTER TABLE core.calendars ADD CONSTRAINT calendars_pkey PRIMARY KEY (id);
ALTER TABLE core.calendars ADD CONSTRAINT uq_calendars_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.campuses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  address_id uuid,
  is_primary boolean DEFAULT false NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.campuses ADD CONSTRAINT ck_campuses_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text])));
ALTER TABLE core.campuses ADD CONSTRAINT campuses_pkey PRIMARY KEY (id);
ALTER TABLE core.campuses ADD CONSTRAINT uq_campuses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.college_accreditations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  body text NOT NULL,
  grade_or_status text,
  score numeric(18,2),
  reference_no text,
  valid_from date,
  valid_to date,
  certificate_document_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.college_accreditations ADD CONSTRAINT ck_college_accreditations_body CHECK ((body = ANY (ARRAY['NAAC'::text, 'NBA'::text, 'UGC'::text, 'AICTE'::text, 'NIRF'::text, 'ISO'::text, 'OTHER'::text])));
ALTER TABLE core.college_accreditations ADD CONSTRAINT ck_college_accreditations_x1 CHECK (((valid_to IS NULL) OR (valid_from IS NULL) OR (valid_to >= valid_from)));
ALTER TABLE core.college_accreditations ADD CONSTRAINT college_accreditations_pkey PRIMARY KEY (id);
ALTER TABLE core.college_accreditations ADD CONSTRAINT uq_college_accreditations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.colleges (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  legal_name text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  provisioning_status text DEFAULT 'REQUESTED'::text NOT NULL,
  affiliating_university text,
  established_year smallint,
  address_id uuid,
  email text,
  phone text,
  website text,
  profile jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.colleges ADD CONSTRAINT ck_colleges_provisioning_status CHECK ((provisioning_status = ANY (ARRAY['REQUESTED'::text, 'VALIDATED'::text, 'PROVISIONING'::text, 'ACTIVE'::text, 'SUSPENDED'::text, 'FAILED'::text])));
ALTER TABLE core.colleges ADD CONSTRAINT ck_colleges_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text])));
ALTER TABLE core.colleges ADD CONSTRAINT ck_colleges_x1 CHECK ((code ~ '^[A-Z0-9_-]{2,30}$'::text));
ALTER TABLE core.colleges ADD CONSTRAINT colleges_pkey PRIMARY KEY (id);
ALTER TABLE core.colleges ADD CONSTRAINT uq_colleges_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.comments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  body text NOT NULL,
  is_internal boolean DEFAULT true NOT NULL,
  parent_comment_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.comments ADD CONSTRAINT comments_pkey PRIMARY KEY (id);
ALTER TABLE core.comments ADD CONSTRAINT uq_comments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.departments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  parent_department_id uuid,
  head_employee_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.departments ADD CONSTRAINT ck_departments_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE core.departments ADD CONSTRAINT departments_pkey PRIMARY KEY (id);
ALTER TABLE core.departments ADD CONSTRAINT uq_departments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.entity_tags (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  tag_id uuid NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE core.entity_tags ADD CONSTRAINT entity_tags_pkey PRIMARY KEY (id);
ALTER TABLE core.entity_tags ADD CONSTRAINT uq_entity_tags_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.entity_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  version_no integer NOT NULL,
  status text,
  effective_from timestamp with time zone,
  effective_to timestamp with time zone,
  change_reason text,
  snapshot jsonb DEFAULT '{}'::jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE core.entity_versions ADD CONSTRAINT ck_entity_versions_version_no CHECK ((version_no > 0));
ALTER TABLE core.entity_versions ADD CONSTRAINT entity_versions_pkey PRIMARY KEY (id);
ALTER TABLE core.entity_versions ADD CONSTRAINT uq_entity_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.lookup_types (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  is_system boolean DEFAULT false NOT NULL,
  description text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.lookup_types ADD CONSTRAINT lookup_types_pkey PRIMARY KEY (id);
ALTER TABLE core.lookup_types ADD CONSTRAINT uq_lookup_types_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.lookup_values (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  lookup_type_id uuid NOT NULL,
  code text NOT NULL,
  label text NOT NULL,
  sort_order integer DEFAULT 0 NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  attrs jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.lookup_values ADD CONSTRAINT lookup_values_pkey PRIMARY KEY (id);
ALTER TABLE core.lookup_values ADD CONSTRAINT uq_lookup_values_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.number_sequences (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scope_key text NOT NULL,
  prefix text,
  suffix text,
  next_value bigint DEFAULT 1 NOT NULL,
  padding smallint DEFAULT 6 NOT NULL,
  reset_policy text DEFAULT 'NEVER'::text NOT NULL,
  last_reset_on date,
  required_permission text,
  college_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.number_sequences ADD CONSTRAINT ck_number_sequences_next_value CHECK ((next_value >= 0));
ALTER TABLE core.number_sequences ADD CONSTRAINT ck_number_sequences_reset_policy CHECK ((reset_policy = ANY (ARRAY['NEVER'::text, 'YEARLY'::text, 'MONTHLY'::text, 'ACADEMIC_YEAR'::text])));
ALTER TABLE core.number_sequences ADD CONSTRAINT number_sequences_pkey PRIMARY KEY (id);
ALTER TABLE core.number_sequences ADD CONSTRAINT uq_number_sequences_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.person_contacts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  contact_type text NOT NULL,
  value text NOT NULL,
  is_primary boolean DEFAULT false NOT NULL,
  is_verified boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.person_contacts ADD CONSTRAINT ck_person_contacts_contact_type CHECK ((contact_type = ANY (ARRAY['EMAIL'::text, 'PHONE'::text, 'ALT_PHONE'::text, 'WHATSAPP'::text])));
ALTER TABLE core.person_contacts ADD CONSTRAINT person_contacts_pkey PRIMARY KEY (id);
ALTER TABLE core.person_contacts ADD CONSTRAINT uq_person_contacts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.person_emergency_contacts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  name text NOT NULL,
  relationship text NOT NULL,
  phone text NOT NULL,
  alt_phone text,
  priority smallint DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.person_emergency_contacts ADD CONSTRAINT person_emergency_contacts_pkey PRIMARY KEY (id);
ALTER TABLE core.person_emergency_contacts ADD CONSTRAINT uq_person_emergency_contacts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.person_experience (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  organisation text NOT NULL,
  designation text,
  from_date date,
  to_date date,
  is_teaching boolean DEFAULT false NOT NULL,
  responsibilities text,
  document_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.person_experience ADD CONSTRAINT ck_person_experience_x1 CHECK (((to_date IS NULL) OR (from_date IS NULL) OR (to_date >= from_date)));
ALTER TABLE core.person_experience ADD CONSTRAINT person_experience_pkey PRIMARY KEY (id);
ALTER TABLE core.person_experience ADD CONSTRAINT uq_person_experience_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.person_identifiers (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  id_type text NOT NULL,
  masked_value text,
  value_ciphertext bytea,
  value_hash bytea,
  issuing_authority text,
  valid_from date,
  valid_to date,
  is_verified boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.person_identifiers ADD CONSTRAINT ck_person_identifiers_id_type CHECK ((id_type = ANY (ARRAY['AADHAAR'::text, 'PAN'::text, 'PASSPORT'::text, 'DRIVING_LICENSE'::text, 'VOTER_ID'::text, 'UAN'::text, 'ESI'::text, 'APAAR'::text, 'OTHER'::text])));
ALTER TABLE core.person_identifiers ADD CONSTRAINT person_identifiers_pkey PRIMARY KEY (id);
ALTER TABLE core.person_identifiers ADD CONSTRAINT uq_person_identifiers_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.person_qualifications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  level_code text NOT NULL,
  degree text NOT NULL,
  specialization text,
  institution text,
  board_or_university text,
  year_of_passing smallint,
  grade_or_percentage text,
  document_id uuid,
  is_verified boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.person_qualifications ADD CONSTRAINT person_qualifications_pkey PRIMARY KEY (id);
ALTER TABLE core.person_qualifications ADD CONSTRAINT uq_person_qualifications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.persons (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  first_name text NOT NULL,
  middle_name text,
  last_name text,
  display_name text NOT NULL,
  date_of_birth date,
  gender text,
  blood_group text,
  nationality text,
  marital_status text,
  primary_email text,
  primary_phone text,
  photo_document_id uuid,
  permanent_address_id uuid,
  current_address_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.persons ADD CONSTRAINT ck_persons_gender CHECK ((gender = ANY (ARRAY['M'::text, 'F'::text, 'O'::text, 'U'::text])));
ALTER TABLE core.persons ADD CONSTRAINT ck_persons_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])));
ALTER TABLE core.persons ADD CONSTRAINT persons_pkey PRIMARY KEY (id);
ALTER TABLE core.persons ADD CONSTRAINT uq_persons_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.publications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  version_no integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'PUBLISHED'::text NOT NULL,
  audience jsonb DEFAULT '{}'::jsonb,
  published_by uuid,
  published_at timestamp with time zone,
  unpublished_at timestamp with time zone,
  notes text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.publications ADD CONSTRAINT ck_publications_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SCHEDULED'::text, 'PUBLISHED'::text, 'UNPUBLISHED'::text, 'WITHDRAWN'::text])));
ALTER TABLE core.publications ADD CONSTRAINT publications_pkey PRIMARY KEY (id);
ALTER TABLE core.publications ADD CONSTRAINT uq_publications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.rooms (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  campus_id uuid NOT NULL,
  building text,
  code text NOT NULL,
  name text,
  room_type text DEFAULT 'CLASSROOM'::text NOT NULL,
  capacity integer,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.rooms ADD CONSTRAINT ck_rooms_capacity CHECK ((capacity >= 0));
ALTER TABLE core.rooms ADD CONSTRAINT ck_rooms_room_type CHECK ((room_type = ANY (ARRAY['CLASSROOM'::text, 'LAB'::text, 'SEMINAR'::text, 'AUDITORIUM'::text, 'EXAM_HALL'::text, 'OFFICE'::text, 'OTHER'::text])));
ALTER TABLE core.rooms ADD CONSTRAINT ck_rooms_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text, 'MAINTENANCE'::text])));
ALTER TABLE core.rooms ADD CONSTRAINT rooms_pkey PRIMARY KEY (id);
ALTER TABLE core.rooms ADD CONSTRAINT uq_rooms_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.tags (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  label text NOT NULL,
  color text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.tags ADD CONSTRAINT tags_pkey PRIMARY KEY (id);
ALTER TABLE core.tags ADD CONSTRAINT uq_tags_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS core.tenants (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  legal_name text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  currency_code character(3) DEFAULT 'INR'::bpchar NOT NULL,
  timezone text DEFAULT 'Asia/Kolkata'::text NOT NULL,
  locale text DEFAULT 'en-IN'::text NOT NULL,
  profile jsonb DEFAULT '{}'::jsonb,
  onboarded_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE core.tenants ADD CONSTRAINT ck_tenants_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'INACTIVE'::text, 'ARCHIVED'::text])));
ALTER TABLE core.tenants ADD CONSTRAINT ck_tenants_x1 CHECK ((code ~ '^[A-Z0-9_-]{2,30}$'::text));
ALTER TABLE core.tenants ADD CONSTRAINT tenants_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS doc.document_deliveries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid NOT NULL,
  recipient_person_id uuid,
  channel text NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  delivered_at timestamp with time zone,
  client_ip inet,
  delivery_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.document_deliveries ADD CONSTRAINT ck_document_deliveries_channel CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'PORTAL'::text, 'DOWNLOAD'::text, 'PRINT'::text, 'COURIER'::text])));
ALTER TABLE doc.document_deliveries ADD CONSTRAINT ck_document_deliveries_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'SENT'::text, 'DELIVERED'::text, 'FAILED'::text, 'DOWNLOADED'::text])));
ALTER TABLE doc.document_deliveries ADD CONSTRAINT document_deliveries_pkey PRIMARY KEY (id);
ALTER TABLE doc.document_deliveries ADD CONSTRAINT uq_document_deliveries_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.document_links (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  relation text DEFAULT 'ATTACHMENT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE doc.document_links ADD CONSTRAINT document_links_pkey PRIMARY KEY (id);
ALTER TABLE doc.document_links ADD CONSTRAINT uq_document_links_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.document_renditions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid NOT NULL,
  rendition_type text NOT NULL,
  storage_path text NOT NULL,
  size_bytes bigint,
  status text DEFAULT 'READY'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.document_renditions ADD CONSTRAINT ck_document_renditions_rendition_type CHECK ((rendition_type = ANY (ARRAY['THUMBNAIL'::text, 'PREVIEW'::text, 'TRANSCODE'::text, 'OCR'::text, 'PDF'::text])));
ALTER TABLE doc.document_renditions ADD CONSTRAINT ck_document_renditions_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'READY'::text, 'FAILED'::text])));
ALTER TABLE doc.document_renditions ADD CONSTRAINT document_renditions_pkey PRIMARY KEY (id);
ALTER TABLE doc.document_renditions ADD CONSTRAINT uq_document_renditions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.document_types (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  category text,
  module text,
  default_classification text DEFAULT 'INTERNAL'::text NOT NULL,
  requires_signature boolean DEFAULT false NOT NULL,
  retention_policy_code text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.document_types ADD CONSTRAINT ck_document_types_default_classification CHECK ((default_classification = ANY (ARRAY['PUBLIC'::text, 'INTERNAL'::text, 'CONFIDENTIAL'::text, 'RESTRICTED'::text])));
ALTER TABLE doc.document_types ADD CONSTRAINT document_types_pkey PRIMARY KEY (id);
ALTER TABLE doc.document_types ADD CONSTRAINT uq_document_types_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.document_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid NOT NULL,
  version_no integer NOT NULL,
  storage_path text NOT NULL,
  size_bytes bigint,
  checksum_sha256 text,
  change_note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE doc.document_versions ADD CONSTRAINT ck_document_versions_version_no CHECK ((version_no > 0));
ALTER TABLE doc.document_versions ADD CONSTRAINT document_versions_pkey PRIMARY KEY (id);
ALTER TABLE doc.document_versions ADD CONSTRAINT uq_document_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.documents (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  document_type_id uuid,
  title text NOT NULL,
  classification text DEFAULT 'INTERNAL'::text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  owner_id uuid,
  storage_bucket text,
  storage_path text,
  mime_type text,
  size_bytes bigint,
  checksum_sha256 text,
  current_version_no integer DEFAULT 1 NOT NULL,
  published_at timestamp with time zone,
  expires_at timestamp with time zone,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.documents ADD CONSTRAINT ck_documents_classification CHECK ((classification = ANY (ARRAY['PUBLIC'::text, 'INTERNAL'::text, 'CONFIDENTIAL'::text, 'RESTRICTED'::text])));
ALTER TABLE doc.documents ADD CONSTRAINT ck_documents_size_bytes CHECK ((size_bytes >= 0));
ALTER TABLE doc.documents ADD CONSTRAINT ck_documents_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'APPROVED'::text, 'REJECTED'::text, 'PUBLISHED'::text, 'ARCHIVED'::text, 'CANCELLED'::text])));
ALTER TABLE doc.documents ADD CONSTRAINT documents_pkey PRIMARY KEY (id);
ALTER TABLE doc.documents ADD CONSTRAINT uq_documents_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.lifecycle_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid,
  entity_type text,
  entity_id uuid,
  request_type text NOT NULL,
  reason text NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  requested_by uuid,
  workflow_instance_id uuid,
  new_document_id uuid,
  completed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT ck_lifecycle_requests_request_type CHECK ((request_type = ANY (ARRAY['REISSUE'::text, 'CANCEL'::text, 'CORRECTION'::text])));
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT ck_lifecycle_requests_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'COMPLETED'::text])));
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT lifecycle_requests_pkey PRIMARY KEY (id);
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT uq_lifecycle_requests_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.print_job_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  print_job_id uuid NOT NULL,
  document_id uuid,
  seq integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'QUEUED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.print_job_items ADD CONSTRAINT print_job_items_pkey PRIMARY KEY (id);
ALTER TABLE doc.print_job_items ADD CONSTRAINT uq_print_job_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.print_jobs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid,
  entity_type text,
  entity_id uuid,
  requested_by uuid,
  copies integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'QUEUED'::text NOT NULL,
  printed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.print_jobs ADD CONSTRAINT ck_print_jobs_status CHECK ((status = ANY (ARRAY['QUEUED'::text, 'PRINTING'::text, 'DONE'::text, 'FAILED'::text])));
ALTER TABLE doc.print_jobs ADD CONSTRAINT print_jobs_pkey PRIMARY KEY (id);
ALTER TABLE doc.print_jobs ADD CONSTRAINT uq_print_jobs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.signatures (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid NOT NULL,
  signer_id uuid,
  signature_type text NOT NULL,
  signed_at timestamp with time zone NOT NULL,
  certificate_ref text,
  signature_hash text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE doc.signatures ADD CONSTRAINT ck_signatures_signature_type CHECK ((signature_type = ANY (ARRAY['DIGITAL'::text, 'ESIGN'::text, 'STAMP'::text])));
ALTER TABLE doc.signatures ADD CONSTRAINT signatures_pkey PRIMARY KEY (id);
ALTER TABLE doc.signatures ADD CONSTRAINT uq_signatures_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.template_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  template_id uuid NOT NULL,
  version_no integer NOT NULL,
  subject text,
  body text NOT NULL,
  variables jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  published_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.template_versions ADD CONSTRAINT ck_template_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'RETIRED'::text])));
ALTER TABLE doc.template_versions ADD CONSTRAINT ck_template_versions_version_no CHECK ((version_no > 0));
ALTER TABLE doc.template_versions ADD CONSTRAINT template_versions_pkey PRIMARY KEY (id);
ALTER TABLE doc.template_versions ADD CONSTRAINT uq_template_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.templates (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  template_kind text NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  channel text,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  active_version_no integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE doc.templates ADD CONSTRAINT ck_templates_channel CHECK ((channel = ANY (ARRAY['EMAIL'::text, 'SMS'::text, 'PUSH'::text, 'WHATSAPP'::text, 'IN_APP'::text, 'PRINT'::text])));
ALTER TABLE doc.templates ADD CONSTRAINT ck_templates_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE doc.templates ADD CONSTRAINT ck_templates_template_kind CHECK ((template_kind = ANY (ARRAY['DOCUMENT'::text, 'HALL_TICKET'::text, 'RECEIPT'::text, 'PAYSLIP'::text, 'MESSAGE'::text, 'REPORT'::text, 'CERTIFICATE'::text])));
ALTER TABLE doc.templates ADD CONSTRAINT templates_pkey PRIMARY KEY (id);
ALTER TABLE doc.templates ADD CONSTRAINT uq_templates_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS doc.verifications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  document_id uuid,
  entity_type text,
  entity_id uuid,
  verification_code text NOT NULL,
  result text NOT NULL,
  verified_at timestamp with time zone DEFAULT now() NOT NULL,
  client_ip inet,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE doc.verifications ADD CONSTRAINT ck_verifications_result CHECK ((result = ANY (ARRAY['VALID'::text, 'INVALID'::text, 'EXPIRED'::text, 'REVOKED'::text])));
ALTER TABLE doc.verifications ADD CONSTRAINT verifications_pkey PRIMARY KEY (id);
ALTER TABLE doc.verifications ADD CONSTRAINT uq_verifications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.answer_scripts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  exam_session_id uuid NOT NULL,
  registration_id uuid NOT NULL,
  barcode text NOT NULL,
  status text DEFAULT 'ISSUED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.answer_scripts ADD CONSTRAINT ck_answer_scripts_status CHECK ((status = ANY (ARRAY['ISSUED'::text, 'SCANNED'::text, 'IN_VALUATION'::text, 'VALUED'::text, 'MODERATED'::text, 'ARCHIVED'::text, 'MISSING'::text])));
ALTER TABLE exm.answer_scripts ADD CONSTRAINT answer_scripts_pkey PRIMARY KEY (id);
ALTER TABLE exm.answer_scripts ADD CONSTRAINT uq_answer_scripts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.eligibility_snapshots (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  registration_id uuid NOT NULL,
  rule_ref text,
  result text NOT NULL,
  detail jsonb,
  evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE exm.eligibility_snapshots ADD CONSTRAINT ck_eligibility_snapshots_result CHECK ((result = ANY (ARRAY['ELIGIBLE'::text, 'INELIGIBLE'::text, 'CONDITIONAL'::text])));
ALTER TABLE exm.eligibility_snapshots ADD CONSTRAINT eligibility_snapshots_pkey PRIMARY KEY (id);
ALTER TABLE exm.eligibility_snapshots ADD CONSTRAINT uq_eligibility_snapshots_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.exam_centres (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  address_id uuid,
  capacity integer,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.exam_centres ADD CONSTRAINT ck_exam_centres_capacity CHECK ((capacity >= 0));
ALTER TABLE exm.exam_centres ADD CONSTRAINT ck_exam_centres_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])));
ALTER TABLE exm.exam_centres ADD CONSTRAINT exam_centres_pkey PRIMARY KEY (id);
ALTER TABLE exm.exam_centres ADD CONSTRAINT uq_exam_centres_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.exam_rooms (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  centre_id uuid NOT NULL,
  room_id uuid,
  code text NOT NULL,
  capacity integer NOT NULL,
  seating_layout jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.exam_rooms ADD CONSTRAINT ck_exam_rooms_capacity CHECK ((capacity >= 0));
ALTER TABLE exm.exam_rooms ADD CONSTRAINT exam_rooms_pkey PRIMARY KEY (id);
ALTER TABLE exm.exam_rooms ADD CONSTRAINT uq_exam_rooms_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.exam_sessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  examination_id uuid NOT NULL,
  exam_subject_id uuid NOT NULL,
  centre_id uuid,
  session_date date NOT NULL,
  start_time time without time zone NOT NULL,
  end_time time without time zone NOT NULL,
  status text DEFAULT 'SCHEDULED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.exam_sessions ADD CONSTRAINT ck_exam_sessions_status CHECK ((status = ANY (ARRAY['SCHEDULED'::text, 'ONGOING'::text, 'COMPLETED'::text, 'CANCELLED'::text, 'POSTPONED'::text])));
ALTER TABLE exm.exam_sessions ADD CONSTRAINT ck_exam_sessions_x1 CHECK ((end_time > start_time));
ALTER TABLE exm.exam_sessions ADD CONSTRAINT exam_sessions_pkey PRIMARY KEY (id);
ALTER TABLE exm.exam_sessions ADD CONSTRAINT uq_exam_sessions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.exam_subjects (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  examination_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  assessment_structure_id uuid,
  max_marks numeric(18,2) NOT NULL,
  pass_marks numeric(18,2),
  duration_min integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.exam_subjects ADD CONSTRAINT ck_exam_subjects_duration_min CHECK ((duration_min >= 0));
ALTER TABLE exm.exam_subjects ADD CONSTRAINT ck_exam_subjects_max_marks CHECK ((max_marks >= (0)::numeric));
ALTER TABLE exm.exam_subjects ADD CONSTRAINT ck_exam_subjects_pass_marks CHECK ((pass_marks >= (0)::numeric));
ALTER TABLE exm.exam_subjects ADD CONSTRAINT exam_subjects_pkey PRIMARY KEY (id);
ALTER TABLE exm.exam_subjects ADD CONSTRAINT uq_exam_subjects_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.examinations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  term_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  exam_type text DEFAULT 'REGULAR'::text NOT NULL,
  status text DEFAULT 'PLANNED'::text NOT NULL,
  registration_start timestamp with time zone,
  registration_end timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.examinations ADD CONSTRAINT ck_examinations_exam_type CHECK ((exam_type = ANY (ARRAY['REGULAR'::text, 'SUPPLEMENTARY'::text, 'BACKLOG'::text, 'IMPROVEMENT'::text])));
ALTER TABLE exm.examinations ADD CONSTRAINT ck_examinations_status CHECK ((status = ANY (ARRAY['PLANNED'::text, 'REGISTRATION_OPEN'::text, 'SCHEDULED'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'RESULTS_PUBLISHED'::text, 'CANCELLED'::text])));
ALTER TABLE exm.examinations ADD CONSTRAINT examinations_pkey PRIMARY KEY (id);
ALTER TABLE exm.examinations ADD CONSTRAINT uq_examinations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.hall_ticket_nocs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  hall_ticket_id uuid NOT NULL,
  noc_type text NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  granted_by uuid,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.hall_ticket_nocs ADD CONSTRAINT ck_hall_ticket_nocs_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'GRANTED'::text, 'DENIED'::text])));
ALTER TABLE exm.hall_ticket_nocs ADD CONSTRAINT hall_ticket_nocs_pkey PRIMARY KEY (id);
ALTER TABLE exm.hall_ticket_nocs ADD CONSTRAINT uq_hall_ticket_nocs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.hall_ticket_subjects (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  hall_ticket_id uuid NOT NULL,
  exam_subject_id uuid NOT NULL,
  session_id uuid,
  seat_no text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.hall_ticket_subjects ADD CONSTRAINT hall_ticket_subjects_pkey PRIMARY KEY (id);
ALTER TABLE exm.hall_ticket_subjects ADD CONSTRAINT uq_hall_ticket_subjects_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.hall_tickets (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  registration_id uuid NOT NULL,
  student_id uuid NOT NULL,
  template_id uuid,
  document_id uuid,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  generated_at timestamp with time zone,
  published_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.hall_tickets ADD CONSTRAINT ck_hall_tickets_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'GENERATED'::text, 'PUBLISHED'::text, 'DOWNLOADED'::text, 'PRINTED'::text, 'REVOKED'::text])));
ALTER TABLE exm.hall_tickets ADD CONSTRAINT hall_tickets_pkey PRIMARY KEY (id);
ALTER TABLE exm.hall_tickets ADD CONSTRAINT uq_hall_tickets_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.invigilator_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  room_id uuid,
  employee_id uuid NOT NULL,
  role text DEFAULT 'INVIGILATOR'::text NOT NULL,
  status text DEFAULT 'ASSIGNED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT ck_invigilator_assignments_role CHECK ((role = ANY (ARRAY['CHIEF'::text, 'INVIGILATOR'::text, 'SQUAD'::text, 'RELIEVER'::text])));
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT ck_invigilator_assignments_status CHECK ((status = ANY (ARRAY['ASSIGNED'::text, 'CONFIRMED'::text, 'DECLINED'::text, 'COMPLETED'::text])));
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT invigilator_assignments_pkey PRIMARY KEY (id);
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT uq_invigilator_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.marks_corrections (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  marks_entry_id uuid NOT NULL,
  old_marks numeric(18,2),
  new_marks numeric(18,2),
  reason text NOT NULL,
  approved_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE exm.marks_corrections ADD CONSTRAINT marks_corrections_pkey PRIMARY KEY (id);
ALTER TABLE exm.marks_corrections ADD CONSTRAINT uq_marks_corrections_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.marks_entries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  answer_script_id uuid NOT NULL,
  valuation_session_id uuid NOT NULL,
  total_marks numeric(18,2) NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  submitted_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.marks_entries ADD CONSTRAINT ck_marks_entries_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'MODERATED'::text, 'FINALIZED'::text])));
ALTER TABLE exm.marks_entries ADD CONSTRAINT ck_marks_entries_total_marks CHECK ((total_marks >= (0)::numeric));
ALTER TABLE exm.marks_entries ADD CONSTRAINT marks_entries_pkey PRIMARY KEY (id);
ALTER TABLE exm.marks_entries ADD CONSTRAINT uq_marks_entries_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.marks_finalizations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  examination_id uuid NOT NULL,
  exam_subject_id uuid,
  finalized_by uuid,
  finalized_at timestamp with time zone DEFAULT now() NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE exm.marks_finalizations ADD CONSTRAINT marks_finalizations_pkey PRIMARY KEY (id);
ALTER TABLE exm.marks_finalizations ADD CONSTRAINT uq_marks_finalizations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.question_marks (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  marks_entry_id uuid NOT NULL,
  question_no text NOT NULL,
  marks numeric(18,2) NOT NULL,
  max_marks numeric(18,2) NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.question_marks ADD CONSTRAINT ck_question_marks_marks CHECK ((marks >= (0)::numeric));
ALTER TABLE exm.question_marks ADD CONSTRAINT ck_question_marks_max_marks CHECK ((max_marks >= (0)::numeric));
ALTER TABLE exm.question_marks ADD CONSTRAINT question_marks_pkey PRIMARY KEY (id);
ALTER TABLE exm.question_marks ADD CONSTRAINT uq_question_marks_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.registration_corrections (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  registration_id uuid NOT NULL,
  field_name text NOT NULL,
  old_value text,
  new_value text,
  reason text,
  approved_by uuid,
  status text DEFAULT 'PENDING'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.registration_corrections ADD CONSTRAINT ck_registration_corrections_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text])));
ALTER TABLE exm.registration_corrections ADD CONSTRAINT registration_corrections_pkey PRIMARY KEY (id);
ALTER TABLE exm.registration_corrections ADD CONSTRAINT uq_registration_corrections_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.registration_subjects (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  registration_id uuid NOT NULL,
  exam_subject_id uuid NOT NULL,
  attempt_no smallint DEFAULT 1 NOT NULL,
  status text DEFAULT 'REGISTERED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.registration_subjects ADD CONSTRAINT ck_registration_subjects_status CHECK ((status = ANY (ARRAY['REGISTERED'::text, 'WITHDRAWN'::text])));
ALTER TABLE exm.registration_subjects ADD CONSTRAINT registration_subjects_pkey PRIMARY KEY (id);
ALTER TABLE exm.registration_subjects ADD CONSTRAINT uq_registration_subjects_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.registrations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  examination_id uuid NOT NULL,
  student_id uuid NOT NULL,
  batch_id uuid,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  submitted_at timestamp with time zone,
  registration_no text,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.registrations ADD CONSTRAINT ck_registrations_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'LOCKED'::text, 'APPROVED'::text, 'REJECTED'::text, 'CANCELLED'::text])));
ALTER TABLE exm.registrations ADD CONSTRAINT registrations_pkey PRIMARY KEY (id);
ALTER TABLE exm.registrations ADD CONSTRAINT uq_registrations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.result_backlogs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  subject_id uuid NOT NULL,
  first_result_id uuid,
  cleared_result_id uuid,
  status text DEFAULT 'PENDING'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.result_backlogs ADD CONSTRAINT ck_result_backlogs_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'CLEARED'::text])));
ALTER TABLE exm.result_backlogs ADD CONSTRAINT result_backlogs_pkey PRIMARY KEY (id);
ALTER TABLE exm.result_backlogs ADD CONSTRAINT uq_result_backlogs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.result_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  examination_id uuid NOT NULL,
  rule_type text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.result_rules ADD CONSTRAINT ck_result_rules_rule_type CHECK ((rule_type = ANY (ARRAY['GRADE'::text, 'PASS'::text, 'GRACE'::text, 'MODERATION'::text])));
ALTER TABLE exm.result_rules ADD CONSTRAINT result_rules_pkey PRIMARY KEY (id);
ALTER TABLE exm.result_rules ADD CONSTRAINT uq_result_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.result_semesters (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  result_id uuid NOT NULL,
  term_id uuid NOT NULL,
  sgpa numeric(18,2),
  credits_earned numeric(18,2),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.result_semesters ADD CONSTRAINT result_semesters_pkey PRIMARY KEY (id);
ALTER TABLE exm.result_semesters ADD CONSTRAINT uq_result_semesters_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.result_subjects (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  result_id uuid NOT NULL,
  exam_subject_id uuid NOT NULL,
  marks_obtained numeric(18,2),
  max_marks numeric(18,2),
  grade text,
  grade_points numeric(18,2),
  grace_marks numeric(18,2),
  moderated_grade text,
  status text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.result_subjects ADD CONSTRAINT ck_result_subjects_grace_marks CHECK ((grace_marks >= (0)::numeric));
ALTER TABLE exm.result_subjects ADD CONSTRAINT ck_result_subjects_status CHECK ((status = ANY (ARRAY['PASS'::text, 'FAIL'::text, 'ABSENT'::text, 'WITHHELD'::text])));
ALTER TABLE exm.result_subjects ADD CONSTRAINT result_subjects_pkey PRIMARY KEY (id);
ALTER TABLE exm.result_subjects ADD CONSTRAINT uq_result_subjects_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.results (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  registration_id uuid NOT NULL,
  examination_id uuid NOT NULL,
  student_id uuid NOT NULL,
  sgpa numeric(18,2),
  cgpa numeric(18,2),
  total_marks numeric(18,2),
  total_max_marks numeric(18,2),
  overall_grade text,
  overall_status text NOT NULL,
  grace_marks_applied numeric(18,2),
  workflow_instance_id uuid,
  version_no integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  published_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.results ADD CONSTRAINT ck_results_grace_marks_applied CHECK ((grace_marks_applied >= (0)::numeric));
ALTER TABLE exm.results ADD CONSTRAINT ck_results_overall_status CHECK ((overall_status = ANY (ARRAY['PASS'::text, 'FAIL'::text, 'WITHHELD'::text, 'DETAINED'::text, 'ABSENT'::text])));
ALTER TABLE exm.results ADD CONSTRAINT ck_results_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PROCESSED'::text, 'APPROVED'::text, 'PUBLISHED'::text, 'AMENDED'::text, 'WITHDRAWN'::text])));
ALTER TABLE exm.results ADD CONSTRAINT results_pkey PRIMARY KEY (id);
ALTER TABLE exm.results ADD CONSTRAINT uq_results_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.revaluation_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  registration_id uuid NOT NULL,
  exam_subject_id uuid NOT NULL,
  request_type text DEFAULT 'REVALUATION'::text NOT NULL,
  fee_paid boolean DEFAULT false NOT NULL,
  status text DEFAULT 'REQUESTED'::text NOT NULL,
  old_marks numeric(18,2),
  new_marks numeric(18,2),
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT ck_revaluation_requests_request_type CHECK ((request_type = ANY (ARRAY['REVALUATION'::text, 'RETOTALLING'::text])));
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT ck_revaluation_requests_status CHECK ((status = ANY (ARRAY['REQUESTED'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'REJECTED'::text])));
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT revaluation_requests_pkey PRIMARY KEY (id);
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT uq_revaluation_requests_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.schedule_conflicts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  schedule_version_id uuid NOT NULL,
  conflict_type text NOT NULL,
  entry_a_id uuid,
  entry_b_id uuid,
  detail jsonb,
  resolved_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.schedule_conflicts ADD CONSTRAINT ck_schedule_conflicts_conflict_type CHECK ((conflict_type = ANY (ARRAY['ROOM'::text, 'STUDENT'::text, 'INVIGILATOR'::text, 'CAPACITY'::text])));
ALTER TABLE exm.schedule_conflicts ADD CONSTRAINT schedule_conflicts_pkey PRIMARY KEY (id);
ALTER TABLE exm.schedule_conflicts ADD CONSTRAINT uq_schedule_conflicts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.schedule_entries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  schedule_version_id uuid NOT NULL,
  session_id uuid NOT NULL,
  room_id uuid NOT NULL,
  seat_from integer,
  seat_to integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.schedule_entries ADD CONSTRAINT schedule_entries_pkey PRIMARY KEY (id);
ALTER TABLE exm.schedule_entries ADD CONSTRAINT uq_schedule_entries_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.schedule_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  examination_id uuid NOT NULL,
  version_no integer NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  published_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.schedule_versions ADD CONSTRAINT ck_schedule_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'SUPERSEDED'::text])));
ALTER TABLE exm.schedule_versions ADD CONSTRAINT ck_schedule_versions_version_no CHECK ((version_no > 0));
ALTER TABLE exm.schedule_versions ADD CONSTRAINT schedule_versions_pkey PRIMARY KEY (id);
ALTER TABLE exm.schedule_versions ADD CONSTRAINT uq_schedule_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.scheduling_constraints (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  examination_id uuid NOT NULL,
  constraint_type text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb NOT NULL,
  is_hard boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.scheduling_constraints ADD CONSTRAINT scheduling_constraints_pkey PRIMARY KEY (id);
ALTER TABLE exm.scheduling_constraints ADD CONSTRAINT uq_scheduling_constraints_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.script_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  answer_script_id uuid NOT NULL,
  valuation_assignment_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE exm.script_assignments ADD CONSTRAINT script_assignments_pkey PRIMARY KEY (id);
ALTER TABLE exm.script_assignments ADD CONSTRAINT uq_script_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.script_movements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  packet_id uuid NOT NULL,
  answer_script_id uuid,
  movement_type text NOT NULL,
  from_location text,
  to_location text,
  quantity integer,
  detail jsonb,
  moved_at timestamp with time zone DEFAULT now() NOT NULL,
  moved_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE exm.script_movements ADD CONSTRAINT ck_script_movements_movement_type CHECK ((movement_type = ANY (ARRAY['DISPATCH'::text, 'RECEIVE'::text, 'TRANSFER'::text, 'RETURN'::text, 'SCAN'::text, 'RECONCILE'::text, 'EXCEPTION'::text])));
ALTER TABLE exm.script_movements ADD CONSTRAINT script_movements_pkey PRIMARY KEY (id);
ALTER TABLE exm.script_movements ADD CONSTRAINT uq_script_movements_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.script_packets (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  exam_session_id uuid NOT NULL,
  room_id uuid,
  packet_no text NOT NULL,
  script_count integer NOT NULL,
  seal_no text,
  status text DEFAULT 'SEALED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.script_packets ADD CONSTRAINT ck_script_packets_script_count CHECK ((script_count >= 0));
ALTER TABLE exm.script_packets ADD CONSTRAINT ck_script_packets_status CHECK ((status = ANY (ARRAY['SEALED'::text, 'DISPATCHED'::text, 'RECEIVED'::text, 'OPENED'::text])));
ALTER TABLE exm.script_packets ADD CONSTRAINT script_packets_pkey PRIMARY KEY (id);
ALTER TABLE exm.script_packets ADD CONSTRAINT uq_script_packets_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.valuation_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  valuation_batch_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  role text DEFAULT 'EVALUATOR'::text NOT NULL,
  status text DEFAULT 'ASSIGNED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.valuation_assignments ADD CONSTRAINT ck_valuation_assignments_role CHECK ((role = ANY (ARRAY['EVALUATOR'::text, 'MODERATOR'::text, 'CHIEF_EXAMINER'::text])));
ALTER TABLE exm.valuation_assignments ADD CONSTRAINT ck_valuation_assignments_status CHECK ((status = ANY (ARRAY['ASSIGNED'::text, 'ACCEPTED'::text, 'DECLINED'::text, 'COMPLETED'::text])));
ALTER TABLE exm.valuation_assignments ADD CONSTRAINT valuation_assignments_pkey PRIMARY KEY (id);
ALTER TABLE exm.valuation_assignments ADD CONSTRAINT uq_valuation_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.valuation_batches (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  exam_subject_id uuid NOT NULL,
  centre_id uuid,
  code text NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.valuation_batches ADD CONSTRAINT ck_valuation_batches_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'LOCKED'::text])));
ALTER TABLE exm.valuation_batches ADD CONSTRAINT valuation_batches_pkey PRIMARY KEY (id);
ALTER TABLE exm.valuation_batches ADD CONSTRAINT uq_valuation_batches_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.valuation_centres (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  address_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.valuation_centres ADD CONSTRAINT ck_valuation_centres_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])));
ALTER TABLE exm.valuation_centres ADD CONSTRAINT valuation_centres_pkey PRIMARY KEY (id);
ALTER TABLE exm.valuation_centres ADD CONSTRAINT uq_valuation_centres_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.valuation_schemes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  exam_subject_id uuid NOT NULL,
  scheme_type text DEFAULT 'SINGLE'::text NOT NULL,
  anonymised boolean DEFAULT true NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.valuation_schemes ADD CONSTRAINT ck_valuation_schemes_scheme_type CHECK ((scheme_type = ANY (ARRAY['SINGLE'::text, 'DOUBLE'::text, 'MODERATED'::text, 'ONLINE'::text])));
ALTER TABLE exm.valuation_schemes ADD CONSTRAINT ck_valuation_schemes_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE exm.valuation_schemes ADD CONSTRAINT valuation_schemes_pkey PRIMARY KEY (id);
ALTER TABLE exm.valuation_schemes ADD CONSTRAINT uq_valuation_schemes_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS exm.valuation_sessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  valuation_batch_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  started_at timestamp with time zone,
  ended_at timestamp with time zone,
  status text DEFAULT 'OPEN'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE exm.valuation_sessions ADD CONSTRAINT ck_valuation_sessions_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'SUBMITTED'::text, 'LOCKED'::text])));
ALTER TABLE exm.valuation_sessions ADD CONSTRAINT valuation_sessions_pkey PRIMARY KEY (id);
ALTER TABLE exm.valuation_sessions ADD CONSTRAINT uq_valuation_sessions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.adjustments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  adjustment_type text NOT NULL,
  amount numeric(18,2) NOT NULL,
  reason text NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  workflow_instance_id uuid,
  journal_entry_id uuid,
  applied_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.adjustments ADD CONSTRAINT ck_adjustments_adjustment_type CHECK ((adjustment_type = ANY (ARRAY['WRITE_OFF'::text, 'CORRECTION'::text, 'DISCOUNT'::text, 'PENALTY'::text, 'REVERSAL'::text, 'WAIVER'::text, 'OTHER'::text])));
ALTER TABLE fin.adjustments ADD CONSTRAINT ck_adjustments_entity_type CHECK ((entity_type = ANY (ARRAY['RECEIPT'::text, 'COLLECTION'::text, 'OBLIGATION'::text, 'EXPENSE'::text, 'SCHOLARSHIP'::text, 'TAX'::text, 'PAYMENT'::text])));
ALTER TABLE fin.adjustments ADD CONSTRAINT ck_adjustments_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'APPLIED'::text])));
ALTER TABLE fin.adjustments ADD CONSTRAINT adjustments_pkey PRIMARY KEY (id);
ALTER TABLE fin.adjustments ADD CONSTRAINT uq_adjustments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.collection_batches (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  batch_no text NOT NULL,
  counter_user_id uuid,
  collection_date date NOT NULL,
  total_amount numeric(18,2) DEFAULT 0 NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  closed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.collection_batches ADD CONSTRAINT ck_collection_batches_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'CLOSED'::text, 'RECONCILED'::text])));
ALTER TABLE fin.collection_batches ADD CONSTRAINT collection_batches_pkey PRIMARY KEY (id);
ALTER TABLE fin.collection_batches ADD CONSTRAINT uq_collection_batches_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.concession_types (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  basis text DEFAULT 'PERCENT'::text NOT NULL,
  default_value numeric(18,2),
  requires_approval boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.concession_types ADD CONSTRAINT ck_concession_types_basis CHECK ((basis = ANY (ARRAY['PERCENT'::text, 'AMOUNT'::text])));
ALTER TABLE fin.concession_types ADD CONSTRAINT concession_types_pkey PRIMARY KEY (id);
ALTER TABLE fin.concession_types ADD CONSTRAINT uq_concession_types_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.disbursements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payment_request_id uuid,
  source_type text,
  source_id uuid,
  payee_name text,
  amount numeric(18,2) NOT NULL,
  method text,
  utr_no text,
  paid_on date,
  bank_reference text,
  status text DEFAULT 'PAID'::text NOT NULL,
  journal_entry_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.disbursements ADD CONSTRAINT ck_disbursements_amount CHECK ((amount > (0)::numeric));
ALTER TABLE fin.disbursements ADD CONSTRAINT ck_disbursements_status CHECK ((status = ANY (ARRAY['PAID'::text, 'FAILED'::text, 'REVERSED'::text])));
ALTER TABLE fin.disbursements ADD CONSTRAINT disbursements_pkey PRIMARY KEY (id);
ALTER TABLE fin.disbursements ADD CONSTRAINT uq_disbursements_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.expense_categories (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  parent_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  ledger_account_id uuid,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.expense_categories ADD CONSTRAINT expense_categories_pkey PRIMARY KEY (id);
ALTER TABLE fin.expense_categories ADD CONSTRAINT uq_expense_categories_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.expense_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  expense_id uuid NOT NULL,
  description text NOT NULL,
  quantity numeric(18,4) DEFAULT 1 NOT NULL,
  unit_price numeric(18,2) NOT NULL,
  amount numeric(18,2) NOT NULL,
  tax_code_id uuid,
  tax_amount numeric(18,2) DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.expense_items ADD CONSTRAINT ck_expense_items_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.expense_items ADD CONSTRAINT ck_expense_items_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE fin.expense_items ADD CONSTRAINT ck_expense_items_unit_price CHECK ((unit_price >= (0)::numeric));
ALTER TABLE fin.expense_items ADD CONSTRAINT expense_items_pkey PRIMARY KEY (id);
ALTER TABLE fin.expense_items ADD CONSTRAINT uq_expense_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.expenses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  department_id uuid,
  category_id uuid,
  vendor_id uuid,
  claimant_employee_id uuid,
  expense_no text NOT NULL,
  expense_date date NOT NULL,
  expense_type text DEFAULT 'PURCHASE'::text NOT NULL,
  total_amount numeric(18,2) NOT NULL,
  tax_amount numeric(18,2) DEFAULT 0 NOT NULL,
  budget_line_id uuid,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  description text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.expenses ADD CONSTRAINT ck_expenses_expense_type CHECK ((expense_type = ANY (ARRAY['PURCHASE'::text, 'REIMBURSEMENT'::text, 'ADVANCE'::text, 'PETTY_CASH'::text])));
ALTER TABLE fin.expenses ADD CONSTRAINT ck_expenses_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'APPROVED'::text, 'REJECTED'::text, 'PAID'::text, 'CANCELLED'::text])));
ALTER TABLE fin.expenses ADD CONSTRAINT ck_expenses_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE fin.expenses ADD CONSTRAINT ck_expenses_total_amount CHECK ((total_amount >= (0)::numeric));
ALTER TABLE fin.expenses ADD CONSTRAINT expenses_pkey PRIMARY KEY (id);
ALTER TABLE fin.expenses ADD CONSTRAINT uq_expenses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_components (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_version_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  component_type text DEFAULT 'TUITION'::text NOT NULL,
  amount numeric(18,2) NOT NULL,
  is_refundable boolean DEFAULT false NOT NULL,
  is_taxable boolean DEFAULT false NOT NULL,
  tax_code_id uuid,
  ledger_account_id uuid,
  sort_order integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_components ADD CONSTRAINT ck_fee_components_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.fee_components ADD CONSTRAINT ck_fee_components_component_type CHECK ((component_type = ANY (ARRAY['TUITION'::text, 'HOSTEL'::text, 'TRANSPORT'::text, 'EXAM'::text, 'LIBRARY'::text, 'LAB'::text, 'DEVELOPMENT'::text, 'DEPOSIT'::text, 'FINE'::text, 'OTHER'::text])));
ALTER TABLE fin.fee_components ADD CONSTRAINT fee_components_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_components ADD CONSTRAINT uq_fee_components_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_concessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  concession_type_id uuid NOT NULL,
  structure_version_id uuid,
  component_code text,
  basis text NOT NULL,
  value numeric(18,2) NOT NULL,
  valid_from date,
  valid_to date,
  reason text,
  status text DEFAULT 'PENDING'::text NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_concessions ADD CONSTRAINT ck_fee_concessions_basis CHECK ((basis = ANY (ARRAY['PERCENT'::text, 'AMOUNT'::text])));
ALTER TABLE fin.fee_concessions ADD CONSTRAINT ck_fee_concessions_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'EXPIRED'::text])));
ALTER TABLE fin.fee_concessions ADD CONSTRAINT ck_fee_concessions_value CHECK ((value >= (0)::numeric));
ALTER TABLE fin.fee_concessions ADD CONSTRAINT fee_concessions_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_concessions ADD CONSTRAINT uq_fee_concessions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_installments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_version_id uuid NOT NULL,
  installment_no smallint NOT NULL,
  fee_period_id uuid,
  due_date date NOT NULL,
  percentage numeric(9,4),
  amount numeric(18,2),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_installments ADD CONSTRAINT ck_fee_installments_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.fee_installments ADD CONSTRAINT ck_fee_installments_installment_no CHECK ((installment_no > 0));
ALTER TABLE fin.fee_installments ADD CONSTRAINT ck_fee_installments_percentage CHECK (((percentage >= (0)::numeric) AND (percentage <= (100)::numeric)));
ALTER TABLE fin.fee_installments ADD CONSTRAINT fee_installments_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_installments ADD CONSTRAINT uq_fee_installments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_obligation_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  obligation_id uuid NOT NULL,
  fee_component_id uuid,
  description text NOT NULL,
  amount numeric(18,2) NOT NULL,
  tax_amount numeric(18,2) DEFAULT 0 NOT NULL,
  paid_amount numeric(18,2) DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT ck_fee_obligation_items_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT ck_fee_obligation_items_paid_amount CHECK ((paid_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT ck_fee_obligation_items_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT fee_obligation_items_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT uq_fee_obligation_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_obligations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  structure_version_id uuid,
  fee_period_id uuid,
  installment_id uuid,
  obligation_no text NOT NULL,
  due_date date,
  gross_amount numeric(18,2) DEFAULT 0 NOT NULL,
  concession_amount numeric(18,2) DEFAULT 0 NOT NULL,
  scholarship_amount numeric(18,2) DEFAULT 0 NOT NULL,
  tax_amount numeric(18,2) DEFAULT 0 NOT NULL,
  net_amount numeric(18,2) DEFAULT 0 NOT NULL,
  paid_amount numeric(18,2) DEFAULT 0 NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_concession_amount CHECK ((concession_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_gross_amount CHECK ((gross_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_net_amount CHECK ((net_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_paid_amount CHECK ((paid_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_scholarship_amount CHECK ((scholarship_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'PARTIALLY_PAID'::text, 'PAID'::text, 'OVERDUE'::text, 'WAIVED'::text, 'CANCELLED'::text])));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT ck_fee_obligations_x1 CHECK ((paid_amount <= (net_amount + 0.005)));
ALTER TABLE fin.fee_obligations ADD CONSTRAINT fee_obligations_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_obligations ADD CONSTRAINT uq_fee_obligations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_periods (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  academic_year_id uuid NOT NULL,
  term_id uuid,
  name text NOT NULL,
  start_date date,
  due_date date,
  end_date date,
  status text DEFAULT 'OPEN'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_periods ADD CONSTRAINT ck_fee_periods_status CHECK ((status = ANY (ARRAY['PLANNED'::text, 'OPEN'::text, 'CLOSED'::text])));
ALTER TABLE fin.fee_periods ADD CONSTRAINT fee_periods_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_periods ADD CONSTRAINT uq_fee_periods_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_version_id uuid NOT NULL,
  rule_type text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb NOT NULL,
  priority integer DEFAULT 100 NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_rules ADD CONSTRAINT ck_fee_rules_rule_type CHECK ((rule_type = ANY (ARRAY['LATE_FEE'::text, 'EARLY_DISCOUNT'::text, 'GRACE'::text, 'INSTALLMENT'::text, 'WAIVER'::text])));
ALTER TABLE fin.fee_rules ADD CONSTRAINT fee_rules_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_rules ADD CONSTRAINT uq_fee_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_structure_programs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_id uuid NOT NULL,
  program_id uuid NOT NULL,
  batch_id uuid,
  term_no smallint,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE fin.fee_structure_programs ADD CONSTRAINT fee_structure_programs_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_structure_programs ADD CONSTRAINT uq_fee_structure_programs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_structure_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_id uuid NOT NULL,
  version_no integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  total_amount numeric(18,2),
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT ck_fee_structure_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text])));
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT ck_fee_structure_versions_total_amount CHECK ((total_amount >= (0)::numeric));
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT ck_fee_structure_versions_version_no CHECK ((version_no > 0));
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT ck_fee_structure_versions_x1 CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT fee_structure_versions_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT uq_fee_structure_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.fee_structures (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  academic_year_id uuid,
  currency_code character(3) DEFAULT 'INR'::bpchar NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  current_version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.fee_structures ADD CONSTRAINT ck_fee_structures_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PENDING_APPROVAL'::text, 'ACTIVE'::text, 'SUPERSEDED'::text, 'RETIRED'::text])));
ALTER TABLE fin.fee_structures ADD CONSTRAINT fee_structures_pkey PRIMARY KEY (id);
ALTER TABLE fin.fee_structures ADD CONSTRAINT uq_fee_structures_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.gateway_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  gateway text NOT NULL,
  event_id text NOT NULL,
  event_type text NOT NULL,
  payload jsonb DEFAULT '{}'::jsonb NOT NULL,
  signature_valid boolean,
  processed_at timestamp with time zone,
  status text DEFAULT 'RECEIVED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE fin.gateway_events ADD CONSTRAINT ck_gateway_events_status CHECK ((status = ANY (ARRAY['RECEIVED'::text, 'PROCESSED'::text, 'IGNORED'::text, 'FAILED'::text])));
ALTER TABLE fin.gateway_events ADD CONSTRAINT gateway_events_pkey PRIMARY KEY (id);
ALTER TABLE fin.gateway_events ADD CONSTRAINT uq_gateway_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.gateway_transactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payment_id uuid,
  gateway text NOT NULL,
  gateway_order_id text,
  gateway_payment_id text,
  amount numeric(18,2) NOT NULL,
  status text DEFAULT 'CREATED'::text NOT NULL,
  request_payload jsonb,
  response_payload jsonb,
  signature_verified boolean,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.gateway_transactions ADD CONSTRAINT ck_gateway_transactions_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.gateway_transactions ADD CONSTRAINT ck_gateway_transactions_status CHECK ((status = ANY (ARRAY['CREATED'::text, 'AUTHORIZED'::text, 'CAPTURED'::text, 'FAILED'::text, 'REFUNDED'::text])));
ALTER TABLE fin.gateway_transactions ADD CONSTRAINT gateway_transactions_pkey PRIMARY KEY (id);
ALTER TABLE fin.gateway_transactions ADD CONSTRAINT uq_gateway_transactions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.journal_entries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  entry_no text NOT NULL,
  entry_date date NOT NULL,
  source_type text,
  source_id uuid,
  narration text,
  status text DEFAULT 'POSTED'::text NOT NULL,
  reverses_entry_id uuid,
  posted_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.journal_entries ADD CONSTRAINT ck_journal_entries_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'POSTED'::text, 'REVERSED'::text])));
ALTER TABLE fin.journal_entries ADD CONSTRAINT journal_entries_pkey PRIMARY KEY (id);
ALTER TABLE fin.journal_entries ADD CONSTRAINT uq_journal_entries_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.journal_lines (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  entry_id uuid NOT NULL,
  account_id uuid NOT NULL,
  department_id uuid,
  cost_centre_id uuid,
  debit numeric(18,2) DEFAULT 0 NOT NULL,
  credit numeric(18,2) DEFAULT 0 NOT NULL,
  memo text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE fin.journal_lines ADD CONSTRAINT ck_journal_lines_credit CHECK ((credit >= (0)::numeric));
ALTER TABLE fin.journal_lines ADD CONSTRAINT ck_journal_lines_debit CHECK ((debit >= (0)::numeric));
ALTER TABLE fin.journal_lines ADD CONSTRAINT ck_journal_lines_x1 CHECK (((debit = (0)::numeric) <> (credit = (0)::numeric)));
ALTER TABLE fin.journal_lines ADD CONSTRAINT journal_lines_pkey PRIMARY KEY (id);
ALTER TABLE fin.journal_lines ADD CONSTRAINT uq_journal_lines_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.ledger_accounts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  account_type text NOT NULL,
  parent_account_id uuid,
  is_postable boolean DEFAULT true NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.ledger_accounts ADD CONSTRAINT ck_ledger_accounts_account_type CHECK ((account_type = ANY (ARRAY['ASSET'::text, 'LIABILITY'::text, 'EQUITY'::text, 'INCOME'::text, 'EXPENSE'::text])));
ALTER TABLE fin.ledger_accounts ADD CONSTRAINT ledger_accounts_pkey PRIMARY KEY (id);
ALTER TABLE fin.ledger_accounts ADD CONSTRAINT uq_ledger_accounts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.payment_allocations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payment_id uuid NOT NULL,
  obligation_id uuid NOT NULL,
  obligation_item_id uuid,
  amount numeric(18,2) NOT NULL,
  reverses_allocation_id uuid,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE fin.payment_allocations ADD CONSTRAINT ck_payment_allocations_x1 CHECK ((amount <> (0)::numeric));
ALTER TABLE fin.payment_allocations ADD CONSTRAINT ck_payment_allocations_x2 CHECK ((((reverses_allocation_id IS NULL) AND (amount > (0)::numeric)) OR ((reverses_allocation_id IS NOT NULL) AND (amount < (0)::numeric))));
ALTER TABLE fin.payment_allocations ADD CONSTRAINT payment_allocations_pkey PRIMARY KEY (id);
ALTER TABLE fin.payment_allocations ADD CONSTRAINT uq_payment_allocations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.payment_methods (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  method_type text NOT NULL,
  gateway_provider text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.payment_methods ADD CONSTRAINT ck_payment_methods_method_type CHECK ((method_type = ANY (ARRAY['CASH'::text, 'CHEQUE'::text, 'DD'::text, 'UPI'::text, 'CARD'::text, 'NETBANKING'::text, 'WALLET'::text, 'BANK_TRANSFER'::text])));
ALTER TABLE fin.payment_methods ADD CONSTRAINT payment_methods_pkey PRIMARY KEY (id);
ALTER TABLE fin.payment_methods ADD CONSTRAINT uq_payment_methods_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.payment_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  source_type text NOT NULL,
  source_id uuid NOT NULL,
  payee_type text NOT NULL,
  payee_id uuid,
  payee_name text,
  amount numeric(18,2) NOT NULL,
  method text,
  bank_account_ref uuid,
  status text DEFAULT 'PENDING'::text NOT NULL,
  workflow_instance_id uuid,
  requested_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.payment_requests ADD CONSTRAINT ck_payment_requests_amount CHECK ((amount > (0)::numeric));
ALTER TABLE fin.payment_requests ADD CONSTRAINT ck_payment_requests_payee_type CHECK ((payee_type = ANY (ARRAY['VENDOR'::text, 'EMPLOYEE'::text, 'STUDENT'::text, 'OTHER'::text])));
ALTER TABLE fin.payment_requests ADD CONSTRAINT ck_payment_requests_source_type CHECK ((source_type = ANY (ARRAY['EXPENSE'::text, 'PAYROLL'::text, 'SCHOLARSHIP'::text, 'REFUND'::text, 'VENDOR_INVOICE'::text])));
ALTER TABLE fin.payment_requests ADD CONSTRAINT ck_payment_requests_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'SENT'::text, 'PAID'::text, 'FAILED'::text, 'CANCELLED'::text])));
ALTER TABLE fin.payment_requests ADD CONSTRAINT payment_requests_pkey PRIMARY KEY (id);
ALTER TABLE fin.payment_requests ADD CONSTRAINT uq_payment_requests_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.payments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  payer_person_id uuid,
  payment_method_id uuid,
  collection_batch_id uuid,
  payment_no text NOT NULL,
  amount numeric(18,2) NOT NULL,
  currency_code character(3) DEFAULT 'INR'::bpchar NOT NULL,
  paid_on timestamp with time zone,
  reference_no text,
  instrument_no text,
  instrument_date date,
  status text DEFAULT 'INITIATED'::text NOT NULL,
  idempotency_key text,
  settlement_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.payments ADD CONSTRAINT ck_payments_amount CHECK ((amount > (0)::numeric));
ALTER TABLE fin.payments ADD CONSTRAINT ck_payments_status CHECK ((status = ANY (ARRAY['INITIATED'::text, 'PENDING'::text, 'SUCCESS'::text, 'FAILED'::text, 'CANCELLED'::text, 'REFUNDED'::text, 'PARTIALLY_REFUNDED'::text])));
ALTER TABLE fin.payments ADD CONSTRAINT payments_pkey PRIMARY KEY (id);
ALTER TABLE fin.payments ADD CONSTRAINT uq_payments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.receipt_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  receipt_id uuid NOT NULL,
  obligation_item_id uuid,
  description text NOT NULL,
  amount numeric(18,2) NOT NULL,
  tax_amount numeric(18,2) DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.receipt_items ADD CONSTRAINT ck_receipt_items_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.receipt_items ADD CONSTRAINT ck_receipt_items_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE fin.receipt_items ADD CONSTRAINT receipt_items_pkey PRIMARY KEY (id);
ALTER TABLE fin.receipt_items ADD CONSTRAINT uq_receipt_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.receipts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payment_id uuid,
  student_id uuid,
  payer_person_id uuid,
  receipt_no text NOT NULL,
  receipt_date date NOT NULL,
  total_amount numeric(18,2) NOT NULL,
  tax_amount numeric(18,2) DEFAULT 0 NOT NULL,
  status text DEFAULT 'ISSUED'::text NOT NULL,
  document_id uuid,
  template_id uuid,
  verification_code text,
  supersedes_receipt_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.receipts ADD CONSTRAINT ck_receipts_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ISSUED'::text, 'CANCELLED'::text, 'REISSUED'::text])));
ALTER TABLE fin.receipts ADD CONSTRAINT ck_receipts_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE fin.receipts ADD CONSTRAINT ck_receipts_total_amount CHECK ((total_amount >= (0)::numeric));
ALTER TABLE fin.receipts ADD CONSTRAINT receipts_pkey PRIMARY KEY (id);
ALTER TABLE fin.receipts ADD CONSTRAINT uq_receipts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.reconciliations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  recon_type text NOT NULL,
  source_ref text,
  settlement_id uuid,
  payment_id uuid,
  expected_amount numeric(18,2),
  actual_amount numeric(18,2),
  variance numeric(18,2),
  status text DEFAULT 'UNMATCHED'::text NOT NULL,
  reconciled_by uuid,
  reconciled_at timestamp with time zone,
  entity_type text,
  entity_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.reconciliations ADD CONSTRAINT ck_reconciliations_recon_type CHECK ((recon_type = ANY (ARRAY['PAYMENT'::text, 'BANK'::text, 'GATEWAY'::text, 'TAX'::text, 'PAYROLL'::text])));
ALTER TABLE fin.reconciliations ADD CONSTRAINT ck_reconciliations_status CHECK ((status = ANY (ARRAY['MATCHED'::text, 'UNMATCHED'::text, 'DISPUTED'::text, 'ADJUSTED'::text])));
ALTER TABLE fin.reconciliations ADD CONSTRAINT reconciliations_pkey PRIMARY KEY (id);
ALTER TABLE fin.reconciliations ADD CONSTRAINT uq_reconciliations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.refunds (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payment_id uuid NOT NULL,
  refund_no text NOT NULL,
  amount numeric(18,2) NOT NULL,
  reason text NOT NULL,
  status text DEFAULT 'REQUESTED'::text NOT NULL,
  workflow_instance_id uuid,
  processed_at timestamp with time zone,
  payment_request_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.refunds ADD CONSTRAINT ck_refunds_amount CHECK ((amount > (0)::numeric));
ALTER TABLE fin.refunds ADD CONSTRAINT ck_refunds_status CHECK ((status = ANY (ARRAY['REQUESTED'::text, 'APPROVED'::text, 'PROCESSED'::text, 'REJECTED'::text, 'FAILED'::text])));
ALTER TABLE fin.refunds ADD CONSTRAINT refunds_pkey PRIMARY KEY (id);
ALTER TABLE fin.refunds ADD CONSTRAINT uq_refunds_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_application_periods (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scheme_id uuid NOT NULL,
  academic_year_id uuid,
  open_from timestamp with time zone NOT NULL,
  open_to timestamp with time zone NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.scholarship_application_periods ADD CONSTRAINT ck_scholarship_application_periods_status CHECK ((status = ANY (ARRAY['PLANNED'::text, 'OPEN'::text, 'CLOSED'::text])));
ALTER TABLE fin.scholarship_application_periods ADD CONSTRAINT ck_scholarship_application_periods_x1 CHECK ((open_to > open_from));
ALTER TABLE fin.scholarship_application_periods ADD CONSTRAINT scholarship_application_periods_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_application_periods ADD CONSTRAINT uq_scholarship_application_periods_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_applications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scheme_id uuid NOT NULL,
  period_id uuid,
  student_id uuid NOT NULL,
  application_no text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  submitted_at timestamp with time zone,
  workflow_instance_id uuid,
  declared_data jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT ck_scholarship_applications_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'UNDER_REVIEW'::text, 'SHORTLISTED'::text, 'APPROVED'::text, 'REJECTED'::text, 'WITHDRAWN'::text])));
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT scholarship_applications_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT uq_scholarship_applications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_award_components (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  award_id uuid NOT NULL,
  benefit_id uuid,
  description text,
  amount numeric(18,2) NOT NULL,
  utilized_amount numeric(18,2) DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.scholarship_award_components ADD CONSTRAINT ck_scholarship_award_components_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.scholarship_award_components ADD CONSTRAINT ck_scholarship_award_components_utilized_amount CHECK ((utilized_amount >= (0)::numeric));
ALTER TABLE fin.scholarship_award_components ADD CONSTRAINT scholarship_award_components_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_award_components ADD CONSTRAINT uq_scholarship_award_components_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_award_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  award_id uuid NOT NULL,
  event_type text NOT NULL,
  reason text,
  effective_from date,
  effective_to date,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE fin.scholarship_award_events ADD CONSTRAINT ck_scholarship_award_events_event_type CHECK ((event_type = ANY (ARRAY['RENEWAL'::text, 'SUSPENSION'::text, 'REINSTATEMENT'::text, 'CANCELLATION'::text])));
ALTER TABLE fin.scholarship_award_events ADD CONSTRAINT scholarship_award_events_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_award_events ADD CONSTRAINT uq_scholarship_award_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_awards (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  application_id uuid,
  scheme_id uuid NOT NULL,
  student_id uuid NOT NULL,
  award_no text NOT NULL,
  valid_from date,
  valid_to date,
  total_amount numeric(18,2) NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT ck_scholarship_awards_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'CANCELLED'::text, 'COMPLETED'::text, 'EXPIRED'::text])));
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT ck_scholarship_awards_total_amount CHECK ((total_amount >= (0)::numeric));
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT scholarship_awards_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT uq_scholarship_awards_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_benefits (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scheme_id uuid NOT NULL,
  benefit_type text NOT NULL,
  basis text NOT NULL,
  value numeric(18,2) NOT NULL,
  max_amount numeric(18,2),
  fee_component_type text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT ck_scholarship_benefits_basis CHECK ((basis = ANY (ARRAY['PERCENT'::text, 'AMOUNT'::text])));
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT ck_scholarship_benefits_benefit_type CHECK ((benefit_type = ANY (ARRAY['TUITION_WAIVER'::text, 'STIPEND'::text, 'BOOK_ALLOWANCE'::text, 'HOSTEL'::text, 'TRANSPORT'::text, 'OTHER'::text])));
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT ck_scholarship_benefits_max_amount CHECK ((max_amount >= (0)::numeric));
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT ck_scholarship_benefits_value CHECK ((value >= (0)::numeric));
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT scholarship_benefits_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT uq_scholarship_benefits_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_criteria (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scheme_id uuid NOT NULL,
  criterion_type text NOT NULL,
  operator text NOT NULL,
  value jsonb DEFAULT '{}'::jsonb NOT NULL,
  is_mandatory boolean DEFAULT true NOT NULL,
  weight numeric(9,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.scholarship_criteria ADD CONSTRAINT ck_scholarship_criteria_criterion_type CHECK ((criterion_type = ANY (ARRAY['INCOME'::text, 'MERIT'::text, 'CATEGORY'::text, 'ATTENDANCE'::text, 'PROGRAM'::text, 'GENDER'::text, 'CUSTOM'::text])));
ALTER TABLE fin.scholarship_criteria ADD CONSTRAINT scholarship_criteria_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_criteria ADD CONSTRAINT uq_scholarship_criteria_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_evaluations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  application_id uuid NOT NULL,
  evaluation_type text DEFAULT 'ELIGIBILITY'::text NOT NULL,
  result text,
  score numeric(18,2),
  rank_no integer,
  detail jsonb,
  evaluated_by uuid,
  evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE fin.scholarship_evaluations ADD CONSTRAINT ck_scholarship_evaluations_evaluation_type CHECK ((evaluation_type = ANY (ARRAY['ELIGIBILITY'::text, 'MERIT'::text, 'COMMITTEE'::text])));
ALTER TABLE fin.scholarship_evaluations ADD CONSTRAINT scholarship_evaluations_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_evaluations ADD CONSTRAINT uq_scholarship_evaluations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_schemes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  sponsor_name text,
  sponsor_type text,
  academic_year_id uuid,
  total_budget numeric(18,2),
  status text DEFAULT 'DRAFT'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT ck_scholarship_schemes_sponsor_type CHECK ((sponsor_type = ANY (ARRAY['GOVERNMENT'::text, 'INSTITUTIONAL'::text, 'CORPORATE'::text, 'TRUST'::text, 'INDIVIDUAL'::text])));
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT ck_scholarship_schemes_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'CLOSED'::text, 'RETIRED'::text])));
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT ck_scholarship_schemes_total_budget CHECK ((total_budget >= (0)::numeric));
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT scholarship_schemes_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT uq_scholarship_schemes_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.scholarship_utilizations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  award_component_id uuid NOT NULL,
  obligation_id uuid,
  amount numeric(18,2) NOT NULL,
  utilized_on date NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE fin.scholarship_utilizations ADD CONSTRAINT ck_scholarship_utilizations_amount CHECK ((amount > (0)::numeric));
ALTER TABLE fin.scholarship_utilizations ADD CONSTRAINT scholarship_utilizations_pkey PRIMARY KEY (id);
ALTER TABLE fin.scholarship_utilizations ADD CONSTRAINT uq_scholarship_utilizations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.settlements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  gateway text NOT NULL,
  settlement_ref text NOT NULL,
  settled_on date,
  gross_amount numeric(18,2),
  fees_amount numeric(18,2),
  net_amount numeric(18,2),
  status text DEFAULT 'PENDING'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.settlements ADD CONSTRAINT ck_settlements_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'RECEIVED'::text, 'RECONCILED'::text])));
ALTER TABLE fin.settlements ADD CONSTRAINT settlements_pkey PRIMARY KEY (id);
ALTER TABLE fin.settlements ADD CONSTRAINT uq_settlements_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.vendor_bank_accounts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  vendor_id uuid NOT NULL,
  bank_name text,
  ifsc text,
  account_holder text,
  account_last4 text,
  account_ciphertext bytea,
  is_primary boolean DEFAULT false NOT NULL,
  is_verified boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.vendor_bank_accounts ADD CONSTRAINT vendor_bank_accounts_pkey PRIMARY KEY (id);
ALTER TABLE fin.vendor_bank_accounts ADD CONSTRAINT uq_vendor_bank_accounts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.vendor_invoices (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  expense_id uuid,
  vendor_id uuid NOT NULL,
  invoice_no text NOT NULL,
  invoice_date date NOT NULL,
  due_date date,
  amount numeric(18,2) NOT NULL,
  tax_amount numeric(18,2) DEFAULT 0 NOT NULL,
  status text DEFAULT 'RECEIVED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT ck_vendor_invoices_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT ck_vendor_invoices_status CHECK ((status = ANY (ARRAY['RECEIVED'::text, 'VERIFIED'::text, 'APPROVED'::text, 'PAID'::text, 'DISPUTED'::text])));
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT ck_vendor_invoices_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT vendor_invoices_pkey PRIMARY KEY (id);
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT uq_vendor_invoices_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS fin.vendors (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  vendor_type text,
  gstin text,
  pan_masked text,
  contact_person_id uuid,
  address_id uuid,
  email text,
  phone text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE fin.vendors ADD CONSTRAINT ck_vendors_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'BLOCKED'::text, 'INACTIVE'::text])));
ALTER TABLE fin.vendors ADD CONSTRAINT vendors_pkey PRIMARY KEY (id);
ALTER TABLE fin.vendors ADD CONSTRAINT uq_vendors_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.attendance_days (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  attendance_date date NOT NULL,
  shift_id uuid,
  first_in timestamp with time zone,
  last_out timestamp with time zone,
  worked_minutes integer,
  overtime_minutes integer,
  status text NOT NULL,
  is_late boolean DEFAULT false NOT NULL,
  leave_application_id uuid,
  is_regularized boolean DEFAULT false NOT NULL,
  locked boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.attendance_days ADD CONSTRAINT ck_attendance_days_status CHECK ((status = ANY (ARRAY['PRESENT'::text, 'ABSENT'::text, 'HALF_DAY'::text, 'LEAVE'::text, 'HOLIDAY'::text, 'WEEK_OFF'::text, 'ON_DUTY'::text, 'LOP'::text])));
ALTER TABLE hrm.attendance_days ADD CONSTRAINT attendance_days_pkey PRIMARY KEY (id);
ALTER TABLE hrm.attendance_days ADD CONSTRAINT uq_attendance_days_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.attendance_policies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  name text NOT NULL,
  applies_to jsonb DEFAULT '{}'::jsonb,
  late_after_minutes integer,
  half_day_hours numeric(18,2),
  full_day_hours numeric(18,2),
  overtime_after_hours numeric(18,2),
  lop_rule jsonb DEFAULT '{}'::jsonb,
  effective_from date NOT NULL,
  effective_to date,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.attendance_policies ADD CONSTRAINT attendance_policies_pkey PRIMARY KEY (id);
ALTER TABLE hrm.attendance_policies ADD CONSTRAINT uq_attendance_policies_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.attendance_punches (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  punched_at timestamp with time zone NOT NULL,
  punch_type text NOT NULL,
  source text DEFAULT 'BIOMETRIC'::text NOT NULL,
  device_id text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.attendance_punches ADD CONSTRAINT ck_attendance_punches_punch_type CHECK ((punch_type = ANY (ARRAY['IN'::text, 'OUT'::text])));
ALTER TABLE hrm.attendance_punches ADD CONSTRAINT ck_attendance_punches_source CHECK ((source = ANY (ARRAY['BIOMETRIC'::text, 'MOBILE'::text, 'WEB'::text, 'MANUAL'::text, 'RFID'::text])));
ALTER TABLE hrm.attendance_punches ADD CONSTRAINT attendance_punches_pkey PRIMARY KEY (id, punched_at);

CREATE TABLE IF NOT EXISTS hrm.attendance_punches_default (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  punched_at timestamp with time zone NOT NULL,
  punch_type text NOT NULL,
  source text DEFAULT 'BIOMETRIC'::text NOT NULL,
  device_id text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.attendance_punches_default ADD CONSTRAINT ck_attendance_punches_punch_type CHECK ((punch_type = ANY (ARRAY['IN'::text, 'OUT'::text])));
ALTER TABLE hrm.attendance_punches_default ADD CONSTRAINT ck_attendance_punches_source CHECK ((source = ANY (ARRAY['BIOMETRIC'::text, 'MOBILE'::text, 'WEB'::text, 'MANUAL'::text, 'RFID'::text])));
ALTER TABLE hrm.attendance_punches_default ADD CONSTRAINT attendance_punches_default_pkey PRIMARY KEY (id, punched_at);

CREATE TABLE IF NOT EXISTS hrm.attendance_punches_p202610 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  punched_at timestamp with time zone NOT NULL,
  punch_type text NOT NULL,
  source text DEFAULT 'BIOMETRIC'::text NOT NULL,
  device_id text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.attendance_punches_p202610 ADD CONSTRAINT ck_attendance_punches_punch_type CHECK ((punch_type = ANY (ARRAY['IN'::text, 'OUT'::text])));
ALTER TABLE hrm.attendance_punches_p202610 ADD CONSTRAINT ck_attendance_punches_source CHECK ((source = ANY (ARRAY['BIOMETRIC'::text, 'MOBILE'::text, 'WEB'::text, 'MANUAL'::text, 'RFID'::text])));
ALTER TABLE hrm.attendance_punches_p202610 ADD CONSTRAINT attendance_punches_p202610_pkey PRIMARY KEY (id, punched_at);

CREATE TABLE IF NOT EXISTS hrm.attendance_punches_p202611 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  punched_at timestamp with time zone NOT NULL,
  punch_type text NOT NULL,
  source text DEFAULT 'BIOMETRIC'::text NOT NULL,
  device_id text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.attendance_punches_p202611 ADD CONSTRAINT ck_attendance_punches_punch_type CHECK ((punch_type = ANY (ARRAY['IN'::text, 'OUT'::text])));
ALTER TABLE hrm.attendance_punches_p202611 ADD CONSTRAINT ck_attendance_punches_source CHECK ((source = ANY (ARRAY['BIOMETRIC'::text, 'MOBILE'::text, 'WEB'::text, 'MANUAL'::text, 'RFID'::text])));
ALTER TABLE hrm.attendance_punches_p202611 ADD CONSTRAINT attendance_punches_p202611_pkey PRIMARY KEY (id, punched_at);

CREATE TABLE IF NOT EXISTS hrm.attendance_punches_p202612 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  punched_at timestamp with time zone NOT NULL,
  punch_type text NOT NULL,
  source text DEFAULT 'BIOMETRIC'::text NOT NULL,
  device_id text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.attendance_punches_p202612 ADD CONSTRAINT ck_attendance_punches_punch_type CHECK ((punch_type = ANY (ARRAY['IN'::text, 'OUT'::text])));
ALTER TABLE hrm.attendance_punches_p202612 ADD CONSTRAINT ck_attendance_punches_source CHECK ((source = ANY (ARRAY['BIOMETRIC'::text, 'MOBILE'::text, 'WEB'::text, 'MANUAL'::text, 'RFID'::text])));
ALTER TABLE hrm.attendance_punches_p202612 ADD CONSTRAINT attendance_punches_p202612_pkey PRIMARY KEY (id, punched_at);

CREATE TABLE IF NOT EXISTS hrm.attendance_punches_p202701 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  punched_at timestamp with time zone NOT NULL,
  punch_type text NOT NULL,
  source text DEFAULT 'BIOMETRIC'::text NOT NULL,
  device_id text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.attendance_punches_p202701 ADD CONSTRAINT ck_attendance_punches_punch_type CHECK ((punch_type = ANY (ARRAY['IN'::text, 'OUT'::text])));
ALTER TABLE hrm.attendance_punches_p202701 ADD CONSTRAINT ck_attendance_punches_source CHECK ((source = ANY (ARRAY['BIOMETRIC'::text, 'MOBILE'::text, 'WEB'::text, 'MANUAL'::text, 'RFID'::text])));
ALTER TABLE hrm.attendance_punches_p202701 ADD CONSTRAINT attendance_punches_p202701_pkey PRIMARY KEY (id, punched_at);

CREATE TABLE IF NOT EXISTS hrm.attendance_regularizations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  attendance_date date NOT NULL,
  request_type text NOT NULL,
  requested_in timestamp with time zone,
  requested_out timestamp with time zone,
  reason text NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.attendance_regularizations ADD CONSTRAINT ck_attendance_regularizations_request_type CHECK ((request_type = ANY (ARRAY['MISSED_PUNCH'::text, 'ON_DUTY'::text, 'WORK_FROM_HOME'::text, 'CORRECTION'::text, 'ADJUSTMENT'::text])));
ALTER TABLE hrm.attendance_regularizations ADD CONSTRAINT ck_attendance_regularizations_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text, 'CANCELLED'::text])));
ALTER TABLE hrm.attendance_regularizations ADD CONSTRAINT attendance_regularizations_pkey PRIMARY KEY (id);
ALTER TABLE hrm.attendance_regularizations ADD CONSTRAINT uq_attendance_regularizations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.candidate_applications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  candidate_id uuid NOT NULL,
  posting_id uuid NOT NULL,
  applied_at timestamp with time zone DEFAULT now() NOT NULL,
  stage text DEFAULT 'APPLIED'::text NOT NULL,
  screening_score numeric(18,2),
  screening_notes text,
  screened_by uuid,
  selection_decision text,
  selection_rank integer,
  selected_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT ck_candidate_applications_selection_decision CHECK ((selection_decision = ANY (ARRAY['SELECTED'::text, 'WAITLISTED'::text, 'REJECTED'::text])));
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT ck_candidate_applications_stage CHECK ((stage = ANY (ARRAY['APPLIED'::text, 'SCREENED'::text, 'INTERVIEW'::text, 'SELECTED'::text, 'OFFERED'::text, 'ACCEPTED'::text, 'REJECTED'::text, 'WITHDRAWN'::text, 'HIRED'::text])));
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT candidate_applications_pkey PRIMARY KEY (id);
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT uq_candidate_applications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.candidates (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  source_channel_id uuid,
  current_organisation text,
  total_experience_years numeric(9,4),
  notice_period_days integer,
  expected_ctc_range text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  converted_employee_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.candidates ADD CONSTRAINT ck_candidates_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'BLACKLISTED'::text, 'HIRED'::text, 'WITHDRAWN'::text])));
ALTER TABLE hrm.candidates ADD CONSTRAINT candidates_pkey PRIMARY KEY (id);
ALTER TABLE hrm.candidates ADD CONSTRAINT uq_candidates_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.competencies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  category text,
  description text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.competencies ADD CONSTRAINT competencies_pkey PRIMARY KEY (id);
ALTER TABLE hrm.competencies ADD CONSTRAINT uq_competencies_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.competency_assessments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  review_id uuid NOT NULL,
  competency_id uuid NOT NULL,
  rating_level_id uuid,
  score numeric(18,2),
  comment text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.competency_assessments ADD CONSTRAINT competency_assessments_pkey PRIMARY KEY (id);
ALTER TABLE hrm.competency_assessments ADD CONSTRAINT uq_competency_assessments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.designations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  grade text,
  category text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.designations ADD CONSTRAINT ck_designations_category CHECK ((category = ANY (ARRAY['TEACHING'::text, 'NON_TEACHING'::text, 'ADMIN'::text, 'SUPPORT'::text])));
ALTER TABLE hrm.designations ADD CONSTRAINT designations_pkey PRIMARY KEY (id);
ALTER TABLE hrm.designations ADD CONSTRAINT uq_designations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.employee_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  department_id uuid,
  designation_id uuid,
  college_id uuid,
  campus_id uuid,
  assignment_type text DEFAULT 'PRIMARY'::text NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT ck_employee_assignments_assignment_type CHECK ((assignment_type = ANY (ARRAY['PRIMARY'::text, 'SECONDARY'::text, 'ADDITIONAL_CHARGE'::text, 'DEPUTATION'::text])));
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT ck_employee_assignments_x1 CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT employee_assignments_pkey PRIMARY KEY (id);
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT uq_employee_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.employee_learning_paths (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  path_id uuid NOT NULL,
  assigned_on date,
  status text DEFAULT 'ASSIGNED'::text NOT NULL,
  progress_pct numeric(9,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.employee_learning_paths ADD CONSTRAINT ck_employee_learning_paths_progress_pct CHECK (((progress_pct >= (0)::numeric) AND (progress_pct <= (100)::numeric)));
ALTER TABLE hrm.employee_learning_paths ADD CONSTRAINT ck_employee_learning_paths_status CHECK ((status = ANY (ARRAY['ASSIGNED'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text])));
ALTER TABLE hrm.employee_learning_paths ADD CONSTRAINT employee_learning_paths_pkey PRIMARY KEY (id);
ALTER TABLE hrm.employee_learning_paths ADD CONSTRAINT uq_employee_learning_paths_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.employee_skills (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  skill_id uuid NOT NULL,
  level smallint,
  progress_pct numeric(9,4),
  last_assessed_on date,
  source text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.employee_skills ADD CONSTRAINT ck_employee_skills_progress_pct CHECK (((progress_pct >= (0)::numeric) AND (progress_pct <= (100)::numeric)));
ALTER TABLE hrm.employee_skills ADD CONSTRAINT employee_skills_pkey PRIMARY KEY (id);
ALTER TABLE hrm.employee_skills ADD CONSTRAINT uq_employee_skills_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.employees (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  college_id uuid NOT NULL,
  department_id uuid,
  designation_id uuid,
  reporting_manager_id uuid,
  employee_no text NOT NULL,
  employment_type text DEFAULT 'PERMANENT'::text NOT NULL,
  staff_category text,
  date_of_joining date,
  confirmation_date date,
  probation_end_date date,
  date_of_exit date,
  exit_reason text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.employees ADD CONSTRAINT ck_employees_employment_type CHECK ((employment_type = ANY (ARRAY['PERMANENT'::text, 'CONTRACT'::text, 'VISITING'::text, 'PART_TIME'::text, 'INTERN'::text, 'DEPUTATION'::text])));
ALTER TABLE hrm.employees ADD CONSTRAINT ck_employees_staff_category CHECK ((staff_category = ANY (ARRAY['TEACHING'::text, 'NON_TEACHING'::text, 'ADMIN'::text, 'SUPPORT'::text])));
ALTER TABLE hrm.employees ADD CONSTRAINT ck_employees_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ON_LEAVE'::text, 'SUSPENDED'::text, 'NOTICE_PERIOD'::text, 'EXITED'::text, 'RETIRED'::text])));
ALTER TABLE hrm.employees ADD CONSTRAINT ck_employees_x1 CHECK (((date_of_exit IS NULL) OR (date_of_joining IS NULL) OR (date_of_exit >= date_of_joining)));
ALTER TABLE hrm.employees ADD CONSTRAINT employees_pkey PRIMARY KEY (id);
ALTER TABLE hrm.employees ADD CONSTRAINT uq_employees_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.employment_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  event_type text NOT NULL,
  event_date date NOT NULL,
  from_value jsonb,
  to_value jsonb,
  reason text,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.employment_events ADD CONSTRAINT ck_employment_events_event_type CHECK ((event_type = ANY (ARRAY['JOINED'::text, 'CONFIRMED'::text, 'TRANSFERRED'::text, 'PROMOTED'::text, 'DEMOTED'::text, 'SUSPENDED'::text, 'REINSTATED'::text, 'RESIGNED'::text, 'TERMINATED'::text, 'RETIRED'::text, 'REHIRED'::text])));
ALTER TABLE hrm.employment_events ADD CONSTRAINT employment_events_pkey PRIMARY KEY (id);
ALTER TABLE hrm.employment_events ADD CONSTRAINT uq_employment_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.goal_metrics (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  goal_id uuid NOT NULL,
  name text NOT NULL,
  unit text,
  target_value numeric(18,2),
  actual_value numeric(18,2),
  weightage_pct numeric(9,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.goal_metrics ADD CONSTRAINT ck_goal_metrics_weightage_pct CHECK (((weightage_pct >= (0)::numeric) AND (weightage_pct <= (100)::numeric)));
ALTER TABLE hrm.goal_metrics ADD CONSTRAINT goal_metrics_pkey PRIMARY KEY (id);
ALTER TABLE hrm.goal_metrics ADD CONSTRAINT uq_goal_metrics_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.goal_milestones (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  goal_id uuid NOT NULL,
  title text NOT NULL,
  due_date date,
  completed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.goal_milestones ADD CONSTRAINT goal_milestones_pkey PRIMARY KEY (id);
ALTER TABLE hrm.goal_milestones ADD CONSTRAINT uq_goal_milestones_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.improvement_plans (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  plan_id uuid,
  plan_type text NOT NULL,
  objectives jsonb DEFAULT '{}'::jsonb NOT NULL,
  start_date date NOT NULL,
  end_date date,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  outcome text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.improvement_plans ADD CONSTRAINT ck_improvement_plans_plan_type CHECK ((plan_type = ANY (ARRAY['PIP'::text, 'DEVELOPMENT'::text])));
ALTER TABLE hrm.improvement_plans ADD CONSTRAINT ck_improvement_plans_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'COMPLETED'::text, 'EXTENDED'::text, 'TERMINATED'::text])));
ALTER TABLE hrm.improvement_plans ADD CONSTRAINT improvement_plans_pkey PRIMARY KEY (id);
ALTER TABLE hrm.improvement_plans ADD CONSTRAINT uq_improvement_plans_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.interview_evaluations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  interview_id uuid NOT NULL,
  panelist_employee_id uuid,
  score numeric(18,2),
  recommendation text,
  criteria_scores jsonb DEFAULT '{}'::jsonb,
  comments text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.interview_evaluations ADD CONSTRAINT ck_interview_evaluations_recommendation CHECK ((recommendation = ANY (ARRAY['STRONG_YES'::text, 'YES'::text, 'NO'::text, 'STRONG_NO'::text])));
ALTER TABLE hrm.interview_evaluations ADD CONSTRAINT interview_evaluations_pkey PRIMARY KEY (id);
ALTER TABLE hrm.interview_evaluations ADD CONSTRAINT uq_interview_evaluations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.interview_panelists (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  interview_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  role text DEFAULT 'MEMBER'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.interview_panelists ADD CONSTRAINT ck_interview_panelists_role CHECK ((role = ANY (ARRAY['CHAIR'::text, 'MEMBER'::text, 'OBSERVER'::text, 'EXTERNAL_EXPERT'::text])));
ALTER TABLE hrm.interview_panelists ADD CONSTRAINT interview_panelists_pkey PRIMARY KEY (id);
ALTER TABLE hrm.interview_panelists ADD CONSTRAINT uq_interview_panelists_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.interview_rounds (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  posting_id uuid NOT NULL,
  round_no smallint NOT NULL,
  name text NOT NULL,
  round_type text,
  max_score numeric(18,2),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.interview_rounds ADD CONSTRAINT ck_interview_rounds_round_no CHECK ((round_no > 0));
ALTER TABLE hrm.interview_rounds ADD CONSTRAINT ck_interview_rounds_round_type CHECK ((round_type = ANY (ARRAY['SCREENING'::text, 'TECHNICAL'::text, 'TEACHING_DEMO'::text, 'HR'::text, 'PANEL'::text])));
ALTER TABLE hrm.interview_rounds ADD CONSTRAINT interview_rounds_pkey PRIMARY KEY (id);
ALTER TABLE hrm.interview_rounds ADD CONSTRAINT uq_interview_rounds_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.interviews (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  application_id uuid NOT NULL,
  round_id uuid NOT NULL,
  scheduled_at timestamp with time zone,
  room_id uuid,
  mode text,
  status text DEFAULT 'SCHEDULED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.interviews ADD CONSTRAINT ck_interviews_mode CHECK ((mode = ANY (ARRAY['IN_PERSON'::text, 'ONLINE'::text])));
ALTER TABLE hrm.interviews ADD CONSTRAINT ck_interviews_status CHECK ((status = ANY (ARRAY['SCHEDULED'::text, 'COMPLETED'::text, 'CANCELLED'::text, 'NO_SHOW'::text])));
ALTER TABLE hrm.interviews ADD CONSTRAINT interviews_pkey PRIMARY KEY (id);
ALTER TABLE hrm.interviews ADD CONSTRAINT uq_interviews_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.job_descriptions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  designation_id uuid,
  title text NOT NULL,
  summary text,
  responsibilities jsonb DEFAULT '{}'::jsonb,
  qualifications jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.job_descriptions ADD CONSTRAINT ck_job_descriptions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE hrm.job_descriptions ADD CONSTRAINT job_descriptions_pkey PRIMARY KEY (id);
ALTER TABLE hrm.job_descriptions ADD CONSTRAINT uq_job_descriptions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.job_postings (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  requisition_id uuid,
  job_description_id uuid,
  channel_id uuid,
  posting_no text NOT NULL,
  title text NOT NULL,
  opens_on date,
  closes_on date,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.job_postings ADD CONSTRAINT ck_job_postings_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'CLOSED'::text, 'CANCELLED'::text])));
ALTER TABLE hrm.job_postings ADD CONSTRAINT job_postings_pkey PRIMARY KEY (id);
ALTER TABLE hrm.job_postings ADD CONSTRAINT uq_job_postings_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.learning_path_courses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  path_id uuid NOT NULL,
  course_id uuid NOT NULL,
  seq smallint DEFAULT 1 NOT NULL,
  is_mandatory boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.learning_path_courses ADD CONSTRAINT learning_path_courses_pkey PRIMARY KEY (id);
ALTER TABLE hrm.learning_path_courses ADD CONSTRAINT uq_learning_path_courses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.learning_paths (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  description text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.learning_paths ADD CONSTRAINT ck_learning_paths_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE hrm.learning_paths ADD CONSTRAINT learning_paths_pkey PRIMARY KEY (id);
ALTER TABLE hrm.learning_paths ADD CONSTRAINT uq_learning_paths_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.leave_application_days (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  application_id uuid NOT NULL,
  leave_date date NOT NULL,
  day_fraction numeric(18,2) DEFAULT 1 NOT NULL,
  is_holiday boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.leave_application_days ADD CONSTRAINT ck_leave_application_days_x1 CHECK ((day_fraction = ANY (ARRAY[0.5, (1)::numeric])));
ALTER TABLE hrm.leave_application_days ADD CONSTRAINT leave_application_days_pkey PRIMARY KEY (id);
ALTER TABLE hrm.leave_application_days ADD CONSTRAINT uq_leave_application_days_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.leave_applications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  leave_type_id uuid NOT NULL,
  from_date date NOT NULL,
  to_date date NOT NULL,
  total_days numeric(18,2) NOT NULL,
  reason text,
  contact_during_leave text,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  cancelled_at timestamp with time zone,
  cancel_reason text,
  substitute_employee_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.leave_applications ADD CONSTRAINT ck_leave_applications_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'APPROVED'::text, 'REJECTED'::text, 'CANCELLED'::text, 'WITHDRAWN'::text])));
ALTER TABLE hrm.leave_applications ADD CONSTRAINT ck_leave_applications_total_days CHECK ((total_days > (0)::numeric));
ALTER TABLE hrm.leave_applications ADD CONSTRAINT ck_leave_applications_x1 CHECK ((to_date >= from_date));
ALTER TABLE hrm.leave_applications ADD CONSTRAINT leave_applications_pkey PRIMARY KEY (id);
ALTER TABLE hrm.leave_applications ADD CONSTRAINT uq_leave_applications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.leave_entitlements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  leave_type_id uuid NOT NULL,
  policy_id uuid,
  calendar_year smallint NOT NULL,
  entitled_days numeric(18,2) NOT NULL,
  carried_forward_days numeric(18,2) DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT ck_leave_entitlements_carried_forward_days CHECK ((carried_forward_days >= (0)::numeric));
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT ck_leave_entitlements_entitled_days CHECK ((entitled_days >= (0)::numeric));
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT leave_entitlements_pkey PRIMARY KEY (id);
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT uq_leave_entitlements_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.leave_ledger (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  leave_type_id uuid NOT NULL,
  application_id uuid,
  entry_type text NOT NULL,
  days numeric(18,2) NOT NULL,
  effective_date date NOT NULL,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.leave_ledger ADD CONSTRAINT ck_leave_ledger_entry_type CHECK ((entry_type = ANY (ARRAY['OPENING'::text, 'ACCRUAL'::text, 'CONSUMED'::text, 'CARRY_FORWARD'::text, 'LAPSED'::text, 'ENCASHED'::text, 'ADJUSTMENT'::text, 'CANCELLATION_REVERSAL'::text])));
ALTER TABLE hrm.leave_ledger ADD CONSTRAINT leave_ledger_pkey PRIMARY KEY (id);
ALTER TABLE hrm.leave_ledger ADD CONSTRAINT uq_leave_ledger_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.leave_policies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  leave_type_id uuid NOT NULL,
  name text NOT NULL,
  applies_to jsonb DEFAULT '{}'::jsonb,
  annual_quota numeric(18,2),
  accrual_type text DEFAULT 'YEARLY'::text NOT NULL,
  carry_forward_limit numeric(18,2),
  max_consecutive_days numeric(18,2),
  min_notice_days integer,
  sandwich_rule boolean DEFAULT false NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.leave_policies ADD CONSTRAINT ck_leave_policies_accrual_type CHECK ((accrual_type = ANY (ARRAY['YEARLY'::text, 'MONTHLY'::text, 'NONE'::text])));
ALTER TABLE hrm.leave_policies ADD CONSTRAINT ck_leave_policies_x1 CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
ALTER TABLE hrm.leave_policies ADD CONSTRAINT leave_policies_pkey PRIMARY KEY (id);
ALTER TABLE hrm.leave_policies ADD CONSTRAINT uq_leave_policies_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.leave_types (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  is_paid boolean DEFAULT true NOT NULL,
  is_encashable boolean DEFAULT false NOT NULL,
  requires_document boolean DEFAULT false NOT NULL,
  gender_restriction text,
  unit text DEFAULT 'DAY'::text NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.leave_types ADD CONSTRAINT ck_leave_types_unit CHECK ((unit = ANY (ARRAY['DAY'::text, 'HALF_DAY'::text, 'HOUR'::text])));
ALTER TABLE hrm.leave_types ADD CONSTRAINT leave_types_pkey PRIMARY KEY (id);
ALTER TABLE hrm.leave_types ADD CONSTRAINT uq_leave_types_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.offers (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  application_id uuid NOT NULL,
  offer_no text NOT NULL,
  designation_id uuid,
  proposed_ctc numeric(18,2),
  joining_date date,
  valid_until date,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  document_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.offers ADD CONSTRAINT ck_offers_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PENDING_APPROVAL'::text, 'APPROVED'::text, 'SENT'::text, 'ACCEPTED'::text, 'DECLINED'::text, 'EXPIRED'::text, 'WITHDRAWN'::text])));
ALTER TABLE hrm.offers ADD CONSTRAINT offers_pkey PRIMARY KEY (id);
ALTER TABLE hrm.offers ADD CONSTRAINT uq_offers_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.onboarding_tasks (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  onboarding_id uuid NOT NULL,
  task_name text NOT NULL,
  owner_role_id uuid,
  assignee_id uuid,
  due_date date,
  requires_document boolean DEFAULT false NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  completed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.onboarding_tasks ADD CONSTRAINT ck_onboarding_tasks_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'DONE'::text, 'SKIPPED'::text])));
ALTER TABLE hrm.onboarding_tasks ADD CONSTRAINT onboarding_tasks_pkey PRIMARY KEY (id);
ALTER TABLE hrm.onboarding_tasks ADD CONSTRAINT uq_onboarding_tasks_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.onboardings (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  offer_id uuid,
  employee_id uuid,
  candidate_id uuid,
  expected_join_date date,
  status text DEFAULT 'PENDING'::text NOT NULL,
  completed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.onboardings ADD CONSTRAINT ck_onboardings_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'CANCELLED'::text])));
ALTER TABLE hrm.onboardings ADD CONSTRAINT onboardings_pkey PRIMARY KEY (id);
ALTER TABLE hrm.onboardings ADD CONSTRAINT uq_onboardings_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.performance_calibrations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  cycle_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  original_level_id uuid,
  calibrated_level_id uuid,
  reason text,
  calibrated_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT performance_calibrations_pkey PRIMARY KEY (id);
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT uq_performance_calibrations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.performance_cycles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  name text NOT NULL,
  academic_year_id uuid,
  start_date date NOT NULL,
  end_date date NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  rating_scale_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.performance_cycles ADD CONSTRAINT ck_performance_cycles_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'IN_REVIEW'::text, 'CALIBRATION'::text, 'CLOSED'::text])));
ALTER TABLE hrm.performance_cycles ADD CONSTRAINT performance_cycles_pkey PRIMARY KEY (id);
ALTER TABLE hrm.performance_cycles ADD CONSTRAINT uq_performance_cycles_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.performance_feedback (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  given_by_employee_id uuid,
  plan_id uuid,
  feedback_type text DEFAULT 'CONTINUOUS'::text NOT NULL,
  body text NOT NULL,
  is_anonymous boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.performance_feedback ADD CONSTRAINT ck_performance_feedback_feedback_type CHECK ((feedback_type = ANY (ARRAY['CONTINUOUS'::text, 'PEER'::text, 'UPWARD'::text, 'RECOGNITION'::text])));
ALTER TABLE hrm.performance_feedback ADD CONSTRAINT performance_feedback_pkey PRIMARY KEY (id);
ALTER TABLE hrm.performance_feedback ADD CONSTRAINT uq_performance_feedback_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.performance_goals (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  plan_id uuid NOT NULL,
  parent_goal_id uuid,
  goal_type text DEFAULT 'GOAL'::text NOT NULL,
  title text NOT NULL,
  description text,
  weightage_pct numeric(9,4),
  due_date date,
  progress_pct numeric(9,4),
  status text DEFAULT 'NOT_STARTED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.performance_goals ADD CONSTRAINT ck_performance_goals_goal_type CHECK ((goal_type = ANY (ARRAY['GOAL'::text, 'KRA'::text])));
ALTER TABLE hrm.performance_goals ADD CONSTRAINT ck_performance_goals_progress_pct CHECK (((progress_pct >= (0)::numeric) AND (progress_pct <= (100)::numeric)));
ALTER TABLE hrm.performance_goals ADD CONSTRAINT ck_performance_goals_status CHECK ((status = ANY (ARRAY['NOT_STARTED'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'DEFERRED'::text, 'CANCELLED'::text])));
ALTER TABLE hrm.performance_goals ADD CONSTRAINT ck_performance_goals_weightage_pct CHECK (((weightage_pct >= (0)::numeric) AND (weightage_pct <= (100)::numeric)));
ALTER TABLE hrm.performance_goals ADD CONSTRAINT performance_goals_pkey PRIMARY KEY (id);
ALTER TABLE hrm.performance_goals ADD CONSTRAINT uq_performance_goals_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.performance_plans (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  cycle_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  manager_id uuid,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.performance_plans ADD CONSTRAINT ck_performance_plans_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'APPROVED'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text])));
ALTER TABLE hrm.performance_plans ADD CONSTRAINT performance_plans_pkey PRIMARY KEY (id);
ALTER TABLE hrm.performance_plans ADD CONSTRAINT uq_performance_plans_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.performance_reviews (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  plan_id uuid NOT NULL,
  reviewer_employee_id uuid,
  review_type text NOT NULL,
  overall_score numeric(18,2),
  rating_level_id uuid,
  comments text,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  submitted_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT ck_performance_reviews_review_type CHECK ((review_type = ANY (ARRAY['SELF'::text, 'MANAGER'::text, 'PEER'::text, 'SKIP_LEVEL'::text, 'HR'::text, 'FINAL'::text])));
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT ck_performance_reviews_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'ACKNOWLEDGED'::text])));
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT performance_reviews_pkey PRIMARY KEY (id);
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT uq_performance_reviews_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.rating_scale_levels (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scale_id uuid NOT NULL,
  level_no smallint NOT NULL,
  label text NOT NULL,
  min_score numeric(18,2),
  max_score numeric(18,2),
  descriptor text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.rating_scale_levels ADD CONSTRAINT ck_rating_scale_levels_level_no CHECK ((level_no > 0));
ALTER TABLE hrm.rating_scale_levels ADD CONSTRAINT rating_scale_levels_pkey PRIMARY KEY (id);
ALTER TABLE hrm.rating_scale_levels ADD CONSTRAINT uq_rating_scale_levels_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.rating_scales (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.rating_scales ADD CONSTRAINT ck_rating_scales_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE hrm.rating_scales ADD CONSTRAINT rating_scales_pkey PRIMARY KEY (id);
ALTER TABLE hrm.rating_scales ADD CONSTRAINT uq_rating_scales_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.recruitment_channels (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  channel_type text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.recruitment_channels ADD CONSTRAINT recruitment_channels_pkey PRIMARY KEY (id);
ALTER TABLE hrm.recruitment_channels ADD CONSTRAINT uq_recruitment_channels_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.requisitions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  department_id uuid,
  designation_id uuid,
  requisition_no text NOT NULL,
  positions integer NOT NULL,
  justification text,
  employment_type text,
  target_join_date date,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  requested_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.requisitions ADD CONSTRAINT ck_requisitions_positions CHECK ((positions > 0));
ALTER TABLE hrm.requisitions ADD CONSTRAINT ck_requisitions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PENDING_APPROVAL'::text, 'APPROVED'::text, 'REJECTED'::text, 'FILLED'::text, 'CANCELLED'::text])));
ALTER TABLE hrm.requisitions ADD CONSTRAINT requisitions_pkey PRIMARY KEY (id);
ALTER TABLE hrm.requisitions ADD CONSTRAINT uq_requisitions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.shift_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  shift_id uuid NOT NULL,
  valid_from date NOT NULL,
  valid_to date,
  weekly_pattern jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.shift_assignments ADD CONSTRAINT shift_assignments_pkey PRIMARY KEY (id);
ALTER TABLE hrm.shift_assignments ADD CONSTRAINT uq_shift_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.shifts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  start_time time without time zone NOT NULL,
  end_time time without time zone NOT NULL,
  break_minutes integer DEFAULT 0 NOT NULL,
  grace_minutes integer DEFAULT 0 NOT NULL,
  is_night_shift boolean DEFAULT false NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.shifts ADD CONSTRAINT shifts_pkey PRIMARY KEY (id);
ALTER TABLE hrm.shifts ADD CONSTRAINT uq_shifts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.skills (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  category text,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.skills ADD CONSTRAINT skills_pkey PRIMARY KEY (id);
ALTER TABLE hrm.skills ADD CONSTRAINT uq_skills_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_assessment_attempts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  assessment_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  attempt_no smallint DEFAULT 1 NOT NULL,
  score numeric(18,2),
  passed boolean,
  attempted_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.training_assessment_attempts ADD CONSTRAINT training_assessment_attempts_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_assessment_attempts ADD CONSTRAINT uq_training_assessment_attempts_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_assessments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid NOT NULL,
  title text NOT NULL,
  max_score numeric(18,2),
  pass_score numeric(18,2),
  max_attempts smallint,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.training_assessments ADD CONSTRAINT training_assessments_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_assessments ADD CONSTRAINT uq_training_assessments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_attendance (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  enrollment_id uuid NOT NULL,
  status text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.training_attendance ADD CONSTRAINT ck_training_attendance_status CHECK ((status = ANY (ARRAY['PRESENT'::text, 'ABSENT'::text, 'EXCUSED'::text])));
ALTER TABLE hrm.training_attendance ADD CONSTRAINT training_attendance_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_attendance ADD CONSTRAINT uq_training_attendance_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_completions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  enrollment_id uuid NOT NULL,
  completed_on date NOT NULL,
  score numeric(18,2),
  result text,
  certificate_document_id uuid,
  certificate_no text,
  feedback_rating smallint,
  feedback_comment text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.training_completions ADD CONSTRAINT ck_training_completions_result CHECK ((result = ANY (ARRAY['PASS'::text, 'FAIL'::text, 'COMPLETED'::text])));
ALTER TABLE hrm.training_completions ADD CONSTRAINT ck_training_completions_x1 CHECK (((feedback_rating IS NULL) OR ((feedback_rating >= 1) AND (feedback_rating <= 5))));
ALTER TABLE hrm.training_completions ADD CONSTRAINT training_completions_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_completions ADD CONSTRAINT uq_training_completions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_course_skills (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid NOT NULL,
  skill_id uuid NOT NULL,
  target_level smallint,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.training_course_skills ADD CONSTRAINT training_course_skills_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_course_skills ADD CONSTRAINT uq_training_course_skills_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_courses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  program_id uuid NOT NULL,
  code text NOT NULL,
  title text NOT NULL,
  duration_hours numeric(18,2),
  delivery_mode text,
  max_participants integer,
  certificate_template_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.training_courses ADD CONSTRAINT ck_training_courses_delivery_mode CHECK ((delivery_mode = ANY (ARRAY['CLASSROOM'::text, 'ONLINE'::text, 'HYBRID'::text])));
ALTER TABLE hrm.training_courses ADD CONSTRAINT training_courses_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_courses ADD CONSTRAINT uq_training_courses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_enrollments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  nominated_by uuid,
  status text DEFAULT 'NOMINATED'::text NOT NULL,
  workflow_instance_id uuid,
  enrolled_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT ck_training_enrollments_status CHECK ((status = ANY (ARRAY['NOMINATED'::text, 'APPROVED'::text, 'ENROLLED'::text, 'COMPLETED'::text, 'DROPPED'::text, 'REJECTED'::text])));
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT training_enrollments_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT uq_training_enrollments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_programs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  description text,
  program_type text,
  provider text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.training_programs ADD CONSTRAINT ck_training_programs_program_type CHECK ((program_type = ANY (ARRAY['FDP'::text, 'WORKSHOP'::text, 'SEMINAR'::text, 'CERTIFICATION'::text, 'ORIENTATION'::text, 'ONLINE'::text])));
ALTER TABLE hrm.training_programs ADD CONSTRAINT ck_training_programs_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE hrm.training_programs ADD CONSTRAINT training_programs_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_programs ADD CONSTRAINT uq_training_programs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_sessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid NOT NULL,
  title text,
  session_date date NOT NULL,
  start_time time without time zone,
  end_time time without time zone,
  room_id uuid,
  virtual_url text,
  status text DEFAULT 'SCHEDULED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE hrm.training_sessions ADD CONSTRAINT ck_training_sessions_status CHECK ((status = ANY (ARRAY['SCHEDULED'::text, 'COMPLETED'::text, 'CANCELLED'::text])));
ALTER TABLE hrm.training_sessions ADD CONSTRAINT training_sessions_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_sessions ADD CONSTRAINT uq_training_sessions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS hrm.training_trainers (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  employee_id uuid,
  external_trainer_name text,
  role text DEFAULT 'TRAINER'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE hrm.training_trainers ADD CONSTRAINT ck_training_trainers_x1 CHECK (((employee_id IS NOT NULL) OR (external_trainer_name IS NOT NULL)));
ALTER TABLE hrm.training_trainers ADD CONSTRAINT training_trainers_pkey PRIMARY KEY (id);
ALTER TABLE hrm.training_trainers ADD CONSTRAINT uq_training_trainers_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.access_review_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  review_id uuid NOT NULL,
  role_assignment_id uuid NOT NULL,
  decision text,
  decided_at timestamp with time zone,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.access_review_items ADD CONSTRAINT ck_access_review_items_decision CHECK ((decision = ANY (ARRAY['KEEP'::text, 'REVOKE'::text, 'MODIFY'::text])));
ALTER TABLE iam.access_review_items ADD CONSTRAINT access_review_items_pkey PRIMARY KEY (id);
ALTER TABLE iam.access_review_items ADD CONSTRAINT uq_access_review_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.access_reviews (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  cycle_name text NOT NULL,
  reviewer_id uuid,
  status text DEFAULT 'PENDING'::text NOT NULL,
  due_on date,
  completed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.access_reviews ADD CONSTRAINT ck_access_reviews_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'EXPIRED'::text])));
ALTER TABLE iam.access_reviews ADD CONSTRAINT access_reviews_pkey PRIMARY KEY (id);
ALTER TABLE iam.access_reviews ADD CONSTRAINT uq_access_reviews_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.api_client_grants (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  client_id uuid NOT NULL,
  permission_id uuid NOT NULL,
  college_id uuid,
  department_id uuid,
  resource_scope jsonb DEFAULT '{}'::jsonb,
  valid_to timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.api_client_grants ADD CONSTRAINT api_client_grants_pkey PRIMARY KEY (id);
ALTER TABLE iam.api_client_grants ADD CONSTRAINT uq_api_client_grants_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.api_clients (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  user_id uuid NOT NULL,
  name text NOT NULL,
  owner_user_id uuid,
  description text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  allowed_ips text[],
  expires_at timestamp with time zone,
  last_used_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.api_clients ADD CONSTRAINT ck_api_clients_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'REVOKED'::text])));
ALTER TABLE iam.api_clients ADD CONSTRAINT api_clients_pkey PRIMARY KEY (id);
ALTER TABLE iam.api_clients ADD CONSTRAINT uq_api_clients_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.delegations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  from_user_id uuid NOT NULL,
  to_user_id uuid NOT NULL,
  role_id uuid NOT NULL,
  valid_from timestamp with time zone NOT NULL,
  valid_to timestamp with time zone NOT NULL,
  reason text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.delegations ADD CONSTRAINT ck_delegations_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'REVOKED'::text, 'EXPIRED'::text])));
ALTER TABLE iam.delegations ADD CONSTRAINT ck_delegations_x1 CHECK ((valid_to > valid_from));
ALTER TABLE iam.delegations ADD CONSTRAINT delegations_pkey PRIMARY KEY (id);
ALTER TABLE iam.delegations ADD CONSTRAINT uq_delegations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.department_access (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  user_id uuid NOT NULL,
  department_id uuid NOT NULL,
  access_level text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.department_access ADD CONSTRAINT ck_department_access_access_level CHECK ((access_level = ANY (ARRAY['READ'::text, 'WRITE'::text, 'ADMIN'::text])));
ALTER TABLE iam.department_access ADD CONSTRAINT department_access_pkey PRIMARY KEY (id);
ALTER TABLE iam.department_access ADD CONSTRAINT uq_department_access_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.permissions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  code text NOT NULL,
  module text NOT NULL,
  resource text,
  action text NOT NULL,
  description text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.permissions ADD CONSTRAINT permissions_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS iam.role_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  user_id uuid,
  group_id uuid,
  role_id uuid NOT NULL,
  college_id uuid,
  department_id uuid,
  valid_from date,
  valid_to date,
  granted_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.role_assignments ADD CONSTRAINT ck_role_assignments_x1 CHECK (((user_id IS NULL) <> (group_id IS NULL)));
ALTER TABLE iam.role_assignments ADD CONSTRAINT ck_role_assignments_x2 CHECK (((valid_to IS NULL) OR (valid_from IS NULL) OR (valid_to >= valid_from)));
ALTER TABLE iam.role_assignments ADD CONSTRAINT role_assignments_pkey PRIMARY KEY (id);
ALTER TABLE iam.role_assignments ADD CONSTRAINT uq_role_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.role_grant_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  grantor_role text NOT NULL,
  grantable_role text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.role_grant_rules ADD CONSTRAINT role_grant_rules_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS iam.role_hierarchy (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  parent_role text NOT NULL,
  child_role text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.role_hierarchy ADD CONSTRAINT role_hierarchy_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS iam.role_permissions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  role_id uuid NOT NULL,
  permission_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE iam.role_permissions ADD CONSTRAINT role_permissions_pkey PRIMARY KEY (id);
ALTER TABLE iam.role_permissions ADD CONSTRAINT uq_role_permissions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.role_template_permissions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  role_code text NOT NULL,
  permission_code text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.role_template_permissions ADD CONSTRAINT role_template_permissions_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS iam.role_templates (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  catalogue_id text,
  kind text,
  scope_note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.role_templates ADD CONSTRAINT role_templates_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS iam.roles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  description text,
  is_system boolean DEFAULT false NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.roles ADD CONSTRAINT ck_roles_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])));
ALTER TABLE iam.roles ADD CONSTRAINT roles_pkey PRIMARY KEY (id);
ALTER TABLE iam.roles ADD CONSTRAINT uq_roles_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.transition_log (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  table_schema text NOT NULL,
  table_name text NOT NULL,
  record_id uuid NOT NULL,
  status_column text NOT NULL,
  from_status text,
  to_status text,
  actor_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE iam.transition_log ADD CONSTRAINT transition_log_pkey PRIMARY KEY (id);
ALTER TABLE iam.transition_log ADD CONSTRAINT uq_transition_log_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.transition_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  table_schema text NOT NULL,
  table_name text NOT NULL,
  status_column text DEFAULT 'status'::text NOT NULL,
  from_status text,
  to_status text NOT NULL,
  any_permission text[] DEFAULT '{}'::text[],
  sod_distinct_from text[] DEFAULT '{}'::text[],
  block_self text,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.transition_rules ADD CONSTRAINT transition_rules_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS iam.user_group_members (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  group_id uuid NOT NULL,
  user_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE iam.user_group_members ADD CONSTRAINT user_group_members_pkey PRIMARY KEY (id);
ALTER TABLE iam.user_group_members ADD CONSTRAINT uq_user_group_members_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.user_groups (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  description text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.user_groups ADD CONSTRAINT user_groups_pkey PRIMARY KEY (id);
ALTER TABLE iam.user_groups ADD CONSTRAINT uq_user_groups_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS iam.user_profiles (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid,
  college_id uuid,
  department_id uuid,
  username text,
  email text NOT NULL,
  full_name text NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  preferences jsonb DEFAULT '{}'::jsonb,
  last_seen_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE iam.user_profiles ADD CONSTRAINT ck_user_profiles_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'LOCKED'::text, 'INACTIVE'::text])));
ALTER TABLE iam.user_profiles ADD CONSTRAINT user_profiles_pkey PRIMARY KEY (id);
ALTER TABLE iam.user_profiles ADD CONSTRAINT uq_user_profiles_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.access_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  target_type text NOT NULL,
  target_id uuid NOT NULL,
  audience_type text NOT NULL,
  audience_ref_id uuid,
  permission text DEFAULT 'VIEW'::text NOT NULL,
  valid_from timestamp with time zone,
  valid_to timestamp with time zone,
  policy jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.access_rules ADD CONSTRAINT ck_access_rules_audience_type CHECK ((audience_type = ANY (ARRAY['ALL'::text, 'ROLE'::text, 'DEPARTMENT'::text, 'PROGRAM'::text, 'BATCH'::text, 'SECTION'::text, 'STUDENT'::text, 'EMPLOYEE'::text, 'GROUP'::text])));
ALTER TABLE lms.access_rules ADD CONSTRAINT ck_access_rules_permission CHECK ((permission = ANY (ARRAY['VIEW'::text, 'DOWNLOAD'::text, 'COMMENT'::text, 'EDIT'::text, 'SUBMIT'::text, 'HOST'::text])));
ALTER TABLE lms.access_rules ADD CONSTRAINT ck_access_rules_target_type CHECK ((target_type = ANY (ARRAY['CONTENT'::text, 'RESOURCE'::text, 'CLASSROOM'::text, 'ASSIGNMENT'::text, 'COLLECTION'::text])));
ALTER TABLE lms.access_rules ADD CONSTRAINT access_rules_pkey PRIMARY KEY (id);
ALTER TABLE lms.access_rules ADD CONSTRAINT uq_access_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.assignment_questions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  assignment_id uuid NOT NULL,
  seq integer DEFAULT 1 NOT NULL,
  question_type text DEFAULT 'SUBJECTIVE'::text NOT NULL,
  prompt text NOT NULL,
  options jsonb,
  answer_key jsonb,
  max_marks numeric(18,2) NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.assignment_questions ADD CONSTRAINT ck_assignment_questions_max_marks CHECK ((max_marks >= (0)::numeric));
ALTER TABLE lms.assignment_questions ADD CONSTRAINT ck_assignment_questions_question_type CHECK ((question_type = ANY (ARRAY['SUBJECTIVE'::text, 'MCQ'::text, 'TRUE_FALSE'::text, 'CODE'::text, 'FILE'::text])));
ALTER TABLE lms.assignment_questions ADD CONSTRAINT assignment_questions_pkey PRIMARY KEY (id);
ALTER TABLE lms.assignment_questions ADD CONSTRAINT uq_assignment_questions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.assignment_resources (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  assignment_id uuid NOT NULL,
  resource_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.assignment_resources ADD CONSTRAINT assignment_resources_pkey PRIMARY KEY (id);
ALTER TABLE lms.assignment_resources ADD CONSTRAINT uq_assignment_resources_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  section_id uuid,
  course_id uuid,
  subject_id uuid,
  created_by_employee_id uuid,
  rubric_id uuid,
  title text NOT NULL,
  instructions text,
  assignment_type text DEFAULT 'ASSIGNMENT'::text NOT NULL,
  max_marks numeric(18,2),
  weightage_pct numeric(9,4),
  available_from timestamp with time zone,
  due_at timestamp with time zone,
  close_at timestamp with time zone,
  late_penalty_pct numeric(9,4),
  allow_resubmission boolean DEFAULT false NOT NULL,
  version_no integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  assessment_component_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.assignments ADD CONSTRAINT ck_assignments_assignment_type CHECK ((assignment_type = ANY (ARRAY['ASSIGNMENT'::text, 'QUIZ'::text, 'PROJECT'::text, 'LAB'::text, 'PRESENTATION'::text])));
ALTER TABLE lms.assignments ADD CONSTRAINT ck_assignments_late_penalty_pct CHECK (((late_penalty_pct >= (0)::numeric) AND (late_penalty_pct <= (100)::numeric)));
ALTER TABLE lms.assignments ADD CONSTRAINT ck_assignments_max_marks CHECK ((max_marks >= (0)::numeric));
ALTER TABLE lms.assignments ADD CONSTRAINT ck_assignments_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'CLOSED'::text, 'ARCHIVED'::text])));
ALTER TABLE lms.assignments ADD CONSTRAINT ck_assignments_weightage_pct CHECK (((weightage_pct >= (0)::numeric) AND (weightage_pct <= (100)::numeric)));
ALTER TABLE lms.assignments ADD CONSTRAINT assignments_pkey PRIMARY KEY (id);
ALTER TABLE lms.assignments ADD CONSTRAINT uq_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.badge_awards (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  badge_id uuid NOT NULL,
  student_id uuid NOT NULL,
  awarded_at timestamp with time zone DEFAULT now() NOT NULL,
  credential_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.badge_awards ADD CONSTRAINT badge_awards_pkey PRIMARY KEY (id);
ALTER TABLE lms.badge_awards ADD CONSTRAINT uq_badge_awards_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.badge_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  badge_id uuid NOT NULL,
  rule_type text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.badge_rules ADD CONSTRAINT badge_rules_pkey PRIMARY KEY (id);
ALTER TABLE lms.badge_rules ADD CONSTRAINT uq_badge_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.badges (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  description text,
  icon_document_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.badges ADD CONSTRAINT ck_badges_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE lms.badges ADD CONSTRAINT badges_pkey PRIMARY KEY (id);
ALTER TABLE lms.badges ADD CONSTRAINT uq_badges_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.breakout_room_members (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  room_id uuid NOT NULL,
  person_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.breakout_room_members ADD CONSTRAINT breakout_room_members_pkey PRIMARY KEY (id);
ALTER TABLE lms.breakout_room_members ADD CONSTRAINT uq_breakout_room_members_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.breakout_rooms (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  name text NOT NULL,
  opened_at timestamp with time zone,
  closed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.breakout_rooms ADD CONSTRAINT breakout_rooms_pkey PRIMARY KEY (id);
ALTER TABLE lms.breakout_rooms ADD CONSTRAINT uq_breakout_rooms_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.certification_applications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  certification_id uuid NOT NULL,
  student_id uuid NOT NULL,
  status text DEFAULT 'APPLIED'::text NOT NULL,
  applied_at timestamp with time zone DEFAULT now() NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.certification_applications ADD CONSTRAINT ck_certification_applications_status CHECK ((status = ANY (ARRAY['APPLIED'::text, 'ELIGIBLE'::text, 'INELIGIBLE'::text, 'APPROVED'::text, 'REJECTED'::text, 'ISSUED'::text])));
ALTER TABLE lms.certification_applications ADD CONSTRAINT certification_applications_pkey PRIMARY KEY (id);
ALTER TABLE lms.certification_applications ADD CONSTRAINT uq_certification_applications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.certification_programs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  issuer text,
  description text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.certification_programs ADD CONSTRAINT ck_certification_programs_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE lms.certification_programs ADD CONSTRAINT certification_programs_pkey PRIMARY KEY (id);
ALTER TABLE lms.certification_programs ADD CONSTRAINT uq_certification_programs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.certification_requirements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  certification_id uuid NOT NULL,
  requirement_type text NOT NULL,
  ref_id uuid,
  params jsonb DEFAULT '{}'::jsonb,
  is_mandatory boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.certification_requirements ADD CONSTRAINT ck_certification_requirements_requirement_type CHECK ((requirement_type = ANY (ARRAY['COURSE'::text, 'SUBJECT'::text, 'CREDITS'::text, 'ATTENDANCE'::text, 'ASSESSMENT'::text, 'CUSTOM'::text])));
ALTER TABLE lms.certification_requirements ADD CONSTRAINT certification_requirements_pkey PRIMARY KEY (id);
ALTER TABLE lms.certification_requirements ADD CONSTRAINT uq_certification_requirements_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.certifications (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  program_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  credential_type text DEFAULT 'CERTIFICATE'::text NOT NULL,
  validity_months smallint,
  template_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.certifications ADD CONSTRAINT ck_certifications_credential_type CHECK ((credential_type = ANY (ARRAY['CERTIFICATE'::text, 'MICRO_CREDENTIAL'::text, 'DIPLOMA'::text])));
ALTER TABLE lms.certifications ADD CONSTRAINT ck_certifications_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE lms.certifications ADD CONSTRAINT certifications_pkey PRIMARY KEY (id);
ALTER TABLE lms.certifications ADD CONSTRAINT uq_certifications_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.content_dependencies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  topic_id uuid NOT NULL,
  depends_on_topic_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.content_dependencies ADD CONSTRAINT ck_content_dependencies_x1 CHECK ((topic_id <> depends_on_topic_id));
ALTER TABLE lms.content_dependencies ADD CONSTRAINT content_dependencies_pkey PRIMARY KEY (id);
ALTER TABLE lms.content_dependencies ADD CONSTRAINT uq_content_dependencies_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.content_modules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  content_id uuid NOT NULL,
  seq integer DEFAULT 1 NOT NULL,
  title text NOT NULL,
  description text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.content_modules ADD CONSTRAINT content_modules_pkey PRIMARY KEY (id);
ALTER TABLE lms.content_modules ADD CONSTRAINT uq_content_modules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.content_topics (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  module_id uuid NOT NULL,
  seq integer DEFAULT 1 NOT NULL,
  title text NOT NULL,
  content_type text DEFAULT 'TEXT'::text NOT NULL,
  body text,
  document_id uuid,
  external_url text,
  duration_min integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.content_topics ADD CONSTRAINT ck_content_topics_content_type CHECK ((content_type = ANY (ARRAY['TEXT'::text, 'VIDEO'::text, 'DOCUMENT'::text, 'QUIZ'::text, 'LINK'::text, 'SCORM'::text, 'LIVE'::text])));
ALTER TABLE lms.content_topics ADD CONSTRAINT ck_content_topics_duration_min CHECK ((duration_min >= 0));
ALTER TABLE lms.content_topics ADD CONSTRAINT content_topics_pkey PRIMARY KEY (id);
ALTER TABLE lms.content_topics ADD CONSTRAINT uq_content_topics_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.contents (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  course_id uuid,
  subject_id uuid,
  owner_employee_id uuid,
  title text NOT NULL,
  description text,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  metadata jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.contents ADD CONSTRAINT ck_contents_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'IN_REVIEW'::text, 'PUBLISHED'::text, 'ARCHIVED'::text])));
ALTER TABLE lms.contents ADD CONSTRAINT contents_pkey PRIMARY KEY (id);
ALTER TABLE lms.contents ADD CONSTRAINT uq_contents_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.credential_revocations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  credential_id uuid NOT NULL,
  reason text NOT NULL,
  revoked_by uuid,
  revoked_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.credential_revocations ADD CONSTRAINT credential_revocations_pkey PRIMARY KEY (id);
ALTER TABLE lms.credential_revocations ADD CONSTRAINT uq_credential_revocations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.credential_shares (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  credential_id uuid NOT NULL,
  share_token text NOT NULL,
  channel text,
  expires_at timestamp with time zone,
  revoked_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.credential_shares ADD CONSTRAINT credential_shares_pkey PRIMARY KEY (id);
ALTER TABLE lms.credential_shares ADD CONSTRAINT uq_credential_shares_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.credentials (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  certification_id uuid NOT NULL,
  student_id uuid NOT NULL,
  credential_no text NOT NULL,
  issued_at timestamp with time zone DEFAULT now() NOT NULL,
  expires_at timestamp with time zone,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  document_id uuid,
  verification_code text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.credentials ADD CONSTRAINT ck_credentials_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'REVOKED'::text, 'EXPIRED'::text, 'SUPERSEDED'::text])));
ALTER TABLE lms.credentials ADD CONSTRAINT credentials_pkey PRIMARY KEY (id);
ALTER TABLE lms.credentials ADD CONSTRAINT uq_credentials_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.eligibility_evaluations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  application_id uuid NOT NULL,
  result text NOT NULL,
  detail jsonb,
  evaluated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.eligibility_evaluations ADD CONSTRAINT ck_eligibility_evaluations_result CHECK ((result = ANY (ARRAY['ELIGIBLE'::text, 'INELIGIBLE'::text, 'PARTIAL'::text])));
ALTER TABLE lms.eligibility_evaluations ADD CONSTRAINT eligibility_evaluations_pkey PRIMARY KEY (id);
ALTER TABLE lms.eligibility_evaluations ADD CONSTRAINT uq_eligibility_evaluations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.eligibility_evidence (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  evaluation_id uuid NOT NULL,
  evidence_type text NOT NULL,
  ref_id uuid,
  document_id uuid,
  detail jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.eligibility_evidence ADD CONSTRAINT eligibility_evidence_pkey PRIMARY KEY (id);
ALTER TABLE lms.eligibility_evidence ADD CONSTRAINT uq_eligibility_evidence_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.evaluation_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  evaluation_id uuid NOT NULL,
  question_id uuid,
  criterion_id uuid,
  level_id uuid,
  marks numeric(18,2),
  comment text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.evaluation_items ADD CONSTRAINT ck_evaluation_items_marks CHECK ((marks >= (0)::numeric));
ALTER TABLE lms.evaluation_items ADD CONSTRAINT evaluation_items_pkey PRIMARY KEY (id);
ALTER TABLE lms.evaluation_items ADD CONSTRAINT uq_evaluation_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.evaluations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  submission_id uuid NOT NULL,
  attempt_no integer,
  evaluator_employee_id uuid,
  total_marks numeric(18,2),
  rubric_id uuid,
  status text DEFAULT 'PENDING'::text NOT NULL,
  evaluated_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.evaluations ADD CONSTRAINT ck_evaluations_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'MODERATED'::text])));
ALTER TABLE lms.evaluations ADD CONSTRAINT ck_evaluations_total_marks CHECK ((total_marks >= (0)::numeric));
ALTER TABLE lms.evaluations ADD CONSTRAINT evaluations_pkey PRIMARY KEY (id);
ALTER TABLE lms.evaluations ADD CONSTRAINT uq_evaluations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.grades (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_assignment_id uuid NOT NULL,
  student_id uuid NOT NULL,
  evaluation_id uuid,
  marks numeric(18,2),
  grade_letter text,
  is_published boolean DEFAULT false NOT NULL,
  published_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.grades ADD CONSTRAINT ck_grades_marks CHECK ((marks >= (0)::numeric));
ALTER TABLE lms.grades ADD CONSTRAINT grades_pkey PRIMARY KEY (id);
ALTER TABLE lms.grades ADD CONSTRAINT uq_grades_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.learning_streaks (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  current_streak integer DEFAULT 0 NOT NULL,
  longest_streak integer DEFAULT 0 NOT NULL,
  last_active_on date,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.learning_streaks ADD CONSTRAINT ck_learning_streaks_current_streak CHECK ((current_streak >= 0));
ALTER TABLE lms.learning_streaks ADD CONSTRAINT ck_learning_streaks_longest_streak CHECK ((longest_streak >= 0));
ALTER TABLE lms.learning_streaks ADD CONSTRAINT learning_streaks_pkey PRIMARY KEY (id);
ALTER TABLE lms.learning_streaks ADD CONSTRAINT uq_learning_streaks_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_activities (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  activity_type text NOT NULL,
  title text,
  duration_min integer,
  details jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.lesson_activities ADD CONSTRAINT lesson_activities_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_activities ADD CONSTRAINT uq_lesson_activities_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_content_links (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  topic_id uuid NOT NULL,
  content_topic_id uuid,
  resource_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.lesson_content_links ADD CONSTRAINT ck_lesson_content_links_x1 CHECK (((content_topic_id IS NOT NULL) OR (resource_id IS NOT NULL)));
ALTER TABLE lms.lesson_content_links ADD CONSTRAINT lesson_content_links_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_content_links ADD CONSTRAINT uq_lesson_content_links_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_deliveries (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  delivered_on date NOT NULL,
  delivery_mode text DEFAULT 'CLASSROOM'::text NOT NULL,
  coverage_pct numeric(9,4),
  variance_reason text,
  remarks text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.lesson_deliveries ADD CONSTRAINT ck_lesson_deliveries_coverage_pct CHECK (((coverage_pct >= (0)::numeric) AND (coverage_pct <= (100)::numeric)));
ALTER TABLE lms.lesson_deliveries ADD CONSTRAINT ck_lesson_deliveries_delivery_mode CHECK ((delivery_mode = ANY (ARRAY['CLASSROOM'::text, 'ONLINE'::text, 'HYBRID'::text])));
ALTER TABLE lms.lesson_deliveries ADD CONSTRAINT lesson_deliveries_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_deliveries ADD CONSTRAINT uq_lesson_deliveries_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_faculty (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  plan_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  role text DEFAULT 'CONTRIBUTOR'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.lesson_faculty ADD CONSTRAINT lesson_faculty_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_faculty ADD CONSTRAINT uq_lesson_faculty_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_plan_modules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  plan_id uuid NOT NULL,
  seq integer DEFAULT 1 NOT NULL,
  title text NOT NULL,
  planned_hours smallint,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.lesson_plan_modules ADD CONSTRAINT lesson_plan_modules_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_plan_modules ADD CONSTRAINT uq_lesson_plan_modules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_plan_topics (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  module_id uuid NOT NULL,
  seq integer DEFAULT 1 NOT NULL,
  title text NOT NULL,
  planned_hours numeric(9,4),
  bloom_level text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.lesson_plan_topics ADD CONSTRAINT ck_lesson_plan_topics_x1 CHECK (((bloom_level IS NULL) OR (bloom_level = ANY (ARRAY['REMEMBER'::text, 'UNDERSTAND'::text, 'APPLY'::text, 'ANALYSE'::text, 'EVALUATE'::text, 'CREATE'::text]))));
ALTER TABLE lms.lesson_plan_topics ADD CONSTRAINT lesson_plan_topics_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_plan_topics ADD CONSTRAINT uq_lesson_plan_topics_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_plans (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  section_id uuid,
  subject_id uuid,
  employee_id uuid NOT NULL,
  term_id uuid,
  title text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  workflow_instance_id uuid,
  planned_hours smallint,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.lesson_plans ADD CONSTRAINT ck_lesson_plans_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'APPROVED'::text, 'REJECTED'::text, 'COMPLETED'::text])));
ALTER TABLE lms.lesson_plans ADD CONSTRAINT lesson_plans_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_plans ADD CONSTRAINT uq_lesson_plans_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.lesson_sessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  plan_id uuid NOT NULL,
  topic_id uuid,
  planned_date date,
  planned_start time without time zone,
  planned_end time without time zone,
  status text DEFAULT 'PLANNED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.lesson_sessions ADD CONSTRAINT ck_lesson_sessions_status CHECK ((status = ANY (ARRAY['PLANNED'::text, 'DELIVERED'::text, 'MISSED'::text, 'RESCHEDULED'::text, 'CANCELLED'::text])));
ALTER TABLE lms.lesson_sessions ADD CONSTRAINT lesson_sessions_pkey PRIMARY KEY (id);
ALTER TABLE lms.lesson_sessions ADD CONSTRAINT uq_lesson_sessions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.progress (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NOT NULL,
  status text DEFAULT 'NOT_STARTED'::text NOT NULL,
  percent_complete numeric(9,4) DEFAULT 0 NOT NULL,
  time_spent_sec bigint DEFAULT 0 NOT NULL,
  started_at timestamp with time zone,
  completed_at timestamp with time zone,
  last_activity_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.progress ADD CONSTRAINT ck_progress_percent_complete CHECK (((percent_complete >= (0)::numeric) AND (percent_complete <= (100)::numeric)));
ALTER TABLE lms.progress ADD CONSTRAINT ck_progress_scope_type CHECK ((scope_type = ANY (ARRAY['ACTIVITY'::text, 'TOPIC'::text, 'MODULE'::text, 'LESSON'::text, 'COURSE'::text, 'SUBJECT'::text, 'PROGRAM'::text])));
ALTER TABLE lms.progress ADD CONSTRAINT ck_progress_status CHECK ((status = ANY (ARRAY['NOT_STARTED'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text])));
ALTER TABLE lms.progress ADD CONSTRAINT ck_progress_time_spent_sec CHECK ((time_spent_sec >= 0));
ALTER TABLE lms.progress ADD CONSTRAINT progress_pkey PRIMARY KEY (id);
ALTER TABLE lms.progress ADD CONSTRAINT uq_progress_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.progress_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  student_id uuid NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NOT NULL,
  event_type text NOT NULL,
  payload jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.progress_events ADD CONSTRAINT progress_events_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS lms.progress_events_default (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  student_id uuid NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NOT NULL,
  event_type text NOT NULL,
  payload jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.progress_events_default ADD CONSTRAINT progress_events_default_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS lms.progress_events_p202610 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  student_id uuid NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NOT NULL,
  event_type text NOT NULL,
  payload jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.progress_events_p202610 ADD CONSTRAINT progress_events_p202610_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS lms.progress_events_p202611 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  student_id uuid NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NOT NULL,
  event_type text NOT NULL,
  payload jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.progress_events_p202611 ADD CONSTRAINT progress_events_p202611_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS lms.progress_events_p202612 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  student_id uuid NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NOT NULL,
  event_type text NOT NULL,
  payload jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.progress_events_p202612 ADD CONSTRAINT progress_events_p202612_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS lms.progress_events_p202701 (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id(),
  student_id uuid NOT NULL,
  scope_type text NOT NULL,
  scope_id uuid NOT NULL,
  event_type text NOT NULL,
  payload jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.progress_events_p202701 ADD CONSTRAINT progress_events_p202701_pkey PRIMARY KEY (id, created_at);

CREATE TABLE IF NOT EXISTS lms.progress_milestones (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  milestone_code text NOT NULL,
  scope_type text,
  scope_id uuid,
  achieved_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.progress_milestones ADD CONSTRAINT progress_milestones_pkey PRIMARY KEY (id);
ALTER TABLE lms.progress_milestones ADD CONSTRAINT uq_progress_milestones_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.progress_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scope_type text NOT NULL,
  subject_id uuid,
  course_id uuid,
  criteria jsonb DEFAULT '{}'::jsonb NOT NULL,
  weight numeric(9,4),
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.progress_rules ADD CONSTRAINT progress_rules_pkey PRIMARY KEY (id);
ALTER TABLE lms.progress_rules ADD CONSTRAINT uq_progress_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resource_associations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  resource_id uuid NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  relation text DEFAULT 'REFERENCE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.resource_associations ADD CONSTRAINT ck_resource_associations_entity_type CHECK ((entity_type = ANY (ARRAY['COURSE'::text, 'SUBJECT'::text, 'TOPIC'::text, 'LESSON'::text, 'ASSIGNMENT'::text, 'PROGRAM'::text, 'RESOURCE'::text, 'TRAINING_COURSE'::text])));
ALTER TABLE lms.resource_associations ADD CONSTRAINT resource_associations_pkey PRIMARY KEY (id);
ALTER TABLE lms.resource_associations ADD CONSTRAINT uq_resource_associations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resource_categories (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  parent_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  sort_order integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.resource_categories ADD CONSTRAINT resource_categories_pkey PRIMARY KEY (id);
ALTER TABLE lms.resource_categories ADD CONSTRAINT uq_resource_categories_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resource_collection_items (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  collection_id uuid NOT NULL,
  resource_id uuid NOT NULL,
  sort_order integer DEFAULT 0 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.resource_collection_items ADD CONSTRAINT resource_collection_items_pkey PRIMARY KEY (id);
ALTER TABLE lms.resource_collection_items ADD CONSTRAINT uq_resource_collection_items_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resource_collections (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  owner_employee_id uuid,
  name text NOT NULL,
  description text,
  is_public boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.resource_collections ADD CONSTRAINT resource_collections_pkey PRIMARY KEY (id);
ALTER TABLE lms.resource_collections ADD CONSTRAINT uq_resource_collections_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resource_reviews (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  resource_id uuid NOT NULL,
  reviewer_person_id uuid NOT NULL,
  rating smallint NOT NULL,
  comment text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.resource_reviews ADD CONSTRAINT ck_resource_reviews_x1 CHECK (((rating >= 1) AND (rating <= 5)));
ALTER TABLE lms.resource_reviews ADD CONSTRAINT resource_reviews_pkey PRIMARY KEY (id);
ALTER TABLE lms.resource_reviews ADD CONSTRAINT uq_resource_reviews_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resource_usage_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  resource_id uuid NOT NULL,
  person_id uuid,
  event_type text NOT NULL,
  duration_sec integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.resource_usage_events ADD CONSTRAINT ck_resource_usage_events_event_type CHECK ((event_type = ANY (ARRAY['VIEW'::text, 'DOWNLOAD'::text, 'SHARE'::text, 'COMPLETE'::text])));
ALTER TABLE lms.resource_usage_events ADD CONSTRAINT resource_usage_events_pkey PRIMARY KEY (id);
ALTER TABLE lms.resource_usage_events ADD CONSTRAINT uq_resource_usage_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resources (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  category_id uuid,
  owner_employee_id uuid,
  title text NOT NULL,
  description text,
  resource_type text NOT NULL,
  document_id uuid,
  external_url text,
  language text,
  author text,
  license text,
  retain_until date,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  metadata jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.resources ADD CONSTRAINT ck_resources_resource_type CHECK ((resource_type = ANY (ARRAY['FILE'::text, 'VIDEO'::text, 'LINK'::text, 'EMBED'::text, 'BOOK'::text, 'PAPER'::text, 'DATASET'::text, 'SLIDES'::text])));
ALTER TABLE lms.resources ADD CONSTRAINT ck_resources_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'PUBLISHED'::text, 'ARCHIVED'::text])));
ALTER TABLE lms.resources ADD CONSTRAINT ck_resources_x1 CHECK (((document_id IS NOT NULL) OR (external_url IS NOT NULL)));
ALTER TABLE lms.resources ADD CONSTRAINT resources_pkey PRIMARY KEY (id);
ALTER TABLE lms.resources ADD CONSTRAINT uq_resources_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.resubmission_requests (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_assignment_id uuid NOT NULL,
  reason text,
  status text DEFAULT 'PENDING'::text NOT NULL,
  new_due_at timestamp with time zone,
  decided_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.resubmission_requests ADD CONSTRAINT ck_resubmission_requests_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'REJECTED'::text])));
ALTER TABLE lms.resubmission_requests ADD CONSTRAINT resubmission_requests_pkey PRIMARY KEY (id);
ALTER TABLE lms.resubmission_requests ADD CONSTRAINT uq_resubmission_requests_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.rubric_criteria (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  rubric_id uuid NOT NULL,
  seq integer DEFAULT 1 NOT NULL,
  name text NOT NULL,
  description text,
  max_points numeric(18,2) NOT NULL,
  weight numeric(9,4),
  outcome_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.rubric_criteria ADD CONSTRAINT ck_rubric_criteria_max_points CHECK ((max_points >= (0)::numeric));
ALTER TABLE lms.rubric_criteria ADD CONSTRAINT rubric_criteria_pkey PRIMARY KEY (id);
ALTER TABLE lms.rubric_criteria ADD CONSTRAINT uq_rubric_criteria_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.rubric_levels (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  criterion_id uuid NOT NULL,
  level_no smallint NOT NULL,
  label text NOT NULL,
  points numeric(18,2) NOT NULL,
  descriptor text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.rubric_levels ADD CONSTRAINT ck_rubric_levels_level_no CHECK ((level_no > 0));
ALTER TABLE lms.rubric_levels ADD CONSTRAINT ck_rubric_levels_points CHECK ((points >= (0)::numeric));
ALTER TABLE lms.rubric_levels ADD CONSTRAINT rubric_levels_pkey PRIMARY KEY (id);
ALTER TABLE lms.rubric_levels ADD CONSTRAINT uq_rubric_levels_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.rubrics (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  title text NOT NULL,
  description text,
  max_points numeric(18,2),
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  owner_employee_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.rubrics ADD CONSTRAINT ck_rubrics_max_points CHECK ((max_points >= (0)::numeric));
ALTER TABLE lms.rubrics ADD CONSTRAINT ck_rubrics_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE lms.rubrics ADD CONSTRAINT rubrics_pkey PRIMARY KEY (id);
ALTER TABLE lms.rubrics ADD CONSTRAINT uq_rubrics_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.session_interactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  person_id uuid,
  interaction_type text NOT NULL,
  payload jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.session_interactions ADD CONSTRAINT ck_session_interactions_interaction_type CHECK ((interaction_type = ANY (ARRAY['CHAT'::text, 'QUESTION'::text, 'HAND_RAISE'::text, 'REACTION'::text])));
ALTER TABLE lms.session_interactions ADD CONSTRAINT session_interactions_pkey PRIMARY KEY (id);
ALTER TABLE lms.session_interactions ADD CONSTRAINT uq_session_interactions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.session_participants (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  person_id uuid NOT NULL,
  role text DEFAULT 'ATTENDEE'::text NOT NULL,
  first_join_at timestamp with time zone,
  last_leave_at timestamp with time zone,
  total_minutes integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.session_participants ADD CONSTRAINT ck_session_participants_role CHECK ((role = ANY (ARRAY['HOST'::text, 'COHOST'::text, 'ATTENDEE'::text, 'OBSERVER'::text])));
ALTER TABLE lms.session_participants ADD CONSTRAINT ck_session_participants_total_minutes CHECK ((total_minutes >= 0));
ALTER TABLE lms.session_participants ADD CONSTRAINT session_participants_pkey PRIMARY KEY (id);
ALTER TABLE lms.session_participants ADD CONSTRAINT uq_session_participants_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.session_poll_responses (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  poll_id uuid NOT NULL,
  person_id uuid NOT NULL,
  answer jsonb NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.session_poll_responses ADD CONSTRAINT session_poll_responses_pkey PRIMARY KEY (id);
ALTER TABLE lms.session_poll_responses ADD CONSTRAINT uq_session_poll_responses_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.session_polls (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  question text NOT NULL,
  options jsonb DEFAULT '{}'::jsonb NOT NULL,
  opened_at timestamp with time zone,
  closed_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.session_polls ADD CONSTRAINT session_polls_pkey PRIMARY KEY (id);
ALTER TABLE lms.session_polls ADD CONSTRAINT uq_session_polls_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.session_recordings (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  document_id uuid,
  duration_sec integer,
  status text DEFAULT 'PROCESSING'::text NOT NULL,
  published boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.session_recordings ADD CONSTRAINT ck_session_recordings_status CHECK ((status = ANY (ARRAY['PROCESSING'::text, 'READY'::text, 'FAILED'::text, 'DELETED'::text])));
ALTER TABLE lms.session_recordings ADD CONSTRAINT session_recordings_pkey PRIMARY KEY (id);
ALTER TABLE lms.session_recordings ADD CONSTRAINT uq_session_recordings_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.session_resources (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  session_id uuid NOT NULL,
  resource_id uuid NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.session_resources ADD CONSTRAINT session_resources_pkey PRIMARY KEY (id);
ALTER TABLE lms.session_resources ADD CONSTRAINT uq_session_resources_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.student_assignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  assignment_id uuid NOT NULL,
  student_id uuid NOT NULL,
  status text DEFAULT 'ASSIGNED'::text NOT NULL,
  assigned_at timestamp with time zone DEFAULT now() NOT NULL,
  extended_due_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.student_assignments ADD CONSTRAINT ck_student_assignments_status CHECK ((status = ANY (ARRAY['ASSIGNED'::text, 'IN_PROGRESS'::text, 'SUBMITTED'::text, 'GRADED'::text, 'MISSED'::text, 'EXCUSED'::text])));
ALTER TABLE lms.student_assignments ADD CONSTRAINT student_assignments_pkey PRIMARY KEY (id);
ALTER TABLE lms.student_assignments ADD CONSTRAINT uq_student_assignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.submission_feedback (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  submission_id uuid NOT NULL,
  given_by uuid,
  body text NOT NULL,
  visibility text DEFAULT 'STUDENT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.submission_feedback ADD CONSTRAINT ck_submission_feedback_visibility CHECK ((visibility = ANY (ARRAY['STUDENT'::text, 'STAFF'::text])));
ALTER TABLE lms.submission_feedback ADD CONSTRAINT submission_feedback_pkey PRIMARY KEY (id);
ALTER TABLE lms.submission_feedback ADD CONSTRAINT uq_submission_feedback_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.submission_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  submission_id uuid NOT NULL,
  attempt_no integer NOT NULL,
  answers jsonb DEFAULT '{}'::jsonb,
  text_body text,
  submitted_at timestamp with time zone DEFAULT now() NOT NULL,
  similarity_score numeric(9,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE lms.submission_versions ADD CONSTRAINT ck_submission_versions_attempt_no CHECK ((attempt_no > 0));
ALTER TABLE lms.submission_versions ADD CONSTRAINT ck_submission_versions_similarity_score CHECK (((similarity_score >= (0)::numeric) AND (similarity_score <= (100)::numeric)));
ALTER TABLE lms.submission_versions ADD CONSTRAINT submission_versions_pkey PRIMARY KEY (id);
ALTER TABLE lms.submission_versions ADD CONSTRAINT uq_submission_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.submissions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_assignment_id uuid NOT NULL,
  student_id uuid NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  first_submitted_at timestamp with time zone,
  latest_submitted_at timestamp with time zone,
  attempt_count integer DEFAULT 0 NOT NULL,
  is_late boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.submissions ADD CONSTRAINT ck_submissions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'SUBMITTED'::text, 'RETURNED'::text, 'GRADED'::text])));
ALTER TABLE lms.submissions ADD CONSTRAINT submissions_pkey PRIMARY KEY (id);
ALTER TABLE lms.submissions ADD CONSTRAINT uq_submissions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.virtual_classrooms (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  section_id uuid,
  course_id uuid,
  owner_employee_id uuid,
  name text NOT NULL,
  provider text NOT NULL,
  settings jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT ck_virtual_classrooms_provider CHECK ((provider = ANY (ARRAY['ZOOM'::text, 'MEET'::text, 'TEAMS'::text, 'JITSI'::text, 'BIGBLUEBUTTON'::text, 'OTHER'::text])));
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT ck_virtual_classrooms_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ARCHIVED'::text])));
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT virtual_classrooms_pkey PRIMARY KEY (id);
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT uq_virtual_classrooms_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS lms.virtual_sessions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  classroom_id uuid NOT NULL,
  title text,
  scheduled_start timestamp with time zone NOT NULL,
  scheduled_end timestamp with time zone,
  actual_start timestamp with time zone,
  actual_end timestamp with time zone,
  provider_meeting_id text,
  join_url text,
  host_url text,
  attendance_session_id uuid,
  status text DEFAULT 'SCHEDULED'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE lms.virtual_sessions ADD CONSTRAINT ck_virtual_sessions_status CHECK ((status = ANY (ARRAY['SCHEDULED'::text, 'LIVE'::text, 'ENDED'::text, 'CANCELLED'::text])));
ALTER TABLE lms.virtual_sessions ADD CONSTRAINT virtual_sessions_pkey PRIMARY KEY (id);
ALTER TABLE lms.virtual_sessions ADD CONSTRAINT uq_virtual_sessions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS obe.alignments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  outcome_id uuid NOT NULL,
  target_type text NOT NULL,
  target_id uuid NOT NULL,
  weightage_pct numeric(9,4),
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE obe.alignments ADD CONSTRAINT ck_alignments_target_type CHECK ((target_type = ANY (ARRAY['ASSIGNMENT'::text, 'ASSESSMENT_COMPONENT'::text, 'QUESTION'::text, 'ACTIVITY'::text, 'LESSON_TOPIC'::text, 'EXAM_QUESTION'::text])));
ALTER TABLE obe.alignments ADD CONSTRAINT ck_alignments_weightage_pct CHECK (((weightage_pct >= (0)::numeric) AND (weightage_pct <= (100)::numeric)));
ALTER TABLE obe.alignments ADD CONSTRAINT alignments_pkey PRIMARY KEY (id);
ALTER TABLE obe.alignments ADD CONSTRAINT uq_alignments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS obe.attainment_evidence (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  outcome_id uuid NOT NULL,
  student_id uuid,
  section_id uuid,
  source_type text NOT NULL,
  source_id uuid,
  score numeric(18,2),
  max_score numeric(18,2),
  measure text DEFAULT 'DIRECT'::text NOT NULL,
  term_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT ck_attainment_evidence_measure CHECK ((measure = ANY (ARRAY['DIRECT'::text, 'INDIRECT'::text])));
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT attainment_evidence_pkey PRIMARY KEY (id);
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT uq_attainment_evidence_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS obe.attainment_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scope text NOT NULL,
  subject_id uuid,
  program_id uuid,
  direct_weight_pct numeric(9,4) DEFAULT 80 NOT NULL,
  indirect_weight_pct numeric(9,4) DEFAULT 20 NOT NULL,
  target_level smallint,
  effective_from date,
  effective_to date,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE obe.attainment_rules ADD CONSTRAINT ck_attainment_rules_direct_weight_pct CHECK (((direct_weight_pct >= (0)::numeric) AND (direct_weight_pct <= (100)::numeric)));
ALTER TABLE obe.attainment_rules ADD CONSTRAINT ck_attainment_rules_indirect_weight_pct CHECK (((indirect_weight_pct >= (0)::numeric) AND (indirect_weight_pct <= (100)::numeric)));
ALTER TABLE obe.attainment_rules ADD CONSTRAINT ck_attainment_rules_scope CHECK ((scope = ANY (ARRAY['COURSE'::text, 'PROGRAM'::text])));
ALTER TABLE obe.attainment_rules ADD CONSTRAINT ck_attainment_rules_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE obe.attainment_rules ADD CONSTRAINT ck_attainment_rules_x1 CHECK (((direct_weight_pct + indirect_weight_pct) = (100)::numeric));
ALTER TABLE obe.attainment_rules ADD CONSTRAINT attainment_rules_pkey PRIMARY KEY (id);
ALTER TABLE obe.attainment_rules ADD CONSTRAINT uq_attainment_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS obe.attainment_thresholds (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  rule_id uuid NOT NULL,
  level_no smallint NOT NULL,
  min_pct numeric(9,4) NOT NULL,
  label text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE obe.attainment_thresholds ADD CONSTRAINT ck_attainment_thresholds_level_no CHECK ((level_no > 0));
ALTER TABLE obe.attainment_thresholds ADD CONSTRAINT ck_attainment_thresholds_min_pct CHECK (((min_pct >= (0)::numeric) AND (min_pct <= (100)::numeric)));
ALTER TABLE obe.attainment_thresholds ADD CONSTRAINT attainment_thresholds_pkey PRIMARY KEY (id);
ALTER TABLE obe.attainment_thresholds ADD CONSTRAINT uq_attainment_thresholds_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS obe.attainments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  outcome_id uuid NOT NULL,
  term_id uuid,
  section_id uuid,
  rule_id uuid,
  direct_pct numeric(9,4),
  indirect_pct numeric(9,4),
  overall_pct numeric(9,4),
  level_no smallint,
  computed_at timestamp with time zone DEFAULT now() NOT NULL,
  is_frozen boolean DEFAULT false NOT NULL,
  frozen_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE obe.attainments ADD CONSTRAINT ck_attainments_direct_pct CHECK (((direct_pct >= (0)::numeric) AND (direct_pct <= (100)::numeric)));
ALTER TABLE obe.attainments ADD CONSTRAINT ck_attainments_indirect_pct CHECK (((indirect_pct >= (0)::numeric) AND (indirect_pct <= (100)::numeric)));
ALTER TABLE obe.attainments ADD CONSTRAINT ck_attainments_overall_pct CHECK (((overall_pct >= (0)::numeric) AND (overall_pct <= (100)::numeric)));
ALTER TABLE obe.attainments ADD CONSTRAINT attainments_pkey PRIMARY KEY (id);
ALTER TABLE obe.attainments ADD CONSTRAINT uq_attainments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS obe.outcome_mappings (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  from_outcome_id uuid NOT NULL,
  to_outcome_id uuid NOT NULL,
  correlation_level smallint NOT NULL,
  rationale text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE obe.outcome_mappings ADD CONSTRAINT ck_outcome_mappings_correlation_level CHECK ((correlation_level = ANY (ARRAY['1'::smallint, '2'::smallint, '3'::smallint])));
ALTER TABLE obe.outcome_mappings ADD CONSTRAINT ck_outcome_mappings_x1 CHECK ((from_outcome_id <> to_outcome_id));
ALTER TABLE obe.outcome_mappings ADD CONSTRAINT outcome_mappings_pkey PRIMARY KEY (id);
ALTER TABLE obe.outcome_mappings ADD CONSTRAINT uq_outcome_mappings_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS obe.outcomes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  outcome_type text NOT NULL,
  code text NOT NULL,
  statement text NOT NULL,
  program_id uuid,
  subject_id uuid,
  curriculum_version_id uuid,
  parent_outcome_id uuid,
  bloom_level text,
  sequence smallint,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE obe.outcomes ADD CONSTRAINT ck_outcomes_outcome_type CHECK ((outcome_type = ANY (ARRAY['PEO'::text, 'PO'::text, 'PSO'::text, 'CO'::text, 'TLO'::text])));
ALTER TABLE obe.outcomes ADD CONSTRAINT ck_outcomes_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE obe.outcomes ADD CONSTRAINT ck_outcomes_x1 CHECK ((((outcome_type = ANY (ARRAY['PEO'::text, 'PO'::text, 'PSO'::text])) AND (program_id IS NOT NULL)) OR ((outcome_type = ANY (ARRAY['CO'::text, 'TLO'::text])) AND (subject_id IS NOT NULL))));
ALTER TABLE obe.outcomes ADD CONSTRAINT ck_outcomes_x2 CHECK (((bloom_level IS NULL) OR (bloom_level = ANY (ARRAY['REMEMBER'::text, 'UNDERSTAND'::text, 'APPLY'::text, 'ANALYSE'::text, 'EVALUATE'::text, 'CREATE'::text]))));
ALTER TABLE obe.outcomes ADD CONSTRAINT outcomes_pkey PRIMARY KEY (id);
ALTER TABLE obe.outcomes ADD CONSTRAINT uq_outcomes_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.compensation_lines (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  compensation_id uuid NOT NULL,
  component_id uuid NOT NULL,
  amount numeric(18,2) NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.compensation_lines ADD CONSTRAINT ck_compensation_lines_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE pay.compensation_lines ADD CONSTRAINT compensation_lines_pkey PRIMARY KEY (id);
ALTER TABLE pay.compensation_lines ADD CONSTRAINT uq_compensation_lines_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.compensation_revisions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  from_compensation_id uuid,
  to_compensation_id uuid,
  revision_type text NOT NULL,
  effective_from date NOT NULL,
  reason text,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT ck_compensation_revisions_revision_type CHECK ((revision_type = ANY (ARRAY['INCREMENT'::text, 'PROMOTION'::text, 'CORRECTION'::text, 'DEMOTION'::text])));
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT compensation_revisions_pkey PRIMARY KEY (id);
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT uq_compensation_revisions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.employee_compensations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  structure_version_id uuid,
  payroll_group_id uuid,
  effective_from date NOT NULL,
  effective_to date,
  annual_ctc numeric(18,2),
  monthly_gross numeric(18,2),
  bank_account_ciphertext bytea,
  bank_last4 text,
  ifsc text,
  pf_applicable boolean DEFAULT true NOT NULL,
  esi_applicable boolean DEFAULT false NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.employee_compensations ADD CONSTRAINT ck_employee_compensations_annual_ctc CHECK ((annual_ctc >= (0)::numeric));
ALTER TABLE pay.employee_compensations ADD CONSTRAINT ck_employee_compensations_monthly_gross CHECK ((monthly_gross >= (0)::numeric));
ALTER TABLE pay.employee_compensations ADD CONSTRAINT ck_employee_compensations_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUPERSEDED'::text, 'SUSPENDED'::text])));
ALTER TABLE pay.employee_compensations ADD CONSTRAINT ck_employee_compensations_x1 CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
ALTER TABLE pay.employee_compensations ADD CONSTRAINT employee_compensations_pkey PRIMARY KEY (id);
ALTER TABLE pay.employee_compensations ADD CONSTRAINT uq_employee_compensations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.employee_loans (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  employee_id uuid NOT NULL,
  loan_type text NOT NULL,
  principal numeric(18,2) NOT NULL,
  interest_pct numeric(9,4),
  installments integer NOT NULL,
  emi_amount numeric(18,2),
  start_run_period_id uuid,
  outstanding numeric(18,2) NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.employee_loans ADD CONSTRAINT ck_employee_loans_installments CHECK ((installments > 0));
ALTER TABLE pay.employee_loans ADD CONSTRAINT ck_employee_loans_loan_type CHECK ((loan_type = ANY (ARRAY['LOAN'::text, 'ADVANCE'::text])));
ALTER TABLE pay.employee_loans ADD CONSTRAINT ck_employee_loans_outstanding CHECK ((outstanding >= (0)::numeric));
ALTER TABLE pay.employee_loans ADD CONSTRAINT ck_employee_loans_principal CHECK ((principal > (0)::numeric));
ALTER TABLE pay.employee_loans ADD CONSTRAINT ck_employee_loans_status CHECK ((status = ANY (ARRAY['REQUESTED'::text, 'APPROVED'::text, 'ACTIVE'::text, 'CLOSED'::text, 'CANCELLED'::text])));
ALTER TABLE pay.employee_loans ADD CONSTRAINT employee_loans_pkey PRIMARY KEY (id);
ALTER TABLE pay.employee_loans ADD CONSTRAINT uq_employee_loans_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.loan_installments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  loan_id uuid NOT NULL,
  installment_no integer NOT NULL,
  due_period_id uuid,
  amount numeric(18,2) NOT NULL,
  status text DEFAULT 'DUE'::text NOT NULL,
  payslip_line_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.loan_installments ADD CONSTRAINT ck_loan_installments_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE pay.loan_installments ADD CONSTRAINT ck_loan_installments_installment_no CHECK ((installment_no > 0));
ALTER TABLE pay.loan_installments ADD CONSTRAINT ck_loan_installments_status CHECK ((status = ANY (ARRAY['DUE'::text, 'DEDUCTED'::text, 'SKIPPED'::text, 'WAIVED'::text])));
ALTER TABLE pay.loan_installments ADD CONSTRAINT loan_installments_pkey PRIMARY KEY (id);
ALTER TABLE pay.loan_installments ADD CONSTRAINT uq_loan_installments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.payroll_groups (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  pay_frequency text DEFAULT 'MONTHLY'::text NOT NULL,
  pay_day smallint,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.payroll_groups ADD CONSTRAINT ck_payroll_groups_pay_frequency CHECK ((pay_frequency = ANY (ARRAY['MONTHLY'::text, 'WEEKLY'::text, 'BIWEEKLY'::text])));
ALTER TABLE pay.payroll_groups ADD CONSTRAINT ck_payroll_groups_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'INACTIVE'::text])));
ALTER TABLE pay.payroll_groups ADD CONSTRAINT payroll_groups_pkey PRIMARY KEY (id);
ALTER TABLE pay.payroll_groups ADD CONSTRAINT uq_payroll_groups_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.payroll_inputs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  run_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  input_type text NOT NULL,
  component_id uuid,
  quantity numeric(18,4),
  amount numeric(18,2),
  source_ref uuid,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.payroll_inputs ADD CONSTRAINT ck_payroll_inputs_input_type CHECK ((input_type = ANY (ARRAY['ATTENDANCE'::text, 'LEAVE'::text, 'OVERTIME'::text, 'LOP'::text, 'VARIABLE'::text, 'ADJUSTMENT'::text])));
ALTER TABLE pay.payroll_inputs ADD CONSTRAINT payroll_inputs_pkey PRIMARY KEY (id);
ALTER TABLE pay.payroll_inputs ADD CONSTRAINT uq_payroll_inputs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.payroll_periods (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payroll_group_id uuid,
  period_month smallint NOT NULL,
  period_year smallint NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  pay_date date,
  status text DEFAULT 'OPEN'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.payroll_periods ADD CONSTRAINT ck_payroll_periods_period_month CHECK ((period_month >= 0));
ALTER TABLE pay.payroll_periods ADD CONSTRAINT ck_payroll_periods_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'PROCESSING'::text, 'CLOSED'::text, 'PAID'::text])));
ALTER TABLE pay.payroll_periods ADD CONSTRAINT ck_payroll_periods_x1 CHECK (((period_month >= 1) AND (period_month <= 12)));
ALTER TABLE pay.payroll_periods ADD CONSTRAINT payroll_periods_pkey PRIMARY KEY (id);
ALTER TABLE pay.payroll_periods ADD CONSTRAINT uq_payroll_periods_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.payroll_runs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  period_id uuid NOT NULL,
  run_no text NOT NULL,
  run_type text DEFAULT 'REGULAR'::text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  employee_count integer,
  total_gross numeric(18,2),
  total_deductions numeric(18,2),
  total_net numeric(18,2),
  workflow_instance_id uuid,
  finalized_by uuid,
  finalized_at timestamp with time zone,
  job_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.payroll_runs ADD CONSTRAINT ck_payroll_runs_run_type CHECK ((run_type = ANY (ARRAY['REGULAR'::text, 'SUPPLEMENTARY'::text, 'OFF_CYCLE'::text, 'FNF'::text])));
ALTER TABLE pay.payroll_runs ADD CONSTRAINT ck_payroll_runs_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'CALCULATING'::text, 'CALCULATED'::text, 'PENDING_APPROVAL'::text, 'APPROVED'::text, 'FINALIZED'::text, 'PAID'::text, 'CANCELLED'::text])));
ALTER TABLE pay.payroll_runs ADD CONSTRAINT payroll_runs_pkey PRIMARY KEY (id);
ALTER TABLE pay.payroll_runs ADD CONSTRAINT uq_payroll_runs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.payslip_lines (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payslip_id uuid NOT NULL,
  component_id uuid NOT NULL,
  line_type text NOT NULL,
  amount numeric(18,2) NOT NULL,
  is_arrear boolean DEFAULT false NOT NULL,
  note text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.payslip_lines ADD CONSTRAINT ck_payslip_lines_line_type CHECK ((line_type = ANY (ARRAY['EARNING'::text, 'DEDUCTION'::text, 'EMPLOYER_CONTRIBUTION'::text, 'REIMBURSEMENT'::text])));
ALTER TABLE pay.payslip_lines ADD CONSTRAINT payslip_lines_pkey PRIMARY KEY (id);
ALTER TABLE pay.payslip_lines ADD CONSTRAINT uq_payslip_lines_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.payslips (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  run_id uuid NOT NULL,
  employee_id uuid NOT NULL,
  compensation_id uuid,
  payslip_no text NOT NULL,
  working_days numeric(9,4),
  paid_days numeric(9,4),
  lop_days numeric(9,4),
  gross_earnings numeric(18,2) DEFAULT 0 NOT NULL,
  total_deductions numeric(18,2) DEFAULT 0 NOT NULL,
  net_pay numeric(18,2) DEFAULT 0 NOT NULL,
  employer_contribution numeric(18,2) DEFAULT 0 NOT NULL,
  status text DEFAULT 'CALCULATED'::text NOT NULL,
  document_id uuid,
  published_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.payslips ADD CONSTRAINT ck_payslips_status CHECK ((status = ANY (ARRAY['CALCULATED'::text, 'APPROVED'::text, 'PUBLISHED'::text, 'PAID'::text, 'HELD'::text])));
ALTER TABLE pay.payslips ADD CONSTRAINT payslips_pkey PRIMARY KEY (id);
ALTER TABLE pay.payslips ADD CONSTRAINT uq_payslips_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.run_adjustments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  run_id uuid,
  employee_id uuid NOT NULL,
  adjustment_type text NOT NULL,
  component_id uuid,
  amount numeric(18,2) NOT NULL,
  reason text,
  status text DEFAULT 'PENDING'::text NOT NULL,
  workflow_instance_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.run_adjustments ADD CONSTRAINT ck_run_adjustments_adjustment_type CHECK ((adjustment_type = ANY (ARRAY['ARREAR'::text, 'BONUS'::text, 'RECOVERY'::text, 'INCENTIVE'::text, 'CORRECTION'::text, 'OTHER'::text])));
ALTER TABLE pay.run_adjustments ADD CONSTRAINT ck_run_adjustments_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'APPROVED'::text, 'APPLIED'::text, 'REJECTED'::text])));
ALTER TABLE pay.run_adjustments ADD CONSTRAINT run_adjustments_pkey PRIMARY KEY (id);
ALTER TABLE pay.run_adjustments ADD CONSTRAINT uq_run_adjustments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.salary_components (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  component_type text NOT NULL,
  calc_type text DEFAULT 'FIXED'::text NOT NULL,
  is_taxable boolean DEFAULT true NOT NULL,
  is_statutory boolean DEFAULT false NOT NULL,
  ledger_account_id uuid,
  display_order integer DEFAULT 0 NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.salary_components ADD CONSTRAINT ck_salary_components_calc_type CHECK ((calc_type = ANY (ARRAY['FIXED'::text, 'PERCENT_OF'::text, 'FORMULA'::text, 'SLAB'::text])));
ALTER TABLE pay.salary_components ADD CONSTRAINT ck_salary_components_component_type CHECK ((component_type = ANY (ARRAY['EARNING'::text, 'DEDUCTION'::text, 'EMPLOYER_CONTRIBUTION'::text, 'REIMBURSEMENT'::text])));
ALTER TABLE pay.salary_components ADD CONSTRAINT salary_components_pkey PRIMARY KEY (id);
ALTER TABLE pay.salary_components ADD CONSTRAINT uq_salary_components_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.salary_structure_versions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_id uuid NOT NULL,
  version_no integer NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.salary_structure_versions ADD CONSTRAINT ck_salary_structure_versions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'APPROVED'::text, 'ACTIVE'::text, 'SUPERSEDED'::text])));
ALTER TABLE pay.salary_structure_versions ADD CONSTRAINT ck_salary_structure_versions_version_no CHECK ((version_no > 0));
ALTER TABLE pay.salary_structure_versions ADD CONSTRAINT salary_structure_versions_pkey PRIMARY KEY (id);
ALTER TABLE pay.salary_structure_versions ADD CONSTRAINT uq_salary_structure_versions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.salary_structures (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  payroll_group_id uuid,
  code text NOT NULL,
  name text NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.salary_structures ADD CONSTRAINT ck_salary_structures_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE pay.salary_structures ADD CONSTRAINT salary_structures_pkey PRIMARY KEY (id);
ALTER TABLE pay.salary_structures ADD CONSTRAINT uq_salary_structures_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.statutory_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  rule_type text NOT NULL,
  jurisdiction_id uuid,
  params jsonb DEFAULT '{}'::jsonb NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.statutory_rules ADD CONSTRAINT ck_statutory_rules_rule_type CHECK ((rule_type = ANY (ARRAY['PF'::text, 'ESI'::text, 'PT'::text, 'TDS'::text, 'LWF'::text, 'GRATUITY'::text, 'BONUS'::text])));
ALTER TABLE pay.statutory_rules ADD CONSTRAINT ck_statutory_rules_x1 CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
ALTER TABLE pay.statutory_rules ADD CONSTRAINT statutory_rules_pkey PRIMARY KEY (id);
ALTER TABLE pay.statutory_rules ADD CONSTRAINT uq_statutory_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS pay.structure_components (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  structure_version_id uuid NOT NULL,
  component_id uuid NOT NULL,
  amount numeric(18,2),
  percent_of_component_id uuid,
  percentage numeric(9,4),
  formula text,
  min_amount numeric(18,2),
  max_amount numeric(18,2),
  sequence smallint DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE pay.structure_components ADD CONSTRAINT structure_components_pkey PRIMARY KEY (id);
ALTER TABLE pay.structure_components ADD CONSTRAINT uq_structure_components_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS plat.alerts (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_ref_id uuid,
  severity text NOT NULL,
  title text NOT NULL,
  detail text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  acknowledged_by uuid,
  resolved_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.alerts ADD CONSTRAINT ck_alerts_severity CHECK ((severity = ANY (ARRAY['INFO'::text, 'WARNING'::text, 'ERROR'::text, 'CRITICAL'::text])));
ALTER TABLE plat.alerts ADD CONSTRAINT ck_alerts_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ACKNOWLEDGED'::text, 'RESOLVED'::text])));
ALTER TABLE plat.alerts ADD CONSTRAINT alerts_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS plat.gateway_transactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  invoice_id uuid,
  gateway text NOT NULL,
  gateway_ref text,
  amount numeric(18,2) NOT NULL,
  currency_code character(3) DEFAULT 'INR'::bpchar NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  raw_response jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.gateway_transactions ADD CONSTRAINT ck_gateway_transactions_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE plat.gateway_transactions ADD CONSTRAINT ck_gateway_transactions_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'SUCCESS'::text, 'FAILED'::text, 'REFUNDED'::text])));
ALTER TABLE plat.gateway_transactions ADD CONSTRAINT gateway_transactions_pkey PRIMARY KEY (id);
ALTER TABLE plat.gateway_transactions ADD CONSTRAINT uq_gateway_transactions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS plat.invoice_lines (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  invoice_id uuid NOT NULL,
  description text NOT NULL,
  quantity numeric(18,4) DEFAULT 1 NOT NULL,
  unit_price numeric(18,2) NOT NULL,
  amount numeric(18,2) NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.invoice_lines ADD CONSTRAINT ck_invoice_lines_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE plat.invoice_lines ADD CONSTRAINT ck_invoice_lines_unit_price CHECK ((unit_price >= (0)::numeric));
ALTER TABLE plat.invoice_lines ADD CONSTRAINT invoice_lines_pkey PRIMARY KEY (id);
ALTER TABLE plat.invoice_lines ADD CONSTRAINT uq_invoice_lines_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS plat.invoices (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  subscription_id uuid NOT NULL,
  invoice_number text NOT NULL,
  issued_on date,
  due_date date,
  amount numeric(18,2) NOT NULL,
  currency_code character(3) DEFAULT 'INR'::bpchar NOT NULL,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  paid_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.invoices ADD CONSTRAINT ck_invoices_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE plat.invoices ADD CONSTRAINT ck_invoices_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ISSUED'::text, 'PAID'::text, 'VOID'::text, 'UNCOLLECTIBLE'::text])));
ALTER TABLE plat.invoices ADD CONSTRAINT invoices_pkey PRIMARY KEY (id);
ALTER TABLE plat.invoices ADD CONSTRAINT uq_invoices_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS plat.plan_entitlements (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  plan_id uuid NOT NULL,
  entitlement_key text NOT NULL,
  limit_value bigint,
  is_enabled boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.plan_entitlements ADD CONSTRAINT plan_entitlements_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS plat.platform_admins (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  user_id uuid NOT NULL,
  full_name text,
  role_code text NOT NULL,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.platform_admins ADD CONSTRAINT ck_platform_admins_role_code CHECK ((role_code = ANY (ARRAY['SUPER_ADMIN'::text, 'SYSTEM_ADMIN'::text, 'SUPPORT'::text, 'BILLING'::text, 'AUDITOR'::text])));
ALTER TABLE plat.platform_admins ADD CONSTRAINT ck_platform_admins_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'LOCKED'::text, 'INACTIVE'::text])));
ALTER TABLE plat.platform_admins ADD CONSTRAINT platform_admins_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS plat.service_health (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  service_name text NOT NULL,
  status text NOT NULL,
  latency_ms integer,
  last_heartbeat timestamp with time zone NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.service_health ADD CONSTRAINT ck_service_health_status CHECK ((status = ANY (ARRAY['HEALTHY'::text, 'DEGRADED'::text, 'UNHEALTHY'::text])));
ALTER TABLE plat.service_health ADD CONSTRAINT service_health_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS plat.subscription_plans (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  plan_code text NOT NULL,
  name text NOT NULL,
  billing_cycle text NOT NULL,
  price numeric(18,2) NOT NULL,
  currency_code character(3) DEFAULT 'INR'::bpchar NOT NULL,
  is_published boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.subscription_plans ADD CONSTRAINT ck_subscription_plans_billing_cycle CHECK ((billing_cycle = ANY (ARRAY['MONTHLY'::text, 'QUARTERLY'::text, 'ANNUALLY'::text])));
ALTER TABLE plat.subscription_plans ADD CONSTRAINT ck_subscription_plans_price CHECK ((price >= (0)::numeric));
ALTER TABLE plat.subscription_plans ADD CONSTRAINT subscription_plans_pkey PRIMARY KEY (id);

CREATE TABLE IF NOT EXISTS plat.subscriptions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  plan_id uuid NOT NULL,
  start_date date NOT NULL,
  end_date date,
  auto_renew boolean DEFAULT true NOT NULL,
  status text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.subscriptions ADD CONSTRAINT ck_subscriptions_status CHECK ((status = ANY (ARRAY['TRIAL'::text, 'ACTIVE'::text, 'PAST_DUE'::text, 'SUSPENDED'::text, 'CANCELLED'::text])));
ALTER TABLE plat.subscriptions ADD CONSTRAINT ck_subscriptions_x1 CHECK (((end_date IS NULL) OR (end_date >= start_date)));
ALTER TABLE plat.subscriptions ADD CONSTRAINT subscriptions_pkey PRIMARY KEY (id);
ALTER TABLE plat.subscriptions ADD CONSTRAINT uq_subscriptions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS plat.tenant_provisioning (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  workflow_instance_id uuid,
  current_step text NOT NULL,
  attempt integer DEFAULT 1 NOT NULL,
  status text DEFAULT 'REQUESTED'::text NOT NULL,
  idempotency_key text,
  requested_by uuid,
  failure_reason text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.tenant_provisioning ADD CONSTRAINT ck_tenant_provisioning_status CHECK ((status = ANY (ARRAY['REQUESTED'::text, 'VALIDATED'::text, 'PROVISIONING'::text, 'COMPLETED'::text, 'FAILED'::text])));
ALTER TABLE plat.tenant_provisioning ADD CONSTRAINT tenant_provisioning_pkey PRIMARY KEY (id);
ALTER TABLE plat.tenant_provisioning ADD CONSTRAINT uq_tenant_provisioning_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS plat.usage_metrics (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  metric_name text NOT NULL,
  dimension text,
  metric_value numeric(18,4) NOT NULL,
  recorded_on date NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE plat.usage_metrics ADD CONSTRAINT ck_usage_metrics_metric_value CHECK ((metric_value >= (0)::numeric));
ALTER TABLE plat.usage_metrics ADD CONSTRAINT usage_metrics_pkey PRIMARY KEY (id);
ALTER TABLE plat.usage_metrics ADD CONSTRAINT uq_usage_metrics_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.dashboard_widgets (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  dashboard_id uuid NOT NULL,
  widget_type text NOT NULL,
  title text,
  kpi_id uuid,
  report_definition_id uuid,
  config jsonb DEFAULT '{}'::jsonb,
  position jsonb DEFAULT '{}'::jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE rpt.dashboard_widgets ADD CONSTRAINT dashboard_widgets_pkey PRIMARY KEY (id);
ALTER TABLE rpt.dashboard_widgets ADD CONSTRAINT uq_dashboard_widgets_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.dashboards (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  module text NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  layout jsonb DEFAULT '{}'::jsonb,
  owner_role_id uuid,
  is_default boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE rpt.dashboards ADD CONSTRAINT dashboards_pkey PRIMARY KEY (id);
ALTER TABLE rpt.dashboards ADD CONSTRAINT uq_dashboards_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.kpi_definitions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  module text NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  unit text,
  formula text,
  source_view text,
  target_value numeric(18,2),
  threshold_warn numeric(18,2),
  threshold_crit numeric(18,2),
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE rpt.kpi_definitions ADD CONSTRAINT kpi_definitions_pkey PRIMARY KEY (id);
ALTER TABLE rpt.kpi_definitions ADD CONSTRAINT uq_kpi_definitions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.kpi_values (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  kpi_id uuid NOT NULL,
  as_of date NOT NULL,
  dimension jsonb DEFAULT '{}'::jsonb,
  value numeric(18,2),
  computed_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE rpt.kpi_values ADD CONSTRAINT kpi_values_pkey PRIMARY KEY (id);
ALTER TABLE rpt.kpi_values ADD CONSTRAINT uq_kpi_values_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.refresh_status (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  view_name text NOT NULL,
  last_refreshed_at timestamp with time zone,
  duration_ms integer,
  status text DEFAULT 'OK'::text NOT NULL,
  error text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE rpt.refresh_status ADD CONSTRAINT ck_refresh_status_status CHECK ((status = ANY (ARRAY['OK'::text, 'RUNNING'::text, 'FAILED'::text])));
ALTER TABLE rpt.refresh_status ADD CONSTRAINT refresh_status_pkey PRIMARY KEY (id);
ALTER TABLE rpt.refresh_status ADD CONSTRAINT uq_refresh_status_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.report_definitions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  module text NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  report_type text,
  query_ref text,
  default_params jsonb DEFAULT '{}'::jsonb,
  output_formats text[] DEFAULT '{PDF,XLSX,CSV}'::text[],
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  current_version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE rpt.report_definitions ADD CONSTRAINT ck_report_definitions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE rpt.report_definitions ADD CONSTRAINT report_definitions_pkey PRIMARY KEY (id);
ALTER TABLE rpt.report_definitions ADD CONSTRAINT uq_report_definitions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.report_executions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  report_definition_id uuid NOT NULL,
  requested_by uuid,
  params jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'QUEUED'::text NOT NULL,
  document_id uuid,
  started_at timestamp with time zone,
  finished_at timestamp with time zone,
  row_count bigint,
  error text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE rpt.report_executions ADD CONSTRAINT ck_report_executions_status CHECK ((status = ANY (ARRAY['QUEUED'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'EXPIRED'::text])));
ALTER TABLE rpt.report_executions ADD CONSTRAINT report_executions_pkey PRIMARY KEY (id);
ALTER TABLE rpt.report_executions ADD CONSTRAINT uq_report_executions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.report_schedules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  report_definition_id uuid NOT NULL,
  cron_expression text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb,
  recipients_group_id uuid,
  recipient_role_ids uuid[],
  is_active boolean DEFAULT true NOT NULL,
  next_run_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE rpt.report_schedules ADD CONSTRAINT report_schedules_pkey PRIMARY KEY (id);
ALTER TABLE rpt.report_schedules ADD CONSTRAINT uq_report_schedules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS rpt.snapshots (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  module text NOT NULL,
  snapshot_type text NOT NULL,
  as_of date NOT NULL,
  scope jsonb DEFAULT '{}'::jsonb,
  data jsonb DEFAULT '{}'::jsonb NOT NULL,
  is_frozen boolean DEFAULT false NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE rpt.snapshots ADD CONSTRAINT snapshots_pkey PRIMARY KEY (id);
ALTER TABLE rpt.snapshots ADD CONSTRAINT uq_snapshots_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS stu.guardianships (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  student_id uuid NOT NULL,
  guardian_person_id uuid NOT NULL,
  relationship text NOT NULL,
  is_primary boolean DEFAULT false NOT NULL,
  can_view_academics boolean DEFAULT true NOT NULL,
  can_view_fees boolean DEFAULT true NOT NULL,
  can_pay_fees boolean DEFAULT false NOT NULL,
  valid_from date,
  valid_to date,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE stu.guardianships ADD CONSTRAINT ck_guardianships_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'SUSPENDED'::text, 'ENDED'::text])));
ALTER TABLE stu.guardianships ADD CONSTRAINT guardianships_pkey PRIMARY KEY (id);
ALTER TABLE stu.guardianships ADD CONSTRAINT uq_guardianships_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS stu.students (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  person_id uuid NOT NULL,
  college_id uuid NOT NULL,
  program_id uuid,
  enrollment_no text NOT NULL,
  admission_no text,
  admission_date date,
  admission_academic_year_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE stu.students ADD CONSTRAINT ck_students_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'ALUMNI'::text, 'SUSPENDED'::text, 'WITHDRAWN'::text, 'DETAINED'::text, 'GRADUATED'::text])));
ALTER TABLE stu.students ADD CONSTRAINT students_pkey PRIMARY KEY (id);
ALTER TABLE stu.students ADD CONSTRAINT uq_students_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.data_quality_issues (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  job_id uuid,
  import_row_id uuid,
  severity text NOT NULL,
  rule_name text NOT NULL,
  message text,
  resolved_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE sys.data_quality_issues ADD CONSTRAINT ck_data_quality_issues_severity CHECK ((severity = ANY (ARRAY['WARNING'::text, 'ERROR'::text])));
ALTER TABLE sys.data_quality_issues ADD CONSTRAINT data_quality_issues_pkey PRIMARY KEY (id);
ALTER TABLE sys.data_quality_issues ADD CONSTRAINT uq_data_quality_issues_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.dead_letter_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  source_service text NOT NULL,
  original_event_id text,
  failure_reason text NOT NULL,
  payload jsonb,
  status text DEFAULT 'FAILED'::text NOT NULL,
  replayed_at timestamp with time zone,
  expires_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE sys.dead_letter_events ADD CONSTRAINT ck_dead_letter_events_status CHECK ((status = ANY (ARRAY['FAILED'::text, 'REPLAYED'::text, 'DISCARDED'::text])));
ALTER TABLE sys.dead_letter_events ADD CONSTRAINT dead_letter_events_pkey PRIMARY KEY (id);
ALTER TABLE sys.dead_letter_events ADD CONSTRAINT uq_dead_letter_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.entity_locks (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  entity_type text NOT NULL,
  entity_id uuid NOT NULL,
  lock_type text DEFAULT 'EXCLUSIVE'::text NOT NULL,
  holder_id uuid,
  expires_at timestamp with time zone NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE sys.entity_locks ADD CONSTRAINT entity_locks_pkey PRIMARY KEY (id);
ALTER TABLE sys.entity_locks ADD CONSTRAINT uq_entity_locks_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.export_jobs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  entity_type text NOT NULL,
  format text DEFAULT 'CSV'::text NOT NULL,
  status text DEFAULT 'REQUESTED'::text NOT NULL,
  requested_by uuid,
  result_document_id uuid,
  expires_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE sys.export_jobs ADD CONSTRAINT ck_export_jobs_format CHECK ((format = ANY (ARRAY['CSV'::text, 'XLSX'::text, 'PDF'::text, 'JSON'::text])));
ALTER TABLE sys.export_jobs ADD CONSTRAINT ck_export_jobs_status CHECK ((status = ANY (ARRAY['REQUESTED'::text, 'PROCESSING'::text, 'COMPLETED'::text, 'FAILED'::text])));
ALTER TABLE sys.export_jobs ADD CONSTRAINT export_jobs_pkey PRIMARY KEY (id);
ALTER TABLE sys.export_jobs ADD CONSTRAINT uq_export_jobs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.id_map (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  source_collection text NOT NULL,
  mongo_id text NOT NULL,
  target_table text NOT NULL,
  target_id uuid NOT NULL,
  migrated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE sys.id_map ADD CONSTRAINT id_map_pkey PRIMARY KEY (id);
ALTER TABLE sys.id_map ADD CONSTRAINT uq_id_map_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.idempotency_keys (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  scope text NOT NULL,
  idempotency_key text NOT NULL,
  request_hash text,
  response jsonb,
  expires_at timestamp with time zone NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE sys.idempotency_keys ADD CONSTRAINT idempotency_keys_pkey PRIMARY KEY (id);
ALTER TABLE sys.idempotency_keys ADD CONSTRAINT uq_idempotency_keys_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.import_jobs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  college_id uuid,
  entity_type text NOT NULL,
  mode text DEFAULT 'INSERT'::text NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  total_rows integer DEFAULT 0 NOT NULL,
  processed_rows integer DEFAULT 0 NOT NULL,
  failed_rows integer DEFAULT 0 NOT NULL,
  source_document_id uuid,
  started_at timestamp with time zone,
  finished_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE sys.import_jobs ADD CONSTRAINT ck_import_jobs_entity_type CHECK ((entity_type = ANY (ARRAY['STUDENT'::text, 'FACULTY'::text, 'DEPARTMENT'::text, 'COURSE'::text, 'OTHER'::text])));
ALTER TABLE sys.import_jobs ADD CONSTRAINT ck_import_jobs_mode CHECK ((mode = ANY (ARRAY['INSERT'::text, 'UPSERT'::text, 'REPLACE'::text])));
ALTER TABLE sys.import_jobs ADD CONSTRAINT ck_import_jobs_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'PROCESSING'::text, 'COMPLETED'::text, 'FAILED'::text])));
ALTER TABLE sys.import_jobs ADD CONSTRAINT import_jobs_pkey PRIMARY KEY (id);
ALTER TABLE sys.import_jobs ADD CONSTRAINT uq_import_jobs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.import_rows (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  job_id uuid NOT NULL,
  row_number integer NOT NULL,
  status text DEFAULT 'PENDING'::text NOT NULL,
  raw_data jsonb,
  errors jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE sys.import_rows ADD CONSTRAINT ck_import_rows_row_number CHECK ((row_number > 0));
ALTER TABLE sys.import_rows ADD CONSTRAINT ck_import_rows_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'SUCCESS'::text, 'VALIDATION_FAILED'::text, 'SYSTEM_ERROR'::text])));
ALTER TABLE sys.import_rows ADD CONSTRAINT import_rows_pkey PRIMARY KEY (id);
ALTER TABLE sys.import_rows ADD CONSTRAINT uq_import_rows_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.inbox_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  source_service text NOT NULL,
  event_id text NOT NULL,
  event_type text NOT NULL,
  payload jsonb,
  status text DEFAULT 'RECEIVED'::text NOT NULL,
  processed_at timestamp with time zone,
  error text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE sys.inbox_events ADD CONSTRAINT ck_inbox_events_status CHECK ((status = ANY (ARRAY['RECEIVED'::text, 'PROCESSED'::text, 'FAILED'::text])));
ALTER TABLE sys.inbox_events ADD CONSTRAINT inbox_events_pkey PRIMARY KEY (id);
ALTER TABLE sys.inbox_events ADD CONSTRAINT uq_inbox_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.jobs (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  module text NOT NULL,
  job_type text NOT NULL,
  entity_type text,
  entity_id uuid,
  status text DEFAULT 'QUEUED'::text NOT NULL,
  params jsonb DEFAULT '{}'::jsonb,
  result jsonb,
  progress_pct smallint,
  error text,
  scheduled_for timestamp with time zone,
  started_at timestamp with time zone,
  finished_at timestamp with time zone,
  requested_by uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE sys.jobs ADD CONSTRAINT ck_jobs_progress_pct CHECK (((progress_pct >= 0) AND (progress_pct <= 100)));
ALTER TABLE sys.jobs ADD CONSTRAINT ck_jobs_status CHECK ((status = ANY (ARRAY['QUEUED'::text, 'RUNNING'::text, 'COMPLETED'::text, 'FAILED'::text, 'CANCELLED'::text])));
ALTER TABLE sys.jobs ADD CONSTRAINT jobs_pkey PRIMARY KEY (id);
ALTER TABLE sys.jobs ADD CONSTRAINT uq_jobs_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS sys.outbox_events (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  service text NOT NULL,
  aggregate_type text NOT NULL,
  aggregate_id uuid,
  event_type text NOT NULL,
  payload jsonb DEFAULT '{}'::jsonb NOT NULL,
  headers jsonb DEFAULT '{}'::jsonb,
  status text DEFAULT 'PENDING'::text NOT NULL,
  attempts integer DEFAULT 0 NOT NULL,
  next_attempt_at timestamp with time zone,
  dispatched_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL
);
ALTER TABLE sys.outbox_events ADD CONSTRAINT ck_outbox_events_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'DISPATCHED'::text, 'FAILED'::text])));
ALTER TABLE sys.outbox_events ADD CONSTRAINT outbox_events_pkey PRIMARY KEY (id);
ALTER TABLE sys.outbox_events ADD CONSTRAINT uq_outbox_events_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_codes (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  tax_type_id uuid NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  hsn_sac text,
  is_exempt boolean DEFAULT false NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_codes ADD CONSTRAINT tax_codes_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_codes ADD CONSTRAINT uq_tax_codes_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_exemptions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  tax_code_id uuid,
  person_id uuid,
  vendor_id uuid,
  certificate_no text,
  valid_from date,
  valid_to date,
  document_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_exemptions ADD CONSTRAINT tax_exemptions_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_exemptions ADD CONSTRAINT uq_tax_exemptions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_filings (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  period_id uuid NOT NULL,
  return_type text NOT NULL,
  acknowledgement_no text,
  filed_on date,
  status text DEFAULT 'DRAFT'::text NOT NULL,
  document_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_filings ADD CONSTRAINT ck_tax_filings_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'FILED'::text, 'ACCEPTED'::text, 'REJECTED'::text, 'REVISED'::text])));
ALTER TABLE tax.tax_filings ADD CONSTRAINT tax_filings_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_filings ADD CONSTRAINT uq_tax_filings_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_jurisdictions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  country_code character(3) DEFAULT 'IND'::bpchar NOT NULL,
  state_code text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_jurisdictions ADD CONSTRAINT tax_jurisdictions_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_jurisdictions ADD CONSTRAINT uq_tax_jurisdictions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_payments (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  period_id uuid NOT NULL,
  challan_no text,
  amount numeric(18,2) NOT NULL,
  paid_on date,
  disbursement_id uuid,
  status text DEFAULT 'PAID'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_payments ADD CONSTRAINT ck_tax_payments_amount CHECK ((amount >= (0)::numeric));
ALTER TABLE tax.tax_payments ADD CONSTRAINT tax_payments_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_payments ADD CONSTRAINT uq_tax_payments_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_periods (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  period_type text NOT NULL,
  financial_year text NOT NULL,
  start_date date NOT NULL,
  end_date date NOT NULL,
  status text DEFAULT 'OPEN'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_periods ADD CONSTRAINT ck_tax_periods_period_type CHECK ((period_type = ANY (ARRAY['MONTH'::text, 'QUARTER'::text, 'YEAR'::text])));
ALTER TABLE tax.tax_periods ADD CONSTRAINT ck_tax_periods_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'CLOSED'::text, 'FILED'::text])));
ALTER TABLE tax.tax_periods ADD CONSTRAINT tax_periods_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_periods ADD CONSTRAINT uq_tax_periods_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_rates (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  tax_code_id uuid NOT NULL,
  jurisdiction_id uuid,
  component text DEFAULT 'TOTAL'::text NOT NULL,
  rate_pct numeric(9,4) NOT NULL,
  effective_from date NOT NULL,
  effective_to date,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_rates ADD CONSTRAINT ck_tax_rates_component CHECK ((component = ANY (ARRAY['TOTAL'::text, 'CGST'::text, 'SGST'::text, 'IGST'::text, 'CESS'::text, 'TDS'::text])));
ALTER TABLE tax.tax_rates ADD CONSTRAINT ck_tax_rates_rate_pct CHECK ((rate_pct >= (0)::numeric));
ALTER TABLE tax.tax_rates ADD CONSTRAINT ck_tax_rates_x1 CHECK (((effective_to IS NULL) OR (effective_to >= effective_from)));
ALTER TABLE tax.tax_rates ADD CONSTRAINT tax_rates_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_rates ADD CONSTRAINT uq_tax_rates_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_rules (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  tax_code_id uuid,
  rule_type text NOT NULL,
  condition jsonb DEFAULT '{}'::jsonb NOT NULL,
  priority integer DEFAULT 100 NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_rules ADD CONSTRAINT tax_rules_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_rules ADD CONSTRAINT uq_tax_rules_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_transactions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  period_id uuid,
  tax_code_id uuid NOT NULL,
  source_type text NOT NULL,
  source_id uuid NOT NULL,
  direction text NOT NULL,
  taxable_amount numeric(18,2) NOT NULL,
  tax_amount numeric(18,2) NOT NULL,
  rate_pct numeric(9,4),
  breakdown jsonb DEFAULT '{}'::jsonb,
  txn_date date NOT NULL,
  calculation_snapshot jsonb,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE tax.tax_transactions ADD CONSTRAINT ck_tax_transactions_direction CHECK ((direction = ANY (ARRAY['OUTPUT'::text, 'INPUT'::text, 'WITHHELD'::text])));
ALTER TABLE tax.tax_transactions ADD CONSTRAINT ck_tax_transactions_tax_amount CHECK ((tax_amount >= (0)::numeric));
ALTER TABLE tax.tax_transactions ADD CONSTRAINT ck_tax_transactions_taxable_amount CHECK ((taxable_amount >= (0)::numeric));
ALTER TABLE tax.tax_transactions ADD CONSTRAINT tax_transactions_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_transactions ADD CONSTRAINT uq_tax_transactions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS tax.tax_types (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  category text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE tax.tax_types ADD CONSTRAINT ck_tax_types_category CHECK ((category = ANY (ARRAY['GST'::text, 'TDS'::text, 'TCS'::text, 'PT'::text, 'PF'::text, 'ESI'::text, 'INCOME_TAX'::text, 'OTHER'::text])));
ALTER TABLE tax.tax_types ADD CONSTRAINT tax_types_pkey PRIMARY KEY (id);
ALTER TABLE tax.tax_types ADD CONSTRAINT uq_tax_types_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.decisions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  instance_id uuid NOT NULL,
  task_id uuid,
  decision text NOT NULL,
  decided_by uuid NOT NULL,
  comment text,
  decided_at timestamp with time zone DEFAULT now() NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid()
);
ALTER TABLE wf.decisions ADD CONSTRAINT ck_decisions_decision CHECK ((decision = ANY (ARRAY['APPROVE'::text, 'REJECT'::text, 'RETURN'::text, 'ABSTAIN'::text])));
ALTER TABLE wf.decisions ADD CONSTRAINT decisions_pkey PRIMARY KEY (id);
ALTER TABLE wf.decisions ADD CONSTRAINT uq_decisions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.definition_steps (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  definition_id uuid NOT NULL,
  step_no integer NOT NULL,
  name text NOT NULL,
  approver_type text NOT NULL,
  approver_role_id uuid,
  approver_user_id uuid,
  mode text DEFAULT 'ANY'::text NOT NULL,
  quorum integer,
  sla_hours integer,
  escalate_after_hours integer,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.definition_steps ADD CONSTRAINT ck_definition_steps_approver_type CHECK ((approver_type = ANY (ARRAY['ROLE'::text, 'USER'::text, 'DEPARTMENT_HEAD'::text, 'REPORTING_MANAGER'::text, 'GROUP'::text])));
ALTER TABLE wf.definition_steps ADD CONSTRAINT ck_definition_steps_mode CHECK ((mode = ANY (ARRAY['ANY'::text, 'ALL'::text, 'QUORUM'::text])));
ALTER TABLE wf.definition_steps ADD CONSTRAINT ck_definition_steps_step_no CHECK ((step_no > 0));
ALTER TABLE wf.definition_steps ADD CONSTRAINT definition_steps_pkey PRIMARY KEY (id);
ALTER TABLE wf.definition_steps ADD CONSTRAINT uq_definition_steps_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.definitions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  code text NOT NULL,
  name text NOT NULL,
  entity_type text NOT NULL,
  module text,
  college_id uuid,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  version_no integer DEFAULT 1 NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.definitions ADD CONSTRAINT ck_definitions_status CHECK ((status = ANY (ARRAY['DRAFT'::text, 'ACTIVE'::text, 'RETIRED'::text])));
ALTER TABLE wf.definitions ADD CONSTRAINT definitions_pkey PRIMARY KEY (id);
ALTER TABLE wf.definitions ADD CONSTRAINT uq_definitions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.delegations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  from_user_id uuid NOT NULL,
  to_user_id uuid NOT NULL,
  definition_id uuid,
  valid_from timestamp with time zone NOT NULL,
  valid_to timestamp with time zone NOT NULL,
  reason text,
  status text DEFAULT 'ACTIVE'::text NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.delegations ADD CONSTRAINT ck_delegations_status CHECK ((status = ANY (ARRAY['ACTIVE'::text, 'REVOKED'::text, 'EXPIRED'::text])));
ALTER TABLE wf.delegations ADD CONSTRAINT ck_delegations_x1 CHECK ((valid_to > valid_from));
ALTER TABLE wf.delegations ADD CONSTRAINT delegations_pkey PRIMARY KEY (id);
ALTER TABLE wf.delegations ADD CONSTRAINT uq_delegations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.escalations (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  task_id uuid NOT NULL,
  escalated_to uuid,
  reason text,
  escalated_at timestamp with time zone DEFAULT now() NOT NULL,
  resolved_at timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.escalations ADD CONSTRAINT escalations_pkey PRIMARY KEY (id);
ALTER TABLE wf.escalations ADD CONSTRAINT uq_escalations_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.exceptions (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  instance_id uuid NOT NULL,
  exception_type text NOT NULL,
  reason text NOT NULL,
  granted_by uuid,
  valid_until timestamp with time zone,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.exceptions ADD CONSTRAINT exceptions_pkey PRIMARY KEY (id);
ALTER TABLE wf.exceptions ADD CONSTRAINT uq_exceptions_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.instances (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  definition_id uuid,
  college_id uuid,
  workflow_type text NOT NULL,
  entity_type text,
  entity_id uuid,
  status text DEFAULT 'PENDING'::text NOT NULL,
  requested_by uuid,
  current_step_no integer,
  submitted_at timestamp with time zone,
  completed_at timestamp with time zone,
  payload_snapshot jsonb,
  idempotency_key text,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.instances ADD CONSTRAINT ck_instances_status CHECK ((status = ANY (ARRAY['PENDING'::text, 'RUNNING'::text, 'IN_PROGRESS'::text, 'APPROVED'::text, 'REJECTED'::text, 'CANCELLED'::text, 'ESCALATED'::text, 'EXPIRED'::text, 'COMPLETED'::text, 'FAILED'::text, 'SUSPENDED'::text])));
ALTER TABLE wf.instances ADD CONSTRAINT instances_pkey PRIMARY KEY (id);
ALTER TABLE wf.instances ADD CONSTRAINT uq_instances_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.policies (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  definition_id uuid NOT NULL,
  name text,
  condition jsonb DEFAULT '{}'::jsonb NOT NULL,
  priority integer DEFAULT 100 NOT NULL,
  is_active boolean DEFAULT true NOT NULL,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.policies ADD CONSTRAINT policies_pkey PRIMARY KEY (id);
ALTER TABLE wf.policies ADD CONSTRAINT uq_policies_tenant_id UNIQUE (tenant_id, id);

CREATE TABLE IF NOT EXISTS wf.tasks (
  id uuid DEFAULT gen_random_uuid() NOT NULL,
  tenant_id uuid DEFAULT core.current_tenant_id() NOT NULL,
  instance_id uuid NOT NULL,
  step_no integer,
  assignee_id uuid,
  role_id uuid,
  status text DEFAULT 'OPEN'::text NOT NULL,
  due_at timestamp with time zone,
  completed_at timestamp with time zone,
  college_id uuid,
  created_at timestamp with time zone DEFAULT now() NOT NULL,
  updated_at timestamp with time zone DEFAULT now() NOT NULL,
  created_by uuid DEFAULT auth.uid(),
  updated_by uuid,
  row_version integer DEFAULT 1 NOT NULL,
  deleted_at timestamp with time zone
);
ALTER TABLE wf.tasks ADD CONSTRAINT ck_tasks_status CHECK ((status = ANY (ARRAY['OPEN'::text, 'IN_PROGRESS'::text, 'COMPLETED'::text, 'BLOCKED'::text, 'SKIPPED'::text, 'DELEGATED'::text])));
ALTER TABLE wf.tasks ADD CONSTRAINT tasks_pkey PRIMARY KEY (id);
ALTER TABLE wf.tasks ADD CONSTRAINT uq_tasks_tenant_id UNIQUE (tenant_id, id);

ALTER TABLE acd.assessment_components ADD CONSTRAINT fk_assessment_components_structure_id FOREIGN KEY (tenant_id, structure_id) REFERENCES acd.assessment_structures(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.assessment_components ADD CONSTRAINT fk_assessment_components_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.assessment_structures ADD CONSTRAINT fk_assessment_structures_curriculum_version_id FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.assessment_structures ADD CONSTRAINT fk_assessment_structures_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.assessment_structures ADD CONSTRAINT fk_assessment_structures_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT fk_attendance_corrections_approved_by FOREIGN KEY (tenant_id, approved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT fk_attendance_corrections_record_id FOREIGN KEY (tenant_id, record_id) REFERENCES acd.attendance_records(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT fk_attendance_corrections_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT fk_attendance_corrections_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_corrections ADD CONSTRAINT fk_attendance_corrections_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_records ADD CONSTRAINT fk_attendance_records_marked_by FOREIGN KEY (tenant_id, marked_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_records ADD CONSTRAINT fk_attendance_records_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES acd.attendance_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.attendance_records ADD CONSTRAINT fk_attendance_records_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_records ADD CONSTRAINT fk_attendance_records_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_sessions ADD CONSTRAINT fk_attendance_sessions_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_sessions ADD CONSTRAINT fk_attendance_sessions_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_sessions ADD CONSTRAINT fk_attendance_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.attendance_sessions ADD CONSTRAINT fk_attendance_sessions_timetable_entry_id FOREIGN KEY (tenant_id, timetable_entry_id) REFERENCES acd.timetable_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batch_capacity_overrides ADD CONSTRAINT fk_batch_capacity_overrides_approved_by FOREIGN KEY (tenant_id, approved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batch_capacity_overrides ADD CONSTRAINT fk_batch_capacity_overrides_batch_id FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.batch_capacity_overrides ADD CONSTRAINT fk_batch_capacity_overrides_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.batch_capacity_overrides ADD CONSTRAINT fk_batch_capacity_overrides_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batch_students ADD CONSTRAINT fk_batch_students_batch_id FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.batch_students ADD CONSTRAINT fk_batch_students_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batch_students ADD CONSTRAINT fk_batch_students_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.batches ADD CONSTRAINT fk_batches_admission_year_id FOREIGN KEY (tenant_id, admission_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batches ADD CONSTRAINT fk_batches_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batches ADD CONSTRAINT fk_batches_curriculum_version_id FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batches ADD CONSTRAINT fk_batches_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.batches ADD CONSTRAINT fk_batches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.calendar_terms ADD CONSTRAINT fk_calendar_terms_calendar_id FOREIGN KEY (tenant_id, calendar_id) REFERENCES core.calendars(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.calendar_terms ADD CONSTRAINT fk_calendar_terms_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.course_offerings ADD CONSTRAINT fk_course_offerings_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.course_offerings ADD CONSTRAINT fk_course_offerings_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.course_offerings ADD CONSTRAINT fk_course_offerings_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.course_offerings ADD CONSTRAINT fk_course_offerings_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.course_prerequisites ADD CONSTRAINT fk_course_prerequisites_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.course_prerequisites ADD CONSTRAINT fk_course_prerequisites_prerequisite_course_id FOREIGN KEY (tenant_id, prerequisite_course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.course_prerequisites ADD CONSTRAINT fk_course_prerequisites_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.course_program_map ADD CONSTRAINT fk_course_program_map_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.course_program_map ADD CONSTRAINT fk_course_program_map_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.course_program_map ADD CONSTRAINT fk_course_program_map_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.course_sections ADD CONSTRAINT fk_course_sections_batch_id FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.course_sections ADD CONSTRAINT fk_course_sections_default_room_id FOREIGN KEY (tenant_id, default_room_id) REFERENCES core.rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.course_sections ADD CONSTRAINT fk_course_sections_offering_id FOREIGN KEY (tenant_id, offering_id) REFERENCES acd.course_offerings(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.course_sections ADD CONSTRAINT fk_course_sections_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.course_versions ADD CONSTRAINT fk_course_versions_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.course_versions ADD CONSTRAINT fk_course_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.courses ADD CONSTRAINT fk_courses_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.courses ADD CONSTRAINT fk_courses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.curricula ADD CONSTRAINT fk_curricula_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.curricula ADD CONSTRAINT fk_curricula_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT fk_curriculum_subjects_curriculum_version_id FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT fk_curriculum_subjects_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.curriculum_subjects ADD CONSTRAINT fk_curriculum_subjects_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT fk_curriculum_versions_curriculum_id FOREIGN KEY (tenant_id, curriculum_id) REFERENCES acd.curricula(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT fk_curriculum_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.curriculum_versions ADD CONSTRAINT fk_curriculum_versions_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.enrollment_rules ADD CONSTRAINT fk_enrollment_rules_offering_id FOREIGN KEY (tenant_id, offering_id) REFERENCES acd.course_offerings(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.enrollment_rules ADD CONSTRAINT fk_enrollment_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.programs ADD CONSTRAINT fk_programs_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.programs ADD CONSTRAINT fk_programs_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.programs ADD CONSTRAINT fk_programs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.section_enrollments ADD CONSTRAINT fk_section_enrollments_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.section_enrollments ADD CONSTRAINT fk_section_enrollments_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.section_enrollments ADD CONSTRAINT fk_section_enrollments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.section_faculty ADD CONSTRAINT fk_section_faculty_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.section_faculty ADD CONSTRAINT fk_section_faculty_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.section_faculty ADD CONSTRAINT fk_section_faculty_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_change_requests ADD CONSTRAINT fk_subject_change_requests_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_change_requests ADD CONSTRAINT fk_subject_change_requests_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_change_requests ADD CONSTRAINT fk_subject_change_requests_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_change_requests ADD CONSTRAINT fk_subject_change_requests_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_course_map ADD CONSTRAINT fk_subject_course_map_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.subject_course_map ADD CONSTRAINT fk_subject_course_map_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.subject_course_map ADD CONSTRAINT fk_subject_course_map_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_equivalencies ADD CONSTRAINT fk_subject_equivalencies_equivalent_subject_id FOREIGN KEY (tenant_id, equivalent_subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.subject_equivalencies ADD CONSTRAINT fk_subject_equivalencies_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.subject_equivalencies ADD CONSTRAINT fk_subject_equivalencies_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT fk_subject_prerequisites_curriculum_version_id FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT fk_subject_prerequisites_prerequisite_subject_id FOREIGN KEY (tenant_id, prerequisite_subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT fk_subject_prerequisites_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.subject_prerequisites ADD CONSTRAINT fk_subject_prerequisites_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.subject_versions ADD CONSTRAINT fk_subject_versions_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.subject_versions ADD CONSTRAINT fk_subject_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.subjects ADD CONSTRAINT fk_subjects_category_id FOREIGN KEY (tenant_id, category_id) REFERENCES core.lookup_values(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.subjects ADD CONSTRAINT fk_subjects_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.subjects ADD CONSTRAINT fk_subjects_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT fk_timetable_conflicts_entry_a_id FOREIGN KEY (tenant_id, entry_a_id) REFERENCES acd.timetable_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT fk_timetable_conflicts_entry_b_id FOREIGN KEY (tenant_id, entry_b_id) REFERENCES acd.timetable_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT fk_timetable_conflicts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_conflicts ADD CONSTRAINT fk_timetable_conflicts_timetable_version_id FOREIGN KEY (tenant_id, timetable_version_id) REFERENCES acd.timetable_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.timetable_entries ADD CONSTRAINT fk_timetable_entries_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_entries ADD CONSTRAINT fk_timetable_entries_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES core.rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_entries ADD CONSTRAINT fk_timetable_entries_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_entries ADD CONSTRAINT fk_timetable_entries_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_entries ADD CONSTRAINT fk_timetable_entries_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_entries ADD CONSTRAINT fk_timetable_entries_timetable_version_id FOREIGN KEY (tenant_id, timetable_version_id) REFERENCES acd.timetable_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.timetable_versions ADD CONSTRAINT fk_timetable_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.timetable_versions ADD CONSTRAINT fk_timetable_versions_timetable_id FOREIGN KEY (tenant_id, timetable_id) REFERENCES acd.timetables(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE acd.timetables ADD CONSTRAINT fk_timetables_batch_id FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetables ADD CONSTRAINT fk_timetables_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE acd.timetables ADD CONSTRAINT fk_timetables_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE acd.timetables ADD CONSTRAINT fk_timetables_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.access_events ADD CONSTRAINT fk_access_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.access_events_default ADD CONSTRAINT fk_access_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.access_events_p202610 ADD CONSTRAINT fk_access_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.access_events_p202611 ADD CONSTRAINT fk_access_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.access_events_p202612 ADD CONSTRAINT fk_access_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.access_events_p202701 ADD CONSTRAINT fk_access_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.archives ADD CONSTRAINT fk_archives_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.archives ADD CONSTRAINT fk_archives_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.case_actions ADD CONSTRAINT fk_case_actions_actor_id FOREIGN KEY (tenant_id, actor_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.case_actions ADD CONSTRAINT fk_case_actions_case_id FOREIGN KEY (tenant_id, case_id) REFERENCES audit.cases(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE audit.case_actions ADD CONSTRAINT fk_case_actions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.case_items ADD CONSTRAINT fk_case_items_case_id FOREIGN KEY (tenant_id, case_id) REFERENCES audit.cases(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE audit.case_items ADD CONSTRAINT fk_case_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.cases ADD CONSTRAINT fk_cases_opened_by FOREIGN KEY (tenant_id, opened_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.cases ADD CONSTRAINT fk_cases_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.change_log ADD CONSTRAINT fk_change_log_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.change_log_default ADD CONSTRAINT fk_change_log_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.change_log_p202610 ADD CONSTRAINT fk_change_log_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.change_log_p202611 ADD CONSTRAINT fk_change_log_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.change_log_p202612 ADD CONSTRAINT fk_change_log_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.change_log_p202701 ADD CONSTRAINT fk_change_log_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_evaluations ADD CONSTRAINT fk_compliance_evaluations_rule_id FOREIGN KEY (tenant_id, rule_id) REFERENCES audit.compliance_rules(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_evaluations ADD CONSTRAINT fk_compliance_evaluations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_exceptions ADD CONSTRAINT fk_compliance_exceptions_approved_by FOREIGN KEY (tenant_id, approved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_exceptions ADD CONSTRAINT fk_compliance_exceptions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_exceptions ADD CONSTRAINT fk_compliance_exceptions_violation_id FOREIGN KEY (tenant_id, violation_id) REFERENCES audit.compliance_violations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_rules ADD CONSTRAINT fk_compliance_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_violations ADD CONSTRAINT fk_compliance_violations_evaluation_id FOREIGN KEY (tenant_id, evaluation_id) REFERENCES audit.compliance_evaluations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_violations ADD CONSTRAINT fk_compliance_violations_rule_id FOREIGN KEY (tenant_id, rule_id) REFERENCES audit.compliance_rules(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.compliance_violations ADD CONSTRAINT fk_compliance_violations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.events ADD CONSTRAINT fk_events_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.events ADD CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.events_default ADD CONSTRAINT fk_events_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.events_default ADD CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202610 ADD CONSTRAINT fk_events_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202610 ADD CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202611 ADD CONSTRAINT fk_events_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202611 ADD CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202612 ADD CONSTRAINT fk_events_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202612 ADD CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202701 ADD CONSTRAINT fk_events_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.events_p202701 ADD CONSTRAINT fk_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.evidence ADD CONSTRAINT fk_evidence_case_id FOREIGN KEY (tenant_id, case_id) REFERENCES audit.cases(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.evidence ADD CONSTRAINT fk_evidence_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.evidence ADD CONSTRAINT fk_evidence_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.integrity_records ADD CONSTRAINT fk_integrity_records_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.retention_policies ADD CONSTRAINT fk_retention_policies_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.sod_rules ADD CONSTRAINT fk_sod_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.sod_violations ADD CONSTRAINT fk_sod_violations_rule_id FOREIGN KEY (tenant_id, rule_id) REFERENCES audit.sod_rules(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE audit.sod_violations ADD CONSTRAINT fk_sod_violations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE audit.sod_violations ADD CONSTRAINT fk_sod_violations_user_id FOREIGN KEY (tenant_id, user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.assumptions ADD CONSTRAINT fk_assumptions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_controls ADD CONSTRAINT fk_budget_controls_budget_id FOREIGN KEY (tenant_id, budget_id) REFERENCES bud.budgets(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_controls ADD CONSTRAINT fk_budget_controls_category_id FOREIGN KEY (tenant_id, category_id) REFERENCES bud.categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_controls ADD CONSTRAINT fk_budget_controls_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_lines ADD CONSTRAINT fk_budget_lines_budget_version_id FOREIGN KEY (tenant_id, budget_version_id) REFERENCES bud.budget_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE bud.budget_lines ADD CONSTRAINT fk_budget_lines_category_id FOREIGN KEY (tenant_id, category_id) REFERENCES bud.categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_lines ADD CONSTRAINT fk_budget_lines_cost_centre_id FOREIGN KEY (tenant_id, cost_centre_id) REFERENCES bud.cost_centres(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_lines ADD CONSTRAINT fk_budget_lines_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_lines ADD CONSTRAINT fk_budget_lines_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES bud.budget_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_lines ADD CONSTRAINT fk_budget_lines_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_periods ADD CONSTRAINT fk_budget_periods_budget_id FOREIGN KEY (tenant_id, budget_id) REFERENCES bud.budgets(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE bud.budget_periods ADD CONSTRAINT fk_budget_periods_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_transfers ADD CONSTRAINT fk_budget_transfers_from_line_id FOREIGN KEY (tenant_id, from_line_id) REFERENCES bud.budget_lines(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_transfers ADD CONSTRAINT fk_budget_transfers_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_transfers ADD CONSTRAINT fk_budget_transfers_to_line_id FOREIGN KEY (tenant_id, to_line_id) REFERENCES bud.budget_lines(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_transfers ADD CONSTRAINT fk_budget_transfers_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budget_versions ADD CONSTRAINT fk_budget_versions_budget_id FOREIGN KEY (tenant_id, budget_id) REFERENCES bud.budgets(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE bud.budget_versions ADD CONSTRAINT fk_budget_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.budgets ADD CONSTRAINT fk_budgets_academic_year_id FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budgets ADD CONSTRAINT fk_budgets_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.budgets ADD CONSTRAINT fk_budgets_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.budgets ADD CONSTRAINT fk_budgets_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.categories ADD CONSTRAINT fk_categories_ledger_account_id FOREIGN KEY (tenant_id, ledger_account_id) REFERENCES fin.ledger_accounts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.categories ADD CONSTRAINT fk_categories_parent_id FOREIGN KEY (tenant_id, parent_id) REFERENCES bud.categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.categories ADD CONSTRAINT fk_categories_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.commitments ADD CONSTRAINT fk_commitments_budget_line_id FOREIGN KEY (tenant_id, budget_line_id) REFERENCES bud.budget_lines(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.commitments ADD CONSTRAINT fk_commitments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.control_results ADD CONSTRAINT fk_control_results_budget_line_id FOREIGN KEY (tenant_id, budget_line_id) REFERENCES bud.budget_lines(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.control_results ADD CONSTRAINT fk_control_results_control_id FOREIGN KEY (tenant_id, control_id) REFERENCES bud.budget_controls(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.control_results ADD CONSTRAINT fk_control_results_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.cost_centres ADD CONSTRAINT fk_cost_centres_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.cost_centres ADD CONSTRAINT fk_cost_centres_parent_id FOREIGN KEY (tenant_id, parent_id) REFERENCES bud.cost_centres(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.cost_centres ADD CONSTRAINT fk_cost_centres_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.forecast_lines ADD CONSTRAINT fk_forecast_lines_category_id FOREIGN KEY (tenant_id, category_id) REFERENCES bud.categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.forecast_lines ADD CONSTRAINT fk_forecast_lines_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.forecast_lines ADD CONSTRAINT fk_forecast_lines_forecast_id FOREIGN KEY (tenant_id, forecast_id) REFERENCES bud.forecasts(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE bud.forecast_lines ADD CONSTRAINT fk_forecast_lines_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES bud.budget_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.forecast_lines ADD CONSTRAINT fk_forecast_lines_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.forecasts ADD CONSTRAINT fk_forecasts_budget_id FOREIGN KEY (tenant_id, budget_id) REFERENCES bud.budgets(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.forecasts ADD CONSTRAINT fk_forecasts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.scenario_results ADD CONSTRAINT fk_scenario_results_scenario_id FOREIGN KEY (tenant_id, scenario_id) REFERENCES bud.scenarios(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE bud.scenario_results ADD CONSTRAINT fk_scenario_results_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.scenarios ADD CONSTRAINT fk_scenarios_budget_id FOREIGN KEY (tenant_id, budget_id) REFERENCES bud.budgets(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.scenarios ADD CONSTRAINT fk_scenarios_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE bud.variance_explanations ADD CONSTRAINT fk_variance_explanations_budget_line_id FOREIGN KEY (tenant_id, budget_line_id) REFERENCES bud.budget_lines(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.variance_explanations ADD CONSTRAINT fk_variance_explanations_explained_by FOREIGN KEY (tenant_id, explained_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.variance_explanations ADD CONSTRAINT fk_variance_explanations_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES bud.budget_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE bud.variance_explanations ADD CONSTRAINT fk_variance_explanations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.alert_acknowledgements ADD CONSTRAINT fk_alert_acknowledgements_alert_id FOREIGN KEY (tenant_id, alert_id) REFERENCES cam.emergency_alerts(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.alert_acknowledgements ADD CONSTRAINT fk_alert_acknowledgements_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.alert_acknowledgements ADD CONSTRAINT fk_alert_acknowledgements_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.alert_escalation_rules ADD CONSTRAINT fk_alert_escalation_rules_escalate_to_group_id FOREIGN KEY (tenant_id, escalate_to_group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.alert_escalation_rules ADD CONSTRAINT fk_alert_escalation_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.audience_groups ADD CONSTRAINT fk_audience_groups_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.audience_groups ADD CONSTRAINT fk_audience_groups_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.audience_members ADD CONSTRAINT fk_audience_members_group_id FOREIGN KEY (tenant_id, group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.audience_members ADD CONSTRAINT fk_audience_members_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.audience_members ADD CONSTRAINT fk_audience_members_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.audience_rules ADD CONSTRAINT fk_audience_rules_group_id FOREIGN KEY (tenant_id, group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.audience_rules ADD CONSTRAINT fk_audience_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.audience_snapshots ADD CONSTRAINT fk_audience_snapshots_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.campaigns ADD CONSTRAINT fk_campaigns_audience_group_id FOREIGN KEY (tenant_id, audience_group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.campaigns ADD CONSTRAINT fk_campaigns_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.campaigns ADD CONSTRAINT fk_campaigns_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.campaigns ADD CONSTRAINT fk_campaigns_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.deliveries ADD CONSTRAINT fk_deliveries_message_id FOREIGN KEY (tenant_id, message_id) REFERENCES cam.messages(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.deliveries ADD CONSTRAINT fk_deliveries_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.deliveries ADD CONSTRAINT fk_deliveries_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.deliveries ADD CONSTRAINT fk_deliveries_recipient_id FOREIGN KEY (tenant_id, recipient_id) REFERENCES cam.message_recipients(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.deliveries ADD CONSTRAINT fk_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_attempts ADD CONSTRAINT fk_delivery_attempts_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES cam.deliveries(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.delivery_attempts ADD CONSTRAINT fk_delivery_attempts_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_attempts ADD CONSTRAINT fk_delivery_attempts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events ADD CONSTRAINT fk_delivery_events_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES cam.deliveries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events ADD CONSTRAINT fk_delivery_events_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events ADD CONSTRAINT fk_delivery_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_default ADD CONSTRAINT fk_delivery_events_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES cam.deliveries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_default ADD CONSTRAINT fk_delivery_events_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_default ADD CONSTRAINT fk_delivery_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202610 ADD CONSTRAINT fk_delivery_events_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES cam.deliveries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202610 ADD CONSTRAINT fk_delivery_events_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202610 ADD CONSTRAINT fk_delivery_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202611 ADD CONSTRAINT fk_delivery_events_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES cam.deliveries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202611 ADD CONSTRAINT fk_delivery_events_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202611 ADD CONSTRAINT fk_delivery_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202612 ADD CONSTRAINT fk_delivery_events_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES cam.deliveries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202612 ADD CONSTRAINT fk_delivery_events_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202612 ADD CONSTRAINT fk_delivery_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202701 ADD CONSTRAINT fk_delivery_events_delivery_id FOREIGN KEY (tenant_id, delivery_id) REFERENCES cam.deliveries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202701 ADD CONSTRAINT fk_delivery_events_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.delivery_events_p202701 ADD CONSTRAINT fk_delivery_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.device_tokens ADD CONSTRAINT fk_device_tokens_device_id FOREIGN KEY (tenant_id, device_id) REFERENCES cam.devices(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.device_tokens ADD CONSTRAINT fk_device_tokens_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.device_tokens ADD CONSTRAINT fk_device_tokens_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.devices ADD CONSTRAINT fk_devices_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.devices ADD CONSTRAINT fk_devices_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT fk_emergency_alerts_audience_group_id FOREIGN KEY (tenant_id, audience_group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT fk_emergency_alerts_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT fk_emergency_alerts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.emergency_alerts ADD CONSTRAINT fk_emergency_alerts_triggered_by FOREIGN KEY (tenant_id, triggered_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.message_recipients ADD CONSTRAINT fk_message_recipients_message_id FOREIGN KEY (tenant_id, message_id) REFERENCES cam.messages(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.message_recipients ADD CONSTRAINT fk_message_recipients_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.message_recipients ADD CONSTRAINT fk_message_recipients_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.messages ADD CONSTRAINT fk_messages_campaign_id FOREIGN KEY (tenant_id, campaign_id) REFERENCES cam.campaigns(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.messages ADD CONSTRAINT fk_messages_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.messages ADD CONSTRAINT fk_messages_created_by_user_id FOREIGN KEY (tenant_id, created_by_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.messages ADD CONSTRAINT fk_messages_template_id FOREIGN KEY (tenant_id, template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.messages ADD CONSTRAINT fk_messages_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.notification_rules ADD CONSTRAINT fk_notification_rules_audience_group_id FOREIGN KEY (tenant_id, audience_group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.notification_rules ADD CONSTRAINT fk_notification_rules_template_id FOREIGN KEY (tenant_id, template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.notification_rules ADD CONSTRAINT fk_notification_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.provider_health ADD CONSTRAINT fk_provider_health_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.provider_health ADD CONSTRAINT fk_provider_health_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.provider_routes ADD CONSTRAINT fk_provider_routes_provider_id FOREIGN KEY (tenant_id, provider_id) REFERENCES cam.providers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.provider_routes ADD CONSTRAINT fk_provider_routes_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.providers ADD CONSTRAINT fk_providers_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.rule_evaluations ADD CONSTRAINT fk_rule_evaluations_rule_id FOREIGN KEY (tenant_id, rule_id) REFERENCES cam.notification_rules(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.rule_evaluations ADD CONSTRAINT fk_rule_evaluations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.survey_answers ADD CONSTRAINT fk_survey_answers_question_id FOREIGN KEY (tenant_id, question_id) REFERENCES cam.survey_questions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.survey_answers ADD CONSTRAINT fk_survey_answers_response_id FOREIGN KEY (tenant_id, response_id) REFERENCES cam.survey_responses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.survey_answers ADD CONSTRAINT fk_survey_answers_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.survey_questions ADD CONSTRAINT fk_survey_questions_outcome_id FOREIGN KEY (tenant_id, outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.survey_questions ADD CONSTRAINT fk_survey_questions_survey_id FOREIGN KEY (tenant_id, survey_id) REFERENCES cam.surveys(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE cam.survey_questions ADD CONSTRAINT fk_survey_questions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.survey_responses ADD CONSTRAINT fk_survey_responses_respondent_person_id FOREIGN KEY (tenant_id, respondent_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.survey_responses ADD CONSTRAINT fk_survey_responses_survey_id FOREIGN KEY (tenant_id, survey_id) REFERENCES cam.surveys(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.survey_responses ADD CONSTRAINT fk_survey_responses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cam.surveys ADD CONSTRAINT fk_surveys_audience_group_id FOREIGN KEY (tenant_id, audience_group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.surveys ADD CONSTRAINT fk_surveys_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cam.surveys ADD CONSTRAINT fk_surveys_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cfg.feature_flag_rules ADD CONSTRAINT fk_feature_flag_rules_flag_id FOREIGN KEY (flag_id) REFERENCES cfg.feature_flags(id) ON DELETE CASCADE;
ALTER TABLE cfg.feature_overrides ADD CONSTRAINT fk_feature_overrides_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cfg.feature_overrides ADD CONSTRAINT fk_feature_overrides_flag_id FOREIGN KEY (flag_id) REFERENCES cfg.feature_flags(id) ON DELETE RESTRICT;
ALTER TABLE cfg.feature_overrides ADD CONSTRAINT fk_feature_overrides_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE cfg.setting_values ADD CONSTRAINT fk_setting_values_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE cfg.setting_values ADD CONSTRAINT fk_setting_values_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.academic_years ADD CONSTRAINT fk_academic_years_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.addresses ADD CONSTRAINT fk_addresses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.calendar_events ADD CONSTRAINT fk_calendar_events_calendar_id FOREIGN KEY (tenant_id, calendar_id) REFERENCES core.calendars(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.calendar_events ADD CONSTRAINT fk_calendar_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.calendar_working_days ADD CONSTRAINT fk_calendar_working_days_calendar_id FOREIGN KEY (tenant_id, calendar_id) REFERENCES core.calendars(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.calendar_working_days ADD CONSTRAINT fk_calendar_working_days_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.calendars ADD CONSTRAINT fk_calendars_academic_year_id FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.calendars ADD CONSTRAINT fk_calendars_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.calendars ADD CONSTRAINT fk_calendars_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.campuses ADD CONSTRAINT fk_campuses_address_id FOREIGN KEY (tenant_id, address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.campuses ADD CONSTRAINT fk_campuses_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.campuses ADD CONSTRAINT fk_campuses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.college_accreditations ADD CONSTRAINT fk_college_accreditations_certificate_document_id FOREIGN KEY (tenant_id, certificate_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.college_accreditations ADD CONSTRAINT fk_college_accreditations_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.college_accreditations ADD CONSTRAINT fk_college_accreditations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.colleges ADD CONSTRAINT fk_colleges_address_id FOREIGN KEY (tenant_id, address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.colleges ADD CONSTRAINT fk_colleges_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.comments ADD CONSTRAINT fk_comments_parent_comment_id FOREIGN KEY (tenant_id, parent_comment_id) REFERENCES core.comments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.comments ADD CONSTRAINT fk_comments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.departments ADD CONSTRAINT fk_departments_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.departments ADD CONSTRAINT fk_departments_head_employee_id FOREIGN KEY (tenant_id, head_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.departments ADD CONSTRAINT fk_departments_parent_department_id FOREIGN KEY (tenant_id, parent_department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.departments ADD CONSTRAINT fk_departments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.entity_tags ADD CONSTRAINT fk_entity_tags_tag_id FOREIGN KEY (tenant_id, tag_id) REFERENCES core.tags(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.entity_tags ADD CONSTRAINT fk_entity_tags_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.entity_versions ADD CONSTRAINT fk_entity_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.lookup_types ADD CONSTRAINT fk_lookup_types_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.lookup_values ADD CONSTRAINT fk_lookup_values_lookup_type_id FOREIGN KEY (tenant_id, lookup_type_id) REFERENCES core.lookup_types(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.lookup_values ADD CONSTRAINT fk_lookup_values_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.number_sequences ADD CONSTRAINT fk_number_sequences_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.number_sequences ADD CONSTRAINT fk_number_sequences_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.person_contacts ADD CONSTRAINT fk_person_contacts_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.person_contacts ADD CONSTRAINT fk_person_contacts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.person_emergency_contacts ADD CONSTRAINT fk_person_emergency_contacts_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.person_emergency_contacts ADD CONSTRAINT fk_person_emergency_contacts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.person_experience ADD CONSTRAINT fk_person_experience_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.person_experience ADD CONSTRAINT fk_person_experience_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.person_experience ADD CONSTRAINT fk_person_experience_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.person_identifiers ADD CONSTRAINT fk_person_identifiers_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.person_identifiers ADD CONSTRAINT fk_person_identifiers_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.person_qualifications ADD CONSTRAINT fk_person_qualifications_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.person_qualifications ADD CONSTRAINT fk_person_qualifications_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE core.person_qualifications ADD CONSTRAINT fk_person_qualifications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.persons ADD CONSTRAINT fk_persons_current_address_id FOREIGN KEY (tenant_id, current_address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.persons ADD CONSTRAINT fk_persons_permanent_address_id FOREIGN KEY (tenant_id, permanent_address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.persons ADD CONSTRAINT fk_persons_photo_document_id FOREIGN KEY (tenant_id, photo_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.persons ADD CONSTRAINT fk_persons_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.publications ADD CONSTRAINT fk_publications_published_by FOREIGN KEY (tenant_id, published_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.publications ADD CONSTRAINT fk_publications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.rooms ADD CONSTRAINT fk_rooms_campus_id FOREIGN KEY (tenant_id, campus_id) REFERENCES core.campuses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE core.rooms ADD CONSTRAINT fk_rooms_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE core.tags ADD CONSTRAINT fk_tags_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.document_deliveries ADD CONSTRAINT fk_document_deliveries_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.document_deliveries ADD CONSTRAINT fk_document_deliveries_recipient_person_id FOREIGN KEY (tenant_id, recipient_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.document_deliveries ADD CONSTRAINT fk_document_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.document_links ADD CONSTRAINT fk_document_links_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE doc.document_links ADD CONSTRAINT fk_document_links_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.document_renditions ADD CONSTRAINT fk_document_renditions_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE doc.document_renditions ADD CONSTRAINT fk_document_renditions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.document_types ADD CONSTRAINT fk_document_types_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.document_versions ADD CONSTRAINT fk_document_versions_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE doc.document_versions ADD CONSTRAINT fk_document_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.documents ADD CONSTRAINT fk_documents_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.documents ADD CONSTRAINT fk_documents_document_type_id FOREIGN KEY (tenant_id, document_type_id) REFERENCES doc.document_types(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.documents ADD CONSTRAINT fk_documents_owner_id FOREIGN KEY (tenant_id, owner_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.documents ADD CONSTRAINT fk_documents_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.documents ADD CONSTRAINT fk_documents_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT fk_lifecycle_requests_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT fk_lifecycle_requests_new_document_id FOREIGN KEY (tenant_id, new_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT fk_lifecycle_requests_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT fk_lifecycle_requests_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.lifecycle_requests ADD CONSTRAINT fk_lifecycle_requests_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.print_job_items ADD CONSTRAINT fk_print_job_items_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.print_job_items ADD CONSTRAINT fk_print_job_items_print_job_id FOREIGN KEY (tenant_id, print_job_id) REFERENCES doc.print_jobs(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE doc.print_job_items ADD CONSTRAINT fk_print_job_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.print_jobs ADD CONSTRAINT fk_print_jobs_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.print_jobs ADD CONSTRAINT fk_print_jobs_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.print_jobs ADD CONSTRAINT fk_print_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.signatures ADD CONSTRAINT fk_signatures_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE doc.signatures ADD CONSTRAINT fk_signatures_signer_id FOREIGN KEY (tenant_id, signer_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.signatures ADD CONSTRAINT fk_signatures_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.template_versions ADD CONSTRAINT fk_template_versions_template_id FOREIGN KEY (tenant_id, template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE doc.template_versions ADD CONSTRAINT fk_template_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.templates ADD CONSTRAINT fk_templates_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.templates ADD CONSTRAINT fk_templates_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE doc.verifications ADD CONSTRAINT fk_verifications_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE doc.verifications ADD CONSTRAINT fk_verifications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.answer_scripts ADD CONSTRAINT fk_answer_scripts_exam_session_id FOREIGN KEY (tenant_id, exam_session_id) REFERENCES exm.exam_sessions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.answer_scripts ADD CONSTRAINT fk_answer_scripts_registration_id FOREIGN KEY (tenant_id, registration_id) REFERENCES exm.registrations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.answer_scripts ADD CONSTRAINT fk_answer_scripts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.eligibility_snapshots ADD CONSTRAINT fk_eligibility_snapshots_registration_id FOREIGN KEY (tenant_id, registration_id) REFERENCES exm.registrations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.eligibility_snapshots ADD CONSTRAINT fk_eligibility_snapshots_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_centres ADD CONSTRAINT fk_exam_centres_address_id FOREIGN KEY (tenant_id, address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_centres ADD CONSTRAINT fk_exam_centres_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_centres ADD CONSTRAINT fk_exam_centres_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_rooms ADD CONSTRAINT fk_exam_rooms_centre_id FOREIGN KEY (tenant_id, centre_id) REFERENCES exm.exam_centres(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.exam_rooms ADD CONSTRAINT fk_exam_rooms_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES core.rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_rooms ADD CONSTRAINT fk_exam_rooms_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_sessions ADD CONSTRAINT fk_exam_sessions_centre_id FOREIGN KEY (tenant_id, centre_id) REFERENCES exm.exam_centres(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_sessions ADD CONSTRAINT fk_exam_sessions_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_sessions ADD CONSTRAINT fk_exam_sessions_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.exam_sessions ADD CONSTRAINT fk_exam_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_subjects ADD CONSTRAINT fk_exam_subjects_assessment_structure_id FOREIGN KEY (tenant_id, assessment_structure_id) REFERENCES acd.assessment_structures(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_subjects ADD CONSTRAINT fk_exam_subjects_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.exam_subjects ADD CONSTRAINT fk_exam_subjects_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.exam_subjects ADD CONSTRAINT fk_exam_subjects_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.examinations ADD CONSTRAINT fk_examinations_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.examinations ADD CONSTRAINT fk_examinations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.examinations ADD CONSTRAINT fk_examinations_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_ticket_nocs ADD CONSTRAINT fk_hall_ticket_nocs_granted_by FOREIGN KEY (tenant_id, granted_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_ticket_nocs ADD CONSTRAINT fk_hall_ticket_nocs_hall_ticket_id FOREIGN KEY (tenant_id, hall_ticket_id) REFERENCES exm.hall_tickets(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.hall_ticket_nocs ADD CONSTRAINT fk_hall_ticket_nocs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_ticket_subjects ADD CONSTRAINT fk_hall_ticket_subjects_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.hall_ticket_subjects ADD CONSTRAINT fk_hall_ticket_subjects_hall_ticket_id FOREIGN KEY (tenant_id, hall_ticket_id) REFERENCES exm.hall_tickets(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.hall_ticket_subjects ADD CONSTRAINT fk_hall_ticket_subjects_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES exm.exam_sessions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_ticket_subjects ADD CONSTRAINT fk_hall_ticket_subjects_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_tickets ADD CONSTRAINT fk_hall_tickets_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_tickets ADD CONSTRAINT fk_hall_tickets_registration_id_consistent FOREIGN KEY (tenant_id, registration_id, student_id) REFERENCES exm.registrations(tenant_id, id, student_id) ON DELETE CASCADE;
ALTER TABLE exm.hall_tickets ADD CONSTRAINT fk_hall_tickets_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_tickets ADD CONSTRAINT fk_hall_tickets_template_id FOREIGN KEY (tenant_id, template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.hall_tickets ADD CONSTRAINT fk_hall_tickets_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT fk_invigilator_assignments_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT fk_invigilator_assignments_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES exm.exam_rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT fk_invigilator_assignments_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES exm.exam_sessions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.invigilator_assignments ADD CONSTRAINT fk_invigilator_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_corrections ADD CONSTRAINT fk_marks_corrections_approved_by FOREIGN KEY (tenant_id, approved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_corrections ADD CONSTRAINT fk_marks_corrections_marks_entry_id FOREIGN KEY (tenant_id, marks_entry_id) REFERENCES exm.marks_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_corrections ADD CONSTRAINT fk_marks_corrections_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_entries ADD CONSTRAINT fk_marks_entries_answer_script_id FOREIGN KEY (tenant_id, answer_script_id) REFERENCES exm.answer_scripts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_entries ADD CONSTRAINT fk_marks_entries_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_entries ADD CONSTRAINT fk_marks_entries_valuation_session_id FOREIGN KEY (tenant_id, valuation_session_id) REFERENCES exm.valuation_sessions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_finalizations ADD CONSTRAINT fk_marks_finalizations_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_finalizations ADD CONSTRAINT fk_marks_finalizations_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_finalizations ADD CONSTRAINT fk_marks_finalizations_finalized_by FOREIGN KEY (tenant_id, finalized_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_finalizations ADD CONSTRAINT fk_marks_finalizations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.marks_finalizations ADD CONSTRAINT fk_marks_finalizations_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.question_marks ADD CONSTRAINT fk_question_marks_marks_entry_id FOREIGN KEY (tenant_id, marks_entry_id) REFERENCES exm.marks_entries(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.question_marks ADD CONSTRAINT fk_question_marks_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.registration_corrections ADD CONSTRAINT fk_registration_corrections_approved_by FOREIGN KEY (tenant_id, approved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.registration_corrections ADD CONSTRAINT fk_registration_corrections_registration_id FOREIGN KEY (tenant_id, registration_id) REFERENCES exm.registrations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.registration_corrections ADD CONSTRAINT fk_registration_corrections_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.registration_subjects ADD CONSTRAINT fk_registration_subjects_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.registration_subjects ADD CONSTRAINT fk_registration_subjects_registration_id FOREIGN KEY (tenant_id, registration_id) REFERENCES exm.registrations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.registration_subjects ADD CONSTRAINT fk_registration_subjects_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.registrations ADD CONSTRAINT fk_registrations_batch_id FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.registrations ADD CONSTRAINT fk_registrations_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.registrations ADD CONSTRAINT fk_registrations_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.registrations ADD CONSTRAINT fk_registrations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.registrations ADD CONSTRAINT fk_registrations_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.result_backlogs ADD CONSTRAINT fk_result_backlogs_cleared_result_id FOREIGN KEY (tenant_id, cleared_result_id) REFERENCES exm.results(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.result_backlogs ADD CONSTRAINT fk_result_backlogs_first_result_id FOREIGN KEY (tenant_id, first_result_id) REFERENCES exm.results(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.result_backlogs ADD CONSTRAINT fk_result_backlogs_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.result_backlogs ADD CONSTRAINT fk_result_backlogs_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.result_backlogs ADD CONSTRAINT fk_result_backlogs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.result_rules ADD CONSTRAINT fk_result_rules_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.result_rules ADD CONSTRAINT fk_result_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.result_semesters ADD CONSTRAINT fk_result_semesters_result_id FOREIGN KEY (tenant_id, result_id) REFERENCES exm.results(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.result_semesters ADD CONSTRAINT fk_result_semesters_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.result_semesters ADD CONSTRAINT fk_result_semesters_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.result_subjects ADD CONSTRAINT fk_result_subjects_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.result_subjects ADD CONSTRAINT fk_result_subjects_result_id FOREIGN KEY (tenant_id, result_id) REFERENCES exm.results(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.result_subjects ADD CONSTRAINT fk_result_subjects_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.results ADD CONSTRAINT fk_results_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.results ADD CONSTRAINT fk_results_registration_id_consistent FOREIGN KEY (tenant_id, registration_id, student_id, examination_id) REFERENCES exm.registrations(tenant_id, id, student_id, examination_id) ON DELETE CASCADE;
ALTER TABLE exm.results ADD CONSTRAINT fk_results_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.results ADD CONSTRAINT fk_results_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.results ADD CONSTRAINT fk_results_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT fk_revaluation_requests_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT fk_revaluation_requests_registration_id FOREIGN KEY (tenant_id, registration_id) REFERENCES exm.registrations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT fk_revaluation_requests_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.revaluation_requests ADD CONSTRAINT fk_revaluation_requests_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.schedule_conflicts ADD CONSTRAINT fk_schedule_conflicts_entry_a_id FOREIGN KEY (tenant_id, entry_a_id) REFERENCES exm.schedule_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.schedule_conflicts ADD CONSTRAINT fk_schedule_conflicts_entry_b_id FOREIGN KEY (tenant_id, entry_b_id) REFERENCES exm.schedule_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.schedule_conflicts ADD CONSTRAINT fk_schedule_conflicts_schedule_version_id FOREIGN KEY (tenant_id, schedule_version_id) REFERENCES exm.schedule_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.schedule_conflicts ADD CONSTRAINT fk_schedule_conflicts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.schedule_entries ADD CONSTRAINT fk_schedule_entries_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES exm.exam_rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.schedule_entries ADD CONSTRAINT fk_schedule_entries_schedule_version_id FOREIGN KEY (tenant_id, schedule_version_id) REFERENCES exm.schedule_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.schedule_entries ADD CONSTRAINT fk_schedule_entries_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES exm.exam_sessions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.schedule_entries ADD CONSTRAINT fk_schedule_entries_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.schedule_versions ADD CONSTRAINT fk_schedule_versions_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.schedule_versions ADD CONSTRAINT fk_schedule_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.scheduling_constraints ADD CONSTRAINT fk_scheduling_constraints_examination_id FOREIGN KEY (tenant_id, examination_id) REFERENCES exm.examinations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.scheduling_constraints ADD CONSTRAINT fk_scheduling_constraints_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.script_assignments ADD CONSTRAINT fk_script_assignments_answer_script_id FOREIGN KEY (tenant_id, answer_script_id) REFERENCES exm.answer_scripts(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.script_assignments ADD CONSTRAINT fk_script_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.script_assignments ADD CONSTRAINT fk_script_assignments_valuation_assignment_id FOREIGN KEY (tenant_id, valuation_assignment_id) REFERENCES exm.valuation_assignments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.script_movements ADD CONSTRAINT fk_script_movements_answer_script_id FOREIGN KEY (tenant_id, answer_script_id) REFERENCES exm.answer_scripts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.script_movements ADD CONSTRAINT fk_script_movements_moved_by FOREIGN KEY (tenant_id, moved_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.script_movements ADD CONSTRAINT fk_script_movements_packet_id FOREIGN KEY (tenant_id, packet_id) REFERENCES exm.script_packets(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.script_movements ADD CONSTRAINT fk_script_movements_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.script_packets ADD CONSTRAINT fk_script_packets_exam_session_id FOREIGN KEY (tenant_id, exam_session_id) REFERENCES exm.exam_sessions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.script_packets ADD CONSTRAINT fk_script_packets_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES exm.exam_rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.script_packets ADD CONSTRAINT fk_script_packets_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_assignments ADD CONSTRAINT fk_valuation_assignments_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_assignments ADD CONSTRAINT fk_valuation_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_assignments ADD CONSTRAINT fk_valuation_assignments_valuation_batch_id FOREIGN KEY (tenant_id, valuation_batch_id) REFERENCES exm.valuation_batches(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE exm.valuation_batches ADD CONSTRAINT fk_valuation_batches_centre_id FOREIGN KEY (tenant_id, centre_id) REFERENCES exm.valuation_centres(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_batches ADD CONSTRAINT fk_valuation_batches_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_batches ADD CONSTRAINT fk_valuation_batches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_centres ADD CONSTRAINT fk_valuation_centres_address_id FOREIGN KEY (tenant_id, address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_centres ADD CONSTRAINT fk_valuation_centres_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_schemes ADD CONSTRAINT fk_valuation_schemes_exam_subject_id FOREIGN KEY (tenant_id, exam_subject_id) REFERENCES exm.exam_subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_schemes ADD CONSTRAINT fk_valuation_schemes_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_sessions ADD CONSTRAINT fk_valuation_sessions_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_sessions ADD CONSTRAINT fk_valuation_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE exm.valuation_sessions ADD CONSTRAINT fk_valuation_sessions_valuation_batch_id FOREIGN KEY (tenant_id, valuation_batch_id) REFERENCES exm.valuation_batches(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.adjustments ADD CONSTRAINT fk_adjustments_journal_entry_id FOREIGN KEY (tenant_id, journal_entry_id) REFERENCES fin.journal_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.adjustments ADD CONSTRAINT fk_adjustments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.adjustments ADD CONSTRAINT fk_adjustments_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.collection_batches ADD CONSTRAINT fk_collection_batches_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.collection_batches ADD CONSTRAINT fk_collection_batches_counter_user_id FOREIGN KEY (tenant_id, counter_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.collection_batches ADD CONSTRAINT fk_collection_batches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.concession_types ADD CONSTRAINT fk_concession_types_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.disbursements ADD CONSTRAINT fk_disbursements_journal_entry_id FOREIGN KEY (tenant_id, journal_entry_id) REFERENCES fin.journal_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.disbursements ADD CONSTRAINT fk_disbursements_payment_request_id FOREIGN KEY (tenant_id, payment_request_id) REFERENCES fin.payment_requests(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.disbursements ADD CONSTRAINT fk_disbursements_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.expense_categories ADD CONSTRAINT fk_expense_categories_ledger_account_id FOREIGN KEY (tenant_id, ledger_account_id) REFERENCES fin.ledger_accounts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expense_categories ADD CONSTRAINT fk_expense_categories_parent_id FOREIGN KEY (tenant_id, parent_id) REFERENCES fin.expense_categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expense_categories ADD CONSTRAINT fk_expense_categories_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.expense_items ADD CONSTRAINT fk_expense_items_expense_id FOREIGN KEY (tenant_id, expense_id) REFERENCES fin.expenses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.expense_items ADD CONSTRAINT fk_expense_items_tax_code_id FOREIGN KEY (tenant_id, tax_code_id) REFERENCES tax.tax_codes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expense_items ADD CONSTRAINT fk_expense_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_budget_line_id FOREIGN KEY (tenant_id, budget_line_id) REFERENCES bud.budget_lines(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_category_id FOREIGN KEY (tenant_id, category_id) REFERENCES fin.expense_categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_claimant_employee_id FOREIGN KEY (tenant_id, claimant_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_vendor_id FOREIGN KEY (tenant_id, vendor_id) REFERENCES fin.vendors(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.expenses ADD CONSTRAINT fk_expenses_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_components ADD CONSTRAINT fk_fee_components_ledger_account_id FOREIGN KEY (tenant_id, ledger_account_id) REFERENCES fin.ledger_accounts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_components ADD CONSTRAINT fk_fee_components_structure_version_id FOREIGN KEY (tenant_id, structure_version_id) REFERENCES fin.fee_structure_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.fee_components ADD CONSTRAINT fk_fee_components_tax_code_id FOREIGN KEY (tenant_id, tax_code_id) REFERENCES tax.tax_codes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_components ADD CONSTRAINT fk_fee_components_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_concessions ADD CONSTRAINT fk_fee_concessions_concession_type_id FOREIGN KEY (tenant_id, concession_type_id) REFERENCES fin.concession_types(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_concessions ADD CONSTRAINT fk_fee_concessions_structure_version_id FOREIGN KEY (tenant_id, structure_version_id) REFERENCES fin.fee_structure_versions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_concessions ADD CONSTRAINT fk_fee_concessions_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_concessions ADD CONSTRAINT fk_fee_concessions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_concessions ADD CONSTRAINT fk_fee_concessions_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_installments ADD CONSTRAINT fk_fee_installments_fee_period_id FOREIGN KEY (tenant_id, fee_period_id) REFERENCES fin.fee_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_installments ADD CONSTRAINT fk_fee_installments_structure_version_id FOREIGN KEY (tenant_id, structure_version_id) REFERENCES fin.fee_structure_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.fee_installments ADD CONSTRAINT fk_fee_installments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT fk_fee_obligation_items_fee_component_id FOREIGN KEY (tenant_id, fee_component_id) REFERENCES fin.fee_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT fk_fee_obligation_items_obligation_id FOREIGN KEY (tenant_id, obligation_id) REFERENCES fin.fee_obligations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.fee_obligation_items ADD CONSTRAINT fk_fee_obligation_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_obligations ADD CONSTRAINT fk_fee_obligations_fee_period_id FOREIGN KEY (tenant_id, fee_period_id) REFERENCES fin.fee_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_obligations ADD CONSTRAINT fk_fee_obligations_installment_id FOREIGN KEY (tenant_id, installment_id) REFERENCES fin.fee_installments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_obligations ADD CONSTRAINT fk_fee_obligations_structure_version_id FOREIGN KEY (tenant_id, structure_version_id) REFERENCES fin.fee_structure_versions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_obligations ADD CONSTRAINT fk_fee_obligations_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_obligations ADD CONSTRAINT fk_fee_obligations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_periods ADD CONSTRAINT fk_fee_periods_academic_year_id FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_periods ADD CONSTRAINT fk_fee_periods_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_periods ADD CONSTRAINT fk_fee_periods_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_rules ADD CONSTRAINT fk_fee_rules_structure_version_id FOREIGN KEY (tenant_id, structure_version_id) REFERENCES fin.fee_structure_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.fee_rules ADD CONSTRAINT fk_fee_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_structure_programs ADD CONSTRAINT fk_fee_structure_programs_batch_id FOREIGN KEY (tenant_id, batch_id) REFERENCES acd.batches(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_structure_programs ADD CONSTRAINT fk_fee_structure_programs_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.fee_structure_programs ADD CONSTRAINT fk_fee_structure_programs_structure_id FOREIGN KEY (tenant_id, structure_id) REFERENCES fin.fee_structures(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.fee_structure_programs ADD CONSTRAINT fk_fee_structure_programs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT fk_fee_structure_versions_structure_id FOREIGN KEY (tenant_id, structure_id) REFERENCES fin.fee_structures(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.fee_structure_versions ADD CONSTRAINT fk_fee_structure_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_structures ADD CONSTRAINT fk_fee_structures_academic_year_id FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_structures ADD CONSTRAINT fk_fee_structures_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_structures ADD CONSTRAINT fk_fee_structures_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.fee_structures ADD CONSTRAINT fk_fee_structures_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.gateway_events ADD CONSTRAINT fk_gateway_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.gateway_transactions ADD CONSTRAINT fk_gateway_transactions_payment_id FOREIGN KEY (tenant_id, payment_id) REFERENCES fin.payments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.gateway_transactions ADD CONSTRAINT fk_gateway_transactions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.journal_entries ADD CONSTRAINT fk_journal_entries_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.journal_entries ADD CONSTRAINT fk_journal_entries_reverses_entry_id FOREIGN KEY (tenant_id, reverses_entry_id) REFERENCES fin.journal_entries(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.journal_entries ADD CONSTRAINT fk_journal_entries_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.journal_lines ADD CONSTRAINT fk_journal_lines_account_id FOREIGN KEY (tenant_id, account_id) REFERENCES fin.ledger_accounts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.journal_lines ADD CONSTRAINT fk_journal_lines_cost_centre_id FOREIGN KEY (tenant_id, cost_centre_id) REFERENCES bud.cost_centres(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.journal_lines ADD CONSTRAINT fk_journal_lines_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.journal_lines ADD CONSTRAINT fk_journal_lines_entry_id FOREIGN KEY (tenant_id, entry_id) REFERENCES fin.journal_entries(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.journal_lines ADD CONSTRAINT fk_journal_lines_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.ledger_accounts ADD CONSTRAINT fk_ledger_accounts_parent_account_id FOREIGN KEY (tenant_id, parent_account_id) REFERENCES fin.ledger_accounts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.ledger_accounts ADD CONSTRAINT fk_ledger_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_allocations ADD CONSTRAINT fk_payment_allocations_obligation_id FOREIGN KEY (tenant_id, obligation_id) REFERENCES fin.fee_obligations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_allocations ADD CONSTRAINT fk_payment_allocations_obligation_item_id FOREIGN KEY (tenant_id, obligation_item_id) REFERENCES fin.fee_obligation_items(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_allocations ADD CONSTRAINT fk_payment_allocations_payment_id FOREIGN KEY (tenant_id, payment_id) REFERENCES fin.payments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_allocations ADD CONSTRAINT fk_payment_allocations_reverses_allocation_id FOREIGN KEY (tenant_id, reverses_allocation_id) REFERENCES fin.payment_allocations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_allocations ADD CONSTRAINT fk_payment_allocations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_methods ADD CONSTRAINT fk_payment_methods_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_requests ADD CONSTRAINT fk_payment_requests_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_requests ADD CONSTRAINT fk_payment_requests_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.payment_requests ADD CONSTRAINT fk_payment_requests_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payments ADD CONSTRAINT fk_payments_collection_batch_id FOREIGN KEY (tenant_id, collection_batch_id) REFERENCES fin.collection_batches(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payments ADD CONSTRAINT fk_payments_payer_person_id FOREIGN KEY (tenant_id, payer_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payments ADD CONSTRAINT fk_payments_payment_method_id FOREIGN KEY (tenant_id, payment_method_id) REFERENCES fin.payment_methods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payments ADD CONSTRAINT fk_payments_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.payments ADD CONSTRAINT fk_payments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.receipt_items ADD CONSTRAINT fk_receipt_items_obligation_item_id FOREIGN KEY (tenant_id, obligation_item_id) REFERENCES fin.fee_obligation_items(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.receipt_items ADD CONSTRAINT fk_receipt_items_receipt_id FOREIGN KEY (tenant_id, receipt_id) REFERENCES fin.receipts(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.receipt_items ADD CONSTRAINT fk_receipt_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.receipts ADD CONSTRAINT fk_receipts_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.receipts ADD CONSTRAINT fk_receipts_payer_person_id FOREIGN KEY (tenant_id, payer_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.receipts ADD CONSTRAINT fk_receipts_payment_id_consistent FOREIGN KEY (tenant_id, payment_id, student_id) REFERENCES fin.payments(tenant_id, id, student_id) ON DELETE RESTRICT;
ALTER TABLE fin.receipts ADD CONSTRAINT fk_receipts_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.receipts ADD CONSTRAINT fk_receipts_supersedes_receipt_id FOREIGN KEY (tenant_id, supersedes_receipt_id) REFERENCES fin.receipts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.receipts ADD CONSTRAINT fk_receipts_template_id FOREIGN KEY (tenant_id, template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.receipts ADD CONSTRAINT fk_receipts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.reconciliations ADD CONSTRAINT fk_reconciliations_payment_id FOREIGN KEY (tenant_id, payment_id) REFERENCES fin.payments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.reconciliations ADD CONSTRAINT fk_reconciliations_reconciled_by FOREIGN KEY (tenant_id, reconciled_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.reconciliations ADD CONSTRAINT fk_reconciliations_settlement_id FOREIGN KEY (tenant_id, settlement_id) REFERENCES fin.settlements(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.reconciliations ADD CONSTRAINT fk_reconciliations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.refunds ADD CONSTRAINT fk_refunds_payment_id FOREIGN KEY (tenant_id, payment_id) REFERENCES fin.payments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.refunds ADD CONSTRAINT fk_refunds_payment_request_id FOREIGN KEY (tenant_id, payment_request_id) REFERENCES fin.payment_requests(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.refunds ADD CONSTRAINT fk_refunds_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.refunds ADD CONSTRAINT fk_refunds_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_application_periods ADD CONSTRAINT fk_scholarship_application_periods_academic_year_id FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_application_periods ADD CONSTRAINT fk_scholarship_application_periods_scheme_id FOREIGN KEY (tenant_id, scheme_id) REFERENCES fin.scholarship_schemes(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.scholarship_application_periods ADD CONSTRAINT fk_scholarship_application_periods_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT fk_scholarship_applications_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES fin.scholarship_application_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT fk_scholarship_applications_scheme_id FOREIGN KEY (tenant_id, scheme_id) REFERENCES fin.scholarship_schemes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT fk_scholarship_applications_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT fk_scholarship_applications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_applications ADD CONSTRAINT fk_scholarship_applications_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_award_components ADD CONSTRAINT fk_scholarship_award_components_award_id FOREIGN KEY (tenant_id, award_id) REFERENCES fin.scholarship_awards(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.scholarship_award_components ADD CONSTRAINT fk_scholarship_award_components_benefit_id FOREIGN KEY (tenant_id, benefit_id) REFERENCES fin.scholarship_benefits(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_award_components ADD CONSTRAINT fk_scholarship_award_components_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_award_events ADD CONSTRAINT fk_scholarship_award_events_award_id FOREIGN KEY (tenant_id, award_id) REFERENCES fin.scholarship_awards(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.scholarship_award_events ADD CONSTRAINT fk_scholarship_award_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_award_events ADD CONSTRAINT fk_scholarship_award_events_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT fk_scholarship_awards_application_id FOREIGN KEY (tenant_id, application_id) REFERENCES fin.scholarship_applications(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT fk_scholarship_awards_scheme_id FOREIGN KEY (tenant_id, scheme_id) REFERENCES fin.scholarship_schemes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT fk_scholarship_awards_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_awards ADD CONSTRAINT fk_scholarship_awards_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT fk_scholarship_benefits_scheme_id FOREIGN KEY (tenant_id, scheme_id) REFERENCES fin.scholarship_schemes(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.scholarship_benefits ADD CONSTRAINT fk_scholarship_benefits_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_criteria ADD CONSTRAINT fk_scholarship_criteria_scheme_id FOREIGN KEY (tenant_id, scheme_id) REFERENCES fin.scholarship_schemes(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.scholarship_criteria ADD CONSTRAINT fk_scholarship_criteria_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_evaluations ADD CONSTRAINT fk_scholarship_evaluations_application_id FOREIGN KEY (tenant_id, application_id) REFERENCES fin.scholarship_applications(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.scholarship_evaluations ADD CONSTRAINT fk_scholarship_evaluations_evaluated_by FOREIGN KEY (tenant_id, evaluated_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_evaluations ADD CONSTRAINT fk_scholarship_evaluations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT fk_scholarship_schemes_academic_year_id FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT fk_scholarship_schemes_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_schemes ADD CONSTRAINT fk_scholarship_schemes_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_utilizations ADD CONSTRAINT fk_scholarship_utilizations_award_component_id FOREIGN KEY (tenant_id, award_component_id) REFERENCES fin.scholarship_award_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_utilizations ADD CONSTRAINT fk_scholarship_utilizations_obligation_id FOREIGN KEY (tenant_id, obligation_id) REFERENCES fin.fee_obligations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.scholarship_utilizations ADD CONSTRAINT fk_scholarship_utilizations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.settlements ADD CONSTRAINT fk_settlements_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.vendor_bank_accounts ADD CONSTRAINT fk_vendor_bank_accounts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.vendor_bank_accounts ADD CONSTRAINT fk_vendor_bank_accounts_vendor_id FOREIGN KEY (tenant_id, vendor_id) REFERENCES fin.vendors(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT fk_vendor_invoices_expense_id FOREIGN KEY (tenant_id, expense_id) REFERENCES fin.expenses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT fk_vendor_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE fin.vendor_invoices ADD CONSTRAINT fk_vendor_invoices_vendor_id FOREIGN KEY (tenant_id, vendor_id) REFERENCES fin.vendors(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.vendors ADD CONSTRAINT fk_vendors_address_id FOREIGN KEY (tenant_id, address_id) REFERENCES core.addresses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.vendors ADD CONSTRAINT fk_vendors_contact_person_id FOREIGN KEY (tenant_id, contact_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE fin.vendors ADD CONSTRAINT fk_vendors_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_days ADD CONSTRAINT fk_attendance_days_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_days ADD CONSTRAINT fk_attendance_days_leave_application_id FOREIGN KEY (tenant_id, leave_application_id) REFERENCES hrm.leave_applications(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_days ADD CONSTRAINT fk_attendance_days_shift_id FOREIGN KEY (tenant_id, shift_id) REFERENCES hrm.shifts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_days ADD CONSTRAINT fk_attendance_days_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_policies ADD CONSTRAINT fk_attendance_policies_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_punches ADD CONSTRAINT fk_attendance_punches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_punches_default ADD CONSTRAINT fk_attendance_punches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_punches_p202610 ADD CONSTRAINT fk_attendance_punches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_punches_p202611 ADD CONSTRAINT fk_attendance_punches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_punches_p202612 ADD CONSTRAINT fk_attendance_punches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_punches_p202701 ADD CONSTRAINT fk_attendance_punches_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_regularizations ADD CONSTRAINT fk_attendance_regularizations_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_regularizations ADD CONSTRAINT fk_attendance_regularizations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.attendance_regularizations ADD CONSTRAINT fk_attendance_regularizations_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT fk_candidate_applications_candidate_id FOREIGN KEY (tenant_id, candidate_id) REFERENCES hrm.candidates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT fk_candidate_applications_posting_id FOREIGN KEY (tenant_id, posting_id) REFERENCES hrm.job_postings(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT fk_candidate_applications_screened_by FOREIGN KEY (tenant_id, screened_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidate_applications ADD CONSTRAINT fk_candidate_applications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidates ADD CONSTRAINT fk_candidates_converted_employee_id FOREIGN KEY (tenant_id, converted_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidates ADD CONSTRAINT fk_candidates_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidates ADD CONSTRAINT fk_candidates_source_channel_id FOREIGN KEY (tenant_id, source_channel_id) REFERENCES hrm.recruitment_channels(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.candidates ADD CONSTRAINT fk_candidates_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.competencies ADD CONSTRAINT fk_competencies_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.competency_assessments ADD CONSTRAINT fk_competency_assessments_competency_id FOREIGN KEY (tenant_id, competency_id) REFERENCES hrm.competencies(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.competency_assessments ADD CONSTRAINT fk_competency_assessments_rating_level_id FOREIGN KEY (tenant_id, rating_level_id) REFERENCES hrm.rating_scale_levels(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.competency_assessments ADD CONSTRAINT fk_competency_assessments_review_id FOREIGN KEY (tenant_id, review_id) REFERENCES hrm.performance_reviews(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.competency_assessments ADD CONSTRAINT fk_competency_assessments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.designations ADD CONSTRAINT fk_designations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT fk_employee_assignments_campus_id FOREIGN KEY (tenant_id, campus_id) REFERENCES core.campuses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT fk_employee_assignments_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT fk_employee_assignments_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT fk_employee_assignments_designation_id FOREIGN KEY (tenant_id, designation_id) REFERENCES hrm.designations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT fk_employee_assignments_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.employee_assignments ADD CONSTRAINT fk_employee_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_learning_paths ADD CONSTRAINT fk_employee_learning_paths_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_learning_paths ADD CONSTRAINT fk_employee_learning_paths_path_id FOREIGN KEY (tenant_id, path_id) REFERENCES hrm.learning_paths(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_learning_paths ADD CONSTRAINT fk_employee_learning_paths_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_skills ADD CONSTRAINT fk_employee_skills_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_skills ADD CONSTRAINT fk_employee_skills_skill_id FOREIGN KEY (tenant_id, skill_id) REFERENCES hrm.skills(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employee_skills ADD CONSTRAINT fk_employee_skills_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.employees ADD CONSTRAINT fk_employees_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employees ADD CONSTRAINT fk_employees_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employees ADD CONSTRAINT fk_employees_designation_id FOREIGN KEY (tenant_id, designation_id) REFERENCES hrm.designations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employees ADD CONSTRAINT fk_employees_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employees ADD CONSTRAINT fk_employees_reporting_manager_id FOREIGN KEY (tenant_id, reporting_manager_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.employees ADD CONSTRAINT fk_employees_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.employment_events ADD CONSTRAINT fk_employment_events_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.employment_events ADD CONSTRAINT fk_employment_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.employment_events ADD CONSTRAINT fk_employment_events_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.goal_metrics ADD CONSTRAINT fk_goal_metrics_goal_id FOREIGN KEY (tenant_id, goal_id) REFERENCES hrm.performance_goals(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.goal_metrics ADD CONSTRAINT fk_goal_metrics_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.goal_milestones ADD CONSTRAINT fk_goal_milestones_goal_id FOREIGN KEY (tenant_id, goal_id) REFERENCES hrm.performance_goals(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.goal_milestones ADD CONSTRAINT fk_goal_milestones_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.improvement_plans ADD CONSTRAINT fk_improvement_plans_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.improvement_plans ADD CONSTRAINT fk_improvement_plans_plan_id FOREIGN KEY (tenant_id, plan_id) REFERENCES hrm.performance_plans(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.improvement_plans ADD CONSTRAINT fk_improvement_plans_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.interview_evaluations ADD CONSTRAINT fk_interview_evaluations_interview_id FOREIGN KEY (tenant_id, interview_id) REFERENCES hrm.interviews(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.interview_evaluations ADD CONSTRAINT fk_interview_evaluations_panelist_employee_id FOREIGN KEY (tenant_id, panelist_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.interview_evaluations ADD CONSTRAINT fk_interview_evaluations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.interview_panelists ADD CONSTRAINT fk_interview_panelists_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.interview_panelists ADD CONSTRAINT fk_interview_panelists_interview_id FOREIGN KEY (tenant_id, interview_id) REFERENCES hrm.interviews(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.interview_panelists ADD CONSTRAINT fk_interview_panelists_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.interview_rounds ADD CONSTRAINT fk_interview_rounds_posting_id FOREIGN KEY (tenant_id, posting_id) REFERENCES hrm.job_postings(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.interview_rounds ADD CONSTRAINT fk_interview_rounds_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.interviews ADD CONSTRAINT fk_interviews_application_id FOREIGN KEY (tenant_id, application_id) REFERENCES hrm.candidate_applications(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.interviews ADD CONSTRAINT fk_interviews_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES core.rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.interviews ADD CONSTRAINT fk_interviews_round_id FOREIGN KEY (tenant_id, round_id) REFERENCES hrm.interview_rounds(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.interviews ADD CONSTRAINT fk_interviews_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.job_descriptions ADD CONSTRAINT fk_job_descriptions_designation_id FOREIGN KEY (tenant_id, designation_id) REFERENCES hrm.designations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.job_descriptions ADD CONSTRAINT fk_job_descriptions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.job_postings ADD CONSTRAINT fk_job_postings_channel_id FOREIGN KEY (tenant_id, channel_id) REFERENCES hrm.recruitment_channels(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.job_postings ADD CONSTRAINT fk_job_postings_job_description_id FOREIGN KEY (tenant_id, job_description_id) REFERENCES hrm.job_descriptions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.job_postings ADD CONSTRAINT fk_job_postings_requisition_id FOREIGN KEY (tenant_id, requisition_id) REFERENCES hrm.requisitions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.job_postings ADD CONSTRAINT fk_job_postings_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.learning_path_courses ADD CONSTRAINT fk_learning_path_courses_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES hrm.training_courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.learning_path_courses ADD CONSTRAINT fk_learning_path_courses_path_id FOREIGN KEY (tenant_id, path_id) REFERENCES hrm.learning_paths(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.learning_path_courses ADD CONSTRAINT fk_learning_path_courses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.learning_paths ADD CONSTRAINT fk_learning_paths_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_application_days ADD CONSTRAINT fk_leave_application_days_application_id FOREIGN KEY (tenant_id, application_id) REFERENCES hrm.leave_applications(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.leave_application_days ADD CONSTRAINT fk_leave_application_days_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_applications ADD CONSTRAINT fk_leave_applications_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_applications ADD CONSTRAINT fk_leave_applications_leave_type_id FOREIGN KEY (tenant_id, leave_type_id) REFERENCES hrm.leave_types(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_applications ADD CONSTRAINT fk_leave_applications_substitute_employee_id FOREIGN KEY (tenant_id, substitute_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_applications ADD CONSTRAINT fk_leave_applications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_applications ADD CONSTRAINT fk_leave_applications_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT fk_leave_entitlements_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT fk_leave_entitlements_leave_type_id FOREIGN KEY (tenant_id, leave_type_id) REFERENCES hrm.leave_types(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT fk_leave_entitlements_policy_id FOREIGN KEY (tenant_id, policy_id) REFERENCES hrm.leave_policies(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_entitlements ADD CONSTRAINT fk_leave_entitlements_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_ledger ADD CONSTRAINT fk_leave_ledger_application_id FOREIGN KEY (tenant_id, application_id) REFERENCES hrm.leave_applications(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_ledger ADD CONSTRAINT fk_leave_ledger_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_ledger ADD CONSTRAINT fk_leave_ledger_leave_type_id FOREIGN KEY (tenant_id, leave_type_id) REFERENCES hrm.leave_types(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_ledger ADD CONSTRAINT fk_leave_ledger_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_policies ADD CONSTRAINT fk_leave_policies_leave_type_id FOREIGN KEY (tenant_id, leave_type_id) REFERENCES hrm.leave_types(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_policies ADD CONSTRAINT fk_leave_policies_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.leave_types ADD CONSTRAINT fk_leave_types_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.offers ADD CONSTRAINT fk_offers_application_id FOREIGN KEY (tenant_id, application_id) REFERENCES hrm.candidate_applications(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.offers ADD CONSTRAINT fk_offers_designation_id FOREIGN KEY (tenant_id, designation_id) REFERENCES hrm.designations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.offers ADD CONSTRAINT fk_offers_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.offers ADD CONSTRAINT fk_offers_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.offers ADD CONSTRAINT fk_offers_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.onboarding_tasks ADD CONSTRAINT fk_onboarding_tasks_assignee_id FOREIGN KEY (tenant_id, assignee_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.onboarding_tasks ADD CONSTRAINT fk_onboarding_tasks_onboarding_id FOREIGN KEY (tenant_id, onboarding_id) REFERENCES hrm.onboardings(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.onboarding_tasks ADD CONSTRAINT fk_onboarding_tasks_owner_role_id FOREIGN KEY (tenant_id, owner_role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.onboarding_tasks ADD CONSTRAINT fk_onboarding_tasks_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.onboardings ADD CONSTRAINT fk_onboardings_candidate_id FOREIGN KEY (tenant_id, candidate_id) REFERENCES hrm.candidates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.onboardings ADD CONSTRAINT fk_onboardings_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.onboardings ADD CONSTRAINT fk_onboardings_offer_id FOREIGN KEY (tenant_id, offer_id) REFERENCES hrm.offers(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.onboardings ADD CONSTRAINT fk_onboardings_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT fk_performance_calibrations_calibrated_by FOREIGN KEY (tenant_id, calibrated_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT fk_performance_calibrations_calibrated_level_id FOREIGN KEY (tenant_id, calibrated_level_id) REFERENCES hrm.rating_scale_levels(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT fk_performance_calibrations_cycle_id FOREIGN KEY (tenant_id, cycle_id) REFERENCES hrm.performance_cycles(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT fk_performance_calibrations_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT fk_performance_calibrations_original_level_id FOREIGN KEY (tenant_id, original_level_id) REFERENCES hrm.rating_scale_levels(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_calibrations ADD CONSTRAINT fk_performance_calibrations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_cycles ADD CONSTRAINT fk_performance_cycles_academic_year_id FOREIGN KEY (tenant_id, academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_cycles ADD CONSTRAINT fk_performance_cycles_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_cycles ADD CONSTRAINT fk_performance_cycles_rating_scale_id FOREIGN KEY (tenant_id, rating_scale_id) REFERENCES hrm.rating_scales(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_cycles ADD CONSTRAINT fk_performance_cycles_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_feedback ADD CONSTRAINT fk_performance_feedback_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_feedback ADD CONSTRAINT fk_performance_feedback_given_by_employee_id FOREIGN KEY (tenant_id, given_by_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_feedback ADD CONSTRAINT fk_performance_feedback_plan_id FOREIGN KEY (tenant_id, plan_id) REFERENCES hrm.performance_plans(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_feedback ADD CONSTRAINT fk_performance_feedback_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_goals ADD CONSTRAINT fk_performance_goals_parent_goal_id FOREIGN KEY (tenant_id, parent_goal_id) REFERENCES hrm.performance_goals(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_goals ADD CONSTRAINT fk_performance_goals_plan_id FOREIGN KEY (tenant_id, plan_id) REFERENCES hrm.performance_plans(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.performance_goals ADD CONSTRAINT fk_performance_goals_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_plans ADD CONSTRAINT fk_performance_plans_cycle_id FOREIGN KEY (tenant_id, cycle_id) REFERENCES hrm.performance_cycles(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.performance_plans ADD CONSTRAINT fk_performance_plans_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_plans ADD CONSTRAINT fk_performance_plans_manager_id FOREIGN KEY (tenant_id, manager_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_plans ADD CONSTRAINT fk_performance_plans_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_plans ADD CONSTRAINT fk_performance_plans_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT fk_performance_reviews_plan_id FOREIGN KEY (tenant_id, plan_id) REFERENCES hrm.performance_plans(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT fk_performance_reviews_rating_level_id FOREIGN KEY (tenant_id, rating_level_id) REFERENCES hrm.rating_scale_levels(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT fk_performance_reviews_reviewer_employee_id FOREIGN KEY (tenant_id, reviewer_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.performance_reviews ADD CONSTRAINT fk_performance_reviews_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.rating_scale_levels ADD CONSTRAINT fk_rating_scale_levels_scale_id FOREIGN KEY (tenant_id, scale_id) REFERENCES hrm.rating_scales(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.rating_scale_levels ADD CONSTRAINT fk_rating_scale_levels_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.rating_scales ADD CONSTRAINT fk_rating_scales_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.recruitment_channels ADD CONSTRAINT fk_recruitment_channels_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.requisitions ADD CONSTRAINT fk_requisitions_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.requisitions ADD CONSTRAINT fk_requisitions_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.requisitions ADD CONSTRAINT fk_requisitions_designation_id FOREIGN KEY (tenant_id, designation_id) REFERENCES hrm.designations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.requisitions ADD CONSTRAINT fk_requisitions_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.requisitions ADD CONSTRAINT fk_requisitions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.requisitions ADD CONSTRAINT fk_requisitions_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.shift_assignments ADD CONSTRAINT fk_shift_assignments_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.shift_assignments ADD CONSTRAINT fk_shift_assignments_shift_id FOREIGN KEY (tenant_id, shift_id) REFERENCES hrm.shifts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.shift_assignments ADD CONSTRAINT fk_shift_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.shifts ADD CONSTRAINT fk_shifts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.skills ADD CONSTRAINT fk_skills_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_assessment_attempts ADD CONSTRAINT fk_training_assessment_attempts_assessment_id FOREIGN KEY (tenant_id, assessment_id) REFERENCES hrm.training_assessments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_assessment_attempts ADD CONSTRAINT fk_training_assessment_attempts_enrollment_id FOREIGN KEY (tenant_id, enrollment_id) REFERENCES hrm.training_enrollments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_assessment_attempts ADD CONSTRAINT fk_training_assessment_attempts_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_assessments ADD CONSTRAINT fk_training_assessments_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES hrm.training_courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_assessments ADD CONSTRAINT fk_training_assessments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_attendance ADD CONSTRAINT fk_training_attendance_enrollment_id FOREIGN KEY (tenant_id, enrollment_id) REFERENCES hrm.training_enrollments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_attendance ADD CONSTRAINT fk_training_attendance_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES hrm.training_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_attendance ADD CONSTRAINT fk_training_attendance_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_completions ADD CONSTRAINT fk_training_completions_certificate_document_id FOREIGN KEY (tenant_id, certificate_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_completions ADD CONSTRAINT fk_training_completions_enrollment_id FOREIGN KEY (tenant_id, enrollment_id) REFERENCES hrm.training_enrollments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_completions ADD CONSTRAINT fk_training_completions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_course_skills ADD CONSTRAINT fk_training_course_skills_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES hrm.training_courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_course_skills ADD CONSTRAINT fk_training_course_skills_skill_id FOREIGN KEY (tenant_id, skill_id) REFERENCES hrm.skills(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_course_skills ADD CONSTRAINT fk_training_course_skills_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_courses ADD CONSTRAINT fk_training_courses_certificate_template_id FOREIGN KEY (tenant_id, certificate_template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_courses ADD CONSTRAINT fk_training_courses_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES hrm.training_programs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_courses ADD CONSTRAINT fk_training_courses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT fk_training_enrollments_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES hrm.training_courses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT fk_training_enrollments_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT fk_training_enrollments_nominated_by FOREIGN KEY (tenant_id, nominated_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT fk_training_enrollments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_enrollments ADD CONSTRAINT fk_training_enrollments_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_programs ADD CONSTRAINT fk_training_programs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_sessions ADD CONSTRAINT fk_training_sessions_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES hrm.training_courses(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_sessions ADD CONSTRAINT fk_training_sessions_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES core.rooms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_sessions ADD CONSTRAINT fk_training_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_trainers ADD CONSTRAINT fk_training_trainers_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE hrm.training_trainers ADD CONSTRAINT fk_training_trainers_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES hrm.training_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE hrm.training_trainers ADD CONSTRAINT fk_training_trainers_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.access_review_items ADD CONSTRAINT fk_access_review_items_review_id FOREIGN KEY (tenant_id, review_id) REFERENCES iam.access_reviews(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.access_review_items ADD CONSTRAINT fk_access_review_items_role_assignment_id FOREIGN KEY (tenant_id, role_assignment_id) REFERENCES iam.role_assignments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.access_review_items ADD CONSTRAINT fk_access_review_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.access_reviews ADD CONSTRAINT fk_access_reviews_reviewer_id FOREIGN KEY (tenant_id, reviewer_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.access_reviews ADD CONSTRAINT fk_access_reviews_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.api_client_grants ADD CONSTRAINT fk_api_client_grants_client_id FOREIGN KEY (tenant_id, client_id) REFERENCES iam.api_clients(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.api_client_grants ADD CONSTRAINT fk_api_client_grants_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.api_client_grants ADD CONSTRAINT fk_api_client_grants_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.api_client_grants ADD CONSTRAINT fk_api_client_grants_permission_id FOREIGN KEY (permission_id) REFERENCES iam.permissions(id) ON DELETE RESTRICT;
ALTER TABLE iam.api_client_grants ADD CONSTRAINT fk_api_client_grants_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.api_clients ADD CONSTRAINT fk_api_clients_owner_user_id FOREIGN KEY (tenant_id, owner_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.api_clients ADD CONSTRAINT fk_api_clients_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.api_clients ADD CONSTRAINT fk_api_clients_user_id FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE RESTRICT;
ALTER TABLE iam.delegations ADD CONSTRAINT fk_delegations_from_user_id FOREIGN KEY (tenant_id, from_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.delegations ADD CONSTRAINT fk_delegations_role_id FOREIGN KEY (tenant_id, role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.delegations ADD CONSTRAINT fk_delegations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.delegations ADD CONSTRAINT fk_delegations_to_user_id FOREIGN KEY (tenant_id, to_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.department_access ADD CONSTRAINT fk_department_access_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.department_access ADD CONSTRAINT fk_department_access_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.department_access ADD CONSTRAINT fk_department_access_user_id FOREIGN KEY (tenant_id, user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.role_assignments ADD CONSTRAINT fk_role_assignments_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.role_assignments ADD CONSTRAINT fk_role_assignments_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.role_assignments ADD CONSTRAINT fk_role_assignments_granted_by FOREIGN KEY (tenant_id, granted_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.role_assignments ADD CONSTRAINT fk_role_assignments_group_id FOREIGN KEY (tenant_id, group_id) REFERENCES iam.user_groups(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.role_assignments ADD CONSTRAINT fk_role_assignments_role_id FOREIGN KEY (tenant_id, role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.role_assignments ADD CONSTRAINT fk_role_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.role_assignments ADD CONSTRAINT fk_role_assignments_user_id FOREIGN KEY (tenant_id, user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.role_permissions ADD CONSTRAINT fk_role_permissions_permission_id FOREIGN KEY (permission_id) REFERENCES iam.permissions(id) ON DELETE RESTRICT;
ALTER TABLE iam.role_permissions ADD CONSTRAINT fk_role_permissions_role_id FOREIGN KEY (tenant_id, role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.role_permissions ADD CONSTRAINT fk_role_permissions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.roles ADD CONSTRAINT fk_roles_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.roles ADD CONSTRAINT fk_roles_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.transition_log ADD CONSTRAINT fk_transition_log_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.user_group_members ADD CONSTRAINT fk_user_group_members_group_id FOREIGN KEY (tenant_id, group_id) REFERENCES iam.user_groups(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.user_group_members ADD CONSTRAINT fk_user_group_members_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.user_group_members ADD CONSTRAINT fk_user_group_members_user_id FOREIGN KEY (tenant_id, user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE iam.user_groups ADD CONSTRAINT fk_user_groups_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE iam.user_profiles ADD CONSTRAINT fk_user_profiles_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.user_profiles ADD CONSTRAINT fk_user_profiles_department_id FOREIGN KEY (tenant_id, department_id) REFERENCES core.departments(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.user_profiles ADD CONSTRAINT fk_user_profiles_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE iam.user_profiles ADD CONSTRAINT fk_user_profiles_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.access_rules ADD CONSTRAINT fk_access_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.assignment_questions ADD CONSTRAINT fk_assignment_questions_assignment_id FOREIGN KEY (tenant_id, assignment_id) REFERENCES lms.assignments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.assignment_questions ADD CONSTRAINT fk_assignment_questions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.assignment_resources ADD CONSTRAINT fk_assignment_resources_assignment_id FOREIGN KEY (tenant_id, assignment_id) REFERENCES lms.assignments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.assignment_resources ADD CONSTRAINT fk_assignment_resources_resource_id FOREIGN KEY (tenant_id, resource_id) REFERENCES lms.resources(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.assignment_resources ADD CONSTRAINT fk_assignment_resources_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.assignments ADD CONSTRAINT fk_assignments_assessment_component_id FOREIGN KEY (tenant_id, assessment_component_id) REFERENCES acd.assessment_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.assignments ADD CONSTRAINT fk_assignments_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.assignments ADD CONSTRAINT fk_assignments_created_by_employee_id FOREIGN KEY (tenant_id, created_by_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.assignments ADD CONSTRAINT fk_assignments_rubric_id FOREIGN KEY (tenant_id, rubric_id) REFERENCES lms.rubrics(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.assignments ADD CONSTRAINT fk_assignments_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.assignments ADD CONSTRAINT fk_assignments_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.assignments ADD CONSTRAINT fk_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.badge_awards ADD CONSTRAINT fk_badge_awards_badge_id FOREIGN KEY (tenant_id, badge_id) REFERENCES lms.badges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.badge_awards ADD CONSTRAINT fk_badge_awards_credential_id FOREIGN KEY (tenant_id, credential_id) REFERENCES lms.credentials(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.badge_awards ADD CONSTRAINT fk_badge_awards_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.badge_awards ADD CONSTRAINT fk_badge_awards_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.badge_rules ADD CONSTRAINT fk_badge_rules_badge_id FOREIGN KEY (tenant_id, badge_id) REFERENCES lms.badges(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.badge_rules ADD CONSTRAINT fk_badge_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.badges ADD CONSTRAINT fk_badges_icon_document_id FOREIGN KEY (tenant_id, icon_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.badges ADD CONSTRAINT fk_badges_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.breakout_room_members ADD CONSTRAINT fk_breakout_room_members_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.breakout_room_members ADD CONSTRAINT fk_breakout_room_members_room_id FOREIGN KEY (tenant_id, room_id) REFERENCES lms.breakout_rooms(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.breakout_room_members ADD CONSTRAINT fk_breakout_room_members_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.breakout_rooms ADD CONSTRAINT fk_breakout_rooms_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.virtual_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.breakout_rooms ADD CONSTRAINT fk_breakout_rooms_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.certification_applications ADD CONSTRAINT fk_certification_applications_certification_id FOREIGN KEY (tenant_id, certification_id) REFERENCES lms.certifications(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.certification_applications ADD CONSTRAINT fk_certification_applications_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.certification_applications ADD CONSTRAINT fk_certification_applications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.certification_applications ADD CONSTRAINT fk_certification_applications_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.certification_programs ADD CONSTRAINT fk_certification_programs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.certification_requirements ADD CONSTRAINT fk_certification_requirements_certification_id FOREIGN KEY (tenant_id, certification_id) REFERENCES lms.certifications(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.certification_requirements ADD CONSTRAINT fk_certification_requirements_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.certifications ADD CONSTRAINT fk_certifications_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES lms.certification_programs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.certifications ADD CONSTRAINT fk_certifications_template_id FOREIGN KEY (tenant_id, template_id) REFERENCES doc.templates(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.certifications ADD CONSTRAINT fk_certifications_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.content_dependencies ADD CONSTRAINT fk_content_dependencies_depends_on_topic_id FOREIGN KEY (tenant_id, depends_on_topic_id) REFERENCES lms.content_topics(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.content_dependencies ADD CONSTRAINT fk_content_dependencies_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.content_dependencies ADD CONSTRAINT fk_content_dependencies_topic_id FOREIGN KEY (tenant_id, topic_id) REFERENCES lms.content_topics(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.content_modules ADD CONSTRAINT fk_content_modules_content_id FOREIGN KEY (tenant_id, content_id) REFERENCES lms.contents(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.content_modules ADD CONSTRAINT fk_content_modules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.content_topics ADD CONSTRAINT fk_content_topics_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.content_topics ADD CONSTRAINT fk_content_topics_module_id FOREIGN KEY (tenant_id, module_id) REFERENCES lms.content_modules(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.content_topics ADD CONSTRAINT fk_content_topics_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.contents ADD CONSTRAINT fk_contents_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.contents ADD CONSTRAINT fk_contents_owner_employee_id FOREIGN KEY (tenant_id, owner_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.contents ADD CONSTRAINT fk_contents_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.contents ADD CONSTRAINT fk_contents_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.credential_revocations ADD CONSTRAINT fk_credential_revocations_credential_id FOREIGN KEY (tenant_id, credential_id) REFERENCES lms.credentials(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.credential_revocations ADD CONSTRAINT fk_credential_revocations_revoked_by FOREIGN KEY (tenant_id, revoked_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.credential_revocations ADD CONSTRAINT fk_credential_revocations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.credential_shares ADD CONSTRAINT fk_credential_shares_credential_id FOREIGN KEY (tenant_id, credential_id) REFERENCES lms.credentials(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.credential_shares ADD CONSTRAINT fk_credential_shares_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.credentials ADD CONSTRAINT fk_credentials_certification_id FOREIGN KEY (tenant_id, certification_id) REFERENCES lms.certifications(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.credentials ADD CONSTRAINT fk_credentials_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.credentials ADD CONSTRAINT fk_credentials_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.credentials ADD CONSTRAINT fk_credentials_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.eligibility_evaluations ADD CONSTRAINT fk_eligibility_evaluations_application_id FOREIGN KEY (tenant_id, application_id) REFERENCES lms.certification_applications(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.eligibility_evaluations ADD CONSTRAINT fk_eligibility_evaluations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.eligibility_evidence ADD CONSTRAINT fk_eligibility_evidence_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.eligibility_evidence ADD CONSTRAINT fk_eligibility_evidence_evaluation_id FOREIGN KEY (tenant_id, evaluation_id) REFERENCES lms.eligibility_evaluations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.eligibility_evidence ADD CONSTRAINT fk_eligibility_evidence_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.evaluation_items ADD CONSTRAINT fk_evaluation_items_criterion_id FOREIGN KEY (tenant_id, criterion_id) REFERENCES lms.rubric_criteria(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.evaluation_items ADD CONSTRAINT fk_evaluation_items_evaluation_id FOREIGN KEY (tenant_id, evaluation_id) REFERENCES lms.evaluations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.evaluation_items ADD CONSTRAINT fk_evaluation_items_level_id FOREIGN KEY (tenant_id, level_id) REFERENCES lms.rubric_levels(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.evaluation_items ADD CONSTRAINT fk_evaluation_items_question_id FOREIGN KEY (tenant_id, question_id) REFERENCES lms.assignment_questions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.evaluation_items ADD CONSTRAINT fk_evaluation_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.evaluations ADD CONSTRAINT fk_evaluations_evaluator_employee_id FOREIGN KEY (tenant_id, evaluator_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.evaluations ADD CONSTRAINT fk_evaluations_rubric_id FOREIGN KEY (tenant_id, rubric_id) REFERENCES lms.rubrics(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.evaluations ADD CONSTRAINT fk_evaluations_submission_id FOREIGN KEY (tenant_id, submission_id) REFERENCES lms.submissions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.evaluations ADD CONSTRAINT fk_evaluations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.grades ADD CONSTRAINT fk_grades_evaluation_id FOREIGN KEY (tenant_id, evaluation_id) REFERENCES lms.evaluations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.grades ADD CONSTRAINT fk_grades_student_assignment_id_consistent FOREIGN KEY (tenant_id, student_assignment_id, student_id) REFERENCES lms.student_assignments(tenant_id, id, student_id) ON DELETE CASCADE;
ALTER TABLE lms.grades ADD CONSTRAINT fk_grades_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.grades ADD CONSTRAINT fk_grades_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.learning_streaks ADD CONSTRAINT fk_learning_streaks_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.learning_streaks ADD CONSTRAINT fk_learning_streaks_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_activities ADD CONSTRAINT fk_lesson_activities_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.lesson_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.lesson_activities ADD CONSTRAINT fk_lesson_activities_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_content_links ADD CONSTRAINT fk_lesson_content_links_content_topic_id FOREIGN KEY (tenant_id, content_topic_id) REFERENCES lms.content_topics(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_content_links ADD CONSTRAINT fk_lesson_content_links_resource_id FOREIGN KEY (tenant_id, resource_id) REFERENCES lms.resources(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_content_links ADD CONSTRAINT fk_lesson_content_links_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_content_links ADD CONSTRAINT fk_lesson_content_links_topic_id FOREIGN KEY (tenant_id, topic_id) REFERENCES lms.lesson_plan_topics(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.lesson_deliveries ADD CONSTRAINT fk_lesson_deliveries_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.lesson_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.lesson_deliveries ADD CONSTRAINT fk_lesson_deliveries_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_faculty ADD CONSTRAINT fk_lesson_faculty_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_faculty ADD CONSTRAINT fk_lesson_faculty_plan_id FOREIGN KEY (tenant_id, plan_id) REFERENCES lms.lesson_plans(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.lesson_faculty ADD CONSTRAINT fk_lesson_faculty_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plan_modules ADD CONSTRAINT fk_lesson_plan_modules_plan_id FOREIGN KEY (tenant_id, plan_id) REFERENCES lms.lesson_plans(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.lesson_plan_modules ADD CONSTRAINT fk_lesson_plan_modules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plan_topics ADD CONSTRAINT fk_lesson_plan_topics_module_id FOREIGN KEY (tenant_id, module_id) REFERENCES lms.lesson_plan_modules(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.lesson_plan_topics ADD CONSTRAINT fk_lesson_plan_topics_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plans ADD CONSTRAINT fk_lesson_plans_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plans ADD CONSTRAINT fk_lesson_plans_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plans ADD CONSTRAINT fk_lesson_plans_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plans ADD CONSTRAINT fk_lesson_plans_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plans ADD CONSTRAINT fk_lesson_plans_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_plans ADD CONSTRAINT fk_lesson_plans_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_sessions ADD CONSTRAINT fk_lesson_sessions_plan_id FOREIGN KEY (tenant_id, plan_id) REFERENCES lms.lesson_plans(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.lesson_sessions ADD CONSTRAINT fk_lesson_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.lesson_sessions ADD CONSTRAINT fk_lesson_sessions_topic_id FOREIGN KEY (tenant_id, topic_id) REFERENCES lms.lesson_plan_topics(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.progress ADD CONSTRAINT fk_progress_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.progress ADD CONSTRAINT fk_progress_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_events ADD CONSTRAINT fk_progress_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_events_default ADD CONSTRAINT fk_progress_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_events_p202610 ADD CONSTRAINT fk_progress_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_events_p202611 ADD CONSTRAINT fk_progress_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_events_p202612 ADD CONSTRAINT fk_progress_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_events_p202701 ADD CONSTRAINT fk_progress_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_milestones ADD CONSTRAINT fk_progress_milestones_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_milestones ADD CONSTRAINT fk_progress_milestones_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_rules ADD CONSTRAINT fk_progress_rules_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_rules ADD CONSTRAINT fk_progress_rules_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.progress_rules ADD CONSTRAINT fk_progress_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_associations ADD CONSTRAINT fk_resource_associations_resource_id FOREIGN KEY (tenant_id, resource_id) REFERENCES lms.resources(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.resource_associations ADD CONSTRAINT fk_resource_associations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_categories ADD CONSTRAINT fk_resource_categories_parent_id FOREIGN KEY (tenant_id, parent_id) REFERENCES lms.resource_categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_categories ADD CONSTRAINT fk_resource_categories_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_collection_items ADD CONSTRAINT fk_resource_collection_items_collection_id FOREIGN KEY (tenant_id, collection_id) REFERENCES lms.resource_collections(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.resource_collection_items ADD CONSTRAINT fk_resource_collection_items_resource_id FOREIGN KEY (tenant_id, resource_id) REFERENCES lms.resources(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.resource_collection_items ADD CONSTRAINT fk_resource_collection_items_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_collections ADD CONSTRAINT fk_resource_collections_owner_employee_id FOREIGN KEY (tenant_id, owner_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_collections ADD CONSTRAINT fk_resource_collections_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_reviews ADD CONSTRAINT fk_resource_reviews_resource_id FOREIGN KEY (tenant_id, resource_id) REFERENCES lms.resources(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.resource_reviews ADD CONSTRAINT fk_resource_reviews_reviewer_person_id FOREIGN KEY (tenant_id, reviewer_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_reviews ADD CONSTRAINT fk_resource_reviews_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_usage_events ADD CONSTRAINT fk_resource_usage_events_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_usage_events ADD CONSTRAINT fk_resource_usage_events_resource_id FOREIGN KEY (tenant_id, resource_id) REFERENCES lms.resources(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resource_usage_events ADD CONSTRAINT fk_resource_usage_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resources ADD CONSTRAINT fk_resources_category_id FOREIGN KEY (tenant_id, category_id) REFERENCES lms.resource_categories(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resources ADD CONSTRAINT fk_resources_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resources ADD CONSTRAINT fk_resources_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resources ADD CONSTRAINT fk_resources_owner_employee_id FOREIGN KEY (tenant_id, owner_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resources ADD CONSTRAINT fk_resources_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.resubmission_requests ADD CONSTRAINT fk_resubmission_requests_decided_by FOREIGN KEY (tenant_id, decided_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.resubmission_requests ADD CONSTRAINT fk_resubmission_requests_student_assignment_id FOREIGN KEY (tenant_id, student_assignment_id) REFERENCES lms.student_assignments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.resubmission_requests ADD CONSTRAINT fk_resubmission_requests_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.rubric_criteria ADD CONSTRAINT fk_rubric_criteria_outcome_id FOREIGN KEY (tenant_id, outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.rubric_criteria ADD CONSTRAINT fk_rubric_criteria_rubric_id FOREIGN KEY (tenant_id, rubric_id) REFERENCES lms.rubrics(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.rubric_criteria ADD CONSTRAINT fk_rubric_criteria_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.rubric_levels ADD CONSTRAINT fk_rubric_levels_criterion_id FOREIGN KEY (tenant_id, criterion_id) REFERENCES lms.rubric_criteria(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.rubric_levels ADD CONSTRAINT fk_rubric_levels_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.rubrics ADD CONSTRAINT fk_rubrics_owner_employee_id FOREIGN KEY (tenant_id, owner_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.rubrics ADD CONSTRAINT fk_rubrics_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.session_interactions ADD CONSTRAINT fk_session_interactions_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.session_interactions ADD CONSTRAINT fk_session_interactions_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.virtual_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.session_interactions ADD CONSTRAINT fk_session_interactions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.session_participants ADD CONSTRAINT fk_session_participants_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.session_participants ADD CONSTRAINT fk_session_participants_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.virtual_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.session_participants ADD CONSTRAINT fk_session_participants_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.session_poll_responses ADD CONSTRAINT fk_session_poll_responses_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.session_poll_responses ADD CONSTRAINT fk_session_poll_responses_poll_id FOREIGN KEY (tenant_id, poll_id) REFERENCES lms.session_polls(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.session_poll_responses ADD CONSTRAINT fk_session_poll_responses_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.session_polls ADD CONSTRAINT fk_session_polls_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.virtual_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.session_polls ADD CONSTRAINT fk_session_polls_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.session_recordings ADD CONSTRAINT fk_session_recordings_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.session_recordings ADD CONSTRAINT fk_session_recordings_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.virtual_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.session_recordings ADD CONSTRAINT fk_session_recordings_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.session_resources ADD CONSTRAINT fk_session_resources_resource_id FOREIGN KEY (tenant_id, resource_id) REFERENCES lms.resources(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.session_resources ADD CONSTRAINT fk_session_resources_session_id FOREIGN KEY (tenant_id, session_id) REFERENCES lms.virtual_sessions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.session_resources ADD CONSTRAINT fk_session_resources_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.student_assignments ADD CONSTRAINT fk_student_assignments_assignment_id FOREIGN KEY (tenant_id, assignment_id) REFERENCES lms.assignments(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.student_assignments ADD CONSTRAINT fk_student_assignments_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.student_assignments ADD CONSTRAINT fk_student_assignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.submission_feedback ADD CONSTRAINT fk_submission_feedback_given_by FOREIGN KEY (tenant_id, given_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.submission_feedback ADD CONSTRAINT fk_submission_feedback_submission_id FOREIGN KEY (tenant_id, submission_id) REFERENCES lms.submissions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.submission_feedback ADD CONSTRAINT fk_submission_feedback_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.submission_versions ADD CONSTRAINT fk_submission_versions_submission_id FOREIGN KEY (tenant_id, submission_id) REFERENCES lms.submissions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.submission_versions ADD CONSTRAINT fk_submission_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.submissions ADD CONSTRAINT fk_submissions_student_assignment_id_consistent FOREIGN KEY (tenant_id, student_assignment_id, student_id) REFERENCES lms.student_assignments(tenant_id, id, student_id) ON DELETE CASCADE;
ALTER TABLE lms.submissions ADD CONSTRAINT fk_submissions_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.submissions ADD CONSTRAINT fk_submissions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT fk_virtual_classrooms_course_id FOREIGN KEY (tenant_id, course_id) REFERENCES acd.courses(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT fk_virtual_classrooms_owner_employee_id FOREIGN KEY (tenant_id, owner_employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT fk_virtual_classrooms_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.virtual_classrooms ADD CONSTRAINT fk_virtual_classrooms_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE lms.virtual_sessions ADD CONSTRAINT fk_virtual_sessions_attendance_session_id FOREIGN KEY (tenant_id, attendance_session_id) REFERENCES acd.attendance_sessions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE lms.virtual_sessions ADD CONSTRAINT fk_virtual_sessions_classroom_id FOREIGN KEY (tenant_id, classroom_id) REFERENCES lms.virtual_classrooms(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE lms.virtual_sessions ADD CONSTRAINT fk_virtual_sessions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE obe.alignments ADD CONSTRAINT fk_alignments_outcome_id FOREIGN KEY (tenant_id, outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE obe.alignments ADD CONSTRAINT fk_alignments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT fk_attainment_evidence_outcome_id FOREIGN KEY (tenant_id, outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT fk_attainment_evidence_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT fk_attainment_evidence_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT fk_attainment_evidence_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_evidence ADD CONSTRAINT fk_attainment_evidence_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_rules ADD CONSTRAINT fk_attainment_rules_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_rules ADD CONSTRAINT fk_attainment_rules_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_rules ADD CONSTRAINT fk_attainment_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE obe.attainment_thresholds ADD CONSTRAINT fk_attainment_thresholds_rule_id FOREIGN KEY (tenant_id, rule_id) REFERENCES obe.attainment_rules(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE obe.attainment_thresholds ADD CONSTRAINT fk_attainment_thresholds_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE obe.attainments ADD CONSTRAINT fk_attainments_outcome_id FOREIGN KEY (tenant_id, outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainments ADD CONSTRAINT fk_attainments_rule_id FOREIGN KEY (tenant_id, rule_id) REFERENCES obe.attainment_rules(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainments ADD CONSTRAINT fk_attainments_section_id FOREIGN KEY (tenant_id, section_id) REFERENCES acd.course_sections(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.attainments ADD CONSTRAINT fk_attainments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE obe.attainments ADD CONSTRAINT fk_attainments_term_id FOREIGN KEY (tenant_id, term_id) REFERENCES acd.calendar_terms(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.outcome_mappings ADD CONSTRAINT fk_outcome_mappings_from_outcome_id FOREIGN KEY (tenant_id, from_outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE obe.outcome_mappings ADD CONSTRAINT fk_outcome_mappings_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE obe.outcome_mappings ADD CONSTRAINT fk_outcome_mappings_to_outcome_id FOREIGN KEY (tenant_id, to_outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE obe.outcomes ADD CONSTRAINT fk_outcomes_curriculum_version_id FOREIGN KEY (tenant_id, curriculum_version_id) REFERENCES acd.curriculum_versions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.outcomes ADD CONSTRAINT fk_outcomes_parent_outcome_id FOREIGN KEY (tenant_id, parent_outcome_id) REFERENCES obe.outcomes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.outcomes ADD CONSTRAINT fk_outcomes_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.outcomes ADD CONSTRAINT fk_outcomes_subject_id FOREIGN KEY (tenant_id, subject_id) REFERENCES acd.subjects(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE obe.outcomes ADD CONSTRAINT fk_outcomes_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.compensation_lines ADD CONSTRAINT fk_compensation_lines_compensation_id FOREIGN KEY (tenant_id, compensation_id) REFERENCES pay.employee_compensations(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE pay.compensation_lines ADD CONSTRAINT fk_compensation_lines_component_id FOREIGN KEY (tenant_id, component_id) REFERENCES pay.salary_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.compensation_lines ADD CONSTRAINT fk_compensation_lines_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT fk_compensation_revisions_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT fk_compensation_revisions_from_compensation_id FOREIGN KEY (tenant_id, from_compensation_id) REFERENCES pay.employee_compensations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT fk_compensation_revisions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT fk_compensation_revisions_to_compensation_id FOREIGN KEY (tenant_id, to_compensation_id) REFERENCES pay.employee_compensations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.compensation_revisions ADD CONSTRAINT fk_compensation_revisions_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_compensations ADD CONSTRAINT fk_employee_compensations_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_compensations ADD CONSTRAINT fk_employee_compensations_payroll_group_id FOREIGN KEY (tenant_id, payroll_group_id) REFERENCES pay.payroll_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_compensations ADD CONSTRAINT fk_employee_compensations_structure_version_id FOREIGN KEY (tenant_id, structure_version_id) REFERENCES pay.salary_structure_versions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_compensations ADD CONSTRAINT fk_employee_compensations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_loans ADD CONSTRAINT fk_employee_loans_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_loans ADD CONSTRAINT fk_employee_loans_start_run_period_id FOREIGN KEY (tenant_id, start_run_period_id) REFERENCES pay.payroll_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_loans ADD CONSTRAINT fk_employee_loans_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.employee_loans ADD CONSTRAINT fk_employee_loans_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.loan_installments ADD CONSTRAINT fk_loan_installments_due_period_id FOREIGN KEY (tenant_id, due_period_id) REFERENCES pay.payroll_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.loan_installments ADD CONSTRAINT fk_loan_installments_loan_id FOREIGN KEY (tenant_id, loan_id) REFERENCES pay.employee_loans(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE pay.loan_installments ADD CONSTRAINT fk_loan_installments_payslip_line_id FOREIGN KEY (tenant_id, payslip_line_id) REFERENCES pay.payslip_lines(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.loan_installments ADD CONSTRAINT fk_loan_installments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_groups ADD CONSTRAINT fk_payroll_groups_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_groups ADD CONSTRAINT fk_payroll_groups_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_inputs ADD CONSTRAINT fk_payroll_inputs_component_id FOREIGN KEY (tenant_id, component_id) REFERENCES pay.salary_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_inputs ADD CONSTRAINT fk_payroll_inputs_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_inputs ADD CONSTRAINT fk_payroll_inputs_run_id FOREIGN KEY (tenant_id, run_id) REFERENCES pay.payroll_runs(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE pay.payroll_inputs ADD CONSTRAINT fk_payroll_inputs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_periods ADD CONSTRAINT fk_payroll_periods_payroll_group_id FOREIGN KEY (tenant_id, payroll_group_id) REFERENCES pay.payroll_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_periods ADD CONSTRAINT fk_payroll_periods_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_runs ADD CONSTRAINT fk_payroll_runs_finalized_by FOREIGN KEY (tenant_id, finalized_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_runs ADD CONSTRAINT fk_payroll_runs_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES pay.payroll_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_runs ADD CONSTRAINT fk_payroll_runs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.payroll_runs ADD CONSTRAINT fk_payroll_runs_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payslip_lines ADD CONSTRAINT fk_payslip_lines_component_id FOREIGN KEY (tenant_id, component_id) REFERENCES pay.salary_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payslip_lines ADD CONSTRAINT fk_payslip_lines_payslip_id FOREIGN KEY (tenant_id, payslip_id) REFERENCES pay.payslips(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE pay.payslip_lines ADD CONSTRAINT fk_payslip_lines_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.payslips ADD CONSTRAINT fk_payslips_compensation_id FOREIGN KEY (tenant_id, compensation_id) REFERENCES pay.employee_compensations(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payslips ADD CONSTRAINT fk_payslips_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payslips ADD CONSTRAINT fk_payslips_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payslips ADD CONSTRAINT fk_payslips_run_id FOREIGN KEY (tenant_id, run_id) REFERENCES pay.payroll_runs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.payslips ADD CONSTRAINT fk_payslips_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.run_adjustments ADD CONSTRAINT fk_run_adjustments_component_id FOREIGN KEY (tenant_id, component_id) REFERENCES pay.salary_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.run_adjustments ADD CONSTRAINT fk_run_adjustments_employee_id FOREIGN KEY (tenant_id, employee_id) REFERENCES hrm.employees(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.run_adjustments ADD CONSTRAINT fk_run_adjustments_run_id FOREIGN KEY (tenant_id, run_id) REFERENCES pay.payroll_runs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.run_adjustments ADD CONSTRAINT fk_run_adjustments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.run_adjustments ADD CONSTRAINT fk_run_adjustments_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.salary_components ADD CONSTRAINT fk_salary_components_ledger_account_id FOREIGN KEY (tenant_id, ledger_account_id) REFERENCES fin.ledger_accounts(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.salary_components ADD CONSTRAINT fk_salary_components_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.salary_structure_versions ADD CONSTRAINT fk_salary_structure_versions_structure_id FOREIGN KEY (tenant_id, structure_id) REFERENCES pay.salary_structures(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE pay.salary_structure_versions ADD CONSTRAINT fk_salary_structure_versions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.salary_structures ADD CONSTRAINT fk_salary_structures_payroll_group_id FOREIGN KEY (tenant_id, payroll_group_id) REFERENCES pay.payroll_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.salary_structures ADD CONSTRAINT fk_salary_structures_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.statutory_rules ADD CONSTRAINT fk_statutory_rules_jurisdiction_id FOREIGN KEY (tenant_id, jurisdiction_id) REFERENCES tax.tax_jurisdictions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.statutory_rules ADD CONSTRAINT fk_statutory_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE pay.structure_components ADD CONSTRAINT fk_structure_components_component_id FOREIGN KEY (tenant_id, component_id) REFERENCES pay.salary_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.structure_components ADD CONSTRAINT fk_structure_components_percent_of_component_id FOREIGN KEY (tenant_id, percent_of_component_id) REFERENCES pay.salary_components(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE pay.structure_components ADD CONSTRAINT fk_structure_components_structure_version_id FOREIGN KEY (tenant_id, structure_version_id) REFERENCES pay.salary_structure_versions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE pay.structure_components ADD CONSTRAINT fk_structure_components_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE plat.alerts ADD CONSTRAINT fk_alerts_tenant_ref_id FOREIGN KEY (tenant_ref_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE plat.gateway_transactions ADD CONSTRAINT fk_gateway_transactions_invoice_id FOREIGN KEY (tenant_id, invoice_id) REFERENCES plat.invoices(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE plat.gateway_transactions ADD CONSTRAINT fk_gateway_transactions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE plat.invoice_lines ADD CONSTRAINT fk_invoice_lines_invoice_id FOREIGN KEY (tenant_id, invoice_id) REFERENCES plat.invoices(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE plat.invoice_lines ADD CONSTRAINT fk_invoice_lines_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE plat.invoices ADD CONSTRAINT fk_invoices_subscription_id FOREIGN KEY (tenant_id, subscription_id) REFERENCES plat.subscriptions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE plat.invoices ADD CONSTRAINT fk_invoices_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE plat.plan_entitlements ADD CONSTRAINT fk_plan_entitlements_plan_id FOREIGN KEY (plan_id) REFERENCES plat.subscription_plans(id) ON DELETE CASCADE;
ALTER TABLE plat.platform_admins ADD CONSTRAINT fk_platform_admins_user_id FOREIGN KEY (user_id) REFERENCES auth.users(id) ON DELETE RESTRICT;
ALTER TABLE plat.subscriptions ADD CONSTRAINT fk_subscriptions_plan_id FOREIGN KEY (plan_id) REFERENCES plat.subscription_plans(id) ON DELETE RESTRICT;
ALTER TABLE plat.subscriptions ADD CONSTRAINT fk_subscriptions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE plat.tenant_provisioning ADD CONSTRAINT fk_tenant_provisioning_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE plat.tenant_provisioning ADD CONSTRAINT fk_tenant_provisioning_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE plat.tenant_provisioning ADD CONSTRAINT fk_tenant_provisioning_workflow_instance_id FOREIGN KEY (tenant_id, workflow_instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE plat.usage_metrics ADD CONSTRAINT fk_usage_metrics_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.dashboard_widgets ADD CONSTRAINT fk_dashboard_widgets_dashboard_id FOREIGN KEY (tenant_id, dashboard_id) REFERENCES rpt.dashboards(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE rpt.dashboard_widgets ADD CONSTRAINT fk_dashboard_widgets_kpi_id FOREIGN KEY (tenant_id, kpi_id) REFERENCES rpt.kpi_definitions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE rpt.dashboard_widgets ADD CONSTRAINT fk_dashboard_widgets_report_definition_id FOREIGN KEY (tenant_id, report_definition_id) REFERENCES rpt.report_definitions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE rpt.dashboard_widgets ADD CONSTRAINT fk_dashboard_widgets_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.dashboards ADD CONSTRAINT fk_dashboards_owner_role_id FOREIGN KEY (tenant_id, owner_role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE rpt.dashboards ADD CONSTRAINT fk_dashboards_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.kpi_definitions ADD CONSTRAINT fk_kpi_definitions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.kpi_values ADD CONSTRAINT fk_kpi_values_kpi_id FOREIGN KEY (tenant_id, kpi_id) REFERENCES rpt.kpi_definitions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE rpt.kpi_values ADD CONSTRAINT fk_kpi_values_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.refresh_status ADD CONSTRAINT fk_refresh_status_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.report_definitions ADD CONSTRAINT fk_report_definitions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.report_executions ADD CONSTRAINT fk_report_executions_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE rpt.report_executions ADD CONSTRAINT fk_report_executions_report_definition_id FOREIGN KEY (tenant_id, report_definition_id) REFERENCES rpt.report_definitions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE rpt.report_executions ADD CONSTRAINT fk_report_executions_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE rpt.report_executions ADD CONSTRAINT fk_report_executions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.report_schedules ADD CONSTRAINT fk_report_schedules_recipients_group_id FOREIGN KEY (tenant_id, recipients_group_id) REFERENCES cam.audience_groups(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE rpt.report_schedules ADD CONSTRAINT fk_report_schedules_report_definition_id FOREIGN KEY (tenant_id, report_definition_id) REFERENCES rpt.report_definitions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE rpt.report_schedules ADD CONSTRAINT fk_report_schedules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE rpt.snapshots ADD CONSTRAINT fk_snapshots_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE stu.guardianships ADD CONSTRAINT fk_guardianships_guardian_person_id FOREIGN KEY (tenant_id, guardian_person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE stu.guardianships ADD CONSTRAINT fk_guardianships_student_id FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE stu.guardianships ADD CONSTRAINT fk_guardianships_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE stu.students ADD CONSTRAINT fk_students_admission_academic_year_id FOREIGN KEY (tenant_id, admission_academic_year_id) REFERENCES core.academic_years(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE stu.students ADD CONSTRAINT fk_students_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE stu.students ADD CONSTRAINT fk_students_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE stu.students ADD CONSTRAINT fk_students_program_id FOREIGN KEY (tenant_id, program_id) REFERENCES acd.programs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE stu.students ADD CONSTRAINT fk_students_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.data_quality_issues ADD CONSTRAINT fk_data_quality_issues_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.data_quality_issues ADD CONSTRAINT fk_data_quality_issues_import_row_id FOREIGN KEY (tenant_id, import_row_id) REFERENCES sys.import_rows(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.data_quality_issues ADD CONSTRAINT fk_data_quality_issues_job_id FOREIGN KEY (tenant_id, job_id) REFERENCES sys.import_jobs(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.data_quality_issues ADD CONSTRAINT fk_data_quality_issues_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.dead_letter_events ADD CONSTRAINT fk_dead_letter_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.entity_locks ADD CONSTRAINT fk_entity_locks_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.export_jobs ADD CONSTRAINT fk_export_jobs_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.export_jobs ADD CONSTRAINT fk_export_jobs_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.export_jobs ADD CONSTRAINT fk_export_jobs_result_document_id FOREIGN KEY (tenant_id, result_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.export_jobs ADD CONSTRAINT fk_export_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.id_map ADD CONSTRAINT fk_id_map_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.idempotency_keys ADD CONSTRAINT fk_idempotency_keys_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.import_jobs ADD CONSTRAINT fk_import_jobs_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.import_jobs ADD CONSTRAINT fk_import_jobs_source_document_id FOREIGN KEY (tenant_id, source_document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE sys.import_jobs ADD CONSTRAINT fk_import_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.import_rows ADD CONSTRAINT fk_import_rows_job_id FOREIGN KEY (tenant_id, job_id) REFERENCES sys.import_jobs(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE sys.import_rows ADD CONSTRAINT fk_import_rows_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.inbox_events ADD CONSTRAINT fk_inbox_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.jobs ADD CONSTRAINT fk_jobs_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE sys.outbox_events ADD CONSTRAINT fk_outbox_events_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_codes ADD CONSTRAINT fk_tax_codes_tax_type_id FOREIGN KEY (tenant_id, tax_type_id) REFERENCES tax.tax_types(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_codes ADD CONSTRAINT fk_tax_codes_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_exemptions ADD CONSTRAINT fk_tax_exemptions_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_exemptions ADD CONSTRAINT fk_tax_exemptions_person_id FOREIGN KEY (tenant_id, person_id) REFERENCES core.persons(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_exemptions ADD CONSTRAINT fk_tax_exemptions_tax_code_id FOREIGN KEY (tenant_id, tax_code_id) REFERENCES tax.tax_codes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_exemptions ADD CONSTRAINT fk_tax_exemptions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_exemptions ADD CONSTRAINT fk_tax_exemptions_vendor_id FOREIGN KEY (tenant_id, vendor_id) REFERENCES fin.vendors(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_filings ADD CONSTRAINT fk_tax_filings_document_id FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_filings ADD CONSTRAINT fk_tax_filings_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES tax.tax_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_filings ADD CONSTRAINT fk_tax_filings_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_jurisdictions ADD CONSTRAINT fk_tax_jurisdictions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_payments ADD CONSTRAINT fk_tax_payments_disbursement_id FOREIGN KEY (tenant_id, disbursement_id) REFERENCES fin.disbursements(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_payments ADD CONSTRAINT fk_tax_payments_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES tax.tax_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_payments ADD CONSTRAINT fk_tax_payments_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_periods ADD CONSTRAINT fk_tax_periods_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_rates ADD CONSTRAINT fk_tax_rates_jurisdiction_id FOREIGN KEY (tenant_id, jurisdiction_id) REFERENCES tax.tax_jurisdictions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_rates ADD CONSTRAINT fk_tax_rates_tax_code_id FOREIGN KEY (tenant_id, tax_code_id) REFERENCES tax.tax_codes(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE tax.tax_rates ADD CONSTRAINT fk_tax_rates_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_rules ADD CONSTRAINT fk_tax_rules_tax_code_id FOREIGN KEY (tenant_id, tax_code_id) REFERENCES tax.tax_codes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_rules ADD CONSTRAINT fk_tax_rules_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_transactions ADD CONSTRAINT fk_tax_transactions_period_id FOREIGN KEY (tenant_id, period_id) REFERENCES tax.tax_periods(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_transactions ADD CONSTRAINT fk_tax_transactions_tax_code_id FOREIGN KEY (tenant_id, tax_code_id) REFERENCES tax.tax_codes(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_transactions ADD CONSTRAINT fk_tax_transactions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE tax.tax_types ADD CONSTRAINT fk_tax_types_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.decisions ADD CONSTRAINT fk_decisions_decided_by FOREIGN KEY (tenant_id, decided_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.decisions ADD CONSTRAINT fk_decisions_instance_id FOREIGN KEY (tenant_id, instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.decisions ADD CONSTRAINT fk_decisions_task_id FOREIGN KEY (tenant_id, task_id) REFERENCES wf.tasks(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.decisions ADD CONSTRAINT fk_decisions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.definition_steps ADD CONSTRAINT fk_definition_steps_approver_role_id FOREIGN KEY (tenant_id, approver_role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.definition_steps ADD CONSTRAINT fk_definition_steps_approver_user_id FOREIGN KEY (tenant_id, approver_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.definition_steps ADD CONSTRAINT fk_definition_steps_definition_id FOREIGN KEY (tenant_id, definition_id) REFERENCES wf.definitions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE wf.definition_steps ADD CONSTRAINT fk_definition_steps_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.definitions ADD CONSTRAINT fk_definitions_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.definitions ADD CONSTRAINT fk_definitions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.delegations ADD CONSTRAINT fk_delegations_definition_id FOREIGN KEY (tenant_id, definition_id) REFERENCES wf.definitions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.delegations ADD CONSTRAINT fk_delegations_from_user_id FOREIGN KEY (tenant_id, from_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.delegations ADD CONSTRAINT fk_delegations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.delegations ADD CONSTRAINT fk_delegations_to_user_id FOREIGN KEY (tenant_id, to_user_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.escalations ADD CONSTRAINT fk_escalations_escalated_to FOREIGN KEY (tenant_id, escalated_to) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.escalations ADD CONSTRAINT fk_escalations_task_id FOREIGN KEY (tenant_id, task_id) REFERENCES wf.tasks(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE wf.escalations ADD CONSTRAINT fk_escalations_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.exceptions ADD CONSTRAINT fk_exceptions_granted_by FOREIGN KEY (tenant_id, granted_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.exceptions ADD CONSTRAINT fk_exceptions_instance_id FOREIGN KEY (tenant_id, instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE wf.exceptions ADD CONSTRAINT fk_exceptions_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.instances ADD CONSTRAINT fk_instances_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.instances ADD CONSTRAINT fk_instances_definition_id FOREIGN KEY (tenant_id, definition_id) REFERENCES wf.definitions(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.instances ADD CONSTRAINT fk_instances_requested_by FOREIGN KEY (tenant_id, requested_by) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.instances ADD CONSTRAINT fk_instances_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.policies ADD CONSTRAINT fk_policies_definition_id FOREIGN KEY (tenant_id, definition_id) REFERENCES wf.definitions(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE wf.policies ADD CONSTRAINT fk_policies_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;
ALTER TABLE wf.tasks ADD CONSTRAINT fk_tasks_assignee_id FOREIGN KEY (tenant_id, assignee_id) REFERENCES iam.user_profiles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.tasks ADD CONSTRAINT fk_tasks_college_id FOREIGN KEY (tenant_id, college_id) REFERENCES core.colleges(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.tasks ADD CONSTRAINT fk_tasks_instance_id FOREIGN KEY (tenant_id, instance_id) REFERENCES wf.instances(tenant_id, id) ON DELETE CASCADE;
ALTER TABLE wf.tasks ADD CONSTRAINT fk_tasks_role_id FOREIGN KEY (tenant_id, role_id) REFERENCES iam.roles(tenant_id, id) ON DELETE RESTRICT;
ALTER TABLE wf.tasks ADD CONSTRAINT fk_tasks_tenant FOREIGN KEY (tenant_id) REFERENCES core.tenants(id) ON DELETE RESTRICT;