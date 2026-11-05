import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';
import { query } from '@/lib/db';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const AddressUpdateSchema = z.object({
  addressType: z.enum(['PERMANENT', 'CURRENT']),
  line1: z.string().min(1, 'Address line 1 is required').max(255),
  line2: z.string().max(255).optional().nullable(),
  landmark: z.string().max(255).optional().nullable(),
  city: z.string().min(1, 'City is required').max(100),
  district: z.string().max(100).optional().nullable(),
  stateCode: z.string().min(1, 'State is required').max(50),
  postalCode: z.string().regex(/^\d{6}$/, 'Postal code must be exactly 6 digits'),
  countryCode: z.string().max(3).default('IND'),
});

export async function PUT(
  req: NextRequest,
  { params }: { params: Promise<{ id: string }> }
) {
  try {
    const { id } = await params;
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/unauthenticated', title: 'Unauthenticated', status: 401 },
        { status: 401 }
      );
    }

    const { tenantId, userId, role } = resolution.session;
    if (!tenantId || !userId) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 },
        { status: 400 }
      );
    }

    // Role-based boundary: only students can access student address self-service
    if (role !== 'student') {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403, detail: 'Access denied: student role required' },
        { status: 403 }
      );
    }

    // Strict student ID ownership validation: [id] must match the caller's own student record
    const studentCheck = await query(
      `SELECT s.id FROM stu.students s
       JOIN iam.user_profiles up ON up.person_id = s.person_id
       WHERE up.id = $1 AND s.tenant_id = $2 AND s.deleted_at IS NULL
       LIMIT 1`,
      [userId, tenantId]
    );

    const myStudentId = studentCheck.rows[0]?.id;
    if (!myStudentId || myStudentId !== id) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403, detail: 'Access denied: cannot operate on another student profile' },
        { status: 403 }
      );
    }

    const body = await req.json();
    const parsed = AddressUpdateSchema.safeParse(body);
    if (!parsed.success) {
      return NextResponse.json(
        { error: 'Validation failed', details: parsed.error.format() },
        { status: 400 }
      );
    }

    // Role is derived server-side from session and cannot be spoofed by client headers
    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
    headers['Content-Type'] = 'application/json';

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/students/${id}/address`;
    const res = await fetch(gatewayUrl, {
      method: 'PUT',
      headers,
      body: JSON.stringify(parsed.data),
      cache: 'no-store',
    });

    if (!res.ok) {
      const err = await res.text();
      return NextResponse.json({ error: err }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data);
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}
