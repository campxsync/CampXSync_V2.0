import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  employeeId: z.string().uuid(),
  basicPay: z.number().positive().max(10000000),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a payslip.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const employee = await query(`SELECT id, college_id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.employeeId, auth.tenantId]);
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member is not in this institute.');
    const structure = await query(
      `SELECT s.payroll_group_id, c.id AS component_id
       FROM pay.salary_structures s
       JOIN pay.salary_components c ON c.tenant_id = s.tenant_id AND c.code = s.code || '-BASIC' AND c.deleted_at IS NULL
       WHERE s.tenant_id = $1 AND s.deleted_at IS NULL
       ORDER BY s.created_at DESC LIMIT 1`,
      [auth.tenantId]
    );
    if (!structure.rows[0]) return problem(409, 'Conflict', 'Record a salary structure before a payslip.');

    const created = await withTransaction(async (client) => {
      const period = await client.query(
        `INSERT INTO pay.payroll_periods (tenant_id, payroll_group_id, period_month, period_year, start_date, end_date, status, created_by)
         VALUES ($1, $2, 10, 2026, '2026-10-01', '2026-10-31', 'OPEN', $3)
         RETURNING id`,
        [auth.tenantId, structure.rows[0].payroll_group_id, auth.userId]
      );
      const run = await client.query(
        `INSERT INTO pay.payroll_runs (tenant_id, period_id, run_no, run_type, status, employee_count, total_gross, total_deductions, total_net, created_by)
         VALUES ($1, $2, 'RUN-2026-10', 'REGULAR', 'CALCULATED', 1, $3, 0, $3, $4)
         RETURNING id`,
        [auth.tenantId, period.rows[0].id, data.basicPay, auth.userId]
      );
      const slip = await client.query(
        `INSERT INTO pay.payslips (tenant_id, run_id, employee_id, payslip_no, gross_earnings, total_deductions, net_pay, status, created_by)
         VALUES ($1, $2, $3, 'PS-2026-10-001', $4, 0, $4, 'CALCULATED', $5)
         RETURNING id, payslip_no, status, net_pay`,
        [auth.tenantId, run.rows[0].id, data.employeeId, data.basicPay, auth.userId]
      );
      await client.query(
        `INSERT INTO pay.payslip_lines (tenant_id, payslip_id, component_id, line_type, amount, created_by)
         VALUES ($1, $2, $3, 'EARNING', $4, $5)`,
        [auth.tenantId, slip.rows[0].id, structure.rows[0].component_id, data.basicPay, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'PAYSLIP_CALCULATED', 'COLLEGE_ADMIN', $3, 'PAYSLIP', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, employee.rows[0].college_id, auth.userId, slip.rows[0].id, JSON.stringify({ net: data.basicPay })]
      );
      return slip.rows[0];
    });
    return NextResponse.json({
      success: true,
      message: 'Payslip calculated from the entered basic pay. It is not approved, published, or paid to a bank.',
      payslip: created,
    }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A payslip for this run already exists.');
    console.error('POST /api/college/staff/payslip', err);
    return problem(500, 'Server Error', 'The payslip could not be saved.');
  }
}
