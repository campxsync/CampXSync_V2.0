import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  employeeId: z.string().uuid(),
  joinDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open onboarding.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const employee = await query(`SELECT id, college_id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.employeeId, auth.tenantId]);
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member is not in this institute.');
    const created = await withTransaction(async (client) => {
      const row = await client.query(
        `INSERT INTO hrm.onboardings (tenant_id, employee_id, expected_join_date, status, created_by)
         VALUES ($1, $2, $3, 'PENDING', $4)
         RETURNING id, status`,
        [auth.tenantId, data.employeeId, data.joinDate, auth.userId]
      );
      await client.query(
        `INSERT INTO hrm.onboarding_tasks (tenant_id, onboarding_id, task_name, due_date, requires_document, status, created_by)
         VALUES ($1, $2, 'Confirm joining date', $3, false, 'OPEN', $4)`,
        [auth.tenantId, row.rows[0].id, data.joinDate, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'ONBOARDING_OPENED', 'COLLEGE_ADMIN', $3, 'ONBOARDING', $4, 'SUCCESS', 'INFO', '{}')`,
        [auth.tenantId, employee.rows[0].college_id, auth.userId, row.rows[0].id]
      );
      return row.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Onboarding opened as pending with one task. No document was uploaded.', onboarding: created }, { status: 201 });
  } catch (err: any) {
    console.error('POST /api/college/staff/onboarding', err);
    return problem(500, 'Server Error', 'Onboarding could not be saved.');
  }
}
