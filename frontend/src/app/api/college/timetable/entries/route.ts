import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { query } from '@/lib/db';

const CreateTimetableEntrySchema = z.object({
  timetableVersionId: z.string().uuid('Valid timetable version ID required'),
  sectionId: z.string().uuid('Valid section ID required'),
  subjectId: z.string().uuid('Valid subject ID required'),
  employeeId: z.string().uuid('Valid faculty employee ID required'),
  dayOfWeek: z.number().int().min(0).max(6),
  startTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/, 'Time format must be HH:MM or HH:MM:SS'),
  endTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/, 'Time format must be HH:MM or HH:MM:SS'),
  entryType: z.enum(['LECTURE', 'LAB', 'TUTORIAL', 'SEMINAR']).default('LECTURE'),
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
          detail: 'Only college administrators can manage timetable slots.',
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
    const parseResult = CreateTimetableEntrySchema.safeParse(body);
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

    const { timetableVersionId, sectionId, subjectId, employeeId, dayOfWeek, startTime, endTime, entryType } = parseResult.data;

    // 1. Verify tenant ownership of subject
    const subRes = await query(`
      SELECT id FROM acd.subjects
      WHERE id = $1 AND tenant_id = $2 AND deleted_at IS NULL
      LIMIT 1
    `, [subjectId, tenantId]);

    if (subRes.rows.length === 0) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/not-found',
          title: 'Subject Not Found',
          status: 404,
          detail: 'Subject does not exist in your tenant workspace.',
        },
        { status: 404 }
      );
    }

    // 2. Safeguard trigger acd.guard_timetable_entry: ensure section_faculty assignment exists
    await query(`
      INSERT INTO acd.section_faculty (tenant_id, section_id, employee_id, role)
      VALUES ($1, $2, $3, 'PRIMARY')
      ON CONFLICT DO NOTHING
    `, [tenantId, sectionId, employeeId]);

    // 3. Insert timetable entry
    const entryRes = await query(`
      INSERT INTO acd.timetable_entries (
        tenant_id, timetable_version_id, section_id, subject_id, employee_id,
        day_of_week, start_time, end_time, entry_type, valid_from
      )
      VALUES ($1, $2, $3, $4, $5, $6, $7, $8, $9, $10)
      RETURNING id, day_of_week, start_time, end_time, entry_type, created_at
    `, [
      tenantId,
      timetableVersionId,
      sectionId,
      subjectId,
      employeeId,
      dayOfWeek,
      startTime,
      endTime,
      entryType,
      new Date().toISOString().split('T')[0],
    ]);

    const entry = entryRes.rows[0];

    // 4. Record Audit Event in audit.events
    await query(`
      INSERT INTO audit.events (
        tenant_id, event_time, action, principal_type, principal_id, resource_type, resource_id, status, severity, detail
      )
      VALUES ($1, now(), 'TIMETABLE_ENTRY_CREATED', 'COLLEGE_ADMIN', $2, 'TIMETABLE_ENTRY', $3, 'SUCCESS', 'INFO', $4)
    `, [
      tenantId,
      resolution.session.userId || null,
      entry.id,
      JSON.stringify({ timetableVersionId, sectionId, subjectId, employeeId, dayOfWeek, startTime, endTime, entryType }),
    ]);

    return NextResponse.json(
      {
        success: true,
        message: 'Timetable entry added successfully.',
        entry,
      },
      { status: 201 }
    );
  } catch (err: any) {
    if (err?.message?.includes('ex_tte_employee')) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/conflict',
          title: 'Faculty Schedule Conflict',
          status: 409,
          detail: 'The assigned faculty member is already scheduled for another class during this time slot.',
        },
        { status: 409 }
      );
    }

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
