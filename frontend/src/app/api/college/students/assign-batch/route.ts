import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const AssignBatchSchema = z.object({
  studentId: z.string().uuid('Valid student ID is required'),
  batchId: z.string().uuid('Valid batch ID is required'),
  rollNo: z.string().max(30).optional(),
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
          detail: 'Only college administrators can assign students to academic batches.',
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
    const parseResult = AssignBatchSchema.safeParse(body);
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

    const { studentId, batchId, rollNo } = parseResult.data;

    const gatewayUrl = new URL(`/api/v1/academics/batches/${batchId}/students`, GATEWAY_BASE_URL);
    const headers: Record<string, string> = {
      'Accept': 'application/json',
      'Content-Type': 'application/json',
      'X-User-Id': resolution.session.userId || '00000000-0000-0000-0000-000000000001',
      'X-Tenant-Id': tenantId,
      'X-User-Role': 'COLLEGE_ADMIN',
    };
    if (resolution.session.collegeId) {
      headers['X-College-Id'] = resolution.session.collegeId;
    }

    const gatewayPayload = {
      studentId,
      rollNo: rollNo || undefined,
    };

    const gatewayRes = await fetch(gatewayUrl.toString(), {
      method: 'POST',
      headers,
      body: JSON.stringify(gatewayPayload),
    });

    const data = await gatewayRes.json().catch(() => ({}));

    if (!gatewayRes.ok) {
      return NextResponse.json(
        {
          type: data.type || 'https://campxsync.edu/errors/gateway-error',
          title: data.title || 'Batch Assignment Failed',
          status: gatewayRes.status,
          detail: data.detail || data.message || 'Error communicating with batch management service via API Gateway.',
        },
        { status: gatewayRes.status }
      );
    }

    const assignment = data.data || data;
    const studentName = assignment.studentName || 'Student';
    const batchName = assignment.batchName || 'batch';

    return NextResponse.json({
      success: true,
      message: `Student '${studentName}' successfully assigned to batch '${batchName}'.`,
      data: assignment,
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
