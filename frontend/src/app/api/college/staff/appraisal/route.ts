import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  name: z.string().trim().min(3).max(120),
  startDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  endDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open an appraisal cycle.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    if (data.endDate < data.startDate) return problem(400, 'Validation Failed', 'The cycle must end on or after it starts.');
    const college = await query(`SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.collegeId, auth.tenantId]);
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const created = await withTransaction(async (client) => {
      const cycle = await client.query(
        `INSERT INTO hrm.performance_cycles (tenant_id, college_id, name, start_date, end_date, status, created_by)
         VALUES ($1, $2, $3, $4, $5, 'DRAFT', $6)
         RETURNING id, status`,
        [auth.tenantId, data.collegeId, data.name, data.startDate, data.endDate, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'APPRAISAL_CYCLE_OPENED', 'COLLEGE_ADMIN', $3, 'PERFORMANCE_CYCLE', $4, 'SUCCESS', 'INFO', '{}')`,
        [auth.tenantId, data.collegeId, auth.userId, cycle.rows[0].id]
      );
      return cycle.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Appraisal cycle saved as a draft. No review score was entered.', cycle: created }, { status: 201 });
  } catch (err: any) {
    console.error('POST /api/college/staff/appraisal', err);
    return problem(500, 'Server Error', 'The appraisal cycle could not be saved.');
  }
}
