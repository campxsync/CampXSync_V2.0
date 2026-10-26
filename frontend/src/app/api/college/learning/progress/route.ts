import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  studentId: z.string().uuid(),
  subjectId: z.string().uuid(),
  percentComplete: z.number().min(0).max(100),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record learner progress.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const student = await query(
      `SELECT id FROM stu.students WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
         AND ($3::uuid IS NULL OR college_id = $3)`,
      [data.studentId, auth.tenantId, auth.collegeId]
    );
    if (!student.rows[0]) return problem(404, 'Not Found', 'That student is not in this institute.');
    const subject = await query(`SELECT id FROM acd.subjects WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.subjectId, auth.tenantId]);
    if (!subject.rows[0]) return problem(404, 'Not Found', 'That subject is not in this institute.');

    const status = data.percentComplete === 0 ? 'NOT_STARTED' : data.percentComplete === 100 ? 'COMPLETED' : 'IN_PROGRESS';
    const existing = await query(
      `SELECT id FROM lms.progress
       WHERE tenant_id = $1 AND student_id = $2 AND scope_type = 'SUBJECT' AND scope_id = $3 AND deleted_at IS NULL`,
      [auth.tenantId, data.studentId, data.subjectId]
    );
    const row = existing.rows[0]
      ? await query(
          `UPDATE lms.progress
           SET percent_complete = $2, status = $3, last_activity_at = now(),
               completed_at = CASE WHEN $3 = 'COMPLETED' THEN now() ELSE completed_at END,
               updated_by = $4, updated_at = now(), row_version = row_version + 1
           WHERE id = $1
           RETURNING id, status, percent_complete`,
          [existing.rows[0].id, data.percentComplete, status, auth.userId]
        )
      : await query(
          `INSERT INTO lms.progress (tenant_id, student_id, scope_type, scope_id, status, percent_complete, last_activity_at, completed_at, created_by)
           VALUES ($1, $2, 'SUBJECT', $3, $4, $5, now(), CASE WHEN $4 = 'COMPLETED' THEN now() ELSE NULL END, $6)
           RETURNING id, status, percent_complete`,
          [auth.tenantId, data.studentId, data.subjectId, status, data.percentComplete, auth.userId]
        );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'PROGRESS_RECORDED', 'COLLEGE_ADMIN', $3, 'LEARNER_PROGRESS', $4, 'SUCCESS', 'INFO', $5)`,
      [auth.tenantId, auth.collegeId, auth.userId, row.rows[0].id, JSON.stringify({ percentComplete: data.percentComplete })]
    );
    return NextResponse.json({ success: true, message: 'Learner progress recorded.', progress: row.rows[0] }, { status: 201 });
  } catch (err) {
    console.error('POST /api/college/learning/progress', err);
    return problem(500, 'Server Error', 'Learner progress could not be recorded.');
  }
}
