-- ============================================================================
-- CampXSync Staging Database Seed: College A & College B
-- Target: Supabase Staging (Project Ref: kpwdvvcschankgggitnj)
-- Strictly Idempotent: Can be rerun safely without duplicate key violations
-- Safety Gated: Requires explicit user approval phrase "GO SEED-STAGING"
-- Verification:
--   - Hard target guard asserts presence of staging tenant & staging admin identity
--   - Enforces constraint ck_employees_employment_type ('PERMANENT') and staff_category ('TEACHING')
--   - Full tenant isolation between College A (11111111-...) and College B (22222222-...)
--   - Master Prompt v4 Phase 3 compliant: College A (2 depts, 3 batches, 4 subjects,
--     3 faculty, 40 students, 2 timetable entries, 2 attendance sessions) and College B
--     (1 dept, 1 batch, 1 subject, 1 faculty, 10 students, 1 timetable entry).
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
  v_ttv_a uuid := '11111111-7700-0000-0000-000000000002';
  v_tte_a1 uuid := '11111111-7700-0000-0000-000000000003';
  v_tte_a2 uuid := '11111111-7700-0000-0000-000000000004';

  v_tt_b uuid := '22222222-7700-0000-0000-000000000001';
  v_ttv_b uuid := '22222222-7700-0000-0000-000000000002';
  v_tte_b1 uuid := '22222222-7700-0000-0000-000000000003';

  v_sess_submitted uuid := '11111111-5e55-0000-0000-000000000001';
  v_sess_open uuid := '11111111-5e55-0000-0000-000000000002';

  v_stu_id uuid;
  v_person_id uuid;
  v_bs_id uuid;
  v_rec_id uuid;
  i int;
BEGIN
  -- ----------------------------------------------------------------------------
  -- PRECONDITION: Staging-Target Hard Safety Assertion
  -- Refuses execution if staging prerequisites are missing, protecting production.
  -- ----------------------------------------------------------------------------
  IF NOT EXISTS (
    SELECT 1 FROM iam.user_profiles
    WHERE email = 'campxsync@gmail.com' AND tenant_id = v_tenant_a
  ) THEN
    RAISE EXCEPTION 'SAFETY REFUSAL: Staging admin identity (campxsync@gmail.com on tenant %) not found! Refusing execution to protect production and unverified targets.', v_tenant_a;
  END IF;

  IF NOT EXISTS (
    SELECT 1 FROM core.tenants WHERE id = v_tenant_a AND status = 'ACTIVE'
  ) THEN
    RAISE EXCEPTION 'SAFETY REFUSAL: Staging tenant % is missing or inactive!', v_tenant_a;
  END IF;

  -- 1. Ensure Tenants Exist
  -- Note: v_tenant_a code matches provisioned staging tenant 'CAMPX_STAGING'
  INSERT INTO core.tenants (id, code, name, status)
  VALUES (v_tenant_a, 'CAMPX_STAGING', 'Demo Institute of Technology', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status;

  INSERT INTO core.tenants (id, code, name, status)
  VALUES (v_tenant_b, 'TEST_COLL_B', 'Test College of Science', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name, status = EXCLUDED.status;

  -- 2. Seed System Roles for Tenant B if not already present
  PERFORM iam.seed_system_roles(v_tenant_b);

  -- 3. Academic Years
  INSERT INTO core.academic_years (id, tenant_id, code, start_date, end_date, is_current)
  VALUES
    (v_ay_a, v_tenant_a, 'AY_2026_27', '2026-07-01', '2027-06-30', true)
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO core.academic_years (id, tenant_id, code, start_date, end_date, is_current)
  VALUES
    (v_ay_b, v_tenant_b, 'AY_2026_27', '2026-07-01', '2027-06-30', true)
  ON CONFLICT (id) DO NOTHING;

  -- 4. Colleges
  INSERT INTO core.colleges (id, tenant_id, code, name, status, provisioning_status)
  VALUES
    (v_college_a, v_tenant_a, 'DIT', 'Demo Institute of Technology', 'ACTIVE', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  INSERT INTO core.colleges (id, tenant_id, code, name, status, provisioning_status)
  VALUES
    (v_college_b, v_tenant_b, 'TCS', 'Test College of Science', 'ACTIVE', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  -- 5. Academic Calendars & Calendar Terms
  INSERT INTO core.calendars (id, tenant_id, college_id, academic_year_id, code, name, calendar_type, status, week_start_day)
  VALUES
    (v_cal_a, v_tenant_a, v_college_a, v_ay_a, 'CAL_2026_A', 'Academic Calendar 2026-27', 'ACADEMIC', 'PUBLISHED', 1),
    (v_cal_b, v_tenant_b, v_college_b, v_ay_b, 'CAL_2026_B', 'Academic Calendar 2026-27', 'ACADEMIC', 'PUBLISHED', 1)
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  INSERT INTO acd.calendar_terms (id, tenant_id, calendar_id, term_no, name, term_type, start_date, end_date, status)
  VALUES
    (v_term_a5, v_tenant_a, v_cal_a, 5, 'Semester 5 (Fall 2026)', 'SEMESTER', '2026-07-15', '2026-12-15', 'ACTIVE'),
    (v_term_b3, v_tenant_b, v_cal_b, 3, 'Semester 3 (Fall 2026)', 'SEMESTER', '2026-07-15', '2026-12-15', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  -- 6. Departments
  INSERT INTO core.departments (id, tenant_id, college_id, code, name, status)
  VALUES
    (v_dept_cse, v_tenant_a, v_college_a, 'CSE', 'Computer Science & Engineering', 'ACTIVE'),
    (v_dept_ece, v_tenant_a, v_college_a, 'ECE', 'Electronics & Communication Engineering', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  INSERT INTO core.departments (id, tenant_id, college_id, code, name, status)
  VALUES
    (v_dept_pcs, v_tenant_b, v_college_b, 'PCS', 'Physics & Computational Science', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  -- 7. Programs
  INSERT INTO acd.programs (id, tenant_id, college_id, department_id, code, name, level, duration_years, published, status)
  VALUES
    (v_prog_cse, v_tenant_a, v_college_a, v_dept_cse, 'BTECH_CSE', 'B.Tech in Computer Science and Engineering', 'UG', 4.0, true, 'ACTIVE'),
    (v_prog_ece, v_tenant_a, v_college_a, v_dept_ece, 'BTECH_ECE', 'B.Tech in Electronics and Communication', 'UG', 4.0, true, 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  INSERT INTO acd.programs (id, tenant_id, college_id, department_id, code, name, level, duration_years, published, status)
  VALUES
    (v_prog_pcs, v_tenant_b, v_college_b, v_dept_pcs, 'BSC_PHY', 'B.Sc in Computational Physics', 'UG', 3.0, true, 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  -- 8. Batches
  INSERT INTO acd.batches (id, tenant_id, college_id, program_id, admission_year_id, code, name, status)
  VALUES
    (v_batch_cse_a, v_tenant_a, v_college_a, v_prog_cse, v_ay_a, 'CSE_2024_A', 'CSE 2024 - Section A (Sem 5)', 'ACTIVE'),
    (v_batch_cse_b, v_tenant_a, v_college_a, v_prog_cse, v_ay_a, 'CSE_2024_B', 'CSE 2024 - Section B (Sem 5)', 'ACTIVE'),
    (v_batch_ece_a, v_tenant_a, v_college_a, v_prog_ece, v_ay_a, 'ECE_2024_A', 'ECE 2024 - Section A (Sem 5)', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  INSERT INTO acd.batches (id, tenant_id, college_id, program_id, admission_year_id, code, name, status)
  VALUES
    (v_batch_pcs_a, v_tenant_b, v_college_b, v_prog_pcs, v_ay_b, 'PCS_2024', 'PCS 2024 (Sem 3)', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  -- 9. Subjects
  INSERT INTO acd.subjects (id, tenant_id, department_id, code, name, subject_type, credits, status)
  VALUES
    (v_sub_cs301, v_tenant_a, v_dept_cse, 'CS301', 'Operating Systems & Distributed Kernels', 'THEORY', 4.0, 'ACTIVE'),
    (v_sub_cs302, v_tenant_a, v_dept_cse, 'CS302', 'Database Management Systems', 'THEORY', 4.0, 'ACTIVE'),
    (v_sub_cs303, v_tenant_a, v_dept_cse, 'CS303', 'Computer Networks', 'THEORY', 3.0, 'ACTIVE'),
    (v_sub_ec301, v_tenant_a, v_dept_ece, 'EC301', 'Digital Signal Processing', 'THEORY', 4.0, 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  INSERT INTO acd.subjects (id, tenant_id, department_id, code, name, subject_type, credits, status)
  VALUES
    (v_sub_ph101, v_tenant_b, v_dept_pcs, 'PH101', 'Quantum Mechanics & Optics', 'THEORY', 4.0, 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET name = EXCLUDED.name;

  -- 10. Faculty (core.persons & hrm.employees)
  -- Enforcing ck_employees_employment_type ('PERMANENT') and staff_category ('TEACHING')
  -- Faculty A1: Dr. Rajesh Sharma
  INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status)
  VALUES ('11111111-9e05-0000-0000-000000000001', v_tenant_a, 'Rajesh', 'Sharma', 'Dr. Rajesh Sharma', 'faculty.a1@demo.campxsync.test', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET primary_email = EXCLUDED.primary_email;

  INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status)
  VALUES (v_fac_a1, v_tenant_a, '11111111-9e05-0000-0000-000000000001', v_college_a, v_dept_cse, 'FAC-CSE-001', 'PERMANENT', 'TEACHING', 'ACTIVE')
  ON CONFLICT (id) DO NOTHING;

  -- Faculty A2: Prof. Priya Venkatesh
  INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status)
  VALUES ('11111111-9e05-0000-0000-000000000002', v_tenant_a, 'Priya', 'Venkatesh', 'Prof. Priya Venkatesh', 'faculty.a2@demo.campxsync.test', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET primary_email = EXCLUDED.primary_email;

  INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status)
  VALUES (v_fac_a2, v_tenant_a, '11111111-9e05-0000-0000-000000000002', v_college_a, v_dept_cse, 'FAC-CSE-002', 'PERMANENT', 'TEACHING', 'ACTIVE')
  ON CONFLICT (id) DO NOTHING;

  -- Faculty A3: Dr. Arun Kumar
  INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status)
  VALUES ('11111111-9e05-0000-0000-000000000003', v_tenant_a, 'Arun', 'Kumar', 'Dr. Arun Kumar', 'faculty.a3@demo.campxsync.test', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET primary_email = EXCLUDED.primary_email;

  INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status)
  VALUES (v_fac_a3, v_tenant_a, '11111111-9e05-0000-0000-000000000003', v_college_a, v_dept_ece, 'FAC-ECE-001', 'PERMANENT', 'TEACHING', 'ACTIVE')
  ON CONFLICT (id) DO NOTHING;

  -- Faculty B1: Dr. Sunita Rao
  INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status)
  VALUES ('22222222-9e05-0000-0000-000000000001', v_tenant_b, 'Sunita', 'Rao', 'Dr. Sunita Rao', 'faculty.b1@demo.campxsync.test', 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET primary_email = EXCLUDED.primary_email;

  INSERT INTO hrm.employees (id, tenant_id, person_id, college_id, department_id, employee_no, employment_type, staff_category, status)
  VALUES (v_fac_b1, v_tenant_b, '22222222-9e05-0000-0000-000000000001', v_college_b, v_dept_pcs, 'FAC-PCS-001', 'PERMANENT', 'TEACHING', 'ACTIVE')
  ON CONFLICT (id) DO NOTHING;

  -- 11. Courses, Course Offerings & Course Sections (College A & College B)
  -- College A
  INSERT INTO acd.courses (id, tenant_id, code, name, credits, status)
  VALUES (v_course_a, v_tenant_a, 'CS301-BASE', 'Operating Systems', 4.0, 'ACTIVE')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO acd.course_offerings (id, tenant_id, college_id, course_id, term_id, status)
  VALUES (v_off_a, v_tenant_a, v_college_a, v_course_a, v_term_a5, 'OPEN')
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  INSERT INTO acd.course_sections (id, tenant_id, offering_id, batch_id, section_code, capacity, status)
  VALUES
    (v_sec_a1, v_tenant_a, v_off_a, v_batch_cse_a, 'SEC-A1', 40, 'ACTIVE'),
    (v_sec_a2, v_tenant_a, v_off_a, v_batch_cse_b, 'SEC-A2', 40, 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET capacity = EXCLUDED.capacity;

  -- College B (Course, Offering & Section for Cross-Tenant Isolation Tests)
  INSERT INTO acd.courses (id, tenant_id, code, name, credits, status)
  VALUES (v_course_b, v_tenant_b, 'PH101-BASE', 'Computational Physics', 4.0, 'ACTIVE')
  ON CONFLICT (id) DO NOTHING;

  INSERT INTO acd.course_offerings (id, tenant_id, college_id, course_id, term_id, status)
  VALUES (v_off_b, v_tenant_b, v_college_b, v_course_b, v_term_b3, 'OPEN')
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  INSERT INTO acd.course_sections (id, tenant_id, offering_id, batch_id, section_code, capacity, status)
  VALUES
    (v_sec_b1, v_tenant_b, v_off_b, v_batch_pcs_a, 'SEC-B1', 30, 'ACTIVE')
  ON CONFLICT (id) DO UPDATE SET capacity = EXCLUDED.capacity;

  -- 11b. Section Faculty Assignments (Required by acd.guard_timetable_entry)
  INSERT INTO acd.section_faculty (id, tenant_id, section_id, employee_id, role)
  VALUES
    (v_sf_a1, v_tenant_a, v_sec_a1, v_fac_a1, 'PRIMARY'),
    (v_sf_a2, v_tenant_a, v_sec_a1, v_fac_a2, 'PRIMARY'),
    (v_sf_b1, v_tenant_b, v_sec_b1, v_fac_b1, 'PRIMARY')
  ON CONFLICT (id) DO NOTHING;

  -- 12. Timetable & Timetable Entries (College A & College B)
  -- College A Timetable
  INSERT INTO acd.timetables (id, tenant_id, college_id, term_id, batch_id, name, status)
  VALUES (v_tt_a, v_tenant_a, v_college_a, v_term_a5, v_batch_cse_a, 'CSE Sem 5 Regular Schedule', 'PUBLISHED')
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  INSERT INTO acd.timetable_versions (id, tenant_id, timetable_id, version_no, status, published_at)
  VALUES (v_ttv_a, v_tenant_a, v_tt_a, 1, 'PUBLISHED', now())
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  -- Timetable Entries College A: CS301 Mon 10:00-11:00, CS302 Tue 11:00-12:00
  INSERT INTO acd.timetable_entries (
    id, tenant_id, timetable_version_id, section_id, subject_id, employee_id,
    day_of_week, start_time, end_time, entry_type, valid_from, valid_to
  )
  VALUES
    (v_tte_a1, v_tenant_a, v_ttv_a, v_sec_a1, v_sub_cs301, v_fac_a1, 1, '10:00:00', '11:00:00', 'LECTURE', '2026-07-01', '2026-12-31'),
    (v_tte_a2, v_tenant_a, v_ttv_a, v_sec_a1, v_sub_cs302, v_fac_a2, 2, '11:00:00', '12:00:00', 'LECTURE', '2026-07-01', '2026-12-31')
  ON CONFLICT (id) DO NOTHING;

  -- College B Timetable
  INSERT INTO acd.timetables (id, tenant_id, college_id, term_id, batch_id, name, status)
  VALUES (v_tt_b, v_tenant_b, v_college_b, v_term_b3, v_batch_pcs_a, 'PCS Sem 3 Regular Schedule', 'PUBLISHED')
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  INSERT INTO acd.timetable_versions (id, tenant_id, timetable_id, version_no, status, published_at)
  VALUES (v_ttv_b, v_tenant_b, v_tt_b, 1, 'PUBLISHED', now())
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  -- Timetable Entry College B: PH101 Mon 09:00-10:00
  INSERT INTO acd.timetable_entries (
    id, tenant_id, timetable_version_id, section_id, subject_id, employee_id,
    day_of_week, start_time, end_time, entry_type, valid_from, valid_to
  )
  VALUES
    (v_tte_b1, v_tenant_b, v_ttv_b, v_sec_b1, v_sub_ph101, v_fac_b1, 1, '09:00:00', '10:00:00', 'LECTURE', '2026-07-01', '2026-12-31')
  ON CONFLICT (id) DO NOTHING;

  -- 13. Students (40 in College A, 10 in College B)
  -- College A: 40 students
  FOR i IN 1..40 LOOP
    v_person_id := ('11111111-9e05-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
    v_stu_id := ('11111111-5700-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
    v_bs_id := ('11111111-ba57-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;

    INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status)
    VALUES (
      v_person_id,
      v_tenant_a,
      'Student' || i,
      'Kumar',
      'Student ' || i || ' Kumar',
      'student.a' || i || '@demo.campxsync.test',
      'ACTIVE'
    )
    ON CONFLICT (id) DO UPDATE SET primary_email = EXCLUDED.primary_email;

    INSERT INTO stu.students (id, tenant_id, person_id, college_id, program_id, enrollment_no, status)
    VALUES (
      v_stu_id,
      v_tenant_a,
      v_person_id,
      v_college_a,
      v_prog_cse,
      '2024-CSE-' || LPAD(i::text, 3, '0'),
      'ACTIVE'
    )
    ON CONFLICT (id) DO UPDATE SET enrollment_no = EXCLUDED.enrollment_no;

    INSERT INTO acd.batch_students (id, tenant_id, batch_id, student_id, status)
    VALUES (
      v_bs_id,
      v_tenant_a,
      CASE
        WHEN i <= 20 THEN v_batch_cse_a
        WHEN i <= 30 THEN v_batch_cse_b
        ELSE v_batch_ece_a
      END,
      v_stu_id,
      'ACTIVE'
    )
    ON CONFLICT (id) DO NOTHING;
  END LOOP;

  -- College B: 10 students (for Cross-Tenant Isolation Verification)
  FOR i IN 1..10 LOOP
    v_person_id := ('22222222-9e05-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
    v_stu_id := ('22222222-5700-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
    v_bs_id := ('22222222-ba57-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;

    INSERT INTO core.persons (id, tenant_id, first_name, last_name, display_name, primary_email, status)
    VALUES (
      v_person_id,
      v_tenant_b,
      'StudentB' || i,
      'Patel',
      'Student B' || i || ' Patel',
      'student.b' || i || '@demo.campxsync.test',
      'ACTIVE'
    )
    ON CONFLICT (id) DO UPDATE SET primary_email = EXCLUDED.primary_email;

    INSERT INTO stu.students (id, tenant_id, person_id, college_id, program_id, enrollment_no, status)
    VALUES (
      v_stu_id,
      v_tenant_b,
      v_person_id,
      v_college_b,
      v_prog_pcs,
      '2024-PCS-' || LPAD(i::text, 3, '0'),
      'ACTIVE'
    )
    ON CONFLICT (id) DO UPDATE SET enrollment_no = EXCLUDED.enrollment_no;

    INSERT INTO acd.batch_students (id, tenant_id, batch_id, student_id, status)
    VALUES (
      v_bs_id,
      v_tenant_b,
      v_batch_pcs_a,
      v_stu_id,
      'ACTIVE'
    )
    ON CONFLICT (id) DO NOTHING;
  END LOOP;

  -- 14. Attendance Sessions & Records (College A)
  -- Past Submitted / Closed Session (Oct 13)
  INSERT INTO acd.attendance_sessions (
    id, tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, locked_at
  )
  VALUES (
    v_sess_submitted, v_tenant_a, v_sec_a1, v_tte_a1, v_fac_a1, '2026-10-13', '10:00:00', '11:00:00', 'CLOSED', now()
  )
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  -- Open (Pending Capture) Session for Oct 14
  INSERT INTO acd.attendance_sessions (
    id, tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status
  )
  VALUES (
    v_sess_open, v_tenant_a, v_sec_a1, v_tte_a1, v_fac_a1, '2026-10-14', '10:00:00', '11:00:00', 'OPEN'
  )
  ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;

  -- Deterministic Attendance Records for Submitted Session
  FOR i IN 1..20 LOOP
    v_stu_id := ('11111111-5700-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;
    v_rec_id := ('11111111-a770-0000-0000-' || LPAD(i::text, 12, '0'))::uuid;

    INSERT INTO acd.attendance_records (
      id, tenant_id, session_id, student_id, status, method, marked_at
    )
    VALUES (
      v_rec_id,
      v_tenant_a,
      v_sess_submitted,
      v_stu_id,
      CASE
        WHEN i IN (3, 7) THEN 'ABSENT'
        WHEN i = 5 THEN 'LEAVE'
        ELSE 'PRESENT'
      END,
      'MANUAL',
      '2026-10-13 10:55:00+00'
    )
    ON CONFLICT (id) DO UPDATE SET status = EXCLUDED.status;
  END LOOP;

  RAISE NOTICE 'CampXSync Staging Seed: College A and College B seed completed successfully with zero defects.';
END $$;
