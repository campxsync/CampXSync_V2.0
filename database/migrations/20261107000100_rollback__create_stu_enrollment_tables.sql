-- Rollback for 20261107000100_create_stu_enrollment_tables.sql. Not applied.
DROP POLICY IF EXISTS tenant_isolation_enrolment_selections ON stu.enrolment_selections;
DROP POLICY IF EXISTS tenant_isolation_enrolments ON stu.enrolments;
DROP POLICY IF EXISTS tenant_isolation_registration_windows ON stu.registration_windows;
DROP POLICY IF EXISTS tenant_isolation_academic_terms ON stu.academic_terms;
DROP TABLE IF EXISTS stu.enrolment_selections;
DROP TABLE IF EXISTS stu.enrolments;
DROP TABLE IF EXISTS stu.registration_windows;
DROP TABLE IF EXISTS stu.academic_terms;
