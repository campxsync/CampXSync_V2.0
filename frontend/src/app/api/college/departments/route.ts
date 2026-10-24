import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const CreateDepartmentSchema = z.object({
  name: z.string().min(2, 'Department name must be at least 2 characters').max(100),
  code: z
    .string()
    .min(2, 'Department code must be at least 2 characters')
    .max(20, 'Department code must be at most 20 characters')
    .regex(/^[A-Z0-9_-]+$/, 'Code must contain only uppercase alphanumeric characters, underscores, or hyphens'),
});

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    // 1. Enforce College Admin role
    if (!resolution.success || resolution.session.role !== 'college_admin') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only college administrators can configure academic departments.',
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
    const parseResult = CreateDepartmentSchema.safeParse(body);
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

    const { name, code } = parseResult.data;

    const gatewayUrl = new URL('/api/v1/college-admin/departments', GATEWAY_BASE_URL);
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

    const payload = {
      name,
      departmentCode: code,
      collegeId: resolution.session.collegeId || null,
      headUserId: 'FACULTY_HOD',
    };

    const gatewayRes = await fetch(gatewayUrl.toString(), {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });

    const data = await gatewayRes.json();
    if (!gatewayRes.ok) {
      return NextResponse.json(
        {
          type: data.type || 'https://campxsync.edu/errors/gateway-error',
          title: data.title || 'Department Creation Failed',
          status: gatewayRes.status,
          detail: data.detail || data.message || 'Error communicating with college admin service.',
        },
        { status: gatewayRes.status }
      );
    }

    return NextResponse.json(
      {
        success: true,
        message: `Department '${name}' (${code}) created successfully.`,
        department: data,
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
