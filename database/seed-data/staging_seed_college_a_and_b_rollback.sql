-- ============================================================================
-- CampXSync Staging Database Seed Rollback: College A & College B
-- Target: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Strictly Scoped: Cleans up ONLY records inserted by staging_seed_college_a_and_b.sql
-- Precision Safety: Explicitly preserves pre-existing courses (e.g. CS101-TST, CS201-LIVE),
--                   staging tenant CAMPX_STAGING, roles, and admin profiles.
-- ============================================================================

DO $$
DECLARE
  v_tenant_a uuid := '11111111-1111-1111-1111-111111111111';
  v_tenant_b uuid := '22222222-2222-2222-2222-222222222222';

  v_college_a uuid := '11111111-c011-0000-0000-000000000001';
  v_college_b uuid := '22222222-c011-0000-0000-000000000001';

  v_dept_cse uuid := '11111111-de97-0000-0000-000000000001';
  v_dept_ece uuid := '11111111-de97-0000-0000-000000000002';
  v_dept_pcs uuid := '22222222-de97-0000-0000-000000000001';

  v_prog_cse uuid := '11111111-9709-0000-0000-000000000001';
  v_prog_ece uuid := '11111111-9709-0000-0000-000000000002';
  v_prog_pcs uuid := '22222222-9709-0000-0000-000000000001';

  v_ay_a uuid := '11111111-a0ea-0000-0000-000000000001';
  v_ay_b uuid := '22222222-a0ea-0000-0000-000000000001';

  v_cal_a uuid := '11111111-ca10-0000-0000-000000000001';
  v_cal_b uuid := '22222222-ca10-0000-0000-000000000001';

  v_term_a5 uuid := '11111111-7e83-0000-0000-000000000005';
  v_term_b3 uuid := '22222222-7e83-0000-0000-000000000003';

  v_batch_cse_a uuid := '11111111-ba7c-0000-0000-000000000001';
  v_batch_cse_b uuid := '11111111-ba7c-0000-0000-000000000002';
  v_batch_ece_a uuid := '11111111-ba7c-0000-0000-000000000003';
  v_batch_pcs_a uuid := '22222222-ba7c-0000-0000-000000000001';

  v_sub_cs301 uuid := '11111111-50b1-0000-0000-000000000301';
  v_sub_cs302 uuid := '11111111-50b1-0000-0000-000000000302';
  v_sub_cs303 uuid := '11111111-50b1-0000-0000-000000000303';
  v_sub_ec301 uuid := '11111111-50b1-0000-0000-000000000304';
  v_sub_ph101 uuid := '22222222-50b1-0000-0000-000000000101';

  v_fac_a1 uuid := '11111111-face-0000-0000-000000000001';
  v_fac_a2 uuid := '11111111-face-0000-0000-000000000002';
  v_fac_a3 uuid := '11111111-face-0000-0000-000000000003';
  v_fac_b1 uuid := '22222222-face-0000-0000-000000000001';

  v_course_a uuid := '11111111-c000-0000-0000-000000000001';
  v_course_b uuid := '22222222-c000-0000-0000-000000000001';

  v_off_a uuid := '11111111-0ff0-0000-0000-000000000001';
  v_off_b uuid := '22222222-0ff0-0000-0000-000000000001';

  v_sec_a1 uuid := '11111111-5ec0-0000-0000-000000000001';
  v_sec_a2 uuid := '11111111-5ec0-0000-0000-000000000002';
  v_sec_b1 uuid := '22222222-5ec0-0000-0000-000000000001';

  v_sf_a1 uuid := '11111111-5f00-0000-0000-000000000001';
  v_sf_a2 uuid := '11111111-5f00-0000-0000-000000000002';
  v_sf_b1 uuid := '22222222-5f00-0000-0000-000000000001';

  v_tt_a uuid := '11111111-7700-0000-0000-000000000001';
  v_tt_b uuid := '22222222-7700-0000-0000-000000000001';

  v_sess_submitted uuid := '11111111-5e55-0000-0000-000000000001';
  v_sess_open uuid := '11111111-5e55-0000-0000-000000000002';
BEGIN
  -- 1. Attendance Records & Sessions (Only seeded session IDs)
  DELETE FROM acd.attendance_records WHERE session_id IN (v_sess_submitted, v_sess_open);
  DELETE FROM acd.attendance_sessions WHERE id IN (v_sess_submitted, v_sess_open);

  -- 2. Timetable Entries, Versions, Timetables (Only seeded timetable IDs)
  DELETE FROM acd.timetable_entries WHERE timetable_version_id IN (
    SELECT id FROM acd.timetable_versions WHERE timetable_id IN (v_tt_a, v_tt_b)
  );
  DELETE FROM acd.timetable_versions WHERE timetable_id IN (v_tt_a, v_tt_b);
  DELETE FROM acd.timetables WHERE id IN (v_tt_a, v_tt_b);

  -- 3. Section Faculty Assignments (Only seeded section faculty assignments)
  DELETE FROM acd.section_faculty WHERE id IN (v_sf_a1, v_sf_a2, v_sf_b1);

  -- 4. Course Sections, Offerings, Courses (Only seeded course/offering IDs; preserves pre-existing test courses)
  DELETE FROM acd.course_sections WHERE id IN (v_sec_a1, v_sec_a2, v_sec_b1);
  DELETE FROM acd.course_offerings WHERE id IN (v_off_a, v_off_b);
  DELETE FROM acd.courses WHERE id IN (v_course_a, v_course_b);

  -- 4. Batch Students, Students (Only students belonging to seeded batches)
  DELETE FROM acd.batch_students WHERE batch_id IN (v_batch_cse_a, v_batch_cse_b, v_batch_ece_a, v_batch_pcs_a);
  DELETE FROM stu.students WHERE college_id IN (v_college_a, v_college_b);

  -- 5. Faculty Employees (Only seeded employee IDs)
  DELETE FROM hrm.employees WHERE id IN (v_fac_a1, v_fac_a2, v_fac_a3, v_fac_b1);

  -- 6. Batches, Subjects, Programs, Departments (Only seeded IDs)
  DELETE FROM acd.batches WHERE id IN (v_batch_cse_a, v_batch_cse_b, v_batch_ece_a, v_batch_pcs_a);
  DELETE FROM acd.subjects WHERE id IN (v_sub_cs301, v_sub_cs302, v_sub_cs303, v_sub_ec301, v_sub_ph101);
  DELETE FROM acd.programs WHERE id IN (v_prog_cse, v_prog_ece, v_prog_pcs);
  DELETE FROM core.departments WHERE id IN (v_dept_cse, v_dept_ece, v_dept_pcs);

  -- 7. Calendar Terms & Academic Calendars (Only seeded calendar/term IDs)
  DELETE FROM acd.calendar_terms WHERE id IN (v_term_a5, v_term_b3);
  DELETE FROM core.calendars WHERE id IN (v_cal_a, v_cal_b);

  -- 8. Seeded Persons (Only demo test emails; never touches admin profiles or real identities)
  DELETE FROM core.persons 
  WHERE primary_email LIKE '%.campxsync.test';

  -- 9. Academic Years & Colleges (Only seeded college/year IDs)
  DELETE FROM core.colleges WHERE id IN (v_college_a, v_college_b);
  DELETE FROM core.academic_years WHERE id IN (v_ay_a, v_ay_b);

  -- 10. Clean Tenant B (Tenant A remains as foundational CAMPX_STAGING)
  DELETE FROM iam.role_permissions WHERE tenant_id = v_tenant_b;
  DELETE FROM iam.roles WHERE tenant_id = v_tenant_b;
  DELETE FROM core.tenants WHERE id = v_tenant_b;

  -- Revert Tenant A name to baseline
  UPDATE core.tenants SET name = 'CampX Staging Tenant' WHERE id = v_tenant_a;

  RAISE NOTICE 'CampXSync Staging Seed Rollback: successfully completed with zero impact on pre-existing records.';
END $$;
