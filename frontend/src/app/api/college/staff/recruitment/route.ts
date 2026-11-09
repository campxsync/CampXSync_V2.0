import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  requisitionNo: z.string().trim().regex(/^[A-Z0-9_-]+$/),
  title: z.string().trim().min(3).max(160),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can open a vacancy.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const college = await query(`SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.collegeId, auth.tenantId]);
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const created = await withTransaction(async (client) => {
      const requisition = await client.query(
        `INSERT INTO hrm.requisitions (tenant_id, college_id, requisition_no, positions, justification, status, created_by)
         VALUES ($1, $2, $3, 1, $4, 'DRAFT', $5)
         RETURNING id`,
        [auth.tenantId, data.collegeId, data.requisitionNo, data.title, auth.userId]
      );
      const posting = await client.query(
        `INSERT INTO hrm.job_postings (tenant_id, requisition_id, posting_no, title, status, created_by)
         VALUES ($1, $2, $3, $4, 'DRAFT', $5)
         RETURNING id, status`,
        [auth.tenantId, requisition.rows[0].id, data.requisitionNo, data.title, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'VACANCY_RECORDED', 'COLLEGE_ADMIN', $3, 'JOB_POSTING', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, data.collegeId, auth.userId, posting.rows[0].id, JSON.stringify({ requisitionNo: data.requisitionNo })]
      );
      return posting.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Vacancy saved as a draft for one position. No applicant or interview was recorded.', posting: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That requisition number already exists.');
    console.error('POST /api/college/staff/recruitment', err);
    return problem(500, 'Server Error', 'The vacancy could not be saved.');
  }
}
