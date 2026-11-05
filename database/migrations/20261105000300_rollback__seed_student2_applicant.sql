-- Rollback: 20261105000300_rollback__seed_student2_applicant.sql
-- Description: Rollback of student 2 applicant persona
-- Target Ref: azulbaxszsqxymqmlwfe (Development ONLY)

BEGIN;

DELETE FROM stu.application_documents WHERE id = 'bdf7a805-ff27-4ecd-862e-4c0c7b700004';
DELETE FROM stu.applications WHERE id = 'bdf7a805-ff27-4ecd-862e-4c0c7b700003';
DELETE FROM iam.role_assignments WHERE user_id = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae';
DELETE FROM iam.user_profiles WHERE id = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae';
DELETE FROM core.persons WHERE id = 'bdf7a805-ff27-4ecd-862e-4c0c7b7090ae';

COMMIT;

