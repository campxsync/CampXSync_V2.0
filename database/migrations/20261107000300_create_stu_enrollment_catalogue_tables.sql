-- STM-03 catalogue / waitlist / history addendum for user-story completion.
-- Target: azulbaxszsqxymqmlwfe only. Paired rollback: 20261107000300_rollback__create_stu_enrollment_catalogue_tables.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS stu.enrolment_courses (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    program_id UUID NOT NULL,
    term_id UUID NOT NULL,
    code VARCHAR(32) NOT NULL,
    credits INTEGER NOT NULL,
    is_core BOOLEAN NOT NULL DEFAULT FALSE,
    prerequisites UUID[] NOT NULL DEFAULT '{}',
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_enrolment_courses PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_enrolment_courses_code UNIQUE (tenant_id, program_id, term_id, code),
    CONSTRAINT ck_enrolment_courses_credits CHECK (credits > 0),
    CONSTRAINT fk_enrolment_courses_term FOREIGN KEY (tenant_id, term_id) REFERENCES stu.academic_terms(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS stu.enrolment_sections (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    course_id UUID NOT NULL,
    capacity INTEGER NOT NULL,
    enrolled_count INTEGER NOT NULL DEFAULT 0,
    faculty_user_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_enrolment_sections PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_enrolment_sections_capacity CHECK (capacity > 0 AND enrolled_count >= 0 AND enrolled_count <= capacity),
    CONSTRAINT fk_enrolment_sections_course FOREIGN KEY (tenant_id, course_id) REFERENCES stu.enrolment_courses(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS stu.section_waitlist (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    section_id UUID NOT NULL,
    student_id UUID NOT NULL,
    position INTEGER NOT NULL,
    joined_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_section_waitlist PRIMARY KEY (tenant_id, section_id, student_id),
    CONSTRAINT uq_section_waitlist_position UNIQUE (tenant_id, section_id, position),
    CONSTRAINT fk_section_waitlist_section FOREIGN KEY (tenant_id, section_id) REFERENCES stu.enrolment_sections(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS stu.enrolment_status_history (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    enrolment_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_enrolment_status_history PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_enrolment_status_history_enrolment FOREIGN KEY (tenant_id, enrolment_id) REFERENCES stu.enrolments(tenant_id, id)
);

CREATE TABLE IF NOT EXISTS stu.student_cleared_courses (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL,
    course_id UUID NOT NULL,
    cleared_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_student_cleared_courses PRIMARY KEY (tenant_id, student_id, course_id)
);

ALTER TABLE stu.enrolment_courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolment_courses FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolment_sections ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolment_sections FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.section_waitlist ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.section_waitlist FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolment_status_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolment_status_history FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.student_cleared_courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.student_cleared_courses FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_enrolment_courses ON stu.enrolment_courses
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_enrolment_sections ON stu.enrolment_sections
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_section_waitlist ON stu.section_waitlist
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_enrolment_status_history ON stu.enrolment_status_history
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_student_cleared_courses ON stu.student_cleared_courses
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON stu.enrolment_courses, stu.enrolment_sections, stu.section_waitlist, stu.enrolment_status_history, stu.student_cleared_courses TO authenticated;
