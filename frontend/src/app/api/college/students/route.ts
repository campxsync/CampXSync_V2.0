import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

/**
 * Cutover STU-01 Student Roster and Lifecycle Route.
 * Routes directly to the authoritative CampXSync API Gateway (/api/v1/students)
 * backed by the Java Student Domain Service.
 * Direct database access has been removed in compliance with Wave 4 Stage C cutover.
 */

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const CreateStudentSchema = z.object({
  firstName: z.string().min(1, 'First name is required').max(50),
  lastName: z.string().min(1, 'Last name is required').max(50),
  email: z.string().email('Valid email address is required'),
  enrollmentNo: z.string().min(3, 'Enrollment number is required').max(30),
  programId: z.string().uuid('Valid degree program ID is required'),
  batchId: z.string().uuid('Valid batch ID required').optional(),
});

const DeleteStudentSchema = z.object({
  id: z.string().uuid('Valid student ID is required'),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can inspect the student roster.',
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

    // Forward query parameters to Java Gateway
    const url = new URL(req.url);
    const gatewayUrl = new URL('/api/v1/students', GATEWAY_BASE_URL);
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
          detail: data.detail || 'Failed to retrieve student roster from gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    // Adapt Java CursorPagedResult<StudentRosterItem> to frontend roster format
    const items = data.items || [];
    const students = items.map((s: any) => ({
      id: s.id,
      enrollment_no: s.enrollmentNo || s.enrollment_no || '',
      status: s.status || 'ACTIVE',
      program_id: s.programId || s.program_id || '',
      program_name: s.programName || s.program_name || '',
      program_code: s.programCode || s.program_code || '',
      batch_id: s.batchId || s.batch_id || null,
      batch_name: s.batchName || s.batch_name || '',
      batch_code: s.batchCode || s.batch_code || '',
      roll_no: s.rollNo || s.roll_no || null,
      display_name: s.displayName || s.display_name || 'Unnamed Student',
      primary_email: s.primaryEmail || s.primary_email || 'No Email',
      created_at: s.createdAt || s.created_at || new Date().toISOString(),
    }));

    return NextResponse.json({
      success: true,
      students,
      nextCursor: data.nextCursor || null,
      hasMore: !!data.hasMore,
      totalCount: data.totalCount || students.length,
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

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can enroll students.',
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
    const parseResult = CreateStudentSchema.safeParse(body);
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

    const { firstName, lastName, email, enrollmentNo, programId, batchId } = parseResult.data;
    const effectiveCollegeId = resolution.session.collegeId || tenantId;

    const gatewayPayload = {
      firstName,
      lastName,
      primaryEmail: email,
      enrollmentNo,
      collegeId: effectiveCollegeId,
      programId,
      batchId: batchId || null,
    };

    const headers: Record<string, string> = {
      'Content-Type': 'application/json',
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'college_admin',
      'X-College-Id': effectiveCollegeId,
    };

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/students`, {
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
          detail: data.detail || 'Failed to enroll student via API Gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    const displayName = data.displayName || `${firstName} ${lastName}`.trim();
    const batchAssigned = !!batchId;

    return NextResponse.json(
      {
        success: true,
        message: `Student '${displayName}' (${enrollmentNo}) enrolled successfully${batchAssigned ? ' and assigned to batch' : ''}.`,
        student: {
          id: data.id,
          enrollment_no: data.enrollmentNo || enrollmentNo,
          status: data.status || 'ACTIVE',
          created_at: data.createdAt || new Date().toISOString(),
          display_name: displayName,
          email: data.primaryEmail || email,
          batch_assigned: batchAssigned,
        },
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

export async function DELETE(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can deactivate students.',
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
    const parseResult = DeleteStudentSchema.safeParse(body);
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

    const { id } = parseResult.data;

    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': resolution.session.role || 'college_admin',
    };

    const gatewayResponse = await fetch(`${GATEWAY_BASE_URL}/api/v1/students/${id}`, {
      method: 'DELETE',
      headers,
    });

    const data = await gatewayResponse.json();

    if (!gatewayResponse.ok) {
      return NextResponse.json(
        {
          type: data.type || 'https://campxsync.edu/errors/gateway-error',
          title: data.title || 'Gateway Error',
          status: gatewayResponse.status,
          detail: data.detail || 'Failed to deactivate student via API Gateway.',
        },
        { status: gatewayResponse.status }
      );
    }

    return NextResponse.json({
      success: true,
      message: data.message || 'Student deactivated successfully.',
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
