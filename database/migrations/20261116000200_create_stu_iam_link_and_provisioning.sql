-- Migration: 20261116000200_create_stu_iam_link_and_provisioning.sql
-- Module: STM-07 Student Account Provisioning
-- Enabler: STM-07-EN-001 Supabase student-account linkage and provisioning metadata
-- Target: Development Supabase azulbaxszsqxymqmlwfe only

CREATE TABLE IF NOT EXISTS stu.student_iam_links (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT core.current_tenant_id(),
    student_id UUID NOT NULL,
    iam_user_id UUID NOT NULL,
    link_status TEXT NOT NULL DEFAULT 'ACTIVE',
    linked_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    linked_by UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID DEFAULT auth.uid(),
    updated_by UUID,
    row_version INTEGER NOT NULL DEFAULT 1,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_student_iam_links_status CHECK (link_status IN ('ACTIVE', 'REVOKED')),
    CONSTRAINT uq_student_iam_links_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_student_iam_links_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students (tenant_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_iam_links_active_student
    ON stu.student_iam_links (tenant_id, student_id)
    WHERE deleted_at IS NULL AND link_status = 'ACTIVE';

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_iam_links_active_user
    ON stu.student_iam_links (tenant_id, iam_user_id)
    WHERE deleted_at IS NULL AND link_status = 'ACTIVE';

CREATE TABLE IF NOT EXISTS stu.account_provisioning_requests (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT core.current_tenant_id(),
    student_id UUID NOT NULL,
    requested_iam_user_id UUID,
    request_status TEXT NOT NULL DEFAULT 'REQUESTED',
    failure_reason TEXT,
    requested_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    completed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID DEFAULT auth.uid(),
    updated_by UUID,
    row_version INTEGER NOT NULL DEFAULT 1,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_account_provisioning_requests_status
        CHECK (request_status IN ('REQUESTED', 'IN_PROGRESS', 'SUCCEEDED', 'FAILED', 'CANCELLED')),
    CONSTRAINT uq_account_provisioning_requests_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_account_provisioning_requests_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students (tenant_id, id) ON DELETE RESTRICT
);

CREATE INDEX IF NOT EXISTS ix_account_provisioning_requests_student
    ON stu.account_provisioning_requests (tenant_id, student_id, requested_at DESC)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE stu.student_iam_links IS
    'Durable student ↔ IAM user linkage metadata (STM-07-EN-001 / US-001)';
COMMENT ON TABLE stu.account_provisioning_requests IS
    'Provisioning request history for student IAM accounts (STM-07-EN-001)';

ALTER TABLE stu.student_iam_links ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.student_iam_links FORCE ROW LEVEL SECURITY;
ALTER TABLE stu.account_provisioning_requests ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.account_provisioning_requests FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'stu' AND tablename = 'student_iam_links'
          AND policyname = 'student_iam_links_tenant_select'
    ) THEN
        CREATE POLICY student_iam_links_tenant_select ON stu.student_iam_links
            FOR SELECT TO authenticated
            USING (tenant_id = (SELECT core.current_tenant_id()));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'stu' AND tablename = 'student_iam_links'
          AND policyname = 'student_iam_links_tenant_write'
    ) THEN
        CREATE POLICY student_iam_links_tenant_write ON stu.student_iam_links
            FOR ALL TO authenticated
            USING (tenant_id = (SELECT core.current_tenant_id()))
            WITH CHECK (tenant_id = (SELECT core.current_tenant_id()));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'stu' AND tablename = 'account_provisioning_requests'
          AND policyname = 'account_provisioning_requests_tenant_select'
    ) THEN
        CREATE POLICY account_provisioning_requests_tenant_select ON stu.account_provisioning_requests
            FOR SELECT TO authenticated
            USING (tenant_id = (SELECT core.current_tenant_id()));
    END IF;
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'stu' AND tablename = 'account_provisioning_requests'
          AND policyname = 'account_provisioning_requests_tenant_write'
    ) THEN
        CREATE POLICY account_provisioning_requests_tenant_write ON stu.account_provisioning_requests
            FOR ALL TO authenticated
            USING (tenant_id = (SELECT core.current_tenant_id()))
            WITH CHECK (tenant_id = (SELECT core.current_tenant_id()));
    END IF;
END $$;

GRANT USAGE ON SCHEMA stu TO authenticated, campx_app_server;
GRANT SELECT, INSERT, UPDATE ON stu.student_iam_links TO authenticated, campx_app_server;
GRANT SELECT, INSERT, UPDATE ON stu.account_provisioning_requests TO authenticated, campx_app_server;
