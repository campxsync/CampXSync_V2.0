import { NextRequest, NextResponse } from 'next/server';
import { z } from 'zod';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

const CreateIntakeSchema = z.object({
  programId: z.string().uuid('Valid program UUID is required'),
  academicYearId: z.string().uuid().optional(),
  intakeName: z.string().min(2, 'Intake name must be at least 2 characters'),
  intakeCode: z.string().min(2, 'Intake code is required'),
  applicationWindowStart: z.string(),
  applicationWindowEnd: z.string(),
  capacity: z.number().int().positive('Capacity must be positive'),
  feeRequired: z.boolean().default(false),
  feeAmount: z.number().nullable().optional(),
  currency: z.string().default('INR'),
  feeTiming: z.string().default('PRE_SUBMISSION'),
});

export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403 }, { status: 403 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'COLLEGE_ADMIN');
    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/intakes/active`;
    const res = await fetch(gatewayUrl, {
      method: 'GET',
      headers,
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

export async function POST(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success || (resolution.session.role !== 'college_admin' && resolution.session.role !== 'platform_admin')) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/forbidden', title: 'Forbidden', status: 403 }, { status: 403 });
    }

    const { tenantId, userId, collegeId } = resolution.session;
    if (!tenantId) {
      return NextResponse.json({ type: 'https://campxsync.edu/errors/bad-request', title: 'Missing Tenant Context', status: 400 }, { status: 400 });
    }

    const rawBody = await req.json();
    const parsed = CreateIntakeSchema.safeParse(rawBody);
    if (!parsed.success) {
      return NextResponse.json({ error: 'Validation failed', details: parsed.error.format() }, { status: 422 });
    }

    const payload = {
      tenantId,
      collegeId: collegeId || '11111111-2222-3333-4444-555555555555',
      ...parsed.data,
      academicYearId: parsed.data.academicYearId || '11111111-aaaa-bbbb-cccc-000000000001',
    };

    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'COLLEGE_ADMIN');
    headers['Content-Type'] = 'application/json';

    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/admissions/intakes`;
    const res = await fetch(gatewayUrl, {
      method: 'POST',
      headers,
      body: JSON.stringify(payload),
    });

    if (!res.ok) {
      const err = await res.text();
      return NextResponse.json({ error: err }, { status: res.status });
    }

    const data = await res.json();
    return NextResponse.json(data, { status: 201 });
  } catch (err: any) {
    return NextResponse.json({ error: err.message || 'Internal Server Error' }, { status: 500 });
  }
}
