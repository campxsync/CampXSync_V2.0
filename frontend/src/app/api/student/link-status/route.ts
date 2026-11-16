import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

const GATEWAY_BASE_URL = process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

/**
 * BFF: STM-07-US-013 student account-link status via API Gateway → student-service.
 */
export async function GET(req: NextRequest) {
  try {
    const supabase = await createClient();
    const resolution = await resolveUserRole(supabase);

    if (!resolution.success) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/unauthenticated', title: 'Unauthenticated', status: 401 },
        { status: 401 }
      );
    }

    const studentId = req.nextUrl.searchParams.get('studentId');
    if (!studentId) {
      return NextResponse.json(
        { type: 'https://campxsync.edu/errors/bad-request', title: 'studentId is required', status: 400 },
        { status: 400 }
      );
    }

    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
    const gatewayUrl = `${GATEWAY_BASE_URL}/api/v1/students/${encodeURIComponent(studentId)}/link-status`;

    const res = await fetch(gatewayUrl, {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    if (!res.ok) {
      const err = await res.text();
      return NextResponse.json({ error: err }, { status: res.status });
    }

    return NextResponse.json(await res.json());
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Internal Server Error';
    return NextResponse.json({ error: message }, { status: 500 });
  }
}
