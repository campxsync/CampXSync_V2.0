import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../learning/guard';

const Schema = z.object({
  collegeId: z.string().uuid(),
  code: z.string().trim().regex(/^[A-Z0-9_-]+$/),
  subject: z.string().trim().min(3).max(160),
  body: z.string().trim().min(3).max(1000),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a campus notice.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    const college = await query(`SELECT id FROM core.colleges WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`, [data.collegeId, auth.tenantId]);
    if (!college.rows[0]) return problem(404, 'Not Found', 'That college is not in this institute.');
    const created = await withTransaction(async (client) => {
      await client.query(
        `INSERT INTO cam.audience_groups (tenant_id, code, name, created_by)
         VALUES ($1, $2, $3, $4)`,
        [auth.tenantId, data.code, data.subject, auth.userId]
      );
      const message = await client.query(
        `INSERT INTO cam.messages (tenant_id, college_id, channel, subject, body, priority, status, created_by_user_id, created_by)
         VALUES ($1, $2, 'IN_APP', $3, $4, 'NORMAL', 'DRAFT', $5, $5)
         RETURNING id, status`,
        [auth.tenantId, data.collegeId, data.subject, data.body, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'NOTICE_RECORDED', 'COLLEGE_ADMIN', $3, 'CAMPUS_MESSAGE', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, data.collegeId, auth.userId, message.rows[0].id, JSON.stringify({ channel: 'IN_APP' })]
      );
      return message.rows[0];
    });
    return NextResponse.json({ success: true, message: 'In-app notice saved as a draft. It was not emailed, texted, or sent on WhatsApp.', notice: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That notice code already exists.');
    console.error('POST /api/college/communications', err);
    return problem(500, 'Server Error', 'The notice could not be saved.');
  }
}
