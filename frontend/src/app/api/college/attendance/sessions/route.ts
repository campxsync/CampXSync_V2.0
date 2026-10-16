import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const CreateSessionSchema = z.object({
  timetableEntryId: z.string().uuid('Valid timetable entry ID required').optional(),
  sectionId: z.string().uuid('Valid section ID required'),
  employeeId: z.string().uuid('Valid faculty employee ID required'),
  sessionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Date format must be YYYY-MM-DD'),
  startTime: z.string().optional(),
  endTime: z.string().optional(),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (
      !resolution.success ||
      (resolution.session.role !== 'college_admin' && resolution.session.role !== 'faculty')
    ) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Unauthorized: only instructors and college administrators can create attendance sessions.',
        },
        { status: 403 }
      );
    }

    const tenantId = resolution.session.tenantId;
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

    const body = await req.json();
    const parseResult = CreateSessionSchema.safeParse(body);
    if (!parseResult.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Validation Failed',
          status: 400,
          detail: parseResult.error.errors.map((e) => `${e.path.join('.')}: ${e.message}`).join(', '),
        },
        { status: 400 }
      );
    }

    const { timetableEntryId, sectionId, employeeId, sessionDate, startTime, endTime } = parseResult.data;

    // Check if session already exists for this entry and date
    if (timetableEntryId) {
      const existingRes = await query(`
        SELECT id, status, session_date
        FROM acd.attendance_sessions
        WHERE tenant_id = $1 AND timetable_entry_id = $2 AND session_date = $3 AND deleted_at IS NULL
        LIMIT 1
      `, [tenantId, timetableEntryId, sessionDate]);

      if (existingRes.rows.length > 0) {
        return NextResponse.json(
          {
            success: true,
            message: 'Attendance session already exists for this slot and date.',
            session: existingRes.rows[0],
          },
          { status: 200 }
        );
      }
    }

    // Insert attendance session
    const insertRes = await query(`
      INSERT INTO acd.attendance_sessions (tenant_id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status)
      VALUES ($1, $2, $3, $4, $5, $6, $7, 'OPEN')
      RETURNING id, section_id, session_date, start_time, end_time, status, created_at
    `, [
      tenantId,
      sectionId,
      timetableEntryId || null,
      employeeId,
      sessionDate,
      startTime || '10:00:00',
      endTime || '11:00:00'
    ]);

    const session = insertRes.rows[0];

    return NextResponse.json(
      {
        success: true,
        message: 'Attendance session opened successfully.',
        session,
      },
      { status: 201 }
    );
  } catch (err: any) {
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
