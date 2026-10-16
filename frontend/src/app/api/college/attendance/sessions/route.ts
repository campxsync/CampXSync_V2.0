import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const CreateSessionSchema = z.object({
  timetableEntryId: z.string().uuid('Valid timetable entry ID required').optional(),
  sectionId: z.string().uuid('Valid section ID required').optional(),
  employeeId: z.string().uuid('Valid faculty employee ID required').optional(),
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

    let { timetableEntryId, sectionId, employeeId, sessionDate, startTime, endTime } = parseResult.data;

    // 1. Backend Enforcement: Verify timetable entry validity within tenant context
    if (timetableEntryId) {
      const entryRes = await query(`
        SELECT tte.id, tte.section_id, tte.employee_id, tte.start_time, tte.end_time
        FROM acd.timetable_entries tte
        WHERE tte.id = $1 AND tte.tenant_id = $2 AND tte.deleted_at IS NULL
        LIMIT 1
      `, [timetableEntryId, tenantId]);

      if (entryRes.rows.length === 0) {
        return NextResponse.json(
          {
            type: 'https://campxsync.edu/errors/not-found',
            title: 'Timetable Entry Not Found',
            status: 404,
            detail: 'The specified timetable slot does not exist or does not belong to your institution.',
          },
          { status: 404 }
        );
      }

      const validEntry = entryRes.rows[0];
      if (!sectionId) sectionId = validEntry.section_id;
      if (!employeeId) employeeId = validEntry.employee_id;
      if (!startTime) startTime = validEntry.start_time?.slice(0, 8);
      if (!endTime) endTime = validEntry.end_time?.slice(0, 8);
    }

    if (!sectionId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Section Required',
          status: 400,
          detail: 'Section identifier is required to open an attendance session.',
        },
        { status: 400 }
      );
    }

    // 2. Prevent Duplicate Sessions: Check if session already exists for this entry/section and date
    let existingQuery = '';
    let existingParams: any[] = [];

    if (timetableEntryId) {
      existingQuery = `
        SELECT id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, created_at, locked_at
        FROM acd.attendance_sessions
        WHERE tenant_id = $1 AND timetable_entry_id = $2 AND session_date = $3 AND deleted_at IS NULL
        LIMIT 1
      `;
      existingParams = [tenantId, timetableEntryId, sessionDate];
    } else {
      existingQuery = `
        SELECT id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, created_at, locked_at
        FROM acd.attendance_sessions
        WHERE tenant_id = $1 AND section_id = $2 AND session_date = $3 AND deleted_at IS NULL
        LIMIT 1
      `;
      existingParams = [tenantId, sectionId, sessionDate];
    }

    const existingRes = await query(existingQuery, existingParams);

    if (existingRes.rows.length > 0) {
      const existingSession = existingRes.rows[0];
      return NextResponse.json(
        {
          success: true,
          message: 'Attendance session already exists for this slot and date. Existing session retrieved.',
          session: existingSession,
          isExisting: true,
        },
        { status: 200 }
      );
    }

    // 3. Fallback employee ID if not assigned to slot
    if (!employeeId) {
      const empRes = await query(`
        SELECT id FROM hrm.employees 
        WHERE tenant_id = $1 AND deleted_at IS NULL 
        LIMIT 1
      `, [tenantId]);
      if (empRes.rows.length > 0) {
        employeeId = empRes.rows[0].id;
      }
    }

    // 4. Insert new attendance session with OPEN state
    const insertRes = await query(`
      INSERT INTO acd.attendance_sessions (
        tenant_id, 
        section_id, 
        timetable_entry_id, 
        employee_id, 
        session_date, 
        start_time, 
        end_time, 
        status
      ) VALUES ($1, $2, $3, $4, $5, $6, $7, 'OPEN')
      RETURNING id, section_id, timetable_entry_id, employee_id, session_date, start_time, end_time, status, created_at
    `, [
      tenantId,
      sectionId,
      timetableEntryId || null,
      employeeId || null,
      sessionDate,
      startTime || '10:00:00',
      endTime || '11:00:00'
    ]);

    const session = insertRes.rows[0];

    // 5. Append audit event in audit.events
    try {
      await query(`
        INSERT INTO audit.events (
          tenant_id,
          event_time,
          action,
          resource_type,
          resource_id,
          status,
          severity,
          detail,
          created_at
        ) VALUES (
          $1, now(), 'ATTENDANCE_SESSION_OPENED', 'ATTENDANCE_SESSION', $2, 'SUCCESS', 'INFO',
          $3, now()
        )
      `, [
        tenantId,
        session.id,
        JSON.stringify({
          timetableEntryId,
          sectionId,
          sessionDate,
          openedBy: resolution.session.displayName || 'Faculty Member',
          role: resolution.session.role,
        }),
      ]);
    } catch (auditErr: any) {
      console.warn('Failed to record session open audit event:', auditErr.message);
    }

    return NextResponse.json(
      {
        success: true,
        message: 'Attendance session opened successfully.',
        session,
        isExisting: false,
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
