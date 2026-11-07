-- STM-03 enrolment tables. Applied 2026-11-07 to azulbaxszsqxymqmlwfe (D-RBAC-07).
CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS stu.academic_terms (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    year_code VARCHAR(16) NOT NULL,
    term_code VARCHAR(32) NOT NULL,
    name VARCHAR(120) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_academic_terms PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_academic_terms_code UNIQUE (tenant_id, year_code, term_code)
);

CREATE TABLE IF NOT EXISTS stu.registration_windows (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    term_id UUID NOT NULL,
    opens_at TIMESTAMPTZ NOT NULL,
    closes_at TIMESTAMPTZ NOT NULL,
    min_credits INTEGER NOT NULL,
    max_credits INTEGER NOT NULL,
    CONSTRAINT pk_registration_windows PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_registration_windows_term FOREIGN KEY (tenant_id, term_id) REFERENCES stu.academic_terms(tenant_id, id),
    CONSTRAINT ck_registration_window_order CHECK (closes_at > opens_at),
    CONSTRAINT ck_registration_credits CHECK (min_credits >= 0 AND max_credits >= min_credits)
);

CREATE TABLE IF NOT EXISTS stu.enrolments (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    application_id UUID NOT NULL,
    student_id UUID NOT NULL,
    program_id UUID NOT NULL,
    term_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    total_credits INTEGER NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_enrolments PRIMARY KEY (tenant_id, id)
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_enrolments_application ON stu.enrolments (tenant_id, application_id);
CREATE UNIQUE INDEX IF NOT EXISTS uq_enrolments_student_term_active ON stu.enrolments (tenant_id, student_id, term_id) WHERE status <> 'REJECTED';

CREATE TABLE IF NOT EXISTS stu.enrolment_selections (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    enrolment_id UUID NOT NULL,
    course_id UUID NOT NULL,
    section_id UUID NOT NULL,
    CONSTRAINT pk_enrolment_selections PRIMARY KEY (tenant_id, enrolment_id, course_id),
    CONSTRAINT fk_enrolment_selections FOREIGN KEY (tenant_id, enrolment_id) REFERENCES stu.enrolments(tenant_id, id)
);

ALTER TABLE stu.academic_terms ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.academic_terms FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.registration_windows ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.registration_windows FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolments ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolments FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolment_selections ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.enrolment_selections FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_academic_terms ON stu.academic_terms
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_registration_windows ON stu.registration_windows
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_enrolments ON stu.enrolments
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_enrolment_selections ON stu.enrolment_selections
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

GRANT SELECT, INSERT, UPDATE ON stu.academic_terms, stu.registration_windows, stu.enrolments, stu.enrolment_selections TO authenticated;
