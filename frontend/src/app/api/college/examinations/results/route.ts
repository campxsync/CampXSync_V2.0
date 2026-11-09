import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({ registrationId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a result.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));

    const row = await query(
      `SELECT r.id, r.student_id, r.examination_id, e.college_id, es.id AS exam_subject_id, es.max_marks, es.pass_marks,
              m.total_marks
       FROM exm.registrations r
       JOIN exm.examinations e ON e.id = r.examination_id
       JOIN exm.exam_subjects es ON es.examination_id = e.id AND es.deleted_at IS NULL
       JOIN exm.exam_sessions sess ON sess.examination_id = e.id AND sess.exam_subject_id = es.id AND sess.deleted_at IS NULL
       JOIN exm.answer_scripts s ON s.registration_id = r.id AND s.exam_session_id = sess.id AND s.deleted_at IS NULL
       JOIN exm.marks_entries m ON m.answer_script_id = s.id AND m.deleted_at IS NULL AND m.status = 'FINALIZED'
       WHERE r.id = $1 AND r.tenant_id = $2 AND r.deleted_at IS NULL`,
      [parsed.data.registrationId, auth.tenantId]
    );
    if (!row.rows[0]) return problem(404, 'Not Found', 'Finalized marks are required before a result can be recorded.');
    const marks = Number(row.rows[0].total_marks);
    const pass = row.rows[0].pass_marks === null ? null : Number(row.rows[0].pass_marks);
    const subjectStatus = pass !== null && marks >= pass ? 'PASS' : 'FAIL';

    const created = await withTransaction(async (client) => {
      const result = await client.query(
        `INSERT INTO exm.results (tenant_id, registration_id, examination_id, student_id, overall_status, status, version_no, grace_marks_applied, created_by)
         VALUES ($1, $2, $3, $4, $5, 'PROCESSED', 1, 0, $6)
         RETURNING id, overall_status, status`,
        [auth.tenantId, parsed.data.registrationId, row.rows[0].examination_id, row.rows[0].student_id, subjectStatus, auth.userId]
      );
      await client.query(
        `INSERT INTO exm.result_subjects (tenant_id, result_id, exam_subject_id, marks_obtained, max_marks, status, grace_marks, created_by)
         VALUES ($1, $2, $3, $4, $5, $6, 0, $7)`,
        [auth.tenantId, result.rows[0].id, row.rows[0].exam_subject_id, marks, row.rows[0].max_marks, subjectStatus, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'EXAM_RESULT_PROCESSED', 'COLLEGE_ADMIN', $3, 'EXAM_RESULT', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, row.rows[0].college_id, auth.userId, result.rows[0].id, JSON.stringify({ marks, subjectStatus })]
      );
      return { ...result.rows[0], marks, subjectStatus };
    });
    return NextResponse.json({
      success: true,
      message: 'Result processed from the finalized marks. It is not published, and no SGPA was calculated.',
      result: created,
    }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A result already exists for this registration.');
    console.error('POST /api/college/examinations/results', err);
    return problem(500, 'Server Error', 'The result could not be saved.');
  }
}
