import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  scriptId: z.string().uuid(),
  employeeId: z.string().uuid(),
  totalMarks: z.number().min(0).max(1000),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record marks.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    const script = await query(
      `SELECT s.id, es.id AS exam_subject_id, es.max_marks, e.id AS examination_id, e.college_id
       FROM exm.answer_scripts s
       JOIN exm.exam_sessions sess ON sess.id = s.exam_session_id
       JOIN exm.exam_subjects es ON es.id = sess.exam_subject_id
       JOIN exm.examinations e ON e.id = sess.examination_id
       WHERE s.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL`,
      [data.scriptId, auth.tenantId]
    );
    if (!script.rows[0]) return problem(404, 'Not Found', 'That answer script is not in this institute.');
    if (data.totalMarks > Number(script.rows[0].max_marks)) return problem(400, 'Validation Failed', 'Marks cannot exceed the paper maximum.');
    const employee = await query(
      `SELECT id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [data.employeeId, auth.tenantId]
    );
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member is not in this institute.');

    const created = await withTransaction(async (client) => {
      const stamp = Date.now().toString().slice(-6);
      const batch = await client.query(
        `INSERT INTO exm.valuation_batches (tenant_id, exam_subject_id, code, status, created_by)
         VALUES ($1, $2, $3, 'COMPLETED', $4)
         RETURNING id`,
        [auth.tenantId, script.rows[0].exam_subject_id, `VAL-${stamp}`, auth.userId]
      );
      const session = await client.query(
        `INSERT INTO exm.valuation_sessions (tenant_id, valuation_batch_id, employee_id, started_at, ended_at, status, created_by)
         VALUES ($1, $2, $3, now(), now(), 'LOCKED', $4)
         RETURNING id`,
        [auth.tenantId, batch.rows[0].id, data.employeeId, auth.userId]
      );
      const marks = await client.query(
        `INSERT INTO exm.marks_entries (tenant_id, answer_script_id, valuation_session_id, total_marks, status, created_by)
         VALUES ($1, $2, $3, $4, 'FINALIZED', $5)
         RETURNING id, total_marks, status`,
        [auth.tenantId, data.scriptId, session.rows[0].id, data.totalMarks, auth.userId]
      );
      await client.query(
        `INSERT INTO exm.marks_finalizations (tenant_id, examination_id, exam_subject_id, finalized_by, finalized_at, created_by)
         VALUES ($1, $2, $3, $4, now(), $4)`,
        [auth.tenantId, script.rows[0].examination_id, script.rows[0].exam_subject_id, auth.userId]
      );
      await client.query(
        `UPDATE exm.answer_scripts SET status = 'VALUED', updated_by = $2 WHERE id = $1`,
        [data.scriptId, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'EXAM_MARKS_FINALIZED', 'COLLEGE_ADMIN', $3, 'MARKS_ENTRY', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, script.rows[0].college_id, auth.userId, marks.rows[0].id, JSON.stringify({ totalMarks: data.totalMarks })]
      );
      return marks.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Marks recorded and finalized. No result has been published.', marks: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'Marks are already recorded for this script.');
    console.error('POST /api/college/examinations/marks', err);
    return problem(500, 'Server Error', 'The marks could not be saved.');
  }
}
