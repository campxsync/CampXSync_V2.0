import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

/**
 * Cutover ACD-05 Timetable Slot and Version Route.
 * Routes directly to authoritative CampXSync API Gateway (/api/v1/academics/timetables/entries)
 * backed by Java Timetable Management Domain Service.
 * Direct database access has been removed in compliance with Wave 5 relational persistence cutover.
 */

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const CreateTimetableEntrySchema = z.object({
  timetableVersionId: z.string().uuid('Valid timetable version ID required'),
  sectionId: z.string().uuid('Valid section ID required'),
  subjectId: z.string().uuid('Valid subject ID required'),
  employeeId: z.string().uuid('Valid faculty employee ID required'),
  roomId: z.string().uuid('Valid room ID required').optional(),
  dayOfWeek: z.number().int().min(0).max(6),
  startTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/, 'Time format must be HH:MM or HH:MM:SS'),
  endTime: z.string().regex(/^\d{2}:\d{2}(:\d{2})?$/, 'Time format must be HH:MM or HH:MM:SS'),
  entryType: z.enum(['LECTURE', 'LAB', 'TUTORIAL', 'SEMINAR']).default('LECTURE'),
  validFrom: z.string().optional(),
  validTo: z.string().optional(),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Unauthorized session.',
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

    const url = new URL(req.url);
    const gatewayUrl = new URL('/api/v1/academics/timetables/entries', GATEWAY_BASE_URL);
    url.searchParams.forEach((value, key) => {
      gatewayUrl.searchParams.set(key, value);
    });

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'college_admin',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const gatewayResponse = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
    });

    const data = await gatewayResponse.json();

    if (!gatewayResponse.ok) {
      return NextResponse.json(
        {
          type: data.type || 'https://campxsync.edu/errors/gateway-error',
          title: data.title || 'Gateway Error',
          status: gatewayResponse.status,
          detail: data.detail || 'Failed to retrieve timetable entries from gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    return NextResponse.json(data, { status: 200 });
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

    const payload = parseResult.data;

    // Normalize HH:MM to HH:MM:00 if needed for Java LocalTime
    const normalizeTime = (t: string) => (t.length === 5 ? `${t}:00` : t);

    const gatewayPayload = {
      timetableVersionId: payload.timetableVersionId,
      sectionId: payload.sectionId,
      subjectId: payload.subjectId,
      employeeId: payload.employeeId,
      roomId: payload.roomId || null,
      dayOfWeek: payload.dayOfWeek,
      startTime: normalizeTime(payload.startTime),
      endTime: normalizeTime(payload.endTime),
      slotType: payload.entryType,
      validFrom: payload.validFrom || new Date().toISOString().split('T')[0],
      validTo: payload.validTo || null,
    };

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'college_admin',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/academics/timetables/entries`, {
      method: 'POST',
      headers,
      body: JSON.stringify(gatewayPayload),
    });

    const data = await gatewayResponse.json();

    if (!gatewayResponse.ok) {
      return NextResponse.json(
        {
          type: data.type || 'https://campxsync.edu/errors/gateway-error',
          title: data.title || 'Gateway Error',
          status: gatewayResponse.status,
          detail: data.detail || 'Failed to create timetable slot via API Gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    return NextResponse.json(
      {
        success: true,
        message: 'Timetable entry added successfully.',
        entry: data,
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
