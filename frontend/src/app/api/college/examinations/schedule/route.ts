import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../../learning/guard';

const Schema = z.object({
  examinationId: z.string().uuid(),
  centreCode: z.string().trim().min(2).max(40),
  centreName: z.string().trim().min(2).max(160),
  roomCode: z.string().trim().min(2).max(40),
  capacity: z.number().int().min(1).max(500),
  sessionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/),
  startTime: z.string().regex(/^\d{2}:\d{2}$/),
  endTime: z.string().regex(/^\d{2}:\d{2}$/),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can schedule an examination.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    if (data.endTime <= data.startTime) return problem(400, 'Validation Failed', 'The session must end after it starts.');

    const exam = await query(
      `SELECT e.id, e.college_id, es.id AS exam_subject_id
       FROM exm.examinations e
       JOIN exm.exam_subjects es ON es.examination_id = e.id AND es.deleted_at IS NULL
       WHERE e.id = $1 AND e.tenant_id = $2 AND e.deleted_at IS NULL`,
      [data.examinationId, auth.tenantId]
    );
    if (!exam.rows[0]) return problem(404, 'Not Found', 'That examination has no subject paper.');

    const created = await withTransaction(async (client) => {
      const centre = await client.query(
        `INSERT INTO exm.exam_centres (tenant_id, college_id, code, name, capacity, status, created_by)
         VALUES ($1, $2, $3, $4, $5, 'ACTIVE', $6)
         RETURNING id, code`,
        [auth.tenantId, exam.rows[0].college_id, data.centreCode.toUpperCase(), data.centreName, data.capacity, auth.userId]
      );
      const room = await client.query(
        `INSERT INTO exm.exam_rooms (tenant_id, centre_id, code, capacity, created_by)
         VALUES ($1, $2, $3, $4, $5)
         RETURNING id, code`,
        [auth.tenantId, centre.rows[0].id, data.roomCode.toUpperCase(), data.capacity, auth.userId]
      );
      const session = await client.query(
        `INSERT INTO exm.exam_sessions (tenant_id, examination_id, exam_subject_id, centre_id, session_date, start_time, end_time, status, created_by)
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'SCHEDULED', $8)
         RETURNING id`,
        [auth.tenantId, data.examinationId, exam.rows[0].exam_subject_id, centre.rows[0].id, data.sessionDate, data.startTime, data.endTime, auth.userId]
      );
      const version = await client.query(
        `INSERT INTO exm.schedule_versions (tenant_id, examination_id, version_no, status, created_by)
         VALUES ($1, $2, 1, 'PUBLISHED', $3)
         RETURNING id`,
        [auth.tenantId, data.examinationId, auth.userId]
      );
      await client.query(
        `INSERT INTO exm.schedule_entries (tenant_id, schedule_version_id, session_id, room_id, seat_from, seat_to, created_by)
         VALUES ($1, $2, $3, $4, 1, $5, $6)`,
        [auth.tenantId, version.rows[0].id, session.rows[0].id, room.rows[0].id, data.capacity, auth.userId]
      );
      await client.query(
        `UPDATE exm.examinations SET status = 'SCHEDULED', updated_by = $2 WHERE id = $1 AND status = 'REGISTRATION_OPEN'`,
        [data.examinationId, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (tenant_id, college_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail)
         VALUES ($1, $2, now(), 'EXAM_SCHEDULE_PUBLISHED', 'COLLEGE_ADMIN', $3, 'EXAM_SESSION', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, exam.rows[0].college_id, auth.userId, session.rows[0].id, JSON.stringify({ room: room.rows[0].code })]
      );
      return { centre: centre.rows[0].code, room: room.rows[0].code, sessionId: session.rows[0].id };
    });
    return NextResponse.json({ success: true, message: 'Schedule published for one session. No hall ticket was issued.', schedule: created }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') return problem(409, 'Conflict', 'That centre, room, or schedule already exists.');
    console.error('POST /api/college/examinations/schedule', err);
    return problem(500, 'Server Error', 'The schedule could not be saved.');
  }
}
