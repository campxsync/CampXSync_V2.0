-- ============================================================================
-- CampXSync Latency Audit — Proposed Database Indexes (REC-07)
-- STATUS: PROPOSED ONLY — STRICTLY NO EXECUTION WITHOUT EXPLICIT APPROVAL
-- Target Database: Dev Supabase azulbaxszsqxymqmlwfe (AWS Mumbai ap-south-1)
-- ============================================================================

-- 1. Student Domain (STU-01 / STM-01)
-- Accelerates student resolution by person_id, user email, and tenant
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_stu_students_tenant_person 
  ON stu.students (tenant_id, person_id) 
  WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_stu_students_enrollment_no 
  ON stu.students (tenant_id, enrollment_no) 
  WHERE deleted_at IS NULL;

-- 2. Attendance & Timetable Domain (ACD-05 / ACD-06)
-- Accelerates attendance session lookups for students across active timetable entries
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_records_student_session 
  ON acd.attendance_records (student_id, session_id, status) 
  WHERE tenant_id IS NOT NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_attendance_sessions_timetable_status 
  ON acd.attendance_sessions (timetable_entry_id, status, session_date DESC) 
  WHERE deleted_at IS NULL;

-- 3. Admissions Domain (STM-02)
-- Accelerates application lookups by applicant person and status
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_adm_applications_tenant_applicant 
  ON adm.applications (tenant_id, applicant_person_id, status) 
  WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_adm_intakes_tenant_window 
  ON adm.intakes (tenant_id, active, application_window_start, application_window_end) 
  WHERE deleted_at IS NULL;

-- 4. Identity & Access Management (Gateway & Auth)
-- Accelerates platform admin resolution and role lookup
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_plat_admins_user_status 
  ON plat.platform_admins (user_id, role_code, status) 
  WHERE deleted_at IS NULL;

CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_iam_roles_tenant_user 
  ON iam.role_assignments (tenant_id, user_id, role_id) 
  WHERE deleted_at IS NULL;

-- 5. Enrollment Domain (STM-03)
-- Accelerates term enrolment lookups for student
CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_enr_enrolments_student_term 
  ON enr.enrolments (student_id, term_id, status) 
  WHERE tenant_id IS NOT NULL;
