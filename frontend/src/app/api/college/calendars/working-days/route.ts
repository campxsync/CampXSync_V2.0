import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query } from '@/lib/db';

const WorkingDaySchema = z.object({
  calendarId: z.string().uuid(),
  dayOfWeek: z.number().int().min(0).max(6),
  isWorking: z.boolean(),
  isHalfDay: z.boolean(),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);
    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can set working days.',
        },
        { status: 403 }
      );
    }

    const tenantId = resolution.session.tenantId;
    const userId = resolution.session.userId;
    if (!tenantId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/bad-request',
          title: 'Missing Tenant Context',
          status: 400,
          detail: 'No active tenant linked to user session.',
        },
        { status: 400 }
      );
    }

    const parsed = WorkingDaySchema.safeParse(await req.json());
    if (!parsed.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: parsed.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { calendarId, dayOfWeek, isWorking } = parsed.data;
    const isHalfDay = parsed.data.isHalfDay && isWorking;

    const calendarRes = await query(
      `SELECT status, college_id
       FROM core.calendars
       WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
       LIMIT 1`,
      [calendarId, tenantId]
    );
    const calendar = calendarRes.rows[0];
    if (!calendar) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Calendar Not Found',
          status: 404,
          detail: 'That calendar does not exist in this institute.',
        },
        { status: 404 }
      );
    }
    if (crossesCollege(resolution.session.assignedCollegeId, calendar.college_id)) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'That calendar belongs to another college.',
        },
        { status: 403 }
      );
    }
    if (calendar.status === 'ARCHIVED') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Calendar Archived',
          status: 409,
          detail: 'An archived calendar cannot change its working days.',
        },
        { status: 409 }
      );
    }

    const saved = await query(
      `INSERT INTO core.calendar_working_days (
         tenant_id, calendar_id, day_of_week, is_working, is_half_day, created_by, updated_by
       )
       VALUES ($1, $2, $3, $4, $5, $6, $6)
       ON CONFLICT (tenant_id, calendar_id, day_of_week) WHERE deleted_at IS NULL
       DO UPDATE SET
         is_working = EXCLUDED.is_working,
         is_half_day = EXCLUDED.is_half_day,
         updated_by = EXCLUDED.updated_by,
         updated_at = now(),
         row_version = core.calendar_working_days.row_version + 1
       RETURNING day_of_week, is_working, is_half_day`,
      [tenantId, calendarId, dayOfWeek, isWorking, isHalfDay, userId]
    );

    return NextResponse.json({ success: true, day: saved.rows[0] });
  } catch (err: any) {
    console.error('POST /api/college/calendars/working-days error:', err);
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred.',
      },
      { status: 500 }
    );
  }
}
