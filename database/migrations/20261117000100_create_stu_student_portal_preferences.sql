-- Migration: 20261117000100_create_stu_student_portal_preferences.sql
-- Module: STM-10 Student Portal & Experience
-- Enabler: STM-10-EN-001 Supabase persistence for portal-owned preferences/configuration only
-- Owner decision D7 (17-11-2026): student-service schema, college column, FORCE RLS, own-row only
-- Target: Development Supabase azulbaxszsqxymqmlwfe only

CREATE TABLE IF NOT EXISTS stu.student_portal_preferences (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT core.current_tenant_id(),
    college_id UUID NOT NULL,
    student_id UUID NOT NULL,
    preferences_json JSONB NOT NULL DEFAULT '{}'::jsonb,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID DEFAULT auth.uid(),
    updated_by UUID,
    row_version INTEGER NOT NULL DEFAULT 1,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT uq_student_portal_preferences_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_student_portal_preferences_student
        FOREIGN KEY (tenant_id, student_id) REFERENCES stu.students (tenant_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_student_portal_preferences_active_student_college
    ON stu.student_portal_preferences (tenant_id, college_id, student_id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_student_portal_preferences_student
    ON stu.student_portal_preferences (tenant_id, student_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE stu.student_portal_preferences IS
    'Per-student, per-college portal preferences (widgets, shortcuts, language/display) — STM-10-EN-001';

ALTER TABLE stu.student_portal_preferences ENABLE ROW LEVEL SECURITY;
ALTER TABLE stu.student_portal_preferences FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'stu' AND tablename = 'student_portal_preferences'
          AND policyname = 'student_portal_preferences_own_select'
    ) THEN
        CREATE POLICY student_portal_preferences_own_select ON stu.student_portal_preferences
            FOR SELECT TO authenticated
            USING (
                tenant_id = (SELECT core.current_tenant_id())
                AND student_id IN (
                    SELECT l.student_id
                    FROM stu.student_iam_links l
                    WHERE l.tenant_id = stu.student_portal_preferences.tenant_id
                      AND l.iam_user_id = auth.uid()
                      AND l.link_status = 'ACTIVE'
                      AND l.deleted_at IS NULL
                )
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies WHERE schemaname = 'stu' AND tablename = 'student_portal_preferences'
          AND policyname = 'student_portal_preferences_own_write'
    ) THEN
        CREATE POLICY student_portal_preferences_own_write ON stu.student_portal_preferences
            FOR ALL TO authenticated
            USING (
                tenant_id = (SELECT core.current_tenant_id())
                AND student_id IN (
                    SELECT l.student_id
                    FROM stu.student_iam_links l
                    WHERE l.tenant_id = stu.student_portal_preferences.tenant_id
                      AND l.iam_user_id = auth.uid()
                      AND l.link_status = 'ACTIVE'
                      AND l.deleted_at IS NULL
                )
            )
            WITH CHECK (
                tenant_id = (SELECT core.current_tenant_id())
                AND student_id IN (
                    SELECT l.student_id
                    FROM stu.student_iam_links l
                    WHERE l.tenant_id = stu.student_portal_preferences.tenant_id
                      AND l.iam_user_id = auth.uid()
                      AND l.link_status = 'ACTIVE'
                      AND l.deleted_at IS NULL
                )
            );
    END IF;
END $$;

GRANT USAGE ON SCHEMA stu TO authenticated, campx_app_server;
GRANT SELECT, INSERT, UPDATE ON stu.student_portal_preferences TO authenticated, campx_app_server;
