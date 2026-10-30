import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  subjectId: z.string().uuid(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open an examination.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    const college = await query(
      `SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND ($3::uuid IS NULL OR id = $3)`,
      [data.collegeId, auth.tenantId, auth.collegeId]
    );
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const subject = await query(`SELECT id FROM acd.subjects WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.subjectId, auth.tenantId]);
    if (!subject.rows[0]) return problem(404, 'Not Found', 'That subject is not in this institute.');
    const scheme = await query(
      `SELECT id, total_marks, pass_marks FROM acd.assessment_structures
       WHERE tenant_id = $1 AND subject_id = $2 AND status = 'ACTIVE' AND deleted_at IS NULL
       ORDER BY created_at DESC LIMIT 1`,
      [auth.tenantId, data.subjectId]
    );
    const marks = scheme.rows[0];
    if (!marks) return problem(409, 'Conflict', 'Activate an assessment scheme for this subject before opening an examination.');

    const created = await withTransaction(async (client) => {
      const exam = await client.query(
        `INSERT INTO exm.examinations (tenant_id, college_id, code, name, exam_type, status, created_by)
         VALUES ($1, $2, $3, $4, 'REGULAR', 'PLANNED', $5)
         RETURNING id, code, status`,
        [auth.tenantId, data.collegeId, data.code, data.name, auth.userId]
      );
      await client.query(
        `INSERT INTO exm.exam_subjects (tenant_id, examination_id, subject_id, assessment_structure_id, max_marks, pass_marks, created_by)
         VALUES ($1, $2, $3, $4, $5, $6, $7)`,
        [auth.tenantId, exam.rows[0].id, data.subjectId, marks.id, marks.total_marks, marks.pass_marks, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'EXAMINATION_OPENED', 'COLLEGE_ADMIN', $3, 'EXAMINATION', $4, 'SUCCESS', 'INFO', '{}')`,
        [auth.tenantId, data.collegeId, auth.userId, exam.rows[0].id]
      );
      return exam.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Examination opened as planned. Registration is not open yet.', examination: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'An examination with that code already exists.');
    console.error('POST /api/college/examinations', err);
    return problem(500, 'Server Error', 'The examination could not be opened.');
  }
}
