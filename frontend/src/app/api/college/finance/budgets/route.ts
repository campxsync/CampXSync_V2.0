import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  academicYearId: z.string().uuid(),
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  amount: z.number().positive(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open a budget.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const college = await query(
      `SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND ($3::uuid IS NULL OR id = $3)`,
      [data.collegeId, auth.tenantId, auth.collegeId]
    );
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const year = await query(`SELECT id FROM core.academic_years WHERE id = $1 AND tenant_id = $2`, [data.academicYearId, auth.tenantId]);
    if (!year.rows[0]) return problem(404, 'Not Found', 'That academic year is not in this institute.');

    const created = await withTransaction(async (client) => {
      const budget = await client.query(
        `INSERT INTO bud.budgets (tenant_id, college_id, academic_year_id, code, name, budget_type, status, current_version_no, total_amount, created_by)
         VALUES ($1, $2, $3, $4, $5, 'OPERATING', 'DRAFT', 1, $6, $7)
         RETURNING id, code, status`,
        [auth.tenantId, data.collegeId, data.academicYearId, data.code, data.name, data.amount, auth.userId]
      );
      const version = await client.query(
        `INSERT INTO bud.budget_versions (tenant_id, budget_id, version_no, status, total_amount, created_by)
         VALUES ($1, $2, 1, 'DRAFT', $3, $4)
         RETURNING id`,
        [auth.tenantId, budget.rows[0].id, data.amount, auth.userId]
      );
      await client.query(
        `INSERT INTO bud.budget_lines (tenant_id, budget_version_id, description, planned_amount, allocated_amount, committed_amount, actual_amount, created_by)
         VALUES ($1, $2, $3, $4, $4, 0, 0, $5)`,
        [auth.tenantId, version.rows[0].id, data.name, data.amount, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'BUDGET_OPENED', 'COLLEGE_ADMIN', $3, 'BUDGET', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, data.collegeId, auth.userId, budget.rows[0].id, JSON.stringify({ amount: data.amount })]
      );
      return budget.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Operating budget opened as a draft.', budget: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A budget with that code already exists.');
    console.error('POST /api/college/finance/budgets', err);
    return problem(500, 'Server Error', 'The budget could not be opened.');
  }
}
