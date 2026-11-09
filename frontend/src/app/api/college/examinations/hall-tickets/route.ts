import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  registrationId: z.string().uuid(),
  seatNo: z.string().trim().min(1).max(20),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can record a hall ticket.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;

    const registration = await query(
      `SELECT r.id, r.student_id, r.examination_id, e.college_id, es.id AS exam_subject_id, sess.id AS session_id
       FROM exm.registrations r
       JOIN exm.examinations e ON e.id = r.examination_id
       JOIN exm.exam_subjects es ON es.examination_id = e.id AND es.deleted_at IS NULL
       JOIN exm.exam_sessions sess ON sess.examination_id = e.id AND sess.exam_subject_id = es.id AND sess.deleted_at IS NULL
       WHERE r.id = $1 AND r.tenant_id = $2 AND r.deleted_at IS NULL`,
      [data.registrationId, auth.tenantId]
    );
    if (!registration.rows[0]) return problem(404, 'Not Found', 'That registration has no scheduled session.');

    const created = await withTransaction(async (client) => {
      const ticket = await client.query(
        `INSERT INTO exm.hall_tickets (tenant_id, registration_id, student_id, status, generated_at, created_by)
         VALUES ($1, $2, $3, 'GENERATED', now(), $4)
         RETURNING id, status`,
        [auth.tenantId, data.registrationId, registration.rows[0].student_id, auth.userId]
      );
      await client.query(
        `INSERT INTO exm.hall_ticket_subjects (tenant_id, hall_ticket_id, exam_subject_id, session_id, seat_no, created_by)
         VALUES ($1, $2, $3, $4, $5, $6)`,
        [auth.tenantId, ticket.rows[0].id, registration.rows[0].exam_subject_id, registration.rows[0].session_id, data.seatNo, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'HALL_TICKET_RECORDED', 'COLLEGE_ADMIN', $3, 'HALL_TICKET', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, registration.rows[0].college_id, auth.userId, ticket.rows[0].id, JSON.stringify({ seatNo: data.seatNo })]
      );
      return ticket.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Hall ticket recorded as generated. No PDF was produced.', hallTicket: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'A hall ticket already exists for this registration.');
    console.error('POST /api/college/examinations/hall-tickets', err);
    return problem(500, 'Server Error', 'The hall ticket could not be saved.');
  }
}
