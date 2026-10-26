import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  category: z.enum(['GST', 'TDS', 'TCS', 'PT', 'PF', 'ESI', 'INCOME_TAX', 'OTHER']),
  ratePct: z.number().min(0).max(100),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a tax code.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const created = await withTransaction(async (client) => {
      const existing = await client.query(
        `SELECT id FROM tax.tax_types WHERE tenant_id = $1 AND code = $2 AND deleted_at IS NULL`,
        [auth.tenantId, data.category]
      );
      const typeId = existing.rows[0]?.id || (await client.query(
        `INSERT INTO tax.tax_types (tenant_id, code, name, category, created_by)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING id`,
        [auth.tenantId, data.category, data.category, data.category, auth.userId]
      )).rows[0].id;
      const code = await client.query(
        `INSERT INTO tax.tax_codes (tenant_id, tax_type_id, code, name, is_exempt, is_active, created_by)
         VALUES ($1, $2, $3, $4, false, true, $5)
         RETURNING id, code`,
        [auth.tenantId, typeId, data.code, data.name, auth.userId]
      );
      await client.query(
        `INSERT INTO tax.tax_rates (tenant_id, tax_code_id, component, rate_pct, effective_from, created_by)
         VALUES ($1, $2, 'TOTAL', $3, CURRENT_DATE, $4)`,
        [auth.tenantId, code.rows[0].id, data.ratePct, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'TAX_CODE_RECORDED', 'COLLEGE_ADMIN', $3, 'TAX_CODE', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, auth.collegeId, auth.userId, code.rows[0].id, JSON.stringify({ ratePct: data.ratePct })]
      );
      return code.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Tax code recorded. No return is filed from this screen.', taxCode: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That tax code already exists.');
    console.error('POST /api/college/finance/tax', err);
    return problem(500, 'Server Error', 'The tax code could not be recorded.');
  }
}
