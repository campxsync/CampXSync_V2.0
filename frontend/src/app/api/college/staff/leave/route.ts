import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  employeeId: z.string().uuid(),
  fromDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  toDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  reason: z.string().trim().min(3).max(240),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record staff leave.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    if (data.toDate < data.fromDate) return problem(400, 'Validation Failed', 'Leave must end on or after the start date.');
    const employee = await query(
      `SELECT id, college_id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [data.employeeId, auth.tenantId]
    );
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member is not in this institute.');

    const created = await withTransaction(async (client) => {
      const existing = await client.query(
        `SELECT id FROM hrm.leave_types WHERE tenant_id = $1 AND code = 'CASUAL' AND deleted_at IS NULL`,
        [auth.tenantId]
      );
      let typeId = existing.rows[0]?.id as string | undefined;
      if (!typeId) {
        const inserted = await client.query(
          `INSERT INTO hrm.leave_types (tenant_id, code, name, is_paid, unit, is_active, created_by)
           VALUES ($1, 'CASUAL', 'Casual leave', true, 'DAY', true, $2)
           RETURNING id`,
          [auth.tenantId, auth.userId]
        );
        typeId = inserted.rows[0].id;
      }
      const days = Math.round((Date.parse(data.toDate) - Date.parse(data.fromDate)) / 86400000) + 1;
      const application = await client.query(
        `INSERT INTO hrm.leave_applications (tenant_id, employee_id, leave_type_id, from_date, to_date, total_days, reason, status, created_by)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'DRAFT', $8)
         RETURNING id, status`,
        [auth.tenantId, data.employeeId, typeId, data.fromDate, data.toDate, days, data.reason, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'LEAVE_RECORDED', 'COLLEGE_ADMIN', $3, 'LEAVE_APPLICATION', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, employee.rows[0].college_id, auth.userId, application.rows[0].id, JSON.stringify({ from: data.fromDate })]
      );
      return application.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Casual leave saved as a draft. It is not approved, and no substitute was assigned.', leave: created }, { status: 201 });
  } catch (err: any) {
    console.error('POST /api/college/staff/leave', err);
    return problem(500, 'Server Error', 'The leave application could not be saved.');
  }
}
