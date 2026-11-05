-- Seed: 20261105000300_seed_student2_applicant.sql
-- Description: Seed student 2 applicant persona for STM-01 Phase 3b document workflows
-- Tag: STM01_PHASE3B_SEED
-- Target Ref: azulbaxszsqxymqmlwfe (Development ONLY)
-- Identity: student2.campx@gmail.com (auth UUID: bdf7a805-ff27-4ecd-862e-4c0c7b7090ae)

BEGIN;

-- 1. core.persons
INSERT INTO core.persons (
    id, tenant_id, first_name, last_name, display_name, primary_email, status, created_at, updated_at
) VALUES (
    'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae',
    'a0000000-0000-0000-0000-000000000001',
    'Student',
    'Two',
    'Student Two',
    'student2.campx@gmail.com',
    'ACTIVE',
    now(),
    now()
) ON CONFLICT (id) DO UPDATE SET
    first_name = EXCLUDED.first_name,
    last_name = EXCLUDED.last_name,
    display_name = EXCLUDED.display_name,
    primary_email = EXCLUDED.primary_email;

-- 2. iam.user_profiles
INSERT INTO iam.user_profiles (
    id, tenant_id, person_id, college_id, username, email, full_name, status, preferences, created_at, updated_at
) VALUES (
    'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae',
    'a0000000-0000-0000-0000-000000000001',
    'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae',
    'a0000000-0000-0000-0000-000000000002',
    'student2.campx',
    'student2.campx@gmail.com',
    'Student Two',
    'ACTIVE',
    '{"tag":"STM01_PHASE3B_SEED"}'::jsonb,
    now(),
    now()
) ON CONFLICT (id) DO UPDATE SET
    tenant_id = EXCLUDED.tenant_id,
    person_id = EXCLUDED.person_id,
    college_id = EXCLUDED.college_id,
    full_name = EXCLUDED.full_name,
    status = EXCLUDED.status;

-- 3. iam.role_assignments (STUDENT role)
INSERT INTO iam.role_assignments (
    id, tenant_id, user_id, role_id, college_id, valid_from, created_at, updated_at
) VALUES (
    'bdf7a805-ff27-4ecd-862e-4c0c7b700002',
    'a0000000-0000-0000-0000-000000000001',
    'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae',
    'caa7f8a2-c3eb-46b3-bf2e-ec6640289ecb',
    'a0000000-0000-0000-0000-000000000002',
    now(),
    now(),
    now()
) ON CONFLICT (id) DO NOTHING;

-- 4. stu.applications (SUBMITTED status, in open intake)
INSERT INTO stu.applications (
    tenant_id, id, application_number, intake_id, applicant_person_id,
    status, document_verification_status, payment_status, payment_amount,
    declaration_accepted, privacy_consent_accepted, applicant_data,
    submission_timestamp, created_at, updated_at, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'bdf7a805-ff27-4ecd-862e-4c0c7b700003',
    'APP-DEV-2026-0002',
    'a0000000-0000-0000-0000-000000000050',
    'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae',
    'SUBMITTED',
    'PENDING',
    'PAID',
    0.00,
    true,
    true,
    '{"tag":"STM01_PHASE3B_SEED","phone":"+1-555-0200","first_name":"Student","last_name":"Two","primary_email":"student2.campx@gmail.com"}'::jsonb,
    now(),
    now(),
    now(),
    1
) ON CONFLICT (tenant_id, id) DO UPDATE SET
    status = EXCLUDED.status,
    document_verification_status = EXCLUDED.document_verification_status,
    applicant_data = EXCLUDED.applicant_data;

-- 5. stu.application_documents (1 rejected document for re-upload testing)
INSERT INTO stu.application_documents (
    tenant_id, id, application_id, document_id, document_type, storage_path,
    file_name, mime_type, file_size_bytes, verification_status, rejection_reason,
    is_fraudulent, created_at, updated_at, row_version
) VALUES (
    'a0000000-0000-0000-0000-000000000001',
    'bdf7a805-ff27-4ecd-862e-4c0c7b700004',
    'bdf7a805-ff27-4ecd-862e-4c0c7b700003',
    'bdf7a805-ff27-4ecd-862e-4c0c7b700005',
    'HIGH_SCHOOL_TRANSCRIPT',
    'admissions/bdf7a805-ff27-4ecd-862e-4c0c7b700003/transcript_v1.pdf',
    'high_school_transcript.pdf',
    'application/pdf',
    245760,
    'REJECTED',
    'Illegible scan: text is blurred. Please provide a clear 300 DPI scan.',
    false,
    now(),
    now(),
    1
) ON CONFLICT (tenant_id, id) DO UPDATE SET
    verification_status = EXCLUDED.verification_status,
    rejection_reason = EXCLUDED.rejection_reason;

COMMIT;

