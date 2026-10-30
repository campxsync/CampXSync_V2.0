import { redirect } from 'next/navigation';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';
import { ExaminationsManager } from './examinations-manager';

export const dynamic = 'force-dynamic';

export default async function CollegeExaminationsPage() {
  const supabase = await createClient();
  const resolution = await resolveUserRole(supabase);
  if (!resolution.success || resolution.session.role !== 'college_admin') redirect('/access-denied?reason=forbidden');
  const tenantId = resolution.session.tenantId;
  const collegeId = resolution.session.assignedCollegeId;
  if (!tenantId) redirect('/access-denied?reason=forbidden');

  const colleges = await query(`SELECT id, code, name FROM core.colleges WHERE tenant_id = $1 AND deleted_at IS NULL AND ($2::uuid IS NULL OR id = $2) ORDER BY code`, [tenantId, collegeId]);
  const subjects = await query(`SELECT id, code, name FROM acd.subjects WHERE tenant_id = $1 AND deleted_at IS NULL ORDER BY code`, [tenantId]);
  const students = await query(
      `SELECT s.id, s.enrollment_no, p.display_name FROM stu.students s LEFT JOIN core.persons p ON p.id = s.person_id
       WHERE s.tenant_id = $1 AND s.deleted_at IS NULL AND ($2::uuid IS NULL OR s.college_id = $2) ORDER BY s.enrollment_no`,
      [tenantId, collegeId]
    );
  const faculty = await query(
      `SELECT e.id, e.employee_no, p.display_name
       FROM hrm.employees e LEFT JOIN core.persons p ON p.id = e.person_id
       WHERE e.tenant_id = $1 AND e.deleted_at IS NULL AND ($2::uuid IS NULL OR e.college_id = $2)
       ORDER BY e.employee_no`,
      [tenantId, collegeId]
    );
  const exams = await query(
      `SELECT e.id, e.code, e.name, e.status, s.code AS subject_code, es.max_marks, es.pass_marks
       FROM exm.examinations e
       LEFT JOIN exm.exam_subjects es ON es.examination_id = e.id AND es.deleted_at IS NULL
       LEFT JOIN acd.subjects s ON s.id = es.subject_id
       WHERE e.tenant_id = $1 AND e.deleted_at IS NULL
       ORDER BY e.code`,
      [tenantId]
    );
  const registrations = await query(
      `SELECT r.id, r.registration_no, r.status, st.enrollment_no, e.code AS exam_code, snap.result
       FROM exm.registrations r
       JOIN exm.examinations e ON e.id = r.examination_id
       JOIN stu.students st ON st.id = r.student_id
       LEFT JOIN exm.eligibility_snapshots snap ON snap.registration_id = r.id
       WHERE r.tenant_id = $1 AND r.deleted_at IS NULL
       ORDER BY r.created_at DESC`,
      [tenantId]
    );
  const sessions = await query(
      `SELECT sess.id, sess.session_date::text, sess.start_time::text, sess.end_time::text, sess.status, c.code AS centre_code, rm.code AS room_code, e.code AS exam_code
       FROM exm.exam_sessions sess
       JOIN exm.examinations e ON e.id = sess.examination_id
       LEFT JOIN exm.exam_centres c ON c.id = sess.centre_id
       LEFT JOIN exm.schedule_entries se ON se.session_id = sess.id AND se.deleted_at IS NULL
       LEFT JOIN exm.exam_rooms rm ON rm.id = se.room_id
       WHERE sess.tenant_id = $1 AND sess.deleted_at IS NULL
       ORDER BY sess.session_date`,
      [tenantId]
    );
  const tickets = await query(
      `SELECT h.id, h.status, st.enrollment_no, hs.seat_no
       FROM exm.hall_tickets h
       JOIN stu.students st ON st.id = h.student_id
       LEFT JOIN exm.hall_ticket_subjects hs ON hs.hall_ticket_id = h.id AND hs.deleted_at IS NULL
       WHERE h.tenant_id = $1 AND h.deleted_at IS NULL`,
      [tenantId]
    );
  const scripts = await query(
      `SELECT s.id, s.barcode, s.status, st.enrollment_no
       FROM exm.answer_scripts s
       JOIN exm.registrations r ON r.id = s.registration_id
       JOIN stu.students st ON st.id = r.student_id
       WHERE s.tenant_id = $1 AND s.deleted_at IS NULL`,
      [tenantId]
    );
  const marks = await query(
      `SELECT m.id, m.total_marks, m.status, s.barcode
       FROM exm.marks_entries m
       JOIN exm.answer_scripts s ON s.id = m.answer_script_id
       WHERE m.tenant_id = $1 AND m.deleted_at IS NULL`,
      [tenantId]
    );
  const results = await query(
      `SELECT res.id, res.overall_status, res.status, st.enrollment_no, rs.marks_obtained, rs.max_marks
       FROM exm.results res
       JOIN stu.students st ON st.id = res.student_id
       LEFT JOIN exm.result_subjects rs ON rs.result_id = res.id AND rs.deleted_at IS NULL
       WHERE res.tenant_id = $1 AND res.deleted_at IS NULL`,
      [tenantId]
    );

  return (
    <ExaminationsManager
      colleges={colleges.rows}
      subjects={subjects.rows}
      students={students.rows.map((row) => ({ id: row.id, label: `${row.enrollment_no} · ${row.display_name || 'Student'}` }))}
      faculty={faculty.rows.map((row) => ({ id: row.id, label: `${row.employee_no} · ${row.display_name || 'Faculty'}` }))}
      exams={exams.rows.map((row) => ({ ...row, max_marks: Number(row.max_marks || 0), pass_marks: row.pass_marks === null ? null : Number(row.pass_marks) }))}
      registrations={registrations.rows}
      sessions={sessions.rows}
      tickets={tickets.rows}
      scripts={scripts.rows}
      marks={marks.rows.map((row) => ({ ...row, total_marks: Number(row.total_marks) }))}
      results={results.rows.map((row) => ({ ...row, marks_obtained: row.marks_obtained === null ? null : Number(row.marks_obtained), max_marks: row.max_marks === null ? null : Number(row.max_marks) }))}
    />
  );
}
