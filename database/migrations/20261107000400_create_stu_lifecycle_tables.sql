-- STM-04 student lifecycle tables. Applied 2026-11-07 to azulbaxszsqxymqmlwfe only.
-- Paired rollback: 20261107000400_rollback__create_stu_lifecycle_tables.sql

CREATE EXTENSION IF NOT EXISTS pgcrypto;

CREATE TABLE IF NOT EXISTS stu.lifecycle_status (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    student_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    effective_from TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_lifecycle_status PRIMARY KEY (tenant_id, student_id),
    CONSTRAINT ck_lifecycle_status CHECK (status IN (
        'ACTIVE', 'ON_LEAVE', 'TRANSFERRED', 'WITHDRAWN', 'GRADUATED', 'SUSPENDED'
    ))
);

CREATE TABLE IF NOT EXISTS stu.lifecycle_history (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL,
    status VARCHAR(32) NOT NULL,
    action VARCHAR(64) NOT NULL,
    actor_role VARCHAR(64) NOT NULL,
    occurred_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_lifecycle_history PRIMARY KEY (tenant_id, id),
    CONSTRAINT fk_lifecycle_history_student FOREIGN KEY (tenant_id, student_id)
        REFERENCES stu.lifecycle_status(tenant_id, student_id)
);

CREATE TABLE IF NOT EXISTS stu.lifecycle_requests (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL,
    request_type VARCHAR(32) NOT NULL,
    status VARCHAR(32) NOT NULL DEFAULT 'PENDING',
    reason TEXT,
    decided_by UUID,
    decided_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_lifecycle_requests PRIMARY KEY (tenant_id, id),
    CONSTRAINT ck_lifecycle_request_type CHECK (request_type IN ('LEAVE', 'REINSTATE', 'TRANSFER', 'WITHDRAWAL')),
    CONSTRAINT ck_lifecycle_request_status CHECK (status IN ('PENDING', 'APPROVED', 'REJECTED')),
    CONSTRAINT fk_lifecycle_requests_student FOREIGN KEY (tenant_id, student_id)
        REFERENCES stu.lifecycle_status(tenant_id, student_id)
);

CREATE TABLE IF NOT EXISTS stu.lifecycle_transition_rules (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    from_status VARCHAR(32) NOT NULL,
    to_status VARCHAR(32) NOT NULL,
    requires_approval BOOLEAN NOT NULL DEFAULT TRUE,
    active BOOLEAN NOT NULL DEFAULT TRUE,
    CONSTRAINT pk_lifecycle_transition_rules PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_lifecycle_transition_rules UNIQUE (tenant_id, from_status, to_status)
);

CREATE TABLE IF NOT EXISTS stu.graduation_records (
    tenant_id UUID NOT NULL REFERENCES core.tenants(id) ON DELETE RESTRICT,
    id UUID NOT NULL DEFAULT gen_random_uuid(),
    student_id UUID NOT NULL,
    program_id UUID NOT NULL,
    graduated_on DATE NOT NULL,
    notes TEXT,
    recorded_at TIMESTAMPTZ NOT NULL DEFAULT clock_timestamp(),
    CONSTRAINT pk_graduation_records PRIMARY KEY (tenant_id, id),
    CONSTRAINT uq_graduation_student_program UNIQUE (tenant_id, student_id, program_id),
    CONSTRAINT fk_graduation_student FOREIGN KEY (tenant_id, student_id)
        REFERENCES stu.lifecycle_status(tenant_id, student_id)
);

ALTER TABLE stu.lifecycle_status ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.lifecycle_status FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.lifecycle_history ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.lifecycle_history FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.lifecycle_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.lifecycle_requests FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.lifecycle_transition_rules ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.lifecycle_transition_rules FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.graduation_records ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.graduation_records FORCE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_lifecycle_status ON stu.lifecycle_status
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_lifecycle_history ON stu.lifecycle_history
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_lifecycle_requests ON stu.lifecycle_requests
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_lifecycle_transition_rules ON stu.lifecycle_transition_rules
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

CREATE POLICY tenant_isolation_graduation_records ON stu.graduation_records
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

GRANT SELECT, INSERT, UPDATE, DELETE ON
    stu.lifecycle_status,
    stu.lifecycle_history,
    stu.lifecycle_requests,
    stu.lifecycle_transition_rules,
    stu.graduation_records
TO authenticated;
