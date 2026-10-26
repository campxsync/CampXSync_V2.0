import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../guard';

const Schema = z.object({ assignmentId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can publish an assignment.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', 'An assignment is required.');
    const current = await query(
      `SELECT id, status FROM lms.assignments WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [parsed.data.assignmentId, auth.tenantId]
    );
    const assignment = current.rows[0];
    if (!assignment) return problem(404, 'Not Found', 'That assignment is not in this institute.');
    if (assignment.status !== 'DRAFT') return problem(409, 'Conflict', 'Only a draft assignment can be published.');
    await query(
      `UPDATE lms.assignments SET status = 'PUBLISHED', updated_by = $3, updated_at = now(), row_version = row_version + 1
       WHERE id = $1 AND tenant_id = $2`,
      [assignment.id, auth.tenantId, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'ASSIGNMENT_PUBLISHED', 'COLLEGE_ADMIN', $3, 'ASSIGNMENT', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, auth.collegeId, auth.userId, assignment.id]
    );
    return NextResponse.json({ success: true, message: 'Assignment published. Student submission is not opened from this screen.' });
  } catch (err) {
    console.error('POST /api/college/learning/assignments/publish', err);
    return problem(500, 'Server Error', 'The assignment could not be published.');
  }
}
