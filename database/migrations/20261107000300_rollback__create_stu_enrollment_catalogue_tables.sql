-- Rollback for 20261107000300_create_stu_enrollment_catalogue_tables.sql
DROP POLICY IF EXISTS tenant_isolation_student_cleared_courses ON stu.student_cleared_courses;
DROP POLICY IF EXISTS tenant_isolation_enrolment_status_history ON stu.enrolment_status_history;
DROP POLICY IF EXISTS tenant_isolation_section_waitlist ON stu.section_waitlist;
DROP POLICY IF EXISTS tenant_isolation_enrolment_sections ON stu.enrolment_sections;
DROP POLICY IF EXISTS tenant_isolation_enrolment_courses ON stu.enrolment_courses;
DROP TABLE IF EXISTS stu.student_cleared_courses;
DROP TABLE IF EXISTS stu.enrolment_status_history;
DROP TABLE IF EXISTS stu.section_waitlist;
DROP TABLE IF EXISTS stu.enrolment_sections;
DROP TABLE IF EXISTS stu.enrolment_courses;
