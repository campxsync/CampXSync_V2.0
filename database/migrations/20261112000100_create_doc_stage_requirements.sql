-- Migration: 20261112000100_create_doc_stage_requirements.sql
-- Module: STM-06 Student Documents Service
-- User Story: STM-06-US-002 Define mandatory documents for programmes and stages
-- Persona: College Admin (scoped) / Registrar (R25, R28)

CREATE TABLE IF NOT EXISTS doc.stage_document_requirements (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    tenant_id UUID NOT NULL DEFAULT core.current_tenant_id(),
    program_id UUID REFERENCES acd.programs(id) ON DELETE RESTRICT,
    academic_stage TEXT NOT NULL,
    document_type_id UUID NOT NULL REFERENCES doc.document_types(id) ON DELETE RESTRICT,
    is_mandatory BOOLEAN NOT NULL DEFAULT true,
    min_count INTEGER NOT NULL DEFAULT 1,
    max_count INTEGER DEFAULT 1,
    submission_deadline_days INTEGER,
    description TEXT,
    is_active BOOLEAN NOT NULL DEFAULT true,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by UUID DEFAULT auth.uid(),
    updated_by UUID,
    row_version INTEGER NOT NULL DEFAULT 1,
    deleted_at TIMESTAMPTZ,
    CONSTRAINT ck_stage_document_requirements_stage CHECK (
        academic_stage IN ('ADMISSION', 'ENROLLMENT', 'ANNUAL_PROGRESSION', 'EXAMINATION', 'GRADUATION')
    ),
    CONSTRAINT ck_stage_document_requirements_counts CHECK (
        min_count >= 0 AND (max_count IS NULL OR max_count >= min_count)
    ),
    CONSTRAINT uq_stage_document_requirements_tenant_id UNIQUE (tenant_id, id)
);

-- Unique index enforcing single requirement rule per tenant, program (or institution-wide if null), stage, and document type
CREATE UNIQUE INDEX IF NOT EXISTS uq_stage_document_requirements_rule 
ON doc.stage_document_requirements (
    tenant_id, 
    COALESCE(program_id, '00000000-0000-0000-0000-000000000000'::uuid), 
    academic_stage, 
    document_type_id
) 
WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS ix_stage_document_requirements_stage_prog 
ON doc.stage_document_requirements (tenant_id, academic_stage, program_id);

CREATE INDEX IF NOT EXISTS ix_stage_document_requirements_doctype 
ON doc.stage_document_requirements (tenant_id, document_type_id);

COMMENT ON TABLE doc.stage_document_requirements IS 'Mandatory and optional document requirements per academic stage and programme for student document governance (STM-06-US-002)';

-- Enable and force Row Level Security
ALTER TABLE doc.stage_document_requirements ENABLE ROW LEVEL SECURITY;
ALTER TABLE doc.stage_document_requirements FORCE ROW LEVEL SECURITY;

-- Tenant-isolated policies matching doc catalog permissions
DO $$
BEGIN
    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'doc' AND tablename = 'stage_document_requirements' AND policyname = 'stage_document_requirements_select'
    ) THEN
        CREATE POLICY stage_document_requirements_select ON doc.stage_document_requirements
            FOR SELECT TO authenticated
            USING (
                tenant_id = (SELECT core.current_tenant_id()) 
                AND (SELECT iam.has_permission('doc.view'))
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'doc' AND tablename = 'stage_document_requirements' AND policyname = 'stage_document_requirements_insert'
    ) THEN
        CREATE POLICY stage_document_requirements_insert ON doc.stage_document_requirements
            FOR INSERT TO authenticated
            WITH CHECK (
                tenant_id = (SELECT core.current_tenant_id()) 
                AND (SELECT iam.has_permission('doc.admin'))
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'doc' AND tablename = 'stage_document_requirements' AND policyname = 'stage_document_requirements_update'
    ) THEN
        CREATE POLICY stage_document_requirements_update ON doc.stage_document_requirements
            FOR UPDATE TO authenticated
            USING (
                tenant_id = (SELECT core.current_tenant_id()) 
                AND (SELECT iam.has_permission('doc.admin'))
            )
            WITH CHECK (
                tenant_id = (SELECT core.current_tenant_id()) 
                AND (SELECT iam.has_permission('doc.admin'))
            );
    END IF;

    IF NOT EXISTS (
        SELECT 1 FROM pg_policies 
        WHERE schemaname = 'doc' AND tablename = 'stage_document_requirements' AND policyname = 'stage_document_requirements_delete'
    ) THEN
        CREATE POLICY stage_document_requirements_delete ON doc.stage_document_requirements
            FOR DELETE TO authenticated
            USING (
                tenant_id = (SELECT core.current_tenant_id()) 
                AND (SELECT iam.has_permission('doc.admin'))
            );
    END IF;
END $$;

-- Grants for application server and authenticated roles
GRANT USAGE ON SCHEMA doc TO authenticated, campx_app_server;
GRANT SELECT, INSERT, UPDATE, DELETE ON doc.stage_document_requirements TO authenticated, campx_app_server;

