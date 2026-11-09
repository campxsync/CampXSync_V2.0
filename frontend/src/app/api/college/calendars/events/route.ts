import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query } from '@/lib/db';

const DateText = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

const AddEventSchema = z.object({
  calendarId: z.string().uuid(),
  eventType: z.enum(['HOLIDAY', 'TERM_START', 'TERM_END', 'EXAM', 'COMMENCEMENT', 'EVENT', 'DEADLINE', 'OTHER']),
  title: z.string().min(2).max(160),
  startDate: DateText,
  endDate: DateText,
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
          detail: 'Only college administrators can record calendar events.',
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

    const parsed = AddEventSchema.safeParse(await req.json());
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

    const { calendarId, eventType, title, startDate, endDate } = parsed.data;
    if (endDate < startDate) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: 'The event end date cannot be before the start date.',
        },
        { status: 400 }
      );
    }

    const calendarRes = await query(
      `SELECT c.status, c.college_id, c.academic_year_id
       FROM core.calendars c
       WHERE c.id = $1 AND c.tenant_id = $2 AND c.deleted_at IS NULL
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
          detail: 'An archived calendar cannot take new events.',
        },
        { status: 409 }
      );
    }

    if (calendar.academic_year_id) {
      const yearRes = await query(
        `SELECT 1 FROM core.academic_years
         WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
           AND $3::date >= start_date AND $4::date <= end_date`,
        [calendar.academic_year_id, tenantId, startDate, endDate]
      );
      if (yearRes.rows.length === 0) {
        return NextResponse.json(
          {
            type: 'https://campxsync.edu/errors/validation-failed',
            title: 'Outside Academic Year',
            status: 400,
            detail: 'The event dates must fall inside the academic year.',
          },
          { status: 400 }
        );
      }
    }

    const inserted = await query(
      `INSERT INTO core.calendar_events (
         tenant_id, calendar_id, event_type, title, start_date, end_date, is_holiday, created_by, updated_by
       )
       VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $8)
       RETURNING id, event_type, title, start_date::text AS start_date, end_date::text AS end_date, is_holiday`,
      [tenantId, calendarId, eventType, title, startDate, endDate, eventType === 'HOLIDAY', userId]
    );

    return NextResponse.json(
      { success: true, message: `'${title}' recorded on the calendar.`, event: inserted.rows[0] },
      { status: 201 }
    );
  } catch (err: any) {
    console.error('POST /api/college/calendars/events error:', err);
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
