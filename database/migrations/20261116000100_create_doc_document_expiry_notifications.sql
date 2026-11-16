-- Migration: 20261116000100_create_doc_document_expiry_notifications.sql
-- Module: STM-06 Student Documents Service
-- User Story: STM-06-US-015 Receive document-expiry notifications
-- Target: Development Supabase azulbaxszsqxymqmlwfe only
-- Persona: Registrar (R31) in-app inbox (not email/SMS/push)

CREATE TABLE IF NOT EXISTS doc.document_expiry_notifications (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT core.current_tenant_id(),
    college_id UUID,
    user_id UUID NOT NULL,
    document_id UUID NOT NULL,
    notification_type TEXT NOT NULL DEFAULT 'DOCUMENT_EXPIRING',
    document_expires_at TIMESTAMPTZ,
    read_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID DEFAULT auth.uid(),
    updated_by UUID,
    row_version INTEGER NOT NULL DEFAULT 1,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_document_expiry_notifications_type
        CHECK (notification_type IN ('DOCUMENT_EXPIRING')),
    CONSTRAINT uq_document_expiry_notifications_tenant_id UNIQUE (tenant_id, id),
    CONSTRAINT fk_document_expiry_notifications_document
        FOREIGN KEY (tenant_id, document_id) REFERENCES doc.documents (tenant_id, id) ON DELETE RESTRICT
);

CREATE UNIQUE INDEX IF NOT EXISTS uq_document_expiry_notifications_recipient_doc_type
    ON doc.document_expiry_notifications (tenant_id, user_id, document_id, notification_type)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_document_expiry_notifications_tenant_user_read
    ON doc.document_expiry_notifications (tenant_id, user_id, read_at)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_document_expiry_notifications_document
    ON doc.document_expiry_notifications (tenant_id, document_id)
    WHERE deleted_at IS NULL;

COMMENT ON TABLE doc.document_expiry_notifications IS
    'Registrar in-app document expiry notifications (STM-06-US-015); not email/SMS/push';

ALTER TABLE doc.document_expiry_notifications ENABLE ROW LEVEL SECURITY;
ALTER TABLE doc.document_expiry_notifications FORCE ROW LEVEL SECURITY;

DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'doc' AND tablename = 'document_expiry_notifications'
          AND policyname = 'document_expiry_notifications_select'
    ) THEN
        CREATE POLICY document_expiry_notifications_select ON doc.document_expiry_notifications
            FOR SELECT TO authenticated
            USING (
                tenant_id = (SELECT core.current_tenant_id())
                AND user_id = auth.uid()
                AND (SELECT iam.has_permission('doc.view'))
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'doc' AND tablename = 'document_expiry_notifications'
          AND policyname = 'document_expiry_notifications_insert'
    ) THEN
        CREATE POLICY document_expiry_notifications_insert ON doc.document_expiry_notifications
            FOR INSERT TO authenticated
            WITH CHECK (
                tenant_id = (SELECT core.current_tenant_id())
                AND user_id = auth.uid()
                AND (SELECT iam.has_permission('doc.view'))
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies
        WHERE schemaname = 'doc' AND tablename = 'document_expiry_notifications'
          AND policyname = 'document_expiry_notifications_update'
    ) THEN
        CREATE POLICY document_expiry_notifications_update ON doc.document_expiry_notifications
            FOR UPDATE TO authenticated
            USING (
                tenant_id = (SELECT core.current_tenant_id())
                AND user_id = auth.uid()
                AND (SELECT iam.has_permission('doc.view'))
            )
            WITH CHECK (
                tenant_id = (SELECT core.current_tenant_id())
                AND user_id = auth.uid()
                AND (SELECT iam.has_permission('doc.view'))
            );
    END IF;
END $$;

GRANT USAGE ON SCHEMA doc TO authenticated, campx_app_server;
GRANT SELECT, INSERT, UPDATE ON doc.document_expiry_notifications TO authenticated, campx_app_server;
