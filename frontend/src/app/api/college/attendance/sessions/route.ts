import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

/**
 * Cutover ACD-06 Attendance Sessions Route.
 * Routes directly to authoritative CampXSync API Gateway (/api/v1/academics/attendance/sessions)
 * backed by Java Attendance Management Domain Service.
 * Direct database access has been removed in compliance with Wave 6 relational persistence cutover.
 */

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const CreateSessionSchema = z.object({
  timetableEntryId: z.string().uuid('Valid timetable entry ID required').optional(),
  sectionId: z.string().uuid('Valid section ID required').optional(),
  employeeId: z.string().uuid('Valid faculty employee ID required').optional(),
  sessionDate: z.string().regex(/^\d{4}-\d{2}-\d{2}$/, 'Date format must be YYYY-MM-DD'),
  startTime: z.string().optional(),
  endTime: z.string().optional(),
});

export async function GET(req: NextRequest) {
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
          detail: 'Unauthorized: only instructors and college administrators can view attendance sessions.',
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
    const gatewayUrl = new URL('/api/v1/academics/attendance/sessions', GATEWAY_BASE_URL);
    url.searchParams.forEach((value, key) => {
      gatewayUrl.searchParams.set(key, value);
    });

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'faculty',
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
          detail: data.detail || 'Failed to retrieve attendance sessions from gateway.',
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

    const payload = parseResult.data;

    const gatewayPayload = {
      timetableEntryId: payload.timetableEntryId,
      sectionId: payload.sectionId,
      employeeId: payload.employeeId,
      sessionDate: payload.sessionDate,
      startTime: payload.startTime || '10:00:00',
      endTime: payload.endTime || '11:00:00',
    };

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'faculty',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/academics/attendance/sessions`, {
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
          detail: data.detail || 'Failed to create attendance session via API Gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    return NextResponse.json(
      {
        success: true,
        message: 'Attendance session opened successfully.',
        session: data.data || data,
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
