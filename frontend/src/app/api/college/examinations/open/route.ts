import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({ examinationId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open exam registration.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', 'An examination is required.');
    const current = await query(
      `SELECT id, status, college_id FROM exm.examinations WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [parsed.data.examinationId, auth.tenantId]
    );
    const exam = current.rows[0];
    if (!exam) return problem(404, 'Not Found', 'That examination is not in this institute.');
    if (exam.status !== 'PLANNED') return problem(409, 'Conflict', 'Registration can be opened only while the examination is planned.');
    const subjects = await query(
      `SELECT count(*)::int AS n FROM exm.exam_subjects WHERE examination_id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [exam.id, auth.tenantId]
    );
    if (subjects.rows[0].n < 1) return problem(409, 'Conflict', 'Add a subject before opening registration.');
    await query(
      `UPDATE exm.examinations
       SET status = 'REGISTRATION_OPEN', registration_start = now(), registration_end = now() + interval '14 days',
           updated_by = $3, updated_at = now(), row_version = row_version + 1
       WHERE id = $1 AND tenant_id = $2`,
      [exam.id, auth.tenantId, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
       VALUES ($1, $2, now(), 'EXAM_REGISTRATION_OPENED', 'COLLEGE_ADMIN', $3, 'EXAMINATION', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, exam.college_id, auth.userId, exam.id]
    );
    return NextResponse.json({ success: true, message: 'Registration is open for 14 days. No hall ticket is issued from this screen.' });
  } catch (err) {
    console.error('POST /api/college/examinations/open', err);
    return problem(500, 'Server Error', 'Registration could not be opened.');
  }
}
