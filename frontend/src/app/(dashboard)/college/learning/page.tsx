import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { LearningManager } from './learning-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeLearningPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') {
    redirect('/access-denied?reason=forbidden');
  }
  const tenantId = resolution.session.tenantId;
  const collegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');

  const [subjects, departments, faculty, programs, students, courses, contents, lessons, assignments, classrooms, progress, badges, certificates, outcomes] = await Promise.all([
    query(`SELECT id, code, name FROM acd.subjects WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`, [tenantId]),
    query(`SELECT id, code, name FROM core.departments WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR college_id = $2) ORDER BY code`, [tenantId, collegeId]),
    query(
      `SELECT e.id, e.employee_no, p.display_name
       FROM hrm.employees e
       LEFT JOIN core.persons p ON p.id = e.person_id
       WHERE e.tenant_id = $1 AND e.deleted_at IS NULL AND ($2::uuid IS NULL OR e.college_id = $2)
       ORDER BY e.employee_no`,
      [tenantId, collegeId]
    ),
    query(`SELECT id, code, name FROM acd.programs WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR college_id = $2) ORDER BY code`, [tenantId, collegeId]),
    query(
      `SELECT s.id, s.enrollment_no, p.display_name
       FROM stu.students s
       LEFT JOIN core.persons p ON p.id = s.person_id
       WHERE s.tenant_id = $1 AND s.deleted_at IS NULL AND ($2::uuid IS NULL OR s.college_id = $2)
       ORDER BY s.enrollment_no`,
      [tenantId, collegeId]
    ),
    query(`SELECT id, code, name, status, credits FROM acd.courses WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`, [tenantId]),
    query(
      `SELECT c.id, c.title, c.status, s.code AS subject_code, m.title AS module_title, t.title AS topic_title, t.content_type
       FROM lms.contents c
       LEFT JOIN acd.subjects s ON s.id = c.subject_id
       LEFT JOIN lms.content_modules m ON m.content_id = c.id AND m.deleted_at IS NULL
       LEFT JOIN lms.content_topics t ON t.module_id = m.id AND t.deleted_at IS NULL
       WHERE c.tenant_id = $1 AND c.deleted_at IS NULL
       ORDER BY c.created_at DESC`,
      [tenantId]
    ),
    query(
      `SELECT lp.id, lp.title, lp.status, lp.planned_hours, s.code AS subject_code, e.employee_no, p.display_name,
              ls.planned_date::text AS planned_date, ls.status AS session_status
       FROM lms.lesson_plans lp
       LEFT JOIN acd.subjects s ON s.id = lp.subject_id
       LEFT JOIN hrm.employees e ON e.id = lp.employee_id
       LEFT JOIN core.persons p ON p.id = e.person_id
       LEFT JOIN lms.lesson_sessions ls ON ls.plan_id = lp.id AND ls.deleted_at IS NULL
       WHERE lp.tenant_id = $1 AND lp.deleted_at IS NULL
       ORDER BY lp.created_at DESC`,
      [tenantId]
    ),
    query(
      `SELECT a.id, a.title, a.status, a.assignment_type, a.max_marks, s.code AS subject_code, a.due_at
       FROM lms.assignments a
       LEFT JOIN acd.subjects s ON s.id = a.subject_id
       WHERE a.tenant_id = $1 AND a.deleted_at IS NULL
       ORDER BY a.created_at DESC`,
      [tenantId]
    ),
    query(
      `SELECT r.id, r.name, r.provider, r.status, v.title, v.status AS session_status, v.scheduled_start
       FROM lms.virtual_classrooms r
       LEFT JOIN lms.virtual_sessions v ON v.classroom_id = r.id AND v.deleted_at IS NULL
       WHERE r.tenant_id = $1 AND r.deleted_at IS NULL
       ORDER BY r.created_at DESC`,
      [tenantId]
    ),
    query(
      `SELECT g.id, g.status, g.percent_complete, s.enrollment_no, sub.code AS subject_code
       FROM lms.progress g
       JOIN stu.students s ON s.id = g.student_id
       LEFT JOIN acd.subjects sub ON sub.id = g.scope_id AND g.scope_type = 'SUBJECT'
       WHERE g.tenant_id = $1 AND g.deleted_at IS NULL
       ORDER BY g.updated_at DESC`,
      [tenantId]
    ),
    query(`SELECT id, code, name, status FROM lms.badges WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`, [tenantId]),
    query(`SELECT id, code, name, status, credential_type FROM lms.certifications WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`, [tenantId]),
    query(
      `SELECT co.code AS course_code, po.code AS program_code, m.correlation_level
       FROM obe.outcome_mappings m
       JOIN obe.outcomes co ON co.id = m.from_outcome_id AND co.deleted_at IS NULL
       JOIN obe.outcomes po ON po.id = m.to_outcome_id AND po.deleted_at IS NULL
       WHERE m.tenant_id = $1 AND m.deleted_at IS NULL
       ORDER BY co.code`,
      [tenantId]
    ),
  ]);

  return (
    <LearningManager
      subjects={subjects.rows}
      departments={departments.rows}
      faculty={faculty.rows.map((row) => ({ id: row.id, label: `${row.employee_no} · ${row.display_name || 'Faculty'}` }))}
      programs={programs.rows}
      students={students.rows.map((row) => ({ id: row.id, label: `${row.enrollment_no} · ${row.display_name || 'Student'}` }))}
      courses={courses.rows.map((row) => ({ ...row, credits: Number(row.credits) }))}
      contents={contents.rows}
      lessons={lessons.rows.map((row) => ({ ...row, planned_hours: row.planned_hours === null ? null : Number(row.planned_hours) }))}
      assignments={assignments.rows.map((row) => ({ ...row, max_marks: row.max_marks === null ? null : Number(row.max_marks) }))}
      classrooms={classrooms.rows}
      progress={progress.rows.map((row) => ({ ...row, percent_complete: Number(row.percent_complete) }))}
      badges={badges.rows}
      certificates={certificates.rows}
      outcomes={outcomes.rows.map((row) => ({ ...row, correlation_level: Number(row.correlation_level) }))}
    />
  );
}
