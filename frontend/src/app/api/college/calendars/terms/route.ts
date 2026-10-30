import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { withTransaction } from '@/lib/db';

const DateText = z.string().regex(/^\d{4}-\d{2}-\d{2}$/);

const AddTermSchema = z.object({
  calendarId: z.string().uuid(),
  termNo: z.number().int().min(1).max(16),
  name: z.string().min(2).max(120),
  termType: z.enum(['SEMESTER', 'TRIMESTER', 'QUARTER', 'SUMMER', 'WINTER']),
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
          detail: 'Only college administrators can set academic terms.',
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

    const parsed = AddTermSchema.safeParse(await req.json());
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

    const { calendarId, termNo, name, termType, startDate, endDate } = parsed.data;
    if (endDate <= startDate) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: 'The term end date must be after the start date.',
        },
        { status: 400 }
      );
    }

    const inserted = await withTransaction(async (client) => {
      const calendarRes = await client.query(
        `SELECT c.id, c.status, c.college_id, c.academic_year_id
         FROM core.calendars c
         WHERE c.id = $1 AND c.tenant_id = $2 AND c.deleted_at IS NULL
         FOR UPDATE`,
        [calendarId, tenantId]
      );
      const calendar = calendarRes.rows[0];
      if (!calendar) return { error: 'not_found' as const };
      if (crossesCollege(resolution.session.assignedCollegeId, calendar.college_id)) {
        return { error: 'other_college' as const };
      }
      if (calendar.status !== 'DRAFT') {
        return { error: 'locked' as const };
      }

      if (calendar.academic_year_id) {
        const yearRes = await client.query(
          `SELECT 1 FROM core.academic_years
           WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
             AND $3::date >= start_date AND $4::date <= end_date`,
          [calendar.academic_year_id, tenantId, startDate, endDate]
        );
        if (yearRes.rows.length === 0) return { error: 'outside_year' as const };
      }

      const overlapRes = await client.query(
        `SELECT 1 FROM acd.calendar_terms
         WHERE tenant_id = $1 AND calendar_id = $2 AND deleted_at IS NULL
           AND start_date <= $4::date AND end_date >= $3::date
         LIMIT 1`,
        [tenantId, calendarId, startDate, endDate]
      );
      if (overlapRes.rows.length > 0) return { error: 'overlap' as const };

      const termRes = await client.query(
        `INSERT INTO acd.calendar_terms (
           tenant_id, calendar_id, term_no, name, term_type, start_date, end_date, status, created_by, updated_by
         )
         VALUES ($1, $2, $3, $4, $5, $6, $7, 'PLANNED', $8, $8)
         RETURNING id, term_no, name, status`,
        [tenantId, calendarId, termNo, name, termType, startDate, endDate, userId]
      );
      return { term: termRes.rows[0] };
    });

    if ('error' in inserted && inserted.error) {
      const errors: Record<string, { title: string; status: number; detail: string }> = {
        not_found: { title: 'Calendar Not Found', status: 404, detail: 'That calendar does not exist in this institute.' },
        other_college: { title: 'Forbidden', status: 403, detail: 'That calendar belongs to another college.' },
        locked: { title: 'Calendar Locked', status: 409, detail: 'Terms can be added only while the calendar is a draft.' },
        outside_year: { title: 'Outside Academic Year', status: 400, detail: 'The term dates must fall inside the academic year.' },
        overlap: { title: 'Overlapping Term', status: 409, detail: 'Those dates overlap another term on this calendar.' },
      };
      const match = errors[inserted.error];
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/calendar', title: match.title, status: match.status, detail: match.detail },
        { status: match.status }
      );
    }

    return NextResponse.json({ success: true, message: `Term '${name}' added.`, term: inserted.term }, { status: 201 });
  } catch (err: any) {
    if (err?.code === '23505') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Term',
          status: 409,
          detail: 'That term number is already on this calendar.',
        },
        { status: 409 }
      );
    }
    console.error('POST /api/college/calendars/terms error:', err);
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
