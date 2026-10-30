import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({ messageId: z.string().uuid(), employeeId: z.string().uuid() });

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can deliver an in-app notice.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const message = await query(
      `SELECT id, college_id, status, channel FROM cam.messages WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL`,
      [parsed.data.messageId, auth.tenantId]
    );
    if (!message.rows[0]) return problem(404, 'Not Found', 'That notice is not in this institute.');
    if (message.rows[0].channel !== 'IN_APP') return problem(409, 'Conflict', 'Only an in-app notice can be delivered here.');
    const employee = await query(
      `SELECT id, person_id, college_id FROM hrm.employees WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL AND person_id IS NOT NULL`,
      [parsed.data.employeeId, auth.tenantId]
    );
    if (!employee.rows[0]) return problem(404, 'Not Found', 'That faculty member has no person record to receive the notice.');

    const created = await withTransaction(async (client) => {
      await client.query(
        `INSERT INTO cam.message_recipients (tenant_id, message_id, person_id, status, created_by)
         VALUES ($1, $2, $3, 'DELIVERED', $4)`,
        [auth.tenantId, parsed.data.messageId, employee.rows[0].person_id, auth.userId]
      );
      const delivery = await client.query(
        `INSERT INTO cam.deliveries (tenant_id, message_id, person_id, channel, status, sent_at, delivered_at, created_by)
         VALUES ($1, $2, $3, 'IN_APP', 'DELIVERED', now(), now(), $4)
         RETURNING id, status`,
        [auth.tenantId, parsed.data.messageId, employee.rows[0].person_id, auth.userId]
      );
      await client.query(
        `UPDATE cam.messages SET status = 'SENT', updated_by = $2 WHERE id = $1 AND status = 'DRAFT'`,
        [parsed.data.messageId, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'NOTICE_DELIVERED_IN_APP', 'COLLEGE_ADMIN', $3, 'CAMPUS_MESSAGE', $4, 'SUCCESS', 'INFO', '{"channel":"IN_APP"}')`,
        [auth.tenantId, message.rows[0].college_id, auth.userId, parsed.data.messageId]
      );
      return delivery.rows[0];
    });
    return NextResponse.json({
      success: true,
      message: 'Notice delivered in the app to that faculty member. No email, SMS, or WhatsApp was sent.',
      delivery: created,
    }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That notice was already delivered to this person.');
    console.error('POST /api/college/communications/deliver', err);
    return problem(500, 'Server Error', 'The notice could not be delivered.');
  }
}
