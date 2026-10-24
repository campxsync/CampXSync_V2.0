import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

/**
 * Cutover ACD-06 Attendance Corrections Route.
 * Routes directly to authoritative CampXSync API Gateway (/api/v1/academics/attendance/corrections)
 * backed by Java Attendance Management Domain Service.
 * Direct database access (lib/db.ts) has been removed in compliance with Wave 7 attendance corrections cutover.
 */

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const CorrectAttendanceSchema = z.object({
  recordId: z.string().uuid('Valid attendance record ID required'),
  sessionId: z.string().uuid('Valid session ID required'),
  newStatus: z.enum(['PRESENT', 'ABSENT', 'LATE', 'ON_DUTY', 'LEAVE', 'EXCUSED'], {
    errorMap: () => ({ message: 'Status must be PRESENT, ABSENT, LATE, ON_DUTY, LEAVE, or EXCUSED' }),
  }),
  reason: z.string().min(5, 'Mandatory correction reason must be at least 5 characters'),
  expectedVersion: z.number().int().min(1, 'Expected record version is required for optimistic locking'),
  approverId: z.string().uuid().optional(),
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
          detail: 'Unauthorized: insufficient role privileges to submit attendance corrections.',
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
    const parseResult = CorrectAttendanceSchema.safeParse(body);
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

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/academics/attendance/corrections`, {
      method: 'POST',
      headers,
      body: JSON.stringify(parseResult.data),
    });

    const data = await gatewayResponse.json();

    if (!gatewayResponse.ok) {
      return NextResponse.json(
        {
          type: data.type || 'https://campxsync.edu/errors/gateway-error',
          title: data.title || 'Gateway Correction Error',
          status: gatewayResponse.status,
          detail: data.detail || 'Failed to process attendance correction via gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    return NextResponse.json({
      success: true,
      message: data.message || 'Attendance correction processed successfully.',
      record: data.data || data,
    });
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred during attendance correction.',
      },
      { status: 500 }
    );
  }
}

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required to view attendance corrections.',
        },
        { status: 401 }
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

    const { searchParams } = new URL(req.url);
    const sessionId = searchParams.get('sessionId');

    if (!sessionId) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/validation-failed',
          title: 'Missing sessionId Parameter',
          status: 400,
          detail: 'Valid sessionId query parameter is required.',
        },
        { status: 400 }
      );
    }

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'faculty',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/academics/attendance/sessions/${sessionId}/corrections`, {
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
          detail: data.detail || 'Failed to retrieve attendance corrections from gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    return NextResponse.json({
      success: true,
      corrections: data.data || data || [],
    });
  } catch (err: any) {
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: err.message || 'An unexpected error occurred retrieving corrections.',
      },
      { status: 500 }
    );
  }
}
