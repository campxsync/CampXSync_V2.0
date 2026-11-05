-- Migration: 20261105000200_stm01_applicant_documents_rls.sql
-- Description: STM-01 Phase 3b - Scoped RLS for Application Documents & Applications
-- Target Ref: azulbaxszsqxymqmlwfe (Development ONLY)
-- Enforces:
--   1. Applicants (student role) can SELECT and INSERT documents ONLY for their own applications.
--   2. Forbids applicant modification of verification_status (must be PENDING), verified_by, verified_at, rejection_reason, fraud flags.
--   3. Restricts document upload to active applications in open intake windows.
--   4. Preserves staff and service access for admissions workflows and verification.

BEGIN;

-- 1. Applications table: replace tenant-wide policy with staff + student ownership policies
DROP POLICY IF EXISTS tenant_isolation_applications ON stu.applications;
DROP POLICY IF EXISTS applications_staff_all ON stu.applications;
DROP POLICY IF EXISTS applications_student_select ON stu.applications;

CREATE POLICY applications_staff_all ON stu.applications
    FOR ALL TO authenticated
    USING (
        tenant_id = (SELECT core.current_tenant_id())
        AND (
            auth.uid() IS NULL
            OR (SELECT iam.has_permission('core.write'))
            OR (SELECT iam.has_permission('adm01.manage'))
            OR (SELECT iam.has_permission('adm01.write'))
            OR (SELECT iam.has_permission('adm01.view'))
        )
    )
    WITH CHECK (
        tenant_id = (SELECT core.current_tenant_id())
        AND (
            auth.uid() IS NULL
            OR (SELECT iam.has_permission('core.write'))
            OR (SELECT iam.has_permission('adm01.manage'))
            OR (SELECT iam.has_permission('adm01.write'))
        )
    );

CREATE POLICY applications_student_select ON stu.applications
    FOR SELECT TO authenticated
    USING (
        tenant_id = (SELECT core.current_tenant_id())
        AND applicant_person_id = auth.uid()
    );

-- 2. Application documents table: replace tenant-wide policy with staff + applicant ownership policies
DROP POLICY IF EXISTS tenant_isolation_app_documents ON stu.application_documents;
DROP POLICY IF EXISTS app_documents_staff_all ON stu.application_documents;
DROP POLICY IF EXISTS app_documents_student_select ON stu.application_documents;
DROP POLICY IF EXISTS app_documents_student_insert ON stu.application_documents;

CREATE POLICY app_documents_staff_all ON stu.application_documents
    FOR ALL TO authenticated
    USING (
        tenant_id = (SELECT core.current_tenant_id())
        AND (
            auth.uid() IS NULL
            OR (SELECT iam.has_permission('core.write'))
            OR (SELECT iam.has_permission('adm01.manage'))
            OR (SELECT iam.has_permission('adm01.write'))
            OR (SELECT iam.has_permission('adm01.view'))
        )
    )
    WITH CHECK (
        tenant_id = (SELECT core.current_tenant_id())
        AND (
            auth.uid() IS NULL
            OR (SELECT iam.has_permission('core.write'))
            OR (SELECT iam.has_permission('adm01.manage'))
            OR (SELECT iam.has_permission('adm01.write'))
        )
    );

CREATE POLICY app_documents_student_select ON stu.application_documents
    FOR SELECT TO authenticated
    USING (
        tenant_id = (SELECT core.current_tenant_id())
        AND application_id IN (
            SELECT id FROM stu.applications
            WHERE applicant_person_id = auth.uid()
              AND tenant_id = (SELECT core.current_tenant_id())
        )
    );

CREATE POLICY app_documents_student_insert ON stu.application_documents
    FOR INSERT TO authenticated
    WITH CHECK (
        tenant_id = (SELECT core.current_tenant_id())
        AND application_id IN (
            SELECT a.id FROM stu.applications a
            JOIN stu.admission_intakes i ON i.id = a.intake_id AND i.tenant_id = a.tenant_id
            WHERE a.applicant_person_id = auth.uid()
              AND a.tenant_id = (SELECT core.current_tenant_id())
              AND a.status IN ('DRAFT', 'SUBMITTED', 'UNDER_REVIEW')
              AND i.is_active = true
              AND (i.application_window_start IS NULL OR now() >= i.application_window_start)
              AND (i.application_window_end IS NULL OR now() <= i.application_window_end)
        )
        AND verification_status = 'PENDING'
        AND verified_by IS NULL
        AND verified_at IS NULL
        AND rejection_reason IS NULL
        AND is_fraudulent = false
        AND fraud_notes IS NULL
    );

COMMIT;
