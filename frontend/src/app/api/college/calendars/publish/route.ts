import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { withTransaction } from '@/lib/db';

const PublishSchema = z.object({
  calendarId: z.string().uuid(),
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
          detail: 'Only college administrators can publish a calendar.',
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

    const parsed = PublishSchema.safeParse(await req.json());
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

    const { calendarId } = parsed.data;
    const published = await withTransaction(async (client) => {
      const calendarRes = await client.query(
        `SELECT id, status, college_id
         FROM core.calendars
         WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
         FOR UPDATE`,
        [calendarId, tenantId]
      );
      const calendar = calendarRes.rows[0];
      if (!calendar) return { error: 'not_found' as const };
      if (crossesCollege(resolution.session.assignedCollegeId, calendar.college_id)) {
        return { error: 'other_college' as const };
      }
      if (calendar.status !== 'DRAFT') return { error: 'locked' as const };

      const termRes = await client.query(
        `SELECT count(*)::int AS term_count
         FROM acd.calendar_terms
         WHERE tenant_id = $1 AND calendar_id = $2 AND deleted_at IS NULL`,
        [tenantId, calendarId]
      );
      if ((termRes.rows[0].term_count as number) < 1) return { error: 'empty' as const };

      const updated = await client.query(
        `UPDATE core.calendars
         SET status = 'PUBLISHED',
             published_at = now(),
             updated_by = $3,
             updated_at = now(),
             row_version = row_version + 1
         WHERE id = $1 AND tenant_id = $2
         RETURNING id, status, published_at`,
        [calendarId, tenantId, userId]
      );

      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         )
         VALUES ($1, $2, now(), 'CALENDAR_PUBLISHED', 'COLLEGE_ADMIN', $3, 'CALENDAR', $4, 'SUCCESS', 'INFO', $5)`,
        [tenantId, calendar.college_id, userId, calendarId, JSON.stringify({ termCount: termRes.rows[0].term_count })]
      );

      return { calendar: updated.rows[0] };
    });

    if ('error' in published && published.error) {
      const errors: Record<string, { title: string; status: number; detail: string }> = {
        not_found: { title: 'Calendar Not Found', status: 404, detail: 'That calendar does not exist in this institute.' },
        other_college: { title: 'Forbidden', status: 403, detail: 'That calendar belongs to another college.' },
        locked: { title: 'Calendar Locked', status: 409, detail: 'Only a draft calendar can be published.' },
        empty: { title: 'Empty Calendar', status: 400, detail: 'Add at least one term before publishing the calendar.' },
      };
      const match = errors[published.error];
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/calendar', title: match.title, status: match.status, detail: match.detail },
        { status: match.status }
      );
    }

    return NextResponse.json({
      success: true,
      message: 'Calendar published. Terms are now fixed. Holidays and working days can still be updated.',
      calendar: published.calendar,
    });
  } catch (err: any) {
    console.error('POST /api/college/calendars/publish error:', err);
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
