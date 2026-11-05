-- Rollback: 20261105000200_rollback__stm01_applicant_documents_rls.sql
-- Description: Rollback of STM-01 Phase 3b application documents RLS
-- Target Ref: azulbaxszsqxymqmlwfe (Development ONLY)

BEGIN;

-- 1. Restore applications policy
DROP POLICY IF EXISTS applications_student_select ON stu.applications;
DROP POLICY IF EXISTS applications_staff_all ON stu.applications;

CREATE POLICY tenant_isolation_applications ON stu.applications
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

-- 2. Restore application documents policy
DROP POLICY IF EXISTS app_documents_student_insert ON stu.application_documents;
DROP POLICY IF EXISTS app_documents_student_select ON stu.application_documents;
DROP POLICY IF EXISTS app_documents_staff_all ON stu.application_documents;

CREATE POLICY tenant_isolation_app_documents ON stu.application_documents
    FOR ALL TO authenticated
    USING (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid)
    WITH CHECK (tenant_id = (current_setting('request.jwt.claims', true)::jsonb ->> 'tenant_id')::uuid);

COMMIT;

