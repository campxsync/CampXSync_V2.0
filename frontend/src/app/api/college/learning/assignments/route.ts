import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  subjectId: z.string().uuid(),
  title: z.string().min(2).max(160),
  assignmentType: z.enum(['ASSIGNMENT', 'QUIZ', 'PROJECT', 'LAB', 'PRESENTATION']),
  maxMarks: z.number().min(0).max(1000),
  dueAt: z.string().datetime().optional(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open an assignment.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const subject = await query(`SELECT id FROM acd.subjects WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.subjectId, auth.tenantId]);
    if (!subject.rows[0]) return problem(404, 'Not Found', 'That subject is not in this institute.');

    const inserted = await query(
      `INSERT INTO lms.assignments (tenant_id, subject_id, title, assignment_type, max_marks, due_at, status, created_by)
       VALUES ($1, $2, $3, $4, $5, $6, 'DRAFT', $7)
       RETURNING id, title, status`,
      [auth.tenantId, data.subjectId, data.title, data.assignmentType, data.maxMarks, data.dueAt || null, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'ASSIGNMENT_OPENED', 'COLLEGE_ADMIN', $3, 'ASSIGNMENT', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, auth.collegeId, auth.userId, inserted.rows[0].id]
    );
    return NextResponse.json({ success: true, message: 'Assignment opened as a draft.', assignment: inserted.rows[0] }, { status: 201 });
  } catch (err) {
    console.error('POST /api/college/learning/assignments', err);
    return problem(500, 'Server Error', 'The assignment could not be opened.');
  }
}
