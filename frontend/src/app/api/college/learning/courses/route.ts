import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  credits: z.number().min(0).max(40),
  departmentId: z.string().uuid().optional(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open a delivery course.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));

    const { code, name, credits, departmentId } = parsed.data;
    if (departmentId) {
      const department = await query(
        `SELECT id FROM core.departments WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
        [departmentId, auth.tenantId]
      );
      if (!department.rows[0]) return problem(404, 'Not Found', 'That department is not in this institute.');
    }

    const inserted = await query(
      `INSERT INTO acd.courses (tenant_id, department_id, code, name, credits, status, created_by)
       VALUES ($1, $2, $3, $4, $5, 'DRAFT', $6)
       RETURNING id, code, name, status`,
      [auth.tenantId, departmentId || null, code, name, credits, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (
         tenant_id, college_id, event_time, action, principal_type, principal_id,
         resource_type, resource_id, status, severity, detail
       ) VALUES ($1, $2, now(), 'DELIVERY_COURSE_OPENED', 'COLLEGE_ADMIN', $3, 'COURSE', $4, 'SUCCESS', 'INFO', $5)`,
      [auth.tenantId, auth.collegeId, auth.userId, inserted.rows[0].id, JSON.stringify({ code })]
    );
    return NextResponse.json({ success: true, message: 'Delivery course opened as a draft.', course: inserted.rows[0] }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A course with that code already exists.');
    console.error('POST /api/college/learning/courses', err);
    return problem(500, 'Server Error', 'The delivery course could not be opened.');
  }
}
