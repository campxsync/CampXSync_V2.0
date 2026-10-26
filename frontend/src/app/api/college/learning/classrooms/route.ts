import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { query, withTransaction } from '@/lib/db';
import { problem, requireCollegeAdmin } from '../guard';

const Schema = z.object({
  name: z.string().min(2).max(160),
  provider: z.enum(['ZOOM', 'MEET', 'TEAMS', 'JITSI', 'BIGBLUEBUTTON', 'OTHER']),
  title: z.string().min(2).max(160),
  scheduledStart: z.string().datetime(),
  scheduledEnd: z.string().datetime(),
});

export async function POST(req: NextRequest) {
  try {
    const auth = await requireCollegeAdmin('Only college administrators can schedule a classroom.');
    if ('error' in auth) return auth.error;
    const parsed = Schema.safeParse(await req.json());
    if (!parsed.success) return problem(400, 'Validation Failed', parsed.error.errors.map((e) => e.message).join(', '));
    const data = parsed.data;
    if (data.scheduledEnd <= data.scheduledStart) return problem(400, 'Validation Failed', 'The session must end after it starts.');

    const created = await withTransaction(async (client) => {
      const room = await client.query(
        `INSERT INTO lms.virtual_classrooms (tenant_id, name, provider, status, created_by)
         VALUES ($1, $2, $3, 'ACTIVE', $4)
         RETURNING id, name, provider`,
        [auth.tenantId, data.name, data.provider, auth.userId]
      );
      const session = await client.query(
        `INSERT INTO lms.virtual_sessions (tenant_id, classroom_id, title, scheduled_start, scheduled_end, status, created_by)
         VALUES ($1, $2, $3, $4, $5, 'SCHEDULED', $6)
         RETURNING id`,
        [auth.tenantId, room.rows[0].id, data.title, data.scheduledStart, data.scheduledEnd, auth.userId]
      );
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         ) VALUES ($1, $2, now(), 'CLASSROOM_SCHEDULED', 'COLLEGE_ADMIN', $3, 'VIRTUAL_CLASSROOM', $4, 'SUCCESS', 'INFO', $5)`,
        [auth.tenantId, auth.collegeId, auth.userId, room.rows[0].id, JSON.stringify({ sessionId: session.rows[0].id, provider: data.provider })]
      );
      return room.rows[0];
    });
    return NextResponse.json({ success: true, message: 'Classroom scheduled. No meeting provider is called from this screen.', classroom: created }, { status: 201 });
  } catch (err) {
    console.error('POST /api/college/learning/classrooms', err);
    return problem(500, 'Server Error', 'The classroom could not be scheduled.');
  }
}
