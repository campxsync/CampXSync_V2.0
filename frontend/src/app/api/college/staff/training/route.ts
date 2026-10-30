import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  employeeId: z.string().uuid(),
  code: z.string().trim().regex(/^[A-Z0-9_-]+$/),
  name: z.string().trim().min(3).max(160),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record faculty training.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const employee = await query(`SELECT id, college_id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.employeeId, auth.tenantId]);
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member is not in this institute.');
    const created = await withTransaction(async (client) => {
      const program = await client.query(
        `INSERT INTO hrm.training_programs (tenant_id, code, name, program_type, status, created_by)
         VALUES ($1, $2, $3, 'FDP', 'DRAFT', $4)
         RETURNING id`,
        [auth.tenantId, data.code, data.name, auth.userId]
      );
      const course = await client.query(
        `INSERT INTO hrm.training_courses (tenant_id, program_id, code, title, duration_hours, created_by)
         VALUES ($1, $2, $3, $4, 6, $5)
         RETURNING id`,
        [auth.tenantId, program.rows[0].id, data.code, data.name, auth.userId]
      );
      const enrollment = await client.query(
        `INSERT INTO hrm.training_enrollments (tenant_id, course_id, employee_id, status, created_by)
         VALUES ($1, $2, $3, 'NOMINATED', $4)
         RETURNING id, status`,
        [auth.tenantId, course.rows[0].id, data.employeeId, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'TRAINING_NOMINATED', 'COLLEGE_ADMIN', $3, 'TRAINING_ENROLLMENT', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, employee.rows[0].college_id, auth.userId, enrollment.rows[0].id, JSON.stringify({ code: data.code })]
      );
      return enrollment.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Faculty development program saved as a draft and the faculty member was nominated. No certificate was issued.', training: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That training code already exists.');
    console.error('POST /api/college/staff/training', err);
    return problem(500, 'Server Error', 'The training record could not be saved.');
  }
}
