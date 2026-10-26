import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { crossesCollege } from '@/lib/auth/college-scope';
import { query, withTransaction } from '@/lib/db';

const CreateCalendarSchema = z.object({
  collegeId: z.string().uuid('Valid college is required'),
  academicYearId: z.string().uuid('Valid academic year is required'),
  code: z
    .string()
    .min(2)
    .max(32)
    .regex(/^[A-Z0-9_-]+$/, 'Code must contain only uppercase letters, numbers, underscores, or hyphens'),
  name: z.string().min(2).max(160),
  calendarType: z.enum(['ACADEMIC', 'HOLIDAY', 'EXAM', 'ADMINISTRATIVE']),
});

const WORKING_WEEK = [
  { day: 0, working: false },
  { day: 1, working: true },
  { day: 2, working: true },
  { day: 3, working: true },
  { day: 4, working: true },
  { day: 5, working: true },
  { day: 6, working: false },
];

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
          detail: 'Only college administrators can maintain the academic calendar.',
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

    const parsed = CreateCalendarSchema.safeParse(await req.json());
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

    const { collegeId, academicYearId, code, name, calendarType } = parsed.data;
    const collegeRes = await query(
      `SELECT id FROM core.colleges
       WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
       LIMIT 1`,
      [collegeId, tenantId]
    );
    if (collegeRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'College Not Found',
          status: 404,
          detail: 'That college is not in this institute.',
        },
        { status: 404 }
      );
    }
    if (crossesCollege(resolution.session.assignedCollegeId, collegeId)) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'That college is outside this office.',
        },
        { status: 403 }
      );
    }

    const yearRes = await query(
      `SELECT id FROM core.academic_years
       WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
       LIMIT 1`,
      [academicYearId, tenantId]
    );
    if (yearRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Academic Year Not Found',
          status: 404,
          detail: 'That academic year is not in this institute.',
        },
        { status: 404 }
      );
    }

    const created = await withTransaction(async (client) => {
      const calendarRes = await client.query(
        `INSERT INTO core.calendars (
           tenant_id, college_id, academic_year_id, code, name, calendar_type, status, week_start_day, created_by, updated_by
         )
         VALUES ($1, $2, $3, $4, $5, $6, 'DRAFT', 1, $7, $7)
         RETURNING id, code, name, status`,
        [tenantId, collegeId, academicYearId, code, name, calendarType, userId]
      );
      const calendar = calendarRes.rows[0];
      for (const day of WORKING_WEEK) {
        await client.query(
          `INSERT INTO core.calendar_working_days (
             tenant_id, calendar_id, day_of_week, is_working, is_half_day, created_by, updated_by
           )
           VALUES ($1, $2, $3, $4, false, $5, $5)`,
          [tenantId, calendar.id, day.day, day.working, userId]
        );
      }
      await client.query(
        `INSERT INTO audit.events (
           tenant_id, college_id, event_time, action, principal_type, principal_id,
           resource_type, resource_id, status, severity, detail
         )
         VALUES ($1, $2, now(), 'CALENDAR_CREATED', 'COLLEGE_ADMIN', $3, 'CALENDAR', $4, 'SUCCESS', 'INFO', $5)`,
        [tenantId, collegeId, userId, calendar.id, JSON.stringify({ code, name, calendarType, academicYearId })]
      );
      return calendar;
    });

    return NextResponse.json(
      {
        success: true,
        message: `Calendar '${name}' (${code}) opened as a draft. Monday to Friday are working days.`,
        calendar: created,
      },
      { status: 201 }
    );
  } catch (err: any) {
    if (err?.code === '23505') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Duplicate Calendar Code',
          status: 409,
          detail: 'A calendar with that code already exists in this institute.',
        },
        { status: 409 }
      );
    }
    console.error('POST /api/college/calendars error:', err);
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
