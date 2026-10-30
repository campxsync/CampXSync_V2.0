import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../guard';

const Schema = z.object({ courseId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can activate a delivery course.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', 'A course is required.');

    const current = await query(
      `SELECT id, status FROM acd.courses WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [parsed.data.courseId, auth.tenantId]
    );
    const course = current.rows[0];
    if (!course) return problem(404, 'Not Found', 'That course is not in this institute.');
    if (course.status !== 'DRAFT') return problem(409, 'Conflict', 'Only a draft course can be activated.');

    const updated = await query(
      `UPDATE acd.courses
       SET status = 'ACTIVE', updated_by = $3, updated_at = now(), row_version = row_version + 1
       WHERE id = $1 AND tenant_id = $2
       RETURNING id, code, status`,
      [course.id, auth.tenantId, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'DELIVERY_COURSE_ACTIVATED', 'COLLEGE_ADMIN', $3, 'COURSE', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, auth.collegeId, auth.userId, course.id]
    );
    return NextResponse.json({ success: true, message: 'Delivery course activated.', course: updated.rows[0] });
  } catch (err) {
    console.error('POST /api/college/learning/courses/publish', err);
    return problem(500, 'Server Error', 'The delivery course could not be activated.');
  }
}
