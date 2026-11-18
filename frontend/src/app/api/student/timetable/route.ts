import { NextRequest, NextResponse } from 'next/server';
import { createClient } from '@/lib/supabase/server';
import { resolveUserRole } from '@/lib/auth/resolve-role';
import { getGatewayHeaders } from '@/app/api/student/admissions/gateway-auth';

/**
 * STM-10-US-004 — student-owned my timetable BFF.
 * Proxies GET /api/v1/academics/timetables/my through the API Gateway.
 * Never forwards a client-supplied batch or student id.
 */
export const dynamic = 'force-dynamic';

const GATEWAY_BASE_URL =
  process.env.API_GATEWAY_URL || process.env.NEXT_PUBLIC_GATEWAY_URL || 'http://localhost:8080';

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
          detail: 'Authentication required to view your timetable.',
        },
        { status: 401 }
      );
    }

    if (resolution.session.role !== 'student') {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/forbidden',
          title: 'Forbidden',
          status: 403,
          detail: 'Only students may view the student timetable.',
        },
        { status: 403 }
      );
    }

    const headers = await getGatewayHeaders(req, supabase, resolution.session, 'STUDENT');
    // Intentionally do not forward ?batchId / studentId from the browser.
    const gatewayUrl = new URL('/api/v1/academics/timetables/my', GATEWAY_BASE_URL);

    const gatewayResponse = await fetch(gatewayUrl.toString(), {
      method: 'GET',
      headers,
      cache: 'no-store',
    });

    const data = await gatewayResponse.json().catch(() => ({}));
    if (!gatewayResponse.ok) {
      return NextResponse.json(data, { status: gatewayResponse.status });
    }

    return NextResponse.json(data, { status: 200 });
  } catch (err: unknown) {
    const message = err instanceof Error ? err.message : 'Unexpected error';
    if (message.startsWith('GATEWAY_MISSING_AUTH')) {
      return NextResponse.json(
        {
          type: 'https://campxsync.edu/errors/unauthorized',
          title: 'Unauthorized',
          status: 401,
          detail: 'Authentication required to view your timetable.',
        },
        { status: 401 }
      );
    }
    return NextResponse.json(
      {
        type: 'https://campxsync.edu/errors/internal-server-error',
        title: 'Internal Server Error',
        status: 500,
        detail: message,
      },
      { status: 500 }
    );
  }
}
