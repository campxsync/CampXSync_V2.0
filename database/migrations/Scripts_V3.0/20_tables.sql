-- CampXSync ERP on Supabase (PostgreSQL 15+)
-- Generated from the design specs; edit the specs, not this file, if you regenerate.


-- ============================================================ core

create table core.tenants (
  id uuid primary key default gen_random_uuid(),
  code text not null,
  name text not null,
  legal_name text,
  status text not null default 'ACTIVE',
  currency_code char(3) not null default 'INR',
  timezone text not null default 'Asia/Kolkata',
  locale text not null default 'en-IN',
  profile jsonb default '{}'::jsonb,
  onboarded_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tenants_status check (status in ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'ARCHIVED')),
  constraint ck_tenants_x1 check (code ~ '^[A-Z0-9_-]{2,30}$')
);
create unique index uq_tenants_code on core.tenants (code) where deleted_at is null;
comment on table core.tenants is 'Tenant root = one Institute (Mongo tenantId). Every tenant-scoped row carries tenant_id -> core.tenants.id [Mongo: ADM01.institutes]';

create table core.addresses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  line1 text not null,
  line2 text,
  landmark text,
  city text,
  district text,
  state_code text,
  postal_code text,
  country_code char(3) not null default 'IND',
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_addresses_tenant_id unique (tenant_id, id)
);
comment on table core.addresses is 'Normalised postal address, referenced by colleges, campuses, persons, vendors, centres';

create table core.colleges (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  legal_name text,
  status text not null default 'ACTIVE',
  provisioning_status text not null default 'REQUESTED',
  affiliating_university text,
  established_year smallint,
  address_id uuid,
  email text,
  phone text,
  website text,
  profile jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_colleges_status check (status in ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'ARCHIVED')),
  constraint ck_colleges_provisioning_status check (provisioning_status in ('REQUESTED', 'VALIDATED', 'PROVISIONING', 'ACTIVE', 'SUSPENDED', 'FAILED')),
  constraint ck_colleges_x1 check (code ~ '^[A-Z0-9_-]{2,30}$'),
  constraint uq_colleges_tenant_id unique (tenant_id, id)
);
create unique index uq_colleges_tenant_id_code on core.colleges (tenant_id, code) where deleted_at is null;
create index ix_colleges_address_id on core.colleges (tenant_id, address_id);
comment on table core.colleges is 'College (institution) under an Institute. Mongo institutionId. ADM-01 registry + ADM-02 profile merged 1:1 [Mongo: ADM01.colleges, ADM02.college_profiles]';

create table core.campuses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  code text not null,
  name text not null,
  address_id uuid,
  is_primary boolean not null default false,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_campuses_status check (status in ('ACTIVE', 'SUSPENDED', 'INACTIVE', 'ARCHIVED')),
  constraint uq_campuses_tenant_id unique (tenant_id, id)
);
create unique index uq_campuses_tenant_id_college_id_code on core.campuses (tenant_id, college_id, code) where deleted_at is null;
create index ix_campuses_college_id on core.campuses (tenant_id, college_id);
create index ix_campuses_address_id on core.campuses (tenant_id, address_id);
comment on table core.campuses is 'Campus of a college (normalised from ADM01.colleges.campusIds[])';

create table core.college_accreditations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  body text not null,
  grade_or_status text,
  score numeric(18,2),
  reference_no text,
  valid_from date,
  valid_to date,
  certificate_document_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_college_accreditations_body check (body in ('NAAC', 'NBA', 'UGC', 'AICTE', 'NIRF', 'ISO', 'OTHER')),
  constraint ck_college_accreditations_x1 check (valid_to is null or valid_from is null or valid_to >= valid_from),
  constraint uq_college_accreditations_tenant_id unique (tenant_id, id)
);
create index ix_college_accreditations_tenant_id_college_id_body on core.college_accreditations (tenant_id, college_id, body);
create index ix_college_accreditations_certificate_document_id on core.college_accreditations (tenant_id, certificate_document_id);
comment on table core.college_accreditations is 'Accreditations (normalised from ADM02.college_profiles.accreditationRefs[])';

create table core.departments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  code text not null,
  name text not null,
  parent_department_id uuid,
  head_employee_id uuid,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_departments_status check (status in ('ACTIVE', 'RETIRED')),
  constraint uq_departments_tenant_id unique (tenant_id, id)
);
create unique index uq_departments_tenant_id_college_id_code on core.departments (tenant_id, college_id, code) where deleted_at is null;
create index ix_departments_tenant_id_college_id_parent_department_id on core.departments (tenant_id, college_id, parent_department_id);
create index ix_departments_parent_department_id on core.departments (tenant_id, parent_department_id);
create index ix_departments_head_employee_id on core.departments (tenant_id, head_employee_id);
comment on table core.departments is 'Academic / administrative departments (self-referencing hierarchy) [Mongo: ADM02.departments]';

create table core.academic_years (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  start_date date not null,
  end_date date not null,
  is_current boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_academic_years_x1 check (end_date > start_date),
  constraint uq_academic_years_tenant_id unique (tenant_id, id)
);
create unique index uq_academic_years_tenant_id_code on core.academic_years (tenant_id, code) where deleted_at is null;
create unique index uq_academic_years_tenant_id_is_current on core.academic_years (tenant_id, is_current) where deleted_at is null and is_current;
comment on table core.academic_years is 'Tenant-wide academic year, e.g. 2026-27. One current year per tenant';

create table core.calendars (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  academic_year_id uuid,
  code text not null,
  name text not null,
  calendar_type text not null,
  status text not null default 'DRAFT',
  published_at timestamptz,
  week_start_day smallint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_calendars_calendar_type check (calendar_type in ('ACADEMIC', 'HOLIDAY', 'EXAM', 'ADMINISTRATIVE')),
  constraint ck_calendars_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  constraint uq_calendars_tenant_id unique (tenant_id, id)
);
create unique index uq_calendars_tenant_id_code on core.calendars (tenant_id, code) where deleted_at is null;
create index ix_calendars_tenant_id_college_id_calendar_type_ac_6cbad949 on core.calendars (tenant_id, college_id, calendar_type, academic_year_id);
create index ix_calendars_academic_year_id on core.calendars (tenant_id, academic_year_id);
comment on table core.calendars is 'One calendar entity for academic, holiday and exam calendars (was 3 separate collections) [Mongo: ACD07.academic_calendars, ADM02.college_calendar_configuration, HRM02.holiday_calendars]';

create table core.calendar_working_days (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  calendar_id uuid not null,
  day_of_week smallint not null,
  is_working boolean not null default true,
  is_half_day boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_calendar_working_days_day_of_week check (day_of_week >= 0),
  constraint ck_calendar_working_days_x1 check (day_of_week between 0 and 6),
  constraint uq_calendar_working_days_tenant_id unique (tenant_id, id)
);
create unique index uq_calendar_working_days_tenant_id_calendar_id_day_of_week on core.calendar_working_days (tenant_id, calendar_id, day_of_week) where deleted_at is null;
create index ix_calendar_working_days_calendar_id on core.calendar_working_days (tenant_id, calendar_id);
comment on table core.calendar_working_days is 'Working-week pattern (normalised from ADM02 workingDaysPerWeek)';

create table core.calendar_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  calendar_id uuid not null,
  event_type text not null,
  title text not null,
  description text,
  start_date date not null,
  end_date date not null,
  is_holiday boolean not null default false,
  applies_to jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_calendar_events_event_type check (event_type in ('HOLIDAY', 'TERM_START', 'TERM_END', 'EXAM', 'COMMENCEMENT', 'EVENT', 'DEADLINE', 'OTHER')),
  constraint ck_calendar_events_x1 check (end_date >= start_date),
  constraint uq_calendar_events_tenant_id unique (tenant_id, id)
);
create index ix_calendar_events_tenant_id_calendar_id_start_date on core.calendar_events (tenant_id, calendar_id, start_date);
create index ix_calendar_events_tenant_id_start_date_end_date on core.calendar_events (tenant_id, start_date, end_date);
comment on table core.calendar_events is 'Dated calendar entries; holidays feed both student timetable and employee attendance [Mongo: ACD07.calendar_events, HRM03.attendance_holidays]';

create table core.persons (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  first_name text not null,
  middle_name text,
  last_name text,
  display_name text not null,
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
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_persons_gender check (gender in ('M', 'F', 'O', 'U')),
  constraint ck_persons_status check (status in ('ACTIVE', 'INACTIVE')),
  constraint uq_persons_tenant_id unique (tenant_id, id)
);
create index ix_persons_tenant_id_display_name on core.persons (tenant_id, display_name);
create index ix_persons_tenant_id_primary_email on core.persons (tenant_id, primary_email);
create index ix_persons_tenant_id_primary_phone on core.persons (tenant_id, primary_phone);
create index ix_persons_photo_document_id on core.persons (tenant_id, photo_document_id);
create index ix_persons_permanent_address_id on core.persons (tenant_id, permanent_address_id);
create index ix_persons_current_address_id on core.persons (tenant_id, current_address_id);
comment on table core.persons is 'Identity supertype for students, employees, candidates, guardians. Employee/student rows hold role-specific data only';

create table core.person_contacts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  contact_type text not null,
  value text not null,
  is_primary boolean not null default false,
  is_verified boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_person_contacts_contact_type check (contact_type in ('EMAIL', 'PHONE', 'ALT_PHONE', 'WHATSAPP')),
  constraint uq_person_contacts_tenant_id unique (tenant_id, id)
);
create unique index uq_person_contacts_tenant_id_person_id_contact_type_value on core.person_contacts (tenant_id, person_id, contact_type, value) where deleted_at is null;
create index ix_person_contacts_person_id on core.person_contacts (tenant_id, person_id);
comment on table core.person_contacts is '[Mongo: HRM01.employee_contacts]';

create table core.person_emergency_contacts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  name text not null,
  relationship text not null,
  phone text not null,
  alt_phone text,
  priority smallint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_person_emergency_contacts_tenant_id unique (tenant_id, id)
);
create index ix_person_emergency_contacts_person_id on core.person_emergency_contacts (tenant_id, person_id);
comment on table core.person_emergency_contacts is '[Mongo: HRM01.employee_emergency_contacts]';

create table core.person_identifiers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  id_type text not null,
  masked_value text,
  value_ciphertext bytea,
  value_hash bytea,
  issuing_authority text,
  valid_from date,
  valid_to date,
  is_verified boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_person_identifiers_id_type check (id_type in ('AADHAAR', 'PAN', 'PASSPORT', 'DRIVING_LICENSE', 'VOTER_ID', 'UAN', 'ESI', 'APAAR', 'OTHER')),
  constraint uq_person_identifiers_tenant_id unique (tenant_id, id)
);
create unique index uq_person_identifiers_tenant_id_person_id_id_type on core.person_identifiers (tenant_id, person_id, id_type) where deleted_at is null;
create unique index uq_person_identifiers_tenant_id_id_type_value_hash on core.person_identifiers (tenant_id, id_type, value_hash) where deleted_at is null and value_hash is not null;
create index ix_person_identifiers_person_id on core.person_identifiers (tenant_id, person_id);
comment on table core.person_identifiers is 'Government identifiers. Store ciphertext via Supabase Vault/pgsodium; hash enables duplicate detection; never store plaintext [Mongo: HRM01.employee_identifiers]';

create table core.person_qualifications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  level_code text not null,
  degree text not null,
  specialization text,
  institution text,
  board_or_university text,
  year_of_passing smallint,
  grade_or_percentage text,
  document_id uuid,
  is_verified boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_person_qualifications_tenant_id unique (tenant_id, id)
);
create index ix_person_qualifications_tenant_id_person_id_level_code on core.person_qualifications (tenant_id, person_id, level_code);
create index ix_person_qualifications_document_id on core.person_qualifications (tenant_id, document_id);
comment on table core.person_qualifications is 'Shared by employees, candidates and faculty [Mongo: HRM01.employee_qualifications]';

create table core.person_experience (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  organisation text not null,
  designation text,
  from_date date,
  to_date date,
  is_teaching boolean not null default false,
  responsibilities text,
  document_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_person_experience_x1 check (to_date is null or from_date is null or to_date >= from_date),
  constraint uq_person_experience_tenant_id unique (tenant_id, id)
);
create index ix_person_experience_person_id on core.person_experience (tenant_id, person_id);
create index ix_person_experience_document_id on core.person_experience (tenant_id, document_id);
comment on table core.person_experience is '[Mongo: HRM01.employee_experience]';

create table core.lookup_types (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  is_system boolean not null default false,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_lookup_types_tenant_id unique (tenant_id, id)
);
create unique index uq_lookup_types_tenant_id_code on core.lookup_types (tenant_id, code) where deleted_at is null;
comment on table core.lookup_types is 'Configurable code lists (designation, employee type, subject category, qualification level ...)';

create table core.lookup_values (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  lookup_type_id uuid not null,
  code text not null,
  label text not null,
  sort_order integer not null default 0,
  is_active boolean not null default true,
  attrs jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_lookup_values_tenant_id unique (tenant_id, id)
);
create unique index uq_lookup_values_tenant_id_lookup_type_id_code on core.lookup_values (tenant_id, lookup_type_id, code) where deleted_at is null;
create index ix_lookup_values_lookup_type_id on core.lookup_values (tenant_id, lookup_type_id);
comment on table core.lookup_values is 'Values of lookup types. Replaces per-module classification collections [Mongo: LMS01.course_classifications, LMS03.subject_classifications]';

create table core.number_sequences (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scope_key text not null,
  prefix text,
  suffix text,
  next_value bigint not null default 1,
  padding smallint not null default 6,
  reset_policy text not null default 'NEVER',
  last_reset_on date,
  required_permission text,
  college_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_number_sequences_next_value check (next_value >= 0),
  constraint ck_number_sequences_reset_policy check (reset_policy in ('NEVER', 'YEARLY', 'MONTHLY', 'ACADEMIC_YEAR')),
  constraint uq_number_sequences_tenant_id unique (tenant_id, id)
);
create unique index uq_number_sequences_tenant_id_scope_key_college_id on core.number_sequences (tenant_id, scope_key, college_id) where deleted_at is null;
create index ix_number_sequences_college_id on core.number_sequences (tenant_id, college_id);
comment on table core.number_sequences is 'Gap-free business numbering (receipts, registrations, hall tickets ...). Allocate via core.next_number() [Mongo: EXM01.registration_sequences, FIN05.receipt_number_sequences]';

create table core.comments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  entity_type text not null,
  entity_id uuid not null,
  body text not null,
  is_internal boolean not null default true,
  parent_comment_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_comments_tenant_id unique (tenant_id, id)
);
create index ix_comments_tenant_id_entity_type_entity_id_created_at on core.comments (tenant_id, entity_type, entity_id, created_at);
create index ix_comments_parent_comment_id on core.comments (tenant_id, parent_comment_id);
comment on table core.comments is 'Generic comments on any record (was *_comments in FIN03/06/07/08/09/10) [Mongo: FIN03.expense_comments, FIN06.scholarship_comments, FIN07.payroll_comments, FIN08.report_comments, FIN09.audit_comments, FIN10.budget_comments, FIN09.audit_case_comments, FIN09.audit_comments]';

create table core.tags (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  label text not null,
  color text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_tags_tenant_id unique (tenant_id, id)
);
create unique index uq_tags_tenant_id_code on core.tags (tenant_id, code) where deleted_at is null;
comment on table core.tags is '[Mongo: LMS07.resource_tags]';

create table core.entity_tags (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  tag_id uuid not null,
  entity_type text not null,
  entity_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_entity_tags_tenant_id unique (tenant_id, id)
);
create unique index uq_entity_tags_tenant_id_tag_id_entity_type_entity_id on core.entity_tags (tenant_id, tag_id, entity_type, entity_id);
create index ix_entity_tags_tenant_id_entity_type_entity_id on core.entity_tags (tenant_id, entity_type, entity_id);

create table core.entity_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  entity_type text not null,
  entity_id uuid not null,
  version_no integer not null,
  status text,
  effective_from timestamptz,
  effective_to timestamptz,
  change_reason text,
  snapshot jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_entity_versions_version_no check (version_no > 0),
  constraint uq_entity_versions_tenant_id unique (tenant_id, id)
);
create index ix_entity_versions_tenant_id_entity_type_entity_id_7502a837 on core.entity_versions (tenant_id, entity_type, entity_id, version_no desc);
create index gx_entity_versions_snapshot on core.entity_versions using gin (snapshot);
comment on table core.entity_versions is 'Append-only immutable snapshots for versioned records that are not first-class (see DESIGN §6.4) [Mongo: ACD08.resource_versions, EXM03.hall_ticket_versions, EXM08.result_versions, FIN06.scholarship_versions, FIN08.report_versions, FIN09.compliance_rule_versions, FIN10.forecast_versions, FIN10.scenario_versions, HRM04.performance_cycle_versions, HRM04.performance_rating_scale_versions, LMS02.content_versions, LMS04.lesson_plan_versions, LMS05.assignment_versions, LMS06.virtual_classroom_versions, LMS10.outcome_versions, LMS07.resource_versions, ADM01.configuration_versions]';

create table core.rooms (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  campus_id uuid not null,
  building text,
  code text not null,
  name text,
  room_type text not null default 'CLASSROOM',
  capacity integer,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_rooms_room_type check (room_type in ('CLASSROOM', 'LAB', 'SEMINAR', 'AUDITORIUM', 'EXAM_HALL', 'OFFICE', 'OTHER')),
  constraint ck_rooms_capacity check (capacity >= 0),
  constraint ck_rooms_status check (status in ('ACTIVE', 'INACTIVE', 'MAINTENANCE')),
  constraint uq_rooms_tenant_id unique (tenant_id, id)
);
create unique index uq_rooms_tenant_id_campus_id_code on core.rooms (tenant_id, campus_id, code) where deleted_at is null;
create index ix_rooms_campus_id on core.rooms (tenant_id, campus_id);
comment on table core.rooms is 'Physical rooms (new; required by timetables and exam room allocation — not a Mongo collection)';

create table core.publications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  entity_type text not null,
  entity_id uuid not null,
  version_no integer not null default 1,
  status text not null default 'PUBLISHED',
  audience jsonb default '{}'::jsonb,
  published_by uuid,
  published_at timestamptz,
  unpublished_at timestamptz,
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_publications_status check (status in ('DRAFT', 'SCHEDULED', 'PUBLISHED', 'UNPUBLISHED', 'WITHDRAWN')),
  constraint uq_publications_tenant_id unique (tenant_id, id)
);
create index ix_publications_tenant_id_entity_type_entity_id_published_at on core.publications (tenant_id, entity_type, entity_id, published_at desc);
create index ix_publications_published_by on core.publications (tenant_id, published_by);
comment on table core.publications is 'Generic publish/unpublish ledger (content, resources, exam schedules, hall tickets, results) [Mongo: LMS02.content_publications, LMS07.resource_publications, EXM02.publication_records, EXM03.hall_ticket_publications, EXM08.result_publications]';


-- ============================================================ stu

create table stu.students (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  college_id uuid not null,
  program_id uuid,
  enrollment_no text not null,
  admission_no text,
  admission_date date,
  admission_academic_year_id uuid,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_students_status check (status in ('ACTIVE', 'ALUMNI', 'SUSPENDED', 'WITHDRAWN', 'DETAINED', 'GRADUATED')),
  constraint uq_students_tenant_id unique (tenant_id, id)
);
create unique index uq_students_tenant_id_enrollment_no on stu.students (tenant_id, enrollment_no) where deleted_at is null;
create unique index uq_students_tenant_id_person_id_college_id on stu.students (tenant_id, person_id, college_id) where deleted_at is null;
create index ix_students_tenant_id_college_id_program_id_status on stu.students (tenant_id, college_id, program_id, status);
create index ix_students_person_id on stu.students (tenant_id, person_id);
create index ix_students_program_id on stu.students (tenant_id, program_id);
create index ix_students_admission_academic_year_id on stu.students (tenant_id, admission_academic_year_id);
comment on table stu.students is 'Minimal student anchor required by ACD/EXM/FIN/LMS foreign keys. Extend from the STU module design';

create table stu.guardianships (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  guardian_person_id uuid not null,
  relationship text not null,
  is_primary boolean not null default false,
  can_view_academics boolean not null default true,
  can_view_fees boolean not null default true,
  can_pay_fees boolean not null default false,
  valid_from date,
  valid_to date,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_guardianships_status check (status in ('ACTIVE', 'SUSPENDED', 'ENDED')),
  constraint uq_guardianships_tenant_id unique (tenant_id, id)
);
create unique index uq_guardianships_tenant_id_student_id_guardian_person_id on stu.guardianships (tenant_id, student_id, guardian_person_id) where deleted_at is null;
create index ix_guardianships_tenant_id_guardian_person_id on stu.guardianships (tenant_id, guardian_person_id);
create index ix_guardianships_student_id on stu.guardianships (tenant_id, student_id);
comment on table stu.guardianships is 'NEW: Parent/Guardian (R26) -> student link. A guardian sees only linked students; can_pay_fees controls fin.start_payment';


-- ============================================================ iam

create table iam.permissions (
  id uuid primary key default gen_random_uuid(),
  code text not null,
  module text not null,
  resource text,
  action text not null,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create unique index uq_permissions_code on iam.permissions (code) where deleted_at is null;
comment on table iam.permissions is 'Global permission catalogue shared by platform and tenant planes [Mongo: ADM01.permissions]';

create table iam.user_profiles (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid,
  college_id uuid,
  department_id uuid,
  username text,
  email text not null,
  full_name text not null,
  status text not null default 'ACTIVE',
  preferences jsonb default '{}'::jsonb,
  last_seen_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_user_profiles_status check (status in ('ACTIVE', 'SUSPENDED', 'LOCKED', 'INACTIVE')),
  constraint uq_user_profiles_tenant_id unique (tenant_id, id)
);
create unique index uq_user_profiles_tenant_id_email on iam.user_profiles (tenant_id, email) where deleted_at is null;
create unique index uq_user_profiles_tenant_id_username on iam.user_profiles (tenant_id, username) where deleted_at is null;
create unique index uq_user_profiles_tenant_id_person_id on iam.user_profiles (tenant_id, person_id) where deleted_at is null and person_id is not null;
create index ix_user_profiles_tenant_id_college_id_status on iam.user_profiles (tenant_id, college_id, status);
create index ix_user_profiles_person_id on iam.user_profiles (tenant_id, person_id);
create index ix_user_profiles_department_id on iam.user_profiles (tenant_id, department_id);
comment on table iam.user_profiles is '1:1 with auth.users (id = auth uid). passwordHash from Mongo is NOT migrated; credentials live in Supabase Auth [Mongo: ADM02.college_users]';

create table iam.roles (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  code text not null,
  name text not null,
  description text,
  is_system boolean not null default false,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_roles_status check (status in ('ACTIVE', 'INACTIVE')),
  constraint uq_roles_tenant_id unique (tenant_id, id)
);
create unique index uq_roles_tenant_id_code on iam.roles (tenant_id, code) where deleted_at is null and college_id is null;
create unique index uq_roles_tenant_id_college_id_code on iam.roles (tenant_id, college_id, code) where deleted_at is null and college_id is not null;
create index ix_roles_college_id on iam.roles (tenant_id, college_id);
comment on table iam.roles is 'college_id NULL = institute-wide role. is_system roles are seeded per tenant by iam.seed_system_roles() [Mongo: ADM02.college_roles, ADM01.roles]';

create table iam.role_permissions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  role_id uuid not null,
  permission_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_role_permissions_tenant_id unique (tenant_id, id)
);
create unique index uq_role_permissions_tenant_id_role_id_permission_id on iam.role_permissions (tenant_id, role_id, permission_id);
create index ix_role_permissions_permission_id on iam.role_permissions (permission_id);
comment on table iam.role_permissions is 'Normalised from roles.permissions[]';

create table iam.user_groups (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_user_groups_tenant_id unique (tenant_id, id)
);
create unique index uq_user_groups_tenant_id_code on iam.user_groups (tenant_id, code) where deleted_at is null;
comment on table iam.user_groups is 'Groups (Mongo principalType=GROUP)';

create table iam.user_group_members (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  group_id uuid not null,
  user_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_user_group_members_tenant_id unique (tenant_id, id)
);
create unique index uq_user_group_members_tenant_id_group_id_user_id on iam.user_group_members (tenant_id, group_id, user_id);
create index ix_user_group_members_user_id on iam.user_group_members (tenant_id, user_id);

create table iam.role_assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  user_id uuid,
  group_id uuid,
  role_id uuid not null,
  college_id uuid,
  department_id uuid,
  valid_from date,
  valid_to date,
  granted_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_role_assignments_x1 check ((user_id is null) <> (group_id is null)),
  constraint ck_role_assignments_x2 check (valid_to is null or valid_from is null or valid_to >= valid_from),
  constraint uq_role_assignments_tenant_id unique (tenant_id, id)
);
create index ix_role_assignments_tenant_id_user_id_role_id on iam.role_assignments (tenant_id, user_id, role_id);
create index ix_role_assignments_tenant_id_group_id_role_id on iam.role_assignments (tenant_id, group_id, role_id);
create index ix_role_assignments_tenant_id_college_id on iam.role_assignments (tenant_id, college_id);
create index ix_role_assignments_role_id on iam.role_assignments (tenant_id, role_id);
create index ix_role_assignments_department_id on iam.role_assignments (tenant_id, department_id);
create index ix_role_assignments_granted_by on iam.role_assignments (tenant_id, granted_by);
comment on table iam.role_assignments is 'Scope: college_id NULL = institute-wide; department_id narrows further [Mongo: ADM02.role_assignments, ADM01.role_bindings]';

create table iam.department_access (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  user_id uuid not null,
  department_id uuid not null,
  access_level text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_department_access_access_level check (access_level in ('READ', 'WRITE', 'ADMIN')),
  constraint uq_department_access_tenant_id unique (tenant_id, id)
);
create unique index uq_department_access_tenant_id_user_id_department_id on iam.department_access (tenant_id, user_id, department_id) where deleted_at is null;
create index ix_department_access_user_id on iam.department_access (tenant_id, user_id);
create index ix_department_access_department_id on iam.department_access (tenant_id, department_id);
comment on table iam.department_access is '[Mongo: ADM02.department_access]';

create table iam.access_reviews (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  cycle_name text not null,
  reviewer_id uuid,
  status text not null default 'PENDING',
  due_on date,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_access_reviews_status check (status in ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'EXPIRED')),
  constraint uq_access_reviews_tenant_id unique (tenant_id, id)
);
create index ix_access_reviews_reviewer_id on iam.access_reviews (tenant_id, reviewer_id);
comment on table iam.access_reviews is '[Mongo: ADM01.access_reviews]';

create table iam.access_review_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  review_id uuid not null,
  role_assignment_id uuid not null,
  decision text,
  decided_at timestamptz,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_access_review_items_decision check (decision in ('KEEP', 'REVOKE', 'MODIFY')),
  constraint uq_access_review_items_tenant_id unique (tenant_id, id)
);
create unique index uq_access_review_items_tenant_id_review_id_role_as_c7696a57 on iam.access_review_items (tenant_id, review_id, role_assignment_id) where deleted_at is null;
create index ix_access_review_items_review_id on iam.access_review_items (tenant_id, review_id);
create index ix_access_review_items_role_assignment_id on iam.access_review_items (tenant_id, role_assignment_id);
comment on table iam.access_review_items is 'Per-assignment outcome of an access review (new; implied by ADM01.access_reviews)';

create table iam.delegations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  from_user_id uuid not null,
  to_user_id uuid not null,
  role_id uuid not null,
  valid_from timestamptz not null,
  valid_to timestamptz not null,
  reason text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_delegations_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED')),
  constraint ck_delegations_x1 check (valid_to > valid_from),
  constraint uq_delegations_tenant_id unique (tenant_id, id)
);
create index ix_delegations_tenant_id_to_user_id_status on iam.delegations (tenant_id, to_user_id, status);
create index ix_delegations_from_user_id on iam.delegations (tenant_id, from_user_id);
create index ix_delegations_role_id on iam.delegations (tenant_id, role_id);
comment on table iam.delegations is 'NEW: time-boxed delegation of a role you hold (max 90 days). Delegated approvals still obey separation of duties because the actor is the delegate';

create table iam.api_clients (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  user_id uuid not null,
  name text not null,
  owner_user_id uuid,
  description text,
  status text not null default 'ACTIVE',
  allowed_ips text[],
  expires_at timestamptz,
  last_used_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_api_clients_status check (status in ('ACTIVE', 'SUSPENDED', 'REVOKED')),
  constraint uq_api_clients_tenant_id unique (tenant_id, id)
);
create unique index uq_api_clients_tenant_id_user_id on iam.api_clients (tenant_id, user_id) where deleted_at is null;
create index ix_api_clients_user_id on iam.api_clients (user_id);
create index ix_api_clients_owner_user_id on iam.api_clients (tenant_id, owner_user_id);
comment on table iam.api_clients is 'NEW (R27): machine identity. Authenticates as its own auth.users row (service account); permissions come ONLY from iam.api_client_grants';

create table iam.api_client_grants (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  client_id uuid not null,
  permission_id uuid not null,
  college_id uuid,
  department_id uuid,
  resource_scope jsonb default '{}'::jsonb,
  valid_to timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_api_client_grants_tenant_id unique (tenant_id, id)
);
create index ix_api_client_grants_tenant_id_client_id on iam.api_client_grants (tenant_id, client_id);
create index ix_api_client_grants_permission_id on iam.api_client_grants (permission_id);
create index ix_api_client_grants_college_id on iam.api_client_grants (tenant_id, college_id);
create index ix_api_client_grants_department_id on iam.api_client_grants (tenant_id, department_id);
comment on table iam.api_client_grants is 'NEW: explicit scoped grants for an API client (no role inheritance)';

create table iam.role_templates (
  id uuid primary key default gen_random_uuid(),
  code text not null,
  name text not null,
  catalogue_id text,
  kind text,
  scope_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create unique index uq_role_templates_code on iam.role_templates (code) where deleted_at is null;
comment on table iam.role_templates is 'System role definitions copied into every tenant by iam.seed_system_roles()';

create table iam.role_template_permissions (
  id uuid primary key default gen_random_uuid(),
  role_code text not null,
  permission_code text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create unique index uq_role_template_permissions_role_code_permission_code on iam.role_template_permissions (role_code, permission_code) where deleted_at is null;

create table iam.role_grant_rules (
  id uuid primary key default gen_random_uuid(),
  grantor_role text not null,
  grantable_role text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create unique index uq_role_grant_rules_grantor_role_grantable_role on iam.role_grant_rules (grantor_role, grantable_role) where deleted_at is null;
comment on table iam.role_grant_rules is 'Who may assign which system role (matrix section 12). Custom roles: grantor must hold every permission in them';

create table iam.role_hierarchy (
  id uuid primary key default gen_random_uuid(),
  parent_role text not null,
  child_role text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create unique index uq_role_hierarchy_parent_role_child_role on iam.role_hierarchy (parent_role, child_role) where deleted_at is null;
comment on table iam.role_hierarchy is 'Matrix section 17 (informational: authority chain). Permissions are NOT inherited; each role lists its own';

create table iam.transition_rules (
  id uuid primary key default gen_random_uuid(),
  table_schema text not null,
  table_name text not null,
  status_column text not null default 'status',
  from_status text,
  to_status text not null,
  any_permission text[] default '{}',
  sod_distinct_from text[] default '{}',
  block_self text,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create unique index uq_transition_rules_table_schema_table_name_status_24c52dd0 on iam.transition_rules (table_schema, table_name, status_column, to_status) where deleted_at is null and from_status is null;
create unique index uq_transition_rules_table_schema_table_name_status_6ee18bc5 on iam.transition_rules (table_schema, table_name, status_column, to_status, from_status) where deleted_at is null and from_status is not null;
comment on table iam.transition_rules is 'Workflow states that need a specific permission, and separation-of-duties constraints. Enforced by iam.guard_transition()';

create table iam.transition_log (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  table_schema text not null,
  table_name text not null,
  record_id uuid not null,
  status_column text not null,
  from_status text,
  to_status text,
  actor_id uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_transition_log_tenant_id unique (tenant_id, id)
);
create index ix_transition_log_tenant_id_table_schema_table_nam_53c32d47 on iam.transition_log (tenant_id, table_schema, table_name, record_id);
comment on table iam.transition_log is 'Who moved a record into which state (basis for separation-of-duties checks)';


-- ============================================================ cfg

create table cfg.setting_definitions (
  id uuid primary key default gen_random_uuid(),
  setting_key text not null,
  data_type text not null,
  default_value jsonb,
  is_secret boolean not null default false,
  allowed_scopes text[] not null default '{INSTITUTE,COLLEGE}',
  description text,
  effective_from date,
  effective_to date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_setting_definitions_data_type check (data_type in ('STRING', 'INTEGER', 'BOOLEAN', 'JSON', 'SECRET'))
);
create unique index uq_setting_definitions_setting_key on cfg.setting_definitions (setting_key) where deleted_at is null;
comment on table cfg.setting_definitions is 'Platform-level definition + global default of each setting (scope GLOBAL) [Mongo: ADM01.global_settings]';

create table cfg.setting_values (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  setting_key text not null,
  scope text not null,
  college_id uuid,
  value jsonb,
  secret_ref uuid,
  is_overridden boolean not null default false,
  effective_from date,
  effective_to date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_setting_values_scope check (scope in ('INSTITUTE', 'COLLEGE')),
  constraint ck_setting_values_x1 check ((scope = 'COLLEGE') = (college_id is not null)),
  constraint uq_setting_values_tenant_id unique (tenant_id, id)
);
create unique index uq_setting_values_tenant_id_setting_key on cfg.setting_values (tenant_id, setting_key) where deleted_at is null and college_id is null;
create unique index uq_setting_values_tenant_id_setting_key_college_id on cfg.setting_values (tenant_id, setting_key, college_id) where deleted_at is null and college_id is not null;
create index ix_setting_values_college_id on cfg.setting_values (tenant_id, college_id);
comment on table cfg.setting_values is 'Institute/college overrides. Resolve with cfg.effective_setting(). Secrets: secret_ref -> vault.secrets. History: audit.change_log (cfg.v_setting_history) [Mongo: ADM02.college_settings]';

create table cfg.feature_flags (
  id uuid primary key default gen_random_uuid(),
  flag_key text not null,
  name text,
  description text,
  enabled boolean not null default false,
  rollout_percentage smallint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_feature_flags_rollout_percentage check (rollout_percentage between 0 and 100)
);
create unique index uq_feature_flags_flag_key on cfg.feature_flags (flag_key) where deleted_at is null;
comment on table cfg.feature_flags is '[Mongo: ADM01.feature_flags]';

create table cfg.feature_flag_rules (
  id uuid primary key default gen_random_uuid(),
  flag_id uuid not null,
  rule_type text not null,
  params jsonb default '{}'::jsonb,
  priority integer not null default 100,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create index ix_feature_flag_rules_flag_id on cfg.feature_flag_rules (flag_id);
comment on table cfg.feature_flag_rules is 'Normalised from feature_flags.rules[]';

create table cfg.feature_overrides (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  flag_id uuid not null,
  college_id uuid,
  enabled boolean not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_feature_overrides_tenant_id unique (tenant_id, id)
);
create unique index uq_feature_overrides_tenant_id_flag_id on cfg.feature_overrides (tenant_id, flag_id) where deleted_at is null and college_id is null;
create unique index uq_feature_overrides_tenant_id_flag_id_college_id on cfg.feature_overrides (tenant_id, flag_id, college_id) where deleted_at is null and college_id is not null;
create index ix_feature_overrides_flag_id on cfg.feature_overrides (flag_id);
create index ix_feature_overrides_college_id on cfg.feature_overrides (tenant_id, college_id);
comment on table cfg.feature_overrides is '[Mongo: ADM02.feature_overrides]';

create table cfg.policies (
  id uuid primary key default gen_random_uuid(),
  policy_code text not null,
  name text,
  category text,
  rules jsonb not null default '{}'::jsonb,
  enforcement_mode text not null default 'ENFORCE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_policies_enforcement_mode check (enforcement_mode in ('ENFORCE', 'AUDIT', 'DISABLED'))
);
create unique index uq_policies_policy_code on cfg.policies (policy_code) where deleted_at is null;
comment on table cfg.policies is '[Mongo: ADM01.global_policies]';

create table cfg.data_retention_policies (
  id uuid primary key default gen_random_uuid(),
  policy_code text not null,
  entity_type text not null,
  retention_days integer not null,
  action text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_data_retention_policies_retention_days check (retention_days > 0),
  constraint ck_data_retention_policies_action check (action in ('ARCHIVE', 'DELETE', 'ANONYMIZE'))
);
create unique index uq_data_retention_policies_policy_code on cfg.data_retention_policies (policy_code) where deleted_at is null;
comment on table cfg.data_retention_policies is '[Mongo: ADM01.data_retention_policies]';

create table cfg.data_classifications (
  id uuid primary key default gen_random_uuid(),
  classification_code text not null,
  sensitivity_level text not null,
  encryption_required boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_data_classifications_sensitivity_level check (sensitivity_level in ('LOW', 'MEDIUM', 'HIGH', 'RESTRICTED'))
);
create unique index uq_data_classifications_classification_code on cfg.data_classifications (classification_code) where deleted_at is null;
comment on table cfg.data_classifications is '[Mongo: ADM01.data_classifications]';


-- ============================================================ plat

create table plat.platform_admins (
  id uuid primary key default gen_random_uuid(),
  user_id uuid not null,
  full_name text,
  role_code text not null,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_platform_admins_role_code check (role_code in ('SUPER_ADMIN', 'SYSTEM_ADMIN', 'SUPPORT', 'BILLING', 'AUDITOR')),
  constraint ck_platform_admins_status check (status in ('ACTIVE', 'SUSPENDED', 'LOCKED', 'INACTIVE'))
);
create unique index uq_platform_admins_user_id on plat.platform_admins (user_id) where deleted_at is null;
create index ix_platform_admins_user_id on plat.platform_admins (user_id);
comment on table plat.platform_admins is 'CampSync operators. passwordHash NOT migrated (Supabase Auth). Hard-separated from tenant RBAC [Mongo: ADM01.admin_users]';

create table plat.subscription_plans (
  id uuid primary key default gen_random_uuid(),
  plan_code text not null,
  name text not null,
  billing_cycle text not null,
  price numeric(18,2) not null,
  currency_code char(3) not null default 'INR',
  is_published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_subscription_plans_billing_cycle check (billing_cycle in ('MONTHLY', 'QUARTERLY', 'ANNUALLY')),
  constraint ck_subscription_plans_price check (price >= 0)
);
create unique index uq_subscription_plans_plan_code on plat.subscription_plans (plan_code) where deleted_at is null;
comment on table plat.subscription_plans is '[Mongo: ADM01.subscription_plans]';

create table plat.plan_entitlements (
  id uuid primary key default gen_random_uuid(),
  plan_id uuid not null,
  entitlement_key text not null,
  limit_value bigint,
  is_enabled boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz
);
create unique index uq_plan_entitlements_plan_id_entitlement_key on plat.plan_entitlements (plan_id, entitlement_key) where deleted_at is null;
create index ix_plan_entitlements_plan_id on plat.plan_entitlements (plan_id);
comment on table plat.plan_entitlements is 'Normalised from subscription_plans.entitlements[]';

create table plat.subscriptions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  plan_id uuid not null,
  start_date date not null,
  end_date date,
  auto_renew boolean not null default true,
  status text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_subscriptions_status check (status in ('TRIAL', 'ACTIVE', 'PAST_DUE', 'SUSPENDED', 'CANCELLED')),
  constraint ck_subscriptions_x1 check (end_date is null or end_date >= start_date),
  constraint uq_subscriptions_tenant_id unique (tenant_id, id)
);
create index ix_subscriptions_tenant_id_status_end_date on plat.subscriptions (tenant_id, status, end_date);
create index ix_subscriptions_plan_id on plat.subscriptions (plan_id);
comment on table plat.subscriptions is '[Mongo: ADM01.subscriptions]';

create table plat.invoices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  subscription_id uuid not null,
  invoice_number text not null,
  issued_on date,
  due_date date,
  amount numeric(18,2) not null,
  currency_code char(3) not null default 'INR',
  status text not null default 'DRAFT',
  paid_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_invoices_amount check (amount >= 0),
  constraint ck_invoices_status check (status in ('DRAFT', 'ISSUED', 'PAID', 'VOID', 'UNCOLLECTIBLE')),
  constraint uq_invoices_tenant_id unique (tenant_id, id)
);
create unique index uq_invoices_tenant_id_invoice_number on plat.invoices (tenant_id, invoice_number) where deleted_at is null;
create index ix_invoices_subscription_id on plat.invoices (tenant_id, subscription_id);
comment on table plat.invoices is '[Mongo: ADM01.invoices]';

create table plat.invoice_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  invoice_id uuid not null,
  description text not null,
  quantity numeric(18,4) not null default 1,
  unit_price numeric(18,2) not null,
  amount numeric(18,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_invoice_lines_unit_price check (unit_price >= 0),
  constraint ck_invoice_lines_amount check (amount >= 0),
  constraint uq_invoice_lines_tenant_id unique (tenant_id, id)
);
create index ix_invoice_lines_invoice_id on plat.invoice_lines (tenant_id, invoice_id);
comment on table plat.invoice_lines is 'Line items (normalised; ADM01.invoices held a single amount)';

create table plat.gateway_transactions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  invoice_id uuid,
  gateway text not null,
  gateway_ref text,
  amount numeric(18,2) not null,
  currency_code char(3) not null default 'INR',
  status text not null default 'PENDING',
  raw_response jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_gateway_transactions_amount check (amount >= 0),
  constraint ck_gateway_transactions_status check (status in ('PENDING', 'SUCCESS', 'FAILED', 'REFUNDED')),
  constraint uq_gateway_transactions_tenant_id unique (tenant_id, id)
);
create unique index uq_gateway_transactions_tenant_id_gateway_gateway_ref on plat.gateway_transactions (tenant_id, gateway, gateway_ref) where deleted_at is null and gateway_ref is not null;
create index ix_gateway_transactions_invoice_id on plat.gateway_transactions (tenant_id, invoice_id);
comment on table plat.gateway_transactions is 'SaaS billing gateway. Student fee payments live in fin.gateway_transactions [Mongo: ADM01.billing_gateway_transactions]';

create table plat.usage_metrics (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  metric_name text not null,
  dimension text,
  metric_value numeric(18,4) not null,
  recorded_on date not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_usage_metrics_metric_value check (metric_value >= 0),
  constraint uq_usage_metrics_tenant_id unique (tenant_id, id)
);
create index ix_usage_metrics_tenant_id_metric_name_recorded_on on plat.usage_metrics (tenant_id, metric_name, recorded_on desc);
comment on table plat.usage_metrics is '[Mongo: ADM01.usage_metrics]';

create table plat.service_health (
  id uuid primary key default gen_random_uuid(),
  service_name text not null,
  status text not null,
  latency_ms integer,
  last_heartbeat timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_service_health_status check (status in ('HEALTHY', 'DEGRADED', 'UNHEALTHY'))
);
create unique index uq_service_health_service_name on plat.service_health (service_name) where deleted_at is null;
comment on table plat.service_health is '[Mongo: ADM01.platform_health]';

create table plat.alerts (
  id uuid primary key default gen_random_uuid(),
  tenant_ref_id uuid,
  severity text not null,
  title text not null,
  detail text,
  status text not null default 'ACTIVE',
  acknowledged_by uuid,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_alerts_severity check (severity in ('INFO', 'WARNING', 'ERROR', 'CRITICAL')),
  constraint ck_alerts_status check (status in ('ACTIVE', 'ACKNOWLEDGED', 'RESOLVED'))
);
create index ix_alerts_status_severity on plat.alerts (status, severity);
create index ix_alerts_tenant_ref_id on plat.alerts (tenant_ref_id);
comment on table plat.alerts is '[Mongo: ADM01.alerts]';

create table plat.tenant_provisioning (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  workflow_instance_id uuid,
  current_step text not null,
  attempt integer not null default 1,
  status text not null default 'REQUESTED',
  idempotency_key text,
  requested_by uuid,
  failure_reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tenant_provisioning_status check (status in ('REQUESTED', 'VALIDATED', 'PROVISIONING', 'COMPLETED', 'FAILED')),
  constraint uq_tenant_provisioning_tenant_id unique (tenant_id, id)
);
create unique index uq_tenant_provisioning_tenant_id_idempotency_key on plat.tenant_provisioning (tenant_id, idempotency_key) where deleted_at is null and idempotency_key is not null;
create index ix_tenant_provisioning_college_id on plat.tenant_provisioning (tenant_id, college_id);
create index ix_tenant_provisioning_workflow_instance_id on plat.tenant_provisioning (tenant_id, workflow_instance_id);
comment on table plat.tenant_provisioning is '[Mongo: ADM01.tenant_provisioning]';


-- ============================================================ wf

create table wf.definitions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  entity_type text not null,
  module text,
  college_id uuid,
  status text not null default 'ACTIVE',
  version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_definitions_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_definitions_tenant_id unique (tenant_id, id)
);
create unique index uq_definitions_tenant_id_code on wf.definitions (tenant_id, code) where deleted_at is null;
create index ix_definitions_college_id on wf.definitions (tenant_id, college_id);
comment on table wf.definitions is 'One definition per approvable entity type (fee structure, payroll run, leave, results ...) [Mongo: EXM05.approval_workflows]';

create table wf.definition_steps (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  definition_id uuid not null,
  step_no integer not null,
  name text not null,
  approver_type text not null,
  approver_role_id uuid,
  approver_user_id uuid,
  mode text not null default 'ANY',
  quorum integer,
  sla_hours integer,
  escalate_after_hours integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_definition_steps_step_no check (step_no > 0),
  constraint ck_definition_steps_approver_type check (approver_type in ('ROLE', 'USER', 'DEPARTMENT_HEAD', 'REPORTING_MANAGER', 'GROUP')),
  constraint ck_definition_steps_mode check (mode in ('ANY', 'ALL', 'QUORUM')),
  constraint uq_definition_steps_tenant_id unique (tenant_id, id)
);
create unique index uq_definition_steps_tenant_id_definition_id_step_no on wf.definition_steps (tenant_id, definition_id, step_no) where deleted_at is null;
create index ix_definition_steps_definition_id on wf.definition_steps (tenant_id, definition_id);
create index ix_definition_steps_approver_role_id on wf.definition_steps (tenant_id, approver_role_id);
create index ix_definition_steps_approver_user_id on wf.definition_steps (tenant_id, approver_user_id);

create table wf.policies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  definition_id uuid not null,
  name text,
  condition jsonb not null default '{}'::jsonb,
  priority integer not null default 100,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_policies_tenant_id unique (tenant_id, id)
);
create index ix_policies_definition_id on wf.policies (tenant_id, definition_id);
comment on table wf.policies is 'Routing conditions (amount thresholds, department ...) that select a definition/steps [Mongo: EXM05.approval_policies, FIN10.budget_approval_rules]';

create table wf.instances (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  definition_id uuid,
  college_id uuid,
  workflow_type text not null,
  entity_type text,
  entity_id uuid,
  status text not null default 'PENDING',
  requested_by uuid,
  current_step_no integer,
  submitted_at timestamptz,
  completed_at timestamptz,
  payload_snapshot jsonb,
  idempotency_key text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_instances_status check (status in ('PENDING', 'RUNNING', 'IN_PROGRESS', 'APPROVED', 'REJECTED', 'CANCELLED', 'ESCALATED', 'EXPIRED', 'COMPLETED', 'FAILED', 'SUSPENDED')),
  constraint uq_instances_tenant_id unique (tenant_id, id)
);
create index ix_instances_tenant_id_entity_type_entity_id on wf.instances (tenant_id, entity_type, entity_id);
create index ix_instances_tenant_id_status_workflow_type on wf.instances (tenant_id, status, workflow_type);
create index ix_instances_definition_id on wf.instances (tenant_id, definition_id);
create index ix_instances_college_id on wf.instances (tenant_id, college_id);
create index ix_instances_requested_by on wf.instances (tenant_id, requested_by);
comment on table wf.instances is 'Runtime approval/saga instance. Domain rows keep an approval_status + workflow_instance_id [Mongo: EXM05.approval_instances, ADM01.workflow_instances, ADM02.approval_requests, EXM05.marks_approval_snapshots, FIN01.fee_structure_approvals, FIN03.expense_approvals, FIN06.scholarship_approvals, FIN07.payroll_approvals, FIN10.budget_approvals, FIN10.budget_approval_history, HRM02.leave_approvals, HRM05.payroll_approvals, HRM06.recruitment_requisition_approvals, HRM06.offer_approvals, LMS04.lesson_approvals, EXM08.result_approvals, EXM04.moderation_records]';

create table wf.tasks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  instance_id uuid not null,
  step_no integer,
  assignee_id uuid,
  role_id uuid,
  status text not null default 'OPEN',
  due_at timestamptz,
  completed_at timestamptz,
  college_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tasks_status check (status in ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'BLOCKED', 'SKIPPED', 'DELEGATED')),
  constraint uq_tasks_tenant_id unique (tenant_id, id)
);
create index ix_tasks_tenant_id_assignee_id_status on wf.tasks (tenant_id, assignee_id, status);
create index ix_tasks_tenant_id_instance_id_step_no on wf.tasks (tenant_id, instance_id, step_no);
create index ix_tasks_role_id on wf.tasks (tenant_id, role_id);
create index ix_tasks_college_id on wf.tasks (tenant_id, college_id);
comment on table wf.tasks is '[Mongo: ADM02.workflow_tasks, EXM05.approval_tasks]';

create table wf.decisions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  instance_id uuid not null,
  task_id uuid,
  decision text not null,
  decided_by uuid not null,
  comment text,
  decided_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_decisions_decision check (decision in ('APPROVE', 'REJECT', 'RETURN', 'ABSTAIN')),
  constraint uq_decisions_tenant_id unique (tenant_id, id)
);
create index ix_decisions_tenant_id_instance_id_decided_at on wf.decisions (tenant_id, instance_id, decided_at);
create index ix_decisions_task_id on wf.decisions (tenant_id, task_id);
create index ix_decisions_decided_by on wf.decisions (tenant_id, decided_by);
comment on table wf.decisions is '[Mongo: EXM05.approval_decisions]';

create table wf.delegations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  from_user_id uuid not null,
  to_user_id uuid not null,
  definition_id uuid,
  valid_from timestamptz not null,
  valid_to timestamptz not null,
  reason text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_delegations_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED')),
  constraint ck_delegations_x1 check (valid_to > valid_from),
  constraint uq_delegations_tenant_id unique (tenant_id, id)
);
create index ix_delegations_from_user_id on wf.delegations (tenant_id, from_user_id);
create index ix_delegations_to_user_id on wf.delegations (tenant_id, to_user_id);
create index ix_delegations_definition_id on wf.delegations (tenant_id, definition_id);
comment on table wf.delegations is '[Mongo: EXM05.approval_delegations]';

create table wf.escalations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  task_id uuid not null,
  escalated_to uuid,
  reason text,
  escalated_at timestamptz not null default now(),
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_escalations_tenant_id unique (tenant_id, id)
);
create index ix_escalations_task_id on wf.escalations (tenant_id, task_id);
create index ix_escalations_escalated_to on wf.escalations (tenant_id, escalated_to);
comment on table wf.escalations is '[Mongo: EXM05.approval_escalations]';

create table wf.exceptions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  instance_id uuid not null,
  exception_type text not null,
  reason text not null,
  granted_by uuid,
  valid_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_exceptions_tenant_id unique (tenant_id, id)
);
create index ix_exceptions_instance_id on wf.exceptions (tenant_id, instance_id);
create index ix_exceptions_granted_by on wf.exceptions (tenant_id, granted_by);
comment on table wf.exceptions is '[Mongo: EXM05.approval_exceptions]';


-- ============================================================ doc

create table doc.document_types (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  category text,
  module text,
  default_classification text not null default 'INTERNAL',
  requires_signature boolean not null default false,
  retention_policy_code text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_document_types_default_classification check (default_classification in ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')),
  constraint uq_document_types_tenant_id unique (tenant_id, id)
);
create unique index uq_document_types_tenant_id_code on doc.document_types (tenant_id, code) where deleted_at is null;
comment on table doc.document_types is '[Mongo: EXM06.document_types]';

create table doc.documents (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  document_type_id uuid,
  title text not null,
  classification text not null default 'INTERNAL',
  status text not null default 'DRAFT',
  owner_id uuid,
  storage_bucket text,
  storage_path text,
  mime_type text,
  size_bytes bigint,
  checksum_sha256 text,
  current_version_no integer not null default 1,
  published_at timestamptz,
  expires_at timestamptz,
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_documents_classification check (classification in ('PUBLIC', 'INTERNAL', 'CONFIDENTIAL', 'RESTRICTED')),
  constraint ck_documents_status check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PUBLISHED', 'ARCHIVED', 'CANCELLED')),
  constraint ck_documents_size_bytes check (size_bytes >= 0),
  constraint uq_documents_tenant_id unique (tenant_id, id)
);
create index ix_documents_tenant_id_document_type_id_status on doc.documents (tenant_id, document_type_id, status);
create index ix_documents_tenant_id_owner_id on doc.documents (tenant_id, owner_id);
create index ix_documents_college_id on doc.documents (tenant_id, college_id);
create index ix_documents_workflow_instance_id on doc.documents (tenant_id, workflow_instance_id);
comment on table doc.documents is 'Every stored file = one row pointing at storage.objects. Domain tables link via document_id or doc.document_links [Mongo: ADM02.document_metadata, EXM06.document_instances, HRM01.employee_documents, HRM06.candidate_documents, HRM06.onboarding_documents, FIN03.expense_documents, FIN06.scholarship_application_documents, FIN09.evidence_documents, EXM03.hall_ticket_documents, FIN05.receipt_documents, FIN07.payslip_documents, FIN10.budget_documents, FIN04.tax_documents]';

create table doc.document_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid not null,
  version_no integer not null,
  storage_path text not null,
  size_bytes bigint,
  checksum_sha256 text,
  change_note text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_document_versions_version_no check (version_no > 0),
  constraint uq_document_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_document_versions_tenant_id_document_id_version_no on doc.document_versions (tenant_id, document_id, version_no);
comment on table doc.document_versions is '[Mongo: EXM06.document_versions, EXM06.document_snapshots]';

create table doc.document_links (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid not null,
  entity_type text not null,
  entity_id uuid not null,
  relation text not null default 'ATTACHMENT',
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_document_links_tenant_id unique (tenant_id, id)
);
create unique index uq_document_links_tenant_id_document_id_entity_typ_4949eedf on doc.document_links (tenant_id, document_id, entity_type, entity_id, relation);
create index ix_document_links_tenant_id_entity_type_entity_id on doc.document_links (tenant_id, entity_type, entity_id);
comment on table doc.document_links is 'Polymorphic attachment link (employee documents, expense bills, evidence ...) [Mongo: EXM06.document_subjects]';

create table doc.templates (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  template_kind text not null,
  code text not null,
  name text not null,
  channel text,
  status text not null default 'DRAFT',
  active_version_no integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_templates_template_kind check (template_kind in ('DOCUMENT', 'HALL_TICKET', 'RECEIPT', 'PAYSLIP', 'MESSAGE', 'REPORT', 'CERTIFICATE')),
  constraint ck_templates_channel check (channel in ('EMAIL', 'SMS', 'PUSH', 'WHATSAPP', 'IN_APP', 'PRINT')),
  constraint ck_templates_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_templates_tenant_id unique (tenant_id, id)
);
create unique index uq_templates_tenant_id_template_kind_code on doc.templates (tenant_id, template_kind, code) where deleted_at is null;
create index ix_templates_college_id on doc.templates (tenant_id, college_id);
comment on table doc.templates is 'One template registry (documents, hall tickets, receipts, messages, reports) [Mongo: EXM06.document_templates, EXM03.hall_ticket_templates, FIN05.receipt_templates, CAM02.message_templates, EXM09.report_templates, LMS09.certificate_templates]';

create table doc.template_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  template_id uuid not null,
  version_no integer not null,
  subject text,
  body text not null,
  variables jsonb default '{}'::jsonb,
  status text not null default 'DRAFT',
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_template_versions_version_no check (version_no > 0),
  constraint ck_template_versions_status check (status in ('DRAFT', 'PUBLISHED', 'RETIRED')),
  constraint uq_template_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_template_versions_tenant_id_template_id_version_no on doc.template_versions (tenant_id, template_id, version_no) where deleted_at is null;
create index ix_template_versions_template_id on doc.template_versions (tenant_id, template_id);
comment on table doc.template_versions is '[Mongo: EXM06.document_template_versions, FIN05.receipt_template_versions]';

create table doc.signatures (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid not null,
  signer_id uuid,
  signature_type text not null,
  signed_at timestamptz not null,
  certificate_ref text,
  signature_hash text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_signatures_signature_type check (signature_type in ('DIGITAL', 'ESIGN', 'STAMP')),
  constraint uq_signatures_tenant_id unique (tenant_id, id)
);
create index ix_signatures_document_id on doc.signatures (tenant_id, document_id);
create index ix_signatures_signer_id on doc.signatures (tenant_id, signer_id);
comment on table doc.signatures is '[Mongo: EXM06.document_signatures]';

create table doc.verifications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid,
  entity_type text,
  entity_id uuid,
  verification_code text not null,
  result text not null,
  verified_at timestamptz not null default now(),
  client_ip inet,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_verifications_result check (result in ('VALID', 'INVALID', 'EXPIRED', 'REVOKED')),
  constraint uq_verifications_tenant_id unique (tenant_id, id)
);
create index ix_verifications_tenant_id_verification_code on doc.verifications (tenant_id, verification_code);
create index ix_verifications_tenant_id_entity_type_entity_id on doc.verifications (tenant_id, entity_type, entity_id);
create index ix_verifications_document_id on doc.verifications (tenant_id, document_id);
comment on table doc.verifications is '[Mongo: EXM06.document_verifications, EXM03.hall_ticket_verification, FIN05.receipt_verifications, LMS09.credential_verifications]';

create table doc.print_jobs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid,
  entity_type text,
  entity_id uuid,
  requested_by uuid,
  copies integer not null default 1,
  status text not null default 'QUEUED',
  printed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_print_jobs_status check (status in ('QUEUED', 'PRINTING', 'DONE', 'FAILED')),
  constraint uq_print_jobs_tenant_id unique (tenant_id, id)
);
create index ix_print_jobs_document_id on doc.print_jobs (tenant_id, document_id);
create index ix_print_jobs_requested_by on doc.print_jobs (tenant_id, requested_by);
comment on table doc.print_jobs is '[Mongo: EXM06.document_print_jobs, EXM03.hall_ticket_prints]';

create table doc.print_job_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  print_job_id uuid not null,
  document_id uuid,
  seq integer not null default 1,
  status text not null default 'QUEUED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_print_job_items_tenant_id unique (tenant_id, id)
);
create index ix_print_job_items_print_job_id on doc.print_job_items (tenant_id, print_job_id);
create index ix_print_job_items_document_id on doc.print_job_items (tenant_id, document_id);
comment on table doc.print_job_items is '[Mongo: EXM06.document_print_items]';

create table doc.lifecycle_requests (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid,
  entity_type text,
  entity_id uuid,
  request_type text not null,
  reason text not null,
  status text not null default 'PENDING',
  requested_by uuid,
  workflow_instance_id uuid,
  new_document_id uuid,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_lifecycle_requests_request_type check (request_type in ('REISSUE', 'CANCEL', 'CORRECTION')),
  constraint ck_lifecycle_requests_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'COMPLETED')),
  constraint uq_lifecycle_requests_tenant_id unique (tenant_id, id)
);
create index ix_lifecycle_requests_document_id on doc.lifecycle_requests (tenant_id, document_id);
create index ix_lifecycle_requests_requested_by on doc.lifecycle_requests (tenant_id, requested_by);
create index ix_lifecycle_requests_workflow_instance_id on doc.lifecycle_requests (tenant_id, workflow_instance_id);
create index ix_lifecycle_requests_new_document_id on doc.lifecycle_requests (tenant_id, new_document_id);
comment on table doc.lifecycle_requests is 'Reissue / cancellation / correction requests for any issued document [Mongo: EXM06.document_reissues, EXM06.document_cancellations, FIN05.receipt_reissue_requests, FIN05.receipt_cancellation_requests]';

create table doc.document_deliveries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid not null,
  recipient_person_id uuid,
  channel text not null,
  status text not null default 'PENDING',
  delivered_at timestamptz,
  client_ip inet,
  delivery_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_document_deliveries_channel check (channel in ('EMAIL', 'SMS', 'PORTAL', 'DOWNLOAD', 'PRINT', 'COURIER')),
  constraint ck_document_deliveries_status check (status in ('PENDING', 'SENT', 'DELIVERED', 'FAILED', 'DOWNLOADED')),
  constraint uq_document_deliveries_tenant_id unique (tenant_id, id)
);
create index ix_document_deliveries_tenant_id_document_id_channel on doc.document_deliveries (tenant_id, document_id, channel);
create index ix_document_deliveries_recipient_person_id on doc.document_deliveries (tenant_id, recipient_person_id);
comment on table doc.document_deliveries is 'Who received/downloaded a document. delivery_id optionally points to cam.deliveries [Mongo: EXM06.document_delivery, FIN05.receipt_delivery_records, EXM03.hall_ticket_downloads]';

create table doc.document_renditions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  document_id uuid not null,
  rendition_type text not null,
  storage_path text not null,
  size_bytes bigint,
  status text not null default 'READY',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_document_renditions_rendition_type check (rendition_type in ('THUMBNAIL', 'PREVIEW', 'TRANSCODE', 'OCR', 'PDF')),
  constraint ck_document_renditions_status check (status in ('PENDING', 'READY', 'FAILED')),
  constraint uq_document_renditions_tenant_id unique (tenant_id, id)
);
create unique index uq_document_renditions_tenant_id_document_id_rendition_type on doc.document_renditions (tenant_id, document_id, rendition_type) where deleted_at is null;
create index ix_document_renditions_document_id on doc.document_renditions (tenant_id, document_id);
comment on table doc.document_renditions is 'Derived previews/thumbnails; generation runs as sys.jobs [Mongo: LMS07.resource_previews, LMS02.content_processing, LMS07.resource_processing]';


-- ============================================================ sys

create table sys.outbox_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  service text not null,
  aggregate_type text not null,
  aggregate_id uuid,
  event_type text not null,
  payload jsonb not null default '{}'::jsonb,
  headers jsonb default '{}'::jsonb,
  status text not null default 'PENDING',
  attempts integer not null default 0,
  next_attempt_at timestamptz,
  dispatched_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_outbox_events_status check (status in ('PENDING', 'DISPATCHED', 'FAILED')),
  constraint uq_outbox_events_tenant_id unique (tenant_id, id)
);
create index ix_outbox_events_tenant_id_status_next_attempt_at on sys.outbox_events (tenant_id, status, next_attempt_at);
create index ix_outbox_events_tenant_id_service_event_type on sys.outbox_events (tenant_id, service, event_type);
comment on table sys.outbox_events is 'Transactional outbox. Poll with pg_cron / Supabase Queues (pgmq) or Database Webhooks';

create table sys.inbox_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  source_service text not null,
  event_id text not null,
  event_type text not null,
  payload jsonb,
  status text not null default 'RECEIVED',
  processed_at timestamptz,
  error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_inbox_events_status check (status in ('RECEIVED', 'PROCESSED', 'FAILED')),
  constraint uq_inbox_events_tenant_id unique (tenant_id, id)
);
create unique index uq_inbox_events_tenant_id_source_service_event_id on sys.inbox_events (tenant_id, source_service, event_id);

create table sys.idempotency_keys (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scope text not null,
  idempotency_key text not null,
  request_hash text,
  response jsonb,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_idempotency_keys_tenant_id unique (tenant_id, id)
);
create unique index uq_idempotency_keys_tenant_id_scope_idempotency_key on sys.idempotency_keys (tenant_id, scope, idempotency_key);
create index ix_idempotency_keys_tenant_id_expires_at on sys.idempotency_keys (tenant_id, expires_at);

create table sys.dead_letter_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  source_service text not null,
  original_event_id text,
  failure_reason text not null,
  payload jsonb,
  status text not null default 'FAILED',
  replayed_at timestamptz,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_dead_letter_events_status check (status in ('FAILED', 'REPLAYED', 'DISCARDED')),
  constraint uq_dead_letter_events_tenant_id unique (tenant_id, id)
);
create index ix_dead_letter_events_tenant_id_status_created_at on sys.dead_letter_events (tenant_id, status, created_at desc);
comment on table sys.dead_letter_events is '[Mongo: FIN09.audit_event_failures]';

create table sys.jobs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  module text not null,
  job_type text not null,
  entity_type text,
  entity_id uuid,
  status text not null default 'QUEUED',
  params jsonb default '{}'::jsonb,
  result jsonb,
  progress_pct smallint,
  error text,
  scheduled_for timestamptz,
  started_at timestamptz,
  finished_at timestamptz,
  requested_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_jobs_status check (status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'CANCELLED')),
  constraint ck_jobs_progress_pct check (progress_pct between 0 and 100),
  constraint uq_jobs_tenant_id unique (tenant_id, id)
);
create index ix_jobs_tenant_id_module_job_type_status on sys.jobs (tenant_id, module, job_type, status);
create index ix_jobs_tenant_id_scheduled_for on sys.jobs (tenant_id, scheduled_for);
comment on table sys.jobs is 'Async work: hall-ticket generation, result processing, report generation, alerts, notification fan-out ... [Mongo: LMS08.progress_calculations]';

create table sys.entity_locks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  entity_type text not null,
  entity_id uuid not null,
  lock_type text not null default 'EXCLUSIVE',
  holder_id uuid,
  expires_at timestamptz not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_entity_locks_tenant_id unique (tenant_id, id)
);
create unique index uq_entity_locks_tenant_id_entity_type_entity_id_lock_type on sys.entity_locks (tenant_id, entity_type, entity_id, lock_type);
create index ix_entity_locks_tenant_id_expires_at on sys.entity_locks (tenant_id, expires_at);
comment on table sys.entity_locks is 'Lease locks for long-running edits (registration, valuation). Short locks: use SELECT ... FOR UPDATE / advisory locks';

create table sys.import_jobs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  entity_type text not null,
  mode text not null default 'INSERT',
  status text not null default 'PENDING',
  total_rows integer not null default 0,
  processed_rows integer not null default 0,
  failed_rows integer not null default 0,
  source_document_id uuid,
  started_at timestamptz,
  finished_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_import_jobs_entity_type check (entity_type in ('STUDENT', 'FACULTY', 'DEPARTMENT', 'COURSE', 'OTHER')),
  constraint ck_import_jobs_mode check (mode in ('INSERT', 'UPSERT', 'REPLACE')),
  constraint ck_import_jobs_status check (status in ('PENDING', 'PROCESSING', 'COMPLETED', 'FAILED')),
  constraint uq_import_jobs_tenant_id unique (tenant_id, id)
);
create index ix_import_jobs_college_id on sys.import_jobs (tenant_id, college_id);
create index ix_import_jobs_source_document_id on sys.import_jobs (tenant_id, source_document_id);
comment on table sys.import_jobs is '[Mongo: ADM02.user_import_jobs]';

create table sys.import_rows (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  job_id uuid not null,
  row_number integer not null,
  status text not null default 'PENDING',
  raw_data jsonb,
  errors jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_import_rows_row_number check (row_number > 0),
  constraint ck_import_rows_status check (status in ('PENDING', 'SUCCESS', 'VALIDATION_FAILED', 'SYSTEM_ERROR')),
  constraint uq_import_rows_tenant_id unique (tenant_id, id)
);
create unique index uq_import_rows_tenant_id_job_id_row_number on sys.import_rows (tenant_id, job_id, row_number);
comment on table sys.import_rows is '[Mongo: ADM02.data_import_rows]';

create table sys.data_quality_issues (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  job_id uuid,
  import_row_id uuid,
  severity text not null,
  rule_name text not null,
  message text,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_data_quality_issues_severity check (severity in ('WARNING', 'ERROR')),
  constraint uq_data_quality_issues_tenant_id unique (tenant_id, id)
);
create index ix_data_quality_issues_college_id on sys.data_quality_issues (tenant_id, college_id);
create index ix_data_quality_issues_job_id on sys.data_quality_issues (tenant_id, job_id);
create index ix_data_quality_issues_import_row_id on sys.data_quality_issues (tenant_id, import_row_id);
comment on table sys.data_quality_issues is '[Mongo: ADM02.data_quality_issues]';

create table sys.export_jobs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  entity_type text not null,
  format text not null default 'CSV',
  status text not null default 'REQUESTED',
  requested_by uuid,
  result_document_id uuid,
  expires_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_export_jobs_format check (format in ('CSV', 'XLSX', 'PDF', 'JSON')),
  constraint ck_export_jobs_status check (status in ('REQUESTED', 'PROCESSING', 'COMPLETED', 'FAILED')),
  constraint uq_export_jobs_tenant_id unique (tenant_id, id)
);
create index ix_export_jobs_college_id on sys.export_jobs (tenant_id, college_id);
create index ix_export_jobs_requested_by on sys.export_jobs (tenant_id, requested_by);
create index ix_export_jobs_result_document_id on sys.export_jobs (tenant_id, result_document_id);
comment on table sys.export_jobs is '[Mongo: ADM01.export_requests, ADM02.data_export_requests]';

create table sys.id_map (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  source_collection text not null,
  mongo_id text not null,
  target_table text not null,
  target_id uuid not null,
  migrated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint uq_id_map_tenant_id unique (tenant_id, id)
);
create unique index uq_id_map_tenant_id_source_collection_mongo_id on sys.id_map (tenant_id, source_collection, mongo_id);
create index ix_id_map_tenant_id_target_table_target_id on sys.id_map (tenant_id, target_table, target_id);
comment on table sys.id_map is 'One-off Mongo ObjectId -> Postgres uuid map used by the ETL; drop after cut-over';


-- ============================================================ audit

create table audit.change_log (
  id uuid not null default gen_random_uuid(),
  tenant_id uuid default core.current_tenant_id(),
  table_schema text not null,
  table_name text not null,
  record_id uuid,
  action text not null,
  changed_fields text[],
  old_data jsonb,
  new_data jsonb,
  actor_id uuid,
  request_id text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_change_log_action check (action in ('I', 'U', 'D')),
  primary key (id, created_at)
) partition by range (created_at);
create table audit.change_log_default partition of audit.change_log default;
create index ix_change_log_tenant_id_table_schema_table_name_re_fe9b7ce2 on audit.change_log (tenant_id, table_schema, table_name, record_id, created_at desc);
create index ix_change_log_tenant_id_actor_id_created_at on audit.change_log (tenant_id, actor_id, created_at desc);
comment on table audit.change_log is 'Row-level before/after images written by audit.capture() trigger. Replaces every *_audit, *_history, *_audit_snapshots, *_transactions journal collection';

create table audit.events (
  id uuid not null default gen_random_uuid(),
  tenant_id uuid default core.current_tenant_id(),
  college_id uuid,
  event_time timestamptz not null default now(),
  action text not null,
  principal_id uuid,
  principal_type text default 'USER',
  resource_type text,
  resource_id uuid,
  status text,
  severity text default 'INFO',
  ip inet,
  user_agent text,
  correlation_id text,
  detail jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  primary key (id, created_at)
) partition by range (created_at);
create table audit.events_default partition of audit.events default;
create index ix_events_tenant_id_resource_type_resource_id on audit.events (tenant_id, resource_type, resource_id);
create index ix_events_tenant_id_principal_id_created_at on audit.events (tenant_id, principal_id, created_at desc);
create index ix_events_tenant_id_action_created_at on audit.events (tenant_id, action, created_at desc);
create index ix_events_college_id on audit.events (tenant_id, college_id);
comment on table audit.events is 'Semantic security/business audit events (login, export, approve, publish ...) [Mongo: ADM01.audit_logs, ADM02.audit_logs, EXM02.audit_logs, FIN09.audit_events, FIN09.audit_records, FIN09.administrative_audit, FIN09.approval_audit_records, FIN09.audit_snapshots]';

create table audit.access_events (
  id uuid not null default gen_random_uuid(),
  tenant_id uuid default core.current_tenant_id(),
  principal_id uuid,
  resource_type text not null,
  resource_id uuid,
  access_type text not null,
  sensitivity text,
  ip inet,
  reason text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_access_events_access_type check (access_type in ('READ', 'EXPORT', 'PRINT', 'DOWNLOAD')),
  primary key (id, created_at)
) partition by range (created_at);
create table audit.access_events_default partition of audit.access_events default;
create index ix_access_events_tenant_id_resource_type_resource_id on audit.access_events (tenant_id, resource_type, resource_id);
create index ix_access_events_tenant_id_principal_id_created_at on audit.access_events (tenant_id, principal_id, created_at desc);
comment on table audit.access_events is 'Read-access / sensitive-data access log (high volume, monthly partitions) [Mongo: FIN09.data_access_audit, FIN09.sensitive_access_audit, FIN09.evidence_access_logs]';

create table audit.compliance_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  rule_type text not null,
  severity text not null default 'MEDIUM',
  params jsonb default '{}'::jsonb,
  active boolean not null default true,
  version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_compliance_rules_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  constraint uq_compliance_rules_tenant_id unique (tenant_id, id)
);
create unique index uq_compliance_rules_tenant_id_code on audit.compliance_rules (tenant_id, code) where deleted_at is null;
comment on table audit.compliance_rules is '[Mongo: FIN09.compliance_rules]';

create table audit.compliance_evaluations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  rule_id uuid not null,
  entity_type text,
  entity_id uuid,
  result text not null,
  detail jsonb,
  evaluated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_compliance_evaluations_result check (result in ('PASS', 'FAIL', 'WARN')),
  constraint uq_compliance_evaluations_tenant_id unique (tenant_id, id)
);
create index ix_compliance_evaluations_tenant_id_rule_id_evaluated_at on audit.compliance_evaluations (tenant_id, rule_id, evaluated_at desc);
comment on table audit.compliance_evaluations is '[Mongo: FIN09.compliance_evaluations]';

create table audit.compliance_violations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  rule_id uuid not null,
  evaluation_id uuid,
  entity_type text,
  entity_id uuid,
  severity text not null,
  status text not null default 'OPEN',
  detail jsonb,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_compliance_violations_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  constraint ck_compliance_violations_status check (status in ('OPEN', 'UNDER_REVIEW', 'EXCEPTION_GRANTED', 'RESOLVED')),
  constraint uq_compliance_violations_tenant_id unique (tenant_id, id)
);
create index ix_compliance_violations_rule_id on audit.compliance_violations (tenant_id, rule_id);
create index ix_compliance_violations_evaluation_id on audit.compliance_violations (tenant_id, evaluation_id);
comment on table audit.compliance_violations is '[Mongo: FIN09.compliance_violations]';

create table audit.compliance_exceptions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  violation_id uuid not null,
  reason text not null,
  approved_by uuid,
  valid_until timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_compliance_exceptions_tenant_id unique (tenant_id, id)
);
create index ix_compliance_exceptions_violation_id on audit.compliance_exceptions (tenant_id, violation_id);
create index ix_compliance_exceptions_approved_by on audit.compliance_exceptions (tenant_id, approved_by);
comment on table audit.compliance_exceptions is '[Mongo: FIN09.compliance_exceptions]';

create table audit.sod_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  conflicting_permissions text[] not null default '{}',
  severity text not null default 'HIGH',
  active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_sod_rules_tenant_id unique (tenant_id, id)
);
create unique index uq_sod_rules_tenant_id_code on audit.sod_rules (tenant_id, code) where deleted_at is null;
comment on table audit.sod_rules is 'Segregation-of-duties matrix [Mongo: FIN09.segregation_duty_rules]';

create table audit.sod_violations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  rule_id uuid not null,
  user_id uuid,
  entity_type text,
  entity_id uuid,
  status text not null default 'OPEN',
  detected_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_sod_violations_tenant_id unique (tenant_id, id)
);
create index ix_sod_violations_rule_id on audit.sod_violations (tenant_id, rule_id);
create index ix_sod_violations_user_id on audit.sod_violations (tenant_id, user_id);
comment on table audit.sod_violations is '[Mongo: FIN09.segregation_duty_violations]';

create table audit.cases (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  case_no text not null,
  title text not null,
  status text not null default 'OPEN',
  severity text,
  opened_by uuid,
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_cases_status check (status in ('OPEN', 'INVESTIGATING', 'CLOSED')),
  constraint uq_cases_tenant_id unique (tenant_id, id)
);
create unique index uq_cases_tenant_id_case_no on audit.cases (tenant_id, case_no) where deleted_at is null;
create index ix_cases_opened_by on audit.cases (tenant_id, opened_by);
comment on table audit.cases is '[Mongo: FIN09.audit_cases]';

create table audit.case_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  case_id uuid not null,
  entity_type text not null,
  entity_id uuid not null,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_case_items_tenant_id unique (tenant_id, id)
);
create index ix_case_items_case_id on audit.case_items (tenant_id, case_id);
comment on table audit.case_items is '[Mongo: FIN09.audit_case_items]';

create table audit.case_actions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  case_id uuid not null,
  action text not null,
  actor_id uuid,
  detail text,
  acted_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_case_actions_tenant_id unique (tenant_id, id)
);
create index ix_case_actions_case_id on audit.case_actions (tenant_id, case_id);
create index ix_case_actions_actor_id on audit.case_actions (tenant_id, actor_id);
comment on table audit.case_actions is '[Mongo: FIN09.audit_case_actions]';

create table audit.evidence (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  case_id uuid,
  document_id uuid,
  entity_type text,
  entity_id uuid,
  sha256 text,
  collected_at timestamptz not null default now(),
  chain_of_custody jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_evidence_tenant_id unique (tenant_id, id)
);
create index ix_evidence_case_id on audit.evidence (tenant_id, case_id);
create index ix_evidence_document_id on audit.evidence (tenant_id, document_id);
comment on table audit.evidence is 'Evidence item + reference + integrity hash collapsed into one row [Mongo: FIN09.audit_evidence, FIN09.evidence_references, FIN09.evidence_hashes]';

create table audit.retention_policies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  entity_type text not null,
  retention_days integer not null,
  action text not null default 'ARCHIVE',
  legal_hold boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_retention_policies_retention_days check (retention_days > 0),
  constraint ck_retention_policies_action check (action in ('ARCHIVE', 'DELETE', 'ANONYMIZE')),
  constraint uq_retention_policies_tenant_id unique (tenant_id, id)
);
comment on table audit.retention_policies is '[Mongo: FIN09.audit_retention_policies, FIN09.audit_configuration]';

create table audit.archives (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  period_from date not null,
  period_to date not null,
  document_id uuid,
  row_count bigint,
  sha256 text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_archives_tenant_id unique (tenant_id, id)
);
create index ix_archives_document_id on audit.archives (tenant_id, document_id);
comment on table audit.archives is '[Mongo: FIN09.audit_archives]';

create table audit.integrity_records (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  checked_from timestamptz not null,
  checked_to timestamptz not null,
  chain_hash text,
  status text not null,
  checked_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_integrity_records_status check (status in ('VERIFIED', 'BROKEN')),
  constraint uq_integrity_records_tenant_id unique (tenant_id, id)
);
comment on table audit.integrity_records is 'Periodic hash-chain verification of audit.change_log partitions [Mongo: FIN09.audit_integrity_records]';

create table audit.field_policies (
  id uuid primary key default gen_random_uuid(),
  table_schema text not null,
  table_name text not null,
  mode text not null default 'REDACT',
  columns text[] default '{}',
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_field_policies_mode check (mode in ('REDACT', 'ALLOWLIST', 'METADATA_ONLY'))
);
create unique index uq_field_policies_table_schema_table_name on audit.field_policies (table_schema, table_name) where deleted_at is null;
comment on table audit.field_policies is 'What audit.change_log may store per table. Default (no row) = REDACT: values of personal/contact/secret-looking columns are replaced by [REDACTED]. METADATA_ONLY stores no values; ALLOWLIST stores only the listed columns';


-- ============================================================ acd

create table acd.programs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  department_id uuid not null,
  code text not null,
  name text not null,
  level text not null,
  duration_years numeric(9,4) not null,
  total_terms smallint,
  published boolean not null default false,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_programs_level check (level in ('CERTIFICATE', 'DIPLOMA', 'UG', 'PG', 'PHD', 'INTEGRATED')),
  constraint ck_programs_duration_years check (duration_years > 0),
  constraint ck_programs_status check (status in ('ACTIVE', 'DISCONTINUED', 'DRAFT')),
  constraint uq_programs_tenant_id unique (tenant_id, id)
);
create unique index uq_programs_tenant_id_college_id_code on acd.programs (tenant_id, college_id, code) where deleted_at is null;
create index ix_programs_tenant_id_department_id on acd.programs (tenant_id, department_id);
create index ix_programs_college_id on acd.programs (tenant_id, college_id);
comment on table acd.programs is '[Mongo: ADM02.programs]';

create table acd.curricula (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  program_id uuid not null,
  code text not null,
  name text not null,
  regulation_year smallint,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_curricula_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_curricula_tenant_id unique (tenant_id, id)
);
create unique index uq_curricula_tenant_id_code on acd.curricula (tenant_id, code) where deleted_at is null;
create index ix_curricula_program_id on acd.curricula (tenant_id, program_id);
comment on table acd.curricula is 'Regulation/scheme of a program (e.g. R2021) [Mongo: ACD02.curricula]';

create table acd.curriculum_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  curriculum_id uuid not null,
  version_no integer not null,
  effective_from date not null,
  effective_to date,
  status text not null default 'DRAFT',
  total_credits numeric(18,2),
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_curriculum_versions_version_no check (version_no > 0),
  constraint ck_curriculum_versions_status check (status in ('DRAFT', 'APPROVED', 'ACTIVE', 'SUPERSEDED', 'RETIRED')),
  constraint ck_curriculum_versions_total_credits check (total_credits >= 0),
  constraint ck_curriculum_versions_x1 check (effective_to is null or effective_to >= effective_from),
  constraint uq_curriculum_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_curriculum_versions_tenant_id_curriculum_id_version_no on acd.curriculum_versions (tenant_id, curriculum_id, version_no) where deleted_at is null;
create index ix_curriculum_versions_curriculum_id on acd.curriculum_versions (tenant_id, curriculum_id);
create index ix_curriculum_versions_workflow_instance_id on acd.curriculum_versions (tenant_id, workflow_instance_id);
comment on table acd.curriculum_versions is 'First-class effective-dated version: enrolments, results and outcomes bind to a version [Mongo: ACD02.curriculum_versions]';

create table acd.subjects (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  department_id uuid,
  code text not null,
  name text not null,
  subject_type text not null default 'THEORY',
  category_id uuid,
  credits numeric(18,2),
  lecture_hours smallint,
  tutorial_hours smallint,
  practical_hours smallint,
  status text not null default 'DRAFT',
  metadata jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_subjects_subject_type check (subject_type in ('THEORY', 'LAB', 'PROJECT', 'SEMINAR', 'INTERNSHIP', 'MOOC', 'AUDIT')),
  constraint ck_subjects_credits check (credits >= 0),
  constraint ck_subjects_lecture_hours check (lecture_hours >= 0),
  constraint ck_subjects_tutorial_hours check (tutorial_hours >= 0),
  constraint ck_subjects_practical_hours check (practical_hours >= 0),
  constraint ck_subjects_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_subjects_tenant_id unique (tenant_id, id)
);
create unique index uq_subjects_tenant_id_code on acd.subjects (tenant_id, code) where deleted_at is null;
create index ix_subjects_tenant_id_department_id_status on acd.subjects (tenant_id, department_id, status);
create index ix_subjects_category_id on acd.subjects (tenant_id, category_id);
comment on table acd.subjects is 'ONE subject master (ACD03 and LMS03 duplicated it). Classification via category_id [Mongo: ACD03.subjects, LMS03.subjects, ACD03.subject_metadata]';

create table acd.subject_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  subject_id uuid not null,
  version_no integer not null,
  effective_from date,
  effective_to date,
  credits numeric(18,2),
  syllabus jsonb default '{}'::jsonb,
  reference_books jsonb default '{}'::jsonb,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_subject_versions_version_no check (version_no > 0),
  constraint ck_subject_versions_credits check (credits >= 0),
  constraint ck_subject_versions_status check (status in ('DRAFT', 'APPROVED', 'ACTIVE', 'SUPERSEDED')),
  constraint uq_subject_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_subject_versions_tenant_id_subject_id_version_no on acd.subject_versions (tenant_id, subject_id, version_no) where deleted_at is null;
create index ix_subject_versions_subject_id on acd.subject_versions (tenant_id, subject_id);
comment on table acd.subject_versions is 'Syllabus versions [Mongo: ACD03.subject_versions, LMS03.subject_versions]';

create table acd.subject_equivalencies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  subject_id uuid not null,
  equivalent_subject_id uuid not null,
  valid_from date,
  valid_to date,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_subject_equivalencies_x1 check (subject_id <> equivalent_subject_id),
  constraint uq_subject_equivalencies_tenant_id unique (tenant_id, id)
);
create unique index uq_subject_equivalencies_tenant_id_subject_id_equi_f2246f78 on acd.subject_equivalencies (tenant_id, subject_id, equivalent_subject_id) where deleted_at is null;
create index ix_subject_equivalencies_subject_id on acd.subject_equivalencies (tenant_id, subject_id);
create index ix_subject_equivalencies_equivalent_subject_id on acd.subject_equivalencies (tenant_id, equivalent_subject_id);
comment on table acd.subject_equivalencies is '[Mongo: LMS03.subject_equivalencies]';

create table acd.subject_prerequisites (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  subject_id uuid not null,
  prerequisite_subject_id uuid not null,
  relation text not null default 'PREREQUISITE',
  min_grade text,
  curriculum_version_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_subject_prerequisites_relation check (relation in ('PREREQUISITE', 'COREQUISITE')),
  constraint ck_subject_prerequisites_x1 check (subject_id <> prerequisite_subject_id),
  constraint uq_subject_prerequisites_tenant_id unique (tenant_id, id)
);
create unique index uq_subject_prerequisites_tenant_id_subject_id_prer_af55c281 on acd.subject_prerequisites (tenant_id, subject_id, prerequisite_subject_id, relation, curriculum_version_id) where deleted_at is null;
create index ix_subject_prerequisites_subject_id on acd.subject_prerequisites (tenant_id, subject_id);
create index ix_subject_prerequisites_prerequisite_subject_id on acd.subject_prerequisites (tenant_id, prerequisite_subject_id);
create index ix_subject_prerequisites_curriculum_version_id on acd.subject_prerequisites (tenant_id, curriculum_version_id);
comment on table acd.subject_prerequisites is 'curriculum_version_id NULL = global rule; set = rule scoped to that curriculum [Mongo: ACD03.subject_prerequisites, LMS03.subject_prerequisites, ACD02.curriculum_prerequisites]';

create table acd.subject_change_requests (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  subject_id uuid not null,
  change_type text not null,
  proposed_changes jsonb not null default '{}'::jsonb,
  status text not null default 'PENDING',
  requested_by uuid,
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_subject_change_requests_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
  constraint uq_subject_change_requests_tenant_id unique (tenant_id, id)
);
create index ix_subject_change_requests_subject_id on acd.subject_change_requests (tenant_id, subject_id);
create index ix_subject_change_requests_requested_by on acd.subject_change_requests (tenant_id, requested_by);
create index ix_subject_change_requests_workflow_instance_id on acd.subject_change_requests (tenant_id, workflow_instance_id);
comment on table acd.subject_change_requests is '[Mongo: LMS03.subject_change_requests]';

create table acd.curriculum_subjects (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  curriculum_version_id uuid not null,
  subject_id uuid not null,
  term_no smallint not null,
  category text not null default 'CORE',
  credits numeric(18,2),
  elective_group text,
  display_order smallint not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_curriculum_subjects_term_no check (term_no > 0),
  constraint ck_curriculum_subjects_category check (category in ('CORE', 'ELECTIVE', 'OPEN_ELECTIVE', 'MANDATORY_NON_CREDIT', 'PROJECT')),
  constraint ck_curriculum_subjects_credits check (credits >= 0),
  constraint uq_curriculum_subjects_tenant_id unique (tenant_id, id)
);
create unique index uq_curriculum_subjects_tenant_id_curriculum_versio_b5a60d64 on acd.curriculum_subjects (tenant_id, curriculum_version_id, subject_id) where deleted_at is null;
create index ix_curriculum_subjects_tenant_id_curriculum_versio_2e60e8e2 on acd.curriculum_subjects (tenant_id, curriculum_version_id, term_no);
create index ix_curriculum_subjects_subject_id on acd.curriculum_subjects (tenant_id, subject_id);
comment on table acd.curriculum_subjects is 'Places a subject in a curriculum/term. Replaces three overlapping mapping collections [Mongo: ACD02.curriculum_subjects, LMS03.subject_program_mappings, LMS03.subject_semester_mappings]';

create table acd.courses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  department_id uuid,
  code text not null,
  name text not null,
  description text,
  level text,
  credits numeric(18,2),
  status text not null default 'DRAFT',
  metadata jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_courses_credits check (credits >= 0),
  constraint ck_courses_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_courses_tenant_id unique (tenant_id, id)
);
create unique index uq_courses_tenant_id_code on acd.courses (tenant_id, code) where deleted_at is null;
create index ix_courses_department_id on acd.courses (tenant_id, department_id);
comment on table acd.courses is 'ASSUMPTION: a Course is a deliverable teaching unit (may bundle subjects). ACD01 and LMS01 held the same entity [Mongo: ACD01.courses, LMS01.courses]';

create table acd.course_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid not null,
  version_no integer not null,
  effective_from date,
  effective_to date,
  content_outline jsonb default '{}'::jsonb,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_course_versions_version_no check (version_no > 0),
  constraint ck_course_versions_status check (status in ('DRAFT', 'APPROVED', 'ACTIVE', 'SUPERSEDED')),
  constraint uq_course_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_course_versions_tenant_id_course_id_version_no on acd.course_versions (tenant_id, course_id, version_no) where deleted_at is null;
create index ix_course_versions_course_id on acd.course_versions (tenant_id, course_id);
comment on table acd.course_versions is '[Mongo: ACD01.course_versions, LMS01.course_versions]';

create table acd.course_prerequisites (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid not null,
  prerequisite_course_id uuid not null,
  relation text not null default 'PREREQUISITE',
  min_grade text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_course_prerequisites_relation check (relation in ('PREREQUISITE', 'COREQUISITE')),
  constraint ck_course_prerequisites_x1 check (course_id <> prerequisite_course_id),
  constraint uq_course_prerequisites_tenant_id unique (tenant_id, id)
);
create unique index uq_course_prerequisites_tenant_id_course_id_prereq_741c9219 on acd.course_prerequisites (tenant_id, course_id, prerequisite_course_id, relation) where deleted_at is null;
create index ix_course_prerequisites_course_id on acd.course_prerequisites (tenant_id, course_id);
create index ix_course_prerequisites_prerequisite_course_id on acd.course_prerequisites (tenant_id, prerequisite_course_id);
comment on table acd.course_prerequisites is '[Mongo: ACD01.course_prerequisites, LMS01.course_prerequisites]';

create table acd.course_program_map (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid not null,
  program_id uuid not null,
  term_no smallint,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_course_program_map_tenant_id unique (tenant_id, id)
);
create unique index uq_course_program_map_tenant_id_course_id_program_id on acd.course_program_map (tenant_id, course_id, program_id);
create index ix_course_program_map_program_id on acd.course_program_map (tenant_id, program_id);
comment on table acd.course_program_map is '[Mongo: LMS01.course_program_mapping]';

create table acd.subject_course_map (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  subject_id uuid not null,
  course_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_subject_course_map_tenant_id unique (tenant_id, id)
);
create unique index uq_subject_course_map_tenant_id_subject_id_course_id on acd.subject_course_map (tenant_id, subject_id, course_id);
create index ix_subject_course_map_course_id on acd.subject_course_map (tenant_id, course_id);
comment on table acd.subject_course_map is '[Mongo: LMS03.subject_course_mappings]';

create table acd.calendar_terms (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  calendar_id uuid not null,
  term_no smallint not null,
  name text not null,
  term_type text not null default 'SEMESTER',
  start_date date not null,
  end_date date not null,
  status text not null default 'PLANNED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_calendar_terms_term_no check (term_no > 0),
  constraint ck_calendar_terms_term_type check (term_type in ('SEMESTER', 'TRIMESTER', 'QUARTER', 'SUMMER', 'WINTER')),
  constraint ck_calendar_terms_status check (status in ('PLANNED', 'ACTIVE', 'CLOSED')),
  constraint ck_calendar_terms_x1 check (end_date > start_date),
  constraint uq_calendar_terms_tenant_id unique (tenant_id, id)
);
create unique index uq_calendar_terms_tenant_id_calendar_id_term_no on acd.calendar_terms (tenant_id, calendar_id, term_no) where deleted_at is null;
create index ix_calendar_terms_calendar_id on acd.calendar_terms (tenant_id, calendar_id);
comment on table acd.calendar_terms is '[Mongo: ACD07.calendar_terms]';

create table acd.batches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  program_id uuid not null,
  curriculum_version_id uuid,
  admission_year_id uuid,
  code text not null,
  name text not null,
  current_term_no smallint,
  capacity integer,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_batches_capacity check (capacity >= 0),
  constraint ck_batches_status check (status in ('PLANNED', 'ACTIVE', 'GRADUATED', 'CLOSED')),
  constraint uq_batches_tenant_id unique (tenant_id, id)
);
create unique index uq_batches_tenant_id_college_id_code on acd.batches (tenant_id, college_id, code) where deleted_at is null;
create index ix_batches_tenant_id_program_id_status on acd.batches (tenant_id, program_id, status);
create index ix_batches_college_id on acd.batches (tenant_id, college_id);
create index ix_batches_curriculum_version_id on acd.batches (tenant_id, curriculum_version_id);
create index ix_batches_admission_year_id on acd.batches (tenant_id, admission_year_id);
comment on table acd.batches is '[Mongo: ACD04.batches]';

create table acd.batch_students (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  batch_id uuid not null,
  student_id uuid not null,
  roll_no text,
  joined_on date,
  left_on date,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_batch_students_status check (status in ('ACTIVE', 'TRANSFERRED', 'DETAINED', 'COMPLETED', 'DROPPED')),
  constraint uq_batch_students_tenant_id unique (tenant_id, id)
);
create unique index uq_batch_students_tenant_id_batch_id_student_id on acd.batch_students (tenant_id, batch_id, student_id) where deleted_at is null;
create unique index uq_batch_students_tenant_id_batch_id_roll_no on acd.batch_students (tenant_id, batch_id, roll_no) where deleted_at is null and roll_no is not null;
create index ix_batch_students_tenant_id_student_id on acd.batch_students (tenant_id, student_id);
create index ix_batch_students_batch_id on acd.batch_students (tenant_id, batch_id);
comment on table acd.batch_students is '[Mongo: ACD04.batch_rosters]';

create table acd.batch_capacity_overrides (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  batch_id uuid not null,
  term_id uuid,
  capacity integer not null,
  reason text,
  approved_by uuid,
  valid_from date,
  valid_to date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_batch_capacity_overrides_capacity check (capacity >= 0),
  constraint uq_batch_capacity_overrides_tenant_id unique (tenant_id, id)
);
create index ix_batch_capacity_overrides_batch_id on acd.batch_capacity_overrides (tenant_id, batch_id);
create index ix_batch_capacity_overrides_term_id on acd.batch_capacity_overrides (tenant_id, term_id);
create index ix_batch_capacity_overrides_approved_by on acd.batch_capacity_overrides (tenant_id, approved_by);
comment on table acd.batch_capacity_overrides is '[Mongo: ACD04.batch_capacity_overrides]';

create table acd.course_offerings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  course_id uuid not null,
  term_id uuid not null,
  start_date date,
  end_date date,
  capacity integer,
  status text not null default 'PLANNED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_course_offerings_capacity check (capacity >= 0),
  constraint ck_course_offerings_status check (status in ('PLANNED', 'OPEN', 'RUNNING', 'COMPLETED', 'CANCELLED')),
  constraint uq_course_offerings_tenant_id unique (tenant_id, id)
);
create unique index uq_course_offerings_tenant_id_course_id_term_id_college_id on acd.course_offerings (tenant_id, course_id, term_id, college_id) where deleted_at is null;
create index ix_course_offerings_tenant_id_term_id_status on acd.course_offerings (tenant_id, term_id, status);
create index ix_course_offerings_college_id on acd.course_offerings (tenant_id, college_id);
create index ix_course_offerings_course_id on acd.course_offerings (tenant_id, course_id);
comment on table acd.course_offerings is '[Mongo: LMS01.course_offerings]';

create table acd.course_sections (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  offering_id uuid not null,
  batch_id uuid,
  section_code text not null,
  capacity integer,
  default_room_id uuid,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_course_sections_capacity check (capacity >= 0),
  constraint ck_course_sections_status check (status in ('ACTIVE', 'CLOSED', 'CANCELLED')),
  constraint uq_course_sections_tenant_id unique (tenant_id, id)
);
create unique index uq_course_sections_tenant_id_offering_id_section_code on acd.course_sections (tenant_id, offering_id, section_code) where deleted_at is null;
create index ix_course_sections_offering_id on acd.course_sections (tenant_id, offering_id);
create index ix_course_sections_batch_id on acd.course_sections (tenant_id, batch_id);
create index ix_course_sections_default_room_id on acd.course_sections (tenant_id, default_room_id);
comment on table acd.course_sections is '[Mongo: LMS01.course_sections]';

create table acd.section_faculty (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  section_id uuid not null,
  employee_id uuid not null,
  role text not null default 'PRIMARY',
  valid_from date,
  valid_to date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_section_faculty_role check (role in ('PRIMARY', 'CO_INSTRUCTOR', 'LAB_INSTRUCTOR', 'TEACHING_ASSISTANT', 'MENTOR')),
  constraint uq_section_faculty_tenant_id unique (tenant_id, id)
);
create unique index uq_section_faculty_tenant_id_section_id_employee_id_role on acd.section_faculty (tenant_id, section_id, employee_id, role) where deleted_at is null;
create index ix_section_faculty_tenant_id_employee_id on acd.section_faculty (tenant_id, employee_id);
create index ix_section_faculty_section_id on acd.section_faculty (tenant_id, section_id);
comment on table acd.section_faculty is '[Mongo: LMS01.course_faculty]';

create table acd.enrollment_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  offering_id uuid not null,
  rule_type text not null,
  params jsonb not null default '{}'::jsonb,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_enrollment_rules_tenant_id unique (tenant_id, id)
);
create index ix_enrollment_rules_offering_id on acd.enrollment_rules (tenant_id, offering_id);
comment on table acd.enrollment_rules is '[Mongo: LMS01.enrollment_rules]';

create table acd.section_enrollments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  section_id uuid not null,
  student_id uuid not null,
  status text not null default 'ENROLLED',
  enrolled_at timestamptz not null default now(),
  dropped_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_section_enrollments_status check (status in ('ENROLLED', 'DROPPED', 'WITHDRAWN', 'COMPLETED', 'AUDIT')),
  constraint uq_section_enrollments_tenant_id unique (tenant_id, id)
);
create unique index uq_section_enrollments_tenant_id_section_id_student_id on acd.section_enrollments (tenant_id, section_id, student_id) where deleted_at is null;
create index ix_section_enrollments_tenant_id_student_id_status on acd.section_enrollments (tenant_id, student_id, status);
create index ix_section_enrollments_section_id on acd.section_enrollments (tenant_id, section_id);
comment on table acd.section_enrollments is 'NEW: student <-> section enrolment. Not in the uploaded inventory but required as the anchor for attendance, progress and grades';

create table acd.timetables (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  term_id uuid not null,
  batch_id uuid,
  name text not null,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_timetables_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  constraint uq_timetables_tenant_id unique (tenant_id, id)
);
create index ix_timetables_college_id on acd.timetables (tenant_id, college_id);
create index ix_timetables_term_id on acd.timetables (tenant_id, term_id);
create index ix_timetables_batch_id on acd.timetables (tenant_id, batch_id);
comment on table acd.timetables is '[Mongo: ACD05.timetables]';

create table acd.timetable_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  timetable_id uuid not null,
  version_no integer not null,
  status text not null default 'DRAFT',
  published_at timestamptz,
  change_note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_timetable_versions_version_no check (version_no > 0),
  constraint ck_timetable_versions_status check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
  constraint uq_timetable_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_timetable_versions_tenant_id_timetable_id_version_no on acd.timetable_versions (tenant_id, timetable_id, version_no) where deleted_at is null;
create index ix_timetable_versions_timetable_id on acd.timetable_versions (tenant_id, timetable_id);
comment on table acd.timetable_versions is '[Mongo: ACD05.timetable_versions]';

create table acd.timetable_entries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  timetable_version_id uuid not null,
  section_id uuid,
  subject_id uuid,
  employee_id uuid,
  room_id uuid,
  day_of_week smallint not null,
  start_time time not null,
  end_time time not null,
  entry_type text not null default 'LECTURE',
  valid_from date,
  valid_to date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_timetable_entries_day_of_week check (day_of_week >= 0),
  constraint ck_timetable_entries_entry_type check (entry_type in ('LECTURE', 'LAB', 'TUTORIAL', 'SEMINAR', 'BREAK')),
  constraint ck_timetable_entries_x1 check (end_time > start_time),
  constraint ck_timetable_entries_x2 check (day_of_week between 0 and 6),
  constraint uq_timetable_entries_tenant_id unique (tenant_id, id)
);
create index ix_timetable_entries_tenant_id_employee_id_day_of__af7e4356 on acd.timetable_entries (tenant_id, employee_id, day_of_week, start_time);
create index ix_timetable_entries_tenant_id_room_id_day_of_week_4f241504 on acd.timetable_entries (tenant_id, room_id, day_of_week, start_time);
create index ix_timetable_entries_tenant_id_section_id on acd.timetable_entries (tenant_id, section_id);
create index ix_timetable_entries_timetable_version_id on acd.timetable_entries (tenant_id, timetable_version_id);
create index ix_timetable_entries_subject_id on acd.timetable_entries (tenant_id, subject_id);
comment on table acd.timetable_entries is '[Mongo: ACD05.timetable_entries, LMS01.course_schedules]';

create table acd.timetable_conflicts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  timetable_version_id uuid not null,
  conflict_type text not null,
  entry_a_id uuid,
  entry_b_id uuid,
  severity text not null default 'ERROR',
  detail jsonb,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_timetable_conflicts_conflict_type check (conflict_type in ('FACULTY', 'ROOM', 'BATCH', 'CAPACITY', 'OTHER')),
  constraint ck_timetable_conflicts_severity check (severity in ('WARNING', 'ERROR')),
  constraint uq_timetable_conflicts_tenant_id unique (tenant_id, id)
);
create index ix_timetable_conflicts_timetable_version_id on acd.timetable_conflicts (tenant_id, timetable_version_id);
create index ix_timetable_conflicts_entry_a_id on acd.timetable_conflicts (tenant_id, entry_a_id);
create index ix_timetable_conflicts_entry_b_id on acd.timetable_conflicts (tenant_id, entry_b_id);
comment on table acd.timetable_conflicts is '[Mongo: ACD05.conflict_results]';

create table acd.attendance_sessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  section_id uuid not null,
  timetable_entry_id uuid,
  employee_id uuid,
  session_date date not null,
  start_time time,
  end_time time,
  status text not null default 'SCHEDULED',
  locked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attendance_sessions_status check (status in ('SCHEDULED', 'OPEN', 'CLOSED', 'CANCELLED')),
  constraint uq_attendance_sessions_tenant_id unique (tenant_id, id)
);
create unique index uq_attendance_sessions_tenant_id_section_id_sessio_f3a84572 on acd.attendance_sessions (tenant_id, section_id, session_date, start_time) where deleted_at is null;
create index ix_attendance_sessions_tenant_id_session_date on acd.attendance_sessions (tenant_id, session_date);
create index ix_attendance_sessions_tenant_id_employee_id_session_date on acd.attendance_sessions (tenant_id, employee_id, session_date);
create index ix_attendance_sessions_section_id on acd.attendance_sessions (tenant_id, section_id);
create index ix_attendance_sessions_timetable_entry_id on acd.attendance_sessions (tenant_id, timetable_entry_id);
comment on table acd.attendance_sessions is '[Mongo: ACD06.attendance_sessions]';

create table acd.attendance_records (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  student_id uuid not null,
  status text not null,
  method text not null default 'MANUAL',
  marked_by uuid,
  marked_at timestamptz not null default now(),
  remarks text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attendance_records_status check (status in ('PRESENT', 'ABSENT', 'LATE', 'ON_DUTY', 'LEAVE', 'EXCUSED')),
  constraint ck_attendance_records_method check (method in ('MANUAL', 'QR', 'BIOMETRIC', 'GEOFENCE', 'IMPORT')),
  constraint uq_attendance_records_tenant_id unique (tenant_id, id)
);
create unique index uq_attendance_records_tenant_id_session_id_student_id on acd.attendance_records (tenant_id, session_id, student_id) where deleted_at is null;
create index ix_attendance_records_tenant_id_student_id_status on acd.attendance_records (tenant_id, student_id, status);
create index ix_attendance_records_session_id on acd.attendance_records (tenant_id, session_id);
create index ix_attendance_records_marked_by on acd.attendance_records (tenant_id, marked_by);
comment on table acd.attendance_records is 'Highest-volume academic table. Corrections are audited in attendance_corrections; consider RANGE partitioning by session date at >50M rows [Mongo: ACD06.attendance_records]';

create table acd.attendance_corrections (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  record_id uuid not null,
  old_status text not null,
  new_status text not null,
  reason text not null,
  requested_by uuid,
  approved_by uuid,
  workflow_instance_id uuid,
  status text not null default 'PENDING',
  decided_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attendance_corrections_new_status check (new_status in ('PRESENT', 'ABSENT', 'LATE', 'ON_DUTY', 'LEAVE', 'EXCUSED')),
  constraint ck_attendance_corrections_status check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  constraint uq_attendance_corrections_tenant_id unique (tenant_id, id)
);
create index ix_attendance_corrections_record_id on acd.attendance_corrections (tenant_id, record_id);
create index ix_attendance_corrections_requested_by on acd.attendance_corrections (tenant_id, requested_by);
create index ix_attendance_corrections_approved_by on acd.attendance_corrections (tenant_id, approved_by);
create index ix_attendance_corrections_workflow_instance_id on acd.attendance_corrections (tenant_id, workflow_instance_id);
comment on table acd.attendance_corrections is '[Mongo: ACD06.attendance_corrections]';

create table acd.assessment_structures (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  subject_id uuid,
  curriculum_version_id uuid,
  code text not null,
  name text not null,
  total_marks numeric(18,2) not null,
  pass_marks numeric(18,2),
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_assessment_structures_total_marks check (total_marks >= 0),
  constraint ck_assessment_structures_pass_marks check (pass_marks >= 0),
  constraint ck_assessment_structures_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_assessment_structures_tenant_id unique (tenant_id, id)
);
create unique index uq_assessment_structures_tenant_id_code on acd.assessment_structures (tenant_id, code) where deleted_at is null;
create index ix_assessment_structures_subject_id on acd.assessment_structures (tenant_id, subject_id);
create index ix_assessment_structures_curriculum_version_id on acd.assessment_structures (tenant_id, curriculum_version_id);
comment on table acd.assessment_structures is '[Mongo: ACD09.assessment_structures]';

create table acd.assessment_components (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_id uuid not null,
  code text not null,
  name text not null,
  component_type text not null,
  is_external boolean not null default false,
  max_marks numeric(18,2) not null,
  min_marks numeric(18,2),
  weightage_pct numeric(9,4),
  sequence smallint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_assessment_components_component_type check (component_type in ('INTERNAL_TEST', 'ASSIGNMENT', 'LAB', 'VIVA', 'PROJECT', 'ATTENDANCE', 'END_SEMESTER', 'OTHER')),
  constraint ck_assessment_components_max_marks check (max_marks >= 0),
  constraint ck_assessment_components_min_marks check (min_marks >= 0),
  constraint ck_assessment_components_weightage_pct check (weightage_pct between 0 and 100),
  constraint uq_assessment_components_tenant_id unique (tenant_id, id)
);
create unique index uq_assessment_components_tenant_id_structure_id_code on acd.assessment_components (tenant_id, structure_id, code) where deleted_at is null;
create index ix_assessment_components_structure_id on acd.assessment_components (tenant_id, structure_id);
comment on table acd.assessment_components is '[Mongo: ACD09.assessment_components]';


-- ============================================================ lms

create table lms.contents (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid,
  subject_id uuid,
  owner_employee_id uuid,
  title text not null,
  description text,
  status text not null default 'DRAFT',
  metadata jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_contents_status check (status in ('DRAFT', 'IN_REVIEW', 'PUBLISHED', 'ARCHIVED')),
  constraint uq_contents_tenant_id unique (tenant_id, id)
);
create index ix_contents_tenant_id_course_id on lms.contents (tenant_id, course_id);
create index ix_contents_tenant_id_subject_id on lms.contents (tenant_id, subject_id);
create index ix_contents_owner_employee_id on lms.contents (tenant_id, owner_employee_id);
comment on table lms.contents is '[Mongo: LMS02.contents, LMS02.content_metadata]';

create table lms.content_modules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  content_id uuid not null,
  seq integer not null default 1,
  title text not null,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_content_modules_tenant_id unique (tenant_id, id)
);
create unique index uq_content_modules_tenant_id_content_id_seq on lms.content_modules (tenant_id, content_id, seq) where deleted_at is null;
create index ix_content_modules_content_id on lms.content_modules (tenant_id, content_id);
comment on table lms.content_modules is '[Mongo: LMS02.content_modules]';

create table lms.content_topics (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  module_id uuid not null,
  seq integer not null default 1,
  title text not null,
  content_type text not null default 'TEXT',
  body text,
  document_id uuid,
  external_url text,
  duration_min integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_content_topics_content_type check (content_type in ('TEXT', 'VIDEO', 'DOCUMENT', 'QUIZ', 'LINK', 'SCORM', 'LIVE')),
  constraint ck_content_topics_duration_min check (duration_min >= 0),
  constraint uq_content_topics_tenant_id unique (tenant_id, id)
);
create unique index uq_content_topics_tenant_id_module_id_seq on lms.content_topics (tenant_id, module_id, seq) where deleted_at is null;
create index ix_content_topics_module_id on lms.content_topics (tenant_id, module_id);
create index ix_content_topics_document_id on lms.content_topics (tenant_id, document_id);
comment on table lms.content_topics is '[Mongo: LMS02.content_topics]';

create table lms.content_dependencies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  topic_id uuid not null,
  depends_on_topic_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_content_dependencies_x1 check (topic_id <> depends_on_topic_id),
  constraint uq_content_dependencies_tenant_id unique (tenant_id, id)
);
create unique index uq_content_dependencies_tenant_id_topic_id_depends_dd9fadff on lms.content_dependencies (tenant_id, topic_id, depends_on_topic_id);
create index ix_content_dependencies_depends_on_topic_id on lms.content_dependencies (tenant_id, depends_on_topic_id);
comment on table lms.content_dependencies is '[Mongo: LMS02.content_dependencies]';

create table lms.access_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  target_type text not null,
  target_id uuid not null,
  audience_type text not null,
  audience_ref_id uuid,
  permission text not null default 'VIEW',
  valid_from timestamptz,
  valid_to timestamptz,
  policy jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_access_rules_target_type check (target_type in ('CONTENT', 'RESOURCE', 'CLASSROOM', 'ASSIGNMENT', 'COLLECTION')),
  constraint ck_access_rules_audience_type check (audience_type in ('ALL', 'ROLE', 'DEPARTMENT', 'PROGRAM', 'BATCH', 'SECTION', 'STUDENT', 'EMPLOYEE', 'GROUP')),
  constraint ck_access_rules_permission check (permission in ('VIEW', 'DOWNLOAD', 'COMMENT', 'EDIT', 'SUBMIT', 'HOST')),
  constraint uq_access_rules_tenant_id unique (tenant_id, id)
);
create index ix_access_rules_tenant_id_target_type_target_id on lms.access_rules (tenant_id, target_type, target_id);
create index ix_access_rules_tenant_id_audience_type_audience_ref_id on lms.access_rules (tenant_id, audience_type, audience_ref_id);
comment on table lms.access_rules is 'ONE audience/permission table for every learning artefact (was 6 collections) [Mongo: LMS02.content_access, LMS07.resource_permissions, LMS07.resource_access_policies, ACD08.resource_access, LMS06.classroom_access, LMS05.assignment_targets]';

create table lms.resource_categories (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  parent_id uuid,
  code text not null,
  name text not null,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_resource_categories_tenant_id unique (tenant_id, id)
);
create unique index uq_resource_categories_tenant_id_code on lms.resource_categories (tenant_id, code) where deleted_at is null;
create index ix_resource_categories_parent_id on lms.resource_categories (tenant_id, parent_id);
comment on table lms.resource_categories is '[Mongo: LMS07.resource_categories]';

create table lms.resources (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  category_id uuid,
  owner_employee_id uuid,
  title text not null,
  description text,
  resource_type text not null,
  document_id uuid,
  external_url text,
  language text,
  author text,
  license text,
  retain_until date,
  status text not null default 'DRAFT',
  metadata jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_resources_resource_type check (resource_type in ('FILE', 'VIDEO', 'LINK', 'EMBED', 'BOOK', 'PAPER', 'DATASET', 'SLIDES')),
  constraint ck_resources_status check (status in ('DRAFT', 'PUBLISHED', 'ARCHIVED')),
  constraint ck_resources_x1 check (document_id is not null or external_url is not null),
  constraint uq_resources_tenant_id unique (tenant_id, id)
);
create index ix_resources_tenant_id_category_id_status on lms.resources (tenant_id, category_id, status);
create index ix_resources_tenant_id_college_id on lms.resources (tenant_id, college_id);
create index ix_resources_owner_employee_id on lms.resources (tenant_id, owner_employee_id);
create index ix_resources_document_id on lms.resources (tenant_id, document_id);
create index gx_resources_metadata on lms.resources using gin (metadata);
comment on table lms.resources is 'ONE learning-resource library (ACD08 + LMS07). File bytes live in Supabase Storage via doc.documents [Mongo: ACD08.resources, LMS07.resources, LMS07.resource_metadata, LMS07.resource_storage, LMS07.resource_retention]';

create table lms.resource_associations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  resource_id uuid not null,
  entity_type text not null,
  entity_id uuid not null,
  relation text not null default 'REFERENCE',
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_resource_associations_entity_type check (entity_type in ('COURSE', 'SUBJECT', 'TOPIC', 'LESSON', 'ASSIGNMENT', 'PROGRAM', 'RESOURCE', 'TRAINING_COURSE')),
  constraint uq_resource_associations_tenant_id unique (tenant_id, id)
);
create unique index uq_resource_associations_tenant_id_resource_id_ent_e77d36a4 on lms.resource_associations (tenant_id, resource_id, entity_type, entity_id, relation);
create index ix_resource_associations_tenant_id_entity_type_entity_id on lms.resource_associations (tenant_id, entity_type, entity_id);
comment on table lms.resource_associations is '[Mongo: LMS07.resource_associations, LMS07.resource_links, HRM09.training_resources]';

create table lms.resource_collections (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  owner_employee_id uuid,
  name text not null,
  description text,
  is_public boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_resource_collections_tenant_id unique (tenant_id, id)
);
create index ix_resource_collections_owner_employee_id on lms.resource_collections (tenant_id, owner_employee_id);
comment on table lms.resource_collections is '[Mongo: LMS07.resource_collections]';

create table lms.resource_collection_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  collection_id uuid not null,
  resource_id uuid not null,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_resource_collection_items_tenant_id unique (tenant_id, id)
);
create unique index uq_resource_collection_items_tenant_id_collection__8b2af53b on lms.resource_collection_items (tenant_id, collection_id, resource_id);
create index ix_resource_collection_items_resource_id on lms.resource_collection_items (tenant_id, resource_id);

create table lms.resource_usage_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  resource_id uuid not null,
  person_id uuid,
  event_type text not null,
  duration_sec integer,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_resource_usage_events_event_type check (event_type in ('VIEW', 'DOWNLOAD', 'SHARE', 'COMPLETE')),
  constraint uq_resource_usage_events_tenant_id unique (tenant_id, id)
);
create index ix_resource_usage_events_tenant_id_resource_id_created_at on lms.resource_usage_events (tenant_id, resource_id, created_at desc);
create index ix_resource_usage_events_tenant_id_person_id_created_at on lms.resource_usage_events (tenant_id, person_id, created_at desc);
comment on table lms.resource_usage_events is '[Mongo: LMS07.resource_usage]';

create table lms.resource_reviews (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  resource_id uuid not null,
  reviewer_person_id uuid not null,
  rating smallint not null,
  comment text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_resource_reviews_x1 check (rating between 1 and 5),
  constraint uq_resource_reviews_tenant_id unique (tenant_id, id)
);
create unique index uq_resource_reviews_tenant_id_resource_id_reviewer_person_id on lms.resource_reviews (tenant_id, resource_id, reviewer_person_id) where deleted_at is null;
create index ix_resource_reviews_resource_id on lms.resource_reviews (tenant_id, resource_id);
create index ix_resource_reviews_reviewer_person_id on lms.resource_reviews (tenant_id, reviewer_person_id);
comment on table lms.resource_reviews is '[Mongo: LMS07.resource_reviews]';

create table lms.lesson_plans (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  section_id uuid,
  subject_id uuid,
  employee_id uuid not null,
  term_id uuid,
  title text not null,
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  planned_hours smallint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_lesson_plans_status check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'COMPLETED')),
  constraint uq_lesson_plans_tenant_id unique (tenant_id, id)
);
create index ix_lesson_plans_tenant_id_employee_id_term_id on lms.lesson_plans (tenant_id, employee_id, term_id);
create index ix_lesson_plans_section_id on lms.lesson_plans (tenant_id, section_id);
create index ix_lesson_plans_subject_id on lms.lesson_plans (tenant_id, subject_id);
create index ix_lesson_plans_term_id on lms.lesson_plans (tenant_id, term_id);
create index ix_lesson_plans_workflow_instance_id on lms.lesson_plans (tenant_id, workflow_instance_id);
comment on table lms.lesson_plans is '[Mongo: LMS04.lesson_plans]';

create table lms.lesson_plan_modules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  plan_id uuid not null,
  seq integer not null default 1,
  title text not null,
  planned_hours smallint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_lesson_plan_modules_tenant_id unique (tenant_id, id)
);
create unique index uq_lesson_plan_modules_tenant_id_plan_id_seq on lms.lesson_plan_modules (tenant_id, plan_id, seq) where deleted_at is null;
create index ix_lesson_plan_modules_plan_id on lms.lesson_plan_modules (tenant_id, plan_id);
comment on table lms.lesson_plan_modules is '[Mongo: LMS04.lesson_plan_modules]';

create table lms.lesson_plan_topics (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  module_id uuid not null,
  seq integer not null default 1,
  title text not null,
  planned_hours numeric(9,4),
  bloom_level text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_lesson_plan_topics_x1 check (bloom_level is null or bloom_level in ('REMEMBER','UNDERSTAND','APPLY','ANALYSE','EVALUATE','CREATE')),
  constraint uq_lesson_plan_topics_tenant_id unique (tenant_id, id)
);
create unique index uq_lesson_plan_topics_tenant_id_module_id_seq on lms.lesson_plan_topics (tenant_id, module_id, seq) where deleted_at is null;
create index ix_lesson_plan_topics_module_id on lms.lesson_plan_topics (tenant_id, module_id);
comment on table lms.lesson_plan_topics is '[Mongo: LMS04.lesson_plan_topics]';

create table lms.lesson_sessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  plan_id uuid not null,
  topic_id uuid,
  planned_date date,
  planned_start time,
  planned_end time,
  status text not null default 'PLANNED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_lesson_sessions_status check (status in ('PLANNED', 'DELIVERED', 'MISSED', 'RESCHEDULED', 'CANCELLED')),
  constraint uq_lesson_sessions_tenant_id unique (tenant_id, id)
);
create index ix_lesson_sessions_tenant_id_plan_id_planned_date on lms.lesson_sessions (tenant_id, plan_id, planned_date);
create index ix_lesson_sessions_topic_id on lms.lesson_sessions (tenant_id, topic_id);
comment on table lms.lesson_sessions is '[Mongo: LMS04.lesson_sessions]';

create table lms.lesson_activities (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  activity_type text not null,
  title text,
  duration_min integer,
  details jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_lesson_activities_tenant_id unique (tenant_id, id)
);
create index ix_lesson_activities_session_id on lms.lesson_activities (tenant_id, session_id);
comment on table lms.lesson_activities is '[Mongo: LMS04.lesson_activities]';

create table lms.lesson_faculty (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  plan_id uuid not null,
  employee_id uuid not null,
  role text not null default 'CONTRIBUTOR',
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_lesson_faculty_tenant_id unique (tenant_id, id)
);
create unique index uq_lesson_faculty_tenant_id_plan_id_employee_id on lms.lesson_faculty (tenant_id, plan_id, employee_id);
create index ix_lesson_faculty_employee_id on lms.lesson_faculty (tenant_id, employee_id);
comment on table lms.lesson_faculty is '[Mongo: LMS04.lesson_faculty_assignments]';

create table lms.lesson_content_links (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  topic_id uuid not null,
  content_topic_id uuid,
  resource_id uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_lesson_content_links_x1 check ((content_topic_id is not null) or (resource_id is not null)),
  constraint uq_lesson_content_links_tenant_id unique (tenant_id, id)
);
create index ix_lesson_content_links_topic_id on lms.lesson_content_links (tenant_id, topic_id);
create index ix_lesson_content_links_content_topic_id on lms.lesson_content_links (tenant_id, content_topic_id);
create index ix_lesson_content_links_resource_id on lms.lesson_content_links (tenant_id, resource_id);
comment on table lms.lesson_content_links is '[Mongo: LMS04.lesson_content_mappings]';

create table lms.lesson_deliveries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  delivered_on date not null,
  delivery_mode text not null default 'CLASSROOM',
  coverage_pct numeric(9,4),
  variance_reason text,
  remarks text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_lesson_deliveries_delivery_mode check (delivery_mode in ('CLASSROOM', 'ONLINE', 'HYBRID')),
  constraint ck_lesson_deliveries_coverage_pct check (coverage_pct between 0 and 100),
  constraint uq_lesson_deliveries_tenant_id unique (tenant_id, id)
);
create unique index uq_lesson_deliveries_tenant_id_session_id_delivered_on on lms.lesson_deliveries (tenant_id, session_id, delivered_on) where deleted_at is null;
create index ix_lesson_deliveries_session_id on lms.lesson_deliveries (tenant_id, session_id);
comment on table lms.lesson_deliveries is 'Variance vs plan is derived: see lms.v_lesson_variance [Mongo: LMS04.lesson_deliveries]';

create table lms.rubrics (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  title text not null,
  description text,
  max_points numeric(18,2),
  status text not null default 'ACTIVE',
  owner_employee_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_rubrics_max_points check (max_points >= 0),
  constraint ck_rubrics_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_rubrics_tenant_id unique (tenant_id, id)
);
create index ix_rubrics_owner_employee_id on lms.rubrics (tenant_id, owner_employee_id);
comment on table lms.rubrics is '[Mongo: LMS05.assignment_rubrics, LMS10.rubrics]';

create table lms.rubric_criteria (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  rubric_id uuid not null,
  seq integer not null default 1,
  name text not null,
  description text,
  max_points numeric(18,2) not null,
  weight numeric(9,4),
  outcome_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_rubric_criteria_max_points check (max_points >= 0),
  constraint uq_rubric_criteria_tenant_id unique (tenant_id, id)
);
create unique index uq_rubric_criteria_tenant_id_rubric_id_seq on lms.rubric_criteria (tenant_id, rubric_id, seq) where deleted_at is null;
create index ix_rubric_criteria_rubric_id on lms.rubric_criteria (tenant_id, rubric_id);
create index ix_rubric_criteria_outcome_id on lms.rubric_criteria (tenant_id, outcome_id);
comment on table lms.rubric_criteria is '[Mongo: LMS05.assignment_rubric_criteria, LMS10.rubric_criteria]';

create table lms.rubric_levels (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  criterion_id uuid not null,
  level_no smallint not null,
  label text not null,
  points numeric(18,2) not null,
  descriptor text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_rubric_levels_level_no check (level_no > 0),
  constraint ck_rubric_levels_points check (points >= 0),
  constraint uq_rubric_levels_tenant_id unique (tenant_id, id)
);
create unique index uq_rubric_levels_tenant_id_criterion_id_level_no on lms.rubric_levels (tenant_id, criterion_id, level_no) where deleted_at is null;
create index ix_rubric_levels_criterion_id on lms.rubric_levels (tenant_id, criterion_id);
comment on table lms.rubric_levels is 'Normalised performance levels per rubric criterion (new)';

create table lms.assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  section_id uuid,
  course_id uuid,
  subject_id uuid,
  created_by_employee_id uuid,
  rubric_id uuid,
  title text not null,
  instructions text,
  assignment_type text not null default 'ASSIGNMENT',
  max_marks numeric(18,2),
  weightage_pct numeric(9,4),
  available_from timestamptz,
  due_at timestamptz,
  close_at timestamptz,
  late_penalty_pct numeric(9,4),
  allow_resubmission boolean not null default false,
  version_no integer not null default 1,
  status text not null default 'DRAFT',
  assessment_component_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_assignments_assignment_type check (assignment_type in ('ASSIGNMENT', 'QUIZ', 'PROJECT', 'LAB', 'PRESENTATION')),
  constraint ck_assignments_max_marks check (max_marks >= 0),
  constraint ck_assignments_weightage_pct check (weightage_pct between 0 and 100),
  constraint ck_assignments_late_penalty_pct check (late_penalty_pct between 0 and 100),
  constraint ck_assignments_status check (status in ('DRAFT', 'PUBLISHED', 'CLOSED', 'ARCHIVED')),
  constraint uq_assignments_tenant_id unique (tenant_id, id)
);
create index ix_assignments_tenant_id_section_id_status on lms.assignments (tenant_id, section_id, status);
create index ix_assignments_tenant_id_due_at on lms.assignments (tenant_id, due_at);
create index ix_assignments_course_id on lms.assignments (tenant_id, course_id);
create index ix_assignments_subject_id on lms.assignments (tenant_id, subject_id);
create index ix_assignments_created_by_employee_id on lms.assignments (tenant_id, created_by_employee_id);
create index ix_assignments_rubric_id on lms.assignments (tenant_id, rubric_id);
create index ix_assignments_assessment_component_id on lms.assignments (tenant_id, assessment_component_id);
comment on table lms.assignments is 'Availability window + late policy folded into the row (was assignment_availability). Versions in core.entity_versions [Mongo: LMS05.assignments, LMS05.assignment_availability]';

create table lms.assignment_questions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  assignment_id uuid not null,
  seq integer not null default 1,
  question_type text not null default 'SUBJECTIVE',
  prompt text not null,
  options jsonb,
  answer_key jsonb,
  max_marks numeric(18,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_assignment_questions_question_type check (question_type in ('SUBJECTIVE', 'MCQ', 'TRUE_FALSE', 'CODE', 'FILE')),
  constraint ck_assignment_questions_max_marks check (max_marks >= 0),
  constraint uq_assignment_questions_tenant_id unique (tenant_id, id)
);
create unique index uq_assignment_questions_tenant_id_assignment_id_seq on lms.assignment_questions (tenant_id, assignment_id, seq) where deleted_at is null;
create index ix_assignment_questions_assignment_id on lms.assignment_questions (tenant_id, assignment_id);
comment on table lms.assignment_questions is '[Mongo: LMS05.assignment_questions]';

create table lms.assignment_resources (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  assignment_id uuid not null,
  resource_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_assignment_resources_tenant_id unique (tenant_id, id)
);
create unique index uq_assignment_resources_tenant_id_assignment_id_resource_id on lms.assignment_resources (tenant_id, assignment_id, resource_id);
create index ix_assignment_resources_resource_id on lms.assignment_resources (tenant_id, resource_id);
comment on table lms.assignment_resources is '[Mongo: LMS05.assignment_resources]';

create table lms.student_assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  assignment_id uuid not null,
  student_id uuid not null,
  status text not null default 'ASSIGNED',
  assigned_at timestamptz not null default now(),
  extended_due_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_student_assignments_status check (status in ('ASSIGNED', 'IN_PROGRESS', 'SUBMITTED', 'GRADED', 'MISSED', 'EXCUSED')),
  constraint uq_student_assignments_tenant_id unique (tenant_id, id)
);
create unique index uq_student_assignments_tenant_id_assignment_id_student_id on lms.student_assignments (tenant_id, assignment_id, student_id) where deleted_at is null;
create index ix_student_assignments_tenant_id_student_id_status on lms.student_assignments (tenant_id, student_id, status);
create index ix_student_assignments_assignment_id on lms.student_assignments (tenant_id, assignment_id);
comment on table lms.student_assignments is '[Mongo: LMS05.student_assignments]';

create table lms.submissions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_assignment_id uuid not null,
  student_id uuid not null,
  status text not null default 'DRAFT',
  first_submitted_at timestamptz,
  latest_submitted_at timestamptz,
  attempt_count integer not null default 0,
  is_late boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_submissions_status check (status in ('DRAFT', 'SUBMITTED', 'RETURNED', 'GRADED')),
  constraint uq_submissions_tenant_id unique (tenant_id, id)
);
create unique index uq_submissions_tenant_id_student_assignment_id on lms.submissions (tenant_id, student_assignment_id) where deleted_at is null;
create index ix_submissions_student_assignment_id on lms.submissions (tenant_id, student_assignment_id);
create index ix_submissions_student_id on lms.submissions (tenant_id, student_id);
comment on table lms.submissions is '[Mongo: LMS05.submissions]';

create table lms.submission_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  submission_id uuid not null,
  attempt_no integer not null,
  answers jsonb default '{}'::jsonb,
  text_body text,
  submitted_at timestamptz not null default now(),
  similarity_score numeric(9,4),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_submission_versions_attempt_no check (attempt_no > 0),
  constraint ck_submission_versions_similarity_score check (similarity_score between 0 and 100),
  constraint uq_submission_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_submission_versions_tenant_id_submission_id_attempt_no on lms.submission_versions (tenant_id, submission_id, attempt_no);
comment on table lms.submission_versions is 'Immutable per-attempt content. Files: doc.document_links (entity_type=lms.submission_versions) [Mongo: LMS05.submission_versions, LMS05.submission_files]';

create table lms.evaluations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  submission_id uuid not null,
  attempt_no integer,
  evaluator_employee_id uuid,
  total_marks numeric(18,2),
  rubric_id uuid,
  status text not null default 'PENDING',
  evaluated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_evaluations_total_marks check (total_marks >= 0),
  constraint ck_evaluations_status check (status in ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'MODERATED')),
  constraint uq_evaluations_tenant_id unique (tenant_id, id)
);
create index ix_evaluations_tenant_id_evaluator_employee_id_status on lms.evaluations (tenant_id, evaluator_employee_id, status);
create index ix_evaluations_submission_id on lms.evaluations (tenant_id, submission_id);
create index ix_evaluations_rubric_id on lms.evaluations (tenant_id, rubric_id);
comment on table lms.evaluations is '[Mongo: LMS05.evaluations]';

create table lms.evaluation_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  evaluation_id uuid not null,
  question_id uuid,
  criterion_id uuid,
  level_id uuid,
  marks numeric(18,2),
  comment text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_evaluation_items_marks check (marks >= 0),
  constraint uq_evaluation_items_tenant_id unique (tenant_id, id)
);
create index ix_evaluation_items_evaluation_id on lms.evaluation_items (tenant_id, evaluation_id);
create index ix_evaluation_items_question_id on lms.evaluation_items (tenant_id, question_id);
create index ix_evaluation_items_criterion_id on lms.evaluation_items (tenant_id, criterion_id);
create index ix_evaluation_items_level_id on lms.evaluation_items (tenant_id, level_id);
comment on table lms.evaluation_items is '[Mongo: LMS05.evaluation_items]';

create table lms.grades (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_assignment_id uuid not null,
  student_id uuid not null,
  evaluation_id uuid,
  marks numeric(18,2),
  grade_letter text,
  is_published boolean not null default false,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_grades_marks check (marks >= 0),
  constraint uq_grades_tenant_id unique (tenant_id, id)
);
create unique index uq_grades_tenant_id_student_assignment_id on lms.grades (tenant_id, student_assignment_id) where deleted_at is null;
create index ix_grades_student_assignment_id on lms.grades (tenant_id, student_assignment_id);
create index ix_grades_student_id on lms.grades (tenant_id, student_id);
create index ix_grades_evaluation_id on lms.grades (tenant_id, evaluation_id);
comment on table lms.grades is '[Mongo: LMS05.grades]';

create table lms.submission_feedback (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  submission_id uuid not null,
  given_by uuid,
  body text not null,
  visibility text not null default 'STUDENT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_submission_feedback_visibility check (visibility in ('STUDENT', 'STAFF')),
  constraint uq_submission_feedback_tenant_id unique (tenant_id, id)
);
create index ix_submission_feedback_submission_id on lms.submission_feedback (tenant_id, submission_id);
create index ix_submission_feedback_given_by on lms.submission_feedback (tenant_id, given_by);
comment on table lms.submission_feedback is '[Mongo: LMS05.feedback]';

create table lms.resubmission_requests (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_assignment_id uuid not null,
  reason text,
  status text not null default 'PENDING',
  new_due_at timestamptz,
  decided_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_resubmission_requests_status check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  constraint uq_resubmission_requests_tenant_id unique (tenant_id, id)
);
create index ix_resubmission_requests_student_assignment_id on lms.resubmission_requests (tenant_id, student_assignment_id);
create index ix_resubmission_requests_decided_by on lms.resubmission_requests (tenant_id, decided_by);
comment on table lms.resubmission_requests is '[Mongo: LMS05.resubmissions]';

create table lms.virtual_classrooms (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  section_id uuid,
  course_id uuid,
  owner_employee_id uuid,
  name text not null,
  provider text not null,
  settings jsonb default '{}'::jsonb,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_virtual_classrooms_provider check (provider in ('ZOOM', 'MEET', 'TEAMS', 'JITSI', 'BIGBLUEBUTTON', 'OTHER')),
  constraint ck_virtual_classrooms_status check (status in ('ACTIVE', 'ARCHIVED')),
  constraint uq_virtual_classrooms_tenant_id unique (tenant_id, id)
);
create index ix_virtual_classrooms_section_id on lms.virtual_classrooms (tenant_id, section_id);
create index ix_virtual_classrooms_course_id on lms.virtual_classrooms (tenant_id, course_id);
create index ix_virtual_classrooms_owner_employee_id on lms.virtual_classrooms (tenant_id, owner_employee_id);
comment on table lms.virtual_classrooms is '[Mongo: LMS06.virtual_classrooms, LMS06.meeting_configurations]';

create table lms.virtual_sessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  classroom_id uuid not null,
  title text,
  scheduled_start timestamptz not null,
  scheduled_end timestamptz,
  actual_start timestamptz,
  actual_end timestamptz,
  provider_meeting_id text,
  join_url text,
  host_url text,
  attendance_session_id uuid,
  status text not null default 'SCHEDULED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_virtual_sessions_status check (status in ('SCHEDULED', 'LIVE', 'ENDED', 'CANCELLED')),
  constraint uq_virtual_sessions_tenant_id unique (tenant_id, id)
);
create index ix_virtual_sessions_tenant_id_classroom_id_scheduled_start on lms.virtual_sessions (tenant_id, classroom_id, scheduled_start);
create index ix_virtual_sessions_attendance_session_id on lms.virtual_sessions (tenant_id, attendance_session_id);
comment on table lms.virtual_sessions is '[Mongo: LMS06.virtual_sessions, LMS06.provider_meetings, LMS06.session_attendance_refs]';

create table lms.session_participants (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  person_id uuid not null,
  role text not null default 'ATTENDEE',
  first_join_at timestamptz,
  last_leave_at timestamptz,
  total_minutes integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_session_participants_role check (role in ('HOST', 'COHOST', 'ATTENDEE', 'OBSERVER')),
  constraint ck_session_participants_total_minutes check (total_minutes >= 0),
  constraint uq_session_participants_tenant_id unique (tenant_id, id)
);
create unique index uq_session_participants_tenant_id_session_id_person_id on lms.session_participants (tenant_id, session_id, person_id) where deleted_at is null;
create index ix_session_participants_session_id on lms.session_participants (tenant_id, session_id);
create index ix_session_participants_person_id on lms.session_participants (tenant_id, person_id);
comment on table lms.session_participants is '[Mongo: LMS06.session_participants, LMS06.participant_sessions]';

create table lms.session_recordings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  document_id uuid,
  duration_sec integer,
  status text not null default 'PROCESSING',
  published boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_session_recordings_status check (status in ('PROCESSING', 'READY', 'FAILED', 'DELETED')),
  constraint uq_session_recordings_tenant_id unique (tenant_id, id)
);
create index ix_session_recordings_session_id on lms.session_recordings (tenant_id, session_id);
create index ix_session_recordings_document_id on lms.session_recordings (tenant_id, document_id);
comment on table lms.session_recordings is '[Mongo: LMS06.session_recordings]';

create table lms.session_interactions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  person_id uuid,
  interaction_type text not null,
  payload jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_session_interactions_interaction_type check (interaction_type in ('CHAT', 'QUESTION', 'HAND_RAISE', 'REACTION')),
  constraint uq_session_interactions_tenant_id unique (tenant_id, id)
);
create index ix_session_interactions_tenant_id_session_id_created_at on lms.session_interactions (tenant_id, session_id, created_at);
create index ix_session_interactions_person_id on lms.session_interactions (tenant_id, person_id);
comment on table lms.session_interactions is '[Mongo: LMS06.session_interactions]';

create table lms.session_polls (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  question text not null,
  options jsonb not null default '{}'::jsonb,
  opened_at timestamptz,
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_session_polls_tenant_id unique (tenant_id, id)
);
create index ix_session_polls_session_id on lms.session_polls (tenant_id, session_id);
comment on table lms.session_polls is '[Mongo: LMS06.session_polls]';

create table lms.session_poll_responses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  poll_id uuid not null,
  person_id uuid not null,
  answer jsonb not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_session_poll_responses_tenant_id unique (tenant_id, id)
);
create unique index uq_session_poll_responses_tenant_id_poll_id_person_id on lms.session_poll_responses (tenant_id, poll_id, person_id);
create index ix_session_poll_responses_person_id on lms.session_poll_responses (tenant_id, person_id);
comment on table lms.session_poll_responses is 'Normalised from session_polls results';

create table lms.breakout_rooms (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  name text not null,
  opened_at timestamptz,
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_breakout_rooms_tenant_id unique (tenant_id, id)
);
create index ix_breakout_rooms_session_id on lms.breakout_rooms (tenant_id, session_id);
comment on table lms.breakout_rooms is '[Mongo: LMS06.breakout_rooms]';

create table lms.breakout_room_members (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  room_id uuid not null,
  person_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_breakout_room_members_tenant_id unique (tenant_id, id)
);
create unique index uq_breakout_room_members_tenant_id_room_id_person_id on lms.breakout_room_members (tenant_id, room_id, person_id);
create index ix_breakout_room_members_person_id on lms.breakout_room_members (tenant_id, person_id);

create table lms.session_resources (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  resource_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_session_resources_tenant_id unique (tenant_id, id)
);
create unique index uq_session_resources_tenant_id_session_id_resource_id on lms.session_resources (tenant_id, session_id, resource_id);
create index ix_session_resources_resource_id on lms.session_resources (tenant_id, resource_id);
comment on table lms.session_resources is '[Mongo: LMS06.session_resources]';

create table lms.progress (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  scope_type text not null,
  scope_id uuid not null,
  status text not null default 'NOT_STARTED',
  percent_complete numeric(9,4) not null default 0,
  time_spent_sec bigint not null default 0,
  started_at timestamptz,
  completed_at timestamptz,
  last_activity_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_progress_scope_type check (scope_type in ('ACTIVITY', 'TOPIC', 'MODULE', 'LESSON', 'COURSE', 'SUBJECT', 'PROGRAM')),
  constraint ck_progress_status check (status in ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED')),
  constraint ck_progress_percent_complete check (percent_complete between 0 and 100),
  constraint ck_progress_time_spent_sec check (time_spent_sec >= 0),
  constraint uq_progress_tenant_id unique (tenant_id, id)
);
create unique index uq_progress_tenant_id_student_id_scope_type_scope_id on lms.progress (tenant_id, student_id, scope_type, scope_id) where deleted_at is null;
create index ix_progress_tenant_id_scope_type_scope_id_status on lms.progress (tenant_id, scope_type, scope_id, status);
create index ix_progress_student_id on lms.progress (tenant_id, student_id);
comment on table lms.progress is 'ONE progress fact keyed by (student, scope). Replaces 6 per-level progress collections + completion_records [Mongo: LMS08.progress_records, LMS08.activity_progress, LMS08.course_progress, LMS08.subject_progress, LMS08.module_progress, LMS08.lesson_progress, LMS08.completion_records]';

create table lms.progress_events (
  id uuid not null default gen_random_uuid(),
  tenant_id uuid default core.current_tenant_id(),
  student_id uuid not null,
  scope_type text not null,
  scope_id uuid not null,
  event_type text not null,
  payload jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  primary key (id, created_at)
) partition by range (created_at);
create table lms.progress_events_default partition of lms.progress_events default;
create index ix_progress_events_tenant_id_student_id_created_at on lms.progress_events (tenant_id, student_id, created_at desc);
create index ix_progress_events_tenant_id_scope_type_scope_id_created_at on lms.progress_events (tenant_id, scope_type, scope_id, created_at desc);
comment on table lms.progress_events is 'Raw learner events, monthly partitions, no FKs by design (ingest speed) [Mongo: LMS08.progress_events]';

create table lms.progress_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scope_type text not null,
  subject_id uuid,
  course_id uuid,
  criteria jsonb not null default '{}'::jsonb,
  weight numeric(9,4),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_progress_rules_tenant_id unique (tenant_id, id)
);
create index ix_progress_rules_subject_id on lms.progress_rules (tenant_id, subject_id);
create index ix_progress_rules_course_id on lms.progress_rules (tenant_id, course_id);
comment on table lms.progress_rules is '[Mongo: LMS08.progress_rules]';

create table lms.progress_milestones (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  milestone_code text not null,
  scope_type text,
  scope_id uuid,
  achieved_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_progress_milestones_tenant_id unique (tenant_id, id)
);
create unique index uq_progress_milestones_tenant_id_student_id_milest_023402ba on lms.progress_milestones (tenant_id, student_id, milestone_code, scope_id) where deleted_at is null;
create index ix_progress_milestones_student_id on lms.progress_milestones (tenant_id, student_id);
comment on table lms.progress_milestones is '[Mongo: LMS08.progress_milestones]';

create table lms.learning_streaks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  current_streak integer not null default 0,
  longest_streak integer not null default 0,
  last_active_on date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_learning_streaks_current_streak check (current_streak >= 0),
  constraint ck_learning_streaks_longest_streak check (longest_streak >= 0),
  constraint uq_learning_streaks_tenant_id unique (tenant_id, id)
);
create unique index uq_learning_streaks_tenant_id_student_id on lms.learning_streaks (tenant_id, student_id) where deleted_at is null;
create index ix_learning_streaks_student_id on lms.learning_streaks (tenant_id, student_id);
comment on table lms.learning_streaks is '[Mongo: LMS08.learning_streaks]';

create table lms.certification_programs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  issuer text,
  description text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_certification_programs_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_certification_programs_tenant_id unique (tenant_id, id)
);
create unique index uq_certification_programs_tenant_id_code on lms.certification_programs (tenant_id, code) where deleted_at is null;
comment on table lms.certification_programs is '[Mongo: LMS09.certification_programs]';

create table lms.certifications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  program_id uuid,
  code text not null,
  name text not null,
  credential_type text not null default 'CERTIFICATE',
  validity_months smallint,
  template_id uuid,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_certifications_credential_type check (credential_type in ('CERTIFICATE', 'MICRO_CREDENTIAL', 'DIPLOMA')),
  constraint ck_certifications_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_certifications_tenant_id unique (tenant_id, id)
);
create unique index uq_certifications_tenant_id_code on lms.certifications (tenant_id, code) where deleted_at is null;
create index ix_certifications_program_id on lms.certifications (tenant_id, program_id);
create index ix_certifications_template_id on lms.certifications (tenant_id, template_id);
comment on table lms.certifications is '[Mongo: LMS09.certification_definitions]';

create table lms.certification_requirements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  certification_id uuid not null,
  requirement_type text not null,
  ref_id uuid,
  params jsonb default '{}'::jsonb,
  is_mandatory boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_certification_requirements_requirement_type check (requirement_type in ('COURSE', 'SUBJECT', 'CREDITS', 'ATTENDANCE', 'ASSESSMENT', 'CUSTOM')),
  constraint uq_certification_requirements_tenant_id unique (tenant_id, id)
);
create index ix_certification_requirements_certification_id on lms.certification_requirements (tenant_id, certification_id);
comment on table lms.certification_requirements is '[Mongo: LMS09.certification_requirements, LMS09.certification_rules]';

create table lms.certification_applications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  certification_id uuid not null,
  student_id uuid not null,
  status text not null default 'APPLIED',
  applied_at timestamptz not null default now(),
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_certification_applications_status check (status in ('APPLIED', 'ELIGIBLE', 'INELIGIBLE', 'APPROVED', 'REJECTED', 'ISSUED')),
  constraint uq_certification_applications_tenant_id unique (tenant_id, id)
);
create unique index uq_certification_applications_tenant_id_certificat_4b14c432 on lms.certification_applications (tenant_id, certification_id, student_id) where deleted_at is null;
create index ix_certification_applications_certification_id on lms.certification_applications (tenant_id, certification_id);
create index ix_certification_applications_student_id on lms.certification_applications (tenant_id, student_id);
create index ix_certification_applications_workflow_instance_id on lms.certification_applications (tenant_id, workflow_instance_id);
comment on table lms.certification_applications is '[Mongo: LMS09.certification_applications]';

create table lms.eligibility_evaluations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  application_id uuid not null,
  result text not null,
  detail jsonb,
  evaluated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_eligibility_evaluations_result check (result in ('ELIGIBLE', 'INELIGIBLE', 'PARTIAL')),
  constraint uq_eligibility_evaluations_tenant_id unique (tenant_id, id)
);
create index ix_eligibility_evaluations_application_id on lms.eligibility_evaluations (tenant_id, application_id);
comment on table lms.eligibility_evaluations is '[Mongo: LMS09.eligibility_evaluations]';

create table lms.eligibility_evidence (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  evaluation_id uuid not null,
  evidence_type text not null,
  ref_id uuid,
  document_id uuid,
  detail jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_eligibility_evidence_tenant_id unique (tenant_id, id)
);
create index ix_eligibility_evidence_evaluation_id on lms.eligibility_evidence (tenant_id, evaluation_id);
create index ix_eligibility_evidence_document_id on lms.eligibility_evidence (tenant_id, document_id);
comment on table lms.eligibility_evidence is '[Mongo: LMS09.eligibility_evidence]';

create table lms.credentials (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  certification_id uuid not null,
  student_id uuid not null,
  credential_no text not null,
  issued_at timestamptz not null default now(),
  expires_at timestamptz,
  status text not null default 'ACTIVE',
  document_id uuid,
  verification_code text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_credentials_status check (status in ('ACTIVE', 'REVOKED', 'EXPIRED', 'SUPERSEDED')),
  constraint uq_credentials_tenant_id unique (tenant_id, id)
);
create unique index uq_credentials_tenant_id_credential_no on lms.credentials (tenant_id, credential_no) where deleted_at is null;
create unique index uq_credentials_tenant_id_verification_code on lms.credentials (tenant_id, verification_code) where deleted_at is null and verification_code is not null;
create index ix_credentials_tenant_id_student_id_status on lms.credentials (tenant_id, student_id, status);
create index ix_credentials_certification_id on lms.credentials (tenant_id, certification_id);
create index ix_credentials_document_id on lms.credentials (tenant_id, document_id);
comment on table lms.credentials is 'Issued certificate + registry entry + expiry state in one row; re-issues are new rows (status SUPERSEDED on the old one) [Mongo: LMS09.certificates, LMS09.credentials, LMS09.certificate_versions, LMS09.credential_expiry]';

create table lms.credential_revocations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  credential_id uuid not null,
  reason text not null,
  revoked_by uuid,
  revoked_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_credential_revocations_tenant_id unique (tenant_id, id)
);
create index ix_credential_revocations_credential_id on lms.credential_revocations (tenant_id, credential_id);
create index ix_credential_revocations_revoked_by on lms.credential_revocations (tenant_id, revoked_by);
comment on table lms.credential_revocations is '[Mongo: LMS09.credential_revocations]';

create table lms.credential_shares (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  credential_id uuid not null,
  share_token text not null,
  channel text,
  expires_at timestamptz,
  revoked_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_credential_shares_tenant_id unique (tenant_id, id)
);
create unique index uq_credential_shares_tenant_id_share_token on lms.credential_shares (tenant_id, share_token) where deleted_at is null;
create index ix_credential_shares_credential_id on lms.credential_shares (tenant_id, credential_id);
comment on table lms.credential_shares is '[Mongo: LMS09.credential_shares]';

create table lms.badges (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  description text,
  icon_document_id uuid,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_badges_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_badges_tenant_id unique (tenant_id, id)
);
create unique index uq_badges_tenant_id_code on lms.badges (tenant_id, code) where deleted_at is null;
create index ix_badges_icon_document_id on lms.badges (tenant_id, icon_document_id);
comment on table lms.badges is '[Mongo: LMS09.badges]';

create table lms.badge_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  badge_id uuid not null,
  rule_type text not null,
  params jsonb not null default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_badge_rules_tenant_id unique (tenant_id, id)
);
create index ix_badge_rules_badge_id on lms.badge_rules (tenant_id, badge_id);
comment on table lms.badge_rules is '[Mongo: LMS09.badge_rules]';

create table lms.badge_awards (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  badge_id uuid not null,
  student_id uuid not null,
  awarded_at timestamptz not null default now(),
  credential_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_badge_awards_tenant_id unique (tenant_id, id)
);
create unique index uq_badge_awards_tenant_id_badge_id_student_id on lms.badge_awards (tenant_id, badge_id, student_id) where deleted_at is null;
create index ix_badge_awards_badge_id on lms.badge_awards (tenant_id, badge_id);
create index ix_badge_awards_student_id on lms.badge_awards (tenant_id, student_id);
create index ix_badge_awards_credential_id on lms.badge_awards (tenant_id, credential_id);
comment on table lms.badge_awards is '[Mongo: LMS09.badge_awards]';


-- ============================================================ obe

create table obe.outcomes (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  outcome_type text not null,
  code text not null,
  statement text not null,
  program_id uuid,
  subject_id uuid,
  curriculum_version_id uuid,
  parent_outcome_id uuid,
  bloom_level text,
  sequence smallint,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_outcomes_outcome_type check (outcome_type in ('PEO', 'PO', 'PSO', 'CO', 'TLO')),
  constraint ck_outcomes_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint ck_outcomes_x1 check ((outcome_type in ('PEO','PO','PSO') and program_id is not null) or (outcome_type in ('CO','TLO') and subject_id is not null)),
  constraint ck_outcomes_x2 check (bloom_level is null or bloom_level in ('REMEMBER','UNDERSTAND','APPLY','ANALYSE','EVALUATE','CREATE')),
  constraint uq_outcomes_tenant_id unique (tenant_id, id)
);
create unique index uq_outcomes_tenant_id_outcome_type_program_id_code on obe.outcomes (tenant_id, outcome_type, program_id, code) where deleted_at is null and outcome_type in ('PEO','PO','PSO');
create unique index uq_outcomes_tenant_id_outcome_type_subject_id_curr_542d842a on obe.outcomes (tenant_id, outcome_type, subject_id, curriculum_version_id, code) where deleted_at is null and outcome_type in ('CO','TLO');
create index ix_outcomes_tenant_id_program_id_outcome_type on obe.outcomes (tenant_id, program_id, outcome_type);
create index ix_outcomes_tenant_id_subject_id_outcome_type on obe.outcomes (tenant_id, subject_id, outcome_type);
create index ix_outcomes_curriculum_version_id on obe.outcomes (tenant_id, curriculum_version_id);
create index ix_outcomes_parent_outcome_id on obe.outcomes (tenant_id, parent_outcome_id);
comment on table obe.outcomes is 'PO/PSO/CO/TLO in ONE table; bloom_level = Revised Bloom taxonomy [Mongo: LMS10.program_outcomes, LMS10.program_specific_outcomes, LMS10.course_outcomes, LMS10.teaching_learning_outcomes, ACD02.curriculum_outcomes]';

create table obe.outcome_mappings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  from_outcome_id uuid not null,
  to_outcome_id uuid not null,
  correlation_level smallint not null,
  rationale text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_outcome_mappings_correlation_level check (correlation_level in ('1', '2', '3')),
  constraint ck_outcome_mappings_x1 check (from_outcome_id <> to_outcome_id),
  constraint uq_outcome_mappings_tenant_id unique (tenant_id, id)
);
create unique index uq_outcome_mappings_tenant_id_from_outcome_id_to_outcome_id on obe.outcome_mappings (tenant_id, from_outcome_id, to_outcome_id) where deleted_at is null;
create index ix_outcome_mappings_from_outcome_id on obe.outcome_mappings (tenant_id, from_outcome_id);
create index ix_outcome_mappings_to_outcome_id on obe.outcome_mappings (tenant_id, to_outcome_id);
comment on table obe.outcome_mappings is 'CO-PO, CO-PSO, CO-TLO articulation matrix (level 1-3). Mapping kind is implied by the two outcome types [Mongo: LMS10.outcome_mappings, LMS10.co_tlo_mappings, LMS10.co_po_mappings, LMS10.co_pso_mappings]';

create table obe.alignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  outcome_id uuid not null,
  target_type text not null,
  target_id uuid not null,
  weightage_pct numeric(9,4),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_alignments_target_type check (target_type in ('ASSIGNMENT', 'ASSESSMENT_COMPONENT', 'QUESTION', 'ACTIVITY', 'LESSON_TOPIC', 'EXAM_QUESTION')),
  constraint ck_alignments_weightage_pct check (weightage_pct between 0 and 100),
  constraint uq_alignments_tenant_id unique (tenant_id, id)
);
create unique index uq_alignments_tenant_id_outcome_id_target_type_target_id on obe.alignments (tenant_id, outcome_id, target_type, target_id) where deleted_at is null;
create index ix_alignments_tenant_id_target_type_target_id on obe.alignments (tenant_id, target_type, target_id);
create index ix_alignments_outcome_id on obe.alignments (tenant_id, outcome_id);
comment on table obe.alignments is 'Outcome -> assessment artefact alignment (polymorphic target; validate via trigger or app layer) [Mongo: LMS10.activity_outcome_mappings, LMS10.assessment_outcome_mappings, LMS10.question_outcome_mappings, ACD09.outcome_mappings, LMS05.assignment_outcome_mappings, LMS04.lesson_outcome_mappings]';

create table obe.attainment_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scope text not null,
  subject_id uuid,
  program_id uuid,
  direct_weight_pct numeric(9,4) not null default 80,
  indirect_weight_pct numeric(9,4) not null default 20,
  target_level smallint,
  effective_from date,
  effective_to date,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attainment_rules_scope check (scope in ('COURSE', 'PROGRAM')),
  constraint ck_attainment_rules_direct_weight_pct check (direct_weight_pct between 0 and 100),
  constraint ck_attainment_rules_indirect_weight_pct check (indirect_weight_pct between 0 and 100),
  constraint ck_attainment_rules_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint ck_attainment_rules_x1 check (direct_weight_pct + indirect_weight_pct = 100),
  constraint uq_attainment_rules_tenant_id unique (tenant_id, id)
);
create index ix_attainment_rules_subject_id on obe.attainment_rules (tenant_id, subject_id);
create index ix_attainment_rules_program_id on obe.attainment_rules (tenant_id, program_id);
comment on table obe.attainment_rules is '[Mongo: LMS10.attainment_rules]';

create table obe.attainment_thresholds (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  rule_id uuid not null,
  level_no smallint not null,
  min_pct numeric(9,4) not null,
  label text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attainment_thresholds_level_no check (level_no > 0),
  constraint ck_attainment_thresholds_min_pct check (min_pct between 0 and 100),
  constraint uq_attainment_thresholds_tenant_id unique (tenant_id, id)
);
create unique index uq_attainment_thresholds_tenant_id_rule_id_level_no on obe.attainment_thresholds (tenant_id, rule_id, level_no) where deleted_at is null;
create index ix_attainment_thresholds_rule_id on obe.attainment_thresholds (tenant_id, rule_id);
comment on table obe.attainment_thresholds is '[Mongo: LMS10.attainment_thresholds]';

create table obe.attainment_evidence (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  outcome_id uuid not null,
  student_id uuid,
  section_id uuid,
  source_type text not null,
  source_id uuid,
  score numeric(18,2),
  max_score numeric(18,2),
  measure text not null default 'DIRECT',
  term_id uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_attainment_evidence_measure check (measure in ('DIRECT', 'INDIRECT')),
  constraint uq_attainment_evidence_tenant_id unique (tenant_id, id)
);
create index ix_attainment_evidence_tenant_id_outcome_id_term_id on obe.attainment_evidence (tenant_id, outcome_id, term_id);
create index ix_attainment_evidence_tenant_id_student_id on obe.attainment_evidence (tenant_id, student_id);
create index ix_attainment_evidence_section_id on obe.attainment_evidence (tenant_id, section_id);
create index ix_attainment_evidence_term_id on obe.attainment_evidence (tenant_id, term_id);
comment on table obe.attainment_evidence is 'Per-student score evidence feeding attainment; append-only [Mongo: LMS10.attainment_evidence, LMS10.outcome_assessments]';

create table obe.attainments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  outcome_id uuid not null,
  term_id uuid,
  section_id uuid,
  rule_id uuid,
  direct_pct numeric(9,4),
  indirect_pct numeric(9,4),
  overall_pct numeric(9,4),
  level_no smallint,
  computed_at timestamptz not null default now(),
  is_frozen boolean not null default false,
  frozen_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attainments_direct_pct check (direct_pct between 0 and 100),
  constraint ck_attainments_indirect_pct check (indirect_pct between 0 and 100),
  constraint ck_attainments_overall_pct check (overall_pct between 0 and 100),
  constraint uq_attainments_tenant_id unique (tenant_id, id)
);
create index ix_attainments_tenant_id_outcome_id_term_id on obe.attainments (tenant_id, outcome_id, term_id);
create index ix_attainments_term_id on obe.attainments (tenant_id, term_id);
create index ix_attainments_section_id on obe.attainments (tenant_id, section_id);
create index ix_attainments_rule_id on obe.attainments (tenant_id, rule_id);
comment on table obe.attainments is 'CO/PO/PSO attainment rows (discriminated by outcome.outcome_type). Frozen rows = reporting snapshots [Mongo: LMS10.co_attainment, LMS10.po_attainment, LMS10.pso_attainment, LMS10.outcome_snapshots]';


-- ============================================================ exm

create table exm.examinations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  term_id uuid,
  code text not null,
  name text not null,
  exam_type text not null default 'REGULAR',
  status text not null default 'PLANNED',
  registration_start timestamptz,
  registration_end timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_examinations_exam_type check (exam_type in ('REGULAR', 'SUPPLEMENTARY', 'BACKLOG', 'IMPROVEMENT')),
  constraint ck_examinations_status check (status in ('PLANNED', 'REGISTRATION_OPEN', 'SCHEDULED', 'IN_PROGRESS', 'COMPLETED', 'RESULTS_PUBLISHED', 'CANCELLED')),
  constraint uq_examinations_tenant_id unique (tenant_id, id)
);
create unique index uq_examinations_tenant_id_code on exm.examinations (tenant_id, code) where deleted_at is null;
create index ix_examinations_college_id on exm.examinations (tenant_id, college_id);
create index ix_examinations_term_id on exm.examinations (tenant_id, term_id);
comment on table exm.examinations is '[Mongo: EXM02.examinations]';

create table exm.exam_subjects (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  examination_id uuid not null,
  subject_id uuid not null,
  assessment_structure_id uuid,
  max_marks numeric(18,2) not null,
  pass_marks numeric(18,2),
  duration_min integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_exam_subjects_max_marks check (max_marks >= 0),
  constraint ck_exam_subjects_pass_marks check (pass_marks >= 0),
  constraint ck_exam_subjects_duration_min check (duration_min >= 0),
  constraint uq_exam_subjects_tenant_id unique (tenant_id, id)
);
create unique index uq_exam_subjects_tenant_id_examination_id_subject_id on exm.exam_subjects (tenant_id, examination_id, subject_id) where deleted_at is null;
create index ix_exam_subjects_examination_id on exm.exam_subjects (tenant_id, examination_id);
create index ix_exam_subjects_subject_id on exm.exam_subjects (tenant_id, subject_id);
create index ix_exam_subjects_assessment_structure_id on exm.exam_subjects (tenant_id, assessment_structure_id);
comment on table exm.exam_subjects is '[Mongo: EXM02.exam_subjects]';

create table exm.registrations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  examination_id uuid not null,
  student_id uuid not null,
  batch_id uuid,
  status text not null default 'DRAFT',
  submitted_at timestamptz,
  registration_no text,
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_registrations_status check (status in ('DRAFT', 'SUBMITTED', 'LOCKED', 'APPROVED', 'REJECTED', 'CANCELLED')),
  constraint uq_registrations_tenant_id unique (tenant_id, id)
);
create unique index uq_registrations_tenant_id_examination_id_student_id on exm.registrations (tenant_id, examination_id, student_id) where deleted_at is null;
create unique index uq_registrations_tenant_id_registration_no on exm.registrations (tenant_id, registration_no) where deleted_at is null and registration_no is not null;
create index ix_registrations_tenant_id_student_id on exm.registrations (tenant_id, student_id);
create index ix_registrations_examination_id on exm.registrations (tenant_id, examination_id);
create index ix_registrations_batch_id on exm.registrations (tenant_id, batch_id);
create index ix_registrations_workflow_instance_id on exm.registrations (tenant_id, workflow_instance_id);
comment on table exm.registrations is 'Numbering via core.number_sequences (scope_key = EXM_REGISTRATION) [Mongo: EXM01.exam_registrations, EXM01.registration_workflows, EXM01.registration_sequences]';

create table exm.registration_subjects (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  registration_id uuid not null,
  exam_subject_id uuid not null,
  attempt_no smallint not null default 1,
  status text not null default 'REGISTERED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_registration_subjects_status check (status in ('REGISTERED', 'WITHDRAWN')),
  constraint uq_registration_subjects_tenant_id unique (tenant_id, id)
);
create unique index uq_registration_subjects_tenant_id_registration_id_ac5780d2 on exm.registration_subjects (tenant_id, registration_id, exam_subject_id, attempt_no) where deleted_at is null;
create index ix_registration_subjects_registration_id on exm.registration_subjects (tenant_id, registration_id);
create index ix_registration_subjects_exam_subject_id on exm.registration_subjects (tenant_id, exam_subject_id);
comment on table exm.registration_subjects is '[Mongo: EXM01.exam_registration_subjects]';

create table exm.eligibility_snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  registration_id uuid not null,
  rule_ref text,
  result text not null,
  detail jsonb,
  evaluated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_eligibility_snapshots_result check (result in ('ELIGIBLE', 'INELIGIBLE', 'CONDITIONAL')),
  constraint uq_eligibility_snapshots_tenant_id unique (tenant_id, id)
);
create index ix_eligibility_snapshots_registration_id on exm.eligibility_snapshots (tenant_id, registration_id);
comment on table exm.eligibility_snapshots is '[Mongo: EXM01.exam_eligibility_snapshots]';

create table exm.registration_corrections (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  registration_id uuid not null,
  field_name text not null,
  old_value text,
  new_value text,
  reason text,
  approved_by uuid,
  status text not null default 'PENDING',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_registration_corrections_status check (status in ('PENDING', 'APPROVED', 'REJECTED')),
  constraint uq_registration_corrections_tenant_id unique (tenant_id, id)
);
create index ix_registration_corrections_registration_id on exm.registration_corrections (tenant_id, registration_id);
create index ix_registration_corrections_approved_by on exm.registration_corrections (tenant_id, approved_by);
comment on table exm.registration_corrections is '[Mongo: EXM01.exam_registration_corrections]';

create table exm.exam_centres (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  code text not null,
  name text not null,
  address_id uuid,
  capacity integer,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_exam_centres_capacity check (capacity >= 0),
  constraint ck_exam_centres_status check (status in ('ACTIVE', 'INACTIVE')),
  constraint uq_exam_centres_tenant_id unique (tenant_id, id)
);
create unique index uq_exam_centres_tenant_id_code on exm.exam_centres (tenant_id, code) where deleted_at is null;
create index ix_exam_centres_college_id on exm.exam_centres (tenant_id, college_id);
create index ix_exam_centres_address_id on exm.exam_centres (tenant_id, address_id);
comment on table exm.exam_centres is '[Mongo: EXM02.exam_centres]';

create table exm.exam_rooms (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  centre_id uuid not null,
  room_id uuid,
  code text not null,
  capacity integer not null,
  seating_layout jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_exam_rooms_capacity check (capacity >= 0),
  constraint uq_exam_rooms_tenant_id unique (tenant_id, id)
);
create unique index uq_exam_rooms_tenant_id_centre_id_code on exm.exam_rooms (tenant_id, centre_id, code) where deleted_at is null;
create index ix_exam_rooms_centre_id on exm.exam_rooms (tenant_id, centre_id);
create index ix_exam_rooms_room_id on exm.exam_rooms (tenant_id, room_id);
comment on table exm.exam_rooms is '[Mongo: EXM02.exam_rooms]';

create table exm.exam_sessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  examination_id uuid not null,
  exam_subject_id uuid not null,
  centre_id uuid,
  session_date date not null,
  start_time time not null,
  end_time time not null,
  status text not null default 'SCHEDULED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_exam_sessions_status check (status in ('SCHEDULED', 'ONGOING', 'COMPLETED', 'CANCELLED', 'POSTPONED')),
  constraint ck_exam_sessions_x1 check (end_time > start_time),
  constraint uq_exam_sessions_tenant_id unique (tenant_id, id)
);
create index ix_exam_sessions_tenant_id_session_date on exm.exam_sessions (tenant_id, session_date);
create index ix_exam_sessions_tenant_id_exam_subject_id on exm.exam_sessions (tenant_id, exam_subject_id);
create index ix_exam_sessions_examination_id on exm.exam_sessions (tenant_id, examination_id);
create index ix_exam_sessions_centre_id on exm.exam_sessions (tenant_id, centre_id);
comment on table exm.exam_sessions is '[Mongo: EXM02.exam_sessions]';

create table exm.schedule_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  examination_id uuid not null,
  version_no integer not null,
  status text not null default 'DRAFT',
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_schedule_versions_version_no check (version_no > 0),
  constraint ck_schedule_versions_status check (status in ('DRAFT', 'PUBLISHED', 'SUPERSEDED')),
  constraint uq_schedule_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_schedule_versions_tenant_id_examination_id_version_no on exm.schedule_versions (tenant_id, examination_id, version_no) where deleted_at is null;
create index ix_schedule_versions_examination_id on exm.schedule_versions (tenant_id, examination_id);
comment on table exm.schedule_versions is '[Mongo: EXM02.schedule_versions]';

create table exm.schedule_entries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  schedule_version_id uuid not null,
  session_id uuid not null,
  room_id uuid not null,
  seat_from integer,
  seat_to integer,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_schedule_entries_tenant_id unique (tenant_id, id)
);
create index ix_schedule_entries_tenant_id_session_id on exm.schedule_entries (tenant_id, session_id);
create index ix_schedule_entries_tenant_id_room_id on exm.schedule_entries (tenant_id, room_id);
create index ix_schedule_entries_schedule_version_id on exm.schedule_entries (tenant_id, schedule_version_id);
comment on table exm.schedule_entries is '[Mongo: EXM02.schedule_entries]';

create table exm.scheduling_constraints (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  examination_id uuid not null,
  constraint_type text not null,
  params jsonb not null default '{}'::jsonb,
  is_hard boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_scheduling_constraints_tenant_id unique (tenant_id, id)
);
create index ix_scheduling_constraints_examination_id on exm.scheduling_constraints (tenant_id, examination_id);
comment on table exm.scheduling_constraints is '[Mongo: EXM02.scheduling_constraints]';

create table exm.schedule_conflicts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  schedule_version_id uuid not null,
  conflict_type text not null,
  entry_a_id uuid,
  entry_b_id uuid,
  detail jsonb,
  resolved_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_schedule_conflicts_conflict_type check (conflict_type in ('ROOM', 'STUDENT', 'INVIGILATOR', 'CAPACITY')),
  constraint uq_schedule_conflicts_tenant_id unique (tenant_id, id)
);
create index ix_schedule_conflicts_schedule_version_id on exm.schedule_conflicts (tenant_id, schedule_version_id);
create index ix_schedule_conflicts_entry_a_id on exm.schedule_conflicts (tenant_id, entry_a_id);
create index ix_schedule_conflicts_entry_b_id on exm.schedule_conflicts (tenant_id, entry_b_id);
comment on table exm.schedule_conflicts is '[Mongo: EXM02.conflict_logs]';

create table exm.invigilator_assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  room_id uuid,
  employee_id uuid not null,
  role text not null default 'INVIGILATOR',
  status text not null default 'ASSIGNED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_invigilator_assignments_role check (role in ('CHIEF', 'INVIGILATOR', 'SQUAD', 'RELIEVER')),
  constraint ck_invigilator_assignments_status check (status in ('ASSIGNED', 'CONFIRMED', 'DECLINED', 'COMPLETED')),
  constraint uq_invigilator_assignments_tenant_id unique (tenant_id, id)
);
create unique index uq_invigilator_assignments_tenant_id_session_id_ro_605256bb on exm.invigilator_assignments (tenant_id, session_id, room_id, employee_id) where deleted_at is null;
create index ix_invigilator_assignments_session_id on exm.invigilator_assignments (tenant_id, session_id);
create index ix_invigilator_assignments_room_id on exm.invigilator_assignments (tenant_id, room_id);
create index ix_invigilator_assignments_employee_id on exm.invigilator_assignments (tenant_id, employee_id);
comment on table exm.invigilator_assignments is '[Mongo: EXM02.invigilator_assignments]';

create table exm.hall_tickets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  registration_id uuid not null,
  student_id uuid not null,
  template_id uuid,
  document_id uuid,
  status text not null default 'DRAFT',
  generated_at timestamptz,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_hall_tickets_status check (status in ('DRAFT', 'GENERATED', 'PUBLISHED', 'DOWNLOADED', 'PRINTED', 'REVOKED')),
  constraint uq_hall_tickets_tenant_id unique (tenant_id, id)
);
create unique index uq_hall_tickets_tenant_id_registration_id on exm.hall_tickets (tenant_id, registration_id) where deleted_at is null;
create index ix_hall_tickets_registration_id on exm.hall_tickets (tenant_id, registration_id);
create index ix_hall_tickets_student_id on exm.hall_tickets (tenant_id, student_id);
create index ix_hall_tickets_template_id on exm.hall_tickets (tenant_id, template_id);
create index ix_hall_tickets_document_id on exm.hall_tickets (tenant_id, document_id);
comment on table exm.hall_tickets is 'One row per registration; PDF lives in doc.documents. Generation = sys.jobs, printing = doc.print_jobs [Mongo: EXM03.hall_tickets, EXM03.hall_ticket_generation_jobs, EXM03.hall_ticket_documents, EXM03.hall_ticket_templates]';

create table exm.hall_ticket_subjects (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  hall_ticket_id uuid not null,
  exam_subject_id uuid not null,
  session_id uuid,
  seat_no text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_hall_ticket_subjects_tenant_id unique (tenant_id, id)
);
create unique index uq_hall_ticket_subjects_tenant_id_hall_ticket_id_e_9a8dd655 on exm.hall_ticket_subjects (tenant_id, hall_ticket_id, exam_subject_id) where deleted_at is null;
create index ix_hall_ticket_subjects_hall_ticket_id on exm.hall_ticket_subjects (tenant_id, hall_ticket_id);
create index ix_hall_ticket_subjects_exam_subject_id on exm.hall_ticket_subjects (tenant_id, exam_subject_id);
create index ix_hall_ticket_subjects_session_id on exm.hall_ticket_subjects (tenant_id, session_id);
comment on table exm.hall_ticket_subjects is '[Mongo: EXM03.hall_ticket_subjects]';

create table exm.hall_ticket_nocs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  hall_ticket_id uuid not null,
  noc_type text not null,
  status text not null default 'PENDING',
  granted_by uuid,
  reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_hall_ticket_nocs_status check (status in ('PENDING', 'GRANTED', 'DENIED')),
  constraint uq_hall_ticket_nocs_tenant_id unique (tenant_id, id)
);
create index ix_hall_ticket_nocs_hall_ticket_id on exm.hall_ticket_nocs (tenant_id, hall_ticket_id);
create index ix_hall_ticket_nocs_granted_by on exm.hall_ticket_nocs (tenant_id, granted_by);
comment on table exm.hall_ticket_nocs is '[Mongo: EXM03.hall_ticket_noc, EXM03.hall_ticket_eligibility]';

create table exm.valuation_schemes (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  exam_subject_id uuid not null,
  scheme_type text not null default 'SINGLE',
  anonymised boolean not null default true,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_valuation_schemes_scheme_type check (scheme_type in ('SINGLE', 'DOUBLE', 'MODERATED', 'ONLINE')),
  constraint ck_valuation_schemes_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_valuation_schemes_tenant_id unique (tenant_id, id)
);
create index ix_valuation_schemes_exam_subject_id on exm.valuation_schemes (tenant_id, exam_subject_id);
comment on table exm.valuation_schemes is '[Mongo: EXM04.valuation_schemes]';

create table exm.valuation_centres (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  address_id uuid,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_valuation_centres_status check (status in ('ACTIVE', 'INACTIVE')),
  constraint uq_valuation_centres_tenant_id unique (tenant_id, id)
);
create unique index uq_valuation_centres_tenant_id_code on exm.valuation_centres (tenant_id, code) where deleted_at is null;
create index ix_valuation_centres_address_id on exm.valuation_centres (tenant_id, address_id);
comment on table exm.valuation_centres is '[Mongo: EXM07.valuation_centres]';

create table exm.valuation_batches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  exam_subject_id uuid not null,
  centre_id uuid,
  code text not null,
  status text not null default 'OPEN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_valuation_batches_status check (status in ('OPEN', 'IN_PROGRESS', 'COMPLETED', 'LOCKED')),
  constraint uq_valuation_batches_tenant_id unique (tenant_id, id)
);
create unique index uq_valuation_batches_tenant_id_code on exm.valuation_batches (tenant_id, code) where deleted_at is null;
create index ix_valuation_batches_exam_subject_id on exm.valuation_batches (tenant_id, exam_subject_id);
create index ix_valuation_batches_centre_id on exm.valuation_batches (tenant_id, centre_id);
comment on table exm.valuation_batches is '[Mongo: EXM04.valuation_batches]';

create table exm.valuation_assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  valuation_batch_id uuid not null,
  employee_id uuid not null,
  role text not null default 'EVALUATOR',
  status text not null default 'ASSIGNED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_valuation_assignments_role check (role in ('EVALUATOR', 'MODERATOR', 'CHIEF_EXAMINER')),
  constraint ck_valuation_assignments_status check (status in ('ASSIGNED', 'ACCEPTED', 'DECLINED', 'COMPLETED')),
  constraint uq_valuation_assignments_tenant_id unique (tenant_id, id)
);
create unique index uq_valuation_assignments_tenant_id_valuation_batch_b64eb157 on exm.valuation_assignments (tenant_id, valuation_batch_id, employee_id, role) where deleted_at is null;
create index ix_valuation_assignments_valuation_batch_id on exm.valuation_assignments (tenant_id, valuation_batch_id);
create index ix_valuation_assignments_employee_id on exm.valuation_assignments (tenant_id, employee_id);
comment on table exm.valuation_assignments is '[Mongo: EXM04.valuation_assignments]';

create table exm.valuation_sessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  valuation_batch_id uuid not null,
  employee_id uuid not null,
  started_at timestamptz,
  ended_at timestamptz,
  status text not null default 'OPEN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_valuation_sessions_status check (status in ('OPEN', 'SUBMITTED', 'LOCKED')),
  constraint uq_valuation_sessions_tenant_id unique (tenant_id, id)
);
create index ix_valuation_sessions_valuation_batch_id on exm.valuation_sessions (tenant_id, valuation_batch_id);
create index ix_valuation_sessions_employee_id on exm.valuation_sessions (tenant_id, employee_id);
comment on table exm.valuation_sessions is '[Mongo: EXM04.valuation_sessions]';

create table exm.answer_scripts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  exam_session_id uuid not null,
  registration_id uuid not null,
  barcode text not null,
  status text not null default 'ISSUED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_answer_scripts_status check (status in ('ISSUED', 'SCANNED', 'IN_VALUATION', 'VALUED', 'MODERATED', 'ARCHIVED', 'MISSING')),
  constraint uq_answer_scripts_tenant_id unique (tenant_id, id)
);
create unique index uq_answer_scripts_tenant_id_barcode on exm.answer_scripts (tenant_id, barcode) where deleted_at is null;
create index ix_answer_scripts_tenant_id_registration_id on exm.answer_scripts (tenant_id, registration_id);
create index ix_answer_scripts_exam_session_id on exm.answer_scripts (tenant_id, exam_session_id);
comment on table exm.answer_scripts is '[Mongo: EXM04.answer_scripts, EXM07.scripts, EXM07.script_barcodes, EXM07.script_archives]';

create table exm.marks_entries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  answer_script_id uuid not null,
  valuation_session_id uuid not null,
  total_marks numeric(18,2) not null,
  status text not null default 'DRAFT',
  submitted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_marks_entries_total_marks check (total_marks >= 0),
  constraint ck_marks_entries_status check (status in ('DRAFT', 'SUBMITTED', 'MODERATED', 'FINALIZED')),
  constraint uq_marks_entries_tenant_id unique (tenant_id, id)
);
create unique index uq_marks_entries_tenant_id_answer_script_id_valuat_cc734085 on exm.marks_entries (tenant_id, answer_script_id, valuation_session_id) where deleted_at is null;
create index ix_marks_entries_answer_script_id on exm.marks_entries (tenant_id, answer_script_id);
create index ix_marks_entries_valuation_session_id on exm.marks_entries (tenant_id, valuation_session_id);
comment on table exm.marks_entries is '[Mongo: EXM04.marks_entries]';

create table exm.question_marks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  marks_entry_id uuid not null,
  question_no text not null,
  marks numeric(18,2) not null,
  max_marks numeric(18,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_question_marks_marks check (marks >= 0),
  constraint ck_question_marks_max_marks check (max_marks >= 0),
  constraint uq_question_marks_tenant_id unique (tenant_id, id)
);
create unique index uq_question_marks_tenant_id_marks_entry_id_question_no on exm.question_marks (tenant_id, marks_entry_id, question_no) where deleted_at is null;
create index ix_question_marks_marks_entry_id on exm.question_marks (tenant_id, marks_entry_id);
comment on table exm.question_marks is '[Mongo: EXM04.question_marks, EXM04.marks_components]';

create table exm.revaluation_requests (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  registration_id uuid not null,
  exam_subject_id uuid not null,
  request_type text not null default 'REVALUATION',
  fee_paid boolean not null default false,
  status text not null default 'REQUESTED',
  old_marks numeric(18,2),
  new_marks numeric(18,2),
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_revaluation_requests_request_type check (request_type in ('REVALUATION', 'RETOTALLING')),
  constraint ck_revaluation_requests_status check (status in ('REQUESTED', 'IN_PROGRESS', 'COMPLETED', 'REJECTED')),
  constraint uq_revaluation_requests_tenant_id unique (tenant_id, id)
);
create index ix_revaluation_requests_tenant_id_registration_id on exm.revaluation_requests (tenant_id, registration_id);
create index ix_revaluation_requests_exam_subject_id on exm.revaluation_requests (tenant_id, exam_subject_id);
create index ix_revaluation_requests_workflow_instance_id on exm.revaluation_requests (tenant_id, workflow_instance_id);
comment on table exm.revaluation_requests is '[Mongo: EXM04.revaluation_requests, EXM04.retotalling_requests]';

create table exm.marks_corrections (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  marks_entry_id uuid not null,
  old_marks numeric(18,2),
  new_marks numeric(18,2),
  reason text not null,
  approved_by uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_marks_corrections_tenant_id unique (tenant_id, id)
);
create index ix_marks_corrections_marks_entry_id on exm.marks_corrections (tenant_id, marks_entry_id);
create index ix_marks_corrections_approved_by on exm.marks_corrections (tenant_id, approved_by);
comment on table exm.marks_corrections is '[Mongo: EXM04.marks_corrections]';

create table exm.marks_finalizations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  examination_id uuid not null,
  exam_subject_id uuid,
  finalized_by uuid,
  finalized_at timestamptz not null default now(),
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_marks_finalizations_tenant_id unique (tenant_id, id)
);
create index ix_marks_finalizations_examination_id on exm.marks_finalizations (tenant_id, examination_id);
create index ix_marks_finalizations_exam_subject_id on exm.marks_finalizations (tenant_id, exam_subject_id);
create index ix_marks_finalizations_finalized_by on exm.marks_finalizations (tenant_id, finalized_by);
create index ix_marks_finalizations_workflow_instance_id on exm.marks_finalizations (tenant_id, workflow_instance_id);
comment on table exm.marks_finalizations is '[Mongo: EXM05.marks_finalizations]';

create table exm.script_packets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  exam_session_id uuid not null,
  room_id uuid,
  packet_no text not null,
  script_count integer not null,
  seal_no text,
  status text not null default 'SEALED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_script_packets_script_count check (script_count >= 0),
  constraint ck_script_packets_status check (status in ('SEALED', 'DISPATCHED', 'RECEIVED', 'OPENED')),
  constraint uq_script_packets_tenant_id unique (tenant_id, id)
);
create unique index uq_script_packets_tenant_id_packet_no on exm.script_packets (tenant_id, packet_no) where deleted_at is null;
create index ix_script_packets_exam_session_id on exm.script_packets (tenant_id, exam_session_id);
create index ix_script_packets_room_id on exm.script_packets (tenant_id, room_id);
comment on table exm.script_packets is '[Mongo: EXM07.script_packets, EXM07.script_batches, EXM07.script_seals]';

create table exm.script_movements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  packet_id uuid not null,
  answer_script_id uuid,
  movement_type text not null,
  from_location text,
  to_location text,
  quantity integer,
  detail jsonb,
  moved_at timestamptz not null default now(),
  moved_by uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_script_movements_movement_type check (movement_type in ('DISPATCH', 'RECEIVE', 'TRANSFER', 'RETURN', 'SCAN', 'RECONCILE', 'EXCEPTION')),
  constraint uq_script_movements_tenant_id unique (tenant_id, id)
);
create index ix_script_movements_tenant_id_packet_id_moved_at on exm.script_movements (tenant_id, packet_id, moved_at);
create index ix_script_movements_tenant_id_answer_script_id_moved_at on exm.script_movements (tenant_id, answer_script_id, moved_at);
create index ix_script_movements_moved_by on exm.script_movements (tenant_id, moved_by);
comment on table exm.script_movements is 'Chain-of-custody ledger: 7 logistics collections collapse into one append-only event table [Mongo: EXM07.script_movements, EXM07.script_scans, EXM07.script_locations, EXM07.centre_receipts, EXM07.centre_dispatches, EXM07.script_reconciliation, EXM07.script_exceptions]';

create table exm.script_assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  answer_script_id uuid not null,
  valuation_assignment_id uuid not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_script_assignments_tenant_id unique (tenant_id, id)
);
create unique index uq_script_assignments_tenant_id_answer_script_id_v_48042e26 on exm.script_assignments (tenant_id, answer_script_id, valuation_assignment_id);
create index ix_script_assignments_valuation_assignment_id on exm.script_assignments (tenant_id, valuation_assignment_id);
comment on table exm.script_assignments is '[Mongo: EXM07.script_assignments]';

create table exm.result_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  examination_id uuid not null,
  rule_type text not null,
  params jsonb not null default '{}'::jsonb,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_result_rules_rule_type check (rule_type in ('GRADE', 'PASS', 'GRACE', 'MODERATION')),
  constraint uq_result_rules_tenant_id unique (tenant_id, id)
);
create index ix_result_rules_examination_id on exm.result_rules (tenant_id, examination_id);
comment on table exm.result_rules is 'One rules table discriminated by rule_type [Mongo: EXM08.result_rules, EXM08.grade_rules, EXM08.pass_rules, EXM08.grace_rules, EXM08.moderation_rules]';

create table exm.results (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  registration_id uuid not null,
  examination_id uuid not null,
  student_id uuid not null,
  sgpa numeric(18,2),
  cgpa numeric(18,2),
  total_marks numeric(18,2),
  total_max_marks numeric(18,2),
  overall_grade text,
  overall_status text not null,
  grace_marks_applied numeric(18,2),
  workflow_instance_id uuid,
  version_no integer not null default 1,
  status text not null default 'DRAFT',
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_results_overall_status check (overall_status in ('PASS', 'FAIL', 'WITHHELD', 'DETAINED', 'ABSENT')),
  constraint ck_results_grace_marks_applied check (grace_marks_applied >= 0),
  constraint ck_results_status check (status in ('DRAFT', 'PROCESSED', 'APPROVED', 'PUBLISHED', 'AMENDED', 'WITHDRAWN')),
  constraint uq_results_tenant_id unique (tenant_id, id)
);
create unique index uq_results_tenant_id_registration_id on exm.results (tenant_id, registration_id) where deleted_at is null;
create index ix_results_tenant_id_student_id on exm.results (tenant_id, student_id);
create index ix_results_tenant_id_examination_id_overall_status on exm.results (tenant_id, examination_id, overall_status);
create index ix_results_registration_id on exm.results (tenant_id, registration_id);
create index ix_results_workflow_instance_id on exm.results (tenant_id, workflow_instance_id);
comment on table exm.results is 'Current-state result per registration; history in core.entity_versions; amendments = new version_no; processing runs via sys.jobs [Mongo: EXM08.results, EXM08.result_inputs, EXM08.result_snapshots, EXM08.result_processing_jobs, EXM08.result_approvals, EXM08.result_publications, EXM08.result_amendments, EXM08.result_versions]';

create table exm.result_subjects (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  result_id uuid not null,
  exam_subject_id uuid not null,
  marks_obtained numeric(18,2),
  max_marks numeric(18,2),
  grade text,
  grade_points numeric(18,2),
  grace_marks numeric(18,2),
  moderated_grade text,
  status text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_result_subjects_grace_marks check (grace_marks >= 0),
  constraint ck_result_subjects_status check (status in ('PASS', 'FAIL', 'ABSENT', 'WITHHELD')),
  constraint uq_result_subjects_tenant_id unique (tenant_id, id)
);
create unique index uq_result_subjects_tenant_id_result_id_exam_subject_id on exm.result_subjects (tenant_id, result_id, exam_subject_id) where deleted_at is null;
create index ix_result_subjects_result_id on exm.result_subjects (tenant_id, result_id);
create index ix_result_subjects_exam_subject_id on exm.result_subjects (tenant_id, exam_subject_id);
comment on table exm.result_subjects is '[Mongo: EXM08.result_subjects, EXM08.result_grace, EXM08.result_moderation]';

create table exm.result_semesters (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  result_id uuid not null,
  term_id uuid not null,
  sgpa numeric(18,2),
  credits_earned numeric(18,2),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_result_semesters_tenant_id unique (tenant_id, id)
);
create unique index uq_result_semesters_tenant_id_result_id_term_id on exm.result_semesters (tenant_id, result_id, term_id) where deleted_at is null;
create index ix_result_semesters_result_id on exm.result_semesters (tenant_id, result_id);
create index ix_result_semesters_term_id on exm.result_semesters (tenant_id, term_id);
comment on table exm.result_semesters is '[Mongo: EXM08.result_semesters]';

create table exm.result_backlogs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  subject_id uuid not null,
  first_result_id uuid,
  cleared_result_id uuid,
  status text not null default 'PENDING',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_result_backlogs_status check (status in ('PENDING', 'CLEARED')),
  constraint uq_result_backlogs_tenant_id unique (tenant_id, id)
);
create unique index uq_result_backlogs_tenant_id_student_id_subject_id on exm.result_backlogs (tenant_id, student_id, subject_id) where deleted_at is null;
create index ix_result_backlogs_student_id on exm.result_backlogs (tenant_id, student_id);
create index ix_result_backlogs_subject_id on exm.result_backlogs (tenant_id, subject_id);
create index ix_result_backlogs_first_result_id on exm.result_backlogs (tenant_id, first_result_id);
create index ix_result_backlogs_cleared_result_id on exm.result_backlogs (tenant_id, cleared_result_id);
comment on table exm.result_backlogs is '[Mongo: EXM08.result_backlogs]';


-- ============================================================ fin

create table fin.ledger_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  account_type text not null,
  parent_account_id uuid,
  is_postable boolean not null default true,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_ledger_accounts_account_type check (account_type in ('ASSET', 'LIABILITY', 'EQUITY', 'INCOME', 'EXPENSE')),
  constraint uq_ledger_accounts_tenant_id unique (tenant_id, id)
);
create unique index uq_ledger_accounts_tenant_id_code on fin.ledger_accounts (tenant_id, code) where deleted_at is null;
create index ix_ledger_accounts_parent_account_id on fin.ledger_accounts (tenant_id, parent_account_id);
comment on table fin.ledger_accounts is 'NEW: chart of accounts. Needed as the source of truth behind the *_fact_daily reporting projections [Mongo: FIN08.ledger_fact_daily]';

create table fin.journal_entries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  entry_no text not null,
  entry_date date not null,
  source_type text,
  source_id uuid,
  narration text,
  status text not null default 'POSTED',
  reverses_entry_id uuid,
  posted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_journal_entries_status check (status in ('DRAFT', 'POSTED', 'REVERSED')),
  constraint uq_journal_entries_tenant_id unique (tenant_id, id)
);
create unique index uq_journal_entries_tenant_id_entry_no on fin.journal_entries (tenant_id, entry_no) where deleted_at is null;
create index ix_journal_entries_tenant_id_source_type_source_id on fin.journal_entries (tenant_id, source_type, source_id);
create index ix_journal_entries_tenant_id_entry_date on fin.journal_entries (tenant_id, entry_date);
create index ix_journal_entries_college_id on fin.journal_entries (tenant_id, college_id);
create index ix_journal_entries_reverses_entry_id on fin.journal_entries (tenant_id, reverses_entry_id);
comment on table fin.journal_entries is 'NEW: double-entry header. Lines must balance (enforced by deferred constraint trigger)';

create table fin.journal_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  entry_id uuid not null,
  account_id uuid not null,
  department_id uuid,
  cost_centre_id uuid,
  debit numeric(18,2) not null default 0,
  credit numeric(18,2) not null default 0,
  memo text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_journal_lines_debit check (debit >= 0),
  constraint ck_journal_lines_credit check (credit >= 0),
  constraint ck_journal_lines_x1 check ((debit = 0) <> (credit = 0)),
  constraint uq_journal_lines_tenant_id unique (tenant_id, id)
);
create index ix_journal_lines_tenant_id_account_id on fin.journal_lines (tenant_id, account_id);
create index ix_journal_lines_tenant_id_entry_id on fin.journal_lines (tenant_id, entry_id);
create index ix_journal_lines_department_id on fin.journal_lines (tenant_id, department_id);
create index ix_journal_lines_cost_centre_id on fin.journal_lines (tenant_id, cost_centre_id);

create table fin.fee_periods (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  academic_year_id uuid not null,
  term_id uuid,
  name text not null,
  start_date date,
  due_date date,
  end_date date,
  status text not null default 'OPEN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_periods_status check (status in ('PLANNED', 'OPEN', 'CLOSED')),
  constraint uq_fee_periods_tenant_id unique (tenant_id, id)
);
create index ix_fee_periods_academic_year_id on fin.fee_periods (tenant_id, academic_year_id);
create index ix_fee_periods_term_id on fin.fee_periods (tenant_id, term_id);
comment on table fin.fee_periods is '[Mongo: FIN01.academic_fee_periods]';

create table fin.fee_structures (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid not null,
  code text not null,
  name text not null,
  academic_year_id uuid,
  currency_code char(3) not null default 'INR',
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  current_version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_structures_status check (status in ('DRAFT', 'PENDING_APPROVAL', 'ACTIVE', 'SUPERSEDED', 'RETIRED')),
  constraint uq_fee_structures_tenant_id unique (tenant_id, id)
);
create unique index uq_fee_structures_tenant_id_college_id_code on fin.fee_structures (tenant_id, college_id, code) where deleted_at is null;
create index ix_fee_structures_college_id on fin.fee_structures (tenant_id, college_id);
create index ix_fee_structures_academic_year_id on fin.fee_structures (tenant_id, academic_year_id);
create index ix_fee_structures_workflow_instance_id on fin.fee_structures (tenant_id, workflow_instance_id);
comment on table fin.fee_structures is '[Mongo: FIN01.fee_structures, FIN01.fee_structure_approvals]';

create table fin.fee_structure_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_id uuid not null,
  version_no integer not null,
  effective_from date not null,
  effective_to date,
  total_amount numeric(18,2),
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_structure_versions_version_no check (version_no > 0),
  constraint ck_fee_structure_versions_total_amount check (total_amount >= 0),
  constraint ck_fee_structure_versions_status check (status in ('DRAFT', 'APPROVED', 'ACTIVE', 'SUPERSEDED')),
  constraint ck_fee_structure_versions_x1 check (effective_to is null or effective_to >= effective_from),
  constraint uq_fee_structure_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_fee_structure_versions_tenant_id_structure_id_version_no on fin.fee_structure_versions (tenant_id, structure_id, version_no) where deleted_at is null;
create index ix_fee_structure_versions_structure_id on fin.fee_structure_versions (tenant_id, structure_id);
comment on table fin.fee_structure_versions is '[Mongo: FIN01.fee_versions]';

create table fin.fee_components (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_version_id uuid not null,
  code text not null,
  name text not null,
  component_type text not null default 'TUITION',
  amount numeric(18,2) not null,
  is_refundable boolean not null default false,
  is_taxable boolean not null default false,
  tax_code_id uuid,
  ledger_account_id uuid,
  sort_order integer not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_components_component_type check (component_type in ('TUITION', 'HOSTEL', 'TRANSPORT', 'EXAM', 'LIBRARY', 'LAB', 'DEVELOPMENT', 'DEPOSIT', 'FINE', 'OTHER')),
  constraint ck_fee_components_amount check (amount >= 0),
  constraint uq_fee_components_tenant_id unique (tenant_id, id)
);
create unique index uq_fee_components_tenant_id_structure_version_id_code on fin.fee_components (tenant_id, structure_version_id, code) where deleted_at is null;
create index ix_fee_components_structure_version_id on fin.fee_components (tenant_id, structure_version_id);
create index ix_fee_components_tax_code_id on fin.fee_components (tenant_id, tax_code_id);
create index ix_fee_components_ledger_account_id on fin.fee_components (tenant_id, ledger_account_id);
comment on table fin.fee_components is '[Mongo: FIN01.fee_components]';

create table fin.fee_installments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_version_id uuid not null,
  installment_no smallint not null,
  fee_period_id uuid,
  due_date date not null,
  percentage numeric(9,4),
  amount numeric(18,2),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_installments_installment_no check (installment_no > 0),
  constraint ck_fee_installments_percentage check (percentage between 0 and 100),
  constraint ck_fee_installments_amount check (amount >= 0),
  constraint uq_fee_installments_tenant_id unique (tenant_id, id)
);
create unique index uq_fee_installments_tenant_id_structure_version_id_3ea91859 on fin.fee_installments (tenant_id, structure_version_id, installment_no) where deleted_at is null;
create index ix_fee_installments_structure_version_id on fin.fee_installments (tenant_id, structure_version_id);
create index ix_fee_installments_fee_period_id on fin.fee_installments (tenant_id, fee_period_id);
comment on table fin.fee_installments is '[Mongo: FIN01.fee_installments]';

create table fin.fee_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_version_id uuid not null,
  rule_type text not null,
  params jsonb not null default '{}'::jsonb,
  priority integer not null default 100,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_rules_rule_type check (rule_type in ('LATE_FEE', 'EARLY_DISCOUNT', 'GRACE', 'INSTALLMENT', 'WAIVER')),
  constraint uq_fee_rules_tenant_id unique (tenant_id, id)
);
create index ix_fee_rules_structure_version_id on fin.fee_rules (tenant_id, structure_version_id);
comment on table fin.fee_rules is '[Mongo: FIN01.fee_rules]';

create table fin.fee_structure_programs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_id uuid not null,
  program_id uuid not null,
  batch_id uuid,
  term_no smallint,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_fee_structure_programs_tenant_id unique (tenant_id, id)
);
create unique index uq_fee_structure_programs_tenant_id_structure_id_p_7dbbc115 on fin.fee_structure_programs (tenant_id, structure_id, program_id, batch_id, term_no);
create index ix_fee_structure_programs_program_id on fin.fee_structure_programs (tenant_id, program_id);
create index ix_fee_structure_programs_batch_id on fin.fee_structure_programs (tenant_id, batch_id);
comment on table fin.fee_structure_programs is '[Mongo: FIN01.program_fee_mappings]';

create table fin.concession_types (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  basis text not null default 'PERCENT',
  default_value numeric(18,2),
  requires_approval boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_concession_types_basis check (basis in ('PERCENT', 'AMOUNT')),
  constraint uq_concession_types_tenant_id unique (tenant_id, id)
);
create unique index uq_concession_types_tenant_id_code on fin.concession_types (tenant_id, code) where deleted_at is null;
comment on table fin.concession_types is 'NEW: master for fee concessions (normalised out of fee_concessions)';

create table fin.fee_concessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  concession_type_id uuid not null,
  structure_version_id uuid,
  component_code text,
  basis text not null,
  value numeric(18,2) not null,
  valid_from date,
  valid_to date,
  reason text,
  status text not null default 'PENDING',
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_concessions_basis check (basis in ('PERCENT', 'AMOUNT')),
  constraint ck_fee_concessions_value check (value >= 0),
  constraint ck_fee_concessions_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'EXPIRED')),
  constraint uq_fee_concessions_tenant_id unique (tenant_id, id)
);
create index ix_fee_concessions_tenant_id_student_id_status on fin.fee_concessions (tenant_id, student_id, status);
create index ix_fee_concessions_concession_type_id on fin.fee_concessions (tenant_id, concession_type_id);
create index ix_fee_concessions_structure_version_id on fin.fee_concessions (tenant_id, structure_version_id);
create index ix_fee_concessions_workflow_instance_id on fin.fee_concessions (tenant_id, workflow_instance_id);
comment on table fin.fee_concessions is '[Mongo: FIN01.fee_concessions]';

create table fin.fee_obligations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  structure_version_id uuid,
  fee_period_id uuid,
  installment_id uuid,
  obligation_no text not null,
  due_date date,
  gross_amount numeric(18,2) not null default 0,
  concession_amount numeric(18,2) not null default 0,
  scholarship_amount numeric(18,2) not null default 0,
  tax_amount numeric(18,2) not null default 0,
  net_amount numeric(18,2) not null default 0,
  paid_amount numeric(18,2) not null default 0,
  status text not null default 'OPEN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_obligations_gross_amount check (gross_amount >= 0),
  constraint ck_fee_obligations_concession_amount check (concession_amount >= 0),
  constraint ck_fee_obligations_scholarship_amount check (scholarship_amount >= 0),
  constraint ck_fee_obligations_tax_amount check (tax_amount >= 0),
  constraint ck_fee_obligations_net_amount check (net_amount >= 0),
  constraint ck_fee_obligations_paid_amount check (paid_amount >= 0),
  constraint ck_fee_obligations_status check (status in ('OPEN', 'PARTIALLY_PAID', 'PAID', 'OVERDUE', 'WAIVED', 'CANCELLED')),
  constraint ck_fee_obligations_x1 check (paid_amount <= net_amount + 0.005),
  constraint uq_fee_obligations_tenant_id unique (tenant_id, id)
);
create unique index uq_fee_obligations_tenant_id_obligation_no on fin.fee_obligations (tenant_id, obligation_no) where deleted_at is null;
create index ix_fee_obligations_tenant_id_student_id_status on fin.fee_obligations (tenant_id, student_id, status);
create index ix_fee_obligations_tenant_id_due_date_status on fin.fee_obligations (tenant_id, due_date, status);
create index ix_fee_obligations_structure_version_id on fin.fee_obligations (tenant_id, structure_version_id);
create index ix_fee_obligations_fee_period_id on fin.fee_obligations (tenant_id, fee_period_id);
create index ix_fee_obligations_installment_id on fin.fee_obligations (tenant_id, installment_id);
comment on table fin.fee_obligations is 'Amounts are denormalised roll-ups maintained by triggers on fin.payment_allocations / scholarship utilisations; balance = net_amount - paid_amount [Mongo: FIN02.fee_obligations]';

create table fin.fee_obligation_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  obligation_id uuid not null,
  fee_component_id uuid,
  description text not null,
  amount numeric(18,2) not null,
  tax_amount numeric(18,2) not null default 0,
  paid_amount numeric(18,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_fee_obligation_items_amount check (amount >= 0),
  constraint ck_fee_obligation_items_tax_amount check (tax_amount >= 0),
  constraint ck_fee_obligation_items_paid_amount check (paid_amount >= 0),
  constraint uq_fee_obligation_items_tenant_id unique (tenant_id, id)
);
create index ix_fee_obligation_items_obligation_id on fin.fee_obligation_items (tenant_id, obligation_id);
create index ix_fee_obligation_items_fee_component_id on fin.fee_obligation_items (tenant_id, fee_component_id);
comment on table fin.fee_obligation_items is '[Mongo: FIN02.fee_obligation_items]';

create table fin.payment_methods (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  method_type text not null,
  gateway_provider text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payment_methods_method_type check (method_type in ('CASH', 'CHEQUE', 'DD', 'UPI', 'CARD', 'NETBANKING', 'WALLET', 'BANK_TRANSFER')),
  constraint uq_payment_methods_tenant_id unique (tenant_id, id)
);
create unique index uq_payment_methods_tenant_id_code on fin.payment_methods (tenant_id, code) where deleted_at is null;
comment on table fin.payment_methods is '[Mongo: FIN02.payment_methods]';

create table fin.collection_batches (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  batch_no text not null,
  counter_user_id uuid,
  collection_date date not null,
  total_amount numeric(18,2) not null default 0,
  status text not null default 'OPEN',
  closed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_collection_batches_status check (status in ('OPEN', 'CLOSED', 'RECONCILED')),
  constraint uq_collection_batches_tenant_id unique (tenant_id, id)
);
create unique index uq_collection_batches_tenant_id_batch_no on fin.collection_batches (tenant_id, batch_no) where deleted_at is null;
create index ix_collection_batches_college_id on fin.collection_batches (tenant_id, college_id);
create index ix_collection_batches_counter_user_id on fin.collection_batches (tenant_id, counter_user_id);
comment on table fin.collection_batches is '[Mongo: FIN02.collection_batches]';

create table fin.payments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  student_id uuid not null,
  payer_person_id uuid,
  payment_method_id uuid,
  collection_batch_id uuid,
  payment_no text not null,
  amount numeric(18,2) not null,
  currency_code char(3) not null default 'INR',
  paid_on timestamptz,
  reference_no text,
  instrument_no text,
  instrument_date date,
  status text not null default 'INITIATED',
  idempotency_key text,
  settlement_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payments_amount check (amount > 0),
  constraint ck_payments_status check (status in ('INITIATED', 'PENDING', 'SUCCESS', 'FAILED', 'CANCELLED', 'REFUNDED', 'PARTIALLY_REFUNDED')),
  constraint uq_payments_tenant_id unique (tenant_id, id)
);
create unique index uq_payments_tenant_id_payment_no on fin.payments (tenant_id, payment_no) where deleted_at is null;
create unique index uq_payments_tenant_id_idempotency_key on fin.payments (tenant_id, idempotency_key) where deleted_at is null and idempotency_key is not null;
create index ix_payments_tenant_id_student_id_status on fin.payments (tenant_id, student_id, status);
create index ix_payments_tenant_id_paid_on on fin.payments (tenant_id, paid_on);
create index ix_payments_payer_person_id on fin.payments (tenant_id, payer_person_id);
create index ix_payments_payment_method_id on fin.payments (tenant_id, payment_method_id);
create index ix_payments_collection_batch_id on fin.payments (tenant_id, collection_batch_id);
comment on table fin.payments is 'Payment intent and transaction are ONE row moving through status (was two collections) [Mongo: FIN02.payment_intents, FIN02.payment_transactions]';

create table fin.payment_allocations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payment_id uuid not null,
  obligation_id uuid not null,
  obligation_item_id uuid,
  amount numeric(18,2) not null,
  reverses_allocation_id uuid,
  reason text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_payment_allocations_x1 check (amount <> 0),
  constraint ck_payment_allocations_x2 check ((reverses_allocation_id is null and amount > 0) or (reverses_allocation_id is not null and amount < 0)),
  constraint uq_payment_allocations_tenant_id unique (tenant_id, id)
);
create unique index uq_payment_allocations_tenant_id_reverses_allocation_id on fin.payment_allocations (tenant_id, reverses_allocation_id) where reverses_allocation_id is not null;
create index ix_payment_allocations_tenant_id_obligation_id on fin.payment_allocations (tenant_id, obligation_id);
create index ix_payment_allocations_tenant_id_payment_id on fin.payment_allocations (tenant_id, payment_id);
create index ix_payment_allocations_obligation_item_id on fin.payment_allocations (tenant_id, obligation_item_id);
create index ix_payment_allocations_reverses_allocation_id on fin.payment_allocations (tenant_id, reverses_allocation_id);
comment on table fin.payment_allocations is 'APPEND-ONLY ledger. Fix a mistake by reversing (negative mirror row via fin.reverse_allocation) and allocating again. Triggers enforce: same student, payment has funds left, obligation not over-paid; fee_obligations.paid_amount is derived from this table [Mongo: FIN02.payment_allocations]';

create table fin.gateway_transactions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payment_id uuid,
  gateway text not null,
  gateway_order_id text,
  gateway_payment_id text,
  amount numeric(18,2) not null,
  status text not null default 'CREATED',
  request_payload jsonb,
  response_payload jsonb,
  signature_verified boolean,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_gateway_transactions_amount check (amount >= 0),
  constraint ck_gateway_transactions_status check (status in ('CREATED', 'AUTHORIZED', 'CAPTURED', 'FAILED', 'REFUNDED')),
  constraint uq_gateway_transactions_tenant_id unique (tenant_id, id)
);
create unique index uq_gateway_transactions_tenant_id_gateway_gateway_payment_id on fin.gateway_transactions (tenant_id, gateway, gateway_payment_id) where deleted_at is null and gateway_payment_id is not null;
create index ix_gateway_transactions_tenant_id_payment_id on fin.gateway_transactions (tenant_id, payment_id);
comment on table fin.gateway_transactions is '[Mongo: FIN02.payment_gateway_transactions]';

create table fin.gateway_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  gateway text not null,
  event_id text not null,
  event_type text not null,
  payload jsonb not null default '{}'::jsonb,
  signature_valid boolean,
  processed_at timestamptz,
  status text not null default 'RECEIVED',
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_gateway_events_status check (status in ('RECEIVED', 'PROCESSED', 'IGNORED', 'FAILED')),
  constraint uq_gateway_events_tenant_id unique (tenant_id, id)
);
create index ix_gateway_events_tenant_id_gateway_event_id on fin.gateway_events (tenant_id, gateway, event_id);
comment on table fin.gateway_events is 'Raw webhooks; receive via Edge Function using service_role [Mongo: FIN02.payment_webhook_events]';

create table fin.refunds (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payment_id uuid not null,
  refund_no text not null,
  amount numeric(18,2) not null,
  reason text not null,
  status text not null default 'REQUESTED',
  workflow_instance_id uuid,
  processed_at timestamptz,
  payment_request_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_refunds_amount check (amount > 0),
  constraint ck_refunds_status check (status in ('REQUESTED', 'APPROVED', 'PROCESSED', 'REJECTED', 'FAILED')),
  constraint uq_refunds_tenant_id unique (tenant_id, id)
);
create unique index uq_refunds_tenant_id_refund_no on fin.refunds (tenant_id, refund_no) where deleted_at is null;
create index ix_refunds_payment_id on fin.refunds (tenant_id, payment_id);
create index ix_refunds_workflow_instance_id on fin.refunds (tenant_id, workflow_instance_id);
create index ix_refunds_payment_request_id on fin.refunds (tenant_id, payment_request_id);
comment on table fin.refunds is '[Mongo: FIN02.refunds]';

create table fin.settlements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  gateway text not null,
  settlement_ref text not null,
  settled_on date,
  gross_amount numeric(18,2),
  fees_amount numeric(18,2),
  net_amount numeric(18,2),
  status text not null default 'PENDING',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_settlements_status check (status in ('PENDING', 'RECEIVED', 'RECONCILED')),
  constraint uq_settlements_tenant_id unique (tenant_id, id)
);
create unique index uq_settlements_tenant_id_gateway_settlement_ref on fin.settlements (tenant_id, gateway, settlement_ref) where deleted_at is null;
comment on table fin.settlements is '[Mongo: FIN02.payment_settlements]';

create table fin.reconciliations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  recon_type text not null,
  source_ref text,
  settlement_id uuid,
  payment_id uuid,
  expected_amount numeric(18,2),
  actual_amount numeric(18,2),
  variance numeric(18,2),
  status text not null default 'UNMATCHED',
  reconciled_by uuid,
  reconciled_at timestamptz,
  entity_type text,
  entity_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_reconciliations_recon_type check (recon_type in ('PAYMENT', 'BANK', 'GATEWAY', 'TAX', 'PAYROLL')),
  constraint ck_reconciliations_status check (status in ('MATCHED', 'UNMATCHED', 'DISPUTED', 'ADJUSTED')),
  constraint uq_reconciliations_tenant_id unique (tenant_id, id)
);
create index ix_reconciliations_tenant_id_recon_type_status on fin.reconciliations (tenant_id, recon_type, status);
create index ix_reconciliations_settlement_id on fin.reconciliations (tenant_id, settlement_id);
create index ix_reconciliations_payment_id on fin.reconciliations (tenant_id, payment_id);
create index ix_reconciliations_reconciled_by on fin.reconciliations (tenant_id, reconciled_by);
comment on table fin.reconciliations is 'ONE reconciliation table for payments, gateway settlements, tax and payroll [Mongo: FIN02.reconciliation_records, FIN04.tax_reconciliations, FIN07.payroll_reconciliations]';

create table fin.adjustments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  entity_type text not null,
  entity_id uuid not null,
  adjustment_type text not null,
  amount numeric(18,2) not null,
  reason text not null,
  status text not null default 'PENDING',
  workflow_instance_id uuid,
  journal_entry_id uuid,
  applied_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_adjustments_entity_type check (entity_type in ('RECEIPT', 'COLLECTION', 'OBLIGATION', 'EXPENSE', 'SCHOLARSHIP', 'TAX', 'PAYMENT')),
  constraint ck_adjustments_adjustment_type check (adjustment_type in ('WRITE_OFF', 'CORRECTION', 'DISCOUNT', 'PENALTY', 'REVERSAL', 'WAIVER', 'OTHER')),
  constraint ck_adjustments_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
  constraint uq_adjustments_tenant_id unique (tenant_id, id)
);
create index ix_adjustments_tenant_id_entity_type_entity_id on fin.adjustments (tenant_id, entity_type, entity_id);
create index ix_adjustments_workflow_instance_id on fin.adjustments (tenant_id, workflow_instance_id);
create index ix_adjustments_journal_entry_id on fin.adjustments (tenant_id, journal_entry_id);
comment on table fin.adjustments is 'One financial adjustment record for every domain (was 5 collections) [Mongo: FIN02.collection_adjustments, FIN03.expense_adjustments, FIN04.tax_adjustments, FIN05.receipt_adjustments, FIN06.scholarship_adjustments]';

create table fin.receipts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payment_id uuid,
  student_id uuid,
  payer_person_id uuid,
  receipt_no text not null,
  receipt_date date not null,
  total_amount numeric(18,2) not null,
  tax_amount numeric(18,2) not null default 0,
  status text not null default 'ISSUED',
  document_id uuid,
  template_id uuid,
  verification_code text,
  supersedes_receipt_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_receipts_total_amount check (total_amount >= 0),
  constraint ck_receipts_tax_amount check (tax_amount >= 0),
  constraint ck_receipts_status check (status in ('DRAFT', 'ISSUED', 'CANCELLED', 'REISSUED')),
  constraint uq_receipts_tenant_id unique (tenant_id, id)
);
create unique index uq_receipts_tenant_id_receipt_no on fin.receipts (tenant_id, receipt_no) where deleted_at is null;
create unique index uq_receipts_tenant_id_verification_code on fin.receipts (tenant_id, verification_code) where deleted_at is null and verification_code is not null;
create index ix_receipts_tenant_id_student_id on fin.receipts (tenant_id, student_id);
create index ix_receipts_tenant_id_payment_id on fin.receipts (tenant_id, payment_id);
create index ix_receipts_payer_person_id on fin.receipts (tenant_id, payer_person_id);
create index ix_receipts_document_id on fin.receipts (tenant_id, document_id);
create index ix_receipts_template_id on fin.receipts (tenant_id, template_id);
create index ix_receipts_supersedes_receipt_id on fin.receipts (tenant_id, supersedes_receipt_id);
comment on table fin.receipts is 'Numbering via core.number_sequences; PDF in doc.documents; verify via doc.verifications; reissue/cancel via doc.lifecycle_requests [Mongo: FIN05.receipts, FIN05.receipt_documents, FIN05.receipt_templates, FIN05.receipt_generation_jobs]';

create table fin.receipt_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  receipt_id uuid not null,
  obligation_item_id uuid,
  description text not null,
  amount numeric(18,2) not null,
  tax_amount numeric(18,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_receipt_items_amount check (amount >= 0),
  constraint ck_receipt_items_tax_amount check (tax_amount >= 0),
  constraint uq_receipt_items_tenant_id unique (tenant_id, id)
);
create index ix_receipt_items_receipt_id on fin.receipt_items (tenant_id, receipt_id);
create index ix_receipt_items_obligation_item_id on fin.receipt_items (tenant_id, obligation_item_id);
comment on table fin.receipt_items is '[Mongo: FIN05.receipt_items]';

create table fin.scholarship_schemes (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  code text not null,
  name text not null,
  sponsor_name text,
  sponsor_type text,
  academic_year_id uuid,
  total_budget numeric(18,2),
  status text not null default 'DRAFT',
  current_version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scholarship_schemes_sponsor_type check (sponsor_type in ('GOVERNMENT', 'INSTITUTIONAL', 'CORPORATE', 'TRUST', 'INDIVIDUAL')),
  constraint ck_scholarship_schemes_total_budget check (total_budget >= 0),
  constraint ck_scholarship_schemes_status check (status in ('DRAFT', 'ACTIVE', 'CLOSED', 'RETIRED')),
  constraint uq_scholarship_schemes_tenant_id unique (tenant_id, id)
);
create unique index uq_scholarship_schemes_tenant_id_code on fin.scholarship_schemes (tenant_id, code) where deleted_at is null;
create index ix_scholarship_schemes_college_id on fin.scholarship_schemes (tenant_id, college_id);
create index ix_scholarship_schemes_academic_year_id on fin.scholarship_schemes (tenant_id, academic_year_id);
comment on table fin.scholarship_schemes is 'Program (sponsor umbrella) and scheme merged; versions in core.entity_versions [Mongo: FIN06.scholarship_programs, FIN06.scholarship_schemes]';

create table fin.scholarship_criteria (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scheme_id uuid not null,
  criterion_type text not null,
  operator text not null,
  value jsonb not null default '{}'::jsonb,
  is_mandatory boolean not null default true,
  weight numeric(9,4),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scholarship_criteria_criterion_type check (criterion_type in ('INCOME', 'MERIT', 'CATEGORY', 'ATTENDANCE', 'PROGRAM', 'GENDER', 'CUSTOM')),
  constraint uq_scholarship_criteria_tenant_id unique (tenant_id, id)
);
create index ix_scholarship_criteria_scheme_id on fin.scholarship_criteria (tenant_id, scheme_id);
comment on table fin.scholarship_criteria is '[Mongo: FIN06.scholarship_criteria]';

create table fin.scholarship_benefits (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scheme_id uuid not null,
  benefit_type text not null,
  basis text not null,
  value numeric(18,2) not null,
  max_amount numeric(18,2),
  fee_component_type text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scholarship_benefits_benefit_type check (benefit_type in ('TUITION_WAIVER', 'STIPEND', 'BOOK_ALLOWANCE', 'HOSTEL', 'TRANSPORT', 'OTHER')),
  constraint ck_scholarship_benefits_basis check (basis in ('PERCENT', 'AMOUNT')),
  constraint ck_scholarship_benefits_value check (value >= 0),
  constraint ck_scholarship_benefits_max_amount check (max_amount >= 0),
  constraint uq_scholarship_benefits_tenant_id unique (tenant_id, id)
);
create index ix_scholarship_benefits_scheme_id on fin.scholarship_benefits (tenant_id, scheme_id);
comment on table fin.scholarship_benefits is '[Mongo: FIN06.scholarship_benefits]';

create table fin.scholarship_application_periods (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scheme_id uuid not null,
  academic_year_id uuid,
  open_from timestamptz not null,
  open_to timestamptz not null,
  status text not null default 'OPEN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scholarship_application_periods_status check (status in ('PLANNED', 'OPEN', 'CLOSED')),
  constraint ck_scholarship_application_periods_x1 check (open_to > open_from),
  constraint uq_scholarship_application_periods_tenant_id unique (tenant_id, id)
);
create index ix_scholarship_application_periods_scheme_id on fin.scholarship_application_periods (tenant_id, scheme_id);
create index ix_scholarship_application_periods_academic_year_id on fin.scholarship_application_periods (tenant_id, academic_year_id);
comment on table fin.scholarship_application_periods is '[Mongo: FIN06.scholarship_application_periods]';

create table fin.scholarship_applications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scheme_id uuid not null,
  period_id uuid,
  student_id uuid not null,
  application_no text not null,
  status text not null default 'DRAFT',
  submitted_at timestamptz,
  workflow_instance_id uuid,
  declared_data jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scholarship_applications_status check (status in ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW', 'SHORTLISTED', 'APPROVED', 'REJECTED', 'WITHDRAWN')),
  constraint uq_scholarship_applications_tenant_id unique (tenant_id, id)
);
create unique index uq_scholarship_applications_tenant_id_application_no on fin.scholarship_applications (tenant_id, application_no) where deleted_at is null;
create unique index uq_scholarship_applications_tenant_id_scheme_id_pe_b8a075b4 on fin.scholarship_applications (tenant_id, scheme_id, period_id, student_id) where deleted_at is null;
create index ix_scholarship_applications_tenant_id_student_id_status on fin.scholarship_applications (tenant_id, student_id, status);
create index ix_scholarship_applications_scheme_id on fin.scholarship_applications (tenant_id, scheme_id);
create index ix_scholarship_applications_period_id on fin.scholarship_applications (tenant_id, period_id);
create index ix_scholarship_applications_workflow_instance_id on fin.scholarship_applications (tenant_id, workflow_instance_id);
comment on table fin.scholarship_applications is 'Application documents via doc.document_links; approvals via wf [Mongo: FIN06.scholarship_applications, FIN06.scholarship_application_documents, FIN06.scholarship_approvals]';

create table fin.scholarship_evaluations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  application_id uuid not null,
  evaluation_type text not null default 'ELIGIBILITY',
  result text,
  score numeric(18,2),
  rank_no integer,
  detail jsonb,
  evaluated_by uuid,
  evaluated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_scholarship_evaluations_evaluation_type check (evaluation_type in ('ELIGIBILITY', 'MERIT', 'COMMITTEE')),
  constraint uq_scholarship_evaluations_tenant_id unique (tenant_id, id)
);
create index ix_scholarship_evaluations_application_id on fin.scholarship_evaluations (tenant_id, application_id);
create index ix_scholarship_evaluations_evaluated_by on fin.scholarship_evaluations (tenant_id, evaluated_by);
comment on table fin.scholarship_evaluations is '[Mongo: FIN06.eligibility_evaluations, FIN06.scholarship_evaluations, FIN06.scholarship_rankings]';

create table fin.scholarship_awards (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  application_id uuid,
  scheme_id uuid not null,
  student_id uuid not null,
  award_no text not null,
  valid_from date,
  valid_to date,
  total_amount numeric(18,2) not null,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scholarship_awards_total_amount check (total_amount >= 0),
  constraint ck_scholarship_awards_status check (status in ('ACTIVE', 'SUSPENDED', 'CANCELLED', 'COMPLETED', 'EXPIRED')),
  constraint uq_scholarship_awards_tenant_id unique (tenant_id, id)
);
create unique index uq_scholarship_awards_tenant_id_award_no on fin.scholarship_awards (tenant_id, award_no) where deleted_at is null;
create index ix_scholarship_awards_tenant_id_student_id_status on fin.scholarship_awards (tenant_id, student_id, status);
create index ix_scholarship_awards_application_id on fin.scholarship_awards (tenant_id, application_id);
create index ix_scholarship_awards_scheme_id on fin.scholarship_awards (tenant_id, scheme_id);
comment on table fin.scholarship_awards is '[Mongo: FIN06.scholarship_awards]';

create table fin.scholarship_award_components (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  award_id uuid not null,
  benefit_id uuid,
  description text,
  amount numeric(18,2) not null,
  utilized_amount numeric(18,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scholarship_award_components_amount check (amount >= 0),
  constraint ck_scholarship_award_components_utilized_amount check (utilized_amount >= 0),
  constraint uq_scholarship_award_components_tenant_id unique (tenant_id, id)
);
create index ix_scholarship_award_components_award_id on fin.scholarship_award_components (tenant_id, award_id);
create index ix_scholarship_award_components_benefit_id on fin.scholarship_award_components (tenant_id, benefit_id);
comment on table fin.scholarship_award_components is '[Mongo: FIN06.scholarship_award_components]';

create table fin.scholarship_award_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  award_id uuid not null,
  event_type text not null,
  reason text,
  effective_from date,
  effective_to date,
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_scholarship_award_events_event_type check (event_type in ('RENEWAL', 'SUSPENSION', 'REINSTATEMENT', 'CANCELLATION')),
  constraint uq_scholarship_award_events_tenant_id unique (tenant_id, id)
);
create index ix_scholarship_award_events_award_id on fin.scholarship_award_events (tenant_id, award_id);
create index ix_scholarship_award_events_workflow_instance_id on fin.scholarship_award_events (tenant_id, workflow_instance_id);
comment on table fin.scholarship_award_events is '[Mongo: FIN06.scholarship_renewals, FIN06.scholarship_suspensions, FIN06.scholarship_cancellations]';

create table fin.scholarship_utilizations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  award_component_id uuid not null,
  obligation_id uuid,
  amount numeric(18,2) not null,
  utilized_on date not null,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_scholarship_utilizations_amount check (amount > 0),
  constraint uq_scholarship_utilizations_tenant_id unique (tenant_id, id)
);
create index ix_scholarship_utilizations_tenant_id_obligation_id on fin.scholarship_utilizations (tenant_id, obligation_id);
create index ix_scholarship_utilizations_award_component_id on fin.scholarship_utilizations (tenant_id, award_component_id);
comment on table fin.scholarship_utilizations is '[Mongo: FIN06.scholarship_utilizations]';

create table fin.vendors (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  vendor_type text,
  gstin text,
  pan_masked text,
  contact_person_id uuid,
  address_id uuid,
  email text,
  phone text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_vendors_status check (status in ('ACTIVE', 'BLOCKED', 'INACTIVE')),
  constraint uq_vendors_tenant_id unique (tenant_id, id)
);
create unique index uq_vendors_tenant_id_code on fin.vendors (tenant_id, code) where deleted_at is null;
create unique index uq_vendors_tenant_id_gstin on fin.vendors (tenant_id, gstin) where deleted_at is null and gstin is not null;
create index ix_vendors_contact_person_id on fin.vendors (tenant_id, contact_person_id);
create index ix_vendors_address_id on fin.vendors (tenant_id, address_id);
comment on table fin.vendors is '[Mongo: FIN03.vendors]';

create table fin.vendor_bank_accounts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  vendor_id uuid not null,
  bank_name text,
  ifsc text,
  account_holder text,
  account_last4 text,
  account_ciphertext bytea,
  is_primary boolean not null default false,
  is_verified boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_vendor_bank_accounts_tenant_id unique (tenant_id, id)
);
create index ix_vendor_bank_accounts_vendor_id on fin.vendor_bank_accounts (tenant_id, vendor_id);
comment on table fin.vendor_bank_accounts is 'Account number stored ciphertext-only (Vault/pgsodium); never plaintext [Mongo: FIN03.vendor_bank_accounts]';

create table fin.expense_categories (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  parent_id uuid,
  code text not null,
  name text not null,
  ledger_account_id uuid,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_expense_categories_tenant_id unique (tenant_id, id)
);
create unique index uq_expense_categories_tenant_id_code on fin.expense_categories (tenant_id, code) where deleted_at is null;
create index ix_expense_categories_parent_id on fin.expense_categories (tenant_id, parent_id);
create index ix_expense_categories_ledger_account_id on fin.expense_categories (tenant_id, ledger_account_id);
comment on table fin.expense_categories is '[Mongo: FIN03.expense_categories]';

create table fin.expenses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  department_id uuid,
  category_id uuid,
  vendor_id uuid,
  claimant_employee_id uuid,
  expense_no text not null,
  expense_date date not null,
  expense_type text not null default 'PURCHASE',
  total_amount numeric(18,2) not null,
  tax_amount numeric(18,2) not null default 0,
  budget_line_id uuid,
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  description text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_expenses_expense_type check (expense_type in ('PURCHASE', 'REIMBURSEMENT', 'ADVANCE', 'PETTY_CASH')),
  constraint ck_expenses_total_amount check (total_amount >= 0),
  constraint ck_expenses_tax_amount check (tax_amount >= 0),
  constraint ck_expenses_status check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'PAID', 'CANCELLED')),
  constraint uq_expenses_tenant_id unique (tenant_id, id)
);
create unique index uq_expenses_tenant_id_expense_no on fin.expenses (tenant_id, expense_no) where deleted_at is null;
create index ix_expenses_tenant_id_department_id_status on fin.expenses (tenant_id, department_id, status);
create index ix_expenses_tenant_id_vendor_id on fin.expenses (tenant_id, vendor_id);
create index ix_expenses_college_id on fin.expenses (tenant_id, college_id);
create index ix_expenses_category_id on fin.expenses (tenant_id, category_id);
create index ix_expenses_claimant_employee_id on fin.expenses (tenant_id, claimant_employee_id);
create index ix_expenses_budget_line_id on fin.expenses (tenant_id, budget_line_id);
create index ix_expenses_workflow_instance_id on fin.expenses (tenant_id, workflow_instance_id);
comment on table fin.expenses is 'Reimbursements are expenses with expense_type=REIMBURSEMENT [Mongo: FIN03.expenses, FIN03.expense_reimbursements]';

create table fin.expense_items (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  expense_id uuid not null,
  description text not null,
  quantity numeric(18,4) not null default 1,
  unit_price numeric(18,2) not null,
  amount numeric(18,2) not null,
  tax_code_id uuid,
  tax_amount numeric(18,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_expense_items_unit_price check (unit_price >= 0),
  constraint ck_expense_items_amount check (amount >= 0),
  constraint ck_expense_items_tax_amount check (tax_amount >= 0),
  constraint uq_expense_items_tenant_id unique (tenant_id, id)
);
create index ix_expense_items_expense_id on fin.expense_items (tenant_id, expense_id);
create index ix_expense_items_tax_code_id on fin.expense_items (tenant_id, tax_code_id);
comment on table fin.expense_items is '[Mongo: FIN03.expense_items]';

create table fin.vendor_invoices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  expense_id uuid,
  vendor_id uuid not null,
  invoice_no text not null,
  invoice_date date not null,
  due_date date,
  amount numeric(18,2) not null,
  tax_amount numeric(18,2) not null default 0,
  status text not null default 'RECEIVED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_vendor_invoices_amount check (amount >= 0),
  constraint ck_vendor_invoices_tax_amount check (tax_amount >= 0),
  constraint ck_vendor_invoices_status check (status in ('RECEIVED', 'VERIFIED', 'APPROVED', 'PAID', 'DISPUTED')),
  constraint uq_vendor_invoices_tenant_id unique (tenant_id, id)
);
create unique index uq_vendor_invoices_tenant_id_vendor_id_invoice_no on fin.vendor_invoices (tenant_id, vendor_id, invoice_no) where deleted_at is null;
create index ix_vendor_invoices_expense_id on fin.vendor_invoices (tenant_id, expense_id);
create index ix_vendor_invoices_vendor_id on fin.vendor_invoices (tenant_id, vendor_id);
comment on table fin.vendor_invoices is '[Mongo: FIN03.expense_invoices]';

create table fin.payment_requests (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  source_type text not null,
  source_id uuid not null,
  payee_type text not null,
  payee_id uuid,
  payee_name text,
  amount numeric(18,2) not null,
  method text,
  bank_account_ref uuid,
  status text not null default 'PENDING',
  workflow_instance_id uuid,
  requested_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payment_requests_source_type check (source_type in ('EXPENSE', 'PAYROLL', 'SCHOLARSHIP', 'REFUND', 'VENDOR_INVOICE')),
  constraint ck_payment_requests_payee_type check (payee_type in ('VENDOR', 'EMPLOYEE', 'STUDENT', 'OTHER')),
  constraint ck_payment_requests_amount check (amount > 0),
  constraint ck_payment_requests_status check (status in ('PENDING', 'APPROVED', 'SENT', 'PAID', 'FAILED', 'CANCELLED')),
  constraint uq_payment_requests_tenant_id unique (tenant_id, id)
);
create index ix_payment_requests_tenant_id_source_type_source_id on fin.payment_requests (tenant_id, source_type, source_id);
create index ix_payment_requests_tenant_id_status on fin.payment_requests (tenant_id, status);
create index ix_payment_requests_workflow_instance_id on fin.payment_requests (tenant_id, workflow_instance_id);
create index ix_payment_requests_requested_by on fin.payment_requests (tenant_id, requested_by);
comment on table fin.payment_requests is 'Outbound payment instruction for any source [Mongo: FIN03.expense_payment_requests, FIN07.payroll_payment_requests]';

create table fin.disbursements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payment_request_id uuid,
  source_type text,
  source_id uuid,
  payee_name text,
  amount numeric(18,2) not null,
  method text,
  utr_no text,
  paid_on date,
  bank_reference text,
  status text not null default 'PAID',
  journal_entry_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_disbursements_amount check (amount > 0),
  constraint ck_disbursements_status check (status in ('PAID', 'FAILED', 'REVERSED')),
  constraint uq_disbursements_tenant_id unique (tenant_id, id)
);
create index ix_disbursements_tenant_id_payment_request_id on fin.disbursements (tenant_id, payment_request_id);
create index ix_disbursements_tenant_id_source_type_source_id on fin.disbursements (tenant_id, source_type, source_id);
create index ix_disbursements_journal_entry_id on fin.disbursements (tenant_id, journal_entry_id);
comment on table fin.disbursements is 'Executed outbound payments (bank file line / UTR) [Mongo: FIN06.scholarship_disbursements, FIN07.payroll_payment_records]';


-- ============================================================ tax

create table tax.tax_types (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  category text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tax_types_category check (category in ('GST', 'TDS', 'TCS', 'PT', 'PF', 'ESI', 'INCOME_TAX', 'OTHER')),
  constraint uq_tax_types_tenant_id unique (tenant_id, id)
);
create unique index uq_tax_types_tenant_id_code on tax.tax_types (tenant_id, code) where deleted_at is null;
comment on table tax.tax_types is '[Mongo: FIN04.tax_types]';

create table tax.tax_jurisdictions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  country_code char(3) not null default 'IND',
  state_code text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_tax_jurisdictions_tenant_id unique (tenant_id, id)
);
create unique index uq_tax_jurisdictions_tenant_id_code on tax.tax_jurisdictions (tenant_id, code) where deleted_at is null;
comment on table tax.tax_jurisdictions is '[Mongo: FIN04.tax_jurisdictions]';

create table tax.tax_codes (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  tax_type_id uuid not null,
  code text not null,
  name text not null,
  hsn_sac text,
  is_exempt boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_tax_codes_tenant_id unique (tenant_id, id)
);
create unique index uq_tax_codes_tenant_id_code on tax.tax_codes (tenant_id, code) where deleted_at is null;
create index ix_tax_codes_tax_type_id on tax.tax_codes (tenant_id, tax_type_id);
comment on table tax.tax_codes is '[Mongo: FIN04.tax_codes]';

create table tax.tax_rates (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  tax_code_id uuid not null,
  jurisdiction_id uuid,
  component text not null default 'TOTAL',
  rate_pct numeric(9,4) not null,
  effective_from date not null,
  effective_to date,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tax_rates_component check (component in ('TOTAL', 'CGST', 'SGST', 'IGST', 'CESS', 'TDS')),
  constraint ck_tax_rates_rate_pct check (rate_pct >= 0),
  constraint ck_tax_rates_x1 check (effective_to is null or effective_to >= effective_from),
  constraint uq_tax_rates_tenant_id unique (tenant_id, id)
);
create index ix_tax_rates_tenant_id_tax_code_id_effective_from on tax.tax_rates (tenant_id, tax_code_id, effective_from);
create index ix_tax_rates_jurisdiction_id on tax.tax_rates (tenant_id, jurisdiction_id);
comment on table tax.tax_rates is 'Effective-dated rates by component (CGST/SGST/IGST ...) [Mongo: FIN04.tax_rates, FIN04.tax_components]';

create table tax.tax_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  tax_code_id uuid,
  rule_type text not null,
  condition jsonb not null default '{}'::jsonb,
  priority integer not null default 100,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_tax_rules_tenant_id unique (tenant_id, id)
);
create index ix_tax_rules_tax_code_id on tax.tax_rules (tenant_id, tax_code_id);
comment on table tax.tax_rules is '[Mongo: FIN04.tax_rules]';

create table tax.tax_exemptions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  tax_code_id uuid,
  person_id uuid,
  vendor_id uuid,
  certificate_no text,
  valid_from date,
  valid_to date,
  document_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_tax_exemptions_tenant_id unique (tenant_id, id)
);
create index ix_tax_exemptions_tax_code_id on tax.tax_exemptions (tenant_id, tax_code_id);
create index ix_tax_exemptions_person_id on tax.tax_exemptions (tenant_id, person_id);
create index ix_tax_exemptions_vendor_id on tax.tax_exemptions (tenant_id, vendor_id);
create index ix_tax_exemptions_document_id on tax.tax_exemptions (tenant_id, document_id);
comment on table tax.tax_exemptions is '[Mongo: FIN04.tax_exemptions]';

create table tax.tax_periods (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  period_type text not null,
  financial_year text not null,
  start_date date not null,
  end_date date not null,
  status text not null default 'OPEN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tax_periods_period_type check (period_type in ('MONTH', 'QUARTER', 'YEAR')),
  constraint ck_tax_periods_status check (status in ('OPEN', 'CLOSED', 'FILED')),
  constraint uq_tax_periods_tenant_id unique (tenant_id, id)
);
create unique index uq_tax_periods_tenant_id_period_type_start_date on tax.tax_periods (tenant_id, period_type, start_date) where deleted_at is null;
comment on table tax.tax_periods is 'Monthly/yearly tax sheets are views over tax.tax_transactions (see 90_views) [Mongo: FIN04.tax_periods, FIN04.monthly_tax_sheets, FIN04.yearly_tax_sheets]';

create table tax.tax_transactions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  period_id uuid,
  tax_code_id uuid not null,
  source_type text not null,
  source_id uuid not null,
  direction text not null,
  taxable_amount numeric(18,2) not null,
  tax_amount numeric(18,2) not null,
  rate_pct numeric(9,4),
  breakdown jsonb default '{}'::jsonb,
  txn_date date not null,
  calculation_snapshot jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_tax_transactions_direction check (direction in ('OUTPUT', 'INPUT', 'WITHHELD')),
  constraint ck_tax_transactions_taxable_amount check (taxable_amount >= 0),
  constraint ck_tax_transactions_tax_amount check (tax_amount >= 0),
  constraint uq_tax_transactions_tenant_id unique (tenant_id, id)
);
create index ix_tax_transactions_tenant_id_period_id_tax_code_id on tax.tax_transactions (tenant_id, period_id, tax_code_id);
create index ix_tax_transactions_tenant_id_source_type_source_id on tax.tax_transactions (tenant_id, source_type, source_id);
create index ix_tax_transactions_tax_code_id on tax.tax_transactions (tenant_id, tax_code_id);
comment on table tax.tax_transactions is 'Component breakdown in jsonb; snapshot proves rate used at the time [Mongo: FIN04.tax_transactions, FIN04.tax_transaction_items, FIN04.tax_calculation_snapshots]';

create table tax.tax_payments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  period_id uuid not null,
  challan_no text,
  amount numeric(18,2) not null,
  paid_on date,
  disbursement_id uuid,
  status text not null default 'PAID',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tax_payments_amount check (amount >= 0),
  constraint uq_tax_payments_tenant_id unique (tenant_id, id)
);
create index ix_tax_payments_period_id on tax.tax_payments (tenant_id, period_id);
create index ix_tax_payments_disbursement_id on tax.tax_payments (tenant_id, disbursement_id);
comment on table tax.tax_payments is '[Mongo: FIN04.tax_payments]';

create table tax.tax_filings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  period_id uuid not null,
  return_type text not null,
  acknowledgement_no text,
  filed_on date,
  status text not null default 'DRAFT',
  document_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_tax_filings_status check (status in ('DRAFT', 'FILED', 'ACCEPTED', 'REJECTED', 'REVISED')),
  constraint uq_tax_filings_tenant_id unique (tenant_id, id)
);
create index ix_tax_filings_period_id on tax.tax_filings (tenant_id, period_id);
create index ix_tax_filings_document_id on tax.tax_filings (tenant_id, document_id);
comment on table tax.tax_filings is '[Mongo: FIN04.tax_filing_records, FIN04.tax_documents]';


-- ============================================================ bud

create table bud.cost_centres (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  department_id uuid,
  parent_id uuid,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_cost_centres_tenant_id unique (tenant_id, id)
);
create unique index uq_cost_centres_tenant_id_code on bud.cost_centres (tenant_id, code) where deleted_at is null;
create index ix_cost_centres_department_id on bud.cost_centres (tenant_id, department_id);
create index ix_cost_centres_parent_id on bud.cost_centres (tenant_id, parent_id);
comment on table bud.cost_centres is 'Budget dimensions (department / cost centre / project) [Mongo: FIN10.budget_dimensions]';

create table bud.categories (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  parent_id uuid,
  code text not null,
  name text not null,
  ledger_account_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_categories_tenant_id unique (tenant_id, id)
);
create unique index uq_categories_tenant_id_code on bud.categories (tenant_id, code) where deleted_at is null;
create index ix_categories_parent_id on bud.categories (tenant_id, parent_id);
create index ix_categories_ledger_account_id on bud.categories (tenant_id, ledger_account_id);
comment on table bud.categories is '[Mongo: FIN10.budget_categories]';

create table bud.budgets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  academic_year_id uuid not null,
  code text not null,
  name text not null,
  budget_type text not null default 'OPERATING',
  status text not null default 'DRAFT',
  current_version_no integer not null default 1,
  workflow_instance_id uuid,
  total_amount numeric(18,2),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_budgets_budget_type check (budget_type in ('OPERATING', 'CAPITAL', 'PROJECT')),
  constraint ck_budgets_status check (status in ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'ACTIVE', 'CLOSED')),
  constraint ck_budgets_total_amount check (total_amount >= 0),
  constraint uq_budgets_tenant_id unique (tenant_id, id)
);
create unique index uq_budgets_tenant_id_code on bud.budgets (tenant_id, code) where deleted_at is null;
create index ix_budgets_college_id on bud.budgets (tenant_id, college_id);
create index ix_budgets_academic_year_id on bud.budgets (tenant_id, academic_year_id);
create index ix_budgets_workflow_instance_id on bud.budgets (tenant_id, workflow_instance_id);
comment on table bud.budgets is '[Mongo: FIN10.budgets, FIN10.budget_configuration]';

create table bud.budget_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_id uuid not null,
  version_no integer not null,
  status text not null default 'DRAFT',
  total_amount numeric(18,2),
  notes text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_budget_versions_version_no check (version_no > 0),
  constraint ck_budget_versions_status check (status in ('DRAFT', 'APPROVED', 'ACTIVE', 'SUPERSEDED')),
  constraint ck_budget_versions_total_amount check (total_amount >= 0),
  constraint uq_budget_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_budget_versions_tenant_id_budget_id_version_no on bud.budget_versions (tenant_id, budget_id, version_no) where deleted_at is null;
create index ix_budget_versions_budget_id on bud.budget_versions (tenant_id, budget_id);
comment on table bud.budget_versions is '[Mongo: FIN10.budget_versions]';

create table bud.budget_periods (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_id uuid not null,
  period_no smallint not null,
  name text,
  start_date date not null,
  end_date date not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_budget_periods_period_no check (period_no > 0),
  constraint uq_budget_periods_tenant_id unique (tenant_id, id)
);
create unique index uq_budget_periods_tenant_id_budget_id_period_no on bud.budget_periods (tenant_id, budget_id, period_no) where deleted_at is null;
create index ix_budget_periods_budget_id on bud.budget_periods (tenant_id, budget_id);
comment on table bud.budget_periods is '[Mongo: FIN10.budget_periods]';

create table bud.budget_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_version_id uuid not null,
  period_id uuid,
  category_id uuid,
  department_id uuid,
  cost_centre_id uuid,
  description text,
  planned_amount numeric(18,2) not null,
  allocated_amount numeric(18,2) not null default 0,
  committed_amount numeric(18,2) not null default 0,
  actual_amount numeric(18,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_budget_lines_planned_amount check (planned_amount >= 0),
  constraint ck_budget_lines_allocated_amount check (allocated_amount >= 0),
  constraint ck_budget_lines_committed_amount check (committed_amount >= 0),
  constraint ck_budget_lines_actual_amount check (actual_amount >= 0),
  constraint uq_budget_lines_tenant_id unique (tenant_id, id)
);
create index ix_budget_lines_tenant_id_budget_version_id_department_id on bud.budget_lines (tenant_id, budget_version_id, department_id);
create index ix_budget_lines_tenant_id_category_id on bud.budget_lines (tenant_id, category_id);
create index ix_budget_lines_period_id on bud.budget_lines (tenant_id, period_id);
create index ix_budget_lines_department_id on bud.budget_lines (tenant_id, department_id);
create index ix_budget_lines_cost_centre_id on bud.budget_lines (tenant_id, cost_centre_id);
comment on table bud.budget_lines is 'Allocation = line-level allocated_amount; allocation history via core.entity_versions / audit [Mongo: FIN10.budget_lines, FIN10.budget_allocations, FIN10.budget_allocation_versions]';

create table bud.budget_transfers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  from_line_id uuid not null,
  to_line_id uuid not null,
  amount numeric(18,2) not null,
  reason text,
  status text not null default 'PENDING',
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_budget_transfers_amount check (amount > 0),
  constraint ck_budget_transfers_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'APPLIED')),
  constraint ck_budget_transfers_x1 check (from_line_id <> to_line_id),
  constraint uq_budget_transfers_tenant_id unique (tenant_id, id)
);
create index ix_budget_transfers_from_line_id on bud.budget_transfers (tenant_id, from_line_id);
create index ix_budget_transfers_to_line_id on bud.budget_transfers (tenant_id, to_line_id);
create index ix_budget_transfers_workflow_instance_id on bud.budget_transfers (tenant_id, workflow_instance_id);
comment on table bud.budget_transfers is '[Mongo: FIN10.budget_transfers, FIN10.budget_transfer_items]';

create table bud.budget_controls (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_id uuid,
  category_id uuid,
  control_type text not null,
  threshold_pct numeric(9,4),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_budget_controls_control_type check (control_type in ('HARD_STOP', 'WARN', 'APPROVAL_REQUIRED')),
  constraint uq_budget_controls_tenant_id unique (tenant_id, id)
);
create index ix_budget_controls_budget_id on bud.budget_controls (tenant_id, budget_id);
create index ix_budget_controls_category_id on bud.budget_controls (tenant_id, category_id);
comment on table bud.budget_controls is '[Mongo: FIN10.budget_controls]';

create table bud.control_results (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  control_id uuid,
  budget_line_id uuid,
  source_type text,
  source_id uuid,
  result text not null,
  detail jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_control_results_result check (result in ('PASS', 'WARN', 'BLOCK')),
  constraint uq_control_results_tenant_id unique (tenant_id, id)
);
create index ix_control_results_control_id on bud.control_results (tenant_id, control_id);
create index ix_control_results_budget_line_id on bud.control_results (tenant_id, budget_line_id);
comment on table bud.control_results is '[Mongo: FIN10.budget_control_results]';

create table bud.commitments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_line_id uuid not null,
  entry_type text not null,
  source_type text,
  source_id uuid,
  amount numeric(18,2) not null,
  entry_date date not null default current_date,
  note text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_commitments_entry_type check (entry_type in ('RESERVATION', 'COMMITMENT', 'RELEASE', 'ACTUAL')),
  constraint uq_commitments_tenant_id unique (tenant_id, id)
);
create index ix_commitments_tenant_id_budget_line_id_entry_type on bud.commitments (tenant_id, budget_line_id, entry_type);
create index ix_commitments_tenant_id_source_type_source_id on bud.commitments (tenant_id, source_type, source_id);
comment on table bud.commitments is 'Append-only budget ledger; budget_lines.committed/actual are roll-ups of this table [Mongo: FIN10.budget_commitments, FIN10.budget_reservations, FIN10.budget_releases, FIN10.budget_actuals]';

create table bud.forecasts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_id uuid,
  name text not null,
  method text,
  status text not null default 'DRAFT',
  current_version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_forecasts_status check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED')),
  constraint uq_forecasts_tenant_id unique (tenant_id, id)
);
create index ix_forecasts_budget_id on bud.forecasts (tenant_id, budget_id);
comment on table bud.forecasts is '[Mongo: FIN10.forecasts]';

create table bud.forecast_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  forecast_id uuid not null,
  version_no integer not null default 1,
  period_id uuid,
  category_id uuid,
  department_id uuid,
  amount numeric(18,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_forecast_lines_tenant_id unique (tenant_id, id)
);
create index ix_forecast_lines_tenant_id_forecast_id_version_no on bud.forecast_lines (tenant_id, forecast_id, version_no);
create index ix_forecast_lines_period_id on bud.forecast_lines (tenant_id, period_id);
create index ix_forecast_lines_category_id on bud.forecast_lines (tenant_id, category_id);
create index ix_forecast_lines_department_id on bud.forecast_lines (tenant_id, department_id);
comment on table bud.forecast_lines is '[Mongo: FIN10.forecast_lines]';

create table bud.scenarios (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_id uuid,
  name text not null,
  description text,
  status text not null default 'DRAFT',
  current_version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_scenarios_status check (status in ('DRAFT', 'ACTIVE', 'ARCHIVED')),
  constraint uq_scenarios_tenant_id unique (tenant_id, id)
);
create index ix_scenarios_budget_id on bud.scenarios (tenant_id, budget_id);
comment on table bud.scenarios is '[Mongo: FIN10.budget_scenarios]';

create table bud.assumptions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  owner_type text not null,
  owner_id uuid not null,
  key text not null,
  value jsonb not null default '{}'::jsonb,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_assumptions_owner_type check (owner_type in ('FORECAST', 'SCENARIO')),
  constraint uq_assumptions_tenant_id unique (tenant_id, id)
);
create index ix_assumptions_tenant_id_owner_type_owner_id on bud.assumptions (tenant_id, owner_type, owner_id);
comment on table bud.assumptions is '[Mongo: FIN10.forecast_assumptions, FIN10.scenario_assumptions]';

create table bud.scenario_results (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scenario_id uuid not null,
  version_no integer not null default 1,
  metric text not null,
  value numeric(18,2),
  detail jsonb,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_scenario_results_tenant_id unique (tenant_id, id)
);
create index ix_scenario_results_scenario_id on bud.scenario_results (tenant_id, scenario_id);
comment on table bud.scenario_results is '[Mongo: FIN10.scenario_results]';

create table bud.variance_explanations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  budget_line_id uuid not null,
  period_id uuid,
  explanation text not null,
  explained_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_variance_explanations_tenant_id unique (tenant_id, id)
);
create index ix_variance_explanations_budget_line_id on bud.variance_explanations (tenant_id, budget_line_id);
create index ix_variance_explanations_period_id on bud.variance_explanations (tenant_id, period_id);
create index ix_variance_explanations_explained_by on bud.variance_explanations (tenant_id, explained_by);
comment on table bud.variance_explanations is 'Variance itself is a view: planned - actual (bud.v_variance) [Mongo: FIN10.variance_explanations]';


-- ============================================================ pay

create table pay.payroll_groups (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  code text not null,
  name text not null,
  pay_frequency text not null default 'MONTHLY',
  pay_day smallint,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payroll_groups_pay_frequency check (pay_frequency in ('MONTHLY', 'WEEKLY', 'BIWEEKLY')),
  constraint ck_payroll_groups_status check (status in ('ACTIVE', 'INACTIVE')),
  constraint uq_payroll_groups_tenant_id unique (tenant_id, id)
);
create unique index uq_payroll_groups_tenant_id_code on pay.payroll_groups (tenant_id, code) where deleted_at is null;
create index ix_payroll_groups_college_id on pay.payroll_groups (tenant_id, college_id);
comment on table pay.payroll_groups is '[Mongo: FIN07.payroll_groups]';

create table pay.salary_components (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  component_type text not null,
  calc_type text not null default 'FIXED',
  is_taxable boolean not null default true,
  is_statutory boolean not null default false,
  ledger_account_id uuid,
  display_order integer not null default 0,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_salary_components_component_type check (component_type in ('EARNING', 'DEDUCTION', 'EMPLOYER_CONTRIBUTION', 'REIMBURSEMENT')),
  constraint ck_salary_components_calc_type check (calc_type in ('FIXED', 'PERCENT_OF', 'FORMULA', 'SLAB')),
  constraint uq_salary_components_tenant_id unique (tenant_id, id)
);
create unique index uq_salary_components_tenant_id_code on pay.salary_components (tenant_id, code) where deleted_at is null;
create index ix_salary_components_ledger_account_id on pay.salary_components (tenant_id, ledger_account_id);
comment on table pay.salary_components is '[Mongo: FIN07.salary_components, HRM05.salary_components]';

create table pay.salary_structures (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payroll_group_id uuid,
  code text not null,
  name text not null,
  status text not null default 'DRAFT',
  current_version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_salary_structures_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_salary_structures_tenant_id unique (tenant_id, id)
);
create unique index uq_salary_structures_tenant_id_code on pay.salary_structures (tenant_id, code) where deleted_at is null;
create index ix_salary_structures_payroll_group_id on pay.salary_structures (tenant_id, payroll_group_id);
comment on table pay.salary_structures is '[Mongo: FIN07.salary_structures, HRM05.salary_structures]';

create table pay.salary_structure_versions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_id uuid not null,
  version_no integer not null,
  effective_from date not null,
  effective_to date,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_salary_structure_versions_version_no check (version_no > 0),
  constraint ck_salary_structure_versions_status check (status in ('DRAFT', 'APPROVED', 'ACTIVE', 'SUPERSEDED')),
  constraint uq_salary_structure_versions_tenant_id unique (tenant_id, id)
);
create unique index uq_salary_structure_versions_tenant_id_structure_i_fca43643 on pay.salary_structure_versions (tenant_id, structure_id, version_no) where deleted_at is null;
create index ix_salary_structure_versions_structure_id on pay.salary_structure_versions (tenant_id, structure_id);
comment on table pay.salary_structure_versions is '[Mongo: FIN07.salary_structure_versions, HRM05.salary_component_versions]';

create table pay.structure_components (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  structure_version_id uuid not null,
  component_id uuid not null,
  amount numeric(18,2),
  percent_of_component_id uuid,
  percentage numeric(9,4),
  formula text,
  min_amount numeric(18,2),
  max_amount numeric(18,2),
  sequence smallint not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_structure_components_tenant_id unique (tenant_id, id)
);
create unique index uq_structure_components_tenant_id_structure_versio_bc0d7262 on pay.structure_components (tenant_id, structure_version_id, component_id) where deleted_at is null;
create index ix_structure_components_structure_version_id on pay.structure_components (tenant_id, structure_version_id);
create index ix_structure_components_component_id on pay.structure_components (tenant_id, component_id);
create index ix_structure_components_percent_of_component_id on pay.structure_components (tenant_id, percent_of_component_id);

create table pay.employee_compensations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  structure_version_id uuid,
  payroll_group_id uuid,
  effective_from date not null,
  effective_to date,
  annual_ctc numeric(18,2),
  monthly_gross numeric(18,2),
  bank_account_ciphertext bytea,
  bank_last4 text,
  ifsc text,
  pf_applicable boolean not null default true,
  esi_applicable boolean not null default false,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_employee_compensations_annual_ctc check (annual_ctc >= 0),
  constraint ck_employee_compensations_monthly_gross check (monthly_gross >= 0),
  constraint ck_employee_compensations_status check (status in ('ACTIVE', 'SUPERSEDED', 'SUSPENDED')),
  constraint ck_employee_compensations_x1 check (effective_to is null or effective_to >= effective_from),
  constraint uq_employee_compensations_tenant_id unique (tenant_id, id)
);
create index ix_employee_compensations_tenant_id_employee_id_ef_b20c8abd on pay.employee_compensations (tenant_id, employee_id, effective_from desc);
create index ix_employee_compensations_structure_version_id on pay.employee_compensations (tenant_id, structure_version_id);
create index ix_employee_compensations_payroll_group_id on pay.employee_compensations (tenant_id, payroll_group_id);
comment on table pay.employee_compensations is 'Effective-dated compensation; NEVER stores plaintext bank account [Mongo: FIN07.employee_payroll_profiles, HRM05.employee_compensations]';

create table pay.compensation_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  compensation_id uuid not null,
  component_id uuid not null,
  amount numeric(18,2) not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_compensation_lines_amount check (amount >= 0),
  constraint uq_compensation_lines_tenant_id unique (tenant_id, id)
);
create unique index uq_compensation_lines_tenant_id_compensation_id_component_id on pay.compensation_lines (tenant_id, compensation_id, component_id) where deleted_at is null;
create index ix_compensation_lines_compensation_id on pay.compensation_lines (tenant_id, compensation_id);
create index ix_compensation_lines_component_id on pay.compensation_lines (tenant_id, component_id);
comment on table pay.compensation_lines is 'Per-employee component amounts (normalised)';

create table pay.compensation_revisions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  from_compensation_id uuid,
  to_compensation_id uuid,
  revision_type text not null,
  effective_from date not null,
  reason text,
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_compensation_revisions_revision_type check (revision_type in ('INCREMENT', 'PROMOTION', 'CORRECTION', 'DEMOTION')),
  constraint uq_compensation_revisions_tenant_id unique (tenant_id, id)
);
create index ix_compensation_revisions_employee_id on pay.compensation_revisions (tenant_id, employee_id);
create index ix_compensation_revisions_from_compensation_id on pay.compensation_revisions (tenant_id, from_compensation_id);
create index ix_compensation_revisions_to_compensation_id on pay.compensation_revisions (tenant_id, to_compensation_id);
create index ix_compensation_revisions_workflow_instance_id on pay.compensation_revisions (tenant_id, workflow_instance_id);
comment on table pay.compensation_revisions is '[Mongo: HRM05.compensation_revisions]';

create table pay.payroll_periods (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payroll_group_id uuid,
  period_month smallint not null,
  period_year smallint not null,
  start_date date not null,
  end_date date not null,
  pay_date date,
  status text not null default 'OPEN',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payroll_periods_period_month check (period_month >= 0),
  constraint ck_payroll_periods_status check (status in ('OPEN', 'PROCESSING', 'CLOSED', 'PAID')),
  constraint ck_payroll_periods_x1 check (period_month between 1 and 12),
  constraint uq_payroll_periods_tenant_id unique (tenant_id, id)
);
create unique index uq_payroll_periods_tenant_id_payroll_group_id_peri_4a85c406 on pay.payroll_periods (tenant_id, payroll_group_id, period_year, period_month) where deleted_at is null;
create index ix_payroll_periods_payroll_group_id on pay.payroll_periods (tenant_id, payroll_group_id);
comment on table pay.payroll_periods is '[Mongo: FIN07.payroll_periods, HRM05.payroll_periods]';

create table pay.payroll_runs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  period_id uuid not null,
  run_no text not null,
  run_type text not null default 'REGULAR',
  status text not null default 'DRAFT',
  employee_count integer,
  total_gross numeric(18,2),
  total_deductions numeric(18,2),
  total_net numeric(18,2),
  workflow_instance_id uuid,
  finalized_by uuid,
  finalized_at timestamptz,
  job_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payroll_runs_run_type check (run_type in ('REGULAR', 'SUPPLEMENTARY', 'OFF_CYCLE', 'FNF')),
  constraint ck_payroll_runs_status check (status in ('DRAFT', 'CALCULATING', 'CALCULATED', 'PENDING_APPROVAL', 'APPROVED', 'FINALIZED', 'PAID', 'CANCELLED')),
  constraint uq_payroll_runs_tenant_id unique (tenant_id, id)
);
create unique index uq_payroll_runs_tenant_id_run_no on pay.payroll_runs (tenant_id, run_no) where deleted_at is null;
create index ix_payroll_runs_period_id on pay.payroll_runs (tenant_id, period_id);
create index ix_payroll_runs_workflow_instance_id on pay.payroll_runs (tenant_id, workflow_instance_id);
create index ix_payroll_runs_finalized_by on pay.payroll_runs (tenant_id, finalized_by);
comment on table pay.payroll_runs is '[Mongo: FIN07.payroll_runs, HRM05.payroll_runs, FIN07.payroll_finalizations, FIN07.payroll_approvals, HRM05.payroll_approvals]';

create table pay.payroll_inputs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  run_id uuid not null,
  employee_id uuid not null,
  input_type text not null,
  component_id uuid,
  quantity numeric(18,4),
  amount numeric(18,2),
  source_ref uuid,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payroll_inputs_input_type check (input_type in ('ATTENDANCE', 'LEAVE', 'OVERTIME', 'LOP', 'VARIABLE', 'ADJUSTMENT')),
  constraint uq_payroll_inputs_tenant_id unique (tenant_id, id)
);
create index ix_payroll_inputs_tenant_id_run_id_employee_id on pay.payroll_inputs (tenant_id, run_id, employee_id);
create index ix_payroll_inputs_employee_id on pay.payroll_inputs (tenant_id, employee_id);
create index ix_payroll_inputs_component_id on pay.payroll_inputs (tenant_id, component_id);
comment on table pay.payroll_inputs is '[Mongo: FIN07.payroll_inputs, FIN07.payroll_input_items, HRM05.payroll_inputs]';

create table pay.payslips (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  run_id uuid not null,
  employee_id uuid not null,
  compensation_id uuid,
  payslip_no text not null,
  working_days numeric(9,4),
  paid_days numeric(9,4),
  lop_days numeric(9,4),
  gross_earnings numeric(18,2) not null default 0,
  total_deductions numeric(18,2) not null default 0,
  net_pay numeric(18,2) not null default 0,
  employer_contribution numeric(18,2) not null default 0,
  status text not null default 'CALCULATED',
  document_id uuid,
  published_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payslips_status check (status in ('CALCULATED', 'APPROVED', 'PUBLISHED', 'PAID', 'HELD')),
  constraint uq_payslips_tenant_id unique (tenant_id, id)
);
create unique index uq_payslips_tenant_id_run_id_employee_id on pay.payslips (tenant_id, run_id, employee_id) where deleted_at is null;
create unique index uq_payslips_tenant_id_payslip_no on pay.payslips (tenant_id, payslip_no) where deleted_at is null;
create index ix_payslips_tenant_id_employee_id on pay.payslips (tenant_id, employee_id);
create index ix_payslips_run_id on pay.payslips (tenant_id, run_id);
create index ix_payslips_compensation_id on pay.payslips (tenant_id, compensation_id);
create index ix_payslips_document_id on pay.payslips (tenant_id, document_id);
comment on table pay.payslips is '[Mongo: FIN07.payslips, HRM05.payslips, FIN07.payroll_calculations, HRM05.payroll_calculations, FIN07.payslip_documents]';

create table pay.payslip_lines (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  payslip_id uuid not null,
  component_id uuid not null,
  line_type text not null,
  amount numeric(18,2) not null,
  is_arrear boolean not null default false,
  note text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_payslip_lines_line_type check (line_type in ('EARNING', 'DEDUCTION', 'EMPLOYER_CONTRIBUTION', 'REIMBURSEMENT')),
  constraint uq_payslip_lines_tenant_id unique (tenant_id, id)
);
create index ix_payslip_lines_tenant_id_payslip_id on pay.payslip_lines (tenant_id, payslip_id);
create index ix_payslip_lines_component_id on pay.payslip_lines (tenant_id, component_id);
comment on table pay.payslip_lines is 'Earnings and deductions in one normalised line table [Mongo: FIN07.payroll_calculation_items, HRM05.payroll_earnings, HRM05.payroll_deductions]';

create table pay.run_adjustments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  run_id uuid,
  employee_id uuid not null,
  adjustment_type text not null,
  component_id uuid,
  amount numeric(18,2) not null,
  reason text,
  status text not null default 'PENDING',
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_run_adjustments_adjustment_type check (adjustment_type in ('ARREAR', 'BONUS', 'RECOVERY', 'INCENTIVE', 'CORRECTION', 'OTHER')),
  constraint ck_run_adjustments_status check (status in ('PENDING', 'APPROVED', 'APPLIED', 'REJECTED')),
  constraint uq_run_adjustments_tenant_id unique (tenant_id, id)
);
create index ix_run_adjustments_run_id on pay.run_adjustments (tenant_id, run_id);
create index ix_run_adjustments_employee_id on pay.run_adjustments (tenant_id, employee_id);
create index ix_run_adjustments_component_id on pay.run_adjustments (tenant_id, component_id);
create index ix_run_adjustments_workflow_instance_id on pay.run_adjustments (tenant_id, workflow_instance_id);
comment on table pay.run_adjustments is '[Mongo: FIN07.payroll_adjustments, HRM05.payroll_adjustments, FIN07.payroll_arrears, FIN07.payroll_bonuses]';

create table pay.employee_loans (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  loan_type text not null,
  principal numeric(18,2) not null,
  interest_pct numeric(9,4),
  installments integer not null,
  emi_amount numeric(18,2),
  start_run_period_id uuid,
  outstanding numeric(18,2) not null,
  status text not null default 'ACTIVE',
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_employee_loans_loan_type check (loan_type in ('LOAN', 'ADVANCE')),
  constraint ck_employee_loans_principal check (principal > 0),
  constraint ck_employee_loans_installments check (installments > 0),
  constraint ck_employee_loans_outstanding check (outstanding >= 0),
  constraint ck_employee_loans_status check (status in ('REQUESTED', 'APPROVED', 'ACTIVE', 'CLOSED', 'CANCELLED')),
  constraint uq_employee_loans_tenant_id unique (tenant_id, id)
);
create index ix_employee_loans_employee_id on pay.employee_loans (tenant_id, employee_id);
create index ix_employee_loans_start_run_period_id on pay.employee_loans (tenant_id, start_run_period_id);
create index ix_employee_loans_workflow_instance_id on pay.employee_loans (tenant_id, workflow_instance_id);
comment on table pay.employee_loans is '[Mongo: FIN07.payroll_loans, FIN07.payroll_advances]';

create table pay.loan_installments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  loan_id uuid not null,
  installment_no integer not null,
  due_period_id uuid,
  amount numeric(18,2) not null,
  status text not null default 'DUE',
  payslip_line_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_loan_installments_installment_no check (installment_no > 0),
  constraint ck_loan_installments_amount check (amount >= 0),
  constraint ck_loan_installments_status check (status in ('DUE', 'DEDUCTED', 'SKIPPED', 'WAIVED')),
  constraint uq_loan_installments_tenant_id unique (tenant_id, id)
);
create unique index uq_loan_installments_tenant_id_loan_id_installment_no on pay.loan_installments (tenant_id, loan_id, installment_no) where deleted_at is null;
create index ix_loan_installments_loan_id on pay.loan_installments (tenant_id, loan_id);
create index ix_loan_installments_due_period_id on pay.loan_installments (tenant_id, due_period_id);
create index ix_loan_installments_payslip_line_id on pay.loan_installments (tenant_id, payslip_line_id);
comment on table pay.loan_installments is 'NEW: repayment schedule';

create table pay.statutory_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  rule_type text not null,
  jurisdiction_id uuid,
  params jsonb not null default '{}'::jsonb,
  effective_from date not null,
  effective_to date,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_statutory_rules_rule_type check (rule_type in ('PF', 'ESI', 'PT', 'TDS', 'LWF', 'GRATUITY', 'BONUS')),
  constraint ck_statutory_rules_x1 check (effective_to is null or effective_to >= effective_from),
  constraint uq_statutory_rules_tenant_id unique (tenant_id, id)
);
create index ix_statutory_rules_tenant_id_rule_type_effective_from on pay.statutory_rules (tenant_id, rule_type, effective_from);
create index ix_statutory_rules_jurisdiction_id on pay.statutory_rules (tenant_id, jurisdiction_id);
comment on table pay.statutory_rules is 'Each row IS an effective-dated version (replaces rules + rule_versions) [Mongo: HRM05.statutory_rules, HRM05.statutory_rule_versions]';


-- ============================================================ hrm

create table hrm.designations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  grade text,
  category text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_designations_category check (category in ('TEACHING', 'NON_TEACHING', 'ADMIN', 'SUPPORT')),
  constraint uq_designations_tenant_id unique (tenant_id, id)
);
create unique index uq_designations_tenant_id_code on hrm.designations (tenant_id, code) where deleted_at is null;
comment on table hrm.designations is 'Configurable designation master (was a free-text/lookup value)';

create table hrm.employees (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  college_id uuid not null,
  department_id uuid,
  designation_id uuid,
  reporting_manager_id uuid,
  employee_no text not null,
  employment_type text not null default 'PERMANENT',
  staff_category text,
  date_of_joining date,
  confirmation_date date,
  probation_end_date date,
  date_of_exit date,
  exit_reason text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_employees_employment_type check (employment_type in ('PERMANENT', 'CONTRACT', 'VISITING', 'PART_TIME', 'INTERN', 'DEPUTATION')),
  constraint ck_employees_staff_category check (staff_category in ('TEACHING', 'NON_TEACHING', 'ADMIN', 'SUPPORT')),
  constraint ck_employees_status check (status in ('ACTIVE', 'ON_LEAVE', 'SUSPENDED', 'NOTICE_PERIOD', 'EXITED', 'RETIRED')),
  constraint ck_employees_x1 check (date_of_exit is null or date_of_joining is null or date_of_exit >= date_of_joining),
  constraint uq_employees_tenant_id unique (tenant_id, id)
);
create unique index uq_employees_tenant_id_employee_no on hrm.employees (tenant_id, employee_no) where deleted_at is null;
create unique index uq_employees_tenant_id_person_id_college_id on hrm.employees (tenant_id, person_id, college_id) where deleted_at is null;
create index ix_employees_tenant_id_college_id_department_id_status on hrm.employees (tenant_id, college_id, department_id, status);
create index ix_employees_tenant_id_reporting_manager_id on hrm.employees (tenant_id, reporting_manager_id);
create index ix_employees_person_id on hrm.employees (tenant_id, person_id);
create index ix_employees_department_id on hrm.employees (tenant_id, department_id);
create index ix_employees_designation_id on hrm.employees (tenant_id, designation_id);
comment on table hrm.employees is 'Role-specific employee data only; identity/contacts/IDs/qualifications live on core.persons [Mongo: HRM01.employees]';

create table hrm.employee_assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  department_id uuid,
  designation_id uuid,
  college_id uuid,
  campus_id uuid,
  assignment_type text not null default 'PRIMARY',
  effective_from date not null,
  effective_to date,
  reason text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_employee_assignments_assignment_type check (assignment_type in ('PRIMARY', 'SECONDARY', 'ADDITIONAL_CHARGE', 'DEPUTATION')),
  constraint ck_employee_assignments_x1 check (effective_to is null or effective_to >= effective_from),
  constraint uq_employee_assignments_tenant_id unique (tenant_id, id)
);
create index ix_employee_assignments_tenant_id_employee_id_effective_from on hrm.employee_assignments (tenant_id, employee_id, effective_from desc);
create index ix_employee_assignments_department_id on hrm.employee_assignments (tenant_id, department_id);
create index ix_employee_assignments_designation_id on hrm.employee_assignments (tenant_id, designation_id);
create index ix_employee_assignments_college_id on hrm.employee_assignments (tenant_id, college_id);
create index ix_employee_assignments_campus_id on hrm.employee_assignments (tenant_id, campus_id);
comment on table hrm.employee_assignments is '[Mongo: HRM01.employee_assignments]';

create table hrm.employment_events (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  event_type text not null,
  event_date date not null,
  from_value jsonb,
  to_value jsonb,
  reason text,
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_employment_events_event_type check (event_type in ('JOINED', 'CONFIRMED', 'TRANSFERRED', 'PROMOTED', 'DEMOTED', 'SUSPENDED', 'REINSTATED', 'RESIGNED', 'TERMINATED', 'RETIRED', 'REHIRED')),
  constraint uq_employment_events_tenant_id unique (tenant_id, id)
);
create index ix_employment_events_tenant_id_employee_id_event_date on hrm.employment_events (tenant_id, employee_id, event_date);
create index ix_employment_events_workflow_instance_id on hrm.employment_events (tenant_id, workflow_instance_id);
comment on table hrm.employment_events is 'Business-level lifecycle ledger (row-level diffs are in audit.change_log) [Mongo: HRM01.employee_history]';

create table hrm.leave_types (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  is_paid boolean not null default true,
  is_encashable boolean not null default false,
  requires_document boolean not null default false,
  gender_restriction text,
  unit text not null default 'DAY',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_leave_types_unit check (unit in ('DAY', 'HALF_DAY', 'HOUR')),
  constraint uq_leave_types_tenant_id unique (tenant_id, id)
);
create unique index uq_leave_types_tenant_id_code on hrm.leave_types (tenant_id, code) where deleted_at is null;
comment on table hrm.leave_types is '[Mongo: HRM02.leave_types]';

create table hrm.leave_policies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  leave_type_id uuid not null,
  name text not null,
  applies_to jsonb default '{}'::jsonb,
  annual_quota numeric(18,2),
  accrual_type text not null default 'YEARLY',
  carry_forward_limit numeric(18,2),
  max_consecutive_days numeric(18,2),
  min_notice_days integer,
  sandwich_rule boolean not null default false,
  effective_from date not null,
  effective_to date,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_leave_policies_accrual_type check (accrual_type in ('YEARLY', 'MONTHLY', 'NONE')),
  constraint ck_leave_policies_x1 check (effective_to is null or effective_to >= effective_from),
  constraint uq_leave_policies_tenant_id unique (tenant_id, id)
);
create index ix_leave_policies_tenant_id_leave_type_id_effective_from on hrm.leave_policies (tenant_id, leave_type_id, effective_from);
comment on table hrm.leave_policies is 'Each row is an effective-dated policy version; eligibility rules in applies_to jsonb [Mongo: HRM02.leave_policies, HRM02.leave_policy_versions, HRM02.leave_entitlement_rules]';

create table hrm.leave_entitlements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  leave_type_id uuid not null,
  policy_id uuid,
  calendar_year smallint not null,
  entitled_days numeric(18,2) not null,
  carried_forward_days numeric(18,2) not null default 0,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_leave_entitlements_entitled_days check (entitled_days >= 0),
  constraint ck_leave_entitlements_carried_forward_days check (carried_forward_days >= 0),
  constraint uq_leave_entitlements_tenant_id unique (tenant_id, id)
);
create unique index uq_leave_entitlements_tenant_id_employee_id_leave__0973b4b9 on hrm.leave_entitlements (tenant_id, employee_id, leave_type_id, calendar_year) where deleted_at is null;
create index ix_leave_entitlements_employee_id on hrm.leave_entitlements (tenant_id, employee_id);
create index ix_leave_entitlements_leave_type_id on hrm.leave_entitlements (tenant_id, leave_type_id);
create index ix_leave_entitlements_policy_id on hrm.leave_entitlements (tenant_id, policy_id);
comment on table hrm.leave_entitlements is '[Mongo: HRM02.employee_leave_entitlements]';

create table hrm.leave_applications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  leave_type_id uuid not null,
  from_date date not null,
  to_date date not null,
  total_days numeric(18,2) not null,
  reason text,
  contact_during_leave text,
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  cancelled_at timestamptz,
  cancel_reason text,
  substitute_employee_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_leave_applications_total_days check (total_days > 0),
  constraint ck_leave_applications_status check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'REJECTED', 'CANCELLED', 'WITHDRAWN')),
  constraint ck_leave_applications_x1 check (to_date >= from_date),
  constraint uq_leave_applications_tenant_id unique (tenant_id, id)
);
create index ix_leave_applications_tenant_id_employee_id_from_date on hrm.leave_applications (tenant_id, employee_id, from_date);
create index ix_leave_applications_tenant_id_status_from_date on hrm.leave_applications (tenant_id, status, from_date);
create index ix_leave_applications_leave_type_id on hrm.leave_applications (tenant_id, leave_type_id);
create index ix_leave_applications_workflow_instance_id on hrm.leave_applications (tenant_id, workflow_instance_id);
create index ix_leave_applications_substitute_employee_id on hrm.leave_applications (tenant_id, substitute_employee_id);
comment on table hrm.leave_applications is '[Mongo: HRM02.leave_applications, HRM02.leave_approvals, HRM02.leave_cancellations]';

create table hrm.leave_application_days (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  application_id uuid not null,
  leave_date date not null,
  day_fraction numeric(18,2) not null default 1,
  is_holiday boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_leave_application_days_x1 check (day_fraction in (0.5, 1)),
  constraint uq_leave_application_days_tenant_id unique (tenant_id, id)
);
create unique index uq_leave_application_days_tenant_id_application_id_1f9d1089 on hrm.leave_application_days (tenant_id, application_id, leave_date) where deleted_at is null;
create index ix_leave_application_days_tenant_id_leave_date on hrm.leave_application_days (tenant_id, leave_date);
create index ix_leave_application_days_application_id on hrm.leave_application_days (tenant_id, application_id);
comment on table hrm.leave_application_days is '[Mongo: HRM02.leave_application_days]';

create table hrm.leave_ledger (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  leave_type_id uuid not null,
  application_id uuid,
  entry_type text not null,
  days numeric(18,2) not null,
  effective_date date not null,
  note text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_leave_ledger_entry_type check (entry_type in ('OPENING', 'ACCRUAL', 'CONSUMED', 'CARRY_FORWARD', 'LAPSED', 'ENCASHED', 'ADJUSTMENT', 'CANCELLATION_REVERSAL')),
  constraint uq_leave_ledger_tenant_id unique (tenant_id, id)
);
create index ix_leave_ledger_tenant_id_employee_id_leave_type_i_2dc49f4a on hrm.leave_ledger (tenant_id, employee_id, leave_type_id, effective_date);
create index ix_leave_ledger_leave_type_id on hrm.leave_ledger (tenant_id, leave_type_id);
create index ix_leave_ledger_application_id on hrm.leave_ledger (tenant_id, application_id);
comment on table hrm.leave_ledger is 'Append-only ledger; balance = SUM(days) — see hrm.v_leave_balance (replaces the Mongo balances collection) [Mongo: HRM02.leave_transactions, HRM02.employee_leave_balances]';

create table hrm.shifts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  start_time time not null,
  end_time time not null,
  break_minutes integer not null default 0,
  grace_minutes integer not null default 0,
  is_night_shift boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_shifts_tenant_id unique (tenant_id, id)
);
create unique index uq_shifts_tenant_id_code on hrm.shifts (tenant_id, code) where deleted_at is null;
comment on table hrm.shifts is '[Mongo: HRM03.attendance_shift_definitions]';

create table hrm.shift_assignments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  shift_id uuid not null,
  valid_from date not null,
  valid_to date,
  weekly_pattern jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_shift_assignments_tenant_id unique (tenant_id, id)
);
create index ix_shift_assignments_tenant_id_employee_id_valid_from on hrm.shift_assignments (tenant_id, employee_id, valid_from);
create index ix_shift_assignments_shift_id on hrm.shift_assignments (tenant_id, shift_id);
comment on table hrm.shift_assignments is '[Mongo: HRM03.employee_shift_assignments, HRM03.attendance_schedules]';

create table hrm.attendance_policies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  name text not null,
  applies_to jsonb default '{}'::jsonb,
  late_after_minutes integer,
  half_day_hours numeric(18,2),
  full_day_hours numeric(18,2),
  overtime_after_hours numeric(18,2),
  lop_rule jsonb default '{}'::jsonb,
  effective_from date not null,
  effective_to date,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_attendance_policies_tenant_id unique (tenant_id, id)
);
comment on table hrm.attendance_policies is 'Effective-dated rows = versions [Mongo: HRM03.attendance_policies, HRM03.attendance_policy_versions]';

create table hrm.attendance_punches (
  id uuid not null default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  punched_at timestamptz not null,
  punch_type text not null,
  source text not null default 'BIOMETRIC',
  device_id text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_attendance_punches_punch_type check (punch_type in ('IN', 'OUT')),
  constraint ck_attendance_punches_source check (source in ('BIOMETRIC', 'MOBILE', 'WEB', 'MANUAL', 'RFID')),
  primary key (id, punched_at)
) partition by range (punched_at);
create table hrm.attendance_punches_default partition of hrm.attendance_punches default;
create index ix_attendance_punches_tenant_id_employee_id_punched_at on hrm.attendance_punches (tenant_id, employee_id, punched_at desc);
comment on table hrm.attendance_punches is 'Raw device punches, monthly partitions, no FK by design (ingest speed); reconciled into hrm.attendance_days [Mongo: HRM03.attendance_punches]';

create table hrm.attendance_days (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  attendance_date date not null,
  shift_id uuid,
  first_in timestamptz,
  last_out timestamptz,
  worked_minutes integer,
  overtime_minutes integer,
  status text not null,
  is_late boolean not null default false,
  leave_application_id uuid,
  is_regularized boolean not null default false,
  locked boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attendance_days_status check (status in ('PRESENT', 'ABSENT', 'HALF_DAY', 'LEAVE', 'HOLIDAY', 'WEEK_OFF', 'ON_DUTY', 'LOP')),
  constraint uq_attendance_days_tenant_id unique (tenant_id, id)
);
create unique index uq_attendance_days_tenant_id_employee_id_attendance_date on hrm.attendance_days (tenant_id, employee_id, attendance_date) where deleted_at is null;
create index ix_attendance_days_tenant_id_attendance_date_status on hrm.attendance_days (tenant_id, attendance_date, status);
create index ix_attendance_days_employee_id on hrm.attendance_days (tenant_id, employee_id);
create index ix_attendance_days_shift_id on hrm.attendance_days (tenant_id, shift_id);
create index ix_attendance_days_leave_application_id on hrm.attendance_days (tenant_id, leave_application_id);
comment on table hrm.attendance_days is 'ONE row per employee per day (record + calculation + daily summary). Monthly summary = view [Mongo: HRM03.attendance_records, HRM03.attendance_daily_summaries, HRM03.attendance_calculations, HRM03.attendance_adjustments]';

create table hrm.attendance_regularizations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  attendance_date date not null,
  request_type text not null,
  requested_in timestamptz,
  requested_out timestamptz,
  reason text not null,
  status text not null default 'PENDING',
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_attendance_regularizations_request_type check (request_type in ('MISSED_PUNCH', 'ON_DUTY', 'WORK_FROM_HOME', 'CORRECTION', 'ADJUSTMENT')),
  constraint ck_attendance_regularizations_status check (status in ('PENDING', 'APPROVED', 'REJECTED', 'CANCELLED')),
  constraint uq_attendance_regularizations_tenant_id unique (tenant_id, id)
);
create index ix_attendance_regularizations_tenant_id_employee_i_46f3e855 on hrm.attendance_regularizations (tenant_id, employee_id, attendance_date);
create index ix_attendance_regularizations_workflow_instance_id on hrm.attendance_regularizations (tenant_id, workflow_instance_id);
comment on table hrm.attendance_regularizations is '[Mongo: HRM03.attendance_regularizations, HRM03.attendance_regularization_approvals]';

create table hrm.performance_cycles (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  name text not null,
  academic_year_id uuid,
  start_date date not null,
  end_date date not null,
  status text not null default 'DRAFT',
  current_version_no integer not null default 1,
  rating_scale_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_performance_cycles_status check (status in ('DRAFT', 'ACTIVE', 'IN_REVIEW', 'CALIBRATION', 'CLOSED')),
  constraint uq_performance_cycles_tenant_id unique (tenant_id, id)
);
create index ix_performance_cycles_college_id on hrm.performance_cycles (tenant_id, college_id);
create index ix_performance_cycles_academic_year_id on hrm.performance_cycles (tenant_id, academic_year_id);
create index ix_performance_cycles_rating_scale_id on hrm.performance_cycles (tenant_id, rating_scale_id);
comment on table hrm.performance_cycles is '[Mongo: HRM04.performance_cycles]';

create table hrm.rating_scales (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  status text not null default 'ACTIVE',
  current_version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_rating_scales_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_rating_scales_tenant_id unique (tenant_id, id)
);
create unique index uq_rating_scales_tenant_id_code on hrm.rating_scales (tenant_id, code) where deleted_at is null;
comment on table hrm.rating_scales is '[Mongo: HRM04.performance_rating_scales]';

create table hrm.rating_scale_levels (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  scale_id uuid not null,
  level_no smallint not null,
  label text not null,
  min_score numeric(18,2),
  max_score numeric(18,2),
  descriptor text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_rating_scale_levels_level_no check (level_no > 0),
  constraint uq_rating_scale_levels_tenant_id unique (tenant_id, id)
);
create unique index uq_rating_scale_levels_tenant_id_scale_id_level_no on hrm.rating_scale_levels (tenant_id, scale_id, level_no) where deleted_at is null;
create index ix_rating_scale_levels_scale_id on hrm.rating_scale_levels (tenant_id, scale_id);
comment on table hrm.rating_scale_levels is 'Normalised from scale.levels[]';

create table hrm.competencies (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  category text,
  description text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_competencies_tenant_id unique (tenant_id, id)
);
create unique index uq_competencies_tenant_id_code on hrm.competencies (tenant_id, code) where deleted_at is null;
comment on table hrm.competencies is '[Mongo: HRM04.performance_competencies]';

create table hrm.performance_plans (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  cycle_id uuid not null,
  employee_id uuid not null,
  manager_id uuid,
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_performance_plans_status check (status in ('DRAFT', 'SUBMITTED', 'APPROVED', 'IN_PROGRESS', 'COMPLETED')),
  constraint uq_performance_plans_tenant_id unique (tenant_id, id)
);
create unique index uq_performance_plans_tenant_id_cycle_id_employee_id on hrm.performance_plans (tenant_id, cycle_id, employee_id) where deleted_at is null;
create index ix_performance_plans_cycle_id on hrm.performance_plans (tenant_id, cycle_id);
create index ix_performance_plans_employee_id on hrm.performance_plans (tenant_id, employee_id);
create index ix_performance_plans_manager_id on hrm.performance_plans (tenant_id, manager_id);
create index ix_performance_plans_workflow_instance_id on hrm.performance_plans (tenant_id, workflow_instance_id);
comment on table hrm.performance_plans is '[Mongo: HRM04.performance_plans]';

create table hrm.performance_goals (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  plan_id uuid not null,
  parent_goal_id uuid,
  goal_type text not null default 'GOAL',
  title text not null,
  description text,
  weightage_pct numeric(9,4),
  due_date date,
  progress_pct numeric(9,4),
  status text not null default 'NOT_STARTED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_performance_goals_goal_type check (goal_type in ('GOAL', 'KRA')),
  constraint ck_performance_goals_weightage_pct check (weightage_pct between 0 and 100),
  constraint ck_performance_goals_progress_pct check (progress_pct between 0 and 100),
  constraint ck_performance_goals_status check (status in ('NOT_STARTED', 'IN_PROGRESS', 'COMPLETED', 'DEFERRED', 'CANCELLED')),
  constraint uq_performance_goals_tenant_id unique (tenant_id, id)
);
create index ix_performance_goals_tenant_id_plan_id on hrm.performance_goals (tenant_id, plan_id);
create index ix_performance_goals_parent_goal_id on hrm.performance_goals (tenant_id, parent_goal_id);
comment on table hrm.performance_goals is '[Mongo: HRM04.performance_goals, HRM04.performance_kras]';

create table hrm.goal_metrics (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  goal_id uuid not null,
  name text not null,
  unit text,
  target_value numeric(18,2),
  actual_value numeric(18,2),
  weightage_pct numeric(9,4),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_goal_metrics_weightage_pct check (weightage_pct between 0 and 100),
  constraint uq_goal_metrics_tenant_id unique (tenant_id, id)
);
create index ix_goal_metrics_goal_id on hrm.goal_metrics (tenant_id, goal_id);
comment on table hrm.goal_metrics is 'KPIs of a goal/KRA [Mongo: HRM04.performance_kpis]';

create table hrm.goal_milestones (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  goal_id uuid not null,
  title text not null,
  due_date date,
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_goal_milestones_tenant_id unique (tenant_id, id)
);
create index ix_goal_milestones_goal_id on hrm.goal_milestones (tenant_id, goal_id);
comment on table hrm.goal_milestones is '[Mongo: HRM04.performance_milestones]';

create table hrm.performance_reviews (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  plan_id uuid not null,
  reviewer_employee_id uuid,
  review_type text not null,
  overall_score numeric(18,2),
  rating_level_id uuid,
  comments text,
  status text not null default 'DRAFT',
  submitted_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_performance_reviews_review_type check (review_type in ('SELF', 'MANAGER', 'PEER', 'SKIP_LEVEL', 'HR', 'FINAL')),
  constraint ck_performance_reviews_status check (status in ('DRAFT', 'SUBMITTED', 'ACKNOWLEDGED')),
  constraint uq_performance_reviews_tenant_id unique (tenant_id, id)
);
create index ix_performance_reviews_tenant_id_plan_id_review_type on hrm.performance_reviews (tenant_id, plan_id, review_type);
create index ix_performance_reviews_reviewer_employee_id on hrm.performance_reviews (tenant_id, reviewer_employee_id);
create index ix_performance_reviews_rating_level_id on hrm.performance_reviews (tenant_id, rating_level_id);
comment on table hrm.performance_reviews is 'Assessment and review merged; review_type distinguishes self/manager/peer/final [Mongo: HRM04.performance_assessments, HRM04.performance_reviews]';

create table hrm.competency_assessments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  review_id uuid not null,
  competency_id uuid not null,
  rating_level_id uuid,
  score numeric(18,2),
  comment text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_competency_assessments_tenant_id unique (tenant_id, id)
);
create unique index uq_competency_assessments_tenant_id_review_id_competency_id on hrm.competency_assessments (tenant_id, review_id, competency_id) where deleted_at is null;
create index ix_competency_assessments_review_id on hrm.competency_assessments (tenant_id, review_id);
create index ix_competency_assessments_competency_id on hrm.competency_assessments (tenant_id, competency_id);
create index ix_competency_assessments_rating_level_id on hrm.competency_assessments (tenant_id, rating_level_id);
comment on table hrm.competency_assessments is '[Mongo: HRM04.competency_assessments]';

create table hrm.performance_feedback (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  given_by_employee_id uuid,
  plan_id uuid,
  feedback_type text not null default 'CONTINUOUS',
  body text not null,
  is_anonymous boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_performance_feedback_feedback_type check (feedback_type in ('CONTINUOUS', 'PEER', 'UPWARD', 'RECOGNITION')),
  constraint uq_performance_feedback_tenant_id unique (tenant_id, id)
);
create index ix_performance_feedback_tenant_id_employee_id on hrm.performance_feedback (tenant_id, employee_id);
create index ix_performance_feedback_given_by_employee_id on hrm.performance_feedback (tenant_id, given_by_employee_id);
create index ix_performance_feedback_plan_id on hrm.performance_feedback (tenant_id, plan_id);
comment on table hrm.performance_feedback is '[Mongo: HRM04.performance_feedback]';

create table hrm.performance_calibrations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  cycle_id uuid not null,
  employee_id uuid not null,
  original_level_id uuid,
  calibrated_level_id uuid,
  reason text,
  calibrated_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_performance_calibrations_tenant_id unique (tenant_id, id)
);
create unique index uq_performance_calibrations_tenant_id_cycle_id_employee_id on hrm.performance_calibrations (tenant_id, cycle_id, employee_id) where deleted_at is null;
create index ix_performance_calibrations_cycle_id on hrm.performance_calibrations (tenant_id, cycle_id);
create index ix_performance_calibrations_employee_id on hrm.performance_calibrations (tenant_id, employee_id);
create index ix_performance_calibrations_original_level_id on hrm.performance_calibrations (tenant_id, original_level_id);
create index ix_performance_calibrations_calibrated_level_id on hrm.performance_calibrations (tenant_id, calibrated_level_id);
create index ix_performance_calibrations_calibrated_by on hrm.performance_calibrations (tenant_id, calibrated_by);
comment on table hrm.performance_calibrations is '[Mongo: HRM04.performance_calibrations]';

create table hrm.improvement_plans (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  plan_id uuid,
  plan_type text not null,
  objectives jsonb not null default '{}'::jsonb,
  start_date date not null,
  end_date date,
  status text not null default 'ACTIVE',
  outcome text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_improvement_plans_plan_type check (plan_type in ('PIP', 'DEVELOPMENT')),
  constraint ck_improvement_plans_status check (status in ('ACTIVE', 'COMPLETED', 'EXTENDED', 'TERMINATED')),
  constraint uq_improvement_plans_tenant_id unique (tenant_id, id)
);
create index ix_improvement_plans_employee_id on hrm.improvement_plans (tenant_id, employee_id);
create index ix_improvement_plans_plan_id on hrm.improvement_plans (tenant_id, plan_id);
comment on table hrm.improvement_plans is '[Mongo: HRM04.performance_improvement_plans, HRM04.performance_development_plans]';

create table hrm.requisitions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  department_id uuid,
  designation_id uuid,
  requisition_no text not null,
  positions integer not null,
  justification text,
  employment_type text,
  target_join_date date,
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  requested_by uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_requisitions_positions check (positions > 0),
  constraint ck_requisitions_status check (status in ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'REJECTED', 'FILLED', 'CANCELLED')),
  constraint uq_requisitions_tenant_id unique (tenant_id, id)
);
create unique index uq_requisitions_tenant_id_requisition_no on hrm.requisitions (tenant_id, requisition_no) where deleted_at is null;
create index ix_requisitions_college_id on hrm.requisitions (tenant_id, college_id);
create index ix_requisitions_department_id on hrm.requisitions (tenant_id, department_id);
create index ix_requisitions_designation_id on hrm.requisitions (tenant_id, designation_id);
create index ix_requisitions_workflow_instance_id on hrm.requisitions (tenant_id, workflow_instance_id);
create index ix_requisitions_requested_by on hrm.requisitions (tenant_id, requested_by);
comment on table hrm.requisitions is '[Mongo: HRM06.recruitment_requisitions, HRM06.recruitment_requisition_approvals]';

create table hrm.job_descriptions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  designation_id uuid,
  title text not null,
  summary text,
  responsibilities jsonb default '{}'::jsonb,
  qualifications jsonb default '{}'::jsonb,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_job_descriptions_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_job_descriptions_tenant_id unique (tenant_id, id)
);
create index ix_job_descriptions_designation_id on hrm.job_descriptions (tenant_id, designation_id);
comment on table hrm.job_descriptions is '[Mongo: HRM06.job_descriptions]';

create table hrm.recruitment_channels (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  channel_type text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_recruitment_channels_tenant_id unique (tenant_id, id)
);
create unique index uq_recruitment_channels_tenant_id_code on hrm.recruitment_channels (tenant_id, code) where deleted_at is null;
comment on table hrm.recruitment_channels is '[Mongo: HRM06.recruitment_channels]';

create table hrm.job_postings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  requisition_id uuid,
  job_description_id uuid,
  channel_id uuid,
  posting_no text not null,
  title text not null,
  opens_on date,
  closes_on date,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_job_postings_status check (status in ('DRAFT', 'PUBLISHED', 'CLOSED', 'CANCELLED')),
  constraint uq_job_postings_tenant_id unique (tenant_id, id)
);
create unique index uq_job_postings_tenant_id_posting_no on hrm.job_postings (tenant_id, posting_no) where deleted_at is null;
create index ix_job_postings_requisition_id on hrm.job_postings (tenant_id, requisition_id);
create index ix_job_postings_job_description_id on hrm.job_postings (tenant_id, job_description_id);
create index ix_job_postings_channel_id on hrm.job_postings (tenant_id, channel_id);
comment on table hrm.job_postings is '[Mongo: HRM06.job_postings]';

create table hrm.candidates (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  source_channel_id uuid,
  current_organisation text,
  total_experience_years numeric(9,4),
  notice_period_days integer,
  expected_ctc_range text,
  status text not null default 'ACTIVE',
  converted_employee_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_candidates_status check (status in ('ACTIVE', 'BLACKLISTED', 'HIRED', 'WITHDRAWN')),
  constraint uq_candidates_tenant_id unique (tenant_id, id)
);
create unique index uq_candidates_tenant_id_person_id on hrm.candidates (tenant_id, person_id) where deleted_at is null;
create index ix_candidates_person_id on hrm.candidates (tenant_id, person_id);
create index ix_candidates_source_channel_id on hrm.candidates (tenant_id, source_channel_id);
create index ix_candidates_converted_employee_id on hrm.candidates (tenant_id, converted_employee_id);
comment on table hrm.candidates is 'Candidate = a person in the recruitment pipeline; on hire the same person_id becomes an employee [Mongo: HRM06.candidates]';

create table hrm.candidate_applications (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  candidate_id uuid not null,
  posting_id uuid not null,
  applied_at timestamptz not null default now(),
  stage text not null default 'APPLIED',
  screening_score numeric(18,2),
  screening_notes text,
  screened_by uuid,
  selection_decision text,
  selection_rank integer,
  selected_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_candidate_applications_stage check (stage in ('APPLIED', 'SCREENED', 'INTERVIEW', 'SELECTED', 'OFFERED', 'ACCEPTED', 'REJECTED', 'WITHDRAWN', 'HIRED')),
  constraint ck_candidate_applications_selection_decision check (selection_decision in ('SELECTED', 'WAITLISTED', 'REJECTED')),
  constraint uq_candidate_applications_tenant_id unique (tenant_id, id)
);
create unique index uq_candidate_applications_tenant_id_candidate_id_posting_id on hrm.candidate_applications (tenant_id, candidate_id, posting_id) where deleted_at is null;
create index ix_candidate_applications_tenant_id_posting_id_stage on hrm.candidate_applications (tenant_id, posting_id, stage);
create index ix_candidate_applications_candidate_id on hrm.candidate_applications (tenant_id, candidate_id);
create index ix_candidate_applications_screened_by on hrm.candidate_applications (tenant_id, screened_by);
comment on table hrm.candidate_applications is '[Mongo: HRM06.candidate_applications, HRM06.candidate_screenings, HRM06.candidate_selections]';

create table hrm.interview_rounds (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  posting_id uuid not null,
  round_no smallint not null,
  name text not null,
  round_type text,
  max_score numeric(18,2),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_interview_rounds_round_no check (round_no > 0),
  constraint ck_interview_rounds_round_type check (round_type in ('SCREENING', 'TECHNICAL', 'TEACHING_DEMO', 'HR', 'PANEL')),
  constraint uq_interview_rounds_tenant_id unique (tenant_id, id)
);
create unique index uq_interview_rounds_tenant_id_posting_id_round_no on hrm.interview_rounds (tenant_id, posting_id, round_no) where deleted_at is null;
create index ix_interview_rounds_posting_id on hrm.interview_rounds (tenant_id, posting_id);
comment on table hrm.interview_rounds is '[Mongo: HRM06.interview_rounds]';

create table hrm.interviews (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  application_id uuid not null,
  round_id uuid not null,
  scheduled_at timestamptz,
  room_id uuid,
  mode text,
  status text not null default 'SCHEDULED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_interviews_mode check (mode in ('IN_PERSON', 'ONLINE')),
  constraint ck_interviews_status check (status in ('SCHEDULED', 'COMPLETED', 'CANCELLED', 'NO_SHOW')),
  constraint uq_interviews_tenant_id unique (tenant_id, id)
);
create unique index uq_interviews_tenant_id_application_id_round_id on hrm.interviews (tenant_id, application_id, round_id) where deleted_at is null;
create index ix_interviews_application_id on hrm.interviews (tenant_id, application_id);
create index ix_interviews_round_id on hrm.interviews (tenant_id, round_id);
create index ix_interviews_room_id on hrm.interviews (tenant_id, room_id);
comment on table hrm.interviews is 'NEW: concrete interview event (implied by panels/evaluations)';

create table hrm.interview_panelists (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  interview_id uuid not null,
  employee_id uuid not null,
  role text not null default 'MEMBER',
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_interview_panelists_role check (role in ('CHAIR', 'MEMBER', 'OBSERVER', 'EXTERNAL_EXPERT')),
  constraint uq_interview_panelists_tenant_id unique (tenant_id, id)
);
create unique index uq_interview_panelists_tenant_id_interview_id_employee_id on hrm.interview_panelists (tenant_id, interview_id, employee_id);
create index ix_interview_panelists_employee_id on hrm.interview_panelists (tenant_id, employee_id);
comment on table hrm.interview_panelists is '[Mongo: HRM06.interview_panels]';

create table hrm.interview_evaluations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  interview_id uuid not null,
  panelist_employee_id uuid,
  score numeric(18,2),
  recommendation text,
  criteria_scores jsonb default '{}'::jsonb,
  comments text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_interview_evaluations_recommendation check (recommendation in ('STRONG_YES', 'YES', 'NO', 'STRONG_NO')),
  constraint uq_interview_evaluations_tenant_id unique (tenant_id, id)
);
create unique index uq_interview_evaluations_tenant_id_interview_id_pa_68a07345 on hrm.interview_evaluations (tenant_id, interview_id, panelist_employee_id) where deleted_at is null;
create index ix_interview_evaluations_interview_id on hrm.interview_evaluations (tenant_id, interview_id);
create index ix_interview_evaluations_panelist_employee_id on hrm.interview_evaluations (tenant_id, panelist_employee_id);
comment on table hrm.interview_evaluations is '[Mongo: HRM06.interview_evaluations]';

create table hrm.offers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  application_id uuid not null,
  offer_no text not null,
  designation_id uuid,
  proposed_ctc numeric(18,2),
  joining_date date,
  valid_until date,
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  document_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_offers_status check (status in ('DRAFT', 'PENDING_APPROVAL', 'APPROVED', 'SENT', 'ACCEPTED', 'DECLINED', 'EXPIRED', 'WITHDRAWN')),
  constraint uq_offers_tenant_id unique (tenant_id, id)
);
create unique index uq_offers_tenant_id_offer_no on hrm.offers (tenant_id, offer_no) where deleted_at is null;
create index ix_offers_application_id on hrm.offers (tenant_id, application_id);
create index ix_offers_designation_id on hrm.offers (tenant_id, designation_id);
create index ix_offers_workflow_instance_id on hrm.offers (tenant_id, workflow_instance_id);
create index ix_offers_document_id on hrm.offers (tenant_id, document_id);
comment on table hrm.offers is '[Mongo: HRM06.candidate_offers, HRM06.offer_approvals]';

create table hrm.onboardings (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  offer_id uuid,
  employee_id uuid,
  candidate_id uuid,
  expected_join_date date,
  status text not null default 'PENDING',
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_onboardings_status check (status in ('PENDING', 'IN_PROGRESS', 'COMPLETED', 'CANCELLED')),
  constraint uq_onboardings_tenant_id unique (tenant_id, id)
);
create index ix_onboardings_offer_id on hrm.onboardings (tenant_id, offer_id);
create index ix_onboardings_employee_id on hrm.onboardings (tenant_id, employee_id);
create index ix_onboardings_candidate_id on hrm.onboardings (tenant_id, candidate_id);
comment on table hrm.onboardings is '[Mongo: HRM06.onboarding_records]';

create table hrm.onboarding_tasks (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  onboarding_id uuid not null,
  task_name text not null,
  owner_role_id uuid,
  assignee_id uuid,
  due_date date,
  requires_document boolean not null default false,
  status text not null default 'OPEN',
  completed_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_onboarding_tasks_status check (status in ('OPEN', 'DONE', 'SKIPPED')),
  constraint uq_onboarding_tasks_tenant_id unique (tenant_id, id)
);
create index ix_onboarding_tasks_onboarding_id on hrm.onboarding_tasks (tenant_id, onboarding_id);
create index ix_onboarding_tasks_owner_role_id on hrm.onboarding_tasks (tenant_id, owner_role_id);
create index ix_onboarding_tasks_assignee_id on hrm.onboarding_tasks (tenant_id, assignee_id);
comment on table hrm.onboarding_tasks is '[Mongo: HRM06.onboarding_tasks]';

create table hrm.skills (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  category text,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_skills_tenant_id unique (tenant_id, id)
);
create unique index uq_skills_tenant_id_code on hrm.skills (tenant_id, code) where deleted_at is null;
comment on table hrm.skills is '[Mongo: HRM09.training_skills]';

create table hrm.training_programs (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  description text,
  program_type text,
  provider text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_training_programs_program_type check (program_type in ('FDP', 'WORKSHOP', 'SEMINAR', 'CERTIFICATION', 'ORIENTATION', 'ONLINE')),
  constraint ck_training_programs_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_training_programs_tenant_id unique (tenant_id, id)
);
create unique index uq_training_programs_tenant_id_code on hrm.training_programs (tenant_id, code) where deleted_at is null;
comment on table hrm.training_programs is '[Mongo: HRM09.training_programs, HRM09.training_approvals]';

create table hrm.training_courses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  program_id uuid not null,
  code text not null,
  title text not null,
  duration_hours numeric(18,2),
  delivery_mode text,
  max_participants integer,
  certificate_template_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_training_courses_delivery_mode check (delivery_mode in ('CLASSROOM', 'ONLINE', 'HYBRID')),
  constraint uq_training_courses_tenant_id unique (tenant_id, id)
);
create unique index uq_training_courses_tenant_id_code on hrm.training_courses (tenant_id, code) where deleted_at is null;
create index ix_training_courses_program_id on hrm.training_courses (tenant_id, program_id);
create index ix_training_courses_certificate_template_id on hrm.training_courses (tenant_id, certificate_template_id);
comment on table hrm.training_courses is '[Mongo: HRM09.training_courses]';

create table hrm.training_course_skills (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid not null,
  skill_id uuid not null,
  target_level smallint,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_training_course_skills_tenant_id unique (tenant_id, id)
);
create unique index uq_training_course_skills_tenant_id_course_id_skill_id on hrm.training_course_skills (tenant_id, course_id, skill_id);
create index ix_training_course_skills_skill_id on hrm.training_course_skills (tenant_id, skill_id);

create table hrm.training_sessions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid not null,
  title text,
  session_date date not null,
  start_time time,
  end_time time,
  room_id uuid,
  virtual_url text,
  status text not null default 'SCHEDULED',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_training_sessions_status check (status in ('SCHEDULED', 'COMPLETED', 'CANCELLED')),
  constraint uq_training_sessions_tenant_id unique (tenant_id, id)
);
create index ix_training_sessions_tenant_id_course_id_session_date on hrm.training_sessions (tenant_id, course_id, session_date);
create index ix_training_sessions_room_id on hrm.training_sessions (tenant_id, room_id);
comment on table hrm.training_sessions is '[Mongo: HRM09.training_sessions, HRM09.training_schedules]';

create table hrm.training_trainers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  employee_id uuid,
  external_trainer_name text,
  role text not null default 'TRAINER',
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_training_trainers_x1 check (employee_id is not null or external_trainer_name is not null),
  constraint uq_training_trainers_tenant_id unique (tenant_id, id)
);
create index ix_training_trainers_session_id on hrm.training_trainers (tenant_id, session_id);
create index ix_training_trainers_employee_id on hrm.training_trainers (tenant_id, employee_id);
comment on table hrm.training_trainers is '[Mongo: HRM09.training_trainers]';

create table hrm.training_enrollments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid not null,
  employee_id uuid not null,
  nominated_by uuid,
  status text not null default 'NOMINATED',
  workflow_instance_id uuid,
  enrolled_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_training_enrollments_status check (status in ('NOMINATED', 'APPROVED', 'ENROLLED', 'COMPLETED', 'DROPPED', 'REJECTED')),
  constraint uq_training_enrollments_tenant_id unique (tenant_id, id)
);
create unique index uq_training_enrollments_tenant_id_course_id_employee_id on hrm.training_enrollments (tenant_id, course_id, employee_id) where deleted_at is null;
create index ix_training_enrollments_course_id on hrm.training_enrollments (tenant_id, course_id);
create index ix_training_enrollments_employee_id on hrm.training_enrollments (tenant_id, employee_id);
create index ix_training_enrollments_nominated_by on hrm.training_enrollments (tenant_id, nominated_by);
create index ix_training_enrollments_workflow_instance_id on hrm.training_enrollments (tenant_id, workflow_instance_id);
comment on table hrm.training_enrollments is '[Mongo: HRM09.training_nominations, HRM09.training_enrollments]';

create table hrm.training_attendance (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  session_id uuid not null,
  enrollment_id uuid not null,
  status text not null,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_training_attendance_status check (status in ('PRESENT', 'ABSENT', 'EXCUSED')),
  constraint uq_training_attendance_tenant_id unique (tenant_id, id)
);
create unique index uq_training_attendance_tenant_id_session_id_enrollment_id on hrm.training_attendance (tenant_id, session_id, enrollment_id) where deleted_at is null;
create index ix_training_attendance_session_id on hrm.training_attendance (tenant_id, session_id);
create index ix_training_attendance_enrollment_id on hrm.training_attendance (tenant_id, enrollment_id);
comment on table hrm.training_attendance is '[Mongo: HRM09.training_attendance]';

create table hrm.training_assessments (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  course_id uuid not null,
  title text not null,
  max_score numeric(18,2),
  pass_score numeric(18,2),
  max_attempts smallint,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_training_assessments_tenant_id unique (tenant_id, id)
);
create index ix_training_assessments_course_id on hrm.training_assessments (tenant_id, course_id);
comment on table hrm.training_assessments is '[Mongo: HRM09.training_assessments]';

create table hrm.training_assessment_attempts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  assessment_id uuid not null,
  enrollment_id uuid not null,
  attempt_no smallint not null default 1,
  score numeric(18,2),
  passed boolean,
  attempted_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_training_assessment_attempts_tenant_id unique (tenant_id, id)
);
create unique index uq_training_assessment_attempts_tenant_id_assessme_af69c0da on hrm.training_assessment_attempts (tenant_id, assessment_id, enrollment_id, attempt_no);
create index ix_training_assessment_attempts_enrollment_id on hrm.training_assessment_attempts (tenant_id, enrollment_id);
comment on table hrm.training_assessment_attempts is '[Mongo: HRM09.training_assessment_attempts]';

create table hrm.training_completions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  enrollment_id uuid not null,
  completed_on date not null,
  score numeric(18,2),
  result text,
  certificate_document_id uuid,
  certificate_no text,
  feedback_rating smallint,
  feedback_comment text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_training_completions_result check (result in ('PASS', 'FAIL', 'COMPLETED')),
  constraint ck_training_completions_x1 check (feedback_rating is null or feedback_rating between 1 and 5),
  constraint uq_training_completions_tenant_id unique (tenant_id, id)
);
create unique index uq_training_completions_tenant_id_enrollment_id on hrm.training_completions (tenant_id, enrollment_id) where deleted_at is null;
create index ix_training_completions_enrollment_id on hrm.training_completions (tenant_id, enrollment_id);
create index ix_training_completions_certificate_document_id on hrm.training_completions (tenant_id, certificate_document_id);
comment on table hrm.training_completions is '[Mongo: HRM09.training_completions, HRM09.training_certificates, HRM09.training_feedback]';

create table hrm.learning_paths (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  description text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_learning_paths_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_learning_paths_tenant_id unique (tenant_id, id)
);
create unique index uq_learning_paths_tenant_id_code on hrm.learning_paths (tenant_id, code) where deleted_at is null;
comment on table hrm.learning_paths is '[Mongo: HRM09.training_learning_paths]';

create table hrm.learning_path_courses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  path_id uuid not null,
  course_id uuid not null,
  seq smallint not null default 1,
  is_mandatory boolean not null default true,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_learning_path_courses_tenant_id unique (tenant_id, id)
);
create unique index uq_learning_path_courses_tenant_id_path_id_course_id on hrm.learning_path_courses (tenant_id, path_id, course_id);
create index ix_learning_path_courses_course_id on hrm.learning_path_courses (tenant_id, course_id);

create table hrm.employee_learning_paths (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  path_id uuid not null,
  assigned_on date,
  status text not null default 'ASSIGNED',
  progress_pct numeric(9,4),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_employee_learning_paths_status check (status in ('ASSIGNED', 'IN_PROGRESS', 'COMPLETED')),
  constraint ck_employee_learning_paths_progress_pct check (progress_pct between 0 and 100),
  constraint uq_employee_learning_paths_tenant_id unique (tenant_id, id)
);
create unique index uq_employee_learning_paths_tenant_id_employee_id_path_id on hrm.employee_learning_paths (tenant_id, employee_id, path_id) where deleted_at is null;
create index ix_employee_learning_paths_employee_id on hrm.employee_learning_paths (tenant_id, employee_id);
create index ix_employee_learning_paths_path_id on hrm.employee_learning_paths (tenant_id, path_id);
comment on table hrm.employee_learning_paths is '[Mongo: HRM09.employee_learning_paths]';

create table hrm.employee_skills (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  employee_id uuid not null,
  skill_id uuid not null,
  level smallint,
  progress_pct numeric(9,4),
  last_assessed_on date,
  source text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_employee_skills_progress_pct check (progress_pct between 0 and 100),
  constraint uq_employee_skills_tenant_id unique (tenant_id, id)
);
create unique index uq_employee_skills_tenant_id_employee_id_skill_id on hrm.employee_skills (tenant_id, employee_id, skill_id) where deleted_at is null;
create index ix_employee_skills_employee_id on hrm.employee_skills (tenant_id, employee_id);
create index ix_employee_skills_skill_id on hrm.employee_skills (tenant_id, skill_id);
comment on table hrm.employee_skills is '[Mongo: HRM09.employee_skill_progress]';


-- ============================================================ cam

create table cam.audience_groups (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  code text not null,
  name text not null,
  group_type text not null default 'STATIC',
  description text,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_audience_groups_group_type check (group_type in ('STATIC', 'DYNAMIC')),
  constraint ck_audience_groups_status check (status in ('ACTIVE', 'INACTIVE')),
  constraint uq_audience_groups_tenant_id unique (tenant_id, id)
);
create unique index uq_audience_groups_tenant_id_code on cam.audience_groups (tenant_id, code) where deleted_at is null;
create index ix_audience_groups_college_id on cam.audience_groups (tenant_id, college_id);
comment on table cam.audience_groups is 'Audience groups and push topics unified [Mongo: CAM01.announcement_groups, CAM04.push_topics]';

create table cam.audience_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  group_id uuid not null,
  rule_type text not null,
  criteria jsonb not null default '{}'::jsonb,
  priority integer not null default 100,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_audience_rules_tenant_id unique (tenant_id, id)
);
create index ix_audience_rules_group_id on cam.audience_rules (tenant_id, group_id);
comment on table cam.audience_rules is '[Mongo: CAM01.group_rules]';

create table cam.audience_members (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  group_id uuid not null,
  person_id uuid not null,
  source text not null default 'MANUAL',
  joined_at timestamptz not null default now(),
  left_at timestamptz,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_audience_members_source check (source in ('MANUAL', 'RULE', 'IMPORT', 'SELF_SUBSCRIBED')),
  constraint uq_audience_members_tenant_id unique (tenant_id, id)
);
create unique index uq_audience_members_tenant_id_group_id_person_id on cam.audience_members (tenant_id, group_id, person_id);
create index ix_audience_members_tenant_id_person_id on cam.audience_members (tenant_id, person_id);
comment on table cam.audience_members is '[Mongo: CAM01.group_memberships, CAM04.topic_memberships]';

create table cam.audience_snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  owner_type text not null,
  owner_id uuid not null,
  person_count integer not null,
  person_ids uuid[],
  criteria jsonb,
  frozen_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_audience_snapshots_owner_type check (owner_type in ('CAMPAIGN', 'MESSAGE', 'SURVEY', 'ALERT', 'NOTIFICATION_RULE')),
  constraint ck_audience_snapshots_person_count check (person_count >= 0),
  constraint uq_audience_snapshots_tenant_id unique (tenant_id, id)
);
create index ix_audience_snapshots_tenant_id_owner_type_owner_id on cam.audience_snapshots (tenant_id, owner_type, owner_id);
comment on table cam.audience_snapshots is 'Frozen recipient list at send time (for audit/dispute) [Mongo: CAM05.audience_snapshots, CAM06.audience_snapshots]';

create table cam.campaigns (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  name text not null,
  campaign_type text not null default 'ANNOUNCEMENT',
  audience_group_id uuid,
  scheduled_at timestamptz,
  status text not null default 'DRAFT',
  workflow_instance_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_campaigns_campaign_type check (campaign_type in ('ANNOUNCEMENT', 'REMINDER', 'MARKETING', 'ALERT', 'EVENT')),
  constraint ck_campaigns_status check (status in ('DRAFT', 'SCHEDULED', 'SENDING', 'COMPLETED', 'CANCELLED', 'FAILED')),
  constraint uq_campaigns_tenant_id unique (tenant_id, id)
);
create index ix_campaigns_college_id on cam.campaigns (tenant_id, college_id);
create index ix_campaigns_audience_group_id on cam.campaigns (tenant_id, audience_group_id);
create index ix_campaigns_workflow_instance_id on cam.campaigns (tenant_id, workflow_instance_id);
comment on table cam.campaigns is '[Mongo: CAM02.message_campaigns, CAM02.message_schedules]';

create table cam.messages (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  campaign_id uuid,
  template_id uuid,
  channel text not null,
  subject text,
  body text,
  payload jsonb default '{}'::jsonb,
  priority text not null default 'NORMAL',
  scheduled_at timestamptz,
  expires_at timestamptz,
  status text not null default 'DRAFT',
  created_by_user_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_messages_channel check (channel in ('EMAIL', 'SMS', 'PUSH', 'WHATSAPP', 'IN_APP')),
  constraint ck_messages_priority check (priority in ('LOW', 'NORMAL', 'HIGH', 'URGENT')),
  constraint ck_messages_status check (status in ('DRAFT', 'QUEUED', 'SENDING', 'SENT', 'PARTIALLY_SENT', 'FAILED', 'CANCELLED')),
  constraint uq_messages_tenant_id unique (tenant_id, id)
);
create index ix_messages_tenant_id_campaign_id on cam.messages (tenant_id, campaign_id);
create index ix_messages_tenant_id_status_scheduled_at on cam.messages (tenant_id, status, scheduled_at);
create index ix_messages_college_id on cam.messages (tenant_id, college_id);
create index ix_messages_template_id on cam.messages (tenant_id, template_id);
create index ix_messages_created_by_user_id on cam.messages (tenant_id, created_by_user_id);
comment on table cam.messages is 'One logical message per channel; push messages are channel=PUSH [Mongo: CAM02.messages, CAM04.push_messages]';

create table cam.message_recipients (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  message_id uuid not null,
  person_id uuid not null,
  address text,
  status text not null default 'PENDING',
  read_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_message_recipients_status check (status in ('PENDING', 'QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'BOUNCED', 'OPTED_OUT')),
  constraint uq_message_recipients_tenant_id unique (tenant_id, id)
);
create unique index uq_message_recipients_tenant_id_message_id_person_id on cam.message_recipients (tenant_id, message_id, person_id) where deleted_at is null;
create index ix_message_recipients_tenant_id_person_id_status on cam.message_recipients (tenant_id, person_id, status);
create index ix_message_recipients_message_id on cam.message_recipients (tenant_id, message_id);
comment on table cam.message_recipients is '[Mongo: CAM02.message_recipients]';

create table cam.providers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  channel text not null,
  provider_type text,
  priority integer not null default 100,
  secret_ref uuid,
  config jsonb default '{}'::jsonb,
  status text not null default 'ACTIVE',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_providers_channel check (channel in ('EMAIL', 'SMS', 'PUSH', 'WHATSAPP', 'VOICE')),
  constraint ck_providers_status check (status in ('ACTIVE', 'DISABLED', 'DEGRADED')),
  constraint uq_providers_tenant_id unique (tenant_id, id)
);
create unique index uq_providers_tenant_id_code on cam.providers (tenant_id, code) where deleted_at is null;
comment on table cam.providers is 'Credentials NOT stored here: secret_ref -> vault.secrets [Mongo: CAM03.providers, CAM03.provider_credentials]';

create table cam.provider_routes (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  channel text not null,
  provider_id uuid not null,
  match_rule jsonb default '{}'::jsonb,
  priority integer not null default 100,
  is_fallback boolean not null default false,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_provider_routes_tenant_id unique (tenant_id, id)
);
create index ix_provider_routes_provider_id on cam.provider_routes (tenant_id, provider_id);
comment on table cam.provider_routes is '[Mongo: CAM03.provider_routes]';

create table cam.provider_health (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  provider_id uuid not null,
  status text not null,
  latency_ms integer,
  success_rate_pct numeric(9,4),
  checked_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_provider_health_status check (status in ('HEALTHY', 'DEGRADED', 'DOWN')),
  constraint uq_provider_health_tenant_id unique (tenant_id, id)
);
create index ix_provider_health_tenant_id_provider_id_checked_at on cam.provider_health (tenant_id, provider_id, checked_at desc);
comment on table cam.provider_health is '[Mongo: CAM03.provider_health]';

create table cam.devices (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  person_id uuid not null,
  platform text not null,
  device_name text,
  app_version text,
  last_seen_at timestamptz,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_devices_platform check (platform in ('ANDROID', 'IOS', 'WEB')),
  constraint uq_devices_tenant_id unique (tenant_id, id)
);
create index ix_devices_person_id on cam.devices (tenant_id, person_id);
comment on table cam.devices is '[Mongo: CAM04.devices]';

create table cam.device_tokens (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  device_id uuid not null,
  provider_id uuid,
  token text not null,
  token_type text not null default 'FCM',
  subscription jsonb,
  is_valid boolean not null default true,
  invalidated_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_device_tokens_token_type check (token_type in ('FCM', 'APNS', 'WEBPUSH')),
  constraint uq_device_tokens_tenant_id unique (tenant_id, id)
);
create unique index uq_device_tokens_tenant_id_token on cam.device_tokens (tenant_id, token) where deleted_at is null;
create index ix_device_tokens_device_id on cam.device_tokens (tenant_id, device_id);
create index ix_device_tokens_provider_id on cam.device_tokens (tenant_id, provider_id);
comment on table cam.device_tokens is '[Mongo: CAM04.push_tokens, CAM04.push_subscriptions]';

create table cam.deliveries (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  message_id uuid,
  recipient_id uuid,
  person_id uuid,
  source_type text,
  source_id uuid,
  channel text not null,
  provider_id uuid,
  to_address text,
  provider_message_id text,
  status text not null default 'QUEUED',
  attempts integer not null default 0,
  next_attempt_at timestamptz,
  sent_at timestamptz,
  delivered_at timestamptz,
  failure_reason text,
  idempotency_key text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_deliveries_channel check (channel in ('EMAIL', 'SMS', 'PUSH', 'WHATSAPP', 'IN_APP', 'VOICE')),
  constraint ck_deliveries_status check (status in ('QUEUED', 'SENT', 'DELIVERED', 'READ', 'FAILED', 'BOUNCED', 'EXPIRED')),
  constraint uq_deliveries_tenant_id unique (tenant_id, id)
);
create unique index uq_deliveries_tenant_id_idempotency_key on cam.deliveries (tenant_id, idempotency_key) where deleted_at is null and idempotency_key is not null;
create index ix_deliveries_tenant_id_status_next_attempt_at on cam.deliveries (tenant_id, status, next_attempt_at);
create index ix_deliveries_tenant_id_message_id on cam.deliveries (tenant_id, message_id);
create index ix_deliveries_tenant_id_source_type_source_id on cam.deliveries (tenant_id, source_type, source_id);
create index ix_deliveries_recipient_id on cam.deliveries (tenant_id, recipient_id);
create index ix_deliveries_person_id on cam.deliveries (tenant_id, person_id);
create index ix_deliveries_provider_id on cam.deliveries (tenant_id, provider_id);
comment on table cam.deliveries is 'THE delivery record for every channel and every producer (messages, notifications, emergency alerts, OTP, receipts). source_type/source_id identify the producer [Mongo: CAM03.delivery_requests, CAM04.push_delivery_status, CAM05.notification_dispatches, CAM07.emergency_dispatches]';

create table cam.delivery_attempts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  delivery_id uuid not null,
  attempt_no smallint not null,
  provider_id uuid,
  status text not null,
  response_code text,
  error text,
  latency_ms integer,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_delivery_attempts_attempt_no check (attempt_no > 0),
  constraint ck_delivery_attempts_status check (status in ('SUCCESS', 'FAILED', 'TIMEOUT')),
  constraint uq_delivery_attempts_tenant_id unique (tenant_id, id)
);
create unique index uq_delivery_attempts_tenant_id_delivery_id_attempt_no on cam.delivery_attempts (tenant_id, delivery_id, attempt_no);
create index ix_delivery_attempts_provider_id on cam.delivery_attempts (tenant_id, provider_id);
comment on table cam.delivery_attempts is '[Mongo: CAM03.delivery_attempts, CAM04.push_delivery_attempts, CAM05.dispatch_attempts, CAM07.dispatch_attempts]';

create table cam.delivery_events (
  id uuid not null default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  delivery_id uuid,
  provider_id uuid,
  provider_message_id text,
  event_type text not null,
  payload jsonb,
  occurred_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_delivery_events_event_type check (event_type in ('SENT', 'DELIVERED', 'READ', 'CLICKED', 'BOUNCED', 'COMPLAINED', 'FAILED', 'OPTED_OUT')),
  primary key (id, occurred_at)
) partition by range (occurred_at);
create table cam.delivery_events_default partition of cam.delivery_events default;
create index ix_delivery_events_tenant_id_delivery_id_occurred_at on cam.delivery_events (tenant_id, delivery_id, occurred_at);
create index ix_delivery_events_tenant_id_provider_message_id on cam.delivery_events (tenant_id, provider_message_id);
create index ix_delivery_events_provider_id on cam.delivery_events (tenant_id, provider_id);
comment on table cam.delivery_events is 'Raw + normalised provider callbacks and engagement events; monthly partitions [Mongo: CAM03.delivery_callbacks, CAM03.normalized_delivery_events, CAM04.push_provider_callbacks, CAM08.analytics_events]';

create table cam.notification_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  code text not null,
  name text not null,
  event_type text not null,
  condition jsonb default '{}'::jsonb,
  template_id uuid,
  channels text[] default '{}',
  audience_group_id uuid,
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_notification_rules_tenant_id unique (tenant_id, id)
);
create unique index uq_notification_rules_tenant_id_code on cam.notification_rules (tenant_id, code) where deleted_at is null;
create index ix_notification_rules_template_id on cam.notification_rules (tenant_id, template_id);
create index ix_notification_rules_audience_group_id on cam.notification_rules (tenant_id, audience_group_id);
comment on table cam.notification_rules is '[Mongo: CAM05.notification_rules, CAM05.event_subscriptions]';

create table cam.rule_evaluations (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  rule_id uuid not null,
  event_ref text,
  matched boolean not null,
  detail jsonb,
  evaluated_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_rule_evaluations_tenant_id unique (tenant_id, id)
);
create index ix_rule_evaluations_tenant_id_rule_id_evaluated_at on cam.rule_evaluations (tenant_id, rule_id, evaluated_at desc);
comment on table cam.rule_evaluations is '[Mongo: CAM05.rule_evaluations]';

create table cam.surveys (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  title text not null,
  description text,
  survey_type text,
  audience_group_id uuid,
  opens_at timestamptz,
  closes_at timestamptz,
  is_anonymous boolean not null default false,
  status text not null default 'DRAFT',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_surveys_survey_type check (survey_type in ('FEEDBACK', 'COURSE_EXIT', 'PULSE', 'OBE_INDIRECT', 'STAKEHOLDER')),
  constraint ck_surveys_status check (status in ('DRAFT', 'OPEN', 'CLOSED', 'ARCHIVED')),
  constraint uq_surveys_tenant_id unique (tenant_id, id)
);
create index ix_surveys_college_id on cam.surveys (tenant_id, college_id);
create index ix_surveys_audience_group_id on cam.surveys (tenant_id, audience_group_id);
comment on table cam.surveys is '[Mongo: CAM06.surveys]';

create table cam.survey_questions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  survey_id uuid not null,
  seq integer not null default 1,
  question_type text not null,
  prompt text not null,
  options jsonb,
  is_required boolean not null default true,
  outcome_id uuid,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_survey_questions_question_type check (question_type in ('SINGLE', 'MULTI', 'TEXT', 'RATING', 'SCALE', 'YES_NO')),
  constraint uq_survey_questions_tenant_id unique (tenant_id, id)
);
create unique index uq_survey_questions_tenant_id_survey_id_seq on cam.survey_questions (tenant_id, survey_id, seq) where deleted_at is null;
create index ix_survey_questions_survey_id on cam.survey_questions (tenant_id, survey_id);
create index ix_survey_questions_outcome_id on cam.survey_questions (tenant_id, outcome_id);
comment on table cam.survey_questions is '[Mongo: CAM06.survey_questions]';

create table cam.survey_responses (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  survey_id uuid not null,
  respondent_person_id uuid,
  submitted_at timestamptz,
  status text not null default 'IN_PROGRESS',
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_survey_responses_status check (status in ('IN_PROGRESS', 'SUBMITTED')),
  constraint uq_survey_responses_tenant_id unique (tenant_id, id)
);
create index ix_survey_responses_survey_id on cam.survey_responses (tenant_id, survey_id);
create index ix_survey_responses_respondent_person_id on cam.survey_responses (tenant_id, respondent_person_id);
comment on table cam.survey_responses is '[Mongo: CAM06.survey_responses]';

create table cam.survey_answers (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  response_id uuid not null,
  question_id uuid not null,
  answer_text text,
  answer_value numeric(18,2),
  answer_options text[],
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_survey_answers_tenant_id unique (tenant_id, id)
);
create unique index uq_survey_answers_tenant_id_response_id_question_id on cam.survey_answers (tenant_id, response_id, question_id) where deleted_at is null;
create index ix_survey_answers_response_id on cam.survey_answers (tenant_id, response_id);
create index ix_survey_answers_question_id on cam.survey_answers (tenant_id, question_id);
comment on table cam.survey_answers is '[Mongo: CAM06.survey_answers]';

create table cam.emergency_alerts (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  college_id uuid,
  alert_type text not null,
  severity text not null default 'HIGH',
  title text not null,
  body text not null,
  audience_group_id uuid,
  triggered_by uuid,
  triggered_at timestamptz not null default now(),
  status text not null default 'ACTIVE',
  all_clear_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_emergency_alerts_alert_type check (alert_type in ('FIRE', 'WEATHER', 'SECURITY', 'MEDICAL', 'CAMPUS_CLOSURE', 'OTHER')),
  constraint ck_emergency_alerts_severity check (severity in ('LOW', 'MEDIUM', 'HIGH', 'CRITICAL')),
  constraint ck_emergency_alerts_status check (status in ('ACTIVE', 'ESCALATED', 'ALL_CLEAR', 'CANCELLED')),
  constraint uq_emergency_alerts_tenant_id unique (tenant_id, id)
);
create index ix_emergency_alerts_tenant_id_status_triggered_at on cam.emergency_alerts (tenant_id, status, triggered_at desc);
create index ix_emergency_alerts_college_id on cam.emergency_alerts (tenant_id, college_id);
create index ix_emergency_alerts_audience_group_id on cam.emergency_alerts (tenant_id, audience_group_id);
create index ix_emergency_alerts_triggered_by on cam.emergency_alerts (tenant_id, triggered_by);
comment on table cam.emergency_alerts is '[Mongo: CAM07.emergency_alerts, CAM07.emergency_recipients]';

create table cam.alert_escalation_rules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  alert_type text,
  severity text,
  after_minutes integer not null,
  escalate_to_group_id uuid,
  channels text[] default '{}',
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_alert_escalation_rules_after_minutes check (after_minutes > 0),
  constraint uq_alert_escalation_rules_tenant_id unique (tenant_id, id)
);
create index ix_alert_escalation_rules_escalate_to_group_id on cam.alert_escalation_rules (tenant_id, escalate_to_group_id);
comment on table cam.alert_escalation_rules is '[Mongo: CAM07.escalation_rules]';

create table cam.alert_acknowledgements (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  alert_id uuid not null,
  person_id uuid not null,
  acknowledged_at timestamptz not null default now(),
  status text,
  latitude numeric(18,4),
  longitude numeric(18,4),
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_alert_acknowledgements_status check (status in ('SAFE', 'NEED_HELP', 'ACKNOWLEDGED')),
  constraint uq_alert_acknowledgements_tenant_id unique (tenant_id, id)
);
create unique index uq_alert_acknowledgements_tenant_id_alert_id_person_id on cam.alert_acknowledgements (tenant_id, alert_id, person_id) where deleted_at is null;
create index ix_alert_acknowledgements_alert_id on cam.alert_acknowledgements (tenant_id, alert_id);
create index ix_alert_acknowledgements_person_id on cam.alert_acknowledgements (tenant_id, person_id);
comment on table cam.alert_acknowledgements is '[Mongo: CAM07.alert_acknowledgements]';


-- ============================================================ rpt

create table rpt.report_definitions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  module text not null,
  code text not null,
  name text not null,
  report_type text,
  query_ref text,
  default_params jsonb default '{}'::jsonb,
  output_formats text[] default '{PDF,XLSX,CSV}',
  status text not null default 'ACTIVE',
  current_version_no integer not null default 1,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint ck_report_definitions_status check (status in ('DRAFT', 'ACTIVE', 'RETIRED')),
  constraint uq_report_definitions_tenant_id unique (tenant_id, id)
);
create unique index uq_report_definitions_tenant_id_module_code on rpt.report_definitions (tenant_id, module, code) where deleted_at is null;
comment on table rpt.report_definitions is 'Parameters and dimensions are jsonb inside the definition; versions in core.entity_versions [Mongo: EXM09.report_templates, FIN08.report_definitions, FIN08.report_parameters, FIN08.report_dimensions, FIN09.audit_report_definitions, HRM08.report_definitions, ADM02.report_definitions, EXM09.report_filters, EXM09.report_access, LMS10.obe_reports]';

create table rpt.report_schedules (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  report_definition_id uuid not null,
  cron_expression text not null,
  params jsonb default '{}'::jsonb,
  recipients_group_id uuid,
  recipient_role_ids uuid[],
  is_active boolean not null default true,
  next_run_at timestamptz,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_report_schedules_tenant_id unique (tenant_id, id)
);
create index ix_report_schedules_report_definition_id on rpt.report_schedules (tenant_id, report_definition_id);
create index ix_report_schedules_recipients_group_id on rpt.report_schedules (tenant_id, recipients_group_id);
comment on table rpt.report_schedules is '[Mongo: EXM09.report_schedules, FIN08.report_schedules, FIN08.report_subscriptions, HRM08.scheduled_reports, ADM02.report_schedules]';

create table rpt.report_executions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  report_definition_id uuid not null,
  requested_by uuid,
  params jsonb default '{}'::jsonb,
  status text not null default 'QUEUED',
  document_id uuid,
  started_at timestamptz,
  finished_at timestamptz,
  row_count bigint,
  error text,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint ck_report_executions_status check (status in ('QUEUED', 'RUNNING', 'COMPLETED', 'FAILED', 'EXPIRED')),
  constraint uq_report_executions_tenant_id unique (tenant_id, id)
);
create index ix_report_executions_tenant_id_report_definition_i_7a09d686 on rpt.report_executions (tenant_id, report_definition_id, created_at desc);
create index ix_report_executions_requested_by on rpt.report_executions (tenant_id, requested_by);
create index ix_report_executions_document_id on rpt.report_executions (tenant_id, document_id);
comment on table rpt.report_executions is 'Also feeds EXM09 exam/student/subject/programme/result/attendance/valuation/script/document report collections [Mongo: EXM09.report_executions, EXM09.report_exports, FIN08.report_jobs, FIN08.report_exports, FIN08.report_deliveries, FIN09.audit_report_jobs, FIN09.audit_report_exports, ADM02.report_runs, EXM09.exam_reports, EXM09.student_reports, EXM09.subject_reports, EXM09.programme_reports, EXM09.result_reports, EXM09.attendance_reports, EXM09.valuation_reports, EXM09.script_reports, EXM09.document_reports]';

create table rpt.dashboards (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  module text not null,
  code text not null,
  name text not null,
  layout jsonb default '{}'::jsonb,
  owner_role_id uuid,
  is_default boolean not null default false,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_dashboards_tenant_id unique (tenant_id, id)
);
create unique index uq_dashboards_tenant_id_module_code on rpt.dashboards (tenant_id, module, code) where deleted_at is null;
create index ix_dashboards_owner_role_id on rpt.dashboards (tenant_id, owner_role_id);
comment on table rpt.dashboards is '[Mongo: FIN08.dashboard_definitions, HRM08.dashboard_definitions]';

create table rpt.dashboard_widgets (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  dashboard_id uuid not null,
  widget_type text not null,
  title text,
  kpi_id uuid,
  report_definition_id uuid,
  config jsonb default '{}'::jsonb,
  position jsonb default '{}'::jsonb,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_dashboard_widgets_tenant_id unique (tenant_id, id)
);
create index ix_dashboard_widgets_dashboard_id on rpt.dashboard_widgets (tenant_id, dashboard_id);
create index ix_dashboard_widgets_kpi_id on rpt.dashboard_widgets (tenant_id, kpi_id);
create index ix_dashboard_widgets_report_definition_id on rpt.dashboard_widgets (tenant_id, report_definition_id);
comment on table rpt.dashboard_widgets is '[Mongo: FIN08.dashboard_widgets]';

create table rpt.kpi_definitions (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  module text not null,
  code text not null,
  name text not null,
  unit text,
  formula text,
  source_view text,
  target_value numeric(18,2),
  threshold_warn numeric(18,2),
  threshold_crit numeric(18,2),
  is_active boolean not null default true,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  updated_by uuid,
  row_version integer not null default 1,
  deleted_at timestamptz,
  constraint uq_kpi_definitions_tenant_id unique (tenant_id, id)
);
create unique index uq_kpi_definitions_tenant_id_module_code on rpt.kpi_definitions (tenant_id, module, code) where deleted_at is null;
comment on table rpt.kpi_definitions is '[Mongo: FIN08.kpi_definitions, HRM08.kpi_definitions, CAM08.metric_definitions]';

create table rpt.kpi_values (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  kpi_id uuid not null,
  as_of date not null,
  dimension jsonb default '{}'::jsonb,
  value numeric(18,2),
  computed_at timestamptz not null default now(),
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_kpi_values_tenant_id unique (tenant_id, id)
);
create index ix_kpi_values_tenant_id_kpi_id_as_of on rpt.kpi_values (tenant_id, kpi_id, as_of desc);
comment on table rpt.kpi_values is '[Mongo: FIN08.kpi_values, HRM08.kpi_results, CAM08.metric_snapshots]';

create table rpt.snapshots (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  module text not null,
  snapshot_type text not null,
  as_of date not null,
  scope jsonb default '{}'::jsonb,
  data jsonb not null default '{}'::jsonb,
  is_frozen boolean not null default false,
  created_at timestamptz not null default now(),
  created_by uuid default auth.uid(),
  constraint uq_snapshots_tenant_id unique (tenant_id, id)
);
create index ix_snapshots_tenant_id_module_snapshot_type_as_of on rpt.snapshots (tenant_id, module, snapshot_type, as_of desc);
create index gx_snapshots_data on rpt.snapshots using gin (data);
comment on table rpt.snapshots is 'Frozen reporting snapshots (e.g. year-end closing figures). Live figures come from views [Mongo: FIN08.finance_daily_snapshots, FIN08.finance_monthly_snapshots, FIN08.finance_academic_year_snapshots, FIN09.audit_snapshots, EXM09.dashboard_metrics, ADM02.dashboard_snapshots, LMS08.progress_snapshots]';

create table rpt.refresh_status (
  id uuid primary key default gen_random_uuid(),
  tenant_id uuid not null default core.current_tenant_id(),
  view_name text not null,
  last_refreshed_at timestamptz,
  duration_ms integer,
  status text not null default 'OK',
  error text,
  created_at timestamptz not null default now(),
  updated_at timestamptz not null default now(),
  constraint ck_refresh_status_status check (status in ('OK', 'RUNNING', 'FAILED')),
  constraint uq_refresh_status_tenant_id unique (tenant_id, id)
);
create unique index uq_refresh_status_tenant_id_view_name on rpt.refresh_status (tenant_id, view_name);
comment on table rpt.refresh_status is 'Tracks materialised-view refresh (pg_cron) [Mongo: FIN08.data_refresh_status, EXM09.projection_status, EXM09.event_checkpoints]';
