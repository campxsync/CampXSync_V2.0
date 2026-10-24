import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

/**
 * Cutover ACD-06 Attendance Records Route.
 * Routes directly to authoritative CampXSync API Gateway (/api/v1/academics/attendance/sessions/:id/records)
 * backed by Java Attendance Management Domain Service.
 * Direct database access has been removed in compliance with Wave 6 relational persistence cutover.
 */

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const AttendanceRecordSchema = z.object({
  studentId: z.string().uuid('Valid student ID required'),
  status: z.enum(['PRESENT', 'ABSENT', 'LATE', 'LEAVE', 'ON_DUTY', 'EXCUSED'], {
    errorMap: () => ({ message: 'Status must be PRESENT, ABSENT, LATE, LEAVE, ON_DUTY, or EXCUSED' }),
  }),
  remarks: z.string().optional(),
});

const SubmitAttendanceSchema = z.object({
  sessionId: z.string().uuid('Valid session ID required'),
  closeSession: z.boolean().default(true),
  records: z.array(AttendanceRecordSchema).min(1, 'At least one student attendance record required'),
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
          detail: 'Unauthorized: only instructors and college administrators can view attendance records.',
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

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/academics/attendance/sessions/${sessionId}/records`, {
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
          detail: data.detail || 'Failed to retrieve attendance records from gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    const records = data.data || data || [];
    const totalRecords = records.length;
    const presentCount = records.filter((r: any) => r.status === 'PRESENT').length;
    const absentCount = records.filter((r: any) => r.status === 'ABSENT').length;
    const leaveCount = records.filter((r: any) => r.status === 'LEAVE').length;
    const lateCount = records.filter((r: any) => r.status === 'LATE').length;
    const onDutyCount = records.filter((r: any) => r.status === 'ON_DUTY').length;
    const attendedCount = presentCount + lateCount + onDutyCount;
    const attendancePercentage = totalRecords > 0 ? Math.round((attendedCount / totalRecords) * 100) : 0;

    return NextResponse.json({
      success: true,
      records,
      summary: {
        totalRecords,
        presentCount,
        absentCount,
        leaveCount,
        lateCount,
        onDutyCount,
        attendancePercentage,
      },
    });
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
          detail: 'Unauthorized: only instructors and college administrators can mark attendance.',
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
    const parseResult = SubmitAttendanceSchema.safeParse(body);
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

    const { sessionId, closeSession, records } = parseResult.data;

    const gatewayPayload = {
      records: records.map((r) => ({
        studentId: r.studentId,
        status: r.status,
        remarks: r.remarks || null,
        source: 'FACULTY',
      })),
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

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/academics/attendance/sessions/${sessionId}/records`, {
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
          detail: data.detail || 'Failed to submit attendance via API Gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    // If closeSession is requested, call submit/lock on gateway
    if (closeSession) {
      await fetch(`${GATEWAY_BASE_URL}/api/v1/academics/attendance/sessions/${sessionId}/submit`, {
        method: 'POST',
        headers,
      });
    }

    return NextResponse.json(
      {
        success: true,
        message: 'Attendance submitted successfully.',
        data: data.data || data,
      },
      { status: 200 }
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
