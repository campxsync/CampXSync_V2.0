import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  code: z.string().min(2).max(32).regex(/^[A-Z0-9_-]+$/),
  name: z.string().min(2).max(160),
  totalBudget: z.number().min(0),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open a scholarship scheme.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const college = await query(
      `SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND ($3::uuid IS NULL OR id = $3)`,
      [data.collegeId, auth.tenantId, auth.collegeId]
    );
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const inserted = await query(
      `INSERT INTO fin.scholarship_schemes (tenant_id, college_id, code, name, sponsor_type, total_budget, status, created_by)
       VALUES ($1, $2, $3, $4, 'INSTITUTIONAL', $5, 'DRAFT', $6)
       RETURNING id, code, status`,
      [auth.tenantId, data.collegeId, data.code, data.name, data.totalBudget, auth.userId]
    );
    await query(
      `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
       VALUES ($1, $2, now(), 'SCHOLARSHIP_OPENED', 'COLLEGE_ADMIN', $3, 'SCHOLARSHIP', $4, 'SUCCESS', 'INFO', '{}')`,
      [auth.tenantId, data.collegeId, auth.userId, inserted.rows[0].id]
    );
    return NextResponse.json({ success: true, message: 'Scholarship scheme opened as a draft. No award is paid from this screen.', scheme: inserted.rows[0] }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A scholarship with that code already exists.');
    console.error('POST /api/college/finance/scholarships', err);
    return problem(500, 'Server Error', 'The scholarship could not be opened.');
  }
}
