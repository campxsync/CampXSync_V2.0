import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  registrationId: z.string().uuid(),
  barcode: z.string().trim().min(4).max(40),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record an answer script.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    const registration = await query(
      `SELECT r.id, e.college_id, sess.id AS session_id
       FROM exm.registrations r
       JOIN exm.examinations e ON e.id = r.examination_id
       JOIN exm.exam_sessions sess ON sess.examination_id = e.id AND sess.deleted_at IS NULL
       WHERE r.id = $1 AND r.tenant_id = $2 AND r.deleted_at IS NULL`,
      [data.registrationId, auth.tenantId]
    );
    if (!registration.rows[0]) return problem(404, 'Not Found', 'That registration has no scheduled session.');

    const created = await withTransaction(async (client) => {
      const script = await client.query(
        `INSERT INTO exm.answer_scripts (tenant_id, exam_session_id, registration_id, barcode, status, created_by)
         VALUES ($1, $2, $3, $4, 'ISSUED', $5)
         RETURNING id, barcode, status`,
        [auth.tenantId, registration.rows[0].session_id, data.registrationId, data.barcode.toUpperCase(), auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'ANSWER_SCRIPT_RECORDED', 'COLLEGE_ADMIN', $3, 'ANSWER_SCRIPT', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, registration.rows[0].college_id, auth.userId, script.rows[0].id, JSON.stringify({ barcode: script.rows[0].barcode })]
      );
      return script.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Answer script recorded as issued. No scanner was used.', script: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That barcode or registration already has a script.');
    console.error('POST /api/college/examinations/scripts', err);
    return problem(500, 'Server Error', 'The answer script could not be saved.');
  }
}
