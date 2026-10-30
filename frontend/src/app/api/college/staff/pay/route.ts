import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  code: z.string().trim().regex(/^[A-Z0-9_-]+$/),
  name: z.string().trim().min(3).max(120),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a salary structure.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const college = await query(`SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.collegeId, auth.tenantId]);
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const created = await withTransaction(async (client) => {
      const group = await client.query(
        `INSERT INTO pay.payroll_groups (tenant_id, college_id, code, name, pay_frequency, status, created_by)
         VALUES ($1, $2, $3, $4, 'MONTHLY', 'ACTIVE', $5)
         RETURNING id`,
        [auth.tenantId, data.collegeId, data.code, data.name, auth.userId]
      );
      const structure = await client.query(
        `INSERT INTO pay.salary_structures (tenant_id, payroll_group_id, code, name, status, created_by)
         VALUES ($1, $2, $3, $4, 'DRAFT', $5)
         RETURNING id, status`,
        [auth.tenantId, group.rows[0].id, data.code, data.name, auth.userId]
      );
      await client.query(
        `INSERT INTO pay.salary_components (tenant_id, code, name, component_type, calc_type, is_taxable, is_statutory, display_order, is_active, created_by)
         VALUES ($1, $2, 'Basic pay', 'EARNING', 'FIXED', true, false, 1, true, $3)`,
        [auth.tenantId, `${data.code}-BASIC`, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'SALARY_STRUCTURE_RECORDED', 'COLLEGE_ADMIN', $3, 'SALARY_STRUCTURE', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, data.collegeId, auth.userId, structure.rows[0].id, JSON.stringify({ code: data.code })]
      );
      return structure.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Salary structure saved as a draft with a basic-pay component. No payslip or bank file was produced.', structure: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That salary code already exists.');
    console.error('POST /api/college/staff/pay', err);
    return problem(500, 'Server Error', 'The salary structure could not be saved.');
  }
}
