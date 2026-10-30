import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  academicYearId: z.string().uuid(),
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  componentName: z.string().min(2).max(160),
  amount: z.number().positive().max(10000000),
  taxable: z.boolean(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open a fee structure.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const college = await query(
      `SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
         AND ($3::uuid IS NULL OR id = $3)`,
      [data.collegeId, auth.tenantId, auth.collegeId]
    );
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const year = await query(`SELECT id FROM core.academic_years WHERE id = $1 AND tenant_id = $2`, [data.academicYearId, auth.tenantId]);
    if (!year.rows[0]) return problem(404, 'Not Found', 'That academic year is not in this institute.');

    const created = await withTransaction(async (client) => {
      const structure = await client.query(
        `INSERT INTO fin.fee_structures (tenant_id, college_id, academic_year_id, code, name, currency_code, status, current_version_no, created_by)
         VALUES ($1, $2, $3, $4, $5, 'INR', 'DRAFT', 1, $6)
         RETURNING id, code, status`,
        [auth.tenantId, data.collegeId, data.academicYearId, data.code, data.name, auth.userId]
      );
      const version = await client.query(
        `INSERT INTO fin.fee_structure_versions (tenant_id, structure_id, version_no, effective_from, total_amount, status, created_by)
         VALUES ($1, $2, 1, CURRENT_DATE, $3, 'DRAFT', $4)
         RETURNING id`,
        [auth.tenantId, structure.rows[0].id, data.amount, auth.userId]
      );
      await client.query(
        `INSERT INTO fin.fee_components (tenant_id, structure_version_id, code, name, component_type, amount, is_taxable, sort_order, created_by)
         VALUES ($1, $2, 'TUITION', $3, 'TUITION', $4, $5, 1, $6)`,
        [auth.tenantId, version.rows[0].id, data.componentName, data.amount, data.taxable, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'FEE_STRUCTURE_OPENED', 'COLLEGE_ADMIN', $3, 'FEE_STRUCTURE', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, data.collegeId, auth.userId, structure.rows[0].id, JSON.stringify({ amount: data.amount })]
      );
      return { ...structure.rows[0], versionId: version.rows[0].id };
    });
    return NextResponse.json({ success: true, message: 'Fee structure opened as a draft.', structure: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A fee structure with that code already exists.');
    console.error('POST /api/college/finance/structures', err);
    return problem(500, 'Server Error', 'The fee structure could not be opened.');
  }
}
