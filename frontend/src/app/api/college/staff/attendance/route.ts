import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  employeeId: z.string().uuid(),
  attendanceDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record staff attendance.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const employee = await query(`SELECT id, college_id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.employeeId, auth.tenantId]);
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member is not in this institute.');
    const created = await withTransaction(async (client) => {
      const day = await client.query(
        `INSERT INTO hrm.attendance_days (tenant_id, employee_id, attendance_date, status, created_by)
         VALUES ($1, $2, $3, 'PRESENT', $4)
         RETURNING id, status`,
        [auth.tenantId, data.employeeId, data.attendanceDate, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'STAFF_ATTENDANCE_RECORDED', 'COLLEGE_ADMIN', $3, 'STAFF_ATTENDANCE', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, employee.rows[0].college_id, auth.userId, day.rows[0].id, JSON.stringify({ date: data.attendanceDate })]
      );
      return day.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Staff day recorded as present. No biometric punch was captured.', attendance: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'Attendance is already recorded for that faculty member on that date.');
    console.error('POST /api/college/staff/attendance', err);
    return problem(500, 'Server Error', 'The attendance day could not be saved.');
  }
}
